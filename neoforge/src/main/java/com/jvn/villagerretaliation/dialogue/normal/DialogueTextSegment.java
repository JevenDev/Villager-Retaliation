package com.jvn.villagerretaliation.dialogue.normal;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.network.RegistryFriendlyByteBuf;

public record DialogueTextSegment(String text, DialogueTextEffects effects) {
    private static final int MAX_SEGMENTS = 64;
    private static final int MAX_NETWORK_TEXT_LENGTH = 512;

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
        if (text.indexOf('<') < 0) {
            return plain(text, safeBase);
        }
        String lowerText = text.toLowerCase(Locale.ROOT);
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
            DialogueTextEffects tagEffects = closing ? null : effectsForTag(effectTag);
            if ((closing && !isKnownClosingTag(tag)) || (!closing && tagEffects == null)) {
                current.append(text, open, close + 1);
                index = close + 1;
                continue;
            }
            if (!closing && !hasClosingTag(lowerText, close + 1, tag)) {
                current.append(text, open, close + 1);
                index = close + 1;
                continue;
            }

            flush(segments, current, currentEffects(stack));
            if (closing) {
                popTag(stack);
            } else {
                stack.add(currentEffects(stack).merge(tagEffects));
            }
            index = close + 1;
        }

        flush(segments, current, currentEffects(stack));
        return segments.isEmpty() ? plain(stripTagsFallback(text), safeBase) : List.copyOf(segments);
    }

    public static String plainText(List<DialogueTextSegment> segments) {
        if (segments == null || segments.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (DialogueTextSegment segment : segments) {
            if (segment != null) {
                builder.append(segment.text());
            }
        }
        return builder.toString();
    }

    public static List<DialogueTextSegment> slice(List<DialogueTextSegment> segments, int start, int end) {
        if (segments == null || segments.isEmpty() || end <= start) {
            return List.of();
        }

        int safeStart = Math.max(0, start);
        int safeEnd = Math.max(safeStart, end);
        int cursor = 0;
        List<DialogueTextSegment> sliced = new ArrayList<>();
        for (DialogueTextSegment segment : segments) {
            String segmentText = segment.text();
            int segmentStart = cursor;
            int segmentEnd = segmentStart + segmentText.length();
            cursor = segmentEnd;
            if (segmentEnd <= safeStart || segmentStart >= safeEnd) {
                continue;
            }

            int localStart = Math.max(0, safeStart - segmentStart);
            int localEnd = Math.min(segmentText.length(), safeEnd - segmentStart);
            if (localStart < localEnd) {
                sliced.add(new DialogueTextSegment(segmentText.substring(localStart, localEnd), segment.effects()));
            }
        }
        return List.copyOf(sliced);
    }

    public static void writeList(RegistryFriendlyByteBuf buffer, List<DialogueTextSegment> segments) {
        List<DialogueTextSegment> safeSegments = forNetwork(segments);
        buffer.writeVarInt(safeSegments.size());
        for (DialogueTextSegment segment : safeSegments) {
            buffer.writeUtf(segment.text(), MAX_NETWORK_TEXT_LENGTH);
            DialogueTextEffects.write(buffer, segment.effects());
        }
    }

    public static List<DialogueTextSegment> readList(RegistryFriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        if (size < 0 || size > MAX_SEGMENTS) {
            throw new IllegalArgumentException(
                    "dialogue text segment count " + size + " is outside 0.." + MAX_SEGMENTS);
        }
        List<DialogueTextSegment> segments = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            segments.add(new DialogueTextSegment(
                    buffer.readUtf(MAX_NETWORK_TEXT_LENGTH),
                    DialogueTextEffects.read(buffer)));
        }
        return List.copyOf(segments);
    }

    /**
     * Fits styled text into the packet contract without dropping the dialogue tail.
     * Excess style runs are folded into the final segment, while text beyond the
     * payload's 512-character limit is truncated on a Unicode boundary.
     */
    public static List<DialogueTextSegment> forNetwork(List<DialogueTextSegment> segments) {
        if (segments == null || segments.isEmpty()) {
            return List.of();
        }

        List<DialogueTextSegment> bounded = new ArrayList<>();
        int remainingCharacters = MAX_NETWORK_TEXT_LENGTH;
        for (DialogueTextSegment segment : segments) {
            if (segment == null || segment.text().isEmpty() || remainingCharacters <= 0) {
                continue;
            }
            String text = truncate(segment.text(), remainingCharacters);
            remainingCharacters -= text.length();
            if (text.isEmpty()) {
                continue;
            }

            DialogueTextEffects effects = segment.effects();
            if (!bounded.isEmpty() && bounded.getLast().effects().equals(effects)) {
                DialogueTextSegment previous = bounded.removeLast();
                bounded.add(new DialogueTextSegment(previous.text() + text, effects));
            } else if (bounded.size() < MAX_SEGMENTS) {
                bounded.add(new DialogueTextSegment(text, effects));
            } else {
                DialogueTextSegment previous = bounded.removeLast();
                bounded.add(new DialogueTextSegment(previous.text() + text, previous.effects()));
            }
        }
        return List.copyOf(bounded);
    }

    /**
     * Reconciles a packet's redundant text and style-run fields. The text field may
     * still contain inline effect tags while the supplied segments already contain
     * their parsed representation. In that case the styled segments are retained;
     * a genuinely mismatched segment list is replaced by the authoritative text.
     */
    public static List<DialogueTextSegment> forNetwork(
            String text,
            List<DialogueTextSegment> segments) {
        String safeText = text == null ? "" : text;
        List<DialogueTextSegment> bounded = forNetwork(segments);
        String boundedText = plainText(bounded);
        if (!bounded.isEmpty() && boundedText.equals(safeText)) {
            if (safeText.indexOf('<') < 0 || safeText.indexOf('>') < 0) {
                return bounded;
            }
            List<DialogueTextSegment> parsed = forNetwork(parse(safeText, DialogueTextEffects.NONE));
            return plainText(parsed).equals(safeText) ? bounded : parsed;
        }

        List<DialogueTextSegment> parsed = forNetwork(parse(safeText, DialogueTextEffects.NONE));
        if (bounded.isEmpty() || !plainText(parsed).equals(boundedText)) {
            return parsed;
        }
        return bounded;
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || maxLength <= 0) {
            return "";
        }
        if (value.length() <= maxLength) {
            return value;
        }
        int end = maxLength;
        if (end > 0
                && Character.isHighSurrogate(value.charAt(end - 1))
                && Character.isLowSurrogate(value.charAt(end))) {
            end--;
        }
        return value.substring(0, end);
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

    private static void popTag(List<DialogueTextEffects> stack) {
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

    private static boolean isKnownClosingTag(String tag) {
        return switch (tag) {
            case "i", "italic", "italics", "b", "bold", "u", "underline", "underlined",
                    "s", "strike", "strikethrough", "obfuscated", "obfuscate", "magic",
                    "wave", "wavy", "shake", "shaky", "pulse", "pulsing", "jump", "jumping",
                    "bounce", "bouncy", "rainbow", "color", "gradient" -> true;
            default -> DialogueTextEffects.fromTag(tag).active();
        };
    }

    private static boolean hasClosingTag(String lowerText, int start, String tag) {
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
