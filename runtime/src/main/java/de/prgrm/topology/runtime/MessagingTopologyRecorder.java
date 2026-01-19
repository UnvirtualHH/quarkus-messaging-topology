package de.prgrm.topology.runtime;

import de.prgrm.topology.runtime.model.TopologyInfo;
import de.prgrm.topology.runtime.model.TopologyRegistry;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class MessagingTopologyRecorder {

    public void registerTopology(TopologyInfo topologyInfo) {
        TopologyRegistry.INSTANCE.setTopology(topologyInfo);
    }
}