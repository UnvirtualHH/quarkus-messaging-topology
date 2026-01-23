package de.prgrm.topology.runtime.web;

import java.util.Map;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import de.prgrm.topology.runtime.model.TopologyInfo;
import de.prgrm.topology.runtime.model.TopologyRegistry;
import de.prgrm.topology.runtime.service.MessageSender;
import de.prgrm.topology.runtime.service.SchemaIntrospector;
import de.prgrm.topology.runtime.service.ServiceRegistry;

@Path("/q/messaging-topology")
public class TopologyApiController {

    @Inject
    SchemaIntrospector schemaIntrospector;

    @Inject
    MessageSender messageSender;

    @Inject
    ServiceRegistry serviceRegistry;

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

        System.out.println("📨 Send endpoint called");

        try {
            String channel = (String) request.get("channel");
            String topic = (String) request.get("topic");
            Object payload = request.get("payload");

            if (channel == null || payload == null) {
                return Response.status(400)
                        .entity(Map.of("error", "Missing channel or payload"))
                        .build();
            }

            messageSender.send(channel, payload);

            return Response.ok(Map.of(
                    "success", true,
                    "message", "Message sent to " + channel,
                    "channel", channel,
                    "topic", topic != null ? topic : channel)).build();
        } catch (Exception e) {
            return Response.status(500)
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        }
    }
}
