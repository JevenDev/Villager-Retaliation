package com.jvn.villagerretaliation.authoring.client;

import com.jvn.villagerretaliation.authoring.core.DefinitionKind;
import com.jvn.villagerretaliation.dialogue.DialogueCue;
import com.jvn.villagerretaliation.dialogue.normal.DialogueTextEffects;
import com.jvn.villagerretaliation.dialogue.normal.DialogueTextSegment;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

final class EditorTextCompletion {
    record Context(DefinitionKind kind, String path, Set<String> conditions, Map<String, String> values) {
        static Context empty(DefinitionKind kind) { return new Context(kind, "", Set.of(), Map.of()); }

        int priority(String group) {
            if (group.equals("Player and villager") || group.equals("World and time")) return 0;
            if (group.equals("Player items and enchantments") && conditions.stream().anyMatch(type -> type.contains("item"))) return 0;
            if (group.equals("Quest trigger events") && path.contains("/triggers/")) return 0;
            if (group.equals("Performance rewards") && path.contains("/performance")) return 0;
            if (group.startsWith("Quest") && (kind == DefinitionKind.QUEST || kind == DefinitionKind.CONVERSATION || path.contains("/quests/"))) return 1;
            if ((group.equals("Family") || group.equals("Relationships") || group.equals("Conversation memories")) && path.contains("/dialogue/")) return 1;
            if (group.equals("Theft and retaliation") && conditions.stream().anyMatch(type -> type.contains("theft") || type.contains("retaliation"))) return 1;
            if (group.equals("Villager details") && kind != DefinitionKind.FORCED) return 1;
            return 2;
        }
    }

    record Token(int start, int end, int caret, char delimiter, String query) { }
    record Suggestion(String label, String value, String closing, String category, String help, int priority) {
        String token() { return category.equals("Variable") ? "{" + value + "}" : "<" + value + ">"; }
    }
    record Inspection(String token, String help) { }
    private static final Pattern TOKENS = Pattern.compile("\\{([^{}\\n]+)}|<([^<>\\n]+)>");
    private static final String CUSTOM = "Custom variable. It resolves only when your pack supplies a value with this name.";

    private EditorTextCompletion() { }

    static Token token(String text, int byteCaret, boolean formatting, boolean explicit) {
        int caret = EditorTextEdit.offset(text, byteCaret);
        int start = caret;
        while (start > 0) {
            char c = text.charAt(start - 1);
            if (c == '{' || c == '<') {
                start--;
                if (c == '<' && !formatting) return null;
                String query = text.substring(start + 1, caret);
                if (query.indexOf('\n') >= 0 || c == '{' && !query.matches("[A-Za-z0-9_:.]*")
                        || c == '<' && !query.isEmpty() && !query.matches("[A-Za-z/].*")) return null;
                int end = caret;
                char close = c == '{' ? '}' : '>';
                while (end < text.length() && text.charAt(end) != close && "{}<>\n".indexOf(text.charAt(end)) < 0
                        && (c == '<' || !Character.isWhitespace(text.charAt(end)))) end++;
                if (end < text.length() && text.charAt(end) == close) end++;
                else end = caret;
                return new Token(start, end, caret, c, query);
            }
            if (c == '}' || c == '>' || c == '\n') break;
            start--;
        }
        return explicit ? new Token(caret, caret, caret, formatting ? '<' : '{', "") : null;
    }

    static List<Suggestion> suggestions(String text, Token token, Context context, Set<String> styles) {
        if (token == null) return List.of();
        String query = token.query().toLowerCase(Locale.ROOT);
        var choices = new ArrayList<Suggestion>();
        if (token.delimiter() == '{') {
            var known = new java.util.HashSet<String>();
            for (var group : EditorVariables.matching(query)) for (var variable : group.variables()) {
                known.add(variable.key());
                String help = group.availability();
                if (context.values().containsKey(variable.key())) help += "\nPreview value: " + context.values().get(variable.key());
                choices.add(new Suggestion(variable.label(), variable.key(), "", "Variable", help,
                        context.values().containsKey(variable.key()) ? 0 : context.priority(group.title())));
            }
            for (var entry : context.values().entrySet()) if (!known.contains(entry.getKey()) && entry.getKey().toLowerCase(Locale.ROOT).contains(query))
                choices.add(new Suggestion(entry.getKey(), entry.getKey(), "", "Variable", "Supplied by the preview context.\nPreview value: " + entry.getValue(), 0));
            choices.sort(Comparator.comparingInt((Suggestion entry) -> entry.value().equals(query) ? -1
                            : query.isBlank() || entry.value().startsWith(query) ? 0 : entry.value().contains(query) ? 1 : 2)
                    .thenComparingInt(Suggestion::priority).thenComparing(Suggestion::value));
            return List.copyOf(choices);
        }
        if (query.startsWith("/")) {
            var stack = openTags(text.substring(0, token.start()), styles);
            if (!stack.isEmpty()) {
                String tag = stack.getLast();
                if (tag.startsWith(query.substring(1))) choices.add(new Suggestion("Close " + tag, "/" + tag, "", "Closing tag",
                        "Closes the innermost text span.", 0));
            }
            return choices.stream().distinct().toList();
        }
        for (var style : EditorTextStyles.STYLES)
            choices.add(new Suggestion(style.name(), style.tag(), style.tag(), style.shortcut().isEmpty() ? "Effect" : "Formatting",
                    "Wraps text in <" + style.tag() + ">...</" + style.tag() + ">.", 0));
        for (String color : EditorTextStyles.COLORS)
            choices.add(new Suggestion(color.replace('_', ' '), "color:" + color, "color", "Color", "Applies " + color.replace('_', ' ') + " to a text span.", 0));
        choices.add(new Suggestion("Custom color", "color:#ffaa00", "color", "Color", "Use a six-digit RGB hex color, or choose a color below.", 0));
        choices.add(new Suggestion("Gradient", "gradient:#ffaa00:#ff5555", "gradient", "Effect", "Two named or six-digit RGB colors, separated by a colon.", 0));
        cue(choices, "Pause", "pause=500", "Pause in milliseconds, from 0 to 60000. 500 ms is half a second.");
        cue(choices, "Typing speed", "speed=1", "Typing speed multiplier from 0.1 to 10. Use 1 to return to normal speed.");
        cue(choices, "Agree", "agree", "Makes the speaker nod.");
        cue(choices, "Disagree", "disagree", "Makes the speaker shake their head.");
        cue(choices, "Reset pose", "reset", "Resets the speaker's pose.");
        for (String target : List.of("player", "left", "right", "up", "down", "nearest_participant", "role:provider"))
            cue(choices, "Look at " + target.replace('_', ' '), "look=" + target, "Gaze cue. role:name requires a bound character with that role.");
        cue(choices, "Sound", "sound=minecraft:entity.villager.yes volume=1 pitch=1", "Sound resource ID. Volume: 0 to 1. Pitch: 0.5 to 2. No closing tag.");
        cue(choices, "Particle", "particle=minecraft:happy_villager amount=1", "Simple particle resource ID. Amount: 1 to 64. No closing tag.");
        for (String style : new java.util.TreeSet<>(styles))
            choices.add(new Suggestion(style, style, style, "Custom style", "Style defined by this pack. Wraps text and may include animation or dialogue cues.", 0));
        String name = query.split("[=:]", 2)[0];
        if (query.contains(":") || query.contains("=")) {
            boolean effect = DialogueTextEffects.fromTag(query).active();
            boolean cue = DialogueCue.isTag(query) && DialogueCue.parse(query) != null;
            if ((effect || cue) && choices.stream().noneMatch(entry -> entry.value().equals(query))) {
                String help = choices.stream().filter(entry -> entry.value().split("[=:]", 2)[0].equals(name))
                        .map(Suggestion::help).findFirst().orElse("Uses the parameters you typed.");
                choices.addFirst(new Suggestion("Use typed parameters", query, effect ? name : "", cue ? "Dialogue cue" : "Formatting", help, 0));
            }
        }
        return choices.stream().filter(entry -> entry.value().startsWith(query)
                || !query.contains(":") && !query.contains("=") && entry.label().toLowerCase(Locale.ROOT).contains(query)
                || (query.contains("=") || query.startsWith("gradient:") || query.startsWith("color:#")) && entry.value().split("[=:]", 2)[0].equals(name)).toList();
    }

    private static void cue(List<Suggestion> choices, String label, String value, String help) {
        choices.add(new Suggestion(label, value, "", "Dialogue cue", help, 0));
    }

    private static String canonical(String tag) {
        return switch (tag) {
            case "bold" -> "b";
            case "italic", "italics" -> "i";
            case "underline", "underlined" -> "u";
            case "strike", "strikethrough" -> "s";
            case "wavy" -> "wave";
            case "shaky" -> "shake";
            case "pulsing" -> "pulse";
            case "jumping", "bounce", "bouncy" -> "jump";
            case "obfuscate", "magic" -> "obfuscated";
            default -> tag;
        };
    }

    static List<String> openTags(String text, Set<String> styles) {
        var stack = new ArrayList<String>();
        var matcher = TOKENS.matcher(text);
        while (matcher.find()) {
            String raw = matcher.group(2);
            if (raw == null) continue;
            String tag = raw.toLowerCase(Locale.ROOT).trim();
            if (tag.startsWith("/")) {
                if (!stack.isEmpty() && canonical(stack.getLast()).equals(canonical(tag.substring(1)))) stack.removeLast();
            } else if (!DialogueCue.isTag(tag) && (DialogueTextEffects.fromTag(tag).active() || styles.contains(tag))) stack.add(tag.split(":", 2)[0]);
        }
        return stack;
    }

    static EditorTextEdit complete(String text, Token token, Suggestion choice, int selectionStart, int selectionEnd) {
        int start = token.start(), end = token.end();
        String before = text.substring(0, start), after = text.substring(end);
        String open = choice.token();
        String selected = "";
        if (start == end && selectionStart != selectionEnd) {
            start = EditorTextEdit.offset(text, Math.min(selectionStart, selectionEnd));
            end = EditorTextEdit.offset(text, Math.max(selectionStart, selectionEnd));
            before = text.substring(0, start); after = text.substring(end);
            selected = text.substring(start, end);
        }
        String close = choice.closing().isEmpty() ? "" : "</" + choice.closing() + ">";
        if (!close.isEmpty() && token.end() > token.start() && text.charAt(token.end() - 1) == '>'
                && hasClosingSpan(after, choice.closing())) close = "";
        String result = before + open + selected + close + after;
        int caret = EditorTextEdit.bytes(before + open);
        return new EditorTextEdit(result, caret, caret + EditorTextEdit.bytes(selected));
    }

    private static boolean hasClosingSpan(String text, String closing) {
        int nested = 0;
        var matcher = TOKENS.matcher(text);
        while (matcher.find()) {
            String tag = matcher.group(2);
            if (tag == null) continue;
            tag = tag.toLowerCase(Locale.ROOT).trim();
            if (tag.startsWith("/") && canonical(tag.substring(1)).equals(canonical(closing))) {
                if (nested == 0) return true;
                nested--;
            } else if (canonical(tag.split(":", 2)[0]).equals(canonical(closing))) nested++;
        }
        return false;
    }

    static List<Inspection> inspect(String text, Context context, Map<String, com.jvn.villagerretaliation.dialogue.DialogueStyle> styles, boolean formatting) {
        var result = new LinkedHashMap<String, Inspection>();
        var matcher = TOKENS.matcher(text);
        while (matcher.find()) {
            String variable = matcher.group(1), tag = matcher.group(2), token = matcher.group();
            if (result.containsKey(token)) continue;
            String help = "";
            if (variable != null) {
                help = CUSTOM;
                for (var group : EditorVariables.groups()) for (var entry : group.variables()) if (entry.key().equals(variable))
                    help = entry.label() + ". " + group.availability();
                if (context.values().containsKey(variable)) help += "\nPreview value: " + context.values().get(variable);
                else help += "\nNo value supplied by the current preview.";
            } else if (formatting) {
                var matches = suggestions("<" + tag, new Token(0, tag.length() + 1, tag.length() + 1, '<', tag), context, styles.keySet());
                help = matches.isEmpty() ? "Text span or cue. Check the markup messages below for invalid syntax." : matches.getFirst().help();
                if (tag.startsWith("/")) help = "Closes the " + tag.substring(1) + " text span.";
            }
            if (!help.isEmpty()) result.put(token, new Inspection(token, help));
        }
        return List.copyOf(result.values());
    }

    static List<String> warnings(String text, Context context, Map<String, com.jvn.villagerretaliation.dialogue.DialogueStyle> styles, boolean formatting) {
        var result = new ArrayList<String>();
        if (formatting) result.addAll(DialogueTextSegment.markupWarnings(text, styles));
        var matcher = TOKENS.matcher(text);
        Set<String> known = EditorVariables.groups().stream().flatMap(group -> group.variables().stream()).map(EditorVariables.Variable::key)
                .collect(java.util.stream.Collectors.toSet());
        while (matcher.find()) if (matcher.group(1) != null && !known.contains(matcher.group(1)) && !context.values().containsKey(matcher.group(1))) {
            String key = matcher.group(1);
            String similar = known.stream().filter(name -> distance(key, name) <= 2).min(Comparator.comparingInt(name -> distance(key, name))).orElse("");
            result.add("{" + key + "}: custom or unknown variable. Supply it in your pack." + (similar.isEmpty() ? "" : " Did you mean {" + similar + "}?"));
        }
        return result.stream().distinct().toList();
    }

    private static int distance(String a, String b) {
        int[] row = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) row[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            int diagonal = row[0]; row[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int previous = row[j];
                row[j] = Math.min(Math.min(row[j] + 1, row[j - 1] + 1), diagonal + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1));
                diagonal = previous;
            }
        }
        return row[b.length()];
    }
}
