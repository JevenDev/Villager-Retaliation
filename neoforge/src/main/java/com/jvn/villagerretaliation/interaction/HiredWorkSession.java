package com.jvn.villagerretaliation.interaction;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.jvn.villagerretaliation.interaction.work.HiredRoleWorker;
import com.jvn.villagerretaliation.interaction.work.HiredRoleWorkerRegistry;
import com.jvn.villagerretaliation.interaction.work.HiredWorkContext;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;

public record HiredWorkSession(
        CompoundTag state,
        HiredVillagerRole role,
        HiredRoleWorker worker,
        HiredJobInventory inventory,
        int maxRadius,
        HiredWorkArea area,
        HiredRoute route,
        HiredJobSite jobSite,
        int efficiency,
        HiredWorkContext context) {
    private static final long METADATA_CACHE_TICKS = 40L;
    private static final Map<UUID, CachedMetadata> METADATA_CACHE = new HashMap<>();

    public static HiredWorkSession active(ServerLevel level, Villager villager) {
        return create(level, villager, HiredVillagerContractService.activeRole(level, villager));
    }

    public static HiredWorkSession create(ServerLevel level, Villager villager, HiredVillagerRole role) {
        CompoundTag state = HiredWorkStateStore.state(villager);
        HiredWorkStateStore.initializeDefaults(state, villager);
        HiredVillagerRole safeRole = role == null ? HiredVillagerRoles.defaultRole(level, villager) : role;
        HiredRoleWorker worker = HiredRoleWorkerRegistry.get(safeRole);
        HiredJobInventory inventory = HiredJobInventory.getJobInventory(villager);
        CachedMetadata metadata = metadata(level, villager, safeRole, state, inventory);
        HiredWorkArea area = metadata.jobSite.workArea();
        HiredRoute route = HiredRoute.fromState(state);
        int skillWorkSpeedPercent = HiredVillagerRoles.skillWorkSpeedPercent(metadata.roleScore);
        int transferCapacityPercent = HiredVillagerRoles.transferCapacityPercent(metadata.roleScore);
        HiredWorkContext context = new HiredWorkContext(
                inventory,
                state,
                area.center(),
                area.min(),
                area.max(),
                area.horizontalRadius(),
                area.verticalRadius(),
                area.usable(),
                metadata.roleScore,
                skillWorkSpeedPercent,
                transferCapacityPercent,
                metadata.efficiency,
                state.getBoolean("AutoDepositOutputs"),
                state.getBoolean("UseAssignedStorageForSupplies"),
                metadata.jobSite,
                route);
        return new HiredWorkSession(state, safeRole, worker, inventory, metadata.maxRadius, area, route,
                metadata.jobSite, metadata.efficiency, context);
    }

    /** Uses cached efficiency for the early scheduling gate without constructing a full session. */
    public static int cachedDecisionInterval(ServerLevel level, Villager villager, HiredVillagerRole role) {
        CachedMetadata cached = METADATA_CACHE.get(villager.getUUID());
        if (cached == null) {
            return Math.max(10, VillagerRetaliationConfig.HIRED_WORK_TICK_INTERVAL.get());
        }
        CompoundTag state = HiredWorkStateStore.state(villager);
        HiredJobInventory inventory = HiredJobInventory.getJobInventory(villager);
        MetadataSignature signature = signature(level, villager, role, state, inventory);
        if (!cached.signature.equals(signature) || level.getGameTime() >= cached.refreshGameTime) {
            return Math.max(10, VillagerRetaliationConfig.HIRED_WORK_TICK_INTERVAL.get());
        }
        return HiredVillagerWorkService.effectiveWorkTickInterval(cached.efficiency);
    }

    public static void invalidate(Villager villager) {
        if (villager != null) {
            METADATA_CACHE.remove(villager.getUUID());
        }
    }

    public static void clearRuntimeState() {
        METADATA_CACHE.clear();
    }

    private static CachedMetadata metadata(
            ServerLevel level,
            Villager villager,
            HiredVillagerRole role,
            CompoundTag state,
            HiredJobInventory inventory) {
        MetadataSignature signature = signature(level, villager, role, state, inventory);
        CachedMetadata cached = METADATA_CACHE.get(villager.getUUID());
        if (cached != null
                && cached.signature.equals(signature)
                && level.getGameTime() < cached.refreshGameTime) {
            return cached;
        }
        int roleScore = HiredVillagerRoles.roleScore(level, villager, role);
        int maxRadius = HiredVillagerWorkService.maxWorkRadius(level, villager, role, roleScore);
        HiredJobSite jobSite = HiredVillagerWorkService.jobSite(level, villager, role, state, maxRadius);
        int efficiency = HiredVillagerWorkService.efficiencyPercent(
                level, villager, role, state, inventory, roleScore);
        CachedMetadata refreshed = new CachedMetadata(
                signature,
                roleScore,
                maxRadius,
                jobSite,
                efficiency,
                level.getGameTime() + METADATA_CACHE_TICKS);
        METADATA_CACHE.put(villager.getUUID(), refreshed);
        return refreshed;
    }

    private static MetadataSignature signature(
            ServerLevel level,
            Villager villager,
            HiredVillagerRole role,
            CompoundTag state,
            HiredJobInventory inventory) {
        GlobalPos jobSite = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).orElse(null);
        int equipmentFingerprint = 1;
        for (int slot = 0; slot <= HiredJobInventory.OFFHAND_SLOT; slot++) {
            ItemStack stack = inventory.getItem(slot);
            equipmentFingerprint = 31 * equipmentFingerprint + (stack.isEmpty() ? 0 : stack.getItem().hashCode());
            equipmentFingerprint = 31 * equipmentFingerprint + (stack.isEmpty() ? 0 : stack.getComponents().hashCode());
            equipmentFingerprint = 31 * equipmentFingerprint + stack.getCount();
        }
        return new MetadataSignature(
                level.dimension(),
                role,
                villager.getVillagerData().getProfession(),
                villager.getVillagerData().getLevel(),
                state.getLong(HiredWorkArea.WORK_CENTER_POS_TAG),
                state.getLong(HiredWorkArea.WORK_MIN_POS_TAG),
                state.getLong(HiredWorkArea.WORK_MAX_POS_TAG),
                state.getInt(HiredWorkArea.RADIUS_TAG),
                state.getBoolean(HiredWorkArea.WORK_AREA_ASSIGNED_TAG),
                jobSite,
                equipmentFingerprint);
    }

    private record CachedMetadata(
            MetadataSignature signature,
            int roleScore,
            int maxRadius,
            HiredJobSite jobSite,
            int efficiency,
            long refreshGameTime) {
    }

    private record MetadataSignature(
            Object dimension,
            HiredVillagerRole role,
            Object profession,
            int professionLevel,
            long workCenter,
            long workMin,
            long workMax,
            int radius,
            boolean assigned,
            GlobalPos jobSite,
            int equipmentFingerprint) {
    }
}
