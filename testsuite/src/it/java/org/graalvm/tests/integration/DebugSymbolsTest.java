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

import org.graalvm.home.Version;
import org.graalvm.tests.integration.utils.Apps;
import org.graalvm.tests.integration.utils.ContainerNames;
import org.graalvm.tests.integration.utils.GDBSession;
import org.graalvm.tests.integration.utils.Logs;
import org.graalvm.tests.integration.utils.WebpageTester;
import org.graalvm.tests.integration.utils.versions.QuarkusVersion;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.graalvm.tests.integration.DebugSymbolsTest.DebugOptions.DebugCodeInfoUseSourceMappings_23_0;
import static org.graalvm.tests.integration.DebugSymbolsTest.DebugOptions.OmitInlinedMethodDebugLineInfo_23_0;
import static org.graalvm.tests.integration.DebugSymbolsTest.DebugOptions.TrackNodeSourcePosition_23_0;
import static org.graalvm.tests.integration.utils.Commands.CMD_DEFAULT_TIMEOUT_MS;
import static org.graalvm.tests.integration.utils.Commands.CMD_LONG_TIMEOUT_MS;
import static org.graalvm.tests.integration.utils.Commands.CONTAINER_RUNTIME;
import static org.graalvm.tests.integration.utils.Commands.GOTO_URL_TIMEOUT_MS;
import static org.graalvm.tests.integration.utils.Commands.LONG_GOTO_URL_TIMEOUT_MS;
import static org.graalvm.tests.integration.utils.Commands.QUARKUS_VERSION;
import static org.graalvm.tests.integration.utils.Commands.builderRoutine;
import static org.graalvm.tests.integration.utils.Commands.cleanTarget;
import static org.graalvm.tests.integration.utils.Commands.cleanup;
import static org.graalvm.tests.integration.utils.Commands.getBaseDir;
import static org.graalvm.tests.integration.utils.Commands.getRunCommand;
import static org.graalvm.tests.integration.utils.Commands.processStopper;
import static org.graalvm.tests.integration.utils.Commands.removeContainers;
import static org.graalvm.tests.integration.utils.Commands.runCommand;
import static org.graalvm.tests.integration.utils.Commands.stopAllRunningContainers;
import static org.graalvm.tests.integration.utils.Commands.stopRunningContainers;
import static org.graalvm.tests.integration.utils.Commands.waitForBufferToMatch;
import static org.graalvm.tests.integration.utils.Commands.waitForContainerLogToMatch;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tests for build and start of applications with some real source code.
 * Focused on debug symbols.
 *
 * @author Michal Karm Babacek <karm@redhat.com>
 */
@Tag("reproducers")
public class DebugSymbolsTest {

    private static final Logger LOGGER = Logger.getLogger(DebugSymbolsTest.class.getName());

    public static final String BASE_DIR = getBaseDir();

    // GOTO i.e. accessing a URL of a debugged test app to trigger a certain code path
    private static final long GOTO_URL_SLEEP_MS = 50;

    public enum DebugOptions {
        TrackNodeSourcePosition_23_0("<DEBUG_FLAGS_23_0_a>", "-H:+TrackNodeSourcePosition"),
        DebugCodeInfoUseSourceMappings_23_0("<DEBUG_FLAGS_23_0_b>", "-H:+DebugCodeInfoUseSourceMappings"),
        OmitInlinedMethodDebugLineInfo_23_0("<DEBUG_FLAGS_23_0_c>", "-H:+OmitInlinedMethodDebugLineInfo");

        public final String token;
        final String replacement;

        DebugOptions(String token, String replacement) {
            this.token = token;
            this.replacement = replacement;
        }
    }

    @Test
    @Tag("debugSymbolsSmoke")
    @DisabledOnOs({OS.WINDOWS})
    public void debugSymbolsSmokeGDB(TestInfo testInfo) throws IOException, InterruptedException {
        final Apps app = Apps.DEBUG_SYMBOLS_SMOKE;
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

            Map<String, String> switches;
            Version version = UsedVersion.getVersion(app.runtimeContainer != ContainerNames.NONE);
            if (version.compareTo(Version.create(23, 0, 0)) >= 0) {
                switches = Map.of(
                        TrackNodeSourcePosition_23_0.token, TrackNodeSourcePosition_23_0.replacement,
                        DebugCodeInfoUseSourceMappings_23_0.token, DebugCodeInfoUseSourceMappings_23_0.replacement,
                        OmitInlinedMethodDebugLineInfo_23_0.token, OmitInlinedMethodDebugLineInfo_23_0.replacement);
            } else {
                switches = Map.of(
                        TrackNodeSourcePosition_23_0.token, "",
                        DebugCodeInfoUseSourceMappings_23_0.token, "",
                        OmitInlinedMethodDebugLineInfo_23_0.token, "");
            }
            // In this case, the two last commands are used for running the app; one in JVM mode and the other in Native mode.
            // We should somehow capture this semantically in an Enum or something. This is fragile...
            builderRoutine(app.buildAndRunCmds.cmds.length - 2, app, report, cn, mn, appDir, processLog, null, switches);

            assertTrue(Files.exists(Path.of(appDir.getAbsolutePath(), "target", "debug-symbols-smoke")),
                    "debug-symbols-smoke executable does not exist. Compilation failed. Check the logs.");

            final ProcessBuilder processBuilder = new ProcessBuilder(getRunCommand("gdb", "--interpreter=mi", "./target/debug-symbols-smoke"));
            final Map<String, String> envA = processBuilder.environment();
            envA.put("PATH", System.getenv("PATH"));
            processBuilder.directory(appDir)
                    .redirectErrorStream(true);
            final Process process = processBuilder.start();
            assertNotNull(process, "GDB process failed to start.");
            final ExecutorService esvc = Executors.newCachedThreadPool();
            final StringBuffer stringBuffer = new StringBuffer();
            final Runnable reader = () -> {
                try (BufferedReader r = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        stringBuffer.append(filterAndUnescapeGDBMIOutput(line));
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            };
            esvc.submit(reader);

            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()))) {
                Logs.appendlnSection(report, appDir.getAbsolutePath());
                Logs.appendln(report, String.join(" ", processBuilder.command()));
                final long increasedTimeoutMs = (UsedVersion.getVersion(false)
                        .compareTo(Version.create(23, 0, 0)) >= 0) ? CMD_LONG_TIMEOUT_MS : CMD_DEFAULT_TIMEOUT_MS;
                boolean result = waitForBufferToMatch(report, stringBuffer,
                        Pattern.compile(".*Reading symbols from.*", Pattern.DOTALL),
                        increasedTimeoutMs, 50, TimeUnit.MILLISECONDS); // Time unit is the same for timeout and sleep.
                Logs.appendlnSection(report, stringBuffer.toString());
                assertTrue(result,
                        "GDB session did not start well. Check the names, paths... Content was: " + stringBuffer);

                carryOutGDBSession(stringBuffer, GDBSession.DEBUG_SYMBOLS_SMOKE, esvc, writer, report, false);

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

            // Patch for compatibility
            if (QUARKUS_VERSION.majorIs(3) || QUARKUS_VERSION.isSnapshot()) {
                runCommand(getRunCommand("git", "apply", "quarkus_3.x.patch"),
                        Path.of(BASE_DIR, app.dir).toFile());
            }

            // Build
            processLog = Path.of(appDir.getAbsolutePath(), "logs", "build-and-run.log").toFile();
            builderRoutine(app.buildAndRunCmds.cmds.length - 1, app, report, cn, mn, appDir, processLog);

            assertTrue(Files.exists(Path.of(appDir.getAbsolutePath(), "target", "quarkus-runner")),
                    "Quarkus executable does not exist. Compilation failed. Check the logs.");

            final ProcessBuilder processBuilder = new ProcessBuilder(getRunCommand("gdb", "--interpreter=mi", "./target/quarkus-runner"));
            final Map<String, String> envA = processBuilder.environment();
            envA.put("PATH", System.getenv("PATH"));
            processBuilder.directory(appDir)
                    .redirectErrorStream(true);
            final Process process = processBuilder.start();
            assertNotNull(process, "GDB process failed to start.");
            final ExecutorService esvc = Executors.newCachedThreadPool();
            final StringBuffer stringBuffer = new StringBuffer();
            final Runnable reader = () -> {
                try (BufferedReader r = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        stringBuffer.append(filterAndUnescapeGDBMIOutput(line));
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            };
            esvc.submit(reader);

            Logs.appendlnSection(report, appDir.getAbsolutePath());
            Logs.appendln(report, String.join(" ", processBuilder.command()));
            final long increasedTimeoutMs = (UsedVersion.getVersion(false)
                    .compareTo(Version.create(23, 0, 0)) >= 0) ? CMD_LONG_TIMEOUT_MS : CMD_DEFAULT_TIMEOUT_MS;
            boolean result = waitForBufferToMatch(report, stringBuffer,
                    Pattern.compile(".*Reading symbols from.*", Pattern.DOTALL),
                    increasedTimeoutMs, 50, TimeUnit.MILLISECONDS); // Time unit is the same for timeout and sleep.
            Logs.appendlnSection(report, stringBuffer.toString());
            assertTrue(result,
                    "GDB session did not start well. Check the names, paths... Content was: " + stringBuffer);

            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()))) {
                writer.write("set confirm off\n");
                writer.flush();

                if (applySourcesPatch()) {
                    writer.write("set directories " + appDir.getAbsolutePath() + "/target/quarkus-native-image-source-jar/sources\n");
                } else {
                    writer.write("set directories " + appDir.getAbsolutePath() + "/target/sources\n");
                }

                writer.flush();

                carryOutGDBSession(stringBuffer, GDBSession.DEBUG_QUARKUS_FULL_MICROPROFILE, esvc, writer, report, false);

                writer.write("q\n");
                writer.flush();
            }
            process.waitFor(1, TimeUnit.SECONDS);
            processStopper(process, true);
            Logs.checkLog(cn, mn, app, processLog);
        } finally {
            cleanup(null, cn, mn, report, app, processLog);
            if (QUARKUS_VERSION.majorIs(3) || QUARKUS_VERSION.isSnapshot()) {
                runCommand(getRunCommand("git", "apply", "-R", "quarkus_3.x.patch"),
                        Path.of(BASE_DIR, app.dir).toFile());
            }
        }
    }

    // See https://github.com/quarkusio/quarkus/pull/20355
    private boolean applySourcesPatch() {
        QuarkusVersion v224 = new QuarkusVersion("2.2.4");
        QuarkusVersion v230 = new QuarkusVersion("2.3.0");
        QuarkusVersion v240 = new QuarkusVersion("2.4.0");
        return (QUARKUS_VERSION.compareTo(v224) >= 0 && QUARKUS_VERSION.compareTo(v230) < 0) ||
                QUARKUS_VERSION.compareTo(v240) >= 0 || QUARKUS_VERSION.isSnapshot();
    }

    @Test
    @Tag("debugSymbolsQuarkus")
    @Tag("builder-image")
    @DisabledOnOs({OS.WINDOWS})
    public void debugSymbolsQuarkusContainer(TestInfo testInfo) throws IOException, InterruptedException {
        final Apps app = Apps.DEBUG_QUARKUS_BUILDER_IMAGE_VERTX;
        LOGGER.info("Testing app: " + app);
        File processLog = null;
        final StringBuilder report = new StringBuilder();
        final File appDir = Path.of(BASE_DIR, app.dir).toFile();
        final String cn = testInfo.getTestClass().get().getCanonicalName();
        final String mn = testInfo.getTestMethod().get().getName();
        final Pattern dbReady = Pattern.compile(".*ready to accept connections.*");
        try {
            // Cleanup
            cleanTarget(app);
            stopAllRunningContainers();
            Files.createDirectories(Paths.get(appDir.getAbsolutePath() + File.separator + "logs"));

            if (applySourcesPatch()) {
                runCommand(getRunCommand("git", "apply", "quarkus_sources.patch"), appDir);
            }
            if (QUARKUS_VERSION.majorIs(3) || QUARKUS_VERSION.isSnapshot()) {
                runCommand(getRunCommand("git", "apply", "quarkus_3.x.patch"), appDir);
            }

            // Build & Run
            processLog = Path.of(appDir.getAbsolutePath(), "logs", "build-and-run.log").toFile();
            builderRoutine(app.buildAndRunCmds.cmds.length, app, report, cn, mn, appDir, processLog);

            waitForContainerLogToMatch("quarkus_test_db", dbReady, 20, 1, TimeUnit.SECONDS);

            // Check the log now to make sure there are no install failures
            // before gdb session starts.
            Logs.checkLog(cn, mn, app, processLog);

            // GDB process
            // Note that Q 2.x and Mandrel 21.1.x work with /work/application too, while
            // Q 1.11.7.Final and Mandrel 20.3.2 needs work/application.debug
            // Is Q 2.x baking debug symbols to the main executable too?
            final ProcessBuilder processBuilder = new ProcessBuilder(getRunCommand(
                    CONTAINER_RUNTIME, "exec", "-i", ContainerNames.QUARKUS_BUILDER_IMAGE_ENCODING.name, "/usr/bin/gdb", "--interpreter=mi", "/work/application.debug", "1"))
                    .directory(appDir)
                    .redirectErrorStream(true);
            final Map<String, String> envA = processBuilder.environment();
            envA.put("PATH", System.getenv("PATH"));
            final Process gdbProcess = processBuilder.start();
            assertNotNull(gdbProcess, "GDB process in container failed to start.");
            final ExecutorService esvc = Executors.newCachedThreadPool();
            final StringBuffer stringBuffer = new StringBuffer();
            final Runnable reader = () -> {
                try (BufferedReader r = new BufferedReader(new InputStreamReader(gdbProcess.getInputStream()))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        stringBuffer.append(filterAndUnescapeGDBMIOutput(line));
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            };
            esvc.submit(reader);

            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(gdbProcess.getOutputStream()))) {
                Logs.appendlnSection(report, appDir.getAbsolutePath());
                Logs.appendln(report, String.join(" ", processBuilder.command()));
                final long increasedTimeoutMs = (UsedVersion.getVersion(true)
                        .compareTo(Version.create(23, 0, 0)) >= 0) ? CMD_LONG_TIMEOUT_MS : CMD_DEFAULT_TIMEOUT_MS;
                boolean result = waitForBufferToMatch(report, stringBuffer,
                        Pattern.compile(".*Reading symbols from.*", Pattern.DOTALL),
                        increasedTimeoutMs, 50, TimeUnit.MILLISECONDS); // Time unit is the same for timeout and sleep.
                Logs.appendlnSection(report, stringBuffer.toString());
                assertTrue(result,
                        "GDB session did not start well. Check the names, paths... Content was: " + stringBuffer);

                writer.write("set confirm off\n");
                writer.flush();

                writer.write("set directories /work/sources\n");
                writer.flush();
                carryOutGDBSession(stringBuffer, GDBSession.DEBUG_QUARKUS_BUILDER_IMAGE_VERTX, esvc, writer, report, true);
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
            if (applySourcesPatch()) {
                runCommand(getRunCommand("git", "apply", "-R", "quarkus_sources.patch"), appDir);
            }
            if (QUARKUS_VERSION.isSnapshot()) {
                runCommand(getRunCommand("git", "apply", "-R", "quarkus_snapshot.patch"), appDir);
            }
            if (QUARKUS_VERSION.majorIs(3) || QUARKUS_VERSION.isSnapshot()) {
                runCommand(getRunCommand("git", "apply", "-R", "quarkus_3.x.patch"), appDir);
            }
        }
    }

    public static void carryOutGDBSession(StringBuffer stringBuffer, GDBSession gdbSession, ExecutorService esvc,
                                          BufferedWriter writer, StringBuilder report, boolean inContainer) {
        final ConcurrentLinkedQueue<String> errorQueue = new ConcurrentLinkedQueue<>();
        Stream.of(gdbSession.get(inContainer)).forEach(cp -> {
                    stringBuffer.delete(0, stringBuffer.length());
                    try {
                        if (cp.c.startsWith("GOTO URL")) {
                            /* This one web request can block on a breakpoint, it might also come too early,
                               so we need to retry it if necessary. */
                            final AtomicBoolean failedToConnect = new AtomicBoolean(true);
                            final Runnable webRequest = () -> {
                                final long gotoURLStart = System.currentTimeMillis();
                                final long timeoutMs = (UsedVersion.getVersion(inContainer)
                                        .compareTo(Version.create(23, 0, 0)) >= 0) ? LONG_GOTO_URL_TIMEOUT_MS : GOTO_URL_TIMEOUT_MS;
                                long durationMs = 0;
                                final String url = cp.c.split("URL ")[1];
                                while (failedToConnect.get() && durationMs < timeoutMs) {
                                    try {
                                        final String content = WebpageTester.getUrlContents(url);
                                        failedToConnect.set(false);
                                        if (!cp.p.matcher(content).matches()) {
                                            errorQueue.add("Content of URL " + url + " should have matched regexp " + cp.p.pattern() +
                                                    " but it was this: " + content);
                                        }
                                    } catch (IOException e) {
                                        try {
                                            Thread.sleep(GOTO_URL_SLEEP_MS);
                                        } catch (InterruptedException x) {
                                            throw new RuntimeException(x);
                                        }
                                        durationMs = System.currentTimeMillis() - gotoURLStart;
                                    }
                                }
                                if (failedToConnect.get()) {
                                    errorQueue.add("Unexpected GOTO URL " + url + " connection failure in " + durationMs + "ms.");
                                }
                            };
                            esvc.submit(webRequest);
                            Logs.appendln(report, cp.c);

                            // We might want to just access a URL and check the content.
                            // We also might want to access a URL to hit a breakpoint. In that case the thread above keeps trying
                            // until the server is ready to talk, and then it hangs on the breakpoint, which is fine.
                            // We don't want to keep spawning more URL accessing threads. We just need to know that the server
                            // was ready to talk so as we can continue with the GDB session, e.g. by calling `bt`.
                            // A simpler version would be to jam a hardcoded sleep instead. Immediately running `bt`
                            // won't work as the breakpoint might not have been hit yet.
                            final long start = System.currentTimeMillis();
                            while (failedToConnect.get() && System.currentTimeMillis() - start < cp.timeoutMs) {
                                try {
                                    Thread.sleep(GOTO_URL_SLEEP_MS);
                                } catch (InterruptedException e) {
                                    throw new RuntimeException(e);
                                }
                            }

                        } else {
                            writer.write(cp.c);
                            writer.flush();
                            Logs.appendln(report, cp.c);
                            // Time unit is the same for timeout and sleep.
                            boolean m = waitForBufferToMatch(report, stringBuffer, cp.p, cp.timeoutMs, 50, TimeUnit.MILLISECONDS);
                            Logs.appendlnSection(report, stringBuffer.toString());
                            if (!m) {
                                errorQueue.add("Command '" + cp.c.trim() + "' did not match the expected pattern in time '" +
                                        cp.p.pattern() + "'.\nOutput was:\n" + stringBuffer);
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

    private static String filterAndUnescapeGDBMIOutput(String line) {
        switch (line.charAt(0)) {
            case '&':
                // Strip & prefix added by GDB/MI to gdb input
            case '=':
                // Strip = prefix added by GDB/MI to program output
                line = line.substring(1);
                break;
            case '~':
                // Strip ~ prefix and quotes added by GDB/MI
                line = line.substring(2, line.length() - 1);
                break;
            default:
                break;
        }
        // Replace \n with newlines
        line = line.replace("\\n", System.lineSeparator());
        // Replace \" with "
        line = line.replace("\\\"", "\"");
        // Replace \t with tab
        line = line.replace("\\t", "\t");
        return line;
    }
}
