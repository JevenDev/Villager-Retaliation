package com.jvn.villagerretaliation.debug;

import com.jvn.villagerretaliation.interaction.HiredVillagerContractService;
import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.HiredVillagerWorkService;
import com.jvn.villagerretaliation.interaction.HiredWorkArea;
import com.jvn.villagerretaliation.inventory.AssignedStorageSavedData.AssignedContainerRecord;
import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import com.jvn.villagerretaliation.network.HiredDebugPreviewSyncPayload;
import com.jvn.villagerretaliation.villager.VillagerPresetNameRegistry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.network.PacketDistributor;

public final class HiredDebugPreviewService {
    public static final double DEFAULT_RADIUS = 128.0D;
    public static final double MAX_RADIUS = 512.0D;
    private static final int REFRESH_TICKS = 80;
    private static final int VISIBLE_TICKS = REFRESH_TICKS + 40;
    private static final Map<UUID, DebugPreviewState> ENABLED_PLAYERS = new HashMap<>();

    private HiredDebugPreviewService() {
    }

    public static DebugPreviewSummary toggle(ServerPlayer player, double radius) {
        UUID playerId = player.getUUID();
        if (ENABLED_PLAYERS.containsKey(playerId)) {
            ENABLED_PLAYERS.remove(playerId);
            PacketDistributor.sendToPlayer(player, HiredDebugPreviewSyncPayload.disabled());
            return new DebugPreviewSummary(false, 0, 0, 0, sanitizeRadius(radius));
        }
        ENABLED_PLAYERS.put(playerId, new DebugPreviewState(sanitizeRadius(radius), 0L));
        return refreshNow(player);
    }

    public static DebugPreviewSummary setEnabled(ServerPlayer player, boolean enabled, double radius) {
        UUID playerId = player.getUUID();
        if (!enabled) {
            ENABLED_PLAYERS.remove(playerId);
            PacketDistributor.sendToPlayer(player, HiredDebugPreviewSyncPayload.disabled());
            return new DebugPreviewSummary(false, 0, 0, 0, sanitizeRadius(radius));
        }
        ENABLED_PLAYERS.put(playerId, new DebugPreviewState(sanitizeRadius(radius), 0L));
        return refreshNow(player);
    }

    public static void onPlayerTick(ServerPlayer player) {
        DebugPreviewState state = ENABLED_PLAYERS.get(player.getUUID());
        if (state == null || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        long gameTime = level.getGameTime();
        if (gameTime < state.nextRefreshGameTime()) {
            return;
        }
        refresh(player, state, gameTime);
    }

    public static void clearRuntimeState() {
        ENABLED_PLAYERS.clear();
    }

    private static DebugPreviewSummary refreshNow(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return new DebugPreviewSummary(false, 0, 0, 0, DEFAULT_RADIUS);
        }
        DebugPreviewState state = ENABLED_PLAYERS.get(player.getUUID());
        if (state == null) {
            return new DebugPreviewSummary(false, 0, 0, 0, DEFAULT_RADIUS);
        }
        return refresh(player, state, level.getGameTime());
    }

    private static DebugPreviewSummary refresh(ServerPlayer player, DebugPreviewState state, long gameTime) {
        ServerLevel level = player.serverLevel();
        List<Villager> villagers = nearbyHiredVillagers(level, player, state.radius());
        List<HiredDebugPreviewSyncPayload.WorkAreaEntry> workAreas = new ArrayList<>();
        List<HiredDebugPreviewSyncPayload.StorageEntry> storage = new ArrayList<>();
        Set<StorageDebugKey> seenStorage = new LinkedHashSet<>();
        ResourceLocation currentDimension = level.dimension().location();
        for (Villager villager : villagers) {
            String ownerName = VillagerPresetNameRegistry.resolveDisplayName(villager).getString();
            HiredVillagerRole role = HiredVillagerContractService.activeRole(level, villager);
            HiredWorkArea area = HiredVillagerWorkService.workArea(level, villager);
            if (area.usable() && workAreas.size() < HiredDebugPreviewSyncPayload.MAX_WORK_AREAS) {
                workAreas.add(new HiredDebugPreviewSyncPayload.WorkAreaEntry(
                        currentDimension,
                        area.min(),
                        area.max(),
                        area.center(),
                        true,
                        area.min(),
                        false,
                        area.max(),
                        false,
                        ownerName,
                        role.label()
                ));
            }
            addStorageEntries(storage, seenStorage, ownerName, AssignedStorageService.allAssignedStorage(level, villager));
        }
        PacketDistributor.sendToPlayer(player, new HiredDebugPreviewSyncPayload(true, workAreas, storage, VISIBLE_TICKS));
        ENABLED_PLAYERS.put(player.getUUID(), new DebugPreviewState(state.radius(), gameTime + REFRESH_TICKS));
        return new DebugPreviewSummary(true, villagers.size(), workAreas.size(), storage.size(), state.radius());
    }

    private static List<Villager> nearbyHiredVillagers(ServerLevel level, ServerPlayer player, double radius) {
        AABB bounds = AABB.ofSize(player.position(), radius * 2.0D, radius * 2.0D, radius * 2.0D);
        return level.getEntitiesOfClass(Villager.class, bounds, villager ->
                villager.isAlive() && HiredVillagerContractService.isHired(level, villager));
    }

    private static void addStorageEntries(
            List<HiredDebugPreviewSyncPayload.StorageEntry> entries,
            Set<StorageDebugKey> seen,
            String ownerName,
            List<AssignedContainerRecord> records) {
        for (AssignedContainerRecord record : records) {
            if (entries.size() >= HiredDebugPreviewSyncPayload.MAX_STORAGE) {
                return;
            }
            boolean payment = AssignedStorageService.PAYMENT_PURPOSE.equals(record.purpose());
            StorageDebugKey key = new StorageDebugKey(record.dimension().location(), record.pos(), payment);
            if (seen.add(key)) {
                entries.add(new HiredDebugPreviewSyncPayload.StorageEntry(
                        record.dimension().location(),
                        record.pos(),
                        payment,
                        ownerName,
                        storagePurposeLabel(record.purpose())
                ));
            }
        }
    }

    private static String storagePurposeLabel(String purpose) {
        String normalized = AssignedStorageService.normalizePurpose(purpose);
        if (AssignedStorageService.PAYMENT_PURPOSE.equals(normalized)) {
            return "Payment";
        }
        if (AssignedStorageService.TOOL_PURPOSE.equals(normalized)) {
            return "Tool";
        }
        if (AssignedStorageService.INPUT_PURPOSE.equals(normalized)) {
            return "Input";
        }
        if (AssignedStorageService.OUTPUT_PURPOSE.equals(normalized)) {
            return "Output";
        }
        return "Global";
    }

    private static double sanitizeRadius(double radius) {
        return Math.clamp(radius, 1.0D, MAX_RADIUS);
    }

    public record DebugPreviewSummary(boolean enabled, int villagers, int workAreas, int storage, double radius) {
    }

    private record DebugPreviewState(double radius, long nextRefreshGameTime) {
    }

    private record StorageDebugKey(ResourceLocation dimension, BlockPos pos, boolean payment) {
    }
}
