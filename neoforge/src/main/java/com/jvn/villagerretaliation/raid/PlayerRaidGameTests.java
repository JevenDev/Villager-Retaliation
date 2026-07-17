package com.jvn.villagerretaliation.raid;

import com.jvn.villagerretaliation.allegiance.AllegianceAssignmentSource;
import com.jvn.villagerretaliation.allegiance.AllegianceCombatContext;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceApi;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceCombatPolicy;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.combat.VillagerRetaliationHandler;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.reputation.VillagerReputationManager;
import com.jvn.villagerretaliation.village.VillagerRaidMemorySavedData;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
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
        UUID mercyCandidate = UUID.randomUUID();
        UUID defector = UUID.randomUUID();
        PlayerRaidSavedData.RaidRecord raid = data.create(
                village, helper.getLevel().dimension().location(), new BlockPos(4, 2, 4),
                Set.of(SectionPos.asLong(new BlockPos(4, 2, 4))), "Test Village", player, UUID.randomUUID(),
                Set.of(player), Set.of(raiderVillager), Set.of(defender, defector),
                Set.of(mercyCandidate), Set.of(mercyCandidate), Set.of(defector), 42L);
        raid.setPhase(PlayerRaidSavedData.Phase.MERCY, 242L);
        raid.setGolemBudget(4);
        raid.addSpawnedGolems(1);
        raid.setNextMercyPleaAt(mercyCandidate, 900L);
        raid.setNextRaidMercyPleaAt(700L);
        CompoundTag saved = data.save(new CompoundTag(), helper.getLevel().registryAccess());
        PlayerRaidSavedData loaded = PlayerRaidSavedData.load(saved, helper.getLevel().registryAccess());
        PlayerRaidSavedData.RaidRecord restored = loaded.raid(raid.id());
        helper.assertTrue(restored != null, "raid identity should survive save/load");
        helper.assertValueEqual(restored.villageId(), village, "village id");
        helper.assertValueEqual(restored.phase(), PlayerRaidSavedData.Phase.MERCY, "mercy phase");
        helper.assertValueEqual(restored.raiderPlayers(), Set.of(player), "snapshotted players");
        helper.assertValueEqual(restored.defenders(), Set.of(defender, defector), "snapshotted defenders");
        helper.assertValueEqual(restored.defectors(), Set.of(defector), "defector set");
        helper.assertValueEqual(restored.initialDefenderCount(), 2, "initial objective count");
        helper.assertValueEqual(restored.mercyCandidates(), Set.of(mercyCandidate), "mercy candidate set");
        helper.assertValueEqual(restored.mercyKind(mercyCandidate), PlayerRaidSavedData.MercyKind.BABY, "mercy kind");
        helper.assertValueEqual(restored.initialMercyCandidateCount(), 1, "initial mercy count");
        helper.assertValueEqual(restored.nextMercyPleaAt(mercyCandidate), 900L, "candidate plea cooldown");
        helper.assertValueEqual(restored.nextRaidMercyPleaAt(), 700L, "raid plea cooldown");
        helper.assertTrue(restored.mercyEnabled(), "new raid snapshots should retain mercy behavior");
        helper.assertValueEqual(restored.golemBudget(), 4, "fixed golem budget");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void partyRaidMemoryRoundTripsAndVictoryCanOnlyBeClaimedOnce(GameTestHelper helper) {
        VillagerRaidMemorySavedData data = new VillagerRaidMemorySavedData();
        UUID villager = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        data.remember(villager, player, VillagerRaidMemorySavedData.RaidOutcome.VICTORY, 42L);

        CompoundTag saved = data.save(new CompoundTag(), helper.getLevel().registryAccess());
        VillagerRaidMemorySavedData loaded = VillagerRaidMemorySavedData.load(
                saved, helper.getLevel().registryAccess());

        helper.assertTrue(loaded.hasUnacknowledgedVictory(villager, player),
                "victory acknowledgement should survive save/load");
        helper.assertValueEqual(
                loaded.memory(villager, player).orElseThrow().outcome(),
                VillagerRaidMemorySavedData.RaidOutcome.VICTORY,
                "saved raid outcome");
        helper.assertTrue(loaded.claimVictoryAcknowledgement(villager, player),
                "first acknowledgement should claim the pending victory");
        helper.assertFalse(loaded.claimVictoryAcknowledgement(villager, player),
                "victory acknowledgement should only be claimable once");

        loaded.remember(villager, player, VillagerRaidMemorySavedData.RaidOutcome.LOSS, 84L);
        helper.assertValueEqual(
                loaded.memory(villager, player).orElseThrow().outcome(),
                VillagerRaidMemorySavedData.RaidOutcome.LOSS,
                "latest shared raid outcome");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void legacyRaidSnapshotDoesNotGainMercyCandidates(GameTestHelper helper) {
        PlayerRaidSavedData data = new PlayerRaidSavedData();
        UUID player = UUID.randomUUID();
        UUID defender = UUID.randomUUID();
        PlayerRaidSavedData.RaidRecord raid = data.create(
                VillageAllegianceId.random(), helper.getLevel().dimension().location(), new BlockPos(4, 2, 4),
                Set.of(SectionPos.asLong(new BlockPos(4, 2, 4))), "Legacy Village", player, null,
                Set.of(player), Set.of(), Set.of(defender), Set.of(), 42L);
        CompoundTag saved = data.save(new CompoundTag(), helper.getLevel().registryAccess());
        saved.putInt("FormatVersion", 1);
        CompoundTag legacyRaid = saved.getList("Raids", net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0);
        legacyRaid.remove("MercyEnabled");
        legacyRaid.remove("MercyCandidates");
        legacyRaid.remove("BabyMercyCandidates");
        legacyRaid.remove("InitialMercyCandidates");
        legacyRaid.remove("MercyPleaCooldowns");
        legacyRaid.remove("NextRaidMercyPleaAt");
        PlayerRaidSavedData.RaidRecord restored = PlayerRaidSavedData
                .load(saved, helper.getLevel().registryAccess())
                .raid(raid.id());
        helper.assertTrue(restored != null, "legacy raid should load");
        helper.assertFalse(restored.mercyEnabled(), "legacy raid should retain its original lifecycle");
        helper.assertTrue(restored.mercyCandidates().isEmpty(), "legacy raid should not invent candidates");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void mercyVerdictsAreServerAuthoritativeAndSpareEveryRaider(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        UUID otherRaider = UUID.randomUUID();
        BlockPos center = helper.absolutePos(new BlockPos(4, 2, 4));
        player.moveTo(center.getX() + 0.5D, center.getY(), center.getZ() + 0.5D, 0.0F, 0.0F);
        Villager candidate = EntityType.VILLAGER.create(helper.getLevel());
        helper.assertTrue(candidate != null, "mercy candidate should be creatable");
        candidate.moveTo(center.getX() + 1.5D, center.getY(), center.getZ() + 0.5D, 0.0F, 0.0F);
        candidate.setBaby(true);
        helper.assertTrue(helper.getLevel().addFreshEntity(candidate), "mercy candidate should spawn");

        PlayerRaidSavedData data = PlayerRaidSavedData.get(helper.getLevel());
        PlayerRaidSavedData.RaidRecord raid = data.create(
                VillageAllegianceId.random(), helper.getLevel().dimension().location(), center,
                Set.of(SectionPos.asLong(center)), "Mercy Village", player.getUUID(), null,
                Set.of(player.getUUID(), otherRaider), Set.of(), Set.of(),
                Set.of(candidate.getUUID()), Set.of(candidate.getUUID()), Set.of(), 42L);
        raid.setPhase(PlayerRaidSavedData.Phase.MERCY, 43L);
        candidate.setBaby(false);
        helper.assertValueEqual(raid.mercyKind(candidate.getUUID()), PlayerRaidSavedData.MercyKind.BABY,
                "matured child should retain snapshotted mercy kind");
        VillagerReputationManager.setReputation(helper.getLevel(), candidate, player.getUUID(), 125);
        VillagerReputationManager.setReputation(helper.getLevel(), candidate, otherRaider, 250);

        helper.assertTrue(PlayerRaidMercyService.shouldHandleInteraction(
                candidate, player, InteractionHand.MAIN_HAND), "raider should be authorized to open mercy verdict");
        helper.assertValueEqual(PlayerRaidMercyService.openVerdict(player, candidate), InteractionResult.CONSUME,
                "mercy verdict should open");
        PlayerRaidMercyService.handleDialogueRequest(
                player, candidate.getId() + 1, PlayerRaidMercyService.SPARE_OPTION_ID);
        helper.assertTrue(raid.mercyCandidates().contains(candidate.getUUID()),
                "spoofed entity id should not resolve a candidate");
        PlayerRaidMercyService.handleDialogueRequest(player, candidate.getId(), PlayerRaidMercyService.KILL_OPTION_ID);
        helper.assertTrue(candidate.isAlive(), "Kill option should wait for a manual attack");
        helper.assertTrue(raid.mercyCandidates().contains(candidate.getUUID()), "Kill should leave candidate unresolved");

        helper.assertValueEqual(PlayerRaidMercyService.openVerdict(player, candidate), InteractionResult.CONSUME,
                "candidate should remain reconsiderable");
        PlayerRaidMercyService.handleDialogueRequest(player, candidate.getId(), PlayerRaidMercyService.SILENCE_OPTION_ID);
        helper.assertTrue(raid.mercyCandidates().contains(candidate.getUUID()), "silence should leave candidate unresolved");

        helper.assertValueEqual(PlayerRaidMercyService.openVerdict(player, candidate), InteractionResult.CONSUME,
                "candidate should reopen for sparing");
        PlayerRaidMercyService.handleDialogueRequest(player, candidate.getId(), PlayerRaidMercyService.SPARE_OPTION_ID);
        helper.assertTrue(candidate.isAlive(), "spared candidate should remain alive");
        helper.assertFalse(raid.mercyCandidates().contains(candidate.getUUID()), "spare should resolve candidate once");
        helper.assertValueEqual(
                VillagerReputationManager.getReputation(helper.getLevel(), candidate, player.getUUID()),
                -1000,
                "chooser reputation");
        helper.assertValueEqual(
                VillagerReputationManager.getReputation(helper.getLevel(), candidate, otherRaider),
                -1000,
                "offline raider reputation");
        helper.assertValueEqual(raid.phase(), PlayerRaidSavedData.Phase.RAIDER_VICTORY,
                "last spared candidate should complete the raid");
        data.remove(raid.id());
        candidate.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void emptyDefenderObjectiveEntersMercyOrCompletesVictory(GameTestHelper helper) {
        PlayerRaidSavedData data = PlayerRaidSavedData.get(helper.getLevel());
        UUID player = UUID.randomUUID();
        UUID candidate = UUID.randomUUID();
        BlockPos center = helper.absolutePos(new BlockPos(4, 2, 4));
        PlayerRaidSavedData.RaidRecord mercyRaid = data.create(
                VillageAllegianceId.random(), helper.getLevel().dimension().location(), center,
                Set.of(SectionPos.asLong(center)), "Candidate-only Village", player, null,
                Set.of(player), Set.of(), Set.of(), Set.of(candidate), Set.of(), Set.of(), 42L);
        mercyRaid.setPhase(PlayerRaidSavedData.Phase.ACTIVE, 43L);
        helper.assertTrue(PlayerRaidService.resolveDefenderObjective(
                        helper.getLevel().getServer(), data, mercyRaid, 44L),
                "empty defender objective should resolve");
        helper.assertValueEqual(mercyRaid.phase(), PlayerRaidSavedData.Phase.MERCY,
                "candidate-only raid should enter mercy");
        helper.assertValueEqual(mercyRaid.golemBudget(), 0, "candidate-only raid should not budget golems");
        helper.assertTrue(
                mercyRaid.nextMercyPleaAt(candidate) >= 44L + 20L * 30L
                        && mercyRaid.nextMercyPleaAt(candidate) <= 44L + 20L * 60L,
                "entering mercy should persist an initial 30-60 second candidate cooldown");

        UUID secondPlayer = UUID.randomUUID();
        PlayerRaidSavedData.RaidRecord immediateVictory = data.create(
                VillageAllegianceId.random(), helper.getLevel().dimension().location(), center,
                Set.of(SectionPos.asLong(center)), "Empty Village", secondPlayer, null,
                Set.of(secondPlayer), Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), 45L);
        immediateVictory.setPhase(PlayerRaidSavedData.Phase.ACTIVE, 46L);
        helper.assertTrue(PlayerRaidService.resolveDefenderObjective(
                        helper.getLevel().getServer(), data, immediateVictory, 47L),
                "empty raid objective should resolve");
        helper.assertValueEqual(immediateVictory.phase(), PlayerRaidSavedData.Phase.RAIDER_VICTORY,
                "raid with no candidates should complete immediately");
        data.remove(mercyRaid.id());
        data.remove(immediateVictory.id());
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void residentClassificationSnapshotsBabyAndNitwitCandidates(GameTestHelper helper) {
        Set<UUID> defenders = new LinkedHashSet<>();
        Set<UUID> candidates = new LinkedHashSet<>();
        Set<UUID> babyCandidates = new LinkedHashSet<>();
        Villager baby = EntityType.VILLAGER.create(helper.getLevel());
        Villager loadedNitwit = EntityType.VILLAGER.create(helper.getLevel());
        helper.assertTrue(baby != null && loadedNitwit != null, "classification villagers should be creatable");
        baby.setBaby(true);
        loadedNitwit.setVillagerData(loadedNitwit.getVillagerData().setProfession(VillagerProfession.NITWIT));
        PlayerRaidService.classifyResident(baby, defenders, candidates, babyCandidates);
        PlayerRaidService.classifyResident(loadedNitwit, defenders, candidates, babyCandidates);

        UUID unloadedNitwitDefector = UUID.randomUUID();
        UUID unloadedAdult = UUID.randomUUID();
        PlayerRaidService.classifyResident(
                new VillageAllegianceRegistrySavedData.ResidentRecord(unloadedNitwitDefector, true, true, 10L),
                defenders, candidates, babyCandidates);
        PlayerRaidService.classifyResident(
                new VillageAllegianceRegistrySavedData.ResidentRecord(unloadedAdult, true, false, 10L),
                defenders, candidates, babyCandidates);
        baby.setBaby(false);

        helper.assertTrue(candidates.contains(baby.getUUID()) && babyCandidates.contains(baby.getUUID()),
                "baby eligibility should be snapshotted even after maturation");
        helper.assertTrue(candidates.contains(loadedNitwit.getUUID()),
                "loaded-entity backstop should classify a nitwit as a mercy candidate");
        helper.assertTrue(candidates.contains(unloadedNitwitDefector),
                "unloaded nitwit defectors should remain mercy candidates from roster data");
        helper.assertValueEqual(defenders, Set.of(unloadedAdult),
                "only combat-capable adults should enter the defender objective");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void lastDefenderDeathEntersMercyAndManualCandidateDeathWins(GameTestHelper helper) {
        BlockPos center = helper.absolutePos(new BlockPos(4, 2, 4));
        Villager defender = EntityType.VILLAGER.create(helper.getLevel());
        Villager candidate = EntityType.VILLAGER.create(helper.getLevel());
        helper.assertTrue(defender != null && candidate != null, "raid villagers should be creatable");
        defender.moveTo(center.getX() - 1.0D, center.getY(), center.getZ(), 0.0F, 0.0F);
        candidate.moveTo(center.getX() + 1.0D, center.getY(), center.getZ(), 0.0F, 0.0F);
        helper.assertTrue(helper.getLevel().addFreshEntity(defender), "defender should spawn");
        helper.assertTrue(helper.getLevel().addFreshEntity(candidate), "candidate should spawn");

        PlayerRaidSavedData data = PlayerRaidSavedData.get(helper.getLevel());
        UUID player = UUID.randomUUID();
        PlayerRaidSavedData.RaidRecord raid = data.create(
                VillageAllegianceId.random(), helper.getLevel().dimension().location(), center,
                Set.of(SectionPos.asLong(center)), "Death Village", player, null,
                Set.of(player), Set.of(), Set.of(defender.getUUID()), Set.of(candidate.getUUID()),
                Set.of(), Set.of(), 42L);
        raid.setPhase(PlayerRaidSavedData.Phase.ACTIVE, 43L);

        PlayerRaidService.onLivingDeath(new LivingDeathEvent(defender, defender.damageSources().generic()));
        helper.assertValueEqual(raid.phase(), PlayerRaidSavedData.Phase.MERCY,
                "the last combat defender death should enter mercy immediately");
        helper.assertTrue(raid.mercyCandidates().contains(candidate.getUUID()),
                "candidate should remain unresolved in mercy");
        PlayerRaidService.onLivingDeath(new LivingDeathEvent(candidate, candidate.damageSources().generic()));
        helper.assertValueEqual(raid.phase(), PlayerRaidSavedData.Phase.RAIDER_VICTORY,
                "manual candidate death should resolve the final mercy objective");
        data.remove(raid.id());
        defender.discard();
        candidate.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void mercyPleasRequireApproachAndRespectBothCooldowns(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos center = helper.absolutePos(new BlockPos(4, 2, 4));
        Villager candidate = EntityType.VILLAGER.create(helper.getLevel());
        helper.assertTrue(candidate != null, "mercy candidate should be creatable");
        candidate.moveTo(center.getX() + 0.5D, center.getY(), center.getZ() + 0.5D, 0.0F, 0.0F);
        helper.assertTrue(helper.getLevel().addFreshEntity(candidate), "mercy candidate should spawn");
        player.moveTo(center.getX() + 20.5D, center.getY(), center.getZ() + 0.5D, 0.0F, 0.0F);

        PlayerRaidSavedData data = PlayerRaidSavedData.get(helper.getLevel());
        PlayerRaidSavedData.RaidRecord raid = data.create(
                VillageAllegianceId.random(), helper.getLevel().dimension().location(), center,
                Set.of(SectionPos.asLong(center)), "Plea Village", player.getUUID(), null,
                Set.of(player.getUUID()), Set.of(), Set.of(), Set.of(candidate.getUUID()), Set.of(), Set.of(), 42L);
        raid.setPhase(PlayerRaidSavedData.Phase.MERCY, 43L);
        long now = helper.getLevel().getGameTime();
        raid.setNextMercyPleaAt(candidate.getUUID(), now);
        raid.setNextRaidMercyPleaAt(now);
        PlayerRaidMercyService.tick(helper.getLevel().getServer(), data, raid, now);
        helper.assertValueEqual(raid.nextMercyPleaAt(candidate.getUUID()), now,
                "distant raider should not consume candidate cooldown");

        player.moveTo(center.getX() + 1.5D, center.getY(), center.getZ() + 0.5D, 0.0F, 0.0F);
        PlayerRaidMercyService.tick(helper.getLevel().getServer(), data, raid, now);
        long nextCandidatePlea = raid.nextMercyPleaAt(candidate.getUUID());
        helper.assertTrue(nextCandidatePlea >= now + 20L * 30L,
                "approach plea should schedule a 30-60 second candidate cooldown");
        helper.assertValueEqual(raid.nextRaidMercyPleaAt(), now + 20L * 5L,
                "approach plea should schedule the five-second raid gap");
        PlayerRaidMercyService.tick(helper.getLevel().getServer(), data, raid, now + 20L);
        helper.assertValueEqual(raid.nextMercyPleaAt(candidate.getUUID()), nextCandidatePlea,
                "raid gap should suppress a repeated plea");
        data.remove(raid.id());
        candidate.discard();
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
        long winFinishedAt = helper.getLevel().getServer().overworld().getGameTime();
        helper.assertValueEqual(
                data.cooldownUntil(won.villageId()),
                winFinishedAt + Math.max(0, VillagerRetaliationConfig.PLAYER_RAID_VILLAGE_COOLDOWN_DAYS.get()) * 24_000L,
                "raider victory persisted village cooldown");

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
        long lossFinishedAt = helper.getLevel().getServer().overworld().getGameTime();
        helper.assertValueEqual(
                data.cooldownUntil(lost.villageId()),
                lossFinishedAt + Math.max(0, VillagerRetaliationConfig.PLAYER_RAID_VILLAGE_COOLDOWN_DAYS.get()) * 24_000L,
                "defender victory persisted village cooldown");
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
    public static void raidDefenderDebugHighlightUsesPersistedObjective(GameTestHelper helper) {
        BlockPos center = helper.absolutePos(new BlockPos(4, 2, 4));
        Villager defender = EntityType.VILLAGER.create(helper.getLevel());
        helper.assertTrue(defender != null, "villager should be creatable");
        defender.moveTo(center.getX() + 0.5D, center.getY(), center.getZ() + 0.5D, 0.0F, 0.0F);
        helper.assertTrue(helper.getLevel().addFreshEntity(defender), "villager should spawn");

        PlayerRaidSavedData data = PlayerRaidSavedData.get(helper.getLevel());
        UUID player = UUID.randomUUID();
        PlayerRaidSavedData.RaidRecord raid = data.create(
                VillageAllegianceId.random(), helper.getLevel().dimension().location(), center,
                Set.of(SectionPos.asLong(center)), "Highlight Village", player, null,
                Set.of(player), Set.of(), Set.of(defender.getUUID()), Set.of(), 42L);
        raid.setPhase(PlayerRaidSavedData.Phase.ACTIVE, 43L);
        PlayerRaidService.highlightTrackedDefenders(helper.getLevel().getServer(), raid);
        helper.assertTrue(defender.hasEffect(MobEffects.GLOWING),
                "the defender retained in the raid objective should glow");
        data.remove(raid.id());
        defender.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void permanentlyDiscardedDefenderLeavesRaidObjective(GameTestHelper helper) {
        BlockPos center = helper.absolutePos(new BlockPos(4, 2, 4));
        Villager defender = EntityType.VILLAGER.create(helper.getLevel());
        helper.assertTrue(defender != null, "villager should be creatable");
        defender.moveTo(center.getX() + 0.5D, center.getY(), center.getZ() + 0.5D, 0.0F, 0.0F);
        helper.assertTrue(helper.getLevel().addFreshEntity(defender), "villager should spawn");

        PlayerRaidSavedData data = PlayerRaidSavedData.get(helper.getLevel());
        UUID player = UUID.randomUUID();
        PlayerRaidSavedData.RaidRecord raid = data.create(
                VillageAllegianceId.random(), helper.getLevel().dimension().location(), center,
                Set.of(SectionPos.asLong(center)), "Discard Village", player, null,
                Set.of(player), Set.of(), Set.of(defender.getUUID()), Set.of(), 42L);
        raid.setPhase(PlayerRaidSavedData.Phase.ACTIVE, 43L);
        defender.discard();
        helper.assertTrue(raid.defenders().isEmpty(),
                "a permanently discarded defender should be removed from the objective");
        helper.assertValueEqual(raid.phase(), PlayerRaidSavedData.Phase.RAIDER_VICTORY,
                "discarding the final defender should settle the raid");
        data.remove(raid.id());
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
        golem.moveTo(center.getX() + 0.5D, center.getY() + 34.0D, center.getZ() + 0.5D, 0.0F, 0.0F);
        defender.moveTo(center.getX() + 5.5D, center.getY() + 40.0D, center.getZ() + 0.5D, 0.0F, 0.0F);
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
        VillagerRetaliationHandler.onEntityTickPost(new EntityTickEvent.Post(raider));
        helper.assertTrue(VillagerRetaliationHandler.hasRetaliationTarget(raider, golem),
                "a raiding party villager should retain a distant aligned village golem after its AI tick");

        data.remove(raid.id());
        raider.discard();
        defender.discard();
        golem.discard();
        helper.succeed();
    }
}
