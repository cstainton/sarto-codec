package io.instanto.sarto.codec.codegen;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import io.instanto.sarto.codec.codegen.model.CodecFieldModel;
import io.instanto.sarto.codec.codegen.model.CodecTypeModel;
import io.instanto.sarto.codec.codegen.model.ConstructionStyle;
import io.instanto.sarto.codec.json.JsonCodec;
import io.instanto.sarto.codec.json.JsonOutput;
import io.instanto.sarto.codec.json.JsonTypeCodec;
import io.instanto.sarto.codec.json.PortableJsonParser;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import javax.lang.model.element.Modifier;

/** Generates a reflection-free JSON registry and one mapping for every supplied Java type. */
public final class JsonCodecSourceGenerator {

  public String generateRegistry(String codecType, Collection<CodecTypeModel> models) {
    return generateRegistry(codecType, models, List.of());
  }

  /** Additional interfaces let a caller expose its own facade without moving that facade here. */
  public String generateRegistry(
      String codecType,
      Collection<CodecTypeModel> models,
      Collection<String> additionalInterfaces) {
    Objects.requireNonNull(models, "models");
    QualifiedType codec = QualifiedType.parse(codecType);
    TypeSpec.Builder registry =
        TypeSpec.classBuilder(codec.simpleName())
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .superclass(JsonCodec.class)
            .addJavadoc("Generated reflection-free JSON mappings.\nDO NOT EDIT.\n");
    for (String additionalInterface : additionalInterfaces) {
      registry.addSuperinterface(ClassName.bestGuess(additionalInterface));
    }

    MethodSpec.Builder constructor = MethodSpec.constructorBuilder().addModifiers(Modifier.PUBLIC);
    for (CodecTypeModel model : models) {
      constructor.addStatement(
          "register($T.class, new $LJsonTypeCodec())",
          ClassName.bestGuess(model.javaType()),
          model.simpleName());
      registry.addType(jsonTypeCodec(model));
    }
    registry.addMethod(constructor.build());
    return JavaFile.builder(codec.packageName(), registry.build())
        .skipJavaLangImports(true)
        .build()
        .toString();
  }

  private static TypeSpec jsonTypeCodec(CodecTypeModel model) {
    TypeName valueType = ClassName.bestGuess(model.javaType());
    TypeSpec.Builder codec =
        TypeSpec.classBuilder(model.simpleName() + "JsonTypeCodec")
            .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
            .addSuperinterface(
                ParameterizedTypeName.get(ClassName.get(JsonTypeCodec.class), valueType));

    MethodSpec.Builder encode =
        MethodSpec.methodBuilder("encode")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .addParameter(JsonOutput.class, "output")
            .addParameter(valueType, "value")
            .addStatement("output.raw('{')");
    for (int index = 0; index < model.fields().size(); index++) {
      CodecFieldModel field = model.fields().get(index);
      if (index > 0) encode.addStatement("output.raw(',')");
      encode.addStatement("output.string($S).raw(':')", field.serializedName());
      encode.addStatement(
          "writeValue(output, value.$L())",
          model.construction() == ConstructionStyle.RECORD
              ? field.javaName()
              : field.readAccessor());
    }
    encode.addStatement("output.raw('}')");
    codec.addMethod(encode.build());

    MethodSpec.Builder decode =
        MethodSpec.methodBuilder("decode")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(valueType)
            .addParameter(PortableJsonParser.class, "parser");
    if (model.construction() == ConstructionStyle.RECORD) {
      for (CodecFieldModel field : model.fields()) {
        decode.addStatement(
            "$T $N = $L",
            SourceTypes.typeName(field.javaType()),
            field.javaName(),
            defaultValue(field.javaType()));
      }
    } else {
      decode.addStatement("$T value = new $T()", valueType, valueType);
    }
    decode
        .addStatement("parser.expect('{')")
        .beginControlFlow("while (!parser.peek('}'))")
        .addStatement("String field = parser.readString()")
        .addStatement("parser.expect(':')")
        .beginControlFlow("switch (field)");
    for (CodecFieldModel field : model.fields()) {
      decode.addCode("case $S -> ", field.serializedName());
      CodeBlock decoded =
          field.list()
              ? CodeBlock.of("readList(parser, $T.class)", SourceTypes.erased(field.elementType()))
              : CodeBlock.of("readValue(parser, $T.class)", SourceTypes.erased(field.javaType()));
      if (model.construction() == ConstructionStyle.RECORD) {
        decode.addStatement("$N = $L", field.javaName(), decoded);
      } else {
        decode.addStatement("value.$L($L)", field.writeMutator(), decoded);
      }
    }
    decode
        .addStatement("default -> parser.skipValue()")
        .endControlFlow()
        .addStatement("if (!parser.peek('}')) parser.expect(',')")
        .endControlFlow()
        .addStatement("parser.expect('}')");
    if (model.construction() == ConstructionStyle.RECORD) {
      CodeBlock.Builder arguments = CodeBlock.builder();
      for (int index = 0; index < model.fields().size(); index++) {
        if (index > 0) arguments.add(", ");
        arguments.add("$N", model.fields().get(index).javaName());
      }
      decode.addStatement("return new $T($L)", valueType, arguments.build());
    } else {
      decode.addStatement("return value");
    }
    codec.addMethod(decode.build());
    return codec.build();
  }

  private static CodeBlock defaultValue(String javaType) {
    return switch (javaType.trim()) {
      case "boolean" -> CodeBlock.of("false");
      case "byte", "short", "int", "long", "char" -> CodeBlock.of("0");
      case "float" -> CodeBlock.of("0.0f");
      case "double" -> CodeBlock.of("0.0d");
      default -> CodeBlock.of("null");
    };
  }

  private record QualifiedType(String packageName, String simpleName) {
    static QualifiedType parse(String value) {
      String type = Objects.requireNonNull(value, "codecType").trim();
      int separator = type.lastIndexOf('.');
      if (separator <= 0 || separator == type.length() - 1) {
        throw new IllegalArgumentException("codecType must be fully qualified");
      }
      return new QualifiedType(type.substring(0, separator), type.substring(separator + 1));
    }
  }
}
