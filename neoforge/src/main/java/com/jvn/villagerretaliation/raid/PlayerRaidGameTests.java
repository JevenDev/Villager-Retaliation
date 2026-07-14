package com.jvn.villagerretaliation.raid;

import com.jvn.villagerretaliation.allegiance.AllegianceAssignmentSource;
import com.jvn.villagerretaliation.allegiance.AllegianceCombatContext;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceApi;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceCombatPolicy;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import com.jvn.villagerretaliation.combat.VillagerRetaliationHandler;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class PlayerRaidGameTests {
    private static final String EMPTY_TEMPLATE = "empty";

    private PlayerRaidGameTests() {
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void raidSnapshotRoundTrips(GameTestHelper helper) {
        PlayerRaidSavedData data = new PlayerRaidSavedData();
        VillageAllegianceId village = VillageAllegianceId.random();
        UUID player = UUID.randomUUID();
        UUID raiderVillager = UUID.randomUUID();
        UUID defender = UUID.randomUUID();
        UUID defector = UUID.randomUUID();
        PlayerRaidSavedData.RaidRecord raid = data.create(
                village, helper.getLevel().dimension().location(), new BlockPos(4, 2, 4),
                Set.of(SectionPos.asLong(new BlockPos(4, 2, 4))), "Test Village", player, UUID.randomUUID(),
                Set.of(player), Set.of(raiderVillager), Set.of(defender, defector), Set.of(defector), 42L);
        raid.setPhase(PlayerRaidSavedData.Phase.ACTIVE, 242L);
        raid.setGolemBudget(4);
        raid.addSpawnedGolems(1);
        CompoundTag saved = data.save(new CompoundTag(), helper.getLevel().registryAccess());
        PlayerRaidSavedData loaded = PlayerRaidSavedData.load(saved, helper.getLevel().registryAccess());
        PlayerRaidSavedData.RaidRecord restored = loaded.raid(raid.id());
        helper.assertTrue(restored != null, "raid identity should survive save/load");
        helper.assertValueEqual(restored.villageId(), village, "village id");
        helper.assertValueEqual(restored.phase(), PlayerRaidSavedData.Phase.ACTIVE, "active phase");
        helper.assertValueEqual(restored.raiderPlayers(), Set.of(player), "snapshotted players");
        helper.assertValueEqual(restored.defenders(), Set.of(defender, defector), "snapshotted defenders");
        helper.assertValueEqual(restored.defectors(), Set.of(defector), "defector set");
        helper.assertValueEqual(restored.initialDefenderCount(), 2, "initial objective count");
        helper.assertValueEqual(restored.golemBudget(), 4, "fixed golem budget");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void betrayalPenaltyAndGolemBudgetAreBounded(GameTestHelper helper) {
        helper.assertValueEqual(PlayerRaidService.betrayalReputation(100), -250, "positive reputation floors at -250");
        helper.assertValueEqual(PlayerRaidService.betrayalReputation(-250), -250, "exact floor is stable");
        helper.assertValueEqual(PlayerRaidService.betrayalReputation(-400), -650, "worse reputation loses another 250");
        helper.assertValueEqual(PlayerRaidService.calculateGolemBudget(17, 5, 1, 8, 2, 1, 6), 4,
                "ceil defenders plus raider bonus less existing golems");
        helper.assertValueEqual(PlayerRaidService.calculateGolemBudget(1, 1, 6, 8, 2, 1, 6), 0,
                "existing aligned golems can exhaust the fixed budget");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void debugOutcomesSettleRunningRaids(GameTestHelper helper) {
        PlayerRaidSavedData data = PlayerRaidSavedData.get(helper.getLevel());
        UUID player = UUID.randomUUID();
        BlockPos center = helper.absolutePos(new BlockPos(4, 2, 4));
        PlayerRaidSavedData.RaidRecord won = data.create(
                VillageAllegianceId.random(), helper.getLevel().dimension().location(), center,
                Set.of(SectionPos.asLong(center)), "Debug Win Village", player, null,
                Set.of(player), Set.of(), Set.of(UUID.randomUUID()), Set.of(), 42L);
        won.setPhase(PlayerRaidSavedData.Phase.ACTIVE, 43L);
        helper.assertValueEqual(
                PlayerRaidService.debugFinishRaid(helper.getLevel(), center, player, true), won,
                "participant raid selected for debug win");
        helper.assertValueEqual(won.phase(), PlayerRaidSavedData.Phase.RAIDER_VICTORY, "debug win outcome");

        UUID secondPlayer = UUID.randomUUID();
        PlayerRaidSavedData.RaidRecord lost = data.create(
                VillageAllegianceId.random(), helper.getLevel().dimension().location(), center,
                Set.of(SectionPos.asLong(center)), "Debug Lose Village", secondPlayer, null,
                Set.of(secondPlayer), Set.of(), Set.of(UUID.randomUUID()), Set.of(), 44L);
        lost.setPhase(PlayerRaidSavedData.Phase.ACTIVE, 45L);
        helper.assertValueEqual(
                PlayerRaidService.debugFinishRaid(helper.getLevel(), center, secondPlayer, false), lost,
                "participant raid selected for debug loss");
        helper.assertValueEqual(lost.phase(), PlayerRaidSavedData.Phase.DEFENDER_VICTORY, "debug loss outcome");
        data.remove(won.id());
        data.remove(lost.id());
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void activeRaiderHornRevealsTrackedInvisibleDefender(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos center = helper.absolutePos(new BlockPos(4, 2, 4));
        player.moveTo(center.getX() + 0.5D, center.getY(), center.getZ() + 0.5D, 0.0F, 0.0F);
        Villager defender = EntityType.VILLAGER.create(helper.getLevel());
        helper.assertTrue(defender != null, "villager should be creatable");
        defender.moveTo(center.getX() + 2.5D, center.getY(), center.getZ() + 0.5D, 0.0F, 0.0F);
        defender.setInvisible(true);
        helper.assertTrue(helper.getLevel().addFreshEntity(defender), "villager should spawn");

        PlayerRaidSavedData data = PlayerRaidSavedData.get(helper.getLevel());
        PlayerRaidSavedData.RaidRecord raid = data.create(
                VillageAllegianceId.random(), helper.getLevel().dimension().location(), center,
                Set.of(SectionPos.asLong(center)), "Reveal Village", player.getUUID(), null,
                Set.of(player.getUUID()), Set.of(), Set.of(defender.getUUID()), Set.of(), 42L);
        raid.setPhase(PlayerRaidSavedData.Phase.ACTIVE, 43L);
        helper.assertTrue(PlayerRaidService.tryRevealDefenders(player), "active raider horn should be handled");
        helper.assertTrue(defender.hasEffect(MobEffects.GLOWING), "tracked invisible defender should glow");
        data.remove(raid.id());
        defender.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void activePlayerRaidSidesRemainLegalCombatOpponents(GameTestHelper helper) {
        BlockPos center = helper.absolutePos(new BlockPos(4, 2, 4));
        Villager raider = EntityType.VILLAGER.create(helper.getLevel());
        Villager defender = EntityType.VILLAGER.create(helper.getLevel());
        helper.assertTrue(raider != null && defender != null, "raid villagers should be creatable");
        raider.moveTo(center.getX() - 1.5D, center.getY(), center.getZ() + 0.5D, 0.0F, 0.0F);
        defender.moveTo(center.getX() + 1.5D, center.getY(), center.getZ() + 0.5D, 0.0F, 0.0F);
        helper.assertTrue(helper.getLevel().addFreshEntity(raider), "raider should spawn");
        helper.assertTrue(helper.getLevel().addFreshEntity(defender), "defender should spawn");

        PlayerRaidSavedData data = PlayerRaidSavedData.get(helper.getLevel());
        UUID player = UUID.randomUUID();
        PlayerRaidSavedData.RaidRecord raid = data.create(
                VillageAllegianceId.random(), helper.getLevel().dimension().location(), center,
                Set.of(SectionPos.asLong(center)), "Combat Policy Village", player, null,
                Set.of(player), Set.of(raider.getUUID()), Set.of(defender.getUUID()), Set.of(), 42L);
        raid.setPhase(PlayerRaidSavedData.Phase.ACTIVE, 43L);
        helper.assertTrue(PlayerRaidService.areOpposingParticipants(raider, defender),
                "snapshotted raid sides should be recognized as opponents");
        helper.assertFalse(VillageAllegianceCombatPolicy.evaluate(
                        helper.getLevel(), raider, defender, AllegianceCombatContext.PARTY_ATTACK, false).denied(),
                "allegiance policy should not reject combat between Player Raid sides");
        data.remove(raid.id());
        raider.discard();
        defender.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void activePlayerRaidTargetsAlignedIronGolems(GameTestHelper helper) {
        BlockPos center = helper.absolutePos(new BlockPos(4, 2, 4));
        Villager raider = EntityType.VILLAGER.create(helper.getLevel());
        Villager defender = EntityType.VILLAGER.create(helper.getLevel());
        IronGolem golem = EntityType.IRON_GOLEM.create(helper.getLevel());
        helper.assertTrue(raider != null && defender != null && golem != null,
                "raid combatants should be creatable");
        raider.moveTo(center.getX() - 1.5D, center.getY(), center.getZ() + 0.5D, 0.0F, 0.0F);
        golem.moveTo(center.getX() + 0.5D, center.getY(), center.getZ() + 0.5D, 0.0F, 0.0F);
        defender.moveTo(center.getX() + 5.5D, center.getY(), center.getZ() + 0.5D, 0.0F, 0.0F);
        helper.assertTrue(helper.getLevel().addFreshEntity(raider), "raider should spawn");
        helper.assertTrue(helper.getLevel().addFreshEntity(defender), "defender should spawn");
        helper.assertTrue(helper.getLevel().addFreshEntity(golem), "golem should spawn");

        VillageAllegianceId village = VillageAllegianceId.random();
        VillageAllegianceApi.assignKnown(
                helper.getLevel(), golem, village, AllegianceAssignmentSource.EXPLICIT_API);
        PlayerRaidSavedData data = PlayerRaidSavedData.get(helper.getLevel());
        UUID player = UUID.randomUUID();
        PlayerRaidSavedData.RaidRecord raid = data.create(
                village, helper.getLevel().dimension().location(), center,
                Set.of(SectionPos.asLong(center)), "Golem Defense Village", player, UUID.randomUUID(),
                Set.of(player), Set.of(raider.getUUID()), Set.of(defender.getUUID()), Set.of(), 42L);
        raid.setPhase(PlayerRaidSavedData.Phase.ACTIVE, 43L);

        helper.assertTrue(PlayerRaidService.areOpposingParticipants(raider, golem),
                "an aligned village golem should count as a Player Raid defender");
        PlayerRaidService.reconcileCombat(helper.getLevel().getServer(), raid);
        helper.assertTrue(VillagerRetaliationHandler.hasRetaliationTarget(raider, golem),
                "a raiding party villager should target the nearest aligned village golem");

        data.remove(raid.id());
        raider.discard();
        defender.discard();
        golem.discard();
        helper.succeed();
    }
}
