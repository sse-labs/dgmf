
package Application.Commands;

import Application.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

import static Utilities.GlobalUtilities.*;

/**
 * Command implementation that parses all Artifact-to-Package edges in the database into Artifact-to-Artifact edges
 */
public class LinkageCommand implements Command {
    private static final String[] possibleCommands = new String[]{"parse","parsedependencies","dependenciesparse","linkageparse","parseLinkage"};

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Override
    public boolean canExecute(String[] cliParams){
        return Arrays.asList(possibleCommands).contains(cliParams[0]);
    }

    @Override
    public Task execute(Task task, String[] cliParams) {
        if((task.getStatus()!=Status.WAITING)&&(task.getStatus()!=Status.FINISHED)){
            logger.warn("Current task with repository " + task.getRepositoryName() + " running. Stop task to parse dependencies");
        } else {
            task.taskParseDependenciesCommand();
        }
        return task;
    }

    @Override
    public String getDescription() {
        return "- Command 'parse': parses all dependencies from Linkage Package->Artifact to Linkage Artifact->Artifact";
    }
}
