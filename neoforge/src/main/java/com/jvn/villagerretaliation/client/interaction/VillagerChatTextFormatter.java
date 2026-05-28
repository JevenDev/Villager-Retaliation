package com.jvn.villagerretaliation.client.interaction;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.dialogue.DialogueTextEffects;
import com.jvn.villagerretaliation.dialogue.DialogueTextSegment;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;

public final class VillagerChatTextFormatter {
    private VillagerChatTextFormatter() {
    }

    public static void onClientChatReceived(ClientChatReceivedEvent event) {
        if (VillagerRetaliationConfig.DISABLE_DIALOGUE_TEXT_EFFECTS.get()) {
            return;
        }

        Component formatted = format(event.getMessage());
        if (formatted != event.getMessage()) {
            event.setMessage(formatted);
        }
    }

    static Component format(Component message) {
        String text = message.getString();
        if (text.isBlank() || text.indexOf('<') < 0 || text.indexOf('>') < 0) {
            return message;
        }

        List<DialogueTextSegment> segments = DialogueTextSegment.parse(text, DialogueTextEffects.NONE);
        if (segments.stream().noneMatch(segment -> segment.effects().active())) {
            return message;
        }

        MutableComponent formatted = Component.empty().withStyle(message.getStyle());
        Style baseStyle = message.getStyle();
        for (DialogueTextSegment segment : segments) {
            appendSegment(formatted, segment, baseStyle);
        }
        VillagerAnimatedChatText.remember(segments);
        return formatted;
    }

    private static void appendSegment(MutableComponent message, DialogueTextSegment segment, Style baseStyle) {
        DialogueTextEffects effects = segment.effects();
        if (effects.rainbow()) {
            appendRainbow(message, segment, baseStyle);
            return;
        }
        if (effects.hasGradient()) {
            appendGradient(message, segment, baseStyle);
            return;
        }
        message.append(Component.literal(segment.text()).withStyle(styleFor(baseStyle, effects, effects.color())));
    }

    private static void appendRainbow(MutableComponent message, DialogueTextSegment segment, Style baseStyle) {
        int index = 0;
        int length = Math.max(1, segment.text().codePointCount(0, segment.text().length()));
        for (int offset = 0; offset < segment.text().length(); ) {
            int codePoint = segment.text().codePointAt(offset);
            String glyph = new String(Character.toChars(codePoint));
            message.append(Component.literal(glyph).withStyle(styleFor(baseStyle, segment.effects(), rainbowColor(index / (float) length))));
            offset += Character.charCount(codePoint);
            index++;
        }
    }

    private static void appendGradient(MutableComponent message, DialogueTextSegment segment, Style baseStyle) {
        int length = Math.max(1, segment.text().codePointCount(0, segment.text().length()) - 1);
        int index = 0;
        for (int offset = 0; offset < segment.text().length(); ) {
            int codePoint = segment.text().codePointAt(offset);
            String glyph = new String(Character.toChars(codePoint));
            float progress = length == 0 ? 0.0F : index / (float) length;
            int color = lerpColor(segment.effects().gradientStartColor(), segment.effects().gradientEndColor(), progress);
            message.append(Component.literal(glyph).withStyle(styleFor(baseStyle, segment.effects(), color)));
            offset += Character.charCount(codePoint);
            index++;
        }
    }

    private static Style styleFor(Style baseStyle, DialogueTextEffects effects, Integer color) {
        Style style = baseStyle
                .withItalic(effects.italic() || Boolean.TRUE.equals(baseStyle.isItalic()))
                .withBold(effects.bold() || Boolean.TRUE.equals(baseStyle.isBold()))
                .withUnderlined(effects.underlined() || Boolean.TRUE.equals(baseStyle.isUnderlined()))
                .withStrikethrough(effects.strikethrough() || Boolean.TRUE.equals(baseStyle.isStrikethrough()))
                .withObfuscated(effects.obfuscated() || Boolean.TRUE.equals(baseStyle.isObfuscated()));
        if (VillagerChatEffectRenderer.usesAnimatedRenderer(effects)) {
            return style.withColor(VillagerChatEffectRenderer.STATIC_EFFECT_TEXT_COLOR);
        }
        return color == null ? style : style.withColor(color);
    }

    private static int lerpColor(int start, int end, float progress) {
        float clamped = Math.clamp(progress, 0.0F, 1.0F);
        int red = Math.round(((start >> 16) & 0xFF) + (((end >> 16) & 0xFF) - ((start >> 16) & 0xFF)) * clamped);
        int green = Math.round(((start >> 8) & 0xFF) + (((end >> 8) & 0xFF) - ((start >> 8) & 0xFF)) * clamped);
        int blue = Math.round((start & 0xFF) + ((end & 0xFF) - (start & 0xFF)) * clamped);
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
}
