package com.jvn.villagerretaliation.dialogue.normal;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.network.RegistryFriendlyByteBuf;

public record DialogueTextSegment(String text, DialogueTextEffects effects) {
    private static final int MAX_SEGMENTS = 64;

    public DialogueTextSegment {
        text = text == null ? "" : text;
        effects = effects == null ? DialogueTextEffects.NONE : effects;
    }

    public static List<DialogueTextSegment> plain(String text, DialogueTextEffects baseEffects) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        return List.of(new DialogueTextSegment(text, baseEffects));
    }

    public static List<DialogueTextSegment> parse(String text, DialogueTextEffects baseEffects) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }

        DialogueTextEffects safeBase = baseEffects == null ? DialogueTextEffects.NONE : baseEffects;
        List<DialogueTextSegment> segments = new ArrayList<>();
        List<DialogueTextEffects> stack = new ArrayList<>();
        stack.add(safeBase);
        StringBuilder current = new StringBuilder();

        int index = 0;
        while (index < text.length()) {
            int open = text.indexOf('<', index);
            if (open < 0) {
                current.append(text, index, text.length());
                break;
            }
            int close = text.indexOf('>', open + 1);
            if (close < 0) {
                current.append(text, index, text.length());
                break;
            }

            current.append(text, index, open);
            String rawTag = text.substring(open + 1, close).trim();
            boolean closing = rawTag.startsWith("/");
            String tag = normalizeTag(closing ? rawTag.substring(1) : tagName(rawTag));
            String effectTag = normalizeTag(closing ? rawTag.substring(1) : rawTag);
            DialogueTextEffects tagEffects = closing ? closingTagMarker(tag) : effectsForTag(effectTag);
            if (tagEffects == null) {
                current.append(text, open, close + 1);
                index = close + 1;
                continue;
            }
            if (!closing && !hasClosingTag(text, close + 1, tag)) {
                current.append(text, open, close + 1);
                index = close + 1;
                continue;
            }

            flush(segments, current, currentEffects(stack));
            if (closing) {
                popTag(stack, tagEffects);
            } else {
                stack.add(currentEffects(stack).merge(tagEffects));
            }
            index = close + 1;
        }

        flush(segments, current, currentEffects(stack));
        return segments.isEmpty() ? plain(stripTagsFallback(text), safeBase) : List.copyOf(segments);
    }

    public static String plainText(List<DialogueTextSegment> segments) {
        StringBuilder builder = new StringBuilder();
        for (DialogueTextSegment segment : segments) {
            builder.append(segment.text());
        }
        return builder.toString();
    }

    public static void writeList(RegistryFriendlyByteBuf buffer, List<DialogueTextSegment> segments) {
        List<DialogueTextSegment> safeSegments = segments == null ? List.of() : segments;
        buffer.writeVarInt(Math.min(safeSegments.size(), MAX_SEGMENTS));
        for (int index = 0; index < Math.min(safeSegments.size(), MAX_SEGMENTS); index++) {
            DialogueTextSegment segment = safeSegments.get(index);
            buffer.writeUtf(segment.text(), 512);
            DialogueTextEffects.write(buffer, segment.effects());
        }
    }

    public static List<DialogueTextSegment> readList(RegistryFriendlyByteBuf buffer) {
        int size = Math.min(buffer.readVarInt(), MAX_SEGMENTS);
        List<DialogueTextSegment> segments = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            segments.add(new DialogueTextSegment(buffer.readUtf(512), DialogueTextEffects.read(buffer)));
        }
        return List.copyOf(segments);
    }

    private static void flush(List<DialogueTextSegment> segments, StringBuilder current, DialogueTextEffects effects) {
        if (current.isEmpty()) {
            return;
        }
        DialogueTextSegment next = new DialogueTextSegment(current.toString(), effects);
        current.setLength(0);
        if (!segments.isEmpty() && segments.getLast().effects().equals(next.effects())) {
            DialogueTextSegment previous = segments.removeLast();
            segments.add(new DialogueTextSegment(previous.text() + next.text(), next.effects()));
        } else {
            segments.add(next);
        }
    }

    private static void popTag(List<DialogueTextEffects> stack, DialogueTextEffects tagEffects) {
        if (stack.size() <= 1) {
            return;
        }
        stack.removeLast();
    }

    private static DialogueTextEffects currentEffects(List<DialogueTextEffects> stack) {
        return stack.isEmpty() ? DialogueTextEffects.NONE : stack.getLast();
    }

    private static DialogueTextEffects effectsForTag(String tag) {
        DialogueTextEffects effects = DialogueTextEffects.fromTag(tag);
        return effects.active() ? effects : null;
    }

    private static DialogueTextEffects closingTagMarker(String tag) {
        return switch (tag) {
            case "i", "italic", "italics", "b", "bold", "u", "underline", "underlined",
                    "s", "strike", "strikethrough", "obfuscated", "obfuscate", "magic",
                    "wave", "wavy", "shake", "shaky", "pulse", "pulsing", "jump", "jumping",
                    "bounce", "bouncy", "rainbow", "color", "gradient" -> DialogueTextEffects.fromTag("bold");
            default -> DialogueTextEffects.fromTag(tag).active() ? DialogueTextEffects.fromTag("bold") : null;
        };
    }

    private static boolean hasClosingTag(String text, int start, String tag) {
        String lowerText = text.toLowerCase(Locale.ROOT);
        for (String alias : aliasesFor(tag)) {
            if (lowerText.indexOf("</" + alias + ">", start) >= 0) {
                return true;
            }
        }
        return false;
    }

    private static List<String> aliasesFor(String tag) {
        return switch (tag) {
            case "i", "italic", "italics" -> List.of("i", "italic", "italics");
            case "b", "bold" -> List.of("b", "bold");
            case "u", "underline", "underlined" -> List.of("u", "underline", "underlined");
            case "s", "strike", "strikethrough" -> List.of("s", "strike", "strikethrough");
            case "obfuscated", "obfuscate", "magic" -> List.of("obfuscated", "obfuscate", "magic");
            case "wave", "wavy" -> List.of("wave", "wavy");
            case "shake", "shaky" -> List.of("shake", "shaky");
            case "pulse", "pulsing" -> List.of("pulse", "pulsing");
            case "jump", "jumping", "bounce", "bouncy" -> List.of("jump", "jumping", "bounce", "bouncy");
            case "rainbow" -> List.of("rainbow");
            case "color" -> List.of("color");
            case "gradient" -> List.of("gradient");
            default -> List.of(tag);
        };
    }

    private static String normalizeTag(String tag) {
        return tag == null ? "" : tag.trim().toLowerCase(Locale.ROOT);
    }

    private static String tagName(String rawTag) {
        if (rawTag == null) {
            return "";
        }
        String tag = rawTag.trim();
        int separator = tag.indexOf(':');
        return separator < 0 ? tag : tag.substring(0, separator);
    }

    private static String stripTagsFallback(String text) {
        return text == null ? "" : text.replaceAll("</?[a-zA-Z_]+>", "");
    }
}
