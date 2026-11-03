package com.jvn.villagerretaliation.settlement;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public final class SettlementFallenVillager extends Entity {
    private static final EntityDataAccessor<CompoundTag> APPEARANCE = SynchedEntityData.defineId(SettlementFallenVillager.class, EntityDataSerializers.COMPOUND_TAG);
    public SettlementFallenVillager(EntityType<? extends SettlementFallenVillager> type, Level level) { super(type, level); }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) { builder.define(APPEARANCE, new CompoundTag()); }
    public CompoundTag appearance() { return this.entityData.get(APPEARANCE).copy(); }
    void appearance(CompoundTag snapshot) {
        var tag = new CompoundTag();
        for (String key : new String[]{"UUID", "VillagerData", "CustomName", "Age"}) if (snapshot.contains(key)) tag.put(key, snapshot.get(key).copy());
        this.entityData.set(APPEARANCE, tag);
    }
    @Override protected void readAdditionalSaveData(CompoundTag tag) { this.entityData.set(APPEARANCE, tag.getCompound("Appearance").copy()); }
    @Override protected void addAdditionalSaveData(CompoundTag tag) { tag.put("Appearance", appearance()); }
    @Override public boolean isPickable() { return true; }
    @Override public boolean isPushable() { return false; }
    @Override public boolean hurt(net.minecraft.world.damagesource.DamageSource source, float amount) { return false; }
    @Override public InteractionResult interact(Player player, InteractionHand hand) {
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) SettlementEncounterRecovery.claim(serverPlayer, this);
        return InteractionResult.sidedSuccess(this.level().isClientSide);
    }
}
