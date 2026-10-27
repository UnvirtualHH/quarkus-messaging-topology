package de.prgrm.topology.deployment;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.when;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fasterxml.jackson.databind.JsonNode;

import de.prgrm.topology.deployment.app.Greeting;
import de.prgrm.topology.deployment.app.GreetingResource;
import io.quarkus.test.QuarkusDevModeTest;
import io.restassured.RestAssured;

class MessagingTopologyDevModeTest {

    @RegisterExtension
    static final QuarkusDevModeTest devMode = new QuarkusDevModeTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(Greeting.class, GreetingResource.class)
                    .addAsResource(new StringAsset("quarkus.messaging-topology.directory=target/topology-dev-test\n"),
                            "application.properties"));

    @Test
    void topologyContainsDiscoveredChannels() {
        when().get("/q/messaging-topology")
                .then().statusCode(200)
                .body("channels.findAll { it.channelName == 'greetings' }.direction", hasItems("incoming", "outgoing"))
                .body("channels.find { it.direction == 'outgoing' }.sendable", is(true))
                .body("channels.find { it.direction == 'outgoing' }.messageType", is(Greeting.class.getName()));
    }

    @Test
    void devUiPageIsServed() {
        when().get("/q/dev-ui/quarkus-messaging-topology/qwc-messaging-topology.js")
                .then().statusCode(200)
                .body(containsString("customElements.define('qwc-messaging-topology'"));
    }

    @Test
    void mermaidIsServedLocallyThroughTheImportMap() {
        String index = when().get("/q/dev-ui/").then().statusCode(200).extract().asString();
        Matcher matcher = Pattern.compile("\"mermaid/\"\\s*:\\s*\"([^\"]+)\"").matcher(index);
        assertTrue(matcher.find(), "Mermaid must be part of the Dev UI import map");

        when().get(matcher.group(1) + "mermaid.esm.min.mjs").then().statusCode(200);
    }

    @Test
    void jsonRpcReturnsTopologyWithDiagram() throws Exception {
        try (DevUiJsonRpcClient client = new DevUiJsonRpcClient(RestAssured.port)) {
            JsonNode topology = client.call("getTopology", Map.of());

            assertTrue(topology.path("enabled").asBoolean());
            String localService = topology.path("localService").asText();
            assertFalse(localService.isEmpty());
            JsonNode local = topology.path("services").get(0);
            assertEquals(localService, local.path("serviceName").asText());

            String diagram = topology.path("diagram").asText();
            assertTrue(diagram.contains("topic_greetings[(\"greetings\")]"), diagram);
            assertTrue(diagram.contains("localServiceClass"), diagram);
            assertEquals("topic", topology.path("nodes").path("topic_greetings").path("kind").asText());

            JsonNode outgoing = null;
            for (JsonNode channel : local.path("channels")) {
                if ("outgoing".equals(channel.path("direction").asText())) {
                    outgoing = channel;
                }
            }
            assertNotNull(outgoing);
            assertTrue(outgoing.path("injected").asBoolean());
            assertEquals("Example Name", outgoing.path("examplePayload").path("name").asText());
            assertEquals("integer", outgoing.path("schema").path("properties").path("count").path("type").asText());
        }
    }

    @Test
    void jsonRpcSendDeliversMessage() throws Exception {
        try (DevUiJsonRpcClient client = new DevUiJsonRpcClient(RestAssured.port)) {
            JsonNode result = client.call("sendMessage",
                    Map.of("channel", "greetings", "payload", "{\"name\":\"from-dev-ui\",\"count\":3}"));
            assertTrue(result.path("success").asBoolean(), result.toString());

            JsonNode invalid = client.call("sendMessage", Map.of("channel", "greetings", "payload", "{not json"));
            assertFalse(invalid.path("success").asBoolean());
            assertTrue(invalid.path("message").asText().startsWith("Invalid JSON"), invalid.toString());
        }

        when().get("/received")
                .then().statusCode(200)
                .body("name", hasItem("from-dev-ui"));
    }

    @Test
    void sendDeliversMessageThroughEmitter() {
        given().contentType("application/json")
                .body("{\"channel\":\"greetings\",\"payload\":{\"name\":\"dev-ui\",\"count\":7}}")
                .when().post("/q/messaging-topology/send")
                .then().statusCode(200)
                .body("success", is(true));

        when().get("/received")
                .then().statusCode(200)
                .body("name", hasItem("dev-ui"))
                .body("count", hasItem(7));
    }

    @Test
    void sendToUnknownChannelIsRejected() {
        given().contentType("application/json")
                .body("{\"channel\":\"does-not-exist\",\"payload\":{}}")
                .when().post("/q/messaging-topology/send")
                .then().statusCode(400);
    }

    @Test
    void sendWithMismatchingPayloadIsRejected() {
        given().contentType("application/json")
                .body("{\"channel\":\"greetings\",\"payload\":{\"count\":\"not-a-number\"}}")
                .when().post("/q/messaging-topology/send")
                .then().statusCode(400);
    }
}
