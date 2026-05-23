package com.jvn.villagerretaliation.util;

import com.jvn.villagerretaliation.combat.VillagerPacifyPaymentResources;
import com.jvn.villagerretaliation.dialogue.BiomeStoryResources;
import com.jvn.villagerretaliation.dialogue.DangerousStructureStoryResources;
import com.jvn.villagerretaliation.dialogue.VillagerDialogueResources;
import com.jvn.villagerretaliation.dialogue.VillagerInteractionSavedData;
import com.jvn.villagerretaliation.interaction.VillagerGiftResources;
import com.jvn.villagerretaliation.notification.VillagerNotificationResources;
import com.jvn.villagerretaliation.reputation.VillagerReputationSavedData;
import com.jvn.villagerretaliation.social.VillagerSocialGraphSavedData;
import com.jvn.villagerretaliation.village.VillageEventMemory;
import com.jvn.villagerretaliation.village.VillageMembership;
import com.jvn.villagerretaliation.villager.VillagerPresetNameRegistry;
import com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerWeapons;
import net.minecraft.server.MinecraftServer;

public final class VillagerDataWarmup {
    private VillagerDataWarmup() {
    }

    public static void warm(MinecraftServer server) {
        VillagerDialogueResources.warm(server);
        VillagerGiftResources.warm(server);
        VillagerPacifyPaymentResources.warm(server);
        VillagerNotificationResources.warm(server);
        BiomeStoryResources.warm(server);
        DangerousStructureStoryResources.warm(server);
        VillagerPresetNameRegistry.warm(server);
        VillagerInteractionSavedData.get(server.overworld());
        VillagerReputationSavedData.get(server.overworld());
        VillagerSocialGraphSavedData.get(server.overworld());
    }

    public static void clearCaches() {
        clearResourceCaches();
        VillageEventMemory.clear();
    }

    public static void clearResourceCaches() {
        VillagerDialogueResources.clearCache();
        VillagerGiftResources.clearCache();
        VillagerPacifyPaymentResources.clearCache();
        VillagerNotificationResources.clearCache();
        BiomeStoryResources.clearCache();
        DangerousStructureStoryResources.clearCache();
        VillagerPresetNameRegistry.clearCache();
        VillageMembership.clearCache();
        VillagerRetaliationVillagerWeapons.clearCache();
    }
}
