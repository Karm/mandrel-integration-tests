/*
 * Copyright (c) 2025, IBM. All rights reserved.
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

import org.graalvm.tests.integration.PerfCheckTest;
import org.jboss.logging.Logger;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Patrik Cerbak <pcerbak@redhat.com>
 */
public class HyperfoilHelper {
    private static final Logger LOGGER = Logger.getLogger(HyperfoilHelper.class.getName());

    public static void uploadBenchmark(Apps app, File appDir, String urlContent, HttpClient hc) throws URISyntaxException, IOException, InterruptedException {
        final HttpRequest uploadRequest = HttpRequest.newBuilder()
                .uri(new URI(urlContent))
                .header("Content-Type", "text/vnd.yaml")
                .POST(HttpRequest.BodyPublishers.ofFile(Path.of(appDir.getAbsolutePath() + "/benchmark.hf.yaml")))
                .build();
        final HttpResponse<String> releaseResponse = hc.send(uploadRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(204, releaseResponse.statusCode(), "App returned a non HTTP 204 response. The perf report is invalid.");
        LOGGER.info("Hyperfoil upload response code: " + releaseResponse.statusCode());
    }
}
