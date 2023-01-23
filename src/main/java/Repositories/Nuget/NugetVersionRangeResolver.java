package Repositories.Nuget;

import Repositories.VersionRangeResolver;

import javax.annotation.Nullable;
import java.util.Objects;

/**
 * VersionRangeResolver implementation for Nuget.org. Based on <a href="https://learn.microsoft.com/en-us/nuget/concepts/package-versioning">the official specifications.</a>
 */
public class NugetVersionRangeResolver extends VersionRangeResolver<NugetVersionRangeResolver.NugetVersionRange> {


    @Override
    protected boolean isRangeSpecification(String potentialVersionRangeSpec) {
        // Everything that is not [<version>] is a range!
        return !potentialVersionRangeSpec.startsWith("[") || !potentialVersionRangeSpec.endsWith("]") || potentialVersionRangeSpec.contains(",");
    }

    @Override
    protected String normalizeFixedVersionReference(String versionRef) {
        if(versionRef.startsWith("[") && versionRef.endsWith("]")) return versionRef.substring(1, versionRef.length() - 1);

        return versionRef;
    }

    @Override
    protected boolean isVersionInRange(String originalVersionRangeSpec, NugetVersionRange versionRangeRepresentation, String version) {
        SimpleVersion versionToCheck = new SimpleVersion(version);

        if(versionRangeRepresentation.lowerBoundVersion != null){

            if(versionToCheck.isLowerThan(versionRangeRepresentation.lowerBoundVersion))
                return false;
            else if(!versionRangeRepresentation.lowerBoundInclusive && versionToCheck.equalsNoSuffix(versionRangeRepresentation.lowerBoundVersion))
                return false;

        }

        if(versionRangeRepresentation.upperBoundVersion != null){

            if(versionToCheck.isGreaterThan(versionRangeRepresentation.upperBoundVersion))
                return false;
            else if(!versionRangeRepresentation.upperBoundInclusive && versionToCheck.equalsNoSuffix(versionRangeRepresentation.upperBoundVersion))
                return false;
        }



        return true;
    }

    @Override
    public boolean fixedRangeEquals(String range, String version){
        // Fixed references are equal to a target version if all parts + suffix match. I.e. [1.0.0-abc] only matches "1.0.0-abc", not "1.0.0"
        SimpleVersion rangeV = new SimpleVersion(range);
        SimpleVersion versionV = new SimpleVersion(version);
        return rangeV.equals(versionV);
    }

    @Override
    protected NugetVersionRange buildVersionRangeRepresentation(String versionRangeSpec) {

        if(versionRangeSpec.contains("*")){
            // Special handling for floating versions: Versions of kind 1.*, 1.2.*, * are also ranges, but specified differently
            return buildRangeForFloatingVersion(versionRangeSpec);
        }

        NugetVersionRange repr = new NugetVersionRange();

        String rangeString = versionRangeSpec.trim();
        char firstChar = rangeString.charAt(0);

        if(Character.isDigit(firstChar)){
            // Can only mean that we have version of format '1.2.3', which means 'minimum version inclusive'
            repr.lowerBoundInclusive = true;
            repr.upperBoundInclusive = false;
            repr.upperBoundVersion = null;
            repr.lowerBoundVersion = new SimpleVersion(rangeString);
        } else {
            if(firstChar == '('){
                repr.lowerBoundInclusive = false;
            } else if(firstChar == '['){
                repr.lowerBoundInclusive = true;
            } else {
                throw new IllegalArgumentException("Invalid NuGet version range: " + versionRangeSpec);
            }
            rangeString = rangeString.substring(1);
            char lastChar = rangeString.charAt(rangeString.length() - 1);

            if(lastChar == ')'){
                repr.upperBoundInclusive = false;
            } else if (lastChar == ']') {
                repr.upperBoundInclusive = true;
            } else {
                throw new IllegalArgumentException("Invalid NuGet version range: " + versionRangeSpec);
            }
            rangeString = rangeString.substring(0, rangeString.length() - 1).trim();

            String[] parts = rangeString.split(",");
            if(rangeString.trim().equals(",")){
                repr.lowerBoundVersion = null;
                repr.upperBoundVersion = null;
            } else if(rangeString.charAt(0) == ',' && parts.length == 1){
                repr.lowerBoundVersion = null;
                repr.upperBoundVersion = new SimpleVersion(parts[0].trim());
            } else if(rangeString.charAt(rangeString.length() - 1) == ',' && parts.length == 1){
                repr.upperBoundVersion = null;
                repr.lowerBoundVersion = new SimpleVersion(parts[0]);
            } else if(parts.length == 2){
                if(parts[0].isBlank()) repr.lowerBoundVersion = null;
                else repr.lowerBoundVersion = new SimpleVersion(parts[0].trim());

                if(parts[1].isBlank()) repr.upperBoundVersion = null;
                else repr.upperBoundVersion = new SimpleVersion(parts[1].trim());
            } else {
                throw new IllegalArgumentException("Invalid NuGet version range: " + versionRangeSpec);
            }
        }

        return repr;
    }

    private NugetVersionRange buildRangeForFloatingVersion(String floatingSpec){
        NugetVersionRange range = new NugetVersionRange();

        // This is always the case for floating references
        range.upperBoundInclusive = false;
        range.lowerBoundInclusive = true;

        String[] parts = floatingSpec.trim().split("\\.");
        int starIndex = -1;

        StringBuilder lowerVersionBuilder = new StringBuilder();

        for(int i = 0; i < parts.length; i++){
            String curr = parts[i].trim();

            if(i != 0) lowerVersionBuilder.append(".");

            if(curr.equals("*")){
                lowerVersionBuilder.append("0");
                starIndex = i;
                break;
            } else {
                lowerVersionBuilder.append(curr);
            }
        }

        String lowerVersion = lowerVersionBuilder.toString();

        range.lowerBoundVersion = new SimpleVersion(lowerVersion);

        if(starIndex > 0) {
            SimpleVersion upperBoundVersion = new SimpleVersion(lowerVersion);
            upperBoundVersion.increaseAt(starIndex - 1, 1);
            range.upperBoundVersion = upperBoundVersion;
        } else {
            range.upperBoundVersion = null;
        }


        return range;
    }

    @Override
    protected boolean isValidVersionReference(String versionRef) {
        return !versionRef.isBlank() && (versionRef.startsWith("(") || versionRef.startsWith("[") || Character.isDigit(versionRef.charAt(0))|| versionRef.charAt(0) == '*');
    }

    /**
     * Simple representation of nuget versions. They have an upper- and lower-bound, both may be inclusive or exclusive.
     */
    static class NugetVersionRange {
        public SimpleVersion lowerBoundVersion;
        public SimpleVersion upperBoundVersion;

        public boolean lowerBoundInclusive;
        public boolean upperBoundInclusive;
    }

    /**
     * Simple version implementation that allows for arbitrary length of semantic versions (dot-separated) with an optional suffix
     * in the end, separated by a dash. Provides functionality to compare versions based on Nuget.org specifications.
     */
    static class SimpleVersion implements Comparable<SimpleVersion> {

        private final String raw;

        private final int[] parts;

        private String suffix = null;

        public SimpleVersion(String version) {
            this.raw = version;

            String[] verAndSuffix = this.raw.split("-");

            if(verAndSuffix.length > 1){
                this.suffix = verAndSuffix[1];
            }

            String[] tmp = verAndSuffix[0].split("\\.");

            this.parts = new int[tmp.length];

            for(int i = 0; i < tmp.length; i++){
                try{
                    String curr = tmp[i];

                    this.parts[i] = Integer.parseInt(curr);
                } catch (NumberFormatException nfx){
                    // Very simple handling
                    this.parts[i] = 0;
                }

            }
        }

        public int[] getParts(){
            return this.parts;
        }

        public String getRaw(){
            return this.raw;
        }

        public String getSuffix() { return this.suffix; }

        public void increaseAt(int index, int delta) {
            if(index >= 0 && index < parts.length){
                this.parts[index] += delta;
            }
        }

        public boolean isGreaterThan(SimpleVersion other) {
            return this.compareTo(other) > 0;
        }

        public boolean isLowerThan(SimpleVersion other){
            return this.compareTo(other) < 0;
        }

        /**
         * Notice that 1.0-abc === 1.0.0-abc
         * @param other
         * @return
         */
        @Override
        public boolean equals(Object other){
            if(other instanceof SimpleVersion){
                SimpleVersion o = (SimpleVersion) other;

                if(!equalsNoSuffix(o)) return false;

                return Objects.equals(o.getSuffix(), this.suffix);
            }
            return false;
        }

        public boolean equalsNoSuffix(SimpleVersion o){
            int[] oParts = o.getParts();

            int maxL = Math.max(oParts.length, this.parts.length);

            for(int i = 0; i < maxL; i++){
                int currPart = i < this.parts.length ? this.parts[i] : 0;
                int oPart = i < oParts.length ? oParts[i] : 0;

                if(oPart != currPart) return false;
            }

            return true;
        }


        @Override
        public int compareTo(@Nullable SimpleVersion other) {
            if(other == null) return 1;

            int[] oParts = other.getParts();

            int maxL = Math.max(this.parts.length, oParts.length);

            for(int i = 0; i < maxL; i++){
                int currPart = i < this.parts.length ? this.parts[i] : 0;
                int oPart = i < oParts.length ? oParts[i] : 0;

                if(currPart > oPart) return 1;
                else if(currPart < oPart) return -1;
            }

            if(this.suffix == null && other.getSuffix() != null) return 1;
            else if(this.suffix == null) return 0;
            else if(other.getSuffix() == null) return -1;
            else return this.suffix.compareTo(other.getSuffix());

        }
    }
}
