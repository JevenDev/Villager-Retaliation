package com.jvn.villagerretaliation.util;

import com.jvn.villagerretaliation.dialogue.BiomeStoryResources;
import com.jvn.villagerretaliation.dialogue.DangerousStructureStoryResources;
import com.jvn.villagerretaliation.dialogue.VillagerDialogueResources;
import com.jvn.villagerretaliation.dialogue.VillagerInteractionSavedData;
import com.jvn.villagerretaliation.interaction.VillagerGiftResources;
import com.jvn.villagerretaliation.notification.VillagerNotificationResources;
import com.jvn.villagerretaliation.reputation.VillagerReputationSavedData;
import com.jvn.villagerretaliation.villager.VillagerPresetNameRegistry;
import net.minecraft.server.MinecraftServer;

public final class VillagerDataWarmup {
    private VillagerDataWarmup() {
    }

    public static void warm(MinecraftServer server) {
        VillagerDialogueResources.warm(server);
        VillagerGiftResources.warm(server);
        VillagerNotificationResources.warm(server);
        BiomeStoryResources.warm(server);
        DangerousStructureStoryResources.warm(server);
        VillagerPresetNameRegistry.warm(server);
        VillagerInteractionSavedData.get(server.overworld());
        VillagerReputationSavedData.get(server.overworld());
    }

    public static void clearCaches() {
        VillagerDialogueResources.clearCache();
        VillagerGiftResources.clearCache();
        VillagerNotificationResources.clearCache();
        BiomeStoryResources.clearCache();
        DangerousStructureStoryResources.clearCache();
        VillagerPresetNameRegistry.clearCache();
    }
}
