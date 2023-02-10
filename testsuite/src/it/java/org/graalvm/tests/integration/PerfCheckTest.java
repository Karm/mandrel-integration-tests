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
import org.graalvm.tests.integration.utils.Logs;
import org.graalvm.tests.integration.utils.Uploader;
import org.graalvm.tests.integration.utils.WebpageTester;
import org.graalvm.tests.integration.utils.versions.IfMandrelVersion;
import org.graalvm.tests.integration.utils.versions.UsedVersion;
import org.jboss.logging.Logger;
import org.jboss.resteasy.spi.HttpResponseCodes;
import org.junit.jupiter.api.Assertions;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.graalvm.tests.integration.AppReproducersTest.BASE_DIR;
import static org.graalvm.tests.integration.utils.Commands.GRAALVM_BUILD_OUTPUT_JSON_FILE;
import static org.graalvm.tests.integration.utils.Commands.GRAALVM_BUILD_OUTPUT_JSON_FILE_SWITCH;
import static org.graalvm.tests.integration.utils.Commands.QUARKUS_VERSION;
import static org.graalvm.tests.integration.utils.Commands.builderRoutine;
import static org.graalvm.tests.integration.utils.Commands.cleanTarget;
import static org.graalvm.tests.integration.utils.Commands.getProperty;
import static org.graalvm.tests.integration.utils.Commands.getRSSkB;
import static org.graalvm.tests.integration.utils.Commands.getRunCommand;
import static org.graalvm.tests.integration.utils.Commands.mapToJSON;
import static org.graalvm.tests.integration.utils.Commands.parsePerfRecord;
import static org.graalvm.tests.integration.utils.Commands.parsePort;
import static org.graalvm.tests.integration.utils.Commands.parseSerialGCLog;
import static org.graalvm.tests.integration.utils.Commands.processStopper;
import static org.graalvm.tests.integration.utils.Commands.runCommand;
import static org.graalvm.tests.integration.utils.Commands.waitForFileToMatch;
import static org.graalvm.tests.integration.utils.Commands.waitForTcpClosed;
import static org.graalvm.tests.integration.utils.Uploader.PERF_APP_REPORT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Michal Karm Babacek <karm@redhat.com>
 */
@Tag("perfcheck")
@DisabledOnOs({OS.WINDOWS}) // We need to replace perf with wmic & Dr.Memory or something
public class PerfCheckTest {

    private static final Logger LOGGER = Logger.getLogger(PerfCheckTest.class.getName());

    public static final int LIGHT_REQUESTS = Integer.parseInt(getProperty("PERFCHECK_TEST_LIGHT_REQUESTS", "100"));
    public static final int HEAVY_REQUESTS = Integer.parseInt(getProperty("PERFCHECK_TEST_HEAVY_REQUESTS", "2"));
    public static final int MX_HEAP_MB = Integer.parseInt(getProperty("PERFCHECK_TEST_REQUESTS_MX_HEAP_MB", "2560"));
    // Reporting
    public static final String APP_CONTEXT = "api/v1/perfstats/perf";

    public static Map<String, String> populateHeader(Map<String, String> report) {
        report.put("arch", getProperty("perf.app.arch", System.getProperty("os.arch")));
        report.put("os", getProperty("perf.app.os", System.getProperty("os.name")));
        report.put("quarkusVersion", QUARKUS_VERSION.getVersionString());
        report.put("mandrelVersion", UsedVersion.getVersion(false).toString());
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
    @IfMandrelVersion(min = "21.3")
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
        try {
            // Cleanup
            cleanTarget(app);
            Files.createDirectories(Paths.get(appDir.getAbsolutePath(), "logs"));
            assertTrue(app.buildAndRunCmds.cmds.length > 1);

            if (QUARKUS_VERSION.majorIs(3)) {
                runCommand(getRunCommand("git", "apply", "quarkus_3.x.patch"), appDir);
            }

            // Build executables
            final Map<String, String> switches;
            if (UsedVersion.getVersion(false).compareTo(Version.create(22, 2, 0)) >= 0) {
                switches = Map.of(
                        GRAALVM_BUILD_OUTPUT_JSON_FILE + "-ParseOnce", "," + GRAALVM_BUILD_OUTPUT_JSON_FILE_SWITCH + "quarkus-json_minus-ParseOnce.json",
                        GRAALVM_BUILD_OUTPUT_JSON_FILE + "+ParseOnce", "," + GRAALVM_BUILD_OUTPUT_JSON_FILE_SWITCH + "quarkus-json_plus-ParseOnce.json"
                );
            } else {
                switches = Map.of(
                        GRAALVM_BUILD_OUTPUT_JSON_FILE + "-ParseOnce", "",
                        GRAALVM_BUILD_OUTPUT_JSON_FILE + "+ParseOnce", ""
                );
            }
            builderRoutine(3, app, null, null, null, appDir, processLog, null, switches);
            assertTrue(processLog.exists());

            int line = 0;
            for (int i = 3; i >= 1; i--) {
                final Map<String, String> report = populateHeader(new TreeMap<>());
                final List<String> cmd = getRunCommand(app.buildAndRunCmds.cmds[app.buildAndRunCmds.cmds.length - i]);
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
                final String[] headers = new String[]{
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
                final String statsFor = Arrays.stream(app.buildAndRunCmds.cmds[app.buildAndRunCmds.cmds.length - i])
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
                Assertions.assertTrue(waitForTcpClosed("localhost", parsePort(app.urlContent.urlContent[0][0]), 60),
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
                final HttpResponse<String> response = Uploader.postPayload(APP_CONTEXT, reportPayload);
                if (response != null) {
                    LOGGER.info("Response code:" + response.statusCode());
                    LOGGER.info("Response body:" + response.body());
                    if (response.statusCode() != HttpResponseCodes.SC_CREATED) {
                        LOGGER.error("Payload was NOT uploaded tot the collector server!");
                    }
                }
            }
            LOGGER.info("Gonna wait for ports closed...");
            Assertions.assertTrue(waitForTcpClosed("localhost", parsePort(app.urlContent.urlContent[0][0]), 60),
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
            cleanTarget(app);
            if (QUARKUS_VERSION.majorIs(3)) {
                runCommand(getRunCommand("git", "apply", "-R", "quarkus_3.x.patch"), appDir);
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
        try {
            // Cleanup
            cleanTarget(app);
            Files.createDirectories(Paths.get(appDir.getAbsolutePath(), "logs"));
            assertTrue(app.buildAndRunCmds.cmds.length > 1);

            if (QUARKUS_VERSION.majorIs(3)) {
                runCommand(getRunCommand("git", "apply", "quarkus_3.x.patch"), appDir);
            }

            // Build executables
            final Map<String, String> switches;
            if (UsedVersion.getVersion(false).compareTo(Version.create(22, 2, 0)) >= 0) {
                switches = Map.of(GRAALVM_BUILD_OUTPUT_JSON_FILE, "," + GRAALVM_BUILD_OUTPUT_JSON_FILE_SWITCH + "quarkus-json.json");
            } else {
                switches = Map.of(GRAALVM_BUILD_OUTPUT_JSON_FILE, "");
            }
            builderRoutine(2, app, null, null, null, appDir, processLog, null, switches);

            int line = 0;
            for (int i = 2; i >= 1; i--) {
                final Map<String, String> report = populateHeader(new TreeMap<>());
                final List<String> cmd = getRunCommand(app.buildAndRunCmds.cmds[app.buildAndRunCmds.cmds.length - i]);
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
                final String[] headers = new String[]{
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
                final String statsFor = Arrays.stream(app.buildAndRunCmds.cmds[app.buildAndRunCmds.cmds.length - i])
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
                Assertions.assertTrue(waitForTcpClosed("localhost", parsePort(app.urlContent.urlContent[0][0]), 60),
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
                final HttpResponse<String> response = Uploader.postPayload(APP_CONTEXT, reportPayload);
                if (response != null) {
                    LOGGER.info("Response code:" + response.statusCode());
                    LOGGER.info("Response body:" + response.body());
                    if (response.statusCode() != HttpResponseCodes.SC_CREATED) {
                        LOGGER.error("Payload was NOT uploaded tot the collector server!");
                    }
                }
            }
            LOGGER.info("Gonna wait for ports closed...");
            Assertions.assertTrue(waitForTcpClosed("localhost", parsePort(app.urlContent.urlContent[0][0]), 60),
                    "Main port is still open");
            Logs.checkLog(cn, mn, app, processLog);
        } finally {
            Files.deleteIfExists(json.toPath());
            if (process != null) {
                processStopper(process, true);
            }
            Logs.archiveLog(cn, mn, Path.of(appDir.getAbsolutePath(), "target",
                    "quarkus-json-native-image-source-jar", "quarkus-json.json").toFile());
            Logs.archiveLog(cn, mn, processLog);
            cleanTarget(app);
            if (QUARKUS_VERSION.majorIs(3)) {
                runCommand(getRunCommand("git", "apply", "-R", "quarkus_3.x.patch"), appDir);
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
        try {
            // Cleanup
            cleanTarget(app);
            Files.createDirectories(Paths.get(appDir.getAbsolutePath(), "logs"));
            assertTrue(app.buildAndRunCmds.cmds.length > 1);

            if (QUARKUS_VERSION.majorIs(3)) {
                runCommand(getRunCommand("git", "apply", "quarkus_3.x.patch"), appDir);
            }

            // Build executables
            final Map<String, String> switches;
            if (UsedVersion.getVersion(false).compareTo(Version.create(22, 2, 0)) >= 0) {
                switches = Map.of(GRAALVM_BUILD_OUTPUT_JSON_FILE, "," + GRAALVM_BUILD_OUTPUT_JSON_FILE_SWITCH + "quarkus-json.json");
            } else {
                switches = Map.of(GRAALVM_BUILD_OUTPUT_JSON_FILE, "");
            }
            builderRoutine(2, app, null, null, null, appDir, processLog, null, switches);

            int line = 0;
            for (int i = 2; i >= 1; i--) {
                final Map<String, String> report = populateHeader(new TreeMap<>());
                report.replace("testApp", "https://github.com/Karm/mandrel-integration-tests/apps/quarkus-full-microprofile/");
                final List<String> cmd = getRunCommand(app.buildAndRunCmds.cmds[app.buildAndRunCmds.cmds.length - i]);
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
                        hc.send(httpRequest, HttpResponse.BodyHandlers.ofString());
                        System.out.print('.');
                    }
                }
                System.out.println();
                report.put("rssKb", Long.toString(getRSSkB(process.children().sorted().findFirst().get().pid())));
                processStopper(process, false, true);
                final String statsFor = Arrays.stream(app.buildAndRunCmds.cmds[app.buildAndRunCmds.cmds.length - i])
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
                Assertions.assertTrue(waitForTcpClosed("localhost", parsePort(app.urlContent.urlContent[0][0]), 60),
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
                final HttpResponse<String> response = Uploader.postPayload(APP_CONTEXT, reportPayload);
                if (response != null) {
                    LOGGER.info("Response code:" + response.statusCode());
                    LOGGER.info("Response body:" + response.body());
                    if (response.statusCode() != HttpResponseCodes.SC_CREATED) {
                        LOGGER.error("Payload was NOT uploaded tot the collector server!");
                    }
                }
            }
            LOGGER.info("Gonna wait for ports closed...");
            Assertions.assertTrue(waitForTcpClosed("localhost", parsePort(app.urlContent.urlContent[0][0]), 60),
                    "Main port is still open");
            Logs.checkLog(cn, mn, app, processLog);
        } finally {
            if (process != null) {
                processStopper(process, true);
            }
            Logs.archiveLog(cn, mn, Path.of(appDir.getAbsolutePath(),
                    "target", "quarkus-native-image-source-jar", "quarkus-json.json").toFile());
            Logs.archiveLog(cn, mn, processLog);
            cleanTarget(app);
            if (QUARKUS_VERSION.majorIs(3)) {
                runCommand(getRunCommand("git", "apply", "-R", "quarkus_3.x.patch"), appDir);
            }
        }
    }
}
