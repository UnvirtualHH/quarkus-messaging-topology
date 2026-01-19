package de.prgrm.topology.runtime.web.model;

import de.prgrm.topology.runtime.model.ChannelInfo;

public class ChannelViewModel {
    private final ChannelInfo channel;
    private final String simpleClassName;
    private final String topic;

    public ChannelViewModel(ChannelInfo channel) {
        this.channel = channel;
        this.simpleClassName = extractSimpleClassName(channel.getClassName());
        this.topic = channel.getTopic() != null ? channel.getTopic() : channel.getChannelName();
    }

    private String extractSimpleClassName(String fullClassName) {
        if (fullClassName == null)
            return "";
        int lastDot = fullClassName.lastIndexOf('.');
        return lastDot >= 0 ? fullClassName.substring(lastDot + 1) : fullClassName;
    }

    // Alle Properties für Templates
    public String getChannelName() {
        return channel.getChannelName();
    }

    public String getDirection() {
        return channel.getDirection();
    }

    public String getMethodName() {
        return channel.getMethodName();
    }

    public String getSimpleClassName() {
        return simpleClassName;
    }

    public String getTopic() {
        return topic;
    }

    public ChannelInfo getChannel() {
        return channel;
    }
}