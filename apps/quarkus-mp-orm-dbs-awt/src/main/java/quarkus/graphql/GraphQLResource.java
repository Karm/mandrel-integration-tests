package quarkus.graphql;

import io.smallrye.graphql.api.Subscription;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.operators.multi.processors.BroadcastProcessor;
import org.eclipse.microprofile.graphql.Description;
import org.eclipse.microprofile.graphql.GraphQLApi;
import org.eclipse.microprofile.graphql.Mutation;
import org.eclipse.microprofile.graphql.Name;
import org.eclipse.microprofile.graphql.Query;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.List;

@GraphQLApi
@ApplicationScoped
public class GraphQLResource {

    private final List<String> salutations = new ArrayList<>();
    private final BroadcastProcessor<String> processor = BroadcastProcessor.create();

    @Mutation
    public String createSalutation(String salutation) {
        salutations.add(salutation);
        processor.onNext(salutation);
        return salutation;
    }

    @Query("getSalutation")
    @Description("Meh")
    public String getSalutation(@Name("salutationId") int id) {
        if (id >= 0 && id < salutations.size()) {
            return salutations.get(id);
        } else {
            throw new IllegalArgumentException("Salutation with the given id does not exist.");
        }
    }

    @Subscription
    public Multi<String> salutationSent() {
        return processor;
    }
}
