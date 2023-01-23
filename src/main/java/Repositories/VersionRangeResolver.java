package Repositories;

import Application.ExceptionLogger;

import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Abstract superclass for all version range resolver implementations. Based on a given version range specification and
 * a set of target versions, the resolver finds all target versions addressed by the range. It keeps track of statistics
 * and uses different caches to avoid redundant computations.
 *
 * @param <T> Type of range representations after they have been parsed
 */
public abstract class VersionRangeResolver<T> {

    protected int totalRangeSpecsProcessed = 0;
    protected int totalRanges = 0;
    protected int totalFixedVersionReferences = 0;

    protected final VersionRangeContainmentCache rangeContainmentCache = new VersionRangeContainmentCache();
    protected final VersionRangeRepresentationCache<T> rangeReprCache = new VersionRangeRepresentationCache<>();

    /**
     * For the given dependency specification and set of target artifact versions, finds all versions addressed by the
     * dependency specification
     *
     * @param versionRangeSpec The dependency version specification
     * @param allVersions List of all versions of the target artifact
     * @return Unordered List of all target versions matching the dependency specification
     */
    public Set<String> findMatchingVersions(String versionRangeSpec, Set<String> allVersions){
        totalRangeSpecsProcessed += 1;

        // Check if versionRange contains illegal characters
        if(isValidVersionReference(versionRangeSpec)) {

            // Check if versionSpec is single version or range
            if (isRangeSpecification(versionRangeSpec)) {
                totalRanges += 1;

                T preprocessedRange;

                if (rangeReprCache.hasEntry(versionRangeSpec)) {
                    preprocessedRange = rangeReprCache.getEntry(versionRangeSpec);
                } else {
                    preprocessedRange = buildVersionRangeRepresentation(versionRangeSpec);
                    rangeReprCache.pushEntry(versionRangeSpec, preprocessedRange);
                }

                Set<String> resultSet = new HashSet<>();

                for (String version : allVersions) {
                    Boolean cacheEntry = rangeContainmentCache.getEntryOrElseNull(versionRangeSpec, version);

                    if (cacheEntry != null) {
                        if (cacheEntry) resultSet.add(version);
                    } else {
                        boolean isInRage = isVersionInRange(versionRangeSpec, preprocessedRange, version);
                        rangeContainmentCache.pushEntry(versionRangeSpec, version, isInRage);

                        if (isInRage) resultSet.add(version);
                    }
                }

                return resultSet;
            } else {
                // For fixed version references we just normalize the specification and lookup the version in the list
                totalFixedVersionReferences += 1;

                String normalizedTargetVersion = normalizeFixedVersionReference(versionRangeSpec);

                Set<String> resultSet = new HashSet<>();

                for (String version:allVersions) {
                    if(normalizedTargetVersion.equals(version)|| fixedRangeEquals(normalizedTargetVersion,version)){
                        resultSet.add(version);
                    }
                }

                return resultSet;
            }
        } else {
            return Collections.emptySet();
        }
    }

    /**
     * Clears the statistics and caches of this VersionRangeResolver instance.
     */
    public void clear(){
        totalRangeSpecsProcessed = 0;
        totalRanges = 0;
        totalFixedVersionReferences = 0;

        rangeContainmentCache.clear();
        rangeReprCache.clear();
    }

    /**
     * Gets the number of total dependency specifications processed by this instance.
     * @return Count of specifications processed
     */
    public int getTotalSpecsProcessed() { return totalRangeSpecsProcessed; }

    /**
     * Gets the number of dependency specifications that have been identified to be actual version ranges.
     * @return Count of version ranges processed
     */
    public int getRangesProcessed() { return totalRanges; }

    /**
     * Gets the number of dependency specifications that have been identifier to be fixed version references.
     * @return Count of fixed version references processed
     */
    public int getFixedVersionsProcessed() { return totalFixedVersionReferences; }

    /**
     * Checks whether the given dependency specification is a range specification or not.
     * @param potentialVersionRangeSpec The dependency specification to check
     * @return True if the specification is a range specification, false otherwise
     */
    protected abstract boolean isRangeSpecification(String potentialVersionRangeSpec);

    /**
     * Normalizes a fixed version reference so that it can be compared to the actual versions of target artifacts. This
     * is necessary e.g. in Maven, where [1.2.3-SNAPSHOT] is a valid fixed version reference, but maps to the target version
     * 1.2.3
     * @param versionRef Fixed version reference to normalize
     * @return The normalized version
     */
    protected abstract String normalizeFixedVersionReference(String versionRef);

    /**
     * Checks whether the given version is contained in the given version range specification. The specification must
     * be a valid range specification, and not a fixed version specification.
     * @param originalVersionRangeSpec Version Range Specification that is NOT a fixed version reference
     * @param versionRangeRepresentation Intermediate, preprocessed representation of the version range
     * @param version The version to check
     * @return True if version is contained in versionRangeSpec, false otherwise
     */
    protected abstract boolean isVersionInRange(String originalVersionRangeSpec, T versionRangeRepresentation, String version);

    /**
     * Processes a version range and builds an intermediate representation. This intermediate representation may carry
     * any information that is necessary to do the actual version containment checks.
     * @param versionRangeSpec The version range specification to process
     * @return Intermediate representation of the version range
     */
    protected abstract T buildVersionRangeRepresentation(String versionRangeSpec);

    /**
     * Checks whether the given versionRef is a valid reference. Returns true if this is the case, false otherwise
     * (e.g. when there are illegal characters contained).
     * @param versionRef Version reference to validate
     * @return true if valid, false otherwise
     */
    protected abstract boolean isValidVersionReference(String versionRef);

    /**
     * Checks whether a given fixed range reference matches the given version string. In some cases, fixed references
     * match versions that are not in fact equal strings. For example 1.0 == 1.0.0. This is only called for fixed ranges,
     * i.e. version references addressing a single target version.
     *
     * @param versionRange versionRange for comparison
     * @param version version for comparison
     * @return true, if version is equal or within definition of versionRange
     */
    protected boolean fixedRangeEquals(String versionRange, String version){
        try {
            if(version.length()==0){
                return false;
            }
            String[] versionRangeSplit = versionRange.split("[.]");
            String[] versionSplit = version.split("[.]");

            /* If version range is more specific than version return false*/
            if(versionRangeSplit.length>versionSplit.length){
                return false;
            }

            /* Check for each part of the version, if version range part is equal to version part */
            for(int i = 0; i< versionRangeSplit.length; i++){
                try{
                    if((!versionRangeSplit[i].equals("x")) &&
                            (!versionSplit[i].equals(versionRangeSplit[i]))&&
                            ((parseNumber(versionSplit[i])) !=parseNumber(versionRangeSplit[i]))){
                        return false;
                    }
                } catch (NumberFormatException exception){
                    //print("version: "+version+", versionRange: "+versionRange);
                }
            }
        } catch (Exception ex){
            ExceptionLogger.add(ex,this.getClass().getName());
        }
        return true;
    }

    private static class VersionRangeRepresentationCache<E> {

        private final Map<String, E> theCache = new HashMap<>();
        private final ReentrantLock cacheLock = new ReentrantLock();

        private final int maxEntries;

        public VersionRangeRepresentationCache(){
            this(5000);
        }

        public VersionRangeRepresentationCache(int limit){
            this.maxEntries = limit;
        }

        public boolean hasEntry(String range) { return theCache.containsKey(range); }

        public E getEntry(String range) { return theCache.get(range); }

        public void pushEntry(String range, E representation){

            cacheLock.lock();
            try{
                theCache.put(range, representation);

                if(theCache.size() > maxEntries){
                    String key = theCache.keySet().stream().findAny().orElse(range);
                    theCache.remove(key);
                }
            } finally {
                cacheLock.unlock();
            }

        }

        public void clear() {
            cacheLock.lock();
            try{ theCache.clear(); } finally { cacheLock.unlock(); }
        }
    }

    /**
     * Simple Cache implementation
     */
    private static class VersionRangeContainmentCache {

        static class RangeResolverCacheEntry {
            private final Map<String, Boolean> isVersionInRangeMap = new HashMap<>();

            private int totalVersionsContained() { return isVersionInRangeMap.size(); }
        }

        private final Map<String, RangeResolverCacheEntry> theCache = new HashMap<>();
        private final ReentrantLock cacheLock = new ReentrantLock();

        private final int maxTotalEntries;
        private final int maxTotalVersions;

        private int totalEntryCnt = 0;
        private int totalVersionCnt = 0;

        public VersionRangeContainmentCache(){
            // Default cache limits
            this(5000, 5000 * 3000);
        }

        public VersionRangeContainmentCache(int entryLimit, int versionLimit){
            this.maxTotalEntries = entryLimit;
            this.maxTotalVersions = versionLimit;
        }

        public boolean hasEntry(String rangeSpec, String version){
            return theCache.containsKey(rangeSpec) &&
                    theCache.get(rangeSpec).isVersionInRangeMap.containsKey(version);
        }

        public Boolean getEntryOrElseNull(String rangeSpec, String version){
            cacheLock.lock();

            try {
                if(hasEntry(rangeSpec, version))
                    return theCache.get(rangeSpec).isVersionInRangeMap.get(version);
                else
                    return null;
            } finally {
                cacheLock.unlock();
            }
        }

        public void pushEntry(String rangeSpec, String version, boolean versionInRange){

            cacheLock.lock();
            try {
                // Recheck: If entry has been added in the mean time, we do not need to do anything
                if(hasEntry(rangeSpec, version)) return;

                if(!theCache.containsKey(rangeSpec)){
                    RangeResolverCacheEntry e = new RangeResolverCacheEntry();
                    theCache.put(rangeSpec, e);
                    totalEntryCnt += 1;
                }

                theCache.get(rangeSpec).isVersionInRangeMap.put(version, versionInRange);
                totalVersionCnt += 1;

                checkLimits();

            } finally {
                cacheLock.unlock();
            }
        }

        public void clear(){
            cacheLock.lock();

            try {
                theCache.clear();
                totalEntryCnt = 0;
                totalVersionCnt = 0;
            } finally { cacheLock.unlock(); }
        }

        private void checkLimits(){
            if(totalEntryCnt > maxTotalEntries || totalVersionCnt > maxTotalVersions){
                removeSmallestRange();
            }
        }

        /**
         * Removes the range that has the smallest number of versions associated to it. This
         * should be an indicator for ranges that are "not as relevant" as others.
         */
        private void removeSmallestRange(){
            String smallestRange = null;
            int smallestRangeSize = Integer.MAX_VALUE;

            for(String range : theCache.keySet()){
                RangeResolverCacheEntry entry = theCache.get(range);
                if(entry.totalVersionsContained() < smallestRangeSize){
                    smallestRangeSize = entry.totalVersionsContained();
                    smallestRange = range;
                }
            }

            if(smallestRange != null){
                theCache.remove(smallestRange);
                totalEntryCnt -= 1;
                totalVersionCnt -= smallestRangeSize;
            }
        }

    }

    /*** Help function to parse String into integer with handling of exceptions
     * @param a String for Parsing
     * @return parsed Integer, returns -1 if param doesn't contain a number
     */
    protected int parseNumber(String a){
        try{
            int maxsize = 12;
            if(a.length()>maxsize)
                a = a.substring(0,maxsize-1);
            return Integer.parseInt(a.replaceAll("\\D", ""));
        } catch (NumberFormatException exception){
            //Logger.add(exception,this.getClass().getName());
            return -1;
        }
    }
}
