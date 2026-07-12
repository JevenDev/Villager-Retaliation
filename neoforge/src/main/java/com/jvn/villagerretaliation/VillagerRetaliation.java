package com.jvn.villagerretaliation;

import com.jvn.villagerretaliation.command.VillagerRetaliationCommands;
import com.jvn.villagerretaliation.api.VillagerRetaliationRegistries;
import com.jvn.villagerretaliation.block.VillagerRetaliationBlockEntityTypes;
import com.jvn.villagerretaliation.block.VillagerRetaliationBlocks;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.debug.VillagerRetaliationDebugItems;
import com.jvn.villagerretaliation.entity.VillagerRetaliationEntityTypes;
import com.jvn.villagerretaliation.event.VillagerRetaliationEvents;
import com.jvn.villagerretaliation.inventory.VillagerRetaliationMenus;
import com.jvn.villagerretaliation.item.VillagerRetaliationCreativeTabs;
import com.jvn.villagerretaliation.item.VillagerRetaliationItems;
import com.jvn.villagerretaliation.interaction.work.HiredPathMemory;
import com.jvn.villagerretaliation.interaction.work.mining.HiredOreBlockTracker;
import com.jvn.villagerretaliation.network.VillagerReputationNetworking;
import com.jvn.villagerretaliation.reputation.VillagerReputationEvents;
import com.jvn.villagerretaliation.recipe.VillagerRetaliationRecipes;
import com.jvn.villagerretaliation.social.VillagerSocialGraphService;
import com.jvn.villagerretaliation.dialogue.forced.ForcedDialogueService;
import com.jvn.villagerretaliation.trade.VillagerSkillTradeEvents;
import com.jvn.villagerretaliation.villager.VillagerConversionPersistenceService;
import com.jvn.toucanlib.neoforge.event.ToucanEventBuses;
import com.jvn.toucanlib.util.ToucanIds;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

@Mod(VillagerRetaliation.MOD_ID)
public class VillagerRetaliation {
    public static final String MOD_ID = "villagerretaliation";
    private static final ToucanIds IDS = ToucanIds.create(MOD_ID);

    public static ResourceLocation id(String path) {
        return IDS.id(path);
    }

    public VillagerRetaliation(IEventBus modEventBus, ModContainer modContainer) {
        VillagerRetaliationRegistries.registerBuiltIns();
        VillagerRetaliationConfig.init();
        VillagerRetaliationBlocks.register(modEventBus);
        VillagerRetaliationBlockEntityTypes.register(modEventBus);
        VillagerRetaliationEntityTypes.register(modEventBus);
        VillagerRetaliationMenus.register(modEventBus);
        VillagerRetaliationItems.register(modEventBus);
        VillagerRetaliationRecipes.register(modEventBus);
        VillagerRetaliationCreativeTabs.register(modEventBus);
        VillagerRetaliationDebugItems.register(modEventBus);
        ToucanEventBuses.on(modEventBus)
                .listener(VillagerRetaliationEvents::onEntityAttributeModification)
                .listener(VillagerReputationNetworking::registerPayloads);
        ToucanEventBuses.game()
                .listener(VillagerRetaliationEvents::onServerStarted)
                .listener(VillagerRetaliationEvents::onServerStopping)
                .listener(VillagerRetaliationEvents::onAddReloadListeners)
                .listener(VillagerSkillTradeEvents::onVillagerTrades)
                .listener(VillagerSkillTradeEvents::onWandererTrades)
                .listener(VillagerRetaliationCommands::onRegisterCommands)
                .listener(VillagerRetaliationEvents::onLivingDamagePre)
                .listener(VillagerRetaliationEvents::onLivingDamageFinalPre)
                .listener(VillagerRetaliationEvents::onLivingDamage)
                .listener(VillagerReputationEvents::onLivingDamage)
                .listener(VillagerRetaliationEvents::onLivingDeath)
                .listener(VillagerSocialGraphService::onLivingDeath)
                .listener(VillagerReputationEvents::onLivingDeath)
                .listener(VillagerRetaliationEvents::onLivingDrops)
                .listener(VillagerRetaliationEvents::onEntityTickPre)
                .listener(VillagerRetaliationEvents::onEntityTickPost)
                .listener(VillagerRetaliationEvents::onPlayerLoggedIn)
                .listener(VillagerRetaliationEvents::onServerTickPost)
                .listener(VillagerReputationEvents::onServerTickPost)
                .listener(VillagerSocialGraphService::onBabyEntitySpawn)
                .listener(VillagerSocialGraphService::onLivingConversionPost)
                .listener(VillagerReputationEvents::onLivingConversionPost)
                .listener(VillagerConversionPersistenceService::onLivingConversionPost)
                .listener(VillagerRetaliationEvents::onEntityJoinLevel)
                .listener(VillagerSocialGraphService::onEntityJoinLevel)
                .listener(VillagerRetaliationEvents::onPlayerStartTracking)
                .listener(VillagerRetaliationEvents::onPlayerLoggedOut)
                .listener(VillagerRetaliationEvents::onPlayerClone)
                .listener(VillagerRetaliationEvents::onEntityInteract)
                .listener(VillagerRetaliationEvents::onEntityInteractSpecific)
                .listener(VillagerRetaliationEvents::onRightClickBlock)
                .listener(VillagerRetaliationEvents::onLeftClickBlock)
                .listener(VillagerRetaliationEvents::onBlockBreak)
                .listener(VillagerRetaliationEvents::onBlockPlace)
                .listener(HiredOreBlockTracker::onBlockPlace)
                .listener(HiredOreBlockTracker::onFluidPlaceBlock)
                .listener(HiredPathMemory::onFluidPlaceBlock)
                .listener(HiredOreBlockTracker::onChunkUnload)
                .listener(VillagerReputationEvents::onTradeWithVillager)
                .listener(ForcedDialogueService::onContainerOpen)
                .listener(VillagerReputationEvents::onContainerOpen)
                .listener(ForcedDialogueService::onItemToss)
                .listener(ForcedDialogueService::onContainerClose)
                .listener(VillagerRetaliationEvents::onEntityLeaveLevel);
    }
}
