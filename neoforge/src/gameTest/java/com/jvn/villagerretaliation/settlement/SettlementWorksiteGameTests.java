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

    @GameTest(template = "empty", batch = "settlement_worksite_links")
    public static void worksiteStorageLinksConstrainActiveAndBackgroundWorkers(GameTestHelper helper) {
        var fixture = fixture(helper, SettlementAreaType.FARM, true);
        try {
            var level = helper.getLevel();
            var town = fixture.town();
            var resident = fixture.villager().getUUID();
            var source = town.registerDepot(town.leader(), fixture.area().id(), fixture.area().min(), SettlementDepotPurpose.GENERAL);
            var output = town.registerDepot(town.leader(), fixture.area().id(), fixture.area().min().east(), SettlementDepotPurpose.GENERAL);
            helper.assertTrue(source != null && output != null, "the worksite has two communal depots");
            source.observeRules(List.of());
            output.observeRules(List.of());
            source.setItem(0, new ItemStack(Items.WHEAT, 9));
            output.setItem(0, new ItemStack(Items.CARROT, 12));
            var links = new SettlementWorksiteLinks(Set.of(source.id()), Set.of(output.id()), Set.of());
            var defaults = SettlementWorksitePlan.defaults(HiredVillagerRole.FARMING);
            var plan = new SettlementWorksitePlan(defaults.settings(), 1, links);
            var delegate = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "StorageDelegate"));
            town.setPermissions(town.leader(), delegate.getUUID(), Set.of(SettlementPermission.STAFF));
            helper.assertFalse(SettlementWorksiteService.configure(delegate, town, fixture.area().id(), plan, true),
                    "staff permission alone cannot change communal storage access");
            town.setPermissions(town.leader(), delegate.getUUID(), Set.of(SettlementPermission.STAFF, SettlementPermission.LOGISTICS));
            helper.assertTrue(SettlementWorksiteService.configure(delegate, town, fixture.area().id(), plan, true),
                    "authorized delegates can link separate supply and output depots");
            var assigned = com.jvn.villagerretaliation.inventory.AssignedStorageSavedData.get(level).assignedTo(resident);
            helper.assertValueEqual(assigned.size(), 2, "the active worker receives only the selected purposes");
            helper.assertTrue(assigned.stream().allMatch(record -> record.purpose().equals("supply")
                            ? record.pos().equals(source.position()) : record.pos().equals(output.position())),
                    "active storage links preserve direction");
            var worker = town.workers().get(resident);
            var items = worker.items(level.registryAccess());
            var supplies = SettlementProductionInventory.supplies(level, worker, items, List.of(source, output));
            helper.assertTrue(supplies.stream().anyMatch(supply -> supply.depot() == source), "selected supply stock is available");
            helper.assertTrue(supplies.stream().noneMatch(supply -> supply.depot() == output), "output storage cannot supply ingredients");
            int slot = worker.outputSlots().getFirst();
            items.set(slot, new ItemStack(Items.POTATO, 7));
            worker.updateInventory(level.registryAccess(), items);
            helper.assertTrue(SettlementBackgroundHauling.depositCarried(level, town, worker, 1_000, 1.0D,
                    List.of(source, output), false), "background workers deposit their carried outputs");
            helper.assertValueEqual(source.count(stack -> stack.is(Items.POTATO)), 0, "supplies do not receive outputs");
            helper.assertValueEqual(output.count(stack -> stack.is(Items.POTATO)), 7, "selected outputs receive the whole delivery");
            var restored = SettlementRecord.load(town.save(level.registryAccess()), level.registryAccess(), () -> {});
            helper.assertValueEqual(restored.worksitePlans().get(fixture.area().id()).links(), links, "storage links survive reload");
            var foreign = new SettlementWorksitePlan(defaults.settings(), 1,
                    new SettlementWorksiteLinks(Set.of(UUID.randomUUID()), Set.of(output.id()), Set.of()));
            helper.assertFalse(SettlementWorksiteService.configure(fixture.leader(), town, fixture.area().id(), foreign, false),
                    "worksites cannot claim foreign depot identities");
            helper.assertTrue(SettlementWorkforceService.release(level, fixture.leader(), town.id(), resident), "the civic assignment can be released");
            helper.assertFalse(HiredWorkStateStore.state(fixture.villager()).contains(SettlementWorksiteLinks.STATE_TAG),
                    "communal links cannot leak into a later personal job");
        } finally {
            fixture.villager().discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "settlement_worksite_links")
    public static void worksiteStationsRespectObservationSelectionAndSaveVersions(GameTestHelper helper) {
        var fixture = fixture(helper, SettlementAreaType.WORKSHOP, false);
        var level = helper.getLevel();
        var town = fixture.town();
        var first = fixture.area().min();
        var second = first.east();
        var table = ResourceLocation.withDefaultNamespace("crafting_table");
        town.recordSurvey(fixture.area().id(), new SettlementBuildingSurvey.Survey(level.getGameTime(), List.of(),
                List.of(first, second), true, java.util.Map.of(first, table, second, table)));
        var defaults = SettlementWorksitePlan.defaults(HiredVillagerRole.CRAFTSMAN);
        var links = new SettlementWorksiteLinks(Set.of(), Set.of(), Set.of(first));
        var plan = new SettlementWorksitePlan(defaults.settings(), 1, links);
        helper.assertTrue(SettlementWorksiteService.configure(fixture.leader(), town, fixture.area().id(), plan, true),
                "an observed workstation can be selected");
        SettlementBackgroundService.onActive(fixture.villager());
        var context = com.jvn.villagerretaliation.interaction.HiredWorkSession.active(level, fixture.villager()).context();
        helper.assertTrue(context.permitsStation(first), "active work sessions admit the selected station");
        helper.assertFalse(context.permitsStation(second), "active work sessions reject the nearby unselected station");
        var unobserved = new SettlementWorksitePlan(defaults.settings(), 1,
                new SettlementWorksiteLinks(Set.of(), Set.of(), Set.of(first.south())));
        helper.assertFalse(SettlementWorksiteService.configure(fixture.leader(), town, fixture.area().id(), unobserved, false),
                "unobserved stations cannot be selected by coordinates alone");
        town.recordSurvey(fixture.area().id(), new SettlementBuildingSurvey.Survey(level.getGameTime(), List.of(),
                List.of(second), true, java.util.Map.of(second, table)));
        var worker = town.workers().get(fixture.villager().getUUID());
        SettlementBackgroundCrafting.tick(level, town, worker, 1_000, 1.0D);
        helper.assertValueEqual(worker.status(), "no_crafting_table", "a missing selected station never falls back to another table");
        var action = new SettlementManagementActionPayload(town.id().value(),
                new SettlementManagementActionPayload.SaveWorksite(fixture.area().id(), plan, false));
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess());
        try {
            SettlementManagementActionPayload.STREAM_CODEC.encode(buffer, action);
            helper.assertValueEqual(SettlementManagementActionPayload.STREAM_CODEC.decode(buffer), action, "station links retain exact positions over the network");
            var stations = SettlementManagementService.snapshot(fixture.leader(), new SettlementManagementRequestPayload(town.id().value(),
                    SettlementManagementRequestPayload.Page.STATIONS, 0, fixture.area().id().toString(), 1, false));
            SettlementManagementPayload.STREAM_CODEC.encode(buffer, stations);
            var decoded = SettlementManagementPayload.STREAM_CODEC.decode(buffer);
            helper.assertValueEqual(((SettlementManagementPayload.Station) decoded.entries().getFirst()).position(), second,
                    "station pickers receive the currently observed choices");
        } finally {
            buffer.release();
        }
        var legacy = plan.save();
        legacy.putInt("Version", 1);
        legacy.remove("Links");
        helper.assertValueEqual(SettlementWorksitePlan.load(legacy).links(), SettlementWorksiteLinks.AUTOMATIC,
                "older plans keep automatic storage and station discovery");
        var damaged = plan.save();
        damaged.getCompound("Links").putString("Stations", "invalid");
        boolean rejected = false;
        try {
            SettlementWorksitePlan.load(damaged);
        } catch (IllegalArgumentException exception) {
            rejected = true;
        }
        helper.assertTrue(rejected, "damaged station lists reach save recovery");
        helper.assertFalse(level.hasChunkAt(first), "worksite links never force remote chunks to load");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "settlement_worksite_crafting_access")
    public static void recursiveCraftingRespectsWorksiteStationSelection(GameTestHelper helper) {
        var fixture = fixture(helper, SettlementAreaType.WORKSHOP, true);
        try {
            var level = helper.getLevel();
            var selected = fixture.area().min();
            var other = selected.east();
            level.setBlockAndUpdate(other, net.minecraft.world.level.block.Blocks.CRAFTING_TABLE.defaultBlockState());
            fixture.town().recordSurvey(fixture.area().id(), new SettlementBuildingSurvey.Survey(level.getGameTime(), List.of(),
                    List.of(selected, other), true, java.util.Map.of(selected, ResourceLocation.withDefaultNamespace("crafting_table"),
                            other, ResourceLocation.withDefaultNamespace("crafting_table"))));
            var plan = new SettlementWorksitePlan(SettlementWorksitePlan.defaults(HiredVillagerRole.CRAFTSMAN).settings(), 1,
                    new SettlementWorksiteLinks(Set.of(), Set.of(), Set.of(selected)));
            helper.assertTrue(SettlementWorksiteService.configure(fixture.leader(), fixture.town(), fixture.area().id(), plan, true),
                    "the workshop adopts its selected table");
            var context = com.jvn.villagerretaliation.interaction.HiredWorkSession.active(level, fixture.villager()).context();
            helper.assertFalse(com.jvn.villagerretaliation.interaction.work.HiredSupplyCrafting.hasCraftingTable(level, context),
                    "recursive ingredient crafting cannot use a disallowed nearby table");
            level.setBlockAndUpdate(selected, net.minecraft.world.level.block.Blocks.CRAFTING_TABLE.defaultBlockState());
            helper.assertTrue(com.jvn.villagerretaliation.interaction.work.HiredSupplyCrafting.hasCraftingTable(level, context),
                    "recursive ingredient crafting can use the selected table when present");
        } finally {
            fixture.villager().discard();
        }
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
