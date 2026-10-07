package com.jvn.villagerretaliation.campaign;

import com.jvn.villagerretaliation.campaign.content.CampaignDefinition;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

final class CampaignFactCodec {
    private CampaignFactCodec() {
    }

    static CompoundTag save(CampaignFacts facts) {
        var tag = new CompoundTag();
        tag.put("World", saveValues(facts.world()));
        var players = new ListTag();
        facts.players().forEach((id, values) -> {
            var player = new CompoundTag();
            player.putUUID("Player", id);
            player.put("Values", saveValues(values));
            players.add(player);
        });
        tag.put("Players", players);
        var receipts = new ListTag();
        facts.receipts().values().forEach(value -> {
            var receipt = new CompoundTag();
            receipt.putUUID("Operation", value.operation());
            receipt.putUUID("Player", value.player());
            receipt.putString("Fact", value.fact());
            receipt.putString("Scope", value.scope().name());
            receipt.putString("Mutation", value.mutation().name());
            receipt.put("Operand", saveValue(value.operand()));
            receipt.put("Result", saveValue(value.result()));
            receipt.putLong("Revision", value.revision());
            receipt.putLong("CommittedAt", value.committedAt());
            receipts.add(receipt);
        });
        tag.put("Receipts", receipts);
        return tag;
    }

    static CampaignFacts load(CompoundTag tag, long worldRevision, Set<Long> worldRevisions, Set<UUID> operations,
            Map<UUID, CampaignInstance.PlayerRecord> playerRecords) {
        var world = loadValues(compound(tag, "World"));
        var players = new LinkedHashMap<UUID, Map<String, CampaignFacts.Value>>();
        for (Tag raw : CampaignStateCodec.list(tag, "Players", Tag.TAG_COMPOUND)) {
            var player = (CompoundTag) raw;
            CampaignStateCodec.require(player.hasUUID("Player"), "missing story fact player");
            UUID id = player.getUUID("Player");
            CampaignStateCodec.require(playerRecords.containsKey(id), "story facts require a personal record");
            CampaignStateCodec.require(players.putIfAbsent(id, loadValues(compound(player, "Values"))) == null,
                    "duplicate story fact player");
        }
        var receipts = new LinkedHashMap<UUID, CampaignFacts.Receipt>();
        var personalRevisions = new HashMap<UUID, Set<Long>>();
        for (Tag raw : CampaignStateCodec.list(tag, "Receipts", Tag.TAG_COMPOUND)) {
            var receipt = (CompoundTag) raw;
            CampaignStateCodec.require(receipt.hasUUID("Operation") && receipt.hasUUID("Player"), "missing story fact receipt identity");
            var value = new CampaignFacts.Receipt(receipt.getUUID("Operation"), receipt.getUUID("Player"), text(receipt, "Fact"),
                    CampaignDefinition.FactScope.valueOf(text(receipt, "Scope")), CampaignFacts.Mutation.valueOf(text(receipt, "Mutation")),
                    loadValue(compound(receipt, "Operand")), loadValue(compound(receipt, "Result")),
                    number(receipt, "Revision"), number(receipt, "CommittedAt"));
            CampaignStateCodec.require(operations.add(value.operation()), "duplicate campaign operation identity");
            if (value.scope() == CampaignDefinition.FactScope.WORLD) {
                CampaignStateCodec.require(value.revision() <= worldRevision && worldRevisions.add(value.revision()), "invalid world fact revision");
            } else {
                var player = playerRecords.get(value.player());
                CampaignStateCodec.require(player != null && value.revision() <= player.revision()
                        && personalRevisions.computeIfAbsent(value.player(), ignored -> new HashSet<>()).add(value.revision()),
                        "invalid personal fact revision");
            }
            CampaignStateCodec.require(receipts.putIfAbsent(value.operation(), value) == null, "duplicate story fact receipt");
        }
        var facts = new CampaignFacts(world, players, receipts);
        var replayedWorld = new LinkedHashMap<String, CampaignFacts.Value>();
        var replayedPlayers = new LinkedHashMap<UUID, Map<String, CampaignFacts.Value>>();
        for (var receipt : receipts.values().stream().sorted(Comparator.comparingLong(CampaignFacts.Receipt::revision)).toList()) {
            var target = receipt.scope() == CampaignDefinition.FactScope.WORLD ? replayedWorld
                    : replayedPlayers.computeIfAbsent(receipt.player(), ignored -> new LinkedHashMap<>());
            var current = target.get(receipt.fact());
            CampaignStateCodec.require(current == null || current.kind() == receipt.operand().kind(), "story fact kind changed within its history");
            if (receipt.mutation() == CampaignFacts.Mutation.ADD) {
                try {
                    long expected = Math.addExact(current == null ? 0L : Long.parseLong(current.text()), Long.parseLong(receipt.operand().text()));
                    CampaignStateCodec.require(Long.toString(expected).equals(receipt.result().text()), "story fact counter differs from its receipt");
                } catch (ArithmeticException exception) {
                    throw new IllegalArgumentException("Story fact counter receipt overflows.", exception);
                }
            }
            target.put(receipt.fact(), receipt.result());
        }
        CampaignStateCodec.require(replayedWorld.equals(world) && replayedPlayers.equals(players), "story facts differ from their receipts");
        return facts;
    }

    private static CompoundTag saveValues(Map<String, CampaignFacts.Value> values) {
        var tag = new CompoundTag();
        values.forEach((id, value) -> tag.put(id, saveValue(value)));
        return tag;
    }

    private static Map<String, CampaignFacts.Value> loadValues(CompoundTag tag) {
        var values = new LinkedHashMap<String, CampaignFacts.Value>();
        for (String id : tag.getAllKeys().stream().sorted().toList()) {
            CampaignStateCodec.require(id.matches(CampaignOutcomeAction.ID_PATTERN), "invalid story fact identity");
            values.put(id, loadValue(compound(tag, id)));
        }
        return values;
    }

    private static CompoundTag saveValue(CampaignFacts.Value value) {
        var tag = new CompoundTag();
        tag.putString("Kind", value.kind().name());
        tag.putString("Value", value.text());
        return tag;
    }

    private static CampaignFacts.Value loadValue(CompoundTag tag) {
        return new CampaignFacts.Value(CampaignDefinition.FactKind.valueOf(text(tag, "Kind")), text(tag, "Value"));
    }

    private static CompoundTag compound(CompoundTag tag, String key) {
        CampaignStateCodec.require(tag.contains(key, Tag.TAG_COMPOUND), "missing story fact compound " + key);
        return tag.getCompound(key);
    }

    private static String text(CompoundTag tag, String key) {
        CampaignStateCodec.require(tag.contains(key, Tag.TAG_STRING), "invalid story fact text " + key);
        return tag.getString(key);
    }

    private static long number(CompoundTag tag, String key) {
        CampaignStateCodec.require(tag.contains(key, Tag.TAG_LONG), "invalid story fact number " + key);
        return tag.getLong(key);
    }
}
