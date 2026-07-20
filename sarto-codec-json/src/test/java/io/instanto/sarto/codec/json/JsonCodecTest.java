package io.instanto.sarto.codec.json;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonCodecTest {
    @Test
    void generatedTypeMappingsRoundTripWithoutReflection() {
        JsonCodec codec = new JsonCodec().register(Person.class, new PersonCodec());

        String json = codec.encode(new Person("Ada", 37));

        assertEquals("{\"name\":\"Ada\",\"age\":37}", json);
        assertEquals(new Person("Ada", 37), codec.decode(json, Person.class));
        assertEquals(List.of(new Person("Ada", 37)), codec.decodeList("[" + json + "]", Person.class));
        assertTrue(codec.supports("application/problem+json; charset=utf-8"));
    }

    private record Person(String name, int age) {}

    private static final class PersonCodec implements JsonTypeCodec<Person> {
        @Override
        public void encode(JsonOutput output, Person value) {
            output.raw('{').string("name").raw(':').string(value.name())
                    .raw(',').string("age").raw(':').raw(Integer.toString(value.age())).raw('}');
        }

        @Override
        public Person decode(PortableJsonParser parser) {
            String name = null;
            int age = 0;
            parser.expect('{');
            while (!parser.peek('}')) {
                String field = parser.readString();
                parser.expect(':');
                switch (field) {
                    case "name" -> name = parser.readStringOrNull();
                    case "age" -> age = (int) parser.readLong();
                    default -> parser.skipValue();
                }
                if (!parser.peek('}')) parser.expect(',');
            }
            parser.expect('}');
            return new Person(name, age);
        }
    }
}
