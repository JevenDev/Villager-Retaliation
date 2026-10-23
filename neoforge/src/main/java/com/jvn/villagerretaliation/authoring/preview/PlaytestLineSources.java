package com.jvn.villagerretaliation.authoring.preview;

import com.jvn.villagerretaliation.dialogue.DialogueSpeech;
import java.util.LinkedHashMap;
import java.util.UUID;

public final class PlaytestLineSources {
    public record Source(String document, String node, String response, String collection, String line) { }
    private static final LinkedHashMap<UUID, Source> SOURCES = new LinkedHashMap<>();
    private static volatile boolean active;
    private static volatile long revision;
    public static long revision() { return revision; }
    public static synchronized java.util.Set<String> documents() {
        return SOURCES.values().stream().map(Source::document).collect(java.util.stream.Collectors.toSet());
    }
    private PlaytestLineSources() { }
    public static boolean active() { return active; }
    public static synchronized void active(boolean enabled) { active = enabled; if (!enabled) SOURCES.clear(); revision++; }
    public static synchronized DialogueSpeech register(DialogueSpeech speech, Source source) {
        if (active && source != null) {
            SOURCES.put(speech.presentationId(), source);
            revision++;
            if (SOURCES.size() > 4096) SOURCES.pollFirstEntry();
        }
        return speech;
    }
    public static synchronized Source get(UUID presentation) { return SOURCES.get(presentation); }
}
