package com.jvn.villagerretaliation.dialogue;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.reputation.VillagerReputationLevel;
import com.jvn.villagerretaliation.reputation.VillagerReputationManager;
import net.minecraft.world.entity.npc.VillagerProfession;

public final class DialogueReputationService {
    private static final long DAY_TICKS = 24000L;
    private static final int MAP_REPORT_REPUTATION_GAIN = 10;
    private static final int COMBAT_SURVIVAL_REPORT_REPUTATION_GAIN = 12;
    private static final int APOLOGY_REPUTATION_GAIN = 4;
    private static final int VILLAGE_DEFENSE_REPORT_REPUTATION_GAIN = 8;

    private DialogueReputationService() {
    }

    public static DialogueReputationEffect apply(DialogueContext context, DialogueRequestType requestType, VillagerInteractionTracker.InteractionState interactionState) {
        if (!VillagerRetaliationConfig.ENABLE_DIALOGUE_REPUTATION_EFFECTS.get()
                || !VillagerRetaliationConfig.ENABLE_VILLAGER_REPUTATION.get()) {
            return DialogueReputationEffect.none(requestType);
        }
        if (context.villager().isBaby()
                && requestType != DialogueRequestType.INSULT
                && requestType != DialogueRequestType.APOLOGY) {
            return DialogueReputationEffect.none(requestType);
        }

        PlannedEffect plannedEffect = planEffect(context, requestType, interactionState);
        if (plannedEffect.delta() == 0) {
            return DialogueReputationEffect.none(requestType);
        }

        long day = context.level().getDayTime() / DAY_TICKS;
        if (isBlockedByCooldown(interactionState, plannedEffect, day)) {
            return new DialogueReputationEffect(
                    requestType,
                    0,
                    plannedEffect.reason(),
                    plannedEffect.cooldownCategory(),
                    interactionState.firstConversation(),
                    false,
                    true,
                    null
            );
        }

        VillagerReputationManager.addDialogueReputation(context.level(), context.villager(), context.player(), plannedEffect.delta());
        VillagerInteractionTracker.rememberDialogueReputation(
                context.level(),
                context.villager(),
                context.player(),
                requestType,
                plannedEffect.delta(),
                plannedEffect.badFirstImpression()
        );
        return new DialogueReputationEffect(
                requestType,
                plannedEffect.delta(),
                plannedEffect.reason(),
                plannedEffect.cooldownCategory(),
                interactionState.firstConversation(),
                true,
                false,
                plannedEffect.responseOverride()
        );
    }

    private static PlannedEffect planEffect(DialogueContext context, DialogueRequestType requestType, VillagerInteractionTracker.InteractionState interactionState) {
        if (context.villager().isBaby()) {
            return requestType == DialogueRequestType.INSULT
                    ? new PlannedEffect(
                    VillagerRetaliationConfig.INSULT_REPUTATION_LOSS.get(),
                    "insult_child",
                    DialogueReputationEffect.CooldownCategory.NEGATIVE,
                    false,
                    response(context, "reputation.insult_child")
            )
                    : PlannedEffect.none();
        }
        if (isDialogueOptionExhausted(context, requestType, interactionState)) {
            return new PlannedEffect(
                    VillagerRetaliationConfig.REPEATED_QUESTION_REPUTATION_LOSS.get(),
                    "repeated_" + requestType.name().toLowerCase(java.util.Locale.ROOT),
                    DialogueReputationEffect.CooldownCategory.NEGATIVE,
                    false,
                    annoyedOptionResponse(context, requestType)
            );
        }
        return switch (requestType) {
            case CHAT -> PlannedEffect.none();
            case GREETING -> planGreeting(context, interactionState.firstConversation());
            case QUESTION -> planQuestion(context);
            case GIFT_PREFERENCES -> PlannedEffect.none();
            case MAP_REPORT -> new PlannedEffect(
                    MAP_REPORT_REPUTATION_GAIN,
                    "map_report",
                    DialogueReputationEffect.CooldownCategory.NONE,
                    false,
                    null
            );
            case STORY_HINT_REPORT -> new PlannedEffect(
                    MAP_REPORT_REPUTATION_GAIN,
                    "story_hint_report",
                    DialogueReputationEffect.CooldownCategory.NONE,
                    false,
                    null
            );
            case COMBAT_SURVIVAL_REPORT -> new PlannedEffect(
                    COMBAT_SURVIVAL_REPORT_REPUTATION_GAIN,
                    "combat_survival_report",
                    DialogueReputationEffect.CooldownCategory.NONE,
                    false,
                    null
            );
            case APOLOGY -> context.hasUnapologizedRememberedHarm()
                    ? new PlannedEffect(
                    APOLOGY_REPUTATION_GAIN,
                    "apology",
                    DialogueReputationEffect.CooldownCategory.NONE,
                    false,
                    null
            )
                    : PlannedEffect.none();
            case VILLAGE_DEFENSE_REPORT -> context.hasUnreportedVillageDefense()
                    ? new PlannedEffect(
                    VILLAGE_DEFENSE_REPORT_REPUTATION_GAIN,
                    "village_defense_report",
                    DialogueReputationEffect.CooldownCategory.NONE,
                    false,
                    null
            )
                    : PlannedEffect.none();
            case STORY -> planStory(context);
            case JOKE -> planJoke(context);
            case INSULT -> planInsult(context, interactionState.firstConversation());
        };
    }

    private static PlannedEffect planGreeting(DialogueContext context, boolean firstConversation) {
        if (context.reputationLevel().trustRank() < VillagerReputationLevel.NEUTRAL.trustRank()) {
            return PlannedEffect.none();
        }
        if (!firstConversation) {
            return context.reputationLevel().trustRank() >= VillagerReputationLevel.TRUSTED.trustRank()
                    && context.random().nextInt(100) < 15
                    ? positive(VillagerRetaliationConfig.GREETING_REPUTATION_GAIN.get(), "greeting", occasionalPositiveResponse(context))
                    : PlannedEffect.none();
        }
        return positive(VillagerRetaliationConfig.FIRST_GREETING_REPUTATION_GAIN.get(), "first_greeting", occasionalPositiveResponse(context));
    }

    private static PlannedEffect planQuestion(DialogueContext context) {
        if (context.reputationLevel().trustRank() < VillagerReputationLevel.NEUTRAL.trustRank()) {
            return PlannedEffect.none();
        }
        int chance = context.reputationLevel().trustRank() >= VillagerReputationLevel.TRUSTED.trustRank() ? 55 : 30;
        return context.random().nextInt(100) < chance
                ? positive(VillagerRetaliationConfig.QUESTION_REPUTATION_GAIN.get(), "question", occasionalPositiveResponse(context))
                : PlannedEffect.none();
    }

    private static boolean isDialogueOptionExhausted(DialogueContext context, DialogueRequestType requestType, VillagerInteractionTracker.InteractionState interactionState) {
        if (requestType == DialogueRequestType.INSULT
                || requestType == DialogueRequestType.MAP_REPORT
                || requestType == DialogueRequestType.STORY_HINT_REPORT
                || requestType == DialogueRequestType.COMBAT_SURVIVAL_REPORT
                || requestType == DialogueRequestType.APOLOGY
                || requestType == DialogueRequestType.VILLAGE_DEFENSE_REPORT) {
            return false;
        }
        int limit = repeatedDialogueLimit(context.reputationLevel());
        return limit >= 0 && interactionState.requestUseCount(requestType) >= limit;
    }

    private static int repeatedDialogueLimit(VillagerReputationLevel reputationLevel) {
        int limit = VillagerRetaliationConfig.REPEATED_QUESTION_POSITIVE_LIMIT.get();
        if (limit < 0) {
            return limit;
        }
        return limit + switch (reputationLevel) {
            case ROYALTY -> VillagerRetaliationConfig.ROYALTY_REPEATED_DIALOGUE_LIMIT_BONUS.get();
            case REVERED -> VillagerRetaliationConfig.REVERED_REPEATED_DIALOGUE_LIMIT_BONUS.get();
            case RESPECTED -> VillagerRetaliationConfig.RESPECTED_REPEATED_DIALOGUE_LIMIT_BONUS.get();
            case TRUSTED -> VillagerRetaliationConfig.TRUSTED_REPEATED_DIALOGUE_LIMIT_BONUS.get();
            default -> 0;
        };
    }

    private static PlannedEffect planStory(DialogueContext context) {
        if (context.reputationLevel().trustRank() < VillagerReputationLevel.NEUTRAL.trustRank()) {
            return PlannedEffect.none();
        }
        int chance = context.reputationLevel().trustRank() >= VillagerReputationLevel.TRUSTED.trustRank() ? 65 : 40;
        if (context.profession() == VillagerProfession.LIBRARIAN
                || context.profession() == VillagerProfession.CLERIC
                || context.profession() == VillagerProfession.NITWIT) {
            chance += 15;
        }
        return context.random().nextInt(100) < chance
                ? positive(VillagerRetaliationConfig.STORY_REPUTATION_GAIN.get(), "story", occasionalPositiveResponse(context))
                : PlannedEffect.none();
    }

    private static PlannedEffect planJoke(DialogueContext context) {
        int goodChance = switch (context.reputationLevel()) {
            case ROYALTY, REVERED -> 85;
            case RESPECTED, TRUSTED -> 70;
            case NEUTRAL -> 50;
            case SUSPICIOUS -> 35;
            case HOSTILE -> 25;
            case DESPISED, FEARED -> 15;
        };
        if (context.profession() == VillagerProfession.NITWIT) {
            goodChance += 10;
        }

        if (context.random().nextInt(100) < goodChance) {
            return new PlannedEffect(
                    VillagerRetaliationConfig.JOKE_REPUTATION_GAIN.get(),
                    "joke_landed",
                    DialogueReputationEffect.CooldownCategory.JOKE,
                    false,
                    occasionalPositiveResponse(context)
            );
        }
        return new PlannedEffect(
                VillagerRetaliationConfig.JOKE_REPUTATION_LOSS.get(),
                "joke_missed",
                DialogueReputationEffect.CooldownCategory.JOKE,
                false,
                context.random().nextInt(3) == 0 ? response(context, "reputation.joke_missed") : null
        );
    }

    private static PlannedEffect planInsult(DialogueContext context, boolean firstConversation) {
        int delta = firstConversation
                ? VillagerRetaliationConfig.FIRST_INSULT_REPUTATION_LOSS.get()
                : VillagerRetaliationConfig.INSULT_REPUTATION_LOSS.get();
        if (!firstConversation && context.reputationLevel().trustRank() >= VillagerReputationLevel.TRUSTED.trustRank()) {
            delta *= 2;
        } else if (!firstConversation && context.reputationLevel().trustRank() <= VillagerReputationLevel.SUSPICIOUS.trustRank()) {
            delta += -1;
        }
        return new PlannedEffect(
                delta,
                firstConversation ? "first_insult" : "insult",
                DialogueReputationEffect.CooldownCategory.NEGATIVE,
                firstConversation,
                angryResponse(context)
        );
    }

    private static boolean isBlockedByCooldown(VillagerInteractionTracker.InteractionState interactionState, PlannedEffect plannedEffect, long day) {
        if (plannedEffect.delta() < 0 || plannedEffect.cooldownCategory() == DialogueReputationEffect.CooldownCategory.NONE) {
            return false;
        }
        return !hasDayCooldownElapsed(
                day,
                interactionState.lastPositiveDialogueReputationDay(),
                VillagerRetaliationConfig.DIALOGUE_POSITIVE_REPUTATION_COOLDOWN_DAYS.get()
        );
    }

    private static boolean hasDayCooldownElapsed(long day, long lastDay, int cooldownDays) {
        if (cooldownDays <= 0 || lastDay == Long.MIN_VALUE) {
            return true;
        }
        return day >= lastDay + cooldownDays;
    }

    private static PlannedEffect positive(int delta, String reason, String responseOverride) {
        return new PlannedEffect(delta, reason, DialogueReputationEffect.CooldownCategory.POSITIVE, false, responseOverride);
    }

    private static String occasionalPositiveResponse(DialogueContext context) {
        if (context.random().nextInt(4) != 0) {
            return null;
        }
        return response(context, "reputation.positive");
    }

    private static String angryResponse(DialogueContext context) {
        return response(context, "reputation.angry");
    }

    private static String annoyedOptionResponse(DialogueContext context, DialogueRequestType requestType) {
        if (requestType == DialogueRequestType.QUESTION) {
            return response(context, "reputation.repeated_question");
        }
        return response(context, "reputation.repeated_dialogue");
    }

    private static String response(DialogueContext context, String key) {
        return VillagerDialogueResources.message(context, key).orElse("");
    }

    private record PlannedEffect(
            int delta,
            String reason,
            DialogueReputationEffect.CooldownCategory cooldownCategory,
            boolean badFirstImpression,
            String responseOverride
    ) {
        static PlannedEffect none() {
            return new PlannedEffect(0, "none", DialogueReputationEffect.CooldownCategory.NONE, false, null);
        }
    }
}
