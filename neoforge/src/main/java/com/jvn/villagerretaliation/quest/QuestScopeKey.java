package com.jvn.villagerretaliation.quest;

import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.village.VillageScopeKeys;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

public record QuestScopeKey(
        Kind kind,
        UUID playerId,
        ResourceLocation questId,
        UUID villagerId,
        String villageKey
) {
    public static final QuestScopeKey EMPTY = new QuestScopeKey(Kind.EMPTY, null, null, null, "");
    public static final QuestScopeKey WORLD = new QuestScopeKey(Kind.WORLD, null, null, null, "world");

    public QuestScopeKey {
        kind = kind == null ? Kind.EMPTY : kind;
        villageKey = villageKey == null ? "" : villageKey.trim();
    }

    public static QuestScopeKey player(UUID playerId) {
        return playerId == null ? EMPTY : new QuestScopeKey(Kind.PLAYER, playerId, null, null, "");
    }

    public static QuestScopeKey playerWorld(UUID playerId) {
        // PLAYER_WORLD intentionally persists with the legacy player scope key.
        return playerId == null ? EMPTY : new QuestScopeKey(Kind.PLAYER_WORLD, playerId, null, null, "");
    }

    public static QuestScopeKey quest(UUID playerId, ResourceLocation questId) {
        return playerId == null || questId == null
                ? EMPTY
                : new QuestScopeKey(Kind.QUEST, playerId, questId, null, "");
    }

    public static QuestScopeKey villager(UUID villagerId) {
        return villagerId == null ? EMPTY : new QuestScopeKey(Kind.VILLAGER, null, null, villagerId, "");
    }

    public static QuestScopeKey village(String villageKey) {
        return villageKey == null || villageKey.isBlank()
                ? EMPTY
                : new QuestScopeKey(Kind.VILLAGE, null, null, null, villageKey);
    }

    public static QuestScopeKey fromFactScope(
            DialogueContext context,
            QuestFactScope scope,
            ResourceLocation questId) {
        if (context == null) {
            return EMPTY;
        }
        QuestFactScope resolvedScope = scope == null ? QuestFactScope.PLAYER : scope;
        return switch (resolvedScope) {
            case PLAYER -> player(context.player().getUUID());
            case WORLD -> WORLD;
            case QUEST -> quest(context.player().getUUID(), questId);
            case VILLAGER -> villager(context.villager().getUUID());
            case VILLAGE -> village(VillageScopeKeys.forVillager(context.level(), context.villager()));
        };
    }

    public static QuestScopeKey fromCompletionScope(
            DialogueContext context,
            QuestDefinition.CompletionScope scope,
            ResourceLocation questId) {
        if (context == null) {
            return EMPTY;
        }
        QuestDefinition.CompletionScope resolvedScope = scope == null
                ? QuestDefinition.CompletionScope.PLAYER
                : scope;
        return switch (resolvedScope) {
            case PLAYER -> player(context.player().getUUID());
            case PLAYER_WORLD -> playerWorld(context.player().getUUID());
            case WORLD -> WORLD;
            case VILLAGER -> villager(context.villager().getUUID());
            case VILLAGE -> village(VillageScopeKeys.forVillager(context.level(), context.villager()));
        };
    }

    public static Optional<QuestScopeKey> parse(String serialized) {
        String value = serialized == null ? "" : serialized.trim();
        if (value.isBlank()) {
            return Optional.empty();
        }
        if ("world".equals(value)) {
            return Optional.of(WORLD);
        }
        if (value.startsWith("player:")) {
            return parseUuid(value.substring("player:".length()))
                    .map(QuestScopeKey::player);
        }
        if (value.startsWith("villager:")) {
            return parseUuid(value.substring("villager:".length()))
                    .map(QuestScopeKey::villager);
        }
        if (value.startsWith("quest:")) {
            String[] parts = value.split(":", 3);
            if (parts.length != 3) {
                return Optional.empty();
            }
            Optional<UUID> playerId = parseUuid(parts[1]);
            ResourceLocation questId = ResourceLocation.tryParse(parts[2]);
            return playerId.isEmpty() || questId == null
                    ? Optional.empty()
                    : Optional.of(quest(playerId.get(), questId));
        }
        if (value.startsWith("village:")) {
            return Optional.of(village(value));
        }
        return Optional.empty();
    }

    public boolean isBlank() {
        return this.asString().isBlank();
    }

    public String asString() {
        return switch (this.kind) {
            case EMPTY -> "";
            case PLAYER, PLAYER_WORLD -> this.playerId == null ? "" : "player:" + this.playerId;
            case WORLD -> "world";
            case QUEST -> this.playerId == null || this.questId == null
                    ? ""
                    : "quest:" + this.playerId + ":" + this.questId;
            case VILLAGER -> this.villagerId == null ? "" : "villager:" + this.villagerId;
            case VILLAGE -> this.villageKey;
        };
    }

    @Override
    public String toString() {
        return this.asString();
    }

    private static Optional<UUID> parseUuid(String value) {
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    public enum Kind {
        EMPTY,
        PLAYER,
        PLAYER_WORLD,
        WORLD,
        QUEST,
        VILLAGER,
        VILLAGE
    }
}
