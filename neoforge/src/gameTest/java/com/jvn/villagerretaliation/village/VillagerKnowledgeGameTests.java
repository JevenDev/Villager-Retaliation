package com.jvn.villagerretaliation.village;

import com.jvn.villagerretaliation.allegiance.AllegianceAssignmentSource;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceApi;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.dialogue.normal.DialogueDisposition;
import com.jvn.villagerretaliation.dialogue.normal.VillagerDialogueService;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.reputation.VillagerGossipHooks;
import com.jvn.villagerretaliation.reputation.VillagerReputationManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("villager_knowledge")
@PrefixGameTestTemplate(false)
public final class VillagerKnowledgeGameTests {
    private VillagerKnowledgeGameTests() { }

    @GameTest(template = "empty")
    public static void replacementDoesNotKnowUnwitnessedKilling(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(2, 2, 2));
        var village = village(level, pos);
        try {
            var player = helper.makeMockServerPlayerInLevel();
            var victim = resident(level, pos, village);
            helper.assertTrue(victim.hurt(level.damageSources().playerAttack(player), 1000), "villager was killed");
            helper.assertFalse(victim.isAlive(), "victim is dead before replacement exists");
            victim.discard();
            var replacement = resident(level, pos, village);
            VillagerGossipHooks.processPending(level.getGameTime());
            var context = VillagerInteractionService.createDialogueContext(level, player, replacement);
            helper.assertValueEqual(context.reputation(), 0, "replacement has no inherited reputation penalty");
            helper.assertFalse(context.hasRecentPlayerEvent(
                    VillageEventMemory.EventTag.PLAYER_ATTACKED_VILLAGER,
                    VillageEventMemory.EventTag.PLAYER_KILLED_VILLAGER), "replacement never learned about the violence");
            helper.assertFalse(context.hasRecentEvent(VillageEventMemory.EventTag.VILLAGER_DEATH),
                    "communal death history does not give a replacement knowledge");
            helper.assertValueEqual(VillagerDialogueService.moodFor(context), DialogueDisposition.NEUTRAL,
                    "unwitnessed killing does not make replacement dialogue hostile");
            helper.assertTrue(VillageEventMemory.hasAny(VillageEventMemory.recentForVillage(level, village),
                    VillageEventMemory.EventTag.VILLAGER_DEATH), "village incident history remains available");
        } finally {
            removeHome(level, pos);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void queuedGossipCannotOutliveItsSource(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(2, 2, 2));
        var village = village(level, pos);
        try {
            var player = helper.makeMockServerPlayerInLevel();
            var source = resident(level, pos, village);
            VillagerGossipHooks.spreadReputation(level, source, player.getUUID(), -40);
            source.discard();
            var replacement = resident(level, pos.offset(1, 0, 0), village);
            VillagerGossipHooks.processPending(level.getGameTime());
            helper.assertValueEqual(VillagerReputationManager.getReputation(level, replacement, player.getUUID()), 0,
                    "removed source cannot gossip to a later arrival");
        } finally {
            removeHome(level, pos);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void livingWitnessCanTellALaterArrivalAboutKilling(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(2, 2, 2));
        var village = village(level, pos);
        try {
            var player = helper.makeMockServerPlayerInLevel();
            var victim = resident(level, pos, village);
            var witness = resident(level, pos.offset(1, 0, 0), village);
            helper.assertTrue(victim.hurt(level.damageSources().playerAttack(player), 1000), "villager was killed");
            victim.discard();
            var witnessContext = VillagerInteractionService.createDialogueContext(level, player, witness);
            helper.assertTrue(witnessContext.hasRecentEvent(VillageEventMemory.EventTag.VILLAGER_DEATH),
                    "eyewitness remembers the death");
            var arrival = resident(level, pos.offset(2, 0, 0), village);
            var before = VillagerInteractionService.createDialogueContext(level, player, arrival);
            helper.assertFalse(before.hasRecentEvent(VillageEventMemory.EventTag.VILLAGER_DEATH),
                    "arrival does not know before gossip is delivered");
            VillagerGossipHooks.processPending(level.getGameTime());
            var after = VillagerInteractionService.createDialogueContext(level, player, arrival);
            helper.assertTrue(after.hasRecentEvent(VillageEventMemory.EventTag.VILLAGER_DEATH),
                    "living eyewitness communicates knowledge through gossip");
            helper.assertTrue(after.reputation() < 0, "witness gossip carries the reputation penalty");
            var data = VillageEventMemorySavedData.get(level);
            var loaded = VillageEventMemorySavedData.load(
                    data.save(new CompoundTag(), level.registryAccess()), level.registryAccess());
            helper.assertTrue(VillageEventMemory.hasAny(
                    java.util.List.copyOf(loaded.villagerEvents(arrival.getUUID())),
                    VillageEventMemory.EventTag.VILLAGER_DEATH), "learned knowledge survives saving and loading");
        } finally {
            removeHome(level, pos);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void distantResidentDoesNotWitnessKilling(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(2, 2, 2));
        var village = village(level, pos);
        try {
            var player = helper.makeMockServerPlayerInLevel();
            var victim = resident(level, pos, village);
            int distance = (int) Math.ceil(VillagerRetaliationConfig.WITNESS_RADIUS.get()) + 8;
            var distant = resident(level, pos.offset(0, 0, distance), village);
            helper.assertTrue(victim.hurt(level.damageSources().playerAttack(player), 1000), "villager was killed");
            victim.discard();
            var context = VillagerInteractionService.createDialogueContext(level, player, distant);
            helper.assertValueEqual(context.reputation(), 0, "distant resident did not witness the killing");
            helper.assertFalse(context.hasRecentEvent(VillageEventMemory.EventTag.VILLAGER_DEATH),
                    "sharing a village does not grant knowledge of an unwitnessed killing");
            VillagerGossipHooks.processPending(level.getGameTime());
            helper.assertFalse(VillagerInteractionService.createDialogueContext(level, player, distant)
                    .hasRecentEvent(VillageEventMemory.EventTag.VILLAGER_DEATH),
                    "no witness exists to tell the distant resident");
        } finally {
            removeHome(level, pos);
        }
        helper.succeed();
    }

    private static VillageAllegianceId village(ServerLevel level, BlockPos pos) {
        level.getChunkAt(pos);
        var home = level.registryAccess().registryOrThrow(Registries.POINT_OF_INTEREST_TYPE)
                .getHolderOrThrow(PoiTypes.HOME);
        level.getPoiManager().add(pos, home);
        level.getPoiManager().take(type -> type.is(PoiTypes.HOME),
                (type, candidate) -> candidate.equals(pos), pos, 1).orElseThrow();
        return VillageAllegianceRegistrySavedData.get(level).discoverAt(level, pos).orElseThrow();
    }

    private static Villager resident(ServerLevel level, BlockPos pos, VillageAllegianceId village) {
        var villager = EntityType.VILLAGER.create(level);
        if (villager == null) throw new IllegalStateException("Could not create test villager");
        villager.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
        villager.setNoAi(true);
        level.addFreshEntity(villager);
        VillageAllegianceApi.assignKnown(level, villager, village, AllegianceAssignmentSource.ADMIN);
        return villager;
    }

    private static void removeHome(ServerLevel level, BlockPos pos) {
        var manager = level.getPoiManager();
        if (manager.getFreeTickets(pos) == 0) manager.release(pos);
        manager.remove(pos);
    }
}
