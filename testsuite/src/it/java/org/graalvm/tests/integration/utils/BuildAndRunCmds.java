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

import org.graalvm.tests.integration.DebugSymbolsTest;

import java.io.File;

import static org.graalvm.tests.integration.AppReproducersTest.BASE_DIR;
import static org.graalvm.tests.integration.JFRTest.JFR_FLIGHT_RECORDER_HOTSPOT_TOKEN;
import static org.graalvm.tests.integration.JFRTest.JFR_MONITORING_SWITCH_TOKEN;
import static org.graalvm.tests.integration.PerfCheckTest.MX_HEAP_MB;
import static org.graalvm.tests.integration.utils.Commands.BUILDER_IMAGE;
import static org.graalvm.tests.integration.utils.Commands.CONTAINER_RUNTIME;
import static org.graalvm.tests.integration.utils.Commands.GRAALVM_BUILD_OUTPUT_JSON_FILE;
import static org.graalvm.tests.integration.utils.Commands.IS_THIS_WINDOWS;
import static org.graalvm.tests.integration.utils.Commands.QUARKUS_VERSION;
import static org.graalvm.tests.integration.utils.Commands.getUnixUIDGID;

/**
 * BuildAndRunCmds
 *
 * The last command is used to run the final binary.
 * All previous commands are used to build it.
 *
 * Hints:
 * Tempted to use e.g. UsedVersion.getVersion(... here? Might not work.
 * Builder image tests do not require the host env to have native-image installed.
 *
 * @author Michal Karm Babacek <karm@redhat.com>
 */
public enum BuildAndRunCmds {
    // Note that at least 2 commands are expected. One or more to build. The last one to run the app.
    // Make sure you use an explicit --name when running the app as a container. It is used throughout the TS.
    QUARKUS_FULL_MICROPROFILE(new String[][]{
            new String[]{"mvn", "package", "-Pnative", "-Dquarkus.version=" + QUARKUS_VERSION.getVersionString(),
                    "-Dquarkus.native.additional-build-args=" +
                            "-H:Log=registerResource:," +
                            "--trace-object-instantiation=java.util.Random," +
                            "--initialize-at-run-time=io.vertx.ext.auth.impl.jose.JWT"
            },
            new String[]{IS_THIS_WINDOWS ? "target\\quarkus-runner.exe" : "./target/quarkus-runner"}
    }),
    DEBUG_QUARKUS_FULL_MICROPROFILE(new String[][]{
            new String[]{"mvn", "package", "-Pnative", "-Dquarkus.native.debug.enabled=true", "-Dquarkus.version=" + QUARKUS_VERSION.getVersionString(),
                    "-Dquarkus.native.additional-build-args=" +
                            "-H:Log=registerResource:," +
                            "--trace-object-instantiation=java.util.Random," +
                            "--initialize-at-run-time=io.vertx.ext.auth.impl.jose.JWT"
            },
            new String[]{"mvn", "dependency:sources", "-Dquarkus.version=" + QUARKUS_VERSION.getVersionString()},
            new String[]{IS_THIS_WINDOWS ? "target\\quarkus-runner.exe" : "./target/quarkus-runner"}
    }),
    QUARKUS_FULL_MICROPROFILE_PERF(new String[][]{
            new String[]{"mvn", "package", "-Pnative", "-Dquarkus.version=" + QUARKUS_VERSION.getVersionString(),
                    "-Dquarkus.native.additional-build-args=" +
                            "-H:Log=registerResource:," +
                            "--trace-object-instantiation=java.util.Random," +
                            "--initialize-at-run-time=io.vertx.ext.auth.impl.jose.JWT," +
                            "-R:MaxHeapSize=" + MX_HEAP_MB + "m" +
                            GRAALVM_BUILD_OUTPUT_JSON_FILE
            },
            new String[]{"mvn", "package", "-Dquarkus.version=" + QUARKUS_VERSION.getVersionString()},
            // GC: https://github.com/Karm/mandrel-integration-tests/pull/127#discussion_r1066802872
            // -XX:+UseShenandoahGC
            // -XX:+UseSerialGC
            // -XX:+UseG1GC
            // Profile capture:
            // No "--delay", "2000",  for perf o capture startup too...
            new String[]{"perf", "stat", "java", "-Xlog:gc", "-XX:+UseSerialGC", "-Xmx" + MX_HEAP_MB + "m", "-jar", "target/quarkus-app/quarkus-run.jar"},
            new String[]{"perf", "stat", "./target/quarkus-runner", "-XX:+PrintGC"}
    }),
    QUARKUS_JSON_PERF_PARSEONCE(new String[][]{
            // TODO tune and report: https://www.graalvm.org/22.0/reference-manual/native-image/MemoryManagement/
            new String[]{"mvn", "package", "-Pnative", "-Dquarkus.version=" + QUARKUS_VERSION.getVersionString(),
                    "-Dquarkus.native.additional-build-args=" +
                            "-R:MaxHeapSize=" + MX_HEAP_MB + "m," +
                            "-H:-ParseOnce" +
                            GRAALVM_BUILD_OUTPUT_JSON_FILE + "-ParseOnce",
                    "-Dfinal.name=quarkus-json_-ParseOnce"},
            new String[]{"mvn", "package", "-Pnative", "-Dquarkus.version=" + QUARKUS_VERSION.getVersionString(),
                    "-Dquarkus.native.additional-build-args=" +
                            "-R:MaxHeapSize=" + MX_HEAP_MB + "m," +
                            "-H:+ParseOnce" +
                            GRAALVM_BUILD_OUTPUT_JSON_FILE + "+ParseOnce",
                    "-Dfinal.name=quarkus-json_+ParseOnce"},
            new String[]{"mvn", "package", "-Dfinal.name=quarkus-json", "-Dquarkus.version=" + QUARKUS_VERSION.getVersionString()},
            //-XX:+UseShenandoahGC
            //-XX:+UseSerialGC
            //-XX:+UseG1GC
            new String[]{"perf", "stat", "--delay", "2000", "java", "-Xlog:gc", "-XX:+UseSerialGC", "-Xmx" + MX_HEAP_MB + "m", "-jar", "target/quarkus-app/quarkus-run.jar"},
            new String[]{"perf", "stat", "--delay", "1000", "./target/quarkus-json_-ParseOnce-runner", "-XX:+PrintGC"},
            new String[]{"perf", "stat", "--delay", "1000", "./target/quarkus-json_+ParseOnce-runner", "-XX:+PrintGC"}
    }),
    QUARKUS_JSON_PERF(new String[][]{
            // TODO tune and report: https://www.graalvm.org/22.0/reference-manual/native-image/MemoryManagement/
            new String[]{"mvn", "package", "-Pnative", "-Dquarkus.version=" + QUARKUS_VERSION.getVersionString(),
                    "-Dquarkus.native.additional-build-args=" +
                            "-R:MaxHeapSize=" + MX_HEAP_MB + "m" +
                            GRAALVM_BUILD_OUTPUT_JSON_FILE,
                    "-Dfinal.name=quarkus-json"},
            new String[]{"mvn", "package", "-Dfinal.name=quarkus-json", "-Dquarkus.version=" + QUARKUS_VERSION.getVersionString()},
            new String[]{"perf", "stat", "--delay", "2000", "java", "-Xlog:gc", "-XX:+UseSerialGC", "-Xmx" + MX_HEAP_MB + "m", "-jar", "target/quarkus-app/quarkus-run.jar"},
            new String[]{"perf", "stat", "--delay", "1000", "./target/quarkus-json-runner", "-XX:+PrintGC"},
    }),
    QUARKUS_BUILDER_IMAGE_ENCODING(new String[][]{
            new String[]{"mvn", "package", "-Pnative", "-Dquarkus.native.container-build=true",
                    "-Dquarkus.native.container-runtime=" + CONTAINER_RUNTIME,
                    "-Dquarkus.native.builder-image=" + BUILDER_IMAGE, "-Dquarkus.version=" + QUARKUS_VERSION.getVersionString()},
            new String[]{CONTAINER_RUNTIME, "build", "-f", "src/main/docker/Dockerfile.native", "-t", "my-quarkus-mandrel-app", "."},
            new String[]{CONTAINER_RUNTIME, "run", "-i", "--rm", "-p", "8080:8080",
                    "--name", ContainerNames.QUARKUS_BUILDER_IMAGE_ENCODING.name, "my-quarkus-mandrel-app"}
    }),
    DEBUG_QUARKUS_BUILDER_IMAGE_VERTX(new String[][]{
            new String[]{"mvn", "package", "-Pnative", "-Dquarkus.native.container-build=true",
                    "-Dquarkus.native.container-runtime=" + CONTAINER_RUNTIME,
                    "-Dquarkus.native.builder-image=" + BUILDER_IMAGE,
                    "-Dquarkus.native.debug.enabled=true", "-Dquarkus.version=" + QUARKUS_VERSION.getVersionString()},
            new String[]{"mvn", "dependency:sources", "-Dquarkus.version=" + QUARKUS_VERSION.getVersionString()},
            new String[]{CONTAINER_RUNTIME, "build", "--network=host", "-f", "src/main/docker/Dockerfile.native", "-t", "my-quarkus-mandrel-app", "."},
            new String[]{CONTAINER_RUNTIME, "run", "--network=host", "--ulimit", "memlock=-1:-1", "-it", "-d", "--rm=true",
                    "--name", "quarkus_test_db", "-e", "POSTGRES_USER=quarkus_test", "-e", "POSTGRES_PASSWORD=quarkus_test",
                    "-e", "POSTGRES_DB=quarkus_test", "quay.io/debezium/postgres:15"},
            new String[]{CONTAINER_RUNTIME, "run", "--network=host", "--cap-add=SYS_PTRACE", "--security-opt=seccomp=unconfined",
                    "-i", "-d", "--rm", "--name", ContainerNames.DEBUG_QUARKUS_BUILDER_IMAGE_VERTX.name, "my-quarkus-mandrel-app"}
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
            new String[]{"java", "-Djava.awt.headless=true", "-agentlib:native-image-agent=config-output-dir=src/main/resources/META-INF/native-image", "-jar", "target/imageio.jar"},
            new String[]{"jar", "uf", "target/imageio.jar", "-C", "src/main/resources/", "META-INF"},
            new String[]{"native-image", "-J-Djava.awt.headless=true", "-H:IncludeResources=Grace_M._Hopper.jp2,MyFreeMono.ttf,MyFreeSerif.ttf", "--no-fallback", "-jar", "target/imageio.jar", "target/imageio"},
            new String[]{IS_THIS_WINDOWS ? "target\\imageio.exe" : "./target/imageio", "-Djava.home=.", "-Djava.awt.headless=true"}
    }),
    IMAGEIO_BUILDER_IMAGE(new String[][]{
            // Bring Your Own Maven (not a part of the builder image toolchain)
            new String[]{"mvn", "package"},
            // TODO: Ad -u: Test access rights with -u on Windows, Docker Desktop Hyper-V backend vs. WSL2 backend.
            // Java from Builder image container is used for the sake of consistence.
            new String[]{CONTAINER_RUNTIME, "run", IS_THIS_WINDOWS ? "" : "-u", IS_THIS_WINDOWS ? "" : getUnixUIDGID(),
                    "-t", "--entrypoint", "java", "-v", BASE_DIR + File.separator + "apps" + File.separator + "imageio:/project:z",
                    BUILDER_IMAGE, "-Djava.awt.headless=true",
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
                    "-J-Djava.awt.headless=true", "-H:IncludeResources=Grace_M._Hopper.jp2,MyFreeMono.ttf,MyFreeSerif.ttf", "--no-fallback", "-jar", "target/imageio.jar", "target/imageio"},
            // We build a runtime image, ubi 8 minimal based, runtime dependencies installed
            new String[]{CONTAINER_RUNTIME, "build", "--network=host", "-t", ContainerNames.IMAGEIO_BUILDER_IMAGE.name, "."},
            // We have to run in the same env as we run the java part above, i.e. in the same container base.
            // Hashsums of font rotations would differ otherwise as your linux host might have different freetype native libs.
            new String[]{CONTAINER_RUNTIME, "run", IS_THIS_WINDOWS ? "" : "-u", IS_THIS_WINDOWS ? "" : getUnixUIDGID(),
                    "-t", "-v", BASE_DIR + File.separator + "apps" + File.separator + "imageio:/work:z",
                    ContainerNames.IMAGEIO_BUILDER_IMAGE.name, "/work/target/imageio", "-Djava.home=.", "-Djava.awt.headless=true"}
    }),
    DEBUG_SYMBOLS_SMOKE(new String[][]{
            new String[]{"mvn", "package"},
            IS_THIS_WINDOWS ?
                    new String[]{"powershell", "-c", "\"Expand-Archive -Path test_data.txt.zip -DestinationPath target -Force\""}
                    :
                    new String[]{"unzip", "test_data.txt.zip", "-d", "target"},

            new String[]{"native-image", "-H:GenerateDebugInfo=1", "-H:+PreserveFramePointer", "-H:-DeleteLocalSymbols",
                    DebugSymbolsTest.DebugOptions.TrackNodeSourcePosition_23_0.token,
                    DebugSymbolsTest.DebugOptions.DebugCodeInfoUseSourceMappings_23_0.token,
                    DebugSymbolsTest.DebugOptions.OmitInlinedMethodDebugLineInfo_23_0.token,
                    "-jar", "target/debug-symbols-smoke.jar", "target/debug-symbols-smoke"},
            new String[]{"java", "-jar", "./target/debug-symbols-smoke.jar"},
            new String[]{IS_THIS_WINDOWS ? "target\\debug-symbols-smoke.exe" : "./target/debug-symbols-smoke"}
    }),
    JFR_PERFORMANCE(new String[][]{
            new String[]{"mvn", "package", "-Pnative", "-Dquarkus.version=" + QUARKUS_VERSION.getVersionString(), "-Dquarkus.native.monitoring=jfr", "-Dquarkus.native.additional-build-args=-H:+SignalHandlerBasedExecutionSampler"},
            new String[]{"mv", "target/jfr-native-image-performance-1.0.0-SNAPSHOT-runner", "target/jfr-native-image-performance-1.0.0-SNAPSHOT-runner_JFR_PERFORMANCE"},
            new String[]{"./target/jfr-native-image-performance-1.0.0-SNAPSHOT-runner_JFR_PERFORMANCE",
                    "-XX:+FlightRecorder",
                    "-XX:StartFlightRecording=settings=" + BASE_DIR + File.separator + "apps" + File.separator + "jfr-native-image-performance/jfr-perf.jfc,filename=logs/flight-native.jfr",
                    "-XX:FlightRecorderLogging=jfr"},
            new String[]{CONTAINER_RUNTIME, "run", "--name", ContainerNames.HYPERFOIL.name, "--rm", "--network=host", "quay.io/hyperfoil/hyperfoil:0.25.2", "standalone"}
    }),
    PLAINTEXT_PERFORMANCE(new String[][]{
            new String[]{"mvn", "package", "-Pnative", "-Dquarkus.version=" + QUARKUS_VERSION.getVersionString()},
            new String[]{"mv", "target/jfr-native-image-performance-1.0.0-SNAPSHOT-runner", "target/jfr-native-image-performance-1.0.0-SNAPSHOT-runner_PLAINTEXT_PERFORMANCE"},
            new String[]{"./target/jfr-native-image-performance-1.0.0-SNAPSHOT-runner_PLAINTEXT_PERFORMANCE"},
            new String[]{CONTAINER_RUNTIME, "run", "--name", ContainerNames.HYPERFOIL.name, "--rm", "--network=host", "quay.io/hyperfoil/hyperfoil:0.25.2", "standalone"}
    }),
    JFR_SMOKE(new String[][]{
            new String[]{"mvn", "package"},
            IS_THIS_WINDOWS ?
                    new String[]{"powershell", "-c", "\"Expand-Archive -Path test_data.txt.zip -DestinationPath target -Force\""}
                    :
                    new String[]{"unzip", "test_data.txt.zip", "-d", "target"},
            new String[]{"native-image", JFR_MONITORING_SWITCH_TOKEN, "-jar", "target/debug-symbols-smoke.jar", "target/debug-symbols-smoke"},
            new String[]{"java", "-jar", "./target/debug-symbols-smoke.jar"},
            new String[]{"java",
                    JFR_FLIGHT_RECORDER_HOTSPOT_TOKEN,
                    "-XX:StartFlightRecording=filename=logs/flight-java.jfr",
                    "-Xlog:jfr", "-jar", "./target/debug-symbols-smoke.jar"},
            new String[]{IS_THIS_WINDOWS ? "target\\debug-symbols-smoke.exe" : "./target/debug-symbols-smoke"},
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
                    "--name", ContainerNames.JFR_SMOKE_BUILDER_IMAGE.name + "-run-java",
                    BUILDER_IMAGE, "-jar", "./target/debug-symbols-smoke.jar"},
            new String[]{
                    CONTAINER_RUNTIME, "run", "-u", IS_THIS_WINDOWS ? "" : getUnixUIDGID(),
                    "-i",
                    "--entrypoint", "java", "-v", BASE_DIR + File.separator + "apps" + File.separator + "debug-symbols-smoke:/project:z",
                    "--name", ContainerNames.JFR_SMOKE_BUILDER_IMAGE.name + "-run-java-jfr",
                    BUILDER_IMAGE,
                    JFR_FLIGHT_RECORDER_HOTSPOT_TOKEN,
                    "-XX:StartFlightRecording=filename=logs/flight-java.jfr",
                    "-Xlog:jfr", "-jar", "./target/debug-symbols-smoke.jar"},
            new String[]{"./target/debug-symbols-smoke"},
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
