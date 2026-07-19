package com.jvn.villagerretaliation.combat;

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
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
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
