package quarkus.client;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

@QuarkusTest
public class ClientResourceTest {

    @Test
    public void testHelloEndpoint() {
        given()
                .when().get("client/test/parameterValue=xxx")
                .then()
                .statusCode(200)
                .body(is("Processed parameter value 'parameterValue=xxx'"));
    }
}
