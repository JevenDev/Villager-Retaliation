package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.work.builder.BuilderConstructionCost;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderStructureCatalog;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderStructureScanner;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderWorker;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("settlement_construction_materials")
@PrefixGameTestTemplate(false)
public final class SettlementConstructionMaterialsGameTests {
    private SettlementConstructionMaterialsGameTests() { }

    @GameTest(template = "empty", timeoutTicks = 1000)
    public static void ordinaryBarrelTransfersWaitForReceiptsAndRefundOnce(GameTestHelper helper) {
        var origin = helper.absolutePos(new BlockPos(2, 2, 2));
        SettlementBackgroundConstructionGameTests.fixture(helper, origin, blocks(), false, fixture -> {
            var level = helper.getLevel();
            var position = fixture.depot().position().east();
            level.setBlockAndUpdate(position, Blocks.BARREL.defaultBlockState());
            var barrel = (net.minecraft.world.Container) level.getBlockEntity(position);
            var submitted = new ItemStack(Items.OAK_PLANKS, 4);
            submitted.set(DataComponents.CUSTOM_NAME, Component.literal("Original barrel donation"));
            barrel.setItem(0, submitted.copy());
            var storage = new SettlementStorageLinks(false, List.of(new SettlementStorageLinks.Link(position, SettlementStorageLinks.Purpose.GENERAL, 0)));
            fixture.town().setWorksitePlan(fixture.town().leader(), fixture.area().id(), new SettlementWorksitePlan(
                    SettlementWorksitePlan.defaults(com.jvn.villagerretaliation.interaction.HiredVillagerRole.BUILDER).settings().withStorage(storage), 1));
            var plan = withCost(fixture.plan(), BuilderConstructionCost.EXACT);
            SettlementTestPlots.planSupplies(level, fixture.town());
            var history = new net.minecraft.nbt.CompoundTag[2];
            helper.startSequence().thenExecute(() -> {
                helper.assertFalse(SettlementConstructionMaterials.reserve(level, fixture.town(), fixture.project(), plan, fixture.builder()), "ordinary pickup awaits durable chunk receipt");
                helper.assertTrue(barrel.isEmpty(), "write-ahead debit removes the submitted stack once");
                helper.assertTrue(SettlementConstructionMaterials.reserved(fixture.project()), "the source debit has a persistent escrow owner");
                history[0] = fixture.project().materialEscrow().copy();
            }).thenWaitUntil(() -> helper.assertTrue(SettlementConstructionMaterials.reserve(level, fixture.town(), fixture.project(), plan, fixture.builder()),
                    "source chunk checkpoint completes before escrow becomes spendable"))
                    .thenExecute(() -> {
                        restoreLedger(fixture.project(), history[0]);
                        helper.assertTrue(SettlementConstructionMaterials.reserve(level, fixture.town(), fixture.project(), plan, fixture.builder()), "older ledger reconciles the newer source receipt");
                        helper.assertTrue(barrel.isEmpty(), "older ledger cannot debit the source twice");
                        SettlementConstructionMaterials.refund(level, fixture.town(), fixture.project());
                        history[1] = fixture.project().materialEscrow().copy();
                    })
                    .thenWaitUntil(() -> {
                        SettlementConstructionMaterials.refund(level, fixture.town(), fixture.project());
                        helper.assertFalse(SettlementConstructionMaterials.refundPending(fixture.project()), "return chunk receipt completes before escrow retirement");
                    }).thenExecute(() -> {
                        helper.assertTrue(ItemStack.matches(submitted, barrel.getItem(0)), "ordinary return preserves exact stack data");
                        restoreLedger(fixture.project(), history[1]);
                        SettlementConstructionMaterials.refund(level, fixture.town(), fixture.project());
                        helper.assertValueEqual(barrel.getItem(0).getCount(), 4, "older refund ledger uses the receipt without duplicate insertion");
                        helper.assertFalse(SettlementConstructionMaterials.refundPending(fixture.project()), "older refund ledger retires after receipt replay");
                        SettlementConstructionMaterials.validate(fixture.project().materialEscrow());
                    }).thenSucceed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 1000)
    public static void cancellationDuringOrdinaryDebitRetainsExactlyOneRefund(GameTestHelper helper) {
        SettlementBackgroundConstructionGameTests.fixture(helper, helper.absolutePos(new BlockPos(2, 2, 2)), blocks(), false, fixture -> {
            var level = helper.getLevel();
            var position = fixture.depot().position().east();
            var barrel = ordinarySource(helper, fixture, position);
            var plan = withCost(fixture.plan(), BuilderConstructionCost.EXACT);
            SettlementTestPlots.planSupplies(level, fixture.town());
            helper.assertFalse(SettlementConstructionMaterials.reserve(level, fixture.town(), fixture.project(), plan, fixture.builder()), "debit awaits source checkpoint");
            helper.assertTrue(barrel.isEmpty(), "source is debited before cancellation");
            var reloaded = SettlementProject.load(fixture.project().save());
            restoreLedger(fixture.project(), reloaded.materialEscrow());
            fixture.project().finish(SettlementProject.Status.CANCELLED);
            helper.startSequence().thenWaitUntil(() -> {
                SettlementConstructionMaterials.refund(level, fixture.town(), fixture.project());
                helper.assertFalse(SettlementConstructionMaterials.refundPending(fixture.project()), "pending debit and cancellation refund both reach durable receipts");
            }).thenExecute(() -> {
                helper.assertValueEqual(barrel.getItem(0).getCount(), 4, "cancellation returns exactly the unspent debit");
                SettlementConstructionMaterials.refund(level, fixture.town(), fixture.project());
                helper.assertValueEqual(barrel.getItem(0).getCount(), 4, "cancel retry does not duplicate return");
                SettlementConstructionMaterials.validate(fixture.project().materialEscrow());
            }).thenSucceed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 1000)
    public static void staleOrdinaryRefundReplansAfterFullAndRemovedSource(GameTestHelper helper) {
        SettlementBackgroundConstructionGameTests.fixture(helper, helper.absolutePos(new BlockPos(2, 2, 2)), blocks(), false, fixture -> {
            var level = helper.getLevel();
            var position = fixture.depot().position().east();
            var barrel = ordinarySource(helper, fixture, position);
            var plan = withCost(fixture.plan(), BuilderConstructionCost.EXACT);
            SettlementTestPlots.planSupplies(level, fixture.town());
            helper.startSequence().thenWaitUntil(() -> helper.assertTrue(SettlementConstructionMaterials.reserve(level, fixture.town(), fixture.project(), plan, fixture.builder()), "ordinary source debit becomes durable"))
                    .thenExecute(() -> {
                        // Persist an insertion intent without applying it, as at the crash boundary.
                        barrel.setItem(0, new ItemStack(Items.OAK_PLANKS, 4));
                        var intent = SettlementConstructionTransfers.debit(level, position, barrel, Map.of(0, 4), java.util.UUID.randomUUID());
                        barrel.setItem(0, ItemStack.EMPTY);
                        var change = intent.getList("Changes", Tag.TAG_COMPOUND).getCompound(0);
                        var before = change.getCompound("Before").copy();
                        change.put("Before", change.getCompound("After").copy());
                        change.put("After", before);
                        intent.putBoolean("Refund", true);
                        intent.putInt("Lot", 0);
                        intent.putInt("Amount", 4);
                        var refunds = new net.minecraft.nbt.ListTag();
                        refunds.add(intent);
                        fixture.project().materialEscrow().put("Refunds", refunds);
                        helper.assertTrue(SettlementConstructionJournal.persist(level), "unapplied refund intent is durable");
                        restoreLedger(fixture.project(), SettlementProject.load(fixture.project().save()).materialEscrow());
                        for (int slot = 0; slot < barrel.getContainerSize(); slot++) barrel.setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
                        SettlementConstructionMaterials.refund(level, fixture.town(), fixture.project());
                        helper.assertTrue(fixture.project().materialEscrow().getList("Refunds", Tag.TAG_COMPOUND).getCompound(0).getBoolean("Abandoned"), "no receipt proves stale insertion can be replanned");
                        helper.assertValueEqual(remaining(fixture.project()), 4, "full ordinary source retains all escrow");
                        level.setBlockAndUpdate(position, Blocks.AIR.defaultBlockState());
                        SettlementConstructionMaterials.refund(level, fixture.town(), fixture.project());
                        helper.assertValueEqual(remaining(fixture.project()), 4, "removed ordinary source retains all escrow");
                        helper.assertValueEqual(SettlementConstructionMaterials.blocker(fixture.project()), "refund_pending", "unavailable return remains visible");
                        level.setBlockAndUpdate(position, Blocks.BARREL.defaultBlockState());
                    }).thenWaitUntil(() -> {
                        SettlementConstructionMaterials.refund(level, fixture.town(), fixture.project());
                        helper.assertFalse(SettlementConstructionMaterials.refundPending(fixture.project()), "restored authorized source receives replanned return");
                    }).thenExecute(() -> {
                        var replacement = (net.minecraft.world.Container) level.getBlockEntity(position);
                        helper.assertValueEqual(replacement.getItem(0).getCount(), 4, "only retained four materials return");
                        SettlementConstructionMaterials.refund(level, fixture.town(), fixture.project());
                        helper.assertValueEqual(replacement.getItem(0).getCount(), 4, "replanned return cannot repeat");
                        SettlementConstructionMaterials.validate(fixture.project().materialEscrow());
                    }).thenSucceed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 1000)
    public static void managedNativePlacementJournalsDebitAndReplaysOnce(GameTestHelper helper) {
        SettlementBackgroundConstructionGameTests.fixture(helper, helper.absolutePos(new BlockPos(2, 2, 2)), blocks(), false, fixture -> {
            var level = helper.getLevel();
            level.setBlockAndUpdate(fixture.depot().position(), com.jvn.villagerretaliation.block.VillagerRetaliationBlocks.SETTLEMENT_DEPOT.get().defaultBlockState());
            ((com.jvn.villagerretaliation.block.SettlementDepotBlockEntity) level.getBlockEntity(fixture.depot().position())).bind(fixture.town().id(), fixture.depot().id());
            fixture.depot().observeRules(List.of());
            fixture.depot().setItem(0, new ItemStack(Items.OAK_PLANKS, 4));
            var plan = withCost(fixture.plan(), BuilderConstructionCost.EXACT);
            SettlementTestPlots.planSupplies(level, fixture.town());
            helper.assertTrue(SettlementConstructionMaterials.reserve(level, fixture.town(), fixture.project(), plan, fixture.builder()), "native placement has complete escrow");
            SettlementConstructionMaterials.collect(fixture.town(), fixture.project(), fixture.builder().getUUID(), fixture.depot().position());
            var group = BuilderWorker.placementGroup(plan, fixture.origin(), plan.blocks().getFirst());
            for (var part : group.parts()) level.setBlockAndUpdate(part.worldPos(), Blocks.AIR.defaultBlockState());
            SettlementConstructionSiteService.refresh(level, fixture.site());
            var state = com.jvn.villagerretaliation.interaction.HiredWorkStateStore.state(fixture.builder());
            helper.assertTrue(SettlementConstructionMaterials.place(fixture.builder(), state, group), "native hook queues and applies managed output");
            helper.assertTrue(level.getBlockState(group.materialPart().worldPos()).is(Blocks.OAK_PLANKS), "paid output reaches real world");
            helper.assertValueEqual(remaining(fixture.project()), 3, "world placement spends exactly one item");
            var restored = SettlementProject.load(fixture.project().save());
            helper.startSequence().thenWaitUntil(() -> {
                SettlementConstructionSiteService.refresh(level, fixture.site());
                helper.assertFalse(fixture.site().unsettled(), "managed placement reaches its durable chunk receipt");
            }).thenExecute(() -> {
                SettlementConstructionSiteService.refresh(level, restored.site());
                helper.assertFalse(restored.site().unsettled(), "reloaded pending placement recognizes durable receipt");
                helper.assertTrue(SettlementConstructionMaterials.place(fixture.builder(), state, group), "replaying matching world output is harmless");
                helper.assertValueEqual(remaining(fixture.project()), 3, "replay cannot charge twice");
                SettlementConstructionMaterials.refund(level, fixture.town(), fixture.project());
                helper.assertValueEqual(fixture.depot().count(stack -> stack.is(Items.OAK_PLANKS)), 3, "cancellation returns only three unspent items");
                helper.assertTrue(level.getBlockState(group.materialPart().worldPos()).is(Blocks.OAK_PLANKS), "spent output survives cancellation");
            }).thenSucceed();
        });
    }

    private static net.minecraft.world.Container ordinarySource(GameTestHelper helper, SettlementBackgroundConstructionGameTests.Fixture fixture, BlockPos position) {
        var level = helper.getLevel();
        level.setBlockAndUpdate(position, Blocks.BARREL.defaultBlockState());
        var barrel = (net.minecraft.world.Container) level.getBlockEntity(position);
        barrel.setItem(0, new ItemStack(Items.OAK_PLANKS, 4));
        var storage = new SettlementStorageLinks(false, List.of(new SettlementStorageLinks.Link(position, SettlementStorageLinks.Purpose.GENERAL, 0)));
        fixture.town().setWorksitePlan(fixture.town().leader(), fixture.area().id(), new SettlementWorksitePlan(
                SettlementWorksitePlan.defaults(com.jvn.villagerretaliation.interaction.HiredVillagerRole.BUILDER).settings().withStorage(storage), 1));
        return barrel;
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void broadTagsCannotStealSpecificItemRequirements(GameTestHelper helper) {
        SettlementBackgroundConstructionGameTests.fixture(helper, false, blocks(), fixture -> {
            var cost = new BuilderConstructionCost(BuilderConstructionCost.Mode.SIMPLIFIED, List.of(
                    new BuilderConstructionCost.Ingredient(ResourceLocation.withDefaultNamespace("logs"), true, 3),
                    new BuilderConstructionCost.Ingredient(ResourceLocation.withDefaultNamespace("oak_log"), false, 3)));
            fixture.depot().setItem(0, new ItemStack(Items.OAK_LOG, 3));
            fixture.depot().setItem(1, new ItemStack(Items.BIRCH_LOG, 3));
            SettlementTestPlots.planSupplies(helper.getLevel(), fixture.town());
            helper.assertTrue(SettlementConstructionMaterials.reserve(helper.getLevel(), fixture.town(), fixture.project(),
                    withCost(fixture.plan(), cost), fixture.builder()), "specific oak row is fulfilled before general logs");
            helper.assertValueEqual(remaining(fixture.project()), 6, "each submitted stack is reserved once");
            SettlementConstructionMaterials.refund(helper.getLevel(), fixture.town(), fixture.project());
            helper.assertValueEqual(fixture.depot().count(stack -> stack.is(Items.OAK_LOG)), 3, "oak identity survives tag allocation");
            helper.assertValueEqual(fixture.depot().count(stack -> stack.is(Items.BIRCH_LOG)), 3, "birch identity survives tag allocation");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void fundedQueueActivatesOnlyAfterWholeQuoteReservation(GameTestHelper helper) {
        SettlementBackgroundConstructionGameTests.fixture(helper, true, blocks(), fixture -> {
            var level = helper.getLevel();
            level.setBlockAndUpdate(fixture.depot().position(), com.jvn.villagerretaliation.block.VillagerRetaliationBlocks.SETTLEMENT_DEPOT.get().defaultBlockState());
            ((com.jvn.villagerretaliation.block.SettlementDepotBlockEntity) level.getBlockEntity(fixture.depot().position())).bind(fixture.town().id(), fixture.depot().id());
            fixture.depot().observeRules(List.of());
            helper.assertTrue(level.addFreshEntity(fixture.builder()), "queue has a live eligible builder");
            fixture.project().reservePayment(fixture.plan().price());
            fixture.project().queue(fixture.town().leader());
            fixture.town().economy().credit(com.jvn.villagerretaliation.sell.CurrencyAmount.of(10_000, 1));
            int slot = 0;
            int quoted = 0;
            for (var requirement : BuilderConstructionCost.requirements(fixture.plan())) {
                var sample = net.minecraft.core.registries.BuiltInRegistries.ITEM.stream().map(ItemStack::new)
                        .filter(requirement::matches).findFirst().orElseThrow();
                int count = requirement.count();
                quoted += count;
                while (count > 0) {
                    int amount = Math.min(count, sample.getMaxStackSize());
                    fixture.depot().setItem(slot++, sample.copyWithCount(amount));
                    count -= amount;
                }
            }
            for (int retry = 0; retry < 3 && fixture.project().pending(); retry++) {
                SettlementTestPlots.planSupplies(level, fixture.town());
                SettlementConstructionService.tickQueue(level, fixture.town());
            }
            helper.assertTrue(fixture.project().active(), "funded eligible queue activates: " + fixture.project().blocker());
            helper.assertTrue(SettlementConstructionMaterials.reserved(fixture.project()), "activation owns a complete material escrow");
            helper.assertValueEqual(remaining(fixture.project()), quoted, "activation reserves the entire quoted bill");
            helper.assertTrue(fixture.depot().isEmpty(), "reserved stock cannot be reused by another queued project");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void quoteReservationIsAtomicAndIdempotent(GameTestHelper helper) {
        SettlementBackgroundConstructionGameTests.fixture(helper, false, blocks(), fixture -> {
            var plan = withCost(fixture.plan(), simple("oak_log", 3, "cobblestone", 2));
            fixture.depot().setItem(0, new ItemStack(Items.OAK_LOG, 3));
            SettlementTestPlots.planSupplies(helper.getLevel(), fixture.town());
            helper.assertFalse(SettlementConstructionMaterials.reserve(helper.getLevel(), fixture.town(), fixture.project(), plan, fixture.builder()), "partial quote cannot reserve");
            helper.assertValueEqual(fixture.depot().getItem(0).getCount(), 3, "failed whole quote leaves every source untouched");
            fixture.depot().setItem(1, new ItemStack(Items.COBBLESTONE, 2));
            SettlementTestPlots.planSupplies(helper.getLevel(), fixture.town());
            helper.assertTrue(SettlementConstructionMaterials.reserve(helper.getLevel(), fixture.town(), fixture.project(), plan, fixture.builder()), "complete quote reserves");
            helper.assertTrue(fixture.depot().isEmpty(), "whole bill is excluded from spendable stock");
            helper.assertTrue(SettlementConstructionMaterials.reserve(helper.getLevel(), fixture.town(), fixture.project(), plan, fixture.builder()), "activation retry reuses escrow");
            helper.assertValueEqual(remaining(fixture.project()), 5, "retry never fabricates a second reservation");
            SettlementConstructionMaterials.validate(fixture.project().materialEscrow());
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void exactRefundPreservesComponentsAcrossReload(GameTestHelper helper) {
        SettlementBackgroundConstructionGameTests.fixture(helper, false, blocks(), fixture -> {
            var sample = new ItemStack(Items.OAK_PLANKS, 4);
            sample.set(DataComponents.CUSTOM_NAME, Component.literal("Donated by Ada"));
            fixture.depot().setItem(0, sample.copy());
            var plan = withCost(fixture.plan(), BuilderConstructionCost.EXACT);
            SettlementTestPlots.planSupplies(helper.getLevel(), fixture.town());
            helper.assertTrue(SettlementConstructionMaterials.reserve(helper.getLevel(), fixture.town(), fixture.project(), plan, fixture.builder()), "exact quote accepts submitted named materials");
            var restored = SettlementRecord.load(fixture.town().save(helper.getLevel().registryAccess()), helper.getLevel().registryAccess(), () -> {});
            var project = restored.projects().get(fixture.project().id());
            SettlementConstructionMaterials.refund(helper.getLevel(), restored, project);
            var returned = restored.depots().get(fixture.depot().id()).getItem(0);
            helper.assertTrue(ItemStack.matches(sample, returned), "refund uses original item components and count");
            SettlementConstructionMaterials.refund(helper.getLevel(), restored, project);
            helper.assertValueEqual(restored.depots().get(fixture.depot().id()).count(stack -> stack.is(Items.OAK_PLANKS)), 4, "refund retry is idempotent");
            helper.assertFalse(SettlementConstructionMaterials.refundPending(project), "fully returned escrow can settle");
            SettlementConstructionMaterials.validate(project.materialEscrow());
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void fullAndRemovedSourcesRetainVisibleRefunds(GameTestHelper helper) {
        SettlementBackgroundConstructionGameTests.fixture(helper, false, blocks(), fixture -> {
            var plan = withCost(fixture.plan(), BuilderConstructionCost.EXACT);
            fixture.depot().setItem(0, new ItemStack(Items.OAK_PLANKS, 4));
            SettlementTestPlots.planSupplies(helper.getLevel(), fixture.town());
            helper.assertTrue(SettlementConstructionMaterials.reserve(helper.getLevel(), fixture.town(), fixture.project(), plan, fixture.builder()), "reserve before source is removed");
            for (int slot = 0; slot < fixture.depot().getContainerSize(); slot++) fixture.depot().setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
            SettlementConstructionMaterials.refund(helper.getLevel(), fixture.town(), fixture.project());
            helper.assertValueEqual(remaining(fixture.project()), 4, "full source leaves original stacks in escrow");
            helper.assertValueEqual(SettlementConstructionMaterials.blocker(fixture.project()), "refund_pending", "retained refund is visible");
            fixture.town().removeDepot(fixture.depot().id());
            var restored = SettlementRecord.load(fixture.town().save(helper.getLevel().registryAccess()), helper.getLevel().registryAccess(), () -> {});
            var project = restored.projects().get(fixture.project().id());
            SettlementConstructionMaterials.refund(helper.getLevel(), restored, project);
            helper.assertValueEqual(remaining(project), 4, "removed source cannot erase a saved refund");
            var fallback = restored.registerDepot(restored.leader(), fixture.depot().area(), fixture.depot().position().east(), SettlementDepotPurpose.GENERAL);
            helper.assertTrue(fallback != null, "replacement authorized storage exists");
            fallback.observeRules(List.of());
            SettlementConstructionMaterials.refund(helper.getLevel(), restored, project);
            helper.assertValueEqual(fallback.count(stack -> stack.is(Items.OAK_PLANKS)), 4, "fallback receives original unspent stacks");
            SettlementConstructionMaterials.refund(helper.getLevel(), restored, project);
            helper.assertValueEqual(fallback.count(stack -> stack.is(Items.OAK_PLANKS)), 4, "fallback refund never repeats");
            SettlementConstructionMaterials.validate(project.materialEscrow());
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void progressiveDebitCannotProduceFreeFirstBlock(GameTestHelper helper) {
        SettlementBackgroundConstructionGameTests.fixture(helper, false, blocks(), fixture -> {
            var plan = withCost(fixture.plan(), simple("oak_log", 3, "cobblestone", 2));
            fixture.depot().setItem(0, new ItemStack(Items.OAK_LOG, 3));
            fixture.depot().setItem(1, new ItemStack(Items.COBBLESTONE, 2));
            SettlementTestPlots.planSupplies(helper.getLevel(), fixture.town());
            helper.assertTrue(SettlementConstructionMaterials.reserve(helper.getLevel(), fixture.town(), fixture.project(), plan, fixture.builder()), "quote reserves");
            var block = plan.blocks().getFirst();
            var group = BuilderWorker.placementGroup(plan, fixture.origin(), block);
            var changes = List.of(new SettlementConstructionPlacement.Part(group.materialPart().worldPos(), Blocks.AIR.defaultBlockState(), block.state(), null));
            helper.assertFalse(SettlementConstructionMaterials.spend(helper.getLevel(), fixture.town(), fixture.project(), group, changes), "physical collection must precede output");
            SettlementConstructionMaterials.collect(fixture.town(), fixture.project(), fixture.builder().getUUID(), fixture.depot().position());
            helper.assertTrue(SettlementConstructionMaterials.spend(helper.getLevel(), fixture.town(), fixture.project(), group, changes), "first output charges ceiling cost");
            helper.assertValueEqual(remaining(fixture.project()), 3, "one log and one stone are spent on first quarter");
            helper.assertFalse(SettlementConstructionMaterials.spend(helper.getLevel(), fixture.town(), fixture.project(), group, changes), "replayed position cannot spend or place twice");
            var restored = SettlementRecord.load(fixture.town().save(helper.getLevel().registryAccess()), helper.getLevel().registryAccess(), () -> {});
            var project = restored.projects().get(fixture.project().id());
            SettlementConstructionMaterials.refund(helper.getLevel(), restored, project);
            var depot = restored.depots().get(fixture.depot().id());
            helper.assertValueEqual(depot.count(stack -> stack.is(Items.OAK_LOG)), 2, "cancel returns only two unspent logs");
            helper.assertValueEqual(depot.count(stack -> stack.is(Items.COBBLESTONE)), 1, "cancel returns only one unspent stone");
            SettlementConstructionMaterials.validate(project.materialEscrow());
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void completedSimplifiedBillSpendsExactTotal(GameTestHelper helper) {
        helper.assertValueEqual(SettlementConstructionMaterials.cumulative(3, 1, 1000), 1, "large projects still prepay their earliest output");
        helper.assertValueEqual(SettlementConstructionMaterials.cumulative(1_000_000, 100_000, 100_000), 1_000_000, "cumulative arithmetic avoids overflow");
        SettlementBackgroundConstructionGameTests.fixture(helper, false, blocks(), fixture -> {
            var plan = withCost(fixture.plan(), simple("oak_log", 3, "cobblestone", 2));
            fixture.depot().setItem(0, new ItemStack(Items.OAK_LOG, 3));
            fixture.depot().setItem(1, new ItemStack(Items.COBBLESTONE, 2));
            SettlementTestPlots.planSupplies(helper.getLevel(), fixture.town());
            helper.assertTrue(SettlementConstructionMaterials.reserve(helper.getLevel(), fixture.town(), fixture.project(), plan, fixture.builder()), "whole quote reserved");
            SettlementConstructionMaterials.collect(fixture.town(), fixture.project(), fixture.builder().getUUID(), fixture.depot().position());
            for (var block : plan.blocks()) {
                var group = BuilderWorker.placementGroup(plan, fixture.origin(), block);
                helper.assertTrue(SettlementConstructionMaterials.spend(helper.getLevel(), fixture.town(), fixture.project(), group,
                        List.of(new SettlementConstructionPlacement.Part(group.materialPart().worldPos(), Blocks.AIR.defaultBlockState(), block.state(), null))), "each unique output has enough prepaid cost");
            }
            SettlementConstructionMaterials.complete(helper.getLevel(), fixture.town(), fixture.project());
            helper.assertValueEqual(remaining(fixture.project()), 0, "four output groups charge exactly five quoted items");
            helper.assertTrue(fixture.depot().isEmpty(), "completion creates no refund or substitute blocks");
            SettlementConstructionMaterials.validate(fixture.project().materialEscrow());
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void exactDoubleSlabConsumesTwoItems(GameTestHelper helper) {
        var slab = Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE);
        SettlementBackgroundConstructionGameTests.fixture(helper, false, Map.of(BlockPos.ZERO, slab), fixture -> {
            var plan = withCost(fixture.plan(), BuilderConstructionCost.EXACT);
            fixture.depot().setItem(0, new ItemStack(Items.OAK_SLAB, 2));
            SettlementTestPlots.planSupplies(helper.getLevel(), fixture.town());
            helper.assertTrue(SettlementConstructionMaterials.reserve(helper.getLevel(), fixture.town(), fixture.project(), plan, fixture.builder()), "exact stacked block quote reserves both slabs");
            SettlementConstructionMaterials.collect(fixture.town(), fixture.project(), fixture.builder().getUUID(), fixture.depot().position());
            var group = BuilderWorker.placementGroup(plan, fixture.origin(), plan.blocks().getFirst());
            helper.assertTrue(SettlementConstructionMaterials.spend(helper.getLevel(), fixture.town(), fixture.project(), group,
                    List.of(new SettlementConstructionPlacement.Part(group.materialPart().worldPos(), Blocks.AIR.defaultBlockState(), slab, null))), "stacked state spends atomically");
            helper.assertValueEqual(remaining(fixture.project()), 0, "double slab cannot be built for one slab item");
        });
    }

    private static int remaining(SettlementProject project) {
        return project.materialEscrow().getList("Lots", Tag.TAG_COMPOUND).stream()
                .mapToInt(raw -> ((net.minecraft.nbt.CompoundTag) raw).getInt("Remaining")).sum();
    }

    private static void restoreLedger(SettlementProject project, net.minecraft.nbt.CompoundTag snapshot) {
        var target = project.materialEscrow();
        for (String key : List.copyOf(target.getAllKeys())) target.remove(key);
        for (String key : snapshot.getAllKeys()) target.put(key, snapshot.get(key).copy());
        SettlementConstructionMaterials.validate(target);
    }

    private static BuilderConstructionCost simple(String first, int a, String second, int b) {
        return new BuilderConstructionCost(BuilderConstructionCost.Mode.SIMPLIFIED, List.of(
                new BuilderConstructionCost.Ingredient(ResourceLocation.withDefaultNamespace(first), false, a),
                new BuilderConstructionCost.Ingredient(ResourceLocation.withDefaultNamespace(second), false, b)));
    }

    private static BuilderStructureScanner.StructurePlan withCost(BuilderStructureScanner.StructurePlan plan, BuilderConstructionCost cost) {
        var entry = plan.entry();
        return new BuilderStructureScanner.StructurePlan(new BuilderStructureCatalog.Entry(entry.id(), entry.category(), entry.label(), entry.baseCost(), cost, ""),
                plan.rotation(), plan.templateSize(), plan.localMin(), plan.localMax(), plan.blocks(), plan.materials(), plan.price(), plan.clearingBlocks());
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void filledBucketsReturnContainersAfterReloadAndCompletion(GameTestHelper helper) {
        var blocks = Map.of(BlockPos.ZERO, Blocks.WATER.defaultBlockState(), BlockPos.ZERO.east(), Blocks.LAVA.defaultBlockState(),
                BlockPos.ZERO.south(), Blocks.POWDER_SNOW.defaultBlockState(), BlockPos.ZERO.east().south(),
                Blocks.OAK_SLAB.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED, true));
        SettlementBackgroundConstructionGameTests.fixture(helper, false, blocks, fixture -> {
            var level = helper.getLevel();
            var plan = withCost(fixture.plan(), BuilderConstructionCost.EXACT);
            fixture.depot().setItem(0, new ItemStack(Items.WATER_BUCKET));
            fixture.depot().setItem(1, new ItemStack(Items.WATER_BUCKET));
            fixture.depot().setItem(2, new ItemStack(Items.LAVA_BUCKET));
            fixture.depot().setItem(3, new ItemStack(Items.POWDER_SNOW_BUCKET));
            fixture.depot().setItem(4, new ItemStack(Items.OAK_SLAB));
            SettlementTestPlots.planSupplies(level, fixture.town());
            helper.assertTrue(SettlementConstructionMaterials.reserve(level, fixture.town(), fixture.project(), plan, fixture.builder()), "the exact fluid quote reserves");
            SettlementConstructionMaterials.collect(fixture.town(), fixture.project(), fixture.builder().getUUID(), fixture.depot().position());
            for (var block : plan.blocks()) {
                var group = BuilderWorker.placementGroup(plan, fixture.origin(), block);
                helper.assertTrue(SettlementConstructionMaterials.spend(level, fixture.town(), fixture.project(), group,
                        List.of(new SettlementConstructionPlacement.Part(group.materialPart().worldPos(), Blocks.AIR.defaultBlockState(), block.state(), null))), "each fluid placement spends once");
            }
            SettlementConstructionMaterials.validate(fixture.project().materialEscrow());
            var restored = SettlementRecord.load(fixture.town().save(level.registryAccess()), level.registryAccess(), () -> {});
            var project = restored.projects().get(fixture.project().id());
            SettlementConstructionMaterials.complete(level, restored, project);
            SettlementConstructionMaterials.complete(level, restored, project);
            helper.assertValueEqual(restored.depots().get(fixture.depot().id()).count(stack -> stack.is(Items.BUCKET)), 4, "water, lava, powder snow and waterlogging return exactly four containers");
            helper.assertFalse(SettlementConstructionMaterials.refundPending(project), "all containers reached storage");
            SettlementConstructionMaterials.validate(project.materialEscrow());
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void cancelledFluidBuildRetainsContainersWhileStorageIsFull(GameTestHelper helper) {
        SettlementBackgroundConstructionGameTests.fixture(helper, false, Map.of(BlockPos.ZERO, Blocks.WATER.defaultBlockState(),
                BlockPos.ZERO.east(), Blocks.WATER.defaultBlockState()), fixture -> {
            var level = helper.getLevel();
            var plan = withCost(fixture.plan(), BuilderConstructionCost.EXACT);
            fixture.depot().setItem(0, new ItemStack(Items.WATER_BUCKET));
            fixture.depot().setItem(1, new ItemStack(Items.WATER_BUCKET));
            SettlementTestPlots.planSupplies(level, fixture.town());
            helper.assertTrue(SettlementConstructionMaterials.reserve(level, fixture.town(), fixture.project(), plan, fixture.builder()), "two water placements reserve");
            SettlementConstructionMaterials.collect(fixture.town(), fixture.project(), fixture.builder().getUUID(), fixture.depot().position());
            var block = plan.blocks().getFirst();
            var group = BuilderWorker.placementGroup(plan, fixture.origin(), block);
            helper.assertTrue(SettlementConstructionMaterials.spend(level, fixture.town(), fixture.project(), group,
                    List.of(new SettlementConstructionPlacement.Part(group.materialPart().worldPos(), Blocks.AIR.defaultBlockState(), block.state(), null))), "only the first filled bucket is used");
            for (int slot = 0; slot < fixture.depot().getContainerSize(); slot++) fixture.depot().setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
            fixture.project().finish(SettlementProject.Status.CANCELLED);
            SettlementConstructionMaterials.refund(level, fixture.town(), fixture.project());
            helper.assertTrue(SettlementConstructionMaterials.refundPending(fixture.project()), "full storage retains both filled and empty bucket ownership");
            var restored = SettlementRecord.load(fixture.town().save(level.registryAccess()), level.registryAccess(), () -> {});
            var project = restored.projects().get(fixture.project().id());
            var depot = restored.depots().get(fixture.depot().id());
            depot.setItem(0, ItemStack.EMPTY);
            depot.setItem(1, ItemStack.EMPTY);
            SettlementConstructionMaterials.refund(level, restored, project);
            SettlementConstructionMaterials.refund(level, restored, project);
            helper.assertValueEqual(depot.count(stack -> stack.is(Items.WATER_BUCKET)), 1, "the unspent filled bucket returns once");
            helper.assertValueEqual(depot.count(stack -> stack.is(Items.BUCKET)), 1, "the spent bucket container returns once after reload");
            helper.assertFalse(SettlementConstructionMaterials.refundPending(project), "both retained lots are delivered");
            SettlementConstructionMaterials.validate(project.materialEscrow());
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void reservedProjectCannotClaimAnotherProjectsMaterialsAgain(GameTestHelper helper) {
        SettlementBackgroundConstructionGameTests.fixture(helper, false, blocks(), fixture -> {
            var level = helper.getLevel();
            fixture.depot().setItem(0, new ItemStack(Items.OAK_PLANKS, 8));
            var plan = withCost(fixture.plan(), BuilderConstructionCost.EXACT);
            SettlementTestPlots.planSupplies(level, fixture.town());
            helper.assertTrue(SettlementConstructionMaterials.reserve(level, fixture.town(), fixture.project(), plan, fixture.builder()), "the first project reserves four planks");
            helper.assertValueEqual(fixture.depot().count(stack -> stack.is(Items.OAK_PLANKS)), 4, "four planks remain for another request");
            SettlementTestPlots.planSupplies(level, fixture.town());
            var next = SettlementProject.create(fixture.town(), fixture.area().id(), fixture.builder().getUUID(), plan,
                    fixture.origin().south(2), java.util.UUID.randomUUID(), level.getGameTime());
            helper.assertTrue(SettlementConstructionMaterials.reserve(level, fixture.town(), next, plan, fixture.builder()), "already owned escrow cannot allocate the remaining stock a second time");
            helper.assertTrue(fixture.depot().isEmpty(), "both projects have exactly their quoted materials");
            SettlementConstructionMaterials.validate(fixture.project().materialEscrow());
            SettlementConstructionMaterials.validate(next.materialEscrow());
        });
    }

    private static Map<BlockPos, net.minecraft.world.level.block.state.BlockState> blocks() {
        return Map.of(BlockPos.ZERO, Blocks.OAK_PLANKS.defaultBlockState(), BlockPos.ZERO.east(), Blocks.OAK_PLANKS.defaultBlockState(),
                BlockPos.ZERO.south(), Blocks.OAK_PLANKS.defaultBlockState(), BlockPos.ZERO.east().south(), Blocks.OAK_PLANKS.defaultBlockState());
    }
}
