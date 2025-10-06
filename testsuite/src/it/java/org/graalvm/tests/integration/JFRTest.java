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
import org.graalvm.tests.integration.utils.Commands;
import org.graalvm.tests.integration.utils.ContainerNames;
import org.graalvm.tests.integration.utils.HyperfoilHelper;
import org.graalvm.tests.integration.utils.LogBuilder;
import org.graalvm.tests.integration.utils.Logs;
import org.graalvm.tests.integration.utils.WebpageTester;
import org.graalvm.tests.integration.utils.versions.IfMandrelVersion;
import org.graalvm.tests.integration.utils.versions.IfQuarkusVersion;
import org.graalvm.tests.integration.utils.versions.QuarkusVersion;
import org.graalvm.tests.integration.utils.versions.UsedVersion;
import org.jboss.logging.Logger;
import org.json.JSONObject;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

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
import java.util.Scanner;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.graalvm.tests.integration.utils.Commands.BUILDER_IMAGE;
import static org.graalvm.tests.integration.utils.Commands.CONTAINER_RUNTIME;
import static org.graalvm.tests.integration.utils.Commands.IS_THIS_MACOS;
import static org.graalvm.tests.integration.utils.Commands.IS_THIS_WINDOWS;
import static org.graalvm.tests.integration.utils.Commands.QUARKUS_VERSION;
import static org.graalvm.tests.integration.utils.Commands.builderRoutine;
import static org.graalvm.tests.integration.utils.Commands.cleanTarget;
import static org.graalvm.tests.integration.utils.Commands.cleanup;
import static org.graalvm.tests.integration.utils.Commands.clearCaches;
import static org.graalvm.tests.integration.utils.Commands.disableTurbo;
import static org.graalvm.tests.integration.utils.Commands.enableTurbo;
import static org.graalvm.tests.integration.utils.Commands.findExecutable;
import static org.graalvm.tests.integration.utils.Commands.getBaseDir;
import static org.graalvm.tests.integration.utils.Commands.getContainerMemoryKb;
import static org.graalvm.tests.integration.utils.Commands.getPodmanMachineSSHPort;
import static org.graalvm.tests.integration.utils.Commands.getRSSkB;
import static org.graalvm.tests.integration.utils.Commands.getRunCommand;
import static org.graalvm.tests.integration.utils.Commands.getUnixUIDGID;
import static org.graalvm.tests.integration.utils.Commands.openSSHTunnel;
import static org.graalvm.tests.integration.utils.Commands.pidKiller;
import static org.graalvm.tests.integration.utils.Commands.processStopper;
import static org.graalvm.tests.integration.utils.Commands.removeContainers;
import static org.graalvm.tests.integration.utils.Commands.replaceSwitchesInCmd;
import static org.graalvm.tests.integration.utils.Commands.runCommand;
import static org.graalvm.tests.integration.utils.Commands.stopAllRunningContainers;
import static org.graalvm.tests.integration.utils.Commands.waitForTcpClosed;
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

    public enum Endpoint {
        REGULAR,
        WORK;

        @Override
        public String toString() {
            return name().toLowerCase();
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

    /**
     * This test compares a simple Quarkus plaintext app built with and without JFR.
     * The comparison is done using two different Hyperfoil benchmarks. This results in 4 runs total.
     * Thresholds defined in the app directory are not absolute values (unlike in other perf tests in the project),
     * instead they are the percent difference between the runs with/without JFR. This allows for a relative comparison
     * to see how much JFR is impacting performance.
     * The "worst" case benchmark intentionally emits an unrealistically large number of JFR events. jdk.ThreadPark has
     * been chosen as the event to be emitted in a loop because it can be called directly with
     * {@link LockSupport#parkNanos(long)}. This allows us to exercise the JFR infrastructure maximally without adding
     * too much non-JFR overhead. This should help expose slowness in JFR substrateVM code. The "normal" case
     * hyperfoil benchmark is meant to be a more realistic representation of the impact of JFR. We only compare against
     * the defined thresholds with respect to the "normal" case benchmark.
     *
     * e.g. in "work Measurements Diff %:" report, timeToFirstOKRequestMs 47 means that JFR was 47% slower than no JFR.
     */
    @Test
    @Tag("jfr-perf")
    @Tag("jfr")
    @Tag("builder-image")
    @IfMandrelVersion(min = "23.0.0", inContainer = true) // Thread park event is introduced in 23.0
    @IfQuarkusVersion(min = "3.0")
    public void jfrPerfContainerTest(TestInfo testInfo) throws IOException, InterruptedException {
        jfrPerfTestRun(testInfo, true);
    }

    @Test
    @Tag("jfr-perf")
    @Tag("jfr")
    @IfMandrelVersion(min = "23.0.0") // Thread park event is introduced in 23.0
    @IfQuarkusVersion(min = "3.0")
    public void jfrPerfTest(TestInfo testInfo) throws IOException, InterruptedException {
        jfrPerfTestRun(testInfo, false);
    }

    public void jfrPerfTestRun(TestInfo testInfo, boolean inContainer) throws IOException, InterruptedException {
        final Apps appJfr = inContainer ? Apps.JFR_PERFORMANCE_BUILDER_IMAGE : Apps.JFR_PERFORMANCE;
        final Apps appNoJfr = inContainer ? Apps.PLAINTEXT_PERFORMANCE_BUILDER_IMAGE : Apps.PLAINTEXT_PERFORMANCE;
        LOGGER.info("Testing app: " + appJfr);
        File processLog = null;
        final StringBuilder report = new StringBuilder();
        final File appDir = Path.of(BASE_DIR, appJfr.dir).toFile();
        final String cn = testInfo.getTestClass().get().getCanonicalName();
        final String mn = testInfo.getTestMethod().get().getName();
        final Path measurementsLog = Paths.get(Logs.getLogsDir(cn, mn).toString(), "measurements.csv");
        final Path jfrPerfJfc = Paths.get(appDir.getAbsolutePath(), "jfr-perf.jfc");
        String patch = null;

        try {
            // Cleanup
            cleanTarget(appJfr);
            if (inContainer) {
                stopAllRunningContainers();
                removeContainers(ContainerNames.HYPERFOIL.name, appJfr.runtimeContainer.name, appNoJfr.runtimeContainer.name);
            }
            Files.createDirectories(Paths.get(appDir.getAbsolutePath() + File.separator + "logs"));

            // Build and run
            processLog = Path.of(appDir.getAbsolutePath(), "logs", "build-and-run.log").toFile();

            generateJFRConfigurationFile(inContainer, jfrPerfJfc, processLog);

            Map<String, String> switches = null;
            if (UsedVersion.getVersion(inContainer).compareTo(Version.create(23, 1, 0)) >= 0) {
                switches = Map.of("-H:+SignalHandlerBasedExecutionSampler", "-H:+UnlockExperimentalVMOptions,-H:+SignalHandlerBasedExecutionSampler,-H:-UnlockExperimentalVMOptions");
            }

            if (QUARKUS_VERSION.compareTo(QuarkusVersion.V_3_9_0) >= 0) {
                patch = "quarkus_3.9.x.patch";
                runCommand(getRunCommand("git", "apply", patch), appDir);
            }

            // Container build requires an additional step: docker build...
            builderRoutine(appJfr, report, cn, mn, appDir, processLog, null, switches);
            builderRoutine(appNoJfr, report, cn, mn, appDir, processLog, null, switches);

            startComparisonForBenchmark(Endpoint.REGULAR, true, processLog, cn, mn, report, measurementsLog, appDir, appJfr, appNoJfr, inContainer);
            LOGGER.info("REGULAR workload completed.");
            startComparisonForBenchmark(Endpoint.WORK, false, processLog, cn, mn, report, measurementsLog, appDir, appJfr, appNoJfr, inContainer);
            LOGGER.info("WORK workload completed.");

            Logs.checkLog(cn, mn, appJfr, processLog);
        } finally {
            Files.deleteIfExists(jfrPerfJfc);
            cleanup(null, cn, mn, report, appJfr,
                    processLog,
                    new File(appDir.getAbsolutePath() + File.separator + "logs", Endpoint.REGULAR + "-" + appNoJfr.name().toLowerCase() + "-hyperfoil-result.json"),
                    new File(appDir.getAbsolutePath() + File.separator + "logs", Endpoint.WORK + "-" + appNoJfr.name().toLowerCase() + "-hyperfoil-result.json"),
                    new File(appDir.getAbsolutePath() + File.separator + "logs", Endpoint.REGULAR + "-" + appJfr.name().toLowerCase() + "-hyperfoil-result.json"),
                    new File(appDir.getAbsolutePath() + File.separator + "logs", Endpoint.WORK + "-" + appJfr.name().toLowerCase() + "-hyperfoil-result.json"),
                    new File(appDir.getAbsolutePath() + File.separator + "logs", Endpoint.REGULAR + "-" + appJfr.name().toLowerCase() + "-flight-native.jfr"),
                    new File(appDir.getAbsolutePath() + File.separator + "logs", Endpoint.WORK + "-" + appJfr.name().toLowerCase() + "-flight-native.jfr"));
            stopAllRunningContainers();
            removeContainers(ContainerNames.HYPERFOIL.name);
            if (!inContainer) {
                removeContainers(appJfr.runtimeContainer.name, appNoJfr.runtimeContainer.name);
            } else{
                removeContainers(ContainerNames.JFR_PERFORMANCE_BUILDER_IMAGE.name, ContainerNames.JFR_PLAINTEXT_BUILDER_IMAGE.name);
            }
            enableTurbo();
            if (patch != null) {
                runCommand(getRunCommand("git", "apply", "-R", patch), appDir);
            }
        }
    }

    private static long getMeasurementDiff(String measurement, Map<String, Integer> measurementsJfr, Map<String, Integer> measurementsNoJfr) {
        if (measurementsJfr.get(measurement) == 0 || measurementsNoJfr.get(measurement) == 0) {
            LOGGER.error(measurement + " should not be 0! JFR: " + measurementsJfr.get(measurement) + " No JFR: " + measurementsNoJfr.get(measurement));
            return -1;
        } else {
            return (long) (Math.abs(measurementsJfr.get(measurement) - measurementsNoJfr.get(measurement)) * 100.0 / measurementsNoJfr.get(measurement));
        }
    }

    private void startComparisonForBenchmark(Endpoint endpoint, boolean checkThresholds, File processLog, String cn, String mn,
                                             StringBuilder report, Path measurementsLog, File appDir, Apps appJfr, Apps appNoJfr,
                                             boolean inContainer) throws IOException, InterruptedException {

        final Map<String, Integer> measurementsJfr = runBenchmarkForApp(endpoint, 5, appJfr, appDir, processLog,
                cn, mn, report, measurementsLog, inContainer, "jfr-perf-runner");
        final Map<String, Integer> measurementsNoJfr = runBenchmarkForApp(endpoint, 5, appNoJfr, appDir, processLog,
                cn, mn, report, measurementsLog, inContainer,  "jfr-plaintext-runner");

        LOGGER.info("JFR measurementsJfr records: " + measurementsJfr.size() + ", measurementsNoJfr records: " + measurementsNoJfr.size());
        long imageSizeDiff = getMeasurementDiff("imageSize", measurementsJfr, measurementsNoJfr);
        long timeToFirstOKRequestMsDiff = getMeasurementDiff("startup", measurementsJfr, measurementsNoJfr);
        long rssKbDiff = getMeasurementDiff("rss", measurementsJfr, measurementsNoJfr);
        long meanResponseTimeDiff = getMeasurementDiff("mean", measurementsJfr, measurementsNoJfr);
        long maxResponseTimeDiff = getMeasurementDiff("max", measurementsJfr, measurementsNoJfr);
        long responseTime50PercentileDiff = getMeasurementDiff("p50", measurementsJfr, measurementsNoJfr);
        long responseTime90PercentileDiff = getMeasurementDiff("p90", measurementsJfr, measurementsNoJfr);
        long responseTime99PercentileDiff = getMeasurementDiff("p99", measurementsJfr, measurementsNoJfr);

        final LogBuilder logBuilder = new LogBuilder();
        final LogBuilder.Log log = logBuilder.app(appJfr)
                .executableSizeKb(imageSizeDiff)
                .timeToFirstOKRequestMs(timeToFirstOKRequestMsDiff)
                .rssKb(rssKbDiff)
                .meanResponseTime(meanResponseTimeDiff)
                .maxResponseTime(maxResponseTimeDiff)
                .responseTime50Percentile(responseTime50PercentileDiff)
                .responseTime90Percentile(responseTime90PercentileDiff)
                .responseTime99Percentile(responseTime99PercentileDiff)
                .build();
        Logs.logMeasurements(log, measurementsLog);
        Logs.appendln(report, "These values represent the % difference as calculated (measurement_JFR - measurement_no_JFR)/measurement_no_JFR");
        Logs.appendln(report, endpoint + " Measurements Diff %:");
        Logs.appendln(report, log.headerMarkdown + "\n" + log.lineMarkdown);

        final Path recording = Paths.get(appDir.getAbsolutePath(), "logs", endpoint + "-" + appJfr.name().toLowerCase() + "-flight-native.jfr");
        if (Files.exists(recording)) {
            LOGGER.info("Processing JFR events from " + recording);
            final long jfrRequestCount = measurementsJfr.get("requestCount");
            final long jfrResponseCount = measurementsJfr.get("responseCount");
            final long jfrRequestTimeouts = measurementsJfr.get("requestTimeouts");
            final long jfrInternalErrors = measurementsJfr.get("internalErrors");
            final long jdkThreadParkEvents = countJFREvents(inContainer, "jdk.ThreadPark", recording, processLog, report);
            final long parkedClassGreetingService = countJFREventMatches(inContainer, "jdk.ThreadPark",
                    Pattern.compile("^[\\s\\t]*parkedClass = org\\.acme\\.getting\\.started\\.GreetingService[\\s\\t]+.*[\\n\\r]*"), recording, processLog, report);
            LOGGER.info("JFR file results for endpoint " + endpoint + ": \n" +
                    "jfrRequestCount: " + jfrRequestCount + "\n" +
                    "jfrResponseCount: " + jfrResponseCount + "\n" +
                    "jfrRequestTimeouts: " + jfrRequestTimeouts + "\n" +
                    "jfrInternalErrors: " + jfrInternalErrors + "\n" +
                    "jdkThreadParkEvents: " + jdkThreadParkEvents + "\n" +
                    "parkedClassGreetingService: " + parkedClassGreetingService);

            assertEquals(0, jfrInternalErrors, "The test app is not expected to return any errors " +
                    "during the Hyperfoil run.");
            assertEquals(jfrRequestCount, jfrResponseCount, "The number of requests: " + jfrRequestCount +
                    " is expected to be the same as the number of responses: " + jfrResponseCount);
            assertEquals(0, jfrRequestTimeouts, "The test app is not expected to return any timeouts " +
                    "during the Hyperfoil run.");
            assertTrue(jdkThreadParkEvents > parkedClassGreetingService, "There must have been more " +
                    "jdk.ThreadPark events in general than those specific to GreetingService class.");
            if (endpoint == Endpoint.REGULAR) {
                assertEquals(jfrRequestCount, parkedClassGreetingService,
                        "In the " + Endpoint.REGULAR + " case, the number of requests " + jfrRequestCount +
                                " is expected to match the total " +
                                "amount of jdk.ThreadPark events in GreetingService class: " + parkedClassGreetingService);
            } else {
                assertEquals(jfrRequestCount * 1000, parkedClassGreetingService,
                        "In the " + Endpoint.WORK + " case, the number of requests " + jfrRequestCount +
                                " is expected to generate " +
                                "a thousand more amount of jdk.ThreadPark events in GreetingService class: " + parkedClassGreetingService);
            }
        }
        if (checkThresholds) {
            Logs.checkThreshold(appJfr, Logs.Mode.DIFF_NATIVE, imageSizeDiff, rssKbDiff, timeToFirstOKRequestMsDiff, meanResponseTimeDiff, responseTime50PercentileDiff, responseTime90PercentileDiff);
        }
    }

    private Map<String, Integer> runBenchmarkForApp(Endpoint endpoint, int trials, Apps app, File appDir, File processLog,
            String cn, String mn, StringBuilder report, Path measurementsLog,
            boolean inContainer, String binaryName) throws IOException, InterruptedException {

        Process process = null;
        Process hyperfoilProcess = null;
        int rssSum = 0;
        int startupSum = 0;
        final long[] tunnelPIDs = new long[] { -1L, -1L };

        try {
            for (int i = 0; i < trials; i++) {
                if (process != null) {
                    processStopper(process, true, true);
                    if (inContainer) {
                        stopAllRunningContainers();
                    }
                    assertTrue(waitForTcpClosed("localhost", 8080, 10),
                            "Quarkus app likely hanging on port 8080.");
                    assertTrue(waitForTcpClosed("localhost", 8090, 10),
                            "Hyperfoil likely hanging on port 8090.");
                }
                final List<String> cmd = getRunCommand(app.buildAndRunCmds.runCommands[0]);
                clearCaches(); //TODO consider using warm up instead of clearing caches
                Logs.appendln(report, "Trial " + i + " in " + appDir.getAbsolutePath());
                Logs.appendlnSection(report, String.join(" ", cmd));
                process = runCommand(cmd, appDir, processLog, app);
                assertNotNull(process, "The test application failed to run. Check " + getLogsDir(cn, mn) + File.separator + processLog.getName());
                startupSum += WebpageTester.testWeb(app.urlContent.urlContent[0][0], 10, app.urlContent.urlContent[0][1], true);
                if (inContainer) {
                    rssSum += getContainerMemoryKb(app.runtimeContainer.name);
                } else {
                    rssSum += getRSSkB(process.pid());
                }
                LOGGER.info("Trial " + i + " startup time sum: " + startupSum + " ms, RSS sum: " + rssSum + " KB");
            }

            if (IS_THIS_MACOS) {
                // TODO: This is obviously way too tailored to our particular macOS podman installation.
                // We will generalize it as a part of porting to GHA.
                final String identity = "/Users/tester/.local/share/containers/podman/machine/machine";
                final int port = getPodmanMachineSSHPort();
                tunnelPIDs[0] = openSSHTunnel(identity, String.valueOf(port), "core", "localhost", "8080", false);
                tunnelPIDs[1] = openSSHTunnel(identity, String.valueOf(port), "core", "localhost", "8090", true);
            }

            // Run Hyperfoil controller in container and expose port for test
            final List<String> getAndStartHyperfoil = getRunCommand(app.buildAndRunCmds.runCommands[1]);
            hyperfoilProcess = runCommand(getAndStartHyperfoil, appDir, processLog, app);
            Logs.appendln(report, appDir.getAbsolutePath());
            Logs.appendlnSection(report, String.join(" ", getAndStartHyperfoil));
            assertNotNull(hyperfoilProcess, "Hyperfoil failed to run. Check " + getLogsDir(cn, mn) + File.separator + processLog.getName());

            // Wait for Hyperfoil to download & start
            Commands.waitForContainerLogToMatch(ContainerNames.HYPERFOIL.name,
                    Pattern.compile(".*Hyperfoil controller listening.*", Pattern.DOTALL), 600, 5, TimeUnit.SECONDS);
            // Wait for Hyperfoil to open endpoint
            WebpageTester.testWeb(app.urlContent.urlContent[2][0], 15, app.urlContent.urlContent[2][1], false);

            // Upload the benchmark
            final HttpClient hc = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build();
            HyperfoilHelper.uploadBenchmark(app, appDir, app.urlContent.urlContent[1][0], hc);

            // Run the benchmark
            disableTurbo();
            final HttpRequest benchmarkRequest = HttpRequest.newBuilder()
                    .uri(new URI(app.urlContent.urlContent[3][0] + "?templateParam=ENDPOINT=" + endpoint))
                    .GET()
                    .build();
            final HttpResponse<String> benchmarkResponse = hc.send(benchmarkRequest, HttpResponse.BodyHandlers.ofString());
            final JSONObject benchmarkResponseJson = new JSONObject(benchmarkResponse.body());
            final String id = benchmarkResponseJson.getString("id");

            // Wait for benchmark to complete
            Commands.waitForContainerLogToMatch(ContainerNames.HYPERFOIL.name,
                    Pattern.compile(".*Successfully persisted run.*", Pattern.DOTALL), 30, 2, TimeUnit.SECONDS);
            enableTurbo();

            // Get the results
            final HttpRequest resultsRequest = HttpRequest.newBuilder()
                    .uri(new URI("http://localhost:8090/run/" + id + "/stats/all/json"))
                    .GET()
                    .timeout(Duration.ofSeconds(3)) // set timeout to allow for cleanup, otherwise will stall at first request above
                    .build();
            final HttpResponse<String> resultsResponse = hc.send(resultsRequest, HttpResponse.BodyHandlers.ofString());
            LOGGER.info("Hyperfoil results response code " + resultsResponse.statusCode());
            final JSONObject resultsResponseJson = new JSONObject(resultsResponse.body());

            // Persist the benchmark result in case a human needs to see it.
            Files.writeString(Paths.get(appDir.getAbsolutePath(), "logs", endpoint + "-" + app.name().toLowerCase() + "-hyperfoil-result.json"),
                    resultsResponseJson.toString(2), StandardOpenOption.CREATE_NEW);

            // Get image size in KB, safe to be within int.
            final int imageSizeKB = (int) (findExecutable(Path.of(appDir.getAbsolutePath(), "target"),
                    Pattern.compile(".*" + binaryName)).length() / 1024L);
            LOGGER.info(app.name() + " image size " + imageSizeKB + " KB");

            // Parse JSON response from Hyperfoil controller server
            final Map<String, Integer> measurements = new HashMap<>();
            measurements.put("mean", resultsResponseJson.getJSONArray("stats").getJSONObject(0).getJSONObject("total").getJSONObject("summary").getInt("meanResponseTime"));
            measurements.put("max", resultsResponseJson.getJSONArray("stats").getJSONObject(0).getJSONObject("total").getJSONObject("summary").getInt("maxResponseTime"));
            measurements.put("p50", resultsResponseJson.getJSONArray("stats").getJSONObject(0).getJSONObject("total").getJSONObject("summary").getJSONObject("percentileResponseTime").getInt("50.0"));
            measurements.put("p90", resultsResponseJson.getJSONArray("stats").getJSONObject(0).getJSONObject("total").getJSONObject("summary").getJSONObject("percentileResponseTime").getInt("90.0"));
            measurements.put("p99", resultsResponseJson.getJSONArray("stats").getJSONObject(0).getJSONObject("total").getJSONObject("summary").getJSONObject("percentileResponseTime").getInt("99.0"));
            measurements.put("startup", startupSum / trials);
            measurements.put("rss", rssSum / trials);
            measurements.put("imageSize", imageSizeKB);
            measurements.put("requestCount", resultsResponseJson.getJSONArray("stats").getJSONObject(0).getJSONObject("total").getJSONObject("summary").getInt("requestCount"));
            measurements.put("responseCount", resultsResponseJson.getJSONArray("stats").getJSONObject(0).getJSONObject("total").getJSONObject("summary").getInt("responseCount"));
            measurements.put("requestTimeouts", resultsResponseJson.getJSONArray("stats").getJSONObject(0).getJSONObject("total").getJSONObject("summary").getInt("requestTimeouts"));
            measurements.put("internalErrors", resultsResponseJson.getJSONArray("stats").getJSONObject(0).getJSONObject("total").getJSONObject("summary").getInt("internalErrors"));

            LOGGER.info("mean:" + measurements.get("mean")
                    + ", max:" + measurements.get("max")
                    + ", p50:" + measurements.get("p50")
                    + ", p90:" + measurements.get("p90")
                    + ", p99:" + measurements.get("p99")
                    + ", startup:" + measurements.get("startup")
                    + ", rss:" + measurements.get("rss")
                    + ", imageSize:" + measurements.get("imageSize")
                    + ", requestCount:" + measurements.get("requestCount")
                    + ", responseCount:" + measurements.get("responseCount")
                    + ", requestTimeouts:" + measurements.get("requestTimeouts")
                    + ", internalErrors:" + measurements.get("internalErrors")
            );

            final LogBuilder logBuilder = new LogBuilder();
            final LogBuilder.Log log = logBuilder.app(app)
                    .executableSizeKb(imageSizeKB)
                    .timeToFirstOKRequestMs(measurements.get("startup"))
                    .rssKb(measurements.get("rss"))
                    .meanResponseTime(measurements.get("mean"))
                    .maxResponseTime(measurements.get("max"))
                    .responseTime50Percentile(measurements.get("p50"))
                    .responseTime90Percentile(measurements.get("p90"))
                    .responseTime99Percentile(measurements.get("p99"))
                    .build();
            Logs.logMeasurements(log, measurementsLog);
            Logs.appendln(report, endpoint + " Measurements " + app.name() + ":");
            Logs.appendln(report, log.headerMarkdown + "\n" + log.lineMarkdown);
            return measurements;
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        } finally {
            // Stop container before stopping Hyperfoil process
            stopAllRunningContainers();
            if (process != null && process.isAlive()) {
                processStopper(process, true);
            }
            removeContainers(ContainerNames.HYPERFOIL.name);
            if (hyperfoilProcess != null && hyperfoilProcess.isAlive()) {
                processStopper(hyperfoilProcess, true);
            }
            // Store the JFR recording
            final Path recording = Paths.get(appDir.getAbsolutePath(), "logs", "flight-native.jfr");
            if (Files.exists(recording)) {
                Files.move(recording,
                        Paths.get(appDir.getAbsolutePath(), "logs", endpoint + "-" + app.name().toLowerCase() + "-flight-native.jfr"));
            }
            // Bury the tunnels
            if (IS_THIS_MACOS) {
                for (long pid : tunnelPIDs) {
                    if (pid != -1)
                        pidKiller(pid, true);
                }
            }
        }
    }

    private void generateJFRConfigurationFile(boolean inContainer, Path jfrPerfJfc, File logFile) throws IOException {
        final List<String> command;
        if (inContainer) {
            command = getRunCommand(CONTAINER_RUNTIME, "run", IS_THIS_WINDOWS ? "" : "-u", IS_THIS_WINDOWS ? "" : getUnixUIDGID(),
                    "-t", "--entrypoint", "jfr", "-v", jfrPerfJfc.getParent().toString() + ":/project:z",
                    BUILDER_IMAGE,
                    "configure", "--input", "profile.jfc", "method-profiling=max", "jdk.ThreadPark#threshold=0ns", "--output", "./" + jfrPerfJfc.getFileName().toString());
        } else {
            command = getRunCommand("jfr", "configure", "method-profiling=max", "jdk.ThreadPark#threshold=0ns", "--output", jfrPerfJfc.toString());
        }
        final String c = "Command: " + String.join(" ", command) + "\n";
        LOGGER.infof("Command: %s", command);
        Files.write(logFile.toPath(), c.getBytes(StandardCharsets.UTF_8), StandardOpenOption.APPEND, StandardOpenOption.CREATE);
        runCommand(command);
        assertTrue(Files.exists(jfrPerfJfc), "The JFR config file " + jfrPerfJfc + " MUST exist at this point in time.");
    }

    /**
     * @param inContainer     Whether to use jfr tool from a container
     * @param eventType       e.g. jdk.ThreadPark
     * @param flightRecording e.g. path to your flight-native.jfr
     * @param logFile         e.g. path to the build and run log
     * @return parsed Count from JFR file summary for the particular event
     * @throws IOException
     * @throws InterruptedException
     */
    private long countJFREvents(boolean inContainer, String eventType, Path flightRecording, File logFile, StringBuilder report) throws IOException, InterruptedException {
        assertTrue(Files.exists(flightRecording), "The flight record file " + flightRecording + " MUST exist at this point in time.");
        final Pattern pattern = Pattern.compile("^[\\s\\t]*" + eventType + "[\\s\\t]+([0-9]+)[\\s\\t]+.*[\\n\\r]*");
        final List<String> cmd;
        if (inContainer) {
            cmd = getRunCommand(CONTAINER_RUNTIME, "run", IS_THIS_WINDOWS ? "" : "-u", IS_THIS_WINDOWS ? "" : getUnixUIDGID(),
                    "-t", "--entrypoint", "jfr", "-v", flightRecording.getParent().toString() + ":/project:z",
                    BUILDER_IMAGE,
                    "summary", "./" + flightRecording.getFileName().toString());
        } else {
            cmd = getRunCommand("jfr", "summary", flightRecording.toString());
        }
        final String command = "Command: " + String.join(" ", cmd) + "\n";
        Logs.appendlnSection(report, command);
        LOGGER.infof("Command: %s", cmd);
        Files.writeString(logFile.toPath(), command, StandardOpenOption.APPEND, StandardOpenOption.CREATE);
        final Process p = Commands.runCommand(cmd, new File("."), null, null);
        // UTF-8 on Windows might work poorly with colours in terminal.
        try (BufferedReader processOutputReader =
                     new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String l;
            while ((l = processOutputReader.readLine()) != null) {
                final Matcher m = pattern.matcher(l);
                if (m.matches()) {
                    return Long.parseLong(m.group(1));
                }
            }
            p.waitFor(5, TimeUnit.MINUTES);
        }
        LOGGER.error("None of the output lines matched regexp: " + pattern.pattern());
        return -1L;
    }

    private long countJFREventMatches(boolean inContainer, String eventType, Pattern regexp, Path flightRecording, File logFile, StringBuilder report) throws IOException, InterruptedException {
        assertTrue(Files.exists(flightRecording), "The flight record file " + flightRecording + " MUST exist at this point in time.");
        final List<String> cmd;
        if (inContainer) {
            cmd = getRunCommand(CONTAINER_RUNTIME, "run", IS_THIS_WINDOWS ? "" : "-u", IS_THIS_WINDOWS ? "" : getUnixUIDGID(),
                    "-t", "--entrypoint", "jfr", "-v", flightRecording.getParent().toString() + ":/project:z",
                    BUILDER_IMAGE,
                    "print", "--events", eventType, "./" + flightRecording.getFileName().toString());
        } else {
            cmd = getRunCommand("jfr", "print", "--events", eventType, flightRecording.toString());
        }
        final String command = "Command: " + String.join(" ", cmd) + "\n";
        Logs.appendlnSection(report, command);
        LOGGER.infof("Command: %s", cmd);
        Files.writeString(logFile.toPath(), command, StandardOpenOption.APPEND, StandardOpenOption.CREATE);
        // logFile is null, the output could easily be hundreds of megabytes, we log just the start manually here
        final Process p = Commands.runCommand(cmd, new File("."), null, null);
        long c = 0;
        int i = 0;
        long start = System.currentTimeMillis();
        final StringBuilder s = new StringBuilder();
        // UTF-8 on Windows might work poorly with colours in terminal.
        try (BufferedReader processOutputReader =
                     new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String l;
            // TODO: This is kind of naive. There could be hundreds of thousands of matches and it would be better to do big chunks.
            while ((l = processOutputReader.readLine()) != null) {
                if (i < 100) {
                    s.append(l);
                    s.append("\n");
                    i++;
                }
                if (regexp.matcher(l).matches()) {
                    c++;
                }
            }
            Files.writeString(logFile.toPath(), "First " + i + " lines of output: " + s, StandardOpenOption.APPEND, StandardOpenOption.CREATE);
            p.waitFor(5, TimeUnit.MINUTES);
        }
        LOGGER.info("Found " + c + " matches in " + (System.currentTimeMillis() - start) + " ms.");
        return c;
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
            // In this case, four commands are used to run the app, JVM, JVM JFR, Native, Native JFR
            builderRoutine(app, report, cn, mn, appDir, processLog, null, switches);

            final File inputData = Path.of(BASE_DIR, app.dir, "target", "test_data.txt").toFile();

            LOGGER.info("Running JVM mode...");
            long start = System.currentTimeMillis();
            List<String> cmd = getRunCommand(app.buildAndRunCmds.runCommands[0]);
            process = runCommand(cmd, appDir, processLog, app, inputData);
            assertNotNull(process, "The test application failed to run. Check " + getLogsDir(cn, mn) + File.separator + processLog.getName());
            process.waitFor(30, TimeUnit.SECONDS);
            long jvmRunTookMs = System.currentTimeMillis() - start;
            Logs.appendln(report, appDir.getAbsolutePath());
            Logs.appendlnSection(report, String.join(" ", cmd));

            LOGGER.info("Running JVM JFR mode...");
            start = System.currentTimeMillis();
            cmd = getRunCommand(app.buildAndRunCmds.runCommands[1]);
            if (UsedVersion.jdkFeature(inContainer) >= 17) {
                cmd = replaceSwitchesInCmd(cmd, Map.of(JFR_FLIGHT_RECORDER_HOTSPOT_TOKEN, JFROption.HOTSPOT_17_FLIGHT_RECORDER.replacement));
            } else {
                cmd = replaceSwitchesInCmd(cmd, Map.of(JFR_FLIGHT_RECORDER_HOTSPOT_TOKEN, JFROption.HOTSPOT_11_FLIGHT_RECORDER.replacement));
            }
            process = runCommand(cmd, appDir, processLog, app, inputData);
            assertNotNull(process, "The test application failed to run. Check " + getLogsDir(cn, mn) + File.separator + processLog.getName());
            process.waitFor(30, TimeUnit.SECONDS);
            long jvmJfrRunTookMs = System.currentTimeMillis() - start;
            Logs.appendln(report, appDir.getAbsolutePath());
            Logs.appendlnSection(report, String.join(" ", cmd));

            LOGGER.info("Running Native mode...");
            start = System.currentTimeMillis();
            cmd = getRunCommand(app.buildAndRunCmds.runCommands[2]);
            process = runCommand(cmd, appDir, processLog, app, inputData);
            assertNotNull(process, "The test application failed to run. Check " + getLogsDir(cn, mn) + File.separator + processLog.getName());
            process.waitFor(30, TimeUnit.SECONDS);
            long nativeRunTookMs = System.currentTimeMillis() - start;
            Logs.appendln(report, appDir.getAbsolutePath());
            Logs.appendlnSection(report, String.join(" ", cmd));

            LOGGER.info("Running Native mode JFR...");
            start = System.currentTimeMillis();
            cmd = getRunCommand(app.buildAndRunCmds.runCommands[3]);
            process = runCommand(cmd, appDir, processLog, app, inputData);
            assertNotNull(process, "The test application failed to run. Check " + getLogsDir(cn, mn) + File.separator + processLog.getName());
            process.waitFor(30, TimeUnit.SECONDS);
            long nativeJfrRunTookMs = System.currentTimeMillis() - start;
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

            assertEquals(4, count, "There were four same hashes " + magicHash + " expected in the log. " +
                    "One from JVM run, one from JVM run with JFR, one for Native image run and one for Native image run with JFR. " +
                    "" + count + " such hashes were found. Check build-and-run.log and report.md.");

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
            LogBuilder.Log logJVMJFR = new LogBuilder()
                    .app(app + "_JVM_JFR")
                    .timeToFinishMs(jvmJfrRunTookMs)
                    .build();
            LogBuilder.Log logNativeJFR = new LogBuilder()
                    .app(app + "_NATIVE_JFR")
                    .timeToFinishMs(nativeJfrRunTookMs)
                    .build();
            Logs.logMeasurements(logJVM, measurementsLog);
            Logs.logMeasurements(logNative, measurementsLog);
            Logs.logMeasurements(logJVMJFR, measurementsLog);
            Logs.logMeasurements(logNativeJFR, measurementsLog);
            Logs.appendln(report, "Measurements:");
            Logs.appendln(report, logJVM.headerMarkdown + "\n" + logJVM.lineMarkdown);
            Logs.appendln(report, logNative.lineMarkdown);
            Logs.appendln(report, logJVMJFR.headerMarkdown + "\n" + logJVMJFR.lineMarkdown);
            Logs.appendln(report, logNativeJFR.lineMarkdown);

            Logs.checkThreshold(app, Logs.Mode.JVM, Logs.SKIP, Logs.SKIP, Logs.SKIP, jvmRunTookMs);
            Logs.checkThreshold(app, Logs.Mode.NATIVE, Logs.SKIP, Logs.SKIP, Logs.SKIP, nativeRunTookMs);

            long jvmRunTookMsDiff = (long) (Math.abs(jvmJfrRunTookMs - jvmRunTookMs) * 100.0 / jvmRunTookMs);
            long nativeRunTookMsDiff = (long) (Math.abs(nativeJfrRunTookMs - nativeRunTookMs) * 100.0 / nativeRunTookMs);

            Logs.checkThreshold(app, Logs.Mode.DIFF_JVM, Logs.SKIP, Logs.SKIP, Logs.SKIP, jvmRunTookMsDiff);
            Logs.checkThreshold(app, Logs.Mode.DIFF_NATIVE, Logs.SKIP, Logs.SKIP, Logs.SKIP, nativeRunTookMsDiff);
        } finally {
            cleanup(process, cn, mn, report, app, processLog);
            if (app.runtimeContainer != ContainerNames.NONE) {
                stopAllRunningContainers();
                removeContainers(
                        app.runtimeContainer.name,
                        app.runtimeContainer.name + "-build",
                        app.runtimeContainer.name + "-run-java",
                        app.runtimeContainer.name + "-run-java-jfr");
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
            builderRoutine(app, report, cn, mn, appDir, processLog, null, switches);

            final Map<String[], Pattern> cmdOutput = new HashMap<>();
            cmdOutput.put(new String[]{"./target/timezones",
                            "-XX:+FlightRecorder",
                            "-XX:StartFlightRecording=maxsize=10000,filename=logs/flight-native.jfr",
                            "-XX:FlightRecorderLogging=jfr"},
                    Pattern.compile(".* Started recording .* \\{maxsize=9.8kB.*", Pattern.DOTALL));
            cmdOutput.put(new String[]{"./target/timezones",
                            "-XX:+FlightRecorder",
                            "-XX:StartFlightRecording=maxsize=10000,filename=logs/flight-native.jfr",
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
                    Files.writeString(processLog.toPath(), Files.readString(interimLog) + "\n", StandardOpenOption.APPEND, StandardOpenOption.CREATE);
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
