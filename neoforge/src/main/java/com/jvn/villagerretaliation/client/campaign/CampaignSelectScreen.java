package com.jvn.villagerretaliation.client.campaign;

import com.jvn.villagerretaliation.campaign.selection.WorldCampaign;
import com.jvn.villagerretaliation.campaign.selection.WorldCampaignCatalog;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

public final class CampaignSelectScreen extends Screen {
    private final Screen parent;
    private final List<WorldCampaign> campaigns;
    private final Consumer<WorldCampaign> create;
    private final Map<ResourceLocation, Image> images = new LinkedHashMap<>();
    private WorldCampaign selected;
    private Button createButton;
    private int page;

    public CampaignSelectScreen(Screen parent, WorldCampaignCatalog catalog, ResourceManager resources, Consumer<WorldCampaign> create) {
        super(Component.translatable("screen.villagerretaliation.campaign.title"));
        this.parent = parent;
        this.campaigns = catalog.choices();
        this.create = create;
        for (WorldCampaign campaign : this.campaigns) images.computeIfAbsent(campaign.image(), id -> loadImage(resources, id));
    }

    private static Image loadImage(ResourceManager resources, ResourceLocation id) {
        try (var stream = WorldCampaign.MOD_ICON.equals(id)
                ? CampaignSelectScreen.class.getResourceAsStream("/villager_retaliation_cover.png") : resources.getResourceOrThrow(id).open()) {
            if (stream == null) throw new IOException("Mod icon is missing");
            byte[] bytes = stream.readNBytes(4 * 1024 * 1024 + 1);
            if (bytes.length > 4 * 1024 * 1024) throw new IOException("Campaign image exceeds 4 MiB");
            return new Image(bytes);
        } catch (IOException exception) {
            LogUtils.getLogger().warn("Could not load campaign image {}", id, exception);
            if (!WorldCampaign.MOD_ICON.equals(id)) return loadImage(resources, WorldCampaign.MOD_ICON);
            return new Image(new byte[0]);
        }
    }

    @Override
    protected void init() {
        int cardWidth = Math.min(460, this.width - 32);
        int rows = Math.max(1, (this.height - 90) / 86);
        int pages = Math.max(1, (campaigns.size() + rows - 1) / rows);
        this.page = Math.min(this.page, pages - 1);
        for (int index = page * rows; index < Math.min(campaigns.size(), (page + 1) * rows); index++) {
            WorldCampaign campaign = campaigns.get(index);
            addRenderableWidget(new Card((width - cardWidth) / 2, 42 + (index % rows) * 86, cardWidth, campaign));
        }
        int y = height - 28;
        addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, button -> onClose()).bounds(width / 2 - 154, y, 150, 20).build());
        this.createButton = addRenderableWidget(Button.builder(Component.translatable("selectWorld.create"), button -> create.accept(selected))
                .bounds(width / 2 + 4, y, 150, 20).build());
        this.createButton.active = selected != null;
        if (pages > 1) {
            Button previous = addRenderableWidget(Button.builder(Component.literal("<"), button -> { page--; rebuildWidgets(); })
                    .bounds(16, height - 55, 20, 20).build());
            previous.active = page > 0;
            Button next = addRenderableWidget(Button.builder(Component.literal(">"), button -> { page++; rebuildWidgets(); })
                    .bounds(width - 36, height - 55, 20, 20).build());
            next.active = page + 1 < pages;
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 12, 0xffffff);
        graphics.drawCenteredString(font, Component.translatable("screen.villagerretaliation.campaign.permanent"), width / 2, height - 43, 0xffffff);
        if (campaigns.isEmpty()) graphics.drawCenteredString(font, Component.translatable("screen.villagerretaliation.campaign.empty"), width / 2, 58, 0xffffff);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public void removed() {
        for (Image image : images.values()) image.release();
    }

    private final class Card extends Button {
        private final WorldCampaign campaign;

        private Card(int x, int y, int width, WorldCampaign campaign) {
            super(x, y, width, 78, Component.literal(campaign.name()), button -> {
                selected = campaign;
                createButton.active = true;
            }, DEFAULT_NARRATION);
            this.campaign = campaign;
            this.active = !campaign.locked();
            setTooltip(Tooltip.create(Component.literal(campaign.description())));
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            boolean chosen = selected != null && selected.id().equals(campaign.id());
            int border = chosen ? 0xffb8d59b : isHoveredOrFocused() && active ? 0xffffffff : 0xff777777;
            graphics.fill(getX(), getY(), getX() + width, getY() + height, border);
            graphics.fill(getX() + 1, getY() + 1, getX() + width - 1, getY() + height - 1, active ? 0xff222222 : 0xff333333);
            Image image = images.get(campaign.image());
            image.render(graphics, getX() + 8, getY() + 7, campaign.locked());
            int x = getX() + 80;
            int textWidth = width - 88;
            int color = active ? 0xffffff : 0x999999;
            graphics.drawString(font, font.plainSubstrByWidth(campaign.name(), textWidth), x, getY() + 8, color);
            int y = getY() + 23;
            if (!campaign.authors().isEmpty()) {
                graphics.drawString(font, font.plainSubstrByWidth(String.join(", ", campaign.authors()), textWidth), x, y, 0xb0b0b0);
                y += 12;
            }
            var lines = font.split(Component.literal(campaign.description()), textWidth);
            int count = Math.min(campaign.locked() ? 1 : 2, lines.size());
            for (int i = 0; i < count; i++) graphics.drawString(font, lines.get(i), x, y + i * 10, color);
            if (campaign.locked()) graphics.drawString(font, Component.translatable("screen.villagerretaliation.campaign.coming_soon"), x, getY() + 60, 0xb0b0b0);
        }
    }

    private static final class Image {
        private final byte[] bytes;
        private ResourceLocation texture;
        private boolean failed;
        private int width;
        private int height;

        private Image(byte[] bytes) { this.bytes = bytes; }

        private void render(GuiGraphics graphics, int x, int y, boolean locked) {
            if (failed || bytes.length == 0) return;
            if (texture == null) {
                try {
                    NativeImage pixels = NativeImage.read(new java.io.ByteArrayInputStream(bytes));
                    width = pixels.getWidth();
                    height = pixels.getHeight();
                    texture = Minecraft.getInstance().getTextureManager().register("world_campaign", new DynamicTexture(pixels));
                } catch (IOException exception) {
                    failed = true;
                    LogUtils.getLogger().warn("Invalid campaign image", exception);
                    return;
                }
            }
            float scale = Math.min(64.0F / width, 64.0F / height);
            int w = Math.max(1, (int) (width * scale));
            int h = Math.max(1, (int) (height * scale));
            graphics.setColor(locked ? 0.4F : 1, locked ? 0.4F : 1, locked ? 0.4F : 1, 1);
            graphics.blit(texture, x + (64 - w) / 2, y + (64 - h) / 2, w, h, 0, 0, width, height, width, height);
            graphics.setColor(1, 1, 1, 1);
        }

        private void release() {
            if (texture != null) Minecraft.getInstance().getTextureManager().release(texture);
            texture = null;
        }
    }
}
