package com.jvn.villagerretaliation.campaign.content;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

public record CampaignReferenceReport(long generation, Map<Key, Node> nodes, List<Reference> references) {
    public CampaignReferenceReport {
        nodes = Collections.unmodifiableMap(new LinkedHashMap<>(nodes));
        references = List.copyOf(references);
    }

    public static CampaignReferenceReport build(CampaignContentCatalog content) {
        return new CampaignReferenceCollector(content).collect();
    }

    public List<Match> trace(Key start, Direction direction, int depth) {
        if (!nodes.containsKey(start)) throw new IllegalArgumentException("Unknown story reference: " + start.text());
        if (depth < 1 || depth > 16) throw new IllegalArgumentException("Reference depth must be between 1 and 16.");
        var adjacency = new LinkedHashMap<Key, List<Reference>>();
        for (var reference : references) {
            Key source = direction == Direction.DEPENDENCIES ? reference.from() : reference.to();
            adjacency.computeIfAbsent(source, unused -> new ArrayList<>()).add(reference);
        }
        var visited = new LinkedHashSet<Key>();
        visited.add(start);
        var pending = new ArrayList<Key>();
        pending.add(start);
        var distances = new LinkedHashMap<Key, Integer>();
        distances.put(start, 0);
        var matches = new ArrayList<Match>();
        var reported = new LinkedHashSet<Reference>();
        for (int position = 0; position < pending.size(); position++) {
            Key current = pending.get(position);
            int distance = distances.get(current) + 1;
            if (distance > depth) continue;
            for (var reference : adjacency.getOrDefault(current, List.of())) {
                if (reported.add(reference)) matches.add(new Match(reference, distance));
                Key target = direction == Direction.DEPENDENCIES ? reference.to() : reference.from();
                if (visited.add(target)) {
                    distances.put(target, distance);
                    pending.add(target);
                }
            }
        }
        return List.copyOf(matches);
    }

    public enum Kind {
        CAMPAIGN, CHAPTER, MILESTONE, OUTCOME, CHARACTER, LOCATION, FACT, REWARD, EVIDENCE, VARIANT,
        QUEST, CONVERSATION, SCENE, FIXTURE, SOCIAL_PROGRESSION, SOCIAL_SKILL, RELATIONSHIP, FACTION, REACTION
    }

    public enum Direction { DEPENDENCIES, DEPENDENTS }

    public enum Relation {
        INCLUDES, REQUIRES, PROVIDER, READS, WRITES, COMMITS, GRANTS, CATCH_UP, OBSERVES,
        SPEAKS, INVESTIGATES, FOLLOWS, STARTS, LOCATED_AT, REPLACES, CHANGES, BLOCKS_ON_START, BLOCKS_ON_COMPLETION
    }

    public record Key(Kind kind, ResourceLocation owner, List<String> path) {
        public Key {
            Objects.requireNonNull(kind);
            Objects.requireNonNull(owner);
            path = List.copyOf(path);
        }

        public static Key of(Kind kind, ResourceLocation owner, String... path) {
            return new Key(kind, owner, List.of(path));
        }

        public String text() {
            String suffix = path.stream().map(CampaignReferenceReport::escape).collect(java.util.stream.Collectors.joining("/"));
            return kind.name().toLowerCase(Locale.ROOT) + "/" + escape(owner.toString()) + (suffix.isEmpty() ? "" : "/" + suffix);
        }
    }

    public record Source(ResourceLocation resource, String pack, String pointer) {
        public Source {
            Objects.requireNonNull(resource);
            pack = pack == null ? "" : pack;
            pointer = pointer == null ? "" : pointer;
        }

        public Source child(String... tokens) {
            String suffix = java.util.Arrays.stream(tokens).map(CampaignReferenceReport::escape)
                    .collect(java.util.stream.Collectors.joining("/", "/", ""));
            return new Source(resource, pack, pointer + suffix);
        }

        public String text() {
            return resource + "#" + (pointer.isEmpty() ? "/" : pointer) + (pack.isBlank() ? "" : " [" + pack + "]");
        }
    }

    public record Node(Key key, List<Source> declarations) {
        public Node { declarations = List.copyOf(declarations); }
        public boolean missing() { return declarations.isEmpty(); }
    }

    public record Reference(Key from, Key to, Relation relation, Source source) { }
    public record Match(Reference reference, int depth) { }

    private static String escape(String value) {
        return value.replace("~", "~0").replace("/", "~1");
    }
}
