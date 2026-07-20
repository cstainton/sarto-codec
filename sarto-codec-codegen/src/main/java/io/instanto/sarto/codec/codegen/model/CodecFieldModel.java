package io.instanto.sarto.codec.codegen.model;

import java.util.Objects;

/** Representation-neutral description of one serializable Java property. */
public record CodecFieldModel(
    String serializedName,
    String javaName,
    String javaType,
    String accessor,
    String mutator,
    int protobufFieldNumber) {

  public CodecFieldModel(String serializedName, String javaName, String javaType) {
    this(serializedName, javaName, javaType, null, null, 0);
  }

  public CodecFieldModel {
    serializedName = requireText(serializedName, "serializedName");
    javaName = requireText(javaName, "javaName");
    javaType = requireText(javaType, "javaType");
    accessor = optionalText(accessor);
    mutator = optionalText(mutator);
    if (protobufFieldNumber < 0) {
      throw new IllegalArgumentException("protobufFieldNumber cannot be negative");
    }
  }

  public boolean list() {
    String type = javaType.trim();
    return type.startsWith("java.util.List<") || type.startsWith("List<");
  }

  public boolean array() {
    return javaType.trim().endsWith("[]");
  }

  public boolean repeated() {
    return list() || array();
  }

  public String elementType() {
    String type = javaType.trim();
    if (array()) return type.substring(0, type.length() - 2).trim();
    if (list()) return type.substring(type.indexOf('<') + 1, type.lastIndexOf('>')).trim();
    return type;
  }

  public String readAccessor() {
    if (accessor != null) return accessor;
    String prefix = javaType.equals("boolean") ? "is" : "get";
    return prefix + capitalized(javaName);
  }

  public String writeMutator() {
    return mutator != null ? mutator : "set" + capitalized(javaName);
  }

  private static String capitalized(String value) {
    return Character.toUpperCase(value.charAt(0)) + value.substring(1);
  }

  private static String requireText(String value, String name) {
    String text = Objects.requireNonNull(value, name).trim();
    if (text.isEmpty()) throw new IllegalArgumentException(name + " is required");
    return text;
  }

  private static String optionalText(String value) {
    if (value == null) return null;
    String text = value.trim();
    return text.isEmpty() ? null : text;
  }
}
