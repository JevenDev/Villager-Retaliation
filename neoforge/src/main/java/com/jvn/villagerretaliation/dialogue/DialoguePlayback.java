package com.jvn.villagerretaliation.dialogue;

import com.jvn.villagerretaliation.dialogue.normal.DialogueTextSegment;
import java.util.ArrayList;
import java.util.List;

public final class DialoguePlayback {
    private static final double LOOK_TRANSITION_MILLIS = 180;
    private final long startedAt;
    private final List<Double> revealTimes;
    private final List<TimedCue> cues;
    private final List<TimedCue> gestureCues;
    private final List<Pause> pauses;
    private final double duration;
    private final boolean instant;
    private boolean skipped;
    private int nextParticle;
    private int nextSound;
    private final List<TimedCue> sounds;
    private final List<TimedCue> particles;

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
        double gazeEnd = 0;
        boolean customGaze = false;
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
                        case LOOK_LEFT, LOOK_RIGHT, LOOK_UP, LOOK_DOWN, LOOK_PLAYER -> {
                            gazeEnd = time + LOOK_TRANSITION_MILLIS;
                            customGaze = true;
                        }
                        case RESET -> {
                            if (customGaze) gazeEnd = time + LOOK_TRANSITION_MILLIS;
                            customGaze = false;
                        }
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
        var gestures = new ArrayList<TimedCue>();
        for (var event : events) {
            if (event.cue().kind() != DialogueCue.Kind.AGREE && event.cue().kind() != DialogueCue.Kind.DISAGREE
                    && event.cue().kind() != DialogueCue.Kind.RESET) continue;
            if (!gestures.isEmpty() && gestures.getLast().time() == event.time()) gestures.removeLast();
            gestures.add(event);
        }
        double gestureEnd = 0;
        for (int index = 0; index < gestures.size(); index++) {
            var event = gestures.get(index);
            if (event.cue().kind() == DialogueCue.Kind.RESET) continue;
            double until = event.time() + gestureDuration(event.cue().kind());
            if (index + 1 < gestures.size()) until = Math.min(until, gestures.get(index + 1).time() + LOOK_TRANSITION_MILLIS);
            gestureEnd = Math.max(gestureEnd, until);
        }
        this.gestureCues = List.copyOf(gestures);
        this.revealTimes = List.copyOf(reveal);
        this.cues = List.copyOf(events);
        this.particles = events.stream().filter(event -> event.cue().kind() == DialogueCue.Kind.PARTICLE).toList();
        this.sounds = events.stream().filter(event -> event.cue().kind() == DialogueCue.Kind.SOUND).toList();
        this.pauses = List.copyOf(waits);
        this.duration = Math.max(Math.max(end, Math.max(gestureEnd, gazeEnd)), events.isEmpty() ? 0 : events.getLast().time());
    }

    private DialoguePlayback(DialoguePlayback source) {
        startedAt = source.startedAt;
        revealTimes = source.revealTimes;
        cues = source.cues;
        gestureCues = source.gestureCues;
        pauses = source.pauses;
        duration = source.duration;
        instant = source.instant;
        skipped = source.skipped;
        particles = source.particles;
        nextParticle = source.nextParticle;
        sounds = source.sounds;
        nextSound = source.nextSound;
    }

    public DialoguePlayback copy() { return new DialoguePlayback(this); }
    public void skip() { skipped = true; nextSound = sounds.size(); }

    public DialoguePlayback retime(List<DialogueTextSegment> segments, long millisPerCharacter) {
        var result = new DialoguePlayback(segments, startedAt, millisPerCharacter);
        result.skipped = skipped;
        result.nextParticle = Math.min(nextParticle, result.particles.size());
        result.nextSound = Math.min(nextSound, result.sounds.size());
        return result;
    }

    public List<DialogueCue> pollParticles(long now) {
        double elapsed = skipped ? Double.POSITIVE_INFINITY : elapsed(now);
        var due = new ArrayList<DialogueCue>();
        while (nextParticle < particles.size() && particles.get(nextParticle).time() <= elapsed)
            due.add(particles.get(nextParticle++).cue());
        return List.copyOf(due);
    }

    public List<DialogueCue> pollSounds(long now) {
        var due = new ArrayList<DialogueCue>();
        double elapsed = elapsed(now);
        while (nextSound < sounds.size() && sounds.get(nextSound).time() <= elapsed)
            due.add(sounds.get(nextSound++).cue());
        return List.copyOf(due);
    }

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
        }
        Pose pose = interpolate(from, target, (elapsed - turnStarted) / LOOK_TRANSITION_MILLIS);
        if (skipped) return pose;
        float yaw = 0;
        float pitch = 0;
        for (var gesture : gestureCues) {
            if (gesture.time() > elapsed) break;
            float retained = 1 - smoothstep((elapsed - gesture.time()) / LOOK_TRANSITION_MILLIS);
            yaw *= retained;
            pitch *= retained;
            if (gesture.cue().kind() == DialogueCue.Kind.RESET) continue;
            double progress = (elapsed - gesture.time()) / gestureDuration(gesture.cue().kind());
            if (progress >= 1) continue;
            double envelope = Math.sin(progress * Math.PI);
            float movement = (float) (Math.sin(progress * Math.PI * 4) * envelope * envelope);
            if (gesture.cue().kind() == DialogueCue.Kind.AGREE) pitch += movement * 12;
            else yaw += movement * 18;
        }
        return new Pose(pose.yaw() + yaw, pose.pitch() + pitch);
    }

    private double elapsed(long now) { return Math.max(0L, now - startedAt); }

    private static double gestureDuration(DialogueCue.Kind kind) {
        return kind == DialogueCue.Kind.AGREE ? 650 : 800;
    }

    private static float smoothstep(double progress) {
        float amount = (float) Math.clamp(progress, 0, 1);
        return amount * amount * (3 - 2 * amount);
    }

    private static Pose interpolate(Pose from, Pose to, double progress) {
        float amount = smoothstep(progress);
        return new Pose(from.yaw() + (to.yaw() - from.yaw()) * amount,
                from.pitch() + (to.pitch() - from.pitch()) * amount);
    }
}
