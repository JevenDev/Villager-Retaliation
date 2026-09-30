package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.AllegianceAssignmentSource;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceApi;
import com.jvn.villagerretaliation.interaction.HiredVillagerRoles;
import com.jvn.villagerretaliation.interaction.work.courier.HiredTransferQueue;
import com.jvn.villagerretaliation.interaction.work.production.HiredProductionOrder;
import com.jvn.villagerretaliation.interaction.work.production.HiredProductionRecipe;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.jvn.villagerretaliation.profile.VillagerProfileManager;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("villagerretaliation")
@PrefixGameTestTemplate(false)
public final class SettlementSupplyChainGameTests {
    private SettlementSupplyChainGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 100, batch = "supply_chain")
    public static void harvestedWheatBecomesOneSharedMealAcrossSaveAndReturn(GameTestHelper helper) {
        SettlementFarmingGameTests.fixture(helper, fixture -> {
            var level = helper.getLevel();
            var town = fixture.town();
            var root = fixture.plot().position();
            var field = new SettlementArea(fixture.area().id(), SettlementAreaType.FARM, "Wheat field", root, root.offset(2, 2, 0), 5, true);
            town.putArea(town.leader(), field);
            town.setWorksitePlan(town.leader(), field.id(), SettlementWorksitePlan.defaults(field.type().role()));
            for (int index = 0; index < 3; index++) {
                var position = root.east(index);
                set(fixture.chunk(), position, Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
                set(fixture.chunk(), position.above(), ((CropBlock) Blocks.WHEAT).getStateForAge(7));
                var saved = SettlementFieldPlot.capture(level, field.id(), position, fixture.chunk(), 0L).save();
                saved.putInt("BlockLight", 15);
                town.recordField(SettlementFieldPlot.load(saved));
            }
            fixture.supply().setItem(0, new ItemStack(Items.WHEAT_SEEDS, 3));
            for (int tick = 0; tick < 20; tick++) SettlementBackgroundFarming.tick(level, town, fixture.worker(), 200, 1.0D);
            helper.assertValueEqual(fixture.output().count(stack -> stack.is(Items.WHEAT)), 3, "the three real crop drops reach the harvest depot");
            var storage = town.areas().get(fixture.supply().area());
            town.setWorksitePlan(town.leader(), storage.id(), SettlementWorksitePlan.defaults(storage.type().role()));
            var courier = worker(helper, town, storage, root.east(7), Items.AIR);
            var transfer = transfer(town, storage, fixture.output(), fixture.supply(), Items.WHEAT, 3);
            var kitchen = area(helper, town, SettlementAreaType.KITCHEN, "Bakery", root.north(4), root.offset(2, 2, -2));
            var food = depot(town, storage, root.east(10), SettlementDepotPurpose.FOOD);
            var links = new SettlementWorksiteLinks(Set.of(fixture.supply().id()), Set.of(food.id()), Set.of());
            town.setWorksitePlan(town.leader(), kitchen.id(), new SettlementWorksitePlan(SettlementWorksitePlan.defaults(kitchen.type().role()).settings(), 2, links));
            table(town, kitchen);
            var cook = worker(helper, town, kitchen, kitchen.min(), Items.AIR);
            var partner = worker(helper, town, kitchen, kitchen.min().east(), Items.AIR);
            var order = order(helper, town, kitchen, "bread", HiredProductionOrder.Mode.MAINTAIN_STOCK, 1, food.position());
            var viewer = net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,
                    new com.mojang.authlib.GameProfile(town.leader(), "SupplyViewer"));
            var shortage = SettlementSupplyService.supplies(viewer, town).stream()
                    .filter(row -> row.position().equals(food.position()) && row.item().is(Items.BREAD)).findFirst().orElseThrow();
            helper.assertValueEqual(shortage.shortage(), 1L, "the meal target appears before wheat arrives");
            helper.assertTrue(SettlementSupplyService.links(viewer, town, shortage.id().toString()).stream()
                    .anyMatch(link -> link.kind() == com.jvn.villagerretaliation.network.SettlementManagementPayload.SupplyLinkKind.INPUT_STORAGE
                            && fixture.supply().position().equals(link.position())), "the bread shortage links to the kitchen's actual wheat storage");
            var restored = SettlementRecord.load(town.save(level.registryAccess()), level.registryAccess(), () -> {});
            for (int tick = 0; tick < 40; tick++) {
                SettlementBackgroundTransfers.tick(level, restored, restored.workers().get(courier.id()), 200, 1.0D);
                SettlementBackgroundCrafting.tick(level, restored, restored.workers().get(cook.id()), 200, 1.0D);
                SettlementBackgroundCrafting.tick(level, restored, restored.workers().get(partner.id()), 200, 1.0D);
            }
            helper.assertValueEqual(restored.transferQueues().get(storage.id()).order(transfer.id()).delivered(), 3L, "one finite shipment supplies the kitchen");
            helper.assertValueEqual(restored.productionQueues().get(kitchen.id()).order(order.id()).produced(), 1L, "two cooks share one maintained meal target");
            helper.assertValueEqual(restored.depots().get(food.id()).count(stack -> stack.is(Items.BREAD)), 1, "the requested meal reaches food storage");
            helper.assertValueEqual(count(helper, restored, Items.WHEAT), 0, "one native bread recipe consumes exactly three harvested wheat");
            helper.assertValueEqual(count(helper, restored, Items.BREAD), 1, "stock accounting prevents duplicate meals");
            var fulfilled = SettlementSupplyService.supplies(viewer, restored).stream()
                    .filter(row -> row.id().equals(shortage.id())).findFirst().orElseThrow();
            helper.assertValueEqual(fulfilled.stored(), 1L, "the overview observes the finished meal after reload");
            helper.assertValueEqual(fulfilled.incoming(), 0L, "completed work is no longer incoming");
            helper.assertValueEqual(fulfilled.shortage(), 0L, "the real supply chain resolves its displayed shortage");
            for (var plot : restored.fields().values()) helper.assertTrue(plot.reconcile(fixture.chunk()), "returned crops accept their durable harvest receipts");
            var returning = EntityType.VILLAGER.create(level);
            returning.setUUID(cook.id());
            helper.assertTrue(restored.workers().get(cook.id()).restore(returning), "the returning cook receives the final inventory once");
            helper.assertFalse(restored.workers().get(cook.id()).restore(returning), "the same delivery handoff cannot replay");
            helper.assertFalse(level.hasChunkAt(root), "the entire chain uses observations without loading the field");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "supply_chain")
    public static void harvestedTimberSuppliesACompleteCooperativeProjectAcrossReload(GameTestHelper helper) {
        var blocks = new LinkedHashMap<BlockPos, BlockState>();
        for (int x = 0; x < 4; x++) for (int z = 0; z < 4; z++) blocks.put(new BlockPos(x, 0, z), Blocks.OAK_PLANKS.defaultBlockState());
        SettlementBackgroundConstructionGameTests.fixture(helper, false, blocks, fixture -> {
            var level = helper.getLevel();
            var town = fixture.town();
            var assistant = SettlementConstructionExecutionGameTests.assistant(helper, fixture);
            var storage = area(helper, town, SettlementAreaType.DEPOT, "Production storage", fixture.origin().east(15), fixture.origin().offset(18, 2, 2));
            var raw = depot(town, storage, storage.min(), SettlementDepotPurpose.GENERAL);
            var finished = depot(town, storage, storage.min().east(), SettlementDepotPurpose.OUTPUT);
            var forestRoot = fixture.origin().south(12);
            var forestry = area(helper, town, SettlementAreaType.FORESTRY, "Timber", forestRoot.offset(-3, -1, -3), forestRoot.offset(3, 5, 3));
            var forestLinks = new SettlementWorksiteLinks(Set.of(raw.id()), Set.of(raw.id()), Set.of());
            town.setWorksitePlan(town.leader(), forestry.id(), new SettlementWorksitePlan(SettlementWorksitePlan.defaults(forestry.type().role()).settings(), 1, forestLinks));
            raw.setItem(0, new ItemStack(Items.OAK_SAPLING));
            var chunks = forest(helper, town, forestry, forestRoot);
            var logger = worker(helper, town, forestry, forestRoot.west(2), Items.DIAMOND_AXE);
            for (int tick = 0; tick < 60; tick++) SettlementBackgroundLogging.tick(level, town, logger, 200, 1.0D);
            helper.assertValueEqual(raw.count(stack -> stack.is(Items.OAK_LOG)), 4, "four observed tree blocks produce four delivered logs");
            helper.assertTrue(town.forests().get(forestry.id()).getBlockState(forestRoot).is(Blocks.OAK_SAPLING), "the tree consumes one real replanting sapling");
            var workshop = area(helper, town, SettlementAreaType.WORKSHOP, "Sawmill", fixture.origin().offset(-2, 0, -10), fixture.origin().offset(2, 3, -6));
            town.setWorksitePlan(town.leader(), workshop.id(), new SettlementWorksitePlan(SettlementWorksitePlan.defaults(workshop.type().role()).settings(), 2,
                    new SettlementWorksiteLinks(Set.of(raw.id()), Set.of(finished.id()), Set.of())));
            table(town, workshop);
            var craftsman = worker(helper, town, workshop, workshop.min(), Items.AIR);
            var partner = worker(helper, town, workshop, workshop.min().east(), Items.AIR);
            var order = order(helper, town, workshop, "oak_planks", HiredProductionOrder.Mode.BATCH, 16, finished.position());
            for (int tick = 0; tick < 40; tick++) {
                SettlementBackgroundCrafting.tick(level, town, craftsman, 200, 1.0D);
                SettlementBackgroundCrafting.tick(level, town, partner, 200, 1.0D);
            }
            helper.assertValueEqual(finished.count(stack -> stack.is(Items.OAK_PLANKS)), 16, "four native recipe batches turn the harvested logs into sixteen planks");
            helper.assertValueEqual(town.productionQueues().get(workshop.id()).order(order.id()).produced(), 16L, "both craftspeople credit one shared quantity");
            helper.assertValueEqual(count(helper, town, Items.OAK_LOG), 0, "all four raw logs are consumed");
            var courier = worker(helper, town, storage, storage.min(), Items.AIR);
            var shipment = transfer(town, storage, finished, fixture.depot(), Items.OAK_PLANKS, 16);
            for (int tick = 0; tick < 10; tick++) SettlementBackgroundTransfers.tick(level, town, courier, 200, 1.0D);
            helper.assertValueEqual(town.transferQueues().get(storage.id()).order(shipment.id()).delivered(), 16L, "one finite material transfer supplies construction");
            SettlementBackgroundConstruction.tick(level, town, fixture.worker(), 400, 1.0D);
            SettlementBackgroundConstruction.tick(level, town, town.workers().get(assistant.getUUID()), 400, 1.0D);
            var restored = SettlementRecord.load(town.save(level.registryAccess()), level.registryAccess(), () -> {});
            var project = restored.projects().get(fixture.project().id());
            for (int tick = 0; tick < 100 && project.active(); tick++) {
                SettlementBackgroundConstruction.tick(level, restored, restored.workers().get(fixture.builder().getUUID()), 200, 1.0D);
                SettlementBackgroundConstruction.tick(level, restored, restored.workers().get(assistant.getUUID()), 200, 1.0D);
            }
            helper.assertValueEqual(project.status(), SettlementProject.Status.COMPLETED, "the crew completes construction from harvested resources");
            helper.assertValueEqual(project.placed(), 16, "the shared ledger credits sixteen placements");
            helper.assertValueEqual(project.site().placements().size(), 16, "each placement has one durable world receipt");
            helper.assertValueEqual(count(helper, restored, Items.OAK_PLANKS), 0, "construction consumes every crafted plank exactly once");
            var saved = restored.save(level.registryAccess());
            var reopened = SettlementRecord.load(saved, level.registryAccess(), () -> {});
            SettlementBackgroundConstruction.tick(level, reopened, reopened.workers().get(assistant.getUUID()), 400, 1.0D);
            helper.assertValueEqual(reopened.projects().get(project.id()).placed(), 16, "completed work cannot replay after another load");
            for (var chunk : chunks) SettlementForestService.observeArea(level, restored, forestry, chunk);
            helper.assertTrue(chunks.stream().filter(chunk -> chunk.getPos().equals(new ChunkPos(forestRoot))).findFirst().orElseThrow()
                    .getBlockState(forestRoot).is(Blocks.OAK_SAPLING), "the returning forest applies its saved cut and replanting");
            helper.assertFalse(level.hasChunkAt(fixture.origin()) || level.hasChunkAt(forestRoot), "the full supply chain never force-loads either worksite");
        });
    }

    private static SettlementArea area(GameTestHelper helper, SettlementRecord town, SettlementAreaType type, String name, BlockPos min, BlockPos max) {
        SettlementTestPlots.claim(helper.getLevel(), town, min, max);
        var area = new SettlementArea(UUID.randomUUID(), type, name, min, max, 5, true);
        helper.assertTrue(town.putArea(town.leader(), area), "the supply-chain worksite registers");
        town.setWorksitePlan(town.leader(), area.id(), SettlementWorksitePlan.defaults(type.role()));
        return area;
    }

    private static SettlementDepot depot(SettlementRecord town, SettlementArea storage, BlockPos position, SettlementDepotPurpose purpose) {
        var depot = town.registerDepot(town.leader(), storage.id(), position, purpose);
        depot.observeRules(List.of());
        return depot;
    }

    private static SettlementWorkerState worker(GameTestHelper helper, SettlementRecord town, SettlementArea area, BlockPos position, Item tool) {
        var level = helper.getLevel();
        var villager = EntityType.VILLAGER.create(level);
        villager.setPos(position.getBottomCenter());
        villager.setNoAi(true);
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.TOOLSMITH));
        VillageAllegianceApi.assignKnown(level, villager, town.id(), AllegianceAssignmentSource.EXPLICIT_API);
        for (var skill : HiredVillagerRoles.roleSkills(area.type().role())) VillagerProfileManager.setSkill(level, villager, skill, 60);
        town.assignDuty(villager.getUUID(), area.id());
        SettlementWorkforceService.reconcile(level, villager);
        if (tool != Items.AIR) HiredJobInventory.getJobInventory(villager).setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(tool));
        var worker = SettlementWorkerState.capture(level, villager, SettlementWorkforceService.duty(villager).orElseThrow(), 0L);
        town.recordWorker(worker);
        town.recordResident(SettlementResidentState.capture(villager, town, 0L));
        return worker;
    }

    private static void table(SettlementRecord town, SettlementArea area) {
        town.recordSurvey(area.id(), new SettlementBuildingSurvey.Survey(0L, List.of(), List.of(area.min()), true,
                Map.of(area.min(), ResourceLocation.withDefaultNamespace("crafting_table"))));
    }

    private static HiredProductionOrder order(GameTestHelper helper, SettlementRecord town, SettlementArea area, String recipe,
                                               HiredProductionOrder.Mode mode, int count, BlockPos output) {
        var order = HiredProductionOrder.create(helper.getLevel(), HiredProductionRecipe.exact(ResourceLocation.withDefaultNamespace(recipe), Map.of()), mode, count, output);
        helper.assertTrue(town.beginProduction(town.leader(), area.id()).append(order), "the finite shared recipe order is accepted");
        return order;
    }

    private static HiredTransferQueue.Order transfer(SettlementRecord town, SettlementArea area, SettlementDepot source,
                                                       SettlementDepot destination, Item item, int count) {
        var order = HiredTransferQueue.Order.create(new HiredTransferQueue.Endpoint(source.position(), source.id()),
                new HiredTransferQueue.Endpoint(destination.position(), destination.id()), new ItemStack(item), count);
        if (!town.beginTransfers(town.leader(), area.id()).append(order)) throw new IllegalStateException("Cannot add supply-chain transfer");
        return order;
    }

    private static int count(GameTestHelper helper, SettlementRecord town, Item item) {
        return town.depots().values().stream().mapToInt(depot -> depot.count(stack -> stack.is(item))).sum()
                + town.workers().values().stream().flatMap(worker -> worker.items(helper.getLevel().registryAccess()).stream())
                    .filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
    }

    private static List<LevelChunk> forest(GameTestHelper helper, SettlementRecord town, SettlementArea area, BlockPos root) {
        var chunks = new LinkedHashMap<ChunkPos, LevelChunk>();
        for (var position : BlockPos.betweenClosed(area.min(), area.max())) {
            var chunk = chunks.computeIfAbsent(new ChunkPos(position), key -> new LevelChunk(helper.getLevel(), key));
            if (position.getY() < root.getY()) set(chunk, position, Blocks.DIRT.defaultBlockState());
        }
        for (int y = 0; y < 4; y++) set(chunks.get(new ChunkPos(root)), root.above(y), Blocks.OAK_LOG.defaultBlockState());
        for (var direction : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            var leaf = root.above(3).relative(direction);
            set(chunks.get(new ChunkPos(leaf)), leaf, Blocks.OAK_LEAVES.defaultBlockState());
        }
        for (var chunk : chunks.values()) SettlementForestService.observeArea(helper.getLevel(), town, area, chunk);
        return List.copyOf(chunks.values());
    }

    private static void set(LevelChunk chunk, BlockPos position, BlockState block) {
        chunk.getSection(chunk.getSectionIndex(position.getY())).setBlockState(position.getX() & 15, position.getY() & 15, position.getZ() & 15, block);
    }
}
