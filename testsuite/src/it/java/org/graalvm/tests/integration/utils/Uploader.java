package org.graalvm.tests.integration.utils;

import org.jboss.logging.Logger;
import org.json.JSONObject;
import org.junit.platform.commons.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.graalvm.tests.integration.utils.Commands.getProperty;
import static org.jboss.resteasy.spi.HttpResponseCodes.SC_ACCEPTED;
import static org.jboss.resteasy.spi.HttpResponseCodes.SC_CREATED;
import static org.jboss.resteasy.spi.HttpResponseCodes.SC_OK;

public class Uploader {
    private static final Logger LOGGER = Logger.getLogger(Uploader.class.getName());

    public static final boolean PERF_APP_REPORT = Boolean.parseBoolean(getProperty("PERF_APP_REPORT", "false"));
    public static final String PERF_APP_ENDPOINT = getProperty("PERF_APP_ENDPOINT");
    public static final String PERF_APP_SECRET_TOKEN = getProperty("PERF_APP_SECRET_TOKEN");
    public static final String PERF_APP_RUNNER_INFO_ID = getProperty("PERF_APP_RUNNER_INFO_ID");

    public static final String USER_AGENT = "Mandrel Integration TS";

    private static HttpClient hc = null;

    public static HttpResponse<String> postRuntimePayload(final String appContext, final String jsonPayload) throws URISyntaxException, IOException, InterruptedException {
        if (PERF_APP_ENDPOINT == null || PERF_APP_ENDPOINT.isEmpty() || PERF_APP_SECRET_TOKEN == null || PERF_APP_SECRET_TOKEN.isEmpty()) {
            LOGGER.error("Both PERF_APP_ENDPOINT and PERF_APP_SECRET_TOKEN (or -Dperf.app.endpoint -Dperf.app.secret.token) must" +
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

    public static HttpResponse<String> postBuildtimePayload(final String appContext, final String qversion, final String mversion,
            final String... jsonPayload) throws URISyntaxException, IOException, InterruptedException {
        if (StringUtils.isBlank(PERF_APP_ENDPOINT) || StringUtils.isBlank(PERF_APP_SECRET_TOKEN)) {
            LOGGER.error("Both PERF_APP_ENDPOINT and PERF_APP_SECRET_TOKEN (or -Dperf.app.endpoint -Dperf.app.secret.token) must" +
                    "be populated to use the uploader.");
            return null;
        }
        if (jsonPayload.length < 1 || jsonPayload.length > 2) {
            LOGGER.error("Invalid number of JSON payloads. Expected 1 or 2, got " + jsonPayload.length);
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
        final HttpRequest mainPayload = HttpRequest.newBuilder()
                .method("POST", HttpRequest.BodyPublishers.ofString(jsonPayload[0]))
                .uri(new URI(PERF_APP_ENDPOINT + "/" + appContext + "/import?t=" + mversion + "," + qversion
                        + (PERF_APP_RUNNER_INFO_ID != null ? "&runnerid=" + PERF_APP_RUNNER_INFO_ID : "")))
                .headers(headers)
                .build();
        LOGGER.info("POSTing to " + mainPayload.uri() + ", payload: " + jsonPayload[0]);
        final HttpResponse<String> r = hc.send(mainPayload, HttpResponse.BodyHandlers.ofString());
        if (jsonPayload.length == 2 && jsonPayload[1] != null && !jsonPayload[1].isEmpty()) {
            if (!(r.statusCode() == SC_CREATED || r.statusCode() == SC_ACCEPTED || r.statusCode() == SC_OK) || r.body().isEmpty()) {
                LOGGER.error("Failed to POST main payload, SKIPPING secondary payload: " + jsonPayload[1]);
                return r;
            }
            int id = (new JSONObject(r.body())).getInt("id");
            final HttpRequest secondaryPayload = HttpRequest.newBuilder()
                    .method("PUT", HttpRequest.BodyPublishers.ofString(jsonPayload[1]))
                    .uri(new URI(PERF_APP_ENDPOINT + "/" + appContext + "/" + id))
                    .headers(headers)
                    .build();
            LOGGER.info("POSTing payload to " + secondaryPayload.uri());
            return hc.send(secondaryPayload, HttpResponse.BodyHandlers.ofString());
        }
        return r;
    }
}
