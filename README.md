# Quarkus Messaging Topology Extension

A Quarkus **dev mode** extension that automatically discovers and visualizes the messaging topology of Eclipse MicroProfile Reactive Messaging applications. Get instant insights into your message flows with interactive diagrams, schema inspection, test messages and cross-service topology aggregation.

![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)
![Quarkus](https://img.shields.io/badge/Quarkus-3.40.1.1-blue)
![Java](https://img.shields.io/badge/Java-17%2B-orange)

## Features

- **Automatic Discovery**: Scans `@Incoming`, `@Outgoing`, and `@Channel` annotations at build time
- **Schema Introspection**: Generates a JSON schema from the fields of your message types (POJOs and records)
- **Native Dev UI Page**: Interactive diagram of services, topics and message flows, styled with the Dev UI theme (light and dark)
- **Explore**: Zoom, pan, click on services and topics to see producers, consumers, schemas and implementations
- **Multi-Service Support**: Aggregates the topology of multiple services running on the same machine
- **Test Messages**: Send messages through your application's own `Emitter` with auto-generated example payloads

## Dev Mode Only

The extension is a development tool. The Dev UI page, the REST API, its beans and the topology files are **only active in dev mode** (`mvn quarkus:dev`). In test and production mode (including native builds) none of them are registered, so it is safe to keep the dependency in your application.

## Quick Start

### 1. Add the Extension

```xml
<dependency>
    <groupId>de.prgrm.quarkus</groupId>
    <artifactId>quarkus-messaging-topology</artifactId>
    <version>1.0.2</version>
</dependency>
```

### 2. Configure (Optional)

```properties
# Project name for grouping related services
quarkus.messaging-topology.project-name=my-project

# Directory where topology files are stored
quarkus.messaging-topology.directory=/tmp/topology

# Keep the topology file after shutdown (default: true = delete)
quarkus.messaging-topology.cleanup-on-shutdown=false
```

### 3. Start Your Application in Dev Mode

```bash
mvn quarkus:dev
```

### 4. View the Topology

Open the Quarkus Dev UI at `http://localhost:8080/q/dev-ui` and click **Topology** on the **Messaging Topology** card.

> The page is part of the Quarkus Dev UI. If your application shows no Dev UI at `/q/dev-ui`, add `io.quarkus:quarkus-devui` to your dependencies.

## The Dev UI Page

### Diagram
- Services are drawn as boxes (your own service is highlighted), topics as cylinders; edges are labeled with the producing or consuming method.
- Topics with four or more connections are marked as *busy*.
- **Scroll** to zoom, **drag** to pan, **double-click** or the fit button to fit the diagram to the screen.
- **Click a service or topic** to highlight its connections and open the details panel with all producers and consumers.
- Copy the Mermaid source to paste the diagram into your documentation.

### Channels
A filterable table of all channels of all services with direction, topic, service, implementation, message type and connector.

### Channel Details
Implementation, connector and message type of a channel, the schema of the message type and, for channels backed by an `Emitter`, an editor to send a test message.

Mermaid is shipped with the extension (as [mvnpm](https://mvnpm.org) dependency of the deployment module), so the page works offline and nothing is added to your application's runtime.

### Schema Inspector
Automatic JSON schema generation with example payloads:

```json
{
  "type": "object",
  "properties": {
    "id": {"type": "string"},
    "text": {"type": "string"},
    "authorId": {"type": "string"},
    "timestamp": {"type": "string", "format": "date-time", "javaType": "Instant"}
  }
}
```

## How It Works

### Build-Time Discovery

The extension scans your code at build time using Jandex to discover:

```java
@ApplicationScoped
public class MessageProcessor {

    @Incoming("order.created")
    @Outgoing("order.processed")
    public OrderProcessedEvent process(OrderCreatedEvent event) {
        // Your business logic
        return new OrderProcessedEvent(event.id(), ...);
    }
}
```

`@Channel` is detected on injected fields (e.g. `@Channel("orders") Emitter<Order> emitter;`).

### Runtime Schema Generation

On startup, the extension resolves the configured topic and connector of every channel (`mp.messaging.[incoming|outgoing].<channel>.topic/connector`) and introspects the message types to generate:
- JSON schemas
- Example payloads
- Field-level type information

### Multi-Service Aggregation

Every service writes its topology to `<directory>/<service-name>.json` on startup. The Dev UI page reads all files in that directory, so services running in dev mode **on the same machine** with the same `directory` (and, if set, the same `project-name`) appear in one diagram.

### Sending Test Messages

Channels backed by an injected `Emitter` or `MutinyEmitter` get a **Send test message** editor in the channel details of the Dev UI page, prefilled with an example payload. The JSON payload is converted to the channel's message type and sent through that emitter, so it takes the same path (connector, topic, serialization) as messages sent by the application itself. Channels produced by `@Outgoing` methods cannot be fed from outside and therefore have no editor.

## REST API

All endpoints are only available in dev mode.

### Get Topology

```bash
GET /q/messaging-topology
```

Returns the topology of the current service with all channels, schemas and example payloads.

### Send a Test Message

```bash
POST /q/messaging-topology/send
Content-Type: application/json

{"channel": "orders", "payload": {"id": "42", "amount": 9.99}}
```

Returns `400` if the channel is unknown, not backed by an emitter, or the payload does not match the message type.

## Supported Message Types

Schema generation is based on the declared fields of the message type (including inherited fields). Example payloads are generated from the schema and can be deserialized into the message type:

- **POJOs** and **Records**, including nested objects (up to four levels, recursive types are cut off)
- **Primitive and wrapper types**, `String`, `BigDecimal`, `BigInteger`, enums (with their constants)
- **Date and time types**: `Instant`, `OffsetDateTime`, `ZonedDateTime`, `Date`, `LocalDateTime`, `LocalDate`, `LocalTime`, `Duration` (with ISO-8601 example values)
- **`UUID`**, **`Optional<T>`**
- **Collections and arrays** (`array` with the element schema) and **maps** (`object` with the value schema)

## Configuration Reference

All properties are runtime properties and only take effect in dev mode.

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `quarkus.messaging-topology.enabled` | boolean | `true` | Enable/disable the extension |
| `quarkus.messaging-topology.directory` | string | `/tmp/topology` | Directory for topology files |
| `quarkus.messaging-topology.project-name` | string | - | Project name for grouping services |
| `quarkus.messaging-topology.service-name` | string | artifactId of the application | Override service name |
| `quarkus.messaging-topology.group-id` | string | groupId of the application | Override group ID |
| `quarkus.messaging-topology.auto-save` | boolean | `true` | Save topology file on startup |
| `quarkus.messaging-topology.cleanup-on-shutdown` | boolean | `true` | Delete topology file on shutdown |
| `quarkus.messaging-topology.include-schema` | boolean | `true` | Include schemas and example payloads |

## Architecture

The extension consists of two modules:

### Deployment Module
- Build-time annotation scanning with Jandex
- Message type extraction from method signatures
- Dev UI card and page (`dev-ui/qwc-messaging-topology.js`, a Lit web component)
- Mermaid as mvnpm dependency, served through the Dev UI import map
- Restriction of all beans and endpoints to dev mode

### Runtime Module
- JSON-RPC backend of the Dev UI page (`MessagingTopologyJsonRpcService`)
- REST API endpoints (`TopologyApiController`)
- Schema introspection and example payloads (`SchemaIntrospector`)
- Service registry for topology aggregation
- Mermaid diagram generation (`MermaidDiagramGenerator`)

## Use Cases

- **Documentation**: Auto-generated messaging documentation
- **Onboarding**: Help new team members understand message flows
- **Testing**: Generate test payloads from schemas and send them through your emitters
- **Debugging**: Visualize message paths through your system

## Requirements

- Quarkus 3.40.1.1 or higher
- Java 17 or higher
- Maven 3.9+

## Building from Source

```bash
git clone https://github.com/UnvirtualHH/quarkus-messaging-topology.git
cd quarkus-messaging-topology
mvn clean install
```

Artifacts are only signed when building with the `release` profile (`-Prelease`), which the Maven Release Plugin activates automatically.

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.

## Roadmap

- [ ] Support for Kafka Streams topology
- [ ] GraphQL API endpoint
- [ ] Export to AsyncAPI specification
- [ ] Dead letter queue visualization
- [ ] Message flow tracing integration
- [ ] Support for more messaging connectors (RabbitMQ, SQS, etc.)

## Support

For questions and support, create an issue on GitHub.

---

Built with ❤️ for the Quarkus community
