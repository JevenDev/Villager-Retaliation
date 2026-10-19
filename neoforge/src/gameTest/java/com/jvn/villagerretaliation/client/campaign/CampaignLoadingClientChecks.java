package com.jvn.villagerretaliation.client.campaign;

import com.jvn.villagerretaliation.campaign.selection.WorldCampaignCatalog;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

final class CampaignLoadingClientChecks {
    private static Screen parent;
    private static CampaignLoadingScreen loading;
    private static final CompletableFuture<CampaignSelectScreen> PENDING = new CompletableFuture<>();
    private static final CompletableFuture<Boolean> BACKGROUND = new CompletableFuture<>();

    private CampaignLoadingClientChecks() { }

    static void start(Screen screen) {
        parent = screen;
        loading = new CampaignLoadingScreen(parent, () -> {
            BACKGROUND.complete(!RenderSystem.isOnRenderThread());
            return PENDING.join();
        });
        Minecraft.getInstance().setScreen(loading);
    }

    static void cancel() throws ReflectiveOperationException {
        require(Minecraft.getInstance().screen == loading && BACKGROUND.getNow(false), "Campaign preparation runs away from the render thread");
        var rendered = CampaignLoadingScreen.class.getDeclaredField("rendered");
        rendered.setAccessible(true);
        require(rendered.getBoolean(loading) && !PENDING.isDone(), "The previous menu keeps rendering while campaign content loads");
        loading.onClose();
        PENDING.complete(new CampaignSelectScreen(parent, new WorldCampaignCatalog(Map.of()),
                Minecraft.getInstance().getResourceManager(), () -> { throw new AssertionError("Unexpected reload"); }, ignored -> { }));
    }

    static void verifyCanceled() {
        require(Minecraft.getInstance().screen == parent, "Finishing a canceled load does not replace the restored world setup screen");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
