package com.jvn.villagerretaliation.fabric;

import com.jvn.villagerretaliation.VillagerRetaliationCommon;
import net.fabricmc.api.ModInitializer;

public final class VillagerRetaliationFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        VillagerRetaliationCommon.init();
    }
}
