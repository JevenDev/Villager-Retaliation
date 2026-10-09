package com.jvn.villagerretaliation.campaign;

import com.jvn.villagerretaliation.dialogue.conversation.ConversationSession;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
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
import static com.jvn.villagerretaliation.campaign.CampaignTransaction.require;

public final class CampaignTransactionSavedData extends SavedData {
    public static final int CURRENT_DATA_VERSION = 1;
    static final String DATA_NAME = "villagerretaliation_campaign_transactions";
    private final Path directory;
    private Map<UUID, CampaignTransaction.Receipt> receipts = Map.of();
    private final Map<UUID, CampaignTransaction.Receipt> pending = new LinkedHashMap<>();
    private final Map<UUID, String> issues = new LinkedHashMap<>();
    private String diagnostic = "";
    private CompoundTag preserved;

    private CampaignTransactionSavedData(Path directory) { this.directory = directory; }

    public static CampaignTransactionSavedData get(ServerLevel level) {
        var directory = level.getServer().getWorldPath(LevelResource.ROOT).resolve("data");
        return level.getServer().overworld().getDataStorage().computeIfAbsent(new Factory<>(() -> missing(directory),
                (tag, provider) -> load(tag, directory), DataFixTypes.LEVEL), DATA_NAME);
    }

    private static CampaignTransactionSavedData missing(Path directory) {
        var data = new CampaignTransactionSavedData(directory);
        if (!Files.notExists(directory.resolve(DATA_NAME + ".dat")))
            data.diagnostic = "Campaign transactions could not be read. Restore " + DATA_NAME + ".dat from a known-good backup.";
        return data;
    }

    static CampaignTransactionSavedData load(CompoundTag tag, Path directory) {
        var data = new CampaignTransactionSavedData(directory);
        if (tag.isEmpty() && Files.notExists(directory.resolve(DATA_NAME + ".dat"))) return data;
        try {
            require(tag.contains("DataVersion", Tag.TAG_INT) && tag.getInt("DataVersion") == CURRENT_DATA_VERSION,
                    "Unsupported campaign transaction save version");
            var receipts = new LinkedHashMap<UUID, CampaignTransaction.Receipt>();
            var tokens = new HashSet<UUID>();
            for (var raw : CampaignStateCodec.list(tag, "Receipts", Tag.TAG_COMPOUND)) {
                var receipt = CampaignTransactionCodec.load((CompoundTag) raw);
                require(receipts.putIfAbsent(receipt.operation(), receipt) == null && tokens.add(receipt.token()),
                        "Duplicate campaign transaction identity");
            }
            data.receipts = immutable(receipts);
        } catch (IllegalArgumentException exception) {
            data.preserved = tag.copy();
            data.diagnostic = exception.getMessage() + ". Restore compatible campaign transactions or a known-good backup.";
        }
        return data;
    }

    public synchronized String diagnostic() { return diagnostic; }
    public synchronized String issue(UUID operation) { return diagnostic.isBlank() ? issues.getOrDefault(operation, "") : diagnostic; }
    public synchronized Map<UUID, CampaignTransaction.Receipt> receipts() { return receipts; }
    synchronized Map<UUID, CampaignTransaction.Receipt> pendingReceipts() { return immutable(pending); }

    public synchronized CampaignTransaction.Receipt negotiate(CampaignTransaction.Terms terms, CampaignTransaction.Source source,
            Supplier<ConversationSession.Check> roll, long time) throws IOException {
        healthy();
        var prior = prior(terms);
        if (prior != null) {
            require(prior.negotiation() != null, "Payment for this transaction has already been prepared without bargaining");
            require(prior.negotiation().source().compatible(source), "This transaction belongs to another saved negotiation. Restore its original conversation content.");
            if (pending.containsKey(prior.operation())) persist(prior);
            return prior;
        }
        require(terms.bargainedCount() != null, "This campaign transaction does not allow bargaining");
        var negotiation = new CampaignTransaction.Negotiation(source, roll.get());
        var receipt = new CampaignTransaction.Receipt(terms, UUID.randomUUID(), negotiation, CampaignTransaction.Phase.NEGOTIATED, time);
        persist(receipt);
        return receipt;
    }

    public synchronized CampaignTransaction.Receipt preparePayment(CampaignTransaction.Terms terms, long time) throws IOException {
        healthy();
        var prior = prior(terms);
        if (prior != null && pending.containsKey(prior.operation()) && prior.phase() == CampaignTransaction.Phase.NEGOTIATED)
            throw new IOException("The bargaining result is waiting to save. Retry that check before paying.");
        var receipt = prior == null
                ? new CampaignTransaction.Receipt(terms, UUID.randomUUID(), null, CampaignTransaction.Phase.PAYMENT_PENDING, time)
                : prior.prepare(time);
        if (prior == null || !receipt.equals(prior) || pending.containsKey(receipt.operation())) persist(receipt);
        return receipt;
    }

    public synchronized CampaignTransaction.Receipt finish(UUID operation, UUID token, UUID player, long time) throws IOException {
        healthy();
        var prior = pending.getOrDefault(operation, receipts.get(operation));
        require(prior != null && prior.token().equals(token) && prior.terms().key().player().equals(player),
                "Campaign payment acknowledgement belongs to another transaction or player");
        var receipt = prior.finish(time);
        if (!receipt.equals(prior) || pending.containsKey(operation)) persist(receipt);
        return receipt;
    }

    private CampaignTransaction.Receipt prior(CampaignTransaction.Terms terms) {
        var prior = pending.getOrDefault(terms.key().operation(), receipts.get(terms.key().operation()));
        require(prior == null || prior.terms().compatible(terms), "Saved transaction terms or seller identity changed. Restore matching campaign content and bindings.");
        return prior;
    }

    private void healthy() throws IOException {
        if (!diagnostic.isBlank()) throw new IOException(diagnostic);
    }

    private void persist(CampaignTransaction.Receipt receipt) throws IOException {
        UUID operation = receipt.operation();
        require(receipts.values().stream().noneMatch(value -> !value.operation().equals(operation) && value.token().equals(receipt.token()))
                && pending.values().stream().noneMatch(value -> !value.operation().equals(operation) && value.token().equals(receipt.token())),
                "Campaign payment token belongs to another transaction");
        var next = new LinkedHashMap<>(receipts);
        next.put(operation, receipt);
        var wrapper = new CompoundTag();
        wrapper.put("data", encode(next));
        NbtUtils.addCurrentDataVersion(wrapper);
        pending.put(operation, receipt);
        try {
            IOUtilities.waitUntilIOWorkerComplete();
            Files.createDirectories(directory);
            IOUtilities.writeNbtCompressed(wrapper, directory.resolve(DATA_NAME + ".dat"));
        } catch (IOException exception) {
            issues.put(operation, "Campaign transaction is waiting for world storage: " + exception.getMessage());
            throw exception;
        }
        receipts = immutable(next);
        pending.remove(operation);
        issues.remove(operation);
        setDirty(false);
    }

    private static CompoundTag encode(Map<UUID, CampaignTransaction.Receipt> receipts) {
        var tag = new CompoundTag();
        tag.putInt("DataVersion", CURRENT_DATA_VERSION);
        var values = new ListTag();
        receipts.values().forEach(receipt -> values.add(CampaignTransactionCodec.save(receipt)));
        tag.put("Receipts", values);
        return tag;
    }

    private static <K, V> Map<K, V> immutable(Map<K, V> source) { return Collections.unmodifiableMap(new LinkedHashMap<>(source)); }
    @Override public synchronized CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        return preserved == null ? encode(receipts) : preserved.copy();
    }
    @Override public synchronized void save(File file, HolderLookup.Provider provider) {
        if (diagnostic.isBlank()) super.save(file, provider);
    }
}
