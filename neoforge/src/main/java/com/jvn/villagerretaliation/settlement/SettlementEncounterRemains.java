package com.jvn.villagerretaliation.settlement;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

final class SettlementEncounterRemains {
    static final int RADIUS = 24;
    final UUID id, encounter, resident;
    final BlockPos center;
    final CompoundTag appearance;
    final List<ItemStack> items = new ArrayList<>();
    BlockPos position;
    UUID claimant;
    boolean claimed;
    final List<SettlementCourierRecovery.Drop> overflow = new ArrayList<>();
    SettlementEncounterRemains(UUID id, UUID encounter, UUID resident, BlockPos center, CompoundTag appearance) {
        this.id = id; this.encounter = encounter; this.resident = resident; this.center = center.immutable(); this.appearance = appearance.copy();
    }
    boolean pending() { return !claimed && !items.isEmpty() || overflow.stream().anyMatch(drop -> drop.state == SettlementCourierRecovery.State.RESERVED); }
    CompoundTag save(HolderLookup.Provider provider) {
        var tag = new CompoundTag(); tag.putUUID("Id", id); tag.putUUID("Encounter", encounter); tag.putUUID("Resident", resident);
        tag.putLong("Center", center.asLong()); tag.put("Appearance", appearance.copy());
        if (position != null) tag.putLong("Position", position.asLong());
        if (claimant != null) tag.putUUID("Claimant", claimant);
        tag.putBoolean("Claimed", claimed); var stacks = new ListTag();
        items.forEach(item -> stacks.add(item.save(provider))); tag.put("Items", stacks);
        tag.put("Overflow", SettlementCourierRecovery.save(overflow, provider)); return tag;
    }
    static SettlementEncounterRemains load(CompoundTag tag, HolderLookup.Provider provider) {
        if (!tag.hasUUID("Id") || !tag.hasUUID("Encounter") || !tag.hasUUID("Resident") || !tag.contains("Center", Tag.TAG_LONG)
                || !tag.contains("Appearance", Tag.TAG_COMPOUND) || !tag.contains("Claimed", Tag.TAG_BYTE))
            throw new IllegalArgumentException("Invalid encounter remains");
        var result = new SettlementEncounterRemains(tag.getUUID("Id"), tag.getUUID("Encounter"), tag.getUUID("Resident"),
                BlockPos.of(tag.getLong("Center")), tag.getCompound("Appearance"));
        if (tag.contains("Position")) result.position = BlockPos.of(tag.getLong("Position"));
        if (tag.contains("Claimant")) result.claimant = tag.getUUID("Claimant");
        result.claimed = tag.getBoolean("Claimed");
        var items = tag.getList("Items", Tag.TAG_COMPOUND);
        if (!items.equals(tag.get("Items")) || items.size() > 512) throw new IllegalArgumentException("Invalid recovery items");
        for (var raw : items) result.items.add(ItemStack.parse(provider, (CompoundTag) raw)
                .orElseThrow(() -> new IllegalArgumentException("Unreadable recovery item")));
        var overflowItems = tag.getList("Overflow", Tag.TAG_COMPOUND);
        if (!overflowItems.equals(tag.get("Overflow")) || result.claimed && (!result.items.isEmpty() || result.claimant == null))
            throw new IllegalArgumentException("Invalid recovery claim state");
        var overflow = tag.copy(); overflow.put("Recovery", overflowItems);
        result.overflow.addAll(SettlementCourierRecovery.load(overflow, provider, 512));
        if (result.position != null && (Math.pow((double) result.position.getX() - result.center.getX(), 2)
                + Math.pow((double) result.position.getZ() - result.center.getZ(), 2)) > RADIUS * RADIUS)
            throw new IllegalArgumentException("Remains outside recovery area");
        return result;
    }
}
