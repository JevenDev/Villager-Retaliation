package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.AllegianceAssignmentSource;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceApi;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.combat.VillagerRangedCombatHelper;
import com.jvn.villagerretaliation.interaction.HiredWorkStateStore;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class SettlementRangedHuntingGameTests {
    private SettlementRangedHuntingGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_ranged_hunting")
    public static void unloadedBowHunterFiresPaidNativeArrowsAndInjuresPrey(GameTestHelper helper) {
        var fixture = fixture(helper, Items.BOW, 8);
        var hits = new AtomicInteger();
        Consumer<LivingIncomingDamageEvent> listener = event -> {
            if (event.getEntity().getUUID().equals(fixture.target().getUUID())
                    && event.getSource().getDirectEntity() instanceof AbstractArrow) hits.incrementAndGet();
        };
        NeoForge.EVENT_BUS.addListener(listener);
        try {
            hunt(helper, fixture);
            int arrows = ammo(fixture.worker().context(helper.getLevel(), fixture.town()));
            int wear = fixture.worker().items(helper.getLevel().registryAccess()).get(HiredJobInventory.MAINHAND_SLOT).getDamageValue();
            helper.assertTrue(arrows < 8 && arrows > 0, "the running interval emits paid shots at native draw and recovery intervals");
            helper.assertValueEqual(wear, 8 - arrows, "each real bow shot spends one arrow and one durability");
            helper.assertTrue(hits.get() > 0, "physical flight reaches prey through native arrow damage");
            var target = fixture.town().huntingTargets().get(fixture.target().getUUID()).context(helper.getLevel());
            helper.assertTrue(target.getHealth() < 500.0F && target.getHealth() > 0.0F, "native ranged damage reaches the observed prey");
            helper.assertValueEqual(fixture.target().getHealth(), 500.0F, "the stored native body waits for reconciliation");
            helper.assertFalse(helper.getLevel().hasChunkAt(fixture.town().center()), "shooting and flight do not load the remote town");
            float health = target.getHealth();
            hunt(helper, fixture);
            helper.assertValueEqual(fixture.town().huntingTargets().get(fixture.target().getUUID()).context(helper.getLevel()).getHealth(),
                    health, "a repeated running interval cannot replay its projectile hits");
        } finally {
            NeoForge.EVENT_BUS.unregister(listener);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_ranged_hunting")
    public static void theLastReservedCrossbowArrowCanFireWithoutAnotherSupply(GameTestHelper helper) {
        var fixture = fixture(helper, Items.CROSSBOW, 1);
        var terrain = new SettlementExperienceTerrain(helper.getLevel(), fixture.town());
        for (int tick = 0; tick < 5; tick++) VillagerRangedCombatHelper.attackObserved(fixture.owner(), fixture.target(), terrain, speed -> {});
        helper.assertTrue(fixture.owner().isUsingItem(), "the native crossbow reserves its last arrow");
        helper.assertValueEqual(ammo(fixture.owner()), 0, "the reserved arrow is already paid");
        var context = new com.jvn.villagerretaliation.interaction.work.HiredWorkContext(
                HiredJobInventory.getJobInventory(fixture.owner()), HiredWorkStateStore.state(fixture.owner()), fixture.town().center(),
                fixture.area().min(), fixture.area().max(), 14, 14, true, 100, false, true);
        helper.assertFalse(com.jvn.villagerretaliation.interaction.work.HiredRangedAmmo.isRangedAttackBlockedByAmmo(fixture.owner()),
                "native hunting and combat target maintenance recognize the paid charge");
        helper.assertTrue(com.jvn.villagerretaliation.interaction.work.HiredRangedAmmo.hasAmmoForEquippedWeapon(context, fixture.owner()),
                "the native work context recognizes the same reserved ammunition");
        helper.assertTrue(com.jvn.villagerretaliation.interaction.work.HiredRangedAmmo.ensureReady(
                helper.getLevel(), fixture.owner(), context, 0.5D) == null, "native supply readiness does not interrupt a paid charge");
        fixture.worker().observe(helper.getLevel(), fixture.owner(), new SettlementWorkforceService.Duty(fixture.town(), fixture.area()), 0L);
        hunt(helper, fixture);
        var owner = fixture.worker().context(helper.getLevel(), fixture.town());
        helper.assertValueEqual(ammo(owner), 0, "the shot cannot consume a second arrow");
        helper.assertValueEqual(owner.getMainHandItem().getDamageValue(), 1, "the paid charge emits a native shot despite empty supplies");
        helper.assertFalse(owner.isUsingItem(), "the restored charge finishes");
        helper.assertFalse(helper.getLevel().hasChunkAt(fixture.town().center()), "the crossbow cycle never requests the remote chunk");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_ranged_hunting")
    public static void observedFreeFlightMatchesNativeArrowPositionAndVelocity(GameTestHelper helper) {
        var fixture = fixture(helper, Items.BOW, 2);
        var arrow = arrow(helper, fixture, fixture.owner().position().add(0.0D, 12.0D, 0.0D), new Vec3(0.65D, 0.22D, 0.03D));
        var state = SettlementProjectile.launched(arrow, fixture.worker(), 0L);
        var simulation = new SettlementProjectileSimulation(helper.getLevel(), fixture.town(), state, fixture.owner(), null);
        var actual = EntityType.ARROW.create(helper.getLevel());
        actual.load(arrow.saveWithoutId(new CompoundTag()));
        var origin = helper.absolutePos(new BlockPos(2, 12, 2)).getCenter();
        actual.setPos(origin);
        var remoteOrigin = arrow.position();
        for (int tick = 1; tick <= 10; tick++) {
            actual.tick();
            helper.assertTrue(simulation.step(arrow, tick), "ordinary observed air supports flight");
            helper.assertTrue(arrow.position().subtract(remoteOrigin).distanceTo(actual.position().subtract(origin)) < 1.0E-7D,
                    "background and native displacement match on every flight frame");
            helper.assertTrue(arrow.getDeltaMovement().distanceTo(actual.getDeltaMovement()) < 1.0E-10D,
                    "native drag and gravity match on every flight frame");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_ranged_hunting")
    public static void physicalImpactUsesNativeDamageAndReconcilesOnce(GameTestHelper helper) {
        var fixture = fixture(helper, Items.BOW, 2);
        var arrow = arrow(helper, fixture, fixture.target().position().add(-2.0D, 0.65D, 0.0D), new Vec3(2.0D, 0.0D, 0.0D));
        arrow.setBaseDamage(3.0D);
        var state = SettlementProjectile.launched(arrow, fixture.worker(), 0L);
        var target = fixture.town().huntingTargets().get(fixture.target().getUUID()).context(helper.getLevel());
        var simulation = new SettlementProjectileSimulation(helper.getLevel(), fixture.town(), state, fixture.owner(), target);
        helper.assertTrue(simulation.step(arrow, helper.getLevel().getGameTime()), "the collision segment is observed");
        helper.assertTrue(arrow.isRemoved(), "a successful ordinary arrow impact consumes its projectile");
        helper.assertValueEqual(target.getHealth(), 494.0F, "native arrow speed and base damage determine the injury");
        var saved = SettlementRecord.load(fixture.town().save(helper.getLevel().registryAccess()), helper.getLevel().registryAccess(), () -> {});
        var injury = saved.huntingTargets().get(target.getUUID());
        helper.assertTrue(injury.restore(fixture.target(), helper.getLevel().getGameTime()), "the native body receives the projectile injury");
        helper.assertValueEqual(fixture.target().getHealth(), 494.0F, "the saved impact is exact");
        helper.assertFalse(injury.restore(fixture.target(), helper.getLevel().getGameTime()), "a returning body cannot receive the same hit twice");
        helper.assertFalse(helper.getLevel().hasChunkAt(fixture.town().center()), "native damage callbacks do not load remote terrain");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_ranged_hunting")
    public static void canceledProjectileImpactPreservesPreyAndContinuesFlight(GameTestHelper helper) {
        var fixture = fixture(helper, Items.BOW, 2);
        var arrow = arrow(helper, fixture, fixture.target().position().add(-2.0D, 0.65D, 0.0D), new Vec3(2.0D, 0.0D, 0.0D));
        var state = SettlementProjectile.launched(arrow, fixture.worker(), 0L);
        var target = fixture.town().huntingTargets().get(fixture.target().getUUID()).context(helper.getLevel());
        Consumer<ProjectileImpactEvent> cancel = event -> {
            if (event.getProjectile() == arrow) event.setCanceled(true);
        };
        NeoForge.EVENT_BUS.addListener(cancel);
        var start = arrow.position();
        try {
            var simulation = new SettlementProjectileSimulation(helper.getLevel(), fixture.town(), state, fixture.owner(), target);
            helper.assertTrue(simulation.step(arrow, helper.getLevel().getGameTime()), "a canceled hit still completes native flight");
            helper.assertValueEqual(target.getHealth(), 500.0F, "the native impact hook prevents damage");
            helper.assertFalse(arrow.isRemoved(), "cancellation preserves the projectile");
            helper.assertTrue(arrow.position().distanceTo(start) > 1.99D, "the canceled collision does not freeze flight");
        } finally {
            NeoForge.EVENT_BUS.unregister(cancel);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_ranged_hunting")
    public static void aPendingFlightKeepsIdentityAndMotionAcrossSettlementSave(GameTestHelper helper) {
        var fixture = fixture(helper, Items.BOW, 2);
        var arrow = arrow(helper, fixture, fixture.owner().position().add(0.0D, 4.0D, 0.0D), new Vec3(0.8D, 0.0D, 0.0D));
        SettlementProjectileService.launched(fixture.town(), fixture.worker(), arrow, 0L);
        var state = fixture.town().projectiles().get(arrow.getUUID());
        var simulation = new SettlementProjectileSimulation(helper.getLevel(), fixture.town(), state, fixture.owner(), null);
        helper.assertTrue(simulation.step(arrow, 1L), "the first physical frame advances");
        state.advance(arrow, 1L);
        var saved = SettlementRecord.load(fixture.town().save(helper.getLevel().registryAccess()), helper.getLevel().registryAccess(), () -> {});
        var restored = saved.projectiles().get(arrow.getUUID());
        var body = restored.context(helper.getLevel());
        helper.assertValueEqual(body.getUUID(), arrow.getUUID(), "the shot keeps its unique native identity");
        helper.assertValueEqual(body.position(), arrow.position(), "saving preserves the in-flight position");
        helper.assertValueEqual(body.getDeltaMovement(), arrow.getDeltaMovement(), "saving preserves native flight velocity");
        helper.assertTrue(restored.pendingSpawn(), "the saved shot remains pending until entity storage is loaded");
        helper.assertTrue(restored.restore(arrow), "the flight can reconcile to an older native version");
        helper.assertFalse(restored.restore(arrow), "a reconciled flight version cannot repeat");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_ranged_hunting")
    public static void projectileCaptureRunsAfterTheNativeSpawnVeto(GameTestHelper helper) {
        var fixture = fixture(helper, Items.BOW, 2);
        var terrain = new SettlementExperienceTerrain(helper.getLevel(), fixture.town());
        var joins = new AtomicInteger();
        Consumer<EntityJoinLevelEvent> cancel = event -> {
            if (event.getEntity() instanceof AbstractArrow arrow && arrow.getOwner() == fixture.owner()) {
                joins.incrementAndGet();
                event.setCanceled(true);
            }
        };
        NeoForge.EVENT_BUS.addListener(cancel);
        try (var capture = new SettlementProjectileCapture(fixture.owner())) {
            for (int tick = 0; tick < 21; tick++) VillagerRangedCombatHelper.attackObserved(fixture.owner(), fixture.target(), terrain, speed -> {});
            helper.assertValueEqual(joins.get(), 1, "the actual native launcher dispatches its spawn event");
            helper.assertTrue(capture.projectiles().isEmpty(), "a vetoed native spawn cannot enter background storage");
            helper.assertValueEqual(ammo(fixture.owner()), 1, "the native firing attempt keeps its ordinary ammo cost");
        } finally {
            NeoForge.EVENT_BUS.unregister(cancel);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_ranged_hunting")
    public static void capturedLaunchNeverEntersNativeEntityStorage(GameTestHelper helper) {
        var fixture = fixture(helper, Items.BOW, 2);
        var terrain = new SettlementExperienceTerrain(helper.getLevel(), fixture.town());
        try (var capture = new SettlementProjectileCapture(fixture.owner())) {
            for (int tick = 0; tick < 21; tick++) VillagerRangedCombatHelper.attackObserved(fixture.owner(), fixture.target(), terrain, speed -> {});
            helper.assertValueEqual(capture.projectiles().size(), 1, "the native bow emits one accepted projectile");
            var projectile = capture.projectiles().getFirst();
            helper.assertTrue(helper.getLevel().getEntity(projectile.getUUID()) == null, "only the background ledger owns the native shot");
            helper.assertTrue(projectile.getDeltaMovement().lengthSqr() > 0.0D, "the launcher retains real flight velocity");
            helper.assertFalse(helper.getLevel().hasChunkAt(projectile.blockPosition()), "capturing the shot cannot create an entity-storage chunk");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_ranged_hunting")
    public static void aMissEmbedsInObservedStoneAndKeepsItsNativeDespawnTimer(GameTestHelper helper) {
        var fixture = fixture(helper, Items.BOW, 2);
        var arrow = arrow(helper, fixture, fixture.owner().position().add(0.0D, 0.5D, 2.0D), new Vec3(0.0D, -1.0D, 0.0D));
        var state = SettlementProjectile.launched(arrow, fixture.worker(), 0L);
        var simulation = new SettlementProjectileSimulation(helper.getLevel(), fixture.town(), state, fixture.owner(), null);
        helper.assertTrue(simulation.step(arrow, 1L), "the observed ordinary block accepts the native impact");
        var access = (com.jvn.villagerretaliation.mixin.AbstractArrowAccessor) arrow;
        helper.assertTrue(access.villagerretaliation$isInGround(), "the native block impact embeds the arrow");
        for (int tick = 2; tick <= 601; tick++) simulation.step(arrow, tick);
        state.advance(arrow, 601L);
        var saved = SettlementProjectile.load(state.save());
        var restored = (Arrow) saved.context(helper.getLevel());
        helper.assertValueEqual(((com.jvn.villagerretaliation.mixin.AbstractArrowAccessor) restored).villagerretaliation$inGroundTime(),
                600, "grounded native age survives a saved background flight");
        var resumed = new SettlementProjectileSimulation(helper.getLevel(), fixture.town(), saved, fixture.owner(), null);
        for (int tick = 602; tick < 1201; tick++) resumed.step(restored, tick);
        helper.assertFalse(restored.isRemoved(), "the native arrow remains until its full embedded lifetime");
        resumed.step(restored, 1201L);
        helper.assertTrue(restored.isRemoved(), "the native despawn callback removes the spent projectile on time");
        helper.assertFalse(helper.getLevel().hasChunkAt(fixture.town().center()), "block impact and aging never load the observed chunk");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_ranged_hunting")
    public static void aLethalArrowCreatesNativeDropsAndOneSavedDeath(GameTestHelper helper) {
        var fixture = fixture(helper, Items.BOW, 2);
        fixture.target().setHealth(1.0F);
        fixture.town().recordHuntingTarget(SettlementHuntingTarget.capture(fixture.target(), 0L));
        var target = fixture.town().huntingTargets().get(fixture.target().getUUID()).context(helper.getLevel());
        var arrow = arrow(helper, fixture, target.position().add(-2.0D, 0.65D, 0.0D), new Vec3(2.0D, 0.0D, 0.0D));
        var state = SettlementProjectile.launched(arrow, fixture.worker(), 0L);
        var simulation = new SettlementProjectileSimulation(helper.getLevel(), fixture.town(), state, fixture.owner(), target);
        helper.assertTrue(simulation.step(arrow, helper.getLevel().getGameTime()), "the lethal shot completes its physical flight frame");
        helper.assertFalse(target.isAlive(), "native projectile damage kills the observed prey");
        helper.assertTrue(fixture.town().entityDeaths().containsKey(target.getUUID()), "the death waits for native reconciliation");
        helper.assertFalse(fixture.town().huntingTargets().containsKey(target.getUUID()), "dead prey cannot be selected again");
        helper.assertTrue(fixture.town().itemDrops().values().stream().anyMatch(drop -> drop.item(helper.getLevel().registryAccess()).is(Items.BEEF)),
                "native cow loot enters the finite pending drop ledger");
        var saved = SettlementRecord.load(fixture.town().save(helper.getLevel().registryAccess()), helper.getLevel().registryAccess(), () -> {});
        helper.assertValueEqual(saved.entityDeaths().size(), 1, "saving retains exactly one native death");
        helper.assertFalse(helper.getLevel().hasChunkAt(fixture.town().center()), "native death and loot callbacks do not load the town");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_ranged_hunting")
    public static void theLastBowArrowIsReturnedByInventoryBeforeItsStackBecomesEmpty(GameTestHelper helper) {
        var fixture = fixture(helper, Items.BOW, 1);
        var terrain = new SettlementExperienceTerrain(helper.getLevel(), fixture.town());
        try (var capture = new SettlementProjectileCapture(fixture.owner())) {
            for (int tick = 0; tick < 21; tick++) VillagerRangedCombatHelper.attackObserved(fixture.owner(), fixture.target(), terrain, speed -> {});
            helper.assertValueEqual(capture.projectiles().size(), 1, "the final physical supply item reaches the native bow launcher");
            helper.assertValueEqual(ammo(fixture.owner()), 0, "the one arrow is consumed exactly once");
            helper.assertValueEqual(fixture.owner().getMainHandItem().getDamageValue(), 1, "the paid final shot applies native weapon wear");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_ranged_hunting")
    public static void aRejectedSlowArrowReturnsItsPickupThroughThePendingItemLedger(GameTestHelper helper) {
        var fixture = fixture(helper, Items.BOW, 2);
        var target = fixture.town().huntingTargets().get(fixture.target().getUUID()).context(helper.getLevel());
        var start = new Vec3(target.getBoundingBox().minX - (double) 0.3F - 0.00001D, target.getY() + 0.65D, target.getZ());
        var arrow = arrow(helper, fixture, start, new Vec3(0.0001D, 0.0D, 0.0D));
        arrow.pickup = AbstractArrow.Pickup.ALLOWED;
        var state = SettlementProjectile.launched(arrow, fixture.worker(), 0L);
        Consumer<LivingIncomingDamageEvent> cancel = event -> {
            if (event.getEntity() == target) event.setCanceled(true);
        };
        NeoForge.EVENT_BUS.addListener(cancel);
        try {
            var simulation = new SettlementProjectileSimulation(helper.getLevel(), fixture.town(), state, fixture.owner(), target);
            helper.assertTrue(simulation.step(arrow, helper.getLevel().getGameTime()), "the slow collision executes native rejection behavior");
            helper.assertTrue(arrow.isRemoved(), "native deflection retires a projectile with negligible remaining speed");
            helper.assertValueEqual(fixture.town().itemDrops().size(), 1, "the native arrow pickup becomes one pending drop");
            var drop = fixture.town().itemDrops().values().iterator().next();
            helper.assertTrue(drop.item(helper.getLevel().registryAccess()).is(Items.ARROW), "the exact native pickup item is retained");
            helper.assertValueEqual(drop.item(helper.getLevel().registryAccess()).getCount(), 1, "one spent arrow produces one pickup");
            helper.assertTrue(helper.getLevel().getEntity(drop.id()) == null, "the background pickup never enters native entity storage early");
        } finally {
            NeoForge.EVENT_BUS.unregister(cancel);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_ranged_hunting")
    public static void piercingRemembersVictimIdentityAcrossNewNativeEntityIds(GameTestHelper helper) {
        var fixture = fixture(helper, Items.BOW, 2);
        var second = EntityType.COW.create(helper.getLevel());
        second.setPos(fixture.target().position().add(2.0D, 0.0D, 0.0D));
        second.getAttribute(Attributes.MAX_HEALTH).setBaseValue(500.0D);
        second.setHealth(500.0F);
        fixture.town().recordHuntingTarget(SettlementHuntingTarget.capture(second, 0L));
        var target = fixture.town().huntingTargets().get(fixture.target().getUUID()).context(helper.getLevel());
        var arrow = arrow(helper, fixture, target.position().add(-2.0D, 0.65D, 0.0D), new Vec3(6.0D, 0.0D, 0.0D));
        var charged = arrow.saveWithoutId(new CompoundTag());
        charged.putByte("PierceLevel", (byte) 1);
        arrow.load(charged);
        arrow.setOwner(fixture.owner());
        var state = SettlementProjectile.launched(arrow, fixture.worker(), 0L);
        var simulation = new SettlementProjectileSimulation(helper.getLevel(), fixture.town(), state, fixture.owner(), target);
        helper.assertTrue(simulation.step(arrow, helper.getLevel().getGameTime()), "one native piercing trajectory crosses both victims");
        helper.assertValueEqual(SettlementProjectileSimulation.pierced(arrow).size(), 2, "native piercing records both victim UUIDs");
        state.advance(arrow, helper.getLevel().getGameTime());
        var saved = SettlementProjectile.load(state.save());
        var restored = saved.context(helper.getLevel());
        var newBody = fixture.town().huntingTargets().get(target.getUUID()).context(helper.getLevel());
        helper.assertTrue(newBody.getId() != target.getId(), "the returning victim has a new transient native entity id");
        helper.assertFalse(((com.jvn.villagerretaliation.mixin.ProjectileCombatAccessor) restored).villagerretaliation$canHitEntity(newBody),
                "restoring the arrow cannot make an already pierced victim eligible again");
        float before = newBody.getHealth();
        restored.setOwner(fixture.owner());
        restored.setDeltaMovement(new Vec3(-6.0D, 0.0D, 0.0D));
        var resumed = new SettlementProjectileSimulation(helper.getLevel(), fixture.town(), saved, fixture.owner(), newBody);
        helper.assertTrue(resumed.step(restored, helper.getLevel().getGameTime() + 1L), "the saved piercing arrow can continue physical flight");
        helper.assertValueEqual(newBody.getHealth(), before, "return flight cannot repeat the saved victim hit");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_ranged_hunting")
    public static void aPendingShotMaterializesOnceWhenItsNativeStorageIsAvailable(GameTestHelper helper) {
        var fixture = fixture(helper, Items.BOW, 2);
        var arrow = arrow(helper, fixture, helper.absolutePos(new BlockPos(3, 4, 3)).getCenter(), new Vec3(0.0D, 0.1D, 0.0D));
        long now = helper.getLevel().getGameTime();
        SettlementProjectileService.launched(fixture.town(), fixture.worker(), arrow, now);
        try {
            SettlementProjectileService.update(helper.getLevel(), fixture.town(), now);
            var actual = helper.getLevel().getEntity(arrow.getUUID());
            helper.assertTrue(actual instanceof Arrow, "loaded native entity storage receives the pending real projectile");
            helper.assertFalse(fixture.town().projectiles().get(arrow.getUUID()).pendingSpawn(), "the accepted native body takes over ownership");
            SettlementProjectileService.update(helper.getLevel(), fixture.town(), now);
            helper.assertTrue(helper.getLevel().getEntity(arrow.getUUID()) == actual, "repeated reconciliation keeps the same native body");
        } finally {
            var actual = helper.getLevel().getEntity(arrow.getUUID());
            if (actual != null) actual.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_ranged_hunting")
    public static void aNativeRemovalReceiptSuppressesAnOlderPendingSettlementSave(GameTestHelper helper) {
        var fixture = fixture(helper, Items.BOW, 2);
        var arrow = arrow(helper, fixture, helper.absolutePos(new BlockPos(3, 4, 3)).getCenter(), new Vec3(0.0D, 0.1D, 0.0D));
        long now = helper.getLevel().getGameTime();
        SettlementProjectileService.launched(fixture.town(), fixture.worker(), arrow, now);
        var oldSave = fixture.town().save(helper.getLevel().registryAccess());
        SettlementProjectileService.update(helper.getLevel(), fixture.town(), now);
        var actual = helper.getLevel().getEntity(arrow.getUUID());
        helper.assertTrue(actual != null, "the original pending shot entered real entity storage");
        actual.discard();
        var older = SettlementRecord.load(oldSave, helper.getLevel().registryAccess(), () -> {});
        SettlementProjectileService.update(helper.getLevel(), older, now);
        helper.assertTrue(helper.getLevel().getEntity(arrow.getUUID()) == null, "a newer native removal prevents respawning the older pending shot");
        helper.assertFalse(older.projectiles().containsKey(arrow.getUUID()), "the acknowledged removal retires the stale pending record");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_ranged_hunting")
    public static void aShotKeepsFlyingAfterItsWorkerRecordIsGone(GameTestHelper helper) {
        var fixture = fixture(helper, Items.BOW, 2);
        long now = helper.getLevel().getGameTime();
        var arrow = arrow(helper, fixture, fixture.target().position().add(-2.0D, 0.65D, 0.0D), new Vec3(2.0D, 0.0D, 0.0D));
        SettlementProjectileService.launched(fixture.town(), fixture.worker(), arrow, Math.max(0L, now - 1L));
        fixture.town().removeWorker(fixture.worker().id());
        var hits = new AtomicInteger();
        Consumer<LivingIncomingDamageEvent> listener = event -> {
            if (event.getEntity().getUUID().equals(fixture.target().getUUID())) {
                helper.assertFalse(event.getSource().getEntity() instanceof Villager, "an unavailable owner snapshot cannot become a living phantom attacker");
                hits.incrementAndGet();
            }
        };
        NeoForge.EVENT_BUS.addListener(listener);
        try {
            SettlementProjectileService.update(helper.getLevel(), fixture.town(), now);
            helper.assertValueEqual(hits.get(), 1, "an already launched projectile remains independent of the worker assignment");
            helper.assertTrue(fixture.town().huntingTargets().get(fixture.target().getUUID()).context(helper.getLevel()).getHealth() < 500.0F,
                    "physical flight still reaches its target after the worker is removed");
            helper.assertFalse(fixture.town().entityDeaths().containsKey(fixture.owner().getUUID()), "the retired owner cannot create a duplicate death or equipment drop");
        } finally {
            NeoForge.EVENT_BUS.unregister(listener);
        }
        helper.succeed();
    }

    private static Fixture fixture(GameTestHelper helper, Item weapon, int ammunition) {
        var level = helper.getLevel();
        var center = helper.absolutePos(new BlockPos(2, 2, 2)).offset(17_000_000, 0, 17_000_000).atY(80);
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var id = registry.create(0L, level.dimension().location(), center, "");
        var town = SettlementSavedData.get(level).establish(registry, id, UUID.randomUUID(), center, 0L).orElseThrow();
        var area = new SettlementArea(UUID.randomUUID(), SettlementAreaType.HUNTING_GROUNDS, "Hunting grounds",
                center.offset(-2, -1, -2), center.offset(14, 14, 10), 5, true);
        town.putArea(town.leader(), area);
        var owner = EntityType.VILLAGER.create(level);
        owner.setPos(center.getBottomCenter());
        owner.setNoAi(true);
        owner.setNoGravity(true);
        owner.setVillagerData(owner.getVillagerData().setProfession(VillagerProfession.FLETCHER));
        VillageAllegianceApi.assignKnown(level, owner, town.id(), AllegianceAssignmentSource.EXPLICIT_API);
        town.assignDuty(owner.getUUID(), area.id());
        SettlementWorkforceService.reconcile(level, owner);
        HiredWorkStateStore.state(owner).putBoolean("AutoDepositOutputs", false);
        var inventory = HiredJobInventory.getJobInventory(owner);
        inventory.setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(weapon));
        inventory.setItem(HiredJobInventory.HOTBAR_START, new ItemStack(Items.ARROW, ammunition));
        inventory.syncMainHandEquipment();
        var worker = SettlementWorkerState.capture(level, owner, new SettlementWorkforceService.Duty(town, area), 0L);
        town.recordWorker(worker);
        town.recordResident(SettlementResidentState.capture(owner, town, 0L));
        SettlementExperienceTestTerrain.flat(town);
        var target = EntityType.COW.create(level);
        target.setPos(center.east(6).getBottomCenter());
        target.setNoAi(true);
        target.setNoGravity(true);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(500.0D);
        target.setHealth(500.0F);
        town.recordHuntingTarget(SettlementHuntingTarget.capture(target, 0L));
        return new Fixture(town, area, worker, owner, target);
    }

    private static Arrow arrow(GameTestHelper helper, Fixture fixture, Vec3 position, Vec3 velocity) {
        var arrow = new Arrow(helper.getLevel(), fixture.owner(), new ItemStack(Items.ARROW), new ItemStack(Items.BOW));
        arrow.setPos(position);
        arrow.setDeltaMovement(velocity);
        return arrow;
    }

    private static int ammo(Villager owner) {
        var inventory = HiredJobInventory.getJobInventory(owner);
        int count = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (inventory.getItem(slot).is(Items.ARROW)) count += inventory.getItem(slot).getCount();
        }
        return count;
    }

    private static void hunt(GameTestHelper helper, Fixture fixture) {
        SettlementBackgroundHunting.tick(helper.getLevel(), fixture.town(), fixture.worker(), 200, 1.0D);
    }

    private record Fixture(SettlementRecord town, SettlementArea area, SettlementWorkerState worker, Villager owner, Cow target) {
    }
}
