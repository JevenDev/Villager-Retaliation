package com.jvn.villagerretaliation.villager;

import com.jvn.villagerretaliation.combat.downed.VillagerDeathProtectionResolver;
import com.jvn.villagerretaliation.combat.downed.VillagerDownedService;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.interaction.HiredVillagerContractService;
import com.jvn.villagerretaliation.party.PartyVillagerContractService;
import com.jvn.villagerretaliation.reputation.VillagerReputationManager;
import com.mojang.authlib.GameProfile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.StructureUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class VillagerBehaviorSuppressionGameTests {
    private static final String EMPTY_TEMPLATE = "empty";

    static {
        configureGameTestStructures();
    }

    private VillagerBehaviorSuppressionGameTests() {
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void sleepingVillagerHealsToConfiguredThresholdWithoutReducingHealth(GameTestHelper helper) {
        boolean previousEnabled = VillagerRetaliationConfig.ENABLE_VILLAGER_SLEEP_HEALING.get();
        double previousPercent = VillagerRetaliationConfig.VILLAGER_SLEEP_HEALING_MAX_HEALTH_PERCENT.get();
        Villager villager = spawnVillager(helper, new BlockPos(1, 2, 1));
        villager.startSleeping(villager.blockPosition());

        try {
            VillagerRetaliationConfig.ENABLE_VILLAGER_SLEEP_HEALING.set(true);
            VillagerRetaliationConfig.VILLAGER_SLEEP_HEALING_MAX_HEALTH_PERCENT.set(0.50D);

            villager.setHealth(4.0F);
            VillagerSleepHealingService.onVillagerTick(villager);
            helper.assertValueEqual(villager.getHealth(), villager.getMaxHealth() * 0.50F,
                    "sleep healing reaches the configured threshold");

            villager.setHealth(16.0F);
            VillagerSleepHealingService.onVillagerTick(villager);
            helper.assertValueEqual(villager.getHealth(), 16.0F,
                    "sleep healing does not lower health above the threshold");

            VillagerRetaliationConfig.ENABLE_VILLAGER_SLEEP_HEALING.set(false);
            villager.setHealth(4.0F);
            VillagerSleepHealingService.onVillagerTick(villager);
            helper.assertValueEqual(villager.getHealth(), 4.0F,
                    "disabled sleep healing leaves health unchanged");
        } finally {
            VillagerRetaliationConfig.ENABLE_VILLAGER_SLEEP_HEALING.set(previousEnabled);
            VillagerRetaliationConfig.VILLAGER_SLEEP_HEALING_MAX_HEALTH_PERCENT.set(previousPercent);
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void hiredDownedRecoveryAndReleaseUseOnePolicy(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "suppression_hired");
        Villager villager = spawnVillager(helper, new BlockPos(1, 2, 1));
        Villager partner = spawnVillager(helper, new BlockPos(3, 2, 1));
        seedIncompatibleMemories(level, villager, partner);

        HiredVillagerContractService.startHireContract(level, villager, hirer, 1, 0);
        assertState(helper, villager, VillagerBehaviorSuppressionPolicy.ControlState.HIRED, "hired");
        assertControlledMemoriesCleared(helper, villager, "hire transition");
        helper.assertTrue(suppresses(villager, VillagerBehaviorSuppressionPolicy.Behavior.BREEDING),
                "hired breeding must be suppressed");
        helper.assertTrue(suppresses(villager, VillagerBehaviorSuppressionPolicy.Behavior.VILLAGE_MIGRATION),
                "hired village migration must be suppressed");
        helper.assertTrue(suppresses(villager, VillagerBehaviorSuppressionPolicy.Behavior.TRADING),
                "hired trading must be suppressed");
        helper.assertValueEqual(villager.mobInteract(hirer, InteractionHand.MAIN_HAND), InteractionResult.FAIL,
                "hired vanilla right-click trading must be rejected");
        helper.assertFalse(suppresses(villager, VillagerBehaviorSuppressionPolicy.Behavior.SLEEPING),
                "hired rest remains an explicit compatibility exception");

        VillagerDownedService.enterDowned(
                level,
                villager,
                new VillagerDeathProtectionResolver.ProtectionResult(true, List.of("gametest")));
        assertState(helper, villager, VillagerBehaviorSuppressionPolicy.ControlState.DOWNED, "downed precedence");
        helper.assertTrue(villager.isNoAi(), "downed villager AI must be disabled");
        helper.assertTrue(suppresses(villager, VillagerBehaviorSuppressionPolicy.Behavior.COMBAT),
                "downed combat must be suppressed");
        helper.assertTrue(suppresses(villager, VillagerBehaviorSuppressionPolicy.Behavior.INTERACTION_MENUS),
                "downed interaction menus must be suppressed");

        VillagerDownedService.recover(villager);
        assertState(helper, villager, VillagerBehaviorSuppressionPolicy.ControlState.HIRED, "recovered hire");
        helper.assertFalse(villager.isNoAi(), "recovery must restore the pre-downed AI flag");
        helper.assertTrue(VillagerBehaviorSuppressionPolicy.shouldSuppressVanillaBrainTick(level, villager),
                "recovery must resume hired suppression, not ordinary village AI");

        villager.getPersistentData().getCompound("VillagerRetaliationHireContract")
                .putLong("EndGameTime", level.getGameTime());
        HiredVillagerContractService.expireHireContractIfNeeded(level, villager);
        assertState(helper, villager, VillagerBehaviorSuppressionPolicy.ControlState.NORMAL, "expired hire");
        helper.assertFalse(suppresses(villager, VillagerBehaviorSuppressionPolicy.Behavior.BREEDING),
                "contract expiry must restore ordinary behavior eligibility");

        villager.discard();
        partner.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void partyLoadAndDismissReapplyAndReleaseSuppression(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer leader = fakePlayer(level, "suppression_party");
        Villager villager = spawnVillager(helper, new BlockPos(1, 2, 1));
        Villager partner = spawnVillager(helper, new BlockPos(3, 2, 1));
        leader.moveTo(villager.getX(), villager.getY(), villager.getZ() + 1.0D, 0.0F, 0.0F);
        leader.getInventory().add(new ItemStack(Items.EMERALD, PartyVillagerContractService.DAILY_EMERALD_COST));
        VillagerReputationManager.setReputation(level, villager, leader.getUUID(), 0);

        PartyVillagerContractService.ContractResult recruited = PartyVillagerContractService.recruit(leader, villager);
        helper.assertTrue(recruited.success(), "party recruitment fixture: " + recruited.messageKey());
        seedIncompatibleMemories(level, villager, partner);
        PartyVillagerContractService.onVillagerLoaded(villager);

        assertState(helper, villager, VillagerBehaviorSuppressionPolicy.ControlState.PARTIED, "loaded party");
        assertControlledMemoriesCleared(helper, villager, "party load");
        helper.assertTrue(suppresses(villager, VillagerBehaviorSuppressionPolicy.Behavior.TRADING),
                "party trading must be suppressed");
        helper.assertValueEqual(villager.mobInteract(leader, InteractionHand.MAIN_HAND), InteractionResult.FAIL,
                "party vanilla right-click trading must be rejected");
        helper.assertTrue(suppresses(villager, VillagerBehaviorSuppressionPolicy.Behavior.SLEEPING),
                "party sleeping must be suppressed");
        helper.assertTrue(suppresses(villager, VillagerBehaviorSuppressionPolicy.Behavior.JOB_SITE_CLAIMING),
                "party job-site claiming must be suppressed");
        helper.assertFalse(suppresses(villager, VillagerBehaviorSuppressionPolicy.Behavior.COMBAT),
                "party combat remains owned by the party combat system");
        helper.assertTrue(VillagerBehaviorSuppressionPolicy.shouldSuppressVanillaBrainTick(level, villager),
                "party reload must suppress the complete vanilla brain");

        helper.assertTrue(PartyVillagerContractService.dismiss(leader, villager).success(),
                "party dismissal fixture");
        assertState(helper, villager, VillagerBehaviorSuppressionPolicy.ControlState.NORMAL, "dismissed party");
        helper.assertFalse(VillagerBehaviorSuppressionPolicy.shouldSuppressVanillaBrainTick(level, villager),
                "dismissal must restore vanilla scheduling");

        villager.discard();
        partner.discard();
        helper.succeed();
    }

    private static boolean suppresses(Villager villager, VillagerBehaviorSuppressionPolicy.Behavior behavior) {
        return VillagerBehaviorSuppressionPolicy.suppresses(villager, behavior);
    }

    private static void assertState(
            GameTestHelper helper,
            Villager villager,
            VillagerBehaviorSuppressionPolicy.ControlState expected,
            String label) {
        helper.assertValueEqual(VillagerBehaviorSuppressionPolicy.state(villager), expected, label);
    }

    private static void seedIncompatibleMemories(ServerLevel level, Villager villager, Villager partner) {
        villager.getBrain().setMemory(MemoryModuleType.BREED_TARGET, partner);
        villager.getBrain().setMemory(MemoryModuleType.INTERACTION_TARGET, partner);
        villager.getBrain().setMemory(
                MemoryModuleType.POTENTIAL_JOB_SITE,
                GlobalPos.of(level.dimension(), villager.blockPosition().offset(1, 0, 0)));
    }

    private static void assertControlledMemoriesCleared(GameTestHelper helper, Villager villager, String label) {
        helper.assertFalse(villager.getBrain().hasMemoryValue(MemoryModuleType.BREED_TARGET), label + " breed target");
        helper.assertFalse(villager.getBrain().hasMemoryValue(MemoryModuleType.INTERACTION_TARGET),
                label + " interaction target");
        helper.assertFalse(villager.getBrain().hasMemoryValue(MemoryModuleType.POTENTIAL_JOB_SITE),
                label + " potential job site");
    }

    private static Villager spawnVillager(GameTestHelper helper, BlockPos relativePos) {
        ServerLevel level = helper.getLevel();
        Villager villager = EntityType.VILLAGER.create(level);
        if (villager == null) throw new GameTestAssertException("Could not create villager");
        BlockPos pos = helper.absolutePos(relativePos);
        villager.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);
        if (!level.addFreshEntity(villager)) throw new GameTestAssertException("Could not add villager");
        level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
        return villager;
    }

    private static ServerPlayer fakePlayer(ServerLevel level, String name) {
        UUID id = UUID.nameUUIDFromBytes(("villagerretaliation:" + name).getBytes(StandardCharsets.UTF_8));
        return FakePlayerFactory.get(level, new GameProfile(id, name));
    }

    private static void configureGameTestStructures() {
        String configured = System.getProperty("villagerretaliation.gameteststructures");
        if (configured != null && !configured.isBlank()) {
            StructureUtils.testStructuresDir = configured;
            return;
        }
        List<Path> candidates = new ArrayList<>();
        candidates.add(Path.of("src/main/gameteststructures"));
        candidates.add(Path.of("../src/main/gameteststructures"));
        candidates.add(Path.of("neoforge/src/main/gameteststructures"));
        for (Path candidate : candidates) {
            if (Files.isDirectory(candidate)) {
                StructureUtils.testStructuresDir = candidate.toAbsolutePath().normalize().toString();
                return;
            }
        }
    }
}
