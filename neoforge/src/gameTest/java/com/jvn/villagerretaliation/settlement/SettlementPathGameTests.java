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
        helper.assertValueEqual(draft.path().nodes().getFirst(), curved.nodes().getFirst(), "branch anchor cannot be moved off its junction");
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
