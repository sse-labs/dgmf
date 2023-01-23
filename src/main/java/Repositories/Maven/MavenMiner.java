package Repositories.Maven;

import Application.ExceptionLogger;
import Model.*;
import Model.Package;
import Repositories.Miner;
import Repositories.RepositoryController;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.XML;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static Utilities.GlobalUtilities.*;

/**
 * Miner implementation for the Maven Central repository. Makes use of the maven-metadata.xml files that can be obtained
 * via GA-Tuples in order to list all version numbers for a given package. Builds artifact information based on POM
 * file contents.
 */
public class MavenMiner extends Miner {

    private static final String RepositoryName = "maven";

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final AtomicInteger timeoutCounter;
    private final AtomicInteger formatErrorCounter;

    /**
     * Create a new instance of the Maven Central miner.
     */
    public MavenMiner() {
        timeoutCounter = new AtomicInteger(0);
        formatErrorCounter = new AtomicInteger(0);
    }

    /** Fixed Http client */
    private static final ExecutorService executorService = Executors.newCachedThreadPool();
    private static final HttpClient httpClient = HttpClient.newBuilder()
            .executor(executorService)
            .version(HttpClient.Version.HTTP_2)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Override
    public JSONObject minePackage(String packageName) {
        try {
            CompletableFuture<String> result = httpClient.sendAsync(
                            HttpRequest.newBuilder(URI.create("https://repo1.maven.org/maven2/"
                                            + packageName + "/maven-metadata.xml"))
                                    .build(),
                            HttpResponse.BodyHandlers.ofString())
                    .thenApplyAsync((resp) -> {
                        int status = resp.statusCode();
                        if (status != 200) {
                            return "no Metadata";
                        } else {
                            return resp.body();
                        }
                    });
            String response = result.get();
            if (Objects.equals(response, "no Metadata"))
                return minePomFilesWithoutMetadata(packageName);

            if ((!Objects.equals(response, "")) && (response != null)) {
                JSONObject metadata = XML.toJSONObject(response);

                String ga = metadata.getJSONObject("metadata").get("groupId") + ":" + metadata.getJSONObject("metadata").get("artifactId");

                JSONObject versions = metadata.getJSONObject("metadata").getJSONObject("versioning").getJSONObject("versions");
                JSONArray pomFiles = new JSONArray();
                if (versions.has("version") && !versions.isNull("version")) {
                    try {
                        JSONArray versionsArray = versions.getJSONArray("version");
                        for (int i = 0; i < versionsArray.length(); i++) {
                            if(versionsArray.get(i)!=null) {
                                String currentVersion = versionsArray.get(i).toString();

                                if(!artifactAlreadyPresent(RepositoryName + ":" + ga , currentVersion)){
                                    JSONObject pomJson = minePomFile(versionsArray.get(i).toString(), packageName, true);
                                    if (pomJson != null)
                                        pomFiles.put(pomJson);
                                }
                            }
                        }
                    } catch (org.json.JSONException exception) {
                        String versionName = versions.get("version").toString();
                        if(!artifactAlreadyPresent(RepositoryName + ":" + ga , versionName)) {
                            JSONObject pomJson = minePomFile(versionName, packageName, true);
                            if (pomJson != null)
                                pomFiles.put(pomJson);
                        }
                    }
                }
                metadata.append("pomfiles", pomFiles);
                return metadata;
            } else {
                formatErrorCounter.incrementAndGet();
                return null;
            }
        } catch (JSONException | IllegalArgumentException exception) {
            formatErrorCounter.incrementAndGet();
            ExceptionLogger.add(exception,this.getClass().getName());
            return null;
        } catch (Exception ex) {
            logger.error("Unexpected error while mining Maven package", ex);
            ExceptionLogger.add(ex,this.getClass().getName());
            return null;
        }
    }

    /** Function for the backup solution if maven package doesn't have a maven-metadata.xml */
    private JSONObject minePomFilesWithoutMetadata(String packageName) {
        JSONArray pomFiles = new JSONArray();
        JSONObject metadata = new JSONObject();
        String[] packNameSplit = packageName.split("/");
        String artifactId = "";
        int i = 1;
        while (i < packNameSplit.length) {
            artifactId = artifactId + ":" + packNameSplit[i];
            i++;
        }
        metadata.put("metadata", new JSONObject().put("groupId", packNameSplit[0]).put("artifactId", artifactId));
        try {
            CompletableFuture<String> result = httpClient.sendAsync(
                            HttpRequest.newBuilder(URI.create("https://repo1.maven.org/maven2/" + packageName + "/"))
                                    .build(),
                            HttpResponse.BodyHandlers.ofString())
                    .thenApplyAsync((resp) -> {
                        int status = resp.statusCode();
                        if (status == 200) {
                            return resp.body();
                        } else {
                            return "";
                        }
                    });
            String response = result.get();
            String[] responseArray = response.split("<a href=\"");
            int pointer = 0;
            while (pointer + 1 < responseArray.length) {
                pointer++;
                String version = responseArray[pointer].split("/\">")[0];
                if (checkString(version) && !artifactAlreadyPresent(RepositoryName + ":" + packNameSplit[0]+ ":" + artifactId, version)) {
                    pomFiles.put(minePomFile(version, packageName, false));
                }
                pointer++;
            }
        } catch (org.json.JSONException | java.lang.StringIndexOutOfBoundsException ex) {
            logger.warn("Invalid package format", ex);
            ExceptionLogger.add(ex,this.getClass().getName());
        } catch (Exception ex) {
            ex.printStackTrace();
            ExceptionLogger.add(ex,this.getClass().getName());
        }
        metadata.append("pomfiles", pomFiles);
        return metadata;
    }

    /** Function to mine the .pom file for more metadata of the maven package */
    private JSONObject minePomFile(String versionName, String packageName, Boolean countTimeouts) {
        try {
            if (versionName.contains("$")) {
                return null;
            }
            String[] artifact = packageName.split("/");
            CompletableFuture<String> result = httpClient.sendAsync(
                            HttpRequest.newBuilder(URI.create("https://repo1.maven.org/maven2/" + packageName
                                            + "/" + versionName + "/" + artifact[artifact.length - 1]
                                            + "-" + versionName + ".pom"))
                                    .build(),
                            HttpResponse.BodyHandlers.ofString())
                    .thenApplyAsync((resp) -> {
                        int status = resp.statusCode();
                        if (status == 404) {
                            if(countTimeouts)
                                if(versionName.matches("(.*)(\\d){8}.(\\d){6}(-(\\d){0,2})?") && !packageName.contains("jetty")){
                                    String newVersionName = versionName.substring(0,versionName.length()-18);
                                    minePomFile(newVersionName,packageName,true);
                                }
                                else {
                                    //timedoutCounter++;
                                }
                            return null;
                        } else {
                            return resp.body();
                        }
                    });
            String response = result.get();
            try {
                return XML.toJSONObject(response);
            } catch (NullPointerException | IllegalArgumentException | JSONException exception) {
                //Logger.add(exception,this.getClass().getName());
                return null;
            }
        } catch (IllegalArgumentException ex){
            //Logger.add(ex,this.getClass().getName());
            return null;
        } catch (Exception exception) {
            logger.error("Error while mining POM file", exception);
            ExceptionLogger.add(exception,this.getClass().getName());
            return null;
        }
    }

    @Override
    public Package parsePackage(JSONObject p) {
        try {
            Package newPackage;
            JSONArray pomFiles = p.getJSONArray("pomfiles").getJSONArray(0);
            JSONObject metadata = p.getJSONObject("metadata");
            String packageName = metadata.get("groupId") + ":" + metadata.get("artifactId");
            newPackage = new Package(packageName, "maven");
            if (metadata.has("version"))
                newPackage.setCustomAttribute("latest",metadata.get("version").toString());

            for (int i = 0; (i < pomFiles.length())&&(!pomFiles.isNull(i)); i++) {
                JSONObject pomfile = pomFiles.getJSONObject(i).getJSONObject("project");
                String version = "";
                if (pomfile.has("version"))
                    version = pomfile.get("version").toString();
                else if(pomfile.has("parent")){
                    version = pomfile.getJSONObject("parent").get("version").toString();
                }
                Artifact artifact = new Artifact(version, packageName, "maven");
                if (pomfile.has("dependencies") && !(pomfile.get("dependencies") == "")) {
                    try {
                        JSONObject dependency = pomfile.getJSONObject("dependencies").getJSONObject("dependency");
                        artifact.addDependency(parseDependency(dependency, metadata));
                    } catch (JSONException ex) {
                        try {
                            JSONArray dependency = pomfile.getJSONObject("dependencies").getJSONArray("dependency");
                            for (Object dep : dependency)
                                artifact.addDependency(parseDependency((JSONObject) dep, metadata));
                        } catch (JSONException ex1) {
                            //Logger.add(ex1,this.getClass().getName());
                            formatErrorCounter.incrementAndGet();
                        }
                    }
                }
                newPackage.addArtifact(artifact);
            }
            return newPackage;
        } catch (JSONException ex) {
            ExceptionLogger.add(ex,this.getClass().getName());
            formatErrorCounter.incrementAndGet();
        } catch (Exception exception) {
            ExceptionLogger.add(exception,this.getClass().getName());
        }
        return null;
    }

    /** Help Function: parse the description of a maven dependency */
    private Dependency parseDependency(JSONObject depJson, JSONObject metadata) throws JSONException {
        String groupId = depJson.get("groupId").toString();
        if (Objects.equals(groupId, "${pom.groupId}")) {
            groupId = metadata.get("groupId").toString();
        }
        String artifactId = depJson.get("artifactId").toString();
        String version = "";
        if (depJson.has("version")) {
            version = depJson.get("version").toString();
            if ((Objects.equals(version, "${pom.version}") || Objects.equals(version, "${pom.currentVersion}"))&&(metadata.has("version"))) {
                version = metadata.get("version").toString();
            }
        }
        Dependency newDependency = new Dependency(groupId + ":" + artifactId, version);

        if (depJson.has("optional") && Objects.equals(depJson.get("optional").toString(), "true")) {
            newDependency.setCustomAttribute("type","optional");
        }
        return newDependency;
    }

    /** Help Function: check if path consists a folder of a file */
    private boolean checkString(String a){
        return (!a.contains("..")) && (!a.contains(".xml"))
                && (!a.contains(".txt")) && (!a.contains(".md5"))
                && (!a.contains(".shal")) && (!a.contains(".jar"))
                && (!a.contains(".aar")) && (!a.contains(".asc"))
                && (!a.contains("tar")) && (!a.contains("zip"))
                && (!a.contains("gz")) && (!a.contains(".sha1"))
                && (!a.contains(".pom"));
    }

    /** Get functions */

    @Override
    public int getFormatErrorCounter() {
        return formatErrorCounter.intValue();
    }

    @Override
    public int getRequestErrorCounter() {
        return timeoutCounter.intValue();
    }
}
