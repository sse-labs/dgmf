package Application.Commands;

import Application.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

import static Utilities.GlobalUtilities.*;

/**
 * Command implementation to delete all contents of the current database
 */
public class DeleteCommand implements Command {
    private static final String[] possibleCommands = new String[]{"delete","deleteAll"};

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Override
    public boolean canExecute(String[] cliParams){
        return Arrays.asList(possibleCommands).contains(cliParams[0]);
    }

    @Override
    public Task execute(Task task, String[] cliParams) {
        if((task.getStatus()!=Status.WAITING)&&(task.getStatus()!=Status.FINISHED)){
            logger.warn("Task with repository " + task.getRepositoryName() + " running. Stop running task before using 'delete'");
        } else {
            task.taskDeleteCommand();
        }
        return task;
    }

    @Override
    public String getDescription() {
        return "- Command 'delete': deletes all nodes and edges of Neo4j Database";
    }
}
