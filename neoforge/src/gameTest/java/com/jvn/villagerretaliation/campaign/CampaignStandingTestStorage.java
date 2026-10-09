package com.jvn.villagerretaliation.campaign;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerLevel;

final class CampaignStandingTestStorage implements AutoCloseable {
    private final ServerLevel level;
    private final CampaignStandingSavedData prior;
    private final Path directory = Files.createTempDirectory("campaign-standing-");
    private boolean blocked;

    CampaignStandingTestStorage(ServerLevel level) throws IOException {
        this.level = level;
        prior = CampaignStandingSavedData.get(level);
        install(CampaignStandingSavedData.load(new CompoundTag(), directory));
    }

    CampaignStandingSavedData data() { return CampaignStandingSavedData.get(level); }
    Path directory() { return directory; }
    Path file() { return directory.resolve(CampaignStandingSavedData.DATA_NAME + ".dat"); }
    CompoundTag read() throws IOException { return NbtIo.readCompressed(file(), NbtAccounter.unlimitedHeap()).getCompound("data"); }
    void reload() throws IOException { install(CampaignStandingSavedData.load(read(), directory)); }
    void install(CampaignStandingSavedData data) { level.getServer().overworld().getDataStorage().set(CampaignStandingSavedData.DATA_NAME, data); }

    void blockWrites() throws IOException {
        if (blocked) throw new IllegalStateException("Standing writes are already blocked");
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
        install(prior);
        restoreWrites();
        Files.deleteIfExists(file());
        Files.delete(directory);
    }
}
