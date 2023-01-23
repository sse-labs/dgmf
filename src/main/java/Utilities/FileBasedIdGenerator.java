package Utilities;

import Repositories.RepositoryController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Properties;

public class FileBasedIdGenerator implements RepositoryController.IdGenerator {

    private final Logger logger = LoggerFactory.getLogger(getClass());


    @Override
    public List<String> generateIds() {
        Properties props = System.getProperties();
        String repo = props.getProperty("dgm.repo", "npm");
        String defaultFileName = repo + "_ids.txt";

        int limit = Integer.parseInt(props.getOrDefault("dgm.limit","0").toString());
        int offset = Integer.parseInt(props.getOrDefault("dgm.offset","0").toString());

        Path inputFilePath = Paths.get(props.getProperty("dgm.id-file", defaultFileName));

        if(Files.exists(inputFilePath)){

            List<String> ids;
            try{
                logger.info("Loading ids for repo " + repo + " from file " + inputFilePath);
                ids = Files.readAllLines(inputFilePath);
                logger.info("Done loading " + ids.size() + " ids from file.");
            } catch (IOException iox){
                logger.error("Failed to load ids from file", iox);
                return null;
            }

            if(offset > 0 && offset < ids.size()){
                ids = ids.subList(offset, ids.size());
            }

            if(limit > 0 && limit < ids.size()){
                ids = ids.subList(0, limit);
            }

            return ids;
        } else {
            logger.error("Input file not found at " + inputFilePath);
            return null;
        }
    }
}
