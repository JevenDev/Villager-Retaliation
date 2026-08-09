package com.jvn.villagerretaliation.combat;

import com.jvn.villagerretaliation.allegiance.AllegianceAssignmentSource;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceApi;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.combat.downed.VillagerDeathProtectionResolver;
import com.jvn.villagerretaliation.combat.downed.VillagerDownedService;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.jvn.villagerretaliation.inventory.VillagerInventoryAccess;
import com.jvn.villagerretaliation.profile.VillagerProfileManager;
import com.jvn.villagerretaliation.skill.VillagerSkill;
import com.jvn.villagerretaliation.villager.VillagerMovementSpeedPolicy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.StructureUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class VillagerRangedCombatGameTests {
    private static final String EMPTY_TEMPLATE = "empty";

    static {
        configureGameTestStructures();
    }

    private VillagerRangedCombatGameTests() {
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void retaliationRunSpeedDoesNotChangeVillagerWalkSpeed(GameTestHelper helper) {
        buildFloor(helper, 0, 4, 0, 4, 1);
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 2));

        helper.assertTrue(
                Math.abs(villager.getAttributeValue(Attributes.MOVEMENT_SPEED)
                        - RetaliationCombatStats.WALK_SPEED) < 0.000001D,
                "villagers should retain their normal vanilla walking speed");

        VillagerRetaliationRetaliationUtil.boostCombatMovement(villager);

        helper.assertTrue(
                Math.abs(villager.getAttributeValue(Attributes.MOVEMENT_SPEED)
                        - RetaliationCombatStats.WALK_SPEED) < 0.000001D,
                "entering retaliation should not replace the villager movement attribute");
        helper.assertTrue(
                Math.abs(villager.getAttributeValue(Attributes.MOVEMENT_SPEED)
                        * VillagerCombatRoles.movementSpeed(villager)
                        - RetaliationCombatStats.RUN_SPEED) < 0.000001D,
                "retaliation should use the expected run speed");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void movementPolicyNormalizesWalkRunAndFollowSpeeds(GameTestHelper helper) {
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 2));
        villager.getBrain().setMemory(
                MemoryModuleType.WALK_TARGET,
                new WalkTarget(villager.position().add(4.0D, 0.0D, 0.0D), 0.9F, 0));

        VillagerMovementSpeedPolicy.enforce(helper.getLevel(), villager);
        helper.assertTrue(
                Math.abs(villager.getBrain().getMemory(MemoryModuleType.WALK_TARGET)
                        .orElseThrow()
                        .getSpeedModifier() - VillagerMovementSpeedPolicy.WALK_SPEED_MODIFIER) < 0.000001D,
                "ordinary movement uses the vanilla villager walk modifier");

        villager.getBrain().setMemory(MemoryModuleType.HEARD_BELL_TIME, helper.getLevel().getGameTime());
        VillagerMovementSpeedPolicy.enforce(helper.getLevel(), villager);
        helper.assertTrue(
                Math.abs(villager.getBrain().getMemory(MemoryModuleType.WALK_TARGET)
                        .orElseThrow()
                        .getSpeedModifier() - VillagerMovementSpeedPolicy.RUN_SPEED_MODIFIER) < 0.000001D,
                "flee movement uses the Vindicator-equivalent run modifier");
        helper.assertValueEqual(
                VillagerMovementSpeedPolicy.following(8.0D * 8.0D),
                VillagerMovementSpeedPolicy.WALK_SPEED_MODIFIER,
                "followers walk within eight blocks");
        helper.assertValueEqual(
                VillagerMovementSpeedPolicy.following(8.01D * 8.01D),
                VillagerMovementSpeedPolicy.RUN_SPEED_MODIFIER,
                "followers run beyond eight blocks");

        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void pointBlankRangedShotCoversBodyContactTargets(GameTestHelper helper) {
        buildFloor(helper, 0, 6, 0, 4, 1);
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 2));
        Zombie target = spawnZombie(helper, new BlockPos(3, 2, 2));

        helper.assertTrue(
                VillagerRangedCombatHelper.hasPointBlankRangedShot(villager, target),
                "body-contact target should count as a clear point-blank ranged shot");

        BlockPos far = helper.absolutePos(new BlockPos(5, 2, 2));
        target.moveTo(far.getX() + 0.5D, far.getY(), far.getZ() + 0.5D, 0.0F, 0.0F);

        helper.assertFalse(
                VillagerRangedCombatHelper.hasPointBlankRangedShot(villager, target),
                "non-contact target should still rely on normal ranged line of sight");

        target.discard();
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void hardModeArmorerRaisesShieldBetweenPointBlankAttacks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Difficulty previousDifficulty = level.getDifficulty();
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 2));
        Zombie target = spawnZombie(helper, new BlockPos(3, 2, 2));
        try {
            level.getServer().setDifficulty(Difficulty.HARD, true);
            villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.ARMORER));
            villager.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
            villager.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD));
            VillagerRetaliationHandler.forceAngerSilently(villager, target);

            VillagerRetaliationHandler.onEntityTickPost(new EntityTickEvent.Post(villager));
            VillagerRetaliationHandler.onEntityTickPost(new EntityTickEvent.Post(villager));

            helper.assertTrue(villager.isUsingItem()
                            && villager.getUsedItemHand() == InteractionHand.OFF_HAND
                            && villager.getUseItem().is(Items.SHIELD),
                    "hard-mode armorer should raise its shield during the cooldown after a point-blank attack");
            helper.assertValueEqual(
                    VillagerArmorerCombatTactics.movementSpeedFactor(villager),
                    0.45D,
                    "point-blank shield guard movement factor");
        } finally {
            VillagerArmorerCombatTactics.resetState(villager);
            level.getServer().setDifficulty(previousDifficulty, true);
            target.discard();
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void combatStateSelectionUsesDistanceAmmoAndShieldPressure(GameTestHelper helper) {
        helper.assertValueEqual(
                VillagerCombatStateMachine.selectMode(null, 64.0D, false, true, true, true),
                VillagerCombatStateMachine.CombatMode.RANGED,
                EMPTY_TEMPLATE);
        helper.assertValueEqual(
                VillagerCombatStateMachine.selectMode(
                        VillagerCombatStateMachine.CombatMode.RANGED, 25.0D, false, true, true, true),
                VillagerCombatStateMachine.CombatMode.RANGED,
                EMPTY_TEMPLATE);
        helper.assertValueEqual(
                VillagerCombatStateMachine.selectMode(null, 9.0D, false, true, true, true),
                VillagerCombatStateMachine.CombatMode.MELEE,
                EMPTY_TEMPLATE);
        helper.assertValueEqual(
                VillagerCombatStateMachine.selectMode(null, 64.0D, false, false, true, false),
                VillagerCombatStateMachine.CombatMode.MELEE,
                EMPTY_TEMPLATE);
        helper.assertValueEqual(
                VillagerCombatStateMachine.selectMode(null, 64.0D, true, true, true, true),
                VillagerCombatStateMachine.CombatMode.AXE_BREAKER,
                EMPTY_TEMPLATE);
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void combatStateSwapsBetweenCarriedWeapons(GameTestHelper helper) {
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 2));
        Zombie target = spawnZombie(helper, new BlockPos(3, 2, 2));
        HiredJobInventory inventory = HiredJobInventory.getJobInventory(villager);
        inventory.setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.IRON_SWORD));
        inventory.setItem(HiredJobInventory.HOTBAR_START, new ItemStack(Items.CROSSBOW));
        inventory.setItem(HiredJobInventory.HOTBAR_START + 1, new ItemStack(Items.ARROW));
        inventory.markPlayerPlacedSupply(HiredJobInventory.HOTBAR_START);
        VillagerCombatStateMachine.prepare(villager, target, 64.0D);
        helper.assertTrue(villager.getMainHandItem().is(Items.CROSSBOW), "ranged mode should equip a supplied crossbow");
        VillagerCombatStateMachine.prepare(villager, target, 9.0D);
        helper.assertTrue(villager.getMainHandItem().is(Items.IRON_SWORD), "melee mode should restore the job sword");
        VillagerCombatStateMachine.clearState(villager);
        target.discard();
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void anyVillagerWithShieldGuardsBetweenAttacks(GameTestHelper helper) {
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 2));
        Zombie target = spawnZombie(helper, new BlockPos(3, 2, 2));
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.FARMER));
        villager.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        villager.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD));
        VillagerRetaliationHandler.forceAngerSilently(villager, target);
        VillagerRetaliationHandler.onEntityTickPost(new EntityTickEvent.Post(villager));
        VillagerRetaliationHandler.onEntityTickPost(new EntityTickEvent.Post(villager));
        helper.assertTrue(villager.isUsingItem()
                        && villager.getUsedItemHand() == InteractionHand.OFF_HAND
                        && villager.getUseItem().is(Items.SHIELD),
                EMPTY_TEMPLATE);
        VillagerArmorerCombatTactics.resetState(villager);
        VillagerCombatStateMachine.clearState(villager);
        target.discard();
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void brokenShieldUsesFullPlayerRecoveryTimeAcrossCombatReset(GameTestHelper helper) {
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 2));
        villager.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD));
        villager.startUsingItem(InteractionHand.OFF_HAND);
        long disabledAt = villager.level().getGameTime();

        VillagerArmorerCombatTactics.disableShield(villager);
        helper.assertFalse(villager.isUsingItem(), "breaking a shield should lower it immediately");
        helper.assertTrue(
                VillagerArmorerCombatTactics.isShieldDisabled(villager, disabledAt + 99L),
                "villager shield should remain disabled for the player's full 100-tick cooldown");

        VillagerArmorerCombatTactics.resetState(villager);
        helper.assertTrue(
                VillagerArmorerCombatTactics.isShieldDisabled(villager, disabledAt + 99L),
                "ordinary combat cleanup must not erase an active shield cooldown");
        helper.assertFalse(
                VillagerArmorerCombatTactics.isShieldDisabled(villager, disabledAt + 100L),
                "villager shield should recover on the same tick as a player's shield");

        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void emptyCrossbowFallsBackToPersonalMeleeWeapon(GameTestHelper helper) {
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 2));
        Zombie target = spawnZombie(helper, new BlockPos(3, 2, 2));
        HiredJobInventory inventory = HiredJobInventory.getJobInventory(villager);
        inventory.setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.CROSSBOW));
        VillagerInventoryAccess.addItem(villager, new ItemStack(Items.IRON_SWORD));
        VillagerCombatStateMachine.prepare(villager, target, 64.0D);
        helper.assertTrue(villager.getMainHandItem().is(Items.IRON_SWORD), EMPTY_TEMPLATE);
        helper.assertTrue(inventory.getItem(HiredJobInventory.MAINHAND_SLOT).is(Items.CROSSBOW), EMPTY_TEMPLATE);
        VillagerInventoryAccess.addItem(villager, new ItemStack(Items.ARROW));
        VillagerCombatStateMachine.prepare(villager, target, 64.0D);
        helper.assertTrue(villager.getMainHandItem().is(Items.CROSSBOW), EMPTY_TEMPLATE);
        helper.assertTrue(VillagerInventoryAccess.hasCarriedItem(villager, stack -> stack.is(Items.IRON_SWORD)), EMPTY_TEMPLATE);
        VillagerCombatStateMachine.clearState(villager);
        target.discard();
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void shieldingTargetPullsAxeFromPersonalInventory(GameTestHelper helper) {
        Villager attacker = spawnVillager(helper, new BlockPos(2, 2, 2));
        Villager target = spawnVillager(helper, new BlockPos(3, 2, 2));
        HiredJobInventory inventory = HiredJobInventory.getJobInventory(attacker);
        inventory.setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.CROSSBOW));
        VillagerInventoryAccess.addItem(attacker, new ItemStack(Items.IRON_AXE));
        VillagerProfileManager.setSkill(
                helper.getLevel(),
                attacker,
                VillagerSkill.GUARDING,
                VillagerCombatSkillBehavior.AXE_BREAKER_GUARDING_REQUIRED);
        target.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD));
        target.startUsingItem(InteractionHand.OFF_HAND);
        helper.assertValueEqual(
                VillagerCombatStateMachine.prepare(attacker, target, 9.0D),
                VillagerCombatStateMachine.CombatMode.AXE_BREAKER,
                EMPTY_TEMPLATE);
        helper.assertTrue(attacker.getMainHandItem().is(Items.IRON_AXE), EMPTY_TEMPLATE);
        helper.assertTrue(inventory.getItem(HiredJobInventory.MAINHAND_SLOT).is(Items.CROSSBOW), EMPTY_TEMPLATE);
        VillagerCombatStateMachine.clearState(attacker);
        target.stopUsingItem();
        target.discard();
        attacker.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void axeBreakerCanStrikeShieldBeforeBodyContact(GameTestHelper helper) {
        Villager attacker = spawnVillager(helper, new BlockPos(2, 2, 2));
        Villager target = spawnVillager(helper, new BlockPos(4, 2, 2));
        VillagerInventoryAccess.addItem(attacker, new ItemStack(Items.IRON_AXE));
        VillagerProfileManager.setSkill(
                helper.getLevel(),
                attacker,
                VillagerSkill.GUARDING,
                VillagerCombatSkillBehavior.AXE_BREAKER_GUARDING_REQUIRED);
        target.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD));
        target.startUsingItem(InteractionHand.OFF_HAND);
        target.moveTo(attacker.getX() + 1.5D, attacker.getY(), attacker.getZ(), 0.0F, 0.0F);

        helper.assertValueEqual(
                VillagerCombatStateMachine.prepare(attacker, target, attacker.distanceToSqr(target)),
                VillagerCombatStateMachine.CombatMode.AXE_BREAKER,
                EMPTY_TEMPLATE);
        helper.assertTrue(
                VillagerRetaliationRetaliationUtil.canMeleeHit(attacker, target),
                EMPTY_TEMPLATE);

        VillagerCombatStateMachine.clearState(attacker);
        target.stopUsingItem();
        target.discard();
        attacker.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void rangedCombatRepairsStaleDownedHitbox(GameTestHelper helper) {
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 2));
        Zombie target = spawnZombie(helper, new BlockPos(5, 2, 2));
        float standingHeight = villager.getDimensions(net.minecraft.world.entity.Pose.STANDING).height();
        VillagerDownedService.enterDowned(
                helper.getLevel(),
                villager,
                new VillagerDeathProtectionResolver.ProtectionResult(true, List.of(EMPTY_TEMPLATE)));
        helper.assertTrue(villager.getBbHeight() < standingHeight, EMPTY_TEMPLATE);

        villager.getPersistentData().remove(VillagerDownedService.DOWNED_STATE_TAG);
        VillagerCombatStateMachine.prepare(villager, target, villager.distanceToSqr(target));

        helper.assertTrue(Math.abs(villager.getBbHeight() - standingHeight) < 1.0E-4F, EMPTY_TEMPLATE);
        Vec3 torsoStart = new Vec3(villager.getX() - 1.0D, villager.getY() + 1.25D, villager.getZ());
        Vec3 torsoEnd = new Vec3(villager.getX() + 1.0D, villager.getY() + 1.25D, villager.getZ());
        Vec3 headStart = new Vec3(villager.getX() - 1.0D, villager.getY() + 1.8D, villager.getZ());
        Vec3 headEnd = new Vec3(villager.getX() + 1.0D, villager.getY() + 1.8D, villager.getZ());
        helper.assertTrue(villager.getBoundingBox().clip(torsoStart, torsoEnd).isPresent(), EMPTY_TEMPLATE);
        helper.assertTrue(villager.getBoundingBox().clip(headStart, headEnd).isPresent(), EMPTY_TEMPLATE);

        VillagerCombatStateMachine.clearState(villager);
        target.discard();
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, batch = "combat_config_gates")
    public static void professionCombatTogglesGateArmedVillagers(GameTestHelper helper) {
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 2));
        boolean weaponsmiths = VillagerRetaliationConfig.WEAPONSMITHS_FIGHT_BACK.get();
        boolean toolsmiths = VillagerRetaliationConfig.TOOLSMITHS_FIGHT_BACK.get();
        boolean armorers = VillagerRetaliationConfig.ARMORERS_FIGHT_BACK.get();
        boolean fletchers = VillagerRetaliationConfig.FLETCHERS_FIGHT_BACK.get();
        boolean butchers = VillagerRetaliationConfig.BUTCHERS_FIGHT_BACK.get();
        boolean clerics = VillagerRetaliationConfig.CLERICS_USE_POTIONS.get();
        try {
            VillagerRetaliationConfig.WEAPONSMITHS_FIGHT_BACK.set(false);
            VillagerRetaliationConfig.TOOLSMITHS_FIGHT_BACK.set(false);
            VillagerRetaliationConfig.ARMORERS_FIGHT_BACK.set(false);
            VillagerRetaliationConfig.FLETCHERS_FIGHT_BACK.set(false);
            VillagerRetaliationConfig.BUTCHERS_FIGHT_BACK.set(false);
            VillagerRetaliationConfig.CLERICS_USE_POTIONS.set(false);
            villager.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));

            for (VillagerProfession profession : List.of(
                    VillagerProfession.WEAPONSMITH,
                    VillagerProfession.TOOLSMITH,
                    VillagerProfession.MASON,
                    VillagerProfession.ARMORER,
                    VillagerProfession.FLETCHER,
                    VillagerProfession.BUTCHER,
                    VillagerProfession.CLERIC)) {
                villager.setVillagerData(villager.getVillagerData().setProfession(profession));
                helper.assertFalse(
                        VillagerCombatRoles.canFightBack(villager),
                        profession + " should obey its disabled combat setting even while armed");
            }

            VillagerRetaliationConfig.FLETCHERS_FIGHT_BACK.set(true);
            villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.FLETCHER));
            helper.assertTrue(VillagerCombatRoles.canFightBack(villager), "enabled fletcher should retaliate");
        } finally {
            VillagerRetaliationConfig.WEAPONSMITHS_FIGHT_BACK.set(weaponsmiths);
            VillagerRetaliationConfig.TOOLSMITHS_FIGHT_BACK.set(toolsmiths);
            VillagerRetaliationConfig.ARMORERS_FIGHT_BACK.set(armorers);
            VillagerRetaliationConfig.FLETCHERS_FIGHT_BACK.set(fletchers);
            VillagerRetaliationConfig.BUTCHERS_FIGHT_BACK.set(butchers);
            VillagerRetaliationConfig.CLERICS_USE_POTIONS.set(clerics);
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void clericsSupportAndProtectWanderingTradersFromSplash(GameTestHelper helper) {
        Villager cleric = spawnVillager(helper, new BlockPos(2, 2, 2));
        cleric.setVillagerData(cleric.getVillagerData().setProfession(VillagerProfession.CLERIC));
        Zombie hostile = spawnZombie(helper, new BlockPos(4, 2, 2));
        WanderingTrader trader = EntityType.WANDERING_TRADER.create(helper.getLevel());
        if (trader == null) {
            throw new GameTestAssertException("Could not create wandering trader");
        }
        BlockPos traderPos = helper.absolutePos(new BlockPos(4, 2, 3));
        trader.moveTo(traderPos.getX() + 0.5D, traderPos.getY(), traderPos.getZ() + 0.5D, 0.0F, 0.0F);
        helper.getLevel().addFreshEntity(trader);
        trader.setHealth(Math.max(1.0F, trader.getMaxHealth() * 0.25F));

        helper.assertTrue(
                VillagerClericPotionHelper.isSupportTarget(cleric, trader, 0.6F, false),
                "injured wandering trader should be a cleric support target");
        helper.assertFalse(
                VillagerClericPotionHelper.isSafeOffensiveThrow(
                        cleric,
                        hostile,
                        PotionContents.createItemStack(Items.SPLASH_POTION, Potions.HARMING)),
                "cleric should not throw a harmful splash potion beside a wandering trader");

        trader.discard();
        hostile.discard();
        cleric.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void smithRepairsOnlyNaturalAlliedGolems(GameTestHelper helper) {
        Villager smith = spawnVillager(helper, new BlockPos(2, 2, 2));
        IronGolem allied = helper.spawn(EntityType.IRON_GOLEM, new BlockPos(3, 2, 2));
        IronGolem foreign = helper.spawn(EntityType.IRON_GOLEM, new BlockPos(4, 2, 2));
        IronGolem playerCreated = helper.spawn(EntityType.IRON_GOLEM, new BlockPos(5, 2, 2));
        allied.setHealth(50.0F);
        foreign.setHealth(50.0F);
        playerCreated.setHealth(50.0F);
        playerCreated.setPlayerCreated(true);

        VillageAllegianceRegistrySavedData registry = VillageAllegianceRegistrySavedData.get(helper.getLevel());
        VillageAllegianceId smithHome = registry.create(
                helper.getLevel().getGameTime(),
                helper.getLevel().dimension().location(),
                smith.blockPosition(),
                "Smith Repair Home");
        VillageAllegianceId foreignHome = registry.create(
                helper.getLevel().getGameTime(),
                helper.getLevel().dimension().location(),
                foreign.blockPosition(),
                "Foreign Golem Home");
        VillageAllegianceApi.assignKnown(
                helper.getLevel(), smith, smithHome, AllegianceAssignmentSource.ADMIN);
        VillageAllegianceApi.assignKnown(
                helper.getLevel(), allied, smithHome, AllegianceAssignmentSource.ADMIN);
        VillageAllegianceApi.assignKnown(
                helper.getLevel(), foreign, foreignHome, AllegianceAssignmentSource.ADMIN);
        VillageAllegianceApi.assignKnown(
                helper.getLevel(), playerCreated, smithHome, AllegianceAssignmentSource.ADMIN);

        helper.assertTrue(
                VillagerSmithGolemRepairSupport.isRepairTarget(smith, helper.getLevel(), allied),
                "a damaged natural golem from the smith''s village should be repairable");
        helper.assertFalse(
                VillagerSmithGolemRepairSupport.isRepairTarget(smith, helper.getLevel(), foreign),
                "a foreign-village golem should not be repairable");
        helper.assertFalse(
                VillagerSmithGolemRepairSupport.isRepairTarget(smith, helper.getLevel(), playerCreated),
                "a player-created golem should not be repairable");

        allied.discard();
        foreign.discard();
        playerCreated.discard();
        smith.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void firedBowLosesDurability(GameTestHelper helper) {
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 2));
        Zombie target = spawnZombie(helper, new BlockPos(5, 2, 2));
        ItemStack bow = new ItemStack(Items.BOW);
        villager.setItemInHand(InteractionHand.MAIN_HAND, bow);
        VillagerInventoryAccess.addItem(villager, new ItemStack(Items.ARROW));

        helper.assertTrue(
                VillagerRangedCombatHelper.fireBowLikeIllusioner(villager, target, helper.getLevel(), 1.0F),
                "bow attack should fire");
        helper.assertValueEqual(villager.getMainHandItem().getDamageValue(), 1, "bow durability after one shot");

        target.discard();
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void thrownTridentPreservesHeldStackComponents(GameTestHelper helper) {
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 2));
        Zombie target = spawnZombie(helper, new BlockPos(5, 2, 2));
        Component marker = Component.literal("component-preserving trident");
        ItemStack trident = new ItemStack(Items.TRIDENT);
        trident.set(DataComponents.CUSTOM_NAME, marker);
        villager.setItemInHand(InteractionHand.MAIN_HAND, trident);

        helper.assertTrue(
                VillagerRangedCombatHelper.tryAttack(
                        villager, target, helper.getLevel(), villager.distanceToSqr(target)),
                "trident attack should fire");
        List<ThrownTrident> projectiles = helper.getLevel().getEntitiesOfClass(
                ThrownTrident.class,
                villager.getBoundingBox().inflate(16.0D));
        helper.assertFalse(projectiles.isEmpty(), "trident projectile should be spawned");
        helper.assertValueEqual(
                projectiles.getFirst().getWeaponItem().get(DataComponents.CUSTOM_NAME),
                marker,
                "thrown trident should preserve held stack components");
        helper.assertValueEqual(villager.getMainHandItem().getDamageValue(), 1, "held trident durability after one throw");

        projectiles.forEach(ThrownTrident::discard);
        target.discard();
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void interruptedCrossbowChargeReusesReservedArrow(GameTestHelper helper) {
        Villager villager = spawnVillager(helper, new BlockPos(2, 2, 2));
        Zombie target = spawnZombie(helper, new BlockPos(5, 2, 2));
        HiredJobInventory inventory = HiredJobInventory.getJobInventory(villager);
        inventory.setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.CROSSBOW));
        inventory.setItem(HiredJobInventory.HOTBAR_START, new ItemStack(Items.ARROW, 2));
        inventory.syncMainHandEquipment();

        for (int tick = 0; tick < 5; tick++) {
            VillagerRangedCombatHelper.tryAttack(
                    villager, target, helper.getLevel(), villager.distanceToSqr(target));
        }
        helper.assertValueEqual(
                inventory.getItem(HiredJobInventory.HOTBAR_START).getCount(),
                1,
                "starting the first charge should reserve one arrow");
        helper.assertTrue(VillagerRangedCombatHelper.hasLoadedCrossbowProjectile(villager), "arrow should be reserved");

        villager.stopUsingItem();
        VillagerRangedCombatHelper.tryAttack(
                villager, target, helper.getLevel(), villager.distanceToSqr(target));
        VillagerRangedCombatHelper.tryAttack(
                villager, target, helper.getLevel(), villager.distanceToSqr(target));

        helper.assertValueEqual(
                inventory.getItem(HiredJobInventory.HOTBAR_START).getCount(),
                1,
                "restarting an interrupted charge must not consume another arrow");
        VillagerRangedCombatHelper.clearState(villager);
        target.discard();
        villager.discard();
        helper.succeed();
    }

    private static void buildFloor(GameTestHelper helper, int minX, int maxX, int minZ, int maxZ, int y) {
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                setBlock(helper, new BlockPos(x, y, z), Blocks.STONE.defaultBlockState());
                setBlock(helper, new BlockPos(x, y + 1, z), Blocks.AIR.defaultBlockState());
                setBlock(helper, new BlockPos(x, y + 2, z), Blocks.AIR.defaultBlockState());
            }
        }
    }

    private static void setBlock(GameTestHelper helper, BlockPos relativePos, BlockState state) {
        helper.getLevel().setBlock(helper.absolutePos(relativePos), state, Block.UPDATE_ALL);
    }

    private static Villager spawnVillager(GameTestHelper helper, BlockPos relativePos) {
        ServerLevel level = helper.getLevel();
        Villager villager = EntityType.VILLAGER.create(level);
        if (villager == null) {
            throw new GameTestAssertException("Could not create villager");
        }
        BlockPos pos = helper.absolutePos(relativePos);
        villager.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);
        if (!level.addFreshEntity(villager)) {
            throw new GameTestAssertException("Could not add villager to level");
        }
        return villager;
    }

    private static Zombie spawnZombie(GameTestHelper helper, BlockPos relativePos) {
        ServerLevel level = helper.getLevel();
        Zombie zombie = EntityType.ZOMBIE.create(level);
        if (zombie == null) {
            throw new GameTestAssertException("Could not create zombie");
        }
        BlockPos pos = helper.absolutePos(relativePos);
        zombie.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);
        if (!level.addFreshEntity(zombie)) {
            throw new GameTestAssertException("Could not add zombie to level");
        }
        return zombie;
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
