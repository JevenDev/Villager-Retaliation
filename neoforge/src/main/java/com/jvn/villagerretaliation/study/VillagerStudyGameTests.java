package com.jvn.villagerretaliation.study;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.profile.VillagerProfile;
import com.jvn.villagerretaliation.profile.VillagerProfileManager;
import com.jvn.villagerretaliation.profile.VillagerSocialAttributes;
import com.jvn.villagerretaliation.skill.VillagerSkill;
import com.jvn.villagerretaliation.skill.VillagerSkillGenerator;
import com.jvn.villagerretaliation.skill.VillagerSkillSet;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class VillagerStudyGameTests {
    private static final String EMPTY_TEMPLATE = "empty";

    private VillagerStudyGameTests() {
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void eligibilityRejectsIncompatibleVillagers(GameTestHelper helper) {
        VillagerStudyState none = VillagerStudyState.NONE;
        helper.assertValueEqual(
                eligibility(true, false, false, false, false, none, VillagerSkill.MINING, 50),
                VillagerStudyService.Eligibility.UNAVAILABLE,
                "invalid villagers must be rejected");
        helper.assertValueEqual(
                eligibility(true, true, true, false, false, none, VillagerSkill.MINING, 50),
                VillagerStudyService.Eligibility.BABY,
                "babies must be rejected");
        helper.assertValueEqual(
                eligibility(true, true, false, true, false, none, VillagerSkill.MINING, 50),
                VillagerStudyService.Eligibility.HIRED,
                "hired villagers must be rejected");
        helper.assertValueEqual(
                eligibility(true, true, false, false, true, none, VillagerSkill.MINING, 50),
                VillagerStudyService.Eligibility.RECRUITED,
                "party villagers must be rejected");
        helper.assertValueEqual(
                eligibility(true, true, false, false, false, none, VillagerSkill.MINING, 50),
                VillagerStudyService.Eligibility.ELIGIBLE,
                "ordinary adults must be eligible");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void cooldownAndActiveSessionAreGlobal(GameTestHelper helper) {
        VillagerStudyState active = VillagerStudyState.NONE.start(VillagerSkill.CARTOGRAPHY);
        helper.assertValueEqual(
                eligibility(true, true, false, false, false, active, VillagerSkill.FARMING, 50),
                VillagerStudyService.Eligibility.ALREADY_STUDYING,
                "switching skills must not bypass an active session");

        VillagerStudyState cooldown = active.complete(10_000L);
        helper.assertValueEqual(
                eligibilityAt(true, true, false, false, false, cooldown, VillagerSkill.FARMING, 50, 500L),
                VillagerStudyService.Eligibility.COOLDOWN,
                "switching skills must not bypass the global cooldown");
        helper.assertValueEqual(cooldown.cooldownRemaining(500L), 9_500L,
                "cooldown should use persisted game time");
        helper.assertValueEqual(
                eligibilityAt(true, true, false, false, false, cooldown, VillagerSkill.FARMING, 50, 10_000L),
                VillagerStudyService.Eligibility.ELIGIBLE,
                "villager should become eligible when cooldown expires");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void activeTicksPauseAndResumeWithoutReset(GameTestHelper helper) {
        VillagerStudyState state = VillagerStudyState.NONE.start(VillagerSkill.SCHOLARSHIP);
        for (int i = 0; i < 151; i++) {
            state = state.advance();
        }
        VillagerStudyState paused = state.withPaused(true);
        for (int i = 0; i < 200; i++) {
            paused = paused.advance();
        }
        helper.assertValueEqual(paused.activeTicks(), 151,
                "paused sessions must not accumulate active time");
        VillagerStudyState resumed = paused.withPaused(false).advance();
        helper.assertValueEqual(resumed.activeTicks(), 152,
                "waking or interruption recovery must resume at saved progress");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void studyStateSurvivesProfileSaveAndLoad(GameTestHelper helper) {
        VillagerProfile profile = profileWithSkills();
        VillagerStudyState studying =
                new VillagerStudyState(VillagerSkill.CARTOGRAPHY, 3_020, true, 0L);
        profile.setStudyState(studying, 20L);
        VillagerProfile loaded = VillagerProfile.load(profile.save());
        helper.assertTrue(loaded != null, "profile should load");
        helper.assertValueEqual(loaded.studyState(), studying,
                "selected skill, progress, and pause state must persist");

        VillagerStudyState cooldown = studying.complete(12_345L);
        profile.setStudyState(cooldown, 30L);
        loaded = VillagerProfile.load(profile.save());
        helper.assertTrue(loaded != null, "cooldown profile should load");
        helper.assertValueEqual(loaded.studyState(), cooldown,
                "cooldown expiry must persist");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void rewardRangeAndSkillCapAreDefensive(GameTestHelper helper) {
        int oldMin = VillagerRetaliationConfig.STUDY_MINIMUM_REWARD.get();
        int oldMax = VillagerRetaliationConfig.STUDY_MAXIMUM_REWARD.get();
        try {
            VillagerRetaliationConfig.STUDY_MINIMUM_REWARD.set(3);
            VillagerRetaliationConfig.STUDY_MAXIMUM_REWARD.set(1);
            VillagerStudyService.RewardRange normalized = VillagerStudyService.configuredRewardRange();
            helper.assertValueEqual(normalized.minimum(), 1, "inverted minimum should normalize");
            helper.assertValueEqual(normalized.maximum(), 3, "inverted maximum should normalize");
            helper.assertValueEqual(VillagerStudyService.appliedReward(99, 3), 1,
                    "reward must report only points that fit below the cap");
            helper.assertValueEqual(VillagerStudyService.appliedReward(100, 3), 0,
                    "maxed skills must not gain points");
        } finally {
            VillagerRetaliationConfig.STUDY_MINIMUM_REWARD.set(oldMin);
            VillagerRetaliationConfig.STUDY_MAXIMUM_REWARD.set(oldMax);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void completedSessionChangesOnlySelectedSkill(GameTestHelper helper) {
        int oldDuration = VillagerRetaliationConfig.STUDY_DURATION_TICKS.get();
        int oldCooldown = VillagerRetaliationConfig.STUDY_COOLDOWN_TICKS.get();
        int oldMin = VillagerRetaliationConfig.STUDY_MINIMUM_REWARD.get();
        int oldMax = VillagerRetaliationConfig.STUDY_MAXIMUM_REWARD.get();
        try {
            VillagerRetaliationConfig.STUDY_DURATION_TICKS.set(1);
            VillagerRetaliationConfig.STUDY_COOLDOWN_TICKS.set(9_600);
            VillagerRetaliationConfig.STUDY_MINIMUM_REWARD.set(1);
            VillagerRetaliationConfig.STUDY_MAXIMUM_REWARD.set(1);

            Villager villager = spawnVillager(helper);
            ServerLevel level = helper.getLevel();
            VillagerProfileManager.setSkill(level, villager, VillagerSkill.CARTOGRAPHY, 31);
            VillagerProfileManager.setSkill(level, villager, VillagerSkill.FARMING, 47);

            VillagerStudyService.StartResult start =
                    VillagerStudyService.start(level, villager, VillagerSkill.CARTOGRAPHY);
            helper.assertTrue(start.started(), "eligible adult should start studying");
            VillagerStudyService.TickResult result = VillagerStudyService.tick(villager);
            helper.assertTrue(result.completed(), "one-tick configured session should complete");
            helper.assertValueEqual(result.appliedPoints(), 1, "configured reward should be applied");
            helper.assertValueEqual(
                    VillagerProfileManager.getSkill(level, villager, VillagerSkill.CARTOGRAPHY),
                    32,
                    "selected skill should increase");
            helper.assertValueEqual(
                    VillagerProfileManager.getSkill(level, villager, VillagerSkill.FARMING),
                    47,
                    "unrelated skill must not change");
            helper.assertTrue(VillagerStudyService.state(level, villager)
                            .cooldownRemaining(level.getServer().overworld().getGameTime()) > 0L,
                    "cooldown must begin only after completion");
        } finally {
            VillagerRetaliationConfig.STUDY_DURATION_TICKS.set(oldDuration);
            VillagerRetaliationConfig.STUDY_COOLDOWN_TICKS.set(oldCooldown);
            VillagerRetaliationConfig.STUDY_MINIMUM_REWARD.set(oldMin);
            VillagerRetaliationConfig.STUDY_MAXIMUM_REWARD.set(oldMax);
        }
        helper.succeed();
    }

    private static VillagerStudyService.Eligibility eligibility(
            boolean enabled, boolean available, boolean baby, boolean hired, boolean recruited,
            VillagerStudyState state, VillagerSkill skill, int skillValue
    ) {
        return eligibilityAt(enabled, available, baby, hired, recruited, state, skill, skillValue, 0L);
    }

    private static VillagerStudyService.Eligibility eligibilityAt(
            boolean enabled, boolean available, boolean baby, boolean hired, boolean recruited,
            VillagerStudyState state, VillagerSkill skill, int skillValue, long gameTime
    ) {
        return VillagerStudyService.evaluateEligibility(
                enabled, available, baby, hired, recruited, state, skill, skillValue, gameTime);
    }

    private static VillagerProfile profileWithSkills() {
        return VillagerProfile.create(
                UUID.randomUUID(),
                VillagerProfile.CURRENT_GENERATION_VERSION,
                42L,
                VillagerSocialAttributes.DEFAULT,
                VillagerSkillGenerator.CURRENT_GENERATION_VERSION,
                VillagerSkillSet.filled(50),
                "minecraft:none",
                0L);
    }

    private static Villager spawnVillager(GameTestHelper helper) {
        Villager villager = EntityType.VILLAGER.create(helper.getLevel());
        if (villager == null) {
            throw new IllegalStateException("Failed to create villager");
        }
        BlockPos position = helper.absolutePos(new BlockPos(1, 1, 1));
        villager.moveTo(position.getX() + 0.5D, position.getY(), position.getZ() + 0.5D, 0.0F, 0.0F);
        villager.setNoAi(true);
        helper.getLevel().addFreshEntity(villager);
        return villager;
    }
}
