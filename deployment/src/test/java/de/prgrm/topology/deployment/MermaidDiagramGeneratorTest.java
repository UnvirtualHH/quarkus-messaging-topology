package de.prgrm.topology.deployment;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import de.prgrm.topology.runtime.generator.MermaidDiagramGenerator;
import de.prgrm.topology.runtime.generator.MermaidDiagramGenerator.Node;
import de.prgrm.topology.runtime.model.ChannelInfo;
import de.prgrm.topology.runtime.model.TopologyInfo;

class MermaidDiagramGeneratorTest {

    private final MermaidDiagramGenerator generator = new MermaidDiagramGenerator();

    @Test
    void emptyTopologyProducesValidDiagram() {
        String result = generator.generate(List.of());
        assertNotNull(result);
        assertTrue(result.startsWith("graph LR"));
    }

    @Test
    void servicesAndTopicsAreRenderedWithShapesAndConnections() {
        String result = generator.generate(List.of(orderService()));

        assertTrue(result.contains("svc_order_service(\"order-service\")"), "Service as rounded node");
        assertTrue(result.contains("topic_orders_topic[(\"orders-topic\")]"), "Topic as cylinder");
        assertTrue(result.contains("svc_order_service -->|\"produce\"| topic_orders_topic"), "Outgoing edge");
        assertTrue(result.contains("topic_payments_topic -->|\"consume\"| svc_order_service"), "Incoming edge");
        assertTrue(result.contains("class svc_order_service serviceClass"));
        assertTrue(result.contains("class topic_orders_topic topicClass"));
        assertFalse(result.contains("classDef"), "Colors are applied by the Dev UI theme");
    }

    @Test
    void localServiceGetsOwnClass() {
        String result = generator.generate(List.of(orderService()), "order-service");
        assertTrue(result.contains("class svc_order_service localServiceClass"));
    }

    @Test
    void hotTopicStylingAppliedForManyConnections() {
        TopologyInfo topology = new TopologyInfo();
        topology.setServiceName("svc-1");

        for (int i = 0; i < 4; i++) {
            ChannelInfo ch = new ChannelInfo("ch-" + i, i % 2 == 0 ? "outgoing" : "incoming", "com.example.Svc", "method" + i);
            ch.setTopic("hot-topic");
            topology.addChannel(ch);
        }

        String result = generator.generate(List.of(topology));
        assertTrue(result.contains("class topic_hot_topic hotTopicClass"));
    }

    @Test
    void labelsAreEscapedAndIdsSanitized() {
        TopologyInfo topology = new TopologyInfo();
        topology.setServiceName("my \"service\".v2");

        ChannelInfo ch = new ChannelInfo("my.channel-name", "outgoing", "com.example.Svc", "send<T>");
        ch.setTopic("end");
        topology.addChannel(ch);

        String result = generator.generate(List.of(topology));

        assertTrue(result.contains("svc_my__service__v2(\"my #quot;service#quot;.v2\")"));
        assertTrue(result.contains("|\"send#lt;T#gt;\"|"));
        // reserved words like "end" must not become node ids
        assertTrue(result.contains("topic_end[(\"end\")]"));
    }

    @Test
    void nodesMapIdsToServicesAndTopics() {
        Map<String, Node> nodes = generator.nodes(List.of(orderService()));

        assertEquals(new Node("service", "order-service"), nodes.get("svc_order_service"));
        assertEquals(new Node("topic", "orders-topic"), nodes.get("topic_orders_topic"));
        assertEquals(new Node("topic", "payments-topic"), nodes.get("topic_payments_topic"));
        assertEquals(3, nodes.size());
    }

    private static TopologyInfo orderService() {
        TopologyInfo topology = new TopologyInfo();
        topology.setServiceName("order-service");

        ChannelInfo outgoing = new ChannelInfo("orders-out", "outgoing", "com.example.OrderProducer", "produce");
        outgoing.setTopic("orders-topic");
        topology.addChannel(outgoing);

        ChannelInfo incoming = new ChannelInfo("payments-in", "incoming", "com.example.PaymentConsumer", "consume");
        incoming.setTopic("payments-topic");
        topology.addChannel(incoming);
        return topology;
    }
}
