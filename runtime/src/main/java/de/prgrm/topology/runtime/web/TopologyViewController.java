package de.prgrm.topology.runtime.web;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.prgrm.topology.runtime.generator.MermaidDiagramGenerator;
import de.prgrm.topology.runtime.model.TopologyInfo;
import de.prgrm.topology.runtime.model.TopologyRegistry;
import de.prgrm.topology.runtime.service.ServiceRegistry;
import de.prgrm.topology.runtime.web.model.TopologyViewModel;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;

@Path("/q/messaging-topology-viewer")
public class TopologyViewController {

    @Inject
    ServiceRegistry serviceRegistry;

    @Inject
    MermaidDiagramGenerator mermaidGenerator;

    private final ObjectMapper mapper = new ObjectMapper();

    @CheckedTemplate
    public static class Templates {
        public static native TemplateInstance topologyViewer(TopologyViewModel model, String mermaidDiagram);
    }

    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance viewer(@QueryParam("auto") @DefaultValue("true") boolean autoDiscover) {

        TopologyInfo localTopology = getLocalTopology();
        List<TopologyInfo> allTopologies = new ArrayList<>();
        allTopologies.add(localTopology);

        List<String> serviceUrls = new ArrayList<>();
        List<String> failedServices = new ArrayList<>();

        if (autoDiscover) {
            serviceUrls = serviceRegistry.getRegisteredServices();

            loadRemoteTopologies(serviceUrls, allTopologies, failedServices);
        }

        TopologyViewModel model = new TopologyViewModel(
                allTopologies,
                serviceUrls.size(),
                failedServices);

        String mermaidDiagram = mermaidGenerator.generate(allTopologies);

        return Templates.topologyViewer(model, mermaidDiagram);
    }

    private TopologyInfo getLocalTopology() {
        TopologyInfo localTopology = TopologyRegistry.INSTANCE.getTopology();
        if (localTopology == null) {
            localTopology = new TopologyInfo();
            localTopology.setServiceName("Unknown");
        }
        return localTopology;
    }

    private void loadRemoteTopologies(List<String> serviceUrls,
            List<TopologyInfo> allTopologies,
            List<String> failedServices) {
        if (serviceUrls.isEmpty())
            return;

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();

        for (String serviceUrl : serviceUrls) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(serviceUrl + "/q/messaging-topology"))
                        .timeout(Duration.ofSeconds(2))
                        .GET()
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    TopologyInfo remoteTopology = mapper.readValue(response.body(), TopologyInfo.class);
                    allTopologies.add(remoteTopology);
                } else {
                    failedServices.add(serviceUrl + " (HTTP " + response.statusCode() + ")");
                }
            } catch (Exception e) {
                failedServices.add(serviceUrl + " (" + e.getMessage() + ")");
            }
        }
    }
}
