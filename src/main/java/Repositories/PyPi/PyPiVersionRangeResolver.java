package Repositories.PyPi;

import Application.ExceptionLogger;
import Repositories.VersionRangeResolver;

import javax.accessibility.AccessibleValue;

/**
 * VersionRangeResolver implementation for PyPi.
 */
public class PyPiVersionRangeResolver extends VersionRangeResolver<PyPiVersionRangeResolver.PyPiVersionRangeRepresentation> {

    @Override
    protected boolean isValidVersionReference(String potentialVersionRangeSpec){
        return (!((potentialVersionRangeSpec.contains("$"))
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
                                       PyPiVersionRangeRepresentation versionRangeRepresentation,
                                       String originalToVersion) {

        /* Case ALL */
        if(versionRangeRepresentation.RangeType.equals(PyPiRangeType.ALL)){
            return true;
        }

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
                        PyPiVersionRangeRepresentation repr = new PyPiVersionRangeRepresentation();
                        repr.RangeType= PyPiRangeType.HIGHER2;
                        repr.NormalizedRangeString = versionRange;
                        returnValue = isVersionInRange(originalVersionRangeSpec,repr,version);
                    } else {
                        returnValue = true;
                    }
                }
                case HIGHEREQUAL3 -> {
                    if(!fixedRangeEquals(versionRange,version)){
                        PyPiVersionRangeRepresentation repr = new PyPiVersionRangeRepresentation();
                        repr.RangeType= PyPiRangeType.HIGHER3;
                        repr.NormalizedRangeString = versionRange;
                        returnValue = isVersionInRange(originalVersionRangeSpec,repr,version);
                    } else {
                        returnValue = true;
                    }
                }

                case LOWEREQUAL1 -> {
                    if(!fixedRangeEquals(versionRange,version)){
                        PyPiVersionRangeRepresentation repr = new PyPiVersionRangeRepresentation();
                        repr.RangeType= PyPiRangeType.LOWER1;
                        repr.NormalizedRangeString = versionRange;
                        returnValue = isVersionInRange(originalVersionRangeSpec,repr,version);
                    } else {
                        returnValue = true;
                    }
                }
                case LOWEREQUAL2 -> {
                    if(!fixedRangeEquals(versionRange,version)){
                        PyPiVersionRangeRepresentation repr = new PyPiVersionRangeRepresentation();
                        repr.RangeType= PyPiRangeType.LOWER2;
                        repr.NormalizedRangeString = versionRange;
                        returnValue = isVersionInRange(originalVersionRangeSpec,repr,version);
                    } else {
                        returnValue = true;
                    }
                }
                case LOWEREQUAL3 -> {
                    if(!fixedRangeEquals(versionRange,version)){
                        PyPiVersionRangeRepresentation repr = new PyPiVersionRangeRepresentation();
                        repr.RangeType= PyPiRangeType.LOWER3;
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
                    if(toSplit.length>=1){
                        returnValue = (fromSplit[1].equals("x"))||
                                ((parseNumber(toSplit[0])==parseNumber(fromSplit[0]))
                                        && (((parseNumber(toSplit[1])>=parseNumber(fromSplit[1]))) || (fromSplit[2].equals("x"))));
                    }
                }
                case TILDE3 -> {
                    String[] fromSplit = versionRange.split("[.]");
                    String[] toSplit = version.split("[.]");
                    if(toSplit.length>=2){
                        returnValue =
                                ((parseNumber(toSplit[0])==parseNumber(fromSplit[0]))
                                && (((parseNumber(toSplit[1])==parseNumber(fromSplit[1]))) || (fromSplit[2].equals("x")))
                                && (((parseNumber(toSplit[2])>=parseNumber(fromSplit[2]))) || (fromSplit[3].equals("x"))));
                    }
                }

                case NOT ->
                        {
                            if(versionRange.split("[.]").length<3){
                                versionRange=extendVersionRange(versionRange);
                            }
                            returnValue = !(versionRange.equals(version)|| fixedRangeEquals(versionRange,version));
                        }

                case OR -> {
                    String orOperator = "(\\|\\|)";
                    String[] splitVersionRanges = versionRange.split(orOperator);

                    PyPiVersionRangeRepresentation recursiveReprOne = new PyPiVersionRangeRepresentation();
                    recursiveReprOne.RangeType= PyPiRangeType.RECURSIVE;
                    recursiveReprOne.NormalizedRangeString = splitVersionRanges[0];

                    PyPiVersionRangeRepresentation recursiveReprTwo = new PyPiVersionRangeRepresentation();
                    recursiveReprTwo.RangeType= PyPiRangeType.RECURSIVE;
                    recursiveReprTwo.NormalizedRangeString = splitVersionRanges[1];

                    returnValue = (isVersionInRange(originalVersionRangeSpec,recursiveReprOne,version) || isVersionInRange(originalVersionRangeSpec,recursiveReprTwo,version));
                }

                case AND -> {
                    String andOperator = ",";
                    String[] splitVersionRanges = versionRange.split(andOperator);

                    PyPiVersionRangeRepresentation recursiveReprOne = new PyPiVersionRangeRepresentation();
                    recursiveReprOne.RangeType= PyPiRangeType.RECURSIVE;
                    recursiveReprOne.NormalizedRangeString = splitVersionRanges[0];

                    PyPiVersionRangeRepresentation recursiveReprTwo = new PyPiVersionRangeRepresentation();
                    recursiveReprTwo.RangeType= PyPiRangeType.RECURSIVE;
                    recursiveReprTwo.NormalizedRangeString = splitVersionRanges[1];

                    returnValue = (isVersionInRange(originalVersionRangeSpec,recursiveReprOne,version) && isVersionInRange(originalVersionRangeSpec,recursiveReprTwo,version));
                }

                case RECURSIVE -> {
                    if(versionRange.contains("workspace:"))
                        versionRange = versionRange.split("[workspace:]")[1];
                    PyPiVersionRangeRepresentation recursiveRangeRepresentation = buildVersionRangeRepresentation(versionRange);
                    returnValue = isVersionInRange(originalVersionRangeSpec, recursiveRangeRepresentation, version);
                }
            }
        } catch (Exception ex){
            ExceptionLogger.add(ex,this.getClass().toString());
        }
        return returnValue;
    }

    @Override
    protected PyPiVersionRangeRepresentation buildVersionRangeRepresentation(String versionRangeSpec) {
        PyPiVersionRangeRepresentation repr = new PyPiVersionRangeRepresentation();

        String fromVersionRange = versionRangeSpec.split("[-]")[0]
                .split("[@]")[0].replaceAll("(\\*|X)", "x").replaceAll("(\"|\')", "").replaceAll(" ","");
        repr.NormalizedRangeString = fromVersionRange;

        /* Empty versionRanges are equal to all Versions in PyPi */
        if(fromVersionRange.equals("")){
            repr.RangeType=PyPiRangeType.ALL;
            return repr;
        }

        String pattern = "(v?)((((\\d)+|x).){0,2}((\\d)+|x))(((.)?)((\\w)*))?";
        String prefixes = "(\\^|~|>|(>=)|<|(<=)|==|===|~=|(!=))";
        String orOperator = "(\\|\\|)";
        String andOperator = "(,)";

        try {
            /* X.X.X */
            if(fromVersionRange.matches("((==)|(===))?"+pattern)){
                repr.RangeType = PyPiRangeType.STANDARD;
                return repr;
            }

            // ^X.X.X
            if (fromVersionRange.matches("\\^" + pattern)) {
                repr.NormalizedRangeString = fromVersionRange.replaceAll("(,)?","").replaceAll("\\^","");
                String[] split = fromVersionRange.split("[.]");
                switch (split.length) {
                    case 1 -> {
                        repr.RangeType = PyPiRangeType.DASH1;
                        return repr;
                    }
                    case 2 -> {
                        repr.RangeType = PyPiRangeType.DASH2;
                        return repr;
                    }
                    case 3 -> {
                        repr.RangeType = PyPiRangeType.DASH3;
                        return repr;
                    }
                }
            }

            // >X.X.X
            if (fromVersionRange.matches(">"+pattern)) {
                String[] split = fromVersionRange.split("[.]");
                switch (split.length) {
                    case 1 -> {
                        repr.RangeType = PyPiRangeType.HIGHER1;
                        return repr;
                    }
                    case 2 -> {
                        repr.RangeType = PyPiRangeType.HIGHER2;
                        return repr;
                    }
                    case 3 -> {
                        repr.RangeType = PyPiRangeType.HIGHER3;
                        return repr;
                    }
                }
            }

            // >=X.X.X
            if (fromVersionRange.matches(">="+pattern)) {
                String[] split = fromVersionRange.split("[.]");
                switch (split.length) {
                    case 1 -> {
                        repr.RangeType = PyPiRangeType.HIGHEREQUAL1;
                        return repr;
                    }
                    case 2 -> {
                        repr.RangeType = PyPiRangeType.HIGHEREQUAL2;
                        return repr;
                    }
                    case 3 -> {
                        repr.RangeType = PyPiRangeType.HIGHEREQUAL3;
                        return repr;
                    }
                }
            }

            // <X.X.X
            if (fromVersionRange.matches("<"+pattern)) {
                String[] split = fromVersionRange.split("[.]");
                switch (split.length) {
                    case 1 -> {
                        repr.RangeType = PyPiRangeType.LOWER1;
                        return repr;
                    }
                    case 2 -> {
                        repr.RangeType = PyPiRangeType.LOWER2;
                        return repr;
                    }
                    case 3 -> {
                        repr.RangeType = PyPiRangeType.LOWER3;
                        return repr;
                    }
                }
            }

            // <=X.X.X
            if (fromVersionRange.matches("<="+pattern)) {
                String[] split = fromVersionRange.split("[.]");
                switch (split.length) {
                    case 1 -> {
                        repr.RangeType = PyPiRangeType.LOWEREQUAL1;
                        return repr;
                    }
                    case 2 -> {
                        repr.RangeType = PyPiRangeType.LOWEREQUAL2;
                        return repr;
                    }
                    case 3 -> {
                        repr.RangeType = PyPiRangeType.LOWEREQUAL3;
                        return repr;
                    }
                }
            }

            // ~X.X.X
            if (fromVersionRange.matches("~(=)?"+pattern)) {
                String[] split = fromVersionRange.split("[.]");
                switch (split.length){
                    case 1 -> {
                        repr.RangeType = PyPiRangeType.TILDE1;
                        return repr;
                    }
                    case 2 -> {
                        repr.RangeType = PyPiRangeType.TILDE2;
                        return repr;
                    }
                    case 3 -> {
                        repr.RangeType = PyPiRangeType.TILDE3;
                        return repr;
                    }
                }
            }

            // !X.X.X
            if (fromVersionRange.matches("!(\\=)?"+pattern)||fromVersionRange.matches("!\\=(\\d)+")) {
                repr.RangeType = PyPiRangeType.NOT;
                return repr;
            }

            /* OR */
            if(fromVersionRange.matches(prefixes+"?"+pattern+orOperator+prefixes+"?"+pattern)){
                repr.RangeType = PyPiRangeType.OR;
                return repr;
            }

            /* And */
            if(fromVersionRange.matches(prefixes+"?"+pattern+andOperator+prefixes+"?"+pattern)){
                repr.RangeType = PyPiRangeType.AND;
                return repr;
            }

            /* Unknown */
            repr.RangeType = PyPiRangeType.UNKNOWN;
            return repr;
        } catch (Exception ex){
            ExceptionLogger.add(ex,this.getClass().toString());
        }
        return repr;
    }

    static class PyPiVersionRangeRepresentation {
        public PyPiRangeType RangeType;
        public String NormalizedRangeString;
    }

    enum PyPiRangeType {
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
        NOT,
        ALL
    }

    private String extendVersionRange(String version){
        version=version.replaceAll("!=","");
        String[] versionSplit = version.split("[.]");

        return switch (versionSplit.length) {
            case 0 -> "";
            case 1 -> version + ".0.0";
            case 2 -> version + ".0";
            default -> version;
        };
    }
}
