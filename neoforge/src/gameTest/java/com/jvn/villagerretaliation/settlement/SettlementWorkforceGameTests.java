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
