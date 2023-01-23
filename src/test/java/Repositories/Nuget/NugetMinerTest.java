package Repositories.Nuget;

import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import Model.Package;

import java.util.List;

public class NugetMinerTest {

    private NugetIdGenerator generator = null;
    private NugetMiner miner = null;

    @BeforeEach
    void init(){
        System.setProperty("dgm.limit", "1");
        generator = new NugetIdGenerator();
        miner = new NugetMiner();
    }

    @AfterEach
    void clearResolver(){
        generator = null;
        miner = null;
    }

    @Test
    @DisplayName("Mining valid packages")
    void test_mine_packages() {
        List<String> ids = generator.generateIds();

        assert(!ids.isEmpty());


        System.out.println("Starting Miner...");

        ids.subList(0, 20).forEach( id -> {
            JSONObject p = miner.minePackage(id);

            assert(p != null);

            Package result = miner.parsePackage(p);

            assert(result.getArtifactList().size() == 1);

            System.out.println("Package " + result.getArtifactList().get(0).getId() + " has " + result.getArtifactList().get(0).getDependencies().size() + " dependencies.");
        });
    }

}
