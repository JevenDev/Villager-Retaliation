package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.client.settlement.SettlementPathDraft;
import com.jvn.villagerretaliation.network.SettlementPathRequestPayload;
import com.jvn.villagerretaliation.network.SettlementPathResultPayload;
import io.netty.buffer.Unpooled;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("settlement_path")
@PrefixGameTestTemplate(false)
public final class SettlementPathGameTests {
    private SettlementPathGameTests() {}

    @GameTest(template = "empty", batch = "settlement_path")
    public static void widthsCurvesNoiseAndOverlapsStayBoundedAndConnected(GameTestHelper helper) {
        for (int width = 1; width <= 7; width++) {
            for (int sign : List.of(-1, 1)) {
                var path = path(new Vec3(sign * 20 + 0.5, 70, 0.5), new Vec3(sign * 20 + 12.5, 70, 0.5), width, false);
                var footprint = SettlementPathGeometry.rasterize(path);
                helper.assertTrue(footprint.error().isEmpty(), "straight path is valid");
                int middle = sign * 20 + 6;
                long crossSection = footprint.columns().stream().filter(pos -> pos.getX() == middle).count();
                helper.assertValueEqual(crossSection, (long) width, "every width has an exact axis-aligned cross section");
                var natural = new SettlementPath(path.nodes(), width, SettlementPath.Style.NATURAL, path.material(), 1337, false);
                var worn = SettlementPathGeometry.rasterize(natural);
                helper.assertValueEqual(worn, SettlementPathGeometry.rasterize(natural), "noise is stable");
                helper.assertTrue(new HashSet<>(footprint.columns()).containsAll(worn.columns()), "noise stays inside the width");
                connected(helper, worn.columns());
                if (width == 1) helper.assertValueEqual(worn.columns(), footprint.columns(), "one-block paths remain solid");
            }
        }
        for (int width : List.of(2, 4, 6)) {
            for (long seed = 0; seed < 32; seed++) {
                var forward = path(new Vec3(0.5, 70, 0.5), new Vec3(12.5, 70, 12.5), width, false);
                var reverse = new SettlementPath(List.of(forward.nodes().getLast(), forward.nodes().getFirst()), width,
                        SettlementPath.Style.NATURAL, SettlementPath.Material.DIRT, seed, false);
                var cells = SettlementPathGeometry.rasterize(reverse).columns();
                for (var node : reverse.nodes()) helper.assertTrue(cells.contains(BlockPos.containing(node.position())),
                        "natural paths retain every chosen connection point, including the far endpoint");
                connected(helper, cells);
            }
        }
        var diagonal = path(new Vec3(0.5, 70, 0.5), new Vec3(12.5, 70, 12.5), 1, false);
        connected(helper, SettlementPathGeometry.rasterize(diagonal).columns());
        var curve = new SettlementPath(List.of(new SettlementPath.Node(new Vec3(0.5, 70, 0.5), Vec3.ZERO, new Vec3(0, 0, 8)),
                new SettlementPath.Node(new Vec3(12.5, 70, 0.5), new Vec3(0, 0, 8), Vec3.ZERO)), 3,
                SettlementPath.Style.CLEAN, SettlementPath.Material.DIRT, 1, false);
        helper.assertTrue(SettlementPathGeometry.point(curve.nodes().getFirst(), curve.nodes().getLast(), 0.5).z > 5,
                "Bezier handles form a visible arc");
        connected(helper, SettlementPathGeometry.rasterize(curve).columns());
        var overlap = new SettlementPath(List.of(diagonal.nodes().getFirst(), diagonal.nodes().getLast(), diagonal.nodes().getFirst()),
                7, SettlementPath.Style.CLEAN, SettlementPath.Material.DIRT, 1, false);
        var columns = SettlementPathGeometry.rasterize(overlap).columns();
        helper.assertValueEqual(columns.size(), new HashSet<>(columns).size(), "overlapping segments convert each column once");
        helper.assertFalse(SettlementPathGeometry.rasterize(path(Vec3.ZERO, new Vec3(2000, 0, 0), 7, false)).error().isEmpty(),
                "oversized drafts report a limit instead of truncating");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "settlement_path")
    public static void conversionClearsOnlyGrassAndPreservesObstacles(GameTestHelper helper) {
        var base = helper.absolutePos(new BlockPos(1, 20, 1));
        var level = helper.getLevel();
        var soils = List.of(Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.COARSE_DIRT, Blocks.ROOTED_DIRT, Blocks.PODZOL, Blocks.MYCELIUM,
                Blocks.FARMLAND, Blocks.STONE, Blocks.DIRT, Blocks.DIRT, Blocks.DIRT, Blocks.DIRT, Blocks.DIRT_PATH, Blocks.DIRT);
        for (int i = 0; i < soils.size(); i++) {
            var pos = base.offset(i, 0, 0);
            for (int y = 1; y <= 5; y++) level.setBlock(pos.above(y), Blocks.AIR.defaultBlockState(), 2);
            level.setBlock(pos, soils.get(i).defaultBlockState(), 2);
        }
        level.setBlock(base.above(), Blocks.SHORT_GRASS.defaultBlockState(), 2);
        level.setBlock(base.offset(1, 1, 0), Blocks.LARGE_FERN.defaultBlockState().setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER), 2);
        level.setBlock(base.offset(1, 2, 0), Blocks.LARGE_FERN.defaultBlockState().setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER), 2);
        level.setBlock(base.offset(8, 1, 0), Blocks.POPPY.defaultBlockState(), 2);
        level.setBlock(base.offset(9, 1, 0), Blocks.OAK_SAPLING.defaultBlockState(), 2);
        level.setBlock(base.offset(10, 1, 0), Blocks.WATER.defaultBlockState(), 2);
        level.setBlock(base.offset(11, 2, 0), Blocks.STONE.defaultBlockState(), 2);
        level.setBlock(base.offset(13, 1, 0), Blocks.DIRT.defaultBlockState(), 2);
        var path = path(base.getCenter(), base.offset(13, 1, 0).getCenter(), 1, false);
        var preview = SettlementPathTerrain.preview(level, path, pos -> true);
        helper.assertTrue(preview.error().isEmpty(), "gaps do not prevent confirmation: " + preview.error() + " " + preview.cells().stream().limit(2).toList());
        helper.assertValueEqual(preview.count(SettlementPathTerrain.Kind.CONVERT), 7L, "only six eligible soils and a raised dirt block convert");
        helper.assertValueEqual(preview.count(SettlementPathTerrain.Kind.EXISTING), 1L, "existing path is identified");
        SettlementPathTerrain.apply(level, preview);
        for (int i = 0; i < 6; i++) helper.assertTrue(level.getBlockState(base.offset(i, 0, 0)).is(Blocks.DIRT_PATH), "eligible soil converts");
        helper.assertTrue(level.getBlockState(base.above()).isAir() && level.getBlockState(base.offset(1, 2, 0)).isAir(), "grass and both fern halves clear");
        helper.assertTrue(level.getBlockState(base.offset(8, 1, 0)).is(Blocks.POPPY), "flowers survive");
        helper.assertTrue(level.getBlockState(base.offset(9, 1, 0)).is(Blocks.OAK_SAPLING), "saplings survive");
        helper.assertTrue(level.getBlockState(base.offset(6, 0, 0)).is(Blocks.FARMLAND), "farmland is preserved");
        helper.assertTrue(level.getBlockState(base.offset(10, 0, 0)).is(Blocks.DIRT), "submerged dirt is preserved");
        helper.assertTrue(level.getBlockState(base.offset(11, 0, 0)).is(Blocks.DIRT), "soil below an overhang is untouched");
        helper.assertTrue(level.getBlockState(base.offset(13, 1, 0)).is(Blocks.DIRT_PATH), "paths follow raised terrain");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "settlement_path")
    public static void confirmationRequiresCurrentTerrainPermissionAndToken(GameTestHelper helper) {
        var base = helper.absolutePos(new BlockPos(1, 20, 1));
        var level = helper.getLevel();
        for (int i = 0; i < 5; i++) {
            for (int y = 1; y < 5; y++) level.setBlock(base.offset(i, y, 0), Blocks.AIR.defaultBlockState(), 2);
            level.setBlock(base.offset(i, 0, 0), Blocks.DIRT.defaultBlockState(), 2);
        }
        var session = new SettlementPathSession();
        UUID id = UUID.randomUUID(), empty = new UUID(0, 0);
        var request = new SettlementPathRequestPayload(id, 1, SettlementPathRequestPayload.Action.PREVIEW, empty,
                path(base.getCenter(), base.offset(4, 0, 0).getCenter(), 1, false));
        var preview = session.request(level, id, true, pos -> true, request);
        helper.assertValueEqual(preview.status(), SettlementPathResultPayload.Status.PREVIEW, "preview changes no terrain: " + preview.message() + " " + preview.cells().stream().limit(2).toList());
        helper.assertTrue(level.getBlockState(base).is(Blocks.DIRT), "preview leaves soil intact");
        var confirm = new SettlementPathRequestPayload(id, 1, SettlementPathRequestPayload.Action.CONFIRM, preview.token(), null);
        helper.assertValueEqual(session.request(level, id, false, pos -> true, confirm).status(), SettlementPathResultPayload.Status.REJECTED, "permission is checked again");
        helper.assertValueEqual(session.request(level, UUID.randomUUID(), true, pos -> true, confirm).status(),
                SettlementPathResultPayload.Status.REJECTED, "wrong viewing session is rejected");
        var forged = new SettlementPathRequestPayload(id, 1, SettlementPathRequestPayload.Action.CONFIRM, empty, null);
        helper.assertValueEqual(session.request(level, id, true, pos -> true, forged).status(), SettlementPathResultPayload.Status.REJECTED, "unknown preview token is rejected");
        level.setBlock(base.offset(2, 0, 0), Blocks.STONE.defaultBlockState(), 2);
        var refreshed = session.request(level, id, true, pos -> true, confirm);
        helper.assertValueEqual(refreshed.status(), SettlementPathResultPayload.Status.PREVIEW, "changed terrain requires a fresh review");
        helper.assertFalse(refreshed.token().equals(preview.token()), "terrain refresh invalidates the old token");
        helper.assertTrue(level.getBlockState(base).is(Blocks.DIRT), "stale confirmation applies nothing");
        confirm = new SettlementPathRequestPayload(id, 1, SettlementPathRequestPayload.Action.CONFIRM, refreshed.token(), null);
        var applied = session.request(level, id, true, pos -> true, confirm);
        helper.assertValueEqual(applied.status(), SettlementPathResultPayload.Status.APPLIED, "confirmed path applies without workers or resources");
        level.setBlock(base, Blocks.GRASS_BLOCK.defaultBlockState(), 2);
        helper.assertValueEqual(session.request(level, id, true, pos -> true, confirm), applied, "duplicate confirmation returns its receipt");
        helper.assertTrue(level.getBlockState(base).is(Blocks.GRASS_BLOCK), "duplicate confirmation does not overwrite later edits");
        var cancelled = new SettlementPathRequestPayload(id, 2, SettlementPathRequestPayload.Action.CANCEL, empty, null);
        session.request(level, id, true, pos -> true, cancelled);
        helper.assertValueEqual(session.request(level, id, true, pos -> true, confirm).status(), SettlementPathResultPayload.Status.REJECTED, "cancelled and older revisions cannot replay");
        helper.assertFalse(SettlementPathTerrain.preview(level, request.path(), pos -> false).error().isEmpty(), "out-of-bounds paths are rejected");
        var unloaded = path(new Vec3(20_000_000.5, 64, 20_000_000.5), new Vec3(20_000_003.5, 64, 20_000_000.5), 1, false);
        helper.assertFalse(SettlementPathTerrain.preview(level, unloaded, pos -> true).error().isEmpty(), "unloaded terrain is never generated by previewing");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "settlement_path")
    public static void branchingUsesTerrainAndDraftHistoryStaysLocal(GameTestHelper helper) {
        var base = helper.absolutePos(new BlockPos(1, 20, 1));
        for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++) {
            helper.getLevel().setBlock(base.offset(x, 0, z), Blocks.DIRT.defaultBlockState(), 2);
            for (int y = 1; y < 5; y++) helper.getLevel().setBlock(base.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
        }
        var branch = path(base.offset(3, 0, 0).getCenter(), base.offset(3, 0, 5).getCenter(), 3, true);
        helper.assertFalse(SettlementPathTerrain.preview(helper.getLevel(), branch, pos -> true).error().isEmpty(), "branch requires existing terrain path");
        helper.getLevel().setBlock(base.offset(3, 0, 0), Blocks.DIRT_PATH.defaultBlockState(), 2);
        var preview = SettlementPathTerrain.preview(helper.getLevel(), branch, pos -> true);
        helper.assertTrue(preview.error().isEmpty(), "a manually placed path accepts a branch: " + preview.error() + " " + preview.cells().stream().limit(2).toList());
        var draft = new SettlementPathDraft(3, SettlementPath.Style.CLEAN, 42, true);
        draft.place(branch.nodes().getFirst().position());
        draft.finish();
        draft.place(branch.nodes().getLast().position());
        draft.drag(branch.nodes().getLast().position().add(2, 0, 2), false);
        draft.finish();
        var curved = draft.path();
        helper.assertValueEqual(curved.nodes().getLast().incoming(), curved.nodes().getLast().outgoing().scale(-1), "new curve handles mirror");
        draft.begin(1, 1);
        draft.drag(curved.nodes().getLast().position().add(3, 0, 0), true);
        draft.finish();
        helper.assertValueEqual(draft.path().nodes().getLast().incoming(), curved.nodes().getLast().incoming(), "independent handle leaves its opposite unchanged");
        draft.undo();
        helper.assertValueEqual(draft.path(), curved, "undo restores the complete previous gesture");
        draft.redo();
        helper.assertFalse(draft.path().equals(curved), "redo restores independent handle adjustment");
        draft.settings(7, SettlementPath.Style.NATURAL);
        draft.undo();
        helper.assertValueEqual(draft.path().width(), 3, "width changes participate in draft undo");
        draft.select(0);
        draft.remove();
        helper.assertValueEqual(draft.path().nodes().size(), 2, "branch anchor cannot be deleted");
        draft.begin(0, 0);
        draft.drag(base.getCenter(), false);
        draft.finish();
        helper.assertValueEqual(draft.path().nodes().getFirst().position(), curved.nodes().getFirst().position(), "branch anchor cannot be moved off its junction");
        draft.inserting(true);
        draft.place(base.offset(4, 0, 2).getCenter());
        draft.finish();
        helper.assertValueEqual(draft.path().nodes().size(), 3, "nodes insert after the selection");
        draft.remove();
        helper.assertValueEqual(draft.path().nodes().size(), 2, "inserted nodes can be removed");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "settlement_path")
    public static void payloadsRoundTripAndRejectInvalidDrafts(GameTestHelper helper) {
        var request = new SettlementPathRequestPayload(UUID.randomUUID(), 5, SettlementPathRequestPayload.Action.PREVIEW, UUID.randomUUID(),
                path(new Vec3(-5.5, 70, -4.5), new Vec3(5.5, 72, -4.5), 7, true));
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            SettlementPathRequestPayload.STREAM_CODEC.encode(buffer, request);
            helper.assertValueEqual(SettlementPathRequestPayload.STREAM_CODEC.decode(buffer), request, "path request round trips");
            var result = new SettlementPathResultPayload(request.session(), request.revision(), request.token(), SettlementPathResultPayload.Status.PREVIEW,
                    List.of(new SettlementPathResultPayload.Cell(BlockPos.ZERO, SettlementPathTerrain.Kind.CONVERT)), "");
            SettlementPathResultPayload.STREAM_CODEC.encode(buffer, result);
            helper.assertValueEqual(SettlementPathResultPayload.STREAM_CODEC.decode(buffer), result, "preview result round trips");
        } finally { buffer.release(); }
        boolean invalid = false;
        try { path(Vec3.ZERO, Vec3.ZERO, 8, false); } catch (IllegalArgumentException expected) { invalid = true; }
        helper.assertTrue(invalid, "out-of-range widths are rejected");
        invalid = false;
        try { new SettlementPath.Node(new Vec3(Double.NaN, 0, 0)); } catch (IllegalArgumentException expected) { invalid = true; }
        helper.assertTrue(invalid, "non-finite node coordinates are rejected");
        invalid = false;
        try { new SettlementPath(java.util.Collections.nCopies(65, new SettlementPath.Node(Vec3.ZERO)), 3, SettlementPath.Style.CLEAN, SettlementPath.Material.DIRT, 1, false); }
        catch (IllegalArgumentException expected) { invalid = true; }
        helper.assertTrue(invalid, "node count is bounded");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "settlement_path")
    public static void loopsCloseAtEveryWidthAndApplyTheClosingSegment(GameTestHelper helper) {
        var base = helper.absolutePos(new BlockPos(1, 20, 1));
        var level = helper.getLevel();
        for (int x = -4; x <= 10; x++) for (int z = -4; z <= 10; z++) {
            level.setBlock(base.offset(x, 0, z), Blocks.DIRT.defaultBlockState(), 2);
            for (int y = 1; y <= 4; y++) level.setBlock(base.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
        }
        var nodes = List.of(new SettlementPath.Node(base.getCenter()), new SettlementPath.Node(base.offset(6, 0, 0).getCenter()),
                new SettlementPath.Node(base.offset(6, 0, 6).getCenter()), new SettlementPath.Node(base.offset(0, 0, 6).getCenter()));
        for (int width = 1; width <= 7; width++) for (var style : SettlementPath.Style.values()) {
            var loop = new SettlementPath(nodes, width, style, SettlementPath.Material.DIRT, 42, false, true);
            var samples = SettlementPathGeometry.samples(loop);
            helper.assertValueEqual(samples.getLast(), samples.getFirst(), "closed curves return to the first anchor");
            connected(helper, SettlementPathGeometry.rasterize(loop).columns());
        }
        var loop = new SettlementPath(nodes, 1, SettlementPath.Style.NATURAL, SettlementPath.Material.DIRT, 42, false, true);
        var session = new SettlementPathSession();
        var id = UUID.randomUUID();
        var request = new SettlementPathRequestPayload(id, 1, SettlementPathRequestPayload.Action.PREVIEW, new UUID(0, 0), loop);
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess());
        try {
            SettlementPathRequestPayload.STREAM_CODEC.encode(buffer, request);
            helper.assertValueEqual(SettlementPathRequestPayload.STREAM_CODEC.decode(buffer), request, "closed loops round trip");
        } finally { buffer.release(); }
        var preview = session.request(level, id, true, pos -> true, request);
        helper.assertValueEqual(preview.status(), SettlementPathResultPayload.Status.PREVIEW, "server previews loops");
        var result = session.request(level, id, true, pos -> true,
                new SettlementPathRequestPayload(id, 1, SettlementPathRequestPayload.Action.CONFIRM, preview.token(), null));
        helper.assertValueEqual(result.status(), SettlementPathResultPayload.Status.APPLIED, "server confirms loops");
        helper.assertTrue(level.getBlockState(base.offset(0, 0, 3)).is(Blocks.DIRT_PATH), "the closing segment becomes terrain");
        boolean invalid = false;
        try { new SettlementPath(nodes.subList(0, 2), 1, loop.style(), loop.material(), 42, false, true); }
        catch (IllegalArgumentException expected) { invalid = true; }
        helper.assertTrue(invalid, "loops require at least three nodes");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "settlement_path")
    public static void stoppingExtensionKeepsDraftAndCurvedNodesContinue(GameTestHelper helper) {
        var draft = new SettlementPathDraft(3, SettlementPath.Style.CLEAN, 42, false, true);
        draft.place(new Vec3(0.5, 70, 0.5));
        draft.finish();
        var preview = draft.preview(new Vec3(6.5, 70, 0.5));
        draft.place(new Vec3(6.5, 70, 0.5));
        draft.finish();
        helper.assertValueEqual(draft.path(), preview, "curved hover preview matches the placed nodes");
        helper.assertTrue(draft.path().nodes().stream().allMatch(node -> node.outgoing().lengthSqr() > 0),
                "curved placement initializes mirrored handles without dragging");
        var kept = draft.path();
        draft.stopExtending();
        helper.assertValueEqual(draft.path(), kept, "stopping does not discard geometry");
        helper.assertFalse(draft.place(new Vec3(6.5, 70, 6.5)), "stopped drafts reject extension");
        draft.extend();
        draft.place(new Vec3(6.5, 70, 6.5));
        draft.finish();
        draft.toggleLoop();
        helper.assertTrue(draft.path().closed() && !draft.extending(), "closed drafts stop extending");
        draft.undo();
        helper.assertFalse(draft.path().closed(), "undo opens the loop");
        draft.redo();
        helper.assertTrue(draft.path().closed(), "redo closes the loop");
        draft.begin(0, 0);
        draft.drag(new Vec3(1.5, 70, 1.5), false);
        draft.finish();
        var samples = SettlementPathGeometry.samples(draft.path());
        helper.assertValueEqual(samples.getLast(), draft.path().nodes().getFirst().position(), "moving the start keeps the loop attached");
        draft.remove();
        helper.assertFalse(draft.path().closed(), "removing down to two nodes opens the loop");
        draft.smooth();
        helper.assertFalse(draft.smoothMode(), "the player can return to corners");
        draft.undo();
        helper.assertTrue(draft.smoothMode(), "undo restores the curve choice");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "settlement_path")
    public static void curveModeMakesEverySelectedNodeEditable(GameTestHelper helper) {
        var draft = new SettlementPathDraft(3, SettlementPath.Style.CLEAN, 42, false);
        for (var point : List.of(new Vec3(0, 70, 0), new Vec3(6, 70, 0), new Vec3(6, 70, 6))) {
            draft.place(point);
            draft.finish();
        }
        draft.stopExtending();
        draft.smooth();
        var before = draft.path();
        int version = draft.version();
        draft.begin(0, 0);
        helper.assertTrue(draft.path().nodes().getFirst().outgoing().lengthSqr() > 0,
                "selecting a straight node in curve mode immediately exposes handles");
        helper.assertTrue(draft.version() > version, "new handles invalidate the terrain preview");
        draft.cancelDrag();
        helper.assertValueEqual(draft.path(), before, "cancelling selection restores the original handles");
        draft.begin(0, 0);
        draft.finish();
        var curved = draft.path();
        draft.undo();
        helper.assertValueEqual(draft.path(), before, "handle initialization can be undone");
        helper.assertTrue(draft.smoothMode(), "undoing a node edit keeps curve mode");
        draft.redo();
        helper.assertValueEqual(draft.path(), curved, "handle initialization can be redone");
        draft.begin(0, 1);
        draft.drag(new Vec3(3, 70, 1), true);
        draft.finish();
        var adjusted = draft.path().nodes().getFirst();
        draft.begin(1, 0);
        draft.finish();
        helper.assertTrue(draft.path().nodes().get(1).outgoing().lengthSqr() > 0,
                "another straight node gets handles without toggling the mode again");
        draft.begin(0, 0);
        draft.finish();
        helper.assertValueEqual(draft.path().nodes().getFirst(), adjusted, "reselecting preserves independently edited handles");
        draft.smooth();
        draft.begin(1, 0);
        draft.finish();
        helper.assertFalse(draft.smoothMode(), "selecting a curved node keeps the chosen lines mode");
        draft.begin(0, 0);
        draft.finish();
        helper.assertTrue(draft.path().nodes().getFirst().outgoing().lengthSqr() == 0,
                "lines mode does not initialize curve handles on selection");
        draft.extend();
        draft.place(new Vec3(12, 70, 6));
        draft.finish();
        helper.assertTrue(draft.path().nodes().getLast().outgoing().lengthSqr() == 0, "lines mode continues with straight nodes");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "settlement_path")
    public static void invalidColumnsStayVisibleWithoutApplyingAnyTerrain(GameTestHelper helper) {
        var base = helper.absolutePos(new BlockPos(1, 20, 1));
        var level = helper.getLevel();
        for (int x = 0; x < 5; x++) {
            level.setBlock(base.offset(x, 0, 0), Blocks.DIRT.defaultBlockState(), 2);
            for (int y = 1; y <= 4; y++) level.setBlock(base.offset(x, y, 0), Blocks.AIR.defaultBlockState(), 2);
        }
        var path = path(base.getCenter(), base.offset(4, 0, 0).getCenter(), 1, false);
        java.util.function.Predicate<BlockPos> inside = pos -> pos.getX() < base.getX() + 3;
        var preview = SettlementPathTerrain.preview(level, path, inside);
        helper.assertValueEqual(preview.cells().size(), 5, "invalid columns do not discard the footprint");
        helper.assertValueEqual(preview.count(SettlementPathTerrain.Kind.CONVERT), 3L, "eligible columns remain visible");
        helper.assertValueEqual(preview.count(SettlementPathTerrain.Kind.OUTSIDE), 2L, "outside columns have their own preview status");
        helper.assertValueEqual(SettlementPathTerrain.apply(level, preview), 0, "invalid previews cannot partially apply");
        var id = UUID.randomUUID();
        var session = new SettlementPathSession();
        var result = session.request(level, id, true, inside,
                new SettlementPathRequestPayload(id, 1, SettlementPathRequestPayload.Action.PREVIEW, new UUID(0, 0), path));
        helper.assertValueEqual(result.status(), SettlementPathResultPayload.Status.REJECTED, "server rejects outside paths");
        helper.assertValueEqual(result.cells().size(), 5, "server rejection includes the visible footprint");
        var confirm = session.request(level, id, true, inside,
                new SettlementPathRequestPayload(id, 1, SettlementPathRequestPayload.Action.CONFIRM, result.token(), null));
        helper.assertValueEqual(confirm.status(), SettlementPathResultPayload.Status.REJECTED, "outside preview tokens cannot confirm");
        helper.assertTrue(level.getBlockState(base).is(Blocks.DIRT), "rejected paths change no terrain");
        var unloaded = path(new Vec3(20_000_000.5, 64, 20_000_000.5), new Vec3(20_000_003.5, 64, 20_000_000.5), 1, false);
        var missing = SettlementPathTerrain.preview(level, unloaded, pos -> true);
        helper.assertValueEqual(missing.count(SettlementPathTerrain.Kind.UNAVAILABLE), 4L, "unloaded columns keep their footprint without loading chunks");
        helper.assertFalse(level.hasChunkAt(unloaded.start()), "previewing unavailable terrain does not load it");
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess());
        try {
            SettlementPathResultPayload.STREAM_CODEC.encode(buffer, result);
            helper.assertValueEqual(SettlementPathResultPayload.STREAM_CODEC.decode(buffer), result, "outside preview statuses round trip");
        } finally { buffer.release(); }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "settlement_path", timeoutTicks = 100)
    public static void rejectedPreviewsRemainVisibleAndRefreshStationaryTerrain(GameTestHelper helper) {
        var base = helper.absolutePos(new BlockPos(1, 20, 1));
        var level = helper.getLevel();
        for (int x = 0; x < 5; x++) {
            level.setBlock(base.offset(x, 0, 0), Blocks.DIRT.defaultBlockState(), 2);
            for (int y = 1; y <= 4; y++) level.setBlock(base.offset(x, y, 0), Blocks.AIR.defaultBlockState(), 2);
        }
        var time = new java.util.concurrent.atomic.AtomicLong(1000);
        var editor = new com.jvn.villagerretaliation.client.settlement.SettlementPathEditor(time::get);
        var id = UUID.randomUUID();
        editor.begin(false);
        editor.draft().settings(1, SettlementPath.Style.CLEAN);
        editor.draft().place(base.getCenter());
        editor.draft().finish();
        editor.draft().place(base.offset(4, 0, 0).getCenter());
        editor.draft().finish();
        editor.stopExtending();
        editor.tick(level, id, pos -> true, request -> {});
        helper.assertValueEqual(editor.cells().size(), 5, "valid draft has a block preview");
        editor.accept(new SettlementPathResultPayload(id, 1, UUID.randomUUID(), SettlementPathResultPayload.Status.REJECTED,
                List.of(), "Review the path"));
        editor.tick(level, id, pos -> true, request -> {});
        helper.assertValueEqual(editor.cells().size(), 5, "an empty rejection does not erase the client preview");
        helper.assertFalse(editor.canConfirm(), "retained previews do not bypass server rejection");
        helper.assertFalse(editor.centerline().isEmpty(), "rejected paths retain their curve gizmo");
        var cells = editor.cells();
        var centerline = editor.centerline();
        time.addAndGet(300);
        editor.tick(level, id, pos -> true, request -> {});
        helper.assertTrue(editor.cells() == cells, "unchanged terrain keeps the render snapshot");
        helper.assertTrue(editor.centerline() == centerline, "stationary terrain refreshes reuse curve samples");
        level.setBlock(base.offset(2, 0, 0), Blocks.STONE.defaultBlockState(), 2);
        time.addAndGet(250);
        editor.tick(level, id, pos -> true, request -> {});
        helper.assertTrue(editor.cells().stream().anyMatch(cell -> cell.position().equals(base.offset(2, 0, 0))
                && cell.kind() == SettlementPathTerrain.Kind.SKIPPED), "stationary previews refresh changed terrain");
        time.addAndGet(250);
        editor.tick(level, id, pos -> false, request -> {});
        helper.assertTrue(editor.cells().stream().allMatch(cell -> cell.kind() == SettlementPathTerrain.Kind.OUTSIDE),
                "changed territory updates a stationary draft to outside markers");
        helper.assertValueEqual(editor.cells().size(), 5, "outside markers remain visible after an empty rejection");
        editor.accept(new SettlementPathResultPayload(id, 1, UUID.randomUUID(), SettlementPathResultPayload.Status.REJECTED,
                List.of(new SettlementPathResultPayload.Cell(base, SettlementPathTerrain.Kind.UNAVAILABLE)), "Load terrain"));
        editor.tick(level, id, pos -> true, request -> {});
        time.addAndGet(250);
        var requests = new java.util.ArrayList<SettlementPathRequestPayload>();
        editor.tick(level, id, pos -> true, requests::add);
        helper.assertTrue(requests.stream().anyMatch(request -> request.action() == SettlementPathRequestPayload.Action.PREVIEW
                && request.revision() == 2), "loaded terrain automatically requests a fresh authoritative preview");
        helper.assertValueEqual(editor.cells().size(), 5, "automatic refresh keeps the footprint visible");
        helper.succeed();
    }

    private static SettlementPath path(Vec3 a, Vec3 b, int width, boolean branch) {
        return new SettlementPath(List.of(new SettlementPath.Node(a), new SettlementPath.Node(b)), width,
                SettlementPath.Style.CLEAN, SettlementPath.Material.DIRT, 1337, branch);
    }
    private static void connected(GameTestHelper helper, List<BlockPos> columns) {
        var remaining = new HashSet<>(columns.stream().map(pos -> new BlockPos(pos.getX(), 0, pos.getZ())).toList());
        var queue = new ArrayDeque<BlockPos>();
        var first = remaining.iterator().next();
        remaining.remove(first);
        queue.add(first);
        while (!queue.isEmpty()) {
            var point = queue.remove();
            for (var neighbor : List.of(point.north(), point.south(), point.east(), point.west()))
                if (remaining.remove(neighbor)) queue.add(neighbor);
        }
        helper.assertTrue(remaining.isEmpty(), "path footprint is connected without diagonal-only gaps: " + remaining.stream().limit(8).toList() + " first " + first);
    }
}
