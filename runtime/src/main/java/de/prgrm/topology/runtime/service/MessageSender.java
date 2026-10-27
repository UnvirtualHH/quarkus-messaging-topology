package de.prgrm.topology.runtime.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.jboss.logging.Logger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.prgrm.topology.runtime.model.ChannelInfo;
import de.prgrm.topology.runtime.model.TopologyRegistry;
import io.smallrye.reactive.messaging.ChannelRegistry;
import io.smallrye.reactive.messaging.MutinyEmitter;

/**
 * Sends test messages through the application's own {@link Emitter} / {@link MutinyEmitter} of a channel, so the
 * message takes the same path (connector, topic, serialization) as messages sent by the application itself.
 */
@ApplicationScoped
public class MessageSender {

    private static final Logger LOG = Logger.getLogger(MessageSender.class);

    @Inject
    ChannelRegistry channelRegistry;

    @Inject
    ObjectMapper mapper;

    public void send(String channelName, Object payload) {
        var topology = TopologyRegistry.INSTANCE.getTopology();
        if (topology == null) {
            throw new IllegalStateException("Topology not initialized");
        }

        ChannelInfo channel = topology.getChannels().stream()
                .filter(c -> c.getChannelName().equals(channelName) && c.isSendable())
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Channel not found or not backed by an Emitter: " + channelName));

        Object message = toMessage(payload, channel.getMessageType());

        MutinyEmitter<Object> mutinyEmitter = channelRegistry.getEmitter(channelName, MutinyEmitter.class);
        if (mutinyEmitter != null) {
            mutinyEmitter.send(message).subscribe().with(
                    ignored -> LOG.debugf("Test message acknowledged on channel %s", channelName),
                    failure -> LOG.warnf("Test message on channel %s was not acknowledged: %s", channelName,
                            failure.getMessage()));
            return;
        }

        Emitter<Object> emitter = channelRegistry.getEmitter(channelName, Emitter.class);
        if (emitter == null) {
            throw new IllegalStateException("No Emitter registered for channel: " + channelName);
        }
        emitter.send(message).whenComplete((ignored, failure) -> {
            if (failure != null) {
                LOG.warnf("Test message on channel %s was not acknowledged: %s", channelName, failure.getMessage());
            }
        });
    }

    private Object toMessage(Object payload, String messageType) {
        if (messageType == null) {
            return payload;
        }

        Class<?> type;
        try {
            type = Thread.currentThread().getContextClassLoader().loadClass(messageType);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Message type not loadable: " + messageType, e);
        }

        if (type == String.class) {
            if (payload instanceof String s) {
                return s;
            }
            try {
                return mapper.writeValueAsString(payload);
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException("Could not serialize payload: " + e.getMessage(), e);
            }
        }

        try {
            return mapper.convertValue(payload, type);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Payload does not match " + type.getSimpleName() + ": " + e.getMessage(), e);
        }
    }
}
