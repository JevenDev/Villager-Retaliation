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

public final class CampaignVariantSavedData extends SavedData {
    static final String DATA_NAME = "villagerretaliation_campaign_variants";
    private final Path directory;
    private Map<UUID, CampaignVariantReceipt> receipts = Map.of();
    private String diagnostic = "";
    private CompoundTag preserved;

    private CampaignVariantSavedData(Path directory) { this.directory = directory; }

    public static CampaignVariantSavedData get(ServerLevel level) {
        var directory = level.getServer().getWorldPath(LevelResource.ROOT).resolve("data");
        return level.getServer().overworld().getDataStorage().computeIfAbsent(new Factory<>(() -> missing(directory),
                (tag, provider) -> load(tag, directory), DataFixTypes.LEVEL), DATA_NAME);
    }

    private static CampaignVariantSavedData missing(Path directory) {
        var data = new CampaignVariantSavedData(directory);
        if (!Files.notExists(directory.resolve(DATA_NAME + ".dat")))
            data.diagnostic = "Campaign location changes could not be read. Restore " + DATA_NAME + ".dat from a known-good backup.";
        return data;
    }

    static CampaignVariantSavedData load(CompoundTag tag, Path directory) {
        var data = new CampaignVariantSavedData(directory);
        if (tag.isEmpty() && Files.notExists(directory.resolve(DATA_NAME + ".dat"))) return data;
        try {
            CampaignStateCodec.require(tag.getAllKeys().equals(java.util.Set.of("DataVersion", "Receipts"))
                    && tag.contains("DataVersion", Tag.TAG_INT) && tag.getInt("DataVersion") == 1, "unsupported campaign location change save");
            var receipts = new LinkedHashMap<UUID, CampaignVariantReceipt>();
            for (var raw : CampaignStateCodec.list(tag, "Receipts", Tag.TAG_COMPOUND)) {
                var receipt = CampaignVariantReceipt.load((CompoundTag) raw);
                CampaignStateCodec.require(receipts.putIfAbsent(receipt.operation(), receipt) == null, "duplicate campaign location change");
            }
            validate(receipts);
            data.receipts = immutable(receipts);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            data.preserved = tag.copy();
            data.diagnostic = "Campaign location changes are unreadable. Restore compatible variant data or a known-good backup.";
        }
        return data;
    }

    public synchronized String diagnostic() { return diagnostic; }
    public synchronized Map<UUID, CampaignVariantReceipt> receipts() { return receipts; }

    synchronized CampaignVariantReceipt record(CampaignVariantReceipt candidate) throws IOException {
        if (!diagnostic.isBlank()) throw new IOException(diagnostic);
        var prior = receipts.get(candidate.operation());
        if (candidate.equals(prior)) return prior;
        CampaignStateCodec.require(prior == null || prior.sameIntent(candidate) && !prior.complete()
                && candidate.cursor() >= prior.cursor() && (prior.binding() == null
                || prior.binding().equals(candidate.binding()) && prior.cells().equals(candidate.cells())), "location change history cannot be replaced");
        var next = new LinkedHashMap<>(receipts);
        next.put(candidate.operation(), candidate);
        validate(next);
        var wrapper = new CompoundTag();
        wrapper.put("data", encode(next));
        NbtUtils.addCurrentDataVersion(wrapper);
        IOUtilities.waitUntilIOWorkerComplete();
        Files.createDirectories(directory);
        IOUtilities.writeNbtCompressed(wrapper, directory.resolve(DATA_NAME + ".dat"));
        receipts = immutable(next);
        setDirty(false);
        return candidate;
    }

    private static void validate(Map<UUID, CampaignVariantReceipt> receipts) {
        CampaignStateCodec.require(receipts.size() <= 65536, "campaign location change capacity exceeded");
        var sites = new LinkedHashMap<UUID, CampaignVariantReceipt>();
        var campaigns = new LinkedHashMap<net.minecraft.resources.ResourceLocation, CampaignVariantReceipt>();
        for (var receipt : receipts.values()) {
            var prior = campaigns.putIfAbsent(receipt.campaign(), receipt);
            CampaignStateCodec.require(prior == null || prior.instance().equals(receipt.instance())
                    && prior.definitionRevision() == receipt.definitionRevision() && prior.fingerprint().equals(receipt.fingerprint()), "conflicting location campaign ownership");
            for (var cell : receipt.cells()) {
                var owner = sites.putIfAbsent(cell.generated().site(), receipt);
                CampaignStateCodec.require(owner == null || owner.instance().equals(receipt.instance()) && owner.campaign().equals(receipt.campaign()),
                        "generated location already belongs to another campaign instance");
            }
        }
    }

    private static Map<UUID, CampaignVariantReceipt> immutable(Map<UUID, CampaignVariantReceipt> values) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    private static CompoundTag encode(Map<UUID, CampaignVariantReceipt> receipts) {
        var tag = new CompoundTag();
        tag.putInt("DataVersion", 1);
        var list = new ListTag();
        receipts.values().forEach(receipt -> list.add(receipt.save()));
        tag.put("Receipts", list);
        return tag;
    }

    @Override public synchronized CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        return preserved == null ? encode(receipts) : preserved.copy();
    }
    @Override public synchronized void save(File file, HolderLookup.Provider provider) { if (diagnostic.isBlank()) super.save(file, provider); }
}
