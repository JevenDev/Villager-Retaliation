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
                    helper.assertTrue(solid.contains(center) || wall.towerRadius(BlockPos.ZERO) == 0
                                    && solid.stream().anyMatch(pos -> pos.getY() == 0 && pos.distManhattan(center) <= 1),
                            "wall stays within one block of the route through rounded intermediate nodes at " + center);
                }
                var footprint = new HashSet<BlockPos>();
                solid.stream().filter(pos -> pos.getY() == 0).forEach(footprint::add);
                var pending = new java.util.ArrayDeque<BlockPos>();
                pending.add(BlockPos.ZERO);
                footprint.remove(BlockPos.ZERO);
                while (!pending.isEmpty()) {
                    var current = pending.removeFirst();
                    for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                        var neighbor = current.offset(dx, 0, dz);
                        if (footprint.remove(neighbor)) pending.add(neighbor);
                    }
                }
                helper.assertTrue(footprint.isEmpty(), "all posts and span columns form one connected barrier");
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
        level.setBlock(obstacle.below(2), Blocks.STONE.defaultBlockState(), 2);
        var extended = SettlementWallTerrain.preview(level, wall, pos -> true);
        helper.assertTrue(extended.error().isEmpty(), "floating spans extend to support: " + extended.error());
        helper.assertTrue(extended.cells().stream().anyMatch(cell -> cell.position().equals(obstacle.below())
                && cell.kind() == SettlementWallTerrain.Kind.PLACE), "the missing foundation block is previewed");
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
        helper.assertTrue(SettlementWallTerrain.apply(level, ready) > 0, "terrain application matches the accepted geometry");
        for (var cell : ready.cells()) helper.assertValueEqual(level.getBlockState(cell.position()), cell.target(), "applied blocks match the preview");
        helper.assertTrue(SettlementWallTerrain.preview(level, wall, pos -> true).cells().stream().allMatch(cell -> cell.kind() == SettlementWallTerrain.Kind.EXISTING || cell.kind() == SettlementWallTerrain.Kind.SKIPPED),
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
        var wall = new SettlementWall(SettlementWall.Type.TIMBER, List.of(BlockPos.ZERO, new BlockPos(24, 0, 0)), false, 12,
                List.of(SettlementWall.Tower.NONE, SettlementWall.Tower.LOW_STONE));
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

    @GameTest(template = "empty", batch = "settlement_wall")
    public static void overhangingWallsAndTowersExtendTheirBaseMaterialsToUnevenGround(GameTestHelper helper) {
        var level = helper.getLevel();
        int height = 30;
        for (var type : SettlementWall.Type.values()) {
            var base = floor(helper, height);
            height += 30;
            var wall = new SettlementWall(type, List.of(base, base.offset(18, 2, 3)), false, 9);
            var plan = SettlementWallGeometry.build(wall);
            for (var cell : plan.cells()) level.setBlock(cell.position(), Blocks.AIR.defaultBlockState(), 2);
            var expected = new java.util.HashMap<BlockPos, net.minecraft.world.level.block.state.BlockState>();
            for (var cell : plan.cells()) {
                if (!plan.foundations().contains(cell.position())) continue;
                int depth = 1 + Math.floorMod(cell.position().getX() + cell.position().getZ(), 4);
                for (int down = 1; down <= depth; down++) {
                    var pos = cell.position().below(down);
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                    expected.put(pos, cell.state());
                }
                level.setBlock(cell.position().below(depth + 1), Blocks.STONE.defaultBlockState(), 2);
            }
            var preview = SettlementWallTerrain.preview(level, wall, pos -> true);
            helper.assertTrue(preview.error().isEmpty(), "uneven ground supports " + type + ": " + preview.error());
            helper.assertValueEqual(preview, SettlementWallTerrain.preview(level, wall, pos -> true),
                    "foundation previews are deterministic");
            var targets = new java.util.HashMap<BlockPos, net.minecraft.world.level.block.state.BlockState>();
            preview.cells().forEach(cell -> targets.put(cell.position(), cell.target()));
            expected.forEach((pos, state) -> helper.assertValueEqual(targets.get(pos), state,
                    "each extension repeats its authored base material"));
            helper.assertTrue(SettlementWallTerrain.apply(level, preview) > 0, "extended walls place instantly");
            for (var cell : preview.cells()) {
                helper.assertValueEqual(level.getBlockState(cell.position()), cell.target(), "placement matches expanded preview");
            }
            expected.keySet().forEach(pos -> helper.assertFalse(level.getBlockState(pos).isAir(), "no air remains beneath the wall"));
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "settlement_wall")
    public static void extendedFoundationsRespectObstaclesEntitiesAndVerticalBounds(GameTestHelper helper) {
        var base = floor(helper, 130);
        var wall = new SettlementWall(SettlementWall.Type.LOW_STONE, List.of(base, base.east(18)), false, 0);
        var level = helper.getLevel();
        var gap = base.east(8).below();
        level.setBlock(gap, Blocks.AIR.defaultBlockState(), 2);
        level.setBlock(gap.below(), Blocks.AIR.defaultBlockState(), 2);
        level.setBlock(gap.below(2), Blocks.STONE.defaultBlockState(), 2);
        for (var obstruction : List.of(Blocks.WATER.defaultBlockState(), Blocks.STONE_SLAB.defaultBlockState())) {
            level.setBlock(gap, obstruction, 2);
            var blocked = SettlementWallTerrain.preview(level, wall, pos -> true);
            if (obstruction.is(Blocks.WATER)) {
                helper.assertFalse(blocked.error().isEmpty(), "foundation cannot replace fluid");
                helper.assertValueEqual(SettlementWallTerrain.apply(level, blocked), 0, "blocked foundations do not partially apply");
            } else {
                helper.assertTrue(blocked.error().isEmpty(), "foundation can rest above a slab");
                helper.assertFalse(blocked.cells().stream().anyMatch(cell -> cell.position().equals(gap)), "slab is not excavated for a foundation");
            }
            helper.assertValueEqual(level.getBlockState(gap), obstruction, "obstruction remains intact");
        }
        level.setBlock(gap, Blocks.AIR.defaultBlockState(), 2);
        var outside = SettlementWallTerrain.preview(level, wall, pos -> pos.getY() >= base.getY() - 1);
        helper.assertFalse(outside.error().isEmpty(), "extensions cannot escape allowed vertical bounds");
        helper.assertValueEqual(SettlementWallTerrain.apply(level, outside), 0, "outside foundations apply nothing");
        var pig = net.minecraft.world.entity.EntityType.PIG.create(level);
        pig.setPos(gap.below().getBottomCenter());
        pig.setNoAi(true);
        level.addFreshEntity(pig);
        var occupied = SettlementWallTerrain.preview(level, wall, pos -> true);
        helper.assertFalse(occupied.error().isEmpty(), "entities below the authored wall are included in collision checks");
        helper.assertTrue(occupied.cells().stream().anyMatch(cell -> cell.position().equals(gap.below())
                && cell.kind() == SettlementWallTerrain.Kind.BLOCKED), "blocked foundation remains visible");
        pig.discard();
        helper.assertTrue(SettlementWallTerrain.preview(level, wall, pos -> true).error().isEmpty(), "cleared foundations are valid");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "settlement_wall")
    public static void deeperGroundRequiresReviewBeforeFoundationPlacement(GameTestHelper helper) {
        var base = floor(helper, 160);
        var level = helper.getLevel();
        var wall = new SettlementWall(SettlementWall.Type.LOW_STONE, List.of(base, base.east(18)), false, 0);
        var gap = base.east(8).below();
        level.setBlock(gap, Blocks.AIR.defaultBlockState(), 2);
        level.setBlock(gap.below(), Blocks.STONE.defaultBlockState(), 2);
        var session = new SettlementWallSession();
        var id = UUID.randomUUID();
        var request = new SettlementWallRequestPayload(id, 1, SettlementWallRequestPayload.Action.PREVIEW, new UUID(0, 0), wall);
        var preview = session.request(level, id, true, pos -> true, request);
        helper.assertValueEqual(preview.status(), SettlementWallResultPayload.Status.PREVIEW, "initial overhang is valid");
        level.setBlock(gap.below(), Blocks.AIR.defaultBlockState(), 2);
        level.setBlock(gap.below(2), Blocks.STONE.defaultBlockState(), 2);
        var confirm = new SettlementWallRequestPayload(id, 1, SettlementWallRequestPayload.Action.CONFIRM, preview.token(), null);
        var refreshed = session.request(level, id, true, pos -> true, confirm);
        helper.assertValueEqual(refreshed.status(), SettlementWallResultPayload.Status.PREVIEW, "deeper support refreshes preview");
        helper.assertFalse(refreshed.token().equals(preview.token()), "changed depth invalidates confirmation");
        helper.assertValueEqual(refreshed.cells().size(), preview.cells().size() + 1, "the deeper foundation appears in the new preview");
        helper.assertTrue(level.getBlockState(gap).isAir(), "stale confirmation places nothing");
        confirm = new SettlementWallRequestPayload(id, 1, SettlementWallRequestPayload.Action.CONFIRM, refreshed.token(), null);
        helper.assertValueEqual(session.request(level, id, true, pos -> true, confirm).status(),
                SettlementWallResultPayload.Status.APPLIED, "reviewed extension applies");
        for (var cell : refreshed.cells()) helper.assertValueEqual(level.getBlockState(cell.position()), cell.state(),
                "confirmed terrain matches the extended preview");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "settlement_wall")
    public static void deepFoundationsKeepPreviewPayloadsBounded(GameTestHelper helper) {
        var base = floor(helper, 220);
        var level = helper.getLevel();
        var wall = new SettlementWall(SettlementWall.Type.LOW_STONE, List.of(base, base.east(18)), false, 0);
        for (var pos : SettlementWallGeometry.build(wall).foundations()) {
            for (int y = level.getMinBuildHeight(); y < pos.getY(); y++) {
                level.setBlock(pos.atY(y), Blocks.AIR.defaultBlockState(), 2);
            }
        }
        var preview = SettlementWallTerrain.preview(level, wall, pos -> true);
        helper.assertFalse(preview.error().isEmpty(), "bottomless foundations are rejected");
        helper.assertTrue(preview.cells().size() <= SettlementWall.MAX_BLOCKS, "extensions obey the existing network and placement limit");
        helper.assertValueEqual(SettlementWallTerrain.apply(level, preview), 0, "oversized or unsupported previews never partially apply");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "settlement_wall")
    public static void towerChoicesUseExistingDesignsAndKeepTowerlessSpansContinuous(GameTestHelper helper) {
        var start = new BlockPos(0, 80, 0);
        var end = start.east(24);
        var wall = new SettlementWall(SettlementWall.Type.LOW_STONE, List.of(start, end), false, 0,
                List.of(SettlementWall.Tower.NONE, SettlementWall.Tower.TALL_STONE));
        var plan = SettlementWallGeometry.build(wall);
        helper.assertTrue(plan.error().isEmpty(), "mixed tower options are valid");
        helper.assertTrue(plan.cells().stream().anyMatch(cell -> cell.position().equals(start) && !cell.state().isAir()),
                "removing a tower keeps the wall through its node");
        helper.assertFalse(plan.cells().stream().anyMatch(cell -> cell.position().equals(start.above(8))),
                "towerless node has no tower above the span");
        helper.assertTrue(plan.cells().stream().anyMatch(cell -> cell.position().getY() == end.getY() + 10 && !cell.state().isAir()),
                "selected tall tower uses its authored height");
        var automatic = new SettlementWall(wall.type(), wall.nodes(), false, 12, wall.nodeTowers());
        helper.assertTrue(SettlementWallGeometry.build(automatic).error().isEmpty(), "automatic towers coexist with node overrides");
        helper.assertValueEqual(automatic.towerAt(start.east(12)), wall.type().tower(), "automatic towers follow the wall type");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "settlement_wall")
    public static void heightAndTowerEditingPreserveDraftHistoryAndSettings(GameTestHelper helper) {
        var draft = new SettlementWallDraft(SettlementWall.Type.LOW_STONE, false, 16);
        draft.place(new BlockPos(0, 80, 0));
        draft.place(new BlockPos(24, 83, 0));
        draft.height(2);
        helper.assertValueEqual(draft.wall().nodes().getLast().getY(), 85, "height controls move the selected node");
        draft.tower(SettlementWall.Tower.NONE);
        draft.undo();
        helper.assertValueEqual(draft.wall().nodeTowers().getLast(), SettlementWall.Tower.DEFAULT, "undo restores tower option");
        draft.redo();
        draft.toggleLevel();
        helper.assertTrue(draft.wall().nodes().stream().allMatch(node -> node.getY() == 80), "level mode aligns the route");
        draft.height(1);
        helper.assertTrue(draft.wall().nodes().stream().allMatch(node -> node.getY() == 81), "height controls move a level route together");
        draft.place(new BlockPos(24, 70, 24));
        helper.assertValueEqual(draft.wall().nodes().getLast().getY(), 81, "new nodes retain the level route height");
        draft.stop();
        draft.begin(1); draft.drag(new BlockPos(26, 60, 0)); draft.finish();
        helper.assertValueEqual(draft.wall().nodes().get(1).getY(), 81, "horizontal dragging retains edited height");
        draft.undo();
        helper.assertValueEqual(draft.wall().nodes().get(1), new BlockPos(24, 81, 0), "undo restores moved node");
        draft.toggleLevel();
        draft.extend(); draft.place(new BlockPos(0, 75, 24));
        helper.assertValueEqual(draft.wall().nodes().getLast().getY(), 75, "ground mode accepts the picked elevation");
        var editor = new SettlementWallEditor();
        editor.begin(); editor.draft().toggleLevel(); editor.draft().toggleSnapping();
        editor.cancel(UUID.randomUUID(), request -> {});
        editor.begin();
        helper.assertTrue(editor.draft().level() && !editor.draft().snapping(), "height and snapping choices persist between drafts");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "settlement_wall")
    public static void draftSnappingClosesLoopsAndWeldsJunctions(GameTestHelper helper) {
        var draft = new SettlementWallDraft(SettlementWall.Type.LOW_STONE, false, 16);
        draft.place(BlockPos.ZERO); draft.place(new BlockPos(24, 0, 0)); draft.place(new BlockPos(24, 0, 24));
        var loop = draft.preview(new BlockPos(1, 0, 1));
        helper.assertTrue(loop.closed() && loop.nodes().size() == 3, "snapping previews a closed loop without a duplicate endpoint");
        draft.place(new BlockPos(1, 0, 1));
        helper.assertTrue(draft.wall().closed(), "the click matches the snapped preview");
        draft.undo();
        helper.assertFalse(draft.wall().closed(), "closing the loop is undoable");
        draft.place(new BlockPos(25, 0, 1));
        helper.assertFalse(draft.extending(), "connecting to an intermediate draft node stops extension");
        helper.assertValueEqual(draft.wall().nodes().getLast(), draft.wall().nodes().get(1), "junction positions match exactly");
        draft.tower(SettlementWall.Tower.TALL_STONE);
        helper.assertValueEqual(draft.wall().nodeTowers().get(1), SettlementWall.Tower.TALL_STONE, "junction tower choice is shared");
        draft.height(2);
        helper.assertValueEqual(draft.wall().nodes().getLast(), draft.wall().nodes().get(1), "height edits keep the junction welded");
        draft.begin(1); draft.drag(new BlockPos(26, 0, 1)); draft.finish();
        helper.assertValueEqual(draft.wall().nodes().getLast(), draft.wall().nodes().get(1), "dragging keeps the junction welded");
        helper.assertTrue(SettlementWallGeometry.build(draft.wall()).error().isEmpty(), "connected draft builds without duplicate towers");
        draft.undo(); draft.undo(); draft.undo(); draft.undo();
        draft.toggleSnapping();
        helper.assertFalse(draft.preview(new BlockPos(1, 0, 1)).closed(), "snapping can be disabled");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "settlement_wall")
    public static void terrainOverlapsAreSkippedAndNeverOwnedByWalls(GameTestHelper helper) {
        var base = floor(helper, 260);
        var level = helper.getLevel();
        var wall = new SettlementWall(SettlementWall.Type.LOW_STONE, List.of(base, base.east(18)), false, 0);
        var session = new SettlementWallSession();
        var id = UUID.randomUUID();
        var record = SettlementWallMaterialsGameTests.town(level, base);
        SettlementWallMaterialsGameTests.supply(level, record, base.north(8));
        var town = record.id();
        var preview = session.request(level, id, true, pos -> true,
                new SettlementWallRequestPayload(id, 1, SettlementWallRequestPayload.Action.PREVIEW, UUID.randomUUID(), wall), town);
        helper.assertValueEqual(preview.status(), SettlementWallResultPayload.Status.PREVIEW, "clear wall previews");
        var plan = SettlementWallGeometry.build(wall);
        var matching = plan.cells().stream().filter(cell -> cell.position().equals(base.east(8))).findFirst().orElseThrow();
        var hollow = plan.cells().stream().filter(cell -> cell.state().isAir()).findFirst().orElseThrow().position();
        var terrain = java.util.Map.of(base.east(6), Blocks.GRASS_BLOCK.defaultBlockState(),
                base.east(6).above(), Blocks.DIRT.defaultBlockState(), matching.position(), matching.state(),
                base.east(9), Blocks.PODZOL.defaultBlockState(), hollow, Blocks.STONE.defaultBlockState());
        terrain.forEach((pos, state) -> level.setBlock(pos, state, 2));
        level.setBlock(base.east(6).below(), Blocks.AIR.defaultBlockState(), 2);
        var reviewed = session.request(level, id, true, pos -> true,
                new SettlementWallRequestPayload(id, 1, SettlementWallRequestPayload.Action.CONFIRM, preview.token(), null), town);
        helper.assertValueEqual(reviewed.status(), SettlementWallResultPayload.Status.PREVIEW, "new terrain refreshes instead of rejecting the wall");
        helper.assertFalse(reviewed.token().equals(preview.token()), "terrain change requires another confirmation");
        helper.assertTrue(level.getBlockState(base).isAir(), "stale confirmation places nothing");
        for (var pos : terrain.keySet()) helper.assertTrue(reviewed.cells().stream().anyMatch(cell ->
                cell.position().equals(pos) && cell.kind() == SettlementWallTerrain.Kind.SKIPPED), "terrain is explicitly skipped");
        helper.assertFalse(reviewed.cells().stream().anyMatch(cell -> cell.position().equals(base.east(6).below())),
                "embedded foundations do not extend into caves under terrain");
        helper.assertValueEqual(session.request(level, id, true, pos -> true,
                new SettlementWallRequestPayload(id, 1, SettlementWallRequestPayload.Action.CONFIRM, reviewed.token(), null), town).status(),
                SettlementWallResultPayload.Status.APPLIED, "wall places around terrain");
        var data = SettlementWallsSavedData.get(level);
        for (var cell : reviewed.cells()) if (cell.kind() != SettlementWallTerrain.Kind.SKIPPED)
            helper.assertValueEqual(level.getBlockState(cell.position()), cell.state(), "remaining wall matches preview");
        terrain.forEach((pos, state) -> {
            helper.assertValueEqual(level.getBlockState(pos), state, "terrain survives placement");
            helper.assertTrue(data.at(town, level.dimension().location(), pos) == null, "even matching terrain is not owned by the wall");
        });
        var span = data.at(town, level.dimension().location(), base.east(10));
        helper.assertTrue(span != null, "placed span retains an identity");
        helper.assertTrue(SettlementWallsSavedData.modification(level, span, pos -> true).isEmpty(), "skipped terrain is not wall damage");
        var upgrade = new SettlementWall(SettlementWall.Type.TIMBER, span.wall().nodes(), false, 0, span.wall().nodeTowers(), span.wall().part());
        var changed = SettlementWallTerrain.preview(level, upgrade, pos -> true, span.blocks());
        helper.assertTrue(changed.error().isEmpty(), "upgrade also skips terrain: " + changed.error());
        SettlementWallTerrain.apply(level, changed);
        data.record(span.id(), town, level, upgrade, changed);
        span = data.get(span.id());
        var restored = SettlementWallsSavedData.load(data.save(new net.minecraft.nbt.CompoundTag(), level.registryAccess()), level.registryAccess());
        helper.assertValueEqual(restored.get(span.id()), span, "terrain-aware ownership survives reload");
        SettlementWallTerrain.apply(level, SettlementWallTerrain.demolition(level, span.blocks(), pos -> true));
        terrain.forEach((pos, state) -> {
            helper.assertValueEqual(level.getBlockState(pos), state, "terrain survives upgrade and destruction");
            helper.assertTrue(restored.at(town, level.dimension().location(), pos) == null, "terrain remains unowned after reload");
        });
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "settlement_wall")
    public static void wallsRestOnPathsAndSlabsWithoutExcavatingThem(GameTestHelper helper) {
        for (var type : SettlementWall.Type.values()) {
            var base = floor(helper, 280);
            var wall = new SettlementWall(type, List.of(base, base.east(18)), false, 0);
            var terrain = new java.util.HashMap<BlockPos, net.minecraft.world.level.block.state.BlockState>();
            for (var pos : SettlementWallGeometry.build(wall).foundations()) terrain.put(pos.below(),
                    (pos.getX() % 2 == 0 ? Blocks.DIRT_PATH : Blocks.STONE_SLAB).defaultBlockState());
            terrain.put(base.east(6), Blocks.STONE_SLAB.defaultBlockState());
            terrain.forEach((pos, state) -> helper.getLevel().setBlock(pos, state, 2));
            var preview = SettlementWallTerrain.preview(helper.getLevel(), wall, pos -> true);
            helper.assertTrue(preview.error().isEmpty(), "partial-height ground accepts " + type + ": " + preview.error());
            helper.assertTrue(SettlementWallTerrain.apply(helper.getLevel(), preview) > 0, "wall places above partial-height terrain");
            terrain.forEach((pos, state) -> helper.assertValueEqual(helper.getLevel().getBlockState(pos), state, "paths and slabs remain intact"));
        }
        helper.succeed();
    }

    private static BlockPos floor(GameTestHelper helper, int height) {
        var base = helper.absolutePos(new BlockPos(5, height, 5));
        for (var pos : BlockPos.betweenClosed(base.offset(-4, -1, -5), base.offset(23, 12, 5)))
            helper.getLevel().setBlock(pos, pos.getY() == base.getY() - 1 ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
        return base;
    }
}
