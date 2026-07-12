package com.jvn.villagerretaliation.party;

import com.jvn.villagerretaliation.allegiance.AllegianceAssignmentSource;
import com.jvn.villagerretaliation.allegiance.AllegianceCombatContext;
import com.jvn.villagerretaliation.allegiance.AllegianceCombatDecision;
import com.jvn.villagerretaliation.allegiance.AllegianceConfidence;
import com.jvn.villagerretaliation.allegiance.AllegianceState;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceCombatPolicy;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceData;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceEntityData;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.allegiance.VillageCombatAuthorizationService;
import com.jvn.villagerretaliation.interaction.VillagerContractTime;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
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

        VillageAllegianceEntityData.write(source, VillageAllegianceData.unknown(
                AllegianceAssignmentSource.MIGRATION, AllegianceConfidence.LEGACY_INFERRED,
                43L, level.dimension().location(), BlockPos.ZERO));
        helper.assertValueEqual(
                VillageAllegianceEntityData.read(roundTrip(level, source)).orElseThrow().state(),
                AllegianceState.UNKNOWN,
                "unknown state round-trip");

        VillageAllegianceEntityData.write(source, VillageAllegianceData.unaffiliated(
                AllegianceAssignmentSource.EXPLICIT_API, 44L, level.dimension().location(), BlockPos.ZERO));
        helper.assertValueEqual(
                VillageAllegianceEntityData.read(roundTrip(level, source)).orElseThrow().state(),
                AllegianceState.UNAFFILIATED,
                "unaffiliated state round-trip");

        CompoundTag malformed = new CompoundTag();
        malformed.putInt("DataVersion", VillageAllegianceData.CURRENT_VERSION + 1);
        source.getPersistentData().put(VillageAllegianceEntityData.ROOT_TAG, malformed);
        helper.assertValueEqual(VillageAllegianceEntityData.read(source).orElseThrow().state(),
                AllegianceState.UNKNOWN, "unsupported future data is conservative");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void registryAliasesScopesAndTombstonesRoundTrip(GameTestHelper helper) {
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
        registry.addScopeCandidate("scope:a", first);
        registry.addScopeCandidate("scope:b", second);
        registry.addScopeCandidate("scope:ambiguous", first);
        VillageAllegianceId fourth = registry.create(4L, level.dimension().location(), BlockPos.ZERO, "Fourth");
        registry.addScopeCandidate("scope:ambiguous", fourth);

        CompoundTag saved = registry.save(new CompoundTag(), level.registryAccess());
        VillageAllegianceRegistrySavedData restored = VillageAllegianceRegistrySavedData.load(saved, level.registryAccess());
        helper.assertValueEqual(restored.canonical(first).orElseThrow(), third, "saved alias chain");
        helper.assertTrue(restored.record(first).orElseThrow().archived(), "tombstone persistence");
        helper.assertValueEqual(restored.uniqueCandidate("scope:a").orElseThrow(), third,
                "multiple scopes may resolve to one canonical allegiance");
        helper.assertTrue(restored.uniqueCandidate("scope:ambiguous").isEmpty(),
                "one scope may retain multiple candidates");
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
                    level, actor, target, AllegianceCombatContext.PARTY_ATTACK),
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
                    level, actor, target, AllegianceCombatContext.PARTY_ATTACK),
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
