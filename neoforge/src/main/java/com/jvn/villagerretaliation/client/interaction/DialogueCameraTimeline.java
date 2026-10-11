package com.jvn.villagerretaliation.client.interaction;

import com.jvn.villagerretaliation.dialogue.DialogueCamera.Shot;
import java.util.List;
import java.util.function.Function;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class DialogueCameraTimeline {
    private final List<Shot> shots;
    private int index;
    private double elapsed;
    private boolean holding;
    private Pose source;

    public DialogueCameraTimeline(List<Shot> shots, Pose source) {
        if (shots.isEmpty()) throw new IllegalArgumentException("Missing camera shots");
        this.shots = List.copyOf(shots);
        this.source = source;
    }

    public Shot shot() { return shots.get(index); }
    public int index() { return index; }
    public void hold() { holding = true; }

    public Pose update(double seconds, Function<Shot, Pose> destination) {
        return update(seconds, destination, null);
    }

    public Pose update(double seconds, Function<Shot, Pose> destination, Pose rendered) {
        if (!Double.isFinite(seconds) || seconds < 0) throw new IllegalArgumentException("Invalid camera elapsed time");
        boolean useRendered = rendered != null && (elapsed > 0 || shot().durationSeconds() + shot().holdSeconds() > 0);
        if (!holding) elapsed += seconds;
        while (!holding && index + 1 < shots.size() && elapsed >= shot().durationSeconds() + shot().holdSeconds()) {
            source = useRendered ? rendered : destination.apply(shot());
            useRendered = false;
            elapsed -= shot().durationSeconds() + shot().holdSeconds();
            index++;
        }
        return source.interpolate(destination.apply(shot()), shot().progress(elapsed));
    }

    public record Pose(Vec3 position, float yaw, float pitch, double fov) {
        public Pose interpolate(Pose destination, double progress) {
            if (progress <= 0) return this;
            if (progress >= 1) return destination;
            float amount = (float) progress;
            return new Pose(position.lerp(destination.position, progress),
                    Mth.rotLerp(amount, yaw, destination.yaw), Mth.lerp(amount, pitch, destination.pitch),
                    Mth.lerp(progress, fov, destination.fov));
        }
    }
}
