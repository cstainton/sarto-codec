package io.instanto.sarto.codec.codegen;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.Compiler;
import com.google.testing.compile.JavaFileObjects;
import io.instanto.sarto.codec.codegen.model.CodecFieldModel;
import io.instanto.sarto.codec.codegen.model.CodecTypeModel;
import io.instanto.sarto.codec.codegen.model.ConstructionStyle;
import java.util.List;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.Test;

class CodecSourceGeneratorTest {
  @Test
  void generatesCompilingJsonRegistryForBeansAndRecords() {
    CodecTypeModel bean =
        new CodecTypeModel(
            "example.Person",
            List.of(
                new CodecFieldModel(
                    "display_name", "name", "java.lang.String", "getName", "setName", 0),
                new CodecFieldModel("active", "active", "boolean", "isActive", "setActive", 0)));
    CodecTypeModel record =
        new CodecTypeModel(
            "example.Team",
            List.of(new CodecFieldModel("members", "members", "java.util.List<example.Person>")),
            ConstructionStyle.RECORD);
    String generated =
        new JsonCodecSourceGenerator()
            .generateRegistry("example.GeneratedJsonCodec", List.of(bean, record));

    Compilation compilation =
        Compiler.javac()
            .compile(
                JavaFileObjects.forSourceString("example.GeneratedJsonCodec", generated),
                JavaFileObjects.forSourceString(
                    "example.Person",
                    """
                    package example;
                    public final class Person {
                        private String name;
                        private boolean active;
                        public String getName() { return name; }
                        public void setName(String name) { this.name = name; }
                        public boolean isActive() { return active; }
                        public void setActive(boolean active) { this.active = active; }
                    }
                    """),
                JavaFileObjects.forSourceString(
                    "example.Team",
                    "package example; public record Team(java.util.List<Person> members) {}"));

    assertThat(compilation).succeeded();
    assertTrue(generated.contains("value.isActive()"));
    assertTrue(generated.contains("new Team(members)"));
  }

  @Test
  void generatesCompilingProtobufCodecWithExplicitFieldNumbers() {
    CodecTypeModel message =
        new CodecTypeModel(
            "example.Message",
            List.of(
                new CodecFieldModel("id", "id", "long", null, null, 1),
                new CodecFieldModel(
                    "labels", "labels", "java.util.List<java.lang.String>", null, null, 2)),
            ConstructionStyle.RECORD);
    String generated =
        new WireCodecSourceGenerator()
            .generate(message, new WireGenerationOptions("example.Message_WireCodec"));
    JavaFileObject target =
        JavaFileObjects.forSourceString(
            "example.Message",
            "package example; public record Message(long id, java.util.List<String> labels) {}");

    Compilation compilation =
        Compiler.javac()
            .compile(
                JavaFileObjects.forSourceString("example.Message_WireCodec", generated), target);

    assertThat(compilation).succeeded();
    assertTrue(generated.contains("case 1:"));
    assertTrue(generated.contains("case 2:"));
    assertTrue(generated.contains("implements WireTypeCodec<Message>"));
  }
}
