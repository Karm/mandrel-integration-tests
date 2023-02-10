/*
 * Copyright (c) 2021, Red Hat Inc. All rights reserved.
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
package org.graalvm.tests.integration.utils.versions;

import org.graalvm.home.Version;
import org.graalvm.tests.integration.utils.Commands;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.graalvm.tests.integration.utils.Commands.BUILDER_IMAGE;
import static org.graalvm.tests.integration.utils.Commands.CONTAINER_RUNTIME;
import static org.graalvm.tests.integration.utils.Commands.getRunCommand;

/**
 * Lazy loads Mandrel version either from a container or from
 * the local installation, being thread safe about it.
 *
 * Supported `native-image --version' output:
 * GraalVM 20.3.3 Java 11 (Java Version 11.0.12+6-jvmci-20.3-b20)
 * GraalVM 21.1.0 Java 11 CE (Java Version 11.0.11+8-jvmci-21.1-b05)
 * GraalVM 21.3.0 Java 11 CE (Java Version 11.0.13+7-jvmci-21.3-b05)
 * GraalVM Version 20.1.0.4.Final (Mandrel Distribution) (Java Version 11.0.10+9)
 * GraalVM Version 20.3.3.0-0b1 (Mandrel Distribution) (Java Version 11.0.12+7-LTS)
 * native-image 21.1.0.0-Final (Mandrel Distribution) (Java Version 11.0.11+9)
 * native-image 21.2.0.2-0b3 Mandrel Distribution (Java Version 11.0.13+8-LTS)
 * native-image 21.3.0.0-Final Mandrel Distribution (Java Version 17.0.1+12)
 * native-image 23.0.0-devf2da442e8f1 Mandrel Distribution (Java Version 20-beta+33-202302010338)
 *
 * @author Michal Karm Babacek <karm@redhat.com>
 */
public class UsedVersion {
    private static final int UNDEFINED = -1;

    public static Version getVersion(boolean inContainer) {
        return inContainer ? InContainer.mVersion.version : Locally.mVersion.version;
    }

    public static boolean jdkUsesSysLibs(boolean inContainer) {
        return inContainer ? InContainer.mVersion.jdkUsesSysLibs : Locally.mVersion.jdkUsesSysLibs;
    }

    public static int jdkFeature(boolean inContainer) {
        return inContainer ? InContainer.mVersion.jdkFeature : Locally.mVersion.jdkFeature;
    }

    public static int jdkInterim(boolean inContainer) {
        return inContainer ? InContainer.mVersion.jdkInterim : Locally.mVersion.jdkInterim;
    }

    public static int jdkUpdate(boolean inContainer) {
        return inContainer ? InContainer.mVersion.jdkUpdate : Locally.mVersion.jdkUpdate;
    }

    /**
     * Mandrel Version plus additional metadata, e.g. jdkUsesSysLibs
     */
    private static final class MVersion {
        private static final Logger LOGGER = Logger.getLogger(MVersion.class.getName());
        private static final Pattern VERSION_PATTERN = Pattern.compile(
                "(?:GraalVM|native-image)(?: Version)? (?<version>[^ ]*).*" +
                        "Java Version (?<jfeature>[\\d]+)((?<beta>-beta\\+[\\d]+-[\\d]+)|\\.(?<jinterim>[\\d]+)\\.(?<jupdate>[\\d]+)).*");

        private final Version version;
        private final boolean jdkUsesSysLibs;
        private final int jdkFeature;
        private final int jdkInterim;
        private final int jdkUpdate;

        public MVersion(boolean inContainer) {
            final String lastLine;
            if (inContainer) {
                final List<String> cmd = List.of(CONTAINER_RUNTIME, "run", "-t", BUILDER_IMAGE, "native-image", "--version");
                LOGGER.info("Running command " + cmd + " to determine Mandrel version used.");
                final String out;
                try {
                    out = Commands.runCommand(cmd);
                } catch (IOException e) {
                    throw new RuntimeException("Is " + CONTAINER_RUNTIME + " running?", e);
                }
                final String[] lines = out.split(System.lineSeparator());
                lastLine = lines[lines.length - 1].trim();
            } else {
                final String TEST_TESTSUITE_ABSOLUTE_PATH = System.getProperty("FAKE_NATIVE_IMAGE_DIR", "");
                final List<String> cmd = getRunCommand(TEST_TESTSUITE_ABSOLUTE_PATH + "native-image", "--version");
                LOGGER.info("Running command " + cmd + " to determine Mandrel version used.");
                try {
                    lastLine = Commands.runCommand(cmd).trim();
                } catch (IOException e) {
                    throw new RuntimeException("Is native-image command available? Check if you are not trying " +
                            "to run tests expecting locally installed native-image without having one. -Ptestsuite-builder-image is the " +
                            "correct profile for running without locally installed native-image.", e);
                }
            }

            jdkUsesSysLibs = lastLine.contains("-LTS");
            if (inContainer && !lastLine.contains("Mandrel")) {
                LOGGER.warn("You are probably running GraalVM and not Mandrel container. " +
                        "It might not work as tests might expect certain paths such as /opt/mandrel/.");
            }
            final Matcher m = VERSION_PATTERN.matcher(lastLine);
            if (!m.matches()) {
                if (inContainer) {
                    throw new IllegalArgumentException("native-image command failed to produce a parseable output. " +
                            "Is " + CONTAINER_RUNTIME + " running and image " + BUILDER_IMAGE + " available? " +
                            "Output: '" + lastLine + "'");
                } else {
                    throw new IllegalArgumentException("native-image command failed to produce a parseable output. " +
                            "Is it on PATH? " +
                            "Output: '" + lastLine + "'");
                }
            }
            version = versionParse(m.group("version"));
            final String jFeature = m.group("jfeature");
            jdkFeature = jFeature == null ? UNDEFINED : Integer.parseInt(jFeature);
            final boolean beta = m.group("beta") != null;
            if (beta) {
                jdkInterim = 0;
                jdkUpdate = 0;
            } else {
                final String jInterim = m.group("jinterim");
                final String jUpdate = m.group("jupdate");
                jdkInterim = jInterim == null ? UNDEFINED : Integer.parseInt(jInterim);
                jdkUpdate = jUpdate == null ? UNDEFINED : Integer.parseInt(jUpdate);
            }
            if (jdkFeature == UNDEFINED) {
                LOGGER.warn("Failed to correctly parse Java feature (major) version from native-image version command output. " +
                        "JDK version constraints in tests won't work reliably.");
            }
            LOGGER.infof("The test suite runs with Mandrel version %s %s, JDK %d.%d.%d%s.",
                    version.toString(), inContainer ? "in container" : "installed locally on PATH", jdkFeature, jdkInterim, jdkUpdate, beta ? m.group("beta") : "");
        }

        private static Version versionParse(String version) {
            // Invalid version string '20.1.0.4.Final'
            return Version.parse(version
                    .toLowerCase()
                    .replace(".f", "-f")
                    .replace(".s", "-s"));
        }
    }

    private static class InContainer {
        private static final MVersion mVersion = new MVersion(true);
    }

    private static class Locally {
        private static final MVersion mVersion = new MVersion(false);
    }

    public static int[] featureInterimUpdate(Pattern pattern, String version, int defaultValue) {
        final Matcher m = pattern.matcher(version);
        if (!m.matches()) {
            return new int[]{defaultValue, defaultValue, defaultValue};
        }
        final String jFeature = m.group("jfeature");
        final String jInterim = m.group("jinterim");
        final String jUpdate = m.group("jupdate");
        return new int[]{
                jFeature == null ? defaultValue : Integer.parseInt(jFeature),
                jInterim == null ? defaultValue : Integer.parseInt(jInterim),
                jUpdate == null ? defaultValue : Integer.parseInt(jUpdate)
        };
    }

    public static int compareJDKVersion(int[] a, int[] b) {
        if (a.length != 3 || b.length != 3) {
            throw new IllegalArgumentException("3 version elements expected: feature, interim, update");
        }
        for (int i = 0; i < 3; i++) {
            int compare = Integer.compare(a[i], b[i]);
            if (compare != 0) {
                return compare;
            }
        }
        return 0;
    }
}
