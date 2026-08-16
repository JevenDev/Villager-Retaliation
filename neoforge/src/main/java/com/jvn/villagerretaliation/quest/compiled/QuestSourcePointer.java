package com.jvn.villagerretaliation.quest.compiled;

import com.jvn.villagerretaliation.quest.schema.QuestResourceEnvelope;
import com.jvn.villagerretaliation.quest.schema.QuestSchemaVersion;
import java.util.Arrays;
import java.util.stream.Collectors;
import net.minecraft.resources.ResourceLocation;

public record QuestSourcePointer(
        ResourceLocation resource,
        QuestSchemaVersion schemaVersion,
        String sourcePackId,
        String jsonPointer
) {
    public QuestSourcePointer {
        schemaVersion = schemaVersion == null ? QuestSchemaVersion.V1 : schemaVersion;
        sourcePackId = sourcePackId == null ? "" : sourcePackId;
        jsonPointer = normalizeJsonPointer(jsonPointer);
    }

    public static QuestSourcePointer from(QuestResourceEnvelope envelope) {
        return new QuestSourcePointer(
                envelope.location(),
                envelope.schemaVersion(),
                envelope.source().sourcePackId(),
                "");
    }

    public QuestSourcePointer child(String... tokens) {
        if (tokens == null || tokens.length == 0) {
            return this;
        }
        String suffix = Arrays.stream(tokens)
                .map(QuestSourcePointer::escapeJsonPointerToken)
                .collect(Collectors.joining("/", "/", ""));
        return new QuestSourcePointer(this.resource, this.schemaVersion, this.sourcePackId, this.jsonPointer + suffix);
    }

    private static String normalizeJsonPointer(String pointer) {
        if (pointer == null || pointer.isBlank()) {
            return "";
        }
        return pointer.startsWith("/") ? pointer : "/" + pointer;
    }

    private static String escapeJsonPointerToken(String token) {
        return (token == null ? "" : token)
                .replace("~", "~0")
                .replace("/", "~1");
    }
}
