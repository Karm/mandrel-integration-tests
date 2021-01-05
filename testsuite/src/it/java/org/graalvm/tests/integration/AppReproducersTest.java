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
import org.graalvm.tests.integration.utils.Apps;
import org.graalvm.tests.integration.utils.Commands;
import org.graalvm.tests.integration.utils.ContainerNames;
import org.graalvm.tests.integration.utils.GDBSession;
import org.graalvm.tests.integration.utils.LogBuilder;
import org.graalvm.tests.integration.utils.Logs;
import org.graalvm.tests.integration.utils.WebpageTester;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Date;
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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.graalvm.tests.integration.utils.Commands.CONTAINER_RUNTIME;
import static org.graalvm.tests.integration.utils.Commands.waitForContainerLogToMatch;
import static org.junit.jupiter.api.Assertions.assertEquals;
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

    public static final String BASE_DIR = Commands.getBaseDir();

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
            Commands.cleanTarget(app);
            Files.createDirectories(Paths.get(appDir.getAbsolutePath() + File.separator + "logs"));

            // Build
            processLog = new File(appDir.getAbsolutePath() + File.separator + "logs" + File.separator + "build-and-run.log");

            builderRoutine(app, report, cn, mn, appDir, processLog);

            LOGGER.info("Running...#1");
            List<String> cmd = Commands.getRunCommand(app.buildAndRunCmds.cmds[app.buildAndRunCmds.cmds.length - 1]);
            process = Commands.runCommand(cmd, appDir, processLog, app);
            process.waitFor(5, TimeUnit.SECONDS);
            Logs.appendln(report, appDir.getAbsolutePath());
            Logs.appendlnSection(report, String.join(" ", cmd));

            LOGGER.info("Running...#2");
            cmd = Commands.getRunCommand(app.buildAndRunCmds.cmds[app.buildAndRunCmds.cmds.length - 1]);
            process = Commands.runCommand(cmd, appDir, processLog, app);
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

            Commands.processStopper(process, false);
            Logs.checkLog(cn, mn, app, processLog);
        } finally {
            cleanup(process, cn, mn, processLog, report, app);
        }
    }

    @Test
    @Tag("imageio")
    public void imageioAWTTest(TestInfo testInfo) throws IOException, InterruptedException {
        final Apps app = Apps.IMAGEIO;
        LOGGER.info("Testing app: " + app.toString());

        final Map<String, String> controlData = Map.ofEntries(
                Map.entry("mytest.bmp", "ec29663c7be2fbebd39be4fcea1ca18e81c2e0f31d92f73b3907c47c9f59aa1e"),
                Map.entry("mytest.gif", "2e8b712b9e3fe240b9fd1d9ccedd622a63344f94998b9cca6d86baad7bac8e81"),
                Map.entry("mytest.jpg", "c427e9377c1ad6e787a5c3bb0096eeca68d4c917254f541aefa92e027c2263e8"),
                Map.entry("mytest.png", "68638780950b392fb5f4fbd93d900d14446c55a91c52afb2adc0cbbeb84508f8"),
                Map.entry("mytest.svg", "b802d1d7609bb8503e14c7d59e13e554470f892ea3db266b3aaa9fff49aacb77"),
                Map.entry("mytest.tiff", "7b879c98b55e70a414db6796b76ddcd9c353abb9998bfb2f6b3aa2c1fa6bc81a"),
                Map.entry("mytest_toC.png", "3d823e4b879c4a094da95d8eccf5ec5c6a413ec64696502d52d6c68b496d1a91"),
                Map.entry("mytest_toG.png", "0bd36a1d079a805792ec69a54061e0234042daa199d1a3adcc4e7f95125cc9c1"),
                Map.entry("mytest_toL.png", "1190ea971d2ac879988a43007219dd31bd0e26852e5cd3b04d5d88d768de7d07"),
                Map.entry("mytest_toP.png", "17db14e726ec590c8834dfb68320d05b4558dac82602df2fa54e04bf95e2f708"),
                Map.entry("mytest_toS.png", "4ce0872610f9865d39bfcfc237230a1e3c3a1903ad93b9d4f2522955ed4769dc"),
                Map.entry("mytest.wbmp", "d5a9cae953a5d6436def014115254c4d7268f042a97069c07942e1ef9193998b")
        );

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
            Commands.cleanTarget(app);
            if (metaINF.exists()) {
                FileUtils.cleanDirectory(metaINF);
            }
            Files.createDirectories(Paths.get(appDir.getAbsolutePath() + File.separator + "logs"));

            // Build
            processLog = new File(appDir.getAbsolutePath() + File.separator + "logs" + File.separator + "build-and-run.log");

            builderRoutine(app, report, cn, mn, appDir, processLog);

            // Remove files generated by java run to prevent false negatives
            controlData.keySet().forEach(f -> new File(appDir, f).delete());

            LOGGER.info("Running...");
            final List<String> cmd = Commands.getRunCommand(app.buildAndRunCmds.cmds[app.buildAndRunCmds.cmds.length - 1]);
            process = Commands.runCommand(cmd, appDir, processLog, app);
            process.waitFor(15, TimeUnit.SECONDS);
            Logs.appendln(report, appDir.getAbsolutePath());
            Logs.appendlnSection(report, String.join(" ", cmd));

            // Test output
            final List<String> errors = new ArrayList<>(12);
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

            Commands.processStopper(process, false);
            Logs.checkLog(cn, mn, app, processLog);
        } finally {
            cleanup(process, cn, mn, processLog, report, app);
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
            Commands.cleanTarget(app);
            Files.createDirectories(Paths.get(appDir.getAbsolutePath() + File.separator + "logs"));

            // Build
            processLog = new File(appDir.getAbsolutePath() + File.separator + "logs" + File.separator + "build-and-run.log");

            builderRoutine(app, report, cn, mn, appDir, processLog);

            LOGGER.info("Running...");
            List<String> cmd = Commands.getRunCommand(app.buildAndRunCmds.cmds[app.buildAndRunCmds.cmds.length - 1]);
            process = Commands.runCommand(cmd, appDir, processLog, app);
            process.waitFor(5, TimeUnit.SECONDS);
            Logs.appendln(report, appDir.getAbsolutePath());
            Logs.appendlnSection(report, String.join(" ", cmd));

            final Pattern p = Pattern.compile(".*d’Europe centrale.*");
            boolean found = false;
            try (Scanner sc = new Scanner(processLog, UTF_8)) {
                while (sc.hasNextLine()) {
                    final Matcher m = p.matcher(sc.nextLine());
                    if (m.matches()) {
                        found = true;
                        break;
                    }
                }
            }

            assertTrue(found, "`d’Europe centrale' string was expected. " +
                    "There might be a problem with timezones inclusion. See https://github.com/oracle/graal/issues/2776");

            Commands.processStopper(process, false);
            Logs.checkLog(cn, mn, app, processLog);
        } finally {
            cleanup(process, cn, mn, processLog, report, app);
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
            Commands.cleanTarget(app);
            Files.createDirectories(Paths.get(appDir.getAbsolutePath() + File.separator + "logs"));

            // Build
            processLog = new File(appDir.getAbsolutePath() + File.separator + "logs" + File.separator + "build-and-run.log");

            // In this case, the two last commands are used for running the app; one in JVM mode and the other in Native mode.
            // We should somehow capture this semantically in an Enum or something. This is fragile...
            builderRoutine(app.buildAndRunCmds.cmds.length - 2, app, report, cn, mn, appDir, processLog);

            final File inputData = new File(BASE_DIR + File.separator + app.dir + File.separator + "target" + File.separator + "test_data.txt");

            LOGGER.info("Running JVM mode...");
            long start = System.currentTimeMillis();
            List<String> cmd = Commands.getRunCommand(app.buildAndRunCmds.cmds[app.buildAndRunCmds.cmds.length - 2]);
            process = Commands.runCommand(cmd, appDir, processLog, app, inputData);
            process.waitFor(30, TimeUnit.SECONDS);
            long jvmRunTookMs = System.currentTimeMillis() - start;
            Logs.appendln(report, appDir.getAbsolutePath());
            Logs.appendlnSection(report, String.join(" ", cmd));

            LOGGER.info("Running Native mode...");
            start = System.currentTimeMillis();
            cmd = Commands.getRunCommand(app.buildAndRunCmds.cmds[app.buildAndRunCmds.cmds.length - 1]);
            process = Commands.runCommand(cmd, appDir, processLog, app, inputData);
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

            Commands.processStopper(process, false);
            Logs.checkLog(cn, mn, app, processLog);
            final Path measurementsLog = Paths.get(Logs.getLogsDir(cn, mn).toString(), "measurements.csv");
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
            Logs.checkThreshold(app, "jvm", Logs.SKIP, Logs.SKIP, jvmRunTookMs);
            Logs.checkThreshold(app, "native", Logs.SKIP, Logs.SKIP, nativeRunTookMs);
        } finally {
            cleanup(process, cn, mn, processLog, report, app);
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
            Commands.cleanTarget(app);
            Files.createDirectories(Paths.get(appDir.getAbsolutePath() + File.separator + "logs"));

            // Build
            processLog = new File(appDir.getAbsolutePath() + File.separator + "logs" + File.separator + "build-and-run.log");

            // In this case, the two last commands are used for running the app; one in JVM mode and the other in Native mode.
            // We should somehow capture this semantically in an Enum or something. This is fragile...
            builderRoutine(app.buildAndRunCmds.cmds.length - 2, app, report, cn, mn, appDir, processLog);

            final ProcessBuilder processBuilder = new ProcessBuilder("gdb", "./target/debug-symbols-smoke");
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
                assertTrue(waitForBufferToMatch(stringBuffer,
                        Pattern.compile(".*Reading symbols from \\./target/debug-symbols-smoke\\.\\.\\.done\\..*", Pattern.DOTALL),
                        3000, 500, TimeUnit.MILLISECONDS),
                        "GDB session did not start well. Check the names, paths... Content was: " + stringBuffer.toString());
                Logs.appendln(report, appDir.getAbsolutePath());
                Logs.appendlnSection(report, String.join(" ", processBuilder.command()));
                Logs.appendln(report, stringBuffer.toString());

                carryOutGDBSession(stringBuffer, GDBSession.DEBUG_SYMBOLS_SMOKE, esvc, writer, report);

                writer.write("q\n");
                writer.flush();
            }
            process.waitFor(1, TimeUnit.SECONDS);

            Commands.processStopper(process, false);
            Logs.checkLog(cn, mn, app, processLog);
        } finally {
            cleanup(null, cn, mn, processLog, report, app);
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
            Commands.cleanTarget(app);
            Files.createDirectories(Paths.get(appDir.getAbsolutePath() + File.separator + "logs"));

            // Build
            processLog = new File(appDir.getAbsolutePath() + File.separator + "logs" + File.separator + "build-and-run.log");
            builderRoutine(app.buildAndRunCmds.cmds.length - 1, app, report, cn, mn, appDir, processLog);

            final ProcessBuilder processBuilder = new ProcessBuilder("gdb", "./target/quarkus-runner");
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
                assertTrue(waitForBufferToMatch(stringBuffer,
                        Pattern.compile(".*Reading symbols from.*quarkus-runner.debug.*done.*", Pattern.DOTALL),
                        3000, 500, TimeUnit.MILLISECONDS),
                        "GDB session did not start well. Check the names, paths... Content was: " + stringBuffer.toString());
                Logs.appendln(report, appDir.getAbsolutePath());
                Logs.appendlnSection(report, String.join(" ", processBuilder.command()));
                Logs.appendln(report, stringBuffer.toString());

                writer.write("set confirm off\n");
                writer.flush();

                writer.write("set directories " +
                        appDir.getAbsolutePath() + "/target/sources/:" +
                        appDir.getAbsolutePath() + "/target/sources/src/\n");
                writer.flush();

                carryOutGDBSession(stringBuffer, GDBSession.DEBUG_QUARKUS_FULL_MICROPROFILE, esvc, writer, report);

                writer.write("q\n");
                writer.flush();
            }
            process.waitFor(1, TimeUnit.SECONDS);
            Commands.processStopper(process, true);
            Logs.checkLog(cn, mn, app, processLog);
        } finally {
            cleanup(null, cn, mn, processLog, report, app);
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
            Commands.cleanTarget(app);
            Commands.stopAllRunningContainers();
            Files.createDirectories(Paths.get(appDir.getAbsolutePath() + File.separator + "logs"));

            // Build & Run
            processLog = new File(appDir.getAbsolutePath() + File.separator + "logs" + File.separator + "build-and-run.log");
            builderRoutine(app.buildAndRunCmds.cmds.length, app, report, cn, mn, appDir, processLog);

            waitForContainerLogToMatch("quarkus_test_db", dbReady, 20, 1, TimeUnit.SECONDS);

            // GDB process
            final ProcessBuilder processBuilder = new ProcessBuilder(
                    CONTAINER_RUNTIME, "exec", "-i", ContainerNames.QUARKUS_BUILDER_IMAGE_ENCODING.name, "/usr/bin/gdb", "/work/application", "1")
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
                assertTrue(waitForBufferToMatch(stringBuffer,
                        Pattern.compile(".*Reading symbols from.*/work/quarkus-runner.debug.*done.*", Pattern.DOTALL),
                        3000, 500, TimeUnit.MILLISECONDS),
                        "GDB session did not start well. Check the names, paths... Content was: " + stringBuffer.toString());
                Logs.appendln(report, appDir.getAbsolutePath());
                Logs.appendlnSection(report, String.join(" ", processBuilder.command()));
                Logs.appendln(report, stringBuffer.toString());

                writer.write("set confirm off\n");
                writer.flush();

                writer.write("set directories /work/sources:/work/sources/src\n");
                writer.flush();
                carryOutGDBSession(stringBuffer, GDBSession.DEBUG_QUARKUS_BUILDER_IMAGE_VERTX, esvc, writer, report);
                writer.write("q\n");
                writer.flush();
            }

            gdbProcess.waitFor(1, TimeUnit.SECONDS);

            final Process process = Commands.runCommand(
                    Commands.getRunCommand(new String[]{CONTAINER_RUNTIME, "logs", app.runtimeContainer.name}),
                    appDir, processLog, app);
            process.waitFor(5, TimeUnit.SECONDS);

            Commands.processStopper(gdbProcess, true);
            Commands.stopRunningContainer(app.runtimeContainer.name);
            Commands.stopRunningContainer("quarkus_test_db");
            Logs.checkLog(cn, mn, app, processLog);
        } finally {
            cleanup(null, cn, mn, processLog, report, app);
            Commands.stopAllRunningContainers();
        }
    }

    public static void carryOutGDBSession(StringBuffer stringBuffer, GDBSession gdbSession, ExecutorService esvc, BufferedWriter writer, StringBuilder report) {
        final ConcurrentLinkedQueue<String> errorQueue = new ConcurrentLinkedQueue<>();
        Stream.of(gdbSession.gdbOutput).forEach(cp -> {
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

    public static void builderRoutine(int steps, Apps app, StringBuilder report, String cn, String mn, File appDir, File processLog) throws InterruptedException {
        // The last command is reserved for running it
        assertTrue(app.buildAndRunCmds.cmds.length > 1);
        Logs.appendln(report, "# " + cn + ", " + mn);
        for (int i = 0; i < steps; i++) {
            // We cannot run commands in parallel, we need them to follow one after another
            final ExecutorService buildService = Executors.newFixedThreadPool(1);
            final List<String> cmd = Commands.getRunCommand(app.buildAndRunCmds.cmds[i]);
            buildService.submit(new Commands.ProcessRunner(appDir, processLog, cmd, 10)); // might take a long time....
            Logs.appendln(report, (new Date()).toString());
            Logs.appendln(report, appDir.getAbsolutePath());
            Logs.appendlnSection(report, String.join(" ", cmd));
            buildService.shutdown();
            buildService.awaitTermination(10, TimeUnit.MINUTES); // Native image build might take a long time....
        }
        assertTrue(processLog.exists());
    }

    public static void builderRoutine(Apps app, StringBuilder report, String cn, String mn, File appDir, File processLog) throws InterruptedException {
        builderRoutine(app.buildAndRunCmds.cmds.length - 1, app, report, cn, mn, appDir, processLog);
    }

    public static void cleanup(Process process, String cn, String mn, File processLog, StringBuilder report, Apps app)
            throws InterruptedException, IOException {
        // Make sure processes are down even if there was an exception / failure
        if (process != null) {
            Commands.processStopper(process, true);
        }
        // Archive logs no matter what
        Logs.archiveLog(cn, mn, processLog);
        Logs.writeReport(cn, mn, report.toString());
        Commands.cleanTarget(app);
    }

    public static boolean waitForBufferToMatch(StringBuffer stringBuffer, Pattern pattern, long timeout, long sleep, TimeUnit unit) {
        long timeoutMillis = unit.toMillis(timeout);
        long sleepMillis = unit.toMillis(sleep);
        long startMillis = System.currentTimeMillis();
        while (System.currentTimeMillis() - startMillis < timeoutMillis) {
            if (pattern.matcher(stringBuffer.toString()).matches()) {
                return true;
            }
            try {
                Thread.sleep(sleepMillis);
            } catch (InterruptedException e) {
                e.printStackTrace();
                Thread.currentThread().interrupt();
            }
        }
        return false;
    }
}
