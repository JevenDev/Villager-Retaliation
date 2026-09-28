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

    @GameTest(template = EMPTY_TEMPLATE, batch = "dialogue_memory")
    public static void savedDialogueMemoryIsBoundedAndOwnedByEachResident(GameTestHelper helper) {
        var owner = UUID.randomUUID();
        var player = UUID.randomUUID();
        var entry = new CompoundTag();
        entry.putUUID("Villager", owner);
        entry.putUUID("Player", player);
        var recent = new ListTag();
        var usage = new ListTag();
        for (int index = 0; index < 300; index++) {
            recent.add(StringTag.valueOf("line_" + index));
            var line = new CompoundTag();
            line.putString("DialogueId", "line_" + index);
            line.putInt("Count", Integer.MAX_VALUE);
            line.putLong("GameTime", index);
            usage.add(line);
        }
        entry.put("RecentLines", recent);
        entry.put("DialogueUsage", usage);
        var entries = new ListTag();
        entries.add(entry);
        var root = new CompoundTag();
        root.put("Entries", entries);
        var restored = VillagerInteractionSavedData.load(root, helper.getLevel().registryAccess());
        var memory = restored.getOrCreate(owner, player);
        helper.assertValueEqual(memory.recentDialogueIds().size(), 5, "oversized saves retain only the bounded recent history");
        helper.assertValueEqual(memory.recentDialogueIds().getLast(), "line_299", "the newest authored line survives");
        memory.rememberDialogueId(com.jvn.villagerretaliation.dialogue.normal.DialogueRequestType.GREETING, "line_299", 400L, 0L, 24000L);
        helper.assertValueEqual(memory.dialogueUsage("line_299").count(), Integer.MAX_VALUE, "long-lived usage never wraps and re-enables exhausted content");
        helper.assertTrue(restored.getOrCreate(UUID.randomUUID(), player).recentDialogueIds().isEmpty(), "neighbors do not inherit personal dialogue history");
        var saved = restored.save(new CompoundTag(), helper.getLevel().registryAccess());
        helper.assertValueEqual(saved.getList("Entries", net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0)
                .getList("DialogueUsage", net.minecraft.nbt.Tag.TAG_COMPOUND).size(), 256, "the durable usage history stays bounded");
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
