package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.AllegianceAssignmentSource;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceApi;
import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.HiredWorkSession;
import com.jvn.villagerretaliation.interaction.HiredWorkStateStore;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderWorker;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.jvn.villagerretaliation.network.SettlementPathRequestPayload;
import com.jvn.villagerretaliation.network.SettlementPathResultPayload;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("settlement_mixed_construction")
@PrefixGameTestTemplate(false)
public final class SettlementMixedConstructionGameTests {
    @GameTest(template = "empty", timeoutTicks = 2400, batch = "mixed_construction")
    public static void housePathFarmAndClosingWallShareOneSettlement(GameTestHelper helper) {
        var f = SettlementConstructionOrderGameTests.fixture(helper);
        var level = helper.getLevel();
        for (var pos : BlockPos.betweenClosed(f.area().min().atY(f.origin().getY()), f.area().max().above(36)))
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
        helper.assertTrue(f.town().putArea(f.leader().getUUID(), new SettlementArea(f.area().id(), SettlementAreaType.CONSTRUCTION,
                "Builders", f.origin().offset(-4, -1, -4), f.origin().offset(0, 2, 3), 5, true)), "builders have a separate base");
        for (var pos : BlockPos.betweenClosed(f.area().min().below(2), f.area().max().atY(f.origin().getY() - 1)))
            level.setBlock(pos, Blocks.DIRT.defaultBlockState(), 2);
        var assistant = SettlementConstructionCrewGameTests.assistant(helper, f);
        SettlementConstructionCrewGameTests.adopt(helper, f);
        var farmer = farmer(helper, f);
        var farm = new SettlementFarm(f.origin().offset(14, -1, 14), f.origin().offset(17, -1, 17));
        var field = SettlementFarmTerrain.preview(level, farm, f.town()::contains);
        helper.assertTrue(field.error().isEmpty(), "field preview is valid: " + field.error());
        helper.assertFalse(SettlementFarmWork.start(f.leader(), f.town(), field).isEmpty(), "farmland waits for irrigation supplies");
        var inventory = HiredJobInventory.getJobInventory(farmer);
        inventory.setItem(inventory.supplySlots().getFirst(), new ItemStack(Items.WATER_BUCKET));
        helper.assertTrue(SettlementFarmWork.start(f.leader(), f.town(), field).isEmpty(), "farmer receives the supplied field order");
        var farmerState = HiredWorkStateStore.state(farmer).copy();
        helper.assertTrue(SettlementFarmWork.next(level, farmerState).water(), "the saved field order resumes at irrigation");

        var path = new SettlementPath(List.of(new SettlementPath.Node(Vec3.atCenterOf(f.origin().offset(1, -1, 10))),
                new SettlementPath.Node(Vec3.atCenterOf(f.origin().offset(8, -1, 10)))), 1, SettlementPath.Style.CLEAN, SettlementPath.Material.DIRT, 1L, false);
        var paths = new SettlementPathSession();
        var session = UUID.randomUUID();
        var review = paths.request(level, session, true, f.town()::contains,
                new SettlementPathRequestPayload(session, 1, SettlementPathRequestPayload.Action.PREVIEW, session, path));
        helper.assertValueEqual(review.status(), SettlementPathResultPayload.Status.PREVIEW, "path preview is valid");
        var applied = paths.request(level, session, true, f.town()::contains,
                new SettlementPathRequestPayload(session, 1, SettlementPathRequestPayload.Action.CONFIRM, review.token(), null));
        helper.assertValueEqual(applied.status(), SettlementPathResultPayload.Status.APPLIED, "the path uses its existing direct construction flow");

        var houseId = UUID.randomUUID();
        var anchor = f.origin().offset(f.plan().localCenter().getX(), f.plan().localMin().getY(), f.plan().localCenter().getZ());
        helper.assertTrue(SettlementConstructionService.commission(f.leader(), f.town(), houseId, f.plan().entry().id(), anchor,
                Rotation.NONE, SettlementProject.AUTOMATIC_BUILDER).isEmpty(), "house queued");
        var first = f.origin().offset(10, 0, 4);
        var wall = new SettlementWall(SettlementWall.Type.PALISADE,
                List.of(first, first.east(8), first.east(8).south(8), first.south(8)), true, 0);
        var wallId = UUID.randomUUID();
        helper.assertTrue(SettlementWallConstruction.commission(f.leader(), f.town(), wallId, wall,
                SettlementWallTerrain.preview(level, wall, f.town()::contains)).isEmpty(), "closed wall queues behind the house");
        var house = f.town().projects().get(houseId);
        var project = f.town().projects().get(wallId);
        helper.assertTrue(house.pending() && project.pending(), "both projects retain their queue positions during a supply shortage");
        SettlementConstructionGameTests.fundAndDispatch(helper, f.town(), f.plan(), f.origin(), houseId);
        helper.assertTrue(project.pending(), "Hamlet capacity leaves the wall queued while the house is active");
        helper.assertTrue(SettlementConstructionService.configureCrew(f.leader(), f.town(), houseId, house.crewRevision(), 5,
                List.of(assistant.getUUID())), "house uses the shared builder crew");
        var restored = SettlementRecord.load(f.town().save(level.registryAccess()), level.registryAccess(), () -> {});
        helper.assertTrue(restored.projects().get(houseId).active() && restored.projects().get(wallId).pending(),
                "reload preserves the active house and queued wall");
        helper.assertTrue(restored.projects().get(houseId).assistants().contains(assistant.getUUID()), "reload preserves shared staffing");
        helper.assertTrue(SettlementConstructionService.cancel(f.leader(), f.town(), houseId), "cancellation releases the house crew");
        helper.assertFalse(SettlementConstructionMaterials.refundPending(house), "unspent house supplies return to storage");
        helper.assertTrue(project.pending(), "wall still waits for its own material quote");
        var plan = SettlementConstructionService.scan(level, project);
        SettlementConstructionGameTests.fundAndDispatch(helper, f.town(), plan, f.origin(), wallId);
        helper.assertTrue(SettlementConstructionService.configureCrew(f.leader(), f.town(), wallId, project.crewRevision(), 5,
                List.of(project.builder().equals(assistant.getUUID()) ? f.builder().getUUID() : assistant.getUUID())), "the released crew moves onto the wall");
        SettlementConstructionMaterials.collect(f.town(), project, f.builder().getUUID(), SettlementConstructionMaterials.collectionTarget(project));
        var worker = new BuilderWorker();
        var participants = new HashSet<UUID>();
        helper.startSequence().thenWaitUntil(() -> {
            for (var builder : List.of(f.builder(), assistant)) {
                if (!project.active()) break;
                SettlementConstructionService.reconcile(builder);
                int index = SettlementConstructionCoordination.select(f.town(), project, builder.getUUID(), plan, level, level.getGameTime());
                if (index >= 0 && index < project.total()) {
                    var target = plan.worldPos(project.origin(), plan.blocks().get(index));
                    var stance = reachableOutside(builder, first, target, f.origin().getY());
                    helper.assertTrue(stance != null, "builders keep a navigable outside approach from " + builder.blockPosition() + " as the wall closes at " + target);
                    builder.setPos(stance.getBottomCenter());
                }
                int before = project.placed();
                var work = HiredWorkSession.create(level, builder, HiredVillagerRole.BUILDER);
                worker.tick(level, builder, null, work.context());
                SettlementConstructionService.observe(builder, HiredWorkStateStore.state(builder));
                if (project.placed() > before) participants.add(builder.getUUID());
            }
            helper.assertValueEqual(project.status(), SettlementProject.Status.COMPLETED, "shared crew finishes the closed wall: " + project.blocker());
        }).thenExecute(() -> {
            helper.assertValueEqual(participants.size(), 2, "both builders contribute after the house cancellation");
            helper.assertTrue(level.getBlockState(f.origin().offset(4, -1, 10)).is(Blocks.DIRT_PATH), "wall construction preserves the separate path");
            helper.assertTrue(HiredWorkStateStore.state(farmer).contains(SettlementFarmWork.TAG), "builder projects do not consume or cancel the farmer order");
            f.builder().setOnGround(true);
            var exit = f.builder().getNavigation().createPath(f.origin().offset(-2, 0, 2), 0);
            helper.assertTrue(exit != null && exit.canReach(), "the crew can return to its base after closure");
            f.builder().discard();
            assistant.discard();
            farmer.discard();
        }).thenSucceed();
    }

    private static BlockPos reachableOutside(Villager builder, BlockPos corner, BlockPos target, int y) {
        builder.setOnGround(true);
        for (int distance : new int[]{2, 3}) for (var direction : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            var candidate = target.relative(direction, distance).atY(y);
            if (candidate.getX() >= corner.getX() && candidate.getX() <= corner.getX() + 8
                    && candidate.getZ() >= corner.getZ() && candidate.getZ() <= corner.getZ() + 8) continue;
            var path = builder.getNavigation().createPath(candidate, 0);
            if (path != null && path.canReach()) return candidate;
        }
        return null;
    }

    private static Villager farmer(GameTestHelper helper, SettlementConstructionGameTests.Fixture f) {
        var area = new SettlementArea(UUID.randomUUID(), SettlementAreaType.FARM, "Field", f.origin().offset(14, -1, 14),
                f.origin().offset(17, 3, 17), 5, true);
        helper.assertTrue(f.town().putArea(f.leader().getUUID(), area), "the field is separate from construction");
        var farmer = EntityType.VILLAGER.create(helper.getLevel());
        farmer.setNoAi(true);
        farmer.setPos(area.min().above().getBottomCenter());
        farmer.setVillagerData(farmer.getVillagerData().setProfession(VillagerProfession.FARMER));
        VillageAllegianceApi.assignKnown(helper.getLevel(), farmer, f.town().id(), AllegianceAssignmentSource.EXPLICIT_API);
        f.town().assignDuty(farmer.getUUID(), area.id());
        helper.getLevel().addFreshEntity(farmer);
        SettlementWorkforceService.reconcile(helper.getLevel(), farmer);
        var inventory = HiredJobInventory.getJobInventory(farmer);
        inventory.setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.IRON_HOE));
        var state = HiredWorkStateStore.state(farmer);
        state.putBoolean("AutoDepositOutputs", false);
        state.putBoolean("UseAssignedStorageForSupplies", false);
        return farmer;
    }
}
