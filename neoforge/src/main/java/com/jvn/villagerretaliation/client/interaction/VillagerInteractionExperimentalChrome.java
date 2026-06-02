package com.jvn.villagerretaliation.client.interaction;

import com.jvn.toucanlib.client.ToucanColors;
import com.jvn.toucanlib.client.ToucanEasing;
import com.jvn.toucanlib.client.ToucanGuiText;
import com.jvn.villagerretaliation.client.ui.VillagerClientUiUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

final class VillagerInteractionExperimentalChrome {
    private static final float NAME_TEXT_SCALE = 1.85F;
    private static final float NAME_DETAIL_TEXT_SCALE = 1.4F;
    private static final int NAME_LINE_GAP = 5;
    private static final float EXIT_DURATION_MILLIS = 860.0F;
    private static final float TEXT_FADE_IN_DURATION_MILLIS = 320.0F;
    private static final float TEXT_ALPHA_DRAW_THRESHOLD = 0.04F;

    private static long backdropAnimationStartMillis = -1L;
    private static long backdropExitStartMillis = -1L;
    private static List<ExitTextElement> exitTextElements = List.of();
    private static List<ExitFadeTextElement> exitFadeTextElements = List.of();
    private static List<ExitFadeRectElement> exitFadeRectElements = List.of();
    private static ExitSkillsPanel exitSkillsPanel;
    private static VillagerProfessionUiColors.ColorPair professionColors = VillagerProfessionUiColors.DEFAULT_COLORS;

    private VillagerInteractionExperimentalChrome() {
    }

    static void resetAnimation() {
        resetAnimation(VillagerProfessionUiColors.DEFAULT_COLORS);
    }

    static void resetAnimation(VillagerProfessionUiColors.ColorPair colors) {
        backdropAnimationStartMillis = Util.getMillis();
        backdropExitStartMillis = -1L;
        exitTextElements = List.of();
        exitFadeTextElements = List.of();
        exitFadeRectElements = List.of();
        exitSkillsPanel = null;
        professionColors = colors == null ? VillagerProfessionUiColors.DEFAULT_COLORS : colors;
    }

    static void startExitAnimation(
            List<ExitTextElement> textElements,
            List<ExitFadeTextElement> fadeTextElements,
            List<ExitFadeRectElement> fadeRectElements) {
        startExitAnimation(textElements, fadeTextElements, fadeRectElements, null);
    }

    static void startExitAnimation(
            List<ExitTextElement> textElements,
            List<ExitFadeTextElement> fadeTextElements,
            List<ExitFadeRectElement> fadeRectElements,
            ExitSkillsPanel skillsPanel) {
        if (backdropExitStartMillis < 0L) {
            backdropExitStartMillis = Util.getMillis();
            exitTextElements = new ArrayList<>(textElements);
            exitFadeTextElements = new ArrayList<>(fadeTextElements);
            exitFadeRectElements = new ArrayList<>(fadeRectElements);
            exitSkillsPanel = skillsPanel;
        }
    }

    static boolean exitAnimationRunning() {
        return backdropExitStartMillis >= 0L && !exitAnimationComplete();
    }

    static boolean exitAnimationComplete() {
        return backdropExitStartMillis >= 0L
                && Util.getMillis() - backdropExitStartMillis >= EXIT_DURATION_MILLIS;
    }

    static void renderBackdrop(GuiGraphics graphics, int width, int height, float veilTop, int mouseX, int mouseY) {
        long now = Util.getMillis();
        if (backdropAnimationStartMillis < 0L) {
            backdropAnimationStartMillis = now;
        }
        float elapsedMillis = now - backdropAnimationStartMillis;
        float exitElapsedMillis = backdropExitStartMillis < 0L ? -1.0F : now - backdropExitStartMillis;

        VillagerClientUiUtil.pushGuiLayer(graphics, VillagerClientUiUtil.hudLayerZ());
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        if (exitSkillsPanel != null && exitElapsedMillis >= 0.0F) {
            renderExitSkillsPanel(graphics, exitSkillsPanel, width, height, elapsedMillis, exitElapsedMillis, mouseX, mouseY);
        } else {
            VillagerInteractionScreenShaderRenderer.renderExperimentalChrome(
                    graphics,
                    width,
                    height,
                    elapsedMillis,
                    exitElapsedMillis,
                    mouseX,
                    mouseY,
                    professionColors);
        }
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        if (exitElapsedMillis >= 0.0F) {
            renderExitText(graphics, exitElapsedMillis);
            renderExitFadeChrome(graphics, exitElapsedMillis);
        }
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
        VillagerClientUiUtil.popGuiLayer(graphics);
    }

    private static void renderExitSkillsPanel(
            GuiGraphics graphics,
            ExitSkillsPanel panel,
            int width,
            int height,
            float elapsedMillis,
            float exitElapsedMillis,
            int mouseX,
            int mouseY) {
        VillagerInteractionScreenShaderRenderer.renderExperimentalSkillsPanel(
                graphics,
                panel.left(),
                panel.top(),
                panel.right(),
                panel.bottom(),
                chromeAlpha(),
                (Util.getMillis() % 1_000_000L) / 50.0F,
                panel.elapsedMillis(),
                exitElapsedMillis,
                elapsedMillis,
                exitElapsedMillis,
                width,
                height,
                mouseX,
                mouseY,
                professionColors,
                false);
    }

    static void renderFocus(Context context, GuiGraphics graphics, int mouseX, int mouseY) {
        Font font = context.font();
        int right = context.infoRight();
        float nameScale = NAME_TEXT_SCALE * context.experimentalTextScale();
        float detailScale = NAME_DETAIL_TEXT_SCALE * context.experimentalTextScale();
        int lineGap = context.experimentalUnit(NAME_LINE_GAP);
        int walletY = context.infoBottom() - Math.round(font.lineHeight * detailScale);
        int reputationY = walletY - lineGap - Math.round(font.lineHeight * detailScale);
        int professionY = reputationY - lineGap - Math.round(font.lineHeight * detailScale);
        int nameY = professionY - lineGap - Math.round(font.lineHeight * nameScale);

        drawAnimatedRightAlignedScaled(
                graphics,
                font,
                context.villagerName(),
                right,
                nameY,
                context.moodColor(),
                nameScale,
                textFadeInAlpha());
        drawAnimatedRightAlignedScaled(
                graphics,
                font,
                context.professionName(),
                right,
                professionY,
                context.infoSecondaryColor(),
                detailScale,
                textFadeInAlpha());
        drawAnimatedRightAlignedScaled(
                graphics,
                font,
                context.reputationText(),
                right,
                reputationY,
                context.reputationColor(),
                detailScale,
                textFadeInAlpha());
        drawAnimatedRightAlignedScaled(
                graphics,
                font,
                context.walletText(),
                right,
                walletY,
                context.walletColor(),
                detailScale,
                textFadeInAlpha());
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

    static void renderWalletTooltip(Context context, GuiGraphics graphics, int mouseX, int mouseY) {
        if (!isPointInsideWallet(context, mouseX, mouseY)) {
            return;
        }

        List<Component> tooltip = new ArrayList<>();
        tooltip.add(Component.literal(context.walletTooltipTitle()).withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.literal(context.walletTooltipBody()).withStyle(ChatFormatting.GRAY));
        graphics.renderComponentTooltip(context.font(), tooltip, mouseX, mouseY);
    }

    private static boolean isPointInsideName(Context context, double mouseX, double mouseY) {
        Font font = context.font();
        float nameScale = NAME_TEXT_SCALE * context.experimentalTextScale();
        float detailScale = NAME_DETAIL_TEXT_SCALE * context.experimentalTextScale();
        int lineGap = context.experimentalUnit(NAME_LINE_GAP);
        int nameWidth = Math.round(font.width(context.villagerName()) * nameScale);
        int nameLeft = context.infoRight() - nameWidth;
        int walletY = context.infoBottom() - Math.round(font.lineHeight * detailScale);
        int reputationY = walletY - lineGap - Math.round(font.lineHeight * detailScale);
        int professionY = reputationY - lineGap - Math.round(font.lineHeight * detailScale);
        int nameTop = professionY - lineGap - Math.round(font.lineHeight * nameScale);
        int nameHeight = Math.round(font.lineHeight * nameScale);
        return mouseX >= nameLeft - 4
                && mouseX <= nameLeft + nameWidth + 4
                && mouseY >= nameTop - 3
                && mouseY <= nameTop + nameHeight + 3;
    }

    private static boolean isPointInsideWallet(Context context, double mouseX, double mouseY) {
        Font font = context.font();
        float detailScale = NAME_DETAIL_TEXT_SCALE * context.experimentalTextScale();
        int walletWidth = Math.round(font.width(context.walletText()) * detailScale);
        int walletLeft = context.infoRight() - walletWidth;
        int walletTop = context.infoBottom() - Math.round(font.lineHeight * detailScale);
        int walletHeight = Math.round(font.lineHeight * detailScale);
        return mouseX >= walletLeft - 4
                && mouseX <= walletLeft + walletWidth + 4
                && mouseY >= walletTop - 3
                && mouseY <= walletTop + walletHeight + 3;
    }

    private static void drawAnimatedRightAlignedScaled(
            GuiGraphics graphics,
            Font font,
            String text,
            int right,
            int y,
            int color,
            float scale,
            float alpha) {
        if (!shouldDrawText(alpha)) {
            return;
        }

        ToucanGuiText.drawRightAlignedScaledString(graphics, font, text, right, y, ToucanColors.multiplyAlpha(color, alpha), scale);
    }

    private static void renderExitText(GuiGraphics graphics, float exitElapsedMillis) {
        if (exitTextElements.isEmpty()) {
            return;
        }

        Font font = Minecraft.getInstance().font;
        float progress = normalizedProgress(exitElapsedMillis, 0.0F, 540.0F);
        if (progress <= 0.0F) {
            return;
        }

        float fall = easeInCubic(progress);
        float fade = 1.0F - smoothstep(normalizedProgress(exitElapsedMillis, 650.0F, 170.0F));
        if (!shouldDrawText(fade)) {
            return;
        }

        for (ExitTextElement element : exitTextElements) {
            int color = ToucanColors.multiplyAlpha(element.color(), fade);
            float x = element.x() + element.driftX() * fall;
            if (element.rightAligned()) {
                x -= font.width(element.text()) * element.scale();
            }
            float y = element.y() + element.fallDistance() * fall;
            float scale = element.scale() * (1.0F + progress * 0.035F);
            graphics.pose().pushPose();
            graphics.pose().translate(x, y, 0.0F);
            graphics.pose().scale(scale, scale, 1.0F);
            graphics.drawString(font, element.text(), 0, 0, color, false);
            graphics.pose().popPose();
        }
    }

    private static void renderExitFadeChrome(GuiGraphics graphics, float exitElapsedMillis) {
        float alpha = 1.0F - smoothstep(normalizedProgress(exitElapsedMillis, 0.0F, 260.0F));
        if (alpha <= 0.0F) {
            return;
        }

        Font font = Minecraft.getInstance().font;
        for (ExitFadeRectElement element : exitFadeRectElements) {
            graphics.fill(
                    element.left(),
                    element.top(),
                    element.right(),
                    element.bottom(),
                    ToucanColors.multiplyAlpha(element.color(), alpha * element.alpha()));
        }
        for (ExitFadeTextElement element : exitFadeTextElements) {
            if (!shouldDrawText(alpha)) {
                continue;
            }
            graphics.pose().pushPose();
            graphics.pose().translate(element.x(), element.y(), 0.0F);
            graphics.pose().scale(element.scale(), element.scale(), 1.0F);
            graphics.drawString(font, element.text(), 0, 0, ToucanColors.multiplyAlpha(element.color(), alpha), false);
            graphics.pose().popPose();
        }
    }

    private static float normalizedProgress(float elapsedMillis, float delayMillis, float durationMillis) {
        return Mth.clamp((elapsedMillis - delayMillis) / durationMillis, 0.0F, 1.0F);
    }

    static float textEntranceProgress(float delayMillis, float durationMillis) {
        long now = Util.getMillis();
        if (backdropAnimationStartMillis < 0L) {
            backdropAnimationStartMillis = now;
        }
        return normalizedProgress(now - backdropAnimationStartMillis, delayMillis, durationMillis);
    }

    static float textFadeInAlpha() {
        return ToucanEasing.smoothstep(textEntranceProgress(0.0F, TEXT_FADE_IN_DURATION_MILLIS));
    }

    static float backdropElapsedMillis() {
        long now = Util.getMillis();
        if (backdropAnimationStartMillis < 0L) {
            backdropAnimationStartMillis = now;
        }
        return now - backdropAnimationStartMillis;
    }

    static float backdropExitElapsedMillis() {
        return backdropExitStartMillis < 0L ? -1.0F : Util.getMillis() - backdropExitStartMillis;
    }

    static float chromeAlpha() {
        return textFadeInAlpha();
    }

    static boolean shouldDrawText(float alpha) {
        return alpha > TEXT_ALPHA_DRAW_THRESHOLD;
    }

    record ExitTextElement(
            String text,
            int x,
            int y,
            int color,
            float scale,
            float delayMillis,
            float driftX,
            float fallDistance,
            boolean rightAligned) {
    }

    record ExitFadeTextElement(String text, int x, int y, int color, float scale) {
    }

    record ExitFadeRectElement(int left, int top, int right, int bottom, int color, float alpha) {
    }

    record ExitSkillsPanel(int left, int top, int right, int bottom, float elapsedMillis) {
    }

    private static float easeInCubic(float progress) {
        return progress * progress * progress;
    }

    static float smoothstep(float progress) {
        return ToucanEasing.smoothstep(progress);
    }

    interface Context {
        Font font();

        int infoRight();

        int infoBottom();

        int screenWidth();

        int screenHeight();

        String villagerName();

        String professionName();

        String genderText();

        String moodText();

        String reputationText();

        String walletText();

        String walletTooltipTitle();

        String walletTooltipBody();

        int moodColor();

        int reputationColor();

        int walletColor();

        int infoSecondaryColor();

        int infoLabelColor();

        float experimentalTextScale();

        int experimentalUnit(int value);
    }

}
