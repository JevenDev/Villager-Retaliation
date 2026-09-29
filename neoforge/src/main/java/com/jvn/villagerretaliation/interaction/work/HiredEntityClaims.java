package com.jvn.villagerretaliation.interaction.work;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Level;

public final class HiredEntityClaims {
    private static final long DURATION = 400L;
    private static final Map<ResourceKey<Level>, Map<UUID, Claim>> CLAIMS = new HashMap<>();

    private HiredEntityClaims() {
    }

    public static boolean available(ServerLevel level, Villager worker, LivingEntity target) {
        if (com.jvn.villagerretaliation.settlement.SettlementHuntingTargetService.claimedByOther(level, worker.getUUID(), target.getUUID())) return false;
        var claims = CLAIMS.get(level.dimension());
        if (claims == null) return true;
        var claim = claims.get(target.getUUID());
        if (claim == null) return true;
        if (!claim.valid(level.getGameTime())) {
            claims.remove(target.getUUID());
            return true;
        }
        return claim.worker().equals(worker.getUUID());
    }

    public static boolean reserve(ServerLevel level, Villager worker, LivingEntity... targets) {
        for (var target : targets) {
            if (!target.isAlive() || target.level() != level || !available(level, worker, target)) return false;
        }
        var keep = new HashSet<UUID>();
        for (var target : targets) keep.add(target.getUUID());
        var claims = CLAIMS.computeIfAbsent(level.dimension(), ignored -> new HashMap<>());
        claims.entrySet().removeIf(entry -> entry.getValue().worker().equals(worker.getUUID()) && !keep.contains(entry.getKey()));
        var claim = new Claim(worker.getUUID(), new WeakReference<>(worker), level.getGameTime() + DURATION);
        for (var target : targets) claims.put(target.getUUID(), claim);
        return true;
    }

    public static void release(Villager worker) {
        CLAIMS.values().forEach(claims -> claims.entrySet().removeIf(entry -> entry.getValue().worker().equals(worker.getUUID())));
        CLAIMS.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    public static void expire(ServerLevel level) {
        var claims = CLAIMS.get(level.dimension());
        if (claims == null) return;
        claims.entrySet().removeIf(entry -> !entry.getValue().valid(level.getGameTime()));
        if (claims.isEmpty()) CLAIMS.remove(level.dimension());
    }

    public static void clear() {
        CLAIMS.clear();
    }

    private record Claim(UUID worker, WeakReference<Villager> entity, long expires) {
        boolean valid(long now) {
            var owner = entity.get();
            return now < expires && owner != null && owner.isAlive() && !owner.isRemoved();
        }
    }
}
