package de.prgrm.topology.runtime.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.prgrm.topology.runtime.model.TopologyRegistry;
import io.vertx.core.Vertx;

@ApplicationScoped
public class MessageSender {

    @Inject
    Vertx vertx;

    @Inject
    ObjectMapper mapper;

    public void send(String channelName, Object payload) {
        var topology = TopologyRegistry.INSTANCE.getTopology();
        if (topology == null) {
            throw new IllegalStateException("Topology not initialized");
        }

        boolean channelExists = topology.getChannels().stream()
                .anyMatch(c -> c.getChannelName().equals(channelName) && "outgoing".equals(c.getDirection()));

        if (!channelExists) {
            throw new IllegalArgumentException("Channel not found or not outgoing: " + channelName);
        }

        String jsonPayload;
        if (payload instanceof String s) {
            jsonPayload = s;
        } else {
            try {
                jsonPayload = mapper.writeValueAsString(payload);
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException("Could not serialize payload: " + e.getMessage(), e);
            }
        }

        vertx.eventBus().publish(channelName, jsonPayload);
    }
}
