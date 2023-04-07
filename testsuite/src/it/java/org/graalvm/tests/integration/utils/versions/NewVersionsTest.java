/*
 * Copyright (c) 2023, Red Hat, Inc.
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

package org.graalvm.tests.integration.utils.versions;

import static org.graalvm.tests.integration.utils.Commands.IS_THIS_WINDOWS;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;

import org.graalvm.tests.integration.utils.Logs;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

/**
 * Testing test suite...
 *
 * A fake native-image executable script is created, it contains a single version string.
 * Using the property FAKE_NATIVE_IMAGE_DIR, the test suite is tricked into running it
 * during native-image version resolution.
 * A series of test methods is executed by JUnit, business as usual. Those test methods log
 * their executing into a file.
 * After all tests are done, the file is examined. If it contains any superfluous entries
 * or if it's missing anything, the test fails.
 */
@Tag("testing-testsuite")
public class NewVersionsTest {

    static final StandardOpenOption[] LOG_FILE_OPS = new StandardOpenOption[]{StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.WRITE};
    static final String VERSION = "native-image 17.0.6 2023-01-17" + System.lineSeparator()
        + "GraalVM Runtime Environment Mandrel-23.0.0-dev (build 17.0.6+10)" + System.lineSeparator()
        + "Substrate VM Mandrel-23.0.0-dev (build 17.0.6+10, serial gc)";
    static final Path TEMP_DIR;
    static final Path LOG;
    static final Path NATIVE_IMAGE;

    static {
        try {
            TEMP_DIR = Files.createTempDirectory(NewVersionsTest.class.getSimpleName());
            LOG = TEMP_DIR.resolve(Path.of("new-versions-log"));
            NATIVE_IMAGE = TEMP_DIR.resolve(Path.of(IS_THIS_WINDOWS ? "native-image.cmd" : "native-image"));
        } catch (IOException e) {
            throw new RuntimeException("Failed setting up temp directory for test");
        }
    }

    @BeforeAll
    public static void setup() throws IOException {
        System.setProperty("FAKE_NATIVE_IMAGE_DIR", TEMP_DIR.toAbsolutePath().toString() + File.separator);
        Files.writeString(NATIVE_IMAGE, getNativeImageVersionScript(),
                StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        if (!IS_THIS_WINDOWS) {
            Files.setPosixFilePermissions(NATIVE_IMAGE, PosixFilePermissions.fromString("rwxr-xr-x"));
        }
        Files.deleteIfExists(LOG);
        // Reset parsed instances so as to avoid side-effects on other tests
        UsedVersion.Locally.resetInstance();
    }

    @AfterAll
    public static void teardown() throws IOException {
        System.clearProperty("FAKE_NATIVE_IMAGE_DIR");
        Files.deleteIfExists(NATIVE_IMAGE);
        try {
            final String testlog = Files.readString(LOG, StandardCharsets.UTF_8);
            assertEquals(
                    "Running test jdkNewVersionCheckA\n" +
                    "Running test jdkNewVersionCheckB\n", testlog);
        } finally {
            Logs.archiveLog(NewVersionsTest.class.getCanonicalName(), "versionTest", LOG.toFile());
            Files.deleteIfExists(LOG);
            Files.deleteIfExists(TEMP_DIR);
        }
    }

    private static String getNativeImageVersionScript() {
        StringBuilder builder = new StringBuilder();
        if (IS_THIS_WINDOWS) {
            builder.append("@echo off" + System.lineSeparator());
        } else {
            builder.append("#!/bin/sh" + System.lineSeparator());
        }
        for (String line: VERSION.split(System.lineSeparator())) {
            builder.append(IS_THIS_WINDOWS ?
                        "echo " + line + System.lineSeparator() :
                        "echo '" + line + "'" + System.lineSeparator());
        }
        return builder.toString();
    }

    @Test
    @Order(1)
    @IfMandrelVersion(min = "23.0.0")
    public void jdkNewVersionCheckA(TestInfo testInfo) throws IOException {
        Files.writeString(LOG, "Running test " + testInfo.getTestMethod().get().getName() + "\n", StandardCharsets.UTF_8, LOG_FILE_OPS);
    }

    @Test
    @Order(2)
    @IfMandrelVersion(min = "23.0.0", max = "23.1.0")
    public void jdkNewVersionCheckB(TestInfo testInfo) throws IOException {
        Files.writeString(LOG, "Running test " + testInfo.getTestMethod().get().getName() + "\n", StandardCharsets.UTF_8, LOG_FILE_OPS);
    }

    @Test
    @Order(3)
    @IfMandrelVersion(min = "30.0")
    public void jdkNewVersionCheckC(TestInfo testInfo) throws IOException {
        Files.writeString(LOG, "Running test " + testInfo.getTestMethod().get().getName() + "\n", StandardCharsets.UTF_8, LOG_FILE_OPS);
    }

    @Test
    @Order(4)
    @IfMandrelVersion(min = "20.0.0", max = "22.3")
    public void jdkNewVersionCheckD(TestInfo testInfo) throws IOException {
        Files.writeString(LOG, "Running test " + testInfo.getTestMethod().get().getName() + "\n", StandardCharsets.UTF_8, LOG_FILE_OPS);
    }
}
