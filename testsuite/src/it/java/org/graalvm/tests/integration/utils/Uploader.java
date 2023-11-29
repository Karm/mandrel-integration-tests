package org.graalvm.tests.integration.utils;

import org.jboss.logging.Logger;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.graalvm.tests.integration.utils.Commands.getProperty;

public class Uploader {
    private static final Logger LOGGER = Logger.getLogger(Uploader.class.getName());

    public static final boolean PERF_APP_REPORT = Boolean.parseBoolean(getProperty("PERF_APP_REPORT", "false"));
    public static final String PERF_APP_ENDPOINT = getProperty("PERF_APP_ENDPOINT");
    public static final String PERF_APP_SECRET_TOKEN = getProperty("PERF_APP_SECRET_TOKEN");

    public static final String USER_AGENT = "Mandrel Integration TS";

    private static HttpClient hc = null;

    public static HttpResponse<String> postPayload(final String appContext, final String jsonPayload)
            throws URISyntaxException, IOException, InterruptedException {
        if (PERF_APP_ENDPOINT == null || PERF_APP_ENDPOINT.isEmpty() || PERF_APP_SECRET_TOKEN == null
                || PERF_APP_SECRET_TOKEN.isEmpty()) {
            LOGGER.error(
                    "Both PERF_APP_ENDPOINT and PERF_APP_SECRET_TOKEN (or -Dperf.app.endpoint -Dperf.app.secret.token) must" +
                            "be populated to use the uploader.");
            return null;
        }
        if (hc == null) {
            hc = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build();
        }
        final String[] headers = new String[] {
                "User-Agent", USER_AGENT,
                "token", PERF_APP_SECRET_TOKEN,
                "Content-Type", "application/json",
                "Accept", "application/json"
        };
        final HttpRequest releaseRequest = HttpRequest.newBuilder()
                .method("POST", HttpRequest.BodyPublishers.ofString(jsonPayload))
                .uri(new URI(PERF_APP_ENDPOINT + "/" + appContext))
                .headers(headers)
                .build();
        LOGGER.info("POSTing payload to " + releaseRequest.uri());
        return hc.send(releaseRequest, HttpResponse.BodyHandlers.ofString());
    }
}
