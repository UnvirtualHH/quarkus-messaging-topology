package de.prgrm.topology.runtime.web.model;

import java.util.*;

import de.prgrm.topology.runtime.model.TopologyInfo;

public class TopologyViewModel {
    private final List<TopologyWithChannels> topologies;
    private final Stats stats;

    public TopologyViewModel(List<TopologyInfo> topologies) {
        this.topologies = topologies.stream()
                .map(TopologyWithChannels::new)
                .toList();
        this.stats = Stats.from(topologies);
    }

    public List<TopologyWithChannels> getTopologies() {
        return topologies;
    }

    public Stats getStats() {
        return stats;
    }

    public record Stats(int servicesCount, int topicsCount, int producersCount, int consumersCount) {

        static Stats from(List<TopologyInfo> topologies) {
            Set<String> uniqueTopics = new HashSet<>();
            int producers = 0;
            int consumers = 0;

            for (TopologyInfo topology : topologies) {
                for (var channel : topology.getChannels()) {
                    String topic = channel.getTopic() != null ? channel.getTopic() : channel.getChannelName();
                    uniqueTopics.add(topic);

                    if ("outgoing".equals(channel.getDirection())) {
                        producers++;
                    } else if ("incoming".equals(channel.getDirection())) {
                        consumers++;
                    }
                }
            }

            return new Stats(topologies.size(), uniqueTopics.size(), producers, consumers);
        }
    }

    public static class TopologyWithChannels {
        private final TopologyInfo topology;
        private final List<ChannelViewModel> channels;

        public TopologyWithChannels(TopologyInfo topology) {
            this.topology = topology;
            this.channels = topology.getChannels().stream()
                    .map(ChannelViewModel::new)
                    .toList();
        }

        public String getServiceName() {
            return topology.getServiceName();
        }

        public String getVersion() {
            return topology.getVersion();
        }

        public List<ChannelViewModel> getChannels() {
            return channels;
        }
    }
}
