package Repositories.NPM;

import Repositories.RepositoryController;
import Utilities.HttpUtilities;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class NpmIdGenerator implements RepositoryController.IdGenerator {

    private static final String allPackagesUrlGitHub = "https://raw.githubusercontent.com/bconnorwhite/all-package-names/master/data/all.json?raw=true";
    private static final String packageArrayJsonKey = "packageNames";

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private int limit = -1;
    private int offset = 0;

    public NpmIdGenerator() {
        Properties props = System.getProperties();
        this.limit = Integer.parseInt(props.getOrDefault("dgm.limit","-1").toString());
        this.offset = Integer.parseInt(props.getOrDefault("dgm.offset","0").toString());
    }


    @Override
    public List<String> generateIds() {

        logger.debug("Starting to retrieve NPM ids from GitHub...");

        JSONObject responseObj = HttpUtilities.getContentAsJSON(allPackagesUrlGitHub);

        if(responseObj != null && responseObj.has(packageArrayJsonKey)){
            JSONArray packageNames = responseObj.getJSONArray(packageArrayJsonKey);

            List<String> plainPackageNames = new ArrayList<>(packageNames.length());

            for(int i = offset ; i < packageNames.length() ; i++){
                plainPackageNames.add(packageNames.getString(i));

                if(limit > 0 && i >= limit + offset) break;
            }

            logger.info("Done loading " + plainPackageNames.size() + " package names for NPM.");

            return plainPackageNames;
        } else {
            logger.error("Malformed GitHub response, cannot load package names");
            return null;
        }
    }
}
