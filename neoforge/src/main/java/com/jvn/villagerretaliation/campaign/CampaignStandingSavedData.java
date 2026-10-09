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

public final class CampaignStandingSavedData extends SavedData {
    public static final int CURRENT_DATA_VERSION = 1;
    static final String DATA_NAME = "villagerretaliation_campaign_standing";
    private final Path directory;
    private Map<CampaignStanding.Key, CampaignStanding.Value> values = Map.of();
    private Map<UUID, CampaignStanding.Receipt> receipts = Map.of();
    private String diagnostic = "";
    private CompoundTag preserved;

    private CampaignStandingSavedData(Path directory) { this.directory = directory; }

    public static CampaignStandingSavedData get(ServerLevel level) {
        var directory = level.getServer().getWorldPath(LevelResource.ROOT).resolve("data");
        return level.getServer().overworld().getDataStorage().computeIfAbsent(new Factory<>(() -> missing(directory),
                (tag, provider) -> load(tag, directory), DataFixTypes.LEVEL), DATA_NAME);
    }

    private static CampaignStandingSavedData missing(Path directory) {
        var data = new CampaignStandingSavedData(directory);
        if (!Files.notExists(directory.resolve(DATA_NAME + ".dat")))
            data.diagnostic = "Campaign standing could not be read. Restore " + DATA_NAME + ".dat from a known-good backup.";
        return data;
    }

    static CampaignStandingSavedData load(CompoundTag tag, Path directory) {
        var data = new CampaignStandingSavedData(directory);
        if (tag.isEmpty() && Files.notExists(directory.resolve(DATA_NAME + ".dat"))) return data;
        try {
            CampaignStanding.require(tag.contains("DataVersion", Tag.TAG_INT) && tag.getInt("DataVersion") == CURRENT_DATA_VERSION,
                    "Unsupported campaign standing save version");
            var receipts = new LinkedHashMap<UUID, CampaignStanding.Receipt>();
            var values = new LinkedHashMap<CampaignStanding.Key, CampaignStanding.Value>();
            for (var raw : CampaignStateCodec.list(tag, "Receipts", Tag.TAG_COMPOUND)) {
                var receipt = CampaignStandingCodec.load((CompoundTag) raw);
                var key = receipt.subject().key();
                var previous = values.get(key);
                CampaignStanding.require(previous == null || previous.subject().compatible(receipt.subject()),
                        "Standing history changed its captured definition");
                CampaignStanding.require(receipt.before() == (previous == null ? receipt.subject().initial() : previous.amount())
                        && receipt.revision() == (previous == null ? 1 : previous.revision() + 1),
                        "Standing receipt does not follow its prior value");
                CampaignStanding.require(receipts.putIfAbsent(receipt.operation(), receipt) == null, "Duplicate campaign standing operation");
                values.put(key, new CampaignStanding.Value(receipt.subject(), receipt.after(), receipt.revision()));
            }
            data.values = immutable(values);
            data.receipts = immutable(receipts);
        } catch (IllegalArgumentException exception) {
            data.preserved = tag.copy();
            data.diagnostic = exception.getMessage() + ". Restore compatible campaign standing data or a known-good backup.";
        }
        return data;
    }

    public synchronized String diagnostic() { return diagnostic; }
    public synchronized Map<CampaignStanding.Key, CampaignStanding.Value> values() { return values; }
    public synchronized Map<UUID, CampaignStanding.Receipt> receipts() { return receipts; }

    synchronized CampaignStanding.Receipt replay(CampaignStanding.Change change) throws IOException {
        if (!diagnostic.isBlank()) throw new IOException(diagnostic);
        var prior = receipts.get(change.operation());
        CampaignStanding.require(prior == null || prior.change().equals(change), "Standing operation already belongs to another change");
        return prior;
    }

    synchronized CampaignStanding.Result apply(CampaignStanding.Change change, CampaignStanding.Subject subject, long time) throws IOException {
        var prior = replay(change);
        if (prior != null) return new CampaignStanding.Result(prior, false);
        CampaignStanding.require(subject != null && change.owns(subject.key()), "Standing change does not own its target");
        var current = values.get(subject.key());
        CampaignStanding.require(current == null || current.subject().compatible(subject),
                "Saved standing needs its original campaign definition and bounds");
        if (current != null) subject = current.subject();
        int before = current == null ? subject.initial() : current.amount();
        long revision = current == null ? 1 : current.revision() + 1;
        var receipt = new CampaignStanding.Receipt(change, subject, before, subject.apply(before, change.amount()), revision, time);
        var nextReceipts = new LinkedHashMap<>(receipts);
        nextReceipts.put(receipt.operation(), receipt);
        var nextValues = new LinkedHashMap<>(values);
        nextValues.put(subject.key(), new CampaignStanding.Value(subject, receipt.after(), receipt.revision()));
        var wrapper = new CompoundTag();
        wrapper.put("data", encode(nextReceipts));
        NbtUtils.addCurrentDataVersion(wrapper);
        IOUtilities.waitUntilIOWorkerComplete();
        Files.createDirectories(directory);
        IOUtilities.writeNbtCompressed(wrapper, directory.resolve(DATA_NAME + ".dat"));
        receipts = immutable(nextReceipts);
        values = immutable(nextValues);
        setDirty(false);
        return new CampaignStanding.Result(receipt, true);
    }

    private static CompoundTag encode(Map<UUID, CampaignStanding.Receipt> receipts) {
        var tag = new CompoundTag();
        tag.putInt("DataVersion", CURRENT_DATA_VERSION);
        var entries = new ListTag();
        receipts.values().forEach(receipt -> entries.add(CampaignStandingCodec.save(receipt)));
        tag.put("Receipts", entries);
        return tag;
    }

    private static <K, V> Map<K, V> immutable(Map<K, V> values) { return Collections.unmodifiableMap(new LinkedHashMap<>(values)); }
    @Override public synchronized CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        return preserved == null ? encode(receipts) : preserved.copy();
    }
    @Override public synchronized void save(File file, HolderLookup.Provider provider) {
        if (diagnostic.isBlank()) super.save(file, provider);
    }
}
