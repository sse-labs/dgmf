package Utilities;

import Repositories.VersionRangeResolver;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static Repositories.RepositoryController.registerAllVersionRangeResolver;

/**
 * Singleton object that delegates requests to resolve version ranges to the corresponding repository-specific resolvers.
 */
public class GlobalVersionRangeResolver {

    // Eager Singleton - Will be instantiated either way, but do not want data races in getInstance
    private static final GlobalVersionRangeResolver theInstance = new GlobalVersionRangeResolver();

    /**
     * Retrieve the singleton instance of this object
     * @return Singleton instance
     */
    public static GlobalVersionRangeResolver getInstance() {
        return theInstance;
    }

    private final Map<String, VersionRangeResolver<?>> registeredResolvers = new HashMap<>();

    private GlobalVersionRangeResolver(){
        registerAllVersionRangeResolver(this);
    }

    /**
     * Resolves the given version range specification for the given version in the context of a given set of versions available.
     *
     * @param repoHint Repository name - dictates how range specifications are parsed
     * @param versionRangeSpec The version range specification to resolve
     * @param allVersions Set of version numbers to search for matching versions in
     * @return Set of version numbers contained in allVersions that match the range specification
     */
    public Set<String> findMatchingVersions(String repoHint, String versionRangeSpec, Set<String> allVersions){
        if(registeredResolvers.containsKey(repoHint))
            return registeredResolvers.get(repoHint).findMatchingVersions(versionRangeSpec, allVersions);
        else
            throw new UnsupportedOperationException("No version range resolver is registered for repository: " + repoHint);
    }

    /**
     * Register a new version range resolver for the given repository name.
     * @param repoHint Name of the repository that hits resolver is designed for
     * @param resolver Resolver to use for the given repository
     */
    public void registerResolver(String repoHint, VersionRangeResolver<?> resolver){
        registeredResolvers.put(repoHint, resolver);
    }
}
