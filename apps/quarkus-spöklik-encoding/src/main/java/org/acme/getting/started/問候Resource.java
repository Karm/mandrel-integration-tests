package org.acme.getting.started;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Path("/sånt är livet")
public class 問候Resource {

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String こんにちは() {
        return "žluťoučká, říká ďolíčkatý koníček";
    }
}
