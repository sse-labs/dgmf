package Repositories.PyPi;

import Application.ExceptionLogger;
import Model.*;
import Model.Package;
import Repositories.Miner;
import Repositories.RepositoryController;
import Utilities.HttpUtilities;
import org.json.JSONArray;
import org.json.JSONObject;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Miner implementation for PyPi. Uses JSON HTTP API of PyPi to retrieve information on packages, artifacts and dependencies.
 */
public class PyPiMiner extends Miner {

    private final AtomicInteger timeoutCounter;
    private final AtomicInteger formatErrorCounter;

    private static final String baseUrl = "https://pypi.org/pypi/";

    /** Constructor of the MavenMiner */
    public PyPiMiner() {
        timeoutCounter = new AtomicInteger(0);
        formatErrorCounter = new AtomicInteger(0);
    }

    @Override
    public JSONObject minePackage(String packageName) {

        JSONObject o = HttpUtilities.getContentAsJSON(baseUrl + packageName + "/json");

        if(o == null) timeoutCounter.incrementAndGet();

        return o;
    }

    @Override
    public Package parsePackage(JSONObject p) {
        try {
            Package newPackage;
            if ((!p.isEmpty()) && (p.has("info"))) {
                JSONObject info = p.getJSONObject("info");
                String name = info.get("name").toString();
                name = name.replaceAll("[\"|\'|(|)]", "");
                newPackage = new Package(name, "pypi");
                if (info.has("version"))
                    newPackage.setCustomAttribute("latest",info.get("version").toString());
                ArrayList<Dependency> packageDependancy = new ArrayList<Dependency>();

                if (info.has("requires_dist") && (!info.isNull("requires_dist"))) {
                    JSONArray dependancys = info.getJSONArray("requires_dist");
                    for (int c = 0; c < dependancys.length(); c++) {
                        String[] dep = dependancys.get(c).toString().split("(;|\\(|=|<|>|:)");
                        Dependency newDependancy = new Dependency(dep[0].replaceAll(" ",""));
                        if (dep.length > 1){
                            if(dep[1].contains(" extra == ")) {
                                dep = dependancys.get(c).toString().split("=|'");
                                newDependancy.setCustomAttribute("type",dep[3]);
                            } else {
                                String versionString = dep[1].split("[)]")[0];
                                if(versionString.contains("python_version")){
                                    String versionRange = versionString.replaceAll("[python_version]","").replaceAll("\"","");
                                    Dependency newDependancyPyhon = new Dependency("Python", versionRange); //TODO: Why is this unused?
                                    packageDependancy.add(newDependancy);
                                } else {
                                    newDependancy.setVersionRange(versionString);
                                }
                            }
                            if((dep.length > 2)&&(dep[2].contains(" extra == "))) {
                                dep = dependancys.get(c).toString().split("=|'");
                                newDependancy.setCustomAttribute("type",dep[3]);
                            }
                        }
                        packageDependancy.add(newDependancy);

                    }
                }
                if (p.has("releases") && (!p.isNull("releases"))) {
                    JSONObject artifacts = p.getJSONObject("releases");
                    if (artifacts.names() != null) {
                        JSONArray versionNames = artifacts.names();
                        for (int c = 0; c < versionNames.length(); c++) {
                            String currentVersion = versionNames.get(c).toString();

                            if(!artifactAlreadyPresent(newPackage.getId(), currentVersion)){
                                Artifact newArtifact = new Artifact(currentVersion, name, "pypi");
                                for (Dependency dep:packageDependancy) {
                                    newArtifact.addDependency(dep);
                                }
                                newPackage.addArtifact(newArtifact);
                            }
                        }
                    }
                } else {
                    formatErrorCounter.incrementAndGet();
                }
                return newPackage;
            } else {
                formatErrorCounter.incrementAndGet();
                return null;
            }
        } catch (IndexOutOfBoundsException exception) {
            formatErrorCounter.incrementAndGet();
            ExceptionLogger.add(exception,this.getClass().getName());
        } catch (Exception exception) {
            ExceptionLogger.add(exception,this.getClass().getName());
            exception.printStackTrace();
        }
        return null;
    }

    @Override
    public int getFormatErrorCounter() {
        return formatErrorCounter.intValue();
    }

    @Override
    public int getRequestErrorCounter() {
        return timeoutCounter.intValue();
    }
}
