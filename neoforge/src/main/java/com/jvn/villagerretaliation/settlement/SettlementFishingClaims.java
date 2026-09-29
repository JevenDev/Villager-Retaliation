package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.entity.VillagerFishingHook;
import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.HiredWorkStateStore;
import com.jvn.villagerretaliation.interaction.work.HiredPathMemory;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;

public final class SettlementFishingClaims {
    private SettlementFishingClaims() {
    }

    public static boolean available(ServerLevel level, UUID worker, BlockPos water) {
        if (HiredPathMemory.isReservedByOther(level, worker, water)) return false;
        boolean continuing = continuing(level, worker, water);
        for (var town : SettlementSavedData.get(level).settlements()) {
            if (!town.dimension().equals(level.dimension().location())) continue;
            for (var other : town.workers().values()) {
                if (!other.id().equals(worker) && holding(level, town, other, water)
                        && (!continuing || other.id().compareTo(worker) < 0)) return false;
            }
        }
        return true;
    }

    public static boolean reserve(ServerLevel level, UUID worker, BlockPos water) {
        if (!available(level, worker, water)) return false;
        HiredPathMemory.reserveTarget(level, worker, water);
        return true;
    }

    private static boolean continuing(ServerLevel level, UUID worker, BlockPos water) {
        for (var town : SettlementSavedData.get(level).settlements()) {
            var own = town.workers().get(worker);
            if (town.dimension().equals(level.dimension().location()) && own != null && holding(level, town, own, water)) return true;
        }
        return false;
    }

    private static boolean holding(ServerLevel level, SettlementRecord town, SettlementWorkerState worker, BlockPos water) {
        if (worker.role() != HiredVillagerRole.FISHING || !worker.area().equals(town.duties().get(worker.id()))) return false;
        var area = town.workplace(worker.area());
        var effective = town.effectiveWorkerSettings(worker.id());
        if (area == null || !area.enabled() || effective != null && !effective.enabled()) return false;
        CompoundTag state = worker.settings();
        if (level.getEntity(worker.id()) instanceof Villager villager) {
            if (!villager.isAlive() || SettlementWorkforceService.hasPersonalCommitment(villager)) return false;
            var live = HiredWorkStateStore.state(villager);
            if (worker.version() <= villager.getPersistentData().getLong("VillagerRetaliationSettlementWorkVersion")) state = live;
            if (live.contains("FishingActiveHookId", Tag.TAG_INT) && live.contains("FishingWaterPos", Tag.TAG_LONG)
                    && live.getLong("FishingWaterPos") == water.asLong()
                    && level.getEntity(live.getInt("FishingActiveHookId")) instanceof VillagerFishingHook hook
                    && hook.isAlive() && hook.getOwner() == villager) return true;
        }
        return (!state.contains("Enabled") || state.getBoolean("Enabled"))
                && state.contains("SettlementFishingCast", Tag.TAG_COMPOUND)
                && state.getCompound("SettlementFishingCast").contains("Water", Tag.TAG_LONG)
                && state.getCompound("SettlementFishingCast").getLong("Water") == water.asLong();
    }
}
