package com.jvn.villagerretaliation.client.interaction;

import com.jvn.villagerretaliation.client.villager.VillagerModelPreviewRenderContext;
import com.jvn.villagerretaliation.dialogue.DialoguePlayback;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public final class VillagerDialoguePerformance {
    private static LivingEntity speaker;
    private static DialoguePlayback playback;
    private static java.util.List<Entity> participants = java.util.List.of();
    private static java.util.Map<String, Entity> roles = java.util.Map.of();
    private static final java.util.Map<String, Entity> gazeTargets = new java.util.HashMap<>();

    private VillagerDialoguePerformance() { }

    public static void update(Entity entity, DialoguePlayback clock) {
        update(entity, clock, java.util.List.of(), java.util.Map.of());
    }

    public static void update(Entity entity, DialoguePlayback clock, java.util.List<Entity> subjects,
            java.util.Map<String, Entity> bindings) {
        if (entity != speaker || clock != playback) gazeTargets.clear();
        speaker = entity instanceof LivingEntity living ? living : null;
        playback = speaker == null ? null : clock;
        participants = java.util.List.copyOf(subjects);
        roles = java.util.Map.copyOf(bindings);
    }

    public static void emitParticles(Entity entity, DialoguePlayback clock, long now) {
        if (entity == null || !entity.level().isClientSide()) return;
        var random = entity.getRandom();
        for (var cue : clock.pollParticles(now)) {
            if (!(BuiltInRegistries.PARTICLE_TYPE.get(cue.particle()) instanceof SimpleParticleType particle)) continue;
            for (int index = 0; index < (int) cue.value(); index++)
                entity.level().addParticle(particle,
                        entity.getX() + (random.nextDouble() - 0.5) * entity.getBbWidth(),
                        entity.getEyeY() + 0.15 + random.nextDouble() * 0.2,
                        entity.getZ() + (random.nextDouble() - 0.5) * entity.getBbWidth(),
                        (random.nextDouble() - 0.5) * 0.02, 0.02, (random.nextDouble() - 0.5) * 0.02);
        }
    }

    public static java.util.List<SoundInstance> emitSounds(Entity entity, DialoguePlayback clock, long now) {
        var minecraft = Minecraft.getInstance();
        var result = new java.util.ArrayList<SoundInstance>();
        for (var cue : clock.pollSounds(now)) {
            var sound = cue.sound();
            if (minecraft.getSoundManager().getSoundEvent(sound.id()) == null) {
                com.mojang.logging.LogUtils.getLogger().warn("Unknown dialogue cue sound: {}", sound.id());
                continue;
            }
            var instance = new SimpleSoundInstance(sound.id(), SoundSource.VOICE, sound.volume(), sound.pitch(),
                    RandomSource.create(), false, 0, entity == null ? SoundInstance.Attenuation.NONE : SoundInstance.Attenuation.LINEAR,
                    entity == null ? 0 : entity.getX(), entity == null ? 0 : entity.getEyeY(),
                    entity == null ? 0 : entity.getZ(), entity == null);
            minecraft.getSoundManager().play(instance);
            result.add(instance);
        }
        return java.util.List.copyOf(result);
    }

    public static void clear() {
        speaker = null;
        playback = null;
        participants = java.util.List.of();
        roles = java.util.Map.of();
        gazeTargets.clear();
    }

    public static Entity selectTarget(Entity speaker, String name, java.util.List<Entity> participants,
            java.util.Map<String, Entity> roles) {
        if (name.equals("nearest_participant")) return participants.stream()
                .filter(entity -> validTarget(speaker, entity))
                .min(java.util.Comparator.comparingDouble((Entity entity) -> entity.distanceToSqr(speaker))
                        .thenComparing(entity -> entity.getUUID().toString())).orElse(null);
        Entity target = name.startsWith("role:") ? roles.get(name.substring(5)) : null;
        return validTarget(speaker, target) ? target : null;
    }

    private static boolean validTarget(Entity speaker, Entity target) {
        return target != null && target != speaker && target.level() == speaker.level() && target.isAlive() && !target.isRemoved();
    }

    private static DialoguePlayback.Pose targetPose(LivingEntity entity, String name, float partialTick) {
        Entity target = name.equals("nearest_participant")
                ? gazeTargets.computeIfAbsent(name, key -> selectTarget(entity, name, participants, roles))
                : selectTarget(entity, name, participants, roles);
        if (!validTarget(entity, target) || name.equals("nearest_participant") && !participants.contains(target)) return null;
        var delta = target.getEyePosition(partialTick).subtract(entity.getEyePosition(partialTick));
        float bodyYaw = Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot);
        float yaw = Mth.clamp(Mth.wrapDegrees((float) Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90 - bodyYaw), -70, 70);
        float pitch = Mth.clamp((float) -Math.toDegrees(Math.atan2(delta.y, Math.sqrt(delta.x * delta.x + delta.z * delta.z))), -45, 45);
        return new DialoguePlayback.Pose(yaw, pitch);
    }

    public static void apply(LivingEntity entity, ModelPart head) {
        apply(entity, head, 1);
    }

    public static void apply(LivingEntity entity, ModelPart head, float partialTick) {
        long now = Util.getMillis();
        var minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof VillagerInteractionScreen screen) screen.updateDialoguePerformance();
        else clear();
        if (entity != speaker || playback == null || VillagerModelPreviewRenderContext.isRenderingInventoryPreview(entity)) return;
        float playerYaw = 0;
        float playerPitch = 0;
        var player = minecraft.player;
        if (player != null && !VillagerModelPreviewRenderContext.isRendering(entity)) {
            var delta = player.getEyePosition(partialTick).subtract(entity.getEyePosition(partialTick));
            float bodyYaw = Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot);
            playerYaw = Mth.clamp(Mth.wrapDegrees((float) Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90 - bodyYaw), -70, 70);
            playerPitch = Mth.clamp((float) -Math.toDegrees(Math.atan2(delta.y, Math.sqrt(delta.x * delta.x + delta.z * delta.z))), -45, 45);
        }
        var pose = playback.pose(now, head.yRot * Mth.RAD_TO_DEG, head.xRot * Mth.RAD_TO_DEG, playerYaw, playerPitch,
                name -> targetPose(entity, name, partialTick));
        head.yRot = pose.yaw() * Mth.DEG_TO_RAD;
        head.xRot = pose.pitch() * Mth.DEG_TO_RAD;
    }
}
