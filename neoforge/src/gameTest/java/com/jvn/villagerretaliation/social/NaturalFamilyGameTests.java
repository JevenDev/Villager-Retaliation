package com.jvn.villagerretaliation.social;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.interaction.HiredVillagerContractService;
import com.jvn.villagerretaliation.interaction.HiredWorkStateStore;
import com.jvn.villagerretaliation.villager.VillagerBehaviorSuppressionPolicy;
import com.jvn.villagerretaliation.villager.VillagerGender;
import com.jvn.villagerretaliation.villager.VillagerPresetNameRegistry;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.entity.schedule.Schedule;
import net.minecraft.world.entity.schedule.ScheduleBuilder;
import net.neoforged.neoforge.event.entity.living.BabyEntitySpawnEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("villager_life")
@PrefixGameTestTemplate(false)
public final class NaturalFamilyGameTests {
    private NaturalFamilyGameTests() {
    }

    @GameTest(template = "empty", batch = "natural_families")
    public static void visitsNeedThreeDistinctDaysAndRespectExclusivePartners(GameTestHelper helper) {
        natural(helper, level -> {
            var graph = VillagerSocialGraphSavedData.get(level);
            var first = adult(level);
            var second = adult(level);
            var third = adult(level);
            helper.assertTrue(graph.recordCourtshipVisit(level, first, second), "the first visit creates a crush");
            helper.assertValueEqual(graph.relationshipStage(first.getUUID(), second.getUUID()).orElseThrow(),
                    VillagerRelationshipStage.CRUSH, "first visit stage");
            helper.assertFalse(graph.recordCourtshipVisit(level, second, first), "a reversed pair cannot count twice today");
            level.setDayTime(level.getDayTime() + 24_000);
            graph.recordCourtshipVisit(level, second, first);
            helper.assertValueEqual(graph.relationshipStage(first.getUUID(), second.getUUID()).orElseThrow(),
                    VillagerRelationshipStage.CRUSH, "two days still need a third visit");
            var saved = graph.save(new CompoundTag(), level.registryAccess());
            var restored = VillagerSocialGraphSavedData.load(saved, level.registryAccess());
            helper.assertFalse(restored.recordCourtshipVisit(level, first, second), "reload preserves today's visit");
            level.setDayTime(level.getDayTime() + 24_000);
            restored.recordCourtshipVisit(level, first, second);
            helper.assertValueEqual(restored.relationshipStage(first.getUUID(), second.getUUID()).orElseThrow(),
                    VillagerRelationshipStage.DATING, "the third distinct day starts dating");
            helper.assertFalse(restored.recordCourtshipVisit(level, first, third), "dating excludes another automatic crush");
        });
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "natural_families")
    public static void everyCapCountsLivingBiologicalChildrenAtAnyAge(GameTestHelper helper) {
        natural(helper, level -> {
            for (int limit = 1; limit <= 4; limit++) {
                var graph = new VillagerSocialGraphSavedData();
                var first = adult(level);
                var second = adult(level);
                graph.linkSpouses(level, first, second);
                int rolled = graph.childLimit(first.getUUID(), second.getUUID());
                helper.assertTrue(rolled >= 1 && rolled <= 4, "new partnerships receive a valid random cap");
                var saved = graph.save(new CompoundTag(), level.registryAccess());
                saved.getList("RomanticBonds", Tag.TAG_COMPOUND).getCompound(0).putInt("ChildLimit", limit);
                graph = VillagerSocialGraphSavedData.load(saved, level.registryAccess());
                Villager child = null;
                for (int index = 0; index <= limit; index++) {
                    child = adult(level);
                    graph.linkParentsAndChild(level, first, second, child);
                }
                helper.assertValueEqual(graph.livingBiologicalChildren(first.getUUID(), second.getUUID()), limit + 1,
                        "recorded adult children count, including preexisting families above their cap");
                graph.markDead(level, child, "test");
                helper.assertValueEqual(graph.livingBiologicalChildren(first.getUUID(), second.getUUID()), limit,
                        "a child's death removes only that living child");
                var adoption = adult(level);
                adoption.setAge(-24_000);
                graph.linkAdoptiveParentsAndChild(level, first, second, adoption);
                helper.assertValueEqual(graph.livingBiologicalChildren(first.getUUID(), second.getUUID()), limit,
                        "adopted children do not consume biological slots");
                var unrelatedParent = adult(level);
                graph.linkParentsAndChild(level, first, unrelatedParent, adult(level));
                helper.assertValueEqual(graph.livingBiologicalChildren(first.getUUID(), second.getUUID()), limit,
                        "children of a different partnership do not consume this pair's slots");
                graph.setRomanticRelationshipStage(level, first, second, VillagerRelationshipStage.SEPARATED);
                graph.setRomanticRelationshipStage(level, first, second, VillagerRelationshipStage.DATING);
                helper.assertValueEqual(graph.childLimit(first.getUUID(), second.getUUID()), limit, "reconciliation retains the cap");
                UUID converted = UUID.randomUUID();
                graph.transferIdentity(first.getUUID(), converted);
                graph = VillagerSocialGraphSavedData.load(graph.save(new CompoundTag(), level.registryAccess()), level.registryAccess());
                helper.assertValueEqual(graph.childLimit(converted, second.getUUID()), limit, "conversion and reload retain the cap");
                helper.assertValueEqual(graph.livingBiologicalChildren(converted, second.getUUID()), limit, "conversion migrates parentage");
            }
        });
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "natural_families")
    public static void acceptedBirthsAreIdempotentAndCooldownsUseElapsedTime(GameTestHelper helper) {
        natural(helper, level -> {
            var first = adult(level);
            var second = adult(level);
            var child = adult(level);
            child.setAge(-24_000);
            var graph = VillagerSocialGraphSavedData.get(level);
            graph.linkSpouses(level, first, second);
            long now = level.getGameTime();
            VillagerBirthService.initializeNewborn(level, first, second, child, null);
            VillagerBirthService.initializeNewborn(level, first, second, child, null);
            helper.assertValueEqual(graph.livingBiologicalChildren(first.getUUID(), second.getUUID()), 1, "initialization links once");
            helper.assertFalse(graph.birthCooldownReady(first.getUUID(), now + 71_999, 72_000), "three full days must elapse");
            helper.assertTrue(graph.birthCooldownReady(first.getUUID(), now + 72_000, 72_000), "cooldown expires at its elapsed boundary");
            level.setDayTime(level.getDayTime() + 240_000);
            helper.assertValueEqual(VillagerBreedingPolicy.evaluateParent(level, first).reason(), BreedingBlockReason.BIRTH_COOLDOWN,
                    "changing the daylight clock does not bypass elapsed cooldowns");
            var loaded = VillagerSocialGraphSavedData.load(graph.save(new CompoundTag(), level.registryAccess()), level.registryAccess());
            UUID converted = UUID.randomUUID();
            loaded.transferIdentity(first.getUUID(), converted);
            helper.assertFalse(loaded.birthCooldownReady(converted, now + 100, 72_000), "conversion and reload preserve parent cooldown");
            helper.assertTrue(child.isBaby(), "newborn sanitation preserves vanilla age");
        });
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "natural_families")
    public static void canceledOrUnspawnedChildrenDoNotStartCooldowns(GameTestHelper helper) {
        natural(helper, level -> {
            var first = adult(level);
            var second = adult(level);
            var graph = VillagerSocialGraphSavedData.get(level);
            graph.linkSpouses(level, first, second);
            var child = adult(level);
            var event = new BabyEntitySpawnEvent(first, second, child);
            VillagerBirthService.onBabyEntitySpawn(event);
            helper.assertFalse(event.isCanceled(), "the valid proposed birth passes validation");
            event.setCanceled(true);
            VillagerBirthService.finishAcceptedSpawns();
            helper.assertTrue(graph.birthCooldownReady(first.getUUID(), level.getGameTime(), 72_000), "an unaccepted birth has no cooldown");
            helper.assertValueEqual(graph.livingBiologicalChildren(first.getUUID(), second.getUUID()), 0, "an unspawned child has no parentage");
        });
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "natural_families")
    public static void naturalRulesRequireCouplesAndCannotBypassKinship(GameTestHelper helper) {
        natural(helper, level -> {
            var first = adult(level);
            var second = adult(level);
            var graph = VillagerSocialGraphSavedData.get(level);
            helper.assertValueEqual(VillagerBreedingPolicy.evaluatePair(level, first, second).reason(),
                    BreedingBlockReason.RELATIONSHIP_REQUIRED, "available strangers cannot breed");
            graph.setRomanticRelationshipStage(level, first, second, VillagerRelationshipStage.CRUSH);
            helper.assertValueEqual(VillagerBreedingPolicy.evaluatePair(level, first, second).reason(),
                    BreedingBlockReason.RELATIONSHIP_REQUIRED, "a crush is not a breeding couple");
            graph.setRomanticRelationshipStage(level, first, second, VillagerRelationshipStage.DATING);
            helper.assertTrue(VillagerBreedingPolicy.evaluatePair(level, first, second).allowed(), "dating adults pass the family gate");
            int cap = graph.childLimit(first.getUUID(), second.getUUID());
            Villager child = null;
            for (int index = 0; index < cap; index++) {
                child = adult(level);
                graph.linkParentsAndChild(level, first, second, child);
            }
            helper.assertValueEqual(VillagerBreedingPolicy.evaluatePair(level, first, second).reason(),
                    BreedingBlockReason.FAMILY_LIMIT, "the living child cap is authoritative");
            graph.markDead(level, child, "test");
            helper.assertTrue(VillagerBreedingPolicy.evaluatePair(level, first, second).allowed(), "a death reopens a slot");
            var relative = adult(level);
            graph.linkParentsAndChild(level, first, second, relative);
            helper.assertFalse(graph.recordCourtshipVisit(level, first, relative), "natural courtship rejects close relatives");
            helper.assertValueEqual(VillagerBreedingPolicy.evaluatePair(level, first, relative).reason(),
                    BreedingBlockReason.INVALID_FAMILY_PAIR, "natural rules enforce kinship even with legacy family rules off");
            VillagerRetaliationConfig.ENABLE_NATURAL_FAMILIES.set(false);
            helper.assertTrue(VillagerBreedingPolicy.evaluatePair(level, first, relative).allowed(), "disabling restores legacy rules");
            VillagerRetaliationConfig.ENABLE_NATURAL_FAMILIES.set(true);
            VillagerRetaliationConfig.ENABLE_VILLAGER_SOCIAL_GRAPH.set(false);
            helper.assertTrue(VillagerBreedingPolicy.evaluatePair(level, first, relative).allowed(), "disabling the graph restores legacy rules");
        });
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "natural_families")
    public static void settledIdleHiresCanParticipateWithoutReleasingOtherSuppression(GameTestHelper helper) {
        natural(helper, level -> {
            var first = adult(level);
            var player = net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(level);
            HiredVillagerContractService.startHireContract(level, first, player, 1, 0);
            first.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), BlockPos.ZERO));
            HiredWorkStateStore.state(first).putBoolean("Enabled", false);
            helper.assertTrue(VillagerBreedingPolicy.evaluateParent(level, first).allowed(), "a settled available hire can breed off duty");
            helper.assertFalse(VillagerBehaviorSuppressionPolicy.suppresses(first, VillagerBehaviorSuppressionPolicy.Behavior.BREEDING),
                    "the blanket hired breeding suppression yields to the family gate");
            helper.assertTrue(VillagerBehaviorSuppressionPolicy.suppresses(first, VillagerBehaviorSuppressionPolicy.Behavior.JOB_SITE_CLAIMING),
                    "unrelated hired suppression stays intact");
            first.setTradingPlayer(player);
            helper.assertValueEqual(VillagerBreedingPolicy.evaluateParent(level, first).reason(), BreedingBlockReason.CONTROLLED_ACTIVITY,
                    "trading blocks participation");
            first.setTradingPlayer(null);
            first.getBrain().setSchedule(new ScheduleBuilder(new Schedule()).changeActivityAt(0, Activity.WORK).build());
            helper.assertValueEqual(VillagerBreedingPolicy.evaluateParent(level, first).reason(), BreedingBlockReason.OFF_HOURS,
                    "ordinary work hours remain reserved");
            HiredVillagerContractService.endHireContract(level, first, player);
        });
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "natural_families")
    public static void controllersAndOppositeGenderRulesBlockAutomaticCourtship(GameTestHelper helper) {
        natural(helper, level -> {
            var first = adult(level);
            var second = adult(level);
            var graph = VillagerSocialGraphSavedData.get(level);
            var owner = UUID.randomUUID();
            com.jvn.villagerretaliation.interaction.VillagerCommandController.beginFollow(level, first, owner);
            helper.assertValueEqual(VillagerBreedingPolicy.evaluateParent(level, first).reason(), BreedingBlockReason.FOLLOWING_PLAYER,
                    "a follow command blocks natural participation");
            com.jvn.villagerretaliation.interaction.VillagerCommandController.beginStay(level, first, owner, BlockPos.ZERO);
            helper.assertValueEqual(VillagerBreedingPolicy.evaluateParent(level, first).reason(), BreedingBlockReason.ORDERED_TO_STAY,
                    "a stay command blocks natural participation");
            com.jvn.villagerretaliation.interaction.VillagerCommandController.clear(first);
            var downed = new CompoundTag();
            downed.putBoolean("Downed", true);
            first.getPersistentData().put("VillagerRetaliationDownedState", downed);
            helper.assertFalse(graph.recordCourtshipVisit(level, first, second), "downed villagers cannot court");
            first.getPersistentData().remove("VillagerRetaliationDownedState");
            HiredWorkStateStore.state(first).putBoolean("Enabled", true);
            HiredWorkStateStore.state(first).putString("WorkerTaskState", "working");
            helper.assertValueEqual(VillagerBreedingPolicy.evaluateParent(level, first).reason(), BreedingBlockReason.WORKING,
                    "an active worker task blocks participation");
            HiredWorkStateStore.clearInheritedStateForNewborn(first);
            first.setTarget(EntityType.ZOMBIE.create(level));
            helper.assertFalse(graph.recordCourtshipVisit(level, first, second), "combat blocks visits");
            first.setTarget(null);
            first.getBrain().setMemory(MemoryModuleType.NEAREST_HOSTILE, EntityType.ZOMBIE.create(level));
            helper.assertFalse(graph.recordCourtshipVisit(level, first, second), "a known threat blocks visits");
            first.getBrain().eraseMemory(MemoryModuleType.NEAREST_HOSTILE);
            var profile = com.jvn.villagerretaliation.profile.VillagerProfileManager.getOrCreateProfile(level, first);
            profile.setStudyState(com.jvn.villagerretaliation.study.VillagerStudyState.NONE
                    .start(com.jvn.villagerretaliation.skill.VillagerSkill.MINING), level.getGameTime());
            helper.assertValueEqual(VillagerBreedingPolicy.evaluateParent(level, first).reason(), BreedingBlockReason.CONTROLLED_ACTIVITY,
                    "study blocks participation");
            profile.setStudyState(com.jvn.villagerretaliation.study.VillagerStudyState.NONE, level.getGameTime());
            VillagerRetaliationConfig.ENABLE_OPPOSITE_GENDER_BREEDING_RULES.set(true);
            helper.assertFalse(graph.recordCourtshipVisit(level, first, second), "automatic courtship honors optional gender pairing");
            VillagerPresetNameRegistry.setStoredGender(second, VillagerGender.FEMALE);
            helper.assertTrue(graph.recordCourtshipVisit(level, first, second), "available compatible adults can court again");
            VillagerRetaliationConfig.ENABLE_NATURAL_FAMILIES.set(false);
            level.setDayTime(level.getDayTime() + 24_000L);
            helper.assertFalse(graph.recordCourtshipVisit(level, first, second), "disabling natural families stops automatic courtship");
        });
        helper.succeed();
    }

    private static Villager adult(ServerLevel level) {
        var villager = EntityType.VILLAGER.create(level);
        villager.setAge(0);
        villager.getBrain().setSchedule(new ScheduleBuilder(new Schedule()).changeActivityAt(0, Activity.IDLE).build());
        VillagerPresetNameRegistry.setStoredGender(villager, VillagerGender.MALE);
        return villager;
    }

    private static void natural(GameTestHelper helper, Consumer<ServerLevel> action) {
        boolean enabled = VillagerRetaliationConfig.ENABLE_NATURAL_FAMILIES.get();
        boolean graph = VillagerRetaliationConfig.ENABLE_VILLAGER_SOCIAL_GRAPH.get();
        boolean legacy = VillagerRetaliationConfig.ENABLE_FAMILY_BREEDING_RULES.get();
        boolean gender = VillagerRetaliationConfig.ENABLE_OPPOSITE_GENDER_BREEDING_RULES.get();
        long day = helper.getLevel().getDayTime();
        try {
            VillagerRetaliationConfig.ENABLE_NATURAL_FAMILIES.set(true);
            VillagerRetaliationConfig.ENABLE_VILLAGER_SOCIAL_GRAPH.set(true);
            VillagerRetaliationConfig.ENABLE_FAMILY_BREEDING_RULES.set(false);
            VillagerRetaliationConfig.ENABLE_OPPOSITE_GENDER_BREEDING_RULES.set(false);
            helper.getLevel().setDayTime(10_000);
            action.accept(helper.getLevel());
        } finally {
            VillagerRetaliationConfig.ENABLE_NATURAL_FAMILIES.set(enabled);
            VillagerRetaliationConfig.ENABLE_VILLAGER_SOCIAL_GRAPH.set(graph);
            VillagerRetaliationConfig.ENABLE_FAMILY_BREEDING_RULES.set(legacy);
            VillagerRetaliationConfig.ENABLE_OPPOSITE_GENDER_BREEDING_RULES.set(gender);
            helper.getLevel().setDayTime(day);
            VillagerBirthService.clearRuntimeState();
        }
    }
}
