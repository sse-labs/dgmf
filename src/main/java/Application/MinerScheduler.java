package Application;

import Database.Neo4jDatabaseController;
import Model.Linkage;
import Repositories.Miner;
import akka.Done;
import akka.NotUsed;
import akka.actor.ActorSystem;
import akka.japi.function.Function;
import akka.stream.*;
import akka.stream.javadsl.*;
import Model.Package;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.text.DecimalFormat;
import java.util.concurrent.atomic.AtomicInteger;

import static Utilities.GlobalUtilities.*;


/** Class Scheduler, the main control instance of the DependencyGraphMiner */
public class MinerScheduler {

    private static final int PROGRESS_PRINTOUT_STEP = 2;

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final boolean isInUpdateMode;
    private final Miner miner;
    private final Neo4jDatabaseController neo4jDatabaseController;
    private final int bufferSize;
    private final int parallel;
    public final List<String> names;
    private final int noOfNames;
    public Long startTime;
    private ActorSystem system;
    private final AtomicInteger completedPackageCounter;
    private final AtomicInteger lastPrintoutPercentage;
    private boolean finishedMining;
    private long endTime;
    private static final DecimalFormat df = new DecimalFormat("0.00");
    private String repositoryName;



    /**
     * Constructor of Scheduler
     * @param source String list of package identifiers, base of miner
     * @param miner Implementation of Miner interface
     * @param neo4jDatabaseController DatabaseController for writing nodes and edges
     */
    public MinerScheduler(List<String> source, Miner miner, Neo4jDatabaseController neo4jDatabaseController, boolean updateModeEnabled) {
        this.names = source;
        this.noOfNames = names.size();
        this.neo4jDatabaseController = neo4jDatabaseController;
        this.miner = miner;
        Properties props = System.getProperties();
        this.parallel = Integer.parseInt(props.getOrDefault("dgm.parallel","10").toString());
        this.bufferSize = parallel*4;
        this.completedPackageCounter = new AtomicInteger(0);
        this.lastPrintoutPercentage = new AtomicInteger(-1 * PROGRESS_PRINTOUT_STEP);
        this.finishedMining = false;
        this.endTime = 0;
        this.isInUpdateMode = updateModeEnabled;
    }

    /**
     * Function to run a given Miner in parallel Threads and write Nodes and Edges with the DatabaseController into the Database
     * @param task current task
     */
    public void runProcess(Task task) {
        try {
            this.repositoryName = task.getRepositoryName();
            /* Setup Actor and Source of the Threads */
            Source<String, NotUsed> source = Source.from(names);
            startTime = System.currentTimeMillis();
            system = ActorSystem.create();

            /* Stage One mines meta information to a given package name and returns JSON */
            Function<String, CompletionStage<JSONObject>> stageOne = (i) -> CompletableFuture.supplyAsync(() -> {
                try {

                    int percentDone = completedPackageCounter.incrementAndGet() * 100 / noOfNames;
                    int lastPrint = lastPrintoutPercentage.get();

                    if(percentDone - lastPrint >= PROGRESS_PRINTOUT_STEP){
                        boolean needToPrint = false;
                        synchronized(lastPrintoutPercentage) {
                            if(percentDone - lastPrintoutPercentage.get() >= PROGRESS_PRINTOUT_STEP){
                                needToPrint = true;
                                for(int cnt = 0; cnt < PROGRESS_PRINTOUT_STEP; cnt++)
                                    lastPrintoutPercentage.incrementAndGet();
                            }
                        }

                        if(needToPrint) this.printPercentage();
                    }


                    logger.debug("Mining " + i);
                    JSONObject ret =  miner.minePackage(i);
                    logger.debug("Done mining " + i);
                    return ret;
                } catch (Exception ex){
                    logger.error("Uncaught error in stage one", ex);
                    ExceptionLogger.add(ex,miner.getClass().toString());
                    return null;
                }
            }, system.dispatcher());

            /* Stage Two parses given JSON into a Package Object */
            Function<JSONObject, CompletionStage<Package>> stageTwo = (i) -> CompletableFuture.supplyAsync(() -> {
                try {
                    Package p = miner.parsePackage(i);

                    if(isInUpdateMode && p != null && p.getArtifactList().isEmpty()) return null;

                    if(p != null) logger.debug("Done parsing " + p.getName());

                    return p;
                } catch (Exception ex){
                    logger.error("Uncaught error in stage two", ex);
                    ExceptionLogger.add(ex,miner.getClass().toString());
                    return null;
                }
            }, system.dispatcher());


            Sink<Package, CompletionStage<Done>> storageSink = Sink.foreachAsync(parallel, p -> CompletableFuture.runAsync(() -> {
                try {
                    String pName = p != null ? p.getName() : "null";
                    logger.debug("Storing " + pName);

                    if(!neo4jDatabaseController.isClosed){
                        neo4jDatabaseController.createPackageNode(p, isInUpdateMode);

                    }

                    logger.debug("Done storing " + pName);
                } catch (Exception ex){
                    logger.error("Uncaught error in stage three", ex);
                    ExceptionLogger.add(ex,Neo4jDatabaseController.class.getName());
                }

            }, system.dispatcher()));

            Runnable onComplete = () -> {
                try {
                    logger.info("== Miner finished ==");
                    this.finishedMining = true;
                    this.endTime = System.currentTimeMillis();
                    printPercentage();
                    neo4jDatabaseController.drainCollisionBuffer();
                    this.endTime = System.currentTimeMillis();
                    printReport();
                    if(this.neo4jDatabaseController.getDependencyLinkage()== Linkage.ArtifactArtifact){
                        task.taskParseDependenciesCommand();
                    }
                    task.setStatus(Status.FINISHED);
                    miner.shutdown();
                    neo4jDatabaseController.close();
                    system.terminate();
                } catch (Exception ex){
                    logger.error("Uncaught error in final stage",  ex);
                    ExceptionLogger.add(ex,neo4jDatabaseController.getClass().toString());
                }
            };

            java.util.function.Function<Throwable, Void> onError = (ex) -> {
                logger.error("Mining finished with errors", ex);
                this.endTime = System.currentTimeMillis();
                this.finishedMining = true;
                task.setStatus(Status.ERROR);
                miner.shutdown();
                neo4jDatabaseController.close();
                system.terminate();
                return null;
            };

            logger.info("Start mining " + noOfNames + " packages for repository " + this.repositoryName + "...");

            /* Run source elements parallel through all stages and filter out empty elements */
            source.filter(i -> ((i != null) && (!i.equals(""))))
                    .buffer(bufferSize, OverflowStrategy.backpressure())
                    .mapAsyncUnordered(parallel, stageOne)
                    .filter(Objects::nonNull)
                    .buffer(bufferSize, OverflowStrategy.backpressure())
                    .mapAsyncUnordered(parallel, stageTwo)
                    .filter(Objects::nonNull)
                    .buffer(bufferSize, OverflowStrategy.backpressure())
                    .runWith(storageSink, system)
                    .thenRun(onComplete)
                    .exceptionally(onError);

        } catch (Exception ex){
            logger.error("Uncaught error setting up pipeline", ex);
            ExceptionLogger.add(ex,this.getClass().toString());
        }
    }

    /**
     * Help Function: printing current process
     * */
    public void printPercentage() {
        if(!finishedMining){
            int remain = this.completedPackageCounter.get();
            int percentage = Math.round(((float) remain * 100) / noOfNames);
            float timespan = ((float) (System.currentTimeMillis() - this.startTime)) / 1000 /60;
            logger.info("=> " + percentage + "%" + " [" + remain + "/" + noOfNames + "], time " + df.format(timespan) + " minutes <=");
        } else {
            float generatingTime = ((float) (this.endTime - startTime) / 1000) / 60 / 60;
            logger.info("=> Finished Mining after "+df.format(generatingTime)+" hours, number of format errors " + miner.getFormatErrorCounter() + " packages, number of request errors: " + miner.getRequestErrorCounter() + ", Collision Buffer: "+ neo4jDatabaseController.numberOfCollisions + " <=");
        }
    }

    /**
     * Help Function: kill all Threads of the Scheduler
     * */
    public void killProcess(){
        if(this.system!=null){
            system.terminate();
        }
    }

    public void printReport(){
        logger.info("==> Report of mining repository " + this.repositoryName);
        logger.info("    Number of packages: " + completedPackageCounter +" of total " + noOfNames);
        logger.info("    Missing packages: " + miner.getRequestErrorCounter() +" request errors, " + miner.getFormatErrorCounter() + " format errors");
        logger.info("    Configuration: " + this.parallel +" threads, dependency linkage " + neo4jDatabaseController.getDependencyLinkage());
        logger.info("==> Total time: "+df.format(((float) (this.endTime - this.startTime)) / 1000 /60) + " minutes or " + df.format(((float) (this.endTime - this.startTime)) / 1000 /60 /60) + " hours");
    }
}