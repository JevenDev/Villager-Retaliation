package com.jvn.villagerretaliation;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.event.VillagerRetaliationEvents;
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
        NeoForge.EVENT_BUS.addListener(VillagerRetaliationEvents::onLivingDamagePre);
        NeoForge.EVENT_BUS.addListener(VillagerRetaliationEvents::onLivingDamage);
        NeoForge.EVENT_BUS.addListener(VillagerRetaliationEvents::onLivingDeath);
        NeoForge.EVENT_BUS.addListener(VillagerRetaliationEvents::onLivingDrops);
        NeoForge.EVENT_BUS.addListener(VillagerRetaliationEvents::onEntityTickPre);
        NeoForge.EVENT_BUS.addListener(VillagerRetaliationEvents::onEntityTickPost);
        NeoForge.EVENT_BUS.addListener(VillagerRetaliationEvents::onEntityJoinLevel);
        NeoForge.EVENT_BUS.addListener(VillagerRetaliationEvents::onEntityInteract);
        NeoForge.EVENT_BUS.addListener(VillagerRetaliationEvents::onEntityLeaveLevel);
    }
}
