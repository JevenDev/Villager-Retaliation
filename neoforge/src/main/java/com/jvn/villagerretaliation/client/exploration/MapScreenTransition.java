package com.jvn.villagerretaliation.client.exploration;

import java.util.function.LongSupplier;

final class MapScreenTransition {
    private final com.jvn.toucanlib.client.ToucanScreenTransition transition;

    MapScreenTransition() { this(System::nanoTime); }
    MapScreenTransition(LongSupplier clock) {
        transition = new com.jvn.toucanlib.client.ToucanScreenTransition(1250, 600, clock);
    }
    void open() { transition.open(); }
    boolean closing() { return transition.closing(); }
    boolean close(boolean animate) { return transition.close(animate); }
    void finishOpening() { transition.finishOpening(); }
    void suspend() { transition.suspend(); }
    float progress(boolean animate) { return transition.progress(animate); }

    float pitch(float pitch, boolean animate) {
        float progress = progress(animate);
        if (progress >= .4f) return pitch;
        float turn = ease(progress / .4f);
        return pitch + (Math.max(-90, pitch - 6) - pitch) * turn;
    }

    static float ease(float progress) {
        progress = Math.clamp(progress, 0, 1);
        return Math.clamp(progress * progress * progress * (progress * (progress * 6 - 15) + 10), 0, 1);
    }
}
