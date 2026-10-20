package com.jvn.villagerretaliation.dialogue;

import com.jvn.villagerretaliation.dialogue.normal.DialogueTextSegment;
import java.util.ArrayList;
import java.util.List;

public final class DialoguePlayback {
    private static final double LOOK_TRANSITION_MILLIS = 180;
    private final long startedAt;
    private final List<Double> revealTimes;
    private final List<TimedCue> cues;
    private final List<Pause> pauses;
    private final double duration;
    private final boolean instant;
    private boolean skipped;

    private record TimedCue(double time, DialogueCue cue) { }
    private record Pause(double start, double end) { }
    public record Pose(float yaw, float pitch) { }

    public DialoguePlayback(List<DialogueTextSegment> segments, long startedAt, long millisPerCharacter) {
        this.startedAt = startedAt;
        this.instant = millisPerCharacter <= 0;
        var reveal = new ArrayList<Double>();
        var events = new ArrayList<TimedCue>();
        var waits = new ArrayList<Pause>();
        double time = 0;
        double speed = 1;
        double end = 0;
        double gestureEnd = 0;
        for (var segment : segments) {
            int cueIndex = 0;
            int offset = 0;
            while (offset <= segment.text().length()) {
                while (cueIndex < segment.cues().size() && segment.cues().get(cueIndex).offset() == offset) {
                    var cue = segment.cues().get(cueIndex++).cue();
                    events.add(new TimedCue(time, cue));
                    switch (cue.kind()) {
                        case PAUSE -> {
                            double until = time + (instant ? 0 : cue.value());
                            waits.add(new Pause(time, until));
                            time = until;
                            end = Math.max(end, time);
                        }
                        case SPEED -> speed = cue.value();
                        case AGREE, DISAGREE -> gestureEnd = time + gestureDuration(cue.kind());
                        case RESET -> gestureEnd = Math.min(gestureEnd, time);
                        default -> { }
                    }
                }
                if (offset == segment.text().length()) break;
                reveal.add(time);
                end = Math.max(end, time);
                offset += Character.charCount(segment.text().codePointAt(offset));
                time += instant ? 0 : millisPerCharacter / speed;
            }
        }
        this.revealTimes = List.copyOf(reveal);
        this.cues = List.copyOf(events);
        this.pauses = List.copyOf(waits);
        this.duration = Math.max(Math.max(end, gestureEnd), events.isEmpty() ? 0 : events.getLast().time());
    }

    private DialoguePlayback(DialoguePlayback source) {
        startedAt = source.startedAt;
        revealTimes = source.revealTimes;
        cues = source.cues;
        pauses = source.pauses;
        duration = source.duration;
        instant = source.instant;
        skipped = source.skipped;
    }

    public DialoguePlayback copy() { return new DialoguePlayback(this); }
    public void skip() { skipped = true; }

    public int visibleCharacters(long now) {
        if (skipped || instant) return revealTimes.size();
        double elapsed = elapsed(now);
        int start = 0;
        int end = revealTimes.size();
        while (start < end) {
            int middle = (start + end) >>> 1;
            if (revealTimes.get(middle) <= elapsed) start = middle + 1;
            else end = middle;
        }
        return start;
    }

    public boolean complete(long now) { return skipped || instant || elapsed(now) >= duration; }

    public boolean paused(long now) {
        if (skipped || instant) return false;
        double elapsed = elapsed(now);
        return pauses.stream().anyMatch(pause -> elapsed >= pause.start() && elapsed < pause.end());
    }

    public boolean talking(long now) {
        return !paused(now) && visibleCharacters(now) < revealTimes.size();
    }

    public Pose pose(long now, float neutralYaw, float neutralPitch, float playerYaw, float playerPitch) {
        double elapsed = skipped ? duration + LOOK_TRANSITION_MILLIS : elapsed(now);
        Pose from = new Pose(neutralYaw, neutralPitch);
        Pose target = from;
        double turnStarted = -LOOK_TRANSITION_MILLIS;
        TimedCue gesture = null;
        for (var event : cues) {
            if (event.time() > elapsed) break;
            Pose next = switch (event.cue().kind()) {
                case LOOK_LEFT -> new Pose(35, 0);
                case LOOK_RIGHT -> new Pose(-35, 0);
                case LOOK_UP -> new Pose(0, -22);
                case LOOK_DOWN -> new Pose(0, 22);
                case LOOK_PLAYER -> new Pose(playerYaw, playerPitch);
                case RESET -> new Pose(neutralYaw, neutralPitch);
                default -> null;
            };
            if (next != null) {
                from = interpolate(from, target, (event.time() - turnStarted) / LOOK_TRANSITION_MILLIS);
                target = next;
                turnStarted = event.time();
            }
            if (event.cue().kind() == DialogueCue.Kind.AGREE || event.cue().kind() == DialogueCue.Kind.DISAGREE)
                gesture = event;
            if (event.cue().kind() == DialogueCue.Kind.RESET) gesture = null;
        }
        Pose pose = interpolate(from, target, (elapsed - turnStarted) / LOOK_TRANSITION_MILLIS);
        if (gesture == null || skipped) return pose;
        double progress = (elapsed - gesture.time()) / gestureDuration(gesture.cue().kind());
        if (progress < 0 || progress >= 1) return pose;
        float movement = (float) (Math.sin(progress * Math.PI * 4) * Math.sin(progress * Math.PI));
        return gesture.cue().kind() == DialogueCue.Kind.AGREE
                ? new Pose(pose.yaw(), pose.pitch() + movement * 12)
                : new Pose(pose.yaw() + movement * 18, pose.pitch());
    }

    private double elapsed(long now) { return Math.max(0L, now - startedAt); }

    private static double gestureDuration(DialogueCue.Kind kind) {
        return kind == DialogueCue.Kind.AGREE ? 650 : 800;
    }

    private static Pose interpolate(Pose from, Pose to, double progress) {
        float amount = (float) Math.clamp(progress, 0, 1);
        amount = amount * amount * (3 - 2 * amount);
        return new Pose(from.yaw() + (to.yaw() - from.yaw()) * amount,
                from.pitch() + (to.pitch() - from.pitch()) * amount);
    }
}
