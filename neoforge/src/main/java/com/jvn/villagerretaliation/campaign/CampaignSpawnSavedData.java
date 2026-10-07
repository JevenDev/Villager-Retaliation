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

public final class CampaignSpawnSavedData extends SavedData {
    public static final int CURRENT_DATA_VERSION = 1;
    static final String DATA_NAME = "villagerretaliation_campaign_spawns";
    private final Path directory;
    private Map<CampaignCharacterBinding.Key, CampaignSpawnReceipt> receipts = Map.of();
    private Map<UUID, CampaignSpawnReceipt> byEntity = Map.of();
    private String diagnostic = "";
    private CompoundTag preservedData;

    private CampaignSpawnSavedData(Path directory) { this.directory = directory; }

    public static CampaignSpawnSavedData get(ServerLevel level) {
        Path directory = level.getServer().getWorldPath(LevelResource.ROOT).resolve("data");
        return level.getServer().overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(() -> missing(directory),
                (tag, provider) -> load(tag, directory), DataFixTypes.LEVEL), DATA_NAME);
    }

    private static CampaignSpawnSavedData missing(Path directory) {
        var data = new CampaignSpawnSavedData(directory);
        if (!Files.notExists(directory.resolve(DATA_NAME + ".dat"))) data.diagnostic = "Campaign spawn save could not be read. Restore " + DATA_NAME + ".dat from a known-good backup.";
        return data;
    }

    static CampaignSpawnSavedData load(CompoundTag tag, Path directory) {
        var data = new CampaignSpawnSavedData(directory);
        if (tag.isEmpty() && Files.notExists(directory.resolve(DATA_NAME + ".dat"))) return data;
        try {
            CampaignStateCodec.require(tag.contains("DataVersion", Tag.TAG_INT) && tag.getInt("DataVersion") == CURRENT_DATA_VERSION,
                    "unsupported campaign spawn save version");
            var receipts = new LinkedHashMap<CampaignCharacterBinding.Key, CampaignSpawnReceipt>();
            var entities = new LinkedHashMap<UUID, CampaignSpawnReceipt>();
            var operations = new java.util.HashSet<UUID>();
            var owners = new LinkedHashMap<net.minecraft.resources.ResourceLocation, CampaignCharacterBinding>();
            for (var raw : CampaignStateCodec.list(tag, "Receipts", Tag.TAG_COMPOUND)) {
                var receipt = CampaignSpawnReceipt.load((CompoundTag) raw);
                var binding = receipt.binding();
                var owner = owners.putIfAbsent(binding.campaign(), binding);
                CampaignStateCodec.require(owner == null || owner.instance().equals(binding.instance())
                        && owner.definitionRevision() == binding.definitionRevision() && owner.fingerprint().equals(binding.fingerprint()),
                        "conflicting campaign spawn ownership");
                CampaignStateCodec.require(receipts.putIfAbsent(receipt.key(), receipt) == null
                        && entities.putIfAbsent(binding.entityId(), receipt) == null && operations.add(receipt.operation()),
                        "duplicate campaign spawn identity or operation");
            }
            data.install(receipts);
        } catch (IllegalArgumentException exception) {
            data.preservedData = tag.copy();
            data.diagnostic = exception.getMessage() + ". Restore compatible spawn data or a known-good backup.";
        }
        return data;
    }

    public synchronized String diagnostic() { return diagnostic; }
    public synchronized Map<CampaignCharacterBinding.Key, CampaignSpawnReceipt> receipts() { return receipts; }
    public synchronized CampaignSpawnReceipt forEntity(UUID entity) { return byEntity.get(entity); }

    synchronized CampaignSpawnReceipt prepare(CampaignSpawnReceipt candidate) throws IOException {
        if (!diagnostic.isBlank()) throw new IOException(diagnostic);
        if (candidate.committed()) throw new IllegalArgumentException("New campaign spawns must be prepared before commitment.");
        var prior = receipts.get(candidate.key());
        if (prior != null) {
            if (!prior.binding().sameOwner(candidate.binding())) throw new IllegalArgumentException("Campaign spawn already belongs to another definition or instance.");
            return prior;
        }
        for (var receipt : receipts.values()) {
            var binding = receipt.binding();
            var next = candidate.binding();
            if (binding.entityId().equals(next.entityId()) || receipt.operation().equals(candidate.operation())) {
                throw new IllegalArgumentException("Campaign spawn identity or operation is already reserved.");
            }
            if (binding.campaign().equals(next.campaign()) && (!binding.instance().equals(next.instance())
                    || binding.definitionRevision() != next.definitionRevision() || !binding.fingerprint().equals(next.fingerprint()))) {
                throw new IllegalArgumentException("Campaign spawn ownership conflicts with its other roles.");
            }
        }
        var next = new LinkedHashMap<>(receipts);
        next.put(candidate.key(), candidate);
        persist(next);
        return candidate;
    }

    synchronized CampaignSpawnReceipt commit(CampaignCharacterBinding.Key key, UUID operation, long time) throws IOException {
        if (!diagnostic.isBlank()) throw new IOException(diagnostic);
        var receipt = receipts.get(key);
        if (receipt == null || !receipt.operation().equals(operation)) throw new IllegalArgumentException("Campaign spawn operation does not match its prepared receipt.");
        if (receipt.committed()) return receipt;
        var next = new LinkedHashMap<>(receipts);
        var committed = receipt.commit(time);
        next.put(key, committed);
        persist(next);
        return committed;
    }

    private void persist(Map<CampaignCharacterBinding.Key, CampaignSpawnReceipt> next) throws IOException {
        var wrapper = new CompoundTag();
        wrapper.put("data", encode(next));
        NbtUtils.addCurrentDataVersion(wrapper);
        IOUtilities.waitUntilIOWorkerComplete();
        Files.createDirectories(directory);
        IOUtilities.writeNbtCompressed(wrapper, directory.resolve(DATA_NAME + ".dat"));
        install(next);
        setDirty(false);
    }

    private void install(Map<CampaignCharacterBinding.Key, CampaignSpawnReceipt> next) {
        receipts = Collections.unmodifiableMap(new LinkedHashMap<>(next));
        var entities = new LinkedHashMap<UUID, CampaignSpawnReceipt>();
        receipts.values().forEach(receipt -> entities.put(receipt.binding().entityId(), receipt));
        byEntity = Map.copyOf(entities);
    }

    private static CompoundTag encode(Map<CampaignCharacterBinding.Key, CampaignSpawnReceipt> receipts) {
        var tag = new CompoundTag();
        tag.putInt("DataVersion", CURRENT_DATA_VERSION);
        var list = new ListTag();
        receipts.values().forEach(receipt -> list.add(receipt.save()));
        tag.put("Receipts", list);
        return tag;
    }

    @Override
    public synchronized CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) { return preservedData == null ? encode(receipts) : preservedData.copy(); }

    @Override
    public synchronized void save(File file, HolderLookup.Provider provider) { if (diagnostic.isBlank()) super.save(file, provider); }
}
