package Repositories.NPM;

import Application.ExceptionLogger;

import Repositories.VersionRangeResolver;

/**
 * VersionRangeResolver implementation for the NPM registry.
 */
public class NpmVersionRangeResolver extends VersionRangeResolver<NpmVersionRangeResolver.NpmVersionRangeRepresentation> {

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
        String pattern = "(v?)((((\\d)+|x).){0,2}((\\d)+|x))(((.)?)((\\w)*))?";
        return !potentialVersionRangeSpec.matches(pattern);
    }

    @Override
    protected String normalizeFixedVersionReference(String versionRef) {
        if(versionRef.contains("workspace:"))
            versionRef = versionRef.split("[workspace:]")[1];

        return versionRef.replaceAll(" ", "").split("[-]")[0]
                .split("[@]")[0].replaceAll("(\\*|X)", "x").replaceAll("(\"|\')", "").replaceAll(" ","");
    }

    @Override
    protected boolean isVersionInRange(String originalVersionRangeSpec,
                                       NpmVersionRangeRepresentation versionRangeRepresentation,
                                       String originalToVersion) {

        /* Short down version string for comparison */
        String version = originalToVersion.replaceAll(" ", "");

        /* Version definitions with $ cannot be compared here and empty versions cannot be used*/
        if (version.contains("$")||version.equals("")) {
            return false;
        }

        String versionRange = versionRangeRepresentation.NormalizedRangeString;

        boolean returnValue = false;
        try {
            switch (versionRangeRepresentation.RangeType){
                case STANDARD, UNKNOWN -> returnValue = (versionRange.equals(version)|| fixedRangeEquals(versionRange,version));
                case DASH1,HIGHEREQUAL1 -> {
                    String[] fromSplit = versionRange.split("[.]");
                    String[] toSplit = version.split("[.]");
                    if(toSplit.length>=1){
                        returnValue = (fromSplit[0].equals("x"))||(parseNumber(toSplit[0])>=parseNumber(fromSplit[0]));
                    }
                }
                case DASH2 -> {
                    String[] fromSplit = versionRange.split("[.]");
                    String[] toSplit = version.split("[.]");
                    if(toSplit.length>=2){
                        returnValue = (fromSplit[1].equals("x"))||
                                ((parseNumber(toSplit[0])==parseNumber(fromSplit[0]))&&(parseNumber(toSplit[1])>=parseNumber(fromSplit[1])));
                    }
                }
                case DASH3 -> {
                    String[] fromSplit = versionRange.split("[.]");
                    String[] toSplit = version.split("[.]");
                    if(toSplit.length>=3){
                        returnValue = (fromSplit[2].equals("x"))||
                                ((parseNumber(toSplit[0])==parseNumber(fromSplit[0]))&&(parseNumber(toSplit[1])>parseNumber(fromSplit[1])))||
                                ((parseNumber(toSplit[0])==parseNumber(fromSplit[0]))&&(parseNumber(toSplit[1])>=parseNumber(fromSplit[1]))&&(parseNumber(toSplit[2])>=parseNumber(fromSplit[2])));
                    }
                }

                case HIGHEREQUAL2 -> {
                    if(!fixedRangeEquals(versionRange,version)){
                        NpmVersionRangeRepresentation repr = new NpmVersionRangeRepresentation();
                        repr.RangeType= NpmRangeType.HIGHER2;
                        repr.NormalizedRangeString = versionRange;
                        returnValue = isVersionInRange(originalVersionRangeSpec,repr,version);
                    } else {
                        returnValue = true;
                    }
                }
                case HIGHEREQUAL3 -> {
                    if(!fixedRangeEquals(versionRange,version)){
                        NpmVersionRangeRepresentation repr = new NpmVersionRangeRepresentation();
                        repr.RangeType= NpmRangeType.HIGHER3;
                        repr.NormalizedRangeString = versionRange;
                        returnValue = isVersionInRange(originalVersionRangeSpec,repr,version);
                    } else {
                        returnValue = true;
                    }
                }

                case LOWEREQUAL1 -> {
                    if(!fixedRangeEquals(versionRange,version)){
                        NpmVersionRangeRepresentation repr = new NpmVersionRangeRepresentation();
                        repr.RangeType= NpmRangeType.LOWER1;
                        repr.NormalizedRangeString = versionRange;
                        returnValue = isVersionInRange(originalVersionRangeSpec,repr,version);
                    } else {
                        returnValue = true;
                    }
                }
                case LOWEREQUAL2 -> {
                    if(!fixedRangeEquals(versionRange,version)){
                        NpmVersionRangeRepresentation repr = new NpmVersionRangeRepresentation();
                        repr.RangeType= NpmRangeType.LOWER2;
                        repr.NormalizedRangeString = versionRange;
                        returnValue = isVersionInRange(originalVersionRangeSpec,repr,version);
                    } else {
                        returnValue = true;
                    }
                }
                case LOWEREQUAL3 -> {
                    if(!fixedRangeEquals(versionRange,version)){
                        NpmVersionRangeRepresentation repr = new NpmVersionRangeRepresentation();
                        repr.RangeType= NpmRangeType.LOWER3;
                        repr.NormalizedRangeString = versionRange;
                        returnValue = isVersionInRange(originalVersionRangeSpec,repr,version);
                    } else {
                        returnValue = true;
                    }
                }

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
                                || (parseNumber(toSplit[0])>=parseNumber(fromSplit[0]))&&(parseNumber(toSplit[1])>parseNumber(fromSplit[1])));
                    }
                }
                case HIGHER3 -> {
                    String[] fromSplit = versionRange.split("[.]");
                    String[] toSplit = version.split("[.]");
                    if(toSplit.length>=3){
                        returnValue = (fromSplit[2].equals("x"))
                                ||((parseNumber(toSplit[0])>parseNumber(fromSplit[0]))
                                ||(parseNumber(toSplit[0])>=parseNumber(fromSplit[0])&&(parseNumber(toSplit[1])>parseNumber(fromSplit[1])))
                                ||(parseNumber(toSplit[0])>=parseNumber(fromSplit[0])&&(parseNumber(toSplit[1])>=parseNumber(fromSplit[1])&&(parseNumber(toSplit[2])>parseNumber(fromSplit[2])))));
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
                        returnValue = (fromSplit[2].equals("x"))
                                ||((parseNumber(toSplit[0])<parseNumber(fromSplit[0]))
                                ||(parseNumber(toSplit[0])<=parseNumber(fromSplit[0])&&(parseNumber(toSplit[1])<parseNumber(fromSplit[1])))
                                ||(parseNumber(toSplit[0])<=parseNumber(fromSplit[0])&&(parseNumber(toSplit[1])<=parseNumber(fromSplit[1])&&(parseNumber(toSplit[2])<parseNumber(fromSplit[2])))));
                    }
                }

                case TILDE1 -> {
                    String[] toSplit = version.split("[.]");
                    if(toSplit.length>=1){
                        returnValue = true;
                    }
                }
                case TILDE2 -> {
                    String[] fromSplit = versionRange.split("[.]");
                    String[] toSplit = version.split("[.]");
                    if(toSplit.length>=2){
                        returnValue = (fromSplit[1].equals("x"))||
                                ((parseNumber(toSplit[0])==parseNumber(fromSplit[0]))
                                        && (((parseNumber(toSplit[1])>=parseNumber(fromSplit[1]))) || (fromSplit[2].equals("x"))));
                    }
                }
                case TILDE3 -> {
                    String[] fromSplit = versionRange.split("[.]");
                    String[] toSplit = version.split("[.]");
                    if(toSplit.length>=3){
                        returnValue =
                                ((parseNumber(toSplit[0])==parseNumber(fromSplit[0]))
                                && (((parseNumber(toSplit[1])==parseNumber(fromSplit[1]))) || (fromSplit[2].equals("x")))
                                && (((parseNumber(toSplit[2])>=parseNumber(fromSplit[2]))) || (fromSplit[3].equals("x"))));
                    }
                }

                case OR -> {
                    String orOperator = "(\\|\\|)";
                    String[] splitVersionRanges = versionRange.split(orOperator);

                    NpmVersionRangeRepresentation recursiveReprOne = new NpmVersionRangeRepresentation();
                    recursiveReprOne.RangeType= NpmRangeType.RECURSIVE;
                    recursiveReprOne.NormalizedRangeString = splitVersionRanges[0];

                    NpmVersionRangeRepresentation recursiveReprTwo = new NpmVersionRangeRepresentation();
                    recursiveReprTwo.RangeType= NpmRangeType.RECURSIVE;
                    recursiveReprTwo.NormalizedRangeString = splitVersionRanges[1];

                    return (isVersionInRange(originalVersionRangeSpec,recursiveReprOne,version) || isVersionInRange(originalVersionRangeSpec,recursiveReprTwo,version));
                }

                case AND -> {
                    String andOperator = ",";
                    String[] splitVersionRanges = versionRange.split(andOperator);

                    NpmVersionRangeRepresentation recursiveReprOne = new NpmVersionRangeRepresentation();
                    recursiveReprOne.RangeType= NpmRangeType.RECURSIVE;
                    recursiveReprOne.NormalizedRangeString = splitVersionRanges[0];

                    NpmVersionRangeRepresentation recursiveReprTwo = new NpmVersionRangeRepresentation();
                    recursiveReprTwo.RangeType= NpmRangeType.RECURSIVE;
                    recursiveReprTwo.NormalizedRangeString = splitVersionRanges[1];

                    boolean valid = (isVersionInRange(originalVersionRangeSpec,recursiveReprOne,version) && isVersionInRange(originalVersionRangeSpec,recursiveReprTwo,version));
                    if(version.equals("0.2.2"))
                        System.out.println(version+":   "+splitVersionRanges[0]+ ": "+isVersionInRange(originalVersionRangeSpec,recursiveReprOne,version)+", "+splitVersionRanges[1]+ ": "+isVersionInRange(originalVersionRangeSpec,recursiveReprTwo,version));
                    return valid;
                }

                case RECURSIVE -> {
                    if(versionRange.contains("workspace:"))
                        versionRange = versionRange.split("[workspace:]")[1];
                    NpmVersionRangeRepresentation recursiveRangeRepresentation = buildVersionRangeRepresentation(versionRange);
                    returnValue = isVersionInRange(originalVersionRangeSpec, recursiveRangeRepresentation, version);
                }

                case NOT ->
                    returnValue = !(versionRange.equals(version)|| fixedRangeEquals(versionRange,version));

            }
        } catch (Exception ex){
            ExceptionLogger.add(ex,this.getClass().toString());
        }
        return returnValue;
    }

    @Override
    protected NpmVersionRangeRepresentation buildVersionRangeRepresentation(String versionRangeSpec) {
        NpmVersionRangeRepresentation repr = new NpmVersionRangeRepresentation();
        String fromVersionRange = versionRangeSpec.replaceAll(" ", "").split("[-]")[0]
                .split("[@]")[0].replaceAll("(\\*|X)", "x").replaceAll("(\"|\')", "").replaceAll(" ","");
        repr.NormalizedRangeString = fromVersionRange;

        String pattern = "(v?)((((\\d)+|x).){0,2}((\\d)+|x))(((.)?)((\\w)*))?";
        String prefixes = "(\\^|~|>|(>=)|<|(<=)|==|~=|(!=))";
        String orOperator = "(\\|\\|)";
        String andOperator = "(,)?";

        try {
            /* X.X.X */
            if(fromVersionRange.matches(pattern)){
                repr.RangeType = NpmRangeType.STANDARD;
                return repr;
            }

            // ^X.X.X
            if (fromVersionRange.matches("\\^" + pattern+andOperator)) {
                repr.NormalizedRangeString = fromVersionRange.replaceAll("(,)?","").replaceAll("\\^","");
                String[] split = fromVersionRange.split("[.]");
                switch (split.length) {
                    case 1 -> {
                        repr.RangeType = NpmRangeType.DASH1;
                        return repr;
                    }
                    case 2 -> {
                        repr.RangeType = NpmRangeType.DASH2;
                        return repr;
                    }
                    case 3 -> {
                        repr.RangeType = NpmRangeType.DASH3;
                        return repr;
                    }
                }
            }

            // >X.X.X
            if (fromVersionRange.matches(">"+pattern+andOperator)) {
                String[] split = fromVersionRange.split("[.]");
                switch (split.length) {
                    case 1 -> {
                        repr.RangeType = NpmRangeType.HIGHER1;
                        return repr;
                    }
                    case 2 -> {
                        repr.RangeType = NpmRangeType.HIGHER2;
                        return repr;
                    }
                    case 3 -> {
                        repr.RangeType = NpmRangeType.HIGHER3;
                        return repr;
                    }
                }
            }

            // >=X.X.X
            if (fromVersionRange.matches(">="+pattern+andOperator)) {
                String[] split = fromVersionRange.split("[.]");
                switch (split.length) {
                    case 1 -> {
                        repr.RangeType = NpmRangeType.HIGHEREQUAL1;
                        return repr;
                    }
                    case 2 -> {
                        repr.RangeType = NpmRangeType.HIGHEREQUAL2;
                        return repr;
                    }
                    case 3 -> {
                        repr.RangeType = NpmRangeType.HIGHEREQUAL3;
                        return repr;
                    }
                }
            }

            // <X.X.X
            if (fromVersionRange.matches("<"+pattern+andOperator)) {
                String[] split = fromVersionRange.split("[.]");
                switch (split.length) {
                    case 1 -> {
                        repr.RangeType = NpmRangeType.LOWER1;
                        return repr;
                    }
                    case 2 -> {
                        repr.RangeType = NpmRangeType.LOWER2;
                        return repr;
                    }
                    case 3 -> {
                        repr.RangeType = NpmRangeType.LOWER3;
                        return repr;
                    }
                }
            }

            // <=X.X.X
            if (fromVersionRange.matches("<="+pattern+andOperator)) {
                String[] split = fromVersionRange.split("[.]");
                switch (split.length) {
                    case 1 -> {
                        repr.RangeType = NpmRangeType.LOWEREQUAL1;
                        return repr;
                    }
                    case 2 -> {
                        repr.RangeType = NpmRangeType.LOWEREQUAL2;
                        return repr;
                    }
                    case 3 -> {
                        repr.RangeType = NpmRangeType.LOWEREQUAL3;
                        return repr;
                    }
                }
            }

            // ~X.X.X
            if (fromVersionRange.matches("~(=)?"+pattern+andOperator)) {
                String[] split = fromVersionRange.split("[.]");
                switch (split.length){
                    case 1 -> {
                        repr.RangeType = NpmRangeType.TILDE1;
                        return repr;
                    }
                    case 2 -> {
                        repr.RangeType = NpmRangeType.TILDE2;
                        return repr;
                    }
                    case 3 -> {
                        repr.RangeType = NpmRangeType.TILDE3;
                        return repr;
                    }
                }
            }

            /* OR */
            if(fromVersionRange.matches(prefixes+"?"+pattern+orOperator+prefixes+"?"+pattern)){
                repr.RangeType = NpmRangeType.OR;
                return repr;
            }

            /* And */
            if(fromVersionRange.matches(prefixes+pattern+andOperator+prefixes+pattern)){
                repr.RangeType = NpmRangeType.AND;
                return repr;
            }

            // !X.X.X
            if (fromVersionRange.matches("!(=)?"+pattern)) {
                repr.RangeType = NpmRangeType.NOT;
                return repr;
            }

            /* Unknown */
            repr.RangeType = NpmRangeType.UNKNOWN;
            return repr;
        } catch (Exception ex){
            ExceptionLogger.add(ex,this.getClass().toString());
        }
        return repr;
    }

    /**
     * Class for representation NPM version ranges that have been processed already.
     */
    static class NpmVersionRangeRepresentation {
        public NpmRangeType RangeType;
        public String NormalizedRangeString;
    }

    enum NpmRangeType {
        UNKNOWN,
        STANDARD,
        RECURSIVE,
        AND,
        OR,
        DASH1,
        DASH2,
        DASH3,
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
        TILDE1,
        TILDE2,
        TILDE3,
        NOT
    }
}
