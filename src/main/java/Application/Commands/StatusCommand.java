package Application.Commands;

import Application.Task;

import java.util.Arrays;

/**
 * Command implementation that prints the current status of the DGMF application
 */
public class StatusCommand implements Command {
    private static final String[] possibleCommands = new String[]{"status"};

    @Override
    public boolean canExecute(String[] cliParams){
        return Arrays.asList(possibleCommands).contains(cliParams[0]);
    }

    @Override
    public Task execute(Task task, String[] cliParams) {
        task.taskStatusCommand();
        return task;
    }

    @Override
    public String getDescription() {
        return "- Command 'status': prints status of current task";
    }
}
