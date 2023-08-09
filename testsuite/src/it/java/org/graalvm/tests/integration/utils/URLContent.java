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

import static org.graalvm.tests.integration.utils.Commands.QUARKUS_VERSION;

/**
 * Available endpoints and expected content.
 *
 * @author Michal Karm Babacek <karm@redhat.com>
 */
public enum URLContent {
    NONE(new String[][]{}),

    QUARKUS_FULL_MICROPROFILE(new String[][]{
            new String[]{"http://localhost:8080/" + (QUARKUS_VERSION.majorIs(1) ? "" : "q/") + "health", "\"UP\""},
            new String[]{"http://localhost:8080/" + (QUARKUS_VERSION.majorIs(1) ? "" : "q/") + "metrics", "ontroller_timed_request_seconds_count"},
            new String[]{"http://localhost:8080/" + (QUARKUS_VERSION.majorIs(1) ? "" : "q/") + "openapi", "/resilience"},
            new String[]{"http://localhost:8080", "Hello from a full MicroProfile suite"},
            new String[]{"http://localhost:8080/data/hello", "Hello World"},
            new String[]{"http://localhost:8080/data/config/injected", "Config value as Injected by CDI Injected value"},
            new String[]{"http://localhost:8080/data/config/lookup", "Config value from ConfigProvider lookup value"},
            new String[]{"http://localhost:8080/data/resilience", "Fallback answer due to timeout"},
            new String[]{"http://localhost:8080/data/metric/timed", "Request is used in statistics, check with the Metrics call."},
            new String[]{"http://localhost:8080/data/secured/test", "Jessie specific value"},
            new String[]{"http://localhost:8080/data/client/test/parameterValue=xxx", "Processed parameter value 'parameterValue=xxx'"}
    }),

    QUARKUS_JSON_PERF(new String[][]{
            // apps/quarkus-json/src/main/resources/application.properties
            new String[]{"http://localhost:8887/ready", "Yes."},
            new String[]{"http://localhost:8887/dump"},
            new String[]{"http://localhost:8887/load"},
            new String[]{"http://localhost:8887/load/hash"},
    }),

    QUARKUS_BUILDER_IMAGE_ENCODING(new String[][]{
            new String[]{"http://localhost:8080/s%C3%A5nt%20%C3%A4r%20livet", "žluťoučká, říká ďolíčkatý koníček"}}),

    JFR_PERF(new String[][]{
            new String[]{"http://0.0.0.0:8080/hello/", "hello"},
            new String[]{"http://0.0.0.0:8090/benchmark"},
            new String[]{"http://0.0.0.0:8090/openapi", "openapi: 3.0.0"},
            new String[]{"http://0.0.0.0:8090/benchmark/jfr-hyperfoil/start"},
    }),
    HELIDON_QUICKSTART_SE(new String[][]{
            new String[]{"http://localhost:8080/greet", "Hello World!"},
            new String[]{"http://localhost:8080/greet/Karm", "Hello Karm!"},
            new String[]{"http://localhost:8080/health", "\"UP\""},
            new String[]{"http://localhost:8080/metrics", "availableProcessors"}
    });

    public final String[][] urlContent;

    URLContent(String[][] urlContent) {
        this.urlContent = urlContent;
    }
}
