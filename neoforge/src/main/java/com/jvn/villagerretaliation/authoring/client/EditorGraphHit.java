package com.jvn.villagerretaliation.authoring.client;

final class EditorGraphHit {
    private EditorGraphHit() { }

    static boolean port(float mouseX, float mouseY, float x, float y, float rowHeight) {
        float halfHeight = Math.min(12, rowHeight / 2);
        return Math.abs(mouseX - x) <= 12 && mouseY >= y - halfHeight && mouseY < y + halfHeight;
    }
}
