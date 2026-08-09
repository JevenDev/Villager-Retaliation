package com.jvn.villagerretaliation.villager;

import com.jvn.villagerretaliation.combat.downed.VillagerDeathProtectionResolver;
import com.jvn.villagerretaliation.combat.downed.VillagerDownedService;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.interaction.HiredVillagerContractService;
import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
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
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
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
    public static void naturalJobArmorRequiresArmorForTheConfiguredSlot(GameTestHelper helper) {
        helper.assertTrue(
                VillagerNaturalJobArmorResources.isArmorForSlot(Items.IRON_BOOTS, EquipmentSlot.FEET),
                "boots should be accepted for the feet slot");
        helper.assertFalse(
                VillagerNaturalJobArmorResources.isArmorForSlot(Items.IRON_HELMET, EquipmentSlot.FEET),
                "armor for a different slot must be rejected");
        helper.assertFalse(
                VillagerNaturalJobArmorResources.isArmorForSlot(Items.STONE, EquipmentSlot.CHEST),
                "non-armor items must be rejected");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void villagerTrafficUsesStableRightOfWayAndSafePassing(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Villager trailing = spawnVillager(helper, new BlockPos(1, 2, 1));
        Villager leading = spawnVillager(helper, new BlockPos(2, 2, 1));
        Vec3 east = new Vec3(1.0D, 0.0D, 0.0D);
        Vec3 west = new Vec3(-1.0D, 0.0D, 0.0D);

        helper.assertTrue(
                VillagerTrafficService.shouldYieldTo(trailing, leading, east, east),
                "the trailing villager should yield on a shared route");
        helper.assertFalse(
                VillagerTrafficService.shouldYieldTo(leading, trailing, east, east),
                "the leading villager should retain right-of-way on a shared route");

        boolean trailingYieldsHeadOn = VillagerTrafficService.shouldYieldTo(trailing, leading, east, west);
        boolean leadingYieldsHeadOn = VillagerTrafficService.shouldYieldTo(leading, trailing, west, east);
        helper.assertTrue(
                trailingYieldsHeadOn != leadingYieldsHeadOn,
                "exactly one head-on villager should receive stable right-of-way");

        BlockPos openSouthFloor = helper.absolutePos(new BlockPos(1, 1, 2));
        BlockPos openNorthFloor = helper.absolutePos(new BlockPos(1, 1, 0));
        level.setBlockAndUpdate(openSouthFloor, Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(openNorthFloor, Blocks.STONE.defaultBlockState());
        helper.assertTrue(
                VillagerTrafficService.safeSidestep(level, trailing, east) != null,
                "open terrain should provide a safe passing maneuver");

        for (BlockPos relative : List.of(
                new BlockPos(1, 2, 2),
                new BlockPos(1, 3, 2),
                new BlockPos(1, 2, 0),
                new BlockPos(1, 3, 0))) {
            level.setBlockAndUpdate(helper.absolutePos(relative), Blocks.STONE.defaultBlockState());
        }
        helper.assertTrue(
                VillagerTrafficService.safeSidestep(level, trailing, east) == null,
                "a one-block-wide passage should make the villager queue instead of clipping sideways");

        trailing.discard();
        leading.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void villagerHealsOnlyAfterSuccessfulSleep(GameTestHelper helper) {
        boolean previousEnabled = VillagerRetaliationConfig.ENABLE_VILLAGER_SLEEP_HEALING.get();
        double previousPercent = VillagerRetaliationConfig.VILLAGER_SLEEP_HEALING_MAX_HEALTH_PERCENT.get();
        long previousDayTime = helper.getLevel().getDayTime();
        Villager villager = spawnVillager(helper, new BlockPos(1, 2, 1));

        try {
            VillagerRetaliationConfig.ENABLE_VILLAGER_SLEEP_HEALING.set(true);
            VillagerRetaliationConfig.VILLAGER_SLEEP_HEALING_MAX_HEALTH_PERCENT.set(0.50D);

            villager.setHealth(4.0F);
            villager.startSleeping(villager.blockPosition());
            VillagerSleepHealingService.onVillagerTick(villager);
            helper.assertValueEqual(villager.getHealth(), 4.0F,
                    "entering sleep does not immediately restore health");

            helper.getLevel().setDayTime(1000L);
            villager.stopSleeping();
            VillagerSleepHealingService.onVillagerTick(villager);
            helper.assertValueEqual(villager.getHealth(), villager.getMaxHealth() * 0.50F,
                    "a completed sleep restores health to the configured threshold");

            villager.setHealth(16.0F);
            villager.startSleeping(villager.blockPosition());
            VillagerSleepHealingService.onVillagerTick(villager);
            villager.stopSleeping();
            VillagerSleepHealingService.onVillagerTick(villager);
            helper.assertValueEqual(villager.getHealth(), 16.0F,
                    "completed sleep does not lower health above the threshold");

            helper.getLevel().setDayTime(13000L);
            villager.setHealth(4.0F);
            villager.startSleeping(villager.blockPosition());
            VillagerSleepHealingService.onVillagerTick(villager);
            villager.stopSleeping();
            VillagerSleepHealingService.onVillagerTick(villager);
            helper.assertValueEqual(villager.getHealth(), 4.0F,
                    "interrupted nighttime sleep does not restore health");
        } finally {
            VillagerRetaliationConfig.ENABLE_VILLAGER_SLEEP_HEALING.set(previousEnabled);
            VillagerRetaliationConfig.VILLAGER_SLEEP_HEALING_MAX_HEALTH_PERCENT.set(previousPercent);
            helper.getLevel().setDayTime(previousDayTime);
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
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.FARMER));
        seedIncompatibleMemories(level, villager, partner);

        helper.assertTrue(
                HiredVillagerContractService.startHireContract(
                        level, villager, hirer, 1, 0, HiredVillagerRole.FARMING),
                "suppression fixture should start a non-guard hire");
        assertState(helper, villager, VillagerBehaviorSuppressionPolicy.ControlState.HIRED, "hired");
        assertControlledMemoriesCleared(helper, villager, "hire transition");
        helper.assertTrue(suppresses(villager, VillagerBehaviorSuppressionPolicy.Behavior.BREEDING),
                "hired breeding must be suppressed");
        helper.assertTrue(suppresses(villager, VillagerBehaviorSuppressionPolicy.Behavior.VILLAGE_MIGRATION),
                "hired village migration must be suppressed");
        helper.assertFalse(suppresses(villager, VillagerBehaviorSuppressionPolicy.Behavior.TRADING),
                "hired trading remains available above role and movement intents");
        helper.assertFalse(suppresses(villager, VillagerBehaviorSuppressionPolicy.Behavior.VANILLA_PANIC),
                "hired villagers retain vanilla danger arbitration");
        helper.assertFalse(suppresses(villager, VillagerBehaviorSuppressionPolicy.Behavior.VANILLA_WORKING),
                "profession activities remain available when no concrete role task owns the tick");
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
        helper.assertFalse(VillagerBehaviorSuppressionPolicy.shouldSuppressVanillaBrainTick(level, villager),
                "an idle recovered hire must remain eligible for ordinary scheduled AI");

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
    public static void followIntentYieldsWithoutErasingBrainState(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer hirer = fakePlayer(level, "arbitration_hirer");
        Villager villager = spawnVillager(helper, new BlockPos(1, 2, 1));
        Villager attacker = spawnVillager(helper, new BlockPos(4, 2, 1));

        HiredVillagerContractService.startHireContract(level, villager, hirer, 1, 0);
        helper.assertTrue(
                com.jvn.villagerretaliation.interaction.VillagerRecruitmentService.startFollowing(
                        level, villager, hirer),
                "hired fixture should accept its owner's follow intent");
        villager.getBrain().setMemory(MemoryModuleType.NEAREST_HOSTILE, attacker);
        villager.getBrain().setMemory(MemoryModuleType.HURT_BY_ENTITY, attacker);
        villager.getBrain().setMemory(
                MemoryModuleType.WALK_TARGET,
                new net.minecraft.world.entity.ai.memory.WalkTarget(attacker.position(), 0.6F, 0));

        com.jvn.villagerretaliation.interaction.VillagerRecruitmentService.onVillagerTickPre(villager);
        com.jvn.villagerretaliation.interaction.VillagerRecruitmentService.onVillagerTickPost(villager);

        helper.assertTrue(villager.getBrain().hasMemoryValue(MemoryModuleType.NEAREST_HOSTILE),
                "follow intent must preserve hostile memory");
        helper.assertTrue(villager.getBrain().hasMemoryValue(MemoryModuleType.HURT_BY_ENTITY),
                "follow intent must preserve damage memory");
        helper.assertTrue(villager.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET),
                "yielding follow intent must preserve higher-priority movement memory");
        helper.assertValueEqual(
                com.jvn.villagerretaliation.interaction.VillagerAiArbitration.currentPriority(level, villager),
                com.jvn.villagerretaliation.interaction.VillagerAiArbitration.Priority.IMMEDIATE_DANGER,
                "damage must outrank follow movement");

        villager.discard();
        attacker.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void vanillaTradeFallbackCannotBypassInteractionOwnership(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = fakePlayer(level, "suppression_trade_fallback");
        Villager villager = spawnVillager(helper, new BlockPos(1, 2, 1));
        boolean previousInteractionScreen = VillagerRetaliationConfig.ENABLE_INTERACTION_SCREEN.get();
        boolean previousShiftBypass = VillagerRetaliationConfig.SHIFT_RIGHT_CLICK_BYPASSES_INTERACTION_SCREEN.get();
        double previousDialogueDistance = VillagerRetaliationConfig.MAX_DIALOGUE_DISTANCE.get();

        try {
            VillagerRetaliationConfig.ENABLE_INTERACTION_SCREEN.set(true);
            VillagerRetaliationConfig.SHIFT_RIGHT_CLICK_BYPASSES_INTERACTION_SCREEN.set(true);
            VillagerRetaliationConfig.MAX_DIALOGUE_DISTANCE.set(0.25D);
            player.moveTo(villager.getX(), villager.getY(), villager.getZ() + 1.0D, 0.0F, 0.0F);

            helper.assertTrue(VillagerInteractionService.shouldSuppressVanillaTradeFallback(
                            villager, player, InteractionHand.MAIN_HAND),
                    "dialogue distance must not turn a valid interaction packet into vanilla trading");

            player.setShiftKeyDown(true);
            helper.assertFalse(VillagerInteractionService.shouldSuppressVanillaTradeFallback(
                            villager, player, InteractionHand.MAIN_HAND),
                    "the configured adult shift-click trading bypass must remain available");
        } finally {
            VillagerRetaliationConfig.ENABLE_INTERACTION_SCREEN.set(previousInteractionScreen);
            VillagerRetaliationConfig.SHIFT_RIGHT_CLICK_BYPASSES_INTERACTION_SCREEN.set(previousShiftBypass);
            VillagerRetaliationConfig.MAX_DIALOGUE_DISTANCE.set(previousDialogueDistance);
            player.setShiftKeyDown(false);
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void controlledTradeOpeningBypassesOnlyTheRoutingGuard(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = fakePlayer(level, "suppression_controlled_trade");
        Villager villager = spawnVillager(helper, new BlockPos(1, 2, 1));
        boolean previousInteractionScreen = VillagerRetaliationConfig.ENABLE_INTERACTION_SCREEN.get();

        try {
            VillagerRetaliationConfig.ENABLE_INTERACTION_SCREEN.set(true);
            player.moveTo(villager.getX(), villager.getY(), villager.getZ() + 1.0D, 0.0F, 0.0F);
            villager.getOffers().add(new MerchantOffer(
                    new ItemCost(Items.EMERALD, 1),
                    new ItemStack(Items.BREAD),
                    12,
                    2,
                    0.05F));

            helper.assertValueEqual(
                    villager.mobInteract(player, InteractionHand.MAIN_HAND),
                    InteractionResult.FAIL,
                    "an unowned vanilla trade entry must remain suppressed");

            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.APPLE));
            InteractionResult controlledResult = VillagerInteractionService.openTrading(player, villager, false);
            helper.assertTrue(
                    controlledResult.consumesAction(),
                    "the interaction system's validated trade entry must reach vanilla trading");
            helper.assertTrue(
                    villager.getTradingPlayer() == player,
                    "the controlled trade entry must install the trading player");
        } finally {
            villager.setTradingPlayer(null);
            VillagerRetaliationConfig.ENABLE_INTERACTION_SCREEN.set(previousInteractionScreen);
            villager.discard();
        }
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
