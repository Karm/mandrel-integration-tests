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
import org.graalvm.tests.integration.utils.ContainerNames;
import org.graalvm.tests.integration.utils.GDBSession;
import org.graalvm.tests.integration.utils.LogBuilder;
import org.graalvm.tests.integration.utils.Logs;
import org.graalvm.tests.integration.utils.WebpageTester;
import org.graalvm.tests.integration.utils.versions.IfMandrelVersion;
import org.graalvm.tests.integration.utils.versions.UsedVersion;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.graalvm.tests.integration.utils.Commands.CONTAINER_RUNTIME;
import static org.graalvm.tests.integration.utils.Commands.QUARKUS_VERSION;
import static org.graalvm.tests.integration.utils.Commands.builderRoutine;
import static org.graalvm.tests.integration.utils.Commands.cleanTarget;
import static org.graalvm.tests.integration.utils.Commands.getBaseDir;
import static org.graalvm.tests.integration.utils.Commands.getRunCommand;
import static org.graalvm.tests.integration.utils.Commands.processStopper;
import static org.graalvm.tests.integration.utils.Commands.removeContainers;
import static org.graalvm.tests.integration.utils.Commands.runCommand;
import static org.graalvm.tests.integration.utils.Commands.searchBinaryFile;
import static org.graalvm.tests.integration.utils.Commands.searchLogLines;
import static org.graalvm.tests.integration.utils.Commands.stopAllRunningContainers;
import static org.graalvm.tests.integration.utils.Commands.stopRunningContainers;
import static org.graalvm.tests.integration.utils.Commands.waitForBufferToMatch;
import static org.graalvm.tests.integration.utils.Commands.waitForContainerLogToMatch;
import static org.graalvm.tests.integration.utils.Logs.getLogsDir;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

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
        LOGGER.info("Testing app: " + app.toString());
        Process process = null;
        File processLog = null;
        final StringBuilder report = new StringBuilder();
        final File appDir = new File(BASE_DIR + File.separator + app.dir);
        final String cn = testInfo.getTestClass().get().getCanonicalName();
        final String mn = testInfo.getTestMethod().get().getName();
        try {
            // Cleanup
            cleanTarget(app);
            Files.createDirectories(Paths.get(appDir.getAbsolutePath() + File.separator + "logs"));

            // Build
            processLog = new File(appDir.getAbsolutePath() + File.separator + "logs" + File.separator + "build-and-run.log");

            builderRoutine(app, report, cn, mn, appDir, processLog);

            LOGGER.info("Running...#1");
            List<String> cmd = getRunCommand(app.buildAndRunCmds.cmds[app.buildAndRunCmds.cmds.length - 1]);
            process = runCommand(cmd, appDir, processLog, app);
            process.waitFor(5, TimeUnit.SECONDS);
            Logs.appendln(report, appDir.getAbsolutePath());
            Logs.appendlnSection(report, String.join(" ", cmd));

            LOGGER.info("Running...#2");
            cmd = getRunCommand(app.buildAndRunCmds.cmds[app.buildAndRunCmds.cmds.length - 1]);
            process = runCommand(cmd, appDir, processLog, app);
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
        final File appDir = new File(BASE_DIR + File.separator + app.dir);
        final File metaINF = new File(appDir,
                "src" + File.separator + "main" + File.separator + "resources" + File.separator + "META-INF" + File.separator + "native-image");
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
            processLog = new File(appDir.getAbsolutePath() + File.separator + "logs" + File.separator + "build-and-run.log");

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
                    "mytest.wbmp"
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
            process.waitFor(15, TimeUnit.SECONDS);
            Logs.appendln(report, appDir.getAbsolutePath());
            Logs.appendlnSection(report, String.join(" ", cmd));

            // Test output
            controlData.forEach((fileName, hash) -> {
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
            assertTrue(errors.isEmpty(),
                    "There were errors checking the generated image files, see:\n" + String.join("\n", errors));

            // Test static libs in the executable
            final File executable = new File(appDir.getAbsolutePath() + File.separator + "target", "imageio");
            //TODO: This might be too fragile... e.g. order shouldn't matter.
            final String toFind = "libnet.a|libjavajpeg.a|libnio.a|liblibchelper.a|libjava.a|liblcms.a|libfontmanager.a|libawt_headless.a|libawt.a|libharfbuzz.a|libfdlibm.a|libzip.a|libjvm.a";
            final byte[] match = toFind.getBytes(US_ASCII);
            // Given the structure of the file, we can skip the first n bytes.
            boolean found = searchBinaryFile(executable, match, 1800);
            assertTrue(found, "String: " + toFind + " was expected in the executable file: " + executable);

            processStopper(process, false);
            Logs.checkLog(cn, mn, app, processLog);
        } finally {
            cleanup(process, cn, mn, report, app, processLog);
            if (metaINF.exists()) {
                FileUtils.cleanDirectory(metaINF);
            }
            new File(appDir, "dependency-reduced-pom.xml").delete();
            controlData.keySet().forEach(f -> new File(appDir, f).delete());
        }
    }

    @Test
    @Tag("timezones")
    public void timezonesBakedIn(TestInfo testInfo) throws IOException, InterruptedException {
        final Apps app = Apps.TIMEZONES;
        LOGGER.info("Testing app: " + app.toString());
        Process process = null;
        File processLog = null;
        final StringBuilder report = new StringBuilder();
        final File appDir = new File(BASE_DIR + File.separator + app.dir);
        final String cn = testInfo.getTestClass().get().getCanonicalName();
        final String mn = testInfo.getTestMethod().get().getName();
        try {
            // Cleanup
            cleanTarget(app);
            Files.createDirectories(Paths.get(appDir.getAbsolutePath() + File.separator + "logs"));

            // Build
            processLog = new File(appDir.getAbsolutePath() + File.separator + "logs" + File.separator + "build-and-run.log");

            builderRoutine(app, report, cn, mn, appDir, processLog);

            LOGGER.info("Running...");
            List<String> cmd = getRunCommand(app.buildAndRunCmds.cmds[app.buildAndRunCmds.cmds.length - 1]);
            process = runCommand(cmd, appDir, processLog, app);
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
            final Pattern p = Pattern.compile(".*d.Europe centrale.*");
            assertTrue(searchLogLines(p, processLog, Charset.defaultCharset()), "Expected pattern " + p.toString() + " was not found in the log. " +
                    "There might be a problem with timezones inclusion. See https://github.com/oracle/graal/issues/2776");

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
        LOGGER.info("Testing app: " + app.toString());
        Process process = null;
        File processLog = null;
        final StringBuilder report = new StringBuilder();
        final File appDir = new File(BASE_DIR + File.separator + app.dir);
        final String cn = testInfo.getTestClass().get().getCanonicalName();
        final String mn = testInfo.getTestMethod().get().getName();
        try {
            // Cleanup
            cleanTarget(app);
            Files.createDirectories(Paths.get(appDir.getAbsolutePath() + File.separator + "logs"));

            // Build
            processLog = new File(appDir.getAbsolutePath() + File.separator + "logs" + File.separator + "build-and-run.log");

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
        LOGGER.info("Testing app: " + app.toString());
        Process process = null;
        File processLog = null;
        final StringBuilder report = new StringBuilder();
        final File appDir = new File(BASE_DIR + File.separator + app.dir);
        final String cn = testInfo.getTestClass().get().getCanonicalName();
        final String mn = testInfo.getTestMethod().get().getName();
        try {
            // Cleanup
            cleanTarget(app);
            Files.createDirectories(Paths.get(appDir.getAbsolutePath() + File.separator + "logs"));

            // Build
            processLog = new File(appDir.getAbsolutePath() + File.separator + "logs" + File.separator + "build-and-run.log");

            // In this case, the two last commands are used for running the app; one in JVM mode and the other in Native mode.
            // We should somehow capture this semantically in an Enum or something. This is fragile...
            builderRoutine(app.buildAndRunCmds.cmds.length - 2, app, report, cn, mn, appDir, processLog);

            final File inputData = new File(BASE_DIR + File.separator + app.dir + File.separator + "target" + File.separator + "test_data.txt");

            LOGGER.info("Running JVM mode...");
            long start = System.currentTimeMillis();
            List<String> cmd = getRunCommand(app.buildAndRunCmds.cmds[app.buildAndRunCmds.cmds.length - 2]);
            process = runCommand(cmd, appDir, processLog, app, inputData);
            process.waitFor(30, TimeUnit.SECONDS);
            long jvmRunTookMs = System.currentTimeMillis() - start;
            Logs.appendln(report, appDir.getAbsolutePath());
            Logs.appendlnSection(report, String.join(" ", cmd));

            LOGGER.info("Running Native mode...");
            start = System.currentTimeMillis();
            cmd = getRunCommand(app.buildAndRunCmds.cmds[app.buildAndRunCmds.cmds.length - 1]);
            process = runCommand(cmd, appDir, processLog, app, inputData);
            process.waitFor(30, TimeUnit.SECONDS);
            long nativeRunTookMs = System.currentTimeMillis() - start;
            Logs.appendln(report, appDir.getAbsolutePath());
            Logs.appendlnSection(report, String.join(" ", cmd));

            // Test output and log measurements (time it took to run)

            int count = 0;
            // This magic hash is what the app is supposed to spit out. See ./apps/debug-symbols-smoke/src/main/java/debug_symbols_smoke/Main.java
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
            LogBuilder.Log logJVM = new LogBuilder()
                    .app(app.toString() + "_JVM")
                    .timeToFinishMs(jvmRunTookMs)
                    .build();
            LogBuilder.Log logNative = new LogBuilder()
                    .app(app.toString() + "_NATIVE")
                    .timeToFinishMs(nativeRunTookMs)
                    .build();
            Logs.logMeasurements(logJVM, measurementsLog);
            Logs.logMeasurements(logNative, measurementsLog);
            Logs.appendln(report, "Measurements:");
            Logs.appendln(report, logJVM.headerMarkdown + "\n" + logJVM.lineMarkdown);
            Logs.appendln(report, logNative.lineMarkdown);
            Logs.checkThreshold(app, "jvm", Logs.SKIP, Logs.SKIP, Logs.SKIP, jvmRunTookMs);
            Logs.checkThreshold(app, "native", Logs.SKIP, Logs.SKIP, Logs.SKIP, nativeRunTookMs);
        } finally {
            cleanup(process, cn, mn, report, app, processLog);
        }
    }

    @Test
    @Tag("debugSymbolsSmoke")
    @DisabledOnOs({OS.WINDOWS})
    public void debugSymbolsSmokeGDB(TestInfo testInfo) throws IOException, InterruptedException {
        final Apps app = Apps.DEBUG_SYMBOLS_SMOKE;
        LOGGER.info("Testing app: " + app.toString());
        File processLog = null;
        final StringBuilder report = new StringBuilder();
        final File appDir = new File(BASE_DIR + File.separator + app.dir);
        final String cn = testInfo.getTestClass().get().getCanonicalName();
        final String mn = testInfo.getTestMethod().get().getName();
        try {
            // Cleanup
            cleanTarget(app);
            Files.createDirectories(Paths.get(appDir.getAbsolutePath() + File.separator + "logs"));

            // Build
            processLog = new File(appDir.getAbsolutePath() + File.separator + "logs" + File.separator + "build-and-run.log");

            // In this case, the two last commands are used for running the app; one in JVM mode and the other in Native mode.
            // We should somehow capture this semantically in an Enum or something. This is fragile...
            builderRoutine(app.buildAndRunCmds.cmds.length - 2, app, report, cn, mn, appDir, processLog);

            final ProcessBuilder processBuilder = new ProcessBuilder(getRunCommand("gdb", "./target/debug-symbols-smoke"));
            final Map<String, String> envA = processBuilder.environment();
            envA.put("PATH", System.getenv("PATH"));
            processBuilder.directory(appDir)
                    .redirectErrorStream(true);
            final Process process = processBuilder.start();
            final ExecutorService esvc = Executors.newCachedThreadPool();
            final StringBuffer stringBuffer = new StringBuffer();
            final Runnable reader = () -> {
                try (BufferedReader r = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        stringBuffer.append(line);
                        stringBuffer.append('\n');
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            };
            esvc.submit(reader);

            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()))) {
                Logs.appendln(report, appDir.getAbsolutePath());
                Logs.appendlnSection(report, String.join(" ", processBuilder.command()));
                Logs.appendln(report, stringBuffer.toString());
                assertTrue(waitForBufferToMatch(stringBuffer,
                        Pattern.compile(".*Reading symbols from.*", Pattern.DOTALL),
                        3000, 500, TimeUnit.MILLISECONDS),
                        "GDB session did not start well. Check the names, paths... Content was: " + stringBuffer.toString());

                carryOutGDBSession(stringBuffer, GDBSession.DEBUG_SYMBOLS_SMOKE, esvc, writer, report, UsedVersion.getVersion(false));

                writer.write("q\n");
                writer.flush();
            }
            process.waitFor(1, TimeUnit.SECONDS);

            processStopper(process, false);
            Logs.checkLog(cn, mn, app, processLog);
        } finally {
            cleanup(null, cn, mn, report, app, processLog);
        }
    }

    @Test
    @Tag("debugSymbolsQuarkus")
    @DisabledOnOs({OS.WINDOWS})
    public void debugSymbolsQuarkus(TestInfo testInfo) throws IOException, InterruptedException {
        final Apps app = Apps.DEBUG_QUARKUS_FULL_MICROPROFILE;
        LOGGER.info("Testing app: " + app.toString());
        File processLog = null;
        final StringBuilder report = new StringBuilder();
        final File appDir = new File(BASE_DIR + File.separator + app.dir);
        final String cn = testInfo.getTestClass().get().getCanonicalName();
        final String mn = testInfo.getTestMethod().get().getName();
        try {
            // Cleanup
            cleanTarget(app);
            Files.createDirectories(Paths.get(appDir.getAbsolutePath() + File.separator + "logs"));

            // Patch for compatibility
            if (QUARKUS_VERSION.startsWith("1.")) {
                runCommand(getRunCommand("git", "apply", "quarkus_1.x.patch"),
                        Path.of(BASE_DIR, Apps.QUARKUS_FULL_MICROPROFILE.dir).toFile());
            }

            // Build
            processLog = new File(appDir.getAbsolutePath() + File.separator + "logs" + File.separator + "build-and-run.log");
            builderRoutine(app.buildAndRunCmds.cmds.length - 1, app, report, cn, mn, appDir, processLog);

            final ProcessBuilder processBuilder = new ProcessBuilder(getRunCommand("gdb", "./target/quarkus-runner"));
            final Map<String, String> envA = processBuilder.environment();
            envA.put("PATH", System.getenv("PATH"));
            processBuilder.directory(appDir)
                    .redirectErrorStream(true);
            final Process process = processBuilder.start();
            final ExecutorService esvc = Executors.newCachedThreadPool();
            final StringBuffer stringBuffer = new StringBuffer();
            final Runnable reader = () -> {
                try (BufferedReader r = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        stringBuffer.append(line);
                        stringBuffer.append('\n');
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            };
            esvc.submit(reader);

            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()))) {
                Logs.appendln(report, appDir.getAbsolutePath());
                Logs.appendlnSection(report, String.join(" ", processBuilder.command()));
                Logs.appendln(report, stringBuffer.toString());
                assertTrue(waitForBufferToMatch(stringBuffer,
                        Pattern.compile(".*Reading symbols from.*", Pattern.DOTALL),
                        3000, 500, TimeUnit.MILLISECONDS),
                        "GDB session did not start well. Check the names, paths... Content was: " + stringBuffer.toString());

                writer.write("set confirm off\n");
                writer.flush();

                writer.write("set directories "+appDir.getAbsolutePath() + "/target/sources\n");
                writer.flush();

                carryOutGDBSession(stringBuffer, GDBSession.DEBUG_QUARKUS_FULL_MICROPROFILE, esvc, writer, report, UsedVersion.getVersion(false));

                writer.write("q\n");
                writer.flush();
            }
            process.waitFor(1, TimeUnit.SECONDS);
            processStopper(process, true);
            Logs.checkLog(cn, mn, app, processLog);
        } finally {
            cleanup(null, cn, mn, report, app, processLog);
            if (QUARKUS_VERSION.startsWith("1.")) {
                runCommand(getRunCommand("git", "apply", "-R", "quarkus_1.x.patch"),
                        Path.of(BASE_DIR, Apps.QUARKUS_FULL_MICROPROFILE.dir).toFile());
            }
        }
    }

    @Test
    @Tag("debugSymbolsQuarkus")
    @Tag("builder-image")
    @DisabledOnOs({OS.WINDOWS})
    public void debugSymbolsQuarkusContainer(TestInfo testInfo) throws IOException, InterruptedException {
        final Apps app = Apps.DEBUG_QUARKUS_BUILDER_IMAGE_VERTX;
        LOGGER.info("Testing app: " + app.toString());
        File processLog = null;
        final StringBuilder report = new StringBuilder();
        final File appDir = new File(BASE_DIR + File.separator + app.dir);
        final String cn = testInfo.getTestClass().get().getCanonicalName();
        final String mn = testInfo.getTestMethod().get().getName();
        final Pattern dbReady = Pattern.compile(".*ready to accept connections.*");
        try {
            // Cleanup
            cleanTarget(app);
            stopAllRunningContainers();
            Files.createDirectories(Paths.get(appDir.getAbsolutePath() + File.separator + "logs"));

            // Build & Run
            processLog = new File(appDir.getAbsolutePath() + File.separator + "logs" + File.separator + "build-and-run.log");
            builderRoutine(app.buildAndRunCmds.cmds.length, app, report, cn, mn, appDir, processLog);

            waitForContainerLogToMatch("quarkus_test_db", dbReady, 20, 1, TimeUnit.SECONDS);

            // GDB process
            // Note that Q 2.x and Mandrel 21.1.x work with /work/application too, while
            // Q 1.11.7.Final and Mandrel 20.3.2 needs work/application.debug
            // Is Q 2.x baking debug symbols to the main executable too?
            final ProcessBuilder processBuilder = new ProcessBuilder(getRunCommand(
                    CONTAINER_RUNTIME, "exec", "-i", ContainerNames.QUARKUS_BUILDER_IMAGE_ENCODING.name, "/usr/bin/gdb", "/work/application.debug", "1"))
                    .directory(appDir)
                    .redirectErrorStream(true);
            final Map<String, String> envA = processBuilder.environment();
            envA.put("PATH", System.getenv("PATH"));
            final Process gdbProcess = processBuilder.start();
            final ExecutorService esvc = Executors.newCachedThreadPool();
            final StringBuffer stringBuffer = new StringBuffer();
            final Runnable reader = () -> {
                try (BufferedReader r = new BufferedReader(new InputStreamReader(gdbProcess.getInputStream()))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        stringBuffer.append(line);
                        stringBuffer.append('\n');
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            };
            esvc.submit(reader);

            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(gdbProcess.getOutputStream()))) {
                Logs.appendln(report, appDir.getAbsolutePath());
                Logs.appendlnSection(report, String.join(" ", processBuilder.command()));
                Logs.appendln(report, stringBuffer.toString());
                assertTrue(waitForBufferToMatch(stringBuffer,
                        Pattern.compile(".*Reading symbols from.*", Pattern.DOTALL),
                        3000, 500, TimeUnit.MILLISECONDS),
                        "GDB session did not start well. Check the names, paths... Content was: " + stringBuffer.toString());

                writer.write("set confirm off\n");
                writer.flush();

                writer.write("set directories /work/sources\n");
                writer.flush();
                carryOutGDBSession(stringBuffer, GDBSession.DEBUG_QUARKUS_BUILDER_IMAGE_VERTX, esvc, writer, report, UsedVersion.getVersion(true));
                writer.write("q\n");
                writer.flush();
            }

            gdbProcess.waitFor(1, TimeUnit.SECONDS);

            final Process process = runCommand(
                    getRunCommand(CONTAINER_RUNTIME, "logs", app.runtimeContainer.name),
                    appDir, processLog, app);
            process.waitFor(5, TimeUnit.SECONDS);

            processStopper(gdbProcess, true);
            stopRunningContainers(app.runtimeContainer.name, "quarkus_test_db");
            Logs.checkLog(cn, mn, app, processLog);
        } finally {
            cleanup(null, cn, mn, report, app, processLog);
            stopAllRunningContainers();
            removeContainers(app.runtimeContainer.name, "quarkus_test_db");
        }
    }

    public static void carryOutGDBSession(StringBuffer stringBuffer, GDBSession gdbSession, ExecutorService esvc,
                                          BufferedWriter writer, StringBuilder report, Version mandrelVersion) {
        final ConcurrentLinkedQueue<String> errorQueue = new ConcurrentLinkedQueue<>();
        Stream.of(gdbSession.get(mandrelVersion)).forEach(cp -> {
                    stringBuffer.delete(0, stringBuffer.length());
                    try {
                        if (cp.c.startsWith("GOTO URL")) {
                            final Runnable webRequest = () -> {
                                try {
                                    final String url = cp.c.split("URL ")[1];
                                    final String content = WebpageTester.getUrlContents(url);
                                    if (!cp.p.matcher(content).matches()) {
                                        errorQueue.add("Content of URL " + url + " should have matched regexp " + cp.p.pattern() + " but it was this: " + content);
                                    }
                                } catch (IOException e) {
                                    e.printStackTrace();
                                    fail("Unexpected failure: ", e);
                                }
                            };
                            esvc.submit(webRequest);
                            // Well, there is a possible race condition where the request didn't hit the app yet...
                        } else {
                            writer.write(cp.c);
                            writer.flush();
                            boolean m = waitForBufferToMatch(stringBuffer, cp.p, 10, 1, TimeUnit.SECONDS);
                            Logs.appendlnSection(report, cp.c);
                            Logs.appendln(report, stringBuffer.toString());
                            if (!m) {
                                errorQueue.add("Command '" + cp.c.trim() + "' did not match the expected pattern '" +
                                        cp.p.pattern() + "'. Output was: " + stringBuffer.toString());
                            }
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                        fail("Unexpected failure: ", e);
                    }
                }
        );
        assertTrue(errorQueue.isEmpty(), "There were errors in the GDB session. " +
                "Note that commands in the session might depend on each other. Errors: " +
                System.lineSeparator() + String.join(", " + System.lineSeparator(), errorQueue));
    }

    public static void cleanup(Process process, String cn, String mn, StringBuilder report, Apps app, File... log)
            throws InterruptedException, IOException {
        // Make sure processes are down even if there was an exception / failure
        if (process != null) {
            processStopper(process, true);
        }
        // Archive logs no matter what
        for (File f : log) {
            Logs.archiveLog(cn, mn, f);
        }
        Logs.writeReport(cn, mn, report.toString());
        cleanTarget(app);
    }

}
