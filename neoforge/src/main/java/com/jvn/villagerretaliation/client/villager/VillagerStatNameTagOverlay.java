package com.jvn.villagerretaliation.client.villager;

import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.client.config.VillagerRetaliationClientPreferences;
import com.jvn.villagerretaliation.client.config.VillagerRetaliationServerConfigClient;
import com.jvn.villagerretaliation.client.interaction.VillagerInteractionVisibilityFade;
import com.jvn.villagerretaliation.client.party.PartyRosterClient;
import com.jvn.villagerretaliation.config.VillagerStatDisplayMode;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
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
    private static final Map<Villager, CachedNameplate> NAMEPLATES = new WeakHashMap<>();

    private VillagerStatNameTagOverlay() {
    }

    public static void registerReloadListener(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) resourceManager -> NAMEPLATES.clear());
    }

    public static void onRenderNameTag(RenderNameTagEvent event) {
        if (VillagerModelPreviewRenderContext.isRendering(event.getEntity())) {
            event.setCanRender(TriState.FALSE);
            return;
        }
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

        EntityRenderer renderer = event.getEntityRenderer();
        Font font = renderer.getFont();
        CachedNameplate nameplate = NAMEPLATES.get(villager);
        if (nameplate == null) {
            nameplate = new CachedNameplate();
            NAMEPLATES.put(villager, nameplate);
        }
        nameplate.update(
                font,
                event.getContent(),
                villager.getHealth(),
                villager.getArmorValue(),
                VillagerHungerClientCache.hunger(villager));
        render(event, villager, attachment, nameplate, renderStats, alpha);
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
            case PARTY_ONLY -> isPartyMember(villager);
            case NEVER -> false;
        };
    }

    private static boolean isPartyMember(Villager villager) {
        var roster = PartyRosterClient.roster();
        if (!roster.active()) {
            return false;
        }
        var entries = roster.villagers();
        for (int i = 0, size = entries.size(); i < size; i++) {
            if (entries.get(i).villagerId().equals(villager.getUUID())) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void render(
            RenderNameTagEvent event,
            Villager villager,
            Vec3 attachment,
            CachedNameplate nameplate,
            boolean renderStats,
            float alpha) {
        Font font = nameplate.font;
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource buffers = event.getMultiBufferSource();
        MultiBufferSource fadedBuffers = alpha >= 0.999F ? buffers : nameplate.alphaBuffers.configure(buffers, alpha);
        poseStack.pushPose();
        poseStack.translate(attachment.x, attachment.y + (renderStats ? NAMEPLATE_RAISE : 0.0F), attachment.z);
        poseStack.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
        poseStack.scale(0.025F, -0.025F, 0.025F);

        Matrix4f pose = poseStack.last().pose();
        renderName(font, fadedBuffers, pose, nameplate.name, nameplate.nameWidth,
                renderStats ? -font.lineHeight - 4.0F : 0.0F,
                !villager.isDiscrete(), event.getPackedLight());
        if (renderStats) {
            float x = -nameplate.totalStatWidth / 2.0F;
            renderIcon(fadedBuffers, pose, HEALTH_ICON, x, ROW_Y, alpha);
            renderOutlinedText(font, fadedBuffers, pose, nameplate.health, x + ICON_SIZE + ICON_TEXT_GAP,
                    ROW_Y + 2.0F, 0xFFFF1313, event.getPackedLight());
            x += ICON_SIZE + ICON_TEXT_GAP + nameplate.healthWidth + STAT_GAP;

            renderIcon(fadedBuffers, pose, ARMOR_ICON, x, ROW_Y, alpha);
            renderOutlinedText(font, fadedBuffers, pose, nameplate.armor, x + ICON_SIZE + ICON_TEXT_GAP,
                    ROW_Y + 2.0F, 0xFFB8B9C4, event.getPackedLight());
            x += ICON_SIZE + ICON_TEXT_GAP + nameplate.armorWidth + STAT_GAP;

            renderIcon(fadedBuffers, pose, HUNGER_ICON, x, ROW_Y, alpha);
            renderOutlinedText(font, fadedBuffers, pose, nameplate.hunger, x + ICON_SIZE + ICON_TEXT_GAP,
                    ROW_Y + 2.0F, 0xFFB88458, event.getPackedLight());
        }
        poseStack.popPose();
    }

    private static void renderName(
            Font font,
            MultiBufferSource buffers,
            Matrix4f pose,
            FormattedCharSequence name,
            int nameWidth,
            float y,
            boolean seeThrough,
            int packedLight) {
        float x = -nameWidth / 2.0F;
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
            FormattedCharSequence text,
            float x,
            float y,
            int color,
            int packedLight) {
        font.drawInBatch8xOutline(
                text,
                x,
                y,
                color,
                0xFF000000,
                pose,
                buffers,
                packedLight);
    }

    private static final class AlphaBufferSource implements MultiBufferSource {
        private final AlphaVertexConsumer consumer = new AlphaVertexConsumer();
        private MultiBufferSource delegate;
        private float alpha;

        private AlphaBufferSource configure(MultiBufferSource delegate, float alpha) {
            this.delegate = delegate;
            this.alpha = alpha;
            return this;
        }

        @Override
        public VertexConsumer getBuffer(RenderType renderType) {
            return this.consumer.configure(this.delegate.getBuffer(renderType), this.alpha);
        }
    }

    private static final class AlphaVertexConsumer implements VertexConsumer {
        private VertexConsumer delegate;
        private float alpha;

        private AlphaVertexConsumer configure(VertexConsumer delegate, float alpha) {
            this.delegate = delegate;
            this.alpha = alpha;
            return this;
        }

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

    private static final class CachedNameplate {
        private final AlphaBufferSource alphaBuffers = new AlphaBufferSource();
        private Font font;
        private Language language;
        private Component nameSnapshot;
        private int healthBits;
        private int armorValue;
        private int hungerValue;
        private FormattedCharSequence name;
        private FormattedCharSequence health;
        private FormattedCharSequence armor;
        private FormattedCharSequence hunger;
        private int nameWidth;
        private int healthWidth;
        private int armorWidth;
        private int totalStatWidth;

        private void update(Font font, Component name, float health, int armor, int hunger) {
            int currentHealthBits = Float.floatToIntBits(health);
            Language currentLanguage = Language.getInstance();
            if (this.font == font
                    && this.language == currentLanguage
                    && this.healthBits == currentHealthBits
                    && this.armorValue == armor
                    && this.hungerValue == hunger
                    && name.equals(this.nameSnapshot)) {
                return;
            }

            this.font = font;
            this.language = currentLanguage;
            this.nameSnapshot = name.copy();
            this.healthBits = currentHealthBits;
            this.armorValue = armor;
            this.hungerValue = hunger;
            this.name = this.nameSnapshot.getVisualOrderText();
            this.health = Component.literal(formatValue(health)).getVisualOrderText();
            this.armor = Component.literal(Integer.toString(armor)).getVisualOrderText();
            this.hunger = Component.literal(Integer.toString(hunger)).getVisualOrderText();
            this.nameWidth = font.width(this.name);
            this.healthWidth = font.width(this.health);
            this.armorWidth = font.width(this.armor);
            int hungerWidth = font.width(this.hunger);
            this.totalStatWidth = ICON_SIZE * 3
                    + ICON_TEXT_GAP * 3
                    + STAT_GAP * 2
                    + this.healthWidth
                    + this.armorWidth
                    + hungerWidth;
        }
    }
}
