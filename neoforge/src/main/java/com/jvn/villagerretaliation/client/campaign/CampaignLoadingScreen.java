package com.jvn.villagerretaliation.client.campaign;

import com.mojang.logging.LogUtils;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class CampaignLoadingScreen extends Screen {
    private final Screen parent;
    private final Supplier<? extends Screen> load;
    private boolean started;
    private boolean rendered;
    private Screen prepared;

    public CampaignLoadingScreen(Screen parent, Supplier<? extends Screen> load) {
        super(Component.translatable("screen.villagerretaliation.campaign.title"));
        this.parent = parent;
        this.load = load;
    }

    @Override
    protected void init() {
        if (parent.width != width || parent.height != height) parent.resize(minecraft, width, height);
        if (started) return;
        started = true;
        CompletableFuture.supplyAsync(() -> {
            Screen screen = load.get();
            if (screen instanceof CampaignSelectScreen campaigns) campaigns.prepareFirstPage();
            return screen;
        }, Util.backgroundExecutor()).whenCompleteAsync((screen, error) -> {
            if (minecraft.screen != this) {
                if (screen != null) screen.removed();
                return;
            }
            if (error != null) {
                Throwable cause = error instanceof CompletionException ? error.getCause() : error;
                LogUtils.getLogger().warn("Could not open campaign selection", cause);
                minecraft.setScreen(new AlertScreen(() -> minecraft.setScreen(parent),
                        Component.translatable("screen.villagerretaliation.campaign.error"), Component.literal(cause.toString())));
            } else prepared = screen;
        }, minecraft);
    }

    @Override
    public void tick() {
        if (rendered && prepared != null) {
            Screen screen = prepared;
            prepared = null;
            minecraft.setScreen(screen);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        parent.render(graphics, -1, -1, partialTick);
        rendered = true;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public void removed() {
        if (prepared != null) {
            prepared.removed();
            prepared = null;
        }
    }
}
