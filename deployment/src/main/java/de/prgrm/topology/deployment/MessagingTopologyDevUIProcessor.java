package de.prgrm.topology.deployment;

import de.prgrm.topology.runtime.devui.MessagingTopologyJsonRpcService;
import io.quarkus.deployment.IsDevelopment;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.devui.spi.JsonRPCProvidersBuildItem;
import io.quarkus.devui.spi.page.CardPageBuildItem;
import io.quarkus.devui.spi.page.Page;

public class MessagingTopologyDevUIProcessor {

    @BuildStep(onlyIf = IsDevelopment.class)
    CardPageBuildItem createCard() {
        CardPageBuildItem card = new CardPageBuildItem();

        card.addPage(Page.webComponentPageBuilder()
                .title("Topology")
                .icon("font-awesome-solid:diagram-project")
                .componentLink("qwc-messaging-topology.js")
                .dynamicLabelJsonRPCMethodName("getServiceCount"));

        card.addPage(Page.externalPageBuilder("Topology API")
                .icon("font-awesome-solid:code")
                .url("/q/messaging-topology")
                .isJsonContent());

        return card;
    }

    @BuildStep(onlyIf = IsDevelopment.class)
    JsonRPCProvidersBuildItem createJsonRpcService() {
        return new JsonRPCProvidersBuildItem(MessagingTopologyJsonRpcService.class);
    }
}
