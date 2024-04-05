package quarkus.jwt;

import org.eclipse.microprofile.jwt.Claim;
import org.eclipse.microprofile.jwt.ClaimValue;

import javax.annotation.security.RolesAllowed;
import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;

@Path("/protected")
@RequestScoped
public class ProtectedResource {

    @Inject
    @Claim("custom-value")
    ClaimValue<String> custom;

    @GET
    @RolesAllowed("protected")
    public String getJWTBasedValue() {
        return "PROTECTED: " + custom.getValue();
    }
}
