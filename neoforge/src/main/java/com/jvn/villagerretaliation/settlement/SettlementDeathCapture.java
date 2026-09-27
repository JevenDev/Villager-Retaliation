package com.jvn.villagerretaliation.settlement;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.Vec3;

public final class SettlementDeathCapture implements AutoCloseable {
    private static final ThreadLocal<SettlementDeathCapture> ACTIVE = new ThreadLocal<>();
    private final SettlementDeathCapture previous;
    private final LivingEntity victim;
    private final java.util.Map<java.util.UUID, ItemEntity> drops = new java.util.LinkedHashMap<>();
    private final List<ExperienceOrb> experience = new ArrayList<>();
    private boolean accepted;

    SettlementDeathCapture(LivingEntity victim) {
        this.victim = victim;
        this.previous = ACTIVE.get();
        ACTIVE.set(this);
    }

    private static SettlementDeathCapture capture(LivingEntity victim) {
        for (var capture = ACTIVE.get(); capture != null; capture = capture.previous) {
            if (capture.victim == victim) return capture;
        }
        return null;
    }

    public static void accepted(LivingEntity victim) {
        var capture = capture(victim);
        if (capture != null) capture.accepted = true;
    }

    public static boolean drops(LivingEntity victim, Collection<ItemEntity> drops) {
        var capture = capture(victim);
        if (capture == null) return false;
        for (var drop : drops) capture.drops.putIfAbsent(drop.getUUID(), drop);
        return true;
    }

    public static boolean experience(LivingEntity victim, Vec3 position, int value, Villager owner) {
        var capture = capture(victim);
        if (capture == null) return false;
        while (value > 0) {
            int amount = owner == null ? ExperienceOrb.getExperienceValue(value) : value;
            var orb = new ExperienceOrb((ServerLevel) victim.level(), position.x, position.y, position.z, amount);
            if (owner != null) orb.getPersistentData().putUUID("VillagerRetaliationWorkExperienceOwner", owner.getUUID());
            capture.experience.add(orb);
            value -= amount;
        }
        return true;
    }

    boolean accepted() {
        return this.accepted;
    }

    List<ItemEntity> drops() {
        return List.copyOf(this.drops.values());
    }

    List<ExperienceOrb> experience() {
        return List.copyOf(this.experience);
    }

    @Override
    public void close() {
        if (ACTIVE.get() != this) throw new IllegalStateException("Death capture scopes closed out of order");
        if (this.previous == null) ACTIVE.remove();
        else ACTIVE.set(this.previous);
    }
}
