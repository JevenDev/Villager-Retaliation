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
    public static final int CURRENT_DATA_VERSION = 12;
    static final String DATA_NAME = "villagerretaliation_conversations";
    private final Path directory;
    private Map<UUID, ConversationSession> sessions = Map.of();
    private Map<UUID, UUID> locks = Map.of();
    private java.util.Set<ConversationObjectiveEvidence.Key> objectiveEvidence = java.util.Set.of();
    private final java.util.ArrayDeque<UUID> maintenance = new java.util.ArrayDeque<>();
    private final java.util.ArrayDeque<UUID> socialAwardMaintenance = new java.util.ArrayDeque<>();
    private final Map<UUID, ConversationSession> pending = new LinkedHashMap<>();
    private final Map<UUID, String> persistenceIssues = new LinkedHashMap<>();
    private final Map<UUID, String> socialIssues = new LinkedHashMap<>();
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
            int version = ConversationSessionCodec.integer(tag, "DataVersion");
            ConversationSessionCodec.require(version >= 1 && version <= CURRENT_DATA_VERSION, "unsupported conversation save version");
            var sessions = new LinkedHashMap<UUID, ConversationSession>();
            for (var raw : ConversationSessionCodec.list(tag, "Sessions")) {
                var saved = ((CompoundTag) raw).copy();
                if (version == 1) saved.put("Consent", new CompoundTag());
                if (version < 3) saved.put("DecisionParticipants", new CompoundTag());
                if (version < 4) saved.remove("NestedDecision");
                if (version < 5) ConversationParticipationEvidence.migrate(saved);
                if (version < 6) saved.put("ObjectiveEvidence", new ListTag());
                if (version < 8) {
                    ConversationSessionCodec.require(!saved.contains("SpeakerHistory") || ConversationSessionCodec.list(saved, "SpeakerHistory").isEmpty(),
                            "legacy conversation data contains speaker repairs");
                    saved.put("SpeakerHistory", new ListTag());
                }
                if (version < 9) {
                    ConversationSessionCodec.require(!saved.contains("SocialAwards") || ConversationSessionCodec.compound(saved, "SocialAwards").isEmpty(),
                            "legacy conversation data contains social experience receipts");
                    for (var check : ConversationSessionCodec.list(saved, "Checks"))
                        ConversationSessionCodec.require(!((CompoundTag) check).contains("Social"), "legacy conversation data contains social modifiers");
                    saved.put("SocialAwards", new CompoundTag());
                }
                if (version < 10) for (var checkTag : ConversationSessionCodec.list(saved, "Checks")) {
                    var check = (CompoundTag) checkTag;
                    ConversationSessionCodec.require(!check.contains("Modifiers") || ConversationSessionCodec.list(check, "Modifiers").isEmpty(),
                            "legacy conversation data contains situational modifiers");
                    check.put("Modifiers", new ListTag());
                }
                if (version < 11) {
                    ConversationSessionCodec.require(!saved.contains("TransactionChecks") || ConversationSessionCodec.compound(saved, "TransactionChecks").isEmpty(),
                            "legacy conversation data contains transaction receipts");
                    saved.put("TransactionChecks", new CompoundTag());
                }
                var session = ConversationSessionCodec.load(saved);
                if (version < 3 && (session.phase == ConversationSession.Phase.RESPONSE_EFFECTS
                        || session.phase == ConversationSession.Phase.CHECK || session.phase == ConversationSession.Phase.ROLLED)) {
                    session.choices.stream().filter(choice -> choice.visit() == session.visit && choice.node().equals(session.node)
                            && choice.response().equals(session.response) && choice.player().equals(session.actingPlayer)).findFirst().ifPresent(choice ->
                            session.decisionParticipants = new com.jvn.villagerretaliation.campaign.CampaignDecisionParticipants(
                                    session.identity.id(), choice.request(), session.identity.campaign(), session.identity.campaignInstance(),
                                    choice.player(), choice.participants()));
                }
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

    public synchronized boolean hasTransaction(UUID operation) {
        return sessions.values().stream().anyMatch(session -> session.transactionChecks.containsValue(operation));
    }

    public synchronized boolean hasObjectiveEvidence(ConversationObjectiveEvidence.Key key) {
        return diagnostic.isBlank() && objectiveEvidence.contains(key);
    }

    private static java.util.Set<ConversationObjectiveEvidence.Key> objectiveEvidence(Map<UUID, ConversationSession> sessions) {
        var keys = new java.util.HashSet<ConversationObjectiveEvidence.Key>();
        sessions.values().forEach(session -> keys.addAll(session.objectiveEvidence.keySet()));
        return java.util.Set.copyOf(keys);
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

    synchronized java.util.List<UUID> socialAwardBatch() {
        var result = new java.util.ArrayList<UUID>();
        for (int count = Math.min(16, socialAwardMaintenance.size()); count > 0; count--) {
            UUID id = socialAwardMaintenance.removeFirst();
            result.add(id);
            socialAwardMaintenance.addLast(id);
        }
        return java.util.List.copyOf(result);
    }

    synchronized boolean hasPendingSocialAwards(UUID player) {
        return socialAwardMaintenance.stream().anyMatch(id -> {
            var session = sessions.get(id);
            return session.checks.values().stream().anyMatch(check -> check.player().equals(player)
                    && check.social() != null && !session.socialAwards.contains(check.id()));
        });
    }

    synchronized java.util.List<UUID> controlledBy(UUID player) {
        var ids = new java.util.LinkedHashSet<UUID>();
        maintenance.stream().filter(id -> player.equals(sessions.get(id).controller)).forEach(ids::add);
        pending.values().stream().filter(session -> player.equals(session.controller)).forEach(session -> ids.add(session.identity.id()));
        return java.util.List.copyOf(ids);
    }

    synchronized boolean pausePendingController(UUID id, UUID player, String reason) {
        var candidate = pending.get(id);
        if (candidate == null || !player.equals(candidate.controller)) return false;
        candidate = new ConversationSession(candidate);
        candidate.controller = null;
        candidate.diagnostic = reason;
        candidate.revision++;
        pending.put(id, candidate);
        return true;
    }

    synchronized UUID activeCharacter(UUID character) {
        for (var candidate : pending.values()) {
            if (candidate.active() && candidate.speakers.values().stream().anyMatch(speaker -> speaker.entity().equals(character))) return candidate.identity.id();
        }
        return locks.get(character);
    }

    synchronized java.util.List<ConversationSession> activeSessions() {
        var active = new LinkedHashMap<UUID, ConversationSession>();
        maintenance.forEach(id -> active.put(id, sessions.get(id)));
        pending.forEach((id, candidate) -> { if (candidate.active()) active.put(id, candidate); });
        return active.values().stream().filter(ConversationSession::active).map(ConversationSession::new).toList();
    }

    private synchronized UUID activeRole(RoleKey role) {
        for (var session : pending.values())
            if (session.active() && role.matches(session)) return session.identity.id();
        for (UUID id : maintenance)
            if (role.matches(sessions.get(id))) return id;
        return null;
    }

    private record RoleKey(net.minecraft.resources.ResourceLocation campaign, UUID instance, String role) {
        boolean matches(ConversationSession session) {
            return session.identity.campaign().equals(campaign) && session.identity.campaignInstance().equals(instance) && session.speakers.containsKey(role);
        }
    }

    synchronized String socialIssue(UUID session) { return socialIssues.getOrDefault(session, ""); }

    synchronized boolean socialIssue(UUID session, String issue) {
        String prior = socialIssues.getOrDefault(session, "");
        if (issue.isBlank()) socialIssues.remove(session);
        else socialIssues.put(session, issue);
        return !prior.equals(issue);
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
            UUID roleOwner = activeRole(new RoleKey(candidate.identity.campaign(), candidate.identity.campaignInstance(), speaker.role()));
            if (candidate.active() && (owner != null && !owner.equals(candidate.identity.id())
                    || roleOwner != null && !roleOwner.equals(candidate.identity.id()))) {
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
                        || receipt.result().suspended()
                                && (receipt.result().suspensionKind() == com.jvn.villagerretaliation.action.ActionResult.SuspensionKind.SCENE
                                        || receipt.result().suspensionKind() == com.jvn.villagerretaliation.action.ActionResult.SuspensionKind.CONTINUATION));
        if (level != null && sceneDependency) SceneSavedData.get(level).persistCampaignHandoff(level.getServer().overworld());
        var wrapper = new CompoundTag();
        wrapper.put("data", encode(next));
        NbtUtils.addCurrentDataVersion(wrapper);
        IOUtilities.waitUntilIOWorkerComplete();
        Files.createDirectories(directory);
        com.jvn.villagerretaliation.util.AtomicDataFiles.writeCompressed(wrapper, directory.resolve(DATA_NAME + ".dat"));
        sessions = java.util.Collections.unmodifiableMap(next);
        locks = nextLocks;
        objectiveEvidence = objectiveEvidence(next);
        if (candidate.active()) {
            if (!maintenance.contains(candidate.identity.id())) maintenance.addLast(candidate.identity.id());
        } else maintenance.remove(candidate.identity.id());
        if (candidate.hasPendingSocialAwards()) {
            if (!socialAwardMaintenance.contains(candidate.identity.id())) socialAwardMaintenance.addLast(candidate.identity.id());
        } else {
            socialAwardMaintenance.remove(candidate.identity.id());
            socialIssues.remove(candidate.identity.id());
        }
        setDirty(false);
    }

    private void install(Map<UUID, ConversationSession> next) {
        var nextLocks = locks(next);
        sessions = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(next));
        locks = nextLocks;
        objectiveEvidence = objectiveEvidence(next);
        maintenance.clear();
        next.values().stream().filter(ConversationSession::active).forEach(session -> maintenance.addLast(session.identity.id()));
        socialAwardMaintenance.clear();
        next.values().stream().filter(ConversationSession::hasPendingSocialAwards).forEach(session -> socialAwardMaintenance.addLast(session.identity.id()));
    }

    private static Map<UUID, UUID> locks(Map<UUID, ConversationSession> sessions) {
        var result = new LinkedHashMap<UUID, UUID>();
        var roles = new LinkedHashMap<RoleKey, UUID>();
        for (var session : sessions.values()) if (session.active()) {
            for (var speaker : session.speakers.values()) {
                var prior = result.putIfAbsent(speaker.entity(), session.identity.id());
                ConversationSessionCodec.require(prior == null || prior.equals(session.identity.id()), "character is locked by multiple conversations");
                var role = new RoleKey(session.identity.campaign(), session.identity.campaignInstance(), speaker.role());
                var priorRole = roles.putIfAbsent(role, session.identity.id());
                ConversationSessionCodec.require(priorRole == null || priorRole.equals(session.identity.id()), "campaign role is locked by multiple conversations");
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
