package com.example.quarkus.secure;

import org.eclipse.microprofile.jwt.Claim;
import org.eclipse.microprofile.jwt.ClaimValue;

import javax.annotation.security.RolesAllowed;
import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;

@Path("/protected")
@RequestScoped
public class ProtectedController {

    @Inject
    @Claim("custom-value")
    // https://quarkus.io/guides/cdi-reference#private-members
    // - private ClaimValue<String> custom;
    ClaimValue<String> custom;

    @GET
    @RolesAllowed("protected")
    public String getJWTBasedValue() {
        return "Protected Resource; Custom value : " + custom.getValue();
    }
}
