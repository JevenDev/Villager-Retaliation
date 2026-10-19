package com.jvn.villagerretaliation.progress;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import com.jvn.villagerretaliation.progress.ExplorationHistorySavedData.SettlementDestination;
import com.jvn.villagerretaliation.progress.ExplorationHistorySavedData.StructureDestination;
import com.jvn.villagerretaliation.settlement.SettlementSavedData;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.StructureStart;

public final class ExplorationHistoryService {
    private static final Map<UUID, Observation> observations = new HashMap<>();
    private ExplorationHistoryService() { }

    public static void tick(ServerPlayer player) {
        if (!player.isAlive()) return;
        observeInventory(player);
        var level = player.serverLevel();
        var dimension = level.dimension().location();
        var position = player.blockPosition();
        long now = level.getServer().overworld().getGameTime();
        var previous = observations.get(player.getUUID());
        if (previous != null && previous.dimension.equals(dimension) && previous.position.equals(position) && now < previous.nextScan) return;
        var chunk = level.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4);
        if (chunk == null) return;
        var structures = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        var candidates = new HashMap<StructureDestination, StructureStart>();
        for (var entry : chunk.getAllStarts().entrySet()) {
            var id = structures.getKey(entry.getKey());
            if (id != null && entry.getValue().isValid()) candidates.put(
                    new StructureDestination(dimension, id, entry.getValue().getChunkPos().toLong()), entry.getValue());
        }
        var unresolved = new HashSet<StructureDestination>();
        for (var entry : chunk.getAllReferences().entrySet()) {
            var id = structures.getKey(entry.getKey());
            if (id == null) continue;
            for (long reference : entry.getValue()) {
                var startPosition = new ChunkPos(reference);
                var startChunk = level.getChunkSource().getChunkNow(startPosition.x, startPosition.z);
                if (startChunk == null) {
                    unresolved.add(new StructureDestination(dimension, id, reference));
                    continue;
                }
                var start = startChunk.getStartForStructure(entry.getKey());
                if (start != null && start.isValid()) candidates.put(new StructureDestination(dimension, id, reference), start);
            }
        }
        var present = new HashSet<StructureDestination>();
        // bounding boxes include open courtyards and gaps between village buildings
        candidates.forEach((destination, start) -> { if (start.getBoundingBox().isInside(position)) present.add(destination); });
        var data = ExplorationHistorySavedData.get(level);
        for (var entered : data.observeStructures(player.getUUID(), dimension, present, unresolved, now))
            ProgressHistoryService.playerEvent(player, "structure_visited", entered.structure());
        var town = SettlementSavedData.get(level).at(dimension, position).filter(record -> record.contains(position)).orElse(null);
        var destination = town == null ? null : new SettlementDestination(dimension, town.id().value());
        if (data.observeSettlement(level, player.getUUID(), destination, now) && town != null)
            EventHistorySavedData.get(level).record(level, player.getUUID(), null, new VillageAllegianceId(destination.settlement()),
                    ProgressHistoryService.event("settlement_visited"), "", 1, 1);
        observations.put(player.getUUID(), new Observation(dimension, position.immutable(), now + 20));
    }

    public static void pickedUp(net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent.Post event) {
        if (event.getPlayer() instanceof ServerPlayer player) {
            var original = event.getOriginalStack();
            if (original.getCount() > event.getCurrentStack().getCount()) observeItem(player, original);
        }
    }

    public static void observeInventory(ServerPlayer player) {
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) observeItem(player, inventory.getItem(slot));
        if (player.containerMenu != null) observeItem(player, player.containerMenu.getCarried());
    }

    public static void observeItem(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) return;
        var level = player.serverLevel();
        var item = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (ExplorationHistorySavedData.get(level).possess(player.getUUID(), item, level.getServer().overworld().getGameTime()))
            ProgressHistoryService.playerEvent(player, "item_discovered", item);
    }

    public static void clear(UUID player) { observations.remove(player); }
    public static void clear() { observations.clear(); }
    private record Observation(ResourceLocation dimension, BlockPos position, long nextScan) { }
}
