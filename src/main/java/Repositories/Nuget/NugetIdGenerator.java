package Repositories.Nuget;

import Repositories.RepositoryController;
import Utilities.HttpUtilities;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.*;

/**
 * Id generator implementation for Nuget.org. Uses the nuget catalog to iterator creation- and deletion-events.
 */
public class NugetIdGenerator implements RepositoryController.IdGenerator {
    private static final String catalogIndexRoot = "https://api.nuget.org/v3/index.json";

    private final int limitPages;
    private final int offsetPages;

    /**
     * Create a new Nuget id generator that reads offset and limit from properties.
     */
    public NugetIdGenerator(){
        Properties props = System.getProperties();
        limitPages = Integer.parseInt(props.getOrDefault("dgm.limit","10").toString());
        offsetPages = Integer.parseInt(props.getOrDefault("dgm.offset","0").toString());
    }

    @Override
    public List<String> generateIds() {
        String currentCatalogUrl = getCurrentCatalogUrl();

        if(currentCatalogUrl == null) return null;

        JSONObject currentCatalogJson = HttpUtilities.getContentAsJSON(currentCatalogUrl);

        if(currentCatalogJson == null) return null;

        List<String> relevantPageUrls = getRelevantPageUrls(currentCatalogJson);

        if(relevantPageUrls == null) return null;

        Set<String> packageNames = new HashSet<>();

        int pageCnt = 0;

        for(String pageUrl : relevantPageUrls){
            JSONObject pageJson = HttpUtilities.getContentAsJSON(pageUrl);

            if(pageJson != null){
                JSONArray pageItems = pageJson.getJSONArray("items");

                for(int i = 0; i < pageItems.length(); i++){
                    JSONObject currItem = pageItems.getJSONObject(i);
                    String itemType = currItem.getString("@type").toLowerCase();
                    String packageUrl = currItem.getString("@id");

                    if(itemType.equals("nuget:packagedetails")){
                        packageNames.add(packageUrl);
                    } else if(itemType.equals("nuget:packagedelete")) {
                        packageNames.remove(packageUrl);
                    } else {
                        System.err.println("Unknown catalog item type: " + itemType);
                    }

                }
            } else {
                System.err.println("Got null response when querying catalog page " + pageUrl);
            }

            if(++pageCnt % 5 == 0){
                System.out.println("Processing catalog page #" + pageCnt);
            }

        }

        return new ArrayList<>(packageNames);
    }

    private List<String> getRelevantPageUrls(JSONObject catalogJson){

        int totalPages = catalogJson.getInt("count");

        if(offsetPages >= totalPages){
            System.err.println("Invalid offset, total number of pages is " + totalPages);
            return null;
        }

        List<JSONObject> pages = new ArrayList<>();
        JSONArray itemsArray = catalogJson.getJSONArray("items");

        for(int i=0; i < itemsArray.length(); i++){
            pages.add(itemsArray.getJSONObject(i));
        }

        // Sort by commit time to get reliable ordering
        pages.sort(Comparator.comparing( o -> o.getString("commitTimeStamp")));

        List<String> relevantPageUrls = new ArrayList<>();

        for(int i = offsetPages; i < Math.min(offsetPages + limitPages, totalPages); i++){
            relevantPageUrls.add(pages.get(i).getString("@id"));
        }

        return relevantPageUrls;
    }

    private String getCurrentCatalogUrl(){
        JSONObject catalogIndexJson = HttpUtilities.getContentAsJSON(catalogIndexRoot);

        if(catalogIndexJson == null) return null;

        JSONArray catalogResources = catalogIndexJson.getJSONArray("resources");

        String catalogUrl = null;

        for(int i = 0; i < catalogResources.length(); i++){
            JSONObject currentEntry = catalogResources.getJSONObject(i);

            if(currentEntry.getString("@type").equals("Catalog/3.0.0"))
                catalogUrl = currentEntry.getString("@id");

        }

        return catalogUrl;
    }


}
