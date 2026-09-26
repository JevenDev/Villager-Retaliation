package com.jvn.villagerretaliation.settlement;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Sheep;

public final class SettlementLivestock {
    private static final String VERSION_TAG = "VillagerRetaliationSettlementAnimalVersion";
    private final UUID id;
    private final UUID area;
    private final ResourceLocation type;
    private final Map<UUID, Access> access = new LinkedHashMap<>();
    private CompoundTag entity;
    private BlockPos position;
    private long checkedAt;
    private long version;
    private boolean pending;
    private boolean unsafe;

    private SettlementLivestock(UUID id, UUID area, ResourceLocation type) {
        this.id = id;
        this.area = area;
        this.type = type;
    }

    static SettlementLivestock capture(Animal animal, UUID area, long now) {
        var state = new SettlementLivestock(animal.getUUID(), area, BuiltInRegistries.ENTITY_TYPE.getKey(animal.getType()));
        state.observe(animal, now);
        return state;
    }

    public UUID id() {
        return this.id;
    }

    public UUID area() {
        return this.area;
    }

    public ResourceLocation type() {
        return this.type;
    }

    public BlockPos position() {
        return this.position;
    }

    long checkedAt() {
        return this.checkedAt;
    }

    boolean pending() {
        return this.pending;
    }

    boolean unsafe() {
        return this.unsafe;
    }

    void observe(Animal animal, long now) {
        if (!animal.blockPosition().equals(this.position)) this.access.clear();
        this.position = animal.blockPosition().immutable();
        this.entity = animal.saveWithoutId(new CompoundTag());
        this.version = Math.max(this.version, animal.getPersistentData().getLong(VERSION_TAG));
        this.checkedAt = now;
        this.pending = false;
        this.unsafe = animal.getHealth() <= 0.0F || animal.isOnFire() || animal.isInLava()
                || animal.getLastHurtByMob() != null || animal.isPassenger();
    }

    Animal context(ServerLevel level) {
        var type = BuiltInRegistries.ENTITY_TYPE.getOptional(this.type).orElse(null);
        if (type == null || !(type.create(level) instanceof Animal animal)) return null;
        animal.load(this.entity.copy());
        return animal;
    }

    boolean restore(Animal animal) {
        if (!this.id.equals(animal.getUUID()) || !this.type.equals(BuiltInRegistries.ENTITY_TYPE.getKey(animal.getType()))) return false;
        if (animal.getPersistentData().getLong(VERSION_TAG) >= this.version) {
            this.pending = false;
            return false;
        }
        animal.setAge(this.entity.getInt("Age"));
        animal.setInLoveTime(this.entity.getInt("InLove"));
        if (animal instanceof Sheep sheep) sheep.setSheared(this.entity.getBoolean("Sheared"));
        animal.getPersistentData().putLong(VERSION_TAG, this.version);
        this.pending = false;
        return true;
    }

    int advance(long now) {
        int elapsed = (int) Math.clamp(now - this.checkedAt, 0L, 200L);
        this.checkedAt = now;
        if (elapsed == 0 || this.unsafe) return elapsed;
        int age = this.entity.getInt("Age");
        int love = this.entity.getInt("InLove");
        int nextAge = age;
        int nextLove = love;
        for (int tick = 0; tick < elapsed; tick++) {
            if (nextAge != 0 && !this.entity.getBoolean("NoAI")) nextLove = 0;
            nextAge += Integer.compare(0, nextAge);
            if (nextAge != 0) nextLove = 0;
            nextLove = Math.max(0, nextLove - 1);
        }
        if (nextAge != age || nextLove != love) {
            this.entity.putInt("Age", nextAge);
            this.entity.putInt("InLove", nextLove);
            changed();
        }
        return elapsed;
    }

    void shear() {
        this.entity.putBoolean("Sheared", true);
        changed();
    }

    private void changed() {
        this.version++;
        this.pending = true;
    }

    void access(UUID worker, BlockPos start, int steps) {
        if (steps < 0) this.access.remove(worker);
        else this.access.put(worker, new Access(start.immutable(), steps));
    }

    int steps(SettlementWorkerState worker) {
        var route = this.access.get(worker.id());
        return route != null && route.start().equals(worker.position()) ? route.steps() : -1;
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", this.id);
        tag.putUUID("Area", this.area);
        tag.putString("Type", this.type.toString());
        tag.put("Entity", this.entity.copy());
        tag.putLong("Position", this.position.asLong());
        tag.putLong("CheckedAt", this.checkedAt);
        tag.putLong("Version", this.version);
        tag.putBoolean("Pending", this.pending);
        tag.putBoolean("Unsafe", this.unsafe);
        ListTag routes = new ListTag();
        this.access.forEach((worker, route) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Worker", worker);
            entry.putLong("Start", route.start().asLong());
            entry.putInt("Steps", route.steps());
            routes.add(entry);
        });
        tag.put("Access", routes);
        return tag;
    }

    static SettlementLivestock load(CompoundTag tag) {
        ResourceLocation type = ResourceLocation.tryParse(tag.getString("Type"));
        var entity = tag.getCompound("Entity");
        if (!tag.hasUUID("Id") || !tag.hasUUID("Area") || type == null
                || !entity.hasUUID("UUID") || !entity.getUUID("UUID").equals(tag.getUUID("Id"))
                || !tag.contains("Position", Tag.TAG_LONG)) {
            throw new IllegalArgumentException("Livestock identity, type or position is invalid");
        }
        var state = new SettlementLivestock(tag.getUUID("Id"), tag.getUUID("Area"), type);
        state.entity = entity.copy();
        state.position = BlockPos.of(tag.getLong("Position"));
        state.checkedAt = Math.max(0L, tag.getLong("CheckedAt"));
        state.version = Math.max(0L, tag.getLong("Version"));
        state.pending = tag.getBoolean("Pending");
        state.unsafe = tag.getBoolean("Unsafe");
        for (Tag raw : tag.getList("Access", Tag.TAG_COMPOUND)) {
            var entry = (CompoundTag) raw;
            if (!entry.hasUUID("Worker") || !entry.contains("Start", Tag.TAG_LONG)
                    || entry.getInt("Steps") < 0 || entry.getInt("Steps") > 4096
                    || state.access.putIfAbsent(entry.getUUID("Worker"),
                            new Access(BlockPos.of(entry.getLong("Start")), entry.getInt("Steps"))) != null) {
                throw new IllegalArgumentException("Livestock access is duplicated or invalid");
            }
        }
        return state;
    }

    private record Access(BlockPos start, int steps) {
    }
}
