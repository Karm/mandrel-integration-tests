package quarkus.jwt;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsStringIgnoringCase;

@QuarkusTest
public class JWTResourceTest {

    @Test
    public void testJWT() {
        given()
                .when().get("secured/test")
                .then()
                .statusCode(200)
                .body(containsStringIgnoringCase("PROTECTED: My value"));
    }
}
