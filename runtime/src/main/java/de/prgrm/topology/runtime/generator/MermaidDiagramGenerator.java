package de.prgrm.topology.runtime.generator;

import java.util.*;

import de.prgrm.topology.runtime.model.ChannelInfo;
import de.prgrm.topology.runtime.model.TopologyInfo;

/**
 * Renders the topology as Mermaid flowchart. Colors are not part of the diagram source: the Dev UI page styles the
 * node classes ({@code serviceClass}, {@code localServiceClass}, {@code topicClass}, {@code hotTopicClass}) with
 * the current Dev UI theme.
 */
public class MermaidDiagramGenerator {

    static final int HOT_TOPIC_CONNECTIONS = 4;

    public String generate(List<TopologyInfo> topologies) {
        return generate(topologies, null);
    }

    public String generate(List<TopologyInfo> topologies, String localServiceName) {
        StringBuilder mermaid = new StringBuilder();
        mermaid.append("graph LR\n");

        Map<String, Set<Connection>> topicConnections = collectConnections(topologies);

        renderServices(mermaid, topologies);
        renderTopics(mermaid, topicConnections);
        renderConnections(mermaid, topicConnections);
        renderClasses(mermaid, topologies, topicConnections, localServiceName);

        return mermaid.toString();
    }

    /**
     * Maps every Mermaid node id of the diagram to the service or topic it represents.
     */
    public Map<String, Node> nodes(List<TopologyInfo> topologies) {
        Map<String, Node> nodes = new LinkedHashMap<>();
        for (TopologyInfo topology : topologies) {
            nodes.put(serviceNodeId(topology.getServiceName()), new Node("service", topology.getServiceName()));
        }
        for (String topic : collectConnections(topologies).keySet()) {
            nodes.put(topicNodeId(topic), new Node("topic", topic));
        }
        return nodes;
    }

    public static String serviceNodeId(String serviceName) {
        return "svc_" + sanitize(serviceName);
    }

    public static String topicNodeId(String topic) {
        return "topic_" + sanitize(topic);
    }

    public static String topicOf(ChannelInfo channel) {
        return channel.getTopic() != null ? channel.getTopic() : channel.getChannelName();
    }

    private Map<String, Set<Connection>> collectConnections(List<TopologyInfo> topologies) {
        Map<String, Set<Connection>> topicConnections = new TreeMap<>();

        for (TopologyInfo topology : topologies) {
            for (ChannelInfo channel : topology.getChannels()) {
                topicConnections.computeIfAbsent(topicOf(channel), k -> new LinkedHashSet<>())
                        .add(new Connection(topology.getServiceName(), channel.getMethodName(), channel.getDirection()));
            }
        }

        return topicConnections;
    }

    private void renderServices(StringBuilder mermaid, List<TopologyInfo> topologies) {
        mermaid.append("\n    %% Services\n");
        for (TopologyInfo topology : topologies) {
            mermaid.append("    ").append(serviceNodeId(topology.getServiceName()))
                    .append("(\"").append(label(topology.getServiceName())).append("\")\n");
        }
    }

    private void renderTopics(StringBuilder mermaid, Map<String, Set<Connection>> topicConnections) {
        mermaid.append("\n    %% Topics\n");
        for (String topic : topicConnections.keySet()) {
            mermaid.append("    ").append(topicNodeId(topic))
                    .append("[(\"").append(label(topic)).append("\")]\n");
        }
    }

    private void renderConnections(StringBuilder mermaid, Map<String, Set<Connection>> topicConnections) {
        mermaid.append("\n    %% Connections\n");
        for (Map.Entry<String, Set<Connection>> entry : topicConnections.entrySet()) {
            String topicId = topicNodeId(entry.getKey());

            for (Connection conn : entry.getValue()) {
                String serviceId = serviceNodeId(conn.serviceName());
                String edgeLabel = " -->|\"" + label(conn.method()) + "\"| ";

                if ("outgoing".equals(conn.direction())) {
                    mermaid.append("    ").append(serviceId).append(edgeLabel).append(topicId).append("\n");
                } else {
                    mermaid.append("    ").append(topicId).append(edgeLabel).append(serviceId).append("\n");
                }
            }
        }
    }

    private void renderClasses(StringBuilder mermaid, List<TopologyInfo> topologies,
            Map<String, Set<Connection>> topicConnections, String localServiceName) {
        mermaid.append("\n    %% Classes\n");

        for (TopologyInfo topology : topologies) {
            String cssClass = topology.getServiceName().equals(localServiceName) ? "localServiceClass" : "serviceClass";
            mermaid.append("    class ").append(serviceNodeId(topology.getServiceName()))
                    .append(" ").append(cssClass).append("\n");
        }

        for (Map.Entry<String, Set<Connection>> entry : topicConnections.entrySet()) {
            String cssClass = entry.getValue().size() >= HOT_TOPIC_CONNECTIONS ? "hotTopicClass" : "topicClass";
            mermaid.append("    class ").append(topicNodeId(entry.getKey()))
                    .append(" ").append(cssClass).append("\n");
        }
    }

    private static String sanitize(String id) {
        return id.replaceAll("[^a-zA-Z0-9_]", "_");
    }

    /**
     * Escapes characters that would break a quoted Mermaid label or be interpreted as markup.
     */
    private static String label(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "#amp;")
                .replace("\"", "#quot;")
                .replace("<", "#lt;")
                .replace(">", "#gt;");
    }

    public record Node(String kind, String name) {
    }

    private record Connection(String serviceName, String method, String direction) {
    }
}
