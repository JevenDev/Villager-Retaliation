package com.jvn.villagerretaliation.quest.schema;

import java.util.Locale;
import java.util.Optional;

public enum QuestSchemaVersion {
    V2("villagerretaliation:quest/v2"),
    V3("villagerretaliation:quest/v3");

    private final String schemaId;

    QuestSchemaVersion(String schemaId) {
        this.schemaId = schemaId;
    }

    public String schemaId() {
        return this.schemaId;
    }

    public static Optional<QuestSchemaVersion> bySchemaId(String schemaId) {
        String normalized = schemaId == null ? "" : schemaId.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return Optional.empty();
        }
        for (QuestSchemaVersion version : values()) {
            if (version.schemaId.equals(normalized)) {
                return Optional.of(version);
            }
        }
        return Optional.empty();
    }
}
