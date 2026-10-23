package com.jvn.villagerretaliation.authoring.preview;

import com.jvn.villagerretaliation.quest.VillagerQuestResources;
import com.jvn.villagerretaliation.quest.VillagerQuestSavedData;
import com.jvn.villagerretaliation.quest.tracking.VillagerQuestTargets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = "villagerretaliation")
public final class QuestPlaytestStructureWork {
    private static final Map<MinecraftServer, Map<UUID, Work>> PENDING = new HashMap<>();
    private static final TicketType<ChunkPos> LOAD = TicketType.create("villagerretaliation_playtest",
            java.util.Comparator.comparingLong(ChunkPos::toLong), 40);

    private QuestPlaytestStructureWork() { }

    static boolean pending(ServerPlayer player) {
        return PENDING.getOrDefault(player.server, Map.of()).containsKey(player.getUUID());
    }

    static String start(ServerPlayer player, ResourceLocation quest, ServerLevel level, ChunkPos origin,
            StructureStart start, net.minecraft.world.level.levelgen.structure.StructurePiece destination, List<ChunkPos> chunks, VillagerQuestTargets.LocatedTarget target) {
        var work = new Work(player, quest, level, origin, start, destination, chunks, target);
        PENDING.computeIfAbsent(player.server, ignored -> new HashMap<>()).put(player.getUUID(), work);
        return "Preparing structure in " + chunks.size() + " chunks. Progress appears in the test log.";
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        var pending = PENDING.get(event.getServer());
        if (pending == null) return;
        var iterator = pending.values().iterator();
        while (iterator.hasNext()) {
            var work = iterator.next();
            boolean finished;
            try { finished = work.step(); }
            catch (IllegalStateException | IllegalArgumentException | java.util.concurrent.CompletionException exception) {
                work.controls.log("Structure preparation stopped: " + exception.getMessage());
                finished = true;
            }
            if (finished) {
                work.release();
                iterator.remove();
            }
        }
        if (pending.isEmpty()) PENDING.remove(event.getServer());
    }

    @SubscribeEvent
    public static void stopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) {
        PENDING.remove(event.getServer());
    }

    private static final class Work {
        private final ServerPlayer player;
        private final ResourceLocation quest;
        private final ServerLevel level;
        private final ChunkPos origin;
        private final StructureStart start;
        private final List<ChunkPos> chunks;
        private final List<ChunkPos> loads;
        private final VillagerQuestTargets.LocatedTarget target;
        private final net.minecraft.world.level.levelgen.structure.StructurePiece destination;
        private final QuestPlaytestSession.Controls controls;
        private final com.jvn.villagerretaliation.quest.QuestDefinition definition;
        private final List<CompletableFuture<? extends ChunkResult<?>>> futures = new ArrayList<>();
        private int placed, ticks;
        private boolean checked;

        private Work(ServerPlayer player, ResourceLocation quest, ServerLevel level, ChunkPos origin,
                StructureStart start, net.minecraft.world.level.levelgen.structure.StructurePiece destination, List<ChunkPos> chunks, VillagerQuestTargets.LocatedTarget target) {
            this.player = player;
            this.quest = quest;
            this.level = level;
            this.origin = origin;
            this.start = start;
            this.destination = destination;
            this.chunks = List.copyOf(chunks);
            this.loads = new ArrayList<>(chunks);
            if (!loads.contains(origin)) loads.add(origin);
            this.target = target;
            this.controls = QuestPlaytestSession.require(player, quest);
            this.definition = VillagerQuestResources.quest(player.server, quest).orElseThrow();
        }

        private boolean step() {
            if (player.isRemoved() || QuestPlaytestSession.controls(player, quest) != controls) return true;
            if (QuestPlaytestSession.suspended(player, quest)) return false;
            if (VillagerQuestResources.quest(player.server, quest).orElse(null) != definition)
                throw new IllegalStateException("Quest content changed. Retry preparation with the applied draft.");
            if (++ticks > 1200) throw new IllegalStateException("Chunk loading timed out. Retry in a loaded area.");
            for (var chunk : loads) level.getChunkSource().addRegionTicket(LOAD, chunk, 0, chunk);
            if (futures.size() < loads.size()) {
                var chunk = loads.get(futures.size());
                futures.add(level.getChunkSource().getChunkFuture(chunk.x, chunk.z, ChunkStatus.FULL, true));
                return false;
            }
            if (futures.stream().anyMatch(future -> !future.isDone())) return false;
            if (futures.stream().anyMatch(future -> !future.join().isSuccess()))
                throw new IllegalStateException("A structure chunk could not be loaded");
            var bounds = start.getBoundingBox();
            if (!checked) {
                for (var chunk : chunks) {
                    var loaded = level.getChunk(chunk.x, chunk.z);
                    if (!loaded.getBlockEntities().isEmpty() || loaded.getAllStarts().values().stream()
                            .anyMatch(existing -> existing.isValid() && existing.getBoundingBox().intersects(bounds)))
                        throw new IllegalStateException("The area contains a structure or block entity. Move to a clear area.");
                }
                checked = true;
            }
            for (var entity : level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
                    net.minecraft.world.phys.AABB.of(bounds).inflate(16))) {
                if (entity instanceof net.minecraft.world.entity.player.Player || entity instanceof net.minecraft.world.entity.npc.Villager)
                    throw new IllegalStateException("The area contains a player or villager. Move to a clear area.");
            }
            var chunk = chunks.get(placed);
            start.placeInChunk(level, level.structureManager(), level.getChunkSource().getGenerator(), level.getRandom(),
                    new BoundingBox(chunk.getMinBlockX(), level.getMinBuildHeight(), chunk.getMinBlockZ(),
                            chunk.getMaxBlockX(), level.getMaxBuildHeight(), chunk.getMaxBlockZ()), chunk);
            placed++;
            if (placed % 4 == 0) controls.log("Preparing structure: " + placed + " / " + chunks.size() + " chunks");
            if (placed < chunks.size()) return false;
            // visit checks require the structure start and references after placement finishes
            level.getChunk(origin.x, origin.z).setStartForStructure(start.getStructure(), start);
            for (var position : chunks) level.getChunk(position.x, position.z).addReferenceForStructure(start.getStructure(), origin.toLong());
            var target = new VillagerQuestTargets.LocatedTarget(level.dimension(), destination.getBoundingBox().getCenter(), this.target.objectiveId());
            controls.target(target);
            var data = VillagerQuestSavedData.get(player.serverLevel());
            var progress = data.get(player.getUUID(), quest);
            if (progress != null && progress.state() == VillagerQuestSavedData.QuestState.ACTIVE
                    && (target.objectiveId().isBlank() || target.objectiveId().equals(progress.targetObjectiveId()))) {
                progress.setTarget(progress.startedVillagerId(), target.dimension(), target.pos(), target.objectiveId());
                data.setDirty();
                com.jvn.villagerretaliation.quest.VillagerQuestService.refreshTracker(player);
            }
            controls.log("Structure ready at " + target.pos().toShortString() + ". Travel to it to test the objective.");
            return true;
        }

        private void release() {
            for (var chunk : loads) level.getChunkSource().removeRegionTicket(LOAD, chunk, 0, chunk);
        }
    }
}
