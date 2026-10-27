package de.prgrm.topology.deployment;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.when;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import de.prgrm.topology.deployment.app.Greeting;
import de.prgrm.topology.deployment.app.GreetingResource;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * Outside of dev mode (here: test mode) none of the extension's endpoints may be exposed.
 */
class MessagingTopologyTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(Greeting.class, GreetingResource.class));

    @Test
    void topologyApiIsNotExposed() {
        when().get("/q/messaging-topology").then().statusCode(404);
    }

    @Test
    void oldViewerLocationsAreNotExposed() {
        when().get("/q/messaging-topology-viewer").then().statusCode(404);
        when().get("/q/messaging-topology/static/topology-viewer.js").then().statusCode(404);
    }

    @Test
    void sendEndpointIsNotExposed() {
        given().contentType("application/json")
                .body("{\"channel\":\"greetings\",\"payload\":{\"name\":\"x\",\"count\":1}}")
                .when().post("/q/messaging-topology/send")
                .then().statusCode(404);
    }

    @Test
    void applicationStillWorks() {
        when().get("/received").then().statusCode(200);
    }
}
