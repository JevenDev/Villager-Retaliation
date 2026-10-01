package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.AllegianceAssignmentSource;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceApi;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.profile.VillagerProfileManager;
import com.jvn.villagerretaliation.skill.VillagerSkillGenerator;
import com.jvn.villagerretaliation.skill.VillagerSkillSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class SettlementAutonomyGameTests {
    private SettlementAutonomyGameTests() {
    }

    @GameTest(template = "empty", batch = "settlement_autonomy")
    public static void backgroundRecruitmentUsesExistingHousingWithoutLoadingChunks(GameTestHelper helper) {
        var town = fixture(helper, 0);
        helper.assertFalse(town.manualManagement(), "managed settlements are autonomous by default");
        SettlementPopulationService.update(helper.getLevel(), town, town.nextApplicantAt());
        var roster = VillageAllegianceRegistrySavedData.get(helper.getLevel()).residentRoster(town.id(), helper.getLevel().getGameTime());
        helper.assertValueEqual(roster.size(), 1, "supported existing housing admits one new resident");
        helper.assertTrue(town.applicants().isEmpty(), "automatic admission consumes its application once");
        SettlementPopulationService.update(helper.getLevel(), town, town.nextApplicantAt() - 1L);
        helper.assertValueEqual(town.residents().size(), 1, "the next applicant still waits for its deadline");
        helper.assertFalse(helper.getLevel().hasChunkAt(town.center()), "recruitment never loads unknown terrain");
        helper.assertTrue(town.projects().isEmpty(), "growth does not create construction projects");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "settlement_autonomy")
    public static void manualManagementPersistsAndKeepsApplicantsPending(GameTestHelper helper) {
        var town = fixture(helper, 0);
        helper.assertFalse(town.setManualManagement(UUID.randomUUID(), true), "only the leader changes management policy");
        helper.assertTrue(town.setManualManagement(town.leader(), true), "the leader can manage the settlement manually");
        SettlementPopulationService.update(helper.getLevel(), town, town.nextApplicantAt());
        helper.assertValueEqual(town.applicants().size(), 1, "manual management keeps recruitment reviewable");
        helper.assertTrue(town.residents().isEmpty(), "manual applicants are not silently admitted");
        var restored = SettlementRecord.load(town.save(helper.getLevel().registryAccess()), helper.getLevel().registryAccess(), () -> {});
        helper.assertTrue(restored.manualManagement(), "the manual override survives reload");
        var legacy = town.save(helper.getLevel().registryAccess());
        legacy.remove("ManualManagement");
        helper.assertFalse(SettlementRecord.load(legacy, helper.getLevel().registryAccess(), () -> {}).manualManagement(),
                "older settlements receive the autonomous default");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "settlement_autonomy")
    public static void manualFillPrioritizesFoodAndAptitudeWithoutChangingPlayerChoices(GameTestHelper helper) {
        var town = fixture(helper, 3);
        var farm = town.areas().values().stream().filter(area -> area.type() == SettlementAreaType.FARM).findFirst().orElseThrow();
        var strongest = town.residents().values().stream().map(state -> state.context(helper.getLevel()))
                .max(java.util.Comparator.comparingInt(resident -> com.jvn.villagerretaliation.interaction.HiredVillagerRoles
                        .roleScore(helper.getLevel(), resident, farm.type().role()))).orElseThrow();
        SettlementAutonomyService.fill(helper.getLevel(), town, null);
        helper.assertValueEqual(town.duties().get(strongest.getUUID()), farm.id(), "food service receives the most suitable available resident");
        var duties = java.util.Map.copyOf(town.duties());
        SettlementAutonomyService.fill(helper.getLevel(), town, null);
        helper.assertValueEqual(town.duties(), duties, "stable staffing never reshuffles occupied jobs");
        town.removeDuty(strongest.getUUID());
        town.rememberManualDuty(strongest.getUUID());
        SettlementAutonomyService.fill(helper.getLevel(), town, null);
        helper.assertFalse(town.duties().containsKey(strongest.getUUID()), "an explicit player release remains idle");
        var restored = SettlementRecord.load(town.save(helper.getLevel().registryAccess()), helper.getLevel().registryAccess(), () -> {});
        helper.assertTrue(restored.manuallyStaffed(strongest.getUUID()), "player staffing ownership survives reload");
        helper.assertFalse(helper.getLevel().hasChunkAt(town.center()), "remote staffing uses saved residents");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "settlement_autonomy")
    public static void readyStageAdvancesOnceUnlessManuallyManaged(GameTestHelper helper) {
        var town = fixture(helper, 5);
        SettlementAutonomyService.fill(helper.getLevel(), town, null);
        helper.assertTrue(SettlementProgressionService.assess(helper.getLevel(), town).missing().isEmpty(), "the fixture meets real stage requirements");
        long required = SettlementProgressionService.requiredTicks();
        for (long tick = 0L; tick <= required; tick += 200L) town.support().observe(tick, true, false);
        town.setManualManagement(town.leader(), true);
        SettlementProgressionService.update(helper.getLevel(), town, required);
        helper.assertValueEqual(town.stage(), SettlementStage.CAMP, "manual management preserves the advancement decision");
        town.setManualManagement(town.leader(), false);
        SettlementProgressionService.update(helper.getLevel(), town, required);
        helper.assertValueEqual(town.stage(), SettlementStage.HAMLET, "sustained support advances an autonomous settlement");
        SettlementProgressionService.update(helper.getLevel(), town, required);
        helper.assertValueEqual(town.history().stream().filter(event -> event.type() == SettlementEvent.Type.STAGE_ADVANCED).count(), 1L,
                "a repeated update cannot duplicate stage advancement");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "settlement_autonomy")
    public static void automaticStaffingRestoresFutureAuthorityWithoutChangingDuties(GameTestHelper helper) {
        var town = fixture(helper, 3);
        var level = helper.getLevel();
        enroll(helper, town);
        var leader = net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,
                new com.mojang.authlib.GameProfile(town.leader(), "StaffingLeader"));
        var residents = town.residents().keySet().stream().sorted().toList();
        residents.forEach(town::rememberManualDuty);
        SettlementWorkerPoolSchedulingGameTests.cycle(helper, town, 100);
        helper.assertTrue(town.duties().isEmpty(), "manual idle choices block automatic assignment");
        var outsider = net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "StaffingViewer"));
        helper.assertFalse(SettlementWorkforceService.automaticStaffing(outsider, town, null), "outsiders cannot restore staffing");
        helper.assertFalse(SettlementWorkforceService.automaticStaffing(leader, town, UUID.randomUUID()), "unknown residents are rejected");
        town.setPermissions(town.leader(), outsider.getUUID(), java.util.Set.of(SettlementPermission.STAFF));
        helper.assertTrue(SettlementWorkforceService.automaticStaffing(outsider, town, residents.getFirst()), "staff delegates can restore an unloaded resident");
        long revision = town.revision();
        helper.assertTrue(SettlementWorkforceService.automaticStaffing(outsider, town, residents.getFirst()), "repeated requests succeed");
        helper.assertValueEqual(town.revision(), revision, "repeated requests do not change the record");
        var restored = SettlementRecord.load(town.save(level.registryAccess()), level.registryAccess(), () -> {});
        helper.assertFalse(restored.manuallyStaffed(residents.getFirst()), "automatic authority survives reload");
        helper.assertTrue(restored.manuallyStaffed(residents.getLast()), "other manual choices survive reload");
        helper.assertTrue(SettlementWorkforceService.automaticStaffing(leader, town, null), "bulk restore succeeds");
        SettlementWorkerPoolSchedulingGameTests.cycle(helper, town, 100);
        helper.assertFalse(town.duties().isEmpty(), "eligible idle residents receive suitable work");
        var duties = java.util.Map.copyOf(town.duties());
        duties.keySet().forEach(town::rememberManualDuty);
        SettlementWorkforceService.automaticStaffing(leader, town, null);
        helper.assertValueEqual(town.duties(), duties, "returning authority preserves current duties");
        town.setManualManagement(town.leader(), true);
        residents.forEach(town::rememberManualDuty);
        town.setManualManagement(town.leader(), false);
        helper.assertTrue(town.manuallyStaffed(residents.getFirst()), "town policy never clears personal manual choices");
        town.removeResident(residents.getFirst());
        helper.assertFalse(town.manuallyStaffed(residents.getFirst()), "resident removal clears obsolete authority");
        helper.assertFalse(level.hasChunkAt(town.center()), "staffing controls do not load chunks");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "settlement_autonomy")
    public static void restoredStaffingStillRespectsPersonalCommitmentsDeparturesAndAvailability(GameTestHelper helper) {
        var town = fixture(helper, 1);
        var level = helper.getLevel();
        enroll(helper, town);
        var id = town.residents().keySet().iterator().next();
        var saved = town.residents().get(id).save();
        saved.putBoolean("PersonalCommitment", true);
        town.recordResident(SettlementResidentState.load(saved));
        town.rememberManualDuty(id);
        town.returnToAutomaticStaffing(town.leader(), id);
        SettlementWorkerPoolSchedulingGameTests.cycle(helper, town, 100);
        helper.assertFalse(town.duties().containsKey(id), "returning authority cannot override a personal commitment");
        saved.putBoolean("PersonalCommitment", false);
        saved.putBoolean("Present", false);
        town.recordResident(SettlementResidentState.load(saved));
        SettlementWorkerPoolSchedulingGameTests.cycle(helper, town, 100);
        helper.assertFalse(town.duties().containsKey(id), "unavailable residents wait for a usable observation");
        saved.putBoolean("Present", true);
        town.recordResident(SettlementResidentState.load(saved));
        town.recordDeparture(new SettlementDeparture(id, "Departing", level.getGameTime(), SettlementDeparture.Reason.FOOD,
                town.residents().get(id)));
        SettlementWorkerPoolSchedulingGameTests.cycle(helper, town, 100);
        helper.assertFalse(town.duties().containsKey(id), "returning authority cannot cancel a departure");
        town.removeDeparture(id);
        SettlementWorkerPoolSchedulingGameTests.cycle(helper, town, 100);
        helper.assertTrue(town.duties().containsKey(id), "the resident becomes eligible when their commitment and availability allow");
        var successor = UUID.randomUUID();
        town.rememberManualDuty(id);
        var offer = town.offerLeadership(town.leader(), successor, level.getGameTime());
        helper.assertTrue(town.acceptLeadership(successor, offer.id(), level.getGameTime()), "leadership can change normally");
        helper.assertTrue(town.manuallyStaffed(id), "leadership changes preserve deliberate staffing choices");
        helper.assertTrue(town.returnToAutomaticStaffing(successor, id), "the new leader can explicitly return staffing authority");
        helper.succeed();
    }

    private static void enroll(GameTestHelper helper, SettlementRecord town) {
        helper.getLevel().setDayTime(4_000);
        var sites = town.areas().values().stream().filter(area -> area.type().role() != null).toList();
        for (var area : sites) SettlementTestPlots.claim(helper.getLevel(), town, area.min(), area.max());
        helper.assertTrue(town.configureWorkerPool(town.leader(), UUID.randomUUID(), -1,
                new SettlementWorkerPool.Configuration("Explicit staffing", List.copyOf(town.residents().keySet()),
                        sites.stream().map(area -> SettlementWorkerPool.Staffing.defaults(town, area.id())).toList()), true),
                "the player explicitly enrolls residents and approved worksites");
    }

    private static SettlementRecord fixture(GameTestHelper helper, int population) {
        var level = helper.getLevel();
        var origin = helper.absolutePos(BlockPos.ZERO);
        var center = new BlockPos(17000000 + origin.getX() * 16, 80, 17000000 + origin.getZ() * 16);
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var id = registry.create(level.getGameTime(), level.dimension().location(), center, "Autonomous settlement");
        var town = SettlementSavedData.get(level).establish(registry, id, UUID.randomUUID(), center, level.getGameTime()).orElseThrow();
        var home = new SettlementArea(UUID.randomUUID(), SettlementAreaType.HOME, "Housing", center.west(8), center.offset(-4, 2, 8), 5, true);
        var farm = new SettlementArea(UUID.randomUUID(), SettlementAreaType.FARM, "Farm", center, center.offset(3, 2, 3), 5, true);
        var stores = new SettlementArea(UUID.randomUUID(), SettlementAreaType.DEPOT, "Stores", center.east(8), center.offset(10, 2, 2), 9, true);
        for (var area : List.of(home, farm, stores)) town.putArea(town.leader(), area);
        var beds = new ArrayList<BlockPos>();
        for (int index = 0; index < 8; index++) beds.add(home.min().south(index));
        town.recordSurvey(home.id(), new SettlementBuildingSurvey.Survey(level.getGameTime(), beds, List.of(), true));
        var depot = town.registerDepot(town.leader(), stores.id(), stores.min(), SettlementDepotPurpose.GENERAL);
        depot.observeRules(List.of());
        depot.setItem(0, new ItemStack(Items.BREAD, 64));
        for (int index = 0; index < population; index++) {
            var resident = EntityType.VILLAGER.create(level);
            resident.setPos(center.east(index).getBottomCenter());
            resident.setVillagerData(resident.getVillagerData().setProfession(VillagerProfession.FARMER));
            VillageAllegianceApi.assignKnown(level, resident, id, AllegianceAssignmentSource.EXPLICIT_API);
            VillagerProfileManager.getOrCreateProfile(level, resident).replaceSkills(VillagerSkillSet.filled(30 + index * 10),
                    VillagerSkillGenerator.CURRENT_GENERATION_VERSION, level.getGameTime());
            town.recordResident(SettlementResidentState.capture(resident, town, level.getGameTime()));
        }
        return town;
    }
}
