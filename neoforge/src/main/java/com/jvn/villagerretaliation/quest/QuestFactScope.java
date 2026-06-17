package com.jvn.villagerretaliation.quest;

import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.village.VillageMembership;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

public enum QuestFactScope {
    PLAYER,
    WORLD,
    QUEST,
    VILLAGER,
    VILLAGE;

    public static QuestFactScope bySerializedName(String value, QuestFactScope fallback) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "player", "player_world", "per_player" -> PLAYER;
            case "world", "global", "server" -> WORLD;
            case "quest", "quest_progress", "player_quest" -> QUEST;
            case "villager", "issuer", "quest_giver" -> VILLAGER;
            case "village", "settlement" -> VILLAGE;
            default -> fallback == null ? PLAYER : fallback;
        };
    }

    public String scopeKey(DialogueContext context, ResourceLocation questId) {
        if (context == null) {
            return "";
        }
        String dimension = context.level().dimension().location().toString();
        return switch (this) {
            case PLAYER -> "player:" + context.player().getUUID();
            case WORLD -> "world";
            case QUEST -> questId == null
                    ? ""
                    : "quest:" + context.player().getUUID() + ":" + questId;
            case VILLAGER -> "villager:" + context.villager().getUUID();
            case VILLAGE -> VillageMembership.resolve(context.level(), context.villager())
                    .map(area -> "village:" + dimension + ":" + posKey(area.centerBlock()))
                    .orElseGet(() -> "village:" + dimension + ":" + posKey(context.villager().blockPosition()));
        };
    }

    private static String posKey(BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }
}
