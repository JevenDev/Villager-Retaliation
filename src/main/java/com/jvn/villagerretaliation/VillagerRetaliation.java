package com.jvn.villagerretaliation;

import com.jvn.villagerretaliation.command.VillagerRetaliationCommands;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.event.VillagerRetaliationEvents;
import com.jvn.villagerretaliation.network.VillagerReputationNetworking;
import com.jvn.villagerretaliation.reputation.VillagerReputationEvents;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;

@Mod(VillagerRetaliation.MOD_ID)
public class VillagerRetaliation {
    public static final String MOD_ID = "villagerretaliation";

    public VillagerRetaliation(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, VillagerRetaliationConfig.SPEC);
        modEventBus.addListener(VillagerRetaliationEvents::onEntityAttributeModification);
        modEventBus.addListener(VillagerReputationNetworking::registerPayloads);
        NeoForge.EVENT_BUS.addListener(VillagerRetaliationCommands::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(VillagerRetaliationEvents::onLivingDamagePre);
        NeoForge.EVENT_BUS.addListener(VillagerRetaliationEvents::onLivingDamage);
        NeoForge.EVENT_BUS.addListener(VillagerReputationEvents::onLivingDamage);
        NeoForge.EVENT_BUS.addListener(VillagerRetaliationEvents::onLivingDeath);
        NeoForge.EVENT_BUS.addListener(VillagerReputationEvents::onLivingDeath);
        NeoForge.EVENT_BUS.addListener(VillagerRetaliationEvents::onLivingDrops);
        NeoForge.EVENT_BUS.addListener(VillagerRetaliationEvents::onEntityTickPre);
        NeoForge.EVENT_BUS.addListener(VillagerRetaliationEvents::onEntityTickPost);
        NeoForge.EVENT_BUS.addListener(VillagerReputationEvents::onEntityTickPost);
        NeoForge.EVENT_BUS.addListener(VillagerReputationEvents::onServerTickPost);
        NeoForge.EVENT_BUS.addListener(VillagerReputationEvents::onLivingConversionPost);
        NeoForge.EVENT_BUS.addListener(VillagerRetaliationEvents::onEntityJoinLevel);
        NeoForge.EVENT_BUS.addListener(VillagerRetaliationEvents::onEntityInteract);
        NeoForge.EVENT_BUS.addListener(VillagerRetaliationEvents::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(VillagerReputationEvents::onTradeWithVillager);
        NeoForge.EVENT_BUS.addListener(VillagerReputationEvents::onContainerOpen);
        NeoForge.EVENT_BUS.addListener(VillagerRetaliationEvents::onEntityLeaveLevel);
    }
}
