package com.jvn.villagerretaliation.client.interaction;

import com.jvn.villagerretaliation.network.QuestTrackerSyncPayload;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Places quest prerequisites into a compact left-to-right advancement-style graph. */
final class QuestlineGraphLayout {
    static final int COLUMN_GAP = 42;
    static final int ROW_GAP = 30;

    private QuestlineGraphLayout() {
    }

    static Layout arrange(List<QuestTrackerSyncPayload.QuestlineNode> source) {
        if (source == null || source.isEmpty()) {
            return Layout.EMPTY;
        }
        Map<String, QuestTrackerSyncPayload.QuestlineNode> nodes = new LinkedHashMap<>();
        for (QuestTrackerSyncPayload.QuestlineNode node : source) {
            if (node != null && !node.questId().isBlank()) {
                nodes.putIfAbsent(node.questId(), node);
            }
        }
        if (nodes.isEmpty()) {
            return Layout.EMPTY;
        }

        Map<String, List<String>> parents = new LinkedHashMap<>();
        Map<String, List<String>> primaryChildren = new LinkedHashMap<>();
        for (QuestTrackerSyncPayload.QuestlineNode node : nodes.values()) {
            List<String> knownParents = node.parentQuestIds().stream().filter(nodes::containsKey).distinct().toList();
            parents.put(node.questId(), knownParents);
            if (!knownParents.isEmpty()) {
                primaryChildren.computeIfAbsent(knownParents.getFirst(), ignored -> new ArrayList<>()).add(node.questId());
            }
        }
        Comparator<String> nodeOrder = Comparator
                .comparing((String id) -> nodes.get(id).title(), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(id -> id);
        primaryChildren.values().forEach(children -> children.sort(nodeOrder));

        Map<String, Integer> depths = new HashMap<>();
        for (String id : nodes.keySet()) {
            depth(id, parents, depths, new HashSet<>());
        }

        List<String> roots = nodes.keySet().stream()
                .filter(id -> parents.getOrDefault(id, List.of()).isEmpty())
                .sorted(nodeOrder)
                .toList();
        Map<String, Integer> rows = new HashMap<>();
        int[] nextRow = {0};
        Set<String> visiting = new HashSet<>();
        for (String root : roots) {
            assignRow(root, primaryChildren, rows, nextRow, visiting);
        }
        for (String id : nodes.keySet().stream().sorted(nodeOrder).toList()) {
            if (!rows.containsKey(id)) {
                assignRow(id, primaryChildren, rows, nextRow, visiting);
            }
        }

        Map<String, PositionedNode> positioned = new LinkedHashMap<>();
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (QuestTrackerSyncPayload.QuestlineNode node : nodes.values()) {
            int x = depths.getOrDefault(node.questId(), 0) * COLUMN_GAP;
            int y = rows.getOrDefault(node.questId(), 0);
            positioned.put(node.questId(), new PositionedNode(node, x, y));
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
        }

        List<Edge> edges = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : parents.entrySet()) {
            for (String parent : entry.getValue()) {
                edges.add(new Edge(parent, entry.getKey()));
            }
        }
        return new Layout(Map.copyOf(positioned), List.copyOf(edges), minX, minY, maxX, maxY);
    }

    private static int depth(
            String id,
            Map<String, List<String>> parents,
            Map<String, Integer> depths,
            Set<String> visiting) {
        Integer cached = depths.get(id);
        if (cached != null) {
            return cached;
        }
        if (!visiting.add(id)) {
            return 0;
        }
        int value = 0;
        for (String parent : parents.getOrDefault(id, List.of())) {
            value = Math.max(value, depth(parent, parents, depths, visiting) + 1);
        }
        visiting.remove(id);
        depths.put(id, value);
        return value;
    }

    private static int assignRow(
            String id,
            Map<String, List<String>> children,
            Map<String, Integer> rows,
            int[] nextRow,
            Set<String> visiting) {
        Integer cached = rows.get(id);
        if (cached != null) {
            return cached;
        }
        if (!visiting.add(id)) {
            int row = nextRow[0] * ROW_GAP;
            nextRow[0]++;
            rows.put(id, row);
            return row;
        }
        List<String> childIds = children.getOrDefault(id, List.of());
        int row;
        if (childIds.isEmpty()) {
            row = nextRow[0] * ROW_GAP;
            nextRow[0]++;
        } else {
            int first = assignRow(childIds.getFirst(), children, rows, nextRow, visiting);
            int last = first;
            for (int index = 1; index < childIds.size(); index++) {
                last = assignRow(childIds.get(index), children, rows, nextRow, visiting);
            }
            row = (first + last) / 2;
        }
        visiting.remove(id);
        rows.put(id, row);
        return row;
    }

    record PositionedNode(QuestTrackerSyncPayload.QuestlineNode node, int x, int y) {
    }

    record Edge(String parentQuestId, String childQuestId) {
    }

    record Layout(
            Map<String, PositionedNode> nodes,
            List<Edge> edges,
            int minX,
            int minY,
            int maxX,
            int maxY) {
        private static final Layout EMPTY = new Layout(Map.of(), List.of(), 0, 0, 0, 0);
    }
}
