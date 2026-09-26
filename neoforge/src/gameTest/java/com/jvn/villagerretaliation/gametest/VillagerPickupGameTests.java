package com.jvn.villagerretaliation.gametest;

import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.NearestItemSensor;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityMobGriefingEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class VillagerPickupGameTests {
    private static final String EMPTY_TEMPLATE = "empty";

    private VillagerPickupGameTests() {
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void wantedItemSensorRetainsReachableFoodAcrossFence(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Villager villager = villager(helper, new BlockPos(4, 2, 2));
        BlockPos fence = helper.absolutePos(new BlockPos(3, 2, 2));
        level.setBlockAndUpdate(fence, Blocks.OAK_FENCE.defaultBlockState());
        ItemEntity bread = bread(level, villager.getX() - 2.0D, villager.getY(), villager.getZ());
        var sensor = new TestItemSensor();
        try {
            helper.assertFalse(villager.hasLineOfSight(bread), "the fence blocks the vanilla item sensor's sight line");
            sensor.scan(level, villager);
            helper.assertTrue(villager.getBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_WANTED_ITEM)
                    .orElse(null) == bread, "sensor scans retain food physically reachable through a fence");
            bread.setPickUpDelay(40);
            sensor.scan(level, villager);
            helper.assertTrue(villager.getBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_WANTED_ITEM).isEmpty(),
                    "the fence fallback cannot select food that is not ready for pickup");
            bread.setNoPickUpDelay();
            level.setBlockAndUpdate(fence, Blocks.STONE.defaultBlockState());
            sensor.scan(level, villager);
            helper.assertTrue(villager.getBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_WANTED_ITEM).isEmpty(),
                    "the fallback cannot find food through a solid wall");
            level.removeBlock(fence, false);
            sensor.scan(level, villager);
            helper.assertTrue(villager.getBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_WANTED_ITEM)
                    .orElse(null) == bread, "normal visible-item sensing still works");
        } finally {
            villager.discard();
            bread.discard();
            level.removeBlock(fence, false);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void extraPickupRespectsDisabledLootPickup(GameTestHelper helper) {
        Villager villager = villager(helper, new BlockPos(2, 2, 2));
        villager.setCanPickUpLoot(false);
        ItemEntity bread = bread(helper.getLevel(), villager.getX() + 1.2D, villager.getY(), villager.getZ());
        villager.getBrain().setMemory(MemoryModuleType.NEAREST_VISIBLE_WANTED_ITEM, bread);
        helper.runAfterDelay(4, () -> {
            try {
                helper.assertTrue(bread.isAlive(), "a villager with pickup disabled must leave wanted food alone");
                helper.assertValueEqual(villager.getInventory().countItem(Items.BREAD), 0,
                        "the extra pickup path cannot bypass the pickup flag");
            } finally {
                villager.discard();
                bread.discard();
            }
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void extraPickupRespectsGriefingProtection(GameTestHelper helper) {
        Villager villager = villager(helper, new BlockPos(2, 2, 2));
        ItemEntity bread = bread(helper.getLevel(), villager.getX() + 1.2D, villager.getY(), villager.getZ());
        villager.getBrain().setMemory(MemoryModuleType.NEAREST_VISIBLE_WANTED_ITEM, bread);
        Consumer<EntityMobGriefingEvent> protection = event -> {
            if (event.getEntity() == villager) {
                event.setCanGrief(false);
            }
        };
        NeoForge.EVENT_BUS.addListener(EntityMobGriefingEvent.class, protection);
        helper.runAfterDelay(4, () -> {
            try {
                helper.assertTrue(bread.isAlive(), "griefing protection must also block the extra pickup path");
                helper.assertValueEqual(villager.getInventory().countItem(Items.BREAD), 0,
                        "protected food must not enter the villager inventory");
            } finally {
                NeoForge.EVENT_BUS.unregister(protection);
                villager.discard();
                bread.discard();
            }
            helper.succeed();
        });
    }

    private static Villager villager(GameTestHelper helper, BlockPos position) {
        ServerLevel level = helper.getLevel();
        Villager villager = EntityType.VILLAGER.create(level);
        helper.assertTrue(villager != null, "test villager can be created");
        BlockPos absolute = helper.absolutePos(position);
        villager.moveTo(absolute.getX() + 0.5D, absolute.getY(), absolute.getZ() + 0.5D);
        villager.setNoGravity(true);
        villager.setCanPickUpLoot(true);
        villager.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.0D);
        level.addFreshEntity(villager);
        return villager;
    }

    private static ItemEntity bread(ServerLevel level, double x, double y, double z) {
        ItemEntity bread = new ItemEntity(level, x, y, z, new ItemStack(Items.BREAD));
        bread.setNoPickUpDelay();
        bread.setNoGravity(true);
        bread.setDeltaMovement(Vec3.ZERO);
        level.addFreshEntity(bread);
        return bread;
    }

    private static final class TestItemSensor extends NearestItemSensor {
        private void scan(ServerLevel level, Villager villager) {
            super.doTick(level, villager);
        }
    }
}
