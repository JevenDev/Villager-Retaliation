package com.jvn.villagerretaliation.campaign.content;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

public record CampaignDefinition(ResourceLocation id, int revision, String fingerprint, String titleKey, String localizationPrefix,
        List<ResourceLocation> questBundles, Map<String, Chapter> chapters, Map<String, Milestone> milestones,
        Map<String, Fact> facts, ResourceLocation source) {
    public CampaignDefinition {
        questBundles = List.copyOf(questBundles);
        chapters = ordered(chapters);
        milestones = ordered(milestones);
        facts = ordered(facts);
    }

    private static <T> Map<String, T> ordered(Map<String, T> values) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    public record Chapter(String id, String titleKey, List<Prerequisite> prerequisites,
            List<ResourceLocation> quests, String pointer) {
        public Chapter {
            prerequisites = List.copyOf(prerequisites);
            quests = List.copyOf(quests);
        }
    }

    public record Prerequisite(String chapter, String milestone, String outcome, String pointer) {
    }

    public record Milestone(String id, String chapter, ResourceLocation quest, String stage, boolean irreversible,
            Map<String, Outcome> outcomes, CatchUp catchUp, Observation observation, String pointer) {
        public Milestone {
            outcomes = ordered(outcomes);
        }

        public Milestone(String id, String chapter, ResourceLocation quest, boolean irreversible,
                Map<String, Outcome> outcomes, CatchUp catchUp, Observation observation, String pointer) {
            this(id, chapter, quest, "", irreversible, outcomes, catchUp, observation, pointer);
        }
    }

    public record Outcome(String id, String titleKey, String pointer) {
    }

    public record CatchUp(ResourceLocation quest, String stage, String pointer) {
    }

    public record Observation(ResourceLocation event, String outcome, String pointer) {
    }

    public record Fact(String id, FactScope scope, FactKind kind, String pointer) {
    }

    public enum FactScope { WORLD, PLAYER }

    public enum FactKind { TAG, VARIABLE, COUNTER }
}
