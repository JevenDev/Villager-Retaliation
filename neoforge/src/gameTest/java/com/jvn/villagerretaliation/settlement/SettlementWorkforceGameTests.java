package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.AllegianceAssignmentSource;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceApi;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.interaction.HireContract;
import com.jvn.villagerretaliation.interaction.HireContractAssignmentAdapter;
import com.jvn.villagerretaliation.interaction.HireContractStore;
import com.jvn.villagerretaliation.interaction.HiredVillagerContractService;
import com.jvn.villagerretaliation.interaction.HiredVillagerFocusService;
import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.HiredVillagerWorkService;
import com.jvn.villagerretaliation.interaction.HiredWorkSession;
import com.jvn.villagerretaliation.interaction.HiredWorkDecisionScheduler;
import com.jvn.villagerretaliation.interaction.RecruitmentPolicy;
import com.jvn.villagerretaliation.interaction.VillagerAssignmentCommand;
import com.jvn.villagerretaliation.interaction.VillagerAssignmentState;
import com.jvn.villagerretaliation.interaction.VillagerAssignmentStore;
import com.jvn.villagerretaliation.interaction.VillagerWorkAuthority;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.jvn.villagerretaliation.inventory.VillagerInventoryMenu;
import com.jvn.villagerretaliation.inventory.VillagerItemFilterService;
import com.jvn.villagerretaliation.inventory.VillagerJobInventoryAuthorization;
import com.jvn.villagerretaliation.party.PartyVillagerContractService;
import com.jvn.villagerretaliation.villager.VillagerBehaviorSuppressionPolicy;
import com.mojang.authlib.GameProfile;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class SettlementWorkforceGameTests {
    private SettlementWorkforceGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void civicEmploymentUsesExistingFarmWithoutOnlineLeader(GameTestHelper helper) {
        Fixture fixture = fixture(helper, new UUID(Long.MIN_VALUE, -helper.getLevel().getGameTime()));
        ServerLevel level = helper.getLevel();
        long dayTime = level.getDayTime();
        BlockPos crop = fixture.villager().blockPosition().east();
        var oldCrop = level.getBlockState(crop);
        var oldFloor = level.getBlockState(crop.below());
        try {
            level.setDayTime(6_000L);
            assign(helper, fixture);
            var authority = VillagerWorkAuthority.resolve(level, fixture.villager());
            helper.assertTrue(authority.civic() && authority.canWork(level, fixture.villager()),
                    "civic work is available during work hours");
            helper.assertTrue(authority.onlineOwner(level) == null, "the leader is not an online server player");
            helper.assertFalse(HiredVillagerContractService.hasContract(fixture.villager()), "civic work creates no wages");
            level.setBlockAndUpdate(crop.below(), Blocks.FARMLAND.defaultBlockState());
            level.setBlockAndUpdate(crop, ((CropBlock) Blocks.WHEAT).getStateForAge(7));
            var session = HiredWorkSession.active(level, fixture.villager());
            session.inventory().setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.IRON_HOE));
            session.inventory().insertPlainSupply(new ItemStack(Items.WHEAT_SEEDS));
            HiredWorkDecisionScheduler.clearRuntimeState();
            HiredVillagerWorkService.onVillagerTickPost(fixture.villager());
            helper.assertValueEqual(session.state().getString("Status"), "interaction.work.farming.completed_crop",
                    "the existing farmer routine completes without an online leader");
            helper.assertValueEqual(level.getBlockState(crop).getValue(CropBlock.AGE), 0, "the real crop is replanted");
            helper.assertTrue(session.inventory().hasOutput(stack -> stack.is(Items.WHEAT)),
                    "the harvest enters the real worker inventory");
        } finally {
            level.setBlockAndUpdate(crop, oldCrop);
            level.setBlockAndUpdate(crop.below(), oldFloor);
            level.setDayTime(dayTime);
            fixture.villager().discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void civicResidentsCannotBeHiredCommandedOrPartied(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        assign(helper, fixture);
        var villager = fixture.villager();
        var level = helper.getLevel();
        helper.assertFalse(RecruitmentPolicy.mayHire(level, villager, fixture.leader()).allowed(),
                "leadership must explicitly release a civic worker before hiring");
        helper.assertFalse(RecruitmentPolicy.mayCommand(level, villager, fixture.leader(),
                VillagerAssignmentCommand.FOLLOW).allowed(), "personal following cannot displace a civic duty");
        helper.assertFalse(HiredVillagerContractService.startHireContract(level, villager, fixture.leader(),
                1, 5, HiredVillagerRole.FARMING), "direct contract creation cannot bypass town authority");
        helper.assertFalse(PartyVillagerContractService.recruit(fixture.leader(), villager).success(),
                "party recruitment cannot displace a civic duty");
        VillagerAssignmentStore.beginFollowing(villager, fixture.leader().getUUID(), villager.blockPosition(), "", false);
        VillagerAssignmentStore.hire(villager, fixture.leader().getUUID(), HiredVillagerRole.FARMING, 0L, null);
        helper.assertValueEqual(VillagerAssignmentStore.snapshot(villager).state(), VillagerAssignmentState.CIVIC,
                "direct assignment helpers preserve civic employment");
        helper.assertFalse(VillagerAssignmentStore.isFollowing(villager), "legacy following tags cannot bypass civic duty");
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void civicDutySurvivesReloadAndContractSynchronization(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        assign(helper, fixture);
        HireContractAssignmentAdapter.synchronize(helper.getLevel(), fixture.villager());
        HireContractAssignmentAdapter.contractEnded(fixture.villager());
        helper.assertValueEqual(VillagerAssignmentStore.snapshot(fixture.villager()).state(),
                VillagerAssignmentState.CIVIC, "absence of a paid contract does not clear civic work");
        CompoundTag saved = new CompoundTag();
        fixture.villager().saveWithoutId(saved);
        Villager restored = EntityType.VILLAGER.create(helper.getLevel());
        restored.load(saved);
        helper.assertValueEqual(VillagerAssignmentStore.civicSettlement(restored).orElseThrow(),
                fixture.town().id().value(), "entity save retains the settlement identity");
        helper.assertValueEqual(VillagerAssignmentStore.civicArea(restored).orElseThrow(),
                fixture.area().id(), "entity save retains the workplace");
        var towns = SettlementSavedData.get(helper.getLevel());
        var restoredTowns = SettlementSavedData.load(towns.save(new CompoundTag(), helper.getLevel().registryAccess()),
                helper.getLevel().registryAccess());
        helper.assertValueEqual(restoredTowns.get(fixture.town().id()).orElseThrow().duties()
                .get(fixture.villager().getUUID()), fixture.area().id(), "town save retains the offline duty");
        fixture.villager().discard();
        restored.discard();
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void paidContractWinsConflictingCivicStateWithoutLosingPayment(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        assign(helper, fixture);
        HireContract paid = HireContract.regular(UUID.randomUUID(), helper.getLevel().getGameTime(),
                3, 4, 12, HiredVillagerRole.FARMING);
        HireContractStore.save(fixture.villager(), paid);
        CompoundTag before = fixture.villager().getPersistentData().getCompound("VillagerRetaliationHireContract").copy();
        HireContractAssignmentAdapter.synchronize(helper.getLevel(), fixture.villager());
        helper.assertValueEqual(fixture.villager().getPersistentData().getCompound("VillagerRetaliationHireContract"), before,
                "reconciliation preserves all paid contract and escrow fields");
        helper.assertValueEqual(VillagerAssignmentStore.snapshot(fixture.villager()).state(),
                VillagerAssignmentState.HIRED, "the existing paid commitment remains authoritative");
        helper.assertFalse(fixture.town().duties().containsKey(fixture.villager().getUUID()),
                "the conflicting civic duty is removed");
        fixture.villager().discard();
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void civicWorkKeepsRestAndFamilyLife(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        long dayTime = helper.getLevel().getDayTime();
        try {
            assign(helper, fixture);
            helper.getLevel().setDayTime(10_000L);
            HiredVillagerWorkService.onVillagerTickPost(fixture.villager());
            helper.assertFalse(VillagerWorkAuthority.resolve(helper.getLevel(), fixture.villager())
                    .canWork(helper.getLevel(), fixture.villager()), "ordinary civic workers have time off");
            helper.assertFalse(HiredVillagerFocusService.shouldSuppressVanillaBrainTick(helper.getLevel(), fixture.villager()),
                    "off-duty scheduling remains available");
            helper.assertFalse(VillagerBehaviorSuppressionPolicy.suppresses(fixture.villager(),
                    VillagerBehaviorSuppressionPolicy.Behavior.BREEDING), "civic work permits families");
            helper.assertFalse(VillagerBehaviorSuppressionPolicy.suppresses(fixture.villager(),
                    VillagerBehaviorSuppressionPolicy.Behavior.GOSSIPING), "civic work permits social life");
            helper.assertTrue(HiredVillagerFocusService.shouldUseVanillaRest(helper.getLevel(), fixture.villager()),
                    "workers yield to their ordinary schedule when off duty");
        } finally {
            helper.getLevel().setDayTime(dayTime);
            fixture.villager().discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void onlyLeadershipCanReleaseCivicCommitment(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        assign(helper, fixture);
        ServerPlayer delegate = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "StaffDelegate"));
        fixture.town().setPermissions(fixture.leader().getUUID(), delegate.getUUID(), Set.of(SettlementPermission.STAFF));
        helper.assertFalse(SettlementWorkforceService.release(helper.getLevel(), delegate,
                fixture.town().id(), fixture.villager().getUUID()), "staffing cannot authorize a personal transfer");
        helper.assertTrue(SettlementWorkforceService.release(helper.getLevel(), fixture.leader(),
                fixture.town().id(), fixture.villager().getUUID()), "leadership can explicitly release a civic worker");
        helper.assertValueEqual(VillagerAssignmentStore.snapshot(fixture.villager()).state(),
                VillagerAssignmentState.UNASSIGNED, "release clears the loaded assignment");
        helper.assertFalse(SettlementWorkforceService.blocksPersonalRecruitment(helper.getLevel(), fixture.villager()),
                "the released worker can take a personal commitment");
        fixture.villager().discard();
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void removingWorkplaceReleasesDutyAndKeepsExactBounds(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        assign(helper, fixture);
        var session = HiredWorkSession.active(helper.getLevel(), fixture.villager());
        helper.assertValueEqual(session.area().min(), fixture.area().min(), "registered minimum is used directly");
        helper.assertValueEqual(session.area().max(), fixture.area().max(), "registered maximum is used directly");
        var data = SettlementSavedData.get(helper.getLevel());
        data.removeArea(VillageAllegianceRegistrySavedData.get(helper.getLevel()), fixture.town().id(),
                fixture.leader().getUUID(), fixture.area().id());
        SettlementWorkforceService.reconcile(helper.getLevel(), fixture.villager());
        helper.assertTrue(fixture.town().duties().isEmpty(), "removing a workplace clears offline assignments");
        helper.assertValueEqual(VillagerAssignmentStore.snapshot(fixture.villager()).state(),
                VillagerAssignmentState.UNASSIGNED, "the loaded worker reconciles the removal");
        fixture.villager().discard();
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void independentVillageRetainsItsLastFoodWorker(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        var registry = VillageAllegianceRegistrySavedData.get(helper.getLevel());
        var independent = registry.create(helper.getLevel().getGameTime(), helper.getLevel().dimension().location(),
                fixture.villager().blockPosition().offset(96, 0, 0), "");
        VillageAllegianceApi.assignKnown(helper.getLevel(), fixture.villager(), independent, AllegianceAssignmentSource.EXPLICIT_API);
        helper.assertTrue(SettlementWorkforceService.blocksPersonalRecruitment(helper.getLevel(), fixture.villager()),
                "an independent village keeps its last food producer");
        Villager replacement = EntityType.VILLAGER.create(helper.getLevel());
        replacement.setVillagerData(replacement.getVillagerData().setProfession(VillagerProfession.FISHERMAN));
        replacement.setPos(fixture.villager().position());
        helper.getLevel().addFreshEntity(replacement);
        VillageAllegianceApi.assignKnown(helper.getLevel(), replacement, independent, AllegianceAssignmentSource.EXPLICIT_API);
        helper.assertFalse(SettlementWorkforceService.blocksPersonalRecruitment(helper.getLevel(), fixture.villager()),
                "a village with a replacement can spare a worker");
        fixture.villager().discard();
        replacement.discard();
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void staffedWorkplaceCannotSilentlyChangeTheResidentsRole(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        assign(helper, fixture);
        var changed = new SettlementArea(fixture.area().id(), SettlementAreaType.HOME, "House",
                fixture.area().min(), fixture.area().max(), 5, true);
        helper.assertFalse(SettlementSavedData.get(helper.getLevel()).putArea(
                VillageAllegianceRegistrySavedData.get(helper.getLevel()), fixture.town().id(),
                fixture.leader().getUUID(), changed), "a staffed workplace must be released before changing its function");
        helper.assertValueEqual(SettlementWorkforceService.role(fixture.villager()), HiredVillagerRole.FARMING,
                "a rejected building change preserves the resident's job");
        fixture.villager().discard();
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void departureRemovesTheFormerTownsWorkCommitment(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        assign(helper, fixture);
        VillageAllegianceApi.assignUnaffiliated(helper.getLevel(), fixture.villager(),
                AllegianceAssignmentSource.EXPLICIT_API);
        SettlementWorkforceService.reconcile(helper.getLevel(), fixture.villager());
        helper.assertFalse(fixture.town().duties().containsKey(fixture.villager().getUUID()),
                "a departed resident cannot keep producing for the former town");
        helper.assertValueEqual(VillagerAssignmentStore.snapshot(fixture.villager()).state(),
                VillagerAssignmentState.UNASSIGNED, "departure clears civic employment");
        fixture.villager().discard();
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void civicEquipmentAccessUsesLogisticsAndRevokesOpenMenus(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        assign(helper, fixture);
        var level = helper.getLevel();
        var delegate = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "CivicEquipment"));
        delegate.setPos(fixture.villager().position());
        var inventory = HiredJobInventory.getJobInventory(fixture.villager());
        inventory.setItem(HiredJobInventory.MAIN_GRID_START, new ItemStack(Items.BREAD, 5));
        try {
            helper.assertTrue(VillagerJobInventoryAuthorization.canAccess(level, fixture.villager(), fixture.leader()),
                    "the leader can supply civic equipment");
            fixture.town().setPermissions(fixture.leader().getUUID(), delegate.getUUID(), Set.of(SettlementPermission.STAFF));
            helper.assertFalse(VillagerJobInventoryAuthorization.canAccess(level, fixture.villager(), delegate),
                    "staffing access does not grant control of supplies");
            fixture.town().setPermissions(fixture.leader().getUUID(), delegate.getUUID(), Set.of(SettlementPermission.LOGISTICS));
            helper.assertTrue(VillagerJobInventoryAuthorization.canAccess(level, fixture.villager(), delegate),
                    "logistics delegates can manage civic equipment");
            var menu = new VillagerInventoryMenu(1, delegate.getInventory(), fixture.villager(), VillagerInventoryMenu.ViewMode.JOB);
            try {
                helper.assertTrue(menu.stillValid(delegate), "the nearby authorized job inventory stays open");
                helper.assertFalse(menu.canSwitchToPersonalInventory(), "settlement access does not reveal personal belongings");
                menu.switchViewMode(VillagerInventoryMenu.ViewMode.PERSONAL);
                helper.assertValueEqual(menu.viewMode(), VillagerInventoryMenu.ViewMode.JOB, "personal inventory remains inaccessible");
                fixture.town().setPermissions(fixture.leader().getUUID(), delegate.getUUID(), Set.of());
                helper.assertFalse(menu.stillValid(delegate), "revoking logistics invalidates an already open menu");
                helper.assertTrue(menu.quickMoveStack(delegate, HiredJobInventory.MAIN_GRID_START).isEmpty(),
                        "stale menu shift-clicks cannot take settlement goods");
                menu.clicked(HiredJobInventory.MAIN_GRID_START, 0, net.minecraft.world.inventory.ClickType.PICKUP, delegate);
                helper.assertValueEqual(inventory.getItem(HiredJobInventory.MAIN_GRID_START).getCount(), 5,
                        "stale menu clicks leave goods untouched");
                helper.assertTrue(menu.getCarried().isEmpty(), "no item reaches the cursor after access is revoked");
            } finally {
                menu.removed(delegate);
            }
            helper.assertTrue(VillagerJobInventoryAuthorization.activeContractId(level, fixture.villager()).isEmpty(),
                    "civic equipment does not invent a paid contract identity");
        } finally {
            fixture.villager().discard();
            delegate.getInventory().clearContent();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void civicFilterAssignmentConsumesRealItemsAndRejectsStaleDelegates(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        assign(helper, fixture);
        var delegate = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "CivicFilter"));
        delegate.setPos(fixture.villager().position());
        delegate.getInventory().clearContent();
        delegate.getAbilities().instabuild = false;
        fixture.town().setPermissions(fixture.leader().getUUID(), delegate.getUUID(), Set.of(SettlementPermission.LOGISTICS));
        ItemStack filter = new ItemStack(com.jvn.villagerretaliation.item.VillagerRetaliationItems.ITEM_FILTER.get(), 3);
        com.jvn.villagerretaliation.item.VillagerItemFilterData.setEntry(filter, 0, new ItemStack(Items.WHEAT_SEEDS));
        delegate.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, filter);
        try {
            var mode = com.jvn.villagerretaliation.item.VillagerItemFilterData.Mode.ALLOWLIST;
            var result = VillagerItemFilterService.assignHeldFilter(delegate, fixture.villager(), mode);
            helper.assertTrue(result.assigned(), "a logistics delegate can assign the held filter");
            helper.assertValueEqual(filter.getCount(), 2, "assignment consumes exactly one real filter");
            helper.assertTrue(VillagerItemFilterService.mayWithdraw(fixture.villager(), new ItemStack(Items.WHEAT_SEEDS)),
                    "the civic worker follows the assigned filter");
            helper.assertFalse(VillagerItemFilterService.mayWithdraw(fixture.villager(), new ItemStack(Items.COBBLESTONE)),
                    "the civic filter rejects unlisted supplies");
            ItemStack replacement = new ItemStack(com.jvn.villagerretaliation.item.VillagerRetaliationItems.ITEM_FILTER.get(), 2);
            com.jvn.villagerretaliation.item.VillagerItemFilterData.setEntry(replacement, 0, new ItemStack(Items.CARROT));
            delegate.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, replacement);
            result = VillagerItemFilterService.assignHeldFilter(delegate, fixture.villager(), mode);
            helper.assertTrue(result.assigned() && result.replaced(), "replacement returns the previous filter");
            long returned = delegate.getInventory().items.stream()
                    .filter(stack -> com.jvn.villagerretaliation.item.VillagerRetaliationItems.isItemFilter(stack)
                            && com.jvn.villagerretaliation.item.VillagerItemFilterData.entry(stack, 0).is(Items.WHEAT_SEEDS))
                    .mapToInt(ItemStack::getCount).sum();
            helper.assertValueEqual(returned, 1L, "the prior filter is returned once");
            fixture.town().setPermissions(fixture.leader().getUUID(), delegate.getUUID(), Set.of());
            helper.assertFalse(VillagerItemFilterService.assignHeldFilter(delegate, fixture.villager(), mode).assigned(),
                    "revoked delegates cannot use a stale filter conversation");
            helper.assertValueEqual(replacement.getCount(), 1, "a denied request consumes no filter");
            helper.assertFalse(HiredVillagerContractService.hasContract(fixture.villager()), "filter access creates no wages or hire contract");
        } finally {
            fixture.villager().discard();
            delegate.getInventory().clearContent();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void civicEquipmentAccessEndsWhenLeadershipOrDutyChanges(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        assign(helper, fixture);
        var level = helper.getLevel();
        var successor = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "CivicSuccessor"));
        try {
            var offer = fixture.town().offerLeadership(fixture.leader().getUUID(), successor.getUUID(), level.getGameTime());
            fixture.town().acceptLeadership(successor.getUUID(), offer.id(), level.getGameTime());
            helper.assertFalse(VillagerJobInventoryAuthorization.canAccess(level, fixture.villager(), fixture.leader()),
                    "former leaders do not retain civic equipment access");
            helper.assertTrue(VillagerJobInventoryAuthorization.canAccess(level, fixture.villager(), successor),
                    "the successor can manage the existing civic equipment");
            SettlementWorkforceService.release(level, successor, fixture.town().id(), fixture.villager().getUUID());
            helper.assertFalse(VillagerJobInventoryAuthorization.canAccess(level, fixture.villager(), successor),
                    "release removes civic access without granting access to personal property");
            helper.assertFalse(VillagerItemFilterService.canAssign(level, fixture.villager(), successor),
                    "released residents do not accept civic filters");
        } finally {
            fixture.villager().discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void noviceCivicWorkersKeepProfessionUntilReleased(GameTestHelper helper) {
        var fixture = fixture(helper);
        var villager = fixture.villager();
        var level = helper.getLevel();
        try {
            villager.setVillagerXp(0);
            villager.getBrain().eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.JOB_SITE);
            assign(helper, fixture);
            var reset = net.minecraft.world.entity.ai.behavior.ResetProfession.create();
            helper.assertFalse(reset.tryStart(level, villager, level.getGameTime()),
                    "civic employment keeps a novice profession without claiming a vanilla job site");
            helper.assertValueEqual(villager.getVillagerData().getProfession(), VillagerProfession.FARMER,
                    "the recruited farmer remains able to execute its civic work");
            helper.assertValueEqual(villager.getVillagerXp(), 0, "profession protection grants no trade experience");
            helper.assertTrue(SettlementWorkforceService.release(level, fixture.leader(), fixture.town().id(), villager.getUUID()),
                    "the leader releases the civic duty");
            helper.assertTrue(reset.tryStart(level, villager, level.getGameTime()),
                    "the native profession reset resumes after release");
            helper.assertValueEqual(villager.getVillagerData().getProfession(), VillagerProfession.NONE,
                    "an unassigned novice can return to ordinary job selection");
        } finally {
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void farmerFinishesDepositBeforeHoeCheckAndFieldCooldown(GameTestHelper helper) {
        var fixture = fixture(helper);
        var level = helper.getLevel();
        var villager = fixture.villager();
        var pos = villager.blockPosition().east();
        var original = level.getBlockState(pos);
        long dayTime = level.getDayTime();
        try {
            level.setDayTime(6_000L);
            var storage = new SettlementArea(UUID.randomUUID(), SettlementAreaType.DEPOT, "Harvest",
                    pos.below(), pos.above(), 5, true);
            helper.assertTrue(SettlementBuildingService.register(fixture.leader(), fixture.town().id(), storage),
                    "the farmer has a designated output depot");
            level.setBlockAndUpdate(pos, com.jvn.villagerretaliation.block.VillagerRetaliationBlocks.SETTLEMENT_DEPOT.get().defaultBlockState());
            helper.assertTrue(SettlementDepotService.register(fixture.leader(), pos), "the physical depot registers");
            assign(helper, fixture);
            var session = HiredWorkSession.active(level, villager);
            session.inventory().setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.IRON_HOE));
            session.inventory().insertOutput(new ItemStack(Items.WHEAT, 8));
            session.state().putLong("NextFarmingFieldScanGameTime", level.getGameTime() + 1_000L);
            com.jvn.villagerretaliation.interaction.work.HiredWorkerBrain.setStorageTarget(session.context(), pos);
            com.jvn.villagerretaliation.interaction.work.HiredWorkerBrain.setState(session.context(),
                    com.jvn.villagerretaliation.interaction.work.HiredWorkerTaskState.MOVING_TO_STORAGE, null);
            new com.jvn.villagerretaliation.interaction.work.FarmingWorker().tick(level, villager, null, session.context());
            var depot = ((com.jvn.villagerretaliation.block.SettlementDepotBlockEntity) level.getBlockEntity(pos)).inventory();
            helper.assertValueEqual(depot.count(stack -> stack.is(Items.WHEAT)), 8,
                    "a pending delivery survives the hoe check and the field scan cooldown");
            helper.assertFalse(session.inventory().hasOutput(stack -> stack.is(Items.WHEAT)),
                    "the deposit moves the original harvest without duplication");
            helper.assertValueEqual(com.jvn.villagerretaliation.interaction.work.HiredWorkerBrain.snapshot(session.state(), level.getGameTime()).taskState(),
                    com.jvn.villagerretaliation.interaction.work.HiredWorkerTaskState.RETURNING_TO_WORK_AREA,
                    "the farmer returns to its field after completing delivery");
        } finally {
            villager.discard();
            level.setBlockAndUpdate(pos, original);
            level.setDayTime(dayTime);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void civicFarmerDeliversFullStacksDuringOngoingHarvest(GameTestHelper helper) {
        var fixture = fixture(helper);
        var level = helper.getLevel();
        var villager = fixture.villager();
        var pos = villager.blockPosition().east();
        var original = level.getBlockState(pos);
        long dayTime = level.getDayTime();
        try {
            level.setDayTime(6_000L);
            var storage = new SettlementArea(UUID.randomUUID(), SettlementAreaType.DEPOT, "Harvest",
                    pos.below(), pos.above(), 5, true);
            helper.assertTrue(SettlementBuildingService.register(fixture.leader(), fixture.town().id(), storage),
                    "the farmer has a designated output depot");
            level.setBlockAndUpdate(pos, com.jvn.villagerretaliation.block.VillagerRetaliationBlocks.SETTLEMENT_DEPOT.get().defaultBlockState());
            helper.assertTrue(SettlementDepotService.register(fixture.leader(), pos), "the physical depot registers");
            assign(helper, fixture);
            var session = HiredWorkSession.active(level, villager);
            session.inventory().setItem(HiredJobInventory.MAINHAND_SLOT, new ItemStack(Items.IRON_HOE));
            session.inventory().insertOutput(new ItemStack(Items.WHEAT, 8));
            session.state().putLong("NextFarmingFieldScanGameTime", level.getGameTime() + 1_000L);
            var farmer = new com.jvn.villagerretaliation.interaction.work.FarmingWorker();
            farmer.tick(level, villager, null, session.context());
            helper.assertValueEqual(session.inventory().countItem(Items.WHEAT), 8,
                    "a partial harvest does not cause a separate trip for every crop");
            session.inventory().insertOutput(new ItemStack(Items.WHEAT, 56));
            helper.assertTrue(session.inventory().hasOutputSpace(), "the harvest batch is delivered before inventory capacity is exhausted");
            farmer.tick(level, villager, null, session.context());
            var depot = ((com.jvn.villagerretaliation.block.SettlementDepotBlockEntity) level.getBlockEntity(pos)).inventory();
            helper.assertValueEqual(depot.count(stack -> stack.is(Items.WHEAT)), 64,
                    "a full harvest stack reaches the depot while the field remains on cooldown");
            helper.assertValueEqual(session.inventory().countItem(Items.WHEAT), 0,
                    "the delivery conserves the original harvest");
        } finally {
            villager.discard();
            level.setBlockAndUpdate(pos, original);
            level.setDayTime(dayTime);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void civicCookCollectsIngredientsOutsideKitchenBounds(GameTestHelper helper) {
        var fixture = fixture(helper);
        var level = helper.getLevel();
        var villager = fixture.villager();
        var center = villager.blockPosition();
        var table = center.west();
        var storagePos = center.east(5);
        var oldTable = level.getBlockState(table);
        var oldStorage = level.getBlockState(storagePos);
        long dayTime = level.getDayTime();
        try {
            level.setDayTime(6_000L);
            level.setBlockAndUpdate(table, Blocks.CRAFTING_TABLE.defaultBlockState());
            var kitchen = new SettlementArea(UUID.randomUUID(), SettlementAreaType.KITCHEN, "Kitchen",
                    center.offset(-2, -1, -2), center.offset(2, 2, 2), 5, true);
            helper.assertTrue(SettlementBuildingService.register(fixture.leader(), fixture.town().id(), kitchen), "the kitchen registers");
            var storage = new SettlementArea(UUID.randomUUID(), SettlementAreaType.DEPOT, "Ingredients",
                    storagePos.below(), storagePos.above(), 5, true);
            helper.assertTrue(SettlementBuildingService.register(fixture.leader(), fixture.town().id(), storage), "the separate ingredient depot registers");
            level.setBlockAndUpdate(storagePos, com.jvn.villagerretaliation.block.VillagerRetaliationBlocks.SETTLEMENT_DEPOT.get().defaultBlockState());
            fixture.leader().setPos(storagePos.north().getBottomCenter());
            helper.assertTrue(SettlementDepotService.register(fixture.leader(), storagePos), "the physical ingredient depot registers");
            var depot = ((com.jvn.villagerretaliation.block.SettlementDepotBlockEntity) level.getBlockEntity(storagePos)).inventory();
            depot.setItem(0, new ItemStack(Items.WHEAT, 12));
            helper.assertTrue(SettlementWorkforceService.assign(level, fixture.leader(), fixture.town().id(), villager, kitchen.id()),
                    "the resident receives civic cooking work");
            var inventory = HiredJobInventory.getJobInventory(villager);
            var filter = new ItemStack(com.jvn.villagerretaliation.item.VillagerRetaliationItems.ITEM_FILTER.get());
            com.jvn.villagerretaliation.item.VillagerItemFilterData.setEntry(filter, 0, new ItemStack(Items.BREAD));
            inventory.setItem(HiredJobInventory.FILTER_SLOT, filter);
            villager.setPos(storagePos.north().getBottomCenter());
            var session = HiredWorkSession.active(level, villager);
            new com.jvn.villagerretaliation.interaction.work.CookingWorker().tick(level, villager, null, session.context());
            helper.assertValueEqual(depot.count(stack -> stack.is(Items.WHEAT)), 9,
                    "a cook outside its kitchen can collect one real bread recipe");
            helper.assertValueEqual(inventory.countItem(Items.WHEAT), 3, "the three ingredients enter the worker inventory");
            helper.assertValueEqual(com.jvn.villagerretaliation.interaction.work.HiredWorkerBrain.snapshot(session.state(), level.getGameTime()).taskState(),
                    com.jvn.villagerretaliation.interaction.work.HiredWorkerTaskState.RETURNING_TO_WORK_AREA,
                    "the cook returns to the registered kitchen before crafting");
            helper.assertTrue(level.getBlockState(table).is(Blocks.CRAFTING_TABLE), "the kitchen fixture remains intact");
        } finally {
            villager.discard();
            level.setBlockAndUpdate(table, oldTable);
            level.setBlockAndUpdate(storagePos, oldStorage);
            level.setDayTime(dayTime);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void leaderPaidHireReleasesCivicDutyAndChargesExactlyOnce(GameTestHelper helper) {
        var fixture = fixture(helper);
        assign(helper, fixture);
        var level = helper.getLevel();
        var villager = fixture.villager();
        try {
            int cost = HiredVillagerContractService.getHireCost(level, villager, fixture.leader(), 1, HiredVillagerRole.FARMING);
            com.jvn.villagerretaliation.interaction.VillagerCurrencyPayment.give(fixture.leader(), cost + 5);
            HiredJobInventory.getJobInventory(villager).setItem(HiredJobInventory.HOTBAR_START, new ItemStack(Items.WHEAT_SEEDS, 7));
            helper.assertTrue(RecruitmentPolicy.mayAcceptPaidContract(level, villager, fixture.leader()).allowed(),
                    "the leader can accept a paid civic transfer");
            helper.assertFalse(RecruitmentPolicy.mayHire(level, villager, fixture.leader()).allowed(),
                    "general recruitment still cannot displace civic duty");
            long revision = VillagerAssignmentStore.snapshot(villager).revision();
            paidRequest(helper, fixture, fixture.leader(), HiredVillagerRole.FARMING, revision);
            helper.assertTrue(HiredVillagerContractService.isHiredBy(level, villager, fixture.leader()), "the paid contract starts");
            helper.assertFalse(fixture.town().duties().containsKey(villager.getUUID()), "acceptance releases civic duty");
            helper.assertValueEqual(VillagerAssignmentStore.snapshot(villager).state(), VillagerAssignmentState.HIRED,
                    "the entity and town agree on the transfer");
            helper.assertValueEqual(HiredJobInventory.getJobInventory(villager).countItem(Items.WHEAT_SEEDS), 7,
                    "the transfer preserves job supplies");
            helper.assertValueEqual(com.jvn.villagerretaliation.interaction.VillagerCurrencyPayment.count(fixture.leader()), 5,
                    "the normal price is charged once");
            com.jvn.villagerretaliation.interaction.VillagerInteractionService.handleRecruitRequest(fixture.leader(),
                    villager.getId(), com.jvn.villagerretaliation.network.VillagerRecruitRequestPayload.Action.HIRE_ONE_DAY,
                    HiredVillagerRole.FARMING, revision);
            helper.assertValueEqual(com.jvn.villagerretaliation.interaction.VillagerCurrencyPayment.count(fixture.leader()), 5,
                    "replaying the stale acceptance cannot charge again");
            helper.assertFalse(SettlementWorkforceService.assign(level, fixture.leader(), fixture.town().id(), villager, fixture.area().id()),
                    "the paid contract cannot transfer back into civic duty");
        } finally {
            com.jvn.villagerretaliation.interaction.VillagerConversationService.endForPlayer(fixture.leader(), false);
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void civicHireRejectsNonleaderDespiteStaffPermission(GameTestHelper helper) {
        var fixture = fixture(helper);
        assign(helper, fixture);
        var outsider = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "HiringStaff"));
        outsider.setPos(fixture.leader().position());
        fixture.town().setPermissions(fixture.leader().getUUID(), outsider.getUUID(), Set.of(SettlementPermission.STAFF));
        outsider.getInventory().setItem(0, new ItemStack(Items.EMERALD, 64));
        try {
            paidRequest(helper, fixture, outsider, HiredVillagerRole.FARMING, VillagerAssignmentStore.snapshot(fixture.villager()).revision());
            assertCivic(helper, fixture);
            helper.assertValueEqual(com.jvn.villagerretaliation.interaction.VillagerCurrencyPayment.count(outsider), 64,
                    "rejected staff hiring takes no payment");
        } finally {
            com.jvn.villagerretaliation.interaction.VillagerConversationService.endForPlayer(outsider, false);
            fixture.villager().discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void civicHirePreservesDutyWhenPaymentIsInsufficient(GameTestHelper helper) {
        var fixture = fixture(helper);
        assign(helper, fixture);
        try {
            fixture.leader().getInventory().clearContent();
            paidRequest(helper, fixture, fixture.leader(), HiredVillagerRole.FARMING,
                    VillagerAssignmentStore.snapshot(fixture.villager()).revision());
            assertCivic(helper, fixture);
            helper.assertValueEqual(com.jvn.villagerretaliation.interaction.VillagerCurrencyPayment.count(fixture.leader()), 0,
                    "failed payment does not create currency");
        } finally {
            com.jvn.villagerretaliation.interaction.VillagerConversationService.endForPlayer(fixture.leader(), false);
            fixture.villager().discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void civicHireRejectsStaleAssignmentAndUnsuitableRole(GameTestHelper helper) {
        var fixture = fixture(helper);
        assign(helper, fixture);
        fixture.leader().getInventory().setItem(0, new ItemStack(Items.EMERALD, 64));
        try {
            long revision = VillagerAssignmentStore.snapshot(fixture.villager()).revision();
            paidRequest(helper, fixture, fixture.leader(), HiredVillagerRole.FARMING, revision - 1);
            assertCivic(helper, fixture);
            paidRequest(helper, fixture, fixture.leader(), HiredVillagerRole.BUILDER, revision);
            assertCivic(helper, fixture);
            helper.assertValueEqual(com.jvn.villagerretaliation.interaction.VillagerCurrencyPayment.count(fixture.leader()), 64,
                    "stale requests and unsuitable contract roles take no payment");
        } finally {
            com.jvn.villagerretaliation.interaction.VillagerConversationService.endForPlayer(fixture.leader(), false);
            fixture.villager().discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void civicHireRejectsMismatchedTownAssignment(GameTestHelper helper) {
        var fixture = fixture(helper);
        assign(helper, fixture);
        fixture.leader().getInventory().setItem(0, new ItemStack(Items.EMERALD, 64));
        try {
            VillagerAssignmentStore.assignCivic(fixture.villager(), fixture.town().id().value(), UUID.randomUUID(),
                    HiredVillagerRole.FARMING, helper.getLevel().getGameTime());
            helper.assertFalse(HiredVillagerContractService.startPaidHireContract(helper.getLevel(), fixture.villager(),
                    fixture.leader(), 1, 5, HiredVillagerRole.FARMING), "mismatched entity and town assignments reject transfer");
            helper.assertValueEqual(fixture.town().duties().get(fixture.villager().getUUID()), fixture.area().id(),
                    "failed validation preserves the town duty");
            helper.assertFalse(HiredVillagerContractService.hasContract(fixture.villager()), "failed validation creates no contract");
        } finally {
            fixture.villager().discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void civicLeadershipDoesNotBypassHiringReputation(GameTestHelper helper) {
        var fixture = fixture(helper);
        assign(helper, fixture);
        fixture.leader().getInventory().setItem(0, new ItemStack(Items.EMERALD, 64));
        try {
            com.jvn.villagerretaliation.reputation.VillagerReputationManager.setReputation(
                    helper.getLevel(), fixture.villager(), fixture.leader().getUUID(), -100);
            helper.assertValueEqual(RecruitmentPolicy.mayAcceptPaidContract(helper.getLevel(), fixture.villager(), fixture.leader()).reason(),
                    RecruitmentPolicy.DenialReason.INSUFFICIENT_REPUTATION, "leadership retains the normal trust requirement");
            com.jvn.villagerretaliation.interaction.HiredContractRequestHandler.handle(fixture.leader(), helper.getLevel(),
                    fixture.villager(), com.jvn.villagerretaliation.network.VillagerRecruitRequestPayload.Action.HIRE_ONE_DAY,
                    HiredVillagerRole.FARMING);
            assertCivic(helper, fixture);
            helper.assertValueEqual(com.jvn.villagerretaliation.interaction.VillagerCurrencyPayment.count(fixture.leader()), 64,
                    "insufficient trust takes no payment");
        } finally {
            fixture.villager().discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void personalHireNeedsNoSettlementAuthority(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Villager villager = EntityType.VILLAGER.create(level);
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.FARMER));
        ServerPlayer hirer = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "PersonalHirer"));
        try {
            helper.assertTrue(HiredVillagerContractService.startHireContract(level, villager, hirer,
                    2, 8, HiredVillagerRole.FARMING), "personal hiring needs no village or leadership");
            var authority = VillagerWorkAuthority.resolve(level, villager);
            helper.assertTrue(authority.active() && !authority.civic(), "the contract owns the work authority");
            helper.assertValueEqual(authority.owner().orElseThrow(), hirer.getUUID(), "ownership belongs to the hirer");
            helper.assertValueEqual(authority.role(), HiredVillagerRole.FARMING, "the chosen personal role is active");
            helper.assertTrue(SettlementWorkforceService.duty(villager).isEmpty(), "hiring creates no civic duty");
            helper.assertFalse(authority.canWork(level, villager), "a hirer outside the online player list pauses work");
        } finally {
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void authorityQueriesPreserveExpiredPersonalCommitmentUntilMaintenance(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        assign(helper, fixture);
        var villager = fixture.villager();
        var paid = HireContract.regular(UUID.randomUUID(), 0L, 1, 4, 4, HiredVillagerRole.COURIER);
        paid.endGameTime(helper.getLevel().getGameTime() - 1L);
        paid.autoPayment(true);
        HireContractStore.save(villager, paid);
        CompoundTag before = villager.getPersistentData().getCompound("VillagerRetaliationHireContract").copy();
        try {
            var authority = VillagerWorkAuthority.resolve(helper.getLevel(), villager);
            helper.assertTrue(authority.active() && !authority.civic(), "a pending personal commitment takes precedence");
            helper.assertValueEqual(VillagerWorkAuthority.activeRole(helper.getLevel(), villager), HiredVillagerRole.COURIER,
                    "shared role lookup resolves personal precedence");
            helper.assertValueEqual(HiredVillagerContractService.activeRoleWithoutMaintenance(helper.getLevel(), villager),
                    HiredVillagerRole.COURIER, "personal lookup resolves the contract alone");
            HiredWorkDecisionScheduler.isDecisionOpportunity(helper.getLevel(), villager);
            helper.assertValueEqual(villager.getPersistentData().getCompound("VillagerRetaliationHireContract"), before,
                    "read-only resolution never expires, renews, charges or refunds the contract");
            helper.assertValueEqual(fixture.town().duties().get(villager.getUUID()), fixture.area().id(),
                    "read-only resolution leaves assignment reconciliation to lifecycle maintenance");
        } finally {
            villager.discard();
        }
        helper.succeed();
    }

    private static void paidRequest(GameTestHelper helper, Fixture fixture, ServerPlayer player, HiredVillagerRole role, long revision) {
        helper.assertTrue(com.jvn.villagerretaliation.interaction.VillagerConversationService.start(player, fixture.villager()),
                "the existing villager conversation is available");
        com.jvn.villagerretaliation.interaction.VillagerInteractionService.handleRecruitRequest(player, fixture.villager().getId(),
                com.jvn.villagerretaliation.network.VillagerRecruitRequestPayload.Action.HIRE_ONE_DAY, role, revision);
    }

    private static void assertCivic(GameTestHelper helper, Fixture fixture) {
        helper.assertValueEqual(fixture.town().duties().get(fixture.villager().getUUID()), fixture.area().id(),
                "the town duty survives rejected hiring");
        helper.assertValueEqual(VillagerAssignmentStore.snapshot(fixture.villager()).state(), VillagerAssignmentState.CIVIC,
                "the entity remains on civic duty");
        helper.assertFalse(HiredVillagerContractService.hasContract(fixture.villager()), "rejected hiring creates no contract");
    }

    private static void assign(GameTestHelper helper, Fixture fixture) {
        helper.assertTrue(SettlementWorkforceService.assign(helper.getLevel(), fixture.leader(), fixture.town().id(),
                fixture.villager(), fixture.area().id()), "leadership can assign an eligible resident");
    }

    private static Fixture fixture(GameTestHelper helper) {
        return fixture(helper, UUID.randomUUID());
    }

    private static Fixture fixture(GameTestHelper helper, UUID residentId) {
        ServerLevel level = helper.getLevel();
        BlockPos center = helper.absolutePos(new BlockPos(1, 2, 1));
        level.getChunkAt(center);
        ServerPlayer leader = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "TownLeader"));
        leader.setPos(center.getX() + 0.5D, center.getY(), center.getZ() + 0.5D);
        Villager villager = EntityType.VILLAGER.create(level);
        villager.setUUID(residentId);
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.FARMER));
        villager.setPos(center.getX() + 0.5D, center.getY(), center.getZ() + 0.5D);
        villager.setNoAi(true);
        helper.assertTrue(level.addFreshEntity(villager), "the resident is added to the world");
        helper.assertTrue(level.getEntity(villager.getUUID()) == villager, "the resident is tracked in the test chunk");
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var id = registry.create(level.getGameTime(), level.dimension().location(), center, "");
        var data = SettlementSavedData.get(level);
        var town = data.establish(registry, id, leader.getUUID(), center, level.getGameTime()).orElseThrow();
        VillageAllegianceApi.assignKnown(level, villager, id, AllegianceAssignmentSource.EXPLICIT_API);
        var area = new SettlementArea(UUID.randomUUID(), SettlementAreaType.FARM, "Field",
                center.offset(-2, -1, -2), center.offset(3, 2, 3), 5, true);
        helper.assertTrue(data.putArea(registry, id, leader.getUUID(), area), "the test farm is registered");
        return new Fixture(town, area, villager, leader);
    }

    private record Fixture(SettlementRecord town, SettlementArea area, Villager villager, ServerPlayer leader) {
    }
}
