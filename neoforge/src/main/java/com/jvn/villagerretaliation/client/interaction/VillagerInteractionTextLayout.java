package com.jvn.villagerretaliation.client.interaction;

import net.minecraft.client.gui.Font;

final class VillagerInteractionTextLayout {
    private VillagerInteractionTextLayout() {
    }

    static Nameplate nameplate(
            Font font,
            String villagerName,
            int minTextureWidth,
            int horizontalPadding) {
        String displayName = villagerName == null ? "" : villagerName;
        int safeHorizontalPadding = Math.max(0, horizontalPadding);
        int width = Math.max(minTextureWidth, font.width(displayName) + safeHorizontalPadding * 2);
        return new Nameplate(displayName, width);
    }

    record Nameplate(String displayName, int width) {
    }
}
