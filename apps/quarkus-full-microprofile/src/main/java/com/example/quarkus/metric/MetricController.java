package com.example.quarkus.metric;

import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.MetricUnits;
import org.eclipse.microprofile.metrics.annotation.Gauge;
import org.eclipse.microprofile.metrics.annotation.Metric;
import org.eclipse.microprofile.metrics.annotation.Timed;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import java.util.Random;

@Path("/metric")
@ApplicationScoped
public class MetricController {

    @Inject
    @Metric(name = "endpoint_counter")
    Counter counter;

    @Path("timed")
    @Timed(name = "timed-request")
    @GET
    public String timedRequest() {
        // This is just for demo/test proposes!!!
        int wait = new Random().nextInt(1000);
        try {
            Thread.sleep(wait);
        } catch (InterruptedException e) {
            // Demo
            e.printStackTrace();
        }
        return "Request is used in statistics, check with the Metrics call.";
    }

    @Path("increment")
    @GET
    public long doIncrement() {
        counter.inc();
        return counter.getCount();
    }

    @Gauge(name = "counter_gauge", unit = MetricUnits.NONE)
    long getCustomerCount() {
        return counter.getCount();
    }
}
