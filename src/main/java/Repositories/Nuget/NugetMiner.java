package Repositories.Nuget;

import Application.ExceptionLogger;
import Model.Artifact;
import Model.Dependency;
import Model.Package;
import Repositories.Miner;
import Utilities.HttpUtilities;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Miner implementation for Nuget.org. Parses package creation events via HTTP as JSON and extracts all relevant information
 * for package, artifacts and dependencies.
 */
public class NugetMiner extends Miner {


    private final AtomicInteger timeoutCounter;
    private final AtomicInteger formatErrorCounter;

    public NugetMiner(){
        timeoutCounter = new AtomicInteger(0);
        formatErrorCounter = new AtomicInteger(0);
    }

    @Override
    public JSONObject minePackage(String packageId) {
        JSONObject resp = HttpUtilities.getContentAsJSON(packageId);

        if(resp == null) timeoutCounter.incrementAndGet();

        return resp;
    }

    @Override
    public Package parsePackage(JSONObject p) {
        Package pObj = new Package(p.getString("id"), "nuget");

        Artifact a = jsonToArtifact(p);
        if(a != null)  pObj.addArtifact(a);

        return pObj;
    }

    private Artifact jsonToArtifact(JSONObject artifactJSON){

        try{

            String version = artifactJSON.getString("version");
            String packageName = artifactJSON.getString("id");

            if(artifactAlreadyPresent(packageName, version)) return null;

            Artifact aObj = new Artifact(version, packageName, "nuget");
            aObj.setCustomAttribute("authors", artifactJSON.getString("authors"));
            aObj.setCustomAttribute("published", artifactJSON.getString("published"));

            if(artifactJSON.has("dependencyGroups")){
                JSONArray dependencyGroupsJSON = artifactJSON.getJSONArray("dependencyGroups");

                for(int i = 0; i < dependencyGroupsJSON.length(); i++){
                    JSONObject currentGroupJSON = dependencyGroupsJSON.getJSONObject(i);
                    JSONArray groupDependenciesJSON = currentGroupJSON.getJSONArray("dependencies");

                    for(int j = 0; j < groupDependenciesJSON.length(); j++){
                        JSONObject dependencyJSON = groupDependenciesJSON.getJSONObject(j);
                        String targetPackage = dependencyJSON.getString("id");
                        String targetVersionRange = dependencyJSON.getString("range");
                        String dependencyType = dependencyJSON.getString("@type");

                        if(dependencyType.equals("PackageDependency")){
                            aObj.addDependency(new Dependency(targetPackage, targetVersionRange));
                        } else {
                            System.out.println("Unknown package dependency type: " + dependencyType);
                        }
                    }
                }
            }

            return aObj;
        } catch (JSONException jx){
            ExceptionLogger.add(jx, getClass().getName());
            formatErrorCounter.incrementAndGet();
            return null;
        }
    }

    @Override
    public int getFormatErrorCounter(){
        return formatErrorCounter.intValue();
    }

    @Override
    public int getRequestErrorCounter(){
        return timeoutCounter.intValue();
    }
}
