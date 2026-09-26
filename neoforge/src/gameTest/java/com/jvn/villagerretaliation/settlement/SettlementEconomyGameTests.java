package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.AllegianceAssignmentSource;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceApi;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.villager.VillagerRecoveryService;
import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class SettlementEconomyGameTests {
    private SettlementEconomyGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void hungryResidentEatsRealTownFoodThroughNormalRecovery(GameTestHelper helper) {
        var fixture = fixture(helper);
        var resident = resident(helper, fixture);
        fixture.depot().setItem(0, new ItemStack(Items.BREAD));
        VillagerRecoveryService.restoreRecoveryState(resident, new VillagerRecoveryService.RecoverySnapshot(0, 0, 0, 0));
        helper.assertTrue(VillagerRecoveryService.onVillagerTickPost(resident), "a hungry resident starts eating");
        helper.assertTrue(fixture.depot().isEmpty(), "the meal is reserved from real stock before the animation");
        for (int tick = 0; tick < 31; tick++) {
            VillagerRecoveryService.onVillagerTickPost(resident);
        }
        helper.assertValueEqual(VillagerRecoveryService.foodLevel(resident), 5, "bread retains its normal nutrition");
        helper.assertFalse(VillagerRecoveryService.onVillagerTickPost(resident), "an empty depot cannot supply another meal");
        helper.assertTrue(fixture.depot().isEmpty(), "eating cannot recreate the withdrawn stock");
        resident.discard();
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void interruptedMealReturnsOneRealItemAndFoodDepotsRespectPurpose(GameTestHelper helper) {
        var fixture = fixture(helper);
        var resident = resident(helper, fixture);
        fixture.depot().setItem(0, new ItemStack(Items.BREAD));
        VillagerRecoveryService.restoreRecoveryState(resident, new VillagerRecoveryService.RecoverySnapshot(0, 0, 0, 0));
        VillagerRecoveryService.onVillagerTickPost(resident);
        VillagerRecoveryService.cancelForcedRecovery(resident);
        helper.assertValueEqual(resident.getInventory().countItem(Items.BREAD), 1, "cancelled eating returns the reserved meal");
        helper.assertTrue(fixture.depot().isEmpty(), "cancelling does not also refund the town ledger");
        fixture.depot().setItem(0, new ItemStack(Items.BREAD, 6));
        fixture.town().configureDepot(fixture.leader().getUUID(), fixture.depot().id(), SettlementDepotPurpose.OUTPUT, 5);
        helper.assertTrue(SettlementFoodService.takeMeal(resident).isEmpty(), "output-only depots cannot be consumed");
        helper.assertValueEqual(SettlementFoodService.nutrition(fixture.town()), 0L, "output stock is not a usable food reserve");
        resident.discard();
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void stockTransfersPreserveComponentsAndDoNotOverfill(GameTestHelper helper) {
        var fixture = fixture(helper);
        ItemStack named = new ItemStack(Items.BREAD, 9);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Baked today"));
        SettlementStock.deposit(fixture.town(), named);
        helper.assertValueEqual(named.getCount(), 9, "deposit does not mutate the caller's stack");
        ItemStack taken = SettlementStock.take(fixture.town(), SettlementFoodService::isMeal, 3);
        helper.assertValueEqual(taken.getHoverName().getString(), "Baked today", "stock transfers preserve item components");
        helper.assertValueEqual(SettlementFoodService.nutrition(fixture.town()), 30L, "food reserve counts remaining nutrition");
        for (int slot = 0; slot < fixture.depot().getContainerSize(); slot++) {
            fixture.depot().setItem(slot, new ItemStack(Items.STONE, 64));
        }
        helper.assertValueEqual(SettlementStock.capacity(fixture.town(), named), 0, "a full depot has no output capacity");
        helper.assertValueEqual(SettlementStock.deposit(fixture.town(), named).getCount(), 9, "full depots return all excess");
        helper.assertFalse(SettlementFoodService.isMeal(new ItemStack(Items.ROTTEN_FLESH)), "unsafe food is excluded from town meals");
        helper.assertFalse(SettlementFoodService.isMeal(new ItemStack(Items.GOLDEN_APPLE)), "emergency food is not consumed by town meals");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void settlementStarvationIsNonfatalButOtherDamageRemainsDangerous(GameTestHelper helper) {
        var fixture = fixture(helper);
        Villager resident = resident(helper, fixture);
        var server = helper.getLevel().getServer();
        Difficulty previousDifficulty = helper.getLevel().getDifficulty();
        boolean previousStarvation = VillagerRetaliationConfig.ENABLE_VILLAGER_STARVATION_DAMAGE.get();
        try {
            server.setDifficulty(Difficulty.HARD, true);
            VillagerRetaliationConfig.ENABLE_VILLAGER_STARVATION_DAMAGE.set(true);
            resident.setHealth(1.0F);
            VillagerRecoveryService.restoreRecoveryState(resident, new VillagerRecoveryService.RecoverySnapshot(0, 0, 0, 79));
            VillagerRecoveryService.onVillagerTickPost(resident);
            helper.assertTrue(resident.isAlive(), "care shortages cannot kill a resident");
            helper.assertValueEqual(resident.getHealth(), 1.0F, "starvation respects the nonfatal floor");
            resident.hurt(helper.getLevel().damageSources().genericKill(), 100.0F);
            helper.assertFalse(resident.isAlive(), "the starvation rule does not grant general invulnerability");
        } finally {
            server.setDifficulty(previousDifficulty, true);
            VillagerRetaliationConfig.ENABLE_VILLAGER_STARVATION_DAMAGE.set(previousStarvation);
            resident.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void advancementRequiresSustainedSupportAndExplicitLeaderAction(GameTestHelper helper) {
        var fixture = fixture(helper);
        var registry = VillageAllegianceRegistrySavedData.get(helper.getLevel());
        int population = SettlementStage.HAMLET.population();
        List<BlockPos> beds = new ArrayList<>();
        for (int index = 0; index < population; index++) {
            UUID resident = UUID.randomUUID();
            registry.addOrUpdateResident(fixture.town().id(), resident, true, helper.getLevel().getGameTime());
            if (index == 0) {
                fixture.town().assignDuty(resident, fixture.farm().id());
            }
            beds.add(fixture.home().min().offset((index % 3) * 2, 0, (index / 3) * 2 + 1));
        }
        for (BlockPos pos : BlockPos.betweenClosed(fixture.home().min().below(), fixture.home().max().atY(fixture.home().min().getY() - 1))) {
            helper.getLevel().setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
        }
        for (BlockPos head : beds) {
            placeBed(helper, head);
        }
        SettlementBuildingService.refresh(helper.getLevel(), fixture.town(), fixture.home());
        fixture.depot().setItem(0, new ItemStack(Items.BREAD, 64));
        var supported = SettlementProgressionService.assess(helper.getLevel(), fixture.town());
        helper.assertTrue(supported.missing().isEmpty(), "housing, food and a staffed food service meet the requirements");
        helper.assertFalse(supported.ready(), "requirements must remain supported over time");
        long duration = SettlementProgressionService.requiredTicks();
        for (long tick = 0L; tick <= duration; tick += 200L) {
            fixture.town().support().observe(tick, true, false);
        }
        helper.assertValueEqual(fixture.town().stage(), SettlementStage.CAMP, "support does not automatically advance a town");
        ServerPlayer stranger = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "TownVisitor"));
        helper.assertFalse(SettlementProgressionService.advance(stranger, fixture.town().id()), "a visitor cannot advance the town");
        helper.getLevel().removeBlock(beds.getFirst(), false);
        helper.assertFalse(SettlementProgressionService.advance(fixture.leader(), fixture.town().id()),
                "advancement rechecks real buildings before approving the stage");
        placeBed(helper, beds.getFirst());
        SettlementBuildingService.refresh(helper.getLevel(), fixture.town(), fixture.home());
        fixture.town().support().observe(0L, true, false);
        for (long tick = 200L; tick <= duration; tick += 200L) {
            fixture.town().support().observe(tick, true, false);
        }
        helper.assertTrue(SettlementProgressionService.advance(fixture.leader(), fixture.town().id()), "the leader can explicitly advance");
        helper.assertValueEqual(fixture.town().stage(), SettlementStage.HAMLET, "the approved stage unlocks");
        fixture.depot().clearContent();
        fixture.town().support().observe(duration + 200L, false, true);
        helper.assertValueEqual(fixture.town().stage(), SettlementStage.HAMLET, "later shortages do not downgrade the stage");
        helper.assertValueEqual(fixture.town().support().sustainedTicks(), 0L, "shortages interrupt advancement support");
        var restored = SettlementSavedData.load(SettlementSavedData.get(helper.getLevel())
                .save(new CompoundTag(), helper.getLevel().registryAccess()), helper.getLevel().registryAccess())
                .get(fixture.town().id()).orElseThrow();
        helper.assertValueEqual(restored.stage(), SettlementStage.HAMLET, "stage advancement survives reload");
        helper.assertValueEqual(restored.support().shortageTicks(), 200L, "shortage duration survives reload");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void functionalSurveysFindActualEquipmentAndRemoveBrokenStations(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        var kitchen = new SettlementArea(UUID.randomUUID(), SettlementAreaType.KITCHEN, "Kitchen",
                pos, pos.offset(3, 2, 3), 5, true);
        helper.assertTrue(SettlementBuildingSurvey.inspect(level, kitchen).workstations().isEmpty(),
                "an empty designated kitchen has no equipment");
        level.setBlockAndUpdate(pos, Blocks.FURNACE.defaultBlockState());
        helper.assertValueEqual(SettlementBuildingSurvey.inspect(level, kitchen).workstations(), List.of(pos),
                "a real furnace equips the kitchen even without a villager POI");
        level.setBlockAndUpdate(pos, Blocks.CAMPFIRE.defaultBlockState());
        helper.assertTrue(SettlementBuildingSurvey.inspect(level, kitchen).workstations().isEmpty(),
                "a kitchen cannot count equipment that the existing cook cannot operate");
        level.setBlockAndUpdate(pos, Blocks.CRAFTING_TABLE.defaultBlockState());
        helper.assertValueEqual(SettlementBuildingSurvey.inspect(level, kitchen).workstations(), List.of(pos),
                "a crafting table supports the cook's existing food recipes");
        var workshop = new SettlementArea(kitchen.id(), SettlementAreaType.WORKSHOP, "Workshop",
                kitchen.min(), kitchen.max(), 5, true);
        helper.assertValueEqual(SettlementBuildingSurvey.inspect(level, workshop).workstations(), List.of(pos),
                "the crafting table equips a workshop");
        level.removeBlock(pos, false);
        helper.assertTrue(SettlementBuildingSurvey.inspect(level, workshop).workstations().isEmpty(),
                "destroyed equipment is removed from the next survey");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void hungerAndMissingHousingReduceCivicOutputWithoutCancellingDuty(GameTestHelper helper) {
        var fixture = fixture(helper);
        var resident = resident(helper, fixture);
        fixture.town().assignDuty(resident.getUUID(), fixture.farm().id());
        VillagerRecoveryService.restoreRecoveryState(resident, new VillagerRecoveryService.RecoverySnapshot(0, 0, 0, 0));
        helper.assertTrue(SettlementFoodService.workMultiplier(helper.getLevel(), resident) < 0.5D,
                "severe hunger and homelessness reduce civic work output");
        fixture.town().recordSurvey(fixture.home().id(),
                new SettlementBuildingSurvey.Survey(0L, List.of(fixture.home().min()), List.of(), true));
        VillagerRecoveryService.restoreRecoveryState(resident, new VillagerRecoveryService.RecoverySnapshot(20, 5, 0, 0));
        helper.assertValueEqual(SettlementFoodService.workMultiplier(helper.getLevel(), resident), 1.0D,
                "restoring food and housing restores normal output");
        helper.assertTrue(fixture.town().duties().containsKey(resident.getUUID()), "shortages preserve the assignment");
        resident.discard();
        helper.succeed();
    }

    private static void placeBed(GameTestHelper helper, BlockPos head) {
        var bed = Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH);
        helper.getLevel().setBlock(head, bed.setValue(BedBlock.PART, BedPart.HEAD), 2);
        helper.getLevel().setBlock(head.south(), bed.setValue(BedBlock.PART, BedPart.FOOT), 2);
        helper.getLevel().getPoiManager().add(head, helper.getLevel().registryAccess()
                .registryOrThrow(Registries.POINT_OF_INTEREST_TYPE).getHolderOrThrow(PoiTypes.HOME));
    }

    private static Villager resident(GameTestHelper helper, Fixture fixture) {
        var villager = EntityType.VILLAGER.create(helper.getLevel());
        villager.setPos(fixture.leader().position());
        villager.setNoAi(true);
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.FARMER));
        helper.getLevel().addFreshEntity(villager);
        VillageAllegianceApi.assignKnown(helper.getLevel(), villager, fixture.town().id(), AllegianceAssignmentSource.EXPLICIT_API);
        return villager;
    }

    private static Fixture fixture(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos center = helper.absolutePos(new BlockPos(1, 2, 1));
        ServerPlayer leader = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "TownSteward"));
        leader.setPos(center.getX() + 0.5D, center.getY(), center.getZ() + 0.5D);
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var data = SettlementSavedData.get(level);
        var id = registry.create(level.getGameTime(), level.dimension().location(), center, "");
        var town = data.establish(registry, id, leader.getUUID(), center, level.getGameTime()).orElseThrow();
        var farm = new SettlementArea(UUID.randomUUID(), SettlementAreaType.FARM, "Farm",
                center, center.offset(5, 2, 5), 5, true);
        var home = new SettlementArea(UUID.randomUUID(), SettlementAreaType.HOME, "Home",
                center.above(3), center.offset(5, 6, 5), 5, true);
        data.putArea(registry, id, leader.getUUID(), farm);
        data.putArea(registry, id, leader.getUUID(), home);
        var depot = town.registerDepot(leader.getUUID(), farm.id(), center.offset(1, 0, 1), SettlementDepotPurpose.GENERAL);
        return new Fixture(town, farm, home, depot, leader);
    }

    private record Fixture(SettlementRecord town, SettlementArea farm, SettlementArea home,
                           SettlementDepot depot, ServerPlayer leader) {
    }
}
