package quarkus.faulttolerance;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

@QuarkusTest
public class ResilienceResourceTest {

    @Test
    public void testFallback() {
        given()
                .when().get("resilience")
                .then()
                .statusCode(200)
                .body(is("Fallback answer due to timeout"));
    }
}
