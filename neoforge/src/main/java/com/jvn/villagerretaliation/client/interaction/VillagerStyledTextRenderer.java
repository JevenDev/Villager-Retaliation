package com.jvn.villagerretaliation.client.interaction;

import com.jvn.villagerretaliation.dialogue.normal.DialogueTextEffects;
import com.jvn.villagerretaliation.dialogue.normal.DialogueTextSegment;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

final class VillagerStyledTextRenderer {
    static final int STATIC_EFFECT_TEXT_COLOR = 0x010101;

    private VillagerStyledTextRenderer() {
    }

    static MutableComponent component(List<DialogueTextSegment> segments, Style baseStyle, Integer fallbackColor) {
        Style safeBaseStyle = baseStyle == null ? Style.EMPTY : baseStyle;
        MutableComponent formatted = Component.empty().withStyle(safeBaseStyle);
        if (segments == null || segments.isEmpty()) {
            return formatted;
        }

        for (DialogueTextSegment segment : segments) {
            appendSegment(formatted, segment, safeBaseStyle, fallbackColor);
        }
        return formatted;
    }

    static void renderLine(
            GuiGraphics graphics,
            Font font,
            FormattedCharSequence line,
            List<DialogueTextSegment> segments,
            int x,
            int y,
            int fallbackColor,
            int alpha,
            int tickCount) {
        int clampedAlpha = Mth.clamp(alpha, 0, 255);
        int fallbackRgb = fallbackColor & 0x00FFFFFF;
        if (!hasAnimatedEffects(segments)) {
            if (containsAnimatedTextMarker(line)) {
                graphics.drawString(font, plainText(line), x, y, fallbackRgb | (clampedAlpha << 24), false);
            } else {
                graphics.drawString(font, line, x, y, fallbackRgb | (clampedAlpha << 24), false);
            }
            return;
        }

        drawAnimatedText(graphics, font, collect(line).glyphs(), segments, x, y, fallbackRgb, clampedAlpha, tickCount);
    }

    static String plainText(FormattedCharSequence sequence) {
        StringBuilder text = new StringBuilder();
        sequence.accept((index, style, codePoint) -> {
            text.appendCodePoint(codePoint);
            return true;
        });
        return text.toString();
    }

    static boolean containsAnimatedTextMarker(FormattedCharSequence sequence) {
        final boolean[] containsMarker = {false};
        sequence.accept((index, style, codePoint) -> {
            TextColor color = style.getColor();
            if (color != null && color.getValue() == STATIC_EFFECT_TEXT_COLOR) {
                containsMarker[0] = true;
                return false;
            }
            return true;
        });
        return containsMarker[0];
    }

    static boolean hasAnimatedEffects(List<DialogueTextSegment> segments) {
        if (segments == null || segments.isEmpty()) {
            return false;
        }
        for (DialogueTextSegment segment : segments) {
            if (segment != null && usesAnimatedRenderer(segment.effects())) {
                return true;
            }
        }
        return false;
    }

    static boolean usesAnimatedRenderer(DialogueTextEffects effects) {
        return effects != null && (effects.wavy()
                || effects.shake()
                || effects.pulse()
                || effects.jump()
                || effects.rainbow());
    }

    private static void appendSegment(
            MutableComponent message,
            DialogueTextSegment segment,
            Style baseStyle,
            Integer fallbackColor) {
        DialogueTextEffects effects = segment.effects();
        if (effects.rainbow()) {
            appendRainbow(message, segment, baseStyle, fallbackColor);
            return;
        }
        if (effects.hasGradient()) {
            appendGradient(message, segment, baseStyle, fallbackColor);
            return;
        }
        message.append(Component.literal(segment.text()).withStyle(styleFor(baseStyle, effects, effects.color(), fallbackColor)));
    }

    private static void appendRainbow(
            MutableComponent message,
            DialogueTextSegment segment,
            Style baseStyle,
            Integer fallbackColor) {
        int index = 0;
        int length = Math.max(1, segment.text().codePointCount(0, segment.text().length()));
        for (int offset = 0; offset < segment.text().length(); ) {
            int codePoint = segment.text().codePointAt(offset);
            String glyph = new String(Character.toChars(codePoint));
            message.append(Component.literal(glyph).withStyle(styleFor(
                    baseStyle,
                    segment.effects(),
                    rainbowColor(index / (float) length),
                    fallbackColor)));
            offset += Character.charCount(codePoint);
            index++;
        }
    }

    private static void appendGradient(
            MutableComponent message,
            DialogueTextSegment segment,
            Style baseStyle,
            Integer fallbackColor) {
        int length = Math.max(1, segment.text().codePointCount(0, segment.text().length()) - 1);
        int index = 0;
        for (int offset = 0; offset < segment.text().length(); ) {
            int codePoint = segment.text().codePointAt(offset);
            String glyph = new String(Character.toChars(codePoint));
            float progress = length == 0 ? 0.0F : index / (float) length;
            int color = lerpColor(segment.effects().gradientStartColor(), segment.effects().gradientEndColor(), progress);
            message.append(Component.literal(glyph).withStyle(styleFor(baseStyle, segment.effects(), color, fallbackColor)));
            offset += Character.charCount(codePoint);
            index++;
        }
    }

    private static Style styleFor(Style baseStyle, DialogueTextEffects effects, Integer color, Integer fallbackColor) {
        Style style = baseStyle
                .withItalic(effects.italic() || Boolean.TRUE.equals(baseStyle.isItalic()))
                .withBold(effects.bold() || Boolean.TRUE.equals(baseStyle.isBold()))
                .withUnderlined(effects.underlined() || Boolean.TRUE.equals(baseStyle.isUnderlined()))
                .withStrikethrough(effects.strikethrough() || Boolean.TRUE.equals(baseStyle.isStrikethrough()))
                .withObfuscated(effects.obfuscated() || Boolean.TRUE.equals(baseStyle.isObfuscated()));
        if (usesAnimatedRenderer(effects)) {
            return style.withColor(STATIC_EFFECT_TEXT_COLOR);
        }

        Integer resolvedColor = color == null ? fallbackColor : color;
        return resolvedColor == null ? style : style.withColor(resolvedColor);
    }

    private static void drawAnimatedText(
            GuiGraphics graphics,
            Font font,
            List<Glyph> glyphs,
            List<DialogueTextSegment> effectSegments,
            int x,
            int y,
            int fallbackRgb,
            int alpha,
            int tickCount) {
        int drawX = x;
        int charIndex = 0;
        EffectCursor effectCursor = new EffectCursor(effectSegments);
        for (Glyph glyph : glyphs) {
            DialogueTextEffects effects = effectCursor.effectsAt(charIndex);
            if (glyph.text().isBlank()) {
                drawX += font.width(glyph.text());
                charIndex += glyph.text().length();
                continue;
            }

            float yOffset = effects.wavy() ? Mth.sin((tickCount + drawX * 0.55F) * 0.24F) * 1.75F : 0.0F;
            if (effects.jump()) {
                yOffset -= Math.abs(Mth.sin(tickCount * 0.28F - charIndex * 0.55F)) * 2.4F;
            }
            float xOffset = effects.shake() ? Mth.sin((tickCount * 1.7F) + charIndex * 1.31F) * 1.15F : 0.0F;
            int color = colorWithAlpha(glyph.style(), effects, fallbackRgb, alpha, tickCount, charIndex);
            Style renderStyle = glyph.style().withColor(color & 0x00FFFFFF);
            graphics.pose().pushPose();
            graphics.pose().translate(xOffset, yOffset, 0.0F);
            graphics.drawString(font, Component.literal(glyph.text()).withStyle(renderStyle), drawX, y, color, false);
            graphics.pose().popPose();
            drawX += font.width(glyph.text());
            charIndex += glyph.text().length();
        }
    }

    private static int colorWithAlpha(
            Style style,
            DialogueTextEffects effects,
            int fallbackRgb,
            int alpha,
            int tickCount,
            int charIndex) {
        TextColor textColor = style.getColor();
        int rgb = effects.color() == null ? (textColor == null ? fallbackRgb : textColor.getValue()) : effects.color();
        if (rgb == STATIC_EFFECT_TEXT_COLOR) {
            rgb = fallbackRgb;
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

    static int lerpColor(int start, int end, float progress) {
        float clamped = Math.clamp(progress, 0.0F, 1.0F);
        int red = Math.round(((start >> 16) & 0xFF) + (((end >> 16) & 0xFF) - ((start >> 16) & 0xFF)) * clamped);
        int green = Math.round(((start >> 8) & 0xFF) + (((end >> 8) & 0xFF) - ((start >> 8) & 0xFF)) * clamped);
        int blue = Math.round((start & 0xFF) + ((end & 0xFF) - (start & 0xFF)) * clamped);
        return (red << 16) | (green << 8) | blue;
    }

    private static int scaleColor(int rgb, float scale) {
        int red = Mth.clamp(Math.round(((rgb >> 16) & 0xFF) * scale), 0, 255);
        int green = Mth.clamp(Math.round(((rgb >> 8) & 0xFF) * scale), 0, 255);
        int blue = Mth.clamp(Math.round((rgb & 0xFF) * scale), 0, 255);
        return (red << 16) | (green << 8) | blue;
    }

    static int rainbowColor(float progress) {
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

    private record LineText(String text, List<Glyph> glyphs) {
    }

    private record Glyph(String text, Style style) {
    }

    private static final class EffectCursor {
        private final List<DialogueTextSegment> segments;
        private int segmentIndex;
        private int segmentStart;

        private EffectCursor(List<DialogueTextSegment> segments) {
            this.segments = segments == null ? List.of() : segments;
        }

        private DialogueTextEffects effectsAt(int charIndex) {
            while (this.segmentIndex < this.segments.size()) {
                DialogueTextSegment segment = this.segments.get(this.segmentIndex);
                if (segment == null) {
                    this.segmentIndex++;
                    continue;
                }
                int segmentEnd = this.segmentStart + segment.text().length();
                if (charIndex < segmentEnd) {
                    return segment.effects();
                }
                this.segmentStart = segmentEnd;
                this.segmentIndex++;
            }
            return DialogueTextEffects.NONE;
        }
    }
}
