package com.jvn.villagerretaliation.util;

import com.jvn.villagerretaliation.allegiance.VillageNameResources;
import com.jvn.villagerretaliation.combat.VillagerPacifyPaymentResources;
import com.jvn.villagerretaliation.dialogue.resources.BiomeStoryResources;
import com.jvn.villagerretaliation.dialogue.resources.DangerousStructureStoryResources;
import com.jvn.villagerretaliation.dialogue.forced.ForcedDialogueResources;
import com.jvn.villagerretaliation.dialogue.forced.container.GeneratedContainerLootResources;
import com.jvn.villagerretaliation.dialogue.DialogueTreeResources;
import com.jvn.villagerretaliation.dialogue.resources.VillagerDialogueResources;
import com.jvn.villagerretaliation.dialogue.VillagerInteractionSavedData;
import com.jvn.villagerretaliation.event.VillagerEventTriggerSavedData;
import com.jvn.villagerretaliation.event.VillagerEventTriggerService;
import com.jvn.villagerretaliation.interaction.VillagerCurrencyResources;
import com.jvn.villagerretaliation.interaction.VillagerGiftResources;
import com.jvn.villagerretaliation.interaction.VillagerItemText;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderStructureCatalog;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderStructureScanner;
import com.jvn.villagerretaliation.loot.ProfessionLootResources;
import com.jvn.villagerretaliation.notification.VillagerNotificationResources;
import com.jvn.villagerretaliation.quest.VillagerQuestResources;
import com.jvn.villagerretaliation.quest.VillagerQuestSavedData;
import com.jvn.villagerretaliation.quest.pool.QuestPoolResources;
import com.jvn.villagerretaliation.raid.PlayerRaidLoadoutResources;
import com.jvn.villagerretaliation.raid.PlayerRaidSavedData;
import com.jvn.villagerretaliation.scene.SceneResources;
import com.jvn.villagerretaliation.scene.SceneRuntime;
import com.jvn.villagerretaliation.scene.persistence.SceneSavedData;
import com.jvn.villagerretaliation.scene.encounter.EncounterResources;
import com.jvn.villagerretaliation.sell.SellPriceResources;
import com.jvn.villagerretaliation.reputation.VillagerReputationSavedData;
import com.jvn.villagerretaliation.social.VillagerSocialGraphSavedData;
import com.jvn.villagerretaliation.trade.SkillTradeResources;
import com.jvn.villagerretaliation.village.VillageEventMemory;
import com.jvn.villagerretaliation.village.VillageEventMemorySavedData;
import com.jvn.villagerretaliation.village.VillageMembership;
import com.jvn.villagerretaliation.villager.VillagerNaturalJobArmorResources;
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
        SellPriceResources.warm(server);
        VillagerCurrencyResources.warm(server);
        VillagerItemText.warm(server);
        VillagerNaturalJobArmorResources.warm(server);
        PlayerRaidLoadoutResources.warm(server);
        VillagerPacifyPaymentResources.warm(server);
        BuilderStructureCatalog.warm(server);
        VillagerNotificationResources.warm(server);
        ForcedDialogueResources.warm(server);
        GeneratedContainerLootResources.warm(server);
        BiomeStoryResources.warm(server);
        DangerousStructureStoryResources.warm(server);
        VillagerQuestResources.warm(server);
        QuestPoolResources.warm(server);
        SceneResources.warm(server);
        EncounterResources.warm(server);
        VillagerEventTriggerService.warm(server);
        VillagerPresetNameRegistry.warm(server);
        VillageNameResources.warm(server);
        VillagerInteractionSavedData.get(server.overworld());
        VillagerReputationSavedData.get(server.overworld());
        VillagerSocialGraphSavedData.get(server.overworld());
        VillagerQuestSavedData.get(server.overworld());
        VillagerEventTriggerSavedData.get(server.overworld());
        VillageEventMemorySavedData.get(server.overworld());
        SceneSavedData.get(server.overworld());
        PlayerRaidSavedData.get(server.overworld());
        SceneRuntime.initialize(server);
    }

    public static void clearCaches() {
        clearResourceCaches();
        VillageNameResources.reset();
        VillageEventMemory.clear();
        VillagerEventTriggerService.clearRuntimeState();
        SceneRuntime.clearRuntimeState();
    }

    public static void clearResourceCaches() {
        DatapackDiagnostics.clear();
        VillagerDialogueResources.clearCache();
        DialogueTreeResources.clearCache();
        VillagerGiftResources.clearCache();
        ProfessionLootResources.clearCache();
        SkillTradeResources.clearCache();
        SellPriceResources.clearCache();
        VillagerCurrencyResources.clearCache();
        VillagerItemText.clearCache();
        VillagerNaturalJobArmorResources.clearCache();
        PlayerRaidLoadoutResources.clearCache();
        VillagerPacifyPaymentResources.clearCache();
        BuilderStructureCatalog.clearCache();
        BuilderStructureScanner.clearCache();
        VillagerNotificationResources.clearCache();
        ForcedDialogueResources.clearCache();
        GeneratedContainerLootResources.clearCache();
        BiomeStoryResources.clearCache();
        DangerousStructureStoryResources.clearCache();
        VillagerWorldTargetCache.clearCache();
        VillagerQuestResources.clearCache();
        QuestPoolResources.clearCache();
        SceneResources.clearCache();
        EncounterResources.clearCache();
        VillagerEventTriggerService.clearCache();
        VillagerPresetNameRegistry.clearCache();
        VillageNameResources.clearCache();
        VillageMembership.clearCache();
        VillagerRetaliationVillagerWeapons.clearCache();
    }
}
