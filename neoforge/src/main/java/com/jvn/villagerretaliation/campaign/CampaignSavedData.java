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
    public static final int CURRENT_DATA_VERSION = 1;
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
            if (tag.isEmpty()) return data;
            CampaignStateCodec.require(tag.contains("DataVersion", Tag.TAG_INT)
                    && tag.getInt("DataVersion") == CURRENT_DATA_VERSION, "unsupported campaign save version");
            Map<ResourceLocation, CampaignInstance> instances = new LinkedHashMap<>();
            Set<UUID> identities = new HashSet<>();
            for (Tag raw : CampaignStateCodec.list(tag, "Campaigns", Tag.TAG_COMPOUND)) {
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
        for (UUID participant : participants) {
            var previous = players.getOrDefault(participant, CampaignInstance.PlayerRecord.empty());
            Set<String> participation = new HashSet<>(previous.participation());
            participation.add(milestoneId);
            Map<String, String> discoveries = new LinkedHashMap<>(previous.discoveredOutcomes());
            discoveries.put(milestoneId, outcomeId);
            players.put(participant, new CampaignInstance.PlayerRecord(previous.revision() + 1L, participation, discoveries));
        }
        var next = replace(current, committed.revision(), outcomes, players);
        persist(next);
        return result(Status.APPLIED, next, committed, "");
    }

    synchronized boolean discover(CampaignDefinition definition, UUID playerId, String milestoneId) throws IOException {
        String issue = compatibility(definition);
        if (!issue.isBlank()) throw new IOException(issue);
        var current = this.instances.get(definition.id());
        if (current == null || !current.outcomes().containsKey(milestoneId)) return false;
        var player = current.players().getOrDefault(playerId, CampaignInstance.PlayerRecord.empty());
        if (player.discoveredOutcomes().containsKey(milestoneId)) return false;
        Map<String, String> discoveries = new LinkedHashMap<>(player.discoveredOutcomes());
        discoveries.put(milestoneId, current.outcomes().get(milestoneId).outcome());
        Map<UUID, CampaignInstance.PlayerRecord> players = new LinkedHashMap<>(current.players());
        players.put(playerId, new CampaignInstance.PlayerRecord(player.revision() + 1L, player.participation(), discoveries));
        persist(replace(current, current.worldRevision(), current.outcomes(), players));
        return true;
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

    public enum Status { APPLIED, ALREADY_COMMITTED, STALE, UNAVAILABLE, INVALID_REQUEST }

    public record CommitResult(Status status, long worldRevision, CampaignInstance.OutcomeCommit committed, String diagnostic) {
    }
}
