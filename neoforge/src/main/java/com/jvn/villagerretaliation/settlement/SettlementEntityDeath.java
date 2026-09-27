package com.jvn.villagerretaliation.settlement;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;

final class SettlementEntityDeath {
    private final UUID id;
    private final ResourceLocation type;
    private final BlockPos position;
    private final UUID area;
    private final UUID worker;
    private final BlockPos workerStart;
    private final Map<UUID, Experience> experience = new LinkedHashMap<>();
    private boolean applied;

    private SettlementEntityDeath(UUID id, ResourceLocation type, BlockPos position, UUID area, UUID worker, BlockPos workerStart) {
        this.id = id;
        this.type = type;
        this.position = position.immutable();
        this.area = area;
        this.worker = worker;
        this.workerStart = workerStart.immutable();
    }

    static SettlementEntityDeath create(LivingEntity entity, BlockPos nativePosition, SettlementWorkerState worker, int steps,
                                        List<ExperienceOrb> orbs, long now, boolean killed) {
        var death = new SettlementEntityDeath(killed ? entity.getUUID() : UUID.randomUUID(), BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()),
                nativePosition, worker.area(), worker.id(), worker.position());
        death.applied = !killed;
        for (var orb : orbs) {
            int route = orb.blockPosition().equals(entity.blockPosition()) ? steps : -1;
            if (route == 0 && worker.position().getBottomCenter().distanceToSqr(orb.position()) > 2.25D) route = -1;
            var experience = new Experience(orb.saveWithoutId(new CompoundTag()), route, now);
            if (death.experience.putIfAbsent(orb.getUUID(), experience) != null) throw new IllegalArgumentException("Death experience duplicates an identity");
        }
        return death;
    }

    UUID id() {
        return this.id;
    }

    ResourceLocation type() {
        return this.type;
    }

    BlockPos position() {
        return this.position;
    }

    UUID area() {
        return this.area;
    }

    boolean applied() {
        return this.applied;
    }

    void apply() {
        this.applied = true;
    }

    Map<UUID, Experience> experience() {
        return Collections.unmodifiableMap(this.experience);
    }

    void removeExperience(UUID id) {
        this.experience.remove(id);
    }

    int steps(SettlementWorkerState worker, Experience experience) {
        return this.worker.equals(worker.id()) && this.area.equals(worker.area()) && this.workerStart.equals(worker.position())
                ? experience.steps : -1;
    }

    CompoundTag save() {
        var tag = new CompoundTag();
        tag.putUUID("Id", this.id);
        tag.putString("Type", this.type.toString());
        tag.putLong("Position", this.position.asLong());
        tag.putUUID("Area", this.area);
        tag.putUUID("Worker", this.worker);
        tag.putLong("WorkerStart", this.workerStart.asLong());
        tag.putBoolean("Applied", this.applied);
        var experience = new ListTag();
        this.experience.values().forEach(value -> experience.add(value.save()));
        tag.put("Experience", experience);
        return tag;
    }

    static SettlementEntityDeath load(CompoundTag tag) {
        var type = ResourceLocation.tryParse(tag.getString("Type"));
        if (!tag.hasUUID("Id") || !tag.hasUUID("Area") || !tag.hasUUID("Worker") || type == null
                || !tag.contains("Position", Tag.TAG_LONG) || !tag.contains("WorkerStart", Tag.TAG_LONG)) {
            throw new IllegalArgumentException("Entity death identity or position is invalid");
        }
        var death = new SettlementEntityDeath(tag.getUUID("Id"), type, BlockPos.of(tag.getLong("Position")),
                tag.getUUID("Area"), tag.getUUID("Worker"), BlockPos.of(tag.getLong("WorkerStart")));
        death.applied = tag.getBoolean("Applied");
        for (var raw : tag.getList("Experience", Tag.TAG_COMPOUND)) {
            var entry = (CompoundTag) raw;
            var experience = new Experience(entry.getCompound("Entity"), entry.getInt("Steps"), Math.max(0L, entry.getLong("CheckedAt")));
            if (death.id.equals(experience.id()) || death.experience.putIfAbsent(experience.id(), experience) != null) {
                throw new IllegalArgumentException("Death experience duplicates an identity");
            }
        }
        return death;
    }

    static final class Experience {
        private final CompoundTag entity;
        private final int steps;
        private long checkedAt;

        private Experience(CompoundTag entity, int steps, long checkedAt) {
            var pos = entity.getList("Pos", Tag.TAG_DOUBLE);
            if (!entity.hasUUID("UUID") || pos.size() != 3 || !Double.isFinite(pos.getDouble(0))
                    || !Double.isFinite(pos.getDouble(1)) || !Double.isFinite(pos.getDouble(2))
                    || entity.getShort("Value") <= 0 || entity.getInt("Count") <= 0 || steps < -1 || steps > 4096) {
                throw new IllegalArgumentException("Death experience entity or access is invalid");
            }
            this.entity = entity.copy();
            this.steps = steps;
            this.checkedAt = checkedAt;
        }

        UUID id() {
            return this.entity.getUUID("UUID");
        }

        BlockPos position() {
            var pos = this.entity.getList("Pos", Tag.TAG_DOUBLE);
            return BlockPos.containing(pos.getDouble(0), pos.getDouble(1), pos.getDouble(2));
        }

        ExperienceOrb context(ServerLevel level) {
            var orb = new ExperienceOrb(level, 0, 0, 0, 1);
            orb.load(this.entity.copy());
            return orb;
        }

        boolean advance(long now) {
            int elapsed = (int) Math.clamp(now - this.checkedAt, 0L, 200L);
            this.checkedAt = now;
            int age = Math.min(6000, this.entity.getShort("Age") + elapsed);
            this.entity.putShort("Age", (short) age);
            return age < 6000;
        }

        int value() {
            return this.entity.getShort("Value");
        }

        void consume() {
            this.entity.putInt("Count", this.entity.getInt("Count") - 1);
        }

        boolean empty() {
            return this.entity.getInt("Count") <= 0;
        }

        CompoundTag save() {
            var tag = new CompoundTag();
            tag.put("Entity", this.entity.copy());
            tag.putInt("Steps", this.steps);
            tag.putLong("CheckedAt", this.checkedAt);
            return tag;
        }
    }
}
