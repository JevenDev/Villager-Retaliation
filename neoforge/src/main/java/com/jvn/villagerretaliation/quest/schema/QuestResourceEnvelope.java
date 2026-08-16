package com.jvn.villagerretaliation.quest.schema;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.util.DatapackDiagnostics;
import com.jvn.villagerretaliation.util.DatapackResourceLoader;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

public record QuestResourceEnvelope(
        ResourceLocation location,
        JsonObject root,
        QuestSchemaVersion schemaVersion,
        QuestResourceSource source
) {
    private static final String SCHEMA_FIELD = "schema";

    public QuestResourceEnvelope {
        if (schemaVersion == null) {
            schemaVersion = QuestSchemaVersion.V1;
        }
        if (source == null) {
            source = new QuestResourceSource(location, "");
        }
    }

    public static Optional<QuestResourceEnvelope> read(
            DatapackResourceLoader.JsonResource resource,
            JsonObject root) {
        return read(
                resource.location(),
                root,
                new QuestResourceSource(resource.location(), resource.resource().sourcePackId()));
    }

    public static Optional<QuestResourceEnvelope> read(ResourceLocation location, JsonObject root) {
        return read(location, root, new QuestResourceSource(location, ""));
    }

    public static Optional<QuestResourceEnvelope> read(
            ResourceLocation location,
            JsonObject root,
            QuestResourceSource source) {
        if (root == null) {
            return Optional.empty();
        }
        Optional<QuestSchemaVersion> schemaVersion = readSchemaVersion(location, root);
        return schemaVersion.map(version -> new QuestResourceEnvelope(location, root, version, source));
    }

    private static Optional<QuestSchemaVersion> readSchemaVersion(ResourceLocation location, JsonObject root) {
        JsonElement schema = root.get(SCHEMA_FIELD);
        if (schema == null || schema.isJsonNull()) {
            return Optional.of(QuestSchemaVersion.V1);
        }
        if (!schema.isJsonPrimitive() || !schema.getAsJsonPrimitive().isString()) {
            DatapackDiagnostics.warnSkippedEntry(
                    location,
                    "quest",
                    "schema",
                    "schema must be a string such as villagerretaliation:quest/v2.");
            return Optional.empty();
        }

        String schemaId = schema.getAsString().trim();
        if (schemaId.isBlank()) {
            DatapackDiagnostics.warnSkippedEntry(
                    location,
                    "quest",
                    "schema",
                    "schema must not be blank; omit it for v1 quest resources.");
            return Optional.empty();
        }
        Optional<QuestSchemaVersion> version = QuestSchemaVersion.bySchemaId(schemaId);
        if (version.isEmpty()) {
            DatapackDiagnostics.warnSkippedEntry(
                    location,
                    "quest",
                    "schema",
                    "unsupported schema \"" + schemaId + "\".");
        }
        return version;
    }
}
