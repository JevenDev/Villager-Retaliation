package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.network.SettlementManagementActionPayload;
import com.jvn.villagerretaliation.network.SettlementViewChunkPayload;
import com.jvn.villagerretaliation.network.SettlementViewRequestPayload;
import com.jvn.villagerretaliation.network.SettlementViewStatePayload;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class SettlementViewGameTests {
    private SettlementViewGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_view")
    public static void everyStageKeepsCameraAndChunksWithinItsBoundary(GameTestHelper helper) {
        var fixture = fixture(helper);
        for (SettlementStage stage : SettlementStage.values()) {
            helper.assertValueEqual(fixture.town().stage(), stage, "the fixture follows each town stage");
            for (int direction : List.of(-1, 1)) {
                var window = SettlementViewWindow.around(fixture.town(), direction * Double.MAX_VALUE,
                        -direction * Double.MAX_VALUE, Float.MAX_VALUE);
                int radius = stage.radius() + SettlementViewWindow.BORDER;
                helper.assertValueEqual(window.radius(), radius, "the small camera border follows the stage");
                helper.assertValueEqual(window.x(), (double) fixture.town().center().getX() + direction * radius,
                        "remote coordinates cannot exceed the east or west boundary");
                helper.assertValueEqual(window.z(), (double) fixture.town().center().getZ() - direction * radius,
                        "remote coordinates cannot exceed the north or south boundary");
                helper.assertValueEqual(window.height(), SettlementViewWindow.MAX_HEIGHT, "zoom has a finite upper limit");
                helper.assertTrue(window.chunks().size() <= 121, "the camera requests a bounded chunk window");
                helper.assertValueEqual(new HashSet<>(window.chunks()).size(), window.chunks().size(), "chunk tickets cannot be duplicated");
                for (ChunkPos chunk : window.chunks()) {
                    helper.assertTrue(chunk.getMaxBlockX() >= window.center().getX() - radius
                            && chunk.getMinBlockX() <= window.center().getX() + radius
                            && chunk.getMaxBlockZ() >= window.center().getZ() - radius
                            && chunk.getMinBlockZ() <= window.center().getZ() + radius, "every requested chunk intersects the view boundary");
                }
            }
            fixture.town().advance();
        }
        helper.assertValueEqual(SettlementViewWindow.around(fixture.town(), fixture.town().center().getX(),
                fixture.town().center().getZ(), -10.0F).height(), SettlementViewWindow.MIN_HEIGHT, "zoom cannot enter the ground");
        helper.assertFalse(helper.getLevel().hasChunkAt(fixture.town().center()), "calculating the camera never loads terrain");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_view")
    public static void worldViewRequiresPermissionAndNeverMovesThePlayersBody(GameTestHelper helper) {
        var fixture = fixture(helper);
        ServerPlayer delegate = player(helper, "ViewDelegate");
        var body = delegate.position();
        try {
            helper.assertFalse(SettlementViewService.open(delegate, fixture.town().id()), "outsiders cannot open the world view");
            fixture.town().setPermissions(fixture.player().getUUID(), delegate.getUUID(), Set.of(SettlementPermission.VIEW));
            helper.assertTrue(SettlementManagementService.execute(delegate,
                    new SettlementManagementActionPayload(fixture.town().id().value(), new SettlementManagementActionPayload.View())),
                    "view access opens the actual management action");
            var session = SettlementViewService.session(delegate.getUUID());
            helper.assertTrue(session != null, "the server owns one view session");
            helper.assertValueEqual(delegate.position(), body, "opening does not teleport the body");
            helper.assertValueEqual(delegate.getCamera(), delegate, "the server camera remains the vulnerable player");
            helper.assertFalse(delegate.isSpectator(), "viewing does not grant spectator mode");
            fixture.town().setPermissions(fixture.player().getUUID(), delegate.getUUID(), Set.of());
            SettlementViewService.request(delegate, new SettlementViewRequestPayload(session.id, 1,
                    fixture.town().center().getX(), fixture.town().center().getZ(), 32.0F, false));
            helper.assertTrue(SettlementViewService.session(delegate.getUUID()) == null, "revoked access closes the session");
        } finally {
            SettlementViewService.close(delegate.getUUID(), false);
            helper.getLevel().getChunkSource().removeEntity(delegate);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_view")
    public static void cameraRequestsRejectForeignSessionsAndStaleMovesButAlwaysAllowClosing(GameTestHelper helper) {
        var fixture = fixture(helper);
        try {
            helper.assertTrue(SettlementViewService.open(fixture.player(), fixture.town().id()), "the leader opens a session");
            var session = SettlementViewService.session(fixture.player().getUUID());
            var original = session.window;
            SettlementViewService.request(fixture.player(), new SettlementViewRequestPayload(UUID.randomUUID(), 5, 0, 0, 64, true));
            helper.assertTrue(SettlementViewService.session(fixture.player().getUUID()) == session, "a foreign close token has no effect");
            SettlementViewService.request(fixture.player(), new SettlementViewRequestPayload(session.id, 1, Double.MAX_VALUE, Double.MAX_VALUE, 100, false));
            helper.assertValueEqual(session.requestId, 1, "the current request is accepted");
            helper.assertFalse(session.window.equals(original), "the camera moves within the bounded town");
            var accepted = session.window;
            SettlementViewService.request(fixture.player(), new SettlementViewRequestPayload(session.id, 0, 0, 0, 16, false));
            helper.assertValueEqual(session.window, accepted, "an old request cannot move the camera backwards");
            SettlementViewService.request(fixture.player(), new SettlementViewRequestPayload(session.id, 2, 0, 0, 16, false));
            helper.assertValueEqual(session.window, accepted, "rapid moves respect the server request limit");
            SettlementViewService.request(fixture.player(), new SettlementViewRequestPayload(session.id, 0, 0, 0, 16, true));
            helper.assertTrue(SettlementViewService.session(fixture.player().getUUID()) == null, "closing bypasses move throttling and stale request checks");
        } finally {
            SettlementViewService.close(fixture.player().getUUID(), false);
            helper.getLevel().getChunkSource().removeEntity(fixture.player());
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_view")
    public static void viewTicketsAreIncrementalAndReleasedWhenTheSessionChanges(GameTestHelper helper) {
        var fixture = fixture(helper);
        try {
            SettlementViewService.open(fixture.player(), fixture.town().id());
            var session = SettlementViewService.session(fixture.player().getUUID());
            SettlementViewService.update(session);
            helper.assertValueEqual(session.held.size(), 2, "a frame adds only two chunk tickets");
            SettlementViewService.update(session);
            helper.assertValueEqual(session.held.size(), 4, "later frames expand the window incrementally");
            var priorTickets = Set.copyOf(session.held);
            var distance = helper.getLevel().getChunkSource().chunkMap.getDistanceManager();
            var oldId = session.id;
            SettlementViewService.open(fixture.player(), fixture.town().id());
            helper.assertTrue(session.held.isEmpty() && session.sent.isEmpty() && session.remote.isEmpty(),
                    "replacing the camera releases the previous window");
            helper.assertTrue(priorTickets.stream().noneMatch(position -> distance.shouldForceTicks(position.toLong())),
                    "replacing the view removes its actual forced tick tickets");
            helper.assertFalse(SettlementViewService.session(fixture.player().getUUID()).id.equals(oldId),
                    "a replaced session gets a new token");
            var current = SettlementViewService.session(fixture.player().getUUID());
            SettlementViewService.update(current);
            fixture.player().hurtTime = 10;
            SettlementViewService.tick(helper.getLevel().getServer());
            helper.assertTrue(SettlementViewService.session(fixture.player().getUUID()) == null && current.held.isEmpty(),
                    "damage or disconnection releases every ticket");
        } finally {
            fixture.player().hurtTime = 0;
            SettlementViewService.close(fixture.player().getUUID(), false);
            helper.getLevel().getChunkSource().removeEntity(fixture.player());
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_view")
    public static void entityAndBlockUpdatesRequireAReadyAuthorizedCameraChunk(GameTestHelper helper) {
        var fixture = fixture(helper);
        try {
            SettlementViewService.open(fixture.player(), fixture.town().id());
            var session = SettlementViewService.session(fixture.player().getUUID());
            Villager resident = EntityType.VILLAGER.create(helper.getLevel());
            resident.setPos(fixture.town().center().getCenter());
            helper.assertFalse(SettlementViewService.tracks(fixture.player(), resident), "entities wait for their terrain packet");
            session.sent.add(resident.chunkPosition());
            helper.assertTrue(SettlementViewService.tracks(fixture.player(), resident), "ready town residents can be tracked remotely");
            helper.assertTrue(helper.getLevel().getChunkSource().chunkMap.getPlayers(resident.chunkPosition(), false).contains(fixture.player()),
                    "the native chunk broadcaster includes the remote viewer");
            resident.setPos(fixture.town().center().offset(fixture.town().stage().radius() + 17, 0, 0).getCenter());
            session.sent.add(resident.chunkPosition());
            helper.assertFalse(SettlementViewService.tracks(fixture.player(), resident), "a ready chunk cannot bypass the exact town boundary");
            fixture.player().hurtTime = 10;
            helper.assertTrue(SettlementViewService.viewers(helper.getLevel(), new ChunkPos(fixture.town().center())).isEmpty(),
                    "damage stops remote world updates immediately");
            helper.assertFalse(SettlementViewService.open(fixture.player(), fixture.town().id()), "an injured player cannot reopen the view");
        } finally {
            fixture.player().hurtTime = 0;
            SettlementViewService.close(fixture.player().getUUID(), false);
            helper.getLevel().getChunkSource().removeEntity(fixture.player());
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_view")
    public static void cameraPacketsRoundTripRealChunkContentsAndRejectInvalidCoordinates(GameTestHelper helper) {
        var fixture = fixture(helper);
        UUID id = UUID.randomUUID();
        var window = SettlementViewWindow.around(fixture.town(), fixture.town().center().getX(), fixture.town().center().getZ(), 64);
        roundTrip(helper, SettlementViewRequestPayload.STREAM_CODEC, new SettlementViewRequestPayload(id, 42, -123.5, 82.25, 24, false));
        roundTrip(helper, SettlementViewStatePayload.STREAM_CODEC,
                new SettlementViewStatePayload(id, fixture.town().id().value(), fixture.town().dimension(), 42, window, true));
        var chunk = helper.getLevel().getChunkAt(helper.absolutePos(BlockPos.ZERO));
        var packet = new SettlementViewChunkPayload(id,
                new ClientboundLevelChunkWithLightPacket(chunk, helper.getLevel().getLightEngine(), null, null));
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            SettlementViewChunkPayload.STREAM_CODEC.encode(buffer, packet);
            var decoded = SettlementViewChunkPayload.STREAM_CODEC.decode(buffer);
            helper.assertValueEqual(decoded.session(), id, "the terrain packet keeps its session token");
            helper.assertValueEqual(decoded.chunk().getX(), chunk.getPos().x, "the terrain packet keeps its chunk x");
            helper.assertValueEqual(decoded.chunk().getZ(), chunk.getPos().z, "the terrain packet keeps its chunk z");
            var originalData = packet.chunk().getChunkData().getReadBuffer();
            var receivedData = decoded.chunk().getChunkData().getReadBuffer();
            try {
                helper.assertTrue(ByteBufUtil.equals(originalData, receivedData), "the existing chunk codec keeps every block and biome");
            } finally {
                originalData.release();
                receivedData.release();
            }
            helper.assertValueEqual(buffer.readableBytes(), 0, "the entire wrapped terrain packet is consumed");
        } finally {
            buffer.release();
        }
        expectInvalid(helper, () -> new SettlementViewRequestPayload(id, 0, Double.NaN, 0, 32, false));
        expectInvalid(helper, () -> new SettlementViewRequestPayload(id, -1, 0, 0, 32, false));
        expectInvalid(helper, () -> new SettlementViewWindow(window.center(), window.radius(), window.x(), window.z(), window.height(),
                List.of(window.chunks().getFirst(), window.chunks().getFirst())));
        expectInvalid(helper, () -> new SettlementViewWindow(window.center(), window.radius(), window.x(), window.z(), window.height(),
                List.of(new ChunkPos(0, 0))));
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_view")
    public static void overlappingViewersKeepIndependentChunkTickets(GameTestHelper helper) {
        var fixture = fixture(helper);
        ServerPlayer delegate = player(helper, "SecondViewer");
        fixture.town().setPermissions(fixture.player().getUUID(), delegate.getUUID(), Set.of(SettlementPermission.VIEW));
        var distance = helper.getLevel().getChunkSource().chunkMap.getDistanceManager();
        try {
            SettlementViewService.open(fixture.player(), fixture.town().id());
            SettlementViewService.open(delegate, fixture.town().id());
            var first = SettlementViewService.session(fixture.player().getUUID());
            var second = SettlementViewService.session(delegate.getUUID());
            SettlementViewService.update(first);
            SettlementViewService.update(second);
            var position = first.window.chunks().getFirst();
            helper.assertTrue(distance.shouldForceTicks(position.toLong()), "the remote terrain continues ticking without a nearby body");
            SettlementViewService.close(fixture.player().getUUID(), false);
            helper.assertTrue(distance.shouldForceTicks(position.toLong()), "closing one viewer preserves the other viewer's ticket");
            SettlementViewService.close(delegate.getUUID(), false);
            helper.assertFalse(distance.shouldForceTicks(position.toLong()), "the last viewer releases the actual forced tick ticket");
        } finally {
            SettlementViewService.close(fixture.player().getUUID(), false);
            SettlementViewService.close(delegate.getUUID(), false);
            helper.getLevel().getChunkSource().removeEntity(fixture.player());
            helper.getLevel().getChunkSource().removeEntity(delegate);
        }
        helper.succeed();
    }

    private static void expectInvalid(GameTestHelper helper, Runnable action) {
        boolean rejected = false;
        try {
            action.run();
        } catch (IllegalArgumentException exception) {
            rejected = true;
        }
        helper.assertTrue(rejected, "invalid camera data is rejected");
    }

    private static <T> void roundTrip(GameTestHelper helper, StreamCodec<RegistryFriendlyByteBuf, T> codec, T value) {
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            codec.encode(buffer, value);
            helper.assertValueEqual(codec.decode(buffer), value, "camera packets retain their bounded state");
            helper.assertValueEqual(buffer.readableBytes(), 0, "the entire camera packet is consumed");
        } finally {
            buffer.release();
        }
    }

    private static ServerPlayer player(GameTestHelper helper, String name) {
        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
        player.setPos(helper.absolutePos(BlockPos.ZERO).getCenter());
        return player;
    }

    private static Fixture fixture(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        BlockPos center = new BlockPos(2_500_000 + origin.getX() * 8, 80, 2_500_000 + origin.getZ() * 8);
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var player = player(helper, "TownViewer");
        var id = registry.create(level.getGameTime(), level.dimension().location(), center, "View town");
        var town = SettlementSavedData.get(level).establish(registry, id, player.getUUID(), center, level.getGameTime()).orElseThrow();
        return new Fixture(player, town);
    }

    private record Fixture(ServerPlayer player, SettlementRecord town) {
    }
}
