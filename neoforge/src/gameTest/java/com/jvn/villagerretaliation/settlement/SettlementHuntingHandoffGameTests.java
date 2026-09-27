package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.AllegianceAssignmentSource;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceApi;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.interaction.HiredWorkStateStore;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.jvn.villagerretaliation.mixin.LivingEntityCombatAccessor;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class SettlementHuntingHandoffGameTests {
    private SettlementHuntingHandoffGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_hunting_handoff")
    public static void anAppliedBodyResumesItsPendingClockOnlyOnce(GameTestHelper helper) {
        var cow = EntityType.COW.create(helper.getLevel());
        long now = helper.getLevel().getGameTime();
        var state = SettlementHuntingTarget.capture(cow, now - 7L);
        var before = cow.saveWithoutId(new CompoundTag());
        cow.setHealth(8.0F);
        clock(cow, 20, 10, 2.0F);
        state.interacted(cow, before, now - 7L);
        var body = EntityType.COW.create(helper.getLevel());
        body.load(cow.saveWithoutId(new CompoundTag()));
        var saved = SettlementHuntingTarget.load(state.save());
        helper.assertFalse(saved.restore(body, now), "the native body has already applied this damage version");
        SettlementHuntingTarget.resumeNativeTimers(body, now);
        helper.assertValueEqual(body.invulnerableTime, 13, "the persisted pending clock advances despite matching damage versions");
        helper.assertValueEqual(body.hurtTime, 3, "the visible hurt timer uses the same elapsed interval");
        helper.assertValueEqual(((LivingEntityCombatAccessor) body).villagerretaliation$getLastHurt(), 2.0F,
                "the native damage comparison survives the handoff");
        clock(body, 19, 9, 6.0F);
        SettlementHuntingTarget.resumeNativeTimers(body, now + 1L);
        helper.assertValueEqual(body.invulnerableTime, 19, "later native immunity cannot be replaced by a second handoff");
        helper.assertValueEqual(((LivingEntityCombatAccessor) body).villagerretaliation$getLastHurt(), 6.0F,
                "later native damage keeps its own comparison");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_hunting_handoff")
    public static void observingAFrozenBodyPreservesTheOriginalPendingClock(GameTestHelper helper) {
        var cow = EntityType.COW.create(helper.getLevel());
        long now = helper.getLevel().getGameTime();
        var state = SettlementHuntingTarget.capture(cow, now - 40L);
        var before = cow.saveWithoutId(new CompoundTag());
        cow.setHealth(8.0F);
        clock(cow, 20, 10, 2.0F);
        state.interacted(cow, before, now - 40L);
        state.observe(cow, now);
        var saved = SettlementHuntingTarget.load(state.save());
        helper.assertValueEqual(saved.observedAt(), now - 40L, "repeated frozen observations cannot restart the pending immunity");
        var target = saved.context(helper.getLevel());
        helper.assertValueEqual(target.invulnerableTime, 0, "elapsed immunity is absent from a reconstructed combat body");
        var nativeBody = EntityType.COW.create(helper.getLevel());
        nativeBody.setUUID(cow.getUUID());
        saved.restore(nativeBody, now);
        helper.assertValueEqual(nativeBody.invulnerableTime, 0, "an older returning body receives the same elapsed clock");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_hunting_handoff")
    public static void newerBodyClockSurvivesAnOlderSettlementSnapshot(GameTestHelper helper) {
        var cow = EntityType.COW.create(helper.getLevel());
        long now = helper.getLevel().getGameTime();
        var state = SettlementHuntingTarget.capture(cow, now - 20L);
        var before = cow.saveWithoutId(new CompoundTag());
        clock(cow, 20, 10, 2.0F);
        state.interacted(cow, before, now - 20L);
        var older = SettlementHuntingTarget.load(state.save());
        before = cow.saveWithoutId(new CompoundTag());
        clock(cow, 20, 10, 5.0F);
        state.interacted(cow, before, now - 4L);
        helper.assertFalse(older.restore(cow, now), "the older damage version cannot replace the newer body");
        SettlementHuntingTarget.resumeNativeTimers(cow, now);
        helper.assertValueEqual(cow.invulnerableTime, 16, "the body carries its newer pending clock independently");
        helper.assertValueEqual(((LivingEntityCombatAccessor) cow).villagerretaliation$getLastHurt(), 5.0F,
                "the newer impact remains the native damage comparison");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_hunting_handoff")
    public static void oldCombatSavesRetainTheirStoredHurtTimer(GameTestHelper helper) {
        var cow = EntityType.COW.create(helper.getLevel());
        long now = helper.getLevel().getGameTime();
        clock(cow, 20, 10, 3.0F);
        var state = SettlementHuntingTarget.capture(cow, now - 4L);
        var tag = state.save();
        tag.remove("HurtTicks");
        var loaded = SettlementHuntingTarget.load(tag).context(helper.getLevel());
        helper.assertValueEqual(loaded.hurtTime, 6, "old saves recover their native HurtTime when the separate clock field is absent");
        helper.assertValueEqual(loaded.invulnerableTime, 16, "existing stored immunity remains compatible");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_hunting_handoff")
    public static void hunterInjuryRetainsItsPendingClockThroughWorkerSaving(GameTestHelper helper) {
        var fixture = fixture(helper);
        long now = helper.getLevel().getGameTime();
        var owner = fixture.worker().context(helper.getLevel(), fixture.town());
        var before = owner.saveWithoutId(new CompoundTag());
        owner.setHealth(17.0F);
        clock(owner, 20, 10, 3.0F);
        fixture.town().residents().get(owner.getUUID()).combat(owner);
        fixture.worker().huntingInjury(owner, before, now - 6L);
        var saved = SettlementWorkerState.load(fixture.worker().save());
        saved.restore(fixture.villager());
        SettlementHuntingTarget.resumeNativeTimers(fixture.villager(), now);
        helper.assertValueEqual(fixture.villager().invulnerableTime, 14, "the returning hunter keeps only its remaining hit immunity");
        helper.assertValueEqual(fixture.villager().getHealth(), 17.0F, "the clock handoff retains the actual injury");
        clock(fixture.villager(), 18, 8, 4.0F);
        saved.restore(fixture.villager());
        SettlementHuntingTarget.resumeNativeTimers(fixture.villager(), now + 2L);
        helper.assertValueEqual(fixture.villager().invulnerableTime, 18, "repeated worker restoration cannot reset newer native immunity");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 400, batch = "settlement_hunting_clock_chunks")
    public static void anActualFrozenTargetRecoversBeforeNativeCombatResumes(GameTestHelper helper) {
        var fixture = fixture(helper);
        var level = helper.getLevel();
        var cow = cow(helper, fixture);
        var chunk = new ChunkPos(fixture.town().center());
        fixture.worker().settings().putBoolean("Enabled", false);
        level.getChunkSource().addRegionTicket(TicketType.FORCED, chunk, 0, chunk);
        level.getChunk(chunk.x, chunk.z);
        helper.startSequence().thenWaitUntil(() -> {
            helper.assertTrue(level.areEntitiesLoaded(chunk.toLong()), "the native target storage is loaded");
            helper.assertFalse(level.isPositionEntityTicking(cow.blockPosition()), "the target starts outside native ticking");
        }).thenExecute(() -> {
            helper.assertTrue(level.addFreshEntity(cow), "the actual target enters frozen native storage");
            fixture.town().recordHuntingTarget(SettlementHuntingTarget.capture(cow, level.getGameTime()));
            SettlementBackgroundHunting.tick(level, fixture.town(), fixture.worker(), 200, 1.0D);
            helper.assertTrue(cow.invulnerableTime > 0, "the actual background strike starts native hit immunity");
        }).thenIdle(40).thenExecute(() -> {
            SettlementHuntingTargetService.update(level, fixture.town(), level.getGameTime());
            level.getChunkSource().addRegionTicket(TicketType.FORCED, chunk, 2, chunk);
        }).thenWaitUntil(() -> {
            helper.assertTrue(level.isPositionEntityTicking(cow.blockPosition()), "the actual target becomes native active");
            helper.assertValueEqual(cow.invulnerableTime, 0, "native tick reconciliation expires the old frozen immunity");
        }).thenExecute(() -> {
            helper.assertTrue(cow.hurt(level.damageSources().mobAttack(fixture.villager()), 2.0F),
                    "the next ordinary native hit succeeds after recovery");
        }).thenIdle(1).thenExecute(() -> {
            try {
                helper.assertTrue(cow.invulnerableTime >= 18, "subsequent native ticks preserve the new hit's immunity");
                helper.assertFalse(cow.hurt(level.damageSources().mobAttack(fixture.villager()), 1.0F),
                        "newer native damage still uses native immunity");
            } finally {
                cow.discard();
                level.getChunkSource().removeRegionTicket(TicketType.FORCED, chunk, 2, chunk);
                level.getChunkSource().removeRegionTicket(TicketType.FORCED, chunk, 0, chunk);
            }
        }).thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_hunting_handoff")
    public static void anOwnedNativeHuntSurvivesWorkerSavingAndContinuesAgainstItsTarget(GameTestHelper helper) {
        var fixture = fixture(helper);
        var target = cow(helper, fixture);
        var closer = cow(helper, fixture);
        closer.setPos(fixture.villager().position().add(0.3D, 0.0D, 0.0D));
        engage(helper, fixture, target);
        fixture.villager().setLastHurtByMob(target);
        fixture.worker().observe(helper.getLevel(), fixture.villager(), new SettlementWorkforceService.Duty(fixture.town(), fixture.area()), 0L);
        helper.assertFalse(fixture.worker().unsafe(), "the hunter's own valid engagement does not freeze background work");
        var saved = SettlementRecord.load(fixture.town().save(helper.getLevel().registryAccess()), helper.getLevel().registryAccess(), () -> {});
        var worker = saved.workers().get(fixture.worker().id());
        helper.assertTrue(worker.huntingEngaged(), "the ongoing native fight survives saving");
        helper.assertValueEqual(worker.huntingTarget(), target.getUUID(), "the selected opponent survives saving");
        saved.recordHuntingTarget(SettlementHuntingTarget.capture(closer, helper.getLevel().getGameTime()));
        SettlementBackgroundHunting.tick(helper.getLevel(), saved, worker, 200, 1.0D);
        helper.assertTrue(saved.huntingTargets().get(target.getUUID()).context(helper.getLevel()).getHealth() < 10.0F,
                "the resumed native fight performs actual melee damage");
        helper.assertValueEqual(saved.huntingTargets().get(closer.getUUID()).context(helper.getLevel()).getHealth(), 10.0F,
                "a nearer animal cannot steal an ongoing engagement");
        com.jvn.villagerretaliation.combat.VillagerRetaliationHandler.clearCustomTarget(fixture.villager());
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_hunting_handoff")
    public static void unrelatedDangerStillStopsAnOwnedHunt(GameTestHelper helper) {
        var fixture = fixture(helper);
        var target = cow(helper, fixture);
        var attacker = cow(helper, fixture);
        engage(helper, fixture, target);
        fixture.villager().setLastHurtByMob(attacker);
        var duty = new SettlementWorkforceService.Duty(fixture.town(), fixture.area());
        fixture.worker().observe(helper.getLevel(), fixture.villager(), duty, 0L);
        helper.assertTrue(fixture.worker().unsafe(), "an unrelated attacker keeps the existing safety stop");
        fixture.villager().setLastHurtByMob(null);
        fixture.villager().setRemainingFireTicks(40);
        fixture.worker().observe(helper.getLevel(), fixture.villager(), duty, 0L);
        helper.assertTrue(fixture.worker().unsafe(), "an owned target cannot waive fire danger");
        fixture.villager().clearFire();
        HiredWorkStateStore.state(fixture.villager()).remove("HuntingActiveTarget");
        fixture.worker().observe(helper.getLevel(), fixture.villager(), duty, 0L);
        helper.assertTrue(fixture.worker().unsafe(), "an unowned combat target cannot become background hunting");
        com.jvn.villagerretaliation.combat.VillagerRetaliationHandler.clearCustomTarget(fixture.villager());
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_hunting_handoff")
    public static void changedSelectionAndBoundsCannotAuthorizeHuntContinuation(GameTestHelper helper) {
        var fixture = fixture(helper);
        var target = cow(helper, fixture);
        engage(helper, fixture, target);
        var duty = new SettlementWorkforceService.Duty(fixture.town(), fixture.area());
        var settings = HiredWorkStateStore.state(fixture.villager());
        settings.putBoolean("HuntAnimals", false);
        fixture.worker().observe(helper.getLevel(), fixture.villager(), duty, 0L);
        helper.assertTrue(fixture.worker().unsafe() && !fixture.worker().huntingEngaged(), "disabled prey categories revoke continuation");
        settings.putBoolean("HuntAnimals", true);
        target.setPos(fixture.area().max().east(3).getBottomCenter());
        fixture.worker().observe(helper.getLevel(), fixture.villager(), duty, 0L);
        helper.assertTrue(fixture.worker().unsafe() && !fixture.worker().huntingEngaged(), "an opponent outside the hunting grounds cannot authorize continuation");
        com.jvn.villagerretaliation.combat.VillagerRetaliationHandler.clearCustomTarget(fixture.villager());
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_hunting_handoff")
    public static void targetAndHunterUnloadOrderPreservesTheOwnedFight(GameTestHelper helper) {
        var fixture = fixture(helper);
        var target = cow(helper, fixture);
        engage(helper, fixture, target);
        target.setRemoved(net.minecraft.world.entity.Entity.RemovalReason.UNLOADED_TO_CHUNK);
        fixture.villager().setRemoved(net.minecraft.world.entity.Entity.RemovalReason.UNLOADED_TO_CHUNK);
        fixture.worker().observe(helper.getLevel(), fixture.villager(), new SettlementWorkforceService.Duty(fixture.town(), fixture.area()), 0L);
        helper.assertFalse(fixture.worker().unsafe(), "chunk removal does not make an otherwise valid owned fight unsafe");
        helper.assertTrue(fixture.worker().huntingEngaged(), "the earlier target unload keeps the native engagement");
        helper.assertTrue(fixture.town().huntingTargets().containsKey(target.getUUID()), "the final unload captures the target body");
        com.jvn.villagerretaliation.combat.VillagerRetaliationHandler.clearCustomTarget(fixture.villager());
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_hunting_handoff")
    public static void nativeAcquisitionRecordsOwnershipBeforeItsFirstCombatTick(GameTestHelper helper) {
        var fixture = fixture(helper, true);
        var target = cow(helper, fixture);
        var level = helper.getLevel();
        helper.assertTrue(level.addFreshEntity(fixture.villager()) && level.addFreshEntity(target), "the native hunt has actual active bodies");
        try {
            new com.jvn.villagerretaliation.interaction.work.HuntingWorker().tick(level, fixture.villager(), null, context(fixture));
            helper.assertTrue(com.jvn.villagerretaliation.interaction.work.HuntingWorker.activeTarget(fixture.villager()).orElse(null) == target,
                    "native target acquisition immediately records its own engagement");
            fixture.worker().observe(level, fixture.villager(), new SettlementWorkforceService.Duty(fixture.town(), fixture.area()), level.getGameTime());
            helper.assertTrue(fixture.worker().huntingEngaged(), "an immediate unload can preserve the newly acquired hunt");
        } finally {
            com.jvn.villagerretaliation.combat.VillagerRetaliationHandler.clearCustomTarget(fixture.villager());
            fixture.villager().discard();
            target.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 300, batch = "settlement_hunting_native_damage_chunks")
    public static void nativeDamageBeforeTickingReconcilesBothTargetAndHunter(GameTestHelper helper) {
        var fixture = fixture(helper);
        var level = helper.getLevel();
        var target = cow(helper, fixture);
        var chunk = new ChunkPos(fixture.town().center());
        fixture.worker().settings().putBoolean("Enabled", false);
        level.getChunkSource().addRegionTicket(TicketType.FORCED, chunk, 0, chunk);
        level.getChunk(chunk.x, chunk.z);
        helper.startSequence().thenWaitUntil(() -> {
            helper.assertTrue(level.areEntitiesLoaded(chunk.toLong()), "the bodies enter loaded native storage");
            helper.assertFalse(level.isPositionEntityTicking(target.blockPosition()), "native ticking is still suspended");
        }).thenExecute(() -> {
            try {
                helper.assertTrue(level.addFreshEntity(target) && level.addFreshEntity(fixture.villager()), "both actual frozen bodies are present");
                long now = level.getGameTime();
                var state = SettlementHuntingTarget.capture(target, now - 40L);
                var before = target.saveWithoutId(new CompoundTag());
                target.setHealth(8.0F);
                clock(target, 20, 10, 2.0F);
                state.interacted(target, before, now - 40L);
                fixture.town().recordHuntingTarget(state);
                helper.assertTrue(target.hurt(level.damageSources().mobAttack(fixture.villager()), 1.0F),
                        "a native attack before the first tick consumes the expired pending immunity");
                helper.assertValueEqual(target.getHealth(), 7.0F, "the native hit builds on the actual earlier injury");
                helper.assertValueEqual(target.invulnerableTime, 20, "the new native hit starts its own immunity");
                helper.assertFalse(target.hurt(level.damageSources().mobAttack(fixture.villager()), 1.0F),
                        "reconciliation cannot replace the new native hit with the older clock");
                helper.assertValueEqual(fixture.town().huntingTargets().get(target.getUUID()).context(level).getHealth(), 7.0F,
                        "later background work sees the newer native injury");
                SettlementHuntingTarget.resumeNativeTimers(target, now + 7L);
                helper.assertValueEqual(target.invulnerableTime, 13, "a rejected native hit cannot discard the frozen body's pending clock");
                var owner = fixture.worker().context(level, fixture.town());
                before = owner.saveWithoutId(new CompoundTag());
                owner.setHealth(17.0F);
                clock(owner, 20, 10, 3.0F);
                fixture.town().residents().get(owner.getUUID()).combat(owner);
                fixture.worker().huntingInjury(owner, before, now - 40L);
                helper.assertTrue(fixture.villager().hurt(level.damageSources().mobAttack(target), 2.0F),
                        "a hit on the frozen hunter first restores its background injury and elapsed immunity");
                helper.assertValueEqual(fixture.villager().getHealth(), 15.0F, "the native hit cannot erase the background injury");
                helper.assertValueEqual(fixture.town().residents().get(owner.getUUID()).health(), 15.0F,
                        "care records the fresh native damage before another background interval");
                helper.assertValueEqual(fixture.worker().context(level, fixture.town()).getHealth(), 15.0F,
                        "the worker context cannot heal the fresh native damage");
                helper.assertTrue(fixture.worker().unsafe(), "an unrelated native attack still stops further background work");
            } finally {
                fixture.villager().discard();
                target.discard();
                level.getChunkSource().removeRegionTicket(TicketType.FORCED, chunk, 0, chunk);
            }
        }).thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_hunting_handoff")
    public static void anOngoingFightTakesPriorityOverNearbyLoot(GameTestHelper helper) {
        var fixture = fixture(helper);
        var target = cow(helper, fixture);
        engage(helper, fixture, target);
        fixture.worker().observe(helper.getLevel(), fixture.villager(), new SettlementWorkforceService.Duty(fixture.town(), fixture.area()), 0L);
        var item = new net.minecraft.world.entity.item.ItemEntity(helper.getLevel(),
                fixture.villager().getX(), fixture.villager().getY(), fixture.villager().getZ(), new ItemStack(Items.BONE, 2));
        var drop = SettlementItemDrop.create(item, fixture.area().id(), helper.getLevel().getGameTime());
        drop.access(fixture.worker(), 0);
        fixture.town().recordItemDrop(drop);
        SettlementBackgroundHunting.tick(helper.getLevel(), fixture.town(), fixture.worker(), 200, 1.0D);
        helper.assertTrue(fixture.town().huntingTargets().get(target.getUUID()).context(helper.getLevel()).getHealth() < 10.0F,
                "an ongoing fight continues before collecting reachable loot");
        helper.assertTrue(fixture.town().itemDrops().containsKey(drop.id()), "the finite nearby loot remains available after combat");
        helper.assertTrue(fixture.worker().items(helper.getLevel().registryAccess()).stream().noneMatch(stack -> stack.is(Items.BONE)),
                "loot collection cannot consume the same combat interval");
        com.jvn.villagerretaliation.combat.VillagerRetaliationHandler.clearCustomTarget(fixture.villager());
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 600, batch = "settlement_hunting_unload")
    public static void aNativeHuntContinuesAfterItsActualChunkUnloads(GameTestHelper helper) {
        var fixture = fixture(helper);
        var level = helper.getLevel();
        var target = cow(helper, fixture);
        target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(1000.0D);
        target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0D);
        target.setHealth(1000.0F);
        var chunk = new ChunkPos(fixture.town().center());
        var health = new float[1];
        level.getChunkSource().addRegionTicket(TicketType.FORCED, chunk, 2, chunk);
        level.getChunk(chunk.x, chunk.z);
        helper.startSequence().thenWaitUntil(() -> {
            helper.assertTrue(level.areEntitiesLoaded(chunk.toLong()) && level.isPositionEntityTicking(target.blockPosition()),
                    "the actual native hunt starts in a ticking chunk");
        }).thenExecute(() -> {
            helper.assertTrue(level.addFreshEntity(target) && level.addFreshEntity(fixture.villager()), "the native hunter and target are present");
            new com.jvn.villagerretaliation.interaction.work.HuntingWorker().tick(level, fixture.villager(), null, context(fixture));
            helper.assertTrue(com.jvn.villagerretaliation.interaction.work.HuntingWorker.activeTarget(fixture.villager()).orElse(null) == target,
                    "the native hunting worker owns the acquired target");
            level.getChunkSource().removeRegionTicket(TicketType.FORCED, chunk, 2, chunk);
        }).thenWaitUntil(() -> {
            helper.assertFalse(level.areEntitiesLoaded(chunk.toLong()), "the native entity section finishes unloading");
            helper.assertTrue(level.getEntity(fixture.villager().getUUID()) == null && level.getEntity(target.getUUID()) == null,
                    "neither combatant remains in native entity storage");
        }).thenExecute(() -> {
            var worker = fixture.town().workers().get(fixture.worker().id());
            helper.assertTrue(worker != null && worker.huntingEngaged() && !worker.unsafe(),
                    "the real unload callback preserves an eligible owned hunt: worker=" + (worker == null ? "missing" : worker.status())
                            + ", engaged=" + (worker != null && worker.huntingEngaged()) + ", unsafe=" + (worker != null && worker.unsafe())
                            + ", request=" + (worker == null ? null : worker.huntingTarget()) + ", actorTarget=" + fixture.villager().getTarget()
                            + ", marker=" + HiredWorkStateStore.state(fixture.villager()).hasUUID("HuntingActiveTarget")
                            + ", actorRemoval=" + fixture.villager().getRemovalReason() + ", targetRemoval=" + target.getRemovalReason()
                            + ", targetHealth=" + target.getHealth() + ", targetPosition=" + target.blockPosition());
            worker.settings().putBoolean("Enabled", false);
            health[0] = fixture.town().huntingTargets().get(target.getUUID()).context(level).getHealth();
        }).thenIdle(40).thenExecute(() -> {
            try {
                var worker = fixture.town().workers().get(fixture.worker().id());
                SettlementBackgroundHunting.tick(level, fixture.town(), worker, 40, 1.0D);
                helper.assertTrue(fixture.town().huntingTargets().get(target.getUUID()).context(level).getHealth() < health[0],
                        "the actually unloaded hunt continues dealing native melee damage");
                helper.assertFalse(level.hasChunkAt(target.blockPosition()), "continuation cannot reload the native terrain");
            } finally {
                fixture.town().removeDuty(fixture.worker().id());
                level.getChunkSource().removeRegionTicket(TicketType.FORCED, chunk, 2, chunk);
            }
        }).thenSucceed();
    }

    private static void engage(GameTestHelper helper, Fixture fixture, Cow target) {
        helper.assertTrue(com.jvn.villagerretaliation.combat.VillagerRetaliationHandler.engageCustomTarget(fixture.villager(), target, false),
                "the native combat policy permits the work target");
        fixture.villager().setTarget(target);
        HiredWorkStateStore.state(fixture.villager()).putUUID("HuntingActiveTarget", target.getUUID());
    }

    private static com.jvn.villagerretaliation.interaction.work.HiredWorkContext context(Fixture fixture) {
        return new com.jvn.villagerretaliation.interaction.work.HiredWorkContext(
                HiredJobInventory.getJobInventory(fixture.villager()), HiredWorkStateStore.state(fixture.villager()),
                fixture.town().center(), fixture.area().min(), fixture.area().max(), 12, 6, true, 100, false, true);
    }

    private static void clock(net.minecraft.world.entity.LivingEntity entity, int invulnerable, int hurt, float damage) {
        entity.invulnerableTime = invulnerable;
        entity.hurtTime = hurt;
        ((LivingEntityCombatAccessor) entity).villagerretaliation$setLastHurt(damage);
    }

    private static Fixture fixture(GameTestHelper helper) {
        return fixture(helper, false);
    }

    private static Fixture fixture(GameTestHelper helper, boolean loaded) {
        var level = helper.getLevel();
        var origin = helper.absolutePos(new BlockPos(2, 2, 2));
        var center = loaded ? origin : origin.offset(18_000_000, 0, 18_000_000).atY(80);
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
        SettlementExperienceTestTerrain.flat(town);
        return new Fixture(town, area, worker, villager);
    }

    private static Cow cow(GameTestHelper helper, Fixture fixture) {
        var cow = EntityType.COW.create(helper.getLevel());
        cow.setPos(fixture.town().center().east().getBottomCenter());
        cow.setNoAi(true);
        cow.setNoGravity(true);
        return cow;
    }

    private record Fixture(SettlementRecord town, SettlementArea area, SettlementWorkerState worker, Villager villager) {
    }
}
