package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.AllegianceAssignmentSource;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceApi;
import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.HiredWorkSession;
import com.jvn.villagerretaliation.interaction.HiredWorkStateStore;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderStructureScanner;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderTaskState;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderWorker;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import java.util.List;
import java.util.Map;
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
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("villagerretaliation")
@PrefixGameTestTemplate(false)
public final class SettlementConstructionExecutionGameTests {
    private SettlementConstructionExecutionGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 100, batch = "construction_execution")
    public static void crewSelectsDistinctSupportedPlacements(GameTestHelper helper) {
        var blocks = Map.of(BlockPos.ZERO, Blocks.OAK_PLANKS.defaultBlockState(), BlockPos.ZERO.above(), Blocks.TORCH.defaultBlockState(),
                BlockPos.ZERO.east(2), Blocks.OAK_PLANKS.defaultBlockState());
        SettlementBackgroundConstructionGameTests.fixture(helper, false, blocks, fixture -> {
            var second = UUID.randomUUID();
            var third = UUID.randomUUID();
            fixture.project().configureCrew(0L, 5, List.of(second, third));
            var view = new SettlementConstructionView(helper.getLevel(), fixture.site());
            int first = SettlementConstructionCoordination.select(fixture.town(), fixture.project(), fixture.builder().getUUID(), fixture.plan(), view, 0L);
            int next = SettlementConstructionCoordination.select(fixture.town(), fixture.project(), second, fixture.plan(), view, 0L);
            helper.assertTrue(first >= 0 && next >= 0 && first != next, "two workers claim independent placements");
            helper.assertTrue(fixture.plan().blocks().get(first).state().is(Blocks.OAK_PLANKS)
                    && fixture.plan().blocks().get(next).state().is(Blocks.OAK_PLANKS), "unsupported torches cannot be selected");
            helper.assertValueEqual(SettlementConstructionCoordination.select(fixture.town(), fixture.project(), third, fixture.plan(), view, 0L),
                    -1, "the third worker waits for supports instead of duplicating claims");
            var block = fixture.plan().blocks().get(first);
            var pos = fixture.plan().worldPos(fixture.origin(), block);
            fixture.site().queue(new SettlementConstructionPlacement(helper.getLevel(),
                    List.of(new SettlementConstructionPlacement.Part(pos, Blocks.AIR.defaultBlockState(), block.state(), null))));
            SettlementConstructionCoordination.refresh(fixture.town(), fixture.project(), fixture.plan(), view);
            helper.assertValueEqual(fixture.project().placed(), 1, "one recorded world placement advances shared progress once");
            helper.assertFalse(helper.getLevel().hasChunkAt(fixture.origin()), "coordination never loads the remote site");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "construction_execution")
    public static void backgroundCrewConsumesFiniteMaterialsAcrossReload(GameTestHelper helper) {
        SettlementBackgroundConstructionGameTests.fixture(helper, false, planks(), fixture -> {
            var assistant = assistant(helper, fixture);
            var worker = fixture.town().workers().get(assistant.getUUID());
            fixture.depot().setItem(0, new ItemStack(Items.OAK_PLANKS, 4));
            SettlementBackgroundConstruction.tick(helper.getLevel(), fixture.town(), fixture.worker(), 400, 1.0D);
            SettlementBackgroundConstruction.tick(helper.getLevel(), fixture.town(), worker, 400, 1.0D);
            helper.assertValueEqual(fixture.depot().count(stack -> stack.is(Items.OAK_PLANKS)), 2,
                    "each builder fetches one claimed placement instead of claiming the entire project inventory");
            helper.assertTrue(fixture.project().crew().claim(fixture.builder().getUUID(), helper.getLevel().getGameTime()).index()
                    != fixture.project().crew().claim(assistant.getUUID(), helper.getLevel().getGameTime()).index(), "in-flight pickup claims remain distinct");
            var restored = SettlementRecord.load(fixture.town().save(helper.getLevel().registryAccess()), helper.getLevel().registryAccess(), () -> {});
            var project = restored.projects().get(fixture.project().id());
            var first = restored.workers().get(fixture.builder().getUUID());
            var second = restored.workers().get(assistant.getUUID());
            for (int tick = 0; tick < 50 && project.active(); tick++) {
                SettlementBackgroundConstruction.tick(helper.getLevel(), restored, first, 200, 1.0D);
                SettlementBackgroundConstruction.tick(helper.getLevel(), restored, second, 200, 1.0D);
            }
            helper.assertValueEqual(project.status(), SettlementProject.Status.COMPLETED,
                    "the reloaded crew completes the shared project: " + first.status() + " / " + second.status() + " / " + project.blocker());
            helper.assertValueEqual(project.placed(), 4, "all four placements count once");
            helper.assertValueEqual(project.site().placements().size(), 4, "there is one durable receipt per block");
            helper.assertValueEqual(restored.depots().get(fixture.depot().id()).count(stack -> stack.is(Items.OAK_PLANKS))
                    + carried(helper, first) + carried(helper, second), 0, "exactly four plank items were consumed");
            helper.assertValueEqual(restored.duties().get(assistant.getUUID()), worker.area(), "completion retains the assistant's separate home assignment");
            int receipts = project.site().placements().size();
            SettlementBackgroundConstruction.tick(helper.getLevel(), restored, second, 1000, 1.0D);
            helper.assertValueEqual(project.site().placements().size(), receipts, "completion cannot replay a worker's private cursor");
            helper.assertFalse(helper.getLevel().hasChunkAt(fixture.origin()), "the entire crew works only from saved observations");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "construction_execution")
    public static void nativeCrewSharesProgressAndUsesProjectBounds(GameTestHelper helper) {
        SettlementBackgroundConstructionGameTests.fixture(helper, true, planks(), fixture -> {
            var level = helper.getLevel();
            for (var pos : BlockPos.betweenClosed(fixture.area().min(), fixture.area().max())) {
                level.setBlock(pos, pos.getY() < fixture.origin().getY() ? Blocks.DIRT.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
            }
            var assistant = assistant(helper, fixture);
            var firstInventory = HiredJobInventory.getJobInventory(fixture.builder());
            var secondInventory = HiredJobInventory.getJobInventory(assistant);
            firstInventory.setItem(firstInventory.supplySlots().getFirst(), new ItemStack(Items.OAK_PLANKS, 2));
            secondInventory.setItem(secondInventory.supplySlots().getFirst(), new ItemStack(Items.OAK_PLANKS, 2));
            SettlementConstructionService.reconcile(assistant);
            var secondSession = HiredWorkSession.create(level, assistant, HiredVillagerRole.BUILDER);
            helper.assertValueEqual(secondSession.area().min(), fixture.area().min(), "the helper works inside the project bounds");
            helper.assertFalse(fixture.town().duties().get(assistant.getUUID()).equals(fixture.area().id()), "the home assignment remains separate");
            var builder = new BuilderWorker();
            for (int tick = 0; tick < 8 && fixture.project().active(); tick++) {
                for (var member : List.of(fixture.builder(), assistant)) {
                    SettlementConstructionService.reconcile(member);
                    builder.tick(level, member, null, HiredWorkSession.create(level, member, HiredVillagerRole.BUILDER).context());
                }
            }
            helper.assertValueEqual(fixture.project().status(), SettlementProject.Status.COMPLETED,
                    "the native crew completes one project: " + fixture.project().blocker());
            for (var block : fixture.plan().blocks()) helper.assertTrue(BuilderStructureScanner.sameSchematicState(
                    level.getBlockState(fixture.plan().worldPos(fixture.origin(), block)), block.state()), "every real block matches the plan");
            helper.assertTrue(firstInventory.findSupply(stack -> stack.is(Items.OAK_PLANKS)).isEmpty()
                    && secondInventory.findSupply(stack -> stack.is(Items.OAK_PLANKS)).isEmpty(), "native placement consumes four items once");
            helper.assertValueEqual(fixture.project().site().awaitingCheckpoint().size(), 4, "native receipts are shared across builders");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "construction_execution")
    public static void missingMaterialsAndUnreachableHelpersPreserveProjectResources(GameTestHelper helper) {
        SettlementBackgroundConstructionGameTests.fixture(helper, false, planks(), fixture -> {
            var assistant = assistant(helper, fixture);
            var worker = fixture.town().workers().get(assistant.getUUID());
            for (int tick = 0; tick < 10; tick++) SettlementBackgroundConstruction.tick(helper.getLevel(), fixture.town(), worker, 200, 1.0D);
            helper.assertValueEqual(worker.status(), "missing_construction_materials", "an empty supply depot produces the existing supply blocker");
            helper.assertValueEqual(fixture.project().placed(), 0, "missing supplies never advance progress");
            helper.assertTrue(fixture.project().crew().claim(assistant.getUUID(), helper.getLevel().getGameTime()) == null,
                    "a supply blocker releases the unstarted placement");
            assistant.setPos(fixture.origin().offset(-30, 0, 0).getBottomCenter());
            var unreachable = SettlementWorkerState.capture(helper.getLevel(), assistant, SettlementWorkforceService.duty(assistant).orElseThrow(), 0L);
            fixture.town().recordWorker(unreachable);
            fixture.depot().setItem(0, new ItemStack(Items.OAK_PLANKS, 4));
            SettlementBackgroundConstruction.tick(helper.getLevel(), fixture.town(), unreachable, 1000, 1.0D);
            helper.assertValueEqual(unreachable.status(), "construction_unreachable", "unobserved approaches remain blocked");
            helper.assertValueEqual(fixture.depot().count(stack -> stack.is(Items.OAK_PLANKS)), 4, "unreachable helpers cannot spend supplies");
            fixture.town().removeDuty(assistant.getUUID());
            helper.assertTrue(fixture.project().active() && fixture.project().assistants().isEmpty(), "reassignment detaches only the assistant");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "construction_execution")
    public static void cancellationRetainsCollectedCrewMaterialsAndReleasesClaims(GameTestHelper helper) {
        SettlementBackgroundConstructionGameTests.fixture(helper, false, planks(), fixture -> {
            var assistant = assistant(helper, fixture);
            var worker = fixture.town().workers().get(assistant.getUUID());
            fixture.depot().setItem(0, new ItemStack(Items.OAK_PLANKS, 4));
            SettlementBackgroundConstruction.tick(helper.getLevel(), fixture.town(), worker, 400, 1.0D);
            helper.assertValueEqual(carried(helper, worker), 1, "the assistant collected one real material");
            fixture.town().finishProject(fixture.project().id(), SettlementProject.Status.CANCELLED);
            SettlementBackgroundConstruction.tick(helper.getLevel(), fixture.town(), worker, 200, 1.0D);
            helper.assertValueEqual(carried(helper, worker) + fixture.depot().count(stack -> stack.is(Items.OAK_PLANKS)), 4,
                    "cancellation preserves supplies already carried by the crew");
            helper.assertTrue(fixture.project().crew().claim(assistant.getUUID(), helper.getLevel().getGameTime()) == null, "cancellation releases the placement");
            helper.assertTrue(fixture.site().placements().isEmpty(), "cancelled placement cannot later reach the world");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "construction_execution")
    public static void crewPlacesDoorPairsWithOneItemAndReceiptPerDoor(GameTestHelper helper) {
        var blocks = new java.util.LinkedHashMap<BlockPos, BlockState>();
        for (int x : new int[] { 0, 2 }) {
            var base = new BlockPos(x, 0, 0);
            blocks.put(base, Blocks.OAK_PLANKS.defaultBlockState());
            blocks.put(base.above(), Blocks.OAK_DOOR.defaultBlockState());
            blocks.put(base.above(2), Blocks.OAK_DOOR.defaultBlockState().setValue(
                    net.minecraft.world.level.block.DoorBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));
        }
        SettlementBackgroundConstructionGameTests.fixture(helper, false, blocks, fixture -> {
            var assistant = assistant(helper, fixture);
            var worker = fixture.town().workers().get(assistant.getUUID());
            fixture.depot().setItem(0, new ItemStack(Items.OAK_PLANKS, 2));
            fixture.depot().setItem(1, new ItemStack(Items.OAK_DOOR, 2));
            for (int tick = 0; tick < 50 && fixture.project().active(); tick++) {
                SettlementBackgroundConstruction.tick(helper.getLevel(), fixture.town(), fixture.worker(), 400, 1.0D);
                SettlementBackgroundConstruction.tick(helper.getLevel(), fixture.town(), worker, 400, 1.0D);
            }
            helper.assertValueEqual(fixture.project().status(), SettlementProject.Status.COMPLETED,
                    "supported door pairs complete: " + fixture.project().blocker());
            helper.assertValueEqual(fixture.project().placed(), 6, "each half advances shared block progress exactly once");
            helper.assertValueEqual(fixture.site().placements().size(), 4, "two supports and two paired doors have four receipts");
            helper.assertValueEqual(fixture.site().placements().values().stream().filter(placement -> placement.parts().size() == 2).count(),
                    2L, "each door is reserved and delivered as an indivisible pair");
            helper.assertValueEqual(fixture.depot().count(stack -> stack.is(Items.OAK_DOOR) || stack.is(Items.OAK_PLANKS)), 0,
                    "only two door items and two supports are needed");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "construction_execution")
    public static void prioritySwitchesReleaseClaimsAndPreserveCarriedMaterials(GameTestHelper helper) {
        var fixture = SettlementConstructionGameTests.fixture(helper);
        var assistant = SettlementConstructionCrewGameTests.assistant(helper, fixture);
        try {
            fixture.town().advance();
            fixture.town().advance();
            SettlementConstructionCrewGameTests.adopt(helper, fixture);
            var first = SettlementConstructionCrewGameTests.begin(helper, fixture);
            first.configureCrew(0L, 4, List.of(assistant.getUUID()));
            SettlementConstructionService.reconcile(assistant);
            first.crew().acquire(assistant.getUUID(), 0, List.of(fixture.origin()), helper.getLevel().getGameTime());
            var inventory = HiredJobInventory.getJobInventory(assistant);
            inventory.setItem(inventory.supplySlots().getFirst(), new ItemStack(Items.OAK_PLANKS));
            var otherLead = UUID.randomUUID();
            fixture.town().assignDuty(otherLead, fixture.area().id());
            var second = SettlementProject.create(fixture.town(), fixture.area().id(), otherLead, fixture.plan(), fixture.origin().east(10),
                    UUID.randomUUID(), helper.getLevel().getGameTime());
            helper.assertTrue(fixture.town().startProject(fixture.leader().getUUID(), second), "a second project is available");
            second.configureCrew(0L, 8, List.of(assistant.getUUID()));
            SettlementConstructionService.reconcile(assistant);
            helper.assertValueEqual(BuilderTaskState.jobId(HiredWorkStateStore.state(assistant)).orElseThrow(), second.id(),
                    "the next native work session chooses the higher priority project");
            helper.assertTrue(first.active() && first.crew().claim(assistant.getUUID(), helper.getLevel().getGameTime()) == null,
                    "switching releases only the old placement and leaves its project active");
            helper.assertValueEqual(inventory.getItem(inventory.supplySlots().getFirst()).getCount(), 1, "switching preserves carried supplies");
            fixture.town().removeWorker(assistant.getUUID());
            helper.assertTrue(first.assistants().isEmpty() && second.assistants().isEmpty(), "worker death or removal releases all crew membership");
            helper.assertTrue(first.active() && second.active(), "the surviving leads retain both projects");
        } finally {
            assistant.discard();
            fixture.builder().discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "construction_execution")
    public static void fullClearingOutputReleasesCrewClaimsWithoutSpendingMaterials(GameTestHelper helper) {
        SettlementBackgroundConstructionGameTests.fixture(helper, false, planks(), fixture -> {
            var assistant = assistant(helper, fixture);
            var worker = fixture.town().workers().get(assistant.getUUID());
            fixture.site().queue(new SettlementConstructionPlacement(helper.getLevel(),
                    List.of(new SettlementConstructionPlacement.Part(fixture.origin(), Blocks.AIR.defaultBlockState(),
                            Blocks.STONE.defaultBlockState(), null))));
            var items = worker.items(helper.getLevel().registryAccess());
            for (int slot = HiredJobInventory.MAIN_GRID_START; slot < HiredJobInventory.FILTER_SLOT; slot++) {
                items.set(slot, new ItemStack(Items.COBBLESTONE, 64));
            }
            items.set(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.IRON_PICKAXE));
            worker.updateInventory(helper.getLevel().registryAccess(), items);
            fixture.depot().setItem(0, new ItemStack(Items.OAK_PLANKS, 4));
            SettlementBackgroundConstruction.tick(helper.getLevel(), fixture.town(), worker, 1000, 1.0D);
            helper.assertValueEqual(worker.status(), "output_full", "clearing waits for space for the real mined output");
            helper.assertTrue(fixture.site().getBlockState(fixture.origin()).is(Blocks.STONE), "the obstruction is not removed without output space");
            helper.assertValueEqual(fixture.depot().count(stack -> stack.is(Items.OAK_PLANKS)), 4, "building materials remain untouched");
            helper.assertTrue(fixture.project().crew().claim(assistant.getUUID(), helper.getLevel().getGameTime()) == null,
                    "blocked clearing does not retain a placement claim");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "construction_execution")
    public static void reloadedBuildersPreferPlacementsMatchingTheirCarriedMaterials(GameTestHelper helper) {
        SettlementBackgroundConstructionGameTests.fixture(helper, false,
                Map.of(BlockPos.ZERO, Blocks.OAK_PLANKS.defaultBlockState(), BlockPos.ZERO.east(), Blocks.IRON_BLOCK.defaultBlockState()), fixture -> {
            var assistant = assistant(helper, fixture);
            var worker = fixture.town().workers().get(assistant.getUUID());
            var firstItems = fixture.worker().items(helper.getLevel().registryAccess());
            firstItems.set(fixture.worker().supplySlots().getFirst(), new ItemStack(Items.IRON_BLOCK));
            fixture.worker().updateInventory(helper.getLevel().registryAccess(), firstItems);
            var secondItems = worker.items(helper.getLevel().registryAccess());
            secondItems.set(worker.supplySlots().getFirst(), new ItemStack(Items.OAK_PLANKS));
            worker.updateInventory(helper.getLevel().registryAccess(), secondItems);
            var restored = SettlementRecord.load(fixture.town().save(helper.getLevel().registryAccess()), helper.getLevel().registryAccess(), () -> {});
            var project = restored.projects().get(fixture.project().id());
            for (int tick = 0; tick < 10 && project.active(); tick++) {
                SettlementBackgroundConstruction.tick(helper.getLevel(), restored, restored.workers().get(fixture.builder().getUUID()), 400, 1.0D);
                SettlementBackgroundConstruction.tick(helper.getLevel(), restored, restored.workers().get(assistant.getUUID()), 400, 1.0D);
            }
            helper.assertValueEqual(project.status(), SettlementProject.Status.COMPLETED,
                    "reloaded workers cannot deadlock by claiming placements that need each other's carried materials");
            helper.assertValueEqual(project.site().placements().size(), 2, "each carried material funds exactly one placement");
        });
    }

    static Villager assistant(GameTestHelper helper, SettlementBackgroundConstructionGameTests.Fixture fixture) {
        var level = helper.getLevel();
        var home = new SettlementArea(UUID.randomUUID(), SettlementAreaType.CONSTRUCTION, "Crew home",
                fixture.origin().offset(-14, -1, 0), fixture.origin().offset(-10, 3, 3), 5, true);
        helper.assertTrue(fixture.town().putArea(fixture.town().leader(), home), "a separate construction home is registered");
        fixture.town().setWorksitePlan(fixture.town().leader(), home.id(), SettlementWorksitePlan.defaults(HiredVillagerRole.BUILDER));
        fixture.town().setWorksitePlan(fixture.town().leader(), fixture.area().id(), SettlementWorksitePlan.defaults(HiredVillagerRole.BUILDER));
        var assistant = EntityType.VILLAGER.create(level);
        assistant.setPos(fixture.origin().offset(-2, 0, 1).getBottomCenter());
        assistant.setOnGround(true);
        assistant.setNoAi(true);
        assistant.setVillagerData(assistant.getVillagerData().setProfession(VillagerProfession.MASON));
        VillageAllegianceApi.assignKnown(level, assistant, fixture.town().id(), AllegianceAssignmentSource.EXPLICIT_API);
        fixture.town().assignDuty(assistant.getUUID(), home.id());
        SettlementWorkforceService.reconcile(level, assistant);
        fixture.town().recordResident(SettlementResidentState.capture(assistant, fixture.town(), 0L));
        var worker = SettlementWorkerState.capture(level, assistant, SettlementWorkforceService.duty(assistant).orElseThrow(), 0L);
        fixture.town().recordWorker(worker);
        helper.assertTrue(fixture.project().configureCrew(0L, 5, List.of(assistant.getUUID())), "one assistant joins the shared ledger");
        return assistant;
    }

    private static int carried(GameTestHelper helper, SettlementWorkerState worker) {
        return worker.items(helper.getLevel().registryAccess()).stream().filter(stack -> stack.is(Items.OAK_PLANKS)).mapToInt(ItemStack::getCount).sum();
    }

    private static Map<BlockPos, BlockState> planks() {
        return Map.of(BlockPos.ZERO, Blocks.OAK_PLANKS.defaultBlockState(), BlockPos.ZERO.east(), Blocks.OAK_PLANKS.defaultBlockState(),
                BlockPos.ZERO.south(), Blocks.OAK_PLANKS.defaultBlockState(), BlockPos.ZERO.south().east(), Blocks.OAK_PLANKS.defaultBlockState());
    }
}
