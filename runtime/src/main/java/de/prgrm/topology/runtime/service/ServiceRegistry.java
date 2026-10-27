package de.prgrm.topology.runtime.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.Config;
import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.prgrm.topology.runtime.config.TopologyConfig;
import de.prgrm.topology.runtime.model.ChannelInfo;
import de.prgrm.topology.runtime.model.TopologyInfo;
import de.prgrm.topology.runtime.model.TopologyRegistry;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;

@ApplicationScoped
public class ServiceRegistry {

    private static final Logger LOG = Logger.getLogger(ServiceRegistry.class);

    @Inject
    TopologyConfig topologyConfig;

    @Inject
    Config config;

    @Inject
    SchemaIntrospector schemaIntrospector;

    @Inject
    ObjectMapper mapper;

    void onStart(@Observes StartupEvent event) {
        if (!topologyConfig.enabled()) {
            LOG.info("Messaging Topology is disabled");
            return;
        }

        try {
            Files.createDirectories(Paths.get(topologyConfig.directory()));

            TopologyInfo topology = TopologyRegistry.INSTANCE.getTopology();
            if (topology != null) {
                resolveChannelConfig(topology);
                enrichWithSchema(topology);
                LOG.info("Topology resolved and enriched with schemas");
            }

            if (topologyConfig.autoSave()) {
                saveLocalTopology();
                LOG.infof("Topology saved to %s", topologyConfig.directory());
                topologyConfig.projectName().ifPresent(name -> LOG.infof("Project: %s", name));
            }
        } catch (IOException e) {
            LOG.warnf("Could not create topology dir: %s", e.getMessage());
        }
    }

    void onStop(@Observes ShutdownEvent event) {
        if (topologyConfig.enabled() && topologyConfig.cleanupOnShutdown()) {
            deleteLocalTopology();
        }
    }

    private void resolveChannelConfig(TopologyInfo topology) {
        for (ChannelInfo channel : topology.getChannels()) {
            String channelName = channel.getChannelName();
            String direction = channel.getDirection();

            String topicKey = "mp.messaging." + direction + "." + channelName + ".topic";
            String connectorKey = "mp.messaging." + direction + "." + channelName + ".connector";

            Optional<String> topic = config.getOptionalValue(topicKey, String.class);
            channel.setTopic(topic.orElse(channelName));

            Optional<String> connector = config.getOptionalValue(connectorKey, String.class);
            channel.setConnector(connector.orElse(null));
        }
    }

    private void saveLocalTopology() {
        try {
            TopologyInfo topology = TopologyRegistry.INSTANCE.getTopology();
            if (topology == null) {
                return;
            }

            topologyConfig.serviceName().ifPresent(topology::setServiceName);
            topologyConfig.groupId().ifPresent(topology::setGroupId);
            topologyConfig.projectName().ifPresent(topology::setProjectName);

            String filename = topology.getServiceName() + ".json";
            Path file = Paths.get(topologyConfig.directory(), filename);

            String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(topology);
            Files.writeString(file, json, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            saveServiceUrl(topology.getServiceName());
        } catch (IOException e) {
            LOG.warnf("Could not save topology: %s", e.getMessage());
        }
    }

    private void enrichWithSchema(TopologyInfo topology) {
        for (ChannelInfo channel : topology.getChannels()) {
            try {
                Map<String, Object> schema = schemaIntrospector.getSchema(
                        channel.getChannelName(),
                        channel.getDirection());
                channel.setSchema(schema);

                Map<String, Object> example = schemaIntrospector.getExamplePayload(
                        channel.getChannelName(),
                        channel.getDirection());
                channel.setExamplePayload(example);
            } catch (Exception e) {
                LOG.debugf("Schema not available for channel %s: %s", channel.getChannelName(), e.getMessage());
            }
        }
    }

    private void saveServiceUrl(String serviceName) throws IOException {
        String port = System.getProperty("quarkus.http.port", "8080");
        String host = System.getProperty("quarkus.http.host", "localhost");
        String serviceUrl = "http://" + host + ":" + port;

        Path urlFile = Paths.get(topologyConfig.directory(), serviceName + ".url");
        Files.writeString(urlFile, serviceUrl);
    }

    private void deleteLocalTopology() {
        try {
            TopologyInfo topology = TopologyRegistry.INSTANCE.getTopology();
            if (topology != null) {
                String serviceName = topology.getServiceName();
                Files.deleteIfExists(Paths.get(topologyConfig.directory(), serviceName + ".json"));
                Files.deleteIfExists(Paths.get(topologyConfig.directory(), serviceName + ".url"));
                LOG.info("Topology files removed");
            }
        } catch (IOException e) {
            LOG.warnf("Could not delete topology: %s", e.getMessage());
        }
    }

    public List<TopologyInfo> getAllTopologies() {
        if (!topologyConfig.enabled()) {
            return Collections.emptyList();
        }

        List<TopologyInfo> topologies = new ArrayList<>();

        try (var files = Files.list(Paths.get(topologyConfig.directory()))) {
            files.filter(p -> p.toString().endsWith(".json"))
                    .forEach(file -> {
                        try {
                            String json = Files.readString(file);
                            TopologyInfo topology = mapper.readValue(json, TopologyInfo.class);

                            if (topologyConfig.projectName().isPresent()) {
                                if (topologyConfig.projectName().get().equals(topology.getProjectName())) {
                                    loadTopology(topology, file, topologies);
                                }
                            } else {
                                loadTopology(topology, file, topologies);
                            }
                        } catch (Exception e) {
                            LOG.warnf("Failed to load: %s - %s", file, e.getMessage());
                        }
                    });
        } catch (IOException e) {
            LOG.warnf("Could not read topologies: %s", e.getMessage());
        }

        return topologies;
    }

    private void loadTopology(TopologyInfo topology, Path file, List<TopologyInfo> topologies) throws IOException {
        String serviceName = file.getFileName().toString().replace(".json", "");
        Path urlFile = Paths.get(topologyConfig.directory(), serviceName + ".url");
        if (Files.exists(urlFile)) {
            String serviceUrl = Files.readString(urlFile).trim();
            topology.setServiceUrl(serviceUrl);
        }

        topologies.add(topology);
        LOG.debugf("Loaded: %s (%d channels)", serviceName, topology.getChannels().size());
    }
}
