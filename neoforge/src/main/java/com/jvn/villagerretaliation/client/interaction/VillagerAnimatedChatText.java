package com.jvn.villagerretaliation.client.interaction;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.dialogue.normal.DialogueTextEffects;
import com.jvn.villagerretaliation.dialogue.normal.DialogueTextSegment;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

final class VillagerAnimatedChatText {
    private static final int MAX_TRACKED_MESSAGES = 80;
    private static final Deque<Entry> ENTRIES = new ArrayDeque<>();

    private VillagerAnimatedChatText() {
    }

    static void remember(List<DialogueTextSegment> textSegments) {
        if (VillagerRetaliationConfig.DISABLE_DIALOGUE_TEXT_EFFECTS.get()) {
            return;
        }
        if (!VillagerStyledTextRenderer.hasAnimatedEffects(textSegments)) {
            return;
        }

        String fullText = DialogueTextSegment.plainText(textSegments);
        if (fullText.isBlank()) {
            return;
        }

        ENTRIES.addFirst(new Entry(List.copyOf(textSegments)));
        while (ENTRIES.size() > MAX_TRACKED_MESSAGES) {
            ENTRIES.removeLast();
        }
    }

    static List<DialogueTextSegment> segmentsForLine(String lineText) {
        return beginRenderFrame().segmentsForLine(lineText, true);
    }

    static RenderState beginRenderFrame() {
        return new RenderState(List.copyOf(ENTRIES));
    }

    static boolean hasTrackedEffects() {
        if (VillagerRetaliationConfig.DISABLE_DIALOGUE_TEXT_EFFECTS.get()) {
            return false;
        }
        return !ENTRIES.isEmpty();
    }

    private static List<DialogueTextSegment> sliceSegments(List<DialogueTextSegment> segments, String lineText) {
        if (VillagerRetaliationConfig.DISABLE_DIALOGUE_TEXT_EFFECTS.get()) {
            return List.of();
        }
        if (segments == null || segments.isEmpty() || lineText == null || lineText.isBlank()) {
            return List.of();
        }
        String fullText = DialogueTextSegment.plainText(segments);
        int start = fullText.indexOf(lineText);
        if (start < 0) {
            int embeddedStart = lineText.indexOf(fullText);
            if (embeddedStart < 0) {
                return List.of();
            }
            List<DialogueTextSegment> embedded = new ArrayList<>();
            if (embeddedStart > 0) {
                embedded.add(new DialogueTextSegment(lineText.substring(0, embeddedStart), DialogueTextEffects.NONE));
            }
            embedded.addAll(segments);
            int embeddedEnd = embeddedStart + fullText.length();
            if (embeddedEnd < lineText.length()) {
                embedded.add(new DialogueTextSegment(lineText.substring(embeddedEnd), DialogueTextEffects.NONE));
            }
            return List.copyOf(embedded);
        }

        return DialogueTextSegment.slice(segments, start, start + lineText.length());
    }

    private record Entry(List<DialogueTextSegment> segments) {
    }

    static final class RenderState {
        private final List<Entry> entries;
        private final Set<Entry> claimedEntries = Collections.newSetFromMap(new IdentityHashMap<>());
        private Entry activeEntry;

        private RenderState(List<Entry> entries) {
            this.entries = entries;
        }

        List<DialogueTextSegment> segmentsForLine(String lineText, boolean endOfEntry) {
            if (VillagerRetaliationConfig.DISABLE_DIALOGUE_TEXT_EFFECTS.get() || lineText == null || lineText.isBlank()) {
                if (endOfEntry) {
                    this.activeEntry = null;
                }
                return List.of();
            }

            if (endOfEntry || this.activeEntry == null || !lineMatches(this.activeEntry, lineText)) {
                this.activeEntry = claimEntry(lineText);
            }
            if (this.activeEntry == null) {
                return List.of();
            }

            List<DialogueTextSegment> sliced = sliceSegments(this.activeEntry.segments(), lineText);
            if (!sliced.isEmpty()) {
                return sliced;
            }

            this.activeEntry = claimEntry(lineText);
            return this.activeEntry == null ? List.of() : sliceSegments(this.activeEntry.segments(), lineText);
        }

        private Entry claimEntry(String lineText) {
            for (Entry entry : this.entries) {
                if (this.claimedEntries.contains(entry) || !lineMatches(entry, lineText)) {
                    continue;
                }
                this.claimedEntries.add(entry);
                return entry;
            }
            return null;
        }

        private static boolean lineMatches(Entry entry, String lineText) {
            return !sliceSegments(entry.segments(), lineText).isEmpty();
        }
    }
}
