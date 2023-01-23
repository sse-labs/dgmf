package Repositories.Maven;

import Application.ExceptionLogger;
import Repositories.VersionRangeResolver;

import java.util.Objects;

/**
 * VersionRangeResolver implementation for Maven Central. Based on <a href="https://docs.oracle.com/middleware/1212/core/MAVEN/maven_version.htm#CJHDEHAB"> official Maven specifications</a>.
 */
public class MavenVersionRangeResolver extends VersionRangeResolver<MavenVersionRangeResolver.MavenVersionRangeRepresentation> {
    private static final String pattern = "(v?)((((\\d)+|x).){0,2}((\\d)+|x))(((.)?)((\\w)*))?";
    private final String prefixes = "((\\()|(\\[))";
    private final String suffixes = "((\\))|(]))";
    private final String divider = ",";

    @Override
    protected boolean isValidVersionReference(String potentialVersionRangeSpec){
        return (!((potentialVersionRangeSpec.equals(""))
                ||(potentialVersionRangeSpec.contains("$"))
                ||(potentialVersionRangeSpec.contains("latest"))
                ||(potentialVersionRangeSpec.contains("npm"))
                ||(potentialVersionRangeSpec.contains("git"))
                ||(potentialVersionRangeSpec.contains("dist"))
                ||(potentialVersionRangeSpec.contains("file:"))
                ||(potentialVersionRangeSpec.contains("link:"))
                ||(potentialVersionRangeSpec.contains("sys_platform"))
                ||(potentialVersionRangeSpec.contains("platform_machine"))
                ||(potentialVersionRangeSpec.contains("sys.platform"))
                ||(potentialVersionRangeSpec.contains("https://"))));
    }

    @Override
    protected boolean isRangeSpecification(String potentialVersionRangeSpec) {
        return potentialVersionRangeSpec.contains(",");
    }

    @Override
    protected String normalizeFixedVersionReference(String versionRef) {
        String normalizedVersion = versionRef;
        try{
            if(normalizedVersion.startsWith("[") && normalizedVersion.endsWith("]"))
                normalizedVersion =  normalizedVersion.replaceAll("\\[","").replaceAll("]","");

            if(normalizedVersion.contains("-SNAPSHOT"))
                normalizedVersion = normalizedVersion.replace("-SNAPSHOT", "");

            normalizedVersion = normalizedVersion.replaceAll("[.][+]","");

        } catch (Exception ex){
            ExceptionLogger.add(ex,"MavenVersionRangeResolver");
        }

        return normalizedVersion.replaceAll(" ","");
    }

    @Override
    protected MavenVersionRangeRepresentation buildVersionRangeRepresentation(String versionRangeSpec) {
        MavenVersionRangeRepresentation repr = new MavenVersionRangeRepresentation();
        String normalizedVersionRangeSpec = versionRangeSpec.replaceAll(" ", "").split("[-]")[0]
                .split("[@]")[0].replaceAll("(\\*|X)", "x").replaceAll("(\"|\')", "").replaceAll("-SNAPSHOT","").replaceAll("[.][+]","");
        repr.NormalizedRangeString = normalizedVersionRangeSpec;

        try {

            /* X.X.X or [X.X.X]*/
            if (!normalizedVersionRangeSpec.contains(",")&&normalizedVersionRangeSpec.matches("(\\[)?" + pattern + "(])?")) {
                repr.RangeType = MavenRangeType.STANDARD;
                return repr;
            }

            // [,X.X.X)
            if (normalizedVersionRangeSpec.matches("(\\[)?" + divider + pattern + "(\\))?")) {
                String[] split = normalizedVersionRangeSpec.split("[.]");
                repr.NormalizedRangeString = shrinkVersionRange(normalizedVersionRangeSpec);
                switch (split.length) {
                    case 1 -> {
                        repr.RangeType = MavenRangeType.LOWER1;
                        return repr;
                    }
                    case 2 -> {
                        repr.RangeType = MavenRangeType.LOWER2;
                        return repr;
                    }
                    case 3 -> {
                        repr.RangeType = MavenRangeType.LOWER3;
                        return repr;
                    }
                }
            }

            // (X.X.X,]
            if (normalizedVersionRangeSpec.matches("(\\()?" + pattern + divider + "(])?")) {
                String[] split = normalizedVersionRangeSpec.split("[.]");
                repr.NormalizedRangeString = shrinkVersionRange(normalizedVersionRangeSpec);
                switch (split.length) {
                    case 1 -> {
                        repr.RangeType = MavenRangeType.HIGHER1;
                        return repr;
                    }
                    case 2 -> {
                        repr.RangeType = MavenRangeType.HIGHER2;
                        return repr;
                    }
                    case 3 -> {
                        repr.RangeType = MavenRangeType.HIGHER3;
                        return repr;
                    }
                }
            }

            // (,X.X.X]
            if (normalizedVersionRangeSpec.matches(prefixes+"?" + divider + pattern + "(])?")) {
                String[] split = normalizedVersionRangeSpec.split("[.]");
                repr.NormalizedRangeString = shrinkVersionRange(normalizedVersionRangeSpec);
                switch (split.length) {
                    case 1 -> {
                        repr.RangeType = MavenRangeType.LOWEREQUAL1;
                        return repr;
                    }
                    case 2 -> {
                        repr.RangeType = MavenRangeType.LOWEREQUAL2;
                        return repr;
                    }
                    case 3 -> {
                        repr.RangeType = MavenRangeType.LOWEREQUAL3;
                        return repr;
                    }
                }
            }

            // [X.X.X,)
            if (normalizedVersionRangeSpec.matches("(\\[)?" + pattern + divider + suffixes+"?")) {
                String[] split = normalizedVersionRangeSpec.split("[.]");
                repr.NormalizedRangeString = shrinkVersionRange(normalizedVersionRangeSpec);
                switch (split.length) {
                    case 1 -> {
                        repr.RangeType = MavenRangeType.HIGHEREQUAL1;
                        return repr;
                    }
                    case 2 -> {
                        repr.RangeType = MavenRangeType.HIGHEREQUAL2;
                        return repr;
                    }
                    case 3 -> {
                        repr.RangeType = MavenRangeType.HIGHEREQUAL3;
                        return repr;
                    }
                }
            }

            /* Or Pattern: [[X.X.X],[X.X.X]] */
            if (normalizedVersionRangeSpec.matches(prefixes+"?"
                    + prefixes + pattern + suffixes + divider +
                    prefixes + pattern + suffixes + suffixes +"?"
            )) {
                repr.RangeType = MavenRangeType.OR;
                return repr;
            }

            /* AND Pattern: [X.X.X,X.X.X] */
            if (normalizedVersionRangeSpec.matches(
                    prefixes + pattern + divider + pattern + suffixes)) {
                repr.RangeType = MavenRangeType.AND;
                return repr;
            }
        } catch (Exception ex){
        ExceptionLogger.add(ex,"MavenVersionRangeResolver");
        }

        repr.RangeType = MavenRangeType.UNKNOWN;
        return repr;
    }

    @Override
    protected boolean isVersionInRange(String originalVersionRangeSpec, MavenVersionRangeRepresentation versionRangeRepresentation, String originalVersion) {

        /* Short down version string for comparison */
        String version = originalVersion.replaceAll(" ", "").replaceAll("-SNAPSHOT","");

        /* Version definitions with $ cannot be compared here and empty versions cannot be used*/
        if (version.contains("$")||version.equals("")) {
            return false;
        }

        String versionRange = versionRangeRepresentation.NormalizedRangeString;
        MavenRangeType rangeCase = versionRangeRepresentation.RangeType;
        boolean returnValue = false;

        try {
            switch (rangeCase) {
                case STANDARD, UNKNOWN -> returnValue = (versionRange.equals(version) || fixedRangeEquals(versionRange, version));
                case HIGHER1 -> {
                    String[] fromSplit = versionRange.split("[.]");
                    String[] toSplit = version.split("[.]");
                    if(toSplit.length>=1){
                        returnValue = (fromSplit[0].equals("x"))||(parseNumber(toSplit[0])>parseNumber(fromSplit[0]));
                    }
                }
                case HIGHER2 -> {
                    String[] fromSplit = versionRange.split("[.]");
                    String[] toSplit = version.split("[.]");
                    if(toSplit.length>=2){
                        returnValue = (fromSplit[1].equals("x"))
                                || ((parseNumber(toSplit[0])>parseNumber(fromSplit[0]))
                                || (parseNumber(toSplit[0])==parseNumber(fromSplit[0]))&&(parseNumber(toSplit[1])>parseNumber(fromSplit[1])));
                    }
                }
                case HIGHER3 -> {
                    String[] fromSplit = versionRange.split("[.]");
                    String[] toSplit = version.split("[.]");
                    if(toSplit.length>=3){
                        returnValue = (fromSplit[2].equals("x"))
                                ||((parseNumber(toSplit[0])>parseNumber(fromSplit[0]))
                                ||(parseNumber(toSplit[0])==parseNumber(fromSplit[0])&&(parseNumber(toSplit[1])>parseNumber(fromSplit[1])))
                                ||(parseNumber(toSplit[0])==parseNumber(fromSplit[0])&&(parseNumber(toSplit[1])==parseNumber(fromSplit[1])&&(parseNumber(toSplit[2])>parseNumber(fromSplit[2])))));
                    }
                }

                case LOWER1 -> {
                    String[] fromSplit = versionRange.split("[.]");
                    String[] toSplit = version.split("[.]");
                    if(toSplit.length>=1){
                        returnValue = (fromSplit[0].equals("x"))||(parseNumber(toSplit[0])<parseNumber(fromSplit[0]));
                    }
                }
                case LOWER2 -> {
                    String[] fromSplit = versionRange.split("[.]");
                    String[] toSplit = version.split("[.]");
                    if(toSplit.length>=2){
                        returnValue = (fromSplit[1].equals("x"))
                                || ((parseNumber(toSplit[0])<parseNumber(fromSplit[0]))
                                || (parseNumber(toSplit[0])==parseNumber(fromSplit[0]))&&(parseNumber(toSplit[1])<parseNumber(fromSplit[1])));
                    }
                }
                case LOWER3 -> {
                    String[] fromSplit = versionRange.split("[.]");
                    String[] toSplit = version.split("[.]");
                    if(toSplit.length>=3){
                        returnValue = (fromSplit[2].equals("x"))||
                                ((parseNumber(toSplit[0])<parseNumber(fromSplit[0]))||(parseNumber(toSplit[1])<parseNumber(fromSplit[1]))||(parseNumber(toSplit[2])<parseNumber(fromSplit[2])));
                    }
                }

                case HIGHEREQUAL1 -> {
                    if(!(versionRange.equals(version)|| fixedRangeEquals(versionRange,version))){
                        String[] fromSplit = versionRange.split("[.]");
                        String[] toSplit = version.split("[.]");
                        if(toSplit.length>=1){
                            returnValue = (fromSplit[0].equals("x"))||(parseNumber(toSplit[0])>parseNumber(fromSplit[0]));
                        }
                    } else {
                        returnValue = true;
                    }
                }
                case HIGHEREQUAL2 -> {
                    if(!(versionRange.equals(version)|| fixedRangeEquals(versionRange,version))){
                        String[] fromSplit = versionRange.split("[.]");
                        String[] toSplit = version.split("[.]");
                        if(toSplit.length>=2){
                            returnValue = (fromSplit[1].equals("x"))
                                    || ((parseNumber(toSplit[0])>parseNumber(fromSplit[0]))
                                    || (parseNumber(toSplit[0])==parseNumber(fromSplit[0]))&&(parseNumber(toSplit[1])>parseNumber(fromSplit[1])));
                        }
                    } else {
                        returnValue = true;
                    }
                }
                case HIGHEREQUAL3 -> {
                    if(!(versionRange.equals(version)|| fixedRangeEquals(versionRange,version))){
                        String[] fromSplit = versionRange.split("[.]");
                        String[] toSplit = version.split("[.]");
                        if(toSplit.length>=3){
                            returnValue = (fromSplit[2].equals("x"))
                                    ||((parseNumber(toSplit[0])>parseNumber(fromSplit[0]))
                                    ||(parseNumber(toSplit[0])==parseNumber(fromSplit[0])&&(parseNumber(toSplit[1])>parseNumber(fromSplit[1])))
                                    ||(parseNumber(toSplit[0])==parseNumber(fromSplit[0])&&(parseNumber(toSplit[1])==parseNumber(fromSplit[1])&&(parseNumber(toSplit[2])>parseNumber(fromSplit[2])))));
                        }
                    } else {
                        returnValue = true;
                    }
                }

                case LOWEREQUAL1 -> {
                    if(!(versionRange.equals(version)|| fixedRangeEquals(versionRange,version))){
                        String[] fromSplit = versionRange.split("[.]");
                        String[] toSplit = version.split("[.]");
                        if(toSplit.length>=1){
                            returnValue = (fromSplit[0].equals("x"))||(parseNumber(toSplit[0])<parseNumber(fromSplit[0]));
                        }
                    } else {
                        returnValue = true;
                    }
                }
                case LOWEREQUAL2 -> {
                    if(!(versionRange.equals(version)|| fixedRangeEquals(versionRange,version))){
                        String[] fromSplit = versionRange.split("[.]");
                        String[] toSplit = version.split("[.]");
                        if(toSplit.length>=2){
                            returnValue = (fromSplit[1].equals("x"))
                                    || ((parseNumber(toSplit[0])<parseNumber(fromSplit[0]))
                                    || (parseNumber(toSplit[0])==parseNumber(fromSplit[0]))&&(parseNumber(toSplit[1])<parseNumber(fromSplit[1])));
                        }
                    } else {
                        returnValue = true;
                    }
                }
                case LOWEREQUAL3 -> {
                    if(!(versionRange.equals(version)|| fixedRangeEquals(versionRange,version))){
                        String[] fromSplit = versionRange.split("[.]");
                        String[] toSplit = version.split("[.]");
                        if(toSplit.length>=3){
                            returnValue = (fromSplit[2].equals("x"))||
                                    ((parseNumber(toSplit[0])<parseNumber(fromSplit[0]))||(parseNumber(toSplit[1])<parseNumber(fromSplit[1]))||(parseNumber(toSplit[2])<parseNumber(fromSplit[2])));
                        }
                    } else {
                        returnValue = true;
                    }
                }

                case OR -> {
                    if(versionRange.split(divider).length==2) {
                        // Separate the the or cases

                        String[] splitVersionRanges = versionRange.replaceAll(prefixes, "").replaceAll(suffixes, "").split(divider);
                        MavenVersionRangeRepresentation recursiveOne = new MavenVersionRangeRepresentation();
                        recursiveOne.RangeType = MavenRangeType.RECURSIVE;
                        recursiveOne.NormalizedRangeString = splitVersionRanges[0];
                        MavenVersionRangeRepresentation recursiveTwo = new MavenVersionRangeRepresentation();
                        recursiveTwo.RangeType = MavenRangeType.RECURSIVE;
                        recursiveTwo.NormalizedRangeString = splitVersionRanges[1];

                        // recursive call of function
                        if (!versionRange.equals(recursiveOne.NormalizedRangeString) && !versionRange.equals(recursiveTwo.NormalizedRangeString) && !Objects.equals(recursiveOne.NormalizedRangeString, recursiveTwo.NormalizedRangeString)) {
                            returnValue = ((isVersionInRange(originalVersionRangeSpec, recursiveOne, version))
                                    || (isVersionInRange(originalVersionRangeSpec, recursiveTwo, version)));
                        }
                    }
                }

                case AND -> {
                    // Separate the and cases
                    if(versionRange.split(divider).length==2) {
                        String[] splitVersionRanges = versionRange.replaceFirst(prefixes, "").replaceFirst(suffixes, "").split(divider);
                        MavenVersionRangeRepresentation recursiveOne = new MavenVersionRangeRepresentation();
                        recursiveOne.RangeType = MavenRangeType.RECURSIVE;
                        recursiveOne.NormalizedRangeString = versionRange.replaceAll(splitVersionRanges[1], "");
                        MavenVersionRangeRepresentation recursiveTwo = new MavenVersionRangeRepresentation();
                        recursiveTwo.RangeType = MavenRangeType.RECURSIVE;
                        recursiveTwo.NormalizedRangeString = versionRange.replaceAll(splitVersionRanges[0], "");
                        // recursive call of function
                        if (!versionRange.equals(recursiveOne.NormalizedRangeString) && !versionRange.equals(recursiveTwo.NormalizedRangeString) && !Objects.equals(recursiveOne.NormalizedRangeString, recursiveTwo.NormalizedRangeString)) {
                            returnValue = ((isVersionInRange(originalVersionRangeSpec, recursiveOne, version))
                                    && (isVersionInRange(originalVersionRangeSpec, recursiveTwo, version)));
                        }
                    }
                }

                case RECURSIVE -> {
                    MavenVersionRangeRepresentation recursiveRangeRepresentation = buildVersionRangeRepresentation(versionRange);
                    returnValue = isVersionInRange(originalVersionRangeSpec, recursiveRangeRepresentation, version);
                }
            }
        } catch (Exception ex){
            ExceptionLogger.add(ex,"MavenVersionRangeResolver");
        }
        return returnValue;
    }

    /**
     * Class for representation maven version ranges that have been processed already.
     */
    static class MavenVersionRangeRepresentation {
        public MavenRangeType RangeType;
        public String NormalizedRangeString;
    }

    enum MavenRangeType {
        UNKNOWN,
        STANDARD,
        HIGHER1,
        HIGHER2,
        HIGHER3,
        HIGHEREQUAL1,
        HIGHEREQUAL2,
        HIGHEREQUAL3,
        LOWER1,
        LOWER2,
        LOWER3,
        LOWEREQUAL1,
        LOWEREQUAL2,
        LOWEREQUAL3,
        OR,
        AND,
        RECURSIVE
    }

    private String shrinkVersionRange(String versionRange){
        return versionRange.replaceAll("(\\()?","").replaceAll("(])?","").replaceAll(",","");
    }

}
