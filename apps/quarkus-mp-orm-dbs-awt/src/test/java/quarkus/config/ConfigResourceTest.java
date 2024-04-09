package quarkus.config;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

@QuarkusTest
public class ConfigResourceTest {

    @Test
    public void testInjected() {
        given()
                .when().get("config/injected")
                .then()
                .statusCode(200)
                .body(is("Injected by CDI: INJECTED"));
    }

    @Test
    public void testLookup() {
        given()
                .when().get("config/lookup")
                .then()
                .statusCode(200)
                .body(is("ConfigProvider lookup: LOOKED UP"));
    }
}
