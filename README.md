# Sarto Codec

Sarto Codec is the shared serialization foundation used by independently
consumable Sarto libraries such as Sarto REST and the Sarto RPC framework. It
supplies content-type-neutral codec contracts, portable runtimes, and the
build-time machinery that generates type-specific mappings for JVM and TeaVM.

It is independently built and released so those libraries can share it without
depending on one another. It is not presented as a separate application-facing
framework: most users receive its runtime transitively and interact with their
REST, RPC, or persistence API instead.

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
| `sarto-codec-runtime-json` | TeaVM-safe JSON parser/output plus the registry used by generated type codecs. |
| `sarto-codec-runtime-wire` | Binary Protobuf/Wire registry for generated message codecs. |
| `sarto-codec-codegen` | Caller-neutral type model and shared JSON/Protobuf source generators. |

## Other representations

The next useful shared format is XML. Its generated mapping must respect XML
schema details such as attributes, elements, namespaces, and wrapped lists; it
should not guess a JSON-shaped XML document from Java field names. Plain text
can be handled as a scalar codec. Form URL encoding and multipart are request
entity encoders rather than general object codecs. The shared API is
byte-oriented so binary formats are not forced through text or Base64.
Protobuf is the wire format used by Sarto's existing generated Wire codecs.
`sarto-codec-runtime-wire` supplies the reusable runtime contract and media-type
registry; it does not maintain a second protobuf model or encoder.
`sarto-codec-codegen` emits field/tag encoding using Square Wire primitives.
REST, RPC, persistence, and other Sarto libraries translate their own contract
metadata into the shared codec type model; protobuf field identity remains the
responsibility of that caller context. CBOR can follow the same optional-module
pattern.

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
