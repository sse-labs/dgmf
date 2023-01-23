package Utilities;

import Application.ExceptionLogger;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 *  Class providing basic HTTP utilities
 */
public class HttpUtilities {

    private static final Logger logger = LoggerFactory.getLogger(HttpUtilities.class);

    private static final HttpClient httpClient = HttpClient.newHttpClient();

    private static final int TimeoutMinutes = 5;

    /**
     * Retrieves the content of the given URL and parses it into a JSON object. Only success-responses (200) will be handled.
     * Returns null if an error occurred.
     * @param url URL to retrieve contents from
     * @return JSON object representation of the URL's contents, or null if an error occurred.
     */
    public static JSONObject getContentAsJSON(String url){
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .timeout(Duration.ofMinutes(TimeoutMinutes))
                    .uri(new URI(url))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if(response.statusCode() != 200) return null;
            else return new JSONObject(response.body());

        } catch(Exception ex){
            if(GlobalUtilities.isBoolPropertyEnabled("dgm.drop-http-errors"))
                logger.debug("Exception while downloading JSON: " + ex.getClass() + " -> " + ex.getMessage());
            else
                ExceptionLogger.add(ex, HttpUtilities.class.getName());

            return null;
        }
    }
}
