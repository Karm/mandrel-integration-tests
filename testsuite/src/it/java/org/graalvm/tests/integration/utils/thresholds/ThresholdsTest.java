package org.graalvm.tests.integration.utils.thresholds;
/*
 * Copyright (c) 2023, Red Hat Inc. All rights reserved.
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

import org.graalvm.tests.integration.utils.versions.QuarkusVersion;
import org.graalvm.tests.integration.utils.versions.UsedVersion;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Map;
import java.util.regex.Matcher;

import static org.graalvm.tests.integration.RuntimesSmokeTest.BASE_DIR;
import static org.graalvm.tests.integration.utils.Commands.IS_THIS_WINDOWS;
import static org.graalvm.tests.integration.utils.Commands.getProperty;
import static org.graalvm.tests.integration.utils.thresholds.Thresholds.MVERSION_PATTERN;
import static org.graalvm.tests.integration.utils.thresholds.Thresholds.QVERSION_PATTERN;
import static org.graalvm.tests.integration.utils.thresholds.Thresholds.parseProperties;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test thresholds config values
 *
 * @author Michal Karm Babacek <karm@redhat.com>
 */
@Tag("testing-testsuite")
public class ThresholdsTest {

    @ParameterizedTest
    //@formatter:off
    @CsvSource(value = {
    "@IfMandrelVersion(min = \"23\", max = \"23.3\" , minJDK = \"17.0.1\", maxJDK = \"20.0.0\")                   | 23  | 23.3     | 17.0.1 | 20.0.0 | N/A   ",
    "@IfMandrelVersion(maxJDK = \"20.0.0\", min = \"23\", max = \"23.3\", minJDK = \"17.0.1\", inContainer =true) | 23  | 23.3     | 17.0.1 | 20.0.0 | true  ",
    "@IfMandrelVersion(maxJDK = \"20.0.0\", min = \"23\", max = \"23.3\", minJDK = \"17.0.1\")                    | 23  | 23.3     | 17.0.1 | 20.0.0 | N/A   ",
    "  @IfMandrelVersion(max = \"23.3\", maxJDK = \"20.0.0\", min = \"23\", minJDK = \"17.0.1\")                  | 23  | 23.3     | 17.0.1 | 20.0.0 | N/A   ",
    "@IfMandrelVersion(min = \"24\", minJDK = \"21.0.1\")                                                         | 24  | N/A      | 21.0.1 | N/A    | N/A   ",
    "@IfMandrelVersion(inContainer = true,min = \"24\", minJDK = \"21.0.1\")                                      | 24  | N/A      | 21.0.1 | N/A    | true  ",
    "@IfMandrelVersion(  min = \"22\", max = \"22.3.999\")                                                        | 22  | 22.3.999 | N/A    | N/A    | N/A   ",
    "@IfMandrelVersion(max=     \"22.3.999\"     , min = \"22\")                                                  | 22  | 22.3.999 | N/A    | N/A    | N/A   ",
    "@IfMandrelVersion(max =     \"22.3.999\"     ,minJDK = \"17.0.1\",  min = \"22\")                            | 22  | 22.3.999 | 17.0.1 | N/A    | N/A   ",
    "@IfMandrelVersion(min = \"23\")                                                                              | 23  | N/A      | N/A    | N/A    | N/A   ",
    "@IfMandrelVersion (max = \"21\")                                                                             | N/A | 21       | N/A    | N/A    | N/A   ",
    "@IfMandrelVersion(maxJDK = \"23\",  minJDK=\"17\")                                                           | N/A | N/A      | 17     | 23     | N/A   ",
    "@IfMandrelVersion(maxJDK = \"23\",  inContainer =false, minJDK=\"17\")                                       | N/A | N/A      | 17     | 23     | false ",
    "@IfMandrelVersion(maxJDK = \"23\")                                                                           | N/A | N/A      | N/A    | 23     | N/A   ",
    "@IfMandrelVersion(  minJDK=\"17\"    )                                                                       | N/A | N/A      | 17     | N/A    | N/A   ",
    "@IfMandrelVersion(  minJDK=\"17\" ,inContainer =true   )                                                     | N/A | N/A      | 17     | N/A    | true  ",
    }, delimiter = '|')
    //@formatter:on
    public void testMandrelVersion(String expression, String min, String max, String minJDK, String maxJDK, String inContainer) {
        final Matcher m = MVERSION_PATTERN.matcher(expression);
        assertTrue(m.matches(), "Expression '" + expression + "' MUST match the pattern '" + MVERSION_PATTERN.pattern() + "'");
        if ("N/A".equals(min)) {
            assertNull(m.group("min"));
        } else {
            assertEquals(min, m.group("min"));
        }
        if ("N/A".equals(max)) {
            assertNull(m.group("max"));
        } else {
            assertEquals(max, m.group("max"));
        }
        if ("N/A".equals(minJDK)) {
            assertNull(m.group("minJDK"));
        } else {
            assertEquals(minJDK, m.group("minJDK"));
        }
        if ("N/A".equals(maxJDK)) {
            assertNull(m.group("maxJDK"));
        } else {
            assertEquals(maxJDK, m.group("maxJDK"));
        }
        if ("N/A".equals(inContainer)) {
            assertNull(m.group("inContainer"));
        } else {
            assertEquals(inContainer, m.group("inContainer"));
        }
    }

    @ParameterizedTest
    //@formatter:off
    @CsvSource(value = {
    "@IfQuarkusVersion(min = \"2.7.0.Final\", max = \"3\" )        | 2.7.0.Final | 3   ",
    "@IfQuarkusVersion(max = \"3\",min = \"2.7.0.Final\" )         | 2.7.0.Final | 3   ",
    "@IfQuarkusVersion(min=\"3.6.0\")                              | 3.6.0       | N/A ",
    "@IfQuarkusVersion( max = \"3.2\" )                            | N/A         | 3.2 ",
    " @IfQuarkusVersion (  max = \"3\",    min = \"2.7.0.Final\" ) | 2.7.0.Final | 3   ",
    }, delimiter = '|')
    //@formatter:on
    public void testQuarkusVersion(String expression, String min, String max) {
        final Matcher m = QVERSION_PATTERN.matcher(expression);
        assertTrue(m.matches(), "Expression '" + expression + "' MUST match the pattern '" + MVERSION_PATTERN.pattern() + "'");
        if ("N/A".equals(min)) {
            assertNull(m.group("min"));
        } else {
            assertEquals(min, m.group("min"));
        }
        if ("N/A".equals(max)) {
            assertNull(m.group("max"));
        } else {
            assertEquals(max, m.group("max"));
        }
    }

    @ParameterizedTest
    //@formatter:off
    @CsvSource(value = {
    // Native-image version string                                                        | Config file      | Q version   | a   | b   | c
    "native-image 21.0.1 2023-10-17\\n" +
    "OpenJDK Runtime Environment Mandrel-23.1.1.0-Final (build 21.0.1+12-LTS)\\n" +
    "OpenJDK 64-Bit Server VM Mandrel-23.1.1.0-Final (build 21.0.1+12-LTS, mixed mode)\\n | threshold-1.conf | 3.3.3.Final | 150 | 250 | 350 ",
    // -----------------------------------------------------------------------------------------------------------------------------------------
    "native-image 21.0.1 2023-10-17\\n" +
    "GraalVM Runtime Environment GraalVM CE 21.0.1-dev+12.1 (build 21.0.1+12-jvmci-23.1-b22)\\n" +
    "Substrate VM GraalVM CE 21.0.1-dev+12.1 (build 21.0.1+12, serial gc)\\n              | threshold-1.conf | 3.1.9.Final | 160 | 260 | 360 ",
    // -----------------------------------------------------------------------------------------------------------------------------------------
    "native-image 23 2026-09-01\\n" +
    "GraalVM Runtime Environment GraalVM CE 23+35.1 (build 23+35-jvmci-24.1-b99)\\n" +
    "Substrate VM GraalVM CE 23+35.1 (build 23+35, serial gc)\\n                          | threshold-1.conf | 3.7.0       | 170 | 270 | 100 ",
    // -----------------------------------------------------------------------------------------------------------------------------------------
    "native-image 21.3.6.0-Final Mandrel Distribution (Java Version 17.0.7+7)\\n          | threshold-1.conf | 3.6.0       | N/A | 90 | 100 ",
    // -----------------------------------------------------------------------------------------------------------------------------------------
    "native-image 22.3.4.0-Final Mandrel Distribution (Java Version 17.0.9+9)\\n          | threshold-2.conf | 2.7.9.Final | 200 | 300 | 400 ",
    // -----------------------------------------------------------------------------------------------------------------------------------------
    "native-image 17.0.9 2023-10-17\\n" +
    "OpenJDK Runtime Environment Mandrel-23.0.2.1-Final (build 17.0.9+9)\\n" +
    "OpenJDK 64-Bit Server VM Mandrel-23.0.2.1-Final (build 17.0.9+9, mixed mode)\\n      | threshold-3.conf | 3.6.0       | 100 | 200 | 300 ",
    // -----------------------------------------------------------------------------------------------------------------------------------------
    "native-image 20.0.2 2023-07-18\\n" +
    "OpenJDK Runtime Environment Mandrel-23.0.1.2-Final (build 20.0.2+9)\\n" +
    "OpenJDK 64-Bit Server VM Mandrel-23.0.1.2-Final (build 20.0.2+9, mixed mode)\\n      | threshold-4.conf | 3.6.0       | 100 | 200 | 300 ",
    // -----------------------------------------------------------------------------------------------------------------------------------------
    "native-image 20.0.2 2023-07-18\\n" +
    "OpenJDK Runtime Environment Mandrel-23.0.1.2-Final (build 20.0.2+9)\\n" +
    "OpenJDK 64-Bit Server VM Mandrel-23.0.1.2-Final (build 20.0.2+9, mixed mode)\\n      | threshold-4.conf | 3.8.0       | 300 | 400 | 100 ",
    // -----------------------------------------------------------------------------------------------------------------------------------------
    "native-image 20.0.8 2023-07-18\\n" +
    "OpenJDK Runtime Environment Mandrel-23.0.1.2-Final (build 20.0.8+9)\\n" +
    "OpenJDK 64-Bit Server VM Mandrel-23.0.1.2-Final (build 20.0.8+9, mixed mode)\\n      | threshold-4.conf | 3.0.0       | 191 | 192 | 193 ",
    // -----------------------------------------------------------------------------------------------------------------------------------------
    "native-image 23 2026-09-01\\n" +
    "GraalVM Runtime Environment GraalVM CE 23+35.1 (build 23+35-jvmci-24.1-b99)\\n" +
    "Substrate VM GraalVM CE 23+35.1 (build 23+35, serial gc)\\n                          | threshold-5.conf | 3.5.5       | 110 | 210 | 310 ",
    // -----------------------------------------------------------------------------------------------------------------------------------------
    "native-image 23 2026-09-01\\n" +
    "GraalVM Runtime Environment GraalVM CE 23+35.1 (build 23+35-jvmci-24.1-b99)\\n" +
    "Substrate VM GraalVM CE 23+35.1 (build 23+35, serial gc)\\n                          | threshold-5.conf | 3.6.0       | 100 | 200 | 300 ",
    }, delimiter = '|')
    //@formatter:on
    public void testThreshold1(String nativeImageVersion, String conf, String quarkusVersion, String a, String b, String c) throws IOException {
        final Path config = Path.of(BASE_DIR, "testsuite", "src", "test", "resources", conf);
        assertTrue(Files.exists(config), "Config file '" + config + "' MUST exist.");
        final Path tmpDir = Files.createTempDirectory(ThresholdsTest.class.getSimpleName());
        final Path nativeImage = tmpDir.resolve(Path.of(IS_THIS_WINDOWS ? "native-image.cmd" : "native-image"));
        final String quarkusVersionTmp = getProperty("QUARKUS_VERSION", QuarkusVersion.DEFAULT_VERSION);
        System.setProperty("FAKE_NATIVE_IMAGE_DIR", tmpDir.toAbsolutePath() + File.separator);
        System.setProperty("QUARKUS_VERSION", quarkusVersion);
        // Why this \\\\n? @CsvSource does not like \n in its values.
        final String nativeImageText = nativeImageVersion.replaceAll("\\\\n", System.lineSeparator());
        try {
            Files.writeString(nativeImage, IS_THIS_WINDOWS
                            ? "@echo off" + System.lineSeparator() + "echo " + nativeImageText
                            : "#!/bin/sh" + System.lineSeparator() + "echo '" + nativeImageText + "'",
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            if (!IS_THIS_WINDOWS) {
                Files.setPosixFilePermissions(nativeImage, PosixFilePermissions.fromString("rwxr-xr-x"));
            }
            // Reset parsed instances to avoid side effects on other tests
            // Note that container instance isn't being used, so isn't reset.
            UsedVersion.Locally.resetInstance();

            final Map<String, Long> thresholds = parseProperties(config);
            final String[] expected = { a, b, c };
            final String[] propNames = { "a", "b", "c" };
            for (int i = 0; i < expected.length; i++) {
                final String k = "some.property." + propNames[i];
                assertEquals(expected[i], thresholds.containsKey(k) ? thresholds.get(k).toString() : "N/A",
                        String.format("Expected value for some.property.%s is %s, but got %s,\n " +
                                "Native-image: %s\n" +
                                "Quarkus: %s\n" +
                                "Conf: %s\n", propNames[i], expected[i], thresholds.get(k), nativeImageText, quarkusVersion, config));
            }
        } finally {
            System.clearProperty("FAKE_NATIVE_IMAGE_DIR");
            System.setProperty("QUARKUS_VERSION", quarkusVersionTmp);
            Files.deleteIfExists(nativeImage);
            Files.deleteIfExists(tmpDir);
        }
    }

}
