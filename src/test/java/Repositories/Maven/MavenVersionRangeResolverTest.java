package Repositories.Maven;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.*;

import java.util.Set;

/**
 * Test for Maven version range resolution based on https://docs.oracle.com/middleware/1212/core/MAVEN/maven_version.htm#CJHDEHAB
 */
class MavenVersionRangeResolverTest {

    private static MavenVersionRangeResolver theResolver;

    private final Set<String> sampleTargetVersions = Set.of("1.0.0", "1.2.3", "1.3.3-SNAPSHOT", "2.0.0");

    @BeforeAll
    static void init(){
        theResolver = new MavenVersionRangeResolver();
    }

    @BeforeEach
    void clearResolver(){
        theResolver.clear();
    }

    @Test
    @DisplayName("Finding matching versions for fixed ranges")
    void test_findMatchingVersions_1() {
        assertEquals(Set.of("1.2.3"), theResolver.findMatchingVersions("1.2.3", sampleTargetVersions));
        assertEquals(Set.of("1.0.0"), theResolver.findMatchingVersions("1.0.0-SNAPSHOT", sampleTargetVersions));
        assertEquals(Set.of("2.0.0"), theResolver.findMatchingVersions("[2.0.0]", sampleTargetVersions));
    }

    @Test
    @DisplayName("Finding matching versions for real ranges")
    void test_findMatchingVersions_2(){
        Set<String> target = Set.of("1.2.3", "1.3.3-SNAPSHOT");
        Set<String> foundVersions = theResolver.findMatchingVersions("[1.2.3, 2.0.0)", sampleTargetVersions);
        System.out.println(target+ "  "+foundVersions);
        assertEquals(Set.of("1.2.3", "1.3.3-SNAPSHOT"), foundVersions);
        assertEquals(Set.of("1.3.3-SNAPSHOT"), theResolver.findMatchingVersions("[1.3.0,2.0.0]", sampleTargetVersions));
    }

    @Test
    @DisplayName("Finding matching versions if patch version is missing")
    void text_findMatchingVersions_3(){
        Set<String> target = Set.of("1.2.3", "1.3.3-SNAPSHOT");
        Set<String> foundVersions = theResolver.findMatchingVersions("[1.0.1,2.0]", sampleTargetVersions);
        System.out.println(target+ "  "+foundVersions);
        assertEquals(target, foundVersions);
    }

    @Test
    @DisplayName("Finding matching for or case")
    void text_findMatchingVersions_4(){
        Set<String> target = Set.of("1.2.3","1.0.0");
        Set<String> foundVersions = theResolver.findMatchingVersions("[1.2.3],[1.0.0]", sampleTargetVersions);
        System.out.println(target+ "  "+foundVersions);
        assertEquals(target, foundVersions);
    }

    @Test
    @DisplayName("Identification of actual version ranges")
    void test_isRangeSpecification() {
        assertTrue(theResolver.isRangeSpecification("(,1.0]"));
        assertFalse(theResolver.isRangeSpecification("1.0"));
        assertFalse(theResolver.isRangeSpecification("[1.0]"));
        assertTrue(theResolver.isRangeSpecification("[1.0.0,1.2.3]"));
    }
}