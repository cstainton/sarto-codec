package io.instanto.sarto.codec;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Ordered registry used when a client contract contains more than one representation. */
public final class ContentCodecs {
    private final List<ContentCodec> codecs = new ArrayList<>();

    public ContentCodecs register(ContentCodec codec) {
        codecs.add(Objects.requireNonNull(codec, "codec"));
        return this;
    }

    public ContentCodec require(String mediaType) {
        String normalized = normalize(mediaType);
        return codecs.stream()
                .filter(codec -> codec.supports(normalized))
                .findFirst()
                .orElseThrow(() -> new UnsupportedMediaTypeException(normalized));
    }

    public static String normalize(String mediaType) {
        if (mediaType == null || mediaType.isBlank()) return "application/octet-stream";
        int parameter = mediaType.indexOf(';');
        return (parameter < 0 ? mediaType : mediaType.substring(0, parameter)).trim().toLowerCase(Locale.ROOT);
    }
}
