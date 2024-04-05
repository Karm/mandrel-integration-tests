package quarkus.health;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
public class HealthResourceTest {

    @Test
    public void testHealth() {
        given()
                .when().get("q/health")
                .then()
                .statusCode(200)
                .body("status", equalTo("UP"));
    }
}
