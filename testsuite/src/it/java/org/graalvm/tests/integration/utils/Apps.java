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

import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static org.graalvm.tests.integration.RuntimesSmokeTest.BASE_DIR;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Maven commands.
 *
 * @author Michal Karm Babacek <karm@redhat.com>
 */
public enum Apps {

    RANDOM_NUMBERS("apps" + File.separator + "random-numbers",
            URLContent.NONE,
            WhitelistLogLines.NONE,
            BuildAndRunCmds.RANDOM_NUMBERS,
            ContainerNames.NONE),
    QUARKUS_FULL_MICROPROFILE("apps" + File.separator + "quarkus-full-microprofile",
            URLContent.QUARKUS_FULL_MICROPROFILE,
            WhitelistLogLines.QUARKUS_FULL_MICROPROFILE,
            BuildAndRunCmds.QUARKUS_FULL_MICROPROFILE,
            ContainerNames.NONE),
    QUARKUS_FULL_MICROPROFILE_PERF("apps" + File.separator + "quarkus-full-microprofile",
            URLContent.QUARKUS_FULL_MICROPROFILE,
            WhitelistLogLines.QUARKUS_FULL_MICROPROFILE,
            BuildAndRunCmds.QUARKUS_FULL_MICROPROFILE_PERF,
            ContainerNames.NONE),
    QUARKUS_JSON_PERF_PARSEONCE("apps" + File.separator + "quarkus-json",
            URLContent.QUARKUS_JSON_PERF,
            WhitelistLogLines.QUARKUS_FULL_MICROPROFILE,
            BuildAndRunCmds.QUARKUS_JSON_PERF_PARSEONCE,
            ContainerNames.NONE),
    QUARKUS_JSON_PERF("apps" + File.separator + "quarkus-json",
            URLContent.QUARKUS_JSON_PERF,
            WhitelistLogLines.QUARKUS_FULL_MICROPROFILE,
            BuildAndRunCmds.QUARKUS_JSON_PERF,
            ContainerNames.NONE),
    DEBUG_QUARKUS_FULL_MICROPROFILE("apps" + File.separator + "quarkus-full-microprofile",
            URLContent.NONE,
            WhitelistLogLines.QUARKUS_FULL_MICROPROFILE,
            BuildAndRunCmds.DEBUG_QUARKUS_FULL_MICROPROFILE,
            ContainerNames.NONE),
    DEBUG_QUARKUS_BUILDER_IMAGE_VERTX("apps" + File.separator + "quarkus-vertx",
            URLContent.DEBUG_QUARKUS_BUILDER_IMAGE_VERTX,
            WhitelistLogLines.DEBUG_QUARKUS_BUILDER_IMAGE_VERTX,
            BuildAndRunCmds.DEBUG_QUARKUS_BUILDER_IMAGE_VERTX,
            ContainerNames.DEBUG_QUARKUS_BUILDER_IMAGE_VERTX),
    QUARKUS_BUILDER_IMAGE_ENCODING("apps" + File.separator + "quarkus-spöklik-encoding",
            URLContent.QUARKUS_BUILDER_IMAGE_ENCODING,
            WhitelistLogLines.QUARKUS_BUILDER_IMAGE_ENCODING,
            BuildAndRunCmds.QUARKUS_BUILDER_IMAGE_ENCODING,
            ContainerNames.QUARKUS_BUILDER_IMAGE_ENCODING),
    HELIDON_QUICKSTART_SE("apps" + File.separator + "helidon-quickstart-se",
            URLContent.HELIDON_QUICKSTART_SE,
            WhitelistLogLines.HELIDON_QUICKSTART_SE,
            BuildAndRunCmds.HELIDON_QUICKSTART_SE,
            ContainerNames.NONE),
    TIMEZONES("apps" + File.separator + "timezones",
            URLContent.NONE,
            WhitelistLogLines.NONE,
            BuildAndRunCmds.TIMEZONES,
            ContainerNames.NONE),
    CALENDARS("apps" + File.separator + "calendars",
            URLContent.NONE,
            WhitelistLogLines.NONE,
            BuildAndRunCmds.CALENDARS,
            ContainerNames.NONE),
    RECORDANNOTATIONS("apps" + File.separator + "recordannotations",
            URLContent.NONE,
            WhitelistLogLines.NONE,
            BuildAndRunCmds.RECORDANNOTATIONS,
            ContainerNames.NONE),
    VERSIONS("apps" + File.separator + "versions",
            URLContent.NONE,
            WhitelistLogLines.NONE,
            BuildAndRunCmds.VERSIONS,
            ContainerNames.NONE),
    IMAGEIO("apps" + File.separator + "imageio",
            URLContent.NONE,
            WhitelistLogLines.IMAGEIO,
            BuildAndRunCmds.IMAGEIO,
            ContainerNames.NONE),
    IMAGEIO_BUILDER_IMAGE("apps" + File.separator + "imageio",
            URLContent.NONE,
            WhitelistLogLines.IMAGEIO_BUILDER_IMAGE,
            BuildAndRunCmds.IMAGEIO_BUILDER_IMAGE,
            ContainerNames.IMAGEIO_BUILDER_IMAGE),
    DEBUG_SYMBOLS_SMOKE("apps" + File.separator + "debug-symbols-smoke",
            URLContent.NONE,
            WhitelistLogLines.NONE,
            BuildAndRunCmds.DEBUG_SYMBOLS_SMOKE,
            ContainerNames.NONE),
    JFR_SMOKE("apps" + File.separator + "debug-symbols-smoke",
            URLContent.NONE,
            WhitelistLogLines.JFR,
            BuildAndRunCmds.JFR_SMOKE,
            ContainerNames.NONE),
    JFR_PERFORMANCE("apps" + File.separator + "jfr-native-image-performance",
            URLContent.JFR_PERF,
            WhitelistLogLines.JFR,
            BuildAndRunCmds.JFR_PERFORMANCE,
            ContainerNames.NONE),
    JFR_PERFORMANCE_BUILDER_IMAGE("apps" + File.separator + "jfr-native-image-performance",
            URLContent.JFR_PERF,
            WhitelistLogLines.JFR,
            BuildAndRunCmds.JFR_PERFORMANCE_BUILDER_IMAGE,
            ContainerNames.JFR_PERFORMANCE_BUILDER_IMAGE),
    PLAINTEXT_PERFORMANCE("apps" + File.separator + "jfr-native-image-performance",
            URLContent.JFR_PERF,
            WhitelistLogLines.JFR,
            BuildAndRunCmds.PLAINTEXT_PERFORMANCE,
            ContainerNames.NONE),
    PLAINTEXT_PERFORMANCE_BUILDER_IMAGE("apps" + File.separator + "jfr-native-image-performance",
            URLContent.JFR_PERF,
            WhitelistLogLines.JFR,
            BuildAndRunCmds.PLAINTEXT_PERFORMANCE_BUILDER_IMAGE,
            ContainerNames.JFR_PLAINTEXT_BUILDER_IMAGE),
    JFR_SMOKE_BUILDER_IMAGE("apps" + File.separator + "debug-symbols-smoke",
            URLContent.NONE,
            WhitelistLogLines.JFR,
            BuildAndRunCmds.JFR_SMOKE_BUILDER_IMAGE,
            ContainerNames.JFR_SMOKE_BUILDER_IMAGE),
    JFR_OPTIONS("apps" + File.separator + "timezones",
            URLContent.NONE,
            WhitelistLogLines.JFR,
            BuildAndRunCmds.JFR_OPTIONS,
            ContainerNames.NONE),
    JFR_OPTIONS_BUILDER_IMAGE("apps" + File.separator + "timezones",
            URLContent.NONE,
            WhitelistLogLines.JFR,
            BuildAndRunCmds.JFR_OPTIONS_BUILDER_IMAGE,
            ContainerNames.JFR_SMOKE_BUILDER_IMAGE),
    RESLOCATIONS("apps" + File.separator + "reslocations",
            URLContent.NONE,
            WhitelistLogLines.RESLOCATIONS,
            BuildAndRunCmds.RESLOCATIONS,
            ContainerNames.NONE);

    public final String dir;
    public final URLContent urlContent;
    public final WhitelistLogLines whitelistLogLines;
    public final BuildAndRunCmds buildAndRunCmds;
    public final Map<String, Long> thresholdProperties = new HashMap<>();
    public final ContainerNames runtimeContainer;

    Apps(String dir, URLContent urlContent, WhitelistLogLines whitelistLogLines, BuildAndRunCmds buildAndRunCmds,
            ContainerNames runtimeContainer) {
        this.dir = dir;
        this.urlContent = urlContent;
        this.whitelistLogLines = whitelistLogLines;
        this.buildAndRunCmds = buildAndRunCmds;
        this.runtimeContainer = runtimeContainer;
        File tpFile = new File(BASE_DIR + File.separator + dir + File.separator + "threshold.properties");
        // Some apps don't have threshold.properties
        if (tpFile.exists()) {
            String appDirNormalized = dir.toUpperCase()
                    .replace(File.separator, "_")
                    .replace('-', '_')
                    + "_";
            try (InputStream input = new FileInputStream(tpFile)) {
                Properties props = new Properties();
                props.load(input);
                for (String pn : props.stringPropertyNames()) {
                    String normPn = pn.toUpperCase().replace('.', '_');
                    String env = System.getenv().get(appDirNormalized + normPn);
                    if (StringUtils.isNotBlank(env)) {
                        props.replace(pn, env);
                    }
                    String sys = System.getProperty(appDirNormalized + normPn);
                    if (StringUtils.isNotBlank(sys)) {
                        props.replace(pn, sys);
                    }
                    thresholdProperties.put(pn, Long.parseLong(props.getProperty(pn)));
                }
            } catch (NumberFormatException e) {
                fail("Check threshold.properties and Sys and Env variables " +
                        "(upper case, underscores instead of dots). " +
                        "All values are expected to be of type long.");
            } catch (IOException e) {
                fail("Couldn't find " + tpFile.getAbsolutePath());
            }
        }
    }
}
