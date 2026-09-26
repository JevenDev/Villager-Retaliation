package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.profile.VillagerProfile;
import com.jvn.villagerretaliation.profile.VillagerProfileManager;
import com.jvn.villagerretaliation.profile.VillagerProfileSavedData;
import com.jvn.villagerretaliation.skill.HiredWorkPractice;
import com.jvn.villagerretaliation.skill.HiredWorkSkillGrowthService;
import com.jvn.villagerretaliation.skill.VillagerSkill;
import java.util.List;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class SettlementPracticeGameTests {
    private SettlementPracticeGameTests() {
    }

    @GameTest(template = "empty")
    public static void backgroundAndNativeWorkShareProgressionAndRepetitionLimits(GameTestHelper helper) {
        var level = helper.getLevel();
        for (var role : List.of(HiredVillagerRole.FARMING, HiredVillagerRole.COURIER, HiredVillagerRole.COOK,
                HiredVillagerRole.CRAFTSMAN, HiredVillagerRole.SMELTER, HiredVillagerRole.BREWING)) {
            Villager nativeWorker = EntityType.VILLAGER.create(level);
            var active = VillagerProfileManager.getOrCreateProfile(level, nativeWorker);
            reset(active, level.getGameTime());
            var background = active.copyFor(UUID.randomUUID());
            VillagerProfileSavedData.get(level).put(background);
            var practice = HiredWorkPractice.batch(VillagerSkill.CRAFTING, "test:work", 4, 21L);
            var state = new CompoundTag();
            double first = 0.0D;
            double last = 0.0D;
            for (int index = 0; index < 40; index++) {
                var nativeResult = HiredWorkSkillGrowthService.onPractice(level, nativeWorker, null, role, state, practice);
                var backgroundResult = HiredWorkSkillGrowthService.onBackgroundPractice(level, background.villagerUuid(), role, practice);
                helper.assertValueEqual(backgroundResult.grantedXp(), nativeResult.grantedXp(), "both paths grant the same practice for " + role);
                if (index == 0) first = backgroundResult.grantedXp();
                last = backgroundResult.grantedXp();
            }
            helper.assertTrue(first > 0.0D && last < first, "repeated background work respects progression reductions for " + role);
            helper.assertValueEqual(background.skills().asMap(), active.skills().asMap(), "both paths produce the same final skills");
            helper.assertValueEqual(background.skillPracticeXp(), active.skillPracticeXp(), "both paths preserve the same remaining practice XP");
            var restored = VillagerProfile.load(background.save());
            helper.assertValueEqual(restored.skillPracticeXp(), background.skillPracticeXp(), "background practice survives saving");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void disabledBackgroundPracticeAndUnknownWorkersCannotGainXp(GameTestHelper helper) {
        var level = helper.getLevel();
        Villager villager = EntityType.VILLAGER.create(level);
        var profile = VillagerProfileManager.getOrCreateProfile(level, villager);
        reset(profile, level.getGameTime());
        boolean enabled = VillagerRetaliationConfig.ENABLE_HIRED_WORK_SKILL_GROWTH.get();
        double rate = VillagerRetaliationConfig.HIRED_WORK_SKILL_GROWTH_FARMING.get();
        try {
            VillagerRetaliationConfig.ENABLE_HIRED_WORK_SKILL_GROWTH.set(false);
            var disabled = HiredWorkSkillGrowthService.onBackgroundPractice(level, villager.getUUID(),
                    HiredVillagerRole.FARMING, HiredWorkPractice.farming("harvest_replant"));
            helper.assertFalse(disabled.profileChanged(), "disabled background progression does not change the profile");
            VillagerRetaliationConfig.ENABLE_HIRED_WORK_SKILL_GROWTH.set(true);
            VillagerRetaliationConfig.HIRED_WORK_SKILL_GROWTH_FARMING.set(0.0D);
            var zero = HiredWorkSkillGrowthService.onBackgroundPractice(level, villager.getUUID(),
                    HiredVillagerRole.FARMING, HiredWorkPractice.farming("harvest_replant"));
            helper.assertFalse(zero.profileChanged(), "a zero role rate disables background progression");
            UUID missing = UUID.randomUUID();
            var absent = HiredWorkSkillGrowthService.onBackgroundPractice(level, missing,
                    HiredVillagerRole.FARMING, HiredWorkPractice.farming("harvest_replant"));
            helper.assertFalse(absent.profileChanged(), "an unknown worker cannot receive practice");
            helper.assertTrue(VillagerProfileSavedData.get(level).get(missing) == null, "background work does not invent a missing profile");
            helper.assertValueEqual(xp(profile), 0.0D, "disabled work grants no practice XP");
        } finally {
            VillagerRetaliationConfig.ENABLE_HIRED_WORK_SKILL_GROWTH.set(enabled);
            VillagerRetaliationConfig.HIRED_WORK_SKILL_GROWTH_FARMING.set(rate);
        }
        helper.succeed();
    }

    static VillagerProfile prepare(GameTestHelper helper, SettlementWorkerState worker) {
        var profile = VillagerProfileSavedData.get(helper.getLevel()).get(worker.id());
        reset(profile, helper.getLevel().getGameTime());
        return profile;
    }

    static double xp(VillagerProfile profile) {
        return profile.skillPracticeXp().values().stream().mapToDouble(Double::doubleValue).sum();
    }

    private static void reset(VillagerProfile profile, long now) {
        for (VillagerSkill skill : VillagerSkill.values()) {
            profile.setSkill(skill, 40, now);
            profile.setSkillPracticeXp(skill, 0.0D, now);
        }
    }
}
