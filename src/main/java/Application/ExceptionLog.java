package Application;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * Class for persistently logging exceptions that occurred during the mining process
 */
public class ExceptionLog {
    private final int id;
    private final Exception exception;
    private final Date time;
    private final String className;
    private int count;

    /**
     * Create a new ExceptionLog with the given id. The log represents the given exception that occurred in the given
     * class at the given time.
     *
     * @param id Id of this log entry
     * @param className Name of the class the exception was thrown in
     * @param exception The exception that was thrown
     * @param time Time the exception was thrown at
     */
    public ExceptionLog(int id, String className, Exception exception, Date time){
        this.id = id;
        this.className = className;
        this.exception = exception;
        this.time = time;
        this.count = 1;
    }

    /**
     * Create a new ExceptionLog with the given id. The logs represents the given exception that occurred in the given
     * class at the current point in time.
     *
     * @param id Id of this log entry
     * @param className Name of the class the exception was thrown in
     * @param exception The exception that was thrown
     */
    public ExceptionLog(int id, String className, Exception exception){
        this(id, className, exception, new Date());
    }

    /**
     * Creates a one-line string summary for this log entry
     * @return Log entry summary
     */
    public String getSummary(){
        return id + ": " + getMessage() + ", class: " + getClassName() + ", " + getCount() + " times";
    }

    /**
     * Creates a detailed, multi-line log message for this object
     * @return Detailed, formatted log message as string
     */
    public String getDetailLog(){
        StringBuilder builder = new StringBuilder("- Exception with id ");
        builder.append(id);
        builder.append(":\n  ");
        builder.append(getException().toString());
        builder.append("\n  from class: ");
        builder.append(getClassName());
        builder.append("\n  ");
        builder.append(getCount());
        builder.append(" times\n  Path");

        List<StackTraceElement> stackElems = Arrays.asList(exception.getStackTrace());
        Collections.reverse(stackElems);

        for(StackTraceElement ste : stackElems){
            builder.append(ste);
            builder.append(" -> ");
        }

        return builder.toString();
    }


    /**
     * Retrieve the exception for this log entry
     * @return Exception object
     */
    public Exception getException() {
        return exception;
    }

    /**
     * Retrieves the time that this exception was logged
     * @return Time object
     */
    public Date getTime() {
        return time;
    }

    /**
     * Retrieve name of the class that the exception was thrown in
     * @return Classname as String
     */
    public String getClassName() {
        return className;
    }

    /**
     * Retrieve number of times this exception was thrown.
     * @return Amount of times this exception was thrown
     */
    public int getCount() {
        return count;
    }

    /**
     * Increases the number of times this exception was thrown by one
     */
    public void addCount() {
        this.count++;
    }

    /**
     * Retrieves the exception message
     * @return Message as String
     */
    public String getMessage() {
        return exception.getMessage();
    }
}
