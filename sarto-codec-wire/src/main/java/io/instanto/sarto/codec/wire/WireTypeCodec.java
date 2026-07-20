package io.instanto.sarto.codec.wire;

/** Reflection-free binary mapping generated for one message type. */
public interface WireTypeCodec<T> {
    Class<T> type();

    byte[] encode(T value);

    T decode(byte[] content);
}
