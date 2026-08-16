package com.jvn.villagerretaliation.client.reputation;

import com.jvn.villagerretaliation.client.villager.VillagerHungerClientCache;
import com.jvn.villagerretaliation.client.villager.VillagerModelPreviewRenderContext;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import org.joml.Matrix4f;

public final class VillagerReputationDebugOverlay {
    private VillagerReputationDebugOverlay() {
    }

    public static void onRenderNameTag(RenderNameTagEvent event) {
        if (VillagerModelPreviewRenderContext.isRendering(event.getEntity())
                || !VillagerRetaliationConfig.SHOW_VILLAGER_REPUTATION_DEBUG_OVERLAY.get()
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
                .map(entry -> format(villager, entry))
                .ifPresent(lines -> renderOverlay(event, villager, lines));
    }

    public static void onClientTick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        VillagerReputationClientCache.pruneMissing();
    }

    public static void onLoggingOut(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        VillagerReputationClientCache.clear();
    }

    private static List<String> format(AbstractVillager villager, VillagerReputationClientCache.DisplayEntry entry) {
        List<String> lines = new ArrayList<>();
        boolean showNumber = VillagerRetaliationConfig.REPUTATION_DEBUG_OVERLAY_SHOW_NUMBER.get();
        boolean showTier = VillagerRetaliationConfig.REPUTATION_DEBUG_OVERLAY_SHOW_TIER.get();
        if (showNumber && showTier) {
            lines.add(I18n.get(
                    "villagerretaliation.reputation.debug.value_and_level",
                    entry.reputation(),
                    VillagerReputationIconSet.formatLevel(entry.level())
            ));
        } else if (showNumber) {
            lines.add(I18n.get("villagerretaliation.reputation.value_format", entry.reputation()));
        } else if (showTier) {
            lines.add(VillagerReputationIconSet.formatLevel(entry.level()));
        }

        if (VillagerRetaliationConfig.REPUTATION_DEBUG_OVERLAY_SHOW_HEALTH.get()) {
            lines.add(I18n.get(
                    "villagerretaliation.reputation.debug.health",
                    String.format(Locale.ROOT, "%.1f", villager.getHealth()),
                    String.format(Locale.ROOT, "%.1f", villager.getMaxHealth())
            ));
        }
        if (VillagerRetaliationConfig.REPUTATION_DEBUG_OVERLAY_SHOW_ARMOR.get()) {
            lines.add(I18n.get("villagerretaliation.reputation.debug.armor", villager.getArmorValue()));
        }
        if (VillagerRetaliationConfig.REPUTATION_DEBUG_OVERLAY_SHOW_HUNGER.get()
                && villager instanceof Villager ordinaryVillager) {
            lines.add(I18n.get(
                    "villagerretaliation.reputation.debug.hunger",
                    VillagerHungerClientCache.hunger(ordinaryVillager),
                    20));
        }

        return lines;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void renderOverlay(RenderNameTagEvent event, AbstractVillager villager, List<String> lines) {
        if (lines.isEmpty()) {
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
        int background = ((int) (Minecraft.getInstance().options.getBackgroundOpacity(0.25F) * 255.0F)) << 24;
        boolean seeThrough = !villager.isDiscrete();
        int lineHeight = font.lineHeight + 1;
        for (int index = 0; index < lines.size(); index++) {
            Component component = Component.literal(lines.get(index));
            float x = -font.width(component) / 2.0F;
            float y = index * lineHeight;
            font.drawInBatch(component, x, y, 0xFFDDDDDD, false, pose, bufferSource,
                    seeThrough ? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.NORMAL, background, event.getPackedLight());
            if (seeThrough) {
                font.drawInBatch(component, x, y, 0xFFFFFFFF, false, pose, bufferSource, Font.DisplayMode.NORMAL, 0, event.getPackedLight());
            }
        }
        poseStack.popPose();
    }
}
