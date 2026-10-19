package com.jvn.villagerretaliation.authoring.client;

public record EditorLayout(float contentFraction, float detailsFraction, boolean compact) {
    public static EditorLayout forWidth(float width) {
        if (!Float.isFinite(width) || width <= 0) throw new IllegalArgumentException("Invalid editor width");
        boolean compact = width < 1200;
        float content = compact ? Math.min(240, width * 0.25F) : Math.min(380, (width - 600) / 2);
        float details = compact ? Math.min(380, width * 0.29F) : content;
        return new EditorLayout(content / width, details / (width - content), compact);
    }
}
