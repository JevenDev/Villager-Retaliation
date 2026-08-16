package com.jvn.villagerretaliation.quest.schema;

import java.util.Locale;
import java.util.Optional;

public enum QuestSchemaVersion {
    V1(""),
    V2("villagerretaliation:quest/v2");

    private final String schemaId;

    QuestSchemaVersion(String schemaId) {
        this.schemaId = schemaId;
    }

    public String schemaId() {
        return this.schemaId;
    }

    public boolean isExplicit() {
        return !this.schemaId.isBlank();
    }

    public static Optional<QuestSchemaVersion> bySchemaId(String schemaId) {
        String normalized = schemaId == null ? "" : schemaId.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return Optional.of(V1);
        }
        for (QuestSchemaVersion version : values()) {
            if (version.isExplicit() && version.schemaId.equals(normalized)) {
                return Optional.of(version);
            }
        }
        return Optional.empty();
    }
}
