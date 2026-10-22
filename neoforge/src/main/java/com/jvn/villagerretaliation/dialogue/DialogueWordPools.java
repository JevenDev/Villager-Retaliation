package com.jvn.villagerretaliation.dialogue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;

public final class DialogueWordPools {
    private static final Pattern TOKEN = Pattern.compile("\\{word(?=[:}\\s]|$)[^{}]*(?:}|(?=\\{|$))");
    private static final Pattern REFERENCE = Pattern.compile("\\{word:([a-z0-9_.-]+:[a-z0-9/._-]+)}");

    private DialogueWordPools() { }

    public static UnaryOperator<String> resolver(Function<ResourceLocation, List<String>> pools,
            RandomSource random, Consumer<String> diagnostic) {
        Map<String, String> choices = new HashMap<>();
        return text -> {
            if (!text.contains("{word")) return text;
            var matcher = TOKEN.matcher(text);
            var result = new StringBuilder();
            while (matcher.find()) {
                String token = matcher.group();
                String value = choices.computeIfAbsent(token, key -> {
                    var reference = REFERENCE.matcher(key);
                    if (!reference.matches()) {
                        diagnostic.accept("Malformed word pool reference: " + key);
                        return key;
                    }
                    var id = ResourceLocation.parse(reference.group(1));
                    List<String> values = pools.apply(id);
                    if (values == null || values.isEmpty()) {
                        diagnostic.accept("Missing word pool: " + id);
                        return key;
                    }
                    return values.get(random.nextInt(values.size()));
                });
                matcher.appendReplacement(result, Matcher.quoteReplacement(value));
            }
            matcher.appendTail(result);
            return result.toString();
        };
    }

    public static boolean matches(String template, String text, Function<ResourceLocation, List<String>> pools) {
        if (!template.contains("{word")) return template.equals(text);
        var matcher = REFERENCE.matcher(template);
        var pattern = new StringBuilder();
        Map<String, Integer> groups = new HashMap<>();
        int cursor = 0;
        while (matcher.find()) {
            pattern.append(Pattern.quote(template.substring(cursor, matcher.start())));
            String key = matcher.group(1);
            if (groups.containsKey(key)) {
                pattern.append("\\").append(groups.get(key));
            } else {
                groups.put(key, groups.size() + 1);
                List<String> values = pools.apply(ResourceLocation.parse(key));
                if (values == null || values.isEmpty()) values = List.of(matcher.group());
                pattern.append('(').append(values.stream().map(Pattern::quote)
                        .collect(java.util.stream.Collectors.joining("|"))).append(')');
            }
            cursor = matcher.end();
        }
        pattern.append(Pattern.quote(template.substring(cursor)));
        return Pattern.compile(pattern.toString()).matcher(text).matches();
    }

    public static String templateLiterals(String text) {
        return REFERENCE.matcher(text).replaceAll("word");
    }
}
