package com.jvn.villagerretaliation.campaign;

import com.jvn.villagerretaliation.campaign.content.CampaignContentCatalog;
import com.jvn.villagerretaliation.campaign.content.CampaignDefinition;
import com.jvn.villagerretaliation.quest.content.bundle.QuestLocaleCatalog;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.common.IOUtilities;

public final class CampaignSavedData extends SavedData {
    public static final int CURRENT_DATA_VERSION = 2;
    static final String DATA_NAME = "villagerretaliation_campaigns";
    private final Path directory;
    private Map<ResourceLocation, CampaignInstance> instances = Map.of();
    private String diagnostic = "";
    private CompoundTag preservedData;

    private CampaignSavedData(Path directory) {
        this.directory = directory;
    }

    public static CampaignSavedData get(ServerLevel level) {
        var overworld = level.getServer().overworld();
        return get(overworld.getDataStorage(), level.getServer().getWorldPath(LevelResource.ROOT).resolve("data"));
    }

    static CampaignSavedData get(DimensionDataStorage storage, Path directory) {
        return storage.computeIfAbsent(new SavedData.Factory<>(() -> missing(directory),
                (tag, provider) -> load(tag, directory), DataFixTypes.LEVEL), DATA_NAME);
    }

    private static CampaignSavedData missing(Path directory) {
        var data = new CampaignSavedData(directory);
        if (!Files.notExists(directory.resolve(DATA_NAME + ".dat"))) {
            data.diagnostic = "Campaign save could not be read. Restore data/" + DATA_NAME + ".dat from a known-good backup.";
        }
        return data;
    }

    static CampaignSavedData load(CompoundTag tag, Path directory) {
        var data = new CampaignSavedData(directory);
        try {
            var migrated = CampaignSaveMigrations.migrate(tag);
            Map<ResourceLocation, CampaignInstance> instances = new LinkedHashMap<>();
            Set<UUID> identities = new HashSet<>();
            for (Tag raw : CampaignStateCodec.list(migrated, "Campaigns", Tag.TAG_COMPOUND)) {
                var instance = CampaignStateCodec.load((CompoundTag) raw);
                CampaignStateCodec.require(identities.add(instance.instanceId()), "duplicate campaign instance identity");
                CampaignStateCodec.require(instances.putIfAbsent(instance.campaignId(), instance) == null, "duplicate campaign instance");
            }
            data.instances = immutable(instances);
        } catch (IllegalArgumentException exception) {
            data.preservedData = tag.copy();
            data.diagnostic = exception.getMessage() + ". Use a compatible mod version or restore data/" + DATA_NAME + ".dat from a known-good backup.";
        }
        return data;
    }

    public synchronized Map<ResourceLocation, CampaignInstance> instances() {
        return this.instances;
    }

    public synchronized String diagnostic() {
        return this.diagnostic;
    }

    public synchronized String compatibility(CampaignDefinition definition) {
        if (!this.diagnostic.isBlank()) return this.diagnostic;
        var current = this.instances.get(definition.id());
        if (current != null && (current.definitionRevision() != definition.revision() || !current.fingerprint().equals(definition.fingerprint()))) {
            return "Campaign " + definition.id() + " requires its saved definition revision " + current.definitionRevision()
                    + " and fingerprint " + current.fingerprint() + ". Restore that content before continuing.";
        }
        return "";
    }

    synchronized CampaignInstance open(CampaignContentCatalog.Entry content, long gameTime) throws IOException {
        var definition = content.definition();
        String issue = compatibility(definition);
        if (!issue.isBlank()) throw new IOException(issue);
        var current = this.instances.get(definition.id());
        if (current != null) return current;
        var created = new CampaignInstance(definition.id(), UUID.randomUUID(), definition.revision(), definition.fingerprint(),
                title(content, definition.titleKey()), gameTime, 0L, Map.of(), Map.of());
        persist(created);
        return created;
    }

    synchronized CommitResult commitWorldOutcome(CampaignContentCatalog.Entry content, String milestoneId, String outcomeId,
            UUID operationId, long expectedRevision, CampaignInstance.Origin origin, Set<UUID> participants, long gameTime) throws IOException {
        var definition = content.definition();
        String issue = compatibility(definition);
        var current = this.instances.get(definition.id());
        if (!issue.isBlank() || current == null) return result(Status.UNAVAILABLE, current, null,
                issue.isBlank() ? "Campaign instance has not started" : issue);
        var prior = current.outcomes().get(milestoneId);
        if (prior != null) return result(Status.ALREADY_COMMITTED, current, prior, "");
        if (operationId == null || current.outcomes().values().stream().anyMatch(value -> value.operationId().equals(operationId))) {
            return result(Status.INVALID_REQUEST, current, null, "Outcome operation id is missing or already belongs to another milestone");
        }
        var milestone = definition.milestones().get(milestoneId);
        if (milestone == null || !milestone.irreversible() || !milestone.outcomes().containsKey(outcomeId)) {
            return result(Status.INVALID_REQUEST, current, null, "Shared outcome must name an authored irreversible milestone outcome");
        }
        if (origin == null || participants == null || participants.stream().anyMatch(java.util.Objects::isNull)
                || origin == CampaignInstance.Origin.WORLD_OBSERVATION && (!participants.isEmpty()
                        || milestone.observation() == null || !milestone.observation().outcome().equals(outcomeId))) {
            return result(Status.INVALID_REQUEST, current, null, "World observations cannot grant participation or select an unauthored outcome");
        }
        if (expectedRevision != current.worldRevision()) return result(Status.STALE, current, null, "Campaign world revision changed");
        var committed = new CampaignInstance.OutcomeCommit(operationId, milestoneId, outcomeId,
                title(content, milestone.outcomes().get(outcomeId).titleKey()), current.worldRevision() + 1L, gameTime, origin);
        Map<String, CampaignInstance.OutcomeCommit> outcomes = new LinkedHashMap<>(current.outcomes());
        outcomes.put(milestoneId, committed);
        Map<UUID, CampaignInstance.PlayerRecord> players = new LinkedHashMap<>(current.players());
        Set<UUID> affected = new HashSet<>(participants);
        players.forEach((id, player) -> {
            if (player.milestones().containsKey(milestoneId)) affected.add(id);
        });
        for (UUID playerId : affected) {
            var previous = players.getOrDefault(playerId, CampaignInstance.PlayerRecord.empty());
            boolean participant = participants.contains(playerId);
            Set<String> participation = new HashSet<>(previous.participation());
            if (participant) participation.add(milestoneId);
            Map<String, String> discoveries = new LinkedHashMap<>(previous.discoveredOutcomes());
            discoveries.put(milestoneId, outcomeId);
            Map<String, CampaignInstance.MilestoneProgress> milestones = new LinkedHashMap<>(previous.milestones());
            if (milestones.containsKey(milestoneId)) milestones.put(milestoneId,
                    CampaignProgression.reconcile(milestones.get(milestoneId), committed, participant));
            players.put(playerId, new CampaignInstance.PlayerRecord(previous.revision() + 1L, participation, discoveries,
                    milestones, previous.completedQuests()));
        }
        var next = replace(current, committed.revision(), outcomes, players);
        persist(next);
        return result(Status.APPLIED, next, committed, "");
    }

    synchronized boolean discover(CampaignDefinition definition, UUID playerId, String milestoneId) throws IOException {
        String issue = compatibility(definition);
        if (!issue.isBlank()) throw new IOException(issue);
        var current = this.instances.get(definition.id());
        if (current == null || playerId == null || !current.outcomes().containsKey(milestoneId)) return false;
        var player = current.players().getOrDefault(playerId, CampaignInstance.PlayerRecord.empty());
        if (player.discoveredOutcomes().containsKey(milestoneId)) return false;
        Map<String, String> discoveries = new LinkedHashMap<>(player.discoveredOutcomes());
        discoveries.put(milestoneId, current.outcomes().get(milestoneId).outcome());
        Map<UUID, CampaignInstance.PlayerRecord> players = new LinkedHashMap<>(current.players());
        players.put(playerId, new CampaignInstance.PlayerRecord(player.revision() + 1L, player.participation(), discoveries,
                player.milestones(), player.completedQuests()));
        persist(replace(current, current.worldRevision(), current.outcomes(), players));
        return true;
    }

    synchronized ProgressResult enterMilestone(CampaignDefinition definition, UUID playerId, String milestoneId, long gameTime) throws IOException {
        var current = this.instances.get(definition.id());
        String issue = compatibility(definition);
        if (!issue.isBlank() || current == null) return progressResult(Status.UNAVAILABLE, null, issue.isBlank() ? "Campaign instance has not started" : issue);
        var milestone = definition.milestones().get(milestoneId);
        if (milestone == null || playerId == null) return progressResult(Status.INVALID_REQUEST, null, "Unknown milestone or player");
        var player = current.players().getOrDefault(playerId, CampaignInstance.PlayerRecord.empty());
        if (player.milestones().containsKey(milestoneId)) return progressResult(Status.ALREADY_SATISFIED,
                CampaignProgression.entry(definition, current, playerId, milestoneId), "");
        var chapter = CampaignProgression.chapter(definition, current, playerId, milestone.chapter());
        if (!chapter.available()) return new ProgressResult(Status.UNAVAILABLE, null, chapter.blockers());
        var outcome = current.outcomes().get(milestoneId);
        Map<String, CampaignInstance.MilestoneProgress> milestones = new LinkedHashMap<>(player.milestones());
        milestones.put(milestoneId, CampaignProgression.enter(outcome, gameTime));
        Map<String, String> discoveries = new LinkedHashMap<>(player.discoveredOutcomes());
        if (outcome != null) discoveries.put(milestoneId, outcome.outcome());
        var next = replacePlayer(current, playerId, new CampaignInstance.PlayerRecord(player.revision() + 1L, player.participation(),
                discoveries, milestones, player.completedQuests()));
        persist(next);
        return progressResult(Status.APPLIED, CampaignProgression.entry(definition, next, playerId, milestoneId), "");
    }

    synchronized ProgressResult completeMilestone(CampaignDefinition definition, UUID playerId, String milestoneId,
            CampaignInstance.Route expectedRoute, String outcomeId, UUID completionOperation, long gameTime) throws IOException {
        var current = this.instances.get(definition.id());
        String issue = compatibility(definition);
        if (!issue.isBlank() || current == null) return progressResult(Status.UNAVAILABLE, null, issue.isBlank() ? "Campaign instance has not started" : issue);
        var milestone = definition.milestones().get(milestoneId);
        var player = current.players().get(playerId);
        var progress = player == null ? null : player.milestones().get(milestoneId);
        if (milestone == null || progress == null || !milestone.outcomes().containsKey(outcomeId) || completionOperation == null) {
            return progressResult(Status.INVALID_REQUEST, null, "Personal completion requires an entered milestone, authored outcome, and operation id");
        }
        var entry = CampaignProgression.entry(definition, current, playerId, milestoneId);
        if (progress.completed()) return progressResult(Status.ALREADY_SATISFIED, entry, "");
        if (progress.route() != expectedRoute) return progressResult(Status.STALE, entry, "Milestone route changed. Refresh the authored entry before continuing.");
        var worldOutcome = current.outcomes().get(milestoneId);
        if (milestone.irreversible() && (worldOutcome == null || !worldOutcome.outcome().equals(outcomeId)
                || !worldOutcome.operationId().equals(progress.worldOperation()))) {
            return progressResult(Status.UNAVAILABLE, entry, "Personal completion must follow the authoritative world outcome");
        }
        if (player.milestones().values().stream().anyMatch(value -> completionOperation.equals(value.completionOperation()))) {
            return progressResult(Status.INVALID_REQUEST, entry, "Completion operation already belongs to another milestone");
        }
        Map<String, CampaignInstance.MilestoneProgress> milestones = new LinkedHashMap<>(player.milestones());
        milestones.put(milestoneId, new CampaignInstance.MilestoneProgress(progress.route(), outcomeId, progress.worldOperation(),
                progress.enteredAt(), completionOperation, gameTime));
        var completedQuests = CampaignProgression.completedQuests(definition, milestones, player.completedQuests());
        var next = replacePlayer(current, playerId, new CampaignInstance.PlayerRecord(player.revision() + 1L, player.participation(),
                player.discoveredOutcomes(), milestones, completedQuests));
        persist(next);
        return progressResult(Status.APPLIED, CampaignProgression.entry(definition, next, playerId, milestoneId), "");
    }

    synchronized boolean completeQuest(CampaignDefinition definition, UUID playerId, ResourceLocation quest) throws IOException {
        String issue = compatibility(definition);
        if (!issue.isBlank()) throw new IOException(issue);
        var current = this.instances.get(definition.id());
        if (current == null || playerId == null || !definition.questBundles().contains(quest)) return false;
        var player = current.players().getOrDefault(playerId, CampaignInstance.PlayerRecord.empty());
        if (player.completedQuests().contains(quest)) return false;
        Set<ResourceLocation> completed = new HashSet<>(player.completedQuests());
        completed.add(quest);
        var reconciled = CampaignProgression.completedQuests(definition, player.milestones(), completed);
        persist(replacePlayer(current, playerId, new CampaignInstance.PlayerRecord(player.revision() + 1L, player.participation(),
                player.discoveredOutcomes(), player.milestones(), reconciled)));
        return true;
    }

    private static CampaignInstance replacePlayer(CampaignInstance current, UUID playerId, CampaignInstance.PlayerRecord player) {
        Map<UUID, CampaignInstance.PlayerRecord> players = new LinkedHashMap<>(current.players());
        players.put(playerId, player);
        return replace(current, current.worldRevision(), current.outcomes(), players);
    }

    private static ProgressResult progressResult(Status status, CampaignProgression.Entry entry, String diagnostic) {
        return new ProgressResult(status, entry, diagnostic.isBlank() ? java.util.List.of() : java.util.List.of(diagnostic));
    }

    private static CampaignInstance replace(CampaignInstance current, long revision,
            Map<String, CampaignInstance.OutcomeCommit> outcomes, Map<UUID, CampaignInstance.PlayerRecord> players) {
        return new CampaignInstance(current.campaignId(), current.instanceId(), current.definitionRevision(), current.fingerprint(),
                current.title(), current.createdAt(), revision, outcomes, players);
    }

    private void persist(CampaignInstance replacement) throws IOException {
        Map<ResourceLocation, CampaignInstance> candidate = new LinkedHashMap<>(this.instances);
        candidate.put(replacement.campaignId(), replacement);
        CompoundTag wrapper = new CompoundTag();
        wrapper.put("data", encode(candidate));
        NbtUtils.addCurrentDataVersion(wrapper);
        IOUtilities.waitUntilIOWorkerComplete();
        Files.createDirectories(this.directory);
        IOUtilities.writeNbtCompressed(wrapper, this.directory.resolve(DATA_NAME + ".dat"));
        this.instances = immutable(candidate);
        setDirty(false);
    }

    private static Map<ResourceLocation, CampaignInstance> immutable(Map<ResourceLocation, CampaignInstance> source) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }

    private static CompoundTag encode(Map<ResourceLocation, CampaignInstance> instances) {
        var tag = new CompoundTag();
        tag.putInt("DataVersion", CURRENT_DATA_VERSION);
        var campaigns = new ListTag();
        instances.values().forEach(instance -> campaigns.add(CampaignStateCodec.save(instance)));
        tag.put("Campaigns", campaigns);
        return tag;
    }

    @Override
    public synchronized CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        return this.preservedData == null ? encode(this.instances) : this.preservedData.copy();
    }

    @Override
    public synchronized void save(File file, HolderLookup.Provider provider) {
        if (this.diagnostic.isBlank()) super.save(file, provider);
    }

    private static String title(CampaignContentCatalog.Entry content, String key) {
        return content.localization().plainText(QuestLocaleCatalog.ENGLISH, key).orElse(key);
    }

    private static CommitResult result(Status status, CampaignInstance instance, CampaignInstance.OutcomeCommit outcome, String diagnostic) {
        return new CommitResult(status, instance == null ? -1L : instance.worldRevision(), outcome, diagnostic);
    }

    public enum Status { APPLIED, ALREADY_SATISFIED, ALREADY_COMMITTED, STALE, UNAVAILABLE, INVALID_REQUEST }

    public record ProgressResult(Status status, CampaignProgression.Entry entry, java.util.List<String> blockers) {
        public ProgressResult {
            blockers = java.util.List.copyOf(blockers);
        }
    }

    public record CommitResult(Status status, long worldRevision, CampaignInstance.OutcomeCommit committed, String diagnostic) {
    }
}
