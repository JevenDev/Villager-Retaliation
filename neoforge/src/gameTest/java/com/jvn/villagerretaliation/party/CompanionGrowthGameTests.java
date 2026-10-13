package com.jvn.villagerretaliation.party;

import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.action.VillagerActionDefinition;
import com.jvn.villagerretaliation.action.VillagerActionRegistry;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.jvn.villagerretaliation.profile.VillagerProfileManager;
import com.jvn.villagerretaliation.skill.VillagerSkill;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("companion_redesign")
@PrefixGameTestTemplate(false)
public final class CompanionGrowthGameTests {
    static { StructureUtils.testStructuresDir = java.nio.file.Path.of("../../src/main/gameteststructures").toAbsolutePath().normalize().toString(); }
    private CompanionGrowthGameTests() { }

    @GameTest(template = "empty", batch = "companion_growth")
    public static void builtInFolioQuestOffersServiceAndChosenPermanentGrowth(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = helper.makeMockServerPlayerInLevel();
        var villager = nearby(helper, player, VillagerProfession.CARTOGRAPHER);
        VillagerProfileManager.setSkill(level, villager, VillagerSkill.CARTOGRAPHY, 10);
        var context = VillagerInteractionService.createDialogueContext(level, player, villager);
        var quest = ResourceLocation.parse("villagerretaliation:blank_map_promise");
        var started = com.jvn.villagerretaliation.quest.VillagerQuestService.performAction(context, quest, VillagerActionDefinition.QuestAction.START).orElseThrow();
        helper.assertValueEqual(started.status(), "started", "real built-in quest starts with its usual provider");
        helper.assertTrue(PartyService.getPartyForVillager(level, villager.getUUID()).isEmpty(), "quest acceptance only offers service");
        helper.assertTrue(CompanionServiceOffers.accept(player, villager, "villagerretaliation:folio_fieldwork"), "authored offer can be chosen");
        player.getInventory().setItem(0, new ItemStack(Items.PAPER, 24));
        player.getInventory().setItem(1, new ItemStack(Items.COMPASS));
        player.getInventory().setItem(2, new ItemStack(Items.INK_SAC, 3));
        for (int i = 0; i < 4; i++) {
            player.tickCount = 100;
            com.jvn.villagerretaliation.quest.VillagerQuestService.onPlayerTick(player);
        }
        var completed = com.jvn.villagerretaliation.quest.VillagerQuestService.performAction(
                VillagerInteractionService.createDialogueContext(level, player, villager), quest, VillagerActionDefinition.QuestAction.TURN_IN).orElseThrow();
        helper.assertValueEqual(completed.status(), "completed", "real folio turn-in completes with actual materials");
        helper.assertTrue(player.getInventory().countItem(Items.PAPER) < 24 && player.getInventory().countItem(Items.COMPASS) == 0
                && player.getInventory().countItem(Items.INK_SAC) == 0, "actual materials consumed before reward stacks refill slots");
        helper.assertValueEqual(CompanionRelationshipService.bond(level, villager.getUUID(), player.getUUID()), 3, "completion grants one shared folio experience");
        var state = CompanionSavedData.get(level).find(villager.getUUID());
        helper.assertTrue(!state.getBoolean("Recruited"), "quest reward does not convert service into permanent membership");
        helper.assertValueEqual(CompanionRelationshipService.recruitmentCost(level, villager, player), 0, "folio reward unlocks free permanent recruitment");
        helper.assertTrue(PartyVillagerContractService.dismiss(player, villager).success(), "player chooses to end service");
        helper.assertTrue(PartyVillagerContractService.recruit(player, villager).success(), "player separately chooses permanent recruitment");
        helper.assertTrue(CompanionGrowthService.choose(player, villager, ResourceLocation.parse("villagerretaliation:the_first_folio"), "careful_maps"), "actual quest history enables selected profession growth");
        PartySavedData.get(level).removeParty(PartyService.getPartyForPlayer(level, player.getUUID()).orElseThrow().id());
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "companion_growth")
    public static void serviceNeedsChoiceRespectsCapAndExpiresSafely(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = helper.makeMockServerPlayerInLevel();
        var villager = nearby(helper, player, VillagerProfession.CARTOGRAPHER);
        grantService(helper, player, villager);
        helper.assertTrue(PartyService.getPartyForVillager(level, villager.getUUID()).isEmpty(), "authored service offer does not join");
        var party = PartySavedData.get(level).createParty(player.getUUID(), level.getGameTime());
        for (int i = 0; i < 4; i++) PartySavedData.get(level).addVillager(party, record(UUID.randomUUID(), player.getUUID(), level, villager.blockPosition()));
        helper.assertTrue(!CompanionServiceOffers.accept(player, villager, "test:fieldwork"), "service cannot bypass four active slots");
        var state = CompanionSavedData.get(level).find(villager.getUUID());
        var offer = state.getCompound("ServiceOffers").getCompound(player.getUUID() + "/test:fieldwork");
        helper.assertTrue(!offer.getBoolean("Used"), "failed acceptance preserves opportunity");
        for (var member : List.copyOf(party.villagers())) PartyService.removeVillager(level, member.villagerId());
        helper.assertTrue(CompanionServiceOffers.accept(player, villager, "test:fieldwork"), "explicit acceptance joins without payment");
        helper.assertTrue(!state.getBoolean("Recruited"), "temporary service is not permanent recruitment");
        PartyVillagerContractService.onVillagerLoaded(villager);
        helper.assertTrue(!state.getBoolean("Recruited"), "load reconciliation does not convert temporary service");
        var reloaded = CompanionSavedData.load(CompanionSavedData.get(level).save(new CompoundTag(), level.registryAccess()), level.registryAccess()).find(villager.getUUID());
        helper.assertTrue(reloaded.getCompound("ActiveService").contains("End") && reloaded.getCompound("ServiceOffers")
                .getCompound(player.getUUID() + "/test:fieldwork").getBoolean("Used"), "absolute expiry and one-use receipt survive save/load");
        HiredJobInventory.getJobInventory(villager).setItem(6, new ItemStack(Items.BREAD, 7));
        var active = party.villager(villager.getUUID());
        state.getCompound("ActiveService").putLong("End", level.getGameTime());
        villager.hurtTime = 10;
        CompanionServiceOffers.maintain(level, party, active);
        helper.assertTrue(party.villager(villager.getUUID()) != null, "expiry during danger retains slot for rescue");
        villager.hurtTime = 0;
        CompanionServiceOffers.maintain(level, party, active);
        helper.assertTrue(party.villager(villager.getUUID()) == null && state.getBoolean("Returning"), "safe expiry releases slot and starts natural departure");
        helper.assertTrue(!state.getBoolean("Recruited") && !state.contains("ActiveService"), "expiry preserves relationship without inventing permanent terms");
        helper.assertValueEqual(CompanionRelationshipService.recruitmentCost(level, villager, player),
                com.jvn.villagerretaliation.config.VillagerRetaliationConfig.COMPANION_RECRUITMENT_COST.get(), "service does not grant free permanent rejoining");
        helper.assertValueEqual(HiredJobInventory.getJobInventory(villager).getItem(6).getCount(), 7, "belongings preserved");
        grantService(helper, player, villager);
        helper.assertTrue(!CompanionServiceOffers.accept(player, villager, "test:fieldwork"), "replayed action cannot renew spent service");
        PartySavedData.get(level).removeParty(party.id());
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "companion_growth")
    public static void serviceDoesNotErasePermanentHistory(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = helper.makeMockServerPlayerInLevel();
        var villager = nearby(helper, player, VillagerProfession.FARMER);
        CompanionRelationshipService.unlock(level, villager.getUUID(), player.getUUID(), true, 0);
        helper.assertTrue(PartyVillagerContractService.recruit(player, villager).success(), "permanent recruitment succeeds");
        helper.assertTrue(PartyVillagerContractService.dismiss(player, villager).success(), "goodbye succeeds");
        CompanionRelationshipService.experience(level, villager.getUUID(), player.getUUID(), "test:history", 5);
        grantService(helper, player, villager);
        helper.assertTrue(CompanionServiceOffers.accept(player, villager, "test:fieldwork"), "former companion can choose short service");
        var party = PartyService.getPartyForPlayer(level, player.getUUID()).orElseThrow();
        var state = CompanionSavedData.get(level).find(villager.getUUID());
        state.getCompound("ActiveService").putLong("End", level.getGameTime());
        CompanionServiceOffers.maintain(level, party, party.villager(villager.getUUID()));
        helper.assertTrue(state.getBoolean("Recruited"), "expiry does not erase earlier permanent recruitment");
        helper.assertValueEqual(CompanionRelationshipService.bond(level, villager.getUUID(), player.getUUID()), 5, "personal bond preserved");
        helper.assertTrue(PartyVillagerContractService.recruit(player, villager).success(), "ordinary rejoining remains free and chosen");
        PartySavedData.get(level).removeParty(party.id());
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "companion_growth")
    public static void storyRequiresRealHistoryProfessionAndOwnerChoice(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = helper.makeMockServerPlayerInLevel();
        var villager = nearby(helper, player, VillagerProfession.CARTOGRAPHER);
        CompanionLifecycleService.associate(level, villager, record(villager.getUUID(), player.getUUID(), level, villager.blockPosition()));
        var story = ResourceLocation.parse("villagerretaliation:the_first_folio");
        VillagerProfileManager.setSkill(level, villager, VillagerSkill.CARTOGRAPHY, 10);
        helper.assertTrue(!CompanionGrowthService.choose(player, villager, story, "careful_maps"), "growth cannot invent a shared event");
        CompanionRelationshipService.experience(level, villager.getUUID(), player.getUUID(), "villagerretaliation:first_folio", 3);
        var stranger = helper.makeMockServerPlayerInLevel();
        stranger.setPos(player.position());
        helper.assertTrue(!CompanionGrowthService.choose(stranger, villager, story, "careful_maps"), "another player cannot choose their history");
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.FARMER));
        helper.assertTrue(CompanionGrowthService.choose(player, villager, story, "careful_maps"), "recorded profession survives job changes and unlocks an explicit choice");
        helper.assertValueEqual(VillagerProfileManager.getSkill(level, villager, VillagerSkill.CARTOGRAPHY), 16, "choice changes existing capability skill");
        helper.assertTrue(!CompanionGrowthService.choose(player, villager, story, "unknown_country"), "alternative cannot be stacked");
        var saved = CompanionSavedData.load(CompanionSavedData.get(level).save(new CompoundTag(), level.registryAccess()), level.registryAccess()).find(villager.getUUID());
        helper.assertValueEqual(saved.getCompound("GrowthChoices").getCompound(story.toString()).getString("Choice"), "careful_maps", "choice receipt survives save/load");
        helper.assertValueEqual(saved.getString("OriginProfession"), "minecraft:cartographer", "original identity survives save/load");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "companion_growth")
    public static void grantTermsAccumulateWithoutWeakeningFreeRequirement(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        var villager = nearby(helper, player, VillagerProfession.FARMER);
        var level = helper.getLevel();
        CompanionRelationshipService.unlock(level, villager.getUUID(), player.getUUID(), true, 1000);
        CompanionRelationshipService.unlock(level, villager.getUUID(), player.getUUID(), false, 0);
        helper.assertTrue(CompanionRelationshipService.recruitmentCost(level, villager, player) > 0, "paid grant cannot waive another grant's free requirement");
        CompanionRelationshipService.unlock(level, villager.getUUID(), player.getUUID(), true, 0);
        CompanionRelationshipService.unlock(level, villager.getUUID(), player.getUUID(), false, 1000);
        helper.assertValueEqual(CompanionRelationshipService.recruitmentCost(level, villager, player), 0, "later paid grant cannot erase earned free terms");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "companion_growth")
    public static void rejectedStoryDataDoesNotInventSkills(GameTestHelper helper) {
        boolean rejected = false;
        try {
            CompanionGrowthService.parse(JsonParser.parseString("""
                    {"title":"Bad","text":"Invalid","experience_id":"test:x","minimum_bond":3,
                     "choices":[{"id":"a","text":"A","skill":"invented_skill","points":5},
                                {"id":"b","text":"B","skill":"survival","points":5}]}
                    """).getAsJsonObject());
        } catch (IllegalArgumentException expected) { rejected = true; }
        helper.assertTrue(rejected, "invalid authored skill rejected");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "companion_return_edges")
    public static void observedDepartureWaitsPastTimeoutAndResumes(GameTestHelper helper) {
        var level = helper.getLevel();
        var source = helper.absolutePos(new BlockPos(2048, 8, 2048));
        var home = source.offset(96, 0, 0);
        platform(level, source); platform(level, home);
        var villager = at(level, source);
        var state = departure(level, villager, home, level);
        var observer = helper.makeMockServerPlayerInLevel();
        observer.setPos(source.getX() + 10, source.getY(), source.getZ());
        state.putInt("DepartureTicks", 1200);
        tick(villager);
        helper.assertValueEqual(state.getString("Status"), "Safe waiting", "observed path remains bounded after timeout");
        helper.assertTrue(villager.blockPosition().equals(source), "timeout never forces a visible disappearance");
        observer.setPos(home.getX() + 40, home.getY(), home.getZ());
        helper.assertTrue(!CompanionLifecycleService.unseen(level, Vec3.atBottomCenterOf(home), villager), "observer sees arrival even while unable to see source");
        tick(villager);
        helper.assertTrue(villager.blockPosition().equals(source), "destination observer also prevents return");
        observer.setPos(source.getX() - 100, source.getY(), source.getZ());
        tick(villager);
        helper.assertValueEqual(state.getString("Status"), "At home", "return resumes when both ends are unobserved");
        helper.assertTrue(level.getEntity(villager.getUUID()) == villager, "same-dimension return retains real entity");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "companion_return_edges")
    public static void destroyedHomeUsesOnlyExplicitSafeFallback(GameTestHelper helper) {
        var level = helper.getLevel();
        var source = helper.absolutePos(new BlockPos(3072, 8, 3072));
        var home = source.offset(96, 0, 0);
        platform(level, source); platform(level, home);
        var villager = at(level, source);
        var state = departure(level, villager, home, level);
        state.putBoolean("HomeBed", true);
        tick(villager);
        helper.assertValueEqual(state.getString("Status"), "Safe waiting", "missing bed never silently chooses arbitrary home");
        var owner = helper.makeMockServerPlayerInLevel();
        owner.setPos(villager.position());
        state.putUUID("Owner", owner.getUUID());
        helper.assertTrue(CompanionLifecycleService.setSafeHome(owner, villager), "owner explicitly agrees safe fallback");
        helper.assertTrue(state.getLong("HomePosition") == home.asLong() && state.getBoolean("HomeBed"), "fallback preserves original bed identity");
        tick(villager);
        helper.assertValueEqual(state.getString("Status"), "At home", "already at agreed fallback may visibly resume village life");
        helper.assertTrue(state.getString("Reason").contains("fallback"), "roster explains fallback destination");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "companion_return_edges")
    public static void hazardousNeighboursAreNotSafeHomes(GameTestHelper helper) {
        var level = helper.getLevel();
        var source = helper.absolutePos(new BlockPos(4096, 8, 4096));
        platform(level, source);
        var villager = at(level, source);
        level.setBlockAndUpdate(source.east().below(), Blocks.STONE.defaultBlockState());
        for (var hazard : List.of(Blocks.FIRE, Blocks.SWEET_BERRY_BUSH, Blocks.WITHER_ROSE, Blocks.POWDER_SNOW, Blocks.LAVA)) {
            level.setBlockAndUpdate(source.east(), hazard.defaultBlockState());
            helper.assertTrue(CompanionLifecycleService.safeDestination(level, villager, source) == null, "adjacent hazard rejects isolated landing: " + hazard);
        }
        level.setBlockAndUpdate(source.east(), Blocks.AIR.defaultBlockState());
        helper.assertTrue(CompanionLifecycleService.safeDestination(level, villager, source) != null, "safe isolated landing accepted");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "companion_dimension_return", timeoutTicks = 1200)
    public static void authorizedDimensionReturnPreservesIdentityEquipmentAndHistory(GameTestHelper helper) {
        var level = helper.getLevel();
        var target = level.getServer().getLevel(Level.NETHER);
        var source = helper.absolutePos(new BlockPos(5120, 8, 5120));
        var home = new BlockPos(5128, 200, 5128);
        platform(level, source); platform(target, home);
        var sourceChunk = new net.minecraft.world.level.ChunkPos(source);
        level.getChunkSource().addRegionTicket(net.minecraft.server.level.TicketType.FORCED, sourceChunk, 0, sourceChunk);
        var villager = at(level, source);
        UUID id = villager.getUUID();
        var state = departure(level, villager, home, target);
        var owner = UUID.randomUUID();
        state.putUUID("Owner", owner);
        CompanionRelationshipService.experience(level, id, owner, "test:before_dimension", 3);
        HiredJobInventory.getJobInventory(villager).setItem(6, new ItemStack(Items.DIAMOND, 3));
        tick(villager);
        helper.assertValueEqual(state.getString("Status"), "Safe waiting", "cross-dimension return requires owner authorization");
        state.putBoolean("AllowDimensionReturn", true);
        state.putInt("DepartureTicks", 200);
        tick(villager);
        helper.assertValueEqual(state.getString("Status"), "Safe waiting", "terrain alone is insufficient without loaded destination entity storage");
        state.putBoolean("AllowDimensionReturn", false);
        var chunk = new net.minecraft.world.level.ChunkPos(home);
        target.getChunkSource().addRegionTicket(net.minecraft.server.level.TicketType.FORCED, chunk, 0, chunk);
        target.getChunk(chunk.x, chunk.z);
        helper.startSequence().thenWaitUntil(() -> {
            helper.assertTrue(target.areEntitiesLoaded(chunk.toLong()), "fixture destination entity storage becomes loaded");
            helper.assertTrue(level.getEntity(id) == villager && !villager.isRemoved(), "fixture source remains loaded during the asynchronous destination preparation");
        }).thenExecute(() -> {
            state.putBoolean("AllowDimensionReturn", true);
            tick(villager);
        }).thenWaitUntil(() -> {
            var moved = target.getEntity(id);
            helper.assertTrue(moved instanceof Villager, "Minecraft dimension transition moves real UUID to loaded destination: " + state.getString("Reason"));
            helper.assertTrue(level.getEntity(id) == null || level.getEntity(id).isRemoved(), "source entity is not duplicated");
            helper.assertValueEqual(HiredJobInventory.getJobInventory((Villager)moved).getItem(6).getCount(), 3, "carried belongings persist through dimension transition");
            helper.assertValueEqual(CompanionRelationshipService.bond(target, id, owner), 5, "existing bond plus single homecoming persists");
            helper.assertValueEqual(state.getString("Status"), "At home", "roster completes dimension return");
            moved.discard();
            target.getChunkSource().removeRegionTicket(net.minecraft.server.level.TicketType.FORCED, chunk, 0, chunk);
            level.getChunkSource().removeRegionTicket(net.minecraft.server.level.TicketType.FORCED, sourceChunk, 0, sourceChunk);
        }).thenSucceed();
    }

    private static Villager nearby(GameTestHelper helper, ServerPlayer player, VillagerProfession profession) {
        var villager = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 2, 1));
        villager.setNoAi(true); villager.setNoGravity(true);
        villager.setVillagerData(villager.getVillagerData().setProfession(profession));
        player.setPos(villager.getX() + 1, villager.getY(), villager.getZ());
        player.getInventory().clearContent();
        return villager;
    }

    @GameTest(template = "empty", batch = "companion_growth")
    public static void disbandCannotDismissAnotherPlayersCompanion(GameTestHelper helper) {
        var level = helper.getLevel();
        var leader = helper.makeMockServerPlayerInLevel();
        var owner = helper.makeMockServerPlayerInLevel();
        var villager = nearby(helper, owner, VillagerProfession.FARMER);
        var data = PartySavedData.get(level);
        var party = data.createParty(leader.getUUID(), level.getGameTime());
        data.addPlayer(party, owner.getUUID());
        var record = record(villager.getUUID(), owner.getUUID(), level, villager.blockPosition());
        record.assignCommander(leader.getUUID());
        record.setCommanderEquipmentAccess(true);
        data.addVillager(party, record);
        CompanionLifecycleService.associate(level, villager, record);
        villager.hurtTime = 10;
        PartyVillagerContractService.disband(leader);
        var personal = PartyService.getPartyForPlayer(level, owner.getUUID()).orElseThrow();
        helper.assertTrue(!personal.id().equals(party.id()) && personal.villager(villager.getUUID()) == record, "disband returns same active record to its owner");
        helper.assertTrue(!CompanionSavedData.get(level).find(villager.getUUID()).getBoolean("Returning"), "leader cannot turn disband into goodbye for another player's companion");
        helper.assertValueEqual(record.commanderId(), owner.getUUID(), "command reverts to personal owner");
        helper.assertTrue(!CompanionAuthority.canEquip(leader.getUUID(), personal, record), "former delegate loses equipment permission");
        data.removeParty(personal.id());
        var solo = data.createParty(leader.getUUID(), level.getGameTime());
        var unloaded = record(UUID.randomUUID(), leader.getUUID(), level, villager.blockPosition());
        data.addVillager(solo, unloaded);
        PartyVillagerContractService.disband(leader);
        helper.assertTrue(PartyService.getPartyForPlayer(level, leader.getUUID()).orElseThrow() == solo,
                "disband cannot dismiss unloaded companions whose combat/downed state cannot be verified");
        data.removeParty(solo.id());
        helper.succeed();
    }

    private static void grantService(GameTestHelper helper, ServerPlayer player, Villager villager) {
        var actions = VillagerActionDefinition.readListOrInline(ResourceLocation.parse("test:service"), "test",
                JsonParser.parseString("{\"type\":\"companion_service\",\"service_id\":\"test:fieldwork\",\"duration_ticks\":200}").getAsJsonObject());
        helper.assertValueEqual(actions.size(), 1, "service action parses");
        helper.assertTrue(VillagerActionRegistry.execute(VillagerInteractionService.createDialogueContext(helper.getLevel(), player, villager),
                actions.getFirst(), Map.of()).success(), "authored service offer executes");
    }

    private static PartyVillagerRecord record(UUID id, UUID owner, ServerLevel level, BlockPos pos) {
        return new PartyVillagerRecord(id, owner, UUID.randomUUID(), 0, PartyCommandMode.FOLLOW, null, null,
                0L, 24000L, 1, 32, "Companion", "", level.dimension().location(), pos);
    }

    private static void platform(ServerLevel level, BlockPos pos) {
        for (var p : BlockPos.betweenClosed(pos.offset(-5, -3, -5), pos.offset(5, 4, 5))) {
            level.getChunkAt(p);
            level.setBlockAndUpdate(p, Blocks.AIR.defaultBlockState());
        }
        level.setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
    }

    private static Villager at(ServerLevel level, BlockPos pos) {
        var villager = EntityType.VILLAGER.create(level);
        villager.setNoAi(true); villager.setNoGravity(true);
        villager.moveTo(Vec3.atBottomCenterOf(pos));
        level.addFreshEntity(villager);
        return villager;
    }

    private static CompoundTag departure(ServerLevel level, Villager villager, BlockPos home, ServerLevel homeLevel) {
        var state = CompanionSavedData.get(level).getOrCreate(villager.getUUID());
        state.putString("HomeDimension", homeLevel.dimension().location().toString());
        state.putLong("HomePosition", home.asLong());
        CompanionLifecycleService.beginDeparture(level, villager.getUUID(), "Goodbye");
        return state;
    }

    private static void tick(Villager villager) { villager.tickCount = 20; CompanionLifecycleService.tick(villager); }
}
