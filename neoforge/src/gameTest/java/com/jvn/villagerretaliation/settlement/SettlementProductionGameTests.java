package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.interaction.HireContract;
import com.jvn.villagerretaliation.interaction.HireContractStore;
import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.HiredWorkStateStore;
import com.jvn.villagerretaliation.interaction.work.production.HiredProductionOrder;
import com.jvn.villagerretaliation.interaction.work.production.HiredProductionQueue;
import com.jvn.villagerretaliation.interaction.work.production.HiredProductionRecipe;
import com.jvn.villagerretaliation.interaction.work.production.HiredProductionService;
import com.mojang.authlib.GameProfile;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("villagerretaliation")
@PrefixGameTestTemplate(false)
public final class SettlementProductionGameTests {
    private SettlementProductionGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "production_authority")
    public static void civicProductionRequiresAdoptionStaffAndCurrentEdits(GameTestHelper helper) {
        var fixture = fixture(helper);
        var town = fixture.town();
        var area = fixture.area();
        var order = sticks(helper);
        helper.assertFalse(SettlementProductionService.append(fixture.leader(), town, area.id(), 0L, order).isEmpty(),
                "a legacy workshop cannot silently adopt shared production");
        helper.assertTrue(town.productionQueues().isEmpty(), "rejected adoption leaves legacy production intact");
        helper.assertTrue(SettlementWorksiteService.configure(fixture.leader(), town, area.id(),
                SettlementWorksitePlan.defaults(HiredVillagerRole.CRAFTSMAN), true), "the workshop is explicitly adopted");
        var delegate = player(helper, "ProductionStaff");
        town.setPermissions(fixture.leader().getUUID(), delegate.getUUID(), Set.of(SettlementPermission.VIEW));
        helper.assertFalse(SettlementProductionService.append(delegate, town, area.id(), 0L, order).isEmpty(), "viewers cannot create orders");
        town.setPermissions(fixture.leader().getUUID(), delegate.getUUID(), Set.of(SettlementPermission.STAFF));
        helper.assertValueEqual(SettlementProductionService.append(delegate, town, area.id(), 0L, order), "", "staff can create a shared order");
        var queue = town.productionQueues().get(area.id());
        helper.assertValueEqual(queue.scope(), HiredProductionQueue.Scope.WORKSITE, "quantity belongs to the worksite");
        helper.assertValueEqual(queue.owner(), area.id(), "the area owns the queue");
        helper.assertFalse(SettlementProductionService.edit(delegate, town, area.id(), 0L, order.id(), HiredProductionService.Edit.PAUSE, 0).isEmpty(),
                "an edit based on an older queue is rejected");
        var worker = UUID.randomUUID();
        town.assignDuty(worker, area.id());
        long revision = queue.configurationRevision();
        var allocation = queue.reserve(worker, order.id(), 4, 1, 0L, area.min());
        queue.start(allocation.id());
        queue.complete(allocation.id(), new ItemStack(Items.STICK, 4));
        helper.assertValueEqual(queue.configurationRevision(), revision, "worker progress does not invalidate an open management action");
        helper.assertValueEqual(SettlementProductionService.edit(delegate, town, area.id(), revision, order.id(), HiredProductionService.Edit.PAUSE, 0),
                "", "a staff action uses current production progress without overwriting it");
        helper.assertValueEqual(queue.order(order.id()).produced(), 4L, "management retains completed quantity");
        town.setPermissions(fixture.leader().getUUID(), delegate.getUUID(), Set.of());
        helper.assertFalse(SettlementProductionService.edit(delegate, town, area.id(), queue.configurationRevision(), order.id(),
                HiredProductionService.Edit.RESUME, 0).isEmpty(), "revoked staff cannot resume production");
        helper.assertFalse(helper.getLevel().hasChunkAt(town.center()), "remote queue management never loads the worksite");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "production_authority")
    public static void productionHandoffsAndSavedOwnershipPreserveOutstandingGoods(GameTestHelper helper) {
        var fixture = fixture(helper);
        var town = fixture.town();
        var area = fixture.area();
        SettlementWorksiteService.configure(fixture.leader(), town, area.id(), SettlementWorksitePlan.defaults(HiredVillagerRole.CRAFTSMAN), true);
        var order = sticks(helper);
        SettlementProductionService.append(fixture.leader(), town, area.id(), 0L, order);
        var queue = town.productionQueues().get(area.id());
        var worker = UUID.randomUUID();
        town.assignDuty(worker, area.id());
        var allocation = queue.reserve(worker, order.id(), 4, 1, 0L, area.min());
        queue.start(allocation.id());
        helper.assertTrue(town.removeDuty(worker), "the worker leaves the worksite");
        helper.assertTrue(queue.allocations().getFirst().worker() == null, "the consumed batch is detached from the departing worker");
        var provider = helper.getLevel().registryAccess();
        var saved = town.save(provider);
        var restored = SettlementRecord.load(saved, provider, () -> {});
        var resumed = restored.productionQueues().get(area.id());
        helper.assertValueEqual(resumed.save(provider), queue.save(provider), "the native batch and worksite quantity survive reloading");
        var replacement = UUID.randomUUID();
        restored.assignDuty(replacement, area.id());
        helper.assertTrue(resumed.resume(allocation.id(), replacement), "new staff can claim the existing batch");
        helper.assertTrue(resumed.complete(allocation.id(), new ItemStack(Items.STICK, 4)), "new staff settle the same receipt");
        helper.assertValueEqual(resumed.order(order.id()).produced(), 4L, "staff handoff counts production only once");
        var legacy = saved.copy();
        legacy.remove("ProductionQueues");
        helper.assertTrue(SettlementRecord.load(legacy, provider, () -> {}).productionQueues().isEmpty(), "old saves retain their legacy worker production");
        var damaged = saved.copy();
        damaged.getList("ProductionQueues", Tag.TAG_COMPOUND).getCompound(0).putUUID("Owner", UUID.randomUUID());
        boolean rejected = false;
        try {
            SettlementRecord.load(damaged, provider, () -> {});
        } catch (IllegalArgumentException exception) {
            rejected = true;
        }
        helper.assertTrue(rejected, "a queue cannot be restored under an unknown or different worksite");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "production_authority")
    public static void personalProductionBelongsToTheHireWithoutSettlementAuthority(GameTestHelper helper) {
        var level = helper.getLevel();
        var owner = player(helper, "PersonalProducer");
        var stranger = player(helper, "OtherProducer");
        var worker = EntityType.VILLAGER.create(level);
        worker.setPos(helper.absolutePos(new BlockPos(2, 2, 2)).getBottomCenter());
        worker.setNoAi(true);
        worker.setNoGravity(true);
        helper.assertTrue(level.addFreshEntity(worker), "the personal worker is loaded");
        try {
            var contract = HireContract.regular(owner.getUUID(), level.getGameTime(), 1, 1, 1, HiredVillagerRole.CRAFTSMAN);
            HireContractStore.save(worker, contract);
            var order = sticks(helper);
            helper.assertFalse(HiredProductionService.appendPersonal(stranger, worker, 0L, order).isEmpty(), "another player cannot own the hire's orders");
            helper.assertValueEqual(HiredProductionService.appendPersonal(owner, worker, 0L, order), "", "the hirer can queue production without ruling a town");
            var access = HiredProductionService.access(level, worker, HiredVillagerRole.CRAFTSMAN);
            helper.assertTrue(access.managed() && access.blocker().isEmpty(), "the personal queue is available to the worker");
            helper.assertValueEqual(access.queue().owner(), contract.id(), "the queue is bound to the active contract");
            helper.assertValueEqual(access.queue().scope(), HiredProductionQueue.Scope.PERSONAL, "the queue remains personal");
            helper.assertTrue(SettlementWorkforceService.duty(worker).isEmpty(), "production creates no civic authority or assignment");
            helper.assertValueEqual(HiredProductionService.editPersonal(owner, worker, access.queue().configurationRevision(), order.id(),
                    HiredProductionService.Edit.PAUSE, 0), "", "the personal order can be paused");
            helper.assertTrue(HiredWorkStateStore.state(worker).getCompound(HiredProductionService.STATE_TAG)
                    .getList("Orders", Tag.TAG_COMPOUND).getCompound(0).getBoolean("Paused"), "personal edits persist on the entity");
            var pausedSave = HiredWorkStateStore.state(worker).getCompound(HiredProductionService.STATE_TAG).copy();
            var replacement = HireContract.regular(stranger.getUUID(), level.getGameTime(), 1, 1, 1, HiredVillagerRole.CRAFTSMAN);
            HireContractStore.save(worker, replacement);
            helper.assertValueEqual(HiredProductionService.access(level, worker, HiredVillagerRole.CRAFTSMAN).blocker(),
                    "production_contract_changed", "another contract cannot resume the old owner's orders");
            helper.assertValueEqual(HiredWorkStateStore.state(worker).getCompound(HiredProductionService.STATE_TAG), pausedSave,
                    "the old queue remains preserved for explicit handling");
        } finally {
            worker.discard();
        }
        helper.succeed();
    }

    private static HiredProductionOrder sticks(GameTestHelper helper) {
        return HiredProductionOrder.create(helper.getLevel(), HiredProductionRecipe.exact(ResourceLocation.withDefaultNamespace("stick"), Map.of()),
                HiredProductionOrder.Mode.BATCH, 12, null);
    }

    private static ServerPlayer player(GameTestHelper helper, String name) {
        return FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
    }

    private static Fixture fixture(GameTestHelper helper) {
        var level = helper.getLevel();
        var origin = helper.absolutePos(BlockPos.ZERO);
        var center = new BlockPos(22_000_000 + origin.getX() * 8, 64, 22_000_000 + origin.getZ() * 8);
        var leader = player(helper, "ProductionLeader");
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var id = registry.create(level.getGameTime(), level.dimension().location(), center, "");
        var town = SettlementSavedData.get(level).establish(registry, id, leader.getUUID(), center, level.getGameTime()).orElseThrow();
        var area = new SettlementArea(UUID.randomUUID(), SettlementAreaType.WORKSHOP, "Workshop", center, center.offset(4, 3, 4), 5, true);
        helper.assertTrue(town.putArea(leader.getUUID(), area), "the workshop exists");
        return new Fixture(town, area, leader);
    }

    private record Fixture(SettlementRecord town, SettlementArea area, ServerPlayer leader) {
    }
}
