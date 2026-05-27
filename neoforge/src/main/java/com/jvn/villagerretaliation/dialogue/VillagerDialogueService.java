package com.jvn.villagerretaliation.dialogue;

import com.jvn.villagerretaliation.combat.PacifyPaymentOffer;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.mood.VillagerMood;
import com.jvn.villagerretaliation.mood.VillagerMoodState;
import com.jvn.villagerretaliation.reputation.VillagerReputationLevel;
import com.jvn.villagerretaliation.combat.VillagerPacificationResult;
import com.jvn.toucanlib.util.ToucanRandom;
import com.jvn.villagerretaliation.interaction.VillagerGiftPreferences;
import com.jvn.villagerretaliation.village.VillageEventMemory;
import com.jvn.villagerretaliation.villager.VillagerPresetNameRegistry;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.UUID;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.AbstractVillager;

public final class VillagerDialogueService {
    private VillagerDialogueService() {
    }

    public static DialogueResult select(DialogueContext context, DialogueRequestType requestType, List<String> recentDialogueIds) {
        return select(context, requestType, "", recentDialogueIds);
    }

    public static DialogueResult select(DialogueContext context, DialogueOptionDefinition option, List<String> recentDialogueIds) {
        return select(context, option.requestType(), option.id(), recentDialogueIds);
    }

    public static DialogueExplanation explain(
            DialogueContext context,
            DialogueRequestType requestType,
            String requestedOptionId,
            List<String> recentDialogueIds) {
        DialogueDisposition disposition = moodFor(context);
        List<DialogueLine> availableLines = availableLines(context);
        List<DialogueLine> matched = availableLines.stream()
                .filter(line -> line.matches(context, requestType, requestedOptionId, disposition))
                .toList();
        boolean usedNeutralFallback = false;
        if (matched.isEmpty() && disposition != DialogueDisposition.NEUTRAL) {
            List<DialogueLine> neutralMatches = availableLines.stream()
                    .filter(line -> line.matches(context, requestType, requestedOptionId, DialogueDisposition.NEUTRAL))
                    .toList();
            if (!neutralMatches.isEmpty()) {
                matched = neutralMatches;
                usedNeutralFallback = true;
            }
        }

        List<DialogueCandidateExplanation> candidates = matched.stream()
                .sorted(Comparator.comparingInt(VillagerDialogueService::effectiveWeight).reversed()
                        .thenComparing(DialogueLine::id))
                .map(line -> new DialogueCandidateExplanation(
                        line.id(),
                        line.weight(),
                        line.specificityScore(),
                        effectiveWeight(line),
                        line.recentlyUsed(recentDialogueIds),
                        line.hasFreshVariant(recentDialogueIds)))
                .toList();
        Set<String> candidateIds = candidates.stream()
                .map(DialogueCandidateExplanation::id)
                .collect(java.util.stream.Collectors.toSet());
        Map<String, Long> rejectionCounts = availableLines.stream()
                .filter(line -> !candidateIds.contains(line.id()))
                .map(line -> rejectionReason(line, context, requestType, requestedOptionId, disposition))
                .collect(java.util.stream.Collectors.groupingBy(reason -> reason, java.util.LinkedHashMap::new, java.util.stream.Collectors.counting()));
        int totalWeight = matched.stream().mapToInt(VillagerDialogueService::effectiveWeight).sum();
        String fallbackReason = candidates.isEmpty()
                ? "No weighted line matched; dialogue.fallback will be used."
                : usedNeutralFallback
                        ? "No line matched current disposition " + disposition.name().toLowerCase(Locale.ROOT) + "; using neutral fallback pool."
                        : "";
        return new DialogueExplanation(
                availableLines.size(),
                candidates,
                rejectionCounts,
                totalWeight,
                disposition,
                usedNeutralFallback,
                fallbackReason);
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
        Optional<DialogueResult> containerTheftMemory = selectContainerTheftMemoryLine(context, requestType, recentDialogueIds);
        if (containerTheftMemory.isPresent()) {
            return containerTheftMemory.get();
        }

        DialogueDisposition disposition = moodFor(context);
        List<DialogueLine> availableLines = availableLines(context);
        List<DialogueLine> candidates = availableLines.stream()
                .filter(line -> line.matches(context, requestType, requestedOptionId, disposition))
                .sorted(Comparator.comparingInt(line -> line.recentlyUsed(recentDialogueIds) ? 1 : 0))
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
                .filter(line -> line.hasFreshVariant(recentDialogueIds))
                .toList();
        if (!freshCandidates.isEmpty()) {
            candidates = freshCandidates;
        }

        int totalWeight = candidates.stream().mapToInt(VillagerDialogueService::effectiveWeight).sum();
        int selected = context.random().nextInt(Math.max(1, totalWeight));
        for (DialogueLine candidate : candidates) {
            selected -= effectiveWeight(candidate);
            if (selected < 0) {
                return resolveText(candidate, context, recentDialogueIds);
            }
        }

        DialogueLine fallback = candidates.getLast();
        return resolveText(fallback, context, recentDialogueIds);
    }

    public static String selectOpeningGreeting(DialogueContext context) {
        Optional<String> giftMemory = selectOpeningGiftMemoryLine(context);
        if (giftMemory.isPresent()) {
            return giftMemory.get();
        }
        Optional<String> containerTheftMemory = selectOpeningContainerTheftMemoryLine(context);
        if (containerTheftMemory.isPresent()) {
            return containerTheftMemory.get();
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

    public static String selectPacifyLine(DialogueContext context, VillagerPacificationResult result, PacifyPaymentOffer payment) {
        return VillagerDialogueResources.pacifyLine(context, result, payment).orElse("");
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
        if (context.hasRecentPlayerEvent(VillageEventMemory.EventTag.PLAYER_CONTAINER_THEFT)) {
            moodRank--;
            maxDrift = Math.max(maxDrift, 2);
        }
        if (context.hasRecentEvent(VillageEventMemory.EventTag.VILLAGER_DEATH, VillageEventMemory.EventTag.RAID)) {
            moodRank--;
        }
        if (context.weather() == DialogueContext.WeatherState.THUNDER) {
            moodRank--;
        }
        int explicitMoodAdjustment = explicitMoodRankAdjustment(context);
        if (explicitMoodAdjustment != 0) {
            moodRank += explicitMoodAdjustment;
            maxDrift = Math.max(maxDrift, Math.abs(explicitMoodAdjustment));
        }
        return clamp(moodRank, baselineRank - maxDrift, baselineRank + maxDrift);
    }

    private static int explicitMoodRankAdjustment(DialogueContext context) {
        VillagerMoodState moodState = context.moodState();
        if (moodState == null || moodState.isNeutral()) {
            return 0;
        }

        int step = moodState.intensity() >= 70 ? 2 : moodState.intensity() >= 25 ? 1 : 0;
        if (step == 0) {
            return 0;
        }
        boolean playerCausedMood = context.player().getUUID().equals(moodState.sourcePlayerId());
        VillagerMood mood = moodState.primaryMood();
        return switch (mood) {
            case GRATEFUL -> playerCausedMood || moodState.sourcePlayerId() == null ? step : 0;
            case CONTENT, HOPEFUL, PROUD -> Math.min(step, 1);
            case AFRAID, ANGRY -> -step;
            case SUSPICIOUS, GRIEVING, STRESSED, LONELY -> -Math.min(step, 1);
            case PROTECTIVE -> playerCausedMood ? -step : 0;
            case NEUTRAL -> 0;
        };
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

    public static int effectiveWeight(DialogueLine line) {
        return line.weight() + line.specificityScore() * 8;
    }

    private static List<DialogueLine> availableLines(DialogueContext context) {
        return VillagerDialogueResources.lines(context.level().getServer());
    }

    private static String rejectionReason(
            DialogueLine line,
            DialogueContext context,
            DialogueRequestType requestedType,
            String requestedOptionId,
            DialogueDisposition disposition) {
        if (line.requestType() != requestedType) {
            return "request";
        }
        if (context.villager().isBaby() ? !line.showForBabies() : !line.showForAdults()) {
            return "age visibility";
        }
        if (!line.optionIds().isEmpty() && !line.optionIds().contains(requestedOptionId)) {
            return "option id";
        }
        if (line.firstConversationOnly() && !context.firstConversation()) {
            return "first conversation";
        }
        if (!line.professions().isEmpty() && !line.professions().contains(context.profession())) {
            return "profession";
        }
        if (!line.dispositions().isEmpty() && !line.dispositions().contains(disposition)) {
            return "disposition";
        }
        if (!line.moods().isEmpty()) {
            if (!VillagerRetaliationConfig.ENABLE_VILLAGER_MOODS.get() || !line.moods().contains(context.primaryMood())) {
                return "mood";
            }
            if (line.minMoodIntensity() > 0 && !context.hasMoodIntensityAtLeast(line.minMoodIntensity())) {
                return "mood intensity";
            }
        }
        if (!line.socialAttributeCondition().isEmpty()
                && !line.socialAttributeCondition().matches(context)) {
            return "social attributes";
        }
        if (!line.reputationCondition().matches(context.reputation(), context.reputationLevel())) {
            return "reputation";
        }
        if (!line.weatherStates().isEmpty() && !line.weatherStates().contains(context.weather())) {
            return "weather";
        }
        if (!line.timeOfDays().isEmpty() && !line.timeOfDays().contains(context.timeOfDay())) {
            return "time";
        }
        if (!line.eventTags().isEmpty() && context.recentEvents().stream().noneMatch(event -> line.eventTags().contains(event.tag()))) {
            return "event tags";
        }
        if (!line.playerEventTags().isEmpty() && !context.hasRecentPlayerEvent(line.playerEventTags().toArray(VillageEventMemory.EventTag[]::new))) {
            return "player event tags";
        }
        if ((line.requiresContainerTheftToSelf() && context.recentContainerTheftToThisVillager().isEmpty())
                || (line.requiresContainerTheftFromOther() && context.recentContainerTheftFromAnotherVillager().isEmpty())) {
            return "container theft memory";
        }
        if ((line.requiresRetaliationToSelf() && context.recentRetaliationToThisVillager().isEmpty())
                || (line.requiresRetaliationFromOther() && context.recentRetaliationFromAnotherVillager().isEmpty())) {
            return "retaliation memory";
        }
        if (!line.equipmentCondition().matches(context.villager())) {
            return "villager equipment";
        }
        if (!line.playerItemCondition().matches(context.player())) {
            return "player item";
        }
        if (!line.storyTargetIds().isEmpty()
                && context.shareableStory().map(report -> !line.storyTargetIds().contains(report.targetId())).orElse(true)) {
            return "story target";
        }
        if (!line.conditions().isEmpty() && !line.conditions().stream().allMatch(condition -> condition.matches(context))) {
            return "conditions";
        }
        if (line.weight() <= 0) {
            return "weight";
        }
        return "other";
    }

    private static Optional<DialogueResult> selectGiftMemoryLine(
            DialogueContext context,
            DialogueRequestType requestType,
            List<String> recentDialogueIds) {
        if (requestType != DialogueRequestType.QUESTION && requestType != DialogueRequestType.GREETING) {
            return Optional.empty();
        }
        int chance = requestType == DialogueRequestType.QUESTION ? 45 : 35;
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
                return Optional.of(new DialogueResult(id, villageGiftLine(villageGift.get(), context)));
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
        return villageGift.map(memoryEvent -> villageGiftLine(memoryEvent, context));
    }

    private static Optional<DialogueResult> selectContainerTheftMemoryLine(
            DialogueContext context,
            DialogueRequestType requestType,
            List<String> recentDialogueIds) {
        if (requestType != DialogueRequestType.QUESTION && requestType != DialogueRequestType.GREETING) {
            return Optional.empty();
        }
        int chance = requestType == DialogueRequestType.QUESTION ? 40 : 25;
        if (context.random().nextInt(100) >= chance) {
            return Optional.empty();
        }

        Optional<VillageEventMemory.MemoryEvent> directTheft = context.recentContainerTheftToThisVillager();
        if (directTheft.isPresent()) {
            String id = "container_theft_memory_direct";
            if (!recentDialogueIds.contains(id)) {
                return Optional.of(new DialogueResult(id, directContainerTheftLine(directTheft.get(), context)));
            }
        }

        Optional<VillageEventMemory.MemoryEvent> villageTheft = context.recentContainerTheftFromAnotherVillager();
        if (villageTheft.isPresent()) {
            String id = "container_theft_memory_village";
            if (!recentDialogueIds.contains(id)) {
                return Optional.of(new DialogueResult(id, villageContainerTheftLine(villageTheft.get(), context)));
            }
        }
        return Optional.empty();
    }

    private static Optional<String> selectOpeningContainerTheftMemoryLine(DialogueContext context) {
        if (context.random().nextInt(100) >= 25) {
            return Optional.empty();
        }
        Optional<VillageEventMemory.MemoryEvent> directTheft = context.recentContainerTheftToThisVillager();
        if (directTheft.isPresent()) {
            return Optional.of(directContainerTheftLine(directTheft.get(), context));
        }
        Optional<VillageEventMemory.MemoryEvent> villageTheft = context.recentContainerTheftFromAnotherVillager();
        return villageTheft.map(memoryEvent -> villageContainerTheftLine(memoryEvent, context));
    }

    private static String directContainerTheftLine(VillageEventMemory.MemoryEvent event, DialogueContext context) {
        return VillagerDialogueResources.message(
                context,
                "container_theft_memory.direct",
                containerTheftReplacements(event, context)
        ).orElse("");
    }

    private static String villageContainerTheftLine(VillageEventMemory.MemoryEvent event, DialogueContext context) {
        return VillagerDialogueResources.message(
                context,
                "container_theft_memory.village",
                containerTheftReplacements(event, context)
        ).orElse("");
    }

    private static String directGiftLine(VillageEventMemory.GiftMemory gift, DialogueContext context) {
        return VillagerDialogueResources.message(
                context,
                "gift_memory.direct." + gift.reaction().name().toLowerCase(Locale.ROOT),
                Map.of("gift_item", gift.itemName())
        ).orElse("");
    }

    private static String villageGiftLine(VillageEventMemory.MemoryEvent event, DialogueContext context) {
        VillageEventMemory.GiftMemory gift = event.gift();
        String villagerName = resolveRememberedVillagerName(context, event.sourceId(), gift.villagerName());
        return VillagerDialogueResources.message(
                context,
                "gift_memory.village." + gift.reaction().name().toLowerCase(Locale.ROOT),
                Map.of("gift_item", gift.itemName(), "villager_name", villagerName)
        ).orElse("");
    }

    private static String resolveRememberedVillagerName(DialogueContext context, UUID villagerId, String fallbackName) {
        if (villagerId != null) {
            Entity entity = context.level().getEntity(villagerId);
            if (entity instanceof AbstractVillager villager) {
                String resolvedName = VillagerPresetNameRegistry.resolveDisplayName(villager).getString().trim();
                if (!resolvedName.isBlank()) {
                    return resolvedName;
                }
            }
        }
        return fallbackName == null || fallbackName.isBlank() ? "someone here" : fallbackName;
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
            case MAP_REPORT, STORY_HINT_REPORT, SHARE_STORY, COMBAT_SURVIVAL_REPORT, GEAR_REPORT, RECRUITMENT_FOLLOWUP, GIFT_ADVICE_FOLLOWUP, APOLOGY, VILLAGE_DEFENSE_REPORT -> candidates;
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
            case GREETING, QUESTION, INSULT -> brokenBedCandidates;
            case MAP_REPORT, STORY_HINT_REPORT, SHARE_STORY, COMBAT_SURVIVAL_REPORT, GEAR_REPORT, RECRUITMENT_FOLLOWUP, GIFT_ADVICE_FOLLOWUP, APOLOGY, VILLAGE_DEFENSE_REPORT -> candidates;
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
        String curedVillagerName = curedVillagerName(context);
        return resolveRetaliationText(resolveContainerTheftText(text
                .replace("{attack_weapon}", context.rememberedAttackWeapon())
                .replace("{gear_kind}", context.gearReportKind())
                .replace("{follow_biome}", context.recruitmentMemoryBiome())
                .replace("{follow_distance}", Integer.toString(context.recruitmentMemoryDistanceBlocks()))
                .replace("{cured_villager}", curedVillagerName)
                .replace("{cured_villager_possessive}", toPossessive(curedVillagerName))
                .replace("{partner}", context.relationships().firstRelationshipPartner())
                .replace("{partner_possessive}", toPossessive(context.relationships().firstRelationshipPartner()))
                .replace("{crush}", context.relationships().firstCrush())
                .replace("{crush_possessive}", toPossessive(context.relationships().firstCrush()))
                .replace("{dating_partner}", context.relationships().firstDatingPartner())
                .replace("{dating_partner_possessive}", toPossessive(context.relationships().firstDatingPartner()))
                .replace("{fiance}", context.relationships().firstFiance())
                .replace("{fiance_possessive}", toPossessive(context.relationships().firstFiance()))
                .replace("{romantic_spouse}", context.relationships().firstRomanticSpouse())
                .replace("{romantic_spouse_possessive}", toPossessive(context.relationships().firstRomanticSpouse()))
                .replace("{ex_partner}", context.relationships().firstSeparatedPartner())
                .replace("{ex_partner_possessive}", toPossessive(context.relationships().firstSeparatedPartner()))
                .replace("{late_partner}", context.relationships().firstWidowedPartner())
                .replace("{late_partner_possessive}", toPossessive(context.relationships().firstWidowedPartner()))
                .replace("{parent}", context.familyTree().firstParent())
                .replace("{parent_possessive}", toPossessive(context.familyTree().firstParent()))
                .replace("{sibling}", context.familyTree().firstSibling())
                .replace("{sibling_possessive}", toPossessive(context.familyTree().firstSibling()))
                .replace("{spouse}", context.familyTree().firstSpouse())
                .replace("{spouse_possessive}", toPossessive(context.familyTree().firstSpouse()))
                .replace("{child}", context.familyTree().firstChild())
                .replace("{child_possessive}", toPossessive(context.familyTree().firstChild()))
                .replace("{grandparent}", context.familyTree().firstGrandparent())
                .replace("{grandparent_possessive}", toPossessive(context.familyTree().firstGrandparent()))
                .replace("{ancestor}", context.familyTree().firstAncestor())
                .replace("{ancestor_possessive}", toPossessive(context.familyTree().firstAncestor()))
                .replace("{grandchild}", context.familyTree().firstGrandchild())
                .replace("{grandchild_possessive}", toPossessive(context.familyTree().firstGrandchild()))
                .replace("{descendant}", context.familyTree().firstDescendant())
                .replace("{descendant_possessive}", toPossessive(context.familyTree().firstDescendant()))
                .replace("{aunt_uncle}", context.familyTree().firstAuntUncle())
                .replace("{aunt_uncle_possessive}", toPossessive(context.familyTree().firstAuntUncle()))
                .replace("{cousin}", context.familyTree().firstCousin())
                .replace("{cousin_possessive}", toPossessive(context.familyTree().firstCousin()))
                .replace("{niece_nephew}", context.familyTree().firstNieceNephew())
                .replace("{niece_nephew_possessive}", toPossessive(context.familyTree().firstNieceNephew()))
                .replace("{deceased_family}", context.familyTree().firstDeceasedFamily())
                .replace("{deceased_family_possessive}", toPossessive(context.familyTree().firstDeceasedFamily()))
                .replace("{extended_relative}", context.familyTree().firstExtendedRelative())
                .replace("{extended_relative_possessive}", toPossessive(context.familyTree().firstExtendedRelative()))
                .replace("{relative}", context.familyTree().firstRelative())
                .replace("{relative_possessive}", toPossessive(context.familyTree().firstRelative())), context), context);
    }

    private static String resolveContainerTheftText(String text, DialogueContext context) {
        return context.recentContainerTheft()
                .map(event -> VillagerDialogueResources.resolveTemplate(text, containerTheftReplacements(event, context)))
                .orElse(text);
    }

    private static String resolveRetaliationText(String text, DialogueContext context) {
        return context.recentRetaliation()
                .map(event -> VillagerDialogueResources.resolveTemplate(text, retaliationReplacements(event, context)))
                .orElse(text);
    }

    private static String resolveText(DialogueLine line, DialogueContext context) {
        return resolveText(line.selectText(context.random()), line, context);
    }

    private static DialogueResult resolveText(DialogueLine line, DialogueContext context, List<String> recentDialogueIds) {
        DialogueLine.SelectedText selected = line.selectText(context.random(), recentDialogueIds);
        return new DialogueResult(selected.id(), resolveText(selected.text(), line, context));
    }

    private static String resolveText(String text, DialogueLine line, DialogueContext context) {
        return VillagerDialogueResources.resolveTemplate(
                resolveText(text, context),
                line.playerItemCondition().replacements(context.player())
        );
    }

    private static String curedVillagerName(DialogueContext context) {
        return context.recentEvents().stream()
                .filter(event -> event.tag() == VillageEventMemory.EventTag.PLAYER_CURED_VILLAGER)
                .filter(event -> context.player().getUUID().equals(event.playerId()))
                .max(Comparator.comparingLong(VillageEventMemory.MemoryEvent::gameTime))
                .map(event -> {
                    String fallbackName = event.curedVillager() == null ? "" : event.curedVillager().villagerName();
                    return resolveRememberedVillagerName(context, event.sourceId(), fallbackName);
                })
                .orElse("someone here");
    }

    private static Map<String, String> containerTheftReplacements(VillageEventMemory.MemoryEvent event, DialogueContext context) {
        VillageEventMemory.ContainerTheftMemory theft = event.containerTheft();
        String itemName = theft == null || theft.itemName() == null || theft.itemName().isBlank() ? "items" : theft.itemName();
        String itemId = theft == null || theft.itemId() == null ? "" : theft.itemId();
        String containerName = theft == null || theft.containerName() == null || theft.containerName().isBlank() ? "container" : theft.containerName();
        String lootTable = theft == null || theft.lootTable() == null ? "" : theft.lootTable();
        int count = theft == null ? 0 : Math.max(0, theft.itemCount());
        String villagerName = resolveRememberedVillagerName(
                context,
                event.sourceId(),
                theft == null ? "" : theft.villagerName()
        );
        return Map.of(
                "stolen_item", itemName,
                "stolen_item_id", itemId,
                "stolen_count", Integer.toString(count),
                "stolen_item_count", Integer.toString(count),
                "stolen_stack", count + "x " + itemName,
                "stolen_container", containerName,
                "stolen_loot_table", lootTable,
                "theft_witness", villagerName,
                "theft_witness_possessive", toPossessive(villagerName)
        );
    }

    private static Map<String, String> retaliationReplacements(VillageEventMemory.MemoryEvent event, DialogueContext context) {
        VillageEventMemory.RetaliationMemory retaliation = event.retaliation();
        String targetName = retaliation == null || retaliation.targetName() == null || retaliation.targetName().isBlank()
                ? "threat"
                : retaliation.targetName();
        String targetTypeId = retaliation == null || retaliation.targetTypeId() == null ? "" : retaliation.targetTypeId();
        String targetKind = targetTypeId.isBlank()
                ? targetName.toLowerCase(Locale.ROOT)
                : targetTypeId.contains(":")
                ? targetTypeId.substring(targetTypeId.indexOf(':') + 1).replace('_', ' ')
                : targetTypeId.replace('_', ' ');
        String villagerName = resolveRememberedVillagerName(
                context,
                event.sourceId(),
                retaliation == null ? "" : retaliation.villagerName()
        );
        return Map.of(
                "retaliation_target", targetName,
                "retaliation_target_name", targetName,
                "retaliation_target_kind", targetKind,
                "retaliation_target_type", targetTypeId,
                "retaliation_witness", villagerName,
                "retaliation_witness_possessive", toPossessive(villagerName),
                "target", targetName,
                "target_name", targetName,
                "target_kind", targetKind,
                "target_type", targetTypeId
        );
    }

    private static String toPossessive(String name) {
        if (name == null || name.isBlank()) {
            return "someone here's";
        }
        return name.endsWith("s") || name.endsWith("S") ? name + "'" : name + "'s";
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

    public record DialogueExplanation(
            int totalLines,
            List<DialogueCandidateExplanation> candidates,
            Map<String, Long> rejectionCounts,
            int totalEffectiveWeight,
            DialogueDisposition disposition,
            boolean usedNeutralFallback,
            String fallbackReason) {
    }

    public record DialogueCandidateExplanation(
            String id,
            int weight,
            int specificityScore,
            int effectiveWeight,
            boolean recentlyUsed,
            boolean hasFreshVariant) {
    }
}
