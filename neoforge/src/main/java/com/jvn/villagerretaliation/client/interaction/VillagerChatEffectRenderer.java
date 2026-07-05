package com.jvn.villagerretaliation.client.interaction;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.dialogue.normal.DialogueTextEffects;
import com.jvn.villagerretaliation.dialogue.normal.DialogueTextSegment;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.ChatVisiblity;

public final class VillagerChatEffectRenderer {
    private static final int MESSAGE_INDENT = 4;
    private static final int BOTTOM_MARGIN = 40;
    private static final int CHAT_X_OFFSET = 4;
    private static final long CHAT_REAPPEAR_FADE_MILLIS = 260L;
    static final int STATIC_EFFECT_TEXT_COLOR = 0x010101;
    private static long reappearFadeStartMillis = -1L;

    private VillagerChatEffectRenderer() {
    }

    public static void startReappearFade() {
        reappearFadeStartMillis = net.minecraft.Util.getMillis();
    }

    public static boolean shouldHijack(Minecraft minecraft) {
        return minecraft != null
                && minecraft.gui != null
                && minecraft.gui.getChat() != null
                && (VillagerAnimatedChatText.hasTrackedEffects() || reappearFadeActive());
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
        double fadeAlpha = reappearFadeAlpha();
        double lineSpacing = minecraft.options.chatLineSpacing().get();
        int lineHeight = chat.getLineHeight();
        int textYAdjust = (int) Math.round(-8.0 * (lineSpacing + 1.0) + 4.0 * lineSpacing);
        int visibleLineCount = 0;

        graphics.pose().pushPose();
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.pose().translate(CHAT_X_OFFSET, 0.0F, 0.0F);
        int chatBottom = Mth.floor((float) (screenHeight - BOTTOM_MARGIN) / scale);

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

            LineText lineText = collect(line.content());
            graphics.pose().pushPose();
            graphics.pose().translate(0.0F, 0.0F, 50.0F);
            List<DialogueTextSegment> effectSegments = VillagerRetaliationConfig.DISABLE_DIALOGUE_TEXT_EFFECTS.get()
                    ? List.of()
                    : VillagerAnimatedChatText.segmentsForLine(lineText.text());
            if (!effectSegments.isEmpty()) {
                drawAnimatedText(graphics, minecraft.font, lineText.glyphs(), effectSegments, textTop, textAlpha, minecraft.gui.getGuiTicks());
            } else {
                graphics.drawString(minecraft.font, line.content(), 0, textTop, 0xFFFFFF | (textAlpha << 24));
            }
            graphics.pose().popPose();
        }

        if (chat.isChatFocused()) {
            renderScrollbar(graphics, chat, chatWidth, chatBottom, totalLines, lineHeight, visibleLineCount, fadeAlpha);
        }

        graphics.pose().popPose();
    }

    private static boolean reappearFadeActive() {
        return reappearFadeStartMillis >= 0L && net.minecraft.Util.getMillis() - reappearFadeStartMillis < CHAT_REAPPEAR_FADE_MILLIS;
    }

    private static double reappearFadeAlpha() {
        if (reappearFadeStartMillis < 0L) {
            return 1.0D;
        }
        double progress = Mth.clamp(
                (double) (net.minecraft.Util.getMillis() - reappearFadeStartMillis) / CHAT_REAPPEAR_FADE_MILLIS,
                0.0D,
                1.0D);
        if (progress >= 1.0D) {
            reappearFadeStartMillis = -1L;
            return 1.0D;
        }
        return progress * progress * (3.0D - 2.0D * progress);
    }

    private static void drawAnimatedText(
            GuiGraphics graphics,
            Font font,
            List<Glyph> glyphs,
            List<DialogueTextSegment> effectSegments,
            int y,
            int alpha,
            int tickCount) {
        int x = 0;
        int charIndex = 0;
        for (Glyph glyph : glyphs) {
            DialogueTextEffects effects = effectsAt(effectSegments, charIndex);
            if (glyph.text().isBlank()) {
                x += font.width(glyph.text());
                charIndex += glyph.text().length();
                continue;
            }

            float yOffset = effects.wavy() ? Mth.sin((tickCount + x * 0.55F) * 0.24F) * 1.75F : 0.0F;
            if (effects.jump()) {
                yOffset -= Math.abs(Mth.sin(tickCount * 0.28F - charIndex * 0.55F)) * 2.4F;
            }
            float xOffset = effects.shake() ? Mth.sin((tickCount * 1.7F) + charIndex * 1.31F) * 1.15F : 0.0F;
            int color = colorWithAlpha(glyph.style(), effects, alpha, tickCount, charIndex);
            Style renderStyle = glyph.style().withColor(color & 0x00FFFFFF);
            graphics.pose().pushPose();
            graphics.pose().translate(xOffset, yOffset, 0.0F);
            graphics.drawString(font, Component.literal(glyph.text()).withStyle(renderStyle), x, y, color, false);
            graphics.pose().popPose();
            x += font.width(glyph.text());
            charIndex += glyph.text().length();
        }
    }

    private static DialogueTextEffects effectsAt(List<DialogueTextSegment> segments, int charIndex) {
        int cursor = 0;
        for (DialogueTextSegment segment : segments) {
            int end = cursor + segment.text().length();
            if (charIndex >= cursor && charIndex < end) {
                return segment.effects();
            }
            cursor = end;
        }
        return DialogueTextEffects.NONE;
    }

    private static int colorWithAlpha(Style style, DialogueTextEffects effects, int alpha, int tickCount, int charIndex) {
        TextColor textColor = style.getColor();
        int rgb = effects.color() == null ? (textColor == null ? 0xFFFFFF : textColor.getValue()) : effects.color();
        if (rgb == STATIC_EFFECT_TEXT_COLOR) {
            rgb = 0xFFFFFF;
        }
        int adjustedAlpha = alpha;
        if (effects.rainbow()) {
            rgb = rainbowColor(tickCount * 0.006F + charIndex * 0.025F);
        }
        if (effects.pulse()) {
            float pulse = 0.72F + (Mth.sin(tickCount * 0.22F + charIndex * 0.18F) * 0.5F + 0.5F) * 0.28F;
            adjustedAlpha = Mth.clamp(Math.round(alpha * pulse), 0, 255);
            rgb = scaleColor(rgb, 0.86F + pulse * 0.24F);
        }
        return rgb | (adjustedAlpha << 24);
    }

    static boolean usesAnimatedRenderer(DialogueTextEffects effects) {
        return effects.wavy() || effects.shake() || effects.pulse() || effects.jump() || effects.rainbow();
    }

    private static int scaleColor(int rgb, float scale) {
        int red = Mth.clamp(Math.round(((rgb >> 16) & 0xFF) * scale), 0, 255);
        int green = Mth.clamp(Math.round(((rgb >> 8) & 0xFF) * scale), 0, 255);
        int blue = Mth.clamp(Math.round((rgb & 0xFF) * scale), 0, 255);
        return (red << 16) | (green << 8) | blue;
    }

    private static int rainbowColor(float progress) {
        float hue = progress - (float) Math.floor(progress);
        float scaled = hue * 6.0F;
        int sector = (int) Math.floor(scaled);
        float fraction = scaled - sector;
        float saturation = 0.58F;
        float value = 0.95F;
        float p = value * (1.0F - saturation);
        float q = value * (1.0F - saturation * fraction);
        float t = value * (1.0F - saturation * (1.0F - fraction));
        return switch (Math.floorMod(sector, 6)) {
            case 0 -> rgb(value, t, p);
            case 1 -> rgb(q, value, p);
            case 2 -> rgb(p, value, t);
            case 3 -> rgb(p, q, value);
            case 4 -> rgb(t, p, value);
            default -> rgb(value, p, q);
        };
    }

    private static int rgb(float red, float green, float blue) {
        return (Math.round(red * 255.0F) << 16)
                | (Math.round(green * 255.0F) << 8)
                | Math.round(blue * 255.0F);
    }

    private static LineText collect(FormattedCharSequence sequence) {
        List<Glyph> glyphs = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        sequence.accept((index, style, codePoint) -> {
            String glyph = new String(Character.toChars(codePoint));
            text.append(glyph);
            glyphs.add(new Glyph(glyph, style));
            return true;
        });
        return new LineText(text.toString(), glyphs);
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

    private record LineText(String text, List<Glyph> glyphs) {
    }

    private record Glyph(String text, Style style) {
    }
}
