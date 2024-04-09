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
import org.graalvm.tests.integration.utils.LogBuilder;
import org.graalvm.tests.integration.utils.Logs;
import org.graalvm.tests.integration.utils.WebpageTester;
import org.graalvm.tests.integration.utils.versions.QuarkusVersion;
import org.graalvm.tests.integration.utils.versions.UsedVersion;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.graalvm.tests.integration.utils.Commands.QUARKUS_VERSION;
import static org.graalvm.tests.integration.utils.Commands.builderRoutine;
import static org.graalvm.tests.integration.utils.Commands.cleanTarget;
import static org.graalvm.tests.integration.utils.Commands.findExecutable;
import static org.graalvm.tests.integration.utils.Commands.getBaseDir;
import static org.graalvm.tests.integration.utils.Commands.getContainerMemoryKb;
import static org.graalvm.tests.integration.utils.Commands.getOpenedFDs;
import static org.graalvm.tests.integration.utils.Commands.getRSSkB;
import static org.graalvm.tests.integration.utils.Commands.getRunCommand;
import static org.graalvm.tests.integration.utils.Commands.parsePort;
import static org.graalvm.tests.integration.utils.Commands.processStopper;
import static org.graalvm.tests.integration.utils.Commands.runCommand;
import static org.graalvm.tests.integration.utils.Commands.stopAllRunningContainers;
import static org.graalvm.tests.integration.utils.Commands.stopRunningContainer;
import static org.graalvm.tests.integration.utils.Commands.waitForTcpClosed;

/**
 * Tests for build and start of applications with some real source code.
 *
 * @author Michal Karm Babacek <karm@redhat.com>
 */
@Tag("runtimes")
public class RuntimesSmokeTest {

    private static final Logger LOGGER = Logger.getLogger(RuntimesSmokeTest.class.getName());

    public static final String BASE_DIR = getBaseDir();

    public void testRuntime(TestInfo testInfo, Apps app) throws IOException, InterruptedException {
        testRuntime(testInfo, app, null);
    }
    public void testRuntime(TestInfo testInfo, Apps app, Map<String, String> switchReplacements) throws IOException, InterruptedException {
        LOGGER.info("Testing app: " + app);
        Process process = null;
        final File appDir = Path.of(BASE_DIR, app.dir).toFile();
        final File processLog = Path.of(appDir.getAbsolutePath(), "logs", "build-and-run.log").toFile();
        final StringBuilder report = new StringBuilder();
        final String cn = testInfo.getTestClass().get().getCanonicalName();
        final String mn = testInfo.getTestMethod().get().getName();
        try {
            // Cleanup
            cleanTarget(app);
            if (app.runtimeContainer != ContainerNames.NONE) {
                // If we are about to be working with containers, we need a clean slate.
                stopAllRunningContainers();
            }
            Files.createDirectories(Paths.get(appDir.getAbsolutePath() + File.separator + "logs"));

            long buildStarts = System.currentTimeMillis();
            builderRoutine(app.buildAndRunCmds.cmds.length - 1, app, report, cn, mn, appDir, processLog, null, switchReplacements);
            long buildEnds = System.currentTimeMillis();
            findExecutable(Path.of(appDir.getAbsolutePath(), "target"), Pattern.compile(".*"));

            // Run
            LOGGER.info("Running...");
            final List<String> cmd = getRunCommand(app.buildAndRunCmds.cmds[app.buildAndRunCmds.cmds.length - 1]);
            process = runCommand(cmd, appDir, processLog, app);
            Logs.appendln(report, appDir.getAbsolutePath());
            Logs.appendlnSection(report, String.join(" ", cmd));

            // Test web pages
            final long timeToFirstOKRequest = WebpageTester.testWeb(app.urlContent.urlContent[0][0], 10, app.urlContent.urlContent[0][1], true);
            LOGGER.info("Testing web page content...");
            for (String[] urlContent : app.urlContent.urlContent) {
                WebpageTester.testWeb(urlContent[0], 5, urlContent[1], false);
            }

            LOGGER.info("Terminate and scan logs...");
            // Makes sure the log is written.
            process.getInputStream().available();

            LogBuilder.Log log;
            long rssKb;
            long executableSizeKb;
            // Running without a container
            if (app.runtimeContainer == ContainerNames.NONE) {
                executableSizeKb = Files.size(Path.of(appDir.getAbsolutePath(),
                        app.buildAndRunCmds.cmds[app.buildAndRunCmds.cmds.length - 1][0])) / 1024L;
                rssKb = getRSSkB(process.pid());
                final long openedFiles = getOpenedFDs(process.pid());
                processStopper(process, false);
                log = new LogBuilder()
                        .app(app)
                        .buildTimeMs(buildEnds - buildStarts)
                        .timeToFirstOKRequestMs(timeToFirstOKRequest)
                        .executableSizeKb(executableSizeKb)
                        .rssKb(rssKb)
                        .openedFiles(openedFiles)
                        .build();
                // Running as a container
            } else {
                //  -runner is a Quarkus specific name, but we don't test Helidon in container anyway...
                executableSizeKb = findExecutable(Path.of(appDir.getAbsolutePath(), "target"),
                        Pattern.compile(".*-runner")).length() / 1024L;
                rssKb = getContainerMemoryKb(app.runtimeContainer.name);
                stopRunningContainer(app.runtimeContainer.name);
                log = new LogBuilder()
                        .app(app)
                        .buildTimeMs(buildEnds - buildStarts)
                        .timeToFirstOKRequestMs(timeToFirstOKRequest)
                        .executableSizeKb(executableSizeKb)
                        .rssKb(rssKb)
                        .build();
            }

            LOGGER.info("Gonna wait for ports closed...");
            // Release ports
            Assertions.assertTrue(waitForTcpClosed("localhost", parsePort(app.urlContent.urlContent[0][0]), 60),
                    "Main port is still open");
            Logs.checkLog(cn, mn, app, processLog);
            Path measurementsLog = Paths.get(Logs.getLogsDir(cn, mn).toString(), "measurements.csv");
            Logs.logMeasurements(log, measurementsLog);
            Logs.appendln(report, "Measurements:");
            Logs.appendln(report, log.headerMarkdown + "\n" + log.lineMarkdown);
            Logs.checkThreshold(app, executableSizeKb, rssKb, timeToFirstOKRequest);
        } finally {
            // Make sure processes are down even if there was an exception / failure
            if (process != null) {
                processStopper(process, true);
            }
            // Archive logs no matter what
            Logs.archiveLog(cn, mn, processLog);
            Logs.writeReport(cn, mn, report.toString());
            // This is debatable. When the run fails,
            // it might be valuable to have the binary and not just the logs?
            // Nope: Delete it. One can reproduce it from the journal file we maintain.
            cleanTarget(app);
        }
    }

    @Test
    @Tag("quarkus")
    public void quarkusFullMicroProfile(TestInfo testInfo) throws IOException, InterruptedException {
        Apps app = Apps.QUARKUS_FULL_MICROPROFILE;
        final Map<String, String> switches;
        if (UsedVersion.getVersion(false).compareTo(Version.create(23, 1, 0)) >= 0) {
            switches = Map.of("-H:Log=registerResource:", "-H:+UnlockExperimentalVMOptions,-H:Log=registerResource:,-H:-UnlockExperimentalVMOptions");
        } else {
            switches = null;
        }

        String patch = null;
        if (QUARKUS_VERSION.compareTo(QuarkusVersion.V_3_9_0) >= 0) {
            patch = "quarkus_3.9.x.patch";
        } else if (QUARKUS_VERSION.compareTo(QuarkusVersion.V_3_8_0) >= 0) {
            patch = "quarkus_3.8.x.patch";
        } else if (QUARKUS_VERSION.compareTo(QuarkusVersion.V_3_2_0) >= 0) {
            patch = "quarkus_3.2.x.patch";
        }
        final File appDir = Path.of(BASE_DIR, app.dir).toFile();
        if (patch != null) {
            try {
                runCommand(getRunCommand("git", "apply", patch), appDir);
                testRuntime(testInfo, app, switches);
            } finally {
                runCommand(getRunCommand("git", "apply", "-R", patch), appDir);
            }
        } else {
            testRuntime(testInfo, app, switches);
        }
    }

    @Test
    @Tag("builder-image")
    @Tag("quarkus")
    public void quarkusEncodingIssues(TestInfo testInfo) throws IOException, InterruptedException {
        Apps apps = Apps.QUARKUS_BUILDER_IMAGE_ENCODING;
        if (QUARKUS_VERSION.compareTo(QuarkusVersion.V_3_0_0) >= 0) {
            try {
                runCommand(getRunCommand("git", "apply", "quarkus_3.x.patch"),
                        Path.of(BASE_DIR, apps.dir).toFile());
                testRuntime(testInfo, apps);
            } finally {
                runCommand(getRunCommand("git", "apply", "-R", "quarkus_3.x.patch"),
                        Path.of(BASE_DIR, apps.dir).toFile());
            }
        } else {
            testRuntime(testInfo, apps);
        }
    }

    @Test
    @Tag("helidon")
    @DisabledOnOs({OS.WINDOWS})
    // No Windows. https://github.com/oracle/helidon/issues/2230
    public void helidonQuickStart(TestInfo testInfo) throws IOException, InterruptedException {
        testRuntime(testInfo, Apps.HELIDON_QUICKSTART_SE);
    }
}
