package Application;

import Database.Neo4jDatabaseController;
import Database.Neo4jLinkageParser;
import Repositories.Miner;
import Repositories.RepositoryController;
import org.neo4j.driver.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

import static Utilities.GlobalUtilities.*;
import static Utilities.CommandUtilities.*;

/**
 * class for a task of the dgm
 */
public class Task {

    private final Logger logger = LoggerFactory.getLogger(getClass());


    private Status status;
    private RepositoryController.IdGenerator idGenerator;
    private Thread currentThread;
    private Miner miner;
    private MinerScheduler minerScheduler;
    private Neo4jDatabaseController neo4jDatabaseController;
    private String repositoryName;
    private Neo4jLinkageParser neo4JLinkageParser;

    private String uri;
    private String username;
    private String password;

    /**
     * Constructor initializes status with WAITING
     */
    public Task (){
        this.status = Status.WAITING;
    }

    /* Functions for each command */

    /**
     * Function to start 'Start' Command
     * @param repositoryName name of the repository of task
     * @param idGenerator implementation of IdGenerator interface
     * @param miner implementation of Miner interface
     */
    public void taskStartCommand(String repositoryName, RepositoryController.IdGenerator idGenerator, Miner miner){
        this.status = Status.GENERATING_IDS;
        this.idGenerator = idGenerator;
        this.miner = miner;
        this.repositoryName = repositoryName;
        getConfiguration();
        try{
            this.neo4jDatabaseController = new Neo4jDatabaseController(this.uri, this.username, this.password);
            this.neo4jDatabaseController.initializeDatabase();

            this.currentThread = new Thread(() -> {
                try{
                    List<String> ids = idGenerator.generateIds();

                    if(ids != null && ids.size() > 0){
                        this.status = Status.MINING;
                        this.minerScheduler = new MinerScheduler(ids, this.miner, this.neo4jDatabaseController, false);
                        this.minerScheduler.runProcess(this);
                    } else {
                        logger.error("=> Mining of PackageIds for Repository "+this.repositoryName+" failed <=");
                        this.status = Status.ERROR;
                    }
                } catch(Exception ex){
                    logger.error("Uncaught exception while mining", ex);
                    ExceptionLogger.add(ex, Task.class.getName());
                    this.status = Status.ERROR;
                }

            });

            this.currentThread.start();
        } catch (Exception ex){
            logger.error("Could not connect to Database at " + this.uri, ex);
            ExceptionLogger.add(ex, this.getClass().getName());
            this.status = Status.ERROR;
        }
    }

    public void taskUpdateCommand(String repositoryName, RepositoryController.IdGenerator idGenerator, Miner miner){
        this.status = Status.GENERATING_IDS;
        this.idGenerator = idGenerator;
        this.miner = miner;
        this.repositoryName = repositoryName;
        getConfiguration();

        try {
            this.neo4jDatabaseController = new Neo4jDatabaseController(this.uri, this.username, this.password);
            this.neo4jDatabaseController.initializeDatabase();

            logger.info("Building index of artifacts available. This might take a while...");
            Set<String> allRepoArtifactIds = neo4jDatabaseController.getAllArtifactIds();
            logger.info("Got " + allRepoArtifactIds.size() + " artifacts currently in db");

            miner.enableUpdateMode(allRepoArtifactIds);

            this.currentThread = new Thread(() -> {
                try{
                    logger.info("Generating package ids...");
                    List<String> ids = idGenerator.generateIds();

                    if(ids != null && ids.size() > 0){
                        logger.info("Got " + ids.size() + " package ids.");
                        this.status = Status.MINING;
                        this.minerScheduler = new MinerScheduler(ids, this.miner, this.neo4jDatabaseController, true);
                        this.minerScheduler.runProcess(this);
                    } else {
                        logger.error("=> Mining of PackageIds for Repository "+this.repositoryName+" failed <=");
                        this.status = Status.ERROR;
                    }
                } catch(Exception ex){
                    logger.error("Uncaught exception while updating", ex);
                    ExceptionLogger.add(ex, Task.class.getName());
                    this.status = Status.ERROR;
                }

            });

            this.currentThread.start();
        } catch (Exception x){
            logger.error("Error initializing database connection", x);
            ExceptionLogger.add(x, getClass().getName());
            this.status = Status.ERROR;
        }

    }


    /**
     * Function to start 'Export Ids' Command
     * @param repositoryName name of the repository of task
     * @param idGenerator implementation of IdGenerator interface
     */
    public void taskExportIdsCommand(String repositoryName, RepositoryController.IdGenerator idGenerator){
        this.idGenerator = idGenerator;
        this.repositoryName = repositoryName;
        Properties props = System.getProperties();
        this.repositoryName= props.getOrDefault("dgm.repo","npm").toString();
        getConfiguration();
        this.status = Status.EXPORTING;
        this.currentThread = new Thread(()->{
            List<String> ids = this.idGenerator.generateIds();
            try {
                File file = new File(this.repositoryName + "_ids.txt");
                FileWriter fw = new FileWriter(file);
                BufferedWriter bw = new BufferedWriter(fw);
                for(int i = 0; i < ids.size(); i++){
                    bw.write(ids.get(i));
                    if(i < ids.size() - 1) bw.newLine();
                }
                bw.close();
                fw.close();
                logger.info("=> Ids exported to file " + this.repositoryName + "_ids.txt <=");
            } catch (IOException e) {
                ExceptionLogger.add(e,this.getClass().getName());
            }
            this.status = Status.FINISHED;
        });
        this.currentThread.start();
    }

    /**
     * Function to start 'Parse' Command
     */
    public void taskParseDependenciesCommand(){
        this.neo4JLinkageParser = new Neo4jLinkageParser(this, getNewNeo4jDatabaseController());
        this.status = Status.PARSING;
        this.currentThread = new Thread(()->{
            neo4JLinkageParser.parseLinkage();
        });
        this.currentThread.start();
    }

    /**
     * Function to start 'Status' Command
     */
    public void taskStatusCommand(){
        switch (this.status){
            case MINING -> this.minerScheduler.printPercentage();
            case GENERATING_IDS -> print("== Package Ids of Repository "+this.repositoryName+" are getting mined ==");
            case ERROR -> print("== Something went wrong, start a new Task ==");
            case WAITING -> print("== Task is waiting, use command 'help' to get help ==");
            case PARSING -> this.neo4JLinkageParser.printPercentage();
            case FINISHED -> this.minerScheduler.printReport();
            case EXPORTING -> print("== Package Ids of Repository "+this.repositoryName+" are getting exported ==");
            case DELETING -> print("== Currently deleting all nodes and edges of Database ==");
        }
    }

    /**
     * Function to start 'Stop' Command
     */
    public void taskStopCommand(){
        switch (this.status){
            case MINING -> this.minerScheduler.killProcess();
            case GENERATING_IDS, EXPORTING,DELETING -> this.currentThread.interrupt();
            case ERROR -> print("== Something went wrong, start a new Task ==");
            case WAITING -> print("== Task is waiting, nothing to stop ==");
            case PARSING -> this.neo4JLinkageParser.stop();
            case FINISHED -> print("== Task is finished, nothing to stop ==");
        }
        this.status = Status.WAITING;
    }

    /**
     * Function to start 'Delete' Command
     */
    public void taskDeleteCommand(){
        this.status = Status.DELETING;
        this.currentThread = new Thread(()->{
            InputStreamReader inputStreamReader = new InputStreamReader(System.in);
            BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
            Neo4jDatabaseController neo4jDatabaseController = getNewNeo4jDatabaseController();
            logger.info("Are you sure you want to delete all data of the Neo4j Database?");
            logger.info("=> Type 'yes' to confirm or 'no' to exit");
            try {
                String confirmation = bufferedReader.readLine().toLowerCase();
                if (confirmation.equals("yes")) {
                    neo4jDatabaseController.clearDatabase();
                    logger.info("All data deleted successfully");
                } else {
                    logger.info("Deletion cancelled");
                }
            } catch (IOException exception){
                ExceptionLogger.add(exception,this.getClass().getName());
            }
            this.status = Status.FINISHED;
        });
        this.currentThread.start();
    }

    /* Get functions */

    private void getConfiguration(){
        Properties props = System.getProperties();
        this.uri = props.getOrDefault("dgm.databaseaddress","bolt\\://0.0.0.0\\:7687").toString();
        this.username = props.getOrDefault("dgm.databaseusername","neo4j").toString();
        this.password = props.getOrDefault("dgm.databasepassword","neo4j").toString();
    }

    /**
     * Retrieves the current status of this task
     * @return Status
     */
    public Status getStatus(){
        return this.status;
    }

    /**
     * Sets the current status for this task
     * @param status Status to set
     */
    public void setStatus(Status status){
        this.status = status;
    }

    /**
     * Get the name of the repository this task is working on
     * @return Repository name
     */
    public String getRepositoryName(){
        return this.repositoryName;
    }


    public void cleanup(){
        if(this.minerScheduler != null) minerScheduler.killProcess();
        if(this.currentThread != null) currentThread.stop();
        if(this.neo4jDatabaseController != null && !this.neo4jDatabaseController.isClosed) neo4jDatabaseController.close();
        if(this.neo4JLinkageParser != null) neo4JLinkageParser.stop();
    }

}

