package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.AllegianceAssignmentSource;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceApi;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.event.VillagerRetaliationEvents;
import com.jvn.villagerretaliation.interaction.HiredWorkStateStore;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class SettlementDeathHandoffGameTests {
    private SettlementDeathHandoffGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 400, batch = "settlement_death_handoff_chunks")
    public static void lethalReflectionReconcilesBeforeTheFrozenHunterTicks(GameTestHelper helper) {
        frozenHunter(helper, false);
    }

    @GameTest(template = "empty", timeoutTicks = 400, batch = "settlement_death_handoff_chunks")
    public static void lethalReflectionReconcilesBeforeTheFrozenHunterTakesDamage(GameTestHelper helper) {
        frozenHunter(helper, true);
    }

    private static void frozenHunter(GameTestHelper helper, boolean damage) {
        var fixture = fixture(helper);
        var level = helper.getLevel();
        var hunter = fixture.villager();
        var prey = EntityType.COW.create(level);
        prey.setPos(hunter.position().add(1.0D, 0.0D, 0.0D));
        prey.setNoAi(true);
        prey.setNoGravity(true);
        var chunk = new ChunkPos(hunter.blockPosition());
        level.getChunkSource().addRegionTicket(TicketType.FORCED, chunk, 0, chunk);
        level.getChunk(chunk.x, chunk.z);
        helper.startSequence().thenWaitUntil(() -> {
            helper.assertTrue(level.areEntitiesLoaded(chunk.toLong()), "the actual native bodies can enter the loaded section");
            helper.assertFalse(level.isPositionEntityTicking(hunter.blockPosition()), "the hunter's native body remains frozen");
        }).thenExecute(() -> {
            Consumer<LivingIncomingDamageEvent> reflect = event -> {
                if (event.getEntity().getUUID().equals(prey.getUUID()) && event.getSource().getEntity() instanceof Villager attacker) {
                    attacker.hurt(level.damageSources().genericKill(), 1000.0F);
                }
            };
            Consumer<LivingDropsEvent> rewards = event -> {
                if (!event.getEntity().getUUID().equals(hunter.getUUID())) return;
                event.getDrops().add(new ItemEntity(level, hunter.getX(), hunter.getY(), hunter.getZ(), new ItemStack(Items.GOLD_NUGGET, 7)));
                SettlementDeathCapture.experience(event.getEntity(), hunter.position(), 3, null);
            };
            int[] deaths = {0};
            Consumer<LivingDeathEvent> count = event -> {
                if (event.getEntity().getUUID().equals(hunter.getUUID())) deaths[0]++;
            };
            NeoForge.EVENT_BUS.addListener(reflect);
            NeoForge.EVENT_BUS.addListener(rewards);
            NeoForge.EVENT_BUS.addListener(count);
            try {
                helper.assertTrue(level.addFreshEntity(hunter) && level.addFreshEntity(prey), "the hunter and prey are actual loaded bodies");
                helper.assertTrue(SettlementEntityDeathService.onActive(hunter), "the original body is observed before any background death");
                var oldBody = hunter.saveWithoutId(new CompoundTag());
                fixture.town().recordHuntingTarget(SettlementHuntingTarget.capture(prey, level.getGameTime()));
                SettlementBackgroundHunting.tick(level, fixture.town(), fixture.worker(), 200, 1.0D);
                helper.assertTrue(hunter.isAlive(), "the frozen original body has not yet received the proxy's lethal injury");
                helper.assertFalse(fixture.town().workers().containsKey(hunter.getUUID()), "lethal reflection ends the work commitment");
                helper.assertFalse(fixture.town().residents().containsKey(hunter.getUUID()), "the dead hunter cannot receive further care");
                var pending = fixture.town().entityDeaths().get(hunter.getUUID());
                helper.assertTrue(pending != null && !pending.applied(), "background combat records the original body's pending death");
                var oldDeath = pending.save();
                if (damage) {
                    helper.assertFalse(hunter.hurt(level.damageSources().genericKill(), 1000.0F), "the native damage hook discards the already dead body before another hit");
                } else {
                    var tick = new EntityTickEvent.Pre(hunter);
                    VillagerRetaliationEvents.onEntityTickPre(tick);
                    helper.assertTrue(tick.isCanceled(), "the first native pre-tick stops before the already dead hunter can act");
                }
                helper.assertTrue(hunter.isRemoved(), "the earlier cached absence cannot conceal the later background death");
                helper.assertValueEqual(deaths[0], 1, "native handoff cannot fire a second death event");
                int loot = fixture.town().itemDrops().values().stream().map(drop -> drop.item(level.registryAccess()))
                        .filter(stack -> stack.is(Items.GOLD_NUGGET)).mapToInt(ItemStack::getCount).sum();
                helper.assertValueEqual(loot, 7, "only the actual background death contributes the hooked loot");
                SettlementEntityDeathService.update(level, fixture.town(), level.getGameTime());
                helper.assertFalse(fixture.town().entityDeaths().containsKey(hunter.getUUID()), "native deletion and experience delivery finish the death record");
                int experience = fixture.town().groundExperience().values().stream().mapToInt(state ->
                        state.context(level).getValue() * state.save().getCompound("Entity").getInt("Count")).sum();
                helper.assertValueEqual(experience, 3, "the finite hooked experience enters the shared ledger exactly once");
                var stale = EntityType.VILLAGER.create(level);
                stale.load(oldBody);
                helper.assertFalse(SettlementEntityDeathService.onJoined(stale), "the native chunk receipt rejects an older living body after the town record is retired");
                fixture.town().recordEntityDeath(SettlementEntityDeath.load(oldDeath));
                SettlementEntityDeathService.update(level, fixture.town(), level.getGameTime());
                int replayed = fixture.town().groundExperience().values().stream().mapToInt(state ->
                        state.context(level).getValue() * state.save().getCompound("Entity").getInt("Count")).sum();
                helper.assertValueEqual(replayed, experience, "an older town death snapshot cannot duplicate delivered experience");
                helper.assertValueEqual(fixture.town().itemDrops().values().stream().map(drop -> drop.item(level.registryAccess()))
                        .filter(stack -> stack.is(Items.GOLD_NUGGET)).mapToInt(ItemStack::getCount).sum(), loot,
                        "older body and town snapshots cannot duplicate death loot");
            } finally {
                NeoForge.EVENT_BUS.unregister(reflect);
                NeoForge.EVENT_BUS.unregister(rewards);
                NeoForge.EVENT_BUS.unregister(count);
                hunter.discard();
                prey.discard();
                level.getChunkSource().removeRegionTicket(TicketType.FORCED, chunk, 0, chunk);
            }
        }).thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_death_handoff")
    public static void aReloadedDeathInvalidatesAnEarlierLivingObservation(GameTestHelper helper) {
        var fixture = fixture(helper);
        var hunter = fixture.villager();
        helper.assertTrue(SettlementEntityDeathService.onActive(hunter), "the body is observed before the death exists");
        var death = SettlementEntityDeath.create(hunter, hunter.blockPosition(), fixture.worker(), List.of(), 0L, true);
        fixture.town().recordEntityDeath(SettlementEntityDeath.load(death.save()));
        helper.assertFalse(SettlementEntityDeathService.onActive(hunter), "a saved death takes precedence over the cached living observation");
        helper.assertTrue(fixture.town().entityDeaths().containsKey(hunter.getUUID()), "an unavailable original chunk retains its pending deletion");
        helper.assertFalse(helper.getLevel().hasChunkAt(hunter.blockPosition()), "reconciliation does not force the death's original chunk");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_death_handoff")
    public static void deathIdentityStillRequiresTheOriginalEntityType(GameTestHelper helper) {
        var fixture = fixture(helper);
        var collision = EntityType.COW.create(helper.getLevel());
        collision.setUUID(fixture.villager().getUUID());
        collision.setPos(fixture.villager().position());
        helper.assertTrue(SettlementEntityDeathService.onActive(collision), "the conflicting type initially has no death record");
        fixture.town().recordEntityDeath(SettlementEntityDeath.create(fixture.villager(), fixture.villager().blockPosition(),
                fixture.worker(), List.of(), 0L, true));
        helper.assertTrue(SettlementEntityDeathService.onActive(collision) && collision.isAlive(), "a UUID collision cannot delete an unrelated entity type");
        helper.assertFalse(SettlementEntityDeathService.onActive(fixture.villager()), "the correctly typed original body still receives its death");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_death_handoff")
    public static void experienceOnlyRecordsCannotDeleteTheSurvivingBody(GameTestHelper helper) {
        var fixture = fixture(helper);
        var hunter = fixture.villager();
        helper.assertTrue(SettlementEntityDeathService.onActive(hunter), "the surviving body is already observed");
        var orb = new ExperienceOrb(helper.getLevel(), hunter.getX(), hunter.getY(), hunter.getZ(), 3);
        var death = SettlementEntityDeath.create(hunter, hunter.blockPosition(), fixture.worker(), List.of(orb), 0L, false);
        fixture.town().recordEntityDeath(death);
        helper.assertTrue(SettlementEntityDeathService.onActive(hunter) && hunter.isAlive(), "reward capture without accepted death cannot delete its source");
        helper.assertFalse(death.id().equals(hunter.getUUID()), "experience-only capture uses its own identity");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 400, batch = "settlement_death_dimension_chunks")
    public static void aCachedTransferredBodyReceivesItsPendingDeath(GameTestHelper helper) {
        transferredBody(helper, false);
    }

    @GameTest(template = "empty", timeoutTicks = 400, batch = "settlement_death_dimension_chunks")
    public static void periodicDeathReconciliationFindsTheTransferredBody(GameTestHelper helper) {
        transferredBody(helper, true);
    }

    private static void transferredBody(GameTestHelper helper, boolean periodic) {
        var fixture = fixture(helper);
        var origin = helper.getLevel();
        var destination = origin.getServer().getLevel(Level.NETHER);
        var body = EntityType.VILLAGER.create(destination);
        body.load(fixture.villager().saveWithoutId(new CompoundTag()));
        var chunk = new ChunkPos(body.blockPosition());
        destination.getChunkSource().addRegionTicket(TicketType.FORCED, chunk, 0, chunk);
        destination.getChunk(chunk.x, chunk.z);
        helper.startSequence().thenWaitUntil(() -> {
            helper.assertTrue(destination.areEntitiesLoaded(chunk.toLong()), "the transferred body has a real loaded destination chunk");
            helper.assertFalse(origin.hasChunkAt(body.blockPosition()), "the original death chunk remains unloaded in its own dimension");
        }).thenExecute(() -> {
            try {
                helper.assertTrue(destination.addFreshEntity(body), "the transferred body enters actual destination storage");
                helper.assertTrue(SettlementEntityDeathService.onActive(body), "the transferred body is observed before its pending death");
                var oldBody = body.saveWithoutId(new CompoundTag());
                var orb = new ExperienceOrb(origin, body.getX(), body.getY(), body.getZ(), 3);
                var death = SettlementEntityDeath.create(fixture.villager(), fixture.villager().blockPosition(), fixture.worker(),
                        List.of(orb), origin.getGameTime(), true);
                fixture.town().recordEntityDeath(death);
                if (periodic) SettlementEntityDeathService.update(origin, fixture.town(), origin.getGameTime());
                else helper.assertFalse(SettlementEntityDeathService.onActive(body), "the native destination hook enforces the pending death");
                helper.assertTrue(body.isRemoved(), "dimension transfer cannot revive a background death");
                helper.assertFalse(death.applied(), "a loaded destination at identical coordinates cannot acknowledge the unavailable original chunk");
                helper.assertTrue(fixture.town().entityDeaths().containsKey(body.getUUID()), "the original chunk still has a pending death record");
                helper.assertFalse(origin.hasChunkAt(body.blockPosition()), "cross-dimension reconciliation never force-loads the original terrain");
                SettlementEntityDeathService.update(origin, fixture.town(), origin.getGameTime());
                helper.assertTrue(fixture.town().groundExperience().containsKey(orb.getUUID()), "death experience remains owned in the source dimension");
                helper.assertTrue(destination.getEntity(orb.getUUID()) == null, "reconciliation cannot move the death reward into another dimension");
                fixture.town().removeEntityDeath(body.getUUID());
                var stale = EntityType.VILLAGER.create(destination);
                stale.load(oldBody);
                helper.assertFalse(SettlementEntityDeathService.onJoined(stale), "the destination chunk independently retains the deletion receipt");
                fixture.town().recordEntityDeath(death);
            } finally {
                body.discard();
                destination.getChunkSource().removeRegionTicket(TicketType.FORCED, chunk, 0, chunk);
            }
        }).thenSucceed();
    }

    private static Fixture fixture(GameTestHelper helper) {
        var level = helper.getLevel();
        var center = helper.absolutePos(new BlockPos(2, 2, 2)).offset(19_000_000, 0, 19_000_000).atY(80);
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
        worker.settings().putBoolean("Enabled", false);
        town.recordWorker(worker);
        town.recordResident(SettlementResidentState.capture(villager, town, 0L));
        SettlementExperienceTestTerrain.flat(town);
        return new Fixture(town, area, worker, villager);
    }

    private record Fixture(SettlementRecord town, SettlementArea area, SettlementWorkerState worker, Villager villager) {
    }
}
