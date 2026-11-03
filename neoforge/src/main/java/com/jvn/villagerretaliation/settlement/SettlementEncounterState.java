package com.jvn.villagerretaliation.settlement;

import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

final class SettlementEncounterState {
    enum Stage { ACTIVE, RETIRING, DELAYED, COMPLETE }
    final UUID id;
    final SettlementEncounterDefinition definition;
    final BlockPos center;
    final List<UUID> participants = new ArrayList<>();
    final List<CompoundTag> attackers = new ArrayList<>();
    long started, lastTick, elapsed, checkpointRevision, generation = 1;
    Stage stage = Stage.ACTIVE;
    boolean visible;
    SettlementEncounterState(UUID id, SettlementEncounterDefinition definition, BlockPos center, long now) {
        this.id = id; this.definition = definition; this.center = center.immutable(); this.started = now; this.lastTick = now;
    }
    boolean complete() { return this.stage == Stage.COMPLETE; }
    CompoundTag save() {
        var tag = new CompoundTag(); tag.putInt("Version", 1); tag.putUUID("Id", id);
        tag.putString("DefinitionId", definition.id().toString()); tag.putString("Definition", definition.source().toString());
        tag.putLong("Center", center.asLong()); tag.putLong("Started", started); tag.putLong("LastTick", lastTick);
        tag.putLong("Elapsed", elapsed); tag.putLong("Generation", generation); tag.putString("Stage", stage.name());
        tag.putLong("CheckpointRevision", checkpointRevision);
        tag.putBoolean("Visible", visible);
        var members = new ListTag(); for (var member : participants) members.add(net.minecraft.nbt.NbtUtils.createUUID(member));
        tag.put("Participants", members); var actors = new ListTag(); attackers.forEach(actor -> actors.add(actor.copy())); tag.put("Attackers", actors);
        return tag;
    }
    static SettlementEncounterState load(CompoundTag tag) {
        if (tag.getInt("Version") != 1 || !tag.hasUUID("Id") || !tag.contains("Center", Tag.TAG_LONG))
            throw new IllegalArgumentException("invalid journey encounter identity");
        var definition = SettlementEncounterDefinition.parse(ResourceLocation.parse(tag.getString("DefinitionId")),
                JsonParser.parseString(tag.getString("Definition")).getAsJsonObject());
        var result = new SettlementEncounterState(tag.getUUID("Id"), definition, BlockPos.of(tag.getLong("Center")), tag.getLong("Started"));
        result.lastTick = tag.getLong("LastTick"); result.elapsed = tag.getLong("Elapsed"); result.generation = tag.getLong("Generation");
        result.checkpointRevision = tag.getLong("CheckpointRevision");
        result.stage = Stage.valueOf(tag.getString("Stage")); result.visible = tag.getBoolean("Visible");
        var members = tag.getList("Participants", Tag.TAG_INT_ARRAY);
        var actors = tag.getList("Attackers", Tag.TAG_COMPOUND);
        if (!members.equals(tag.get("Participants")) || !actors.equals(tag.get("Attackers")) || members.isEmpty()
                || members.size() > 8 || actors.size() > 32 || result.started < 0 || result.lastTick < 0
                || result.elapsed < 0 || result.checkpointRevision < 0 || result.generation < 1) throw new IllegalArgumentException("invalid journey encounter progress");
        for (var member : members) result.participants.add(net.minecraft.nbt.NbtUtils.loadUUID(member));
        var ids = new java.util.HashSet<UUID>();
        for (var raw : actors) {
            var actor = (CompoundTag) raw;
            if (!actor.hasUUID("UUID") || !ids.add(actor.getUUID("UUID")) || !actor.contains("id", Tag.TAG_STRING)
                    || !actor.contains("Health", Tag.TAG_FLOAT) || !Float.isFinite(actor.getFloat("Health")))
                throw new IllegalArgumentException("invalid encounter attacker");
            result.attackers.add(actor.copy());
        }
        if (result.participants.stream().distinct().count() != members.size()) throw new IllegalArgumentException("duplicate encounter participant");
        return result;
    }
}
