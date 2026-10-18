package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.AllegianceAssignmentSource;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceApi;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.HiredWorkStateStore;
import com.jvn.villagerretaliation.interaction.VillagerAssignmentStore;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.jvn.villagerretaliation.item.VillagerItemFilterData;
import com.jvn.villagerretaliation.item.VillagerRetaliationItems;
import com.jvn.villagerretaliation.network.SettlementManagementActionPayload;
import com.jvn.villagerretaliation.network.SettlementManagementPayload;
import com.jvn.villagerretaliation.network.SettlementManagementRequestPayload;
import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class SettlementRemoteStaffingGameTests {
    private SettlementRemoteStaffingGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_remote_staffing")
    public static void unloadedResidentsOfferValidRolesAndStartRealProduction(GameTestHelper helper) {
        var fixture = fixture(helper, 0);
        var level = helper.getLevel();
        var before = fixture.town().revision();
        var snapshot = SettlementManagementService.snapshot(fixture.leader(), new SettlementManagementRequestPayload(fixture.town().id().value(),
                SettlementManagementRequestPayload.Page.RESIDENTS, 0, "", 1, false));
        var resident = (SettlementManagementPayload.Resident) snapshot.entries().getFirst();
        helper.assertTrue(resident.roles().contains(HiredVillagerRole.CRAFTSMAN), "the unloaded resident exposes real staffing choices");
        helper.assertValueEqual(fixture.town().revision(), before, "reading cached role choices does not change the town");
        helper.assertTrue(assign(fixture, fixture.leader(), fixture.workshop()), "the management command assigns the unloaded resident");
        var worker = fixture.town().workers().get(fixture.resident().getUUID());
        helper.assertTrue(worker != null, "assignment creates a worker ledger without a later entity tick");
        fixture.depot().setItem(0, new ItemStack(Items.OAK_PLANKS, 2));
        craft(helper, fixture);
        helper.assertValueEqual(count(helper, worker, Items.STICK), 4, "the newly assigned worker crafts one real recipe batch");
        helper.assertTrue(fixture.depot().isEmpty(), "production consumes finite depot inputs");
        helper.assertFalse(level.hasChunkAt(fixture.town().center()), "staffing and production do not force the town to load");
        helper.assertTrue(level.getEntity(fixture.resident().getUUID()) == null, "remote staffing never spawns a second resident");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_remote_staffing")
    public static void remoteStaffingRequiresPermissionAndCurrentMembership(GameTestHelper helper) {
        var fixture = fixture(helper, 1);
        var delegate = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "StaffDelegate"));
        fixture.town().setPermissions(fixture.town().leader(), delegate.getUUID(), Set.of(SettlementPermission.VIEW));
        helper.assertFalse(assign(fixture, delegate, fixture.workshop()), "viewing alone cannot assign an unloaded resident");
        helper.assertTrue(fixture.town().duties().isEmpty() && fixture.town().workers().isEmpty(), "denied staffing creates no duty or inventory");
        fixture.town().setPermissions(fixture.town().leader(), delegate.getUUID(), Set.of(SettlementPermission.STAFF));
        helper.assertTrue(assign(fixture, delegate, fixture.workshop()), "delegated staffing uses the same resident authority");
        VillageAllegianceRegistrySavedData.get(helper.getLevel()).removeResident(fixture.town().id(), fixture.resident().getUUID());
        helper.assertFalse(assign(fixture, delegate, fixture.storage()), "a stale resident snapshot cannot reassign someone removed from the census");
        helper.assertValueEqual(fixture.town().duties().get(fixture.resident().getUUID()), fixture.workshop().id(), "a rejected reassignment leaves the prior duty unchanged");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_remote_staffing")
    public static void remoteStaffingPreservesPersonalCommitmentsAndChildRestrictions(GameTestHelper helper) {
        var fixture = fixture(helper, 2);
        var level = helper.getLevel();
        fixture.resident().setAge(-1_000);
        SettlementCareService.onUnloaded(fixture.resident());
        helper.assertFalse(assign(fixture, fixture.leader(), fixture.workshop()), "children cannot be remotely employed");
        fixture.resident().setAge(0);
        VillagerAssignmentStore.hire(fixture.resident(), UUID.randomUUID(), HiredVillagerRole.CRAFTSMAN, 0L, null);
        SettlementCareService.onUnloaded(fixture.resident());
        helper.assertFalse(assign(fixture, fixture.leader(), fixture.workshop()), "a recorded personal hire cannot become a civic job");
        helper.assertTrue(fixture.town().residents().get(fixture.resident().getUUID()).availableRoles(level).isEmpty(), "committed residents have no remote civic choices");
        VillagerAssignmentStore.unassign(fixture.resident());
        VillagerAssignmentStore.beginFollowing(fixture.resident(), UUID.randomUUID(), fixture.resident().blockPosition(), "", false);
        SettlementCareService.onUnloaded(fixture.resident());
        helper.assertFalse(assign(fixture, fixture.leader(), fixture.workshop()), "personal following also blocks remote civic assignment");
        helper.assertTrue(fixture.town().workers().isEmpty(), "refused transfers do not create an alternative worker inventory");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_remote_staffing")
    public static void remoteStaffingUsesNativeProfessionAndStageEligibility(GameTestHelper helper) {
        var fixture = fixture(helper, 3);
        var construction = area(fixture, SettlementAreaType.CONSTRUCTION, 20);
        helper.assertFalse(assign(fixture, fixture.leader(), construction), "a camp cannot commission civic construction staff");
        fixture.town().advance();
        helper.assertTrue(assign(fixture, fixture.leader(), construction), "a qualified mason can become a builder at Hamlet");
        var post = area(fixture, SettlementAreaType.TRADING_POST, 30);
        fixture.town().advance();
        helper.assertFalse(assign(fixture, fixture.leader(), post), "stage eligibility cannot bypass the native restricted clerk profession");
        helper.assertFalse(fixture.town().residents().get(fixture.resident().getUUID()).availableRoles(helper.getLevel()).contains(HiredVillagerRole.NITWIT),
                "cached role choices use the same profession restrictions");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_remote_staffing")
    public static void oldCareSavesRemainReadableWithoutInventingStaffingContext(GameTestHelper helper) {
        var fixture = fixture(helper, 4);
        var old = fixture.town().residents().get(fixture.resident().getUUID()).save();
        old.remove("EntityContext");
        fixture.town().recordResident(SettlementResidentState.load(old));
        helper.assertFalse(assign(fixture, fixture.leader(), fixture.workshop()), "old snapshots need native observation before a new unloaded assignment");
        helper.assertValueEqual(fixture.town().residents().get(fixture.resident().getUUID()).food(), old.getInt("Food"), "care data survives the compatible upgrade");
        SettlementCareService.onUnloaded(fixture.resident());
        helper.assertTrue(assign(fixture, fixture.leader(), fixture.workshop()), "observing the actual resident supplies the missing context");
        var saved = SettlementSavedData.get(helper.getLevel()).save(new CompoundTag(), helper.getLevel().registryAccess());
        var restored = SettlementSavedData.load(saved, helper.getLevel().registryAccess()).get(fixture.town().id()).orElseThrow();
        helper.assertTrue(restored.residents().get(fixture.resident().getUUID()).context(helper.getLevel()) != null, "current settlement saves retain their native resident context");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_remote_staffing")
    public static void foreignResidentContextIsRejectedWhenLoading(GameTestHelper helper) {
        var fixture = fixture(helper, 5);
        var saved = fixture.town().residents().get(fixture.resident().getUUID()).save();
        saved.getCompound("EntityContext").putUUID("UUID", UUID.randomUUID());
        boolean rejected = false;
        try {
            SettlementResidentState.load(saved);
        } catch (IllegalArgumentException exception) {
            rejected = true;
        }
        helper.assertTrue(rejected, "resident snapshots cannot impersonate a different entity identity");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_remote_staffing")
    public static void unloadedJobChangesPreserveCraftedInventoryAcrossSaveAndReturn(GameTestHelper helper) {
        var fixture = fixture(helper, 6);
        var level = helper.getLevel();
        HiredJobInventory.getJobInventory(fixture.resident()).setItem(HiredJobInventory.HOTBAR_START, new ItemStack(Items.OAK_PLANKS, 2));
        SettlementCareService.onUnloaded(fixture.resident());
        var original = fixture.resident().saveWithoutId(new CompoundTag());
        helper.assertTrue(assign(fixture, fixture.leader(), fixture.workshop()), "the absent resident receives the first job");
        craft(helper, fixture);
        helper.assertTrue(assign(fixture, fixture.leader(), fixture.storage()), "the absent craftsman can become a hauler");
        var worker = fixture.town().workers().get(fixture.resident().getUUID());
        helper.assertValueEqual(count(helper, worker, Items.OAK_PLANKS), 0, "job changes do not restore consumed supplies");
        helper.assertValueEqual(count(helper, worker, Items.STICK), 4, "job changes retain the actual finished output");
        fixture.town().recordWorker(SettlementWorkerState.load(worker.save()));
        var care = fixture.town().residents().get(worker.id());
        fixture.town().recordResident(SettlementResidentState.load(care.save()));
        helper.assertTrue(assign(fixture, fixture.leader(), fixture.workshop()), "another remote assignment uses the saved current inventory");
        var returning = EntityType.VILLAGER.create(level);
        returning.load(original);
        returning.setPos(helper.absolutePos(new BlockPos(2, 2, 2)).getBottomCenter());
        helper.assertTrue(level.addFreshEntity(returning), "the original resident returns once");
        try {
            SettlementBackgroundService.onActive(returning);
            var inventory = HiredJobInventory.getJobInventory(returning);
            helper.assertValueEqual(inventory.countItem(Items.STICK), 4, "returning native inventory receives the current output");
            helper.assertValueEqual(inventory.countItem(Items.OAK_PLANKS), 0, "returning native inventory cannot replay old inputs");
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) if (inventory.getItem(slot).is(Items.STICK)) inventory.setItem(slot, ItemStack.EMPTY);
            SettlementBackgroundService.onActive(returning);
            helper.assertValueEqual(inventory.countItem(Items.STICK), 0, "a second handoff cannot refill collected output");
            helper.assertValueEqual(VillagerAssignmentStore.civicArea(returning).orElseThrow(), fixture.workshop().id(), "the returning resident follows the latest assignment");
        } finally {
            returning.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_remote_staffing")
    public static void activeAssignmentAndReleaseAcknowledgeInventoryBeforeLaterEdits(GameTestHelper helper) {
        var fixture = fixture(helper, 7);
        var resident = fixture.resident();
        helper.assertTrue(SettlementWorkforceService.assign(helper.getLevel(), fixture.leader(), fixture.town().id(), resident, fixture.workshop().id()),
                "native assignment records the same workforce authority");
        var inventory = HiredJobInventory.getJobInventory(resident);
        inventory.setItem(HiredJobInventory.HOTBAR_START, new ItemStack(Items.DIAMOND, 3));
        SettlementBackgroundService.onActive(resident);
        helper.assertValueEqual(inventory.countItem(Items.DIAMOND), 3, "the assignment receipt preserves subsequent native inventory edits");
        resident.setPos(helper.absolutePos(new BlockPos(2, 2, 2)).getBottomCenter());
        helper.assertTrue(helper.getLevel().addFreshEntity(resident), "the resident is tracked for a loaded release");
        try {
            var worker = fixture.town().workers().get(resident.getUUID());
            var items = worker.items(helper.getLevel().registryAccess());
            items.set(HiredJobInventory.HOTBAR_START, new ItemStack(Items.EMERALD, 2));
            worker.updateInventory(helper.getLevel().registryAccess(), items);
            worker.changed();
            helper.assertTrue(SettlementWorkforceService.release(helper.getLevel(), fixture.leader(), fixture.town().id(), resident.getUUID()),
                    "the leader can release pending civic work");
            helper.assertValueEqual(inventory.countItem(Items.EMERALD), 2, "release delivers the pending inventory immediately");
            inventory.setItem(HiredJobInventory.HOTBAR_START, new ItemStack(Items.DIAMOND, 5));
            SettlementBackgroundService.onActive(resident);
            helper.assertValueEqual(inventory.countItem(Items.DIAMOND), 5, "released worker snapshots cannot overwrite later inventory edits");
        } finally {
            resident.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_remote_staffing")
    public static void aLoadedPersonalCommitmentWinsOverAnOlderUnloadedSnapshot(GameTestHelper helper) {
        var fixture = fixture(helper, 8);
        var resident = fixture.resident();
        resident.setPos(helper.absolutePos(new BlockPos(2, 2, 2)).getBottomCenter());
        helper.assertTrue(helper.getLevel().addFreshEntity(resident), "the current resident is tracked");
        try {
            VillagerAssignmentStore.hire(resident, UUID.randomUUID(), HiredVillagerRole.CRAFTSMAN, 0L, null);
            helper.assertFalse(assign(fixture, fixture.leader(), fixture.workshop()), "current loaded commitments override the earlier eligible snapshot");
            helper.assertTrue(fixture.town().duties().isEmpty(), "the denied command cannot add a civic duty");
        } finally {
            resident.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_remote_staffing")
    public static void remoteJobChangesKeepPendingFishingExperience(GameTestHelper helper) {
        var fixture = fixture(helper, 9);
        var fishing = area(fixture, SettlementAreaType.FISHERY, 20);
        helper.assertTrue(assign(fixture, fixture.leader(), fishing), "the unloaded resident becomes a fisher");
        var worker = fixture.town().workers().get(fixture.resident().getUUID());
        var experience = new ListTag();
        var entry = new CompoundTag();
        entry.putInt("Value", 3);
        entry.putLong("Expires", helper.getLevel().getGameTime() + 6_000L);
        experience.add(entry);
        worker.settings().put("SettlementFishingExperience", experience);
        worker.changed();
        var original = fixture.resident().saveWithoutId(new CompoundTag());
        helper.assertTrue(assign(fixture, fixture.leader(), fixture.storage()), "a fisher with pending experience can take another job");
        var updated = fixture.town().workers().get(worker.id());
        helper.assertValueEqual(updated.settings().getList("SettlementFishingExperience", Tag.TAG_COMPOUND), experience, "the new job retains undelivered fishing experience");
        var returning = EntityType.VILLAGER.create(helper.getLevel());
        returning.load(original);
        updated.restore(returning);
        helper.assertValueEqual(HiredWorkStateStore.state(returning).getList("SettlementFishingExperience", Tag.TAG_COMPOUND), experience,
                "the native handoff restores experience even after leaving the fishing role");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_remote_staffing")
    public static void backgroundAdulthoodUsesCurrentCareWhenAssigning(GameTestHelper helper) {
        var fixture = fixture(helper, 10);
        var level = helper.getLevel();
        fixture.resident().setAge(-100);
        VillageAllegianceApi.assignKnown(level, fixture.resident(), fixture.town().id(), AllegianceAssignmentSource.BIRTH);
        SettlementCareService.onUnloaded(fixture.resident());
        helper.assertFalse(assign(fixture, fixture.leader(), fixture.workshop()), "the recorded child cannot work early");
        SettlementCareService.update(level, fixture.town(), level.getGameTime() + 200L);
        helper.assertTrue(assign(fixture, fixture.leader(), fixture.workshop()), "the current background age takes precedence over the original baby snapshot");
        helper.assertTrue(fixture.town().residents().get(fixture.resident().getUUID()).context(level).getAge() >= 0, "the refreshed native context retains adulthood");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_remote_staffing")
    public static void newlyAdmittedResidentsKeepTheirRemoteWorkWhenMaterializing(GameTestHelper helper) {
        var fixture = SettlementPopulationGameTests.fixture(helper);
        var level = helper.getLevel();
        var center = fixture.town().center();
        var resident = EntityType.VILLAGER.create(level);
        resident.setPos(center.getBottomCenter());
        resident.setNoAi(true);
        resident.setVillagerData(resident.getVillagerData().setProfession(VillagerProfession.MASON));
        var filter = new ItemStack(VillagerRetaliationItems.ITEM_FILTER.get());
        VillagerItemFilterData.setEntry(filter, 0, new ItemStack(Items.STICK));
        HiredJobInventory.getJobInventory(resident).setItem(HiredJobInventory.FILTER_SLOT, filter);
        HiredJobInventory.getJobInventory(resident).setItem(HiredJobInventory.HOTBAR_START, new ItemStack(Items.OAK_PLANKS, 2));
        SettlementArrivalService.admit(level, fixture.town(), resident);
        var workshop = new SettlementArea(UUID.randomUUID(), SettlementAreaType.WORKSHOP, "Workshop", center.west(10), center.offset(-8, 2, 2), 5, true);
        helper.assertTrue(fixture.town().putArea(fixture.town().leader(), workshop), "the new arrival has a registered workplace");
        level.setBlock(workshop.min(), Blocks.CRAFTING_TABLE.defaultBlockState(), 2);
        SettlementBuildingService.refresh(level, fixture.town(), workshop);
        helper.assertTrue(SettlementManagementService.execute(fixture.leader(), new SettlementManagementActionPayload(fixture.town().id().value(),
                new SettlementManagementActionPayload.Assign(resident.getUUID(), workshop.id()))), "an admitted resident can be assigned before its world entity exists");
        var worker = fixture.town().workers().get(resident.getUUID());
        worker.settings().putBoolean("AutoDepositOutputs", false);
        for (int index = 0; index < 20; index++) {
            SettlementTestPlots.planSupplies(level, fixture.town());
            SettlementBackgroundCrafting.tick(level, fixture.town(), worker, 200, 1.0D);
        }
        helper.assertValueEqual(count(helper, worker, Items.STICK), 4, "the pending arrival completes one recipe from its recorded inventory");
        helper.assertTrue(SettlementArrivalService.materialize(level, fixture.town(), resident.getUUID()), "the admitted resident reaches its physical town");
        var actual = (Villager) level.getEntity(resident.getUUID());
        try {
            helper.assertValueEqual(HiredJobInventory.getJobInventory(actual).countItem(Items.STICK), 4, "materialization reconciles the real completed output");
            helper.assertValueEqual(HiredJobInventory.getJobInventory(actual).countItem(Items.OAK_PLANKS), 0, "the older arrival snapshot cannot restore spent inputs");
            helper.assertValueEqual(VillagerAssignmentStore.civicArea(actual).orElseThrow(), workshop.id(), "the latest job applies before native behavior resumes");
        } finally {
            actual.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_remote_staffing_construction")
    public static void unfinishedConstructionMustBeCancelledBeforeRemotelyReassigningItsBuilder(GameTestHelper helper) {
        SettlementBackgroundConstructionGameTests.capacityFixture(helper, 50, Map.of(BlockPos.ZERO, Blocks.OAK_PLANKS.defaultBlockState(),
                BlockPos.ZERO.east(), Blocks.OAK_PLANKS.defaultBlockState()), fixture -> {
            fixture.depot().setItem(0, new ItemStack(Items.OAK_PLANKS));
            SettlementBackgroundConstructionGameTests.work(helper, fixture, 50);
            helper.assertTrue(fixture.project().active() && fixture.site().pending(), "the partly funded project retains a completed placement");
            var leader = FakePlayerFactory.get(helper.getLevel(), new GameProfile(fixture.town().leader(), "BuildLeader"));
            var assignment = new SettlementManagementActionPayload(fixture.town().id().value(),
                    new SettlementManagementActionPayload.Assign(fixture.builder().getUUID(), fixture.depot().area()));
            helper.assertFalse(SettlementManagementService.execute(leader, assignment), "active construction protects its unloaded builder assignment");
            helper.assertTrue(SettlementConstructionService.cancel(leader, fixture.town(), fixture.project().id()), "unfinished construction is explicitly cancelled");
            helper.assertTrue(SettlementManagementService.execute(leader, assignment), "the released unloaded builder can transfer to hauling");
            helper.assertValueEqual(fixture.project().status(), SettlementProject.Status.CANCELLED, "the cancelled project retains its receipt");
            helper.assertTrue(fixture.site().pending(), "already funded world changes remain queued for delivery");
            var worker = fixture.town().workers().get(fixture.builder().getUUID());
            helper.assertValueEqual(worker.role(), HiredVillagerRole.COURIER, "the authoritative worker ledger takes the new job");
            helper.assertValueEqual(count(helper, worker, Items.OAK_PLANKS) + fixture.depot().count(stack -> stack.is(Items.OAK_PLANKS)), 0,
                    "changing jobs does not refund a consumed building material");
            helper.assertFalse(helper.getLevel().hasChunkAt(fixture.origin()), "the transfer does not force the construction site to load");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_remote_staffing")
    public static void remoteJobChangesPreserveCaughtFishWhenTheInventoryIsFull(GameTestHelper helper) {
        var fixture = fixture(helper, 11);
        var level = helper.getLevel();
        var original = fixture.resident().saveWithoutId(new CompoundTag());
        var fishery = area(fixture, SettlementAreaType.FISHERY, 20);
        helper.assertTrue(assign(fixture, fixture.leader(), fishery), "the absent resident becomes a fisher");
        var fisher = fixture.town().workers().get(fixture.resident().getUUID());
        var items = fisher.items(level.registryAccess());
        for (int slot : fisher.outputSlots()) items.set(slot, new ItemStack(Items.DIRT, 64));
        fisher.updateInventory(level.registryAccess(), items);
        var caught = new ListTag();
        caught.add(new ItemStack(Items.COD, 2).save(level.registryAccess()));
        var pending = new CompoundTag();
        pending.put("Items", caught);
        fisher.settings().put("SettlementFishingCatch", pending);
        fisher.changed();
        helper.assertTrue(assign(fixture, fixture.leader(), fixture.storage()), "a full inventory does not discard caught fish when the job changes");
        var worker = fixture.town().workers().get(fisher.id());
        helper.assertTrue(SettlementBackgroundFishing.hasPendingCatch(worker.settings()), "the new worker record retains the undelivered catch");
        var returning = EntityType.VILLAGER.create(level);
        returning.load(original);
        returning.setPos(helper.absolutePos(new BlockPos(2, 2, 2)).getBottomCenter());
        helper.assertTrue(level.addFreshEntity(returning), "the worker returns to its native entity");
        try {
            SettlementBackgroundService.onActive(returning);
            helper.assertTrue(SettlementBackgroundFishing.hasPendingCatch(HiredWorkStateStore.state(returning)), "a full returned inventory keeps the original catch pending");
            var inventory = HiredJobInventory.getJobInventory(returning);
            inventory.setItem(HiredJobInventory.MAIN_GRID_START, ItemStack.EMPTY);
            SettlementBackgroundService.onActive(returning);
            helper.assertValueEqual(inventory.countItem(Items.COD), 2, "freeing room collects the same two caught fish");
            SettlementBackgroundService.onActive(returning);
            helper.assertValueEqual(inventory.countItem(Items.COD), 2, "repeated reconciliation cannot duplicate the catch");
        } finally {
            returning.discard();
        }
        helper.succeed();
    }

    private static void craft(GameTestHelper helper, Fixture fixture) {
        var worker = fixture.town().workers().get(fixture.resident().getUUID());
        worker.settings().putBoolean("AutoDepositOutputs", false);
        for (int index = 0; index < 20; index++) {
            SettlementTestPlots.planSupplies(helper.getLevel(), fixture.town());
            SettlementBackgroundCrafting.tick(helper.getLevel(), fixture.town(), worker, 200, 1.0D);
        }
    }

    private static int count(GameTestHelper helper, SettlementWorkerState worker, Item item) {
        return worker.items(helper.getLevel().registryAccess()).stream().filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
    }

    private static boolean assign(Fixture fixture, ServerPlayer actor, SettlementArea area) {
        return SettlementManagementService.execute(actor, new SettlementManagementActionPayload(fixture.town().id().value(),
                new SettlementManagementActionPayload.Assign(fixture.resident().getUUID(), area.id())));
    }

    private static SettlementArea area(Fixture fixture, SettlementAreaType type, int offset) {
        var min = fixture.town().center().east(offset);
        var area = new SettlementArea(UUID.randomUUID(), type, "Workplace", min, min.offset(2, 2, 2), 5, true);
        SettlementTestPlots.claim(fixture.leader().serverLevel(), fixture.town(), area.min(), area.max());
        if (!fixture.town().putArea(fixture.town().leader(), area)) throw new IllegalStateException("Test workplace could not be registered");
        return area;
    }

    private static Fixture fixture(GameTestHelper helper, int region) {
        var level = helper.getLevel();
        var origin = helper.absolutePos(BlockPos.ZERO);
        var center = new BlockPos(18_000_000 + origin.getX() * 16 + region * 256, 80, 18_000_000 + origin.getZ() * 16);
        var leader = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "RemoteLeader"));
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var id = registry.create(level.getGameTime(), level.dimension().location(), center, "Remote town");
        var town = SettlementSavedData.get(level).establish(registry, id, leader.getUUID(), center, level.getGameTime()).orElseThrow();
        var workshop = new SettlementArea(UUID.randomUUID(), SettlementAreaType.WORKSHOP, "Workshop", center, center.offset(2, 2, 2), 5, true);
        var storage = new SettlementArea(UUID.randomUUID(), SettlementAreaType.DEPOT, "Storage", center.east(8), center.offset(10, 2, 2), 5, true);
        town.putArea(town.leader(), workshop);
        town.putArea(town.leader(), storage);
        town.recordSurvey(workshop.id(), new SettlementBuildingSurvey.Survey(level.getGameTime(), List.of(), List.of(center), false,
                Map.of(center, ResourceLocation.withDefaultNamespace("crafting_table"))));
        var depot = town.registerDepot(town.leader(), storage.id(), storage.min(), SettlementDepotPurpose.MATERIALS);
        depot.observeRules(List.of());
        var resident = EntityType.VILLAGER.create(level);
        resident.setPos(center.getBottomCenter());
        resident.setNoAi(true);
        resident.setVillagerData(resident.getVillagerData().setProfession(VillagerProfession.MASON));
        VillageAllegianceApi.assignKnown(level, resident, id, AllegianceAssignmentSource.EXPLICIT_API);
        var filter = new ItemStack(VillagerRetaliationItems.ITEM_FILTER.get());
        VillagerItemFilterData.setEntry(filter, 0, new ItemStack(Items.STICK));
        HiredJobInventory.getJobInventory(resident).setItem(HiredJobInventory.FILTER_SLOT, filter);
        SettlementCareService.onUnloaded(resident);
        helper.assertFalse(level.hasChunkAt(center), "the staffing fixture begins outside all loaded chunks");
        return new Fixture(town, workshop, storage, depot, resident, leader);
    }

    private record Fixture(SettlementRecord town, SettlementArea workshop, SettlementArea storage, SettlementDepot depot,
                           Villager resident, ServerPlayer leader) {
    }
}
