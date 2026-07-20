package io.instanto.sarto.codec.json;

import io.instanto.sarto.codec.ContentCodec;
import io.instanto.sarto.codec.ContentCodecs;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.nio.charset.StandardCharsets;

/** JSON syntax and type registry shared by generated codecs. */
public class JsonCodec implements ContentCodec {
    private final Map<Class<?>, JsonTypeCodec<?>> types = new LinkedHashMap<>();

    public final <T> JsonCodec register(Class<T> type, JsonTypeCodec<T> codec) {
        types.put(Objects.requireNonNull(type, "type"), Objects.requireNonNull(codec, "codec"));
        return this;
    }

    @Override
    public final boolean supports(String mediaType) {
        String normalized = ContentCodecs.normalize(mediaType);
        return normalized.equals("application/json") || normalized.endsWith("+json");
    }

    public final String encode(Object value) {
        JsonOutput output = new JsonOutput();
        writeValue(output, value);
        return output.toString();
    }

    public final <T> T decode(String content, Class<T> type) {
        PortableJsonParser parser = new PortableJsonParser(content);
        T value = readValue(parser, type);
        parser.expectEnd();
        return value;
    }

    public final <T> List<T> decodeList(String content, Class<T> elementType) {
        PortableJsonParser parser = new PortableJsonParser(content);
        List<T> value = readList(parser, elementType);
        parser.expectEnd();
        return value;
    }

    @Override
    public final byte[] encodeContent(Object value) {
        return encode(value).getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public final <T> T decodeContent(byte[] content, Class<T> type) {
        return decode(new String(content, StandardCharsets.UTF_8), type);
    }

    @Override
    public final <T> List<T> decodeListContent(byte[] content, Class<T> elementType) {
        return decodeList(new String(content, StandardCharsets.UTF_8), elementType);
    }

    protected final void writeValue(JsonOutput output, Object value) {
        if (value == null) {
            output.raw("null");
        } else if (value instanceof String || value instanceof Character || value instanceof Enum<?>) {
            output.string(value.toString());
        } else if (value instanceof Boolean || value instanceof Number) {
            output.raw(value.toString());
        } else if (value instanceof Map<?, ?> map) {
            output.raw('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) output.raw(',');
                first = false;
                output.string(String.valueOf(entry.getKey())).raw(':');
                writeValue(output, entry.getValue());
            }
            output.raw('}');
        } else if (value instanceof Iterable<?> iterable) {
            output.raw('[');
            boolean first = true;
            for (Object item : iterable) {
                if (!first) output.raw(',');
                first = false;
                writeValue(output, item);
            }
            output.raw(']');
        } else {
            typeCodec(value.getClass()).encode(output, value);
        }
    }

    @SuppressWarnings("unchecked")
    protected final <T> T readValue(PortableJsonParser parser, Class<T> type) {
        if (parser.peekNull()) {
            parser.readNull();
            return null;
        }
        Object value;
        if (type == String.class || type == Character.class) {
            value = parser.readString();
        } else if (type == Boolean.class || type == boolean.class) {
            value = parser.readBoolean();
        } else if (type == Integer.class || type == int.class) {
            value = (int) parser.readLong();
        } else if (type == Long.class || type == long.class) {
            value = parser.readLong();
        } else if (type == Double.class || type == double.class) {
            value = parser.readDouble();
        } else if (type == Float.class || type == float.class) {
            value = (float) parser.readDouble();
        } else if (type == Short.class || type == short.class) {
            value = (short) parser.readLong();
        } else if (type == Byte.class || type == byte.class) {
            value = (byte) parser.readLong();
        } else if (type == Object.class || type == Map.class || type == List.class) {
            value = parser.readValue();
        } else {
            value = typeCodec(type).decode(parser);
        }
        return (T) value;
    }

    protected final <T> List<T> readList(PortableJsonParser parser, Class<T> elementType) {
        if (parser.peekNull()) {
            parser.readNull();
            return null;
        }
        List<T> result = new ArrayList<>();
        parser.expect('[');
        while (!parser.peek(']')) {
            result.add(readValue(parser, elementType));
            if (!parser.peek(']')) parser.expect(',');
        }
        parser.expect(']');
        return result;
    }

    @SuppressWarnings("unchecked")
    private JsonTypeCodec<Object> typeCodec(Class<?> type) {
        JsonTypeCodec<?> codec = types.get(type);
        if (codec == null) throw new IllegalArgumentException("No generated JSON codec for " + type.getName());
        return (JsonTypeCodec<Object>) codec;
    }
}
