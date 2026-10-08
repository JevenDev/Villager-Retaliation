package com.jvn.villagerretaliation.scene.encounter;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

record EncounterActorSpawn(String key, UUID encounter, UUID operation, UUID entity, ResourceLocation dimension,
        BlockPos anchor, State state, List<Node> nodes) {
    EncounterActorSpawn {
        require(key != null && !key.isBlank() && key.length() <= 256 && encounter != null && operation != null && entity != null
                && dimension != null && anchor != null && state != null && nodes != null && nodes.size() <= 256, "invalid encounter actor spawn");
        var parts = key.split("/");
        require(parts.length == 3 && parts[0].equals("member") || parts.length == 4 && parts[0].equals("ally") && !parts[2].isBlank(),
                "invalid encounter actor key");
        require(Integer.parseInt(parts[1]) >= 0 && Integer.parseInt(parts[parts.length - 1]) >= 0, "invalid encounter actor generation or index");
        require(operation.equals(operation(encounter, key)), "encounter actor operation changed");
        anchor = anchor.immutable();
        nodes = List.copyOf(nodes);
        require(state == State.INITIALIZING ? nodes.isEmpty() : state.ordinal() > State.COMMITTED.ordinal() || !nodes.isEmpty(), "incomplete encounter actor snapshot");
        var ids = new HashSet<UUID>();
        var prior = new HashSet<UUID>();
        for (var node : nodes) {
            require(ids.add(node.id()) && (node.parent() == null || prior.contains(node.parent())), "duplicate or cyclic encounter actor snapshot");
            if (node.owned()) {
                var owner = node.entity().getCompound("NeoForgeData").getCompound(EncounterActorSpawning.OWNER);
                require(owner.getAllKeys().equals(java.util.Set.of("Encounter", "Operation")) && owner.hasUUID("Encounter") && owner.hasUUID("Operation")
                        && owner.getUUID("Encounter").equals(encounter) && owner.getUUID("Operation").equals(operation), "actor snapshot has conflicting birth ownership");
            }
            prior.add(node.id());
        }
        require(nodes.isEmpty() || nodes.stream().anyMatch(node -> node.owned() && node.id().equals(entity)), "actor snapshot omits its reserved identity");
    }

    static UUID operation(UUID encounter, String key) {
        return UUID.nameUUIDFromBytes((encounter + "/actor/" + key).getBytes(StandardCharsets.UTF_8));
    }

    static EncounterActorSpawn prepare(String key, UUID encounter, UUID entity, ResourceLocation dimension, BlockPos anchor) {
        return new EncounterActorSpawn(key, encounter, operation(encounter, key), entity, dimension, anchor, State.INITIALIZING, List.of());
    }

    EncounterActorSpawn ready(List<Node> captured) {
        require(state == State.INITIALIZING, "actor initialization is already recorded");
        return new EncounterActorSpawn(key, encounter, operation, entity, dimension, anchor, State.READY, captured);
    }

    EncounterActorSpawn reserved() {
        require(state == State.READY, "actor snapshot is not ready");
        return new EncounterActorSpawn(key, encounter, operation, entity, dimension, anchor, State.RESERVED, nodes);
    }

    EncounterActorSpawn committed() {
        require(state == State.RESERVED || state == State.COMMITTED, "unreserved actor cannot commit");
        return new EncounterActorSpawn(key, encounter, operation, entity, dimension, anchor, State.COMMITTED, nodes);
    }

    EncounterActorSpawn retired(boolean remove) {
        return new EncounterActorSpawn(key, encounter, operation, entity, dimension, anchor, remove ? State.REMOVED : State.RELEASED, nodes);
    }

    boolean sameIdentity(EncounterActorSpawn other) {
        return key.equals(other.key) && encounter.equals(other.encounter) && operation.equals(other.operation)
                && entity.equals(other.entity) && dimension.equals(other.dimension) && anchor.equals(other.anchor);
    }

    UUID signature() {
        require(state != State.INITIALIZING, "actor initialization has no native proof");
        var planned = save();
        planned.remove("State");
        return UUID.nameUUIDFromBytes(planned.toString().getBytes(StandardCharsets.UTF_8));
    }

    CompoundTag save() {
        var tag = new CompoundTag();
        tag.putString("Key", key);
        tag.putUUID("Encounter", encounter);
        tag.putUUID("Operation", operation);
        tag.putUUID("Entity", entity);
        tag.putString("Dimension", dimension.toString());
        tag.putLong("Anchor", anchor.asLong());
        tag.putString("State", state.name());
        var list = new ListTag();
        nodes.forEach(node -> list.add(node.save()));
        tag.put("Nodes", list);
        return tag;
    }

    static EncounterActorSpawn load(CompoundTag tag) {
        require(tag.getAllKeys().equals(java.util.Set.of("Key", "Encounter", "Operation", "Entity", "Dimension", "Anchor", "State", "Nodes"))
                && tag.contains("Key", Tag.TAG_STRING) && tag.hasUUID("Encounter") && tag.hasUUID("Operation") && tag.hasUUID("Entity")
                && tag.contains("Dimension", Tag.TAG_STRING) && tag.contains("Anchor", Tag.TAG_LONG) && tag.contains("State", Tag.TAG_STRING)
                && tag.get("Nodes") instanceof ListTag, "invalid saved encounter actor");
        var list = (ListTag) tag.get("Nodes");
        require(list.isEmpty() || list.getElementType() == Tag.TAG_COMPOUND, "invalid actor node list");
        return new EncounterActorSpawn(tag.getString("Key"), tag.getUUID("Encounter"), tag.getUUID("Operation"), tag.getUUID("Entity"),
                ResourceLocation.tryParse(tag.getString("Dimension")), BlockPos.of(tag.getLong("Anchor")), State.valueOf(tag.getString("State")),
                list.stream().map(raw -> Node.load((CompoundTag) raw)).toList());
    }

    static void require(boolean condition, String issue) {
        if (!condition) throw new IllegalArgumentException(issue);
    }

    enum State { INITIALIZING, READY, RESERVED, COMMITTED, RELEASED, REMOVED }

    record Node(CompoundTag entity, UUID parent, boolean owned) {
        Node {
            require(entity != null && entity.hasUUID("UUID") && entity.contains("id", Tag.TAG_STRING)
                    && ResourceLocation.tryParse(entity.getString("id")) != null && !entity.contains("Passengers")
                    && entity.get("Pos") instanceof ListTag position && position.size() == 3 && position.getElementType() == Tag.TAG_DOUBLE,
                    "invalid encounter actor entity snapshot");
            entity = entity.copy();
            var position = entity.getList("Pos", Tag.TAG_DOUBLE);
            for (int index = 0; index < 3; index++) require(Double.isFinite(position.getDouble(index))
                    && Math.abs(position.getDouble(index)) <= 30_000_000, "invalid encounter actor position");
        }
        @Override public CompoundTag entity() { return entity.copy(); }
        UUID id() { return entity.getUUID("UUID"); }
        ResourceLocation type() { return ResourceLocation.parse(entity.getString("id")); }
        BlockPos position() {
            var position = entity.getList("Pos", Tag.TAG_DOUBLE);
            return BlockPos.containing(position.getDouble(0), position.getDouble(1), position.getDouble(2));
        }
        CompoundTag save() {
            var tag = new CompoundTag();
            tag.put("Entity", entity());
            tag.putBoolean("Owned", owned);
            if (parent != null) tag.putUUID("Parent", parent);
            return tag;
        }
        static Node load(CompoundTag tag) {
            require(tag.getAllKeys().equals(tag.contains("Parent") ? java.util.Set.of("Entity", "Owned", "Parent") : java.util.Set.of("Entity", "Owned"))
                    && tag.contains("Entity", Tag.TAG_COMPOUND) && tag.contains("Owned", Tag.TAG_BYTE)
                    && (tag.getByte("Owned") == 0 || tag.getByte("Owned") == 1)
                    && (!tag.contains("Parent") || tag.hasUUID("Parent")), "invalid saved actor node");
            return new Node(tag.getCompound("Entity"), tag.hasUUID("Parent") ? tag.getUUID("Parent") : null, tag.getBoolean("Owned"));
        }
    }
}
