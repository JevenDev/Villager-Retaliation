package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.HiredMiningMode;
import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.HiredWorkStateStore;
import com.jvn.villagerretaliation.interaction.work.HiredRangedAmmo;
import com.jvn.villagerretaliation.interaction.work.HiredEquipmentNeed;
import com.jvn.villagerretaliation.interaction.work.production.WorkSupplyDemand;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("villagerretaliation")
@PrefixGameTestTemplate(false)
public final class SettlementEquipmentGameTests {
    private SettlementEquipmentGameTests() { }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_equipment")
    public static void taskRequirementsPreserveNativeToolTiersAcrossReload(GameTestHelper helper) {
        var requirement = HiredEquipmentNeed.mining(HiredMiningMode.EXPOSED_ORES, Blocks.DIAMOND_ORE.defaultBlockState());
        helper.assertTrue(!requirement.test(new ItemStack(Items.WOODEN_PICKAXE))
                        && requirement.test(new ItemStack(Items.IRON_PICKAXE)),
                "mining demand uses the native drop tier instead of accepting any pickaxe");
        var equivalent = HiredEquipmentNeed.mining(HiredMiningMode.EXPOSED_ORES, Blocks.REDSTONE_ORE.defaultBlockState());
        var consumer = UUID.randomUUID();
        helper.assertValueEqual(WorkSupplyDemand.identity(helper.getLevel().registryAccess(), consumer,
                        WorkSupplyDemand.Kind.EQUIPMENT, requirement.requirement(1)),
                WorkSupplyDemand.identity(helper.getLevel().registryAccess(), consumer,
                        WorkSupplyDemand.Kind.EQUIPMENT, equivalent.requirement(1)),
                "moving to another block with the same compatible tools preserves the demand identity");
        var state = new CompoundTag();
        requirement.remember(state);
        var restored = HiredEquipmentNeed.required(HiredVillagerRole.MINING, state.copy()).getFirst();
        helper.assertValueEqual(restored, requirement, "the next task retains its exact tool requirement after reload");
        helper.assertTrue(HiredEquipmentNeed.required(HiredVillagerRole.BUILDER, state).isEmpty(),
                "an old mining task cannot add equipment demand to a new builder assignment");
        helper.assertTrue(HiredEquipmentNeed.required(HiredVillagerRole.COOK, state).isEmpty(),
                "jobs without a native tool requirement do not request tools");

        var worn = new ItemStack(Items.IRON_PICKAXE);
        worn.setDamageValue(worn.getMaxDamage() - 1);
        helper.assertTrue(restored.test(worn), "low durability does not make an otherwise compatible tool unsuitable");
        worn.setDamageValue(worn.getMaxDamage());
        helper.assertTrue(!restored.test(worn), "exhausted saved equipment cannot cover the requirement");
        var excavation = HiredEquipmentNeed.mining(HiredMiningMode.EXCAVATE_AREA, Blocks.DIRT.defaultBlockState());
        helper.assertTrue(excavation.test(new ItemStack(Items.IRON_SHOVEL))
                        && !excavation.test(new ItemStack(Items.IRON_PICKAXE)),
                "excavation selects the tool for its actual target material");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_equipment")
    public static void essentialToolsPrecedeOptionalStockAndOnlyTheirWorkerCanAcquireThem(GameTestHelper helper) {
        var fixture = fixture(helper, SettlementAreaType.FARM);
        var tool = new ItemStack(Items.IRON_HOE);
        fixture.depot().setItem(0, tool.copy());
        fixture.depot().setStockTarget(tool, 1);
        fixture.depot().configure(SettlementDepotPurpose.GENERAL, 9);
        var row = equipment(helper, fixture);
        helper.assertValueEqual(row.supply().protectedMinimum(), 1L, "a missing required tool is essential");
        helper.assertValueEqual(row.supply().availableNow(), 1L, "the worker receives the tool ahead of an optional reserve");
        helper.assertValueEqual(row.supply().committed(), 0L, "a tool in storage does not already belong to the worker");
        var decision = ready(helper, fixture, tool);
        helper.assertValueEqual(decision.allowance(), 1, "protection permits acquisition by the intended worker");
        var outsider = SettlementAllocationService.inspect(helper.getLevel(), fixture.town(), UUID.randomUUID(), null,
                fixture.depot().position(), tool);
        helper.assertValueEqual(outsider.allowance(), 0, "another worker cannot appropriate the essential tool");
        helper.assertValueEqual(outsider.competingConsumer(), fixture.worker().id(), "the blocker identifies the worker needing equipment");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_equipment")
    public static void equipmentRefreshSeesBreakageAndPreservesOneOwnerAfterBackgroundReplacement(GameTestHelper helper) {
        var fixture = fixture(helper, SettlementAreaType.FARM);
        var level = helper.getLevel();
        var owner = fixture.owner();
        var inventory = HiredJobInventory.getJobInventory(owner);
        inventory.setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.IRON_HOE));
        fixture.worker().inventorySnapshot(inventory.captureSnapshot());
        var snapshot = SettlementDemandService.inspect(level, fixture.town());
        helper.assertValueEqual(snapshot.demands().stream().filter(demand -> demand.kind() == WorkSupplyDemand.Kind.EQUIPMENT)
                        .findFirst().orElseThrow().supply().committed(), 1L,
                "the equipped tool has one inventory owner");

        inventory.setItem(HiredJobInventory.MAINHAND_SLOT, ItemStack.EMPTY);
        fixture.worker().inventorySnapshot(inventory.captureSnapshot());
        fixture.depot().setItem(0, new ItemStack(Items.DIAMOND_HOE));
        var refreshed = snapshot.refresh().demands().stream().filter(demand -> demand.kind() == WorkSupplyDemand.Kind.EQUIPMENT)
                .findFirst().orElseThrow();
        helper.assertValueEqual(refreshed.supply().committed(), 0L, "an old planning snapshot cannot keep crediting broken equipment");
        helper.assertValueEqual(refreshed.supply().availableNow(), 1L, "the replacement becomes available to the same consumer");
        ready(helper, fixture, new ItemStack(Items.DIAMOND_HOE));
        helper.assertTrue(SettlementBackgroundTools.fetchTool(level, fixture.town(), fixture.worker(), inventory,
                        List.of(fixture.depot()), HiredEquipmentNeed.of(HiredEquipmentNeed.Kind.HOE), ignored -> 1.0D,
                        "hoe", new SettlementWorkBudget(fixture.worker(), 1_000, 1.0D)),
                "background work uses its normal budget and storage transfer");
        fixture.worker().inventorySnapshot(inventory.captureSnapshot());
        helper.assertTrue(fixture.depot().getItem(0).isEmpty()
                        && inventory.getItem(HiredJobInventory.MAINHAND_SLOT).is(Items.DIAMOND_HOE),
                "replacement ownership moves from storage to the equipment slot");
        var restored = SettlementRecord.load(fixture.town().save(level.registryAccess()), level.registryAccess(), () -> {});
        var saved = restored.workers().get(fixture.worker().id());
        helper.assertTrue(saved.items(level.registryAccess()).get(HiredJobInventory.MAINHAND_SLOT).is(Items.DIAMOND_HOE),
                "the replacement survives settlement reload");
        var demand = SettlementDemandService.inspect(level, restored).demands().stream()
                .filter(value -> value.kind() == WorkSupplyDemand.Kind.EQUIPMENT).findFirst().orElseThrow();
        helper.assertValueEqual(demand.supply().committed(), 1L, "reload does not create another replacement request");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_equipment")
    public static void backgroundEquipmentHandoffPreservesDisplacedPersonalGearAndUnrelatedState(GameTestHelper helper) {
        var fixture = fixture(helper, SettlementAreaType.MINE);
        var level = helper.getLevel();
        var original = fixture.owner();
        com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerEquipment.setInventoryEquipment(
                original, net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(Items.NETHERITE_SWORD));
        fixture.worker().rememberPayments(original);
        var owner = fixture.worker().context(level, fixture.town());
        var inventory = HiredJobInventory.getJobInventory(owner);
        var full = new java.util.ArrayList<ItemStack>();
        for (int index = 0; index < 36; index++) full.add(new ItemStack(Items.COBBLESTONE, 64));
        com.jvn.villagerretaliation.inventory.VillagerInventoryAccess.replaceFullInventory(owner, full);
        fixture.depot().setItem(0, new ItemStack(Items.IRON_PICKAXE));
        ready(helper, fixture, new ItemStack(Items.IRON_PICKAXE));
        SettlementBackgroundTools.fetchTool(level, fixture.town(), fixture.worker(), inventory, List.of(fixture.depot()),
                HiredEquipmentNeed.of(HiredEquipmentNeed.Kind.PICKAXE), ignored -> 1, "pickaxe",
                new SettlementWorkBudget(fixture.worker(), 1_000, 1));
        helper.assertValueEqual(fixture.worker().status(), "equipment_capacity", "background exchanges report the same storage blocker");
        helper.assertTrue(fixture.depot().getItem(0).is(Items.IRON_PICKAXE) && owner.getMainHandItem().is(Items.NETHERITE_SWORD),
                "a blocked background exchange keeps its source and existing gear intact");

        com.jvn.villagerretaliation.inventory.VillagerInventoryAccess.replaceFullInventory(owner,
                com.jvn.villagerretaliation.inventory.VillagerInventoryAccess.captureFullInventory(original));
        SettlementBackgroundTools.fetchTool(level, fixture.town(), fixture.worker(), inventory, List.of(fixture.depot()),
                HiredEquipmentNeed.of(HiredEquipmentNeed.Kind.PICKAXE), ignored -> 1, "pickaxe",
                new SettlementWorkBudget(fixture.worker(), 1_000, 1));
        fixture.worker().inventorySnapshot(inventory.captureSnapshot());
        helper.assertTrue(fixture.depot().getItem(0).isEmpty(), "the approved replacement leaves its source exactly once");
        var saved = SettlementRecord.load(fixture.town().save(level.registryAccess()), level.registryAccess(), () -> {})
                .workers().get(fixture.worker().id());
        var invalid = saved.save();
        invalid.putString("EquipmentChanges", "invalid");
        rejected(helper, () -> SettlementWorkerState.load(invalid), "malformed equipment changes reach save recovery");
        var mismatched = saved.save();
        mismatched.getCompound("EntityContext").putUUID("UUID", UUID.randomUUID());
        rejected(helper, () -> SettlementWorkerState.load(mismatched), "equipment handoffs reject another worker's entity data");
        helper.assertTrue(SettlementWorkerDiagnostics.diagnostic("equipment_capacity").severity().attention(),
                "capacity blockers have a recovery action");
        helper.assertValueEqual(SettlementWorkerDiagnostics.diagnostic("equipment_busy").severity(),
                SettlementWorkerDiagnostics.Severity.INFO, "temporary equipment use does not create a warning");
        original.setHealth(7);
        original.getPersistentData().putString("UnrelatedLaterState", "preserved");
        helper.assertTrue(saved.restore(original), "the returning entity receives the saved equipment handoff");
        helper.assertValueEqual(original.getHealth(), 7.0F, "equipment handoff does not reset unrelated health");
        helper.assertValueEqual(original.getPersistentData().getString("UnrelatedLaterState"), "preserved",
                "equipment handoff merges only changed entity fields");
        helper.assertTrue(original.getMainHandItem().is(Items.IRON_PICKAXE),
                "the real villager receives the background replacement");
        helper.assertValueEqual(com.jvn.villagerretaliation.inventory.VillagerInventoryAccess.captureFullInventory(original).stream()
                .filter(stack -> stack.is(Items.NETHERITE_SWORD)).mapToInt(ItemStack::getCount).sum(), 1,
                "displaced personal gear survives the loaded handoff exactly once");
        helper.assertFalse(saved.restore(original), "repeated handoff cannot apply the same equipment changes twice");
        var reloaded = net.minecraft.world.entity.EntityType.VILLAGER.create(level);
        reloaded.load(original.saveWithoutId(new CompoundTag()));
        helper.assertValueEqual(com.jvn.villagerretaliation.inventory.VillagerInventoryAccess.captureFullInventory(reloaded).stream()
                .filter(stack -> stack.is(Items.NETHERITE_SWORD)).mapToInt(ItemStack::getCount).sum(), 1,
                "entity reload preserves the personal item's sole owner");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_equipment")
    public static void equipmentSnapshotCannotDiscardUnrecordedLiveGear(GameTestHelper helper) {
        var fixture = fixture(helper, SettlementAreaType.FARM);
        var level = helper.getLevel();
        var owner = fixture.owner();
        var full = new java.util.ArrayList<ItemStack>();
        for (int index = 0; index < 36; index++) full.add(new ItemStack(Items.COBBLESTONE, 64));
        com.jvn.villagerretaliation.inventory.VillagerInventoryAccess.replaceFullInventory(owner, full);
        com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerEquipment.setInventoryEquipment(
                owner, net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(Items.NETHERITE_SWORD));
        var items = fixture.worker().items(level.registryAccess());
        items.set(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.IRON_HOE));
        fixture.worker().updateInventory(level.registryAccess(), items);
        fixture.worker().changed();
        helper.assertTrue(fixture.worker().restore(owner), "older background snapshots still restore their authoritative job inventory");
        var inventory = HiredJobInventory.getJobInventory(owner);
        HiredJobInventory.clearRuntimeState(owner);
        HiredJobInventory.maintainEquipmentSlots(owner);
        helper.assertTrue(owner.getMainHandItem().is(Items.NETHERITE_SWORD)
                        && inventory.getItem(HiredJobInventory.MAINHAND_SLOT).is(Items.IRON_HOE),
                "restoration and maintenance preserve both owners until the displaced item can be stored");
        helper.assertTrue(inventory.equipBestTool(HiredEquipmentNeed.of(HiredEquipmentNeed.Kind.HOE), ignored -> 1).isEmpty(),
                "the work action also waits for capacity");
        full.set(0, ItemStack.EMPTY);
        com.jvn.villagerretaliation.inventory.VillagerInventoryAccess.replaceFullInventory(owner, full);
        helper.assertTrue(inventory.equipBestTool(HiredEquipmentNeed.of(HiredEquipmentNeed.Kind.HOE), ignored -> 1).is(Items.IRON_HOE),
                "freeing capacity completes the previously blocked exchange");
        helper.assertValueEqual(com.jvn.villagerretaliation.inventory.VillagerInventoryAccess.captureFullInventory(owner).stream()
                .filter(stack -> stack.is(Items.NETHERITE_SWORD)).mapToInt(ItemStack::getCount).sum(), 1,
                "unrecorded live gear is stored once after recovery");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_equipment")
    public static void taskChangesRecheckUsableEquipmentWithoutRecountingSupplySlots(GameTestHelper helper) {
        var fixture = fixture(helper, SettlementAreaType.MINE);
        var owner = fixture.worker().context(helper.getLevel(), fixture.town());
        var inventory = HiredJobInventory.getJobInventory(owner);
        inventory.setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.WOODEN_PICKAXE));
        inventory.setItem(HiredJobInventory.HOTBAR_START, new ItemStack(Items.IRON_PICKAXE));
        fixture.worker().inventorySnapshot(inventory.captureSnapshot());
        HiredEquipmentNeed.mining(HiredMiningMode.EXPOSED_ORES, Blocks.DIAMOND_ORE.defaultBlockState()).remember(fixture.worker().settings());
        var row = equipment(helper, fixture);
        helper.assertValueEqual(row.supply().committed(), 1L, "the compatible carried tool covers exactly one requirement");
        helper.assertValueEqual(row.supply().missing(), 0L, "a spare in an eligible job slot can be equipped without an external purchase");
        inventory.setItem(HiredJobInventory.HOTBAR_START, ItemStack.EMPTY);
        fixture.worker().inventorySnapshot(inventory.captureSnapshot());
        row = equipment(helper, fixture);
        helper.assertValueEqual(row.supply().missing(), 1L, "an unsuitable equipped pickaxe does not hide the task's shortage");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_equipment")
    public static void chargedGuardCrossbowsAndCarriedProjectilesKeepNativeAmmunitionAccounting(GameTestHelper helper) {
        var fixture = fixture(helper, SettlementAreaType.GUARD_POST);
        var level = helper.getLevel();
        var inventory = HiredJobInventory.getJobInventory(fixture.owner());
        var crossbow = new ItemStack(Items.CROSSBOW);
        crossbow.set(net.minecraft.core.component.DataComponents.CHARGED_PROJECTILES,
                net.minecraft.world.item.component.ChargedProjectiles.of(List.of(new ItemStack(Items.SPECTRAL_ARROW))));
        inventory.setItem(HiredJobInventory.MAINHAND_SLOT, crossbow);
        fixture.worker().inventorySnapshot(inventory.captureSnapshot());
        helper.assertTrue(fixture.worker().context(level, fixture.town()) != null,
                "background guards retain their equipment ownership context");
        var row = equipment(helper, fixture);
        helper.assertValueEqual(row.supply().protectedMinimum(), 0L,
                "a loaded crossbow does not report an essential ammunition interruption");
        helper.assertTrue(row.requirement().value(new ItemStack(Items.SPECTRAL_ARROW)) > 0
                        && row.requirement().value(new ItemStack(Items.TIPPED_ARROW)) > 0,
                "demand accepts the same projectile families as native combat");

        crossbow.set(net.minecraft.core.component.DataComponents.CHARGED_PROJECTILES,
                net.minecraft.world.item.component.ChargedProjectiles.EMPTY);
        inventory.setItem(HiredJobInventory.MAINHAND_SLOT, crossbow);
        fixture.worker().inventorySnapshot(inventory.captureSnapshot());
        row = equipment(helper, fixture);
        helper.assertValueEqual(row.supply().protectedMinimum(), 1L,
                "consuming the last loaded projectile exposes the essential shortage");
        inventory.setItem(HiredJobInventory.HOTBAR_START, new ItemStack(Items.SPECTRAL_ARROW, 4));
        fixture.worker().inventorySnapshot(inventory.captureSnapshot());
        var restored = SettlementRecord.load(fixture.town().save(level.registryAccess()), level.registryAccess(), () -> {});
        row = SettlementDemandService.inspect(level, restored).demands().stream()
                .filter(value -> value.kind() == WorkSupplyDemand.Kind.EQUIPMENT).findFirst().orElseThrow();
        helper.assertValueEqual(row.supply().committed(), 1L,
                "carried ammunition covers the immediate requirement once after reload");
        helper.assertValueEqual(row.supply().missing(), 0L, "a guard with usable carried projectiles does not buy another immediate round");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_equipment")
    public static void worksiteSparesCreditCarriedStockOnceAndKeepConfiguredPriority(GameTestHelper helper) {
        var fixture = fixture(helper, SettlementAreaType.FARM);
        var level = helper.getLevel();
        var inventory = HiredJobInventory.getJobInventory(fixture.owner());
        inventory.setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.IRON_HOE));
        inventory.setItem(HiredJobInventory.HOTBAR_START, new ItemStack(Items.DIAMOND_HOE));
        fixture.worker().inventorySnapshot(inventory.captureSnapshot());
        fixture.depot().setItem(0, new ItemStack(Items.STONE_HOE));
        var selection = new SettlementEquipmentPlan.Selection(HiredEquipmentNeed.Kind.HOE, 2, false, false);
        var configuration = new SettlementEquipmentPlan.Configuration(List.of(selection), 0, true);
        helper.assertTrue(fixture.town().setEquipmentPlan(fixture.town().leader(), fixture.worker().area(), configuration, -1),
                "an authorized worksite can opt into two spares");
        var rows = SettlementDemandService.inspect(level, fixture.town()).demands();
        var required = rows.stream().filter(row -> row.consumer().equals(fixture.worker().id())).findFirst().orElseThrow();
        var spare = rows.stream().filter(row -> row.consumer().equals(fixture.worker().area())).findFirst().orElseThrow();
        helper.assertValueEqual(required.supply().committed(), 1L, "the equipped tool covers its worker once");
        helper.assertValueEqual(spare.supply().committed(), 1L, "only the unused carried spare covers the reserve");
        helper.assertValueEqual(spare.supply().availableNow(), 1L, "the second spare uses real storage stock");
        helper.assertValueEqual(spare.supply().protectedMinimum(), 0L, "optional spares are not essential");
        helper.assertValueEqual(spare.supply().priority(), 0, "a low reserve priority is not raised to the worksite priority");
        helper.assertValueEqual(spare.supply().targetReserve(), 2L, "the reserve remains separate from the equipped requirement");
        helper.assertValueEqual(spare.missing(), 0L, "equipped, carried, and stored equipment have distinct owners");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_equipment")
    public static void guardDraftsSeparateRequiredWeaponsOptionalArmorAndAmmunitionReserves(GameTestHelper helper) {
        var fixture = fixture(helper, SettlementAreaType.GUARD_POST);
        var draft = SettlementEquipmentPlan.Configuration.draft(List.of(HiredEquipmentNeed.Kind.CROSSBOW,
                HiredEquipmentNeed.Kind.CHESTPLATE, HiredEquipmentNeed.Kind.SHIELD));
        helper.assertTrue(!draft.enabled(), "a draft cannot start supplying a guard loadout");
        helper.assertValueEqual(draft.selection(HiredEquipmentNeed.Kind.CROSSBOW).spares(), 1, "durable drafts request one spare");
        helper.assertValueEqual(draft.selection(HiredEquipmentNeed.Kind.AMMUNITION).spares(), 32, "ranged drafts request 32 compatible projectiles");
        var configuration = new SettlementEquipmentPlan.Configuration(draft.selections(), 3, true);
        helper.assertTrue(fixture.town().setEquipmentPlan(fixture.town().leader(), fixture.worker().area(), configuration, -1),
                "the loadout is explicitly activated");
        var level = helper.getLevel();
        var rows = SettlementDemandService.inspect(level, fixture.town()).demands().stream()
                .filter(row -> row.consumer().equals(fixture.worker().id())).toList();
        var controls = SettlementDemandViewService.demands(level, fixture.town());
        helper.assertTrue(controls.stream().anyMatch(row -> row.consumer().equals(fixture.worker().id())
                        && row.control() == com.jvn.villagerretaliation.network.SettlementManagementPayload.DemandControl.RESIDENT),
                "missing guard equipment opens the resident controls");
        helper.assertTrue(controls.stream().anyMatch(row -> row.consumer().equals(fixture.worker().area())
                        && row.control() == com.jvn.villagerretaliation.network.SettlementManagementPayload.DemandControl.EQUIPMENT),
                "spare equipment opens the worksite reserve controls");
        var weapon = matching(rows, new ItemStack(Items.CROSSBOW));
        var armor = matching(rows, new ItemStack(Items.IRON_CHESTPLATE));
        var ammunition = matching(rows, new ItemStack(Items.ARROW));
        helper.assertValueEqual(weapon.supply().protectedMinimum(), 1L, "a required guard weapon is essential");
        helper.assertValueEqual(armor.supply().protectedMinimum(), 0L, "optional guard armor follows configured priority");
        helper.assertValueEqual(armor.supply().priority(), 3, "the selected loadout priority reaches optional armor");
        helper.assertValueEqual(ammunition.requirement().quantity(), 32L, "the total ammunition target includes the immediate round");
        helper.assertValueEqual(ammunition.supply().protectedMinimum(), 1L, "only the immediate round is essential by default");
        helper.assertValueEqual(ammunition.supply().targetReserve(), 31L, "the immediate round is not duplicated in the reserve");

        var inventory = HiredJobInventory.getJobInventory(fixture.owner());
        inventory.setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.CROSSBOW));
        inventory.setItem(HiredJobInventory.HOTBAR_START, new ItemStack(Items.SPECTRAL_ARROW, 20));
        fixture.worker().inventorySnapshot(inventory.captureSnapshot());
        var restored = SettlementRecord.load(fixture.town().save(level.registryAccess()), level.registryAccess(), () -> {});
        rows = SettlementDemandService.inspect(level, restored).demands().stream()
                .filter(row -> row.consumer().equals(fixture.worker().id())).toList();
        ammunition = matching(rows, new ItemStack(Items.SPECTRAL_ARROW));
        helper.assertValueEqual(ammunition.supply().committed(), 20L, "carried projectiles are credited once after reload");
        helper.assertValueEqual(ammunition.missing(), 12L, "only consumed or missing projectiles need replenishment");
        helper.assertValueEqual(matching(rows, new ItemStack(Items.CROSSBOW)).supply().committed(), 1L,
                "an equipped crossbow cannot also cover the worksite spare");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_equipment")
    public static void equipmentPlansPreserveRevisionAuthorityAndRecoveryBoundaries(GameTestHelper helper) {
        var fixture = fixture(helper, SettlementAreaType.FARM);
        var town = fixture.town();
        var area = fixture.worker().area();
        var delegate = UUID.randomUUID();
        var selection = new SettlementEquipmentPlan.Selection(HiredEquipmentNeed.Kind.HOE, 1, false, true);
        var configuration = new SettlementEquipmentPlan.Configuration(List.of(selection), 4, true);
        helper.assertTrue(!town.setEquipmentPlan(delegate, area, configuration, -1), "viewers cannot authorize equipment");
        town.setPermissions(town.leader(), delegate, java.util.Set.of(SettlementPermission.STAFF));
        helper.assertTrue(town.setEquipmentPlan(delegate, area, configuration, -1), "STAFF authorizes loadouts using existing storage links");
        helper.assertTrue(!town.setEquipmentPlan(delegate, area, configuration, -1), "a stale draft cannot overwrite an existing plan");
        long originalRevision = town.equipmentPlans().get(area).revision();
        town.setPermissions(town.leader(), delegate, java.util.Set.of());
        var suspended = town.equipmentPlans().get(area);
        helper.assertTrue(!suspended.active() && suspended.blocker().equals("policy_permission_changed"),
                "permission revocation suspends new replenishment immediately");
        town.setPermissions(town.leader(), delegate, java.util.Set.of(SettlementPermission.STAFF));
        helper.assertTrue(!town.equipmentPlans().get(area).active(), "restoring permission does not silently reactivate old authorization");
        var level = helper.getLevel();
        var saved = town.save(level.registryAccess());
        var restored = SettlementRecord.load(saved, level.registryAccess(), () -> {});
        helper.assertValueEqual(restored.equipmentPlans().get(area), suspended, "reload retains configuration, authority, revision, and suspension");
        helper.assertTrue(!restored.setEquipmentPlan(delegate, area, configuration, originalRevision), "revocation invalidates an open editor revision");
        helper.assertTrue(restored.setEquipmentPlan(delegate, area, configuration, suspended.revision()), "a fresh explicit edit can reauthorize the plan");
        long removedRevision = restored.equipmentPlans().get(area).revision();
        helper.assertTrue(restored.removeEquipmentPlan(delegate, area, removedRevision)
                        && restored.setEquipmentPlan(delegate, area, configuration, -1),
                "a worksite can explicitly replace its reserve plan");
        helper.assertTrue(!restored.setEquipmentPlan(delegate, area, configuration, removedRevision),
                "an editor for a removed plan cannot overwrite its replacement");
        helper.assertTrue(restored.removeArea(restored.leader(), area) && restored.equipmentPlans().isEmpty(),
                "removing the worksite also removes its loadout ownership");

        var oldSave = saved.copy();
        oldSave.remove("EquipmentPlans");
        helper.assertTrue(SettlementRecord.load(oldSave, level.registryAccess(), () -> {}).equipmentPlans().isEmpty(),
                "older saves acquire no implicit equipment plan");
        var duplicate = saved.copy();
        var plans = duplicate.getList("EquipmentPlans", net.minecraft.nbt.Tag.TAG_COMPOUND);
        plans.add(plans.getFirst().copy());
        rejected(helper, () -> SettlementRecord.load(duplicate, level.registryAccess(), () -> {}), "duplicate equipment ownership reaches save recovery");
        var malformed = saved.copy();
        malformed.getList("EquipmentPlans", net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0)
                .getCompound("Configuration").putString("Enabled", "true");
        rejected(helper, () -> SettlementRecord.load(malformed, level.registryAccess(), () -> {}), "malformed activation cannot silently load");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_equipment")
    public static void equipmentSelectionsRejectIncompatibleRolesAndConflictingHands(GameTestHelper helper) {
        var fixture = fixture(helper, SettlementAreaType.FARM);
        var guard = SettlementEquipmentPlan.Configuration.draft(List.of(HiredEquipmentNeed.Kind.BOW));
        helper.assertTrue(!fixture.town().setEquipmentPlan(fixture.town().leader(), fixture.worker().area(), guard, -1),
                "a farmer cannot receive a guard loadout");
        rejected(helper, () -> SettlementEquipmentPlan.Configuration.draft(List.of(HiredEquipmentNeed.Kind.BOW, HiredEquipmentNeed.Kind.CROSSBOW)),
                "two weapons cannot own the same hand");
        rejected(helper, () -> new SettlementEquipmentPlan.Selection(HiredEquipmentNeed.Kind.MINING, 1, false, false),
                "a block-specific task is not a persistent reserve category");
        helper.assertTrue(HiredEquipmentNeed.of(HiredEquipmentNeed.Kind.HELMET).test(new ItemStack(Items.IRON_HELMET))
                        && !HiredEquipmentNeed.of(HiredEquipmentNeed.Kind.HELMET).test(new ItemStack(Items.IRON_BOOTS)),
                "armor uses the native equipment slot");
        var worn = new ItemStack(Items.SHIELD);
        worn.setDamageValue(worn.getMaxDamage() - 1);
        helper.assertTrue(HiredEquipmentNeed.of(HiredEquipmentNeed.Kind.SHIELD).test(worn), "a usable shield remains eligible at low durability");
        worn.setDamageValue(worn.getMaxDamage());
        helper.assertTrue(!HiredEquipmentNeed.of(HiredEquipmentNeed.Kind.SHIELD).test(worn), "an exhausted shield does not cover a loadout");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_equipment")
    public static void nonTickingEntitiesCannotHideEquipmentConsumedByBackgroundWork(GameTestHelper helper) {
        var fixture = fixture(helper, SettlementAreaType.GUARD_POST);
        var level = helper.getLevel();
        var inventory = HiredJobInventory.getJobInventory(fixture.owner());
        inventory.setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.CROSSBOW));
        inventory.setItem(HiredJobInventory.HOTBAR_START, new ItemStack(Items.ARROW, 20));
        fixture.worker().inventorySnapshot(inventory.captureSnapshot());
        helper.assertTrue(level.addFreshEntity(fixture.owner()), "the loaded entity is registered");
        try {
            helper.assertTrue(!level.isPositionEntityTicking(fixture.owner().blockPosition()), "the worker remains under background authority");
            var context = fixture.worker().context(level, fixture.town());
            var background = HiredJobInventory.getJobInventory(context);
            background.setItem(HiredJobInventory.HOTBAR_START, ItemStack.EMPTY);
            fixture.worker().inventorySnapshot(background.captureSnapshot());
            var row = matching(SettlementDemandService.inspect(level, fixture.town()).demands(), new ItemStack(Items.ARROW));
            helper.assertValueEqual(row.supply().committed(), 0L, "a stale loaded inventory cannot cover consumed background ammunition");
            helper.assertValueEqual(row.missing(), 1L, "the authoritative inventory reports the new shortage");
        } finally {
            fixture.owner().discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_equipment")
    public static void equipmentPoliciesKeepNativeConsumerIdentityAndSuspendWithRemovedReserves(GameTestHelper helper) {
        var fixture = fixture(helper, SettlementAreaType.FARM);
        var level = helper.getLevel();
        var town = fixture.town();
        var row = equipment(helper, fixture);
        var target = new SettlementSupplyPolicy.Target(SettlementSupplyPolicy.TargetKind.EQUIPMENT, fixture.worker().id(),
                row.supply().id(), null, ItemStack.EMPTY, 0);
        var configuration = new SettlementSupplyPolicy.Configuration("Hoe supply", target, 7, List.of(), List.of());
        var policy = new SettlementSupplyPolicy(UUID.randomUUID(), configuration, town.leader(), () -> {});
        helper.assertValueEqual(SettlementPolicyValidation.target(level, town, policy), "", "a native worker requirement is a policy consumer");
        var snapshot = SettlementDemandService.inspect(level, town);
        var inputs = SettlementPolicyPlanner.targets(level, town, policy, snapshot);
        helper.assertValueEqual(inputs.size(), 1, "the selected requirement appears once in the policy preview");
        helper.assertValueEqual(inputs.getFirst().id(), row.supply().id(), "the policy binds the same requirement identity as allocation");
        helper.assertValueEqual(inputs.getFirst().missing(), 1L, "the policy sees the actual missing tool");
        var inventory = HiredJobInventory.getJobInventory(fixture.owner());
        inventory.setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.IRON_HOE));
        fixture.worker().inventorySnapshot(inventory.captureSnapshot());
        inputs = SettlementPolicyPlanner.targets(level, town, policy, snapshot.refresh());
        helper.assertValueEqual(inputs.getFirst().missing(), 0L, "equipping a tool removes proposed replacement production");
        helper.assertValueEqual(inputs.getFirst().id(), row.supply().id(), "fulfilling the requirement does not invalidate its connection");

        var delegate = UUID.randomUUID();
        town.setPermissions(town.leader(), delegate, java.util.Set.of(SettlementPermission.STAFF));
        town.setEquipmentPlan(delegate, fixture.worker().area(), new SettlementEquipmentPlan.Configuration(
                List.of(new SettlementEquipmentPlan.Selection(HiredEquipmentNeed.Kind.HOE, 1, false, false)), 2, true), -1);
        var reserveTarget = new SettlementSupplyPolicy.Target(SettlementSupplyPolicy.TargetKind.EQUIPMENT, fixture.worker().area(),
                null, null, ItemStack.EMPTY, 0);
        var reservePolicy = new SettlementSupplyPolicy(UUID.randomUUID(), new SettlementSupplyPolicy.Configuration(
                "Spare hoes", reserveTarget, 2, List.of(), List.of()), town.leader(), () -> {});
        helper.assertValueEqual(SettlementPolicyValidation.target(level, town, reservePolicy), "", "a worksite reserve is also a policy consumer");
        inputs = SettlementPolicyPlanner.targets(level, town, reservePolicy, SettlementDemandService.inspect(level, town));
        helper.assertValueEqual(inputs.size(), 1, "the worksite target excludes separate worker requirements");
        helper.assertValueEqual(inputs.getFirst().missing(), 1L, "equipped gear does not conceal the spare shortage");
        town.setPermissions(town.leader(), delegate, java.util.Set.of());
        helper.assertValueEqual(SettlementPolicyValidation.target(level, town, reservePolicy), "policy_permission_changed",
                "a reserve's revoked authorization blocks policy work");
        town.removeArea(town.leader(), fixture.worker().area());
        helper.assertValueEqual(SettlementPolicyValidation.target(level, town, policy), "policy_consumer_missing",
                "removing an assignment invalidates its equipment target");
        helper.assertValueEqual(SettlementPolicyValidation.target(level, town, reservePolicy), "policy_consumer_missing",
                "removing a worksite invalidates its reserve target");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_equipment")
    public static void guardShieldLoansCoverLoadoutWithoutDuplicatingTheirInventorySource(GameTestHelper helper) {
        var fixture = fixture(helper, SettlementAreaType.GUARD_POST);
        var town = fixture.town();
        var level = helper.getLevel();
        var configuration = new SettlementEquipmentPlan.Configuration(
                List.of(new SettlementEquipmentPlan.Selection(HiredEquipmentNeed.Kind.SHIELD, 0, true, false)), 4, true);
        helper.assertTrue(town.setEquipmentPlan(town.leader(), fixture.worker().area(), configuration, -1), "a guard can request a shield");
        helper.assertTrue(com.jvn.villagerretaliation.inventory.VillagerInventoryAccess.addItem(fixture.owner(), new ItemStack(Items.SHIELD)).isEmpty(),
                "the shield starts in personal inventory");
        helper.assertTrue(com.jvn.villagerretaliation.inventory.VillagerDefensiveLoadoutService.ensureGuardShield(fixture.owner()),
                "native guard behavior borrows the shield into its temporary main hand");
        fixture.worker().observe(level, fixture.owner(), new SettlementWorkforceService.Duty(town, town.workplace(fixture.worker().area())), level.getGameTime());
        var restored = SettlementRecord.load(town.save(level.registryAccess()), level.registryAccess(), () -> {});
        var row = matching(SettlementDemandService.inspect(level, restored).demands(), new ItemStack(Items.SHIELD));
        helper.assertValueEqual(row.supply().committed(), 1L, "the temporary shield loan covers the configured shield after reload");
        helper.assertValueEqual(row.missing(), 0L, "a shield in use does not trigger another replacement");
        var owner = restored.workers().get(fixture.worker().id()).context(level, restored);
        helper.assertTrue(com.jvn.villagerretaliation.inventory.VillagerInventoryAccess.captureFullInventory(owner).stream()
                        .noneMatch(stack -> stack.is(Items.SHIELD)), "the loan has no second owner in personal storage");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_equipment")
    public static void equipmentManagementPacketsEnforcePermissionsRevisionsAndCompleteSnapshots(GameTestHelper helper) {
        var fixture = fixture(helper, SettlementAreaType.GUARD_POST);
        var town = fixture.town();
        var level = helper.getLevel();
        var player = helper.makeMockServerPlayerInLevel();
        var draft = SettlementEquipmentPlan.Configuration.draft(List.of(HiredEquipmentNeed.Kind.CROSSBOW, HiredEquipmentNeed.Kind.SHIELD));
        var configuration = new SettlementEquipmentPlan.Configuration(draft.selections(), 4, true);
        var request = new com.jvn.villagerretaliation.network.SettlementManagementActionPayload(town.id().value(),
                new com.jvn.villagerretaliation.network.SettlementManagementActionPayload.SaveEquipment(fixture.worker().area(), -1, configuration));
        var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), level.registryAccess());
        try {
            com.jvn.villagerretaliation.network.SettlementManagementActionPayload.STREAM_CODEC.encode(buffer, request);
            var decoded = com.jvn.villagerretaliation.network.SettlementManagementActionPayload.STREAM_CODEC.decode(buffer);
            helper.assertValueEqual(decoded, request, "equipment selections and expected revision survive the request codec");
            town.setPermissions(town.leader(), player.getUUID(), java.util.Set.of(SettlementPermission.VIEW));
            helper.assertValueEqual(SettlementManagementService.executeFailure(player, decoded), "permission_staff",
                    "inspection access cannot configure equipment");
            town.setPermissions(town.leader(), player.getUUID(), java.util.Set.of(SettlementPermission.STAFF));
            helper.assertTrue(SettlementManagementService.execute(player, decoded), "staff can configure equipment without changing logistics");
            helper.assertValueEqual(SettlementManagementService.executeFailure(player, decoded), "state_changed",
                    "replaying a stale create request cannot reset an active loadout");
            var saved = town.equipmentPlans().get(fixture.worker().area());
            var snapshot = SettlementManagementService.snapshot(player,
                    new com.jvn.villagerretaliation.network.SettlementManagementRequestPayload(town.id().value(),
                            com.jvn.villagerretaliation.network.SettlementManagementRequestPayload.Page.AREAS, 0,
                            fixture.worker().area().toString(), 1, false));
            com.jvn.villagerretaliation.network.SettlementManagementPayload.STREAM_CODEC.encode(buffer, snapshot);
            var restored = com.jvn.villagerretaliation.network.SettlementManagementPayload.STREAM_CODEC.decode(buffer);
            var area = (com.jvn.villagerretaliation.network.SettlementManagementPayload.Area) restored.entries().getFirst();
            helper.assertValueEqual(area.equipment(), saved, "both management views receive the complete saved equipment configuration");
            helper.assertValueEqual(buffer.readableBytes(), 0, "the equipment fields leave no unread packet bytes");
            town.setPermissions(town.leader(), player.getUUID(), java.util.Set.of(SettlementPermission.VIEW));
            var remove = new com.jvn.villagerretaliation.network.SettlementManagementActionPayload(town.id().value(),
                    new com.jvn.villagerretaliation.network.SettlementManagementActionPayload.SaveEquipment(area.id(), saved.revision(), null));
            helper.assertValueEqual(SettlementManagementService.executeFailure(player, remove), "permission_staff",
                    "permission revocation is rechecked when a removal arrives");
            town.setPermissions(town.leader(), player.getUUID(), java.util.Set.of(SettlementPermission.STAFF));
            helper.assertValueEqual(SettlementManagementService.executeFailure(player, remove), "state_changed",
                    "revocation also invalidates the previous editor revision");
            remove = new com.jvn.villagerretaliation.network.SettlementManagementActionPayload(town.id().value(),
                    new com.jvn.villagerretaliation.network.SettlementManagementActionPayload.SaveEquipment(area.id(),
                            town.equipmentPlans().get(area.id()).revision(), null));
            com.jvn.villagerretaliation.network.SettlementManagementActionPayload.STREAM_CODEC.encode(buffer, remove);
            helper.assertTrue(SettlementManagementService.execute(player,
                            com.jvn.villagerretaliation.network.SettlementManagementActionPayload.STREAM_CODEC.decode(buffer)),
                    "an authorized current removal uses the same bounded request path");
            helper.assertTrue(town.equipmentPlans().isEmpty(), "removal leaves no implicit reserve or loadout");
        } finally {
            buffer.release();
        }
        helper.succeed();
    }

    private static SettlementDemandService.Demand matching(List<SettlementDemandService.Demand> rows, ItemStack sample) {
        return rows.stream().filter(row -> row.kind() == WorkSupplyDemand.Kind.EQUIPMENT && row.requirement().value(sample) > 0)
                .findFirst().orElseThrow();
    }

    private static void rejected(GameTestHelper helper, Runnable action, String reason) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        helper.fail(reason);
    }

    private static SettlementDemandService.Demand equipment(GameTestHelper helper, Fixture fixture) {
        return SettlementDemandService.inspect(helper.getLevel(), fixture.town()).demands().stream()
                .filter(demand -> demand.kind() == WorkSupplyDemand.Kind.EQUIPMENT && demand.consumer().equals(fixture.worker().id()))
                .findFirst().orElseThrow();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_equipment")
    public static void backgroundFarmerReplacementsPreservePersonalGearAndRejectExhaustedTools(GameTestHelper helper) {
        backgroundToolExchange(helper, SettlementAreaType.FARM, Items.IRON_HOE);
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_equipment")
    public static void backgroundFisherReplacementsPreservePersonalGearAndRejectExhaustedTools(GameTestHelper helper) {
        backgroundToolExchange(helper, SettlementAreaType.FISHERY, Items.FISHING_ROD);
    }

    private static void backgroundToolExchange(GameTestHelper helper, SettlementAreaType type, net.minecraft.world.item.Item item) {
        var fixture = fixture(helper, type);
        var level = helper.getLevel();
        var owner = fixture.owner();
        var inventory = HiredJobInventory.getJobInventory(owner);
        var exhausted = new ItemStack(item);
        exhausted.setDamageValue(exhausted.getMaxDamage());
        inventory.setItem(HiredJobInventory.MAINHAND_SLOT, exhausted);
        fixture.worker().inventorySnapshot(inventory.captureSnapshot());
        com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerEquipment.setInventoryEquipment(
                owner, net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(Items.NETHERITE_SWORD));
        var full = new java.util.ArrayList<ItemStack>();
        for (int index = 0; index < 36; index++) full.add(new ItemStack(Items.COBBLESTONE, 64));
        com.jvn.villagerretaliation.inventory.VillagerInventoryAccess.replaceFullInventory(owner, full);
        fixture.worker().rememberPayments(owner);
        fixture.depot().setItem(0, exhausted.copy());
        var replacement = new ItemStack(item);
        replacement.setDamageValue(replacement.getMaxDamage() - 1);
        fixture.depot().setItem(1, replacement.copy());
        fixture.worker().settings().putBoolean("AutoDepositOutputs", false);
        ready(helper, fixture, replacement);
        Runnable tick = () -> {
            if (type == SettlementAreaType.FARM) SettlementBackgroundFarming.tick(level, fixture.town(), fixture.worker(), 1_000, 1);
            else SettlementBackgroundFishing.tick(level, fixture.town(), fixture.worker(), 1_000, 1, level.getGameTime());
        };
        tick.run();
        helper.assertValueEqual(fixture.worker().status(), "equipment_capacity", "tool collection reports unsafe displacement");
        helper.assertTrue(ItemStack.isSameItemSameComponents(fixture.depot().getItem(1), replacement),
                "blocked collection leaves the real replacement in its approved depot");
        full.set(0, ItemStack.EMPTY);
        com.jvn.villagerretaliation.inventory.VillagerInventoryAccess.replaceFullInventory(owner, full);
        fixture.worker().rememberPayments(owner);
        tick.run();
        helper.assertTrue(fixture.depot().getItem(1).isEmpty() && !fixture.depot().getItem(0).isEmpty(),
                "collection selects usable equipment and leaves the exhausted depot item untouched");
        helper.assertTrue(ItemStack.isSameItemSameComponents(fixture.worker().items(level.registryAccess())
                        .get(HiredJobInventory.MAINHAND_SLOT), replacement),
                "a tool with one remaining durability point is usable");
        tick.run();
        helper.assertTrue(ItemStack.isSameItemSameComponents(fixture.worker().items(level.registryAccess())
                        .get(HiredJobInventory.MAINHAND_SLOT), replacement),
                "an idle background worker retains its usable tool");
        var saved = SettlementWorkerState.load(fixture.worker().save());
        helper.assertTrue(saved.restore(owner), "the saved tool exchange restores to the original worker");
        helper.assertTrue(ItemStack.isSameItemSameComponents(owner.getMainHandItem(), replacement),
                "the equipped tool keeps its exact durability through reload");
        helper.assertValueEqual(com.jvn.villagerretaliation.inventory.VillagerInventoryAccess.captureFullInventory(owner).stream()
                .filter(stack -> stack.is(Items.NETHERITE_SWORD)).mapToInt(ItemStack::getCount).sum(), 1,
                "the displaced personal sword returns exactly once");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_equipment")
    public static void backgroundGuardLoadoutReplenishesEquipmentAndOnlyMissingAmmunition(GameTestHelper helper) {
        var fixture = fixture(helper, SettlementAreaType.GUARD_POST);
        var level = helper.getLevel();
        var configuration = SettlementEquipmentPlan.Configuration.draft(List.of(HiredEquipmentNeed.Kind.CROSSBOW,
                HiredEquipmentNeed.Kind.HELMET, HiredEquipmentNeed.Kind.SHIELD));
        helper.assertTrue(fixture.town().setEquipmentPlan(fixture.town().leader(), fixture.worker().area(),
                new SettlementEquipmentPlan.Configuration(configuration.selections(), 5, true), -1), "the guard loadout is activated");
        var crossbow = new ItemStack(Items.CROSSBOW);
        crossbow.setDamageValue(crossbow.getMaxDamage() - 1);
        crossbow.set(net.minecraft.core.component.DataComponents.CHARGED_PROJECTILES,
                net.minecraft.world.item.component.ChargedProjectiles.of(new ItemStack(Items.TIPPED_ARROW)));
        crossbow.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Guard crossbow"));
        var ammunition = new ItemStack(Items.SPECTRAL_ARROW, 40);
        ammunition.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Guard arrows"));
        fixture.depot().setItem(0, crossbow.copy());
        fixture.depot().setItem(1, ammunition.copy());
        fixture.depot().setItem(2, new ItemStack(Items.IRON_HELMET));
        fixture.depot().setItem(3, new ItemStack(Items.SHIELD));
        com.jvn.villagerretaliation.inventory.VillagerInventoryAccess.addItem(fixture.owner(), new ItemStack(Items.ARROW, 5));
        fixture.worker().rememberPayments(fixture.owner());
        for (int step = 0; step < 6; step++) {
            SettlementTestPlots.planSupplies(level, fixture.town());
            SettlementEquipmentService.tick(level, fixture.town(), fixture.worker(), 1_000, 1);
        }
        var owner = fixture.worker().context(level, fixture.town());
        helper.assertTrue(ItemStack.isSameItemSameComponents(owner.getMainHandItem(), crossbow),
                "the selected crossbow keeps its charged projectile, name and last durability point");
        helper.assertTrue(owner.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).is(Items.IRON_HELMET)
                        && owner.getOffhandItem().is(Items.SHIELD), "configured armor and shield are equipped through native ownership, status=" + fixture.worker().status()
                        + ", head=" + owner.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD) + ", offhand=" + owner.getOffhandItem());
        helper.assertValueEqual(HiredRangedAmmo.carriedAmmo(owner), 32, "personal and job ammunition jointly satisfy the selected reserve");
        fixture.depot().setItem(4, new ItemStack(Items.NETHERITE_HELMET));
        SettlementTestPlots.planSupplies(level, fixture.town());
        helper.assertValueEqual(SettlementAllocationService.inspect(level, fixture.town(), fixture.worker().id(), null,
                fixture.depot().position(), new ItemStack(Items.NETHERITE_HELMET)).allowance(), 0,
                "a fully equipped guard cannot consume the worksite spare as another replacement");
        helper.assertValueEqual(SettlementAllocationService.inspect(level, fixture.town(), UUID.randomUUID(), null,
                fixture.depot().position(), new ItemStack(Items.NETHERITE_HELMET)).allowance(), 0,
                "the worksite spare remains unavailable to unrelated consumers");
        helper.assertValueEqual(fixture.depot().getItem(1).getCount(), 13, "only the missing 27 projectiles leave approved storage");
        var inventory = HiredJobInventory.getJobInventory(owner);
        helper.assertValueEqual(inventory.findSupply(stack -> stack.is(Items.SPECTRAL_ARROW))
                        .get(net.minecraft.core.component.DataComponents.CUSTOM_NAME),
                ammunition.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME),
                "native supply ownership preserves the projectile's custom name");
        helper.assertValueEqual(inventory.consumeSupply(HiredRangedAmmo::isAmmo, 7), 7, "native ammunition use consumes real carried stock");
        for (int slot = HiredJobInventory.MAIN_GRID_START; slot < HiredJobInventory.FILTER_SLOT; slot++)
            if (inventory.getItem(slot).isEmpty()) inventory.setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        fixture.worker().inventorySnapshot(inventory.captureSnapshot());
        fixture.worker().changed();
        SettlementTestPlots.planSupplies(level, fixture.town());
        SettlementEquipmentService.tick(level, fixture.town(), fixture.worker(), 1_000, 1);
        helper.assertValueEqual(fixture.depot().getItem(1).getCount(), 6, "replenishment replaces only consumed ammunition");
        var saved = SettlementWorkerState.load(fixture.worker().save());
        helper.assertTrue(saved.restore(fixture.owner()), "equipment and ammunition return to the native guard");
        helper.assertValueEqual(HiredRangedAmmo.carriedAmmo(fixture.owner()), 32, "handoff preserves exactly one complete ammunition reserve");
        helper.assertTrue(ItemStack.isSameItemSameComponents(fixture.owner().getMainHandItem(), crossbow),
                "replenishing ammunition never replaces a usable charged weapon");
        helper.assertFalse(saved.restore(fixture.owner()), "the guard handoff cannot repeat its inventory changes");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_equipment")
    public static void guardLoadoutHonorsManualEquipmentCapacityAndPermissionRevocation(GameTestHelper helper) {
        var fixture = fixture(helper, SettlementAreaType.GUARD_POST);
        var level = helper.getLevel();
        var selected = new SettlementEquipmentPlan.Configuration(List.of(
                new SettlementEquipmentPlan.Selection(HiredEquipmentNeed.Kind.SHIELD, 0, true, false)), 5, true);
        var delegate = UUID.randomUUID();
        helper.assertTrue(fixture.town().setPermissions(fixture.town().leader(), delegate, java.util.Set.of(SettlementPermission.STAFF)),
                "a staff delegate can manage the loadout");
        helper.assertTrue(fixture.town().setEquipmentPlan(delegate, fixture.worker().area(), selected, -1), "the delegate activates the loadout");
        com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerEquipment.setInventoryEquipment(
                fixture.owner(), net.minecraft.world.entity.EquipmentSlot.OFFHAND, new ItemStack(Items.TOTEM_OF_UNDYING));
        com.jvn.villagerretaliation.inventory.VillagerDefensiveLoadoutService.markManualOffhand(fixture.owner(), true);
        fixture.worker().rememberPayments(fixture.owner());
        fixture.depot().setItem(0, new ItemStack(Items.SHIELD));
        SettlementTestPlots.planSupplies(level, fixture.town());
        SettlementEquipmentService.tick(level, fixture.town(), fixture.worker(), 1_000, 1);
        helper.assertValueEqual(fixture.worker().status(), "equipment_manual", "the explicit offhand choice blocks replacement");
        helper.assertFalse(fixture.depot().getItem(0).isEmpty(), "a manual override does not consume reserve stock");
        com.jvn.villagerretaliation.inventory.VillagerDefensiveLoadoutService.markManualOffhand(fixture.owner(), false);
        var full = new java.util.ArrayList<ItemStack>();
        for (int slot = 0; slot < 36; slot++) full.add(new ItemStack(Items.COBBLESTONE, 64));
        com.jvn.villagerretaliation.inventory.VillagerInventoryAccess.replaceFullInventory(fixture.owner(), full);
        fixture.worker().rememberPayments(fixture.owner());
        SettlementEquipmentService.tick(level, fixture.town(), fixture.worker(), 1_000, 1);
        helper.assertValueEqual(fixture.worker().status(), "equipment_capacity", "displaced personal gear requires safe storage");
        full.set(0, ItemStack.EMPTY);
        com.jvn.villagerretaliation.inventory.VillagerInventoryAccess.replaceFullInventory(fixture.owner(), full);
        fixture.worker().rememberPayments(fixture.owner());
        helper.assertTrue(fixture.town().setPermissions(fixture.town().leader(), delegate, java.util.Set.of()),
                "the equipment authority loses staff access");
        SettlementEquipmentService.tick(level, fixture.town(), fixture.worker(), 1_000, 1);
        helper.assertFalse(fixture.depot().getItem(0).isEmpty(), "revoked authority cannot complete a new exchange");
        var plan = fixture.town().equipmentPlans().get(fixture.worker().area());
        helper.assertTrue(fixture.town().setEquipmentPlan(fixture.town().leader(), fixture.worker().area(), selected, plan.revision()),
                "the leader explicitly reactivates equipment upkeep");
        SettlementTestPlots.planSupplies(level, fixture.town());
        SettlementEquipmentService.tick(level, fixture.town(), fixture.worker(), 1_000, 1);
        var owner = fixture.worker().context(level, fixture.town());
        helper.assertTrue(owner.getOffhandItem().is(Items.SHIELD) && fixture.depot().getItem(0).isEmpty(),
                "the safe exchange moves the approved shield exactly once");
        helper.assertValueEqual(com.jvn.villagerretaliation.inventory.VillagerInventoryAccess.captureFullInventory(owner).stream()
                .filter(stack -> stack.is(Items.TOTEM_OF_UNDYING)).mapToInt(ItemStack::getCount).sum(), 1,
                "the displaced totem remains privately owned");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_equipment")
    public static void loadedGuardWalksToApprovedEquipmentAndRefillsItsConfiguredReserve(GameTestHelper helper) {
        var fixture = fixture(helper, SettlementAreaType.GUARD_POST, true);
        var level = helper.getLevel();
        var owner = fixture.owner();
        var area = fixture.town().areas().get(fixture.worker().area());
        var configuration = SettlementEquipmentPlan.Configuration.draft(List.of(HiredEquipmentNeed.Kind.BOW));
        helper.assertTrue(fixture.town().setEquipmentPlan(fixture.town().leader(), area.id(),
                new SettlementEquipmentPlan.Configuration(configuration.selections(), 5, true), -1), "the active guard loadout is configured");
        SettlementTestPlots.activateDepot(level, fixture.town(), fixture.depot());
        fixture.depot().setItem(0, new ItemStack(Items.BOW));
        fixture.depot().setItem(1, new ItemStack(Items.ARROW, 64));
        for (var floor : BlockPos.betweenClosed(fixture.town().center().offset(-1, -1, -1), fixture.town().center().offset(11, -1, 5)))
            level.setBlockAndUpdate(floor, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        var inventory = HiredJobInventory.getJobInventory(owner);
        var context = new com.jvn.villagerretaliation.interaction.work.HiredWorkContext(inventory, HiredWorkStateStore.state(owner),
                area.center(), area.min(), area.max(), 12, 6, true, 100, false, true);
        try {
            SettlementDepotService.synchronizeStorage(level, owner, fixture.town());
            SettlementTestPlots.planSupplies(level, fixture.town());
            var work = new com.jvn.villagerretaliation.interaction.work.CombatWorker();
            var initialHand = owner.getMainHandItem().copy();
            var travelling = work.tick(level, owner, null, context);
            helper.assertValueEqual(travelling.status(), "interaction.work.status.fetching_equipment",
                    "a loaded guard travels to storage before changing inventory");
            helper.assertTrue(ItemStack.isSameItemSameComponents(owner.getMainHandItem(), initialHand)
                            && fixture.depot().getItem(0).is(Items.BOW),
                    "movement alone cannot withdraw or equip stock");
            owner.setPos(fixture.depot().position().east().getBottomCenter());
            work.tick(level, owner, null, context);
            helper.assertTrue(owner.getMainHandItem().is(Items.BOW) && fixture.depot().getItem(0).isEmpty(),
                    "reaching approved storage equips one bow");
            inventory.insertSupply(new ItemStack(Items.ARROW, 3));
            SettlementTestPlots.planSupplies(level, fixture.town());
            work.tick(level, owner, null, context);
            helper.assertValueEqual(HiredRangedAmmo.carriedAmmo(owner), 32,
                    "the configured reserve replenishes before the last arrow is consumed");
            helper.assertValueEqual(fixture.depot().getItem(1).getCount(), 35, "only 29 additional arrows are withdrawn");
            inventory.consumeSupply(HiredRangedAmmo::isAmmo, 7);
            SettlementTestPlots.planSupplies(level, fixture.town());
            work.tick(level, owner, null, context);
            helper.assertValueEqual(fixture.depot().getItem(1).getCount(), 28, "later loaded upkeep only replaces consumed arrows");
            helper.assertValueEqual(HiredRangedAmmo.carriedAmmo(owner), 32, "the target reserve remains bounded");
            inventory.consumeSupply(HiredRangedAmmo::isAmmo, 7);
            fixture.depot().setItem(1, ItemStack.EMPTY);
            fixture.depot().setItem(2, new ItemStack(Items.IRON_HELMET));
            var expanded = new java.util.ArrayList<>(configuration.selections());
            expanded.add(new SettlementEquipmentPlan.Selection(HiredEquipmentNeed.Kind.HELMET, 0, true, false));
            helper.assertTrue(fixture.town().setEquipmentPlan(fixture.town().leader(), area.id(),
                    new SettlementEquipmentPlan.Configuration(expanded, 5, true), fixture.town().equipmentPlans().get(area.id()).revision()),
                    "the loadout can explicitly add optional armor");
            SettlementTestPlots.planSupplies(level, fixture.town());
            work.tick(level, owner, null, context);
            helper.assertTrue(owner.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).is(Items.IRON_HELMET),
                    "an unfilled optional ammunition reserve does not prevent other available equipment");
            helper.assertValueEqual(HiredRangedAmmo.carriedAmmo(owner), 25, "missing reserve stock is never invented");
        } finally {
            owner.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_equipment")
    public static void configuredGuardsCreditAndRetainCompatibleManualEquipment(GameTestHelper helper) {
        var fixture = fixture(helper, SettlementAreaType.GUARD_POST);
        var level = helper.getLevel();
        var draft = SettlementEquipmentPlan.Configuration.draft(List.of(HiredEquipmentNeed.Kind.CROSSBOW,
                HiredEquipmentNeed.Kind.HELMET, HiredEquipmentNeed.Kind.SHIELD));
        helper.assertTrue(fixture.town().setEquipmentPlan(fixture.town().leader(), fixture.worker().area(),
                new SettlementEquipmentPlan.Configuration(draft.selections(), 5, true), -1), "the loadout is configured");
        var helmet = new ItemStack(Items.IRON_HELMET);
        helmet.setDamageValue(helmet.getMaxDamage() - 1);
        helmet.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Kept helmet"));
        com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerEquipment.setInventoryEquipment(
                fixture.owner(), net.minecraft.world.entity.EquipmentSlot.HEAD, helmet.copy());
        com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerEquipment.setInventoryEquipment(
                fixture.owner(), net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(Items.CROSSBOW));
        com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerEquipment.setInventoryEquipment(
                fixture.owner(), net.minecraft.world.entity.EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        com.jvn.villagerretaliation.inventory.VillagerDefensiveLoadoutService.markManualOffhand(fixture.owner(), true);
        com.jvn.villagerretaliation.inventory.VillagerInventoryAccess.addItem(fixture.owner(), new ItemStack(Items.ARROW, 32));
        fixture.worker().rememberPayments(fixture.owner());
        fixture.depot().setItem(0, new ItemStack(Items.NETHERITE_HELMET));
        var rows = SettlementDemandService.inspect(level, fixture.town()).demands().stream()
                .filter(row -> row.consumer().equals(fixture.worker().id())).toList();
        helper.assertValueEqual(matching(rows, helmet).supply().committed(), 1L, "compatible native armor covers its guard exactly once");
        helper.assertValueEqual(matching(rows, new ItemStack(Items.SHIELD)).supply().committed(), 1L,
                "a compatible manual offhand satisfies the configured shield");
        helper.assertValueEqual(matching(rows, new ItemStack(Items.CROSSBOW)).supply().committed(), 1L,
                "a compatible native weapon is not ordered again");
        SettlementTestPlots.planSupplies(level, fixture.town());
        SettlementEquipmentService.tick(level, fixture.town(), fixture.worker(), 1_000, 1);
        helper.assertValueEqual(fixture.worker().status(), "equipment_ready", "a complete manual loadout needs no exchange");
        var owner = fixture.worker().context(level, fixture.town());
        helper.assertTrue(ItemStack.isSameItemSameComponents(owner.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD), helmet),
                "low durability alone never triggers an upgrade");
        helper.assertTrue(fixture.depot().getItem(0).is(Items.NETHERITE_HELMET), "the better spare remains in storage");
        helper.assertTrue(HiredJobInventory.getJobInventory(owner).getItem(0).isEmpty(),
                "retaining personal equipment does not create a second job inventory owner");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_equipment")
    public static void guardEquipmentFiltersApplyToWithdrawalsWithoutRejectingCarriedGear(GameTestHelper helper) {
        var fixture = fixture(helper, SettlementAreaType.GUARD_POST);
        var level = helper.getLevel();
        var configuration = new SettlementEquipmentPlan.Configuration(List.of(
                new SettlementEquipmentPlan.Selection(HiredEquipmentNeed.Kind.HELMET, 0, true, false)), 5, true);
        helper.assertTrue(fixture.town().setEquipmentPlan(fixture.town().leader(), fixture.worker().area(), configuration, -1),
                "the guard's helmet is explicitly configured");
        var filter = new ItemStack(com.jvn.villagerretaliation.item.VillagerRetaliationItems.ITEM_FILTER.get());
        com.jvn.villagerretaliation.item.VillagerItemFilterData.setEntry(filter, 0, new ItemStack(Items.ARROW));
        var inventory = HiredJobInventory.getJobInventory(fixture.owner());
        inventory.setItem(HiredJobInventory.FILTER_SLOT, filter);
        fixture.worker().inventorySnapshot(inventory.captureSnapshot());
        fixture.depot().setItem(0, new ItemStack(Items.IRON_HELMET));
        SettlementTestPlots.planSupplies(level, fixture.town());
        SettlementEquipmentService.tick(level, fixture.town(), fixture.worker(), 1_000, 1);
        helper.assertValueEqual(fixture.worker().status(), "missing_equipment", "the assigned filter rejects depot withdrawals");
        helper.assertFalse(fixture.depot().getItem(0).isEmpty(), "rejected equipment remains in storage");
        inventory.setItem(HiredJobInventory.HOTBAR_START, new ItemStack(Items.IRON_HELMET));
        fixture.worker().inventorySnapshot(inventory.captureSnapshot());
        fixture.worker().changed();
        SettlementEquipmentService.tick(level, fixture.town(), fixture.worker(), 1_000, 1);
        helper.assertTrue(fixture.worker().context(level, fixture.town()).getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD)
                .is(Items.IRON_HELMET), "the same filter does not reject equipment the guard already carries");
        helper.assertFalse(fixture.depot().getItem(0).isEmpty(), "using carried equipment cannot withdraw another copy");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_equipment")
    public static void configuredHunterRefillsPartialAmmunitionThroughItsNativeSupplyPath(GameTestHelper helper) {
        var fixture = fixture(helper, SettlementAreaType.HUNTING_GROUNDS, true);
        var level = helper.getLevel();
        var owner = fixture.owner();
        var area = fixture.town().areas().get(fixture.worker().area());
        var configuration = new SettlementEquipmentPlan.Configuration(List.of(
                new SettlementEquipmentPlan.Selection(HiredEquipmentNeed.Kind.AMMUNITION, 32, false, false)), 5, true);
        helper.assertTrue(fixture.town().setEquipmentPlan(fixture.town().leader(), area.id(), configuration, -1),
                "the hunter's ammunition reserve is explicitly configured");
        SettlementTestPlots.activateDepot(level, fixture.town(), fixture.depot());
        fixture.depot().setItem(0, new ItemStack(Items.ARROW, 40));
        owner.setPos(fixture.depot().position().east().getBottomCenter());
        level.setBlockAndUpdate(owner.blockPosition().below(), Blocks.STONE.defaultBlockState());
        var inventory = HiredJobInventory.getJobInventory(owner);
        inventory.setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.BOW));
        inventory.insertSupply(new ItemStack(Items.ARROW, 3));
        com.jvn.villagerretaliation.inventory.VillagerInventoryAccess.addItem(owner, new ItemStack(Items.ARROW, 5));
        var context = new com.jvn.villagerretaliation.interaction.work.HiredWorkContext(inventory, HiredWorkStateStore.state(owner),
                area.center(), area.min(), area.max(), 12, 6, true, 100, false, true);
        try {
            SettlementDepotService.synchronizeStorage(level, owner, fixture.town());
            SettlementTestPlots.planSupplies(level, fixture.town());
            var result = HiredRangedAmmo.ensureReady(level, owner, context, 1);
            helper.assertTrue(result != null && result.progressed(), "native hunting supply work tops up a partially filled reserve");
            helper.assertValueEqual(HiredRangedAmmo.carriedAmmo(owner), 32, "the shared reserve counts both personal and job ammunition");
            helper.assertValueEqual(fixture.depot().getItem(0).getCount(), 16, "only the missing 24 arrows are acquired");
        } finally {
            owner.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_equipment")
    public static void unconfiguredBackgroundGuardsKeepNativeEmptyAmmoRestocking(GameTestHelper helper) {
        var fixture = fixture(helper, SettlementAreaType.GUARD_POST);
        var level = helper.getLevel();
        var inventory = HiredJobInventory.getJobInventory(fixture.owner());
        inventory.setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.BOW));
        fixture.worker().inventorySnapshot(inventory.captureSnapshot());
        fixture.worker().rememberPayments(fixture.owner());
        fixture.depot().setItem(0, new ItemStack(Items.ARROW, 64));
        SettlementTestPlots.planSupplies(level, fixture.town());
        SettlementEquipmentService.tick(level, fixture.town(), fixture.worker(), 1_000, 1);
        var owner = fixture.worker().context(level, fixture.town());
        helper.assertValueEqual(HiredRangedAmmo.carriedAmmo(owner), HiredRangedAmmo.RESTOCK_COUNT,
                "a guard without a reserve plan still uses the native empty-ammo restock target");
        inventory = HiredJobInventory.getJobInventory(owner);
        inventory.consumeSupply(HiredRangedAmmo::isAmmo, 1);
        fixture.worker().inventorySnapshot(inventory.captureSnapshot());
        fixture.worker().changed();
        fixture.depot().setItem(0, new ItemStack(Items.ARROW, 32));
        SettlementTestPlots.planSupplies(level, fixture.town());
        SettlementEquipmentService.tick(level, fixture.town(), fixture.worker(), 1_000, 1);
        helper.assertValueEqual(fixture.depot().getItem(0).getCount(), 32,
                "unconfigured upkeep does not proactively refill a partial reserve");
        helper.assertValueEqual(HiredRangedAmmo.carriedAmmo(fixture.worker().context(level, fixture.town())), 63,
                "existing default ammunition behavior remains distinct from configured reserves");
        helper.succeed();
    }


    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_equipment")
    public static void ammunitionPoliciesProtectOneRecipeBatchAndRetainIncomingOwnership(GameTestHelper helper) {
        var fixture = rangedSupplyFixture(helper);
        var level = helper.getLevel();
        var town = fixture.town();
        var workshop = equipmentWorkshop(helper, fixture, fixture.depot().position());
        var step = equipmentProduction(workshop, "arrow", fixture.depot().position());
        var target = matching(SettlementDemandService.inspect(level, town).demands().stream()
                .filter(row -> row.consumer().equals(fixture.worker().id())).toList(), new ItemStack(Items.ARROW));
        var policy = equipmentPolicy(helper, fixture, target, List.of(step),
                List.of(new SettlementSupplyPolicy.Connection(step.id(), null, target.supply().id())));
        helper.assertValueEqual(SettlementPolicyOrders.reconcile(level, town, policy, step, 32), "", "the arrow reserve uses native production");
        var binding = SettlementPolicyOrders.current(policy, step);
        var queue = town.productionQueues().get(workshop.id());
        var ladder = com.jvn.villagerretaliation.interaction.work.production.HiredProductionOrder.create(level,
                equipmentProduction(workshop, "ladder", fixture.depot().position()).recipe(),
                com.jvn.villagerretaliation.interaction.work.production.HiredProductionOrder.Mode.BATCH, 3, fixture.depot().position());
        helper.assertTrue(queue.append(ladder), "a higher-priority manual order competes for sticks");
        fixture.depot().setItem(0, new ItemStack(Items.STICK, 8));
        fixture.depot().setItem(1, new ItemStack(Items.FLINT, 8));
        fixture.depot().setItem(2, new ItemStack(Items.FEATHER, 8));
        var snapshot = SettlementDemandService.inspect(level, town);
        var arrows = productionInput(snapshot, binding.order(), Items.STICK);
        helper.assertValueEqual(arrows.requirement().quantity(), 8L, "32 arrows require eight native recipe batches");
        helper.assertValueEqual(arrows.supply().protectedMinimum(), 1L, "only the first batch needed to resume work is essential");
        helper.assertValueEqual(arrows.supply().priority(), 2, "the configured loadout priority reaches its producer");
        helper.assertValueEqual(arrows.supply().availableNow(), 1L, "the essential batch wins before higher-priority optional work");
        helper.assertValueEqual(productionInput(snapshot, ladder.id(), Items.STICK).supply().availableNow(), 7L,
                "the remaining sticks follow player priority rather than protecting the whole ammunition reserve");

        var allocated = queue.reserve(UUID.randomUUID(), binding.order(), 4, 4, 0, null);
        helper.assertTrue(allocated != null, "a native unstarted arrow batch is a real incoming commitment");
        snapshot = SettlementDemandService.inspect(level, town);
        helper.assertValueEqual(productionInput(snapshot, binding.order(), Items.STICK).supply().protectedMinimum(), 1L,
                "the producer retains essential inputs for its own promised ammunition");
        helper.assertTrue(queue.start(allocated.id()), "the native batch can finish after its materials have been collected");
        helper.assertValueEqual(productionInput(SettlementDemandService.inspect(level, town), binding.order(), Items.STICK)
                .supply().protectedMinimum(), 0L, "already-started output covers the essential need without protecting extra reserve batches");
        helper.assertTrue(queue.allocations().stream().anyMatch(value -> value.id().equals(allocated.id()) && value.started()),
                "changing inherited protection never confiscates a started receipt");
        queue.release(allocated.id());
        fixture.depot().setItem(3, new ItemStack(Items.ARROW));
        snapshot = snapshot.refresh();
        helper.assertValueEqual(productionInput(snapshot, binding.order(), Items.STICK).supply().protectedMinimum(), 0L,
                "real ammunition arriving before acquisition removes inherited protection immediately");
        fixture.depot().setItem(3, ItemStack.EMPTY);
        var manual = com.jvn.villagerretaliation.interaction.work.production.HiredProductionOrder.create(level, step.recipe(),
                com.jvn.villagerretaliation.interaction.work.production.HiredProductionOrder.Mode.BATCH, 4, fixture.depot().position());
        queue.append(manual);
        helper.assertTrue(queue.reserve(UUID.randomUUID(), manual.id(), 4, 4, 0, null) != null, "independent production promises the first arrows");
        helper.assertValueEqual(productionInput(SettlementDemandService.inspect(level, town), binding.order(), Items.STICK)
                .supply().protectedMinimum(), 0L, "another real incoming batch covers the immediate shortage without protecting duplicate work");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_equipment")
    public static void equipmentUrgencyPassesThroughDeliveryAndIntermediateRecipes(GameTestHelper helper) {
        var fixture = rangedSupplyFixture(helper);
        var level = helper.getLevel();
        var town = fixture.town();
        var sourceArea = new SettlementArea(UUID.randomUUID(), SettlementAreaType.DEPOT, "Workshop stores",
                fixture.depot().position().south(8), fixture.depot().position().offset(3, 2, 11), 5, true);
        town.putArea(town.leader(), sourceArea);
        var source = town.registerDepot(town.leader(), sourceArea.id(), sourceArea.min(), SettlementDepotPurpose.GENERAL);
        source.observeRules(List.of());
        equipmentStorage(town, fixture.worker().area(), fixture.depot().position());
        town.setWorksitePlan(town.leader(), fixture.depot().area(), SettlementWorksitePlan.defaults(HiredVillagerRole.COURIER));
        var workshop = equipmentWorkshop(helper, fixture, source.position());
        var rawArea = new SettlementArea(UUID.randomUUID(), SettlementAreaType.DEPOT, "Timber stores",
                fixture.depot().position().offset(5, 0, 8), fixture.depot().position().offset(8, 2, 11), 5, true);
        town.putArea(town.leader(), rawArea);
        var raw = town.registerDepot(town.leader(), rawArea.id(), rawArea.min(), SettlementDepotPurpose.GENERAL);
        raw.observeRules(List.of());
        var timberWorkshop = equipmentWorkshop(helper, fixture, raw.position(), 8);
        var timberPlan = town.worksitePlans().get(timberWorkshop.id());
        town.setWorksitePlan(town.leader(), timberWorkshop.id(), new SettlementWorksitePlan(timberPlan.settings().withStorage(
                new SettlementStorageLinks(false, List.of(
                        new SettlementStorageLinks.Link(raw.position(), SettlementStorageLinks.Purpose.GENERAL, 5),
                        new SettlementStorageLinks.Link(source.position(), SettlementStorageLinks.Purpose.OUTPUT, 5)))), 1));
        var arrows = equipmentProduction(workshop, "arrow", source.position());
        var sticks = equipmentProduction(timberWorkshop, "stick", source.position());
        var delivery = new SettlementSupplyPolicy.Step(UUID.randomUUID(), SettlementSupplyPolicy.StepKind.DELIVERY, fixture.depot().area(),
                null, new ItemStack(Items.ARROW), source.position(), fixture.depot().position());
        var target = matching(SettlementDemandService.inspect(level, town).demands().stream()
                .filter(row -> row.consumer().equals(fixture.worker().id())).toList(), new ItemStack(Items.ARROW));
        helper.assertValueEqual(target.sources(), List.of(fixture.depot().position()),
                "explicitly approved unloaded depots remain the guard's only supply source");
        var arrowInput = SettlementPolicyPlanner.inputs(level, town, arrows, 4).stream()
                .filter(input -> input.requirement().value(new ItemStack(Items.STICK)) > 0).findFirst().orElseThrow();
        var deliveryInput = SettlementPolicyPlanner.inputs(level, town, delivery, 1).getFirst();
        var policy = equipmentPolicy(helper, fixture, target, List.of(sticks, arrows, delivery), List.of(
                new SettlementSupplyPolicy.Connection(sticks.id(), arrows.id(), arrowInput.id()),
                new SettlementSupplyPolicy.Connection(arrows.id(), delivery.id(), deliveryInput.id()),
                new SettlementSupplyPolicy.Connection(delivery.id(), null, target.supply().id())));
        for (var step : List.of(delivery, arrows, sticks))
            helper.assertValueEqual(SettlementPolicyOrders.reconcile(level, town, policy, step, step == sticks ? 8 : 32), "",
                    "each configured step uses an independent native binding");
        var arrowOrder = SettlementPolicyOrders.current(policy, arrows).order();
        var stickOrder = SettlementPolicyOrders.current(policy, sticks).order();
        var transferOrder = SettlementPolicyOrders.current(policy, delivery).order();
        raw.setItem(0, new ItemStack(Items.OAK_PLANKS, 5));
        source.setItem(1, new ItemStack(Items.FLINT, 8));
        source.setItem(2, new ItemStack(Items.FEATHER, 8));
        var slabs = com.jvn.villagerretaliation.interaction.work.production.HiredProductionOrder.create(level,
                equipmentProduction(timberWorkshop, "oak_slab", raw.position()).recipe(),
                com.jvn.villagerretaliation.interaction.work.production.HiredProductionOrder.Mode.BATCH, 6, raw.position());
        town.productionQueues().get(timberWorkshop.id()).append(slabs);
        var snapshot = SettlementDemandService.inspect(level, town);
        helper.assertValueEqual(productionInput(snapshot, transferOrder, Items.ARROW).supply().protectedMinimum(), 1L,
                "the approved courier protects only the first required projectile at its source");
        helper.assertValueEqual(productionInput(snapshot, arrowOrder, Items.STICK).supply().protectedMinimum(), 1L,
                "delivery urgency reaches ammunition production");
        helper.assertValueEqual(productionInput(snapshot, stickOrder, Items.OAK_PLANKS).supply().protectedMinimum(), 2L,
                "the intermediate recipe rounds the essential stick to one native batch");
        helper.assertValueEqual(productionInput(snapshot, stickOrder, Items.OAK_PLANKS).supply().availableNow(), 2L,
                "essential intermediate materials survive competition");
        helper.assertValueEqual(productionInput(snapshot, slabs.id(), Items.OAK_PLANKS).supply().availableNow(), 3L,
                "optional production receives the remaining materials");

        source.setItem(3, new ItemStack(Items.ARROW));
        snapshot = snapshot.refresh();
        var courier = UUID.randomUUID();
        town.assignDuty(courier, fixture.depot().area());
        SettlementTestPlots.planSupplies(level, town);
        helper.assertValueEqual(SettlementAllocationService.inspect(level, town, courier, null,
                source.position(), fixture.depot().position(), new ItemStack(Items.ARROW)).allowance(), 1,
                "a loaded courier can acquire its protected delivery before reserving cargo");
        helper.assertValueEqual(SettlementAllocationService.inspect(level, town, UUID.randomUUID(), null,
                source.position(), new ItemStack(Items.ARROW)).allowance(), 0,
                "unrelated workers cannot take the projectile reserved for delivery");
        helper.assertValueEqual(productionInput(snapshot, arrowOrder, Items.STICK).supply().protectedMinimum(), 0L,
                "an arrow awaiting approved delivery needs no further protected production");
        helper.assertValueEqual(productionInput(snapshot, stickOrder, Items.OAK_PLANKS).supply().protectedMinimum(), 0L,
                "fulfilled downstream supply releases protection throughout the chain");
        source.setItem(3, new ItemStack(Items.STICK));
        snapshot = snapshot.refresh();
        helper.assertValueEqual(productionInput(snapshot, arrowOrder, Items.STICK).supply().protectedMinimum(), 1L,
                "the available intermediate remains assigned to its essential consumer");
        helper.assertValueEqual(productionInput(snapshot, stickOrder, Items.OAK_PLANKS).supply().protectedMinimum(), 0L,
                "available intermediate stock prevents redundant upstream protection");
        fixture.depot().setItem(0, new ItemStack(Items.ARROW, 32));
        snapshot = snapshot.refresh();
        helper.assertTrue(snapshot.demands().stream().noneMatch(row -> row.consumer().equals(transferOrder)),
                "stock arriving at the destination removes stale courier input demand before acquisition");
        fixture.depot().setItem(0, ItemStack.EMPTY);
        helper.assertValueEqual(productionInput(snapshot.refresh(), transferOrder, Items.ARROW).supply().protectedMinimum(), 1L,
                "consumption restores the same native delivery requirement");
        var restored = SettlementRecord.load(town.save(level.registryAccess()), level.registryAccess(), () -> {});
        helper.assertValueEqual(productionInput(SettlementDemandService.inspect(level, restored), transferOrder, Items.ARROW)
                .supply().protectedMinimum(), 1L, "reload derives the same urgency from native bindings and stock");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_equipment")
    public static void optionalArmorProductionOnlyBecomesEssentialWhenExplicitlyProtected(GameTestHelper helper) {
        var fixture = fixture(helper, SettlementAreaType.GUARD_POST);
        var level = helper.getLevel();
        var town = fixture.town();
        var area = fixture.worker().area();
        var optional = new SettlementEquipmentPlan.Configuration(List.of(
                new SettlementEquipmentPlan.Selection(HiredEquipmentNeed.Kind.HELMET, 1, true, false)), 6, true);
        town.setEquipmentPlan(town.leader(), area, optional, -1);
        var workshop = equipmentWorkshop(helper, fixture, fixture.depot().position());
        var step = equipmentProduction(workshop, "iron_helmet", fixture.depot().position());
        var target = matching(SettlementDemandService.inspect(level, town).demands().stream()
                .filter(row -> row.consumer().equals(fixture.worker().id())).toList(), new ItemStack(Items.IRON_HELMET));
        var policy = equipmentPolicy(helper, fixture, target, List.of(step),
                List.of(new SettlementSupplyPolicy.Connection(step.id(), null, target.supply().id())));
        SettlementPolicyOrders.reconcile(level, town, policy, step, 1);
        var order = SettlementPolicyOrders.current(policy, step).order();
        var snapshot = SettlementDemandService.inspect(level, town);
        helper.assertValueEqual(productionInput(snapshot, order, Items.IRON_INGOT).supply().protectedMinimum(), 0L,
                "optional armor does not become essential merely because it has a supply policy");
        helper.assertValueEqual(productionInput(snapshot, order, Items.IRON_INGOT).supply().priority(), 6,
                "optional armor still inherits the configured equipment priority");
        var queue = town.productionQueues().get(workshop.id());
        var manual = com.jvn.villagerretaliation.interaction.work.production.HiredProductionOrder.create(level,
                equipmentProduction(workshop, "iron_block", fixture.depot().position()).recipe(),
                com.jvn.villagerretaliation.interaction.work.production.HiredProductionOrder.Mode.BATCH, 2, fixture.depot().position());
        queue.append(manual);
        var started = queue.reserve(UUID.randomUUID(), manual.id(), 1, 1, 0, null);
        queue.start(started.id());
        var waiting = queue.reserve(UUID.randomUUID(), manual.id(), 1, 1, 0, null);
        SettlementTestPlots.planSupplies(level, town);
        town.setEquipmentPlan(town.leader(), area, new SettlementEquipmentPlan.Configuration(List.of(
                new SettlementEquipmentPlan.Selection(HiredEquipmentNeed.Kind.HELMET, 1, true, true)), 6, true),
                town.equipmentPlans().get(area).revision());
        SettlementTestPlots.planSupplies(level, town);
        helper.assertTrue(queue.allocations().stream().noneMatch(value -> value.id().equals(waiting.id())),
                "changed equipment protection releases unstarted competing allocations");
        helper.assertTrue(queue.allocations().stream().anyMatch(value -> value.id().equals(started.id()) && value.started()),
                "changed reserve settings preserve durable started receipts");
        snapshot = snapshot.refresh();
        helper.assertValueEqual(productionInput(snapshot, order, Items.IRON_INGOT).supply().protectedMinimum(), 5L,
                "explicit protection reaches the exact native armor recipe");
        policy.state(SettlementSupplyPolicy.State.PAUSED, town.leader(), "", level.getGameTime());
        helper.assertValueEqual(productionInput(snapshot.refresh(), order, Items.IRON_INGOT).supply().protectedMinimum(), 0L,
                "a paused policy immediately loses inherited acquisition protection");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_equipment")
    public static void equipmentPoliciesProtectTheNativeCraftingInputsActuallySelected(GameTestHelper helper) {
        var fixture = rangedSupplyFixture(helper);
        var level = helper.getLevel();
        var town = fixture.town();
        var workshop = equipmentWorkshop(helper, fixture, fixture.depot().position());
        var step = equipmentProduction(workshop, "arrow", fixture.depot().position());
        var target = matching(SettlementDemandService.inspect(level, town).demands(), new ItemStack(Items.ARROW));
        var policy = equipmentPolicy(helper, fixture, target, List.of(step),
                List.of(new SettlementSupplyPolicy.Connection(step.id(), null, target.supply().id())));
        SettlementPolicyOrders.reconcile(level, town, policy, step, 32);
        var order = SettlementPolicyOrders.current(policy, step).order();
        fixture.depot().setItem(0, new ItemStack(Items.OAK_PLANKS, 5));
        fixture.depot().setItem(1, new ItemStack(Items.FLINT, 8));
        fixture.depot().setItem(2, new ItemStack(Items.FEATHER, 8));
        var slabs = com.jvn.villagerretaliation.interaction.work.production.HiredProductionOrder.create(level,
                equipmentProduction(workshop, "oak_slab", fixture.depot().position()).recipe(),
                com.jvn.villagerretaliation.interaction.work.production.HiredProductionOrder.Mode.BATCH, 6, fixture.depot().position());
        town.productionQueues().get(workshop.id()).append(slabs);
        var snapshot = SettlementDemandService.inspect(level, town);
        var input = productionInput(snapshot, order, Items.OAK_PLANKS);
        helper.assertValueEqual(input.supply().protectedMinimum(), 2L,
                "native stick crafting protects its real plank batch rather than an unavailable direct ingredient");
        helper.assertValueEqual(input.supply().availableNow(), 2L, "the essential native input wins before optional production");
        helper.assertValueEqual(productionInput(snapshot, slabs.id(), Items.OAK_PLANKS).supply().availableNow(), 3L,
                "native subcrafting does not protect the entire ammunition reserve");
        helper.succeed();
    }

    private static Fixture rangedSupplyFixture(GameTestHelper helper) {
        var fixture = fixture(helper, SettlementAreaType.GUARD_POST);
        var inventory = HiredJobInventory.getJobInventory(fixture.owner());
        inventory.setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.BOW));
        fixture.worker().inventorySnapshot(inventory.captureSnapshot());
        fixture.worker().rememberPayments(fixture.owner());
        fixture.town().setEquipmentPlan(fixture.town().leader(), fixture.worker().area(), new SettlementEquipmentPlan.Configuration(List.of(
                new SettlementEquipmentPlan.Selection(HiredEquipmentNeed.Kind.AMMUNITION, 32, false, false)), 2, true), -1);
        return fixture;
    }

    private static SettlementArea equipmentWorkshop(GameTestHelper helper, Fixture fixture, BlockPos storage) {
        return equipmentWorkshop(helper, fixture, storage, 0);
    }

    private static SettlementArea equipmentWorkshop(GameTestHelper helper, Fixture fixture, BlockPos storage, int south) {
        var position = fixture.depot().position().offset(10, 0, south);
        var workshop = new SettlementArea(UUID.randomUUID(), SettlementAreaType.WORKSHOP, "Equipment workshop",
                position, position.offset(3, 2, 3), 9, true);
        fixture.town().putArea(fixture.town().leader(), workshop);
        equipmentStorage(fixture.town(), workshop.id(), storage);
        fixture.town().recordSurvey(workshop.id(), new SettlementBuildingSurvey.Survey(0, List.of(), List.of(position), true,
                java.util.Map.of(position, net.minecraft.resources.ResourceLocation.withDefaultNamespace("crafting_table"))));
        return workshop;
    }

    private static void equipmentStorage(SettlementRecord town, UUID areaId, BlockPos storage) {
        var area = town.areas().get(areaId);
        var defaults = SettlementWorksitePlan.defaults(area.type().role());
        var links = new SettlementStorageLinks(false, List.of(new SettlementStorageLinks.Link(storage, SettlementStorageLinks.Purpose.GENERAL, 5)));
        town.setWorksitePlan(town.leader(), areaId, new SettlementWorksitePlan(defaults.settings().withStorage(links), 1));
    }

    private static SettlementSupplyPolicy.Step equipmentProduction(SettlementArea area, String recipe, BlockPos destination) {
        return new SettlementSupplyPolicy.Step(UUID.randomUUID(), SettlementSupplyPolicy.StepKind.PRODUCTION, area.id(),
                com.jvn.villagerretaliation.interaction.work.production.HiredProductionRecipe.exact(
                        net.minecraft.resources.ResourceLocation.withDefaultNamespace(recipe), java.util.Map.of()), ItemStack.EMPTY, null, destination);
    }

    private static SettlementSupplyPolicy equipmentPolicy(GameTestHelper helper, Fixture fixture, SettlementDemandService.Demand target,
                                                          List<SettlementSupplyPolicy.Step> steps, List<SettlementSupplyPolicy.Connection> edges) {
        var selection = new SettlementSupplyPolicy.Target(SettlementSupplyPolicy.TargetKind.EQUIPMENT, target.consumer(),
                target.supply().id(), null, ItemStack.EMPTY, 0);
        var policy = new SettlementSupplyPolicy(UUID.randomUUID(), new SettlementSupplyPolicy.Configuration("Equipment chain", selection, 0,
                steps, edges), fixture.town().leader(), fixture.town()::supplyChanged);
        fixture.town().addSupplyPolicy(policy);
        policy.state(SettlementSupplyPolicy.State.ACTIVE, fixture.town().leader(), "", helper.getLevel().getGameTime());
        return policy;
    }

    private static SettlementDemandService.Demand productionInput(SettlementDemandService.Snapshot snapshot, UUID order,
                                                                  net.minecraft.world.item.Item item) {
        return snapshot.demands().stream().filter(row -> row.consumer().equals(order) && row.requirement().value(new ItemStack(item)) > 0)
                .findFirst().orElseThrow(() -> new AssertionError("Missing " + item + " input for " + order + ": "
                        + snapshot.demands().stream().map(row -> row.consumer() + "/" + row.requirement().key() + "/"
                                + row.requirement().quantity() + "/" + row.blocker()).toList()));
    }

    private static SettlementAllocationService.Decision ready(GameTestHelper helper, Fixture fixture, ItemStack sample) {
        SettlementTestPlots.planSupplies(helper.getLevel(), fixture.town());
        var decision = SettlementAllocationService.inspect(helper.getLevel(), fixture.town(), fixture.worker().id(), null,
                fixture.depot().position(), sample);
        helper.assertFalse(decision.blocker().equals("allocation_pending"), "the fixture's allocation work completed");
        return decision;
    }

    private static Fixture fixture(GameTestHelper helper, SettlementAreaType type) {
        return fixture(helper, type, false);
    }

    private static Fixture fixture(GameTestHelper helper, SettlementAreaType type, boolean active) {
        var level = helper.getLevel();
        var origin = helper.absolutePos(BlockPos.ZERO);
        var center = active ? helper.absolutePos(new BlockPos(1, 2, 1))
                : new BlockPos(24_000_000 + origin.getX() * 8, 64, 24_000_000 + origin.getZ() * 8);
        var registry = com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData.get(level);
        var id = registry.create(level.getGameTime(), level.dimension().location(), center, "Equipment test");
        var town = SettlementSavedData.get(level).establish(registry, id, UUID.randomUUID(), center, level.getGameTime()).orElseThrow();
        SettlementTestPlots.claim(level, town, center, center.offset(15, 3, 12));
        var storage = new SettlementArea(UUID.randomUUID(), SettlementAreaType.DEPOT, "Stores", center, center.offset(3, 2, 3), 5, true);
        helper.assertTrue(town.putArea(town.leader(), storage), "equipment storage is registered");
        var depot = town.registerDepot(town.leader(), storage.id(), center, SettlementDepotPurpose.GENERAL);
        depot.observeRules(List.of());
        var workplace = new SettlementArea(UUID.randomUUID(), type, "Equipment work", center.east(5), center.offset(9, 2, 4), 1, true);
        helper.assertTrue(town.putArea(town.leader(), workplace), "the job requiring equipment is registered");
        town.setWorksitePlan(town.leader(), workplace.id(), SettlementWorksitePlan.defaults(type.role()));
        var villager = EntityType.VILLAGER.create(level);
        villager.setPos(workplace.min().getCenter());
        town.assignDuty(villager.getUUID(), workplace.id());
        var worker = SettlementWorkerState.capture(level, villager, new SettlementWorkforceService.Duty(town, workplace), 0);
        town.recordWorker(worker);
        if (active) {
            villager.setNoAi(true);
            villager.setOnGround(true);
            villager.setVillagerData(villager.getVillagerData().setProfession(type == SettlementAreaType.HUNTING_GROUNDS
                    ? net.minecraft.world.entity.npc.VillagerProfession.LEATHERWORKER : net.minecraft.world.entity.npc.VillagerProfession.WEAPONSMITH));
            com.jvn.villagerretaliation.allegiance.VillageAllegianceApi.assignKnown(level, villager, id,
                    com.jvn.villagerretaliation.allegiance.AllegianceAssignmentSource.EXPLICIT_API);
            helper.assertTrue(level.addFreshEntity(villager), "the guard enters the active world");
            var leader = net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,
                    new com.mojang.authlib.GameProfile(town.leader(), "EquipmentLeader"));
            helper.assertTrue(SettlementWorkforceService.assign(level, leader, id, villager, workplace.id()),
                    "the active guard receives canonical civic authority");
        }
        return new Fixture(town, depot, worker, villager);
    }

    private record Fixture(SettlementRecord town, SettlementDepot depot, SettlementWorkerState worker, net.minecraft.world.entity.npc.Villager owner) { }
}
