package com.jvn.villagerretaliation.authoring.client;

public record EditorLayout(float contentFraction, float detailsFraction, boolean compact) {
    public static EditorLayout forWidth(float width) {
        if (!Float.isFinite(width) || width <= 0) throw new IllegalArgumentException("Invalid editor width");
        boolean compact = width < 1200;
        float content = Math.min(compact ? 240 : 300, width * 0.25F);
        float details = Math.min(380, width * 0.29F);
        if (!compact) content = Math.min(content, width - details - 600);
        return new EditorLayout(content / width, details / (width - content), compact);
    }
}
