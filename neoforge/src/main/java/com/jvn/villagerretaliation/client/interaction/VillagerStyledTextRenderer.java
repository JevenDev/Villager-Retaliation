package com.jvn.villagerretaliation.client.interaction;

import com.jvn.toucanlib.client.ToucanTextAnimation;
import com.jvn.toucanlib.client.ToucanTextEffects;
import com.jvn.toucanlib.client.ToucanTextRenderer;
import com.jvn.toucanlib.client.ToucanTextSpan;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.dialogue.normal.DialogueTextAnimation;
import com.jvn.villagerretaliation.dialogue.normal.DialogueTextEffects;
import com.jvn.villagerretaliation.dialogue.normal.DialogueTextSegment;
import java.util.List;
import java.util.function.BooleanSupplier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

public final class VillagerStyledTextRenderer {
    private static final BooleanSupplier EFFECTS_ENABLED = () -> !VillagerRetaliationConfig.DISABLE_DIALOGUE_TEXT_EFFECTS.get();

    private VillagerStyledTextRenderer() { }

    public static int renderSingleLine(GuiGraphics graphics, Font font, String text, int color, int tickCount) {
        var segments = DialogueTextSegment.parse(text.replace('\n', ' ').replace('\r', ' '), DialogueTextEffects.NONE);
        var line = component(segments, Style.EMPTY, color).getVisualOrderText();
        ToucanTextRenderer.renderLine(graphics, font, line, 3, 3, color, 255, tickCount);
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
                ToucanTextRenderer.renderLine(graphics, font, line, 3, y, color, 255, tickCount);
            y += font.lineHeight + 3;
        }
        return y + scroll + 3;
    }

    static MutableComponent component(List<DialogueTextSegment> segments, Style baseStyle, Integer fallbackColor) {
        return ToucanTextRenderer.component(spans(segments), baseStyle, fallbackColor, EFFECTS_ENABLED);
    }

    static void renderOutlinedLine(GuiGraphics graphics, Font font, String text, List<DialogueTextSegment> segments,
            int x, int y, int color, int tickCount) {
        var styled = (segments == null || segments.isEmpty() ? Component.literal(text)
                : component(segments, Style.EMPTY, color & 0xFFFFFF)).getVisualOrderText();
        renderOutlinedLine(graphics, font, styled, x, y, color, tickCount);
    }

    static void renderOutlinedLine(GuiGraphics graphics, Font font, FormattedCharSequence styled,
            int x, int y, int color, int tickCount) {
        ToucanTextRenderer.renderOutlinedLine(graphics, font, styled, x, y, color, tickCount);
    }

    static Component slice(Component component, int start, int end) {
        return ToucanTextRenderer.slice(component, start, end);
    }

    static int horizontalPadding(List<DialogueTextSegment> segments) {
        return ToucanTextRenderer.horizontalPadding(spans(segments));
    }

    static int verticalPadding(List<DialogueTextSegment> segments) {
        return ToucanTextRenderer.verticalPadding(spans(segments));
    }

    private static List<ToucanTextSpan> spans(List<DialogueTextSegment> segments) {
        return segments == null ? List.of() : segments.stream()
                .map(segment -> new ToucanTextSpan(segment.text(), effects(segment.effects()))).toList();
    }

    static ToucanTextEffects effects(DialogueTextEffects effects) {
        return new ToucanTextEffects(effects.italic(), effects.bold(), effects.underlined(), effects.strikethrough(),
                effects.obfuscated(), effects.wavy(), effects.shake(), effects.pulse(), effects.jump(), effects.rainbow(),
                effects.color(), effects.gradientStartColor(), effects.gradientEndColor(),
                animation(effects.waveAnimation()), animation(effects.shakeAnimation()),
                animation(effects.pulseAnimation()), animation(effects.jumpAnimation()));
    }

    private static ToucanTextAnimation animation(DialogueTextAnimation animation) {
        return new ToucanTextAnimation(animation.strength(), animation.speed());
    }
}
