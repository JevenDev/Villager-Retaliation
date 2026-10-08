package com.jvn.villagerretaliation.dialogue.conversation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

public final class ConversationTestStorage implements AutoCloseable {
    private final ServerLevel level;
    private final ConversationSavedData prior;
    private final Path directory = Files.createTempDirectory("conversation-session-");

    public ConversationTestStorage(ServerLevel level) throws IOException {
        this.level = level;
        prior = ConversationSavedData.get(level);
        level.getServer().overworld().getDataStorage().set(ConversationSavedData.DATA_NAME, ConversationSavedData.load(new CompoundTag(), directory));
    }

    public void reload() throws IOException {
        level.getServer().overworld().getDataStorage().set(ConversationSavedData.DATA_NAME,
                ConversationSavedData.load(NbtIo.readCompressed(directory.resolve(ConversationSavedData.DATA_NAME + ".dat"), NbtAccounter.unlimitedHeap()).getCompound("data"), directory));
    }

    public static void maintain(MinecraftServer server) { ConversationService.maintain(server); }

    public void leavePrepared(UUID id) throws IOException {
        var data = ConversationSavedData.get(level);
        var session = data.session(id);
        var receipt = session.receipts.values().iterator().next();
        session.receipts.put(receipt.key(), new ConversationSession.Receipt(receipt.key(), receipt.actor(), receipt.questRun(), receipt.action(),
                ConversationSession.ReceiptState.PREPARED, receipt.result()));
        session.phase = ConversationSession.Phase.NODE_EFFECTS;
        session.exchange = 0;
        session.effect = 0;
        session.transcript.clear();
        long previous = session.revision++;
        data.write(level, previous, session);
    }

    @Override public void close() throws IOException {
        level.getServer().overworld().getDataStorage().set(ConversationSavedData.DATA_NAME, prior);
        Files.deleteIfExists(directory.resolve(ConversationSavedData.DATA_NAME + ".dat"));
        Files.delete(directory);
    }
}
