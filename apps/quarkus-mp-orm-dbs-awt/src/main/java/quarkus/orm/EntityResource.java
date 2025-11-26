package quarkus.orm;

import io.quarkus.panache.common.Sort;
import quarkus.orm.db1.DB1Entity;
import quarkus.orm.db2.DB2Entity;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.util.List;

@Path("orm/entities")
@ApplicationScoped
@Produces("application/json")
@Consumes("application/json")
public class EntityResource {

    @GET
    @Path("/db1")
    public List<DB1Entity> getDB1() {
        return DB1Entity.listAll(Sort.by("field"));
    }

    @GET
    @Path("/db2")
    public List<DB2Entity> getDB2() {
        return DB2Entity.listAll(Sort.by("field"));
    }

    @POST
    @Path("/db1")
    @Transactional
    public Response createDB1(DB1Entity entity) {
        if (entity.id != null) {
            throw new WebApplicationException("Id was invalidly set on request.", Response.Status.BAD_REQUEST);
        }
        entity.persist();
        return Response.ok(entity).status(Response.Status.CREATED).build();
    }

    @POST
    @Path("/db2")
    @Transactional
    public Response createDB2(DB2Entity entity) {
        if (entity.id != null) {
            throw new WebApplicationException("Id was invalidly set on request.", Response.Status.BAD_REQUEST);
        }
        entity.persist();
        return Response.ok(entity).status(Response.Status.CREATED).build();
    }
}
