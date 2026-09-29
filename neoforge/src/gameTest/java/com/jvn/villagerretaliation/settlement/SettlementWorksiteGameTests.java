package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.AllegianceAssignmentSource;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceApi;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.HiredWorkStateStore;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.jvn.villagerretaliation.network.SettlementManagementActionPayload;
import com.jvn.villagerretaliation.network.SettlementManagementPayload;
import com.jvn.villagerretaliation.network.SettlementManagementRequestPayload;
import com.jvn.villagerretaliation.profile.VillagerProfileManager;
import com.jvn.villagerretaliation.skill.VillagerSkillGenerator;
import com.jvn.villagerretaliation.skill.VillagerSkillSet;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("villagerretaliation")
@PrefixGameTestTemplate(false)
public final class SettlementWorksiteGameTests {
    private SettlementWorksiteGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_worksite_settings")
    public static void worksiteAdoptionRetainsOverridesAndNewStaffInherit(GameTestHelper helper) {
        var fixture = fixture(helper, SettlementAreaType.FARM, true);
        try {
            var town = fixture.town();
            var resident = fixture.villager().getUUID();
            var legacy = snapshot(helper, fixture);
            var plan = new SettlementWorksitePlan(legacy.with(SettlementWorkerSetting.TILL_SOIL, 0), 3);
            helper.assertTrue(town.worksitePlans().isEmpty(), "legacy workplaces are not silently adopted");
            helper.assertTrue(SettlementWorksiteService.configure(fixture.leader(), town, fixture.area().id(), plan, true),
                    "the leader can explicitly adopt a worksite plan");
            helper.assertValueEqual(snapshot(helper, fixture), legacy, "adoption retains existing individual settings");
            helper.assertFalse(SettlementWorksiteService.configure(fixture.leader(), town, fixture.area().id(), plan, true),
                    "repeated adoption cannot overwrite worker overrides");
            helper.assertTrue(SettlementWorksiteService.reset(fixture.leader(), town, resident, fixture.area().id(),
                    1 << SettlementWorkerSetting.TILL_SOIL.ordinal()), "one setting can return to worksite control");
            helper.assertFalse(HiredWorkStateStore.state(fixture.villager()).getBoolean("FarmingTillSoil"),
                    "the active worker immediately inherits the plan");
            helper.assertTrue(snapshot(helper, fixture).overrides(SettlementWorkerSetting.AUTO_DEPOSIT),
                    "resetting tilling keeps unrelated overrides");
            var newcomer = UUID.randomUUID();
            helper.assertTrue(town.assignDuty(newcomer, fixture.area().id()), "new staff join the same worksite");
            helper.assertValueEqual(town.effectiveWorkerSettings(newcomer).overrides(), 0, "new staff inherit every setting");
            helper.assertValueEqual(town.effectiveWorkerSettings(newcomer).options().get(SettlementWorkerSetting.TILL_SOIL), 0,
                    "new staff receive the worksite value");
            var changed = new SettlementWorksitePlan(plan.settings().with(SettlementWorkerSetting.TILL_SOIL, 1), 2);
            helper.assertTrue(SettlementWorksiteService.configure(fixture.leader(), town, fixture.area().id(), changed, false),
                    "an adopted plan can be updated");
            helper.assertTrue(HiredWorkStateStore.state(fixture.villager()).getBoolean("FarmingTillSoil"),
                    "inherited values follow later plan changes");
            var restored = SettlementRecord.load(town.save(helper.getLevel().registryAccess()), helper.getLevel().registryAccess(), () -> {});
            helper.assertValueEqual(restored.worksitePlans().get(fixture.area().id()), changed, "plans survive reload");
            helper.assertValueEqual(restored.effectiveWorkerSettings(resident), town.effectiveWorkerSettings(resident),
                    "reload preserves per-setting inheritance");
            restored.removeDuty(resident);
            helper.assertFalse(restored.workerSettings().containsKey(resident), "release clears overrides");
        } finally {
            fixture.villager().discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_worksite_settings")
    public static void worksiteEditsRespectPermissionsAndBackgroundReceipts(GameTestHelper helper) {
        var fixture = fixture(helper, SettlementAreaType.FARM, false);
        var town = fixture.town();
        var resident = fixture.villager().getUUID();
        var worker = town.workers().get(resident);
        var items = worker.items(helper.getLevel().registryAccess());
        items.set(HiredJobInventory.MAIN_GRID_START, new ItemStack(Items.WHEAT, 11));
        worker.updateInventory(helper.getLevel().registryAccess(), items);
        worker.changed();
        long version = worker.version();
        var delegate = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "WorksiteDelegate"));
        var plan = SettlementWorksitePlan.defaults(HiredVillagerRole.FARMING);
        helper.assertFalse(SettlementWorksiteService.configure(delegate, town, fixture.area().id(), plan, true),
                "unprivileged players cannot adopt plans");
        town.setPermissions(fixture.leader().getUUID(), delegate.getUUID(), Set.of(SettlementPermission.STAFF));
        helper.assertTrue(SettlementWorksiteService.configure(delegate, town, fixture.area().id(), plan, true),
                "staff delegates can adopt plans");
        helper.assertTrue(SettlementWorksiteService.reset(delegate, town, resident, fixture.area().id(),
                SettlementWorkerSettings.allOverrides(HiredVillagerRole.FARMING)), "all overrides can reset");
        var disabled = new SettlementWorksitePlan(plan.settings().with(SettlementWorkerSetting.ENABLED, 0), 0);
        helper.assertTrue(SettlementWorksiteService.configure(delegate, town, fixture.area().id(), disabled, false),
                "background workers receive plan changes");
        helper.assertFalse(worker.settings().getBoolean("Enabled"), "background workers see inherited pause immediately");
        helper.assertValueEqual(worker.version(), version, "plan changes preserve inventory receipts");
        SettlementBackgroundService.onActive(fixture.villager());
        helper.assertValueEqual(HiredJobInventory.getJobInventory(fixture.villager()).getItem(HiredJobInventory.MAIN_GRID_START).getCount(),
                11, "returning entities receive the saved inventory exactly once");
        helper.assertFalse(HiredWorkStateStore.state(fixture.villager()).getBoolean("Enabled"), "returning entities keep inheritance");
        town.setPermissions(fixture.leader().getUUID(), delegate.getUUID(), Set.of());
        helper.assertFalse(SettlementWorksiteService.fill(delegate, town, fixture.area().id()), "revoked staff cannot fill targets");
        helper.assertFalse(SettlementWorksiteService.configure(delegate, town, fixture.area().id(), plan, false),
                "revoked staff cannot edit plans");
        helper.assertFalse(helper.getLevel().hasChunkAt(town.center()), "worksite controls do not load remote chunks");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_worksite_settings")
    public static void worksitePayloadsAndDamagedPlansAreValidated(GameTestHelper helper) {
        var fixture = fixture(helper, SettlementAreaType.FARM, false);
        var plan = SettlementWorksitePlan.defaults(HiredVillagerRole.FARMING);
        var action = new SettlementManagementActionPayload(fixture.town().id().value(),
                new SettlementManagementActionPayload.SaveWorksite(fixture.area().id(), plan, true));
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            SettlementManagementActionPayload.STREAM_CODEC.encode(buffer, action);
            helper.assertValueEqual(SettlementManagementActionPayload.STREAM_CODEC.decode(buffer), action, "plans round trip through bounded actions");
            helper.assertTrue(SettlementManagementService.execute(fixture.leader(), action), "the validated action adopts the plan");
            var payload = SettlementManagementService.snapshot(fixture.leader(), new SettlementManagementRequestPayload(
                    fixture.town().id().value(), SettlementManagementRequestPayload.Page.AREAS, 0, "", 1, false));
            SettlementManagementPayload.STREAM_CODEC.encode(buffer, payload);
            var decoded = SettlementManagementPayload.STREAM_CODEC.decode(buffer);
            helper.assertValueEqual(((SettlementManagementPayload.Area) decoded.entries().getFirst()).plan(), plan,
                    "the world view receives the worksite plan");
            helper.assertValueEqual(buffer.readableBytes(), 0, "the entire payload is read");
        } finally {
            buffer.release();
        }
        var saved = fixture.town().save(helper.getLevel().registryAccess());
        saved.getList("WorksitePlans", net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0).putInt("DesiredStaff", 33);
        boolean rejected = false;
        try {
            SettlementRecord.load(saved, helper.getLevel().registryAccess(), () -> {});
        } catch (IllegalArgumentException exception) {
            rejected = true;
        }
        helper.assertTrue(rejected, "damaged staffing limits reach save recovery");
        var filters = SettlementWorkerSettings.capture(HiredVillagerRole.LOGGING, new CompoundTag())
                .withFilters(Set.of(ResourceLocation.withDefaultNamespace("oak_log")), Set.of());
        helper.assertValueEqual(filters.withOverrides(0).resolve(filters).logs(), filters.logs(), "log filters inherit exactly");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "settlement_worksite_staffing")
    public static void worksiteTargetsFillOnlyEligibleIdleResidents(GameTestHelper helper) {
        var town = fixture(helper, 5);
        var farm = town.areas().values().stream().filter(area -> area.type() == SettlementAreaType.FARM).findFirst().orElseThrow();
        var leader = net.neoforged.neoforge.common.util.FakePlayerFactory.get(helper.getLevel(),
                new com.mojang.authlib.GameProfile(town.leader(), "StaffingLeader"));
        var residents = List.copyOf(town.residents().keySet());
        town.rememberManualDuty(residents.getFirst());
        var saved = town.residents().get(residents.get(1)).save();
        saved.putBoolean("PersonalCommitment", true);
        town.recordResident(SettlementResidentState.load(saved));
        var defaults = SettlementWorksitePlan.defaults(farm.type().role());
        helper.assertTrue(SettlementWorksiteService.configure(leader, town, farm.id(),
                new SettlementWorksitePlan(defaults.settings(), 3), true), "a worksite can request multiple workers");
        town.setManualManagement(town.leader(), true);
        SettlementAutonomyService.update(helper.getLevel(), town);
        helper.assertTrue(town.duties().isEmpty(), "manual management waits for an explicit fill action");
        helper.assertTrue(SettlementWorksiteService.fill(leader, town, farm.id()), "the leader can fill the selected worksite");
        helper.assertValueEqual(town.duties().size(), 3, "staffing stops at the target");
        helper.assertTrue(town.duties().values().stream().allMatch(farm.id()::equals), "fill does not staff other workplaces");
        helper.assertFalse(town.duties().containsKey(residents.getFirst()), "manual idle choices remain untouched");
        helper.assertFalse(town.duties().containsKey(residents.get(1)), "personal commitments cannot be purchased or reassigned by fill");
        helper.assertTrue(town.duties().keySet().stream().allMatch(id -> town.effectiveWorkerSettings(id).overrides() == 0),
                "automatically filled staff inherit the plan");
        var before = java.util.Map.copyOf(town.duties());
        helper.assertTrue(SettlementWorksiteService.configure(leader, town, farm.id(),
                new SettlementWorksitePlan(defaults.settings(), 1), false), "the target can be reduced");
        SettlementWorksiteService.fill(leader, town, farm.id());
        helper.assertValueEqual(town.duties(), before, "lowering the target never releases assigned workers");
        helper.assertFalse(helper.getLevel().hasChunkAt(town.center()), "staffing does not load remote chunks");
        helper.succeed();
    }

    private static SettlementWorkerSettings snapshot(GameTestHelper helper, Fixture fixture) {
        return SettlementWorkerConfigurationService.snapshot(helper.getLevel(), fixture.town(), fixture.villager().getUUID());
    }

    private static Fixture fixture(GameTestHelper helper, SettlementAreaType type, boolean loaded) {
        var level = helper.getLevel();
        var origin = helper.absolutePos(new BlockPos(1, 2, 1));
        var center = loaded ? origin : new BlockPos(11_000_000 + origin.getX() * 8 + type.ordinal() * 256, 64, 11_000_000 + origin.getZ() * 8);
        var leader = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "WorkerLeader"));
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var id = registry.create(level.getGameTime(), level.dimension().location(), center, "");
        var town = SettlementSavedData.get(level).establish(registry, id, leader.getUUID(), center, level.getGameTime())
                .orElseThrow(() -> new IllegalStateException("Worksite fixture territory is unavailable at " + center));
        if (type == SettlementAreaType.CONSTRUCTION) {
            town.advance();
        }
        var area = new SettlementArea(UUID.randomUUID(), type, "Workplace", center, center.offset(3, 2, 3), 5, true);
        helper.assertTrue(town.putArea(leader.getUUID(), area), "the workplace is registered");
        Villager villager = EntityType.VILLAGER.create(level);
        villager.setPos(center.getCenter());
        villager.setNoAi(true);
        villager.setCustomName(net.minecraft.network.chat.Component.literal("River"));
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.FARMER));
        VillageAllegianceApi.assignKnown(level, villager, id, AllegianceAssignmentSource.EXPLICIT_API);
        if (loaded) {
            helper.assertTrue(level.addFreshEntity(villager), "the test resident is loaded");
        }
        town.assignDuty(villager.getUUID(), area.id());
        SettlementWorkforceService.reconcile(level, villager);
        town.recordWorker(SettlementWorkerState.capture(level, villager, SettlementWorkforceService.duty(villager).orElseThrow(), 0L));
        SettlementCareService.onUnloaded(villager);
        return new Fixture(town, area, leader, villager);
    }

    private record Fixture(SettlementRecord town, SettlementArea area, ServerPlayer leader, Villager villager) {
    }

    private static SettlementRecord fixture(GameTestHelper helper, int population) {
        var level = helper.getLevel();
        var origin = helper.absolutePos(BlockPos.ZERO);
        var center = new BlockPos(18000000 + origin.getX() * 16, 80, 18000000 + origin.getZ() * 16);
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var id = registry.create(level.getGameTime(), level.dimension().location(), center, "Autonomous settlement");
        var town = SettlementSavedData.get(level).establish(registry, id, UUID.randomUUID(), center, level.getGameTime()).orElseThrow();
        var home = new SettlementArea(UUID.randomUUID(), SettlementAreaType.HOME, "Housing", center.west(8), center.offset(-4, 2, 8), 5, true);
        var farm = new SettlementArea(UUID.randomUUID(), SettlementAreaType.FARM, "Farm", center, center.offset(3, 2, 3), 5, true);
        var stores = new SettlementArea(UUID.randomUUID(), SettlementAreaType.DEPOT, "Stores", center.east(8), center.offset(10, 2, 2), 9, true);
        for (var area : List.of(home, farm, stores)) town.putArea(town.leader(), area);
        var beds = new ArrayList<BlockPos>();
        for (int index = 0; index < 8; index++) beds.add(home.min().south(index));
        town.recordSurvey(home.id(), new SettlementBuildingSurvey.Survey(level.getGameTime(), beds, List.of(), true));
        var depot = town.registerDepot(town.leader(), stores.id(), stores.min(), SettlementDepotPurpose.GENERAL);
        depot.observeRules(List.of());
        depot.setItem(0, new ItemStack(Items.BREAD, 64));
        for (int index = 0; index < population; index++) {
            var resident = EntityType.VILLAGER.create(level);
            resident.setPos(center.east(index).getBottomCenter());
            resident.setVillagerData(resident.getVillagerData().setProfession(VillagerProfession.FARMER));
            VillageAllegianceApi.assignKnown(level, resident, id, AllegianceAssignmentSource.EXPLICIT_API);
            VillagerProfileManager.getOrCreateProfile(level, resident).replaceSkills(VillagerSkillSet.filled(30 + index * 10),
                    VillagerSkillGenerator.CURRENT_GENERATION_VERSION, level.getGameTime());
            town.recordResident(SettlementResidentState.capture(resident, town, level.getGameTime()));
        }
        return town;
    }
}
