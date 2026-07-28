package com.agridabao.api.farm;

import tools.jackson.databind.JsonNode;

import java.time.Instant;

public record FarmSaveResponse(
        long revision,
        int schemaVersion,
        String generatorVersion,
        JsonNode snapshot,
        Instant savedAt,
        Instant lastLogoutAt
) {
    public static FarmSaveResponse from(FarmSave save) {
        return new FarmSaveResponse(
                save.getRevision(),
                save.getSchemaVersion(),
                save.getGeneratorVersion(),
                save.getSnapshot(),
                save.getSavedAt(),
                save.getLastLogoutAt()
        );
    }
}
