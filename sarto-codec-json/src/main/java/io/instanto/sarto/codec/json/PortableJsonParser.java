package io.instanto.sarto.codec.json;

import java.util.Objects;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal hand-coded JSON parser for use by generated {@code @Portable} codecs.
 *
 * <p>Extracted from {@link JsonEnvelopeSerializer} and promoted to a public class
 * so that APT-generated {@code ClassName_Codec} classes can import and use it directly.
 *
 * <p>TeaVM-safe: no external dependencies, no reflection.
 */
public class PortableJsonParser {
    public static final int DEFAULT_MAX_DEPTH = 256;
    public static final int DEFAULT_MAX_STRING_LENGTH = 1_048_576;

    private final String json;
    private final int maxDepth;
    private final int maxStringLength;
    private int pos;

    public PortableJsonParser(String json) {
        this(json, DEFAULT_MAX_DEPTH, DEFAULT_MAX_STRING_LENGTH);
    }

    public PortableJsonParser(String json, int maxDepth, int maxStringLength) {
        this.json = Objects.requireNonNull(json, "json");
        if (maxDepth < 1) {
            throw new IllegalArgumentException("maxDepth must be positive");
        }
        if (maxStringLength < 0) {
            throw new IllegalArgumentException("maxStringLength must be non-negative");
        }
        this.maxDepth = maxDepth;
        this.maxStringLength = maxStringLength;
    }

    public void skipWhitespace() {
        while (pos < json.length() && isJsonWhitespace(json.charAt(pos))) pos++;
    }

    public void expect(char c) {
        skipWhitespace();
        if (pos >= json.length() || json.charAt(pos) != c) {
            throw new IllegalArgumentException(
                    "Expected '" + c + "' at pos " + pos + " but got: " + currentChar());
        }
        pos++;
    }

    public boolean peek(char c) {
        skipWhitespace();
        return pos < json.length() && json.charAt(pos) == c;
    }

    public boolean peekNull() {
        skipWhitespace();
        return json.startsWith("null", pos);
    }

    public void readNull() {
        readLiteral("null");
    }

    public void expectEnd() {
        skipWhitespace();
        if (pos != json.length()) {
            throw new IllegalArgumentException("Expected end of JSON at pos " + pos);
        }
    }

    public char currentChar() {
        return pos < json.length() ? json.charAt(pos) : 0;
    }

    public int position() {
        return pos;
    }

    public String readString() {
        skipWhitespace();
        expect('"');
        StringBuilder sb = new StringBuilder();
        boolean closed = false;
        while (pos < json.length()) {
            char c = json.charAt(pos++);
            if (c == '"') {
                closed = true;
                break;
            }
            if (c < 0x20) {
                throw new IllegalArgumentException("Unescaped control character in string at pos " + (pos - 1));
            }
            if (c == '\\') {
                if (pos >= json.length()) {
                    throw new IllegalArgumentException("Unterminated escape sequence at pos " + pos);
                }
                char esc = json.charAt(pos++);
                switch (esc) {
                    case '"'  -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/'  -> sb.append('/');
                    case 'b'  -> sb.append('\b');
                    case 'f'  -> sb.append('\f');
                    case 'n'  -> sb.append('\n');
                    case 'r'  -> sb.append('\r');
                    case 't'  -> sb.append('\t');
                    case 'u'  -> sb.append(readUnicodeEscape());
                    default   -> throw new IllegalArgumentException("Invalid escape sequence at pos " + (pos - 1));
                }
            } else {
                sb.append(c);
            }
            if (sb.length() > maxStringLength) {
                throw new IllegalArgumentException("JSON string exceeds maximum length " + maxStringLength);
            }
        }
        if (!closed) {
            throw new IllegalArgumentException("Unterminated string at pos " + pos);
        }
        return sb.toString();
    }

    public String readStringOrNull() {
        skipWhitespace();
        if (peekNull()) {
            readNull();
            return null;
        }
        return readString();
    }

    public boolean readBoolean() {
        skipWhitespace();
        if (json.startsWith("true", pos))  { readLiteral("true"); return true; }
        if (json.startsWith("false", pos)) { readLiteral("false"); return false; }
        throw new IllegalArgumentException("Expected boolean at pos " + pos);
    }

    public long readLong() {
        String token = readNumberToken();
        if (token.indexOf('.') >= 0 || token.indexOf('e') >= 0 || token.indexOf('E') >= 0) {
            throw new NumberFormatException("Expected integer but got " + token);
        }
        return Long.parseLong(token);
    }

    public double readDouble() {
        return Double.parseDouble(readNumberToken());
    }

    /** Reads JSON into ordinary strings, numbers, booleans, lists, and maps. */
    public Object readValue() {
        skipWhitespace();
        char c = currentChar();
        if (c == '"') return readString();
        if (c == '{') {
            Map<String, Object> value = new LinkedHashMap<>();
            expect('{');
            while (!peek('}')) {
                String key = readString();
                expect(':');
                value.put(key, readValue());
                if (!peek('}')) expect(',');
            }
            expect('}');
            return value;
        }
        if (c == '[') {
            List<Object> value = new ArrayList<>();
            expect('[');
            while (!peek(']')) {
                value.add(readValue());
                if (!peek(']')) expect(',');
            }
            expect(']');
            return value;
        }
        if (c == 't' || c == 'f') return readBoolean();
        if (c == 'n') {
            readNull();
            return null;
        }
        String number = readNumberToken();
        if (number.indexOf('.') >= 0 || number.indexOf('e') >= 0 || number.indexOf('E') >= 0) {
            return Double.parseDouble(number);
        }
        long integer = Long.parseLong(number);
        return integer >= Integer.MIN_VALUE && integer <= Integer.MAX_VALUE ? (int) integer : integer;
    }

    public void skipValue() {
        skipValue(0);
    }

    private void skipValue(int depth) {
        skipWhitespace();
        char c = currentChar();
        if (c == '"') { readString(); return; }
        if (c == '{') { skipObject(depth); return; }
        if (c == '[') { skipArray(depth); return; }
        if (c == 't') { readLiteral("true"); return; }
        if (c == 'f') { readLiteral("false"); return; }
        if (c == 'n') { readLiteral("null"); return; }
        if (c == '-' || Character.isDigit(c)) {
            readNumberToken();
            return;
        }
        throw new IllegalArgumentException("Expected JSON value at pos " + pos);
    }

    public void skipObject() {
        skipObject(0);
    }

    private void skipObject(int depth) {
        if (depth >= maxDepth) {
            throw new IllegalArgumentException("JSON nesting exceeds maximum depth " + maxDepth);
        }
        expect('{');
        while (!peek('}')) {
            readString(); expect(':'); skipValue(depth + 1);
            if (!peek('}')) expect(',');
        }
        expect('}');
    }

    public void skipArray() {
        skipArray(0);
    }

    private void skipArray(int depth) {
        if (depth >= maxDepth) {
            throw new IllegalArgumentException("JSON nesting exceeds maximum depth " + maxDepth);
        }
        expect('[');
        while (!peek(']')) {
            skipValue(depth + 1);
            if (!peek(']')) expect(',');
        }
        expect(']');
    }

    /**
     * Reads a JSON array of strings, e.g. {@code ["abc","def"]}.
     * Returns an empty array for {@code null} or {@code []}.
     */
    public String[] readStringArray() {
        skipWhitespace();
        if (peekNull()) { readNull(); return new String[0]; }
        expect('[');
        java.util.List<String> list = new java.util.ArrayList<>();
        while (!peek(']')) {
            list.add(readStringOrNull());
            if (!peek(']')) expect(',');
        }
        expect(']');
        return list.toArray(new String[0]);
    }

    /**
     * Escapes a string for embedding in a JSON value.
     * Static so generated codecs can call {@code PortableJsonParser.escapeJson(val)}.
     */
    public static String escapeJson(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"'  -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default   -> {
                    if (c < 0x20) {
                        sb.append("\\u");
                        String hex = Integer.toHexString(c);
                        for (int i = hex.length(); i < 4; i++) {
                            sb.append('0');
                        }
                        sb.append(hex);
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }

    private void readLiteral(String literal) {
        skipWhitespace();
        if (!json.startsWith(literal, pos)) {
            throw new IllegalArgumentException("Expected '" + literal + "' at pos " + pos);
        }
        int end = pos + literal.length();
        if (end < json.length() && !isValueTerminator(json.charAt(end))) {
            throw new IllegalArgumentException("Invalid literal terminator at pos " + end);
        }
        pos = end;
    }

    private String readNumberToken() {
        skipWhitespace();
        int start = pos;
        if (pos < json.length() && json.charAt(pos) == '-') {
            pos++;
        }
        if (pos >= json.length()) {
            throw new NumberFormatException("Expected number at pos " + start);
        }
        if (json.charAt(pos) == '0') {
            pos++;
            if (pos < json.length() && Character.isDigit(json.charAt(pos))) {
                throw new NumberFormatException("Invalid leading zero at pos " + start);
            }
        } else if (isDigitOneToNine(json.charAt(pos))) {
            pos++;
            while (pos < json.length() && Character.isDigit(json.charAt(pos))) {
                pos++;
            }
        } else {
            throw new NumberFormatException("Expected number at pos " + start);
        }
        if (pos < json.length() && json.charAt(pos) == '.') {
            pos++;
            int fractionStart = pos;
            while (pos < json.length() && Character.isDigit(json.charAt(pos))) {
                pos++;
            }
            if (pos == fractionStart) {
                throw new NumberFormatException("Expected fraction digits at pos " + fractionStart);
            }
        }
        if (pos < json.length() && (json.charAt(pos) == 'e' || json.charAt(pos) == 'E')) {
            pos++;
            if (pos < json.length() && (json.charAt(pos) == '+' || json.charAt(pos) == '-')) {
                pos++;
            }
            int exponentStart = pos;
            while (pos < json.length() && Character.isDigit(json.charAt(pos))) {
                pos++;
            }
            if (pos == exponentStart) {
                throw new NumberFormatException("Expected exponent digits at pos " + exponentStart);
            }
        }
        if (pos < json.length() && !isValueTerminator(json.charAt(pos))) {
            throw new NumberFormatException("Invalid number terminator at pos " + pos);
        }
        return json.substring(start, pos);
    }

    private char readUnicodeEscape() {
        if (pos + 4 > json.length()) {
            throw new IllegalArgumentException("Truncated unicode escape at pos " + pos);
        }
        int value = 0;
        for (int i = 0; i < 4; i++) {
            int hex = hexValue(json.charAt(pos++));
            if (hex < 0) {
                throw new IllegalArgumentException("Invalid unicode escape at pos " + (pos - 1));
            }
            value = (value << 4) + hex;
        }
        return (char) value;
    }

    private static int hexValue(char c) {
        if (c >= '0' && c <= '9') return c - '0';
        if (c >= 'a' && c <= 'f') return 10 + c - 'a';
        if (c >= 'A' && c <= 'F') return 10 + c - 'A';
        return -1;
    }

    private static boolean isDigitOneToNine(char c) {
        return c >= '1' && c <= '9';
    }

    private static boolean isJsonWhitespace(char c) {
        return c == ' ' || c == '\t' || c == '\n' || c == '\r';
    }

    private static boolean isValueTerminator(char c) {
        return isJsonWhitespace(c) || c == ',' || c == '}' || c == ']';
    }
}
