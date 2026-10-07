package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.AllegianceAssignmentSource;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceApi;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.HiredWorkStateStore;
import com.jvn.villagerretaliation.interaction.VillagerAssignmentStore;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.jvn.villagerretaliation.mixin.VillagerFamilyAccessor;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("villager_life")
@PrefixGameTestTemplate(false)
public final class SettlementFamilyFoodGameTests {
    private SettlementFamilyFoodGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_family_food")
    public static void unloadedParentsReceiveFiniteProvisionsOnceAndCarryThemBack(GameTestHelper helper) {
        var fixture = SettlementPopulationGameTests.fixture(helper);
        var parent = parent(helper, fixture);
        var stale = parent.saveWithoutId(new CompoundTag());
        SettlementPopulationService.provideFamilyFood(helper.getLevel(), fixture.town());
        helper.assertValueEqual(stock(fixture), 61, "an absent parent receives three actual depot bread");
        helper.assertValueEqual(parent.getInventory().countItem(Items.BREAD), 0, "background provision does not mutate the old native body");
        var state = savedState(helper, fixture, parent);
        var returning = EntityType.VILLAGER.create(helper.getLevel());
        returning.load(stale);
        helper.assertTrue(state.restore(returning), "family food has its own pending handoff without a care interval");
        helper.assertValueEqual(returning.getInventory().countItem(Items.BREAD), 3, "the saved native inventory receives exactly the withdrawn bread");
        helper.assertTrue(returning.canBreed(), "the real native breeding food check sees the supplied items");
        helper.assertFalse(state.restore(returning), "the same inventory handoff cannot replay");
        SettlementPopulationService.provideFamilyFood(helper.getLevel(), fixture.town());
        helper.assertValueEqual(stock(fixture), 61, "repeated background provision does not withdraw another meal");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_family_food")
    public static void nativeBreedingConsumptionCannotReturnFromAnOlderParentSnapshot(GameTestHelper helper) {
        var fixture = SettlementPopulationGameTests.fixture(helper);
        var parent = parent(helper, fixture);
        SettlementPopulationService.provideFamilyFood(helper.getLevel(), fixture.town());
        var state = fixture.town().residents().get(parent.getUUID());
        var context = state.context(helper.getLevel());
        var beforeBirth = context.saveWithoutId(new CompoundTag());
        context.eatAndDigestFood();
        state.rememberFamily(context);
        var restored = savedState(helper, fixture, parent);
        var returning = EntityType.VILLAGER.create(helper.getLevel());
        returning.load(beforeBirth);
        helper.assertTrue(restored.restore(returning), "consuming family food creates a newer independent inventory revision");
        helper.assertValueEqual(returning.getInventory().countItem(Items.BREAD), 0, "the earlier parent cannot restore consumed bread");
        helper.assertValueEqual(SettlementPopulationService.familyFood(returning), 0, "the native digested food reserve was consumed too");
        returning.getInventory().setItem(4, new ItemStack(Items.EMERALD, 7));
        helper.assertFalse(restored.restore(returning), "later native inventory changes remain authoritative after acknowledgment");
        helper.assertValueEqual(returning.getInventory().getItem(4).getCount(), 7, "repeated reconciliation preserves later native goods");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_family_food")
    public static void provisionCountsTheAlreadyDigestedNativeFoodReserve(GameTestHelper helper) {
        var fixture = SettlementPopulationGameTests.fixture(helper);
        var parent = parent(helper, fixture);
        ((VillagerFamilyAccessor) parent).villagerretaliation$setFamilyFood(8);
        parent.getInventory().setItem(3, new ItemStack(Items.CARROT, 2));
        fixture.town().residents().get(parent.getUUID()).rememberFamily(parent);
        SettlementPopulationService.provideFamilyFood(helper.getLevel(), fixture.town());
        var context = fixture.town().residents().get(parent.getUUID()).context(helper.getLevel());
        helper.assertValueEqual(stock(fixture), 63, "only one bread is needed when native food and carrots already supply ten points");
        helper.assertValueEqual(SettlementPopulationService.familyFood(context), 14, "the native reserve and real items are both counted");
        context.eatAndDigestFood();
        helper.assertValueEqual(SettlementPopulationService.familyFood(context), 2, "native digestion consumes exactly twelve points including existing food");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_family_food")
    public static void metabolicCareDoesNotOverwriteAcknowledgedNativeInventory(GameTestHelper helper) {
        var fixture = SettlementPopulationGameTests.fixture(helper);
        var parent = parent(helper, fixture);
        var state = fixture.town().residents().get(parent.getUUID());
        parent.getInventory().setItem(5, new ItemStack(Items.CARROT, 9));
        ((VillagerFamilyAccessor) parent).villagerretaliation$setFamilyFood(4);
        state.advance(helper.getLevel(), fixture.town(), state.checkedAt() + 200L);
        helper.assertTrue(state.restore(parent), "metabolic care advances independently");
        helper.assertValueEqual(parent.getInventory().getItem(5).getCount(), 9, "a care-only revision preserves native inventory changes");
        helper.assertValueEqual(((VillagerFamilyAccessor) parent).villagerretaliation$getFamilyFood(), 4, "care does not reset the native breeding reserve");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_family_food")
    public static void familyHandoffsPreserveAllNativeSlotsAndItemComponents(GameTestHelper helper) {
        var fixture = SettlementPopulationGameTests.fixture(helper);
        var parent = parent(helper, fixture);
        var state = fixture.town().residents().get(parent.getUUID());
        var goods = new ItemStack(Items.EMERALD, 7);
        goods.set(DataComponents.CUSTOM_NAME, Component.literal("Saved keepsake"));
        parent.getInventory().setItem(7, goods.copy());
        state.rememberFamily(parent);
        var stale = parent.saveWithoutId(new CompoundTag());
        SettlementPopulationService.provideFamilyFood(helper.getLevel(), fixture.town());
        var returning = EntityType.VILLAGER.create(helper.getLevel());
        returning.load(stale);
        savedState(helper, fixture, parent).restore(returning);
        helper.assertTrue(ItemStack.matches(returning.getInventory().getItem(7), goods), "the exact native slot, count, and item components survive");
        helper.assertValueEqual(returning.getInventory().countItem(Items.BREAD), 3, "food uses available capacity alongside existing goods");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_family_food")
    public static void fullNativeInventoriesDoNotConsumeTownProvisions(GameTestHelper helper) {
        var fixture = SettlementPopulationGameTests.fixture(helper);
        var parent = parent(helper, fixture);
        for (int slot = 0; slot < parent.getInventory().getContainerSize(); slot++) parent.getInventory().setItem(slot, new ItemStack(Items.EMERALD, 64));
        fixture.town().residents().get(parent.getUUID()).rememberFamily(parent);
        SettlementPopulationService.provideFamilyFood(helper.getLevel(), fixture.town());
        helper.assertValueEqual(stock(fixture), 64, "full native inventories leave every depot item in storage");
        helper.assertValueEqual(fixture.town().residents().get(parent.getUUID()).context(helper.getLevel()).getInventory().countItem(Items.EMERALD),
                512, "background family provision never removes unrelated native goods");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_family_food")
    public static void legacyCareDoesNotInventNativeInventoryAuthority(GameTestHelper helper) {
        var fixture = SettlementPopulationGameTests.fixture(helper);
        var parent = parent(helper, fixture);
        var tag = fixture.town().residents().get(parent.getUUID()).save();
        tag.remove("FamilyInventory");
        var legacy = SettlementResidentState.load(tag);
        parent.getInventory().setItem(2, new ItemStack(Items.BREAD, 5));
        legacy.advance(helper.getLevel(), fixture.town(), legacy.checkedAt() + 200L);
        legacy.restore(parent);
        helper.assertValueEqual(parent.getInventory().getItem(2).getCount(), 5, "legacy metabolism does not overwrite the native inventory");
        tag.remove("EntityContext");
        fixture.town().recordResident(SettlementResidentState.load(tag));
        SettlementPopulationService.provideFamilyFood(helper.getLevel(), fixture.town());
        helper.assertValueEqual(stock(fixture), 64, "a legacy resident without native context cannot manufacture an inventory");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_family_food")
    public static void removedCensusMembersAndForeignLiveIdentitiesCannotReceiveProvisions(GameTestHelper helper) {
        var fixture = SettlementPopulationGameTests.fixture(helper);
        var parent = parent(helper, fixture);
        var registry = VillageAllegianceRegistrySavedData.get(helper.getLevel());
        registry.removeResident(fixture.town().id(), parent.getUUID());
        SettlementPopulationService.provideFamilyFood(helper.getLevel(), fixture.town());
        helper.assertValueEqual(stock(fixture), 64, "stale care records cannot provision a removed census member");
        registry.addOrUpdateResident(fixture.town().id(), parent.getUUID(), true, helper.getLevel().getGameTime());
        var impostor = EntityType.PIG.create(helper.getLevel());
        impostor.setUUID(parent.getUUID());
        impostor.setPos(fixture.town().center().getBottomCenter());
        helper.assertTrue(helper.getLevel().addFreshEntity(impostor), "the live conflicting identity is tracked");
        try {
            SettlementPopulationService.provideFamilyFood(helper.getLevel(), fixture.town());
            helper.assertValueEqual(stock(fixture), 64, "a conflicting live entity blocks provision from an old villager context");
        } finally {
            impostor.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_family_food")
    public static void personalCommitmentsAndChildrenRemainOutsideBackgroundFamilyProvision(GameTestHelper helper) {
        var fixture = SettlementPopulationGameTests.fixture(helper);
        var parent = parent(helper, fixture);
        parent.setAge(-1_000);
        SettlementCareService.onUnloaded(parent);
        SettlementPopulationService.provideFamilyFood(helper.getLevel(), fixture.town());
        helper.assertValueEqual(stock(fixture), 64, "children do not receive breeding provisions");
        parent.setAge(0);
        VillagerAssignmentStore.hire(parent, UUID.randomUUID(), HiredVillagerRole.CRAFTSMAN, 10L, fixture.town().center());
        SettlementCareService.onUnloaded(parent);
        helper.assertTrue(SettlementWorkforceService.hasPersonalCommitment(parent), "the parent has a real personal contract");
        SettlementPopulationService.provideFamilyFood(helper.getLevel(), fixture.town());
        helper.assertValueEqual(stock(fixture), 64, "a personal contract retains its existing family policy");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_family_food")
    public static void offDutyCivicParentsUseCurrentFamilyFoodWithoutChangingWorkInventory(GameTestHelper helper) {
        var fixture = SettlementPopulationGameTests.fixture(helper);
        var parent = parent(helper, fixture);
        parent.setVillagerData(parent.getVillagerData().setProfession(VillagerProfession.FISHERMAN));
        helper.assertTrue(helper.getLevel().addFreshEntity(parent), "the civic parent is initially loaded");
        var area = new SettlementArea(UUID.randomUUID(), SettlementAreaType.FISHERY, "Fishery",
                fixture.town().center().offset(-2, -1, -2), fixture.town().center().offset(3, 2, 3), 5, true);
        fixture.town().putArea(fixture.town().leader(), area);
        helper.assertTrue(SettlementWorkforceService.assign(helper.getLevel(), fixture.leader(), fixture.town().id(), parent, area.id()),
                "the resident receives a normal civic fishing assignment");
        HiredJobInventory.getJobInventory(parent).setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.FISHING_ROD));
        HiredWorkStateStore.state(parent).putBoolean("Enabled", true);
        HiredWorkStateStore.state(parent).putString("WorkerTaskState", "working");
        parent.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        var level = helper.getLevel();
        long time = level.getDayTime();
        try {
            level.setDayTime(6_000L);
            SettlementPopulationService.provideFamilyFood(level, fixture.town());
            helper.assertValueEqual(stock(fixture), 64, "background work hours preserve the work commitment");
            level.setDayTime(12_000L);
            SettlementPopulationService.provideFamilyFood(level, fixture.town());
            helper.assertValueEqual(stock(fixture), 61, "a stale work task does not suppress off-duty family provision");
            var worker = fixture.town().workers().get(parent.getUUID());
            var context = worker.context(level, fixture.town());
            helper.assertValueEqual(context.getInventory().countItem(Items.BREAD), 3, "worker contexts use the same current native family inventory");
            helper.assertTrue(HiredJobInventory.getJobInventory(context).getItem(HiredJobInventory.MAINHAND_SLOT).is(Items.FISHING_ROD),
                    "the separate job inventory retains its actual tool");
        } finally {
            level.setDayTime(time);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_family_food")
    public static void whollyUnloadedTownsProvisionParentsWithoutLoadingChunks(GameTestHelper helper) {
        var level = helper.getLevel();
        var center = helper.absolutePos(new BlockPos(1, 2, 1)).offset(7_800_000, 0, 7_800_000);
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var id = registry.create(0L, level.dimension().location(), center, "");
        var town = SettlementSavedData.get(level).establish(registry, id, UUID.randomUUID(), center, 0L).orElseThrow();
        var home = new SettlementArea(UUID.randomUUID(), SettlementAreaType.HOME, "Home", center, center.offset(3, 2, 3), 5, true);
        var depotArea = new SettlementArea(UUID.randomUUID(), SettlementAreaType.DEPOT, "Depot", center.offset(5, 0, 0), center.offset(6, 2, 2), 5, true);
        town.putArea(town.leader(), home);
        town.putArea(town.leader(), depotArea);
        town.recordSurvey(home.id(), new SettlementBuildingSurvey.Survey(0L, List.of(center, center.east()), List.of(), true));
        var depot = town.registerDepot(town.leader(), depotArea.id(), depotArea.min(), SettlementDepotPurpose.GENERAL);
        depot.setItem(0, new ItemStack(Items.BREAD, 64));
        var parent = EntityType.VILLAGER.create(level);
        parent.setPos(center.getBottomCenter());
        VillageAllegianceApi.assignKnown(level, parent, id, AllegianceAssignmentSource.EXPLICIT_API);
        SettlementCareService.onUnloaded(parent);
        SettlementPopulationService.provideFamilyFood(level, town);
        helper.assertValueEqual(depot.count(stack -> stack.is(Items.BREAD)), 61, "remote town storage supplies the absent parent");
        helper.assertFalse(level.hasChunkAt(center), "family provision never loads the settlement chunk");
        helper.assertTrue(level.getEntity(parent.getUUID()) == null, "family provision never spawns a native parent");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_family_food")
    public static void savedFamilyInventoriesRejectDuplicateSlotsAndMissingFood(GameTestHelper helper) {
        var fixture = SettlementPopulationGameTests.fixture(helper);
        var parent = parent(helper, fixture);
        parent.getInventory().setItem(0, new ItemStack(Items.BREAD));
        fixture.town().residents().get(parent.getUUID()).rememberFamily(parent);
        var tag = fixture.town().residents().get(parent.getUUID()).save();
        var items = tag.getCompound("FamilyInventory").getCompound("Inventory").getList("Items", Tag.TAG_COMPOUND);
        items.add(items.getCompound(0).copy());
        helper.assertTrue(rejected(tag), "duplicate saved slots cannot silently discard native goods");
        tag = fixture.town().residents().get(parent.getUUID()).save();
        tag.getCompound("FamilyInventory").remove("Food");
        helper.assertTrue(rejected(tag), "missing native food is not silently reset to zero");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_family_food")
    public static void sleepingParentsWithDigestedFoodDoNotReceiveSurplusProvisions(GameTestHelper helper) {
        var fixture = SettlementPopulationGameTests.fixture(helper);
        var parent = parent(helper, fixture);
        ((VillagerFamilyAccessor) parent).villagerretaliation$setFamilyFood(12);
        var bed = fixture.town().surveys().get(fixture.home().id()).beds().getFirst();
        parent.startSleeping(bed);
        try {
            helper.assertFalse(parent.canBreed(), "sleeping temporarily prevents native courtship");
            SettlementPopulationService.provideFamilyFood(helper.getLevel(), fixture.town(), parent);
            helper.assertValueEqual(stock(fixture), 64, "a sleeping parent already has its twelve native food points");
            helper.assertTrue(parent.getInventory().isEmpty(), "no surplus bread is inserted while sleeping");
        } finally {
            parent.stopSleeping();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_family_food")
    public static void nativeJoinAndUnloadEventsRetainOneProvisionBalance(GameTestHelper helper) {
        var fixture = SettlementPopulationGameTests.fixture(helper);
        var parent = parent(helper, fixture);
        var stale = parent.saveWithoutId(new CompoundTag());
        SettlementPopulationService.provideFamilyFood(helper.getLevel(), fixture.town());
        var returning = EntityType.VILLAGER.create(helper.getLevel());
        returning.load(stale);
        helper.assertTrue(helper.getLevel().addFreshEntity(returning), "the actual native resident returns");
        try {
            helper.assertValueEqual(returning.getInventory().countItem(Items.BREAD), 3, "the native join event applies pending food before simulation resumes");
            returning.eatAndDigestFood();
            returning.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            var state = fixture.town().residents().get(returning.getUUID());
            helper.assertValueEqual(SettlementPopulationService.familyFood(state.context(helper.getLevel())), 0,
                    "the native unload event captures actual food consumption");
            SettlementPopulationService.provideFamilyFood(helper.getLevel(), fixture.town());
            helper.assertValueEqual(stock(fixture), 58, "a second provision spends another three real bread after actual consumption");
        } finally {
            if (!returning.isRemoved()) returning.discard();
        }
        helper.succeed();
    }

    private static boolean rejected(CompoundTag tag) {
        try {
            SettlementResidentState.load(tag);
            return false;
        } catch (IllegalArgumentException exception) {
            return true;
        }
    }

    private static Villager parent(GameTestHelper helper, SettlementPopulationGameTests.Fixture fixture) {
        var parent = EntityType.VILLAGER.create(helper.getLevel());
        parent.setPos(fixture.town().center().getBottomCenter());
        parent.setNoAi(true);
        VillageAllegianceApi.assignKnown(helper.getLevel(), parent, fixture.town().id(), AllegianceAssignmentSource.EXPLICIT_API);
        SettlementCareService.onUnloaded(parent);
        return parent;
    }

    private static SettlementResidentState savedState(GameTestHelper helper, SettlementPopulationGameTests.Fixture fixture, Villager parent) {
        return SettlementRecord.load(fixture.town().save(helper.getLevel().registryAccess()), helper.getLevel().registryAccess(), () -> {})
                .residents().get(parent.getUUID());
    }

    private static int stock(SettlementPopulationGameTests.Fixture fixture) {
        return fixture.depot().count(stack -> stack.is(Items.BREAD));
    }
}
