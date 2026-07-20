package io.instanto.sarto.codec.codegen;

import java.util.List;
import java.util.Objects;

/** Output-class and optional caller-facade settings for protobuf source generation. */
public record WireGenerationOptions(
    String codecType,
    List<String> additionalInterfaces,
    boolean generateGenericFacade,
    String contentType) {

  public WireGenerationOptions(String codecType) {
    this(codecType, List.of(), false, "application/x-protobuf");
  }

  public WireGenerationOptions {
    codecType = Objects.requireNonNull(codecType, "codecType").trim();
    if (codecType.isEmpty()) throw new IllegalArgumentException("codecType is required");
    additionalInterfaces =
        List.copyOf(Objects.requireNonNull(additionalInterfaces, "additionalInterfaces"));
    contentType = Objects.requireNonNull(contentType, "contentType").trim();
  }
}
