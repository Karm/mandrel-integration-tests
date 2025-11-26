package quarkus.config;

import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

@Path("/config")
@RequestScoped
public class ConfigTestController {

    @Inject
    @ConfigProperty(name = "injected.value")
    String injectedValue;

    @Path("/injected")
    @GET
    public String getInjectedConfigValue() {
        return "Injected by CDI: " + injectedValue;
    }

    @Path("/lookup")
    @GET
    public String getLookupConfigValue() {
        return "ConfigProvider lookup: " + ConfigProvider.getConfig().getValue("value", String.class);
    }
}
