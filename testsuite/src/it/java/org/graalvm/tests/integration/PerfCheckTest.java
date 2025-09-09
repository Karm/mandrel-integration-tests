/*
 * Copyright (c) 2022, Red Hat Inc. All rights reserved.
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

import com.sun.management.OperatingSystemMXBean;
import org.graalvm.home.Version;
import org.graalvm.tests.integration.utils.Apps;
import org.graalvm.tests.integration.utils.Commands;
import org.graalvm.tests.integration.utils.ContainerNames;
import org.graalvm.tests.integration.utils.HyperfoilHelper;
import org.graalvm.tests.integration.utils.Logs;
import org.graalvm.tests.integration.utils.WebpageTester;
import org.graalvm.tests.integration.utils.thresholds.Thresholds;
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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.net.HttpURLConnection.HTTP_OK;
import static org.graalvm.tests.integration.AppReproducersTest.BASE_DIR;
import static org.graalvm.tests.integration.utils.Commands.ARCH;
import static org.graalvm.tests.integration.utils.Commands.GRAALVM_BUILD_OUTPUT_JSON_FILE;
import static org.graalvm.tests.integration.utils.Commands.GRAALVM_BUILD_OUTPUT_JSON_FILE_SWITCH;
import static org.graalvm.tests.integration.utils.Commands.GRAALVM_EXPERIMENTAL_BEGIN;
import static org.graalvm.tests.integration.utils.Commands.GRAALVM_EXPERIMENTAL_END;
import static org.graalvm.tests.integration.utils.Commands.QUARKUS_VERSION;
import static org.graalvm.tests.integration.utils.Commands.builderRoutine;
import static org.graalvm.tests.integration.utils.Commands.cleanTarget;
import static org.graalvm.tests.integration.utils.Commands.disableTurbo;
import static org.graalvm.tests.integration.utils.Commands.enableTurbo;
import static org.graalvm.tests.integration.utils.Commands.findExecutable;
import static org.graalvm.tests.integration.utils.Commands.findFiles;
import static org.graalvm.tests.integration.utils.Commands.getProperty;
import static org.graalvm.tests.integration.utils.Commands.getRSSkB;
import static org.graalvm.tests.integration.utils.Commands.getRunCommand;
import static org.graalvm.tests.integration.utils.Commands.mapToJSON;
import static org.graalvm.tests.integration.utils.Commands.parsePerfRecord;
import static org.graalvm.tests.integration.utils.Commands.parsePort;
import static org.graalvm.tests.integration.utils.Commands.parseSerialGCLog;
import static org.graalvm.tests.integration.utils.Commands.processStopper;
import static org.graalvm.tests.integration.utils.Commands.removeContainer;
import static org.graalvm.tests.integration.utils.Commands.runCommand;
import static org.graalvm.tests.integration.utils.Commands.runJaegerContainer;
import static org.graalvm.tests.integration.utils.Commands.waitForFileToMatch;
import static org.graalvm.tests.integration.utils.Commands.waitForTcpClosed;
import static org.graalvm.tests.integration.utils.Logs.getLogsDir;
import static org.graalvm.tests.integration.utils.Uploader.PERF_APP_REPORT;
import static org.graalvm.tests.integration.utils.Uploader.postBuildtimePayload;
import static org.graalvm.tests.integration.utils.Uploader.postRuntimePayload;
import static org.graalvm.tests.integration.utils.versions.UsedVersion.getVersion;
import static org.jboss.resteasy.spi.HttpResponseCodes.SC_ACCEPTED;
import static org.jboss.resteasy.spi.HttpResponseCodes.SC_CREATED;
import static org.jboss.resteasy.spi.HttpResponseCodes.SC_OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Michal Karm Babacek <karm@redhat.com>
 */
@Tag("perfcheck")
// Windows: We need to replace perf with wmic & Dr.Memory or something.
// Mac: We need to figure out what's Mac's "perf".
@DisabledOnOs({ OS.WINDOWS, OS.MAC })
public class PerfCheckTest {

    private static final Logger LOGGER = Logger.getLogger(PerfCheckTest.class.getName());

    public static final int LIGHT_REQUESTS = Integer.parseInt(getProperty("PERFCHECK_TEST_LIGHT_REQUESTS", "100"));
    public static final int HEAVY_REQUESTS = Integer.parseInt(getProperty("PERFCHECK_TEST_HEAVY_REQUESTS", "2"));
    public static final int MX_HEAP_MB = Integer.parseInt(getProperty("PERFCHECK_TEST_REQUESTS_MX_HEAP_MB", "2560"));

    // Making the heap smaller for GC tests
    public static final int GC_HEAP_MB = Integer.parseInt(getProperty("GC_TEST_HEAP_MB", "64"));

    // Build time constraint
    public static final int NATIVE_IMAGE_XMX_GB = Integer.parseInt(getProperty("PERFCHECK_TEST_NATIVE_IMAGE_XMX_GB", "8"));

    public static final String FINAL_NAME_TOKEN = "<FINAL_NAME>";

    // Reporting
    public static final String APP_RUNTIME_CONTEXT = "api/v1/perfstats/perf";
    public static final String APP_BUILDTIME_CONTEXT = "api/v1/image-stats";

    public static Map<String, String> populateHeader(Map<String, String> report) {
        report.put("arch", getProperty("perf.app.arch", ARCH));
        report.put("os", getProperty("perf.app.os", System.getProperty("os.name")));
        report.put("quarkusVersion", QUARKUS_VERSION.isSnapshot() ?
                QUARKUS_VERSION.getGitSHA() + '.' + QUARKUS_VERSION.getVersionString() : QUARKUS_VERSION.getVersionString());
        report.put("mandrelVersion", getVersion(false).toString());
        report.put("jdkVersion", String.format("%s.%s.%s", UsedVersion.jdkFeature(false),
                UsedVersion.jdkInterim(false), UsedVersion.jdkUpdate(false)));
        report.put("ramAvailableMB", Long.toString(
                ((OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean()).getFreePhysicalMemorySize()
                        / 1024 / 1024));
        report.put("coresAvailable", Integer.toString(
                ManagementFactory.getOperatingSystemMXBean().getAvailableProcessors()));
        report.put("runnerDescription",
                getProperty("PERF_APP_RUNNER_DESCRIPTION", ""));
        report.put("testApp", "https://github.com/Karm/mandrel-integration-tests/apps/quarkus-json/");
        report.put("maxHeapSizeMB", String.valueOf(MX_HEAP_MB));
        return report;
    }

    @Test
    @IfMandrelVersion(min = "21.3", max = "23.999")
    public void testQuarkusJSONParseOnce(TestInfo testInfo) throws IOException, InterruptedException, URISyntaxException {
        final Apps app = Apps.QUARKUS_JSON_PERF_PARSEONCE;
        LOGGER.info("Testing app: " + app);
        Process process = null;
        final File appDir = Path.of(BASE_DIR, app.dir).toFile();
        final File processLog = Path.of(appDir.getAbsolutePath(), "logs", "build-and-run.log").toFile();
        final String cn = testInfo.getTestClass().get().getCanonicalName();
        final String mn = testInfo.getTestMethod().get().getName();
        final List<Map<String, String>> reports = new ArrayList<>(3);
        // Test data tmp storage
        final File json = Path.of(appDir.getAbsolutePath(), "logs", "record.json").toFile();
        String patch = null;

        try {
            // Cleanup
            cleanTarget(app);
            Files.createDirectories(Paths.get(appDir.getAbsolutePath(), "logs"));

            if (QUARKUS_VERSION.compareTo(QuarkusVersion.V_3_9_0) >= 0) {
                patch = "quarkus_3.9.x.patch";
            } else if (QUARKUS_VERSION.compareTo(QuarkusVersion.V_3_0_0) >= 0) {
                patch = "quarkus_3.x.patch";
            }

            if (patch != null) {
                runCommand(getRunCommand("git", "apply", patch), appDir);
            }

            // Build executables
            builderRoutine(app, null, null, null, appDir, processLog, null, getSwitches1());
            assertTrue(processLog.exists());

            int line = 0;
            for (int i = 0; i < app.buildAndRunCmds.runCommands.length; i++) {
                final Map<String, String> report = populateHeader(new TreeMap<>());
                final List<String> cmd = getRunCommand(app.buildAndRunCmds.runCommands[i]);
                Files.writeString(processLog.toPath(), String.join(" ", cmd) + '\n', StandardOpenOption.APPEND, StandardOpenOption.CREATE);
                process = runCommand(cmd, appDir, processLog, app);
                line = waitForFileToMatch(Pattern.compile(".*Events enabled.*"), processLog.toPath(), line, 20, 1, TimeUnit.SECONDS);
                final long timeToFirstOKRequestMs = WebpageTester.testWeb(app.urlContent.urlContent[0][0], 10, app.urlContent.urlContent[0][1], true);
                report.put("timeToFirstOKRequestMs", String.valueOf(timeToFirstOKRequestMs));
                // Test web pages
                try (final ReadableByteChannel readableByteChannel = Channels.newChannel(new URL(app.urlContent.urlContent[1][0]).openStream());
                        final FileOutputStream fileOutputStream = new FileOutputStream(json)) {
                    fileOutputStream.getChannel().transferFrom(readableByteChannel, 0, Long.MAX_VALUE);
                }
                final String[] headers = new String[] {
                        "Content-Type", "application/json",
                        "Accept", "text/plain"
                };
                final HttpRequest releaseRequest = HttpRequest.newBuilder()
                        .method("POST", HttpRequest.BodyPublishers.ofFile(json.toPath()))
                        .version(HttpClient.Version.HTTP_1_1)
                        //[3][0] - uses sha-256, [2][0] just deserialization
                        .uri(new URI(app.urlContent.urlContent[3][0]))
                        .headers(headers)
                        .build();
                final HttpClient hc = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build();
                for (int j = 0; j < HEAVY_REQUESTS; j++) {
                    final HttpResponse<String> releaseResponse = hc.send(releaseRequest, HttpResponse.BodyHandlers.ofString());
                    System.out.print(".");
                    assertEquals(200, releaseResponse.statusCode(), "App returned a non HTTP 200 response. The perf report is invalid.");
                }
                System.out.println();
                report.put("rssKb", Long.toString(getRSSkB(process.children().sorted().findFirst().get().pid())));
                processStopper(process, false, true);
                final String statsFor = Arrays.stream(app.buildAndRunCmds.runCommands[i])
                        // skipping first 4 perf tool conf
                        .skip(4).collect(Collectors.joining(" ")).trim();
                waitForFileToMatch(Pattern.compile(".*Performance counter stats for\\s+'\\Q" + statsFor + "\\E':.*"), processLog.toPath(), 0, 5, 1, TimeUnit.SECONDS);
                final Commands.PerfRecord pr = parsePerfRecord(processLog.toPath(), statsFor);
                report.put("file", statsFor);
                report.put("taskClock", String.valueOf(pr.taskClock));
                report.put("contextSwitches", String.valueOf(pr.contextSwitches));
                report.put("cpuMigrations", String.valueOf(pr.cpuMigrations));
                report.put("pageFaults", String.valueOf(pr.pageFaults));
                report.put("cycles", String.valueOf(pr.cycles));
                report.put("instructions", String.valueOf(pr.instructions));
                report.put("branches", String.valueOf(pr.branches));
                report.put("branchMisses", String.valueOf(pr.branchMisses));
                report.put("secondsTimeElapsed", String.valueOf(pr.secondsTimeElapsed));
                assertTrue(waitForTcpClosed("localhost", parsePort(app.urlContent.urlContent[0][0]), 60),
                        "Main port is still open");
                final Commands.SerialGCLog l;
                if (!statsFor.contains("-jar")) {
                    long executableSizeKb = Files.size(Path.of(appDir.getAbsolutePath(), statsFor.split(" ")[0])) / 1024L;
                    report.put("executableSizeKb", String.valueOf(executableSizeKb));
                    report.put("parseOnce", statsFor.contains("+ParseOnce") ? "true" : "false");
                    l = parseSerialGCLog(processLog.toPath(), statsFor, false);
                    report.put("incrementalGCevents", String.valueOf(l.incrementalGCevents));
                    report.put("fullGCevents", String.valueOf(l.fullGCevents));
                } else {
                    l = parseSerialGCLog(processLog.toPath(), statsFor, true);
                    report.put("incrementalGCevents", "-1");
                    report.put("fullGCevents", "-1");
                    report.put("executableSizeKb", "-1");
                    report.put("parseOnce", "null");
                }
                report.put("timeSpentInGCs", String.valueOf(l.timeSpentInGCs));
                report.put("testMethod", cn + "#" + mn);
                report.put("requestsExecuted", String.valueOf(HEAVY_REQUESTS));
                reports.add(report);
            }
            final String reportPayload = mapToJSON(reports);
            LOGGER.info(reportPayload);
            if (PERF_APP_REPORT) {
                final HttpResponse<String> response = postRuntimePayload(APP_RUNTIME_CONTEXT, reportPayload);
                if (response != null) {
                    LOGGER.info("Response code:" + response.statusCode());
                    LOGGER.info("Response body:" + response.body());
                    if (response.statusCode() != SC_CREATED) {
                        LOGGER.error("Payload was NOT uploaded tot the collector server!");
                    }
                }
            }
            LOGGER.info("Gonna wait for ports closed...");
            assertTrue(waitForTcpClosed("localhost", parsePort(app.urlContent.urlContent[0][0]), 60),
                    "Main port is still open");
            Logs.checkLog(cn, mn, app, processLog);
        } finally {
            Files.deleteIfExists(json.toPath());
            if (process != null) {
                processStopper(process, true);
            }
            Logs.archiveLog(cn, mn, Path.of(appDir.getAbsolutePath(), "target",
                    "quarkus-json_-ParseOnce-native-image-source-jar", "quarkus-json_minus-ParseOnce.json").toFile());
            Logs.archiveLog(cn, mn, Path.of(appDir.getAbsolutePath(), "target",
                    "quarkus-json_+ParseOnce-native-image-source-jar", "quarkus-json_plus-ParseOnce.json").toFile());
            Logs.archiveLog(cn, mn, processLog);
            Logs.archiveLog(cn, mn, Path.of(appDir.getAbsolutePath(), "target", "quarkus.log").toFile());
            cleanTarget(app);
            if (patch != null) {
                runCommand(getRunCommand("git", "apply", "-R", patch), appDir);
            }
        }
    }

    @Test
    @IfMandrelVersion(min = "21.3")
    public void testQuarkusJSON(TestInfo testInfo) throws IOException, InterruptedException, URISyntaxException {
        final Apps app = Apps.QUARKUS_JSON_PERF;
        LOGGER.info("Testing app: " + app);
        Process process = null;
        final File appDir = Path.of(BASE_DIR, app.dir).toFile();
        final File processLog = Path.of(appDir.getAbsolutePath(), "logs", "build-and-run.log").toFile();
        final String cn = testInfo.getTestClass().get().getCanonicalName();
        final String mn = testInfo.getTestMethod().get().getName();
        final List<Map<String, String>> reports = new ArrayList<>(2);
        // Test data tmp storage
        final File json = Path.of(appDir.getAbsolutePath(), "logs", "record.json").toFile();
        String patch = null;

        try {
            // Cleanup
            cleanTarget(app);
            Files.createDirectories(Paths.get(appDir.getAbsolutePath(), "logs"));

            if (QUARKUS_VERSION.compareTo(QuarkusVersion.V_3_9_0) >= 0) {
                patch = "quarkus_3.9.x.patch";
            } else if (QUARKUS_VERSION.compareTo(QuarkusVersion.V_3_0_0) >= 0) {
                patch = "quarkus_3.x.patch";
            }

            if (patch != null) {
                runCommand(getRunCommand("git", "apply", patch), appDir);
            }

            // Build executables
            builderRoutine(app, null, null, null, appDir, processLog, null, getSwitches2());

            int line = 0;
            for (int i = 0; i < app.buildAndRunCmds.runCommands.length; i++) {
                final Map<String, String> report = populateHeader(new TreeMap<>());
                final List<String> cmd = getRunCommand(app.buildAndRunCmds.runCommands[i]);
                Files.writeString(processLog.toPath(), String.join(" ", cmd) + '\n', StandardOpenOption.APPEND, StandardOpenOption.CREATE);
                process = runCommand(cmd, appDir, processLog, app);
                line = waitForFileToMatch(Pattern.compile(".*Events enabled.*"), processLog.toPath(), line, 20, 1, TimeUnit.SECONDS);
                final long timeToFirstOKRequestMs = WebpageTester.testWeb(app.urlContent.urlContent[0][0], 10, app.urlContent.urlContent[0][1], true);
                report.put("timeToFirstOKRequestMs", String.valueOf(timeToFirstOKRequestMs));
                // Test web pages
                try (final ReadableByteChannel readableByteChannel = Channels.newChannel(new URL(app.urlContent.urlContent[1][0]).openStream());
                        final FileOutputStream fileOutputStream = new FileOutputStream(json)) {
                    fileOutputStream.getChannel().transferFrom(readableByteChannel, 0, Long.MAX_VALUE);
                }
                final String[] headers = new String[] {
                        "Content-Type", "application/json",
                        "Accept", "text/plain"
                };
                final HttpRequest releaseRequest = HttpRequest.newBuilder()
                        .method("POST", HttpRequest.BodyPublishers.ofFile(json.toPath()))
                        .version(HttpClient.Version.HTTP_1_1)
                        //[3][0] - uses sha-256, [2][0] just deserialization
                        .uri(new URI(app.urlContent.urlContent[3][0]))
                        .headers(headers)
                        .build();
                final HttpClient hc = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build();
                for (int j = 0; j < HEAVY_REQUESTS; j++) {
                    final HttpResponse<String> releaseResponse = hc.send(releaseRequest, HttpResponse.BodyHandlers.ofString());
                    System.out.print(".");
                    assertEquals(200, releaseResponse.statusCode(), "App returned a non HTTP 200 response. The perf report is invalid.");
                }
                System.out.println();
                report.put("rssKb", Long.toString(getRSSkB(process.children().sorted().findFirst().get().pid())));
                processStopper(process, false, true);
                final String statsFor = Arrays.stream(app.buildAndRunCmds.runCommands[i])
                        // skipping first 4 perf tool conf
                        .skip(4).collect(Collectors.joining(" ")).trim();
                waitForFileToMatch(Pattern.compile(".*Performance counter stats for\\s+'\\Q" + statsFor + "\\E':.*"), processLog.toPath(), 0, 5, 1, TimeUnit.SECONDS);
                final Commands.PerfRecord pr = parsePerfRecord(processLog.toPath(), statsFor);
                report.put("file", statsFor);
                report.put("taskClock", String.valueOf(pr.taskClock));
                report.put("contextSwitches", String.valueOf(pr.contextSwitches));
                report.put("cpuMigrations", String.valueOf(pr.cpuMigrations));
                report.put("pageFaults", String.valueOf(pr.pageFaults));
                report.put("cycles", String.valueOf(pr.cycles));
                report.put("instructions", String.valueOf(pr.instructions));
                report.put("branches", String.valueOf(pr.branches));
                report.put("branchMisses", String.valueOf(pr.branchMisses));
                report.put("secondsTimeElapsed", String.valueOf(pr.secondsTimeElapsed));
                assertTrue(waitForTcpClosed("localhost", parsePort(app.urlContent.urlContent[0][0]), 60),
                        "Main port is still open");
                final Commands.SerialGCLog l;
                if (!statsFor.contains("-jar")) {
                    long executableSizeKb = Files.size(Path.of(appDir.getAbsolutePath(), statsFor.split(" ")[0])) / 1024L;
                    report.put("executableSizeKb", String.valueOf(executableSizeKb));
                    l = parseSerialGCLog(processLog.toPath(), statsFor, false);
                    report.put("incrementalGCevents", String.valueOf(l.incrementalGCevents));
                    report.put("fullGCevents", String.valueOf(l.fullGCevents));
                } else {
                    l = parseSerialGCLog(processLog.toPath(), statsFor, true);
                    report.put("incrementalGCevents", "-1");
                    report.put("fullGCevents", "-1");
                    report.put("executableSizeKb", "-1");
                    report.put("parseOnce", "null");
                }
                report.put("timeSpentInGCs", String.valueOf(l.timeSpentInGCs));
                report.put("testMethod", cn + "#" + mn);
                report.put("requestsExecuted", String.valueOf(HEAVY_REQUESTS));
                reports.add(report);
            }
            final String reportPayload = mapToJSON(reports);
            LOGGER.info(reportPayload);
            if (PERF_APP_REPORT) {
                final HttpResponse<String> response = postRuntimePayload(APP_RUNTIME_CONTEXT, reportPayload);
                if (response != null) {
                    LOGGER.info("Response code:" + response.statusCode());
                    LOGGER.info("Response body:" + response.body());
                    if (response.statusCode() != SC_CREATED) {
                        LOGGER.error("Payload was NOT uploaded tot the collector server!");
                    }
                }
            }
            LOGGER.info("Gonna wait for ports closed...");
            assertTrue(waitForTcpClosed("localhost", parsePort(app.urlContent.urlContent[0][0]), 60),
                    "Main port is still open");
            Logs.checkLog(cn, mn, app, processLog);
        } finally {
            Files.deleteIfExists(json.toPath());
            if (process != null) {
                processStopper(process, true);
            }
            Logs.archiveLog(cn, mn, Path.of(appDir.getAbsolutePath(), "target",
                    "quarkus-json-native-image-source-jar", "quarkus-json.json").toFile());
            Logs.archiveLog(cn, mn, Path.of(appDir.getAbsolutePath(), "target", "quarkus.log").toFile());
            Logs.archiveLog(cn, mn, processLog);
            cleanTarget(app);
            if (patch != null) {
                runCommand(getRunCommand("git", "apply", "-R", patch), appDir);
            }
        }
    }

    @Test
    @IfMandrelVersion(min = "21.3")
    public void testQuarkusFullMicroProfile(TestInfo testInfo) throws IOException, InterruptedException, URISyntaxException {
        final Apps app = Apps.QUARKUS_FULL_MICROPROFILE_PERF;
        LOGGER.info("Testing app: " + app);
        Process process = null;
        final File appDir = Path.of(BASE_DIR, app.dir).toFile();
        final File processLog = Path.of(appDir.getAbsolutePath(), "logs", "build-and-run.log").toFile();
        final String cn = testInfo.getTestClass().get().getCanonicalName();
        final String mn = testInfo.getTestMethod().get().getName();
        final List<Map<String, String>> reports = new ArrayList<>(2);

        String patch = null;
        if (QUARKUS_VERSION.compareTo(QuarkusVersion.V_3_9_0) >= 0) {
            patch = "quarkus_3.9.x.patch";
        } else if (QUARKUS_VERSION.compareTo(QuarkusVersion.V_3_8_0) >= 0) {
            patch = "quarkus_3.8.x.patch";
        } else if (QUARKUS_VERSION.compareTo(QuarkusVersion.V_3_2_0) >= 0) {
            patch = "quarkus_3.2.x.patch";
        }
        try {
            // Cleanup
            cleanTarget(app);
            removeContainer("quarkus_jaeger");
            Files.createDirectories(Paths.get(appDir.getAbsolutePath(), "logs"));

            if (patch != null) {
                runCommand(getRunCommand("git", "apply", patch), appDir);
            }

            // Build executables
            builderRoutine(app, null, null, null, appDir, processLog, null, getSwitches3());

            runJaegerContainer();

            int line = 0;
            for (int i = 0; i < app.buildAndRunCmds.runCommands.length; i++) {
                final Map<String, String> report = populateHeader(new TreeMap<>());
                report.replace("testApp", "https://github.com/Karm/mandrel-integration-tests/apps/quarkus-full-microprofile/");
                final List<String> cmd = getRunCommand(app.buildAndRunCmds.runCommands[i]);
                Files.writeString(processLog.toPath(), String.join(" ", cmd) + '\n', StandardOpenOption.APPEND, StandardOpenOption.CREATE);
                process = runCommand(cmd, appDir, processLog, app);
                final long timeToFirstOKRequestMs = WebpageTester.testWeb(app.urlContent.urlContent[0][0], 10, app.urlContent.urlContent[0][1], true);
                line = waitForFileToMatch(Pattern.compile(".*Events enabled.*"), processLog.toPath(), line, 20, 1, TimeUnit.SECONDS);
                report.put("timeToFirstOKRequestMs", String.valueOf(timeToFirstOKRequestMs));
                LOGGER.info("Testing web page content...");
                // Just serially iterate. No parallel clients...
                final HttpClient hc = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build();
                final List<HttpRequest> requests = new ArrayList<>();
                for (String[] urlContent : app.urlContent.urlContent) {
                    requests.add(HttpRequest.newBuilder().GET().uri(new URI(urlContent[0])).build());
                }
                for (int j = 0; j < LIGHT_REQUESTS; j++) {
                    for (HttpRequest httpRequest : requests) {
                        try {
                            if (hc.send(httpRequest, HttpResponse.BodyHandlers.ofString()).statusCode() == HTTP_OK) {
                                System.out.print('.');
                                continue;
                            }
                        } catch (IOException e) {
                            LOGGER.error("Error while testing web page content", e);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            LOGGER.error("Error while testing web page content", e);
                        }
                        System.out.print('x');
                    }
                }
                System.out.println();
                report.put("rssKb", Long.toString(getRSSkB(process.children().sorted().findFirst().get().pid())));
                processStopper(process, false, true);
                final String statsFor = Arrays.stream(app.buildAndRunCmds.runCommands[i])
                        // skipping first 2:  `perf stat'
                        .skip(2).collect(Collectors.joining(" ")).trim();
                waitForFileToMatch(Pattern.compile(".*Performance counter stats for\\s+'\\Q" + statsFor + "\\E':.*"), processLog.toPath(), 0, 5, 1, TimeUnit.SECONDS);
                final Commands.PerfRecord pr = parsePerfRecord(processLog.toPath(), statsFor);
                report.put("file", statsFor);
                report.put("taskClock", String.valueOf(pr.taskClock));
                report.put("contextSwitches", String.valueOf(pr.contextSwitches));
                report.put("cpuMigrations", String.valueOf(pr.cpuMigrations));
                report.put("pageFaults", String.valueOf(pr.pageFaults));
                report.put("cycles", String.valueOf(pr.cycles));
                report.put("instructions", String.valueOf(pr.instructions));
                report.put("branches", String.valueOf(pr.branches));
                report.put("branchMisses", String.valueOf(pr.branchMisses));
                report.put("secondsTimeElapsed", String.valueOf(pr.secondsTimeElapsed));
                assertTrue(waitForTcpClosed("localhost", parsePort(app.urlContent.urlContent[0][0]), 60),
                        "Main port is still open");
                final Commands.SerialGCLog l;
                if (!statsFor.contains("-jar")) {
                    long executableSizeKb = Files.size(Path.of(appDir.getAbsolutePath(), statsFor.split(" ")[0])) / 1024L;
                    report.put("executableSizeKb", String.valueOf(executableSizeKb));
                    l = parseSerialGCLog(processLog.toPath(), statsFor, false);
                    report.put("incrementalGCevents", String.valueOf(l.incrementalGCevents));
                    report.put("fullGCevents", String.valueOf(l.fullGCevents));
                } else {
                    l = parseSerialGCLog(processLog.toPath(), statsFor, true);
                    report.put("incrementalGCevents", "-1");
                    report.put("fullGCevents", "-1");
                    report.put("executableSizeKb", "-1");
                }
                report.put("timeSpentInGCs", String.valueOf(l.timeSpentInGCs));
                report.put("testMethod", cn + "#" + mn);
                report.put("requestsExecuted", String.valueOf(LIGHT_REQUESTS));
                reports.add(report);
            }
            final String reportPayload = mapToJSON(reports);
            LOGGER.info(reportPayload);
            if (PERF_APP_REPORT) {
                final HttpResponse<String> response = postRuntimePayload(APP_RUNTIME_CONTEXT, reportPayload);
                if (response != null) {
                    LOGGER.info("Response code:" + response.statusCode());
                    LOGGER.info("Response body:" + response.body());
                    if (response.statusCode() != SC_CREATED) {
                        LOGGER.error("Payload was NOT uploaded to the collector server!");
                    }
                }
            }
            LOGGER.info("Gonna wait for ports closed...");
            assertTrue(waitForTcpClosed("localhost", parsePort(app.urlContent.urlContent[0][0]), 60),
                    "Main port is still open");
            Logs.checkLog(cn, mn, app, processLog);
        } finally {
            if (process != null) {
                processStopper(process, true);
            }
            Logs.archiveLog(cn, mn, Path.of(appDir.getAbsolutePath(),
                    "target", "quarkus-native-image-source-jar", "quarkus-json.json").toFile());
            Logs.archiveLog(cn, mn, processLog);
            Logs.archiveLog(cn, mn, Path.of(appDir.getAbsolutePath(), "target", "quarkus.log").toFile());
            cleanTarget(app);
            removeContainer("quarkus_jaeger");
            if (patch != null) {
                runCommand(getRunCommand("git", "apply", "-R", patch), appDir);
            }
        }
    }

    @Test
    @IfMandrelVersion(min = "21.3")
    public void compareNativeAndJVMSerialGCTime(TestInfo testInfo) throws IOException, InterruptedException, URISyntaxException {
        final Apps app = Apps.QUARKUS_FULL_MICROPROFILE_GC;
        LOGGER.info("Testing app: " + app);
        LOGGER.info("Comparing native and JVM SerialGC times.");

        Process process = null;
        final File appDir = Path.of(BASE_DIR, app.dir).toFile();
        final File processLog = Path.of(appDir.getAbsolutePath(), "logs", "build-and-run.log").toFile();
        final String cn = testInfo.getTestClass().get().getCanonicalName();
        final String mn = testInfo.getTestMethod().get().getName();
        final List<Map<String, String>> reports = new ArrayList<>(2);

        // apply patches, when necessary
        String patch = null;
        if (QUARKUS_VERSION.compareTo(QuarkusVersion.V_3_9_0) >= 0) {
            patch = "quarkus_3.9.x.patch";
        } else if (QUARKUS_VERSION.compareTo(QuarkusVersion.V_3_8_0) >= 0) {
            patch = "quarkus_3.8.x.patch";
        } else if (QUARKUS_VERSION.compareTo(QuarkusVersion.V_3_2_0) >= 0) {
            patch = "quarkus_3.2.x.patch";
        }

        try {
            // cleanup before start
            cleanTarget(app);
            Files.createDirectories(Paths.get(appDir.getAbsolutePath(), "logs"));

            if (patch != null) {
                runCommand(getRunCommand("git", "apply", patch), appDir);
            }

            // build executables for testing
            builderRoutine(app, null, null, null, appDir, processLog, null, getSwitches3());

            for (int i = 0; i < app.buildAndRunCmds.runCommands.length - 1; i++) {
                final Map<String, String> report = populateHeader(new TreeMap<>());
                report.replace("testApp", "https://github.com/Karm/mandrel-integration-tests/apps/quarkus-full-microprofile/");

                // run the app
                final List<String> cmd = getRunCommand(app.buildAndRunCmds.runCommands[i]);
                Files.writeString(processLog.toPath(), String.join(" ", cmd) + '\n', StandardOpenOption.APPEND, StandardOpenOption.CREATE);
                process = runCommand(cmd, appDir, processLog, app);
                LOGGER.info("Running app with pid " + process.pid());

                // create a request to teh app and measure the time
                final long timeToFirstOKRequestMs = WebpageTester.testWeb(app.urlContent.urlContent[0][0], 10, app.urlContent.urlContent[0][1], true);
                waitForFileToMatch(Pattern.compile(".*Events enabled.*"), processLog.toPath(), 0, 20, 1, TimeUnit.SECONDS);
                report.put("timeToFirstOKRequestMs", String.valueOf(timeToFirstOKRequestMs));

                // generate some requests to the app with Hyperfoil
                generateRequestsWithHyperfoil(app, appDir, processLog, cn, mn);

                // stop the app
                processStopper(process, false, true);
                assertTrue(waitForTcpClosed("localhost", parsePort(app.urlContent.urlContent[0][0]), 60),
                        "Main port is still open.");

                // parse the GC log (taken from testQuarkusFullMicroprofile)
                final String statsFor = Arrays.stream(app.buildAndRunCmds.runCommands[i]).collect(Collectors.joining(" ")).trim();
                final Commands.SerialGCLog l;
                if (!statsFor.contains("-jar")) {
                    long executableSizeKb = Files.size(Path.of(appDir.getAbsolutePath(), statsFor.split(" ")[0])) / 1024L;
                    report.put("executableSizeKb", String.valueOf(executableSizeKb));
                    l = parseSerialGCLog(processLog.toPath(), statsFor, false);
                    report.put("incrementalGCevents", String.valueOf(l.incrementalGCevents));
                    report.put("fullGCevents", String.valueOf(l.fullGCevents));
                } else {
                    l = parseSerialGCLog(processLog.toPath(), statsFor, true);
                    report.put("incrementalGCevents", "-1");
                    report.put("fullGCevents", "-1");
                    report.put("executableSizeKb", "-1");
                }
                report.put("timeSpentInGCs", String.valueOf(l.timeSpentInGCs));
                report.put("testMethod", cn + "#" + mn);
                reports.add(report);
            }

            // log the report
            final String reportPayload = mapToJSON(reports);
            LOGGER.info(reportPayload);

            LOGGER.info("Wait till the ports close...");
            assertTrue(waitForTcpClosed("localhost", parsePort(app.urlContent.urlContent[0][0]), 60),
                    "Main is still open.");
            Logs.checkLog(cn, mn, app, processLog);

            // sanity check
            assertNotEquals("0.0", reports.get(0).get("timeSpentInGCs"), "Time spent in GCs is zero (JVM).");
            assertNotEquals("0.0", reports.get(1).get("timeSpentInGCs"), "Time spent in GCs is zero (native).");

            // saving time spent in GCs values
            double jvmGCTime = Double.parseDouble(reports.get(1).get("timeSpentInGCs"));
            double nativeGCTime = Double.parseDouble(reports.get(1).get("timeSpentInGCs"));

            // get threshold value
            final Path gcThresholds = appDir.toPath().resolve("gc_threshold.conf");
            Map<String, Long> thresholds = Thresholds.parseProperties(gcThresholds);

            // assert that time spent in GCs in native is inside the threshold (ideally faster)
            double percentageDiff = getPercentageDifference(nativeGCTime, jvmGCTime);
            assertTrue(nativeGCTime < jvmGCTime || percentageDiff <= (double) thresholds.get("timeInGCs"),
                    "Time spent in GCs is " + percentageDiff + "% slower in native than in JVM (threshold is " + thresholds.get("timeInGCs") + "%).");
        } finally {
            // final cleanup after the test is over
            if (process != null) {
                processStopper(process, true);
            }
            Logs.archiveLog(cn, mn, Path.of(appDir.getAbsolutePath(),
                    "target", "quarkus-native-image-source-jar", "quarkus-json.json").toFile());
            Logs.archiveLog(cn, mn, processLog);
            cleanTarget(app);
            if (patch != null) {
                runCommand(getRunCommand("git", "apply", "-R", patch), appDir);
            }
        }
    }

    private void generateRequestsWithHyperfoil(Apps app, File appDir, File processLog, String cn, String mn)
            throws IOException, InterruptedException, URISyntaxException {
        try {
            removeContainer("hyperfoil-container");

            // start Hyperfoil
            final List<String> getAndStartHyperfoil = getRunCommand(app.buildAndRunCmds.runCommands[2]);
            Process hyperfoilProcess = runCommand(getAndStartHyperfoil, appDir, processLog, app);
            assertNotNull(hyperfoilProcess, "Hyperfoil failed to run. Check " + getLogsDir(cn, mn) + File.separator + processLog.getName());
            LOGGER.info("Hyperfoil process started with pid " + hyperfoilProcess.pid());
            Commands.waitForContainerLogToMatch(ContainerNames.HYPERFOIL.name,
                    Pattern.compile(".*Hyperfoil controller listening.*", Pattern.DOTALL), 600, 5, TimeUnit.SECONDS);
            WebpageTester.testWeb(app.urlContent.urlContent[1][0], 15, app.urlContent.urlContent[1][1], false);

            // upload the benchmark
            final HttpClient hc = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build();
            HyperfoilHelper.uploadBenchmark(app, appDir, app.urlContent.urlContent[2][0], hc);

            // run the benchmark
            disableTurbo();
            final HttpRequest benchmarkRequest = HttpRequest.newBuilder()
                    .uri(new URI(app.urlContent.urlContent[3][0]))
                    .GET()
                    .build();
            final HttpResponse<String> benchmarkResponse = hc.send(benchmarkRequest, HttpResponse.BodyHandlers.ofString());
            final JSONObject benchmarkResponseJson = new JSONObject(benchmarkResponse.body());
            final String id = benchmarkResponseJson.getString("id");

            LOGGER.info("Running Hyperfoil benchmark with id " + id);

            // wait for benchmark to complete
            Commands.waitForContainerLogToMatch(ContainerNames.HYPERFOIL.name,
                    Pattern.compile(".*Successfully persisted run.*", Pattern.DOTALL), 30, 2, TimeUnit.SECONDS);
            enableTurbo();

            // get the benchmark results
            final HttpRequest resultsRequest = HttpRequest.newBuilder()
                    .uri(new URI("http://localhost:8090/run/" + id + "/stats/all/json"))
                    .GET()
                    .timeout(Duration.ofSeconds(3)) // set timeout to allow for cleanup, otherwise will stall at first request above
                    .build();
            final HttpResponse<String> resultsResponse = hc.send(resultsRequest, HttpResponse.BodyHandlers.ofString());
            LOGGER.info("Hyperfoil results response code " + resultsResponse.statusCode());
            final JSONObject resultsResponseJson = new JSONObject(resultsResponse.body());
            System.out.println(resultsResponseJson);
        } finally {
            // cleanup
            removeContainer("hyperfoil-container");
        }

    }

    private double getPercentageDifference(double firstNumber, double secondNumber) {
        return Math.abs(firstNumber - secondNumber) * 100.0 / secondNumber;
    }

    /**
     * This test builds and runs integration tests of a more complex Quarkus app,
     * including two databases, testcontainers etc.
     * The test then examines the logs for any unexpected failures.
     * The point is to upload *build time* stats to the Collector.
     * The test does not use the built app at runtime.
     * @param testInfo
     * @throws IOException
     * @throws InterruptedException
     * @throws URISyntaxException
     */
    @Test
    @IfMandrelVersion(min = "22.3")
    @IfQuarkusVersion(min = "2.13.3")
    public void testQuarkusMPOrmAwtLocal(TestInfo testInfo) throws IOException, InterruptedException, URISyntaxException {
        testQuarkusMPOrmAwt(testInfo, false);
    }

    @Test
    @IfMandrelVersion(min = "22.3", inContainer = true)
    @IfQuarkusVersion(min = "2.13.3")
    @Tag("builder-image")
    public void testQuarkusMPOrmAwtContainer(TestInfo testInfo) throws IOException, InterruptedException, URISyntaxException {
        testQuarkusMPOrmAwt(testInfo, true);
    }

    public void testQuarkusMPOrmAwt(TestInfo testInfo, boolean inContainer) throws IOException, InterruptedException, URISyntaxException {
        final Apps app = inContainer ? Apps.QUARKUS_BUILDER_IMAGE_MP_ORM_DBS_AWT : Apps.QUARKUS_MP_ORM_DBS_AWT;
        if ("aarch64".equalsIgnoreCase(ARCH) &&
                (getVersion(inContainer).compareTo(Version.create(24, 2, 0)) >= 0) &&
                (getVersion(inContainer).compareTo(Version.create(25, 0, 0)) <= 0)) {
            LOGGER.warn("Support for the Foreign Function and Memory API is currently available only on the AMD64 architecture.");
            LOGGER.warn("Skipping testing app: " + app);
            return;
        }
        LOGGER.info("Testing app: " + app);
        final File appDir = Path.of(BASE_DIR, app.dir).toFile();
        final File processLog = Path.of(appDir.getAbsolutePath(), "logs", "build-and-run.log").toFile();
        final String cn = testInfo.getTestClass().get().getCanonicalName();
        final String mn = testInfo.getTestMethod().get().getName();
        String patch = null;
        final List<Path> jsonPayloads = new ArrayList<>(2);
        if (QUARKUS_VERSION.compareTo(QuarkusVersion.V_3_21_0) >= 0) {
            patch = "quarkus_3.21.x.patch";
        } else if (QUARKUS_VERSION.compareTo(QuarkusVersion.V_3_9_0) >= 0) {
            patch = "quarkus_3.9.x.patch";
        } else if (QUARKUS_VERSION.compareTo(QuarkusVersion.V_3_2_0) >= 0) {
            patch = "quarkus_3.2.x.patch";
        }
        try {
            // Cleanup
            cleanTarget(app);
            Files.createDirectories(Paths.get(appDir.getAbsolutePath(), "logs"));

            if (patch != null) {
                runCommand(getRunCommand("git", "apply", patch), appDir);
            }

            // Build executables
            final Map<String, String> switches = new HashMap<>() {
                {
                    put(FINAL_NAME_TOKEN, String.format("build-perf-%s-%s-mp-orm-dbs-awt",
                            getProperty("perf.app.arch", ARCH),
                            getProperty("perf.app.os", System.getProperty("os.name"))));
                    put(GRAALVM_BUILD_OUTPUT_JSON_FILE, "quarkus-json.json");
                    if ((getVersion(inContainer).compareTo(Version.create(23, 1, 0)) >= 0)) {
                        put(GRAALVM_EXPERIMENTAL_BEGIN, "-H:+UnlockExperimentalVMOptions,");
                        put(GRAALVM_EXPERIMENTAL_END, "-H:-UnlockExperimentalVMOptions,");
                    } else {
                        put(GRAALVM_EXPERIMENTAL_BEGIN, "");
                        put(GRAALVM_EXPERIMENTAL_END, "");
                    }
                }
            };

            builderRoutine(app, null, null, null, appDir, processLog, null, switches);
            findExecutable(Path.of(appDir.getAbsolutePath(), "target"), Pattern.compile(".*mp-orm-dbs-awt.*"));

            if (PERF_APP_REPORT) {
                // The checking whether there are no more files than we expect is to avoid uploading unexpected artifacts.
                final List<Path> mainPayloads = findFiles(Path.of(appDir.getAbsolutePath(), "target"), Pattern.compile("quarkus-json.json"));
                if (mainPayloads.size() != 1) {
                    throw new IllegalStateException("Expected exactly one quarkus-json.json, found: " + mainPayloads.size());
                }
                jsonPayloads.add(mainPayloads.get(0));
                final List<Path> secondaryPayloads = findFiles(Path.of(appDir.getAbsolutePath(), "target"), Pattern.compile(".*timing-stats.json"));
                if (!secondaryPayloads.isEmpty()) {
                    jsonPayloads.add(secondaryPayloads.get(0));
                }
                if (secondaryPayloads.size() > 1) {
                    throw new IllegalStateException("At most one timing-tats.json file expected, found: " + secondaryPayloads.size());
                }

                final String qversion = QUARKUS_VERSION.isSnapshot() ?
                        QUARKUS_VERSION.getGitSHA() + '.' + QUARKUS_VERSION.getVersionString() : QUARKUS_VERSION.getVersionString();
                final String mversion = getVersion(inContainer).toString();
                final HttpResponse<String> r;
                // The json files are like 4K tops, so we can afford Files.readString...
                if (secondaryPayloads.size() == 1) {
                    r = postBuildtimePayload(APP_BUILDTIME_CONTEXT, qversion, mversion, Files.readString(mainPayloads.get(0)), Files.readString(secondaryPayloads.get(0)));
                } else {
                    r = postBuildtimePayload(APP_BUILDTIME_CONTEXT, qversion, mversion, Files.readString(mainPayloads.get(0)));
                }
                if (r != null) {
                    LOGGER.info("Response code:" + r.statusCode());
                    LOGGER.info("Response body:" + r.body());
                    if (!(r.statusCode() == SC_CREATED || r.statusCode() == SC_ACCEPTED || r.statusCode() == SC_OK)) {
                        LOGGER.error("Payload was NOT uploaded to the collector server!");
                    }
                }
            }
            Logs.checkLog(cn, mn, app, processLog);
        } finally {
            for (Path jsonPayload : jsonPayloads) {
                Logs.archiveLog(cn, mn, jsonPayload.toFile());
            }
            Logs.archiveLog(cn, mn, processLog);
            Logs.archiveLog(cn, mn, Path.of(appDir.getAbsolutePath(), "target", "quarkus.log").toFile());
            cleanTarget(app);
            if (patch != null) {
                runCommand(getRunCommand("git", "apply", "-R", patch), appDir);
            }
        }
    }

    private static Map<String, String> getSwitches1() {
        final Map<String, String> switches;
        if (getVersion(false).compareTo(Version.create(22, 2, 0)) >= 0) {
            if (getVersion(false).compareTo(Version.create(23, 1, 0)) >= 0) {
                switches = Map.of(
                        GRAALVM_BUILD_OUTPUT_JSON_FILE + "-ParseOnce",
                        ",-H:+UnlockExperimentalVMOptions," + GRAALVM_BUILD_OUTPUT_JSON_FILE_SWITCH + "quarkus-json_minus-ParseOnce.json,-H:-UnlockExperimentalVMOptions",
                        GRAALVM_BUILD_OUTPUT_JSON_FILE + "+ParseOnce",
                        ",-H:+UnlockExperimentalVMOptions," + GRAALVM_BUILD_OUTPUT_JSON_FILE_SWITCH + "quarkus-json_plus-ParseOnce.json,-H:-UnlockExperimentalVMOptions",
                        "-H:-ParseOnce", "-H:+UnlockExperimentalVMOptions,-H:-ParseOnce,-H:-UnlockExperimentalVMOptions",
                        "-H:+ParseOnce", "-H:+UnlockExperimentalVMOptions,-H:+ParseOnce,-H:-UnlockExperimentalVMOptions"
                );
            } else {
                switches = Map.of(
                        GRAALVM_BUILD_OUTPUT_JSON_FILE + "-ParseOnce",
                        "," + GRAALVM_BUILD_OUTPUT_JSON_FILE_SWITCH + "quarkus-json_minus-ParseOnce.json",
                        GRAALVM_BUILD_OUTPUT_JSON_FILE + "+ParseOnce",
                        "," + GRAALVM_BUILD_OUTPUT_JSON_FILE_SWITCH + "quarkus-json_plus-ParseOnce.json"
                );
            }
        } else {
            switches = Map.of(
                    GRAALVM_BUILD_OUTPUT_JSON_FILE + "-ParseOnce", "",
                    GRAALVM_BUILD_OUTPUT_JSON_FILE + "+ParseOnce", ""
            );
        }
        return switches;
    }

    private static Map<String, String> getSwitches2() {
        final Map<String, String> switches;
        if (getVersion(false).compareTo(Version.create(22, 2, 0)) >= 0) {
            if (getVersion(false).compareTo(Version.create(23, 1, 0)) >= 0) {
                switches = Map.of(GRAALVM_BUILD_OUTPUT_JSON_FILE,
                        ",-H:+UnlockExperimentalVMOptions," + GRAALVM_BUILD_OUTPUT_JSON_FILE_SWITCH + "quarkus-json.json,-H:-UnlockExperimentalVMOptions");
            } else {
                switches = Map.of(GRAALVM_BUILD_OUTPUT_JSON_FILE,
                        "," + GRAALVM_BUILD_OUTPUT_JSON_FILE_SWITCH + "quarkus-json.json");
            }
        } else {
            switches = Map.of(GRAALVM_BUILD_OUTPUT_JSON_FILE, "");
        }
        return switches;
    }

    private static Map<String, String> getSwitches3() {
        final Map<String, String> switches = new HashMap<>();
        if (getVersion(false).compareTo(Version.create(22, 2, 0)) >= 0) {
            if (getVersion(false).compareTo(Version.create(23, 1, 0)) >= 0) {
                switches.put(GRAALVM_BUILD_OUTPUT_JSON_FILE,
                        ",-H:+UnlockExperimentalVMOptions," + GRAALVM_BUILD_OUTPUT_JSON_FILE_SWITCH + "quarkus-json.json,-H:-UnlockExperimentalVMOptions");
                switches.put("-H:Log=registerResource:",
                        "-H:+UnlockExperimentalVMOptions,-H:Log=registerResource:,-H:-UnlockExperimentalVMOptions");
            } else {
                switches.put(GRAALVM_BUILD_OUTPUT_JSON_FILE, "," + GRAALVM_BUILD_OUTPUT_JSON_FILE_SWITCH + "quarkus-json.json");
            }
        } else {
            switches.put(GRAALVM_BUILD_OUTPUT_JSON_FILE, "");
        }
        return switches;
    }
}
