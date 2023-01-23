package Application;

import Application.Commands.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import static Utilities.GlobalUtilities.*;
import static Utilities.CommandUtilities.*;

/**
 * Main entry point for the DGMF application
 */
public class InteractiveConsoleApplication {

    private static final Logger logger = LoggerFactory.getLogger(InteractiveConsoleApplication.class);


    public static void main(String[] args) throws IOException {

        /* Create list of commands and load configuration from system.properties */
        ArrayList<Command> commandList = new ArrayList<>();
        commandList.add(new StartCommand());
        commandList.add(new LinkageCommand());
        commandList.add(new StopCommand());
        commandList.add(new StatusCommand());
        commandList.add(new ConfigCommand());
        commandList.add(new LogCommand());
        commandList.add(new ExportIdsCommand());
        commandList.add(new DeleteCommand());
        commandList.add(new UpdateCommand());
        loadConfiguration();

        /* Create new Task and print welcome message */
        Task currentTask = new Task();
        logger.info("Dependency Graph Miner started. Type 'help' to see usable commands.");

        /* Read User Input */
        InputStreamReader inputStreamReader = new InputStreamReader(System.in);
        BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
        while (true) {
            if (bufferedReader.ready()) {
                try {
                    /* Parse user input to lowercase */
                    String[] input = bufferedReader.readLine().toLowerCase().split(" ");

                    /* Command 'Help' */
                    if (input.length == 0 || input[0].equals("help")) {
                        logger.info("Use one of the following commands:");
                        for (Command command : commandList) {
                            logger.info(command.getDescription());
                        }
                        for (String description : generalCommandsDescription) {
                            logger.info(description);
                        }
                    /* Command 'Exit' */
                    } else if (input[0].equals("exit")) {
                        System.exit(0);

                    /* Rest of the Commands */
                    } else {
                        for (Command command : commandList) {
                            if (command.canExecute(input)) {
                                currentTask = command.execute(currentTask, input);
                                break;
                            }
                        }
                    }
                } catch (IOException exception) {
                    ExceptionLogger.add(exception,"ConsoleApplication");
                    bufferedReader.close();
                }
            } else {
                try {
                    Thread.sleep(2500);
                } catch (java.lang.InterruptedException exception){
                    ExceptionLogger.add(exception,"ConsoleApplication");
                    exception.printStackTrace();
                }
            }
        }
    }


    /**
     * Command descriptions of commands 'Help' and 'Exit'
     */
    final static String[] generalCommandsDescription = {
            "- Command 'help': shows possible commands of the Dependency Graph Miner",
            "- Command 'exit': stops the Dependency Graph Miner"
    };
}
