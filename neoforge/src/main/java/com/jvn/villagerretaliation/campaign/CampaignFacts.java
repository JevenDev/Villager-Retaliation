package com.jvn.villagerretaliation.campaign;

import com.jvn.villagerretaliation.campaign.content.CampaignDefinition;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public record CampaignFacts(Map<String, Value> world, Map<UUID, Map<String, Value>> players, Map<UUID, Receipt> receipts) {
    public CampaignFacts {
        world = immutable(world);
        var personal = new LinkedHashMap<UUID, Map<String, Value>>();
        players.forEach((player, values) -> personal.put(player, immutable(values)));
        players = immutable(personal);
        receipts = immutable(receipts);
    }

    public static CampaignFacts empty() { return new CampaignFacts(Map.of(), Map.of(), Map.of()); }

    public Optional<Value> value(CampaignDefinition.Fact fact, UUID player) {
        return Optional.ofNullable(fact.scope() == CampaignDefinition.FactScope.WORLD
                ? world.get(fact.id()) : players.getOrDefault(player, Map.of()).get(fact.id()));
    }

    CampaignFacts apply(Receipt receipt) {
        var shared = new LinkedHashMap<>(world);
        var personal = new LinkedHashMap<>(players);
        if (receipt.scope() == CampaignDefinition.FactScope.WORLD) shared.put(receipt.fact(), receipt.result());
        else {
            var values = new LinkedHashMap<>(personal.getOrDefault(receipt.player(), Map.of()));
            values.put(receipt.fact(), receipt.result());
            personal.put(receipt.player(), values);
        }
        var operations = new LinkedHashMap<>(receipts);
        operations.put(receipt.operation(), receipt);
        return new CampaignFacts(shared, personal, operations);
    }

    private static <K, V> Map<K, V> immutable(Map<K, V> values) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    public record Value(CampaignDefinition.FactKind kind, String text) {
        public Value {
            if (kind == null || text == null || text.length() > 4096) throw new IllegalArgumentException("Invalid campaign fact value.");
            if (kind == CampaignDefinition.FactKind.TAG && !text.equals("true") && !text.equals("false")) {
                throw new IllegalArgumentException("Campaign tags require true or false.");
            }
            if (kind == CampaignDefinition.FactKind.COUNTER) {
                try {
                    if (!Long.toString(Long.parseLong(text)).equals(text)) throw new NumberFormatException();
                } catch (NumberFormatException exception) {
                    throw new IllegalArgumentException("Campaign counters require a signed 64-bit integer.", exception);
                }
            }
        }
    }

    public enum Mutation { SET, ADD }

    public record Receipt(UUID operation, UUID player, String fact, CampaignDefinition.FactScope scope,
            Mutation mutation, Value operand, Value result, long revision, long committedAt) {
        public Receipt {
            if (operation == null || player == null || fact == null || !fact.matches(CampaignOutcomeAction.ID_PATTERN)
                    || scope == null || mutation == null || operand == null || result == null || revision <= 0
                    || operand.kind() != result.kind() || mutation == Mutation.SET && !operand.equals(result)
                    || mutation == Mutation.ADD && operand.kind() != CampaignDefinition.FactKind.COUNTER) {
                throw new IllegalArgumentException("Invalid campaign fact receipt.");
            }
        }
    }
}
