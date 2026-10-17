package com.jvn.villagerretaliation.interaction;

import com.jvn.villagerretaliation.allegiance.AllegianceAssignmentSource;
import com.jvn.villagerretaliation.allegiance.AllegianceConfidence;
import com.jvn.villagerretaliation.allegiance.AllegianceState;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceData;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceEntityData;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("worker_performance")
@PrefixGameTestTemplate(false)
public final class ResidentDataCacheGameTests {
    @GameTest(template = "empty")
    public static void civicAssignmentCacheObservesBothReferencesAndState(GameTestHelper helper) {
        var villager = EntityType.VILLAGER.create(helper.getLevel());
        VillagerAssignmentStore.assignCivic(villager, UUID.randomUUID(), UUID.randomUUID(), HiredVillagerRole.MINING, 0);
        var first = VillagerAssignmentStore.civicAssignment(villager).orElseThrow();
        helper.assertTrue(first == VillagerAssignmentStore.civicAssignment(villager).orElseThrow(), "unchanged civic references reuse their decoded UUIDs");
        var tag = villager.getPersistentData().getCompound("VillagerRetaliationAssignment");
        tag.getIntArray("Settlement")[0] ^= 1;
        helper.assertFalse(first.settlement().equals(VillagerAssignmentStore.civicAssignment(villager).orElseThrow().settlement()), "in-place settlement edits take effect");
        tag.getIntArray("SettlementArea")[0] ^= 1;
        helper.assertFalse(first.area().equals(VillagerAssignmentStore.civicAssignment(villager).orElseThrow().area()), "in-place worksite edits take effect");
        tag.remove("SettlementArea");
        helper.assertTrue(VillagerAssignmentStore.civicAssignment(villager).isEmpty(), "incomplete civic assignments are rejected");
        tag.putUUID("SettlementArea", first.area());
        tag.putString("State", "HIRED");
        helper.assertTrue(VillagerAssignmentStore.civicAssignment(villager).isEmpty(), "personal assignments cannot reuse civic duties");
        tag.putString("State", "CIVIC");
        helper.assertTrue(VillagerAssignmentStore.civicAssignment(villager).isPresent(), "restored civic assignments are immediately visible");
        villager.getPersistentData().remove("VillagerRetaliationAssignment");
        helper.assertTrue(VillagerAssignmentStore.civicAssignment(villager).isEmpty(), "removed assignments never return cached duties");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void staggeredWorkerGroupsCannotStarveEachOther(GameTestHelper helper) {
        var first = new UUID(0, 1);
        var second = new UUID(0, 2);
        var third = new UUID(0, 3);
        var separate = new UUID(0, 4);
        var selected = new java.util.HashMap<UUID, Long>();
        var granted = new java.util.HashSet<UUID>();
        for (int tick = 0; tick < 6; tick++) {
            var due = new java.util.ArrayList<>(tick % 2 == 0 ? List.of(third, second, first) : List.of(separate));
            var winners = HiredWorkDecisionScheduler.select(due, selected, 1, tick);
            helper.assertValueEqual(winners.size(), 1, "selection respects the per-tick budget");
            granted.addAll(winners);
        }
        helper.assertValueEqual(granted, java.util.Set.of(first, second, third, separate), "all workers receive a turn despite alternating group sizes");
        var next = HiredWorkDecisionScheduler.select(new java.util.ArrayList<>(List.of(third, first, second)), selected, 1, 6);
        helper.assertValueEqual(next, java.util.Set.of(first), "the longest waiting worker receives the next slot regardless of entity order");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200, batch = "worker_ticking_eligibility")
    public static void loadedWorkersWaitForEntityTickingBeforeRequestingDecisions(GameTestHelper helper) {
        var level = helper.getLevel();
        var activePosition = helper.absolutePos(new BlockPos(1, 2, 1));
        var inactivePosition = activePosition.offset(8192, 0, 8192);
        var chunk = new net.minecraft.world.level.ChunkPos(inactivePosition);
        level.getChunkSource().addRegionTicket(net.minecraft.server.level.TicketType.FORCED, chunk, 0, chunk);
        level.getChunk(chunk.x, chunk.z);
        helper.startSequence().thenWaitUntil(() -> {
            helper.assertTrue(level.areEntitiesLoaded(chunk.toLong()), "the worker's chunk is loaded");
            helper.assertFalse(level.isPositionEntityTicking(inactivePosition), "the loaded chunk does not tick entities");
            helper.assertTrue(level.isPositionEntityTicking(activePosition), "the active worker's chunk ticks entities");
        }).thenExecute(() -> {
            var villager = EntityType.VILLAGER.create(level);
            try {
                villager.setUUID(new UUID(17, -level.getGameTime()));
                villager.setPos(inactivePosition.getCenter());
                villager.setNoAi(true);
                HireContractStore.save(villager, HireContract.regular(UUID.randomUUID(), level.getGameTime(), 1, 0, 0, HiredVillagerRole.MINING));
                HiredWorkStateStore.initializeDefaults(HiredWorkStateStore.state(villager), villager);
                level.addFreshEntity(villager);
                helper.assertTrue(level.getEntity(villager.getUUID()) == villager, "the inactive worker remains loaded");
                helper.assertFalse(HiredWorkDecisionScheduler.isDecisionOpportunity(level, villager), "background workers cannot enter the decision budget");
                helper.assertFalse(HiredWorkDecisionScheduler.tryAcquire(level, villager), "background workers cannot claim a decision slot");
                villager.setPos(activePosition.getCenter());
                helper.assertTrue(HiredWorkDecisionScheduler.isDecisionOpportunity(level, villager), "returning workers can request decisions without rejoining the level");
            } finally {
                HiredWorkSession.invalidate(villager);
                villager.discard();
                level.getChunkSource().removeRegionTicket(net.minecraft.server.level.TicketType.FORCED, chunk, 0, chunk);
            }
        }).thenSucceed();
    }

    @GameTest(template = "empty")
    public static void workerNameLookupsRetainLegacyAliases(GameTestHelper helper) {
        for (var role : HiredVillagerRole.values()) {
            helper.assertValueEqual(HiredVillagerRole.bySerializedName(" " + role.name() + " "), role, "role names remain case and whitespace tolerant");
        }
        for (var state : com.jvn.villagerretaliation.interaction.work.HiredWorkerTaskState.values()) {
            helper.assertValueEqual(com.jvn.villagerretaliation.interaction.work.HiredWorkerTaskState.byId(" " + state.name() + " "), state, "task names remain case and whitespace tolerant");
        }
        helper.assertTrue(HiredVillagerRole.bySerializedName("not_a_role") == null, "invalid roles remain rejected");
        helper.assertValueEqual(com.jvn.villagerretaliation.interaction.work.HiredWorkerTaskState.byId(" TARGET_UNREACHABLE "),
                com.jvn.villagerretaliation.interaction.work.HiredWorkerTaskState.FAILED_COOLDOWN, "legacy task aliases survive");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void allegianceCacheObservesNestedEditsAndRemoval(GameTestHelper helper) {
        var villager = EntityType.VILLAGER.create(helper.getLevel());
        var primary = new VillageAllegianceId(UUID.randomUUID());
        var parent = new VillageAllegianceId(UUID.randomUUID());
        VillageAllegianceEntityData.write(villager, VillageAllegianceData.known(primary,
                AllegianceAssignmentSource.EXPLICIT_API, AllegianceConfidence.AUTHORITATIVE, 0,
                helper.getLevel().dimension().location(), BlockPos.ZERO, List.of(parent)));
        var initial = VillageAllegianceEntityData.read(villager).orElseThrow();
        helper.assertTrue(initial == VillageAllegianceEntityData.read(villager).orElseThrow(), "unchanged reads reuse the decoded payload");
        var tag = villager.getPersistentData().getCompound(VillageAllegianceEntityData.ROOT_TAG);
        var replacement = UUID.randomUUID();
        tag.getList("ProtectedParents", Tag.TAG_COMPOUND).getCompound(0).putUUID("Id", replacement);
        helper.assertValueEqual(VillageAllegianceEntityData.read(villager).orElseThrow().protectedParents(),
                List.of(new VillageAllegianceId(replacement)), "nested mutations invalidate the cached payload");
        tag.getIntArray("Primary")[0] ^= 1;
        helper.assertFalse(VillageAllegianceEntityData.read(villager).orElseThrow().primary().equals(primary), "in-place UUID array edits take effect");
        VillageAllegianceEntityData.clear(villager);
        helper.assertTrue(VillageAllegianceEntityData.read(villager).isEmpty(), "removal never returns the cached allegiance");
        villager.getPersistentData().put(VillageAllegianceEntityData.ROOT_TAG, new CompoundTag());
        helper.assertValueEqual(VillageAllegianceEntityData.read(villager).orElseThrow().state(), AllegianceState.UNKNOWN, "malformed replacement stays conservative");
        villager.setPos(20, 10, 20);
        helper.assertValueEqual(VillageAllegianceEntityData.read(villager).orElseThrow().originPosition(), villager.blockPosition(), "malformed fallback follows movement within the same tick");
        helper.runAfterDelay(1, () -> {
            helper.assertValueEqual(VillageAllegianceEntityData.read(villager).orElseThrow().assignedGameTime(), helper.getLevel().getGameTime(), "malformed fallback follows game time");
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void validAllegianceSurvivesTickAndPositionChanges(GameTestHelper helper) {
        var villager = EntityType.VILLAGER.create(helper.getLevel());
        var id = VillageAllegianceId.random();
        VillageAllegianceEntityData.write(villager, VillageAllegianceData.known(id,
                AllegianceAssignmentSource.EXPLICIT_API, AllegianceConfidence.AUTHORITATIVE, 0,
                helper.getLevel().dimension().location(), BlockPos.ZERO, List.of()));
        var initial = VillageAllegianceEntityData.read(villager).orElseThrow();
        villager.setPos(20, 10, 20);
        helper.runAfterDelay(1, () -> {
            helper.assertTrue(initial == VillageAllegianceEntityData.read(villager).orElseThrow(), "valid saved data remains reusable across ticks and movement");
            var tag = villager.getPersistentData().getCompound(VillageAllegianceEntityData.ROOT_TAG);
            tag.putInt("OriginX", 25);
            helper.assertValueEqual(VillageAllegianceEntityData.read(villager).orElseThrow().originPosition().getX(), 25, "saved origin edits still invalidate the cache");
            tag.remove("DataVersion");
            helper.assertValueEqual(VillageAllegianceEntityData.read(villager).orElseThrow().state(), AllegianceState.UNKNOWN, "removing a required field restores the conservative fallback");
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void primaryLookupRetainsValidationAndImmediateEdits(GameTestHelper helper) {
        var level = helper.getLevel();
        var villager = EntityType.VILLAGER.create(level);
        var first = VillageAllegianceId.random();
        var second = VillageAllegianceId.random();
        var registry = com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData.get(level);
        registry.ensureRecord(second, 0, level.dimension().location(), BlockPos.ZERO);
        com.jvn.villagerretaliation.allegiance.VillageAllegianceApi.assignKnown(level, villager, first, AllegianceAssignmentSource.EXPLICIT_API);
        var tag = villager.getPersistentData().getCompound(VillageAllegianceEntityData.ROOT_TAG);
        helper.assertValueEqual(com.jvn.villagerretaliation.allegiance.VillageAllegianceApi.canonicalPrimary(level, villager).orElseThrow(), first, "initial primary is known");
        tag.putInt("OriginX", 25);
        helper.assertValueEqual(com.jvn.villagerretaliation.allegiance.VillageAllegianceApi.canonicalPrimary(level, villager).orElseThrow(), first, "origin edits do not change primary identity");
        helper.assertValueEqual(VillageAllegianceEntityData.read(villager).orElseThrow().originPosition().getX(), 25, "full reads still observe origin edits");
        tag.remove("Source");
        helper.assertTrue(com.jvn.villagerretaliation.allegiance.VillageAllegianceApi.canonicalPrimary(level, villager).isEmpty(), "malformed metadata cannot retain cached membership");
        tag.putString("Source", AllegianceAssignmentSource.EXPLICIT_API.name());
        tag.putUUID("Primary", second.value());
        helper.assertValueEqual(com.jvn.villagerretaliation.allegiance.VillageAllegianceApi.canonicalPrimary(level, villager).orElseThrow(), second, "changing primary immediately updates membership");
        tag.putString("State", AllegianceState.UNAFFILIATED.name());
        helper.assertTrue(com.jvn.villagerretaliation.allegiance.VillageAllegianceApi.canonicalPrimary(level, villager).isEmpty(), "unaffiliated state overrides an old primary field");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void assignmentCacheRetainsValidationAndImmediateCommands(GameTestHelper helper) {
        var villager = EntityType.VILLAGER.create(helper.getLevel());
        var owner = UUID.randomUUID();
        VillagerAssignmentStore.hire(villager, owner, HiredVillagerRole.MINING, 0, BlockPos.ZERO);
        var initial = VillagerAssignmentStore.snapshot(villager);
        helper.assertTrue(initial == VillagerAssignmentStore.snapshot(villager), "unchanged assignment reads reuse the snapshot");
        var anchor = new BlockPos(5, 3, 7);
        VillagerAssignmentStore.setCommand(villager, owner, VillagerAssignmentCommand.STAY, anchor);
        helper.assertValueEqual(VillagerAssignmentStore.snapshot(villager).workAnchor(), anchor, "commands invalidate the snapshot immediately");
        var tag = villager.getPersistentData().getCompound("VillagerRetaliationAssignment");
        tag.getCompound("WorkAnchor").putInt("X", 99);
        helper.assertValueEqual(VillagerAssignmentStore.snapshot(villager).workAnchor().getX(), 99, "nested edits without revision changes are visible");
        tag.remove("Owner");
        helper.assertValueEqual(VillagerAssignmentStore.snapshot(villager).state(), VillagerAssignmentState.UNASSIGNED, "malformed ownership is still repaired");
        VillagerAssignmentStore.assignCivic(villager, UUID.randomUUID(), UUID.randomUUID(), HiredVillagerRole.MINING, 0);
        tag = villager.getPersistentData().getCompound("VillagerRetaliationAssignment");
        var settlement = VillagerAssignmentStore.civicSettlement(villager).orElseThrow();
        tag.getIntArray("Settlement")[0] ^= 1;
        helper.assertFalse(VillagerAssignmentStore.civicSettlement(villager).orElseThrow().equals(settlement), "civic UUID array edits invalidate references");
        tag.remove("SettlementArea");
        helper.assertTrue(VillagerAssignmentStore.civicArea(villager).isEmpty(), "malformed civic references are immediately rejected");
        tag.putUUID("SettlementArea", UUID.randomUUID());
        tag.putString("Command", "FOLLOW");
        helper.assertValueEqual(VillagerAssignmentStore.snapshot(villager).command(), VillagerAssignmentCommand.WORK, "external civic commands are still normalized");
        villager.getPersistentData().remove("VillagerRetaliationAssignment");
        helper.assertValueEqual(VillagerAssignmentStore.snapshot(villager).state(), VillagerAssignmentState.UNASSIGNED, "removed assignments are not cached");
        helper.succeed();
    }
}
