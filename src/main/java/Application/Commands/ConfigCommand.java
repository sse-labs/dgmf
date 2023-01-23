package Application.Commands;

import Application.ExceptionLogger;
import Application.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Properties;

import static Utilities.GlobalUtilities.*;
import static Utilities.CommandUtilities.*;

/**
 * Command implementation to display or store the current state of the application configuration
 */
public class ConfigCommand implements Command {
    private static final String[] possibleCommands = new String[]{"config","configuration"};

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Override
    public boolean canExecute(String[] cliParams){
        return Arrays.asList(possibleCommands).contains(cliParams[0]);
    }

    @Override
    public Task execute(Task task, String[] cliParams) {
        int inputLength = cliParams.length;
        switch (inputLength) {
            case 1 -> printCurrentConfig();
            case 2 -> {
                if (cliParams[1].equals("save")) {
                    saveConfiguration();
                } else if (cliParams[1].equals("help")) {
                    print(configHelpString);
                } else {
                    print("Wrong parameter for 'config' command, use:");
                    print(this.getDescription());
                }
            }
            case 3 -> changeConfiguration(cliParams[1], cliParams[2]);
        }
        return task;
    }

    @Override
    public String getDescription() {
        return "- Command 'config': shows current configuration of the Dependency Graph Miner\n- Command 'config save': saves current configuration to configuration file\n- Command 'config help': shows possible configurations\n- Command 'config <key> <value>': sets configuration value for given key";
    }

    private void printCurrentConfig(){
        Properties props = System.getProperties();
        print("Current configuration:");
        props.forEach((k, v) -> {
            if(k.toString().startsWith("dgm.")){
                print(" "+k.toString().split("[.]")[1] + ":" + v);
            }
        });
    }

    private void changeConfiguration(String key, String value){
        Properties props = System.getProperties();
        if(checkProp("dgm."+key.toLowerCase(),value.toLowerCase())){
            props.setProperty("dgm."+key.toLowerCase(), value.toLowerCase());
            print("Property: "+key.toLowerCase()+" updated with value: "+value.toLowerCase());
        }
    }

    private void saveConfiguration() {
        Properties props = System.getProperties();
        try{
            Properties savingProps = new Properties();
            props.forEach((k, v) -> {
                if(k.toString().startsWith("dgm.")&&(checkProp(k.toString(),v.toString()))){
                    savingProps.setProperty(k.toString(),v.toString());
                }
            });
            savingProps.store(new FileOutputStream("system.properties"), null);
            print("Current configuration saved");
        } catch (IOException exception){
            ExceptionLogger.add(exception,"ConfigCommand");
        }
    }
}
