package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.sell.CurrencyAmount;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("villagerretaliation")
@PrefixGameTestTemplate(false)
public final class SettlementPurchaseGameTests {
    private SettlementPurchaseGameTests() { }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_purchase")
    public static void purchaseApprovalsRoundTripAndRejectUnsafeLimits(GameTestHelper helper) {
        var approval = approval(UUID.randomUUID(), "minecraft:wheat", new BlockPos(-7, 64, 18));
        var plan = new SettlementPurchasePlan(true, CurrencyAmount.of(123, 2), List.of(approval));
        helper.assertValueEqual(SettlementPurchasePlan.load(plan.save()), plan,
                "the exact commodity, receiving depot, unit price and shared daily cap survive reload");
        helper.assertFalse(SettlementPurchasePlan.DISABLED.enabled(), "new policies have no spending permission");
        helper.assertTrue(SettlementPurchasePlan.DISABLED.approvals().isEmpty(), "new policies approve no commodities");
        invalid(helper, () -> new SettlementPurchasePlan(true, CurrencyAmount.ZERO, List.of(approval)));
        invalid(helper, () -> new SettlementPurchasePlan(true, CurrencyAmount.of(7, 1), List.of()));
        invalid(helper, () -> new SettlementPurchasePlan(false, CurrencyAmount.ZERO, List.of(approval, approval)));
        var duplicate = new SettlementPurchasePlan.Approval(UUID.randomUUID(), approval.consumer(), approval.requirement(),
                approval.item(), approval.destination(), CurrencyAmount.of(2, 1));
        invalid(helper, () -> new SettlementPurchasePlan(false, CurrencyAmount.ZERO, List.of(approval, duplicate)));
        var alternatives = new SettlementPurchasePlan(false, CurrencyAmount.ZERO, List.of(approval,
                approval(approval.requirement(), "minecraft:bread", approval.destination())));
        helper.assertValueEqual(alternatives.approvals().size(), 2, "several explicitly approved commodities may cover one requirement");
        var oversized = new ArrayList<SettlementPurchasePlan.Approval>();
        for (int index = 0; index <= SettlementPurchasePlan.MAX_APPROVALS; index++)
            oversized.add(approval(UUID.randomUUID(), "minecraft:wheat", new BlockPos(index, 64, 0)));
        invalid(helper, () -> new SettlementPurchasePlan(false, CurrencyAmount.ZERO, oversized));
        var malformed = plan.save();
        malformed.getCompound("DailyBudget").putString("Numerator", "invalid");
        invalid(helper, () -> SettlementPurchasePlan.load(malformed));
        var invalidPrice = plan.save();
        invalidPrice.getList("Approvals", Tag.TAG_COMPOUND).getCompound(0).getCompound("UnitPriceLimit").putString("Denominator", "0");
        invalid(helper, () -> SettlementPurchasePlan.load(invalidPrice));
        var invalidList = plan.save();
        invalidList.putString("Approvals", "untrusted");
        invalid(helper, () -> SettlementPurchasePlan.load(invalidList));
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_purchase")
    public static void olderPoliciesAndEconomiesDoNotGainPurchasePermission(GameTestHelper helper) {
        var provider = helper.getLevel().registryAccess();
        var policy = policy(SettlementPurchasePlan.DISABLED, () -> {}, () -> {});
        policy.state(SettlementSupplyPolicy.State.ACTIVE, policy.authority(), "", 10);
        var legacy = policy.save(provider);
        legacy.putInt("Version", 1);
        legacy.remove("PurchaseShortages");
        legacy.getCompound("Configuration").putInt("Version", 1);
        legacy.getCompound("Configuration").remove("Purchases");
        var restored = SettlementSupplyPolicy.load(legacy, provider, () -> {});
        helper.assertValueEqual(restored.configuration().purchases(), SettlementPurchasePlan.DISABLED,
                "legacy policies gain neither purchase approvals nor a spending allowance");
        helper.assertValueEqual(restored.state(), policy.state(), "existing local policy state is preserved");
        helper.assertValueEqual(restored.authority(), policy.authority(), "migration retains the existing authority");
        helper.assertTrue(restored.purchaseShortages().isEmpty(), "old saves cannot inherit an elapsed fallback timer");
        var economy = economy();
        var manual = manual(UUID.randomUUID());
        helper.assertTrue(economy.putOrder(manual), "the native manual order is retained");
        economy.recordPurchase(manual, ResourceLocation.parse("villagerretaliation:grain"), 1, CurrencyAmount.of(2, 1));
        var oldEconomy = economy.save();
        oldEconomy.remove("Version");
        oldEconomy.remove("PolicySpending");
        var restoredEconomy = SettlementEconomy.load(oldEconomy, () -> {});
        helper.assertValueEqual(restoredEconomy.balance(), economy.balance(), "the legacy treasury balance survives migration");
        helper.assertValueEqual(restoredEconomy.orderSpending(manual.id()), CurrencyAmount.of(2, 1),
                "legacy manual-order spending remains authoritative");
        helper.assertValueEqual(restoredEconomy.policySpending(manual.id()), CurrencyAmount.ZERO,
                "a manual order identity does not create a policy expense account");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_purchase")
    public static void purchaseTimersPersistWithoutChangingAllocationsAndResetOnPolicyChanges(GameTestHelper helper) {
        var changed = new AtomicInteger();
        var dirty = new AtomicInteger();
        var approval = approval(UUID.randomUUID(), "minecraft:wheat", new BlockPos(2, 64, 2));
        var policy = policy(new SettlementPurchasePlan(true, CurrencyAmount.of(7, 1), List.of(approval)),
                changed::incrementAndGet, dirty::incrementAndGet);
        policy.state(SettlementSupplyPolicy.State.ACTIVE, policy.authority(), "", 100);
        int allocationChanges = changed.get();
        long revision = policy.revision();
        var shortage = new SettlementSupplyPolicy.PurchaseShortage(UUID.randomUUID(), 400, 24);
        policy.purchaseShortage(approval.id(), shortage);
        helper.assertValueEqual(changed.get(), allocationChanges, "saving a timer does not invalidate resource allocations");
        helper.assertValueEqual(dirty.get(), 1, "new timer state is persisted");
        helper.assertValueEqual(policy.revision(), revision, "runtime shortage observations do not conflict with an open configuration editor");
        policy.purchaseShortage(approval.id(), shortage);
        helper.assertValueEqual(dirty.get(), 1, "unchanged observations do not dirty the save again");
        helper.assertFalse(shortage.eligible(1_599), "a known shortage cannot purchase before 1200 simulated ticks");
        helper.assertTrue(shortage.eligible(1_600), "the full native tick interval makes a shortage eligible");
        helper.assertFalse(shortage.eligible(399), "clock rollback cannot authorize an expired timer");
        var restored = SettlementSupplyPolicy.load(policy.save(helper.getLevel().registryAccess()),
                helper.getLevel().registryAccess(), () -> {}, () -> {});
        helper.assertValueEqual(restored.purchaseShortages().get(approval.id()), shortage, "elapsed shortage state survives reload");
        policy.state(SettlementSupplyPolicy.State.PAUSED, policy.authority(), "", 500);
        helper.assertTrue(policy.purchaseShortages().isEmpty(), "pausing clears fallback eligibility");
        invalid(helper, () -> policy.purchaseShortage(approval.id(), shortage));
        policy.state(SettlementSupplyPolicy.State.ACTIVE, policy.authority(), "", 600);
        policy.purchaseShortage(approval.id(), shortage);
        policy.configure(policy.configuration(), policy.authority(), 700);
        helper.assertTrue(policy.purchaseShortages().isEmpty(), "editing or rebinding a policy restarts the fallback interval");
        policy.purchaseShortage(approval.id(), shortage);
        policy.state(SettlementSupplyPolicy.State.ACTIVE, UUID.randomUUID(), "", 800);
        helper.assertTrue(policy.purchaseShortages().isEmpty(), "new authority cannot inherit an earlier spending timer");
        var bad = restored.save(helper.getLevel().registryAccess());
        bad.getList("PurchaseShortages", Tag.TAG_COMPOUND).getCompound(0).putUUID("Approval", UUID.randomUUID());
        invalid(helper, () -> SettlementSupplyPolicy.load(bad, helper.getLevel().registryAccess(), () -> {}));
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_purchase")
    public static void policySpendingSharesTreasuryAndMarketsWithoutSharingManualOrderIdentity(GameTestHelper helper) {
        var economy = economy();
        var id = UUID.randomUUID();
        var manual = manual(id);
        var cap = CurrencyAmount.of(7, 1);
        var grain = ResourceLocation.parse("villagerretaliation:grain");
        var timber = ResourceLocation.parse("villagerretaliation:timber");
        helper.assertTrue(economy.putOrder(manual), "a manual order may have the same UUID in its separate namespace");
        economy.recordPurchase(manual, grain, 1, CurrencyAmount.of(2, 1));
        economy.recordPolicyPurchase(id, cap, grain, 1, CurrencyAmount.of(3, 2));
        economy.recordPolicyPurchase(id, cap, timber, 1, CurrencyAmount.of(5, 2));
        helper.assertValueEqual(economy.balance(), CurrencyAmount.of(94, 1), "all purchases debit the same exact treasury balance");
        helper.assertValueEqual(economy.spent(), CurrencyAmount.of(6, 1), "town spending includes both manual and policy purchases");
        helper.assertValueEqual(economy.policySpending(id), CurrencyAmount.of(4, 1), "the policy cap covers every approved commodity together");
        helper.assertValueEqual(economy.orderSpending(id), CurrencyAmount.of(2, 1), "manual and policy expense identities remain separate");
        helper.assertValueEqual(economy.market(grain).bought(), 2, "both paths consume the same finite market accounting");
        helper.assertValueEqual(economy.market(timber).bought(), 1, "other commodities retain their own native market group");
        var restored = SettlementEconomy.load(economy.save(), () -> {});
        helper.assertValueEqual(restored.availablePolicyBudget(id, cap), CurrencyAmount.of(3, 1), "reload retains the remaining policy allowance");
        helper.assertValueEqual(restored.availablePolicyBudget(id, CurrencyAmount.of(3, 1)), CurrencyAmount.ZERO,
                "lowering a cap does not erase spending already recorded");
        helper.assertTrue(restored.removeOrder(id), "manual order removal still works independently");
        helper.assertValueEqual(restored.policySpending(id), CurrencyAmount.of(4, 1), "removing a colliding manual order cannot reset policy spending");
        helper.assertTrue(restored.configureBudget(CurrencyAmount.of(90, 1), CurrencyAmount.of(30, 1)), "treasury reserve changes remain native");
        helper.assertValueEqual(restored.availablePolicyBudget(UUID.randomUUID(), CurrencyAmount.of(100, 1)), CurrencyAmount.of(4, 1),
                "all policy accounts respect the protected treasury reserve");
        helper.assertTrue(restored.configureBudget(CurrencyAmount.of(10, 1), CurrencyAmount.of(30, 1)), "restore the configured reserve");
        helper.assertTrue(restored.beginTradeCycle(24_000), "the next native day resets the same accounting period");
        helper.assertValueEqual(restored.policySpending(id), CurrencyAmount.ZERO, "policy spending resets at the native daily boundary");
        helper.assertValueEqual(restored.spent(), CurrencyAmount.ZERO, "the town daily allowance resets at the same boundary");
        helper.assertValueEqual(restored.market(grain).bought(), 0, "market supply resets with native trade accounting");
        helper.assertValueEqual(restored.balance(), CurrencyAmount.of(94, 1), "a new day does not create treasury money");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_purchase")
    public static void failedAndMalformedPolicySpendingCannotResetDailyLimits(GameTestHelper helper) {
        var economy = economy();
        var id = UUID.randomUUID();
        var cap = CurrencyAmount.of(4, 1);
        var grain = ResourceLocation.parse("villagerretaliation:grain");
        economy.recordPolicyPurchase(id, cap, grain, 2, cap);
        var before = economy.save();
        blocked(helper, () -> economy.recordPolicyPurchase(id, cap, grain, 1, CurrencyAmount.of(1, 1)));
        helper.assertValueEqual(economy.save(), before, "a rejected debit leaves balance, market volume and spending unchanged");
        var malformed = before.copy();
        malformed.getList("PolicySpending", Tag.TAG_COMPOUND).getCompound(0).getCompound("Spent").putString("Numerator", "invalid");
        invalid(helper, () -> SettlementEconomy.load(malformed, () -> {}));
        var duplicate = before.copy();
        var accounts = duplicate.getList("PolicySpending", Tag.TAG_COMPOUND);
        accounts.add(accounts.getCompound(0).copy());
        invalid(helper, () -> SettlementEconomy.load(duplicate, () -> {}));
        var future = before.copy();
        future.putInt("Version", 2);
        invalid(helper, () -> SettlementEconomy.load(future, () -> {}));
        var missing = before.copy();
        missing.remove("Version");
        invalid(helper, () -> SettlementEconomy.load(missing, () -> {}));
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_purchase")
    public static void policySpendingAccountsStayBoundedUntilTheNativeDailyReset(GameTestHelper helper) {
        var economy = new SettlementEconomy(() -> {});
        economy.credit(CurrencyAmount.of(5_000, 1));
        helper.assertTrue(economy.configureBudget(CurrencyAmount.ZERO, CurrencyAmount.of(5_000, 1)), "the fixture funds bounded ledger accounts");
        economy.beginTradeCycle(0);
        var grain = ResourceLocation.parse("villagerretaliation:grain");
        var cap = CurrencyAmount.of(2, 1);
        for (int index = 0; index < 1_024; index++)
            economy.recordPolicyPurchase(new UUID(0, index), cap, grain, 1, CurrencyAmount.of(1, 1));
        helper.assertValueEqual(economy.availablePolicyBudget(new UUID(1, 0), cap), CurrencyAmount.ZERO,
                "retired policy identities cannot grow the daily ledger without a bound");
        helper.assertValueEqual(economy.availablePolicyBudget(new UUID(0, 0), cap), CurrencyAmount.of(1, 1),
                "existing accounts retain their valid remaining allowance");
        var saved = economy.save();
        var restored = SettlementEconomy.load(saved, () -> {});
        helper.assertValueEqual(restored.policySpending(new UUID(0, 1_023)), CurrencyAmount.of(1, 1), "the full bounded ledger survives reload");
        var overflow = saved.copy();
        var extra = overflow.getList("PolicySpending", Tag.TAG_COMPOUND).getCompound(0).copy();
        extra.putUUID("Policy", new UUID(1, 0));
        overflow.getList("PolicySpending", Tag.TAG_COMPOUND).add(extra);
        invalid(helper, () -> SettlementEconomy.load(overflow, () -> {}));
        restored.beginTradeCycle(24_000);
        helper.assertValueEqual(restored.availablePolicyBudget(new UUID(1, 0), cap), cap, "new account capacity returns at the daily reset");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_purchase")
    public static void nativeShortagesWaitForPersistenceAndResetAfterStockOrRealIncomingProgress(GameTestHelper helper) {
        var level = helper.getLevel();
        var town = purchaseTown(helper);
        var depot = town.depots().values().iterator().next();
        var policy = purchasePolicy(helper, town, new ItemStack(Items.BREAD), 8, 5);
        var approval = policy.configuration().purchases().approvals().getFirst();
        helper.assertValueEqual(observe(helper, town, policy, approval, 100).missing(), 8L, "the timer starts from the actual allocated deficit");
        observe(helper, town, policy, approval, 1_299);
        helper.assertFalse(policy.purchaseShortages().get(approval.id()).eligible(1_299), "1,199 simulated ticks cannot authorize fallback");
        observe(helper, town, policy, approval, 1_300);
        helper.assertTrue(policy.purchaseShortages().get(approval.id()).eligible(1_300), "an unchanged known deficit becomes eligible at 1,200 ticks");
        depot.setItem(0, new ItemStack(Items.BREAD, 3));
        helper.assertValueEqual(observe(helper, town, policy, approval, 1_400).missing(), 5L, "usable local output reduces the shortage");
        helper.assertValueEqual(policy.purchaseShortages().get(approval.id()).since(), 1_400L, "real stock progress restarts the delay");
        depot.removeItem(0, 1);
        observe(helper, town, policy, approval, 1_500);
        helper.assertValueEqual(policy.purchaseShortages().get(approval.id()).since(), 1_400L, "consumption does not continually postpone a persistent shortage");
        var kitchen = town.areas().values().stream().filter(area -> area.type() == SettlementAreaType.KITCHEN).findFirst().orElseThrow();
        var queue = town.beginProduction(town.leader(), kitchen.id());
        var order = com.jvn.villagerretaliation.interaction.work.production.HiredProductionOrder.create(level,
                com.jvn.villagerretaliation.interaction.work.production.HiredProductionRecipe.exact(ResourceLocation.withDefaultNamespace("bread"), java.util.Map.of()),
                com.jvn.villagerretaliation.interaction.work.production.HiredProductionOrder.Mode.BATCH, 2, depot.position());
        queue.append(order);
        helper.assertValueEqual(observe(helper, town, policy, approval, 1_600).missing(), 6L,
                "an unreserved manual order is proposed work rather than incoming stock");
        helper.assertValueEqual(policy.purchaseShortages().get(approval.id()).since(), 1_400L, "proposed work cannot restart the shortage timer");
        var worker = UUID.randomUUID();
        town.assignDuty(worker, kitchen.id());
        var receipt = queue.reserve(worker, order.id(), 1, 2, 0, null);
        helper.assertTrue(receipt != null, "the fixture creates a real native production commitment");
        var incoming = observe(helper, town, policy, approval, 1_700);
        helper.assertValueEqual(incoming.incoming(), 2L, "reserved native output is credited once");
        helper.assertValueEqual(incoming.missing(), 4L, "purchases exclude incoming output");
        helper.assertValueEqual(policy.purchaseShortages().get(approval.id()).since(), 1_700L, "real incoming progress restarts the delay");
        var restored = SettlementRecord.load(town.save(level.registryAccess()), level.registryAccess(), () -> {});
        var restoredPolicy = restored.supplyPolicies().get(policy.id());
        observe(helper, restored, restoredPolicy, approval, 2_900);
        helper.assertTrue(restoredPolicy.purchaseShortages().get(approval.id()).eligible(2_900), "reloading preserves an unchanged persistent shortage");
        observe(helper, restored, restoredPolicy, approval, 1_000);
        helper.assertValueEqual(restoredPolicy.purchaseShortages().get(approval.id()).since(), 1_000L, "clock rollback cannot inherit future eligibility");
        restored.depots().get(depot.id()).setItem(0, new ItemStack(Items.BREAD, 8));
        helper.assertTrue(observe(helper, restored, restoredPolicy, approval, 1_100) == null, "a satisfied requirement has no purchase demand");
        helper.assertTrue(restoredPolicy.purchaseShortages().isEmpty(), "satisfied demand clears saved purchase eligibility");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_purchase")
    public static void purchaseValidationRejectsWrongRequirementsComponentsAndUnlinkedDepots(GameTestHelper helper) {
        var level = helper.getLevel();
        var town = purchaseTown(helper);
        var policy = purchasePolicy(helper, town, new ItemStack(Items.BREAD), 8, 5);
        var approval = policy.configuration().purchases().approvals().getFirst();
        var snapshot = SettlementDemandService.inspect(level, town);
        var input = SettlementPolicyPlanner.targets(level, town, policy, snapshot).getFirst();
        helper.assertValueEqual(SettlementPurchaseService.validate(level, town, approval, input), "", "an approved compatible commodity uses its linked supply depot");
        var wrongRequirement = new SettlementPurchasePlan.Approval(UUID.randomUUID(), null, UUID.randomUUID(), approval.item(),
                approval.destination(), approval.unitPriceLimit());
        helper.assertValueEqual(SettlementPurchaseService.validate(level, town, wrongRequirement, input), "policy_purchase_requirement",
                "an approval cannot point at a different native requirement");
        var wrongItem = new SettlementPurchasePlan.Approval(UUID.randomUUID(), null, approval.requirement(), ResourceLocation.withDefaultNamespace("wheat"),
                approval.destination(), approval.unitPriceLimit());
        helper.assertValueEqual(SettlementPurchaseService.validate(level, town, wrongItem, input), "policy_purchase_commodity",
                "market eligibility alone cannot satisfy an incompatible requirement");
        var wrongDepot = new SettlementPurchasePlan.Approval(UUID.randomUUID(), null, approval.requirement(), approval.item(),
                approval.destination().east(40), approval.unitPriceLimit());
        helper.assertValueEqual(SettlementPurchaseService.validate(level, town, wrongDepot, input), "policy_purchase_depot",
                "purchases cannot teleport into an arbitrary destination");
        var named = new ItemStack(Items.BREAD);
        named.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Reserved loaf"));
        var componentPolicy = purchasePolicy(helper, town, named, 8, 5);
        var componentApproval = componentPolicy.configuration().purchases().approvals().getFirst();
        var componentInput = SettlementPolicyPlanner.targets(level, town, componentPolicy, SettlementDemandService.inspect(level, town)).getFirst();
        helper.assertValueEqual(SettlementPurchaseService.validate(level, town, componentApproval, componentInput), "policy_purchase_commodity",
                "plain market goods cannot satisfy component-sensitive demand");
        helper.assertValueEqual(plan(helper, town, policy).blocker(), "", "a fully approved purchase target needs no artificial production step");
        helper.assertValueEqual(plan(helper, town, componentPolicy).blocker(), "policy_purchase_commodity",
                "the authoritative preview rejects invalid approvals before activation");
        var original = policy.configuration();
        var invalidPlan = new SettlementSupplyPolicy.Configuration(original.name(), original.target(), original.priority(),
                original.steps(), original.connections(), new SettlementPurchasePlan(true, CurrencyAmount.of(20, 1), List.of(wrongRequirement)));
        policy.configure(invalidPlan, town.leader(), level.getGameTime());
        helper.assertValueEqual(plan(helper, town, policy).blocker(), "policy_purchase_requirement", "structurally valid purchase drafts still validate native requirement identities");
        var disabled = new SettlementSupplyPolicy.Configuration(original.name(), original.target(), original.priority(), List.of(), List.of());
        helper.assertValueEqual(SettlementPolicyGraph.inspect(disabled).blocker(), "policy_chain_empty", "disabled purchasing cannot authorize an empty chain");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_purchase")
    public static void overlappingPurchaseTargetsUseTheSharedAllocationDeficit(GameTestHelper helper) {
        var town = purchaseTown(helper);
        var first = purchasePolicy(helper, town, new ItemStack(Items.BREAD), 8, 9);
        var second = purchasePolicy(helper, town, new ItemStack(Items.BREAD), 12, 1);
        var firstApproval = first.configuration().purchases().approvals().getFirst();
        var secondApproval = second.configuration().purchases().approvals().getFirst();
        helper.assertValueEqual(observe(helper, town, first, firstApproval, 100).missing(), 8L, "the higher priority stock floor receives its full initial deficit");
        helper.assertValueEqual(observe(helper, town, second, secondApproval, 100).missing(), 4L, "overlapping floors request only the independent remainder");
        var depot = town.depots().values().iterator().next();
        depot.setItem(0, new ItemStack(Items.BREAD, 8));
        helper.assertTrue(observe(helper, town, first, firstApproval, 1_300) == null, "arriving stock satisfies the first target");
        helper.assertValueEqual(observe(helper, town, second, secondApproval, 1_300).missing(), 4L, "the remaining purchase deficit stays four instead of buying another target");
        helper.assertTrue(second.purchaseShortages().get(secondApproval.id()).eligible(1_300), "an unchanged independent deficit retains its own waiting time");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_purchase")
    public static void unknownLinkedStorageClearsPurchaseEligibilityUntilItIsObservedAgain(GameTestHelper helper) {
        var level = helper.getLevel();
        var town = purchaseTown(helper);
        var depot = town.depots().values().iterator().next();
        var kitchen = town.areas().values().stream().filter(area -> area.type() == SettlementAreaType.KITCHEN).findFirst().orElseThrow();
        var queue = town.beginProduction(town.leader(), kitchen.id());
        var order = com.jvn.villagerretaliation.interaction.work.production.HiredProductionOrder.create(level,
                com.jvn.villagerretaliation.interaction.work.production.HiredProductionRecipe.exact(ResourceLocation.withDefaultNamespace("bread"), java.util.Map.of()),
                com.jvn.villagerretaliation.interaction.work.production.HiredProductionOrder.Mode.BATCH, 8, depot.position());
        queue.append(order);
        var policy = consumerPurchases(helper, town, order.id(), Items.WHEAT);
        var approval = policy.configuration().purchases().approvals().getFirst();
        helper.assertTrue(observe(helper, town, policy, approval, 100) != null, "known production inputs begin a shortage timer");
        observe(helper, town, policy, approval, 1_300);
        helper.assertTrue(policy.purchaseShortages().get(approval.id()).eligible(1_300), "the original shortage becomes eligible");
        var original = town.worksitePlans().get(kitchen.id());
        var links = new SettlementStorageLinks(false, List.of(
                new SettlementStorageLinks.Link(depot.position(), SettlementStorageLinks.Purpose.GENERAL, 5),
                new SettlementStorageLinks.Link(town.center().east(60), SettlementStorageLinks.Purpose.SUPPLY, 5)));
        town.setWorksitePlan(town.leader(), kitchen.id(),
                new SettlementWorksitePlan(original.settings().withStorage(links), original.desiredStaff(), original.links()));
        helper.assertTrue(observe(helper, town, policy, approval, 1_500) == null, "unobserved linked storage cannot cause a purchase");
        helper.assertTrue(policy.purchaseShortages().isEmpty(), "unknown input stock clears accumulated eligibility");
        town.setWorksitePlan(town.leader(), kitchen.id(), original);
        helper.assertTrue(observe(helper, town, policy, approval, 1_700) != null, "known linked storage restores an observable shortage");
        helper.assertValueEqual(policy.purchaseShortages().get(approval.id()).since(), 1_700L, "recovery requires a fresh persistent shortage");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_purchase")
    public static void unknownStartedStationPreventsPurchasingForTheSameConsumersKnownInputs(GameTestHelper helper) {
        var level = helper.getLevel();
        var town = purchaseTown(helper);
        var depot = town.depots().values().iterator().next();
        var position = town.center().south(8);
        SettlementTestPlots.claim(level, town, position, position.offset(3, 2, 3));
        var area = new SettlementArea(UUID.randomUUID(), SettlementAreaType.SMELTERY, "Smelter", position, position.offset(3, 2, 3), 5, true);
        helper.assertTrue(town.putArea(town.leader(), area), "the native smelting worksite is owned");
        town.setWorksitePlan(town.leader(), area.id(), SettlementWorksitePlan.defaults(area.type().role()));
        var queue = town.beginProduction(town.leader(), area.id());
        var order = com.jvn.villagerretaliation.interaction.work.production.HiredProductionOrder.create(level,
                com.jvn.villagerretaliation.interaction.work.production.HiredProductionRecipe.exact(
                        ResourceLocation.withDefaultNamespace("iron_ingot_from_smelting_raw_iron"), java.util.Map.of()),
                com.jvn.villagerretaliation.interaction.work.production.HiredProductionOrder.Mode.BATCH, 8, depot.position());
        queue.append(order);
        var policy = consumerPurchases(helper, town, order.id(), Items.COAL);
        var approval = policy.configuration().purchases().approvals().getFirst();
        helper.assertTrue(observe(helper, town, policy, approval, 100) != null, "the known fuel shortage is eligible for an approved market commodity");
        var worker = UUID.randomUUID();
        town.assignDuty(worker, area.id());
        var receipt = queue.reserve(worker, order.id(), 1, 1, 0, position);
        helper.assertTrue(receipt != null && queue.start(receipt.id()), "a started native receipt keeps the unavailable station's identity");
        var snapshot = SettlementDemandService.inspect(level, town);
        helper.assertTrue(snapshot.demands().stream().anyMatch(demand -> demand.consumer().equals(order.id()) && !demand.supply().known()),
                "missing machine contents remain explicitly unknown");
        helper.assertTrue(snapshot.demands().stream().anyMatch(demand -> demand.consumer().equals(order.id()) && demand.supply().known()
                && demand.requirement().value(new ItemStack(Items.COAL)) > 0 && demand.missing() > 0),
                "the same consumer still has known requirements for its unstarted work");
        helper.assertTrue(SettlementPurchaseService.observe(level, town, policy, approval, snapshot, 1_300) == null,
                "known inputs cannot bypass unknown contents held by the consumer's started receipt");
        helper.assertTrue(policy.purchaseShortages().isEmpty(), "unknown station contents clear the purchase timer");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_purchase")
    public static void purchasesFeedOnlyTheApprovedDepotAndNativeBackgroundDelivery(GameTestHelper helper) {
        SettlementBackgroundHaulingGameTests.fixture(helper, fixture -> {
            var level = helper.getLevel();
            var town = fixture.town();
            town.advance();
            town.advance();
            town.configureDepot(town.leader(), fixture.source().id(), SettlementDepotPurpose.GENERAL, 5);
            town.configureDepot(town.leader(), fixture.destination().id(), SettlementDepotPurpose.OUTPUT, 5);
            town.setWorksitePlan(town.leader(), fixture.area().id(), SettlementWorksitePlan.defaults(
                    com.jvn.villagerretaliation.interaction.HiredVillagerRole.COURIER));
            var position = town.center().south(6);
            var market = new SettlementArea(UUID.randomUUID(), SettlementAreaType.TRADING_POST, "Market", position, position.offset(3, 2, 3), 5, true);
            SettlementTestPlots.claim(level, town, market.min(), market.max());
            helper.assertTrue(town.putArea(town.leader(), market), "the background trading post is owned");
            town.recordSurvey(market.id(), new SettlementBuildingSurvey.Survey(level.getGameTime(), List.of(), List.of(position), false,
                    java.util.Map.of(position, ResourceLocation.withDefaultNamespace("lectern"))));
            var clerk = net.minecraft.world.entity.EntityType.VILLAGER.create(level);
            clerk.setPos(position.getCenter());
            clerk.setVillagerData(clerk.getVillagerData().setProfession(net.minecraft.world.entity.npc.VillagerProfession.NITWIT));
            com.jvn.villagerretaliation.allegiance.VillageAllegianceApi.assignKnown(level, clerk, town.id(),
                    com.jvn.villagerretaliation.allegiance.AllegianceAssignmentSource.EXPLICIT_API);
            town.assignDuty(clerk.getUUID(), market.id());
            town.recordResident(SettlementResidentState.capture(clerk, town, level.getGameTime()));
            town.economy().credit(CurrencyAmount.of(1_000, 1));
            town.economy().configureBudget(CurrencyAmount.ZERO, CurrencyAmount.of(1_000, 1));
            var id = UUID.randomUUID();
            var sample = new ItemStack(Items.IRON_INGOT);
            var step = new SettlementSupplyPolicy.Step(UUID.randomUUID(), SettlementSupplyPolicy.StepKind.DELIVERY, fixture.area().id(),
                    null, sample, fixture.source().position(), fixture.destination().position());
            var target = new SettlementSupplyPolicy.Target(SettlementSupplyPolicy.TargetKind.STOCK, null, null, fixture.destination().position(), sample, 7);
            var targetRequirement = com.jvn.villagerretaliation.interaction.work.production.WorkSupplyRequirement.exact("policy_stock", sample, 7);
            var targetId = com.jvn.villagerretaliation.interaction.work.production.WorkSupplyDemand.identity(level.registryAccess(), id,
                    com.jvn.villagerretaliation.interaction.work.production.WorkSupplyDemand.Kind.STOCK_RESERVE, targetRequirement);
            var input = SettlementPolicyPlanner.inputs(level, town, step, 7).getFirst();
            var approval = new SettlementPurchasePlan.Approval(UUID.randomUUID(), step.id(), input.id(), ResourceLocation.withDefaultNamespace("iron_ingot"),
                    fixture.source().position(), CurrencyAmount.of(100, 1));
            var configuration = new SettlementSupplyPolicy.Configuration("Imported construction supplies", target, 7, List.of(step),
                    List.of(new SettlementSupplyPolicy.Connection(step.id(), null, targetId)),
                    new SettlementPurchasePlan(true, CurrencyAmount.of(100, 1), List.of(approval)));
            var policy = new SettlementSupplyPolicy(id, configuration, town.leader(), town::supplyChanged, town::changed);
            town.addSupplyPolicy(policy);
            policy.state(SettlementSupplyPolicy.State.ACTIVE, town.leader(), "", level.getGameTime());
            finish(SettlementPolicyService.cycle(level, town));
            helper.assertValueEqual(policy.blocker(), "", "the approved purchase and courier chain validates");
            var order = policy.bindings().getFirst().order();
            var queue = town.transferQueues().get(fixture.area().id());
            SettlementTradeService.process(level, town, 200);
            helper.assertTrue(fixture.source().isEmpty() && fixture.destination().isEmpty(), "neither endpoint receives goods before the timeout");
            helper.assertTrue(policy.purchaseShortages().containsKey(approval.id()), "the native courier shortage starts the timer");
            for (long tick = 1_400; tick <= 3_000 && fixture.source().count(stack -> stack.is(Items.IRON_INGOT)) < 7; tick += 200)
                SettlementTradeService.process(level, town, tick);
            helper.assertValueEqual(fixture.source().count(stack -> stack.is(Items.IRON_INGOT)), 7, "purchases arrive only at the approved receiving depot");
            helper.assertTrue(fixture.destination().isEmpty(), "purchasing cannot bypass the approved courier");
            var price = com.jvn.villagerretaliation.sell.VillageSellMarket.quote(level, town.center(), sample).orElseThrow().baseUnitPrice().multiply(2);
            helper.assertValueEqual(town.economy().policySpending(id), price.multiply(7), "the native market charges exactly the bought quantity");
            SettlementTestPlots.planSupplies(level, town);
            for (int cycle = 0; cycle < 4; cycle++) SettlementBackgroundHauling.tick(level, town, fixture.worker(), 200, 1.0D);
            helper.assertTrue(fixture.source().isEmpty(), "native hauling withdraws the bought goods once");
            helper.assertValueEqual(fixture.destination().count(stack -> stack.is(Items.IRON_INGOT)), 7, "native hauling delivers the approved quantity");
            helper.assertValueEqual(queue.order(order).delivered(), 7L, "the existing courier receipt records delivery");
            var restored = SettlementRecord.load(town.save(level.registryAccess()), level.registryAccess(), () -> {});
            SettlementTradeService.process(level, restored, 3_200);
            helper.assertValueEqual(restored.economy().policySpending(id), price.multiply(7), "reload cannot buy another copy of the completed delivery");
            helper.assertValueEqual(restored.depots().get(fixture.destination().id()).count(stack -> stack.is(Items.IRON_INGOT)), 7,
                    "the receiving inventory survives reload without replaying cargo");
            helper.assertFalse(level.hasChunkAt(town.center()), "purchasing and background delivery do not load settlement chunks");
        });
    }

    private static void finish(Runnable work) {
        int attempts = 0;
        do { work.run(); } while (!SettlementWork.complete(work) && ++attempts < 4_096);
        if (!SettlementWork.complete(work)) throw new AssertionError("Purchase work did not finish within its bounded job count");
    }

    private static SettlementSupplyPolicy consumerPurchases(GameTestHelper helper, SettlementRecord town, UUID consumer, net.minecraft.world.item.Item item) {
        var demand = SettlementDemandService.inspect(helper.getLevel(), town).demands().stream()
                .filter(row -> row.consumer().equals(consumer) && row.requirement().value(new ItemStack(item)) > 0).findFirst().orElseThrow();
        var depot = town.depots().values().iterator().next();
        var approval = new SettlementPurchasePlan.Approval(UUID.randomUUID(), null, demand.supply().id(),
                net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item), depot.position(), CurrencyAmount.of(100, 1));
        var target = new SettlementSupplyPolicy.Target(SettlementSupplyPolicy.TargetKind.CONSUMER, consumer, demand.supply().id(), null, ItemStack.EMPTY, 0);
        var policy = new SettlementSupplyPolicy(UUID.randomUUID(), new SettlementSupplyPolicy.Configuration("Purchased input", target, 5,
                List.of(), List.of(), new SettlementPurchasePlan(true, CurrencyAmount.of(100, 1), List.of(approval))),
                town.leader(), town::supplyChanged, town::changed);
        town.addSupplyPolicy(policy);
        policy.state(SettlementSupplyPolicy.State.ACTIVE, town.leader(), "", helper.getLevel().getGameTime());
        return policy;
    }

    private static SettlementRecord purchaseTown(GameTestHelper helper) {
        var level = helper.getLevel();
        var origin = helper.absolutePos(BlockPos.ZERO);
        var center = new BlockPos(23_000_000 + origin.getX() * 8, 64, 23_000_000 + origin.getZ() * 8);
        var registry = com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData.get(level);
        var id = registry.create(level.getGameTime(), level.dimension().location(), center, "Purchase test");
        var town = SettlementSavedData.get(level).establish(registry, id, UUID.randomUUID(), center, level.getGameTime()).orElseThrow();
        SettlementTestPlots.claim(level, town, center, center.offset(12, 3, 6));
        var stores = new SettlementArea(UUID.randomUUID(), SettlementAreaType.DEPOT, "Stores", center, center.offset(3, 2, 3), 5, true);
        town.putArea(town.leader(), stores);
        town.registerDepot(town.leader(), stores.id(), center, SettlementDepotPurpose.GENERAL).observeRules(List.of());
        town.setWorksitePlan(town.leader(), stores.id(), SettlementWorksitePlan.defaults(stores.type().role()));
        var kitchen = new SettlementArea(UUID.randomUUID(), SettlementAreaType.KITCHEN, "Bakery", center.east(7), center.offset(10, 2, 3), 5, true);
        town.putArea(town.leader(), kitchen);
        town.setWorksitePlan(town.leader(), kitchen.id(), SettlementWorksitePlan.defaults(kitchen.type().role()));
        return town;
    }

    private static SettlementSupplyPolicy purchasePolicy(GameTestHelper helper, SettlementRecord town, ItemStack sample, int amount, int priority) {
        var id = UUID.randomUUID();
        var depot = town.depots().values().iterator().next();
        var requirement = com.jvn.villagerretaliation.interaction.work.production.WorkSupplyRequirement.exact("policy_stock", sample, amount);
        var requirementId = com.jvn.villagerretaliation.interaction.work.production.WorkSupplyDemand.identity(helper.getLevel().registryAccess(), id,
                com.jvn.villagerretaliation.interaction.work.production.WorkSupplyDemand.Kind.STOCK_RESERVE, requirement);
        var approval = new SettlementPurchasePlan.Approval(UUID.randomUUID(), null, requirementId,
                net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(sample.getItem()), depot.position(), CurrencyAmount.of(2, 1));
        var target = new SettlementSupplyPolicy.Target(SettlementSupplyPolicy.TargetKind.STOCK, null, null, depot.position(), sample, amount);
        var policy = new SettlementSupplyPolicy(id, new SettlementSupplyPolicy.Configuration("Purchased reserve", target, priority,
                List.of(), List.of(), new SettlementPurchasePlan(true, CurrencyAmount.of(20, 1), List.of(approval))), town.leader(), town::supplyChanged, town::changed);
        town.addSupplyPolicy(policy);
        policy.state(SettlementSupplyPolicy.State.ACTIVE, town.leader(), "", helper.getLevel().getGameTime());
        return policy;
    }

    private static com.jvn.villagerretaliation.interaction.work.production.WorkSupplyDemand observe(GameTestHelper helper, SettlementRecord town,
            SettlementSupplyPolicy policy, SettlementPurchasePlan.Approval approval, long now) {
        return SettlementPurchaseService.observe(helper.getLevel(), town, policy, approval, SettlementDemandService.inspect(helper.getLevel(), town), now);
    }

    private static SettlementPolicyPlanner.Plan plan(GameTestHelper helper, SettlementRecord town, SettlementSupplyPolicy policy) {
        var results = new ArrayList<SettlementPolicyPlanner.Plan>();
        var work = SettlementPolicyPlanner.plan(helper.getLevel(), town, policy, SettlementDemandService.inspect(helper.getLevel(), town), new java.util.HashSet<>(), results::add);
        int attempts = 0;
        do { work.run(); } while (!SettlementWork.complete(work) && ++attempts < 4_096);
        if (results.size() != 1) throw new AssertionError("Purchase preview did not produce one bounded result");
        return results.getFirst();
    }

    private static SettlementPurchasePlan.Approval approval(UUID requirement, String item, BlockPos destination) {
        return new SettlementPurchasePlan.Approval(UUID.randomUUID(), null, requirement, ResourceLocation.parse(item),
                destination, CurrencyAmount.of(3, 2));
    }

    private static SettlementSupplyPolicy policy(SettlementPurchasePlan purchases, Runnable changed, Runnable dirty) {
        var target = new SettlementSupplyPolicy.Target(SettlementSupplyPolicy.TargetKind.STOCK, null, null,
                new BlockPos(4, 64, 4), new ItemStack(Items.BREAD), 8);
        return new SettlementSupplyPolicy(UUID.randomUUID(), new SettlementSupplyPolicy.Configuration(
                "Approved bread supplies", target, 5, List.of(), List.of(), purchases), UUID.randomUUID(), changed, dirty);
    }

    private static SettlementEconomy economy() {
        var economy = new SettlementEconomy(() -> {});
        economy.credit(CurrencyAmount.of(100, 1));
        economy.configureBudget(CurrencyAmount.of(10, 1), CurrencyAmount.of(30, 1));
        economy.beginTradeCycle(0);
        return economy;
    }

    private static SettlementTradeOrder manual(UUID id) {
        return new SettlementTradeOrder(id, SettlementTradeOrder.Direction.BUY, ResourceLocation.parse("minecraft:wheat"), 10,
                CurrencyAmount.of(2, 1), CurrencyAmount.of(25, 1), true);
    }

    private static void invalid(GameTestHelper helper, Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        helper.fail("malformed purchase state was accepted");
    }

    private static void blocked(GameTestHelper helper, Runnable action) {
        try {
            action.run();
        } catch (IllegalStateException expected) {
            return;
        }
        helper.fail("a purchase exceeding its allowance was accepted");
    }
}
