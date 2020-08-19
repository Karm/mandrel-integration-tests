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

import org.graalvm.tests.integration.utils.Apps;
import org.graalvm.tests.integration.utils.Commands;
import org.graalvm.tests.integration.utils.Logs;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for build and start of applications with some real source code.
 *
 * @author Michal Karm Babacek <karm@redhat.com>
 */
@Tag("reproducers")
public class AppReproducersTest {

    private static final Logger LOGGER = Logger.getLogger(AppReproducersTest.class.getName());

    public static final String BASE_DIR = Commands.getBaseDir();

    @Test
    @Tag("randomNumbers")
    public void randomNumbersReinit(TestInfo testInfo) throws IOException, InterruptedException {
        Apps app = Apps.RANDOM_NUMBERS;
        LOGGER.info("Testing app: " + app.toString());
        Process process = null;
        File processLog = null;
        StringBuilder report = new StringBuilder();
        File appDir = new File(BASE_DIR + File.separator + app.dir);
        String cn = testInfo.getTestClass().get().getCanonicalName();
        String mn = testInfo.getTestMethod().get().getName();
        try {
            // Cleanup
            Commands.cleanTarget(app);
            Files.createDirectories(Paths.get(appDir.getAbsolutePath() + File.separator + "logs"));

            // Build
            processLog = new File(appDir.getAbsolutePath() + File.separator + "logs" + File.separator + "build-and-run.log");

            // The last command is reserved for running it
            assertTrue(app.buildAndRunCmds.cmds.length > 1);
            Logs.appendln(report, "# " + cn + ", " + mn);
            for (int i = 0; i < app.buildAndRunCmds.cmds.length - 1; i++) {
                // We cannot run commands in parallel, we need them to follow one after another
                ExecutorService buildService = Executors.newFixedThreadPool(1);
                List<String> cmd = Commands.getBuildCommand(app.buildAndRunCmds.cmds[i]);
                buildService.submit(new Commands.ProcessRunner(appDir, processLog, cmd, 30)); // Timeout for Maven downloading the Internet
                Logs.appendln(report, (new Date()).toString());
                Logs.appendln(report, appDir.getAbsolutePath());
                Logs.appendlnSection(report, String.join(" ", cmd));
                buildService.shutdown();
                buildService.awaitTermination(30, TimeUnit.MINUTES);
            }
            assertTrue(processLog.exists());
            LOGGER.info("Running...#1");
            List<String> cmd = Commands.getRunCommand(app.buildAndRunCmds.cmds[app.buildAndRunCmds.cmds.length - 1]);
            process = Commands.runCommand(cmd, appDir, processLog);
            process.waitFor(5, TimeUnit.SECONDS);
            Logs.appendln(report, appDir.getAbsolutePath());
            Logs.appendlnSection(report, String.join(" ", cmd));

            LOGGER.info("Running...#2");
            cmd = Commands.getRunCommand(app.buildAndRunCmds.cmds[app.buildAndRunCmds.cmds.length - 1]);
            process = Commands.runCommand(cmd, appDir, processLog);
            process.waitFor(5, TimeUnit.SECONDS);
            Logs.appendln(report, appDir.getAbsolutePath());
            Logs.appendlnSection(report, String.join(" ", cmd));

            Pattern p = Pattern.compile("Hello, (.*)");
            Set<String> parsedLines = new HashSet<>(4);
            try (Scanner sc = new Scanner(processLog, UTF_8)) {
                while (sc.hasNextLine()) {
                    Matcher m = p.matcher(sc.nextLine());
                    if (m.matches()) {
                        parsedLines.add(m.group(1));
                    }
                }
            }

            LOGGER.info(parsedLines.toString());

            assertEquals(4, parsedLines.size(), "There should have been 4 distinct lines in the log," +
                    "showing 2 different pseudorandom sequences. The fact that there are less than 4 means the native image" +
                    "was not properly re-seeded. See https://github.com/oracle/graal/issues/2265.");

            Commands.processStopper(process, false);
            Logs.checkLog(cn, mn, app, processLog);
        } finally {
            // Make sure processes are down even if there was an exception / failure
            if (process != null) {
                Commands.processStopper(process, true);
            }
            // Archive logs no matter what
            Logs.archiveLog(cn, mn, processLog);
            Logs.writeReport(cn, mn, report.toString());
            Commands.cleanTarget(app);
        }
    }
}
