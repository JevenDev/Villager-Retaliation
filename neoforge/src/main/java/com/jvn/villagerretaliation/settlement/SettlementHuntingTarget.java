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
    private final UUID id;
    private final ResourceLocation type;
    private CompoundTag entity;
    private long observedAt;

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
        return new SettlementHuntingTarget(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()),
                entity.saveWithoutId(new CompoundTag()), now);
    }

    UUID id() {
        return this.id;
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
        this.entity = target.saveWithoutId(new CompoundTag());
        this.observedAt = now;
    }

    LivingEntity context(ServerLevel level) {
        var type = BuiltInRegistries.ENTITY_TYPE.getOptional(this.type).orElse(null);
        if (type == null || !(type.create(level) instanceof LivingEntity target) || target instanceof Player) return null;
        target.load(this.entity.copy());
        return target;
    }

    CompoundTag save() {
        var tag = new CompoundTag();
        tag.putString("Type", this.type.toString());
        tag.put("Entity", this.entity.copy());
        tag.putLong("ObservedAt", this.observedAt);
        return tag;
    }

    static SettlementHuntingTarget load(CompoundTag tag) {
        return new SettlementHuntingTarget(ResourceLocation.parse(tag.getString("Type")),
                tag.getCompound("Entity"), tag.getLong("ObservedAt"));
    }
}
