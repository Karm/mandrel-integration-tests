/*
 * Copyright (c) 2021, Red Hat Inc. All rights reserved.
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.graalvm.tests.integration.AppReproducersTest.validateDebugSmokeApp;
import static org.graalvm.tests.integration.utils.Commands.builderRoutine;
import static org.graalvm.tests.integration.utils.Commands.cleanTarget;
import static org.graalvm.tests.integration.utils.Commands.cleanup;
import static org.graalvm.tests.integration.utils.Commands.getBaseDir;
import static org.graalvm.tests.integration.utils.Commands.getRunCommand;
import static org.graalvm.tests.integration.utils.Commands.removeContainers;
import static org.graalvm.tests.integration.utils.Commands.runCommand;
import static org.graalvm.tests.integration.utils.Commands.stopAllRunningContainers;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for build and start of applications with some real source code.
 * Focused on JFR.
 *
 * @author Michal Karm Babacek <karm@redhat.com>
 */
@Tag("reproducers")
@DisabledOnOs({OS.WINDOWS})
public class JFRTest {

    private static final Logger LOGGER = Logger.getLogger(JFRTest.class.getName());

    public static final String BASE_DIR = getBaseDir();

    public enum JFROption {
        MONITOR_22("--enable-monitoring"),
        MONITOR_21("-H:+AllowVMInspection");

        public final String replacement;

        JFROption(String replacement) {
            this.replacement = replacement;
        }
    }

    public static final String JFR_MONITORING_SWITCH_TOKEN = "<ALLOW_VM_INSPECTION>";

    @Test
    @Tag("builder-image")
    @Tag("jfr")
    @IfMandrelVersion(min = "21.2", inContainer = true)
    public void jfrSmokeContainerTest(TestInfo testInfo) throws IOException, InterruptedException {
        jfrSmoke(testInfo, Apps.JFR_SMOKE_BUILDER_IMAGE);
    }

    @Test
    @Tag("jfr")
    @IfMandrelVersion(min = "21.2")
    public void jfrSmokeTest(TestInfo testInfo) throws IOException, InterruptedException {
        jfrSmoke(testInfo, Apps.JFR_SMOKE);
    }

    public void jfrSmoke(TestInfo testInfo, Apps app) throws IOException, InterruptedException {
        LOGGER.info("Testing app: " + app);
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

            // Build and run
            processLog = Path.of(appDir.getAbsolutePath(), "logs", "build-and-run.log").toFile();
            final Map<String, String> switches;
            if (UsedVersion.getVersion(app.runtimeContainer != ContainerNames.NONE).compareTo(Version.create(22, 3, 0)) >= 0) {
                switches = Map.of(JFR_MONITORING_SWITCH_TOKEN, JFROption.MONITOR_22.replacement);
            } else {
                switches = Map.of(JFR_MONITORING_SWITCH_TOKEN, JFROption.MONITOR_21.replacement);
            }
            // In this case, the two last commands are used for running the app; one in JVM mode and the other in Native mode.
            builderRoutine(app.buildAndRunCmds.cmds.length - 2, app, report, cn, mn, appDir, processLog, null, switches);

            final File inputData = Path.of(BASE_DIR, app.dir, "target", "test_data.txt").toFile();

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

            // Check the recording files were created
            final String archivedLogLocation = "See " + Path.of(BASE_DIR, "testsuite", "target", "archived-logs", cn, mn, processLog.getName());
            for (String file : new String[]{"flight-native.jfr", "flight-java.jfr"}) {
                final Path path = Path.of(appDir.getAbsolutePath(), "logs", file);
                assertTrue(Files.exists(path),
                        file + " recording file should have been created. " + archivedLogLocation);
                assertTrue(Files.size(path) > 1024, file + " seems too small. " + archivedLogLocation);
            }

            validateDebugSmokeApp(processLog, cn, mn, process, app, jvmRunTookMs, nativeRunTookMs, report, "jfr");
        } finally {
            cleanup(process, cn, mn, report, app, processLog);
            if (app.runtimeContainer != ContainerNames.NONE) {
                stopAllRunningContainers();
                removeContainers(app.runtimeContainer.name, app.runtimeContainer.name + "-build", app.runtimeContainer.name + "-run");
            }
        }
    }

    @Test
    @Tag("builder-image")
    @Tag("jfr")
    @IfMandrelVersion(min = "21.2", inContainer = true)
    public void jfrOptionsSmokeContainerTest(TestInfo testInfo) throws IOException, InterruptedException {
        jfrOptionsSmoke(testInfo, Apps.JFR_OPTIONS_BUILDER_IMAGE);
    }

    @Test
    @Tag("jfr")
    @IfMandrelVersion(min = "21.2")
    public void jfrOptionsSmokeTest(TestInfo testInfo) throws IOException, InterruptedException {
        jfrOptionsSmoke(testInfo, Apps.JFR_OPTIONS);
    }

    /**
     * e.g.
     * https://github.com/oracle/graal/issues/3638
     * native-image, UX, JFR, -XX:StartFlightRecording flags/arguments units are not aligned with HotSpot convention
     *
     * This is a basic smoke test using various options. It is not delving
     * into whether they actually affect the recording.
     *
     * @param testInfo
     * @throws IOException
     * @throws InterruptedException
     */
    public void jfrOptionsSmoke(TestInfo testInfo, Apps app) throws IOException, InterruptedException {
        LOGGER.info("Testing app: " + app);
        File processLog = null;
        final StringBuilder report = new StringBuilder();
        final File appDir = new File(BASE_DIR + File.separator + app.dir);
        final String cn = testInfo.getTestClass().get().getCanonicalName();
        final String mn = testInfo.getTestMethod().get().getName();
        try {
            // Cleanup
            cleanTarget(app);
            Files.createDirectories(Paths.get(appDir.getAbsolutePath() + File.separator + "logs"));

            // Build and run
            processLog = Path.of(appDir.getAbsolutePath(), "logs", "build-and-run.log").toFile();

            final Map<String, String> switches;
            if (UsedVersion.getVersion(app.runtimeContainer != ContainerNames.NONE).compareTo(Version.create(22, 3, 0)) >= 0) {
                switches = Map.of(JFR_MONITORING_SWITCH_TOKEN, JFROption.MONITOR_22.replacement);
            } else {
                switches = Map.of(JFR_MONITORING_SWITCH_TOKEN, JFROption.MONITOR_21.replacement);
            }
            builderRoutine(2, app, report, cn, mn, appDir, processLog, null, switches);

            final Map<String[], Pattern> cmdOutput = new HashMap<>();
            cmdOutput.put(new String[]{"./target/timezones",
                            "-XX:+FlightRecorder",
                            "-XX:StartFlightRecording=maxsize=10000,filename=logs/flight-native.jfr",
                            "-XX:FlightRecorderLogging=jfr"},
                    Pattern.compile(".* Started recording .* \\{maxsize=9.8kB.*", Pattern.DOTALL));
            cmdOutput.put(new String[]{"./target/timezones",
                            "-XX:+FlightRecorder",
                            "-XX:StartFlightRecording=maxsize=10000c,filename=logs/flight-native.jfr",
                            "-XX:FlightRecorderLogging=jfr"},
                    Pattern.compile(".* Started recording .* \\{maxsize=9.8kB.*", Pattern.DOTALL));
            cmdOutput.put(new String[]{"./target/timezones",
                            "-XX:+FlightRecorder",
                            "-XX:StartFlightRecording=maxsize=10M,filename=logs/flight-native.jfr",
                            "-XX:FlightRecorderLogging=jfr"},
                    Pattern.compile(".* Started recording .* \\{maxsize=10.0MB.*", Pattern.DOTALL));
            cmdOutput.put(new String[]{"./target/timezones",
                            "-XX:+FlightRecorder",
                            "-XX:StartFlightRecording=maxsize=10m,filename=logs/flight-native.jfr",
                            "-XX:FlightRecorderLogging=jfr"},
                    Pattern.compile(".* Started recording .* \\{maxsize=10.0MB.*", Pattern.DOTALL));
            cmdOutput.put(new String[]{"./target/timezones",
                            "-XX:+FlightRecorder",
                            "-XX:StartFlightRecording=maxsize=10k,filename=logs/flight-native.jfr",
                            "-XX:FlightRecorderLogging=jfr"},
                    Pattern.compile(".* Started recording .* \\{maxsize=10.0kB.*", Pattern.DOTALL));
            cmdOutput.put(new String[]{"./target/timezones",
                            "-XX:+FlightRecorder",
                            "-XX:StartFlightRecording=maxsize=10g,filename=logs/flight-native.jfr",
                            "-XX:FlightRecorderLogging=jfr"},
                    Pattern.compile(".* Started recording .* \\{maxsize=10.0GB.*", Pattern.DOTALL));
            cmdOutput.put(new String[]{"./target/timezones",
                            "-XX:+FlightRecorder",
                            "-XX:StartFlightRecording=maxsize=10000,maxage=10000ns,filename=logs/flight-native.jfr",
                            "-XX:FlightRecorderLogging=jfr"},
                    Pattern.compile(".* Started recording .* \\{maxage=10us, maxsize=9.8kB.*", Pattern.DOTALL));
            cmdOutput.put(new String[]{"./target/timezones",
                            "-XX:+FlightRecorder",
                            "-XX:StartFlightRecording=maxsize=10000,maxage=30s,filename=logs/flight-native.jfr",
                            "-XX:FlightRecorderLogging=jfr"},
                    Pattern.compile(".* Started recording .* \\{maxage=30s, maxsize=9.8kB.*", Pattern.DOTALL));
            cmdOutput.put(new String[]{"./target/timezones",
                            "-XX:+FlightRecorder",
                            "-XX:StartFlightRecording=maxsize=10000,maxage=10m,filename=logs/flight-native.jfr",
                            "-XX:FlightRecorderLogging=jfr"},
                    Pattern.compile(".* Started recording .* \\{maxage=10m, maxsize=9.8kB.*", Pattern.DOTALL));
            cmdOutput.put(new String[]{"./target/timezones",
                            "-XX:+FlightRecorder",
                            "-XX:StartFlightRecording=maxsize=10000,maxage=11h,filename=logs/flight-native.jfr",
                            "-XX:FlightRecorderLogging=jfr"},
                    Pattern.compile(".* Started recording .* \\{maxage=11h, maxsize=9.8kB.*", Pattern.DOTALL));
            cmdOutput.put(new String[]{"./target/timezones",
                            "-XX:+FlightRecorder",
                            "-XX:StartFlightRecording=maxsize=10000,delay=1000000000ns,filename=logs/flight-native.jfr",
                            "-XX:FlightRecorderLogging=jfr"},
                    Pattern.compile(".* Scheduled recording .* to start at.*", Pattern.DOTALL));
            cmdOutput.put(new String[]{"./target/timezones",
                            "-XX:+FlightRecorder",
                            "-XX:StartFlightRecording=maxsize=10000,delay=5s,filename=logs/flight-native.jfr",
                            "-XX:FlightRecorderLogging=jfr"},
                    Pattern.compile(".* Scheduled recording .* to start at.*", Pattern.DOTALL));
            cmdOutput.put(new String[]{"./target/timezones",
                            "-XX:+FlightRecorder",
                            "-XX:StartFlightRecording=maxsize=10000,delay=5m,filename=logs/flight-native.jfr",
                            "-XX:FlightRecorderLogging=jfr"},
                    Pattern.compile(".* Scheduled recording .* to start at.*", Pattern.DOTALL));
            cmdOutput.put(new String[]{"./target/timezones",
                            "-XX:+FlightRecorder",
                            "-XX:StartFlightRecording=maxsize=10000,delay=5h,filename=logs/flight-native.jfr",
                            "-XX:FlightRecorderLogging=jfr"},
                    Pattern.compile(".* Scheduled recording .* to start at.*", Pattern.DOTALL));
            cmdOutput.put(new String[]{"./target/timezones",
                            "-XX:+FlightRecorder",
                            "-XX:StartFlightRecording=maxsize=10000,delay=5d,filename=logs/flight-native.jfr",
                            "-XX:FlightRecorderLogging=jfr"},
                    Pattern.compile(".* Scheduled recording .* to start at.*", Pattern.DOTALL));

            for (Map.Entry<String[], Pattern> co : cmdOutput.entrySet()) {
                Path interimLog = null;
                try {
                    interimLog = Path.of(appDir.getAbsolutePath(), "logs", "interim.log");
                    final List<String> cmd = getRunCommand(co.getKey());
                    Files.writeString(interimLog, String.join(" ", cmd) + "\n", StandardOpenOption.CREATE_NEW);
                    final Process p = runCommand(cmd, appDir, interimLog.toFile(), app);
                    p.waitFor(3, TimeUnit.SECONDS);
                    Logs.appendln(report, appDir.getAbsolutePath());
                    Logs.appendlnSection(report, String.join(" ", cmd));
                    Files.writeString(processLog.toPath(), Files.readString(interimLog) + "\n", StandardOpenOption.APPEND);
                    final String interimLogString = Files.readString(interimLog, StandardCharsets.US_ASCII);
                    final Matcher m = co.getValue().matcher(interimLogString);
                    assertTrue(m.matches(), "Command `" + String.join(" ", cmd) + "' " +
                            "output did not match expected pattern `" + co.getValue().toString() + "', it was: " + interimLogString);
                } finally {
                    if (interimLog != null) {
                        Files.delete(interimLog);
                    }
                }
            }

            Logs.checkLog(cn, mn, app, processLog);
        } finally {
            cleanup(null, cn, mn, report, app, processLog);
            if (app.runtimeContainer != ContainerNames.NONE) {
                stopAllRunningContainers();
                removeContainers(app.runtimeContainer.name, app.runtimeContainer.name + "-build", app.runtimeContainer.name + "-run");
            }
        }
    }
}
