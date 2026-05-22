package com.jvn.villagerretaliation.dialogue;

import com.jvn.villagerretaliation.reputation.VillagerReputationLevel;
import com.jvn.villagerretaliation.village.VillageEventMemory;
import java.util.EnumSet;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.VillagerProfession;

public record DialogueLine(
        String id,
        String text,
        DialogueRequestType requestType,
        Set<String> optionIds,
        boolean showForAdults,
        boolean showForBabies,
        Set<VillagerProfession> professions,
        Set<DialogueDisposition> dispositions,
        Set<DialogueContext.WeatherState> weatherStates,
        Set<DialogueContext.TimeOfDay> timeOfDays,
        Set<VillageEventMemory.EventTag> eventTags,
        Set<VillageEventMemory.EventTag> playerEventTags,
        Set<ResourceLocation> storyTargetIds,
        boolean requiresRecentBrokenBedMemory,
        boolean requiresRecentDirectHitMemory,
        boolean requiresGearReportUsedInCombat,
        boolean requiresGearReportUnusedInCombat,
        Set<String> recruitmentFollowupScenarios,
        boolean requiresRecruitmentMemory,
        Set<String> recruitmentMemoryScenarios,
        int minRecruitmentFollowDistance,
        boolean requiresRecruitmentBoatTrip,
        boolean requiresRecruitmentOceanCrossing,
        boolean requiresRecruitmentSwimTrip,
        boolean excludesRecruitmentOceanCrossing,
        boolean firstConversationOnly,
        GiftAdviceKind giftAdviceKind,
        int weight
) {
    public boolean matches(DialogueContext context, DialogueRequestType requestedType, DialogueDisposition disposition) {
        return matches(context, requestedType, "", disposition);
    }

    public boolean matches(DialogueContext context, DialogueRequestType requestedType, String requestedOptionId, DialogueDisposition disposition) {
        if (this.requestType != requestedType) {
            return false;
        }
        if (context.villager().isBaby()) {
            if (!this.showForBabies) {
                return false;
            }
        } else if (!this.showForAdults) {
            return false;
        }
        if (!this.optionIds.isEmpty() && !this.optionIds.contains(requestedOptionId)) {
            return false;
        }
        if (this.firstConversationOnly && !context.firstConversation()) {
            return false;
        }
        if (!this.professions.isEmpty() && !this.professions.contains(context.profession())) {
            return false;
        }
        if (!this.dispositions.isEmpty() && !this.dispositions.contains(disposition)) {
            return false;
        }
        if (!this.weatherStates.isEmpty() && !this.weatherStates.contains(context.weather())) {
            return false;
        }
        if (!this.timeOfDays.isEmpty() && !this.timeOfDays.contains(context.timeOfDay())) {
            return false;
        }
        if (!this.eventTags.isEmpty() && context.recentEvents().stream().noneMatch(event -> this.eventTags.contains(event.tag()))) {
            return false;
        }
        if (!this.playerEventTags.isEmpty() && !context.hasRecentPlayerEvent(this.playerEventTags.toArray(VillageEventMemory.EventTag[]::new))) {
            return false;
        }
        if (!this.storyTargetIds.isEmpty()
                && context.shareableStory().map(report -> !this.storyTargetIds.contains(report.targetId())).orElse(true)) {
            return false;
        }
        if (this.requiresRecentBrokenBedMemory && !context.hasRecentBrokenBedMemory()) {
            return false;
        }
        if (this.requiresRecentDirectHitMemory && !context.hasRecentDirectHitMemory()) {
            return false;
        }
        if (this.requiresGearReportUsedInCombat && !context.hasUnreportedGearReportUsedInCombat()) {
            return false;
        }
        if (this.requiresGearReportUnusedInCombat && !context.hasUnreportedGearReportUnusedInCombat()) {
            return false;
        }
        if (!this.recruitmentFollowupScenarios.isEmpty()
                && !this.recruitmentFollowupScenarios.contains(context.recruitmentFollowupScenario())) {
            return false;
        }
        if (this.requiresRecruitmentMemory && !context.hasRecruitmentMemory()) {
            return false;
        }
        if (!this.recruitmentMemoryScenarios.isEmpty()
                && this.recruitmentMemoryScenarios.stream().noneMatch(context::hasRecruitmentMemoryScenario)) {
            return false;
        }
        if (this.minRecruitmentFollowDistance > 0
                && context.recruitmentMemoryDistanceBlocks() < this.minRecruitmentFollowDistance) {
            return false;
        }
        if (this.requiresRecruitmentBoatTrip && !context.hasRecruitmentMemoryBoatTrip()) {
            return false;
        }
        if (this.requiresRecruitmentOceanCrossing && !context.hasRecruitmentMemoryOceanCrossing()) {
            return false;
        }
        if (this.requiresRecruitmentSwimTrip && !context.hasRecruitmentMemorySwimTrip()) {
            return false;
        }
        if (this.excludesRecruitmentOceanCrossing && context.hasRecruitmentMemoryOceanCrossing()) {
            return false;
        }
        return this.weight > 0;
    }

    public boolean isGenerallyUsefulFor(VillagerReputationLevel reputationLevel) {
        DialogueDisposition disposition = VillagerDialogueService.dispositionFor(reputationLevel);
        return this.dispositions.isEmpty() || this.dispositions.contains(disposition);
    }

    public int specificityScore() {
        int score = 0;
        if (!this.professions.isEmpty()) {
            score += 3;
        }
        if (!this.optionIds.isEmpty()) {
            score += 6;
        }
        if (!this.dispositions.isEmpty()) {
            score += 2;
        }
        if (!this.eventTags.isEmpty()) {
            score += 4;
        }
        if (!this.playerEventTags.isEmpty()) {
            score += 5;
        }
        if (!this.storyTargetIds.isEmpty()) {
            score += 8;
        }
        if (this.requiresRecentBrokenBedMemory) {
            score += 5;
        }
        if (this.requiresRecentDirectHitMemory) {
            score += 5;
        }
        if (this.requiresGearReportUsedInCombat || this.requiresGearReportUnusedInCombat) {
            score += 5;
        }
        if (!this.recruitmentFollowupScenarios.isEmpty()) {
            score += 5;
        }
        if (!this.weatherStates.isEmpty()) {
            score += 4;
        }
        if (!this.timeOfDays.isEmpty()) {
            score += 4;
        }
        if (this.firstConversationOnly) {
            score += 2;
        }
        if (this.giftAdviceKind != null) {
            score += 3;
        }
        return score;
    }

    public static Builder builder(String id, DialogueRequestType requestType, String text) {
        return new Builder(id, requestType, text);
    }

    public static class Builder {
        private final String id;
        private final DialogueRequestType requestType;
        private final String text;
        private final Set<String> optionIds = new java.util.HashSet<>();
        private boolean showForAdults = true;
        private boolean showForBabies = true;
        private final Set<VillagerProfession> professions = java.util.HashSet.newHashSet(1);
        private final Set<DialogueDisposition> dispositions = EnumSet.noneOf(DialogueDisposition.class);
        private final Set<DialogueContext.WeatherState> weatherStates = EnumSet.noneOf(DialogueContext.WeatherState.class);
        private final Set<DialogueContext.TimeOfDay> timeOfDays = EnumSet.noneOf(DialogueContext.TimeOfDay.class);
        private final Set<VillageEventMemory.EventTag> eventTags = EnumSet.noneOf(VillageEventMemory.EventTag.class);
        private final Set<VillageEventMemory.EventTag> playerEventTags = EnumSet.noneOf(VillageEventMemory.EventTag.class);
        private final Set<ResourceLocation> storyTargetIds = new java.util.HashSet<>();
        private boolean requiresRecentBrokenBedMemory;
        private boolean requiresRecentDirectHitMemory;
        private boolean requiresGearReportUsedInCombat;
        private boolean requiresGearReportUnusedInCombat;
        private final Set<String> recruitmentFollowupScenarios = new java.util.HashSet<>();
        private boolean requiresRecruitmentMemory;
        private final Set<String> recruitmentMemoryScenarios = new java.util.HashSet<>();
        private int minRecruitmentFollowDistance;
        private boolean requiresRecruitmentBoatTrip;
        private boolean requiresRecruitmentOceanCrossing;
        private boolean requiresRecruitmentSwimTrip;
        private boolean excludesRecruitmentOceanCrossing;
        private boolean firstConversationOnly;
        private GiftAdviceKind giftAdviceKind;
        private int weight = 10;

        protected Builder(String id, DialogueRequestType requestType, String text) {
            this.id = id;
            this.requestType = requestType;
            this.text = text;
        }

        public Builder professions(VillagerProfession... professions) {
            this.professions.addAll(java.util.List.of(professions));
            return this;
        }

        public Builder optionIds(String... optionIds) {
            for (String optionId : optionIds) {
                if (optionId != null && !optionId.isBlank()) {
                    this.optionIds.add(optionId.trim());
                }
            }
            return this;
        }

        public Builder showForAdults(boolean showForAdults) {
            this.showForAdults = showForAdults;
            return this;
        }

        public Builder showForBabies(boolean showForBabies) {
            this.showForBabies = showForBabies;
            return this;
        }

        public Builder dispositions(DialogueDisposition... dispositions) {
            this.dispositions.addAll(java.util.List.of(dispositions));
            return this;
        }

        public Builder eventTags(VillageEventMemory.EventTag... eventTags) {
            this.eventTags.addAll(java.util.List.of(eventTags));
            return this;
        }

        public Builder playerEventTags(VillageEventMemory.EventTag... eventTags) {
            this.playerEventTags.addAll(java.util.List.of(eventTags));
            return this;
        }

        public Builder storyTargetIds(ResourceLocation... storyTargetIds) {
            for (ResourceLocation storyTargetId : storyTargetIds) {
                if (storyTargetId != null) {
                    this.storyTargetIds.add(storyTargetId);
                }
            }
            return this;
        }

        public Builder requiresRecentBrokenBedMemory() {
            this.requiresRecentBrokenBedMemory = true;
            return this;
        }

        public Builder requiresRecentDirectHitMemory() {
            this.requiresRecentDirectHitMemory = true;
            return this;
        }

        public Builder requiresGearReportUsedInCombat() {
            this.requiresGearReportUsedInCombat = true;
            return this;
        }

        public Builder requiresGearReportUnusedInCombat() {
            this.requiresGearReportUnusedInCombat = true;
            return this;
        }

        public Builder recruitmentFollowupScenarios(String... scenarios) {
            for (String scenario : scenarios) {
                if (scenario != null && !scenario.isBlank()) {
                    this.recruitmentFollowupScenarios.add(scenario.trim().toLowerCase(java.util.Locale.ROOT));
                }
            }
            return this;
        }

        public Builder requiresRecruitmentMemory() {
            this.requiresRecruitmentMemory = true;
            return this;
        }

        public Builder recruitmentMemoryScenarios(String... scenarios) {
            for (String scenario : scenarios) {
                if (scenario != null && !scenario.isBlank()) {
                    this.recruitmentMemoryScenarios.add(scenario.trim().toLowerCase(java.util.Locale.ROOT));
                }
            }
            return this;
        }

        public Builder minRecruitmentFollowDistance(int distance) {
            this.minRecruitmentFollowDistance = Math.max(0, distance);
            return this;
        }

        public Builder requiresRecruitmentBoatTrip() {
            this.requiresRecruitmentBoatTrip = true;
            return this;
        }

        public Builder requiresRecruitmentOceanCrossing() {
            this.requiresRecruitmentOceanCrossing = true;
            return this;
        }

        public Builder requiresRecruitmentSwimTrip() {
            this.requiresRecruitmentSwimTrip = true;
            return this;
        }

        public Builder excludesRecruitmentOceanCrossing() {
            this.excludesRecruitmentOceanCrossing = true;
            return this;
        }

        public Builder weatherStates(DialogueContext.WeatherState... weatherStates) {
            this.weatherStates.addAll(java.util.List.of(weatherStates));
            return this;
        }

        public Builder timeOfDays(DialogueContext.TimeOfDay... timeOfDays) {
            this.timeOfDays.addAll(java.util.List.of(timeOfDays));
            return this;
        }

        public Builder firstConversationOnly() {
            this.firstConversationOnly = true;
            return this;
        }

        public Builder giftAdviceKind(GiftAdviceKind giftAdviceKind) {
            this.giftAdviceKind = giftAdviceKind;
            return this;
        }

        public Builder weight(int weight) {
            this.weight = weight;
            return this;
        }

        public DialogueLine build() {
            return new DialogueLine(
                    this.id,
                    this.text,
                    this.requestType,
                    Set.copyOf(this.optionIds),
                    this.showForAdults,
                    this.showForBabies,
                    Set.copyOf(this.professions),
                    Set.copyOf(this.dispositions),
                    Set.copyOf(this.weatherStates),
                    Set.copyOf(this.timeOfDays),
                    Set.copyOf(this.eventTags),
                    Set.copyOf(this.playerEventTags),
                    Set.copyOf(this.storyTargetIds),
                    this.requiresRecentBrokenBedMemory,
                    this.requiresRecentDirectHitMemory,
                    this.requiresGearReportUsedInCombat,
                    this.requiresGearReportUnusedInCombat,
                    Set.copyOf(this.recruitmentFollowupScenarios),
                    this.requiresRecruitmentMemory,
                    Set.copyOf(this.recruitmentMemoryScenarios),
                    this.minRecruitmentFollowDistance,
                    this.requiresRecruitmentBoatTrip,
                    this.requiresRecruitmentOceanCrossing,
                    this.requiresRecruitmentSwimTrip,
                    this.excludesRecruitmentOceanCrossing,
                    this.firstConversationOnly,
                    this.giftAdviceKind,
                    this.weight
            );
        }
    }
}
