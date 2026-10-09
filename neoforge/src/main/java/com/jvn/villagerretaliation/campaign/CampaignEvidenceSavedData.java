package com.jvn.villagerretaliation.campaign;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
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

public final class CampaignEvidenceSavedData extends SavedData {
    public static final int CURRENT_DATA_VERSION = 1;
    static final String DATA_NAME = "villagerretaliation_campaign_evidence";
    private final Path directory;
    private Map<CampaignEvidenceBinding.Key, CampaignEvidenceBinding> bindings = Map.of();
    private Map<CampaignInvestigationReceipt.Key, CampaignInvestigationReceipt> receipts = Map.of();
    private Map<UUID, CampaignEvidenceBinding.Key> entities = Map.of();
    private String diagnostic = "";
    private CompoundTag preserved;

    private CampaignEvidenceSavedData(Path directory) { this.directory = directory; }

    public static CampaignEvidenceSavedData get(ServerLevel level) {
        var directory = level.getServer().getWorldPath(LevelResource.ROOT).resolve("data");
        return level.getServer().overworld().getDataStorage().computeIfAbsent(new Factory<>(() -> missing(directory),
                (tag, provider) -> load(tag, directory), DataFixTypes.LEVEL), DATA_NAME);
    }

    private static CampaignEvidenceSavedData missing(Path directory) {
        var data = new CampaignEvidenceSavedData(directory);
        if (!Files.notExists(directory.resolve(DATA_NAME + ".dat")))
            data.diagnostic = "Campaign evidence could not be read. Restore " + DATA_NAME + ".dat from a known-good backup.";
        return data;
    }

    static CampaignEvidenceSavedData load(CompoundTag tag, Path directory) {
        var data = new CampaignEvidenceSavedData(directory);
        if (tag.isEmpty() && Files.notExists(directory.resolve(DATA_NAME + ".dat"))) return data;
        try {
            CampaignStateCodec.require(tag.contains("DataVersion", Tag.TAG_INT) && tag.getInt("DataVersion") == CURRENT_DATA_VERSION,
                    "unsupported campaign evidence save version");
            var bindings = new LinkedHashMap<CampaignEvidenceBinding.Key, CampaignEvidenceBinding>();
            for (var raw : CampaignStateCodec.list(tag, "Bindings", Tag.TAG_COMPOUND)) {
                var binding = CampaignEvidenceBinding.load((CompoundTag) raw);
                CampaignStateCodec.require(bindings.putIfAbsent(binding.key(), binding) == null, "duplicate campaign evidence binding");
            }
            var receipts = new LinkedHashMap<CampaignInvestigationReceipt.Key, CampaignInvestigationReceipt>();
            for (var raw : CampaignStateCodec.list(tag, "Receipts", Tag.TAG_COMPOUND)) {
                var receipt = CampaignInvestigationReceipt.load((CompoundTag) raw);
                CampaignStateCodec.require(receipts.putIfAbsent(receipt.key(), receipt) == null, "duplicate investigation receipt");
            }
            validate(bindings, receipts);
            data.install(bindings, receipts);
        } catch (IllegalArgumentException exception) {
            data.preserved = tag.copy();
            data.diagnostic = exception.getMessage() + ". Restore compatible evidence data or a known-good backup.";
        }
        return data;
    }

    public synchronized String diagnostic() { return diagnostic; }
    public synchronized Map<CampaignEvidenceBinding.Key, CampaignEvidenceBinding> bindings() { return bindings; }
    public synchronized Map<CampaignInvestigationReceipt.Key, CampaignInvestigationReceipt> receipts() { return receipts; }
    public synchronized CampaignEvidenceBinding.Key owner(UUID entity) { return entities.get(entity); }
    public synchronized boolean investigated(CampaignInvestigationReceipt.Key key) { return diagnostic.isBlank() && receipts.containsKey(key); }

    synchronized CampaignEvidenceBinding bind(CampaignEvidenceBinding candidate) throws IOException {
        if (!diagnostic.isBlank()) throw new IOException(diagnostic);
        var prior = bindings.get(candidate.key());
        if (prior != null) {
            CampaignStateCodec.require(prior.equals(candidate), "campaign evidence already has a different binding");
            return prior;
        }
        var next = new LinkedHashMap<>(bindings);
        next.put(candidate.key(), candidate);
        persist(next, receipts);
        return candidate;
    }

    synchronized boolean record(List<CampaignInvestigationReceipt> observed) throws IOException {
        if (!diagnostic.isBlank()) throw new IOException(diagnostic);
        var next = new LinkedHashMap<>(receipts);
        boolean changed = false;
        for (var receipt : observed) {
            var prior = next.putIfAbsent(receipt.key(), receipt);
            CampaignStateCodec.require(prior == null || prior.source().equals(receipt.source()), "investigation source identity changed");
            changed |= prior == null;
        }
        if (changed) persist(bindings, next);
        return changed;
    }

    private void persist(Map<CampaignEvidenceBinding.Key, CampaignEvidenceBinding> nextBindings,
            Map<CampaignInvestigationReceipt.Key, CampaignInvestigationReceipt> nextReceipts) throws IOException {
        validate(nextBindings, nextReceipts);
        var wrapper = new CompoundTag();
        wrapper.put("data", encode(nextBindings, nextReceipts));
        NbtUtils.addCurrentDataVersion(wrapper);
        IOUtilities.waitUntilIOWorkerComplete();
        Files.createDirectories(directory);
        com.jvn.villagerretaliation.util.AtomicDataFiles.writeCompressed(wrapper, directory.resolve(DATA_NAME + ".dat"));
        install(nextBindings, nextReceipts);
        setDirty(false);
    }

    private static void validate(Map<CampaignEvidenceBinding.Key, CampaignEvidenceBinding> bindings,
            Map<CampaignInvestigationReceipt.Key, CampaignInvestigationReceipt> receipts) {
        var owners = new LinkedHashMap<net.minecraft.resources.ResourceLocation, CampaignEvidenceBinding>();
        var entities = new java.util.HashSet<UUID>();
        for (var binding : bindings.values()) {
            CampaignStateCodec.require(entities.add(binding.entity()), "evidence entity belongs to multiple sources");
            var prior = owners.putIfAbsent(binding.campaign(), binding);
            CampaignStateCodec.require(prior == null || prior.instance().equals(binding.instance())
                    && prior.definitionRevision() == binding.definitionRevision() && prior.fingerprint().equals(binding.fingerprint()),
                    "evidence sources have conflicting campaign ownership");
        }
        for (var receipt : receipts.values()) {
            var key = receipt.key();
            var binding = bindings.get(new CampaignEvidenceBinding.Key(key.campaign(), key.evidence()));
            CampaignStateCodec.require(binding != null && binding.instance().equals(key.instance()) && binding.fingerprint().equals(key.fingerprint())
                    && binding.entity().equals(receipt.source()) && receipt.observedAt() >= binding.boundAt(),
                    "investigation receipt has no matching evidence source");
        }
    }

    private void install(Map<CampaignEvidenceBinding.Key, CampaignEvidenceBinding> bindings,
            Map<CampaignInvestigationReceipt.Key, CampaignInvestigationReceipt> receipts) {
        this.bindings = Collections.unmodifiableMap(new LinkedHashMap<>(bindings));
        this.receipts = Collections.unmodifiableMap(new LinkedHashMap<>(receipts));
        var entities = new LinkedHashMap<UUID, CampaignEvidenceBinding.Key>();
        bindings.values().forEach(binding -> entities.put(binding.entity(), binding.key()));
        this.entities = Map.copyOf(entities);
    }

    private static CompoundTag encode(Map<CampaignEvidenceBinding.Key, CampaignEvidenceBinding> bindings,
            Map<CampaignInvestigationReceipt.Key, CampaignInvestigationReceipt> receipts) {
        var root = new CompoundTag();
        root.putInt("DataVersion", CURRENT_DATA_VERSION);
        var sources = new ListTag();
        bindings.values().forEach(binding -> sources.add(binding.save()));
        root.put("Bindings", sources);
        var observations = new ListTag();
        receipts.values().forEach(receipt -> observations.add(receipt.save()));
        root.put("Receipts", observations);
        return root;
    }

    @Override public synchronized CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        return preserved == null ? encode(bindings, receipts) : preserved.copy();
    }

    @Override public synchronized void save(File file, HolderLookup.Provider provider) { if (diagnostic.isBlank()) super.save(file, provider); }
}
