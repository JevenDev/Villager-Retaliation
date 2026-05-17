package com.jvn.villagerretaliation.dialogue;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.reputation.VillagerReputationLevel;
import com.jvn.villagerretaliation.reputation.VillagerReputationManager;
import net.minecraft.world.entity.npc.VillagerProfession;

public final class DialogueReputationService {
    private static final long DAY_TICKS = 24000L;

    private DialogueReputationService() {
    }

    public static DialogueReputationEffect apply(DialogueContext context, DialogueRequestType requestType, VillagerInteractionTracker.InteractionState interactionState) {
        if (!VillagerRetaliationConfig.ENABLE_DIALOGUE_REPUTATION_EFFECTS.get()
                || !VillagerRetaliationConfig.ENABLE_VILLAGER_REPUTATION.get()) {
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
        return switch (requestType) {
            case CHAT -> PlannedEffect.none();
            case GREETING -> planGreeting(context, interactionState.firstConversation());
            case QUESTION -> planQuestion(context, interactionState);
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

    private static PlannedEffect planQuestion(DialogueContext context, VillagerInteractionTracker.InteractionState interactionState) {
        if (interactionState.consecutiveQuestionCount() >= VillagerRetaliationConfig.REPEATED_QUESTION_POSITIVE_LIMIT.get()) {
            return new PlannedEffect(
                    VillagerRetaliationConfig.REPEATED_QUESTION_REPUTATION_LOSS.get(),
                    "repeated_question",
                    DialogueReputationEffect.CooldownCategory.NEGATIVE,
                    false,
                    annoyedQuestionResponse(context)
            );
        }
        return planQuestion(context);
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
                context.random().nextInt(3) == 0 ? "Hmph. Maybe don't quit your day job." : null
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
        if (plannedEffect.delta() < 0) {
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
        return switch (context.random().nextInt(3)) {
            case 0 -> "You know, you're not so bad to talk to.";
            case 1 -> "It's good to speak with someone who listens.";
            default -> "Maybe I misjudged you.";
        };
    }

    private static String angryResponse(DialogueContext context) {
        return switch (context.random().nextInt(3)) {
            case 0 -> "I should have known better than to speak with you.";
            case 1 -> "Careful. Villages remember cruelty.";
            default -> "Say that again and see who still trades with you.";
        };
    }

    private static String annoyedQuestionResponse(DialogueContext context) {
        return switch (context.random().nextInt(3)) {
            case 0 -> "You've asked enough questions for one day.";
            case 1 -> "Again with that? My patience has limits.";
            default -> "I have work to do. Ask something else.";
        };
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
