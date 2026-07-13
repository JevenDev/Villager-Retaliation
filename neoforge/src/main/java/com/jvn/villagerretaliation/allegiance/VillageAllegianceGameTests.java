package com.jvn.villagerretaliation.party;

import com.jvn.villagerretaliation.allegiance.AllegianceAssignmentSource;
import com.jvn.villagerretaliation.allegiance.AllegianceCombatContext;
import com.jvn.villagerretaliation.allegiance.AllegianceCombatDecision;
import com.jvn.villagerretaliation.allegiance.AllegianceConfidence;
import com.jvn.villagerretaliation.allegiance.AllegianceState;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceCombatPolicy;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceApi;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceData;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceEntityData;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRelations;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceReassignmentService;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceService;
import com.jvn.villagerretaliation.allegiance.VillageCombatAuthorizationService;
import com.jvn.villagerretaliation.allegiance.VillageLifecycleState;
import com.jvn.villagerretaliation.allegiance.VillageNamingService;
import com.jvn.villagerretaliation.allegiance.VillageFootprintResolver;
import com.jvn.villagerretaliation.interaction.VillagerContractTime;
import com.jvn.villagerretaliation.network.VillageBoundsSyncPayload;
import com.jvn.villagerretaliation.reputation.VillagerReputationManager;
import com.jvn.villagerretaliation.util.VillagerRetaliationTags;
import com.jvn.villagerretaliation.village.VillageMembership;
import com.jvn.villagerretaliation.village.VillageScopeKeys;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class VillageAllegianceGameTests {
    private static final String EMPTY_TEMPLATE = "empty";

    private VillageAllegianceGameTests() {
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void entityPayloadStatesAndParentsRoundTrip(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        VillageAllegianceId primary = VillageAllegianceId.random();
        VillageAllegianceId firstParent = VillageAllegianceId.random();
        VillageAllegianceId secondParent = VillageAllegianceId.random();
        Villager source = createVillager(level);
        VillageAllegianceEntityData.write(source, VillageAllegianceData.known(
                primary, AllegianceAssignmentSource.BIRTH, AllegianceConfidence.INHERITED,
                42L, level.dimension().location(), new BlockPos(1, 2, 3),
                List.of(firstParent, firstParent, secondParent, VillageAllegianceId.random())));
        Villager restored = roundTrip(level, source);
        VillageAllegianceData restoredData = VillageAllegianceEntityData.read(restored).orElseThrow();
        helper.assertValueEqual(restoredData.state(), AllegianceState.KNOWN, "known state");
        helper.assertValueEqual(restoredData.primary(), primary, "known primary id");
        helper.assertValueEqual(restoredData.protectedParents(), List.of(firstParent, secondParent),
                "bounded deduplicated parent protections");
        helper.assertValueEqual(VillageAllegianceEntityData.readHistory(restored).size(), 1,
                "initial assignment creates one durable history entry");

        VillageAllegianceEntityData.write(source, VillageAllegianceData.unknown(
                AllegianceAssignmentSource.MIGRATION, AllegianceConfidence.LEGACY_INFERRED,
                43L, level.dimension().location(), BlockPos.ZERO));
        helper.assertValueEqual(
                VillageAllegianceEntityData.read(roundTrip(level, source)).orElseThrow().state(),
                AllegianceState.UNKNOWN,
                "unknown state round-trip");

        VillageAllegianceEntityData.writePending(source, new VillageAllegianceEntityData.PendingAssignmentData(
                level.dimension().location(), new BlockPos(7, 8, 9),
                AllegianceAssignmentSource.MIGRATION, 3, 120L));
        Villager pendingRestored = roundTrip(level, source);
        VillageAllegianceEntityData.PendingAssignmentData pending =
                VillageAllegianceEntityData.readPending(pendingRestored).orElseThrow();
        helper.assertValueEqual(pending.position(), new BlockPos(7, 8, 9), "pending evidence position");
        helper.assertValueEqual(pending.attempts(), 3, "pending retry count");
        helper.assertValueEqual(pending.nextAttemptGameTime(), 120L, "pending retry time");

        VillageAllegianceEntityData.write(source, VillageAllegianceData.unaffiliated(
                AllegianceAssignmentSource.EXPLICIT_API, 44L, level.dimension().location(), BlockPos.ZERO));
        helper.assertValueEqual(
                VillageAllegianceEntityData.read(roundTrip(level, source)).orElseThrow().state(),
                AllegianceState.UNAFFILIATED,
                "unaffiliated state round-trip");
        UUID responsiblePlayer = UUID.randomUUID();
        VillageAllegianceEntityData.annotateLatestHistoryActor(source, responsiblePlayer);
        var latestHistory = VillageAllegianceEntityData.readHistory(roundTrip(level, source)).getLast();
        helper.assertValueEqual(latestHistory.previousState(), AllegianceState.UNKNOWN,
                "history records the previous allegiance state");
        helper.assertValueEqual(latestHistory.newState(), AllegianceState.UNAFFILIATED,
                "history records the new allegiance state");
        helper.assertValueEqual(latestHistory.responsiblePlayer(), responsiblePlayer,
                "trusted reassignment actors can be audited");

        CompoundTag malformed = new CompoundTag();
        malformed.putInt("DataVersion", VillageAllegianceData.CURRENT_VERSION + 1);
        source.getPersistentData().put(VillageAllegianceEntityData.ROOT_TAG, malformed);
        helper.assertValueEqual(VillageAllegianceEntityData.read(source).orElseThrow().state(),
                AllegianceState.UNKNOWN, "unsupported future data is conservative");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void registryAliasesAndTombstonesRoundTrip(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        VillageAllegianceRegistrySavedData registry = new VillageAllegianceRegistrySavedData();
        VillageAllegianceId first = registry.create(1L, level.dimension().location(), BlockPos.ZERO, "First");
        VillageAllegianceId second = registry.create(2L, level.dimension().location(), BlockPos.ZERO, "Second");
        VillageAllegianceId third = registry.create(3L, level.dimension().location(), BlockPos.ZERO, "Third");
        helper.assertTrue(registry.merge(first, second), "first alias merge");
        helper.assertTrue(registry.merge(second, third), "second alias merge");
        helper.assertValueEqual(registry.canonical(first).orElseThrow(), third, "alias chain canonical id");
        helper.assertFalse(registry.merge(third, first), "alias cycle must be rejected");
        helper.assertTrue(registry.archive(first), "raw source record tombstone");
        CompoundTag saved = registry.save(new CompoundTag(), level.registryAccess());
        VillageAllegianceRegistrySavedData restored = VillageAllegianceRegistrySavedData.load(saved, level.registryAccess());
        helper.assertValueEqual(restored.canonical(first).orElseThrow(), third, "saved alias chain");
        helper.assertTrue(restored.record(first).orElseThrow().archived(), "tombstone persistence");
        VillageAllegianceId lateRecord = VillageAllegianceId.random();
        helper.assertTrue(restored.canonical(lateRecord).isEmpty(), "missing records resolve conservatively");
        restored.ensureRecord(lateRecord, 5L, level.dimension().location(), BlockPos.ZERO);
        helper.assertValueEqual(restored.canonical(lateRecord).orElseThrow(), lateRecord,
                "creating a record invalidates cached missing canonical paths");

        BlockPos indexedPosition = helper.absolutePos(new BlockPos(3, 2, 3));
        VillageAllegianceId indexedVillage = restored.create(
                6L, level.dimension().location(), indexedPosition, "Indexed Village");
        CompoundTag indexedSave = restored.save(new CompoundTag(), level.registryAccess());
        for (Tag raw : indexedSave.getList("Records", Tag.TAG_COMPOUND)) {
            if (raw instanceof CompoundTag record && record.hasUUID("Id")
                    && record.getUUID("Id").equals(indexedVillage.value())) {
                long section = SectionPos.asLong(indexedPosition);
                record.putLongArray("SourceSections", new long[] {section});
                record.putLongArray("FootprintSections", new long[] {section});
                record.putLongArray("HistoricalFootprintSections", new long[] {section});
            }
        }
        VillageAllegianceRegistrySavedData indexedRegistry = VillageAllegianceRegistrySavedData.load(
                indexedSave, level.registryAccess());
        helper.assertValueEqual(indexedRegistry.resolveAt(level, indexedPosition).orElseThrow(), indexedVillage,
                "section index resolves a village without scanning every record");
        helper.assertTrue(indexedRegistry.archive(indexedVillage), "indexed village archives");
        helper.assertTrue(indexedRegistry.resolveAt(level, indexedPosition).isEmpty(),
                "archiving invalidates the section index immediately");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void directVillageMergesCanBeRestored(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        VillageAllegianceRegistrySavedData registry = new VillageAllegianceRegistrySavedData();
        VillageAllegianceId source = registry.create(
                1L, level.dimension().location(), BlockPos.ZERO, "Restorable Source");
        VillageAllegianceId target = registry.create(
                2L, level.dimension().location(), new BlockPos(32, 0, 0), "Restorable Target");
        UUID sourceResident = UUID.randomUUID();
        registry.addOrUpdateResident(source, sourceResident, true, 3L);
        helper.assertTrue(registry.merge(source, target), "direct merge succeeds");
        helper.assertTrue(registry.canonical(source).orElseThrow().equals(target), "source aliases to target");
        helper.assertTrue(registry.undoMerge(source), "direct merge can be restored");
        helper.assertValueEqual(registry.canonical(source).orElseThrow(), source, "source identity is active again");
        helper.assertTrue(registry.record(source).orElseThrow().residents().containsKey(sourceResident),
                "source roster survives merge restoration");
        helper.assertFalse(registry.record(target).orElseThrow().residents().containsKey(sourceResident),
                "restored residents are removed from the former target");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void reassignmentResidencyPersistsAndConfirmationRequiresTwoRequests(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Villager villager = createVillager(level);
        VillageAllegianceId target = VillageAllegianceId.random();
        VillageAllegianceReassignmentService.writeResidency(
                villager, new VillageAllegianceReassignmentService.Residency(target, 42L));
        Villager restored = roundTrip(level, villager);
        helper.assertValueEqual(
                VillageAllegianceReassignmentService.readResidency(restored).orElseThrow(),
                new VillageAllegianceReassignmentService.Residency(target, 42L),
                "residency clock survives entity persistence");

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        helper.assertFalse(VillageAllegianceReassignmentService.confirmOrArm(
                level, player, restored, target), "first reassignment request only arms confirmation");
        helper.assertTrue(VillageAllegianceReassignmentService.confirmOrArm(
                level, player, restored, target), "second matching request confirms reassignment");
        VillageAllegianceReassignmentService.complete(restored);
        helper.assertTrue(VillageAllegianceReassignmentService.readResidency(restored).isEmpty(),
                "completed reassignment clears the old residency clock");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void newbornsOutsideVillagesInheritAParentsHome(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        VillageAllegianceRegistrySavedData registry = VillageAllegianceRegistrySavedData.get(level);
        VillageAllegianceId firstHome = registry.create(
                level.getGameTime(), level.dimension().location(), BlockPos.ZERO, "First Birth Home");
        VillageAllegianceId secondHome = registry.create(
                level.getGameTime(), level.dimension().location(), BlockPos.ZERO, "Second Birth Home");
        Villager firstParent = createVillager(level);
        Villager secondParent = createVillager(level);
        Villager child = createVillager(level);
        child.setBaby(true);
        VillageAllegianceApi.assignKnown(level, firstParent, firstHome, AllegianceAssignmentSource.ADMIN);
        VillageAllegianceApi.assignKnown(level, secondParent, firstHome, AllegianceAssignmentSource.ADMIN);

        VillageAllegianceService.assignBirthAllegiance(level, child, firstParent, secondParent);
        VillageAllegianceData inherited = VillageAllegianceApi.get(child).orElseThrow();
        helper.assertValueEqual(inherited.primary(), firstHome, "shared parent home");
        helper.assertValueEqual(inherited.confidence(), AllegianceConfidence.INHERITED, "inherited confidence");

        VillageAllegianceApi.assignKnown(level, secondParent, secondHome, AllegianceAssignmentSource.ADMIN);
        Villager mixedChild = createVillager(level);
        mixedChild.setBaby(true);
        VillageAllegianceService.assignBirthAllegiance(level, mixedChild, firstParent, secondParent);
        VillageAllegianceData mixed = VillageAllegianceApi.get(mixedChild).orElseThrow();
        helper.assertValueEqual(mixed.state(), AllegianceState.KNOWN,
                "a child outside a village still inherits a parent's known home");
        helper.assertValueEqual(mixed.primary(), firstHome,
                "the first parent with a known home supplies the inherited home");
        helper.assertValueEqual(mixed.protectedParents(), List.of(firstHome, secondHome),
                "both parent communities remain protected while the child is a baby");
        helper.assertTrue(VillageAllegianceRelations.sharesCommunity(level, mixedChild, firstParent),
                "mixed child is protected by the first parent community");
        helper.assertTrue(VillageAllegianceRelations.sharesCommunity(level, mixedChild, secondParent),
                "mixed child is protected by the second parent community");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void partyVillagerHomeOrdersRejectOutsidePlayers(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Villager villager = createVillager(level);
        VillageAllegianceRegistrySavedData registry = VillageAllegianceRegistrySavedData.get(level);
        VillageAllegianceId target = registry.create(
                level.getGameTime(), level.dimension().location(), BlockPos.ZERO, "Party Destination");
        PartySavedData partyData = PartySavedData.get(level);
        PartyRecord party = partyData.createParty(UUID.randomUUID(), level.getGameTime());
        partyData.addVillager(party, partyVillagerRecord(villager.getUUID(), party.leaderId(), level.getGameTime()));
        VillagerReputationManager.setReputation(level, villager, player.getUUID(), Integer.MAX_VALUE);

        helper.assertValueEqual(
                VillageAllegianceReassignmentService.eligibility(level, player, villager, target).reason(),
                VillageAllegianceReassignmentService.Reason.OUTSIDE_PARTY,
                "even a revered outside player cannot choose a party villager's home");
        helper.assertTrue(partyData.addPlayer(party, player.getUUID()),
                "the test player can join the villager's party");
        helper.assertTrue(VillageAllegianceReassignmentService.eligibility(level, player, villager, target).allowed(),
                "a revered player in the same party can choose the villager's home");

        partyData.removeParty(party.id());
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void explicitStatesKeepResidentRostersConsistent(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Villager villager = createVillager(level);
        VillageAllegianceRegistrySavedData registry = VillageAllegianceRegistrySavedData.get(level);
        VillageAllegianceId home = registry.create(
                level.getGameTime(), level.dimension().location(), BlockPos.ZERO, "Roster Home");

        VillageAllegianceApi.assignKnown(level, villager, home, AllegianceAssignmentSource.ADMIN);
        helper.assertTrue(registry.record(home).orElseThrow().residents().containsKey(villager.getUUID()),
                "known explicit assignments add villagers to the canonical roster");

        VillageAllegianceId newHome = registry.create(
                level.getGameTime(), level.dimension().location(), BlockPos.ZERO, "New Roster Home");
        VillageAllegianceApi.assignKnown(level, villager, newHome, AllegianceAssignmentSource.ADMIN);
        helper.assertFalse(registry.record(home).orElseThrow().residents().containsKey(villager.getUUID()),
                "indexed reassignment removes the old roster membership");
        helper.assertTrue(registry.record(newHome).orElseThrow().residents().containsKey(villager.getUUID()),
                "indexed reassignment adds the new roster membership");

        VillageAllegianceApi.assign(villager, VillageAllegianceData.unknown(
                AllegianceAssignmentSource.ADMIN, AllegianceConfidence.AUTHORITATIVE,
                level.getGameTime(), level.dimension().location(), villager.blockPosition()));
        helper.assertFalse(registry.record(newHome).orElseThrow().residents().containsKey(villager.getUUID()),
                "unknown explicit assignments remove stale roster membership");
        helper.assertFalse(registry.residentIds().contains(villager.getUUID()),
                "removing the last membership also clears the runtime resident index");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void centralPolicyProtectsCanonicalVillageAndAllowsForeignOrders(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Villager actor = spawnVillager(helper, new BlockPos(2, 2, 2));
        Villager target = spawnVillager(helper, new BlockPos(4, 2, 2));
        VillageAllegianceRegistrySavedData registry = VillageAllegianceRegistrySavedData.get(level);
        VillageAllegianceId home = registry.create(level.getGameTime(), level.dimension().location(), actor.blockPosition(), "Home");
        VillageAllegianceId alias = registry.create(level.getGameTime(), level.dimension().location(), actor.blockPosition(), "Alias");
        VillageAllegianceId foreign = registry.create(level.getGameTime(), level.dimension().location(), target.blockPosition(), "Foreign");
        registry.merge(alias, home);
        PartyRecord actorParty = PartySavedData.get(level).createParty(UUID.randomUUID(), level.getGameTime());
        PartySavedData.get(level).addVillager(
                actorParty, partyVillagerRecord(actor.getUUID(), actorParty.leaderId(), level.getGameTime()));
        try {
            assign(level, actor, alias, List.of());
            assign(level, target, home, List.of());
            helper.assertValueEqual(VillageAllegianceCombatPolicy.evaluate(
                            level, actor, target, AllegianceCombatContext.PARTY_ATTACK, true).reason(),
                    AllegianceCombatDecision.Reason.SAME_CANONICAL_ALLEGIANCE,
                    "canonical aliases must protect");

            assign(level, target, foreign, List.of(home));
            helper.assertValueEqual(VillageAllegianceCombatPolicy.evaluate(
                            level, actor, target, AllegianceCombatContext.PARTY_ATTACK, false).action(),
                    AllegianceCombatDecision.Action.ALLOW,
                    "legacy parent metadata must not block a foreign-village order");

            assign(level, target, foreign, List.of());
            helper.assertValueEqual(VillageAllegianceCombatPolicy.evaluate(
                            level, actor, target, AllegianceCombatContext.PARTY_ATTACK, false).reason(),
                    AllegianceCombatDecision.Reason.AUTHORIZED_PARTY_CONFLICT,
                    "a foreign target does not need its own party");
            helper.assertValueEqual(VillageAllegianceCombatPolicy.evaluate(
                            level, actor, target, AllegianceCombatContext.PARTY_ATTACK, true).action(),
                    AllegianceCombatDecision.Action.ALLOW,
                    "known different opposing allegiances may be authorized");

            VillageAllegianceEntityData.write(target, VillageAllegianceData.unknown(
                    AllegianceAssignmentSource.MIGRATION, AllegianceConfidence.LEGACY_INFERRED,
                    level.getGameTime(), level.dimension().location(), target.blockPosition()));
            helper.assertValueEqual(VillageAllegianceCombatPolicy.evaluate(
                            level, actor, target, AllegianceCombatContext.PARTY_ATTACK, true).reason(),
                    AllegianceCombatDecision.Reason.UNKNOWN_TARGET,
                    "unknown civilian must deny");
        } finally {
            actor.discard();
            target.discard();
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void recruitedForeignAndWandererDamageButCanonicalMergeStopsResidents(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Villager actor = spawnVillager(helper, new BlockPos(2, 2, 2));
        Villager target = spawnVillager(helper, new BlockPos(3, 2, 2));
        long now = level.getServer().overworld().getGameTime();
        PartySavedData partyData = PartySavedData.get(level);
        PartyRecord actorParty = partyData.createParty(UUID.randomUUID(), now);
        partyData.addVillager(actorParty, partyVillagerRecord(actor.getUUID(), actorParty.leaderId(), now));
        VillageAllegianceRegistrySavedData registry = VillageAllegianceRegistrySavedData.get(level);
        VillageAllegianceId actorHome = registry.create(now, level.dimension().location(), actor.blockPosition(), "Actor");
        VillageAllegianceId targetHome = registry.create(now, level.dimension().location(), target.blockPosition(), "Target");
        assign(level, actor, actorHome, List.of());
        assign(level, target, targetHome, List.of());
        try {
            helper.assertTrue(VillageCombatAuthorizationService.authorize(
                    level, actor, target),
                    "a recruited foreign resident can target a villager without a party");
            float before = target.getHealth();
            helper.assertTrue(target.hurt(level.damageSources().mobAttack(actor), 2.0F),
                    "authorized different known allegiance damage should land");
            helper.assertTrue(target.getHealth() < before, "authorized target health decreases");

            target.invulnerableTime = 0;
            registry.merge(targetHome, actorHome);
            float afterAuthorizedHit = target.getHealth();
            target.hurt(level.damageSources().mobAttack(actor), 2.0F);
            helper.assertValueEqual(target.getHealth(), afterAuthorizedHit,
                    "canonical merge is re-evaluated and stops stale authorized damage");

            VillageAllegianceEntityData.write(actor, VillageAllegianceData.unaffiliated(
                    AllegianceAssignmentSource.ADMIN, now, level.dimension().location(), actor.blockPosition()));
            helper.assertTrue(VillageCombatAuthorizationService.authorize(
                    level, actor, target),
                    "a recruited Wanderer can be authorized against any real village");
            target.invulnerableTime = 0;
            float beforeWandererHit = target.getHealth();
            target.hurt(level.damageSources().mobAttack(actor), 2.0F);
            helper.assertTrue(target.getHealth() < beforeWandererHit,
                    "Wanderer party damage lands without village infighting rules");
        } finally {
            VillageCombatAuthorizationService.clearRuntimeState();
            actor.discard();
            target.discard();
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void registryArchivesAfterObservedGraceAndRebuildsWithNewIdentity(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        VillageAllegianceRegistrySavedData registry = new VillageAllegianceRegistrySavedData();
        VillageAllegianceId original = registry.create(1L, level.dimension().location(), BlockPos.ZERO, "Old Crossing");
        UUID resident = UUID.randomUUID();
        registry.addOrUpdateResident(original, resident, true, 2L);
        registry.observeEmpty(original, VillageAllegianceRegistrySavedData.ARCHIVE_GRACE_TICKS - 1L);
        helper.assertValueEqual(registry.record(original).orElseThrow().lifecycleState(),
                VillageLifecycleState.EMPTY_GRACE, "village remains in empty grace before 72,000 observed ticks");
        registry.observeEmpty(original, 1L);
        helper.assertValueEqual(registry.record(original).orElseThrow().lifecycleState(),
                VillageLifecycleState.ARCHIVED, "village archives exactly at the loaded observation threshold");

        VillageAllegianceId rebuilt = registry.create(72_001L, level.dimension().location(), BlockPos.ZERO, "New Crossing");
        helper.assertFalse(rebuilt.equals(original), "rebuilding creates a new durable identity");
        helper.assertTrue(registry.record(rebuilt).orElseThrow().lifecycleState() == VillageLifecycleState.ACTIVE,
                "rebuilt identity starts active");
        helper.assertTrue(VillageAllegianceRegistrySavedData.validateVillageName("  North   Reach ").orElseThrow()
                .equals("North Reach"), "village names normalize whitespace");
        helper.assertTrue(VillageAllegianceRegistrySavedData.validateVillageName("Bad\u00a7Name").isEmpty(),
                "formatting codes are rejected");
        helper.assertTrue(registry.rename(rebuilt, "North Reach"), "unique valid rename succeeds");
        VillageAllegianceId other = registry.create(72_002L, level.dimension().location(), BlockPos.ZERO, "Other");
        helper.assertFalse(registry.rename(other, "north reach"), "village names are unique case-insensitively");

        VillageAllegianceRegistrySavedData restored = VillageAllegianceRegistrySavedData.load(
                registry.save(new CompoundTag(), level.registryAccess()), level.registryAccess());
        helper.assertTrue(restored.record(original).orElseThrow().residents().containsKey(resident),
                "resident roster survives persistence even after archive");
        helper.assertTrue(restored.residentIds().contains(resident),
                "the runtime resident index rebuilds from persisted rosters");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void migrationCanonicalizesResidentsAndOutsideVillagersBecomeWanderers(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Villager villager = createVillager(level);
        villager.moveTo(helper.absolutePos(new BlockPos(2, 2, 2)), 0.0F, 0.0F);
        VillageAllegianceEntityData.write(villager, new VillageAllegianceData(
                1, AllegianceState.KNOWN, VillageAllegianceId.random(), AllegianceAssignmentSource.MIGRATION,
                AllegianceConfidence.LEGACY_INFERRED, 1L, level.dimension().location(), villager.blockPosition(), List.of()));
        helper.assertTrue(VillageAllegianceService.retryMigration(level, villager), "legacy migration completes");
        helper.assertValueEqual(VillageAllegianceApi.get(villager).orElseThrow().state(), AllegianceState.UNAFFILIATED,
                "an outside villager migrates to Wanderer instead of inheriting a nearby scope");

        VillageAllegianceRegistrySavedData registry = VillageAllegianceRegistrySavedData.get(level);
        VillageAllegianceId alias = registry.create(level.getGameTime(), level.dimension().location(), villager.blockPosition(), "Alias Home");
        VillageAllegianceId canonical = registry.create(level.getGameTime(), level.dimension().location(), villager.blockPosition(), "Canonical Home");
        helper.assertTrue(registry.merge(alias, canonical), "test alias merge succeeds");
        VillageAllegianceApi.assignKnown(level, villager, alias, AllegianceAssignmentSource.ADMIN);
        helper.assertValueEqual(VillageAllegianceApi.get(villager).orElseThrow().primary(), canonical,
                "loaded residents normalize directly to the canonical identity");
        helper.assertTrue(registry.record(canonical).orElseThrow().residents().containsKey(villager.getUUID()),
                "canonical roster tracks the normalized resident");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void reactiveGolemDefenseAndProjectileAuthorizationRecheckCanonicalLoyalty(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Villager actor = spawnVillager(helper, new BlockPos(2, 2, 2));
        Villager resident = spawnVillager(helper, new BlockPos(3, 2, 2));
        IronGolem golem = EntityType.IRON_GOLEM.create(level);
        Entity projectile = EntityType.SNOWBALL.create(level);
        helper.assertTrue(golem != null && projectile != null, "test entities created");
        long now = level.getGameTime();
        PartyRecord party = PartySavedData.get(level).createParty(UUID.randomUUID(), now);
        PartySavedData.get(level).addVillager(party, partyVillagerRecord(actor.getUUID(), party.leaderId(), now));
        VillageAllegianceRegistrySavedData registry = VillageAllegianceRegistrySavedData.get(level);
        VillageAllegianceId actorHome = registry.create(now, level.dimension().location(), actor.blockPosition(), "Golem Actor");
        VillageAllegianceId villageHome = registry.create(now, level.dimension().location(), resident.blockPosition(), "Golem Village");
        assign(level, actor, actorHome, List.of());
        assign(level, resident, villageHome, List.of());
        VillageAllegianceApi.assignKnown(level, golem, villageHome, AllegianceAssignmentSource.ADMIN);
        try {
            helper.assertValueEqual(VillageAllegianceCombatPolicy.evaluate(
                            level, actor, golem, AllegianceCombatContext.PARTY_ATTACK, false).reason(),
                    AllegianceCombatDecision.Reason.GOLEM_RESTRICTED,
                    "party villagers cannot proactively attack a village golem");
            helper.assertValueEqual(VillageAllegianceCombatPolicy.evaluate(
                            level, actor, golem, AllegianceCombatContext.PARTY_DEFEND, false).action(),
                    AllegianceCombatDecision.Action.ALLOW,
                    "a foreign golem that lands hostility becomes a reactive defense target");
            helper.assertTrue(VillageCombatAuthorizationService.authorize(
                    level, actor, resident),
                    "foreign resident receives party authorization");
            VillageCombatAuthorizationService.associateProjectile(projectile, actor, resident);
            helper.assertTrue(VillageCombatAuthorizationService.projectileAuthorized(projectile, actor, resident),
                    "projectile inherits the bounded authorization");
            registry.merge(villageHome, actorHome);
            helper.assertValueEqual(VillageAllegianceCombatPolicy.evaluate(
                            level, actor, resident, AllegianceCombatContext.DAMAGE,
                            VillageCombatAuthorizationService.projectileAuthorized(projectile, actor, resident)).reason(),
                    AllegianceCombatDecision.Reason.SAME_CANONICAL_ALLEGIANCE,
                    "a merge invalidates projectile damage through canonical policy recheck");

            VillageAllegianceEntityData.write(resident, VillageAllegianceData.unaffiliated(
                    AllegianceAssignmentSource.ADMIN, now, level.dimension().location(), resident.blockPosition()));
            helper.assertFalse(VillageAllegianceRelations.sharesCommunity(level, actor, resident),
                    "Wanderers never receive village-scoped gossip or community loyalty");
        } finally {
            VillageCombatAuthorizationService.clearRuntimeState();
            actor.discard();
            resident.discard();
            golem.discard();
            projectile.discard();
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void namingTrustAndDebugFootprintLimitsStayBounded(GameTestHelper helper) {
        helper.assertFalse(VillageNamingService.evaluateTrustGate(3, 1, false).allowed(),
                "one trusted resident is not half of three adults");
        helper.assertTrue(VillageNamingService.evaluateTrustGate(3, 2, false).allowed(),
                "two trusted residents satisfy the rounded-up half gate");
        helper.assertTrue(VillageNamingService.evaluateTrustGate(0, 0, true).allowed(),
                "operators bypass an empty roster gate");
        VillageAllegianceRegistrySavedData rosterRegistry = new VillageAllegianceRegistrySavedData();
        VillageAllegianceId rosterVillage = rosterRegistry.create(
                0L, helper.getLevel().dimension().location(), BlockPos.ZERO, "Fresh Roster");
        rosterRegistry.addOrUpdateResident(rosterVillage, UUID.randomUUID(), true, 0L);
        helper.assertValueEqual(
                rosterRegistry.record(rosterVillage).orElseThrow()
                        .activeAdultResidents(VillageAllegianceRegistrySavedData.RESIDENT_ACTIVE_GRACE_TICKS + 1L).size(),
                0, "stale roster entries do not count toward village trust gates");

        List<Long> oversized = new ArrayList<>();
        for (int index = 0; index < 600; index++) {
            oversized.add(SectionPos.asLong(index, 4, 0));
        }
        VillageBoundsSyncPayload.VillageEntry entry = new VillageBoundsSyncPayload.VillageEntry(
                VillageAllegianceId.random(), "Bounded", new BlockPos(1, 65, 1),
                VillageLifecycleState.ACTIVE, oversized);
        helper.assertValueEqual(entry.sections().size(), VillageBoundsSyncPayload.MAX_SECTIONS_PER_VILLAGE,
                "one footprint cannot exceed its codec section cap");
        helper.assertTrue(entry.contains(new BlockPos(1, 65, 1)), "footprint containment uses the actual section set");
        helper.assertFalse(entry.contains(new BlockPos(-33, 65, 1)), "outside section is not contained");
        List<VillageBoundsSyncPayload.VillageEntry> entries = new ArrayList<>();
        VillageBoundsSyncPayload.VillageEntry emptyEntry = new VillageBoundsSyncPayload.VillageEntry(
                VillageAllegianceId.random(), "Empty", BlockPos.ZERO, VillageLifecycleState.EMPTY_GRACE, List.of());
        for (int index = 0; index < 70; index++) {
            entries.add(emptyEntry);
        }
        VillageBoundsSyncPayload payload = new VillageBoundsSyncPayload(
                true, helper.getLevel().dimension().location(), entries, 120);
        helper.assertValueEqual(payload.villages().size(), VillageBoundsSyncPayload.MAX_VILLAGES,
                "preview stream clamps village count before encoding");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void villageScopeLookupsDoNotDiscoverAllegiances(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos center = helper.absolutePos(new BlockPos(2, 2, 2)).offset(1_000_000, 0, 1_000_000);
        VillageMembership.VillageArea area = new VillageMembership.VillageArea(
                level, Vec3.atCenterOf(center), 32.0D, List.of(), false);
        VillageAllegianceRegistrySavedData allegianceRegistry = VillageAllegianceRegistrySavedData.get(level);
        int allegiancesBefore = allegianceRegistry.activeRecords(level.dimension().location()).size();

        String first = VillageScopeKeys.forArea(level, area);
        String second = VillageScopeKeys.forArea(level, area);

        helper.assertTrue(VillageScopeKeys.isVillageKey(first), "scope lookup returns a stable village key");
        helper.assertValueEqual(second, first, "repeated scope lookups are stable");
        helper.assertValueEqual(
                allegianceRegistry.activeRecords(level.dimension().location()).size(),
                allegiancesBefore,
                "scope lookup must not discover an allegiance record");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void villageFootprintsIncludeTaggedStructuresAndConnectedTerrain(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.assertTrue(Blocks.DIRT_PATH.defaultBlockState().is(VillagerRetaliationTags.Blocks.VILLAGE_TERRAIN),
                "vanilla paths are included by the data-driven terrain tag");
        var structureRegistry = level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE);
        for (var village : List.of(
                BuiltinStructures.VILLAGE_PLAINS,
                BuiltinStructures.VILLAGE_DESERT,
                BuiltinStructures.VILLAGE_SAVANNA,
                BuiltinStructures.VILLAGE_SNOWY,
                BuiltinStructures.VILLAGE_TAIGA)) {
            helper.assertTrue(structureRegistry.getHolderOrThrow(village)
                            .is(VillagerRetaliationTags.Structures.VILLAGE_FOOTPRINT),
                    "every vanilla village structure is included by the footprint structure tag");
        }

        BlockPos basePos = helper.absolutePos(new BlockPos(2, 2, 2));
        SectionPos base = SectionPos.of(basePos);
        BlockPos connectedPath = new BlockPos(
                SectionPos.sectionToBlockCoord(base.x() + 1), basePos.getY(), basePos.getZ());
        BlockPos disconnectedPath = new BlockPos(
                SectionPos.sectionToBlockCoord(base.x() + 2), basePos.getY(), basePos.getZ());
        BlockPos diagonalPath = new BlockPos(
                SectionPos.sectionToBlockCoord(base.x() - 1), basePos.getY(),
                SectionPos.sectionToBlockCoord(base.z() + 1));
        level.setBlock(connectedPath, Blocks.DIRT_PATH.defaultBlockState(), 3);
        level.setBlock(disconnectedPath, Blocks.DIRT_PATH.defaultBlockState(), 3);
        level.setBlock(diagonalPath, Blocks.DIRT_PATH.defaultBlockState(), 3);
        Set<Long> footprint = VillageFootprintResolver.resolve(
                level, Set.of(base.asLong()), basePos, 64);
        helper.assertTrue(footprint.contains(SectionPos.asLong(connectedPath)),
                "a tagged terrain section connected to the village extends its footprint");
        helper.assertFalse(footprint.contains(SectionPos.asLong(disconnectedPath)),
                "adjacent terrain sections do not connect unless their tagged blocks do");
        helper.assertFalse(footprint.contains(SectionPos.asLong(diagonalPath)),
                "corner-touching terrain alone does not extend a village footprint");
        helper.assertFalse(footprint.contains(SectionPos.asLong(base.x(), base.y() + 1, base.z())),
                "village padding does not create an empty section above the village");
        helper.assertFalse(footprint.contains(SectionPos.asLong(base.x(), base.y() - 1, base.z())),
                "village padding does not create an empty section below the village");
        helper.succeed();
    }

    private static void assign(ServerLevel level, Villager villager, VillageAllegianceId id, List<VillageAllegianceId> parents) {
        VillageAllegianceEntityData.write(villager, VillageAllegianceData.known(
                id, AllegianceAssignmentSource.ADMIN, AllegianceConfidence.AUTHORITATIVE,
                level.getGameTime(), level.dimension().location(), villager.blockPosition(), parents));
    }

    private static PartyVillagerRecord partyVillagerRecord(UUID villagerId, UUID leaderId, long now) {
        return new PartyVillagerRecord(
                villagerId,
                leaderId,
                UUID.randomUUID(),
                0,
                PartyCommandMode.FOLLOW,
                null,
                null,
                now,
                VillagerContractTime.endAfterDays(now, 1),
                1,
                32,
                "Test villager",
                "minecraft:none",
                net.minecraft.world.level.Level.OVERWORLD.location(),
                BlockPos.ZERO);
    }

    private static Villager roundTrip(ServerLevel level, Villager source) {
        CompoundTag tag = new CompoundTag();
        source.saveWithoutId(tag);
        Villager restored = createVillager(level);
        restored.load(tag);
        return restored;
    }

    private static Villager createVillager(ServerLevel level) {
        Villager villager = EntityType.VILLAGER.create(level);
        if (villager == null) {
            throw new GameTestAssertException("Could not create villager");
        }
        return villager;
    }

    private static Villager spawnVillager(GameTestHelper helper, BlockPos relativePos) {
        Villager villager = createVillager(helper.getLevel());
        BlockPos pos = helper.absolutePos(relativePos);
        villager.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);
        if (!helper.getLevel().addFreshEntity(villager)) {
            throw new GameTestAssertException("Could not spawn villager");
        }
        return villager;
    }
}
