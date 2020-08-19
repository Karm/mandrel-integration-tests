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
            BuildAndRunCmds.RANDOM_NUMBERS),
    MICRONAUT_HELLOWORLD("apps" + File.separator + "micronaut-helloworld",
            URLContent.MICRONAUT_HELLOWORLD,
            WhitelistLogLines.MICRONAUT_HELLOWORLD,
            BuildAndRunCmds.MICRONAUT_HELLOWORLD),
    QUARKUS_FULL_MICROPROFILE("apps" + File.separator + "quarkus-full-microprofile",
            URLContent.QUARKUS_FULL_MICROPROFILE,
            WhitelistLogLines.QUARKUS_FULL_MICROPROFILE,
            BuildAndRunCmds.QUARKUS_FULL_MICROPROFILE),
    HELIDON_QUICKSTART_SE("apps" + File.separator + "helidon-quickstart-se",
            URLContent.HELIDON_QUICKSTART_SE,
            WhitelistLogLines.HELIDON_QUICKSTART_SE,
            BuildAndRunCmds.HELIDON_QUICKSTART_SE);

    public final String dir;
    public final URLContent urlContent;
    public final WhitelistLogLines whitelistLogLines;
    public final BuildAndRunCmds buildAndRunCmds;
    public final Map<String, Long> thresholdProperties = new HashMap<>();

    Apps(String dir, URLContent urlContent, WhitelistLogLines whitelistLogLines, BuildAndRunCmds buildAndRunCmds) {
        this.dir = dir;
        this.urlContent = urlContent;
        this.whitelistLogLines = whitelistLogLines;
        this.buildAndRunCmds = buildAndRunCmds;
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
