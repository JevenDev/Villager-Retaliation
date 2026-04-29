package com.jvn.commonfolk;

import com.jvn.commonfolk.config.CommonfolkConfig;
import com.jvn.commonfolk.event.CommonfolkEvents;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;

@Mod(Commonfolk.MOD_ID)
public class Commonfolk {
    public static final String MOD_ID = "commonfolk";

    public Commonfolk(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, CommonfolkConfig.SPEC);
        NeoForge.EVENT_BUS.addListener(CommonfolkEvents::onLivingDamage);
        NeoForge.EVENT_BUS.addListener(CommonfolkEvents::onLivingDeath);
        NeoForge.EVENT_BUS.addListener(CommonfolkEvents::onLivingDrops);
        NeoForge.EVENT_BUS.addListener(CommonfolkEvents::onEntityTickPre);
        NeoForge.EVENT_BUS.addListener(CommonfolkEvents::onEntityTickPost);
    }
}
