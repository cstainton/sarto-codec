package io.instanto.sarto.codec.codegen;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import java.util.ArrayList;
import java.util.List;

final class SourceTypes {
  private SourceTypes() {}

  static TypeName typeName(String value) {
    String type = value.trim();
    if (type.endsWith("[]")) return arrayType(type.substring(0, type.length() - 2));
    TypeName primitive = primitive(type);
    if (primitive != null) return primitive;
    int open = type.indexOf('<');
    if (open < 0) return ClassName.bestGuess(type);
    String raw = type.substring(0, open).trim();
    String parameters = type.substring(open + 1, type.lastIndexOf('>'));
    List<TypeName> arguments = split(parameters).stream().map(SourceTypes::typeName).toList();
    return ParameterizedTypeName.get(ClassName.bestGuess(raw), arguments.toArray(TypeName[]::new));
  }

  static TypeName erased(String value) {
    String type = value.trim();
    if (type.endsWith("[]")) return arrayType(type.substring(0, type.length() - 2));
    int open = type.indexOf('<');
    String erased = open < 0 ? type : type.substring(0, open).trim();
    TypeName primitive = primitive(erased);
    return primitive == null ? ClassName.bestGuess(erased) : primitive;
  }

  static TypeName boxed(String value) {
    TypeName type = erased(value);
    return type.isPrimitive() ? type.box() : type;
  }

  private static TypeName arrayType(String component) {
    return com.squareup.javapoet.ArrayTypeName.of(typeName(component));
  }

  private static TypeName primitive(String type) {
    return switch (type) {
      case "boolean" -> TypeName.BOOLEAN;
      case "byte" -> TypeName.BYTE;
      case "short" -> TypeName.SHORT;
      case "int" -> TypeName.INT;
      case "long" -> TypeName.LONG;
      case "char" -> TypeName.CHAR;
      case "float" -> TypeName.FLOAT;
      case "double" -> TypeName.DOUBLE;
      case "void" -> TypeName.VOID;
      default -> null;
    };
  }

  private static List<String> split(String value) {
    List<String> result = new ArrayList<>();
    int depth = 0;
    int start = 0;
    for (int i = 0; i < value.length(); i++) {
      char current = value.charAt(i);
      if (current == '<') depth++;
      else if (current == '>') depth--;
      else if (current == ',' && depth == 0) {
        result.add(value.substring(start, i).trim());
        start = i + 1;
      }
    }
    result.add(value.substring(start).trim());
    return result;
  }
}
