package com.jvn.villagerretaliation.interaction.work.production;

import com.jvn.villagerretaliation.interaction.HireContract;
import com.jvn.villagerretaliation.interaction.HireContractStore;
import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.HiredWorkArea;
import com.jvn.villagerretaliation.interaction.HiredWorkStateStore;
import com.jvn.villagerretaliation.interaction.work.HiredWorkContext;
import com.jvn.villagerretaliation.interaction.work.HiredWorkerBrain;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.mojang.authlib.GameProfile;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("villagerretaliation")
@PrefixGameTestTemplate(false)
public final class HiredQueuedFurnaceGameTests {
    private HiredQueuedFurnaceGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "queued_furnace")
    public static void cancelledOrderFinishesItsLoadedNativeBatchExactlyOnce(GameTestHelper helper) {
        var fixture = fixture(helper);
        try {
            var order = order(helper, "iron_ingot_from_smelting_raw_iron", 3);
            helper.assertValueEqual(HiredProductionService.appendPersonal(fixture.owner(), fixture.villager(), 0L, order), "", "the finite furnace order is accepted");
            tick(helper, fixture);
            var furnace = furnace(helper, fixture);
            helper.assertValueEqual(furnace.getItem(0).getCount(), 3, "only the finite number of native input items is loaded");
            var queue = queue(helper, fixture);
            helper.assertValueEqual(queue.pending(order.id()), 3, "all loaded inputs are accounted as unfinished production");
            helper.assertTrue(queue.allocation(fixture.villager().getUUID()).started(), "the loaded batch is marked consumed");
            helper.assertTrue(queue.cancel(order.id()), "cancellation stops future input");
            cook(helper, fixture, 199);
            helper.assertTrue(furnace.getItem(2).isEmpty(), "a queue cannot shorten native cooking time");
            cook(helper, fixture, 1);
            tick(helper, fixture);
            helper.assertValueEqual(fixture.context().inventory().countItem(Items.IRON_INGOT), 1, "the first completed native item is collected");
            helper.assertValueEqual(queue(helper, fixture).pending(order.id()), 2, "unprocessed input remains reserved after a partial receipt");
            cook(helper, fixture, 400);
            tick(helper, fixture);
            tick(helper, fixture);
            helper.assertValueEqual(fixture.context().inventory().countItem(Items.IRON_INGOT), 3, "the cancelled loaded batch finishes once");
            helper.assertValueEqual(queue(helper, fixture).order(order.id()).produced(), 3L, "only real collected output counts");
            helper.assertValueEqual(queue(helper, fixture).pending(order.id()), 0, "finished production releases the shared quantity");
            helper.assertValueEqual(fixture.context().inventory().countItem(Items.RAW_IRON), 7, "cancellation leaves the remaining raw materials untouched");
            helper.assertTrue(HiredProductionStations.get(helper.getLevel()).available(helper.getLevel(), fixture.station()), "the completed station is reusable");
        } finally {
            cleanup(helper, fixture);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "queued_furnace")
    public static void exactFurnaceRecipeRequiresItsNativeStationType(GameTestHelper helper) {
        var fixture = fixture(helper);
        try {
            var order = order(helper, "iron_ingot_from_blasting_raw_iron", 1);
            HiredProductionService.appendPersonal(fixture.owner(), fixture.villager(), 0L, order);
            tick(helper, fixture);
            helper.assertTrue(furnace(helper, fixture).getItem(0).isEmpty(), "a normal furnace cannot replace an exact blasting recipe");
            helper.assertValueEqual(queue(helper, fixture).pending(order.id()), 0, "an incompatible station does not reserve quantity");
            helper.getLevel().setBlock(fixture.station(), Blocks.BLAST_FURNACE.defaultBlockState(), 3);
            tick(helper, fixture);
            cook(helper, fixture, 99);
            helper.assertTrue(furnace(helper, fixture).getItem(2).isEmpty(), "blasting retains its native duration");
            cook(helper, fixture, 1);
            tick(helper, fixture);
            helper.assertValueEqual(fixture.context().inventory().countItem(Items.IRON_INGOT), 1, "the exact blasting recipe finishes in its supported station");
        } finally {
            cleanup(helper, fixture);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "queued_furnace")
    public static void stationReservationsSurviveReloadAndRejectCorruptOwnership(GameTestHelper helper) {
        var fixture = fixture(helper);
        try {
            var order = order(helper, "iron_ingot_from_smelting_raw_iron", 2);
            HiredProductionService.appendPersonal(fixture.owner(), fixture.villager(), 0L, order);
            tick(helper, fixture);
            var provider = helper.getLevel().registryAccess();
            var saved = HiredProductionStations.get(helper.getLevel()).save(new CompoundTag(), provider);
            var restored = HiredProductionStations.load(saved, provider);
            helper.assertFalse(restored.available(helper.getLevel(), fixture.station()), "an in-progress station stays reserved after loading");
            helper.assertTrue(restored.claim(helper.getLevel(), fixture.station()).started(), "the saved claim retains its native processing state");
            var broken = saved.copy();
            broken.putInt("Version", 100);
            var rejected = HiredProductionStations.load(broken, provider);
            helper.assertTrue(rejected.readOnly(), "unsupported station data is held for recovery");
            helper.assertFalse(rejected.available(helper.getLevel(), fixture.station()), "damaged data cannot grant station ownership");
            helper.assertValueEqual(rejected.save(new CompoundTag(), provider), broken, "recovery preserves the entire original payload");
        } finally {
            cleanup(helper, fixture);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "queued_furnace")
    public static void adoptingOrdersCollectsExistingOutputWithoutCreditingNewProduction(GameTestHelper helper) {
        var fixture = fixture(helper);
        try {
            furnace(helper, fixture).setItem(2, new ItemStack(Items.IRON_INGOT, 2));
            var order = order(helper, "iron_ingot_from_smelting_raw_iron", 3);
            HiredProductionService.appendPersonal(fixture.owner(), fixture.villager(), 0L, order);
            tick(helper, fixture);
            helper.assertValueEqual(fixture.context().inventory().countItem(Items.IRON_INGOT), 2, "existing native output is preserved and collected");
            helper.assertValueEqual(queue(helper, fixture).order(order.id()).produced(), 0L, "legacy output does not satisfy a newly requested finite batch");
            tick(helper, fixture);
            helper.assertValueEqual(furnace(helper, fixture).getItem(0).getCount(), 3, "the new queue then loads only its own requested quantity");
            cook(helper, fixture, 600);
            tick(helper, fixture);
            helper.assertValueEqual(fixture.context().inventory().countItem(Items.IRON_INGOT), 5, "old output and the new finite batch both survive");
            helper.assertValueEqual(queue(helper, fixture).order(order.id()).produced(), 3L, "new progress counts only its own native batch");
        } finally {
            cleanup(helper, fixture);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "queued_furnace")
    public static void legacyWorkersCannotCollectAnotherProducersReservedBatch(GameTestHelper helper) {
        var fixture = fixture(helper);
        var level = helper.getLevel();
        var other = EntityType.VILLAGER.create(level);
        other.setPos(fixture.villager().position());
        other.setNoAi(true);
        other.setNoGravity(true);
        helper.assertTrue(level.addFreshEntity(other), "a second worker is loaded");
        try {
            var order = order(helper, "iron_ingot_from_smelting_raw_iron", 1);
            HiredProductionService.appendPersonal(fixture.owner(), fixture.villager(), 0L, order);
            tick(helper, fixture);
            cook(helper, fixture, 200);
            var state = HiredWorkStateStore.state(other);
            HiredWorkerBrain.initialize(state);
            var original = fixture.context();
            var context = new HiredWorkContext(HiredJobInventory.getJobInventory(other), state, original.workCenter(), original.workMin(), original.workMax(),
                    original.radius(), original.verticalRadius(), true, 100, false, false);
            new com.jvn.villagerretaliation.interaction.work.SmeltingWorker().tick(level, other, fixture.owner(), context);
            helper.assertValueEqual(furnace(helper, fixture).getItem(2).getCount(), 1, "a legacy worker leaves another queue's real output untouched");
            helper.assertValueEqual(context.inventory().countItem(Items.IRON_INGOT), 0, "unreserved legacy work cannot steal the batch");
            tick(helper, fixture);
            helper.assertValueEqual(queue(helper, fixture).order(order.id()).produced(), 1L, "the owning worker settles its original receipt");
        } finally {
            cleanup(helper, fixture);
            other.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "queued_furnace")
    public static void unavailableStationsRetainPendingBatchesUntilRemovalIsVerified(GameTestHelper helper) {
        var fixture = fixture(helper);
        try {
            var order = order(helper, "iron_ingot_from_smelting_raw_iron", 2);
            HiredProductionService.appendPersonal(fixture.owner(), fixture.villager(), 0L, order);
            tick(helper, fixture);
            var links = new CompoundTag();
            links.putLongArray("Stations", new long[] {fixture.station().above().asLong()});
            fixture.context().state().put("WorksiteLinks", links);
            tick(helper, fixture);
            helper.assertValueEqual(queue(helper, fixture).pending(order.id()), 2, "changing station permissions keeps the existing batch reserved");
            helper.assertFalse(HiredProductionStations.get(helper.getLevel()).available(helper.getLevel(), fixture.station()), "another worker cannot claim an inaccessible batch");
            fixture.context().state().remove("WorksiteLinks");
            helper.getLevel().setBlock(fixture.station(), Blocks.AIR.defaultBlockState(), 3);
            tick(helper, fixture);
            helper.assertValueEqual(queue(helper, fixture).pending(order.id()), 0, "verified station removal releases the lost batch");
            helper.assertValueEqual(queue(helper, fixture).order(order.id()).produced(), 0L, "lost inputs do not count as production");
            helper.assertTrue(HiredProductionStations.get(helper.getLevel()).available(helper.getLevel(), fixture.station()), "verified removal releases the physical reservation");
        } finally {
            cleanup(helper, fixture);
        }
        helper.succeed();
    }

    private static HiredProductionOrder order(GameTestHelper helper, String recipe, int amount) {
        return HiredProductionOrder.create(helper.getLevel(), HiredProductionRecipe.exact(ResourceLocation.withDefaultNamespace(recipe), Map.of()),
                HiredProductionOrder.Mode.BATCH, amount, null);
    }

    private static void tick(GameTestHelper helper, Fixture fixture) {
        new com.jvn.villagerretaliation.interaction.work.SmeltingWorker().tick(helper.getLevel(), fixture.villager(), fixture.owner(), fixture.context());
    }

    private static void cook(GameTestHelper helper, Fixture fixture, int ticks) {
        for (int tick = 0; tick < ticks; tick++) {
            AbstractFurnaceBlockEntity.serverTick(helper.getLevel(), fixture.station(), helper.getLevel().getBlockState(fixture.station()), furnace(helper, fixture));
        }
    }

    private static AbstractFurnaceBlockEntity furnace(GameTestHelper helper, Fixture fixture) {
        return (AbstractFurnaceBlockEntity) helper.getLevel().getBlockEntity(fixture.station());
    }

    private static HiredProductionQueue queue(GameTestHelper helper, Fixture fixture) {
        return HiredProductionService.access(helper.getLevel(), fixture.villager(), HiredVillagerRole.SMELTER).queue();
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
        var station = helper.absolutePos(new BlockPos(3, 2, 3));
        level.setBlock(station, Blocks.FURNACE.defaultBlockState(), 3);
        var villager = EntityType.VILLAGER.create(level);
        villager.setPos(helper.absolutePos(new BlockPos(2, 2, 2)).getBottomCenter());
        villager.setNoAi(true);
        villager.setNoGravity(true);
        helper.assertTrue(level.addFreshEntity(villager), "the producer is loaded");
        var owner = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "FurnaceOwner"));
        HireContractStore.save(villager, HireContract.regular(owner.getUUID(), level.getGameTime(), 1, 1, 1, HiredVillagerRole.SMELTER));
        var state = HiredWorkStateStore.state(villager);
        HiredWorkerBrain.initialize(state);
        var area = HiredWorkArea.fromBounds(helper.absolutePos(new BlockPos(1, 2, 1)), helper.absolutePos(new BlockPos(5, 4, 5)), true);
        var inventory = HiredJobInventory.getJobInventory(villager);
        inventory.insertSupply(new ItemStack(Items.RAW_IRON, 10));
        inventory.insertSupply(new ItemStack(Items.COAL));
        var context = new HiredWorkContext(inventory, state, area.center(), area.min(), area.max(), area.horizontalRadius(), area.verticalRadius(),
                true, 100, false, false);
        return new Fixture(villager, owner, station, context);
    }

    private record Fixture(Villager villager, ServerPlayer owner, BlockPos station, HiredWorkContext context) {
    }
}
