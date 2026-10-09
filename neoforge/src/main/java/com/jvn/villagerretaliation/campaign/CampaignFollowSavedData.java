package com.jvn.villagerretaliation.campaign;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.common.IOUtilities;

public final class CampaignFollowSavedData extends SavedData {
    public static final int CURRENT_DATA_VERSION = 1;
    static final String DATA_NAME = "villagerretaliation_campaign_follow";
    private final Path directory;
    private Map<CampaignFollowJourney.Key, CampaignFollowJourney> journeys = Map.of();
    private String diagnostic = "";
    private CompoundTag preserved;

    private CampaignFollowSavedData(Path directory) { this.directory = directory; }

    public static CampaignFollowSavedData get(ServerLevel level) {
        var directory = level.getServer().getWorldPath(LevelResource.ROOT).resolve("data");
        return level.getServer().overworld().getDataStorage().computeIfAbsent(new Factory<>(() -> missing(directory),
                (tag, provider) -> load(tag, directory), DataFixTypes.LEVEL), DATA_NAME);
    }

    private static CampaignFollowSavedData missing(Path directory) {
        var data = new CampaignFollowSavedData(directory);
        if (!Files.notExists(directory.resolve(DATA_NAME + ".dat")))
            data.diagnostic = "Campaign journeys could not be read. Restore " + DATA_NAME + ".dat from a known-good backup.";
        return data;
    }

    static CampaignFollowSavedData load(CompoundTag tag, Path directory) {
        var data = new CampaignFollowSavedData(directory);
        if (tag.isEmpty() && Files.notExists(directory.resolve(DATA_NAME + ".dat"))) return data;
        try {
            CampaignStateCodec.require(tag.contains("DataVersion", Tag.TAG_INT) && tag.getInt("DataVersion") == CURRENT_DATA_VERSION,
                    "unsupported campaign journey save version");
            var journeys = new LinkedHashMap<CampaignFollowJourney.Key, CampaignFollowJourney>();
            for (var raw : CampaignStateCodec.list(tag, "Journeys", Tag.TAG_COMPOUND)) {
                var journey = CampaignFollowJourney.load((CompoundTag) raw);
                CampaignStateCodec.require(journeys.putIfAbsent(journey.key(), journey) == null, "duplicate campaign journey");
            }
            validate(journeys);
            data.journeys = Collections.unmodifiableMap(journeys);
        } catch (IllegalArgumentException exception) {
            data.preserved = tag.copy();
            data.diagnostic = exception.getMessage() + ". Restore compatible journey data or a known-good backup.";
        }
        return data;
    }

    public synchronized String diagnostic() { return diagnostic; }
    public synchronized Map<CampaignFollowJourney.Key, CampaignFollowJourney> journeys() { return journeys; }

    synchronized void record(CampaignFollowJourney candidate) throws IOException {
        if (!diagnostic.isBlank()) throw new IOException(diagnostic);
        var prior = journeys.get(candidate.key());
        if (candidate.equals(prior)) return;
        CampaignStateCodec.require(prior == null || prior.sameIdentity(candidate), "follow journey identity changed");
        CampaignStateCodec.require(prior == null || prior.state() == CampaignFollowJourney.State.ACTIVE, "resolved journey cannot change");
        CampaignStateCodec.require(prior == null || candidate.observedAt() >= prior.observedAt() && candidate.distance() >= prior.distance()
                && candidate.participants().entrySet().stream().allMatch(entry -> entry.getValue().distance() >= prior.participants().get(entry.getKey()).distance()),
                "follow journey observations moved backwards");
        persist(candidate);
    }

    synchronized CampaignFollowJourney retarget(CampaignFollowJourney prior, CampaignLocationBinding location, CampaignLocationSavedData locations) throws IOException {
        if (!diagnostic.isBlank()) throw new IOException(diagnostic);
        CampaignStateCodec.require(prior.equals(journeys.get(prior.key())), "follow journey changed before location repair");
        if (prior.destination().equals(location.position()) || prior.state() != CampaignFollowJourney.State.ACTIVE) return prior;
        CampaignStateCodec.require(prior.campaign().equals(location.campaign()) && prior.instance().equals(location.instance())
                && prior.fingerprint().equals(location.fingerprint()) && prior.location().equals(location.alias())
                && prior.dimension().equals(location.dimension())
                && !locations.authorization(location, prior.dimension(), prior.destination()).isEmpty(), "follow destination has no campaign repair authorization");
        var candidate = prior.retargeted(location.position());
        persist(candidate);
        return candidate;
    }

    private void persist(CampaignFollowJourney candidate) throws IOException {
        var next = new LinkedHashMap<>(journeys);
        next.put(candidate.key(), candidate);
        validate(next);
        var wrapper = new CompoundTag();
        wrapper.put("data", encode(next));
        NbtUtils.addCurrentDataVersion(wrapper);
        IOUtilities.waitUntilIOWorkerComplete();
        Files.createDirectories(directory);
        IOUtilities.writeNbtCompressed(wrapper, directory.resolve(DATA_NAME + ".dat"));
        journeys = Collections.unmodifiableMap(next);
        setDirty(false);
    }

    private static void validate(Map<CampaignFollowJourney.Key, CampaignFollowJourney> journeys) {
        var active = new java.util.HashSet<UUID>();
        for (var journey : journeys.values()) {
            if (journey.state() == CampaignFollowJourney.State.ACTIVE)
                CampaignStateCodec.require(active.add(journey.actor()), "character has conflicting active follow journeys");
        }
    }

    private static CompoundTag encode(Map<CampaignFollowJourney.Key, CampaignFollowJourney> journeys) {
        var tag = new CompoundTag();
        tag.putInt("DataVersion", CURRENT_DATA_VERSION);
        var entries = new ListTag();
        journeys.values().forEach(journey -> entries.add(journey.save()));
        tag.put("Journeys", entries);
        return tag;
    }

    @Override public synchronized CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        return preserved == null ? encode(journeys) : preserved.copy();
    }

    @Override public synchronized void save(File file, HolderLookup.Provider provider) { if (diagnostic.isBlank()) super.save(file, provider); }
}
