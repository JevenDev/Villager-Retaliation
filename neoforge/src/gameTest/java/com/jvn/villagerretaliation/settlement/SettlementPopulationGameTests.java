package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.AllegianceAssignmentSource;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceApi;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.social.BreedingBlockReason;
import com.jvn.villagerretaliation.social.VillagerBreedingPolicy;
import com.jvn.villagerretaliation.villager.VillagerPresetNameRegistry;
import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class SettlementPopulationGameTests {
    private SettlementPopulationGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void applicationsRequireApprovalAndArriveWithTheirSavedIdentity(GameTestHelper helper) {
        var fixture = fixture(helper);
        var applicant = SettlementPopulationService.propose(helper.getLevel(), fixture.town()).orElseThrow();
        helper.assertTrue(helper.getLevel().getEntity(applicant.id()) == null, "an applicant is not already a resident entity");
        helper.assertFalse(SettlementPopulationService.arrive(helper.getLevel(), fixture.town(), applicant),
                "unapproved applications cannot arrive");
        helper.assertTrue(VillageAllegianceRegistrySavedData.get(helper.getLevel())
                .residentRoster(fixture.town().id(), helper.getLevel().getGameTime()).isEmpty(), "applications do not inflate population");
        var saved = SettlementSavedData.get(helper.getLevel()).save(new CompoundTag(), helper.getLevel().registryAccess());
        var restored = SettlementSavedData.load(saved, helper.getLevel().registryAccess())
                .get(fixture.town().id()).orElseThrow().applicants().get(applicant.id());
        helper.assertValueEqual(restored.name(), applicant.name(), "the applicant name persists");
        helper.assertValueEqual(restored.entity(), applicant.entity(), "the pending resident data persists");
        helper.assertTrue(SettlementPopulationService.approve(fixture.leader(), fixture.town().id(), applicant.id()),
                "a staffing authority can approve the arrival");
        Villager resident = (Villager) helper.getLevel().getEntity(applicant.id());
        helper.assertTrue(resident != null, "the approved resident arrives at a safe loaded location");
        helper.assertValueEqual(VillagerPresetNameRegistry.resolveDisplayName(resident).getString(), applicant.name(),
                "arrival preserves the advertised identity");
        helper.assertValueEqual(VillageAllegianceApi.canonicalPrimary(helper.getLevel(), resident).orElseThrow(), fixture.town().id(),
                "the approved town becomes home");
        helper.assertFalse(fixture.town().applicants().containsKey(applicant.id()), "arrival consumes the application");
        helper.assertFalse(SettlementPopulationService.approve(fixture.leader(), fixture.town().id(), applicant.id()),
                "replaying approval cannot create another resident");
        helper.assertFalse(SettlementPopulationService.arrive(helper.getLevel(), fixture.town(), applicant),
                "a stale arrival request cannot recreate an already consumed application");
        resident.discard();
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void applicationsRespectStaffingAuthorityAndCanBeDeclined(GameTestHelper helper) {
        var fixture = fixture(helper);
        var applicant = SettlementPopulationService.propose(helper.getLevel(), fixture.town()).orElseThrow();
        ServerPlayer viewer = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "TownViewer"));
        fixture.town().setPermissions(fixture.leader().getUUID(), viewer.getUUID(), Set.of(SettlementPermission.VIEW));
        helper.assertFalse(SettlementPopulationService.approve(viewer, fixture.town().id(), applicant.id()),
                "viewing does not grant admission authority");
        helper.assertFalse(SettlementPopulationService.decline(viewer, fixture.town().id(), applicant.id()),
                "viewing does not allow rejecting an applicant");
        fixture.town().setPermissions(fixture.leader().getUUID(), viewer.getUUID(), Set.of(SettlementPermission.STAFF));
        helper.assertTrue(SettlementPopulationService.decline(viewer, fixture.town().id(), applicant.id()),
                "a staffing delegate can decline the application");
        helper.assertTrue(fixture.town().applicants().isEmpty(), "declining removes the pending application");
        helper.assertTrue(helper.getLevel().getEntity(applicant.id()) == null, "declining never spawns the applicant");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void approvalRechecksHousingAndPreservesPendingApplicantsDuringShortages(GameTestHelper helper) {
        var fixture = fixture(helper);
        var applicant = SettlementPopulationService.propose(helper.getLevel(), fixture.town()).orElseThrow();
        fixture.depot().clearContent();
        helper.assertFalse(SettlementPopulationService.approve(fixture.leader(), fixture.town().id(), applicant.id()),
                "a food shortage stalls admission");
        helper.assertTrue(fixture.town().applicants().containsKey(applicant.id()), "the pending identity is retained");
        fixture.depot().setItem(0, new ItemStack(Items.BREAD, 64));
        for (BlockPos bed : fixture.town().surveys().get(fixture.home().id()).beds()) {
            helper.getLevel().removeBlock(bed, false);
        }
        helper.assertFalse(SettlementPopulationService.approve(fixture.leader(), fixture.town().id(), applicant.id()),
                "destroyed homes cannot be approved from a stale housing count");
        helper.assertTrue(helper.getLevel().getEntity(applicant.id()) == null, "failed approval does not create an unsupported resident");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void approvedUnloadedArrivalJoinsTheCensusWithoutLoadingTheTown(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos center = new BlockPos(5_000_000, 64, 5_000_000);
        ServerPlayer leader = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "RemoteLeader"));
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var id = registry.create(level.getGameTime(), level.dimension().location(), center, "");
        var town = SettlementSavedData.get(level).establish(registry, id, leader.getUUID(), center, level.getGameTime()).orElseThrow();
        var home = new SettlementArea(UUID.randomUUID(), SettlementAreaType.HOME, "Home", center, center.offset(3, 3, 3), 5, true);
        var depotArea = new SettlementArea(UUID.randomUUID(), SettlementAreaType.DEPOT, "Depot", center.east(4), center.offset(6, 3, 3), 5, true);
        town.putArea(leader.getUUID(), home);
        town.putArea(leader.getUUID(), depotArea);
        town.recordSurvey(home.id(), new SettlementBuildingSurvey.Survey(level.getGameTime(),
                List.of(center.offset(1, 1, 1)), List.of(), true));
        var depot = town.registerDepot(leader.getUUID(), depotArea.id(), depotArea.min(), SettlementDepotPurpose.GENERAL);
        depot.setItem(0, new ItemStack(Items.BREAD, 64));
        helper.assertFalse(level.hasChunkAt(center), "the remote town starts unloaded");
        var applicant = SettlementPopulationService.propose(level, town).orElseThrow();
        helper.assertTrue(SettlementPopulationService.approve(leader, id, applicant.id()), "approval can be queued remotely");
        helper.assertFalse(level.hasChunkAt(center), "approval does not force the town to load");
        helper.assertTrue(level.getEntity(applicant.id()) == null, "arrival waits for a safe loaded position");
        var restored = SettlementSavedData.load(SettlementSavedData.get(level).save(new CompoundTag(), level.registryAccess()),
                level.registryAccess()).get(id).orElseThrow();
        helper.assertTrue(restored.arrivals().containsKey(applicant.id()), "the pending world entity survives reload");
        helper.assertTrue(restored.residents().containsKey(applicant.id()), "the admitted resident receives background care");
        helper.assertTrue(town.applicants().isEmpty(), "completed admission consumes the application");
        helper.assertValueEqual(registry.residentRoster(id, level.getGameTime()).size(), 1, "the admitted resident occupies the vacant bed");
        helper.assertFalse(SettlementPopulationService.canGrow(level, town), "unloaded arrivals count toward housing capacity");
        town.transferLeadership(leader.getUUID(), UUID.randomUUID());
        SettlementArrivalService.update(level, town);
        helper.assertTrue(town.arrivals().containsKey(applicant.id()), "a completed admission survives a leadership transfer");
        helper.assertFalse(level.hasChunkAt(center), "reconciliation also leaves the remote town unloaded");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void familyFoodUsesRealStockAndNeedsRecoverAfterShortages(GameTestHelper helper) {
        var fixture = fixture(helper);
        var level = helper.getLevel();
        Villager resident = EntityType.VILLAGER.create(level);
        resident.setPos(fixture.leader().position());
        resident.setNoAi(true);
        level.addFreshEntity(resident);
        VillageAllegianceApi.assignKnown(level, resident, fixture.town().id(), AllegianceAssignmentSource.EXPLICIT_API);
        SettlementPopulationService.provideFamilyFood(level, fixture.town(), resident);
        helper.assertValueEqual(resident.getInventory().countItem(Items.BREAD), 3, "residents receive real food for ordinary family life");
        helper.assertValueEqual(fixture.depot().count(stack -> stack.is(Items.BREAD)), 61, "family provisions debit the depot");
        SettlementPopulationService.provideFamilyFood(level, fixture.town(), resident);
        helper.assertValueEqual(fixture.depot().count(stack -> stack.is(Items.BREAD)), 61, "adequate provisions are not duplicated");
        fixture.depot().clearContent();
        helper.assertValueEqual(VillagerBreedingPolicy.evaluateParent(level, resident).reason(), BreedingBlockReason.SETTLEMENT_NEEDS,
                "shortages stall growth without erasing family relationships");
        fixture.depot().setItem(0, new ItemStack(Items.BREAD, 64));
        helper.assertTrue(VillagerBreedingPolicy.evaluateParent(level, resident).allowed(), "restoring support permits family life again");
        resident.discard();
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void growthTargetNeverTruncatesAnExistingLargerCensus(GameTestHelper helper) {
        var fixture = fixture(helper);
        var registry = VillageAllegianceRegistrySavedData.get(helper.getLevel());
        for (int index = 0; index < 125; index++) {
            registry.addOrUpdateResident(fixture.town().id(), UUID.randomUUID(), true, helper.getLevel().getGameTime());
        }
        helper.assertFalse(SettlementPopulationService.canGrow(helper.getLevel(), fixture.town()), "automatic growth observes its population target");
        helper.assertTrue(SettlementPopulationService.propose(helper.getLevel(), fixture.town()).isEmpty(), "no applicant is created beyond the target");
        helper.assertValueEqual(registry.residentRoster(fixture.town().id(), helper.getLevel().getGameTime()).size(), 125,
                "existing larger settlements retain every resident");
        helper.succeed();
    }

    static Fixture fixture(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos center = helper.absolutePos(new BlockPos(1, 2, 1));
        ServerPlayer leader = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "PopulationLeader"));
        leader.setPos(center.getCenter());
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var id = registry.create(level.getGameTime(), level.dimension().location(), center, "");
        var town = SettlementSavedData.get(level).establish(registry, id, leader.getUUID(), center, level.getGameTime()).orElseThrow();
        var home = new SettlementArea(UUID.randomUUID(), SettlementAreaType.HOME, "Home",
                center.offset(-1, 0, -1), center.offset(5, 2, 5), 5, true);
        var depotArea = new SettlementArea(UUID.randomUUID(), SettlementAreaType.DEPOT, "Depot",
                center.above(3), center.offset(5, 5, 5), 5, true);
        town.putArea(leader.getUUID(), home);
        town.putArea(leader.getUUID(), depotArea);
        for (BlockPos pos : BlockPos.betweenClosed(home.min().below(), home.max().atY(home.min().getY() - 1))) {
            level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
        }
        for (BlockPos head : List.of(center.offset(1, 0, 2), center.offset(3, 0, 2))) {
            var bed = Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH);
            level.setBlock(head, bed.setValue(BedBlock.PART, BedPart.HEAD), 2);
            level.setBlock(head.south(), bed.setValue(BedBlock.PART, BedPart.FOOT), 2);
            level.getPoiManager().add(head, level.registryAccess().registryOrThrow(Registries.POINT_OF_INTEREST_TYPE)
                    .getHolderOrThrow(PoiTypes.HOME));
        }
        SettlementBuildingService.refresh(level, town, home);
        var depot = town.registerDepot(leader.getUUID(), depotArea.id(), depotArea.min(), SettlementDepotPurpose.GENERAL);
        depot.setItem(0, new ItemStack(Items.BREAD, 64));
        return new Fixture(town, home, depot, leader);
    }

    record Fixture(SettlementRecord town, SettlementArea home, SettlementDepot depot, ServerPlayer leader) {
    }
}
