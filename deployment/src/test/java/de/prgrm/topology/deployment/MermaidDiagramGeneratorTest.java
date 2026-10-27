package de.prgrm.topology.deployment;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

import de.prgrm.topology.runtime.generator.MermaidDiagramGenerator;
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
    void singleServiceWithChannels() {
        TopologyInfo topology = new TopologyInfo();
        topology.setServiceName("order-service");

        ChannelInfo outgoing = new ChannelInfo("orders-out", "outgoing", "com.example.OrderProducer", "produce");
        outgoing.setTopic("orders-topic");
        topology.addChannel(outgoing);

        ChannelInfo incoming = new ChannelInfo("payments-in", "incoming", "com.example.PaymentConsumer", "consume");
        incoming.setTopic("payments-topic");
        topology.addChannel(incoming);

        String result = generator.generate(List.of(topology));

        assertTrue(result.contains("order_service"), "Should contain sanitized service name");
        assertTrue(result.contains("orders_topic"), "Should contain outgoing topic");
        assertTrue(result.contains("payments_topic"), "Should contain incoming topic");
        assertTrue(result.contains("-->"), "Should contain connections");
        assertTrue(result.contains("serviceClass"), "Should contain service styling");
        assertTrue(result.contains("topicClass"), "Should contain topic styling");
    }

    @Test
    void multipleServicesConnectedViaTopic() {
        TopologyInfo producer = new TopologyInfo();
        producer.setServiceName("producer-svc");
        ChannelInfo out = new ChannelInfo("events", "outgoing", "com.example.Producer", "emit");
        out.setTopic("shared-topic");
        producer.addChannel(out);

        TopologyInfo consumer = new TopologyInfo();
        consumer.setServiceName("consumer-svc");
        ChannelInfo in = new ChannelInfo("events", "incoming", "com.example.Consumer", "handle");
        in.setTopic("shared-topic");
        consumer.addChannel(in);

        String result = generator.generate(List.of(producer, consumer));

        assertTrue(result.contains("producer_svc"));
        assertTrue(result.contains("consumer_svc"));
        assertTrue(result.contains("P:1"));
        assertTrue(result.contains("C:1"));
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
        assertTrue(result.contains("hotTopicClass"), "Topic with 4+ connections should get hot styling");
    }

    @Test
    void specialCharactersInNodeIdsAreSanitized() {
        TopologyInfo topology = new TopologyInfo();
        topology.setServiceName("my-service.v2");

        ChannelInfo ch = new ChannelInfo("my.channel-name", "outgoing", "com.example.Svc", "send");
        ch.setTopic("my.topic-name");
        topology.addChannel(ch);

        String result = generator.generate(List.of(topology));

        assertTrue(result.contains("my_service_v2"), "Sanitized node ID should use underscores");
        assertTrue(result.contains("topic_my_topic_name"), "Sanitized topic ID should use underscores");
        assertTrue(result.contains("my-service.v2"), "Display label should keep original name");
    }
}
