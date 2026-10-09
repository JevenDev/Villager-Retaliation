package com.jvn.villagerretaliation.skill.player;

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

public final class PlayerSocialProgressionSavedData extends SavedData {
    public static final int CURRENT_DATA_VERSION = 1;
    public static final String DATA_NAME = "villagerretaliation_player_social_skills";
    private final Path directory;
    private Map<ProgressKey, Long> progress = Map.of();
    private Map<PlayerSocialAward.Identity, Receipt> receipts = Map.of();
    private String diagnostic = "";
    private CompoundTag preserved;

    private PlayerSocialProgressionSavedData(Path directory) { this.directory = directory; }

    public static PlayerSocialProgressionSavedData get(ServerLevel level) {
        var directory = level.getServer().getWorldPath(LevelResource.ROOT).resolve("data");
        return level.getServer().overworld().getDataStorage().computeIfAbsent(new Factory<>(() -> missing(directory),
                (tag, provider) -> load(tag, directory), DataFixTypes.LEVEL), DATA_NAME);
    }

    private static PlayerSocialProgressionSavedData missing(Path directory) {
        var data = new PlayerSocialProgressionSavedData(directory);
        if (!Files.notExists(directory.resolve(DATA_NAME + ".dat")))
            data.diagnostic = "Player social progression could not be read. Restore " + DATA_NAME + ".dat from a known-good backup.";
        return data;
    }

    public static PlayerSocialProgressionSavedData load(CompoundTag tag, Path directory) {
        var data = new PlayerSocialProgressionSavedData(directory);
        if (tag.isEmpty() && Files.notExists(directory.resolve(DATA_NAME + ".dat"))) return data;
        try {
            require(tag.contains("DataVersion", Tag.TAG_INT) && tag.getInt("DataVersion") == CURRENT_DATA_VERSION,
                    "unsupported player social progression save version");
            var progress = new LinkedHashMap<ProgressKey, Long>();
            for (var raw : list(tag, "Progress")) {
                var value = (CompoundTag) raw;
                require(value.hasUUID("Player") && value.contains("Skill", Tag.TAG_STRING), "incomplete player social skill");
                var key = new ProgressKey(value.getUUID("Player"), PlayerSocialSkill.parse(value.getString("Skill")));
                long experience = number(value, "Experience");
                require(experience >= 0 && progress.putIfAbsent(key, experience) == null, "duplicate or invalid player social skill");
            }
            var receipts = new LinkedHashMap<PlayerSocialAward.Identity, Receipt>();
            var earned = new LinkedHashMap<ProgressKey, Long>();
            for (var raw : list(tag, "Receipts")) {
                var value = (CompoundTag) raw;
                require(value.contains("Award", Tag.TAG_COMPOUND), "missing social skill award");
                var award = PlayerSocialAward.load(value.getCompound("Award"));
                var receipt = new Receipt(award, number(value, "Before"), number(value, "After"));
                var key = new ProgressKey(award.player(), award.breakdown().skill());
                require(receipt.before() == earned.getOrDefault(key, 0L), "social skill receipt does not follow prior experience");
                require(receipts.putIfAbsent(award.identity(), receipt) == null, "duplicate social skill award");
                earned.put(key, receipt.after());
            }
            require(progress.equals(earned), "player social experience does not match its award receipts");
            data.progress = immutable(progress);
            data.receipts = immutable(receipts);
        } catch (IllegalArgumentException exception) {
            data.preserved = tag.copy();
            data.diagnostic = exception.getMessage() + ". Restore compatible player social data or a known-good backup.";
        }
        return data;
    }

    public synchronized String diagnostic() { return diagnostic; }
    public synchronized Map<ProgressKey, Long> progress() { return progress; }
    public synchronized Map<PlayerSocialAward.Identity, Receipt> receipts() { return receipts; }

    public synchronized long experience(UUID player, PlayerSocialSkill skill) {
        require(diagnostic.isBlank(), diagnostic);
        return progress.getOrDefault(new ProgressKey(player, skill), 0L);
    }

    public synchronized Receipt grant(PlayerSocialAward award) throws IOException {
        if (!diagnostic.isBlank()) throw new IOException(diagnostic);
        var prior = receipts.get(award.identity());
        if (prior != null) {
            require(prior.award().equals(award), "social skill award identity belongs to another resolved check");
            return prior;
        }
        var key = new ProgressKey(award.player(), award.breakdown().skill());
        long before = progress.getOrDefault(key, 0L);
        long after;
        try { after = Math.addExact(before, award.breakdown().rewardExperience()); }
        catch (ArithmeticException exception) { throw new IllegalArgumentException("Player social experience exceeds its storage limit", exception); }
        var receipt = new Receipt(award, before, after);
        var nextProgress = new LinkedHashMap<>(progress);
        nextProgress.put(key, after);
        var nextReceipts = new LinkedHashMap<>(receipts);
        nextReceipts.put(award.identity(), receipt);
        var wrapper = new CompoundTag();
        wrapper.put("data", encode(nextProgress, nextReceipts));
        NbtUtils.addCurrentDataVersion(wrapper);
        IOUtilities.waitUntilIOWorkerComplete();
        Files.createDirectories(directory);
        IOUtilities.writeNbtCompressed(wrapper, directory.resolve(DATA_NAME + ".dat"));
        progress = immutable(nextProgress);
        receipts = immutable(nextReceipts);
        setDirty(false);
        return receipt;
    }

    private static CompoundTag encode(Map<ProgressKey, Long> progress, Map<PlayerSocialAward.Identity, Receipt> receipts) {
        var tag = new CompoundTag();
        tag.putInt("DataVersion", CURRENT_DATA_VERSION);
        var players = new ListTag();
        progress.forEach((key, experience) -> {
            var value = new CompoundTag();
            value.putUUID("Player", key.player());
            value.putString("Skill", key.skill().id());
            value.putLong("Experience", experience);
            players.add(value);
        });
        tag.put("Progress", players);
        var awards = new ListTag();
        receipts.values().forEach(receipt -> {
            var value = new CompoundTag();
            value.put("Award", receipt.award().save());
            value.putLong("Before", receipt.before());
            value.putLong("After", receipt.after());
            awards.add(value);
        });
        tag.put("Receipts", awards);
        return tag;
    }

    private static ListTag list(CompoundTag tag, String name) {
        require(tag.get(name) instanceof ListTag, "missing social progression list: " + name);
        var list = (ListTag) tag.get(name);
        require(list.isEmpty() || list.getElementType() == Tag.TAG_COMPOUND, "invalid social progression list: " + name);
        return list;
    }
    private static long number(CompoundTag tag, String name) {
        require(tag.contains(name, Tag.TAG_LONG), "missing social progression number: " + name);
        return tag.getLong(name);
    }
    private static void require(boolean value, String message) { if (!value) throw new IllegalArgumentException(message); }
    private static <K, V> Map<K, V> immutable(Map<K, V> values) { return Collections.unmodifiableMap(new LinkedHashMap<>(values)); }

    public record ProgressKey(UUID player, PlayerSocialSkill skill) {
        public ProgressKey { require(player != null && skill != null, "invalid player social skill identity"); }
    }

    public record Receipt(PlayerSocialAward award, long before, long after) {
        public Receipt {
            require(award != null && before >= 0 && after >= before && after - before == award.breakdown().rewardExperience(),
                    "invalid social skill award totals");
        }
    }

    @Override public synchronized CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        return preserved == null ? encode(progress, receipts) : preserved.copy();
    }
    @Override public synchronized void save(File file, HolderLookup.Provider provider) {
        if (diagnostic.isBlank()) super.save(file, provider);
    }
}
