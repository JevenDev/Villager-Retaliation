package com.jvn.villagerretaliation.client.interaction;

import com.google.common.collect.MapMaker;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.dialogue.normal.DialogueTextEffects;
import com.jvn.villagerretaliation.dialogue.normal.DialogueTextSegment;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

public final class VillagerStyledTextRenderer {
    // color identity survives style inheritance and line wrapping without changing the visible color
    private static final Map<TextColor, GlyphEffect> GLYPH_EFFECTS = new MapMaker().weakKeys().makeMap();

    private VillagerStyledTextRenderer() {
    }

    public static int renderSingleLine(GuiGraphics graphics, Font font, String text, int color, int tickCount) {
        var segments = DialogueTextSegment.parse(text.replace('\n', ' ').replace('\r', ' '), DialogueTextEffects.NONE);
        var line = component(segments, Style.EMPTY, color).getVisualOrderText();
        renderLine(graphics, font, line, 3, 3, color, 255, tickCount);
        return font.width(line);
    }

    public static int measureWrapped(Font font, String text, int width) {
        var segments = DialogueTextSegment.parse(text, DialogueTextEffects.NONE);
        return font.split(component(segments, Style.EMPTY, 0xE3E7EC), width).size() * (font.lineHeight + 3) + 3;
    }

    public static int renderWrapped(GuiGraphics graphics, Font font, String text, int width, int height, int scroll, int color, int tickCount) {
        List<DialogueTextSegment> segments = DialogueTextSegment.parse(text, DialogueTextEffects.NONE);
        int y = 3 - scroll;
        for (FormattedCharSequence line : font.split(component(segments, Style.EMPTY, color), width)) {
            if (y + font.lineHeight >= 0 && y < height)
                renderLine(graphics, font, line, 3, y, color, 255, tickCount);
            y += font.lineHeight + 3;
        }
        return y + scroll + 3;
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

    static void renderOutlinedLine(GuiGraphics graphics, Font font, String text, List<DialogueTextSegment> segments,
            int x, int y, int color, int tickCount) {
        var styled = (segments == null || segments.isEmpty() ? Component.literal(text)
                : component(segments, Style.EMPTY, color & 0x00FFFFFF)).getVisualOrderText();
        renderOutlinedLine(graphics, font, styled, x, y, color, tickCount);
    }

    static void renderOutlinedLine(GuiGraphics graphics, Font font, FormattedCharSequence styled,
            int x, int y, int color, int tickCount) {
        renderGlyphs(graphics, font, styled, x, y, color, tickCount, false, true,
                (glyph, drawX, drawY, tint) -> graphics.drawString(font, glyph, drawX, drawY, tint, false));
    }

    static Component slice(Component component, int start, int end) {
        MutableComponent result = Component.empty();
        int[] offset = {0};
        component.visit((style, text) -> {
            int from = Math.max(0, start - offset[0]);
            int to = Math.min(text.length(), end - offset[0]);
            if (from < to) result.append(Component.literal(text.substring(from, to)).withStyle(style));
            offset[0] += text.length();
            return java.util.Optional.empty();
        }, Style.EMPTY);
        return result;
    }

    static int horizontalPadding(List<DialogueTextSegment> segments) {
        float padding = 0;
        for (var segment : segments) {
            var effects = segment.effects();
            if (effects.shake()) padding = Math.max(padding, 1.15F * effects.shakeAnimation().strength());
        }
        return Mth.ceil(padding) + 1;
    }

    static int verticalPadding(List<DialogueTextSegment> segments) {
        float padding = 0;
        for (var segment : segments) {
            var effects = segment.effects();
            padding = Math.max(padding, (effects.wavy() ? 1.75F * effects.waveAnimation().strength() : 0)
                    + (effects.jump() ? 2.4F * effects.jumpAnimation().strength() : 0));
        }
        return Mth.ceil(padding) + 1;
    }

    static void renderLine(
            GuiGraphics graphics,
            Font font,
            FormattedCharSequence line,
            int x,
            int y,
            int fallbackColor,
            int alpha,
            int tickCount) {
        int color = (fallbackColor & 0x00FFFFFF) | (Mth.clamp(alpha, 0, 255) << 24);
        if (!hasGlyphEffects(line) || VillagerRetaliationConfig.DISABLE_DIALOGUE_TEXT_EFFECTS.get()) {
            if ((color >>> 24) > 3) graphics.drawString(font, line, x, y, color, false);
            return;
        }
        renderGlyphs(graphics, font, line, x, y, color, tickCount, false, false,
                (glyph, drawX, drawY, tint) -> graphics.drawString(font, glyph, drawX, drawY, tint, false));
    }

    @FunctionalInterface
    public interface GlyphDrawer {
        int draw(FormattedCharSequence text, int x, int y, int color);
    }

    public static int renderChatLine(GuiGraphics graphics, Font font, FormattedCharSequence line,
            int x, int y, int color, int tickCount, GlyphDrawer drawer) {
        if (!hasGlyphEffects(line) || VillagerRetaliationConfig.DISABLE_DIALOGUE_TEXT_EFFECTS.get()) {
            return drawer.draw(line, x, y, color);
        }
        return renderGlyphs(graphics, font, line, x, y, color, tickCount, true, false, drawer);
    }

    static String plainText(FormattedCharSequence sequence) {
        StringBuilder text = new StringBuilder();
        sequence.accept((index, style, codePoint) -> {
            text.appendCodePoint(codePoint);
            return true;
        });
        return text.toString();
    }

    static boolean hasGlyphEffects(FormattedCharSequence sequence) {
        return !sequence.accept((index, style, codePoint) -> glyphEffect(style) == null);
    }

    public static boolean sameGlyphEffect(Style first, Style second) {
        return first.getColor() == second.getColor() || Objects.equals(glyphEffect(first), glyphEffect(second));
    }

    static GlyphEffect glyphEffect(Style style) {
        return style.getColor() == null ? null : GLYPH_EFFECTS.get(style.getColor());
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
        if (!usesAnimatedRenderer(effects) && !effects.hasGradient()) {
            message.append(Component.literal(segment.text()).withStyle(styleFor(baseStyle, effects, effects.color(), fallbackColor)));
            return;
        }
        int length = segment.text().codePointCount(0, segment.text().length());
        int index = 0;
        for (int offset = 0; offset < segment.text().length(); ) {
            int codePoint = segment.text().codePointAt(offset);
            Integer color = effects.color();
            if (effects.rainbow()) {
                color = rainbowColor(index / (float) Math.max(1, length));
            } else if (effects.hasGradient()) {
                color = lerpColor(effects.gradientStartColor(), effects.gradientEndColor(), index / (float) Math.max(1, length - 1));
            }
            Style style = styleFor(baseStyle, effects, color, fallbackColor);
            if (usesAnimatedRenderer(effects)) {
                int rgb = style.getColor() == null ? 0xFFFFFF : style.getColor().getValue();
                style = style.withColor((TextColor) null).withColor(rgb);
                GLYPH_EFFECTS.put(style.getColor(), new GlyphEffect(effects, index));
            }
            message.append(Component.literal(new String(Character.toChars(codePoint))).withStyle(style));
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
        Integer resolvedColor = color == null ? fallbackColor : color;
        return resolvedColor == null ? style : style.withColor(resolvedColor);
    }

    private static int renderGlyphs(GuiGraphics graphics, Font font, FormattedCharSequence line,
            int x, int y, int color, int tickCount, boolean shadow, boolean outline, GlyphDrawer drawer) {
        int alpha = color >>> 24;
        if (alpha <= 3) return x;
        boolean animate = !VillagerRetaliationConfig.DISABLE_DIALOGUE_TEXT_EFFECTS.get();
        int drawX = x;
        for (Glyph glyph : collect(line)) {
            GlyphEffect metadata = animate ? glyphEffect(glyph.style()) : null;
            DialogueTextEffects effects = metadata == null ? DialogueTextEffects.NONE : metadata.effects();
            int index = metadata == null ? 0 : metadata.index();
            float yOffset = waveOffset(effects, tickCount, index * 6) - jumpOffset(effects, tickCount, index);
            float xOffset = shakeOffset(effects, tickCount, index);
            int tint = colorWithAlpha(glyph.style(), effects, color & 0x00FFFFFF, alpha, tickCount, index);
            Style style = glyph.style().withColor(tint & 0x00FFFFFF);
            var text = FormattedCharSequence.forward(glyph.text(), style);
            graphics.pose().pushPose();
            try {
                graphics.pose().translate(xOffset, yOffset, 0.0F);
                if ((tint >>> 24) <= 3) {
                    if (shadow) {
                        // font makes near-zero alpha opaque, but inline renderers still need each glyph
                        graphics.enableScissor(0, 0, 0, 0);
                        try {
                            drawX = drawer.draw(text, drawX, y, tint) - 1;
                        } finally {
                            graphics.disableScissor();
                        }
                    } else {
                        drawX += font.width(text);
                    }
                    continue;
                }
                if (outline) {
                    var border = FormattedCharSequence.forward(glyph.text(), style.withColor(0));
                    int borderColor = tint & 0xFF000000;
                    graphics.drawString(font, border, drawX - 1, y, borderColor, false);
                    graphics.drawString(font, border, drawX + 1, y, borderColor, false);
                    graphics.drawString(font, border, drawX, y - 1, borderColor, false);
                    graphics.drawString(font, border, drawX, y + 1, borderColor, false);
                }
                // retain advances injected by inline glyph renderers, including Chat Heads
                drawX = drawer.draw(text, drawX, y, tint) - (shadow ? 1 : 0);
            } finally {
                graphics.pose().popPose();
            }
        }
        return drawX + (shadow ? 1 : 0);
    }

    static float waveOffset(DialogueTextEffects effects, int tickCount, int drawX) {
        return effects.wavy() ? Mth.sin((tickCount * effects.waveAnimation().speed() + drawX * 0.55F) * 0.24F)
                * 1.75F * effects.waveAnimation().strength() : 0;
    }

    static float shakeOffset(DialogueTextEffects effects, int tickCount, int charIndex) {
        return effects.shake() ? Mth.sin(tickCount * 1.7F * effects.shakeAnimation().speed() + charIndex * 1.31F)
                * 1.15F * effects.shakeAnimation().strength() : 0;
    }

    static float jumpOffset(DialogueTextEffects effects, int tickCount, int charIndex) {
        return effects.jump() ? Math.abs(Mth.sin(tickCount * 0.28F * effects.jumpAnimation().speed() - charIndex * 0.55F))
                * 2.4F * effects.jumpAnimation().strength() : 0;
    }

    static int colorWithAlpha(
            Style style,
            DialogueTextEffects effects,
            int fallbackRgb,
            int alpha,
            int tickCount,
            int charIndex) {
        TextColor textColor = style.getColor();
        int rgb = textColor == null ? (effects.color() == null ? fallbackRgb : effects.color()) : textColor.getValue();
        int adjustedAlpha = alpha;
        if (effects.rainbow()) {
            rgb = rainbowColor(tickCount * 0.006F + charIndex * 0.025F);
        }
        if (effects.pulse()) {
            float pulse = 0.72F + (Mth.sin(tickCount * 0.22F * effects.pulseAnimation().speed() + charIndex * 0.18F) * 0.5F + 0.5F) * 0.28F;
            float strength = effects.pulseAnimation().strength();
            adjustedAlpha = Mth.clamp(Math.round(alpha * (1 + (pulse - 1) * strength)), 0, 255);
            rgb = scaleColor(rgb, Math.max(0, 1 + (0.86F + pulse * 0.24F - 1) * strength));
        }
        return rgb | (adjustedAlpha << 24);
    }

    private static List<Glyph> collect(FormattedCharSequence sequence) {
        List<Glyph> glyphs = new ArrayList<>();
        sequence.accept((index, style, codePoint) -> {
            glyphs.add(new Glyph(new String(Character.toChars(codePoint)), style));
            return true;
        });
        return glyphs;
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

    record GlyphEffect(DialogueTextEffects effects, int index) {
    }

    private record Glyph(String text, Style style) {
    }
}
