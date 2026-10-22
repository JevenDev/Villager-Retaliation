package com.jvn.villagerretaliation.party;

import com.jvn.villagerretaliation.network.PartyRosterSyncPayload;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

@GameTestHolder("party_commands")
@PrefixGameTestTemplate(false)
public final class PartyCommandGameTests {
    private PartyCommandGameTests() {
    }

    @GameTest(template = "empty")
    public static void leavingPartyKeepsPersonalCompanionsInClientRoster(GameTestHelper helper) throws Exception {
        assertSeparatedCompanionRoster(helper, false);
    }

    @GameTest(template = "empty")
    public static void kickedPlayerKeepsPersonalCompanionsInClientRoster(GameTestHelper helper) throws Exception {
        assertSeparatedCompanionRoster(helper, true);
    }

    @GameTest(template = "empty")
    public static void ownershipTransferWithinPartyResetsCommandsButSameOwnerIsUnchanged(GameTestHelper helper) {
        var level = helper.getLevel();
        PartySavedData data = PartySavedData.get(level);
        UUID owner = UUID.randomUUID();
        UUID commander = UUID.randomUUID();
        PartyRecord party = data.createParty(owner, level.getGameTime());
        data.addPlayer(party, commander);
        var record = new PartyVillagerRecord(
                UUID.randomUUID(), owner, UUID.randomUUID(), 0, PartyCommandMode.FOLLOW,
                null, null, level.getGameTime(), Long.MAX_VALUE, 1, 0, "Companion", "minecraft:none",
                Level.OVERWORLD.location(), BlockPos.ZERO);
        data.addVillager(party, record);
        record.assignCommander(commander);
        record.setCommanderEquipmentAccess(true);
        record.setStaying(level.dimension().location(), new BlockPos(1, 2, 3));
        record.setMoveToReturnCommander(commander);
        try {
            helper.assertTrue(PartyService.transferVillagerOwnership(level, record.villagerId(), owner).success(),
                    "transferring to the same owner succeeds without changing assignments");
            helper.assertValueEqual(record.commanderId(), commander, "same owner preserves delegated command");
            helper.assertTrue(record.commanderEquipmentAccess(), "same owner preserves equipment permission");
            helper.assertValueEqual(record.moveToReturnCommanderId(), commander, "same owner preserves orders");

            helper.assertTrue(PartyService.transferVillagerOwnership(level, record.villagerId(), commander).success(),
                    "ownership can transfer within the party");
            helper.assertValueEqual(record.recruiterId(), commander, "the new owner is recorded");
            helper.assertValueEqual(record.commanderId(), commander, "the new owner commands the companion");
            helper.assertFalse(record.commanderEquipmentAccess(), "ownership changes reset delegated equipment access");
            helper.assertTrue(record.moveToReturnCommanderId() == null, "ownership changes discard the previous order");
            helper.assertValueEqual(record.commandMode(), PartyCommandMode.FOLLOW, "the companion follows its new owner");
            PartyVillagerRecord restored = PartyVillagerRecord.load(record.save());
            helper.assertValueEqual(restored.commanderId(), commander, "the new commander survives serialization");
            helper.assertTrue(restored.moveToReturnCommanderId() == null, "old orders do not return after reload");
        } finally {
            data.removeParty(party.id());
        }
        helper.succeed();
    }

    private static void assertSeparatedCompanionRoster(GameTestHelper helper, boolean kicked) throws Exception {
        var level = helper.getLevel();
        var server = level.getServer();
        var profile = new GameProfile(UUID.randomUUID(), "roster_" + UUID.randomUUID().toString().substring(0, 8));
        var cookie = CommonListenerCookie.createInitial(profile, false);
        var member = new ServerPlayer(server, level, profile, cookie.clientInformation());
        var connection = new Connection(PacketFlow.SERVERBOUND);
        var channel = new EmbeddedChannel(connection);
        ServerPlayer leader = FakePlayerFactory.get(level,
                new GameProfile(UUID.randomUUID(), "leader_" + UUID.randomUUID().toString().substring(0, 8)));
        PartySavedData data = PartySavedData.get(level);
        PartyRecord party = data.createParty(leader.getUUID(), level.getGameTime());
        UUID villagerId = UUID.randomUUID();
        try {
            server.getPlayerList().placeNewPlayer(connection, member, cookie);
            NetworkRegistry.configureMockConnection(connection);
            data.addPlayer(party, member.getUUID());
            data.addVillager(party, new PartyVillagerRecord(
                    villagerId, member.getUUID(), UUID.randomUUID(), 0, PartyCommandMode.FOLLOW,
                    null, null, level.getGameTime(), Long.MAX_VALUE, 1, 0, "Companion", "minecraft:none",
                    Level.OVERWORLD.location(), BlockPos.ZERO));
            latestRoster(channel);

            var dispatcher = server.getCommands().getDispatcher();
            String command = kicked ? "vr party kick @a[name=" + profile.getName() + "]" : "vr party leave";
            var source = (kicked ? leader : member).createCommandSourceStack().withPermission(2);
            dispatcher.execute(command, source);
            PartyRosterSyncPayload latest = latestRoster(channel);
            PartyRecord personal = data.partyForPlayer(member.getUUID()).orElseThrow();
            helper.assertFalse(personal.id().equals(party.id()), "the departing owner receives a personal party");
            helper.assertTrue(latest != null && latest.active(),
                    "the final roster packet must preserve the departing owner's personal party");
            helper.assertValueEqual(latest.partyId(), personal.id(), "the client receives the new personal party");
            helper.assertTrue(latest.villagers().stream().anyMatch(entry -> entry.villagerId().equals(villagerId)),
                    "the client retains the departing owner's companion");

            data.removeParty(personal.id());
            data.addPlayer(party, member.getUUID());
            dispatcher.execute(command, source);
            latest = latestRoster(channel);
            helper.assertTrue(latest != null && !latest.active(),
                    "leaving without companions must still clear the client roster");
        } finally {
            data.partyForPlayer(member.getUUID()).ifPresent(personal -> data.removeParty(personal.id()));
            data.removeParty(party.id());
            server.getPlayerList().remove(member);
            channel.finishAndReleaseAll();
        }
        helper.succeed();
    }

    private static PartyRosterSyncPayload latestRoster(EmbeddedChannel channel) {
        channel.runPendingTasks();
        PartyRosterSyncPayload latest = null;
        Object outgoing;
        while ((outgoing = channel.readOutbound()) != null) {
            if (outgoing instanceof ClientboundCustomPayloadPacket packet
                    && packet.payload() instanceof PartyRosterSyncPayload roster) {
                latest = roster;
            }
        }
        return latest;
    }
}
