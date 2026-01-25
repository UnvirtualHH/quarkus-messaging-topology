package de.prgrm.topology.deployment;

import io.quarkus.deployment.IsDevelopment;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.devui.spi.page.CardPageBuildItem;
import io.quarkus.devui.spi.page.Page;

public class MessagingTopologyDevUIProcessor {

    @BuildStep(onlyIf = IsDevelopment.class)
    CardPageBuildItem createCard() {
        CardPageBuildItem card = new CardPageBuildItem();

        card.addPage(Page.externalPageBuilder("Topology Viewer")
                .icon("font-awesome-solid:diagram-project")
                .url("/q/messaging-topology-viewer")
                .isHtmlContent());

        card.addPage(Page.externalPageBuilder("Topology API")
                .icon("font-awesome-solid:code")
                .url("/q/messaging-topology")
                .isJsonContent());

        return card;
    }
}