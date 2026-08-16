package com.jvn.villagerretaliation.quest.debug;

import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

public record QuestDiagnostic(
        Severity severity,
        String code,
        ResourceLocation resourceId,
        String sourcePack,
        String resourcePath,
        String jsonPointer,
        String message,
        String suggestedFix,
        Set<String> relevantIds
) {
    public QuestDiagnostic {
        severity = severity == null ? Severity.WARNING : severity;
        code = code == null || code.isBlank() ? "datapack.diagnostic" : code.trim();
        sourcePack = sourcePack == null ? "" : sourcePack;
        resourcePath = resourcePath == null ? "" : resourcePath;
        jsonPointer = normalizeJsonPointer(jsonPointer);
        message = message == null ? "" : message;
        suggestedFix = suggestedFix == null ? "" : suggestedFix;
        relevantIds = freezeRelevantIds(relevantIds);
    }

    public static QuestDiagnostic warning(
            String code,
            ResourceLocation resourceId,
            String jsonPointer,
            String message,
            String suggestedFix,
            Set<String> relevantIds) {
        return new QuestDiagnostic(
                Severity.WARNING,
                code,
                resourceId,
                "",
                "",
                jsonPointer,
                message,
                suggestedFix,
                relevantIds);
    }

    private static String normalizeJsonPointer(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String pointer = value.trim();
        return pointer.startsWith("/") ? pointer : "/" + pointer;
    }

    private static Set<String> freezeRelevantIds(Set<String> values) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        Set<String> copy = new LinkedHashSet<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                copy.add(value.trim());
            }
        }
        return copy.isEmpty() ? Set.of() : Set.copyOf(copy);
    }

    public enum Severity {
        INFO,
        WARNING,
        ERROR
    }
}
