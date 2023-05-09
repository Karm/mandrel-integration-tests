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

import static org.graalvm.tests.integration.utils.Commands.getProperty;

public class QuarkusVersion implements Comparable<QuarkusVersion> {

    private final String version;
    private final int major;
    private final int minor;

    private final int patch;
    private final boolean snapshot;
    private final String gitSHA;

    public QuarkusVersion() {
        this(getProperty("QUARKUS_VERSION", "2.13.7.Final"));
    }

    public QuarkusVersion(String version) {
        this.version = version;
        this.snapshot = version.contains("SNAPSHOT");
        if (this.snapshot) {
            this.major = Integer.parseInt(version.split("-")[0]);
            this.minor = 0;
            this.patch = 0;
            this.gitSHA = getProperty("QUARKUS_VERSION_GITSHA", "");
        } else {
            final String[] split = version.split("\\.");
            this.major = Integer.parseInt(split[0]);
            this.minor = Integer.parseInt(split[1]);
            this.patch = Integer.parseInt(split[2]);
            this.gitSHA = null;
        }
    }

    @Override
    public int compareTo(QuarkusVersion other) {
        if (this.snapshot) {
            if (other.snapshot) {
                return 0;
            } else {
                return 1;
            }
        }
        if (other.snapshot) {
            return -1;
        }
        int result = this.major - other.major;
        if (result != 0) {
            return result;
        }
        result = this.minor - other.minor;
        if (result != 0) {
            return result;
        }
        result = this.patch - other.patch;
        if (result != 0) {
            return result;
        }
        return 0;
    }

    public boolean majorIs(int major) {
        return this.major == major;
    }

    public boolean isSnapshot() {
        return snapshot;
    }

    public String getVersionString() {
        return version;
    }

    public String getGitSHA() {
        return gitSHA;
    }

    @Override
    public String toString() {
        return "QuarkusVersion{" +
                "version='" + version + '\'' +
                ", major=" + major +
                ", minor=" + minor +
                ", patch=" + patch +
                ", snapshot=" + snapshot +
                '}';
    }
}
