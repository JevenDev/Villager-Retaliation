package com.jvn.villagerretaliation.quest.schema;

import java.util.Locale;
import net.minecraft.resources.ResourceLocation;

public record QuestResourceSource(ResourceLocation location, String sourcePackId) {
    public QuestResourceSource {
        sourcePackId = sourcePackId == null ? "" : sourcePackId;
    }

    public boolean isFromPack(String packId) {
        String expected = normalizePackId(packId);
        String actual = normalizePackId(this.sourcePackId);
        return !expected.isBlank() && (actual.equals(expected) || actual.endsWith(expected));
    }

    private static String normalizePackId(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "");
    }
}
