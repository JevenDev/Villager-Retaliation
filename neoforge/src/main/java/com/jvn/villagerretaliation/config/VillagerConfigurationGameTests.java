package com.jvn.villagerretaliation.config;

import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.HiredVillagerRoleSettings;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class VillagerConfigurationGameTests {
    private static final String EMPTY_TEMPLATE = "empty";

    private VillagerConfigurationGameTests() {
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void craftsmanSkillGrowthHasIndependentBindingAndCompatibleDefault(GameTestHelper helper) {
        VillagerRetaliationConfigModel.HiredWorkSkillGrowth defaults =
                new VillagerRetaliationConfigModel.HiredWorkSkillGrowth();
        helper.assertValueEqual(defaults.craftsman, defaults.logging, "Craftsman default must preserve prior effective value");
        helper.assertTrue(
                VillagerRetaliationConfig.HIRED_WORK_SKILL_GROWTH_CRAFTSMAN.option()
                        != VillagerRetaliationConfig.HIRED_WORK_SKILL_GROWTH_LOGGING.option(),
                "Craftsman and Logging must use distinct config bindings");
        helper.assertValueEqual(
                HiredVillagerRoleSettings.skillGrowthAmount(HiredVillagerRole.CRAFTSMAN),
                Math.max(0.0D, VillagerRetaliationConfig.HIRED_WORK_SKILL_GROWTH_CRAFTSMAN.get()),
                "Craftsman role must read its dedicated setting");
        helper.assertValueEqual(
                HiredVillagerRoleSettings.skillGrowthAmount(HiredVillagerRole.LOGGING),
                Math.max(0.0D, VillagerRetaliationConfig.HIRED_WORK_SKILL_GROWTH_LOGGING.get()),
                "Logging role must retain its existing setting");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void craftsmanCompatibilityDetectsExistingOwoProperty(GameTestHelper helper) {
        helper.assertTrue(
                VillagerRetaliationConfigCompatibility.configTextHasProperty(
                        "{\n  \"logging\": 0.65,\n  \"craftsman\": 0.4\n}",
                        "craftsman"),
                "quoted owo property was not detected");
        helper.assertTrue(
                VillagerRetaliationConfigCompatibility.configTextHasProperty(
                        "{\n  craftsman: 0.4\n}",
                        "craftsman"),
                "unquoted JSON5 property was not detected");
        helper.assertFalse(
                VillagerRetaliationConfigCompatibility.configTextHasProperty(
                        "{\n  \"logging\": 0.65\n}",
                        "craftsman"),
                "missing Craftsman property was reported as present");
        helper.succeed();
    }
}
