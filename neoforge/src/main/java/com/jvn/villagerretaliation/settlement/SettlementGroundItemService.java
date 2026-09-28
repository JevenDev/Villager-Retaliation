package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.work.HiredPathMemory;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;

public final class SettlementGroundItemService {
    private static final Map<ItemEntity, SettlementRecord> OWNERS = new WeakHashMap<>();
    private static final Map<SettlementArea, Integer> CURSORS = new WeakHashMap<>();
    private static final Map<SettlementGroundItem, Integer> WORKER_CURSORS = new WeakHashMap<>();

    private SettlementGroundItemService() {
    }

    static void observeArea(ServerLevel level, SettlementRecord town, SettlementArea area) {
        if (area.type().role() == null) return;
        var bounds = new AABB(area.min().getX(), area.min().getY(), area.min().getZ(),
                area.max().getX() + 1.0D, area.max().getY() + 1.0D, area.max().getZ() + 1.0D);
        var items = level.getEntitiesOfClass(ItemEntity.class, bounds, item -> item.isAlive() && !item.getItem().isEmpty());
        if (items.isEmpty()) return;
        int cursor = Math.floorMod(CURSORS.getOrDefault(area, 0), items.size());
        int count = Math.min(64, items.size());
        for (int index = 0; index < count; index++) observe(level, town, area, items.get((cursor + index) % items.size()));
        CURSORS.put(area, (cursor + count) % items.size());
    }

    static void observe(ServerLevel level, SettlementRecord town, SettlementArea area, ItemEntity item) {
        if (!town.dimension().equals(level.dimension().location()) || item.level() != level || !area.contains(item.blockPosition())) return;
        var previous = owner(level, item);
        if (previous != null && previous != town) return;
        if (!readReceipt(level, item) || item.getItem().isEmpty()) return;
        var state = town.groundItems().get(item.getUUID());
        if (state == null) {
            state = SettlementGroundItem.capture(item, area.id(), level.getGameTime());
            town.recordGroundItem(state);
        } else {
            state = synchronize(level, town, state);
            apply(level, town, state, item);
            if (item.isRemoved()) return;
            state.observe(item, level.isPositionEntityTicking(item.blockPosition()) ? level.getGameTime() : state.drop().checkedAt());
        }
        OWNERS.put(item, town);
        observeAccess(level, town, state, item);
        town.changed();
    }

    private static void observeAccess(ServerLevel level, SettlementRecord town, SettlementGroundItem state, ItemEntity item) {
        var workers = town.workers().values().stream().filter(worker -> {
            var area = town.workplace(worker.area());
            return area != null && area.contains(item.blockPosition()) && area.contains(worker.position());
        }).toList();
        if (workers.isEmpty()) return;
        int cursor = Math.floorMod(WORKER_CURSORS.getOrDefault(state, 0), workers.size());
        int count = Math.min(4, workers.size());
        for (int index = 0; index < count; index++) {
            var worker = workers.get((cursor + index) % workers.size());
            var area = town.workplace(worker.area());
            int steps = SettlementLivestockService.observedSteps(level, town, area, worker, item);
            if (steps != SettlementLivestockService.UNOBSERVED) state.drop().access(worker, steps);
        }
        WORKER_CURSORS.put(state, (cursor + count) % workers.size());
    }

    static void update(ServerLevel level, SettlementRecord town, long now) {
        for (var original : List.copyOf(town.groundItems().values())) {
            var state = synchronize(level, town, original);
            var entity = SettlementAnimalBirthService.existing(level, state.drop().id());
            if (entity instanceof ItemEntity item && item.level() == level) {
                if (!readReceipt(level, item)) {
                    town.removeGroundItem(item.getUUID());
                    continue;
                }
                apply(level, town, state, item);
                if (item.isRemoved()) continue;
                if (item.getItem().isEmpty()) {
                    if (!state.removed()) state.consume(ItemStack.EMPTY, level.registryAccess());
                    remove(level, town, state);
                    continue;
                }
                boolean active = level.isPositionEntityTicking(item.blockPosition());
                state.observe(item, active ? now : state.drop().checkedAt());
                if (!active) {
                    state.advance(level, now);
                    apply(level, town, state, item);
                }
                if (town.areas().values().stream().noneMatch(area -> area.contains(item.blockPosition()))) {
                    town.removeGroundItem(item.getUUID());
                    OWNERS.remove(item);
                }
            } else if (entity != null) {
                town.removeGroundItem(state.drop().id());
            } else if (level.areEntitiesLoaded(ChunkPos.asLong(state.nativePosition()))) {
                if (!state.removed()) state.consume(ItemStack.EMPTY, level.registryAccess());
                remove(level, town, state);
            } else {
                state.advance(level, now);
            }
            town.changed();
        }
    }

    public static boolean onActive(ItemEntity item) {
        if (!(item.level() instanceof ServerLevel level)) return true;
        var town = owner(level, item);
        if (town != null) {
            var state = synchronize(level, town, town.groundItems().get(item.getUUID()));
            apply(level, town, state, item);
        }
        return !item.isRemoved();
    }

    public static boolean onJoined(ItemEntity item) {
        return !(item.level() instanceof ServerLevel level) || readReceipt(level, item) && onActive(item);
    }

    public static void onUnloaded(ItemEntity item) {
        if (!(item.level() instanceof ServerLevel level)) return;
        var town = owner(level, item);
        OWNERS.remove(item);
        var reason = item.getRemovalReason();
        if (town == null && !item.getItem().isEmpty()
                && (reason == null || reason == Entity.RemovalReason.UNLOADED_TO_CHUNK)) {
            for (var candidate : SettlementSavedData.get(level).settlements()) {
                if (!candidate.dimension().equals(level.dimension().location())) continue;
                var area = candidate.areas().values().stream().filter(value -> value.type().role() != null
                        && value.contains(item.blockPosition())).findFirst().orElse(null);
                if (area == null) continue;
                town = candidate;
                town.recordGroundItem(SettlementGroundItem.capture(item, area.id(), level.getGameTime()));
                break;
            }
        }
        if (town == null) return;
        var state = synchronize(level, town, town.groundItems().get(item.getUUID()));
        if (reason == Entity.RemovalReason.CHANGED_DIMENSION) {
            town.removeGroundItem(item.getUUID());
        } else if (state.removed() || item.getItem().isEmpty()
                || reason != null && reason != Entity.RemovalReason.UNLOADED_TO_CHUNK) {
            if (!state.removed()) state.consume(ItemStack.EMPTY, level.registryAccess());
            remove(level, town, state);
        } else {
            apply(level, town, state, item);
            if (!state.removed()) {
                state.observe(item, level.getGameTime());
                observeAccess(level, town, state, item);
                town.changed();
            }
        }
    }

    private static SettlementRecord owner(ServerLevel level, ItemEntity item) {
        if (OWNERS.containsKey(item)) {
            var cached = OWNERS.get(item);
            if (cached == null || cached.groundItems().containsKey(item.getUUID())) return cached;
        }
        for (var town : SettlementSavedData.get(level).settlements()) {
            if (town.dimension().equals(level.dimension().location()) && town.groundItems().containsKey(item.getUUID())) {
                OWNERS.put(item, town);
                return town;
            }
        }
        OWNERS.put(item, null);
        return null;
    }

    private static LevelChunk chunk(ServerLevel level, BlockPos position) {
        return level.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4);
    }

    private static SettlementGroundItem synchronize(ServerLevel level, SettlementRecord town, SettlementGroundItem state) {
        var chunk = chunk(level, state.nativePosition());
        if (chunk == null) return state;
        var tag = SettlementChunkReceipts.groundItems(chunk, false).getCompound(state.drop().id().toString());
        if (tag.isEmpty() || tag.getLong("Version") < state.version()
                || tag.getLong("Version") == state.version() && !tag.getBoolean("Removed")) return state;
        var receipt = SettlementGroundItem.load(tag, level.registryAccess());
        if (!receipt.drop().id().equals(state.drop().id())) throw new IllegalStateException("Ground item receipt identity differs");
        town.removeGroundItem(state.drop().id());
        town.recordGroundItem(receipt);
        return receipt;
    }

    private static boolean readReceipt(ServerLevel level, ItemEntity item) {
        var chunk = chunk(level, item.blockPosition());
        if (chunk == null) return !item.isRemoved();
        var tag = SettlementChunkReceipts.groundItems(chunk, false).getCompound(item.getUUID().toString());
        if (!tag.isEmpty()) {
            var receipt = SettlementGroundItem.load(tag, level.registryAccess());
            receipt.restore(item);
        }
        return !item.isRemoved();
    }

    private static boolean writeReceipt(ServerLevel level, SettlementGroundItem state, BlockPos position) {
        var chunk = chunk(level, position);
        if (chunk == null) return false;
        var entries = SettlementChunkReceipts.groundItems(chunk, true);
        String key = state.drop().id().toString();
        if (entries.getCompound(key).getLong("Version") > state.version()) return false;
        entries.put(key, state.save());
        chunk.setUnsaved(true);
        return true;
    }

    private static void apply(ServerLevel level, SettlementRecord town, SettlementGroundItem state, ItemEntity item) {
        if (SettlementGroundItem.version(item) > state.version()
                || SettlementGroundItem.version(item) == state.version() && !state.removed()) return;
        writeReceipt(level, state, item.blockPosition());
        state.restore(item);
        if (state.removed()) remove(level, town, state);
        town.changed();
    }

    private static void remove(ServerLevel level, SettlementRecord town, SettlementGroundItem state) {
        if (writeReceipt(level, state, state.nativePosition())) town.removeGroundItem(state.drop().id());
        town.changed();
    }

    static boolean collect(ServerLevel level, SettlementRecord town, SettlementWorkerState worker, Villager owner,
                           HiredJobInventory inventory, SettlementWorkBudget budget, Predicate<ItemStack> filter) {
        var area = town.workplace(worker.area());
        if (area == null || !area.contains(worker.position())) return false;
        observeArea(level, town, area);
        boolean unreachable = false;
        for (var original : town.groundItems().values().stream().filter(state -> area.contains(state.drop().position()))
                .sorted(Comparator.comparingDouble(state -> state.drop().position().distSqr(worker.position()))).toList()) {
            var state = synchronize(level, town, original);
            var entity = SettlementAnimalBirthService.existing(level, state.drop().id());
            if (entity instanceof ItemEntity item && item.level() == level) {
                apply(level, town, state, item);
                if (item.isRemoved() || item.getItem().isEmpty()) continue;
                state.observe(item, level.isPositionEntityTicking(item.blockPosition()) ? level.getGameTime() : state.drop().checkedAt());
            } else if (entity != null || level.areEntitiesLoaded(ChunkPos.asLong(state.nativePosition()))) {
                continue;
            }
            if (state.removed()) continue;
            var drop = state.drop();
            var stack = drop.item(level.registryAccess());
            if (!filter.test(stack) || !area.contains(drop.position())
                    || HiredPathMemory.isAvoided(level, owner, drop.position())) continue;
            int steps = drop.steps(worker);
            if (steps < 0) steps = SettlementLivestockService.observedSteps(level, town, area, worker, drop.context(level));
            if (steps < 0) {
                unreachable = true;
                continue;
            }
            if (!SettlementItemDropService.ready(worker, inventory, budget, stack, steps)) return true;
            var remainder = inventory.insertOutput(stack.copy());
            owner.onItemPickup(entity instanceof ItemEntity item ? item : drop.context(level));
            state.consume(remainder, level.registryAccess());
            town.changed();
            if (entity instanceof ItemEntity item) apply(level, town, state, item);
            worker.status("collected_animal_drops");
            return true;
        }
        if (unreachable) worker.status("animal_drop_unreachable");
        return unreachable;
    }
}
