package Model;

import java.util.*;

/**
 * Class of an Artifact object, which represents a single release of a software package
 */
public class Artifact {
    private final String id;
    private final String repo;
    private final String version;
    private final ArrayList<Dependency> dependencies;
    private final Map<String,String> customAttributes;

    /**
     * Constructor of an Artifact object with a given version number, package name and repository
     * @param version Version number of this release
     * @param packageName Name of the package
     * @param repository Name of repository
     */
    public Artifact(String version, String packageName, String repository) {
        this.version = version;
        this.repo = repository;
        this.id = repository+":"+packageName+":"+version;
        this.dependencies = new ArrayList<>();
        this.customAttributes = new HashMap<>();
        parseVersionToInteger(version);
    }

    /**
     * Function to get the Neo4j-Property-Map of all the Dependencies of the Artifact object
     *
     * @param setUnresolved If true, the property map will contain the attribute "resolved: false" for all edges
     *
     * @return Neo4j-Property-Map containing all dependency parameters
     */
    public Map<String, Object> getDependencyProps(boolean setUnresolved) {
        Map<String, Object> params = new HashMap<>();
        ArrayList<Map<String, Object>> props = new ArrayList<>();
        for (Dependency dependency : this.dependencies) {
            Map<String, Object> prop = new HashMap<>();
            prop.put("packageId", generatePackageId(dependency.getName(), this.repo));
            prop.put("name", dependency.getName());
            Map<String, Object> propDependency = new HashMap<>();
            propDependency.put("repo", this.repo);
            propDependency.put("version", dependency.getVersionRange());

            if(setUnresolved) propDependency.put("resolved", false);

            propDependency.putAll(dependency.getCustomAttributes());
            prop.put("props", propDependency);
            props.add(prop);
        }
        params.put("dependencies", props);
        params.put("artifactId", this.getId());
        return params;
    }

    /**
     * Function to parse version description into a comparable Integer value
     * @param input version description
     */
    private void parseVersionToInteger(String input) {
        String minVersionPart = "00000";
        int maxVersionSize = minVersionPart.length();
        StringBuilder output = new StringBuilder();
        String[] split = input.split("[.]");
        for (int i = 0; i < 3; i++) {
            if ((i >= split.length) || (split[i].contains("x"))) {
                    output.append(minVersionPart);
            } else {
                String versionPart = split[i].replaceAll("\\D+", "");
                if (versionPart.length() > maxVersionSize) {
                        output.append(minVersionPart);
                } else {
                    int missingZeros = maxVersionSize - versionPart.length();
                    output.append("0".repeat(missingZeros));
                    output.append(versionPart);
                }

            }
        }
        this.setCustomAttribute("versionCompare",output.toString());
    }

    /**
     * Function to generate PackageId with a given name and repository String
     * @param name name of package
     * @param repo name of repository
     * @return generated PackageId
     */
    private String generatePackageId(String name, String repo) {
        return repo +":"+ name;
    }


    /**
     * Retrieves the identifier of this artifact.
     * @return Identifier as string
     */
    public String getId() {
        return id;
    }

    /**
     * Retrieves the version of this artifact.
     * @return Version number as string
     */
    public String getVersion() {
        return version;
    }

    /**
     * Retrieves all dependencies of this artifact.
     * @return List of dependencies
     */
    public List<Dependency> getDependencies() {
        return this.dependencies;
    }

    /**
     * Adds the given dependency to this artifact's list of dependencies.
     * @param dependency Dependency to add
     */
    public void addDependency(Dependency dependency) {
        this.dependencies.add(dependency);
    }

    /**
     * Sets a generic custom attribute for this artifact. This may be used to store annotate additional information to this
     * data model, which will then be stored in the database.
     * @param key Name of the custom attribute
     * @param value Value for the custom attribute
     */
    public void setCustomAttribute(String key, String value){
        this.customAttributes.put(key,value);
    }

    /**
     * Returns the map of custom attributes for this artifact.
     *
     * @return Map of custom attribute names to their values.
     */
    public Map<String, String> getCustomAttributes() {
        return customAttributes;
    }
}