package Repositories;

import Model.Package;
import Repositories.Maven.MavenIdGenerator;
import Repositories.Maven.MavenMiner;
import Repositories.Maven.MavenVersionRangeResolver;
import Repositories.NPM.NpmIdGenerator;
import Repositories.NPM.NpmMiner;
import Repositories.NPM.NpmVersionRangeResolver;
import Repositories.Nuget.NugetIdGenerator;
import Repositories.Nuget.NugetMiner;
import Repositories.Nuget.NugetVersionRangeResolver;
import Repositories.PyPi.PyPiIdGenerator;
import Repositories.PyPi.PyPiMiner;
import Repositories.PyPi.PyPiVersionRangeResolver;
import Utilities.FileBasedIdGenerator;
import Utilities.GlobalUtilities;
import Utilities.GlobalVersionRangeResolver;
import org.json.JSONObject;

import java.util.List;
import java.util.Set;

/**
 * Controller between repository-specific- and general components
 * Needs to be updated, if new repositories are added
 */
public class RepositoryController {

    /* --> Add new repositories here */
    public static final String[] repositoryList = {"npm","pypi","maven", "nuget"};

    /** --> Interface of an IdGenerator needs to be implemented for new repository */
    public interface IdGenerator {
        /**
         * Function to generate a list of ids for a repository within set limit and offset configurations
         * @return String list of ids
         */
        List<String> generateIds();
    }


    /**
     * Function to get repository-implementation of miner with repository name
     * @param repositoryName name of repository
     * @return Miner implementation of repository
     */
    public static Miner getMiner(String repositoryName){
        return switch (repositoryName) {
            case "npm" -> new NpmMiner();
            case "maven" -> new MavenMiner();
            case "pypi" -> new PyPiMiner();
            case "nuget" -> new NugetMiner();
            /* --> Add new repositories here */
            default -> null;
        };
    }

    /**
     * Function to get repository-implementation of IdGenerator with repository name. If the property "dgm.id-file" is set,
     * a file-based id generator for the file given in that property is returned.
     * @param repositoryName name of repository
     * @param allowLoadFromFile Switch to control whether loading ids from exported files is allowed or not
     * @return IdGenerator implementation of repository
     */
    public static IdGenerator getIdGenerator(String repositoryName, boolean allowLoadFromFile){
        if(allowLoadFromFile && idImportRequested()){
            return new FileBasedIdGenerator();
        } else {
            return switch (repositoryName) {
                case "npm" -> new NpmIdGenerator();
                case "maven" -> new MavenIdGenerator();
                case "pypi" -> new PyPiIdGenerator();
                case "nuget" -> new NugetIdGenerator();
                /* --> Add new repositories here */
                default -> null;
            };
        }
    }

    /**
     * Function to register all VersionRangeResolver implementations to GlobalVersionRangeResolver
     * @param versionRangeResolver current GlobalVersionRangeResolver
     */
    public static void registerAllVersionRangeResolver(GlobalVersionRangeResolver versionRangeResolver){
        versionRangeResolver.registerResolver("maven", new MavenVersionRangeResolver());
        versionRangeResolver.registerResolver("npm", new NpmVersionRangeResolver());
        versionRangeResolver.registerResolver("pypi", new PyPiVersionRangeResolver());
        versionRangeResolver.registerResolver("nuget", new NugetVersionRangeResolver());
        /* --> Add new repositories here */
    }

    private static boolean idImportRequested(){
        return GlobalUtilities.isBoolPropertyEnabled("dgm.import-ids");
    }

}
