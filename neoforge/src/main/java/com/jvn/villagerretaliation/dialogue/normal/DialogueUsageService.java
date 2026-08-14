package com.jvn.villagerretaliation.dialogue.normal;

import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.dialogue.VillagerInteractionTracker;

/** Applies one usage policy consistently regardless of the dialogue surface. */
public final class DialogueUsageService {
    private DialogueUsageService() {
    }

    public static boolean available(DialogueContext context, String dialogueId, DialogueUsagePolicy policy) {
        if (context == null || dialogueId == null || dialogueId.isBlank()) {
            return true;
        }
        DialogueUsagePolicy effective = policy == null ? DialogueUsagePolicy.DEFAULT : policy;
        if (effective.cooldownTicks() <= 0L && effective.maxUses() <= 0) {
            return true;
        }
        DialogueUsageSavedData.Usage usage = DialogueUsageSavedData.get(context.level())
                .usage(scopeKey(context, effective.scope()), dialogueId);
        if (usage.count() == 0 && effective.scope() == DialogueUsagePolicy.Scope.PLAYER_VILLAGER) {
            VillagerInteractionTracker.DialogueUsage legacy = VillagerInteractionTracker.dialogueUsage(
                    context.level(), context.villager(), context.player(), dialogueId);
            usage = new DialogueUsageSavedData.Usage(legacy.count(), legacy.lastUsedGameTime());
        }
        if (effective.maxUses() > 0 && usage.count() >= effective.maxUses()) {
            return false;
        }
        return effective.cooldownTicks() <= 0L
                || usage.lastUsedGameTime() == Long.MIN_VALUE
                || context.level().getGameTime() >= usage.lastUsedGameTime() + effective.cooldownTicks();
    }

    public static void record(DialogueContext context, String dialogueId, DialogueUsagePolicy policy) {
        if (context == null || dialogueId == null || dialogueId.isBlank()) {
            return;
        }
        DialogueUsagePolicy effective = policy == null ? DialogueUsagePolicy.DEFAULT : policy;
        DialogueUsageSavedData.get(context.level()).remember(
                scopeKey(context, effective.scope()), dialogueId, context.level().getGameTime());
    }

    private static String scopeKey(DialogueContext context, DialogueUsagePolicy.Scope scope) {
        return switch (scope) {
            case PLAYER_VILLAGER -> "player_villager:" + context.player().getUUID() + ":" + context.villager().getUUID();
            case PLAYER -> "player:" + context.player().getUUID();
            case VILLAGER -> "villager:" + context.villager().getUUID();
            case VILLAGE -> "village:" + (context.villageKey() == null || context.villageKey().isBlank()
                    ? context.level().dimension().location() + ":" + context.villager().blockPosition().asLong()
                    : context.villageKey());
            case DIMENSION -> "dimension:" + context.level().dimension().location();
            case WORLD -> "world";
        };
    }
}
