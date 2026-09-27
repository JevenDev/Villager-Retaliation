package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.AllegianceAssignmentSource;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceApi;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.combat.VillagerRetaliationHandler;
import com.jvn.villagerretaliation.interaction.HiredWorkStateStore;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class SettlementHuntingCombatGameTests {
    private SettlementHuntingCombatGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_hunting_combat")
    public static void adjacentUnloadedHuntingUsesRealMeleeDamageAndDurability(GameTestHelper helper) {
        var fixture = fixture(helper, false);
        var cow = cow(helper, fixture, 40.0F);
        record(helper, fixture, cow);
        hunt(helper, fixture);
        var state = fixture.town().huntingTargets().get(cow.getUUID());
        var injured = state.context(helper.getLevel());
        helper.assertTrue(injured.getHealth() < 39.0F && injured.getHealth() > 0.0F,
                "the actual sword modifiers produce a nonlethal native hit");
        helper.assertValueEqual(fixture.worker().status(), "attacking_hunting_target", "the worker records an actual attack");
        helper.assertValueEqual(weaponDamage(helper, fixture), 1, "a successful native sword hit consumes one durability");
        helper.assertValueEqual(cow.getHealth(), 40.0F, "the unloaded body remains untouched until reconciliation");
        helper.assertFalse(helper.getLevel().hasChunkAt(cow.blockPosition()), "native melee cannot force the unloaded terrain");
        helper.assertTrue(fixture.town().entityDeaths().isEmpty() && fixture.town().itemDrops().isEmpty(),
                "a nonlethal hit cannot fabricate loot or a death");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_hunting_combat")
    public static void damageSaveReplaysOnceAndPreservesUnrelatedNativeFields(GameTestHelper helper) {
        var fixture = fixture(helper, false);
        var cow = cow(helper, fixture, 40.0F);
        record(helper, fixture, cow);
        hunt(helper, fixture);
        var saved = SettlementRecord.load(fixture.town().save(helper.getLevel().registryAccess()), helper.getLevel().registryAccess(), () -> {});
        var state = saved.huntingTargets().get(cow.getUUID());
        float health = state.context(helper.getLevel()).getHealth();
        cow.setCustomName(Component.literal("Current native name"));
        cow.setAge(-1200);
        cow.getPersistentData().putString("OtherSystem", "retained");
        helper.assertTrue(state.restore(cow, helper.getLevel().getGameTime()), "the saved impact reaches the returning native body");
        helper.assertValueEqual(cow.getHealth(), health, "the recorded native injury is restored");
        helper.assertValueEqual(cow.getAge(), -1200, "combat cannot roll back unrelated animal age");
        helper.assertValueEqual(cow.getCustomName().getString(), "Current native name", "combat cannot roll back a new name");
        helper.assertValueEqual(cow.getPersistentData().getString("OtherSystem"), "retained", "unrelated nested persistent data survives");
        cow.setHealth(health - 1.0F);
        helper.assertFalse(state.restore(cow, helper.getLevel().getGameTime()), "the impact version cannot replay twice");
        helper.assertValueEqual(cow.getHealth(), health - 1.0F, "fresh native damage cannot be healed by repeated handoff");
        var worker = saved.workers().get(fixture.worker().id());
        worker.restore(fixture.villager());
        helper.assertValueEqual(HiredJobInventory.getJobInventory(fixture.villager()).getItem(HiredJobInventory.MAINHAND_SLOT).getDamageValue(),
                1, "the native hunter receives its weapon wear");
        worker.restore(fixture.villager());
        helper.assertValueEqual(HiredJobInventory.getJobInventory(fixture.villager()).getItem(HiredJobInventory.MAINHAND_SLOT).getDamageValue(),
                1, "the same weapon wear cannot replay");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_hunting_combat")
    public static void repeatedBackgroundIntervalCannotRepeatAnAttack(GameTestHelper helper) {
        var fixture = fixture(helper, false);
        var cow = cow(helper, fixture, 40.0F);
        record(helper, fixture, cow);
        hunt(helper, fixture);
        float health = fixture.town().huntingTargets().get(cow.getUUID()).context(helper.getLevel()).getHealth();
        for (int index = 0; index < 5; index++) hunt(helper, fixture);
        helper.assertValueEqual(fixture.town().huntingTargets().get(cow.getUUID()).context(helper.getLevel()).getHealth(),
                health, "repeated calls at one server time cannot spend the same combat interval");
        helper.assertValueEqual(weaponDamage(helper, fixture), 1, "duplicate updates cannot consume more durability");
        var saved = SettlementWorkerState.load(fixture.worker().save());
        helper.assertTrue(saved.huntingAttackAt() > helper.getLevel().getGameTime(), "the native attack recovery survives saving");
        saved.restore(fixture.villager());
        helper.assertValueEqual(VillagerRetaliationHandler.nextMeleeAttackTick(fixture.villager()), saved.huntingAttackAt(),
                "the returning hunter retains its actual recovery clock");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_hunting_combat")
    public static void runningIntervalUsesBoundedNativeCooldowns(GameTestHelper helper) {
        var fixture = fixture(helper, false);
        var cow = cow(helper, fixture, 500.0F);
        long now = helper.getLevel().getGameTime();
        fixture.town().recordHuntingTarget(SettlementHuntingTarget.capture(cow, Math.max(0L, now - 48_000L)));
        fixture.worker().huntingCombat(Math.max(0L, now - 48_000L), 0L);
        hunt(helper, fixture);
        int wear = weaponDamage(helper, fixture);
        helper.assertTrue(wear > 1 && wear <= 51, "a running interval contains native attacks but never a stopped-server catch-up");
        helper.assertTrue(fixture.town().huntingTargets().get(cow.getUUID()).context(helper.getLevel()).getHealth() < 490.0F,
                "elapsed attack recovery advances hit invulnerability instead of discarding later native hits");
        helper.assertFalse(helper.getLevel().hasChunkAt(cow.blockPosition()), "multiple native strikes still avoid terrain loading");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_hunting_combat")
    public static void canceledIncomingDamageDoesNotWearOrKill(GameTestHelper helper) {
        var fixture = fixture(helper, false);
        var cow = cow(helper, fixture, 40.0F);
        record(helper, fixture, cow);
        Consumer<LivingIncomingDamageEvent> cancel = event -> {
            if (event.getEntity().getUUID().equals(cow.getUUID())) event.setCanceled(true);
        };
        NeoForge.EVENT_BUS.addListener(cancel);
        try {
            hunt(helper, fixture);
            helper.assertValueEqual(fixture.town().huntingTargets().get(cow.getUUID()).context(helper.getLevel()).getHealth(),
                    40.0F, "the actual native cancellation prevents background damage");
            helper.assertValueEqual(weaponDamage(helper, fixture), 0, "canceled damage cannot consume success-only weapon wear");
            helper.assertTrue(fixture.town().itemDrops().isEmpty() && fixture.town().entityDeaths().isEmpty(),
                    "canceled hits cannot create loot or death records");
        } finally {
            NeoForge.EVENT_BUS.unregister(cancel);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_hunting_combat")
    public static void nativeDeathCreatesFiniteDropsExperienceAndADeletionReceipt(GameTestHelper helper) {
        var fixture = fixture(helper, false);
        var inventory = HiredJobInventory.getJobInventory(fixture.villager());
        var sword = new ItemStack(Items.IRON_SWORD);
        sword.enchant(helper.getLevel().registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
                .getHolderOrThrow(net.minecraft.world.item.enchantment.Enchantments.MENDING), 1);
        sword.setDamageValue(20);
        inventory.setItem(HiredJobInventory.MAINHAND_SLOT, sword);
        fixture.worker().inventorySnapshot(inventory.captureSnapshot());
        var cow = cow(helper, fixture, 1.0F);
        record(helper, fixture, cow);
        hunt(helper, fixture);
        helper.assertTrue(fixture.town().entityDeaths().containsKey(cow.getUUID()), "a lethal native hit records the original identity");
        helper.assertFalse(fixture.town().huntingTargets().containsKey(cow.getUUID()), "the killed target cannot be selected again");
        helper.assertTrue(!fixture.town().itemDrops().isEmpty(), "the real native loot table produced finite item entities");
        int drops = fixture.town().itemDrops().values().stream().mapToInt(drop -> drop.item(helper.getLevel().registryAccess()).getCount()).sum();
        SettlementEntityDeathService.update(helper.getLevel(), fixture.town(), helper.getLevel().getGameTime());
        helper.assertTrue(!fixture.town().groundExperience().isEmpty(), "native work-kill experience enters the shared experience ledger");
        helper.assertFalse(SettlementEntityDeathService.onJoined(cow), "the old living body cannot return after the recorded kill");
        SettlementEntityDeathService.onJoined(cow);
        int after = fixture.town().itemDrops().values().stream().mapToInt(drop -> drop.item(helper.getLevel().registryAccess()).getCount()).sum();
        helper.assertValueEqual(after, drops, "replaying a native body cannot duplicate death loot");
        helper.assertFalse(helper.getLevel().hasChunkAt(cow.blockPosition()), "death reconciliation does not force its original chunk");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_hunting_combat")
    public static void canceledDeathKeepsTheNativeRecoveryHealth(GameTestHelper helper) {
        var fixture = fixture(helper, false);
        var cow = cow(helper, fixture, 1.0F);
        record(helper, fixture, cow);
        Consumer<LivingDeathEvent> cancel = event -> {
            if (event.getEntity().getUUID().equals(cow.getUUID())) {
                event.getEntity().setHealth(1.0F);
                event.setCanceled(true);
            }
        };
        NeoForge.EVENT_BUS.addListener(cancel);
        try {
            hunt(helper, fixture);
            helper.assertValueEqual(fixture.town().huntingTargets().get(cow.getUUID()).context(helper.getLevel()).getHealth(),
                    1.0F, "a native death cancellation retains its recovered entity");
            helper.assertTrue(fixture.town().entityDeaths().isEmpty() && fixture.town().itemDrops().isEmpty(),
                    "a canceled death cannot create a tombstone or loot");
        } finally {
            NeoForge.EVENT_BUS.unregister(cancel);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_hunting_combat")
    public static void damageHooksCanInjureTheBackgroundHunter(GameTestHelper helper) {
        var fixture = fixture(helper, false);
        var cow = cow(helper, fixture, 40.0F);
        record(helper, fixture, cow);
        Consumer<LivingIncomingDamageEvent> retaliate = event -> {
            if (event.getEntity().getUUID().equals(cow.getUUID())
                    && event.getSource().getEntity() instanceof Villager attacker) {
                attacker.hurt(helper.getLevel().damageSources().thorns(event.getEntity()), 3.0F);
            }
        };
        NeoForge.EVENT_BUS.addListener(retaliate);
        try {
            hunt(helper, fixture);
            float health = fixture.town().residents().get(fixture.worker().id()).health();
            helper.assertTrue(health < fixture.villager().getHealth(), "native reflected damage reaches the hunter's health ledger");
            var saved = SettlementWorkerState.load(fixture.worker().save());
            saved.restore(fixture.villager());
            helper.assertValueEqual(fixture.villager().getHealth(), health, "the real hunter returns with the native reflected injury");
            fixture.villager().setHealth(health - 1.0F);
            saved.restore(fixture.villager());
            helper.assertValueEqual(fixture.villager().getHealth(), health - 1.0F, "repeated worker handoff cannot heal later damage");
        } finally {
            NeoForge.EVENT_BUS.unregister(retaliate);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_hunting_combat")
    public static void livestockGrowthAndHuntingInjuriesShareTheSameAnimal(GameTestHelper helper) {
        var fixture = fixture(helper, false);
        var cow = cow(helper, fixture, 40.0F);
        record(helper, fixture, cow);
        var livestock = SettlementLivestock.capture(cow, fixture.area().id(), helper.getLevel().getGameTime());
        fixture.town().recordLivestock(livestock);
        hunt(helper, fixture);
        float health = fixture.town().huntingTargets().get(cow.getUUID()).context(helper.getLevel()).getHealth();
        var herdContext = livestock.context(helper.getLevel());
        helper.assertValueEqual(herdContext.getHealth(), health, "the livestock context receives the same pending hunting injury");
        herdContext.setAge(-1000);
        livestock.interacted(herdContext);
        var next = livestock.context(helper.getLevel());
        helper.assertValueEqual(next.getAge(), -1000, "later livestock changes remain authoritative");
        helper.assertValueEqual(next.getHealth(), health, "later livestock contexts cannot heal away hunting injuries");
        livestock.restore(cow);
        SettlementHuntingTargetService.onActive(cow);
        helper.assertValueEqual(cow.getAge(), -1000, "native return preserves the later livestock age");
        helper.assertValueEqual(cow.getHealth(), health, "native return applies the injury only once across both ledgers");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_hunting_combat")
    public static void distantAndActivelyTickingTargetsCannotBeHitByAContactSnapshot(GameTestHelper helper) {
        var distant = fixture(helper, false);
        var cow = cow(helper, distant, 40.0F);
        cow.setPos(distant.town().center().east(4).getBottomCenter());
        record(helper, distant, cow);
        hunt(helper, distant);
        helper.assertValueEqual(distant.town().huntingTargets().get(cow.getUUID()).context(helper.getLevel()).getHealth(),
                40.0F, "contact combat cannot hit beyond the native reach");
        helper.assertValueEqual(weaponDamage(helper, distant), 0, "unreachable targets cost no weapon durability");
        var active = fixture(helper, true);
        var nativeCow = cow(helper, active, 40.0F);
        helper.assertTrue(helper.getLevel().addFreshEntity(nativeCow), "the active native target exists");
        try {
            record(helper, active, nativeCow);
            hunt(helper, active);
            helper.assertValueEqual(nativeCow.getHealth(), 40.0F, "a live target cannot receive invisible duplicate background attacks");
            helper.assertValueEqual(weaponDamage(helper, active), 0, "native target ownership prevents background weapon use");
        } finally {
            nativeCow.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_hunting_combat")
    public static void disabledCombatAndCreeperAvoidanceDoNotBecomeBackgroundStrikes(GameTestHelper helper) {
        var fixture = fixture(helper, false);
        var cow = cow(helper, fixture, 40.0F);
        record(helper, fixture, cow);
        boolean enabled = com.jvn.villagerretaliation.config.VillagerRetaliationConfig.ENABLE_VILLAGER_RETALIATION.get();
        com.jvn.villagerretaliation.config.VillagerRetaliationConfig.ENABLE_VILLAGER_RETALIATION.set(false);
        try {
            hunt(helper, fixture);
            helper.assertValueEqual(fixture.town().huntingTargets().get(cow.getUUID()).context(helper.getLevel()).getHealth(),
                    40.0F, "disabled native retaliation also disables observed melee");
            helper.assertValueEqual(weaponDamage(helper, fixture), 0, "disabled combat cannot wear the weapon");
        } finally {
            com.jvn.villagerretaliation.config.VillagerRetaliationConfig.ENABLE_VILLAGER_RETALIATION.set(enabled);
        }
        var other = fixture;
        var creeper = EntityType.CREEPER.create(helper.getLevel());
        creeper.setPos(other.town().center().east().getBottomCenter());
        var readiness = VillagerRetaliationHandler.prepareObservedMelee(other.worker().context(helper.getLevel(), other.town()),
                creeper, new SettlementExperienceTerrain(helper.getLevel(), other.town()), helper.getLevel().getGameTime());
        helper.assertValueEqual(readiness, VillagerRetaliationHandler.ObservedMelee.RETREAT,
                "the native creeper exclusion remains avoidance rather than a fabricated attack");
        helper.assertValueEqual(creeper.getHealth(), creeper.getMaxHealth(), "checking native tactics cannot hurt the target");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_hunting_combat")
    public static void laterFieldChangesDoNotReplayAnEarlierHealthMutation(GameTestHelper helper) {
        var fixture = fixture(helper, false);
        var cow = cow(helper, fixture, 40.0F);
        var state = SettlementHuntingTarget.capture(cow, helper.getLevel().getGameTime());
        var target = state.context(helper.getLevel());
        var before = target.saveWithoutId(new CompoundTag());
        target.setHealth(30.0F);
        target.getPersistentData().putString("CombatMarker", "first");
        state.interacted(target, before, helper.getLevel().getGameTime());
        helper.assertTrue(state.restore(cow, helper.getLevel().getGameTime()), "the first mutation is applied");
        cow.setHealth(25.0F);
        cow.getPersistentData().putString("UnrelatedMarker", "keep");
        target = state.context(helper.getLevel());
        before = target.saveWithoutId(new CompoundTag());
        target.getPersistentData().remove("CombatMarker");
        target.setCustomName(Component.literal("Changed by a native hook"));
        state.interacted(target, before, helper.getLevel().getGameTime());
        var saved = SettlementHuntingTarget.load(state.save());
        helper.assertTrue(saved.restore(cow, helper.getLevel().getGameTime()), "the later native field mutation is applied");
        helper.assertValueEqual(cow.getHealth(), 25.0F, "an already applied health field cannot be replayed by a later mutation");
        helper.assertFalse(cow.getPersistentData().contains("CombatMarker"), "a native field removal is preserved");
        helper.assertValueEqual(cow.getPersistentData().getString("UnrelatedMarker"), "keep", "nested changes preserve unrelated native data");
        helper.assertValueEqual(cow.getCustomName().getString(), "Changed by a native hook", "the later native change still arrives");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_hunting_combat")
    public static void deletingHuntingGroundsCannotErasePendingDamage(GameTestHelper helper) {
        var fixture = fixture(helper, false);
        var cow = cow(helper, fixture, 40.0F);
        record(helper, fixture, cow);
        hunt(helper, fixture);
        float injured = fixture.town().huntingTargets().get(cow.getUUID()).context(helper.getLevel()).getHealth();
        fixture.town().removeDuty(fixture.worker().id());
        helper.assertTrue(fixture.town().removeArea(fixture.town().leader(), fixture.area().id()), "the old hunting designation is removed");
        SettlementHuntingTargetService.update(helper.getLevel(), fixture.town(), helper.getLevel().getGameTime());
        helper.assertTrue(fixture.town().huntingTargets().containsKey(cow.getUUID()), "the deleted designation cannot discard an unapplied injury");
        SettlementHuntingTargetService.onActive(cow);
        helper.assertValueEqual(cow.getHealth(), injured, "the returning body still receives the damage outside any hunting area");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_hunting_combat")
    public static void nativeDimensionTransferAppliesDamageBeforeReleasingItsRecord(GameTestHelper helper) {
        var fixture = fixture(helper, false);
        var cow = cow(helper, fixture, 40.0F);
        record(helper, fixture, cow);
        hunt(helper, fixture);
        float injured = fixture.town().huntingTargets().get(cow.getUUID()).context(helper.getLevel()).getHealth();
        var nether = helper.getLevel().getServer().getLevel(net.minecraft.world.level.Level.NETHER);
        var transferred = EntityType.COW.create(nether);
        transferred.load(cow.saveWithoutId(new CompoundTag()));
        SettlementHuntingTargetService.onActive(transferred);
        helper.assertValueEqual(transferred.getHealth(), injured, "changing dimension cannot avoid pending native damage");
        helper.assertFalse(fixture.town().huntingTargets().containsKey(cow.getUUID()), "the applied cross-dimension handoff releases the old area observation");
        transferred.setHealth(injured - 1.0F);
        SettlementHuntingTargetService.onActive(transferred);
        helper.assertValueEqual(transferred.getHealth(), injured - 1.0F, "the retired record cannot replay over fresh native damage");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_hunting_combat")
    public static void lethalNativeReflectionRemovesTheHunterAndRetainsItsDeathReceipt(GameTestHelper helper) {
        var fixture = fixture(helper, false);
        var cow = cow(helper, fixture, 40.0F);
        record(helper, fixture, cow);
        Consumer<LivingIncomingDamageEvent> retaliate = event -> {
            if (event.getEntity().getUUID().equals(cow.getUUID())
                    && event.getSource().getEntity() instanceof Villager attacker) {
                attacker.hurt(helper.getLevel().damageSources().genericKill(), 1000.0F);
            }
        };
        NeoForge.EVENT_BUS.addListener(retaliate);
        try {
            hunt(helper, fixture);
            helper.assertFalse(fixture.town().workers().containsKey(fixture.worker().id()), "native lethal damage ends the hunter's work");
            helper.assertFalse(fixture.town().residents().containsKey(fixture.worker().id()), "the dead hunter cannot remain a living care recipient");
            helper.assertFalse(fixture.town().duties().containsKey(fixture.worker().id()), "death releases the civic assignment");
            helper.assertTrue(fixture.town().entityDeaths().containsKey(fixture.worker().id()), "the original hunter body has a persistent deletion receipt");
            helper.assertFalse(SettlementEntityDeathService.onJoined(fixture.villager()), "the old living hunter cannot return after lethal reflection");
        } finally {
            NeoForge.EVENT_BUS.unregister(retaliate);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 400, batch = "settlement_hunting_partial_chunks")
    public static void aLoadedNonTickingTargetAdvancesItsRealHitRecovery(GameTestHelper helper) {
        var fixture = fixture(helper, false);
        var level = helper.getLevel();
        var cow = cow(helper, fixture, 100.0F);
        var chunk = new net.minecraft.world.level.ChunkPos(fixture.town().center());
        var firstHealth = new float[1];
        fixture.worker().settings().putBoolean("Enabled", false);
        level.getChunkSource().addRegionTicket(net.minecraft.server.level.TicketType.FORCED, chunk, 0, chunk);
        level.getChunk(chunk.x, chunk.z);
        helper.startSequence().thenWaitUntil(() -> {
            helper.assertTrue(level.areEntitiesLoaded(chunk.toLong()), "the native entity storage is loaded");
            helper.assertFalse(level.isPositionEntityTicking(cow.blockPosition()), "the native target section does not tick");
        }).thenExecute(() -> {
            helper.assertTrue(level.addFreshEntity(cow), "the actual frozen target enters native storage");
            record(helper, fixture, cow);
            hunt(helper, fixture);
            firstHealth[0] = cow.getHealth();
            helper.assertTrue(firstHealth[0] < 100.0F, "the initial contact strike reaches the actual non-ticking body");
        }).thenIdle(40).thenExecute(() -> {
            try {
                SettlementHuntingTargetService.update(level, fixture.town(), level.getGameTime());
                hunt(helper, fixture);
                helper.assertTrue(cow.getHealth() < firstHealth[0], "observing a frozen body cannot continually reset its native damage immunity");
                helper.assertTrue(weaponDamage(helper, fixture) > 1, "later native recovery permits later strikes");
            } finally {
                cow.discard();
                level.getChunkSource().removeRegionTicket(net.minecraft.server.level.TicketType.FORCED, chunk, 0, chunk);
            }
        }).thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_hunting_combat")
    public static void nativeEffectRemovalAlsoRemovesItsOldAttributeModifiers(GameTestHelper helper) {
        var fixture = fixture(helper, false);
        var cow = cow(helper, fixture, 40.0F);
        cow.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED, 400, 1));
        var state = SettlementHuntingTarget.capture(cow, helper.getLevel().getGameTime());
        var target = state.context(helper.getLevel());
        var before = target.saveWithoutId(new CompoundTag());
        target.removeEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED);
        state.interacted(target, before, helper.getLevel().getGameTime());
        helper.assertTrue(state.restore(cow, helper.getLevel().getGameTime()), "the effect change is applied to the existing native body");
        helper.assertFalse(cow.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED), "an absent saved effect must actually leave the live effect map");
        helper.assertValueEqual(cow.getAttributeValue(Attributes.MOVEMENT_SPEED), cow.getAttribute(Attributes.MOVEMENT_SPEED).getBaseValue(),
                "native effect removal cannot leave a stale speed modifier behind");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_hunting_combat")
    public static void theHunterDoesNotKeepFrozenImmunityAcrossLaterNativeHits(GameTestHelper helper) {
        var fixture = fixture(helper, false);
        var cow = cow(helper, fixture, 500.0F);
        var level = helper.getLevel();
        long now = level.getGameTime();
        fixture.villager().getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0D);
        fixture.villager().setHealth(100.0F);
        fixture.worker().observe(level, fixture.villager(), new SettlementWorkforceService.Duty(fixture.town(), fixture.area()), 0L);
        fixture.town().recordResident(SettlementResidentState.capture(fixture.villager(), fixture.town(), 0L));
        fixture.town().recordHuntingTarget(SettlementHuntingTarget.capture(cow, Math.max(0L, now - 200L)));
        Consumer<LivingIncomingDamageEvent> retaliate = event -> {
            if (event.getEntity().getUUID().equals(cow.getUUID())
                    && event.getSource().getEntity() instanceof Villager attacker) {
                attacker.hurt(level.damageSources().thorns(event.getEntity()), 1.0F);
            }
        };
        NeoForge.EVENT_BUS.addListener(retaliate);
        try {
            hunt(helper, fixture);
            int hits = weaponDamage(helper, fixture);
            helper.assertTrue(hits > 1, "the running interval contains multiple native hits");
            helper.assertValueEqual(fixture.town().residents().get(fixture.worker().id()).health(), 100.0F - hits,
                    "the hunter's damage immunity advances alongside its attack recovery");
        } finally {
            NeoForge.EVENT_BUS.unregister(retaliate);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_hunting_combat")
    public static void laterParentRemovalSupersedesOlderNestedChanges(GameTestHelper helper) {
        for (boolean replacement : new boolean[]{false, true}) {
            var fixture = EntityType.COW.create(helper.getLevel());
            var nested = new CompoundTag();
            nested.putString("Marker", "before");
            fixture.getPersistentData().put("NativeHook", nested);
            var state = SettlementHuntingTarget.capture(fixture, helper.getLevel().getGameTime());
            var target = state.context(helper.getLevel());
            var before = target.saveWithoutId(new CompoundTag());
            target.getPersistentData().getCompound("NativeHook").putString("Marker", "changed");
            state.interacted(target, before, helper.getLevel().getGameTime());
            before = target.saveWithoutId(new CompoundTag());
            target.getPersistentData().remove("NativeHook");
            if (replacement) target.getPersistentData().putString("NativeHook", "replacement");
            state.interacted(target, before, helper.getLevel().getGameTime());
            var saved = SettlementHuntingTarget.load(state.save());
            helper.assertTrue(saved.restore(fixture, helper.getLevel().getGameTime()), "the coalesced native changes reach the older body");
            if (replacement) {
                helper.assertValueEqual(fixture.getPersistentData().getString("NativeHook"), "replacement",
                        "an older nested change cannot replace a newer scalar with a compound");
            } else {
                helper.assertFalse(fixture.getPersistentData().contains("NativeHook"),
                        "an older nested change cannot recreate a removed parent");
            }
        }
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
        SettlementExperienceTestTerrain.flat(town);
        return new Fixture(town, area, worker, villager);
    }

    private static Cow cow(GameTestHelper helper, Fixture fixture, float health) {
        var cow = EntityType.COW.create(helper.getLevel());
        cow.setPos(fixture.town().center().east().getBottomCenter());
        cow.setNoAi(true);
        cow.setNoGravity(true);
        cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(Math.max(10.0F, health));
        cow.setHealth(health);
        return cow;
    }

    private static void record(GameTestHelper helper, Fixture fixture, Cow cow) {
        fixture.town().recordHuntingTarget(SettlementHuntingTarget.capture(cow, helper.getLevel().getGameTime()));
    }

    private static void hunt(GameTestHelper helper, Fixture fixture) {
        SettlementBackgroundHunting.tick(helper.getLevel(), fixture.town(), fixture.worker(), 200, 1.0D);
    }

    private static int weaponDamage(GameTestHelper helper, Fixture fixture) {
        return fixture.worker().items(helper.getLevel().registryAccess()).get(HiredJobInventory.MAINHAND_SLOT).getDamageValue();
    }

    private record Fixture(SettlementRecord town, SettlementArea area, SettlementWorkerState worker, Villager villager) {
    }
}
