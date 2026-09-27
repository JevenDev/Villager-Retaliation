package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.jvn.villagerretaliation.villager.VillagerWorkExperience;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class SettlementPendingExperienceGameTests {
    private SettlementPendingExperienceGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_pending_experience")
    public static void rejectedSpawnRemainsPendingInLoadedEmptyEntityStorage(GameTestHelper helper) {
        var fixture = fixture(helper, true);
        var level = helper.getLevel();
        var orb = orb(helper, fixture, 3);
        java.util.function.Consumer<net.neoforged.neoforge.event.entity.EntityJoinLevelEvent> cancel = event -> {
            if (event.getEntity().getUUID().equals(orb.getUUID())) event.setCanceled(true);
        };
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST, cancel);
        try {
            SettlementGroundExperienceService.submit(level, fixture.town(), orb, level.getGameTime());
            var state = fixture.town().groundExperience().get(orb.getUUID());
            SettlementGroundExperienceService.update(level, fixture.town(), state.checkedAt() + 200L);
            helper.assertTrue(state.pendingSpawn() && !state.removed(), "missing native storage does not consume an unspawned reward");
            helper.assertValueEqual(state.count(), 1, "rejected delivery preserves the finite count");
            helper.assertTrue(level.getEntity(orb.getUUID()) == null, "the canceled reward has no phantom native body");
            var restored = SettlementGroundExperience.load(state.save());
            helper.assertTrue(restored.pendingSpawn(), "the explicit pending state survives saving");
        } finally {
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(cancel);
        }
        var state = fixture.town().groundExperience().get(orb.getUUID());
        SettlementGroundExperienceService.materialize(level, fixture.town(), state);
        try {
            helper.assertTrue(level.getEntity(orb.getUUID()) instanceof ExperienceOrb, "the same reward can retry after cancellation ends");
            helper.assertFalse(fixture.town().groundExperience().get(orb.getUUID()).pendingSpawn(), "accepted native storage acknowledges delivery");
        } finally {
            var body = level.getEntity(orb.getUUID());
            if (body != null) body.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_pending_experience")
    public static void oldDeathRewardsJoinPassiveMendingWithoutASecondCopy(GameTestHelper helper) {
        var fixture = fixture(helper, false);
        SettlementExperienceTestTerrain.flat(fixture.town());
        var worker = worker(helper, fixture);
        var owner = worker.context(helper.getLevel(), fixture.town());
        var victim = EntityType.COW.create(helper.getLevel());
        victim.setPos(fixture.town().center().east().getBottomCenter());
        var orb = VillagerWorkExperience.create(helper.getLevel(), owner, victim.position(), 3);
        orb.setDeltaMovement(Vec3.ZERO);
        var death = SettlementEntityDeath.create(victim, victim.blockPosition(), worker, List.of(orb), helper.getLevel().getGameTime(), true);
        var legacy = death.save();
        fixture.town().recordEntityDeath(death);
        SettlementEntityDeathService.update(helper.getLevel(), fixture.town(), helper.getLevel().getGameTime());
        helper.assertTrue(death.experience().isEmpty(), "legacy death rewards leave their former private queue");
        var experience = fixture.town().groundExperience().get(orb.getUUID());
        helper.assertTrue(experience.pendingSpawn(), "the actual native reward identity is retained before delivery");
        SettlementGroundExperienceService.update(helper.getLevel(), fixture.town(), experience.checkedAt() + 200L);
        helper.assertTrue(experience.removed(), "physical contact consumes the pending death reward");
        int damage = worker.items(helper.getLevel().registryAccess()).get(HiredJobInventory.MAINHAND_SLOT).getDamageValue();
        helper.assertValueEqual(damage, 14, "one native value repairs exactly once");
        fixture.town().removeEntityDeath(death.id());
        fixture.town().recordEntityDeath(SettlementEntityDeath.load(legacy));
        SettlementEntityDeathService.update(helper.getLevel(), fixture.town(), helper.getLevel().getGameTime());
        SettlementGroundExperienceService.update(helper.getLevel(), fixture.town(), experience.checkedAt() + 200L);
        helper.assertValueEqual(worker.items(helper.getLevel().registryAccess()).get(HiredJobInventory.MAINHAND_SLOT).getDamageValue(),
                damage, "replaying an older death queue cannot replay its consumed reward");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200, batch = "settlement_pending_birth_expiry")
    public static void birthExperienceCanExpireBeforeItsChildMaterializes(GameTestHelper helper) {
        var fixture = fixture(helper, false);
        var level = helper.getLevel();
        var child = EntityType.COW.create(level);
        child.setPos(fixture.town().center().getBottomCenter());
        child.setBaby(true);
        var birth = SettlementAnimalBirth.create(child, 5);
        fixture.town().recordAnimalBirth(birth);
        SettlementAnimalBirthService.update(level, fixture.town());
        var experience = fixture.town().groundExperience().get(birth.experienceId());
        helper.assertTrue(experience.pendingSpawn() && !birth.childApplied(), "the reward runs independently of native child delivery");
        long now = experience.checkedAt();
        for (int step = 1; step <= 30; step++) SettlementGroundExperienceService.update(level, fixture.town(), now + step * 200L);
        helper.assertTrue(experience.removed(), "the pending breeding reward obeys the ordinary running lifespan");
        var saved = SettlementRecord.load(fixture.town().save(level.registryAccess()), level.registryAccess(), () -> {});
        helper.assertTrue(saved.animalBirths().get(child.getUUID()).experienceQueued(), "saving retains the reward handoff");
        helper.assertTrue(saved.groundExperience().get(birth.experienceId()).removed(), "saving retains expiry before delivery");
        var position = new ChunkPos(fixture.town().center());
        level.getChunkSource().addRegionTicket(TicketType.FORCED, position, 0, position);
        level.getChunk(position.x, position.z);
        helper.startSequence().thenWaitUntil(() -> {
            helper.assertTrue(level.areEntitiesLoaded(position.toLong()), "the original child section becomes available");
        }).thenExecute(() -> {
            try {
                helper.assertTrue(SettlementAnimalBirthService.materialize(level, fixture.town(), birth), "the living child can still be delivered");
                helper.assertTrue(level.getEntity(child.getUUID()) != null, "experience expiry does not delete the child");
                helper.assertTrue(level.getEntity(birth.experienceId()) == null, "native delivery cannot recreate expired breeding experience");
            } finally {
                var body = level.getEntity(child.getUUID());
                if (body != null) body.discard();
                level.getChunkSource().removeRegionTicket(TicketType.FORCED, position, 0, position);
            }
        }).thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_pending_experience")
    public static void everyQueuedBirthRewardAgesBeyondTheChildDeliveryBatchLimit(GameTestHelper helper) {
        var fixture = fixture(helper, false);
        var level = helper.getLevel();
        for (int index = 0; index < 25; index++) {
            var child = EntityType.COW.create(level);
            child.setPos(fixture.town().center().getBottomCenter());
            fixture.town().recordAnimalBirth(SettlementAnimalBirth.create(child, 1));
        }
        SettlementAnimalBirthService.update(level, fixture.town());
        helper.assertValueEqual(fixture.town().groundExperience().size(), 25, "child delivery batching cannot postpone reward registration");
        SettlementGroundExperienceService.update(level, fixture.town(), level.getGameTime() + 200L);
        helper.assertTrue(fixture.town().groundExperience().values().stream()
                        .allMatch(experience -> experience.save().getCompound("Entity").getShort("Age") == 200),
                "every queued reward receives the same finite running interval");
        helper.assertFalse(level.hasChunkAt(fixture.town().center()), "reward lifetime does not load the birth queue");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_pending_experience")
    public static void legacyBirthWithoutAClockStartsOnceWhenObserved(GameTestHelper helper) {
        var fixture = fixture(helper, false);
        var child = EntityType.COW.create(helper.getLevel());
        child.setPos(fixture.town().center().getBottomCenter());
        var tag = SettlementAnimalBirth.create(child, 3).save();
        tag.remove("CreatedAt");
        tag.remove("ExperienceQueued");
        var birth = SettlementAnimalBirth.load(tag);
        fixture.town().recordAnimalBirth(birth);
        SettlementAnimalBirthService.update(helper.getLevel(), fixture.town());
        var state = fixture.town().groundExperience().get(birth.experienceId());
        helper.assertValueEqual((int) state.save().getCompound("Entity").getShort("Age"), 0, "legacy migration does not invent an offline age");
        SettlementAnimalBirthService.update(helper.getLevel(), fixture.town());
        helper.assertValueEqual(fixture.town().groundExperience().size(), 1, "the legacy reward is imported once");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_pending_experience")
    public static void zeroValueBreedingRewardsCreateNoOrb(GameTestHelper helper) {
        var fixture = fixture(helper, false);
        var child = EntityType.COW.create(helper.getLevel());
        child.setPos(fixture.town().center().getBottomCenter());
        var birth = SettlementAnimalBirth.create(child, 0);
        fixture.town().recordAnimalBirth(birth);
        SettlementAnimalBirthService.update(helper.getLevel(), fixture.town());
        helper.assertTrue(birth.experienceQueued(), "zero-value birth rewards complete their handoff");
        helper.assertTrue(fixture.town().groundExperience().isEmpty(), "the native mob-loot rule cannot fabricate a reward");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_pending_experience")
    public static void legacyFishingRewardsKeepDistinctStableIdentitiesAndAge(GameTestHelper helper) {
        var fixture = fixture(helper, false);
        var worker = worker(helper, fixture);
        var entries = new ListTag();
        for (int value : new int[]{3, 4}) {
            var entry = new CompoundTag();
            entry.putInt("Value", value);
            entry.putLong("Expires", helper.getLevel().getGameTime() + 1_000L);
            entries.add(entry);
        }
        worker.settings().put("SettlementFishingExperience", entries.copy());
        SettlementBackgroundFishing.migrateExperience(helper.getLevel(), fixture.town(), worker);
        var ids = java.util.Set.copyOf(fixture.town().groundExperience().keySet());
        helper.assertValueEqual(ids.size(), 2, "two legacy entries with one expiry retain distinct finite identities");
        helper.assertTrue(fixture.town().groundExperience().values().stream()
                        .allMatch(experience -> experience.save().getCompound("Entity").getShort("Age") == 5_000),
                "migration preserves the remaining native lifespan");
        worker.settings().put("SettlementFishingExperience", entries.copy());
        SettlementBackgroundFishing.migrateExperience(helper.getLevel(), fixture.town(), worker);
        helper.assertValueEqual(fixture.town().groundExperience().keySet(), ids, "an older worker snapshot imports the same identities");
        helper.assertValueEqual(fixture.town().groundExperience().values().stream()
                .mapToInt(experience -> experience.context(helper.getLevel()).getValue()).sum(), 7, "migration cannot multiply the original reward values");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_pending_experience")
    public static void expiredLegacyFishingRewardsCannotBeReissued(GameTestHelper helper) {
        var fixture = fixture(helper, false);
        var worker = worker(helper, fixture);
        var entry = new CompoundTag();
        entry.putInt("Value", 5);
        entry.putLong("Expires", helper.getLevel().getGameTime());
        var entries = new ListTag();
        entries.add(entry);
        worker.settings().put("SettlementFishingExperience", entries);
        SettlementBackgroundFishing.migrateExperience(helper.getLevel(), fixture.town(), worker);
        helper.assertFalse(worker.settings().contains("SettlementFishingExperience"), "expired legacy entries are retired");
        helper.assertTrue(fixture.town().groundExperience().isEmpty(), "expired rewards cannot be revived with a new native identity");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_pending_experience")
    public static void conflictingNativeIdentityCannotEraseAnUnspawnedReward(GameTestHelper helper) {
        var fixture = fixture(helper, true);
        var orb = orb(helper, fixture, 3);
        var existing = EntityType.COW.create(helper.getLevel());
        existing.setUUID(orb.getUUID());
        existing.setPos(orb.position());
        helper.assertTrue(helper.getLevel().addFreshEntity(existing), "the native conflicting identity is accepted first");
        try {
            SettlementGroundExperienceService.submit(helper.getLevel(), fixture.town(), orb, helper.getLevel().getGameTime());
            var state = fixture.town().groundExperience().get(orb.getUUID());
            SettlementGroundExperienceService.update(helper.getLevel(), fixture.town(), state.checkedAt() + 200L);
            helper.assertTrue(state.pendingSpawn() && !state.removed(), "an occupied native identity cannot silently consume the reward");
            helper.assertValueEqual((int) state.save().getCompound("Entity").getShort("Age"), 200,
                    "an occupied native identity cannot freeze the finite reward lifetime");
            helper.assertTrue(existing.isAlive(), "the pending reward cannot replace an unrelated native entity");
        } finally {
            existing.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_pending_experience")
    public static void resubmittingACollectedRewardCannotCreditThePlayerAgain(GameTestHelper helper) {
        var fixture = fixture(helper, true);
        var level = helper.getLevel();
        var reward = orb(helper, fixture, 3);
        var snapshot = reward.saveWithoutId(new CompoundTag());
        var player = helper.makeMockServerPlayerInLevel();
        try {
            SettlementGroundExperienceService.submit(level, fixture.town(), reward, level.getGameTime());
            var nativeOrb = (ExperienceOrb) level.getEntity(reward.getUUID());
            int before = player.totalExperience;
            nativeOrb.playerTouch(player);
            helper.assertValueEqual(player.totalExperience - before, 3, "the native player receives the original reward");
            var replay = orb(helper, fixture, 3);
            replay.load(snapshot);
            SettlementGroundExperienceService.submit(level, fixture.town(), replay, level.getGameTime());
            helper.assertTrue(level.getEntity(reward.getUUID()) == null, "the origin receipt rejects an older unspawned reward");
            helper.assertValueEqual(player.totalExperience - before, 3, "resubmission cannot credit a second reward");
        } finally {
            player.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_pending_experience")
    public static void resubmittingAPartiallyCollectedRewardKeepsItsFiniteRemainder(GameTestHelper helper) {
        var fixture = fixture(helper, true);
        var level = helper.getLevel();
        var reward = orb(helper, fixture, 3);
        var snapshot = reward.saveWithoutId(new CompoundTag());
        snapshot.putInt("Count", 3);
        reward.load(snapshot);
        var player = helper.makeMockServerPlayerInLevel();
        try {
            SettlementGroundExperienceService.submit(level, fixture.town(), reward, level.getGameTime());
            var nativeOrb = (ExperienceOrb) level.getEntity(reward.getUUID());
            nativeOrb.playerTouch(player);
            var replay = orb(helper, fixture, 3);
            replay.load(snapshot);
            SettlementGroundExperienceService.submit(level, fixture.town(), replay, level.getGameTime());
            helper.assertValueEqual(nativeOrb.saveWithoutId(new CompoundTag()).getInt("Count"), 2, "the native remainder cannot be overwritten by the old reward count");
            helper.assertValueEqual(fixture.town().groundExperience().size(), 1, "the duplicate submission cannot create a second identity");
        } finally {
            var body = level.getEntity(reward.getUUID());
            if (body != null) body.discard();
            player.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_pending_experience")
    public static void fishingQueueMigrationCannotRollBackFreshNativeEquipmentWear(GameTestHelper helper) {
        var fixture = fixture(helper, true);
        var level = helper.getLevel();
        var worker = worker(helper, fixture);
        var villager = worker.context(level, fixture.town());
        helper.assertTrue(level.addFreshEntity(villager), "the native worker is present");
        villager.getMainHandItem().setDamageValue(30);
        HiredJobInventory.synchronizeEquipmentDurability(villager, EquipmentSlot.MAINHAND);
        var entry = new CompoundTag();
        entry.putInt("Value", 3);
        entry.putLong("Expires", level.getGameTime() + 6_000L);
        var entries = new ListTag();
        entries.add(entry);
        worker.settings().put("SettlementFishingExperience", entries);
        try {
            SettlementBackgroundFishing.migrateExperience(level, fixture.town(), worker);
            helper.assertFalse(worker.restore(villager), "a queue-only migration acknowledges the current native inventory revision");
            helper.assertValueEqual(villager.getMainHandItem().getDamageValue(), 30, "queue migration cannot restore an older tool snapshot");
            helper.assertValueEqual(worker.items(level.registryAccess()).get(HiredJobInventory.MAINHAND_SLOT).getDamageValue(), 30,
                    "subsequent background work also retains the current native tool wear");
        } finally {
            for (var experience : List.copyOf(fixture.town().groundExperience().values())) {
                var body = level.getEntity(experience.id());
                if (body != null) body.discard();
            }
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_pending_experience")
    public static void legacyFishingRewardDoesNotRequireAnOldEntityContext(GameTestHelper helper) {
        var fixture = fixture(helper, false);
        var original = worker(helper, fixture);
        var saved = original.save();
        saved.remove("EntityContext");
        var worker = SettlementWorkerState.load(saved);
        fixture.town().recordWorker(worker);
        var entry = new CompoundTag();
        entry.putInt("Value", 3);
        entry.putLong("Expires", helper.getLevel().getGameTime() + 6_000L);
        var entries = new ListTag();
        entries.add(entry);
        worker.settings().put("SettlementFishingExperience", entries);
        SettlementBackgroundFishing.migrateExperience(helper.getLevel(), fixture.town(), worker);
        helper.assertFalse(worker.settings().contains("SettlementFishingExperience"), "legacy rewards can migrate after worker context changes");
        helper.assertValueEqual(fixture.town().groundExperience().size(), 1, "the legacy reward retains one independent identity");
        var orb = fixture.town().groundExperience().values().iterator().next().context(helper.getLevel());
        helper.assertValueEqual(orb.getPersistentData().getUUID("VillagerRetaliationWorkExperienceOwner"), worker.id(),
                "migration retains ownership without inventing a villager body");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_pending_experience")
    public static void pendingExperienceBelowTheWorldCannotWaitForARewardDelivery(GameTestHelper helper) {
        var fixture = fixture(helper, false);
        var level = helper.getLevel();
        var orb = orb(helper, fixture, 3);
        orb.setPos(orb.getX(), level.getMinBuildHeight() - 65.0D, orb.getZ());
        SettlementGroundExperienceService.submit(level, fixture.town(), orb, level.getGameTime());
        var state = fixture.town().groundExperience().get(orb.getUUID());
        SettlementGroundExperienceService.update(level, fixture.town(), state.checkedAt() + 1L);
        helper.assertTrue(state.removed(), "pending experience obeys native below-world removal");
        helper.assertTrue(level.getEntity(orb.getUUID()) == null, "an impossible spawn location cannot create a native reward later");
        helper.succeed();
    }

    private static Fixture fixture(GameTestHelper helper, boolean loaded) {
        var level = helper.getLevel();
        var origin = helper.absolutePos(new BlockPos(2, 2, 2));
        var center = loaded ? origin : origin.offset(15_000_000, 0, 15_000_000).atY(85);
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var id = registry.create(0L, level.dimension().location(), center, "");
        var town = SettlementSavedData.get(level).establish(registry, id, UUID.randomUUID(), center, 0L).orElseThrow();
        var area = new SettlementArea(UUID.randomUUID(), SettlementAreaType.ANIMAL_PEN, "Pen", center.offset(-4, -2, -4),
                center.offset(6, 4, 6), 5, true);
        town.putArea(town.leader(), area);
        return new Fixture(town, area);
    }

    private static ExperienceOrb orb(GameTestHelper helper, Fixture fixture, int value) {
        var position = fixture.town().center().getBottomCenter();
        var orb = new ExperienceOrb(helper.getLevel(), position.x, position.y, position.z, value);
        orb.setNoGravity(true);
        orb.setDeltaMovement(Vec3.ZERO);
        return orb;
    }

    private static SettlementWorkerState worker(GameTestHelper helper, Fixture fixture) {
        Villager villager = EntityType.VILLAGER.create(helper.getLevel());
        villager.setPos(fixture.town().center().getBottomCenter());
        villager.setNoAi(true);
        var sword = new ItemStack(Items.IRON_SWORD);
        sword.enchant(helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.MENDING), 1);
        sword.setDamageValue(20);
        HiredJobInventory.getJobInventory(villager).setItem(HiredJobInventory.MAINHAND_SLOT, sword);
        villager.setItemSlot(EquipmentSlot.MAINHAND, sword.copy());
        fixture.town().assignDuty(villager.getUUID(), fixture.area().id());
        var worker = SettlementWorkerState.capture(helper.getLevel(), villager,
                new SettlementWorkforceService.Duty(fixture.town(), fixture.area()), helper.getLevel().getGameTime());
        fixture.town().recordWorker(worker);
        fixture.town().recordResident(SettlementResidentState.capture(villager, fixture.town(), helper.getLevel().getGameTime()));
        return worker;
    }

    private record Fixture(SettlementRecord town, SettlementArea area) {
    }
}
