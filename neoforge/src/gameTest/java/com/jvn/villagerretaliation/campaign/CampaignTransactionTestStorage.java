package com.jvn.villagerretaliation.campaign;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerLevel;

final class CampaignTransactionTestStorage implements AutoCloseable {
    private final ServerLevel level;
    private final CampaignTransactionSavedData prior;
    private final Path directory = Files.createTempDirectory("campaign-bargaining-");
    private boolean blocked;

    CampaignTransactionTestStorage(ServerLevel level) throws IOException {
        this.level = level;
        prior = CampaignTransactionSavedData.get(level);
        install(new CompoundTag());
    }

    CampaignTransactionSavedData data() { return CampaignTransactionSavedData.get(level); }
    Path file() { return directory.resolve(CampaignTransactionSavedData.DATA_NAME + ".dat"); }
    CompoundTag read() throws IOException { return NbtIo.readCompressed(file(), NbtAccounter.unlimitedHeap()).getCompound("data"); }
    void reload() throws IOException { install(read()); }
    void install(CompoundTag saved) {
        level.getServer().overworld().getDataStorage().set(CampaignTransactionSavedData.DATA_NAME, CampaignTransactionSavedData.load(saved, directory));
    }

    void blockWrites() throws IOException {
        if (blocked) throw new IllegalStateException("Transaction writes are already blocked");
        if (Files.exists(file())) Files.move(file(), directory.resolve("previous.dat"));
        Files.createDirectory(file());
        blocked = true;
    }

    void restoreWrites() throws IOException {
        if (!blocked) return;
        Files.delete(file());
        if (Files.exists(directory.resolve("previous.dat"))) Files.move(directory.resolve("previous.dat"), file());
        blocked = false;
    }

    @Override public void close() throws IOException {
        level.getServer().overworld().getDataStorage().set(CampaignTransactionSavedData.DATA_NAME, prior);
        restoreWrites();
        Files.deleteIfExists(file());
        Files.delete(directory);
    }
}
