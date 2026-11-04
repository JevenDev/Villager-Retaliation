package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.network.SettlementBuildingPreviewPayload;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("vr_building_alignment")
@PrefixGameTestTemplate(false)
public final class SettlementBuildingAlignmentGameTests {
    private SettlementBuildingAlignmentGameTests() {}

    @GameTest(template = "empty")
    public static void streetAlignmentFacesRoadAndKeepsEntranceSetback(GameTestHelper helper) {
        var f = SettlementConstructionGameTests.fixture(helper);
        try {
            f.town().advance();
            var anchor = f.origin().offset(f.plan().localCenter().getX(), f.plan().localMin().getY(), f.plan().localCenter().getZ());
            var preview = SettlementBuildingPlanner.preview(f.leader(), new com.jvn.villagerretaliation.network.SettlementBuildingPreviewRequestPayload(
                    f.town().id().value(), 1, f.plan().entry().id(), anchor, Rotation.NONE, f.builder().getUUID()));
            var road = anchor.offset(12, -1, 0);
            var result = SettlementBuildingAlignment.suggest(preview, anchor, SettlementBuildingAlignment.Mode.STREET, 2, List.of(road), List.of());
            var rotation = Rotation.values()[result.turns()];
            var turned = com.jvn.villagerretaliation.interaction.work.builder.BuilderStructureScanner.scan(helper.getLevel(), f.plan().entry(), rotation).orElseThrow();
            var origin = SettlementConstructionService.origin(turned, result.anchor());
            var after = SettlementBuildingPlanner.preview(f.leader(), new com.jvn.villagerretaliation.network.SettlementBuildingPreviewRequestPayload(
                    f.town().id().value(), 2, f.plan().entry().id(), result.anchor(), rotation, f.builder().getUUID()));
            helper.assertFalse(after.entrances().isEmpty(), "aligned building retains an entrance");
            helper.assertTrue(after.entrances().stream().anyMatch(pos -> pos.getX() < road.getX() && Math.abs(pos.getZ() - road.getZ()) <= 1), "entrance faces the street");
            helper.assertValueEqual(SettlementBuildingAlignment.suggest(preview, anchor, SettlementBuildingAlignment.Mode.FREE, 2, List.of(road), List.of()).anchor(), anchor, "free placement remains exact");
        } finally { f.builder().discard(); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void plotSnappingHandlesNegativeCoordinatesAndMissingRoads(GameTestHelper helper) {
        var anchor = new BlockPos(-19, 70, -18);
        var preview = new SettlementBuildingPreviewPayload(java.util.UUID.randomUUID(), 1, net.minecraft.resources.ResourceLocation.parse("test:house"), anchor,
                Rotation.NONE, anchor, anchor, anchor.offset(4, 3, 4), 0, com.jvn.villagerretaliation.sell.CurrencyAmount.ZERO, "",
                List.of(new SettlementBuildingPreviewPayload.PreviewBlock(BlockPos.ZERO, Blocks.STONE.defaultBlockState())), List.of(), List.of());
        var snap = SettlementBuildingAlignment.suggest(preview, anchor, SettlementBuildingAlignment.Mode.PLOTS, 2, List.of(), List.of());
        helper.assertValueEqual(Math.floorMod(snap.anchor().getX(), 16), 2, "plot setback is correct west of zero");
        helper.assertValueEqual(Math.floorMod(snap.anchor().getZ(), 16), 2, "plot setback is correct north of zero");
        helper.assertValueEqual(SettlementBuildingAlignment.suggest(preview, anchor, SettlementBuildingAlignment.Mode.STREET, 2, List.of(), List.of()).anchor(), anchor, "missing roads preserve placement");
        helper.succeed();
    }
}
