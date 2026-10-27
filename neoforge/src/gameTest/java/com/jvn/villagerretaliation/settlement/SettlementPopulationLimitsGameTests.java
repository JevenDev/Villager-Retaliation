package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.AllegianceAssignmentSource;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceApi;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.network.SettlementManagementPayload;
import com.jvn.villagerretaliation.network.SettlementManagementRequestPayload;
import com.jvn.villagerretaliation.social.BreedingBlockReason;
import com.jvn.villagerretaliation.social.VillagerBreedingPolicy;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("settlement_population_limits")
@PrefixGameTestTemplate(false)
public final class SettlementPopulationLimitsGameTests {
    private SettlementPopulationLimitsGameTests() { }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void eachTierStopsAdmissionsAtItsLimitAndAdvancementUnlocksGrowth(GameTestHelper helper) {
        var fixture = supported(helper);
        var level = helper.getLevel();
        var town = fixture.town();
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var limits = List.of(5, 15, 35, 75, 100);
        for (var stage : SettlementStage.values()) {
            int target = limits.get(stage.ordinal());
            helper.assertValueEqual(town.stage(), stage, "the settlement reaches each tier in order");
            helper.assertValueEqual(SettlementPopulationService.growthTarget(level, town), target, "each tier has its own target");
            while (registry.residentCount(town.id(), level.getGameTime()) < target - 1) {
                registry.addOrUpdateResident(town.id(), UUID.randomUUID(), true, level.getGameTime());
            }
            helper.assertTrue(SettlementPopulationService.canGrow(level, town), "one remaining slot permits growth");
            var applicant = SettlementPopulationService.propose(level, town).orElseThrow();
            helper.assertTrue(SettlementPopulationService.propose(level, town).isEmpty(), "pending applications reserve the last slot");
            var approved = applicant.approve(town.leader());
            town.putApplicant(approved);
            helper.assertTrue(SettlementPopulationService.arrive(level, town, approved), "the final arrival reaches the tier limit");
            helper.assertValueEqual(registry.residentCount(town.id(), level.getGameTime()), target, "admission counts the resident immediately");
            helper.assertFalse(SettlementPopulationService.canGrow(level, town), "spare beds and food cannot exceed the tier limit");
            helper.assertTrue(SettlementPopulationService.propose(level, town).isEmpty(), "full tiers cannot generate more applicants");
            var lateId = UUID.randomUUID();
            var lateEntity = applicant.entity();
            lateEntity.putUUID("UUID", lateId);
            var late = new SettlementApplicant(lateId, "Waiting", level.getGameTime(), lateEntity, town.leader());
            town.putApplicant(late);
            helper.assertFalse(SettlementPopulationService.arrive(level, town, late), "previously approved applicants must recheck the tier limit");
            helper.assertTrue(town.applicants().containsKey(late.id()), "waiting applicants are preserved");
            town.removeApplicant(late.id());
            var snapshot = SettlementManagementService.snapshot(fixture.leader(), new SettlementManagementRequestPayload(
                    town.id().value(), SettlementManagementRequestPayload.Page.SUMMARY, 0, "", 1, false));
            helper.assertValueEqual(snapshot.header().growthTarget(), target, "the status bar receives the enforced limit");
            var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess());
            try {
                SettlementManagementPayload.STREAM_CODEC.encode(buffer, snapshot);
                helper.assertValueEqual(SettlementManagementPayload.STREAM_CODEC.decode(buffer), snapshot, "tier limits survive the management packet");
            } finally {
                buffer.release();
            }
            if (stage.next().isPresent()) {
                helper.assertValueEqual(target, stage.next().orElseThrow().population(), "the cap still permits the next advancement requirement");
                town.advance();
                helper.assertTrue(SettlementPopulationService.canGrow(level, town), "advancement unlocks additional population");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_population_limits_config")
    public static void tierLimitsFollowEffectiveThresholdsAndHonorALowerServerCeiling(GameTestHelper helper) {
        var config = VillagerRetaliationConfig.CONFIG;
        var options = List.of("hamletPopulation", "villagePopulation", "townPopulation", "cityPopulation", "populationTarget")
                .stream().map(key -> config.<Integer>optionForKey(new io.wispforest.owo.config.Option.Key("settlements." + key))).toList();
        var saved = options.stream().map(io.wispforest.owo.config.Option::value).toList();
        try {
            for (int index = 0; index < 4; index++) options.get(index).set((index + 1) * 2);
            options.get(4).set(7);
            var limits = List.of(2, 4, 6, 7, 7);
            for (var stage : SettlementStage.values()) {
                helper.assertValueEqual(SettlementPopulationRequirements.growthTarget(stage), limits.get(stage.ordinal()),
                        "custom tier requirements retain the overall server ceiling");
            }
            helper.assertFalse(SettlementPopulationRequirements.capped(SettlementStage.CAMP, 0), "the normal tier cap permits advancement");
            helper.assertTrue(SettlementPopulationRequirements.capped(SettlementStage.TOWN, 6), "a deliberately low server ceiling still warns");
            for (int index = 0; index < 4; index++) options.get(index).set(2);
            options.get(4).set(100);
            helper.assertValueEqual(SettlementPopulationRequirements.growthTarget(SettlementStage.HAMLET), 3,
                    "growth follows adjusted requirements instead of an unreachable raw threshold");
        } finally {
            for (int index = 0; index < options.size(); index++) options.get(index).set(saved.get(index));
            SettlementPopulationRequirements.effective();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void existingOverLimitSettlementsKeepTheirResidentsAcrossReload(GameTestHelper helper) {
        var fixture = supported(helper);
        var level = helper.getLevel();
        var town = fixture.town();
        var registry = VillageAllegianceRegistrySavedData.get(level);
        for (int index = 0; index < 6; index++) {
            registry.addOrUpdateResident(town.id(), UUID.randomUUID(), true, level.getGameTime());
        }
        helper.assertFalse(SettlementPopulationService.canGrow(level, town), "a six resident camp stops growing");
        var restored = SettlementSavedData.load(SettlementSavedData.get(level).save(new CompoundTag(), level.registryAccess()),
                level.registryAccess()).get(town.id()).orElseThrow();
        helper.assertFalse(SettlementPopulationService.canGrow(level, restored), "reload preserves the tier limit");
        helper.assertValueEqual(registry.residentCount(town.id(), level.getGameTime()), 6, "lowering the limit never removes existing residents");
        restored.advance();
        helper.assertTrue(SettlementPopulationService.canGrow(level, restored), "an overfull camp can grow again after advancement");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void familyGrowthUsesTheSameTierLimitAsImmigration(GameTestHelper helper) {
        var fixture = supported(helper);
        var level = helper.getLevel();
        var town = fixture.town();
        var resident = EntityType.VILLAGER.create(level);
        resident.setPos(fixture.leader().position());
        resident.setNoAi(true);
        level.addFreshEntity(resident);
        VillageAllegianceApi.assignKnown(level, resident, town.id(), AllegianceAssignmentSource.EXPLICIT_API);
        var registry = VillageAllegianceRegistrySavedData.get(level);
        for (int index = 1; index < 5; index++) {
            registry.addOrUpdateResident(town.id(), UUID.randomUUID(), true, level.getGameTime());
        }
        helper.assertValueEqual(VillagerBreedingPolicy.evaluateProvision(level, resident).reason(), BreedingBlockReason.SETTLEMENT_NEEDS,
                "family growth is blocked at the tier limit despite surplus support");
        SettlementPopulationService.provideFamilyFood(level, town, resident);
        helper.assertValueEqual(resident.getInventory().countItem(Items.BREAD), 0, "a full tier does not distribute new breeding provisions");
        town.advance();
        helper.assertTrue(VillagerBreedingPolicy.evaluateProvision(level, resident).allowed(), "the next tier permits family growth again");
        SettlementPopulationService.provideFamilyFood(level, town, resident);
        helper.assertValueEqual(resident.getInventory().countItem(Items.BREAD), 3, "advancement restores family provisions");
        resident.discard();
        helper.succeed();
    }

    private static SettlementPopulationGameTests.Fixture supported(GameTestHelper helper) {
        var fixture = SettlementPopulationGameTests.fixture(helper);
        var beds = BlockPos.betweenClosedStream(fixture.home().min(), fixture.home().max()).limit(101).map(BlockPos::immutable).toList();
        fixture.town().recordSurvey(fixture.home().id(), new SettlementBuildingSurvey.Survey(helper.getLevel().getGameTime(), beds, List.of(), true));
        for (int slot = 0; slot < fixture.depot().getContainerSize(); slot++) fixture.depot().setItem(slot, new ItemStack(Items.BREAD, 64));
        return fixture;
    }
}
