package com.jvn.villagerretaliation.quest.runtime;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public final class QuestProgressTypes {
    public static final int MAX_CHOICE_HISTORY = 256;
    public static final int MAX_COMPLETION_HISTORY = 128;
    public static final int MAX_PROVIDER_REBIND_HISTORY = 64;
    public static final int MAX_PENDING_TRIGGER_EVENTS = 64;

    private QuestProgressTypes() { }

    public enum QuestState {
        NOT_STARTED, ACTIVE, COMPLETED, FAILED, ABANDONED, EXPIRED, CONSUMED
    }

    public record ChoiceHistoryEntry(String scenePath, String responseId, String priorStage, String nextStage, long gameTime) {
        public ChoiceHistoryEntry {
            scenePath = normalizeChoiceValue(scenePath);
            responseId = normalizeChoiceValue(responseId);
            priorStage = normalizeChoiceValue(priorStage);
            nextStage = normalizeChoiceValue(nextStage);
        }
    }

    public record CompletionHistoryEntry(int completionIndex, UUID issuerId, long startedGameTime, long completedGameTime,
            String issuerName, Map<String, String> issuerMetadata, ResourceKey<Level> issuerDimension, BlockPos issuerPos, String issuerVillageKey) {
        public CompletionHistoryEntry {
            completionIndex = Math.max(1, completionIndex);
            issuerName = issuerName == null ? "" : issuerName;
            issuerMetadata = Map.copyOf(issuerMetadata);
            issuerPos = issuerPos == null ? null : issuerPos.immutable();
            issuerVillageKey = issuerVillageKey == null ? "" : issuerVillageKey;
        }

        public CompletionHistoryEntry replacingVillagerIdentity(UUID source, UUID target) {
            return issuerId == null || !issuerId.equals(source) ? this : new CompletionHistoryEntry(completionIndex, target,
                    startedGameTime, completedGameTime, issuerName, issuerMetadata, issuerDimension, issuerPos, issuerVillageKey);
        }
    }

    public record ProviderRebindHistoryEntry(UUID previousProviderId, UUID newProviderId, String previousName,
            Map<String, String> previousMetadata, ResourceKey<Level> previousDimension, BlockPos previousPos,
            String previousVillageKey, long gameTime, String reason) {
        public ProviderRebindHistoryEntry {
            previousName = previousName == null ? "" : previousName;
            previousMetadata = Map.copyOf(previousMetadata);
            previousPos = previousPos == null ? null : previousPos.immutable();
            previousVillageKey = previousVillageKey == null ? "" : previousVillageKey;
            reason = normalizeCode(reason, "operator_rebind");
        }
    }

    public static String normalizeCode(String value, String fallback) {
        String normalized = normalizeChoiceValue(value).toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        return normalized.isBlank() ? fallback : normalized;
    }

    private static String normalizeChoiceValue(String value) {
        return value == null ? "" : value.trim();
    }
}
