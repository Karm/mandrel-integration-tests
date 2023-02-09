/*
 * Copyright (c) 2020, Red Hat Inc. All rights reserved.
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
package org.graalvm.tests.integration;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.graalvm.home.Version;
import org.graalvm.tests.integration.utils.Apps;
import org.graalvm.tests.integration.utils.LogBuilder;
import org.graalvm.tests.integration.utils.Logs;
import org.graalvm.tests.integration.utils.versions.IfMandrelVersion;
import org.graalvm.tests.integration.utils.versions.UsedVersion;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.graalvm.tests.integration.utils.Commands.builderRoutine;
import static org.graalvm.tests.integration.utils.Commands.cleanTarget;
import static org.graalvm.tests.integration.utils.Commands.cleanup;
import static org.graalvm.tests.integration.utils.Commands.getBaseDir;
import static org.graalvm.tests.integration.utils.Commands.getRunCommand;
import static org.graalvm.tests.integration.utils.Commands.listStaticLibs;
import static org.graalvm.tests.integration.utils.Commands.processStopper;
import static org.graalvm.tests.integration.utils.Commands.runCommand;
import static org.graalvm.tests.integration.utils.Commands.searchLogLines;
import static org.graalvm.tests.integration.utils.Logs.getLogsDir;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for build and start of applications with some real source code.
 *
 * @author Michal Karm Babacek <karm@redhat.com>
 */
@Tag("reproducers")
public class AppReproducersTest {

    private static final Logger LOGGER = Logger.getLogger(AppReproducersTest.class.getName());

    public static final String BASE_DIR = getBaseDir();

    @Test
    @Tag("randomNumbers")
    public void randomNumbersReinit(TestInfo testInfo) throws IOException, InterruptedException {
        final Apps app = Apps.RANDOM_NUMBERS;
        LOGGER.info("Testing app: " + app);
        Process process = null;
        File processLog = null;
        final StringBuilder report = new StringBuilder();
        final File appDir = Path.of(BASE_DIR, app.dir).toFile();
        final String cn = testInfo.getTestClass().get().getCanonicalName();
        final String mn = testInfo.getTestMethod().get().getName();
        try {
            // Cleanup
            cleanTarget(app);
            Files.createDirectories(Paths.get(appDir.getAbsolutePath() + File.separator + "logs"));

            // Build
            processLog = Path.of(appDir.getAbsolutePath(), "logs", "build-and-run.log").toFile();

            builderRoutine(app, report, cn, mn, appDir, processLog);

            LOGGER.info("Running...#1");
            List<String> cmd = getRunCommand(app.buildAndRunCmds.cmds[app.buildAndRunCmds.cmds.length - 1]);
            process = runCommand(cmd, appDir, processLog, app);
            assertNotNull(process, "The test application failed to run. Check " + getLogsDir(cn, mn) + File.separator + processLog.getName());
            process.waitFor(5, TimeUnit.SECONDS);
            Logs.appendln(report, appDir.getAbsolutePath());
            Logs.appendlnSection(report, String.join(" ", cmd));

            LOGGER.info("Running...#2");
            cmd = getRunCommand(app.buildAndRunCmds.cmds[app.buildAndRunCmds.cmds.length - 1]);
            process = runCommand(cmd, appDir, processLog, app);
            assertNotNull(process, "The test application failed to run. Check " + getLogsDir(cn, mn) + File.separator + processLog.getName());
            process.waitFor(5, TimeUnit.SECONDS);
            Logs.appendln(report, appDir.getAbsolutePath());
            Logs.appendlnSection(report, String.join(" ", cmd));

            final Pattern p = Pattern.compile("Hello, (.*)");
            final Set<String> parsedLines = new HashSet<>(4);
            try (Scanner sc = new Scanner(processLog, UTF_8)) {
                while (sc.hasNextLine()) {
                    Matcher m = p.matcher(sc.nextLine());
                    if (m.matches()) {
                        parsedLines.add(m.group(1));
                    }
                }
            }

            LOGGER.info(parsedLines.toString());

            assertEquals(4, parsedLines.size(), "There should have been 4 distinct lines in the log," +
                    "showing 2 different pseudorandom sequences. The fact that there are less than 4 means the native image" +
                    "was not properly re-seeded. See https://github.com/oracle/graal/issues/2265.");

            processStopper(process, false);
            Logs.checkLog(cn, mn, app, processLog);
        } finally {
            cleanup(process, cn, mn, report, app, processLog);
        }
    }

    @Test
    @Tag("resources")
    @IfMandrelVersion(min = "21.3", max = "21.999")
    public void resLocationsA(TestInfo testInfo) throws IOException, InterruptedException {
        final String expectedOutput = "" +
                "Resources folders:\n" +
                "0:  N/A\n" +
                "1:  N/A\n" +
                "2:  N/A\n" +
                "3:  N/A\n" +
                "4:  NO_SLASH FOLDER\n" +
                "5:  NO_SLASH FOLDER\n" +
                "6:  NO_SLASH FOLDER\n" +
                "7:  NO_SLASH FOLDER\n" +
                "8:  N/A\n" +
                "9:  N/A\n" +
                "10: N/A\n" +
                "11: N/A\n" +
                "\n" +
                "iio-plugin.properties:\n" +
                "0:  N/A\n" +
                "1:  APP\n" +
                "2:  N/A\n" +
                "3:  JDK\n" +
                "4:  JDK\n" +
                "5:  APP\n" +
                "6:  N/A\n" +
                "7:  JDK\n" +
                "8:  JDK\n" +
                "9:  APP\n" +
                "10: N/A\n" +
                "11: JDK\n" +
                "12: APP\n" +
                "13: N/A\n" +
                "14: JDK\n" +
                "15: N/A\n" +
                "16: JDK\n" +
                "17: APP\n" +
                "18: N/A\n" +
                "19: JDK\n" +
                "20: JDK\n" +
                "21: APP\n" +
                "22: N/A\n" +
                "23: JDK\n" +
                "24: JDK\n" +
                "25: APP\n" +
                "26: N/A\n" +
                "27: JDK\n" +
                "28: APP\n" +
                "29: N/A\n" +
                "30: JDK\n" +
                "31: N/A\n";
        resLocations(testInfo, Apps.RESLOCATIONS, expectedOutput);
    }

    @Test
    @Tag("resources")
    @IfMandrelVersion(min = "22.0", max = "22.0")
    public void resLocationsB(TestInfo testInfo) throws IOException, InterruptedException {
        final String expectedOutput = "" +
                "Resources folders:\n" +
                "0:  N/A\n" +
                "1:  N/A\n" +
                "2:  N/A\n" +
                "3:  N/A\n" +
                "4:  NO_SLASH FOLDER\n" +
                "5:  NO_SLASH FOLDER\n" +
                "6:  NO_SLASH FOLDER\n" +
                "7:  NO_SLASH FOLDER\n" +
                "8:  N/A\n" +
                "9:  N/A\n" +
                "10: N/A\n" +
                "11: N/A\n" +
                "\n" +
                "iio-plugin.properties:\n" +
                "0:  N/A\n" +
                "1:  APP\n" +
                "2:  N/A\n" +
                "3:  N/A\n" +
                "4:  JDK\n" +
                "5:  N/A\n" +
                "6:  N/A\n" +
                "7:  JDK\n" +
                "8:  JDK\n" +
                "9:  N/A\n" +
                "10: N/A\n" +
                "11: JDK\n" +
                "12: APP\n" +
                "13: N/A\n" +
                "14: N/A\n" +
                "15: N/A\n" +
                "16: JDK\n" +
                "17: N/A\n" +
                "18: N/A\n" +
                "19: JDK\n" +
                "20: JDK\n" +
                "21: N/A\n" +
                "22: N/A\n" +
                "23: JDK\n" +
                "24: JDK\n" +
                "25: N/A\n" +
                "26: N/A\n" +
                "27: JDK\n" +
                "28: N/A\n" +
                "29: N/A\n" +
                "30: JDK\n" +
                "31: N/A\n";
        resLocations(testInfo, Apps.RESLOCATIONS, expectedOutput);
    }

    @Test
    @Tag("resources")
    @IfMandrelVersion(min = "22.1", max = "22.2")
    public void resLocationsC(TestInfo testInfo) throws IOException, InterruptedException {
        final String expectedOutput = "" +
                "Resources folders:\n" +
                "0:  N/A\n" +
                "1:  N/A\n" +
                "2:  N/A\n" +
                "3:  N/A\n" +
                "4:  NO_SLASH FOLDER\n" +
                "5:  SLASH FOLDER\n" +
                "6:  N/A\n" +
                "7:  N/A\n" +
                "8:  N/A\n" +
                "9:  N/A\n" +
                "10: N/A\n" +
                "11: N/A\n" +
                "\n" +
                "iio-plugin.properties:\n" +
                "0:  N/A\n" +
                "1:  APP\n" +
                "2:  N/A\n" +
                "3:  N/A\n" +
                "4:  JDK\n" +
                "5:  N/A\n" +
                "6:  N/A\n" +
                "7:  JDK\n" +
                "8:  JDK\n" +
                "9:  N/A\n" +
                "10: N/A\n" +
                "11: JDK\n" +
                "12: APP\n" +
                "13: N/A\n" +
                "14: N/A\n" +
                "15: N/A\n" +
                "16: JDK\n" +
                "17: N/A\n" +
                "18: N/A\n" +
                "19: JDK\n" +
                "20: JDK\n" +
                "21: N/A\n" +
                "22: N/A\n" +
                "23: JDK\n" +
                "24: JDK\n" +
                "25: N/A\n" +
                "26: N/A\n" +
                "27: JDK\n" +
                "28: N/A\n" +
                "29: N/A\n" +
                "30: JDK\n" +
                "31: N/A\n";
        resLocations(testInfo, Apps.RESLOCATIONS, expectedOutput);
    }

    /**
     * Resources handling changed:
     *     3: N/A -> JDK
     *    14: N/A -> JDK
     * In https://github.com/oracle/graal/commit/8faf577
     * @param testInfo
     * @throws IOException
     * @throws InterruptedException
     */
    @Test
    @Tag("resources")
    @IfMandrelVersion(min = "22.3", max = "22.3")
    public void resLocationsD(TestInfo testInfo) throws IOException, InterruptedException {
        final String expectedOutput = "" +
                "Resources folders:\n" +
                "0:  N/A\n" +
                "1:  N/A\n" +
                "2:  N/A\n" +
                "3:  N/A\n" +
                "4:  NO_SLASH FOLDER\n" +
                "5:  SLASH FOLDER\n" +
                "6:  N/A\n" +
                "7:  N/A\n" +
                "8:  N/A\n" +
                "9:  N/A\n" +
                "10: N/A\n" +
                "11: N/A\n" +
                "\n" +
                "iio-plugin.properties:\n" +
                "0:  N/A\n" +
                "1:  APP\n" +
                "2:  N/A\n" +
                "3:  JDK\n" +
                "4:  JDK\n" +
                "5:  N/A\n" +
                "6:  N/A\n" +
                "7:  JDK\n" +
                "8:  JDK\n" +
                "9:  N/A\n" +
                "10: N/A\n" +
                "11: JDK\n" +
                "12: APP\n" +
                "13: N/A\n" +
                "14: JDK\n" +
                "15: N/A\n" +
                "16: JDK\n" +
                "17: N/A\n" +
                "18: N/A\n" +
                "19: JDK\n" +
                "20: JDK\n" +
                "21: N/A\n" +
                "22: N/A\n" +
                "23: JDK\n" +
                "24: JDK\n" +
                "25: N/A\n" +
                "26: N/A\n" +
                "27: JDK\n" +
                "28: N/A\n" +
                "29: N/A\n" +
                "30: JDK\n" +
                "31: N/A\n";
        resLocations(testInfo, Apps.RESLOCATIONS, expectedOutput);
    }

    /**
     * Resources handling changed:
     *    31: N/A -> JDK
     * In https://github.com/oracle/graal/commit/3e9313ec2f41e96dcd1a3a621675adc2e9e3f8ac
     * @param testInfo
     * @throws IOException
     * @throws InterruptedException
     */
    @Test
    @Tag("resources")
    @IfMandrelVersion(min = "23.0")
    public void resLocationsE(TestInfo testInfo) throws IOException, InterruptedException {
        final String expectedOutput = "" +
                "Resources folders:\n" +
                "0:  N/A\n" +
                "1:  N/A\n" +
                "2:  N/A\n" +
                "3:  N/A\n" +
                "4:  NO_SLASH FOLDER\n" +
                "5:  SLASH FOLDER\n" +
                "6:  N/A\n" +
                "7:  N/A\n" +
                "8:  N/A\n" +
                "9:  N/A\n" +
                "10: N/A\n" +
                "11: N/A\n" +
                "\n" +
                "iio-plugin.properties:\n" +
                "0:  N/A\n" +
                "1:  APP\n" +
                "2:  N/A\n" +
                "3:  JDK\n" +
                "4:  JDK\n" +
                "5:  N/A\n" +
                "6:  N/A\n" +
                "7:  JDK\n" +
                "8:  JDK\n" +
                "9:  N/A\n" +
                "10: N/A\n" +
                "11: JDK\n" +
                "12: APP\n" +
                "13: N/A\n" +
                "14: JDK\n" +
                "15: N/A\n" +
                "16: JDK\n" +
                "17: N/A\n" +
                "18: N/A\n" +
                "19: JDK\n" +
                "20: JDK\n" +
                "21: N/A\n" +
                "22: N/A\n" +
                "23: JDK\n" +
                "24: JDK\n" +
                "25: N/A\n" +
                "26: N/A\n" +
                "27: JDK\n" +
                "28: N/A\n" +
                "29: N/A\n" +
                "30: JDK\n" +
                "31: JDK\n";
        resLocations(testInfo, Apps.RESLOCATIONS, expectedOutput);
    }

    public void resLocations(TestInfo testInfo, Apps app, String expectedOutput) throws IOException, InterruptedException {
        LOGGER.info("Testing app: " + app);
        File processLog = null;
        final StringBuilder report = new StringBuilder();
        final File appDir = Path.of(BASE_DIR, app.dir).toFile();
        final String cn = testInfo.getTestClass().get().getCanonicalName();
        final String mn = testInfo.getTestMethod().get().getName();
        try {
            // Cleanup
            cleanTarget(app);
            Files.createDirectories(Paths.get(appDir.getAbsolutePath() + File.separator + "logs"));

            // Build
            processLog = Path.of(appDir.getAbsolutePath(), "logs", "build-and-run.log").toFile();

            builderRoutine(app, report, cn, mn, appDir, processLog);

            LOGGER.info("Running...");
            final List<String> cmd = getRunCommand(app.buildAndRunCmds.cmds[app.buildAndRunCmds.cmds.length - 1]);
            final String output = runCommand(cmd, appDir).trim();
            Logs.appendln(report, appDir.getAbsolutePath());
            Logs.appendlnSection(report, String.join(" ", cmd));
            final Map<String, String> errors = new HashMap<>();
            List<String> expected = expectedOutput.trim().lines().collect(Collectors.toList());
            List<String> actual = output.lines().collect(Collectors.toList());
            assertEquals(expected.size(), actual.size(),
                    "Expected output and actual output have a different number of lines. Actual was: " + output);
            for (int i = 0; i < expected.size(); i++) {
                if (!expected.get(i).equals(actual.get(i))) {
                    errors.put(expected.get(i), actual.get(i));
                }
            }
            assertTrue(errors.isEmpty(), "Something changed in how the resources are handled. " +
                    "Check comments on https://github.com/oracle/graal/issues/4326 and https://github.com/quarkusio/quarkus/pull/22403. " +
                    "See discrepancies: " + errors.keySet().stream()
                    .map(key -> "Expected: " + key + ", Actual: " + errors.get(key))
                    .collect(Collectors.joining("\n", "\n", "\n")));
            Logs.checkLog(cn, mn, app, processLog);
        } finally {
            cleanup(null, cn, mn, report, app, processLog);
        }
    }

    @Test
    @Tag("builder-image")
    @Tag("imageio")
    @IfMandrelVersion(min = "21.1", inContainer = true)
    public void imageioAWTContainerTest(TestInfo testInfo) throws IOException, InterruptedException {
        imageioAWT(testInfo, Apps.IMAGEIO_BUILDER_IMAGE);
    }

    @Test
    @Tag("imageio")
    @DisabledOnOs({OS.WINDOWS}) // AWT support is not there yet
    @IfMandrelVersion(min = "21.1")
    public void imageioAWTTest(TestInfo testInfo) throws IOException, InterruptedException {
        imageioAWT(testInfo, Apps.IMAGEIO);
    }

    public void imageioAWT(TestInfo testInfo, Apps app) throws IOException, InterruptedException {
        LOGGER.info("Testing app: " + app.toString());

        final Map<String, String> controlData = new HashMap<>(12);
        Process process = null;
        File processLog = null;
        final StringBuilder report = new StringBuilder();
        final File appDir = Path.of(BASE_DIR, app.dir).toFile();
        final File metaINF = Path.of(BASE_DIR, app.dir, "src", "main", "resources", "META-INF", "native-image").toFile();
        final String cn = testInfo.getTestClass().get().getCanonicalName();
        final String mn = testInfo.getTestMethod().get().getName();
        try {
            // Cleanup
            cleanTarget(app);
            if (metaINF.exists()) {
                FileUtils.cleanDirectory(metaINF);
            }
            Files.createDirectories(Paths.get(appDir.getAbsolutePath() + File.separator + "logs"));

            // Build
            processLog = Path.of(appDir.getAbsolutePath(), "logs", "build-and-run.log").toFile();

            builderRoutine(app, report, cn, mn, appDir, processLog);

            // Record images' hashsums as created by a Java process
            final List<String> errors = new ArrayList<>(12);
            List<String> pictures = List.of(
                    "mytest.bmp",
                    "mytest.gif",
                    "mytest.jpg",
                    "mytest.png",
                    "mytest.svg",
                    "mytest.tiff",
                    "mytest_toC.png",
                    "mytest_toG.png",
                    "mytest_toL.png",
                    "mytest_toP.png",
                    "mytest_toS.png",
                    "mytest.wbmp",
                    "mytest_Resized_Grace_M._Hopper.png"
            );

            pictures.forEach(f -> {
                try {
                    controlData.put(f, DigestUtils.sha256Hex(FileUtils.readFileToByteArray(new File(appDir, f))));
                } catch (IOException e) {
                    errors.add(f + " cannot be loaded.");
                    e.printStackTrace();
                }
            });

            assertEquals(pictures.size(), controlData.size(), "There was supposed to be " + pictures.size() + " pictures generated.");

            // Remove files generated by java run to prevent false negatives
            controlData.keySet().forEach(f -> new File(appDir, f).delete());

            LOGGER.info("Running...");
            final List<String> cmd = getRunCommand(app.buildAndRunCmds.cmds[app.buildAndRunCmds.cmds.length - 1]);
            process = runCommand(cmd, appDir, processLog, app);
            assertNotNull(process, "The test application failed to run. Check " + getLogsDir(cn, mn) + File.separator + processLog.getName());
            process.waitFor(15, TimeUnit.SECONDS);
            Logs.appendln(report, appDir.getAbsolutePath());
            Logs.appendlnSection(report, String.join(" ", cmd));

            // Test output
            final boolean inContainer = app == Apps.IMAGEIO_BUILDER_IMAGE;
            controlData.forEach((fileName, hash) -> {
                final File picture = new File(appDir, fileName);
                if (picture.exists() && picture.isFile()) {
                    try {
                        // Depending on the way the builder image is produced, it might use
                        // a different version e.g. of fontconfig in HotSpot mode (.so system shared) and
                        // in native-image mode (.a static built-in from JDK),
                        // so especially font render could affect hashsum, despite being indistinguishable to the naked eye.

                        // Sanity check size
                        if (inContainer && UsedVersion.jdkUsesSysLibs(true)) {
                            final long expected = 5500;
                            final long actual = FileUtils.sizeOf(picture);
                            if (actual < expected) {
                                errors.add(fileName + "'s length was " + actual + ", expected was at least: " + expected + "bytes");
                            }
                            // Verify actual hashsums
                        } else {
                            final String sha256hex = DigestUtils.sha256Hex(FileUtils.readFileToByteArray(picture));
                            if (!hash.equals(sha256hex)) {
                                errors.add(fileName + "'s sha256 hash was " + sha256hex + ", expected hash: " + hash);
                            }
                        }
                    } catch (IOException e) {
                        errors.add(fileName + " cannot be loaded.");
                        e.printStackTrace();
                    }
                } else {
                    errors.add(fileName + " was not generated.");
                }
            });
            assertTrue(errors.isEmpty(),
                    "There were errors checking the generated image files, see:\n" + String.join("\n", errors));

            // Test static libs in the executable
            final File executable = new File(appDir.getAbsolutePath() + File.separator + "target", "imageio");
            Set<String> expected = Set.of("libawt.a", "libawt_headless.a", "libfdlibm.a", "libfontmanager.a", "libjava.a", "libjavajpeg.a", "libjvm.a", "liblcms.a", "liblibchelper.a", "libnet.a", "libnio.a", "libzip.a");
            if (UsedVersion.jdkFeature(inContainer) > 11 || (UsedVersion.jdkFeature(inContainer) == 11 && UsedVersion.jdkUpdate(inContainer) > 12)) {
                // Harfbuzz removed: https://github.com/graalvm/mandrel/issues/286
                // NO-OP
            } else {
                Set<String> modifiable = new HashSet<>(expected);
                modifiable.add("libharfbuzz.a");
                expected = Collections.unmodifiableSet(modifiable);
            }

            final Set<String> actual = listStaticLibs(executable);

            assertTrue(expected.equals(actual), "A different set of static libraries was expected. \n" +
                    "Expected: " + expected.stream().sorted().collect(Collectors.toList()) + "\n" +
                    "Actual:   " + actual.stream().sorted().collect(Collectors.toList()));

            processStopper(process, false);
            Logs.checkLog(cn, mn, app, processLog);
        } finally {
            cleanup(process, cn, mn, report, app, processLog);
            if (metaINF.exists()) {
                FileUtils.cleanDirectory(metaINF);
            }
            new File(appDir, "dependency-reduced-pom.xml").delete();
            final File fontConfigDir = new File(appDir, "?");
            if (fontConfigDir.exists()) {
                FileUtils.forceDelete(fontConfigDir);
            }
            controlData.keySet().forEach(f -> new File(appDir, f).delete());
        }
    }

    @Test
    @Tag("timezones")
    public void timezonesBakedIn(TestInfo testInfo) throws IOException, InterruptedException {
        final Apps app = Apps.TIMEZONES;
        LOGGER.info("Testing app: " + app);
        Process process = null;
        File processLog = null;
        final StringBuilder report = new StringBuilder();
        final File appDir = Path.of(BASE_DIR, app.dir).toFile();
        final String cn = testInfo.getTestClass().get().getCanonicalName();
        final String mn = testInfo.getTestMethod().get().getName();
        try {
            // Cleanup
            cleanTarget(app);
            Files.createDirectories(Paths.get(appDir.getAbsolutePath() + File.separator + "logs"));

            // Build
            processLog = Path.of(appDir.getAbsolutePath(), "logs", "build-and-run.log").toFile();

            builderRoutine(app, report, cn, mn, appDir, processLog);

            LOGGER.info("Running...");
            List<String> cmd = getRunCommand(app.buildAndRunCmds.cmds[app.buildAndRunCmds.cmds.length - 1]);
            process = runCommand(cmd, appDir, processLog, app);
            assertNotNull(process, "The test application failed to run. Check " + getLogsDir(cn, mn) + File.separator + processLog.getName());
            process.waitFor(5, TimeUnit.SECONDS);
            Logs.appendln(report, appDir.getAbsolutePath());
            Logs.appendlnSection(report, String.join(" ", cmd));

            /*
            ...about encoding and output on Windows vs. Linux:
            Linux output:
            mer. avr. 28 11:46:38 CEST 2021
            heure normale d’Europe centrale

            Windows output:
            mar. avr. 27 12:46:52 HAP 2021
            heure normale dÆEurope centrale

            Match found in the log file according to different encodings used to read it:

                Linux:
                Expected pattern .*d.+Europe centrale.* was found in the log. Encoding: UTF-8
                Expected pattern .*d.+Europe centrale.* was not found in the log. Encoding: US-ASCII
                Expected pattern .*d.+Europe centrale.* was found in the log. Encoding: ISO-8859-1
                Expected pattern .*d.+Europe centrale.* was not found in the log. Encoding: UTF-16

                Windows:
                Expected pattern .*d.+Europe centrale.* was not found in the log. Encoding: UTF-8
                Expected pattern .*d.+Europe centrale.* was not found in the log. Encoding: US-ASCII
                Expected pattern .*d.+Europe centrale.* was found in the log. Encoding: ISO-8859-1
                Expected pattern .*d.+Europe centrale.* was not found in the log. Encoding: UTF-16
                Expected pattern .*d.+Europe centrale.* was found in the log. Encoding: windows-1252

            So we use UTF-8 on Linux and windows-1252 on Windows...and that corresponds to Charset.defaultCharset(). Went the full circle on this.
            */
            final Pattern p = Pattern.compile(".*heure normale.*Europe centrale.*");
            /*
            Ad the aforementioned regexp:
            JDK 11 prints the same both for country=CA and country=FR, i.e.:
                heure normale d’Europe centrale
            JDK 17 prints this only for country=FR while it does something else for country=CA:
                heure normale de l’Europe centrale
             */
            assertTrue(searchLogLines(p, processLog, Charset.defaultCharset()), "Expected pattern " + p.toString() + " was not found in the log. " +
                    "There might be a problem with timezones inclusion. See https://github.com/oracle/graal/issues/2776");

            processStopper(process, false);
            Logs.checkLog(cn, mn, app, processLog);
        } finally {
            cleanup(process, cn, mn, report, app, processLog);
        }
    }

    @Test
    @Tag("jdk-17")
    @Tag("recordannotations")
    @IfMandrelVersion(min = "21.3.1.1", max = "21.3.999", minJDK = "17")
    public void recordAnnotationsWork21_3(TestInfo testInfo) throws IOException, InterruptedException {
        recordAnnotationsWork(testInfo);
    }

    @Test
    @Tag("jdk-17")
    @Tag("recordannotations")
    @IfMandrelVersion(min = "22.1", minJDK = "17")
    public void recordAnnotationsWorkPost22_1(TestInfo testInfo) throws IOException, InterruptedException {
        recordAnnotationsWork(testInfo);
    }

    public void recordAnnotationsWork(TestInfo testInfo) throws IOException, InterruptedException {
        final Apps app = Apps.RECORDANNOTATIONS;
        LOGGER.info("Testing app: " + app);
        Process process = null;
        File processLog = null;
        final StringBuilder report = new StringBuilder();
        final File appDir = Path.of(BASE_DIR, app.dir).toFile();
        final String cn = testInfo.getTestClass().get().getCanonicalName();
        final String mn = testInfo.getTestMethod().get().getName();
        try {
            // Cleanup
            cleanTarget(app);
            Files.createDirectories(Paths.get(appDir.getAbsolutePath() + File.separator + "logs"));

            // Build
            processLog = Path.of(appDir.getAbsolutePath(), "logs", "build-and-run.log").toFile();

            builderRoutine(app, report, cn, mn, appDir, processLog);

            LOGGER.info("Running...");
            List<String> cmd = getRunCommand(app.buildAndRunCmds.cmds[app.buildAndRunCmds.cmds.length - 1]);
            process = runCommand(cmd, appDir, processLog, app);
            assertNotNull(process, "The test application failed to run. Check " + getLogsDir(cn, mn) + File.separator + processLog.getName());
            process.waitFor(5, TimeUnit.SECONDS);
            Logs.appendln(report, appDir.getAbsolutePath());
            Logs.appendlnSection(report, String.join(" ", cmd));

            final Pattern p = Pattern.compile(".*RCA annotation: @recordannotations\\.RCA.*");
            assertTrue(searchLogLines(p, processLog, Charset.defaultCharset()), "Expected pattern " + p.toString() + " was not found in the log.");
            final Pattern p2 = Pattern.compile(".*annotation: @recordannotations\\.RCA.*");
            assertTrue(searchLogLines(p2, processLog, Charset.defaultCharset()), "Expected pattern " + p2.toString() + " was not found in the log.");
            final Pattern p3 = Pattern.compile(".*annotation: @recordannotations\\.RCA2.*");
            assertTrue(searchLogLines(p3, processLog, Charset.defaultCharset()), "Expected pattern " + p3.toString() + " was not found in the log.");

            processStopper(process, false);
            Logs.checkLog(cn, mn, app, processLog);
        } finally {
            cleanup(process, cn, mn, report, app, processLog);
        }
    }

    @Test
    @Tag("versions")
    public void versionsParsingMandrel(TestInfo testInfo) throws IOException, InterruptedException {
        final Apps app = Apps.VERSIONS;
        LOGGER.info("Testing app: " + app);
        Process process = null;
        File processLog = null;
        final StringBuilder report = new StringBuilder();
        final File appDir = Path.of(BASE_DIR, app.dir).toFile();
        final String cn = testInfo.getTestClass().get().getCanonicalName();
        final String mn = testInfo.getTestMethod().get().getName();
        try {
            // Cleanup
            cleanTarget(app);
            Files.createDirectories(Paths.get(appDir.getAbsolutePath() + File.separator + "logs"));

            // Build
            processLog = Path.of(appDir.getAbsolutePath(), "logs", "build-and-run.log").toFile();

            builderRoutine(2, app, report, cn, mn, appDir, processLog);

            LOGGER.info("Running...");
            List<String> cmd = getRunCommand(app.buildAndRunCmds.cmds[app.buildAndRunCmds.cmds.length - 1]);
            process = runCommand(cmd, appDir, processLog, app);
            assertNotNull(process, "The test application failed to run. Check " + getLogsDir(cn, mn) + File.separator + processLog.getName());
            process.waitFor(5, TimeUnit.SECONDS);
            Logs.appendln(report, appDir.getAbsolutePath());
            Logs.appendlnSection(report, String.join(" ", cmd));

            String lastLine = null;
            try (Scanner sc = new Scanner(processLog, UTF_8)) {
                while (sc.hasNextLine()) {
                    lastLine = sc.nextLine();
                }
            }

            assertEquals("TargetSub: Hello!", lastLine, "Sanity check that Graal version parsing worked!");

            processStopper(process, false);
            Logs.checkLog(cn, mn, app, processLog);
        } finally {
            cleanup(process, cn, mn, report, app, processLog);
        }
    }

    @Test
    @Tag("nativeJVMTextProcessing")
    public void nativeJVMTextProcessing(TestInfo testInfo) throws IOException, InterruptedException {
        final Apps app = Apps.DEBUG_SYMBOLS_SMOKE;
        LOGGER.info("Testing app: " + app);
        Process process = null;
        File processLog = null;
        final StringBuilder report = new StringBuilder();
        final File appDir = Path.of(BASE_DIR, app.dir).toFile();
        final String cn = testInfo.getTestClass().get().getCanonicalName();
        final String mn = testInfo.getTestMethod().get().getName();
        try {
            // Cleanup
            cleanTarget(app);
            Files.createDirectories(Paths.get(appDir.getAbsolutePath() + File.separator + "logs"));

            // Build
            processLog = Path.of(appDir.getAbsolutePath(), "logs", "build-and-run.log").toFile();

            // In this case, the two last commands are used for running the app; one in JVM mode and the other in Native mode.
            // We should somehow capture this semantically in an Enum or something. This is fragile...
            builderRoutine(app.buildAndRunCmds.cmds.length - 2, app, report, cn, mn, appDir, processLog);

            final File inputData = new File(BASE_DIR + File.separator + app.dir + File.separator + "target" + File.separator + "test_data.txt");

            LOGGER.info("Running JVM mode...");
            long start = System.currentTimeMillis();
            List<String> cmd = getRunCommand(app.buildAndRunCmds.cmds[app.buildAndRunCmds.cmds.length - 2]);
            process = runCommand(cmd, appDir, processLog, app, inputData);
            assertNotNull(process, "The test application failed to run. Check " + getLogsDir(cn, mn) + File.separator + processLog.getName());
            process.waitFor(30, TimeUnit.SECONDS);
            long jvmRunTookMs = System.currentTimeMillis() - start;
            Logs.appendln(report, appDir.getAbsolutePath());
            Logs.appendlnSection(report, String.join(" ", cmd));

            LOGGER.info("Running Native mode...");
            start = System.currentTimeMillis();
            cmd = getRunCommand(app.buildAndRunCmds.cmds[app.buildAndRunCmds.cmds.length - 1]);
            process = runCommand(cmd, appDir, processLog, app, inputData);
            assertNotNull(process, "The test application failed to run. Check " + getLogsDir(cn, mn) + File.separator + processLog.getName());
            process.waitFor(30, TimeUnit.SECONDS);
            long nativeRunTookMs = System.currentTimeMillis() - start;
            Logs.appendln(report, appDir.getAbsolutePath());
            Logs.appendlnSection(report, String.join(" ", cmd));

            validateDebugSmokeApp(processLog, cn, mn, process, app, jvmRunTookMs, nativeRunTookMs, report, null);

        } finally {
            cleanup(process, cn, mn, report, app, processLog);
        }
    }

    public static void validateDebugSmokeApp(File processLog, String cn, String mn, Process process, Apps app,
                                             long jvmRunTookMs, long nativeRunTookMs, StringBuilder report, String suffix)
            throws IOException, InterruptedException {
        // Test output and log measurements (time it took to run)

        int count = 0;
        // This magic hash is what the app is supposed to spit out.
        // See ./apps/debug-symbols-smoke/src/main/java/debug_symbols_smoke/Main.java
        final String magicHash = "b6951775b0375ea13fc977581e54eb36d483e95ed3bc1e62fcb8da59830f1ef9";
        try (Scanner sc = new Scanner(processLog, UTF_8)) {
            while (sc.hasNextLine()) {
                if (magicHash.equals(sc.nextLine().trim())) {
                    count++;
                }
            }
        }

        assertEquals(2, count, "There were two same hashes " + magicHash + " expected in the log. " +
                "One from JVM run and one for Native image run. " +
                "" + count + " such hashes were found. Check build-and-run.log and report.md.");

        processStopper(process, false);
        Logs.checkLog(cn, mn, app, processLog);
        final Path measurementsLog = Paths.get(getLogsDir(cn, mn).toString(), "measurements.csv");

        String suffixUpper = "";
        String suffixLower = "";
        if (suffix != null && !suffix.isEmpty()) {
            suffixUpper = "_" + suffix.toUpperCase();
            suffixLower = "_" + suffix.toLowerCase();
        }

        LogBuilder.Log logJVM = new LogBuilder()
                .app(app + "_JVM" + suffixUpper)
                .timeToFinishMs(jvmRunTookMs)
                .build();
        LogBuilder.Log logNative = new LogBuilder()
                .app(app + "_NATIVE" + suffixUpper)
                .timeToFinishMs(nativeRunTookMs)
                .build();
        Logs.logMeasurements(logJVM, measurementsLog);
        Logs.logMeasurements(logNative, measurementsLog);
        Logs.appendln(report, "Measurements:");
        Logs.appendln(report, logJVM.headerMarkdown + "\n" + logJVM.lineMarkdown);
        Logs.appendln(report, logNative.lineMarkdown);
        Logs.checkThreshold(app, "jvm" + suffixLower, Logs.SKIP, Logs.SKIP, Logs.SKIP, jvmRunTookMs);
        Logs.checkThreshold(app, "native" + suffixLower, Logs.SKIP, Logs.SKIP, Logs.SKIP, nativeRunTookMs);
    }
}
