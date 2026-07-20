package io.instanto.sarto.codec.json;

/** Reflection-free mapping between one Java type and JSON. */
public interface JsonTypeCodec<T> {
    void encode(JsonOutput output, T value);

    T decode(PortableJsonParser parser);
}
