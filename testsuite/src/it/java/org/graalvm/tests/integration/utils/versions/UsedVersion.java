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
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.graalvm.tests.integration.utils.Commands.BUILDER_IMAGE;
import static org.graalvm.tests.integration.utils.Commands.CONTAINER_RUNTIME;
import static org.graalvm.tests.integration.utils.Commands.IS_THIS_WINDOWS;
import static org.graalvm.tests.integration.utils.Commands.getRunCommand;

/**
 * Lazy loads Mandrel version either from a container or from
 * the local installation, being thread safe about it.
 *
 * Supported `native-image --version' output (old single-line):
 *
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
 * Supported new `native-image --version' output (3-lines of output):
 * ------------------------------------------------------------------
 *
 * native-image 17.0.6 2023-01-17
 * OpenJDK Runtime Environment Mandrel-23.0.0-dev (build 17.0.6+10)
 * OpenJDK 64-Bit Server VM Mandrel-23.0.0-dev (build 17.0.6+10, mixed mode)
 *
 * or
 *
 * native-image 17.0.6 2023-01-17
 * GraalVM Runtime Environment Mandrel-23.0.0-dev (build 17.0.6+10)
 * Substrate VM Mandrel-23.0.0-dev (build 17.0.6+10, serial gc)
 *
 * or
 *
 * native-image 20 2023-03-21
 * GraalVM Runtime Environment GraalVM CE (build 20+34-jvmci-23.0-b10)
 * Substrate VM GraalVM CE (build 20+34, serial gc)
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

    // Implements version parsing after https://github.com/oracle/graal/pull/6302
    static final class VersionParseHelper {

        private static final String JVMCI_BUILD_PREFIX = "jvmci-";
        private static final String MANDREL_VERS_PREFIX = "Mandrel-";

        // Java version info (suitable for Runtime.Version.parse()). See java.lang.VersionProps
        private static final String VNUM = "(?<VNUM>[1-9][0-9]*(?:(?:\\.0)*\\.[1-9][0-9]*)*)";
        private static final String PRE = "(?:-(?<PRE>[a-zA-Z0-9]+))?";
        private static final String BUILD = "(?:(?<PLUS>\\+)(?<BUILD>0|[1-9][0-9]*)?)?";
        private static final String OPT = "(?:-(?<OPT>[-a-zA-Z0-9.]+))?";
        private static final String VSTR_FORMAT = VNUM + PRE + BUILD + OPT;

        private static final String VNUM_GROUP = "VNUM";
        private static final String VENDOR_VERSION_GROUP = "VENDOR";
        private static final String BUILD_INFO_GROUP = "BUILDINFO";

        private static final String VENDOR_VERS = "(?<VENDOR>.*)";
        private static final String JDK_DEBUG = "[^\\)]*"; // zero or more of >anything not a ')'<
        private static final String RUNTIME_NAME = "(?<RUNTIME>(?:OpenJDK|GraalVM) Runtime Environment) ";
        private static final String BUILD_INFO = "(?<BUILDINFO>.*)";
        private static final String VM_NAME = "(?<VM>(?:OpenJDK 64-Bit Server|Substrate) VM) ";

        private static final String FIRST_LINE_PATTERN = "native-image " + VSTR_FORMAT + " .*$";
        private static final String SECOND_LINE_PATTERN = RUNTIME_NAME
                + VENDOR_VERS + " \\(" + JDK_DEBUG + "build " + BUILD_INFO + "\\)$";
        private static final String THIRD_LINE_PATTERN = VM_NAME + VENDOR_VERS + " \\(" + JDK_DEBUG + "build .*\\)$";
        private static final Pattern FIRST_PATTERN = Pattern.compile(FIRST_LINE_PATTERN);
        private static final Pattern SECOND_PATTERN = Pattern.compile(SECOND_LINE_PATTERN);
        private static final Pattern THIRD_PATTERN = Pattern.compile(THIRD_LINE_PATTERN);

        private static final String VERS_FORMAT = "(?<VERSION>[1-9][0-9]*(\\.[0-9]+)+(-dev\\p{XDigit}*)?)";
        private static final String VERSION_GROUP = "VERSION";
        private static final Pattern VERSION_PATTERN = Pattern.compile(VERS_FORMAT);

        static MVersion parse(List<String> lines) {
            Matcher firstMatcher = FIRST_PATTERN.matcher(lines.get(0));
            Matcher secondMatcher = SECOND_PATTERN.matcher(lines.get(1));
            Matcher thirdMatcher = THIRD_PATTERN.matcher(lines.get(2));
            if (firstMatcher.find() && secondMatcher.find() && thirdMatcher.find()) {
                String javaVersion = firstMatcher.group(VNUM_GROUP);
                java.lang.Runtime.Version v = null;
                try {
                    v = java.lang.Runtime.Version.parse(javaVersion);
                } catch (IllegalArgumentException e) {
                    return MVersion.UNKNOWN_VERSION;
                }

                String vendorVersion = secondMatcher.group(VENDOR_VERSION_GROUP);

                String buildInfo = secondMatcher.group(BUILD_INFO_GROUP);
                String graalVersion = graalVersion(buildInfo);
                String mandrelVersion = mandrelVersion(vendorVersion);
                String versNum = (isMandrel(vendorVersion) ? mandrelVersion : graalVersion);
                Version vers = versionParse(versNum);
                VersionBuilder builder = new VersionBuilder();
                return builder.jdkUsesSysLibs(false /* not implemented */)
                       .beta("" /* not implemented */)
                       .jdkFeature(v.feature())
                       .jdkInterim(v.interim())
                       .jdkUpdate(v.update())
                       .version(vers)
                       .build();
            } else {
                return MVersion.UNKNOWN_VERSION;
            }
        }

        private static boolean isMandrel(String vendorVersion) {
            if (vendorVersion == null) {
                return false;
            }
            return !vendorVersion.isBlank() && vendorVersion.startsWith(MANDREL_VERS_PREFIX);
        }

        private static String mandrelVersion(String vendorVersion) {
            if (vendorVersion == null) {
                return null;
            }
            int idx = vendorVersion.indexOf(MANDREL_VERS_PREFIX);
            if (idx < 0) {
                return null;
            }
            String version = vendorVersion.substring(idx + MANDREL_VERS_PREFIX.length());
            return matchVersion(version);
        }

        private static String matchVersion(String version) {
            Matcher versMatcher = VERSION_PATTERN.matcher(version);
            if (versMatcher.find()) {
                return versMatcher.group(VERSION_GROUP);
            }
            return null;
        }

        private static String graalVersion(String buildInfo) {
            if (buildInfo == null) {
                return null;
            }
            int idx = buildInfo.indexOf(JVMCI_BUILD_PREFIX);
            if (idx < 0) {
                return null;
            }
            String version = buildInfo.substring(idx + JVMCI_BUILD_PREFIX.length());
            return matchVersion(version);
        }

        static Version versionParse(String version) {
            // Invalid version string '20.1.0.4.Final'
            return Version.parse(version
                    .toLowerCase()
                    .replace(".f", "-f")
                    .replace(".s", "-s"));
        }
    }

    /**
     * Mandrel Version plus additional metadata, e.g. jdkUsesSysLibs
     */
    private static final class MVersion {
        private static final MVersion UNKNOWN_VERSION = new MVersion(null, false, UNDEFINED, UNDEFINED, UNDEFINED, "beta-unknown");
        private static final Logger LOGGER = Logger.getLogger(MVersion.class.getName());
        private static final Pattern VERSION_PATTERN = Pattern.compile(
                "(?:GraalVM|native-image)(?: Version)? (?<version>[^ ]*).*" +
                        "Java Version (?<jfeature>[\\d]+)((?<beta>-beta\\+[\\d]+-[\\d]+)|\\.(?<jinterim>[\\d]+)\\.(?<jupdate>[\\d]+)).*");

        private final Version version;
        private final boolean jdkUsesSysLibs;
        private final int jdkFeature;
        private final int jdkInterim;
        private final int jdkUpdate;
        private final String betaBits;

        private MVersion(Version version, boolean jdkUsesSysLibs, int jdkFeature, int jdkInterim, int jdkUpdate, String betaBits) {
            this.version = version;
            this.jdkUsesSysLibs = jdkUsesSysLibs;
            this.jdkFeature = jdkFeature;
            this.jdkInterim = jdkInterim;
            this.jdkUpdate = jdkUpdate;
            this.betaBits = betaBits;
        }

        public static MVersion of(boolean inContainer) {
            List<String> lines = runNativeImageVersion(inContainer);
            MVersion mandrelVersion;
            if (lines.size() == 1) {
                mandrelVersion = parseOldVersion(lines, inContainer);
            } else if (lines.size() == 3) {
                mandrelVersion = VersionParseHelper.parse(lines);
            } else {
                mandrelVersion = UNKNOWN_VERSION;
            }
            LOGGER.infof("The test suite runs with Mandrel version %s %s, JDK %d.%d.%d%s.",
                    mandrelVersion.version.toString(), inContainer ? "in container" : "installed locally on PATH", mandrelVersion.jdkFeature, mandrelVersion.jdkInterim, mandrelVersion.jdkUpdate, mandrelVersion.betaBits);
            return mandrelVersion;
        }

        private static MVersion parseOldVersion(List<String> lines, boolean inContainer) {
            final String lastLine = lines.get(lines.size() - 1).trim();
            VersionBuilder builder = new VersionBuilder();
            builder.jdkUsesSysLibs(lastLine.contains("-LTS"));
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
            builder.version(VersionParseHelper.versionParse(m.group("version")));
            final String jFeature = m.group("jfeature");
            builder.jdkFeature(jFeature == null ? UNDEFINED : Integer.parseInt(jFeature));
            final boolean beta = m.group("beta") != null;
            builder.beta(beta ? m.group("beta") : "");
            if (beta) {
                builder.jdkInterim(0);
                builder.jdkUpdate(0);
            } else {
                final String jInterim = m.group("jinterim");
                final String jUpdate = m.group("jupdate");
                builder.jdkInterim(jInterim == null ? UNDEFINED : Integer.parseInt(jInterim));
                builder.jdkUpdate(jUpdate == null ? UNDEFINED : Integer.parseInt(jUpdate));
            }
            MVersion mandrelVersion = builder.build();
            if (mandrelVersion.jdkFeature == UNDEFINED) {
                LOGGER.warn("Failed to correctly parse Java feature (major) version from native-image version command output. " +
                        "JDK version constraints in tests won't work reliably.");
            }
            return mandrelVersion;
        }

        private static List<String> runNativeImageVersion(boolean inContainer) {
            final String out;
            if (inContainer) {
                final List<String> pullCmd = List.of(CONTAINER_RUNTIME, "pull", BUILDER_IMAGE);
                LOGGER.info("Running command " + pullCmd + " so as to pull Mandrel image locally.");
                try {
                    Commands.runCommand(pullCmd);
                } catch (IOException e) {
                    throw new RuntimeException("Failing to pull " + BUILDER_IMAGE, e);
                }
                final List<String> cmd = List.of(CONTAINER_RUNTIME, "run", "-t", BUILDER_IMAGE, "native-image", "--version");
                LOGGER.info("Running command " + cmd + " to determine Mandrel version used.");
                try {
                    out = Commands.runCommand(cmd);
                } catch (IOException e) {
                    throw new RuntimeException("Is " + CONTAINER_RUNTIME + " running?", e);
                }
            } else {
                final String TEST_TESTSUITE_ABSOLUTE_PATH = System.getProperty("FAKE_NATIVE_IMAGE_DIR", "");
                final List<String> cmd = getRunCommand(TEST_TESTSUITE_ABSOLUTE_PATH + (IS_THIS_WINDOWS ? "native-image.cmd" : "native-image"), "--version");
                LOGGER.info("Running command " + cmd + " to determine Mandrel version used.");
                try {
                    out = Commands.runCommand(cmd);
                } catch (IOException e) {
                    throw new RuntimeException("Is native-image command available? Check if you are not trying " +
                            "to run tests expecting locally installed native-image without having one. -Ptestsuite-builder-image is the " +
                            "correct profile for running without locally installed native-image.", e);
                }
            }
            return Arrays.asList(out.split(System.lineSeparator()));
        }
    }

    private static class VersionBuilder {
        private int jdkInterim;
        private int jdkFeature;
        private int jdkUpdate;
        private boolean jdkUsesSysLibs;
        private Version version;
        private String betaBits;

        VersionBuilder jdkInterim(int jdkInterim) {
            this.jdkInterim = jdkInterim;
            return this;
        }

        VersionBuilder beta(String betaBits) {
            this.betaBits = betaBits;
            return this;
        }

        VersionBuilder jdkUsesSysLibs(boolean jdkUsesSysLibs) {
            this.jdkUsesSysLibs = jdkUsesSysLibs;
            return this;
        }

        VersionBuilder jdkFeature(int jdkFeature) {
            this.jdkFeature = jdkFeature;
            return this;
        }

        VersionBuilder jdkUpdate(int jdkUpdate) {
            this.jdkUpdate = jdkUpdate;
            return this;
        }

        VersionBuilder version(Version version) {
            this.version = version;
            return this;
        }

        MVersion build() {
            return new MVersion(version, jdkUsesSysLibs, jdkFeature, jdkInterim, jdkUpdate, betaBits);
        }
    }

    static class InContainer {
        private static volatile MVersion mVersion = MVersion.of(true);
        static void resetInstance() { // used in tests
            mVersion = MVersion.of(true);
        }
    }

    static class Locally {
        private static volatile MVersion mVersion = MVersion.of(false);
        static void resetInstance() { // used in tests
            mVersion = MVersion.of(false);
        }
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
