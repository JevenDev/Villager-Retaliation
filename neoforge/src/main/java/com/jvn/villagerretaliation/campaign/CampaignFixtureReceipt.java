package com.jvn.villagerretaliation.campaign;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

public record CampaignFixtureReceipt(UUID operation, UUID expected, String entry, String chapter, UUID player, UUID operator,
        String reason, long time, List<Effect> effects) {
    public CampaignFixtureReceipt {
        CampaignStateCodec.require(operation != null && expected != null && CampaignFactFields.validId(entry) && CampaignFactFields.validId(chapter)
                && player != null && operator != null && reason != null && !reason.isBlank() && reason.length() <= 256 && time >= 0,
                "invalid campaign fixture audit");
        effects = List.copyOf(effects);
        var ids = new HashSet<String>();
        for (var effect : effects) {
            CampaignStateCodec.require(ids.add(effect.id()), "duplicate fixture effect audit");
            if (effect.applied() || effect.kind() == Kind.QUEST)
                CampaignStateCodec.require(effect.operation().equals(effectOperation(operation, effect.id())), "fixture effect operation differs from its audit");
        }
    }

    public enum Kind { WORLD_OUTCOME, MILESTONE, QUEST, FACT }

    public record Effect(String id, Kind kind, String target, String value, UUID operation, boolean applied) {
        public Effect {
            CampaignStateCodec.require(CampaignFactFields.validId(id) && kind != null && operation != null && value != null && value.length() <= 4096,
                    "invalid fixture effect audit");
            CampaignStateCodec.require(kind == Kind.QUEST ? ResourceLocation.tryParse(target == null ? "" : target) != null && value.isEmpty()
                    : CampaignFactFields.validId(target), "invalid fixture effect target");
            if (kind == Kind.WORLD_OUTCOME || kind == Kind.MILESTONE)
                CampaignStateCodec.require(CampaignFactFields.validId(value), "invalid fixture outcome audit");
        }
    }

    static UUID effectOperation(UUID operation, String effect) {
        return UUID.nameUUIDFromBytes((operation + "/fixture/" + effect).getBytes(StandardCharsets.UTF_8));
    }

    boolean matches(CampaignFixtureService.Request request) {
        return expected.equals(request.expected()) && entry.equals(request.entry()) && player.equals(request.player())
                && operator.equals(request.operator()) && reason.equals(request.reason());
    }

    String definitionIssue(com.jvn.villagerretaliation.campaign.content.CampaignDefinition definition) {
        var authored = definition.fixtureEntries().get(entry);
        if (authored == null || !chapter.equals(authored.chapter()) || effects.size() != authored.effects().size())
            return "Saved fixture entry " + entry + " does not match its campaign definition. Restore matching content and fixture history.";
        for (int index = 0; index < effects.size(); index++) {
            var saved = effects.get(index);
            var effect = authored.effects().get(index);
            boolean matches = saved.id().equals(effect.id()) && switch (effect) {
                case com.jvn.villagerretaliation.campaign.content.CampaignFixtureEntry.WorldOutcome outcome ->
                    saved.kind() == Kind.WORLD_OUTCOME && saved.target().equals(outcome.milestone()) && saved.value().equals(outcome.outcome());
                case com.jvn.villagerretaliation.campaign.content.CampaignFixtureEntry.CompleteMilestone completion ->
                    saved.kind() == Kind.MILESTONE && saved.target().equals(completion.milestone()) && saved.value().equals(completion.outcome());
                case com.jvn.villagerretaliation.campaign.content.CampaignFixtureEntry.CompleteQuest completion ->
                    saved.kind() == Kind.QUEST && saved.target().equals(completion.quest().toString());
                case com.jvn.villagerretaliation.campaign.content.CampaignFixtureEntry.SetFact fact ->
                    saved.kind() == Kind.FACT && saved.target().equals(fact.fact()) && saved.value().equals(fact.value());
            };
            if (!matches) return "Saved fixture effect " + effect.id() + " does not match its campaign definition. Restore matching content and fixture history.";
        }
        return "";
    }

    static boolean reserves(CampaignInstance instance, UUID operation) {
        return instance.fixtureEntries().containsKey(operation) || instance.fixtureEntries().values().stream()
                .flatMap(receipt -> receipt.effects().stream()).anyMatch(effect -> effect.kind() == Kind.QUEST && effect.operation().equals(operation));
    }

    static void validate(CampaignInstance instance, Set<UUID> operations) {
        var applied = new HashSet<UUID>();
        var world = new HashSet<UUID>();
        for (var receipt : instance.fixtureEntries().values()) {
            CampaignStateCodec.require(operations.add(receipt.operation()) && receipt.time() >= instance.createdAt(), "fixture audit operation or time is invalid");
            var player = instance.players().get(receipt.player());
            CampaignStateCodec.require(player != null, "fixture audit player is missing");
            for (var effect : receipt.effects()) {
                CampaignStateCodec.require(!effect.applied() || applied.add(effect.operation()), "fixture effect belongs to multiple setup audits");
                switch (effect.kind()) {
                    case WORLD_OUTCOME -> {
                        var outcome = instance.outcomes().get(effect.target());
                        CampaignStateCodec.require(outcome != null && outcome.operationId().equals(effect.operation()) && outcome.outcome().equals(effect.value()),
                                "fixture audit differs from its world outcome");
                        if (effect.applied()) {
                            CampaignStateCodec.require(outcome.origin() == CampaignInstance.Origin.OPERATOR_FIXTURE && outcome.committedAt() == receipt.time(),
                                    "fixture outcome has no operator origin");
                            world.add(outcome.operationId());
                        }
                    }
                    case MILESTONE -> {
                        var milestone = player.milestones().get(effect.target());
                        CampaignStateCodec.require(milestone != null && milestone.completed() && milestone.completionOperation().equals(effect.operation())
                                && milestone.outcome().equals(effect.value()), "fixture audit differs from its personal milestone");
                        if (effect.applied()) CampaignStateCodec.require(milestone.completedAt() == receipt.time(), "fixture milestone time differs from its audit");
                    }
                    case QUEST -> CampaignStateCodec.require(operations.add(effect.operation())
                            && player.completedQuests().contains(ResourceLocation.parse(effect.target())), "fixture quest completion audit is invalid");
                    case FACT -> {
                        var fact = instance.facts().receipts().get(effect.operation());
                        CampaignStateCodec.require(effect.applied() && fact != null && fact.player().equals(receipt.player()) && fact.fact().equals(effect.target())
                                && fact.mutation() == CampaignFacts.Mutation.SET && fact.result().text().equals(effect.value())
                                && fact.committedAt() == receipt.time(), "fixture audit differs from its story fact receipt");
                    }
                }
            }
        }
        for (var outcome : instance.outcomes().values()) if (outcome.origin() == CampaignInstance.Origin.OPERATOR_FIXTURE)
            CampaignStateCodec.require(world.contains(outcome.operationId()), "operator fixture outcome has no audit");
    }

    CompoundTag save() {
        var tag = new CompoundTag();
        tag.putUUID("Operation", operation);
        tag.putUUID("Expected", expected);
        tag.putString("Entry", entry);
        tag.putString("Chapter", chapter);
        tag.putUUID("Player", player);
        tag.putUUID("Operator", operator);
        tag.putString("Reason", reason);
        tag.putLong("Time", time);
        var rows = new ListTag();
        for (var effect : effects) {
            var row = new CompoundTag();
            row.putString("Id", effect.id());
            row.putString("Kind", effect.kind().name());
            row.putString("Target", effect.target());
            row.putString("Value", effect.value());
            row.putUUID("Operation", effect.operation());
            row.putBoolean("Applied", effect.applied());
            rows.add(row);
        }
        tag.put("Effects", rows);
        return tag;
    }

    static CampaignFixtureReceipt load(CompoundTag tag) {
        CampaignStateCodec.require(tag.hasUUID("Operation") && tag.hasUUID("Expected") && tag.hasUUID("Player") && tag.hasUUID("Operator")
                && tag.contains("Time", Tag.TAG_LONG), "invalid fixture audit fields");
        var effects = new java.util.ArrayList<Effect>();
        for (var raw : CampaignStateCodec.list(tag, "Effects", Tag.TAG_COMPOUND)) {
            var row = (CompoundTag) raw;
            CampaignStateCodec.require(row.hasUUID("Operation") && row.contains("Applied", Tag.TAG_BYTE)
                    && (row.getByte("Applied") == 0 || row.getByte("Applied") == 1), "invalid fixture effect audit fields");
            effects.add(new Effect(text(row, "Id"), Kind.valueOf(text(row, "Kind")), text(row, "Target"), text(row, "Value"),
                    row.getUUID("Operation"), row.getBoolean("Applied")));
        }
        return new CampaignFixtureReceipt(tag.getUUID("Operation"), tag.getUUID("Expected"), text(tag, "Entry"), text(tag, "Chapter"),
                tag.getUUID("Player"), tag.getUUID("Operator"), text(tag, "Reason"), tag.getLong("Time"), effects);
    }

    private static String text(CompoundTag tag, String key) {
        CampaignStateCodec.require(tag.contains(key, Tag.TAG_STRING), "invalid fixture audit text " + key);
        return tag.getString(key);
    }
}
