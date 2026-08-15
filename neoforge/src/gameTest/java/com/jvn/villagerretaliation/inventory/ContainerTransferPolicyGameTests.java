package com.jvn.villagerretaliation.inventory;

import com.jvn.villagerretaliation.item.VillagerAttributeFilterData;
import com.jvn.villagerretaliation.item.VillagerFilterPolicy;
import com.jvn.villagerretaliation.item.VillagerItemFilterData;
import com.jvn.villagerretaliation.item.VillagerRetaliationItems;
import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class ContainerTransferPolicyGameTests {
    private static final String EMPTY_TEMPLATE = "empty";

    private ContainerTransferPolicyGameTests() {
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void courierFiltersIntersectSourceAndDestinationTargets(GameTestHelper helper) {
        buildFloor(helper, 0, 8, 0, 4, 1);
        ServerLevel level = helper.getLevel();
        BlockPos source = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPos destination = helper.absolutePos(new BlockPos(6, 2, 2));
        setBlock(helper, new BlockPos(2, 2, 2), Blocks.CHEST.defaultBlockState());
        setBlock(helper, new BlockPos(6, 2, 2), Blocks.CHEST.defaultBlockState());
        Container sourceContainer = container(level, source);
        Container destinationContainer = container(level, destination);
        setItemCount(sourceContainer, Items.WHEAT, 300);
        setItemCount(destinationContainer, Items.WHEAT, 240);

        Villager villager = spawnVillager(helper, new BlockPos(4, 2, 2));
        ServerPlayer hirer = fakePlayer(level, "P7Both");
        ItemFrame sourceFrame = attachRule(
                level,
                source,
                configuredRule(
                        new ItemStack(Items.WHEAT),
                        VillagerFilterPolicy.TransferDirection.PROVIDE,
                        VillagerFilterPolicy.ListMode.ALLOW_MATCHING,
                        64),
                Direction.SOUTH);
        ItemFrame destinationFrame = attachRule(
                level,
                destination,
                configuredRule(
                        new ItemStack(Items.WHEAT),
                        VillagerFilterPolicy.TransferDirection.RECEIVE,
                        VillagerFilterPolicy.ListMode.ALLOW_MATCHING,
                        256),
                Direction.SOUTH);
        assignStorage(helper, hirer, villager, source, AssignedStorageService.SUPPLY_PURPOSE);
        assignStorage(helper, hirer, villager, destination, AssignedStorageService.OUTPUT_PURPOSE);

        SimpleContainer cargo = new SimpleContainer(9);
        AssignedStorageService.reconcileCourierClaims(villager, List.of());
        int moved = AssignedStorageService.transferCourierItemsAtAssignedStorage(
                villager,
                source,
                100,
                stack -> VillagerInventoryOverflowService.insertIntoContainer(cargo, stack));
        helper.assertValueEqual(moved, 16,
                "source Keep 64 and destination Fill To 256 should intersect at sixteen");
        helper.assertValueEqual(countItem(sourceContainer, Items.WHEAT), 284,
                "the source should lose exactly the accepted cargo");
        helper.assertValueEqual(countItem(cargo, Items.WHEAT), 16,
                "the courier should receive exactly the planned cargo");

        VillagerInventoryOverflowService.ContainerCandidate outputCandidate =
                VillagerInventoryOverflowService.ContainerCandidate.resolve(level, destination);
        helper.assertTrue(outputCandidate != null, "assigned output should resolve");
        helper.assertValueEqual(ContainerTransferClaimLedger.count(
                        level,
                        outputCandidate,
                        null,
                        VillagerFilterPolicy.TransferOperation.RECEIVE,
                        stack -> stack.is(Items.WHEAT)),
                16,
                "accepted cargo should reserve its destination target");

        ItemStack remainder = AssignedStorageService.depositStackAtAssignedStorage(
                villager,
                destination,
                new ItemStack(Items.WHEAT, 16));
        helper.assertTrue(remainder.isEmpty(), "claimed cargo should commit to its destination");
        helper.assertValueEqual(countItem(destinationContainer, Items.WHEAT), 256,
                "the destination should stop exactly at its Fill To target");
        helper.assertValueEqual(
                countItem(sourceContainer, Items.WHEAT) + countItem(destinationContainer, Items.WHEAT),
                540,
                "the committed transfer must neither duplicate nor lose items");

        AssignedStorageService.reconcileCourierClaims(villager, List.of());
        helper.assertValueEqual(ContainerTransferClaimLedger.activeClaimCount(), 0,
                "delivered cargo should release every runtime claim");
        cleanup(level, villager, List.of(sourceFrame, destinationFrame));
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void courierDirectionsAndMissingRulesPreserveCompatibility(GameTestHelper helper) {
        buildFloor(helper, 0, 8, 0, 14, 1);

        FixtureResult sourceOnly = runFixture(
                helper,
                "P7Source",
                2,
                configuredRule(
                        new ItemStack(Items.WHEAT),
                        VillagerFilterPolicy.TransferDirection.PROVIDE,
                        VillagerFilterPolicy.ListMode.ALLOW_MATCHING,
                        5),
                null,
                12,
                0,
                20);
        helper.assertValueEqual(sourceOnly.moved(), 7, "source-only Keep target");
        helper.assertValueEqual(sourceOnly.sourceRemaining(), 5, "source-only reserve");
        helper.assertValueEqual(sourceOnly.accepted(), 7, "source-only cargo");

        FixtureResult destinationOnly = runFixture(
                helper,
                "P7Dest",
                4,
                null,
                configuredRule(
                        new ItemStack(Items.WHEAT),
                        VillagerFilterPolicy.TransferDirection.RECEIVE,
                        VillagerFilterPolicy.ListMode.ALLOW_MATCHING,
                        5),
                12,
                2,
                20);
        helper.assertValueEqual(destinationOnly.moved(), 3, "destination-only Fill To target");
        helper.assertValueEqual(destinationOnly.sourceRemaining(), 9, "destination-only source");
        helper.assertValueEqual(destinationOnly.accepted(), 3, "destination-only cargo");

        FixtureResult neither = runFixture(helper, "P7None", 6, null, null, 8, 0, 20);
        helper.assertValueEqual(neither.moved(), 8, "unframed courier behavior should remain unchanged");
        helper.assertValueEqual(neither.sourceRemaining(), 0, "unframed source should drain normally");

        FixtureResult ignoredDirections = runFixture(
                helper,
                "P7Dirs",
                8,
                configuredRule(
                        new ItemStack(Items.WHEAT),
                        VillagerFilterPolicy.TransferDirection.RECEIVE,
                        VillagerFilterPolicy.ListMode.ALLOW_MATCHING,
                        1),
                configuredRule(
                        new ItemStack(Items.WHEAT),
                        VillagerFilterPolicy.TransferDirection.PROVIDE,
                        VillagerFilterPolicy.ListMode.ALLOW_MATCHING,
                        1),
                9,
                0,
                20);
        helper.assertValueEqual(ignoredDirections.moved(), 9,
                "Receive-only source and Provide-only destination rules must be ignored");

        FixtureResult denied = runFixture(
                helper,
                "P7Deny",
                10,
                configuredRule(
                        new ItemStack(Items.WHEAT),
                        VillagerFilterPolicy.TransferDirection.PROVIDE,
                        VillagerFilterPolicy.ListMode.DENY_MATCHING,
                        0),
                null,
                10,
                0,
                20);
        helper.assertValueEqual(denied.moved(), 0, "matching source deny should veto pickup");
        helper.assertValueEqual(denied.sourceRemaining(), 10, "denied source must remain untouched");

        ItemStack maintain = configuredRule(
                new ItemStack(Items.WHEAT),
                VillagerFilterPolicy.TransferDirection.BOTH,
                VillagerFilterPolicy.ListMode.ALLOW_MATCHING,
                8);
        FixtureResult bothMaintain = runFixture(
                helper,
                "P7Maintain",
                12,
                maintain,
                maintain.copy(),
                12,
                5,
                20);
        helper.assertValueEqual(bothMaintain.moved(), 3,
                "Both Maintain 8 should intersect source surplus with destination deficit");
        helper.assertValueEqual(bothMaintain.sourceRemaining(), 9,
                "overlapping targets should commit only the shared allowance");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void courierClaimsSerializeCompetitorsAndRollbackSafely(GameTestHelper helper) {
        buildFloor(helper, 0, 8, 0, 4, 1);
        ServerLevel level = helper.getLevel();
        BlockPos source = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPos destination = helper.absolutePos(new BlockPos(6, 2, 2));
        setBlock(helper, new BlockPos(2, 2, 2), Blocks.CHEST.defaultBlockState());
        setBlock(helper, new BlockPos(6, 2, 2), Blocks.CHEST.defaultBlockState());
        Container sourceContainer = container(level, source);
        setItemCount(sourceContainer, Items.WHEAT, 300);
        setItemCount(container(level, destination), Items.WHEAT, 240);

        ItemFrame sourceFrame = attachRule(
                level,
                source,
                configuredRule(
                        new ItemStack(Items.WHEAT),
                        VillagerFilterPolicy.TransferDirection.PROVIDE,
                        VillagerFilterPolicy.ListMode.ALLOW_MATCHING,
                        64),
                Direction.SOUTH);
        ItemFrame destinationFrame = attachRule(
                level,
                destination,
                configuredRule(
                        new ItemStack(Items.WHEAT),
                        VillagerFilterPolicy.TransferDirection.RECEIVE,
                        VillagerFilterPolicy.ListMode.ALLOW_MATCHING,
                        256),
                Direction.SOUTH);
        Villager first = spawnVillager(helper, new BlockPos(4, 2, 2));
        Villager second = spawnVillager(helper, new BlockPos(4, 2, 3));
        ServerPlayer hirer = fakePlayer(level, "P7Claims");
        assignStorage(helper, hirer, first, source, AssignedStorageService.SUPPLY_PURPOSE);
        assignStorage(helper, hirer, first, destination, AssignedStorageService.OUTPUT_PURPOSE);
        assignStorage(helper, hirer, second, source, AssignedStorageService.SUPPLY_PURPOSE);
        assignStorage(helper, hirer, second, destination, AssignedStorageService.OUTPUT_PURPOSE);

        AssignedStorageService.reconcileCourierClaims(first, List.of());
        AssignedStorageService.reconcileCourierClaims(second, List.of());
        helper.assertValueEqual(
                AssignedStorageService.reserveCourierTransferClaims(first, source, 100),
                16,
                "first courier should claim the open destination target");
        helper.assertValueEqual(
                AssignedStorageService.reserveCourierTransferClaims(second, source, 100),
                0,
                "second courier must observe the first courier's in-flight claim");

        VillagerInventoryOverflowService.ContainerCandidate inputCandidate =
                VillagerInventoryOverflowService.ContainerCandidate.resolve(level, source);
        VillagerInventoryOverflowService.ContainerCandidate outputCandidate =
                VillagerInventoryOverflowService.ContainerCandidate.resolve(level, destination);
        helper.assertTrue(inputCandidate != null && outputCandidate != null, "claim containers should resolve");
        helper.assertValueEqual(ContainerTransferClaimLedger.count(
                        level,
                        inputCandidate,
                        null,
                        VillagerFilterPolicy.TransferOperation.PROVIDE,
                        stack -> stack.is(Items.WHEAT)),
                16,
                "source outbound claims should reserve the same match group");
        helper.assertValueEqual(ContainerTransferClaimLedger.count(
                        level,
                        outputCandidate,
                        null,
                        VillagerFilterPolicy.TransferOperation.RECEIVE,
                        stack -> stack.is(Items.WHEAT)),
                16,
                "destination inbound claims should reserve the same match group");

        AssignedStorageService.releaseCourierClaims(first);
        helper.assertValueEqual(
                AssignedStorageService.reserveCourierTransferClaims(second, source, 100),
                16,
                "releasing the first courier should make the target claimable");
        AssignedStorageService.releaseCourierClaims(second);
        helper.assertValueEqual(ContainerTransferClaimLedger.activeClaimCount(), 0,
                "released competitors must leave no claims");

        int rolledBack = AssignedStorageService.transferCourierItemsAtAssignedStorage(
                first,
                source,
                100,
                ItemStack::copy);
        helper.assertValueEqual(rolledBack, 0, "a full receiver remainder should roll back");
        helper.assertValueEqual(countItem(sourceContainer, Items.WHEAT), 300,
                "failed insertion must restore every extracted item");

        int threw = AssignedStorageService.transferCourierItemsAtAssignedStorage(
                first,
                source,
                100,
                stack -> {
                    throw new IllegalStateException("test receiver failure");
                });
        helper.assertValueEqual(threw, 0, "receiver failure should not commit cargo");
        helper.assertValueEqual(countItem(sourceContainer, Items.WHEAT), 300,
                "receiver failure must restore the source");
        helper.assertValueEqual(countDropped(level, source, Items.WHEAT), 0,
                "rollback should restore in-place without dropping duplicates");

        int[] accepted = {0};
        int partial = AssignedStorageService.transferCourierItemsAtAssignedStorage(
                first,
                source,
                100,
                stack -> {
                    if (accepted[0] > 0) {
                        return stack.copy();
                    }
                    int take = Math.min(5, stack.getCount());
                    accepted[0] += take;
                    return stack.copyWithCount(stack.getCount() - take);
                });
        helper.assertValueEqual(partial, 5, "partial receiver acceptance should commit only its prefix");
        helper.assertValueEqual(countItem(sourceContainer, Items.WHEAT), 295,
                "partial acceptance should restore the unaccepted suffix");
        helper.assertValueEqual(countItem(sourceContainer, Items.WHEAT) + accepted[0], 300,
                "partial commit must conserve total items");

        cleanup(level, first, List.of());
        cleanup(level, second, List.of(sourceFrame, destinationFrame));
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void courierClaimsRemainPotionVariantAware(GameTestHelper helper) {
        buildFloor(helper, 0, 8, 0, 4, 1);
        ServerLevel level = helper.getLevel();
        BlockPos source = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPos destination = helper.absolutePos(new BlockPos(6, 2, 2));
        setBlock(helper, new BlockPos(2, 2, 2), Blocks.CHEST.defaultBlockState());
        setBlock(helper, new BlockPos(6, 2, 2), Blocks.CHEST.defaultBlockState());
        Container sourceContainer = container(level, source);
        ItemStack healing = PotionContents.createItemStack(Items.POTION, Potions.HEALING);
        ItemStack water = PotionContents.createItemStack(Items.POTION, Potions.WATER);
        for (int slot = 0; slot < 4; slot++) {
            sourceContainer.setItem(slot, healing.copy());
            sourceContainer.setItem(slot + 4, water.copy());
        }

        ItemFrame sourceFrame = attachRule(
                level,
                source,
                configuredRule(
                        healing,
                        VillagerFilterPolicy.TransferDirection.PROVIDE,
                        VillagerFilterPolicy.ListMode.ALLOW_MATCHING,
                        2),
                Direction.SOUTH);
        ItemFrame destinationFrame = attachRule(
                level,
                destination,
                configuredRule(
                        healing,
                        VillagerFilterPolicy.TransferDirection.RECEIVE,
                        VillagerFilterPolicy.ListMode.ALLOW_MATCHING,
                        2),
                Direction.SOUTH);
        Villager first = spawnVillager(helper, new BlockPos(4, 2, 2));
        Villager second = spawnVillager(helper, new BlockPos(4, 2, 3));
        ServerPlayer hirer = fakePlayer(level, "P7Variants");
        assignStorage(helper, hirer, first, source, AssignedStorageService.SUPPLY_PURPOSE);
        assignStorage(helper, hirer, first, destination, AssignedStorageService.OUTPUT_PURPOSE);
        assignStorage(helper, hirer, second, source, AssignedStorageService.SUPPLY_PURPOSE);
        assignStorage(helper, hirer, second, destination, AssignedStorageService.OUTPUT_PURPOSE);
        helper.assertValueEqual(
                AssignedStorageService.assignedOutputCapacityFor(first, healing, 8),
                2,
                "healing destination should expose its component-aware deficit");
        helper.assertTrue(
                AssignedStorageService.assignedCourierInputStoragePositionsContaining(
                                level,
                                first,
                                stack -> ItemStack.isSameItemSameComponents(stack, healing))
                        .contains(source),
                "healing source should expose its component-aware surplus");

        AssignedStorageService.reconcileCourierClaims(first, List.of());
        AssignedStorageService.reconcileCourierClaims(second, List.of());
        helper.assertValueEqual(
                AssignedStorageService.reserveCourierTransferClaims(first, source, 8),
                2,
                "healing potion surplus should produce two claims");
        helper.assertValueEqual(
                AssignedStorageService.reserveCourierTransferClaims(second, source, 8),
                0,
                "matching potion claims should serialize competing couriers");

        VillagerInventoryOverflowService.ContainerCandidate outputCandidate =
                VillagerInventoryOverflowService.ContainerCandidate.resolve(level, destination);
        helper.assertTrue(outputCandidate != null, "variant-aware output should resolve");
        helper.assertValueEqual(ContainerTransferClaimLedger.count(
                        level,
                        outputCandidate,
                        null,
                        VillagerFilterPolicy.TransferOperation.RECEIVE,
                        stack -> ItemStack.isSameItemSameComponents(stack, healing)),
                2,
                "healing claims should retain potion components");
        helper.assertValueEqual(ContainerTransferClaimLedger.count(
                        level,
                        outputCandidate,
                        null,
                        VillagerFilterPolicy.TransferOperation.RECEIVE,
                        stack -> ItemStack.isSameItemSameComponents(stack, water)),
                0,
                "water bottles must not count toward healing claims");

        cleanup(level, first, List.of());
        cleanup(level, second, List.of(sourceFrame, destinationFrame));
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void legacySavedFiltersAndOrdinaryFramesRemainCourierCompatible(GameTestHelper helper) {
        buildFloor(helper, 0, 8, 0, 4, 1);
        ServerLevel level = helper.getLevel();
        BlockPos source = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPos destination = helper.absolutePos(new BlockPos(6, 2, 2));
        setBlock(helper, new BlockPos(2, 2, 2), Blocks.CHEST.defaultBlockState());
        setBlock(helper, new BlockPos(6, 2, 2), Blocks.CHEST.defaultBlockState());
        Container sourceContainer = container(level, source);
        Container destinationContainer = container(level, destination);
        setItemCount(sourceContainer, Items.EMERALD, 5);
        sourceContainer.setItem(1, new ItemStack(Items.DIRT, 3));
        destinationContainer.setItem(0, new ItemStack(Items.EMERALD, 30));

        ItemStack legacyFilter = legacySavedRule(new ItemStack(Items.EMERALD), 32);
        ItemFrame frame = attachRule(level, destination, legacyFilter, Direction.SOUTH);
        Villager villager = spawnVillager(helper, new BlockPos(4, 2, 2));
        ServerPlayer hirer = fakePlayer(level, "P8Legacy");
        assignStorage(helper, hirer, villager, source, AssignedStorageService.SUPPLY_PURPOSE);
        assignStorage(helper, hirer, villager, destination, AssignedStorageService.OUTPUT_PURPOSE);

        SimpleContainer legacyCargo = new SimpleContainer(9);
        int legacyMoved = AssignedStorageService.transferCourierItemsAtAssignedStorage(
                villager,
                source,
                10,
                stack -> VillagerInventoryOverflowService.insertIntoContainer(legacyCargo, stack));
        helper.assertValueEqual(legacyMoved, 2,
                "legacy saved amount filters should retain their receive cap in the courier path");
        helper.assertValueEqual(countItem(sourceContainer, Items.EMERALD), 3,
                "legacy filtering should extract only remaining destination demand");
        helper.assertValueEqual(countItem(sourceContainer, Items.DIRT), 3,
                "legacy allowlists should keep rejecting other source identities");
        helper.assertTrue(AssignedStorageService.depositStackAtAssignedStorage(
                        villager, destination, legacyCargo.getItem(0).copy()).isEmpty(),
                "the legacy-filtered destination should accept its reserved demand");
        helper.assertValueEqual(countItem(destinationContainer, Items.EMERALD), 32,
                "legacy destination stock should stop at its stored entry amount");

        legacyCargo.clearContent();
        AssignedStorageService.reconcileCourierClaims(villager, List.of());
        sourceContainer.setItem(2, new ItemStack(Items.DIAMOND, 3));
        frame.setItem(new ItemStack(Items.DIAMOND));
        ContainerFilterResolver.invalidateFrame(level, frame);
        SimpleContainer ordinaryCargo = new SimpleContainer(9);
        int ordinaryMoved = AssignedStorageService.transferCourierItemsAtAssignedStorage(
                villager,
                source,
                10,
                stack -> VillagerInventoryOverflowService.insertIntoContainer(ordinaryCargo, stack));
        helper.assertValueEqual(ordinaryMoved, 3,
                "ordinary item frames should remain exact-item courier routes");
        helper.assertValueEqual(countItem(ordinaryCargo, Items.DIAMOND), 3,
                "ordinary framed identities should reach courier cargo unchanged");
        helper.assertValueEqual(countItem(ordinaryCargo, Items.DIRT), 0,
                "ordinary item frames should reject non-matching cargo");
        helper.assertValueEqual(
                AssignedStorageService.assignedOutputCapacityFor(
                        villager, new ItemStack(Items.DIRT), 1),
                0,
                "ordinary framed routes should expose no capacity for other identities");

        cleanup(level, villager, List.of(frame));
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void attributeClaimsSpanDamagedSwordVariants(GameTestHelper helper) {
        buildFloor(helper, 0, 10, 0, 4, 1);
        ServerLevel level = helper.getLevel();
        BlockPos sourceLeftRelative = new BlockPos(2, 2, 2);
        BlockPos sourceRightRelative = sourceLeftRelative.east();
        BlockPos destinationRelative = new BlockPos(8, 2, 2);
        BlockPos sourceLeft = helper.absolutePos(sourceLeftRelative);
        BlockPos sourceRight = helper.absolutePos(sourceRightRelative);
        BlockPos destination = helper.absolutePos(destinationRelative);
        setDoubleChest(helper, sourceLeftRelative, sourceRightRelative);
        setBlock(helper, destinationRelative, Blocks.CHEST.defaultBlockState());
        Container leftContainer = container(level, sourceLeft);
        Container rightContainer = container(level, sourceRight);
        for (int index = 0; index < 40; index++) {
            ItemStack sword = new ItemStack(Items.IRON_SWORD);
            sword.setDamageValue(index + 1);
            if (index < 27) {
                leftContainer.setItem(index, sword);
            } else {
                rightContainer.setItem(index - 27, sword);
            }
        }
        helper.assertFalse(
                ItemStack.isSameItemSameComponents(leftContainer.getItem(0), rightContainer.getItem(12)),
                "the reserve fixture should contain distinct damaged variants");

        ItemStack sourceRule = new ItemStack(VillagerRetaliationItems.ATTRIBUTE_FILTER.get());
        helper.assertTrue(VillagerAttributeFilterData.setSelected(
                        sourceRule,
                        new VillagerAttributeFilterData.Attribute(
                                VillagerAttributeFilterData.AttributeType.IN_TAG,
                                "minecraft:swords"),
                        false),
                "the sword attribute rule should be configured");
        VillagerFilterPolicy.setPolicy(
                sourceRule,
                VillagerFilterPolicy.TransferDirection.PROVIDE,
                VillagerFilterPolicy.ListMode.ALLOW_MATCHING,
                VillagerFilterPolicy.CombinationMode.MATCH_ANY,
                OptionalInt.of(32));
        ItemFrame sourceFrame = attachRule(level, sourceLeft, sourceRule, Direction.SOUTH);

        Villager first = spawnVillager(helper, new BlockPos(5, 2, 2));
        Villager second = spawnVillager(helper, new BlockPos(5, 2, 3));
        ServerPlayer hirer = fakePlayer(level, "P8Variants");
        assignStorage(helper, hirer, first, sourceLeft, AssignedStorageService.SUPPLY_PURPOSE);
        assignStorage(helper, hirer, first, destination, AssignedStorageService.OUTPUT_PURPOSE);
        assignStorage(helper, hirer, second, sourceLeft, AssignedStorageService.SUPPLY_PURPOSE);
        assignStorage(helper, hirer, second, destination, AssignedStorageService.OUTPUT_PURPOSE);
        AssignedStorageService.reconcileCourierClaims(first, List.of());
        AssignedStorageService.reconcileCourierClaims(second, List.of());

        helper.assertValueEqual(
                AssignedStorageService.reserveCourierTransferClaims(first, sourceLeft, 8),
                8,
                "forty matching sword variants should expose eight above Keep 32");
        helper.assertValueEqual(
                AssignedStorageService.reserveCourierTransferClaims(second, sourceLeft, 8),
                0,
                "outbound claims must reserve the complete attribute match group");
        VillagerInventoryOverflowService.ContainerCandidate sourceCandidate =
                VillagerInventoryOverflowService.ContainerCandidate.resolve(level, sourceLeft);
        helper.assertTrue(sourceCandidate != null, "variant source double chest should resolve");
        helper.assertValueEqual(ContainerTransferClaimLedger.count(
                        level,
                        sourceCandidate,
                        null,
                        VillagerFilterPolicy.TransferOperation.PROVIDE,
                        stack -> stack.is(Items.IRON_SWORD)),
                8,
                "all claimed sword variants should count against one Keep target");
        Map<BlockPos, List<ItemStack>> claims = ContainerTransferClaimLedger.snapshot(
                level, first.getUUID(), VillagerFilterPolicy.TransferOperation.PROVIDE);
        List<ItemStack> claimedVariants = claims.getOrDefault(sourceCandidate.pos(), List.of());
        helper.assertValueEqual(claimedVariants.size(), 8,
                "component-distinct claimed variants should remain individually represented");
        helper.assertValueEqual(
                (int) claimedVariants.stream().mapToInt(ItemStack::getDamageValue).distinct().count(),
                8,
                "outbound claims should preserve each damaged variant identity");
        helper.assertValueEqual(
                countItem(leftContainer, Items.IRON_SWORD) + countItem(rightContainer, Items.IRON_SWORD),
                40,
                "claiming variants must not mutate source inventory");

        cleanup(level, first, List.of());
        cleanup(level, second, List.of(sourceFrame));
        helper.succeed();
    }

    private static FixtureResult runFixture(
            GameTestHelper helper,
            String name,
            int z,
            ItemStack sourceRule,
            ItemStack destinationRule,
            int sourceCount,
            int destinationCount,
            int maximum) {
        ServerLevel level = helper.getLevel();
        BlockPos sourceRelative = new BlockPos(2, 2, z);
        BlockPos destinationRelative = new BlockPos(6, 2, z);
        BlockPos source = helper.absolutePos(sourceRelative);
        BlockPos destination = helper.absolutePos(destinationRelative);
        setBlock(helper, sourceRelative, Blocks.CHEST.defaultBlockState());
        setBlock(helper, destinationRelative, Blocks.CHEST.defaultBlockState());
        Container sourceContainer = container(level, source);
        setItemCount(sourceContainer, Items.WHEAT, sourceCount);
        setItemCount(container(level, destination), Items.WHEAT, destinationCount);
        Villager villager = spawnVillager(helper, new BlockPos(4, 2, z));
        ServerPlayer hirer = fakePlayer(level, name);
        List<ItemFrame> frames = new ArrayList<>();
        if (sourceRule != null && !sourceRule.isEmpty()) {
            frames.add(attachRule(level, source, sourceRule, Direction.SOUTH));
        }
        if (destinationRule != null && !destinationRule.isEmpty()) {
            frames.add(attachRule(level, destination, destinationRule, Direction.SOUTH));
        }
        assignStorage(helper, hirer, villager, source, AssignedStorageService.SUPPLY_PURPOSE);
        assignStorage(helper, hirer, villager, destination, AssignedStorageService.OUTPUT_PURPOSE);

        int[] accepted = {0};
        AssignedStorageService.reconcileCourierClaims(villager, List.of());
        int moved = AssignedStorageService.transferCourierItemsAtAssignedStorage(
                villager,
                source,
                maximum,
                stack -> {
                    accepted[0] += stack.getCount();
                    return ItemStack.EMPTY;
                });
        FixtureResult result = new FixtureResult(
                moved,
                countItem(sourceContainer, Items.WHEAT),
                accepted[0]);
        cleanup(level, villager, frames);
        return result;
    }

    private static ItemStack legacySavedRule(ItemStack entry, int amount) {
        ItemStack filter = new ItemStack(VillagerRetaliationItems.ITEM_FILTER.get());
        VillagerItemFilterData.setEntry(filter, 0, entry.copyWithCount(1));
        VillagerItemFilterData.setAmount(filter, 0, amount);
        CompoundTag customData = filter.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        customData.getCompound("villagerretaliation:item_filter").remove("EntryCombination");
        filter.set(DataComponents.CUSTOM_DATA, CustomData.of(customData));
        return filter;
    }

    private static ItemStack configuredRule(
            ItemStack entry,
            VillagerFilterPolicy.TransferDirection direction,
            VillagerFilterPolicy.ListMode mode,
            int target) {
        ItemStack filter = new ItemStack(VillagerRetaliationItems.ITEM_FILTER.get());
        VillagerItemFilterData.setEntry(filter, 0, entry.copyWithCount(1));
        VillagerFilterPolicy.setPolicy(
                filter,
                direction,
                mode,
                VillagerFilterPolicy.CombinationMode.MATCH_ANY,
                target > 0 ? OptionalInt.of(target) : OptionalInt.empty());
        return filter;
    }

    private static ItemFrame attachRule(
            ServerLevel level,
            BlockPos container,
            ItemStack rule,
            Direction direction) {
        ItemFrame frame = new ItemFrame(level, container.relative(direction), direction);
        frame.setItem(rule);
        if (!level.addFreshEntity(frame)) {
            throw new GameTestAssertException("Could not add framed rule");
        }
        ContainerFilterResolver.invalidateFrame(level, frame);
        return frame;
    }

    private static void assignStorage(
            GameTestHelper helper,
            ServerPlayer hirer,
            Villager villager,
            BlockPos pos,
            String purpose) {
        helper.assertValueEqual(AssignedStorageService.assign(
                        hirer,
                        villager,
                        List.of(new AssignedStorageService.StoragePosition(helper.getLevel().dimension(), pos)),
                        purpose).assigned(),
                1,
                "assigned " + purpose + " storage");
    }

    private static void cleanup(ServerLevel level, Villager villager, List<ItemFrame> frames) {
        AssignedStorageService.releaseCourierClaims(villager);
        AssignedStorageService.removeAllAssignedStorage(level, villager);
        for (ItemFrame frame : frames) {
            frame.discard();
        }
        villager.discard();
        ContainerFilterResolver.clearRuntimeState();
    }

    private static void setItemCount(Container container, Item item, int count) {
        container.clearContent();
        int remaining = count;
        for (int slot = 0; slot < container.getContainerSize() && remaining > 0; slot++) {
            int stackSize = Math.min(item.getDefaultMaxStackSize(), remaining);
            container.setItem(slot, new ItemStack(item, stackSize));
            remaining -= stackSize;
        }
        if (remaining > 0) {
            throw new GameTestAssertException("Container could not hold test stock");
        }
    }

    private static int countItem(Container container, Item item) {
        int count = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static int countDropped(ServerLevel level, BlockPos center, Item item) {
        int count = 0;
        for (ItemEntity entity : level.getEntitiesOfClass(
                ItemEntity.class,
                new AABB(center).inflate(4.0D))) {
            if (entity.getItem().is(item)) {
                count += entity.getItem().getCount();
            }
        }
        return count;
    }

    private static Container container(ServerLevel level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof Container container) {
            return container;
        }
        throw new GameTestAssertException("Expected container at " + pos);
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

    private static void setDoubleChest(
            GameTestHelper helper,
            BlockPos leftRelative,
            BlockPos rightRelative) {
        ServerLevel level = helper.getLevel();
        level.setBlock(
                helper.absolutePos(leftRelative),
                Blocks.CHEST.defaultBlockState()
                        .setValue(ChestBlock.FACING, Direction.NORTH)
                        .setValue(ChestBlock.TYPE, ChestType.LEFT),
                Block.UPDATE_CLIENTS);
        level.setBlock(
                helper.absolutePos(rightRelative),
                Blocks.CHEST.defaultBlockState()
                        .setValue(ChestBlock.FACING, Direction.NORTH)
                        .setValue(ChestBlock.TYPE, ChestType.RIGHT),
                Block.UPDATE_CLIENTS);
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
            throw new GameTestAssertException("Could not add villager");
        }
        level.tickNonPassenger(villager);
        return villager;
    }

    private static ServerPlayer fakePlayer(ServerLevel level, String name) {
        UUID id = UUID.nameUUIDFromBytes(
                ("villagerretaliation:" + name).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        ServerPlayer player = FakePlayerFactory.get(level, new GameProfile(id, name));
        BlockPos spawn = level.getSharedSpawnPos();
        player.moveTo(spawn.getX() + 0.5D, spawn.getY() + 1.0D, spawn.getZ() + 0.5D, 0.0F, 0.0F);
        return player;
    }

    private record FixtureResult(int moved, int sourceRemaining, int accepted) {
    }
}
