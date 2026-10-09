package com.jvn.villagerretaliation.skill.player;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerLevel;

public final class SocialProgressionTestStorage implements AutoCloseable {
    private final ServerLevel level;
    private final PlayerSocialProgressionSavedData prior;
    private final Path directory = Files.createTempDirectory("player-social-skills-");
    private boolean blocked;

    public SocialProgressionTestStorage(ServerLevel level) throws IOException {
        this.level = level;
        prior = PlayerSocialProgressionSavedData.get(level);
        install(PlayerSocialProgressionSavedData.load(new CompoundTag(), directory));
    }

    public PlayerSocialProgressionSavedData data() { return PlayerSocialProgressionSavedData.get(level); }
    public Path directory() { return directory; }
    public Path file() { return directory.resolve(PlayerSocialProgressionSavedData.DATA_NAME + ".dat"); }
    public CompoundTag read() throws IOException { return NbtIo.readCompressed(file(), NbtAccounter.unlimitedHeap()).getCompound("data"); }
    public void reload() throws IOException { install(PlayerSocialProgressionSavedData.load(read(), directory)); }
    public void install(PlayerSocialProgressionSavedData data) {
        level.getServer().overworld().getDataStorage().set(PlayerSocialProgressionSavedData.DATA_NAME, data);
    }

    public void blockWrites() throws IOException {
        if (blocked) throw new IllegalStateException("Social writes are already blocked");
        if (Files.exists(file())) Files.move(file(), directory.resolve("previous.dat"));
        Files.createDirectory(file());
        blocked = true;
    }

    public void restoreWrites() throws IOException {
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
