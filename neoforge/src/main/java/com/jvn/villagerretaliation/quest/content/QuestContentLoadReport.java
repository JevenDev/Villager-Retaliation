package com.jvn.villagerretaliation.quest.content;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.resources.ResourceLocation;

/** Immutable diagnostics published separately from the live quest-content snapshot. */
public record QuestContentLoadReport(long generation, List<Entry> entries) {
    public QuestContentLoadReport {
        entries = entries == null
                ? List.of()
                : entries.stream()
                        .filter(java.util.Objects::nonNull)
                        .sorted(Comparator.comparing((Entry entry) -> entry.source().toString())
                                .thenComparing(Entry::system)
                                .thenComparing(Entry::message))
                        .toList();
    }

    public static QuestContentLoadReport empty() {
        return new QuestContentLoadReport(0L, List.of());
    }

    public boolean hasErrors() {
        return this.entries.stream().anyMatch(entry -> entry.severity() == Severity.ERROR);
    }

    public List<Entry> entriesFor(ResourceLocation source) {
        if (source == null) {
            return List.of();
        }
        return this.entries.stream().filter(entry -> entry.source().equals(source)).toList();
    }

    public static Builder builder(long generation) {
        return new Builder(generation);
    }

    public enum Severity {
        WARNING,
        ERROR
    }

    public record Entry(ResourceLocation source, String system, Severity severity, String message) {
        public Entry {
            source = source == null ? ResourceLocation.parse("villagerretaliation:unknown") : source;
            system = system == null ? "" : system;
            severity = severity == null ? Severity.ERROR : severity;
            message = message == null ? "" : message;
        }
    }

    public static final class Builder {
        private final long generation;
        private final List<Entry> entries = new ArrayList<>();

        private Builder(long generation) {
            this.generation = generation;
        }

        public Builder add(ResourceLocation source, String system, Severity severity, String message) {
            this.entries.add(new Entry(source, system, severity, message));
            return this;
        }

        public QuestContentLoadReport build() {
            return new QuestContentLoadReport(this.generation, this.entries);
        }
    }
}
