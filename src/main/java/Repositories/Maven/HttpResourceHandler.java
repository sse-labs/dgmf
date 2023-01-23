package Repositories.Maven;

import org.apache.maven.index.reader.ResourceHandler;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Http Implementation of ResourceHandler which enables the maven index reader to read the Maven Central HTTP index.
 */
public class HttpResourceHandler implements ResourceHandler {
    private final String url;
    private HttpResource resource;

    /**
     * Create a new HTTP Resource Handler for the given base URL.
     * @param url Base URL
     */
    public HttpResourceHandler(String url){
        this.url = url;
    }

    @Override
    public Resource locate(String name) {
        this.resource = new HttpResource(url+name);
        return this.resource;
    }

    @Override
    public void close() throws IOException {
        this.resource.close();
    }
}

/**
 * Http implementation of the Maven ResourceHandler's Resource interface.
 */
class HttpResource implements ResourceHandler.Resource {
    private final String url;
    private InputStream inStream;

    /**
     * Creates a new HTTP resource for the given URL.
     * @param url Resource URL
     */
    public HttpResource(String url){
        this.url = url;
    }

    @Override
    public InputStream read() throws IOException {
        HttpURLConnection http = (HttpURLConnection) new URL(url).openConnection();
        http.setRequestMethod("GET");
        http.setReadTimeout(7200000);
        http.setRequestProperty("User-Agent", "DependencyGraphMiner" );
        inStream = http.getInputStream();
        return inStream;
    }

    /**
     * Closes the HTTP Resource
     * @throws IOException In case the closing operation fails
     */
    public void close() throws IOException {
        inStream.close();
    }
}
