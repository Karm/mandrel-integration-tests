package org.graalvm.tests.integration.utils;
/*
 * Copyright (c) 2023, Red Hat Inc. All rights reserved.
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

import org.graalvm.home.Version;
import org.graalvm.tests.integration.utils.versions.UsedVersion;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.graalvm.tests.integration.RuntimesSmokeTest.BASE_DIR;
import static org.graalvm.tests.integration.utils.Commands.parsePerfRecord;
import static org.graalvm.tests.integration.utils.Commands.parseSerialGCLog;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testing test suite...
 * Some "parsers" tests, mostly getting `perf` tools output, GC log output etc.
 */
@Tag("testing-testsuite")
public class UtilsTests {

    public final Path p = Path.of(BASE_DIR, "testsuite", "src", "test", "resources", "parse-serial-gc-build-and-run.log");
    public final Path p_new = Path.of(BASE_DIR, "testsuite", "src", "test", "resources", "parse-serial-gc-build-and-run-new.log");

    @Test
    public void parse() throws IOException {
        final String filename = "./target/quarkus-json_+ParseOnce-runner -XX:+PrintGC";
        final Commands.PerfRecord pr = parsePerfRecord(p, filename);
        final String expected = "" +
                "file ./target/quarkus-json_+ParseOnce-runner -XX:+PrintGC\n" +
                "taskclock 33056.280000\n" +
                "contextswitches 79926\n" +
                "cpumigrations 1843\n" +
                "pagefaults 1617544\n" +
                "cycles 92526592627\n" +
                "instructions 195646680405\n" +
                "branches 42442500297\n" +
                "branchmisses 235505448\n" +
                "secondstimeelapsed 32.637422\n";
        final String actual = String.format(
                "file %s\n" +
                        "taskclock %f\n" +
                        "contextswitches %d\n" +
                        "cpumigrations %d\n" +
                        "pagefaults %d\n" +
                        "cycles %d\n" +
                        "instructions %d\n" +
                        "branches %d\n" +
                        "branchmisses %d\n" +
                        "secondstimeelapsed %f\n"
                ,
                pr.file,
                pr.taskClock,
                pr.contextSwitches,
                pr.cpuMigrations,
                pr.pageFaults,
                pr.cycles,
                pr.instructions,
                pr.branches,
                pr.branchMisses,
                pr.secondsTimeElapsed);
        assertEquals(expected, actual, "perf tool output parsing method was likely changed without updating the test");
    }

    @Test
    public void parseSerialGC() throws IOException {
        final String filename = "./target/quarkus-json_+ParseOnce-runner -XX:+PrintGC";
        final Commands.SerialGCLog pr = parseSerialGCLog(p_new, filename, false);
        final String expected = "" +
                "timeSpentInGCs 14.758271\n" +
                "incrementalGCevents 61\n" +
                "fullGCevents 23\n";
        final String actual = String.format(
                "timeSpentInGCs %f\n" +
                        "incrementalGCevents %d\n" +
                        "fullGCevents %d\n"
                ,
                pr.timeSpentInGCs,
                pr.incrementalGCevents,
                pr.fullGCevents);
        assertEquals(expected, actual, "perf tool output parsing method was likely changed without updating the test");
    }

    @Test
    public void testSearchLogLinesContextBlocks() throws IOException {
        final String mockLog = """
                javax.net.ssl|DEBUG|30|main|2026-08-13 21:03:21.808 CEST|Consuming ServerHello handshake message (
                  "session id"          : "11111111111111111111111111111111",
                  "named group": Secp256r1
                )
                javax.net.ssl|DEBUG|30|main|2026-08-13 21:03:22.808 CEST|Consuming ServerHello handshake message (
                  "session id"          : "22222222222222222222222222222222",
                  "named group": X25519
                )
                javax.net.ssl|DEBUG|30|main|2026-08-13 21:03:23.808 CEST|Consuming ServerHello handshake message (
                  "session id"          : "87A18682D7FA53C939D8D1FC5CF2A8EA75F3E41952D673084781418DE9E885CD",
                  "named group": X25519MLKEM768
                )
                """;
        final Path tempLog = Files.createTempFile("mock-log", ".log");
        Files.writeString(tempLog, mockLog, StandardCharsets.UTF_8);
        final File logFile = tempLog.toFile();
        final String anchor = "Consuming ServerHello handshake message";
        final Pattern kemPattern = Pattern.compile(".*\"named group\"\\s*:\\s*X25519MLKEM768\\s*");
        final Pattern sessionPattern = Pattern.compile(".*\"session id\".*87A18682D7FA53C939D8D1FC5CF2A8EA.*");
        try {
            final boolean foundFirst = Commands.searchLogLines(logFile, anchor, 3, 1, StandardCharsets.UTF_8, kemPattern);
            assertFalse(foundFirst, "Fails when blocksToTry=1 because target is in block 3");
            final boolean foundThird = Commands.searchLogLines(logFile, anchor, 3, 3, StandardCharsets.UTF_8, kemPattern);
            assertTrue(foundThird, "Succeeds when blocksToTry=3 because target is in block 3");
            final boolean foundSecond = Commands.searchLogLines(logFile, anchor, 3, 2, StandardCharsets.UTF_8, kemPattern);
            assertFalse(foundSecond, "Fails when blocksToTry=2 because target is in block 3");
            final boolean foundMulti = Commands.searchLogLines(logFile, anchor, 3, 3, StandardCharsets.UTF_8, kemPattern, sessionPattern);
            assertTrue(foundMulti, "Succeeds finding both patterns in block 3");
            final Pattern missingPattern = Pattern.compile(".*\"session id\".*MISSING.*");
            final boolean foundMultiMissing = Commands.searchLogLines(logFile, anchor, 5, 5, StandardCharsets.UTF_8, kemPattern, missingPattern);
            assertFalse(foundMultiMissing, "Fails when one of the patterns is not in the block");
            final boolean foundLastBytes = Commands.searchLogLines(logFile, 250, StandardCharsets.UTF_8, kemPattern, sessionPattern);
            assertTrue(foundLastBytes, "Succeeds finding patterns in the last 250 bytes");
            final boolean failLastBytes = Commands.searchLogLines(logFile, 50, StandardCharsets.UTF_8, kemPattern, sessionPattern);
            assertFalse(failLastBytes, "Fails when looking at only the last 50 bytes");
            final boolean overflowLastBytes = Commands.searchLogLines(logFile, 9999, StandardCharsets.UTF_8, kemPattern);
            assertTrue(overflowLastBytes, "Succeeds even if bytesAtTheEnd is larger than the file");
        } finally {
            Files.deleteIfExists(tempLog);
        }
    }
}
