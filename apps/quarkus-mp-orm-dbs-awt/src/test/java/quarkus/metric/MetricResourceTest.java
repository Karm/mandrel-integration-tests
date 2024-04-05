package quarkus.metric;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
public class MetricResourceTest {

    @Test
    public void testHealth() {
        given()
                .when().get("metric/increment")
                .then()
                .statusCode(200)
                .body(equalTo("1"));
        given()
                .when().get("q/metrics")
                .then()
                .statusCode(200)
                .body(containsStringIgnoringCase("MetricController_endpoint_counter_total 1.0"));
    }
}
