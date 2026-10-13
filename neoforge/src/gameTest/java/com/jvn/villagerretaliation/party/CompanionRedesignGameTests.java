package com.jvn.villagerretaliation.party;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.StructureUtils;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("companion_redesign")
@PrefixGameTestTemplate(false)
public final class CompanionRedesignGameTests {
    static {
        StructureUtils.testStructuresDir = java.nio.file.Path.of("../../src/main/gameteststructures").toAbsolutePath().normalize().toString();
    }

    private CompanionRedesignGameTests() {
    }

    @GameTest(template = "empty")
    public static void leadershipDoesNotTransferCompanions(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        UUID admin = UUID.randomUUID();
        PartyRecord party = new PartyRecord(UUID.randomUUID(), owner, 0L);
        party.addPlayer(admin);
        party.setAdminPrivileges(admin, true);
        PartyVillagerRecord record = record(owner);
        party.addVillager(record);
        party.promoteLeader(admin);
        helper.assertTrue(CompanionAuthority.canCommand(owner, party, record), "owner keeps command permission");
        helper.assertTrue(!CompanionAuthority.canManage(admin, party, record), "leader cannot dismiss another player's companion");
        helper.assertTrue(!CompanionAuthority.canEquip(admin, party, record), "leader cannot take equipment");
        helper.assertValueEqual(record.commanderId(), owner, "promotion keeps assigned commander");
        record.assignCommander(admin);
        PartyVillagerRecord loaded = PartyVillagerRecord.load(record.save());
        helper.assertValueEqual(loaded.commanderId(), admin, "commander survives save/load");
        helper.assertTrue(CompanionAuthority.canCommand(admin, party, loaded), "entrusted commander can command");
        helper.assertTrue(!CompanionAuthority.canManage(admin, party, loaded), "delegation does not allow dismissal");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void legacyCommanderAndCaps(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        var tag = record(owner).save();
        tag.remove("Commander");
        helper.assertValueEqual(PartyVillagerRecord.load(tag).commanderId(), owner, "legacy commander defaults to recruiter");
        PartyRecord party = new PartyRecord(UUID.randomUUID(), owner, 0L);
        for (int i = 0; i < 4; i++) helper.assertTrue(party.addVillager(record(owner)), "four active villagers allowed");
        helper.assertTrue(!party.addVillager(record(owner)), "fifth villager refused");
        for (int i = 0; i < 3; i++) helper.assertTrue(party.addPlayer(UUID.randomUUID()), "four players allowed");
        helper.assertTrue(!party.addPlayer(UUID.randomUUID()), "fifth player refused");
        helper.assertValueEqual(PartyRecord.load(party.save()).villagers().size(), 4, "cap survives save/load");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void companionMigrationNeverDrainsOffline(GameTestHelper helper) {
        var record = record(UUID.randomUUID());
        helper.assertTrue(record.contractEndGameTime() == Long.MAX_VALUE, "paid rental migrates to persistent companion");
        var loaded = PartyVillagerRecord.load(record.save());
        helper.assertTrue(loaded.contractEndGameTime() == Long.MAX_VALUE, "persistent contract survives load");
        helper.assertTrue(record.save().getLong("LegacyContractEnd") == 24000L, "historical paid expiry preserved");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void departurePersistsAndUnsafeHomeWaits(GameTestHelper helper) {
        var villager = helper.spawn(net.minecraft.world.entity.EntityType.VILLAGER, new BlockPos(1, 2, 1));
        var level = helper.getLevel();
        var record = record(UUID.randomUUID());
        var actual = new PartyVillagerRecord(villager.getUUID(), record.recruiterId(), record.contractId(), 0,
                PartyCommandMode.FOLLOW, null, null, 0L, 24000L, 1, 32, "Companion", "", level.dimension().location(), villager.blockPosition());
        CompanionLifecycleService.associate(level, villager, actual);
        CompanionLifecycleService.beginDeparture(level, villager.getUUID(), "Goodbye");
        villager.tickCount = 20;
        CompanionLifecycleService.tick(villager);
        var state = CompanionSavedData.get(level).find(villager.getUUID());
        helper.assertValueEqual(state.getString("Status"), "Safe waiting", "missing home safely waits");
        var loaded = CompanionSavedData.load(CompanionSavedData.get(level).save(new net.minecraft.nbt.CompoundTag(), level.registryAccess()), level.registryAccess());
        helper.assertTrue(loaded.find(villager.getUUID()).getBoolean("Returning"), "return persists through unload/save/reload");
        helper.assertValueEqual(loaded.find(villager.getUUID()).getUUID("Owner"), actual.recruiterId(), "personal owner persists after dismissal");
        helper.assertTrue(villager.isAlive(), "waiting never removes or duplicates entity");
        helper.succeed();
    }

    private static PartyVillagerRecord record(UUID owner) {
        return new PartyVillagerRecord(UUID.randomUUID(), owner, UUID.randomUUID(), 0,
                PartyCommandMode.FOLLOW, null, null, 0L, 24000L, 1, 32, "Companion", "", ResourceLocation.parse("minecraft:overworld"), BlockPos.ZERO);
    }
}
