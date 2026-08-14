package com.jvn.villagerretaliation.dialogue.normal;

import com.jvn.villagerretaliation.dialogue.VillagerStoryHintService;
import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.dialogue.DialogueCondition;
import com.jvn.villagerretaliation.dialogue.resources.DialogueTuningResources;
import com.jvn.villagerretaliation.dialogue.resources.VillagerDialogueResources;
import com.jvn.villagerretaliation.item.OminousBannerRecognition;
import com.jvn.villagerretaliation.interaction.VillagerItemText;
import com.jvn.villagerretaliation.combat.PacifyPaymentOffer;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.mood.VillagerMood;
import com.jvn.villagerretaliation.mood.VillagerMoodState;
import com.jvn.villagerretaliation.reputation.VillagerReputationLevel;
import com.jvn.villagerretaliation.combat.VillagerPacificationResult;
import com.jvn.villagerretaliation.interaction.VillagerGiftPreferences;
import com.jvn.villagerretaliation.village.VillageEventMemory;
import com.jvn.villagerretaliation.villager.VillagerPresetNameRegistry;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class VillagerDialogueService {
    private VillagerDialogueService() {
    }

    public static DialogueResult select(DialogueContext context, DialogueRequestType requestType, List<String> recentDialogueIds) {
        return select(context, requestType, "", Set.of(), recentDialogueIds);
    }

    public static DialogueResult select(DialogueContext context, DialogueOptionDefinition option, List<String> recentDialogueIds) {
        return select(context, option.requestType(), option.id(), option.metadata().effectiveRoutingTags(), recentDialogueIds);
    }

    public static DialogueExplanation explain(
            DialogueContext context,
            DialogueRequestType requestType,
            String requestedOptionId,
            List<String> recentDialogueIds) {
        LineCandidatePool pool = lineCandidatePool(context, requestType, requestedOptionId, Set.of(), recentDialogueIds);

        List<DialogueCandidateExplanation> candidates = pool.candidates().stream()
                .sorted(Comparator.comparingInt(DialogueLine::priority).reversed()
                        .thenComparing(Comparator.comparingInt(VillagerDialogueService::effectiveWeight).reversed())
                        .thenComparing(DialogueLine::id))
                .map(line -> new DialogueCandidateExplanation(
                        line.id(),
                        line.source() == null ? "" : line.source().toString(),
                        line.metadata(),
                        line.priority(),
                        line.category(),
                        line.weight(),
                        line.specificityScore(),
                        line.specificityWeight(),
                        line.chance(),
                        effectiveWeight(line),
                        line.recentlyUsed(recentDialogueIds),
                        line.hasFreshVariant(recentDialogueIds)))
                .toList();
        Set<String> candidateIds = candidates.stream()
                .map(DialogueCandidateExplanation::id)
                .collect(java.util.stream.Collectors.toSet());
        Set<String> matchedIds = pool.matched().stream()
                .map(DialogueLine::id)
                .collect(java.util.stream.Collectors.toSet());
        Set<String> weightedIds = pool.weightedPool().stream()
                .map(DialogueLine::id)
                .collect(java.util.stream.Collectors.toSet());
        Map<String, Long> rejectionCounts = pool.availableLines().stream()
                .filter(line -> !candidateIds.contains(line.id()))
                .map(line -> {
                    if (matchedIds.contains(line.id()) && !weightedIds.contains(line.id())) {
                        return pool.preferredIds().contains(line.id()) ? "recently used" : "lower preference";
                    }
                    if (weightedIds.contains(line.id())) {
                        return "lower priority";
                    }
                    return rejectionReason(line, context, requestType, requestedOptionId, pool.disposition());
                })
                .collect(java.util.stream.Collectors.groupingBy(reason -> reason, java.util.LinkedHashMap::new, java.util.stream.Collectors.counting()));
        int totalWeight = pool.candidates().stream().mapToInt(VillagerDialogueService::effectiveWeight).sum();
        String fallbackReason = candidates.isEmpty()
                ? "No weighted line matched; dialogue.fallback will be used."
                : pool.usedNeutralFallback()
                        ? "No line matched current disposition " + pool.disposition().name().toLowerCase(Locale.ROOT) + "; using neutral fallback pool."
                        : "";
        return new DialogueExplanation(
                pool.availableLines().size(),
                candidates,
                rejectionCounts,
                totalWeight,
                pool.disposition(),
                pool.usedNeutralFallback(),
                fallbackReason);
    }

    private static DialogueResult select(
            DialogueContext context,
            DialogueRequestType requestType,
            String requestedOptionId,
            Set<String> requestedTags,
            List<String> recentDialogueIds) {
        boolean feared = context.reputationLevel() == VillagerReputationLevel.FEARED;
        if (!feared && requestType == DialogueRequestType.STORY) {
            Optional<DialogueResult> storyHint = VillagerStoryHintService.select(context);
            if (storyHint.isPresent()) {
                return storyHint.get();
            }
        }
        if (!feared) {
            Optional<DialogueResult> giftMemory = selectGiftMemoryLine(context, requestType, recentDialogueIds);
            if (giftMemory.isPresent()) {
                return giftMemory.get();
            }
            Optional<DialogueResult> containerTheftMemory = selectContainerTheftMemoryLine(context, requestType, recentDialogueIds);
            if (containerTheftMemory.isPresent()) {
                return containerTheftMemory.get();
            }
        }

        LineCandidatePool pool = lineCandidatePool(context, requestType, requestedOptionId, requestedTags, recentDialogueIds);
        List<DialogueLine> candidates = pool.candidates().stream()
                .filter(line -> passesChance(line, context.random()))
                .toList();
        if (candidates.isEmpty()) {
            String fallbackKey = feared ? "dialogue.feared_fallback" : "dialogue.fallback";
            return new DialogueResult("fallback", VillagerDialogueResources.message(context, fallbackKey).orElse(""));
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
        if (OminousBannerRecognition.isDisplaying(context.player())) {
            DialogueDisposition disposition = moodFor(context);
            return resolveText(VillagerDialogueResources.openingLine(context, disposition).orElse("hello"), context);
        }
        if (context.reputationLevel() != VillagerReputationLevel.FEARED) {
            Optional<String> giftMemory = selectOpeningGiftMemoryLine(context);
            if (giftMemory.isPresent()) {
                return giftMemory.get();
            }
            Optional<String> containerTheftMemory = selectOpeningContainerTheftMemoryLine(context);
            if (containerTheftMemory.isPresent()) {
                return containerTheftMemory.get();
            }
            Optional<String> longAbsence = selectOpeningLongAbsenceLine(context);
            if (longAbsence.isPresent()) {
                return longAbsence.get();
            }
        }
        DialogueDisposition disposition = moodFor(context);
        return resolveText(VillagerDialogueResources.openingLine(context, disposition).orElse("hello"), context);
    }

    private static Optional<String> selectOpeningLongAbsenceLine(DialogueContext context) {
        if (!context.hasKnownLastSeenDay()) {
            return Optional.empty();
        }
        long daysSinceLastSeen = context.daysSinceLastSeenCount();
        long minimumDays = Math.max(0L, Math.round(DialogueTuningResources.value(
                context, "opening.long_absence.minimum_days", 3.0D)));
        if (daysSinceLastSeen < minimumDays) {
            return Optional.empty();
        }
        double chance = Math.min(
                DialogueTuningResources.value(context, "opening.long_absence.max_chance", 0.85D),
                DialogueTuningResources.value(context, "opening.long_absence.base_chance", 0.40D)
                        + (daysSinceLastSeen - minimumDays)
                        * DialogueTuningResources.value(context, "opening.long_absence.chance_per_day", 0.10D));
        if (!DialogueTuningResources.passes(context, chance)) {
            return Optional.empty();
        }
        Map<String, String> replacements = Map.of(
                "days_since_seen", context.daysSinceLastSeenCountText(),
                "day_or_days", context.daysSinceLastSeenDayUnit(),
                "days_since_seen_phrase", context.daysSinceLastSeenPhrase()
        );
        return VillagerDialogueResources.professionPriorityMessage(context, "opening.return_after_absence", replacements)
                .or(() -> VillagerDialogueResources.message(context, "opening.return_after_absence", replacements));
    }

    public static String selectClosingGoodbye(DialogueContext context) {
        DialogueDisposition disposition = moodFor(context);
        return resolveText(VillagerDialogueResources.closingLine(context, disposition).orElse("goodbye"), context);
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
        if (context.reputationLevel() == VillagerReputationLevel.FEARED) {
            return baselineRank;
        }
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
        return line.weight() + line.specificityScore() * line.specificityWeight();
    }

    public static boolean passesChance(DialogueLine line, net.minecraft.util.RandomSource random) {
        return line.chance() >= 1.0D || (line.chance() > 0.0D && random.nextDouble() < line.chance());
    }

    public static List<DialogueLine> highestPriority(List<DialogueLine> candidates) {
        return preferHighestPriority(candidates);
    }

    private static List<DialogueLine> preferHighestPriority(List<DialogueLine> candidates) {
        if (candidates.isEmpty()) {
            return candidates;
        }
        int highestPriority = candidates.stream()
                .mapToInt(DialogueLine::priority)
                .max()
                .orElse(0);
        return candidates.stream()
                .filter(line -> line.priority() == highestPriority)
                .toList();
    }

    private static LineCandidatePool lineCandidatePool(
            DialogueContext context,
            DialogueRequestType requestType,
            String requestedOptionId,
            Set<String> requestedTags,
            List<String> recentDialogueIds) {
        List<String> recentIds = recentDialogueIds == null ? List.of() : recentDialogueIds;
        DialogueDisposition disposition = moodFor(context);
        List<DialogueLine> availableLines = availableLines(context);
        List<DialogueLine> matched = matchingLines(availableLines, context, requestType, requestedOptionId, disposition, recentIds);
        boolean usedNeutralFallback = false;
        if (matched.isEmpty()
                && disposition != DialogueDisposition.NEUTRAL
                && context.reputationLevel() != VillagerReputationLevel.FEARED) {
            List<DialogueLine> neutralMatches = matchingLines(
                    availableLines,
                    context,
                    requestType,
                    requestedOptionId,
                    DialogueDisposition.NEUTRAL,
                    recentIds);
            if (!neutralMatches.isEmpty()) {
                matched = neutralMatches;
                usedNeutralFallback = true;
            }
        }

        List<DialogueLine> preferred = preferRequestedOptionCandidates(requestedOptionId, matched);
        preferred = preferMetadataTagCandidates(requestedTags, preferred);
        preferred = preferDirectHitMemoryCandidates(context, requestType, preferred);
        preferred = preferBrokenBedMemoryCandidates(context, requestType, preferred);
        preferred = preferVillageEventCandidates(context, requestType, preferred);
        Set<String> preferredIds = preferred.stream()
                .map(DialogueLine::id)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));

        List<DialogueLine> weightedPool = preferred;
        List<DialogueLine> freshCandidates = weightedPool.stream()
                .filter(line -> line.hasFreshVariant(recentIds))
                .toList();
        if (!freshCandidates.isEmpty()) {
            weightedPool = freshCandidates;
        }
        weightedPool = preferFreshMetadata(weightedPool, availableLines, recentIds);
        return new LineCandidatePool(
                availableLines,
                matched,
                preferredIds,
                weightedPool,
                preferHighestPriority(weightedPool),
                disposition,
                usedNeutralFallback);
    }

    private static List<DialogueLine> preferMetadataTagCandidates(Set<String> requestedTags, List<DialogueLine> candidates) {
        if (requestedTags == null || requestedTags.isEmpty()) return candidates;
        List<DialogueLine> tagged = candidates.stream()
                .filter(line -> line.metadata().effectiveRoutingTags().stream().anyMatch(requestedTags::contains))
                .toList();
        return tagged.isEmpty() ? candidates : tagged;
    }

    private static List<DialogueLine> preferFreshMetadata(
            List<DialogueLine> candidates,
            List<DialogueLine> availableLines,
            List<String> recentIds) {
        if (candidates.size() < 2 || recentIds.isEmpty()) return candidates;
        Set<String> recentGroups = availableLines.stream()
                .filter(line -> line.recentlyUsed(recentIds))
                .flatMap(line -> antiRepeatGroups(line).stream())
                .collect(java.util.stream.Collectors.toSet());
        List<DialogueLine> fresh = candidates.stream()
                .filter(line -> antiRepeatGroups(line).stream().noneMatch(recentGroups::contains))
                .toList();
        return fresh.isEmpty() ? candidates : fresh;
    }

    private static Set<String> antiRepeatGroups(DialogueLine line) {
        Set<String> groups = new java.util.LinkedHashSet<>(line.metadata().effectiveAntiRepeatGroups());
        String category = com.jvn.villagerretaliation.util.ContentTags.normalize(line.category());
        if (!category.isBlank()) {
            groups.add(category);
        }
        return Set.copyOf(groups);
    }

    private static List<DialogueLine> matchingLines(
            List<DialogueLine> availableLines,
            DialogueContext context,
            DialogueRequestType requestType,
            String requestedOptionId,
            DialogueDisposition disposition,
            List<String> recentDialogueIds) {
        List<String> recentIds = recentDialogueIds == null ? List.of() : recentDialogueIds;
        return availableLines.stream()
                .filter(line -> context.reputationLevel() != VillagerReputationLevel.FEARED || isFearSpecific(line))
                .filter(line -> line.matches(context, requestType, requestedOptionId, disposition))
                .sorted(Comparator.comparingInt(line -> line.recentlyUsed(recentIds) ? 1 : 0))
                .toList();
    }

    private static boolean isFearSpecific(DialogueLine line) {
        return line.dispositions().contains(DialogueDisposition.FEARFUL)
                || line.reputationCondition().levels().contains(VillagerReputationLevel.FEARED);
    }

    private static List<DialogueLine> availableLines(DialogueContext context) {
        return VillagerDialogueResources.lines(context);
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
                && (!com.jvn.villagerretaliation.profile.VillagerSocialAttributeBehavior.enabled(
                        VillagerRetaliationConfig.ENABLE_SOCIAL_ATTRIBUTE_DIALOGUE_EFFECTS)
                || !line.socialAttributeCondition().matches(context))) {
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
        if (!line.eventTagIds().isEmpty() && !context.hasRecentEventTag(line.eventTagIds())) {
            return "event tags";
        }
        if (!line.playerEventTags().isEmpty() && !context.hasRecentPlayerEvent(line.playerEventTags().toArray(VillageEventMemory.EventTag[]::new))) {
            return "player event tags";
        }
        if (!line.playerEventTagIds().isEmpty() && !context.hasRecentPlayerEventTag(line.playerEventTagIds())) {
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
        String legacyRejection = legacyRejectionReason(line, context);
        if (!legacyRejection.isBlank()) {
            return legacyRejection;
        }
        if (!line.conditions().isEmpty() && !DialogueCondition.matchesAll(context, line.conditions())) {
            return "conditions";
        }
        if (line.weight() <= 0) {
            return "weight";
        }
        return "other";
    }

    private static String legacyRejectionReason(DialogueLine line, DialogueContext context) {
        if (!line.retaliationTargetEntityTypes().isEmpty()
                && context.recentRetaliation()
                .map(event -> event.retaliation() != null
                        && line.retaliationTargetEntityTypes().contains(ResourceLocation.tryParse(event.retaliation().targetTypeId())))
                .orElse(false) == false) {
            return "retaliation target";
        }
        if (line.requiresRecentBrokenBedMemory() && !context.hasRecentBrokenBedMemory()) return "broken bed memory";
        if (line.requiresRecentDirectHitMemory() && !context.hasRecentDirectHitMemory()) return "direct hit memory";
        if ((line.requiresGearReportUsedInCombat() && !context.hasUnreportedGearReportUsedInCombat())
                || (line.requiresGearReportUnusedInCombat() && !context.hasUnreportedGearReportUnusedInCombat())) {
            return "gear report";
        }
        if (!line.recruitmentFollowupScenarios().isEmpty()
                && !line.recruitmentFollowupScenarios().contains(context.recruitmentFollowupScenario())) return "recruitment follow-up";
        if (line.requiresRecruitmentMemory() && !context.hasRecruitmentMemory()) return "recruitment memory";
        if (!line.recruitmentMemoryScenarios().isEmpty()
                && line.recruitmentMemoryScenarios().stream().noneMatch(context::hasRecruitmentMemoryScenario)) return "recruitment scenario";
        if (!line.recruitmentMemoryBiomeKeys().isEmpty()
                && !line.recruitmentMemoryBiomeKeys().contains(context.recruitmentMemoryBiomeKey())) return "recruitment biome";
        if (line.minRecruitmentFollowDistance() > 0
                && context.recruitmentMemoryDistanceBlocks() < line.minRecruitmentFollowDistance()) return "recruitment distance";
        if ((line.requiresRecruitmentBoatTrip() && !context.hasRecruitmentMemoryBoatTrip())
                || (line.requiresRecruitmentOceanCrossing() && !context.hasRecruitmentMemoryOceanCrossing())
                || (line.requiresRecruitmentSwimTrip() && !context.hasRecruitmentMemorySwimTrip())
                || (line.excludesRecruitmentOceanCrossing() && context.hasRecruitmentMemoryOceanCrossing())) return "recruitment travel";
        if ((line.requiresKnownFamily() && !context.hasKnownFamily())
                || (line.requiresKnownParent() && !context.hasKnownParent())
                || (line.requiresKnownSibling() && !context.hasKnownSibling())
                || (line.requiresKnownSpouse() && !context.hasKnownSpouse())
                || (line.requiresKnownChild() && !context.hasKnownChild())
                || (line.requiresKnownGrandparent() && !context.hasKnownGrandparent())
                || (line.requiresKnownGrandchild() && !context.hasKnownGrandchild())
                || (line.requiresKnownDescendant() && !context.hasKnownDescendant())
                || (line.requiresKnownAuntUncle() && !context.hasKnownAuntUncle())
                || (line.requiresKnownCousin() && !context.hasKnownCousin())
                || (line.requiresKnownNieceNephew() && !context.hasKnownNieceNephew())
                || (line.requiresKnownExtendedFamily() && !context.hasKnownExtendedFamily())
                || (line.requiresKnownDeceasedFamily() && !context.hasKnownDeceasedFamily())) return "family";
        if ((line.requiresKnownRelationship() && !context.hasKnownRelationship())
                || (line.requiresKnownCurrentRelationship() && !context.hasKnownCurrentRelationship())
                || (line.requiresKnownPastRelationship() && !context.hasKnownPastRelationship())
                || (line.requiresKnownCrush() && !context.hasKnownCrush())
                || (line.requiresKnownDatingPartner() && !context.hasKnownDatingPartner())
                || (line.requiresKnownFiance() && !context.hasKnownFiance())
                || (line.requiresKnownRomanticSpouse() && !context.hasKnownRomanticSpouse())
                || (line.requiresKnownSeparatedPartner() && !context.hasKnownSeparatedPartner())
                || (line.requiresKnownWidowedPartner() && !context.hasKnownWidowedPartner())) return "relationship";
        return "";
    }

    private static Optional<DialogueResult> selectGiftMemoryLine(
            DialogueContext context,
            DialogueRequestType requestType,
            List<String> recentDialogueIds) {
        if (requestType != DialogueRequestType.QUESTION && requestType != DialogueRequestType.GREETING) {
            return Optional.empty();
        }
        String chanceKey = requestType == DialogueRequestType.QUESTION
                ? "memory.gift.question_chance"
                : "memory.gift.greeting_chance";
        if (!DialogueTuningResources.passes(context, chanceKey,
                requestType == DialogueRequestType.QUESTION ? 0.45D : 0.35D)) {
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
        if (!DialogueTuningResources.passes(context, "memory.gift.opening_chance", 0.30D)) {
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
        String chanceKey = requestType == DialogueRequestType.QUESTION
                ? "memory.container_theft.question_chance"
                : "memory.container_theft.greeting_chance";
        if (!DialogueTuningResources.passes(context, chanceKey,
                requestType == DialogueRequestType.QUESTION ? 0.40D : 0.25D)) {
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
        if (!DialogueTuningResources.passes(context, "memory.container_theft.opening_chance", 0.25D)) {
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
                Map.of("gift_item", rememberedGiftItemName(gift, context))
        ).orElse("");
    }

    private static String villageGiftLine(VillageEventMemory.MemoryEvent event, DialogueContext context) {
        VillageEventMemory.GiftMemory gift = event.gift();
        String villagerName = resolveRememberedVillagerName(context, event.sourceId(), gift.villagerName());
        return VillagerDialogueResources.message(
                context,
                "gift_memory.village." + gift.reaction().name().toLowerCase(Locale.ROOT),
                Map.of("gift_item", rememberedGiftItemName(gift, context), "villager_name", villagerName)
        ).orElse("");
    }

    private static String rememberedGiftItemName(VillageEventMemory.GiftMemory gift, DialogueContext context) {
        ResourceLocation itemId = gift.itemId() == null ? null : ResourceLocation.tryParse(gift.itemId());
        if (itemId != null && BuiltInRegistries.ITEM.containsKey(itemId)) {
            var item = BuiltInRegistries.ITEM.get(itemId);
            if (item != Items.AIR) {
                return VillagerItemText.dialogueName(
                        context.level().getServer(),
                        context.locale(),
                        new ItemStack(item, Math.max(1, gift.itemCount())));
            }
        }
        String itemName = gift.itemName();
        if (itemName == null || itemName.isBlank()) {
            return "gift";
        }
        return itemName.replaceFirst("^\\d+x\\s+", "");
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

    private static List<DialogueLine> preferVillageEventCandidates(
            DialogueContext context,
            DialogueRequestType requestType,
            List<DialogueLine> candidates) {
        if (requestType != DialogueRequestType.VILLAGE_EVENT_REPORT || candidates.isEmpty()) {
            return candidates;
        }

        Optional<VillageEventMemory.MemoryEvent> event = context.recentUnreportedHostileVillageEventConcern();
        if (event.isEmpty()) {
            return candidates;
        }

        VillageEventMemory.EventTag tag = event.get().tag();
        ResourceLocation tagId = event.get().tagId();
        List<DialogueLine> eventCandidates = candidates.stream()
                .filter(line -> line.eventTags().contains(tag)
                        || line.eventTagIds().contains(tagId)
                        || line.playerEventTags().contains(tag)
                        || line.playerEventTagIds().contains(tagId))
                .toList();
        return eventCandidates.isEmpty() ? candidates : eventCandidates;
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
                .replace("{days_since_seen}", context.daysSinceLastSeenCountText())
                .replace("{day_or_days}", context.daysSinceLastSeenDayUnit())
                .replace("{days_since_seen_phrase}", context.daysSinceLastSeenPhrase())
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
        Optional<String> textKeyResult = resolveTextKey(line, context);
        if (textKeyResult.isPresent()) {
            return textKeyResult.get();
        }
        return resolveText(line.selectText(context.random()), line, context);
    }

    private static DialogueResult resolveText(DialogueLine line, DialogueContext context, List<String> recentDialogueIds) {
        Optional<String> textKeyResult = resolveTextKey(line, context);
        if (textKeyResult.isPresent() || line.lines().isEmpty()) {
            return DialogueResult.fromText(line.id(), textKeyResult.orElse(""), line.textEffects());
        }
        DialogueLine.SelectedText selected = line.selectText(context.random(), recentDialogueIds);
        return DialogueResult.fromText(selected.id(), resolveText(selected.text(), line, context), line.textEffects());
    }

    private static Optional<String> resolveTextKey(DialogueLine line, DialogueContext context) {
        if (line.textKey().isBlank()) {
            return Optional.empty();
        }
        return VillagerDialogueResources.message(
                        context,
                        line.textKey(),
                        line.playerItemCondition().replacements(context.player()))
                .map(text -> resolveText(text, context));
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
        ResourceLocation parsedItemId = ResourceLocation.tryParse(itemId);
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
                "stolen_stack", VillagerItemText.countedName(
                        context.level().getServer(), context.locale(), count, parsedItemId, itemName),
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
                "retaliation_witness_possessive", toPossessive(villagerName)
        );
    }

    private static String toPossessive(String name) {
        if (name == null || name.isBlank()) {
            return "someone here's";
        }
        return name.endsWith("s") || name.endsWith("S") ? name + "'" : name + "'s";
    }

    public record DialogueResult(
            String lineId,
            String text,
            DialogueTextEffects textEffects,
            List<DialogueTextSegment> textSegments) {
        public DialogueResult(String lineId, String text) {
            this(lineId, text, DialogueTextEffects.NONE, DialogueTextSegment.parse(text, DialogueTextEffects.NONE));
        }

        public static DialogueResult fromText(String lineId, String text, DialogueTextEffects textEffects) {
            List<DialogueTextSegment> segments = DialogueTextSegment.parse(text, textEffects);
            return new DialogueResult(lineId, DialogueTextSegment.plainText(segments), textEffects, segments);
        }
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
            String source,
            DialogueEntryMetadata metadata,
            int priority,
            String category,
            int weight,
            int specificityScore,
            int specificityWeight,
            double chance,
            int effectiveWeight,
            boolean recentlyUsed,
            boolean hasFreshVariant) {
    }

    private record LineCandidatePool(
            List<DialogueLine> availableLines,
            List<DialogueLine> matched,
            Set<String> preferredIds,
            List<DialogueLine> weightedPool,
            List<DialogueLine> candidates,
            DialogueDisposition disposition,
            boolean usedNeutralFallback) {
    }
}
