package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.HiredWorkArea;
import com.jvn.villagerretaliation.interaction.work.HiredFishingSpots;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("villagerretaliation")
@PrefixGameTestTemplate(false)
public final class SettlementFishingControlsGameTests {
    private SettlementFishingControlsGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_fishing_controls")
    public static void fishingPrioritiesAreBoundedOrderedAndRecoverable(GameTestHelper helper) {
        var position = new BlockPos.MutableBlockPos(1, 65, 2);
        var other = new BlockPos(3, 65, 4);
        var priorities = new HiredFishingSpots(List.of(position, other));
        position.set(20, 70, 30);
        helper.assertValueEqual(priorities.positions().getFirst(), new BlockPos(1, 65, 2), "preferences own immutable positions");
        helper.assertTrue(priorities.priority(other) == 1 && !priorities.selects(position), "priority order and selected spots are distinct");
        var state = new CompoundTag();
        helper.assertTrue(HiredFishingSpots.read(state).selects(position), "legacy fishers discover water automatically");
        helper.assertTrue(priorities.apply(state) && !priorities.apply(state), "unchanged preferences do not rewrite worker state");
        helper.assertValueEqual(HiredFishingSpots.read(state), priorities, "spot priorities survive saving");
        helper.assertTrue(HiredFishingSpots.AUTOMATIC.apply(state) && !state.contains(HiredFishingSpots.STATE_TAG),
                "returning to automatic discovery clears only the spot preference");
        invalid(helper, () -> new HiredFishingSpots(List.of(other, other)));
        invalid(helper, () -> new HiredFishingSpots(java.util.stream.IntStream.range(0, 17).mapToObj(i -> new BlockPos(i, 65, 0)).toList()));
        var damaged = priorities.save();
        damaged.putInt("Version", 2);
        invalid(helper, () -> HiredFishingSpots.load(damaged));
        var malformed = new CompoundTag();
        malformed.putString(HiredFishingSpots.STATE_TAG, "damaged");
        invalid(helper, () -> HiredFishingSpots.read(malformed));
        helper.assertValueEqual(malformed.getString(HiredFishingSpots.STATE_TAG), "damaged", "unknown preferences remain available for recovery");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_fishing_controls")
    public static void fishingPrioritiesInheritAndMigrateWithoutChangingOldDefaults(GameTestHelper helper) {
        var defaults = SettlementWorksitePlan.defaults(HiredVillagerRole.FISHING).settings();
        var preferred = new HiredFishingSpots(List.of(new BlockPos(4, 65, 4), new BlockPos(2, 65, 4)));
        var plan = defaults.withFishingSpots(preferred);
        var inheriting = defaults.withOverrides(0);
        helper.assertValueEqual(inheriting.resolve(plan).fishingSpots(), preferred, "new staff inherit shared priorities");
        var automatic = inheriting.withFishingSpots(HiredFishingSpots.AUTOMATIC);
        helper.assertTrue(automatic.resolve(plan).fishingSpots().positions().isEmpty(), "an individual can override the worksite with automatic fishing");
        helper.assertValueEqual(automatic.withOverrides(0).resolve(plan).fishingSpots(), preferred, "resetting overrides restores shared priorities");
        var state = new CompoundTag();
        preferred.apply(state);
        helper.assertValueEqual(SettlementWorkerSettings.capture(HiredVillagerRole.FISHING, state).fishingSpots(), preferred,
                "adoption captures an existing fisher's individual priorities");
        helper.assertValueEqual(SettlementWorkerSettings.load(plan.save()), plan, "versioned settings preserve ordered priorities");
        var legacy = defaults.save();
        legacy.putInt("Version", 2);
        legacy.remove("FishingSpots");
        legacy.putInt("Overrides", defaults.overrides() & ~SettlementWorkerSettings.FISHING_SPOTS_OVERRIDE);
        helper.assertValueEqual(SettlementWorkerSettings.load(legacy), defaults, "old fully overridden settings retain automatic fishing");
        legacy.putInt("Overrides", 0);
        helper.assertValueEqual(SettlementWorkerSettings.load(legacy).overrides(), 0, "old inherited workers continue inheriting");
        legacy.remove("Version");
        helper.assertValueEqual(SettlementWorkerSettings.load(legacy).fishingSpots(), HiredFishingSpots.AUTOMATIC,
                "unversioned saves have no selected spot preference");
        invalid(helper, () -> SettlementWorksitePlan.defaults(HiredVillagerRole.FARMING).settings().withFishingSpots(preferred));
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_fishing_controls")
    public static void fishingPreferencesUseExistingShoreBoundsAndWireLimits(GameTestHelper helper) {
        var area = HiredWorkArea.fromBounds(new BlockPos(0, 64, 0), new BlockPos(4, 67, 4), true);
        var preferred = new HiredFishingSpots(List.of(new BlockPos(-8, 64, 4), new BlockPos(12, 64, 4)));
        helper.assertTrue(preferred.within(area), "water can use the existing eight-block shore margin");
        helper.assertFalse(new HiredFishingSpots(List.of(new BlockPos(-9, 64, 4))).within(area), "preferences cannot expand work reach");
        var plan = new SettlementWorksitePlan(SettlementWorksitePlan.defaults(HiredVillagerRole.FISHING).settings().withFishingSpots(preferred), 2);
        var action = new com.jvn.villagerretaliation.network.SettlementManagementActionPayload(UUID.randomUUID(),
                new com.jvn.villagerretaliation.network.SettlementManagementActionPayload.SaveWorksite(UUID.randomUUID(), plan, true));
        var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            com.jvn.villagerretaliation.network.SettlementManagementActionPayload.STREAM_CODEC.encode(buffer, action);
            helper.assertValueEqual(com.jvn.villagerretaliation.network.SettlementManagementActionPayload.STREAM_CODEC.decode(buffer), action,
                    "worksite edits retain exact ordered water coordinates");
        } finally {
            buffer.release();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_fishing_controls")
    public static void backgroundFishingHonorsPriorityAndKeepsAnExistingCast(GameTestHelper helper) {
        SettlementFishingWorkGameTests.fixture(helper, false, fixture -> {
            var level = helper.getLevel();
            var other = SettlementFishingSpot.capture(level, fixture.area(), fixture.spot().water().south(), fixture.spot().approach(), fixture.chunk());
            helper.assertTrue(other != null, "the alternate water has a safe shore");
            fixture.town().recordFishingSpots(fixture.area().id(), List.of(fixture.spot(), other));
            var state = fixture.worker().settings();
            state.putBoolean("AutoDepositOutputs", false);
            new HiredFishingSpots(List.of(other.water(), fixture.spot().water())).apply(state);
            fixture.worker().work(40, 1.0D);
            SettlementBackgroundFishing.tick(level, fixture.town(), fixture.worker(), 1, 1.0D, 1L);
            helper.assertValueEqual(BlockPos.of(state.getCompound("SettlementFishingCast").getLong("Water")), other.water(),
                    "priority outranks observation order and distance");
            var progress = state.getCompound("SettlementFishingCast").getCompound("Progress");
            progress.putInt("Lured", 500);
            progress.putInt("Nibble", 0);
            progress.putLong("UpdatedAt", -1L);
            new HiredFishingSpots(List.of(fixture.spot().water())).apply(state);
            SettlementBackgroundFishing.tick(level, fixture.town(), fixture.worker(), 20, 1.0D, 21L);
            helper.assertValueEqual(state.getCompound("SettlementFishingCast").getCompound("Progress").getInt("Lured"), 480,
                    "changed preferences finish the current cast without restarting its clock");
            helper.assertValueEqual(BlockPos.of(state.getCompound("SettlementFishingCast").getLong("Water")), other.water(),
                    "the current cast retains its water");
            state.remove("SettlementFishingCast");
            fixture.worker().work(40, 1.0D);
            SettlementBackgroundFishing.tick(level, fixture.town(), fixture.worker(), 1, 1.0D, 22L);
            helper.assertValueEqual(BlockPos.of(state.getCompound("SettlementFishingCast").getLong("Water")), fixture.spot().water(),
                    "the next cast applies the new list");
            helper.assertFalse(level.hasChunkAt(other.water()), "priority selection never loads observed water");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_fishing_controls")
    public static void fishingClaimsSeparateWorkersAndReleaseWithDuty(GameTestHelper helper) {
        SettlementFishingWorkGameTests.fixture(helper, false, fixture -> {
            var level = helper.getLevel();
            SettlementFishingWorkGameTests.ready(helper, fixture, 0, 500, -1L);
            var other = UUID.randomUUID();
            helper.assertFalse(SettlementFishingClaims.available(level, other, fixture.spot().water()),
                    "a persisted background cast excludes another native or background fisher");
            helper.assertTrue(SettlementFishingClaims.available(level, fixture.worker().id(), fixture.spot().water()), "the owner can resume");
            var alternate = SettlementFishingSpot.capture(level, fixture.area(), fixture.spot().water().south(), fixture.spot().approach(), fixture.chunk());
            fixture.town().recordFishingSpots(fixture.area().id(), List.of(fixture.spot(), alternate));
            var saved = fixture.worker().save();
            saved.putUUID("Id", other);
            saved.getCompound("EntityContext").putUUID("UUID", other);
            saved.getCompound("Inventory").putUUID("Owner", other);
            var second = SettlementWorkerState.load(saved);
            second.settings().remove("SettlementFishingCast");
            second.settings().putBoolean("AutoDepositOutputs", false);
            new HiredFishingSpots(List.of(fixture.spot().water(), alternate.water())).apply(second.settings());
            fixture.town().assignDuty(other, fixture.area().id());
            fixture.town().recordWorker(second);
            second.work(40, 1.0D);
            SettlementBackgroundFishing.tick(level, fixture.town(), second, 1, 1.0D, 1L);
            helper.assertValueEqual(BlockPos.of(second.settings().getCompound("SettlementFishingCast").getLong("Water")), alternate.water(),
                    "a second fisher falls back to its next selected water");
            fixture.town().removeDuty(fixture.worker().id());
            helper.assertTrue(SettlementFishingClaims.available(level, other, fixture.spot().water()), "removing a duty releases its persisted claim");
            helper.assertFalse(level.hasChunkAt(alternate.water()), "claims never load worker terrain");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_fishing_controls")
    public static void nativeFishingKeepsPreferencesAndCastDuringCivicEdits(GameTestHelper helper) {
        SettlementFishingWorkGameTests.fixture(helper, true, fixture -> {
            var level = helper.getLevel();
            var villager = fixture.villager();
            helper.assertTrue(level.addFreshEntity(villager), "the native fisher is loaded");
            var leader = net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,
                    new com.mojang.authlib.GameProfile(fixture.town().leader(), "FishingLeader"));
            var visitor = net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,
                    new com.mojang.authlib.GameProfile(UUID.randomUUID(), "FishingVisitor"));
            var work = new com.jvn.villagerretaliation.interaction.work.FishingWorker();
            try {
                var selected = new HiredFishingSpots(List.of(fixture.spot().water().south(), fixture.spot().water()));
                var plan = SettlementWorksitePlan.defaults(HiredVillagerRole.FISHING);
                plan = new SettlementWorksitePlan(plan.settings().withFishingSpots(selected), 1);
                helper.assertFalse(SettlementWorksiteService.configure(visitor, fixture.town(), fixture.area().id(), plan, true),
                        "viewing a town does not grant fishing edit permission");
                helper.assertTrue(SettlementWorksiteService.configure(leader, fixture.town(), fixture.area().id(), plan, true),
                        "the leader adopts validated water priorities");
                helper.assertTrue(SettlementWorksiteService.reset(leader, fixture.town(), villager.getUUID(), fixture.area().id(),
                        SettlementWorkerSettings.FISHING_SPOTS_OVERRIDE), "the fisher opts into inherited priorities");
                var context = com.jvn.villagerretaliation.interaction.HiredWorkSession.active(level, villager).context();
                work.tick(level, villager, null, context);
                var state = context.state();
                helper.assertValueEqual(BlockPos.of(state.getLong("FishingWaterPos")), selected.positions().getFirst(), "native fishing uses the top selected water");
                var hook = level.getEntity(state.getInt("FishingActiveHookId"));
                helper.assertTrue(hook instanceof com.jvn.villagerretaliation.entity.VillagerFishingHook, "native work casts a real hook");
                var changed = new SettlementWorksitePlan(plan.settings().withFishingSpots(new HiredFishingSpots(List.of(fixture.spot().water()))), 1);
                helper.assertTrue(SettlementWorksiteService.configure(leader, fixture.town(), fixture.area().id(), changed, false), "the shared priority can change");
                helper.assertTrue(hook.isAlive() && state.getInt("FishingActiveHookId") == hook.getId(), "preference edits preserve the active native hook");
                work.maintain(level, villager, com.jvn.villagerretaliation.interaction.HiredWorkSession.active(level, villager).context());
                helper.assertTrue(hook.isAlive(), "maintenance does not cancel a cast excluded by the next preference list");
                var outside = new SettlementWorksitePlan(plan.settings().withFishingSpots(new HiredFishingSpots(List.of(fixture.spot().water().east(100)))), 1);
                helper.assertFalse(SettlementWorksiteService.configure(leader, fixture.town(), fixture.area().id(), outside, false),
                        "server validation rejects water beyond the fishery and territory");
            } finally {
                work.stop(level, villager, com.jvn.villagerretaliation.interaction.HiredWorkSession.active(level, villager).context());
                villager.discard();
            }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_fishing_controls")
    public static void personalFishingEditsRequireOwnerAndRecoverWithoutLosingCatch(GameTestHelper helper) {
        SettlementFishingWorkGameTests.fixture(helper, true, fixture -> {
            var level = helper.getLevel();
            var owner = net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,
                    new com.mojang.authlib.GameProfile(UUID.randomUUID(), "FishingOwner"));
            var visitor = net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,
                    new com.mojang.authlib.GameProfile(UUID.randomUUID(), "FishingStranger"));
            var villager = net.minecraft.world.entity.EntityType.VILLAGER.create(level);
            villager.setPos(fixture.spot().approach().getBottomCenter());
            villager.setNoAi(true);
            villager.setVillagerData(villager.getVillagerData().setProfession(net.minecraft.world.entity.npc.VillagerProfession.FISHERMAN));
            helper.assertTrue(level.addFreshEntity(villager), "the personal fisher is loaded");
            try {
                helper.assertTrue(com.jvn.villagerretaliation.interaction.HiredVillagerContractService.startHireContract(
                        level, villager, owner, 2, 8, HiredVillagerRole.FISHING), "a standalone owner hires the fisher");
                var state = com.jvn.villagerretaliation.interaction.HiredWorkStateStore.state(villager);
                HiredWorkArea.fromBounds(fixture.area().min(), fixture.area().max(), true).save(state);
                var spots = new HiredFishingSpots(List.of(fixture.spot().water()));
                var request = new com.jvn.villagerretaliation.network.HiredFishingRequestPayload(villager.getUUID(), UUID.randomUUID(), 0L, spots);
                helper.assertFalse(com.jvn.villagerretaliation.interaction.work.HiredFishingSpotService.apply(visitor, request).isEmpty(), "another player cannot edit a private fisher");
                helper.assertTrue(com.jvn.villagerretaliation.interaction.work.HiredFishingSpotService.apply(owner, request).isEmpty(), "the contract owner needs no civic authority");
                helper.assertValueEqual(HiredFishingSpots.read(state), spots, "accepted personal priorities reach worker state");
                helper.assertFalse(com.jvn.villagerretaliation.interaction.work.HiredFishingSpotService.apply(owner, request).isEmpty(), "a stale revision cannot overwrite a later edit");
                state.putString(HiredFishingSpots.STATE_TAG, "damaged");
                var catchReceipt = new CompoundTag();
                catchReceipt.putString("Identity", "preserved");
                state.put("SettlementFishingCatch", catchReceipt);
                var reset = new com.jvn.villagerretaliation.network.HiredFishingRequestPayload(villager.getUUID(), UUID.randomUUID(), 1L, HiredFishingSpots.AUTOMATIC);
                helper.assertTrue(com.jvn.villagerretaliation.interaction.work.HiredFishingSpotService.apply(owner, reset).isEmpty(), "an explicit automatic reset repairs malformed preferences");
                helper.assertValueEqual(state.getCompound("SettlementFishingCatch"), catchReceipt, "preference recovery preserves an undelivered catch");
                helper.assertTrue(HiredFishingSpots.read(state).positions().isEmpty(), "recovery selects automatic discovery");
            } finally {
                villager.discard();
            }
        });
    }

    private static void invalid(GameTestHelper helper, Runnable action) {
        boolean rejected = false;
        try {
            action.run();
        } catch (IllegalArgumentException exception) {
            rejected = true;
        }
        helper.assertTrue(rejected, "invalid fishing preferences are rejected");
    }
}
