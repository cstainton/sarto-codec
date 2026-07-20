package io.instanto.sarto.codec;

import java.util.List;

/** Encodes and decodes one family of media types without prescribing a transport. */
public interface ContentCodec {
    boolean supports(String mediaType);

    byte[] encodeContent(Object value);

    <T> T decodeContent(byte[] content, Class<T> type);

    default <T> List<T> decodeListContent(byte[] content, Class<T> elementType) {
        throw new UnsupportedOperationException("List decoding is not supported by " + getClass().getName());
    }
}
