package com.jvn.villagerretaliation.campaign.content;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

public record CampaignDefinition(ResourceLocation id, int revision, String fingerprint, String titleKey, String localizationPrefix,
        List<ResourceLocation> questBundles, Map<String, Chapter> chapters, Map<String, Milestone> milestones,
        Map<String, Fact> facts, Map<String, CampaignRewardDefinition> rewards,
        Map<String, CampaignLocationDefinition> locations, Map<String, CampaignCharacterDefinition> characters,
        Map<String, CampaignEvidenceDefinition> evidence, Map<String, CampaignLocationVariant> locationVariants,
        Map<String, CampaignFixtureEntry> fixtureEntries, ResourceLocation source) {
    public CampaignDefinition {
        questBundles = List.copyOf(questBundles);
        chapters = ordered(chapters);
        milestones = ordered(milestones);
        facts = ordered(facts);
        rewards = ordered(rewards);
        locations = ordered(locations);
        characters = ordered(characters);
        evidence = ordered(evidence);
        locationVariants = ordered(locationVariants);
        fixtureEntries = ordered(fixtureEntries);
    }

    public CampaignDefinition(ResourceLocation id, int revision, String fingerprint, String titleKey, String localizationPrefix,
            List<ResourceLocation> questBundles, Map<String, Chapter> chapters, Map<String, Milestone> milestones,
            Map<String, Fact> facts, Map<String, CampaignRewardDefinition> rewards,
            Map<String, CampaignLocationDefinition> locations, Map<String, CampaignCharacterDefinition> characters, ResourceLocation source) {
        this(id, revision, fingerprint, titleKey, localizationPrefix, questBundles, chapters, milestones, facts, rewards, locations, characters, Map.of(), source);
    }

    public CampaignDefinition(ResourceLocation id, int revision, String fingerprint, String titleKey, String localizationPrefix,
            List<ResourceLocation> questBundles, Map<String, Chapter> chapters, Map<String, Milestone> milestones,
            Map<String, Fact> facts, ResourceLocation source) {
        this(id, revision, fingerprint, titleKey, localizationPrefix, questBundles, chapters, milestones, facts, Map.of(), source);
    }

    public CampaignDefinition(ResourceLocation id, int revision, String fingerprint, String titleKey, String localizationPrefix,
            List<ResourceLocation> questBundles, Map<String, Chapter> chapters, Map<String, Milestone> milestones,
            Map<String, Fact> facts, Map<String, CampaignRewardDefinition> rewards, ResourceLocation source) {
        this(id, revision, fingerprint, titleKey, localizationPrefix, questBundles, chapters, milestones, facts, rewards, Map.of(), source);
    }

    public CampaignDefinition(ResourceLocation id, int revision, String fingerprint, String titleKey, String localizationPrefix,
            List<ResourceLocation> questBundles, Map<String, Chapter> chapters, Map<String, Milestone> milestones,
            Map<String, Fact> facts, Map<String, CampaignRewardDefinition> rewards,
            Map<String, CampaignLocationDefinition> locations, ResourceLocation source) {
        this(id, revision, fingerprint, titleKey, localizationPrefix, questBundles, chapters, milestones, facts, rewards, locations, Map.of(), source);
    }

    public CampaignDefinition(ResourceLocation id, int revision, String fingerprint, String titleKey, String localizationPrefix,
            List<ResourceLocation> questBundles, Map<String, Chapter> chapters, Map<String, Milestone> milestones,
            Map<String, Fact> facts, Map<String, CampaignRewardDefinition> rewards, Map<String, CampaignLocationDefinition> locations,
            Map<String, CampaignCharacterDefinition> characters, Map<String, CampaignEvidenceDefinition> evidence, ResourceLocation source) {
        this(id, revision, fingerprint, titleKey, localizationPrefix, questBundles, chapters, milestones, facts, rewards, locations, characters, evidence, Map.of(), source);
    }

    public CampaignDefinition(ResourceLocation id, int revision, String fingerprint, String titleKey, String localizationPrefix,
            List<ResourceLocation> questBundles, Map<String, Chapter> chapters, Map<String, Milestone> milestones,
            Map<String, Fact> facts, Map<String, CampaignRewardDefinition> rewards, Map<String, CampaignLocationDefinition> locations,
            Map<String, CampaignCharacterDefinition> characters, Map<String, CampaignEvidenceDefinition> evidence,
            Map<String, CampaignLocationVariant> locationVariants, ResourceLocation source) {
        this(id, revision, fingerprint, titleKey, localizationPrefix, questBundles, chapters, milestones, facts, rewards, locations,
                characters, evidence, locationVariants, Map.of(), source);
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

    public record Outcome(String id, String titleKey, List<String> locationVariants, String pointer) {
        public Outcome { locationVariants = List.copyOf(locationVariants); }
        public Outcome(String id, String titleKey, String pointer) { this(id, titleKey, List.of(), pointer); }
    }

    public record CatchUp(ResourceLocation quest, String stage, String pointer) {
    }

    public record Observation(ResourceLocation event, String outcome, String character, String pointer) {
        public static final ResourceLocation CHARACTER_LOST = ResourceLocation.parse("villagerretaliation:campaign_character_lost");

        public Observation(ResourceLocation event, String outcome, String pointer) {
            this(event, outcome, "", pointer);
        }

        public boolean characterLost() { return CHARACTER_LOST.equals(event); }
    }

    public record Fact(String id, FactScope scope, FactKind kind, String pointer) {
    }

    public enum FactScope { WORLD, PLAYER }

    public enum FactKind { TAG, VARIABLE, COUNTER }
}
