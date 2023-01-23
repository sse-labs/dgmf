package Repositories.Nuget;

import org.junit.jupiter.api.*;

import java.util.List;

public class NugetIdGeneratorTest {

    private NugetIdGenerator generator = null;

    @BeforeEach
    void init(){
        System.setProperty("dgm.limit", "20");
        generator = new NugetIdGenerator();
    }

    @AfterEach
    void clearResolver(){
        generator = null;
    }

    @Test
    @DisplayName("Generating valid ids")
    void test_generate_ids() {
        List<String> ids = generator.generateIds();

        assert(!ids.isEmpty());

        System.out.println("Got a total of " + ids.size() + " artifacts.");
    }

}
