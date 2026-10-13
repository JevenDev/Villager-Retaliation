package com.jvn.villagerretaliation.party;

import java.util.UUID;
import java.util.List;
import java.util.Set;
import com.jvn.villagerretaliation.quest.QuestDefinition;
import com.jvn.villagerretaliation.quest.QuestFactScope;
import net.minecraft.world.level.Level;
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

    @GameTest(template = "empty")
    public static void questUnlockDoesNotAutomaticallyRecruit(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        var villager = helper.spawn(net.minecraft.world.entity.EntityType.VILLAGER, new BlockPos(1, 2, 1));
        var json = com.google.gson.JsonParser.parseString("{\"type\":\"companion_unlock\",\"free\":true}").getAsJsonObject();
        var actions = com.jvn.villagerretaliation.action.VillagerActionDefinition.readListOrInline(ResourceLocation.parse("villagerretaliation:test"), "test", json);
        helper.assertValueEqual(actions.size(), 1, "data-driven unlock parses");
        var context = com.jvn.villagerretaliation.interaction.VillagerInteractionService.createDialogueContext(helper.getLevel(), player, villager);
        var result = com.jvn.villagerretaliation.action.VillagerActionRegistry.execute(context, actions.getFirst(), java.util.Map.of());
        helper.assertTrue(result.success(), "quest/dialogue grant executes");
        helper.assertTrue(PartyService.getPartyForVillager(helper.getLevel(), villager.getUUID()).isEmpty(), "eligibility does not claim active membership");
        helper.assertValueEqual(CompanionRelationshipService.recruitmentCost(helper.getLevel(), villager, player), 0, "free grant removes upfront cost");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void meaningfulBondCannotRepeatAndSurvivesSave(GameTestHelper helper) {
        UUID player = UUID.randomUUID();
        UUID villager = UUID.randomUUID();
        helper.assertTrue(CompanionRelationshipService.experience(helper.getLevel(), villager, player, "quest/helped_home", 5), "first meaningful experience grows bond");
        helper.assertTrue(!CompanionRelationshipService.experience(helper.getLevel(), villager, player, "quest/helped_home", 5), "repeating dialogue does not grind bond");
        var loaded = CompanionSavedData.load(CompanionSavedData.get(helper.getLevel()).save(new net.minecraft.nbt.CompoundTag(), helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
        helper.assertValueEqual(loaded.find(villager).getCompound("Bonds").getInt(player.toString()), 5, "bond belongs to villager relationship across save/load");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sharedTurnInRequiresSupplyConsentAndCurrentStack(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        var level = helper.getLevel();
        var villager = helper.spawn(net.minecraft.world.entity.EntityType.VILLAGER, new BlockPos(1, 2, 1));
        player.setPos(villager.getX(), villager.getY(), villager.getZ());
        player.getInventory().clearContent();
        var data = PartySavedData.get(level);
        var party = data.createParty(player.getUUID(), level.getGameTime());
        var record = new PartyVillagerRecord(villager.getUUID(), player.getUUID(), UUID.randomUUID(), 0, PartyCommandMode.STAY,
                level.dimension().location(), villager.blockPosition(), 0L, 24000L, 1, 32, "Companion", "", level.dimension().location(), villager.blockPosition());
        data.addVillager(party, record);
        var shared = new PartySharedQuestRecord(ResourceLocation.parse("villagerretaliation:supply_test"), villager.getUUID(), 0L);
        shared.enroll(player.getUUID(), false);
        party.addSharedQuest(shared);
        var inventory = com.jvn.villagerretaliation.inventory.HiredJobInventory.getJobInventory(villager);
        inventory.setItem(4, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BONE, 5));
        inventory.setItem(6, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BONE, 5));
        var objective = itemObjective("bones", ResourceLocation.parse("minecraft:bone"), 5);
        java.util.function.BiPredicate<QuestDefinition.Objective, net.minecraft.world.item.ItemStack> matcher = (obj, stack) -> stack.is(net.minecraft.world.item.Items.BONE);
        helper.assertTrue(com.jvn.villagerretaliation.quest.PartyQuestService.planSharedItemSubmission(player, shared, List.of(objective), matcher).isEmpty(), "unoffered companion inventory is protected");
        helper.assertTrue(!com.jvn.villagerretaliation.quest.QuestContributionService.offer(player, villager, shared.questId(), 4, 5), "equipped gear cannot be offered");
        helper.assertTrue(com.jvn.villagerretaliation.quest.QuestContributionService.offer(player, villager, shared.questId(), 6, 5), "supplies can be designated");
        var plan = com.jvn.villagerretaliation.quest.PartyQuestService.planSharedItemSubmission(player, shared, List.of(objective), matcher).orElseThrow();
        inventory.setItem(6, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BONE, 5));
        helper.assertTrue(!plan.remove(), "replacing a stack invalidates a planned turn-in");
        helper.assertValueEqual(inventory.getItem(6).getCount(), 5, "replacement stays intact");
        com.jvn.villagerretaliation.quest.QuestContributionService.offer(player, villager, shared.questId(), 6, 5);
        var fresh = com.jvn.villagerretaliation.quest.PartyQuestService.planSharedItemSubmission(player, shared, List.of(objective), matcher).orElseThrow();
        helper.assertTrue(fresh.remove(), "explicit current supplies are consumed");
        helper.assertValueEqual(inventory.getItem(4).getCount(), 5, "equipped stack remains untouched");
        helper.assertTrue(!fresh.remove(), "one-time plan cannot consume twice");
        data.removeParty(party.id());
        com.jvn.villagerretaliation.quest.QuestContributionService.clearRuntimeState();
        helper.succeed();
    }

    private static QuestDefinition.Objective itemObjective(String id, ResourceLocation item, int count) {
        return new QuestDefinition.Objective(
                id,
                QuestDefinition.ObjectiveType.ITEM_CHECK,
                false,
                null,
                Level.OVERWORLD,
                null,
                8,
                List.of(),
                16,
                8,
                item,
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of(),
                null,
                null,
                QuestFactScope.PLAYER,
                null,
                Set.of(),
                "",
                Set.of(),
                null,
                null,
                count,
                true,
                QuestDefinition.ItemRequirements.EMPTY,
                List.of(),
                QuestDefinition.ObjectiveTracker.EMPTY);
    }

    private static PartyVillagerRecord record(UUID owner) {
        return new PartyVillagerRecord(UUID.randomUUID(), owner, UUID.randomUUID(), 0,
                PartyCommandMode.FOLLOW, null, null, 0L, 24000L, 1, 32, "Companion", "", ResourceLocation.parse("minecraft:overworld"), BlockPos.ZERO);
    }
}
