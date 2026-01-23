// runtime/src/main/java/de/prgrm/topology/runtime/service/ServiceRegistry.java
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

import com.fasterxml.jackson.databind.ObjectMapper;

import de.prgrm.topology.runtime.config.TopologyConfig;
import de.prgrm.topology.runtime.model.ChannelInfo;
import de.prgrm.topology.runtime.model.TopologyInfo;
import de.prgrm.topology.runtime.model.TopologyRegistry;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;

@ApplicationScoped
public class ServiceRegistry {

    @Inject
    TopologyConfig config;

    @Inject
    SchemaIntrospector schemaIntrospector;

    private final ObjectMapper mapper = new ObjectMapper();

    void onStart(@Observes StartupEvent event) {
        if (!config.enabled()) {
            System.out.println("🔇 Messaging Topology is disabled");
            return;
        }

        try {
            Files.createDirectories(Paths.get(config.directory()));

            if (config.autoSave()) {
                saveLocalTopology();
                System.out.println("📝 Topology saved to " + config.directory());
                if (config.projectName().isPresent()) {
                    System.out.println("   Project: " + config.projectName().get());
                }
            }
        } catch (IOException e) {
            System.err.println("⚠️ Warning: Could not create topology dir: " + e.getMessage());
        }
    }

    void onStop(@Observes ShutdownEvent event) {
        if (config.enabled()) {
            deleteLocalTopology();
        }
    }

    private void saveLocalTopology() {
        try {
            TopologyInfo topology = TopologyRegistry.INSTANCE.getTopology();
            if (topology == null) {
                return;
            }

            config.serviceName().ifPresent(topology::setServiceName);
            config.groupId().ifPresent(topology::setGroupId);

            if (config.projectName().isPresent()) {
                topology.setProjectName(config.projectName().get());
            }

            if (config.includeSchema()) {
                enrichWithSchema(topology);
            }

            String filename = topology.getServiceName() + ".json";
            Path file = Paths.get(config.directory(), filename);

            String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(topology);
            Files.writeString(file, json, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            saveServiceUrl(topology.getServiceName());
        } catch (IOException e) {
            System.err.println("⚠️ Warning: Could not save topology: " + e.getMessage());
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
                // Schema nicht verfügbar - kein Problem
            }
        }
    }

    private void saveServiceUrl(String serviceName) throws IOException {
        String port = System.getProperty("quarkus.http.port", "8080");
        String host = System.getProperty("quarkus.http.host", "localhost");
        String serviceUrl = "http://" + host + ":" + port;

        Path urlFile = Paths.get(config.directory(), serviceName + ".url");
        Files.writeString(urlFile, serviceUrl);
    }

    private void deleteLocalTopology() {
        try {
            TopologyInfo topology = TopologyRegistry.INSTANCE.getTopology();
            if (topology != null) {
                String serviceName = topology.getServiceName();
                Files.deleteIfExists(Paths.get(config.directory(), serviceName + ".json"));
                Files.deleteIfExists(Paths.get(config.directory(), serviceName + ".url"));
                System.out.println("🗑️ Topology files removed");
            }
        } catch (IOException e) {
            System.err.println("⚠️ Warning: Could not delete topology: " + e.getMessage());
        }
    }

    public List<TopologyInfo> getAllTopologies() {
        if (!config.enabled()) {
            return Collections.emptyList();
        }

        List<TopologyInfo> topologies = new ArrayList<>();

        try {
            Files.list(Paths.get(config.directory()))
                    .filter(p -> p.toString().endsWith(".json"))
                    .forEach(file -> {
                        try {
                            String json = Files.readString(file);
                            TopologyInfo topology = mapper.readValue(json, TopologyInfo.class);

                            if (config.projectName().isPresent()) {
                                if (config.projectName().get().equals(topology.getProjectName())) {
                                    loadTopology(topology, file, topologies);
                                }
                            } else {
                                loadTopology(topology, file, topologies);
                            }
                        } catch (Exception e) {
                            System.err.println("  ✗ Failed to load: " + file + " - " + e.getMessage());
                        }
                    });
        } catch (IOException e) {
            System.err.println("⚠️ Warning: Could not read topologies: " + e.getMessage());
        }

        return topologies;
    }

    private void loadTopology(TopologyInfo topology, Path file, List<TopologyInfo> topologies) throws IOException {
        String serviceName = file.getFileName().toString().replace(".json", "");
        Path urlFile = Paths.get(config.directory(), serviceName + ".url");
        if (Files.exists(urlFile)) {
            String serviceUrl = Files.readString(urlFile).trim();
            topology.setServiceUrl(serviceUrl);
        }

        topologies.add(topology);
        System.out.println("  ✓ Loaded: " + serviceName + " (" + topology.getChannels().size() + " channels)");
    }
}
