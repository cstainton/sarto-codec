package io.instanto.sarto.codec;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ContentCodecsTest {
    @Test
    void normalizesMediaTypesAndReportsMissingCodecs() {
        assertEquals("application/problem+json", ContentCodecs.normalize("Application/Problem+JSON; charset=UTF-8"));

        UnsupportedMediaTypeException failure = assertThrows(UnsupportedMediaTypeException.class,
                () -> new ContentCodecs().require("application/cbor"));
        assertEquals("application/cbor", failure.mediaType());
    }
}
