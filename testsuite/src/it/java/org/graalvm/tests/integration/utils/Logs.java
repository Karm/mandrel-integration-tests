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
package org.graalvm.tests.integration.utils;

import org.apache.commons.lang3.StringUtils;
import org.jboss.logging.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Scanner;
import java.util.Set;
import java.util.regex.Pattern;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static org.graalvm.tests.integration.RuntimesSmokeTest.BASE_DIR;
import static org.graalvm.tests.integration.utils.Commands.FAIL_ON_PERF_REGRESSION;
import static org.graalvm.tests.integration.utils.Commands.IS_THIS_MACOS;
import static org.graalvm.tests.integration.utils.Commands.IS_THIS_WINDOWS;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Michal Karm Babacek <karm@redhat.com>
 */
public class Logs {
    private static final Logger LOGGER = Logger.getLogger(Logs.class.getName());
    private static final Pattern WARN_ERROR_DETECTION_PATTERN = Pattern.compile("(?i:.*(ERROR|SEVERE|WARN|No such file|Not found|unknown).*)");
    public static final long SKIP = -1L;

    public static void checkLog(String testClass, String testMethod, Apps app, File log) throws IOException {
        final boolean inContainer = app.runtimeContainer != ContainerNames.NONE;
        final Pattern[] whitelistPatterns = new Pattern[app.whitelistLogLines.get(inContainer).length + WhitelistLogLines.ALL.get(inContainer).length];
        System.arraycopy(app.whitelistLogLines.get(inContainer), 0, whitelistPatterns, 0, app.whitelistLogLines.get(inContainer).length);
        System.arraycopy(WhitelistLogLines.ALL.get(inContainer), 0, whitelistPatterns, app.whitelistLogLines.get(inContainer).length, WhitelistLogLines.ALL.get(inContainer).length);
        try (Scanner sc = new Scanner(log, UTF_8)) {
            final Set<String> offendingLines = new HashSet<>();
            while (sc.hasNextLine()) {
                final String line = sc.nextLine();
                final boolean error = WARN_ERROR_DETECTION_PATTERN.matcher(line).matches();
                boolean whiteListed = false;
                if (error) {
                    for (Pattern p : whitelistPatterns) {
                        if (p.matcher(line).matches()) {
                            whiteListed = true;
                            LOGGER.info(log.getName() + " log for " + testMethod + " contains whitelisted error: `" + line + "'");
                            break;
                        }
                    }
                    if (!whiteListed) {
                        offendingLines.add(line);
                    }
                }
            }
            assertTrue(offendingLines.isEmpty(),
                    log.getName() + " log should not contain error or warning lines that are not whitelisted. " +
                            "See " + Path.of(BASE_DIR, "testsuite", "target", "archived-logs", testClass, testMethod, log.getName()) +
                            " and check these offending " + offendingLines.size() + " lines: \n" + String.join("\n", offendingLines));
        }
    }

    public enum Mode {
        JVM,
        NATIVE,
        DIFF_JVM,
        DIFF_NATIVE,
        NONE;

        @Override
        public String toString() {
            return name().toLowerCase();
        }
    }

    public static void checkThreshold(Apps app, Mode mode, long executableSizeKb, long rssKb, long timeToFirstOKRequest,
            long timeToFinishMs, long mean, long p50, long p90) {

        final Path properties = Path.of(BASE_DIR, app.dir, "threshold.conf");
        if (app.thresholdProperties.isEmpty() &&
                (executableSizeKb != SKIP || rssKb != SKIP || timeToFirstOKRequest != SKIP || timeToFinishMs != SKIP)) {
            LOGGER.warn("It seem there is no " +properties +
                    ". Skipping checking thresholds.");
            return;
        }
        final String propPrefix = (IS_THIS_WINDOWS ? "windows" : (IS_THIS_MACOS ? "macos" : "linux")) +
                ((app.runtimeContainer != ContainerNames.NONE) ? ".container" : "") +
                ((mode != Mode.NONE) ? "." + mode : "");
        final List<String> failures = new ArrayList<>();

        if (executableSizeKb != SKIP) {
            final String key = propPrefix + ".executable.size.threshold.kB";
            if (app.thresholdProperties.containsKey(key)) {
                long executableSizeThresholdKb = app.thresholdProperties.get(key);
                assertThreshold(failures, executableSizeKb <= executableSizeThresholdKb,
                        "Application " + app + (mode != null ? " in mode " + mode : "") + " executable size " +
                                ((mode == Mode.DIFF_JVM || mode == Mode.DIFF_NATIVE) ? "overhead is" : "is ") +
                                executableSizeKb + " kB, which is over " +
                                executableSizeThresholdKb + " kB threshold by " + percentageValOverTh(executableSizeKb, executableSizeThresholdKb) + "%.", false);
            } else {
                LOGGER.error("executableSizeKb was to be checked, but there is no " + key + " in " + properties);
            }
        }

        if (timeToFirstOKRequest != SKIP) {
            final String key = propPrefix + ".time.to.first.ok.request.threshold.ms";
            if (app.thresholdProperties.containsKey(key)) {
                long timeToFirstOKRequestThresholdMs = app.thresholdProperties.get(key);
                assertThreshold(failures, timeToFirstOKRequest <= timeToFirstOKRequestThresholdMs,
                        "Application " + app + (mode != null ? " in mode " + mode : "") +
                                " took " + timeToFirstOKRequest + " ms " + ((mode == Mode.DIFF_JVM || mode == Mode.DIFF_NATIVE) ? "more " : "") +
                                "to get the first OK request, which is over " +
                                timeToFirstOKRequestThresholdMs + " ms threshold by " + percentageValOverTh(timeToFirstOKRequest, timeToFirstOKRequestThresholdMs) + "%.", true);
            } else {
                LOGGER.error("timeToFirstOKRequest was to be checked, but there is no " + key + " in " + properties);
            }
        }

        if (rssKb != SKIP) {
            final String key = propPrefix + ".RSS.threshold.kB";
            if (app.thresholdProperties.containsKey(key)) {
                long rssThresholdKb = app.thresholdProperties.get(key);
                assertThreshold(failures, rssKb <= rssThresholdKb,
                        "Application " + app + (mode != null ? " in mode " + mode : "") +
                                " consumed " + rssKb + " kB of RSS memory " + ((mode == Mode.DIFF_JVM || mode == Mode.DIFF_NATIVE) ? "more " : "") + ", which is over " +
                                rssThresholdKb + " kB threshold by " + percentageValOverTh(rssKb, rssThresholdKb) + "%.", false);
            } else {
                LOGGER.error("rssKb was to be checked, but there is no " + key + " in " + properties);
            }
        }

        if (timeToFinishMs != SKIP) {
            final String key = propPrefix + ".time.to.finish.threshold.ms";
            if (app.thresholdProperties.containsKey(key)) {
                long timeToFinishThresholdMs = app.thresholdProperties.get(key);
                assertThreshold(failures, timeToFinishMs <= timeToFinishThresholdMs,
                        "Application " + app + (mode != null ? " in mode " + mode : "") + " took " +
                                timeToFinishMs + " ms " + ((mode == Mode.DIFF_JVM || mode == Mode.DIFF_NATIVE) ? "more " : "") + "to finish, which is over " +
                                timeToFinishThresholdMs + " ms threshold by " + percentageValOverTh(timeToFinishMs, timeToFinishThresholdMs) + "%.", true);
            } else {
                LOGGER.error("timeToFinishMs was to be checked, but there is no " + key + " in " + properties);
            }
        }

        if (mean != SKIP) {
            final String key = propPrefix + ".mean.latency";
            if (app.thresholdProperties.containsKey(key)) {
                long meanThreshold = app.thresholdProperties.get(key);
                assertThreshold(failures, mean <= meanThreshold,
                        "Application " + app + (mode != null ? " in mode " + mode : "") + " has mean response latency " +
                                mean + ((mode == Mode.DIFF_JVM || mode == Mode.DIFF_NATIVE) ? " more" : " ") + ", which is over " +
                                meanThreshold + " threshold by " + percentageValOverTh(mean, meanThreshold) + "%.", true);
            } else {
                LOGGER.error("mean was to be checked, but there is no " + key + " in " + properties);
            }
        }

        if (p50 != SKIP) {
            final String key = propPrefix + ".p50.latency";
            if (app.thresholdProperties.containsKey(key)) {
                long p50Threshold = app.thresholdProperties.get(key);
                assertThreshold(failures, p50 <= p50Threshold,
                        "Application " + app + (mode != null ? " in mode " + mode : "") + " has p50 response latency " +
                                p50 + ((mode == Mode.DIFF_JVM || mode == Mode.DIFF_NATIVE) ? " more" : "") + ", which is over " +
                                p50Threshold + "  threshold by " + percentageValOverTh(p50, p50Threshold) + "%.", true);
            } else {
                LOGGER.error("p99 was to be checked, but there is no " + key + " in " + properties);
            }
        }

        if (p90 != SKIP) {
            final String key = propPrefix + ".p90.latency";
            if (app.thresholdProperties.containsKey(key)) {
                long p90Threshold = app.thresholdProperties.get(key);
                assertThreshold(failures, p90 <= p90Threshold,
                        "Application " + app + (mode != null ? " in mode " + mode : "") + " has p90 response latency " +
                                p90 + ((mode == Mode.DIFF_JVM || mode == Mode.DIFF_NATIVE) ? " more" : "") + ", which is over " +
                                p90Threshold + "  threshold by " + percentageValOverTh(p90, p90Threshold) + "%.", true);
            } else {
                LOGGER.error("p90 was to be checked, but there is no " + key + " in " + properties);
            }
        }

        assertTrue(failures.isEmpty(), "\n" + String.join("\n", failures) + "\n");
    }

    public static void assertThreshold(List<String> failures, boolean condition, String message, boolean timeSensitive) {
        if (!condition) {
            if (FAIL_ON_PERF_REGRESSION == FailOnPerfRegressionEnum.TRUE ||
                    (FAIL_ON_PERF_REGRESSION == FailOnPerfRegressionEnum.TIME_SENSITIVE && timeSensitive) ||
                    (FAIL_ON_PERF_REGRESSION == FailOnPerfRegressionEnum.NON_TIME_SENSITIVE && !timeSensitive)) {
                failures.add(message);
            } else {
                LOGGER.error(message);
            }
        }
    }

    public static int percentageValOverTh(float value, float threshold) {
        return Math.round(value / (threshold / 100f)) - 100;
    }

    public static void checkThreshold(Apps app, long executableSizeKb, long rssKb, long timeToFirstOKRequest) {
        checkThreshold(app, Mode.NONE, executableSizeKb, rssKb, timeToFirstOKRequest, SKIP, SKIP, SKIP, SKIP);
    }

    public static void checkThreshold(Apps app, Mode mode, long executableSizeKb, long rssKb, long timeToFirstOKRequest, long timeToFinishMs) {
        checkThreshold(app, mode, executableSizeKb, rssKb, timeToFirstOKRequest, timeToFinishMs, SKIP, SKIP, SKIP);
    }

    public static void checkThreshold(Apps app, Mode mode, long executableSizeKb, long rssKb, long timeToFirstOKRequest, long mean, long p50, long p90) {
        checkThreshold(app, mode, executableSizeKb, rssKb, timeToFirstOKRequest, SKIP, mean, p50, p90);
    }

    public static void archiveLog(String testClass, String testMethod, File log) throws IOException {
        if (log == null) {
            LOGGER.warn("log must not be null. Skipping operation.");
            return;
        }
        if (!log.exists()) {
            LOGGER.warn(log + " must be a valid, existing file. Skipping operation.");
            return;
        }
        if (StringUtils.isBlank(testClass)) {
            throw new IllegalArgumentException("testClass must not be blank");
        }
        if (StringUtils.isBlank(testMethod)) {
            throw new IllegalArgumentException("testMethod must not be blank");
        }
        final Path destDir = getLogsDir(testClass, testMethod);
        Files.createDirectories(destDir);
        final String filename = log.getName();
        Files.copy(log.toPath(), Paths.get(destDir.toString(), filename), REPLACE_EXISTING);
    }

    public static void writeReport(String testClass, String testMethod, String text) throws IOException {
        final Path destDir = getLogsDir(testClass, testMethod);
        Files.createDirectories(destDir);
        Files.write(Paths.get(destDir.toString(), "report.md"), text.getBytes(UTF_8), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        final Path aggregateReport = Paths.get(getLogsDir().toString(), "aggregated-report.md");
        if (Files.notExists(aggregateReport)) {
            Files.write(aggregateReport, ("# Aggregated Report\n\n").getBytes(UTF_8), StandardOpenOption.CREATE);
        }
        Files.write(aggregateReport, text.getBytes(UTF_8), StandardOpenOption.APPEND);
    }

    /**
     * Markdown needs two newlines to make a new paragraph.
     */
    public static void appendln(StringBuilder s, String text) {
        s.append(text);
        s.append("\n\n");
    }

    public static void appendlnSection(StringBuilder s, String text) {
        s.append(text);
        s.append("\n\n---\n");
    }

    public static Path getLogsDir(String testClass, String testMethod) throws IOException {
        final Path destDir = new File(getLogsDir(testClass) + File.separator + testMethod).toPath();
        Files.createDirectories(destDir);
        return destDir;
    }

    public static Path getLogsDir(String testClass) throws IOException {
        final Path destDir = new File(getLogsDir() + File.separator + testClass).toPath();
        Files.createDirectories(destDir);
        return destDir;
    }

    public static Path getLogsDir() throws IOException {
        final Path destDir = new File(BASE_DIR + File.separator + "testsuite" + File.separator + "target" +
                File.separator + "archived-logs").toPath();
        Files.createDirectories(destDir);
        return destDir;
    }

    public static void logMeasurements(LogBuilder.Log log, Path path) throws IOException {
        if (Files.notExists(path)) {
            Files.write(path, (log.headerCSV + "\n").getBytes(UTF_8), StandardOpenOption.CREATE);
        }
        Files.write(path, (log.lineCSV + "\n").getBytes(UTF_8), StandardOpenOption.APPEND);
        LOGGER.info("\n" + log.headerCSV + "\n" + log.lineCSV);
    }
}
