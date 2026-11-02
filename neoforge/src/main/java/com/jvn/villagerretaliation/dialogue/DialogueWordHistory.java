package com.jvn.villagerretaliation.dialogue;

import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DialogueWordHistory {
    private final Map<String, ArrayDeque<String>> recent = new LinkedHashMap<>(16, 0.75f, true);

    public List<String> recent(String key, int count) {
        var values = recent.get(key);
        return values == null ? List.of() : values.stream().limit(count).toList();
    }

    public void remember(String key, String text) {
        var values = recent.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        values.addFirst(text);
        while (values.size() > 16) values.removeLast();
        while (recent.size() > 4096) recent.remove(recent.keySet().iterator().next());
    }
}
