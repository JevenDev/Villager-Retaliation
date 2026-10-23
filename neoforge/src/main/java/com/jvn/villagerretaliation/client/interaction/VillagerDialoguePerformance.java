package com.jvn.villagerretaliation.client.interaction;

import com.jvn.villagerretaliation.client.villager.VillagerModelPreviewRenderContext;
import com.jvn.villagerretaliation.dialogue.DialoguePlayback;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public final class VillagerDialoguePerformance {
    private static LivingEntity speaker;
    private static DialoguePlayback playback;

    private VillagerDialoguePerformance() { }

    public static void update(Entity entity, DialoguePlayback clock) {
        speaker = entity instanceof LivingEntity living ? living : null;
        playback = speaker == null ? null : clock;
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

    public static void clear() {
        speaker = null;
        playback = null;
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
        var pose = playback.pose(now, head.yRot * Mth.RAD_TO_DEG, head.xRot * Mth.RAD_TO_DEG, playerYaw, playerPitch);
        head.yRot = pose.yaw() * Mth.DEG_TO_RAD;
        head.xRot = pose.pitch() * Mth.DEG_TO_RAD;
    }
}
