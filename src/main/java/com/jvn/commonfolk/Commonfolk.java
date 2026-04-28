package com.jvn.commonfolk;

import com.jvn.commonfolk.config.CommonfolkConfig;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

@Mod(Commonfolk.MOD_ID)
public class Commonfolk {
    public static final String MOD_ID = "commonfolk";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Commonfolk(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, CommonfolkConfig.SPEC);
    }
}
