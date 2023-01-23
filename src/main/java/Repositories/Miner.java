package Repositories;

import Model.Package;
import org.json.JSONObject;

import java.util.Set;

public abstract class Miner {

    private Set<String> existingArtifactIds = null;

    /**
     * Function to mine metadata of a software package from the repositories HTTP API as JSON object
     * @param packageId given package id
     * @return metadata as JSON object
     */
    public abstract JSONObject minePackage(String packageId);
    /**
     * Function to parse a metadata JSON object into a package object
     * @param p metadata as JSON object
     * @return metadata as package object
     */
    public abstract Package parsePackage(JSONObject p);

    /**
     * Default function to get an optional format error counter, can be implemented by Miner
     * @return always 0 in default function, only used in console output
     */
    public int getFormatErrorCounter(){
        return 0;
    }

    /**
     * Default function to get an optional request error counter, can be implemented by Miner
     * @return always 0 in default function, only used in console output
     */
    public int getRequestErrorCounter(){
        return 0;
    }

    /**
     * Shutdown any resources used by the miner
     */
    public void shutdown() {}

    public void enableUpdateMode(Set<String> knownArtifactIds){
        if(knownArtifactIds == null) throw new IllegalArgumentException("Need non-null list of existing artifact ids");
        existingArtifactIds = knownArtifactIds;
    }

    protected boolean artifactAlreadyPresent(String packageId, String version){
        if(existingArtifactIds == null) return false;

        String artifactId = packageId + ":" + version;

        return existingArtifactIds.contains(artifactId);
    }
}
