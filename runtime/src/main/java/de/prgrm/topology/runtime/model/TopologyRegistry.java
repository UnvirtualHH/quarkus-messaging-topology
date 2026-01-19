package de.prgrm.topology.runtime.model;

public class TopologyRegistry {
    public static final TopologyRegistry INSTANCE = new TopologyRegistry();

    private TopologyInfo topology;

    private TopologyRegistry() {
    }

    public void setTopology(TopologyInfo topology) {
        this.topology = topology;
    }

    public TopologyInfo getTopology() {
        return topology;
    }
}