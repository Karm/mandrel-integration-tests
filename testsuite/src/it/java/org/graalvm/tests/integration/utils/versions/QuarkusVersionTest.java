package org.graalvm.tests.integration.utils.versions;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testing test suite...
 *
 * Test the QuarkusVersion class.
 */
@Tag("testing-testsuite")
class QuarkusVersionTest
{

    @Test
    void compareTo()
    {
        Assertions.assertEquals(0, QuarkusVersion.V_2_2_4.compareTo(new QuarkusVersion("2.2.4")));
        Assertions.assertEquals(0, QuarkusVersion.V_2_2_4.compareTo(QuarkusVersion.V_2_2_4));
        // comparison with different major
        Assertions.assertTrue(QuarkusVersion.V_3_7_0.compareTo(QuarkusVersion.V_2_2_4) > 0);
        Assertions.assertTrue(QuarkusVersion.V_2_4_0.compareTo(QuarkusVersion.V_3_0_0) < 0);
        // comparison with different minor
        Assertions.assertTrue(QuarkusVersion.V_2_3_0.compareTo(QuarkusVersion.V_2_2_4) > 0);
        Assertions.assertTrue(QuarkusVersion.V_2_3_0.compareTo(QuarkusVersion.V_2_4_0) < 0);
        // comparison with different patch
        Assertions.assertTrue(QuarkusVersion.V_2_2_4.compareTo(new QuarkusVersion("2.2.3")) > 0);
        Assertions.assertTrue(QuarkusVersion.V_2_2_4.compareTo(new QuarkusVersion("2.2.5")) < 0);
        // comparison with snapshot
        Assertions.assertTrue(new QuarkusVersion("2.2.4-SNAPSHOT").compareTo(QuarkusVersion.V_2_2_4) > 0);
        Assertions.assertTrue(QuarkusVersion.V_2_2_4.compareTo(new QuarkusVersion("2.2.4-SNAPSHOT")) < 0);
        Assertions.assertTrue(new QuarkusVersion("2.3.4-SNAPSHOT").compareTo(new QuarkusVersion("2.2.4-SNAPSHOT")) > 0);
        Assertions.assertTrue(new QuarkusVersion("2.3.4-SNAPSHOT").compareTo(new QuarkusVersion("3.2.4-SNAPSHOT")) < 0);
        Assertions.assertTrue(new QuarkusVersion("3.8.3").compareTo(new QuarkusVersion("3.8.999-SNAPSHOT")) < 0);
        Assertions.assertTrue(new QuarkusVersion("999-SNAPSHOT").compareTo(new QuarkusVersion("3.9.2")) > 0);
    }

    @Test
    void majorIs()
    {
        Assertions.assertTrue(QuarkusVersion.V_2_2_4.majorIs(2));
        Assertions.assertTrue(new QuarkusVersion("1.3.4-SNAPSHOT").majorIs(1));
        Assertions.assertTrue(QuarkusVersion.V_3_7_0.majorIs(3));
        Assertions.assertTrue(new QuarkusVersion("3.2.1-SNAPSHOT").majorIs(3));
        Assertions.assertFalse(QuarkusVersion.V_3_7_0.majorIs(2));
        Assertions.assertFalse(new QuarkusVersion("3.2.1-SNAPSHOT").majorIs(5));
    }

    @Test
    void isSnapshot()
    {
        Assertions.assertFalse(QuarkusVersion.V_2_2_4.isSnapshot());
        Assertions.assertTrue(new QuarkusVersion("1.3.4-SNAPSHOT").isSnapshot());
        Assertions.assertFalse(new QuarkusVersion("1.3.4.Final").isSnapshot());
        Assertions.assertFalse(QuarkusVersion.V_3_7_0.isSnapshot());
        Assertions.assertTrue(new QuarkusVersion("3.2.1-SNAPSHOT").isSnapshot());
        Assertions.assertFalse(new QuarkusVersion("3.2.1-ABD").isSnapshot());
    }
}
