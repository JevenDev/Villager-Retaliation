package com.jvn.villagerretaliation.allegiance;

import com.jvn.villagerretaliation.event.VillagerEventTriggerSavedData;
import com.jvn.villagerretaliation.quest.VillagerQuestFacts;
import com.jvn.villagerretaliation.quest.VillagerQuestSavedData;
import com.jvn.villagerretaliation.social.VillagerSocialGraphSavedData;
import com.jvn.villagerretaliation.village.VillageScopeKeys;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.ZombieVillager;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class VillageRegistrationRegressionGameTests {
    private static final String EMPTY_TEMPLATE = "empty";

    private VillageRegistrationRegressionGameTests() {
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void chainedMergesUndoOnlyInReverseOrder(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        VillageAllegianceRegistrySavedData registry = new VillageAllegianceRegistrySavedData();
        VillageAllegianceId first = registry.create(
                1L, level.dimension().location(), BlockPos.ZERO, "Undo First");
        VillageAllegianceId second = registry.create(
                2L, level.dimension().location(), new BlockPos(32, 0, 0), "Undo Second");
        VillageAllegianceId third = registry.create(
                3L, level.dimension().location(), new BlockPos(64, 0, 0), "Undo Third");
        UUID resident = UUID.randomUUID();
        registry.addOrUpdateResident(first, resident, true, 4L);

        helper.assertTrue(registry.merge(first, second), "first merge succeeds");
        helper.assertTrue(registry.merge(second, third), "second merge succeeds");
        helper.assertFalse(registry.undoMerge(first),
                "an older link cannot be undone while its target remains aliased");
        helper.assertValueEqual(registry.canonical(first).orElseThrow(), third,
                "rejected undo leaves the alias chain intact");

        helper.assertTrue(registry.undoMerge(second), "latest link can be undone first");
        helper.assertTrue(registry.undoMerge(first), "older link can then be undone");
        helper.assertValueEqual(registry.canonical(first).orElseThrow(), first,
                "source identity is restored after reverse-order undo");
        helper.assertTrue(registry.record(first).orElseThrow().residents().containsKey(resident),
                "restored source retains its resident");
        helper.assertFalse(registry.record(second).orElseThrow().residents().containsKey(resident),
                "intermediate target no longer duplicates the resident");
        helper.assertFalse(registry.record(third).orElseThrow().residents().containsKey(resident),
                "canonical target no longer duplicates the resident");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void futureRegistryFormatsFailClosed(GameTestHelper helper) {
        CompoundTag future = new CompoundTag();
        future.putInt("FormatVersion", 4);
        boolean rejected = false;
        try {
            VillageAllegianceRegistrySavedData.load(future, helper.getLevel().registryAccess());
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        helper.assertTrue(rejected,
                "a newer registry format must fail instead of loading an empty overwriteable registry");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void canonicalMergeMigratesPositionScopedVillageState(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        VillageAllegianceRegistrySavedData registry = VillageAllegianceRegistrySavedData.get(level);
        BlockPos sourcePos = helper.absolutePos(new BlockPos(1, 2, 1));
        BlockPos targetPos = sourcePos.offset(128, 0, 0);
        VillageAllegianceId source = registry.create(
                level.getGameTime(), level.dimension().location(), sourcePos, "Scoped Source");
        VillageAllegianceId target = registry.create(
                level.getGameTime() + 1L, level.dimension().location(), targetPos, "Scoped Target");
        String sourceKey = VillageScopeKeys.forPosition(level.dimension(), sourcePos);
        String targetKey = VillageScopeKeys.forPosition(level.dimension(), targetPos);
        ResourceLocation factTag = ResourceLocation.fromNamespaceAndPath(
                "villagerretaliation", "test/village_merge_" + source.value());

        VillagerQuestFacts facts = VillagerQuestFacts.get(level);
        facts.setTag(sourceKey, factTag);
        UUID playerId = UUID.randomUUID();
        ResourceLocation questId = ResourceLocation.fromNamespaceAndPath(
                "villagerretaliation", "test/scoped_merge_" + source.value());
        VillagerQuestSavedData.QuestProgress progress =
                VillagerQuestSavedData.get(level).getOrCreate(playerId, questId);
        progress.setIssuer(
                UUID.randomUUID(), "Issuer", "minecraft:farmer", 1,
                level.dimension(), sourcePos, sourceKey);

        String triggerId = "villagerretaliation:test_merge_" + source.value();
        VillagerEventTriggerSavedData cooldowns = VillagerEventTriggerSavedData.get(level);
        cooldowns.markRun(triggerId + "|" + sourceKey, 20L);

        Villager villager = EntityType.VILLAGER.create(level);
        if (villager == null) {
            throw new IllegalStateException("Could not create social-profile villager");
        }
        villager.moveTo(sourcePos.getX() + 0.5D, sourcePos.getY(), sourcePos.getZ() + 0.5D);
        villager.getBrain().setMemory(
                MemoryModuleType.HOME, GlobalPos.of(level.dimension(), sourcePos));
        helper.assertTrue(level.addFreshEntity(villager), "social-profile villager enters the test level");
        VillageAllegianceEntityData.write(villager, VillageAllegianceData.known(
                source, AllegianceAssignmentSource.ADMIN, AllegianceConfidence.AUTHORITATIVE,
                level.getGameTime(), level.dimension().location(), sourcePos, List.of()));
        VillagerSocialGraphSavedData social = VillagerSocialGraphSavedData.get(level);
        social.ensureProfile(level, villager);
        helper.assertValueEqual(social.knownVillage(villager.getUUID()).orElseThrow(), sourceKey,
                "test profile starts in the source scope");

        helper.assertTrue(registry.merge(level, source, target), "server-aware merge succeeds");
        helper.assertFalse(facts.hasTag(sourceKey, factTag), "source fact bucket is removed");
        helper.assertTrue(facts.hasTag(targetKey, factTag), "facts follow the canonical target");
        helper.assertValueEqual(progress.issuerVillageKey(), targetKey,
                "active quest issuer scope follows the canonical target");
        helper.assertValueEqual(cooldowns.lastRunGameTime(triggerId + "|" + targetKey), 20L,
                "village event cooldown follows the canonical target");
        helper.assertValueEqual(social.knownVillage(villager.getUUID()).orElseThrow(), targetKey,
                "social village key follows the canonical target");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void canonicalNormalizationPreservesBabyParentCommunities(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        VillageAllegianceRegistrySavedData registry = VillageAllegianceRegistrySavedData.get(level);
        BlockPos origin = helper.absolutePos(new BlockPos(2, 2, 2)).offset(1_600_000, 0, 1_600_000);
        VillageAllegianceId source = registry.create(
                level.getGameTime(), level.dimension().location(), origin, "Baby Source");
        VillageAllegianceId otherParent = registry.create(
                level.getGameTime() + 1L, level.dimension().location(), origin.offset(64, 0, 0), "Other Parent");
        VillageAllegianceId target = registry.create(
                level.getGameTime() + 2L, level.dimension().location(), origin.offset(128, 0, 0), "Baby Target");
        Villager child = EntityType.VILLAGER.create(level);
        if (child == null) {
            throw new IllegalStateException("Could not create baby villager");
        }
        child.setAge(-24_000);
        VillageAllegianceData data = VillageAllegianceData.known(
                source, AllegianceAssignmentSource.BIRTH, AllegianceConfidence.INHERITED,
                level.getGameTime(), level.dimension().location(), origin,
                List.of(source, otherParent));
        VillageAllegianceEntityData.write(child, data);

        helper.assertTrue(registry.merge(level, source, target), "baby home merge succeeds");
        VillageAllegianceService.normalizeAndTrack(level, child, data);
        VillageAllegianceData normalized = VillageAllegianceApi.get(child).orElseThrow();
        helper.assertValueEqual(normalized.primary(), target, "baby primary becomes canonical");
        helper.assertValueEqual(normalized.protectedParents(), List.of(source, otherParent),
                "canonical rewrite retains both protected parent communities");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void unresolvedRepairImmediatelyRemovesOldRosterEntry(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        VillageAllegianceRegistrySavedData registry = VillageAllegianceRegistrySavedData.get(level);
        VillageAllegianceId oldHome = registry.create(
                level.getGameTime(), level.dimension().location(),
                helper.absolutePos(new BlockPos(3, 2, 3)), "Repair Source");
        ZombieVillager zombie = EntityType.ZOMBIE_VILLAGER.create(level);
        if (zombie == null) {
            throw new IllegalStateException("Could not create zombie villager");
        }
        registry.addOrUpdateResident(oldHome, zombie.getUUID(), true, level.getGameTime());
        helper.assertTrue(registry.record(oldHome).orElseThrow().residents().containsKey(zombie.getUUID()),
                "test entity starts in the old roster");

        helper.assertFalse(VillageAllegianceService.retryMigration(level, zombie),
                "unsupported assignment target remains pending");
        helper.assertFalse(registry.record(oldHome).orElseThrow().residents().containsKey(zombie.getUUID()),
                "unknown repair state is removed from the old village roster immediately");
        helper.succeed();
    }
}
