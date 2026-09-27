package com.jvn.villagerretaliation.settlement;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

final class SettlementHuntingTarget {
    private static final String VERSION_TAG = "VillagerRetaliationSettlementHuntingVersion";
    private final SettlementEntityChanges changes = new SettlementEntityChanges();
    private final UUID id;
    private final ResourceLocation type;
    private CompoundTag entity;
    private long observedAt;
    private long version;
    private int invulnerableTicks;
    private float lastHurt;

    private SettlementHuntingTarget(ResourceLocation type, CompoundTag entity, long observedAt) {
        var position = entity.getList("Pos", Tag.TAG_DOUBLE);
        if (!entity.hasUUID("UUID") || position.size() != 3
                || !Double.isFinite(position.getDouble(0)) || !Double.isFinite(position.getDouble(1))
                || !Double.isFinite(position.getDouble(2)) || type.equals(ResourceLocation.withDefaultNamespace("player"))) {
            throw new IllegalArgumentException("Hunting target requires a finite non-player entity identity");
        }
        this.id = entity.getUUID("UUID");
        this.type = type;
        this.entity = entity.copy();
        this.observedAt = Math.max(0L, observedAt);
    }

    static SettlementHuntingTarget capture(LivingEntity entity, long now) {
        if (entity instanceof Player) throw new IllegalArgumentException("Player hunting targets must remain native");
        var state = new SettlementHuntingTarget(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()),
                entity.saveWithoutId(new CompoundTag()), now);
        state.observe(entity, now);
        return state;
    }

    UUID id() {
        return this.id;
    }

    boolean modified() {
        return this.version > 0L;
    }

    long observedAt() {
        return this.observedAt;
    }

    BlockPos position() {
        var position = this.entity.getList("Pos", Tag.TAG_DOUBLE);
        return BlockPos.containing(position.getDouble(0), position.getDouble(1), position.getDouble(2));
    }

    boolean matches(LivingEntity target) {
        return this.id.equals(target.getUUID()) && this.type.equals(BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()));
    }

    void observe(LivingEntity target, long now) {
        if (!matches(target)) throw new IllegalArgumentException("Hunting observation changed entity identity");
        restore(target, now);
        this.entity = target.saveWithoutId(new CompoundTag());
        this.version = Math.max(this.version, target.getPersistentData().getLong(VERSION_TAG));
        captureTimers(target, now);
    }

    void interacted(LivingEntity target, CompoundTag before, long now) {
        if (!matches(target)) throw new IllegalArgumentException("Hunting interaction changed entity identity");
        long next = Math.max(this.version, target.getPersistentData().getLong(VERSION_TAG)) + 1L;
        this.changes.record(before, target.saveWithoutId(new CompoundTag()), next);
        this.version = next;
        target.getPersistentData().putLong(VERSION_TAG, next);
        this.entity = target.saveWithoutId(new CompoundTag());
        captureTimers(target, now);
    }

    private void captureTimers(LivingEntity target, long now) {
        this.observedAt = now;
        this.invulnerableTicks = target.invulnerableTime;
        this.lastHurt = ((com.jvn.villagerretaliation.mixin.LivingEntityCombatAccessor) target).villagerretaliation$getLastHurt();
    }

    boolean restore(LivingEntity target, long now) {
        if (!matches(target) || target.getPersistentData().getLong(VERSION_TAG) >= this.version) return false;
        var merged = target.saveWithoutId(new CompoundTag());
        long applied = target.getPersistentData().getLong(VERSION_TAG);
        this.changes.apply(merged, this.entity, applied);
        if (this.changes.touches("active_effects", applied)) {
            for (var effect : target.getActiveEffects()) effect.getEffect().value().removeAttributeModifiers(target.getAttributes());
            target.getActiveEffectsMap().clear();
            ((com.jvn.villagerretaliation.mixin.LivingEntityCombatAccessor) target).villagerretaliation$setEffectsDirty(true);
        }
        target.load(merged);
        target.getPersistentData().putLong(VERSION_TAG, this.version);
        restoreTimers(target, now);
        return true;
    }

    void restoreTimers(LivingEntity target, long now) {
        int elapsed = (int) Math.clamp(now - this.observedAt, 0L, 200L);
        target.invulnerableTime = Math.max(0, this.invulnerableTicks - elapsed);
        target.hurtTime = Math.max(0, this.entity.getShort("HurtTime") - elapsed);
        ((com.jvn.villagerretaliation.mixin.LivingEntityCombatAccessor) target).villagerretaliation$setLastHurt(this.lastHurt);
    }

    LivingEntity context(ServerLevel level) {
        var type = BuiltInRegistries.ENTITY_TYPE.getOptional(this.type).orElse(null);
        if (type == null || !(type.create(level) instanceof LivingEntity target) || target instanceof Player) return null;
        target.load(this.entity.copy());
        restoreTimers(target, level.getGameTime());
        return target;
    }

    CompoundTag save() {
        var tag = new CompoundTag();
        tag.putString("Type", this.type.toString());
        tag.put("Entity", this.entity.copy());
        tag.putLong("ObservedAt", this.observedAt);
        tag.putLong("Version", this.version);
        tag.put("Changes", this.changes.save());
        tag.putInt("InvulnerableTicks", this.invulnerableTicks);
        tag.putFloat("LastHurt", this.lastHurt);
        return tag;
    }

    static SettlementHuntingTarget load(CompoundTag tag) {
        var state = new SettlementHuntingTarget(ResourceLocation.parse(tag.getString("Type")),
                tag.getCompound("Entity"), tag.getLong("ObservedAt"));
        state.version = Math.max(0L, tag.getLong("Version"));
        state.changes.load(tag.getList("Changes", Tag.TAG_COMPOUND), state.version);
        state.invulnerableTicks = Math.max(0, tag.getInt("InvulnerableTicks"));
        state.lastHurt = Math.max(0.0F, tag.getFloat("LastHurt"));
        return state;
    }
}
