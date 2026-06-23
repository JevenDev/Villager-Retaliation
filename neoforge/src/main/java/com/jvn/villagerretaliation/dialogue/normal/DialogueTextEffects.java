package com.jvn.villagerretaliation.dialogue.normal;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.util.DatapackJsonReader;
import java.util.Locale;
import java.util.Map;
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
        Integer gradientEndColor) {
    public static final DialogueTextEffects NONE = new DialogueTextEffects(
            false, false, false, false, false, false, false, false, false, false, null, null, null);

    private static final Map<String, Integer> NAMED_COLORS = Map.ofEntries(
            Map.entry("black", 0x000000),
            Map.entry("dark_blue", 0x0000AA),
            Map.entry("dark_green", 0x00AA00),
            Map.entry("dark_aqua", 0x00AAAA),
            Map.entry("dark_red", 0xAA0000),
            Map.entry("dark_purple", 0xAA00AA),
            Map.entry("gold", 0xFFAA00),
            Map.entry("gray", 0xAAAAAA),
            Map.entry("dark_gray", 0x555555),
            Map.entry("blue", 0x5555FF),
            Map.entry("green", 0x55FF55),
            Map.entry("aqua", 0x55FFFF),
            Map.entry("red", 0xFF5555),
            Map.entry("light_purple", 0xFF55FF),
            Map.entry("purple", 0xFF55FF),
            Map.entry("magenta", 0xFF55FF),
            Map.entry("yellow", 0xFFFF55),
            Map.entry("white", 0xFFFFFF),
            Map.entry("grey", 0xAAAAAA),
            Map.entry("dark_grey", 0x555555)
    );

    public boolean active() {
        return this.italic
                || this.bold
                || this.underlined
                || this.strikethrough
                || this.obfuscated
                || this.wavy
                || this.shake
                || this.pulse
                || this.jump
                || this.rainbow
                || this.color != null
                || this.gradientStartColor != null
                || this.gradientEndColor != null;
    }

    public boolean hasGradient() {
        return this.gradientStartColor != null && this.gradientEndColor != null;
    }

    public DialogueTextEffects merge(DialogueTextEffects other) {
        if (other == null || !other.active()) {
            return this;
        }
        return new DialogueTextEffects(
                this.italic || other.italic(),
                this.bold || other.bold(),
                this.underlined || other.underlined(),
                this.strikethrough || other.strikethrough(),
                this.obfuscated || other.obfuscated(),
                this.wavy || other.wavy(),
                this.shake || other.shake(),
                this.pulse || other.pulse(),
                this.jump || other.jump(),
                this.rainbow || other.rainbow(),
                other.color() == null ? this.color : other.color(),
                other.gradientStartColor() == null ? this.gradientStartColor : other.gradientStartColor(),
                other.gradientEndColor() == null ? this.gradientEndColor : other.gradientEndColor()
        );
    }

    public static DialogueTextEffects read(JsonObject entry) {
        JsonObject effects = DatapackJsonReader.readObject(entry, "text_effects");
        Integer gradientStart = readColor(entry, effects, "gradient_start", "gradientStart");
        Integer gradientEnd = readColor(entry, effects, "gradient_end", "gradientEnd");
        return new DialogueTextEffects(
                readEffectBoolean(entry, effects, "italic", "italics"),
                readEffectBoolean(entry, effects, "bold", "bolded"),
                readEffectBoolean(entry, effects, "underlined", "underline"),
                readEffectBoolean(entry, effects, "strikethrough", "strikethrough"),
                readEffectBoolean(entry, effects, "obfuscated", "obfuscate"),
                readEffectBoolean(entry, effects, "wavy", "wave"),
                readEffectBoolean(entry, effects, "shake", "shaky"),
                readEffectBoolean(entry, effects, "pulse", "pulsing"),
                readEffectBoolean(entry, effects, "jump", "jumping"),
                readEffectBoolean(entry, effects, "rainbow", "rainbow_text"),
                readColor(entry, effects, "color", "text_color"),
                gradientStart,
                gradientEnd
        );
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
                readNullableColor(buffer)
        );
    }

    public static DialogueTextEffects fromToken(String token) {
        if (token == null || token.isBlank()) {
            return NONE;
        }
        DialogueTextEffects effects = NONE;
        for (String part : token.split(",")) {
            effects = effects.merge(fromTag(part.trim()));
        }
        return effects;
    }

    public static DialogueTextEffects fromTag(String tag) {
        if (tag == null || tag.isBlank()) {
            return NONE;
        }
        String normalized = tag.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("color:")) {
            return color(parseColor(normalized.substring("color:".length())));
        }
        if (normalized.startsWith("gradient:")) {
            String[] colors = normalized.substring("gradient:".length()).split(":", 2);
            if (colors.length == 2) {
                return gradient(parseColor(colors[0]), parseColor(colors[1]));
            }
            return NONE;
        }
        Integer namedColor = parseColor(normalized);
        if (namedColor != null) {
            return color(namedColor);
        }
        return switch (normalized) {
            case "i", "italic", "italics" -> new DialogueTextEffects(true, false, false, false, false, false, false, false, false, false, null, null, null);
            case "b", "bold" -> new DialogueTextEffects(false, true, false, false, false, false, false, false, false, false, null, null, null);
            case "u", "underline", "underlined" -> new DialogueTextEffects(false, false, true, false, false, false, false, false, false, false, null, null, null);
            case "s", "strike", "strikethrough" -> new DialogueTextEffects(false, false, false, true, false, false, false, false, false, false, null, null, null);
            case "obfuscated", "obfuscate", "magic" -> new DialogueTextEffects(false, false, false, false, true, false, false, false, false, false, null, null, null);
            case "wave", "wavy" -> new DialogueTextEffects(false, false, false, false, false, true, false, false, false, false, null, null, null);
            case "shake", "shaky" -> new DialogueTextEffects(false, false, false, false, false, false, true, false, false, false, null, null, null);
            case "pulse", "pulsing" -> new DialogueTextEffects(false, false, false, false, false, false, false, true, false, false, null, null, null);
            case "jump", "jumping", "bounce", "bouncy" -> new DialogueTextEffects(false, false, false, false, false, false, false, false, true, false, null, null, null);
            case "rainbow" -> new DialogueTextEffects(false, false, false, false, false, false, false, false, false, true, null, null, null);
            default -> NONE;
        };
    }

    public static Integer parseColor(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        Integer named = NAMED_COLORS.get(normalized);
        if (named != null) {
            return named;
        }
        if (normalized.startsWith("#")) {
            normalized = normalized.substring(1);
        } else if (normalized.startsWith("0x")) {
            normalized = normalized.substring(2);
        }
        if (normalized.length() != 6) {
            return null;
        }
        try {
            return Integer.parseInt(normalized, 16) & 0xFFFFFF;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static DialogueTextEffects color(Integer color) {
        return new DialogueTextEffects(false, false, false, false, false, false, false, false, false, false, color, null, null);
    }

    private static DialogueTextEffects gradient(Integer start, Integer end) {
        if (start == null || end == null) {
            return NONE;
        }
        return new DialogueTextEffects(false, false, false, false, false, false, false, false, false, false, null, start, end);
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

    private static boolean readEffectBoolean(JsonObject entry, JsonObject effects, String primaryKey, String aliasKey) {
        return DatapackJsonReader.readBoolean(entry, primaryKey)
                || DatapackJsonReader.readBoolean(entry, aliasKey)
                || (effects != null && (DatapackJsonReader.readBoolean(effects, primaryKey)
                || DatapackJsonReader.readBoolean(effects, aliasKey)));
    }

    private static Integer readColor(JsonObject entry, JsonObject effects, String primaryKey, String aliasKey) {
        Integer color = readColor(entry, primaryKey);
        if (color != null) {
            return color;
        }
        color = readColor(entry, aliasKey);
        if (color != null || effects == null) {
            return color;
        }
        color = readColor(effects, primaryKey);
        return color == null ? readColor(effects, aliasKey) : color;
    }

    private static Integer readColor(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonPrimitive()) {
            return null;
        }
        if (element.getAsJsonPrimitive().isNumber()) {
            try {
                return element.getAsInt() & 0xFFFFFF;
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return parseColor(element.getAsString());
    }
}
