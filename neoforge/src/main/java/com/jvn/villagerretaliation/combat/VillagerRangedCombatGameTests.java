package com.jvn.villagerretaliation.combat;

import com.jvn.villagerretaliation.combat.downed.VillagerDeathProtectionResolver;
import com.jvn.villagerretaliation.combat.downed.VillagerDownedService;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.jvn.villagerretaliation.inventory.VillagerInventoryAccess;
import com.jvn.villagerretaliation.villager.VillagerMovementSpeedPolicy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.StructureUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
