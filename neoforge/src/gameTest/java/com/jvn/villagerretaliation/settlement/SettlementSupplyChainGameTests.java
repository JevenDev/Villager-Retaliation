package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.AllegianceAssignmentSource;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceApi;
import com.jvn.villagerretaliation.interaction.HiredVillagerRoles;
import com.jvn.villagerretaliation.interaction.work.HiredEquipmentNeed;
import com.jvn.villagerretaliation.interaction.work.HiredRangedAmmo;
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
            for (int tick = 0; tick < 20; tick++) {
                SettlementTestPlots.planSupplies(level, town);
                SettlementBackgroundFarming.tick(level, town, fixture.worker(), 200, 1.0D);
            }
            helper.assertValueEqual(fixture.output().count(stack -> stack.is(Items.WHEAT)), 3, "the three real crop drops reach the harvest depot");
            var storage = town.areas().get(fixture.supply().area());
            town.setWorksitePlan(town.leader(), storage.id(), SettlementWorksitePlan.defaults(storage.type().role()));
            var courier = worker(helper, town, storage, root.east(7), Items.AIR);
            var transfer = transfer(town, storage, fixture.output(), fixture.supply(), Items.WHEAT, 3, HiredTransferQueue.Mode.MAINTAIN_STOCK);
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
                SettlementTestPlots.planSupplies(level, restored);
                SettlementBackgroundTransfers.tick(level, restored, restored.workers().get(courier.id()), 200, 1.0D);
                SettlementTestPlots.planSupplies(level, restored);
                SettlementBackgroundCrafting.tick(level, restored, restored.workers().get(cook.id()), 200, 1.0D);
                SettlementTestPlots.planSupplies(level, restored);
                SettlementBackgroundCrafting.tick(level, restored, restored.workers().get(partner.id()), 200, 1.0D);
            }
            helper.assertValueEqual(restored.transferQueues().get(storage.id()).order(transfer.id()).delivered(), 3L, "the recurring shipment supplies the kitchen once");
            helper.assertValueEqual(restored.productionQueues().get(kitchen.id()).order(order.id()).produced(), 1L, "two cooks share one maintained meal target");
            helper.assertValueEqual(restored.depots().get(food.id()).count(stack -> stack.is(Items.BREAD)), 1, "the requested meal reaches food storage");
            helper.assertValueEqual(count(helper, restored, Items.WHEAT), 0, "one native bread recipe consumes exactly three harvested wheat");
            helper.assertValueEqual(count(helper, restored, Items.BREAD), 1, "stock accounting prevents duplicate meals");
            var fulfilled = SettlementSupplyService.supplies(viewer, restored).stream()
                    .filter(row -> row.id().equals(shortage.id())).findFirst().orElseThrow();
            helper.assertValueEqual(fulfilled.stored(), 1L, "the overview observes the finished meal after reload");
            helper.assertValueEqual(fulfilled.incoming(), 0L, "completed work is no longer incoming");
            helper.assertValueEqual(fulfilled.shortage(), 0L, "the real supply chain resolves its displayed shortage");
            restored.depots().get(food.id()).removeItem(0, 1);
            restored.depots().get(fixture.output().id()).setItem(0, new ItemStack(Items.WHEAT, 3));
            for (int tick = 0; tick < 40; tick++) {
                SettlementTestPlots.planSupplies(level, restored);
                SettlementBackgroundTransfers.tick(level, restored, restored.workers().get(courier.id()), 200, 1.0D);
                SettlementTestPlots.planSupplies(level, restored);
                SettlementBackgroundCrafting.tick(level, restored, restored.workers().get(cook.id()), 200, 1.0D);
                SettlementTestPlots.planSupplies(level, restored);
                SettlementBackgroundCrafting.tick(level, restored, restored.workers().get(partner.id()), 200, 1.0D);
            }
            helper.assertValueEqual(restored.transferQueues().get(storage.id()).order(transfer.id()).delivered(), 6L, "a recurring delivery supplies the next harvest after consumption");
            helper.assertValueEqual(restored.productionQueues().get(kitchen.id()).order(order.id()).produced(), 2L, "maintained bread production replenishes the consumed meal once");
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
            for (int tick = 0; tick < 60; tick++) {
                SettlementTestPlots.planSupplies(level, town);
                SettlementBackgroundLogging.tick(level, town, logger, 200, 1.0D);
            }
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
                SettlementTestPlots.planSupplies(level, town);
                SettlementBackgroundCrafting.tick(level, town, craftsman, 200, 1.0D);
                SettlementTestPlots.planSupplies(level, town);
                SettlementBackgroundCrafting.tick(level, town, partner, 200, 1.0D);
            }
            helper.assertValueEqual(finished.count(stack -> stack.is(Items.OAK_PLANKS)), 16, "four native recipe batches turn the harvested logs into sixteen planks");
            helper.assertValueEqual(town.productionQueues().get(workshop.id()).order(order.id()).produced(), 16L, "both craftspeople credit one shared quantity");
            helper.assertValueEqual(count(helper, town, Items.OAK_LOG), 0, "all four raw logs are consumed");
            var courier = worker(helper, town, storage, storage.min(), Items.AIR);
            var shipment = transfer(town, storage, finished, fixture.depot(), Items.OAK_PLANKS, 16);
            for (int tick = 0; tick < 10; tick++) {
                SettlementTestPlots.planSupplies(level, town);
                SettlementBackgroundTransfers.tick(level, town, courier, 200, 1.0D);
            }
            helper.assertValueEqual(town.transferQueues().get(storage.id()).order(shipment.id()).delivered(), 16L, "one finite material transfer supplies construction");
            var needs = SettlementDemandService.inspect(level, town).demands().stream().filter(row -> row.consumer().equals(fixture.project().id())).toList();
            helper.assertValueEqual(needs.stream().mapToLong(row -> row.requirement().quantity()).sum(), 16L, "a cooperative crew shares one set of placement requirements");
            helper.assertValueEqual(SettlementDemandService.inspect(level, town).sellable(fixture.depot(), new ItemStack(Items.OAK_PLANKS)), 0, "project requirements protect delivered planks");
            SettlementTestPlots.planSupplies(level, town);
            SettlementBackgroundConstruction.tick(level, town, fixture.worker(), 400, 1.0D);
            SettlementTestPlots.planSupplies(level, town);
            SettlementBackgroundConstruction.tick(level, town, town.workers().get(assistant.getUUID()), 400, 1.0D);
            var restored = SettlementRecord.load(town.save(level.registryAccess()), level.registryAccess(), () -> {});
            var project = restored.projects().get(fixture.project().id());
            for (int tick = 0; tick < 100 && project.active(); tick++) {
                SettlementTestPlots.planSupplies(level, restored);
                SettlementBackgroundConstruction.tick(level, restored, restored.workers().get(fixture.builder().getUUID()), 200, 1.0D);
                SettlementTestPlots.planSupplies(level, restored);
                SettlementBackgroundConstruction.tick(level, restored, restored.workers().get(assistant.getUUID()), 200, 1.0D);
            }
            helper.assertValueEqual(project.status(), SettlementProject.Status.COMPLETED, "the crew completes construction from harvested resources");
            helper.assertValueEqual(project.placed(), 16, "the shared ledger credits sixteen placements");
            helper.assertValueEqual(project.site().placements().size(), 16, "each placement has one durable world receipt");
            helper.assertValueEqual(count(helper, restored, Items.OAK_PLANKS), 0, "construction consumes every crafted plank exactly once");
            var saved = restored.save(level.registryAccess());
            var reopened = SettlementRecord.load(saved, level.registryAccess(), () -> {});
            SettlementTestPlots.planSupplies(level, reopened);
            SettlementBackgroundConstruction.tick(level, reopened, reopened.workers().get(assistant.getUUID()), 400, 1.0D);
            helper.assertValueEqual(reopened.projects().get(project.id()).placed(), 16, "completed work cannot replay after another load");
            for (var chunk : chunks) SettlementForestService.observeArea(level, restored, forestry, chunk);
            helper.assertTrue(chunks.stream().filter(chunk -> chunk.getPos().equals(new ChunkPos(forestRoot))).findFirst().orElseThrow()
                    .getBlockState(forestRoot).is(Blocks.OAK_SAPLING), "the returning forest applies its saved cut and replanting");
            helper.assertFalse(level.hasChunkAt(fixture.origin()) || level.hasChunkAt(forestRoot), "the full supply chain never force-loads either worksite");
        });
    }


    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_supply_chain")
    public static void foodPolicyFollowsPopulationAndNativeMealsAcrossSixDaysAndReloads(GameTestHelper helper) {
        SettlementFarmingGameTests.fixture(helper, fixture -> {
            var level = helper.getLevel();
            int randomTickSpeed = level.getGameRules().getInt(net.minecraft.world.level.GameRules.RULE_RANDOMTICKING);
            try {
                level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_RANDOMTICKING).set(3, level.getServer());
                var town = fixture.town();
                var center = town.center();
                SettlementTestPlots.claim(level, town, center.offset(-4, -1, -4), center.offset(11, 3, 10));
                var farm = new SettlementArea(fixture.area().id(), SettlementAreaType.FARM, "Wheat field",
                        center.offset(-3, 0, -3), center.offset(4, 1, 4), 5, true);
                helper.assertTrue(town.putArea(town.leader(), farm), "the configured field fits the existing settlement claim");
                for (var position : BlockPos.betweenClosed(farm.min(), new BlockPos(farm.max().getX(), center.getY(), farm.max().getZ()))) {
                    boolean water = position.equals(center) || position.equals(center.east(3));
                    set(fixture.chunk(), position, water ? Blocks.WATER.defaultBlockState()
                            : Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
                    set(fixture.chunk(), position.above(), water ? Blocks.AIR.defaultBlockState() : ((CropBlock) Blocks.WHEAT).getStateForAge(7));
                }
                town.removeField(center);
                for (var position : BlockPos.betweenClosed(farm.min(), new BlockPos(farm.max().getX(), center.getY(), farm.max().getZ()))) {
                    if (!fixture.chunk().getBlockState(position).is(Blocks.FARMLAND)) continue;
                    var saved = SettlementFieldPlot.capture(level, farm.id(), position, fixture.chunk(), 0).save();
                    saved.putInt("BlockLight", 15);
                    town.recordField(SettlementFieldPlot.load(saved));
                }
                var raw = fixture.output();
                raw.configure(SettlementDepotPurpose.GENERAL, 5);
                var food = depot(town, town.areas().get(fixture.supply().area()), center.east(10), SettlementDepotPurpose.FOOD);
                fixture.supply().setItem(0, new ItemStack(Items.WHEAT_SEEDS, 64));
                town.setWorksitePlan(town.leader(), farm.id(), new SettlementWorksitePlan(SettlementWorksitePlan.defaults(farm.type().role()).settings(), 1,
                        new SettlementWorksiteLinks(Set.of(fixture.supply().id()), Set.of(raw.id()), Set.of())));
                var kitchen = area(helper, town, SettlementAreaType.KITCHEN, "Bakery", center.offset(8, 0, 4), center.offset(10, 2, 6));
                town.setWorksitePlan(town.leader(), kitchen.id(), new SettlementWorksitePlan(SettlementWorksitePlan.defaults(kitchen.type().role()).settings(), 1,
                        new SettlementWorksiteLinks(Set.of(raw.id()), Set.of(food.id()), Set.of())));
                table(town, kitchen);
                var cook = worker(helper, town, kitchen, kitchen.min(), Items.AIR);
                var gather = new SettlementSupplyPolicy.Step(UUID.randomUUID(), SettlementSupplyPolicy.StepKind.GATHER,
                        farm.id(), null, new ItemStack(Items.WHEAT), null, raw.position());
                var bake = new SettlementSupplyPolicy.Step(UUID.randomUUID(), SettlementSupplyPolicy.StepKind.PRODUCTION, kitchen.id(),
                        HiredProductionRecipe.exact(ResourceLocation.withDefaultNamespace("bread"), Map.of()), ItemStack.EMPTY, null, food.position());
                var foodId = com.jvn.villagerretaliation.interaction.work.production.WorkSupplyDemand.identity(level.registryAccess(), town.id().value(),
                        com.jvn.villagerretaliation.interaction.work.production.WorkSupplyDemand.Kind.FOOD, SettlementFoodService.requirement(0));
                var policy = new SettlementSupplyPolicy(UUID.randomUUID(), new SettlementSupplyPolicy.Configuration("Daily meals",
                        new SettlementSupplyPolicy.Target(SettlementSupplyPolicy.TargetKind.FOOD, town.id().value(), foodId, null, ItemStack.EMPTY, 0), 9,
                        List.of(gather, bake), List.of(
                        new SettlementSupplyPolicy.Connection(gather.id(), bake.id(), SettlementPolicyPlanner.inputs(level, town, bake, 1).getFirst().id()),
                        new SettlementSupplyPolicy.Connection(bake.id(), null, foodId))), town.leader(), town::supplyChanged);
                helper.assertTrue(town.addSupplyPolicy(policy), "the food chain is configured once for the shared nutrition reserve");
                policy.state(SettlementSupplyPolicy.State.ACTIVE, town.leader(), "", level.getGameTime());
                long revision = policy.revision();
                long firstTarget = SettlementFoodService.reserveNutrition(level, town);
                long eaten = 0;
                UUID managedOrder = null;
                int reloads = 0;
                for (int now = 200; now <= 168_000; now += 200) {
                    level.setDayTime(6_000L + now);
                    if (now == 24_000) {
                        for (int index = 0; index < 2; index++) {
                            var arrival = EntityType.VILLAGER.create(level);
                            arrival.setPos(center.getCenter());
                            VillageAllegianceApi.assignKnown(level, arrival, town.id(), AllegianceAssignmentSource.EXPLICIT_API);
                            town.recordResident(SettlementResidentState.capture(arrival, town, now));
                        }
                        helper.assertValueEqual(SettlementFoodService.reserveNutrition(level, town), firstTarget * 2,
                                "two approved arrivals increase the same food reserve without editing its policy");
                    }
                    SettlementTestPlots.planSupplies(level, town);
                    int beforeMeals = count(helper, town, Items.BREAD);
                    for (var resident : town.residents().values()) resident.advance(level, town, now);
                    eaten += beforeMeals - count(helper, town, Items.BREAD);
                    SettlementBackgroundService.update(level, town, now);
                    var binding = SettlementPolicyOrders.current(policy, bake);
                    if (binding != null) {
                        if (managedOrder == null) managedOrder = binding.order();
                        helper.assertValueEqual(binding.order(), managedOrder, "growth and repeated meals retain one native production binding");
                    }
                    if (now >= 144_000 && SettlementFoodService.nutrition(town) >= SettlementFoodService.reserveNutrition(level, town)) break;
                    if (now % 24_000 != 0) continue;
                    var needs = SettlementDemandService.inspect(level, town).demands().stream()
                            .filter(row -> row.kind() == com.jvn.villagerretaliation.interaction.work.production.WorkSupplyDemand.Kind.FOOD).toList();
                    helper.assertValueEqual(needs.size(), 1, "all residents continue sharing one authoritative food requirement");
                    helper.assertValueEqual(needs.getFirst().supply().id(), foodId, "daily consumption and growth preserve food selection identity");
                    helper.assertValueEqual(policy.revision(), revision, "the configured chain requires no daily edits");
                    helper.assertValueEqual(policy.state(), SettlementSupplyPolicy.State.ACTIVE, "the approved chain keeps operating after each simulated day");
                    if (now == 48_000 || now == 96_000) {
                        town = SettlementRecord.load(town.save(level.registryAccess()), level.registryAccess(), () -> {});
                        policy = town.supplyPolicies().get(policy.id());
                        reloads++;
                    }
                }
                helper.assertTrue(eaten >= 8, "native resident metabolism consumes repeated meals across six simulated days");
                helper.assertValueEqual(reloads, 2, "both planned save boundaries ran during the chain");
                helper.assertTrue(managedOrder != null, "the policy creates its approved native bread order");
                helper.assertValueEqual(town.productionQueues().get(kitchen.id()).orders().size(), 1, "population growth does not duplicate production orders");
                long produced = town.productionQueues().get(kitchen.id()).order(managedOrder).produced();
                helper.assertValueEqual(produced, eaten + count(helper, town, Items.BREAD), "every crafted loaf is either present or consumed exactly once");
                helper.assertTrue(SettlementFoodService.nutrition(town) >= SettlementFoodService.reserveNutrition(level, town),
                        "the configured farm and bakery replenish the grown population's reserve: "
                                + SettlementFoodService.nutrition(town) + "/" + SettlementFoodService.reserveNutrition(level, town)
                                + " nutrition, " + produced + " produced, " + eaten + " eaten, "
                                + count(helper, town, Items.WHEAT) + " wheat, " + policy.blocker()
                                + ", farm " + town.workers().get(fixture.worker().id()).status()
                                + ", bakery " + town.workers().get(cook.id()).status());
                helper.assertTrue(town.residents().values().stream().allMatch(resident -> resident.food() >= 9 && resident.health() > 0),
                        "residents keep eating their protected food while the chain operates");
                var returning = EntityType.VILLAGER.create(level);
                returning.setUUID(cook.id());
                helper.assertTrue(town.workers().get(cook.id()).restore(returning), "the returning cook receives its background inventory");
                helper.assertFalse(town.workers().get(cook.id()).restore(returning), "the same inventory handoff cannot replay");
                helper.assertTrue(town.residents().get(cook.id()).restore(returning), "the returning cook receives its native nutrition state");
                helper.assertFalse(town.residents().get(cook.id()).restore(returning), "the same nutrition handoff cannot replay");
                for (var plot : town.fields().values()) helper.assertTrue(plot.reconcile(fixture.chunk()), "return reconciles every saved harvest and replanting");
                helper.assertFalse(level.hasChunkAt(center), "six days of policy work and save recovery never load the background village");
            } finally {
                level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_RANDOMTICKING).set(randomTickSpeed, level.getServer());
            }
        });
    }


    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_supply_chain")
    public static void configuredTimberChainReplacesBrokenToolsAndCompletesItsProjectAcrossReloads(GameTestHelper helper) {
        var blocks = new LinkedHashMap<BlockPos, BlockState>();
        for (int x = 0; x < 4; x++) for (int z = 0; z < 4; z++) blocks.put(new BlockPos(x, 0, z), Blocks.OAK_PLANKS.defaultBlockState());
        SettlementBackgroundConstructionGameTests.fixture(helper, false, blocks, fixture -> {
            var level = helper.getLevel();
            var town = fixture.town();
            var assistant = SettlementConstructionExecutionGameTests.assistant(helper, fixture);
            var storage = area(helper, town, SettlementAreaType.DEPOT, "Production storage", fixture.origin().east(15), fixture.origin().offset(18, 2, 2));
            var raw = depot(town, storage, storage.min(), SettlementDepotPurpose.GENERAL);
            var finished = depot(town, storage, storage.min().east(), SettlementDepotPurpose.OUTPUT);
            var root = fixture.origin().south(12);
            var forest = area(helper, town, SettlementAreaType.FORESTRY, "Timber", root.offset(-3, -1, -3), root.offset(3, 5, 3));
            town.setWorksitePlan(town.leader(), forest.id(), new SettlementWorksitePlan(SettlementWorksitePlan.defaults(forest.type().role()).settings(), 1,
                    new SettlementWorksiteLinks(Set.of(raw.id()), Set.of(raw.id()), Set.of())));
            raw.setItem(0, new ItemStack(Items.OAK_SAPLING));
            raw.setItem(1, new ItemStack(Items.IRON_AXE));
            var chunks = forest(helper, town, forest, root);
            var logger = worker(helper, town, forest, root.west(2), Items.DIAMOND_AXE);
            var equipment = logger.items(level.registryAccess());
            var axe = equipment.get(HiredJobInventory.MAINHAND_SLOT);
            axe.setDamageValue(axe.getMaxDamage() - 1);
            logger.updateInventory(level.registryAccess(), equipment);
            var workshop = area(helper, town, SettlementAreaType.WORKSHOP, "Sawmill", fixture.origin().offset(-2, 0, -10), fixture.origin().offset(2, 3, -6));
            town.setWorksitePlan(town.leader(), workshop.id(), new SettlementWorksitePlan(SettlementWorksitePlan.defaults(workshop.type().role()).settings(), 2,
                    new SettlementWorksiteLinks(Set.of(raw.id()), Set.of(finished.id()), Set.of())));
            table(town, workshop);
            var craftsman = worker(helper, town, workshop, workshop.min(), Items.AIR);
            var partner = worker(helper, town, workshop, workshop.min().east(), Items.AIR);
            var deliveryStorage = new SettlementStorageLinks(false, List.of(
                    new SettlementStorageLinks.Link(finished.position(), SettlementStorageLinks.Purpose.SUPPLY, 0),
                    new SettlementStorageLinks.Link(fixture.depot().position(), SettlementStorageLinks.Purpose.OUTPUT, 1)));
            town.setWorksitePlan(town.leader(), storage.id(), new SettlementWorksitePlan(
                    SettlementWorksitePlan.defaults(storage.type().role()).settings().withStorage(deliveryStorage), 1,
                    new SettlementWorksiteLinks(Set.of(finished.id()), Set.of(fixture.depot().id()), Set.of())));
            var courier = worker(helper, town, storage, storage.min(), Items.AIR);
            var gather = new SettlementSupplyPolicy.Step(UUID.randomUUID(), SettlementSupplyPolicy.StepKind.GATHER,
                    forest.id(), null, new ItemStack(Items.OAK_LOG), null, raw.position());
            var craft = new SettlementSupplyPolicy.Step(UUID.randomUUID(), SettlementSupplyPolicy.StepKind.PRODUCTION, workshop.id(),
                    HiredProductionRecipe.exact(ResourceLocation.withDefaultNamespace("oak_planks"), Map.of()), ItemStack.EMPTY, null, finished.position());
            var delivery = new SettlementSupplyPolicy.Step(UUID.randomUUID(), SettlementSupplyPolicy.StepKind.DELIVERY, storage.id(),
                    null, new ItemStack(Items.OAK_PLANKS), finished.position(), fixture.depot().position());
            var need = SettlementDemandService.inspect(level, town).demands().stream()
                    .filter(row -> row.consumer().equals(fixture.project().id())).findFirst().orElseThrow();
            var policy = new SettlementSupplyPolicy(UUID.randomUUID(), new SettlementSupplyPolicy.Configuration("Complete the platform",
                    new SettlementSupplyPolicy.Target(SettlementSupplyPolicy.TargetKind.CONSUMER, fixture.project().id(), need.supply().id(), null, ItemStack.EMPTY, 0),
                    9, List.of(gather, craft, delivery), List.of(
                    new SettlementSupplyPolicy.Connection(gather.id(), craft.id(), SettlementPolicyPlanner.inputs(level, town, craft, 1).getFirst().id()),
                    new SettlementSupplyPolicy.Connection(craft.id(), delivery.id(), SettlementPolicyPlanner.inputs(level, town, delivery, 1).getFirst().id()),
                    new SettlementSupplyPolicy.Connection(delivery.id(), null, need.supply().id()))), town.leader(), town::supplyChanged);
            helper.assertTrue(town.addSupplyPolicy(policy), "the exact timber, recipe and courier chain is approved once");
            policy.state(SettlementSupplyPolicy.State.ACTIVE, town.leader(), "", level.getGameTime());
            UUID productionOrder = null, transferOrder = null;
            boolean materialReload = false, constructionReload = false;
            for (int cycle = 0; cycle < 300 && town.projects().get(fixture.project().id()).status() != SettlementProject.Status.COMPLETED; cycle++) {
                finishPolicy(SettlementPolicyService.cycle(level, town));
                helper.assertValueEqual(policy.state(), SettlementSupplyPolicy.State.ACTIVE, "the approved construction chain remains valid: " + policy.blocker());
                var productionBinding = SettlementPolicyOrders.current(policy, craft);
                var deliveryBinding = SettlementPolicyOrders.current(policy, delivery);
                if (productionBinding != null) {
                    if (productionOrder == null) productionOrder = productionBinding.order();
                    helper.assertValueEqual(productionBinding.order(), productionOrder, "reevaluation preserves the shared recipe order");
                }
                if (deliveryBinding != null) {
                    if (transferOrder == null) transferOrder = deliveryBinding.order();
                    helper.assertValueEqual(deliveryBinding.order(), transferOrder, "reevaluation preserves the shared courier order");
                }
                SettlementTestPlots.planSupplies(level, town);
                SettlementBackgroundLogging.tick(level, town, town.workers().get(logger.id()), 200, 1.0D);
                SettlementTestPlots.planSupplies(level, town);
                SettlementBackgroundCrafting.tick(level, town, town.workers().get(craftsman.id()), 200, 1.0D);
                SettlementTestPlots.planSupplies(level, town);
                SettlementBackgroundCrafting.tick(level, town, town.workers().get(partner.id()), 200, 1.0D);
                if (!materialReload && count(helper, town, Items.OAK_PLANKS) > 0) {
                    town = SettlementRecord.load(town.save(level.registryAccess()), level.registryAccess(), () -> {});
                    policy = town.supplyPolicies().get(policy.id());
                    materialReload = true;
                }
                SettlementTestPlots.planSupplies(level, town);
                SettlementBackgroundTransfers.tick(level, town, town.workers().get(courier.id()), 200, 1.0D);
                SettlementTestPlots.planSupplies(level, town);
                SettlementBackgroundConstruction.tick(level, town, town.workers().get(fixture.worker().id()), 200, 1.0D);
                var project = town.projects().get(fixture.project().id());
                if (!constructionReload && project.placed() > 0 && project.placed() < 16) {
                    town = SettlementRecord.load(town.save(level.registryAccess()), level.registryAccess(), () -> {});
                    policy = town.supplyPolicies().get(policy.id());
                    constructionReload = true;
                }
                SettlementTestPlots.planSupplies(level, town);
                SettlementBackgroundConstruction.tick(level, town, town.workers().get(assistant.getUUID()), 200, 1.0D);
            }
            var project = town.projects().get(fixture.project().id());
            helper.assertValueEqual(project.status(), SettlementProject.Status.COMPLETED, "the configured chain supplies the full cooperative project");
            helper.assertTrue(materialReload && constructionReload, "both in-progress production and construction cross a save boundary");
            helper.assertValueEqual(project.placed(), 16, "the shared construction ledger credits sixteen unique placements");
            helper.assertValueEqual(project.site().placements().size(), 16, "each constructed block has one durable receipt");
            helper.assertValueEqual(town.productionQueues().get(workshop.id()).orders().size(), 1, "the configured recipe has one native order");
            helper.assertValueEqual(town.transferQueues().get(storage.id()).orders().size(), 1, "the configured courier has one native order");
            helper.assertValueEqual(town.productionQueues().get(workshop.id()).order(productionOrder).produced(), 16L, "the four harvested logs become exactly sixteen planks");
            helper.assertValueEqual(town.transferQueues().get(storage.id()).order(transferOrder).delivered(), 16L, "native delivery accounts for every constructed plank");
            helper.assertValueEqual(count(helper, town, Items.OAK_LOG), 0, "the approved recipe consumes all harvested logs once");
            helper.assertValueEqual(count(helper, town, Items.OAK_PLANKS), 0, "the crew consumes all delivered planks once");
            helper.assertValueEqual(count(helper, town, Items.DIAMOND_AXE), 0, "native logging breaks the worn original axe");
            helper.assertValueEqual(count(helper, town, Items.IRON_AXE), 1, "the approved replacement keeps one inventory owner");
            helper.assertTrue(town.workers().get(logger.id()).items(level.registryAccess()).get(HiredJobInventory.MAINHAND_SLOT).is(Items.IRON_AXE),
                    "native tool acquisition equips the replacement before finishing the tree");
            finishPolicy(SettlementPolicyService.cycle(level, town));
            helper.assertValueEqual(policy.state(), SettlementSupplyPolicy.State.COMPLETE, "finishing the requested project completes its coordinating policy");
            var returned = EntityType.VILLAGER.create(level);
            returned.setUUID(logger.id());
            helper.assertTrue(town.workers().get(logger.id()).restore(returned), "returning logging equipment follows the native handoff");
            helper.assertTrue(HiredJobInventory.getJobInventory(returned).getItem(HiredJobInventory.MAINHAND_SLOT).is(Items.IRON_AXE),
                    "the returning worker owns the same replacement axe");
            helper.assertFalse(town.workers().get(logger.id()).restore(returned), "the completed equipment handoff cannot replay");
            for (var chunk : chunks) SettlementForestService.observeArea(level, town, forest, chunk);
            helper.assertTrue(chunks.stream().filter(chunk -> chunk.getPos().equals(new ChunkPos(root))).findFirst().orElseThrow()
                    .getBlockState(root).is(Blocks.OAK_SAPLING), "return applies the native tree removal and replanting receipt");
            helper.assertFalse(level.hasChunkAt(root) || level.hasChunkAt(fixture.origin()), "the integrated chain never force-loads its worksites");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "supply_chain")
    public static void configuredMineSmeltsGuardEquipmentAndReplenishesConsumedAmmunitionAcrossReloads(GameTestHelper helper) {
        SettlementMiningWorkGameTests.fixture(helper, fixture -> {
            var level = helper.getLevel();
            var town = fixture.town();
            var raw = fixture.output();
            raw.configure(SettlementDepotPurpose.GENERAL, 5);
            var supply = fixture.supply();
            supply.setItem(0, new ItemStack(Items.COAL, 2));
            supply.setItem(1, new ItemStack(Items.OAK_PLANKS, 6));
            supply.setItem(2, new ItemStack(Items.STICK, 14));
            supply.setItem(3, new ItemStack(Items.FLINT, 14));
            supply.setItem(4, new ItemStack(Items.FEATHER, 14));
            town.setWorksitePlan(town.leader(), fixture.area().id(), new SettlementWorksitePlan(
                    SettlementWorksitePlan.defaults(fixture.area().type().role()).settings(), 1,
                    new SettlementWorksiteLinks(Set.of(supply.id()), Set.of(raw.id()), Set.of())));
            var finished = depot(town, town.areas().get(supply.area()), supply.position().east(2), SettlementDepotPurpose.GENERAL);
            var smeltery = area(helper, town, SettlementAreaType.SMELTERY, "Iron furnace", supply.position().south(4), supply.position().offset(2, 2, 6));
            town.setWorksitePlan(town.leader(), smeltery.id(), new SettlementWorksitePlan(
                    SettlementWorksitePlan.defaults(smeltery.type().role()).settings(), 1,
                    new SettlementWorksiteLinks(Set.of(supply.id(), raw.id()), Set.of(raw.id()), Set.of())));
            var furnace = new net.minecraft.world.level.block.entity.FurnaceBlockEntity(smeltery.min(), Blocks.FURNACE.defaultBlockState());
            town.recordFurnace(SettlementFurnaceState.capture(level, smeltery.id(), furnace, 0));
            var smelter = worker(helper, town, smeltery, smeltery.min(), Items.AIR);
            var originalSmelter = smelter.id();
            var workshop = area(helper, town, SettlementAreaType.WORKSHOP, "Guard workshop", supply.position().north(4), supply.position().offset(2, 2, -2));
            town.setWorksitePlan(town.leader(), workshop.id(), new SettlementWorksitePlan(
                    SettlementWorksitePlan.defaults(workshop.type().role()).settings(), 1,
                    new SettlementWorksiteLinks(Set.of(supply.id(), raw.id()), Set.of(finished.id()), Set.of())));
            table(town, workshop);
            var craftsman = worker(helper, town, workshop, workshop.min(), Items.AIR);
            var post = area(helper, town, SettlementAreaType.GUARD_POST, "Guard post", supply.position().east(5), supply.position().offset(7, 2, 2));
            town.setWorksitePlan(town.leader(), post.id(), new SettlementWorksitePlan(
                    SettlementWorksitePlan.defaults(post.type().role()).settings(), 1,
                    new SettlementWorksiteLinks(Set.of(finished.id()), Set.of(finished.id()), Set.of())));
            var guard = worker(helper, town, post, post.min(), Items.CROSSBOW);
            var guardItems = guard.items(level.registryAccess());
            var crossbow = guardItems.get(HiredJobInventory.MAINHAND_SLOT);
            crossbow.setDamageValue(crossbow.getMaxDamage() - 1);
            crossbow.set(net.minecraft.core.component.DataComponents.CHARGED_PROJECTILES,
                    net.minecraft.world.item.component.ChargedProjectiles.of(new ItemStack(Items.TIPPED_ARROW)));
            crossbow.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Town crossbow"));
            var originalCrossbow = crossbow.copy();
            guard.updateInventory(level.registryAccess(), guardItems);
            var equipment = new SettlementEquipmentPlan.Configuration(List.of(
                    new SettlementEquipmentPlan.Selection(HiredEquipmentNeed.Kind.CROSSBOW, 0, true, false),
                    new SettlementEquipmentPlan.Selection(HiredEquipmentNeed.Kind.SHIELD, 0, true, false),
                    new SettlementEquipmentPlan.Selection(HiredEquipmentNeed.Kind.AMMUNITION, 32, false, false)), 7, true);
            helper.assertTrue(town.setEquipmentPlan(town.leader(), post.id(), equipment, -1), "the guard requests a shield and a 32-projectile reserve");
            var gather = new SettlementSupplyPolicy.Step(UUID.randomUUID(), SettlementSupplyPolicy.StepKind.GATHER,
                    fixture.area().id(), null, new ItemStack(Items.RAW_IRON), null, raw.position());
            var smelt = new SettlementSupplyPolicy.Step(UUID.randomUUID(), SettlementSupplyPolicy.StepKind.PRODUCTION, smeltery.id(),
                    HiredProductionRecipe.exact(ResourceLocation.withDefaultNamespace("iron_ingot_from_smelting_raw_iron"), Map.of()),
                    ItemStack.EMPTY, null, raw.position());
            var shield = new SettlementSupplyPolicy.Step(UUID.randomUUID(), SettlementSupplyPolicy.StepKind.PRODUCTION, workshop.id(),
                    HiredProductionRecipe.exact(ResourceLocation.withDefaultNamespace("shield"), Map.of()), ItemStack.EMPTY, null, finished.position());
            var arrows = new SettlementSupplyPolicy.Step(UUID.randomUUID(), SettlementSupplyPolicy.StepKind.PRODUCTION, workshop.id(),
                    HiredProductionRecipe.exact(ResourceLocation.withDefaultNamespace("arrow"), Map.of()), ItemStack.EMPTY, null, finished.position());
            var needs = SettlementDemandService.inspect(level, town).demands().stream()
                    .filter(row -> row.consumer().equals(guard.id())).toList();
            var shieldNeed = needs.stream().filter(row -> row.requirement().value(new ItemStack(Items.SHIELD)) > 0).findFirst().orElseThrow();
            var ammunitionNeed = needs.stream().filter(row -> row.requirement().value(new ItemStack(Items.ARROW)) > 0).findFirst().orElseThrow();
            var rawInput = SettlementPolicyPlanner.inputs(level, town, smelt, 1).stream()
                    .filter(input -> input.requirement().value(new ItemStack(Items.RAW_IRON)) > 0).findFirst().orElseThrow();
            var ironInput = SettlementPolicyPlanner.inputs(level, town, shield, 1).stream()
                    .filter(input -> input.requirement().value(new ItemStack(Items.IRON_INGOT)) > 0).findFirst().orElseThrow();
            var policy = new SettlementSupplyPolicy(UUID.randomUUID(), new SettlementSupplyPolicy.Configuration("Guard supplies",
                    new SettlementSupplyPolicy.Target(SettlementSupplyPolicy.TargetKind.EQUIPMENT, guard.id(), null, null, ItemStack.EMPTY, 0),
                    7, List.of(gather, smelt, shield, arrows), List.of(
                    new SettlementSupplyPolicy.Connection(gather.id(), smelt.id(), rawInput.id()),
                    new SettlementSupplyPolicy.Connection(smelt.id(), shield.id(), ironInput.id()),
                    new SettlementSupplyPolicy.Connection(shield.id(), null, shieldNeed.supply().id()),
                    new SettlementSupplyPolicy.Connection(arrows.id(), null, ammunitionNeed.supply().id()))), town.leader(), town::supplyChanged);
            helper.assertTrue(town.addSupplyPolicy(policy), "mining, smelting and both exact equipment recipes are configured once");
            policy.state(SettlementSupplyPolicy.State.ACTIVE, town.leader(), "", level.getGameTime());
            var bindings = new LinkedHashMap<UUID, UUID>();
            boolean furnaceReload = false;
            int consumed = 0, ammunitionReloads = 0;
            for (long now = 200; now <= 120_000; now += 200) {
                level.setDayTime(6_000L + now);
                SettlementBackgroundService.update(level, town, now);
                helper.assertValueEqual(policy.state(), SettlementSupplyPolicy.State.ACTIVE, "equipment upkeep remains active: " + policy.blocker());
                for (var step : List.of(smelt, shield, arrows)) {
                    var binding = SettlementPolicyOrders.current(policy, step);
                    if (binding == null) continue;
                    var original = bindings.putIfAbsent(step.id(), binding.order());
                    if (original != null) helper.assertValueEqual(binding.order(), original, "every configured recipe retains its native order through reloads");
                }
                if (!furnaceReload && town.furnaces().get(smeltery.min()).items(level.registryAccess()).getFirst().is(Items.RAW_IRON)) {
                    helper.assertTrue(town.productionQueues().get(smeltery.id()).allocations().stream().anyMatch(value -> value.started()),
                            "the reload interrupts a real started furnace receipt");
                    var casualty = town.residents().get(smelter.id()).context(level);
                    smelter.restore(casualty);
                    casualty.remove(net.minecraft.world.entity.Entity.RemovalReason.KILLED);
                    SettlementBackgroundService.onUnloaded(casualty);
                    helper.assertFalse(town.workers().containsKey(originalSmelter), "native death cleanup releases the original furnace worker");
                    town.removeDuty(originalSmelter);
                    town.removeResident(originalSmelter);
                    com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData.get(level).removeResident(town.id(), originalSmelter);
                    smelter = worker(helper, town, smeltery, smeltery.min(), Items.AIR);
                    town = SettlementRecord.load(town.save(level.registryAccess()), level.registryAccess(), () -> {});
                    policy = town.supplyPolicies().get(policy.id());
                    furnaceReload = true;
                }
                var savedGuard = town.workers().get(guard.id());
                var owner = savedGuard.context(level, town);
                if (!owner.getOffhandItem().is(Items.SHIELD) || HiredRangedAmmo.carriedAmmo(owner) != 32) continue;
                if (ammunitionReloads == 3) break;
                var inventory = HiredJobInventory.getJobInventory(owner);
                helper.assertValueEqual(inventory.consumeSupply(HiredRangedAmmo::isAmmo, 7), 7, "native ammunition use consumes seven owned projectiles");
                consumed += 7;
                savedGuard.inventorySnapshot(inventory.captureSnapshot());
                savedGuard.changed();
                town = SettlementRecord.load(town.save(level.registryAccess()), level.registryAccess(), () -> {});
                policy = town.supplyPolicies().get(policy.id());
                ammunitionReloads++;
            }
            var returnedGuard = town.workers().get(guard.id());
            var owner = returnedGuard.context(level, town);
            helper.assertTrue(furnaceReload && ammunitionReloads == 3, "the complete chain survives a smelter death, a started-batch reload and three ammunition reloads"
                    + ", furnaceReload=" + furnaceReload + ", ammunitionReloads=" + ammunitionReloads
                    + ", miner=" + town.workers().get(fixture.worker().id()).status() + ", smelter=" + town.workers().get(smelter.id()).status()
                    + ", craftsman=" + town.workers().get(craftsman.id()).status() + ", guard=" + returnedGuard.status()
                    + ", smelted=" + town.productionQueues().get(smeltery.id()).order(bindings.get(smelt.id())).produced()
                    + ", iron=" + count(helper, town, Items.IRON_INGOT) + ", shield=" + count(helper, town, Items.SHIELD));
            helper.assertTrue(owner.getOffhandItem().is(Items.SHIELD) && HiredRangedAmmo.carriedAmmo(owner) == 32,
                    "the guard returns equipped with replenished ammunition, miner=" + town.workers().get(fixture.worker().id()).status()
                            + ", smelter=" + town.workers().get(smelter.id()).status() + ", craftsman=" + town.workers().get(craftsman.id()).status());
            helper.assertTrue(ItemStack.isSameItemSameComponents(owner.getMainHandItem(), originalCrossbow),
                    "upkeep preserves the charged usable crossbow and its exact components");
            helper.assertValueEqual(town.productionQueues().get(smeltery.id()).orders().size(), 1, "smelting reevaluation creates no duplicate order");
            helper.assertValueEqual(town.productionQueues().get(workshop.id()).orders().size(), 2, "shield and ammunition each keep one order");
            helper.assertValueEqual(town.productionQueues().get(smeltery.id()).order(bindings.get(smelt.id())).produced(), 1L,
                    "one mined ore is smelted for the requested shield");
            helper.assertValueEqual(town.productionQueues().get(workshop.id()).order(bindings.get(shield.id())).produced(), 1L,
                    "the shield is crafted once");
            long producedArrows = town.productionQueues().get(workshop.id()).order(bindings.get(arrows.id())).produced();
            helper.assertValueEqual(producedArrows, 56L, "four-arrow recipe batches cover the initial reserve and 21 consumed projectiles");
            helper.assertValueEqual((long) count(helper, town, Items.ARROW) + consumed, producedArrows,
                    "all produced ammunition has exactly one remaining or consumed owner");
            helper.assertValueEqual(count(helper, town, Items.SHIELD), 1, "only the equipped shield remains");
            helper.assertValueEqual(count(helper, town, Items.IRON_INGOT), 0, "the shield consumes its real smelted ingot");
            helper.assertValueEqual(count(helper, town, Items.OAK_PLANKS), 0, "shield crafting consumes six approved planks");
            helper.assertValueEqual(count(helper, town, Items.STICK) + count(helper, town, Items.FLINT) + count(helper, town, Items.FEATHER), 0,
                    "fourteen native ammunition crafts consume exactly the finite ingredients");
            helper.assertValueEqual(count(helper, town, Items.COAL), 1, "native furnace accounting burns one coal");
            var mine = town.mines().get(fixture.area().id());
            int unmined = 0;
            for (int index = 0; index < 3; index++) if (mine.getBlockState(fixture.ore().east(index)).is(Blocks.IRON_ORE)) unmined++;
            helper.assertValueEqual(unmined + count(helper, town, Items.RAW_IRON) + 1, 3, "mined and unmined ore conserve the original vein");
            SettlementMineService.observeArea(level, town, fixture.area(), fixture.chunk());
            for (int index = 0; index < 3; index++)
                helper.assertValueEqual(fixture.chunk().getBlockState(fixture.ore().east(index)), mine.getBlockState(fixture.ore().east(index)),
                        "the returning terrain receives each saved mining receipt");
            var returned = EntityType.VILLAGER.create(level);
            returned.setUUID(guard.id());
            helper.assertTrue(returnedGuard.restore(returned), "the native guard handoff restores equipment and ammunition");
            helper.assertTrue(returned.getOffhandItem().is(Items.SHIELD) && HiredRangedAmmo.carriedAmmo(returned) == 32,
                    "the loaded guard owns one shield and the full ammunition reserve");
            helper.assertTrue(ItemStack.isSameItemSameComponents(returned.getMainHandItem(), originalCrossbow),
                    "loaded handoff preserves the original crossbow");
            helper.assertFalse(returnedGuard.restore(returned), "the guard handoff cannot duplicate items");
            helper.assertFalse(level.hasChunkAt(fixture.ore()) || level.hasChunkAt(smeltery.min()), "the configured chain never force-loads its worksites");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "supply_chain")
    public static void essentialsThenUrgentConstructionThenOptionalReservesLeaveOnlySurplusForExport(GameTestHelper helper) {
        SettlementBackgroundConstructionGameTests.fixture(helper, false, Map.of(BlockPos.ZERO, Blocks.HAY_BLOCK.defaultBlockState()), fixture -> {
            var level = helper.getLevel();
            long dayTime = level.getDayTime();
            try {
                level.setDayTime(6_000);
                var town = fixture.town();
                town.advance();
                var raw = fixture.depot();
                raw.configure(SettlementDepotPurpose.GENERAL, 5);
                var storage = town.areas().get(raw.area());
                var food = depot(town, storage, raw.position().east(), SettlementDepotPurpose.FOOD);
                var optional = depot(town, storage, raw.position().east(2), SettlementDepotPurpose.OUTPUT);
                var kitchen = area(helper, town, SettlementAreaType.KITCHEN, "Meals", raw.position().north(4), raw.position().offset(2, 2, -2));
                town.setWorksitePlan(town.leader(), kitchen.id(), new SettlementWorksitePlan(
                        SettlementWorksitePlan.defaults(kitchen.type().role()).settings(), 1,
                        new SettlementWorksiteLinks(Set.of(raw.id()), Set.of(food.id()), Set.of())));
                table(town, kitchen);
                var cook = worker(helper, town, kitchen, kitchen.min(), Items.AIR);
                var workshop = area(helper, town, SettlementAreaType.WORKSHOP, "Project supplies", raw.position().south(4), raw.position().offset(2, 2, 6));
                town.setWorksitePlan(town.leader(), workshop.id(), new SettlementWorksitePlan(
                        SettlementWorksitePlan.defaults(workshop.type().role()).settings(), 1,
                        new SettlementWorksiteLinks(Set.of(raw.id()), Set.of(raw.id()), Set.of())));
                table(town, workshop);
                var craftsman = worker(helper, town, workshop, workshop.min(), Items.AIR);
                var bakery = area(helper, town, SettlementAreaType.KITCHEN, "Optional reserve", raw.position().east(6), raw.position().offset(8, 2, 2));
                town.setWorksitePlan(town.leader(), bakery.id(), new SettlementWorksitePlan(
                        SettlementWorksitePlan.defaults(bakery.type().role()).settings(), 1,
                        new SettlementWorksiteLinks(Set.of(raw.id()), Set.of(optional.id()), Set.of())));
                table(town, bakery);
                var baker = worker(helper, town, bakery, bakery.min(), Items.AIR);
                var market = area(helper, town, SettlementAreaType.TRADING_POST, "Market", fixture.origin().north(10), fixture.origin().offset(2, 2, -8));
                town.recordSurvey(market.id(), new SettlementBuildingSurvey.Survey(0, List.of(), List.of(market.min()), false,
                        Map.of(market.min(), ResourceLocation.withDefaultNamespace("lectern"))));
                var clerk = EntityType.VILLAGER.create(level);
                clerk.setPos(market.min().getCenter());
                clerk.setVillagerData(clerk.getVillagerData().setProfession(VillagerProfession.NITWIT));
                VillageAllegianceApi.assignKnown(level, clerk, town.id(), AllegianceAssignmentSource.EXPLICIT_API);
                town.assignDuty(clerk.getUUID(), market.id());
                town.recordResident(SettlementResidentState.capture(clerk, town, 0));
                var sale = new SettlementTradeOrder(UUID.randomUUID(), SettlementTradeOrder.Direction.SELL,
                        ResourceLocation.withDefaultNamespace("wheat"), 0, com.jvn.villagerretaliation.sell.CurrencyAmount.ZERO,
                        com.jvn.villagerretaliation.sell.CurrencyAmount.of(100, 1), true);
                helper.assertTrue(town.economy().putOrder(sale), "the standing export order is configured before shortages");
                var foodNeed = SettlementDemandService.inspect(level, town).demands().stream()
                        .filter(row -> row.kind() == com.jvn.villagerretaliation.interaction.work.production.WorkSupplyDemand.Kind.FOOD).findFirst().orElseThrow();
                int foodLoaves = (int) ((foodNeed.requirement().quantity() + 4) / 5);
                var mealStep = new SettlementSupplyPolicy.Step(UUID.randomUUID(), SettlementSupplyPolicy.StepKind.PRODUCTION, kitchen.id(),
                        HiredProductionRecipe.exact(ResourceLocation.withDefaultNamespace("bread"), Map.of()), ItemStack.EMPTY, null, food.position());
                var foodPolicy = new SettlementSupplyPolicy(UUID.randomUUID(), new SettlementSupplyPolicy.Configuration("Essential meals",
                        new SettlementSupplyPolicy.Target(SettlementSupplyPolicy.TargetKind.FOOD, town.id().value(), foodNeed.supply().id(), null, ItemStack.EMPTY, 0),
                        0, List.of(mealStep), List.of(new SettlementSupplyPolicy.Connection(mealStep.id(), null, foodNeed.supply().id()))),
                        town.leader(), town::supplyChanged);
                var projectNeed = SettlementDemandService.inspect(level, town).demands().stream()
                        .filter(row -> row.consumer().equals(fixture.project().id())).findFirst().orElseThrow();
                var projectStep = new SettlementSupplyPolicy.Step(UUID.randomUUID(), SettlementSupplyPolicy.StepKind.PRODUCTION, workshop.id(),
                        HiredProductionRecipe.exact(ResourceLocation.withDefaultNamespace("hay_block"), Map.of()), ItemStack.EMPTY, null, raw.position());
                var projectPolicy = new SettlementSupplyPolicy(UUID.randomUUID(), new SettlementSupplyPolicy.Configuration("Urgent construction",
                        new SettlementSupplyPolicy.Target(SettlementSupplyPolicy.TargetKind.CONSUMER, fixture.project().id(), projectNeed.supply().id(), null, ItemStack.EMPTY, 0),
                        9, List.of(projectStep), List.of(new SettlementSupplyPolicy.Connection(projectStep.id(), null, projectNeed.supply().id()))),
                        town.leader(), town::supplyChanged);
                var reserveId = UUID.randomUUID();
                var reserveNeed = com.jvn.villagerretaliation.interaction.work.production.WorkSupplyDemand.identity(level.registryAccess(), reserveId,
                        com.jvn.villagerretaliation.interaction.work.production.WorkSupplyDemand.Kind.STOCK_RESERVE,
                        com.jvn.villagerretaliation.interaction.work.production.WorkSupplyRequirement.exact("policy_stock", new ItemStack(Items.BREAD), 2));
                var reserveStep = new SettlementSupplyPolicy.Step(UUID.randomUUID(), SettlementSupplyPolicy.StepKind.PRODUCTION, bakery.id(),
                        HiredProductionRecipe.exact(ResourceLocation.withDefaultNamespace("bread"), Map.of()), ItemStack.EMPTY, null, optional.position());
                var reservePolicy = new SettlementSupplyPolicy(reserveId, new SettlementSupplyPolicy.Configuration("Optional bread",
                        new SettlementSupplyPolicy.Target(SettlementSupplyPolicy.TargetKind.STOCK, null, null, optional.position(), new ItemStack(Items.BREAD), 2),
                        1, List.of(reserveStep), List.of(new SettlementSupplyPolicy.Connection(reserveStep.id(), null, reserveNeed))),
                        town.leader(), town::supplyChanged);
                for (var policy : List.of(foodPolicy, projectPolicy, reservePolicy)) {
                    helper.assertTrue(town.addSupplyPolicy(policy), "all competing consumers are configured before work starts");
                    policy.state(SettlementSupplyPolicy.State.ACTIVE, town.leader(), "", level.getGameTime());
                }
                raw.setItem(0, new ItemStack(Items.WHEAT, 9));
                finishPolicy(SettlementPolicyService.cycle(level, town));
                var foodOrder = SettlementPolicyOrders.current(foodPolicy, mealStep).order();
                var projectOrder = SettlementPolicyOrders.current(projectPolicy, projectStep).order();
                var optionalOrder = SettlementPolicyOrders.current(reservePolicy, reserveStep).order();
                var initial = SettlementDemandService.inspect(level, town);
                var wheatRows = initial.demands().stream().filter(row -> row.requirement().value(new ItemStack(Items.WHEAT)) > 0).toList();
                helper.assertValueEqual(wheatRows.stream().filter(row -> row.consumer().equals(foodOrder)).findFirst().orElseThrow().supply().availableNow(),
                        9L, "protected food receives scarce wheat despite its priority zero");
                helper.assertValueEqual(wheatRows.stream().filter(row -> row.consumer().equals(projectOrder) || row.consumer().equals(optionalOrder))
                        .mapToLong(row -> row.supply().availableNow()).sum(), 0L, "urgent and optional work cannot take protected meal inputs");
                helper.assertValueEqual(initial.sellable(raw, new ItemStack(Items.WHEAT)), 0, "exports cannot take the essential allocation");
                int phase = 0, exported = 0, deliveredWheat = 9;
                boolean reloaded = false;
                for (long now = 200; now <= 80_000; now += 200) {
                    finishPolicy(SettlementPolicyService.cycle(level, town));
                    SettlementTestPlots.planSupplies(level, town);
                    SettlementBackgroundCrafting.tick(level, town, town.workers().get(baker.id()), 200, 1.0D);
                    SettlementTestPlots.planSupplies(level, town);
                    SettlementBackgroundCrafting.tick(level, town, town.workers().get(craftsman.id()), 200, 1.0D);
                    SettlementTestPlots.planSupplies(level, town);
                    SettlementBackgroundConstruction.tick(level, town, town.workers().get(fixture.worker().id()), 200, 1.0D);
                    var result = SettlementTradeService.process(level, town, now).get(sale.id());
                    if (result != null) exported += result.count();
                    SettlementTestPlots.planSupplies(level, town);
                    SettlementBackgroundCrafting.tick(level, town, town.workers().get(cook.id()), 200, 1.0D);
                    var currentFood = town.depots().get(food.id());
                    var currentOptional = town.depots().get(optional.id());
                    if (phase < 2) {
                        helper.assertValueEqual(exported, 0, "exports stay blocked while all arrived wheat belongs to consumers");
                        helper.assertTrue(currentOptional.isEmpty(), "running the optional baker first cannot steal higher-ranked inputs");
                    }
                    if (phase == 0 && currentFood.count(stack -> stack.is(Items.BREAD)) == 3) {
                        helper.assertValueEqual(town.projects().get(fixture.project().id()).placed(), 0, "essential food finishes before urgent construction gets scarce material");
                        town = SettlementRecord.load(town.save(level.registryAccess()), level.registryAccess(), () -> {});
                        reloaded = true;
                        int arrival = (foodLoaves - 3) * 3 + 9;
                        helper.assertTrue(town.depots().get(raw.id()).deposit(new ItemStack(Items.WHEAT, arrival)).isEmpty(),
                                "the next finite shipment enters real shared storage");
                        deliveredWheat += arrival;
                        phase = 1;
                    } else if (phase == 1 && currentFood.count(stack -> stack.is(Items.BREAD)) == foodLoaves
                            && town.projects().get(fixture.project().id()).status() == SettlementProject.Status.COMPLETED) {
                        helper.assertTrue(town.depots().get(raw.id()).deposit(new ItemStack(Items.WHEAT, 14)).isEmpty(),
                                "a final finite shipment covers the optional reserve and eight surplus items");
                        deliveredWheat += 14;
                        phase = 2;
                    } else if (phase == 2 && currentOptional.count(stack -> stack.is(Items.BREAD)) == 2 && exported == 8) break;
                }
                helper.assertTrue(reloaded && phase == 2, "food and urgent construction complete after the intermediate reload");
                helper.assertValueEqual(town.projects().get(fixture.project().id()).placed(), 1, "the urgent project consumes one real crafted hay bale");
                helper.assertValueEqual(town.productionQueues().get(kitchen.id()).order(foodOrder).produced(), (long) foodLoaves,
                        "essential production stops at the shared nutrition reserve");
                helper.assertValueEqual(town.productionQueues().get(workshop.id()).order(projectOrder).produced(), 1L, "the construction intermediate is crafted once");
                helper.assertValueEqual(town.productionQueues().get(bakery.id()).order(optionalOrder).produced(), 2L, "optional production completes after its higher-ranked consumers");
                helper.assertValueEqual(town.depots().get(optional.id()).count(stack -> stack.is(Items.BREAD)), 2, "the optional reserve reaches its approved endpoint");
                helper.assertValueEqual(exported, 8, "only the eight genuine surplus wheat reach the market");
                helper.assertValueEqual(count(helper, town, Items.WHEAT), 0, "every arrived wheat is consumed or exported once");
                helper.assertValueEqual(count(helper, town, Items.BREAD) * 3 + 9 + exported, deliveredWheat,
                        "food, optional stock, construction and trade conserve every delivered ingredient");
                helper.assertTrue(SettlementFoodService.nutrition(town) >= SettlementFoodService.reserveNutrition(level, town),
                        "exports leave the protected nutrition reserve intact");
                helper.assertTrue(town.economy().balance().compareTo(com.jvn.villagerretaliation.sell.CurrencyAmount.ZERO) > 0
                        && town.economy().spent().isZero(), "exports credit native proceeds without purchasing stock");
                for (var area : List.of(kitchen, workshop, bakery))
                    helper.assertValueEqual(town.productionQueues().get(area.id()).orders().size(), 1, "each configured consumer retains one managed order");
                helper.assertFalse(level.hasChunkAt(fixture.origin()), "competition and recovery never force-load the town");
            } finally {
                level.setDayTime(dayTime);
            }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "supply_chain")
    public static void enrolledWorkerCompletesNativeChainWithAssignmentClothingAndProtectedDuties(GameTestHelper helper) {
        long dayTime = helper.getLevel().getDayTime();
        try {
            var fixture = SettlementWorkerPoolAssignmentGameTests.ready(helper, 3);
            var level = helper.getLevel();
            var town = fixture.town();
            town.setManualManagement(town.leader(), true);
            var root = town.center().west(8);
            SettlementTestPlots.claim(level, town, root.offset(-3, -1, -3), root.offset(3, 5, 3));
            var forest = new SettlementArea(fixture.farm().id(), SettlementAreaType.FORESTRY, "Forest",
                    root.offset(-3, -1, -3), root.offset(3, 5, 3), 1, true);
            helper.assertTrue(town.putArea(town.leader(), forest), "the enrolled crew has an observed forestry worksite");
            var source = depot(town, fixture.stores(), fixture.stores().min(), SettlementDepotPurpose.GENERAL);
            var destination = depot(town, fixture.stores(), fixture.stores().min().east(), SettlementDepotPurpose.GENERAL);
            source.setItem(0, new ItemStack(Items.DIAMOND_AXE));
            source.setItem(1, new ItemStack(Items.OAK_SAPLING));
            town.setWorksitePlan(town.leader(), forest.id(), new SettlementWorksitePlan(
                    SettlementWorksitePlan.defaults(forest.type().role()).settings(), 1,
                    new SettlementWorksiteLinks(Set.of(source.id()), Set.of(source.id()), Set.of())));
            var chunks = forest(helper, town, forest, root);
            var workshop = area(helper, town, SettlementAreaType.WORKSHOP, "Workshop", town.center().east(4), town.center().offset(6, 2, 2));
            town.setWorksitePlan(town.leader(), workshop.id(), new SettlementWorksitePlan(
                    SettlementWorksitePlan.defaults(workshop.type().role()).settings(), 1,
                    new SettlementWorksiteLinks(Set.of(source.id()), Set.of(source.id()), Set.of())));
            table(town, workshop);
            var post = area(helper, town, SettlementAreaType.GUARD_POST, "Pinned duty", town.center().south(6), town.center().offset(2, 2, 8));
            var resident = fixture.residents().getFirst();
            resident.setPos(root.west(2).getBottomCenter());
            town.recordResident(SettlementResidentState.capture(resident, town, level.getGameTime()));
            var pinned = fixture.residents().get(1);
            SettlementWorkerPoolAssignmentGameTests.staff(helper, town, pinned, post);
            town.rememberManualDuty(pinned.getUUID());
            var unenrolled = fixture.residents().getLast();
            var gather = new SettlementSupplyPolicy.Step(UUID.randomUUID(), SettlementSupplyPolicy.StepKind.GATHER,
                    forest.id(), null, new ItemStack(Items.OAK_LOG), null, source.position());
            var craft = new SettlementSupplyPolicy.Step(UUID.randomUUID(), SettlementSupplyPolicy.StepKind.PRODUCTION, workshop.id(),
                    HiredProductionRecipe.exact(ResourceLocation.withDefaultNamespace("oak_planks"), Map.of()), ItemStack.EMPTY, null, source.position());
            var delivery = new SettlementSupplyPolicy.Step(UUID.randomUUID(), SettlementSupplyPolicy.StepKind.DELIVERY, fixture.stores().id(),
                    null, new ItemStack(Items.OAK_PLANKS), source.position(), destination.position());
            var policyId = UUID.randomUUID();
            var requirement = com.jvn.villagerretaliation.interaction.work.production.WorkSupplyDemand.identity(level.registryAccess(), policyId,
                    com.jvn.villagerretaliation.interaction.work.production.WorkSupplyDemand.Kind.STOCK_RESERVE,
                    com.jvn.villagerretaliation.interaction.work.production.WorkSupplyRequirement.exact("policy_stock", new ItemStack(Items.OAK_PLANKS), 16));
            var policy = new SettlementSupplyPolicy(policyId, new SettlementSupplyPolicy.Configuration("Pooled timber",
                    new SettlementSupplyPolicy.Target(SettlementSupplyPolicy.TargetKind.STOCK, null, null, destination.position(), new ItemStack(Items.OAK_PLANKS), 16),
                    9, List.of(gather, craft, delivery), List.of(
                    new SettlementSupplyPolicy.Connection(gather.id(), craft.id(), SettlementPolicyPlanner.inputs(level, town, craft, 1).getFirst().id()),
                    new SettlementSupplyPolicy.Connection(craft.id(), delivery.id(), SettlementPolicyPlanner.inputs(level, town, delivery, 1).getFirst().id()),
                    new SettlementSupplyPolicy.Connection(delivery.id(), null, requirement))), town.leader(), town::supplyChanged);
            helper.assertTrue(town.addSupplyPolicy(policy), "the complete gather, craft and deliver chain is approved once");
            policy.state(SettlementSupplyPolicy.State.ACTIVE, town.leader(), "", level.getGameTime());
            for (int cycle = 0; cycle < 4; cycle++) finishPolicy(SettlementPolicyService.cycle(level, town));
            var productionOrder = SettlementPolicyOrders.current(policy, craft).order();
            var transferOrder = SettlementPolicyOrders.current(policy, delivery).order();
            var poolId = UUID.randomUUID();
            helper.assertTrue(town.configureWorkerPool(town.leader(), poolId, -1, new SettlementWorkerPool.Configuration("Supply crew",
                    List.of(resident.getUUID(), pinned.getUUID()), List.of(
                    new SettlementWorkerPool.Staffing(forest.id(), 0, 1),
                    new SettlementWorkerPool.Staffing(workshop.id(), 0, 1),
                    new SettlementWorkerPool.Staffing(fixture.stores().id(), 0, 1))), true), "the player authorizes one worker per chain stage");
            var appearances = new java.util.LinkedHashSet<VillagerProfession>();
            int previousMoves = 0;
            long start = level.getGameTime();
            for (long now = start + 200; now <= start + 100_000; now += 200) {
                finishPolicy(SettlementPolicyService.cycle(level, town));
                SettlementWorkerPoolSchedulingGameTests.cycle(helper, town, now);
                var pool = town.workerPools().get(poolId);
                helper.assertTrue(pool.history().size() - previousMoves <= 1, "a pool performs at most one reassignment per cycle");
                previousMoves = pool.history().size();
                helper.assertValueEqual(town.duties().get(pinned.getUUID()), post.id(), "enrollment never overrides the manual guard assignment");
                helper.assertFalse(town.duties().containsKey(unenrolled.getUUID()), "shortages never recruit an unenrolled resident");
                for (var site : pool.configuration().worksites())
                    helper.assertTrue(town.duties().values().stream().filter(site.area()::equals).count() <= site.maximum(), "every staffing cap remains authoritative");
                var worker = town.workers().get(resident.getUUID());
                if (worker != null) {
                    var context = town.residents().get(resident.getUUID()).context(level);
                    SettlementWorkforceService.reconcile(level, context);
                    var outfit = com.jvn.villagerretaliation.interaction.VillagerWorkAppearance.profession(context);
                    var expected = worker.area().equals(forest.id()) ? VillagerProfession.FLETCHER
                            : worker.area().equals(workshop.id()) ? VillagerProfession.TOOLSMITH : VillagerProfession.CARTOGRAPHER;
                    helper.assertValueEqual(outfit, expected, "the native assignment updates the outfit at every staffing transition");
                    helper.assertValueEqual(context.getVillagerData().getProfession(), VillagerProfession.TOOLSMITH,
                            "staffing and clothing preserve the actual trading profession");
                    appearances.add(outfit);
                }
                SettlementBackgroundService.update(level, town, now);
                if (destination.count(stack -> stack.is(Items.OAK_PLANKS)) == 16) break;
            }
            helper.assertValueEqual(destination.count(stack -> stack.is(Items.OAK_PLANKS)), 16,
                    "one enrolled worker harvests, crafts and delivers the complete configured chain, history=" + town.workerPools().get(poolId).history()
                            + ", worker=" + (town.workers().get(resident.getUUID()) == null ? "unassigned" : town.workers().get(resident.getUUID()).status()));
            var moves = town.workerPools().get(poolId).history();
            helper.assertValueEqual(moves.stream().map(SettlementWorkerPool.Move::to).toList(), List.of(forest.id(), workshop.id(), fixture.stores().id()),
                    "completed demand moves the same worker through the three required stages without oscillation");
            helper.assertTrue(moves.stream().allMatch(move -> move.resident().equals(resident.getUUID())), "only the unpinned enrolled resident is reassigned");
            for (int index = 1; index < moves.size(); index++)
                helper.assertTrue(moves.get(index).at() - moves.get(index - 1).at() >= 1_200, "actual staffing transitions respect the persisted cooldown");
            helper.assertValueEqual(appearances, Set.of(VillagerProfession.FLETCHER, VillagerProfession.TOOLSMITH, VillagerProfession.CARTOGRAPHER),
                    "the complete native chain publishes all three assignment outfits");
            helper.assertValueEqual(town.productionQueues().get(workshop.id()).order(productionOrder).produced(), 16L, "native crafting consumes the four harvested logs");
            helper.assertValueEqual(town.transferQueues().get(fixture.stores().id()).order(transferOrder).delivered(), 16L, "native courier receipts account for all planks");
            helper.assertValueEqual(count(helper, town, Items.OAK_LOG), 0, "staffing changes leave no duplicated harvest");
            helper.assertValueEqual(count(helper, town, Items.OAK_PLANKS), 16, "cargo, production and storage retain one owner per plank");
            helper.assertValueEqual(count(helper, town, Items.DIAMOND_AXE), 1, "changing jobs preserves the acquired tool");
            var restored = SettlementRecord.load(town.save(level.registryAccess()), level.registryAccess(), () -> {});
            helper.assertValueEqual(restored.workerPools().get(poolId), town.workerPools().get(poolId), "the final staffing configuration and history survive reload");
            helper.assertTrue(restored.manuallyStaffed(pinned.getUUID()), "the protected manual assignment survives reload");
            helper.assertValueEqual(restored.workers().get(resident.getUUID()).role(), com.jvn.villagerretaliation.interaction.HiredVillagerRole.COURIER,
                    "the saved assignment retains the final courier role");
            for (var chunk : chunks) SettlementForestService.observeArea(level, town, forest, chunk);
            helper.assertTrue(chunks.stream().filter(chunk -> chunk.getPos().equals(new ChunkPos(root))).findFirst().orElseThrow()
                    .getBlockState(root).is(Blocks.OAK_SAPLING), "the worker finishes replanting before changing jobs");
            helper.assertFalse(level.hasChunkAt(root) || level.hasChunkAt(town.center()), "automatic staffing never force-loads its worksites");
        } finally {
            helper.getLevel().setDayTime(dayTime);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "supply_chain")
    public static void persistentPurchasesYieldToResumedLocalProductionAfterConsumptionAndReload(GameTestHelper helper) {
        SettlementBackgroundHaulingGameTests.fixture(helper, fixture -> {
            var level = helper.getLevel();
            var town = fixture.town();
            town.advance();
            town.advance();
            var source = fixture.source();
            source.configure(SettlementDepotPurpose.GENERAL, 5);
            var destination = fixture.destination();
            destination.configure(SettlementDepotPurpose.OUTPUT, 5);
            town.setWorksitePlan(town.leader(), fixture.area().id(), SettlementWorksitePlan.defaults(fixture.area().type().role()));
            var ingredients = depot(town, fixture.area(), town.center().east(2), SettlementDepotPurpose.MATERIALS);
            var workshop = area(helper, town, SettlementAreaType.WORKSHOP, "Local iron", town.center().north(3), town.center().offset(2, 2, -1));
            town.setWorksitePlan(town.leader(), workshop.id(), new SettlementWorksitePlan(
                    SettlementWorksitePlan.defaults(workshop.type().role()).settings(), 1,
                    new SettlementWorksiteLinks(Set.of(ingredients.id()), Set.of(source.id()), Set.of())));
            table(town, workshop);
            var craftsman = worker(helper, town, workshop, workshop.min(), Items.AIR);
            var market = area(helper, town, SettlementAreaType.TRADING_POST, "Market", town.center().south(6), town.center().offset(2, 2, 8));
            town.recordSurvey(market.id(), new SettlementBuildingSurvey.Survey(0, List.of(), List.of(market.min()), false,
                    Map.of(market.min(), ResourceLocation.withDefaultNamespace("lectern"))));
            var clerk = EntityType.VILLAGER.create(level);
            clerk.setPos(market.min().getCenter());
            clerk.setVillagerData(clerk.getVillagerData().setProfession(VillagerProfession.NITWIT));
            VillageAllegianceApi.assignKnown(level, clerk, town.id(), AllegianceAssignmentSource.EXPLICIT_API);
            town.assignDuty(clerk.getUUID(), market.id());
            town.recordResident(SettlementResidentState.capture(clerk, town, 0));
            town.economy().credit(com.jvn.villagerretaliation.sell.CurrencyAmount.of(1_000, 1));
            town.economy().configureBudget(com.jvn.villagerretaliation.sell.CurrencyAmount.ZERO,
                    com.jvn.villagerretaliation.sell.CurrencyAmount.of(100, 1));
            var craft = new SettlementSupplyPolicy.Step(UUID.randomUUID(), SettlementSupplyPolicy.StepKind.PRODUCTION, workshop.id(),
                    HiredProductionRecipe.exact(ResourceLocation.withDefaultNamespace("iron_ingot_from_iron_block"), Map.of()), ItemStack.EMPTY, null, source.position());
            var delivery = new SettlementSupplyPolicy.Step(UUID.randomUUID(), SettlementSupplyPolicy.StepKind.DELIVERY, fixture.area().id(),
                    null, new ItemStack(Items.IRON_INGOT), source.position(), destination.position());
            var policyId = UUID.randomUUID();
            var requirement = com.jvn.villagerretaliation.interaction.work.production.WorkSupplyDemand.identity(level.registryAccess(), policyId,
                    com.jvn.villagerretaliation.interaction.work.production.WorkSupplyDemand.Kind.STOCK_RESERVE,
                    com.jvn.villagerretaliation.interaction.work.production.WorkSupplyRequirement.exact("policy_stock", new ItemStack(Items.IRON_INGOT), 7));
            var deliveryInput = SettlementPolicyPlanner.inputs(level, town, delivery, 7).getFirst();
            var approval = new SettlementPurchasePlan.Approval(UUID.randomUUID(), delivery.id(), deliveryInput.id(),
                    ResourceLocation.withDefaultNamespace("iron_ingot"), source.position(), com.jvn.villagerretaliation.sell.CurrencyAmount.of(100, 1));
            var policy = new SettlementSupplyPolicy(policyId, new SettlementSupplyPolicy.Configuration("Local iron with approved fallback",
                    new SettlementSupplyPolicy.Target(SettlementSupplyPolicy.TargetKind.STOCK, null, null, destination.position(), new ItemStack(Items.IRON_INGOT), 7),
                    7, List.of(craft, delivery), List.of(
                    new SettlementSupplyPolicy.Connection(craft.id(), delivery.id(), deliveryInput.id()),
                    new SettlementSupplyPolicy.Connection(delivery.id(), null, requirement)),
                    new SettlementPurchasePlan(true, com.jvn.villagerretaliation.sell.CurrencyAmount.of(100, 1), List.of(approval))),
                    town.leader(), town::supplyChanged, town::changed);
            helper.assertTrue(town.addSupplyPolicy(policy), "local production, delivery and the bounded fallback are approved once");
            policy.state(SettlementSupplyPolicy.State.ACTIVE, town.leader(), "", level.getGameTime());
            long revision = policy.revision();
            long now;
            for (now = 200; now <= 6_000; now += 200) {
                finishPolicy(SettlementPolicyService.cycle(level, town));
                helper.assertValueEqual(policy.state(), SettlementSupplyPolicy.State.ACTIVE, "the known local input shortage keeps the approved fallback available: " + policy.blocker());
                SettlementTestPlots.planSupplies(level, town);
                SettlementBackgroundCrafting.tick(level, town, craftsman, 200, 1.0D);
                SettlementTestPlots.planSupplies(level, town);
                SettlementBackgroundHauling.tick(level, town, fixture.worker(), 200, 1.0D);
                SettlementTradeService.process(level, town, now);
                if (now < 1_400) helper.assertTrue(town.economy().policySpending(policyId).isZero(), "purchasing waits for the full persistent-shortage interval");
                if (destination.count(stack -> stack.is(Items.IRON_INGOT)) == 7) break;
            }
            helper.assertValueEqual(destination.count(stack -> stack.is(Items.IRON_INGOT)), 7, "approved imports recover the real persistent local shortage");
            var productionOrder = SettlementPolicyOrders.current(policy, craft).order();
            var transferOrder = SettlementPolicyOrders.current(policy, delivery).order();
            helper.assertValueEqual(town.productionQueues().get(workshop.id()).order(productionOrder).produced(), 0L, "a blocked producer cannot fabricate local progress");
            var unitPrice = com.jvn.villagerretaliation.sell.VillageSellMarket.quote(level, town.center(), new ItemStack(Items.IRON_INGOT))
                    .orElseThrow().baseUnitPrice().multiply(2);
            var spent = unitPrice.multiply(7);
            helper.assertValueEqual(town.economy().policySpending(policyId), spent, "fallback pays for exactly seven real imported ingots");
            helper.assertValueEqual(destination.withdraw(stack -> stack.is(Items.IRON_INGOT), 7).getCount(), 7, "consumption removes the seven delivered ingots");
            town = SettlementRecord.load(town.save(level.registryAccess()), level.registryAccess(), () -> {});
            policy = town.supplyPolicies().get(policyId);
            helper.assertTrue(town.depots().get(ingredients.id()).deposit(new ItemStack(Items.IRON_BLOCK)).isEmpty(),
                    "one finite local ingredient arrives at the approved producer's source");
            long resumed = now;
            for (now += 200; now <= resumed + 8_000; now += 200) {
                finishPolicy(SettlementPolicyService.cycle(level, town));
                SettlementTestPlots.planSupplies(level, town);
                SettlementBackgroundCrafting.tick(level, town, town.workers().get(craftsman.id()), 200, 1.0D);
                SettlementTestPlots.planSupplies(level, town);
                SettlementBackgroundHauling.tick(level, town, town.workers().get(fixture.worker().id()), 200, 1.0D);
                SettlementTradeService.process(level, town, now);
                helper.assertValueEqual(town.economy().policySpending(policyId), spent, "real local output and incoming receipts prevent further fallback spending");
                helper.assertValueEqual(policy.revision(), revision, "local recovery requires no policy edit or reactivation");
                helper.assertValueEqual(SettlementPolicyOrders.current(policy, craft).order(), productionOrder, "resumed production retains its native binding");
                helper.assertValueEqual(SettlementPolicyOrders.current(policy, delivery).order(), transferOrder, "repeated delivery retains its native binding");
            }
            helper.assertValueEqual(town.depots().get(destination.id()).count(stack -> stack.is(Items.IRON_INGOT)), 7, "native local production restores the consumed target");
            helper.assertValueEqual(town.productionQueues().get(workshop.id()).order(productionOrder).produced(), 9L, "one exact native recipe yields nine ingots");
            helper.assertValueEqual(town.transferQueues().get(fixture.area().id()).order(transferOrder).delivered(), 14L, "the shared courier records imported and local deliveries once each");
            helper.assertValueEqual(count(helper, town, Items.IRON_BLOCK), 0, "the local ingredient is consumed exactly once");
            helper.assertValueEqual(count(helper, town, Items.IRON_INGOT), 9, "seven maintained ingots and two recipe surplus ingots retain real ownership");
            helper.assertValueEqual(town.economy().spent(), spent, "local resumption leaves the town spending ledger unchanged");
            helper.assertTrue(policy.purchaseShortages().isEmpty(), "resolved native demand clears persistent purchase timers");
            helper.assertValueEqual(town.productionQueues().get(workshop.id()).orders().size(), 1, "resumption adds no duplicate production");
            helper.assertValueEqual(town.transferQueues().get(fixture.area().id()).orders().size(), 1, "resumption adds no duplicate delivery");
            helper.assertFalse(level.hasChunkAt(town.center()), "purchase and local recovery never force-load their worksites");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "supply_chain")
    public static void interruptedFurnaceSupplyPreservesReceiptsThroughAuthorizedRecoveryAndCancellation(GameTestHelper helper) {
        SettlementBackgroundHaulingGameTests.fixture(helper, fixture -> {
            var level = helper.getLevel();
            var town = fixture.town();
            var operator = helper.makeMockServerPlayerInLevel();
            var authority = operator.getUUID();
            var permissions = Set.of(SettlementPermission.VIEW, SettlementPermission.STAFF, SettlementPermission.LOGISTICS);
            helper.assertTrue(town.setPermissions(town.leader(), authority, permissions), "the operator has explicit production and storage authority");
            var source = fixture.source();
            source.configure(SettlementDepotPurpose.MATERIALS, 5);
            source.setItem(0, new ItemStack(Items.RAW_IRON, 4));
            source.setItem(1, new ItemStack(Items.COAL, 2));
            var destination = fixture.destination();
            destination.configure(SettlementDepotPurpose.OUTPUT, 5);
            var smeltery = area(helper, town, SettlementAreaType.SMELTERY, "Recoverable furnace",
                    town.center().north(3), town.center().offset(2, 2, -1));
            town.setWorksitePlan(town.leader(), smeltery.id(), new SettlementWorksitePlan(
                    SettlementWorksitePlan.defaults(smeltery.type().role()).settings(), 1,
                    new SettlementWorksiteLinks(Set.of(source.id()), Set.of(destination.id()), Set.of())));
            var furnace = new net.minecraft.world.level.block.entity.FurnaceBlockEntity(smeltery.min(), Blocks.FURNACE.defaultBlockState());
            var firstMachine = SettlementFurnaceState.capture(level, smeltery.id(), furnace, 0);
            town.recordFurnace(firstMachine);
            var smelter = worker(helper, town, smeltery, smeltery.min(), Items.AIR);
            var step = new SettlementSupplyPolicy.Step(UUID.randomUUID(), SettlementSupplyPolicy.StepKind.PRODUCTION, smeltery.id(),
                    HiredProductionRecipe.exact(ResourceLocation.withDefaultNamespace("iron_ingot_from_smelting_raw_iron"), Map.of()),
                    ItemStack.EMPTY, null, destination.position());
            var policyId = UUID.randomUUID();
            var requirement = com.jvn.villagerretaliation.interaction.work.production.WorkSupplyDemand.identity(level.registryAccess(), policyId,
                    com.jvn.villagerretaliation.interaction.work.production.WorkSupplyDemand.Kind.STOCK_RESERVE,
                    com.jvn.villagerretaliation.interaction.work.production.WorkSupplyRequirement.exact("policy_stock", new ItemStack(Items.IRON_INGOT), 2));
            var configuration = new SettlementSupplyPolicy.Configuration("Maintain two ingots",
                    new SettlementSupplyPolicy.Target(SettlementSupplyPolicy.TargetKind.STOCK, null, null, destination.position(), new ItemStack(Items.IRON_INGOT), 2),
                    5, List.of(step), List.of(new SettlementSupplyPolicy.Connection(step.id(), null, requirement)));
            helper.assertTrue(SettlementPolicyGameTests.request(operator, town, policyId, 0, smeltery.id(),
                    new com.jvn.villagerretaliation.network.SettlementPolicyRequestPayload.Save(configuration, true)).applied(),
                    "the native management request activates the complete supply policy");
            var policy = town.supplyPolicies().get(policyId);
            UUID firstOrder = null;
            boolean revoked = false;
            long now;
            for (now = 200; now <= 8_000; now += 200) {
                SettlementFurnaceService.update(level, town, now);
                finishPolicy(SettlementPolicyService.cycle(level, town));
                SettlementTestPlots.planSupplies(level, town);
                SettlementBackgroundProductionFurnace.tick(level, town, smelter, 200, 1.0D);
                var binding = SettlementPolicyOrders.current(policy, step);
                if (binding != null) firstOrder = binding.order();
                if (!revoked && firstMachine.items(level.registryAccess()).getFirst().getCount() == 2) {
                    helper.assertTrue(town.productionQueues().get(smeltery.id()).allocations().stream().anyMatch(value -> value.started()),
                            "permission loss interrupts a real started native batch");
                    helper.assertTrue(town.setPermissions(town.leader(), authority, Set.of(SettlementPermission.VIEW)), "the operator loses execution authority");
                    revoked = true;
                }
                if (destination.count(stack -> stack.is(Items.IRON_INGOT)) == 2) break;
            }
            helper.assertTrue(revoked, "the native furnace started before authority was revoked");
            helper.assertValueEqual(policy.state(), SettlementSupplyPolicy.State.SUSPENDED, "revocation suspends further managed production");
            helper.assertValueEqual(policy.blocker(), "policy_permission_changed", "the persistent blocker identifies the lost authority");
            helper.assertValueEqual(destination.count(stack -> stack.is(Items.IRON_INGOT)), 2, "started work safely completes after revocation");
            helper.assertValueEqual(source.count(stack -> stack.is(Items.RAW_IRON)), 2, "revoked work cannot consume the next batch");
            helper.assertTrue(town.productionQueues().get(smeltery.id()).allocations().isEmpty(), "the first native receipt finishes before replacing infrastructure");
            helper.assertValueEqual(destination.withdraw(stack -> stack.is(Items.IRON_INGOT), 2).getCount(), 2, "the completed delivery is consumed before storage is removed");
            helper.assertTrue(town.removeDepot(destination.id()), "the empty destination is physically unregistered");
            town.removeFurnace(smeltery.min());
            helper.assertTrue(town.setPermissions(town.leader(), authority, permissions), "the leader restores the operator's permissions");
            var activate = new com.jvn.villagerretaliation.network.SettlementPolicyRequestPayload.Change(SettlementSupplyPolicy.State.ACTIVE);
            var missingStorage = SettlementPolicyGameTests.request(operator, town, policyId, policy.revision(), smeltery.id(), activate);
            helper.assertFalse(missingStorage.applied(), "restored permissions cannot activate a chain with removed storage");
            helper.assertValueEqual(missingStorage.feedback(), "policy_destination_unlinked", "the control identifies the destination that needs recovery");
            var replacementDepot = depot(town, fixture.area(), destination.position(), SettlementDepotPurpose.OUTPUT);
            helper.assertFalse(replacementDepot.id().equals(destination.id()), "replacement storage has a new native identity");
            helper.assertTrue(SettlementWorksiteService.configure(operator, town, smeltery.id(), new SettlementWorksitePlan(
                    SettlementWorksitePlan.defaults(smeltery.type().role()).settings(), 1,
                    new SettlementWorksiteLinks(Set.of(source.id()), Set.of(replacementDepot.id()), Set.of())), false),
                    "the replacement destination is explicitly approved");
            var missingFurnace = SettlementPolicyGameTests.request(operator, town, policyId, policy.revision(), smeltery.id(), activate);
            helper.assertFalse(missingFurnace.applied(), "storage recovery alone cannot start production without a furnace");
            helper.assertValueEqual(missingFurnace.feedback(), "no_furnace", "the control identifies the remaining missing station");
            var replacement = new net.minecraft.world.level.block.entity.FurnaceBlockEntity(smeltery.min(), Blocks.FURNACE.defaultBlockState());
            var replacementMachine = SettlementFurnaceState.capture(level, smeltery.id(), replacement, now);
            helper.assertFalse(replacementMachine.id().equals(firstMachine.id()), "the replacement furnace has a distinct physical identity");
            town.recordFurnace(replacementMachine);
            helper.assertTrue(SettlementPolicyGameTests.request(operator, town, policyId, policy.revision(), smeltery.id(), activate).applied(),
                    "explicit native reactivation resumes work only after both approved replacements exist");
            boolean cancelled = false, reloaded = false;
            long resumed = now;
            for (now += 200; now <= resumed + 8_000; now += 200) {
                SettlementFurnaceService.update(level, town, now);
                finishPolicy(SettlementPolicyService.cycle(level, town));
                SettlementTestPlots.planSupplies(level, town);
                SettlementBackgroundProductionFurnace.tick(level, town, town.workers().get(smelter.id()), 200, 1.0D);
                if (!cancelled && town.furnaces().get(smeltery.min()).items(level.registryAccess()).getFirst().getCount() == 2) {
                    var queue = town.productionQueues().get(smeltery.id());
                    var order = SettlementPolicyOrders.current(policy, step).order();
                    helper.assertValueEqual(order, firstOrder, "ordinary authorized recovery preserves the existing managed order");
                    helper.assertValueEqual(SettlementProductionService.edit(operator, town, smeltery.id(), queue.configurationRevision(), order,
                            com.jvn.villagerretaliation.interaction.work.production.HiredProductionService.Edit.CANCEL, 0), "",
                            "the native production control cancels future work during the replacement batch");
                    helper.assertValueEqual(policy.state(), SettlementSupplyPolicy.State.SUSPENDED, "native cancellation immediately suspends its coordinating policy");
                    town = SettlementRecord.load(town.save(level.registryAccess()), level.registryAccess(), () -> {});
                    policy = town.supplyPolicies().get(policyId);
                    cancelled = true;
                    reloaded = true;
                }
            }
            helper.assertTrue(cancelled && reloaded, "the replacement batch survives cancellation and a full settlement reload");
            helper.assertValueEqual(policy.state(), SettlementSupplyPolicy.State.SUSPENDED, "reevaluation never silently restarts the cancelled policy");
            helper.assertValueEqual(policy.blocker(), "policy_order_suspended", "the saved blocker directs the player to the stopped managed order");
            var queue = town.productionQueues().get(smeltery.id());
            helper.assertValueEqual(queue.orders().size(), 1, "recovery and cancellation create no duplicate production");
            helper.assertTrue(queue.order(firstOrder).cancelled(), "the native order retains the user's cancellation");
            helper.assertValueEqual(queue.order(firstOrder).produced(), 4L, "both authorized native batches finish exactly once: worker="
                    + town.workers().get(smelter.id()).status() + ", blocker=" + queue.order(firstOrder).blocker()
                    + ", machine=" + town.furnaces().get(smeltery.min()).items(level.registryAccess())
                    + ", allocations=" + queue.allocations() + ", destination=" + town.depots().get(replacementDepot.id()).count(stack -> stack.is(Items.IRON_INGOT)));
            helper.assertTrue(queue.allocations().isEmpty(), "all started receipts complete without leaving stranded ownership");
            helper.assertValueEqual(town.depots().get(replacementDepot.id()).count(stack -> stack.is(Items.IRON_INGOT)), 2,
                    "the second batch reaches only the replacement destination");
            helper.assertValueEqual(count(helper, town, Items.IRON_INGOT) + 2, 4, "the two consumed and two stored ingots conserve all production");
            helper.assertValueEqual(count(helper, town, Items.RAW_IRON), 0, "no extra raw material is consumed or fabricated");
            helper.assertValueEqual(count(helper, town, Items.COAL), 0, "each physical furnace consumes one real fuel item");
            var machine = town.furnaces().get(smeltery.min());
            helper.assertTrue(machine.reconcile(level.registryAccess(), replacement), "the replacement machine receives its saved native state");
            helper.assertTrue(replacement.getItem(0).isEmpty() && replacement.getItem(2).isEmpty(), "loaded machine handoff cannot duplicate already delivered products");
            helper.assertTrue(machine.reconcile(level.registryAccess(), replacement), "replaying an acknowledged handoff remains idempotent");
            helper.assertTrue(replacement.getItem(0).isEmpty() && replacement.getItem(2).isEmpty(), "repeat reconciliation preserves the empty native item slots");
            helper.assertFalse(level.hasChunkAt(town.center()) || level.hasChunkAt(smeltery.min()), "interruption recovery never force-loads its worksites");
        });
    }

    private static void finishPolicy(Runnable work) {
        int remaining = 20_000;
        while (!SettlementWork.complete(work) && --remaining > 0) work.run();
        if (remaining == 0) throw new IllegalStateException("The configured supply policy did not finish its bounded work");
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "supply_chain")
    public static void demandPlantingCreditsGrowingAndReservedPlotsOnceAcrossReload(GameTestHelper helper) {
        SettlementFarmingGameTests.fixture(helper, fixture -> {
            var level = helper.getLevel();
            var town = fixture.town();
            var root = fixture.plot().position();
            var plan = new SettlementCropPlan(List.of(100, 0, 0, 0, 0, 0), true);
            var farm = new SettlementArea(fixture.area().id(), SettlementAreaType.FARM, "Demand field",
                    root, root.offset(3, 1, 0), 5, true, plan);
            helper.assertTrue(town.putArea(town.leader(), farm), "demand planting is enabled explicitly");
            town.configureDepot(town.leader(), fixture.supply().id(), SettlementDepotPurpose.GENERAL, 5);
            fixture.plot().change(fixture.plot().soil(), Blocks.WHEAT.defaultBlockState());
            var one = plantingPlot(helper, fixture, farm, root.east());
            var two = plantingPlot(helper, fixture, farm, root.east(2));
            var three = plantingPlot(helper, fixture, farm, root.east(3));
            var kitchen = area(helper, town, SettlementAreaType.KITCHEN, "Bakery", root.north(4), root.offset(2, 2, -2));
            table(town, kitchen);
            worker(helper, town, kitchen, kitchen.min(), Items.AIR);
            order(helper, town, kitchen, "bread", HiredProductionOrder.Mode.BATCH, 1, fixture.output().position());
            var state = fixture.worker().settings();
            var first = SettlementPlanting.choose(level, town, farm, one, one.position(), state);
            helper.assertValueEqual(first.reason(), "cooking", "the actual bread order selects wheat");
            helper.assertValueEqual(first.crop(), com.jvn.villagerretaliation.interaction.work.HiredFarmingOptions.Crop.WHEAT, "the recipe input is plantable");
            helper.assertValueEqual(SettlementPlanting.choose(level, town, farm, two, two.position(), state).reason(), "cooking",
                    "one growing plot leaves only two plots of recipe demand");
            helper.assertValueEqual(SettlementPlanting.choose(level, town, farm, three, three.position(), state).reason(), "mix",
                    "the third empty plot cannot claim the same recipe shortage again");
            var restored = SettlementRecord.load(town.save(level.registryAccess()), level.registryAccess(), () -> {});
            var saved = restored.fields().get(one.position());
            restored.depots().get(fixture.supply().id()).setItem(0, new ItemStack(Items.WHEAT, 64));
            helper.assertValueEqual(SettlementPlanting.choose(level, restored, farm, saved, saved.position(), state), first,
                    "supplied stock and saving do not oscillate an assigned empty plot");
            var largerReserve = new SettlementArea(farm.id(), farm.type(), farm.name(), farm.min(), farm.max(), farm.priority(), true,
                    new SettlementCropPlan(plan.percentages(), true, 2));
            helper.assertValueEqual(SettlementPlanting.choose(level, restored, largerReserve, saved, saved.position(), state), first,
                    "changing only the reserve size preserves the plot's planting choice");
            state.putString(com.jvn.villagerretaliation.interaction.work.HiredFarmingOptions.CROP_TAG, "CARROT");
            helper.assertFalse(SettlementPlanting.permits(state, first.crop(), new ItemStack(Items.WHEAT_SEEDS)),
                    "a saved choice cannot bypass a changed worker restriction");
            helper.assertFalse(level.hasChunkAt(root), "demand planning does not load fields");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "supply_chain")
    public static void demandPlantingUsesObservedBreedingPairsAndFoodReserves(GameTestHelper helper) {
        SettlementFarmingGameTests.fixture(helper, fixture -> {
            var level = helper.getLevel();
            var town = fixture.town();
            var root = fixture.plot().position();
            var farm = new SettlementArea(fixture.area().id(), SettlementAreaType.FARM, "Feed field", root, root.above(), 5, true,
                    new SettlementCropPlan(List.of(100, 0, 0, 0, 0, 0), true));
            town.putArea(town.leader(), farm);
            town.configureDepot(town.leader(), fixture.supply().id(), SettlementDepotPurpose.GENERAL, 5);
            var pen = area(helper, town, SettlementAreaType.ANIMAL_PEN, "Cows", root.south(4), root.offset(2, 2, 6));
            worker(helper, town, pen, pen.min(), Items.AIR);
            for (int index = 0; index < 2; index++) {
                var cow = EntityType.COW.create(level);
                cow.setPos(pen.min().east(index).getCenter());
                town.recordLivestock(SettlementLivestock.capture(cow, pen.id(), 0L));
            }
            var needs = SettlementDemandService.inspect(level, town).demands().stream()
                    .filter(need -> need.requirement().key().startsWith("animal_feed:")).toList();
            helper.assertValueEqual(needs.size(), 1, "the observed pair creates one shared feed requirement");
            helper.assertValueEqual(needs.getFirst().missing(), 2L, "one item per eligible animal is required");
            fixture.plot().change(fixture.plot().soil(), Blocks.AIR.defaultBlockState());
            var selected = SettlementPlanting.choose(level, town, farm, fixture.plot(), root, fixture.worker().settings());
            helper.assertValueEqual(selected.reason(), "feed", "wheat is planted for the observed cows");
            fixture.supply().setItem(0, new ItemStack(Items.WHEAT, 2));
            helper.assertValueEqual(SettlementDemandService.inspect(level, town).demands().stream()
                    .filter(need -> need.requirement().key().startsWith("animal_feed:")).findFirst().orElseThrow().missing(), 0L,
                    "existing supplies satisfy the breeding requirement");
            var foodFarm = new SettlementArea(farm.id(), farm.type(), farm.name(), farm.min(), farm.max(), 5, true,
                    new SettlementCropPlan(List.of(0, 100, 0, 0, 0, 0), true));
            town.putArea(town.leader(), foodFarm);
            helper.assertTrue(SettlementFoodService.reserveNutrition(level, town) > 0, "known residents require food reserves");
            helper.assertValueEqual(SettlementPlanting.choose(level, town, foodFarm, fixture.plot(), root, fixture.worker().settings()).reason(),
                    "food", "a changed plan can choose carrots for real nutrition demand");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "supply_chain")
    public static void plantingCycleReserveIsSharedProtectedAndSaved(GameTestHelper helper) {
        SettlementFarmingGameTests.fixture(helper, fixture -> {
            var level = helper.getLevel();
            var town = fixture.town();
            var root = fixture.plot().position();
            helper.assertValueEqual(SettlementPlantingReserve.quota(town, fixture.area(), fixture.worker().id(), new ItemStack(Items.WHEAT_SEEDS)),
                    -1, "old farms retain the eight-item inventory behavior");
            var farm = new SettlementArea(fixture.area().id(), SettlementAreaType.FARM, "Reserved field", root, root.offset(3, 1, 0), 5, true,
                    new SettlementCropPlan(List.of(50, 25, 25, 0, 0, 0), false, 1));
            town.putArea(town.leader(), farm);
            for (int i = 1; i < 4; i++) plantingPlot(helper, fixture, farm, root.east(i));
            var partner = worker(helper, town, farm, root.east(), Items.DIAMOND_HOE);
            var targets = SettlementPlantingReserve.targets(town, farm);
            helper.assertValueEqual(targets.get(com.jvn.villagerretaliation.interaction.work.HiredFarmingOptions.Crop.WHEAT), 2,
                    "the next cycle needs two wheat seeds");
            helper.assertValueEqual(targets.get(com.jvn.villagerretaliation.interaction.work.HiredFarmingOptions.Crop.CARROT), 1,
                    "carrots have their own planting reserve");
            helper.assertValueEqual(targets.get(com.jvn.villagerretaliation.interaction.work.HiredFarmingOptions.Crop.POTATO), 1,
                    "potatoes have their own planting reserve");
            for (var item : List.of(Items.WHEAT_SEEDS, Items.CARROT, Items.POTATO)) {
                int total = SettlementPlantingReserve.quota(town, farm, fixture.worker().id(), new ItemStack(item))
                        + SettlementPlantingReserve.quota(town, farm, partner.id(), new ItemStack(item));
                helper.assertValueEqual(total, item == Items.WHEAT_SEEDS ? 2 : 1, "workers split one farm reserve without duplication");
            }
            fixture.supply().setItem(0, new ItemStack(Items.WHEAT_SEEDS, 64));
            var snapshot = SettlementDemandService.inspect(level, town);
            var seeds = snapshot.demands().stream().filter(need -> need.requirement().key().equals("planting_reserve")
                    && need.requirement().value(new ItemStack(Items.WHEAT_SEEDS)) > 0).findFirst().orElseThrow();
            helper.assertValueEqual(seeds.supply().protectedMinimum(), 2L, "planting stock participates in protected allocation");
            helper.assertValueEqual(snapshot.sellable(fixture.supply(), new ItemStack(Items.WHEAT_SEEDS)), 62,
                    "sales leave enough seed for the whole next cycle");
            SettlementTestPlots.planSupplies(level, town);
            helper.assertValueEqual(SettlementAllocationService.inspect(level, town, null, UUID.randomUUID(),
                    fixture.supply().position(), new ItemStack(Items.WHEAT_SEEDS)).allowance(), 62,
                    "unrelated consumers cannot take protected seeds");
            helper.assertValueEqual(SettlementAllocationService.inspect(level, town, fixture.worker().id(), null,
                    fixture.supply().position(), new ItemStack(Items.WHEAT_SEEDS)).allowance(), 64,
                    "the farm can use its own planting reserve");
            var inventory = HiredJobInventory.getJobInventory(fixture.villager());
            helper.assertTrue(com.jvn.villagerretaliation.interaction.work.HiredFarmingInventoryBridge.storeFarmDrops(
                    fixture.villager(), inventory, List.of(new ItemStack(Items.WHEAT_SEEDS, 8), new ItemStack(Items.CARROT, 8), new ItemStack(Items.POTATO, 8))),
                    "harvest storage retains the configured crop supplies");
            for (var item : List.of(Items.WHEAT_SEEDS, Items.CARROT, Items.POTATO)) {
                int retained = inventory.supplySlots().stream().map(inventory::getItem).filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
                helper.assertValueEqual(retained, SettlementPlantingReserve.quota(town, farm, fixture.worker().id(), new ItemStack(item)),
                        "loaded farmers keep only their share of each crop reserve");
            }
            var restored = SettlementRecord.load(town.save(level.registryAccess()), level.registryAccess(), () -> {});
            helper.assertValueEqual(restored.areas().get(farm.id()).crops().cycles(), 1, "cycle settings survive saving");
            helper.assertValueEqual(SettlementPlantingReserve.targets(restored, restored.areas().get(farm.id())), targets,
                    "background operation keeps the same farm-wide reserve");
        });
    }

    private static SettlementFieldPlot plantingPlot(GameTestHelper helper, SettlementFarmingGameTests.Fixture fixture,
                                                    SettlementArea farm, BlockPos position) {
        set(fixture.chunk(), position, Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
        set(fixture.chunk(), position.above(), Blocks.AIR.defaultBlockState());
        var saved = SettlementFieldPlot.capture(helper.getLevel(), farm.id(), position, fixture.chunk(), 0L).save();
        saved.putInt("BlockLight", 15);
        var plot = SettlementFieldPlot.load(saved);
        fixture.town().recordField(plot);
        return plot;
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
        return transfer(town, area, source, destination, item, count, HiredTransferQueue.Mode.FINITE);
    }

    private static HiredTransferQueue.Order transfer(SettlementRecord town, SettlementArea area, SettlementDepot source,
                                                       SettlementDepot destination, Item item, int count, HiredTransferQueue.Mode mode) {
        var order = HiredTransferQueue.Order.create(new HiredTransferQueue.Endpoint(source.position(), source.id()),
                new HiredTransferQueue.Endpoint(destination.position(), destination.id()), new ItemStack(item), count, mode);
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
