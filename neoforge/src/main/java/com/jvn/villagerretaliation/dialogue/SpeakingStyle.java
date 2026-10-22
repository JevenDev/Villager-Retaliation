package com.jvn.villagerretaliation.dialogue;

import java.util.List;
import java.util.UUID;

public final class SpeakingStyle {
    public static final List<String> STYLES = List.of("blunt", "cautious", "cheerful", "formal");

    private SpeakingStyle() { }

    public static String forSpeaker(UUID speaker) {
        if (speaker == null) return "";
        // stable identity keeps the voice unchanged across reloads and profession changes
        long seed = speaker.getMostSignificantBits() ^ speaker.getLeastSignificantBits();
        seed = (seed ^ (seed >>> 33)) * 0xff51afd7ed558ccdL;
        seed = (seed ^ (seed >>> 33)) * 0xc4ceb9fe1a85ec53L;
        return STYLES.get(Math.floorMod(seed ^ (seed >>> 33), STYLES.size()));
    }

    public record Condition(String style) implements DialogueCondition {
        @Override public boolean matches(DialogueContext context) { return context != null && style.equals(forSpeaker(context.providerId())); }
        @Override public int specificityScore() { return 4; }
    }
}
