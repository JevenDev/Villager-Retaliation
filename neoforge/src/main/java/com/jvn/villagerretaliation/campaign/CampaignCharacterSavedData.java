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
    public static final int CURRENT_DATA_VERSION = 2;
    static final String DATA_NAME = "villagerretaliation_campaign_characters";
    private final Path directory;
    private Map<CampaignCharacterBinding.Key, CampaignCharacterBinding> bindings = Map.of();
    private Map<UUID, CampaignCharacterBinding> byEntity = Map.of();
    private Map<CampaignCharacterBinding.Key, CampaignCharacterReplacement> replacements = Map.of();
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
            CampaignStateCodec.require(tag.contains("DataVersion", Tag.TAG_INT) && tag.getInt("DataVersion") >= 1
                    && tag.getInt("DataVersion") <= CURRENT_DATA_VERSION, "unsupported campaign character save version");
            var migrated = tag.copy();
            if (migrated.getInt("DataVersion") == 1) {
                CampaignStateCodec.require(!migrated.contains("Replacements"), "legacy character save contains unsupported replacements");
                migrated.put("Replacements", new ListTag());
                migrated.putInt("DataVersion", CURRENT_DATA_VERSION);
            }
            var bindings = new LinkedHashMap<CampaignCharacterBinding.Key, CampaignCharacterBinding>();
            var owners = new LinkedHashMap<ResourceLocation, CampaignCharacterBinding>();
            var entities = new LinkedHashMap<UUID, CampaignCharacterBinding>();
            for (var raw : CampaignStateCodec.list(tag, "Bindings", Tag.TAG_COMPOUND)) {
                var binding = CampaignCharacterBinding.load((CompoundTag) raw);
                CampaignStateCodec.require(tag.getInt("DataVersion") != 1 || binding.lossPolicy()
                        != com.jvn.villagerretaliation.campaign.content.CampaignCharacterDefinition.LossPolicy.REPLACEMENT,
                        "legacy character save contains an unsupported replacement policy");
                var owner = owners.putIfAbsent(binding.campaign(), binding);
                CampaignStateCodec.require(owner == null || owner.instance().equals(binding.instance())
                        && owner.definitionRevision() == binding.definitionRevision() && owner.fingerprint().equals(binding.fingerprint()),
                        "conflicting campaign character ownership");
                CampaignStateCodec.require(entities.putIfAbsent(binding.entityId(), binding) == null, "entity belongs to multiple campaign roles");
                CampaignStateCodec.require(bindings.putIfAbsent(binding.key(), binding) == null, "duplicate campaign character binding");
            }
            var replacements = new LinkedHashMap<CampaignCharacterBinding.Key, CampaignCharacterReplacement>();
            for (var raw : CampaignStateCodec.list(migrated, "Replacements", Tag.TAG_COMPOUND)) {
                var replacement = CampaignCharacterReplacement.load((CompoundTag) raw);
                CampaignStateCodec.require(replacements.putIfAbsent(replacement.key(), replacement) == null, "duplicate character replacement");
            }
            validateReplacements(bindings, replacements);
            data.bindings = immutable(bindings);
            data.byEntity = Map.copyOf(entities);
            data.replacements = immutable(replacements);
        } catch (IllegalArgumentException exception) {
            data.preservedData = tag.copy();
            data.diagnostic = exception.getMessage() + ". Restore compatible character data or a known-good backup.";
        }
        return data;
    }

    public synchronized String diagnostic() { return diagnostic; }
    public synchronized Map<CampaignCharacterBinding.Key, CampaignCharacterBinding> bindings() { return bindings; }

    public synchronized CampaignCharacterBinding forEntity(UUID entity) { return byEntity.get(entity); }
    public synchronized Map<CampaignCharacterBinding.Key, CampaignCharacterReplacement> replacements() { return replacements; }

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
        if (!lost && replacements.containsKey(prior.key())) throw new IOException("This character already has a saved replacement decision.");
        var next = new LinkedHashMap<>(bindings);
        next.put(prior.key(), prior.withLoss(lost));
        persist(next);
    }

    synchronized CampaignCharacterReplacement prepareReplacement(CampaignCharacterBinding original, String role, long time) throws IOException {
        if (!diagnostic.isBlank()) throw new IOException(diagnostic);
        var candidate = CampaignCharacterReplacement.prepare(original, role, time);
        candidate.validate(bindings);
        var prior = replacements.get(original.key());
        if (prior != null) {
            if (!prior.operation().equals(candidate.operation()) || !prior.originalEntity().equals(candidate.originalEntity())
                    || !prior.replacementRole().equals(role)) throw new IllegalArgumentException("Conflicting character replacement decision.");
            return prior;
        }
        var next = new LinkedHashMap<>(replacements);
        next.put(candidate.key(), candidate);
        persist(bindings, next);
        return candidate;
    }

    synchronized CampaignCharacterReplacement commitReplacement(CampaignCharacterBinding.Key key, CampaignCharacterBinding target, long time) throws IOException {
        if (!diagnostic.isBlank()) throw new IOException(diagnostic);
        var prior = replacements.get(key);
        if (prior == null || !prior.target().equals(target.key())) throw new IllegalArgumentException("Replacement must use its prepared successor role.");
        if (prior.committed()) {
            if (!prior.replacementEntity().equals(target.entityId())) throw new IllegalArgumentException("Replacement already belongs to another entity.");
            return prior;
        }
        var committed = prior.commit(target.entityId(), time);
        var next = new LinkedHashMap<>(replacements);
        next.put(key, committed);
        persist(bindings, next);
        return committed;
    }

    private static void validateReplacements(Map<CampaignCharacterBinding.Key, CampaignCharacterBinding> bindings,
            Map<CampaignCharacterBinding.Key, CampaignCharacterReplacement> replacements) {
        for (var receipt : replacements.values()) {
            receipt.validate(bindings);
            var visited = new java.util.HashSet<CampaignCharacterBinding.Key>();
            var cursor = receipt;
            while (cursor != null) {
                CampaignStateCodec.require(visited.add(cursor.key()), "saved character replacements contain a cycle");
                cursor = replacements.get(cursor.target());
            }
        }
    }

    private void persist(Map<CampaignCharacterBinding.Key, CampaignCharacterBinding> next) throws IOException {
        persist(next, replacements);
    }

    private void persist(Map<CampaignCharacterBinding.Key, CampaignCharacterBinding> next,
            Map<CampaignCharacterBinding.Key, CampaignCharacterReplacement> nextReplacements) throws IOException {
        validateReplacements(next, nextReplacements);
        var wrapper = new CompoundTag();
        wrapper.put("data", encode(next, nextReplacements));
        NbtUtils.addCurrentDataVersion(wrapper);
        IOUtilities.waitUntilIOWorkerComplete();
        Files.createDirectories(directory);
        IOUtilities.writeNbtCompressed(wrapper, directory.resolve(DATA_NAME + ".dat"));
        bindings = immutable(next);
        var entities = new LinkedHashMap<UUID, CampaignCharacterBinding>();
        next.values().forEach(binding -> entities.put(binding.entityId(), binding));
        byEntity = Map.copyOf(entities);
        replacements = immutable(nextReplacements);
        setDirty(false);
    }

    private static <T> Map<CampaignCharacterBinding.Key, T> immutable(Map<CampaignCharacterBinding.Key, T> values) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    private static CompoundTag encode(Map<CampaignCharacterBinding.Key, CampaignCharacterBinding> bindings,
            Map<CampaignCharacterBinding.Key, CampaignCharacterReplacement> replacements) {
        var tag = new CompoundTag();
        tag.putInt("DataVersion", CURRENT_DATA_VERSION);
        var list = new ListTag();
        bindings.values().forEach(binding -> list.add(binding.save()));
        tag.put("Bindings", list);
        var receipts = new ListTag();
        replacements.values().forEach(receipt -> receipts.add(receipt.save()));
        tag.put("Replacements", receipts);
        return tag;
    }

    @Override
    public synchronized CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) { return preservedData == null ? encode(bindings, replacements) : preservedData.copy(); }

    @Override
    public synchronized void save(File file, HolderLookup.Provider provider) {
        if (diagnostic.isBlank()) super.save(file, provider);
    }
}
