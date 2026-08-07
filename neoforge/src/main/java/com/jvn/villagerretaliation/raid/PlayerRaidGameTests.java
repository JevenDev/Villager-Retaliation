package com.jvn.villagerretaliation.raid;

import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.allegiance.AllegianceAssignmentSource;
import com.jvn.villagerretaliation.allegiance.AllegianceCombatContext;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceApi;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceCombatPolicy;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.combat.VillagerRetaliationHandler;
import com.jvn.villagerretaliation.combat.downed.VillagerDeathProtectionResolver;
import com.jvn.villagerretaliation.combat.downed.VillagerDownedService;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.interaction.VillagerConversationService;
import com.jvn.villagerretaliation.item.BannerHelmetData;
import com.jvn.villagerretaliation.item.OminousBannerRecognition;
import com.jvn.villagerretaliation.profile.VillagerProfileManager;
import com.jvn.villagerretaliation.profile.VillagerSocialAttribute;
import com.jvn.villagerretaliation.reputation.VillagerReputationManager;
import com.jvn.villagerretaliation.reputation.VillagerReputationAdvancements;
import com.jvn.villagerretaliation.village.VillagerRaidMemorySavedData;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.entity.raid.Raid;
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
    public static void emptyGraceVillagesCannotStartPlayerRaids(GameTestHelper helper) {
        VillageAllegianceRegistrySavedData registry = new VillageAllegianceRegistrySavedData();
        VillageAllegianceId village = registry.create(
                1L,
                helper.getLevel().dimension().location(),
                helper.absolutePos(new BlockPos(1, 2, 1)),
                "Raid Lifecycle");
        helper.assertTrue(
                PlayerRaidService.isRaidableVillage(registry.record(village).orElseThrow()),
                "active village is raidable");
        registry.observeEmpty(village, 1L);
        helper.assertFalse(
                PlayerRaidService.isRaidableVillage(registry.record(village).orElseThrow()),
                "empty-grace village is not raidable");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void raidHornConfirmationIsPerPlayerPerVillageAndExpires(GameTestHelper helper) {
        PlayerRaidConfirmationTracker tracker = new PlayerRaidConfirmationTracker();
        UUID firstPlayer = UUID.randomUUID();
        UUID secondPlayer = UUID.randomUUID();
        VillageAllegianceId firstVillage = VillageAllegianceId.random();
        VillageAllegianceId secondVillage = VillageAllegianceId.random();

        helper.assertFalse(tracker.consumeOrArm(firstPlayer, firstVillage, 100L),
                "first horn use should arm confirmation");
        helper.assertFalse(tracker.consumeOrArm(firstPlayer, secondVillage, 101L),
                "confirmation should not carry to another village");
        helper.assertFalse(tracker.consumeOrArm(secondPlayer, firstVillage, 102L),
                "confirmation should not carry to another player");
        helper.assertTrue(tracker.consumeOrArm(firstPlayer, firstVillage, 103L),
                "second horn use in the same village should consume confirmation");
        helper.assertFalse(tracker.consumeOrArm(firstPlayer, firstVillage, 104L),
                "consumed confirmation should require arming again");
        helper.assertFalse(tracker.consumeOrArm(
                        firstPlayer,
                        firstVillage,
                        104L + PlayerRaidConfirmationTracker.CONFIRMATION_WINDOW_TICKS),
                "confirmation should expire after 30 seconds");
        tracker.clear();
        helper.assertFalse(tracker.consumeOrArm(firstPlayer, firstVillage, 105L),
                "runtime cleanup should discard an armed confirmation");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void ominousBannerRecognitionCoversWornBannersAndHeldShields(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ItemStack ominousBanner = Raid.getLeaderBannerInstance(
                helper.getLevel().registryAccess().lookupOrThrow(Registries.BANNER_PATTERN));

        player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.WHITE_BANNER));
        helper.assertTrue(!OminousBannerRecognition.isDisplaying(player),
                "an ordinary white banner must not be treated as ominous");

        player.setItemSlot(EquipmentSlot.HEAD, ominousBanner.copy());
        helper.assertTrue(OminousBannerRecognition.isDisplaying(player),
                "the vanilla ominous banner should be recognized in the head slot");

        ItemStack helmet = new ItemStack(Items.IRON_HELMET);
        BannerHelmetData.attach(helmet, ominousBanner, helper.getLevel().registryAccess());
        player.setItemSlot(EquipmentSlot.HEAD, helmet);
        helper.assertTrue(OminousBannerRecognition.isDisplaying(player),
                "the ominous banner should be recognized when attached to a worn helmet");

        player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        ItemStack ominousShield = new ItemStack(Items.SHIELD);
        ominousShield.set(DataComponents.BANNER_PATTERNS, ominousBanner.get(DataComponents.BANNER_PATTERNS));
        ominousShield.set(DataComponents.BASE_COLOR, DyeColor.WHITE);
        player.setItemSlot(EquipmentSlot.MAINHAND, ominousShield.copy());
        helper.assertTrue(OminousBannerRecognition.isDisplaying(player),
                "an ominous-pattern shield should be recognized in the main hand");

        player.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        player.setItemSlot(EquipmentSlot.OFFHAND, ominousShield);
        helper.assertTrue(OminousBannerRecognition.isDisplaying(player),
                "an ominous-pattern shield should be recognized in the off hand");

        var advancement = helper.getLevel().getServer().getAdvancements().get(
                VillagerRetaliation.id("reputation/the_mark_you_chose"));
        helper.assertTrue(advancement != null, "the ominous conversation advancement should be loaded");
        VillagerReputationAdvancements.onVillagerConversationStarted(player);
        helper.assertTrue(player.getAdvancements().getOrStartProgress(advancement).isDone(),
                "starting a conversation while displaying the ominous shield should award the advancement");
        helper.succeed();
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
    public static void preparationClosesBetrayalDialogueSession(GameTestHelper helper) {
        PlayerRaidDialogueService.clearRuntimeState();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos center = helper.absolutePos(new BlockPos(4, 2, 4));
        player.moveTo(center.getX() + 0.5D, center.getY(), center.getZ() + 0.5D, 0.0F, 0.0F);
        Villager defector = EntityType.VILLAGER.create(helper.getLevel());
        helper.assertTrue(defector != null, "defector should be creatable");
        defector.moveTo(center.getX() + 1.5D, center.getY(), center.getZ() + 0.5D, 0.0F, 0.0F);
        helper.assertTrue(helper.getLevel().addFreshEntity(defector), "defector should spawn");

        PlayerRaidSavedData data = PlayerRaidSavedData.get(helper.getLevel());
        PlayerRaidSavedData.RaidRecord raid = data.create(
                VillageAllegianceId.random(), helper.getLevel().dimension().location(), center,
                Set.of(SectionPos.asLong(center)), "Dialogue Village", player.getUUID(), UUID.randomUUID(),
                Set.of(player.getUUID()), Set.of(), Set.of(defector.getUUID()), Set.of(), Set.of(),
                Set.of(defector.getUUID()), 42L);
        helper.assertTrue(PlayerRaidDialogueService.begin(player, raid),
                "betrayal dialogue should start for a loaded defector");
        helper.assertTrue(PlayerRaidDialogueService.hasSession(raid.id()),
                "raid should own a declaration dialogue session");
        helper.assertTrue(VillagerConversationService.isConversing(player),
                "the generic conversation service should hold the forced session");

        PlayerRaidService.beginPreparation(helper.getLevel().getServer(), raid.id());
        helper.assertValueEqual(raid.phase(), PlayerRaidSavedData.Phase.PREPARING,
                "declaration should advance to preparation");
        helper.assertFalse(PlayerRaidDialogueService.hasSession(raid.id()),
                "preparation should remove the raid declaration session");
        helper.assertFalse(VillagerConversationService.isConversing(player),
                "preparation should close the underlying forced conversation");

        data.remove(raid.id());
        defector.discard();
        player.discard();
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
    public static void raidOutcomesAdjustParticipatingVillagerGuts(GameTestHelper helper) {
        PlayerRaidSavedData data = PlayerRaidSavedData.get(helper.getLevel());
        BlockPos center = helper.absolutePos(new BlockPos(4, 2, 4));
        Villager raider = EntityType.VILLAGER.create(helper.getLevel());
        helper.assertTrue(raider != null, "raider villager should be creatable");
        raider.moveTo(
                center.getX() + 0.5D, center.getY(), center.getZ() + 0.5D, 0.0F, 0.0F);
        helper.assertTrue(helper.getLevel().addFreshEntity(raider), "raider villager should spawn");
        VillagerProfileManager.setAttribute(
                helper.getLevel(), raider, VillagerSocialAttribute.GUTS, 50);

        UUID winningPlayer = UUID.randomUUID();
        PlayerRaidSavedData.RaidRecord won = data.create(
                VillageAllegianceId.random(), helper.getLevel().dimension().location(), center,
                Set.of(SectionPos.asLong(center)), "Guts Win Village", winningPlayer, null,
                Set.of(winningPlayer), Set.of(raider.getUUID()),
                Set.of(UUID.randomUUID()), Set.of(), 42L);
        won.setPhase(PlayerRaidSavedData.Phase.ACTIVE, 43L);
        helper.assertValueEqual(
                PlayerRaidService.debugFinishRaid(helper.getLevel(), center, winningPlayer, true), won,
                "participating villager raid should resolve as a win");
        helper.assertValueEqual(
                VillagerProfileManager.getOrCreateProfile(
                        helper.getLevel(), raider).socialAttributes().guts(),
                60, "winning a raid should increase a participating villager's guts by ten");

        UUID losingPlayer = UUID.randomUUID();
        PlayerRaidSavedData.RaidRecord lost = data.create(
                VillageAllegianceId.random(), helper.getLevel().dimension().location(), center,
                Set.of(SectionPos.asLong(center)), "Guts Loss Village", losingPlayer, null,
                Set.of(losingPlayer), Set.of(raider.getUUID()),
                Set.of(UUID.randomUUID()), Set.of(), 44L);
        lost.setPhase(PlayerRaidSavedData.Phase.ACTIVE, 45L);
        helper.assertValueEqual(
                PlayerRaidService.debugFinishRaid(helper.getLevel(), center, losingPlayer, false), lost,
                "participating villager raid should resolve as a loss");
        helper.assertValueEqual(
                VillagerProfileManager.getOrCreateProfile(
                        helper.getLevel(), raider).socialAttributes().guts(),
                55, "losing a raid should decrease a participating villager's guts by five");
        data.remove(won.id());
        data.remove(lost.id());
        raider.discard();
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
    public static void downedDefenderLeavesRaidObjectiveAlive(GameTestHelper helper) {
        BlockPos center = helper.absolutePos(new BlockPos(4, 2, 4));
        Villager defender = EntityType.VILLAGER.create(helper.getLevel());
        helper.assertTrue(defender != null, "villager should be creatable");
        defender.moveTo(center.getX() + 0.5D, center.getY(), center.getZ() + 0.5D, 0.0F, 0.0F);
        helper.assertTrue(helper.getLevel().addFreshEntity(defender), "villager should spawn");

        PlayerRaidSavedData data = PlayerRaidSavedData.get(helper.getLevel());
        UUID player = UUID.randomUUID();
        PlayerRaidSavedData.RaidRecord raid = data.create(
                VillageAllegianceId.random(), helper.getLevel().dimension().location(), center,
                Set.of(SectionPos.asLong(center)), "Downed Village", player, null,
                Set.of(player), Set.of(), Set.of(defender.getUUID()), Set.of(), 42L);
        raid.setPhase(PlayerRaidSavedData.Phase.ACTIVE, 43L);

        helper.assertTrue(VillagerDownedService.enterDowned(
                        helper.getLevel(),
                        defender,
                        new VillagerDeathProtectionResolver.ProtectionResult(
                                true, List.of("player_raid_test"))),
                "protected raid defender should enter the downed state");
        helper.assertTrue(defender.isAlive(), "downed raid defender should survive");
        helper.assertTrue(raid.defenders().isEmpty(),
                "a downed defender should leave the combat objective");
        helper.assertValueEqual(raid.phase(), PlayerRaidSavedData.Phase.RAIDER_VICTORY,
                "downing the final combat defender should settle the raid");

        VillagerDownedService.recover(defender);
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
    public static void defendingVillagersTreatEntireRaidingPartyAsAggressors(GameTestHelper helper) {
        BlockPos center = helper.absolutePos(new BlockPos(4, 2, 4));
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Villager recruitedRaider = EntityType.VILLAGER.create(helper.getLevel());
        Villager defender = EntityType.VILLAGER.create(helper.getLevel());
        helper.assertTrue(recruitedRaider != null && defender != null,
                "raid villagers should be creatable");
        player.moveTo(center.getX() - 8.5D, center.getY(), center.getZ() + 0.5D, 0.0F, 0.0F);
        recruitedRaider.moveTo(center.getX() - 1.5D, center.getY(), center.getZ() + 0.5D, 0.0F, 0.0F);
        defender.moveTo(center.getX() + 1.5D, center.getY(), center.getZ() + 0.5D, 0.0F, 0.0F);
        helper.assertTrue(helper.getLevel().addFreshEntity(recruitedRaider), "recruited raider should spawn");
        helper.assertTrue(helper.getLevel().addFreshEntity(defender), "defender should spawn");

        PlayerRaidSavedData data = PlayerRaidSavedData.get(helper.getLevel());
        PlayerRaidSavedData.RaidRecord raid = data.create(
                VillageAllegianceId.random(), helper.getLevel().dimension().location(), center,
                Set.of(SectionPos.asLong(center)), "Party Aggressor Village", player.getUUID(), UUID.randomUUID(),
                Set.of(player.getUUID()), Set.of(recruitedRaider.getUUID()),
                Set.of(defender.getUUID()), Set.of(), 42L);
        raid.setPhase(PlayerRaidSavedData.Phase.ACTIVE, 43L);

        PlayerRaidService.reconcileCombat(helper.getLevel().getServer(), raid);
        helper.assertTrue(VillagerRetaliationHandler.hasRetaliationTarget(defender, recruitedRaider),
                "defender should target a nearer participating recruited villager");
        helper.assertTrue(VillagerRetaliationHandler.isHostileTowards(defender, player),
                "every participating raider player should remain an aggressor while another party member is targeted");

        PlayerRaidService.debugFinishRaid(helper.getLevel(), center, player.getUUID(), false);
        helper.assertFalse(VillagerRetaliationHandler.hasActiveRetaliationTarget(defender),
                "settling a raid should clear the surviving defender's forced target");

        data.remove(raid.id());
        recruitedRaider.discard();
        defender.discard();
        player.discard();
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

        VillageAllegianceRegistrySavedData registry = VillageAllegianceRegistrySavedData.get(helper.getLevel());
        VillageAllegianceId village = registry.create(
                helper.getLevel().getGameTime(), helper.getLevel().dimension().location(), center, "Golem Source");
        VillageAllegianceId mergedVillage = registry.create(
                helper.getLevel().getGameTime(), helper.getLevel().dimension().location(), center, "Golem Target");
        VillageAllegianceApi.assignKnown(
                helper.getLevel(), golem, village, AllegianceAssignmentSource.EXPLICIT_API);
        PlayerRaidSavedData data = PlayerRaidSavedData.get(helper.getLevel());
        UUID player = UUID.randomUUID();
        PlayerRaidSavedData.RaidRecord raid = data.create(
                village, helper.getLevel().dimension().location(), center,
                Set.of(SectionPos.asLong(center)), "Golem Defense Village", player, UUID.randomUUID(),
                Set.of(player), Set.of(raider.getUUID()), Set.of(defender.getUUID()), Set.of(), 42L);
        raid.setPhase(PlayerRaidSavedData.Phase.ACTIVE, 43L);
        helper.assertTrue(registry.merge(village, mergedVillage),
                "the raid village should merge into a new canonical record");

        helper.assertTrue(PlayerRaidService.areOpposingParticipants(raider, golem),
                "a golem should remain a Player Raid defender after its village is merged");
        PlayerRaidService.reconcileCombat(helper.getLevel().getServer(), raid);
        VillagerRetaliationHandler.onEntityTickPost(new EntityTickEvent.Post(raider));
        helper.assertTrue(VillagerRetaliationHandler.hasRetaliationTarget(raider, golem),
                "a raiding party villager should retain a distant aligned village golem after its AI tick");

        data.remove(raid.id());
        registry.undoMerge(village);
        raider.discard();
        defender.discard();
        golem.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void missingRaidDimensionSettlesAndReleasesParticipants(GameTestHelper helper) {
        PlayerRaidSavedData data = PlayerRaidSavedData.get(helper.getLevel());
        UUID player = UUID.randomUUID();
        PlayerRaidSavedData.RaidRecord raid = data.create(
                VillageAllegianceId.random(),
                VillagerRetaliation.id("missing_raid_dimension_" + UUID.randomUUID()),
                helper.absolutePos(new BlockPos(4, 2, 4)),
                Set.of(),
                "Missing Dimension Village",
                player,
                null,
                Set.of(player),
                Set.of(),
                Set.of(UUID.randomUUID()),
                Set.of(),
                42L);
        raid.setPhase(PlayerRaidSavedData.Phase.ACTIVE, 43L);

        PlayerRaidService.tickRaid(helper.getLevel().getServer(), data, raid, 44L);

        helper.assertValueEqual(raid.phase(), PlayerRaidSavedData.Phase.DEFENDER_VICTORY,
                "a raid whose dimension disappeared should settle as defended");
        helper.assertTrue(data.activeForParticipant(player) == null,
                "settling a missing-dimension raid should release its participants");
        data.remove(raid.id());
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void raidLoadoutsCanRefillSlotsInLaterRaids(GameTestHelper helper) {
        Villager defender = EntityType.VILLAGER.create(helper.getLevel());
        helper.assertTrue(defender != null, "raid defender should be creatable");
        defender.moveTo(helper.absolutePos(new BlockPos(4, 2, 4)), 0.0F, 0.0F);
        helper.assertTrue(helper.getLevel().addFreshEntity(defender), "raid defender should spawn");
        UUID firstRaid = UUID.randomUUID();
        UUID secondRaid = UUID.randomUUID();

        PlayerRaidLoadoutService.equip(defender, firstRaid);
        helper.assertFalse(defender.getMainHandItem().isEmpty(),
                "the first raid should equip an empty main hand");
        defender.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        PlayerRaidLoadoutService.equip(defender, firstRaid);
        helper.assertTrue(defender.getMainHandItem().isEmpty(),
                "the same raid should not reroll its loadout every reconciliation tick");
        PlayerRaidLoadoutService.equip(defender, secondRaid);
        helper.assertFalse(defender.getMainHandItem().isEmpty(),
                "a later raid should refill an equipment slot that became empty");

        defender.discard();
        helper.succeed();
    }
}
