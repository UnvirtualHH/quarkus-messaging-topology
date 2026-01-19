package de.prgrm.topology.runtime.web.model;

import java.util.*;
import java.util.stream.Collectors;

import de.prgrm.topology.runtime.model.TopologyInfo;

public class TopologyViewModel {
    private final List<TopologyWithChannels> topologies;
    private final Stats stats;
    private final List<String> failedServices;
    private final int discoveredServicesCount;

    public TopologyViewModel(List<TopologyInfo> topologies, int totalServices, List<String> failedServices) {
        this.topologies = topologies.stream()
                .map(TopologyWithChannels::new)
                .collect(Collectors.toList());
        this.failedServices = failedServices;
        this.discoveredServicesCount = totalServices;
        this.stats = new Stats(topologies);
    }

    public List<TopologyWithChannels> getTopologies() {
        return topologies;
    }

    public Stats getStats() {
        return stats;
    }

    public List<String> getFailedServices() {
        return failedServices;
    }

    public int getDiscoveredServicesCount() {
        return discoveredServicesCount;
    }

    public static class Stats {
        private final int servicesCount;
        private final int topicsCount;
        private final int producersCount;
        private final int consumersCount;

        public Stats(List<TopologyInfo> topologies) {
            this.servicesCount = topologies.size();

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

            this.topicsCount = uniqueTopics.size();
            this.producersCount = producers;
            this.consumersCount = consumers;
        }

        public int getServicesCount() {
            return servicesCount;
        }

        public int getTopicsCount() {
            return topicsCount;
        }

        public int getProducersCount() {
            return producersCount;
        }

        public int getConsumersCount() {
            return consumersCount;
        }
    }

    public static class TopologyWithChannels {
        private final TopologyInfo topology;
        private final List<ChannelViewModel> channels;

        public TopologyWithChannels(TopologyInfo topology) {
            this.topology = topology;
            this.channels = topology.getChannels().stream()
                    .map(ChannelViewModel::new)
                    .collect(Collectors.toList());
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
