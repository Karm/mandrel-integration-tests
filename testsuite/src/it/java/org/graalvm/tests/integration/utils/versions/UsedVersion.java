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

import static org.graalvm.tests.integration.utils.Commands.BUILDER_IMAGE;
import static org.graalvm.tests.integration.utils.Commands.CONTAINER_RUNTIME;

/**
 * Supported `native-image --version' output:
 * GraalVM Version 20.1.0.4.Final (Mandrel Distribution) (Java Version 11.0.10+9)
 * native-image 21.1.0.0-Final (Mandrel Distribution) (Java Version 11.0.11+9)
 *
 * @author Michal Karm Babacek <karm@redhat.com>
 */
public class UsedVersion {
    private static Version version = null;
    private static Version versionInContainer = null;

    private static final Logger LOGGER = Logger.getLogger(UsedVersion.class.getName());

    private enum VersionType {
        OLD,
        NEW,
        INVALID
    }

    private static VersionType getVersionType(String[] parts) {
        if (parts.length > 2) {
            if ("native-image".equals(parts[0])) {
                return VersionType.NEW;
            }
            if ("GraalVM".equals(parts[0])) {
                return VersionType.OLD;
            }
        }
        return VersionType.INVALID;
    }

    private static Version parseVersion(VersionType versionType, String[] parts) {
        final String v = (versionType == VersionType.NEW) ? parts[1] : parts[2];
        // Invalid version string '20.1.0.4.Final'
        final String sanitized = v.toLowerCase()
                .replace(".f", "-f")
                .replace(".s", "-s");
        return Version.parse(sanitized);
    }

    public static Version getVersion(boolean inContainer) throws IOException {
        if (inContainer) {
            if (versionInContainer == null) {
                final List<String> cmd = List.of(CONTAINER_RUNTIME, "run", "-t", BUILDER_IMAGE, "native-image", "--version");
                LOGGER.info("Running command " + cmd.toString() + " to determine Mandrel version used.");
                final String out = Commands.runCommand(cmd);
                final String[] lines = out.split(System.lineSeparator());
                final String lastLine = lines[lines.length - 1];
                final String[] parts = lastLine.split(" ");
                final VersionType versionType = getVersionType(parts);
                if (versionType == VersionType.INVALID) {
                    throw new IllegalArgumentException("native-image command failed. " +
                            "Is " + CONTAINER_RUNTIME + " running and image " + BUILDER_IMAGE + " available? " +
                            "Output: " + lastLine);
                }
                versionInContainer = parseVersion(versionType, parts);
                LOGGER.info("The test suite runs with Mandrel version " + versionInContainer.toString() + " in container.");
            }
            return versionInContainer;
        }

        if (version == null) {
            final List<String> cmd = List.of("native-image", "--version");
            LOGGER.info("Running command " + cmd.toString() + " to determine Mandrel version used.");
            final String line = Commands.runCommand(cmd);
            final String[] parts = line.split(" ");
            final VersionType versionType = getVersionType(parts);
            if (versionType == VersionType.INVALID) {
                throw new IllegalArgumentException("native-image command failed. " +
                        "Is it on PATH? " +
                        "Output: " + line);
            }
            version = parseVersion(versionType, parts);
            LOGGER.info("The test suite runs with Mandrel version " + version.toString() + " installed locally on PATH.");
        }
        return version;
    }
}
