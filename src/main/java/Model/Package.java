package Model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Class of a Package object, which represents a software package */
public class Package {
    private final String id;
    private final String name;
    private final ArrayList<Artifact> artifactList;
    private final String repository;
    private final Map<String,String> customAttributes;

    /**
     * Creates a new package object with the given package name and repository name
     * @param name Name of this package
     * @param repository Name of the packages' repository
     */
    public Package(String name, String repository) {
        this.name = name;
        this.repository = repository;
        this.artifactList = new ArrayList<>();
        this.id = generatePackageId();
        this.customAttributes = new HashMap<>();
    }

    /**
     * Function to get the Neo4j-Property-Map of this package object.
     *
     * @return Neo4j-Property-Map containing all values characterizing this package
     */
    public Map<String, Object> getPackageProps() {
        Map<String, Object> params = new HashMap<>();
        Map<String, Object> props = new HashMap<>();
        props.put("id", getId());
        props.put("name", getName());
        props.put("repo", getRepository());
        props.putAll(this.customAttributes);
        params.put("props", props);
        return params;
    }

    /**
     * Function to get the Neo4j-Property-Map for all artifacts contained in this package.
     *
     * @return Neo4j-Property-Map containing all values characterizing all artifacts of this package
     */
    public Map<String, Object> getArtifactProps() {
        Map<String, Object> params = new HashMap<>();
        ArrayList<Map<String, Object>> props = new ArrayList<>();
        for (Artifact artifact : artifactList) {
            Map<String, Object> prop = new HashMap<>();
            prop.put("id", artifact.getId());
            prop.put("version", artifact.getVersion());
            prop.putAll(artifact.getCustomAttributes());
            props.add(prop);
        }
        params.put("artifacts", props);
        params.put("packageId", this.getId());
        return params;
    }

    private String generatePackageId() {
        return this.repository+":"+this.name;
    }

    /**
     * Retrieves the list of artifacts for this package.
     * @return List of artifacts
     */
    public List<Artifact> getArtifactList() {
        return artifactList;
    }

    /**
     * Retrieves the package name of this object
     * @return Package name
     */
    public String getName() {
        return name;
    }

    /**
     * Retrieves the package identifier for this object
     * @return Package identifier
     */
    public String getId() {
        return id;
    }

    /**
     * Retrieves the name of the repository this package is contained in
     * @return Repository name
     */
    public String getRepository() {
        return repository;
    }

    /**
     * Adds an artifact to this package
     * @param artifact Artifact to add
     */
    public void addArtifact(Artifact artifact) {
        this.artifactList.add(artifact);
    }

    /**
     * Sets a generic custom attribute for this package. This may be used to store annotate additional information to this
     * data model, which will then be stored in the database.
     * @param key Name of the custom attribute
     * @param value Value for the custom attribute
     */
    public void setCustomAttribute(String key, String value){
        this.customAttributes.put(key,value);
    }
}
