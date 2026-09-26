package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.AllegianceAssignmentSource;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceApi;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.HiredWorkStateStore;
import com.jvn.villagerretaliation.interaction.work.HiredAnimalBreedingTargets;
import com.jvn.villagerretaliation.interaction.work.HiredAnimalCullSettings;
import com.jvn.villagerretaliation.interaction.work.logging.HiredLoggingFilters;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.jvn.villagerretaliation.network.SettlementManagementActionPayload;
import com.jvn.villagerretaliation.network.SettlementManagementPayload;
import com.jvn.villagerretaliation.network.SettlementManagementRequestPayload;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
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

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class SettlementWorkerConfigurationGameTests {
    private SettlementWorkerConfigurationGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_worker_settings")
    public static void settingsRequireStaffAndTheCurrentResidentAssignment(GameTestHelper helper) {
        var fixture = fixture(helper, SettlementAreaType.FARM, true);
        var delegate = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "WorkerDelegate"));
        try {
            var settings = snapshot(helper, fixture).with(SettlementWorkerSetting.TILL_SOIL, 0);
            fixture.town().setPermissions(fixture.leader().getUUID(), delegate.getUUID(), Set.of(SettlementPermission.LOGISTICS));
            helper.assertFalse(configure(delegate, fixture, settings), "inventory permission does not grant worker configuration");
            fixture.town().setPermissions(fixture.leader().getUUID(), delegate.getUUID(), Set.of(SettlementPermission.STAFF));
            helper.assertTrue(configure(delegate, fixture, settings), "staff can configure the civic farmer without a contract");
            helper.assertFalse(HiredWorkStateStore.state(fixture.villager()).getBoolean("FarmingTillSoil"), "the real farmer receives the setting");
            long revision = fixture.town().revision();
            helper.assertFalse(SettlementWorkerConfigurationService.configure(delegate, fixture.town(), UUID.randomUUID(), fixture.area().id(), settings),
                    "foreign residents are rejected");
            helper.assertFalse(SettlementWorkerConfigurationService.configure(delegate, fixture.town(), fixture.villager().getUUID(), UUID.randomUUID(), settings),
                    "a stale workplace is rejected");
            helper.assertFalse(configure(delegate, fixture, SettlementWorkerSettings.capture(HiredVillagerRole.MINING, new CompoundTag())),
                    "settings cannot change the assigned role");
            helper.assertValueEqual(fixture.town().revision(), revision, "denied edits preserve the accepted settings");
            fixture.town().setPermissions(fixture.leader().getUUID(), delegate.getUUID(), Set.of());
            helper.assertFalse(configure(delegate, fixture, settings.with(SettlementWorkerSetting.TILL_SOIL, 1)), "revoked staff cannot configure workers");
        } finally {
            fixture.villager().discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_worker_settings")
    public static void individualPauseSurvivesAreaChangesAndSaveReload(GameTestHelper helper) {
        var fixture = fixture(helper, SettlementAreaType.FARM, true);
        try {
            var paused = snapshot(helper, fixture).with(SettlementWorkerSetting.ENABLED, 0);
            helper.assertTrue(configure(fixture.leader(), fixture, paused), "the farmer can be paused");
            for (int attempt = 0; attempt < 3; attempt++) {
                SettlementWorkforceService.reconcile(helper.getLevel(), fixture.villager());
            }
            helper.assertFalse(HiredWorkStateStore.state(fixture.villager()).getBoolean("Enabled"), "assignment reconciliation preserves the individual pause");
            helper.assertFalse(SettlementWorkforceService.onDuty(helper.getLevel(), fixture.villager()), "paused workers are off duty");
            var area = fixture.area();
            var disabled = new SettlementArea(area.id(), area.type(), area.name(), area.min(), area.max(), area.priority(), false);
            fixture.town().putArea(fixture.leader().getUUID(), disabled);
            helper.assertTrue(configure(fixture.leader(), fixture, paused.with(SettlementWorkerSetting.ENABLED, 1)), "the personal pause can be lifted");
            helper.assertFalse(HiredWorkStateStore.state(fixture.villager()).getBoolean("Enabled"), "a disabled area still prevents work");
            fixture.town().putArea(fixture.leader().getUUID(), area);
            SettlementWorkforceService.reconcile(helper.getLevel(), fixture.villager());
            helper.assertTrue(HiredWorkStateStore.state(fixture.villager()).getBoolean("Enabled"), "reenabling the area restores the configured worker");
            configure(fixture.leader(), fixture, paused);
            CompoundTag saved = fixture.town().save(helper.getLevel().registryAccess());
            var restored = SettlementRecord.load(saved, helper.getLevel().registryAccess(), () -> {});
            helper.assertValueEqual(restored.workerSettings().get(fixture.villager().getUUID()), paused, "settings survive saving");
            CompoundTag legacy = saved.copy();
            legacy.remove("WorkerSettings");
            helper.assertTrue(SettlementRecord.load(legacy, helper.getLevel().registryAccess(), () -> {}).workerSettings().isEmpty(),
                    "older settlements retain their existing worker settings");
            restored.removeDuty(fixture.villager().getUUID());
            helper.assertTrue(restored.workerSettings().isEmpty(), "releasing a duty clears its settlement configuration");
        } finally {
            fixture.villager().discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_worker_settings")
    public static void unloadedSettingsRestoreWithoutOverwritingNewerInventory(GameTestHelper helper) {
        var fixture = fixture(helper, SettlementAreaType.FARM, false);
        var level = helper.getLevel();
        var worker = fixture.town().workers().get(fixture.villager().getUUID());
        CompoundTag entitySave = new CompoundTag();
        fixture.villager().saveWithoutId(entitySave);
        var items = worker.items(level.registryAccess());
        items.set(HiredJobInventory.MAIN_GRID_START, new ItemStack(Items.WHEAT, 7));
        worker.updateInventory(level.registryAccess(), items);
        worker.work(200, 1.0D);
        helper.assertTrue(worker.spend(100), "background production records a new receipt");
        long version = worker.version();
        var settings = snapshot(helper, fixture).with(SettlementWorkerSetting.ENABLED, 0).with(SettlementWorkerSetting.TILL_SOIL, 0);
        helper.assertTrue(configure(fixture.leader(), fixture, settings), "an unloaded resident can be configured");
        helper.assertValueEqual(worker.version(), version, "configuration does not replace the inventory receipt");
        helper.assertFalse(worker.settings().getBoolean("Enabled"), "background work sees the pause immediately");
        Villager returning = EntityType.VILLAGER.create(level);
        returning.load(entitySave);
        SettlementBackgroundService.onActive(returning);
        SettlementWorkforceService.reconcile(level, returning);
        helper.assertValueEqual(HiredJobInventory.getJobInventory(returning).getItem(HiredJobInventory.MAIN_GRID_START).getCount(), 7,
                "the returning worker receives the newer real output");
        helper.assertFalse(HiredWorkStateStore.state(returning).getBoolean("Enabled"), "the returning worker receives the remote pause");
        helper.assertFalse(HiredWorkStateStore.state(returning).getBoolean("FarmingTillSoil"), "remote role options replace the older entity settings");
        SettlementBackgroundService.onActive(returning);
        helper.assertValueEqual(HiredJobInventory.getJobInventory(returning).getItem(HiredJobInventory.MAIN_GRID_START).getCount(), 7,
                "repeated handoff does not duplicate or erase stock");
        helper.assertFalse(level.hasChunkAt(fixture.town().center()), "configuration never loads the absent resident's chunks");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_worker_settings")
    public static void loggingAndAnimalFiltersUseValidatedNativeSelections(GameTestHelper helper) {
        var logger = fixture(helper, SettlementAreaType.FORESTRY, false);
        var current = snapshot(helper, logger);
        var oak = ResourceLocation.withDefaultNamespace("oak_log");
        var stone = ResourceLocation.withDefaultNamespace("stone");
        helper.assertFalse(configure(logger.leader(), logger, new SettlementWorkerSettings(current.role(), current.options(), Set.of(stone), Set.of())),
                "non-log blocks cannot enter the forestry selection");
        var selected = new SettlementWorkerSettings(current.role(), current.options(), Set.of(oak), Set.of());
        helper.assertTrue(configure(logger.leader(), logger, selected), "valid log types are accepted");
        SettlementBackgroundService.onActive(logger.villager());
        helper.assertValueEqual(HiredLoggingFilters.selectedFilterIds(HiredWorkStateStore.state(logger.villager())), Set.of(oak),
                "native logging uses the configured selection");
        var handler = fixture(helper, SettlementAreaType.ANIMAL_PEN, false);
        var animalSettings = snapshot(helper, handler).with(SettlementWorkerSetting.CULL_CAP, 2);
        var cow = ResourceLocation.withDefaultNamespace("cow");
        helper.assertTrue(configure(handler.leader(), handler,
                new SettlementWorkerSettings(animalSettings.role(), animalSettings.options(), Set.of(), Set.of(cow))), "valid animal types are accepted");
        SettlementBackgroundService.onActive(handler.villager());
        helper.assertValueEqual(HiredAnimalBreedingTargets.selectedTargetIds(HiredWorkStateStore.state(handler.villager())), Set.of(cow),
                "native animal handling uses the selected species");
        helper.assertValueEqual(HiredAnimalCullSettings.cap(HiredWorkStateStore.state(handler.villager())), 4, "herd limits use existing supported values");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_worker_settings")
    public static void workerSettingsRoundTripWithManagementAndRejectInvalidModes(GameTestHelper helper) {
        var fixture = fixture(helper, SettlementAreaType.FARM, false);
        var settings = snapshot(helper, fixture).with(SettlementWorkerSetting.TILL_SOIL, 0);
        var command = new SettlementManagementActionPayload(fixture.town().id().value(),
                new SettlementManagementActionPayload.SaveWorker(fixture.villager().getUUID(), fixture.area().id(), settings));
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            SettlementManagementActionPayload.STREAM_CODEC.encode(buffer, command);
            helper.assertValueEqual(SettlementManagementActionPayload.STREAM_CODEC.decode(buffer), command, "typed worker command round trips");
            helper.assertTrue(SettlementManagementService.execute(fixture.leader(), command), "management routes the worker command through staff authority");
            var payload = SettlementManagementService.snapshot(fixture.leader(), new SettlementManagementRequestPayload(
                    fixture.town().id().value(), SettlementManagementRequestPayload.Page.RESIDENTS, 0, fixture.villager().getUUID().toString(), 3, false));
            helper.assertValueEqual(payload.entries().size(), 1, "the exact resident is present in management");
            helper.assertValueEqual(payload.entries().getFirst().name(), "River", "unloaded residents retain their observed name");
            helper.assertValueEqual(((SettlementManagementPayload.Resident) payload.entries().getFirst()).profession(),
                    ResourceLocation.withDefaultNamespace("farmer"), "unloaded residents retain their observed profession");
            SettlementManagementPayload.STREAM_CODEC.encode(buffer, payload);
            var decoded = SettlementManagementPayload.STREAM_CODEC.decode(buffer);
            helper.assertValueEqual(((SettlementManagementPayload.Resident) decoded.entries().getFirst()).settings(), settings,
                    "management supplies the same settings to the client");
        } finally {
            buffer.release();
        }
        boolean rejected = false;
        try {
            settings.with(SettlementWorkerSetting.TILL_SOIL, 2);
        } catch (IllegalArgumentException exception) {
            rejected = true;
        }
        helper.assertTrue(rejected, "booleans reject values outside their range");
        rejected = false;
        try {
            SettlementWorkerSettings.capture(HiredVillagerRole.COMBAT, new CompoundTag()).with(SettlementWorkerSetting.GUARD_MODE, 2);
        } catch (IllegalArgumentException exception) {
            rejected = true;
        }
        helper.assertTrue(rejected, "civic guard settings cannot issue attack-all orders");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "settlement_worker_settings")
    public static void pausingABuilderPreservesTheAcceptedBlueprintJob(GameTestHelper helper) {
        var fixture = fixture(helper, SettlementAreaType.CONSTRUCTION, true);
        try {
            var entry = com.jvn.villagerretaliation.interaction.work.builder.BuilderStructureCatalog.entries(helper.getLevel().getServer()).getFirst();
            var rotation = net.minecraft.world.level.block.Rotation.NONE;
            var plan = com.jvn.villagerretaliation.interaction.work.builder.BuilderStructureScanner.scan(helper.getLevel(), entry, rotation).orElseThrow();
            var state = HiredWorkStateStore.state(fixture.villager());
            com.jvn.villagerretaliation.interaction.work.builder.BuilderTaskState.start(
                    state, entry, plan, fixture.area().min(), rotation, 0, helper.getLevel().getGameTime(), UUID.randomUUID());
            CompoundTag accepted = state.getCompound("BuilderTask").copy();
            var settings = snapshot(helper, fixture);
            helper.assertTrue(configure(fixture.leader(), fixture, settings.with(SettlementWorkerSetting.ENABLED, 0)), "the civic builder can pause");
            helper.assertValueEqual(state.getCompound("BuilderTask"), accepted, "pausing does not cancel the accepted blueprint or its progress");
            helper.assertTrue(configure(fixture.leader(), fixture, settings), "the civic builder can resume");
            helper.assertValueEqual(state.getCompound("BuilderTask"), accepted, "resuming continues the same project");
        } finally {
            fixture.villager().discard();
        }
        helper.succeed();
    }

    private static SettlementWorkerSettings snapshot(GameTestHelper helper, Fixture fixture) {
        return SettlementWorkerConfigurationService.snapshot(helper.getLevel(), fixture.town(), fixture.villager().getUUID());
    }

    private static boolean configure(ServerPlayer player, Fixture fixture, SettlementWorkerSettings settings) {
        return SettlementWorkerConfigurationService.configure(player, fixture.town(), fixture.villager().getUUID(), fixture.area().id(), settings);
    }

    private static Fixture fixture(GameTestHelper helper, SettlementAreaType type, boolean loaded) {
        var level = helper.getLevel();
        var origin = helper.absolutePos(new BlockPos(1, 2, 1));
        var center = loaded ? origin : new BlockPos(10_000_000 + origin.getX() * 8 + type.ordinal() * 256, 64, 10_000_000 + origin.getZ() * 8);
        var leader = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "WorkerLeader"));
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var id = registry.create(level.getGameTime(), level.dimension().location(), center, "");
        var town = SettlementSavedData.get(level).establish(registry, id, leader.getUUID(), center, level.getGameTime()).orElseThrow();
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
}
