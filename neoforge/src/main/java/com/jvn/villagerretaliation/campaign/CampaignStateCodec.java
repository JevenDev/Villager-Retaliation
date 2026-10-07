package com.jvn.villagerretaliation.campaign;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

final class CampaignStateCodec {
    private CampaignStateCodec() {
    }

    static CompoundTag save(CampaignInstance instance) {
        var tag = new CompoundTag();
        tag.putString("Campaign", instance.campaignId().toString());
        tag.putUUID("Instance", instance.instanceId());
        tag.putInt("DefinitionRevision", instance.definitionRevision());
        tag.putString("Fingerprint", instance.fingerprint());
        tag.putString("Title", instance.title());
        tag.putLong("CreatedAt", instance.createdAt());
        tag.putLong("WorldRevision", instance.worldRevision());
        var outcomes = new ListTag();
        for (var outcome : instance.outcomes().values()) {
            var entry = new CompoundTag();
            entry.putUUID("Operation", outcome.operationId());
            entry.putString("Milestone", outcome.milestone());
            entry.putString("Outcome", outcome.outcome());
            entry.putString("Title", outcome.title());
            entry.putLong("Revision", outcome.revision());
            entry.putLong("CommittedAt", outcome.committedAt());
            entry.putString("Origin", outcome.origin().name());
            outcomes.add(entry);
        }
        tag.put("Outcomes", outcomes);
        var players = new ListTag();
        instance.players().forEach((id, progress) -> {
            var player = new CompoundTag();
            player.putUUID("Player", id);
            player.putLong("Revision", progress.revision());
            var participation = new ListTag();
            progress.participation().stream().sorted().forEach(value -> participation.add(StringTag.valueOf(value)));
            player.put("Participation", participation);
            var discoveries = new CompoundTag();
            progress.discoveredOutcomes().forEach(discoveries::putString);
            player.put("DiscoveredOutcomes", discoveries);
            players.add(player);
        });
        tag.put("Players", players);
        return tag;
    }

    static CampaignInstance load(CompoundTag tag) {
        ResourceLocation campaign = ResourceLocation.tryParse(text(tag, "Campaign"));
        require(campaign != null, "invalid campaign id");
        require(tag.hasUUID("Instance"), "missing instance id");
        require(tag.contains("DefinitionRevision", Tag.TAG_INT) && tag.getInt("DefinitionRevision") > 0,
                "invalid definition revision");
        long worldRevision = number(tag, "WorldRevision");
        require(worldRevision >= 0L, "invalid world revision");
        Map<String, CampaignInstance.OutcomeCommit> outcomes = new LinkedHashMap<>();
        Set<UUID> operations = new HashSet<>();
        Set<Long> revisions = new HashSet<>();
        for (Tag raw : list(tag, "Outcomes", Tag.TAG_COMPOUND)) {
            var entry = (CompoundTag) raw;
            require(entry.hasUUID("Operation"), "missing outcome operation id");
            String milestone = text(entry, "Milestone");
            long revision = number(entry, "Revision");
            require(revision > 0 && revision <= worldRevision && revisions.add(revision), "invalid outcome revision");
            UUID operation = entry.getUUID("Operation");
            require(operations.add(operation), "duplicate outcome operation id");
            var outcome = new CampaignInstance.OutcomeCommit(operation, milestone, text(entry, "Outcome"), text(entry, "Title"),
                    revision, number(entry, "CommittedAt"), CampaignInstance.Origin.valueOf(text(entry, "Origin")));
            require(outcomes.putIfAbsent(milestone, outcome) == null, "duplicate milestone outcome");
        }
        require(worldRevision == outcomes.size(), "world revision does not match committed outcomes");
        Map<UUID, CampaignInstance.PlayerRecord> players = new LinkedHashMap<>();
        for (Tag raw : list(tag, "Players", Tag.TAG_COMPOUND)) {
            var entry = (CompoundTag) raw;
            require(entry.hasUUID("Player"), "missing player id");
            long personalRevision = number(entry, "Revision");
            require(personalRevision > 0L, "invalid personal revision");
            Set<String> participation = new HashSet<>();
            for (Tag milestoneTag : list(entry, "Participation", Tag.TAG_STRING)) {
                String milestone = milestoneTag.getAsString();
                var outcome = outcomes.get(milestone);
                require(outcome != null && outcome.origin() == CampaignInstance.Origin.PLAYER_DECISION,
                        "participation requires a player decision");
                require(participation.add(milestone), "duplicate participation");
            }
            require(entry.contains("DiscoveredOutcomes", Tag.TAG_COMPOUND), "missing discoveries");
            var discovered = entry.getCompound("DiscoveredOutcomes");
            Map<String, String> discoveries = new LinkedHashMap<>();
            for (String milestone : discovered.getAllKeys().stream().sorted().toList()) {
                String outcome = text(discovered, milestone);
                require(outcomes.containsKey(milestone) && outcomes.get(milestone).outcome().equals(outcome),
                        "discovery differs from the committed outcome");
                discoveries.put(milestone, outcome);
            }
            require(discoveries.keySet().containsAll(participation), "participants must know the outcome");
            require(players.putIfAbsent(entry.getUUID("Player"), new CampaignInstance.PlayerRecord(personalRevision, participation, discoveries)) == null,
                    "duplicate player record");
        }
        return new CampaignInstance(campaign, tag.getUUID("Instance"), tag.getInt("DefinitionRevision"),
                text(tag, "Fingerprint"), text(tag, "Title"), number(tag, "CreatedAt"), worldRevision, outcomes, players);
    }

    static ListTag list(CompoundTag tag, String key, int elementType) {
        require(tag.get(key) instanceof ListTag, "missing list " + key);
        var list = (ListTag) tag.get(key);
        require(list.isEmpty() || list.getElementType() == elementType, "invalid list " + key);
        return list;
    }

    private static String text(CompoundTag tag, String key) {
        require(tag.contains(key, Tag.TAG_STRING) && !tag.getString(key).isBlank(), "invalid text " + key);
        return tag.getString(key);
    }

    private static long number(CompoundTag tag, String key) {
        require(tag.contains(key, Tag.TAG_LONG), "invalid number " + key);
        return tag.getLong(key);
    }

    static void require(boolean valid, String message) {
        if (!valid) throw new IllegalArgumentException(message);
    }
}
