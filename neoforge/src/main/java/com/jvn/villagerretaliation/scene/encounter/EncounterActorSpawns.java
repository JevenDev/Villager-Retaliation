package com.jvn.villagerretaliation.scene.encounter;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

final class EncounterActorSpawns {
    private final Map<String, EncounterActorSpawn> records = new LinkedHashMap<>();
    private final Map<UUID, EncounterActorSpawn> entities = new LinkedHashMap<>();
    private final Map<UUID, EncounterActorSpawn> operations = new LinkedHashMap<>();
    private boolean legacy;
    private Tag preserved;
    private String diagnostic = "";

    String diagnostic() { return diagnostic; }
    boolean legacy() { return legacy; }
    Map<String, EncounterActorSpawn> records() { return Collections.unmodifiableMap(records); }
    EncounterActorSpawn get(String key) { return records.get(key); }
    EncounterActorSpawn forEntity(UUID id) { return entities.get(id); }
    EncounterActorSpawn forOperation(UUID operation) {
        return operations.get(operation);
    }

    void removeUnattempted(EncounterActorSpawn receipt) {
        EncounterActorSpawn.require(receipt.state() == EncounterActorSpawn.State.INITIALIZING && records.get(receipt.key()) == receipt,
                "only unattempted actor preparation can be removed");
        records.remove(receipt.key());
        operations.remove(receipt.operation());
        entities.values().removeIf(value -> value == receipt);
    }

    void record(EncounterActorSpawn receipt) {
        EncounterActorSpawn.require(!legacy && diagnostic.isBlank(), "encounter actor spawn history is unavailable");
        var prior = records.get(receipt.key());
        EncounterActorSpawn.require(records.size() < 65536 || prior != null, "encounter actor spawn capacity exceeded");
        EncounterActorSpawn.require(prior == null || prior.sameIdentity(receipt) && receipt.state().ordinal() >= prior.state().ordinal()
                && (prior.state() == EncounterActorSpawn.State.INITIALIZING || prior.nodes().equals(receipt.nodes())), "encounter actor history cannot be replaced");
        var ids = new java.util.HashSet<UUID>();
        ids.add(receipt.entity());
        receipt.nodes().stream().filter(EncounterActorSpawn.Node::owned).map(EncounterActorSpawn.Node::id).forEach(ids::add);
        for (var id : ids) EncounterActorSpawn.require(!entities.containsKey(id) || entities.get(id).operation().equals(receipt.operation()), "actor identity belongs to another birth");
        EncounterActorSpawn.require(!operations.containsKey(receipt.operation()) || operations.get(receipt.operation()).key().equals(receipt.key()), "actor operation belongs to another key");
        records.put(receipt.key(), receipt);
        operations.put(receipt.operation(), receipt);
        ids.forEach(id -> entities.put(id, receipt));
    }

    Tag save() {
        if (preserved != null) return preserved.copy();
        if (legacy) return null;
        var tag = new CompoundTag();
        tag.putInt("Version", 1);
        var list = new ListTag();
        records.values().forEach(receipt -> list.add(receipt.save()));
        tag.put("Records", list);
        return tag;
    }

    static EncounterActorSpawns load(Tag input, UUID encounter) {
        var ledger = new EncounterActorSpawns();
        if (input == null) { ledger.legacy = true; return ledger; }
        try {
            EncounterActorSpawn.require(input instanceof CompoundTag, "actor spawn history is not a compound");
            var tag = (CompoundTag) input;
            EncounterActorSpawn.require(tag.getAllKeys().equals(java.util.Set.of("Version", "Records"))
                    && tag.contains("Version", Tag.TAG_INT) && tag.getInt("Version") == 1
                    && tag.get("Records") instanceof ListTag, "unsupported actor spawn history");
            var list = (ListTag) tag.get("Records");
            EncounterActorSpawn.require(list.size() <= 65536 && (list.isEmpty() || list.getElementType() == Tag.TAG_COMPOUND), "invalid actor spawn records");
            for (var raw : list) {
                var receipt = EncounterActorSpawn.load((CompoundTag) raw);
                EncounterActorSpawn.require(receipt.encounter().equals(encounter) && !ledger.records.containsKey(receipt.key()), "conflicting actor spawn ownership");
                ledger.record(receipt);
            }
        } catch (IllegalArgumentException | IllegalStateException exception) {
            ledger.records.clear();
            ledger.entities.clear();
            ledger.operations.clear();
            ledger.preserved = input.copy();
            ledger.diagnostic = "Encounter actor spawn history is unreadable. Restore compatible scene data before continuing.";
        }
        return ledger;
    }
}
