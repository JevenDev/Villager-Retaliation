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
            var milestones = new ListTag();
            progress.milestones().forEach((milestoneId, value) -> {
                var milestone = new CompoundTag();
                milestone.putString("Milestone", milestoneId);
                milestone.putString("Route", value.route().name());
                milestone.putString("Outcome", value.outcome());
                milestone.putLong("EnteredAt", value.enteredAt());
                milestone.putLong("CompletedAt", value.completedAt());
                if (value.worldOperation() != null) milestone.putUUID("WorldOperation", value.worldOperation());
                if (value.completionOperation() != null) milestone.putUUID("CompletionOperation", value.completionOperation());
                milestones.add(milestone);
            });
            player.put("Milestones", milestones);
            var quests = new ListTag();
            progress.completedQuests().stream().sorted().forEach(questId -> quests.add(StringTag.valueOf(questId.toString())));
            player.put("CompletedQuests", quests);
            players.add(player);
        });
        tag.put("Players", players);
        tag.put("Facts", CampaignFactCodec.save(instance.facts()));
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
            Map<String, CampaignInstance.MilestoneProgress> milestones = new LinkedHashMap<>();
            Set<UUID> completions = new HashSet<>();
            for (Tag milestoneTag : list(entry, "Milestones", Tag.TAG_COMPOUND)) {
                var milestone = (CompoundTag) milestoneTag;
                String id = text(milestone, "Milestone");
                var route = CampaignInstance.Route.valueOf(text(milestone, "Route"));
                require(milestone.contains("Outcome", Tag.TAG_STRING), "missing personal outcome");
                String outcome = milestone.getString("Outcome");
                UUID worldOperation = optionalId(milestone, "WorldOperation");
                UUID completion = optionalId(milestone, "CompletionOperation");
                long completedAt = number(milestone, "CompletedAt");
                require(completion == null ? completedAt == -1L : !outcome.isBlank() && completions.add(completion),
                        "invalid personal completion receipt");
                if (worldOperation != null) {
                    var committed = outcomes.get(id);
                    require(committed != null && committed.operationId().equals(worldOperation) && committed.outcome().equals(outcome),
                            "personal progress differs from the committed world outcome");
                    require(discoveries.containsKey(id), "resolved personal progress must know the outcome");
                } else require(route != CampaignInstance.Route.CATCH_UP, "catch-up requires a world outcome");
                require(milestones.putIfAbsent(id, new CampaignInstance.MilestoneProgress(route, outcome, worldOperation,
                        number(milestone, "EnteredAt"), completion, completedAt)) == null, "duplicate personal milestone");
            }
            Set<ResourceLocation> completedQuests = new HashSet<>();
            for (Tag questTag : list(entry, "CompletedQuests", Tag.TAG_STRING)) {
                var quest = ResourceLocation.tryParse(questTag.getAsString());
                require(quest != null && completedQuests.add(quest), "invalid or duplicate completed quest");
            }
            require(players.putIfAbsent(entry.getUUID("Player"), new CampaignInstance.PlayerRecord(personalRevision,
                    participation, discoveries, milestones, completedQuests)) == null, "duplicate player record");
        }
        players.values().forEach(player -> player.milestones().values().forEach(progress -> {
            if (progress.completionOperation() != null) operations.add(progress.completionOperation());
        }));
        require(tag.contains("Facts", Tag.TAG_COMPOUND), "missing story facts");
        var facts = CampaignFactCodec.load(tag.getCompound("Facts"), worldRevision, revisions, operations, players);
        require(worldRevision == revisions.size(), "world revision does not match committed outcomes and facts");
        return new CampaignInstance(campaign, tag.getUUID("Instance"), tag.getInt("DefinitionRevision"),
                text(tag, "Fingerprint"), text(tag, "Title"), number(tag, "CreatedAt"), worldRevision, outcomes, players, facts);
    }

    static ListTag list(CompoundTag tag, String key, int elementType) {
        require(tag.get(key) instanceof ListTag, "missing list " + key);
        var list = (ListTag) tag.get(key);
        require(list.isEmpty() || list.getElementType() == elementType, "invalid list " + key);
        return list;
    }

    private static UUID optionalId(CompoundTag tag, String key) {
        require(!tag.contains(key) || tag.hasUUID(key), "invalid identity " + key);
        return tag.hasUUID(key) ? tag.getUUID(key) : null;
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
