package io.instanto.sarto.codec.wire;

/** Reflection-free binary mapping generated for one message type. */
public interface WireTypeCodec<T> {
    byte[] encode(T value);

    T decode(byte[] content);
}
