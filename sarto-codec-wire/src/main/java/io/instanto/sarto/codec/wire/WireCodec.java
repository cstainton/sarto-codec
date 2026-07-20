package io.instanto.sarto.codec.wire;

import io.instanto.sarto.codec.ContentCodec;
import io.instanto.sarto.codec.ContentCodecs;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Media-type and generated-type registry for Protobuf/Wire payloads. */
public final class WireCodec implements ContentCodec {
    private final Map<Class<?>, WireTypeCodec<?>> types = new LinkedHashMap<>();

    public <T> WireCodec register(Class<T> type, WireTypeCodec<T> codec) {
        types.put(Objects.requireNonNull(type, "type"), Objects.requireNonNull(codec, "codec"));
        return this;
    }

    @Override
    public boolean supports(String mediaType) {
        String normalized = ContentCodecs.normalize(mediaType);
        return normalized.equals("application/protobuf")
                || normalized.equals("application/x-protobuf")
                || normalized.endsWith("+protobuf");
    }

    @Override
    public byte[] encodeContent(Object value) {
        Objects.requireNonNull(value, "value");
        return typeCodec(value.getClass()).encode(value);
    }

    @Override
    public <T> T decodeContent(byte[] content, Class<T> type) {
        return type.cast(typeCodec(type).decode(content));
    }

    @SuppressWarnings("unchecked")
    private WireTypeCodec<Object> typeCodec(Class<?> type) {
        WireTypeCodec<?> codec = types.get(type);
        if (codec == null) throw new IllegalArgumentException("No generated Wire codec for " + type.getName());
        return (WireTypeCodec<Object>) codec;
    }
}
