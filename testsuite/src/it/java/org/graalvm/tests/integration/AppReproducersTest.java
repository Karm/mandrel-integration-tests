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

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;
import static org.graalvm.tests.integration.utils.AuxiliaryOptions.DebugCodeInfoUseSourceMappings_23_0;
import static org.graalvm.tests.integration.utils.AuxiliaryOptions.ForeignAPISupport_24_2;
import static org.graalvm.tests.integration.utils.AuxiliaryOptions.LockExperimentalVMOptions_23_1;
import static org.graalvm.tests.integration.utils.AuxiliaryOptions.OmitInlinedMethodDebugLineInfo_23_0;
import static org.graalvm.tests.integration.utils.AuxiliaryOptions.TrackNodeSourcePosition_23_0;
import static org.graalvm.tests.integration.utils.AuxiliaryOptions.UnlockExperimentalVMOptions_23_1;
import static org.graalvm.tests.integration.utils.Commands.ARCH;
import static org.graalvm.tests.integration.utils.Commands.BUILDER_IMAGE;
import static org.graalvm.tests.integration.utils.Commands.DOCKER_GHA_BUILDX;
import static org.graalvm.tests.integration.utils.Commands.DOCKER_GHA_SUMMARY_NAME;
import static org.graalvm.tests.integration.utils.Commands.builderRoutine;
import static org.graalvm.tests.integration.utils.Commands.cleanDirOrFile;
import static org.graalvm.tests.integration.utils.Commands.cleanTarget;
import static org.graalvm.tests.integration.utils.Commands.cleanup;
import static org.graalvm.tests.integration.utils.Commands.compareArrays;
import static org.graalvm.tests.integration.utils.Commands.getBaseDir;
import static org.graalvm.tests.integration.utils.Commands.getRunCommand;
import static org.graalvm.tests.integration.utils.Commands.getSubstringFromSmallTextFile;
import static org.graalvm.tests.integration.utils.Commands.isBuilderImageIncompatible;
import static org.graalvm.tests.integration.utils.Commands.listStaticLibs;
import static org.graalvm.tests.integration.utils.Commands.processStopper;
import static org.graalvm.tests.integration.utils.Commands.removeContainer;
import static org.graalvm.tests.integration.utils.Commands.removeContainers;
import static org.graalvm.tests.integration.utils.Commands.replaceSwitchesInCmd;
import static org.graalvm.tests.integration.utils.Commands.runCommand;
import static org.graalvm.tests.integration.utils.Commands.searchLogLines;
import static org.graalvm.tests.integration.utils.Logs.getLogsDir;
import static org.graalvm.tests.integration.utils.versions.UsedVersion.getVersion;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
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

import javax.imageio.ImageIO;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.graalvm.home.Version;
import org.graalvm.tests.integration.utils.Apps;
import org.graalvm.tests.integration.utils.ContainerNames;
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

/**
 * Tests for build and start of applications with some real source code.
 *
 * @author Michal Karm Babacek <karm@redhat.com>
 */
@Tag("reproducers")
public class AppReproducersTest {

    private static final Logger LOGGER = Logger.getLogger(AppReproducersTest.class.getName());
    private static final String LOCALEINCLUDES_SWITCH_REPLACEMENT_1_MANDREL_PRE_24_2_0 = "-J-Duser.country=CA";
    private static final String LOCALEINCLUDES_SWITCH_REPLACEMENT_2_MANDREL_PRE_24_2_0 = "-J-Duser.language=fr";
    private static final String LOCALEINCLUDES_SWITCH_REPLACEMENT_1_MANDREL_POST_24_2_0 = "-H:IncludeLocales=fr-CA";
    private static final String LOCALEINCLUDES_SWITCH_REPLACEMENT_2_MANDREL_POST_24_2_0 = "";
    private static final String EXTRA_TZONES_OPTS = "-Duser.language=fr";

    public static final String BASE_DIR = getBaseDir();
    public static final String LOCALEINCLUDES_TOKEN_1 = "<TZ_INCLUDE_TOKEN_1>";
    public static final String LOCALEINCLUDES_TOKEN_2 = "<TZ_INCLUDE_TOKEN_2>";

    public static final String RUNTIME_IMAGE_BASE_TOKEN = "<RUNTIME_IMAGE_BASE>";
    public static final String BUILDX_LOAD_TOKEN = "<BUILDX_PUSH>";
    // Note Docfkerfile.<RUNTIME_IMAGE_BASE> in ./apps/imageio/ for AWT tests.
    /*
    amzn1 is not used. It's here to prove that the test works and that the image built with ubi8 really
    fails on a too old glibc linux.

    The only caveat is that such failure manifests not with a clear:
            /work/target/imageio: /lib64/libc.so.6: version `GLIBC_2.34' not found (required by /work/target/imageio)

    but with a completely misleading:
            Exception in thread "main" java.io.IOException: Problem reading font data.
                at java.desktop@21.0.6/java.awt.Font.createFont0(Font.java:1205)
                at java.desktop@21.0.6/java.awt.Font.createFont(Font.java:1076)

    This is caused by a purposefully vague error message in the JDK over here:
    https://github.com/openjdk/jdk21u-dev/blob/jdk-21.0.6%2B7/src/java.desktop/share/classes/java/awt/Font.java#L1205
     */
    public static final String[] RUNTIME_IMAGE_BASE = new String[] { "ubi8", "ubi9", "cnts10", "amzn2", "amzn2023", "ubnt2204", "ubnt2404" };

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
            List<String> cmd = getRunCommand(app.buildAndRunCmds.runCommands[0]);
            process = runCommand(cmd, appDir, processLog, app);
            assertNotNull(process, "The test application failed to run. Check " + getLogsDir(cn, mn) + File.separator + processLog.getName());
            process.waitFor(5, TimeUnit.SECONDS);
            Logs.appendln(report, appDir.getAbsolutePath());
            Logs.appendlnSection(report, String.join(" ", cmd));

            LOGGER.info("Running...#2");
            cmd = getRunCommand(app.buildAndRunCmds.runCommands[0]);
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

    /**
     * Resources handling changed:
     * 3: N/A -> JDK
     * 14: N/A -> JDK
     * In https://github.com/oracle/graal/commit/8faf577
     *
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
     * 31: N/A -> JDK
     * In https://github.com/oracle/graal/commit/3e9313ec2f41e96dcd1a3a621675adc2e9e3f8ac
     *
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
            final List<String> cmd = getRunCommand(app.buildAndRunCmds.runCommands[0]);
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
    @DisabledOnOs({ OS.WINDOWS, OS.MAC }) // AWT support is not there yet
    @IfMandrelVersion(min = "21.1")
    public void imageioAWTTest(TestInfo testInfo) throws IOException, InterruptedException {
        imageioAWT(testInfo, Apps.IMAGEIO);
    }

    public void imageioAWT(TestInfo testInfo, Apps app) throws IOException, InterruptedException {
        final boolean inContainer = app.runtimeContainer != ContainerNames.NONE;
        if ("aarch64".equalsIgnoreCase(ARCH) &&
                (getVersion(inContainer).compareTo(Version.create(24, 2, 0)) >= 0) &&
                (getVersion(inContainer).compareTo(Version.create(25, 0, 0)) <= 0)) {
            LOGGER.warn("Support for the Foreign Function and Memory API is currently available only on the AMD64 architecture.");
            LOGGER.warn("Skipping testing app: " + app);
            return;
        }
        LOGGER.info("Testing app: " + app);
        Process process = null;
        File processLog = null;
        final StringBuilder report = new StringBuilder();
        final File appDir = Path.of(BASE_DIR, app.dir).toFile();
        final File metaINF = Path.of(BASE_DIR, app.dir, "src", "main", "resources", "META-INF", "native-image").toFile();
        final String cn = testInfo.getTestClass().get().getCanonicalName();
        final String mn = testInfo.getTestMethod().get().getName();
        final List<String> pictures = List.of(
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
        // Control data, records RGBA pixel values at XY coordinates to compare across images
        // generated by HotSpot and native-image.
        final Map<String, Map<Integer[], Integer[]>> imageNamePixelXYValueRGBA = new HashMap<>(pictures.size());
        final Map<String, String> imageNameSha256 = new HashMap<>(pictures.size());
        final Pattern svgWrapperPattern = Pattern.compile("data:image/png;base64,(.*?)\"");

        try {
            // Pre-run cleanup, just in case.
            pictures.forEach(f -> new File(appDir, f).delete());
            cleanTarget(app);
            if (metaINF.exists()) {
                FileUtils.cleanDirectory(metaINF);
            }
            if (inContainer) {
                for (String base : RUNTIME_IMAGE_BASE) {
                    removeContainer(app.runtimeContainer.name + "_" + base);
                }
            }

            // Logs dir
            Files.createDirectories(Paths.get(appDir.getAbsolutePath() + File.separator + "logs"));

            // Build
            processLog = Path.of(appDir.getAbsolutePath(), "logs", "build-and-run.log").toFile();
            builderRoutine(app, report, cn, mn, appDir, processLog, null, getSwitches(app));

            // Record images' pixels as created by a Java process, HotSpot mode
            final List<String> errors;
            // In container, we record pixel values at some predefined interesting points in the images.
            // We don't compare hashsums as different fontconfig for each runtime image could affect
            // antialiasing and thus the hashsums.
            if (inContainer) {
                errors = recordImagePixelXYValueRGBA(pictures, imageNamePixelXYValueRGBA, appDir, svgWrapperPattern);
            } else {
                errors = recordImageSha256(pictures, imageNameSha256, appDir);
            }
            assertTrue(errors.isEmpty(),
                    "There were errors checking the generated image files, see:\n" + String.join("\n", errors));
            assertEquals(pictures.size(), Math.max(imageNamePixelXYValueRGBA.size(), imageNameSha256.size()),
                    "There was supposed to be " + pictures.size() + " pictures generated.");

            // Remove files generated by java run to prevent false negatives
            pictures.forEach(f -> new File(appDir, f).delete());

            // Fontconfig might look for fonts here, see similar thing in Quarkus:
            // https://github.com/quarkusio/quarkus/blob/main/extensions/awt/runtime/src/main/java/io/quarkus/awt/runtime/JDKSubstitutions.java#L53
            // Details: https://github.com/Karm/mandrel-integration-tests/issues/151#issuecomment-1516802244
            Files.createDirectories(Path.of(appDir.toString(), "conf", "fonts"));
            Files.createDirectories(Path.of(appDir.toString(), "lib"));

            if (inContainer) {
                if (DOCKER_GHA_SUMMARY_NAME != null) {
                    final String summary = "│   ├─ Testing builder image " + BUILDER_IMAGE + " begins:\n";
                    Files.writeString(Path.of(BASE_DIR, "..", DOCKER_GHA_SUMMARY_NAME), summary, UTF_8, CREATE, APPEND);
                }
                for (String base : RUNTIME_IMAGE_BASE) {
                    if (isBuilderImageIncompatible(base)) {
                        if (DOCKER_GHA_SUMMARY_NAME != null) {
                            final String summary = "│   │   ⬛ " + base + " based runtime image test SKIPPED (glibc too old)\n";
                            Files.writeString(Path.of(BASE_DIR, "..", DOCKER_GHA_SUMMARY_NAME), summary, UTF_8, CREATE, APPEND);
                        } else {
                            LOGGER.info("Skipping " + base + " based runtime image test (glibc too old)");
                        }
                        continue;
                    }
                    LOGGER.info("Running with " + base + " runtime image...");
                    final List<String> cmdBuildImage = replaceSwitchesInCmd(getRunCommand(app.buildAndRunCmds.runCommands[0]),
                            Map.of(RUNTIME_IMAGE_BASE_TOKEN, base,
                                    BUILDX_LOAD_TOKEN, DOCKER_GHA_BUILDX ? "--load" : ""));
                    process = runCommand(cmdBuildImage, appDir, processLog, app);
                    assertNotNull(process, base + ": Runtime container build failed. Check " + getLogsDir(cn, mn) + File.separator + processLog.getName());
                    process.waitFor(10, TimeUnit.MINUTES); // We are potentially downloading base image, packages etc.
                    Logs.appendln(report, appDir.getAbsolutePath());
                    Logs.appendlnSection(report, String.join(" ", cmdBuildImage));

                    // Remove any files generated by java run to prevent false negatives
                    pictures.forEach(f -> new File(appDir, f).delete());

                    final List<String> cmdRunImage = replaceSwitchesInCmd(getRunCommand(app.buildAndRunCmds.runCommands[1]),
                            Map.of(RUNTIME_IMAGE_BASE_TOKEN, base));
                    process = runCommand(cmdRunImage, appDir, processLog, app);
                    assertNotNull(process, base + ": Runtime container run failed. Check " + getLogsDir(cn, mn) + File.separator + processLog.getName());
                    process.waitFor(15, TimeUnit.SECONDS);
                    Logs.appendln(report, appDir.getAbsolutePath());
                    Logs.appendlnSection(report, String.join(" ", cmdRunImage));

                    // Test output
                    final List<String> interimErrs = verifyGeneratedImages(imageNamePixelXYValueRGBA, appDir, svgWrapperPattern, base);
                    if (DOCKER_GHA_SUMMARY_NAME != null) {
                        final boolean success = interimErrs.isEmpty();
                        final String summary = (success ? "│   │   ✅ " : "│   │   ❌ ") + base + " based runtime image test " + (success ? "succeeded" : "failed") + "\n";
                        Files.writeString(Path.of(BASE_DIR, "..", DOCKER_GHA_SUMMARY_NAME), summary, UTF_8, CREATE, APPEND);
                    }
                    errors.addAll(interimErrs);
                }
                assertTrue(errors.isEmpty(),
                        "There were errors checking the generated image files, see:\n" + String.join("\n", errors));
            } else {
                LOGGER.info("Running...");
                final List<String> cmd = getRunCommand(app.buildAndRunCmds.runCommands[0]);
                process = runCommand(cmd, appDir, processLog, app);
                assertNotNull(process, "The test application failed to run. Check " + getLogsDir(cn, mn) + File.separator + processLog.getName());
                process.waitFor(15, TimeUnit.SECONDS);
                Logs.appendln(report, appDir.getAbsolutePath());
                Logs.appendlnSection(report, String.join(" ", cmd));

                // Test output
                errors.addAll(verifyGeneratedImages(imageNameSha256, appDir));
                assertTrue(errors.isEmpty(),
                        "There were errors checking the generated image files, see:\n" + String.join("\n", errors));
            }

            // Test static libs in the executable
            final File executable = new File(appDir.getAbsolutePath() + File.separator + "target", "imageio");
            final Set<String> expected = new HashSet<>();
            expected.add("libawt.a");
            expected.add("libawt_headless.a");
            expected.add("libfdlibm.a");
            expected.add("libfontmanager.a");
            expected.add("libjava.a");
            expected.add("libjavajpeg.a");
            expected.add("libjvm.a");
            expected.add("liblcms.a");
            expected.add("liblibchelper.a");
            expected.add("libnet.a");
            expected.add("libnio.a");
            expected.add("libzip.a");
            if (getVersion(inContainer).compareTo(Version.parse("24.2")) >= 0) {
                expected.add("libsvm_container.a");
            }
            if (getVersion(inContainer).compareTo(Version.parse("23.0")) >= 0) {
                // The set of static libs for imageio is smaller beginning with Mandrel 23+ as
                // it has dynamic AWT support.
                expected.remove("libawt_headless.a");
                expected.remove("libfontmanager.a");
                expected.remove("libjavajpeg.a");
                expected.remove("liblcms.a");
                expected.remove("libawt.a");
            }
            if (UsedVersion.jdkFeature(inContainer) >= 21) {
                // JDK 21 has fdlibm ported to Java. See JDK-8171407
                expected.remove("libfdlibm.a");
            }
            if (UsedVersion.jdkFeature(inContainer) > 11 || (UsedVersion.jdkFeature(inContainer) == 11 && UsedVersion.jdkUpdate(inContainer) > 12)) {
                // Harfbuzz removed: https://github.com/graalvm/mandrel/issues/286
                // NO-OP
            } else {
                expected.add("libharfbuzz.a");
            }

            final Set<String> actual = listStaticLibs(executable);

            assertEquals(expected, actual, "A different set of static libraries was expected. \n" +
                    "Expected: " + expected.stream().sorted().toList() + "\n" +
                    "Actual:   " + actual.stream().sorted().toList());

            processStopper(process, false);
            Logs.checkLog(cn, mn, app, processLog);
            if (DOCKER_GHA_SUMMARY_NAME != null) {
                final String summary = "│   └─ Completed: " + BUILDER_IMAGE + "\n";
                Files.writeString(Path.of(BASE_DIR, "..", DOCKER_GHA_SUMMARY_NAME), summary, UTF_8, CREATE, APPEND);
            }
        } catch (Throwable e) {
            if (DOCKER_GHA_SUMMARY_NAME != null) {
                final String summary = "│   └─ Completed with errors: " + BUILDER_IMAGE + "\n";
                Files.writeString(Path.of(BASE_DIR, "..", DOCKER_GHA_SUMMARY_NAME), summary, UTF_8, CREATE, APPEND);
            }
            throw e;
        } finally {
            cleanup(process, cn, mn, report, app, processLog);
            if (inContainer) {
                for (String base : RUNTIME_IMAGE_BASE) {
                    removeContainer(app.runtimeContainer.name + "_" + base);
                }
            }
            if (metaINF.exists()) {
                FileUtils.cleanDirectory(metaINF);
            }
            cleanDirOrFile(
                    new File(appDir, "conf").getAbsolutePath(),
                    new File(appDir, "lib").getAbsolutePath(),
                    new File(appDir, "?").getAbsolutePath(),
                    new File(appDir, ".cache").getAbsolutePath(),
                    new File(appDir, ".java").getAbsolutePath(),
                    new File(appDir, "dependency-reduced-pom.xml").getAbsolutePath()
            );
            pictures.forEach(f -> new File(appDir, f).delete());
        }
    }

    public List<String> recordImageSha256(List<String> pictures, Map<String, String> imageNameSha256, File appDir) {
        final List<String> errors = new ArrayList<>(pictures.size());
        pictures.forEach(f -> {
            try {
                imageNameSha256.put(f, DigestUtils.sha256Hex(FileUtils.readFileToByteArray(new File(appDir, f))));
            } catch (IOException e) {
                errors.add(f + " cannot be loaded.");
                e.printStackTrace();
            }
        });
        return errors;
    }

    public List<String> verifyGeneratedImages(Map<String, String> imageNameSha256, File appDir) {
        final List<String> errors = new ArrayList<>(imageNameSha256.size());
        imageNameSha256.forEach((fileName, hash) -> {
            final File picture = new File(appDir, fileName);
            if (picture.exists() && picture.isFile()) {
                try {
                    final String sha256hex = DigestUtils.sha256Hex(FileUtils.readFileToByteArray(picture));
                    if (!hash.equals(sha256hex)) {
                        errors.add(fileName + "'s sha256 hash was " + sha256hex + ", expected hash: " + hash);
                    }
                } catch (IOException e) {
                    errors.add(fileName + " cannot be loaded.");
                    e.printStackTrace();
                }
            } else {
                errors.add(fileName + " was not generated.");
            }
        });
        return errors;
    }

    public List<String> recordImagePixelXYValueRGBA(List<String> pictures, Map<String, Map<Integer[], Integer[]>> imageNamePixelXYValueRGBA, File appDir, Pattern svgWrapperPattern) {
        final List<String> errors = new ArrayList<>(pictures.size());
        pictures.forEach(f -> {
            final File picture = new File(appDir, f);
            try {
                final BufferedImage image = f.endsWith("svg") ? ImageIO.read(new ByteArrayInputStream(
                        Base64.getDecoder().decode(getSubstringFromSmallTextFile(svgWrapperPattern, picture.toPath(), UTF_8)))) :
                        ImageIO.read(picture);
                final Map<Integer[], Integer[]> pixelXYValueRGBA = new HashMap<>();
                for (Integer[] xy : new Integer[][] {
                        { 30, 51 },
                        { 250, 250 },
                        { 374, 374 } }) {
                    final int[] rgba = new int[4]; //4BYTE RGBA, A optional
                    image.getData().getPixel(xy[0], xy[1], rgba);
                    pixelXYValueRGBA.put(xy, new Integer[] { rgba[0], rgba[1], rgba[2], rgba[3] });
                }
                imageNamePixelXYValueRGBA.put(f, pixelXYValueRGBA);
            } catch (Exception e) {
                errors.add(picture.getAbsolutePath() + " cannot be loaded.");
                e.printStackTrace();
            }
        });
        return errors;
    }

    public List<String> verifyGeneratedImages(Map<String, Map<Integer[], Integer[]>> imageNamePixelXYValueRGBA,
            File appDir, Pattern svgWrapperPattern, String base) {
        // When comparing pixel colour values, how much difference from the expected value is allowed.
        // 0 means no difference is tolerated.
        // The tolerance outlined here means we allow for a tiny drift between HotSpot generated imagery
        // and native-image generated imagery, mostly due to different antialiasing of fontconfig/harfbuzz.
        final int[] PIXEL_DIFFERENCE_THRESHOLD_RGBA_VEC = new int[] { 7, 7, 7, 0 };
        final List<String> errors = new ArrayList<>(imageNamePixelXYValueRGBA.size());
        imageNamePixelXYValueRGBA.forEach((fileName, xyRGBA) -> {
            final File picture = new File(appDir, fileName);
            if (picture.exists() && picture.isFile() && picture.length() > 5500) {
                try {
                    final BufferedImage image = fileName.endsWith("svg") ? ImageIO.read(new ByteArrayInputStream(
                            Base64.getDecoder().decode(getSubstringFromSmallTextFile(svgWrapperPattern, picture.toPath(), UTF_8)))) :
                            ImageIO.read(picture);
                    for (Integer[] xy : xyRGBA.keySet()) {
                        final int[] actual = new int[4]; //4BYTE RGBA, A optional
                        image.getData().getPixel(xy[0], xy[1], actual);
                        final int[] expected = new int[] { xyRGBA.get(xy)[0], xyRGBA.get(xy)[1], xyRGBA.get(xy)[2], xyRGBA.get(xy)[3] };
                        assertTrue(compareArrays(expected, actual, PIXEL_DIFFERENCE_THRESHOLD_RGBA_VEC),
                                String.format("%s %s: Wrong pixel at [%d,%d]. Expected: [%d,%d,%d,%d] Actual: [%d,%d,%d,%d]", base, fileName,
                                        xy[0], xy[1],
                                        expected[0], expected[1], expected[2], expected[3],
                                        actual[0], actual[1], actual[2], actual[3]));
                    }
                } catch (IOException e) {
                    errors.add(base + ": " + fileName + " cannot be loaded.");
                    e.printStackTrace();
                }
            } else {
                errors.add(base + ": " + fileName + " was not generated or is an empty file.");
            }
        });
        return errors;
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

            Map<String, String> switches = null;
            final boolean inContainer = app.runtimeContainer != ContainerNames.NONE;
            if (getVersion(inContainer).compareTo(Version.create(24, 2, 0)) >= 0) {
                // Locale inclusion for Mandrel 24.2 ignores -Duser.language and -Duser.country settings
                // at build time.
                switches = Map.of(LOCALEINCLUDES_TOKEN_1, LOCALEINCLUDES_SWITCH_REPLACEMENT_1_MANDREL_POST_24_2_0,
                        LOCALEINCLUDES_TOKEN_2, LOCALEINCLUDES_SWITCH_REPLACEMENT_2_MANDREL_POST_24_2_0);
            } else {
                switches = Map.of(LOCALEINCLUDES_TOKEN_1, LOCALEINCLUDES_SWITCH_REPLACEMENT_1_MANDREL_PRE_24_2_0,
                        LOCALEINCLUDES_TOKEN_2, LOCALEINCLUDES_SWITCH_REPLACEMENT_2_MANDREL_PRE_24_2_0);
            }
            builderRoutine(app, report, cn, mn, appDir, processLog, null, switches);

            LOGGER.info("Running...");
            List<String> cmd = getRunCommand(app.buildAndRunCmds.runCommands[0]);
            if (getVersion(inContainer).compareTo(Version.create(24, 2, 0)) >= 0) {
                // Mandrel 24.2 needs the desired language set at runtime
                cmd.add(EXTRA_TZONES_OPTS);
            }
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
    @Tag("builder-image")
    @IfMandrelVersion(minJDK = "21.0.3", inContainer = true)
    public void monitorFieldOffsetContainerTest(TestInfo testInfo) throws IOException, InterruptedException {
        monitorFieldOffsetOK(testInfo, Apps.MONITOR_OFFSET_OK_BUILDER_IMAGE);
        monitorFieldOffsetNOK(testInfo, Apps.MONITOR_OFFSET_NOK_BUILDER_IMAGE);
    }

    @Test
    @IfMandrelVersion(minJDK = "21.0.3")
    public void monitorFieldOffsetTest(TestInfo testInfo) throws IOException, InterruptedException {
        monitorFieldOffsetOK(testInfo, Apps.MONITOR_OFFSET_OK);
        monitorFieldOffsetNOK(testInfo, Apps.MONITOR_OFFSET_NOK);
    }

    public void monitorFieldOffsetOK(TestInfo testInfo, Apps app) throws IOException, InterruptedException {
        LOGGER.info("Testing app: " + app);
        Process process = null;
        File processLog = null;
        final StringBuilder report = new StringBuilder();
        final File appDir = Path.of(BASE_DIR, app.dir).toFile();
        final String cn = testInfo.getTestClass().get().getCanonicalName();
        final String mn = testInfo.getTestMethod().get().getName();
        final boolean inContainer = app.runtimeContainer != ContainerNames.NONE;
        try {
            // Cleanup
            cleanTarget(app);
            if (inContainer) {
                removeContainers(app.runtimeContainer.name);
            }
            Files.createDirectories(Paths.get(appDir.getAbsolutePath() + File.separator + "logs"));

            // OK version
            processLog = Path.of(appDir.getAbsolutePath(), "logs", "build-and-run.log").toFile();
            builderRoutine(app, report, cn, mn, appDir, processLog);
            LOGGER.info("Running...");
            final List<String> cmd = getRunCommand(app.buildAndRunCmds.runCommands[0]);
            process = runCommand(cmd, appDir, processLog, app);
            assertNotNull(process, "The test application failed to run. Check " + getLogsDir(cn, mn) + File.separator + processLog.getName());
            process.waitFor(5, TimeUnit.SECONDS);
            Logs.appendln(report, appDir.getAbsolutePath());
            Logs.appendlnSection(report, String.join(" ", cmd));
            Logs.checkLog(cn, mn, app, processLog);
            processStopper(process, false);
            final Pattern pok = Pattern.compile(".*Done all 9000 iterations.*");
            assertTrue(searchLogLines(pok, processLog, Charset.defaultCharset()), "Expected pattern " + pok + " was not found in the log." +
                    "Perhaps ContendedPaddingWidth default has changed from 128 bytes to something else?");
        } finally {
            cleanup(process, cn, mn, report, app, processLog);
        }
    }

    public void monitorFieldOffsetNOK(TestInfo testInfo, Apps app) throws IOException, InterruptedException {
        LOGGER.info("Testing app: " + app);
        Process process = null;
        File processLog = null;
        final StringBuilder report = new StringBuilder();
        final File appDir = Path.of(BASE_DIR, app.dir).toFile();
        final String cn = testInfo.getTestClass().get().getCanonicalName();
        final String mn = testInfo.getTestMethod().get().getName();
        final boolean inContainer = app.runtimeContainer != ContainerNames.NONE;
        try {
            // Cleanup
            cleanTarget(app);
            if (inContainer) {
                removeContainers(app.runtimeContainer.name);
            }
            Files.createDirectories(Paths.get(appDir.getAbsolutePath() + File.separator + "logs"));

            // NOK version
            processLog = Path.of(appDir.getAbsolutePath(), "logs", "build-and-run.log").toFile();
            builderRoutine(app, report, cn, mn, appDir, processLog);
            Logs.checkLog(cn, mn, app, processLog);
            final Pattern pnok = Pattern.compile(".*Class monitor_field_offset.Main480 has an invalid monitor field offset.*");
            assertTrue(searchLogLines(pnok, processLog, Charset.defaultCharset()), "Expected pattern " + pnok + " was not found in the log.");
        } finally {
            cleanup(process, cn, mn, report, app, processLog);
        }
    }

    @Test
    @Tag("builder-image")
    @IfMandrelVersion(min = "23.1.6", inContainer = true)
    public void jdkReflectionsContainerTest(TestInfo testInfo) throws IOException, InterruptedException {
        jdkReflections(testInfo, Apps.JDK_REFLECTIONS_BUILDER_IMAGE);
    }

    @Test
    @IfMandrelVersion(min = "23.1.6")
    public void jdkReflectionsTest(TestInfo testInfo) throws IOException, InterruptedException {
        jdkReflections(testInfo, Apps.JDK_REFLECTIONS);
    }

    public void jdkReflections(TestInfo testInfo, Apps app) throws IOException, InterruptedException {
        LOGGER.info("Testing app: " + app);
        Process process = null;
        File buildLog = null;
        File runLog = null;
        final StringBuilder report = new StringBuilder();
        final File appDir = Path.of(BASE_DIR, app.dir).toFile();
        final String cn = testInfo.getTestClass().get().getCanonicalName();
        final String mn = testInfo.getTestMethod().get().getName();
        final boolean inContainer = app.runtimeContainer != ContainerNames.NONE;
        try {
            // Cleanup
            cleanTarget(app);
            if (inContainer) {
                removeContainers(app.runtimeContainer.name);
            }
            Files.createDirectories(Paths.get(appDir.getAbsolutePath() + File.separator + "logs"));
            buildLog = Path.of(appDir.getAbsolutePath(), "logs", "build.log").toFile();
            runLog = Path.of(appDir.getAbsolutePath(), "logs", "run.log").toFile();
            builderRoutine(app, report, cn, mn, appDir, buildLog);
            LOGGER.info("Running...");
            final List<String> cmd = getRunCommand(app.buildAndRunCmds.runCommands[0]);
            process = runCommand(cmd, appDir, runLog, app);
            assertNotNull(process, "The test application failed to run. Check " + getLogsDir(cn, mn) + File.separator + buildLog.getName() +
                    " and also check https://github.com/graalvm/graalvm-community-jdk21u/issues/28");
            process.waitFor(5, TimeUnit.SECONDS);
            Logs.appendln(report, appDir.getAbsolutePath());
            Logs.appendlnSection(report, String.join(" ", cmd));
            Logs.checkLog(cn, mn, app, buildLog);
            Logs.checkLog(cn, mn, app, runLog);
            processStopper(process, true);
            final Pattern p = Pattern.compile(".*Hello from a virtual thread called meh-10000.*");
            assertTrue(searchLogLines(p, runLog, Charset.defaultCharset()),
                    "Expected pattern " + p + " was not found in the log. Check " + getLogsDir(cn, mn) + File.separator + runLog.getName() +
                            " and also check https://github.com/graalvm/graalvm-community-jdk21u/issues/28");
            final Pattern p1 = Pattern.compile(".*java.lang.NoSuchMethodException: java.lang.Thread.getNextThreadIdOffset.*");
            assertTrue(searchLogLines(p, runLog, Charset.defaultCharset()),
                    "Expected pattern " + p1 + " was not found in the log. Check " + getLogsDir(cn, mn) + File.separator + runLog.getName() +
                            ". The method getNextThreadIdOffset is deleted from native-image intentionally.");
        } finally {
            cleanup(process, cn, mn, report, app, buildLog, runLog);
        }
    }

    @Test
    @IfMandrelVersion(min = "23.1.7")
    public void cacertsTest(TestInfo testInfo) throws IOException, InterruptedException {
        cacerts(testInfo, Apps.CACERTS);
    }

    public void cacerts(TestInfo testInfo, Apps app) throws IOException, InterruptedException {
        LOGGER.info("Testing app: " + app);
        Process process = null;
        File buildLog = null;
        File runLog = null;
        final StringBuilder report = new StringBuilder();
        final File appDir = Path.of(BASE_DIR, app.dir).toFile();
        final String cn = testInfo.getTestClass().get().getCanonicalName();
        final String mn = testInfo.getTestMethod().get().getName();
        final boolean inContainer = app.runtimeContainer != ContainerNames.NONE;
        try {
            // Cleanup
            cleanTarget(app);
            if (inContainer) {
                removeContainers(app.runtimeContainer.name);
            }
            Files.createDirectories(Paths.get(appDir.getAbsolutePath() + File.separator + "logs"));
            buildLog = Path.of(appDir.getAbsolutePath(), "logs", "build.log").toFile();
            runLog = Path.of(appDir.getAbsolutePath(), "logs", "run.log").toFile();
            builderRoutine(app, report, cn, mn, appDir, buildLog);
            LOGGER.info("Running...");
            final List<String> cmd = getRunCommand(app.buildAndRunCmds.runCommands[0]);
            process = runCommand(cmd, appDir, runLog, app);
            assertNotNull(process, "The test application failed to run. Check " + getLogsDir(cn, mn) + File.separator + buildLog.getName());
            process.waitFor(5, TimeUnit.SECONDS);
            Logs.appendln(report, appDir.getAbsolutePath());
            Logs.appendlnSection(report, String.join(" ", cmd));
            Logs.checkLog(cn, mn, app, buildLog);
            Logs.checkLog(cn, mn, app, runLog);
            processStopper(process, true);
            final Pattern p = Pattern.compile(".*Checked .* certificates. PASS!*");
            assertTrue(searchLogLines(p, runLog, Charset.defaultCharset()),
                    "Expected pattern " + p + " was not found in the log. Check " + getLogsDir(cn, mn) + File.separator + runLog.getName());
            final Pattern p2 = Pattern.compile(".*Blocked certificates test PASSES\\..*");
            assertTrue(searchLogLines(p, runLog, Charset.defaultCharset()),
                    "Expected pattern " + p2 + " was not found in the log. Check " + getLogsDir(cn, mn) + File.separator + runLog.getName());
        } finally {
            cleanup(process, cn, mn, report, app, buildLog, runLog);
        }
    }

    /*
    TODO: Uncomment when Mandrel 23.1.8 is released and the issue is backported:
    "Fixed System.getProperties() when called from virtual thread."
    https://github.com/oracle/graal/commit/015a8f7fdd5

    @Test
    @Tag("builder-image")
    @IfMandrelVersion(min = "23.1.8", max = "23.1.999", inContainer = true)
    public void vthreadsPropsContainer23_1Test(TestInfo testInfo) throws IOException, InterruptedException {
        vthreadsProps(testInfo, Apps.VTHREADS_PROPS_BUILDER_IMAGE);
    }

    @Test
    @IfMandrelVersion(min = "23.1.8", max = "23.1.999")
    public void vthreadsProps23_1Test(TestInfo testInfo) throws IOException, InterruptedException {
        vthreadsProps(testInfo, Apps.VTHREADS_PROPS);
    }
    */

    @Test
    @Tag("builder-image")
    @IfMandrelVersion(min = "24.2.0", inContainer = true)
    public void vthreadsPropsContainer24_2Test(TestInfo testInfo) throws IOException, InterruptedException {
        vthreadsProps(testInfo, Apps.VTHREADS_PROPS_BUILDER_IMAGE);
    }

    @Test
    @IfMandrelVersion(min = "24.2.0")
    public void vthreadsProps24_2Test(TestInfo testInfo) throws IOException, InterruptedException {
        vthreadsProps(testInfo, Apps.VTHREADS_PROPS);
    }

    /**
     * https://github.com/oracle/graal/issues/9939
     * System.getProperties() fails when called from a virtual thread
     */
    public void vthreadsProps(TestInfo testInfo, Apps app) throws IOException, InterruptedException {
        LOGGER.info("Testing app: " + app);
        Process process = null;
        File processLog = null;
        final StringBuilder report = new StringBuilder();
        final File appDir = Path.of(BASE_DIR, app.dir).toFile();
        final String cn = testInfo.getTestClass().get().getCanonicalName();
        final String mn = testInfo.getTestMethod().get().getName();
        final boolean inContainer = app.runtimeContainer != ContainerNames.NONE;
        Map<String, String> env = null;
        // Linux/Mac only for now when not run in a container and on a JDK < 21 (e.g. 17)
        Runtime.Version version = Runtime.version();
        if (version.feature() < 21 && !inContainer) {
            LOGGER.info("Running with JDK version " + version.feature() + ". Compiling using GraalVM/Mandrel instead.");
            env = new HashMap<>();
            String javaHome = System.getenv("GRAALVM_HOME");
            LOGGER.info("Running maven build with JAVA_HOME = " + javaHome);
            env.put("JAVA_HOME", javaHome);
        }
        final Pattern p = Pattern.compile(".*=== RESULT: true true true true true true ===.*");
        try {
            // Cleanup
            cleanTarget(app);
            if (inContainer) {
                for (String base : RUNTIME_IMAGE_BASE) {
                    removeContainer(app.runtimeContainer.name + "_" + base);
                }
            }
            Files.createDirectories(Paths.get(appDir.getAbsolutePath() + File.separator + "logs"));
            processLog = Path.of(appDir.getAbsolutePath(), "logs", "build-and-run.log").toFile();
            builderRoutine(app, report, cn, mn, appDir, processLog, env, getSwitches(app));
            if (inContainer) {
                final Map<String, String> errors = new HashMap<>();
                for (String base : RUNTIME_IMAGE_BASE) {
                    if (isBuilderImageIncompatible(base)) {
                        LOGGER.info("Skipping " + base + " based runtime image test (glibc too old)");
                        continue;
                    }
                    LOGGER.info("Running with " + base + " runtime image...");
                    final File baseProcessLog = Path.of(appDir.getAbsolutePath(), "logs", base + "-run.log").toFile();
                    for (int i = 0; i < app.buildAndRunCmds.runCommands.length; i++) {
                        final List<String> cmdBuildImage = replaceSwitchesInCmd(getRunCommand(app.buildAndRunCmds.runCommands[i]),
                                Map.of(RUNTIME_IMAGE_BASE_TOKEN, base));
                        process = runCommand(cmdBuildImage, appDir, baseProcessLog, app);
                        assertNotNull(process, base + ": Container failed. Check " + getLogsDir(cn, mn) + File.separator + baseProcessLog.getName());
                        process.waitFor(10, TimeUnit.MINUTES); // We are potentially downloading base image
                        Logs.appendln(report, appDir.getAbsolutePath());
                        Logs.appendlnSection(report, String.join(" ", cmdBuildImage));
                    }
                    if (!searchLogLines(p, baseProcessLog, Charset.defaultCharset())) {
                        errors.put(base, "Expected pattern " + p + " was not found in the log. Check " + getLogsDir(cn, mn) + File.separator + baseProcessLog.getName());
                    }
                }
                assertTrue(errors.isEmpty(), "There were errors checking the runtime logs, see:\n" + String.join("\n", errors.values()));
            } else {
                LOGGER.info("Running...");
                final List<String> cmd = getRunCommand(app.buildAndRunCmds.runCommands[0]);
                process = runCommand(cmd, appDir, processLog, app);
                assertNotNull(process, "The test application failed to run. Check " + getLogsDir(cn, mn) + File.separator + processLog.getName());
                process.waitFor(1, TimeUnit.SECONDS);
                Logs.appendln(report, appDir.getAbsolutePath());
                Logs.appendlnSection(report, String.join(" ", cmd));
                assertTrue(searchLogLines(p, processLog, Charset.defaultCharset()),
                        "Expected pattern " + p + " was not found in the log. Check " + getLogsDir(cn, mn) + File.separator + processLog.getName());
            }
            Logs.checkLog(cn, mn, app, processLog);
        } finally {
            if (inContainer) {
                Arrays.stream(RUNTIME_IMAGE_BASE)
                        .filter(base -> !isBuilderImageIncompatible(base))
                        .map(base -> Path.of(appDir.getAbsolutePath(), "logs", base + "-run.log").toFile()).forEach(f -> {
                            try {
                                Logs.archiveLog(cn, mn, f);
                            } catch (IOException e) {
                                LOGGER.error("Failed to archive " + f.getName(), e);
                            }
                        });
            }
            cleanup(process, cn, mn, report, app, processLog);
            if (inContainer) {
                for (String base : RUNTIME_IMAGE_BASE) {
                    removeContainer(app.runtimeContainer.name + "_" + base);
                }
            }
        }
    }

    @Test
    @Tag("builder-image")
    @IfMandrelVersion(min = "23.1.5", max = "23.1.999", inContainer = true)
    public void forSerializationContainer23_1Test(TestInfo testInfo) throws IOException, InterruptedException {
        forSerialization(testInfo, Apps.FOR_SERIALIZATION_BUILDER_IMAGE);
    }

    @Test
    @IfMandrelVersion(min = "23.1.5", max = "23.1.999")
    public void forSerialization23_1Test(TestInfo testInfo) throws IOException, InterruptedException {
        forSerialization(testInfo, Apps.FOR_SERIALIZATION);
    }

    @Test
    @Tag("builder-image")
    @IfMandrelVersion(min = "24.2.0", inContainer = true)
    public void forSerializationContainerPost24_2Test(TestInfo testInfo) throws IOException, InterruptedException {
        forSerialization(testInfo, Apps.FOR_SERIALIZATION_BUILDER_IMAGE);
    }

    @Test
    @IfMandrelVersion(min = "24.2.0")
    public void forSerializationPost24_2Test(TestInfo testInfo) throws IOException, InterruptedException {
        forSerialization(testInfo, Apps.FOR_SERIALIZATION);
    }

    public void forSerialization(TestInfo testInfo, Apps app) throws IOException, InterruptedException {
        LOGGER.info("Testing app: " + app);
        File processLog = null;
        final StringBuilder report = new StringBuilder();
        final File appDir = Path.of(BASE_DIR, app.dir).toFile();
        final File metaINF = Path.of(BASE_DIR, app.dir, "src", "main", "resources", "META-INF", "native-image").toFile();
        final String cn = testInfo.getTestClass().get().getCanonicalName();
        final String mn = testInfo.getTestMethod().get().getName();
        final boolean inContainer = app.runtimeContainer != ContainerNames.NONE;
        try {
            // Cleanup
            cleanTarget(app);
            if (metaINF.exists()) {
                FileUtils.cleanDirectory(metaINF);
            }
            if (inContainer) {
                removeContainers(app.runtimeContainer.name);
            }
            Files.createDirectories(Paths.get(appDir.getAbsolutePath() + File.separator + "logs"));
            processLog = Path.of(appDir.getAbsolutePath(), "logs", "build-and-run.log").toFile();
            builderRoutine(app, report, cn, mn, appDir, processLog);
            LOGGER.info("Running...");

            final List<String> cmdHotSpot = getRunCommand(app.buildAndRunCmds.runCommands[0]);
            final List<String> cmdNative = getRunCommand(app.buildAndRunCmds.runCommands[1]);
            final String hotSpotOutput = runCommand(cmdHotSpot, appDir);
            final String nativeOutput = runCommand(cmdNative, appDir);
            Logs.appendln(report, appDir.getAbsolutePath());
            Logs.appendlnSection(report, String.join(" ", cmdHotSpot));
            Logs.appendlnSection(report, hotSpotOutput);
            Logs.appendlnSection(report, String.join(" ", cmdNative));
            Logs.appendlnSection(report, nativeOutput);

            assertEquals(hotSpotOutput, nativeOutput, "The output of the HotSpot and native-image runs must be the same.");

            Logs.checkLog(cn, mn, app, processLog);

            if (inContainer) {
                removeContainers(app.runtimeContainer.name);
            }

        } finally {
            cleanup(null, cn, mn, report, app, processLog);
            if (metaINF.exists()) {
                FileUtils.cleanDirectory(metaINF);
            }
        }
    }

    @Test
    @Tag("calendars")
    @IfMandrelVersion(min = "22.3.5") // The fix for this test is in 22.3.5 and better
    public void calendarsBakedIn(TestInfo testInfo) throws IOException, InterruptedException {
        final Apps app = Apps.CALENDARS;
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
            List<String> cmd = getRunCommand(app.buildAndRunCmds.runCommands[0]);
            process = runCommand(cmd, appDir, processLog, app);
            assertNotNull(process, "The test application failed to run. Check " + getLogsDir(cn, mn) + File.separator + processLog.getName());
            process.waitFor(5, TimeUnit.SECONDS);
            Logs.appendln(report, appDir.getAbsolutePath());
            Logs.appendlnSection(report, String.join(" ", cmd));

            final Pattern p = Pattern.compile(".*Year: (?:1|1086), dayOfYear: 1, type: (?:japanese|buddhist|gregory).*");
            assertTrue(searchLogLines(p, processLog, Charset.defaultCharset()), "Expected pattern " + p.toString() + " was not found in the log.");

            processStopper(process, false);
            Logs.checkLog(cn, mn, app, processLog);
        } finally {
            cleanup(process, cn, mn, report, app, processLog);
        }
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
            List<String> cmd = getRunCommand(app.buildAndRunCmds.runCommands[0]);
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

            builderRoutine(app, report, cn, mn, appDir, processLog, null, getSwitches(app));

            final File inputData = new File(BASE_DIR + File.separator + app.dir + File.separator + "target" + File.separator + "test_data.txt");

            LOGGER.info("Running JVM mode...");
            long start = System.currentTimeMillis();
            List<String> cmd = getRunCommand(app.buildAndRunCmds.runCommands[0]);
            process = runCommand(cmd, appDir, processLog, app, inputData);
            assertNotNull(process, "The test application failed to run. Check " + getLogsDir(cn, mn) + File.separator + processLog.getName());
            process.waitFor(30, TimeUnit.SECONDS);
            long jvmRunTookMs = System.currentTimeMillis() - start;
            Logs.appendln(report, appDir.getAbsolutePath());
            Logs.appendlnSection(report, String.join(" ", cmd));

            LOGGER.info("Running Native mode...");
            start = System.currentTimeMillis();
            cmd = getRunCommand(app.buildAndRunCmds.runCommands[1]);
            process = runCommand(cmd, appDir, processLog, app, inputData);
            assertNotNull(process, "The test application failed to run. Check " + getLogsDir(cn, mn) + File.separator + processLog.getName());
            process.waitFor(30, TimeUnit.SECONDS);
            long nativeRunTookMs = System.currentTimeMillis() - start;
            Logs.appendln(report, appDir.getAbsolutePath());
            Logs.appendlnSection(report, String.join(" ", cmd));

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
                    "One from JVM run and one for Native image run. " + count +
                    " such hashes were found. Check build-and-run.log and report.md.");

            processStopper(process, false);
            Logs.checkLog(cn, mn, app, processLog);
            final Path measurementsLog = Paths.get(getLogsDir(cn, mn).toString(), "measurements.csv");

            LogBuilder.Log logJVM = new LogBuilder()
                    .app(app + "_JVM")
                    .timeToFinishMs(jvmRunTookMs)
                    .build();
            LogBuilder.Log logNative = new LogBuilder()
                    .app(app + "_NATIVE")
                    .timeToFinishMs(nativeRunTookMs)
                    .build();

            Logs.logMeasurements(logJVM, measurementsLog);
            Logs.logMeasurements(logNative, measurementsLog);
            Logs.appendln(report, "Measurements:");
            Logs.appendln(report, logJVM.headerMarkdown + "\n" + logJVM.lineMarkdown);
            Logs.appendln(report, logNative.lineMarkdown);
            Logs.checkThreshold(app, Logs.Mode.JVM, Logs.SKIP, Logs.SKIP, Logs.SKIP, jvmRunTookMs);
            Logs.checkThreshold(app, Logs.Mode.NATIVE, Logs.SKIP, Logs.SKIP, Logs.SKIP, nativeRunTookMs);
        } finally {
            cleanup(process, cn, mn, report, app, processLog);
        }
    }

    private static Map<String, String> getSwitches(Apps app) {
        final Map<String, String> switches = new HashMap<>();
        final Version version = getVersion(app.runtimeContainer != ContainerNames.NONE);
        if (version.compareTo(Version.create(23, 1, 0)) >= 0) {
            switches.put(UnlockExperimentalVMOptions_23_1.token, UnlockExperimentalVMOptions_23_1.replacement);
            switches.put(LockExperimentalVMOptions_23_1.token, LockExperimentalVMOptions_23_1.replacement);
        } else {
            switches.put(UnlockExperimentalVMOptions_23_1.token, "");
            switches.put(LockExperimentalVMOptions_23_1.token, "");
        }
        if (version.compareTo(Version.create(23, 0, 0)) >= 0) {
            switches.put(TrackNodeSourcePosition_23_0.token, TrackNodeSourcePosition_23_0.replacement);
            switches.put(DebugCodeInfoUseSourceMappings_23_0.token, DebugCodeInfoUseSourceMappings_23_0.replacement);
            switches.put(OmitInlinedMethodDebugLineInfo_23_0.token, OmitInlinedMethodDebugLineInfo_23_0.replacement);
        } else {
            switches.put(TrackNodeSourcePosition_23_0.token, "");
            switches.put(DebugCodeInfoUseSourceMappings_23_0.token, "");
            switches.put(OmitInlinedMethodDebugLineInfo_23_0.token, "");
        }
        if (version.compareTo(Version.create(24, 2, 0)) >= 0) {
            switches.put(ForeignAPISupport_24_2.token, ForeignAPISupport_24_2.replacement);
        } else {
            switches.put(ForeignAPISupport_24_2.token, "");
        }
        return switches;
    }
}
