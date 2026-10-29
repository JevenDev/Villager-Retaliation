package com.jvn.villagerretaliation.client.allegiance;

import com.jvn.toucanlib.client.ToucanHudText;
import com.jvn.villagerretaliation.allegiance.VillageOpinion;
import com.jvn.villagerretaliation.network.VillageEntryPayload;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

public final class VillageEntryOverlay {
    private static final int LIFETIME = 110;
    private static final int TRANSITION = 16;
    private static VillageEntryPayload active;
    private static int age;

    private VillageEntryOverlay() { }

    public static void accept(VillageEntryPayload payload) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !minecraft.level.dimension().location().equals(payload.dimension())) return;
        active = payload;
        age = 0;
    }

    public static Component opinionText(VillageOpinion opinion) {
        return Component.translatable("villagerretaliation.village.opinion",
                Component.translatable("villagerretaliation.reputation.level."
                        + opinion.level().name().toLowerCase(Locale.ROOT)),
                String.format(Locale.ROOT, "%.1f", opinion.score()));
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        var minecraft = Minecraft.getInstance();
        if (active == null) return;
        if (minecraft.level == null || minecraft.player == null || !minecraft.player.isAlive()
                || minecraft.player.isSpectator()
                || !minecraft.level.dimension().location().equals(active.dimension())) {
            active = null;
        } else if (!minecraft.isPaused() && ++age >= LIFETIME) {
            active = null;
        }
    }

    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        active = null;
        age = 0;
    }

    public static void onRenderGuiLayer(RenderGuiLayerEvent.Post event) {
        if (!VanillaGuiLayers.SAVING_INDICATOR.equals(event.getName()) || active == null) return;
        var minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui || minecraft.screen != null || minecraft.player == null) return;
        float elapsed = age + (minecraft.isPaused() ? 0 : event.getPartialTick().getGameTimeDeltaPartialTick(true));
        float progress = Mth.clamp(Math.min(elapsed, LIFETIME - elapsed) / TRANSITION, 0, 1);
        float eased = progress * progress * (3 - 2 * progress);
        int alpha = Math.round(eased * 255);
        if (alpha < 4) return;
        var graphics = event.getGuiGraphics();
        int width = graphics.guiWidth();
        float y = -36 + 64 * eased;
        graphics.pose().pushPose();
        graphics.pose().translate(width / 2.0F, y, 0);
        drawLine(graphics, active.name(), 1.5F, 0, width, (alpha << 24) | 0xFFCC55, alpha << 24);
        drawLine(graphics, opinionText(active.opinion()).getString(), 1, 19, width,
                (alpha << 24) | 0xFFFFFF, alpha << 24);
        graphics.pose().popPose();
    }

    private static void drawLine(GuiGraphics graphics, String text, float preferredScale, int y,
                                 int width, int color, int outline) {
        var font = Minecraft.getInstance().font;
        float scale = Math.min(preferredScale, (width - 24.0F) / Math.max(1, font.width(text)));
        graphics.pose().pushPose();
        graphics.pose().translate(0, y, 0);
        graphics.pose().scale(scale, scale, 1);
        ToucanHudText.drawOutlinedString(graphics, font, text, -font.width(text) / 2, 0, color, outline);
        graphics.pose().popPose();
    }
}
