package com.jvn.villagerretaliation.campaign;

import net.minecraft.resources.ResourceLocation;
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

public final class CampaignCharacterSavedData extends SavedData {
    public static final int CURRENT_DATA_VERSION = 1;
    static final String DATA_NAME = "villagerretaliation_campaign_characters";
    private final Path directory;
    private Map<CampaignCharacterBinding.Key, CampaignCharacterBinding> bindings = Map.of();
    private Map<UUID, CampaignCharacterBinding> byEntity = Map.of();
    private String diagnostic = "";
    private CompoundTag preservedData;

    private CampaignCharacterSavedData(Path directory) { this.directory = directory; }

    public static CampaignCharacterSavedData get(ServerLevel level) {
        Path directory = level.getServer().getWorldPath(LevelResource.ROOT).resolve("data");
        return level.getServer().overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(() -> missing(directory),
                (tag, provider) -> load(tag, directory), DataFixTypes.LEVEL), DATA_NAME);
    }

    private static CampaignCharacterSavedData missing(Path directory) {
        var data = new CampaignCharacterSavedData(directory);
        if (!Files.notExists(directory.resolve(DATA_NAME + ".dat"))) data.diagnostic = "Campaign character save could not be read. Restore " + DATA_NAME + ".dat from a known-good backup.";
        return data;
    }

    static CampaignCharacterSavedData load(CompoundTag tag, Path directory) {
        var data = new CampaignCharacterSavedData(directory);
        if (tag.isEmpty() && Files.notExists(directory.resolve(DATA_NAME + ".dat"))) return data;
        try {
            CampaignStateCodec.require(tag.contains("DataVersion", Tag.TAG_INT) && tag.getInt("DataVersion") == CURRENT_DATA_VERSION, "unsupported campaign character save version");
            var bindings = new LinkedHashMap<CampaignCharacterBinding.Key, CampaignCharacterBinding>();
            var owners = new LinkedHashMap<ResourceLocation, CampaignCharacterBinding>();
            var entities = new LinkedHashMap<UUID, CampaignCharacterBinding>();
            for (var raw : CampaignStateCodec.list(tag, "Bindings", Tag.TAG_COMPOUND)) {
                var binding = CampaignCharacterBinding.load((CompoundTag) raw);
                var owner = owners.putIfAbsent(binding.campaign(), binding);
                CampaignStateCodec.require(owner == null || owner.instance().equals(binding.instance())
                        && owner.definitionRevision() == binding.definitionRevision() && owner.fingerprint().equals(binding.fingerprint()),
                        "conflicting campaign character ownership");
                CampaignStateCodec.require(entities.putIfAbsent(binding.entityId(), binding) == null, "entity belongs to multiple campaign roles");
                CampaignStateCodec.require(bindings.putIfAbsent(binding.key(), binding) == null, "duplicate campaign character binding");
            }
            data.bindings = immutable(bindings);
            data.byEntity = Map.copyOf(entities);
        } catch (IllegalArgumentException exception) {
            data.preservedData = tag.copy();
            data.diagnostic = exception.getMessage() + ". Restore compatible character data or a known-good backup.";
        }
        return data;
    }

    public synchronized String diagnostic() { return diagnostic; }
    public synchronized Map<CampaignCharacterBinding.Key, CampaignCharacterBinding> bindings() { return bindings; }

    public synchronized CampaignCharacterBinding forEntity(UUID entity) { return byEntity.get(entity); }

    synchronized CampaignCharacterBinding bind(CampaignCharacterBinding candidate) throws IOException {
        if (!diagnostic.isBlank()) throw new IOException(diagnostic);
        var prior = bindings.get(candidate.key());
        if (prior != null) {
            if (!prior.sameOwner(candidate)) throw new IllegalArgumentException("Campaign character already belongs to a different campaign instance or definition.");
            return prior;
        }
        if (byEntity.containsKey(candidate.entityId())) throw new IllegalArgumentException("This character already belongs to a campaign role.");
        for (var binding : bindings.values()) {
            if (binding.campaign().equals(candidate.campaign()) && (!binding.instance().equals(candidate.instance())
                    || binding.definitionRevision() != candidate.definitionRevision() || !binding.fingerprint().equals(candidate.fingerprint()))) {
                throw new IllegalArgumentException("Campaign character ownership does not match its other saved roles.");
            }
        }
        var next = new LinkedHashMap<>(bindings);
        next.put(candidate.key(), candidate);
        persist(next);
        return candidate;
    }

    synchronized void recordLoss(UUID entity, boolean lost) throws IOException {
        if (!diagnostic.isBlank()) throw new IOException(diagnostic);
        var prior = byEntity.get(entity);
        if (prior == null || prior.lost() == lost) return;
        var next = new LinkedHashMap<>(bindings);
        next.put(prior.key(), prior.withLoss(lost));
        persist(next);
    }

    private void persist(Map<CampaignCharacterBinding.Key, CampaignCharacterBinding> next) throws IOException {
        var wrapper = new CompoundTag();
        wrapper.put("data", encode(next));
        NbtUtils.addCurrentDataVersion(wrapper);
        IOUtilities.waitUntilIOWorkerComplete();
        Files.createDirectories(directory);
        IOUtilities.writeNbtCompressed(wrapper, directory.resolve(DATA_NAME + ".dat"));
        bindings = immutable(next);
        var entities = new LinkedHashMap<UUID, CampaignCharacterBinding>();
        next.values().forEach(binding -> entities.put(binding.entityId(), binding));
        byEntity = Map.copyOf(entities);
        setDirty(false);
    }

    private static Map<CampaignCharacterBinding.Key, CampaignCharacterBinding> immutable(Map<CampaignCharacterBinding.Key, CampaignCharacterBinding> values) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    private static CompoundTag encode(Map<CampaignCharacterBinding.Key, CampaignCharacterBinding> bindings) {
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
