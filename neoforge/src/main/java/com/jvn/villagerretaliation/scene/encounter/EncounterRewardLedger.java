package com.jvn.villagerretaliation.scene.encounter;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

final class EncounterRewardLedger {
    private final Map<String, Batch> batches = new LinkedHashMap<>();
    private Tag preserved;
    private CompoundTag definition;
    private String diagnostic = "";

    String diagnostic() { return diagnostic; }
    Map<String, Batch> batches() { return Collections.unmodifiableMap(batches); }
    boolean hasHistory() { return !batches.isEmpty() || !diagnostic.isBlank(); }

    boolean bind(EncounterTemplate template) {
        var candidate = new CompoundTag();
        candidate.putInt("Guts", EncounterService.completionGutsReward(template));
        var rewards = template.rewards();
        candidate.put("Waves", definitions("wave", rewards == null ? List.of() : rewards.waves()));
        candidate.put("Phases", definitions("phase", rewards == null ? List.of() : rewards.phases()));
        candidate.put("Completion", definitions("completion", rewards == null ? List.of() : rewards.completion()));
        if (definition != null) {
            require(definition.equals(candidate), "Encounter rewards have changed. Restore their original content before continuing.");
            return false;
        }
        definition = candidate;
        return true;
    }

    private static ListTag definitions(String kind, List<EncounterTemplate.Reward> rewards) {
        var list = new ListTag();
        rewards.forEach(reward -> list.add(new Batch(kind, reward, 0, Map.of()).save().getCompound("Reward")));
        return list;
    }

    void record(Batch batch) {
        require(diagnostic.isBlank(), diagnostic);
        require(batches.containsKey(batch.key()) || batches.size() < 4096, "encounter reward capacity exceeded");
        batches.put(batch.key(), batch);
    }

    Tag save() {
        if (preserved != null) return preserved.copy();
        if (!diagnostic.isBlank()) return null;
        var tag = new CompoundTag();
        tag.putInt("Version", 1);
        tag.put("Definition", definition == null ? new CompoundTag() : definition.copy());
        var list = new ListTag();
        batches.values().forEach(batch -> list.add(batch.save()));
        tag.put("Batches", list);
        return tag;
    }

    static EncounterRewardLedger load(Tag input, UUID scene, UUID encounter) {
        var ledger = new EncounterRewardLedger();
        if (input == null) {
            ledger.diagnostic = "Encounter reward history is missing. Restore compatible scene data before continuing.";
            return ledger;
        }
        try {
            require(input instanceof CompoundTag, "invalid encounter reward history");
            var tag = (CompoundTag) input;
            require(tag.getAllKeys().equals(Set.of("Version", "Definition", "Batches")) && tag.contains("Version", Tag.TAG_INT)
                    && tag.getInt("Version") == 1 && tag.contains("Definition", Tag.TAG_COMPOUND), "unsupported encounter reward history");
            ledger.definition = tag.getCompound("Definition").isEmpty() ? null : tag.getCompound("Definition").copy();
            var tokens = new java.util.HashSet<UUID>();
            var operations = new java.util.HashSet<String>();
            for (var raw : list(tag, "Batches", 4096)) {
                var batch = Batch.load((CompoundTag) raw);
                require(!ledger.batches.containsKey(batch.key()), "duplicate reward batch");
                for (var grant : batch.grants().values()) {
                    require(grant.operation().startsWith(scene + "/encounter/" + encounter + "/")
                            && tokens.add(grant.token()) && operations.add(grant.operation()), "conflicting reward ownership");
                    require(grant.operation().equals(batch.operation(scene, encounter, grant.key())), "invalid reward operation");
                }
                ledger.record(batch);
            }
            require(ledger.batches.isEmpty() || ledger.definition != null, "reward batches have no saved definition");
        } catch (IllegalArgumentException exception) {
            ledger.batches.clear();
            ledger.preserved = input.copy();
            ledger.diagnostic = "Encounter reward history is unreadable. Restore compatible scene data before continuing.";
        }
        return ledger;
    }

    record Batch(String trigger, EncounterTemplate.Reward reward, int guts, Map<String, Grant> grants) {
        Batch {
            require(trigger != null && !trigger.isBlank() && trigger.length() <= 256, "invalid reward trigger");
            require(reward == null ? trigger.equals("profile/guts") && (guts == 3 || guts == 5) : guts == 0, "invalid reward kind");
            require(grants != null && grants.size() <= 1024, "invalid reward recipients");
            grants = Collections.unmodifiableMap(new LinkedHashMap<>(grants));
            for (var entry : grants.entrySet()) {
                var grant = entry.getValue();
                require(entry.getKey().equals(grant.key()), "invalid reward recipient key");
                require(reward == null ? grant.key().matches("[a-z][a-z0-9_.-]{0,63}") && grant.items().isEmpty() && grant.state() != State.EARNED
                        : grant.key().equals("player/" + grant.recipient()), "invalid reward recipient");
            }
        }

        String key() { return reward == null ? trigger : trigger + "/" + reward.id(); }
        String operation(UUID scene, UUID encounter, String recipientKey) {
            return scene + "/encounter/" + encounter + "/" + (reward == null ? key() : "reward/" + key()) + "/" + recipientKey;
        }
        Batch replace(Grant grant) {
            var updated = new LinkedHashMap<>(grants);
            require(updated.containsKey(grant.key()), "unknown reward recipient");
            updated.put(grant.key(), grant);
            return new Batch(trigger, reward, guts, updated);
        }
        CompoundTag save() {
            var tag = new CompoundTag();
            tag.putString("Trigger", trigger);
            tag.putInt("Guts", guts);
            if (reward != null) {
                var authored = new CompoundTag();
                authored.putString("Id", reward.id());
                authored.putString("Target", reward.target());
                authored.putString("LootTable", reward.lootTable() == null ? "" : reward.lootTable().toString());
                authored.putString("Item", reward.item() == null ? "" : reward.item().toString());
                authored.putInt("Count", reward.count());
                authored.putString("TrophyName", reward.trophyName());
                authored.putString("TrophyNameKey", reward.trophyNameKey());
                tag.put("Reward", authored);
            }
            var recipients = new ListTag();
            grants.values().forEach(grant -> recipients.add(grant.save()));
            tag.put("Grants", recipients);
            return tag;
        }
        static Batch load(CompoundTag tag) {
            require(tag.contains("Trigger", Tag.TAG_STRING) && tag.contains("Guts", Tag.TAG_INT), "invalid reward batch");
            EncounterTemplate.Reward reward = null;
            if (tag.contains("Reward")) {
                require(tag.contains("Reward", Tag.TAG_COMPOUND), "invalid authored reward");
                var authored = tag.getCompound("Reward");
                for (var field : List.of("Id", "Target", "LootTable", "Item", "TrophyName", "TrophyNameKey"))
                    require(authored.contains(field, Tag.TAG_STRING), "missing reward field " + field);
                require(authored.contains("Count", Tag.TAG_INT), "missing reward count");
                reward = new EncounterTemplate.Reward(authored.getString("Id"), authored.getString("Target"),
                        id(authored.getString("LootTable")), id(authored.getString("Item")), authored.getInt("Count"),
                        authored.getString("TrophyName"), authored.getString("TrophyNameKey"));
            }
            var grants = new LinkedHashMap<String, Grant>();
            for (var raw : list(tag, "Grants", 1024)) {
                var grant = Grant.load((CompoundTag) raw);
                require(grants.putIfAbsent(grant.key(), grant) == null, "duplicate reward recipient");
            }
            return new Batch(tag.getString("Trigger"), reward, tag.getInt("Guts"), grants);
        }
    }

    record Grant(String key, String operation, UUID recipient, UUID token, State state, List<CompoundTag> items) {
        Grant {
            require(key != null && !key.isBlank() && key.length() <= 128 && operation != null && !operation.isBlank()
                    && operation.length() <= 1024 && recipient != null && token != null && state != null
                    && items != null && items.size() <= 256 && (state != State.EARNED || items.isEmpty()), "invalid reward grant");
            items = items.stream().map(CompoundTag::copy).toList();
        }
        @Override public List<CompoundTag> items() { return items.stream().map(CompoundTag::copy).toList(); }
        UUID operationId() { return UUID.nameUUIDFromBytes(("encounter-reward/" + operation).getBytes(StandardCharsets.UTF_8)); }
        Grant ready(List<CompoundTag> items) { return new Grant(key, operation, recipient, token, State.READY, items); }
        Grant finish() { return new Grant(key, operation, recipient, token, State.DELIVERED, items); }
        CompoundTag save() {
            var tag = new CompoundTag();
            tag.putString("Key", key);
            tag.putString("Operation", operation);
            tag.putUUID("Recipient", recipient);
            tag.putUUID("Token", token);
            tag.putString("State", state.name());
            var stacks = new ListTag();
            items.forEach(item -> stacks.add(item.copy()));
            tag.put("Items", stacks);
            return tag;
        }
        static Grant load(CompoundTag tag) {
            require(tag.contains("Key", Tag.TAG_STRING) && tag.contains("Operation", Tag.TAG_STRING)
                    && tag.hasUUID("Recipient") && tag.hasUUID("Token") && tag.contains("State", Tag.TAG_STRING), "missing reward identity");
            var items = list(tag, "Items", 256).stream().map(value -> ((CompoundTag) value).copy()).toList();
            return new Grant(tag.getString("Key"), tag.getString("Operation"), tag.getUUID("Recipient"),
                    tag.getUUID("Token"), State.valueOf(tag.getString("State")), items);
        }
    }

    enum State { EARNED, READY, DELIVERED }

    private static ResourceLocation id(String value) {
        if (value.isEmpty()) return null;
        var id = ResourceLocation.tryParse(value);
        require(id != null, "invalid reward resource identity");
        return id;
    }

    private static ListTag list(CompoundTag tag, String key, int limit) {
        require(tag.get(key) instanceof ListTag, "missing reward list " + key);
        var list = (ListTag) tag.get(key);
        require(list.size() <= limit && (list.isEmpty() || list.getElementType() == Tag.TAG_COMPOUND), "invalid reward list " + key);
        return list;
    }

    static void require(boolean valid, String diagnostic) {
        if (!valid) throw new IllegalArgumentException(diagnostic);
    }
}
