package com.jvn.villagerretaliation.campaign;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.common.IOUtilities;

public final class CampaignPlayerRewards {
    static final String DATA_KEY = "villagerretaliation:campaign_rewards";
    private static final int VERSION = 1;
    static final String RECOVERY_KEY = "villagerretaliation:campaign_reward_recovery";

    private CampaignPlayerRewards() {
    }

    static Path directory(ServerPlayer player) { return player.server.getWorldPath(LevelResource.PLAYER_DATA_DIR); }

    static boolean delivered(ServerPlayer player, CampaignRewardReceipt receipt) {
        return delivered(player, delivery(receipt));
    }

    private static Delivery delivery(CampaignRewardReceipt receipt) {
        return new Delivery(receipt.operation(), receipt.token(), receipt.player(), receipt.experience(), receipt.items(), receipt.delivered());
    }

    public record Delivery(UUID operation, UUID token, UUID player, int experience, List<CompoundTag> items,
            boolean delivered, List<CompoundTag> costs) {
        public Delivery {
            if (operation == null || token == null || player == null || experience < 0 || items == null || items.size() > 256
                    || costs == null || costs.size() > 256)
                throw new IllegalArgumentException("Invalid persistent player transaction.");
            items = items.stream().map(CompoundTag::copy).toList();
            costs = costs.stream().map(CompoundTag::copy).toList();
        }

        public Delivery(UUID operation, UUID token, UUID player, int experience, List<CompoundTag> items, boolean delivered) {
            this(operation, token, player, experience, items, delivered, List.of());
        }

        @Override public List<CompoundTag> items() { return items.stream().map(CompoundTag::copy).toList(); }
        @Override public List<CompoundTag> costs() { return costs.stream().map(CompoundTag::copy).toList(); }
    }

    private static boolean delivered(ServerPlayer player, Delivery receipt) {
        var receipts = read(persistentLedger(player.getPersistentData()));
        UUID token = receipts.get(receipt.operation());
        if (token != null && !token.equals(receipt.token())) throw new IllegalArgumentException("The player's reward receipt belongs to a different grant. Restore consistent campaign and player saves.");
        return receipt.token().equals(token);
    }

    static boolean deliver(ServerPlayer player, CampaignRewardReceipt receipt, Path directory) throws IOException {
        return deliver(player, delivery(receipt), directory);
    }

    public static boolean deliver(ServerPlayer player, Delivery receipt) throws IOException {
        return deliver(player, receipt, directory(player));
    }

    static boolean deliver(ServerPlayer player, Delivery receipt, Path directory) throws IOException {
        if (!receipt.player().equals(player.getUUID())) throw new IllegalArgumentException("Campaign reward belongs to another player.");
        if (delivered(player, receipt)) return false;
        if (receipt.delivered()) throw new IllegalArgumentException("Player data is older than the delivered campaign reward. Restore a matching player save before continuing.");
        if ((long) player.totalExperience + receipt.experience() > Integer.MAX_VALUE
                || (long) player.getScore() + receipt.experience() > Integer.MAX_VALUE) throw new IllegalArgumentException("Campaign reward exceeds this player's experience capacity.");
        var originalItems = player.getInventory().items.stream().map(ItemStack::copy).toList();
        var originalOffhand = player.getInventory().offhand.stream().map(ItemStack::copy).toList();
        var candidate = new ArrayList<>(originalItems.stream().map(ItemStack::copy).toList());
        var offhand = new ArrayList<>(originalOffhand.stream().map(ItemStack::copy).toList());
        for (var saved : receipt.costs()) {
            if (!saved.contains("count", Tag.TAG_INT) || saved.getInt("count") < 1 || saved.getInt("count") > net.minecraft.world.item.Item.ABSOLUTE_MAX_STACK_SIZE)
                throw new IllegalArgumentException("A saved campaign payment has an invalid item count.");
            var cost = ItemStack.STRICT_CODEC.parse(player.registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE), saved)
                    .result().orElseThrow(() -> new IllegalArgumentException("A saved campaign payment item is unavailable or invalid. Restore its item content before paying."));
            if (cost.isEmpty()) throw new IllegalArgumentException("A saved campaign payment is empty.");
            int remaining = remove(candidate, cost, cost.getCount());
            if (remove(offhand, cost, remaining) != 0) throw new IllegalArgumentException("You do not have the items required for this campaign payment.");
        }
        for (var saved : receipt.items()) {
            var item = ItemStack.parse(player.registryAccess(), saved).orElseThrow(() -> new IllegalArgumentException("A saved campaign reward item is unavailable. Restore its item content before claiming it."));
            if (item.isEmpty() || !insert(player.getInventory(), candidate, item.copy())) throw new IllegalArgumentException("Make room in your inventory to receive the saved campaign reward.");
        }
        var priorPersistent = player.getPersistentData().copy();
        var receipts = read(persistentLedger(priorPersistent));
        receipts.put(receipt.operation(), receipt.token());
        var persistent = priorPersistent.getCompound(Player.PERSISTED_NBT_TAG).copy();
        persistent.put(DATA_KEY, write(receipts));
        int experience = player.totalExperience;
        int level = player.experienceLevel;
        float progress = player.experienceProgress;
        int score = player.getScore();
        boolean saved = false;
        try {
            for (int index = 0; index < candidate.size(); index++) player.getInventory().items.set(index, candidate.get(index));
            for (int index = 0; index < offhand.size(); index++) player.getInventory().offhand.set(index, offhand.get(index));
            if (receipt.experience() != 0) player.giveExperiencePoints(receipt.experience());
            player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persistent);
            persist(player, directory);
            saved = true;
        } finally {
            if (!saved) {
                for (int index = 0; index < originalItems.size(); index++) player.getInventory().items.set(index, originalItems.get(index));
                for (int index = 0; index < originalOffhand.size(); index++) player.getInventory().offhand.set(index, originalOffhand.get(index));
                player.totalExperience = experience;
                player.experienceLevel = level;
                player.experienceProgress = progress;
                player.setScore(score);
                if (priorPersistent.contains(Player.PERSISTED_NBT_TAG)) player.getPersistentData().put(Player.PERSISTED_NBT_TAG, priorPersistent.get(Player.PERSISTED_NBT_TAG));
                else player.getPersistentData().remove(Player.PERSISTED_NBT_TAG);
            }
        }
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
        net.neoforged.neoforge.event.EventHooks.firePlayerSavingEvent(player, directory.toFile(), player.getStringUUID());
        return true;
    }

    private static int remove(List<ItemStack> inventory, ItemStack cost, int remaining) {
        for (var stack : inventory) {
            if (remaining == 0) break;
            if (stack.isEmpty() || !ItemStack.isSameItemSameComponents(stack, cost)) continue;
            int removed = Math.min(remaining, stack.getCount());
            stack.shrink(removed);
            remaining -= removed;
        }
        return remaining;
    }

    private static boolean insert(net.minecraft.world.entity.player.Inventory owner, List<ItemStack> inventory, ItemStack reward) {
        for (var slot : inventory) {
            if (!slot.isEmpty() && ItemStack.isSameItemSameComponents(slot, reward)) {
                int moved = Math.min(reward.getCount(), Math.max(0, owner.getMaxStackSize(slot) - slot.getCount()));
                slot.grow(moved);
                reward.shrink(moved);
                if (reward.isEmpty()) return true;
            }
        }
        for (int index = 0; index < inventory.size(); index++) {
            if (!inventory.get(index).isEmpty()) continue;
            int moved = Math.min(reward.getCount(), owner.getMaxStackSize(reward));
            inventory.set(index, reward.copyWithCount(moved));
            reward.shrink(moved);
            if (reward.isEmpty()) return true;
        }
        return reward.isEmpty();
    }

    private static void persist(ServerPlayer player, Path directory) throws IOException {
        var snapshot = player.saveWithoutId(new CompoundTag());
        net.minecraft.nbt.NbtUtils.addCurrentDataVersion(snapshot);
        Path target = directory.resolve(player.getStringUUID() + ".dat");
        IOUtilities.waitUntilIOWorkerComplete();
        Files.createDirectories(directory);
        if (Files.exists(target)) IOUtilities.atomicWrite(directory.resolve(player.getStringUUID() + ".dat_old"), stream -> Files.copy(target, stream));
        IOUtilities.writeNbtCompressed(snapshot, target);
    }

    public static CompoundTag loadedOwnerTag(ServerPlayer player, CompoundTag original) {
        if (original == null || !player.server.isSingleplayerOwner(player.getGameProfile())) return original;
        return loadedOwnerTag(player, original, directory(player));
    }

    static CompoundTag loadedOwnerTag(ServerPlayer player, CompoundTag original, Path directory) {
        Map<UUID, UUID> originalReceipts;
        try {
            originalReceipts = read(snapshotLedger(original));
        } catch (IllegalArgumentException exception) {
            return original;
        }
        String readIssue = "";
        for (String suffix : List.of(".dat", ".dat_old")) {
            Path file = directory.resolve(player.getStringUUID() + suffix);
            if (Files.notExists(file)) continue;
            CompoundTag candidate;
            try {
                candidate = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
            } catch (IOException exception) {
                readIssue = recoveryIssue(file, exception);
                continue;
            }
            try {
                if (!candidate.hasUUID("UUID") || !candidate.getUUID("UUID").equals(player.getUUID())) throw new IllegalArgumentException("Player save identity does not match its campaign rewards.");
                candidate = net.minecraft.util.datafix.DataFixTypes.PLAYER.updateToCurrentVersion(player.server.getFixerUpper(), candidate,
                        net.minecraft.nbt.NbtUtils.getDataVersion(candidate, -1));
                var receipts = read(snapshotLedger(candidate));
                boolean fileIncludesOriginal = receipts.entrySet().containsAll(originalReceipts.entrySet());
                boolean originalIncludesFile = originalReceipts.entrySet().containsAll(receipts.entrySet());
                if (!fileIncludesOriginal && !originalIncludesFile) throw new IllegalArgumentException("Player snapshots contain conflicting campaign rewards.");
                var selected = fileIncludesOriginal && receipts.size() > originalReceipts.size() ? candidate : original;
                return readIssue.isBlank() ? selected : preserveRecoveryIssue(selected, readIssue, null);
            } catch (IllegalArgumentException exception) {
                return preserveRecoveryIssue(original, recoveryIssue(file, exception), candidate);
            }
        }
        return readIssue.isBlank() ? original : preserveRecoveryIssue(original, readIssue, null);
    }

    private static String recoveryIssue(Path file, Exception exception) {
        String issue = "Campaign reward recovery could not read " + file.getFileName() + ". Restore a matching player save before continuing.";
        LogUtils.getLogger().error(issue, exception);
        return issue;
    }

    private static CompoundTag preserveRecoveryIssue(CompoundTag original, String diagnostic, CompoundTag candidate) {
        var preserved = original.copy();
        var persistent = preserved.getCompound("NeoForgeData").copy();
        var retained = persistent.getCompound(Player.PERSISTED_NBT_TAG).copy();
        var issue = new CompoundTag();
        issue.putString("Diagnostic", diagnostic);
        if (candidate != null) issue.put("ConflictingSnapshot", candidate.copy());
        retained.put(RECOVERY_KEY, issue);
        persistent.put(Player.PERSISTED_NBT_TAG, retained);
        preserved.put("NeoForgeData", persistent);
        return preserved;
    }

    private static CompoundTag snapshotLedger(CompoundTag snapshot) {
        CampaignStateCodec.require(!snapshot.contains("NeoForgeData") || snapshot.contains("NeoForgeData", Tag.TAG_COMPOUND), "invalid persisted entity data");
        return persistentLedger(snapshot.getCompound("NeoForgeData"));
    }

    private static CompoundTag persistentLedger(CompoundTag persistent) {
        CampaignStateCodec.require(!persistent.contains(Player.PERSISTED_NBT_TAG) || persistent.contains(Player.PERSISTED_NBT_TAG, Tag.TAG_COMPOUND), "invalid persisted player data");
        var saved = persistent.getCompound(Player.PERSISTED_NBT_TAG);
        CampaignStateCodec.require(!saved.contains(RECOVERY_KEY), "Campaign reward recovery needs a matching player save. Resolve the preserved recovery record before continuing.");
        CampaignStateCodec.require(!saved.contains(DATA_KEY) || saved.contains(DATA_KEY, Tag.TAG_COMPOUND), "invalid campaign reward receipts in player data");
        return saved.contains(DATA_KEY) ? saved.getCompound(DATA_KEY) : null;
    }

    private static LinkedHashMap<UUID, UUID> read(CompoundTag tag) {
        var receipts = new LinkedHashMap<UUID, UUID>();
        if (tag == null) return receipts;
        CampaignStateCodec.require(tag.contains("DataVersion", Tag.TAG_INT) && tag.getInt("DataVersion") == VERSION
                && tag.contains("Revision", Tag.TAG_LONG), "unsupported campaign reward receipts in player data");
        var tokens = new HashSet<UUID>();
        for (var raw : CampaignStateCodec.list(tag, "Receipts", Tag.TAG_COMPOUND)) {
            var receipt = (CompoundTag) raw;
            CampaignStateCodec.require(receipt.hasUUID("Operation") && receipt.hasUUID("Token"), "invalid player reward identity");
            CampaignStateCodec.require(receipts.putIfAbsent(receipt.getUUID("Operation"), receipt.getUUID("Token")) == null
                    && tokens.add(receipt.getUUID("Token")), "duplicate player reward identity");
        }
        CampaignStateCodec.require(tag.getLong("Revision") == receipts.size(), "player reward revision does not match its receipts");
        return receipts;
    }

    private static CompoundTag write(Map<UUID, UUID> receipts) {
        var tag = new CompoundTag();
        tag.putInt("DataVersion", VERSION);
        tag.putLong("Revision", receipts.size());
        var list = new ListTag();
        receipts.forEach((operation, token) -> {
            var receipt = new CompoundTag();
            receipt.putUUID("Operation", operation);
            receipt.putUUID("Token", token);
            list.add(receipt);
        });
        tag.put("Receipts", list);
        return tag;
    }
}
