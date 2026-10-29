package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import com.jvn.villagerretaliation.network.SettlementWallRequestPayload;
import com.jvn.villagerretaliation.network.SettlementWallRequestPayload.Action;
import com.jvn.villagerretaliation.network.SettlementWallResultPayload;
import com.jvn.villagerretaliation.network.SettlementWallResultPayload.Status;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("settlement_wall")
@PrefixGameTestTemplate(false)
public final class SettlementWallUpgradeGameTests {
    private SettlementWallUpgradeGameTests() {}

    @GameTest(template = "empty", batch = "settlement_wall")
    public static void upgradesPreserveRouteAndPersistChangedTowersAndFoundations(GameTestHelper helper) {
        var fixture = placed(helper, 45);
        var level = helper.getLevel();
        var session = new SettlementWallSession();
        var id = UUID.randomUUID();
        var selected = select(helper, fixture, session, id);
        helper.assertValueEqual(selected.status(), Status.SELECTED, "placed wall can be selected");
        var original = selected.selected();
        var changed = new SettlementWall(SettlementWall.Type.TALL_STONE, original.nodes(), original.closed(), original.towerSpacing(),
                original.nodeTowers(), original.part());
        var preview = session.request(level, id, true, pos -> true,
                new SettlementWallRequestPayload(id, 2, Action.PREVIEW, UUID.randomUUID(), changed, selected.upgrade(), BlockPos.ZERO), fixture.town());
        helper.assertValueEqual(preview.status(), Status.PREVIEW, "upgrade previews replacements: " + preview.message());
        helper.assertTrue(preview.cells().stream().anyMatch(cell -> !cell.state().isAir()
                && level.getBlockState(cell.position()).isAir()), "taller tower blocks appear in the preview");
        var confirm = new SettlementWallRequestPayload(id, 2, Action.CONFIRM, preview.token(), null);
        var applied = session.request(level, id, true, pos -> true, confirm, fixture.town());
        helper.assertValueEqual(applied.status(), Status.APPLIED, "reviewed upgrade applies");
        for (var cell : preview.cells()) helper.assertValueEqual(level.getBlockState(cell.position()), cell.state(), "upgrade matches preview");
        var data = SettlementWallsSavedData.get(level);
        var entry = data.get(selected.upgrade());
        helper.assertValueEqual(entry.wall(), changed, "same wall identity now stores upgraded design");
        helper.assertValueEqual(entry.wall().nodes(), original.nodes(), "route is preserved");
        var restored = SettlementWallsSavedData.load(data.save(new CompoundTag(), level.registryAccess()), level.registryAccess());
        helper.assertValueEqual(restored.get(entry.id()), entry, "wall identity, node options and blocks survive reload");
        level.setBlock(fixture.base(), Blocks.AIR.defaultBlockState(), 2);
        helper.assertValueEqual(session.request(level, id, true, pos -> true, confirm, fixture.town()), applied, "duplicate confirmation returns receipt");
        helper.assertTrue(level.getBlockState(fixture.base()).isAir(), "duplicate does not overwrite a later edit");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "settlement_wall")
    public static void upgradesRejectForeignChangedAndMovedWalls(GameTestHelper helper) {
        var fixture = placed(helper, 85);
        var level = helper.getLevel();
        var id = UUID.randomUUID();
        var session = new SettlementWallSession();
        var pick = new SettlementWallRequestPayload(id, 1, Action.SELECT_UPGRADE, UUID.randomUUID(), null,
                SettlementWallRequestPayload.NONE, fixture.base());
        helper.assertValueEqual(session.request(level, id, false, pos -> true, pick, fixture.town()).status(), Status.REJECTED,
                "upgrade selection requires BUILD permission");
        helper.assertValueEqual(session.request(level, id, true, pos -> true, pick, VillageAllegianceId.random()).status(), Status.REJECTED,
                "another settlement cannot select this wall");
        session = new SettlementWallSession();
        var selected = select(helper, fixture, session, id);
        var original = selected.selected();
        var moved = new SettlementWall(original.type(), original.nodes().stream().map(BlockPos::east).toList(), false, 0);
        helper.assertValueEqual(session.request(level, id, true, pos -> true,
                new SettlementWallRequestPayload(id, 2, Action.PREVIEW, UUID.randomUUID(), moved, selected.upgrade(), BlockPos.ZERO), fixture.town()).status(),
                Status.REJECTED, "upgrade cannot move the placed route");
        var changed = new SettlementWall(SettlementWall.Type.TALL_STONE, original.nodes(), false, 0, original.nodeTowers(), original.part());
        var request = new SettlementWallRequestPayload(id, 3, Action.PREVIEW, UUID.randomUUID(), changed, selected.upgrade(), BlockPos.ZERO);
        var preview = session.request(level, id, true, pos -> true, request, fixture.town());
        helper.assertValueEqual(preview.status(), Status.PREVIEW, "valid upgrade previews");
        level.setBlock(fixture.base(), Blocks.DIAMOND_BLOCK.defaultBlockState(), 2);
        var rejected = session.request(level, id, true, pos -> true,
                new SettlementWallRequestPayload(id, 3, Action.CONFIRM, preview.token(), null), fixture.town());
        helper.assertValueEqual(rejected.status(), Status.REJECTED, "manual edits invalidate confirmation");
        helper.assertTrue(level.getBlockState(fixture.base()).is(Blocks.DIAMOND_BLOCK), "manual edits survive");
        helper.assertValueEqual(SettlementWallsSavedData.get(level).get(selected.upgrade()).wall(), original, "rejection leaves saved route unchanged");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "settlement_wall")
    public static void concurrentUpgradesAndCancellationNeverOverwriteNewerWork(GameTestHelper helper) {
        var fixture = placed(helper, 125);
        var level = helper.getLevel();
        var a = new SettlementWallSession(); var b = new SettlementWallSession();
        var aid = UUID.randomUUID(); var bid = UUID.randomUUID();
        var first = select(helper, fixture, a, aid);
        var second = select(helper, fixture, b, bid);
        var changed = new SettlementWall(SettlementWall.Type.TALL_STONE, first.selected().nodes(), false, 0, first.selected().nodeTowers(), first.selected().part());
        var ap = a.request(level, aid, true, pos -> true,
                new SettlementWallRequestPayload(aid, 2, Action.PREVIEW, UUID.randomUUID(), changed, first.upgrade(), BlockPos.ZERO), fixture.town());
        var bp = b.request(level, bid, true, pos -> true,
                new SettlementWallRequestPayload(bid, 2, Action.PREVIEW, UUID.randomUUID(), changed, second.upgrade(), BlockPos.ZERO), fixture.town());
        helper.assertValueEqual(a.request(level, aid, true, pos -> true,
                new SettlementWallRequestPayload(aid, 2, Action.CONFIRM, ap.token(), null), fixture.town()).status(), Status.APPLIED, "first upgrade applies");
        helper.assertValueEqual(b.request(level, bid, true, pos -> true,
                new SettlementWallRequestPayload(bid, 2, Action.CONFIRM, bp.token(), null), fixture.town()).status(), Status.REJECTED, "second upgrade must reselect");
        var cancel = new SettlementWallRequestPayload(bid, 3, Action.CANCEL, UUID.randomUUID(), null);
        helper.assertValueEqual(b.request(level, bid, true, pos -> true, cancel, fixture.town()).status(), Status.CANCELLED, "upgrade can be cancelled");
        helper.assertValueEqual(SettlementWallsSavedData.get(level).get(first.upgrade()).wall(), changed, "cancellation preserves the committed upgrade");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "settlement_wall")
    public static void upgradeSelectionPayloadRoundTrips(GameTestHelper helper) {
        var fixture = placed(helper, 165);
        var id = UUID.randomUUID();
        var result = select(helper, fixture, new SettlementWallSession(), id);
        var request = new SettlementWallRequestPayload(id, 1, Action.SELECT_UPGRADE, UUID.randomUUID(), null,
                SettlementWallRequestPayload.NONE, fixture.base());
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            SettlementWallRequestPayload.STREAM_CODEC.encode(buffer, request);
            helper.assertValueEqual(SettlementWallRequestPayload.STREAM_CODEC.decode(buffer), request, "selection position round trips");
            SettlementWallResultPayload.STREAM_CODEC.encode(buffer, result);
            helper.assertValueEqual(SettlementWallResultPayload.STREAM_CODEC.decode(buffer), result, "selected route and replacement blocks round trip");
        } finally { buffer.release(); }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "settlement_wall")
    public static void damagedSpansDoNotBlockOtherPiecesAndDestroyPreservesReplacements(GameTestHelper helper) {
        var fixture = placed(helper, 200);
        var level = helper.getLevel();
        var data = SettlementWallsSavedData.get(level);
        var tower = data.at(fixture.town(), level.dimension().location(), fixture.base());
        var span = data.at(fixture.town(), level.dimension().location(), fixture.base().east(8));
        var other = data.at(fixture.town(), level.dimension().location(), fixture.base().east(18));
        helper.assertTrue(tower != null && span != null && other != null, "both towers and span have identities");
        helper.assertFalse(tower.id().equals(span.id()) || tower.id().equals(other.id()), "piece identities are separate");
        helper.assertValueEqual(tower.group(), span.group(), "pieces remember their original wall");
        helper.assertValueEqual(span.wall().part(), SettlementWall.Part.SPAN, "between-node blocks select only the span");
        var protectedPos = fixture.base().east(8);
        level.setBlock(protectedPos, Blocks.DIAMOND_BLOCK.defaultBlockState(), 2);
        helper.assertFalse(SettlementWallsSavedData.modification(level, span, pos -> true).isEmpty(), "replacement marks only this span modified");
        var a = new SettlementWallSession(); var id = UUID.randomUUID();
        var selected = select(helper, fixture, a, id);
        var changed = new SettlementWall(SettlementWall.Type.TALL_STONE, tower.wall().nodes(), false, 0, tower.wall().nodeTowers(), tower.wall().part());
        var preview = a.request(level, id, true, pos -> true,
                new SettlementWallRequestPayload(id, 2, Action.PREVIEW, UUID.randomUUID(), changed, selected.upgrade(), BlockPos.ZERO), fixture.town());
        helper.assertValueEqual(preview.status(), Status.PREVIEW, "damaged neighboring span does not block tower upgrade");
        a.request(level, id, true, pos -> true, new SettlementWallRequestPayload(id, 2, Action.CONFIRM, preview.token(), null), fixture.town());
        var destroy = new SettlementWallSession(); var did = UUID.randomUUID();
        var picked = destroy.request(level, did, true, pos -> true,
                new SettlementWallRequestPayload(did, 1, Action.SELECT_DESTROY, UUID.randomUUID(), null,
                        SettlementWallRequestPayload.NONE, protectedPos), fixture.town());
        helper.assertValueEqual(picked.upgrade(), span.id(), "Destroy uses the same piece identity");
        var demolition = destroy.request(level, did, true, pos -> true,
                new SettlementWallRequestPayload(did, 2, Action.PREVIEW_DESTROY, UUID.randomUUID(), null, span.id(), BlockPos.ZERO), fixture.town());
        helper.assertValueEqual(demolition.status(), Status.PREVIEW, "modified pieces can still be safely demolished");
        helper.assertFalse(demolition.cells().stream().anyMatch(cell -> cell.position().equals(protectedPos)), "replacement block is excluded from removal");
        var confirm = new SettlementWallRequestPayload(did, 2, Action.CONFIRM, demolition.token(), null);
        helper.assertValueEqual(destroy.request(level, did, false, pos -> true, confirm, fixture.town()).status(),
                Status.REJECTED, "destroy confirmation requires BUILD permission");
        var laterEdit = demolition.cells().getFirst().position();
        level.setBlock(laterEdit, Blocks.EMERALD_BLOCK.defaultBlockState(), 2);
        var refreshed = destroy.request(level, did, true, pos -> true, confirm, fixture.town());
        helper.assertValueEqual(refreshed.status(), Status.PREVIEW, "terrain changes require another demolition review");
        helper.assertFalse(refreshed.token().equals(demolition.token()), "stale demolition token is replaced");
        helper.assertTrue(level.getBlockState(demolition.cells().getLast().position()).equals(span.blocks().get(demolition.cells().getLast().position())),
                "stale confirmation removes nothing");
        demolition = refreshed;
        confirm = new SettlementWallRequestPayload(did, 2, Action.CONFIRM, demolition.token(), null);
        var applied = destroy.request(level, did, true, pos -> true, confirm, fixture.town());
        helper.assertValueEqual(applied.status(), Status.APPLIED, "confirmed destruction applies");
        for (var cell : demolition.cells()) helper.assertTrue(level.getBlockState(cell.position()).isAir(), "recorded span blocks are removed");
        helper.assertTrue(level.getBlockState(protectedPos).is(Blocks.DIAMOND_BLOCK), "player replacement survives destruction");
        helper.assertTrue(level.getBlockState(laterEdit).is(Blocks.EMERALD_BLOCK), "edits made after the first preview also survive");
        helper.assertTrue(data.get(span.id()) == null, "destroyed piece identity is removed");
        helper.assertTrue(data.get(other.id()) != null && level.getBlockState(fixture.base().east(18)).equals(other.blocks().get(fixture.base().east(18))),
                "neighboring tower remains unchanged");
        helper.assertValueEqual(destroy.request(level, did, true, pos -> true, confirm, fixture.town()), applied, "destroy confirmation is idempotent");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "settlement_wall")
    public static void minorHolesRepairButSevereStructuralDamageBlocksUpgrade(GameTestHelper helper) {
        var fixture = placed(helper, 245);
        var level = helper.getLevel();
        var data = SettlementWallsSavedData.get(level);
        var span = data.at(fixture.town(), level.dimension().location(), fixture.base().east(8));
        var shape = SettlementWallGeometry.build(span.wall()).cells().stream().map(SettlementWallTemplates.Cell::position)
                .filter(span.blocks()::containsKey).toList();
        var hole = shape.getFirst();
        level.setBlock(hole, Blocks.AIR.defaultBlockState(), 2);
        helper.assertTrue(SettlementWallsSavedData.modification(level, span, pos -> true).isEmpty(), "a small hole is repairable");
        var replacement = new SettlementWall(SettlementWall.Type.TIMBER, span.wall().nodes(), false, 0, span.wall().nodeTowers(), span.wall().part());
        var preview = SettlementWallTerrain.preview(level, replacement, pos -> true, span.blocks());
        helper.assertTrue(preview.error().isEmpty(), "minor damage allows replacement preview: " + preview.error());
        for (int i = 0; i <= shape.size() / 5; i++) level.setBlock(shape.get(i), Blocks.AIR.defaultBlockState(), 2);
        helper.assertFalse(SettlementWallsSavedData.modification(level, span, pos -> true).isEmpty(), "more than 20 percent missing structural blocks is severe");
        var session = new SettlementWallSession(); var id = UUID.randomUUID();
        var selected = session.request(level, id, true, pos -> true,
                new SettlementWallRequestPayload(id, 1, Action.SELECT_UPGRADE, UUID.randomUUID(), null, SettlementWallRequestPayload.NONE, fixture.base().east(8)), fixture.town());
        helper.assertValueEqual(session.request(level, id, true, pos -> true,
                new SettlementWallRequestPayload(id, 2, Action.PREVIEW, UUID.randomUUID(), replacement, selected.upgrade(), BlockPos.ZERO), fixture.town()).status(),
                Status.REJECTED, "server rejects heavily damaged piece upgrades");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "settlement_wall")
    public static void everyPlacedBlockBelongsToOnePersistentPiece(GameTestHelper helper) {
        var base = helper.absolutePos(new BlockPos(5, 45, 5));
        var nodes = List.of(base, base.east(24).south(8), base.east(16).south(24), base.south(24));
        for (var type : SettlementWall.Type.values()) for (int spacing : new int[]{0, 8}) for (boolean towers : new boolean[]{true, false}) {
            var wall = new SettlementWall(type, nodes, true, spacing,
                    java.util.Collections.nCopies(nodes.size(), towers ? SettlementWall.Tower.DEFAULT : SettlementWall.Tower.NONE));
            var plan = SettlementWallGeometry.build(wall);
            helper.assertTrue(plan.error().isEmpty(), "loop geometry is valid");
            var cells = new java.util.LinkedHashMap<BlockPos, net.minecraft.world.level.block.state.BlockState>();
            for (var cell : plan.cells()) if (!cell.state().isAir()) cells.put(cell.position(), cell.state());
            for (var pos : plan.foundations()) {
                cells.put(pos.below(), cells.get(pos));
                cells.put(pos.below(2), cells.get(pos));
            }
            var preview = new SettlementWallTerrain.Preview(cells.entrySet().stream().map(entry -> new SettlementWallTerrain.Cell(
                    entry.getKey(), entry.getValue(), Blocks.AIR.defaultBlockState(), null, SettlementWallTerrain.Kind.PLACE)).toList(), "");
            var data = new SettlementWallsSavedData();
            var group = UUID.randomUUID(); var town = VillageAllegianceId.random();
            data.record(group, town, helper.getLevel(), wall, preview);
            var restored = SettlementWallsSavedData.load(data.save(new CompoundTag(), helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
            cells.forEach((pos, state) -> {
                var entry = restored.at(town, helper.getLevel().dimension().location(), pos);
                helper.assertTrue(entry != null, "all blocks and foundations remain selectable after reload: " + pos);
                helper.assertValueEqual(entry.blocks().get(pos), state, "piece owns its placed state");
                helper.assertValueEqual(entry.group(), group, "pieces retain the original group");
            });
        }
        helper.succeed();
    }

    private static SettlementWallResultPayload select(GameTestHelper helper, Fixture fixture, SettlementWallSession session, UUID id) {
        return session.request(helper.getLevel(), id, true, pos -> true,
                new SettlementWallRequestPayload(id, 1, Action.SELECT_UPGRADE, UUID.randomUUID(), null,
                        SettlementWallRequestPayload.NONE, fixture.base()), fixture.town());
    }

    private static Fixture placed(GameTestHelper helper, int height) {
        var level = helper.getLevel();
        var base = helper.absolutePos(new BlockPos(5, height, 5));
        for (var pos : BlockPos.betweenClosed(base.offset(-4, -4, -6), base.offset(23, 14, 6))) {
            level.setBlock(pos, pos.getY() <= base.getY() - 3 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
        }
        var wall = new SettlementWall(SettlementWall.Type.LOW_STONE, List.of(base, base.east(18)), false, 0);
        var session = new SettlementWallSession();
        var id = UUID.randomUUID();
        var record = SettlementWallMaterialsGameTests.town(level, base);
        SettlementWallMaterialsGameTests.supply(level, record, base.north(8));
        var town = record.id();
        var preview = session.request(level, id, true, pos -> true,
                new SettlementWallRequestPayload(id, 1, Action.PREVIEW, UUID.randomUUID(), wall), town);
        helper.assertValueEqual(preview.status(), Status.PREVIEW, "source wall previews: " + preview.message());
        var applied = session.request(level, id, true, pos -> true,
                new SettlementWallRequestPayload(id, 1, Action.CONFIRM, preview.token(), null), town);
        helper.assertValueEqual(applied.status(), Status.APPLIED, "source wall is recorded");
        return new Fixture(town, base);
    }
    private record Fixture(VillageAllegianceId town, BlockPos base) {}
}
