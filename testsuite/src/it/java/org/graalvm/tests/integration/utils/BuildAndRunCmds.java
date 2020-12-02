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

import static org.graalvm.tests.integration.utils.Commands.BUILDER_IMAGE;
import static org.graalvm.tests.integration.utils.Commands.CONTAINER_RUNTIME;

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
    // Make sure you use an explicit --name when running the app as a container. It is used throughout the TS.
    QUARKUS_FULL_MICROPROFILE(new String[][]{
            new String[]{"mvn", "clean", "compile", "package", "-Pnative"},
            new String[]{Commands.IS_THIS_WINDOWS ? "target\\quarkus-runner" : "./target/quarkus-runner"}
    }),
    DEBUG_QUARKUS_FULL_MICROPROFILE(new String[][]{
            new String[]{"mvn", "clean", "compile", "package", "-Pnative", "-Dquarkus.native.debug.enabled=true"},
            new String[]{"mvn", "dependency:sources"},
            new String[]{Commands.IS_THIS_WINDOWS ? "target\\quarkus-runner" : "./target/quarkus-runner"}
    }),
    QUARKUS_BUILDER_IMAGE_ENCODING(new String[][]{
            new String[]{"mvn", "clean", "package", "-Pnative", "-Dquarkus.native.container-build=true",
                    "-Dquarkus.native.container-runtime=" + CONTAINER_RUNTIME,
                    "-Dquarkus.native.builder-image=" + BUILDER_IMAGE},
            new String[]{CONTAINER_RUNTIME, "build", "-f", "src/main/docker/Dockerfile.native", "-t", "my-quarkus-mandrel-app", "."},
            new String[]{CONTAINER_RUNTIME, "run", "-i", "--rm", "-p", "8080:8080",
                    "--name", ContainerNames.QUARKUS_BUILDER_IMAGE_ENCODING.name, "my-quarkus-mandrel-app"}
    }),
    DEBUG_QUARKUS_BUILDER_IMAGE_VERTX(new String[][]{
            new String[]{"mvn", "clean", "package", "-Pnative", "-Dquarkus.native.container-build=true",
                    "-Dquarkus.native.container-runtime=" + CONTAINER_RUNTIME,
                    "-Dquarkus.native.builder-image=" + BUILDER_IMAGE,
                    "-Dquarkus.native.debug.enabled=true"},
            new String[]{CONTAINER_RUNTIME, "build", "--network=host", "-f", "src/main/docker/Dockerfile.native", "-t", "my-quarkus-mandrel-app", "."},
            new String[]{CONTAINER_RUNTIME, "run", "--network=host", "--ulimit", "memlock=-1:-1", "-it", "-d", "--rm=true", "--memory-swappiness=0",
                    "--name", "quarkus_test_db", "-e", "POSTGRES_USER=quarkus_test", "-e", "POSTGRES_PASSWORD=quarkus_test",
                    "-e", "POSTGRES_DB=quarkus_test", "postgres:10.5"},
            new String[]{CONTAINER_RUNTIME, "run", "--network=host", "--cap-add=SYS_PTRACE", "--security-opt=seccomp=unconfined",
                    "-i", "-d", "--rm", "--name", ContainerNames.QUARKUS_BUILDER_IMAGE_ENCODING.name, "my-quarkus-mandrel-app"}
    }),
    MICRONAUT_HELLOWORLD(new String[][]{
            new String[]{"mvn", "package"},
            new String[]{"native-image", "-jar", "target/helloworld.jar", "target/helloWorld"},
            new String[]{Commands.IS_THIS_WINDOWS ? "target\\helloWorld" : "./target/helloWorld"}
    }),
    RANDOM_NUMBERS(new String[][]{
            new String[]{"mvn", "package"},
            new String[]{"native-image", "-jar", "target/random-numbers.jar", "target/random-numbers"},
            new String[]{Commands.IS_THIS_WINDOWS ? "target\\random-numbers" : "./target/random-numbers"}
    }),
    HELIDON_QUICKSTART_SE(new String[][]{
            new String[]{"mvn", "package"},
            new String[]{Commands.IS_THIS_WINDOWS ? "target\\helidon-quickstart-se" : "./target/helidon-quickstart-se"}
    }),
    TIMEZONES(new String[][]{
            new String[]{"mvn", "package"},
            new String[]{"native-image", "-J-Duser.country=CA", "-J-Duser.language=fr", "-jar", "target/timezones.jar", "target/timezones"},
            new String[]{Commands.IS_THIS_WINDOWS ? "target\\timezones" : "./target/timezones"}
    }),
    DEBUG_SYMBOLS_SMOKE(new String[][]{
            new String[]{"mvn", "package"},
            Commands.IS_THIS_WINDOWS ?
                    new String[]{"powershell", "-c", "\"Expand-Archive -Path test_data.txt.zip -DestinationPath target -Force\""}
                    :
                    new String[]{"unzip", "test_data.txt.zip", "-d", "target"},
            new String[]{"native-image", "-H:GenerateDebugInfo=1", "-H:+PreserveFramePointer", "-H:-DeleteLocalSymbols",
                    "-jar", "target/debug-symbols-smoke.jar", "target/debug-symbols-smoke"},
            new String[]{"java", "-jar", "./target/debug-symbols-smoke.jar"},
            new String[]{Commands.IS_THIS_WINDOWS ? "target\\debug-symbols-smoke" : "./target/debug-symbols-smoke"}
    });

    public final String[][] cmds;

    BuildAndRunCmds(String[][] cmds) {
        this.cmds = cmds;
    }
}
