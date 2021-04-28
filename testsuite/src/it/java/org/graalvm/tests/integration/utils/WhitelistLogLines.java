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

import java.util.regex.Pattern;

/**
 * Whitelists errors in log files.
 *
 * @author Michal Karm Babacek <karm@redhat.com>
 */
public enum WhitelistLogLines {

    // This is appended to all undermentioned listings
    ALL(new Pattern[]{
            // https://github.com/graalvm/mandrel/issues/125
            Pattern.compile(".*Using an older version of the labsjdk-11.*"),
            // Harmless download, e.g.
            // Downloaded from central: https://repo.maven.apache.org/maven2/org/apache/maven/maven-error-diagnostics...
            Pattern.compile(".*maven-error-diagnostics.*"),
            // JDK:
            Pattern.compile("WARNING.* reflective access.*"),
            Pattern.compile("WARNING: All illegal access operations.*"),
            Pattern.compile("WARNING: Please consider reporting this to the maintainers of com.google.inject.internal.cglib.*")
    }),

    NONE(new Pattern[]{}),

    MICRONAUT_HELLOWORLD(new Pattern[]{
            // Maven shade plugin warning, harmless.
            Pattern.compile(".*Discovered module-info.class. Shading will break its strong encapsulation.*"),
            // https://github.com/oracle/graal/blob/master/substratevm/src/com.oracle.svm.core/src/com/oracle/svm/core/jdk/VarHandleFeature.java#L199
            Pattern.compile(".*GR-10238.*"),
            // A Windows specific warning
            Pattern.compile(".*Failed to create WindowsAnsiOutputStream.*")
    }),

    QUARKUS_FULL_MICROPROFILE(new Pattern[]{
            // Some artifacts names...
            Pattern.compile(".*maven-error-diagnostics.*"),
            Pattern.compile(".*errorprone.*"),
            // Well, the RestClient demo probably should do some cleanup before shutdown...?
            Pattern.compile(".*Closing a class org.jboss.resteasy.client.jaxrs.engines.ApacheHttpClient.*"),
            // https://github.com/oracle/graal/blob/master/substratevm/src/com.oracle.svm.core/src/com/oracle/svm/core/jdk/VarHandleFeature.java#L199
            Pattern.compile(".*GR-10238.*"),
            // Unused argument on new Graal; Quarkus uses it for backward compatibility.
            Pattern.compile(".*Ignoring server-mode native-image argument --no-server.*"),
            // Windows specific warning
            Pattern.compile(".*oracle/graal/issues/2387.*")
    }),

    DEBUG_QUARKUS_BUILDER_IMAGE_VERTX(new Pattern[]{
            // https://github.com/oracle/graal/blob/master/substratevm/src/com.oracle.svm.core/src/com/oracle/svm/core/jdk/VarHandleFeature.java#L199
            Pattern.compile(".*GR-10238.*"),
            Pattern.compile(".*'table \"fruits\" does not exist, skipping'.*")
    }),

    HELIDON_QUICKSTART_SE(new Pattern[]{
            // Unused argument on new Graal
            Pattern.compile(".*Ignoring server-mode native-image argument --no-server.*")
    });

    public final Pattern[] errs;

    WhitelistLogLines(Pattern[] errs) {
        this.errs = errs;
    }
}
