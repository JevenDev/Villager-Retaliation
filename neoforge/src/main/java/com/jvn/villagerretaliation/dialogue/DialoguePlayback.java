package com.jvn.villagerretaliation.dialogue;

import com.jvn.villagerretaliation.dialogue.normal.DialogueTextSegment;
import java.util.ArrayList;
import java.util.List;

public final class DialoguePlayback {
    private static final double LOOK_TRANSITION_MILLIS = 180;
    private static final long PREVIEW_EFFECT_TAIL_MILLIS = 4000;
    private final long startedAt;
    private final List<Double> revealTimes;
    private final List<TimedCue> cues;
    private final List<GestureChange> gestureCues;
    private final List<Pause> pauses;
    private final double duration;
    private final boolean instant;
    private boolean skipped;
    private int nextParticle;
    private int nextSound;
    private boolean playbackPaused;
    private double pausedPosition;
    private double timeOffset;
    private final List<TimedCue> sounds;
    private final List<TimedCue> particles;

    public record TimedCue(double time, DialogueCue cue) { }
    private record Pause(double start, double end) { }
    private record GestureChange(double time, TimedCue gesture) { }
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
        var savedGazes = new ArrayList<Boolean>();
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
                        case LOOK_LEFT, LOOK_RIGHT, LOOK_UP, LOOK_DOWN, LOOK_PLAYER, LOOK_TARGET -> {
                            gazeEnd = time + LOOK_TRANSITION_MILLIS;
                            customGaze = true;
                        }
                        case PUSH_POSE -> savedGazes.add(customGaze);
                        case POP_POSE -> {
                            if (!savedGazes.isEmpty()) {
                                boolean restored = savedGazes.removeLast();
                                if (customGaze || restored) gazeEnd = time + LOOK_TRANSITION_MILLIS;
                                customGaze = restored;
                            }
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
        var gestures = new ArrayList<GestureChange>();
        var savedGestures = new ArrayList<TimedCue>();
        TimedCue activeGesture = null;
        for (var event : events) {
            switch (event.cue().kind()) {
                case PUSH_POSE -> { savedGestures.add(activeGesture); continue; }
                case POP_POSE -> {
                    if (savedGestures.isEmpty()) continue;
                    var restored = savedGestures.removeLast();
                    if (java.util.Objects.equals(activeGesture, restored)) continue;
                    activeGesture = restored;
                }
                case AGREE, DISAGREE -> activeGesture = event;
                case RESET -> activeGesture = null;
                default -> { continue; }
            }
            if (!gestures.isEmpty() && gestures.getLast().time() == event.time()) gestures.removeLast();
            gestures.add(new GestureChange(event.time(), activeGesture));
        }
        double gestureEnd = 0;
        for (int index = 0; index < gestures.size(); index++) {
            var change = gestures.get(index);
            if (change.gesture() == null) continue;
            double until = change.gesture().time() + gestureDuration(change.gesture().cue().kind());
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
        playbackPaused = source.playbackPaused;
        pausedPosition = source.pausedPosition;
        timeOffset = source.timeOffset;
    }

    public DialoguePlayback copy() { return new DialoguePlayback(this); }
    public void skip() { skipped = true; nextSound = sounds.size(); }

    public DialoguePlayback retime(List<DialogueTextSegment> segments, long millisPerCharacter) {
        var result = new DialoguePlayback(segments, startedAt, millisPerCharacter);
        result.skipped = skipped;
        result.nextParticle = Math.min(nextParticle, result.particles.size());
        result.nextSound = Math.min(nextSound, result.sounds.size());
        result.playbackPaused = playbackPaused;
        result.pausedPosition = Math.min(pausedPosition, result.previewDurationMillis());
        result.timeOffset = timeOffset;
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
        if (playbackPaused || skipped) return List.of();
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
        return !playbackPaused && !paused(now) && visibleCharacters(now) < revealTimes.size();
    }

    public Pose pose(long now, float neutralYaw, float neutralPitch, float playerYaw, float playerPitch) {
        return pose(now, neutralYaw, neutralPitch, playerYaw, playerPitch, target -> null);
    }

    public Pose pose(long now, float neutralYaw, float neutralPitch, float playerYaw, float playerPitch,
            java.util.function.Function<String, Pose> targets) {
        double elapsed = skipped ? duration + LOOK_TRANSITION_MILLIS : elapsed(now);
        Pose from = new Pose(neutralYaw, neutralPitch);
        Pose target = from;
        double turnStarted = -LOOK_TRANSITION_MILLIS;
        DialogueCue gaze = null;
        var savedGazes = new ArrayList<DialogueCue>();
        for (var event : cues) {
            if (event.time() > elapsed) break;
            var cue = event.cue();
            if (cue.kind() == DialogueCue.Kind.PUSH_POSE) {
                savedGazes.add(gaze);
                continue;
            }
            Pose next;
            if (cue.kind() == DialogueCue.Kind.POP_POSE) {
                if (savedGazes.isEmpty()) continue;
                gaze = savedGazes.removeLast();
                next = gazePose(gaze, neutralYaw, neutralPitch, playerYaw, playerPitch, targets);
                if (next == null) next = new Pose(neutralYaw, neutralPitch);
            } else {
                next = gazePose(cue, neutralYaw, neutralPitch, playerYaw, playerPitch, targets);
                if (next != null) gaze = cue.kind() == DialogueCue.Kind.RESET ? null : cue;
            }
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
        for (var change : gestureCues) {
            if (change.time() > elapsed) break;
            float retained = 1 - smoothstep((elapsed - change.time()) / LOOK_TRANSITION_MILLIS);
            yaw *= retained;
            pitch *= retained;
            var gesture = change.gesture();
            if (gesture == null) continue;
            double progress = (elapsed - gesture.time()) / gestureDuration(gesture.cue().kind());
            if (progress >= 1) continue;
            double envelope = Math.sin(progress * Math.PI);
            float movement = (float) (Math.sin(progress * Math.PI * 4) * envelope * envelope);
            if (gesture.time() < change.time()) movement *= 1 - retained;
            if (gesture.cue().kind() == DialogueCue.Kind.AGREE) pitch += movement * 12;
            else yaw += movement * 18;
        }
        return new Pose(pose.yaw() + yaw, pose.pitch() + pitch);
    }

    private static Pose gazePose(DialogueCue cue, float neutralYaw, float neutralPitch, float playerYaw, float playerPitch,
            java.util.function.Function<String, Pose> targets) {
        if (cue == null) return new Pose(neutralYaw, neutralPitch);
        return switch (cue.kind()) {
            case LOOK_LEFT -> new Pose(35, 0);
            case LOOK_RIGHT -> new Pose(-35, 0);
            case LOOK_UP -> new Pose(0, -22);
            case LOOK_DOWN -> new Pose(0, 22);
            case LOOK_PLAYER -> new Pose(playerYaw, playerPitch);
            case LOOK_TARGET -> targets.apply(cue.target());
            case RESET -> new Pose(neutralYaw, neutralPitch);
            default -> null;
        };
    }

    public long durationMillis() { return (long) Math.ceil(duration); }
    public long previewDurationMillis() {
        double end = duration;
        if (!particles.isEmpty()) end = Math.max(end, particles.getLast().time() + PREVIEW_EFFECT_TAIL_MILLIS);
        if (!sounds.isEmpty()) end = Math.max(end, sounds.getLast().time() + PREVIEW_EFFECT_TAIL_MILLIS);
        return (long) Math.ceil(end);
    }

    public long positionMillis(long now) { return (long) Math.min(previewDurationMillis(), skipped ? duration : elapsed(now)); }
    public boolean playbackPaused() { return playbackPaused; }
    public List<TimedCue> timelineCues() { return cues; }

    public void pause(long now) {
        pausedPosition = Math.min(previewDurationMillis(), skipped ? duration : elapsed(now));
        playbackPaused = true;
    }

    public void play(long now) {
        if (!playbackPaused) return;
        timeOffset = pausedPosition - (now - startedAt);
        playbackPaused = false;
    }

    public void seek(long position, long now) {
        skipped = false;
        pausedPosition = Math.clamp((double) position, 0, previewDurationMillis());
        timeOffset = pausedPosition - (now - startedAt);
        playbackPaused = true;
        nextParticle = 0;
        while (nextParticle < particles.size() && particles.get(nextParticle).time() <= pausedPosition) nextParticle++;
        nextSound = 0;
        while (nextSound < sounds.size() && sounds.get(nextSound).time() <= pausedPosition) nextSound++;
    }

    public void restart(long now) {
        seek(0, now);
        nextParticle = 0;
        nextSound = 0;
        play(now);
    }

    private double elapsed(long now) {
        return playbackPaused ? pausedPosition : Math.max(0, now - startedAt + timeOffset);
    }

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
