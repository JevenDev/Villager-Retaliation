package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.mixin.EntityStorageAccessor;
import com.jvn.villagerretaliation.mixin.PersistentEntitySectionManagerAccessor;
import com.jvn.villagerretaliation.mixin.ServerLevelEntityManagerAccessor;
import java.util.concurrent.CompletableFuture;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.EntityStorage;

final class SettlementCourierCheckpoint {
    private SettlementCourierCheckpoint() { }
    static CompletableFuture<Void> persist(ServerLevel level, Entity entity) {
        if (!level.getServer().isSameThread() || entity.level() != level || entity.isRemoved())
            return failed("Courier checkpoint requires a live entity on the server thread");
        var root = entity.getRootVehicle(); var position = new ChunkPos(root.blockPosition());
        if (level.getChunkSource().getChunkNow(position.x, position.z) == null || !level.areEntitiesLoaded(position.toLong()))
            return failed("Courier entity chunk is unavailable");
        var manager = ((ServerLevelEntityManagerAccessor) level).villagerretaliation$entityManager();
        var access = (PersistentEntitySectionManagerAccessor) manager;
        if (!(access.villagerretaliation$permanentStorage() instanceof EntityStorage storage))
            return failed("Unsupported courier entity storage");
        var roots = access.villagerretaliation$sectionStorage().getExistingSectionsInChunk(position.toLong())
                .flatMap(section -> section.getEntities()).filter(Entity::shouldBeSaved).limit(513).toList();
        if (roots.size() > 512 || roots.stream().noneMatch(candidate -> candidate == root))
            return failed("Courier checkpoint exceeds its entity limit or is absent from storage");
        var entries = new ListTag();
        for (var candidate : roots) {
            var entry = new CompoundTag();
            try {
                if (!candidate.save(entry) || !entry.hasUUID("UUID") || !candidate.getUUID().equals(entry.getUUID("UUID")))
                    return failed("Courier checkpoint omitted an entity identity");
            } catch (net.minecraft.ReportedException failure) { return CompletableFuture.failedFuture(failure); }
            if (candidate == root && !contains(entry, entity.getUUID())) return failed("Courier passenger was omitted from storage");
            entries.add(entry);
        }
        var snapshot = NbtUtils.addCurrentDataVersion(new CompoundTag());
        snapshot.putIntArray("Position", new int[]{position.x, position.z}); snapshot.put("Entities", entries);
        var storageAccess = (EntityStorageAccessor) storage;
        var region = storageAccess.villagerretaliation$regionStorage();
        storageAccess.villagerretaliation$emptyChunks().remove(position.toLong());
        return region.write(position, snapshot).thenCompose(ignored -> region.synchronize(true));
    }
    private static boolean contains(CompoundTag tag, java.util.UUID id) {
        if (tag.hasUUID("UUID") && id.equals(tag.getUUID("UUID"))) return true;
        for (var passenger : tag.getList("Passengers", net.minecraft.nbt.Tag.TAG_COMPOUND))
            if (contains((CompoundTag) passenger, id)) return true;
        return false;
    }
    private static CompletableFuture<Void> failed(String reason) {
        return CompletableFuture.failedFuture(new IllegalStateException(reason));
    }
}
