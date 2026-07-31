package com.jvn.villagerretaliation.client.interaction;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.dialogue.normal.DialogueTextSegment;
import java.util.List;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.neoforged.fml.ModList;

public final class VillagerChatEffectRenderer {
    private static final String CHAT_HEADS_MOD_ID = "chat_heads";
    private static final int MESSAGE_INDENT = 4;
    private static final int BOTTOM_MARGIN = 40;
    private static final int CHAT_X_OFFSET = 4;

    private VillagerChatEffectRenderer() {
    }

    public static void startReappearFade() {
        VillagerInteractionVisibilityFade.fadeIn();
    }

    public static void startDisappearFade() {
        VillagerInteractionVisibilityFade.fadeOut();
    }

    public static boolean shouldHijack(Minecraft minecraft) {
        return minecraft != null
                && minecraft.gui != null
                && minecraft.gui.getChat() != null
                && animatedChatEffectsEnabled()
                && VillagerAnimatedChatText.hasTrackedEffects();
    }

    static boolean animatedChatEffectsEnabled() {
        return !VillagerRetaliationConfig.DISABLE_DIALOGUE_TEXT_EFFECTS.get()
                && !ModList.get().isLoaded(CHAT_HEADS_MOD_ID);
    }

    public static void render(GuiGraphics graphics, Minecraft minecraft) {
        ChatComponent chat = minecraft.gui.getChat();
        if (minecraft.options.chatVisibility().get() == ChatVisiblity.HIDDEN || chat.trimmedMessages.isEmpty()) {
            return;
        }

        int linesPerPage = chat.getLinesPerPage();
        int totalLines = chat.trimmedMessages.size();
        float scale = (float) chat.getScale();
        int chatWidth = Mth.ceil((float) chat.getWidth() / scale);
        int screenHeight = graphics.guiHeight();
        double opacity = minecraft.options.chatOpacity().get() * 0.9F + 0.1F;
        double backgroundOpacity = minecraft.options.textBackgroundOpacity().get();
        double fadeAlpha = 1.0D;
        double lineSpacing = minecraft.options.chatLineSpacing().get();
        int lineHeight = chat.getLineHeight();
        int textYAdjust = (int) Math.round(-8.0 * (lineSpacing + 1.0) + 4.0 * lineSpacing);
        int visibleLineCount = 0;

        graphics.pose().pushPose();
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.pose().translate(CHAT_X_OFFSET, 0.0F, 0.0F);
        int chatBottom = Mth.floor((float) (screenHeight - BOTTOM_MARGIN) / scale);
        VillagerAnimatedChatText.RenderState renderState = VillagerAnimatedChatText.beginRenderFrame();
        consumeScrolledLines(chat, totalLines, renderState);

        for (int index = 0; index + chat.chatScrollbarPos < totalLines && index < linesPerPage; index++) {
            int lineIndex = index + chat.chatScrollbarPos;
            GuiMessage.Line line = chat.trimmedMessages.get(lineIndex);
            int age = minecraft.gui.getGuiTicks() - line.addedTime();
            boolean focused = chat.isChatFocused();
            if (age >= 200 && !focused) {
                continue;
            }

            double timeFactor = focused ? 1.0D : timeFactor(age);
            int textAlpha = (int) (255.0D * timeFactor * opacity * fadeAlpha);
            int backgroundAlpha = (int) (255.0D * timeFactor * backgroundOpacity * fadeAlpha);
            visibleLineCount++;
            if (textAlpha <= 3) {
                continue;
            }

            int lineBottom = chatBottom - index * lineHeight;
            int textTop = lineBottom + textYAdjust;
            graphics.fill(-4, lineBottom - lineHeight, chatWidth + MESSAGE_INDENT, lineBottom, backgroundAlpha << 24);
            if (line.tag() != null) {
                int indicatorColor = line.tag().indicatorColor() | textAlpha << 24;
                graphics.fill(-4, lineBottom - lineHeight, -2, lineBottom, indicatorColor);
            }

            graphics.pose().pushPose();
            graphics.pose().translate(0.0F, 0.0F, 50.0F);
            boolean needsAnimatedRenderer = VillagerStyledTextRenderer.containsAnimatedTextMarker(line.content());
            List<DialogueTextSegment> effectSegments;
            if (VillagerRetaliationConfig.DISABLE_DIALOGUE_TEXT_EFFECTS.get() || !needsAnimatedRenderer) {
                if (line.endOfEntry()) {
                    renderState.segmentsForLine("", true);
                }
                effectSegments = List.of();
            } else {
                effectSegments = renderState.segmentsForLine(VillagerStyledTextRenderer.plainText(line.content()), line.endOfEntry());
            }
            VillagerStyledTextRenderer.renderLine(
                    graphics,
                    minecraft.font,
                    line.content(),
                    effectSegments,
                    0,
                    textTop,
                    0xFFFFFF,
                    textAlpha,
                    minecraft.gui.getGuiTicks());
            graphics.pose().popPose();
        }

        if (chat.isChatFocused()) {
            renderScrollbar(graphics, chat, chatWidth, chatBottom, totalLines, lineHeight, visibleLineCount, fadeAlpha);
        }

        graphics.pose().popPose();
    }

    private static void consumeScrolledLines(
            ChatComponent chat,
            int totalLines,
            VillagerAnimatedChatText.RenderState renderState) {
        if (VillagerRetaliationConfig.DISABLE_DIALOGUE_TEXT_EFFECTS.get()) {
            return;
        }

        int skippedLines = Math.min(chat.chatScrollbarPos, totalLines);
        for (int index = 0; index < skippedLines; index++) {
            GuiMessage.Line line = chat.trimmedMessages.get(index);
            if (!VillagerStyledTextRenderer.containsAnimatedTextMarker(line.content())) {
                if (line.endOfEntry()) {
                    renderState.segmentsForLine("", true);
                }
                continue;
            }
            renderState.segmentsForLine(VillagerStyledTextRenderer.plainText(line.content()), line.endOfEntry());
        }
    }

    private static void renderScrollbar(
            GuiGraphics graphics,
            ChatComponent chat,
            int chatWidth,
            int chatBottom,
            int totalLines,
            int lineHeight,
            int visibleLineCount,
            double fadeAlpha) {
        int fullHeight = totalLines * lineHeight;
        int visibleHeight = visibleLineCount * lineHeight;
        if (fullHeight == visibleHeight) {
            return;
        }

        int scrollOffset = chat.chatScrollbarPos * visibleHeight / totalLines - chatBottom;
        int thumbHeight = visibleHeight * visibleHeight / fullHeight;
        int alpha = Mth.clamp((int) Math.round((scrollOffset > 0 ? 170 : 96) * fadeAlpha), 0, 255);
        int thumbColor = chat.newMessageSinceScroll ? 13382451 : 3355562;
        int scrollbarX = chatWidth + MESSAGE_INDENT;
        graphics.fill(scrollbarX, -scrollOffset, scrollbarX + 2, -scrollOffset - thumbHeight, 100, thumbColor + (alpha << 24));
        graphics.fill(scrollbarX + 2, -scrollOffset, scrollbarX + 1, -scrollOffset - thumbHeight, 100, 13421772 + (alpha << 24));
    }

    private static double timeFactor(int age) {
        double factor = (double) age / 200.0D;
        factor = 1.0D - factor;
        factor *= 10.0D;
        factor = Mth.clamp(factor, 0.0D, 1.0D);
        return factor * factor;
    }

}
