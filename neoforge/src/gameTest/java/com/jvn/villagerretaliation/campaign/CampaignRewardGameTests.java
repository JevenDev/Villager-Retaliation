package com.jvn.villagerretaliation.campaign;

import com.jvn.villagerretaliation.campaign.content.CampaignRewardDefinition;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.IOUtilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("campaign_state")
@PrefixGameTestTemplate(false)
public final class CampaignRewardGameTests {
    private static final ResourceLocation CAMPAIGN = ResourceLocation.parse("test:rewards");
    private static final ResourceLocation QUEST = ResourceLocation.parse("test:reward_quest");

    private CampaignRewardGameTests() {
    }

    @GameTest(template = "empty", batch = "campaign_rewards")
    public static void preparedLootAndDeliveryReceiptsSurviveDiskReload(GameTestHelper helper) throws IOException {
        try (Disk disk = new Disk()) {
            var player = helper.makeMockServerPlayerInLevel();
            var data = disk.open();
            var receipt = receipt(player, CampaignRewardDefinition.Scope.PERSONAL, List.of((CompoundTag) new ItemStack(Items.EMERALD, 3).save(player.registryAccess())));
            data.prepare(receipt);
            var loaded = disk.load();
            helper.assertValueEqual(loaded.receipts().get(receipt.operation()), receipt, "prepared loot and recipient survive a disk reload");
            var changedRoll = copy(receipt, receipt.player(), receipt.token(), receipt.reward(), receipt.experience(), List.of());
            helper.assertValueEqual(loaded.prepare(changedRoll).items(), receipt.items(), "retry keeps the original loot roll");
            loaded.finish(receipt.operation(), receipt.token(), receipt.player());
            helper.assertTrue(disk.load().receipts().get(receipt.operation()).delivered(), "delivery acknowledgement is durable");
            helper.assertValueEqual(loaded.prepare(receipt), receipt.finish(), "old preparation cannot reset delivery");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_rewards")
    public static void simultaneousSharedGrantsReserveExactlyOneRecipient(GameTestHelper helper) throws Exception {
        try (Disk disk = new Disk(); var executor = Executors.newFixedThreadPool(2)) {
            var data = disk.open();
            var first = receipt(helper.makeMockServerPlayerInLevel(), CampaignRewardDefinition.Scope.SHARED, List.of());
            var second = copy(first, UUID.randomUUID(), UUID.randomUUID(), first.reward(), first.experience(), List.of());
            var results = executor.invokeAll(List.<Callable<CampaignRewardReceipt>>of(() -> data.prepare(first), () -> data.prepare(second)));
            var winner = results.getFirst().get();
            helper.assertValueEqual(results.getLast().get(), winner, "both contenders receive the actual reserved recipient");
            helper.assertValueEqual(disk.load().receipts().size(), 1, "one grant is saved");
            expectInvalid(helper, () -> data.finish(winner.operation(), winner.token(), UUID.randomUUID()));
            helper.assertFalse(data.receipts().get(winner.operation()).delivered(), "losing recipient cannot acknowledge delivery");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_rewards")
    public static void conflictingRewardsCannotReuseAnOperation(GameTestHelper helper) throws IOException {
        try (Disk disk = new Disk()) {
            var data = disk.open();
            var receipt = receipt(helper.makeMockServerPlayerInLevel(), CampaignRewardDefinition.Scope.PERSONAL, List.of());
            data.prepare(receipt);
            expectInvalid(helper, () -> data.prepare(copy(receipt, receipt.player(), receipt.token(), "other", receipt.experience(), List.of())));
            expectInvalid(helper, () -> data.prepare(copy(receipt, receipt.player(), receipt.token(), receipt.reward(), 999, List.of())));
            helper.assertValueEqual(disk.load().receipts().get(receipt.operation()), receipt, "conflicting intent leaves the saved grant intact");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_rewards")
    public static void failedWorldWritesDoNotPublishPreparationOrDelivery(GameTestHelper helper) throws IOException {
        try (Disk disk = new Disk()) {
            Path blocked = disk.root.resolve("blocked");
            Files.writeString(blocked, "occupied");
            var receipt = receipt(helper.makeMockServerPlayerInLevel(), CampaignRewardDefinition.Scope.PERSONAL, List.of());
            var blockedData = CampaignRewardSavedData.load(new CompoundTag(), blocked);
            var failingPrepare = blockedData;
            expectIo(helper, () -> failingPrepare.prepare(receipt));
            helper.assertTrue(blockedData.receipts().isEmpty(), "failed preparation remains unpublished");
            var data = disk.open();
            data.prepare(receipt);
            blockedData = CampaignRewardSavedData.load(data.save(new CompoundTag(), helper.getLevel().registryAccess()), blocked);
            var failingFinish = blockedData;
            expectIo(helper, () -> failingFinish.finish(receipt.operation(), receipt.token(), receipt.player()));
            helper.assertFalse(failingFinish.receipts().get(receipt.operation()).delivered(), "failed acknowledgement retains pending receipt");
            helper.assertValueEqual(disk.load().receipts().get(receipt.operation()), receipt, "saved preparation is unchanged");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_rewards")
    public static void futureAndCorruptRewardSavesRemainAvailableForRepair(GameTestHelper helper) throws IOException {
        try (Disk disk = new Disk()) {
            for (int version : List.of(1, 999)) {
                var tag = new CompoundTag();
                tag.putInt("DataVersion", version);
                tag.putString("Receipts", "unreadable");
                var data = CampaignRewardSavedData.load(tag, disk.directory);
                helper.assertFalse(data.diagnostic().isBlank(), "unsupported save is dormant");
                helper.assertValueEqual(data.save(new CompoundTag(), helper.getLevel().registryAccess()), tag, "raw save is preserved");
                expectIo(helper, () -> data.prepare(receipt(helper.makeMockServerPlayerInLevel(), CampaignRewardDefinition.Scope.PERSONAL, List.of())));
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_rewards")
    public static void anExistingEmptyLedgerCannotBeMistakenForANewWorld(GameTestHelper helper) throws IOException {
        try (Disk disk = new Disk()) {
            Files.createDirectories(disk.directory);
            var wrapper = new CompoundTag();
            wrapper.put("data", new CompoundTag());
            IOUtilities.writeNbtCompressed(wrapper, disk.directory.resolve(CampaignRewardSavedData.DATA_NAME + ".dat"));
            var data = disk.load();
            helper.assertFalse(data.diagnostic().isBlank(), "empty existing reward file is blocked for repair");
            var receipt = receipt(helper.makeMockServerPlayerInLevel(), CampaignRewardDefinition.Scope.SHARED, List.of());
            expectIo(helper, () -> data.prepare(receipt));
            helper.assertValueEqual(NbtIo.readCompressed(disk.directory.resolve(CampaignRewardSavedData.DATA_NAME + ".dat"), NbtAccounter.unlimitedHeap()), wrapper, "blocked preparation preserves the empty source for repair");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_rewards")
    public static void retryAfterPlayerSaveOnlyAcknowledgesTheExistingReward(GameTestHelper helper) throws IOException {
        try (Disk disk = new Disk()) {
            var player = helper.makeMockServerPlayerInLevel();
            var receipt = receipt(player, CampaignRewardDefinition.Scope.PERSONAL, List.of((CompoundTag) new ItemStack(Items.EMERALD, 3).save(player.registryAccess())));
            var data = disk.open();
            data.prepare(receipt);
            helper.assertTrue(CampaignPlayerRewards.deliver(player, receipt, disk.players), "first delivery applies");
            player.getInventory().clearContent();
            player.totalExperience = 0;
            player.getPersistentData().remove(Player.PERSISTED_NBT_TAG);
            player.load(disk.player(player));
            helper.assertTrue(CampaignPlayerRewards.delivered(player, receipt), "player receipt survives disk load with the reward");
            data = disk.load();
            helper.assertFalse(data.receipts().get(receipt.operation()).delivered(), "world acknowledgement was interrupted");
            helper.assertFalse(CampaignRewardService.deliver(player, data, receipt, disk.players), "retry does not apply the reward twice");
            helper.assertValueEqual(player.totalExperience, 17, "experience remains one grant");
            helper.assertValueEqual(player.getInventory().countItem(Items.EMERALD), 3, "inventory remains one grant");
            helper.assertTrue(disk.load().receipts().get(receipt.operation()).delivered(), "retry finishes the world acknowledgement");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_rewards")
    public static void failedPlayerSaveRollsBackInventoryExperienceAndReceipt(GameTestHelper helper) throws IOException {
        try (Disk disk = new Disk()) {
            var player = helper.makeMockServerPlayerInLevel();
            player.giveExperiencePoints(5);
            player.getInventory().setItem(0, new ItemStack(Items.EMERALD, 2));
            var original = player.saveWithoutId(new CompoundTag());
            var receipt = receipt(player, CampaignRewardDefinition.Scope.PERSONAL, List.of((CompoundTag) new ItemStack(Items.EMERALD, 3).save(player.registryAccess())));
            Files.writeString(disk.players, "occupied");
            expectIo(helper, () -> CampaignPlayerRewards.deliver(player, receipt, disk.players));
            helper.assertValueEqual(player.totalExperience, 5, "failed player write restores experience");
            helper.assertValueEqual(player.getScore(), original.getInt("Score"), "failed player write restores score");
            helper.assertValueEqual(player.getInventory().countItem(Items.EMERALD), 2, "failed player write restores inventory");
            helper.assertFalse(CampaignPlayerRewards.delivered(player, receipt), "failed player write leaves no receipt");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_rewards")
    public static void fullInventoryKeepsPreparedLootUntilItCanBeDelivered(GameTestHelper helper) throws IOException {
        try (Disk disk = new Disk()) {
            var player = helper.makeMockServerPlayerInLevel();
            for (int index = 0; index < player.getInventory().items.size(); index++) player.getInventory().items.set(index, new ItemStack(Items.STONE, 64));
            var receipt = receipt(player, CampaignRewardDefinition.Scope.PERSONAL, List.of((CompoundTag) new ItemStack(Items.EMERALD, 3).save(player.registryAccess())));
            var data = disk.open();
            data.prepare(receipt);
            expectInvalid(helper, () -> CampaignRewardService.deliver(player, data, receipt, disk.players));
            helper.assertValueEqual(player.totalExperience, 0, "full inventory prevents partial experience delivery");
            helper.assertFalse(data.receipts().get(receipt.operation()).delivered(), "full inventory keeps the grant pending");
            player.getInventory().items.set(0, ItemStack.EMPTY);
            helper.assertTrue(CampaignRewardService.deliver(player, data, receipt, disk.players), "making room lets the same grant complete");
            helper.assertValueEqual(player.getInventory().countItem(Items.EMERALD), 3, "saved loot is delivered once");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_rewards")
    public static void staleOrConflictingPlayerReceiptsNeverReplayDeliveredRewards(GameTestHelper helper) throws IOException {
        try (Disk disk = new Disk()) {
            var player = helper.makeMockServerPlayerInLevel();
            var receipt = receipt(player, CampaignRewardDefinition.Scope.PERSONAL, List.of());
            expectInvalid(helper, () -> CampaignPlayerRewards.deliver(player, receipt.finish(), disk.players));
            CampaignPlayerRewards.deliver(player, receipt, disk.players);
            var conflicting = copy(receipt, player.getUUID(), UUID.randomUUID(), receipt.reward(), receipt.experience(), List.of());
            expectInvalid(helper, () -> CampaignPlayerRewards.deliver(player, conflicting, disk.players));
            helper.assertValueEqual(player.totalExperience, 17, "mismatched receipts cannot repeat a grant");
            player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).put(CampaignPlayerRewards.DATA_KEY, new CompoundTag());
            expectInvalid(helper, () -> CampaignPlayerRewards.deliver(player, receipt, disk.players));
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_rewards")
    public static void ownerRecoveryUsesTheNewerRewardSnapshotAndIgnoresAStaleBackup(GameTestHelper helper) throws IOException {
        try (Disk disk = new Disk()) {
            var player = helper.makeMockServerPlayerInLevel();
            var original = player.saveWithoutId(new CompoundTag());
            var receipt = receipt(player, CampaignRewardDefinition.Scope.PERSONAL, List.of());
            CampaignPlayerRewards.deliver(player, receipt, disk.players);
            Files.writeString(disk.players.resolve(player.getStringUUID() + ".dat_old"), "broken old backup");
            var selected = CampaignPlayerRewards.loadedOwnerTag(player, original, disk.players);
            helper.assertValueEqual(selected.getInt("XpTotal"), 17, "owner recovers the reward saved after level metadata");
            player.load(selected);
            helper.assertTrue(CampaignPlayerRewards.delivered(player, receipt), "valid primary is not blocked by its stale backup");
            player.giveExperiencePoints(4);
            var newerWorld = player.saveWithoutId(new CompoundTag());
            helper.assertValueEqual(CampaignPlayerRewards.loadedOwnerTag(player, newerWorld, disk.players), newerWorld, "equal receipts retain the newer world snapshot");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_rewards")
    public static void ownerRecoveryPreservesUnsupportedReceiptData(GameTestHelper helper) throws IOException {
        try (Disk disk = new Disk()) {
            var player = helper.makeMockServerPlayerInLevel();
            var original = player.saveWithoutId(new CompoundTag());
            var receipt = receipt(player, CampaignRewardDefinition.Scope.PERSONAL, List.of());
            CampaignPlayerRewards.deliver(player, receipt, disk.players);
            var future = disk.player(player);
            var ledger = future.getCompound("NeoForgeData").getCompound(Player.PERSISTED_NBT_TAG).getCompound(CampaignPlayerRewards.DATA_KEY);
            ledger.putInt("DataVersion", 999);
            IOUtilities.writeNbtCompressed(future, disk.players.resolve(player.getStringUUID() + ".dat"));
            var selected = CampaignPlayerRewards.loadedOwnerTag(player, original, disk.players);
            helper.assertValueEqual(selected.getCompound("NeoForgeData").getCompound(Player.PERSISTED_NBT_TAG).getCompound(CampaignPlayerRewards.RECOVERY_KEY)
                    .getCompound("ConflictingSnapshot").getCompound("NeoForgeData").getCompound(Player.PERSISTED_NBT_TAG).get(CampaignPlayerRewards.DATA_KEY), ledger, "future player receipts remain available for repair");
            player.load(selected);
            expectInvalid(helper, () -> CampaignPlayerRewards.deliver(player, receipt, disk.players));
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_rewards")
    public static void snapshotConflictsStayBlockedAcrossLaterPlayerSaves(GameTestHelper helper) throws IOException {
        try (Disk disk = new Disk()) {
            var player = helper.makeMockServerPlayerInLevel();
            var receipt = receipt(player, CampaignRewardDefinition.Scope.PERSONAL, List.of());
            CampaignPlayerRewards.deliver(player, receipt, disk.players);
            var original = player.saveWithoutId(new CompoundTag());
            var conflicting = disk.player(player);
            conflicting.getCompound("NeoForgeData").getCompound(Player.PERSISTED_NBT_TAG).getCompound(CampaignPlayerRewards.DATA_KEY)
                    .getList("Receipts", net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0).putUUID("Token", UUID.randomUUID());
            IOUtilities.writeNbtCompressed(conflicting, disk.players.resolve(player.getStringUUID() + ".dat"));
            var selected = CampaignPlayerRewards.loadedOwnerTag(player, original, disk.players);
            player.load(selected);
            expectInvalid(helper, () -> CampaignPlayerRewards.deliver(player, receipt, disk.players));
            var savedAgain = player.saveWithoutId(new CompoundTag());
            helper.assertValueEqual(CampaignPlayerRewards.loadedOwnerTag(player, savedAgain, disk.players), savedAgain, "reloading cannot clear the saved recovery issue");
            helper.assertValueEqual(savedAgain.getCompound("NeoForgeData").getCompound(Player.PERSISTED_NBT_TAG).getCompound(CampaignPlayerRewards.RECOVERY_KEY)
                    .getCompound("ConflictingSnapshot"), conflicting, "full conflicting snapshot remains available for repair");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_rewards")
    public static void participantRewardsExcludeCatchUpAndUncreditedWorldObservations(GameTestHelper helper) {
        UUID participant = UUID.randomUUID();
        UUID absent = UUID.randomUUID();
        UUID operation = UUID.randomUUID();
        var outcome = new CampaignInstance.OutcomeCommit(operation, "decision", "chosen", "Chosen", 1L, 1L, CampaignInstance.Origin.PLAYER_DECISION);
        var progress = new CampaignInstance.MilestoneProgress(CampaignInstance.Route.ORIGINAL, "chosen", operation, 1L, null, -1L);
        var personal = new CampaignInstance.PlayerRecord(1L, Set.of("decision"), Map.of("decision", "chosen"), Map.of("decision", progress), Set.of());
        var instance = new CampaignInstance(CAMPAIGN, UUID.randomUUID(), 1, "fingerprint", "Rewards", 0L, 1L, Map.of("decision", outcome), Map.of(participant, personal));
        for (var scope : List.of(CampaignRewardDefinition.Scope.PARTICIPANTS, CampaignRewardDefinition.Scope.SHARED)) {
            var reward = reward(scope);
            helper.assertTrue(CampaignRewardService.eligibility(instance, participant, reward).isBlank(), "actual participant may receive reward");
            helper.assertFalse(CampaignRewardService.eligibility(instance, absent, reward).isBlank(), "absent players cannot receive participation rewards");
            var observed = new CampaignInstance(CAMPAIGN, instance.instanceId(), 1, "fingerprint", "Rewards", 0L, 1L, Map.of("decision", outcome), Map.of());
            helper.assertFalse(CampaignRewardService.eligibility(observed, absent, reward).isBlank(), "world state alone does not invent participation");
        }
        helper.assertTrue(CampaignRewardService.eligibility(instance, absent, reward(CampaignRewardDefinition.Scope.PERSONAL)).isBlank(), "authored personal rewards do not require shared credit");
        var shared = reward(CampaignRewardDefinition.Scope.SHARED);
        helper.assertValueEqual(CampaignRewardService.operationId(instance.instanceId(), participant, shared, "claim"), CampaignRewardService.operationId(instance.instanceId(), absent, shared, "claim"), "shared operation has one recipient across players");
        var own = reward(CampaignRewardDefinition.Scope.PERSONAL);
        helper.assertFalse(CampaignRewardService.operationId(instance.instanceId(), participant, own, "claim").equals(CampaignRewardService.operationId(instance.instanceId(), absent, own, "claim")), "personal rewards stay separate");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_rewards")
    public static void aDifferentOperationCannotDuplicateANamedReward(GameTestHelper helper) throws IOException {
        try (Disk disk = new Disk()) {
            var player = helper.makeMockServerPlayerInLevel();
            var first = receipt(player, CampaignRewardDefinition.Scope.PERSONAL, List.of());
            var data = disk.open();
            data.prepare(first);
            var second = new CampaignRewardReceipt(UUID.randomUUID(), UUID.randomUUID(), first.campaign(), first.instance(), first.definitionRevision(),
                    first.fingerprint(), player.getUUID(), first.quest(), UUID.randomUUID(), first.reward(), "other_operation", first.scope(), first.milestone(), first.experience(), List.of(), 2L, false);
            expectInvalid(helper, () -> data.prepare(second));
            helper.assertValueEqual(disk.load().receipts().size(), 1, "one named reward cannot be awarded through a second operation");
            var damaged = data.save(new CompoundTag(), helper.getLevel().registryAccess());
            damaged.getList("Receipts", net.minecraft.nbt.Tag.TAG_COMPOUND).add(second.save());
            helper.assertFalse(CampaignRewardSavedData.load(damaged, disk.directory).diagnostic().isBlank(), "duplicate named rewards in a save are diagnosed");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_rewards")
    public static void missingItemsAndExperienceOverflowCannotPartiallyDeliverAReward(GameTestHelper helper) throws IOException {
        try (Disk disk = new Disk()) {
            var player = helper.makeMockServerPlayerInLevel();
            var missing = new CompoundTag();
            missing.putString("id", "unavailable:reward_item");
            missing.putInt("count", 1);
            var receipt = receipt(player, CampaignRewardDefinition.Scope.PERSONAL, List.of(missing));
            expectInvalid(helper, () -> CampaignPlayerRewards.deliver(player, receipt, disk.players));
            helper.assertValueEqual(player.totalExperience, 0, "missing item prevents partial experience delivery");
            player.totalExperience = Integer.MAX_VALUE - 1;
            var overflow = receipt(player, CampaignRewardDefinition.Scope.PERSONAL, List.of());
            expectInvalid(helper, () -> CampaignPlayerRewards.deliver(player, overflow, disk.players));
            helper.assertValueEqual(player.totalExperience, Integer.MAX_VALUE - 1, "reward cannot overflow vanilla experience arithmetic");
            helper.assertFalse(CampaignPlayerRewards.delivered(player, overflow), "overflow leaves no player receipt");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_rewards")
    public static void deathKeepsReceiptsWithoutRestoringLostExperience(GameTestHelper helper) throws IOException {
        try (Disk disk = new Disk()) {
            var player = helper.makeMockServerPlayerInLevel();
            var receipt = receipt(player, CampaignRewardDefinition.Scope.PERSONAL, List.of());
            CampaignPlayerRewards.deliver(player, receipt, disk.players);
            var respawned = helper.makeMockServerPlayerInLevel();
            respawned.restoreFrom(player, false);
            helper.assertTrue(CampaignPlayerRewards.delivered(respawned, receipt), "player-persisted receipts survive respawn");
        }
        helper.succeed();
    }

    private static CampaignRewardDefinition reward(CampaignRewardDefinition.Scope scope) {
        return new CampaignRewardDefinition("reward", QUEST, scope, scope == CampaignRewardDefinition.Scope.PERSONAL ? "" : "decision", "", 17, null, "/rewards/0");
    }

    private static CampaignRewardReceipt receipt(ServerPlayer player, CampaignRewardDefinition.Scope scope, List<CompoundTag> items) {
        return new CampaignRewardReceipt(UUID.randomUUID(), UUID.randomUUID(), CAMPAIGN, UUID.randomUUID(), 1, "fingerprint",
                player.getUUID(), QUEST, UUID.randomUUID(), "reward", "claim", scope, scope == CampaignRewardDefinition.Scope.PERSONAL ? "" : "decision", 17, items, 1L, false);
    }

    private static CampaignRewardReceipt copy(CampaignRewardReceipt receipt, UUID player, UUID token, String reward, int experience, List<CompoundTag> items) {
        return new CampaignRewardReceipt(receipt.operation(), token, receipt.campaign(), receipt.instance(), receipt.definitionRevision(),
                receipt.fingerprint(), player, receipt.quest(), UUID.randomUUID(), reward, receipt.authoredOperation(), receipt.scope(), receipt.milestone(), experience, items, receipt.preparedAt(), false);
    }

    private static void expectInvalid(GameTestHelper helper, Checked action) throws IOException {
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        helper.fail("invalid reward request was accepted");
    }

    private static void expectIo(GameTestHelper helper, Checked action) throws IOException {
        try { action.run(); } catch (IOException expected) { return; }
        helper.fail("failed storage unexpectedly accepted a reward");
    }

    @FunctionalInterface
    private interface Checked { void run() throws IOException; }

    private static final class Disk implements AutoCloseable {
        private final Path root = Files.createTempDirectory("campaign-rewards-");
        private final Path directory = root.resolve("data");
        private final Path players = root.resolve("players");

        private Disk() throws IOException {
        }

        private CampaignRewardSavedData open() { return CampaignRewardSavedData.load(new CompoundTag(), directory); }
        private CampaignRewardSavedData load() throws IOException {
            return CampaignRewardSavedData.load(NbtIo.readCompressed(directory.resolve(CampaignRewardSavedData.DATA_NAME + ".dat"), NbtAccounter.unlimitedHeap()).getCompound("data"), directory);
        }
        private CompoundTag player(ServerPlayer player) throws IOException { return NbtIo.readCompressed(players.resolve(player.getStringUUID() + ".dat"), NbtAccounter.unlimitedHeap()); }

        @Override
        public void close() throws IOException {
            IOUtilities.waitUntilIOWorkerComplete();
            try (var files = Files.walk(root)) {
                for (var file : files.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(file);
            }
        }
    }
}
