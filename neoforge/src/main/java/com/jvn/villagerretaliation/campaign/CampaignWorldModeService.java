package com.jvn.villagerretaliation.campaign;

import com.jvn.villagerretaliation.config.NewWorldMode;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ServerLevelData;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;

public final class CampaignWorldModeService {
    private static final Logger LOGGER = LogUtils.getLogger();

    private CampaignWorldModeService() {
    }

    public static void onLevelLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && level.getLevelData() instanceof ServerLevelData settings) {
            initializeNewWorld(level, settings, VillagerRetaliationConfig.NEW_WORLD_MODE.get());
        }
    }

    static boolean initializeNewWorld(ServerLevel level, ServerLevelData settings, NewWorldMode choice) {
        if (!level.dimension().equals(Level.OVERWORLD) || settings.isInitialized()) return false;
        CampaignWorldModeSavedData data = CampaignWorldModeSavedData.get(level);
        String authority = level.getServer().isDedicatedServer() ? "server_operator" : "singleplayer_owner";
        if (!data.selectForNewWorld(choice.worldMode(), authority, level.getGameTime())) return false;
        level.getDataStorage().save();
        return true;
    }

    public static void onServerStarted(ServerStartedEvent event) {
        CampaignWorldModeSavedData data = CampaignWorldModeSavedData.get(event.getServer().overworld());
        data.freezeExistingMode();
        if (!data.diagnostic().isBlank()) {
            LOGGER.error("Campaign mode is unavailable: {}. The original data is preserved. "
                            + "Use a compatible mod version or restore data/{}.dat from a known-good backup.",
                    data.diagnostic(), CampaignWorldModeSavedData.DATA_NAME);
        }
    }
}
