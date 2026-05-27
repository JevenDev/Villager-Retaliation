package com.jvn.villagerretaliation.client.trade;

import com.jvn.villagerretaliation.client.VillagerRetaliationClientAssets;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.neoforged.neoforge.client.event.ContainerScreenEvent;

public final class VillagerTradeScreenBackground {
    private static final int VANILLA_WIDTH = 276;
    private static final int TEXTURE_WIDTH = 512;
    private static final int TEXTURE_HEIGHT = 256;
    private static final int EXTENDED_LEFT_WIDTH = 20;
    private static final int EXTENDED_WIDTH = VANILLA_WIDTH + EXTENDED_LEFT_WIDTH;
    private static final int BACKGROUND_HEIGHT = 166;

    private VillagerTradeScreenBackground() {
    }

    public static void onBackgroundRender(ContainerScreenEvent.Render.Background event) {
        if (!(event.getContainerScreen() instanceof MerchantScreen screen)) {
            return;
        }

        event.getGuiGraphics().blit(
                VillagerRetaliationClientAssets.VILLAGER_TRADE_EXTENDED_TEXTURE,
                screen.getGuiLeft() - EXTENDED_LEFT_WIDTH,
                screen.getGuiTop(),
                0,
                0,
                EXTENDED_WIDTH,
                BACKGROUND_HEIGHT,
                TEXTURE_WIDTH,
                TEXTURE_HEIGHT);
    }
}
