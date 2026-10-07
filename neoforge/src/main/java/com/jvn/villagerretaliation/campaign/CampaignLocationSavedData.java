package com.jvn.villagerretaliation.campaign;

import net.minecraft.resources.ResourceLocation;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
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
    public static final int CURRENT_DATA_VERSION = 1;
    static final String DATA_NAME = "villagerretaliation_campaign_locations";
    private final Path directory;
    private Map<CampaignLocationBinding.Key, CampaignLocationBinding> bindings = Map.of();
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
            CampaignStateCodec.require(tag.contains("DataVersion", Tag.TAG_INT) && tag.getInt("DataVersion") == CURRENT_DATA_VERSION, "unsupported campaign location save version");
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
            data.bindings = immutable(bindings);
        } catch (IllegalArgumentException exception) {
            data.preservedData = tag.copy();
            data.diagnostic = exception.getMessage() + ". Restore compatible location data or a known-good backup.";
        }
        return data;
    }

    public synchronized String diagnostic() { return diagnostic; }
    public synchronized Map<CampaignLocationBinding.Key, CampaignLocationBinding> bindings() { return bindings; }

    synchronized CampaignLocationBinding bind(CampaignLocationBinding candidate) throws IOException {
        if (!diagnostic.isBlank()) throw new IOException(diagnostic);
        var prior = bindings.get(candidate.key());
        if (prior != null) {
            if (!prior.sameOwner(candidate)) throw new IllegalArgumentException("Campaign location already belongs to a different campaign instance or definition.");
            return prior;
        }
        for (var binding : bindings.values()) {
            if (binding.campaign().equals(candidate.campaign()) && (!binding.instance().equals(candidate.instance())
                    || binding.definitionRevision() != candidate.definitionRevision() || !binding.fingerprint().equals(candidate.fingerprint()))) {
                throw new IllegalArgumentException("Campaign location ownership does not match its other saved locations.");
            }
        }
        var next = new LinkedHashMap<>(bindings);
        next.put(candidate.key(), candidate);
        var wrapper = new CompoundTag();
        wrapper.put("data", encode(next));
        NbtUtils.addCurrentDataVersion(wrapper);
        IOUtilities.waitUntilIOWorkerComplete();
        Files.createDirectories(directory);
        IOUtilities.writeNbtCompressed(wrapper, directory.resolve(DATA_NAME + ".dat"));
        bindings = immutable(next);
        setDirty(false);
        return candidate;
    }

    private static Map<CampaignLocationBinding.Key, CampaignLocationBinding> immutable(Map<CampaignLocationBinding.Key, CampaignLocationBinding> values) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    private static CompoundTag encode(Map<CampaignLocationBinding.Key, CampaignLocationBinding> bindings) {
        var tag = new CompoundTag();
        tag.putInt("DataVersion", CURRENT_DATA_VERSION);
        var list = new ListTag();
        bindings.values().forEach(binding -> list.add(binding.save()));
        tag.put("Bindings", list);
        return tag;
    }

    @Override
    public synchronized CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) { return preservedData == null ? encode(bindings) : preservedData.copy(); }

    @Override
    public synchronized void save(File file, HolderLookup.Provider provider) {
        if (diagnostic.isBlank()) super.save(file, provider);
    }
}
