package Application.Commands;

import Application.ExceptionLogger;
import Application.Task;
import java.util.Arrays;
import static Utilities.GlobalUtilities.*;

public class LogCommand implements Command {
    private static final String[] possibleCommands = new String[]{"exceptionLog","logger","logs","log"};

    @Override
    public boolean canExecute(String[] cliParams){
        return Arrays.asList(possibleCommands).contains(cliParams[0]);
    }

    @Override
    public Task execute(Task task, String[] cliParams) {
        int inputLength = cliParams.length;
        switch (inputLength) {
            case 1 -> ExceptionLogger.getInstance().printAllLogs();
            case 2 -> {
                try{
                    int id = Integer.parseInt(cliParams[1]);
                    ExceptionLogger.getInstance().printLog(id);
                } catch (Exception ex) {
                    if(cliParams[1].equals("save")){
                        ExceptionLogger.getInstance().saveLogs();
                    } else {
                        print("- Id of Log has to be a Number");
                        ExceptionLogger.add(ex, "LogCommand");
                    }
                }
            }
        }
        return task;
    }

    @Override
    public String getDescription() {
        return "- Command 'logs': shows all logs of current run\n- Command 'logs save': saves logs in logfile\n- Command 'exceptionLog' <Id> shows detailed exceptionLog with given Id";
    }
}
