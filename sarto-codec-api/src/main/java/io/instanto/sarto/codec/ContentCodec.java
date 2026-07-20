package io.instanto.sarto.codec;

import java.util.List;

/** Encodes and decodes one family of media types without prescribing a transport. */
public interface ContentCodec {
    boolean supports(String mediaType);

    String encode(Object value);

    <T> T decode(String content, Class<T> type);

    default <T> List<T> decodeList(String content, Class<T> elementType) {
        throw new UnsupportedOperationException("List decoding is not supported by " + getClass().getName());
    }
}
