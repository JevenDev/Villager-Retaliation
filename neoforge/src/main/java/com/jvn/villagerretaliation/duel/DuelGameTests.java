package com.jvn.villagerretaliation.duel;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.interaction.VillagerCurrencyPayment;
import com.jvn.villagerretaliation.interaction.VillagerCurrencyResources;
import com.jvn.villagerretaliation.interaction.VillagerWalletService;
import com.jvn.villagerretaliation.profile.VillagerProfileManager;
import com.jvn.villagerretaliation.profile.VillagerSocialAttribute;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class DuelGameTests {
    private static final String EMPTY_TEMPLATE = "empty";

    private DuelGameTests() {}

    @GameTest(template = EMPTY_TEMPLATE)
    public static void recordsAreIsolatedPerPlayerAndLossStreakRefuses(GameTestHelper helper) {
        DuelSavedData data = new DuelSavedData();
        UUID villager = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        data.markStarted(villager, first, 100L);
        data.complete(villager, first, DuelResult.PLAYER_WIN);
        data.complete(villager, first, DuelResult.PLAYER_WIN);
        DuelSavedData.DuelRecord refused = data.complete(villager, first, DuelResult.PLAYER_WIN);
        helper.assertTrue(refused.refuses() && refused.consecutiveLosses() == 3,
                "three consecutive losses should permanently refuse the opponent");
        helper.assertValueEqual(data.record(villager, second), DuelSavedData.DuelRecord.EMPTY,
                "duel records must remain isolated per player");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void villagerVictoryResetsLossStreakAndQueuesReactions(GameTestHelper helper) {
        DuelSavedData data = new DuelSavedData();
        UUID villager = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        data.complete(villager, player, DuelResult.PLAYER_WIN);
        DuelSavedData.DuelRecord record = data.complete(villager, player, DuelResult.VILLAGER_WIN);
        helper.assertValueEqual(record.consecutiveLosses(), 0, "villager victory should reset its loss streak");
        helper.assertValueEqual(data.consumeReaction(villager, player), DuelSavedData.Reaction.GLOAT,
                "victory should queue a one-time gloat");
        helper.assertValueEqual(data.consumeReaction(villager, player), DuelSavedData.Reaction.SULK,
                "later queued reactions should remain available");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void recordsHistoryAndStoryAcknowledgementsSerialize(GameTestHelper helper) {
        DuelSavedData data = new DuelSavedData();
        UUID villager = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        UUID speaker = UUID.randomUUID();
        UUID village = UUID.randomUUID();
        data.markStarted(villager, player, 500L);
        DuelSavedData.DuelRecord record = data.complete(villager, player, DuelResult.VILLAGER_WIN);
        UUID eventId = UUID.randomUUID();
        data.remember(new DuelSavedData.DuelMemory(eventId, villager, player, "Ada", "Player",
                DuelResult.VILLAGER_WIN, 16, 600L, new BlockPos(1, 2, 3).asLong(), village,
                record.villagerWins(), record.villagerLosses()));
        data.acknowledgeStory(speaker, player, eventId);
        CompoundTag saved = data.save(new CompoundTag(), helper.getLevel().registryAccess());
        DuelSavedData loaded = DuelSavedData.load(saved, helper.getLevel().registryAccess());
        helper.assertValueEqual(loaded.record(villager, player), record, "duel record did not survive NBT");
        helper.assertValueEqual(loaded.history().size(), 1, "duel history did not survive NBT");
        helper.assertTrue(loaded.storyAcknowledged(speaker, player, eventId),
                "story acknowledgement did not survive NBT");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void villageHistoryIsCappedAtSixtyFour(GameTestHelper helper) {
        DuelSavedData data = new DuelSavedData();
        UUID villager = UUID.randomUUID(), player = UUID.randomUUID(), village = UUID.randomUUID();
        for (int index = 0; index < 70; index++) {
            data.remember(new DuelSavedData.DuelMemory(UUID.randomUUID(), villager, player, "Ada", "Player",
                    DuelResult.DRAW, 0, index, BlockPos.ZERO.asLong(), village, 0, 0));
        }
        helper.assertValueEqual(data.history().size(), 64, "village duel history must remain bounded");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void assignedLoadoutRestoresInventoryAndRejectsSmuggling(GameTestHelper helper) {
        Participant participant = participant(helper);
        ServerPlayer player = participant.player();
        Villager villager = participant.villager();
        player.getInventory().setItem(4, new ItemStack(Items.APPLE, 3));
        player.getInventory().selected = 4;
        villager.getInventory().setItem(0, new ItemStack(Items.BREAD, 2));
        villager.setCanPickUpLoot(true);

        DuelService.StartResult start = DuelService.start(player, villager, DuelLoadout.MELEE, 0);
        helper.assertTrue(start.started(), "assigned duel should start: " + start.reason());
        helper.assertValueEqual(player.getInventory().countItem(Items.APPLE), 0,
                "assigned gear must isolate the pre-duel inventory");
        helper.assertValueEqual(player.getInventory().selected, 0,
                "assigned gear should select its first usable hotbar slot");
        helper.assertTrue(!DuelService.allowsInventoryClick(
                        player, player.inventoryMenu, InventoryMenu.USE_ROW_SLOT_START, ClickType.PICKUP),
                "server must reject assigned-loadout inventory packets");

        ItemEntity pickup = new ItemEntity(participant.level(), player.getX(), player.getY(), player.getZ(),
                new ItemStack(Items.DIAMOND));
        ItemEntityPickupEvent.Pre pickupEvent = new ItemEntityPickupEvent.Pre(player, pickup);
        DuelService.onItemPickup(pickupEvent);
        helper.assertValueEqual(pickupEvent.canPickup(), TriState.FALSE,
                "duel participants must not pick up world items");

        ItemTossEvent tossEvent = new ItemTossEvent(
                new ItemEntity(participant.level(), player.getX(), player.getY(), player.getZ(),
                        new ItemStack(Items.DIAMOND)),
                player);
        DuelService.onItemToss(tossEvent);
        helper.assertTrue(tossEvent.isCanceled(), "duel participants must not toss items");

        player.getInventory().setItem(0, ItemStack.EMPTY);
        player.getInventory().add(new ItemStack(Items.DIAMOND, 17));
        helper.assertTrue(DuelService.resolveForTest(player, DuelResult.DRAW), "live duel should resolve once");
        helper.assertValueEqual(player.getInventory().countItem(Items.APPLE), 3,
                "pre-duel inventory must be restored exactly");
        helper.assertValueEqual(player.getInventory().countItem(Items.DIAMOND), 0,
                "items introduced during an assigned duel must not escape restoration");
        helper.assertValueEqual(player.getInventory().selected, 4,
                "selected hotbar slot must be restored");
        helper.assertValueEqual(villager.getInventory().countItem(Items.BREAD), 2,
                "villager inventory must be restored exactly");
        helper.assertTrue(villager.canPickUpLoot(), "villager pickup policy must be restored");
        pickup.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void bringYourOwnMutationsPersistWithoutSnapshotDuplication(GameTestHelper helper) {
        Participant participant = participant(helper);
        ServerPlayer player = participant.player();
        player.getInventory().setItem(0, new ItemStack(Items.APPLE, 3));

        DuelService.StartResult start = DuelService.start(
                player, participant.villager(), DuelLoadout.BRING_YOUR_OWN, 0);
        helper.assertTrue(start.started(), "BYO duel should start: " + start.reason());
        helper.assertTrue(DuelService.allowsInventoryClick(
                        player, player.inventoryMenu, InventoryMenu.USE_ROW_SLOT_START, ClickType.PICKUP),
                "BYO players should be able to move gear they brought");
        helper.assertTrue(!DuelService.allowsInventoryClick(
                        player, player.inventoryMenu, 1, ClickType.PICKUP),
                "crafting slots must remain locked during BYO duels");
        helper.assertTrue(!DuelService.allowsInventoryClick(
                        player, player.inventoryMenu, InventoryMenu.USE_ROW_SLOT_START, ClickType.THROW),
                "BYO players must not drop items");

        player.getInventory().getItem(0).shrink(1);
        helper.assertTrue(DuelService.resolveForTest(player, DuelResult.DRAW), "BYO duel should resolve");
        helper.assertValueEqual(player.getInventory().countItem(Items.APPLE), 2,
                "consumed BYO items must stay consumed instead of being restored and duplicated");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void payoutSettlesExactlyOnce(GameTestHelper helper) {
        Participant participant = participant(helper);
        ServerPlayer player = participant.player();
        Villager villager = participant.villager();
        ItemStack currency = VillagerCurrencyResources.createStack(participant.level().getServer(), 8);
        helper.assertTrue(!currency.isEmpty() && currency.getCount() == 8, "test currency stack must resolve");
        player.getInventory().add(currency);
        int walletBefore = VillagerWalletService.getCurrentEmeralds(villager);
        if (walletBefore < 8) {
            VillagerWalletService.addCurrency(
                    villager, 8 - walletBefore, VillagerWalletService.WalletSource.DUEL);
            walletBefore = VillagerWalletService.getCurrentEmeralds(villager);
        }

        DuelService.StartResult start = DuelService.start(player, villager, DuelLoadout.MELEE, 8);
        helper.assertTrue(start.started(), "staked duel should start: " + start.reason());
        helper.assertValueEqual(VillagerCurrencyPayment.count(player), 0,
                "player stake must leave the inventory before snapshotting");
        helper.assertTrue(DuelService.resolveForTest(player, DuelResult.PLAYER_WIN),
                "winning duel should resolve");
        helper.assertValueEqual(VillagerCurrencyPayment.count(player), 16,
                "winner should receive the two-stake pot exactly once");
        helper.assertTrue(!DuelService.resolveForTest(player, DuelResult.PLAYER_WIN),
                "completed duel must reject a second settlement");
        helper.assertValueEqual(VillagerCurrencyPayment.count(player), 16,
                "replayed completion must not duplicate payout");
        if (!VillagerWalletService.hasUnlimitedCurrency()) {
            helper.assertValueEqual(VillagerWalletService.getCurrentEmeralds(villager), walletBefore - 8,
                    "losing villager stake should remain deducted");
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void logoutRestoresAssignedSnapshot(GameTestHelper helper) {
        Participant participant = participant(helper);
        ServerPlayer player = participant.player();
        player.getInventory().setItem(2, new ItemStack(Items.GOLD_INGOT, 5));
        DuelService.StartResult start = DuelService.start(
                player, participant.villager(), DuelLoadout.ARMORED, 0);
        helper.assertTrue(start.started(), "logout duel should start: " + start.reason());
        player.getInventory().add(new ItemStack(Items.DIAMOND_BLOCK, 4));

        DuelService.onPlayerLogout(player);
        helper.assertTrue(!DuelService.isParticipant(player), "logout must remove active duel state");
        helper.assertValueEqual(player.getInventory().countItem(Items.GOLD_INGOT), 5,
                "logout must restore the disconnected player's snapshot");
        helper.assertValueEqual(player.getInventory().countItem(Items.DIAMOND_BLOCK), 0,
                "logout restoration must discard duel-only inventory");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 340)
    public static void timeoutEndsInDrawAndRestoresEquipment(GameTestHelper helper) {
        Participant participant = participant(helper);
        ServerPlayer player = participant.player();
        Villager villager = participant.villager();
        player.getInventory().setItem(1, new ItemStack(Items.CARROT, 6));
        villager.setNoAi(true);
        villager.moveTo(player.getX() + 7.0D, player.getY(), player.getZ(), 0.0F, 0.0F);

        int previousTimeout = VillagerRetaliationConfig.DUEL_TIMEOUT_TICKS.get();
        DuelService.StartResult start;
        try {
            VillagerRetaliationConfig.DUEL_TIMEOUT_TICKS.set(200);
            start = DuelService.start(player, villager, DuelLoadout.BARE_HANDED, 0);
        } finally {
            VillagerRetaliationConfig.DUEL_TIMEOUT_TICKS.set(previousTimeout);
        }
        helper.assertTrue(start.started(), "timeout duel should start: " + start.reason());
        UUID duelId = start.duelId();

        helper.runAfterDelay(280, () -> {
            try {
                helper.assertTrue(!DuelService.isParticipant(player), "timeout must finish the live duel");
                helper.assertValueEqual(player.getInventory().countItem(Items.CARROT), 6,
                        "timeout must restore assigned inventory");
                helper.assertTrue(DuelSavedData.get(participant.level()).history().stream()
                                .anyMatch(memory -> memory.id().equals(duelId)
                                        && memory.result() == DuelResult.DRAW),
                        "timeout must record a draw");
                helper.succeed();
            } finally {
                DuelService.resolveForTest(player, DuelResult.CANCELLED);
            }
        });
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 160)
    public static void boundaryForfeitAwardsVillagerWin(GameTestHelper helper) {
        Participant participant = participant(helper);
        ServerPlayer player = participant.player();
        Villager villager = participant.villager();
        player.getInventory().setItem(0, new ItemStack(Items.POTATO, 4));

        int previousRadius = VillagerRetaliationConfig.DUEL_ARENA_RADIUS.get();
        int previousGrace = VillagerRetaliationConfig.DUEL_BOUNDARY_GRACE_TICKS.get();
        DuelService.StartResult start;
        try {
            VillagerRetaliationConfig.DUEL_ARENA_RADIUS.set(8);
            VillagerRetaliationConfig.DUEL_BOUNDARY_GRACE_TICKS.set(0);
            start = DuelService.start(player, villager, DuelLoadout.BARE_HANDED, 0);
        } finally {
            VillagerRetaliationConfig.DUEL_ARENA_RADIUS.set(previousRadius);
            VillagerRetaliationConfig.DUEL_BOUNDARY_GRACE_TICKS.set(previousGrace);
        }
        helper.assertTrue(start.started(), "boundary duel should start: " + start.reason());
        player.moveTo(player.getX() + 24.0D, player.getY(), player.getZ(), 0.0F, 0.0F);

        helper.runAfterDelay(90, () -> {
            try {
                helper.assertTrue(!DuelService.isParticipant(player), "zero-grace boundary exit must forfeit");
                DuelSavedData.DuelRecord record = DuelSavedData.get(participant.level())
                        .record(villager.getUUID(), player.getUUID());
                helper.assertValueEqual(record.villagerWins(), 1,
                        "player boundary forfeit must award the villager win");
                helper.assertValueEqual(player.getInventory().countItem(Items.POTATO), 4,
                        "boundary resolution must restore assigned inventory");
                helper.succeed();
            } finally {
                DuelService.resolveForTest(player, DuelResult.CANCELLED);
            }
        });
    }

    private static Participant participant(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        GameProfile profile = new GameProfile(
                UUID.randomUUID(), "duel-" + UUID.randomUUID().toString().substring(0, 8));
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);
        ServerPlayer player = new ServerPlayer(
                level.getServer(), level, cookie.gameProfile(), cookie.clientInformation()) {
            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return false;
            }
        };
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        BlockPos playerPos = helper.absolutePos(new BlockPos(2, 2, 2));
        player.moveTo(playerPos.getX() + 0.5D, playerPos.getY(), playerPos.getZ() + 0.5D, 0.0F, 0.0F);
        Villager villager = helper.spawn(EntityType.VILLAGER, 3, 2, 2);
        VillagerProfileManager.setAttribute(level, villager, VillagerSocialAttribute.GUTS, 100);
        return new Participant(level, player, villager);
    }

    private record Participant(ServerLevel level, ServerPlayer player, Villager villager) {}}
