package io.instanto.sarto.codec;

/** Signals that an API declares a representation for which no codec was supplied. */
public final class UnsupportedMediaTypeException extends IllegalStateException {
    private final String mediaType;

    public UnsupportedMediaTypeException(String mediaType) {
        super("No content codec is registered for " + mediaType);
        this.mediaType = mediaType;
    }

    public String mediaType() {
        return mediaType;
    }
}
