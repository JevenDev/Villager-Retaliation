package com.jvn.villagerretaliation.client.interaction;

import net.minecraft.client.gui.Font;

final class VillagerInteractionTextLayout {
    private VillagerInteractionTextLayout() {
    }

    static Nameplate nameplate(
            Font font,
            String villagerName,
            int maxNameCodePoints,
            int minTextureWidth,
            int nameOffsetX,
            int rightPadding) {
        String displayName = truncateByCodePoint(villagerName, maxNameCodePoints);
        int width = Math.max(minTextureWidth, nameOffsetX + font.width(displayName) + rightPadding);
        return new Nameplate(displayName, width);
    }

    private static String truncateByCodePoint(String value, int maxCodePoints) {
        if (value == null || value.isEmpty() || maxCodePoints <= 0) {
            return "";
        }
        if (value.codePointCount(0, value.length()) <= maxCodePoints) {
            return value;
        }
        return value.substring(0, value.offsetByCodePoints(0, maxCodePoints));
    }

    record Nameplate(String displayName, int width) {
    }
}
