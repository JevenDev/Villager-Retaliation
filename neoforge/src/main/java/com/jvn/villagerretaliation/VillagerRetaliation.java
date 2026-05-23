package com.jvn.villagerretaliation;

import com.jvn.villagerretaliation.command.VillagerRetaliationCommands;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.debug.VillagerRetaliationDebugItems;
import com.jvn.villagerretaliation.event.VillagerRetaliationEvents;
import com.jvn.villagerretaliation.inventory.VillagerRetaliationMenus;
import com.jvn.villagerretaliation.network.VillagerReputationNetworking;
import com.jvn.villagerretaliation.reputation.VillagerReputationEvents;
import com.jvn.villagerretaliation.social.VillagerSocialGraphService;
import com.jvn.toucanlib.neoforge.event.ToucanEventBuses;
import com.jvn.toucanlib.util.ToucanIds;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

@Mod(VillagerRetaliation.MOD_ID)
public class VillagerRetaliation {
    public static final String MOD_ID = "villagerretaliation";
    private static final ToucanIds IDS = ToucanIds.create(MOD_ID);

    public static ResourceLocation id(String path) {
        return IDS.id(path);
    }

    public VillagerRetaliation(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, VillagerRetaliationConfig.SPEC);
        VillagerRetaliationMenus.register(modEventBus);
        VillagerRetaliationDebugItems.register(modEventBus);
        ToucanEventBuses.on(modEventBus)
                .listener(VillagerRetaliationEvents::onEntityAttributeModification)
                .listener(VillagerReputationNetworking::registerPayloads);
        ToucanEventBuses.game()
                .listener(VillagerRetaliationEvents::onServerStarted)
                .listener(VillagerRetaliationEvents::onServerStopping)
                .listener(VillagerRetaliationEvents::onAddReloadListeners)
                .listener(VillagerRetaliationCommands::onRegisterCommands)
                .listener(VillagerRetaliationEvents::onLivingDamagePre)
                .listener(VillagerRetaliationEvents::onLivingDamage)
                .listener(VillagerReputationEvents::onLivingDamage)
                .listener(VillagerRetaliationEvents::onLivingDeath)
                .listener(VillagerSocialGraphService::onLivingDeath)
                .listener(VillagerReputationEvents::onLivingDeath)
                .listener(VillagerRetaliationEvents::onLivingDrops)
                .listener(VillagerRetaliationEvents::onEntityTickPre)
                .listener(VillagerRetaliationEvents::onEntityTickPost)
                .listener(VillagerSocialGraphService::onEntityTickPost)
                .listener(VillagerReputationEvents::onEntityTickPost)
                .listener(VillagerReputationEvents::onServerTickPost)
                .listener(VillagerSocialGraphService::onBabyEntitySpawn)
                .listener(VillagerSocialGraphService::onLivingConversionPost)
                .listener(VillagerReputationEvents::onLivingConversionPost)
                .listener(VillagerRetaliationEvents::onEntityJoinLevel)
                .listener(VillagerSocialGraphService::onEntityJoinLevel)
                .listener(VillagerRetaliationEvents::onPlayerStartTracking)
                .listener(VillagerRetaliationEvents::onEntityInteract)
                .listener(VillagerRetaliationEvents::onEntityInteractSpecific)
                .listener(VillagerRetaliationEvents::onRightClickBlock)
                .listener(VillagerRetaliationEvents::onBlockBreak)
                .listener(VillagerReputationEvents::onTradeWithVillager)
                .listener(VillagerReputationEvents::onContainerOpen)
                .listener(VillagerRetaliationEvents::onEntityLeaveLevel);
    }
}
