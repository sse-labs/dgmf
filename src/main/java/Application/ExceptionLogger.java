package Application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import static Utilities.GlobalUtilities.*;

/**
 * Class to persistently manage all exceptions that occur in the DGMF.
 */
public final class ExceptionLogger {

    private static ExceptionLogger _instance = null;
    private final Logger internalLogger = LoggerFactory.getLogger(this.getClass());

    private final List<ExceptionLog> logList = Collections.synchronizedList(new ArrayList<>());
    private final AtomicInteger currentId = new AtomicInteger(0);

    private ExceptionLogger(){}

    public static ExceptionLogger getInstance(){
        if(_instance == null) _instance = new ExceptionLogger();
        return _instance;
    }

    /**
     * Function checks if log already exists in Logger and adds to count or creates new entry
     * @param exception Exception of log
     * @param className Origin class name
     */
    public static void add(Exception exception, String className){
        getInstance().addInternal(exception, className);
    }

    public void addInternal(Exception exception, String className){

        synchronized (logList){
            Iterator<ExceptionLog> i = logList.iterator();

            boolean found = false;

            while(i.hasNext()){
                ExceptionLog current = i.next();
                if((current.getException().equals(exception)|| bothGoAwayExceptions(exception, current.getException()))
                        && current.getClassName().equals(className)){
                    current.addCount();
                    found = true;
                    break;
                }
            }

            if(!found) logList.add(new ExceptionLog(currentId.getAndIncrement(), className, exception));
        }
    }

    private boolean bothGoAwayExceptions(Exception a, Exception b){
        return a.getMessage() != null && b.getMessage() != null && a.getMessage().contains("GOAWAY") &&
                b.getMessage().contains("GOAWAY");
    }

    /**
     * Function to find log with id and print detailed log summary
     * @param id id of log to find
     */
    public void printLog(int id){
        if(id< logList.size()){
            internalLogger.info(logList.get(id).getDetailLog());
        } else {
            internalLogger.warn("No Log with id "+id+" found");
        }
    }

    /**
     * Function to print all summary messages of logs
     */
    public void printAllLogs(){
        if(logList.size()>0) {
            synchronized (logList){
                for (ExceptionLog exceptionLog : logList) {
                    internalLogger.info(exceptionLog.getSummary());
                }
            }
        } else {
            internalLogger.info("Exception logs are empty");
        }
    }

    /**
     * Function to save Logs within logfile in directory log
     */
    public void saveLogs() {
        try{
            internalLogger.debug("Start saving Logs ...");
            File directory = new File("logs");
            if (! directory.exists()){
                directory.mkdir();
            }

            String filename = "logs/logs " + new Date() + ".txt";
            PrintWriter pw = new PrintWriter(new FileOutputStream(filename));

            synchronized (logList){
                for (ExceptionLog exceptionLog : logList)
                    pw.println(exceptionLog.getDetailLog());
            }

            pw.close();
            internalLogger.info("Successfully wrote exception logs to " + filename);
        } catch (IOException exception){
            ExceptionLogger.add(exception,"Application.LogCommand");
        }
    }

    public int getSize(){
        return logList.size();
    }
}

