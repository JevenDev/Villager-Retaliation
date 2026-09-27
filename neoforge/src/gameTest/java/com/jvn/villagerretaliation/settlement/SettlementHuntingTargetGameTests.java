package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.AllegianceAssignmentSource;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceApi;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.combat.VillagerRetaliationHandler;
import com.jvn.villagerretaliation.interaction.HiredWorkStateStore;
import com.jvn.villagerretaliation.interaction.work.HiredHuntingTargets;
import com.jvn.villagerretaliation.interaction.work.HiredWorkContext;
import com.jvn.villagerretaliation.interaction.work.HuntingWorker;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class SettlementHuntingTargetGameTests {
    private SettlementHuntingTargetGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_hunting_targets")
    public static void unloadedHuntingKeepsTheNearestObservedTargetWithoutCreatingABody(GameTestHelper helper) {
        var fixture = fixture(helper, false);
        SettlementExperienceTestTerrain.flat(fixture.town());
        var near = cow(helper, fixture, 2);
        near.setHealth(7.0F);
        near.setCustomName(Component.literal("Observed cow"));
        var far = cow(helper, fixture, 6);
        record(helper, fixture, far);
        record(helper, fixture, near);
        SettlementBackgroundHunting.tick(helper.getLevel(), fixture.town(), fixture.worker(), 200, 1.0D);
        helper.assertValueEqual(fixture.worker().huntingTarget(), near.getUUID(), "the background hunter selects its actual nearest eligible observation");
        helper.assertValueEqual(fixture.worker().status(), "tracking_hunting_target", "target tracking exposes its current work state");
        var saved = SettlementRecord.load(fixture.town().save(helper.getLevel().registryAccess()), helper.getLevel().registryAccess(), () -> {});
        helper.assertValueEqual(saved.workers().get(fixture.worker().id()).huntingTarget(), near.getUUID(), "saving retains the requested native target identity");
        var target = saved.huntingTargets().get(near.getUUID()).context(helper.getLevel());
        helper.assertValueEqual(target.getHealth(), 7.0F, "observations retain actual health without inventing background damage");
        helper.assertValueEqual(target.getCustomName().getString(), "Observed cow", "the original entity data survives saving");
        helper.assertTrue(helper.getLevel().getEntity(near.getUUID()) == null, "target observation does not spawn a duplicate body");
        helper.assertFalse(helper.getLevel().hasChunkAt(near.blockPosition()), "target selection cannot force terrain loading");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_hunting_targets")
    public static void unknownVisibilityWaitsForObservedTerrain(GameTestHelper helper) {
        var fixture = fixture(helper, false);
        var cow = cow(helper, fixture, 4);
        record(helper, fixture, cow);
        helper.assertTrue(select(helper, fixture) == null, "unknown collision geometry cannot authorize a hunting target");
        SettlementExperienceTestTerrain.flat(fixture.town());
        helper.assertValueEqual(select(helper, fixture).getUUID(), cow.getUUID(), "known clear collision geometry permits the same observed target");
        helper.assertFalse(helper.getLevel().hasChunkAt(cow.blockPosition()), "the visibility check uses observations instead of loading chunks");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_hunting_targets")
    public static void currentNativeWallsOverrideAnOlderClearHuntingObservation(GameTestHelper helper) {
        var fixture = fixture(helper, true);
        var cow = cow(helper, fixture, 4);
        helper.assertTrue(helper.getLevel().addFreshEntity(cow), "the actual target is present");
        try {
            SettlementExperienceTestTerrain.flat(fixture.town());
            for (int y = 0; y < 3; y++) helper.getLevel().setBlockAndUpdate(fixture.town().center().east(2).above(y), Blocks.STONE.defaultBlockState());
            helper.assertTrue(select(helper, fixture) == null, "native collision blocks a stale clear line of sight");
            for (int y = 0; y < 3; y++) helper.getLevel().setBlockAndUpdate(fixture.town().center().east(2).above(y), Blocks.AIR.defaultBlockState());
            helper.assertValueEqual(select(helper, fixture).getUUID(), cow.getUUID(), "removing the actual wall restores visibility");
        } finally {
            cow.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_hunting_targets")
    public static void categoryChangesRecheckTheNativeHuntingPolicy(GameTestHelper helper) {
        var fixture = fixture(helper, false);
        SettlementExperienceTestTerrain.flat(fixture.town());
        var cow = cow(helper, fixture, 2);
        var zombie = EntityType.ZOMBIE.create(helper.getLevel());
        zombie.setPos(fixture.town().center().east(4).getBottomCenter());
        record(helper, fixture, cow);
        record(helper, fixture, zombie);
        helper.assertValueEqual(select(helper, fixture).getUUID(), cow.getUUID(), "the default native hunting category selects animals");
        fixture.worker().settings().putBoolean(HiredHuntingTargets.HUNT_ANIMALS_TAG, false);
        fixture.worker().settings().putBoolean(HiredHuntingTargets.HUNT_HOSTILES_TAG, true);
        helper.assertValueEqual(select(helper, fixture).getUUID(), zombie.getUUID(), "hostile-only settings select the actual observed hostile");
        fixture.worker().settings().putBoolean(HiredHuntingTargets.HUNT_HOSTILES_TAG, false);
        helper.assertTrue(select(helper, fixture) == null, "disabled categories cannot retain an earlier target");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_hunting_targets")
    public static void ownedAnimalsAndProtectedVillagersCannotBecomeBackgroundHuntingTargets(GameTestHelper helper) {
        var fixture = fixture(helper, false);
        SettlementExperienceTestTerrain.flat(fixture.town());
        var wolf = EntityType.WOLF.create(helper.getLevel());
        wolf.setPos(fixture.town().center().east().getBottomCenter());
        wolf.setTame(true, false);
        wolf.setOwnerUUID(UUID.randomUUID());
        var golem = EntityType.IRON_GOLEM.create(helper.getLevel());
        golem.setPos(fixture.town().center().east(2).getBottomCenter());
        record(helper, fixture, wolf);
        record(helper, fixture, golem);
        record(helper, fixture, fixture.villager());
        fixture.worker().settings().putBoolean(HiredHuntingTargets.HUNT_HOSTILES_TAG, true);
        helper.assertTrue(select(helper, fixture) == null, "native protection rules still exclude pets, villagers and golems");
        var cow = cow(helper, fixture, 5);
        record(helper, fixture, cow);
        helper.assertValueEqual(select(helper, fixture).getUUID(), cow.getUUID(), "protected nearer entities do not hide a valid target");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_hunting_targets")
    public static void nativeObservationBatchesRetainEveryActualTarget(GameTestHelper helper) {
        var fixture = fixture(helper, true);
        var cows = new java.util.ArrayList<Cow>();
        try {
            for (int index = 0; index < 25; index++) {
                var cow = cow(helper, fixture, 2);
                helper.assertTrue(helper.getLevel().addFreshEntity(cow), "the actual observed herd is accepted");
                cows.add(cow);
            }
            SettlementHuntingTargetService.observeArea(helper.getLevel(), fixture.town(), fixture.area());
            SettlementHuntingTargetService.observeArea(helper.getLevel(), fixture.town(), fixture.area());
            helper.assertValueEqual(fixture.town().huntingTargets().size(), 25, "bounded observation batches cannot truncate the herd");
            var saved = SettlementRecord.load(fixture.town().save(helper.getLevel().registryAccess()), helper.getLevel().registryAccess(), () -> {});
            helper.assertValueEqual(saved.huntingTargets().size(), 25, "every native identity survives saving");
        } finally {
            cows.forEach(Entity::discard);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_hunting_targets")
    public static void nativeMovementAndHealthAlwaysReplaceOlderReadOnlyObservations(GameTestHelper helper) {
        var fixture = fixture(helper, true);
        var cow = cow(helper, fixture, 2);
        helper.assertTrue(helper.getLevel().addFreshEntity(cow), "the native target exists");
        try {
            SettlementHuntingTargetService.observeArea(helper.getLevel(), fixture.town(), fixture.area());
            cow.setHealth(3.0F);
            cow.setCustomName(Component.literal("Current name"));
            SettlementHuntingTargetService.update(helper.getLevel(), fixture.town(), helper.getLevel().getGameTime());
            var observed = fixture.town().huntingTargets().get(cow.getUUID()).context(helper.getLevel());
            helper.assertValueEqual(observed.getHealth(), 3.0F, "observation cannot restore stale native health");
            helper.assertValueEqual(cow.getCustomName().getString(), "Current name", "observation does not reload older native entity data");
            cow.setPos(fixture.town().center().east(30).getBottomCenter());
            SettlementHuntingTargetService.update(helper.getLevel(), fixture.town(), helper.getLevel().getGameTime());
            helper.assertFalse(fixture.town().huntingTargets().containsKey(cow.getUUID()), "leaving all hunting grounds releases the observation");
        } finally {
            cow.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_hunting_targets")
    public static void emptyNativeStorageRejectsAStaleTargetInsteadOfRecreatingIt(GameTestHelper helper) {
        var fixture = fixture(helper, true);
        var cow = cow(helper, fixture, 2);
        record(helper, fixture, cow);
        fixture.worker().huntingTarget(cow.getUUID());
        helper.assertTrue(select(helper, fixture) == null, "missing bodies in loaded entity storage are not virtual hunting targets");
        helper.assertTrue(SettlementHuntingTargetService.requestedNativeTarget(fixture.villager(), target -> true).isEmpty(),
                "native return rejects the missing identity");
        helper.assertTrue(fixture.worker().huntingTarget() == null, "the unavailable request is retired");
        SettlementHuntingTargetService.update(helper.getLevel(), fixture.town(), helper.getLevel().getGameTime());
        helper.assertTrue(fixture.town().huntingTargets().isEmpty(), "the stale observation is released");
        helper.assertTrue(helper.getLevel().getEntity(cow.getUUID()) == null, "reconciliation never invents a replacement animal");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_hunting_targets")
    public static void nativeUnloadKeepsItsLastObservationDespiteTheRemovedFlag(GameTestHelper helper) {
        var fixture = fixture(helper, false);
        var cow = cow(helper, fixture, 2);
        cow.setHealth(6.0F);
        cow.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        SettlementHuntingTargetService.onUnloaded(cow);
        helper.assertTrue(fixture.town().huntingTargets().containsKey(cow.getUUID()), "unloading preserves the original living target");
        SettlementHuntingTargetService.update(helper.getLevel(), fixture.town(), helper.getLevel().getGameTime() + 48_000L);
        helper.assertValueEqual(fixture.town().huntingTargets().get(cow.getUUID()).context(helper.getLevel()).getHealth(), 6.0F,
                "time gaps cannot invent healing or damage in a read-only observation");
        var returned = fixture.town().huntingTargets().get(cow.getUUID()).context(helper.getLevel());
        returned.discard();
        SettlementHuntingTargetService.onUnloaded(returned);
        helper.assertFalse(fixture.town().huntingTargets().containsKey(cow.getUUID()), "permanent native removal releases the identity");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_hunting_targets")
    public static void queuedTargetWaitsForItsNativeSectionAndSurvivesWorkerObservation(GameTestHelper helper) {
        var fixture = fixture(helper, false);
        var cow = cow(helper, fixture, 3);
        record(helper, fixture, cow);
        fixture.worker().huntingTarget(cow.getUUID());
        helper.assertTrue(SettlementHuntingTargetService.requestedNativeTarget(fixture.villager(), target -> true).isEmpty(),
                "a pending target in an unloaded section remains unavailable");
        helper.assertValueEqual(fixture.worker().huntingTarget(), cow.getUUID(), "partial loading cannot erase the native target request");
        fixture.worker().observe(helper.getLevel(), fixture.villager(), new SettlementWorkforceService.Duty(fixture.town(), fixture.area()), 200L);
        helper.assertValueEqual(fixture.worker().huntingTarget(), cow.getUUID(), "a native inventory observation does not discard the separate request");
        var saved = SettlementWorkerState.load(fixture.worker().save());
        helper.assertValueEqual(saved.huntingTarget(), cow.getUUID(), "the target request survives a worker save");
        var different = new SettlementArea(UUID.randomUUID(), SettlementAreaType.HUNTING_GROUNDS, "Other grounds",
                fixture.area().min(), fixture.area().max(), 5, true);
        fixture.worker().observe(helper.getLevel(), fixture.villager(), new SettlementWorkforceService.Duty(fixture.town(), different), 400L);
        helper.assertTrue(fixture.worker().huntingTarget() == null, "a new workplace cannot inherit an old target request");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_hunting_targets")
    public static void nativeHunterResumesTheSavedTargetThroughItsExistingCombatPath(GameTestHelper helper) {
        var fixture = fixture(helper, true);
        var requested = cow(helper, fixture, 5);
        var closer = cow(helper, fixture, 2);
        var level = helper.getLevel();
        helper.assertTrue(level.addFreshEntity(fixture.villager()) && level.addFreshEntity(requested) && level.addFreshEntity(closer),
                "the actual returning hunter and targets are present");
        try {
            record(helper, fixture, requested);
            fixture.worker().huntingTarget(requested.getUUID());
            helper.assertTrue(VillagerRetaliationHandler.canEngageCustomTarget(fixture.villager(), requested), "the shared native engagement policy permits the animal");
            helper.assertFalse(VillagerRetaliationHandler.hasRetaliationTarget(fixture.villager(), requested),
                    "checking engagement policy cannot start a hidden fight");
            new HuntingWorker().tick(level, fixture.villager(), null, context(fixture));
            helper.assertTrue(VillagerRetaliationHandler.hasRetaliationTarget(fixture.villager(), requested),
                    "native acquisition resumes the saved target instead of replacing it with a nearer animal");
            helper.assertTrue(fixture.worker().huntingTarget() == null, "native acquisition consumes the pending request once");
            helper.assertValueEqual(requested.getHealth(), requested.getMaxHealth(), "selection and native handoff cannot award a fabricated kill");
        } finally {
            VillagerRetaliationHandler.clearCustomTarget(fixture.villager());
            fixture.villager().discard();
            requested.discard();
            closer.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_hunting_targets")
    public static void nativeHandoffRechecksChangedTargetSettings(GameTestHelper helper) {
        var fixture = fixture(helper, true);
        var requested = cow(helper, fixture, 3);
        var level = helper.getLevel();
        helper.assertTrue(level.addFreshEntity(fixture.villager()) && level.addFreshEntity(requested), "the native hunter and former target are present");
        try {
            record(helper, fixture, requested);
            fixture.worker().huntingTarget(requested.getUUID());
            var state = HiredWorkStateStore.state(fixture.villager());
            state.putBoolean(HiredHuntingTargets.HUNT_ANIMALS_TAG, false);
            state.putBoolean(HiredHuntingTargets.HUNT_HOSTILES_TAG, false);
            state.putBoolean(HiredHuntingTargets.HUNT_PLAYERS_TAG, false);
            new HuntingWorker().tick(level, fixture.villager(), null, context(fixture));
            helper.assertFalse(VillagerRetaliationHandler.hasRetaliationTarget(fixture.villager(), requested),
                    "a persisted request cannot override current native hunting toggles");
            helper.assertTrue(fixture.worker().huntingTarget() == null, "the now-invalid request is retired");
        } finally {
            VillagerRetaliationHandler.clearCustomTarget(fixture.villager());
            fixture.villager().discard();
            requested.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_hunting_targets")
    public static void pendingNativeDeathCannotRemainAnAvailableHuntingTarget(GameTestHelper helper) {
        var fixture = fixture(helper, false);
        SettlementExperienceTestTerrain.flat(fixture.town());
        var cow = cow(helper, fixture, 2);
        record(helper, fixture, cow);
        fixture.town().recordEntityDeath(SettlementEntityDeath.create(cow, cow.blockPosition(), fixture.worker(), List.of(), 0L, true));
        helper.assertTrue(select(helper, fixture) == null, "a pending native deletion cannot be hunted again");
        SettlementHuntingTargetService.update(helper.getLevel(), fixture.town(), 200L);
        helper.assertFalse(fixture.town().huntingTargets().containsKey(cow.getUUID()), "reconciliation retires the old target observation");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_hunting_targets")
    public static void olderSettlementAndWorkerSavesNeedNoHuntingTargetFields(GameTestHelper helper) {
        var fixture = fixture(helper, false);
        var tag = fixture.town().save(helper.getLevel().registryAccess());
        tag.remove("HuntingTargets");
        helper.assertTrue(SettlementRecord.load(tag, helper.getLevel().registryAccess(), () -> {}).huntingTargets().isEmpty(),
                "older towns start without invented target identities");
        var worker = fixture.worker().save();
        worker.remove("HuntingTarget");
        helper.assertTrue(SettlementWorkerState.load(worker).huntingTarget() == null, "older workers have no pending native request");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_hunting_targets")
    public static void playerHuntingRechecksTownPermissionsAndNeverSavesAPlayerBody(GameTestHelper helper) {
        var fixture = fixture(helper, true);
        var level = helper.getLevel();
        var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "hunting-player"), false);
        var player = new net.minecraft.server.level.ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation());
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        player.setPos(fixture.town().center().east(2).getBottomCenter());
        var players = new HiredHuntingTargets.Selection(false, false, true);
        try {
            helper.assertTrue(HuntingWorker.isEligibleTarget(fixture.villager(), player, players),
                    "an ordinary survival player follows the explicitly enabled native player category");
            fixture.town().setPermissions(fixture.town().leader(), player.getUUID(), java.util.Set.of(SettlementPermission.VIEW));
            helper.assertFalse(HuntingWorker.isEligibleTarget(fixture.villager(), player, players),
                    "town viewing authority protects the player from its civic hunters");
            SettlementHuntingTargetService.observeArea(helper.getLevel(), fixture.town(), fixture.area());
            helper.assertFalse(fixture.town().huntingTargets().containsKey(player.getUUID()), "player bodies are never copied into background target storage");
            boolean rejected = false;
            try {
                SettlementHuntingTarget.capture(player, helper.getLevel().getGameTime());
            } catch (IllegalArgumentException expected) {
                rejected = true;
            }
            helper.assertTrue(rejected, "direct target capture also rejects a player body");
            fixture.town().setPermissions(fixture.town().leader(), player.getUUID(), java.util.Set.of());
            helper.assertTrue(HuntingWorker.isEligibleTarget(fixture.villager(), player, players), "current permissions are checked again after delegation changes");
        } finally {
            player.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_hunting_targets")
    public static void aDifferentNativeEntityTypeCannotReuseAnOlderTargetSnapshot(GameTestHelper helper) {
        var fixture = fixture(helper, true);
        var cow = cow(helper, fixture, 2);
        record(helper, fixture, cow);
        var zombie = EntityType.ZOMBIE.create(helper.getLevel());
        zombie.setUUID(cow.getUUID());
        zombie.setPos(cow.position());
        zombie.setNoAi(true);
        helper.assertTrue(helper.getLevel().addFreshEntity(zombie), "the current native identity has a different actual type");
        try {
            SettlementHuntingTargetService.update(helper.getLevel(), fixture.town(), helper.getLevel().getGameTime());
            helper.assertFalse(fixture.town().huntingTargets().containsKey(cow.getUUID()), "a mismatched native type retires the old snapshot");
            helper.assertTrue(select(helper, fixture) == null, "animal-only hunting cannot reinterpret a live hostile as the saved cow");
            helper.assertTrue(helper.getLevel().getEntity(cow.getUUID()) == zombie, "observation never overwrites or replaces the native body");
        } finally {
            zombie.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_hunting_targets")
    public static void actualNativeRemovalReleasesItsObservedHuntingIdentity(GameTestHelper helper) {
        var fixture = fixture(helper, true);
        var cow = cow(helper, fixture, 2);
        helper.assertTrue(helper.getLevel().addFreshEntity(cow), "the real target enters native storage");
        SettlementHuntingTargetService.observeArea(helper.getLevel(), fixture.town(), fixture.area());
        helper.assertTrue(fixture.town().huntingTargets().containsKey(cow.getUUID()), "the native target is observed first");
        cow.discard();
        helper.assertFalse(fixture.town().huntingTargets().containsKey(cow.getUUID()), "the native leave event releases the permanently removed target");
        helper.succeed();
    }

    private static Fixture fixture(GameTestHelper helper, boolean loaded) {
        var level = helper.getLevel();
        var origin = helper.absolutePos(new BlockPos(2, 2, 2));
        var center = loaded ? origin : origin.offset(17_000_000, 0, 17_000_000).atY(80);
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var id = registry.create(0L, level.dimension().location(), center, "");
        var town = SettlementSavedData.get(level).establish(registry, id, UUID.randomUUID(), center, 0L).orElseThrow();
        var area = new SettlementArea(UUID.randomUUID(), SettlementAreaType.HUNTING_GROUNDS, "Hunting grounds",
                center.offset(-2, -1, -2), center.offset(10, 4, 10), 5, true);
        town.putArea(town.leader(), area);
        var villager = EntityType.VILLAGER.create(level);
        villager.setPos(center.getBottomCenter());
        villager.setNoAi(true);
        villager.setNoGravity(true);
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.FLETCHER));
        VillageAllegianceApi.assignKnown(level, villager, town.id(), AllegianceAssignmentSource.EXPLICIT_API);
        town.assignDuty(villager.getUUID(), area.id());
        SettlementWorkforceService.reconcile(level, villager);
        HiredWorkStateStore.state(villager).putBoolean("AutoDepositOutputs", false);
        HiredJobInventory.getJobInventory(villager).setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.IRON_SWORD));
        var worker = SettlementWorkerState.capture(level, villager, new SettlementWorkforceService.Duty(town, area), 0L);
        town.recordWorker(worker);
        town.recordResident(SettlementResidentState.capture(villager, town, 0L));
        return new Fixture(town, area, worker, villager);
    }

    private static Cow cow(GameTestHelper helper, Fixture fixture, int east) {
        var cow = EntityType.COW.create(helper.getLevel());
        cow.setPos(fixture.town().center().east(east).getBottomCenter());
        cow.setNoAi(true);
        cow.setNoGravity(true);
        return cow;
    }

    private static void record(GameTestHelper helper, Fixture fixture, LivingEntity target) {
        fixture.town().recordHuntingTarget(SettlementHuntingTarget.capture(target, helper.getLevel().getGameTime()));
    }

    private static LivingEntity select(GameTestHelper helper, Fixture fixture) {
        return SettlementHuntingTargetService.select(helper.getLevel(), fixture.town(), fixture.worker(),
                fixture.worker().context(helper.getLevel(), fixture.town()));
    }

    private static HiredWorkContext context(Fixture fixture) {
        return new HiredWorkContext(HiredJobInventory.getJobInventory(fixture.villager()), HiredWorkStateStore.state(fixture.villager()),
                fixture.town().center(), fixture.area().min(), fixture.area().max(), 12, 6, true, 100, false, true);
    }

    private record Fixture(SettlementRecord town, SettlementArea area, SettlementWorkerState worker, Villager villager) {
    }
}
