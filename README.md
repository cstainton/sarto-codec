# Sarto Codec

Sarto Codec is a small, standalone serialization brick for Java code that must
run unchanged on the JVM and through TeaVM. It supplies content-type-neutral
codec contracts and reflection-free JSON machinery. Code generators provide
the type-specific mappings.

It deliberately has no dependency on Sarto REST, CDI, persistence, RPC, or an
application framework.

## Why it exists

Generated clients and protocols know the shape of their wire types at build
time. Generating that mapping is smaller and more predictable than relying on
runtime reflection, and it works in TeaVM without reflection metadata or
classpath scanning.

`sarto-codec-json` contains the JSON syntax implementation once. Generated code
only describes how a particular Java type maps to fields:

```java
JsonCodec json = new JsonCodec()
        .register(Activity.class, new ActivityJsonCodec());

String body = json.encode(activity);
Activity decoded = json.decode(body, Activity.class);
```

JSON is a supplied default because it is the dominant REST representation, not
because REST itself requires JSON. `ContentCodec` and `ContentCodecs` keep media
type selection explicit so XML and other formats can be added without changing
transport or generated client APIs.

## Modules

| Module | Purpose |
| --- | --- |
| `sarto-codec-api` | Content codec contract, media-type registry, and actionable unsupported-media-type failure. |
| `sarto-codec-json` | TeaVM-safe JSON parser/output plus the registry used by generated type codecs. |

## Other representations

The next useful shared format is XML. Its generated mapping must respect XML
schema details such as attributes, elements, namespaces, and wrapped lists; it
should not guess a JSON-shaped XML document from Java field names. Plain text
can be handled as a scalar codec. Form URL encoding and multipart are request
entity encoders rather than general object codecs. Binary formats such as CBOR
or Protocol Buffers should be separate optional modules.

When a contract declares an unsupported media type, a generator should emit a
named placeholder binding for that media type. Construction still succeeds,
but the first attempted encode/decode throws `UnsupportedMediaTypeException`
with the exact media type and the extension point to supply. This keeps the
generated source complete without silently choosing an incorrect wire format.

## Build

```bash
mvn clean verify
```

Licensed under the Apache License, Version 2.0.
