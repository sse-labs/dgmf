package Repositories.PyPi;

import java.io.IOException;
import java.net.URI;

import Application.ExceptionLogger;
import Repositories.RepositoryController;
import org.json.XML;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import static Utilities.GlobalUtilities.*;


/**
 * Id generator implementation for PyPi. Uses the PyPi HTTP API to retrieve package names.
 */
public class PyPiIdGenerator implements RepositoryController.IdGenerator {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final ArrayList<String> names;
    private int limit;
    private final int offset;

    /** Constructor of the Name Generator, gets a limit and offset for the list of names to generate */
    public PyPiIdGenerator(){
        Properties props = System.getProperties();
        this.limit = Integer.parseInt(props.getOrDefault("dgm.limit","0").toString());
        this.offset = Integer.parseInt(props.getOrDefault("dgm.offset","0").toString());
        this.names = new ArrayList<>();
    }


    @Override
    public List<String> generateIds() {
        long startTime = System.currentTimeMillis();
        logger.info("== Mining of PyPi Package names ==");
        try {
            HttpClient client = HttpClient.newHttpClient();
            String endpoint = "https://pypi.org/simple/";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(endpoint))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            JSONArray responseBody = XML.toJSONObject(response.body()
                            .substring(0, 31) + response.body().substring(91))
                    .getJSONObject("html")
                    .getJSONObject("body")
                    .getJSONArray("a");
            if (limit == 0) {
                limit = responseBody.length();
            }
            for (int i = offset; (i < responseBody.length()) && i < (limit + offset); i++) {
                JSONObject pack = responseBody.getJSONObject(i);
                names.add(pack.get("content").toString());
            }
        }
        catch (InterruptedException ex){
            //Do nothing
            ExceptionLogger.add(ex,this.getClass().getName());
        } catch (URISyntaxException | IOException exception){
            ExceptionLogger.add(exception,this.getClass().getName());
            exception.printStackTrace();
        }
        long endTime = System.currentTimeMillis();
        long generatingTime = Math.round(((float)(endTime - startTime) / 1000) / 60);
        logger.info("=> Time for mining of the " + this.limit + " package names: " + generatingTime + " minutes <=");
        return names;
    }
}