package com.jvn.villagerretaliation.authoring.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class EditorGraphSelection {
    private EditorGraphSelection() { }

    static Set<String> box(List<EditorGraph.Card> cards, Map<String, float[]> positions, float x1, float y1, float x2, float y2) {
        var selected = new LinkedHashSet<String>();
        for (var card : cards) {
            float[] point = positions.get(card.node().id());
            if (point != null && point[0] <= Math.max(x1, x2) && point[0] + EditorGraph.WIDTH >= Math.min(x1, x2)
                    && point[1] <= Math.max(y1, y2) && point[1] + card.height() >= Math.min(y1, y2)) selected.add(card.node().id());
        }
        return selected;
    }

    static Map<String, float[]> layout(List<EditorGraph.Card> cards, Map<String, float[]> positions, Set<String> selection, String action) {
        var result = new LinkedHashMap<String, float[]>();
        positions.forEach((id, point) -> result.put(id, point.clone()));
        var selected = new ArrayList<>(cards.stream().filter(card -> selection.contains(card.node().id()) && result.containsKey(card.node().id())).toList());
        if (selected.size() < 2) throw new IllegalArgumentException("Select at least two nodes");
        if (action.equals("Arrange selection")) {
            float left = selected.stream().map(card -> result.get(card.node().id())[0]).min(Float::compare).orElse(0f);
            float top = selected.stream().map(card -> result.get(card.node().id())[1]).min(Float::compare).orElse(0f);
            EditorGraph.arrange(selected).forEach((id, point) -> result.put(id, new float[] {point[0] + left, point[1] + top}));
            return result;
        }
        boolean horizontal = action.equals("Align left") || action.equals("Space horizontally");
        if (!List.of("Align left", "Align top", "Space horizontally", "Space vertically").contains(action)) throw new IllegalArgumentException("Unknown graph alignment");
        int axis = horizontal ? 0 : 1;
        selected.sort(java.util.Comparator.comparingDouble(card -> result.get(card.node().id())[axis]));
        float start = result.get(selected.getFirst().node().id())[axis];
        float span = result.get(selected.getLast().node().id())[axis] + extent(selected.getLast(), horizontal) - start;
        float occupied = (float) selected.stream().mapToDouble(card -> extent(card, horizontal)).sum();
        float gap = Math.max(0, (span - occupied) / (selected.size() - 1));
        boolean spacing = action.startsWith("Space");
        for (var card : selected) {
            result.get(card.node().id())[axis] = start;
            if (spacing) start += extent(card, horizontal) + gap;
        }
        return result;
    }

    static void snap(Map<String, float[]> positions, Set<String> selection, String anchor, float grid) {
        if (!Float.isFinite(grid) || grid <= 0) throw new IllegalArgumentException("Use a positive grid size");
        float[] point = positions.get(anchor);
        if (point == null) return;
        float dx = Math.round(point[0] / grid) * grid - point[0], dy = Math.round(point[1] / grid) * grid - point[1];
        for (String id : selection) {
            float[] selected = positions.get(id);
            if (selected != null) { selected[0] += dx; selected[1] += dy; }
        }
    }

    private static float extent(EditorGraph.Card card, boolean horizontal) { return horizontal ? EditorGraph.WIDTH : card.height(); }
}
