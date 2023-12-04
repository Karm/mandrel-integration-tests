/*
 * Copyright (c) 2023, Red Hat Inc. All rights reserved.
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
package org.graalvm.tests.integration.utils.thresholds;

import org.apache.logging.log4j.util.Strings;
import org.graalvm.tests.integration.utils.versions.QuarkusVersion;
import org.graalvm.tests.integration.utils.versions.UsedVersion;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.graalvm.tests.integration.utils.versions.MandrelVersionCondition.jdkConstraintSatisfied;
import static org.graalvm.tests.integration.utils.versions.MandrelVersionCondition.mandrelConstraintSatisfied;
import static org.graalvm.tests.integration.utils.versions.QuarkusVersionCondition.quarkusConstraintSatisfied;

/**
 * See @README.md
 *
 * @author Michal Karm Babacek <karm@redhat.com>
 */
public class Thresholds {

    private static final Logger LOGGER = Logger.getLogger(Thresholds.class.getName());

    //@formatter:off
    public static final Pattern MVERSION_PATTERN = Pattern.compile(
            "\\s*@IfMandrelVersion\\s*\\((\\s*" +
                    "(?:min\\s*=\\s*\"(?<min>[^\"]+?)\"\\s*,?\\s*)|\\s*" +
                    "(?:max\\s*=\\s*\"(?<max>[^\"]+?)\"\\s*,?\\s*)|\\s*" +
                    "(?:minJDK\\s*=\\s*\"(?<minJDK>[^\"]+?)\"\\s*,?\\s*)|\\s*" +
                    "(?:maxJDK\\s*=\\s*\"(?<maxJDK>[^\"]+?)\"\\s*,?\\s*)|\\s*" +
                    "(?:inContainer\\s*=\\s*(?<inContainer>(?:true|false)?)\\s*,?\\s*))+\\s*\\)\\s*");
    public static final Pattern QVERSION_PATTERN = Pattern.compile(
            "\\s*@IfQuarkusVersion\\s*\\((\\s*" +
                    "(?:min\\s*=\\s*\"(?<min>[^\"]+?)\"\\s*,?\\s*)|\\s*" +
                    "(?:max\\s*=\\s*\"(?<max>[^\"]+?)\"\\s*,?\\s*))+\\s*\\)\\s*");
    public static final Pattern PROP_PATTERN = Pattern.compile(
            "\\s*(?<key>[^=]+?)\\s*=\\s*(?<value>[0-9]+?)\\s*");
    //@formatter:on
    private static final String QMARK = "@IfQ";
    private static final String MMARK = "@IfM";

    public static Map<String, Long> parseProperties(final Path conf) throws IOException {
        final Map<String, Long> props = new HashMap<>();
        // Ignore empty lines, leading, trailing spaces, comments
        final List<String> lines = Files.readAllLines(conf).stream()
                .map(String::trim)
                .filter(line -> !Strings.isBlank(line) && !line.startsWith("#"))
                .collect(Collectors.toList());
        boolean useProp = true;
        for (int i = 0; i < lines.size(); i++) {
            final String line = lines.get(i);
            // Mandrel's annotation first
            if (line.startsWith(MMARK)) {
                final Boolean ifMandrel = ifMandrel(line);
                if (ifMandrel == null) {
                    continue;
                }
                if (i + 1 < lines.size()) {
                    final String lookAhead = lines.get(i + 1);
                    if (lookAhead.startsWith(QMARK)) {
                        final Boolean ifQuarkus = ifQuarkus(lookAhead);
                        if (ifQuarkus == null) {
                            // IfQuarkus was garbage for some reason, we still use the IfMandrel.
                            useProp = ifMandrel;
                        } else {
                            // Both must be satisfied.
                            useProp = ifMandrel && ifQuarkus;
                        }
                        // We do not re-evaluate the IfQuarkus line.
                        i++;
                    } else {
                        useProp = ifMandrel;
                    }
                } else {
                    useProp = ifMandrel;
                }
                continue;
            }
            // Quarkus' annotation first
            if (line.startsWith(QMARK)) {
                final Boolean ifQuarkus = ifQuarkus(line);
                if (ifQuarkus == null) {
                    continue;
                }
                if (i + 1 < lines.size()) {
                    final String lookAhead = lines.get(i + 1);
                    if (lookAhead.startsWith(MMARK)) {
                        final Boolean ifMandrel = ifMandrel(lookAhead);
                        if (ifMandrel == null) {
                            // IfMandrel was garbage for some reason, we still use the IfQuarkus
                            useProp = ifQuarkus;
                        } else {
                            // Both must be satisfied.
                            useProp = ifMandrel && ifQuarkus;
                        }
                        // We do not re-evaluate the IfMandrel line.
                        i++;
                    } else {
                        useProp = ifQuarkus;
                    }
                } else {
                    useProp = ifQuarkus;
                }
                continue;
            }
            final Matcher propMatch = PROP_PATTERN.matcher(line);
            if (propMatch.matches()) {
                final String key = propMatch.group("key");
                final String value = propMatch.group("value");
                if (useProp) {
                    props.put(key, Long.parseLong(value));
                }
            } else {
                LOGGER.error("Line '" + line + "' does not match the pattern '" + PROP_PATTERN.pattern() + "'. Ignoring.");
            }
        }
        return props;
    }

    public static Boolean ifMandrel(final String line) {
        final Matcher mVerMatch = MVERSION_PATTERN.matcher(line);
        if (mVerMatch.matches()) {
            final String min = mVerMatch.group("min");
            final String max = mVerMatch.group("max");
            final String minJDK = mVerMatch.group("minJDK");
            final String maxJDK = mVerMatch.group("maxJDK");
            final boolean inContainer = Boolean.parseBoolean(mVerMatch.group("inContainer"));
            return (jdkConstraintSatisfied(minJDK, maxJDK, inContainer) && mandrelConstraintSatisfied(UsedVersion.getVersion(inContainer), min, max));
        }
        LOGGER.error("Line '" + line + "' does not match the pattern '" + MVERSION_PATTERN.pattern() + "' although it starts with '@IfMandrel.*'. Ignoring.");
        return null;
    }

    public static Boolean ifQuarkus(final String line) {
        final Matcher qVerMatch = QVERSION_PATTERN.matcher(line);
        if (qVerMatch.matches()) {
            final String min = qVerMatch.group("min");
            final String max = qVerMatch.group("max");
            return (quarkusConstraintSatisfied(new QuarkusVersion(), min, max));
        }
        LOGGER.error("Line '" + line + "' does not match the pattern '" + QVERSION_PATTERN.pattern() + "' although it starts with '@IfQuarkus.*'. Ignoring.");
        return null;
    }
}
