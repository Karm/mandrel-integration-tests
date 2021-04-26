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

import java.util.regex.Pattern;

/**
 * GDB commands and expected output
 *
 * @author Michal Karm Babacek <karm@redhat.com>
 */
public enum GDBSession {
    NONE(new CP[]{}),
    DEBUG_SYMBOLS_SMOKE(new CP[]{
            new CP("run < ./test_data_small.txt\n",
                    Pattern.compile(".*fdc7c50f390c145bc58a0bedbe5e6d2e35177ac73d12e2b23df149ce496a5572.*exited normally.*",
                            Pattern.DOTALL)),
            new CP("info functions .*smoke.*\n",
                    Pattern.compile(
                            ".*File debug_symbols_smoke/ClassA.java:.*" +
                                    "class java.lang.String \\*debug_symbols_smoke.ClassA::toString\\(void\\).*" +
                                    "File debug_symbols_smoke/Main\\$\\$Lambda.*.java:.*" +
                                    "void debug_symbols_smoke.Main..Lambda..*::accept\\(java.lang.Object \\*\\).*" +
                                    "File debug_symbols_smoke/Main.java:.*" +
                                    "void debug_symbols_smoke.Main::lambda\\$thisIsTheEnd\\$0\\(java.io.ByteArrayOutputStream \\*, debug_symbols_smoke.ClassA \\*\\).*" +
                                    "void debug_symbols_smoke.Main::main\\(java.lang.String\\[\\] \\*\\).*" +
                                    "void debug_symbols_smoke.Main::thisIsTheEnd\\(java.util.List \\*\\).*"
                            , Pattern.DOTALL)),
            new CP("break Main.java:70\n",
                    Pattern.compile(".*Breakpoint 1 at .*: file debug_symbols_smoke/Main.java, line 70.*",
                            Pattern.DOTALL)),
            new CP("break Main.java:71\n",
                    Pattern.compile(".*Breakpoint 2 at .*: file debug_symbols_smoke/Main.java, line 71.*",
                            Pattern.DOTALL)),
            new CP("break Main.java:76\n",
                    Pattern.compile(".*Breakpoint 3 at .*: file debug_symbols_smoke/Main.java, line 76.*",
                            Pattern.DOTALL)),
            new CP("run < ./test_data_small.txt\n",
                    Pattern.compile(".*Breakpoint 1, .*while \\(sc.hasNextLine\\(\\)\\).*",
                            Pattern.DOTALL)),
            new CP("c\n",
                    Pattern.compile(".*Breakpoint 3, debug_symbols_smoke.Main::main.*at debug_symbols_smoke/Main.java:76.*String l = sc.nextLine\\(\\);.*",
                            Pattern.DOTALL)),
            new CP("c\n",
                    Pattern.compile(".*Breakpoint 2, debug_symbols_smoke.Main::main.*at debug_symbols_smoke/Main.java:71.* if \\(myString != null.*",
                            Pattern.DOTALL)),
            new CP("c\n",
                    Pattern.compile(".*Breakpoint 2, debug_symbols_smoke.Main::main.*at debug_symbols_smoke/Main.java:71.* if \\(myString != null.*",
                            Pattern.DOTALL)),
            new CP("d 2\n",
                    Pattern.compile(".*", Pattern.DOTALL)),
            new CP("c\n",
                    Pattern.compile(".*fdc7c50f390c145bc58a0bedbe5e6d2e35177ac73d12e2b23df149ce496a5572.*exited normally.*",
                            Pattern.DOTALL)),
            new CP("list ClassA.java:30\n",
                    Pattern.compile(".*ClassA\\(int myNumber, String myString\\).*",
                            Pattern.DOTALL)),
    }),
    DEBUG_QUARKUS_FULL_MICROPROFILE(new CP[]{
            new CP("b ConfigTestController.java:33\n",
                    Pattern.compile(".*Breakpoint 1 at .*: file com/example/quarkus/config/ConfigTestController.java, line 33.*",
                            Pattern.DOTALL)),
            new CP("run&\n",
                    Pattern.compile(".*Installed features:.*", Pattern.DOTALL)),
            new CP("GOTO URL http://localhost:8080/data/config/lookup",
                    Pattern.compile(".*lookup value.*", Pattern.DOTALL)),
            new CP("bt\n",
                    Pattern.compile(".*at.*com/example/quarkus/config/ConfigTestController.java:33.*", Pattern.DOTALL)),
            new CP("list\n",
                    Pattern.compile(".*String value = config.getValue\\(\"value\", String.class\\);.*", Pattern.DOTALL)),
            new CP("c&\n",
                    Pattern.compile(".*Continuing.*", Pattern.DOTALL)),
    }),
    DEBUG_QUARKUS_BUILDER_IMAGE_VERTX(new CP[]{
            new CP("b Fruit.java:48\n",
                    Pattern.compile(".*Breakpoint 1.*file org/acme/vertx/Fruit.java,.*line 48.*", Pattern.DOTALL)),
            new CP("c&\n",
                    Pattern.compile(".*", Pattern.DOTALL)),
            new CP("GOTO URL http://localhost:8080/fruits",
                    Pattern.compile(".*Apple.*Orange.*Pear.*", Pattern.DOTALL)),
            new CP("bt\n",
                    Pattern.compile(".*at org/acme/vertx/Fruit.java:48.*", Pattern.DOTALL)),
            new CP("list\n",
                    Pattern.compile(".*48.*return client.query\\(\"SELECT id, name FROM fruits ORDER BY name ASC.*", Pattern.DOTALL)),
            new CP("c&\n",
                    Pattern.compile(".*", Pattern.DOTALL)),
    });

    public final CP[] gdbOutput;

    GDBSession(CP[] gdbOutput) {
        this.gdbOutput = gdbOutput;
    }
}
