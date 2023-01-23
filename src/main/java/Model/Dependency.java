package Model;

import java.util.HashMap;
import java.util.Map;

/**
 * Class of a Dependency, which represents the dependency of one artefact to another package, given an optional version
 * range specification for the target package.
 */
public class Dependency {
    private final String name;
    private String versionRange;
    private final Map<String,String> customAttributes;

    /**
     * Creates a new instance with the given target package name and no version range specification.
     * @param name Name of the target package for this dependency
     */
    public Dependency(String name) {
        this.name = name;
        this.versionRange = "";
        this.customAttributes = new HashMap<>();
    }

    /**
     * Creates a new instance with the given target package name and version range specification
     * @param name Name of the target package for this dependency
     * @param versionRange String specifying which versions of the target package are addressed
     */
    public Dependency(String name, String versionRange){
        this(name);
        this.versionRange = versionRange;
    }

    /**
     * Sets the version range of this dependency
     * @param versionString New version range string
     */
    public void setVersionRange(String versionString) {
        this.versionRange = versionString;
    }

    /**
     * Retrieves the name of the target package for this dependency.
     * @return Target package name
     */
    public String getName() {
        return name;
    }

    /**
     * Retrieves the target version range for this dependency.
     * @return String specification of the target version range
     */
    public String getVersionRange() {
        return versionRange;
    }

    /**
     * Sets a generic custom attribute for this dependency. This may be used to store annotate additional information to this
     * data model, which will then be stored in the database.
     * @param key Name of the custom attribute
     * @param value Value for the custom attribute
     */
    public void setCustomAttribute(String key, String value){
        this.customAttributes.put(key,value);
    }

    /**
     * Returns the map of custom attributes for this dependency.
     *
     * @return Map of custom attribute names to their values.
     */
    public Map<String, String> getCustomAttributes() {
        return customAttributes;
    }
}
