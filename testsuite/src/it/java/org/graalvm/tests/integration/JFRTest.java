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
        final Path measurementsLog = Paths.get(Logs.getLogsDir(cn, mn).toString(), "measurements.csv");
        try {
            // Cleanup
            cleanTarget(appJfr);
            Files.createDirectories(Paths.get(appDir.getAbsolutePath() + File.separator + "logs"));

            // Build and run
            processLog = Path.of(appDir.getAbsolutePath(), "logs", "build-and-run.log").toFile();

            builderRoutine(2, appJfr, report, cn, mn, appDir, processLog, null, null);
            builderRoutine(2, appNoJfr, report, cn, mn, appDir, processLog, null, null);

            // WITH JFR
            Map<String,Integer> measurementsJfr = runPerfTest(5, appJfr, appDir, processLog, cn, mn, report, measurementsLog);

            // NO JFR
            Map<String, Integer> measurementsNoJfr = runPerfTest(5, appNoJfr, appDir, processLog, cn, mn, report, measurementsLog);

            // Write diff results
            LogBuilder logBuilder = new LogBuilder();
            LogBuilder.Log log = logBuilder.app(appNoJfr)
                    .executableSizeKb((long)(Math.abs(measurementsJfr.get("imageSize") - measurementsNoJfr.get("imageSize"))*100.0/measurementsNoJfr.get("imageSize")))
                    .timeToFirstOKRequestMs((long)(Math.abs(measurementsJfr.get("startup")-measurementsNoJfr.get("startup"))*100.0/measurementsNoJfr.get("startup")))
                    .rssKb((long)(Math.abs(measurementsJfr.get("rss")-measurementsNoJfr.get("rss"))*100.0/measurementsNoJfr.get("rss")))
                    .meanResponseTime((long)(Math.abs(measurementsJfr.get("mean")-measurementsNoJfr.get("mean"))*100.0/measurementsNoJfr.get("mean")))
                    .maxResponseTime((long)(Math.abs(measurementsJfr.get("max")-measurementsNoJfr.get("max"))*100.0/measurementsNoJfr.get("max")))
                    .responseTime50Percentile((long)(Math.abs(measurementsJfr.get("50%")-measurementsNoJfr.get("50%"))*100.0/measurementsNoJfr.get("50%")))
                    .responseTime90Percentile((long)(Math.abs(measurementsJfr.get("90%")-measurementsNoJfr.get("90%"))*100.0/measurementsNoJfr.get("90%")))
                    .responseTime99Percentile((long)(Math.abs(measurementsJfr.get("99%")-measurementsNoJfr.get("99%"))*100.0/measurementsNoJfr.get("99%")))
                    .build();
            Logs.logMeasurements(log, measurementsLog);
            Logs.appendln(report, "Measurements Diff %:");
            Logs.appendln(report, log.headerMarkdown + "\n" + log.lineMarkdown);

            Logs.checkLog(cn, mn, appJfr, processLog);
//            Logs.checkThreshold(app, executableSizeKb, rssKb, timeToFirstOKRequest);
        } finally {
            cleanup(null, cn, mn, report, appJfr, processLog);
            // The quarkus process already are stopped
            if (appJfr.runtimeContainer != ContainerNames.NONE) {
                stopAllRunningContainers();
                removeContainers(appJfr.runtimeContainer.name, appJfr.runtimeContainer.name + "-build", appJfr.runtimeContainer.name + "-run");
            }
        }

    }

    private Map<String,Integer> runPerfTest(int trials, Apps app, File appDir, File processLog, String cn,String mn, StringBuilder report, Path measurementsLog) throws IOException, InterruptedException {
        // Get image sizes
        String[] imageSizeCmd = new String[]{"stat", "-c%s", "../"+app.dir+"/target_tmp/jfr-native-image-performance-1.0.0-SNAPSHOT-runner_"+app.name()};

        final ProcessBuilder processBuilder0 = new ProcessBuilder(imageSizeCmd);
        Process p = processBuilder0.start();
        BufferedReader processOutputReader = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8));
        Integer  imageSize = Integer.valueOf(processOutputReader.readLine());
        LOGGER.info(app.name()+" image size "+imageSize);

        Process process = null;
        Process hyperfoilProcess = null;
        int rssSum = 0;
        int startupSum = 0;
        Map<String,Integer> measurements = new HashMap<>();
        final HttpClient hc = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build();

        try {
            for (int i = 0; i < trials; i++) {
                if (process != null){
                    processStopper(process, true, true); // stop each time to avoid influencing startup time
                }
                List<String> cmd = getRunCommand(app.buildAndRunCmds.cmds[2]);
                clearCaches(); // TODO Not sure if this is working or is needed. Doesn't seems to have any affect
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
            WebpageTester.testWeb(app.urlContent.urlContent[2][0], 15, app.urlContent.urlContent[2][1], false);

            // upload benchmark
            final HttpRequest uploadRequest = HttpRequest.newBuilder()
                    .uri(new URI(app.urlContent.urlContent[1][0]))
                    .header("Content-Type", "text/vnd.yaml")
                    .POST( HttpRequest.BodyPublishers.ofFile(Path.of(appDir.getAbsolutePath()+"/worst_case_benchmark.hf.yaml")))
                    .build();
            System.out.println(uploadRequest.toString());
            final HttpResponse<String> releaseResponse = hc.send(uploadRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(204, releaseResponse.statusCode(), "App returned a non HTTP 204 response. The perf report is invalid.");
            LOGGER.info("Hyperfoil upload response code "+ releaseResponse.statusCode());

            // Run benchmark
            final HttpRequest benchmarkRequest = HttpRequest.newBuilder()
                    .uri(new URI(app.urlContent.urlContent[3][0]))
                    .GET()
                    .build();
            final HttpResponse<String> benchmarkResponse = hc.send(benchmarkRequest, HttpResponse.BodyHandlers.ofString());
            JSONObject benchmarkResponseJson = new JSONObject(benchmarkResponse.body());
            String id = benchmarkResponseJson.getString("id");

            // Beanchmark is configured to take 5s
            Thread.sleep(7000);

            // Get results
            final HttpRequest resultsRequest = HttpRequest.newBuilder()
                    .uri(new URI("http://0.0.0.0:8090/run/"+id+"/stats/all/json"))
                    .GET()
                    .timeout(Duration.ofSeconds(3)) // set timeout to allow for cleanup, otherwise will stall at first request above
                    .build();
            final HttpResponse<String> resultsResponse = hc.send(resultsRequest, HttpResponse.BodyHandlers.ofString());
            LOGGER.info("Hyperfoil results response code "+ resultsResponse.statusCode());
            JSONObject resultsResponseJson = new JSONObject(resultsResponse.body());

            measurements.put("mean", resultsResponseJson.getJSONArray("stats").getJSONObject(0).getJSONObject("total").getJSONObject("summary").getInt("meanResponseTime"));
            measurements.put("max", resultsResponseJson.getJSONArray("stats").getJSONObject(0).getJSONObject("total").getJSONObject("summary").getInt("maxResponseTime"));
            measurements.put("50%", resultsResponseJson.getJSONArray("stats").getJSONObject(0).getJSONObject("total").getJSONObject("summary").getJSONObject("percentileResponseTime").getInt("50.0"));
            measurements.put("90%", resultsResponseJson.getJSONArray("stats").getJSONObject(0).getJSONObject("total").getJSONObject("summary").getJSONObject("percentileResponseTime").getInt("90.0"));
            measurements.put("99%", resultsResponseJson.getJSONArray("stats").getJSONObject(0).getJSONObject("total").getJSONObject("summary").getJSONObject("percentileResponseTime").getInt("99.0"));
            measurements.put("startup", startupSum/trials);
            measurements.put("rss", rssSum/trials);
            measurements.put("imageSize", imageSize);

            LOGGER.info("mean:"+measurements.get("mean")
                    + ", max:"+measurements.get("max")
                    + ", 50%:"+measurements.get("50%")
                    + ", 90%:"+measurements.get("90%")
                    + ", 99%:"+measurements.get("99%")
                    + ", startup:"+measurements.get("startup")
                    + ", rss:"+measurements.get("rss")
                    + ", imageSize:"+measurements.get("imageSize"));

            LogBuilder logBuilder = new LogBuilder();
            LogBuilder.Log log = logBuilder.app(app)
                    .executableSizeKb(imageSize)
                    .timeToFirstOKRequestMs(measurements.get("startup"))
                    .rssKb(measurements.get("rss"))
                    .meanResponseTime(measurements.get("mean"))
                    .maxResponseTime(measurements.get("max"))
                    .responseTime50Percentile(measurements.get("50%"))
                    .responseTime90Percentile(measurements.get("90%"))
                    .responseTime99Percentile(measurements.get("99%"))
                    .build();
            Logs.logMeasurements(log, measurementsLog);
            Logs.appendln(report, "Measurements "+app.name()+ ":");
            Logs.appendln(report, log.headerMarkdown + "\n" + log.lineMarkdown);

            return measurements;
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
}
