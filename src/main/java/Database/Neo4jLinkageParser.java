package Database;
import Application.ExceptionLogger;
import Application.Task;
import Model.Linkage;
import Model.Package;
import Utilities.GlobalVersionRangeResolver;
import akka.Done;
import akka.NotUsed;
import akka.actor.ActorSystem;
import akka.japi.function.Function;
import akka.stream.OverflowStrategy;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import org.neo4j.driver.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.util.Try;

import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static Utilities.GlobalUtilities.*;

/**
 * This class provides functionality to convert Artifact-to-Package relations into Artifact-to-Artifact relations. It
 * uses a VersionRangeResolver for the corresponding ecosystem to correctly resolve version range specifications and
 * produce all resulting edges.
 */
public class Neo4jLinkageParser {

    private static final int PROGRESS_PRINTOUT_STEP = 2;
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final Neo4jDatabaseController neo4jDatabaseController;
    private final Task task;
    private Long startTime;
    private Long endTime;
    private static final DecimalFormat df = new DecimalFormat("0.00");
    private int parallel;
    private final AtomicInteger oldDepCount = new AtomicInteger(0);
    private final AtomicInteger newDepCount = new AtomicInteger(0);
    private int packageCountTotal = 0;
    private final AtomicInteger packageCountCurrent = new AtomicInteger(0);

    private final AtomicInteger lastPrintoutPercentage = new AtomicInteger(-1 * PROGRESS_PRINTOUT_STEP);
    private final int batchSize = 500;

    private final GlobalVersionRangeResolver rangeResolver = GlobalVersionRangeResolver.getInstance();

    /**
     * Creates a new Linkage Parser with the given Task, using the given database controller
     * @param task Current task
     * @param neo4jDatabaseController (new) Neo4jDatabaseController
     */
    public Neo4jLinkageParser(Task task, Neo4jDatabaseController neo4jDatabaseController){
        this.neo4jDatabaseController = neo4jDatabaseController;
        this.task = task;
        Properties props = System.getProperties();
        this.parallel = Integer.parseInt(props.getOrDefault("dgm.parallel","10").toString());
        this.parallel = Math.round((float) this.parallel/2);
    }

    /**
     * Function to parse all Artifact-to-Package dependencies to Artifact-to-Artifact dependencies using VersionRangeResolver
     */
    public void parseLinkage(){
        logger.info("== Beginning of Parsing Linkages ==");
        this.startTime = System.currentTimeMillis();

        /* Mining of all packages, use only Packages with Artifacts */
        Set<StringPair> packageIdsAndRepos = neo4jDatabaseController.sendCypherRequestWithResponseSet(
                "MATCH (p:Package) WHERE EXISTS ((:Artifact)-[:dependentOn {resolved: false}]->(p)) AND EXISTS((p)-[:version]->(:Artifact)) RETURN  p.id AS pid, p.repo AS repo",
                r -> new StringPair(r.get("pid").asString(), r.get("repo").asString()), null);
        this.packageCountTotal = packageIdsAndRepos.size();
        int bufferSize = this.parallel*2;
        logger.info("Got a total of " + packageCountTotal + " packages to process for linkages.");


        /* Setup Actor and Source of the Threads */
        Source<StringPair, NotUsed> source = Source.from(packageIdsAndRepos);
        startTime = System.currentTimeMillis();
        ActorSystem system = ActorSystem.create();


        Function<StringPair, CompletionStage<String>> stage = (packageIdAndRepo) -> {
            return CompletableFuture.supplyAsync(() -> {

                Session session = null;

                try {
                    if(!neo4jDatabaseController.isClosed) {
                        String packageId = packageIdAndRepo.First;
                        String packageRepo = packageIdAndRepo.Second;

                        session = neo4jDatabaseController.driver.session();
                        Map<String, Object> packageProp = new HashMap<>();
                        packageProp.put("packageId", packageId);

                        /* Get information of nodes and edges of original dependency edge */

                        List<StringPair> fromDependencies = neo4jDatabaseController.sendCypherRequestWithResponseList(session,
                                "MATCH (a:Artifact)-[d:dependentOn {resolved: false}]->(:Package {id:$packageId}) RETURN a.id AS aid, d.version AS dv",
                                r -> new StringPair(r.get("aid").asString(), r.get("dv").asString()), packageProp);

                        List<StringPair> toArtefacts = neo4jDatabaseController.sendCypherRequestWithResponseList(session,
                                "MATCH (a:Artifact)<-[:version]-(:Package {id:$packageId}) RETURN a.id AS aid, a.version AS av",
                                r -> new StringPair(r.get("aid").asString(), r.get("av").asString()), packageProp);

                        /* Build a lookup from the 'toArtefacts' list */
                        Map<String, String> versionToIdLookup = toArtefacts.stream().map(s -> Map.entry(s.Second, s.First))
                                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

                        this.oldDepCount.addAndGet(fromDependencies.size());

                        ArrayList<Map<String, Object>> dependencyParameters = new ArrayList<>();

                        /* For every dependency */
                        for (StringPair fromDependency : fromDependencies) {
                            String fromVersionString = fromDependency.Second;
                            String fromArtefactId = fromDependency.First;

                            /* For every possible Artifact*/
                            Set<String> containedVersions = rangeResolver.findMatchingVersions(packageRepo, fromVersionString, versionToIdLookup.keySet());
                            for (String toArtefactVersion : containedVersions) {
                                String toArtefactId = versionToIdLookup.get(toArtefactVersion);
                                Map<String, Object> props = new HashMap<>();
                                props.put("artefactId", fromArtefactId);
                                props.put("dependentArtefactId", toArtefactId);
                                dependencyParameters.add(props);
                            }
                            if(dependencyParameters.size()>batchSize){
                                processDependencies(session, dependencyParameters);
                            }
                        }
                        processDependencies(session, dependencyParameters);
                        setIncomingEdgesResolved(session, packageId);

                        return packageId;
                    }
                } catch (IllegalStateException ex){
                    // Case that Parsing got stopped
                    ExceptionLogger.add(ex,this.getClass().getName());
                } catch (Exception x) {
                    logger.error("Unexpected error while parsing packages", x);
                    ExceptionLogger.add(x, this.getClass().getName());
                } finally {
                    if(session != null && session.isOpen()) session.close();
                }
                return null;
            });
        };


        Sink<String, CompletionStage<Done>> progressSink = Sink.foreachAsync(parallel, pId -> CompletableFuture.runAsync(() -> {
            try {
                int percentDone = packageCountCurrent.incrementAndGet() * 100 / packageCountTotal;
                int lastPrint = lastPrintoutPercentage.get();

                if(percentDone - lastPrint >= PROGRESS_PRINTOUT_STEP){
                    boolean needToPrint = false;
                    synchronized(lastPrintoutPercentage) {
                        if(percentDone - lastPrintoutPercentage.get() >= PROGRESS_PRINTOUT_STEP){
                            needToPrint = true;
                            lastPrintoutPercentage.addAndGet(PROGRESS_PRINTOUT_STEP);
                        }
                    }

                    if(needToPrint) this.printPercentage();
                }
            } catch (Exception ex){
                logger.error("Uncaught error in printout sink", ex);
                ExceptionLogger.add(ex,Neo4jDatabaseController.class.getName());
            }

        }, system.dispatcher()));

        Runnable onComplete = () -> {
            try {
                this.neo4jDatabaseController.drainCollisionBuffer();
                this.endTime = System.currentTimeMillis();
                this.printPercentage();
                task.setStatus(Status.FINISHED);
                neo4jDatabaseController.close();
                system.terminate();
            } catch (Exception ex){
                logger.error("Uncaught error in completion stage",  ex);
                ExceptionLogger.add(ex,neo4jDatabaseController.getClass().toString());
            }
        };

        java.util.function.Function<Throwable, Void> onError = (ex) -> {
            logger.error("Mining finished with errors", ex);
            this.endTime = System.currentTimeMillis();
            task.setStatus(Status.ERROR);
            neo4jDatabaseController.close();
            system.terminate();
            return null;
        };

        source
            .filter(i -> ((i != null) && (!i.First.equals(""))))
            .buffer(bufferSize, OverflowStrategy.backpressure())
            .mapAsyncUnordered(this.parallel, stage)
            .runWith(progressSink, system)
            .thenRun(onComplete)
            .exceptionally(onError);
    }

    /**
     * Prints the current progress of the parsing process to the commandline.
     */
    public void printPercentage(){
        int percentage = Math.round(((float) this.packageCountCurrent.get() * 100) / this.packageCountTotal);
        if(this.endTime==null){
            if(this.packageCountTotal==0){
                logger.info("=> Base for Linkage Parsing is getting mined <=");
            } else {
                float timespan = ((float) (System.currentTimeMillis() - this.startTime)) / 1000 / 60;
                logger.info("=> " + percentage + "%" + " [" + this.packageCountCurrent + "/" + packageCountTotal + "] Packages / [" + this.oldDepCount.get() + " -> " + this.newDepCount.get() + " (" + df.format(((float) this.newDepCount.get() / (float) this.oldDepCount.get())) + ")] Dependencies, time " + df.format(timespan) + " minutes <=");
            }
        } else {
            float generatingTime = ((float) (this.endTime - startTime) / 1000) / 60 / 60;
            logger.info("=> Finished Parsing Linkages of "+this.packageCountTotal +" Packages after "+df.format(generatingTime)+" hours, "+this.oldDepCount.get()+" dependencies parsed to "+this.newDepCount.get()+" ("+df.format(((float)this.newDepCount.get()/(float)this.oldDepCount.get()))+")<=");
        }
    }

    /**
     * Function to stop thread and neo4jDatabaseController
     */
    public void stop(){
        this.neo4jDatabaseController.close();
        Thread.currentThread().interrupt();
    }

    /**
     * Help function to process dependencies of type Artifact->Artifact stored dependencies with UNWIND
     * @param dependencyParameters stored dependencies
     */
    private void processDependencies(Session session, ArrayList<Map<String, Object>> dependencyParameters){
        Map<String, Object> cypherParameter = new HashMap<>();
        cypherParameter.put("dependencies",dependencyParameters);

        this.newDepCount.addAndGet(dependencyParameters.size());

        neo4jDatabaseController.sendCypherRequest(session, 0, "UNWIND $dependencies AS dependency MATCH (a:Artifact {id:dependency.artefactId}) MATCH (b:Artifact {id:dependency.dependentArtefactId}) MERGE (b)<-[d:dependentOnAA]-(a)", cypherParameter);
        dependencyParameters.clear();
    }

    private void setIncomingEdgesResolved(Session session, String targetPid){
        final String query = "MATCH (p: Package {id: $pid})<-[d:dependentOn]-() SET d.resolved = true";
        session.run(query, Map.of("pid", targetPid));
    }


    /**
     * Simple String Pair implementation
     */
    public static class StringPair {
        public final String First;
        public final String Second;

        public StringPair(String f, String s) {
            First = f;
            Second = s;
        }

        @Override
        public int hashCode() {
            return (First + Second).hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if(obj instanceof StringPair){
                StringPair other = (StringPair) obj;
                return other.First.equals(First) && other.Second.equals(Second);
            } else {
                return false;
            }
        }
    }

}