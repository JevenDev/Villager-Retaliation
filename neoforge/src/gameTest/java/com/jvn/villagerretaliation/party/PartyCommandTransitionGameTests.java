package com.jvn.villagerretaliation.party;

import com.jvn.villagerretaliation.network.PartyQuickCommandRequestPayload;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

@GameTestHolder("party_commands")
@PrefixGameTestTemplate(false)
public final class PartyCommandTransitionGameTests {
    @net.minecraft.gametest.framework.GameTestGenerator
    public static java.util.List<net.minecraft.gametest.framework.TestFunction> establishedCommandScenarios() {
        return java.util.List.of(
                scenario("move_route", PartyGameTests::moveToUsesSharedNodeRouteNavigation),
                scenario("move_hold", PartyGameTests::moveToHoldsUntilCommanderReturnsThenRegroups),
                scenario("regroup", PartyGameTests::fallBackCancelsActiveMoveToPath),
                scenario("unloaded_regroup", PartyGameTests::unloadingVillagerClearsOrphanedRegroupSuppression),
                scenario("gather", PartyGameTests::partyGatherDropsCollectsItemsAndMoveToOverridesIt),
                scenario("loot", PartyGameTests::partyLootContainersEmptiesContainersNearPing),
                scenario("gather_unavailable", PartyGameTests::persistentMoveToSurvivesUnavailableGatherCommands),
                scenario("guard", PartyGameTests::standGuardUsesMainhandShieldWithOffhandTotem));
    }

    private static net.minecraft.gametest.framework.TestFunction scenario(
            String name, java.util.function.Consumer<GameTestHelper> test) {
        return new net.minecraft.gametest.framework.TestFunction("party_commands_" + name,
                "partycommandtransitiongametests." + name, "party_commands:empty", 200, 0L, true, test);
    }

    @GameTest(template = "empty")
    public static void directFollowMustCancelQuickMove(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper)) {
            fixture.move();
            helper.assertTrue(PartyVillagerContractService.setFollowing(fixture.player, fixture.villager).success(), "follow action accepted");
            helper.assertFalse(PartyQuickCommandService.hasActiveMoveToOrder(fixture.villager), "follow must cancel prior quick move");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void directStayMustCancelQuickMove(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper)) {
            fixture.move();
            helper.assertTrue(PartyVillagerContractService.setStaying(fixture.player, fixture.villager).success(), "stay action accepted");
            helper.assertFalse(PartyQuickCommandService.hasActiveMoveToOrder(fixture.villager), "stay must cancel prior quick move");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void removedCommanderMustLosePersistentOrders(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper)) {
            UUID delegate = UUID.randomUUID();
            fixture.data.addPlayer(fixture.party, delegate);
            fixture.record.assignCommander(delegate);
            fixture.move();
            fixture.record.setMoveToReturnCommander(delegate);
            helper.assertTrue(PartyService.removePlayer(fixture.player, delegate).success(), "delegate removed");
            helper.assertValueEqual(fixture.record.commanderId(), fixture.player.getUUID(), "command returns to owner");
            helper.assertTrue(fixture.record.moveToReturnCommanderId() == null, "removed delegate must not retain persistent move order");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void onlineOwnerCanMoveWithOfflineDelegate(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper)) {
            UUID delegate = UUID.randomUUID();
            fixture.data.addPlayer(fixture.party, delegate);
            fixture.record.assignCommander(delegate);
            fixture.move();
            PartyQuickCommandService.onVillagerTickPost(fixture.villager);
            helper.assertTrue(PartyQuickCommandService.hasActiveMoveToOrder(fixture.villager), "online owner's accepted command must survive offline delegate");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void commanderReassignmentClearsLoadedAndSavedOrders(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper)) {
            UUID delegate = UUID.randomUUID();
            fixture.data.addPlayer(fixture.party, delegate);
            fixture.record.setCommanderEquipmentAccess(true);
            fixture.move();
            PartyCommandService.reassignCommander(helper.getLevel(), fixture.record, delegate);
            helper.assertFalse(PartyQuickCommandService.hasActiveMoveToOrder(fixture.villager), "reassignment cancels live movement");
            helper.assertFalse(fixture.record.commanderEquipmentAccess(), "new commander needs fresh equipment permission");
            var restored = PartyVillagerRecord.load(fixture.record.save());
            helper.assertTrue(restored.moveToReturnCommanderId() == null, "old movement must not return on reload");
            helper.assertValueEqual(restored.commanderId(), delegate, "new commander survives reload");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void delegateLogoutPreservesOwnersActiveOrder(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper)) {
            var delegate = net.neoforged.neoforge.common.util.FakePlayerFactory.get(helper.getLevel(),
                    new GameProfile(UUID.randomUUID(), "delegate"));
            fixture.data.addPlayer(fixture.party, delegate.getUUID());
            fixture.record.assignCommander(delegate.getUUID());
            fixture.move();
            CompanionLifecycleService.onLogout(delegate);
            helper.assertTrue(PartyQuickCommandService.hasActiveMoveToOrder(fixture.villager), "delegate logout preserves owner's order");
            PartyQuickCommandService.onVillagerTickPost(fixture.villager);
            helper.assertTrue(PartyQuickCommandService.hasActiveMoveToOrder(fixture.villager), "owner's order keeps running");
            CompanionLifecycleService.onLogout(fixture.player);
            helper.assertFalse(PartyQuickCommandService.hasActiveMoveToOrder(fixture.villager), "issuer logout stops live movement");
        }
        helper.succeed();
    }

    private static final class Fixture implements AutoCloseable {
        final GameTestHelper helper;
        final ServerPlayer player;
        final EmbeddedChannel channel;
        final Villager villager;
        final PartySavedData data;
        final PartyRecord party;
        final PartyVillagerRecord record;

        Fixture(GameTestHelper helper) {
            this.helper = helper;
            var level = helper.getLevel();
            for (int x = 0; x <= 14; x++) {
                for (int z = 0; z <= 4; z++) helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
            var profile = new GameProfile(UUID.randomUUID(), "order_" + UUID.randomUUID().toString().substring(0, 8));
            var cookie = CommonListenerCookie.createInitial(profile, false);
            player = new ServerPlayer(level.getServer(), level, profile, cookie.clientInformation());
            var connection = new Connection(PacketFlow.SERVERBOUND);
            channel = new EmbeddedChannel(connection);
            level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
            NetworkRegistry.configureMockConnection(connection);
            BlockPos origin = helper.absolutePos(new BlockPos(2, 2, 2));
            player.moveTo(origin.getX() - 0.5D, origin.getY(), origin.getZ() + 0.5D, 0.0F, 0.0F);
            villager = EntityType.VILLAGER.create(level);
            villager.moveTo(origin.getX() + 0.5D, origin.getY(), origin.getZ() + 0.5D, 0.0F, 0.0F);
            level.addFreshEntity(villager);
            level.tickNonPassenger(villager);
            data = PartySavedData.get(level);
            party = data.createParty(player.getUUID(), level.getGameTime());
            record = new PartyVillagerRecord(villager.getUUID(), player.getUUID(), UUID.randomUUID(), 0,
                    PartyCommandMode.FOLLOW, null, null, level.getGameTime(), Long.MAX_VALUE, 1, 0,
                    "Companion", "minecraft:none", level.dimension().location(), origin);
            data.addVillager(party, record);
            PartyVillagerContractService.onVillagerLoaded(villager);
        }

        void move() {
            PartyQuickCommandService.handle(player, new PartyQuickCommandRequestPayload(PartyQuickCommand.MOVE_TO,
                    PartyQuickCommandRequestPayload.NO_ENTITY, helper.absolutePos(new BlockPos(10, 2, 2))));
            helper.assertTrue(PartyQuickCommandService.hasActiveMoveToOrder(villager), "quick move accepted");
        }

        public void close() {
            PartyQuickCommandService.onVillagerUnloaded(villager);
            data.removeParty(party.id());
            villager.discard();
            helper.getLevel().getServer().getPlayerList().remove(player);
            channel.finishAndReleaseAll();
        }
    }
}
