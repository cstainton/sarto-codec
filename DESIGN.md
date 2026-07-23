# sarto-codec Design

This document explains the design thinking behind `sarto-codec`: the problem it solves, why the solution has the shape it does, and what the boundaries of the library are.

## The problem

Any framework that moves typed Java values across a wire needs to encode and decode them. The straightforward approaches — Jackson, Gson, runtime reflection — do not work under TeaVM's AOT compilation model, which cannot support runtime type inspection in the general case. The framework therefore needs serialisation code that:

- is generated at build time from the types it will encode and decode
- has no runtime reflection dependency
- produces and consumes compact output
- compiles cleanly under TeaVM
- is shared between consumers so the rules are consistent

The last point matters. Both `sarto-rest` and the Sarto RPC framework need generated codecs for their wire types. If each defined its own JSON runtime and code generator, the two would diverge: different field naming rules, different handling of nulls and collections, different generated class shapes. A consumer using both would encounter inconsistencies.

## The solution

`sarto-codec` separates three concerns:

**The codec contract** (`sarto-codec-api`) defines how a codec encodes and decodes values for a given content type. It is content-type-neutral: the same `ContentCodec` interface covers JSON, Wire/Protobuf, and any future format. A `ContentCodecs` registry maps media types to codec instances. This module has no runtime dependency on any serialisation library.

**The runtimes** (`sarto-codec-runtime-json`, `sarto-codec-runtime-wire`) provide the actual encoding and decoding logic. The JSON runtime is a small, TeaVM-safe parser and emitter. The Wire runtime implements the Protobuf binary format using Square Wire primitives. Both are deliberately small: they provide the primitives that generated code calls, not a general-purpose reflection-based mapper.

**The code generator** (`sarto-codec-codegen`) emits type-specific mapping classes. Given a Java type and a target format, it produces ordinary Java source that handles field encoding, null checks, nested types, collections, and maps. The generated class is the only place that knows the field names and types of one particular DTO — the runtime itself does not inspect type metadata.

This architecture means the runtime modules are small and their behaviour is stable. The generated classes are transparent: a developer can open `ActivityApiJsonCodec.java` and read exactly what the encoder does.

## Design choices

**Generated code, not runtime introspection.** The tradeoff is build complexity in exchange for TeaVM compatibility and runtime transparency. The generated sources live in `target/generated-sources` and can be inspected. Build-time failures catch type mismatches that would otherwise be silent at runtime.

**Content-type neutral contract.** The `ContentCodec` interface does not assume JSON. This allows a consumer to declare one codec registry and add JSON, Wire, and future formats to it. It also means the contract model in `sarto-rest-contract` can carry a media type annotation without knowing which codec will handle it.

**Shared runtime, generated bindings.** The JSON and Wire runtimes contain the parsing and encoding primitives once. Generated type-specific codecs call into those primitives without duplicating encoding logic. This keeps the runtime jar small and the generated code simple.

**No schema discovery.** The codec contract does not define a way for a codec to announce its supported types at runtime. Types are registered explicitly, by the generated bootstrap, into the codec registry. There is no classpath scan or service loader involved. This is intentional: in a TeaVM application, the compiler must be able to determine all reachable code statically.

## What it does not do

`sarto-codec` does not provide a general-purpose object mapper. It does not support arbitrary Java types through reflection. It does not enforce a particular field naming convention — that is decided by the code generator's caller (the REST library, the RPC framework) based on the contract it is generating from.

It does not own the mapping from Java types to wire schema. The RPC framework knows about `@Portable` and `.proto` schema generation. The REST library knows about JAX-RS and OpenAPI schemas. Each passes its type model to the shared code generator, which emits the corresponding Java codec source.

## Why it is a separate library

Both the Sarto RPC framework and `sarto-rest` need this foundation. Publishing it as a shared library means:

- The JSON runtime is tested and maintained once
- Generated code from both consumers uses the same type registry and the same parsing primitives, so a Sarto application that uses both RPC and REST codecs has consistent encoding behaviour
- The codec contract can evolve independently of both consumers

A library that uses `sarto-codec-codegen` in its build and `sarto-codec-runtime-json` at runtime gains reflection-free serialisation without depending on either the Sarto RPC framework or the REST library.

## For more detail

- `sarto-codec-api` — the content-type-neutral codec contract
- `sarto-codec-runtime-json` — the JSON parsing and encoding runtime
- `sarto-codec-runtime-wire` — the Wire/Protobuf encoding runtime
- `sarto-codec-codegen` — the build-time type mapping generator
