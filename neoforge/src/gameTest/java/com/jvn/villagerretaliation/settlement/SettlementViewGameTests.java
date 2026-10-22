package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.client.settlement.SettlementViewCamera;
import com.jvn.villagerretaliation.client.settlement.SettlementViewBounds;
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
import net.minecraft.core.Direction;
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

@GameTestHolder("settlement_view")
@PrefixGameTestTemplate(false)
public final class SettlementViewGameTests {
    private SettlementViewGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_view")
    public static void draftBoundsSnapTranslateAndResizeEveryFaceWithinLimits(GameTestHelper helper) {
        var limits = new SettlementViewBounds.Limits(new BlockPos(-100, 64, -200), 256, -64, 319);
        var original = new SettlementViewBounds(new BlockPos(-110, -64, -210), new BlockPos(-107, -60, -206));
        var single = SettlementViewBounds.drawing(original.min(), original.min(), limits);
        helper.assertValueEqual(single.volume(), 1L, "a one-block selection stays valid");
        helper.assertTrue(single.valid(limits), "single-block bounds include negative coordinates and minimum world height");
        for (Direction.Axis axis : Direction.Axis.values()) {
            var translated = original.translate(axis, 12, limits);
            helper.assertValueEqual(translated.volume(), original.volume(), "translation preserves volume on every axis");
            helper.assertValueEqual(translated.min().get(axis), original.min().get(axis) + 12, "translation snaps to whole blocks");
            helper.assertValueEqual(translated.translate(axis, -12, limits), original, "translation can return to its original bounds");
            for (int direction : List.of(-1, 1)) {
                var edge = original.translate(axis, direction * 1_000_000, limits);
                helper.assertTrue(edge.valid(limits), "translation respects settlement and build-height limits");
                helper.assertValueEqual(direction < 0 ? edge.min().get(axis) : edge.max().get(axis),
                        direction < 0 ? limits.min(axis) : limits.max(axis), "translation stops exactly at the boundary");
            }
        }
        for (Direction face : Direction.values()) {
            int sign = face.getAxisDirection().getStep();
            var expanded = original.resize(face, sign * 3, limits);
            if (face == Direction.DOWN) {
                helper.assertValueEqual(expanded, original, "the minimum Y face cannot leave the world");
            } else {
                helper.assertValueEqual(expanded.size(face.getAxis()), original.size(face.getAxis()) + 3,
                        "each resize face grows in its own outward direction");
            }
            var contracted = original.resize(face, -sign * 1_000_000, limits);
            helper.assertValueEqual(contracted.size(face.getAxis()), 1, "every face stops at one block instead of inverting");
            for (Direction.Axis other : Direction.Axis.values()) {
                if (other != face.getAxis()) helper.assertValueEqual(contracted.size(other), original.size(other),
                        "resizing preserves the other dimensions");
            }
        }
        for (Direction.Axis axis : Direction.Axis.values()) {
            var maximum = single.resize(Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE), 1_000_000, limits);
            helper.assertValueEqual(maximum.size(axis), axis == Direction.Axis.Y ? 64 : 128, "each dimension keeps its established maximum");
        }
        var full = new SettlementViewBounds(new BlockPos(-128, 0, -256), new BlockPos(-1, 3, -129));
        helper.assertValueEqual(full.volume(), 65_536L, "the maximum volume is inclusive");
        helper.assertValueEqual(full.resize(Direction.UP, 1, limits), full, "resize cannot exceed the volume limit");
        var drawn = SettlementViewBounds.drawing(new BlockPos(-1_000, -500, -1_000), new BlockPos(1_000, 500, 1_000), limits);
        helper.assertTrue(drawn.valid(limits), "initial drawing clamps every dimension, volume and world boundary");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_view")
    public static void cameraRotationTerrainAndBoundariesAreContinuous(GameTestHelper helper) {
        var cameraChunks = new java.util.ArrayList<ChunkPos>();
        for (int x = -10; x <= -4; x++) {
            for (int z = -16; z <= -10; z++) cameraChunks.add(new ChunkPos(x, z));
        }
        var window = new SettlementViewWindow(new BlockPos(-100, 64, -200), 48, -100, -200, 8.0F, cameraChunks);
        for (int yaw = 0; yaw < 360; yaw += 15) {
            double dx = Math.sin(Math.toRadians(yaw));
            double dz = -Math.cos(Math.toRadians(yaw));
            for (int ox : List.of(-48, 0, 48)) {
                for (int oz : List.of(-48, 0, 48)) {
                    double x = -100 + ox;
                    double z = -200 + oz;
                    double offset = Math.min(90.0D, SettlementViewCamera.boundaryOffset(window, x, z, dx, dz));
                    helper.assertTrue(Double.isFinite(offset) && offset >= 0.0D, "every camera bearing has a finite offset");
                    helper.assertTrue(Math.abs(x + dx * offset + 100) <= 48.00001D
                            && Math.abs(z + dz * offset + 200) <= 48.00001D, "all bearings respect negative settlement corners");
                }
            }
        }
        var slow = new SettlementViewCamera();
        var fast = new SettlementViewCamera();
        slow.rotate(-1, 1);
        for (int frame = 0; frame < 100; frame++) fast.rotate(-1, 0.01D);
        helper.assertTrue(Math.abs(slow.yaw() - fast.yaw()) < 0.001F, "rotation is independent of frame rate");
        helper.assertValueEqual(slow.yaw(), 315.0F, "left rotation wraps through north");
        slow.rotate(1, 1);
        helper.assertValueEqual(slow.yaw(), 45.0F, "right rotation reverses left rotation");
        helper.assertTrue(Math.abs(SettlementViewCamera.interpolate((x, z) -> x + z, -1.25D, -2.25D) + 4.5D) < 0.00001D,
                "neighboring terrain samples interpolate across negative blocks");
        var camera = new SettlementViewCamera();
        var flat = camera.position(window, -100, -200, 8, 88, (x, z) -> 64, 0);
        helper.assertTrue(Math.abs(flat.pitch() - SettlementViewCamera.PITCH) < 0.001F, "flat terrain preserves the viewing angle");
        var cliff = camera.position(window, -100, -200, 8, 88, (x, z) -> x > -96 ? 100 : 64, 0.02D);
        helper.assertTrue(cliff.position().y >= 104, "a roof or cliff immediately receives four blocks of clearance");
        var missing = camera.position(window, -100, -200, 8, 88, (x, z) -> Double.NaN, 0.02D);
        helper.assertTrue(Math.abs(missing.position().y - cliff.position().y) < 0.001D, "loading terrain retains the last elevation");
        var recovery = camera.position(window, -100, -200, 8, 88, (x, z) -> 64, 0.02D);
        helper.assertTrue(recovery.position().y < cliff.position().y && recovery.position().y > flat.position().y + 20,
                "downward clearance recovery eases after an obstruction");
        helper.assertValueEqual(SettlementViewWindow.MIN_HEIGHT, 8.0F, "the shared zoom minimum is eight blocks");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_view")
    public static void everyStageKeepsCameraAndChunksWithinItsBoundary(GameTestHelper helper) {
        var fixture = fixture(helper);
        for (SettlementStage stage : SettlementStage.values()) {
            helper.assertValueEqual(fixture.town().stage(), stage, "the fixture follows each town stage");
            for (int direction : List.of(-1, 1)) {
                var window = SettlementViewWindow.around(fixture.town(), direction * Double.MAX_VALUE,
                        -direction * Double.MAX_VALUE, Float.MAX_VALUE);
                int radius = fixture.town().territory().envelopeRadius(fixture.town().center()) + SettlementViewWindow.BORDER;
                helper.assertValueEqual(window.radius(), radius, "the camera envelope follows claims rather than stage unlocks");
                helper.assertTrue(SettlementViewWindow.nearTerritory(fixture.town(), new ChunkPos(BlockPos.containing(window.x(), 0, window.z()))),
                        "remote camera coordinates project onto owned territory or its nearby border");
                helper.assertTrue(window.chunks().stream().allMatch(chunk -> SettlementViewWindow.nearTerritory(fixture.town(), chunk)),
                        "every streamed chunk is within one chunk of owned territory");
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
            int cap = com.jvn.villagerretaliation.config.VillagerRetaliationConfig.SETTLEMENT_MAX_BACKGROUND_VILLAGES.get();
            try {
                com.jvn.villagerretaliation.config.VillagerRetaliationConfig.SETTLEMENT_MAX_BACKGROUND_VILLAGES.set(0);
                helper.assertValueEqual(SettlementSimulationService.suspension(helper.getLevel(), fixture.town()), "",
                        "an actively viewed town remains eligible when background allocation is disabled");
            } finally {
                com.jvn.villagerretaliation.config.VillagerRetaliationConfig.SETTLEMENT_MAX_BACKGROUND_VILLAGES.set(cap);
            }
            helper.assertTrue(session != null, "the server owns one view session");
            helper.assertValueEqual(delegate.position(), body, "opening does not teleport the body");
            helper.assertValueEqual(delegate.getCamera(), delegate, "the server camera remains the vulnerable player");
            helper.assertFalse(delegate.isSpectator(), "viewing does not grant spectator mode");
            fixture.town().setPermissions(fixture.player().getUUID(), delegate.getUUID(), Set.of());
            SettlementViewService.request(delegate, new SettlementViewRequestPayload(session.id, 1,
                    fixture.town().center().getX(), fixture.town().center().getZ(), 32.0F, false));
            helper.assertTrue(SettlementViewService.session(delegate.getUUID()) == null, "revoked access closes the session");
            helper.assertFalse(SettlementSimulationService.foreground(helper.getLevel(), fixture.town()),
                    "closing the last remote session relinquishes foreground eligibility");
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
            helper.assertValueEqual(session.held.size(), 2, "repeated updates cannot reset the per-tick allowance");
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
                new SettlementViewStatePayload(id, fixture.town().id().value(), fixture.town().dimension(), 42, window, true,
                        SettlementPlotService.snapshot(fixture.player(), fixture.town(), window), 2));
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

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_view")
    public static void overviewKeepsStablePagesAndRejectsStaleReplies(GameTestHelper helper) {
        var fixture = fixture(helper);
        try {
            SettlementViewService.open(fixture.player(), fixture.town().id());
            var session = SettlementViewService.session(fixture.player().getUUID());
            var category = com.jvn.villagerretaliation.network.SettlementManagementRequestPayload.Page.RESIDENTS;
            var request = new com.jvn.villagerretaliation.network.SettlementViewOverviewRequestPayload(session.id, 1, 0, category, 0);
            var page = session.overview.page(session, request);
            helper.assertTrue(page != null && page.entries().isEmpty(), "empty rosters complete authoritatively");
            roundTrip(helper, com.jvn.villagerretaliation.network.SettlementViewOverviewRequestPayload.STREAM_CODEC, request);
            roundTrip(helper, com.jvn.villagerretaliation.network.SettlementViewOverviewPayload.STREAM_CODEC, page);
            helper.assertTrue(session.overview.page(session, request) == null, "replayed requests are rejected");
            helper.assertTrue(session.overview.page(session,
                    new com.jvn.villagerretaliation.network.SettlementViewOverviewRequestPayload(session.id, 2,
                            page.generation() + 1, category, 0)) == null, "unknown generations are rejected");
            helper.assertTrue(session.held.isEmpty(), "overview generation does not request terrain");
            var client = new com.jvn.villagerretaliation.client.settlement.SettlementViewSnapshots(session.id, fixture.town().id().value());
            var next = client.request(10);
            helper.assertFalse(client.accept(new com.jvn.villagerretaliation.network.SettlementViewOverviewPayload(
                    UUID.randomUUID(), next.requestId(), page.generation(), page.header(), category, 0, 0, List.of()), 11),
                    "foreign session replies are ignored");
            int time = 1_001;
            for (var tab : com.jvn.villagerretaliation.network.SettlementViewOverviewPayload.CATEGORIES) {
                if (tab != category) next = client.request(time - 1);
                boolean complete = client.accept(new com.jvn.villagerretaliation.network.SettlementViewOverviewPayload(
                        session.id, next.requestId(), page.generation(), page.header(), tab, 0, 0, List.of()), time);
                helper.assertValueEqual(complete, tab == com.jvn.villagerretaliation.network.SettlementViewOverviewPayload.CATEGORIES.getLast(),
                        "only a complete refresh replaces the visible snapshot");
                time += 1_000;
            }
            helper.assertTrue(client.request(time + 3_999) == null, "refresh waits five seconds after completion");
            helper.assertTrue(client.request(time + 4_000) != null, "refresh restarts after the completion interval");
            expectInvalid(helper, () -> new com.jvn.villagerretaliation.network.SettlementViewOverviewRequestPayload(
                    session.id, 3, 0, category, 1));
            SettlementViewService.close(fixture.player().getUUID(), false);
            helper.assertTrue(SettlementViewService.session(fixture.player().getUUID()) == null, "closing releases snapshot ownership");
        } finally {
            SettlementViewService.close(fixture.player().getUUID(), false);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_view")
    public static void controlGroupsRetainBoundedSelectionUntilAuthoritativePruning(GameTestHelper helper) {
        var selection = new com.jvn.villagerretaliation.client.settlement.SettlementViewSelection();
        var members = java.util.stream.IntStream.range(0, 129).mapToObj(i -> UUID.randomUUID()).toList();
        helper.assertFalse(selection.select(members, false), "oversized box selections report truncation");
        helper.assertValueEqual(selection.residents().size(), 128, "selection stays bounded");
        selection.store(0);
        selection.select(List.of(), false);
        helper.assertFalse(selection.recall(0, 1_000), "first recall does not focus");
        helper.assertTrue(selection.recall(0, 1_200), "double recall focuses");
        helper.assertTrue(selection.toggle(members.getFirst()), "shift click removes a member");
        helper.assertValueEqual(selection.residents().size(), 127, "toggle affects only the clicked member");
        selection.prune(Set.of(members.getFirst()));
        selection.recall(0, 2_000);
        helper.assertValueEqual(selection.residents(), List.of(members.getFirst()), "complete rosters prune remembered groups");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_view")
    public static void overviewPaginationAndBulkCommandsStayBoundedAndRevalidate(GameTestHelper helper) {
        var fixture = fixture(helper);
        var town = fixture.town();
        var level = helper.getLevel();
        var area = new SettlementArea(UUID.randomUUID(), SettlementAreaType.GUARD_POST, "Post",
                town.center(), town.center().offset(2, 2, 2), 5, true);
        helper.assertTrue(town.putArea(fixture.player().getUUID(), area), "guard post exists");
        var members = new java.util.ArrayList<UUID>();
        for (int i = 0; i < 65; i++) {
            var villager = EntityType.VILLAGER.create(level);
            villager.setPos(town.center().getCenter());
            com.jvn.villagerretaliation.allegiance.VillageAllegianceApi.assignKnown(level, villager, town.id(),
                    com.jvn.villagerretaliation.allegiance.AllegianceAssignmentSource.EXPLICIT_API);
            members.add(villager.getUUID());
            if (i < 17) town.assignDuty(villager.getUUID(), area.id());
        }
        try {
            SettlementViewService.open(fixture.player(), town.id());
            var session = SettlementViewService.session(fixture.player().getUUID());
            var category = com.jvn.villagerretaliation.network.SettlementManagementRequestPayload.Page.RESIDENTS;
            var first = session.overview.page(session, new com.jvn.villagerretaliation.network.SettlementViewOverviewRequestPayload(
                    session.id, 1, 0, category, 0));
            helper.assertValueEqual(first.entries().size(), 64, "overview pages stop at 64 entries");
            var next = session.overview.page(session, new com.jvn.villagerretaliation.network.SettlementViewOverviewRequestPayload(
                    session.id, 2, first.generation(), category, 1));
            helper.assertValueEqual(next.entries().size(), 1, "the last page contains the remaining resident");
            roundTrip(helper, com.jvn.villagerretaliation.network.SettlementViewOverviewPayload.STREAM_CODEC, first);
            expectInvalid(helper, () -> new com.jvn.villagerretaliation.network.SettlementViewOverviewPayload(session.id, 3,
                    first.generation(), first.header(), category, 0, 65,
                    java.util.stream.Stream.concat(first.entries().stream(), next.entries().stream()).toList()));
            helper.assertValueEqual(next.header(), first.header(), "all pages share one assessment");
            helper.assertTrue(session.held.isEmpty(), "roster pagination does not stream terrain");
            var order = new SettlementGuardOrder(SettlementGuardOrder.Mode.MOVE, List.of(town.center().east(4)));
            var targets = members.subList(0, 18).stream()
                    .map(id -> new com.jvn.villagerretaliation.network.SettlementViewBatchPayload.Target(id, town.duties().get(id))).toList();
            var request = new com.jvn.villagerretaliation.network.SettlementViewBatchPayload(session.id,
                    com.jvn.villagerretaliation.network.SettlementViewBatchPayload.operation(session.id, 1),
                    SettlementViewAction.MOVE, targets, null, order);
            roundTrip(helper, com.jvn.villagerretaliation.network.SettlementViewBatchPayload.STREAM_CODEC, request);
            session.commands.submit(session, request);
            helper.assertValueEqual(session.commands.tick(session, 16), 16, "one tick processes at most its budget");
            helper.assertValueEqual(town.temporaryGuardOrders().size(), 16, "remaining residents wait for the next tick");
            helper.assertValueEqual(session.commands.tick(session, 16), 2, "the next tick completes the batch");
            helper.assertValueEqual(town.temporaryGuardOrders().size(), 17, "unloaded guards accept orders while civilians are skipped");
            long revision = town.revision();
            session.commands.submit(session, request);
            helper.assertValueEqual(session.commands.tick(session, 16), 0, "duplicate operations never execute again");
            helper.assertValueEqual(town.revision(), revision, "deduplication preserves state");
            var invalid = new com.jvn.villagerretaliation.network.SettlementViewBatchPayload(session.id, UUID.randomUUID(),
                    SettlementViewAction.MOVE, targets, null, new SettlementGuardOrder(SettlementGuardOrder.Mode.MOVE,
                            List.of(town.center().east(town.radius() + 1))));
            var outcome = SettlementViewCommands.apply(session, invalid, targets.getFirst());
            helper.assertValueEqual(outcome.status(), com.jvn.villagerretaliation.network.SettlementViewBatchResultPayload.Status.FAILED,
                    "invalid routes fail individually");
            var reassignment = new com.jvn.villagerretaliation.network.SettlementViewBatchPayload(session.id, UUID.randomUUID(),
                    SettlementViewAction.ASSIGN, List.of(new com.jvn.villagerretaliation.network.SettlementViewBatchPayload.Target(
                            members.getFirst(), null)), area.id(), null);
            helper.assertValueEqual(SettlementViewCommands.apply(session, reassignment, reassignment.targets().getFirst()).reason(),
                    "Assignment changed since review", "stale reassignment reviews cannot overwrite a newer assignment");
            var delegate = player(helper, "ViewOnly");
            town.setPermissions(fixture.player().getUUID(), delegate.getUUID(), Set.of(SettlementPermission.VIEW));
            SettlementViewService.open(delegate, town.id());
            var delegateSession = SettlementViewService.session(delegate.getUUID());
            var delegateRequest = new com.jvn.villagerretaliation.network.SettlementViewBatchPayload(delegateSession.id,
                    UUID.randomUUID(), request.action(), request.targets(), request.area(), request.order());
            var denied = SettlementViewCommands.apply(delegateSession, delegateRequest, targets.getFirst());
            helper.assertValueEqual(denied.status(), com.jvn.villagerretaliation.network.SettlementViewBatchResultPayload.Status.FAILED,
                    "view access does not grant staff commands");
            SettlementViewService.close(delegate.getUUID(), false);
            roundTrip(helper, com.jvn.villagerretaliation.network.SettlementViewBatchResultPayload.STREAM_CODEC,
                    new com.jvn.villagerretaliation.network.SettlementViewBatchResultPayload(session.id, request.operation(), List.of(outcome)));
            session.commands.submit(session, new com.jvn.villagerretaliation.network.SettlementViewBatchPayload(session.id,
                    com.jvn.villagerretaliation.network.SettlementViewBatchPayload.operation(session.id, 2),
                    SettlementViewAction.RETURN, targets, null, null));
            SettlementViewService.close(fixture.player().getUUID(), false);
            helper.assertValueEqual(session.commands.tick(session, 16), 0, "session teardown discards pending commands");
            expectInvalid(helper, () -> new com.jvn.villagerretaliation.network.SettlementViewBatchPayload(session.id, UUID.randomUUID(),
                    SettlementViewAction.RETURN, java.util.Collections.nCopies(129, targets.getFirst()), null, null));
            expectInvalid(helper, () -> new com.jvn.villagerretaliation.network.SettlementViewBatchPayload(session.id, UUID.randomUUID(),
                    SettlementViewAction.RETURN, List.of(targets.getFirst(), targets.getFirst()), null, null));
        } finally {
            SettlementViewService.close(fixture.player().getUUID(), false);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "settlement_view")
    public static void concurrentViewsShareTicketsAndCannotResetTheAggregateBudget(GameTestHelper helper) {
        var fixture = fixture(helper);
        var peer = player(helper, "SharedViewer");
        var server = helper.getLevel().getServer();
        var config = com.jvn.villagerretaliation.config.VillagerRetaliationConfig.SETTLEMENT_VIEW_LOADS;
        int previous = config.get();
        int sends = com.jvn.villagerretaliation.config.VillagerRetaliationConfig.SETTLEMENT_VIEW_SENDS.get();
        try {
            config.set(2);
            com.jvn.villagerretaliation.config.VillagerRetaliationConfig.SETTLEMENT_VIEW_SENDS.set(1);
            fixture.town().setPermissions(fixture.player().getUUID(), peer.getUUID(), Set.of(SettlementPermission.VIEW));
            SettlementViewService.open(fixture.player(), fixture.town().id());
            SettlementViewService.open(peer, fixture.town().id());
            var first = SettlementViewService.session(fixture.player().getUUID());
            var second = SettlementViewService.session(peer.getUUID());
            var budget = SettlementViewService.budget(server);
            for (var chunk : first.window.chunks().subList(0, 2)) helper.getLevel().getChunk(chunk.x, chunk.z);
            budget.begin(helper.getLevel().getGameTime() + 1000);
            SettlementViewService.update(first, budget);
            SettlementViewService.update(second, budget);
            helper.assertValueEqual(first.held, second.held, "overlapping demand shares the same two resources");
            helper.assertValueEqual(SettlementViewService.metrics(server).loads(), 2, "shared demand adds no duplicate tickets");
            helper.assertValueEqual(SettlementViewService.metrics(server).sends(), 1, "ready overlapping chunks still obey the shared packet budget");
            var positions = Set.copyOf(first.held);
            SettlementViewService.close(fixture.player().getUUID(), false);
            helper.assertTrue(positions.stream().allMatch(pos -> helper.getLevel().getChunkSource().chunkMap.getDistanceManager()
                    .shouldForceTicks(pos.toLong())), "the remaining viewer retains the shared ticket");
            SettlementViewService.open(fixture.player(), fixture.town().id());
            var reopened = SettlementViewService.session(fixture.player().getUUID());
            SettlementViewService.update(reopened, budget);
            helper.assertValueEqual(SettlementViewService.metrics(server).loads(), 2, "reopening cannot replenish global admissions");
            SettlementViewService.close(peer.getUUID(), false);
            SettlementViewService.close(fixture.player().getUUID(), false);
            helper.assertTrue(positions.stream().noneMatch(pos -> helper.getLevel().getChunkSource().chunkMap.getDistanceManager()
                    .shouldForceTicks(pos.toLong())), "the last reference releases the actual ticket");
            SettlementViewService.close(fixture.player().getUUID(), false);
        } finally {
            config.set(previous);
            com.jvn.villagerretaliation.config.VillagerRetaliationConfig.SETTLEMENT_VIEW_SENDS.set(sends);
            SettlementViewService.close(peer.getUUID(), false);
            SettlementViewService.close(fixture.player().getUUID(), false);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "settlement_view")
    public static void saturatedViewsRotateResourcesAndReleaseEveryLease(GameTestHelper helper) {
        var fixture = fixture(helper);
        var peer = player(helper, "QueuedViewer");
        var server = helper.getLevel().getServer();
        var config = com.jvn.villagerretaliation.config.VillagerRetaliationConfig.SETTLEMENT_VIEW_CHUNKS;
        int previous = config.get();
        try {
            config.set(1);
            var registry = VillageAllegianceRegistrySavedData.get(helper.getLevel());
            var center = fixture.town().center().east(512);
            var id = registry.create(helper.getLevel().getGameTime(), helper.getLevel().dimension().location(), center, "Second view");
            var other = SettlementSavedData.get(helper.getLevel()).establish(registry, id, peer.getUUID(), center, helper.getLevel().getGameTime()).orElseThrow();
            SettlementViewService.open(fixture.player(), fixture.town().id());
            SettlementViewService.open(peer, other.id());
            var first = SettlementViewService.session(fixture.player().getUUID());
            var second = SettlementViewService.session(peer.getUUID());
            var budget = SettlementViewService.budget(server);
            var visited = new HashSet<ChunkPos>();
            int firstTurns = 0;
            int secondTurns = 0;
            for (int tick = 0; tick < 500; tick++) {
                budget.begin(helper.getLevel().getGameTime() + 2000 + tick);
                SettlementViewService.stream(List.of(first, second), budget);
                visited.addAll(first.held);
                visited.addAll(second.held);
                if (!first.held.isEmpty()) firstTurns++;
                if (!second.held.isEmpty()) secondTurns++;
                helper.assertTrue(SettlementViewService.metrics(server).chunks() <= 1, "all sessions share one aggregate ticket");
                helper.assertTrue(SettlementViewService.metrics(server).sends() <= 4, "transmission remains globally bounded");
            }
            helper.assertTrue(firstTurns > 100 && secondTurns > 100, "disjoint viewers both receive service under a one-ticket cap");
            helper.assertTrue(visited.size() >= 10, "pressure rotates through camera demand instead of repeatedly admitting the closest chunk");
            SettlementViewService.close(fixture.player().getUUID(), false);
            SettlementViewService.close(peer.getUUID(), false);
            helper.assertValueEqual(SettlementViewService.metrics(server).chunks(), 0, "all leases are released");
        } finally {
            config.set(previous);
            SettlementViewService.close(peer.getUUID(), false);
            SettlementViewService.close(fixture.player().getUUID(), false);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "settlement_view")
    public static void irregularClaimsDoNotExposeTheirEnvelopeOrAnotherTown(GameTestHelper helper) {
        var fixture = fixture(helper);
        var center = new ChunkPos(fixture.town().center());
        var plots = new java.util.LinkedHashSet<Long>();
        int length = 2 * (SettlementExpeditions.RANGE / 16) + 4;
        for (int x = 0; x <= length; x++) plots.add(ChunkPos.asLong(center.x + x, center.z));
        for (int z = 0; z <= length; z++) plots.add(ChunkPos.asLong(center.x + length, center.z + z));
        var tag = fixture.town().save(helper.getLevel().registryAccess());
        var territory = tag.getCompound("Territory");
        territory.putLongArray("Plots", plots.stream().mapToLong(Long::longValue).toArray());
        territory.putInt("GrandfatheredCapacity", plots.size());
        var town = SettlementRecord.load(tag, helper.getLevel().registryAccess(), () -> {});
        var gap = new ChunkPos(center.x + length / 2, center.z + length / 2);
        var window = SettlementViewWindow.around(town, gap.getMiddleBlockX(), gap.getMiddleBlockZ(), 64);
        helper.assertFalse(window.chunks().contains(gap), "the gap beyond expedition range stays outside the stream");
        var clamped = new ChunkPos(BlockPos.containing(window.x(), 0, window.z()));
        helper.assertValueEqual(plots.stream().map(ChunkPos::new)
                .mapToInt(plot -> Math.max(Math.abs(plot.x - clamped.x), Math.abs(plot.z - clamped.z))).min().orElseThrow(),
                SettlementExpeditions.RANGE / 16, "camera clamps at the expedition edge without jumping back to town");
        helper.assertTrue(window.chunks().stream().allMatch(chunk -> SettlementViewWindow.nearTerritory(town, chunk)),
                "every camera chunk belongs to the claim or the bounded border");
        var focus = window.focus(gap.getMiddleBlockX(), gap.getMiddleBlockZ());
        helper.assertTrue(window.contains(BlockPos.containing(focus)), "the client preview projects focus into the server window");
        double offset = SettlementViewCamera.boundaryOffset(window, window.x(), window.z(), 1, 0);
        helper.assertTrue(window.contains(BlockPos.containing(window.x() + offset, 0, window.z())),
                "the camera orbit cannot cross a missing part of the territory window");
        var distant = SettlementViewWindow.around(town, (center.x + length) * 16.0D, (center.z + length) * 16.0D, 32);
        helper.assertTrue(distant.chunks().contains(new ChunkPos(center.x + length, center.z + length)),
                "a distant legitimate claim remains reachable");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "settlement_view")
    public static void bordersAndForeignClaimsRestrictServerWindows(GameTestHelper helper) {
        var fixture = fixture(helper);
        var level = helper.getLevel();
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var neighborCenter = fixture.town().center().east(48);
        var id = registry.create(level.getGameTime(), level.dimension().location(), neighborCenter, "Neighbor");
        var neighbor = SettlementSavedData.get(level).establish(registry, id, UUID.randomUUID(), neighborCenter, level.getGameTime()).orElseThrow();
        var window = SettlementViewWindow.around(level, fixture.town(), neighborCenter.getX(), neighborCenter.getZ(), 64);
        helper.assertTrue(window.chunks().stream().noneMatch(chunk -> neighbor.territory().contains(chunk.x, chunk.z)),
                "another town's claims are excluded even where they meet the nearby border");
        var border = level.getWorldBorder();
        double x = border.getCenterX(), z = border.getCenterZ(), size = border.getSize();
        try {
            border.setCenter(fixture.town().center().getX(), fixture.town().center().getZ());
            border.setSize(20);
            var clipped = SettlementViewWindow.around(level, fixture.town(), Double.MAX_VALUE, -Double.MAX_VALUE, 64);
            helper.assertTrue(clipped.chunks().stream().allMatch(chunk -> SettlementViewWindow.intersects(border, chunk)), "no requested chunk lies wholly outside the world border");
            helper.assertTrue(clipped.x() < border.getMaxX() && clipped.x() >= border.getMinX()
                    && clipped.z() < border.getMaxZ() && clipped.z() >= border.getMinZ(), "camera focus stays inside the world border");
        } finally {
            border.setCenter(x, z);
            border.setSize(size);
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
        player.setLastSectionPos(net.minecraft.core.SectionPos.of(player));
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
