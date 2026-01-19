package de.prgrm.topology.runtime.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.prgrm.topology.runtime.model.TopologyRegistry;
import io.vertx.core.Vertx;

@ApplicationScoped
public class MessageSender {

    @Inject
    Vertx vertx;

    private final ObjectMapper mapper = new ObjectMapper();

    public void send(String channelName, Object payload) throws Exception {
        var topology = TopologyRegistry.INSTANCE.getTopology();
        if (topology == null) {
            throw new Exception("Topology not initialized");
        }

        boolean channelExists = topology.getChannels().stream()
                .anyMatch(c -> c.getChannelName().equals(channelName) && "outgoing".equals(c.getDirection()));

        if (!channelExists) {
            throw new Exception("Channel not found or not outgoing: " + channelName);
        }

        String jsonPayload = payload instanceof String
                ? (String) payload
                : mapper.writeValueAsString(payload);

        vertx.eventBus().publish(channelName, jsonPayload);
    }
}
