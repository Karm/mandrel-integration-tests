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

import com.sun.management.OperatingSystemMXBean;
import org.graalvm.tests.integration.utils.Commands;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

import static org.graalvm.tests.integration.utils.BuildLogParser.mapToJSON;
import static org.graalvm.tests.integration.utils.BuildLogParser.parse;
import static org.graalvm.tests.integration.utils.Commands.QUARKUS_VERSION;
import static org.graalvm.tests.integration.utils.Commands.getProperty;
import static org.graalvm.tests.integration.utils.Commands.processStopper;
import static org.graalvm.tests.integration.utils.Commands.runCommand;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author Michal Karm Babacek <karm@redhat.com>
 */
@Tag("perfcheck")
public class PerfCheckTest {

    private static final Logger LOGGER = Logger.getLogger(PerfCheckTest.class.getName());

    @Test
    public void buildAndReport(TestInfo testInfo) throws IOException, InterruptedException {
        final String appDir = Commands.getProperty("perf.app.dir");
        assertNotNull(appDir, "We cannot continue without having the PERF_APP_DIR set.");
        final Map<String, String> report = new TreeMap<>();
        report.put("arch", getProperty("perf.app.arch", ""));
        report.put("os", getProperty("perf.app.os", ""));
        report.put("nativeImageXmXMB", getProperty("perf.app.xmxmb", "0"));
        report.put("quarkusVersion", QUARKUS_VERSION.getVersionString());
        report.put("ramAvailableMB", Long.toString(
                ((OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean()).getFreePhysicalMemorySize()
                        / 1024 / 1024));
        report.put("runnerDescription",
                getProperty("perf.app.runner.description", ""));
        report.put("testApp", getProperty("perf.app.url", ""));
        LOGGER.info("Testing app: " + appDir);
        Process process = null;
        final File processLog = new File(appDir, "build-and-run.log");
        Files.deleteIfExists(processLog.toPath());
        try {
            final List<String> cmd = Commands.getRunCommand(
                    "./mvnw",
                    "clean",
                    "package",
                    "-Pnative",
                    "-Dquarkus.native.native-image-xmx=" + report.get("nativeImageXmXMB") + "M",
                    "-Dquarkus.platform.version=" + report.get("quarkusVersion"),
                    "-Dmaven.compiler.release=11");
            process = runCommand(cmd, new File(appDir), processLog, null);
            process.waitFor(60, TimeUnit.MINUTES);

            report.putAll(parse(processLog.toPath()));

            final Path pathToNormalize = Path.of(report.remove("executablePath"));
            final Path executable = Path.of(pathToNormalize.getParent().getParent().toString(),
                    pathToNormalize.getFileName().toString());
            if (Files.notExists(executable) || Files.size(executable) < 1024 * 1024) {
                throw new IllegalArgumentException(
                        "Something is wrong, the executable from the log dos not exist or is way too small. Check "
                                + executable);
            }
            report.put("executableSizeMB", Long.toString(Files.size(executable) / 1024 / 1024));

            final String payload = mapToJSON(report);
            LOGGER.info(payload);
            final String[] headers = new String[] {
                    "User-Agent", "Mandrel Integration TS",
                    "token", Commands.getProperty("perf.app.secret.token"),
                    "Content-Type", "application/json",
                    "Accept", "text/plain"
            };
            final HttpRequest releaseRequest = HttpRequest.newBuilder()
                    .method("POST", HttpRequest.BodyPublishers.ofString(payload))
                    .uri(new URI(Commands.getProperty("perf.app.endpoint")))
                    .headers(headers)
                    .build();
            final HttpClient hc = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build();
            final HttpResponse<String> releaseResponse = hc.send(releaseRequest, HttpResponse.BodyHandlers.ofString());
            LOGGER.info("Response Code : " + releaseResponse.statusCode());
            LOGGER.info("Response Body : " + releaseResponse.body());
        } catch (URISyntaxException e) {
            e.printStackTrace();
        } finally {
            if (process != null) {
                processStopper(process, false);
            }
        }
    }
}
