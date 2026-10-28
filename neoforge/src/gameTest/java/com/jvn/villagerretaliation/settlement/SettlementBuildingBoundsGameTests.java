package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderStructureScanner;
import com.jvn.villagerretaliation.network.SettlementBuildingPreviewRequestPayload;
import com.jvn.villagerretaliation.sell.CurrencyAmount;
import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("settlement_building_bounds")
@PrefixGameTestTemplate(false)
public final class SettlementBuildingBoundsGameTests {
    private static final ResourceLocation FARM = ResourceLocation.withDefaultNamespace("village/plains/houses/plains_large_farm_1");

    private SettlementBuildingBoundsGameTests() { }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void largeFarmFitsEveryOwnedEdgeInEveryRotationWithoutFundsOrMaterials(GameTestHelper helper) {
        var fixture = fixture(helper);
        var chunk = fixture.town().territory().foundingChunk();
        int west = (chunk.x - 1) << 4;
        int east = ((chunk.x + 2) << 4) - 1;
        int north = (chunk.z - 1) << 4;
        int south = ((chunk.z + 2) << 4) - 1;
        for (var rotation : Rotation.values()) {
            var plan = SettlementConstructionService.scan(helper.getLevel(), FARM, rotation);
            int width = plan.localMax().getX() - plan.localMin().getX();
            int depth = plan.localMax().getZ() - plan.localMin().getZ();
            for (var min : List.of(new BlockPos(west, fixture.town().center().getY(), north),
                    new BlockPos(east - width, fixture.town().center().getY(), north),
                    new BlockPos(west, fixture.town().center().getY(), south - depth),
                    new BlockPos(east - width, fixture.town().center().getY(), south - depth))) {
                var origin = min.subtract(plan.localMin());
                load(helper, plan.worldMin(origin), plan.worldMax(origin));
                helper.assertTrue(fixture.town().contains(plan.worldMin(origin), plan.worldMax(origin)), "the visible farm footprint is entirely owned");
                var workplace = SettlementConstructionService.workplace(helper.getLevel(), fixture.town(), plan, origin, UUID.randomUUID());
                helper.assertTrue(fixture.town().owns(workplace), "optional work margins remain inside the owned corner: " + rotation);
                helper.assertTrue(workplace.contains(plan.worldMin(origin)) && workplace.contains(plan.worldMax(origin)), "clipping never cuts into the building");
                var preview = SettlementBuildingPlanner.preview(fixture.player(), new SettlementBuildingPreviewRequestPayload(
                        fixture.town().id().value(), 1, FARM, anchor(plan, origin), rotation, SettlementProject.AUTOMATIC_BUILDER));
                helper.assertValueEqual(preview.failure(), "", "a farm inside the plot border can be queued: " + rotation);
                helper.assertValueEqual(preview.funds(), CurrencyAmount.ZERO, "an empty treasury does not reject placement");
                helper.assertTrue(preview.materials().stream().anyMatch(material -> material.available() < material.required()), "missing materials do not reject placement");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void edgeRequestsStayQueuedAndOwnedAcrossReload(GameTestHelper helper) {
        var fixture = fixture(helper);
        var chunk = fixture.town().territory().foundingChunk();
        var plan = SettlementConstructionService.scan(helper.getLevel(), FARM, Rotation.NONE);
        var min = new BlockPos((chunk.x - 1) << 4, fixture.town().center().getY(), (chunk.z - 1) << 4);
        var origin = min.subtract(plan.localMin());
        load(helper, plan.worldMin(origin), plan.worldMax(origin));
        helper.getLevel().setBlockAndUpdate(plan.worldMin(origin), net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState());
        helper.assertValueEqual(SettlementConstructionService.placementFailure(fixture.player(), fixture.town(), plan, origin),
                "blocked_existing", "edge placement still protects existing containers");
        for (var position : BlockPos.betweenClosed(plan.worldMin(origin), plan.worldMax(origin)))
            helper.getLevel().setBlock(position, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
        var id = UUID.randomUUID();
        helper.assertValueEqual(SettlementConstructionService.commission(fixture.player(), fixture.town(), id, FARM,
                anchor(plan, origin), Rotation.NONE, SettlementProject.AUTOMATIC_BUILDER), "", "an edge building enters the queue without a builder or supplies");
        var project = fixture.town().projects().get(id);
        helper.assertTrue(project.pending() && project.blocker().equals("builder"), "the queued farm waits for a builder");
        helper.assertTrue(fixture.town().owns(project), "the project and its working margin are owned");
        var restored = SettlementSavedData.load(SettlementSavedData.get(helper.getLevel()).save(new CompoundTag(), helper.getLevel().registryAccess()),
                helper.getLevel().registryAccess()).get(fixture.town().id()).orElseThrow();
        helper.assertTrue(restored.owns(restored.projects().get(id)), "the clipped worksite remains owned after reload");
        helper.assertValueEqual(restored.economy().balance(), CurrencyAmount.ZERO, "queueing never spends unavailable funds");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void realFootprintsStillRejectUnownedPlotsAndTerritoryHoles(GameTestHelper helper) {
        var fixture = fixture(helper);
        var town = fixture.town();
        var chunk = town.territory().foundingChunk();
        var plan = SettlementConstructionService.scan(helper.getLevel(), FARM, Rotation.NONE);
        var min = new BlockPos(((chunk.x - 1) << 4) - 1, town.center().getY(), (chunk.z - 1) << 4);
        helper.assertValueEqual(SettlementConstructionService.placementFailure(fixture.player(), town, plan, min.subtract(plan.localMin())),
                "bounds", "even one building block outside owned plots is rejected");
        town.territory().claim(chunk.x + 2, chunk.z + 1, true);
        SettlementSavedData.get(helper.getLevel()).claimed(VillageAllegianceRegistrySavedData.get(helper.getLevel()), town);
        min = new BlockPos(((chunk.x + 2) << 4) - (plan.localMax().getX() - plan.localMin().getX()) / 2,
                town.center().getY(), ((chunk.z + 1) << 4) - (plan.localMax().getZ() - plan.localMin().getZ()) / 2);
        var origin = min.subtract(plan.localMin());
        helper.assertTrue(town.contains(plan.worldMin(origin)) && town.contains(plan.worldMax(origin)), "opposite corners can be owned while the footprint crosses a hole");
        helper.assertFalse(town.contains(plan.worldMin(origin), plan.worldMax(origin)), "the footprint includes an unowned plot");
        helper.assertValueEqual(SettlementConstructionService.placementFailure(fixture.player(), town, plan, origin), "bounds", "an unowned interior plot still rejects the building");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void interiorSitesKeepWorkingRoomAndHeightLimitsRemainEnforced(GameTestHelper helper) {
        var fixture = fixture(helper);
        var level = helper.getLevel();
        var plan = SettlementConstructionService.scan(level, FARM, Rotation.NONE);
        var origin = fixture.town().center().subtract(plan.localMin());
        var min = plan.worldMin(origin);
        var max = plan.worldMax(origin);
        var workplace = SettlementConstructionService.workplace(level, fixture.town(), plan, origin, UUID.randomUUID());
        helper.assertValueEqual(workplace.min(), min.offset(-4, -2, -4), "interior sites keep their normal lower margin");
        helper.assertValueEqual(workplace.max(), max.offset(4, 2, 4), "interior sites keep their normal upper margin");
        var lowOrigin = origin.atY(level.getMinBuildHeight() - plan.localMin().getY());
        workplace = SettlementConstructionService.workplace(level, fixture.town(), plan, lowOrigin, UUID.randomUUID());
        helper.assertValueEqual(workplace.min().getY(), level.getMinBuildHeight(), "optional lower space clips to world height");
        helper.assertValueEqual(SettlementConstructionService.placementFailure(fixture.player(), fixture.town(), plan, lowOrigin.below()), "bounds", "the actual footprint cannot extend below world height");
        var highOrigin = origin.atY(level.getMaxBuildHeight() - 1 - plan.localMax().getY());
        workplace = SettlementConstructionService.workplace(level, fixture.town(), plan, highOrigin, UUID.randomUUID());
        helper.assertValueEqual(workplace.max().getY(), level.getMaxBuildHeight() - 1, "optional upper space clips to world height");
        helper.assertValueEqual(SettlementConstructionService.placementFailure(fixture.player(), fixture.town(), plan, highOrigin.above()), "bounds", "the actual footprint cannot extend above world height");
        helper.succeed();
    }

    private static Fixture fixture(GameTestHelper helper) {
        var level = helper.getLevel();
        var center = helper.absolutePos(new BlockPos(2, 4, 2));
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "BuildingBounds"));
        player.setPos(center.getCenter());
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var id = registry.create(level.getGameTime(), level.dimension().location(), center, "");
        var town = SettlementSavedData.get(level).establish(registry, id, player.getUUID(), center, level.getGameTime()).orElseThrow();
        town.advance();
        return new Fixture(town, player);
    }

    private static BlockPos anchor(BuilderStructureScanner.StructurePlan plan, BlockPos origin) {
        return origin.offset(plan.localCenter().getX(), plan.localMin().getY(), plan.localCenter().getZ());
    }

    private static void load(GameTestHelper helper, BlockPos min, BlockPos max) {
        for (int x = min.getX() >> 4; x <= max.getX() >> 4; x++) {
            for (int z = min.getZ() >> 4; z <= max.getZ() >> 4; z++) helper.getLevel().getChunk(x, z);
        }
    }

    private record Fixture(SettlementRecord town, ServerPlayer player) { }
}
