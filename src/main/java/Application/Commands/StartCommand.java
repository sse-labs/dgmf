package Application.Commands;

import Application.Task;
import Repositories.RepositoryController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Properties;

import static Utilities.GlobalUtilities.*;

/**
 * Command implementation to start the mining process for a given repository.
 */
public class StartCommand implements Command {
    private static final String[] possibleCommands = new String[]{"start", "run"};

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Override
    public boolean canExecute(String[] cliParams){
        return Arrays.asList(possibleCommands).contains(cliParams[0]);
    }

    @Override
    public Task execute(Task task, String[] cliParams) {
        if((task.getStatus()!=Status.WAITING)&&(task.getStatus()!=Status.FINISHED)){
            logger.warn("Current task with repository " + task.getRepositoryName() + " running. Stop task to start a new one");
        } else {
            logger.info("Initialise task");
            int inputLength = cliParams.length;
            String repositoryName;
            if (inputLength <= 1) {
                Properties props = System.getProperties();
                repositoryName = props.getOrDefault("dgm.repo","npm").toString();
            } else {
                repositoryName = cliParams[1];
            }
            task.taskStartCommand(repositoryName, RepositoryController.getIdGenerator(repositoryName, true), RepositoryController.getMiner(repositoryName));
            if (task.getStatus() != Status.ERROR)
                logger.info("Started task with repository " + task.getRepositoryName());
        }
        return task;
    }

    @Override
    public String getDescription() {
        return "- Command 'start <repository>(optional)': starts a new task with given repository";
    }
}
