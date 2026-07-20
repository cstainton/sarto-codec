package io.instanto.sarto.codec.json;

/** Small JSON output used by generated codecs. */
public final class JsonOutput {
    private final StringBuilder value = new StringBuilder();

    public JsonOutput raw(char character) {
        value.append(character);
        return this;
    }

    public JsonOutput raw(String text) {
        value.append(text);
        return this;
    }

    public JsonOutput string(String text) {
        if (text == null) return raw("null");
        value.append('"').append(PortableJsonParser.escapeJson(text)).append('"');
        return this;
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
