package com.jvn.villagerretaliation.util;

import com.jvn.toucanlib.util.ToucanText;
import com.jvn.villagerretaliation.reputation.VillagerReputationLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.VillagerProfession;

public final class VillagerInteractionTextUtil {
    private VillagerInteractionTextUtil() {
    }

    public static String professionName(VillagerProfession profession, String fallbackForNone) {
        String rawName = profession == null ? null : profession.name();
        if (rawName == null || rawName.isBlank() || "none".equalsIgnoreCase(rawName)) {
            return fallbackForNone;
        }
        return ToucanText.titleCaseIdentifier(rawName);
    }

    public static String reputationLevelName(VillagerReputationLevel level) {
        return ToucanText.enumName(level);
    }

    public static String resourcePathName(ResourceLocation id) {
        return ToucanText.resourcePathName(id);
    }

    public static String withIndefiniteArticle(String name) {
        return ToucanText.withIndefiniteArticle(name);
    }

    public static String titleCaseIdentifier(String rawName) {
        return ToucanText.titleCaseIdentifier(rawName);
    }
}
