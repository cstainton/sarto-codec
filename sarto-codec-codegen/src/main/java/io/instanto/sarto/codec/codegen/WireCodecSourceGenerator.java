package io.instanto.sarto.codec.codegen;

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ArrayTypeName;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeVariableName;
import com.squareup.wire.FieldEncoding;
import com.squareup.wire.ProtoAdapter;
import com.squareup.wire.ProtoReader;
import com.squareup.wire.ProtoWriter;
import io.instanto.sarto.codec.codegen.model.CodecFieldModel;
import io.instanto.sarto.codec.codegen.model.CodecTypeModel;
import io.instanto.sarto.codec.codegen.model.ConstructionStyle;
import io.instanto.sarto.codec.wire.WireTypeCodec;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.lang.model.element.Modifier;
import okio.Buffer;

/** Emits reflection-free protobuf codecs using Square Wire's low-level primitives. */
public final class WireCodecSourceGenerator {

  public String generate(CodecTypeModel model, WireGenerationOptions options) {
    requireFieldNumbers(model);
    QualifiedType output = QualifiedType.parse(options.codecType());
    TypeName target = ClassName.bestGuess(model.javaType());
    TypeSpec.Builder codec =
        TypeSpec.classBuilder(output.simpleName())
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addSuperinterface(
                ParameterizedTypeName.get(ClassName.get(WireTypeCodec.class), target))
            .addJavadoc("Generated protobuf codec for {@link $T}.\nDO NOT EDIT.\n", target);
    for (String additionalInterface : options.additionalInterfaces()) {
      codec.addSuperinterface(ClassName.bestGuess(additionalInterface));
    }

    codec.addMethod(
        MethodSpec.methodBuilder("type")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(ParameterizedTypeName.get(ClassName.get(Class.class), target))
            .addStatement("return $T.class", target)
            .build());
    codec.addMethod(encodeMethod(target));
    codec.addMethod(decodeMethod(target));
    codec.addMethod(encodeFieldsMethod(model, target));
    codec.addMethod(encodedSizeMethod(model, target));
    codec.addMethod(decodeMessageMethod(model, target));
    codec.addMethod(varintSizeMethod());
    codec.addMethod(tagSizeMethod());
    if (options.generateGenericFacade()) addGenericFacade(codec, target, options.contentType());

    return JavaFile.builder(output.packageName(), codec.build()).indent("    ").build().toString();
  }

  private static MethodSpec encodeMethod(TypeName target) {
    return MethodSpec.methodBuilder("encode")
        .addAnnotation(Override.class)
        .addModifiers(Modifier.PUBLIC)
        .returns(byte[].class)
        .addParameter(target, "value")
        .beginControlFlow("try")
        .addStatement("$T buffer = new $T()", Buffer.class, Buffer.class)
        .addStatement("encodeFields(new $T(buffer), value)", ProtoWriter.class)
        .addStatement("return buffer.readByteArray()")
        .nextControlFlow("catch ($T error)", IOException.class)
        .addStatement("throw new $T($S, error)", RuntimeException.class, "Protobuf encode failed")
        .endControlFlow()
        .build();
  }

  private static MethodSpec decodeMethod(TypeName target) {
    return MethodSpec.methodBuilder("decode")
        .addAnnotation(Override.class)
        .addModifiers(Modifier.PUBLIC)
        .returns(target)
        .addParameter(byte[].class, "content")
        .beginControlFlow("try")
        .addStatement("$T buffer = new $T()", Buffer.class, Buffer.class)
        .addStatement("buffer.write(content)")
        .addStatement("return decodeMessage(new $T(buffer))", ProtoReader.class)
        .nextControlFlow("catch ($T error)", IOException.class)
        .addStatement("throw new $T($S, error)", RuntimeException.class, "Protobuf decode failed")
        .endControlFlow()
        .build();
  }

  private static MethodSpec encodeFieldsMethod(CodecTypeModel model, TypeName target) {
    MethodSpec.Builder method =
        MethodSpec.methodBuilder("encodeFields")
            .addModifiers(Modifier.PUBLIC)
            .returns(TypeName.VOID)
            .addParameter(ProtoWriter.class, "writer")
            .addParameter(target, "value")
            .addException(IOException.class)
            .addStatement("if (value == null) return");
    for (CodecFieldModel field : model.fields())
      addEncodeField(method, field, model.construction());
    return method.build();
  }

  private static void addEncodeField(
      MethodSpec.Builder method, CodecFieldModel field, ConstructionStyle construction) {
    String expression =
        "value."
            + (construction == ConstructionStyle.RECORD ? field.javaName() : field.readAccessor())
            + "()";
    if (field.repeated()) {
      method
          .addStatement("var field_$L = $L", field.javaName(), expression)
          .beginControlFlow("if (field_$L != null)", field.javaName());
      if (field.array()) {
        method.beginControlFlow("for (var item : field_$L)", field.javaName());
      } else {
        method.beginControlFlow("for (var item : field_$L)", field.javaName());
      }
      if (!SourceTypes.erased(field.elementType()).isPrimitive()) {
        method.beginControlFlow("if (item != null)");
        method.addCode(encodeValue(field.elementType(), "item", field.protobufFieldNumber()));
        method.endControlFlow();
      } else {
        method.addCode(encodeValue(field.elementType(), "item", field.protobufFieldNumber()));
      }
      method.endControlFlow().endControlFlow();
    } else if (SourceTypes.erased(field.javaType()).isPrimitive()) {
      method.addCode(
          encodePrimitiveIfNonDefault(field.javaType(), expression, field.protobufFieldNumber()));
    } else {
      method
          .beginControlFlow("if ($L != null)", expression)
          .addCode(encodeValue(field.javaType(), expression, field.protobufFieldNumber()))
          .endControlFlow();
    }
  }

  private static CodeBlock encodePrimitiveIfNonDefault(String type, String expression, int tag) {
    String condition =
        switch (type) {
          case "boolean" -> expression;
          case "long" -> expression + " != 0L";
          case "float" -> expression + " != 0.0f";
          case "double" -> expression + " != 0.0d";
          default -> expression + " != 0";
        };
    return CodeBlock.builder()
        .beginControlFlow("if ($L)", condition)
        .add(encodeValue(type, expression, tag))
        .endControlFlow()
        .build();
  }

  private static CodeBlock encodeValue(String type, String expression, int tag) {
    CodeBlock.Builder code = CodeBlock.builder();
    switch (type) {
      case "java.lang.String" ->
          code.addStatement(
              "$T.STRING.encodeWithTag(writer, $L, $L)", ProtoAdapter.class, tag, expression);
      case "byte", "short", "int", "java.lang.Byte", "java.lang.Short", "java.lang.Integer" ->
          code.addStatement(
              "$T.INT32.encodeWithTag(writer, $L, $L)", ProtoAdapter.class, tag, expression);
      case "long", "java.lang.Long" ->
          code.addStatement(
              "$T.INT64.encodeWithTag(writer, $L, $L)", ProtoAdapter.class, tag, expression);
      case "boolean", "java.lang.Boolean" ->
          code.addStatement(
              "$T.BOOL.encodeWithTag(writer, $L, $L)", ProtoAdapter.class, tag, expression);
      case "double", "java.lang.Double" ->
          code.addStatement(
              "$T.DOUBLE.encodeWithTag(writer, $L, $L)", ProtoAdapter.class, tag, expression);
      case "float", "java.lang.Float" ->
          code.addStatement(
              "$T.FLOAT.encodeWithTag(writer, $L, $L)", ProtoAdapter.class, tag, expression);
      default ->
          code.addStatement("var nestedCodec = new $T()", nestedCodecType(type))
              .addStatement("int nestedSize = nestedCodec.encodedSizeNoTag($L)", expression)
              .addStatement("writer.writeTag($L, $T.LENGTH_DELIMITED)", tag, FieldEncoding.class)
              .addStatement("writer.writeVarint32(nestedSize)")
              .addStatement("nestedCodec.encodeFields(writer, $L)", expression);
    }
    return code.build();
  }

  private static MethodSpec encodedSizeMethod(CodecTypeModel model, TypeName target) {
    MethodSpec.Builder method =
        MethodSpec.methodBuilder("encodedSizeNoTag")
            .addModifiers(Modifier.PUBLIC)
            .returns(TypeName.INT)
            .addParameter(target, "value")
            .addStatement("if (value == null) return 0")
            .addStatement("int size = 0");
    for (CodecFieldModel field : model.fields()) addSizeField(method, field, model.construction());
    return method.addStatement("return size").build();
  }

  private static void addSizeField(
      MethodSpec.Builder method, CodecFieldModel field, ConstructionStyle construction) {
    String expression =
        "value."
            + (construction == ConstructionStyle.RECORD ? field.javaName() : field.readAccessor())
            + "()";
    if (field.repeated()) {
      method
          .addStatement("var field_$L = $L", field.javaName(), expression)
          .beginControlFlow("if (field_$L != null)", field.javaName())
          .beginControlFlow("for (var item : field_$L)", field.javaName());
      if (!SourceTypes.erased(field.elementType()).isPrimitive())
        method.beginControlFlow("if (item != null)");
      method.addCode(sizeValue(field.elementType(), "item", field.protobufFieldNumber()));
      if (!SourceTypes.erased(field.elementType()).isPrimitive()) method.endControlFlow();
      method.endControlFlow().endControlFlow();
    } else if (SourceTypes.erased(field.javaType()).isPrimitive()) {
      String condition =
          switch (field.javaType()) {
            case "boolean" -> expression;
            case "long" -> expression + " != 0L";
            case "float" -> expression + " != 0.0f";
            case "double" -> expression + " != 0.0d";
            default -> expression + " != 0";
          };
      method
          .beginControlFlow("if ($L)", condition)
          .addCode(sizeValue(field.javaType(), expression, field.protobufFieldNumber()))
          .endControlFlow();
    } else {
      method
          .beginControlFlow("if ($L != null)", expression)
          .addCode(sizeValue(field.javaType(), expression, field.protobufFieldNumber()))
          .endControlFlow();
    }
  }

  private static CodeBlock sizeValue(String type, String expression, int tag) {
    CodeBlock.Builder code = CodeBlock.builder();
    switch (type) {
      case "java.lang.String" ->
          code.addStatement(
              "size += $T.STRING.encodedSizeWithTag($L, $L)", ProtoAdapter.class, tag, expression);
      case "byte", "short", "int", "java.lang.Byte", "java.lang.Short", "java.lang.Integer" ->
          code.addStatement(
              "size += $T.INT32.encodedSizeWithTag($L, $L)", ProtoAdapter.class, tag, expression);
      case "long", "java.lang.Long" ->
          code.addStatement(
              "size += $T.INT64.encodedSizeWithTag($L, $L)", ProtoAdapter.class, tag, expression);
      case "boolean", "java.lang.Boolean" ->
          code.addStatement(
              "size += $T.BOOL.encodedSizeWithTag($L, $L)", ProtoAdapter.class, tag, expression);
      case "double", "java.lang.Double" ->
          code.addStatement(
              "size += $T.DOUBLE.encodedSizeWithTag($L, $L)", ProtoAdapter.class, tag, expression);
      case "float", "java.lang.Float" ->
          code.addStatement(
              "size += $T.FLOAT.encodedSizeWithTag($L, $L)", ProtoAdapter.class, tag, expression);
      default ->
          code.addStatement(
                  "int nestedSize = new $T().encodedSizeNoTag($L)",
                  nestedCodecType(type),
                  expression)
              .addStatement("size += tagSize($L) + varint32Size(nestedSize) + nestedSize", tag);
    }
    return code.build();
  }

  private static MethodSpec decodeMessageMethod(CodecTypeModel model, TypeName target) {
    MethodSpec.Builder method =
        MethodSpec.methodBuilder("decodeMessage")
            .addModifiers(Modifier.PUBLIC)
            .returns(target)
            .addParameter(ProtoReader.class, "reader")
            .addException(IOException.class);
    if (model.construction() == ConstructionStyle.BEAN) {
      method.addStatement("$T value = new $T()", target, target);
    } else {
      for (CodecFieldModel field : model.fields()) {
        method.addStatement(
            "$T $N = $L",
            SourceTypes.typeName(field.javaType()),
            field.javaName(),
            defaultValue(field.javaType()));
      }
    }
    for (CodecFieldModel field : model.fields()) {
      if (field.repeated()) {
        method.addStatement(
            "$T<$T> list_$L = new $T<>()",
            List.class,
            SourceTypes.boxed(field.elementType()),
            field.javaName(),
            ArrayList.class);
      }
    }
    method
        .addStatement("long token = reader.beginMessage()")
        .beginControlFlow("for (int tag; (tag = reader.nextTag()) != -1; )")
        .beginControlFlow("switch (tag)");
    for (CodecFieldModel field : model.fields()) {
      method.addCode("case $L:\n$>", field.protobufFieldNumber());
      if (field.repeated()) {
        method.addStatement("list_$L.add($L)", field.javaName(), decodeValue(field.elementType()));
      } else if (model.construction() == ConstructionStyle.RECORD) {
        method.addStatement("$N = $L", field.javaName(), decodeValue(field.javaType()));
      } else {
        method.addStatement("value.$L($L)", field.writeMutator(), decodeValue(field.javaType()));
      }
      method.addStatement("break").addCode("$<");
    }
    method
        .addCode("default:\n$>")
        .addStatement("reader.readUnknownField(tag)")
        .addStatement("break")
        .addCode("$<")
        .endControlFlow()
        .endControlFlow()
        .addStatement("reader.endMessage(token)");
    for (CodecFieldModel field : model.fields()) {
      if (!field.repeated()) continue;
      if (field.array() && SourceTypes.erased(field.elementType()).isPrimitive()) {
        TypeName component = SourceTypes.typeName(field.elementType());
        method
            .addStatement(
                "$T array_$L = new $T[list_$L.size()]",
                ArrayTypeName.of(component),
                field.javaName(),
                component,
                field.javaName())
            .beginControlFlow(
                "for (int index = 0; index < list_$L.size(); index++)", field.javaName())
            .addStatement(
                "array_$L[index] = list_$L.get(index)", field.javaName(), field.javaName())
            .endControlFlow();
        if (model.construction() == ConstructionStyle.RECORD) {
          method.addStatement("$N = array_$L", field.javaName(), field.javaName());
        } else {
          method.addStatement("value.$L(array_$L)", field.writeMutator(), field.javaName());
        }
      } else {
        CodeBlock value = repeatedResult(field);
        if (model.construction() == ConstructionStyle.RECORD) {
          method.addStatement("$N = $L", field.javaName(), value);
        } else {
          method.addStatement("value.$L($L)", field.writeMutator(), value);
        }
      }
    }
    if (model.construction() == ConstructionStyle.RECORD) {
      CodeBlock.Builder values = CodeBlock.builder();
      for (int index = 0; index < model.fields().size(); index++) {
        if (index > 0) values.add(", ");
        values.add("$N", model.fields().get(index).javaName());
      }
      method.addStatement("return new $T($L)", target, values.build());
    } else {
      method.addStatement("return value");
    }
    return method.build();
  }

  private static CodeBlock repeatedResult(CodecFieldModel field) {
    if (field.list()) return CodeBlock.of("list_$L", field.javaName());
    TypeName component = SourceTypes.typeName(field.elementType());
    if (!component.isPrimitive()) {
      return CodeBlock.of("list_$L.toArray(new $T[0])", field.javaName(), component);
    }
    throw new IllegalStateException("Primitive arrays are assigned as statements");
  }

  private static CodeBlock decodeValue(String type) {
    return switch (type) {
      case "java.lang.String" -> CodeBlock.of("$T.STRING.decode(reader)", ProtoAdapter.class);
      case "byte", "java.lang.Byte" ->
          CodeBlock.of("(byte) (int) $T.INT32.decode(reader)", ProtoAdapter.class);
      case "short", "java.lang.Short" ->
          CodeBlock.of("(short) (int) $T.INT32.decode(reader)", ProtoAdapter.class);
      case "int", "java.lang.Integer" ->
          CodeBlock.of("$T.INT32.decode(reader)", ProtoAdapter.class);
      case "long", "java.lang.Long" -> CodeBlock.of("$T.INT64.decode(reader)", ProtoAdapter.class);
      case "boolean", "java.lang.Boolean" ->
          CodeBlock.of("$T.BOOL.decode(reader)", ProtoAdapter.class);
      case "double", "java.lang.Double" ->
          CodeBlock.of("$T.DOUBLE.decode(reader)", ProtoAdapter.class);
      case "float", "java.lang.Float" ->
          CodeBlock.of("$T.FLOAT.decode(reader)", ProtoAdapter.class);
      default -> CodeBlock.of("new $T().decodeMessage(reader)", nestedCodecType(type));
    };
  }

  private static void addGenericFacade(
      TypeSpec.Builder codec, TypeName target, String contentType) {
    codec.addMethod(
        MethodSpec.methodBuilder("contentType")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(String.class)
            .addStatement("return $S", contentType)
            .build());
    codec.addMethod(
        MethodSpec.methodBuilder("supports")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(TypeName.BOOLEAN)
            .addParameter(
                ParameterizedTypeName.get(
                    ClassName.get(Class.class),
                    com.squareup.javapoet.WildcardTypeName.subtypeOf(Object.class)),
                "candidate")
            .addStatement("return $T.class.equals(candidate)", target)
            .build());
    TypeVariableName value = TypeVariableName.get("V");
    codec.addMethod(
        MethodSpec.methodBuilder("encode")
            .addAnnotation(Override.class)
            .addAnnotation(
                AnnotationSpec.builder(SuppressWarnings.class)
                    .addMember("value", "$S", "unchecked")
                    .build())
            .addModifiers(Modifier.PUBLIC)
            .addTypeVariable(value)
            .returns(byte[].class)
            .addParameter(value, "value")
            .addParameter(ParameterizedTypeName.get(ClassName.get(Class.class), value), "candidate")
            .addStatement("return encode(($T) value)", target)
            .build());
    codec.addMethod(
        MethodSpec.methodBuilder("decode")
            .addAnnotation(Override.class)
            .addAnnotation(
                AnnotationSpec.builder(SuppressWarnings.class)
                    .addMember("value", "$S", "unchecked")
                    .build())
            .addModifiers(Modifier.PUBLIC)
            .addTypeVariable(value)
            .returns(value)
            .addParameter(byte[].class, "content")
            .addParameter(ParameterizedTypeName.get(ClassName.get(Class.class), value), "candidate")
            .addStatement("return (V) decode(content)")
            .build());
  }

  private static MethodSpec varintSizeMethod() {
    return MethodSpec.methodBuilder("varint32Size")
        .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
        .returns(TypeName.INT)
        .addParameter(TypeName.INT, "value")
        .addStatement("if ((value & ~0x7F) == 0) return 1")
        .addStatement("if ((value & ~0x3FFF) == 0) return 2")
        .addStatement("if ((value & ~0x1FFFFF) == 0) return 3")
        .addStatement("if ((value & ~0xFFFFFFF) == 0) return 4")
        .addStatement("return 5")
        .build();
  }

  private static MethodSpec tagSizeMethod() {
    return MethodSpec.methodBuilder("tagSize")
        .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
        .returns(TypeName.INT)
        .addParameter(TypeName.INT, "fieldNumber")
        .addStatement("return varint32Size(fieldNumber << 3)")
        .build();
  }

  private static CodeBlock defaultValue(String type) {
    return switch (type.trim()) {
      case "boolean" -> CodeBlock.of("false");
      case "byte", "short", "int", "long", "char" -> CodeBlock.of("0");
      case "float" -> CodeBlock.of("0.0f");
      case "double" -> CodeBlock.of("0.0d");
      default -> CodeBlock.of("null");
    };
  }

  private static ClassName nestedCodecType(String javaType) {
    String raw = javaType;
    int generic = raw.indexOf('<');
    if (generic >= 0) raw = raw.substring(0, generic);
    String[] parts = raw.split("\\.");
    int firstClassPart = 0;
    while (firstClassPart < parts.length
        && (parts[firstClassPart].isEmpty()
            || !Character.isUpperCase(parts[firstClassPart].charAt(0)))) {
      firstClassPart++;
    }
    if (firstClassPart == 0 || firstClassPart == parts.length) {
      int separator = raw.lastIndexOf('.');
      return ClassName.get(
          raw.substring(0, separator), raw.substring(separator + 1) + "_WireCodec");
    }
    String packageName = String.join(".", java.util.Arrays.copyOfRange(parts, 0, firstClassPart));
    String generatedBase =
        String.join("_", java.util.Arrays.copyOfRange(parts, firstClassPart, parts.length));
    return ClassName.get(packageName, generatedBase + "_WireCodec");
  }

  private static void requireFieldNumbers(CodecTypeModel model) {
    for (CodecFieldModel field : model.fields()) {
      if (field.protobufFieldNumber() <= 0) {
        throw new IllegalArgumentException(
            model.javaType() + "." + field.javaName() + " needs an explicit protobuf field number");
      }
    }
  }

  private record QualifiedType(String packageName, String simpleName) {
    static QualifiedType parse(String value) {
      int separator = value.lastIndexOf('.');
      if (separator <= 0 || separator == value.length() - 1) {
        throw new IllegalArgumentException("codecType must be fully qualified");
      }
      return new QualifiedType(value.substring(0, separator), value.substring(separator + 1));
    }
  }
}
