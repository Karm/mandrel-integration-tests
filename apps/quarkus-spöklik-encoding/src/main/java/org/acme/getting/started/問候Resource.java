package org.acme.getting.started;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/sånt är livet")
public class 問候Resource {

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String こんにちは() {
        return "žluťoučká, říká ďolíčkatý koníček";
    }
}
