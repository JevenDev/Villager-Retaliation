package com.jvn.villagerretaliation.client.reputation;

import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.reputation.VillagerReputationLevel;
import com.jvn.villagerretaliation.util.VillagerInteractionTextUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.ResourceLocation;

public final class VillagerReputationIconSet {
    private VillagerReputationIconSet() {
    }

    public static String formatLevel(VillagerReputationLevel level) {
        return VillagerInteractionTextUtil.reputationLevelName(level);
    }

    public static ChatFormatting colorFor(VillagerReputationLevel level) {
        return switch (level) {
            case ROYALTY -> ChatFormatting.YELLOW;
            case REVERED -> ChatFormatting.GOLD;
            case RESPECTED -> ChatFormatting.AQUA;
            case TRUSTED -> ChatFormatting.GREEN;
            case NEUTRAL -> ChatFormatting.GRAY;
            case SUSPICIOUS -> ChatFormatting.GRAY;
            case HOSTILE -> ChatFormatting.RED;
            case DESPISED -> ChatFormatting.DARK_RED;
            case FEARED -> ChatFormatting.LIGHT_PURPLE;
        };
    }

    public static ResourceLocation iconFor(VillagerReputationLevel level) {
        return switch (level) {
            case ROYALTY -> icon("royalty");
            case REVERED -> icon("revered");
            case RESPECTED -> icon("respected");
            case TRUSTED -> icon("trusted");
            case NEUTRAL -> icon("neutral");
            case SUSPICIOUS -> icon("suspicious");
            case HOSTILE -> icon("hostile");
            case DESPISED -> icon("despised");
            case FEARED -> icon("feared");
        };
    }

    private static ResourceLocation icon(String name) {
        return VillagerRetaliation.id("textures/gui/container/icons/" + name + ".png");
    }
}
