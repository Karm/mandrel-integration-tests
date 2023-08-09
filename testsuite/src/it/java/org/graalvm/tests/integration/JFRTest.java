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
import org.graalvm.tests.integration.utils.WebpageTester;
import org.graalvm.tests.integration.utils.versions.IfMandrelVersion;
import org.graalvm.tests.integration.utils.versions.UsedVersion;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.graalvm.tests.integration.AppReproducersTest.validateDebugSmokeApp;
import static org.graalvm.tests.integration.utils.Commands.CONTAINER_RUNTIME;
import static org.graalvm.tests.integration.utils.Commands.builderRoutine;
import static org.graalvm.tests.integration.utils.Commands.cleanTarget;
import static org.graalvm.tests.integration.utils.Commands.cleanup;
import static org.graalvm.tests.integration.utils.Commands.clearCaches;
import static org.graalvm.tests.integration.utils.Commands.getBaseDir;
import static org.graalvm.tests.integration.utils.Commands.getRSSkB;
import static org.graalvm.tests.integration.utils.Commands.getRunCommand;
import static org.graalvm.tests.integration.utils.Commands.processStopper;
import static org.graalvm.tests.integration.utils.Commands.removeContainers;
import static org.graalvm.tests.integration.utils.Commands.replaceSwitchesInCmd;
import static org.graalvm.tests.integration.utils.Commands.runCommand;
import static org.graalvm.tests.integration.utils.Commands.stopAllRunningContainers;
import static org.graalvm.tests.integration.utils.Logs.getLogsDir;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
        MONITOR_22("--enable-monitoring=jfr"),
        MONITOR_21("-H:+AllowVMInspection"),
        HOTSPOT_17_FLIGHT_RECORDER(""),
        HOTSPOT_11_FLIGHT_RECORDER("-XX:+FlightRecorder");

        public final String replacement;

        JFROption(String replacement) {
            this.replacement = replacement;
        }
    }

    // https://github.com/oracle/graal/pull/4823
    public static final String JFR_MONITORING_SWITCH_TOKEN = "<ALLOW_VM_INSPECTION>";
    // https://bugs.openjdk.org/browse/JDK-8225312
    public static final String JFR_FLIGHT_RECORDER_HOTSPOT_TOKEN = "<FLIGHT_RECORDER>";

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

    @Test
    @Tag("jfr-perf")
    @IfMandrelVersion(min = "22.3")
    public void jfrPerfTest(TestInfo testInfo) throws IOException, InterruptedException {
        Apps appJfr = Apps.JFR_PERFORMANCE;
        Apps appNoJfr = Apps.PLAINTEXT_PERFORMANCE;
        LOGGER.info("Testing app: " + appJfr);
        File processLog = null;
        final StringBuilder report = new StringBuilder();
        final File appDir = Path.of(BASE_DIR, appJfr.dir).toFile();
        final String cn = testInfo.getTestClass().get().getCanonicalName();
        final String mn = testInfo.getTestMethod().get().getName();
        try {
            // Cleanup
            cleanTarget(appJfr);
            Files.createDirectories(Paths.get(appDir.getAbsolutePath() + File.separator + "logs"));

            // Build and run
            processLog = Path.of(appDir.getAbsolutePath(), "logs", "build-and-run.log").toFile();
//
//            builderRoutine(2, appJfr, report, cn, mn, appDir, processLog, null, null);
//            builderRoutine(2, appNoJfr, report, cn, mn, appDir, processLog, null, null);

            // Get image sizes
            String[] cmd1 = new String[]{"stat", "-c%s",
                    "../"+appNoJfr.dir+"/target_tmp/jfr-native-image-performance-1.0.0-SNAPSHOT-runner_no_jfr",
                    ";", "stat", "-c%s","../"+appJfr.dir+"/target_tmp/jfr-native-image-performance-1.0.0-SNAPSHOT-runner_with_jfr"};

            final ProcessBuilder processBuilder0 = new ProcessBuilder(cmd1);
            Process p = processBuilder0.start();
            BufferedReader processOutputReader = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8));
            Integer  imageSizeNoJFR = Integer.valueOf(processOutputReader.readLine());
            Integer  imageSizeWithJFR= Integer.valueOf(processOutputReader.readLine());
            System.out.println("++++++++++++++++++++++="+imageSizeNoJFR);
            System.out.println("++++++++++++++++++++++="+imageSizeWithJFR);


            // WITH JFR
            runPerfTest(5, appJfr, appDir, processLog, cn, mn, report);

            clearCaches();

            // NO JFR
            runPerfTest(5, appNoJfr, appDir, processLog, cn, mn, report);


        } finally {
            // The quarkus process already are stopped
            if (appJfr.runtimeContainer != ContainerNames.NONE) {
                stopAllRunningContainers();
                removeContainers(appJfr.runtimeContainer.name, appJfr.runtimeContainer.name + "-build", appJfr.runtimeContainer.name + "-run");
            }
        }

    }

    // run_test
    private void runPerfTest(int trials, Apps app, File appDir, File processLog, String cn,String mn, StringBuilder report) throws IOException, InterruptedException {
        Process process = null;
        Process hyperfoilProcess = null;
        long rssSum = 0;
        long startupSum = 0;
        final HttpClient hc = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build();

        try {
            for (int i = 0; i < trials; i++) {
                if (process != null){
                    processStopper(process, true, true); // stop each time to avoid influencing startup time
                }
                List<String> cmd = getRunCommand(app.buildAndRunCmds.cmds[2]);
                clearCaches(); // *** Not sure this is working or is needed. Doesnt seems to have any affect
                process = runCommand(cmd, appDir, processLog, app);
                assertNotNull(process, "The test application failed to run. Check " + getLogsDir(cn, mn) + File.separator + processLog.getName());
                startupSum += WebpageTester.testWeb(app.urlContent.urlContent[0][0], 10, app.urlContent.urlContent[0][1], true);
                rssSum += getRSSkB(process.pid());
            }

            // Run Hyperfoil controller in container and expose port for test
            List<String> getAndStartHyperfoil = getRunCommand(app.buildAndRunCmds.cmds[3]);
            hyperfoilProcess = runCommand(getAndStartHyperfoil, appDir, processLog, app);
            assertNotNull(hyperfoilProcess, "The test application failed to run. Check " + getLogsDir(cn, mn) + File.separator + processLog.getName());

            // Wait for hyperfoil to start up
            System.out.println("waiting for hyperfoil to start");
            // TODO this fails occasionally, then will succeed immediately after.
            WebpageTester.testWeb(app.urlContent.urlContent[2][0], 15, app.urlContent.urlContent[2][1], false);
            System.out.println("hyperfoil has started. Done waiting.");

            // upload benchmark
            final HttpRequest uploadRequest = HttpRequest.newBuilder()
                    .uri(new URI(app.urlContent.urlContent[1][0]))
                    .header("Content-Type", "text/vnd.yaml")
                    .POST( HttpRequest.BodyPublishers.ofFile(Path.of(appDir.getAbsolutePath()+"/worst_case_benchmark.hf.yaml")))
                    .build();
            System.out.println(uploadRequest.toString());
            final HttpResponse<String> releaseResponse = hc.send(uploadRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(204, releaseResponse.statusCode(), "App returned a non HTTP 204 response. The perf report is invalid.");
            System.out.println("Response "+ releaseResponse.statusCode());

            // Run benchmark
            final HttpRequest benchmarkRequest = HttpRequest.newBuilder()
                    .uri(new URI(app.urlContent.urlContent[3][0]))
                    .GET()
                    .build();
            final HttpResponse<String> benchmarkResponse = hc.send(benchmarkRequest, HttpResponse.BodyHandlers.ofString());
            JSONObject benchmarkResponseJson = new JSONObject(benchmarkResponse.body());
            String id = benchmarkResponseJson.getString("id");
            System.out.println("id:"+id);
            Thread.sleep(7000);

            // Get results
            final HttpRequest resultsRequest = HttpRequest.newBuilder()
                    .uri(new URI("http://0.0.0.0:8090/run/"+id+"/stats/all/json"))
                    .GET()
                    .timeout(Duration.ofSeconds(3)) // set timeout to allow for cleanup, otherwise will stall at first request above
                    .build();
            // TODO Stalling here because the run never actually completes (check it with curl http://0.0.0.0:8090/run/0000)
            final HttpResponse<String> resultsResponse = hc.send(resultsRequest, HttpResponse.BodyHandlers.ofString());
            System.out.println("response code "+ resultsResponse.statusCode());
            JSONObject resultsResponseJson = new JSONObject(resultsResponse.body());
//            JSONObject resultsResponseJson = new JSONObject(json_response);
            System.out.println("mean:"+resultsResponseJson.getJSONArray("stats").getJSONObject(0).getJSONObject("total").getJSONObject("summary").getInt("meanResponseTime"));
            System.out.println("max:"+resultsResponseJson.getJSONArray("stats").getJSONObject(0).getJSONObject("total").getJSONObject("summary").getInt("maxResponseTime"));
            System.out.println("50%:"+resultsResponseJson.getJSONArray("stats").getJSONObject(0).getJSONObject("total").getJSONObject("summary").getJSONObject("percentileResponseTime").getInt("50.0"));
            System.out.println("90%:"+resultsResponseJson.getJSONArray("stats").getJSONObject(0).getJSONObject("total").getJSONObject("summary").getJSONObject("percentileResponseTime").getInt("90.0"));
            System.out.println("99%:"+resultsResponseJson.getJSONArray("stats").getJSONObject(0).getJSONObject("total").getJSONObject("summary").getJSONObject("percentileResponseTime").getInt("99.0"));

        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        } finally {
            if (process != null && process.isAlive()) {
                processStopper(process, true); // *** confirmed this works may need fuser -k 8080/tcp in case there are remnant bg tasks
            }
            // Stop container before stopping hyperfoil process
            stopAllRunningContainers();
            removeContainers(app.runtimeContainer.name);
            if (hyperfoilProcess != null && hyperfoilProcess.isAlive()) {
                processStopper(hyperfoilProcess, true);
            }

            System.out.println("TIME TO FIRST REQUEST:"+ app.name()+ " "+ startupSum/trials);
            System.out.println("RSS JFR:" + rssSum/trials);
        }
    }

    public void jfrSmoke(TestInfo testInfo, Apps app) throws IOException, InterruptedException {
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

            // Build and run
            processLog = Path.of(appDir.getAbsolutePath(), "logs", "build-and-run.log").toFile();
            final Map<String, String> switches;
            final boolean inContainer = app.runtimeContainer != ContainerNames.NONE;
            if (UsedVersion.getVersion(inContainer).compareTo(Version.create(22, 3, 0)) >= 0) {
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
            if (UsedVersion.jdkFeature(inContainer) >= 17) {
                cmd = replaceSwitchesInCmd(cmd, Map.of(JFR_FLIGHT_RECORDER_HOTSPOT_TOKEN, JFROption.HOTSPOT_17_FLIGHT_RECORDER.replacement));
            } else {
                cmd = replaceSwitchesInCmd(cmd, Map.of(JFR_FLIGHT_RECORDER_HOTSPOT_TOKEN, JFROption.HOTSPOT_11_FLIGHT_RECORDER.replacement));
            }
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
        final File appDir = Path.of(BASE_DIR, app.dir).toFile();
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
                    assertNotNull(p, "Process failed to run.");
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
    private static String json_response ="{'info': {'id': '0164', 'benchmark': 'jfr-hyperfoil', 'params': {}, 'startTime': 1690233905485, 'terminateTime': 1690233910702, 'cancelled': False, 'description': None, 'errors': []}, '$schema': 'http://hyperfoil.io/run-schema/v3.0', 'version': '0.24.1', 'commit': '61563f25459069b44109c8882500a95d3eede3bb', 'failures': [], 'stats': [{'name': 'main', 'phase': 'main', 'iteration': '', 'fork': '', 'metric': 'other', 'isWarmup': False, 'total': {'phase': 'main', 'metric': 'other', 'start': 1690233905486, 'end': 1690233910701, 'summary': {'startTime': 1690233905486, 'endTime': 1690233910701, 'minResponseTime': 209715200, 'meanResponseTime': 213396257, 'maxResponseTime': 242221055, 'percentileResponseTime': {'50.0': 211812351, '90.0': 220200959, '99.0': 238026751, '99.9': 241172479, '99.99': 242221055}, 'requestCount': 2564, 'responseCount': 2564, 'invalid': 0, 'connectionErrors': 0, 'requestTimeouts': 0, 'internalErrors': 0, 'blockedTime': 0, 'extensions': {'http': {'@type': 'http', 'status_2xx': 2564, 'status_3xx': 0, 'status_4xx': 0, 'status_5xx': 0, 'status_other': 0, 'cacheHits': 0}}}, 'failures': 0, 'minSessions': 0, 'maxSessions': 137}, 'histogram': {'percentiles': [{'from': 0.0, 'to': 210763775.0, 'percentile': 0.0, 'count': 1, 'totalCount': 1}, {'from': 210763775.0, 'to': 211812351.0, 'percentile': 0.1, 'count': 1724, 'totalCount': 1725}, {'from': 211812351.0, 'to': 211812351.0, 'percentile': 0.65, 'count': 0, 'totalCount': 1725}, {'from': 211812351.0, 'to': 212860927.0, 'percentile': 0.7, 'count': 303, 'totalCount': 2028}, {'from': 212860927.0, 'to': 212860927.0, 'percentile': 0.775, 'count': 0, 'totalCount': 2028}, {'from': 212860927.0, 'to': 213909503.0, 'percentile': 0.8, 'count': 26, 'totalCount': 2054}, {'from': 213909503.0, 'to': 216006655.0, 'percentile': 0.825, 'count': 144, 'totalCount': 2198}, {'from': 216006655.0, 'to': 216006655.0, 'percentile': 0.85, 'count': 0, 'totalCount': 2198}, {'from': 216006655.0, 'to': 220200959.0, 'percentile': 0.875, 'count': 118, 'totalCount': 2316}, {'from': 220200959.0, 'to': 220200959.0, 'percentile': 0.9, 'count': 0, 'totalCount': 2316}, {'from': 220200959.0, 'to': 221249535.0, 'percentile': 0.9125, 'count': 36, 'totalCount': 2352}, {'from': 221249535.0, 'to': 222298111.0, 'percentile': 0.925, 'count': 98, 'totalCount': 2450}, {'from': 222298111.0, 'to': 222298111.0, 'percentile': 0.95, 'count': 0, 'totalCount': 2450}, {'from': 222298111.0, 'to': 223346687.0, 'percentile': 0.95625, 'count': 16, 'totalCount': 2466}, {'from': 223346687.0, 'to': 224395263.0, 'percentile': 0.9625, 'count': 9, 'totalCount': 2475}, {'from': 224395263.0, 'to': 225443839.0, 'percentile': 0.96875, 'count': 23, 'totalCount': 2498}, {'from': 225443839.0, 'to': 225443839.0, 'percentile': 0.971875, 'count': 0, 'totalCount': 2498}, {'from': 225443839.0, 'to': 226492415.0, 'percentile': 0.975, 'count': 14, 'totalCount': 2512}, {'from': 226492415.0, 'to': 226492415.0, 'percentile': 0.978125, 'count': 0, 'totalCount': 2512}, {'from': 226492415.0, 'to': 235929599.0, 'percentile': 0.98125, 'count': 4, 'totalCount': 2516}, {'from': 235929599.0, 'to': 238026751.0, 'percentile': 0.984375, 'count': 27, 'totalCount': 2543}, {'from': 238026751.0, 'to': 238026751.0, 'percentile': 0.990625, 'count': 0, 'totalCount': 2543}, {'from': 238026751.0, 'to': 239075327.0, 'percentile': 0.9921875, 'count': 15, 'totalCount': 2558}, {'from': 239075327.0, 'to': 239075327.0, 'percentile': 0.99765625, 'count': 0, 'totalCount': 2558}, {'from': 239075327.0, 'to': 240123903.0, 'percentile': 0.998046875, 'count': 2, 'totalCount': 2560}, {'from': 240123903.0, 'to': 240123903.0, 'percentile': 0.9984375, 'count': 0, 'totalCount': 2560}, {'from': 240123903.0, 'to': 241172479.0, 'percentile': 0.9986328125, 'count': 2, 'totalCount': 2562}, {'from': 241172479.0, 'to': 241172479.0, 'percentile': 0.99921875, 'count': 0, 'totalCount': 2562}, {'from': 241172479.0, 'to': 242221055.0, 'percentile': 0.99931640625, 'count': 2, 'totalCount': 2564}, {'from': 242221055.0, 'to': 242221055.0, 'percentile': 1.0, 'count': 0, 'totalCount': 2564}], 'linear': [{'from': 0.0, 'to': 208999999.0, 'percentile': 0.0, 'count': 0, 'totalCount': 0}, {'from': 208999999.0, 'to': 209999999.0, 'percentile': 0.000390015600624025, 'count': 1, 'totalCount': 1}, {'from': 209999999.0, 'to': 210999999.0, 'percentile': 0.672776911076443, 'count': 1724, 'totalCount': 1725}, {'from': 210999999.0, 'to': 211999999.0, 'percentile': 0.7909516380655226, 'count': 303, 'totalCount': 2028}, {'from': 211999999.0, 'to': 212999999.0, 'percentile': 0.8010920436817472, 'count': 26, 'totalCount': 2054}, {'from': 212999999.0, 'to': 213999999.0, 'percentile': 0.8104524180967239, 'count': 24, 'totalCount': 2078}, {'from': 213999999.0, 'to': 214999999.0, 'percentile': 0.8572542901716069, 'count': 120, 'totalCount': 2198}, {'from': 214999999.0, 'to': 215999999.0, 'percentile': 0.8572542901716069, 'count': 0, 'totalCount': 2198}, {'from': 215999999.0, 'to': 216999999.0, 'percentile': 0.8654446177847115, 'count': 21, 'totalCount': 2219}, {'from': 216999999.0, 'to': 217999999.0, 'percentile': 0.8685647425897036, 'count': 8, 'totalCount': 2227}, {'from': 217999999.0, 'to': 218999999.0, 'percentile': 0.8697347893915758, 'count': 3, 'totalCount': 2230}, {'from': 218999999.0, 'to': 219999999.0, 'percentile': 0.9032761310452417, 'count': 86, 'totalCount': 2316}, {'from': 219999999.0, 'to': 220999999.0, 'percentile': 0.9173166926677067, 'count': 36, 'totalCount': 2352}, {'from': 220999999.0, 'to': 221999999.0, 'percentile': 0.9555382215288611, 'count': 98, 'totalCount': 2450}, {'from': 221999999.0, 'to': 241999999.0, 'percentile': 1.0, 'count': 114, 'totalCount': 2564}]}, 'series': [{'startTime': 1690233905488, 'endTime': 1690233906488, 'minResponseTime': 210763776, 'meanResponseTime': 215727171, 'maxResponseTime': 242221055, 'percentileResponseTime': {'50.0': 211812351, '90.0': 227540991, '99.0': 240123903, '99.9': 242221055, '99.99': 242221055}, 'requestCount': 514, 'responseCount': 514, 'invalid': 0, 'connectionErrors': 0, 'requestTimeouts': 0, 'internalErrors': 0, 'blockedTime': 0, 'extensions': {'http': {'@type': 'http', 'status_2xx': 514, 'status_3xx': 0, 'status_4xx': 0, 'status_5xx': 0, 'status_other': 0, 'cacheHits': 0}}}, {'startTime': 1690233906488, 'endTime': 1690233907488, 'minResponseTime': 210763776, 'meanResponseTime': 212838132, 'maxResponseTime': 223346687, 'percentileResponseTime': {'50.0': 211812351, '90.0': 222298111, '99.0': 222298111, '99.9': 223346687, '99.99': 223346687}, 'requestCount': 529, 'responseCount': 529, 'invalid': 0, 'connectionErrors': 0, 'requestTimeouts': 0, 'internalErrors': 0, 'blockedTime': 0, 'extensions': {'http': {'@type': 'http', 'status_2xx': 529, 'status_3xx': 0, 'status_4xx': 0, 'status_5xx': 0, 'status_other': 0, 'cacheHits': 0}}}, {'startTime': 1690233907488, 'endTime': 1690233908488, 'minResponseTime': 210763776, 'meanResponseTime': 213391384, 'maxResponseTime': 226492415, 'percentileResponseTime': {'50.0': 211812351, '90.0': 217055231, '99.0': 223346687, '99.9': 226492415, '99.99': 226492415}, 'requestCount': 510, 'responseCount': 510, 'invalid': 0, 'connectionErrors': 0, 'requestTimeouts': 0, 'internalErrors': 0, 'blockedTime': 0, 'extensions': {'http': {'@type': 'http', 'status_2xx': 510, 'status_3xx': 0, 'status_4xx': 0, 'status_5xx': 0, 'status_other': 0, 'cacheHits': 0}}}, {'startTime': 1690233908488, 'endTime': 1690233909488, 'minResponseTime': 210763776, 'meanResponseTime': 212464364, 'maxResponseTime': 221249535, 'percentileResponseTime': {'50.0': 211812351, '90.0': 220200959, '99.0': 221249535, '99.9': 221249535, '99.99': 221249535}, 'requestCount': 509, 'responseCount': 509, 'invalid': 0, 'connectionErrors': 0, 'requestTimeouts': 0, 'internalErrors': 0, 'blockedTime': 0, 'extensions': {'http': {'@type': 'http', 'status_2xx': 509, 'status_3xx': 0, 'status_4xx': 0, 'status_5xx': 0, 'status_other': 0, 'cacheHits': 0}}}, {'startTime': 1690233909488, 'endTime': 1690233910488, 'minResponseTime': 209715200, 'meanResponseTime': 212547608, 'maxResponseTime': 221249535, 'percentileResponseTime': {'50.0': 211812351, '90.0': 220200959, '99.0': 221249535, '99.9': 221249535, '99.99': 221249535}, 'requestCount': 502, 'responseCount': 502, 'invalid': 0, 'connectionErrors': 0, 'requestTimeouts': 0, 'internalErrors': 0, 'blockedTime': 0, 'extensions': {'http': {'@type': 'http', 'status_2xx': 502, 'status_3xx': 0, 'status_4xx': 0, 'status_5xx': 0, 'status_other': 0, 'cacheHits': 0}}}]}], 'sessions': [{'name': 'main', 'phase': 'main', 'iteration': '', 'fork': '', 'sessions': [{'timestamp': 1690233906400, 'agent': 'in-vm', 'minSessions': 0, 'maxSessions': 137}, {'timestamp': 1690233907401, 'agent': 'in-vm', 'minSessions': 91, 'maxSessions': 127}, {'timestamp': 1690233908399, 'agent': 'in-vm', 'minSessions': 91, 'maxSessions': 126}, {'timestamp': 1690233909401, 'agent': 'in-vm', 'minSessions': 93, 'maxSessions': 127}, {'timestamp': 1690233910400, 'agent': 'in-vm', 'minSessions': 87, 'maxSessions': 121}]}], 'agents': [{'name': 'in-vm', 'stats': [{'name': 'main', 'phase': 'main', 'iteration': '', 'fork': '', 'metric': 'other', 'isWarmup': False, 'total': {'phase': 'main', 'metric': 'other', 'start': 1690233905486, 'end': 1690233910701, 'summary': {'startTime': 1690233905488, 'endTime': 1690233910488, 'minResponseTime': 209715200, 'meanResponseTime': 213396257, 'maxResponseTime': 242221055, 'percentileResponseTime': {'50.0': 211812351, '90.0': 220200959, '99.0': 238026751, '99.9': 241172479, '99.99': 242221055}, 'requestCount': 2564, 'responseCount': 2564, 'invalid': 0, 'connectionErrors': 0, 'requestTimeouts': 0, 'internalErrors': 0, 'blockedTime': 0, 'extensions': {'http': {'@type': 'http', 'status_2xx': 2564, 'status_3xx': 0, 'status_4xx': 0, 'status_5xx': 0, 'status_other': 0, 'cacheHits': 0}}}, 'minSessions': 0, 'maxSessions': 137}, 'histogram': {'percentiles': [{'from': 0.0, 'to': 210763775.0, 'percentile': 0.0, 'count': 1, 'totalCount': 1}, {'from': 210763775.0, 'to': 211812351.0, 'percentile': 0.1, 'count': 1724, 'totalCount': 1725}, {'from': 211812351.0, 'to': 211812351.0, 'percentile': 0.65, 'count': 0, 'totalCount': 1725}, {'from': 211812351.0, 'to': 212860927.0, 'percentile': 0.7, 'count': 303, 'totalCount': 2028}, {'from': 212860927.0, 'to': 212860927.0, 'percentile': 0.775, 'count': 0, 'totalCount': 2028}, {'from': 212860927.0, 'to': 213909503.0, 'percentile': 0.8, 'count': 26, 'totalCount': 2054}, {'from': 213909503.0, 'to': 216006655.0, 'percentile': 0.825, 'count': 144, 'totalCount': 2198}, {'from': 216006655.0, 'to': 216006655.0, 'percentile': 0.85, 'count': 0, 'totalCount': 2198}, {'from': 216006655.0, 'to': 220200959.0, 'percentile': 0.875, 'count': 118, 'totalCount': 2316}, {'from': 220200959.0, 'to': 220200959.0, 'percentile': 0.9, 'count': 0, 'totalCount': 2316}, {'from': 220200959.0, 'to': 221249535.0, 'percentile': 0.9125, 'count': 36, 'totalCount': 2352}, {'from': 221249535.0, 'to': 222298111.0, 'percentile': 0.925, 'count': 98, 'totalCount': 2450}, {'from': 222298111.0, 'to': 222298111.0, 'percentile': 0.95, 'count': 0, 'totalCount': 2450}, {'from': 222298111.0, 'to': 223346687.0, 'percentile': 0.95625, 'count': 16, 'totalCount': 2466}, {'from': 223346687.0, 'to': 224395263.0, 'percentile': 0.9625, 'count': 9, 'totalCount': 2475}, {'from': 224395263.0, 'to': 225443839.0, 'percentile': 0.96875, 'count': 23, 'totalCount': 2498}, {'from': 225443839.0, 'to': 225443839.0, 'percentile': 0.971875, 'count': 0, 'totalCount': 2498}, {'from': 225443839.0, 'to': 226492415.0, 'percentile': 0.975, 'count': 14, 'totalCount': 2512}, {'from': 226492415.0, 'to': 226492415.0, 'percentile': 0.978125, 'count': 0, 'totalCount': 2512}, {'from': 226492415.0, 'to': 235929599.0, 'percentile': 0.98125, 'count': 4, 'totalCount': 2516}, {'from': 235929599.0, 'to': 238026751.0, 'percentile': 0.984375, 'count': 27, 'totalCount': 2543}, {'from': 238026751.0, 'to': 238026751.0, 'percentile': 0.990625, 'count': 0, 'totalCount': 2543}, {'from': 238026751.0, 'to': 239075327.0, 'percentile': 0.9921875, 'count': 15, 'totalCount': 2558}, {'from': 239075327.0, 'to': 239075327.0, 'percentile': 0.99765625, 'count': 0, 'totalCount': 2558}, {'from': 239075327.0, 'to': 240123903.0, 'percentile': 0.998046875, 'count': 2, 'totalCount': 2560}, {'from': 240123903.0, 'to': 240123903.0, 'percentile': 0.9984375, 'count': 0, 'totalCount': 2560}, {'from': 240123903.0, 'to': 241172479.0, 'percentile': 0.9986328125, 'count': 2, 'totalCount': 2562}, {'from': 241172479.0, 'to': 241172479.0, 'percentile': 0.99921875, 'count': 0, 'totalCount': 2562}, {'from': 241172479.0, 'to': 242221055.0, 'percentile': 0.99931640625, 'count': 2, 'totalCount': 2564}, {'from': 242221055.0, 'to': 242221055.0, 'percentile': 1.0, 'count': 0, 'totalCount': 2564}], 'linear': [{'from': 0.0, 'to': 208999999.0, 'percentile': 0.0, 'count': 0, 'totalCount': 0}, {'from': 208999999.0, 'to': 209999999.0, 'percentile': 0.000390015600624025, 'count': 1, 'totalCount': 1}, {'from': 209999999.0, 'to': 210999999.0, 'percentile': 0.672776911076443, 'count': 1724, 'totalCount': 1725}, {'from': 210999999.0, 'to': 211999999.0, 'percentile': 0.7909516380655226, 'count': 303, 'totalCount': 2028}, {'from': 211999999.0, 'to': 212999999.0, 'percentile': 0.8010920436817472, 'count': 26, 'totalCount': 2054}, {'from': 212999999.0, 'to': 213999999.0, 'percentile': 0.8104524180967239, 'count': 24, 'totalCount': 2078}, {'from': 213999999.0, 'to': 214999999.0, 'percentile': 0.8572542901716069, 'count': 120, 'totalCount': 2198}, {'from': 214999999.0, 'to': 215999999.0, 'percentile': 0.8572542901716069, 'count': 0, 'totalCount': 2198}, {'from': 215999999.0, 'to': 216999999.0, 'percentile': 0.8654446177847115, 'count': 21, 'totalCount': 2219}, {'from': 216999999.0, 'to': 217999999.0, 'percentile': 0.8685647425897036, 'count': 8, 'totalCount': 2227}, {'from': 217999999.0, 'to': 218999999.0, 'percentile': 0.8697347893915758, 'count': 3, 'totalCount': 2230}, {'from': 218999999.0, 'to': 219999999.0, 'percentile': 0.9032761310452417, 'count': 86, 'totalCount': 2316}, {'from': 219999999.0, 'to': 220999999.0, 'percentile': 0.9173166926677067, 'count': 36, 'totalCount': 2352}, {'from': 220999999.0, 'to': 221999999.0, 'percentile': 0.9555382215288611, 'count': 98, 'totalCount': 2450}, {'from': 221999999.0, 'to': 241999999.0, 'percentile': 1.0, 'count': 114, 'totalCount': 2564}]}, 'series': [{'startTime': 1690233905488, 'endTime': 1690233906488, 'minResponseTime': 210763776, 'meanResponseTime': 215727171, 'maxResponseTime': 242221055, 'percentileResponseTime': {'50.0': 211812351, '90.0': 227540991, '99.0': 240123903, '99.9': 242221055, '99.99': 242221055}, 'requestCount': 514, 'responseCount': 514, 'invalid': 0, 'connectionErrors': 0, 'requestTimeouts': 0, 'internalErrors': 0, 'blockedTime': 0, 'extensions': {'http': {'@type': 'http', 'status_2xx': 514, 'status_3xx': 0, 'status_4xx': 0, 'status_5xx': 0, 'status_other': 0, 'cacheHits': 0}}}, {'startTime': 1690233906488, 'endTime': 1690233907488, 'minResponseTime': 210763776, 'meanResponseTime': 212838132, 'maxResponseTime': 223346687, 'percentileResponseTime': {'50.0': 211812351, '90.0': 222298111, '99.0': 222298111, '99.9': 223346687, '99.99': 223346687}, 'requestCount': 529, 'responseCount': 529, 'invalid': 0, 'connectionErrors': 0, 'requestTimeouts': 0, 'internalErrors': 0, 'blockedTime': 0, 'extensions': {'http': {'@type': 'http', 'status_2xx': 529, 'status_3xx': 0, 'status_4xx': 0, 'status_5xx': 0, 'status_other': 0, 'cacheHits': 0}}}, {'startTime': 1690233907488, 'endTime': 1690233908488, 'minResponseTime': 210763776, 'meanResponseTime': 213391384, 'maxResponseTime': 226492415, 'percentileResponseTime': {'50.0': 211812351, '90.0': 217055231, '99.0': 223346687, '99.9': 226492415, '99.99': 226492415}, 'requestCount': 510, 'responseCount': 510, 'invalid': 0, 'connectionErrors': 0, 'requestTimeouts': 0, 'internalErrors': 0, 'blockedTime': 0, 'extensions': {'http': {'@type': 'http', 'status_2xx': 510, 'status_3xx': 0, 'status_4xx': 0, 'status_5xx': 0, 'status_other': 0, 'cacheHits': 0}}}, {'startTime': 1690233908488, 'endTime': 1690233909488, 'minResponseTime': 210763776, 'meanResponseTime': 212464364, 'maxResponseTime': 221249535, 'percentileResponseTime': {'50.0': 211812351, '90.0': 220200959, '99.0': 221249535, '99.9': 221249535, '99.99': 221249535}, 'requestCount': 509, 'responseCount': 509, 'invalid': 0, 'connectionErrors': 0, 'requestTimeouts': 0, 'internalErrors': 0, 'blockedTime': 0, 'extensions': {'http': {'@type': 'http', 'status_2xx': 509, 'status_3xx': 0, 'status_4xx': 0, 'status_5xx': 0, 'status_other': 0, 'cacheHits': 0}}}, {'startTime': 1690233909488, 'endTime': 1690233910488, 'minResponseTime': 209715200, 'meanResponseTime': 212547608, 'maxResponseTime': 221249535, 'percentileResponseTime': {'50.0': 211812351, '90.0': 220200959, '99.0': 221249535, '99.9': 221249535, '99.99': 221249535}, 'requestCount': 502, 'responseCount': 502, 'invalid': 0, 'connectionErrors': 0, 'requestTimeouts': 0, 'internalErrors': 0, 'blockedTime': 0, 'extensions': {'http': {'@type': 'http', 'status_2xx': 502, 'status_3xx': 0, 'status_4xx': 0, 'status_5xx': 0, 'status_other': 0, 'cacheHits': 0}}}]}]}], 'connections': {'localhost:8080': {'in-flight requests': [{'timestamp': 1690233906401, 'agent': 'in-vm', 'min': 0, 'max': 137}, {'timestamp': 1690233907401, 'agent': 'in-vm', 'min': 91, 'max': 127}, {'timestamp': 1690233908399, 'agent': 'in-vm', 'min': 91, 'max': 126}, {'timestamp': 1690233909401, 'agent': 'in-vm', 'min': 93, 'max': 127}, {'timestamp': 1690233910400, 'agent': 'in-vm', 'min': 87, 'max': 121}], 'blocked sessions': [{'timestamp': 1690233906401, 'agent': 'in-vm', 'min': 0, 'max': 0}, {'timestamp': 1690233907401, 'agent': 'in-vm', 'min': 0, 'max': 0}, {'timestamp': 1690233908399, 'agent': 'in-vm', 'min': 0, 'max': 0}, {'timestamp': 1690233909401, 'agent': 'in-vm', 'min': 0, 'max': 0}, {'timestamp': 1690233910400, 'agent': 'in-vm', 'min': 0, 'max': 0}], 'used connections': [{'timestamp': 1690233906401, 'agent': 'in-vm', 'min': 0, 'max': 137}, {'timestamp': 1690233907401, 'agent': 'in-vm', 'min': 91, 'max': 127}, {'timestamp': 1690233908399, 'agent': 'in-vm', 'min': 91, 'max': 126}, {'timestamp': 1690233909401, 'agent': 'in-vm', 'min': 93, 'max': 127}, {'timestamp': 1690233910400, 'agent': 'in-vm', 'min': 87, 'max': 121}], 'HTTP 1.x': [{'timestamp': 1690233906401, 'agent': 'in-vm', 'min': 0, 'max': 200}, {'timestamp': 1690233907401, 'agent': 'in-vm', 'min': 200, 'max': 200}, {'timestamp': 1690233908399, 'agent': 'in-vm', 'min': 200, 'max': 200}, {'timestamp': 1690233909401, 'agent': 'in-vm', 'min': 200, 'max': 200}, {'timestamp': 1690233910400, 'agent': 'in-vm', 'min': 200, 'max': 200}]}}, 'agentCpu': {'main': {'in-vm': '11.6% (1.9/16 cores), 1 core max 16.8%'}}}";
}
