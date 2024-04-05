package quarkus.qute;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsStringIgnoringCase;

@QuarkusTest
public class QuteResourceTest {

    @Test
    public void testTemplate() {
        given()
                .when().get("some-page")
                .then()
                .statusCode(200)
                .body(containsStringIgnoringCase("RESTEasy & Qute"));
    }
}
