package com.jvn.villagerretaliation.util;

import com.jvn.villagerretaliation.reputation.VillagerReputationLevel;
import java.util.Locale;
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
        return titleCaseIdentifier(rawName);
    }

    public static String reputationLevelName(VillagerReputationLevel level) {
        return titleCaseIdentifier(level.name());
    }

    public static String resourcePathName(ResourceLocation id) {
        String path = id.getPath();
        int slash = path.lastIndexOf('/');
        if (slash >= 0 && slash + 1 < path.length()) {
            path = path.substring(slash + 1);
        }

        String name = titleCaseIdentifier(path.replace('-', '_'));
        return name.isBlank() ? id.toString() : name;
    }

    public static String withIndefiniteArticle(String name) {
        if (name.isEmpty()) {
            return "somewhere";
        }

        char first = Character.toLowerCase(name.charAt(0));
        String article = first == 'a' || first == 'e' || first == 'i' || first == 'o' || first == 'u' ? "an " : "a ";
        return article + name;
    }

    public static String titleCaseIdentifier(String rawName) {
        String normalizedName = rawName.replace('_', ' ').toLowerCase(Locale.ROOT);
        StringBuilder builder = new StringBuilder(normalizedName.length());
        boolean capitalizeNext = true;
        for (char character : normalizedName.toCharArray()) {
            if (Character.isWhitespace(character)) {
                capitalizeNext = true;
                builder.append(character);
            } else if (capitalizeNext) {
                builder.append(Character.toUpperCase(character));
                capitalizeNext = false;
            } else {
                builder.append(character);
            }
        }
        return builder.toString();
    }
}
