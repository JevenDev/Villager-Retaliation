package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.interaction.HiredWorkStateStore;
import com.jvn.villagerretaliation.interaction.work.courier.HiredTransferQueue;
import com.jvn.villagerretaliation.interaction.work.production.HiredProductionOrder;
import com.jvn.villagerretaliation.interaction.work.production.HiredProductionQueue;
import com.jvn.villagerretaliation.interaction.work.production.HiredProductionRecipe;
import com.jvn.villagerretaliation.interaction.work.production.HiredProductionStock;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.jvn.villagerretaliation.network.SettlementManagementActionPayload;
import com.jvn.villagerretaliation.network.SettlementManagementPayload;
import com.jvn.villagerretaliation.network.SettlementManagementPayload.Supply;
import com.jvn.villagerretaliation.network.SettlementManagementPayload.SupplyLinkKind;
import com.jvn.villagerretaliation.network.SettlementManagementRequestPayload;
import com.jvn.villagerretaliation.network.SettlementManagementRequestPayload.Page;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class SettlementSupplyGameTests {
    private SettlementSupplyGameTests() { }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_supplies")
    public static void sharedIncomingAndOverlappingTargetsSurviveReloadAndReassignment(GameTestHelper helper) {
        var fixture = fixture(helper);
        var town = fixture.town();
        var first = area(town, SettlementAreaType.KITCHEN, "Bakery", 5);
        var second = area(town, SettlementAreaType.KITCHEN, "Second bakery", 10);
        var a = bread(helper, town, first, fixture.destination().position(), 12);
        var b = bread(helper, town, second, fixture.destination().position(), 16);
        var queueA = town.productionQueues().get(first.id());
        var queueB = town.productionQueues().get(second.id());
        var workerA = UUID.randomUUID();
        var workerB = UUID.randomUUID();
        town.assignDuty(workerA, first.id());
        town.assignDuty(workerB, second.id());
        var allocationA = queueA.reserve(workerA, a.id(), 1, 2, 0, null);
        var allocationB = queueB.reserve(workerB, b.id(), 1, 4, 0, null);
        helper.assertTrue(allocationA != null && allocationB != null, "two producers reserve separate shared work");
        queueA.start(allocationA.id());
        queueA.pause(a.id(), true);
        queueB.blocker(b.id(), "missing_ingredients");
        fixture.destination().setItem(0, new ItemStack(Items.BREAD, 5));
        fixture.destination().setStockTarget(new ItemStack(Items.BREAD), 10);
        var transfers = town.beginTransfers(town.leader(), fixture.storage().id());
        var transfer = HiredTransferQueue.Order.create(new HiredTransferQueue.Endpoint(fixture.source().position(), fixture.source().id()),
                new HiredTransferQueue.Endpoint(fixture.destination().position(), fixture.destination().id()), new ItemStack(Items.BREAD), 30);
        transfers.append(transfer);
        var courier = UUID.randomUUID();
        var delivery = transfers.reserve(courier, transfer.id(), 3);
        transfers.picked(delivery.id(), 3);
        transfers.detach(courier);
        var before = town.save(helper.getLevel().registryAccess());
        var supply = breadSupply(fixture.leader(), town, fixture.destination().position());
        helper.assertValueEqual(supply.stored(), 5L, "incoming goods do not inflate stored items");
        helper.assertValueEqual(supply.incoming(), 9L, "each production allocation and transfer is counted once");
        helper.assertValueEqual(supply.target(), 16, "overlapping targets use their maximum and ignore finite transfer quantity");
        helper.assertValueEqual(supply.shortage(), 2L, "shortage subtracts stored stock and commitments once");
        helper.assertValueEqual(SettlementProductionStock.count(helper.getLevel(), town, queueA, queueA.order(a.id())), 12L,
                "the worker calculation excludes its own queue while sharing every other incoming fact");
        helper.assertValueEqual(town.save(helper.getLevel().registryAccess()), before, "supply inspection does not alter orders or allocations");
        var links = SettlementSupplyService.links(fixture.leader(), town, supply.id().toString());
        helper.assertTrue(links.stream().anyMatch(link -> link.targetId() != null && link.targetId().equals(a.id())
                && link.status().equals("paused") && link.incoming() == 2), "paused production retains already committed work");
        helper.assertTrue(links.stream().anyMatch(link -> link.targetId() != null && link.targetId().equals(b.id())
                && link.detail().equals("missing_ingredients")), "the actual production blocker is linked");
        helper.assertTrue(links.stream().anyMatch(link -> link.kind() == SupplyLinkKind.TRANSFER
                && link.detail().equals("transfer_worker_changed") && link.incoming() == 3), "reassignment preserves and explains pending cargo");
        helper.assertTrue(links.stream().anyMatch(link -> link.kind() == SupplyLinkKind.INPUT_STORAGE
                && fixture.source().id().equals(link.targetId())), "the delivery links to its real source");
        var restored = SettlementRecord.load(before, helper.getLevel().registryAccess(), () -> {});
        var after = breadSupply(fixture.leader(), restored, fixture.destination().position());
        helper.assertValueEqual(after.id(), supply.id(), "item and destination identity survives reload");
        helper.assertValueEqual(after.incoming(), supply.incoming(), "reload preserves incoming accounting");
        helper.assertFalse(helper.getLevel().hasChunkAt(town.center()), "inspection does not load the settlement");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_supplies")
    public static void exactComponentsUnknownStorageAndChangedTargetsStayDistinct(GameTestHelper helper) {
        var fixture = fixture(helper);
        var town = fixture.town();
        var named = new ItemStack(Items.BREAD, 3);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Festival bread"));
        fixture.destination().setItem(0, new ItemStack(Items.BREAD, 2));
        fixture.destination().setItem(1, named);
        fixture.destination().setStockTarget(new ItemStack(Items.BREAD), 8);
        fixture.destination().setStockTarget(named, 12);
        var rows = SettlementSupplyService.supplies(fixture.leader(), town).stream()
                .filter(row -> row.position().equals(fixture.destination().position())).toList();
        helper.assertValueEqual(rows.size(), 2, "different item components remain separate supply rows");
        var custom = rows.stream().filter(row -> row.item().has(DataComponents.CUSTOM_NAME)).findFirst().orElseThrow();
        helper.assertValueEqual(custom.stored(), 3L, "the named item has its own stored quantity");
        helper.assertValueEqual(custom.target(), 12, "the named item retains its exact target");
        var escaped = custom.item();
        escaped.set(DataComponents.CUSTOM_NAME, Component.literal("Changed client copy"));
        helper.assertTrue(HiredProductionStock.matches(custom.item(), named), "snapshot item components are defensive copies");
        fixture.destination().setStockTarget(named, 20);
        var updated = SettlementSupplyService.supplies(fixture.leader(), town).stream().filter(row -> row.id().equals(custom.id())).findFirst().orElseThrow();
        helper.assertValueEqual(updated.target(), 20, "refresh changes quantities without changing row identity");
        var bakery = area(town, SettlementAreaType.KITCHEN, "Remote bakery", 5);
        var unknown = town.center().offset(2, 0, 9);
        bread(helper, town, bakery, unknown, 10);
        var missing = breadSupply(fixture.leader(), town, unknown);
        helper.assertValueEqual(missing.stored(), -1L, "unavailable storage is unknown instead of empty");
        helper.assertValueEqual(missing.incoming(), -1L, "unavailable storage does not claim complete incoming observations");
        helper.assertValueEqual(missing.shortage(), -1L, "unknown stock does not produce a fabricated shortage");
        helper.assertValueEqual(missing.status(), "missing", "a removed destination is explicitly unavailable");
        var linked = town.center().offset(1, 0, 10);
        town.setWorksitePlan(town.leader(), bakery.id(), new SettlementWorksitePlan(
                SettlementWorkerSettings.capture(bakery.type().role(), new net.minecraft.nbt.CompoundTag()).withStorage(
                        new SettlementStorageLinks(false, List.of(new SettlementStorageLinks.Link(linked, SettlementStorageLinks.Purpose.OUTPUT, 5)))),
                1, SettlementWorksiteLinks.AUTOMATIC));
        var unloaded = SettlementSupplyService.supplies(fixture.leader(), town).stream().filter(row -> row.position().equals(linked)).findFirst().orElseThrow();
        helper.assertValueEqual(unloaded.status(), "unloaded", "unobserved ordinary storage remains identifiable");
        helper.assertValueEqual(unloaded.stored(), -1L, "unloaded ordinary storage has no invented inventory");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_supplies")
    public static void supplyPagesRoundTripPaginateAndRespectViewPermission(GameTestHelper helper) {
        var fixture = fixture(helper);
        for (int index = 0; index < 30; index++) {
            var sample = new ItemStack(Items.BREAD, index + 1);
            sample.set(DataComponents.CUSTOM_NAME, Component.literal("Batch " + index));
            fixture.destination().setItem(index, sample);
        }
        var request = new SettlementManagementRequestPayload(fixture.town().id().value(), Page.SUPPLIES, 0, "", 11, false);
        var first = SettlementManagementService.snapshot(fixture.leader(), request);
        helper.assertValueEqual(first.total(), 30, "all supply rows participate in normal pagination");
        helper.assertValueEqual(first.entries().size(), SettlementManagementRequestPayload.PAGE_SIZE, "supply pages respect the network bound");
        var second = SettlementManagementService.snapshot(fixture.leader(),
                new SettlementManagementRequestPayload(fixture.town().id().value(), Page.SUPPLIES, 1, "", 12, false));
        helper.assertValueEqual(second.entries().size(), 6, "the final page contains the remaining exact items");
        roundTrip(helper, first);
        var linkPage = SettlementManagementService.snapshot(fixture.leader(),
                new SettlementManagementRequestPayload(fixture.town().id().value(), Page.SUPPLY_LINKS, 0,
                        first.entries().getFirst().id().toString(), 13, false));
        helper.assertTrue(!linkPage.entries().isEmpty(), "supply detail requests resolve the stable item identity");
        roundTrip(helper, linkPage);
        var delegate = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "SupplyViewer"));
        helper.assertTrue(SettlementManagementService.snapshot(delegate, request) == null, "outsiders cannot inspect settlement supply snapshots");
        fixture.town().setPermissions(fixture.town().leader(), delegate.getUUID(), Set.of(SettlementPermission.VIEW));
        helper.assertValueEqual(SettlementManagementService.snapshot(delegate, request).total(), 30, "view delegates can inspect supplies");
        helper.assertFalse(SettlementManagementService.execute(delegate, new SettlementManagementActionPayload(fixture.town().id().value(),
                new SettlementManagementActionPayload.ConfigureDepot(fixture.destination().id(), SettlementDepotPurpose.GENERAL, 9))),
                "supply visibility does not grant logistics authority");
        fixture.town().setPermissions(fixture.town().leader(), delegate.getUUID(), Set.of());
        helper.assertTrue(SettlementManagementService.snapshot(delegate, request) == null, "revocation applies to the next refresh");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_supplies")
    public static void carriedOutputUsesOneLoadedOrBackgroundObservation(GameTestHelper helper) {
        SettlementHaulingGameTests.withFixture(helper, fixture -> {
            var level = helper.getLevel();
            var town = fixture.town();
            var villager = fixture.courier();
            town.configureDepot(town.leader(), fixture.destination().id(), SettlementDepotPurpose.GENERAL, 5);
            var order = HiredProductionOrder.create(level, HiredProductionRecipe.exact(ResourceLocation.withDefaultNamespace("bread"), Map.of()),
                    HiredProductionOrder.Mode.MAINTAIN_STOCK, 12, fixture.destination().position());
            HiredProductionStock.prepareDestination(HiredWorkStateStore.state(villager), false, order);
            var inventory = HiredJobInventory.getJobInventory(villager);
            int slot = inventory.outputSlots().getFirst();
            inventory.setItem(slot, new ItemStack(Items.BREAD, 4));
            town.recordWorker(SettlementWorkerState.capture(level, villager, SettlementWorkforceService.duty(villager).orElseThrow(), level.getGameTime()));
            var loaded = breadSupply(fixture.leader(), town, fixture.destination().position());
            helper.assertValueEqual(loaded.incoming(), 4L, "loaded output is counted once despite a saved worker observation");
            var restored = SettlementRecord.load(town.save(level.registryAccess()), level.registryAccess(), () -> {});
            villager.discard();
            var background = breadSupply(fixture.leader(), restored, fixture.destination().position());
            helper.assertValueEqual(background.incoming(), 4L, "the saved output replaces the loaded observation after handoff");
            helper.assertValueEqual(background.stored(), 0L, "carried items remain separate from physical storage");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_supplies")
    public static void courierQueueAndReceiveClaimAreOneIncomingDelivery(GameTestHelper helper) {
        SettlementHaulingGameTests.withFixture(helper, fixture -> {
            var town = fixture.town();
            var level = helper.getLevel();
            town.setWorksitePlan(town.leader(), fixture.area().id(), SettlementWorksitePlan.defaults(fixture.area().type().role()));
            var queue = town.beginTransfers(town.leader(), fixture.area().id());
            var order = HiredTransferQueue.Order.create(new HiredTransferQueue.Endpoint(fixture.source().position(), fixture.source().id()),
                    new HiredTransferQueue.Endpoint(fixture.destination().position(), fixture.destination().id()), new ItemStack(Items.IRON_INGOT), 7);
            queue.append(order);
            var allocation = queue.reserve(fixture.courier().getUUID(), order.id(), 3);
            helper.assertTrue(allocation != null, "the courier reserves a delivery");
            queue.picked(allocation.id(), 3);
            SettlementTransferService.bind(level, fixture.courier(), town, order);
            com.jvn.villagerretaliation.inventory.AssignedStorageService.reconcileCourierClaims(fixture.courier(), List.of(new ItemStack(Items.IRON_INGOT, 3)));
            helper.assertValueEqual(com.jvn.villagerretaliation.inventory.AssignedStorageService.reservedItemCount(level,
                    fixture.destination().position(), null, com.jvn.villagerretaliation.item.VillagerFilterPolicy.TransferOperation.RECEIVE,
                    new ItemStack(Items.IRON_INGOT)), 3, "the loaded courier also owns a receive reservation");
            var supply = SettlementSupplyService.supplies(fixture.leader(), town).stream()
                    .filter(row -> row.position().equals(fixture.destination().position()) && row.item().is(Items.IRON_INGOT)).findFirst().orElseThrow();
            helper.assertValueEqual(supply.incoming(), 3L, "the queue and receive reservation describe one delivery");
            helper.assertValueEqual(supply.stored(), 0L, "the pending delivery has not arrived");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_supplies")
    public static void inaccessibleStorageKeepsItsContentsUnknown(GameTestHelper helper) {
        SettlementHaulingGameTests.withFixture(helper, fixture -> {
            fixture.destination().setItem(0, new ItemStack(Items.IRON_INGOT, 5));
            fixture.destination().setStockTarget(new ItemStack(Items.IRON_INGOT), 10);
            java.util.function.Consumer<net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock> deny = event -> {
                if (event.getPos().equals(fixture.destination().position())) event.setCanceled(true);
            };
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(deny);
            try {
                var supply = SettlementSupplyService.supplies(fixture.leader(), fixture.town()).stream()
                        .filter(row -> row.position().equals(fixture.destination().position()) && row.item().is(Items.IRON_INGOT)).findFirst().orElseThrow();
                helper.assertValueEqual(supply.status(), "inaccessible", "storage protection is reported explicitly");
                helper.assertValueEqual(supply.stored(), -1L, "denied inventory contents are not exposed");
                helper.assertValueEqual(supply.shortage(), -1L, "unknown stock cannot become a false shortage");
            } finally {
                net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(deny);
            }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_supplies")
    public static void constructionSourcesExposeExistingMissingMaterialDiagnostics(GameTestHelper helper) {
        SettlementBackgroundConstructionGameTests.fixture(helper, false,
                Map.of(BlockPos.ZERO, net.minecraft.world.level.block.Blocks.OAK_PLANKS.defaultBlockState()), fixture -> {
            var town = fixture.town();
            fixture.depot().setStockTarget(new ItemStack(Items.OAK_PLANKS), 4);
            for (int tick = 0; tick < 10; tick++) SettlementBackgroundConstruction.tick(helper.getLevel(), town, fixture.worker(), 200, 1.0D);
            helper.assertFalse(fixture.project().missingMaterials().isBlank(), "construction reports its missing materials");
            var viewer = FakePlayerFactory.get(helper.getLevel(), new GameProfile(town.leader(), "ProjectViewer"));
            var supply = SettlementSupplyService.supplies(viewer, town).stream()
                    .filter(row -> fixture.depot().id().equals(row.depot()) && row.item().is(Items.OAK_PLANKS)).findFirst().orElseThrow();
            var project = SettlementSupplyService.links(viewer, town, supply.id().toString()).stream()
                    .filter(link -> link.kind() == SupplyLinkKind.PROJECT).findFirst().orElseThrow();
            helper.assertValueEqual(project.targetId(), fixture.project().id(), "the source links to the actual project");
            helper.assertTrue(project.detail().contains(fixture.project().missingMaterials()), "the overview preserves the existing missing-material details");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_supplies")
    public static void internalTransfersDoNotInflatePurchasingStock(GameTestHelper helper) {
        var fixture = fixture(helper);
        var town = fixture.town();
        fixture.source().setItem(0, new ItemStack(Items.BREAD, 10));
        var queue = town.beginTransfers(town.leader(), fixture.storage().id());
        var order = HiredTransferQueue.Order.create(new HiredTransferQueue.Endpoint(fixture.source().position(), fixture.source().id()),
                new HiredTransferQueue.Endpoint(fixture.destination().position(), fixture.destination().id()), new ItemStack(Items.BREAD), 6);
        queue.append(order);
        var allocation = queue.reserve(UUID.randomUUID(), order.id(), 6);
        var pool = List.of(fixture.source(), fixture.destination());
        helper.assertValueEqual(SettlementSupplyStock.projected(helper.getLevel(), town, pool, new ItemStack(Items.BREAD)), 10L,
                "planned internal cargo is already part of stored stock");
        fixture.source().removeItem(0, 6);
        queue.picked(allocation.id(), 6);
        helper.assertValueEqual(SettlementSupplyStock.projected(helper.getLevel(), town, pool, new ItemStack(Items.BREAD)), 10L,
                "picked internal cargo replaces its source stock once");
        fixture.destination().setItem(0, new ItemStack(Items.BREAD, 6));
        queue.delivered(allocation.id(), 6);
        helper.assertValueEqual(SettlementSupplyStock.projected(helper.getLevel(), town, pool, new ItemStack(Items.BREAD)), 10L,
                "delivery preserves the purchasing balance");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_supplies")
    public static void maintainedCivicProductionAcceptsOrdinaryOutputStorage(GameTestHelper helper) {
        SettlementHaulingGameTests.withFixture(helper, fixture -> {
            var level = helper.getLevel();
            var town = fixture.town();
            var workshop = town.areas().values().stream().filter(area -> area.type() == SettlementAreaType.WORKSHOP).findFirst().orElseThrow();
            var position = workshop.min().above();
            level.setBlockAndUpdate(position, net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState());
            var container = (net.minecraft.world.Container) level.getBlockEntity(position);
            container.setItem(0, new ItemStack(Items.STICK, 8));
            var defaults = SettlementWorksitePlan.defaults(workshop.type().role());
            var storage = new SettlementStorageLinks(false, List.of(new SettlementStorageLinks.Link(position, SettlementStorageLinks.Purpose.OUTPUT, 0)));
            town.setWorksitePlan(town.leader(), workshop.id(), new SettlementWorksitePlan(defaults.settings().withStorage(storage), 1));
            var order = HiredProductionOrder.create(level, HiredProductionRecipe.exact(ResourceLocation.withDefaultNamespace("stick"), Map.of()),
                    HiredProductionOrder.Mode.MAINTAIN_STOCK, 16, position);
            helper.assertValueEqual(SettlementProductionService.append(fixture.leader(), town, workshop.id(), 0, order), "", "ordinary output storage accepts a shared order");
            var queue = town.productionQueues().get(workshop.id());
            helper.assertValueEqual(SettlementProductionStock.count(level, town, queue, order), 8L, "maintained stock counts the live chest");
            level.setBlockAndUpdate(position, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            helper.assertValueEqual(SettlementProductionStock.count(level, town, queue, order), -1L, "missing output remains unknown");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_supplies")
    public static void consumerDemandSharesStockAndReleasesProtectionWhenPaused(GameTestHelper helper) {
        var fixture = fixture(helper);
        var town = fixture.town();
        var a = area(town, SettlementAreaType.KITCHEN, "First bakery", 5);
        var b = area(town, SettlementAreaType.KITCHEN, "Second bakery", 10);
        var first = bread(helper, town, a, fixture.destination().position(), 4);
        var second = bread(helper, town, b, fixture.destination().position(), 8);
        fixture.source().setItem(0, new ItemStack(Items.WHEAT, 30));
        var demand = SettlementDemandService.inspect(helper.getLevel(), town);
        helper.assertValueEqual(demand.demands().stream().mapToLong(row -> row.requirement().quantity()).sum(), 36L,
                "remaining maintained deficits expand to exact native ingredient quantities");
        helper.assertValueEqual(demand.demands().stream().mapToLong(SettlementDemandService.Demand::supplied).sum(), 30L,
                "competing consumers share each stored item once");
        helper.assertValueEqual(demand.sellable(fixture.source(), new ItemStack(Items.WHEAT)), 0, "active consumers protect their covered inputs");
        town.productionQueues().get(b.id()).pause(second.id(), true);
        var paused = SettlementDemandService.inspect(helper.getLevel(), town);
        helper.assertValueEqual(paused.sellable(fixture.source(), new ItemStack(Items.WHEAT)), 18, "pausing releases future demand");
        fixture.source().setStockTarget(new ItemStack(Items.WHEAT), 15);
        helper.assertValueEqual(SettlementDemandService.inspect(helper.getLevel(), town).sellable(fixture.source(), new ItemStack(Items.WHEAT)), 15,
                "stock floors and covered requirements use their maximum");
        var restored = SettlementRecord.load(town.save(helper.getLevel().registryAccess()), helper.getLevel().registryAccess(), () -> {});
        helper.assertValueEqual(SettlementDemandService.inspect(helper.getLevel(), restored).sellable(restored.depots().get(fixture.source().id()), new ItemStack(Items.WHEAT)), 15,
                "durable orders preserve demand protection across reload");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_supplies")
    public static void recipeDemandRoundsBatchesAndKeepsAlternativesGrouped(GameTestHelper helper) {
        var recipe = HiredProductionRecipe.exact(ResourceLocation.withDefaultNamespace("stick"), Map.of());
        var plan = com.jvn.villagerretaliation.interaction.work.production.WorkProductionDemand.describe(helper.getLevel(), recipe, 5, List.of());
        helper.assertValueEqual(plan.requirements().stream().mapToLong(row -> row.quantity()).sum(), 4L,
                "five sticks need two native four-stick batches and four planks");
        helper.assertValueEqual(plan.requirements().size(), 1, "equivalent ingredient groups merge");
        helper.assertTrue(plan.requirements().getFirst().alternatives().size() > 1, "plank alternatives remain a flexible requirement");
        var supplied = com.jvn.villagerretaliation.interaction.work.production.WorkProductionDemand.describe(helper.getLevel(), recipe, 5,
                List.of(new ItemStack(Items.OAK_LOG)));
        helper.assertValueEqual(supplied.requirements().size(), 1, "native intermediate crafting uses its surplus for the second batch");
        helper.assertTrue(supplied.requirements().getFirst().alternatives().getFirst().is(Items.OAK_LOG), "the actual log is protected");
        helper.assertValueEqual(supplied.requirements().getFirst().quantity(), 1L, "intermediate surplus is consumed once");
        var cooked = com.jvn.villagerretaliation.interaction.work.production.WorkProductionDemand.describe(helper.getLevel(),
                HiredProductionRecipe.exact(ResourceLocation.withDefaultNamespace("iron_ingot_from_smelting_raw_iron"), Map.of()), 3, List.of());
        helper.assertValueEqual(cooked.requirements().stream().filter(row -> row.key().equals("fuel")).findFirst().orElseThrow().quantity(), 600L,
                "furnace fuel is measured in burn ticks");
        helper.succeed();
    }

    private static Supply breadSupply(ServerPlayer player, SettlementRecord town, BlockPos position) {
        return SettlementSupplyService.supplies(player, town).stream().filter(row -> row.position().equals(position)
                && row.item().is(Items.BREAD) && !row.item().has(DataComponents.CUSTOM_NAME)).findFirst().orElseThrow();
    }

    private static HiredProductionOrder bread(GameTestHelper helper, SettlementRecord town, SettlementArea area, BlockPos position, int amount) {
        var order = HiredProductionOrder.create(helper.getLevel(), HiredProductionRecipe.exact(ResourceLocation.withDefaultNamespace("bread"), Map.of()),
                HiredProductionOrder.Mode.MAINTAIN_STOCK, amount, position);
        town.beginProduction(town.leader(), area.id()).append(order);
        return order;
    }

    private static SettlementArea area(SettlementRecord town, SettlementAreaType type, String name, int offset) {
        var min = town.center().offset(offset, 0, 0);
        var area = new SettlementArea(UUID.randomUUID(), type, name, min, min.offset(3, 3, 3), 5, true);
        if (!town.putArea(town.leader(), area) || !town.setWorksitePlan(town.leader(), area.id(), SettlementWorksitePlan.defaults(type.role()))) {
            throw new IllegalStateException("Cannot register supply fixture area");
        }
        return area;
    }

    private static Fixture fixture(GameTestHelper helper) {
        var level = helper.getLevel();
        var origin = helper.absolutePos(BlockPos.ZERO);
        var center = new BlockPos(21_000_000 + origin.getX() * 8, 64, 21_000_000 + origin.getZ() * 8);
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "SupplySteward"));
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var id = registry.create(level.getGameTime(), level.dimension().location(), center, "Supply test");
        var town = SettlementSavedData.get(level).establish(registry, id, player.getUUID(), center, level.getGameTime()).orElseThrow();
        SettlementTestPlots.claim(level, town, center, center.offset(15, 3, 12));
        var storage = area(town, SettlementAreaType.DEPOT, "Store", 0);
        var source = town.registerDepot(town.leader(), storage.id(), center, SettlementDepotPurpose.GENERAL);
        var destination = town.registerDepot(town.leader(), storage.id(), center.east(2), SettlementDepotPurpose.GENERAL);
        source.observeRules(List.of());
        destination.observeRules(List.of());
        return new Fixture(player, town, storage, source, destination);
    }

    private static void roundTrip(GameTestHelper helper, SettlementManagementPayload payload) {
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            SettlementManagementPayload.STREAM_CODEC.encode(buffer, payload);
            var decoded = SettlementManagementPayload.STREAM_CODEC.decode(buffer);
            helper.assertValueEqual(decoded.total(), payload.total(), "wire round trip retains pagination");
            helper.assertValueEqual(decoded.entries().size(), payload.entries().size(), "wire round trip retains every entry");
            for (int index = 0; index < decoded.entries().size(); index++) {
                var expected = payload.entries().get(index);
                var actual = decoded.entries().get(index);
                helper.assertValueEqual(actual.id(), expected.id(), "wire round trip retains exact row identities");
                if (actual instanceof Supply supply && expected instanceof Supply original) {
                    helper.assertTrue(ItemStack.matches(supply.item(), original.item()), "wire round trip preserves item components");
                    helper.assertValueEqual(supply.stored(), original.stored(), "wire round trip preserves stock quantities");
                } else helper.assertValueEqual(actual, expected, "wire round trip preserves source references and diagnostics");
            }
        } finally {
            buffer.release();
        }
    }

    private record Fixture(ServerPlayer leader, SettlementRecord town, SettlementArea storage, SettlementDepot source, SettlementDepot destination) { }
}
