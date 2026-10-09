package com.jvn.villagerretaliation.campaign;

import net.minecraft.resources.ResourceLocation;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
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

public final class CampaignLocationSavedData extends SavedData {
    public static final int CURRENT_DATA_VERSION = 2;
    static final String DATA_NAME = "villagerretaliation_campaign_locations";
    private final Path directory;
    private Map<CampaignLocationBinding.Key, CampaignLocationBinding> bindings = Map.of();
    private Map<UUID, CampaignLocationRepair> repairs = Map.of();
    private String diagnostic = "";
    private CompoundTag preservedData;

    private CampaignLocationSavedData(Path directory) { this.directory = directory; }

    public static CampaignLocationSavedData get(ServerLevel level) {
        Path directory = level.getServer().getWorldPath(LevelResource.ROOT).resolve("data");
        return level.getServer().overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(() -> missing(directory),
                (tag, provider) -> load(tag, directory), DataFixTypes.LEVEL), DATA_NAME);
    }

    private static CampaignLocationSavedData missing(Path directory) {
        var data = new CampaignLocationSavedData(directory);
        if (!Files.notExists(directory.resolve(DATA_NAME + ".dat"))) data.diagnostic = "Campaign location save could not be read. Restore " + DATA_NAME + ".dat from a known-good backup.";
        return data;
    }

    static CampaignLocationSavedData load(CompoundTag tag, Path directory) {
        var data = new CampaignLocationSavedData(directory);
        if (tag.isEmpty() && Files.notExists(directory.resolve(DATA_NAME + ".dat"))) return data;
        try {
            CampaignStateCodec.require(tag.contains("DataVersion", Tag.TAG_INT) && tag.getInt("DataVersion") >= 1
                    && tag.getInt("DataVersion") <= CURRENT_DATA_VERSION, "unsupported campaign location save version");
            CampaignStateCodec.require(tag.getInt("DataVersion") != 1 || !tag.contains("Repairs"), "legacy location save contains unsupported repairs");
            var bindings = new LinkedHashMap<CampaignLocationBinding.Key, CampaignLocationBinding>();
            var owners = new LinkedHashMap<ResourceLocation, CampaignLocationBinding>();
            for (var raw : CampaignStateCodec.list(tag, "Bindings", Tag.TAG_COMPOUND)) {
                var binding = CampaignLocationBinding.load((CompoundTag) raw);
                var owner = owners.putIfAbsent(binding.campaign(), binding);
                CampaignStateCodec.require(owner == null || owner.instance().equals(binding.instance())
                        && owner.definitionRevision() == binding.definitionRevision() && owner.fingerprint().equals(binding.fingerprint()),
                        "conflicting campaign location ownership");
                CampaignStateCodec.require(bindings.putIfAbsent(binding.key(), binding) == null, "duplicate campaign location binding");
            }
            var repairs = new LinkedHashMap<UUID, CampaignLocationRepair>();
            if (tag.getInt("DataVersion") >= 2) for (var raw : CampaignStateCodec.list(tag, "Repairs", Tag.TAG_COMPOUND)) {
                var repair = CampaignLocationRepair.load((CompoundTag) raw);
                CampaignStateCodec.require(repairs.putIfAbsent(repair.operation(), repair) == null, "duplicate location repair operation");
            }
            validateRepairs(bindings, repairs);
            data.bindings = immutable(bindings);
            data.repairs = immutable(repairs);
        } catch (IllegalArgumentException exception) {
            data.preservedData = tag.copy();
            data.diagnostic = exception.getMessage() + ". Restore compatible location data or a known-good backup.";
        }
        return data;
    }

    public synchronized String diagnostic() { return diagnostic; }
    public synchronized Map<CampaignLocationBinding.Key, CampaignLocationBinding> bindings() { return bindings; }

    public synchronized Map<UUID, CampaignLocationRepair> repairs() { return repairs; }

    public synchronized UUID token(CampaignLocationBinding.Key key) {
        UUID token = CampaignLocationRepair.token(bindings.get(key));
        for (var repair : repairs.values()) if (repair.after().key().equals(key)) token = repair.token();
        return token;
    }

    public synchronized boolean knows(CampaignLocationBinding binding) {
        return diagnostic.isBlank() && binding != null && (binding.equals(bindings.get(binding.key()))
                || repairs.values().stream().anyMatch(repair -> binding.equals(repair.before()) || binding.equals(repair.after())));
    }

    public synchronized List<CampaignLocationRepair> authorization(CampaignLocationBinding current, ResourceLocation dimension, BlockPos position) {
        if (!diagnostic.isBlank() || current == null || !current.equals(bindings.get(current.key())) || dimension == null || position == null)
            return List.of();
        var chain = new ArrayList<CampaignLocationRepair>();
        for (var repair : repairs.values()) {
            if (!repair.after().key().equals(current.key())) continue;
            if (chain.isEmpty()) {
                var before = repair.before();
                if (before == null || !before.dimension().equals(dimension) || !before.position().equals(position)) continue;
            }
            chain.add(repair);
        }
        return !chain.isEmpty() && chain.getLast().after().equals(current) ? List.copyOf(chain) : List.of();
    }

    synchronized CampaignLocationRepair repair(UUID operation, UUID expected, CampaignLocationBinding candidate,
            UUID operator, String reason, long time) throws IOException {
        if (!diagnostic.isBlank()) throw new IOException(diagnostic);
        CampaignStateCodec.require(operation != null && expected != null && candidate != null && operator != null
                && reason != null && !reason.isBlank() && reason.length() <= 256, "location repair request is incomplete");
        var priorRepair = repairs.get(operation);
        if (priorRepair != null) {
            if (!priorRepair.sameRequest(expected, candidate, operator, reason)) throw new IllegalArgumentException("Repair operation already belongs to another request.");
            return priorRepair;
        }
        var prior = bindings.get(candidate.key());
        if (!token(candidate.key()).equals(expected)) throw new IllegalArgumentException("Location binding changed. Inspect its current token before repairing.");
        if (prior != null && !prior.sameOwner(candidate)) throw new IllegalArgumentException("Location repair cannot change campaign ownership or dimension.");
        validateOwner(candidate);
        var repair = new CampaignLocationRepair(operation, expected, prior, candidate, operator, reason, time);
        var next = new LinkedHashMap<>(bindings);
        next.put(candidate.key(), candidate);
        var nextRepairs = new LinkedHashMap<>(repairs);
        nextRepairs.put(operation, repair);
        persist(next, nextRepairs);
        return repair;
    }

    synchronized CampaignLocationBinding bind(CampaignLocationBinding candidate) throws IOException {
        if (!diagnostic.isBlank()) throw new IOException(diagnostic);
        var prior = bindings.get(candidate.key());
        if (prior != null) {
            if (!prior.sameOwner(candidate)) throw new IllegalArgumentException("Campaign location already belongs to a different campaign instance or definition.");
            return prior;
        }
        validateOwner(candidate);
        var next = new LinkedHashMap<>(bindings);
        next.put(candidate.key(), candidate);
        persist(next, repairs);
        return candidate;
    }

    private void validateOwner(CampaignLocationBinding candidate) {
        for (var binding : bindings.values()) {
            if (binding.campaign().equals(candidate.campaign()) && (!binding.instance().equals(candidate.instance())
                    || binding.definitionRevision() != candidate.definitionRevision() || !binding.fingerprint().equals(candidate.fingerprint()))) {
                throw new IllegalArgumentException("Campaign location ownership does not match its other saved locations.");
            }
        }
    }

    private void persist(Map<CampaignLocationBinding.Key, CampaignLocationBinding> next, Map<UUID, CampaignLocationRepair> nextRepairs) throws IOException {
        validateRepairs(next, nextRepairs);
        var wrapper = new CompoundTag();
        wrapper.put("data", encode(next, nextRepairs));
        NbtUtils.addCurrentDataVersion(wrapper);
        IOUtilities.waitUntilIOWorkerComplete();
        Files.createDirectories(directory);
        IOUtilities.writeNbtCompressed(wrapper, directory.resolve(DATA_NAME + ".dat"));
        bindings = immutable(next);
        repairs = immutable(nextRepairs);
        setDirty(false);
    }

    private static void validateRepairs(Map<CampaignLocationBinding.Key, CampaignLocationBinding> bindings, Map<UUID, CampaignLocationRepair> repairs) {
        var latest = new LinkedHashMap<CampaignLocationBinding.Key, CampaignLocationRepair>();
        for (var repair : repairs.values()) {
            var key = repair.after().key();
            var prior = latest.get(key);
            CampaignStateCodec.require(prior == null || Objects.equals(prior.after(), repair.before()), "location repair history is discontinuous");
            UUID expected = prior == null ? CampaignLocationRepair.token(repair.before()) : prior.token();
            CampaignStateCodec.require(expected.equals(repair.expected()), "location repair token does not match its history");
            latest.put(key, repair);
        }
        latest.forEach((key, repair) -> CampaignStateCodec.require(repair.after().equals(bindings.get(key)), "location repair does not match its current binding"));
    }

    private static <K, V> Map<K, V> immutable(Map<K, V> values) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    private static CompoundTag encode(Map<CampaignLocationBinding.Key, CampaignLocationBinding> bindings, Map<UUID, CampaignLocationRepair> repairs) {
        var tag = new CompoundTag();
        tag.putInt("DataVersion", CURRENT_DATA_VERSION);
        var list = new ListTag();
        bindings.values().forEach(binding -> list.add(binding.save()));
        tag.put("Bindings", list);
        var history = new ListTag();
        repairs.values().forEach(repair -> history.add(repair.save()));
        tag.put("Repairs", history);
        return tag;
    }

    @Override
    public synchronized CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) { return preservedData == null ? encode(bindings, repairs) : preservedData.copy(); }

    @Override
    public synchronized void save(File file, HolderLookup.Provider provider) {
        if (diagnostic.isBlank()) super.save(file, provider);
    }
}
