package Repositories.PyPi;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Information on PyPi versioning:
 *  *  - <a href="https://peps.python.org/pep-0440/#version-specifiers">Specification</a>
 */
public class PyPiVersionRangeResolverTest {

    private static PyPiVersionRangeResolver theResolver;

    // All lodash NPM versions for testing (https://semver.npmjs.com/)
    private final List<String> sampleTargetVersionList = List.of("0.1.0", "0.2.0", "0.2.1", "0.2.2", "0.3.0", "0.3.1", "0.3.2", "0.4.0",
            "0.4.1", "0.4.2", "0.5.0-rc.1", "0.5.0", "0.5.1", "0.5.2", "0.6.0", "0.6.1", "0.7.0", "0.8.0", "0.8.1", "0.8.2", "0.9.0",
            "0.9.1", "0.9.2", "0.10.0", "1.0.0-rc.1", "1.0.0-rc.2", "1.0.0-rc.3", "1.0.0", "1.0.1", "1.1.0", "1.1.1", "1.2.0", "1.2.1",
            "1.3.0", "1.3.1", "2.0.0", "2.1.0", "2.2.0", "2.2.1", "2.3.0", "2.4.0", "2.4.1", "3.0.0", "3.0.1", "3.1.0", "3.2.0",
            "3.3.0", "3.3.1", "3.4.0", "3.5.0", "3.6.0", "1.0.2", "3.7.0", "2.4.2", "3.8.0", "3.9.0", "3.9.1", "3.9.2", "3.9.3",
            "3.10.0", "3.10.1", "4.0.0", "4.0.1", "4.1.0", "4.2.0", "4.2.1", "4.3.0", "4.4.0", "4.5.0", "4.5.1", "4.6.0", "4.6.1",
            "4.7.0", "4.8.0", "4.8.1", "4.8.2", "4.9.0", "4.10.0", "4.11.0", "4.11.1", "4.11.2", "4.12.0", "4.13.0", "4.13.1", "4.14.0",
            "4.14.1", "4.14.2", "4.15.0", "4.16.0", "4.16.1", "4.16.2", "4.16.3", "4.16.4", "4.16.5", "4.16.6", "4.17.0", "4.17.1",
            "4.17.2", "4.17.3", "4.17.4", "4.17.5", "4.17.9", "4.17.10", "4.17.11", "4.17.12", "4.17.13", "4.17.14", "4.17.15",
            "4.17.16", "4.17.17", "4.17.18", "4.17.19", "4.17.20", "4.17.21");

    private final Set<String> sampleTargetVersions = Set.copyOf(sampleTargetVersionList);

    @BeforeAll
    static void init(){
        theResolver = new PyPiVersionRangeResolver();
    }

    @BeforeEach
    void clearResolver(){
        theResolver.clear();
    }


    @Test
    @DisplayName("Find matching PyPi versions for fixed ranges")
    void test_findMatchingVersion_PyPi_1(){
        assertResultsIn("==0.5.0", Set.of("0.5.0", "0.5.0-rc.1"));
        assertResultsIn("===0.4.0", Set.of("0.4.0"));
    }

    @Test
    @DisplayName("Find matching PyPi versions for greater-than ranges")
    void test_findMatchingVersion_PyPi_2(){
        assertResultsIn(">4.17.19", Set.of("4.17.20", "4.17.21"));
        assertResultsIn(">=4.17.19", Set.of("4.17.19", "4.17.20", "4.17.21"));
    }

    @Test
    @DisplayName("Find matching PyPi versions for lower-than ranges")
    void test_findMatchingVersion_PyPi_3(){
        assertResultsIn("<0.2.2", Set.of("0.1.0", "0.2.0", "0.2.1"));
        assertResultsIn("<=0.2.2", Set.of("0.1.0", "0.2.0", "0.2.1", "0.2.2"));
    }

    @Test
    @DisplayName("Find matching PyPi versions for the compatibility clause")
    void test_findMatchingVersion_PyPi_4(){
        assertResultsIn("~=0.2.1", Set.of("0.2.1", "0.2.2"));
        assertResultsIn("~=3.9", Set.of("3.9.0", "3.9.1", "3.9.2", "3.9.3", "3.10.0", "3.10.1"));
    }

    @Test
    @DisplayName("Find matching PyPi versions with exclusions")
    void test_findMatchingVersion_PyPi_5(){
        assertResultsIn(">=4.17.19, != 4.17.20", Set.of("4.17.19", "4.17.21"));
        assertResultsIn("<0.2.2, != 0.1.0", Set.of("0.2.0", "0.2.1"));
    }

    @Test
    @DisplayName("Find matching PyPi versions with multiple clauses")
    void test_findMatchingVersion_PyPi_6(){
        assertResultsIn(">0.1.0,<0.2.2", Set.of("0.2.0", "0.2.1"));
    }

    @Test
    @DisplayName("Find matching PyPi versions with non-three-part numbers")
    void test_findMatchingVersion_PyPi_7(){
        assertResultsIn("<1", Set.of("0.1.0", "0.2.0", "0.2.1", "0.2.2", "0.3.0", "0.3.1", "0.3.2", "0.4.0", "0.4.1", "0.4.2",
                "0.5.0-rc.1", "0.5.0", "0.5.1", "0.5.2", "0.6.0", "0.6.1", "0.7.0", "0.8.0", "0.8.1", "0.8.2", "0.9.0", "0.9.1", "0.9.2", "0.10.0"));
        assertResultsIn(">2, <=2", Set.of());
        assertResultsIn("<0.3.0, != 0.2", Set.of("0.1.0", "0.2.1", "0.2.2")); // In PyPi, "!= 0.2" should be expanded to "!=0.2.0" and only exclude this exact version number
        // PyPiVersionRangeResolver doesn't support and with more than two arguments
        //assertResultsIn("!=1, >0.10, <= 1.0.0", Set.of("1.0.0-rc.2", "1.0.0-rc.1", "1.0.0-rc.3")); // In PyPi, "!=1" should be expanded to "!=1.0.0" and only exclude this exact version number
    }


    private void assertResultsIn(String range, Set<String> expectedResult){
        assertEquals(expectedResult, theResolver.findMatchingVersions(range, sampleTargetVersions));
    }

}
