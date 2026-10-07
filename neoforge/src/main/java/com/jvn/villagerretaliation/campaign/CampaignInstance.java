package com.jvn.villagerretaliation.campaign;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

public record CampaignInstance(ResourceLocation campaignId, UUID instanceId, int definitionRevision, String fingerprint,
        String title, long createdAt, long worldRevision, Map<String, OutcomeCommit> outcomes,
        Map<UUID, PlayerRecord> players) {
    public CampaignInstance {
        outcomes = Collections.unmodifiableMap(new LinkedHashMap<>(outcomes));
        players = Collections.unmodifiableMap(new LinkedHashMap<>(players));
    }

    public record OutcomeCommit(UUID operationId, String milestone, String outcome, String title,
            long revision, long committedAt, Origin origin) {
    }

    public record PlayerRecord(long revision, Set<String> participation, Map<String, String> discoveredOutcomes) {
        public PlayerRecord {
            participation = Set.copyOf(participation);
            discoveredOutcomes = Collections.unmodifiableMap(new LinkedHashMap<>(discoveredOutcomes));
        }

        public static PlayerRecord empty() {
            return new PlayerRecord(0L, Set.of(), Map.of());
        }
    }

    public enum Origin { PLAYER_DECISION, WORLD_OBSERVATION }
}
