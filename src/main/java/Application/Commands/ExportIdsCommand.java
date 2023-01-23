package Application.Commands;

import Application.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Properties;

import static Repositories.RepositoryController.getIdGenerator;
import static Utilities.GlobalUtilities.*;

/**
 * Command implementation that exports the list of package identifiers for a given repository to file
 */
public class ExportIdsCommand implements Command {
    private static final String[] possibleCommands = new String[]{"export","ids","exportids"};

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Override
    public boolean canExecute(String[] cliParams){
        return Arrays.asList(possibleCommands).contains(cliParams[0]);
    }

    @Override
    public Task execute(Task task, String[] cliParams) {
        if((task.getStatus()!=Status.WAITING)&&(task.getStatus()!=Status.FINISHED)){
            logger.warn("Current task with repository " + task.getRepositoryName() + " running. Stop task to export package Ids a new one");
        } else {
            int inputLength = cliParams.length;
            String repositoryName;
            if (inputLength <= 1) {
                Properties props = System.getProperties();
                repositoryName = props.getOrDefault("dgm.repo","npm").toString();
            } else {
                repositoryName = cliParams[1];
            }
            task.taskExportIdsCommand(repositoryName, getIdGenerator(repositoryName, false));
            logger.info("Initialize Export of Ids with repository " + task.getRepositoryName());
            if (task.getStatus() != Status.ERROR)
                logger.info("Started export with repository " + task.getRepositoryName());
        }
        return task;
    }

    @Override
    public String getDescription() {
        return "- Command 'ids' <repository>: exports Identifier of packages to .txt file";
    }
}
