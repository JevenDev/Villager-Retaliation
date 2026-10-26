package com.jvn.villagerretaliation.diplomacy;

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

public final class VillageDiplomacyCheckpoint {
    private static final Map<VillageDiplomacySavedData, Pending> PENDING = new WeakHashMap<>();
    private VillageDiplomacyCheckpoint() { }
    public static boolean durable(ServerLevel level, long revision) {
        var data = VillageDiplomacySavedData.get(level);
        if (data.readOnly() || !level.getServer().isSameThread() || revision < 0 || revision > data.revision()) return false;
        var pending = PENDING.computeIfAbsent(data, ignored -> new Pending());
        if (pending.durable >= revision) return true;
        long now = level.getServer().overworld().getGameTime();
        if (pending.future != null) {
            if (!pending.future.isDone()) return false;
            try { pending.future.join(); pending.durable = Math.max(pending.durable, pending.revision); }
            catch (CompletionException failure) {
                pending.retryAt = now + 200;
                LogUtils.getLogger().error("Diplomacy checkpoint failed, delivery acknowledgement remains pending", failure.getCause());
            }
            pending.future = null;
            if (pending.durable >= revision) return true;
        }
        if (now < pending.retryAt) return false;
        var snapshot = new CompoundTag(); snapshot.put("data", data.save(new CompoundTag(), level.registryAccess()).copy());
        NbtUtils.addCurrentDataVersion(snapshot);
        var file = level.getServer().getWorldPath(LevelResource.ROOT).resolve("data").resolve("villagerretaliation_village_diplomacy.dat");
        pending.revision = data.revision();
        var completion = new CompletableFuture<Void>(); pending.future = completion;
        IOUtilities.withIOWorker(() -> {
            try { AtomicDataFiles.writeCompressed(snapshot, file); completion.complete(null); }
            catch (IOException | RuntimeException failure) { completion.completeExceptionally(failure); }
        });
        return false;
    }
    private static final class Pending {
        CompletableFuture<Void> future;
        long revision, retryAt;
        long durable = -1;
    }
}
