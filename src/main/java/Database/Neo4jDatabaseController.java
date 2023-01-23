package Database;

import Application.ExceptionLogger;
import Model.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import Model.Package;
import Utilities.GlobalVersionRangeResolver;
import org.neo4j.driver.*;
import org.neo4j.driver.Record;
import org.neo4j.driver.exceptions.TransientException;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import static Utilities.GlobalUtilities.*;

/**
 * Class of the DatabaseController, Connection between the DependencyGraphMiner and the Neo4j Database. Provides functionality
 * to store Package objects in a given Neo4j database. Also exposes some generic functions for other classes to execute
 * Cypher queries with.
 * */
public class Neo4jDatabaseController implements AutoCloseable {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    public final Driver driver;
    public int numberOfCollisions = 0;
    public int numberOfCurrentCollisions = 0;
    private int maximumNumberOfCollisions = 0;
    private final ArrayList<String> collisionBufferRequests = new ArrayList<>();
    private final ArrayList<Map<String, Object>> collisionBufferParams = new ArrayList<>();
    private final Linkage dependencyLinkage;
    private final ArrayList<Map<String,Map<String, Object>>> collisionBuffer = new ArrayList<>();
    public boolean isClosed = false;

    /**
     * Constructor of Neo4jDatabaseController
     * @param uri URL of neo4j database
     * @param user username of neo4j database
     * @param password password of neo4j database
     */
    public Neo4jDatabaseController(String uri, String user, String password) {

        /* Collision Control Setup */
        long maxWaitingTime = 10000;
        long currentWaitingTime = 0;
        while(currentWaitingTime<maxWaitingTime){
            maximumNumberOfCollisions++;
            currentWaitingTime += getCollisionWaitingTime(maximumNumberOfCollisions);
        }

        /* Get Dependency Linkage Configuration */
        String linkageProp = System.getProperties().getOrDefault("dgm.linkage","pp").toString();
        switch (linkageProp) {
            case "pp" -> this.dependencyLinkage = Linkage.PackagePackage;
            case "ap" -> this.dependencyLinkage = Linkage.ArtifactPackage;
            case "aa" -> this.dependencyLinkage = Linkage.ArtifactArtifact;
            default -> {
                logger.warn("Wrong Dependency Linkage configuration, using Package-to-Package instead");
                this.dependencyLinkage = Linkage.PackagePackage;
            }
        }

        /* Driver Setup */
        Config config = Config.builder().withLogging(Logging.none())
                .withConnectionTimeout( 40, TimeUnit.SECONDS)
                .withMaxConnectionLifetime(60, TimeUnit.MINUTES)
                .withMaxConnectionPoolSize(1000)
                .withConnectionAcquisitionTimeout(5, TimeUnit.SECONDS)
                .build();
        driver = GraphDatabase.driver(uri, AuthTokens.basic(user, password), config);
        driver.session().run("RETURN 1");
    }

    /**
     * Stores the given package node in the database. Creates all corresponding artifact nodes, as well as dependency edges
     * to other packages. If dependency targets are not yet part of the database, a prototype package is created instead.
     *
     * @param packageNode package node to write in database
     * @param isUpdate True if this package object is inserte
     */
    public void createPackageNode(Package packageNode, boolean isUpdate) {
        if(isUpdate) updatePackage(packageNode);
        else insertNewPackage(packageNode);
    }


    private void insertNewPackage(Package packageNode){
        try (Session session = driver.session()) {

            /* Query for Package */
            sendCypherRequest(session, 0, "MERGE (p:Package {id: $props.id}) SET p=$props", packageNode.getPackageProps());

            /* Query for Artifacts */
            sendCypherRequest(session, 0,
                    "MATCH (p:Package {id:$packageId})" +
                            "UNWIND $artifacts AS artifact MERGE (p)-[:version]->(a:Artifact {id: artifact.id}) SET a = artifact "
                    , packageNode.getArtifactProps());

            /* Query for Dependencies */
            if (this.dependencyLinkage == Linkage.PackagePackage) {
                // Calculate set of target packages once!
                Set<String> targetPackageIds = new HashSet<>();
                for (Artifact artifact : packageNode.getArtifactList()) {

                    for (Dependency d : artifact.getDependencies()) {
                        String dependencyId = packageNode.getRepository() + ":" + d.getName();
                        targetPackageIds.add(dependencyId);
                    }
                }

                Map<String, Object> params = new HashMap<>();
                params.put("sourceId", packageNode.getId());
                params.put("dependencies", new ArrayList<>(targetPackageIds));

                sendCypherRequest(session, 0, "MATCH(p1: Package {id: $sourceId}) UNWIND $dependencies AS dependency " +
                        "MERGE (p2: Package {id: dependency}) ON CREATE SET p2.name='Prototype Package' " +
                        "MERGE (p1)-[:dependentOnPP]->(p2)", params);

            } else {
                /* Same Query for ArtifactPackage and ArtifactArtifact Linkage */
                for (Artifact artifact : packageNode.getArtifactList()) {
                    sendCypherRequest(session, 0,
                            "MATCH (a:Artifact {id:$artifactId}) " +
                                    "UNWIND $dependencies AS dependency " +
                                    "MERGE (p:Package {id:dependency.packageId}) ON CREATE SET p.name='Prototype Package' " +
                                    "CREATE (a)-[d:dependentOn]->(p) SET d=dependency.props" // CREATE instead of MERGE: Huge performance benefit
                            , artifact.getDependencyProps(true));
                }
            }
        }
    }

    private void updatePackage(Package packageNode){

        final String apEdgeQuery = "MATCH (:Package {id :$id})<-[d:dependentOn {resolved: true}]-(a:Artifact) RETURN d.version AS v, a.id AS id";
        final String insertAAEdge = "MATCH (a: Artifact {id: $ida}) MATCH (b:Artifact {id: $idb}) CREATE (a)-[:dependentOnAA]->(b)";

        try (Session session = driver.session()){

            // Only update package node if it did not exist or was a prototype before
            if(!hasPackage(packageNode.getId(), session)){
                sendCypherRequest(session, 0, "MERGE (p:Package {id: $props.id}) SET p=$props", packageNode.getPackageProps());
            }

            // PackageNode will only contain new artifacts, so this is fine
            sendCypherRequest(session, 0,
                    "MATCH (p:Package {id:$packageId})" +
                            "UNWIND $artifacts AS artifact MERGE (p)-[:version]->(a:Artifact {id: artifact.id}) SET a = artifact "
                    , packageNode.getArtifactProps());

            if(dependencyLinkage == Linkage.ArtifactArtifact && !packageNode.getArtifactList().isEmpty()){
                // Correct all previously resolve Artifact-to-Package edges so that new artifacts are re-evaluated against the corresponding version range
                // In Short: Make sure 'resolved' AP edges stay fully resolved
                GlobalVersionRangeResolver resolver = GlobalVersionRangeResolver.getInstance();

                // Get all AP edges to the package that received new artifacts that were fully resolved before
                Set<Neo4jLinkageParser.StringPair> aidAndVersionRanges = sendCypherRequestWithResponseSet(apEdgeQuery, r -> new Neo4jLinkageParser.StringPair(r.get("v").asString(), r.get("id").asString()),
                        Map.of("id", packageNode.getId()));

                // Build a lookup of newly inserted versions to their Artifact id
                Map<String, String> newVersionToAid = new HashMap<>();

                for(Artifact a : packageNode.getArtifactList()){
                    newVersionToAid.put(a.getVersion(), a.getId());
                }

                // For each edge: Reevaluate if any of the new artifacts fall into the dependency version range
                for(Neo4jLinkageParser.StringPair edge : aidAndVersionRanges){
                    String edgeVersionRange = edge.First;
                    String edgeArtifactId = edge.Second;

                    Set<String> versionsToInsertEdgesTo = resolver.findMatchingVersions(packageNode.getRepository(), edgeVersionRange, newVersionToAid.keySet());

                    // Create AA edges for those new artifacts that belong to the range
                    for(String versionToInsertEdgeTo : versionsToInsertEdgesTo){
                        String targetAid = newVersionToAid.get(versionToInsertEdgeTo);
                        session.run(insertAAEdge, Map.of("ida", edgeArtifactId, "idb", targetAid)).consume();
                    }
                }

            }




            // Creation of new dependencies done as before.
            if (this.dependencyLinkage == Linkage.PackagePackage) {
                // Calculate set of target packages once!
                Set<String> targetPackageIds = new HashSet<>();
                for (Artifact artifact : packageNode.getArtifactList()) {

                    for (Dependency d : artifact.getDependencies()) {
                        String dependencyId = packageNode.getRepository() + ":" + d.getName();
                        targetPackageIds.add(dependencyId);
                    }
                }

                Map<String, Object> params = new HashMap<>();
                params.put("sourceId", packageNode.getId());
                params.put("dependencies", new ArrayList<>(targetPackageIds));

                sendCypherRequest(session, 0, "MATCH(p1: Package {id: $sourceId}) UNWIND $dependencies AS dependency " +
                        "MERGE (p2: Package {id: dependency}) ON CREATE SET p2.name='Prototype Package' " +
                        "MERGE (p1)-[:dependentOnPP]->(p2)", params);

            } else {
                /* Same Query for ArtifactPackage and ArtifactArtifact Linkage */
                for (Artifact artifact : packageNode.getArtifactList()) {
                    sendCypherRequest(session, 0,
                            "MATCH (a:Artifact {id:$artifactId}) " +
                                    "UNWIND $dependencies AS dependency " +
                                    "MERGE (p:Package {id:dependency.packageId}) ON CREATE SET p.name='Prototype Package' " +
                                    "CREATE (a)-[d:dependentOn]->(p) SET d=dependency.props" // CREATE instead of MERGE: Huge performance benefit
                            , artifact.getDependencyProps(true));
                }
            }

        }
    }

    /**
     * Return true if this package object has previously existed in the DB (not as prototype!)
     * @param packageId id of the package object
     * @param s Session
     * @return true if package exists as non-prototype
     */
    private boolean hasPackage(String packageId, Session s){
        final String query = "MATCH (p: Package {id: $id}) RETURN p.name AS name";
        try {
            Result r = s.run(query, Map.of("id", packageId));

            if(!r.hasNext()) return false;
            else {
                String name = r.next().get("name").asString();
                return !name.equals("Prototype Package");
            }
        } catch (Exception x){
            return false;
        }
    }

    /**
     * Function to initialize uniqueness constraints and indices for the database.
     */
    public void initializeDatabase() {
        Session session = this.driver.session();

        /* Create Constraints and Indexes */
        sendCypherRequest(session, 0, "CREATE CONSTRAINT packageConstraint IF NOT EXISTS FOR (p:Package) REQUIRE p.id IS UNIQUE", null);
        sendCypherRequest(session, 0, "CREATE CONSTRAINT artifactConstraint IF NOT EXISTS FOR (a:Artifact) REQUIRE a.id IS UNIQUE", null);
        sendCypherRequest(session, 0, "CREATE INDEX packageIndex IF NOT EXISTS FOR (p:Package) ON (p.id)", null);
        sendCypherRequest(session, 0, "CREATE INDEX artifactIndex IF NOT EXISTS FOR (a:Artifact) ON (a.id)", null);

        session.close();
    }

    public Set<String> getAllArtifactIds(){
        return this.sendCypherRequestWithResponseSet("MATCH (a: Artifact) RETURN a.id AS id", r -> r.get("id").asString(), null);
    }

    /**
     * Default function to execute a Cypher Request that does not involve returning a result. This function implements
     * Collision Detection and Control, meaning that if the operation fails due to locked nodes / concurrent modifications,
     * they are retried for a certain number of times with increasing sleep intervals. If all retries fail, the operation
     * is added to the collision buffer and executed in a sequential fashion at a later stage.
     *
     * @param session Current Neo4j session of database
     * @param trys Number of times the execution of this operation has been tried before
     * @param request Cypher request to execute
     * @param params Optional parameters for the Cypher request
     */
    public void sendCypherRequest(Session session, int trys, String request, Map<String, Object> params) {
        try {
            if (params == null){
                session.run(request).consume();
            } else {
                session.run(request, params).consume();
            }
        } catch (TransientException exception) {

            /* Collision Control */
            if (trys <= maximumNumberOfCollisions) {
                trys++;
                try {
                    Thread.sleep(getCollisionWaitingTime(trys));
                } catch (InterruptedException ex) {
                    ex.printStackTrace();
                }
                sendCypherRequest(session, trys, request, params);
            } else {
                addToCollisionBuffer(request,params);
                numberOfCollisions++;
                numberOfCurrentCollisions++;
            }
        } catch (Exception ex) {
            if(!isClosed){
                ExceptionLogger.add(ex,this.getClass().getName());
            }
        }
    }

    /**
     * Function to execute a given cypher request that does not involve returning a result.
     *
     * @param session Current Neo4j session
     * @param request Cypher request as string
     */
    public void sendCypherRequest(Session session, String request) {
        try {
            Result r = session.run(request);
            r.consume();
        }  catch (Exception ex) {
            ExceptionLogger.add(ex,this.getClass().getName());
        }
    }


    /**
     * Execute the given cypher query (request) on the given session, map all resulting records to a generic result type
     * and return the results as a list.
     *
     * @param s The session to execute the given request on
     * @param request The request to execute
     * @param mapFunc A function that maps Neo4j Records to an arbitrary result type T
     * @param params Optional parameters for the Cypher request
     * @param <T> Element type of the resulting list, must match the result type of mapFunc
     * @return List of result records mapped to type T
     */
    public <T> List<T> sendCypherRequestWithResponseList(Session s, String request, Function<Record, T> mapFunc, Map<String, Object> params){
        try {
            Result r = params == null ? s.run(request) : s.run(request, params);

            return r.list(mapFunc);
        } catch (Exception ex) {
            logger.error("Failed to send request: " + request, ex);
            ExceptionLogger.add(ex, this.getClass().getName());
            return null;
        }
    }

    /**
     * Execute the given Cypher request using a fresh session, map all resulting records to a generic result type and
     * return the results as a Set.
     * @param request The Cypher request to execute
     * @param mapFunc A function that maps Neo4j Records to an arbitrary result type T
     * @param params Optional parameters for the Cypher request
     * @return Set of result records mapped to type T
     * @param <T> Element type of the resulting set, must match the result type of mapFunc
     */
    public <T> Set<T> sendCypherRequestWithResponseSet(String request, Function<Record, T> mapFunc, Map<String, Object> params) {
        long cnt = 0L;
        try (Session s = driver.session()) {
            Result r = params == null ? s.run(request) : s.run(request, params);
            Set<T> resultSet = new HashSet<>();

            while(r.hasNext()){
                cnt += 1L;

                if(cnt % 300000 == 0){
                    logger.debug("Progress while downloading set: " + cnt);
                }

                Record current = r.next();
                resultSet.add(mapFunc.apply(current));
            }

            return resultSet;
        } catch (Exception ex) {
            logger.error("Failed to send request: " + request, ex);
            ExceptionLogger.add(ex, this.getClass().getName());
            return null;
        }
    }

    /**
     * Returns the total number of nodes in the current database
     *
     * @return Count of nodes in DB (all labels)
     */
    public int getTotalNodeCount(){
        Set<Integer> resultSet = sendCypherRequestWithResponseSet("MATCH (n) RETURN COUNT(n) AS cnt",
                r -> r.get("cnt").asInt(), null);

        if(resultSet.size() > 0) return resultSet.iterator().next();
        else {
            ExceptionLogger.add(new IllegalStateException("No node count returned from database"), getClass().getName());
            return -1;
        }
    }

    /**
     * Deletes all nodes and edges of any label in the current database.
     */
    public void clearDatabase(){
        int nodeCount = getTotalNodeCount();
        int deleteCount = 0;
        Session session = driver.session();
        while (deleteCount<nodeCount){
            sendCypherRequest(session,"MATCH (n) WITH n LIMIT 1000 DETACH DELETE n");
            logger.info("1000 nodes deleted");
            deleteCount+=1000;
        }
        session.close();
    }

    /**
     * Adds a Cypher request to the collision buffer when it exceeded its maximum number of retries.
     *
     * @param request Cypher request string
     * @param param Parameters of request
     */
    private void addToCollisionBuffer(String request, Map<String, Object> param){
        this.collisionBufferRequests.add(request);
        this.collisionBufferParams.add(param);
        Map<String, Map<String, Object>> collision = new HashMap<>();
        collision.put(request,param);
        collisionBuffer.add(collision);
    }

    /**
     * Executes all Cypher requests in the collision buffer one after another to ensure that no further locking / concurrent
     * modification exceptions may occur.
     */
    public void drainCollisionBuffer(){
        long startTime = System.currentTimeMillis();
        int collisionBufferSize = this.collisionBuffer.size();
        logger.info("== Start processing Collision Buffer of size " + collisionBufferSize + " ==");
        Session session = driver.session();
        for (int i = 0; i<Math.min(collisionBufferSize,this.collisionBuffer.size());i++){
            sendCypherRequest(session,0,collisionBufferRequests.get(i),collisionBufferParams.get(i));
        }
        session.close();
        float time = ((float) (System.currentTimeMillis() - startTime)) / 1000;
        logger.info("== Collision Buffer drained in "+time+" secounds ==");
    }

    /* Get functions */

    private long getCollisionWaitingTime(int trys){
        return 100+ 10L *trys;
    }

    public Linkage getDependencyLinkage() {
        return dependencyLinkage;
    }

    @Override
    public void close() {
        this.isClosed = true;
        driver.close();
    }

}