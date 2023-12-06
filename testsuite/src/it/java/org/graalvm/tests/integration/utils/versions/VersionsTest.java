package org.graalvm.tests.integration.utils.versions;
/*
 * Copyright (c) 2022, Red Hat Inc. All rights reserved.
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

import org.graalvm.tests.integration.utils.Logs;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static org.graalvm.tests.integration.utils.Commands.IS_THIS_WINDOWS;
import static org.graalvm.tests.integration.utils.Commands.getProperty;
import static org.graalvm.tests.integration.utils.thresholds.ThresholdsTest.createFakeNativeImageFile;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Testing test suite...
 *
 * A fake native-image executable script is created, it contains a single version string.
 * Using the property FAKE_NATIVE_IMAGE_DIR, the test suite is tricked into running it
 * during native-image version resolution.
 * A series of test methods is executed by JUnit, business as usual. Those test methods log
 * their executing into a file.
 * After all tests are done, the file is examined. If it contains any superfluous entries
 * or if it's missing anything, the test lass fails.
 *
 * @author Michal Karm Babacek <karm@redhat.com>
 */
@Tag("testing-testsuite")
public class VersionsTest {
    static final StandardOpenOption[] LOG_FILE_OPS = new StandardOpenOption[] { StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.WRITE };
    static final String VERSION = "native-image 22.2.0-devdb26f5c4fbe Mandrel Distribution (Java Version 17.0.3-beta+5-202203292328)";
    static final String QUARKUS_VERSION = "3.6.0";
    static final String QUARKUS_VERSION_TMP;
    static final Path TEMP_DIR;
    static final Path LOG;
    static final Path NATIVE_IMAGE;

    static {
        try {
            TEMP_DIR = Files.createTempDirectory(VersionsTest.class.getSimpleName());
            LOG = TEMP_DIR.resolve(Path.of("versions-log"));
            NATIVE_IMAGE = TEMP_DIR.resolve(Path.of(IS_THIS_WINDOWS ? "native-image.cmd" : "native-image"));
            QUARKUS_VERSION_TMP = getProperty("QUARKUS_VERSION", QuarkusVersion.DEFAULT_VERSION);
        } catch (IOException e) {
            throw new RuntimeException("Failed setting up temp directory for test");
        }
    }

    @BeforeAll
    public static void setup() throws IOException {
        System.setProperty("FAKE_NATIVE_IMAGE_DIR", TEMP_DIR.toAbsolutePath() + File.separator);
        System.setProperty("QUARKUS_VERSION", QUARKUS_VERSION);
        createFakeNativeImageFile(NATIVE_IMAGE, VERSION);
        Files.deleteIfExists(LOG);
        // Reset parsed instances to avoid side effects on other tests
        // Note that container instance isn't being used, so isn't reset.
        UsedVersion.Locally.resetInstance();
    }

    @AfterAll
    public static void teardown() throws IOException {
        System.clearProperty("FAKE_NATIVE_IMAGE_DIR");
        System.setProperty("QUARKUS_VERSION", QUARKUS_VERSION_TMP);
        Files.deleteIfExists(NATIVE_IMAGE);
        final String testlog = Files.readString(LOG, StandardCharsets.UTF_8);
        try {
            assertEquals(
                    "Running test quarkusVersionCheckA\n" +
                            "Running test jdkVersionCheckA\n" +
                            "Running test jdkVersionCheckB\n" +
                            "Running test jdkVersionCheckC\n" +
                            "Running test jdkVersionCheckD\n" +
                            "Running test jdkVersionCheckI\n", testlog);
        } finally {
            Logs.archiveLog(VersionsTest.class.getCanonicalName(), "versionTest", LOG.toFile());
            Files.deleteIfExists(LOG);
            Files.deleteIfExists(TEMP_DIR);
        }
    }

    private static void test(TestInfo testInfo) throws IOException {
        Files.writeString(LOG, "Running test " + testInfo.getTestMethod().get().getName() + "\n", StandardCharsets.UTF_8, LOG_FILE_OPS);
    }

    @Test
    @Order(1)
    @IfMandrelVersion(min = "21.3.1")
    public void jdkVersionCheckA(TestInfo testInfo) throws IOException {
        test(testInfo);
    }

    @Test
    @Order(2)
    @IfMandrelVersion(min = "21.3.1", minJDK = "17")
    public void jdkVersionCheckB(TestInfo testInfo) throws IOException {
        test(testInfo);
    }

    @Test
    @Order(3)
    @IfMandrelVersion(min = "22", minJDK = "17")
    public void jdkVersionCheckC(TestInfo testInfo) throws IOException {
        test(testInfo);
    }

    @Test
    @Order(4)
    @IfMandrelVersion(min = "22", minJDK = "17.0.2")
    public void jdkVersionCheckD(TestInfo testInfo) throws IOException {
        test(testInfo);
    }

    @Test
    @Order(5)
    @IfMandrelVersion(min = "22", minJDK = "17", maxJDK = "17.0.2")
    public void jdkVersionCheckE(TestInfo testInfo) throws IOException {
        test(testInfo);
    }

    @Test
    @Order(6)
    @IfMandrelVersion(min = "21", minJDK = "11.0.12", maxJDK = "17.0.1")
    public void jdkVersionCheckF(TestInfo testInfo) throws IOException {
        test(testInfo);
    }

    @Test
    @Order(7)
    @IfMandrelVersion(min = "21", maxJDK = "11")
    public void jdkVersionCheckG(TestInfo testInfo) throws IOException {
        test(testInfo);
    }

    @Test
    @Order(8)
    @IfMandrelVersion(min = "21.2", minJDK = "18.0.0")
    public void jdkVersionCheckH(TestInfo testInfo) throws IOException {
        test(testInfo);
    }

    @Test
    @Order(9)
    @IfMandrelVersion(min = "22", max = "22.2", minJDK = "17.0.1", maxJDK = "17.0.3")
    public void jdkVersionCheckI(TestInfo testInfo) throws IOException {
        test(testInfo);
    }

    @Test
    @Order(10)
    @IfMandrelVersion(min = "22")
    @IfQuarkusVersion(min = "3.6.0")
    public void quarkusVersionCheckA(TestInfo testInfo) throws IOException {
        test(testInfo);
    }

    @Test
    @Order(11)
    @IfMandrelVersion(min = "22")
    @IfQuarkusVersion(max = "3.5.0")
    public void quarkusVersionCheckB(TestInfo testInfo) throws IOException {
        test(testInfo);
    }

    @Test
    @Order(12)
    @IfMandrelVersion(min = "22")
    @IfQuarkusVersion(max = "3")
    public void quarkusVersionCheckC(TestInfo testInfo) throws IOException {
        test(testInfo);
    }

}
