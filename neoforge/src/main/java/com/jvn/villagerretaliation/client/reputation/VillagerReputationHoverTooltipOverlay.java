package com.jvn.villagerretaliation.client.reputation;

import com.mojang.blaze3d.systems.RenderSystem;
import com.jvn.villagerretaliation.client.interaction.ClientVillagerConversationState;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.network.VillagerReputationRequestPayload;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.network.PacketDistributor;

public final class VillagerReputationHoverTooltipOverlay {
    private static final int TOOLTIP_OFFSET_Y = 50;
    private static final float FADE_SPEED = 0.72F;
    private static final long REQUEST_COOLDOWN_TICKS = 10L;
    private static final int ICON_SIZE = 16;
    private static final int ICON_TEXT_GAP = 4;
    private static final int TOOLTIP_Z = 400;
    private static final int BORDER_TOP = 0x505000FF;
    private static final int BORDER_BOTTOM = 0x5028007F;
    private static final int BACKGROUND_TOP = 0xE0100010;
    private static final int BACKGROUND_BOTTOM = 0xE0100010;

    private static float alpha;
    private static float previousAlpha;
    private static HoverEntry lastRenderedEntry;
    private static int lastRequestedEntityId = -1;
    private static long lastRequestGameTime = Long.MIN_VALUE;

    private VillagerReputationHoverTooltipOverlay() {
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (ClientVillagerConversationState.active()) {
            alpha = 0.0F;
            previousAlpha = 0.0F;
            lastRenderedEntry = null;
            return;
        }
        previousAlpha = alpha;
        requestHoveredVillagerReputation(minecraft);
        Optional<HoverEntry> currentEntry = resolveTarget(minecraft);
        currentEntry.ifPresent(entry -> lastRenderedEntry = entry);
        float targetAlpha = currentEntry.isPresent() ? 1.0F : 0.0F;
        alpha += (targetAlpha - alpha) * FADE_SPEED;
        if (Math.abs(targetAlpha - alpha) < 0.01F) {
            alpha = targetAlpha;
        }
        if (alpha <= 0.01F && targetAlpha == 0.0F) {
            lastRenderedEntry = null;
        }
    }

    public static void onRenderGuiLayer(RenderGuiLayerEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!VanillaGuiLayers.HOTBAR.equals(event.getName())) {
            return;
        }
        if (minecraft.screen != null) {
            return;
        }
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(true);
        float renderAlpha = lerp(previousAlpha, alpha, partialTick);
        if (renderAlpha <= 0.01F) {
            return;
        }

        Optional<HoverEntry> hoverEntry = resolveTarget(minecraft);
        HoverEntry entry = hoverEntry.orElse(lastRenderedEntry);
        if (entry == null) {
            return;
        }

        renderTooltip(event.getGuiGraphics(), minecraft, entry, renderAlpha);
    }

    public static void reset() {
        alpha = 0.0F;
        previousAlpha = 0.0F;
        lastRenderedEntry = null;
        lastRequestedEntityId = -1;
        lastRequestGameTime = Long.MIN_VALUE;
    }

    private static void requestHoveredVillagerReputation(Minecraft minecraft) {
        if (minecraft.level == null || minecraft.player == null || ClientVillagerConversationState.active()) {
            return;
        }

        HitResult hitResult = minecraft.hitResult;
        if (!(hitResult instanceof EntityHitResult entityHitResult)) {
            return;
        }

        Entity entity = entityHitResult.getEntity();
        if (!(entity instanceof AbstractVillager villager) || !villager.isAlive() || villager.isBaby()) {
            return;
        }

        long gameTime = minecraft.level.getGameTime();
        if (villager.getId() == lastRequestedEntityId && gameTime - lastRequestGameTime < REQUEST_COOLDOWN_TICKS) {
            return;
        }

        lastRequestedEntityId = villager.getId();
        lastRequestGameTime = gameTime;
        PacketDistributor.sendToServer(new VillagerReputationRequestPayload(villager.getId()));
    }

    private static Optional<HoverEntry> resolveTarget(Minecraft minecraft) {
        if (minecraft.level == null
                || minecraft.player == null
                || minecraft.options.hideGui
                || minecraft.screen != null
                || ClientVillagerConversationState.active()) {
            return Optional.empty();
        }

        if (VillagerRetaliationConfig.ENABLE_VILLAGER_REPUTATION.get() == false) {
            return Optional.empty();
        }

        if (VillagerRetaliationConfig.VILLAGER_REPUTATION_HOVER_TOOLTIP_REQUIRES_EMERALD.get()
                && !holdingEmerald(minecraft.player)) {
            return Optional.empty();
        }

        HitResult hitResult = minecraft.hitResult;
        if (!(hitResult instanceof EntityHitResult entityHitResult)) {
            return Optional.empty();
        }

        Entity entity = entityHitResult.getEntity();
        if (!(entity instanceof AbstractVillager villager) || !villager.isAlive()) {
            return Optional.empty();
        }

        return VillagerReputationClientCache.get(villager.getUUID(), villager.getId())
                .map(entry -> new HoverEntry(entry.reputation(), entry.level()));
    }

    private static boolean holdingEmerald(Player player) {
        return player.getMainHandItem().is(Items.EMERALD) || player.getOffhandItem().is(Items.EMERALD);
    }

    private static void renderTooltip(GuiGraphics graphics, Minecraft minecraft, HoverEntry entry, float alphaFactor) {
        if (alphaFactor <= 0.01F) {
            return;
        }
        Font font = minecraft.font;
        String label = I18n.get("villagerretaliation.reputation.value_format", entry.reputation());
        int textWidth = font.width(label);
        int tooltipWidth = ICON_SIZE + ICON_TEXT_GAP + textWidth;
        int tooltipHeight = ICON_SIZE;
        int centerX = minecraft.getWindow().getGuiScaledWidth() / 2;
        int centerY = minecraft.getWindow().getGuiScaledHeight() / 2;
        int left = centerX - tooltipWidth / 2;
        int top = centerY + TOOLTIP_OFFSET_Y;
        int right = left + tooltipWidth;
        int bottom = top + tooltipHeight;

        int bgTop = withAlpha(BACKGROUND_TOP, alphaFactor);
        int bgBottom = withAlpha(BACKGROUND_BOTTOM, alphaFactor);
        int borderTop = withAlpha(BORDER_TOP, alphaFactor);
        int borderBottom = withAlpha(BORDER_BOTTOM, alphaFactor);

        TooltipRenderUtil.renderTooltipBackground(
                graphics,
                left,
                top,
                tooltipWidth,
                tooltipHeight,
                TOOLTIP_Z,
                bgTop,
                bgBottom,
                borderTop,
                borderBottom
        );

        int iconX = left;
        int iconY = top;
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, TOOLTIP_Z);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.setColor(1.0F, 1.0F, 1.0F, alphaFactor);
        graphics.blit(VillagerReputationIconSet.iconFor(entry.level()), iconX, iconY, 0, 0, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);

        int textX = iconX + ICON_SIZE + ICON_TEXT_GAP;
        int textY = top + Math.round((tooltipHeight - font.lineHeight) / 2.0F);
        graphics.drawString(font, label, textX, textY, withAlpha(0xFFFFFFFF, alphaFactor), false);
        graphics.pose().popPose();
    }

    private static float lerp(float start, float end, float delta) {
        return start + (end - start) * delta;
    }

    private static int withAlpha(int color, float alphaFactor) {
        int alphaChannel = color >>> 24;
        int adjustedAlpha = Math.max(0, Math.min(255, Math.round(alphaChannel * alphaFactor)));
        return adjustedAlpha << 24 | (color & 0x00FFFFFF);
    }

    private record HoverEntry(int reputation, com.jvn.villagerretaliation.reputation.VillagerReputationLevel level) {
    }
}
