package de.prgrm.topology.runtime.generator;

import java.util.*;

import de.prgrm.topology.runtime.model.ChannelInfo;
import de.prgrm.topology.runtime.model.TopologyInfo;

public class MermaidDiagramGenerator {

    public String generate(List<TopologyInfo> topologies) {
        StringBuilder mermaid = new StringBuilder();
        mermaid.append("graph LR\n");

        Map<String, Set<Connection>> topicConnections = collectConnections(topologies);

        renderServices(mermaid, topologies);
        renderTopics(mermaid, topicConnections);
        renderConnections(mermaid, topicConnections);
        renderStyles(mermaid, topologies, topicConnections);

        return mermaid.toString();
    }

    private Map<String, Set<Connection>> collectConnections(List<TopologyInfo> topologies) {
        Map<String, Set<Connection>> topicConnections = new HashMap<>();

        for (TopologyInfo topology : topologies) {
            for (ChannelInfo channel : topology.getChannels()) {
                String topic = channel.getTopic() != null ? channel.getTopic() : channel.getChannelName();

                topicConnections.computeIfAbsent(topic, k -> new HashSet<>())
                        .add(new Connection(topology.getServiceName(), channel.getMethodName(), channel.getDirection()));
            }
        }

        return topicConnections;
    }

    private void renderServices(StringBuilder mermaid, List<TopologyInfo> topologies) {
        mermaid.append("\n    %% Services\n");
        for (TopologyInfo topology : topologies) {
            String serviceId = sanitize(topology.getServiceName());
            mermaid.append("    ").append(serviceId)
                    .append("[\"📦 ").append(topology.getServiceName()).append("\"]\n");
        }
    }

    private void renderTopics(StringBuilder mermaid, Map<String, Set<Connection>> topicConnections) {
        mermaid.append("\n    %% Topics\n");
        for (Map.Entry<String, Set<Connection>> entry : topicConnections.entrySet()) {
            String topic = entry.getKey();
            String topicId = sanitize("topic_" + topic);
            Set<Connection> connections = entry.getValue();

            long producers = connections.stream().filter(c -> "outgoing".equals(c.direction)).count();
            long consumers = connections.stream().filter(c -> "incoming".equals(c.direction)).count();

            mermaid.append("    ").append(topicId)
                    .append("((\"💬 ").append(topic)
                    .append("<br/><small>P:").append(producers)
                    .append(" C:").append(consumers).append("</small>\"))\n");
        }
    }

    private void renderConnections(StringBuilder mermaid, Map<String, Set<Connection>> topicConnections) {
        mermaid.append("\n    %% Connections\n");
        for (Map.Entry<String, Set<Connection>> entry : topicConnections.entrySet()) {
            String topicId = sanitize("topic_" + entry.getKey());

            for (Connection conn : entry.getValue()) {
                String serviceId = sanitize(conn.serviceName);

                if ("outgoing".equals(conn.direction)) {
                    mermaid.append("    ").append(serviceId)
                            .append(" -->|\"").append(conn.method).append("\"| ")
                            .append(topicId).append("\n");
                } else {
                    mermaid.append("    ").append(topicId)
                            .append(" -->|\"").append(conn.method).append("\"| ")
                            .append(serviceId).append("\n");
                }
            }
        }
    }

    private void renderStyles(StringBuilder mermaid, List<TopologyInfo> topologies,
            Map<String, Set<Connection>> topicConnections) {
        mermaid.append("\n    %% Styling\n");
        mermaid.append("    classDef serviceClass fill:#4A90E2,stroke:#2E5C8A,stroke-width:2px,color:#fff\n");
        mermaid.append("    classDef topicClass fill:#F5A623,stroke:#D68910,stroke-width:2px,color:#fff\n");
        mermaid.append("    classDef hotTopicClass fill:#E74C3C,stroke:#C0392B,stroke-width:3px,color:#fff\n");

        for (TopologyInfo topology : topologies) {
            mermaid.append("    class ").append(sanitize(topology.getServiceName()))
                    .append(" serviceClass\n");
        }

        for (Map.Entry<String, Set<Connection>> entry : topicConnections.entrySet()) {
            String topicId = sanitize("topic_" + entry.getKey());
            int totalConnections = entry.getValue().size();

            if (totalConnections >= 4) {
                mermaid.append("    class ").append(topicId).append(" hotTopicClass\n");
            } else {
                mermaid.append("    class ").append(topicId).append(" topicClass\n");
            }
        }
    }

    private String sanitize(String id) {
        return id.replaceAll("[^a-zA-Z0-9_]", "_");
    }

    private static class Connection {
        final String serviceName;
        final String method;
        final String direction;

        Connection(String serviceName, String method, String direction) {
            this.serviceName = serviceName;
            this.method = method;
            this.direction = direction;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (!(o instanceof Connection))
                return false;
            Connection that = (Connection) o;
            return Objects.equals(serviceName, that.serviceName) &&
                    Objects.equals(method, that.method) &&
                    Objects.equals(direction, that.direction);
        }

        @Override
        public int hashCode() {
            return Objects.hash(serviceName, method, direction);
        }
    }
}
