package com.jvn.villagerretaliation.dialogue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.minecraft.resources.ResourceLocation;

public final class DialogueWordGrammar {
    private DialogueWordGrammar() { }

    public static List<String> warnings(DialogueWordPool pool) {
        var result = new ArrayList<String>();
        if (pool.role().equals("literal")) return List.of();
        for (var entry : pool.entries()) {
            String text = entry.text();
            if (text.isEmpty()) continue;
            if (!text.equals(text.strip())) result.add("Leading or trailing space in " + pool.role() + " phrase: " + text);
            if (pool.role().equals("sentence") && text.matches("(?s).*[,;:]$")) result.add("Sentence ends with clause punctuation: " + text);
            if (!pool.role().equals("sentence") && text.matches("(?s).*[.!?,;:]$")) result.add(pool.role() + " phrase supplies punctuation that belongs to its template: " + text);
        }
        return List.copyOf(result);
    }

    private static boolean endsWithSentencePool(String before, Function<ResourceLocation, DialogueWordPool> pools) {
        var matcher = java.util.regex.Pattern.compile("\\{word:([a-z0-9_.-]+:[a-z0-9/._-]+)}$").matcher(before);
        if (!matcher.find()) return false;
        var previous = pools.apply(ResourceLocation.parse(matcher.group(1)));
        return previous.role().equals("sentence") && !previous.optional();
    }

    public static List<String> warnings(String template, Function<ResourceLocation, DialogueWordPool> pools) {
        var result = new ArrayList<String>();
        for (var id : DialogueWordPools.references(template)) {
            var pool = pools.apply(id);
            warnings(pool).forEach(warning -> result.add(id + ": " + warning));
            String token = "{word:" + id + "}";
            int position = template.indexOf(token);
            while (position >= 0) {
                String before = template.substring(0, position).replaceAll("<[^>]+>", "").stripTrailing();
                String after = template.substring(position + token.length()).replaceAll("^(?:<[^>]+>)+", "");
                if (pool.role().equals("sentence") && !before.isEmpty() && !before.matches("(?s).*[.!?]$") && !endsWithSentencePool(before, pools))
                    result.add(id + ": sentence pool appears inside a sentence. Choose a clause, address, or adjective phrase for this slot.");
                if (pool.role().equals("sentence") && after.matches("(?s)^[.!?,;:].*")) result.add(id + ": template adds punctuation immediately after a complete sentence");
                position = template.indexOf(token, position + token.length());
            }
        }
        return result.stream().distinct().toList();
    }
}
