package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.util.AtomicDataFiles;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.common.IOUtilities;

final class SettlementAidCheckpoint {
    private static final Map<SettlementSavedData, Pending> PENDING = new WeakHashMap<>();
    private SettlementAidCheckpoint() { }
    static boolean durable(ServerLevel level, long revision) {
        var data = SettlementSavedData.get(level);
        if (data.readOnly() || !level.getServer().isSameThread() || revision > data.aid().revision()) return false;
        var aid = data.aid();
        if (aid.durableRevision >= revision) return true;
        var pending = PENDING.computeIfAbsent(data, ignored -> new Pending());
        long now = level.getServer().overworld().getGameTime();
        if (pending.future != null) {
            if (!pending.future.isDone()) return false;
            try { pending.future.join(); aid.durableRevision = Math.max(aid.durableRevision, pending.revision); }
            catch (CompletionException failure) {
                pending.retryAt = now + 200;
                LogUtils.getLogger().error("Food aid checkpoint failed, custody remains pending", failure.getCause());
            }
            pending.future = null;
            if (aid.durableRevision >= revision) return true;
        }
        if (now < pending.retryAt) return false;
        var snapshot = new CompoundTag(); snapshot.put("data", pending.snapshots.capture(data, level.registryAccess()));
        NbtUtils.addCurrentDataVersion(snapshot);
        var file = level.getServer().getWorldPath(LevelResource.ROOT).resolve("data").resolve("villagerretaliation_settlements.dat");
        pending.revision = aid.revision();
        var completion = new CompletableFuture<Void>(); pending.future = completion;
        IOUtilities.withIOWorker(() -> {
            try { AtomicDataFiles.writeCompressed(snapshot, file); completion.complete(null); }
            catch (IOException | RuntimeException failure) { completion.completeExceptionally(failure); }
        });
        return false;
    }
    static final class Snapshots {
        private final Map<SettlementNavigationSection, CompoundTag> navigation = new WeakHashMap<>();
        CompoundTag capture(SettlementSavedData data, net.minecraft.core.HolderLookup.Provider provider) {
            return data.save(new CompoundTag(), provider, town -> snapshot(town, provider));
        }
        private CompoundTag snapshot(SettlementRecord town, net.minecraft.core.HolderLookup.Provider provider) {
            var snapshot = town.save(provider, new net.minecraft.nbt.ListTag()).copy();
            var navigationTags = new net.minecraft.nbt.ListTag();
            town.navigation().values().forEach(section -> navigationTags.add(this.navigation.computeIfAbsent(section, SettlementNavigationSection::save)));
            snapshot.put("Navigation", navigationTags);
            return snapshot;
        }
    }
    private static final class Pending {
        final Snapshots snapshots = new Snapshots();
        CompletableFuture<Void> future;
        long revision, retryAt;
    }
}
