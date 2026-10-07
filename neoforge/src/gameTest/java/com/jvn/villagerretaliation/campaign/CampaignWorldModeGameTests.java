package com.jvn.villagerretaliation.campaign;

import com.jvn.villagerretaliation.config.NewWorldMode;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfigModel;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ServerLevelData;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("campaign_state")
@PrefixGameTestTemplate(false)
public final class CampaignWorldModeGameTests {
    private CampaignWorldModeGameTests() {
    }

    @GameTest(template = "empty", batch = "campaign_world_mode")
    public static void newWorldConfigurationDefaultsToCampaign(GameTestHelper helper) {
        helper.assertValueEqual(new VillagerRetaliationConfigModel().campaign.newWorldMode,
                NewWorldMode.CAMPAIGN, "new worlds default to Campaign mode");
        helper.assertValueEqual(NewWorldMode.values().length, 2, "new worlds can select Campaign or Sandbox");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_world_mode")
    public static void campaignAndSandboxSelectionsArePermanent(GameTestHelper helper) {
        for (NewWorldMode choice : NewWorldMode.values()) {
            var data = new CampaignWorldModeSavedData();
            helper.assertTrue(data.selectForNewWorld(choice.worldMode(), "server_operator", 43L), "first selection commits");
            helper.assertTrue(data.isDirty(), "selection schedules a save");
            helper.assertFalse(data.selectForNewWorld(other(choice).worldMode(), "singleplayer_owner", 99L),
                    "later configuration cannot change the world mode");
            var restored = reload(helper, data);
            helper.assertValueEqual(restored.mode(), choice.worldMode(), "selected mode survives reload");
            helper.assertValueEqual(restored.selectionSource(), "server_operator", "selection authority survives reload");
            helper.assertValueEqual(restored.selectedAt(), 43L, "selection time survives reload");
            helper.assertFalse(restored.selectForNewWorld(other(choice).worldMode(), "server_operator", 100L),
                    "reload cannot reopen selection");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_world_mode")
    public static void existingWorldsRemainLegacy(GameTestHelper helper) {
        var data = new CampaignWorldModeSavedData();
        data.freezeExistingMode();
        helper.assertFalse(data.selectForNewWorld(CampaignWorldMode.CAMPAIGN, "singleplayer_owner", 1L),
                "existing worlds cannot be converted by a changed default");
        var restored = reload(helper, data);
        helper.assertValueEqual(restored.mode(), CampaignWorldMode.LEGACY, "legacy behavior survives reload");
        helper.assertValueEqual(restored.selectionSource(), "existing_world", "existing worlds record their origin");
        helper.assertValueEqual(restored.selectedAt(), -1L, "existing worlds have no new-world selection time");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_world_mode")
    public static void unversionedDataMigratesWithoutSelectingAMode(GameTestHelper helper) {
        var empty = CampaignWorldModeSavedData.load(new CompoundTag(), helper.getLevel().registryAccess());
        helper.assertValueEqual(empty.mode(), CampaignWorldMode.LEGACY, "missing mode retains existing behavior");
        helper.assertFalse(empty.selectForNewWorld(CampaignWorldMode.CAMPAIGN, "server_operator", 1L),
                "migration does not count as a new world");
        helper.assertValueEqual(empty.save(new CompoundTag(), helper.getLevel().registryAccess()).getInt("DataVersion"),
                CampaignWorldModeSavedData.CURRENT_DATA_VERSION, "migration writes the current version");
        var unversioned = new CompoundTag();
        unversioned.putString("Mode", "sandbox");
        helper.assertValueEqual(CampaignWorldModeSavedData.load(unversioned, helper.getLevel().registryAccess()).mode(),
                CampaignWorldMode.SANDBOX, "an existing unversioned choice remains intact");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_world_mode")
    public static void unsupportedAndMalformedDataRemainReadableAndUntouched(GameTestHelper helper) {
        var future = new CompoundTag();
        future.putInt("DataVersion", CampaignWorldModeSavedData.CURRENT_DATA_VERSION + 1);
        future.putString("Mode", "campaign");
        future.putString("FutureField", "preserve me");
        assertPreserved(helper, future);
        var unknown = new CompoundTag();
        unknown.putInt("DataVersion", CampaignWorldModeSavedData.CURRENT_DATA_VERSION);
        unknown.putString("Mode", "future_mode");
        assertPreserved(helper, unknown);
        var missing = new CompoundTag();
        missing.putInt("DataVersion", CampaignWorldModeSavedData.CURRENT_DATA_VERSION);
        assertPreserved(helper, missing);
        var wrongVersionType = new CompoundTag();
        wrongVersionType.putString("DataVersion", "1");
        assertPreserved(helper, wrongVersionType);
        var wrongModeType = new CompoundTag();
        wrongModeType.putInt("Mode", 1);
        assertPreserved(helper, wrongModeType);
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_world_mode")
    public static void worldInitializationCommitsEachSelectableModeOnce(GameTestHelper helper) {
        try (WorldFixture fixture = new WorldFixture(helper)) {
            for (NewWorldMode choice : NewWorldMode.values()) {
                var data = fixture.replace();
                fixture.settings.setInitialized(false);
                helper.assertTrue(CampaignWorldModeService.initializeNewWorld(fixture.level, fixture.settings, choice),
                        "first world initialization selects the configured mode");
                helper.assertValueEqual(data.mode(), choice.worldMode(), "selected mode is world-owned");
                helper.assertValueEqual(data.selectionSource(), fixture.level.getServer().isDedicatedServer()
                        ? "server_operator" : "singleplayer_owner", "selection records the server authority");
                helper.assertFalse(data.isDirty(), "selection is flushed before initialization returns");
                helper.assertFalse(CampaignWorldModeService.initializeNewWorld(fixture.level, fixture.settings, other(choice)),
                        "duplicate world initialization cannot change the choice");
                fixture.level.getDataStorage().set(CampaignWorldModeSavedData.DATA_NAME, reload(helper, data));
                helper.assertFalse(CampaignWorldModeService.initializeNewWorld(fixture.level, fixture.settings, other(choice)),
                        "reloaded selection cannot change even before world initialization finishes");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_world_mode")
    public static void initializedWorldDoesNotAdoptNewWorldDefault(GameTestHelper helper) {
        try (WorldFixture fixture = new WorldFixture(helper)) {
            var data = fixture.replace();
            fixture.settings.setInitialized(true);
            helper.assertFalse(CampaignWorldModeService.initializeNewWorld(fixture.level, fixture.settings, NewWorldMode.CAMPAIGN),
                    "existing world initialization does not select a new mode");
            data.freezeExistingMode();
            helper.assertValueEqual(data.mode(), CampaignWorldMode.LEGACY, "existing world remains legacy");
            helper.assertFalse(data.selectForNewWorld(CampaignWorldMode.SANDBOX, "server_operator", 10L),
                    "existing world remains locked");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_world_mode")
    public static void levelLoadSelectsModeBeforeSpawnCreation(GameTestHelper helper) {
        try (WorldFixture fixture = new WorldFixture(helper)) {
            var data = fixture.replace();
            fixture.settings.setInitialized(false);
            CampaignWorldModeService.onLevelLoad(new LevelEvent.Load(fixture.level));
            helper.assertValueEqual(data.mode(), VillagerRetaliationConfig.NEW_WORLD_MODE.get().worldMode(),
                    "level loading selects the mode even when spawn creation is bypassed");
            CampaignWorldModeService.onServerStarted(new ServerStartedEvent(fixture.level.getServer()));
            helper.assertValueEqual(data.mode(), VillagerRetaliationConfig.NEW_WORLD_MODE.get().worldMode(),
                    "server startup retains the selection");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_world_mode")
    public static void serverStartupLocksExistingWorldWithoutASelection(GameTestHelper helper) {
        try (WorldFixture fixture = new WorldFixture(helper)) {
            var data = fixture.replace();
            fixture.settings.setInitialized(true);
            CampaignWorldModeService.onLevelLoad(new LevelEvent.Load(fixture.level));
            CampaignWorldModeService.onServerStarted(new ServerStartedEvent(fixture.level.getServer()));
            helper.assertValueEqual(data.mode(), CampaignWorldMode.LEGACY, "existing world retains legacy behavior");
            helper.assertFalse(data.selectForNewWorld(CampaignWorldMode.CAMPAIGN, "server_operator", 1L),
                    "startup freezes existing behavior");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_world_mode")
    public static void allDimensionsUseTheOverworldSelection(GameTestHelper helper) {
        try (WorldFixture fixture = new WorldFixture(helper)) {
            var data = fixture.replace();
            fixture.settings.setInitialized(false);
            var nether = fixture.level.getServer().getLevel(Level.NETHER);
            var end = fixture.level.getServer().getLevel(Level.END);
            helper.assertTrue(nether != null && end != null, "fixture has the standard dimensions");
            CampaignWorldModeService.onLevelLoad(new LevelEvent.Load(nether));
            helper.assertValueEqual(data.mode(), CampaignWorldMode.LEGACY, "another dimension cannot select the mode");
            helper.assertTrue(CampaignWorldModeService.initializeNewWorld(fixture.level, fixture.settings, NewWorldMode.SANDBOX),
                    "overworld initialization owns the selection");
            helper.assertTrue(CampaignWorldModeSavedData.get(nether) == data && CampaignWorldModeSavedData.get(end) == data,
                    "all dimensions share one authoritative record");
            helper.assertValueEqual(CampaignWorldModeSavedData.get(end).mode(), CampaignWorldMode.SANDBOX,
                    "dimension changes retain the world mode");
        }
        helper.succeed();
    }

    private static CampaignWorldModeSavedData reload(GameTestHelper helper, CampaignWorldModeSavedData data) {
        return CampaignWorldModeSavedData.load(data.save(new CompoundTag(), helper.getLevel().registryAccess()),
                helper.getLevel().registryAccess());
    }

    private static NewWorldMode other(NewWorldMode mode) {
        return mode == NewWorldMode.CAMPAIGN ? NewWorldMode.SANDBOX : NewWorldMode.CAMPAIGN;
    }

    private static void assertPreserved(GameTestHelper helper, CompoundTag input) {
        var original = input.copy();
        var data = CampaignWorldModeSavedData.load(input, helper.getLevel().registryAccess());
        input.putString("ExternalMutation", "ignored");
        helper.assertValueEqual(data.mode(), CampaignWorldMode.UNKNOWN, "unrecognized data disables campaign mode");
        helper.assertFalse(data.diagnostic().isBlank(), "unrecognized data provides a diagnostic");
        data.freezeExistingMode();
        helper.assertFalse(data.selectForNewWorld(CampaignWorldMode.CAMPAIGN, "server_operator", 1L),
                "unrecognized data cannot be replaced by a new selection");
        var saved = data.save(new CompoundTag(), helper.getLevel().registryAccess());
        helper.assertValueEqual(saved, original, "all original fields are preserved");
        saved.putString("SaveMutation", "ignored");
        helper.assertValueEqual(data.save(new CompoundTag(), helper.getLevel().registryAccess()), original,
                "callers cannot mutate preserved data");
    }

    private static final class WorldFixture implements AutoCloseable {
        private final ServerLevel level;
        private final ServerLevelData settings;
        private final boolean initialized;
        private final CampaignWorldModeSavedData original;

        private WorldFixture(GameTestHelper helper) {
            this.level = helper.getLevel().getServer().overworld();
            this.settings = (ServerLevelData) this.level.getLevelData();
            this.initialized = this.settings.isInitialized();
            this.original = CampaignWorldModeSavedData.get(this.level);
        }

        private CampaignWorldModeSavedData replace() {
            var data = new CampaignWorldModeSavedData();
            this.level.getDataStorage().set(CampaignWorldModeSavedData.DATA_NAME, data);
            return data;
        }

        @Override
        public void close() {
            this.settings.setInitialized(this.initialized);
            this.level.getDataStorage().set(CampaignWorldModeSavedData.DATA_NAME, this.original);
            this.original.setDirty();
            this.level.getDataStorage().save();
        }
    }
}
