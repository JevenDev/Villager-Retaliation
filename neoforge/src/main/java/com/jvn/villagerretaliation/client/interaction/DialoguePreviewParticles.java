package com.jvn.villagerretaliation.client.interaction;

import com.jvn.villagerretaliation.dialogue.DialogueCue;
import com.jvn.villagerretaliation.dialogue.DialoguePlayback;
import com.jvn.villagerretaliation.mixin.client.ParticleEngineAccessor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;

public final class DialoguePreviewParticles {
    private static final Map<Particle, Float> CONTROLLED = new WeakHashMap<>();
    private final List<Active> particles = new ArrayList<>();
    private DialoguePlayback playback;
    private long lastPosition = -1;
    private int nextCue;

    private static final class Active {
        final Particle particle;
        final double startedAt;
        int ticks;

        Active(Particle particle, double startedAt) {
            this.particle = particle;
            this.startedAt = startedAt;
        }
    }

    public static boolean controls(Particle particle) { return CONTROLLED.containsKey(particle); }
    public static float partialTick(Particle particle, float fallback) {
        Float sampled = CONTROLLED.get(particle);
        return sampled == null ? fallback : sampled;
    }

    public void update(Entity speaker, DialoguePlayback clock, long now) {
        long position = clock.positionMillis(now);
        if (playback != clock || position < lastPosition) {
            clear();
            playback = clock;
        }
        if (speaker == null || !speaker.level().isClientSide()) return;
        var cues = clock.timelineCues();
        while (nextCue < cues.size() && cues.get(nextCue).time() <= position) {
            var event = cues.get(nextCue++);
            if (event.cue().kind() == DialogueCue.Kind.PARTICLE) spawn(speaker, event, position);
        }
        for (var active : particles) advance(active, position);
        particles.removeIf(active -> !active.particle.isAlive());
        lastPosition = position;
    }

    private void spawn(Entity speaker, DialoguePlayback.TimedCue event, long position) {
        if (!(BuiltInRegistries.PARTICLE_TYPE.get(event.cue().particle()) instanceof SimpleParticleType type)) return;
        var engine = Minecraft.getInstance().particleEngine;
        var random = speaker.getRandom();
        for (int index = 0; index < (int) event.cue().value(); index++) {
            var particle = ((ParticleEngineAccessor) engine).villagerretaliation$makeParticle(type,
                    speaker.getX() + (random.nextDouble() - 0.5) * speaker.getBbWidth(),
                    speaker.getEyeY() + 0.15 + random.nextDouble() * 0.2,
                    speaker.getZ() + (random.nextDouble() - 0.5) * speaker.getBbWidth(),
                    (random.nextDouble() - 0.5) * 0.02, 0.02, (random.nextDouble() - 0.5) * 0.02);
            if (particle == null) continue;
            var active = new Active(particle, event.time());
            advance(active, position);
            if (!particle.isAlive()) continue;
            particles.add(active);
            engine.add(particle);
        }
    }

    private static void advance(Active active, long position) {
        double age = Math.max(0, (position - active.startedAt) / 50.0);
        int ticks = (int) Math.floor(age) + 1;
        if (ticks > active.particle.getLifetime()) {
            active.particle.remove();
            return;
        }
        while (active.ticks < ticks && active.particle.isAlive()) {
            active.particle.tick();
            active.ticks++;
        }
        CONTROLLED.put(active.particle, (float) (age - Math.floor(age)));
    }

    public void clear() {
        particles.forEach(active -> active.particle.remove());
        particles.clear();
        playback = null;
        lastPosition = -1;
        nextCue = 0;
    }
}
