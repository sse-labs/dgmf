package Utilities;

import java.util.Properties;

/**
 * Global utilities for the entire application
 */
public final class GlobalUtilities {

    /** Help Function: printing into Console */
    public static void print(String a) {
        System.out.println(a);
    }

    public static boolean isBoolPropertyEnabled(String propKey){
        Properties p = System.getProperties();

        if(p.containsKey(propKey))
            return p.getProperty(propKey).equalsIgnoreCase("true");
        else return false;
    }

    /**
     * Enum for possible status of Task
     */
    public enum Status {
        WAITING,
        GENERATING_IDS,
        MINING,
        ERROR,
        PARSING,
        EXPORTING,
        FINISHED,
        DELETING
    }
}
