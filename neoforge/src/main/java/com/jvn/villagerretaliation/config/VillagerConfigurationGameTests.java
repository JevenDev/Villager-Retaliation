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
    public static void villagerHungerOptionsHaveExpectedDefaultsAndBindings(GameTestHelper helper) {
        VillagerRetaliationConfigModel.Balance balanceDefaults =
                new VillagerRetaliationConfigModel.Balance();
        VillagerRetaliationConfigModel.DebugOverlay debugDefaults =
                new VillagerRetaliationConfigModel.DebugOverlay();
        helper.assertTrue(balanceDefaults.hungerEffectAffectsVillagers,
                "Hunger status effects should affect villagers by default");
        helper.assertFalse(debugDefaults.reputationDebugOverlayShowHunger,
                "Debug hunger should remain opt-in like debug health and armor");
        helper.assertTrue(
                VillagerRetaliationConfig.HUNGER_EFFECT_AFFECTS_VILLAGERS.option()
                        != VillagerRetaliationConfig.REPUTATION_DEBUG_OVERLAY_SHOW_HUNGER.option(),
                "Gameplay and debug hunger settings must use distinct config bindings");
        helper.succeed();
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

    @GameTest(template = EMPTY_TEMPLATE)
    public static void downedCustomizationHasCompatibleDefaultsAndIndependentBindings(GameTestHelper helper) {
        VillagerRetaliationConfigModel.Combat defaults = new VillagerRetaliationConfigModel.Combat();
        helper.assertFalse(defaults.allVillagersUseDownedState,
                "Universal downed protection must remain opt-in");
        helper.assertFalse(defaults.raidVillagersUseDownedState,
                "Raid downed protection must remain opt-in");
        helper.assertFalse(defaults.hiredVillagersUseDownedState,
                "Hired-worker downed protection must remain opt-in");
        helper.assertTrue(defaults.partyVillagersUseDownedState,
                "Party protection must preserve the existing default");
        helper.assertTrue(defaults.playerDamageDownsEligibleVillagers
                        && defaults.mobDamageDownsEligibleVillagers
                        && defaults.environmentalDamageDownsEligibleVillagers,
                "All ordinary damage categories must preserve existing protection by default");
        helper.assertTrue(
                VillagerRetaliationConfig.ALL_VILLAGERS_USE_DOWNED_STATE.option()
                        != VillagerRetaliationConfig.RAID_VILLAGERS_USE_DOWNED_STATE.option(),
                "Universal and raid contexts need independent config bindings");
        helper.assertTrue(
                VillagerRetaliationConfig.HIRED_VILLAGERS_USE_DOWNED_STATE.option()
                        != VillagerRetaliationConfig.PARTY_VILLAGERS_USE_DOWNED_STATE.option(),
                "Hired and party contexts need independent config bindings");
        helper.assertTrue(
                VillagerRetaliationConfig.PLAYER_DAMAGE_DOWNS_ELIGIBLE_VILLAGERS.option()
                        != VillagerRetaliationConfig.ENVIRONMENTAL_DAMAGE_DOWNS_ELIGIBLE_VILLAGERS.option(),
                "Player and environmental damage need independent config bindings");
        helper.succeed();
    }
}
