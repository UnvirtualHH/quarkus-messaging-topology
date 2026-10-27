package de.prgrm.topology.runtime.web;

import java.util.Map;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.jboss.logging.Logger;

import de.prgrm.topology.runtime.model.TopologyInfo;
import de.prgrm.topology.runtime.model.TopologyRegistry;
import de.prgrm.topology.runtime.service.MessageSender;

@Path("/q/messaging-topology")
public class TopologyApiController {

    private static final Logger LOG = Logger.getLogger(TopologyApiController.class);

    @Inject
    MessageSender messageSender;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public TopologyInfo getTopology() {
        return TopologyRegistry.INSTANCE.getTopology();
    }

    @POST
    @Path("/send")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response sendMessage(Map<String, Object> request) {
        String channel = (String) request.get("channel");
        String topic = (String) request.get("topic");
        Object payload = request.get("payload");

        if (channel == null || payload == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Missing channel or payload"))
                    .build();
        }

        try {
            messageSender.send(channel, payload);
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        } catch (IllegalStateException e) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        }

        return Response.ok(Map.of(
                "success", true,
                "message", "Message sent to " + channel,
                "channel", channel,
                "topic", topic != null ? topic : channel)).build();
    }
}
