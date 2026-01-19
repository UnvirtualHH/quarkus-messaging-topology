package de.prgrm.topology.runtime.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;

@ApplicationScoped
public class ServiceRegistry {

    @ConfigProperty(name = "quarkus.http.port", defaultValue = "8080")
    int port;

    @ConfigProperty(name = "quarkus.http.host", defaultValue = "localhost")
    String host;

    @ConfigProperty(name = "topology.registry.file", defaultValue = "/tmp/messaging-topology-registry.txt")
    String registryFile;

    private String myServiceUrl;

    void onStart(@Observes StartupEvent event) {
        myServiceUrl = "http://" + host + ":" + port;
        registerService();
    }

    void onStop(@Observes ShutdownEvent event) {
        unregisterService();
    }

    private void registerService() {
        try {
            Path path = Paths.get(registryFile);

            List<String> services = new ArrayList<>();
            if (Files.exists(path)) {
                services = Files.readAllLines(path).stream()
                        .filter(line -> !line.trim().isEmpty())
                        .filter(line -> !line.equals(myServiceUrl))
                        .collect(Collectors.toList());
            }

            services.add(myServiceUrl);

            Files.write(path, services,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);

        } catch (IOException e) {
            System.err.println("Warning: Could not register service: " + e.getMessage());
        }
    }

    private void unregisterService() {
        try {
            Path path = Paths.get(registryFile);

            if (Files.exists(path)) {
                List<String> services = Files.readAllLines(path).stream()
                        .filter(line -> !line.trim().isEmpty())
                        .filter(line -> !line.equals(myServiceUrl))
                        .collect(Collectors.toList());

                Files.write(path, services,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING);
            }

        } catch (IOException e) {
            System.err.println("Warning: Could not unregister service: " + e.getMessage());
        }
    }

    public List<String> getRegisteredServices() {
        try {
            Path path = Paths.get(registryFile);

            if (Files.exists(path)) {
                return Files.readAllLines(path).stream()
                        .filter(line -> !line.trim().isEmpty())
                        .filter(line -> !line.equals(myServiceUrl))
                        .collect(Collectors.toList());
            }
        } catch (IOException e) {
            System.err.println("Warning: Could not read registry: " + e.getMessage());
        }

        return new ArrayList<>();
    }
}
