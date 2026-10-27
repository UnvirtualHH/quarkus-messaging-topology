package de.prgrm.topology.runtime.service;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

class SchemaIntrospectorTest {

    enum Status {
        NEW,
        PAID
    }

    record Item(String sku, long quantity) {
    }

    record OrderCreated(String orderId, String customerEmail, BigDecimal amount, Instant createdAt, LocalDate day,
            LocalDateTime localTime, UUID reference, Status status, List<Item> items, Map<String, Integer> counts,
            Optional<String> note, int quantity, boolean paid, double rate) {
    }

    static class TreeNode {
        public String name;
        public List<TreeNode> children;
    }

    private final SchemaIntrospector introspector = new SchemaIntrospector();

    // Same modules Quarkus registers on its ObjectMapper
    private final ObjectMapper mapper = JsonMapper.builder().findAndAddModules().build();

    @Test
    @SuppressWarnings("unchecked")
    void schemaDescribesTypesFormatsAndNestedObjects() {
        Map<String, Object> schema = introspector.introspectClass(OrderCreated.class);
        Map<String, Map<String, Object>> properties = (Map<String, Map<String, Object>>) schema.get("properties");

        assertEquals("number", properties.get("amount").get("type"));
        assertEquals("date-time", properties.get("createdAt").get("format"));
        assertEquals("date", properties.get("day").get("format"));
        assertEquals("uuid", properties.get("reference").get("format"));
        assertEquals(List.of("NEW", "PAID"), properties.get("status").get("enum"));
        assertEquals("List<Item>", properties.get("items").get("javaType"));

        Map<String, Object> items = (Map<String, Object>) properties.get("items").get("items");
        assertTrue(((Map<String, Object>) items.get("properties")).containsKey("sku"));
    }

    @Test
    void examplePayloadCanBeDeserializedIntoMessageType() {
        Object example = introspector.generateExample(introspector.introspectClass(OrderCreated.class));

        OrderCreated order = mapper.convertValue(example, OrderCreated.class);

        assertNotNull(order.orderId());
        assertEquals("user@example.com", order.customerEmail());
        assertNotNull(order.amount());
        assertNotNull(order.createdAt());
        assertNotNull(order.day());
        assertNotNull(order.localTime());
        assertNotNull(order.reference());
        assertEquals(Status.NEW, order.status());
        assertEquals(1, order.items().size());
        assertNotNull(order.items().get(0).sku());
        assertEquals(1, order.counts().size());
        assertTrue(order.note().isPresent());
    }

    @Test
    void simpleMessageTypesAreNotIntrospectedAsBeans() {
        Map<String, Object> schema = introspector.introspectClass(String.class);

        assertEquals("string", schema.get("type"));
        assertFalse(schema.containsKey("properties"), "JDK internals of String must not be exposed");
        assertInstanceOf(String.class, introspector.generateExample(schema));
    }

    @Test
    void recursiveTypesTerminate() {
        Object example = introspector.generateExample(introspector.introspectClass(TreeNode.class));

        TreeNode tree = mapper.convertValue(example, TreeNode.class);
        assertNotNull(tree.name);
    }
}
