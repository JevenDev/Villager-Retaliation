package com.jvn.villagerretaliation.quest;

import com.jvn.villagerretaliation.dialogue.DialogueContext;
import java.util.Locale;
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
        return scope(context, questId).asString();
    }

    public QuestScopeKey scope(DialogueContext context, ResourceLocation questId) {
        if (context == null) {
            return QuestScopeKey.EMPTY;
        }
        return QuestScopeKey.fromFactScope(context, this, questId);
    }
}
