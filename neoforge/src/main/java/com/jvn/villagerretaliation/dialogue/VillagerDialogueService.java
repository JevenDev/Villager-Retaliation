package com.jvn.villagerretaliation.dialogue;

import com.jvn.villagerretaliation.reputation.VillagerReputationLevel;
import com.jvn.villagerretaliation.combat.VillagerPacificationResult;
import com.jvn.toucanlib.util.ToucanRandom;
import com.jvn.villagerretaliation.interaction.VillagerGiftPreferences;
import com.jvn.villagerretaliation.village.VillageEventMemory;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

public final class VillagerDialogueService {
    private VillagerDialogueService() {
    }

    public static DialogueResult select(DialogueContext context, DialogueRequestType requestType, List<String> recentDialogueIds) {
        return select(context, requestType, "", recentDialogueIds);
    }

    public static DialogueResult select(DialogueContext context, DialogueOptionDefinition option, List<String> recentDialogueIds) {
        return select(context, option.requestType(), option.id(), recentDialogueIds);
    }

    private static DialogueResult select(
            DialogueContext context,
            DialogueRequestType requestType,
            String requestedOptionId,
            List<String> recentDialogueIds) {
        if (requestType == DialogueRequestType.STORY) {
            Optional<DialogueResult> storyHint = VillagerStoryHintService.select(context);
            if (storyHint.isPresent()) {
                return storyHint.get();
            }
        }
        Optional<DialogueResult> giftMemory = selectGiftMemoryLine(context, requestType, recentDialogueIds);
        if (giftMemory.isPresent()) {
            return giftMemory.get();
        }

        DialogueDisposition disposition = moodFor(context);
        List<DialogueLine> availableLines = availableLines(context);
        List<DialogueLine> candidates = availableLines.stream()
                .filter(line -> line.matches(context, requestType, requestedOptionId, disposition))
                .sorted(Comparator.comparingInt(line -> recentDialogueIds.contains(line.id()) ? 1 : 0))
                .toList();
        candidates = preferRequestedOptionCandidates(requestedOptionId, candidates);
        candidates = preferDirectHitMemoryCandidates(context, requestType, candidates);
        candidates = preferBrokenBedMemoryCandidates(context, requestType, candidates);
        if (candidates.isEmpty()) {
            candidates = availableLines.stream()
                    .filter(line -> line.matches(context, requestType, requestedOptionId, DialogueDisposition.NEUTRAL))
                    .toList();
            candidates = preferRequestedOptionCandidates(requestedOptionId, candidates);
            candidates = preferDirectHitMemoryCandidates(context, requestType, candidates);
            candidates = preferBrokenBedMemoryCandidates(context, requestType, candidates);
        }
        if (candidates.isEmpty()) {
            return new DialogueResult("fallback", VillagerDialogueResources.message(context, "dialogue.fallback").orElse(""));
        }

        List<DialogueLine> freshCandidates = candidates.stream()
                .filter(line -> !recentDialogueIds.contains(line.id()))
                .toList();
        if (!freshCandidates.isEmpty()) {
            candidates = freshCandidates;
        }

        int totalWeight = candidates.stream().mapToInt(VillagerDialogueService::effectiveWeight).sum();
        int selected = context.random().nextInt(Math.max(1, totalWeight));
        for (DialogueLine candidate : candidates) {
            selected -= effectiveWeight(candidate);
            if (selected < 0) {
                return new DialogueResult(candidate.id(), resolveText(candidate.text(), context));
            }
        }

        DialogueLine fallback = candidates.getLast();
        return new DialogueResult(fallback.id(), resolveText(fallback.text(), context));
    }

    public static String selectOpeningGreeting(DialogueContext context) {
        Optional<String> giftMemory = selectOpeningGiftMemoryLine(context);
        if (giftMemory.isPresent()) {
            return giftMemory.get();
        }
        DialogueDisposition disposition = moodFor(context);
        return selectConversationLine(
                context,
                "hello",
                VillagerDialogueResources.openingLines(context, disposition),
                List.of()
        );
    }

    public static String selectClosingGoodbye(DialogueContext context) {
        DialogueDisposition disposition = moodFor(context);
        return selectConversationLine(
                context,
                "goodbye",
                VillagerDialogueResources.closingLines(context, disposition),
                List.of()
        );
    }

    public static String selectPacifyLine(DialogueContext context, VillagerPacificationResult result, int emeraldCost) {
        return VillagerDialogueResources.pacifyLine(context, result, emeraldCost).orElse("");
    }

    public static DialogueDisposition dispositionFor(VillagerReputationLevel reputationLevel) {
        return switch (reputationLevel) {
            case ROYALTY, REVERED -> DialogueDisposition.RESPECTFUL;
            case RESPECTED, TRUSTED -> DialogueDisposition.FRIENDLY;
            case NEUTRAL -> DialogueDisposition.NEUTRAL;
            case SUSPICIOUS -> DialogueDisposition.CAUTIOUS;
            case HOSTILE -> DialogueDisposition.RUDE;
            case DESPISED -> DialogueDisposition.HOSTILE;
            case FEARED -> DialogueDisposition.FEARFUL;
        };
    }

    public static DialogueDisposition moodFor(DialogueContext context) {
        return moodForRank(smoothedMoodRankFor(context));
    }

    public static DialogueDisposition moodFor(DialogueContext context, DialogueRequestType requestType, DialogueReputationEffect reputationEffect) {
        int moodRank = smoothedMoodRankFor(context);
        if (reputationEffect.blockedByCooldown()
                || (requestType == DialogueRequestType.INSULT && !reputationEffect.applied())) {
            moodRank--;
        }
        return moodForRank(moodRank);
    }

    private static int smoothedMoodRankFor(DialogueContext context) {
        DialogueDisposition baseline = dispositionFor(context.reputationLevel());
        int baselineRank = moodRank(baseline);
        int moodRank = baselineRank;
        int maxDrift = 1;
        if (context.hasRecentDirectHitMemory()) {
            moodRank -= context.reputationLevel() == VillagerReputationLevel.FEARED ? 0 : 2;
            maxDrift = Math.max(maxDrift, 2);
        }
        if (context.hasRecentBrokenBedMemory()) {
            moodRank -= 2;
            maxDrift = Math.max(maxDrift, 2);
        }
        if (context.hasRecentNegativeDialogueMoodMemory()) {
            moodRank--;
        }
        if (context.badFirstImpression()) {
            moodRank--;
        }
        if (context.hasRecentPositiveDialogueMoodMemory()) {
            moodRank++;
        }
        moodRank += context.recentGiftToThisVillager()
                .map(event -> switch (event.gift().reaction()) {
                    case LOVED -> 2;
                    case LIKED -> 1;
                    case NEUTRAL -> 0;
                    case DISLIKED -> -1;
                    case HATED -> -2;
                })
                .orElse(0);
        if (context.recentGiftToThisVillager()
                .map(event -> event.gift().reaction() == VillagerGiftPreferences.GiftReaction.LOVED
                        || event.gift().reaction() == VillagerGiftPreferences.GiftReaction.HATED)
                .orElse(false)) {
            maxDrift = Math.max(maxDrift, 2);
        }
        moodRank += context.recentGiftToAnotherVillager()
                .map(event -> switch (event.gift().reaction()) {
                    case LOVED, LIKED -> 1;
                    case NEUTRAL -> 0;
                    case DISLIKED, HATED -> -1;
                })
                .orElse(0);
        if (context.hasRecentPlayerEvent(VillageEventMemory.EventTag.PLAYER_DEFENDED_VILLAGE, VillageEventMemory.EventTag.PLAYER_DEFENDED_RAID)) {
            moodRank++;
        }
        if (context.hasRecentPlayerEvent(VillageEventMemory.EventTag.PLAYER_ATTACKED_VILLAGER)) {
            moodRank -= 2;
            maxDrift = Math.max(maxDrift, 2);
        }
        if (context.hasRecentEvent(VillageEventMemory.EventTag.VILLAGER_DEATH, VillageEventMemory.EventTag.RAID)) {
            moodRank--;
        }
        if (context.weather() == DialogueContext.WeatherState.THUNDER) {
            moodRank--;
        }
        return clamp(moodRank, baselineRank - maxDrift, baselineRank + maxDrift);
    }

    private static int moodRank(DialogueDisposition disposition) {
        return switch (disposition) {
            case RESPECTFUL -> 3;
            case FRIENDLY -> 2;
            case NEUTRAL -> 1;
            case CAUTIOUS -> 0;
            case RUDE -> -1;
            case HOSTILE -> -2;
            case FEARFUL -> -3;
        };
    }

    private static DialogueDisposition moodForRank(int rank) {
        if (rank >= 3) {
            return DialogueDisposition.RESPECTFUL;
        }
        if (rank == 2) {
            return DialogueDisposition.FRIENDLY;
        }
        if (rank == 1) {
            return DialogueDisposition.NEUTRAL;
        }
        if (rank == 0) {
            return DialogueDisposition.CAUTIOUS;
        }
        if (rank == -1) {
            return DialogueDisposition.RUDE;
        }
        if (rank == -2) {
            return DialogueDisposition.HOSTILE;
        }
        return DialogueDisposition.FEARFUL;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int effectiveWeight(DialogueLine line) {
        return line.weight() + line.specificityScore() * 8;
    }

    private static List<DialogueLine> availableLines(DialogueContext context) {
        return VillagerDialogueResources.lines(context.level().getServer());
    }

    private static Optional<DialogueResult> selectGiftMemoryLine(
            DialogueContext context,
            DialogueRequestType requestType,
            List<String> recentDialogueIds) {
        if (requestType != DialogueRequestType.CHAT && requestType != DialogueRequestType.GREETING) {
            return Optional.empty();
        }
        int chance = requestType == DialogueRequestType.CHAT ? 45 : 35;
        if (context.random().nextInt(100) >= chance) {
            return Optional.empty();
        }

        Optional<VillageEventMemory.MemoryEvent> directGift = context.recentGiftToThisVillager();
        if (directGift.isPresent()) {
            String id = "gift_memory_direct_" + directGift.get().gift().reaction().name().toLowerCase();
            if (!recentDialogueIds.contains(id)) {
                return Optional.of(new DialogueResult(id, directGiftLine(directGift.get().gift(), context)));
            }
        }

        Optional<VillageEventMemory.MemoryEvent> villageGift = context.recentGiftToAnotherVillager();
        if (villageGift.isPresent()) {
            String id = "gift_memory_village_" + villageGift.get().gift().reaction().name().toLowerCase();
            if (!recentDialogueIds.contains(id)) {
                return Optional.of(new DialogueResult(id, villageGiftLine(villageGift.get().gift(), context)));
            }
        }
        return Optional.empty();
    }

    private static Optional<String> selectOpeningGiftMemoryLine(DialogueContext context) {
        if (context.random().nextInt(100) >= 30) {
            return Optional.empty();
        }
        Optional<VillageEventMemory.MemoryEvent> directGift = context.recentGiftToThisVillager();
        if (directGift.isPresent()) {
            return Optional.of(directGiftLine(directGift.get().gift(), context));
        }
        Optional<VillageEventMemory.MemoryEvent> villageGift = context.recentGiftToAnotherVillager();
        return villageGift.map(memoryEvent -> villageGiftLine(memoryEvent.gift(), context));
    }

    private static String directGiftLine(VillageEventMemory.GiftMemory gift, DialogueContext context) {
        return VillagerDialogueResources.message(
                context,
                "gift_memory.direct." + gift.reaction().name().toLowerCase(Locale.ROOT),
                Map.of("gift_item", gift.itemName())
        ).orElse("");
    }

    private static String villageGiftLine(VillageEventMemory.GiftMemory gift, DialogueContext context) {
        String villagerName = gift.villagerName() == null || gift.villagerName().isBlank() ? "someone here" : gift.villagerName();
        return VillagerDialogueResources.message(
                context,
                "gift_memory.village." + gift.reaction().name().toLowerCase(Locale.ROOT),
                Map.of("gift_item", gift.itemName(), "villager_name", villagerName)
        ).orElse("");
    }

    private static List<DialogueLine> preferDirectHitMemoryCandidates(
            DialogueContext context,
            DialogueRequestType requestType,
            List<DialogueLine> candidates) {
        List<DialogueLine> directHitCandidates = memoryCandidates(
                candidates,
                context.hasRecentDirectHitMemory(),
                DialogueLine::requiresRecentDirectHitMemory
        );

        return switch (requestType) {
            case GREETING, QUESTION, INSULT -> directHitCandidates;
            case CHAT -> context.random().nextInt(100) < 45 ? directHitCandidates : candidates;
            case MAP_REPORT, COMBAT_SURVIVAL_REPORT, APOLOGY, VILLAGE_DEFENSE_REPORT -> candidates;
            default -> candidates;
        };
    }

    private static List<DialogueLine> preferBrokenBedMemoryCandidates(
            DialogueContext context,
            DialogueRequestType requestType,
            List<DialogueLine> candidates) {
        List<DialogueLine> brokenBedCandidates = memoryCandidates(
                candidates,
                context.hasRecentBrokenBedMemory(),
                DialogueLine::requiresRecentBrokenBedMemory
        );

        return switch (requestType) {
            case GREETING, QUESTION, CHAT, INSULT -> brokenBedCandidates;
            case MAP_REPORT, COMBAT_SURVIVAL_REPORT, APOLOGY, VILLAGE_DEFENSE_REPORT -> candidates;
            default -> candidates;
        };
    }

    private static List<DialogueLine> memoryCandidates(
            List<DialogueLine> candidates,
            boolean hasMemory,
            Predicate<DialogueLine> requirement) {
        if (!hasMemory || candidates.isEmpty()) {
            return candidates;
        }

        List<DialogueLine> memoryCandidates = candidates.stream()
                .filter(requirement)
                .toList();
        return memoryCandidates.isEmpty() ? candidates : memoryCandidates;
    }

    private static List<DialogueLine> preferRequestedOptionCandidates(String requestedOptionId, List<DialogueLine> candidates) {
        if (requestedOptionId == null || requestedOptionId.isBlank() || candidates.isEmpty()) {
            return candidates;
        }

        List<DialogueLine> optionCandidates = candidates.stream()
                .filter(line -> line.optionIds().contains(requestedOptionId))
                .toList();
        return optionCandidates.isEmpty() ? candidates : optionCandidates;
    }

    private static String resolveText(String text, DialogueContext context) {
        return text.replace("{attack_weapon}", context.rememberedAttackWeapon());
    }

    private static String selectConversationLine(
            DialogueContext context,
            String fallback,
            List<String> globalLines,
            List<String> professionLines) {
        List<String> candidates = new ArrayList<>(globalLines);
        candidates.addAll(professionLines);
        if (candidates.isEmpty()) {
            return fallback;
        }
        return ToucanRandom.choose(context.random(), candidates);
    }

    public record DialogueResult(String lineId, String text) {
    }
}
