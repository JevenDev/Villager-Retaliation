package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.AllegianceAssignmentSource;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceApi;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.interaction.HireContract;
import com.jvn.villagerretaliation.interaction.HireContractStore;
import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.VillagerAssignmentState;
import com.jvn.villagerretaliation.interaction.VillagerAssignmentStore;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.jvn.villagerretaliation.social.VillagerSocialGraphSavedData;
import com.jvn.villagerretaliation.social.VillagerSocialGraphSavedData.RelationshipType;
import com.jvn.villagerretaliation.villager.VillagerRecoveryService;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class SettlementDepartureGameTests {
    private SettlementDepartureGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_departures")
    public static void shortagesGiveAFullGracePeriodAndNeverDowngradeTheTown(GameTestHelper helper) {
        var town = town(helper);
        town.advance();
        Villager resident = resident(helper, town, false);
        var state = town.residents().get(resident.getUUID());
        state.observeShortage((int) SettlementDepartureService.graceTicks() - 200, true);
        helper.assertTrue(SettlementDepartureService.process(helper.getLevel(), town, helper.getLevel().getGameTime()).isEmpty(),
                "a resident keeps their home throughout the grace period");
        state.observeShortage(200, true);
        var departed = SettlementDepartureService.process(helper.getLevel(), town, helper.getLevel().getGameTime());
        helper.assertValueEqual(departed, List.of(resident.getUUID()), "prolonged shortages cause an actual census departure");
        helper.assertTrue(VillageAllegianceRegistrySavedData.get(helper.getLevel()).residentRoster(town.id(), helper.getLevel().getGameTime()).isEmpty(),
                "the departed resident no longer counts toward population");
        helper.assertValueEqual(town.stage(), SettlementStage.HAMLET, "decline never silently downgrades an earned stage");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_departures")
    public static void RestoredSupportStopsDepartureBeforeTheWarningHistoryClears(GameTestHelper helper) {
        var town = town(helper);
        Villager resident = resident(helper, town, false);
        var state = town.residents().get(resident.getUUID());
        state.observeShortage((int) SettlementDepartureService.graceTicks(), true);
        var home = new SettlementArea(UUID.randomUUID(), SettlementAreaType.HOME, "Home",
                town.center().west(3), town.center().west(), 5, true);
        town.putArea(town.leader(), home);
        town.recordSurvey(home.id(), new SettlementBuildingSurvey.Survey(0L, List.of(home.min()), List.of(), true));
        VillagerRecoveryService.restoreRecoveryState(resident, new VillagerRecoveryService.RecoverySnapshot(20, 5.0F, 0.0F, 0));
        state.observe(resident, town, helper.getLevel().getGameTime());
        helper.assertTrue(state.shortageTicks() >= SettlementDepartureService.graceTicks(), "the old warning history is still visible");
        helper.assertTrue(SettlementDepartureService.process(helper.getLevel(), town, helper.getLevel().getGameTime()).isEmpty(),
                "restored food and housing stop further departures immediately");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_departures")
    public static void departuresAreGradualAcrossUnrelatedResidents(GameTestHelper helper) {
        var town = town(helper);
        Villager first = resident(helper, town, false);
        Villager second = resident(helper, town, false);
        warn(town, first);
        warn(town, second);
        long now = helper.getLevel().getGameTime();
        helper.assertValueEqual(SettlementDepartureService.process(helper.getLevel(), town, now).size(), 1,
                "one unrelated resident leaves after the grace period");
        helper.assertTrue(SettlementDepartureService.process(helper.getLevel(), town, now + 200L).isEmpty(),
                "a shortage does not empty a town in one simulation pass");
        helper.assertValueEqual(SettlementDepartureService.process(helper.getLevel(), town, now + 24_000L).size(), 1,
                "another resident may leave after another supported recovery window");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_departures")
    public static void dependentChildrenLeaveWithBothParentsAndKeepTheirFamilyLinks(GameTestHelper helper) {
        var town = town(helper);
        Villager parentA = resident(helper, town, false);
        Villager parentB = resident(helper, town, false);
        Villager child = resident(helper, town, true);
        var graph = VillagerSocialGraphSavedData.get(helper.getLevel());
        graph.linkParentsAndChild(helper.getLevel(), parentA, parentB, child);
        warn(town, parentA);
        var departed = SettlementDepartureService.process(helper.getLevel(), town, helper.getLevel().getGameTime());
        helper.assertValueEqual(departed.size(), 3, "the dependent family departs together");
        helper.assertTrue(departed.containsAll(List.of(parentA.getUUID(), parentB.getUUID(), child.getUUID())),
                "both parents remain with their dependent child");
        helper.assertTrue(graph.relationships(child.getUUID(), RelationshipType.PARENT).containsAll(List.of(parentA.getUUID(), parentB.getUUID())),
                "departure preserves the family graph");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_departures")
    public static void aPersonalContractProtectsTheFamilyWithoutChangingEscrow(GameTestHelper helper) {
        var town = town(helper);
        Villager parentA = resident(helper, town, false);
        Villager parentB = resident(helper, town, false);
        Villager child = resident(helper, town, true);
        VillagerSocialGraphSavedData.get(helper.getLevel()).linkParentsAndChild(helper.getLevel(), parentA, parentB, child);
        HireContractStore.save(parentB, HireContract.regular(UUID.randomUUID(), helper.getLevel().getGameTime(), 3, 4, 12, HiredVillagerRole.FARMING));
        CompoundTag contract = parentB.getPersistentData().getCompound("VillagerRetaliationHireContract").copy();
        town.residents().get(parentB.getUUID()).observe(parentB, town, helper.getLevel().getGameTime());
        warn(town, parentA);
        helper.assertTrue(SettlementDepartureService.process(helper.getLevel(), town, helper.getLevel().getGameTime()).isEmpty(),
                "an existing personal commitment prevents involuntary family separation");
        helper.assertValueEqual(parentB.getPersistentData().getCompound("VillagerRetaliationHireContract"), contract,
                "decline preserves the paid contract and its escrow");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_departures")
    public static void departedCivicWorkersKeepTheirIdentityAndCargoWhenTheyReturn(GameTestHelper helper) {
        var town = town(helper);
        Villager resident = resident(helper, town, false);
        resident.setCustomName(Component.literal("Mira"));
        var farm = town.areas().values().iterator().next();
        town.assignDuty(resident.getUUID(), farm.id());
        SettlementWorkforceService.reconcile(helper.getLevel(), resident);
        HiredJobInventory.getJobInventory(resident).insertOutput(new ItemStack(Items.IRON_INGOT, 7));
        CompoundTag oldEntity = new CompoundTag();
        resident.saveWithoutId(oldEntity);
        warn(town, resident);
        SettlementDepartureService.process(helper.getLevel(), town, helper.getLevel().getGameTime());
        helper.assertFalse(town.duties().containsKey(resident.getUUID()), "departure releases the civic post immediately");
        Villager returning = EntityType.VILLAGER.create(helper.getLevel());
        returning.load(oldEntity);
        helper.assertTrue(helper.getLevel().addFreshEntity(returning), "the old entity save can return normally");
        try {
            helper.assertTrue(VillageAllegianceApi.canonicalPrimary(helper.getLevel(), returning).isEmpty(),
                    "the departure is applied before the old allegiance can rejoin the census");
            helper.assertValueEqual(returning.getCustomName().getString(), "Mira", "the same named person remains in the world");
            helper.assertFalse(VillagerAssignmentStore.snapshot(returning).state() == VillagerAssignmentState.CIVIC,
                    "the stale civic assignment is released on return");
            helper.assertValueEqual(HiredJobInventory.getJobInventory(returning).collectOutputItems().stream()
                    .mapToInt(output -> output.stack().getCount()).sum(), 7, "leaving town does not destroy carried stock");
            helper.assertTrue(VillageAllegianceRegistrySavedData.get(helper.getLevel()).residentRoster(town.id(), helper.getLevel().getGameTime()).isEmpty(),
                    "returning to loaded chunks does not restore departed population");
        } finally {
            returning.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_departures")
    public static void departureRecordsSurviveSavingAndExplicitReadmissionClearsThem(GameTestHelper helper) {
        var town = town(helper);
        Villager resident = resident(helper, town, false);
        warn(town, resident);
        SettlementDepartureService.process(helper.getLevel(), town, helper.getLevel().getGameTime());
        var saved = SettlementRecord.load(town.save(helper.getLevel().registryAccess()), helper.getLevel().registryAccess(), () -> {});
        helper.assertTrue(saved.departures().containsKey(resident.getUUID()), "unloaded departures survive a world save");
        helper.assertValueEqual(saved.nextDepartureAt(), town.nextDepartureAt(), "the gradual departure schedule persists");
        SettlementDepartureService.onActive(resident);
        VillageAllegianceApi.assignKnown(helper.getLevel(), resident, town.id(), AllegianceAssignmentSource.EXPLICIT_API);
        helper.assertFalse(town.departures().containsKey(resident.getUUID()), "explicit readmission clears the old departure receipt");
        SettlementDepartureService.onActive(resident);
        helper.assertValueEqual(VillageAllegianceApi.canonicalPrimary(helper.getLevel(), resident).orElseThrow(), town.id(),
                "readmitted residents remain members on later ticks");
        helper.succeed();
    }

    private static void warn(SettlementRecord town, Villager resident) {
        town.residents().get(resident.getUUID()).observeShortage((int) SettlementDepartureService.graceTicks(), true);
    }

    private static Villager resident(GameTestHelper helper, SettlementRecord town, boolean child) {
        Villager resident = EntityType.VILLAGER.create(helper.getLevel());
        resident.setPos(town.center().getCenter());
        resident.setNoAi(true);
        resident.setVillagerData(resident.getVillagerData().setProfession(VillagerProfession.FARMER));
        resident.setAge(child ? -24_000 : 0);
        VillagerRecoveryService.restoreRecoveryState(resident, new VillagerRecoveryService.RecoverySnapshot(0, 0.0F, 0.0F, 0));
        VillageAllegianceApi.assignKnown(helper.getLevel(), resident, town.id(), AllegianceAssignmentSource.EXPLICIT_API);
        town.recordResident(SettlementResidentState.capture(resident, town, helper.getLevel().getGameTime()));
        return resident;
    }

    private static SettlementRecord town(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos center = helper.absolutePos(new BlockPos(3, 2, 3));
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var id = registry.create(level.getGameTime(), level.dimension().location(), center, "");
        var town = SettlementSavedData.get(level).establish(registry, id, UUID.randomUUID(), center, level.getGameTime()).orElseThrow();
        var farm = new SettlementArea(UUID.randomUUID(), SettlementAreaType.FARM, "Fields", center, center.offset(2, 2, 2), 5, true);
        town.putArea(town.leader(), farm);
        return town;
    }
}
