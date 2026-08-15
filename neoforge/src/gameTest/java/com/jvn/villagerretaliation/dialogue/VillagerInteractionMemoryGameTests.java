package com.jvn.villagerretaliation.dialogue;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class VillagerInteractionMemoryGameTests {
    private static final String EMPTY_TEMPLATE = "empty";

    private VillagerInteractionMemoryGameTests() {
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void legacyEncounterRegionsMigrateOnlyWhenUnambiguous(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        VillageAllegianceRegistrySavedData registry = new VillageAllegianceRegistrySavedData();
        BlockPos uniqueCenter = new BlockPos(1_000_000, 64, 1_000_000);
        VillageAllegianceId unique = registry.create(
                level.getGameTime(), level.dimension().location(), uniqueCenter, "Unique Legacy Region");
        UUID uniquePlayer = UUID.randomUUID();
        VillagerInteractionSavedData uniqueData = loadLegacyEncounter(
                helper,
                uniquePlayer,
                regionKey(level, uniqueCenter));
        uniqueData.migrateLegacyVillageEncounters(registry);
        helper.assertTrue(uniqueData.hasVillageEncounter(uniquePlayer, unique, registry),
                "unambiguous legacy encounter did not migrate");

        BlockPos ambiguousCenter = new BlockPos(2_000_000, 64, 2_000_000);
        VillageAllegianceId first = registry.create(
                level.getGameTime(), level.dimension().location(), ambiguousCenter, "First Ambiguous Region");
        VillageAllegianceId second = registry.create(
                level.getGameTime(), level.dimension().location(), ambiguousCenter.offset(1, 0, 1), "Second Ambiguous Region");
        UUID ambiguousPlayer = UUID.randomUUID();
        VillagerInteractionSavedData ambiguousData = loadLegacyEncounter(
                helper,
                ambiguousPlayer,
                regionKey(level, ambiguousCenter));
        ambiguousData.migrateLegacyVillageEncounters(registry);
        helper.assertFalse(ambiguousData.hasVillageEncounter(ambiguousPlayer, first, registry),
                "ambiguous legacy encounter migrated to the first village");
        helper.assertFalse(ambiguousData.hasVillageEncounter(ambiguousPlayer, second, registry),
                "ambiguous legacy encounter migrated to the second village");
        helper.succeed();
    }

    private static VillagerInteractionSavedData loadLegacyEncounter(
            GameTestHelper helper,
            UUID playerId,
            String villageKey) {
        CompoundTag root = new CompoundTag();
        CompoundTag encounters = new CompoundTag();
        encounters.putUUID("Player", playerId);
        ListTag villages = new ListTag();
        villages.add(StringTag.valueOf(villageKey));
        encounters.put("Villages", villages);
        ListTag entries = new ListTag();
        entries.add(encounters);
        root.put("VillageEncounters", entries);
        return VillagerInteractionSavedData.load(root, helper.getLevel().registryAccess());
    }

    private static String regionKey(ServerLevel level, BlockPos center) {
        return level.dimension().location() + ":"
                + Math.floorDiv(center.getX(), 64) + "," + Math.floorDiv(center.getZ(), 64);
    }
}
