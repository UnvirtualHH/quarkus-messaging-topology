package de.prgrm.topology.runtime.model;

import java.util.Map;

public class ChannelInfo {
    private String channelName;
    private String direction; // incoming/outgoing
    private String className;
    private String methodName;
    private String topic;
    private String connector;

    private Map<String, Object> schema;
    private Map<String, Object> examplePayload;

    // Alle Getter/Setter + neue:
    public Map<String, Object> getSchema() {
        return schema;
    }

    public void setSchema(Map<String, Object> schema) {
        this.schema = schema;
    }

    public Map<String, Object> getExamplePayload() {
        return examplePayload;
    }

    public void setExamplePayload(Map<String, Object> examplePayload) {
        this.examplePayload = examplePayload;
    }

    public ChannelInfo() {
    }

    public ChannelInfo(String channelName, String direction, String className, String methodName) {
        this.channelName = channelName;
        this.direction = direction;
        this.className = className;
        this.methodName = methodName;
    }

    // Getters/Setters
    public String getChannelName() {
        return channelName;
    }

    public void setChannelName(String channelName) {
        this.channelName = channelName;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getConnector() {
        return connector;
    }

    public void setConnector(String connector) {
        this.connector = connector;
    }
}