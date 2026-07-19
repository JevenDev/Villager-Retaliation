package com.jvn.villagerretaliation.debug;

import com.jvn.villagerretaliation.interaction.HiredVillagerContractService;
import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.HiredVillagerWorkService;
import com.jvn.villagerretaliation.interaction.HiredRoute;
import com.jvn.villagerretaliation.interaction.HiredWorkArea;
import com.jvn.villagerretaliation.inventory.AssignedStorageSavedData.AssignedContainerRecord;
import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import com.jvn.villagerretaliation.item.VillagerRetaliationItems;
import com.jvn.villagerretaliation.network.ClipboardRouteEntry;
import com.jvn.villagerretaliation.network.HiredDebugPreviewSyncPayload;
import com.jvn.villagerretaliation.network.HiredHitboxDebugPreviewPayload;
import com.jvn.villagerretaliation.network.ServerboundRequestLimiter;
import com.jvn.villagerretaliation.villager.VillagerPresetNameRegistry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
import net.minecraft.world.item.ItemStack;
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
        DebugPreviewState state = ENABLED_PLAYERS.get(playerId);
        if (state != null && state.commandEnabled()) {
            return applyState(player, state.withCommand(false, sanitizeRadius(radius)).refreshNow(), sanitizeRadius(radius));
        }
        DebugPreviewState updated = state == null
                ? new DebugPreviewState(sanitizeRadius(radius), 0L, true, false, false)
                : state.withCommand(true, sanitizeRadius(radius)).refreshNow();
        return applyState(player, updated, sanitizeRadius(radius));
    }

    public static DebugPreviewSummary setEnabled(ServerPlayer player, boolean enabled, double radius) {
        UUID playerId = player.getUUID();
        DebugPreviewState state = ENABLED_PLAYERS.get(playerId);
        DebugPreviewState updated = state == null
                ? new DebugPreviewState(sanitizeRadius(radius), 0L, enabled, false, false)
                : state.withCommand(enabled, sanitizeRadius(radius)).refreshNow();
        return applyState(player, updated, sanitizeRadius(radius));
    }

    public static DebugPreviewSummary setClipboardPreviewEnabled(ServerPlayer player, boolean enabled) {
        if (!enabled) {
            DebugPreviewState state = ENABLED_PLAYERS.get(player.getUUID());
            if (state == null) {
                return new DebugPreviewSummary(false, 0, 0, 0, DEFAULT_RADIUS);
            }
            return applyState(player, state.withClipboard(false).refreshNow(), DEFAULT_RADIUS);
        }
        if (!hasHeldClipboard(player)) {
            DebugPreviewState state = ENABLED_PLAYERS.get(player.getUUID());
            if (state != null) {
                return applyState(player, state.withClipboard(false).refreshNow(), DEFAULT_RADIUS);
            }
            return new DebugPreviewSummary(false, 0, 0, 0, DEFAULT_RADIUS);
        }
        DebugPreviewState state = ENABLED_PLAYERS.get(player.getUUID());
        if (state != null
                && state.clipboardEnabled()
                && player.level() instanceof ServerLevel level
                && level.getGameTime() < state.nextRefreshGameTime()) {
            return new DebugPreviewSummary(true, 0, 0, 0, state.radius());
        }
        DebugPreviewState updated = state == null
                ? new DebugPreviewState(DEFAULT_RADIUS, 0L, false, true, false)
                : state.withClipboard(true).refreshNow();
        return applyState(player, updated, DEFAULT_RADIUS);
    }

    public static DebugPreviewSummary setHitboxDebugPreviewEnabled(ServerPlayer player, boolean enabled) {
        UUID playerId = player.getUUID();
        DebugPreviewState state = ENABLED_PLAYERS.get(playerId);
        if (enabled && !canUsePrivilegedDebugPreview(player)) {
            if (state != null && state.hitboxDebugEnabled()) {
                return applyState(player, state.withHitboxDebug(false).refreshNow(), DEFAULT_RADIUS);
            }
            return new DebugPreviewSummary(state != null && state.active(), 0, 0, 0,
                    state == null ? DEFAULT_RADIUS : state.radius());
        }
        if (enabled && !ServerboundRequestLimiter.tryAcquire(
                player, HiredHitboxDebugPreviewPayload.TYPE.id(), 20L)) {
            return new DebugPreviewSummary(state != null && state.active(), 0, 0, 0,
                    state == null ? DEFAULT_RADIUS : state.radius());
        }
        if (state != null
                && enabled
                && state.hitboxDebugEnabled()
                && player.level() instanceof ServerLevel level
                && level.getGameTime() < state.nextRefreshGameTime()) {
            return new DebugPreviewSummary(true, 0, 0, 0, state.radius());
        }
        DebugPreviewState updated = state == null
                ? new DebugPreviewState(DEFAULT_RADIUS, 0L, false, false, enabled)
                : state.withHitboxDebug(enabled).refreshNow();
        return applyState(player, updated, DEFAULT_RADIUS);
    }

    public static void onPlayerTick(ServerPlayer player) {
        DebugPreviewState state = ENABLED_PLAYERS.get(player.getUUID());
        if (state == null || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        if ((state.commandEnabled() || state.hitboxDebugEnabled()) && !canUsePrivilegedDebugPreview(player)) {
            state = state.withCommand(false, DEFAULT_RADIUS).withHitboxDebug(false).refreshNow();
            if (!state.active()) {
                ENABLED_PLAYERS.remove(player.getUUID());
                PacketDistributor.sendToPlayer(player, HiredDebugPreviewSyncPayload.disabled());
                return;
            }
            ENABLED_PLAYERS.put(player.getUUID(), state);
        }
        if (state.clipboardEnabled() && !hasHeldClipboard(player)) {
            state = state.withClipboard(false).refreshNow();
            if (!state.active()) {
                ENABLED_PLAYERS.remove(player.getUUID());
                PacketDistributor.sendToPlayer(player, HiredDebugPreviewSyncPayload.disabled());
                return;
            }
            ENABLED_PLAYERS.put(player.getUUID(), state);
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

    public static void clearRuntimeState(ServerPlayer player) {
        if (player != null) {
            ENABLED_PLAYERS.remove(player.getUUID());
        }
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
        List<HiredDebugPreviewSyncPayload.WorkAreaEntry> workAreaAssignments = new ArrayList<>();
        List<NamedStorageAssignments> storageAssignments = new ArrayList<>();
        List<ClipboardRouteEntry> routes = new ArrayList<>();
        ResourceLocation currentDimension = level.dimension().location();
        for (Villager villager : villagers) {
            String ownerName = VillagerPresetNameRegistry.resolveDisplayName(villager).getString();
            HiredVillagerRole role = HiredVillagerContractService.activeRole(level, villager);
            HiredWorkArea area = HiredVillagerWorkService.workArea(level, villager);
            if (area.usable()) {
                workAreaAssignments.add(new HiredDebugPreviewSyncPayload.WorkAreaEntry(
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
            HiredRoute route = HiredVillagerWorkService.route(level, villager);
            if (!route.isEmpty() && routes.size() < HiredDebugPreviewSyncPayload.MAX_ROUTES) {
                routes.add(new ClipboardRouteEntry(currentDimension, route.nodes(), route.loop(), ownerName, role.label()));
            }
            storageAssignments.add(new NamedStorageAssignments(
                    ownerName,
                    AssignedStorageService.allAssignedStorage(level, villager)));
        }
        List<HiredDebugPreviewSyncPayload.WorkAreaEntry> workAreas = workAreaEntries(workAreaAssignments);
        List<HiredDebugPreviewSyncPayload.StorageEntry> storage = storageEntries(storageAssignments);
        PacketDistributor.sendToPlayer(player, new HiredDebugPreviewSyncPayload(true, workAreas, storage, routes, VISIBLE_TICKS));
        ENABLED_PLAYERS.put(player.getUUID(), new DebugPreviewState(
                state.radius(),
                gameTime + REFRESH_TICKS,
                state.commandEnabled(),
                state.clipboardEnabled(),
                state.hitboxDebugEnabled()));
        return new DebugPreviewSummary(true, villagers.size(), workAreas.size(), storage.size(), state.radius());
    }

    private static DebugPreviewSummary applyState(ServerPlayer player, DebugPreviewState state, double fallbackRadius) {
        if (!state.active()) {
            ENABLED_PLAYERS.remove(player.getUUID());
            PacketDistributor.sendToPlayer(player, HiredDebugPreviewSyncPayload.disabled());
            return new DebugPreviewSummary(false, 0, 0, 0, fallbackRadius);
        }
        ENABLED_PLAYERS.put(player.getUUID(), state);
        return refreshNow(player);
    }

    private static List<Villager> nearbyHiredVillagers(ServerLevel level, ServerPlayer player, double radius) {
        AABB bounds = AABB.ofSize(player.position(), radius * 2.0D, radius * 2.0D, radius * 2.0D);
        return level.getEntitiesOfClass(Villager.class, bounds, villager ->
                villager.isAlive() && HiredVillagerContractService.isHired(level, villager));
    }

    private static boolean hasHeldClipboard(ServerPlayer player) {
        ItemStack mainHand = player.getMainHandItem();
        return VillagerRetaliationItems.isClipboard(mainHand)
                || VillagerRetaliationItems.isClipboard(player.getOffhandItem());
    }

    private static boolean canUsePrivilegedDebugPreview(ServerPlayer player) {
        return player.createCommandSourceStack().hasPermission(2);
    }

    private static void addStorageEntries(
            Map<StorageDebugKey, StorageDebugEntry> entries,
            String ownerName,
            List<AssignedContainerRecord> records) {
        for (AssignedContainerRecord record : records) {
            String purpose = AssignedStorageService.normalizePurpose(record.purpose());
            boolean payment = AssignedStorageService.PAYMENT_PURPOSE.equals(purpose);
            StorageDebugKey key = new StorageDebugKey(record.dimension().location(), record.pos(), purpose);
            StorageDebugEntry existing = entries.get(key);
            if (existing != null) {
                existing.addOwner(ownerName);
            } else if (entries.size() < HiredDebugPreviewSyncPayload.MAX_STORAGE) {
                entries.put(key, new StorageDebugEntry(
                        record.dimension().location(),
                        record.pos(),
                        payment,
                        ownerName,
                        storagePurposeLabel(record.purpose())));
            }
        }
    }

    static List<HiredDebugPreviewSyncPayload.WorkAreaEntry> workAreaEntries(
            List<HiredDebugPreviewSyncPayload.WorkAreaEntry> assignments) {
        Map<WorkAreaDebugKey, WorkAreaDebugEntry> entries = new LinkedHashMap<>();
        if (assignments != null) {
            for (HiredDebugPreviewSyncPayload.WorkAreaEntry assignment : assignments) {
                if (assignment == null) {
                    continue;
                }
                WorkAreaDebugKey key = new WorkAreaDebugKey(
                        assignment.dimension(),
                        assignment.min(),
                        assignment.max(),
                        assignment.center(),
                        assignment.showCenter(),
                        assignment.firstCorner(),
                        assignment.showFirstCorner(),
                        assignment.secondCorner(),
                        assignment.showSecondCorner(),
                        assignment.jobName());
                WorkAreaDebugEntry existing = entries.get(key);
                if (existing != null) {
                    existing.addOwner(assignment.ownerName());
                } else if (entries.size() < HiredDebugPreviewSyncPayload.MAX_WORK_AREAS) {
                    entries.put(key, new WorkAreaDebugEntry(assignment));
                }
            }
        }
        return entries.values().stream().map(WorkAreaDebugEntry::toPayload).toList();
    }

    static List<HiredDebugPreviewSyncPayload.StorageEntry> storageEntries(
            List<NamedStorageAssignments> assignments) {
        Map<StorageDebugKey, StorageDebugEntry> entries = new LinkedHashMap<>();
        if (assignments != null) {
            for (NamedStorageAssignments assignment : assignments) {
                if (assignment != null) {
                    addStorageEntries(entries, assignment.ownerName(), assignment.records());
                }
            }
        }
        return entries.values().stream().map(StorageDebugEntry::toPayload).toList();
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

    private record DebugPreviewState(
            double radius,
            long nextRefreshGameTime,
            boolean commandEnabled,
            boolean clipboardEnabled,
            boolean hitboxDebugEnabled) {
        private boolean active() {
            return this.commandEnabled || this.clipboardEnabled || this.hitboxDebugEnabled;
        }

        private DebugPreviewState refreshNow() {
            return new DebugPreviewState(this.radius, 0L, this.commandEnabled, this.clipboardEnabled, this.hitboxDebugEnabled);
        }

        private DebugPreviewState withCommand(boolean enabled, double radius) {
            double nextRadius = enabled ? radius : (this.clipboardEnabled || this.hitboxDebugEnabled ? DEFAULT_RADIUS : radius);
            return new DebugPreviewState(nextRadius, this.nextRefreshGameTime, enabled, this.clipboardEnabled, this.hitboxDebugEnabled);
        }

        private DebugPreviewState withClipboard(boolean enabled) {
            return new DebugPreviewState(this.radius, this.nextRefreshGameTime, this.commandEnabled, enabled, this.hitboxDebugEnabled);
        }

        private DebugPreviewState withHitboxDebug(boolean enabled) {
            return new DebugPreviewState(this.radius, this.nextRefreshGameTime, this.commandEnabled, this.clipboardEnabled, enabled);
        }
    }

    private record StorageDebugKey(ResourceLocation dimension, BlockPos pos, String purpose) {
    }

    private record WorkAreaDebugKey(
            ResourceLocation dimension,
            BlockPos min,
            BlockPos max,
            BlockPos center,
            boolean showCenter,
            BlockPos firstCorner,
            boolean showFirstCorner,
            BlockPos secondCorner,
            boolean showSecondCorner,
            String jobName) {
    }

    record NamedStorageAssignments(String ownerName, List<AssignedContainerRecord> records) {
        NamedStorageAssignments {
            ownerName = ownerName == null ? "" : ownerName;
            records = records == null ? List.of() : List.copyOf(records);
        }
    }

    private static final class WorkAreaDebugEntry {
        private final HiredDebugPreviewSyncPayload.WorkAreaEntry assignment;
        private final Set<String> ownerNames = new LinkedHashSet<>();

        private WorkAreaDebugEntry(HiredDebugPreviewSyncPayload.WorkAreaEntry assignment) {
            this.assignment = assignment;
            addOwner(assignment.ownerName());
        }

        private void addOwner(String ownerName) {
            if (ownerName != null && !ownerName.isBlank()) {
                this.ownerNames.add(ownerName);
            }
        }

        private HiredDebugPreviewSyncPayload.WorkAreaEntry toPayload() {
            return new HiredDebugPreviewSyncPayload.WorkAreaEntry(
                    this.assignment.dimension(),
                    this.assignment.min(),
                    this.assignment.max(),
                    this.assignment.center(),
                    this.assignment.showCenter(),
                    this.assignment.firstCorner(),
                    this.assignment.showFirstCorner(),
                    this.assignment.secondCorner(),
                    this.assignment.showSecondCorner(),
                    String.join(", ", this.ownerNames),
                    this.assignment.jobName());
        }
    }

    private static final class StorageDebugEntry {
        private final ResourceLocation dimension;
        private final BlockPos pos;
        private final boolean payment;
        private final String storageType;
        private final Set<String> ownerNames = new LinkedHashSet<>();

        private StorageDebugEntry(
                ResourceLocation dimension,
                BlockPos pos,
                boolean payment,
                String ownerName,
                String storageType) {
            this.dimension = dimension;
            this.pos = pos;
            this.payment = payment;
            this.storageType = storageType;
            addOwner(ownerName);
        }

        private void addOwner(String ownerName) {
            if (ownerName != null && !ownerName.isBlank()) {
                this.ownerNames.add(ownerName);
            }
        }

        private HiredDebugPreviewSyncPayload.StorageEntry toPayload() {
            return new HiredDebugPreviewSyncPayload.StorageEntry(
                    this.dimension,
                    this.pos,
                    this.payment,
                    String.join(", ", this.ownerNames),
                    this.storageType);
        }
    }
}
