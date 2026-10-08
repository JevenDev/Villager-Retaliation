package com.jvn.villagerretaliation.campaign;

import com.jvn.villagerretaliation.mixin.EntityStorageAccessor;
import com.jvn.villagerretaliation.mixin.PersistentEntitySectionManagerAccessor;
import com.jvn.villagerretaliation.mixin.ServerLevelEntityManagerAccessor;
import java.io.IOException;
import java.util.concurrent.CompletionException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.EntityStorage;

public final class CampaignEntityCheckpoint {
    private CampaignEntityCheckpoint() { }

    static void persist(ServerLevel level, Entity entity) throws IOException {
        persistTree(level, entity, snapshot -> true);
    }

    public static void persistTree(ServerLevel level, Entity entity, java.util.function.Predicate<CompoundTag> verify) throws IOException {
        if (!level.getServer().isSameThread() || entity.level() != level || entity.isRemoved() || entity.isPassenger()) {
            throw new IOException("Campaign spawn checkpoint requires a live, unmounted entity on the server thread.");
        }
        var position = new ChunkPos(entity.blockPosition());
        if (level.getChunkSource().getChunkNow(position.x, position.z) == null || !level.areEntitiesLoaded(position.toLong())) {
            throw new IOException("Campaign spawn chunk and its entities must finish loading before they can be saved.");
        }
        var manager = ((ServerLevelEntityManagerAccessor) level).villagerretaliation$entityManager();
        var access = (PersistentEntitySectionManagerAccessor) manager;
        if (!(access.villagerretaliation$permanentStorage() instanceof EntityStorage storage)) {
            throw new IOException("Campaign spawn checkpoint does not support this entity storage provider.");
        }
        var roots = access.villagerretaliation$sectionStorage().getExistingSectionsInChunk(position.toLong())
                .flatMap(section -> section.getEntities()).filter(Entity::shouldBeSaved).toList();
        if (roots.stream().noneMatch(candidate -> candidate == entity)) {
            throw new IOException("Campaign spawn is absent from the loaded entity chunk.");
        }
        var entries = new ListTag();
        for (var root : roots) {
            var entry = new CompoundTag();
            try {
                if (!root.save(entry) || !entry.hasUUID("UUID") || !entry.getUUID("UUID").equals(root.getUUID())) {
                    throw new IOException("Entity chunk serialization omitted the identity of " + root.getUUID() + ".");
                }
            } catch (net.minecraft.ReportedException failure) {
                throw new IOException("Entity chunk could not be serialized without losing another entity.", failure);
            }
            if (root == entity && !verify.test(entry)) throw new IOException("Native entity serialization omitted or changed required campaign actor data.");
            entries.add(entry);
        }
        var snapshot = NbtUtils.addCurrentDataVersion(new CompoundTag());
        snapshot.putIntArray("Position", new int[]{position.x, position.z});
        snapshot.put("Entities", entries);
        var storageAccess = (EntityStorageAccessor) storage;
        var region = storageAccess.villagerretaliation$regionStorage();
        try {
            region.write(position, snapshot).join();
            region.synchronize(true).join();
        } catch (CompletionException failure) {
            throw new IOException("Campaign spawn entity chunk could not be written and flushed.", failure.getCause());
        }
        storageAccess.villagerretaliation$emptyChunks().remove(position.toLong());
    }
}
