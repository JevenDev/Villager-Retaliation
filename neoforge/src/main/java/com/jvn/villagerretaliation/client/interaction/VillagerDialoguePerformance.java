package com.jvn.villagerretaliation.client.interaction;

import com.jvn.villagerretaliation.client.villager.VillagerModelPreviewRenderContext;
import com.jvn.villagerretaliation.dialogue.DialoguePlayback;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public final class VillagerDialoguePerformance {
    private static LivingEntity speaker;
    private static DialoguePlayback playback;
    private static long refreshedAt;

    private VillagerDialoguePerformance() { }

    public static void update(Entity entity, DialoguePlayback clock) {
        speaker = entity instanceof LivingEntity living ? living : null;
        playback = speaker == null ? null : clock;
        refreshedAt = Util.getMillis();
    }

    public static void clear() {
        speaker = null;
        playback = null;
    }

    public static void apply(LivingEntity entity, ModelPart head) {
        long now = Util.getMillis();
        if (now - refreshedAt > 250) clear();
        if (entity != speaker || playback == null || VillagerModelPreviewRenderContext.isRenderingInventoryPreview(entity)) return;
        float playerYaw = 0;
        float playerPitch = 0;
        var player = Minecraft.getInstance().player;
        if (player != null && !VillagerModelPreviewRenderContext.isRendering(entity)) {
            var delta = player.getEyePosition().subtract(entity.getEyePosition());
            playerYaw = Mth.clamp(Mth.wrapDegrees((float) Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90 - entity.yBodyRot), -70, 70);
            playerPitch = Mth.clamp((float) -Math.toDegrees(Math.atan2(delta.y, Math.sqrt(delta.x * delta.x + delta.z * delta.z))), -45, 45);
        }
        var pose = playback.pose(now, head.yRot * Mth.RAD_TO_DEG, head.xRot * Mth.RAD_TO_DEG, playerYaw, playerPitch);
        head.yRot = pose.yaw() * Mth.DEG_TO_RAD;
        head.xRot = pose.pitch() * Mth.DEG_TO_RAD;
    }
}
