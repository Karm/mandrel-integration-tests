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

/**
 * BuildAndRunCmds
 *
 * The last command is used to run the final binary.
 * All previous commands are used to build it.
 *
 * @author Michal Karm Babacek <karm@redhat.com>
 */
public enum BuildAndRunCmds {
    // Note that at least 2 command are expected. One or more to build. The last one to run the app.
    QUARKUS_FULL_MICROPROFILE(new String[][]{
            new String[]{"mvn", "clean", "compile", "package", "-Pnative"},
            new String[]{Commands.isThisWindows ? "target\\quarkus-runner" : "./target/quarkus-runner"}
    }),
    MICRONAUT_HELLOWORLD(new String[][]{
            new String[]{"mvn", "package"},
            new String[]{"native-image", "-jar", "target/helloworld.jar", "target/helloWorld"},
            new String[]{Commands.isThisWindows ? "target\\helloWorld" : "./target/helloWorld"}
    }),
    RANDOM_NUMBERS(new String[][]{
            new String[]{"mvn", "package"},
            new String[]{"native-image", "-jar", "target/random-numbers.jar", "target/random-numbers"},
            new String[]{Commands.isThisWindows ? "target\\random-numbers" : "./target/random-numbers"}
    }),
    HELIDON_QUICKSTART_SE(new String[][]{
            new String[]{"mvn", "package"},
            new String[]{Commands.isThisWindows ? "target\\helidon-quickstart-se" : "./target/helidon-quickstart-se"}
    });

    public final String[][] cmds;

    BuildAndRunCmds(String[][] cmds) {
        this.cmds = cmds;
    }
}
