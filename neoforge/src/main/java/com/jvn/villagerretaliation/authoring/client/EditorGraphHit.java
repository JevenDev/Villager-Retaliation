package com.jvn.villagerretaliation.authoring.client;

final class EditorGraphHit {
    private EditorGraphHit() { }

    static boolean wire(float mx, float my, float x1, float y1, float x2, float y2, float zoom) {
        float curve = Math.max(70 * zoom, Math.abs(x2 - x1) * .45f);
        float px = x1, py = y1;
        for (int i = 1; i <= 32; i++) {
            float t = i / 32f, u = 1 - t;
            float x = u * u * u * x1 + 3 * u * u * t * (x1 + curve) + 3 * u * t * t * (x2 - curve) + t * t * t * x2;
            float y = u * u * u * y1 + 3 * u * u * t * y1 + 3 * u * t * t * y2 + t * t * t * y2;
            float dx = x - px, dy = y - py;
            float length = dx * dx + dy * dy;
            float projection = length == 0 ? 0 : Math.clamp(((mx - px) * dx + (my - py) * dy) / length, 0, 1);
            float ox = mx - px - projection * dx, oy = my - py - projection * dy;
            if (ox * ox + oy * oy <= 36) return true;
            px = x; py = y;
        }
        return false;
    }

    static boolean port(float mouseX, float mouseY, float x, float y, float rowHeight) {
        float halfHeight = Math.min(12, rowHeight / 2);
        return Math.abs(mouseX - x) <= 12 && mouseY >= y - halfHeight && mouseY < y + halfHeight;
    }
}
