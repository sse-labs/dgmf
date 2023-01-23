package Repositories.Maven;

import Application.ExceptionLogger;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import Repositories.RepositoryController;
import org.apache.maven.index.reader.IndexReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static Utilities.GlobalUtilities.*;


/**
 * Id Generator implementation for the Maven Central repository. Uses the official Maven Index Reader implementation to
 * access the Maven Central lucene index via HTTP.
 */
public class MavenIdGenerator implements RepositoryController.IdGenerator {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private int limit;
    private final int offset;
    private static final String urlBase = "https://repo1.maven.org/maven2/";
    private int uniquePackageCount = 0;

    /** Constructor of the Name Generator, gets a limit and offset for the list of names to generate */
    public MavenIdGenerator() {
        Properties props = System.getProperties();
        this.limit = Integer.parseInt(props.getOrDefault("dgm.limit","0").toString());
        this.offset = Integer.parseInt(props.getOrDefault("dgm.offset","0").toString());
    }

    @Override
    public List<String> generateIds() {
        long startTime = System.currentTimeMillis();
        logger.info("== Mining of Maven Package names ==");

        ArrayList<String> packageIds = new ArrayList<>();
        ArrayList<String> uniquePackageIds = new ArrayList<>();

        try {
            IndexReader indexReader = new IndexReader(null, new HttpResourceHandler(urlBase + ".index/"));
            Iterator<Map<String, String>> iterator = indexReader.iterator().next().iterator();
            while (iterator.hasNext()) {
                String[] propertyU = iterator.next().toString().split(", u=");
                try {
                    propertyU = propertyU[1].split(", i=");
                    propertyU = propertyU[0].split("[|]");
                    String newPackageId = propertyU[0].replaceAll("[.]", "/") + "/" + propertyU[1];
                    packageIds.add(newPackageId);

                    /* For every 100000 packages, start collecting package names without duplicates */
                    if(packageIds.size()>=100000) {
                        uniquePackageIds.addAll(packageIds.stream().distinct().collect(Collectors.toList()));
                        packageIds = new ArrayList<>();
                        uniquePackageIds = uniquePackageIds.stream().distinct().collect(Collectors.toCollection(ArrayList::new));
                        int currentSize = uniquePackageIds.size();
                        if(currentSize-uniquePackageCount>50000){
                            printPercentage(currentSize);
                            uniquePackageCount = currentSize;
                        }
                        if(currentSize>(limit+offset)&&limit!=0)
                            break;
                    }
                } catch (IndexOutOfBoundsException ex) {
                    //Logger.add(ex,this.getClass().getName());
                }
            }
        } catch (IOException e) {
            logger.error("Error while reading Maven Central index", e);
            ExceptionLogger.add(e,this.getClass().getName());
        }

        uniquePackageIds = uniquePackageIds.stream().distinct().collect(Collectors.toCollection(ArrayList::new));
        if (this.limit == 0) {
            this.limit = uniquePackageIds.size() - offset;
        }
        long endTime = System.currentTimeMillis();
        int generatingTime = Math.round(((float) (endTime - startTime) / 1000) / 60);
        logger.info("=> Time for mining of the " + this.limit + " package names: " + generatingTime + " minutes <=");

        return new ArrayList<>(uniquePackageIds.subList(offset, limit+offset-1));
    }

    private void printPercentage( int total) {
        logger.info("=> " + " [" + total + "] PackageIds (G.A) mined");
    }

}


