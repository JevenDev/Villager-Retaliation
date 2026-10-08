package com.jvn.villagerretaliation.dialogue.conversation;

import com.jvn.villagerretaliation.scene.persistence.SceneSavedData;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.common.IOUtilities;

public final class ConversationSavedData extends SavedData {
    public static final int CURRENT_DATA_VERSION = 1;
    static final String DATA_NAME = "villagerretaliation_conversations";
    private final Path directory;
    private Map<UUID, ConversationSession> sessions = Map.of();
    private Map<UUID, UUID> locks = Map.of();
    private final java.util.ArrayDeque<UUID> maintenance = new java.util.ArrayDeque<>();
    private final Map<UUID, ConversationSession> pending = new LinkedHashMap<>();
    private final Map<UUID, String> persistenceIssues = new LinkedHashMap<>();
    private String diagnostic = "";
    private CompoundTag preserved;

    private ConversationSavedData(Path directory) { this.directory = directory; }

    public static ConversationSavedData get(ServerLevel level) {
        var directory = level.getServer().getWorldPath(LevelResource.ROOT).resolve("data");
        return level.getServer().overworld().getDataStorage().computeIfAbsent(new Factory<>(() -> missing(directory),
                (tag, provider) -> load(tag, directory), DataFixTypes.LEVEL), DATA_NAME);
    }

    private static ConversationSavedData missing(Path directory) {
        var data = new ConversationSavedData(directory);
        if (!Files.notExists(directory.resolve(DATA_NAME + ".dat"))) {
            data.diagnostic = "Conversation data could not be read. Restore " + DATA_NAME + ".dat from a known-good backup.";
        }
        return data;
    }

    static ConversationSavedData load(CompoundTag tag, Path directory) {
        var data = new ConversationSavedData(directory);
        if (tag.isEmpty() && Files.notExists(directory.resolve(DATA_NAME + ".dat"))) return data;
        try {
            ConversationSessionCodec.require(ConversationSessionCodec.integer(tag, "DataVersion") == CURRENT_DATA_VERSION, "unsupported conversation save version");
            var sessions = new LinkedHashMap<UUID, ConversationSession>();
            for (var raw : ConversationSessionCodec.list(tag, "Sessions")) {
                var session = ConversationSessionCodec.load((CompoundTag) raw);
                ConversationSessionCodec.require(sessions.putIfAbsent(session.identity.id(), session) == null, "duplicate conversation session identity");
            }
            data.install(sessions);
        } catch (IllegalArgumentException exception) {
            data.preserved = tag.copy();
            data.diagnostic = exception.getMessage() + ". Restore compatible conversation data or a known-good backup.";
        }
        return data;
    }

    public synchronized String diagnostic() { return diagnostic; }

    public synchronized Map<UUID, ConversationSession.Snapshot> snapshots() {
        var result = new LinkedHashMap<UUID, ConversationSession.Snapshot>();
        sessions.forEach((id, session) -> result.put(id, session.snapshot()));
        return java.util.Collections.unmodifiableMap(result);
    }

    synchronized ConversationSession session(UUID id) {
        var session = sessions.get(id);
        return session == null ? null : new ConversationSession(session);
    }

    synchronized java.util.List<UUID> maintenanceBatch() {
        var result = new java.util.ArrayList<UUID>();
        for (int count = Math.min(16, maintenance.size()); count > 0; count--) {
            UUID id = maintenance.removeFirst();
            result.add(id);
            maintenance.addLast(id);
        }
        return java.util.List.copyOf(result);
    }

    synchronized java.util.List<UUID> controlledBy(UUID player) {
        return maintenance.stream().filter(id -> player.equals(sessions.get(id).controller)).toList();
    }

    synchronized UUID activeCharacter(UUID character) {
        for (var candidate : pending.values()) {
            if (candidate.active() && candidate.speakers.values().stream().anyMatch(speaker -> speaker.entity().equals(character))) return candidate.identity.id();
        }
        return locks.get(character);
    }

    synchronized String issue(UUID session) {
        return diagnostic.isBlank() ? persistenceIssues.getOrDefault(session, "") : diagnostic;
    }

    synchronized void write(ServerLevel level, long expectedRevision, ConversationSession candidate) throws IOException {
        if (!issue(candidate.identity.id()).isBlank()) throw new IOException(issue(candidate.identity.id()));
        var prior = sessions.get(candidate.identity.id());
        if ((prior == null ? expectedRevision != 0 : prior.revision != expectedRevision || !prior.identity.equals(candidate.identity))
                || candidate.revision != expectedRevision + 1) {
            throw new Conflict("Conversation request no longer matches its saved revision.");
        }
        for (var speaker : candidate.speakers.values()) {
            UUID owner = activeCharacter(speaker.entity());
            if (candidate.active() && owner != null && !owner.equals(candidate.identity.id())) {
                throw new Conflict("This character is already taking part in another conversation.");
            }
        }
        try {
            persist(level, candidate);
        } catch (IOException exception) {
            pending.put(candidate.identity.id(), new ConversationSession(candidate));
            persistenceIssues.put(candidate.identity.id(), "Conversation is waiting for world storage: " + exception.getMessage());
            throw exception;
        }
    }

    synchronized void retryPending(ServerLevel level) {
        for (UUID id : pending.keySet().stream().limit(16).toList()) {
            try {
                persist(level, pending.get(id));
                pending.remove(id);
                persistenceIssues.remove(id);
            } catch (IOException exception) {
                persistenceIssues.put(id, "Conversation is waiting for world storage: " + exception.getMessage());
            }
        }
    }

    private void persist(ServerLevel level, ConversationSession candidate) throws IOException {
        var next = new LinkedHashMap<>(sessions);
        next.put(candidate.identity.id(), new ConversationSession(candidate));
        var nextLocks = locks(next);
        boolean sceneDependency = candidate.receipts.values().stream().anyMatch(receipt ->
                receipt.action().kind() == com.jvn.villagerretaliation.action.VillagerActionDefinition.Kind.START_SCENE
                        || receipt.result().suspended() && receipt.result().suspensionKind() != com.jvn.villagerretaliation.action.ActionResult.SuspensionKind.CAMPAIGN_REWARD);
        if (level != null && sceneDependency) SceneSavedData.get(level).persistCampaignHandoff(level.getServer().overworld());
        var wrapper = new CompoundTag();
        wrapper.put("data", encode(next));
        NbtUtils.addCurrentDataVersion(wrapper);
        IOUtilities.waitUntilIOWorkerComplete();
        Files.createDirectories(directory);
        IOUtilities.writeNbtCompressed(wrapper, directory.resolve(DATA_NAME + ".dat"));
        sessions = java.util.Collections.unmodifiableMap(next);
        locks = nextLocks;
        if (candidate.active()) {
            if (!maintenance.contains(candidate.identity.id())) maintenance.addLast(candidate.identity.id());
        } else maintenance.remove(candidate.identity.id());
        setDirty(false);
    }

    private void install(Map<UUID, ConversationSession> next) {
        var nextLocks = locks(next);
        sessions = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(next));
        locks = nextLocks;
        maintenance.clear();
        next.values().stream().filter(ConversationSession::active).forEach(session -> maintenance.addLast(session.identity.id()));
    }

    private static Map<UUID, UUID> locks(Map<UUID, ConversationSession> sessions) {
        var result = new LinkedHashMap<UUID, UUID>();
        for (var session : sessions.values()) if (session.active()) {
            for (var speaker : session.speakers.values()) {
                var prior = result.putIfAbsent(speaker.entity(), session.identity.id());
                ConversationSessionCodec.require(prior == null || prior.equals(session.identity.id()), "character is locked by multiple conversations");
            }
        }
        return Map.copyOf(result);
    }

    private static CompoundTag encode(Map<UUID, ConversationSession> sessions) {
        var tag = new CompoundTag();
        tag.putInt("DataVersion", CURRENT_DATA_VERSION);
        var values = new ListTag();
        sessions.values().forEach(session -> values.add(ConversationSessionCodec.save(session)));
        tag.put("Sessions", values);
        return tag;
    }

    static final class Conflict extends IllegalArgumentException { Conflict(String message) { super(message); } }

    @Override
    public synchronized CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) { return preserved == null ? encode(sessions) : preserved.copy(); }

    @Override
    public synchronized void save(File file, HolderLookup.Provider provider) { if (diagnostic.isBlank()) super.save(file, provider); }
}
