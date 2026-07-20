package io.instanto.sarto.codec.codegen.model;

import java.util.List;
import java.util.Objects;

/** Caller-neutral Java type description consumed by all codec generators. */
public record CodecTypeModel(
    String javaType, List<CodecFieldModel> fields, ConstructionStyle construction) {
  public CodecTypeModel(String javaType, List<CodecFieldModel> fields) {
    this(javaType, fields, ConstructionStyle.BEAN);
  }

  public CodecTypeModel {
    javaType = Objects.requireNonNull(javaType, "javaType").trim();
    if (javaType.isEmpty()) throw new IllegalArgumentException("javaType is required");
    fields = List.copyOf(Objects.requireNonNull(fields, "fields"));
    construction = Objects.requireNonNull(construction, "construction");
    validateProtobufNumbers(fields);
  }

  public String packageName() {
    return javaType.substring(0, javaType.lastIndexOf('.'));
  }

  public String simpleName() {
    return javaType.substring(javaType.lastIndexOf('.') + 1).replace('.', '_');
  }

  private static void validateProtobufNumbers(List<CodecFieldModel> fields) {
    java.util.Set<Integer> used = new java.util.HashSet<>();
    for (CodecFieldModel field : fields) {
      int number = field.protobufFieldNumber();
      if (number > 0 && !used.add(number)) {
        throw new IllegalArgumentException("Duplicate protobuf field number " + number);
      }
    }
  }
}
