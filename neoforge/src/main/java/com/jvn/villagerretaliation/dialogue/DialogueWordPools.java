package com.jvn.villagerretaliation.dialogue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;

public final class DialogueWordPools {
    private static final Pattern TOKEN = Pattern.compile("\\{word(?=[:}\\s]|$)[^{}]*(?:}|(?=\\{|$))");
    private static final Pattern REFERENCE = Pattern.compile("\\{word:([a-z0-9_.-]+:[a-z0-9/._-]+)}");

    private DialogueWordPools() { }

    public static List<ResourceLocation> references(String text) {
        return REFERENCE.matcher(text).results().map(match -> ResourceLocation.parse(match.group(1))).distinct().toList();
    }

    public static UnaryOperator<String> resolver(Function<ResourceLocation, List<String>> pools,
            RandomSource random, Consumer<String> diagnostic) {
        return selectingResolver(id -> {
            var values = pools.apply(id);
            return values == null || values.isEmpty() ? null : values.get(random.nextInt(values.size()));
        }, diagnostic);
    }

    public static UnaryOperator<String> selectingResolver(Function<ResourceLocation, String> select, Consumer<String> diagnostic) {
        Map<String, String> choices = new HashMap<>();
        return text -> {
            if (!text.contains("{word")) return text;
            var matcher = TOKEN.matcher(text);
            var result = new StringBuilder();
            int cursor = 0;
            while (matcher.find()) {
                String token = matcher.group();
                String value = choices.computeIfAbsent(token, key -> {
                    var reference = REFERENCE.matcher(key);
                    if (!reference.matches()) {
                        diagnostic.accept("Malformed word pool reference: " + key);
                        return key;
                    }
                    var id = ResourceLocation.parse(reference.group(1));
                    String selected = select.apply(id);
                    if (selected == null) {
                        diagnostic.accept("Missing word pool: " + id);
                        return key;
                    }
                    return selected;
                });
                result.append(text, cursor, matcher.start());
                cursor = matcher.end();
                if (value.isEmpty()) {
                    if (cursor == text.length() || cursor < text.length() && Character.isWhitespace(text.charAt(cursor)))
                        while (!result.isEmpty() && (result.charAt(result.length() - 1) == ' ' || result.charAt(result.length() - 1) == '\t')) result.setLength(result.length() - 1);
                    if (result.isEmpty()) while (cursor < text.length() && (text.charAt(cursor) == ' ' || text.charAt(cursor) == '\t')) cursor++;
                } else result.append(value);
            }
            result.append(text, cursor, text.length());
            return result.toString();
        };
    }

    public static boolean matches(String template, String text, Function<ResourceLocation, List<String>> pools) {
        if (!template.contains("{word")) return template.equals(text);
        var matcher = REFERENCE.matcher(template);
        var pattern = new StringBuilder();
        Map<String, Integer> groups = new java.util.LinkedHashMap<>();
        int cursor = 0;
        boolean previousOptional = false, optional = false;
        while (matcher.find()) {
            String key = matcher.group(1);
            List<String> values = pools.apply(ResourceLocation.parse(key));
            if (values == null || values.isEmpty()) values = List.of(matcher.group());
            boolean empty = values.contains("");
            optional |= empty;
            pattern.append(literalPattern(template.substring(cursor, matcher.start()), previousOptional, empty));
            if (groups.containsKey(key)) pattern.append("\\").append(groups.get(key));
            else {
                groups.put(key, groups.size() + 1);
                pattern.append('(').append(values.stream().map(Pattern::quote).collect(java.util.stream.Collectors.joining("|"))).append(')');
            }
            previousOptional = empty;
            cursor = matcher.end();
        }
        pattern.append(literalPattern(template.substring(cursor), previousOptional, false));
        var match = Pattern.compile(pattern.toString()).matcher(text);
        if (!match.matches()) return false;
        if (!optional) return true;
        return selectingResolver(id -> match.group(groups.get(id.toString())), issue -> { }).apply(template).equals(text);
    }

    private static String literalPattern(String literal, boolean leadingOptional, boolean trailingOptional) {
        int start = 0, end = literal.length();
        if (leadingOptional) while (start < end && (literal.charAt(start) == ' ' || literal.charAt(start) == '\t')) start++;
        if (trailingOptional) while (end > start && (literal.charAt(end - 1) == ' ' || literal.charAt(end - 1) == '\t')) end--;
        return (start > 0 ? "[ \t]*" : "") + Pattern.quote(literal.substring(start, end)) + (end < literal.length() ? "[ \t]*" : "");
    }

    public static String templateLiterals(String text) {
        return REFERENCE.matcher(text).replaceAll("word");
    }
}
