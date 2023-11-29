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

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;

import static org.graalvm.tests.integration.RuntimesSmokeTest.BASE_DIR;
import static org.graalvm.tests.integration.utils.Commands.parsePerfRecord;
import static org.graalvm.tests.integration.utils.Commands.parseSerialGCLog;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Testing test suite...
 * Some "parsers" tests, mostly getting `perf` tools output, GC log output etc.
 */
@Tag("testing-testsuite")
public class UtilsTests {

    public final Path p = Path.of(BASE_DIR, "testsuite", "src", "test", "resources", "parse-serial-gc-build-and-run.log");

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
                        "secondstimeelapsed %f\n",
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
        final Commands.SerialGCLog pr = parseSerialGCLog(p, filename, false);
        final String expected = "" +
                "timeSpentInGCs 11.725144\n" +
                "incrementalGCevents 23\n" +
                "fullGCevents 6\n";
        final String actual = String.format(
                "timeSpentInGCs %f\n" +
                        "incrementalGCevents %d\n" +
                        "fullGCevents %d\n",
                pr.timeSpentInGCs,
                pr.incrementalGCevents,
                pr.fullGCevents);
        assertEquals(expected, actual, "perf tool output parsing method was likely changed without updating the test");
    }
}
