package de.prgrm.topology.runtime.service;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.logging.Logger;

import de.prgrm.topology.runtime.model.ChannelInfo;
import de.prgrm.topology.runtime.model.TopologyInfo;
import de.prgrm.topology.runtime.model.TopologyRegistry;

@ApplicationScoped
public class SchemaIntrospector {

    private static final Logger LOG = Logger.getLogger(SchemaIntrospector.class);

    private static final int MAX_DEPTH = 4;

    private static final Set<Class<?>> INTEGER_TYPES = Set.of(int.class, Integer.class, long.class, Long.class,
            short.class, Short.class, byte.class, Byte.class, BigInteger.class);

    private static final Set<Class<?>> NUMBER_TYPES = Set.of(double.class, Double.class, float.class, Float.class,
            BigDecimal.class);

    private static final Map<Class<?>, String> FORMATS = Map.of(
            Instant.class, "date-time",
            OffsetDateTime.class, "date-time",
            ZonedDateTime.class, "date-time",
            Date.class, "date-time",
            LocalDateTime.class, "local-date-time",
            LocalDate.class, "date",
            LocalTime.class, "time",
            UUID.class, "uuid",
            Duration.class, "duration");

    private final Map<String, Map<String, Object>> schemaCache = new ConcurrentHashMap<>();

    public Map<String, Object> getSchema(String channelName, String direction) {
        String key = channelName + ":" + direction;

        Map<String, Object> cached = schemaCache.get(key);
        if (cached != null) {
            return cached;
        }

        TopologyInfo topology = TopologyRegistry.INSTANCE.getTopology();
        if (topology == null) {
            return Collections.emptyMap();
        }

        for (ChannelInfo channel : topology.getChannels()) {

            if (channel.getChannelName().equals(channelName) &&
                    channel.getDirection().equals(direction)) {

                Class<?> messageType = null;

                if (channel.getMessageType() != null) {
                    try {
                        messageType = Thread.currentThread().getContextClassLoader()
                                .loadClass(channel.getMessageType());
                        LOG.debugf("Message type loaded from build-time info: %s", messageType.getName());
                    } catch (ClassNotFoundException e) {
                        LOG.warnf("Could not load message type: %s", channel.getMessageType());
                    }
                }

                if (messageType == null) {
                    try {
                        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
                        Class<?> clazz = contextClassLoader.loadClass(channel.getClassName());
                        LOG.debugf("Class loaded: %s", clazz.getName());

                        messageType = findMessageTypeFromField(clazz, channel.getMethodName());

                        if (messageType != null) {
                            LOG.debugf("Message type found from field: %s", messageType.getName());
                        } else {
                            Method method = findMethod(clazz, channel.getMethodName());
                            if (method != null) {
                                messageType = extractMessageType(method, direction);
                            } else {
                                LOG.debug("Method not found");
                            }
                        }
                    } catch (ClassNotFoundException e) {
                        try {
                            Class<?> clazz = Class.forName(channel.getClassName(), true,
                                    SchemaIntrospector.class.getClassLoader());

                            messageType = findMessageTypeFromField(clazz, channel.getMethodName());
                        } catch (ClassNotFoundException e2) {
                            LOG.warnf("Also failed with alternative classloader: %s", e2.getMessage());
                        }
                    } catch (Exception e) {
                        LOG.warnf("Error during schema introspection: %s", e.getMessage());
                    }
                }

                if (messageType != null) {
                    Map<String, Object> schema = introspectClass(messageType);
                    schemaCache.put(key, schema);
                    return schema;
                } else {
                    LOG.debug("Message type is null");
                }
            }
        }

        LOG.debug("No matching channel found or no schema extracted");
        return Collections.emptyMap();
    }

    public Object getExamplePayload(String channelName, String direction) {
        Map<String, Object> schema = getSchema(channelName, direction);
        return generateExample(schema);
    }

    private Class<?> findMessageTypeFromField(Class<?> clazz, String fieldName) {
        try {
            Field field = clazz.getDeclaredField(fieldName);
            return extractGenericType(field.getGenericType());
        } catch (NoSuchFieldException e) {
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
        if (type instanceof ParameterizedType pType) {
            Type[] args = pType.getActualTypeArguments();

            if (args.length > 0) {
                if (args[0] instanceof Class<?> clazz) {
                    return clazz;
                } else if (args[0] instanceof ParameterizedType nested) {
                    return (Class<?>) nested.getRawType();
                }
            }
        } else if (type instanceof Class<?> clazz) {
            return clazz;
        }

        return null;
    }

    /**
     * Builds a JSON-schema-like description of the message type. Non-bean types (String, numbers, dates, ...) are
     * described directly, beans and records are described by their fields.
     */
    Map<String, Object> introspectClass(Class<?> clazz) {
        Map<String, Object> schema = describe(clazz, clazz, 0, new HashSet<>());
        schema.put("className", clazz.getName());
        schema.put("simpleName", clazz.getSimpleName());
        return schema;
    }

    private Map<String, Object> describe(Type genericType, Class<?> raw, int depth,
            Set<Class<?>> visiting) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("javaType", simpleTypeName(genericType));

        if (raw == Optional.class) {
            Class<?> valueType = rawTypeOfArgument(genericType, 0);
            schema.putAll(describe(typeArgument(genericType, 0), valueType, depth, visiting));
            schema.put("javaType", simpleTypeName(genericType));
            return schema;
        }

        String format = FORMATS.get(raw);
        if (format != null) {
            schema.put("type", "string");
            schema.put("format", format);
        } else if (raw == String.class || raw == char.class || raw == Character.class) {
            schema.put("type", "string");
        } else if (INTEGER_TYPES.contains(raw)) {
            schema.put("type", "integer");
        } else if (NUMBER_TYPES.contains(raw)) {
            schema.put("type", "number");
        } else if (raw == boolean.class || raw == Boolean.class) {
            schema.put("type", "boolean");
        } else if (raw.isEnum()) {
            schema.put("type", "string");
            schema.put("enum", Arrays.stream(raw.getEnumConstants()).map(Object::toString).toList());
        } else if (raw.isArray()) {
            schema.put("type", "array");
            schema.put("items", describe(raw.getComponentType(), raw.getComponentType(), depth + 1, visiting));
        } else if (Collection.class.isAssignableFrom(raw)) {
            schema.put("type", "array");
            schema.put("items", describe(typeArgument(genericType, 0), rawTypeOfArgument(genericType, 0), depth + 1,
                    visiting));
        } else if (Map.class.isAssignableFrom(raw)) {
            schema.put("type", "object");
            schema.put("additionalProperties",
                    describe(typeArgument(genericType, 1), rawTypeOfArgument(genericType, 1), depth + 1, visiting));
        } else {
            schema.put("type", "object");
            if (isBean(raw) && depth < MAX_DEPTH && visiting.add(raw)) {
                Map<String, Object> properties = new LinkedHashMap<>();
                for (Field field : getAllFields(raw)) {
                    properties.put(field.getName(),
                            describe(field.getGenericType(), field.getType(), depth + 1, visiting));
                }
                schema.put("properties", properties);
                schema.put("required", new ArrayList<>(properties.keySet()));
                visiting.remove(raw);
            }
        }
        return schema;
    }

    private static boolean isBean(Class<?> raw) {
        return !raw.isPrimitive() && !raw.isInterface() && !raw.getName().startsWith("java.")
                && !raw.getName().startsWith("javax.");
    }

    private static Type typeArgument(Type type, int index) {
        if (type instanceof ParameterizedType pType && pType.getActualTypeArguments().length > index) {
            return pType.getActualTypeArguments()[index];
        }
        return Object.class;
    }

    private static Class<?> rawTypeOfArgument(Type type, int index) {
        Type argument = typeArgument(type, index);
        if (argument instanceof Class<?> clazz) {
            return clazz;
        }
        if (argument instanceof ParameterizedType pType && pType.getRawType() instanceof Class<?> clazz) {
            return clazz;
        }
        return Object.class;
    }

    private static String simpleTypeName(Type type) {
        // strips packages and enclosing classes: java.util.List<com.acme.Order$Item> -> List<Item>
        return type.getTypeName().replaceAll("\\b(?:[A-Za-z_]\\w*[.$])+", "");
    }

    private List<Field> getAllFields(Class<?> clazz) {
        List<Field> fields = new ArrayList<>();
        Class<?> current = clazz;

        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                int modifiers = field.getModifiers();
                if (!Modifier.isStatic(modifiers) && !Modifier.isTransient(modifiers) && !field.isSynthetic()) {
                    fields.add(field);
                }
            }
            current = current.getSuperclass();
        }

        return fields;
    }

    /**
     * Generates an example payload that can be deserialized into the message type by Jackson.
     */
    Object generateExample(Map<String, Object> schema) {
        if (schema.isEmpty()) {
            return Map.of(
                    "id", "example-" + System.currentTimeMillis(),
                    "message", "Example message",
                    "timestamp", Instant.now().truncatedTo(ChronoUnit.SECONDS).toString());
        }
        return exampleValue(schema, "value");
    }

    @SuppressWarnings("unchecked")
    private Object exampleValue(Map<String, Object> schema, String fieldName) {
        String type = String.valueOf(schema.get("type"));
        return switch (type) {
            case "string" -> exampleString(schema, fieldName);
            case "integer" -> 1;
            case "number" -> 9.99;
            case "boolean" -> true;
            case "array" -> {
                Map<String, Object> items = (Map<String, Object>) schema.get("items");
                yield items == null ? List.of() : List.of(exampleValue(items, fieldName));
            }
            default -> {
                Map<String, Object> example = new LinkedHashMap<>();
                Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
                Map<String, Object> additional = (Map<String, Object>) schema.get("additionalProperties");
                if (properties != null) {
                    properties.forEach((name, property) -> example.put(name,
                            exampleValue((Map<String, Object>) property, name)));
                } else if (additional != null) {
                    example.put("key", exampleValue(additional, "value"));
                }
                yield example;
            }
        };
    }

    @SuppressWarnings("unchecked")
    private Object exampleString(Map<String, Object> schema, String fieldName) {
        List<String> enumValues = (List<String>) schema.get("enum");
        if (enumValues != null && !enumValues.isEmpty()) {
            return enumValues.get(0);
        }

        Object format = schema.get("format");
        if (format != null) {
            return switch (format.toString()) {
                case "date-time" -> Instant.now().truncatedTo(ChronoUnit.SECONDS).toString();
                case "local-date-time" -> LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS).toString();
                case "date" -> LocalDate.now().toString();
                case "time" -> LocalTime.now().truncatedTo(ChronoUnit.SECONDS).toString();
                case "uuid" -> UUID.randomUUID().toString();
                case "duration" -> "PT15M";
                default -> "example";
            };
        }

        String name = fieldName.toLowerCase(Locale.ROOT);
        if (name.equals("id") || name.endsWith("id")) {
            return "example-" + UUID.randomUUID().toString().substring(0, 8);
        }
        if (name.contains("email")) {
            return "user@example.com";
        }
        if (name.contains("currency")) {
            return "EUR";
        }
        if (name.contains("url") || name.contains("uri")) {
            return "https://example.com";
        }
        if (name.contains("name")) {
            return "Example Name";
        }
        return "example-" + fieldName;
    }
}
