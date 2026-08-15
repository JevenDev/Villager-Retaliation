package com.jvn.villagerretaliation.duel;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.interaction.VillagerCurrencyPayment;
import com.jvn.villagerretaliation.interaction.VillagerCurrencyResources;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class PlayerDuelGameTests {
    private static final String EMPTY_TEMPLATE = "empty";

    private PlayerDuelGameTests() {
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void challengeRequiresBothPlayersInsideDuelRadius(GameTestHelper helper) {
        ServerPlayer challenger = player(helper, "range-a", new BlockPos(2, 2, 2));
        int radius = VillagerRetaliationConfig.DUEL_ARENA_RADIUS.get();
        ServerPlayer opponent = player(helper, "range-b", new BlockPos(2 + radius + 2, 2, 2));

        PlayerDuelService.challenge(
                challenger, opponent, DuelLoadout.BRING_YOUR_OWN.id(), 0);

        helper.assertFalse(PlayerDuelService.hasInvitationForTest(challenger, opponent),
                "a challenge outside the configured duel radius must be rejected");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void acceptanceRechecksDuelRadius(GameTestHelper helper) {
        ServerPlayer challenger = player(helper, "accept-a", new BlockPos(2, 2, 2));
        ServerPlayer opponent = player(helper, "accept-b", new BlockPos(3, 2, 2));
        PlayerDuelService.challenge(
                challenger, opponent, DuelLoadout.BRING_YOUR_OWN.id(), 0);
        helper.assertTrue(PlayerDuelService.hasInvitationForTest(challenger, opponent),
                "an in-range challenge should create an invitation");

        int radius = VillagerRetaliationConfig.DUEL_ARENA_RADIUS.get();
        opponent.moveTo(
                challenger.getX() + radius + 1.0D, challenger.getY(), challenger.getZ(),
                0.0F, 0.0F);
        PlayerDuelService.accept(opponent, challenger);
        helper.assertFalse(DuelService.isParticipant(challenger)
                        || DuelService.isParticipant(opponent),
                "acceptance must fail when either player has left the duel radius");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void playerBringYourOwnIgnoresVillageDuelRestriction(GameTestHelper helper) {
        ServerPlayer challenger = player(helper, "byo-a", new BlockPos(2, 2, 2));
        ServerPlayer opponent = player(helper, "byo-b", new BlockPos(3, 2, 2));
        boolean previous = VillagerRetaliationConfig.ALLOW_BRING_YOUR_OWN_DUEL_LOADOUT.get();
        try {
            VillagerRetaliationConfig.ALLOW_BRING_YOUR_OWN_DUEL_LOADOUT.set(false);
            PlayerDuelService.challenge(
                    challenger, opponent, DuelLoadout.BRING_YOUR_OWN.id(), 0);
            helper.assertTrue(PlayerDuelService.hasInvitationForTest(challenger, opponent),
                    "the village-only BYO restriction must not hide player BYO challenges");

            PlayerDuelService.accept(opponent, challenger);
            helper.assertTrue(DuelService.isParticipant(challenger)
                            && DuelService.isParticipant(opponent),
                    "accepting an in-range player BYO challenge should start the duel");
            helper.succeed();
        } finally {
            VillagerRetaliationConfig.ALLOW_BRING_YOUR_OWN_DUEL_LOADOUT.set(previous);
            PlayerDuelService.resolveForTest(challenger, true);
        }
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void assignedKitRestoresBothInventoriesAndPaysWinner(GameTestHelper helper) {
        ServerPlayer challenger = player(helper, "kit-a", new BlockPos(2, 2, 2));
        ServerPlayer opponent = player(helper, "kit-b", new BlockPos(3, 2, 2));
        challenger.getInventory().add(new ItemStack(Items.COBBLESTONE, 3));
        opponent.getInventory().add(new ItemStack(Items.DIRT, 5));
        challenger.getInventory().add(
                VillagerCurrencyResources.createStack(challenger.getServer(), 8));
        opponent.getInventory().add(
                VillagerCurrencyResources.createStack(opponent.getServer(), 8));

        PlayerDuelService.challenge(challenger, opponent, DuelLoadout.MELEE.id(), 8);
        PlayerDuelService.accept(opponent, challenger);
        helper.assertTrue(challenger.getMainHandItem().is(Items.IRON_SWORD)
                        && opponent.getMainHandItem().is(Items.IRON_SWORD),
                "the selected preset kit should be assigned to both players");
        helper.assertValueEqual(VillagerCurrencyPayment.count(challenger), 0,
                "the challenger's wager should be held in escrow");
        helper.assertValueEqual(VillagerCurrencyPayment.count(opponent), 0,
                "the opponent's wager should be held in escrow");

        helper.assertTrue(PlayerDuelService.resolveForTest(challenger, true),
                "the active player duel should resolve");
        helper.assertValueEqual(challenger.getInventory().countItem(Items.COBBLESTONE), 3,
                "the challenger's original inventory should be restored");
        helper.assertValueEqual(opponent.getInventory().countItem(Items.DIRT), 5,
                "the opponent's original inventory should be restored");
        helper.assertValueEqual(VillagerCurrencyPayment.count(challenger), 16,
                "the winner should receive the complete wager pot");
        helper.assertValueEqual(VillagerCurrencyPayment.count(opponent), 0,
                "the loser should not recover their wager");
        helper.succeed();
    }

    private static ServerPlayer player(
            GameTestHelper helper, String prefix, BlockPos relativePosition) {
        ServerLevel level = helper.getLevel();
        GameProfile profile = new GameProfile(
                UUID.randomUUID(), prefix + "-" + UUID.randomUUID().toString().substring(0, 6));
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
        BlockPos position = helper.absolutePos(relativePosition);
        player.moveTo(
                position.getX() + 0.5D, position.getY(), position.getZ() + 0.5D,
                0.0F, 0.0F);
        return player;
    }
}
