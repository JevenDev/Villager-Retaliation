package com.jvn.villagerretaliation.skill;

import com.jvn.villagerretaliation.profile.VillagerProfile;
import com.jvn.villagerretaliation.profile.VillagerSocialAttributes;
import java.util.List;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class VillagerSkillProgressionGameTests {
    private static final String EMPTY_TEMPLATE = "empty";

    private VillagerSkillProgressionGameTests() {
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void progressionCurveCarriesXpAndClampsAtMaximum(GameTestHelper helper) {
        if (!(VillagerSkillProgressionService.requiredXp(80) > VillagerSkillProgressionService.requiredXp(20))) {
            helper.fail("High skills should require more XP");
            return;
        }

        VillagerProfile profile = profileWithSkill(1);
        VillagerSkillProgressionService.apply(profile, List.of(
                practice(100.0D, 1L)), 0L, 1L, 1.0D);
        if (profile.skills().get(VillagerSkill.MINING) <= 2) {
            helper.fail("Excess XP did not carry across thresholds");
            return;
        }

        VillagerProfile maximum = profileWithSkill(99);
        VillagerSkillProgressionService.apply(maximum, List.of(
                practice(1_000.0D, 2L)), 0L, 1L, 1.0D);
        if (maximum.skills().get(VillagerSkill.MINING) != 100 || maximum.skillPracticeXp(VillagerSkill.MINING) != 0.0D) {
            helper.fail("Maximum skill was not clamped and cleared");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void dailyAndRepetitionReductionsResetAndStayBounded(GameTestHelper helper) {
        VillagerProfile daily = profileWithSkill(10);
        double beforeCap = VillagerSkillProgressionService.apply(
                daily, List.of(practice(6.0D, 1L)), 4L, 1L, 1.0D).grantedXp();
        double afterCap = VillagerSkillProgressionService.apply(
                daily, List.of(practice(1.0D, 2L)), 4L, 2L, 1.0D).grantedXp();
        double nextDay = VillagerSkillProgressionService.apply(
                daily, List.of(practice(1.0D, 2L)), 5L, 3L, 1.0D).grantedXp();
        if (Math.abs(beforeCap - 6.0D) > 0.0001D || Math.abs(afterCap - 0.2D) > 0.0001D || Math.abs(nextDay - 1.0D) > 0.0001D) {
            helper.fail("Daily soft cap did not reduce and reset deterministically");
            return;
        }

        VillagerProfile repeated = profileWithSkill(10);
        for (int i = 0; i < 8; i++) {
            VillagerSkillProgressionService.apply(repeated, List.of(practice(0.1D, 77L)), 8L, i, 1.0D);
        }
        double reduced = VillagerSkillProgressionService.apply(
                repeated, List.of(practice(0.1D, 77L)), 8L, 9L, 1.0D).grantedXp();
        if (Math.abs(reduced - 0.035D) > 0.0001D) {
            helper.fail("Repeated equivalent work was not reduced");
            return;
        }
        for (int i = 0; i < 100; i++) {
            VillagerSkillProgressionService.apply(repeated, List.of(practice(0.001D, 1_000L + i)), 9L, 20L + i, 1.0D);
        }
        if (repeated.repetitionKeyCount(VillagerSkill.MINING, 9L) > VillagerSkillProgressionService.MAX_REPETITION_KEYS_PER_SKILL) {
            helper.fail("Repetition keys exceeded their bound");
            return;
        }

        VillagerProfile loaded = VillagerProfile.load(repeated.save());
        if (loaded == null
                || Math.abs(loaded.skillPracticeXp(VillagerSkill.MINING) - repeated.skillPracticeXp(VillagerSkill.MINING)) > 0.0001D
                || loaded.repetitionKeyCount(VillagerSkill.MINING, 9L) != repeated.repetitionKeyCount(VillagerSkill.MINING, 9L)) {
            helper.fail("Practice XP or daily state did not survive save/load");
            return;
        }
        helper.succeed();
    }

    private static VillagerProfile profileWithSkill(int value) {
        return VillagerProfile.create(
                UUID.randomUUID(), 1, 1L, VillagerSocialAttributes.DEFAULT, 1,
                VillagerSkillSet.filled(value), "minecraft:toolsmith", 0L);
    }

    private static VillagerSkillPractice practice(double units, long key) {
        return new VillagerSkillPractice(VillagerSkill.MINING, units, "test:mining", key);
    }
}
