package com.jvn.villagerretaliation.campaign;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
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

public final class CampaignRewardSavedData extends SavedData {
    public static final int CURRENT_DATA_VERSION = 1;
    static final String DATA_NAME = "villagerretaliation_campaign_rewards";
    private final Path directory;
    private Map<UUID, CampaignRewardReceipt> receipts = Map.of();
    private String diagnostic = "";
    private CompoundTag preservedData;

    private CampaignRewardSavedData(Path directory) { this.directory = directory; }

    public static CampaignRewardSavedData get(ServerLevel level) {
        Path directory = level.getServer().getWorldPath(LevelResource.ROOT).resolve("data");
        return level.getServer().overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(() -> missing(directory),
                (tag, provider) -> load(tag, directory), DataFixTypes.LEVEL), DATA_NAME);
    }

    private static CampaignRewardSavedData missing(Path directory) {
        var data = new CampaignRewardSavedData(directory);
        if (!Files.notExists(directory.resolve(DATA_NAME + ".dat"))) data.diagnostic = "Campaign reward save could not be read. Restore " + DATA_NAME + ".dat from a known-good backup.";
        return data;
    }

    static CampaignRewardSavedData load(CompoundTag tag, Path directory) {
        var data = new CampaignRewardSavedData(directory);
        if (tag.isEmpty()) {
            if (!Files.notExists(directory.resolve(DATA_NAME + ".dat"))) {
                data.preservedData = tag.copy();
                data.diagnostic = "Campaign reward save has an empty payload. Restore its original reward ledger before continuing.";
            }
            return data;
        }
        try {
            CampaignStateCodec.require(tag.contains("DataVersion", Tag.TAG_INT) && tag.getInt("DataVersion") == CURRENT_DATA_VERSION, "unsupported campaign reward save version");
            var receipts = new LinkedHashMap<UUID, CampaignRewardReceipt>();
            var tokens = new HashSet<UUID>();
            for (var raw : CampaignStateCodec.list(tag, "Receipts", Tag.TAG_COMPOUND)) {
                var receipt = CampaignRewardReceipt.load((CompoundTag) raw);
                CampaignStateCodec.require(receipts.values().stream().noneMatch(prior -> prior.sameGrant(receipt)), "duplicate named campaign reward");
                CampaignStateCodec.require(receipts.putIfAbsent(receipt.operation(), receipt) == null && tokens.add(receipt.token()), "duplicate campaign reward identity");
            }
            data.receipts = immutable(receipts);
        } catch (IllegalArgumentException exception) {
            data.preservedData = tag.copy();
            data.diagnostic = exception.getMessage() + ". Restore compatible reward data or a known-good backup.";
        }
        return data;
    }

    public synchronized String diagnostic() { return diagnostic; }
    public synchronized Map<UUID, CampaignRewardReceipt> receipts() { return receipts; }

    synchronized CampaignRewardReceipt prepare(CampaignRewardReceipt candidate) throws IOException {
        if (!diagnostic.isBlank()) throw new IOException(diagnostic);
        var prior = receipts.get(candidate.operation());
        if (prior != null) {
            if (!prior.sameIntent(candidate)) throw new IllegalArgumentException("Campaign reward operation already belongs to a different reward.");
            return prior;
        }
        if (receipts.values().stream().anyMatch(value -> value.sameGrant(candidate))) throw new IllegalArgumentException("This named campaign reward already has a grant. Reuse its original operation ID.");
        if (candidate.delivered() || receipts.values().stream().anyMatch(value -> value.token().equals(candidate.token()))) throw new IllegalArgumentException("Invalid new campaign reward receipt.");
        persist(candidate);
        return candidate;
    }

    synchronized CampaignRewardReceipt finish(UUID operation, UUID token, UUID player) throws IOException {
        if (!diagnostic.isBlank()) throw new IOException(diagnostic);
        var receipt = receipts.get(operation);
        if (receipt == null || !receipt.token().equals(token) || !receipt.player().equals(player)) throw new IllegalArgumentException("Campaign reward receipt does not belong to this recipient.");
        if (receipt.delivered()) return receipt;
        var delivered = receipt.finish();
        persist(delivered);
        return delivered;
    }

    private void persist(CampaignRewardReceipt receipt) throws IOException {
        var next = new LinkedHashMap<>(receipts);
        next.put(receipt.operation(), receipt);
        var wrapper = new CompoundTag();
        wrapper.put("data", encode(next));
        NbtUtils.addCurrentDataVersion(wrapper);
        IOUtilities.waitUntilIOWorkerComplete();
        Files.createDirectories(directory);
        IOUtilities.writeNbtCompressed(wrapper, directory.resolve(DATA_NAME + ".dat"));
        receipts = immutable(next);
        setDirty(false);
    }

    private static Map<UUID, CampaignRewardReceipt> immutable(Map<UUID, CampaignRewardReceipt> values) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    private static CompoundTag encode(Map<UUID, CampaignRewardReceipt> receipts) {
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
    public synchronized void save(File file, HolderLookup.Provider provider) {
        if (diagnostic.isBlank()) super.save(file, provider);
    }
}
