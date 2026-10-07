package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.AllegianceAssignmentSource;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceApi;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.social.VillagerSocialGraphSavedData;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.villager.VillagerGender;
import com.jvn.villagerretaliation.villager.VillagerPresetNameRegistry;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("villager_life")
@PrefixGameTestTemplate(false)
public final class SettlementFamilyBirthGameTests {
    private SettlementFamilyBirthGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_family_birth")
    public static void unloadedCourtshipCreatesOneChildWithNativeFoodCooldownAndFamily(GameTestHelper helper) {
        var fixture = remote(helper, 6);
        long now = helper.getLevel().getGameTime();
        daytime(helper, () -> {
            step(helper, fixture, now);
            helper.assertValueEqual(fixture.town.courtships().size(), 1, "two eligible parents begin a real timed visit");
            helper.assertTrue(fixture.town.arrivals().isEmpty(), "beginning courtship does not instantly create a child");
            for (int interval = 1; interval <= 5; interval++) step(helper, fixture, now + interval * 200L);
            helper.assertValueEqual(fixture.town.arrivals().size(), 1, "one completed visit records exactly one newborn");
            var childId = fixture.town.arrivals().keySet().iterator().next();
            var child = fixture.town.residents().get(childId).context(helper.getLevel());
            helper.assertValueEqual(child.getAge(), -24_000, "newborns retain the native childhood duration");
            helper.assertValueEqual(VillageAllegianceApi.get(child).orElseThrow().assignmentSource(), AllegianceAssignmentSource.BIRTH,
                    "birth membership is preserved instead of being converted into an applicant admission");
            var graph = VillagerSocialGraphSavedData.get(helper.getLevel());
            helper.assertTrue(graph.relationships(childId, VillagerSocialGraphSavedData.RelationshipType.BIRTH_PARENT)
                    .containsAll(List.of(fixture.first.getUUID(), fixture.second.getUUID())), "both birth parents are retained");
            for (var parent : List.of(fixture.first, fixture.second)) {
                var care = fixture.town.residents().get(parent.getUUID());
                var returning = EntityType.VILLAGER.create(helper.getLevel());
                returning.load(parent.saveWithoutId(new CompoundTag()));
                helper.assertTrue(care.restore(returning), "the old body receives the completed birth checkpoint");
                helper.assertValueEqual(returning.getAge(), 6_000, "native parent breeding cooldown survives handoff");
                helper.assertValueEqual(SettlementPopulationService.familyFood(returning), 0, "twelve actual food points were consumed");
                helper.assertFalse(care.restore(returning), "the parent checkpoint is applied once");
            }
            var bed = fixture.town.homes().get(childId).bed();
            helper.assertFalse(SettlementHousingService.canUse(helper.getLevel(), bed, null), "the newborn reserves one individual home");
            helper.assertFalse(SettlementPopulationService.canGrow(helper.getLevel(), fixture.town), "the pending child immediately occupies population capacity");
            helper.assertFalse(helper.getLevel().hasChunkAt(fixture.town.center()), "native offspring creation does not load the remote town");
            helper.assertTrue(helper.getLevel().getEntity(childId) == null, "the child remains pending until its actual location loads");
        });
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_family_birth")
    public static void travelCostsTimeAndBothNativeAnchorsArePreserved(GameTestHelper helper) {
        var fixture = remote(helper, 24);
        long now = helper.getLevel().getGameTime();
        daytime(helper, () -> {
            step(helper, fixture, now);
            step(helper, fixture, now + 200L);
            step(helper, fixture, now + 400L);
            helper.assertTrue(fixture.town.arrivals().isEmpty(), "parents across town still owe their observed journey");
            for (int interval = 3; interval <= 7; interval++) step(helper, fixture, now + interval * 200L);
            helper.assertValueEqual(fixture.town.arrivals().size(), 1, "an observed visit can cross unloaded chunk boundaries");
            helper.assertValueEqual(fixture.town.residents().get(fixture.first.getUUID()).position(), fixture.first.blockPosition(),
                    "the visit returns the first parent to its native anchor");
            helper.assertValueEqual(fixture.town.residents().get(fixture.second.getUUID()).position(), fixture.second.blockPosition(),
                    "the other parent retains its own anchor");
        });
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_family_birth")
    public static void savedCourtshipProgressIsCappedAndDoesNotCatchUpOffline(GameTestHelper helper) {
        var fixture = remote(helper, 24);
        long now = helper.getLevel().getGameTime();
        daytime(helper, () -> {
            step(helper, fixture, now);
            step(helper, fixture, now + 200L);
            var saved = SettlementRecord.load(fixture.town.save(helper.getLevel().registryAccess()), helper.getLevel().registryAccess(), () -> {});
            var attempt = saved.courtships().values().iterator().next();
            helper.assertValueEqual(attempt.progress(), 200, "courtship progress survives a save round trip");
            fixture.town.recordCourtship(attempt);
            step(helper, fixture, now + 8_000_000L);
            helper.assertValueEqual(fixture.town.courtships().values().iterator().next().progress(), 400, "a long server gap grants one bounded interval");
            step(helper, fixture, now + 8_000_000L);
            helper.assertValueEqual(fixture.town.courtships().values().iterator().next().progress(), 400, "a repeated timestamp cannot advance twice");
            helper.assertTrue(fixture.town.arrivals().isEmpty(), "offline elapsed time cannot finish the visit");
        });
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_family_birth")
    public static void unobservedTerrainCannotConnectParentsOrBeds(GameTestHelper helper) {
        var fixture = remote(helper, 24);
        fixture.town.retainNavigation(java.util.Set.of());
        daytime(helper, () -> step(helper, fixture, helper.getLevel().getGameTime()));
        helper.assertTrue(fixture.town.courtships().isEmpty(), "unknown terrain is not treated as a walkable empty town");
        helper.assertValueEqual(SettlementPopulationService.familyFood(fixture.town.residents().get(fixture.first.getUUID()).context(helper.getLevel())),
                12, "missing access does not consume the parents' provisions");
        helper.assertFalse(helper.getLevel().hasChunkAt(fixture.town.center()), "path finding does not force missing chunks");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_family_birth")
    public static void blockedRouteCancelsAnExistingVisitWithoutSpendingFood(GameTestHelper helper) {
        var fixture = remote(helper, 24);
        long now = helper.getLevel().getGameTime();
        daytime(helper, () -> {
            step(helper, fixture, now);
            helper.assertFalse(fixture.town.courtships().isEmpty(), "the initial observed route is usable");
            for (var position : BlockPos.betweenClosed(fixture.town.center().offset(12, 0, -1), fixture.town.center().offset(12, 3, 5))) {
                set(fixture, position, Blocks.STONE.defaultBlockState());
            }
            observe(helper, fixture);
            step(helper, fixture, now + 200L);
            helper.assertTrue(fixture.town.courtships().isEmpty(), "changed walls invalidate the ongoing journey");
            helper.assertValueEqual(SettlementPopulationService.familyFood(fixture.town.residents().get(fixture.first.getUUID()).context(helper.getLevel())),
                    12, "an interrupted visit preserves real family food");
        });
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_family_birth")
    public static void losingAReservedBedOrFoodSupportStopsCourtship(GameTestHelper helper) {
        var fixture = remote(helper, 6);
        long now = helper.getLevel().getGameTime();
        daytime(helper, () -> {
            step(helper, fixture, now);
            var bed = fixture.town.courtships().values().iterator().next().bed();
            fixture.town.assignHome(fixture.first.getUUID(), bed);
            step(helper, fixture, now + 200L);
            helper.assertTrue(fixture.town.courtships().values().stream().noneMatch(attempt -> attempt.bed().equals(bed)),
                    "an individual home assignment takes the bed out of the current visit");
            fixture.depot.clearContent();
            step(helper, fixture, now + 400L);
            helper.assertTrue(fixture.town.courtships().isEmpty(), "a town food shortage stops all growth attempts");
            helper.assertTrue(fixture.town.arrivals().isEmpty(), "no child is admitted after support is lost");
        });
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_family_birth")
    public static void currentCensusAndFamilyRulesAreRecheckedDuringTheVisit(GameTestHelper helper) {
        var fixture = remote(helper, 6);
        long now = helper.getLevel().getGameTime();
        daytime(helper, () -> {
            step(helper, fixture, now);
            var graph = VillagerSocialGraphSavedData.get(helper.getLevel());
            var ancestor = EntityType.VILLAGER.create(helper.getLevel());
            graph.linkParentsAndChild(helper.getLevel(), fixture.first, ancestor, fixture.second);
            var setting = com.jvn.villagerretaliation.config.VillagerRetaliationConfig.ENABLE_FAMILY_BREEDING_RULES;
            boolean previous = setting.get();
            try {
                setting.set(true);
                step(helper, fixture, now + 200L);
                helper.assertTrue(fixture.town.courtships().isEmpty(), "the enabled family policy cancels a newly disallowed pair");
            } finally {
                setting.set(previous);
            }
            VillageAllegianceRegistrySavedData.get(helper.getLevel()).removeResident(fixture.town.id(), fixture.second.getUUID());
            step(helper, fixture, now + 400L);
            helper.assertTrue(fixture.town.courtships().isEmpty(), "an old care record cannot act for a departed census member");
        });
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_family_birth")
    public static void nightTimeStopsBackgroundCourtship(GameTestHelper helper) {
        var fixture = remote(helper, 6);
        var level = helper.getLevel();
        long previous = level.getDayTime();
        long now = level.getGameTime();
        try {
            level.setDayTime(10_000L);
            step(helper, fixture, now);
            level.setDayTime(14_000L);
            step(helper, fixture, now + 200L);
            helper.assertTrue(fixture.town.courtships().isEmpty(), "night time is reserved for normal rest");
            helper.assertTrue(fixture.town.arrivals().isEmpty(), "background rest does not produce a child");
        } finally {
            level.setDayTime(previous);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_family_birth")
    public static void nativeSpawnCancellationConsumesFoodAndCooldownWithoutCreatingAResident(GameTestHelper helper) {
        var fixture = remote(helper, 6);
        var events = new java.util.concurrent.atomic.AtomicInteger();
        java.util.function.Consumer<net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent> cancel = event -> {
            if (event.getEntity() instanceof Villager && event.getSpawnType() == net.minecraft.world.entity.MobSpawnType.BREEDING) {
                events.incrementAndGet();
                event.setSpawnCancelled(true);
            }
        };
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(cancel);
        try {
            daytime(helper, () -> finish(helper, fixture));
            helper.assertValueEqual(events.get(), 1, "native offspring creation invokes the actual spawn initialization hook");
            helper.assertTrue(fixture.town.arrivals().isEmpty(), "a cancelled native newborn cannot enter the pending ledger");
            helper.assertTrue(fixture.town.homes().isEmpty(), "a cancelled spawn does not reserve a home");
            helper.assertValueEqual(fixture.town.residents().size(), 2, "no phantom child enters resident care");
            var care = fixture.town.residents().get(fixture.first.getUUID());
            helper.assertValueEqual(care.age(), 6_000, "the attempted native spawn retains its parent cooldown");
            helper.assertValueEqual(SettlementPopulationService.familyFood(care.context(helper.getLevel())), 0,
                    "the real food spent on the native attempt is not refunded");
        } finally {
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(cancel);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_family_birth")
    public static void newbornReturnsOnceWithItsHomeAndBirthHistory(GameTestHelper helper) {
        var fixture = loaded(helper);
        daytime(helper, () -> finish(helper, fixture));
        helper.assertValueEqual(fixture.town.arrivals().size(), 1, "the completed birth has one pending child");
        var arrival = fixture.town.arrivals().values().iterator().next();
        var replay = SettlementArrival.load(arrival.save());
        var id = arrival.id();
        helper.assertTrue(SettlementArrivalService.materialize(helper.getLevel(), fixture.town, id), "the newborn enters the native world");
        var child = (Villager) helper.getLevel().getEntity(id);
        try {
            helper.assertTrue(child != null && child.isBaby(), "one actual baby exists at the birth location");
            var bed = fixture.town.homes().get(id).bed();
            helper.assertValueEqual(child.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.HOME).orElseThrow().pos(),
                    bed, "the native newborn receives its individually reserved bed");
            helper.assertValueEqual(helper.getLevel().getPoiManager().getFreeTickets(bed), 0, "one native home ticket is claimed");
            helper.assertValueEqual(VillageAllegianceApi.get(child).orElseThrow().assignmentSource(), AllegianceAssignmentSource.BIRTH,
                    "materialization preserves inherited allegiance");
            helper.assertTrue(com.jvn.villagerretaliation.village.VillageEventMemory.hasAny(
                    com.jvn.villagerretaliation.village.VillageEventMemory.recentForVillage(helper.getLevel(), fixture.town.id()),
                    com.jvn.villagerretaliation.village.VillageEventMemory.EventTag.BABY_BORN), "the native family history records the birth");
            child.discard();
            fixture.town.recordArrival(replay);
            helper.assertTrue(SettlementArrivalService.materialize(helper.getLevel(), fixture.town, id), "the saved world receipt acknowledges an older ledger");
            helper.assertTrue(helper.getLevel().getEntity(id) == null, "replaying an arrival cannot resurrect a removed child");
        } finally {
            if (child != null && !child.isRemoved()) child.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_family_birth")
    public static void cancelledNativeJoinRetainsTheChildWithoutLeakingBedTickets(GameTestHelper helper) {
        var fixture = loaded(helper);
        daytime(helper, () -> finish(helper, fixture));
        var id = fixture.town.arrivals().keySet().iterator().next();
        var bed = fixture.town.homes().get(id).bed();
        java.util.function.Consumer<net.neoforged.neoforge.event.entity.EntityJoinLevelEvent> cancel = event -> {
            if (event.getEntity().getUUID().equals(id)) event.setCanceled(true);
        };
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(cancel);
        try {
            for (int attempt = 0; attempt < 2; attempt++) {
                helper.assertFalse(SettlementArrivalService.materialize(helper.getLevel(), fixture.town, id), "a rejected native join remains pending");
                helper.assertValueEqual(helper.getLevel().getPoiManager().getFreeTickets(bed), 1, "a rejected body never takes the native bed ticket");
            }
        } finally {
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(cancel);
        }
        helper.assertTrue(fixture.town.arrivals().containsKey(id), "the committed child is not lost when world insertion is blocked");
        helper.assertTrue(SettlementArrivalService.materialize(helper.getLevel(), fixture.town, id), "the child can return when the native join is allowed");
        helper.assertValueEqual(helper.getLevel().getPoiManager().getFreeTickets(bed), 0, "successful insertion takes one home ticket");
        helper.getLevel().getEntity(id).discard();
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_family_birth")
    public static void loadingEitherParentCancelsBackgroundWorkBeforeNativeAiResumes(GameTestHelper helper) {
        var fixture = loaded(helper);
        long now = helper.getLevel().getGameTime();
        daytime(helper, () -> {
            step(helper, fixture, now);
            helper.assertFalse(fixture.town.courtships().isEmpty(), "the unloaded pair initially has a visit");
            helper.assertTrue(helper.getLevel().addFreshEntity(fixture.first), "one parent returns to the native world");
            try {
                helper.assertTrue(fixture.town.courtships().isEmpty(), "the native join immediately cancels pending background courtship");
                for (int interval = 1; interval <= 5; interval++) step(helper, fixture, now + interval * 200L);
                helper.assertTrue(fixture.town.arrivals().isEmpty(), "the background cannot breed a currently ticking parent");
                helper.assertValueEqual(SettlementPopulationService.familyFood(fixture.first), 12, "the interrupted background visit spends no native food");
            } finally {
                fixture.first.discard();
            }
        });
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_family_birth")
    public static void unloadedSleepingParentsWakeAtAnObservedExitDuringTheDay(GameTestHelper helper) {
        var fixture = remote(helper, 6);
        var bed = fixture.town.surveys().values().stream().flatMap(survey -> survey.beds().stream()).findFirst().orElseThrow();
        var tag = fixture.first.saveWithoutId(new CompoundTag());
        tag.putInt("SleepingX", bed.getX());
        tag.putInt("SleepingY", bed.getY());
        tag.putInt("SleepingZ", bed.getZ());
        fixture.first.load(tag);
        fixture.first.setPos(bed.getBottomCenter());
        fixture.town.residents().get(fixture.first.getUUID()).observe(fixture.first, fixture.town, helper.getLevel().getGameTime());
        daytime(helper, () -> finish(helper, fixture));
        helper.assertValueEqual(fixture.town.arrivals().size(), 1, "a saved sleeping pose does not prevent every later daytime birth");
        helper.assertFalse(helper.getLevel().hasChunkAt(bed), "waking the detached family context does not load its bed");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_family_birth")
    public static void woodenDoorsPermitFamilyVisitsButIronDoorsBlockThem(GameTestHelper helper) {
        var fixture = remote(helper, 24);
        var center = fixture.town.center();
        for (var position : BlockPos.betweenClosed(center.offset(12, 0, -1), center.offset(12, 3, 5))) {
            set(fixture, position, Blocks.STONE.defaultBlockState());
        }
        var doorway = center.offset(12, 0, 1);
        var wood = Blocks.OAK_DOOR.defaultBlockState();
        set(fixture, doorway, wood.setValue(net.minecraft.world.level.block.DoorBlock.HALF,
                net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER));
        set(fixture, doorway.above(), wood.setValue(net.minecraft.world.level.block.DoorBlock.HALF,
                net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));
        observe(helper, fixture);
        daytime(helper, () -> {
            long now = helper.getLevel().getGameTime();
            step(helper, fixture, now);
            helper.assertFalse(fixture.town.courtships().isEmpty(), "villagers can traverse an observed wooden door");
            var iron = Blocks.IRON_DOOR.defaultBlockState();
            set(fixture, doorway, iron.setValue(net.minecraft.world.level.block.DoorBlock.HALF,
                    net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER));
            set(fixture, doorway.above(), iron.setValue(net.minecraft.world.level.block.DoorBlock.HALF,
                    net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));
            observe(helper, fixture);
            step(helper, fixture, now + 200L);
            helper.assertTrue(fixture.town.courtships().isEmpty(), "a closed iron door blocks the same family route");
        });
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_family_birth")
    public static void claimedNativeHomesCannotSupportBirths(GameTestHelper helper) {
        var fixture = loaded(helper);
        for (var survey : fixture.town.surveys().values()) {
            for (var bed : survey.beds()) helper.getLevel().getPoiManager().take(type -> type.is(net.minecraft.world.entity.ai.village.poi.PoiTypes.HOME),
                    (type, pos) -> pos.equals(bed), bed, 0);
        }
        daytime(helper, () -> finish(helper, fixture));
        helper.assertTrue(fixture.town.courtships().isEmpty(), "actual occupied native beds override an older availability survey");
        helper.assertTrue(fixture.town.arrivals().isEmpty(), "existing native home occupants retain their tickets");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_family_birth")
    public static void newbornOriginAndAgeSurviveSaveWhileItsChunkIsMissing(GameTestHelper helper) {
        var fixture = remote(helper, 24);
        daytime(helper, () -> finish(helper, fixture));
        var id = fixture.town.arrivals().keySet().iterator().next();
        var care = fixture.town.residents().get(id);
        care.advance(helper.getLevel(), fixture.town, care.checkedAt() + 200L);
        var restored = SettlementRecord.load(fixture.town.save(helper.getLevel().registryAccess()), helper.getLevel().registryAccess(), () -> {});
        helper.assertValueEqual(restored.arrivals().get(id).origin(restored), fixture.town.arrivals().get(id).origin(fixture.town),
                "newborn materialization retains its birth location across save formats");
        helper.assertValueEqual(restored.residents().get(id).context(helper.getLevel()).getAge(), -23_800,
                "pending children grow through the same resident care checkpoint");
        helper.assertFalse(SettlementArrivalService.materialize(helper.getLevel(), restored, id), "the birth remains pending with no loaded origin chunk");
        helper.assertFalse(helper.getLevel().hasChunkAt(fixture.town.center()), "saved-child reconciliation never forces chunks");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_family_birth")
    public static void civicWorkHoursAndPersonalContractsKeepTheirExistingFamilyRules(GameTestHelper helper) {
        var fixture = remote(helper, 6);
        var level = helper.getLevel();
        var area = new SettlementArea(UUID.randomUUID(), SettlementAreaType.FARM, "Farm",
                fixture.town.center().offset(-4, 0, -4), fixture.town.center().offset(-2, 2, -2), 5, true);
        fixture.town.putArea(fixture.town.leader(), area);
        fixture.town.assignDuty(fixture.first.getUUID(), area.id());
        com.jvn.villagerretaliation.interaction.HiredWorkStateStore.state(fixture.first).putString("WorkerTaskState", "working");
        fixture.town.recordWorker(SettlementWorkerState.capture(level, fixture.first,
                new SettlementWorkforceService.Duty(fixture.town, area), level.getGameTime()));
        fixture.town.residents().get(fixture.first.getUUID()).remember(fixture.first);
        long previous = level.getDayTime();
        try {
            level.setDayTime(6_000L);
            step(helper, fixture, level.getGameTime());
            helper.assertTrue(fixture.town.courtships().isEmpty(), "a working civic parent does not also make a family visit");
            level.setDayTime(10_000L);
            step(helper, fixture, level.getGameTime() + 200L);
            helper.assertFalse(fixture.town.courtships().isEmpty(), "off-duty civic employment permits ordinary courtship");
            fixture.town.removeDuty(fixture.first.getUUID());
            com.jvn.villagerretaliation.interaction.VillagerAssignmentStore.hire(fixture.first, UUID.randomUUID(),
                    com.jvn.villagerretaliation.interaction.HiredVillagerRole.CRAFTSMAN, 10L, fixture.town.center());
            fixture.town.residents().get(fixture.first.getUUID()).observe(fixture.first, fixture.town, level.getGameTime());
            step(helper, fixture, level.getGameTime() + 400L);
            helper.assertTrue(fixture.town.courtships().isEmpty(), "a personal commitment cancels the earlier civic visit");
        } finally {
            level.setDayTime(previous);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_family_birth")
    public static void transientCombatDangerRemainsRecordedWhenTheParentUnloads(GameTestHelper helper) {
        var fixture = remote(helper, 6);
        fixture.first.setTarget(EntityType.ZOMBIE.create(helper.getLevel()));
        var care = fixture.town.residents().get(fixture.first.getUUID());
        care.observe(fixture.first, fixture.town, helper.getLevel().getGameTime());
        var restored = SettlementResidentState.load(care.save());
        fixture.town.recordResident(restored);
        helper.assertFalse(restored.safeForFamily(), "unloaded safety does not depend on an unserializable entity target");
        daytime(helper, () -> finish(helper, fixture));
        helper.assertTrue(fixture.town.arrivals().isEmpty(), "a parent unloaded during combat cannot breed in the background");
        helper.assertValueEqual(SettlementPopulationService.familyFood(restored.context(helper.getLevel())), 12,
                "unsafe family attempts leave real provisions intact");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_family_birth")
    public static void aConflictingLiveIdentityCannotActAsTheSavedParent(GameTestHelper helper) {
        var fixture = remote(helper, 6);
        daytime(helper, () -> {
            long now = helper.getLevel().getGameTime();
            step(helper, fixture, now);
            var conflicting = EntityType.PIG.create(helper.getLevel());
            conflicting.setUUID(fixture.first.getUUID());
            conflicting.setPos(helper.absolutePos(new BlockPos(2, 2, 2)).getBottomCenter());
            helper.assertTrue(helper.getLevel().addFreshEntity(conflicting), "a conflicting native identity is present");
            try {
                step(helper, fixture, now + 200L);
                helper.assertTrue(fixture.town.courtships().isEmpty(), "a live non-villager identity cancels the stale parent visit");
                helper.assertTrue(fixture.town.arrivals().isEmpty(), "the old villager context cannot create a child");
            } finally {
                conflicting.discard();
            }
        });
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_family_birth")
    public static void savedNavigationAndCourtshipRejectMalformedState(GameTestHelper helper) {
        var fixture = remote(helper, 6);
        daytime(helper, () -> step(helper, fixture, helper.getLevel().getGameTime()));
        var original = fixture.town.save(helper.getLevel().registryAccess());
        var restored = SettlementRecord.load(original, helper.getLevel().registryAccess(), () -> {});
        var routes = new SettlementFamilyRoutes(helper.getLevel(), restored);
        helper.assertFalse(routes.route(fixture.first.blockPosition(), fixture.second.blockPosition()).isEmpty(),
                "paletted observations preserve the actual walking route through save and reload");
        var duplicate = original.copy();
        var attempts = duplicate.getList("Courtships", net.minecraft.nbt.Tag.TAG_COMPOUND);
        attempts.add(attempts.getCompound(0).copy());
        helper.assertTrue(rejected(helper, duplicate), "saved parents cannot participate in duplicate visits");
        var invalid = original.copy();
        var navigation = invalid.getList("Navigation", net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0);
        navigation.putByteArray("Blocks", new byte[4]);
        helper.assertTrue(rejected(helper, invalid), "a truncated navigation section cannot become invented air");
        var legacy = original.copy();
        legacy.remove("Navigation");
        legacy.remove("Courtships");
        var old = SettlementRecord.load(legacy, helper.getLevel().registryAccess(), () -> {});
        helper.assertTrue(old.navigation().isEmpty() && old.courtships().isEmpty(), "older saves load without fabricated observations or visits");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_family_birth")
    public static void nativeBedClaimsAreRecheckedEvenWhileTheTerrainIsUnloaded(GameTestHelper helper) {
        var fixture = remote(helper, 6);
        var level = helper.getLevel();
        daytime(helper, () -> {
            long now = level.getGameTime();
            step(helper, fixture, now);
            helper.assertFalse(fixture.town.courtships().isEmpty(), "the initially available bed supports a visit");
            for (var survey : fixture.town.surveys().values()) {
                for (var bed : survey.beds()) {
                    level.getPoiManager().add(bed, level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.POINT_OF_INTEREST_TYPE)
                            .getHolderOrThrow(net.minecraft.world.entity.ai.village.poi.PoiTypes.HOME));
                    level.getPoiManager().take(type -> type.is(net.minecraft.world.entity.ai.village.poi.PoiTypes.HOME),
                            (type, pos) -> pos.equals(bed), bed, 0);
                }
            }
            step(helper, fixture, now + 200L);
            helper.assertTrue(fixture.town.courtships().isEmpty(), "new native claims override the previous unloaded availability survey");
            helper.assertTrue(fixture.town.arrivals().isEmpty(), "a native occupant cannot lose its home to a background birth");
            helper.assertFalse(level.hasChunkAt(fixture.town.center()), "reading saved native home tickets does not load terrain");
        });
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_family_birth")
    public static void aSpawnHookCannotReplaceAnExistingResidentWithANewborn(GameTestHelper helper) {
        var fixture = remote(helper, 6);
        java.util.function.Consumer<net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent> duplicate = event -> {
            if (event.getEntity() instanceof Villager && event.getSpawnType() == net.minecraft.world.entity.MobSpawnType.BREEDING) {
                event.getEntity().setUUID(fixture.first.getUUID());
            }
        };
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(duplicate);
        try {
            daytime(helper, () -> finish(helper, fixture));
            helper.assertTrue(fixture.town.arrivals().isEmpty(), "a conflicting newborn identity is not queued for world insertion");
            helper.assertValueEqual(fixture.town.residents().size(), 2, "both existing resident identities remain intact");
            helper.assertValueEqual(fixture.town.residents().get(fixture.first.getUUID()).age(), 6_000,
                    "the existing adult keeps the completed native attempt rather than being overwritten by a baby");
        } finally {
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(duplicate);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_family_birth")
    public static void backgroundFamiliesRequireAnEstablishedCoupleAndHonorLivingChildLimits(GameTestHelper helper) {
        boolean families = VillagerRetaliationConfig.ENABLE_NATURAL_FAMILIES.get();
        boolean socialGraph = VillagerRetaliationConfig.ENABLE_VILLAGER_SOCIAL_GRAPH.get();
        try {
            VillagerRetaliationConfig.ENABLE_NATURAL_FAMILIES.set(true);
            VillagerRetaliationConfig.ENABLE_VILLAGER_SOCIAL_GRAPH.set(true);
            var fixture = remote(helper, 6);
            var level = helper.getLevel();
            var graph = com.jvn.villagerretaliation.social.VillagerSocialGraphSavedData.get(level);
            graph.setRomanticRelationshipStage(level, fixture.first, fixture.second,
                    com.jvn.villagerretaliation.social.VillagerRelationshipStage.SEPARATED);
            daytime(helper, () -> finish(helper, fixture));
            helper.assertTrue(fixture.town.arrivals().isEmpty(), "background simulation never invents social visits or couples");
            helper.assertValueEqual(graph.relationshipStage(fixture.first.getUUID(), fixture.second.getUUID()).orElseThrow(),
                    com.jvn.villagerretaliation.social.VillagerRelationshipStage.SEPARATED, "background time cannot advance a bond");
            graph.setRomanticRelationshipStage(level, fixture.first, fixture.second,
                    com.jvn.villagerretaliation.social.VillagerRelationshipStage.DATING);
            int cap = graph.childLimit(fixture.first.getUUID(), fixture.second.getUUID());
            for (int index = 0; index < cap; index++) {
                var adultChild = EntityType.VILLAGER.create(level);
                graph.linkParentsAndChild(level, fixture.first, fixture.second, adultChild);
            }
            daytime(helper, () -> finish(helper, fixture));
            helper.assertTrue(fixture.town.arrivals().isEmpty(), "background births count all living biological children");
            helper.assertTrue(fixture.town.courtships().isEmpty(), "a capped couple cannot reserve a background bed");
        } finally {
            VillagerRetaliationConfig.ENABLE_NATURAL_FAMILIES.set(families);
            VillagerRetaliationConfig.ENABLE_VILLAGER_SOCIAL_GRAPH.set(socialGraph);
        }
        helper.succeed();
    }

    private static boolean rejected(GameTestHelper helper, CompoundTag tag) {
        try {
            SettlementRecord.load(tag, helper.getLevel().registryAccess(), () -> {});
            return false;
        } catch (IllegalArgumentException exception) {
            return true;
        }
    }

    private static void finish(GameTestHelper helper, Fixture fixture) {
        long now = helper.getLevel().getGameTime();
        for (int interval = 0; interval <= 8; interval++) step(helper, fixture, now + interval * 200L);
    }

    private static Fixture loaded(GameTestHelper helper) {
        var setup = SettlementPopulationGameTests.fixture(helper);
        var level = helper.getLevel();
        var head = setup.town().center().offset(5, 0, 2);
        var bed = Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH);
        level.setBlock(head, bed.setValue(BedBlock.PART, BedPart.HEAD), 2);
        level.setBlock(head.south(), bed.setValue(BedBlock.PART, BedPart.FOOT), 2);
        level.getPoiManager().add(head, level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.POINT_OF_INTEREST_TYPE)
                .getHolderOrThrow(net.minecraft.world.entity.ai.village.poi.PoiTypes.HOME));
        SettlementBuildingService.refresh(level, setup.town(), setup.home());
        return new Fixture(setup.town(), setup.depot(), parent(helper, setup.town(), setup.town().center(), VillagerGender.MALE),
                parent(helper, setup.town(), setup.town().center().east(2), VillagerGender.FEMALE), new LinkedHashMap<>());
    }

    private static Fixture remote(GameTestHelper helper, int separation) {
        var level = helper.getLevel();
        var origin = helper.absolutePos(new BlockPos(1, 2, 1));
        var center = new BlockPos(7_900_000 + origin.getX() * 4, origin.getY(), 7_900_000 + origin.getZ() * 4);
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var id = registry.create(level.getGameTime(), level.dimension().location(), center, "");
        var town = SettlementSavedData.get(level).establish(registry, id, UUID.randomUUID(), center, level.getGameTime()).orElseThrow();
        var home = new SettlementArea(UUID.randomUUID(), SettlementAreaType.HOME, "Home", center.offset(-1, 0, -1),
                center.offset(separation + 5, 3, 5), 5, true);
        var depotArea = new SettlementArea(UUID.randomUUID(), SettlementAreaType.DEPOT, "Depot", center.above(5), center.offset(3, 6, 3), 5, true);
        SettlementTestPlots.claim(level, town, home.min(), home.max());
        SettlementSavedData.get(level).putArea(registry, id, town.leader(), home);
        SettlementSavedData.get(level).putArea(registry, id, town.leader(), depotArea);
        var depot = town.registerDepot(town.leader(), depotArea.id(), depotArea.min(), SettlementDepotPurpose.GENERAL);
        depot.setItem(0, new ItemStack(Items.BREAD, 64));
        var first = parent(helper, town, center, VillagerGender.MALE);
        var second = parent(helper, town, center.offset(separation, 0, 0), VillagerGender.FEMALE);
        var fixture = new Fixture(town, depot, first, second, new LinkedHashMap<>());
        for (var pos : BlockPos.betweenClosed(home.min().below(), home.max().atY(center.getY() - 1))) set(fixture, pos, Blocks.STONE.defaultBlockState());
        var beds = List.of(center.offset(separation + 1, 0, 2), center.offset(separation + 3, 0, 2), center.offset(separation + 5, 0, 2));
        for (var head : beds) {
            var state = Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH);
            set(fixture, head, state.setValue(BedBlock.PART, BedPart.HEAD));
            set(fixture, head.south(), state.setValue(BedBlock.PART, BedPart.FOOT));
        }
        town.recordSurvey(home.id(), new SettlementBuildingSurvey.Survey(level.getGameTime(), beds, List.of(), false,
                java.util.Map.of(), List.of(), beds.stream().collect(java.util.stream.Collectors.toMap(pos -> pos, pos -> true))));
        observe(helper, fixture);
        return fixture;
    }

    private static Villager parent(GameTestHelper helper, SettlementRecord town, BlockPos position, VillagerGender gender) {
        var parent = EntityType.VILLAGER.create(helper.getLevel());
        parent.setPos(position.getBottomCenter());
        parent.setNoAi(true);
        VillagerPresetNameRegistry.setStoredGender(parent, gender);
        parent.getInventory().setItem(0, new ItemStack(Items.BREAD, 3));
        VillageAllegianceApi.assignKnown(helper.getLevel(), parent, town.id(), AllegianceAssignmentSource.EXPLICIT_API);
        SettlementCareService.onUnloaded(parent);
        return parent;
    }

    private static void set(Fixture fixture, BlockPos pos, BlockState state) {
        fixture.chunks.computeIfAbsent(ChunkPos.asLong(pos), key -> new LevelChunk((net.minecraft.server.level.ServerLevel) fixture.first.level(),
                new ChunkPos(pos))).setBlockState(pos, state, false);
    }

    private static void observe(GameTestHelper helper, Fixture fixture) {
        fixture.chunks.values().forEach(chunk -> SettlementNavigationService.observe(helper.getLevel(), fixture.town, chunk));
    }

    private static void step(GameTestHelper helper, Fixture fixture, long now) {
        SettlementFamilyService.update(helper.getLevel(), fixture.town, now);
    }

    private static void daytime(GameTestHelper helper, Runnable action) {
        long previous = helper.getLevel().getDayTime();
        try {
            helper.getLevel().setDayTime(10_000L);
            action.run();
        } finally {
            helper.getLevel().setDayTime(previous);
        }
    }

    private record Fixture(SettlementRecord town, SettlementDepot depot, Villager first, Villager second, java.util.Map<Long, LevelChunk> chunks) {
        private Fixture {
            com.jvn.villagerretaliation.social.VillagerSocialGraphSavedData.get((net.minecraft.server.level.ServerLevel) first.level())
                    .linkSpouses((net.minecraft.server.level.ServerLevel) first.level(), first, second);
        }
    }
}
