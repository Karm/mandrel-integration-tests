package quarkus.graphql;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
public class GraphQLResourceTest {

    @Test
    public void testGetSalutation() {
        final String salutation = "Hello Universe";
        given()
                .contentType(ContentType.JSON)
                .body("{\"query\":\"mutation { createSalutation(salutation: \\\"" + salutation + "\\\") }\"}")
                .when()
                .post("/graphql")
                .then()
                .statusCode(200)
                .body("data.createSalutation", equalTo(salutation));
        given()
                .contentType(ContentType.JSON)
                .body("{\"query\":\"query { getSalutation(salutationId: 0) }\"}")
                .when()
                .post("/graphql")
                .then()
                .statusCode(200)
                .body("data.getSalutation", equalTo(salutation));
    }
}
