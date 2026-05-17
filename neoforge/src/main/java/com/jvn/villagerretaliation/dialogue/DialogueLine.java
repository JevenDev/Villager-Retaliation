package com.jvn.villagerretaliation.dialogue;

import com.jvn.villagerretaliation.reputation.VillagerReputationLevel;
import com.jvn.villagerretaliation.village.VillageEventMemory;
import java.util.EnumSet;
import java.util.Set;
import net.minecraft.world.entity.npc.VillagerProfession;

public record DialogueLine(
        String id,
        String text,
        DialogueRequestType requestType,
        Set<VillagerProfession> professions,
        Set<DialogueDisposition> dispositions,
        Set<DialogueContext.WeatherState> weatherStates,
        Set<VillageEventMemory.EventTag> eventTags,
        boolean firstConversationOnly,
        int weight
) {
    public boolean matches(DialogueContext context, DialogueRequestType requestedType, DialogueDisposition disposition) {
        if (this.requestType != requestedType) {
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
        if (!this.eventTags.isEmpty() && context.recentEvents().stream().noneMatch(event -> this.eventTags.contains(event.tag()))) {
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
        if (!this.dispositions.isEmpty()) {
            score += 2;
        }
        if (!this.eventTags.isEmpty()) {
            score += 4;
        }
        if (!this.weatherStates.isEmpty()) {
            score += 4;
        }
        if (this.firstConversationOnly) {
            score += 2;
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
        private final Set<VillagerProfession> professions = java.util.HashSet.newHashSet(1);
        private final Set<DialogueDisposition> dispositions = EnumSet.noneOf(DialogueDisposition.class);
        private final Set<DialogueContext.WeatherState> weatherStates = EnumSet.noneOf(DialogueContext.WeatherState.class);
        private final Set<VillageEventMemory.EventTag> eventTags = EnumSet.noneOf(VillageEventMemory.EventTag.class);
        private boolean firstConversationOnly;
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

        public Builder dispositions(DialogueDisposition... dispositions) {
            this.dispositions.addAll(java.util.List.of(dispositions));
            return this;
        }

        public Builder eventTags(VillageEventMemory.EventTag... eventTags) {
            this.eventTags.addAll(java.util.List.of(eventTags));
            return this;
        }

        public Builder weatherStates(DialogueContext.WeatherState... weatherStates) {
            this.weatherStates.addAll(java.util.List.of(weatherStates));
            return this;
        }

        public Builder firstConversationOnly() {
            this.firstConversationOnly = true;
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
                    Set.copyOf(this.professions),
                    Set.copyOf(this.dispositions),
                    Set.copyOf(this.weatherStates),
                    Set.copyOf(this.eventTags),
                    this.firstConversationOnly,
                    this.weight
            );
        }
    }
}
