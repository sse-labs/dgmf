package Utilities;

import Application.ExceptionLogger;
import Database.Neo4jDatabaseController;
import Repositories.RepositoryController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Properties;

import static Utilities.GlobalUtilities.*;

/**
 * Utility class for command implementations, provides functionality to parse arguments from properties and configuration
 * files.
 */
public final class CommandUtilities {

    private static final Logger logger = LoggerFactory.getLogger(CommandUtilities.class);

    /**
     * Function to create and return new Neo4jDatabaseController
     * @return new Neo4jDatabaseControllers
     */
    public static Neo4jDatabaseController getNewNeo4jDatabaseController(){
        Properties props = System.getProperties();
        String uri = props.getOrDefault("dgm.databaseaddress","bolt\\://0.0.0.0\\:7687").toString();
        String username = props.getOrDefault("dgm.databaseusername","neo4j").toString();
        String password = props.getOrDefault("dgm.databasepassword","neo4j").toString();
        return new Neo4jDatabaseController(uri, username, password);
    }

    /**
     * Function to load dgm configurations from system.properties file
     */
    public static void loadConfiguration(){
        Properties loadingProps = new Properties();
        try{
            Properties props = System.getProperties();
            loadingProps.load(new FileInputStream("system.properties"));
            loadingProps.forEach((k, v) -> {
                if((k.toString().startsWith("dgm."))&&(checkProp(k.toString(),v.toString()))){
                    props.setProperty(k.toString(),v.toString());
                }
            });
            logger.info("Loaded configuration from system.properties");
        } catch (IOException exception){
            ExceptionLogger.add(exception,"CommandUtilities");
            exception.printStackTrace();
        }
    }

    /**
     * Function to check configuration property of dgm configuration
     * @param key key of property
     * @param value value of property
     * @return true, if key and value of property are valid
     */
    public static boolean checkProp(String key, String value){
        try{
            switch (key) {
                case "dgm.limit", "dgm.offset", "dgm.parallel" -> {
                    int check = Integer.parseInt(value);
                    if (check >= 0) {
                        return true;
                    }
                    logger.warn("Wrong configuration input, " + configHelpString);
                    return false;
                }
                case "dgm.linkage" -> {
                    if ((value.equals("pp") || value.equals("ap") || value.equals("aa"))) {
                        return true;
                    }
                    logger.warn("Wrong configuration input, " + configHelpString);
                    return false;
                }
                case "dgm.repo" -> {
                    if (Arrays.asList(RepositoryController.repositoryList).contains(value)) {
                        return true;
                    }
                    logger.warn("Wrong configuration input, " + configHelpString);
                    return false;
                }
                case "dgm.databaseaddress", "dgm.databaseusername", "dgm.databasepassword" ->{
                    return true;
                }
                case "dgm.import-ids", "dgm.npm.use-github-ids", "dgm.drop-http-errors", "dgm.interactive-shell" -> {
                    if(value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")) return true;
                    logger.warn("Invalid value for '" + key + "', boolean values expected. Supported: 'true', 'false'");
                    return false;
                }
                case "dgm.id-file" -> {
                    return !value.isBlank();
                }
                default -> {
                    logger.warn("Wrong configuration input, " + configHelpString);
                    return false;
                }
            }
        } catch (NumberFormatException nfe) {
            logger.warn("Wrong configuration input, "+configHelpString);
            return false;
        }

    }

    /**
     * Help message for usage of the configuration commands
     */
    public final static String configHelpString = "Possible Configurations:" +
            "\n - repo <"+Arrays.asList(RepositoryController.repositoryList)+"> (sets name of repository)" +
            "\n - limit <Integer> (sets limit for Id Generator)" +
            "\n - offset <Integer> (sets offset for Id Generator)" +
            "\n - parallel <Integer> (sets number of parallel threads for the miner)" +
            "\n - linkage <pp, ap, aa> (defines precision for dependencies)" +
            "\n            pp: package -[dependency]-> package" +
            "\n            ap: artefact -[dependency]-> package" +
            "\n            aa: artefact -[dependency]-> artefact" +
            "\n - databaseAddress <String> (Address for Neo4j Database)" +
            "\n - databaseUsername <String> (Username for Neo4j Database)" +
            "\n - databasePassword <String> (Password for Neo4j Database)";
}
