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

import org.graalvm.tests.integration.utils.Apps;
import org.graalvm.tests.integration.utils.Logs;
import org.graalvm.tests.integration.utils.versions.IfMandrelVersion;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.graalvm.tests.integration.AppReproducersTest.validateDebugSmokeApp;
import static org.graalvm.tests.integration.utils.Commands.builderRoutine;
import static org.graalvm.tests.integration.utils.Commands.cleanTarget;
import static org.graalvm.tests.integration.utils.Commands.cleanup;
import static org.graalvm.tests.integration.utils.Commands.getBaseDir;
import static org.graalvm.tests.integration.utils.Commands.getRunCommand;
import static org.graalvm.tests.integration.utils.Commands.runCommand;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for build and start of applications with some real source code.
 * Focused on JFR.
 *
 * @author Michal Karm Babacek <karm@redhat.com>
 */
@Tag("reproducers")
public class JFRTest {

    private static final Logger LOGGER = Logger.getLogger(JFRTest.class.getName());

    public static final String BASE_DIR = getBaseDir();

    @Test
    @Tag("jfr")
    @IfMandrelVersion(min = "21.2")
    public void jfrSmoke(TestInfo testInfo) throws IOException, InterruptedException {
        final Apps app = Apps.JFR_SMOKE;
        LOGGER.info("Testing app: " + app.toString());
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
            processLog = new File(appDir.getAbsolutePath() + File.separator + "logs" + File.separator + "build-and-run.log");

            // In this case, the two last commands are used for running the app; one in JVM mode and the other in Native mode.
            // We should somehow capture this semantically in an Enum or something. This is fragile...
            builderRoutine(app.buildAndRunCmds.cmds.length - 2, app, report, cn, mn, appDir, processLog);

            final File inputData = new File(BASE_DIR + File.separator + app.dir + File.separator + "target" + File.separator + "test_data.txt");

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
        }
    }

    /**
     * e.g.
     * https://github.com/oracle/graal/issues/3638
     * native-image, UX, JFR, -XX:StartFlightRecording flags/arguments units are not aligned with HotSpot convention
     *
     * @param testInfo
     * @throws IOException
     * @throws InterruptedException
     */
    @Test
    @Tag("jfr")
    @IfMandrelVersion(min = "21.3")
    public void jfrOptionsSmoke(TestInfo testInfo) throws IOException, InterruptedException {
        final Apps app = Apps.JFR_OPTIONS;
        LOGGER.info("Testing app: " + app.toString());
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
            processLog = new File(appDir.getAbsolutePath() + File.separator + "logs" + File.separator + "build-and-run.log");

            builderRoutine(2, app, report, cn, mn, appDir, processLog);

            final String archivedLogLocation = "See " + Path.of(BASE_DIR, "testsuite", "target", "archived-logs", cn, mn, processLog.getName());


            for (int i = 2; i < app.buildAndRunCmds.cmds.length; i++) {
                Path interimLog = null;
                try {
                    interimLog = Path.of(appDir.getAbsolutePath(), "logs", "interim_" + i + ".log");
                    List<String> cmd = getRunCommand(app.buildAndRunCmds.cmds[i]);
                    Files.writeString(interimLog, String.join(" ", cmd) + "\n", StandardOpenOption.CREATE_NEW);
                    Process p = runCommand(cmd, appDir, interimLog.toFile(), app);
                    p.waitFor(3, TimeUnit.SECONDS);
                    Logs.appendln(report, appDir.getAbsolutePath());
                    Logs.appendlnSection(report, String.join(" ", cmd));
                    Files.writeString(processLog.toPath(), Files.readString(interimLog) + "\n", StandardOpenOption.APPEND);

                    /// Check interim log for expected output

                    Logs.checkLog(cn, mn, app, interimLog.toFile());
                } finally {
                    if (interimLog != null) {
                        Files.delete(interimLog);
                    }
                }
            }
            Logs.checkLog(cn, mn, app, processLog);
        } finally {
            cleanup(null, cn, mn, report, app, processLog);
        }
    }

}
