package Application.Commands;

import Application.Task;

import java.util.Arrays;

import static Utilities.GlobalUtilities.*;

/**
 * Command implementation to stop the task that is currently being executed by the application
 */
public class StopCommand implements Command {
    private static final String[] possibleCommands = new String[]{"stop","stopped","break"};

    @Override
    public boolean canExecute(String[] cliParams){
        return Arrays.asList(possibleCommands).contains(cliParams[0]);
    }

    @Override
    public Task execute(Task task, String[] cliParams) {
        task.taskStopCommand();
        print("Task stopped");
        return new Task();
    }

    @Override
    public String getDescription() {
        return "- Command 'stop': stops the current task";
    }
}
