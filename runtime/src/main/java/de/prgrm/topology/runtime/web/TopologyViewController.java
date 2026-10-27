package de.prgrm.topology.runtime.web;

import java.util.*;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import org.jboss.logging.Logger;

import de.prgrm.topology.runtime.generator.MermaidDiagramGenerator;
import de.prgrm.topology.runtime.model.TopologyInfo;
import de.prgrm.topology.runtime.service.ServiceRegistry;
import de.prgrm.topology.runtime.web.model.TopologyViewModel;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;

@Path("/q/messaging-topology-viewer")
public class TopologyViewController {

    private static final Logger LOG = Logger.getLogger(TopologyViewController.class);

    @Inject
    ServiceRegistry serviceRegistry;

    @Inject
    MermaidDiagramGenerator mermaidGenerator;

    @CheckedTemplate
    public static class Templates {
        public static native TemplateInstance topologyViewer(TopologyViewModel model, String mermaidDiagram);
    }

    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance viewer() {
        List<TopologyInfo> allTopologies = serviceRegistry.getAllTopologies();

        LOG.debugf("Loaded %d topologies from files", allTopologies.size());

        TopologyViewModel model = new TopologyViewModel(allTopologies);
        String mermaidDiagram = mermaidGenerator.generate(allTopologies);
        return Templates.topologyViewer(model, mermaidDiagram);
    }
}
