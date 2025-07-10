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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.graalvm.tests.integration.RuntimesSmokeTest.BASE_DIR;
import static org.graalvm.tests.integration.utils.thresholds.Thresholds.parseProperties;
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
    QUARKUS_MP_ORM_DBS_AWT("apps" + File.separator + "quarkus-mp-orm-dbs-awt",
            URLContent.NONE,
            WhitelistLogLines.QUARKUS_MP_ORM_DBS_AWT,
            BuildAndRunCmds.QUARKUS_MP_ORM_DBS_AWT,
            ContainerNames.NONE),
    QUARKUS_BUILDER_IMAGE_MP_ORM_DBS_AWT("apps" + File.separator + "quarkus-mp-orm-dbs-awt",
            URLContent.NONE,
            WhitelistLogLines.QUARKUS_MP_ORM_DBS_AWT,
            BuildAndRunCmds.QUARKUS_BUILDER_IMAGE_MP_ORM_DBS_AWT,
            ContainerNames.QUARKUS_BUILDER_IMAGE_MP_ORM_DBS_AWT),
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
            ContainerNames.NONE),
    MONITOR_OFFSET_OK("apps" + File.separator + "monitor-field-offset",
            URLContent.NONE,
            WhitelistLogLines.MONITOR_OFFSET,
            BuildAndRunCmds.MONITOR_OFFSET_OK,
            ContainerNames.NONE),
    MONITOR_OFFSET_OK_BUILDER_IMAGE("apps" + File.separator + "monitor-field-offset",
            URLContent.NONE,
            WhitelistLogLines.MONITOR_OFFSET,
            BuildAndRunCmds.MONITOR_OFFSET_OK_BUILDER_IMAGE,
            ContainerNames.MONITOR_OFFSET_BUILDER_IMAGE),
    MONITOR_OFFSET_NOK("apps" + File.separator + "monitor-field-offset",
            URLContent.NONE,
            WhitelistLogLines.MONITOR_OFFSET,
            BuildAndRunCmds.MONITOR_OFFSET_NOK,
            ContainerNames.NONE),
    MONITOR_OFFSET_NOK_BUILDER_IMAGE("apps" + File.separator + "monitor-field-offset",
            URLContent.NONE,
            WhitelistLogLines.MONITOR_OFFSET,
            BuildAndRunCmds.MONITOR_OFFSET_NOK_BUILDER_IMAGE,
            ContainerNames.MONITOR_OFFSET_BUILDER_IMAGE),
    FOR_SERIALIZATION("apps" + File.separator + "for-serialization",
            URLContent.NONE,
            WhitelistLogLines.FOR_SERIALIZATION,
            BuildAndRunCmds.FOR_SERIALIZATION,
            ContainerNames.NONE),
    FOR_SERIALIZATION_BUILDER_IMAGE("apps" + File.separator + "for-serialization",
            URLContent.NONE,
            WhitelistLogLines.FOR_SERIALIZATION,
            BuildAndRunCmds.FOR_SERIALIZATION_BUILDER_IMAGE,
            ContainerNames.FOR_SERIALIZATION_BUILDER_IMAGE),
    JDK_REFLECTIONS("apps" + File.separator + "jdkreflections",
            URLContent.NONE,
            WhitelistLogLines.JDK_REFLECTIONS,
            BuildAndRunCmds.JDK_REFLECTIONS,
            ContainerNames.NONE),
    CACERTS("apps" + File.separator + "cacerts",
            URLContent.NONE,
            WhitelistLogLines.CACERTS,
            BuildAndRunCmds.CACERTS,
            ContainerNames.NONE),
    JDK_REFLECTIONS_BUILDER_IMAGE("apps" + File.separator + "jdkreflections",
            URLContent.NONE,
            WhitelistLogLines.JDK_REFLECTIONS,
            BuildAndRunCmds.JDK_REFLECTIONS_BUILDER_IMAGE,
            ContainerNames.JDK_REFLECTIONS_BUILDER_IMAGE),
    VTHREADS_PROPS("apps" + File.separator + "vthread_props",
            URLContent.NONE,
            WhitelistLogLines.VTHREADS,
            BuildAndRunCmds.VTHREADS_PROPS,
            ContainerNames.NONE),
    VTHREADS_PROPS_BUILDER_IMAGE("apps" + File.separator + "vthread_props",
            URLContent.NONE,
            WhitelistLogLines.VTHREADS,
            BuildAndRunCmds.VTHREADS_PROPS_BUILDER_IMAGE,
            ContainerNames.VTHREADS_PROPS_BUILDER_IMAGE);

    public final String dir;
    public final URLContent urlContent;
    public final WhitelistLogLines whitelistLogLines;
    public final BuildAndRunCmds buildAndRunCmds;
    public final Map<String, Long> thresholdProperties = new HashMap<>();
    public final ContainerNames runtimeContainer;

    Apps(String dir, URLContent urlContent, WhitelistLogLines whitelistLogLines, BuildAndRunCmds buildAndRunCmds, ContainerNames runtimeContainer) {
        this.dir = dir;
        this.urlContent = urlContent;
        this.whitelistLogLines = whitelistLogLines;
        this.buildAndRunCmds = buildAndRunCmds;
        this.runtimeContainer = runtimeContainer;

        // Some apps don't have threshold.conf
        final Path tcFile = Path.of(BASE_DIR, dir, "threshold.conf");
        if (Files.exists(tcFile)) {
            final String appDirNormalized = dir.toUpperCase().replace(File.separator, "_").replace('-', '_') + "_";
            try {
                final Map<String, Long> props = parseProperties(tcFile);
                for (String pn : props.keySet()) {
                    final String normPn = pn.toUpperCase().replace('.', '_');
                    final String env = System.getenv().get(appDirNormalized + normPn);
                    if (StringUtils.isNotBlank(env)) {
                        props.replace(pn, Long.parseLong(env));
                    }
                    final String sys = System.getProperty(appDirNormalized + normPn);
                    if (StringUtils.isNotBlank(sys)) {
                        props.replace(pn, Long.parseLong(sys));
                    }
                    thresholdProperties.put(pn, props.get(pn));
                }
            } catch (NumberFormatException e) {
                fail("Check threshold.conf and Sys and Env variables " +
                        "(upper case, underscores instead of dots). " +
                        "All values are expected to be of type long.", e);
            } catch (IOException e) {
                fail("Couldn't find " + tcFile, e);
            }
        }
    }
}
