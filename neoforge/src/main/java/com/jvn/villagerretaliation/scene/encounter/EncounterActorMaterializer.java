package com.jvn.villagerretaliation.scene.encounter;

import com.jvn.villagerretaliation.campaign.CampaignEntityCheckpoint;
import com.jvn.villagerretaliation.mixin.ServerLevelEntityManagerAccessor;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;

final class EncounterActorMaterializer {
    private EncounterActorMaterializer() { }

    static Entity materialize(ServerLevel level, EncounterActorSpawn receipt) throws IOException {
        boolean born = EncounterActorBirths.born(level, receipt);
        var actors = new LinkedHashMap<UUID, Entity>();
        var fresh = new LinkedHashSet<UUID>();
        for (var node : receipt.nodes()) {
            Entity entity = find(level.getServer(), node.id());
            if (entity == null) {
                for (var parent : actors.values()) {
                    entity = parent.getSelfAndPassengers().filter(value -> value.getUUID().equals(node.id())).findFirst().orElse(null);
                    if (entity != null) break;
                }
            }
            if (entity == null) {
                if (!node.owned()) throw new IOException("Load the original external mount before resuming this actor. Existing entities cannot be recreated.");
                if (born) throw new IOException("This actor already has a native birth receipt. Load its saved entities or inspect their permanent loss.");
                for (var dimension : level.getServer().getAllLevels()) {
                    if (((ServerLevelEntityManagerAccessor) dimension).villagerretaliation$entityManager().isLoaded(node.id()))
                        throw new IOException("An actor identity is loaded but unavailable. It cannot be created again.");
                }
                if (!loaded(level, node.position())) throw new IOException("Load the original actor chunks and entities before resuming their saved spawn.");
                entity = EntityType.loadEntityRecursive(node.entity(), level, value -> value);
                if (entity == null) throw new IOException("The saved actor entity type is unavailable.");
                fresh.add(node.id());
            }
            if (entity.level() != level || entity.isRemoved() || !BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).equals(node.type())
                    || !entity.getUUID().equals(node.id()) || node.owned() && !EncounterActorSpawning.owns(entity.getPersistentData(), receipt))
                throw new IOException("The reserved actor identity has conflicting ownership or content. Existing entities were preserved.");
            if (!node.owned() && !entity.blockPosition().equals(node.position()))
                throw new IOException("The external mount moved after actor initialization. Restore its original binding or repair this encounter.");
            actors.put(node.id(), entity);
        }
        for (var node : receipt.nodes()) {
            var entity = actors.get(node.id());
            var parent = node.parent() == null ? null : actors.get(node.parent());
            if (entity.getVehicle() == parent) continue;
            if (!fresh.contains(node.id()) || parent == null)
                throw new IOException("A saved actor's mount relationship changed. Existing entities were preserved.");
            if (parent.getPassengers().stream().anyMatch(passenger -> !actors.containsKey(passenger.getUUID())))
                throw new IOException("An external mount has another rider. Existing passengers were preserved.");
            if (!entity.startRiding(parent, true)) throw new IOException("The saved actor could not join its original mount.");
            parent.positionRider(entity);
        }
        for (var node : receipt.nodes()) {
            var entity = actors.get(node.id());
            if (!node.owned() || level.getEntity(entity.getUUID()) == entity) continue;
            if (!safe(level, entity)) throw new IOException("The prepared actor is obstructed or its chunks are unloaded. Clear the obstruction or load the original area.");
            if (!level.addFreshEntity(entity)) throw new IOException("The prepared encounter actor could not enter the world. Its identity is retained.");
        }
        var roots = new LinkedHashSet<Entity>();
        for (var node : receipt.nodes()) {
            var entity = actors.get(node.id());
            if (node.owned() && (level.getEntity(node.id()) != entity || entity.isRemoved() || !EncounterActorSpawning.owns(entity.getPersistentData(), receipt)))
                throw new IOException("An actor's identity changed while it entered the world.");
            roots.add(entity.getRootVehicle());
        }
        for (var root : roots) {
            var expected = new LinkedHashSet<UUID>();
            root.getSelfAndPassengers().map(Entity::getUUID).forEach(expected::add);
            CampaignEntityCheckpoint.persistTree(level, root, snapshot -> verify(snapshot, expected, receipt));
        }
        EncounterActorBirths.commit(level, receipt);
        return actors.get(receipt.entity());
    }

    static boolean safe(ServerLevel level, Entity entity) {
        var box = entity.getBoundingBox();
        var min = BlockPos.containing(box.minX, box.minY, box.minZ);
        var max = BlockPos.containing(box.maxX, box.maxY, box.maxZ);
        if (level.isOutsideBuildHeight(min) || level.isOutsideBuildHeight(max)
                || !level.getWorldBorder().isWithinBounds(min) || !level.getWorldBorder().isWithinBounds(max)) return false;
        for (int x = min.getX() >> 4; x <= max.getX() >> 4; x++)
            for (int z = min.getZ() >> 4; z <= max.getZ() >> 4; z++)
                if (level.getChunkSource().getChunkNow(x, z) == null || !level.areEntitiesLoaded(ChunkPos.asLong(x, z))) return false;
        return level.noCollision(entity);
    }

    private static boolean loaded(ServerLevel level, BlockPos position) {
        var chunk = new ChunkPos(position);
        return level.getChunkSource().getChunkNow(chunk.x, chunk.z) != null && level.areEntitiesLoaded(chunk.toLong());
    }

    static Entity find(MinecraftServer server, UUID id) {
        for (var level : server.getAllLevels()) {
            var entity = level.getEntity(id);
            if (entity != null) return entity;
        }
        return null;
    }

    private static boolean verify(CompoundTag snapshot, Set<UUID> expected, EncounterActorSpawn receipt) {
        var saved = new LinkedHashMap<UUID, CompoundTag>();
        if (!collect(snapshot, saved) || !saved.keySet().equals(expected)) return false;
        for (var node : receipt.nodes()) {
            var entity = saved.get(node.id());
            if (entity != null && node.owned() && (!node.type().toString().equals(entity.getString("id"))
                    || !EncounterActorSpawning.owns(entity.getCompound("NeoForgeData"), receipt))) return false;
        }
        return true;
    }

    private static boolean collect(CompoundTag entity, Map<UUID, CompoundTag> saved) {
        if (!entity.hasUUID("UUID") || saved.size() >= 256 || saved.putIfAbsent(entity.getUUID("UUID"), entity) != null) return false;
        if (entity.contains("Passengers") && !entity.contains("Passengers", Tag.TAG_LIST)) return false;
        var passengers = entity.getList("Passengers", Tag.TAG_COMPOUND);
        for (var passenger : passengers) if (!collect((CompoundTag) passenger, saved)) return false;
        return true;
    }
}
