package quarkus.jwt;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.JWTOptions;
import io.vertx.ext.auth.PubSecKeyOptions;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.core.Response;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

@Path("/secured")
@ApplicationScoped
public class JWTResource {

    private String key;

    @PostConstruct
    public void init() {
        try (final InputStream is = Objects.requireNonNull(JWTResource.class.getResourceAsStream("/privateKey.pem"))) {
            key = new String(is.readAllBytes(), StandardCharsets.US_ASCII);
        } catch (Exception e) {
            key = null;
        }
    }

    @ConfigProperty(name = "quarkus.http.port")
    int port;

    @GET
    @Path("/test")
    public String testSecureCall() {
        if (key == null) {
            throw new WebApplicationException("Unable to read privateKey.pem", 500);
        }
        try (final Response response = ClientBuilder.newClient()
                .target("http://localhost:" + port + "/protected")
                .request().header("authorization", "Bearer " + generateJWT(key))
                .buildGet().invoke()) {
            return String.format("Claim value within JWT of 'custom-value' : %s", response.readEntity(String.class));
        }
    }

    private static String generateJWT(String key) {
        final JsonObject jsonObject = new JsonObject();
        jsonObject.put("iss", "https://server.example.com");
        jsonObject.put("sub", "XXX");
        jsonObject.put("aud", "targetService");
        jsonObject.put("jti", UUID.randomUUID().toString());
        jsonObject.put("iat", System.currentTimeMillis() / 1000);
        jsonObject.put("exp", (System.currentTimeMillis() + 30000) / 1000);
        jsonObject.put("upn", "XXX");
        jsonObject.put("custom-value", "My value");
        final JsonArray jsonArray = new JsonArray();
        jsonArray.add("user");
        jsonArray.add("protected");
        jsonObject.put("groups", jsonArray);
        final JWTAuth provider = JWTAuth.create(null, new JWTAuthOptions().addPubSecKey(new PubSecKeyOptions().setAlgorithm("RS256").setBuffer(key)));
        return provider.generateToken(new JsonObject().mergeIn(jsonObject), new JWTOptions().setAlgorithm("RS256"));
    }
}
