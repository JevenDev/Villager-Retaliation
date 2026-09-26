package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.allegiance.VillageLifecycleState;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class SettlementAuthorityGameTests {
    private SettlementAuthorityGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void authorityAndAreasSurviveSaveReload(GameTestHelper helper) {
        var registry = new VillageAllegianceRegistrySavedData();
        var data = new SettlementSavedData();
        var id = registry.create(1L, Level.OVERWORLD.location(), BlockPos.ZERO, "Persistent Town");
        UUID leader = UUID.randomUUID();
        UUID builder = UUID.randomUUID();
        var town = data.establish(registry, id, leader, BlockPos.ZERO, 10L).orElseThrow();
        helper.assertTrue(town.setPermissions(leader, builder, Set.of(SettlementPermission.BUILD)),
                "the leader can delegate construction");
        var home = area(SettlementAreaType.HOME, new BlockPos(2, 1, 2), new BlockPos(8, 5, 8));
        helper.assertTrue(data.putArea(registry, id, builder, home), "a builder can register a home");
        var restored = SettlementSavedData.load(data.save(new CompoundTag(), helper.getLevel().registryAccess()),
                helper.getLevel().registryAccess());
        var loaded = restored.get(id).orElseThrow();
        helper.assertValueEqual(loaded.leader(), leader, "leadership persists");
        helper.assertValueEqual(loaded.foundedAt(), 10L, "founding time persists");
        helper.assertValueEqual(loaded.areas().get(home.id()), home, "registered bounds persist");
        helper.assertValueEqual(loaded.revision(), town.revision(), "revision persists");
        helper.assertTrue(loaded.permits(builder, SettlementPermission.BUILD), "delegated construction persists");
        helper.assertTrue(loaded.permits(builder, SettlementPermission.VIEW), "a delegate can inspect the town");
        helper.assertFalse(loaded.permits(builder, SettlementPermission.TREASURY), "construction grants no money access");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void delegatesCannotEscalateOrTransferLeadership(GameTestHelper helper) {
        var registry = new VillageAllegianceRegistrySavedData();
        var data = new SettlementSavedData();
        UUID leader = UUID.randomUUID();
        UUID delegate = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();
        var id = registry.create(0L, Level.OVERWORLD.location(), BlockPos.ZERO, "");
        var town = data.establish(registry, id, leader, BlockPos.ZERO, 0L).orElseThrow();
        town.setPermissions(leader, delegate, Set.of(SettlementPermission.STAFF));
        long revision = town.revision();
        helper.assertFalse(town.setPermissions(delegate, stranger, Set.of(SettlementPermission.TREASURY)),
                "a staffing delegate cannot grant treasury access");
        helper.assertFalse(town.transferLeadership(delegate, stranger), "only the leader can transfer leadership");
        helper.assertFalse(data.putArea(registry, id, delegate,
                area(SettlementAreaType.HOME, BlockPos.ZERO, new BlockPos(2, 2, 2))),
                "staffing authority cannot register buildings");
        helper.assertValueEqual(town.revision(), revision, "rejected requests leave state unchanged");
        helper.assertTrue(town.transferLeadership(leader, stranger), "the leader can explicitly transfer the town");
        helper.assertFalse(town.permits(leader, SettlementPermission.TREASURY), "former leadership is not retained");
        helper.assertTrue(town.permits(stranger, SettlementPermission.TREASURY), "the new leader has authority");
        helper.assertTrue(town.permits(delegate, SettlementPermission.STAFF), "existing delegation survives transfer");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void neighboringAreasCannotOverlapOrExceedExpansion(GameTestHelper helper) {
        var registry = new VillageAllegianceRegistrySavedData();
        var data = new SettlementSavedData();
        UUID leader = UUID.randomUUID();
        var left = registry.create(0L, Level.OVERWORLD.location(), BlockPos.ZERO, "");
        var right = registry.create(0L, Level.OVERWORLD.location(), new BlockPos(64, 0, 0), "");
        data.establish(registry, left, leader, BlockPos.ZERO, 0L).orElseThrow();
        data.establish(registry, right, leader, new BlockPos(64, 0, 0), 0L).orElseThrow();
        helper.assertTrue(data.putArea(registry, left, leader,
                area(SettlementAreaType.WORKSHOP, new BlockPos(30, 0, 0), new BlockPos(40, 4, 8))),
                "a workshop can be registered inside expansion bounds");
        helper.assertFalse(data.putArea(registry, right, leader,
                area(SettlementAreaType.HOME, new BlockPos(35, 0, 0), new BlockPos(45, 4, 8))),
                "two towns cannot claim the same functional space");
        helper.assertTrue(data.putArea(registry, left, leader,
                area(SettlementAreaType.HOME, new BlockPos(30, 5, 0), new BlockPos(40, 9, 8))),
                "housing above a workshop is a separate valid area");
        helper.assertFalse(data.putArea(registry, left, leader,
                area(SettlementAreaType.FARM, new BlockPos(65, 0, 20), new BlockPos(70, 0, 25))),
                "the full area must be inside the current expansion limit");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void managedIdentityCannotMergeArchiveOrLoseResidents(GameTestHelper helper) {
        var registry = new VillageAllegianceRegistrySavedData();
        var data = new SettlementSavedData();
        var managed = registry.create(0L, Level.OVERWORLD.location(), BlockPos.ZERO, "Managed");
        var neighbor = registry.create(0L, Level.OVERWORLD.location(), new BlockPos(32, 0, 0), "Neighbor");
        UUID resident = UUID.randomUUID();
        registry.addOrUpdateResident(managed, resident, true, 0L);
        data.establish(registry, managed, UUID.randomUUID(), BlockPos.ZERO, 0L).orElseThrow();
        registry.observeEmpty(managed, VillageAllegianceRegistrySavedData.ARCHIVE_GRACE_TICKS);
        helper.assertValueEqual(registry.record(managed).orElseThrow().lifecycleState(), VillageLifecycleState.ACTIVE,
                "an empty managed town keeps its identity");
        helper.assertFalse(registry.archive(managed), "automatic archival cannot erase a managed town");
        helper.assertFalse(registry.merge(managed, neighbor), "managed towns cannot be absorbed");
        helper.assertFalse(registry.merge(neighbor, managed), "managed towns cannot absorb neighbors automatically");
        helper.assertValueEqual(registry.residentRoster(managed, 100_000L).size(), 1,
                "unloaded residents do not expire from the managed census");
        var restored = VillageAllegianceRegistrySavedData.load(
                registry.save(new CompoundTag(), helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
        helper.assertTrue(restored.isManaged(managed), "management protection survives reload");
        helper.assertFalse(restored.merge(managed, neighbor), "reload cannot remove merge protection");
        helper.assertValueEqual(restored.residentRoster(managed, 100_000L).getFirst().id(), resident,
                "the durable resident identity survives reload");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void enrollmentPreservesCanonicalIdentityAndRejectsTakeover(GameTestHelper helper) {
        var registry = new VillageAllegianceRegistrySavedData();
        var data = new SettlementSavedData();
        var old = registry.create(0L, Level.OVERWORLD.location(), BlockPos.ZERO, "Old Home");
        var current = registry.create(1L, Level.OVERWORLD.location(), new BlockPos(32, 0, 0), "Current Home");
        registry.merge(old, current);
        UUID leader = UUID.randomUUID();
        var town = data.establish(registry, old, leader, new BlockPos(32, 0, 0), 2L).orElseThrow();
        helper.assertValueEqual(town.id(), current, "enrollment follows the existing canonical identity");
        helper.assertValueEqual(registry.record(current).orElseThrow().displayName(), "Current Home",
                "enrollment preserves the village name");
        helper.assertTrue(data.establish(registry, old, UUID.randomUUID(), new BlockPos(32, 0, 0), 3L).isEmpty(),
                "a second player cannot enroll an already managed identity");
        helper.assertFalse(registry.undoMerge(old), "historical merge reversal cannot split a managed town");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void registeredFootprintReplacesAutomaticExpansion(GameTestHelper helper) {
        var registry = new VillageAllegianceRegistrySavedData();
        var data = new SettlementSavedData();
        UUID leader = UUID.randomUUID();
        var id = registry.create(0L, Level.OVERWORLD.location(), BlockPos.ZERO, "");
        data.establish(registry, id, leader, BlockPos.ZERO, 0L).orElseThrow();
        var farm = area(SettlementAreaType.FARM, new BlockPos(32, 0, 0), new BlockPos(47, 0, 15));
        data.putArea(registry, id, leader, farm);
        helper.assertTrue(registry.record(id).orElseThrow().footprintSections()
                .contains(SectionPos.asLong(farm.min())), "registered areas contribute their footprint");
        data.removeArea(registry, id, leader, farm.id());
        helper.assertFalse(registry.record(id).orElseThrow().footprintSections()
                .contains(SectionPos.asLong(farm.min())), "removing an area removes its active footprint");
        helper.assertTrue(registry.record(id).orElseThrow().historicalFootprintSections()
                .contains(SectionPos.asLong(farm.min())), "historical references retain their prior footprint");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void versionThreeVillageRecordsKeepEmptyFootprints(GameTestHelper helper) {
        var registry = new VillageAllegianceRegistrySavedData();
        var id = registry.create(0L, Level.OVERWORLD.location(), BlockPos.ZERO, "");
        var saved = registry.save(new CompoundTag(), helper.getLevel().registryAccess());
        saved.putInt("FormatVersion", 3);
        var restored = VillageAllegianceRegistrySavedData.load(saved, helper.getLevel().registryAccess());
        helper.assertTrue(restored.record(id).orElseThrow().footprintSections().isEmpty(),
                "adding settlement metadata cannot rerun legacy positional migration");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void nearbyDiscoveryDoesNotExpandRegisteredTown(GameTestHelper helper) {
        var level = helper.getLevel();
        var registry = new VillageAllegianceRegistrySavedData();
        BlockPos center = helper.absolutePos(new BlockPos(1, 2, 1)).atY(64);
        BlockPos neighbor = center.offset(16, 0, 0);
        level.getChunkAt(center);
        level.getChunkAt(neighbor);
        var id = registry.create(0L, level.dimension().location(), center, "");
        registry.manage(id, center, Set.of(SectionPos.asLong(center)));
        var manager = level.getPoiManager();
        manager.add(neighbor, level.registryAccess()
                .registryOrThrow(net.minecraft.core.registries.Registries.POINT_OF_INTEREST_TYPE)
                .getHolderOrThrow(net.minecraft.world.entity.ai.village.poi.PoiTypes.HOME));
        manager.take(type -> type.is(net.minecraft.world.entity.ai.village.poi.PoiTypes.HOME),
                (type, pos) -> pos.equals(neighbor), neighbor, 1).orElseThrow();
        try {
            var discovered = registry.discoverAt(level, neighbor).orElseThrow();
            helper.assertFalse(discovered.equals(id), "touching discovery cannot claim unregistered territory");
            helper.assertValueEqual(registry.record(id).orElseThrow().footprintSections(),
                    Set.of(SectionPos.asLong(center)), "discovery leaves the managed footprint unchanged");
        } finally {
            manager.release(neighbor);
            manager.remove(neighbor);
        }
        helper.succeed();
    }

    private static SettlementArea area(SettlementAreaType type, BlockPos min, BlockPos max) {
        return new SettlementArea(UUID.randomUUID(), type, type.name(), min, max, 5, true);
    }
}
