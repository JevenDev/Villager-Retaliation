package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import java.util.ArrayList;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class SettlementExperienceGameTests {
    private SettlementExperienceGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_experience")
    public static void nativeExperienceAgesWithoutOfflineCatchupOrLostOwnership(GameTestHelper helper) {
        var fixture = fixture(helper, false);
        var orb = orb(helper, fixture, 3, 4, 100);
        UUID owner = UUID.randomUUID();
        orb.getPersistentData().putUUID("VillagerRetaliationWorkExperienceOwner", owner);
        var state = SettlementGroundExperience.capture(orb, 0L);
        fixture.town().recordGroundExperience(state);
        SettlementGroundExperienceService.update(helper.getLevel(), fixture.town(), 48_000L);
        helper.assertValueEqual(age(state.context(helper.getLevel())), 300, "one bounded running interval advances the native orb");
        helper.assertValueEqual(state.count(), 4, "aging retains the merged native count");
        helper.assertValueEqual(state.context(helper.getLevel()).getPersistentData().getUUID("VillagerRetaliationWorkExperienceOwner"),
                owner, "aging retains work experience ownership");
        long version = state.version();
        SettlementGroundExperienceService.update(helper.getLevel(), fixture.town(), 48_000L);
        helper.assertValueEqual(state.version(), version, "repeated observation cannot age the same interval twice");
        helper.assertFalse(helper.getLevel().hasChunkAt(fixture.town().center()), "background experience never loads the town");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_experience")
    public static void expiredNativeExperienceCannotReturnAfterSettlementSave(GameTestHelper helper) {
        var fixture = fixture(helper, false);
        var orb = orb(helper, fixture, 3, 2, 5990);
        var state = SettlementGroundExperience.capture(orb, 0L);
        fixture.town().recordGroundExperience(state);
        SettlementGroundExperienceService.update(helper.getLevel(), fixture.town(), 200L);
        helper.assertTrue(state.removed(), "running native experience expires at its actual lifespan");
        var saved = fixture.town().save(helper.getLevel().registryAccess());
        var restored = SettlementRecord.load(saved, helper.getLevel().registryAccess(), () -> {});
        helper.assertTrue(restored.groundExperience().get(orb.getUUID()).removed(), "expiration survives a settlement save");
        helper.assertFalse(SettlementGroundExperienceService.onActive(orb), "an older native orb is removed before resuming");
        saved.remove("GroundExperience");
        helper.assertTrue(SettlementRecord.load(saved, helper.getLevel().registryAccess(), () -> {}).groundExperience().isEmpty(),
                "legacy settlement saves need no native experience field");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_experience")
    public static void playerPickupAndOldSaveReplayPreserveOnlyUncollectedExperience(GameTestHelper helper) {
        var fixture = fixture(helper, true);
        var orb = orb(helper, fixture, 3, 3, 0);
        var player = helper.makeMockServerPlayerInLevel();
        player.setPos(fixture.town().center().getBottomCenter());
        helper.assertTrue(helper.getLevel().addFreshEntity(orb), "the native orb enters the settlement");
        try {
            var oldState = fixture.town().groundExperience().get(orb.getUUID()).save();
            var oldBody = orb.saveWithoutId(new CompoundTag());
            int before = player.totalExperience;
            orb.playerTouch(player);
            helper.assertValueEqual(player.totalExperience - before, 3, "native pickup credits exactly one orb value");
            helper.assertValueEqual(fixture.town().groundExperience().get(orb.getUUID()).count(), 2, "the partial native pickup updates settlement storage");
            fixture.town().removeGroundExperience(orb.getUUID());
            fixture.town().recordGroundExperience(SettlementGroundExperience.load(oldState));
            orb.load(oldBody);
            helper.assertTrue(SettlementGroundExperienceService.onJoined(orb), "the surviving native orb can return");
            helper.assertValueEqual(count(orb), 2, "the chunk receipt prevents an old town and body from restoring spent experience");
            for (int index = 0; index < 2; index++) {
                player.takeXpDelay = 0;
                orb.playerTouch(player);
            }
            helper.assertValueEqual(player.totalExperience - before, 9, "all pickups total the original finite experience");
            helper.assertTrue(orb.isRemoved(), "the final pickup removes the native orb");
            var replay = orb(helper, fixture, 1, 1, 0);
            replay.load(oldBody);
            helper.assertFalse(helper.getLevel().addFreshEntity(replay), "the removal receipt rejects an older consumed orb");
        } finally {
            orb.discard();
            player.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_experience")
    public static void workExperienceStillRejectsPlayerPickup(GameTestHelper helper) {
        var fixture = fixture(helper, true);
        var orb = orb(helper, fixture, 5, 2, 0);
        UUID owner = UUID.randomUUID();
        orb.getPersistentData().putUUID("VillagerRetaliationWorkExperienceOwner", owner);
        var player = helper.makeMockServerPlayerInLevel();
        helper.assertTrue(helper.getLevel().addFreshEntity(orb), "owned native work experience is present");
        try {
            int before = player.totalExperience;
            orb.playerTouch(player);
            helper.assertValueEqual(player.totalExperience, before, "the player cannot take owned work experience");
            helper.assertValueEqual(fixture.town().groundExperience().get(orb.getUUID()).count(), 2, "a denied pickup cannot reduce the native count");
            helper.assertValueEqual(orb.getPersistentData().getUUID("VillagerRetaliationWorkExperienceOwner"), owner,
                    "settlement observation retains the owner");
        } finally {
            orb.discard();
            player.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_experience")
    public static void actualVillagerMendingUpdatesTheRemainingNativeCount(GameTestHelper helper) {
        var fixture = fixture(helper, true);
        var villager = EntityType.VILLAGER.create(helper.getLevel());
        villager.setPos(fixture.town().center().getBottomCenter());
        villager.setNoAi(true);
        var sword = new ItemStack(Items.IRON_SWORD);
        sword.enchant(helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.MENDING), 1);
        sword.setDamageValue(20);
        villager.setItemSlot(EquipmentSlot.MAINHAND, sword);
        helper.assertTrue(helper.getLevel().addFreshEntity(villager), "the actual villager is present");
        var orb = orb(helper, fixture, 3, 2, 0);
        orb.getPersistentData().putUUID("VillagerRetaliationWorkExperienceOwner", villager.getUUID());
        helper.assertTrue(helper.getLevel().addFreshEntity(orb), "the actual owned orb is present");
        try {
            orb.tickCount = 1;
            orb.tick();
            orb.tick();
            helper.assertTrue(sword.getDamageValue() < 20, "native contact repairs the real equipped weapon");
            helper.assertValueEqual(count(orb), 1, "native Mending consumes one merged orb");
            helper.assertValueEqual(fixture.town().groundExperience().get(orb.getUUID()).count(), 1,
                    "native Mending records only the finite remainder");
        } finally {
            orb.discard();
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_experience")
    public static void nativeMergingPreservesCountsAndRetiresTheConsumedIdentity(GameTestHelper helper) {
        var fixture = fixture(helper, true);
        var first = orb(helper, fixture, 3, 3, 100);
        var second = orb(helper, fixture, 3, 2, 200);
        second.setId(first.getId() + 40);
        helper.assertTrue(helper.getLevel().addFreshEntity(first) && helper.getLevel().addFreshEntity(second), "both native merge candidates are present");
        var oldBody = second.saveWithoutId(new CompoundTag());
        try {
            first.tickCount = 1;
            first.tick();
            helper.assertTrue(second.isRemoved(), "the native merge consumes its donor entity");
            helper.assertValueEqual(count(first), 5, "the native recipient retains the combined count");
            helper.assertValueEqual(fixture.town().groundExperience().get(first.getUUID()).count(), 5, "the combined count reaches settlement storage");
            helper.assertFalse(fixture.town().groundExperience().containsKey(second.getUUID()), "the donor cannot remain a second resource");
            var replay = orb(helper, fixture, 1, 1, 0);
            replay.load(oldBody);
            helper.assertFalse(helper.getLevel().addFreshEntity(replay), "the donor removal receipt rejects an older body");
        } finally {
            first.discard();
            second.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_experience")
    public static void awardingIntoExistingNativeOrbsRecordsTheActualCombinedCount(GameTestHelper helper) {
        var fixture = fixture(helper, true);
        var orbs = new ArrayList<ExperienceOrb>();
        for (int index = 0; index < 40; index++) {
            var orb = orb(helper, fixture, 3, 1, 200);
            orbs.add(orb);
            helper.assertTrue(helper.getLevel().addFreshEntity(orb), "each native merge group is available");
        }
        try {
            ExperienceOrb.award(helper.getLevel(), fixture.town().center().getBottomCenter(), 3);
            helper.assertValueEqual(orbs.stream().mapToInt(SettlementExperienceGameTests::count).sum(), 41,
                    "the native award merges into exactly one existing orb");
            helper.assertValueEqual(fixture.town().groundExperience().values().stream().mapToInt(SettlementGroundExperience::count).sum(),
                    41, "the settlement observes the award's actual finite count");
        } finally {
            orbs.forEach(ExperienceOrb::discard);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_experience")
    public static void stopTickingCapturesChangesSinceTheLastNativeObservation(GameTestHelper helper) {
        var fixture = fixture(helper, true);
        var orb = orb(helper, fixture, 3, 5, 0);
        helper.assertTrue(helper.getLevel().addFreshEntity(orb), "the native orb is present");
        try {
            var changed = orb.saveWithoutId(new CompoundTag());
            changed.putInt("Count", 2);
            changed.putShort("Age", (short) 45);
            orb.load(changed);
            SettlementSimulationHandoff.stopTicking(orb);
            var state = fixture.town().groundExperience().get(orb.getUUID());
            helper.assertValueEqual(state.count(), 2, "the handoff captures the actual last native quantity");
            helper.assertValueEqual(age(state.context(helper.getLevel())), 45, "the handoff captures the actual last native age");
            helper.assertValueEqual(state.checkedAt(), helper.getLevel().getGameTime(), "the background clock starts at the native handoff");
        } finally {
            orb.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_experience")
    public static void activeOrbsKeepNativeAgingAndDiscardAuthority(GameTestHelper helper) {
        var fixture = fixture(helper, true);
        var orb = orb(helper, fixture, 3, 2, 100);
        helper.assertTrue(helper.getLevel().addFreshEntity(orb), "the active native orb is present");
        var saved = fixture.town().groundExperience().get(orb.getUUID()).save();
        SettlementGroundExperienceService.update(helper.getLevel(), fixture.town(), helper.getLevel().getGameTime() + 200L);
        helper.assertValueEqual(age(orb), 100, "background simulation does not also age an active orb");
        orb.discard();
        fixture.town().recordGroundExperience(SettlementGroundExperience.load(saved));
        SettlementGroundExperienceService.update(helper.getLevel(), fixture.town(), helper.getLevel().getGameTime());
        helper.assertFalse(fixture.town().groundExperience().containsKey(orb.getUUID()), "native removal retires a replayed older settlement record");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_experience")
    public static void overlappingDesignationsCannotCopyNativeExperience(GameTestHelper helper) {
        var fixture = fixture(helper, true);
        var other = new SettlementArea(UUID.randomUUID(), SettlementAreaType.HUNTING_GROUNDS, "Hunt",
                fixture.area().min(), fixture.area().max(), 5, true);
        fixture.town().putArea(fixture.town().leader(), other);
        var orb = orb(helper, fixture, 3, 2, 0);
        helper.assertTrue(helper.getLevel().addFreshEntity(orb), "one native orb is present in both areas");
        try {
            SettlementGroundExperienceService.observeArea(helper.getLevel(), fixture.town(), fixture.area());
            SettlementGroundExperienceService.observeArea(helper.getLevel(), fixture.town(), other);
            helper.assertValueEqual(fixture.town().groundExperience().size(), 1, "overlapping designations share one native orb");
            helper.assertValueEqual(fixture.town().groundExperience().get(orb.getUUID()).count(), 2, "overlap does not multiply experience");
        } finally {
            orb.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200, batch = "settlement_experience_viewing")
    public static void loadedNonTickingExperienceKeepsAgingWithoutDuplicateIntervals(GameTestHelper helper) {
        var fixture = fixture(helper, false);
        var level = helper.getLevel();
        var position = new ChunkPos(fixture.town().center());
        level.getChunkSource().addRegionTicket(TicketType.FORCED, position, 0, position);
        level.getChunk(position.x, position.z);
        helper.startSequence().thenWaitUntil(() -> {
            helper.assertTrue(level.areEntitiesLoaded(position.toLong()), "native entity storage is ready");
        }).thenExecute(() -> {
            var orb = orb(helper, fixture, 3, 4, 0);
            try {
                helper.assertFalse(level.isPositionEntityTicking(orb.blockPosition()), "the viewing chunk does not tick native entities");
                helper.assertTrue(level.addFreshEntity(orb), "the orb enters the actual non-ticking chunk");
                long now = level.getGameTime() + 200L;
                SettlementGroundExperienceService.update(level, fixture.town(), now);
                helper.assertValueEqual(age(orb), 200, "background aging reaches the actual non-ticking native orb");
                helper.assertValueEqual(count(orb), 4, "aging keeps its real count");
                long version = fixture.town().groundExperience().get(orb.getUUID()).version();
                SettlementGroundExperienceService.update(level, fixture.town(), now);
                helper.assertValueEqual(age(orb), 200, "the same background interval is not repeated");
                helper.assertValueEqual(fixture.town().groundExperience().get(orb.getUUID()).version(), version,
                        "reconciliation does not fabricate a second version for the same interval");
            } finally {
                orb.discard();
                level.getChunkSource().removeRegionTicket(TicketType.FORCED, position, 0, position);
            }
        }).thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 600, batch = "settlement_experience_chunks")
    public static void aRealNativeOrbPreservesItsAgeAndCountAcrossRepeatedUnloading(GameTestHelper helper) {
        var fixture = fixture(helper, false);
        var level = helper.getLevel();
        var position = new ChunkPos(fixture.town().center());
        var orb = orb(helper, fixture, 3, 4, 100);
        UUID owner = UUID.randomUUID();
        orb.getPersistentData().putUUID("VillagerRetaliationWorkExperienceOwner", owner);
        level.getChunkSource().addRegionTicket(TicketType.FORCED, position, 0, position);
        level.getChunk(position.x, position.z);
        helper.startSequence().thenWaitUntil(() -> {
            helper.assertTrue(level.areEntitiesLoaded(position.toLong()), "the actual orb chunk is ready");
        }).thenExecute(() -> {
            helper.assertTrue(level.addFreshEntity(orb), "the actual native orb enters its chunk");
            level.getChunkSource().removeRegionTicket(TicketType.FORCED, position, 0, position);
        }).thenWaitUntil(() -> {
            helper.assertFalse(level.areEntitiesLoaded(position.toLong()) || level.hasChunkAt(fixture.town().center()),
                    "the native orb unloads to real entity storage");
        }).thenExecute(() -> {
            var state = fixture.town().groundExperience().get(orb.getUUID());
            SettlementGroundExperienceService.update(level, fixture.town(), state.checkedAt() + 200L);
            helper.assertValueEqual(age(state.context(level)), 300, "the unloaded interval advances the recorded native age");
            level.getChunkSource().addRegionTicket(TicketType.FORCED, position, 0, position);
            level.getChunk(position.x, position.z);
        }).thenWaitUntil(() -> {
            helper.assertTrue(level.getEntity(orb.getUUID()) instanceof ExperienceOrb, "the original native identity reloads");
        }).thenExecute(() -> {
            var returning = (ExperienceOrb) level.getEntity(orb.getUUID());
            helper.assertValueEqual(age(returning), 300, "the old entity body receives background aging before resuming");
            helper.assertValueEqual(count(returning), 4, "reloading does not merge or duplicate its count");
            helper.assertValueEqual(returning.getPersistentData().getUUID("VillagerRetaliationWorkExperienceOwner"), owner,
                    "the actual native reload retains work ownership");
            level.getChunkSource().removeRegionTicket(TicketType.FORCED, position, 0, position);
        }).thenWaitUntil(() -> {
            helper.assertFalse(level.areEntitiesLoaded(position.toLong()) || level.hasChunkAt(fixture.town().center()),
                    "the same orb unloads a second time");
        }).thenExecute(() -> {
            var state = fixture.town().groundExperience().get(orb.getUUID());
            SettlementGroundExperienceService.update(level, fixture.town(), state.checkedAt() + 200L);
            level.getChunkSource().addRegionTicket(TicketType.FORCED, position, 0, position);
            level.getChunk(position.x, position.z);
        }).thenWaitUntil(() -> {
            helper.assertTrue(level.getEntity(orb.getUUID()) instanceof ExperienceOrb, "the second native reload completes");
        }).thenExecute(() -> {
            try {
                var returning = (ExperienceOrb) level.getEntity(orb.getUUID());
                helper.assertValueEqual(age(returning), 500, "each actual unloaded interval advances exactly once");
                helper.assertValueEqual(count(returning), 4, "repeated loading preserves the same finite resource");
            } finally {
                var returning = level.getEntity(orb.getUUID());
                if (returning != null) returning.discard();
                level.getChunkSource().removeRegionTicket(TicketType.FORCED, position, 0, position);
            }
        }).thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_experience")
    public static void enteringATownBeforeStopTickingCapturesPreviouslyUnobservedExperience(GameTestHelper helper) {
        var fixture = fixture(helper, true);
        var orb = orb(helper, fixture, 3, 3, 40);
        orb.setPos(fixture.town().center().east(12).getBottomCenter());
        helper.assertTrue(helper.getLevel().addFreshEntity(orb), "the orb starts outside this town");
        try {
            helper.assertFalse(fixture.town().groundExperience().containsKey(orb.getUUID()), "out-of-area experience is not claimed");
            orb.setPos(fixture.town().center().getBottomCenter());
            SettlementSimulationHandoff.stopTicking(orb);
            helper.assertValueEqual(fixture.town().groundExperience().get(orb.getUUID()).count(), 3,
                    "the native handoff captures an orb before the next area survey");
        } finally {
            orb.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_experience")
    public static void deliveredBirthExperienceEntersTheSameNativeLedger(GameTestHelper helper) {
        var fixture = fixture(helper, true);
        var level = helper.getLevel();
        var child = EntityType.COW.create(level);
        child.setPos(fixture.town().center().getBottomCenter());
        child.setAge(-24_000);
        var birth = SettlementAnimalBirth.create(child, 5);
        fixture.town().recordAnimalBirth(birth);
        try {
            helper.assertTrue(SettlementAnimalBirthService.materialize(level, fixture.town(), birth), "the actual birth and experience are delivered");
            var state = fixture.town().groundExperience().get(birth.experienceId());
            helper.assertTrue(state != null, "delivered birth experience is observed before the next area survey");
            helper.assertValueEqual(state.context(level).getValue(), 5, "the native ledger retains the actual birth reward");
            helper.assertFalse(com.jvn.villagerretaliation.villager.VillagerWorkExperience.isOwned(state.context(level)),
                    "ordinary birth experience keeps its native public ownership");
        } finally {
            var body = level.getEntity(child.getUUID());
            if (body != null) body.discard();
            var experience = level.getEntity(birth.experienceId());
            if (experience != null) experience.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_experience")
    public static void nativeRemovalAfterMovingAcknowledgesBothChunkLocations(GameTestHelper helper) {
        var fixture = fixture(helper, true);
        var level = helper.getLevel();
        var orb = orb(helper, fixture, 3, 1, 0);
        var player = helper.makeMockServerPlayerInLevel();
        helper.assertTrue(level.addFreshEntity(orb), "the actual orb is recorded at its original location");
        var original = orb.saveWithoutId(new CompoundTag());
        var destination = fixture.town().center().east(32);
        try {
            helper.assertTrue(level.getChunkSource().getChunkNow(destination.getX() >> 4, destination.getZ() >> 4) != null,
                    "the adjacent native destination chunk is present");
            orb.setPos(destination.getBottomCenter());
            var moved = orb.saveWithoutId(new CompoundTag());
            orb.playerTouch(player);
            helper.assertTrue(orb.isRemoved(), "native pickup consumes the moved orb");
            var oldBody = orb(helper, fixture, 1, 1, 0);
            oldBody.load(original);
            helper.assertFalse(level.addFreshEntity(oldBody), "the last recorded source chunk rejects its older body");
            var movedBody = orb(helper, fixture, 1, 1, 0);
            movedBody.load(moved);
            helper.assertFalse(level.addFreshEntity(movedBody), "the actual consumption chunk also rejects its older body");
        } finally {
            orb.discard();
            player.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_experience")
    public static void canceledNativeExperiencePickupKeepsItsFiniteCount(GameTestHelper helper) {
        var fixture = fixture(helper, true);
        var orb = orb(helper, fixture, 3, 2, 0);
        var player = helper.makeMockServerPlayerInLevel();
        helper.assertTrue(helper.getLevel().addFreshEntity(orb), "the native pickup candidate is present");
        java.util.function.Consumer<net.neoforged.neoforge.event.entity.player.PlayerXpEvent.PickupXp> cancel = event -> {
            if (event.getOrb() == orb) event.setCanceled(true);
        };
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(cancel);
        try {
            int before = player.totalExperience;
            orb.playerTouch(player);
            helper.assertValueEqual(player.totalExperience, before, "the actual native pickup event cancels the reward");
            helper.assertValueEqual(count(orb), 2, "event cancellation retains the actual orb");
            helper.assertValueEqual(fixture.town().groundExperience().get(orb.getUUID()).count(), 2,
                    "the native ledger does not manufacture consumption from a canceled event");
        } finally {
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(cancel);
            orb.discard();
            player.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_experience")
    public static void nativeAwardReconcilesExpiredCandidatesBeforeSearchingForAMerge(GameTestHelper helper) {
        var fixture = fixture(helper, true);
        var orb = orb(helper, fixture, 3, 2, 5990);
        helper.assertTrue(helper.getLevel().addFreshEntity(orb), "the native candidate is present before reconciliation");
        var state = fixture.town().groundExperience().get(orb.getUUID());
        state.advance(helper.getLevel(), new SettlementExperienceSimulation(helper.getLevel(), fixture.town()), state.checkedAt() + 200L);
        ExperienceOrb.award(helper.getLevel(), fixture.town().center().getBottomCenter(), 3);
        try {
            helper.assertTrue(orb.isRemoved(), "an expired pending candidate is removed before the award searches native storage");
            helper.assertFalse(fixture.town().groundExperience().containsKey(orb.getUUID()), "the expired identity is not credited by the new award");
            helper.assertValueEqual(fixture.town().groundExperience().values().stream().mapToInt(SettlementGroundExperience::count).sum(),
                    1, "only the newly awarded native experience remains");
        } finally {
            for (var current : java.util.List.copyOf(fixture.town().groundExperience().values())) {
                var body = helper.getLevel().getEntity(current.id());
                if (body != null) body.discard();
            }
            orb.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_experience")
    public static void nativeMergeReconcilesAnExpiredDonorAfterTheEntitySearch(GameTestHelper helper) {
        var fixture = fixture(helper, true);
        var recipient = orb(helper, fixture, 3, 1, 0);
        var donor = orb(helper, fixture, 3, 2, 5990);
        donor.setId(recipient.getId() + 40);
        helper.assertTrue(helper.getLevel().addFreshEntity(recipient) && helper.getLevel().addFreshEntity(donor), "native merge candidates are present");
        var state = fixture.town().groundExperience().get(donor.getUUID());
        state.advance(helper.getLevel(), new SettlementExperienceSimulation(helper.getLevel(), fixture.town()), state.checkedAt() + 200L);
        try {
            recipient.tickCount = 1;
            recipient.tick();
            helper.assertTrue(donor.isRemoved(), "the pending donor expires before its count can be merged");
            helper.assertValueEqual(count(recipient), 1, "expired experience cannot be absorbed by a native neighbor");
        } finally {
            recipient.discard();
            donor.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_experience")
    public static void anAwardOutsideTheDesignationStillObservesItsNativeRecipientInside(GameTestHelper helper) {
        var fixture = fixture(helper, true);
        var orbs = new ArrayList<ExperienceOrb>();
        var center = fixture.town().center();
        var inside = new Vec3(center.getX() + 4.9D, center.getY(), center.getZ() + 0.5D);
        var outside = inside.add(0.2D, 0.0D, 0.0D);
        helper.assertTrue(fixture.area().contains(BlockPos.containing(inside)) && !fixture.area().contains(BlockPos.containing(outside)),
                "the native award lies just across the designation boundary");
        for (int index = 0; index < 40; index++) {
            var orb = orb(helper, fixture, 3, 1, 0);
            orb.setPos(inside);
            orbs.add(orb);
            helper.assertTrue(helper.getLevel().addFreshEntity(orb), "all native recipients remain inside the designation");
        }
        try {
            ExperienceOrb.award(helper.getLevel(), outside, 3);
            helper.assertValueEqual(orbs.stream().mapToInt(SettlementExperienceGameTests::count).sum(), 41,
                    "the native award merges across the boundary");
            helper.assertValueEqual(fixture.town().groundExperience().values().stream().mapToInt(SettlementGroundExperience::count).sum(),
                    41, "the recipient's native count is observed even when the award position is outside");
        } finally {
            orbs.forEach(ExperienceOrb::discard);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_experience")
    public static void leavingTheTownReleasesNativeExperienceBeforeUnloading(GameTestHelper helper) {
        var fixture = fixture(helper, true);
        var orb = orb(helper, fixture, 3, 2, 100);
        helper.assertTrue(helper.getLevel().addFreshEntity(orb), "the native orb starts inside the town");
        try {
            orb.setPos(fixture.town().center().east(32).getBottomCenter());
            SettlementSimulationHandoff.stopTicking(orb);
            helper.assertFalse(fixture.town().groundExperience().containsKey(orb.getUUID()), "handoff releases experience that left the town");
            helper.assertTrue(orb.isAlive(), "leaving the designation does not consume native experience");
            helper.assertValueEqual(count(orb), 2, "the released native orb keeps its actual value");
            SettlementGroundExperienceService.update(helper.getLevel(), fixture.town(), helper.getLevel().getGameTime() + 200L);
            helper.assertValueEqual(age(orb), 100, "the old town cannot age an orb after it leaves");
        } finally {
            orb.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_experience")
    public static void nativePickupOutsideTheTownReleasesItsRemainingOrb(GameTestHelper helper) {
        var fixture = fixture(helper, true);
        var orb = orb(helper, fixture, 3, 2, 0);
        var player = helper.makeMockServerPlayerInLevel();
        helper.assertTrue(helper.getLevel().addFreshEntity(orb), "the native orb is initially part of this town");
        try {
            orb.setPos(fixture.town().center().east(32).getBottomCenter());
            orb.playerTouch(player);
            helper.assertValueEqual(count(orb), 1, "ordinary native pickup consumes one value outside the designation");
            helper.assertFalse(fixture.town().groundExperience().containsKey(orb.getUUID()),
                    "native observation releases the remaining orb from its old town");
        } finally {
            orb.discard();
            player.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_experience")
    public static void canceledNativeBirthExperienceCanRetryWithoutAPhantomRemoval(GameTestHelper helper) {
        var fixture = fixture(helper, true);
        var level = helper.getLevel();
        for (var floor : BlockPos.betweenClosed(fixture.town().center().offset(-4, -1, -4), fixture.town().center().offset(4, -1, 4))) {
            level.setBlockAndUpdate(floor, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        }
        var child = EntityType.COW.create(level);
        child.setPos(fixture.town().center().getBottomCenter());
        child.setAge(-24_000);
        var birth = SettlementAnimalBirth.create(child, 5);
        fixture.town().recordAnimalBirth(birth);
        java.util.function.Consumer<net.neoforged.neoforge.event.entity.EntityJoinLevelEvent> cancel = event -> {
            if (event.getEntity().getUUID().equals(birth.experienceId())) event.setCanceled(true);
        };
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST, cancel);
        try {
            helper.assertFalse(SettlementAnimalBirthService.materialize(level, fixture.town(), birth), "a later native event rejects the initial experience spawn");
            helper.assertTrue(birth.childApplied(), "the already accepted child remains delivered");
            helper.assertTrue(fixture.town().groundExperience().get(birth.experienceId()).pendingSpawn(),
                    "a rejected native orb remains explicitly unspawned and retryable");
            SettlementGroundExperienceService.update(level, fixture.town(), level.getGameTime() + 200L);
        } finally {
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(cancel);
        }
        try {
            helper.assertTrue(SettlementAnimalBirthService.materialize(level, fixture.town(), birth), "the same finite reward can be delivered after the native rejection is lifted");
            helper.assertTrue(level.getEntity(birth.experienceId()) instanceof ExperienceOrb, "the accepted retry retains its original native identity");
            helper.assertValueEqual(fixture.town().groundExperience().get(birth.experienceId()).context(level).getValue(), 5,
                    "only the successfully accepted native reward becomes a settlement resource");
        } finally {
            var body = level.getEntity(child.getUUID());
            if (body != null) body.discard();
            var experience = level.getEntity(birth.experienceId());
            if (experience != null) experience.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_experience")
    public static void rejectedDuplicateNativeIdentityCannotOverwriteItsAcceptedExperience(GameTestHelper helper) {
        var fixture = fixture(helper, true);
        var first = orb(helper, fixture, 3, 2, 0);
        helper.assertTrue(helper.getLevel().addFreshEntity(first), "the original native identity is accepted");
        try {
            var duplicate = orb(helper, fixture, 3, 7, 0);
            duplicate.setUUID(first.getUUID());
            helper.assertFalse(helper.getLevel().addFreshEntity(duplicate), "native storage rejects the duplicate identity");
            helper.assertValueEqual(fixture.town().groundExperience().get(first.getUUID()).count(), 2,
                    "the rejected duplicate cannot overwrite the accepted finite count");
            helper.assertValueEqual(count(first), 2, "the actual native recipient remains unchanged");
        } finally {
            first.discard();
        }
        helper.succeed();
    }

    private static Fixture fixture(GameTestHelper helper, boolean loaded) {
        var level = helper.getLevel();
        var origin = helper.absolutePos(new BlockPos(2, 2, 2));
        var center = loaded ? origin : origin.offset(13_000_000, 0, 13_000_000).atY(80);
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var id = registry.create(0L, level.dimension().location(), center, "");
        var town = SettlementSavedData.get(level).establish(registry, id, UUID.randomUUID(), center, 0L).orElseThrow();
        var area = new SettlementArea(UUID.randomUUID(), SettlementAreaType.ANIMAL_PEN, "Pen", center.offset(-1, -1, -1), center.offset(4, 3, 4), 5, true);
        town.putArea(town.leader(), area);
        return new Fixture(town, area);
    }

    private static ExperienceOrb orb(GameTestHelper helper, Fixture fixture, int value, int count, int age) {
        var position = fixture.town().center().getBottomCenter();
        var orb = new ExperienceOrb(helper.getLevel(), position.x, position.y, position.z, value);
        orb.setNoGravity(true);
        orb.setDeltaMovement(Vec3.ZERO);
        var tag = orb.saveWithoutId(new CompoundTag());
        tag.putInt("Count", count);
        tag.putShort("Age", (short) age);
        orb.load(tag);
        return orb;
    }

    private static int count(ExperienceOrb orb) {
        return orb.saveWithoutId(new CompoundTag()).getInt("Count");
    }

    private static int age(ExperienceOrb orb) {
        return orb.saveWithoutId(new CompoundTag()).getShort("Age");
    }

    private record Fixture(SettlementRecord town, SettlementArea area) {
    }
}
