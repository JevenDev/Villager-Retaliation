package com.jvn.villagerretaliation.allegiance;

import com.jvn.villagerretaliation.network.OpenVillageNamingPayload;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class VillageSettlementGameTests {
    private static final String EMPTY_TEMPLATE = "empty";

    private VillageSettlementGameTests() {
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void residencyCannotCompleteInAnotherDimension(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2)).atY(64);
        level.getChunkAt(pos);
        var manager = level.getPoiManager();
        manager.add(pos, level.registryAccess().registryOrThrow(Registries.POINT_OF_INTEREST_TYPE)
                .getHolderOrThrow(PoiTypes.HOME));
        manager.take(type -> type.is(PoiTypes.HOME), (type, candidate) -> candidate.equals(pos), pos, 1)
                .orElseThrow();
        VillageAllegianceRegistrySavedData registry = VillageAllegianceRegistrySavedData.get(level);
        VillageAllegianceId village = registry.discoverAt(level, pos).orElseThrow();
        ServerLevel otherLevel = level.getServer().getLevel(Level.NETHER);
        Villager villager = EntityType.VILLAGER.create(otherLevel);
        helper.assertTrue(villager != null, "test villager can be created in the Nether");
        villager.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
        VillageAllegianceEntityData.write(villager, VillageAllegianceData.unaffiliated(
                AllegianceAssignmentSource.ADMIN, otherLevel.getGameTime(),
                otherLevel.dimension().location(), pos));
        VillageAllegianceReassignmentService.writeResidency(villager,
                new VillageAllegianceReassignmentService.Residency(village,
                        otherLevel.getGameTime() - VillageAllegianceReassignmentService.REQUIRED_RESIDENCY_TICKS));
        try {
            helper.assertFalse(VillageAllegianceService.updateWanderingResidency(otherLevel, villager),
                    "matching coordinates in another dimension cannot complete residency");
            helper.assertValueEqual(VillageAllegianceApi.get(villager).orElseThrow().state(),
                    AllegianceState.UNAFFILIATED, "the traveler remains a Wanderer");
            helper.assertTrue(VillageAllegianceReassignmentService.readResidency(villager).isEmpty(),
                    "leaving the village dimension resets the residency clock");
        } finally {
            manager.release(pos);
            manager.remove(pos);
            registry.archive(village);
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void modernRegistryDoesNotInventFootprintsOnReload(GameTestHelper helper) {
        var registry = new VillageAllegianceRegistrySavedData();
        var access = helper.getLevel().registryAccess();
        var village = registry.create(0L, Level.OVERWORLD.location(), BlockPos.ZERO, "Unobserved");
        CompoundTag saved = registry.save(new CompoundTag(), access);
        var restored = VillageAllegianceRegistrySavedData.load(saved, access);
        helper.assertTrue(restored.record(village).orElseThrow().footprintSections().isEmpty(),
                "saving an unobserved identity cannot fabricate a village footprint");
        helper.assertTrue(restored.record(village).orElseThrow().sourceSections().isEmpty(),
                "saving an unobserved identity cannot invent occupied POIs");
        saved.putInt("FormatVersion", 1);
        var legacy = VillageAllegianceRegistrySavedData.load(saved, access);
        helper.assertFalse(legacy.record(village).orElseThrow().footprintSections().isEmpty(),
                "legacy saves retain their positional migration fallback");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void mergingAcrossDimensionsPreservesBothVillages(GameTestHelper helper) {
        var registry = new VillageAllegianceRegistrySavedData();
        var first = registry.create(0L, Level.OVERWORLD.location(), BlockPos.ZERO, "First Dimension");
        var second = registry.create(1L, Level.NETHER.location(), BlockPos.ZERO, "Second Dimension");
        helper.assertFalse(registry.merge(first, second), "different dimensions cannot share a footprint");
        helper.assertValueEqual(registry.canonical(first).orElseThrow(), first, "first identity is preserved");
        helper.assertValueEqual(registry.canonical(second).orElseThrow(), second, "second identity is preserved");
        helper.assertValueEqual(registry.aliasCount(), 0, "a rejected merge creates no aliases");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void unsafeSavedAndCreatedNamesRemainNetworkSafe(GameTestHelper helper) {
        var registry = new VillageAllegianceRegistrySavedData();
        var access = helper.getLevel().registryAccess();
        String oversized = new String(Character.toChars(0x1F333)).repeat(32);
        var village = registry.create(0L, Level.OVERWORLD.location(), BlockPos.ZERO, oversized);
        helper.assertTrue(registry.record(village).orElseThrow().displayName().length() <= 32,
                "custom names respect the network UTF-16 length limit");
        CompoundTag saved = registry.save(new CompoundTag(), access);
        saved.getList("Records", Tag.TAG_COMPOUND).getCompound(0).putString("DisplayName", oversized);
        var restored = VillageAllegianceRegistrySavedData.load(saved, access);
        String name = restored.record(village).orElseThrow().displayName();
        helper.assertTrue(VillageAllegianceRegistrySavedData.validateVillageName(name).isPresent(),
                "older oversized names are repaired on load");
        helper.assertTrue(restored.isDirty(), "repaired names are persisted");
        var payload = new OpenVillageNamingPayload(BlockPos.ZERO, village, name, true, 1, 1);
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), access);
        try {
            OpenVillageNamingPayload.STREAM_CODEC.encode(buffer, payload);
            helper.assertValueEqual(OpenVillageNamingPayload.STREAM_CODEC.decode(buffer), payload,
                    "repaired names round-trip through the actual naming payload");
        } finally {
            buffer.release();
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void longMergeLineageSurvivesReloadAndUndo(GameTestHelper helper) {
        var level = helper.getLevel();
        var registry = new VillageAllegianceRegistrySavedData();
        var first = registry.create(1L, level.dimension().location(), BlockPos.ZERO, "First Village");
        var resident = java.util.UUID.randomUUID();
        registry.addOrUpdateResident(first, resident, true, 1L);
        var current = first;
        var previous = first;
        for (int index = 1; index <= 40; index++) {
            var next = registry.create(index + 1L, level.dimension().location(), new BlockPos(index * 32, 0, 0), "");
            helper.assertTrue(registry.merge(current, next), "successive village merges remain valid");
            previous = current;
            current = next;
        }
        helper.assertValueEqual(registry.canonical(first).orElse(null), current,
                "an older home still resolves after more than 32 merges");
        var restored = VillageAllegianceRegistrySavedData.load(
                registry.save(new CompoundTag(), level.registryAccess()), level.registryAccess());
        helper.assertValueEqual(restored.canonical(first).orElse(null), current,
                "long village lineages survive a world reload");
        helper.assertTrue(restored.canonicalRecord(first).orElseThrow().residents().containsKey(resident),
                "residents remain reachable through their original home identity");
        helper.assertTrue(restored.undoMerge(previous), "the most recent merge can still be undone");
        helper.assertValueEqual(restored.canonical(first).orElse(null), previous,
                "undo invalidates cached aliases throughout the lineage");
        helper.succeed();
    }
}
