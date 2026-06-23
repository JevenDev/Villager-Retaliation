package com.jvn.villagerretaliation.client.interaction;

import com.jvn.villagerretaliation.dialogue.normal.DialogueTextEffects;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.dialogue.normal.DialogueTextSegment;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

final class VillagerAnimatedChatText {
    private static final int MAX_TRACKED_MESSAGES = 80;
    private static final Deque<Entry> ENTRIES = new ArrayDeque<>();

    private VillagerAnimatedChatText() {
    }

    static void remember(List<DialogueTextSegment> textSegments) {
        if (VillagerRetaliationConfig.DISABLE_DIALOGUE_TEXT_EFFECTS.get()) {
            return;
        }
        if (textSegments == null || textSegments.stream().noneMatch(segment -> segment.effects().active())) {
            return;
        }

        String fullText = DialogueTextSegment.plainText(textSegments);
        if (fullText.isBlank()) {
            return;
        }

        ENTRIES.addFirst(new Entry(fullText, List.copyOf(textSegments), System.currentTimeMillis()));
        while (ENTRIES.size() > MAX_TRACKED_MESSAGES) {
            ENTRIES.removeLast();
        }
    }

    static List<DialogueTextSegment> segmentsForLine(String lineText) {
        if (VillagerRetaliationConfig.DISABLE_DIALOGUE_TEXT_EFFECTS.get()) {
            return List.of();
        }
        if (lineText == null || lineText.isBlank()) {
            return List.of();
        }

        for (Entry entry : ENTRIES) {
            List<DialogueTextSegment> sliced = sliceSegments(entry.segments(), lineText);
            if (!sliced.isEmpty()) {
                return sliced;
            }
        }
        return List.of();
    }

    static boolean hasTrackedEffects() {
        if (VillagerRetaliationConfig.DISABLE_DIALOGUE_TEXT_EFFECTS.get()) {
            return false;
        }
        return !ENTRIES.isEmpty();
    }

    private static List<DialogueTextSegment> sliceSegments(List<DialogueTextSegment> segments, String lineText) {
        String fullText = DialogueTextSegment.plainText(segments);
        int start = fullText.indexOf(lineText);
        if (start < 0) {
            int embeddedStart = lineText.indexOf(fullText);
            if (embeddedStart < 0) {
                return List.of();
            }
            List<DialogueTextSegment> embedded = new ArrayList<>();
            if (embeddedStart > 0) {
                embedded.add(new DialogueTextSegment(lineText.substring(0, embeddedStart), com.jvn.villagerretaliation.dialogue.normal.DialogueTextEffects.NONE));
            }
            embedded.addAll(segments);
            int embeddedEnd = embeddedStart + fullText.length();
            if (embeddedEnd < lineText.length()) {
                embedded.add(new DialogueTextSegment(lineText.substring(embeddedEnd), com.jvn.villagerretaliation.dialogue.normal.DialogueTextEffects.NONE));
            }
            return List.copyOf(embedded);
        }

        int end = start + lineText.length();
        int cursor = 0;
        List<DialogueTextSegment> sliced = new ArrayList<>();
        for (DialogueTextSegment segment : segments) {
            String segmentText = segment.text();
            int segmentStart = cursor;
            int segmentEnd = segmentStart + segmentText.length();
            cursor = segmentEnd;
            if (segmentEnd <= start || segmentStart >= end) {
                continue;
            }

            int localStart = Math.max(0, start - segmentStart);
            int localEnd = Math.min(segmentText.length(), end - segmentStart);
            if (localStart < localEnd) {
                sliced.add(new DialogueTextSegment(segmentText.substring(localStart, localEnd), segment.effects()));
            }
        }
        return List.copyOf(sliced);
    }

    private record Entry(String fullText, List<DialogueTextSegment> segments, long createdAtMillis) {
    }
}
