package Repositories.NPM;

import Application.ExceptionLogger;
import Model.*;
import Model.Package;
import Repositories.Miner;
import Utilities.HttpUtilities;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Miner implementation for the NPM registry. Processes JSON files in the registry via HTTP to extract package-,
 * artifact- and dependency-information.
 */
public class NpmMiner extends Miner {

    private final AtomicInteger timeoutCounter;
    private final AtomicInteger formatErrorCounter;

    private static final String baseUrl = "https://registry.npmjs.com/";

    /** Constructor of the NpmMiner */
    public NpmMiner() {
        timeoutCounter = new AtomicInteger(0);
        formatErrorCounter = new AtomicInteger(0);
    }

    @Override
    public JSONObject minePackage(String packageId) {
        JSONObject o = HttpUtilities.getContentAsJSON(baseUrl + packageId);

        if(o == null) timeoutCounter.incrementAndGet();

        return o;
    }

    @Override
    public Package parsePackage(JSONObject p) {
        Package newPackage;
        if (!p.isEmpty()) {
            try {
                String name = p.get("name").toString();
                name = name.replaceAll("[\"|\'|(|)]", "");
                newPackage = new Package(name, "npm");
                if (p.has("dist-tags") && p.getJSONObject("dist-tags").has("latest"))
                    newPackage.setCustomAttribute("latest",p.getJSONObject("dist-tags").get("latest").toString());

                if(!p.has("versions")) return newPackage;

                JSONObject artifacts = p.getJSONObject("versions");
                if (artifacts.names() != null) {
                    JSONArray versionNames = artifacts.names();
                    for (int c = 0; c < versionNames.length(); c++) {
                        String plainVersion = versionNames.get(c).toString();

                        // Only add artifact to package if it is not already present
                        if(!artifactAlreadyPresent(newPackage.getId(), plainVersion)){

                            JSONObject version = artifacts.getJSONObject(plainVersion);

                            Artifact newArtifact = new Artifact(plainVersion, name, "npm");
                            if (version.has("dependencies") && version.getJSONObject("dependencies").names() != null) {
                                JSONArray dependencies = version.getJSONObject("dependencies").names();
                                for (int i = 0; i < dependencies.length(); i++) {
                                    String depName = dependencies.get(i).toString();
                                    if (version.getJSONObject("dependencies").has(depName)) {
                                        String versionRange = version.getJSONObject("dependencies").get(depName).toString();
                                        Dependency dependency = new Dependency(depName, versionRange);
                                        newArtifact.addDependency(dependency);
                                    }
                                }
                            }
                            if (version.has("devDependencies") && version.getJSONObject("devDependencies").names() != null) {
                                JSONArray dependencies = version.getJSONObject("devDependencies").names();
                                for (int i = 0; i < dependencies.length(); i++) {
                                    String depName = dependencies.get(i).toString();
                                    if (version.getJSONObject("devDependencies").has(depName)) {
                                        String versionRange = version.getJSONObject("devDependencies").get(depName).toString();
                                        Dependency dependency = new Dependency(depName, versionRange);
                                        dependency.setCustomAttribute("type","devDependency");
                                        newArtifact.addDependency(dependency);
                                    }
                                }
                            }
                            newPackage.addArtifact(newArtifact);
                        }
                    }
                }
                return newPackage;
            } catch (Exception exception) {
                ExceptionLogger.add(exception,this.getClass().getName());
            }
        }

        formatErrorCounter.incrementAndGet();
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
