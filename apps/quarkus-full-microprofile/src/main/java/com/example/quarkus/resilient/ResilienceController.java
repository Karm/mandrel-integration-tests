package com.example.quarkus.resilient;

import org.eclipse.microprofile.faulttolerance.Fallback;
import org.eclipse.microprofile.faulttolerance.Timeout;

import javax.enterprise.context.ApplicationScoped;
import javax.ws.rs.GET;
import javax.ws.rs.Path;

@Path("/resilience")
@ApplicationScoped
public class ResilienceController {

    @Fallback(fallbackMethod = "fallback")
    @Timeout(500)
    @GET
    public String checkTimeout() {
        try {
            // This is just for a test/demo purpose. Don't copy it to your app.
            Thread.sleep(700L);
        } catch (InterruptedException e) {
            // Silence is golden
        }
        return "Never from normal processing";
    }

    public String fallback() {
        return "Fallback answer due to timeout";
    }
}
