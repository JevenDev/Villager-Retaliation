package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.AllegianceAssignmentSource;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceApi;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.interaction.HireContract;
import com.jvn.villagerretaliation.interaction.HireContractAssignmentAdapter;
import com.jvn.villagerretaliation.interaction.HireContractLifecycle;
import com.jvn.villagerretaliation.interaction.HireContractStore;
import com.jvn.villagerretaliation.interaction.HiredVillagerContractService;
import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.HiredVillagerRoles;
import com.jvn.villagerretaliation.interaction.HiredWorkStateStore;
import com.jvn.villagerretaliation.interaction.VillagerAssignmentCommand;
import com.jvn.villagerretaliation.interaction.VillagerAssignmentStore;
import com.jvn.villagerretaliation.interaction.VillagerWorkAppearance;
import com.jvn.villagerretaliation.interaction.VillagerWorkAppearanceHolder;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.behavior.ResetProfession;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("villagerretaliation")
@PrefixGameTestTemplate(false)
public final class VillagerWorkAppearanceGameTests {
    private VillagerWorkAppearanceGameTests() { }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "work_appearance")
    public static void personalAssignmentKeepsTradesAcrossPausesReloadAndExpiry(GameTestHelper helper) {
        var level = helper.getLevel();
        var villager = villager(helper);
        var owner = UUID.randomUUID();
        var identity = villager.getVillagerData();
        var trades = saved(villager).getCompound("Offers").copy();
        HireContractLifecycle.begin(level, villager, HireContract.regular(owner, level.getGameTime(), 2, 1, 2, HiredVillagerRole.FARMING));
        assertRole(helper, villager, HiredVillagerRole.FARMING, VillagerProfession.FARMER);
        HiredWorkStateStore.state(villager).putBoolean("Enabled", false);
        HiredWorkStateStore.state(villager).putString("Status", "interaction.work.status.missing_materials");
        VillagerAssignmentStore.setCommand(villager, owner, VillagerAssignmentCommand.FOLLOW, null);
        villager.setTarget(EntityType.ZOMBIE.create(level));
        var contract = HireContractStore.load(villager).orElseThrow();
        contract.status(HireContract.Status.AWAITING_AUTO_PAYMENT);
        HireContractAssignmentAdapter.synchronize(level, villager);
        VillagerWorkAppearance.refresh(villager);
        assertRole(helper, villager, HiredVillagerRole.FARMING, VillagerProfession.FARMER);
        helper.assertValueEqual(villager.getVillagerData(), identity, "assignment clothing leaves the real villager identity intact");
        helper.assertValueEqual(villager.getVillagerXp(), 27, "assignment clothing leaves trade experience intact");
        helper.assertValueEqual(saved(villager).getCompound("Offers"), trades, "pauses and combat do not change offers");
        var restored = EntityType.VILLAGER.create(level);
        restored.load(saved(villager));
        assertRole(helper, restored, HiredVillagerRole.FARMING, VillagerProfession.FARMER);
        helper.assertValueEqual(restored.getVillagerData().getProfession(), identity.getProfession(), "reload preserves the actual profession");
        helper.assertValueEqual(restored.getVillagerData().getType(), identity.getType(), "reload preserves the biome");
        helper.assertValueEqual(restored.getVillagerData().getLevel(), identity.getLevel(), "reload preserves the trading level");
        helper.assertValueEqual(saved(restored).getCompound("Offers"), trades, "reload preserves offers");
        var restoredContract = HireContractStore.load(restored).orElseThrow();
        restoredContract.status(HireContract.Status.ACTIVE);
        helper.assertTrue(HiredVillagerContractService.setActiveRole(level, restored, HiredVillagerRole.COURIER), "the contract accepts a different work assignment");
        assertRole(helper, restored, HiredVillagerRole.COURIER, VillagerProfession.CARTOGRAPHER);
        restoredContract.endGameTime(level.getGameTime() - 1);
        HiredVillagerContractService.synchronizeAssignment(level, restored);
        assertRole(helper, restored, null, VillagerProfession.NONE);
        helper.assertValueEqual(restored.getVillagerXp(), 27, "contract expiry does not alter existing trade experience");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "work_appearance")
    public static void everyWorkRoleUsesItsExistingOutfitIncludingOneOffBuilders(GameTestHelper helper) {
        var roles = HiredVillagerRole.values();
        var outfits = new VillagerProfession[] {
                VillagerProfession.ARMORER, VillagerProfession.LEATHERWORKER, VillagerProfession.TOOLSMITH,
                VillagerProfession.FLETCHER, VillagerProfession.FARMER, VillagerProfession.FISHERMAN,
                VillagerProfession.CLERIC, VillagerProfession.TOOLSMITH, VillagerProfession.MASON,
                VillagerProfession.SHEPHERD, VillagerProfession.LIBRARIAN, VillagerProfession.BUTCHER,
                VillagerProfession.ARMORER, VillagerProfession.CARTOGRAPHER
        };
        helper.assertValueEqual(roles.length, outfits.length, "every configured role has an appearance expectation");
        for (int index = 0; index < roles.length; index++) {
            var villager = villager(helper);
            var contract = roles[index] == HiredVillagerRole.BUILDER
                    ? HireContract.oneOffBuilder(UUID.randomUUID(), helper.getLevel().getGameTime())
                    : HireContract.regular(UUID.randomUUID(), helper.getLevel().getGameTime(), 1, 1, 1, roles[index]);
            HireContractLifecycle.begin(helper.getLevel(), villager, contract);
            assertRole(helper, villager, roles[index], outfits[index]);
            helper.assertValueEqual(villager.getVillagerData().getProfession(), VillagerProfession.TOOLSMITH,
                    "the role never replaces the true toolsmith profession");
            contract.status(HireContract.Status.ENDED);
            HireContractAssignmentAdapter.contractEnded(villager);
            assertRole(helper, villager, null, VillagerProfession.NONE);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "work_appearance")
    public static void noviceOneOffBuilderKeepsProfessionUntilJobEnds(GameTestHelper helper) {
        var level = helper.getLevel();
        var villager = villager(helper);
        villager.setVillagerData(villager.getVillagerData().setLevel(1));
        villager.setVillagerXp(0);
        villager.getBrain().eraseMemory(MemoryModuleType.JOB_SITE);
        var identity = villager.getVillagerData();
        var trades = saved(villager).getCompound("Offers").copy();
        HireContractLifecycle.begin(level, villager, HireContract.oneOffBuilder(UUID.randomUUID(), level.getGameTime()));
        helper.assertFalse(ResetProfession.create().tryStart(level, villager, level.getGameTime()),
                "a novice builder keeps its profession while waiting without a vanilla job site");
        helper.assertValueEqual(villager.getVillagerData(), identity, "building preserves the trading identity");
        helper.assertValueEqual(saved(villager).getCompound("Offers"), trades, "building preserves existing offers");
        helper.assertValueEqual(villager.getVillagerXp(), 0, "profession protection grants no trade experience");
        assertRole(helper, villager, HiredVillagerRole.BUILDER, VillagerProfession.MASON);
        var restored = EntityType.VILLAGER.create(level);
        restored.load(saved(villager));
        helper.assertFalse(ResetProfession.create().tryStart(level, restored, level.getGameTime()),
                "the saved one-off contract protects the profession after reload");
        assertRole(helper, restored, HiredVillagerRole.BUILDER, VillagerProfession.MASON);
        HiredVillagerContractService.finishOneOffBuilderJob(level, restored, "interaction.work.status.paused");
        assertRole(helper, restored, null, VillagerProfession.NONE);
        helper.assertTrue(ResetProfession.create().tryStart(level, restored, level.getGameTime()),
                "ordinary profession selection resumes when the one-off job ends");
        helper.assertValueEqual(restored.getVillagerData().getProfession(), VillagerProfession.NONE,
                "an unassigned novice can lose its profession normally");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "work_appearance")
    public static void returningCivicWorkerUsesTheSavedTownDutyBeforeTracking(GameTestHelper helper) {
        var level = helper.getLevel();
        var villager = villager(helper);
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.FARMER));
        var center = helper.absolutePos(new BlockPos(2, 2, 2));
        villager.setPos(center.getBottomCenter());
        level.getChunkAt(center);
        helper.assertTrue(level.addFreshEntity(villager), "the resident is loaded");
        var leader = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "ClothingLeader"));
        leader.setPos(center.getBottomCenter());
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var id = registry.create(level.getGameTime(), level.dimension().location(), center, "Clothing");
        var town = SettlementSavedData.get(level).establish(registry, id, leader.getUUID(), center, level.getGameTime()).orElseThrow();
        VillageAllegianceApi.assignKnown(level, villager, id, AllegianceAssignmentSource.EXPLICIT_API);
        var farm = new SettlementArea(UUID.randomUUID(), SettlementAreaType.FARM, "Farm", center, center.offset(3, 2, 3), 5, true);
        var forest = new SettlementArea(UUID.randomUUID(), SettlementAreaType.FORESTRY, "Forest", center.west(5), center.offset(-2, 2, 3), 5, true);
        helper.assertTrue(town.putArea(leader.getUUID(), farm) && town.putArea(leader.getUUID(), forest), "both worksites are registered");
        helper.assertTrue(SettlementWorkforceService.assign(level, leader, id, villager, farm.id()), "a farmer can take the farming duty");
        assertRole(helper, villager, HiredVillagerRole.FARMING, VillagerProfession.FARMER);
        var tag = saved(villager);
        villager.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        helper.assertTrue(town.assignDuty(villager.getUUID(), forest.id()), "the saved duty can change while its entity is absent");
        var savedTown = SettlementRecord.load(town.save(level.registryAccess()), level.registryAccess(), () -> {});
        helper.assertValueEqual(savedTown.duties().get(villager.getUUID()), forest.id(), "the authoritative duty survives settlement persistence");
        var returned = EntityType.VILLAGER.create(level);
        returned.load(tag);
        helper.assertTrue(level.addFreshEntity(returned), "the old entity snapshot returns");
        assertRole(helper, returned, HiredVillagerRole.LOGGING, VillagerProfession.FLETCHER);
        var tracked = EntityType.VILLAGER.create(level);
        tracked.getEntityData().assignValues(returned.getEntityData().getNonDefaultValues());
        assertRole(helper, tracked, HiredVillagerRole.LOGGING, VillagerProfession.FLETCHER);
        helper.assertTrue(SettlementWorkforceService.release(level, leader, id, returned.getUUID()), "the duty can be released");
        assertRole(helper, returned, null, VillagerProfession.NONE);
        returned.discard();
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "work_appearance")
    public static void unknownAssignmentsAndBabiesStayNeutralWithoutCreatingProfiles(GameTestHelper helper) {
        var villager = villager(helper);
        var before = villager.getPersistentData().copy();
        VillagerWorkAppearance.refresh(villager);
        assertRole(helper, villager, null, VillagerProfession.NONE);
        helper.assertValueEqual(villager.getPersistentData(), before, "unassigned appearance does not generate skills or an appearance save");
        var contract = HireContract.regular(UUID.randomUUID(), helper.getLevel().getGameTime(), 1, 1, 1, HiredVillagerRole.FARMING);
        contract.role(null);
        HireContractStore.save(villager, contract);
        before = villager.getPersistentData().copy();
        VillagerWorkAppearance.refresh(villager);
        assertRole(helper, villager, null, VillagerProfession.NONE);
        helper.assertValueEqual(villager.getPersistentData(), before, "unknown roles stay unknown without changing gameplay state");
        contract.role(HiredVillagerRole.FARMING);
        VillagerWorkAppearance.refresh(villager);
        assertRole(helper, villager, HiredVillagerRole.FARMING, VillagerProfession.FARMER);
        contract.status(HireContract.Status.ENDED);
        HireContractAssignmentAdapter.contractEnded(villager);
        assertRole(helper, villager, null, VillagerProfession.NONE);
        contract.status(HireContract.Status.ACTIVE);
        villager.setAge(-24_000);
        VillagerWorkAppearance.refresh(villager);
        assertRole(helper, villager, null, VillagerProfession.NONE);
        ((VillagerWorkAppearanceHolder) villager).villagerretaliation$assignedWorkRole(HiredVillagerRole.FARMING);
        helper.assertValueEqual(VillagerWorkAppearance.profession(villager), VillagerProfession.NONE, "babies ignore even a stale synchronized adult role");
        helper.assertFalse(EntityType.WANDERING_TRADER.create(helper.getLevel()) instanceof VillagerWorkAppearanceHolder,
                "the entity-data mixin leaves wandering traders unchanged");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "work_appearance")
    public static void farmingContractsRequireTheProfessionUsedByTheWorker(GameTestHelper helper) {
        var level = helper.getLevel();
        var owner = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "FarmingHirer"));
        for (var profession : new VillagerProfession[] {VillagerProfession.NONE, VillagerProfession.NITWIT,
                VillagerProfession.TOOLSMITH, VillagerProfession.FARMER}) {
            var villager = villager(helper);
            villager.setVillagerData(villager.getVillagerData().setProfession(profession));
            boolean farmer = profession == VillagerProfession.FARMER;
            helper.assertValueEqual(HiredVillagerRoles.availableContractRoles(level, villager).contains(HiredVillagerRole.FARMING),
                    farmer, "only native farmers are offered farming contracts");
            helper.assertValueEqual(HiredVillagerContractService.startHireContract(level, villager, owner, 1, 1, HiredVillagerRole.FARMING),
                    farmer, "contract creation enforces the farming profession requirement");
            if (farmer) {
                assertRole(helper, villager, HiredVillagerRole.FARMING, VillagerProfession.FARMER);
                HiredVillagerContractService.endHireContract(level, villager, owner);
            }
            helper.assertTrue(HiredVillagerContractService.startHireContract(level, villager, owner, 1, 1, HiredVillagerRole.MINING),
                    "other ordinary jobs remain available regardless of profession");
            helper.assertValueEqual(HiredVillagerContractService.setActiveRole(level, villager, HiredVillagerRole.FARMING),
                    farmer, "role changes enforce the same farming requirement");
            helper.assertValueEqual(villager.getVillagerData().getProfession(), profession, "work does not rewrite the real profession");
            HiredVillagerContractService.endHireContract(level, villager, owner);
            assertRole(helper, villager, null, VillagerProfession.NONE);
        }
        helper.succeed();
    }

    private static Villager villager(GameTestHelper helper) {
        var villager = EntityType.VILLAGER.create(helper.getLevel());
        villager.setNoAi(true);
        villager.setVillagerData(villager.getVillagerData().setType(VillagerType.SNOW).setProfession(VillagerProfession.TOOLSMITH).setLevel(3));
        villager.setVillagerXp(27);
        var offers = new MerchantOffers();
        offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 4), new ItemStack(Items.IRON_AXE), 8, 2, 0.05F));
        villager.setOffers(offers);
        return villager;
    }

    private static CompoundTag saved(Villager villager) {
        return villager.saveWithoutId(new CompoundTag());
    }

    private static void assertRole(GameTestHelper helper, Villager villager, HiredVillagerRole role, VillagerProfession outfit) {
        helper.assertTrue(((VillagerWorkAppearanceHolder) villager).villagerretaliation$assignedWorkRole() == role, "the synchronized work role matches the assignment");
        helper.assertValueEqual(VillagerWorkAppearance.profession(villager), outfit, "the appearance uses the assigned work outfit");
    }
}
