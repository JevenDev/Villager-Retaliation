package com.jvn.villagerretaliation.dialogue.normal;

import com.google.gson.JsonObject;
import net.minecraft.network.RegistryFriendlyByteBuf;

public record DialogueTextEffects(
        boolean italic,
        boolean bold,
        boolean underlined,
        boolean strikethrough,
        boolean obfuscated,
        boolean wavy,
        boolean shake,
        boolean pulse,
        boolean jump,
        boolean rainbow,
        Integer color,
        Integer gradientStartColor,
        Integer gradientEndColor,
        DialogueTextAnimation waveAnimation,
        DialogueTextAnimation shakeAnimation,
        DialogueTextAnimation pulseAnimation,
        DialogueTextAnimation jumpAnimation) {
    public DialogueTextEffects {
        java.util.Objects.requireNonNull(waveAnimation);
        java.util.Objects.requireNonNull(shakeAnimation);
        java.util.Objects.requireNonNull(pulseAnimation);
        java.util.Objects.requireNonNull(jumpAnimation);
    }

    public DialogueTextEffects(boolean italic, boolean bold, boolean underlined, boolean strikethrough,
            boolean obfuscated, boolean wavy, boolean shake, boolean pulse, boolean jump, boolean rainbow,
            Integer color, Integer gradientStartColor, Integer gradientEndColor) {
        this(italic, bold, underlined, strikethrough, obfuscated, wavy, shake, pulse, jump, rainbow,
                color, gradientStartColor, gradientEndColor, DialogueTextAnimation.DEFAULT, DialogueTextAnimation.DEFAULT,
                DialogueTextAnimation.DEFAULT, DialogueTextAnimation.DEFAULT);
    }
    public static final DialogueTextEffects NONE = new DialogueTextEffects(
            false, false, false, false, false, false, false, false, false, false, null, null, null);

    public com.jvn.toucanlib.text.ToucanTextStyle toStyle() {
        return new com.jvn.toucanlib.text.ToucanTextStyle(
                italic, bold, underlined, strikethrough, obfuscated, wavy, shake, pulse, jump, rainbow, color, gradientStartColor, gradientEndColor,
                new com.jvn.toucanlib.text.ToucanTextMotion(waveAnimation.strength(), waveAnimation.speed()),
                new com.jvn.toucanlib.text.ToucanTextMotion(shakeAnimation.strength(), shakeAnimation.speed()),
                new com.jvn.toucanlib.text.ToucanTextMotion(pulseAnimation.strength(), pulseAnimation.speed()),
                new com.jvn.toucanlib.text.ToucanTextMotion(jumpAnimation.strength(), jumpAnimation.speed()));
    }

    public static DialogueTextEffects fromStyle(com.jvn.toucanlib.text.ToucanTextStyle style) {
        return new DialogueTextEffects(
                style.italic(), style.bold(), style.underlined(), style.strikethrough(), style.obfuscated(), style.wavy(), style.shake(), style.pulse(), style.jump(), style.rainbow(), style.color(), style.gradientStartColor(), style.gradientEndColor(),
                new DialogueTextAnimation(style.waveAnimation().strength(), style.waveAnimation().speed()),
                new DialogueTextAnimation(style.shakeAnimation().strength(), style.shakeAnimation().speed()),
                new DialogueTextAnimation(style.pulseAnimation().strength(), style.pulseAnimation().speed()),
                new DialogueTextAnimation(style.jumpAnimation().strength(), style.jumpAnimation().speed()));
    }

    public boolean active() { return toStyle().active(); }
    public boolean hasGradient() { return gradientStartColor != null && gradientEndColor != null; }
    public DialogueTextEffects merge(DialogueTextEffects other) {
        return other == null || !other.active() ? this : fromStyle(toStyle().merge(other.toStyle()));
    }
    public static DialogueTextEffects read(JsonObject entry) {
        return fromStyle(com.jvn.toucanlib.text.ToucanTextStyle.read(entry));
    }

    public static void write(RegistryFriendlyByteBuf buffer, DialogueTextEffects effects) {
        DialogueTextEffects safeEffects = effects == null ? NONE : effects;
        buffer.writeBoolean(safeEffects.italic());
        buffer.writeBoolean(safeEffects.bold());
        buffer.writeBoolean(safeEffects.underlined());
        buffer.writeBoolean(safeEffects.strikethrough());
        buffer.writeBoolean(safeEffects.obfuscated());
        buffer.writeBoolean(safeEffects.wavy());
        buffer.writeBoolean(safeEffects.shake());
        buffer.writeBoolean(safeEffects.pulse());
        buffer.writeBoolean(safeEffects.jump());
        buffer.writeBoolean(safeEffects.rainbow());
        writeNullableColor(buffer, safeEffects.color());
        writeNullableColor(buffer, safeEffects.gradientStartColor());
        writeNullableColor(buffer, safeEffects.gradientEndColor());
        safeEffects.waveAnimation().write(buffer);
        safeEffects.shakeAnimation().write(buffer);
        safeEffects.pulseAnimation().write(buffer);
        safeEffects.jumpAnimation().write(buffer);
    }

    public static DialogueTextEffects read(RegistryFriendlyByteBuf buffer) {
        return new DialogueTextEffects(
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                readNullableColor(buffer),
                readNullableColor(buffer),
                readNullableColor(buffer),
                DialogueTextAnimation.read(buffer), DialogueTextAnimation.read(buffer),
                DialogueTextAnimation.read(buffer), DialogueTextAnimation.read(buffer)
        );
    }

    public static DialogueTextEffects fromToken(String token) {
        return fromStyle(com.jvn.toucanlib.text.ToucanTextStyle.fromToken(token));
    }

    public static DialogueTextEffects fromTag(String tag) {
        return fromStyle(com.jvn.toucanlib.text.ToucanTextStyle.fromTag(tag));
    }

    public static Integer parseColor(String value) {
        return com.jvn.toucanlib.text.ToucanTextStyle.parseColor(value);
    }

    private static void writeNullableColor(RegistryFriendlyByteBuf buffer, Integer color) {
        buffer.writeBoolean(color != null);
        if (color != null) {
            buffer.writeInt(color);
        }
    }

    private static Integer readNullableColor(RegistryFriendlyByteBuf buffer) {
        return buffer.readBoolean() ? buffer.readInt() : null;
    }

}
