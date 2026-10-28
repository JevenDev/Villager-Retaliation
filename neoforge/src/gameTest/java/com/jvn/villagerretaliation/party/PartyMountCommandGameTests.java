package com.jvn.villagerretaliation.party;

import com.jvn.villagerretaliation.mount.VillagerMountAssignmentService;
import com.jvn.villagerretaliation.network.PartyQuickCommandRequestPayload;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("party_commands")
@PrefixGameTestTemplate(false)
public final class PartyMountCommandGameTests {
    @GameTest(template = "empty")
    public static void nonLeaderOwnerCanManageCompanionMount(GameTestHelper helper) {
        try (var fixture = new PartyCommandTransitionGameTests.Fixture(helper)) {
            UUID leader = UUID.randomUUID();
            fixture.data.addPlayer(fixture.party, leader);
            fixture.party.promoteLeader(leader);
            helper.assertTrue(CompanionAuthority.canManage(fixture.player.getUUID(), fixture.party, fixture.record), "owner retains management rights");
            helper.assertTrue(com.jvn.villagerretaliation.mount.VillagerMountAssignmentService.canManage(fixture.player, fixture.villager),
                    "companion owner must retain mount management after leader promotion");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void unrelatedCompanionMustNotBlockMountCommand(GameTestHelper helper) {
        try (var fixture = new PartyCommandTransitionGameTests.Fixture(helper)) {
            var level = helper.getLevel();
            var mounts = com.jvn.villagerretaliation.mount.VillagerMountAssignmentSavedData.get(level);
            mounts.assign(new com.jvn.villagerretaliation.mount.VillagerMountAssignment(
                    fixture.villager.getUUID(), UUID.randomUUID(), net.minecraft.resources.ResourceLocation.withDefaultNamespace("horse"),
                    level.dimension().location(), fixture.villager.blockPosition(), level.dimension().location(),
                    fixture.villager.blockPosition(), level.getGameTime()));
            try {
                PartyQuickCommandService.handle(fixture.player, new PartyQuickCommandRequestPayload(PartyQuickCommand.RIDE_MOUNT));
                helper.assertTrue(fixture.party.mountMode(), "mount command works before adding another owner's companion");
                fixture.party.setMountMode(false);
                UUID otherOwner = UUID.randomUUID();
                fixture.data.addPlayer(fixture.party, otherOwner);
                UUID otherVillager = UUID.randomUUID();
                fixture.data.addVillager(fixture.party, new PartyVillagerRecord(otherVillager, otherOwner, UUID.randomUUID(), 1,
                        PartyCommandMode.FOLLOW, null, null, level.getGameTime(), Long.MAX_VALUE, 1, 0,
                        "Other", "minecraft:none", level.dimension().location(), fixture.villager.blockPosition()));
                PartyQuickCommandService.handle(fixture.player, new PartyQuickCommandRequestPayload(PartyQuickCommand.RIDE_MOUNT));
                helper.assertTrue(fixture.party.mountMode(), "another owner's companion must not block mounting my companion");
                helper.assertFalse(fixture.party.villager(otherVillager).mountMode(), "other owner retains mount control");
                var restored = PartyRecord.load(fixture.party.save());
                helper.assertTrue(restored.villager(fixture.villager.getUUID()).mountMode(), "own mount setting persists");
                helper.assertFalse(restored.villager(otherVillager).mountMode(), "other mount setting persists independently");
                PartyQuickCommandService.handle(fixture.player, new PartyQuickCommandRequestPayload(PartyQuickCommand.DISMOUNT_MOUNT));
                helper.assertFalse(fixture.record.mountMode(), "owner can dismount in a mixed-owner party");
            } finally {
                com.jvn.villagerretaliation.mount.VillagerMountAssignmentService.clearAssignment(level, fixture.villager.getUUID());
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void disbandMustKeepSurvivingCompanionMount(GameTestHelper helper) {
        try (var fixture = new PartyCommandTransitionGameTests.Fixture(helper)) {
            var level = helper.getLevel();
            UUID otherOwner = UUID.randomUUID();
            fixture.data.addPlayer(fixture.party, otherOwner);
            fixture.record.transferRecruiter(otherOwner);
            fixture.record.assignCommander(otherOwner);
            var mounts = com.jvn.villagerretaliation.mount.VillagerMountAssignmentSavedData.get(level);
            mounts.assign(new com.jvn.villagerretaliation.mount.VillagerMountAssignment(
                    fixture.villager.getUUID(), UUID.randomUUID(), net.minecraft.resources.ResourceLocation.withDefaultNamespace("horse"),
                    level.dimension().location(), fixture.villager.blockPosition(), level.dimension().location(),
                    fixture.villager.blockPosition(), level.getGameTime()));
            try {
                PartyVillagerContractService.disband(fixture.player);
                var personal = fixture.data.partyForPlayer(otherOwner).orElseThrow();
                helper.assertTrue(personal.villager(fixture.villager.getUUID()) != null, "other owner's companion survives disband");
                helper.assertTrue(mounts.forVillager(fixture.villager.getUUID()).isPresent(),
                        "surviving companion must retain its assigned mount after disband");
            } finally {
                fixture.data.partyForPlayer(otherOwner).ifPresent(personal -> fixture.data.removeParty(personal.id()));
                com.jvn.villagerretaliation.mount.VillagerMountAssignmentService.clearAssignment(level, fixture.villager.getUUID());
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void mountManagementRequiresOwnershipOrEquipmentGrant(GameTestHelper helper) {
        try (var fixture = new PartyCommandTransitionGameTests.Fixture(helper)) {
            UUID owner = UUID.randomUUID();
            fixture.data.addPlayer(fixture.party, owner);
            fixture.record.transferRecruiter(owner);
            helper.assertFalse(VillagerMountAssignmentService.canManage(fixture.player, fixture.villager),
                    "leader and commander have no implicit mount assignment rights");
            fixture.record.setCommanderEquipmentAccess(true);
            helper.assertTrue(VillagerMountAssignmentService.canManage(fixture.player, fixture.villager),
                    "explicit equipment grant permits mount assignment");
            fixture.record.setCommanderEquipmentAccess(false);
            helper.assertFalse(VillagerMountAssignmentService.canManage(fixture.player, fixture.villager),
                    "revoking equipment grant revokes mount assignment");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void legacyMountModeMigratesToEachCompanion(GameTestHelper helper) {
        try (var fixture = new PartyCommandTransitionGameTests.Fixture(helper)) {
            var saved = fixture.party.save();
            saved.putBoolean("MountMode", true);
            var member = saved.getList("Villagers", net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0);
            member.remove("MountMode");
            helper.assertTrue(PartyRecord.load(saved).villager(fixture.villager.getUUID()).mountMode(),
                    "legacy enabled mount mode migrates to companion");
            member.putBoolean("MountMode", false);
            helper.assertFalse(PartyRecord.load(saved).villager(fixture.villager.getUUID()).mountMode(),
                    "explicit companion setting overrides legacy party default");
        }
        helper.succeed();
    }
}
