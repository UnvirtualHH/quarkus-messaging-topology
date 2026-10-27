package de.prgrm.topology.runtime.devui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.prgrm.topology.runtime.config.TopologyConfig;
import de.prgrm.topology.runtime.generator.MermaidDiagramGenerator;
import de.prgrm.topology.runtime.model.TopologyInfo;
import de.prgrm.topology.runtime.model.TopologyRegistry;
import de.prgrm.topology.runtime.service.MessageSender;
import de.prgrm.topology.runtime.service.ServiceRegistry;

/**
 * Backend of the Dev UI page. Registered by the deployment module as JSON-RPC provider (dev mode only).
 */
public class MessagingTopologyJsonRpcService {

    @Inject
    TopologyConfig topologyConfig;

    @Inject
    ServiceRegistry serviceRegistry;

    @Inject
    MermaidDiagramGenerator mermaidGenerator;

    @Inject
    MessageSender messageSender;

    @Inject
    ObjectMapper mapper;

    public TopologyView getTopology() {
        if (!topologyConfig.enabled()) {
            return new TopologyView(false, null, List.of(), "", Map.of());
        }

        TopologyInfo local = TopologyRegistry.INSTANCE.getTopology();
        String localServiceName = local != null ? local.getServiceName() : null;

        List<TopologyInfo> topologies = new ArrayList<>(serviceRegistry.getAllTopologies());
        topologies.removeIf(t -> t.getServiceName() == null || t.getServiceName().equals(localServiceName));
        topologies.sort(Comparator.comparing(TopologyInfo::getServiceName));
        if (local != null) {
            // The in-memory topology is always current, even if auto-save is disabled
            topologies.add(0, local);
        }

        return new TopologyView(true, localServiceName, topologies,
                mermaidGenerator.generate(topologies, localServiceName),
                mermaidGenerator.nodes(topologies));
    }

    public String getServiceCount() {
        return String.valueOf(getTopology().services().size());
    }

    public Map<String, Object> sendMessage(String channel, String payload) {
        if (!topologyConfig.enabled()) {
            return failure("Messaging Topology is disabled");
        }

        Object parsedPayload;
        try {
            parsedPayload = mapper.readValue(payload, Object.class);
        } catch (JsonProcessingException e) {
            return failure("Invalid JSON: " + e.getOriginalMessage());
        }
        if (parsedPayload == null) {
            return failure("Payload must not be null");
        }

        try {
            messageSender.send(channel, parsedPayload);
            return Map.of("success", true, "message", "Message sent to " + channel);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return failure(e.getMessage());
        }
    }

    private static Map<String, Object> failure(String message) {
        return Map.of("success", false, "message", message);
    }

    public record TopologyView(boolean enabled, String localService, List<TopologyInfo> services, String diagram,
            Map<String, MermaidDiagramGenerator.Node> nodes) {
    }
}
