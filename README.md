# Quarkus Messaging Topology Extension

A Quarkus extension that automatically discovers and visualizes your messaging topology for Eclipse MicroProfile Reactive Messaging applications. Get instant insights into your message flows with interactive diagrams, schema inspection, and cross-service topology aggregation.

![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)
![Quarkus](https://img.shields.io/badge/Quarkus-3.31.2-blue)
![Java](https://img.shields.io/badge/Java-25-orange)

## Features

- **Automatic Discovery**: Scans `@Incoming`, `@Outgoing`, and `@Channel` annotations at build time
- **Schema Introspection**: Automatically generates JSON schemas from message types (POJOs, records, sealed types)
- **Interactive Visualization**: Mermaid diagrams with real-time topology view
- **Dev UI Integration**: Seamless integration with Quarkus Dev UI
- **Multi-Service Support**: Aggregates topology from multiple services across your project
- **Cross-Language Support**: Python library included for non-Java services
- **Example Payloads**: Auto-generated example messages for testing
- **REST API**: Full REST API for topology inspection and schema access

## Quick Start

### 1. Add the Extension

Add the extension to your Quarkus project:

```xml
<dependency>
    <groupId>de.prgrm.quarkus</groupId>
    <artifactId>quarkus-messaging-topology</artifactId>
    <version>1.0.1-SNAPSHOT</version>
</dependency>
```

### 2. Configure (Optional)

Add configuration to `application.properties`:

```properties
# Project name for grouping related services
quarkus.messaging-topology.project-name=my-project

# Directory where topology files are stored
quarkus.messaging-topology.directory=/tmp/topology

# Automatically save topology on startup (default: true)
quarkus.messaging-topology.auto-save=true

# Cleanup topology file on shutdown (default: true)
quarkus.messaging-topology.cleanup-on-shutdown=false

# Include schema information (default: true)
quarkus.messaging-topology.include-schema=true
```

### 3. Start Your Application

```bash
mvn quarkus:dev
```

### 4. View the Topology

Open the Quarkus Dev UI at `http://localhost:8080/q/dev-ui` and navigate to the **Messaging Topology** card.

## Screenshots

### Topology Viewer
Interactive Mermaid diagram showing all messaging channels:

```
graph LR
    grumbl.created -->|GrumblCreatedEvent| ServiceA
    ServiceA -->|GrumblProcessedEvent| grumbl.processed
    user.created -->|UserCreatedEvent| ServiceB
    ServiceB -->|UserEnrichedEvent| user.enriched
```

### Schema Inspector
Automatic JSON schema generation with example payloads:

```json
{
  "type": "object",
  "properties": {
    "id": {"type": "string"},
    "text": {"type": "string"},
    "authorId": {"type": "string"},
    "timestamp": {"type": "string", "javaType": "Instant"}
  }
}
```

## How It Works

### Build-Time Discovery

The extension scans your code at build time using Jandex to discover:

```java
@ApplicationScoped
public class MessageProcessor {

    @Incoming("grumbl.created")
    @Outgoing("grumbl.processed")
    public GrumblProcessedEvent process(GrumblCreatedEvent event) {
        // Your business logic
        return new GrumblProcessedEvent(event.id(), ...);
    }
}
```

### Runtime Schema Generation

At runtime, the extension introspects message types to generate:
- JSON schemas
- Example payloads
- Field-level type information

### Multi-Service Aggregation

When multiple services share the same `project-name`, their topologies are automatically aggregated in the Dev UI, giving you a complete view of your messaging architecture.

## REST API

The extension provides a REST API for programmatic access:

### Get Topology

```bash
GET /q/messaging-topology
```

Returns complete topology with all channels and schemas.

### Get Aggregated Topology

```bash
GET /q/messaging-topology/aggregated
```

Returns topology from all services in the same project.

### Generate Mermaid Diagram

```bash
GET /q/messaging-topology/mermaid
```

Returns Mermaid diagram source code.

## Python Integration

For Python services that also participate in your messaging topology, use the included Python library:

### 1. Install the Library

Copy `python/messaging_topology.py` to your Python project.

### 2. Define Message Classes

```python
from dataclasses import dataclass
from typing import List

@dataclass
class GrumblCreatedEvent:
    id: str
    text: str
    authorId: str
    timestamp: str
```

### 3. Register Topology

```python
from messaging_topology import MessagingTopology

topology = MessagingTopology(
    project_name="my-project",
    service_name="python-worker"
)

topology.register_incoming(
    channel_name="grumbl.created",
    message_class=GrumblCreatedEvent,
    topic="persistent://public/default/grumbl.created"
)

topology.save()
```

The Python service topology will appear in the Quarkus Dev UI alongside your Java services!

See [python/README.md](python/README.md) for complete documentation.

## Supported Message Types

The extension supports automatic schema generation for:

- **POJOs**: Standard Java classes with getters/setters
- **Records**: Java records (Java 14+)
- **Sealed Types**: Sealed classes and interfaces
- **Collections**: `List<T>`, `Set<T>`, `Map<K,V>`
- **Generics**: Full generic type support
- **Nested Types**: Complex nested object structures

## Configuration Reference

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `quarkus.messaging-topology.enabled` | boolean | `true` | Enable/disable the extension |
| `quarkus.messaging-topology.directory` | string | `/tmp/topology` | Directory for topology files |
| `quarkus.messaging-topology.project-name` | string | - | Project name for grouping services |
| `quarkus.messaging-topology.service-name` | string | `${quarkus.application.name}` | Override service name |
| `quarkus.messaging-topology.group-id` | string | - | Service group ID |
| `quarkus.messaging-topology.auto-save` | boolean | `true` | Auto-save topology on startup |
| `quarkus.messaging-topology.cleanup-on-shutdown` | boolean | `true` | Delete topology file on shutdown |
| `quarkus.messaging-topology.include-schema` | boolean | `true` | Include schema information |

## Architecture

The extension consists of two modules:

### Deployment Module
- Build-time annotation scanning with Jandex
- Message type extraction from method signatures
- Dev UI integration with CardPageBuildItem

### Runtime Module
- REST API endpoints (`TopologyApiController`)
- Schema introspection service (`SchemaIntrospector`)
- Service registry for topology aggregation
- Qute templates for visualization

## Use Cases

- **Documentation**: Auto-generated messaging documentation
- **Onboarding**: Help new team members understand message flows
- **Testing**: Generate test payloads from schemas
- **Debugging**: Visualize message paths through your system
- **Multi-Team Projects**: Shared topology across service boundaries
- **Polyglot Architectures**: Unified view of Java and Python services

## Requirements

- Quarkus 3.31.2 or higher
- Java 25 or higher
- Maven 3.9+

## Building from Source

```bash
git clone https://github.com/your-org/quarkus-messaging-topology.git
cd quarkus-messaging-topology
mvn clean install
```

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

For questions and support:
- Create an issue on GitHub
- Check the [Wiki](../../wiki) for detailed documentation
- See [python/README.md](python/README.md) for Python integration guide

---

Built with ❤️ for the Quarkus community
