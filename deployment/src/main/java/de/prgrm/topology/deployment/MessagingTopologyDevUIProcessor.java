package de.prgrm.topology.deployment;

import io.quarkus.deployment.IsDevelopment;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.devui.spi.page.CardPageBuildItem;
import io.quarkus.devui.spi.page.Page;

public class MessagingTopologyDevUIProcessor {

    @BuildStep(onlyIf = IsDevelopment.class)
    CardPageBuildItem createCard() {
        CardPageBuildItem card = new CardPageBuildItem();

        card.addBuildTimeData("localTopology", "/META-INF/resources/q/messaging-topology");

        card.addPage(Page.webComponentPageBuilder()
                .title("Messaging Topology")
                .icon("font-awesome-solid:diagram-project")
                .componentLink("qwc-messaging-topology.js"));

        return card;
    }
}