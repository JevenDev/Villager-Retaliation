package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.block.SettlementDepotBlockEntity;
import com.jvn.villagerretaliation.block.VillagerRetaliationBlocks;
import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class SettlementDepotRulesGameTests {
    private SettlementDepotRulesGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_depot_rules")
    public static void frameEditsImmediatelyUpdateBothDepotAndLiveContainerRules(GameTestHelper helper) {
        var depot = fixture(helper);
        var frame = frame(helper, depot, Direction.SOUTH, new ItemStack(Items.IRON_INGOT));
        try {
            helper.assertTrue(depot.rulesObserved(), "the fully loaded depot has a complete rule observation");
            helper.assertTrue(depot.observedRules().getFirst().is(Items.IRON_INGOT), "the added frame is observed immediately");
            helper.assertTrue(AssignedStorageService.observedContainerRules(helper.getLevel(), depot.position()).orElseThrow().getFirst().is(Items.IRON_INGOT),
                    "the live container resolver initially sees iron");
            frame.setItem(new ItemStack(Items.BREAD));
            helper.assertTrue(depot.observedRules().getFirst().is(Items.BREAD), "the actual setItem hook records the final edit without a periodic tick");
            helper.assertTrue(AssignedStorageService.observedContainerRules(helper.getLevel(), depot.position()).orElseThrow().getFirst().is(Items.BREAD),
                    "the same edit invalidates the live container rule cache");
        } finally {
            frame.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_depot_rules")
    public static void sequentialFrameUnloadsPreserveEveryFinalRuleAcrossSaving(GameTestHelper helper) {
        var depot = fixture(helper);
        var south = frame(helper, depot, Direction.SOUTH, new ItemStack(Items.IRON_INGOT));
        var north = frame(helper, depot, Direction.NORTH, new ItemStack(Items.BREAD));
        ItemStack edited = new ItemStack(Items.GOLD_INGOT);
        edited.set(DataComponents.CUSTOM_NAME, Component.literal("Final selection"));
        south.setItem(edited);
        south.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        helper.assertValueEqual(depot.observedRules().size(), 2, "the first frame unload does not erase its own rule or its neighbor");
        north.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        var restored = SettlementDepot.load(depot.save(helper.getLevel().registryAccess()), helper.getLevel().registryAccess(), () -> {});
        helper.assertTrue(restored.rulesObserved(), "the final complete observation persists");
        helper.assertValueEqual(restored.observedRules().size(), 2, "sequential unloads preserve both frame rules");
        helper.assertTrue(restored.observedRules().stream().anyMatch(stack -> ItemStack.isSameItemSameComponents(stack, edited)),
                "the last edit preserves exact item components");
        helper.assertTrue(restored.observedRules().stream().anyMatch(stack -> stack.is(Items.BREAD)), "the other frame remains restrictive");
        restored.observeFrame(south.getUUID(), Direction.SOUTH, new ItemStack(Items.DIAMOND));
        helper.assertValueEqual(restored.observedRules().size(), 2, "saved frame identity lets one returning rule replace only itself");
        helper.assertFalse(restored.observedRules().stream().anyMatch(stack -> stack.is(Items.GOLD_INGOT)), "the prior value does not accumulate");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_depot_rules")
    public static void emptyOrDestroyedFramesRemoveOnlyTheirOwnRule(GameTestHelper helper) {
        var depot = fixture(helper);
        var south = frame(helper, depot, Direction.SOUTH, new ItemStack(Items.IRON_INGOT));
        var north = frame(helper, depot, Direction.NORTH, new ItemStack(Items.BREAD));
        try {
            south.setItem(ItemStack.EMPTY);
            helper.assertValueEqual(depot.observedRules().size(), 1, "emptying one frame removes just that restriction");
            helper.assertTrue(depot.observedRules().getFirst().is(Items.BREAD), "the other frame remains authoritative");
            south.setItem(new ItemStack(Items.GOLD_INGOT));
            helper.assertValueEqual(depot.observedRules().size(), 2, "refilling an empty frame restores its real rule");
            north.discard();
            helper.assertValueEqual(depot.observedRules().size(), 1, "destroying a frame removes its rule immediately");
            south.discard();
            helper.assertTrue(depot.rulesObserved() && depot.observedRules().isEmpty(), "removing every frame records a known unrestricted depot");
        } finally {
            if (!south.isRemoved()) south.discard();
            if (!north.isRemoved()) north.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_depot_rules")
    public static void unloadedObservationRetainsRulesWithoutLoadingTerrain(GameTestHelper helper) {
        BlockPos position = new BlockPos(10_000_000 + helper.absolutePos(BlockPos.ZERO).getX() * 8, 64, 10_000_000);
        var depot = new SettlementDepot(UUID.randomUUID(), UUID.randomUUID(), position, SettlementDepotPurpose.GENERAL, 5, () -> {});
        var id = UUID.randomUUID();
        depot.observeFrames(Map.of(id, new SettlementDepot.FrameRule(Direction.SOUTH, new ItemStack(Items.IRON_INGOT))));
        int chunks = helper.getLevel().getChunkSource().getLoadedChunksCount();
        SettlementDepotRules.observe(helper.getLevel(), depot);
        helper.assertTrue(depot.rulesObserved() && depot.observedRules().getFirst().is(Items.IRON_INGOT), "missing entities never become an empty rule observation");
        helper.assertValueEqual(helper.getLevel().getChunkSource().getLoadedChunksCount(), chunks, "rule handoff does not force chunks");
        depot.observeFrame(id, Direction.SOUTH, new ItemStack(Items.BREAD));
        helper.assertTrue(depot.observedRules().getFirst().is(Items.BREAD), "a final per-frame observation updates an unloaded snapshot");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_depot_rules")
    public static void legacyRulesRemainSavedAndPartialEditsRequireACompleteSurvey(GameTestHelper helper) {
        var depot = new SettlementDepot(UUID.randomUUID(), UUID.randomUUID(), new BlockPos(10_000_000, 64, 10_000_000),
                SettlementDepotPurpose.GENERAL, 5, () -> {});
        depot.observeRules(List.of(new ItemStack(Items.IRON_INGOT), new ItemStack(Items.BREAD)));
        var legacy = depot.save(helper.getLevel().registryAccess());
        legacy.remove("FrameRules");
        legacy.remove("FramesObserved");
        var restored = SettlementDepot.load(legacy, helper.getLevel().registryAccess(), () -> {});
        helper.assertTrue(restored.rulesObserved() && restored.observedRules().size() == 2, "legacy complete observations remain usable before edits");
        var id = UUID.randomUUID();
        restored.observeFrame(id, Direction.SOUTH, new ItemStack(Items.GOLD_INGOT));
        helper.assertFalse(restored.rulesObserved(), "an unidentified partial edit cannot make legacy restrictions less strict");
        helper.assertValueEqual(restored.observedRules().size(), 2, "the old rule snapshot is retained until full observation");
        restored.observeFrames(Map.of(id, new SettlementDepot.FrameRule(Direction.SOUTH, new ItemStack(Items.GOLD_INGOT))));
        helper.assertTrue(restored.rulesObserved() && restored.observedRules().getFirst().is(Items.GOLD_INGOT),
                "a complete survey safely replaces the legacy rules");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_depot_rules")
    public static void unrelatedNearbyFramesCannotAlterDepotRules(GameTestHelper helper) {
        var depot = fixture(helper);
        var owned = frame(helper, depot, Direction.SOUTH, new ItemStack(Items.IRON_INGOT));
        var nearby = new ItemFrame(helper.getLevel(), depot.position().east().relative(Direction.SOUTH), Direction.SOUTH);
        nearby.setItem(new ItemStack(Items.BREAD));
        helper.getLevel().addFreshEntity(nearby);
        try {
            SettlementDepotRules.observe(helper.getLevel(), depot);
            helper.assertValueEqual(depot.observedRules().size(), 1, "only frames attached to the depot contribute rules");
            nearby.setItem(new ItemStack(Items.DIAMOND));
            helper.assertTrue(depot.observedRules().getFirst().is(Items.IRON_INGOT), "editing a nearby frame leaves the depot unchanged");
        } finally {
            nearby.discard();
            owned.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_depot_rules")
    public static void terrainLoadedBeforeItsEntitiesCannotClearObservedFilters(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos position = new BlockPos(12_000_000 + helper.absolutePos(BlockPos.ZERO).getX() * 32, 64, 12_000_000);
        level.getChunk(position.getX() >> 4, position.getZ() >> 4);
        helper.assertTrue(level.hasChunkAt(position), "the terrain is available before its entity load is processed");
        helper.assertFalse(level.areEntitiesLoaded(net.minecraft.world.level.ChunkPos.asLong(position)), "the native entity load is still pending");
        var depot = new SettlementDepot(UUID.randomUUID(), UUID.randomUUID(), position, SettlementDepotPurpose.GENERAL, 5, () -> {});
        depot.observeFrames(Map.of(UUID.randomUUID(), new SettlementDepot.FrameRule(Direction.SOUTH, new ItemStack(Items.IRON_INGOT))));
        helper.assertFalse(com.jvn.villagerretaliation.inventory.ContainerFilterResolver.canObserveEntities(level, position),
                "live filter resolution cannot treat pending entities as an empty scan");
        SettlementDepotRules.observe(level, depot);
        helper.assertTrue(depot.rulesObserved() && depot.observedRules().getFirst().is(Items.IRON_INGOT),
                "terrain-only loading preserves the existing restriction");
        helper.succeed();
    }

    private static ItemFrame frame(GameTestHelper helper, SettlementDepot depot, Direction direction, ItemStack item) {
        var frame = new ItemFrame(helper.getLevel(), depot.position().relative(direction), direction);
        frame.setItem(item);
        helper.assertTrue(helper.getLevel().addFreshEntity(frame), "the real item frame is loaded");
        return frame;
    }

    private static SettlementDepot fixture(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos position = helper.absolutePos(new BlockPos(12, 3, 12));
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "FrameLeader"));
        player.setPos(position.getCenter());
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var id = registry.create(level.getGameTime(), level.dimension().location(), position, "");
        var data = SettlementSavedData.get(level);
        var town = data.establish(registry, id, player.getUUID(), position, level.getGameTime()).orElseThrow();
        var area = new SettlementArea(UUID.randomUUID(), SettlementAreaType.DEPOT, "Store", position.offset(-2, -1, -2), position.offset(2, 2, 2), 5, true);
        helper.assertTrue(data.putArea(registry, id, player.getUUID(), area), "the depot area is registered");
        level.setBlockAndUpdate(position, VillagerRetaliationBlocks.SETTLEMENT_DEPOT.get().defaultBlockState());
        helper.assertTrue(SettlementDepotService.register(player, position), "the depot is bound");
        return ((SettlementDepotBlockEntity) level.getBlockEntity(position)).inventory();
    }
}
