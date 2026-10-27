package de.prgrm.topology.deployment.app;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Incoming;

@ApplicationScoped
@Path("/received")
public class GreetingResource {

    private final List<Greeting> received = new CopyOnWriteArrayList<>();

    @Channel("greetings")
    Emitter<Greeting> emitter;

    @Incoming("greetings")
    void consume(Greeting greeting) {
        received.add(greeting);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<Greeting> received() {
        return received;
    }
}
