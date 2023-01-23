package Application;

import Application.Commands.*;
import Utilities.CommandUtilities;
import Utilities.GlobalUtilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ConsoleApplication {

    private static final Logger logger = LoggerFactory.getLogger(ConsoleApplication.class);
    private static final List<Command> commandsAvailable = new ArrayList<>();

    static {
        commandsAvailable.add(new StartCommand());
        commandsAvailable.add(new DeleteCommand());
        commandsAvailable.add(new ExportIdsCommand());
        commandsAvailable.add(new LinkageCommand());
        commandsAvailable.add(new UpdateCommand());

        CommandUtilities.loadConfiguration();
    }

    public static void main(String[] args){
        if (args.length == 0 || args[0].equals("help")) {
            printUsage();
            System.exit(-1);
        }

        boolean interactiveShell = false;

        for(String arg: args){
            if (arg.equalsIgnoreCase("interactive")) {
                interactiveShell = true;
                break;
            }
        }

        interactiveShell = interactiveShell || GlobalUtilities.isBoolPropertyEnabled("dgm.interactive-shell");

        if(interactiveShell){ hostInteractiveShell(args.length); }
        else {
            Command command = findMatchingCommand(args);

            if(command == null){
                logger.error("Invalid arguments provided");
                printUsage();
                System.exit(1);
            }

            // Start async processing for command
            Task currentTask = command.execute(new Task(), args);

            // Wait for command to complete
            while(currentTask.getStatus() != GlobalUtilities.Status.FINISHED && currentTask.getStatus() != GlobalUtilities.Status.ERROR){
                try { Thread.sleep(1000); } catch (InterruptedException ix) { ix.printStackTrace(); }
            }

            currentTask.cleanup();

            // Print errors if necessary
            if(currentTask.getStatus() == GlobalUtilities.Status.ERROR) {
                logger.error("Execution aborted due to errors");
                ExceptionLogger.getInstance().printAllLogs();
            } else {
                logger.info("Execution of DGMF successful");
            }
        }
    }


    private static void printUsage(){
        logger.info("The following commands may be used with DGMF:");
        logger.info("- Command 'interactive': Starts interactive DGMF shell");
        for (Command command : commandsAvailable) {
            logger.info(command.getDescription());
        }
        logger.info("- Command 'help': Displays this help dialog");
    }

    private static void hostInteractiveShell(int numArgs){
        logger.info("-------------STARTING INTERACTIVE DGMF SHELL-------------");
        if(numArgs > 1) logger.warn("Additional arguments have been ignored for interactive mode.");
        try {
            InteractiveConsoleApplication.main(new String[]{});
        } catch (IOException iox){
            logger.info("Interactive shell terminated abruptly: " + iox.getMessage());
        }
    }

    private static Command findMatchingCommand(String[] args){
        for (Command command : commandsAvailable) {
            if (command.canExecute(args)) return command;
        }

        return null;
    }
}
