package com.jvn.villagerretaliation.client.reputation;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import org.joml.Matrix4f;

public final class VillagerReputationDebugOverlay {
    private VillagerReputationDebugOverlay() {
    }

    public static void onRenderNameTag(RenderNameTagEvent event) {
        if (!VillagerRetaliationConfig.SHOW_VILLAGER_REPUTATION_DEBUG_OVERLAY.get()
                || !(event.getEntity() instanceof AbstractVillager villager)) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui) {
            return;
        }
        if (VillagerRetaliationConfig.REPUTATION_DEBUG_OVERLAY_REQUIRE_ADVANCED_TOOLTIPS.get()
                && !minecraft.options.advancedItemTooltips) {
            return;
        }
        if (VillagerRetaliationConfig.REPUTATION_DEBUG_OVERLAY_ONLY_WHEN_SNEAKING.get()
                && !minecraft.player.isShiftKeyDown()) {
            return;
        }

        double maxDistance = VillagerRetaliationConfig.REPUTATION_DEBUG_OVERLAY_MAX_DISTANCE.get();
        if (minecraft.player.distanceToSqr(villager) > maxDistance * maxDistance) {
            return;
        }

        VillagerReputationClientCache.get(villager.getUUID(), villager.getId())
                .map(VillagerReputationDebugOverlay::format)
                .ifPresent(text -> renderOverlay(event, villager, text));
    }

    public static void onClientTick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        VillagerReputationClientCache.pruneMissing();
    }

    public static void onLoggingOut(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        VillagerReputationClientCache.clear();
    }

    private static String format(VillagerReputationClientCache.DisplayEntry entry) {
        boolean showNumber = VillagerRetaliationConfig.REPUTATION_DEBUG_OVERLAY_SHOW_NUMBER.get();
        boolean showTier = VillagerRetaliationConfig.REPUTATION_DEBUG_OVERLAY_SHOW_TIER.get();
        if (showNumber && showTier) {
            return "Reputation: " + entry.reputation() + " (" + entry.level().name() + ")";
        }
        if (showNumber) {
            return "Reputation: " + entry.reputation();
        }
        if (showTier) {
            return entry.level().name();
        }
        return "";
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void renderOverlay(RenderNameTagEvent event, AbstractVillager villager, String text) {
        if (text.isEmpty()) {
            return;
        }

        EntityRenderer renderer = event.getEntityRenderer();
        Entity entity = event.getEntity();
        Vec3 nameTagAttachment = entity.getAttachments().getNullable(EntityAttachment.NAME_TAG, 0, entity.getViewYRot(event.getPartialTick()));
        if (nameTagAttachment == null) {
            return;
        }

        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource bufferSource = event.getMultiBufferSource();
        poseStack.pushPose();
        poseStack.translate(nameTagAttachment.x, nameTagAttachment.y + 0.85D, nameTagAttachment.z);
        poseStack.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
        poseStack.scale(0.025F, -0.025F, 0.025F);

        Matrix4f pose = poseStack.last().pose();
        Font font = renderer.getFont();
        Component component = Component.literal(text);
        float x = -font.width(component) / 2.0F;
        int background = ((int) (Minecraft.getInstance().options.getBackgroundOpacity(0.25F) * 255.0F)) << 24;
        boolean seeThrough = !villager.isDiscrete();
        font.drawInBatch(component, x, 0.0F, 0xFFDDDDDD, false, pose, bufferSource,
                seeThrough ? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.NORMAL, background, event.getPackedLight());
        if (seeThrough) {
            font.drawInBatch(component, x, 0.0F, 0xFFFFFFFF, false, pose, bufferSource, Font.DisplayMode.NORMAL, 0, event.getPackedLight());
        }
        poseStack.popPose();
    }
}
