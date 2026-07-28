package com.agridabao.api.farm;

import tools.jackson.databind.JsonNode;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record FarmSaveRequest(
        @Min(0) long expectedRevision,
        @Min(1) int schemaVersion,
        @NotBlank String generatorVersion,
        @NotNull JsonNode snapshot
) {
}
