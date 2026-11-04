package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.HiredWorkStateStore;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderStructureScanner;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderWorker;
import com.jvn.villagerretaliation.network.SettlementBuildingPreviewPayload;
import com.jvn.villagerretaliation.network.SettlementBuildingPreviewRequestPayload;
import io.netty.buffer.Unpooled;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("settlement_building_preparation")
@PrefixGameTestTemplate(false)
public final class SettlementBuildingPreparationGameTests {
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void foundationQuotePersistsAndUsesRealMaterials(GameTestHelper helper) {
        var f = SettlementConstructionGameTests.fixture(helper);
        try {
            f.town().advance();
            ground(helper, f, 2);
            var options = new SettlementTerrainOptions(SettlementTerrainOptions.Mode.FOUNDATION, 2);
            var preview = SettlementBuildingPlanner.preview(f.leader(), request(f, options));
            helper.assertTrue(preview.failure().isEmpty(), "foundation has a valid quoted placement: " + preview.failure());
            helper.assertFalse(preview.terrain().isEmpty(), "foundation changes are explicit");
            var first = preview.terrain().getFirst();
            helper.assertTrue(helper.getLevel().getBlockState(first.position()).isAir(), "preview does not fill the world");
            helper.assertTrue(preview.materials().stream().anyMatch(material -> material.item().is(net.minecraft.world.item.Items.COBBLESTONE)), "foundation quotes cobblestone");
            var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
            try {
                SettlementBuildingPreviewPayload.STREAM_CODEC.encode(buffer, preview);
                var decoded = SettlementBuildingPreviewPayload.STREAM_CODEC.decode(buffer);
                helper.assertValueEqual(decoded.terrain(), preview.terrain(), "terrain changes round trip");
                helper.assertValueEqual(decoded.diagnostics(), preview.diagnostics(), "placement diagnostics round trip");
                helper.assertValueEqual(decoded.preparation(), options, "preparation options round trip");
                helper.assertValueEqual(decoded.fingerprint(), preview.fingerprint(), "approved plan fingerprint round trips");
            } finally { buffer.release(); }
            var id = UUID.randomUUID();
            helper.assertTrue(SettlementConstructionService.commission(f.leader(), f.town(), id, preview.structure(), preview.anchor(),
                    preview.rotation(), f.builder().getUUID(), options, preview.fingerprint()).isEmpty(), "reviewed foundation queues");
            var project = f.town().projects().get(id);
            var plan = SettlementConstructionService.scan(helper.getLevel(), project);
            helper.assertTrue(plan == SettlementConstructionService.scan(helper.getLevel(), project), "prepared plans retain scanner caching");
            helper.assertFalse(plan.outdoor(), "prepared houses retain indoor construction behavior");
            int per64 = com.jvn.villagerretaliation.config.VillagerRetaliationConfig.HIRED_BUILDER_EMERALDS_PER_64_BLOCKS.get();
            helper.assertValueEqual(plan.price(), f.plan().price() + (int) Math.ceil(plan.blocks().size() / 64.0 * per64)
                    - (int) Math.ceil(f.plan().blocks().size() / 64.0 * per64), "terrain labor uses the existing per-block pricing rule");
            var restored = SettlementProject.load(project.save());
            helper.assertValueEqual(BuilderStructureScanner.fingerprint(SettlementConstructionService.scan(helper.getLevel(), restored)),
                    preview.fingerprint(), "approved terrain survives project reload");
            SettlementConstructionGameTests.fundAndDispatch(helper, f.town(), plan, project.origin(), id);
            SettlementConstructionMaterials.collect(f.town(), project, f.builder().getUUID(), SettlementConstructionMaterials.collectionTarget(project));
            SettlementConstructionService.reconcile(f.builder());
            helper.assertTrue(project.configureCrew(project.crewRevision(), 5, java.util.List.of()), "a prepared building supports crews");
            var state = HiredWorkStateStore.state(f.builder());
            int next = SettlementConstructionService.selectPlacement(f.builder(), state, plan, 0);
            helper.assertTrue(next >= 0 && plan.blocks().get(next).localPos().getY() < f.plan().localMin().getY(),
                    "crews choose preparation before structure blocks");
            var block = plan.blocks().get(next);
            var group = BuilderWorker.placementGroup(plan, project.origin(), block);
            for (int retry = 0; retry < 32 && !project.site().observed(group.materialPart().worldPos()); retry++)
                SettlementConstructionSiteService.observeArea(helper.getLevel(), f.town(), f.town().workplace(project.area()), null);
            helper.assertTrue(project.site().observed(group.materialPart().worldPos()), "the bounded site survey reaches the claimed target");
            helper.assertTrue(SettlementConstructionMaterials.place(f.builder(), HiredWorkStateStore.state(f.builder()), group), "native construction spends reserved foundation supplies: " + project.blocker()
                    + " / collection " + SettlementConstructionMaterials.collectionTarget(project)
                    + " / observed " + project.site().observed(group.materialPart().worldPos()));
            helper.assertTrue(helper.getLevel().getBlockState(plan.worldPos(project.origin(), block)).is(Blocks.COBBLESTONE), "foundation is actually built");
            helper.assertTrue(SettlementConstructionService.pause(f.leader(), f.town(), id, true), "prepared construction pauses");
            helper.assertFalse(SettlementConstructionMaterials.place(f.builder(), HiredWorkStateStore.state(f.builder()),
                    BuilderWorker.placementGroup(plan, project.origin(), plan.blocks().get(1))), "paused projects reject direct placement");
        } finally { f.builder().discard(); }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void changedTerrainAndDeepHolesCannotSilentlyChangeAnApprovedQuote(GameTestHelper helper) {
        var f = SettlementConstructionGameTests.fixture(helper);
        try {
            f.town().advance();
            ground(helper, f, 2);
            var options = new SettlementTerrainOptions(SettlementTerrainOptions.Mode.FILL, 2);
            var preview = SettlementBuildingPlanner.preview(f.leader(), request(f, options));
            helper.assertTrue(preview.failure().isEmpty(), "shallow holes can be filled");
            var changed = preview.terrain().getFirst().position();
            helper.getLevel().setBlock(changed, Blocks.STONE.defaultBlockState(), 2);
            var funds = f.town().economy().balance();
            helper.assertValueEqual(SettlementConstructionService.commission(f.leader(), f.town(), UUID.randomUUID(), preview.structure(), preview.anchor(),
                    preview.rotation(), f.builder().getUUID(), options, preview.fingerprint()), "terrain_changed", "changed fill requires review");
            helper.assertValueEqual(f.town().economy().balance(), funds, "rejected quotes consume no payment");
            helper.assertTrue(f.town().projects().isEmpty(), "rejected quotes create no project");
            helper.getLevel().setBlock(changed, Blocks.CHEST.defaultBlockState(), 2);
            var blocked = SettlementBuildingPlanner.preview(f.leader(), request(f, options));
            helper.assertValueEqual(blocked.failure(), "terrain_unsafe", "existing containers remain protected");
            helper.assertTrue(blocked.diagnostics().stream().anyMatch(d -> d.position().equals(changed) && d.blocking()), "the unsafe cell has a diagnostic");
            helper.getLevel().setBlock(changed, Blocks.AIR.defaultBlockState(), 2);
            ground(helper, f, 4);
            helper.assertValueEqual(SettlementBuildingPlanner.preview(f.leader(), request(f, options)).failure(), "terrain_depth", "fill cannot bridge a deep hole");
        } finally { f.builder().discard(); }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void levelingIsBoundedAndPreviewExplainsDistinctObstructions(GameTestHelper helper) {
        var f = SettlementConstructionGameTests.fixture(helper);
        try {
            f.town().advance();
            var bed = f.plan().blocks().stream().filter(block -> block.state().getBlock() instanceof net.minecraft.world.level.block.BedBlock)
                    .findFirst().orElseThrow();
            var bedPos = f.plan().worldPos(f.origin(), bed);
            helper.getLevel().setBlock(bedPos, Blocks.DIRT.defaultBlockState(), 2);
            var bedPreview = SettlementBuildingPlanner.preview(f.leader(), request(f,
                    new SettlementTerrainOptions(SettlementTerrainOptions.Mode.LEVEL, 4)));
            helper.assertTrue(bedPreview.failure().isEmpty(), "leveling can prepare an authored bed position: " + bedPreview.failure());
            helper.assertTrue(bedPreview.terrain().stream().anyMatch(cell -> cell.position().equals(bedPos) && cell.after().equals(bed.state())),
                    "preparation retains authored block entities instead of rejecting them");
            helper.getLevel().setBlock(bedPos, Blocks.AIR.defaultBlockState(), 2);
            var local = f.plan().localMin();
            var pos = f.origin().offset(local);
            helper.getLevel().setBlock(pos, Blocks.DIRT.defaultBlockState(), 2);
            var options = new SettlementTerrainOptions(SettlementTerrainOptions.Mode.LEVEL, 2);
            var preview = SettlementBuildingPlanner.preview(f.leader(), request(f, options));
            helper.assertTrue(preview.terrain().stream().anyMatch(cell -> cell.position().equals(pos) && cell.before().is(Blocks.DIRT)), "leveling shows every approved earth change");
            helper.assertTrue(helper.getLevel().getBlockState(pos).is(Blocks.DIRT), "leveling preview is read only");
            helper.getLevel().setBlock(pos.above(2), Blocks.DIRT.defaultBlockState(), 2);
            helper.assertValueEqual(SettlementBuildingPlanner.preview(f.leader(), request(f, options)).failure(), "terrain_depth", "excavation depth is enforced");
            helper.getLevel().setBlock(pos.above(2), Blocks.AIR.defaultBlockState(), 2);
            helper.getLevel().setBlock(pos, Blocks.CHEST.defaultBlockState(), 2);
            var obstruction = SettlementBuildingPlanner.preview(f.leader(), request(f, SettlementTerrainOptions.NONE));
            helper.assertTrue(obstruction.diagnostics().stream().anyMatch(d -> d.position().equals(pos) && d.reason().equals("container")), "container obstruction has its own reason");
            helper.getLevel().setBlock(pos, Blocks.BEDROCK.defaultBlockState(), 2);
            var solid = SettlementBuildingPlanner.preview(f.leader(), request(f, SettlementTerrainOptions.NONE));
            helper.assertTrue(solid.diagnostics().stream().anyMatch(d -> d.position().equals(pos) && d.reason().equals("blocked_existing")), "unsafe blocks have their own reason");
        } finally { f.builder().discard(); }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void foundationsAddExactSuppliesToSimplifiedPrefabBills(GameTestHelper helper) {
        var f = SettlementConstructionGameTests.fixture(helper);
        try {
            ground(helper, f, 2);
            var base = f.plan();
            var cost = new com.jvn.villagerretaliation.interaction.work.builder.BuilderConstructionCost(
                    com.jvn.villagerretaliation.interaction.work.builder.BuilderConstructionCost.Mode.SIMPLIFIED,
                    java.util.List.of(new com.jvn.villagerretaliation.interaction.work.builder.BuilderConstructionCost.Ingredient(
                            net.minecraft.resources.ResourceLocation.withDefaultNamespace("cobblestone"), false, 10)));
            var entry = new com.jvn.villagerretaliation.interaction.work.builder.BuilderStructureCatalog.Entry(base.entry().id(),
                    base.entry().category(), base.entry().label(), 0, cost, base.entry().houseSize());
            var source = new BuilderStructureScanner.StructurePlan(entry, base.rotation(), base.templateSize(), base.localMin(), base.localMax(),
                    base.blocks(), base.materials(), base.price(), base.clearingBlocks());
            var preparation = SettlementBuildingPreparation.preview(f.leader(), f.town(), source, f.origin(),
                    new SettlementTerrainOptions(SettlementTerrainOptions.Mode.FOUNDATION, 2));
            helper.assertTrue(preparation.failure().isEmpty(), "simplified prefabs support foundations");
            var plan = preparation.preparation().apply(source);
            var requirements = com.jvn.villagerretaliation.interaction.work.builder.BuilderConstructionCost.requirements(plan);
            helper.assertValueEqual(requirements.size(), 1, "matching foundation ingredients merge with the existing bill");
            helper.assertValueEqual(requirements.getFirst().count(), 10 + preparation.preparation().cells().size(),
                    "foundation supplies are added exactly to the authored bill");
        } finally { f.builder().discard(); }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void existingBuildingPreviewAndCommissionPacketsRemainCompatible(GameTestHelper helper) {
        SettlementBuildingPlanningGameTests.previewAndCommissionPacketsAreReadOnlyBoundedAndCorrelated(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void existingBuildingAuthorityAndBoundsRemainEnforced(GameTestHelper helper) {
        SettlementBuildingPlanningGameTests.directAuthorityBoundsOverlapCapacityAndWorkingDutiesAreEnforced(helper);
    }

    private static SettlementBuildingPreviewRequestPayload request(SettlementConstructionGameTests.Fixture f, SettlementTerrainOptions options) {
        var anchor = f.origin().offset(f.plan().localCenter().getX(), f.plan().localMin().getY(), f.plan().localCenter().getZ());
        return new SettlementBuildingPreviewRequestPayload(f.town().id().value(), 1, f.plan().entry().id(), anchor, Rotation.NONE, null, options);
    }

    private static void ground(GameTestHelper helper, SettlementConstructionGameTests.Fixture f, int depth) {
        var min = f.plan().worldMin(f.origin());
        var max = f.plan().worldMax(f.origin());
        for (int x = min.getX(); x <= max.getX(); x++) for (int z = min.getZ(); z <= max.getZ(); z++) {
            for (int y = min.getY() - depth; y < min.getY(); y++)
                helper.getLevel().setBlock(new BlockPos(x, y, z), y == min.getY() - depth ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
        }
    }
}
