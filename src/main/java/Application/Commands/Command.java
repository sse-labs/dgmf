package Application.Commands;

import Application.Task;

/**
 * Common interface for all commands supported by the DGMF
 */
public interface Command {

    /**
     * Check whether this command can be executed with the given set of CLI parameters
     * @param cliParams CLI parameters passed to this application invocation
     * @return True if command can be executed, false otherwise
     */
    boolean canExecute(String[] cliParams);

    /**
     * Executes this command in the context of the given task, with the given CLI parameters
     * @param task Current task object maintained by the application
     * @param cliParams CLI parameters passed to this application invocation
     * @return Task object after the execution has been started
     */
    Task execute(Task task, String[] cliParams);

    /**
     * Retrieves a textual description of this command in order to display CLI help information
     * @return String description of this command
     */
    String getDescription();
}
