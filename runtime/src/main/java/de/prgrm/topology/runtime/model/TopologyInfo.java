package de.prgrm.topology.runtime.model;

import java.util.ArrayList;
import java.util.List;

public class TopologyInfo {
    private String serviceName;
    private String groupId;
    private String artifactId;
    private String version;
    private String serviceUrl;
    private List<ChannelInfo> channels = new ArrayList<>();

    public TopologyInfo() {
    }

    // Getters/Setters
    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public void setArtifactId(String artifactId) {
        this.artifactId = artifactId;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getServiceUrl() {
        return serviceUrl;
    }

    public void setServiceUrl(String serviceUrl) {
        this.serviceUrl = serviceUrl;
    }

    public List<ChannelInfo> getChannels() {
        return channels;
    }

    public void setChannels(List<ChannelInfo> channels) {
        this.channels = channels;
    }

    public void addChannel(ChannelInfo channel) {
        this.channels.add(channel);
    }
}