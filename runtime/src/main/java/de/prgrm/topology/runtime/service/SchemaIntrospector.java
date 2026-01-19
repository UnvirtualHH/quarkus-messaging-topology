package de.prgrm.topology.runtime.service;

import java.lang.reflect.*;
import java.time.Instant;
import java.util.*;

import jakarta.enterprise.context.ApplicationScoped;

import de.prgrm.topology.runtime.model.ChannelInfo;
import de.prgrm.topology.runtime.model.TopologyInfo;
import de.prgrm.topology.runtime.model.TopologyRegistry;

@ApplicationScoped
public class SchemaIntrospector {

    private final Map<String, Map<String, Object>> schemaCache = new HashMap<>();

    public Map<String, Object> getSchema(String channelName, String direction) {
        String key = channelName + ":" + direction;

        if (schemaCache.containsKey(key)) {
            return schemaCache.get(key);
        }

        TopologyInfo topology = TopologyRegistry.INSTANCE.getTopology();
        if (topology == null) {
            return Collections.emptyMap();
        }

        for (ChannelInfo channel : topology.getChannels()) {

            if (channel.getChannelName().equals(channelName) &&
                    channel.getDirection().equals(direction)) {

                try {
                    ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
                    Class<?> clazz = contextClassLoader.loadClass(channel.getClassName());
                    System.out.println("      ✓ Class loaded: " + clazz.getName());

                    Class<?> messageType = findMessageTypeFromField(clazz, channel.getMethodName());

                    if (messageType != null) {
                        System.out.println("      ✓ Message type found from field: " + messageType.getName());
                    } else {
                        Method method = findMethod(clazz, channel.getMethodName());
                        if (method != null) {
                            messageType = extractMessageType(method, direction);
                        } else {
                            System.out.println("      ✗ Method not found");
                        }
                    }

                    if (messageType != null) {
                        Map<String, Object> schema = introspectClass(messageType);
                        schemaCache.put(key, schema);
                        return schema;
                    } else {
                        System.out.println("      ✗ Message type is null");
                    }
                } catch (ClassNotFoundException e) {
                    try {
                        Class<?> clazz = Class.forName(channel.getClassName(), true,
                                SchemaIntrospector.class.getClassLoader());

                        Class<?> messageType = findMessageTypeFromField(clazz, channel.getMethodName());
                        if (messageType != null) {
                            Map<String, Object> schema = introspectClass(messageType);
                            schemaCache.put(key, schema);
                            return schema;
                        }
                    } catch (ClassNotFoundException e2) {
                        System.err.println("      ✗ Also failed with alternative classloader: " + e2.getMessage());
                    }
                } catch (Exception e) {
                    System.err.println("      ✗ Error: " + e.getMessage());
                }
            }
        }

        System.out.println("  ✗ No matching channel found or no schema extracted");
        return Collections.emptyMap();
    }

    public Map<String, Object> getExamplePayload(String channelName, String direction) {
        Map<String, Object> schema = getSchema(channelName, direction);
        return generateExample(schema);
    }

    private Class<?> findMessageTypeFromField(Class<?> clazz, String fieldName) {
        try {
            Field field = clazz.getDeclaredField(fieldName);

            Type fieldType = field.getGenericType();

            return extractGenericType(fieldType);
        } catch (NoSuchFieldException e) {
            for (Field f : clazz.getDeclaredFields()) {
                System.out.println("          - " + f.getName() + " : " + f.getType().getSimpleName());
            }

            return null;
        }
    }

    private Method findMethod(Class<?> clazz, String methodName) {
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.getName().equals(methodName)) {
                return method;
            }
        }
        return null;
    }

    private Class<?> extractMessageType(Method method, String direction) {
        if ("incoming".equals(direction)) {
            if (method.getParameterCount() > 0) {
                Type paramType = method.getGenericParameterTypes()[0];
                return extractGenericType(paramType);
            }
        } else {
            Type returnType = method.getGenericReturnType();
            return extractGenericType(returnType);
        }
        return null;
    }

    private Class<?> extractGenericType(Type type) {
        if (type instanceof ParameterizedType) {
            ParameterizedType pType = (ParameterizedType) type;

            Type[] args = pType.getActualTypeArguments();

            if (args.length > 0) {
                if (args[0] instanceof Class) {
                    return (Class<?>) args[0];
                } else if (args[0] instanceof ParameterizedType) {
                    return (Class<?>) ((ParameterizedType) args[0]).getRawType();
                }
            }
        } else if (type instanceof Class) {
            return (Class<?>) type;
        }

        return null;
    }

    private Map<String, Object> introspectClass(Class<?> clazz) {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        schema.put("className", clazz.getName());
        schema.put("simpleName", clazz.getSimpleName());

        Map<String, Object> properties = new HashMap<>();

        for (Field field : getAllFields(clazz)) {
            if (Modifier.isStatic(field.getModifiers()))
                continue;
            if (Modifier.isTransient(field.getModifiers()))
                continue;

            Map<String, Object> fieldSchema = new HashMap<>();
            fieldSchema.put("type", getJsonType(field.getType()));
            fieldSchema.put("javaType", field.getType().getSimpleName());

            if (field.getGenericType() instanceof ParameterizedType) {
                ParameterizedType pType = (ParameterizedType) field.getGenericType();
                Type[] args = pType.getActualTypeArguments();
                if (args.length > 0 && args[0] instanceof Class) {
                    fieldSchema.put("genericType", ((Class<?>) args[0]).getSimpleName());
                }
            }

            properties.put(field.getName(), fieldSchema);
        }

        schema.put("properties", properties);

        List<String> requiredFields = new ArrayList<>();
        for (Field field : getAllFields(clazz)) {
            if (!Modifier.isStatic(field.getModifiers()) &&
                    !Modifier.isTransient(field.getModifiers())) {
                requiredFields.add(field.getName());
            }
        }
        schema.put("required", requiredFields);

        return schema;
    }

    private List<Field> getAllFields(Class<?> clazz) {
        List<Field> fields = new ArrayList<>();
        Class<?> current = clazz;

        while (current != null && current != Object.class) {
            fields.addAll(Arrays.asList(current.getDeclaredFields()));
            current = current.getSuperclass();
        }

        return fields;
    }

    private String getJsonType(Class<?> type) {
        if (type == String.class)
            return "string";
        if (type == Integer.class || type == int.class ||
                type == Long.class || type == long.class ||
                type == Short.class || type == short.class ||
                type == Byte.class || type == byte.class)
            return "integer";
        if (type == Boolean.class || type == boolean.class)
            return "boolean";
        if (type == Double.class || type == double.class ||
                type == Float.class || type == float.class)
            return "number";
        if (type.isArray() || Collection.class.isAssignableFrom(type))
            return "array";
        if (type == Date.class || type == Instant.class)
            return "string";
        if (type.isEnum())
            return "string";
        return "object";
    }

    private Map<String, Object> generateExample(Map<String, Object> schema) {
        if (schema.isEmpty()) {
            return Map.of(
                    "id", "example-" + System.currentTimeMillis(),
                    "message", "Example message",
                    "timestamp", Instant.now().toString());
        }

        Map<String, Object> example = new LinkedHashMap<>();

        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");

        if (properties != null) {
            for (Map.Entry<String, Object> entry : properties.entrySet()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> prop = (Map<String, Object>) entry.getValue();
                String type = (String) prop.get("type");
                String javaType = (String) prop.get("javaType");

                example.put(entry.getKey(), generateExampleValue(type, javaType, entry.getKey()));
            }
        }

        return example;
    }

    private Object generateExampleValue(String jsonType, String javaType, String fieldName) {
        if (fieldName.toLowerCase().contains("id")) {
            return "example-" + UUID.randomUUID().toString().substring(0, 8);
        }
        if (fieldName.toLowerCase().contains("timestamp") ||
                fieldName.toLowerCase().contains("date") ||
                fieldName.toLowerCase().contains("time")) {
            return Instant.now().toString();
        }
        if (fieldName.toLowerCase().contains("email")) {
            return "user@example.com";
        }
        if (fieldName.toLowerCase().contains("name")) {
            return "Example Name";
        }

        return switch (jsonType) {
            case "string" -> "example-value";
            case "integer" -> 123;
            case "number" -> 123.45;
            case "boolean" -> true;
            case "array" -> List.of("item1", "item2");
            default -> Map.of("key", "value");
        };
    }
}
