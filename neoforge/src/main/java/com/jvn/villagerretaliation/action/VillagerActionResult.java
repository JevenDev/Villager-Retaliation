package com.jvn.villagerretaliation.action;

import java.util.Map;

public record VillagerActionResult(
        boolean ran,
        String lineId,
        String text,
        Map<String, String> replacements,
        boolean flashTracker) {
    public static final VillagerActionResult EMPTY = new VillagerActionResult(false, "", "", Map.of(), false);

    public VillagerActionResult {
        lineId = lineId == null ? "" : lineId;
        text = text == null ? "" : text;
        replacements = replacements == null ? Map.of() : Map.copyOf(replacements);
    }

    public static VillagerActionResult success() {
        return new VillagerActionResult(true, "", "", Map.of(), false);
    }

    public static VillagerActionResult tracker(boolean flashTracker) {
        return new VillagerActionResult(true, "", "", Map.of(), flashTracker);
    }
}
