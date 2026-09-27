package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.AllegianceAssignmentSource;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceApi;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.interaction.HiredWorkStateStore;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class SettlementHuntingPursuitGameTests {
    private SettlementHuntingPursuitGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_hunting_pursuit")
    public static void anUnloadedHunterWalksIntoNativeMeleeReach(GameTestHelper helper) {
        var fixture = fixture(helper);
        hunt(helper, fixture, 200);
        var worker = fixture.worker();
        helper.assertTrue(worker.context(helper.getLevel(), fixture.town()).getX() > fixture.villager().getX() + 4.0D,
                "the hunter covers the observed distance before attacking");
        helper.assertTrue(fixture.town().huntingTargets().get(fixture.prey().getUUID()).context(helper.getLevel()).getHealth() < 1000.0F,
                "actual native melee follows the completed approach");
        helper.assertValueEqual(worker.nativePosition(), fixture.villager().blockPosition(), "simulated movement preserves the original native body anchor");
        helper.assertValueEqual(fixture.town().residents().get(worker.id()).position(), worker.position(), "care and workforce agree on the moved resident");
        helper.assertFalse(helper.getLevel().hasChunkAt(worker.position()), "walking the observed route cannot load its native terrain");
        var saved = SettlementRecord.load(fixture.town().save(helper.getLevel().registryAccess()), helper.getLevel().registryAccess(), () -> {});
        var restored = saved.workers().get(worker.id());
        var position = restored.context(helper.getLevel(), saved).position();
        restored.restore(fixture.villager());
        helper.assertValueEqual(fixture.villager().position(), position, "the saved movement reaches the returning native hunter");
        fixture.villager().setPos(position.add(-0.25D, 0.0D, 0.0D));
        restored.restore(fixture.villager());
        helper.assertValueEqual(fixture.villager().position(), position.add(-0.25D, 0.0D, 0.0D), "later reconciliation cannot replay an already applied position");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_hunting_pursuit")
    public static void pursuitConsumesElapsedTimeAndCannotReplayTheSameInterval(GameTestHelper helper) {
        var fixture = fixture(helper);
        hunt(helper, fixture, 1);
        var owner = fixture.worker().context(helper.getLevel(), fixture.town());
        double travelled = owner.distanceTo(fixture.villager());
        helper.assertTrue(travelled > 0.0D && travelled < 1.0D, "one tick permits a bounded physical step");
        helper.assertValueEqual(fixture.town().huntingTargets().get(fixture.prey().getUUID()).context(helper.getLevel()).getHealth(), 1000.0F,
                "a distant target cannot be hit before the travel time has elapsed");
        var position = owner.position();
        var velocity = owner.getDeltaMovement();
        hunt(helper, fixture, 200);
        helper.assertValueEqual(fixture.worker().context(helper.getLevel(), fixture.town()).position(), position,
                "repeating the same game time cannot spend movement twice");
        var saved = SettlementWorkerState.load(fixture.worker().save());
        helper.assertValueEqual(saved.context(helper.getLevel(), fixture.town()).getDeltaMovement(), velocity,
                "save reload preserves movement between intervals");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_hunting_pursuit")
    public static void nativeMovementAttributesControlPursuitSpeed(GameTestHelper helper) {
        var fixture = fixture(helper);
        var fast = fixture.worker().context(helper.getLevel(), fixture.town());
        var slow = fixture.worker().context(helper.getLevel(), fixture.town());
        slow.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(fast.getAttributeValue(Attributes.MOVEMENT_SPEED) / 4.0D);
        helper.assertTrue(new SettlementHuntingPursuit(helper.getLevel(), fixture.town(), fixture.worker()).step(fast, fixture.prey()),
                "the ordinary movement attribute permits the route");
        helper.assertTrue(new SettlementHuntingPursuit(helper.getLevel(), fixture.town(), fixture.worker()).step(slow, fixture.prey()),
                "the slower body still makes progress");
        helper.assertTrue(fast.distanceTo(fixture.villager()) > slow.distanceTo(fixture.villager()) * 3.9D,
                "pursuit uses the body's movement attribute instead of a fixed travel allowance");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_hunting_pursuit")
    public static void aNativeTargetMoveReplansTheRemainingApproach(GameTestHelper helper) {
        var fixture = fixture(helper);
        var owner = fixture.worker().context(helper.getLevel(), fixture.town());
        var pursuit = new SettlementHuntingPursuit(helper.getLevel(), fixture.town(), fixture.worker());
        helper.assertTrue(pursuit.step(owner, fixture.prey()), "the first approach begins toward the old target position");
        double z = owner.getZ();
        fixture.prey().setPos(fixture.town().center().south(8).getBottomCenter());
        helper.assertTrue(pursuit.step(owner, fixture.prey()), "the target's changed position receives a new route");
        helper.assertTrue(owner.getZ() > z, "the hunter steers toward the moved target during the same interval");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_hunting_pursuit")
    public static void aClosedBoundaryCannotBeBypassedByLeavingTheHuntingGrounds(GameTestHelper helper) {
        var fixture = fixture(helper);
        for (var position : BlockPos.betweenClosed(fixture.town().center().offset(4, 0, -2), fixture.town().center().offset(4, 3, 10))) {
            stone(fixture.town(), position);
        }
        var before = fixture.worker().context(helper.getLevel(), fixture.town()).position();
        var pursuit = new SettlementHuntingPursuit(helper.getLevel(), fixture.town(), fixture.worker());
        var owner = fixture.worker().context(helper.getLevel(), fixture.town());
        helper.assertFalse(pursuit.step(owner, fixture.prey()), "the route cannot leave the designation to walk around its sealed boundary");
        helper.assertValueEqual(owner.position(), before, "a blocked route does not move through solid blocks");
        helper.assertFalse(helper.getLevel().hasChunkAt(owner.blockPosition()), "a failed route does not force native chunks");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_hunting_pursuit")
    public static void pursuitCanJumpAnObservedOneBlockObstacle(GameTestHelper helper) {
        var fixture = fixture(helper);
        fixture.villager().setNoGravity(false);
        fixture.worker().observe(helper.getLevel(), fixture.villager(), new SettlementWorkforceService.Duty(fixture.town(), fixture.area()),
                Math.max(0L, helper.getLevel().getGameTime() - 200L));
        fixture.worker().settings().putBoolean("Enabled", false);
        for (int z = -2; z <= 10; z++) stone(fixture.town(), fixture.town().center().offset(3, 0, z));
        hunt(helper, fixture, 200);
        var owner = fixture.worker().context(helper.getLevel(), fixture.town());
        helper.assertTrue(owner.getX() > fixture.town().center().getX() + 4.0D, "the hunter uses a jump to cross the observed one-block step");
        helper.assertTrue(fixture.town().huntingTargets().get(fixture.prey().getUUID()).context(helper.getLevel()).getHealth() < 1000.0F,
                "the hunter reaches contact range after the jump");
        helper.assertTrue(owner.getY() >= fixture.town().center().getY(), "collision keeps the hunter above the observed floor");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_hunting_pursuit")
    public static void reachingMeleeRangeDuringAJumpDoesNotFreezeTheHunterInMidair(GameTestHelper helper) {
        var fixture = fixture(helper);
        var level = helper.getLevel();
        long observed = Math.max(0L, level.getGameTime() - 200L);
        fixture.villager().setNoGravity(false);
        fixture.worker().observe(level, fixture.villager(), new SettlementWorkforceService.Duty(fixture.town(), fixture.area()), observed);
        fixture.worker().settings().putBoolean("Enabled", false);
        fixture.prey().setPos(fixture.town().center().east(4).getBottomCenter());
        fixture.town().recordHuntingTarget(SettlementHuntingTarget.capture(fixture.prey(), observed));
        for (int z = -2; z <= 10; z++) stone(fixture.town(), fixture.town().center().offset(3, 0, z));
        hunt(helper, fixture, 200);
        var owner = fixture.worker().context(level, fixture.town());
        helper.assertTrue(fixture.town().huntingTargets().get(fixture.prey().getUUID()).context(level).getHealth() < 1000.0F,
                "the jump reaches actual melee range");
        helper.assertTrue(owner.onGround(), "gravity and landing continue throughout melee recovery");
        helper.assertTrue(Math.abs(owner.getDeltaMovement().y + owner.getGravity() * 0.98D) < 1.0E-5D,
                "the saved body has grounded native motion instead of a frozen upward jump");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_hunting_pursuit")
    public static void movementObservationRetainsTheLastNativePositionAcrossSaving(GameTestHelper helper) {
        var fixture = fixture(helper);
        var nativePosition = fixture.prey().blockPosition();
        var state = fixture.town().huntingTargets().get(fixture.prey().getUUID());
        var proxy = state.context(helper.getLevel());
        var before = proxy.saveWithoutId(new CompoundTag());
        proxy.setPos(proxy.position().add(32.0D, 0.0D, 0.0D));
        state.interacted(proxy, before, helper.getLevel().getGameTime());
        var saved = SettlementHuntingTarget.load(state.save());
        helper.assertValueEqual(saved.nativePosition(), nativePosition, "a proxy movement cannot relocate the original native body anchor");
        helper.assertFalse(saved.position().equals(nativePosition), "the simulated position is persisted independently");
        var legacy = state.save();
        legacy.remove("NativePosition");
        helper.assertValueEqual(SettlementHuntingTarget.load(legacy).nativePosition(), saved.position(), "older saves fall back to their last recorded native position");
        saved.restore(fixture.prey(), helper.getLevel().getGameTime());
        saved.observe(fixture.prey(), helper.getLevel().getGameTime());
        helper.assertValueEqual(saved.nativePosition(), fixture.prey().blockPosition(), "an actual native observation updates the body anchor after reconciliation");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 400, batch = "settlement_hunting_pursuit_chunks")
    public static void aLoadedFrozenHunterDoesNotCollideWithItsOwnOriginalBody(GameTestHelper helper) {
        var fixture = fixture(helper);
        var level = helper.getLevel();
        var chunk = new ChunkPos(fixture.town().center());
        level.getChunkSource().addRegionTicket(TicketType.FORCED, chunk, 0, chunk);
        level.getChunk(chunk.x, chunk.z);
        helper.startSequence().thenWaitUntil(() -> {
            helper.assertTrue(level.areEntitiesLoaded(chunk.toLong()), "native storage is loaded");
            helper.assertFalse(level.isPositionEntityTicking(fixture.villager().blockPosition()), "the native hunter remains frozen");
        }).thenExecute(() -> {
            try {
                for (var pos : BlockPos.betweenClosed(fixture.area().min(), fixture.area().max())) {
                    level.setBlock(pos, pos.getY() < fixture.town().center().getY() ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 3);
                }
                helper.assertTrue(level.addFreshEntity(fixture.villager()) && level.addFreshEntity(fixture.prey()), "both frozen native bodies are present");
                fixture.worker().huntingCombat(Math.max(0L, level.getGameTime() - 200L), 0L);
                var oldPosition = fixture.villager().position();
                hunt(helper, fixture, 200);
                var simulated = fixture.worker().context(level, fixture.town()).position();
                helper.assertTrue(simulated.distanceToSqr(oldPosition) > 16.0D, "the simulation excludes its own native body from collision");
                SettlementBackgroundService.onActive(fixture.villager());
                helper.assertValueEqual(fixture.villager().position(), simulated, "the native pre-tick handoff restores the completed physical approach");
                helper.assertTrue(fixture.prey().getHealth() < 1000.0F, "the actual frozen prey receives native contact damage after the approach");
            } finally {
                fixture.villager().discard();
                fixture.prey().discard();
                level.getChunkSource().removeRegionTicket(TicketType.FORCED, chunk, 0, chunk);
            }
        }).thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 400, batch = "settlement_hunting_pursuit_anchor_chunks")
    public static void loadingOnlyTheSimulatedDestinationCannotDiscardTheNativeSource(GameTestHelper helper) {
        var fixture = fixture(helper);
        var level = helper.getLevel();
        var state = fixture.town().huntingTargets().get(fixture.prey().getUUID());
        var source = state.nativePosition();
        var proxy = state.context(level);
        var before = proxy.saveWithoutId(new CompoundTag());
        proxy.setPos(proxy.position().add(48.0D, 0.0D, 0.0D));
        proxy.setHealth(900.0F);
        state.interacted(proxy, before, level.getGameTime());
        var chunk = new ChunkPos(proxy.blockPosition());
        level.getChunkSource().addRegionTicket(TicketType.FORCED, chunk, 0, chunk);
        level.getChunk(chunk.x, chunk.z);
        helper.startSequence().thenWaitUntil(() -> {
            helper.assertTrue(level.areEntitiesLoaded(chunk.toLong()), "the simulated destination is loaded without its body");
            helper.assertFalse(level.hasChunkAt(source), "the original native body chunk remains unloaded");
        }).thenExecute(() -> {
            try {
                SettlementHuntingTargetService.update(level, fixture.town(), level.getGameTime());
                helper.assertTrue(fixture.town().huntingTargets().containsKey(fixture.prey().getUUID()),
                        "an empty virtual destination cannot prove that the still unloaded original body disappeared");
                SettlementHuntingTargetService.onActive(fixture.prey());
                helper.assertValueEqual(fixture.prey().getHealth(), 900.0F, "the original body still receives the pending injury");
                helper.assertValueEqual(fixture.prey().blockPosition(), proxy.blockPosition(), "the original body still receives the pending movement");
            } finally {
                level.getChunkSource().removeRegionTicket(TicketType.FORCED, chunk, 0, chunk);
            }
        }).thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 400, batch = "settlement_hunting_pursuit_recipient_chunks")
    public static void loadingOnlyAMovedHuntersDestinationRetainsItsExperienceEligibility(GameTestHelper helper) {
        var fixture = fixture(helper);
        var level = helper.getLevel();
        var owner = fixture.worker().context(level, fixture.town());
        var source = owner.blockPosition();
        var before = owner.saveWithoutId(new CompoundTag());
        owner.setPos(owner.position().add(48.0D, 0.0D, 0.0D));
        fixture.worker().huntingMoved(owner, before, level.getGameTime());
        fixture.town().residents().get(owner.getUUID()).combat(owner);
        var chunk = new ChunkPos(owner.blockPosition());
        level.getChunkSource().addRegionTicket(TicketType.FORCED, chunk, 0, chunk);
        level.getChunk(chunk.x, chunk.z);
        helper.startSequence().thenWaitUntil(() -> {
            helper.assertTrue(level.areEntitiesLoaded(chunk.toLong()), "the simulated destination finishes loading");
            helper.assertFalse(level.hasChunkAt(source), "the original hunter remains in its unloaded source chunk");
        }).thenExecute(() -> {
            try {
                var recipient = new SettlementExperienceRecipients(level).find(owner.getUUID());
                helper.assertTrue(recipient != null, "the loaded virtual destination cannot erase an unloaded hunter's experience eligibility");
                helper.assertValueEqual(recipient.position(), owner.position(), "experience contact uses the hunter's simulated position");
                helper.assertValueEqual(fixture.worker().nativePosition(), source, "recipient lookup preserves the original native body anchor");
                helper.assertFalse(level.hasChunkAt(source), "recipient lookup cannot force the original chunk");
            } finally {
                level.getChunkSource().removeRegionTicket(TicketType.FORCED, chunk, 0, chunk);
            }
        }).thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_hunting_pursuit")
    public static void aPursuingHunterRetainsItsOriginalBodyAnchorWhenKilled(GameTestHelper helper) {
        var fixture = fixture(helper);
        var source = fixture.villager().blockPosition();
        helper.assertTrue(SettlementEntityDeathService.onActive(fixture.villager()), "the original body predates its pending death");
        java.util.function.Consumer<net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent> reflect = event -> {
            if (event.getEntity().getUUID().equals(fixture.prey().getUUID()) && event.getSource().getEntity() instanceof Villager attacker) {
                attacker.hurt(helper.getLevel().damageSources().genericKill(), 1000.0F);
            }
        };
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(reflect);
        try {
            hunt(helper, fixture, 200);
            var death = fixture.town().entityDeaths().get(fixture.worker().id());
            helper.assertTrue(death != null, "the approached target's native retaliation records the hunter's death");
            helper.assertFalse(fixture.worker().position().equals(source), "the hunter had already moved before the lethal response");
            helper.assertValueEqual(death.position(), source, "the death belongs to the original native body chunk, not the simulated contact position");
            helper.assertFalse(SettlementEntityDeathService.onActive(fixture.villager()), "the old body cannot survive the moved hunter's death");
        } finally {
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(reflect);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_hunting_pursuit")
    public static void reflectedKnockbackAllowsThePursuingHunterToLandAndAttackAgain(GameTestHelper helper) {
        recoil(helper, false);
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_hunting_pursuit")
    public static void reflectedKnockbackWithoutAnEarlierRouteStillAllowsLanding(GameTestHelper helper) {
        recoil(helper, true);
    }

    private static void recoil(GameTestHelper helper, boolean adjacent) {
        var fixture = fixture(helper);
        var level = helper.getLevel();
        long observed = Math.max(0L, level.getGameTime() - 200L);
        fixture.villager().setNoGravity(false);
        fixture.villager().getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0D);
        fixture.villager().setHealth(100.0F);
        fixture.worker().observe(level, fixture.villager(), new SettlementWorkforceService.Duty(fixture.town(), fixture.area()), observed);
        fixture.worker().settings().putBoolean("Enabled", false);
        fixture.town().recordResident(SettlementResidentState.capture(fixture.villager(), fixture.town(), observed));
        if (adjacent) {
            fixture.prey().setPos(fixture.town().center().east().getBottomCenter());
            fixture.town().recordHuntingTarget(SettlementHuntingTarget.capture(fixture.prey(), observed));
        }
        java.util.function.Consumer<net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent> reflect = event -> {
            if (event.getEntity().getUUID().equals(fixture.prey().getUUID()) && event.getSource().getEntity() instanceof Villager attacker) {
                attacker.hurt(level.damageSources().thorns(event.getEntity()), 1.0F);
            }
        };
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(reflect);
        try {
            hunt(helper, fixture, 200);
            helper.assertFalse(level.hasChunkAt(fixture.town().center()), "native recoil cannot force the original chunk");
            int hits = fixture.worker().items(level.registryAccess()).get(HiredJobInventory.MAINHAND_SLOT).getDamageValue();
            var owner = fixture.worker().context(level, fixture.town());
            helper.assertTrue(hits > 1, "the hunter lands and resumes combat after actual native recoil: hits=" + hits
                    + ", position=" + owner.position() + ", motion=" + owner.getDeltaMovement() + ", status=" + fixture.worker().status());
            helper.assertValueEqual(owner.getHealth(), 100.0F - hits, "movement and native injury clocks advance together");
        } finally {
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(reflect);
        }
        helper.succeed();
    }

    private static void hunt(GameTestHelper helper, Fixture fixture, int elapsed) {
        SettlementBackgroundHunting.tick(helper.getLevel(), fixture.town(), fixture.worker(), elapsed, 1.0D);
    }

    private static void stone(SettlementRecord town, BlockPos position) {
        var origin = SettlementTerrainSection.sectionOrigin(position);
        var section = town.navigation().get(origin);
        var tag = section.save();
        var blocks = tag.getByteArray("Blocks");
        int index = (((position.getY() & 15) << 8) | ((position.getZ() & 15) << 4) | (position.getX() & 15)) * 2;
        blocks[index] = 0;
        blocks[index + 1] = 1;
        tag.putByteArray("Blocks", blocks);
        town.recordNavigation(SettlementNavigationSection.load(tag));
    }

    private static Fixture fixture(GameTestHelper helper) {
        var level = helper.getLevel();
        var origin = helper.absolutePos(new BlockPos(2, 2, 2));
        var center = new BlockPos(((20_000_000 + origin.getX() * 4) & ~15) + 2, 80, ((20_000_000 + origin.getZ() * 4) & ~15) + 2);
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
        long observed = Math.max(0L, level.getGameTime() - 200L);
        var worker = SettlementWorkerState.capture(level, villager, new SettlementWorkforceService.Duty(town, area), observed);
        worker.settings().putBoolean("Enabled", false);
        town.recordWorker(worker);
        town.recordResident(SettlementResidentState.capture(villager, town, observed));
        var prey = EntityType.COW.create(level);
        prey.setPos(center.east(8).getBottomCenter());
        prey.setNoAi(true);
        prey.setNoGravity(true);
        prey.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0D);
        prey.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0D);
        prey.setHealth(1000.0F);
        town.recordHuntingTarget(SettlementHuntingTarget.capture(prey, observed));
        SettlementExperienceTestTerrain.flat(town);
        return new Fixture(town, area, worker, villager, prey);
    }

    private record Fixture(SettlementRecord town, SettlementArea area, SettlementWorkerState worker, Villager villager, Cow prey) {
    }
}
