package com.jvn.villagerretaliation.dialogue.normal;

import com.jvn.villagerretaliation.dialogue.DialogueRuntimeContext;

/** Applies one usage policy consistently regardless of the dialogue surface. */
public final class DialogueUsageService {
    private DialogueUsageService() {
    }

    public static boolean available(DialogueRuntimeContext context, String dialogueId, DialogueUsagePolicy policy) {
        if (context == null || dialogueId == null || dialogueId.isBlank()) {
            return true;
        }
        DialogueUsagePolicy effective = policy == null ? DialogueUsagePolicy.DEFAULT : policy;
        if (effective.cooldownTicks() <= 0L && effective.maxUses() <= 0) {
            return true;
        }
        DialogueUsageSavedData.Usage usage = usage(context, dialogueId, effective);
        if (effective.maxUses() > 0 && usage.count() >= effective.maxUses()) {
            return false;
        }
        return effective.cooldownTicks() <= 0L
                || usage.lastUsedGameTime() == Long.MIN_VALUE
                || context.level().getGameTime() >= usage.lastUsedGameTime() + effective.cooldownTicks();
    }

    public static DialogueUsageSavedData.Usage usage(DialogueRuntimeContext context, String dialogueId, DialogueUsagePolicy policy) {
        if (context == null || dialogueId == null || dialogueId.isBlank()) return DialogueUsageSavedData.Usage.EMPTY;
        DialogueUsagePolicy effective = policy == null ? DialogueUsagePolicy.DEFAULT : policy;
        DialogueUsageSavedData.Usage usage = DialogueUsageSavedData.get(context.level())
                .usage(scopeKey(context, effective.scope()), dialogueId);
        if (usage.count() == 0 && effective.scope() == DialogueUsagePolicy.Scope.PLAYER_VILLAGER) {
            usage = context.legacyUsage(dialogueId);
        }
        return usage;
    }

    public static java.util.Optional<String> lastUsedAmong(
            DialogueRuntimeContext context, java.util.Set<String> dialogueIds, DialogueUsagePolicy policy) {
        if (context == null || dialogueIds == null || dialogueIds.isEmpty()) return java.util.Optional.empty();
        DialogueUsagePolicy effective = policy == null ? DialogueUsagePolicy.DEFAULT : policy;
        return DialogueUsageSavedData.get(context.level()).lastUsedAmong(scopeKey(context, effective.scope()), dialogueIds);
    }

    public static void record(DialogueRuntimeContext context, String dialogueId, DialogueUsagePolicy policy) {
        if (context == null || dialogueId == null || dialogueId.isBlank()) {
            return;
        }
        DialogueUsagePolicy effective = policy == null ? DialogueUsagePolicy.DEFAULT : policy;
        DialogueUsageSavedData.get(context.level()).remember(
                scopeKey(context, effective.scope()), dialogueId, context.level().getGameTime());
    }

    private static String scopeKey(DialogueRuntimeContext context, DialogueUsagePolicy.Scope scope) {
        return switch (scope) {
            // keep persisted scope prefixes compatible with existing worlds
            case PLAYER_VILLAGER -> context.providerId() == null ? "player:" + context.player().getUUID()
                    : "player_villager:" + context.player().getUUID() + ":" + context.providerId();
            case PLAYER -> "player:" + context.player().getUUID();
            case VILLAGER -> context.providerId() == null ? "player:" + context.player().getUUID()
                    : "villager:" + context.providerId();
            case VILLAGE -> "village:" + (context.groupKey() == null || context.groupKey().isBlank()
                    ? context.level().dimension().location() + ":" + context.origin().asLong()
                    : context.groupKey());
            case DIMENSION -> "dimension:" + context.level().dimension().location();
            case WORLD -> "world";
        };
    }
}
