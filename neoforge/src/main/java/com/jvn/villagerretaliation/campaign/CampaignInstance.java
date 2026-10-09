package com.jvn.villagerretaliation.campaign;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

public record CampaignInstance(ResourceLocation campaignId, UUID instanceId, int definitionRevision, String fingerprint,
        String title, long createdAt, long worldRevision, Map<String, OutcomeCommit> outcomes,
        Map<UUID, PlayerRecord> players, CampaignFacts facts, Map<UUID, CampaignFixtureReceipt> fixtureEntries) {
    public CampaignInstance {
        outcomes = Collections.unmodifiableMap(new LinkedHashMap<>(outcomes));
        players = Collections.unmodifiableMap(new LinkedHashMap<>(players));
        fixtureEntries = Collections.unmodifiableMap(new LinkedHashMap<>(fixtureEntries));
    }

    public CampaignInstance(ResourceLocation campaignId, UUID instanceId, int definitionRevision, String fingerprint,
            String title, long createdAt, long worldRevision, Map<String, OutcomeCommit> outcomes,
            Map<UUID, PlayerRecord> players) {
        this(campaignId, instanceId, definitionRevision, fingerprint, title, createdAt, worldRevision, outcomes, players, CampaignFacts.empty());
    }

    public CampaignInstance(ResourceLocation campaignId, UUID instanceId, int definitionRevision, String fingerprint,
            String title, long createdAt, long worldRevision, Map<String, OutcomeCommit> outcomes,
            Map<UUID, PlayerRecord> players, CampaignFacts facts) {
        this(campaignId, instanceId, definitionRevision, fingerprint, title, createdAt, worldRevision, outcomes, players, facts, Map.of());
    }

    public record OutcomeCommit(UUID operationId, String milestone, String outcome, String title,
            long revision, long committedAt, Origin origin) {
    }

    public record PlayerRecord(long revision, Set<String> participation, Map<String, String> discoveredOutcomes,
            Map<String, MilestoneProgress> milestones, Set<ResourceLocation> completedQuests) {
        public PlayerRecord {
            participation = Set.copyOf(participation);
            discoveredOutcomes = Collections.unmodifiableMap(new LinkedHashMap<>(discoveredOutcomes));
            milestones = Collections.unmodifiableMap(new LinkedHashMap<>(milestones));
            completedQuests = Set.copyOf(completedQuests);
        }

        public PlayerRecord(long revision, Set<String> participation, Map<String, String> discoveredOutcomes) {
            this(revision, participation, discoveredOutcomes, Map.of(), Set.of());
        }

        public static PlayerRecord empty() {
            return new PlayerRecord(0L, Set.of(), Map.of());
        }
    }

    public record MilestoneProgress(Route route, String outcome, UUID worldOperation, long enteredAt,
            UUID completionOperation, long completedAt) {
        public boolean completed() {
            return this.completionOperation != null;
        }
    }

    public enum Route { ORIGINAL, CATCH_UP }

    public enum Origin { PLAYER_DECISION, WORLD_OBSERVATION, OPERATOR_FIXTURE }
}
