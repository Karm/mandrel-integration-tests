package quarkus.orm;

import io.quarkus.panache.common.Sort;
import quarkus.orm.db1.DB1Entity;
import quarkus.orm.db2.DB2Entity;

import javax.enterprise.context.ApplicationScoped;
import javax.transaction.Transactional;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
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
