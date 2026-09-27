package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.AllegianceAssignmentSource;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceApi;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.interaction.HiredWorkStateStore;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderStructureCatalog;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderStructureScanner;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderTaskState;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class SettlementConstructionQueueGameTests {
    private static final ResourceLocation HOUSE = ResourceLocation.withDefaultNamespace("village/plains/houses/plains_small_house_1");

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_construction_queue")
    public static void directOrdersValidatePermissionStageBoundsConflictsAndReplay(GameTestHelper helper) {
        var f = fixture(helper);
        var outsider = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "OrderOutsider"));
        var anchor = site(helper, f, 0, Items.OAK_PLANKS, 2);
        helper.assertFalse(SettlementBlueprintService.order(outsider, f.town(), HOUSE, anchor, Rotation.NONE).isEmpty(), "outsiders cannot order");
        helper.assertFalse(order(f, anchor).isEmpty(), "Camp cannot order");
        f.town().advance();
        helper.assertFalse(order(f, anchor.east(f.town().radius() + 20)).isEmpty(), "village bounds are enforced");
        helper.assertFalse(order(f, anchor.atY(helper.getLevel().getMaxBuildHeight())).isEmpty(), "world height is enforced");
        helper.assertFalse(SettlementBlueprintService.order(f.leader(), f.town(), ResourceLocation.withDefaultNamespace("missing"),
                anchor, Rotation.NONE).isEmpty(), "unknown catalog ids are rejected");
        var target = f.plan().worldPos(origin(f, anchor), f.plan().blocks().getFirst());
        var before = helper.getLevel().getBlockState(target);
        helper.getLevel().setBlock(target, Blocks.BEDROCK.defaultBlockState(), 2);
        helper.assertFalse(order(f, anchor).isEmpty(), "protected blocks reject the order");
        helper.getLevel().setBlock(target, before, 2);
        helper.assertTrue(order(f, anchor).isEmpty(), "direct orders require no paper or assigned builder");
        helper.assertFalse(order(f, anchor).isEmpty(), "pending footprints prevent duplicate ordering");
        var project = f.town().projects().values().iterator().next();
        helper.assertTrue(project.builder() == null && project.area() == null, "queued orders have no assignment");
        helper.assertTrue(SettlementConstructionService.cancel(f.leader(), f.town(), project.id()), "pending orders can be cancelled");
        helper.assertTrue(order(f, anchor).isEmpty(), "cancellation releases an unapplied footprint");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_construction_queue")
    public static void queueAndPausedProgressSurviveSaveWithoutAnAssignment(GameTestHelper helper) {
        var f = fixture(helper);
        f.town().advance();
        var first = queue(helper, f, 0, Items.OAK_PLANKS, 2);
        var second = queue(helper, f, 16, Items.OAK_PLANKS, 2);
        first.assign(UUID.randomUUID(), f.area().id());
        var progress = first.state();
        BuilderTaskState.setPlacedIndex(progress, 3);
        first.observe(progress);
        first.pause("missing_materials");
        first.waiting("missing_materials", "2 Oak Planks");
        var restored = SettlementRecord.load(f.town().save(helper.getLevel().registryAccess()), helper.getLevel().registryAccess(), () -> {});
        helper.assertValueEqual(restored.projects().get(first.id()).status(), SettlementProject.Status.PAUSED, "paused state survives");
        helper.assertTrue(restored.projects().get(first.id()).builder() == null, "save retains no builder");
        helper.assertValueEqual(restored.projects().get(first.id()).placed(), 3, "partial progress survives a paused save");
        helper.assertValueEqual(restored.projects().get(first.id()).missingMaterials(), "2 Oak Planks", "missing items survive");
        helper.assertValueEqual(new ArrayList<>(restored.projects().keySet()), List.of(first.id(), second.id()), "insertion order survives");
        helper.assertTrue(restored.projects().get(first.id()).site() != null, "independent survey bounds survive");
        helper.assertTrue(restored.projects().get(second.id()).reservesFootprint(), "queued footprint remains reserved");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_construction_queue")
    public static void fifoSkipsShortagesAndHonorsBuilderAndStageLimits(GameTestHelper helper) {
        var f = fixture(helper);
        f.town().advance();
        var first = queue(helper, f, 0, Items.OAK_PLANKS, 4);
        var second = queue(helper, f, 16, Items.COBBLESTONE, 1);
        var third = queue(helper, f, 32, Items.COBBLESTONE, 1);
        var a = builder(helper, f);
        var b = builder(helper, f);
        try {
            var depot = depot(helper, f, -12);
            depot.setItem(0, new ItemStack(Items.COBBLESTONE, 2));
            SettlementConstructionQueue.dispatch(helper.getLevel(), f.town());
            helper.assertFalse(first.active(), "a shortage leaves the oldest order pending");
            helper.assertTrue(second.active(), "the first ready order dispatches");
            helper.assertFalse(third.active(), "Hamlet limits active projects even with two builders");
            helper.assertTrue(first.builder() == null, "a shortage takes no builder");
            f.town().advance();
            SettlementConstructionQueue.dispatch(helper.getLevel(), f.town());
            helper.assertTrue(third.active(), "Village permits a second ready project: " + third.blocker() + " " + third.missingMaterials());
            helper.assertFalse(second.builder().equals(third.builder()), "parallel orders always use different builders");
            helper.assertValueEqual(f.town().queuePosition(first.id()), 1, "skipping does not move the original queue position");
        } finally {
            a.discard();
            b.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_construction_queue")
    public static void combinedDepotStockIsAllocatedOnceAcrossParallelOrders(GameTestHelper helper) {
        var f = fixture(helper);
        f.town().advance();
        f.town().advance();
        var first = queue(helper, f, 0, Items.OAK_PLANKS, 4);
        var second = queue(helper, f, 16, Items.OAK_PLANKS, 4);
        var a = builder(helper, f);
        var b = builder(helper, f);
        try {
            var left = depot(helper, f, -12);
            var right = depot(helper, f, -18);
            left.setItem(0, new ItemStack(Items.OAK_PLANKS, 2));
            right.setItem(0, new ItemStack(Items.OAK_PLANKS, 2));
            SettlementConstructionQueue.dispatch(helper.getLevel(), f.town());
            helper.assertTrue(first.active(), "stock is aggregated across depots");
            helper.assertFalse(second.active(), "the same stock cannot dispatch two projects");
            helper.assertValueEqual(SettlementStock.count(f.town(), stack -> stack.is(Items.OAK_PLANKS)), 4, "dispatch reserves without consuming");
            right.setItem(0, new ItemStack(Items.OAK_PLANKS, 6));
            SettlementConstructionQueue.dispatch(helper.getLevel(), f.town());
            helper.assertTrue(second.active(), "additional stock admits the next order: " + second.blocker() + " " + second.missingMaterials());
        } finally {
            a.discard();
            b.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_construction_queue")
    public static void shortagePausesAndResumesWhileCarriedStockIsConserved(GameTestHelper helper) {
        var f = fixture(helper);
        f.town().advance();
        var project = queue(helper, f, 0, Items.OAK_PLANKS, 4);
        var a = builder(helper, f);
        try {
            var depot = depot(helper, f, -12);
            depot.setItem(0, new ItemStack(Items.OAK_PLANKS, 4));
            SettlementConstructionQueue.dispatch(helper.getLevel(), f.town());
            helper.assertTrue(project.active(), "funded order dispatches");
            depot.removeItem(0, 1);
            HiredJobInventory.getJobInventory(a).setItem(HiredJobInventory.HOTBAR_START, new ItemStack(Items.OAK_PLANKS));
            SettlementConstructionQueue.dispatch(helper.getLevel(), f.town());
            helper.assertTrue(project.active(), "carried supplies count once alongside the depots");
            depot.removeItem(0, 1);
            SettlementConstructionQueue.dispatch(helper.getLevel(), f.town());
            helper.assertValueEqual(project.status(), SettlementProject.Status.PAUSED, "a real shortage pauses");
            helper.assertTrue(project.builder() == null && f.town().activeProject(a.getUUID()) == null, "pause frees the builder");
            helper.assertFalse(BuilderTaskState.hasTask(HiredWorkStateStore.state(a)), "paused native task is cleared");
            depot.setItem(0, new ItemStack(Items.OAK_PLANKS, 3));
            SettlementConstructionQueue.dispatch(helper.getLevel(), f.town());
            helper.assertTrue(project.active(), "restocking resumes the same project");
            helper.assertValueEqual(HiredJobInventory.getJobInventory(a).getItem(HiredJobInventory.HOTBAR_START).getCount(), 1, "resumption cannot duplicate carried stock");
            helper.assertTrue(SettlementConstructionService.cancel(f.leader(), f.town(), project.id()), "active orders can be cancelled");
            helper.assertValueEqual(SettlementStock.count(f.town(), stack -> stack.is(Items.OAK_PLANKS)), 3, "cancellation does not create a refund");
        } finally {
            a.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_construction_queue")
    public static void dutyLossRequeuesProgressForAnotherAdultBuilder(GameTestHelper helper) {
        var f = fixture(helper);
        f.town().advance();
        var project = queue(helper, f, 0, Items.OAK_PLANKS, 4);
        var a = builder(helper, f);
        var b = builder(helper, f);
        try {
            var depot = depot(helper, f, -12);
            depot.setItem(0, new ItemStack(Items.OAK_PLANKS, 4));
            SettlementConstructionQueue.dispatch(helper.getLevel(), f.town());
            UUID original = project.builder();
            var originalBuilder = original.equals(a.getUUID()) ? a : b;
            var state = HiredWorkStateStore.state(originalBuilder);
            BuilderTaskState.setPlacedIndex(state, 1);
            SettlementConstructionService.observe(originalBuilder, state);
            f.town().removeDuty(original);
            helper.assertValueEqual(project.status(), SettlementProject.Status.PAUSED, "losing duty requeues instead of cancelling");
            SettlementConstructionQueue.dispatch(helper.getLevel(), f.town());
            helper.assertTrue(project.active() && !original.equals(project.builder()), "a different eligible resident takes over");
            helper.assertValueEqual(project.placed(), 1, "handoff preserves progress");
            helper.assertFalse(project.within(f.area()), "the project is outside the duty area");
            helper.assertTrue(project.within(project.site().area()), "the project's own survey controls the building site");
        } finally {
            a.discard();
            b.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_construction_queue")
    public static void directBuilderPlacesAndConsumesOutsideItsDutyArea(GameTestHelper helper) {
        var f = fixture(helper);
        f.town().advance();
        var project = queue(helper, f, 0, Items.OAK_PLANKS, 1);
        var builder = builder(helper, f);
        try {
            var inventory = HiredJobInventory.getJobInventory(builder);
            inventory.setItem(HiredJobInventory.HOTBAR_START, new ItemStack(Items.OAK_PLANKS));
            SettlementConstructionQueue.dispatch(helper.getLevel(), f.town());
            helper.assertTrue(project.active(), "carried stock can dispatch an order");
            var target = f.plan().blocks().stream().filter(block -> block.requiredItem().is(Items.OAK_PLANKS)).findFirst().orElseThrow();
            var targetPos = f.plan().worldPos(project.origin(), target);
            builder.setPos(new BlockPos(project.min().getX() - 2, targetPos.getY(), targetPos.getZ()).getBottomCenter());
            var session = com.jvn.villagerretaliation.interaction.HiredWorkSession.create(helper.getLevel(), builder,
                    com.jvn.villagerretaliation.interaction.HiredVillagerRole.BUILDER);
            var worker = new com.jvn.villagerretaliation.interaction.work.builder.BuilderWorker();
            for (int i = 0; i < 8 && project.active(); i++) worker.tick(helper.getLevel(), builder, null, session.context());
            helper.assertValueEqual(project.status(), SettlementProject.Status.COMPLETED, "direct orders use the native placement path: " + project.blocker());
            helper.assertTrue(helper.getLevel().getBlockState(targetPos).equals(target.state()), "the intended block is placed");
            helper.assertTrue(inventory.getItem(HiredJobInventory.HOTBAR_START).isEmpty(), "one placement consumes one carried item");
        } finally {
            builder.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_construction_queue")
    public static void deadAndDisabledBuildersDoNotHoldTheQueue(GameTestHelper helper) {
        var f = fixture(helper);
        f.town().advance();
        var project = queue(helper, f, 0, Items.OAK_PLANKS, 1);
        var first = builder(helper, f);
        var second = builder(helper, f);
        try {
            var depot = depot(helper, f, -12);
            depot.setItem(0, new ItemStack(Items.OAK_PLANKS));
            first.setAge(-24_000);
            SettlementConstructionQueue.dispatch(helper.getLevel(), f.town());
            helper.assertValueEqual(project.builder(), second.getUUID(), "children cannot claim construction work");
            second.kill();
            first.setAge(0);
            SettlementConstructionQueue.dispatch(helper.getLevel(), f.town());
            helper.assertValueEqual(project.builder(), first.getUUID(), "a builder death frees the order for an adult");
            var settings = SettlementWorkerSettings.capture(com.jvn.villagerretaliation.interaction.HiredVillagerRole.BUILDER,
                    HiredWorkStateStore.state(first)).with(SettlementWorkerSetting.ENABLED, 0);
            f.town().setWorkerSettings(f.leader().getUUID(), first.getUUID(), settings);
            SettlementConstructionQueue.dispatch(helper.getLevel(), f.town());
            helper.assertTrue(project.builder() == null && !project.active(), "disabled construction duty cannot hold a slot");
            helper.assertValueEqual(SettlementStock.count(f.town(), stack -> stack.is(Items.OAK_PLANKS)), 1, "handoffs do not consume or refund materials");
        } finally {
            first.discard();
            second.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_construction_queue")
    public static void preflightIsReadOnlyAndOrderPayloadUsesCardinalRotations(GameTestHelper helper) {
        var f = fixture(helper);
        f.town().advance();
        var anchor = site(helper, f, 0, Items.OAK_PLANKS, 1);
        helper.assertTrue(SettlementBlueprintService.order(f.leader(), f.town(), HOUSE, anchor, Rotation.NONE, false).isEmpty(),
                "preflight validates a legal site");
        helper.assertTrue(f.town().projects().isEmpty(), "preflight cannot queue a project");
        var command = new com.jvn.villagerretaliation.network.SettlementManagementActionPayload.OrderStructure(
                HOUSE, anchor, Rotation.COUNTERCLOCKWISE_90, 17, true);
        var payload = new com.jvn.villagerretaliation.network.SettlementManagementActionPayload(f.town().id().value(), command);
        var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            com.jvn.villagerretaliation.network.SettlementManagementActionPayload.STREAM_CODEC.encode(buffer, payload);
            helper.assertValueEqual(com.jvn.villagerretaliation.network.SettlementManagementActionPayload.STREAM_CODEC.decode(buffer), payload,
                    "village, structure, anchor, cardinal rotation and request identity round trip");
        } finally {
            buffer.release();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_construction_queue")
    public static void backgroundBuilderTravelsFromDutyToIndependentSiteAndConsumesOnce(GameTestHelper helper) {
        var f = fixture(helper);
        f.town().advance();
        var project = queue(helper, f, 0, Items.OAK_PLANKS, 1);
        var builder = builder(helper, f);
        try {
            var site = project.site();
            for (var pos : BlockPos.betweenClosed(site.area().min(),
                    new BlockPos(site.area().max().getX(), project.min().getY() - 1, site.area().max().getZ()))) {
                helper.getLevel().setBlock(pos, Blocks.DIRT.defaultBlockState(), 2);
            }
            HiredJobInventory.getJobInventory(builder).setItem(HiredJobInventory.HOTBAR_START, new ItemStack(Items.OAK_PLANKS));
            SettlementConstructionQueue.dispatch(helper.getLevel(), f.town());
            var worker = f.town().workers().get(builder.getUUID());
            helper.assertTrue(worker != null && !site.area().contains(worker.position()), "background travel starts outside the independent site");
            SettlementCareService.onUnloaded(builder);
            builder.remove(net.minecraft.world.entity.Entity.RemovalReason.UNLOADED_TO_CHUNK);
            helper.assertTrue(project.active(), "unloading preserves the dispatched order");
            for (int i = 0; i < 60 && project.active(); i++) {
                SettlementConstructionSiteService.observeProject(helper.getLevel(), f.town(), project, null);
                SettlementBackgroundConstruction.tick(helper.getLevel(), f.town(), worker, 200, 1.0D);
            }
            helper.assertValueEqual(project.status(), SettlementProject.Status.COMPLETED,
                    "background construction reaches the independent site: " + worker.status() + " / " + project.blocker());
            SettlementConstructionSiteService.update(helper.getLevel(), f.town());
            for (var block : f.plan().blocks()) helper.assertTrue(BuilderStructureScanner.sameSchematicState(
                    helper.getLevel().getBlockState(f.plan().worldPos(project.origin(), block)), block.state()), "background placement reaches the world");
            helper.assertValueEqual(worker.items(helper.getLevel().registryAccess()).stream().filter(stack -> stack.is(Items.OAK_PLANKS))
                    .mapToInt(ItemStack::getCount).sum(), 0, "the only carried plank is consumed once");
        } finally {
            builder.discard();
        }
        helper.succeed();
    }

    private static Fixture fixture(GameTestHelper helper) {
        var level = helper.getLevel();
        var center = helper.absolutePos(new BlockPos(12, 4, 12));
        var leader = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "QueueLeader"));
        leader.setPos(center.getCenter());
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var id = registry.create(level.getGameTime(), level.dimension().location(), center, "");
        var town = SettlementSavedData.get(level).establish(registry, id, leader.getUUID(), center, level.getGameTime()).orElseThrow();
        var duty = new SettlementArea(UUID.randomUUID(), SettlementAreaType.CONSTRUCTION, "Construction duty",
                center.offset(-10, -1, -3), center.offset(-6, 3, 3), 5, true);
        helper.assertTrue(town.putArea(leader.getUUID(), duty), "construction duty is registered");
        var plan = BuilderStructureScanner.scan(level, BuilderStructureCatalog.byId(level.getServer(), HOUSE).orElseThrow(), Rotation.NONE).orElseThrow();
        return new Fixture(town, leader, duty, center, plan);
    }

    private static Villager builder(GameTestHelper helper, Fixture f) {
        var level = helper.getLevel();
        var builder = EntityType.VILLAGER.create(level);
        builder.setPos(f.area().center().getBottomCenter());
        builder.setNoAi(true);
        builder.setVillagerData(builder.getVillagerData().setProfession(VillagerProfession.MASON));
        VillageAllegianceApi.assignKnown(level, builder, f.town().id(), AllegianceAssignmentSource.EXPLICIT_API);
        helper.assertTrue(level.addFreshEntity(builder), "builder is loaded");
        f.town().assignDuty(builder.getUUID(), f.area().id());
        SettlementWorkforceService.reconcile(level, builder);
        SettlementCareService.onUnloaded(builder);
        return builder;
    }

    private static SettlementDepot depot(GameTestHelper helper, Fixture f, int offset) {
        var pos = f.center().offset(offset, 0, -12);
        helper.getLevel().getChunkAt(pos);
        var area = new SettlementArea(UUID.randomUUID(), SettlementAreaType.DEPOT, "Supplies", pos, pos, 5, true);
        helper.assertTrue(f.town().putArea(f.leader().getUUID(), area), "depot area is registered");
        var depot = f.town().registerDepot(f.leader().getUUID(), area.id(), pos, SettlementDepotPurpose.GENERAL);
        helper.assertTrue(depot != null, "depot fits the stage limit");
        SettlementDepotService.observeRules(helper.getLevel(), depot);
        return depot;
    }

    private static BlockPos site(GameTestHelper helper, Fixture f, int offset, Item item, int count) {
        var anchor = f.center().east(offset);
        var origin = origin(f, anchor);
        int missing = count;
        for (var block : f.plan().blocks()) {
            var pos = f.plan().worldPos(origin, block);
            helper.getLevel().getChunkAt(pos);
            boolean absent = missing > 0 && block.requiresMaterial() && block.requiredItem().is(item);
            if (absent) missing--;
            helper.getLevel().setBlock(pos, absent ? Blocks.AIR.defaultBlockState() : block.state(), 2);
        }
        helper.assertValueEqual(missing, 0, "the house contains enough test materials");
        return anchor;
    }

    private static BlockPos origin(Fixture f, BlockPos anchor) {
        return anchor.offset(-f.plan().localCenter().getX(), -f.plan().localMin().getY(), -f.plan().localCenter().getZ());
    }

    private static String order(Fixture f, BlockPos anchor) {
        return SettlementBlueprintService.order(f.leader(), f.town(), HOUSE, anchor, Rotation.NONE);
    }

    private static SettlementProject queue(GameTestHelper helper, Fixture f, int offset, Item item, int count) {
        String result = order(f, site(helper, f, offset, item, count));
        helper.assertTrue(result.isEmpty(), "valid direct order: " + result);
        return new ArrayList<>(f.town().projects().values()).getLast();
    }

    private record Fixture(SettlementRecord town, ServerPlayer leader, SettlementArea area, BlockPos center,
                           BuilderStructureScanner.StructurePlan plan) {
    }
}
