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
    static boolean durable(ServerLevel level, long revision) { return checkpoint(level, revision, false); }
    static boolean urgent(ServerLevel level, long revision) { return checkpoint(level, revision, true); }
    private static boolean checkpoint(ServerLevel level, long revision, boolean urgent) {
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
        if (now < pending.retryAt || !pending.schedule.ready(now, urgent)) return false;
        var snapshot = new CompoundTag();
        long started = com.jvn.villagerretaliation.diplomacy.VillageDiplomacyMetrics.start();
        try { snapshot.put("data", data.snapshot(level.registryAccess())); }
        finally { com.jvn.villagerretaliation.diplomacy.VillageDiplomacyMetrics.finish(level.getServer(),
                com.jvn.villagerretaliation.diplomacy.VillageDiplomacyMetrics.Part.ROOT_CHECKPOINT, started); }
        NbtUtils.addCurrentDataVersion(snapshot);
        var file = level.getServer().getWorldPath(LevelResource.ROOT).resolve("data").resolve("villagerretaliation_settlements.dat");
        pending.revision = aid.revision(); pending.schedule.captured(now);
        var completion = new CompletableFuture<Void>(); pending.future = completion;
        IOUtilities.withIOWorker(() -> {
            try { AtomicDataFiles.writeCompressed(snapshot, file); completion.complete(null); }
            catch (IOException | RuntimeException failure) { completion.completeExceptionally(failure); }
        });
        return false;
    }
    static final class Schedule {
        private long nextCapture;
        boolean ready(long now, boolean urgent) { return urgent || now >= this.nextCapture; }
        void captured(long now) { this.nextCapture = now + 100; }
    }
    private static final class Pending {
        final Schedule schedule = new Schedule();
        CompletableFuture<Void> future;
        long revision, retryAt;
    }
}
