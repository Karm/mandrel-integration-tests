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

import java.io.File;

import static org.graalvm.tests.integration.AppReproducersTest.BASE_DIR;
import static org.graalvm.tests.integration.utils.Commands.BUILDER_IMAGE;
import static org.graalvm.tests.integration.utils.Commands.CONTAINER_RUNTIME;
import static org.graalvm.tests.integration.utils.Commands.IS_THIS_WINDOWS;
import static org.graalvm.tests.integration.utils.Commands.QUARKUS_VERSION;
import static org.graalvm.tests.integration.utils.Commands.JFR_MONITORING_SWITCH_TOKEN;
import static org.graalvm.tests.integration.utils.Commands.getUnixUIDGID;

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
            new String[]{"mvn", "clean", "compile", "package", "-Pnative", "-Dquarkus.version=" + QUARKUS_VERSION.getVersionString()},
            new String[]{IS_THIS_WINDOWS ? "target\\quarkus-runner.exe" : "./target/quarkus-runner"}
    }),
    DEBUG_QUARKUS_FULL_MICROPROFILE(new String[][]{
            new String[]{"mvn", "clean", "compile", "package", "-Pnative", "-Dquarkus.native.debug.enabled=true", "-Dquarkus.version=" + QUARKUS_VERSION.getVersionString()},
            new String[]{"mvn", "dependency:sources", "-Dquarkus.version=" + QUARKUS_VERSION.getVersionString()},
            new String[]{IS_THIS_WINDOWS ? "target\\quarkus-runner.exe" : "./target/quarkus-runner"}
    }),
    QUARKUS_BUILDER_IMAGE_ENCODING(new String[][]{
            new String[]{"mvn", "clean", "package", "-Pnative", "-Dquarkus.native.container-build=true",
                    "-Dquarkus.native.container-runtime=" + CONTAINER_RUNTIME,
                    "-Dquarkus.native.builder-image=" + BUILDER_IMAGE, "-Dquarkus.version=" + QUARKUS_VERSION.getVersionString()},
            new String[]{CONTAINER_RUNTIME, "build", "-f", "src/main/docker/Dockerfile.native", "-t", "my-quarkus-mandrel-app", "."},
            new String[]{CONTAINER_RUNTIME, "run", "-i", "--rm", "-p", "8080:8080",
                    "--name", ContainerNames.QUARKUS_BUILDER_IMAGE_ENCODING.name, "my-quarkus-mandrel-app"}
    }),
    DEBUG_QUARKUS_BUILDER_IMAGE_VERTX(new String[][]{
            new String[]{"mvn", "clean", "package", "-Pnative", "-Dquarkus.native.container-build=true",
                    "-Dquarkus.native.container-runtime=" + CONTAINER_RUNTIME,
                    "-Dquarkus.native.builder-image=" + BUILDER_IMAGE,
                    "-Dquarkus.native.debug.enabled=true", "-Dquarkus.version=" + QUARKUS_VERSION.getVersionString()},
            new String[]{"mvn", "dependency:sources", "-Dquarkus.version=" + QUARKUS_VERSION.getVersionString()},
            new String[]{CONTAINER_RUNTIME, "build", "--network=host", "-f", "src/main/docker/Dockerfile.native", "-t", "my-quarkus-mandrel-app", "."},
            new String[]{CONTAINER_RUNTIME, "run", "--network=host", "--ulimit", "memlock=-1:-1", "-it", "-d", "--rm=true",
                    "--name", "quarkus_test_db", "-e", "POSTGRES_USER=quarkus_test", "-e", "POSTGRES_PASSWORD=quarkus_test",
                    "-e", "POSTGRES_DB=quarkus_test", "quay.io/debezium/postgres:10"},
            new String[]{CONTAINER_RUNTIME, "run", "--network=host", "--cap-add=SYS_PTRACE", "--security-opt=seccomp=unconfined",
                    "-i", "-d", "--rm", "--name", ContainerNames.DEBUG_QUARKUS_BUILDER_IMAGE_VERTX.name, "my-quarkus-mandrel-app"}
    }),
    MICRONAUT_HELLOWORLD(new String[][]{
            new String[]{"mvn", "package"},
            new String[]{"native-image", "-jar", "target/helloworld.jar", "target/helloWorld"},
            new String[]{IS_THIS_WINDOWS ? "target\\helloWorld.exe" : "./target/helloWorld"}
    }),
    RANDOM_NUMBERS(new String[][]{
            new String[]{"mvn", "package"},
            new String[]{"native-image", "-jar", "target/random-numbers.jar", "target/random-numbers"},
            new String[]{IS_THIS_WINDOWS ? "target\\random-numbers.exe" : "./target/random-numbers"}
    }),
    HELIDON_QUICKSTART_SE(new String[][]{
            new String[]{"mvn", "package"},
            new String[]{IS_THIS_WINDOWS ? "target\\helidon-quickstart-se.exe" : "./target/helidon-quickstart-se"}
    }),
    TIMEZONES(new String[][]{
            new String[]{"mvn", "package"},
            new String[]{"native-image", "-J-Duser.country=CA", "-J-Duser.language=fr", "-jar", "target/timezones.jar", "target/timezones"},
            new String[]{IS_THIS_WINDOWS ? "target\\timezones.exe" : "./target/timezones"}
    }),
    RECORDANNOTATIONS(new String[][]{
            new String[]{"mvn", "package"},
            new String[]{"native-image", "--no-fallback", "-jar", "target/recordannotations.jar", "target/recordannotations"},
            new String[]{IS_THIS_WINDOWS ? "target\\recordannotations.exe" : "./target/recordannotations"}
    }),
    VERSIONS(new String[][]{
            new String[]{"mvn", "package"},
            new String[]{"native-image", "--features=org.graalvm.home.HomeFinderFeature", "-jar", "target/version.jar", "target/version"},
            new String[]{IS_THIS_WINDOWS ? "target\\version.exe" : "./target/version"}
    }),
    IMAGEIO(new String[][]{
            new String[]{"mvn", "clean", "package"},
            new String[]{"java", "-agentlib:native-image-agent=config-output-dir=src/main/resources/META-INF/native-image", "-jar", "target/imageio.jar"},
            new String[]{"jar", "uf", "target/imageio.jar", "-C", "src/main/resources/", "META-INF"},
            new String[]{"native-image", "-H:IncludeResources=Grace_M._Hopper.jp2,MyFreeMono.ttf,MyFreeSerif.ttf", "--no-fallback", "-jar", "target/imageio.jar", "target/imageio"},
            new String[]{IS_THIS_WINDOWS ? "target\\imageio.exe" : "./target/imageio"}
    }),
    IMAGEIO_BUILDER_IMAGE(new String[][]{
            // Bring Your Own Maven (not a part of the builder image toolchain)
            new String[]{"mvn", "clean", "package"},
            // TODO: Ad -u: Test access rights with -u on Windows, Docker Desktop Hyper-V backend vs. WSL2 backend.
            // Java from Builder image container is used for the sake of consistence.
            new String[]{CONTAINER_RUNTIME, "run", IS_THIS_WINDOWS ? "" : "-u", IS_THIS_WINDOWS ? "" : getUnixUIDGID(),
                    "-t", "--entrypoint", "java", "-v", BASE_DIR + File.separator + "apps" + File.separator + "imageio:/project:z",
                    BUILDER_IMAGE,
                    "-agentlib:native-image-agent=config-output-dir=src/main/resources/META-INF/native-image", "-jar", "target/imageio.jar"},
            // Jar could be used locally, but we use the one from container too.
            new String[]{CONTAINER_RUNTIME, "run", IS_THIS_WINDOWS ? "" : "-u", IS_THIS_WINDOWS ? "" : getUnixUIDGID(),
                    "-t", "--entrypoint", "jar", "-v", BASE_DIR + File.separator + "apps" + File.separator + "imageio:/project:z",
                    BUILDER_IMAGE,
                    "uf", "target/imageio.jar", "-C", "src/main/resources/", "META-INF"},
            // Native image build itself (jar was updated with properties in the previous step)
            new String[]{CONTAINER_RUNTIME, "run", IS_THIS_WINDOWS ? "" : "-u", IS_THIS_WINDOWS ? "" : getUnixUIDGID(),
                    "-t", "-v", BASE_DIR + File.separator + "apps" + File.separator + "imageio:/project:z",
                    BUILDER_IMAGE,
                    "-H:IncludeResources=Grace_M._Hopper.jp2,MyFreeMono.ttf,MyFreeSerif.ttf", "--no-fallback", "-jar", "target/imageio.jar", "target/imageio"},
            // We build a runtime image, ubi 8 minimal based, runtime dependencies installed
            new String[]{CONTAINER_RUNTIME, "build", "--network=host", "-t", ContainerNames.IMAGEIO_BUILDER_IMAGE.name, "."},
            // We have to run int he same env as we run the java part above, i.e. in the same container base.
            // Hashsums of font rotations would differ otherwise as your linux host might have different freetype native libs.
            new String[]{CONTAINER_RUNTIME, "run", IS_THIS_WINDOWS ? "" : "-u", IS_THIS_WINDOWS ? "" : getUnixUIDGID(),
                    "-t", "-v", BASE_DIR + File.separator + "apps" + File.separator + "imageio:/work:z",
                    ContainerNames.IMAGEIO_BUILDER_IMAGE.name, "/work/target/imageio"}
    }),
    DEBUG_SYMBOLS_SMOKE(new String[][]{
            new String[]{"mvn", "package"},
            IS_THIS_WINDOWS ?
                    new String[]{"powershell", "-c", "\"Expand-Archive -Path test_data.txt.zip -DestinationPath target -Force\""}
                    :
                    new String[]{"unzip", "test_data.txt.zip", "-d", "target"},
            new String[]{"native-image", "-H:GenerateDebugInfo=1", "-H:+PreserveFramePointer", "-H:-DeleteLocalSymbols",
                    "-jar", "target/debug-symbols-smoke.jar", "target/debug-symbols-smoke"},
            new String[]{"java", "-jar", "./target/debug-symbols-smoke.jar"},
            new String[]{IS_THIS_WINDOWS ? "target\\debug-symbols-smoke.exe" : "./target/debug-symbols-smoke"}
    }),
    JFR_SMOKE(new String[][]{
            new String[]{"mvn", "package"},
            IS_THIS_WINDOWS ?
                    new String[]{"powershell", "-c", "\"Expand-Archive -Path test_data.txt.zip -DestinationPath target -Force\""}
                    :
                    new String[]{"unzip", "test_data.txt.zip", "-d", "target"},
            new String[]{"native-image", JFR_MONITORING_SWITCH_TOKEN, "-jar", "target/debug-symbols-smoke.jar", "target/debug-symbols-smoke"},
            new String[]{"java",
                    "-XX:+FlightRecorder",
                    "-XX:StartFlightRecording=filename=logs/flight-java.jfr",
                    "-Xlog:jfr", "-jar", "./target/debug-symbols-smoke.jar"},
            new String[]{IS_THIS_WINDOWS ? "target\\debug-symbols-smoke.exe" : "./target/debug-symbols-smoke",
                    "-XX:+FlightRecorder",
                    "-XX:StartFlightRecording=filename=logs/flight-native.jfr",
                    "-XX:FlightRecorderLogging=jfr"}
    }),
    JFR_SMOKE_BUILDER_IMAGE(new String[][]{
            new String[]{"mvn", "package"},
            new String[]{"unzip", "test_data.txt.zip", "-d", "target"},
            new String[]{
                    CONTAINER_RUNTIME, "run", "-u", IS_THIS_WINDOWS ? "" : getUnixUIDGID(),
                    "-t", "-v", BASE_DIR + File.separator + "apps" + File.separator + "debug-symbols-smoke:/project:z",
                    "--name", ContainerNames.JFR_SMOKE_BUILDER_IMAGE.name + "-build",
                    BUILDER_IMAGE, JFR_MONITORING_SWITCH_TOKEN, "-jar", "target/debug-symbols-smoke.jar", "target/debug-symbols-smoke"},
            new String[]{
                    CONTAINER_RUNTIME, "run", "-u", IS_THIS_WINDOWS ? "" : getUnixUIDGID(),
                    "-i",
                    "--entrypoint", "java", "-v", BASE_DIR + File.separator + "apps" + File.separator + "debug-symbols-smoke:/project:z",
                    "--name", ContainerNames.JFR_SMOKE_BUILDER_IMAGE.name + "-run",
                    BUILDER_IMAGE,
                    "-XX:+FlightRecorder",
                    "-XX:StartFlightRecording=filename=logs/flight-java.jfr",
                    "-Xlog:jfr", "-jar", "./target/debug-symbols-smoke.jar"},
            new String[]{
                    "./target/debug-symbols-smoke",
                    "-XX:+FlightRecorder",
                    "-XX:StartFlightRecording=filename=logs/flight-native.jfr",
                    "-XX:FlightRecorderLogging=jfr"}
    }),
    JFR_OPTIONS(new String[][]{
            new String[]{"mvn", "package"},
            new String[]{"native-image", JFR_MONITORING_SWITCH_TOKEN, "-jar", "target/timezones.jar", "target/timezones"}
            // @see JFRTest.java
    }),
    JFR_OPTIONS_BUILDER_IMAGE(new String[][]{
            new String[]{"mvn", "package"},
            new String[]{
                    CONTAINER_RUNTIME, "run", "-u", IS_THIS_WINDOWS ? "" : getUnixUIDGID(),
                    "-t", "-v", BASE_DIR + File.separator + "apps" + File.separator + "timezones:/project:z",
                    "--name", ContainerNames.JFR_SMOKE_BUILDER_IMAGE.name + "-build",
                    BUILDER_IMAGE, JFR_MONITORING_SWITCH_TOKEN, "-jar", "target/timezones.jar", "target/timezones"}
            // @see JFRTest.java
    }),
    RESLOCATIONS(new String[][]{
            new String[]{"mvn", "package"},
            new String[]{"native-image", "--initialize-at-build-time=.", "--no-fallback",
                    "-J--add-opens=java.desktop/com.sun.imageio.plugins.common=ALL-UNNAMED",
                    "-J--add-exports=java.desktop/com.sun.imageio.plugins.common=ALL-UNNAMED",
                    "-jar", "./target/reslocations.jar", "target/reslocations"},
            new String[]{IS_THIS_WINDOWS ? "target\\reslocations.exe" : "./target/reslocations"}
    });

    public final String[][] cmds;

    BuildAndRunCmds(String[][] cmds) {
        this.cmds = cmds;
    }
}
