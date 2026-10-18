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

    @GameTest(template = "empty", batch = "companion_rescue", timeoutTicks = 160)
    public static void rescueCompletesAndDamageCancelsWithoutSpending(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = helper.makeMockServerPlayerInLevel();
        var villager = helper.spawn(net.minecraft.world.entity.EntityType.VILLAGER, new BlockPos(1, 2, 1));
        villager.setNoGravity(true);
        player.setNoGravity(true);
        player.setPos(villager.getX() + 1.0D, villager.getY(), villager.getZ());
        player.setShiftKeyDown(true);
        player.getInventory().clearContent();
        player.getInventory().setItem(2, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BREAD, 2));
        var record = new PartyVillagerRecord(villager.getUUID(), player.getUUID(), UUID.randomUUID(), 0, PartyCommandMode.FOLLOW,
                null, null, 0L, 24000L, 1, 32, "Companion", "", level.dimension().location(), villager.blockPosition());
        CompanionLifecycleService.associate(level, villager, record);
        com.jvn.villagerretaliation.combat.downed.VillagerDownedService.enterDowned(level, villager,
                new com.jvn.villagerretaliation.combat.downed.VillagerDeathProtectionResolver.ProtectionResult(true, List.of("test")));
        var requiresBread = com.jvn.villagerretaliation.config.VillagerRetaliationConfig.REVIVE_REQUIRES_BREAD;
        boolean original = requiresBread.get();
        requiresBread.set(true);
        try {
            helper.assertTrue(com.jvn.villagerretaliation.combat.downed.VillagerReviveService.begin(player, villager), "deliberate help-up begins");
        } finally { requiresBread.set(original); }
        com.jvn.villagerretaliation.combat.downed.VillagerReviveService.damaged(player);
        helper.assertTrue(!com.jvn.villagerretaliation.combat.downed.VillagerReviveService.isBeingHelped(villager), "damage interrupts channel");
        helper.assertValueEqual(player.getInventory().getItem(2).getCount(), 2, "interrupted rescue uses no resources");
        helper.assertTrue(com.jvn.villagerretaliation.combat.downed.VillagerReviveService.begin(player, villager), "safe retry begins");
        helper.runAfterDelay(100L, () -> {
            helper.assertTrue(!com.jvn.villagerretaliation.combat.downed.VillagerDownedService.isDowned(villager), "four-second rescue completes");
            helper.assertTrue(villager.getHealth() >= villager.getMaxHealth() * 0.25F, "existing recovery health rule preserved");
            helper.assertValueEqual(CompanionRelationshipService.bond(level, villager.getUUID(), player.getUUID()), 3, "meaningful rescue grows personal bond");
            helper.assertTrue(!CompanionRelationshipService.rescued(level, villager, player), "rescue reward cooldown prevents spam");
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void secondWindBridgeKeepsRecoveryHealthOverride(GameTestHelper helper) {
        com.jvn.villagerretaliation.compat.secondwind.VillagerSecondWindCompatGameTests.adapterUsesVillagerTargetAfterReviverArgument(helper);
    }

    @GameTest(template = "empty")
    public static void dimensionReturnRequiresExplicitConsentAndHomeSafety(GameTestHelper helper) {
        var level = helper.getLevel();
        var villager = helper.spawn(net.minecraft.world.entity.EntityType.VILLAGER, new BlockPos(1, 2, 1));
        var state = CompanionSavedData.get(level).getOrCreate(villager.getUUID());
        state.putUUID("Owner", UUID.randomUUID());
        state.putString("HomeDimension", "minecraft:the_nether");
        state.putLong("HomePosition", villager.blockPosition().asLong());
        CompanionLifecycleService.beginDeparture(level, villager.getUUID(), "Goodbye");
        villager.tickCount = 20;
        CompanionLifecycleService.tick(villager);
        helper.assertTrue(state.getString("Reason").contains("authorize dimension return"), "cross-dimension return requires owner consent");
        helper.assertTrue(villager.level() == level, "entity stays in its current dimension while waiting");
        var pos = helper.absolutePos(new BlockPos(4, 2, 4));
        level.setBlockAndUpdate(pos.below(), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(pos.above(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        helper.assertTrue(CompanionLifecycleService.safeDestination(level, villager, pos) != null, "standing destination requires a solid floor and clear headroom");
        var observer = helper.makeMockServerPlayerInLevel();
        observer.setPos(villager.getX() + 10.0D, villager.getY(), villager.getZ());
        helper.assertTrue(!CompanionLifecycleService.unseen(level, villager.position(), villager), "any nearby observer blocks hidden return");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void splitAndRejoinPreservePersonalMembership(GameTestHelper helper) {
        var level = helper.getLevel();
        var owner = helper.makeMockServerPlayerInLevel();
        var leader = net.neoforged.neoforge.common.util.FakePlayerFactory.get(level, new com.mojang.authlib.GameProfile(UUID.randomUUID(), "companion_leader"));
        var data = PartySavedData.get(level);
        var party = data.createParty(leader.getUUID(), level.getGameTime());
        data.addPlayer(party, owner.getUUID());
        var companion = record(owner.getUUID());
        data.addVillager(party, companion);
        helper.assertTrue(PartyService.leaveParty(owner).success(), "owner leaves multiplayer party");
        var personal = data.partyForPlayer(owner.getUUID()).orElseThrow();
        helper.assertTrue(personal.villager(companion.villagerId()) == companion, "same record follows owner into personal party");
        helper.assertTrue(party.villager(companion.villagerId()) == null, "original party releases its active slot");
        var invitation = PartyService.sendInvitation(leader, owner);
        helper.assertTrue(invitation.success(), "solo companion owner can be invited back");
        helper.assertTrue(PartyService.acceptInvitation(owner, invitation.invitationId()).success(), "rejoin carries companion within party cap");
        helper.assertTrue(party.villager(companion.villagerId()) == companion, "same companion record rejoins without duplication");
        helper.assertValueEqual(companion.recruiterId(), owner.getUUID(), "personal association remains with owner");
        data.removeParty(party.id());
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void logoutAndPlayerDeathKeepPredictableSlots(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = helper.makeMockServerPlayerInLevel();
        var villager = helper.spawn(net.minecraft.world.entity.EntityType.VILLAGER, new BlockPos(1, 2, 1));
        var data = PartySavedData.get(level);
        var party = data.createParty(player.getUUID(), level.getGameTime());
        var record = new PartyVillagerRecord(villager.getUUID(), player.getUUID(), UUID.randomUUID(), 0, PartyCommandMode.FOLLOW,
                null, null, 0L, 24000L, 1, 32, "Companion", "", level.dimension().location(), villager.blockPosition());
        data.addVillager(party, record);
        CompanionLifecycleService.associate(level, villager, record);
        CompanionLifecycleService.onLogout(player);
        var state = CompanionSavedData.get(level).find(villager.getUUID());
        helper.assertValueEqual(state.getString("Status"), "Waiting for commander", "logout uses safe waiting");
        helper.assertValueEqual(party.villagers().size(), 1, "logout preserves active slot");
        player.setHealth(0.0F);
        state.putLong("DeathWaitStart", level.getServer().overworld().getGameTime() - 1200L);
        villager.tickCount = 1;
        CompanionLifecycleService.tick(villager);
        helper.assertTrue(state.getBoolean("Returning"), "bounded death wait transitions to a safe return attempt");
        helper.assertValueEqual(party.villagers().size(), 1, "death does not discard the association or slot");
        player.setHealth(player.getMaxHealth());
        data.removeParty(party.id());
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "companion_departure")
    public static void hiddenReturnKeepsRealEntityAndBelongings(GameTestHelper helper) {
        var level = helper.getLevel();
        var source = helper.absolutePos(new BlockPos(1024, 2, 1024));
        var home = source.offset(16, 0, 0);
        level.setChunkForced(source.getX() >> 4, source.getZ() >> 4, true);
        level.setChunkForced(home.getX() >> 4, home.getZ() >> 4, true);
        for (var pos : List.of(source, home)) {
            level.setBlockAndUpdate(pos.below(), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(pos.above(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        }
        var villager = net.minecraft.world.entity.EntityType.VILLAGER.create(level);
        villager.moveTo(source.getX() + 0.5D, source.getY(), source.getZ() + 0.5D, 0, 0);
        villager.setNoAi(true);
        level.addFreshEntity(villager);
        var id = villager.getUUID();
        var inventory = com.jvn.villagerretaliation.inventory.HiredJobInventory.getJobInventory(villager);
        inventory.setItem(6, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BREAD, 7));
        var state = CompanionSavedData.get(level).getOrCreate(id);
        state.putUUID("Owner", UUID.randomUUID());
        state.putString("HomeDimension", level.dimension().location().toString());
        state.putLong("HomePosition", home.asLong());
        helper.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener() {
            @Override public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo test) { }
            @Override public void testPassed(net.minecraft.gametest.framework.GameTestInfo test, net.minecraft.gametest.framework.GameTestRunner runner) { cleanup(); }
            @Override public void testFailed(net.minecraft.gametest.framework.GameTestInfo test, net.minecraft.gametest.framework.GameTestRunner runner) { cleanup(); }
            @Override public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo original, net.minecraft.gametest.framework.GameTestInfo rerun, net.minecraft.gametest.framework.GameTestRunner runner) { }
            private void cleanup() {
                villager.discard();
                level.setChunkForced(source.getX() >> 4, source.getZ() >> 4, false);
                level.setChunkForced(home.getX() >> 4, home.getZ() >> 4, false);
            }
        });
        helper.startSequence().thenWaitUntil(() -> {
            helper.assertTrue(level.getEntity(id) == villager, "source chunk tracks the real companion before departure");
        }).thenExecute(() -> {
            CompanionLifecycleService.beginDeparture(level, id, "Goodbye");
            state.putInt("DepartureTicks", 200);
            villager.tickCount = 20;
            helper.assertTrue(CompanionLifecycleService.unseen(level, villager.position(), villager), "return fixture is out of sight of every nearby player");
            CompanionLifecycleService.tick(villager);
            helper.assertValueEqual(state.getString("Status"), "At home", "unseen safe return completes");
            helper.assertTrue(villager.blockPosition().distSqr(home) < 9.0D, "real entity reaches registered home");
        }).thenWaitUntil(() -> {
            helper.assertTrue(level.getEntity(id) == villager, "same entity and UUID survive return");
        }).thenExecute(() -> {
            helper.assertValueEqual(inventory.getItem(6).getCount(), 7, "belongings survive without copying or dropping");
        }).thenSucceed();
    }

    @GameTest(template = "empty", batch = "companion_recruitment")
    public static void goodbyeRejoinIsChosenFreeAndDoesNotDuplicate(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = helper.makeMockServerPlayerInLevel();
        var villager = helper.spawn(net.minecraft.world.entity.EntityType.VILLAGER, new BlockPos(1, 2, 1));
        player.setPos(villager.getX() + 1, villager.getY(), villager.getZ());
        player.getInventory().clearContent();
        CompanionRelationshipService.unlock(level, villager.getUUID(), player.getUUID(), true, -1000);
        helper.assertTrue(PartyService.getPartyForVillager(level, villager.getUUID()).isEmpty(), "unlock waits for player recruitment choice");
        helper.assertTrue(PartyVillagerContractService.recruit(player, villager).success(), "player chooses to join after free grant");
        var inventory = com.jvn.villagerretaliation.inventory.HiredJobInventory.getJobInventory(villager);
        inventory.setItem(6, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BREAD, 7));
        helper.assertTrue(PartyVillagerContractService.dismiss(player, villager).success(), "safe goodbye releases active slot");
        helper.assertTrue(PartyService.getPartyForVillager(level, villager.getUUID()).isEmpty(), "goodbye immediately frees party slot");
        helper.assertValueEqual(CompanionLifecycleService.owner(level, villager.getUUID()), player.getUUID(), "personal association survives goodbye");
        helper.assertTrue(PartyVillagerContractService.recruit(player, villager).success(), "same companion rejoins without payment");
        var party = PartyService.getPartyForPlayer(level, player.getUUID()).orElseThrow();
        helper.assertValueEqual(party.villagers().size(), 1, "rejoin creates exactly one active record");
        helper.assertValueEqual(inventory.getItem(6).getCount(), 7, "rejoin preserves belongings");
        helper.assertTrue(level.getEntity(villager.getUUID()) == villager, "rejoin retains the same villager entity");
        PartySavedData.get(level).removeParty(party.id());
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void existingEquipmentAndPaymentRegression(GameTestHelper helper) {
        PartyGameTests.recruitmentPaymentExtensionCommandsInventoryAndDismissalAreAuthoritative(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void existingConsentedFetchRegression(GameTestHelper helper) {
        PartyGameTests.sharedFetchSubmissionConsumesPlayerAndVillagerStacksOnlyOnce(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void existingFetchCapacityRegression(GameTestHelper helper) {
        PartyGameTests.sharedFetchCapacityFailurePreservesEveryContributor(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void existingUnloadedMigrationRegression(GameTestHelper helper) {
        PartyGameTests.unloadedCompanionsPersistAcrossHistoricalExpiry(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void existingReleasedCompanionCombatRegression(GameTestHelper helper) {
        PartyGameTests.killOnSightDoesNotTargetDismissedCompanion(helper);
    }

    @GameTest(template = "empty")
    public static void legacyRenewalBecomesFreeRejoin(GameTestHelper helper) {
        PartyGameTests.dismissedCompanionCanRejoinThroughLegacyRenewalWithoutPayment(helper);
    }

    @GameTest(template = "empty")
    public static void partialOwnStackCannotBeConsumedBySamePlanTwice(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        player.getInventory().clearContent();
        player.getInventory().setItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BONE, 10));
        var data = PartySavedData.get(helper.getLevel());
        var party = data.createParty(player.getUUID(), 0L);
        var shared = new PartySharedQuestRecord(ResourceLocation.parse("villagerretaliation:partial_count"), null, 0L);
        shared.enroll(player.getUUID(), false);
        party.addSharedQuest(shared);
        var plan = com.jvn.villagerretaliation.quest.PartyQuestService.planSharedItemSubmission(player, shared,
                List.of(itemObjective("bones", ResourceLocation.parse("minecraft:bone"), 5)),
                (objective, stack) -> stack.is(net.minecraft.world.item.Items.BONE)).orElseThrow();
        helper.assertTrue(plan.remove(), "first settlement removes the requested amount");
        helper.assertTrue(!plan.remove(), "same plan cannot settle a partially remaining stack twice");
        helper.assertValueEqual(player.getInventory().getItem(0).getCount(), 5, "unrequested items stay in the inventory");
        data.removeParty(party.id());
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
