package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.mixin.LivingEntityCombatAccessor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.LivingEntity;

record SettlementCombatClock(long at, int invulnerableTicks, int hurtTicks, float lastHurt) {
    private static final String PENDING_TAG = "VillagerRetaliationSettlementCombatClock";

    static SettlementCombatClock capture(LivingEntity entity, long now) {
        return new SettlementCombatClock(now, entity.invulnerableTime, entity.hurtTime,
                ((LivingEntityCombatAccessor) entity).villagerretaliation$getLastHurt());
    }

    static SettlementCombatClock observe(LivingEntity entity, long now, long version) {
        var pending = entity.getPersistentData().getCompound(PENDING_TAG);
        return !pending.isEmpty() && pending.getLong("Version") == version ? load(pending) : capture(entity, now);
    }

    void apply(LivingEntity entity, long now) {
        int elapsed = (int) Math.clamp(now - this.at, 0L, 200L);
        entity.invulnerableTime = Math.max(0, this.invulnerableTicks - elapsed);
        entity.hurtTime = Math.max(0, this.hurtTicks - elapsed);
        ((LivingEntityCombatAccessor) entity).villagerretaliation$setLastHurt(this.lastHurt);
    }

    void pending(LivingEntity entity, long version) {
        var tag = new CompoundTag();
        save(tag);
        tag.putLong("Version", version);
        entity.getPersistentData().put(PENDING_TAG, tag);
    }

    static void resume(LivingEntity entity, long now, long version) {
        var data = entity.getPersistentData();
        if (!data.contains(PENDING_TAG, Tag.TAG_COMPOUND)) return;
        var pending = data.getCompound(PENDING_TAG);
        if (pending.getLong("Version") == version) load(pending).apply(entity, now);
        data.remove(PENDING_TAG);
    }

    void save(CompoundTag tag) {
        tag.putLong("ObservedAt", this.at);
        tag.putInt("InvulnerableTicks", this.invulnerableTicks);
        tag.putInt("HurtTicks", this.hurtTicks);
        tag.putFloat("LastHurt", this.lastHurt);
    }

    static SettlementCombatClock load(CompoundTag tag) {
        return new SettlementCombatClock(Math.max(0L, tag.getLong("ObservedAt")),
                Math.max(0, tag.getInt("InvulnerableTicks")),
                Math.max(0, tag.contains("HurtTicks") ? tag.getInt("HurtTicks") : tag.getCompound("Entity").getShort("HurtTime")),
                Math.max(0.0F, tag.getFloat("LastHurt")));
    }
}
