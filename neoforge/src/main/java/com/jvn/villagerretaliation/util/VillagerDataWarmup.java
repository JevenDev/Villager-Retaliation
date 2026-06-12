package com.jvn.villagerretaliation.util;

import com.jvn.villagerretaliation.combat.VillagerPacifyPaymentResources;
import com.jvn.villagerretaliation.dialogue.BiomeStoryResources;
import com.jvn.villagerretaliation.dialogue.DangerousStructureStoryResources;
import com.jvn.villagerretaliation.dialogue.ForcedDialogueResources;
import com.jvn.villagerretaliation.dialogue.GeneratedContainerLootResources;
import com.jvn.villagerretaliation.dialogue.DialogueTreeResources;
import com.jvn.villagerretaliation.dialogue.VillagerDialogueResources;
import com.jvn.villagerretaliation.dialogue.VillagerInteractionSavedData;
import com.jvn.villagerretaliation.event.VillagerEventTriggerSavedData;
import com.jvn.villagerretaliation.event.VillagerEventTriggerService;
import com.jvn.villagerretaliation.interaction.VillagerCurrencyResources;
import com.jvn.villagerretaliation.interaction.VillagerGiftResources;
import com.jvn.villagerretaliation.interaction.work.BuilderStructureCatalog;
import com.jvn.villagerretaliation.loot.ProfessionLootResources;
import com.jvn.villagerretaliation.notification.VillagerNotificationResources;
import com.jvn.villagerretaliation.quest.VillagerQuestResources;
import com.jvn.villagerretaliation.quest.VillagerQuestSavedData;
import com.jvn.villagerretaliation.reputation.VillagerReputationSavedData;
import com.jvn.villagerretaliation.social.VillagerSocialGraphSavedData;
import com.jvn.villagerretaliation.trade.SkillTradeResources;
import com.jvn.villagerretaliation.village.VillageEventMemory;
import com.jvn.villagerretaliation.village.VillageEventMemorySavedData;
import com.jvn.villagerretaliation.village.VillageMembership;
import com.jvn.villagerretaliation.villager.VillagerPresetNameRegistry;
import com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerWeapons;
import net.minecraft.server.MinecraftServer;

public final class VillagerDataWarmup {
    private VillagerDataWarmup() {
    }

    public static void warm(MinecraftServer server) {
        VillagerDialogueResources.warm(server);
        DialogueTreeResources.warm(server);
        VillagerGiftResources.warm(server);
        ProfessionLootResources.warm(server);
        SkillTradeResources.warm(server);
        VillagerCurrencyResources.warm(server);
        VillagerPacifyPaymentResources.warm(server);
        BuilderStructureCatalog.warm(server);
        VillagerNotificationResources.warm(server);
        ForcedDialogueResources.warm(server);
        GeneratedContainerLootResources.warm(server);
        BiomeStoryResources.warm(server);
        DangerousStructureStoryResources.warm(server);
        VillagerQuestResources.warm(server);
        VillagerEventTriggerService.warm(server);
        VillagerPresetNameRegistry.warm(server);
        VillagerInteractionSavedData.get(server.overworld());
        VillagerReputationSavedData.get(server.overworld());
        VillagerSocialGraphSavedData.get(server.overworld());
        VillagerQuestSavedData.get(server.overworld());
        VillagerEventTriggerSavedData.get(server.overworld());
        VillageEventMemorySavedData.get(server.overworld());
    }

    public static void clearCaches() {
        clearResourceCaches();
        VillageEventMemory.clear();
        VillagerEventTriggerService.clearRuntimeState();
    }

    public static void clearResourceCaches() {
        DatapackDiagnostics.clear();
        VillagerDialogueResources.clearCache();
        DialogueTreeResources.clearCache();
        VillagerGiftResources.clearCache();
        ProfessionLootResources.clearCache();
        SkillTradeResources.clearCache();
        VillagerCurrencyResources.clearCache();
        VillagerPacifyPaymentResources.clearCache();
        BuilderStructureCatalog.clearCache();
        VillagerNotificationResources.clearCache();
        ForcedDialogueResources.clearCache();
        GeneratedContainerLootResources.clearCache();
        BiomeStoryResources.clearCache();
        DangerousStructureStoryResources.clearCache();
        VillagerQuestResources.clearCache();
        VillagerEventTriggerService.clearCache();
        VillagerPresetNameRegistry.clearCache();
        VillageMembership.clearCache();
        VillagerRetaliationVillagerWeapons.clearCache();
    }
}
