package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.AllegianceAssignmentSource;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceApi;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.villager.VillagerRecoveryService;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class SettlementCareGameTests {
    private SettlementCareGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_care")
    public static void backgroundMealsConsumeRealFoodAndReturnTheirContainers(GameTestHelper helper) {
        var fixture = fixture(helper, 0);
        fixture.depot().setItem(0, new ItemStack(Items.MUSHROOM_STEW));
        fixture.depot().setItem(1, new ItemStack(Items.MUSHROOM_STEW));
        fixture.resident().setHealth(10.0F);
        var state = SettlementResidentState.capture(fixture.resident(), fixture.town(), 0L);
        fixture.town().recordResident(state);
        helper.assertValueEqual(state.advance(helper.getLevel(), fixture.town(), 200L), 200, "only server game ticks are simulated");
        helper.assertValueEqual(fixture.depot().count(stack -> stack.is(Items.MUSHROOM_STEW)), 0, "two actual meals are consumed");
        helper.assertValueEqual(fixture.depot().count(stack -> stack.is(Items.BOWL)), 2, "both meal containers return to real stock");
        helper.assertTrue(state.food() >= 10 && state.health() > 10.0F, "supported background residents eat and recover");
        helper.assertTrue(state.restore(fixture.resident()), "background care transfers to the returning resident");
        helper.assertValueEqual(VillagerRecoveryService.foodLevel(fixture.resident()), state.food(), "active and background hunger agree");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_care")
    public static void careReloadsOnceWithoutOverwritingLaterCombatOrDuplicatingMeals(GameTestHelper helper) {
        var fixture = fixture(helper, 4);
        fixture.resident().setCustomName(Component.literal("Mira"));
        fixture.depot().setItem(0, new ItemStack(Items.BREAD, 20));
        CompoundTag oldEntity = new CompoundTag();
        fixture.resident().saveWithoutId(oldEntity);
        var state = SettlementResidentState.capture(fixture.resident(), fixture.town(), 0L);
        fixture.town().recordResident(state);
        state.advance(helper.getLevel(), fixture.town(), 200L);
        var savedTown = SettlementRecord.load(fixture.town().save(helper.getLevel().registryAccess()),
                helper.getLevel().registryAccess(), () -> {});
        var restoredState = savedTown.residents().get(fixture.resident().getUUID());
        Villager returning = EntityType.VILLAGER.create(helper.getLevel());
        returning.load(oldEntity);
        int stock = fixture.depot().count(stack -> stack.is(Items.BREAD));
        helper.assertTrue(restoredState.restore(returning), "a stale entity snapshot receives the newer care revision");
        helper.assertValueEqual(returning.getUUID(), fixture.resident().getUUID(), "care preserves the resident identity");
        helper.assertValueEqual(returning.getCustomName().getString(), "Mira", "care preserves the resident name");
        returning.setHealth(3.0F);
        helper.assertFalse(restoredState.restore(returning), "the same care revision cannot be replayed");
        helper.assertValueEqual(returning.getHealth(), 3.0F, "later combat damage remains authoritative");
        helper.assertValueEqual(fixture.depot().count(stack -> stack.is(Items.BREAD)), stock, "loading a resident spends no extra meal");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_care")
    public static void backgroundStarvationCannotKillAndResupplyRecoversCare(GameTestHelper helper) {
        var fixture = fixture(helper, 0);
        fixture.resident().setHealth(1.0F);
        var state = SettlementResidentState.capture(fixture.resident(), fixture.town(), 0L);
        for (int tick = 200; tick <= 2_000; tick += 200) {
            state.advance(helper.getLevel(), fixture.town(), tick);
            state.observeShortage(200, false);
        }
        helper.assertValueEqual(state.health(), 1.0F, "care starvation is nonfatal");
        helper.assertValueEqual(state.shortageTicks(), 2_000L, "sustained hunger records a recoverable warning");
        fixture.depot().setItem(0, new ItemStack(Items.BREAD, 20));
        state.advance(helper.getLevel(), fixture.town(), 2_200L);
        state.observeShortage(200, false);
        helper.assertTrue(state.food() >= 10, "resupply restores food without resetting the resident");
        helper.assertTrue(state.shortageTicks() < 2_000L, "support reduces the shortage history");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_care")
    public static void clockGapsCannotCauseOfflineCatchupOrDuplicateCare(GameTestHelper helper) {
        var fixture = fixture(helper, 20);
        fixture.resident().setAge(-1_000);
        var state = SettlementResidentState.capture(fixture.resident(), fixture.town(), 100L);
        helper.assertValueEqual(state.advance(helper.getLevel(), fixture.town(), 10_000_000L), 200, "a clock gap is bounded to one running interval");
        helper.assertValueEqual(state.age(), -800, "children do not age through an offline time gap");
        long version = state.version();
        helper.assertValueEqual(state.advance(helper.getLevel(), fixture.town(), 10_000_000L), 0, "repeated simulation at one time has no effect");
        helper.assertValueEqual(state.version(), version, "idempotent simulation keeps the same revision");
        helper.assertValueEqual(state.advance(helper.getLevel(), fixture.town(), 1L), 0, "a reversed clock never simulates negative time");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_care")
    public static void backgroundChildrenAgeIntoTheExistingCensus(GameTestHelper helper) {
        var fixture = fixture(helper, 20);
        fixture.resident().setAge(-100);
        VillageAllegianceApi.assignKnown(helper.getLevel(), fixture.resident(), fixture.town().id(), AllegianceAssignmentSource.BIRTH);
        var state = SettlementResidentState.capture(fixture.resident(), fixture.town(), 0L);
        fixture.town().recordResident(state);
        SettlementCareService.update(helper.getLevel(), fixture.town(), 200L);
        helper.assertValueEqual(state.age(), 0, "the absent child reaches adulthood");
        helper.assertTrue(VillageAllegianceRegistrySavedData.get(helper.getLevel()).residentRoster(fixture.town().id(), 200L)
                .stream().anyMatch(resident -> resident.id().equals(state.id()) && resident.adult()), "the same census identity becomes an adult");
        state.restore(fixture.resident());
        helper.assertFalse(fixture.resident().isBaby(), "the returning entity agrees with the background census");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_care")
    public static void activeResidentsKeepTheirLiveRecoveryAuthority(GameTestHelper helper) {
        var fixture = fixture(helper, 20);
        fixture.depot().setItem(0, new ItemStack(Items.BREAD, 20));
        fixture.town().recordResident(SettlementResidentState.capture(fixture.resident(), fixture.town(), 0L));
        helper.assertTrue(helper.getLevel().addFreshEntity(fixture.resident()), "the resident is active in the world");
        try {
            helper.assertTrue(helper.getLevel().isPositionEntityTicking(fixture.resident().blockPosition()), "the resident's chunk is ticking");
            VillagerRecoveryService.restoreRecoveryState(fixture.resident(),
                    new VillagerRecoveryService.RecoverySnapshot(4, 0.0F, 0.0F, 0));
            SettlementCareService.update(helper.getLevel(), fixture.town(), 200L);
            helper.assertValueEqual(fixture.town().residents().get(fixture.resident().getUUID()).food(), 4, "background care observes the active hunger value");
            helper.assertValueEqual(fixture.depot().getItem(0).getCount(), 20, "the background scheduler never feeds an active resident twice");
        } finally {
            fixture.resident().discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_care")
    public static void travellingResidentsDoNotConsumeTownFoodWhileAway(GameTestHelper helper) {
        var fixture = fixture(helper, 0);
        fixture.depot().setItem(0, new ItemStack(Items.BREAD, 20));
        fixture.resident().setPos(fixture.town().center().east(200).getCenter());
        var state = SettlementResidentState.capture(fixture.resident(), fixture.town(), 0L);
        helper.assertFalse(state.present(), "a resident outside the town is recorded as away");
        helper.assertValueEqual(state.advance(helper.getLevel(), fixture.town(), 200L), 0, "the town does not simulate care for travellers");
        helper.assertValueEqual(fixture.depot().getItem(0).getCount(), 20, "travellers cannot draw meals from distant town stock");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_care")
    public static void containerMealsWaitForSpaceAndOldSavesLoadWithoutCareRecords(GameTestHelper helper) {
        var fixture = fixture(helper, 0);
        fixture.town().configureDepot(fixture.town().leader(), fixture.depot().id(), SettlementDepotPurpose.FOOD, 5);
        fixture.depot().setItem(0, new ItemStack(Items.MUSHROOM_STEW));
        var state = SettlementResidentState.capture(fixture.resident(), fixture.town(), 0L);
        state.advance(helper.getLevel(), fixture.town(), 200L);
        helper.assertTrue(fixture.depot().getItem(0).is(Items.MUSHROOM_STEW), "a meal is not consumed when its bowl has nowhere to go");
        CompoundTag legacy = fixture.town().save(helper.getLevel().registryAccess());
        legacy.remove("ResidentCare");
        var migrated = SettlementRecord.load(legacy, helper.getLevel().registryAccess(), () -> {});
        helper.assertTrue(migrated.residents().isEmpty(), "old town records initialize care when residents are next observed");
        helper.assertValueEqual(migrated.id(), fixture.town().id(), "migration preserves the town identity");
        helper.succeed();
    }

    private static Fixture fixture(GameTestHelper helper, int food) {
        var level = helper.getLevel();
        BlockPos center = helper.absolutePos(new BlockPos(2, 2, 2));
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var id = registry.create(level.getGameTime(), level.dimension().location(), center, "");
        var town = SettlementSavedData.get(level).establish(registry, id, UUID.randomUUID(), center, level.getGameTime()).orElseThrow();
        var area = new SettlementArea(UUID.randomUUID(), SettlementAreaType.DEPOT, "Stores", center, center.offset(3, 2, 3), 5, true);
        town.putArea(town.leader(), area);
        var depot = town.registerDepot(town.leader(), area.id(), center, SettlementDepotPurpose.GENERAL);
        Villager resident = EntityType.VILLAGER.create(level);
        resident.setPos(center.getCenter());
        resident.setNoAi(true);
        VillagerRecoveryService.restoreRecoveryState(resident, new VillagerRecoveryService.RecoverySnapshot(food, 0.0F, 0.0F, 0));
        VillageAllegianceApi.assignKnown(level, resident, town.id(), AllegianceAssignmentSource.EXPLICIT_API);
        return new Fixture(town, depot, resident);
    }

    private record Fixture(SettlementRecord town, SettlementDepot depot, Villager resident) {
    }
}
