package com.jvn.villagerretaliation.client.villager;

import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.client.config.VillagerRetaliationClientPreferences;
import com.jvn.villagerretaliation.client.config.VillagerRetaliationServerConfigClient;
import com.jvn.villagerretaliation.client.interaction.VillagerInteractionVisibilityFade;
import com.jvn.villagerretaliation.client.party.PartyRosterClient;
import com.jvn.villagerretaliation.config.VillagerStatDisplayMode;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import net.neoforged.neoforge.common.util.TriState;
import org.joml.Matrix4f;

public final class VillagerStatNameTagOverlay {
    private static final double MAX_DISTANCE = 64.0D;
    private static final int ICON_SIZE = 11;
    private static final int ICON_TEXT_GAP = 2;
    private static final int STAT_GAP = 4;
    private static final float NAMEPLATE_RAISE = 0.5F;
    private static final float ROW_Y = 0.0F;
    private static final ResourceLocation HEALTH_ICON =
            VillagerRetaliation.id("textures/gui/villager_stats/villager_health_stat.png");
    private static final ResourceLocation ARMOR_ICON =
            VillagerRetaliation.id("textures/gui/villager_stats/villager_armor_stat.png");
    private static final ResourceLocation HUNGER_ICON =
            VillagerRetaliation.id("textures/gui/villager_stats/villager_hunger_stat.png");

    private VillagerStatNameTagOverlay() {
    }

    public static void onRenderNameTag(RenderNameTagEvent event) {
        if (!(event.getEntity() instanceof Villager villager)) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null
                || minecraft.options.hideGui
                || minecraft.player.distanceToSqr(villager) > MAX_DISTANCE * MAX_DISTANCE) {
            return;
        }

        boolean renderStats = shouldRender(villager);
        float alpha = VillagerInteractionVisibilityFade.alpha();
        if (!renderStats && alpha >= 0.999F) {
            return;
        }
        event.setCanRender(TriState.FALSE);
        if (alpha <= 0.001F) {
            return;
        }

        Vec3 attachment = villager.getAttachments().getNullable(
                EntityAttachment.NAME_TAG, 0, villager.getViewYRot(event.getPartialTick()));
        if (attachment == null) {
            return;
        }

        List<Stat> stats = renderStats ? List.of(
                new Stat(HEALTH_ICON, formatValue(villager.getHealth()), 0xFFFF1313, true),
                new Stat(ARMOR_ICON, Integer.toString(villager.getArmorValue()), 0xFFB8B9C4, true),
                new Stat(HUNGER_ICON, Integer.toString(VillagerHungerClientCache.hunger(villager)), 0xFFB88458, true)) : List.of();
        render(event, villager, event.getContent(), attachment,
                stats.stream().filter(Stat::visible).toList(), renderStats, alpha);
    }

    private static boolean shouldRender(Villager villager) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null
                || minecraft.options.hideGui
                || !VillagerRetaliationServerConfigClient.showVillagerNameTags()
                || !VillagerRetaliationClientPreferences.showVillagerNameTags()
                || minecraft.player.distanceToSqr(villager) > MAX_DISTANCE * MAX_DISTANCE) {
            return false;
        }

        VillagerStatDisplayMode mode = VillagerRetaliationServerConfigClient.villagerStatDisplayMode();
        return switch (mode) {
            case ALWAYS -> true;
            case HIRED_ONLY -> VillagerNameClientCache.isHired(villager.getId());
            case PARTY_ONLY -> PartyRosterClient.roster().active()
                    && PartyRosterClient.roster().villagers().stream()
                    .anyMatch(entry -> entry.villagerId().equals(villager.getUUID()));
            case NEVER -> false;
        };
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void render(
            RenderNameTagEvent event,
            Villager villager,
            Component name,
            Vec3 attachment,
            List<Stat> stats,
            boolean renderStats,
            float alpha) {
        EntityRenderer renderer = event.getEntityRenderer();
        Font font = renderer.getFont();
        int totalWidth = stats.stream().mapToInt(stat -> ICON_SIZE + ICON_TEXT_GAP + font.width(stat.value())).sum()
                + STAT_GAP * (stats.size() - 1);

        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource buffers = event.getMultiBufferSource();
        MultiBufferSource fadedBuffers = alpha >= 0.999F ? buffers : new AlphaBufferSource(buffers, alpha);
        poseStack.pushPose();
        poseStack.translate(attachment.x, attachment.y + (renderStats ? NAMEPLATE_RAISE : 0.0F), attachment.z);
        poseStack.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
        poseStack.scale(0.025F, -0.025F, 0.025F);

        Matrix4f pose = poseStack.last().pose();
        renderName(font, fadedBuffers, pose, name, renderStats ? -font.lineHeight - 4.0F : 0.0F,
                !villager.isDiscrete(), event.getPackedLight());
        float x = -totalWidth / 2.0F;
        for (Stat stat : stats) {
            renderIcon(fadedBuffers, pose, stat.icon(), x, ROW_Y, alpha);
            float textX = x + ICON_SIZE + ICON_TEXT_GAP;
            renderOutlinedText(font, fadedBuffers, pose, stat.value(), textX, ROW_Y + 2.0F,
                    stat.color(), event.getPackedLight());
            x += ICON_SIZE + ICON_TEXT_GAP + font.width(stat.value()) + STAT_GAP;
        }
        poseStack.popPose();
    }

    private static void renderName(
            Font font,
            MultiBufferSource buffers,
            Matrix4f pose,
            Component name,
            float y,
            boolean seeThrough,
            int packedLight) {
        float x = -font.width(name) / 2.0F;
        int backgroundAlpha = Math.round(Minecraft.getInstance().options.getBackgroundOpacity(0.25F) * 255.0F);
        int background = backgroundAlpha << 24;
        font.drawInBatch(name, x, y, 0x20FFFFFF, false, pose, buffers,
                seeThrough ? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.NORMAL, background, packedLight);
        if (seeThrough) {
            font.drawInBatch(name, x, y, 0xFFFFFFFF, false, pose, buffers,
                    Font.DisplayMode.NORMAL, 0, packedLight);
        }
    }

    private static void renderIcon(
            MultiBufferSource buffers,
            Matrix4f pose,
            ResourceLocation texture,
            float x,
            float y,
            float alpha) {
        RenderType renderType = alpha >= 0.999F
                ? RenderType.entityCutoutNoCull(texture)
                : RenderType.entityTranslucent(texture);
        VertexConsumer consumer = buffers.getBuffer(renderType);
        vertex(consumer, pose, x, y + ICON_SIZE, 0.0F, 1.0F);
        vertex(consumer, pose, x + ICON_SIZE, y + ICON_SIZE, 1.0F, 1.0F);
        vertex(consumer, pose, x + ICON_SIZE, y, 1.0F, 0.0F);
        vertex(consumer, pose, x, y, 0.0F, 0.0F);
    }

    private static void vertex(
            VertexConsumer consumer,
            Matrix4f pose,
            float x,
            float y,
            float u,
            float v) {
        consumer.addVertex(pose, x, y, 0.0F)
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(0.0F, 0.0F, 1.0F);
    }

    private static void renderOutlinedText(
            Font font,
            MultiBufferSource buffers,
            Matrix4f pose,
            String text,
            float x,
            float y,
            int color,
            int packedLight) {
        font.drawInBatch8xOutline(
                Component.literal(text).getVisualOrderText(),
                x,
                y,
                color,
                0xFF000000,
                pose,
                buffers,
                packedLight);
    }

    private record AlphaBufferSource(MultiBufferSource delegate, float alpha) implements MultiBufferSource {
        @Override
        public VertexConsumer getBuffer(RenderType renderType) {
            return new AlphaVertexConsumer(this.delegate.getBuffer(renderType), this.alpha);
        }
    }

    private record AlphaVertexConsumer(VertexConsumer delegate, float alpha) implements VertexConsumer {
        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            this.delegate.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            this.delegate.setColor(red, green, blue, Math.round(alpha * this.alpha));
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            this.delegate.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            this.delegate.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            this.delegate.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float normalX, float normalY, float normalZ) {
            this.delegate.setNormal(normalX, normalY, normalZ);
            return this;
        }
    }

    private static String formatValue(float value) {
        return Math.abs(value - Math.round(value)) < 0.01F
                ? Integer.toString(Math.round(value))
                : String.format(Locale.ROOT, "%.1f", value);
    }

    private record Stat(ResourceLocation icon, String value, int color, boolean visible) {
    }
}
