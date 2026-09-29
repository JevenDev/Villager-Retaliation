package com.jvn.villagerretaliation.interaction.work.production;

import com.jvn.villagerretaliation.interaction.HireContract;
import com.jvn.villagerretaliation.interaction.HireContractStore;
import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.HiredWorkArea;
import com.jvn.villagerretaliation.interaction.HiredWorkStateStore;
import com.jvn.villagerretaliation.interaction.work.HiredWorkContext;
import com.jvn.villagerretaliation.interaction.work.HiredWorkerBrain;
import com.jvn.villagerretaliation.interaction.work.brewing.BrewingWorker;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("villagerretaliation")
@PrefixGameTestTemplate(false)
public final class HiredQueuedBrewingGameTests {
    private HiredQueuedBrewingGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "queued_brewing")
    public static void cancelledPersonalBrewingFinishesItsReservedBottlesWithNativeTiming(GameTestHelper helper) {
        var fixture = fixture(helper);
        try {
            var unavailable = order(helper, "regeneration", 1);
            var healing = order(helper, "healing", 3);
            helper.assertTrue(HiredProductionService.appendPersonal(fixture.owner(), fixture.villager(), 0L, unavailable).isEmpty(), "a private brewer accepts an exact potion target");
            helper.assertTrue(HiredProductionService.appendPersonal(fixture.owner(), fixture.villager(), queue(helper, fixture).configurationRevision(), healing).isEmpty(), "a lower runnable potion target is accepted");
            tick(helper, fixture);
            helper.assertValueEqual(queue(helper, fixture).pending(healing.id()), 3, "the exact batch is reserved before native processing (" + queue(helper, fixture).order(healing.id()).blocker() + ", " + fixture.context().state().getString("BrewingMissingMaterials") + ")");
            helper.assertTrue(queue(helper, fixture).allocation(fixture.villager().getUUID()).started(), "loading real water bottles starts the receipt");
            helper.assertFalse(queue(helper, fixture).order(unavailable.id()).blocker().isEmpty(), "the missing ghast tear blocks only its own entry");
            helper.assertTrue(queue(helper, fixture).cancel(healing.id()), "the remaining order can be cancelled");
            tick(helper, fixture);
            tick(helper, fixture);
            brew(helper, fixture, 400);
            helper.assertTrue(PotionContents.createItemStack(Items.POTION, Potions.WATER).getComponents().equals(fixture.stand().getItem(0).getComponents()), "the first step still needs its final native tick");
            brew(helper, fixture, 1);
            tick(helper, fixture);
            brew(helper, fixture, 401);
            tick(helper, fixture);
            helper.assertValueEqual(queue(helper, fixture).order(healing.id()).produced(), 3L, "only the three finished bottles settle the cancelled receipt");
            helper.assertValueEqual(queue(helper, fixture).pending(healing.id()), 0, "collecting the whole batch releases its quantity");
            helper.assertTrue(fixture.context().inventory().hasOutput(stack -> HiredProductionStock.matches(healing.output(), stack)), "exact healing potions are classified as production output");
            helper.assertValueEqual(fixture.context().inventory().countItem(Items.NETHER_WART), 2, "one native batch consumes one wart");
            helper.assertValueEqual(fixture.context().inventory().countItem(Items.GLISTERING_MELON_SLICE), 2, "one native batch consumes one melon");
            helper.assertValueEqual(fixture.context().inventory().countItem(Items.BLAZE_POWDER), 1, "one real powder supplies both native steps");
            tick(helper, fixture);
            helper.assertValueEqual(queue(helper, fixture).order(healing.id()).produced(), 3L, "cancelled work never starts another batch");
            helper.assertTrue(HiredProductionStations.get(helper.getLevel()).available(helper.getLevel(), fixture.stand().getBlockPos()), "completion releases the physical stand");
        } finally {
            cleanup(helper, fixture);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "queued_brewing")
    public static void legacyBrewersCannotCollectAnotherQueuesFinishedPotion(GameTestHelper helper) {
        var fixture = fixture(helper);
        var other = EntityType.VILLAGER.create(helper.getLevel());
        other.setPos(fixture.villager().position());
        other.setNoAi(true);
        other.setNoGravity(true);
        helper.getLevel().addFreshEntity(other);
        try {
            var healing = order(helper, "healing", 1);
            HiredProductionService.appendPersonal(fixture.owner(), fixture.villager(), 0L, healing);
            tick(helper, fixture);
            helper.assertValueEqual(queue(helper, fixture).pending(healing.id()), 1, "the initial native batch is reserved (" + queue(helper, fixture).order(healing.id()).blocker() + ")");
            tick(helper, fixture);
            tick(helper, fixture);
            brew(helper, fixture, 401);
            tick(helper, fixture);
            brew(helper, fixture, 401);
            var state = HiredWorkStateStore.state(other);
            HiredWorkerBrain.initialize(state);
            BrewingWorker.setOrder(state, healing.recipe().brewingItem(), healing.recipe().potion(), 1, false);
            var context = fixture.context();
            var otherContext = new HiredWorkContext(HiredJobInventory.getJobInventory(other), state, context.workCenter(), context.workMin(), context.workMax(),
                    context.radius(), context.verticalRadius(), true, 100, false, false);
            new BrewingWorker().tick(helper.getLevel(), other, fixture.owner(), otherContext);
            helper.assertFalse(fixture.stand().getItem(0).isEmpty(), "legacy attention cannot collect another worker's reserved potion");
            tick(helper, fixture);
            helper.assertValueEqual(queue(helper, fixture).order(healing.id()).produced(), 1L, "the owning worker settles the actual potion");
        } finally {
            cleanup(helper, fixture);
            other.discard();
        }
        helper.succeed();
    }

    private static HiredProductionOrder order(GameTestHelper helper, String potion, int amount) {
        return HiredProductionOrder.create(helper.getLevel(), HiredProductionRecipe.brewing(ResourceLocation.withDefaultNamespace("potion"),
                ResourceLocation.withDefaultNamespace(potion)), HiredProductionOrder.Mode.BATCH, amount, null);
    }

    private static void tick(GameTestHelper helper, Fixture fixture) {
        new BrewingWorker().tick(helper.getLevel(), fixture.villager(), fixture.owner(), fixture.context());
    }

    private static void brew(GameTestHelper helper, Fixture fixture, int ticks) {
        for (int tick = 0; tick < ticks; tick++) BrewingStandBlockEntity.serverTick(helper.getLevel(), fixture.stand().getBlockPos(),
                helper.getLevel().getBlockState(fixture.stand().getBlockPos()), fixture.stand());
    }

    private static HiredProductionQueue queue(GameTestHelper helper, Fixture fixture) {
        return HiredProductionService.access(helper.getLevel(), fixture.villager(), HiredVillagerRole.BREWING).queue();
    }

    private static void cleanup(GameTestHelper helper, Fixture fixture) {
        HiredProductionStations.get(helper.getLevel()).workerRemoved(fixture.villager().getUUID());
        fixture.villager().discard();
    }

    private static Fixture fixture(GameTestHelper helper) {
        var level = helper.getLevel();
        for (int x = 0; x <= 6; x++) for (int z = 0; z <= 6; z++) {
            level.setBlock(helper.absolutePos(new BlockPos(x, 1, z)), Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(helper.absolutePos(new BlockPos(x, 2, z)), Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(helper.absolutePos(new BlockPos(x, 3, z)), Blocks.AIR.defaultBlockState(), 3);
        }
        var position = helper.absolutePos(new BlockPos(3, 2, 3));
        level.setBlock(position, Blocks.BREWING_STAND.defaultBlockState(), 3);
        var stand = (BrewingStandBlockEntity) level.getBlockEntity(position);
        var villager = EntityType.VILLAGER.create(level);
        villager.setPos(helper.absolutePos(new BlockPos(2, 2, 2)).getBottomCenter());
        villager.setNoAi(true);
        villager.setNoGravity(true);
        helper.assertTrue(level.addFreshEntity(villager), "the personal brewer is loaded");
        var owner = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "QueueBrewer"));
        HireContractStore.save(villager, HireContract.regular(owner.getUUID(), level.getGameTime(), 1, 1, 1, HiredVillagerRole.BREWING));
        var state = HiredWorkStateStore.state(villager);
        HiredWorkerBrain.initialize(state);
        var area = HiredWorkArea.fromBounds(helper.absolutePos(new BlockPos(1, 2, 1)), helper.absolutePos(new BlockPos(5, 4, 5)), true);
        var inventory = HiredJobInventory.getJobInventory(villager);
        for (int bottle = 0; bottle < 3; bottle++) helper.assertTrue(inventory.insertSupply(PotionContents.createItemStack(Items.POTION, Potions.WATER)).isEmpty(), "real water bottles fit");
        inventory.insertSupply(new ItemStack(Items.NETHER_WART, 3));
        inventory.insertSupply(new ItemStack(Items.GLISTERING_MELON_SLICE, 3));
        inventory.insertSupply(new ItemStack(Items.BLAZE_POWDER, 2));
        var context = new HiredWorkContext(inventory, state, area.center(), area.min(), area.max(), area.horizontalRadius(), area.verticalRadius(), true, 100, false, false);
        return new Fixture(villager, owner, stand, context);
    }

    private record Fixture(Villager villager, ServerPlayer owner, BrewingStandBlockEntity stand, HiredWorkContext context) {
    }
}
