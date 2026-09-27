package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.block.SettlementDepotBlockEntity;
import com.jvn.villagerretaliation.block.VillagerRetaliationBlocks;
import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class SettlementSharedBuildingGameTests {
    private SettlementSharedBuildingGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_shared_buildings")
    public static void oneBuildingRetainsHousingAndSeparateWorkplacesAcrossReload(GameTestHelper helper) {
        var fixture = fixture(helper);
        var level = helper.getLevel();
        var center = fixture.town().center();
        for (var pos : BlockPos.betweenClosed(center.below(), center.offset(3, -1, 3))) {
            level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
        }
        var bed = Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH);
        level.setBlock(center.offset(1, 0, 1), bed.setValue(BedBlock.PART, BedPart.HEAD), 2);
        level.setBlock(center.offset(1, 0, 2), bed.setValue(BedBlock.PART, BedPart.FOOT), 2);
        level.setBlockAndUpdate(center.offset(3, 0, 1), Blocks.CRAFTING_TABLE.defaultBlockState());
        level.setBlockAndUpdate(center.offset(3, 0, 2), Blocks.FURNACE.defaultBlockState());
        var home = register(helper, fixture, SettlementAreaType.HOME, 5);
        var workshop = register(helper, fixture, SettlementAreaType.WORKSHOP, 5);
        var kitchen = register(helper, fixture, SettlementAreaType.KITCHEN, 5);
        var smeltery = register(helper, fixture, SettlementAreaType.SMELTERY, 5);
        register(helper, fixture, SettlementAreaType.DEPOT, 5);
        register(helper, fixture, SettlementAreaType.CONSTRUCTION, 5);
        helper.assertValueEqual(fixture.town().housingCapacity(), 1, "the shared building counts its usable bed once");
        helper.assertTrue(fixture.town().surveys().get(workshop.id()).fixtures().containsKey(center.offset(3, 0, 1)),
                "the workshop sees its real crafting table");
        helper.assertTrue(fixture.town().surveys().get(kitchen.id()).fixtures().containsKey(center.offset(3, 0, 2))
                && fixture.town().surveys().get(smeltery.id()).fixtures().containsKey(center.offset(3, 0, 2)),
                "the kitchen and smeltery see the same physical furnace");
        var restored = SettlementRecord.load(fixture.town().save(level.registryAccess()), level.registryAccess(), () -> {});
        helper.assertValueEqual(restored.areas(), fixture.town().areas(), "every distinct designation survives a save");
        helper.assertValueEqual(restored.housingCapacity(), 1, "saving does not duplicate housing");
        helper.assertTrue(restored.removeArea(restored.leader(), workshop.id()), "one use can be removed independently");
        helper.assertTrue(restored.areas().containsKey(home.id()) && restored.areas().containsKey(kitchen.id()),
                "removing the workshop preserves the other building uses");
        helper.assertValueEqual(restored.housingCapacity(), 1, "removing a workplace preserves the home's capacity");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_shared_buildings")
    public static void sharedUsesDoNotPermitDuplicateHousingOrBypassConstructionAuthority(GameTestHelper helper) {
        var fixture = fixture(helper);
        var home = register(helper, fixture, SettlementAreaType.HOME, 5);
        var viewer = player(helper, fixture.town().center());
        fixture.town().setPermissions(fixture.town().leader(), viewer.getUUID(), Set.of(SettlementPermission.VIEW));
        var workshop = area(fixture, SettlementAreaType.WORKSHOP, 5);
        helper.assertFalse(SettlementBuildingService.register(viewer, fixture.town().id(), workshop),
                "sharing an existing building still requires construction authority");
        helper.assertFalse(SettlementBuildingService.register(fixture.leader(), fixture.town().id(),
                area(fixture, SettlementAreaType.HOME, 5)), "a second designation cannot count the same home again");
        helper.assertTrue(SettlementBuildingService.register(fixture.leader(), fixture.town().id(), workshop),
                "the authorized leader can add a separate use");
        helper.assertFalse(fixture.town().putArea(fixture.town().leader(), new SettlementArea(workshop.id(), home.type(),
                workshop.name(), workshop.min(), workshop.max(), 5, true)), "editing a workplace cannot bypass duplicate housing checks");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_shared_buildings")
    public static void anotherSettlementCannotClaimASharedBuilding(GameTestHelper helper) {
        var fixture = fixture(helper);
        register(helper, fixture, SettlementAreaType.HOME, 5);
        register(helper, fixture, SettlementAreaType.WORKSHOP, 5);
        var level = helper.getLevel();
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var center = fixture.town().center().above(48);
        var id = registry.create(0L, level.dimension().location(), center, "");
        var other = SettlementSavedData.get(level).establish(registry, id, UUID.randomUUID(), center, 0L).orElseThrow();
        helper.assertFalse(SettlementSavedData.get(level).putArea(registry, id, other.leader(),
                area(fixture, SettlementAreaType.KITCHEN, 5)), "different uses cannot cross settlement ownership");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_shared_buildings")
    public static void competingTerrainUsesRemainExclusiveWhileTheirBuildingsCanBeShared(GameTestHelper helper) {
        var fixture = fixture(helper);
        register(helper, fixture, SettlementAreaType.FARM, 5);
        register(helper, fixture, SettlementAreaType.HOME, 5);
        register(helper, fixture, SettlementAreaType.DEPOT, 5);
        for (var type : List.of(SettlementAreaType.ANIMAL_PEN, SettlementAreaType.FORESTRY,
                SettlementAreaType.MINE, SettlementAreaType.CONSTRUCTION)) {
            helper.assertFalse(SettlementBuildingService.register(fixture.leader(), fixture.town().id(), area(fixture, type, 5)),
                    "a second terrain ledger cannot own the farm's resources: " + type);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_shared_buildings")
    public static void sharedDepotChoosesItsStorageDesignationAndKeepsOneInventory(GameTestHelper helper) {
        var fixture = fixture(helper);
        var home = register(helper, fixture, SettlementAreaType.HOME, 9);
        var workshop = register(helper, fixture, SettlementAreaType.WORKSHOP, 9);
        var storage = register(helper, fixture, SettlementAreaType.DEPOT, 0);
        var pos = fixture.town().center().offset(2, 0, 2);
        helper.getLevel().setBlockAndUpdate(pos, VillagerRetaliationBlocks.SETTLEMENT_DEPOT.get().defaultBlockState());
        helper.assertTrue(SettlementDepotService.register(fixture.leader(), pos), "the home cannot conceal the storage designation");
        var block = (SettlementDepotBlockEntity) helper.getLevel().getBlockEntity(pos);
        var depot = block.inventory();
        helper.assertValueEqual(depot.area(), storage.id(), "the explicit depot designation takes precedence over another workplace");
        block.setItem(0, new ItemStack(Items.BREAD, 12));
        helper.assertTrue(SettlementDepotService.register(fixture.leader(), pos), "repeated registration restores the same binding");
        helper.assertValueEqual(fixture.town().depots().size(), 1, "the physical depot has only one inventory");
        helper.assertTrue(fixture.town().registerDepot(fixture.town().leader(), workshop.id(), pos,
                SettlementDepotPurpose.GENERAL) == null, "another shared designation cannot duplicate its stock");
        helper.assertTrue(fixture.town().removeArea(fixture.town().leader(), home.id()), "the unrelated home can be removed");
        helper.assertFalse(fixture.town().removeArea(fixture.town().leader(), storage.id()), "the owning designation still protects its stock");
        depot.withdraw(stack -> stack.is(Items.BREAD), 5);
        helper.assertValueEqual(block.getItem(0).getCount(), 7, "background consumption reaches the same block inventory");
        var restored = SettlementRecord.load(fixture.town().save(helper.getLevel().registryAccess()),
                helper.getLevel().registryAccess(), () -> {});
        helper.assertValueEqual(restored.depots().get(depot.id()).getItem(0).getCount(), 7, "saving preserves the remaining finite stock");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_shared_buildings")
    public static void aHomeWorkshopCanRegisterStorageWithoutADepotDesignation(GameTestHelper helper) {
        var fixture = fixture(helper);
        register(helper, fixture, SettlementAreaType.HOME, 9);
        var workshop = register(helper, fixture, SettlementAreaType.WORKSHOP, 5);
        var pos = fixture.town().center().east();
        helper.getLevel().setBlockAndUpdate(pos, VillagerRetaliationBlocks.SETTLEMENT_DEPOT.get().defaultBlockState());
        var viewer = player(helper, pos);
        fixture.town().setPermissions(fixture.town().leader(), viewer.getUUID(), Set.of(SettlementPermission.BUILD));
        helper.assertFalse(SettlementDepotService.register(viewer, pos), "construction authority does not grant logistics access");
        helper.assertTrue(SettlementDepotService.register(fixture.leader(), pos), "a workplace can still own storage inside a shared home");
        var block = (SettlementDepotBlockEntity) helper.getLevel().getBlockEntity(pos);
        helper.assertValueEqual(block.inventory().area(), workshop.id(), "the eligible workplace receives the binding");
        helper.succeed();
    }

    private static SettlementArea register(GameTestHelper helper, Fixture fixture, SettlementAreaType type, int priority) {
        var area = area(fixture, type, priority);
        helper.assertTrue(SettlementBuildingService.register(fixture.leader(), fixture.town().id(), area),
                "the building accepts its " + type + " designation");
        return area;
    }

    private static SettlementArea area(Fixture fixture, SettlementAreaType type, int priority) {
        return new SettlementArea(UUID.randomUUID(), type, type.name(), fixture.town().center(),
                fixture.town().center().offset(3, 3, 3), priority, true);
    }

    private static Fixture fixture(GameTestHelper helper) {
        var level = helper.getLevel();
        var center = helper.absolutePos(new BlockPos(2, 2, 2));
        var leader = player(helper, center);
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var id = registry.create(0L, level.dimension().location(), center, "");
        var town = SettlementSavedData.get(level).establish(registry, id, leader.getUUID(), center, 0L).orElseThrow();
        return new Fixture(town, leader);
    }

    private static ServerPlayer player(GameTestHelper helper, BlockPos position) {
        var player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "SharedBuilder"));
        player.setPos(position.getBottomCenter());
        return player;
    }

    private record Fixture(SettlementRecord town, ServerPlayer leader) {
    }
}
