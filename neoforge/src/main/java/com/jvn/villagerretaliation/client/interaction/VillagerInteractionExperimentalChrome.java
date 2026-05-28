package com.jvn.villagerretaliation.client.interaction;

import com.jvn.villagerretaliation.client.VillagerRetaliationClientAssets;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

final class VillagerInteractionExperimentalChrome {
    private static final float NAME_TEXT_SCALE = 1.85F;
    private static final float NAME_DETAIL_TEXT_SCALE = 1.4F;
    private static final int NAME_LINE_GAP = 5;
    private static final int OVERLAY_TEXTURE_WIDTH = 1920;
    private static final int OVERLAY_TEXTURE_HEIGHT = 1080;
    private static final ResourceLocation[] BACKDROP_OVERLAYS = {
            VillagerRetaliationClientAssets.EXPERIMENTAL_OVERLAY_1_TEXTURE,
            VillagerRetaliationClientAssets.EXPERIMENTAL_OVERLAY_2_TEXTURE,
            VillagerRetaliationClientAssets.EXPERIMENTAL_OVERLAY_3_TEXTURE,
            VillagerRetaliationClientAssets.EXPERIMENTAL_OVERLAY_4_TEXTURE
    };

    private VillagerInteractionExperimentalChrome() {
    }

    static void renderBackdrop(GuiGraphics graphics, int width, int height, float veilTop) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        for (ResourceLocation overlay : BACKDROP_OVERLAYS) {
            graphics.blit(
                    overlay,
                    0,
                    0,
                    width,
                    height,
                    0.0F,
                    0.0F,
                    OVERLAY_TEXTURE_WIDTH,
                    OVERLAY_TEXTURE_HEIGHT,
                    OVERLAY_TEXTURE_WIDTH,
                    OVERLAY_TEXTURE_HEIGHT
            );
        }
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
    }

    static void renderFocus(Context context, GuiGraphics graphics, int mouseX, int mouseY) {
        Font font = context.font();
        int right = context.infoRight();
        float nameScale = NAME_TEXT_SCALE * context.experimentalTextScale();
        float detailScale = NAME_DETAIL_TEXT_SCALE * context.experimentalTextScale();
        int lineGap = context.experimentalUnit(NAME_LINE_GAP);
        int reputationY = context.infoBottom() - Math.round(font.lineHeight * detailScale);
        int professionY = reputationY - lineGap - Math.round(font.lineHeight * detailScale);
        int nameY = professionY - lineGap - Math.round(font.lineHeight * nameScale);

        drawRightAlignedScaled(graphics, font, context.villagerName(), right, nameY, context.moodColor(), nameScale);
        drawRightAlignedScaled(graphics, font, context.professionName(), right, professionY, context.infoSecondaryColor(), detailScale);
        drawRightAlignedScaled(graphics, font, context.reputationText(), right, reputationY, context.infoLabelColor(), detailScale);
    }

    static void renderNameTooltip(Context context, GuiGraphics graphics, int mouseX, int mouseY) {
        if (!isPointInsideName(context, mouseX, mouseY)) {
            return;
        }

        List<Component> tooltip = new ArrayList<>();
        tooltip.add(Component.literal(context.villagerName()).withStyle(ChatFormatting.WHITE));
        tooltip.add(Component.literal(context.moodText()).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal(context.genderText()).withStyle(ChatFormatting.GRAY));
        graphics.renderComponentTooltip(context.font(), tooltip, mouseX, mouseY);
    }

    private static boolean isPointInsideName(Context context, double mouseX, double mouseY) {
        Font font = context.font();
        float nameScale = NAME_TEXT_SCALE * context.experimentalTextScale();
        float detailScale = NAME_DETAIL_TEXT_SCALE * context.experimentalTextScale();
        int lineGap = context.experimentalUnit(NAME_LINE_GAP);
        int nameWidth = Math.round(font.width(context.villagerName()) * nameScale);
        int nameLeft = context.infoRight() - nameWidth;
        int reputationY = context.infoBottom() - Math.round(font.lineHeight * detailScale);
        int professionY = reputationY - lineGap - Math.round(font.lineHeight * detailScale);
        int nameTop = professionY - lineGap - Math.round(font.lineHeight * nameScale);
        int nameHeight = Math.round(font.lineHeight * nameScale);
        return mouseX >= nameLeft - 4
                && mouseX <= nameLeft + nameWidth + 4
                && mouseY >= nameTop - 3
                && mouseY <= nameTop + nameHeight + 3;
    }

    private static void drawRightAlignedScaled(GuiGraphics graphics, Font font, String text, int right, int y, int color, float scale) {
        float scaledWidth = font.width(text) * scale;
        graphics.pose().pushPose();
        graphics.pose().translate(right - scaledWidth, y, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(font, text, 0, 0, color, false);
        graphics.pose().popPose();
    }

    interface Context {
        Font font();

        int infoRight();

        int infoBottom();

        String villagerName();

        String professionName();

        String genderText();

        String moodText();

        String reputationText();

        int moodColor();

        int infoSecondaryColor();

        int infoLabelColor();

        float experimentalTextScale();

        int experimentalUnit(int value);
    }

}
