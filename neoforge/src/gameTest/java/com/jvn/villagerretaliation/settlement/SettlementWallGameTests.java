package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.client.settlement.SettlementWallDraft;
import com.jvn.villagerretaliation.client.settlement.SettlementWallEditor;
import com.jvn.villagerretaliation.network.SettlementWallRequestPayload;
import com.jvn.villagerretaliation.network.SettlementWallResultPayload;
import io.netty.buffer.Unpooled;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("settlement_wall")
@PrefixGameTestTemplate(false)
public final class SettlementWallGameTests {
    private SettlementWallGameTests() {}

    @GameTest(template = "empty", batch = "settlement_wall")
    public static void extractedTemplatesRetainAuthoredDimensionsAndDetails(GameTestHelper helper) {
        String[] names = {"stone_tower_low", "stone_tower_tall", "stone_span", "timber_span"};
        int[][] sizes = {{5, 8, 5}, {5, 11, 5}, {14, 7, 5}, {14, 9, 8}};
        long[] solid = {176, 235, 336, 612};
        for (int i = 0; i < names.length; i++) {
            var piece = SettlementWallTemplates.get(names[i]);
            helper.assertValueEqual(piece.size(), new BlockPos(sizes[i][0], sizes[i][1], sizes[i][2]), "authored dimensions");
            helper.assertValueEqual(piece.cells().stream().filter(cell -> !cell.state().isAir()).count(), solid[i], "no source blocks are lost");
        }
        helper.assertTrue(SettlementWallTemplates.get("timber_span").cells().stream().anyMatch(cell -> cell.state().is(Blocks.STRIPPED_SPRUCE_LOG)),
                "timber beams remain in the extracted design");
        helper.assertTrue(SettlementWallTemplates.get("stone_span").cells().stream().anyMatch(cell -> cell.state().is(Blocks.STONE_STAIRS)),
                "decorative stair states remain in the design");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "settlement_wall")
    public static void spansConnectTowersAtEveryHeadingAndAroundLoops(GameTestHelper helper) {
        for (var type : SettlementWall.Type.values()) {
            for (var end : List.of(new BlockPos(30, 0, 0), new BlockPos(-30, 0, 0), new BlockPos(0, 0, 30),
                    new BlockPos(0, 0, -30), new BlockPos(30, 0, 17), new BlockPos(-17, 0, 30))) {
                var wall = new SettlementWall(type, List.of(BlockPos.ZERO, end), false, 10);
                var plan = SettlementWallGeometry.build(wall);
                helper.assertTrue(plan.error().isEmpty(), "valid heading builds: " + plan.error());
                helper.assertValueEqual(plan, SettlementWallGeometry.build(wall), "geometry is deterministic");
                var solid = new HashSet<BlockPos>();
                plan.cells().stream().filter(cell -> !cell.state().isAir()).forEach(cell -> solid.add(cell.position()));
                int length = Math.max(Math.abs(end.getX()), Math.abs(end.getZ()));
                for (int step = 0; step <= length; step++) {
                    var center = new BlockPos(Math.round(end.getX() * (float) step / length), 0, Math.round(end.getZ() * (float) step / length));
                    helper.assertTrue(solid.contains(center), "wall center joins every tower without a gap at " + center);
                }
                helper.assertValueEqual(plan.cells().size(), new HashSet<>(plan.cells().stream().map(SettlementWallTemplates.Cell::position).toList()).size(),
                        "joins place each block only once");
            }
            var loop = new SettlementWall(type, List.of(BlockPos.ZERO, new BlockPos(24, 0, 0),
                    new BlockPos(24, 0, 24), new BlockPos(0, 0, 24)), true, 12);
            var plan = SettlementWallGeometry.build(loop);
            helper.assertTrue(plan.error().isEmpty(), "loop is valid");
            helper.assertTrue(plan.cells().stream().anyMatch(cell -> cell.position().equals(new BlockPos(0, 0, 12)) && !cell.state().isAir()),
                    "closing span is present");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "settlement_wall")
    public static void automaticTowersAreEvenAndKeepUserNodes(GameTestHelper helper) {
        var start = new BlockPos(2, 70, 3);
        var end = start.east(49);
        var wall = new SettlementWall(SettlementWall.Type.LOW_STONE, List.of(start, end), false, 12);
        var towers = SettlementWallGeometry.towers(wall);
        helper.assertValueEqual(towers.size(), 5, "spacing distributes intermediate towers");
        helper.assertValueEqual(towers.getFirst(), start, "start node remains fixed");
        helper.assertValueEqual(towers.getLast(), end, "end node remains fixed");
        int min = Integer.MAX_VALUE, max = 0;
        for (int i = 1; i < towers.size(); i++) {
            int gap = towers.get(i).getX() - towers.get(i - 1).getX();
            min = Math.min(min, gap); max = Math.max(max, gap);
        }
        helper.assertTrue(max - min <= 1, "spacing is even to block precision");
        helper.assertValueEqual(SettlementWallGeometry.towers(new SettlementWall(wall.type(), wall.nodes(), false, 0)), wall.nodes(),
                "disabled distribution only uses manual nodes");
        helper.assertFalse(SettlementWallGeometry.build(new SettlementWall(wall.type(), List.of(start, start.east(129)), false, 12)).error().isEmpty(),
                "spacing cannot bypass span limits");
        helper.assertFalse(SettlementWallGeometry.build(new SettlementWall(wall.type(), List.of(start, start.east(4)), false, 0)).error().isEmpty(),
                "overlapping towers are rejected");
        var crowded = new java.util.ArrayList<BlockPos>();
        for (int i = 0; i < 32; i++) crowded.add(start.offset((i % 2) * 126, 0, i * 7));
        helper.assertFalse(SettlementWallGeometry.build(new SettlementWall(wall.type(), crowded, false, 6)).error().isEmpty(),
                "automatic tower count stays bounded");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "settlement_wall")
    public static void placementPreservesObstaclesAndRequiresSupport(GameTestHelper helper) {
        var base = floor(helper, 20);
        var wall = new SettlementWall(SettlementWall.Type.TIMBER, List.of(base, base.east(18)), false, 0);
        var level = helper.getLevel();
        var good = SettlementWallTerrain.preview(level, wall, pos -> true);
        helper.assertTrue(good.error().isEmpty(), "clear supported ground accepts walls: " + good.error());
        var obstacle = base.east(8);
        level.setBlock(obstacle, Blocks.CHEST.defaultBlockState(), 2);
        var blocked = SettlementWallTerrain.preview(level, wall, pos -> true);
        helper.assertFalse(blocked.error().isEmpty(), "container blocks placement");
        helper.assertValueEqual(SettlementWallTerrain.apply(level, blocked), 0, "blocked drafts cannot partially apply");
        helper.assertTrue(level.getBlockState(obstacle).is(Blocks.CHEST), "container survives");
        level.setBlock(obstacle, Blocks.WATER.defaultBlockState(), 2);
        helper.assertFalse(SettlementWallTerrain.preview(level, wall, pos -> true).error().isEmpty(), "water blocks placement");
        level.setBlock(obstacle, Blocks.AIR.defaultBlockState(), 2);
        level.setBlock(obstacle.below(), Blocks.AIR.defaultBlockState(), 2);
        helper.assertFalse(SettlementWallTerrain.preview(level, wall, pos -> true).error().isEmpty(), "floating spans are rejected");
        level.setBlock(obstacle.below(), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
        level.setBlock(obstacle, Blocks.SHORT_GRASS.defaultBlockState(), 2);
        var pig = net.minecraft.world.entity.EntityType.PIG.create(level);
        pig.setPos(obstacle.getBottomCenter());
        pig.setNoAi(true);
        level.addFreshEntity(pig);
        helper.assertFalse(SettlementWallTerrain.preview(level, wall, pos -> true).error().isEmpty(), "entities cannot be buried by instant walls");
        pig.discard();
        var ready = SettlementWallTerrain.preview(level, wall, pos -> true);
        helper.assertTrue(ready.error().isEmpty(), "replaceable vegetation is allowed");
        helper.assertTrue(SettlementWallTerrain.apply(level, ready) > 0, "instant placement creates blocks without construction resources");
        for (var cell : ready.cells()) helper.assertValueEqual(level.getBlockState(cell.position()), cell.target(), "applied blocks match the preview");
        helper.assertTrue(SettlementWallTerrain.preview(level, wall, pos -> true).cells().stream().allMatch(cell -> cell.kind() == SettlementWallTerrain.Kind.EXISTING),
                "existing matching walls remain unchanged");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "settlement_wall")
    public static void boundariesAndUnavailableChunksKeepInvalidPreviews(GameTestHelper helper) {
        var base = floor(helper, 40);
        var wall = new SettlementWall(SettlementWall.Type.LOW_STONE, List.of(base, base.east(18)), false, 0);
        var outside = SettlementWallTerrain.preview(helper.getLevel(), wall, pos -> pos.getX() <= base.getX() + 10);
        helper.assertFalse(outside.error().isEmpty(), "outside walls are rejected");
        helper.assertTrue(outside.cells().stream().anyMatch(cell -> cell.kind() == SettlementWallTerrain.Kind.OUTSIDE), "outside cells remain visible");
        var remote = new BlockPos(20_000_000, 70, 20_000_000);
        var unloaded = SettlementWallTerrain.preview(helper.getLevel(), new SettlementWall(wall.type(), List.of(remote, remote.east(18)), false, 0), pos -> true);
        helper.assertTrue(unloaded.cells().stream().allMatch(cell -> cell.kind() == SettlementWallTerrain.Kind.UNAVAILABLE), "unloaded cells are explicitly marked");
        helper.assertFalse(helper.getLevel().hasChunkAt(remote), "preview does not generate chunks");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "settlement_wall")
    public static void confirmationsValidateAuthorityTerrainRevisionAndDuplicates(GameTestHelper helper) {
        var base = floor(helper, 60);
        var wall = new SettlementWall(SettlementWall.Type.TALL_STONE, List.of(base, base.east(18)), false, 9);
        var session = new SettlementWallSession();
        var id = UUID.randomUUID();
        var request = new SettlementWallRequestPayload(id, 1, SettlementWallRequestPayload.Action.PREVIEW, new UUID(0, 0), wall);
        var level = helper.getLevel();
        var preview = session.request(level, id, true, pos -> true, request);
        helper.assertValueEqual(preview.status(), SettlementWallResultPayload.Status.PREVIEW, "server accepts the reviewed draft");
        var confirm = new SettlementWallRequestPayload(id, 1, SettlementWallRequestPayload.Action.CONFIRM, preview.token(), null);
        helper.assertValueEqual(session.request(level, id, false, pos -> true, confirm).status(), SettlementWallResultPayload.Status.REJECTED, "build permission is required");
        helper.assertValueEqual(session.request(level, UUID.randomUUID(), true, pos -> true, confirm).status(), SettlementWallResultPayload.Status.REJECTED, "wrong session is rejected");
        level.setBlock(base.below(), Blocks.STONE.defaultBlockState(), 2);
        var refreshed = session.request(level, id, true, pos -> true, confirm);
        helper.assertValueEqual(refreshed.status(), SettlementWallResultPayload.Status.PREVIEW, "changed foundation requires review again");
        helper.assertFalse(preview.token().equals(refreshed.token()), "stale token is replaced");
        helper.assertTrue(level.getBlockState(base).isAir(), "stale confirmation applies nothing");
        confirm = new SettlementWallRequestPayload(id, 1, SettlementWallRequestPayload.Action.CONFIRM, refreshed.token(), null);
        var applied = session.request(level, id, true, pos -> true, confirm);
        helper.assertValueEqual(applied.status(), SettlementWallResultPayload.Status.APPLIED, "current token applies");
        level.setBlock(base, Blocks.AIR.defaultBlockState(), 2);
        helper.assertValueEqual(session.request(level, id, true, pos -> true, confirm), applied, "duplicate returns the receipt");
        helper.assertTrue(level.getBlockState(base).isAir(), "duplicate cannot rebuild later edits");
        session.request(level, id, true, pos -> true, new SettlementWallRequestPayload(id, 2, SettlementWallRequestPayload.Action.CANCEL, new UUID(0, 0), null));
        helper.assertValueEqual(session.request(level, id, true, pos -> true, confirm).status(), SettlementWallResultPayload.Status.REJECTED, "cancel invalidates older drafts");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "settlement_wall")
    public static void draftEditingAndDistributionSettingsStayUndoable(GameTestHelper helper) {
        var draft = new SettlementWallDraft(SettlementWall.Type.LOW_STONE, false, 16);
        draft.place(BlockPos.ZERO); draft.place(new BlockPos(24, 0, 0));
        draft.toggleAutomatic(); draft.spacing(8);
        helper.assertValueEqual(SettlementWallGeometry.towers(draft.wall()).size(), 4, "spacing changes update generated towers");
        draft.undo();
        helper.assertValueEqual(draft.spacing(), 16, "undo restores spacing");
        draft.redo();
        draft.begin(1); draft.drag(new BlockPos(30, 0, 0)); draft.finish();
        draft.undo();
        helper.assertValueEqual(draft.wall().nodes().getLast(), new BlockPos(24, 0, 0), "undo restores moved endpoint");
        draft.stop();
        helper.assertFalse(draft.place(new BlockPos(24, 0, 24)), "Escape-style stop keeps draft without extending");
        draft.extend(); draft.place(new BlockPos(24, 0, 24)); draft.loop();
        helper.assertTrue(draft.wall().closed(), "wall drafts support loops");
        draft.remove();
        helper.assertFalse(draft.wall().closed(), "removing down to two nodes opens the loop");
        var editor = new SettlementWallEditor();
        editor.begin(); editor.draft().type(SettlementWall.Type.TIMBER); editor.draft().toggleAutomatic(); editor.draft().spacing(9);
        editor.cancel(UUID.randomUUID(), request -> {});
        editor.begin();
        helper.assertTrue(editor.draft().automatic() && editor.draft().spacing() == 9 && editor.draft().wall().type() == SettlementWall.Type.TIMBER,
                "type and tower distribution persist between drafts");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "settlement_wall")
    public static void payloadsRoundTripWithBoundedDrafts(GameTestHelper helper) {
        var wall = new SettlementWall(SettlementWall.Type.TIMBER, List.of(BlockPos.ZERO, new BlockPos(24, 0, 0)), false, 12);
        var request = new SettlementWallRequestPayload(UUID.randomUUID(), 2, SettlementWallRequestPayload.Action.PREVIEW, UUID.randomUUID(), wall);
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            SettlementWallRequestPayload.STREAM_CODEC.encode(buffer, request);
            helper.assertValueEqual(SettlementWallRequestPayload.STREAM_CODEC.decode(buffer), request, "wall settings and nodes round trip");
            var result = new SettlementWallResultPayload(request.session(), 2, request.token(), SettlementWallResultPayload.Status.PREVIEW,
                    List.of(new SettlementWallResultPayload.Cell(BlockPos.ZERO, Blocks.STONE_STAIRS.defaultBlockState(), SettlementWallTerrain.Kind.PLACE)), "");
            SettlementWallResultPayload.STREAM_CODEC.encode(buffer, result);
            helper.assertValueEqual(SettlementWallResultPayload.STREAM_CODEC.decode(buffer), result, "authoritative block states round trip");
        } finally { buffer.release(); }
        boolean invalid = false;
        try { new SettlementWall(wall.type(), wall.nodes(), false, 5); } catch (IllegalArgumentException expected) { invalid = true; }
        helper.assertTrue(invalid, "tower spacing is bounded");
        invalid = false;
        try { new SettlementWall(wall.type(), java.util.Collections.nCopies(33, BlockPos.ZERO), false, 0); }
        catch (IllegalArgumentException expected) { invalid = true; }
        helper.assertTrue(invalid, "node count is bounded");
        helper.succeed();
    }

    private static BlockPos floor(GameTestHelper helper, int height) {
        var base = helper.absolutePos(new BlockPos(5, height, 5));
        for (var pos : BlockPos.betweenClosed(base.offset(-4, -1, -5), base.offset(23, 12, 5)))
            helper.getLevel().setBlock(pos, pos.getY() == base.getY() - 1 ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
        return base;
    }
}
