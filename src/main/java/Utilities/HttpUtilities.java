package Utilities;

import Application.ExceptionLogger;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.zip.GZIPInputStream;

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

    public static JSONObject getGZIPContent(String url){
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .timeout(Duration.ofMinutes(TimeoutMinutes))
                    .uri(new URI(url))
                    .build();

            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

            if(response.statusCode() == 200){

                byte[] buffer = new byte[128];
                int len;

                String jsonContent;

                try (ByteArrayOutputStream bos = new ByteArrayOutputStream(); GZIPInputStream gzis = new GZIPInputStream(response.body())) {

                    while ((len = gzis.read(buffer)) != -1) {
                        bos.write(buffer, 0, len);
                    }

                    jsonContent = bos.toString(StandardCharsets.UTF_8);

                    return new JSONObject(jsonContent);
                }
            } else {
                throw new RuntimeException("Got non-success status code when downloading zipped data: " + response.statusCode());
            }



        } catch(Exception ex){
            if(GlobalUtilities.isBoolPropertyEnabled("dgm.drop-http-errors"))
                logger.debug("Exception while downloading JSON: " + ex.getClass() + " -> " + ex.getMessage());
            else
                ExceptionLogger.add(ex, HttpUtilities.class.getName());

            return null;
        }
    }


}
