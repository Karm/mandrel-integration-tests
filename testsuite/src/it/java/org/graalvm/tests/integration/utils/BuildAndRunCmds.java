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
import static org.graalvm.tests.integration.PerfCheckTest.FINAL_NAME_TOKEN;
import static org.graalvm.tests.integration.PerfCheckTest.MX_HEAP_MB;
import static org.graalvm.tests.integration.PerfCheckTest.NATIVE_IMAGE_XMX_GB;
import static org.graalvm.tests.integration.utils.Commands.BUILDER_IMAGE;
import static org.graalvm.tests.integration.utils.Commands.CONTAINER_RUNTIME;
import static org.graalvm.tests.integration.utils.Commands.GRAALVM_BUILD_OUTPUT_JSON_FILE;
import static org.graalvm.tests.integration.utils.Commands.GRAALVM_EXPERIMENTAL_BEGIN;
import static org.graalvm.tests.integration.utils.Commands.GRAALVM_EXPERIMENTAL_END;
import static org.graalvm.tests.integration.utils.Commands.IS_THIS_MACOS;
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

    // Requires with podman:
    //
    // export DOCKER_HOST=unix:///run/user/${UID}/podman/podman.sock
    // export TESTCONTAINERS_RYUK_DISABLED=true
    QUARKUS_MP_ORM_DBS_AWT(new String[][] {
            new String[] { "mvn", "verify", "-Pnative", "-Dquarkus.version=" + QUARKUS_VERSION.getVersionString(),
                    "-Dquarkus.profile=test",
                    "-DBuildOutputJSONFile=" + GRAALVM_BUILD_OUTPUT_JSON_FILE,
                    "-DUnlockExperimentalBEGIN=" + GRAALVM_EXPERIMENTAL_BEGIN,
                    "-DUnlockExperimentalEND=" + GRAALVM_EXPERIMENTAL_END,
                    "-Dquarkus.native.native-image-xmx=" + NATIVE_IMAGE_XMX_GB + "g",
                    "-DfinalName=" + FINAL_NAME_TOKEN
            },
            new String[] { IS_THIS_WINDOWS ? "target\\" + FINAL_NAME_TOKEN + ".exe" : "./target/" + FINAL_NAME_TOKEN }
    }),
    QUARKUS_BUILDER_IMAGE_MP_ORM_DBS_AWT(new String[][] {
            new String[] { "mvn", "verify", "-Pnative", "-Dquarkus.version=" + QUARKUS_VERSION.getVersionString(),

                    "-Dquarkus.native.container-build=true",
                    "-Dquarkus.container-image.build=true",
                    "-Dquarkus.native.container-runtime=" + CONTAINER_RUNTIME,
                    "-Dquarkus.native.builder-image=" + BUILDER_IMAGE,

                    "-Dquarkus.profile=test",
                    "-DBuildOutputJSONFile=" + GRAALVM_BUILD_OUTPUT_JSON_FILE,
                    "-DUnlockExperimentalBEGIN=" + GRAALVM_EXPERIMENTAL_BEGIN,
                    "-DUnlockExperimentalEND=" + GRAALVM_EXPERIMENTAL_END,
                    "-Dquarkus.native.native-image-xmx=" + NATIVE_IMAGE_XMX_GB + "g",
                    "-DfinalName=" + FINAL_NAME_TOKEN
            },
            new String[] { IS_THIS_WINDOWS ? "target\\" + FINAL_NAME_TOKEN + ".exe" : "./target/" + FINAL_NAME_TOKEN }
    }),
    QUARKUS_FULL_MICROPROFILE(new String[][]{
            new String[]{"mvn", "package", "-Pnative", "-Dquarkus.version=" + QUARKUS_VERSION.getVersionString(),
                    "-Dquarkus.native.additional-build-args=" +
                            "-H:Log=registerResource:," +
                            "--trace-object-instantiation=java.util.Random"
            },
            new String[]{IS_THIS_WINDOWS ? "target\\quarkus-runner.exe" : "./target/quarkus-runner"}
    }),
    DEBUG_QUARKUS_FULL_MICROPROFILE(new String[][]{
            new String[]{"mvn", "package", "-Pnative", "-Dquarkus.native.debug.enabled=true", "-Dquarkus.version=" + QUARKUS_VERSION.getVersionString(),
                    "-Dquarkus.native.additional-build-args=" +
                            "-H:Log=registerResource:," +
                            "--trace-object-instantiation=java.util.Random"
            },
            new String[]{"mvn", "dependency:sources", "-Dquarkus.version=" + QUARKUS_VERSION.getVersionString()},
            new String[]{IS_THIS_WINDOWS ? "target\\quarkus-runner.exe" : "./target/quarkus-runner"}
    }),
    QUARKUS_FULL_MICROPROFILE_PERF(new String[][]{
            new String[]{"mvn", "package", "-Pnative", "-Dquarkus.version=" + QUARKUS_VERSION.getVersionString(),
                    "-Dquarkus.native.additional-build-args=" +
                            "-H:Log=registerResource:," +
                            "--trace-object-instantiation=java.util.Random," +
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
                    "-Dcustom.final.name=quarkus-json_-ParseOnce"},
            new String[]{"mvn", "package", "-Pnative", "-Dquarkus.version=" + QUARKUS_VERSION.getVersionString(),
                    "-Dquarkus.native.additional-build-args=" +
                            "-R:MaxHeapSize=" + MX_HEAP_MB + "m," +
                            "-H:+ParseOnce" +
                            GRAALVM_BUILD_OUTPUT_JSON_FILE + "+ParseOnce",
                    "-Dcustom.final.name=quarkus-json_+ParseOnce"},
            new String[]{"mvn", "package", "-Dcustom.final.name=quarkus-json", "-Dquarkus.version=" + QUARKUS_VERSION.getVersionString()},
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
                    "-Dcustom.final.name=quarkus-json"},
            new String[]{"mvn", "package", "-Dcustom.final.name=quarkus-json", "-Dquarkus.version=" + QUARKUS_VERSION.getVersionString()},
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
            new String[]{"mvn", "dependency:sources", "-Dquarkus.version=" + QUARKUS_VERSION.getVersionString()},
            new String[]{"mvn", "package", "-Pnative", "-Dquarkus.native.container-build=true",
                    "-Dquarkus.native.container-runtime=" + CONTAINER_RUNTIME,
                    "-Dquarkus.native.builder-image=" + BUILDER_IMAGE,
                    "-Dquarkus.native.debug.enabled=true", "-Dquarkus.version=" + QUARKUS_VERSION.getVersionString()},
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
    CALENDARS(new String[][]{
        new String[]{"mvn", "package"},
        new String[]{"native-image", "--link-at-build-time=calendar.Main", "-jar", "target/calendars.jar", "target/calendars"},
        new String[]{IS_THIS_WINDOWS ? "target\\calendars.exe" : "./target/calendars"}
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
            new String[]{"native-image", "-J-Djava.awt.headless=true", "--no-fallback", "-jar", "target/imageio.jar", "target/imageio"},
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
                    "-J-Djava.awt.headless=true", "--no-fallback", "-jar", "target/imageio.jar", "target/imageio"},
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

            new String[] { "native-image", DebugSymbolsTest.DebugOptions.UnlockExperimentalVMOptions_23_1.token,
                    "-H:GenerateDebugInfo=" + (IS_THIS_MACOS ? "0" : "1"), "-H:+PreserveFramePointer", "-H:-DeleteLocalSymbols",
                    DebugSymbolsTest.DebugOptions.TrackNodeSourcePosition_23_0.token,
                    DebugSymbolsTest.DebugOptions.DebugCodeInfoUseSourceMappings_23_0.token,
                    DebugSymbolsTest.DebugOptions.OmitInlinedMethodDebugLineInfo_23_0.token,
                    DebugSymbolsTest.DebugOptions.LockExperimentalVMOptions_23_1.token,
                    "-jar", "target/debug-symbols-smoke.jar", "target/debug-symbols-smoke" },
            new String[]{"java", "-jar", "./target/debug-symbols-smoke.jar"},
            new String[]{IS_THIS_WINDOWS ? "target\\debug-symbols-smoke.exe" : "./target/debug-symbols-smoke"}
    }),
    JFR_PERFORMANCE(new String[][]{
            // Why do you need -H:+SignalHandlerBasedExecutionSampler?
            // The recurring callback sampler runs by default and is biased. It also tends to sample a lot more than the
            // SIGPROF based one (even though you can technically specify a desired rate). The SIGPROF one is what people
            // should generally be using for now so I decided it was best to test that one.
            // I don't think the difference between the two will be that huge anyway though.
            // Source: https://github.com/Karm/mandrel-integration-tests/pull/179#discussion_r1295933521
            new String[]{"mvn", "package", "-Pnative", "-Dquarkus.version=" + QUARKUS_VERSION.getVersionString(), "-Dquarkus.native.monitoring=jfr",
                    "-Dquarkus.native.additional-build-args=-H:+SignalHandlerBasedExecutionSampler",
                    "-DfinalName=jfr-perf"},
            new String[]{"./target/jfr-perf-runner",
                    "-XX:+FlightRecorder",
                    "-XX:StartFlightRecording=settings=" + BASE_DIR + File.separator + "apps" + File.separator + "jfr-native-image-performance/jfr-perf.jfc,filename=logs/flight-native.jfr",
                    "-XX:FlightRecorderLogging=jfr"},
            hyperfoil()
    }),
    PLAINTEXT_PERFORMANCE(new String[][]{
            new String[]{"mvn", "package", "-Pnative", "-Dquarkus.version=" + QUARKUS_VERSION.getVersionString(),
                    "-DfinalName=jfr-plaintext"},
            new String[]{"./target/jfr-plaintext-runner"},
            hyperfoil()
    }),
    JFR_PERFORMANCE_BUILDER_IMAGE(new String[][]{
            new String[]{"mvn", "clean", "package", "-Pnative", "-Dquarkus.native.container-build=true",
                    "-Dquarkus.native.container-runtime=" + CONTAINER_RUNTIME,
                    "-Dquarkus.native.builder-image=" + BUILDER_IMAGE, "-Dquarkus.version=" + QUARKUS_VERSION.getVersionString(), "-Dquarkus.native.monitoring=jfr",
                    "-Dquarkus.native.additional-build-args=-H:+SignalHandlerBasedExecutionSampler",
                    "-DfinalName=jfr-perf"},
            new String[]{CONTAINER_RUNTIME, "build", "-f", "src/main/docker/Dockerfile.native", "-t", "jfr-performance-app", "."},
            new String[]{CONTAINER_RUNTIME, "run", "--rm", "--network=host",
                    "-u", IS_THIS_WINDOWS ? "" : getUnixUIDGID(),
                    "-t",
                    "-v", BASE_DIR + File.separator + "apps" + File.separator + "jfr-native-image-performance/logs:/tmp:z",
                    "--name", ContainerNames.JFR_PERFORMANCE_BUILDER_IMAGE.name, "jfr-performance-app", "-XX:+FlightRecorder",
                    "-XX:StartFlightRecording=settings=/work/jfr-perf.jfc,filename=/tmp/flight-native.jfr",
                    "-XX:FlightRecorderLogging=jfr"},
            hyperfoil()
    }),
    PLAINTEXT_PERFORMANCE_BUILDER_IMAGE(new String[][]{
            new String[]{"mvn", "clean", "package", "-Pnative", "-Dquarkus.native.container-build=true",
                    "-Dquarkus.native.container-runtime=" + CONTAINER_RUNTIME,
                    "-Dquarkus.native.builder-image=" + BUILDER_IMAGE, "-Dquarkus.version=" + QUARKUS_VERSION.getVersionString(),
                    "-DfinalName=jfr-plaintext"},
            new String[]{CONTAINER_RUNTIME, "build", "-f", "src/main/docker/Dockerfile.native", "-t", "jfr-plaintext-app", "."},
            new String[]{CONTAINER_RUNTIME, "run", "--rm", "--network=host",
                    "-u", IS_THIS_WINDOWS ? "" : getUnixUIDGID(),
                    "-t",
                    //"-v", BASE_DIR + File.separator + "apps" + File.separator + "jfr-native-image-performance/logs:/tmp:z",
                    "--name", ContainerNames.JFR_PLAINTEXT_BUILDER_IMAGE.name, "jfr-plaintext-app"},
            hyperfoil()
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
    }),
    MONITOR_OFFSET(new String[][] {
            new String[] { "mvn", "package", "-POK" },
            new String[] { "native-image", "-R:-InstallSegfaultHandler", "-march=native", "--gc=serial", "--no-fallback",
                    "-jar", "target/monitor-field-offsets-ok.jar", "target/monitor-field-offsets-ok" },
            new String[] { IS_THIS_WINDOWS ? "target\\monitor-field-offsets-ok" : "./target/monitor-field-offsets-ok" },
            new String[] { "mvn", "package", "-PNOK" },
            new String[] { "native-image", "-R:-InstallSegfaultHandler", "-march=native", "--gc=serial", "--no-fallback",
                    "-jar", "target/monitor-field-offsets-nok.jar", "target/monitor-field-offsets-nok" }
    }),
    MONITOR_OFFSET_BUILDER_IMAGE(new String[][] {
            new String[] { "mvn", "package", "-POK" },
            new String[] {
                    CONTAINER_RUNTIME, "run", "-u", IS_THIS_WINDOWS ? "" : getUnixUIDGID(),
                    "-t", "-v", BASE_DIR + File.separator + "apps" + File.separator + "monitor-field-offset:/project:z",
                    "--name", ContainerNames.MONITOR_OFFSET_BUILDER_IMAGE.name,
                    BUILDER_IMAGE, "-R:-InstallSegfaultHandler", "-march=native", "--gc=serial", "--no-fallback",
                    "-jar", "target/monitor-field-offsets-ok.jar", "target/monitor-field-offsets-ok" },
            new String[]{CONTAINER_RUNTIME, "build", "--network=host", "-t", ContainerNames.MONITOR_OFFSET_BUILDER_IMAGE.name, "."},
            new String[] { CONTAINER_RUNTIME, "run", IS_THIS_WINDOWS ? "" : "-u", IS_THIS_WINDOWS ? "" : getUnixUIDGID(),
                    "-t", "-v", BASE_DIR + File.separator + "apps" + File.separator + "monitor-field-offset:/work:z",
                    ContainerNames.MONITOR_OFFSET_BUILDER_IMAGE.name, "target/monitor-field-offsets-ok" },
            new String[] { "mvn", "package", "-PNOK" },
            new String[] {
                    CONTAINER_RUNTIME, "run", "-u", IS_THIS_WINDOWS ? "" : getUnixUIDGID(),
                    "-t", "-v", BASE_DIR + File.separator + "apps" + File.separator + "monitor-field-offset:/project:z",
                    "--name", ContainerNames.MONITOR_OFFSET_BUILDER_IMAGE.name,
                    BUILDER_IMAGE, "-R:-InstallSegfaultHandler", "-march=native", "--gc=serial", "--no-fallback",
                    "-jar", "target/monitor-field-offsets-nok.jar", "target/monitor-field-offsets-nok" },
    });

    public final String[][] cmds;

    private static String[] hyperfoil() {
        if (IS_THIS_MACOS) {
            // --network=host does not do what you think it does on macOS. It creates a shared network
            // between your container and the VM running it.
            // The step that would bring the connection all the way up the stack to your host
            // is an ssh tunnel. This last step is manual, and we run it separately during the test.
            return new String[] { CONTAINER_RUNTIME, "run", "--name", ContainerNames.HYPERFOIL.name, "--rm", "--network=host",
                    "--entrypoint=/bin/bash", "quay.io/karmkarm/hyperfoil:0.25.2", "/deployment/bin/standalone.sh",
                    "-Dio.hyperfoil.controller.host=localhost" };
        } else {
            return new String[] { CONTAINER_RUNTIME, "run", "--name", ContainerNames.HYPERFOIL.name, "--rm", "--network=host",
                    "quay.io/karmkarm/hyperfoil:0.25.2", "standalone" };
        }
    }

    BuildAndRunCmds(String[][] cmds) {
        this.cmds = cmds;
    }
}
