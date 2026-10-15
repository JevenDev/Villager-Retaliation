package com.jvn.villagerretaliation.authoring.client;

public record EditorLayout(float contentFraction, float detailsFraction, boolean compact) {
    public static EditorLayout forWidth(float width) {
        if (!Float.isFinite(width) || width <= 0) throw new IllegalArgumentException("Invalid editor width");
        boolean compact = width < 1200;
        float content = Math.min(compact ? 210 : 250, width * 0.25F);
        float details = Math.min(380, width * 0.29F);
        return new EditorLayout(content / width, details / (width - content), compact);
    }
}
