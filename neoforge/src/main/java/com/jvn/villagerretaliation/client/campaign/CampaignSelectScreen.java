package com.jvn.villagerretaliation.client.campaign;

import com.google.gson.JsonParseException;
import com.jvn.villagerretaliation.campaign.selection.WorldCampaign;
import com.jvn.villagerretaliation.campaign.selection.WorldCampaignCatalog;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.CloseableResourceManager;
import net.minecraft.server.packs.resources.ResourceManager;
import org.lwjgl.glfw.GLFW;

public final class CampaignSelectScreen extends Screen {
    private static final int CARD_WIDTH = 128;
    private static final int EXPANDED_WIDTH = 374;
    private static final int CARD_HEIGHT = 192;
    private static final int PAGE_SIZE = 3;
    private static final int AUTHOR_Y = CARD_HEIGHT - 7 - 8;
    private static final ResourceLocation CARD = texture("campaign_select_screen_card");
    private static final ResourceLocation EXPANDED = texture("campaign_select_screen_card_expanded");
    private static final ResourceLocation CARET = texture("campaign_select_screen_card_caret_expand");
    private static final ResourceLocation COLLAPSE = texture("campaign_select_screen_card_caret_expanded");
    private final Screen parent;
    private List<WorldCampaign> campaigns;
    private final Consumer<WorldCampaign> create;
    private final Supplier<CloseableResourceManager> reloadResources;
    private final Map<ResourceLocation, CampaignMedia> descriptionImages = new LinkedHashMap<>();
    private final Map<ResourceLocation, CampaignMedia> images = new LinkedHashMap<>();
    private final Map<ResourceLocation, List<CampaignDescription.Block>> descriptions = new LinkedHashMap<>();
    private Component reloadStatus;
    private long reloadStatusUntil;
    private WorldCampaign selected;
    private WorldCampaign expanded;
    private Button createButton;
    private int page;
    private float cardScale;
    private int contentLeft;
    private int contentTop;
    private int descriptionHeight;
    private double descriptionScroll;

    public CampaignSelectScreen(Screen parent, WorldCampaignCatalog catalog, ResourceManager resources,
                                Supplier<CloseableResourceManager> reloadResources, Consumer<WorldCampaign> create) {
        super(Component.translatable("screen.villagerretaliation.campaign.title"));
        this.parent = parent;
        this.campaigns = catalog.choices();
        this.create = create;
        this.reloadResources = reloadResources;
        for (WorldCampaign campaign : campaigns) {
            images.computeIfAbsent(campaign.image(), id -> CampaignMedia.load(resources, id));
            var blocks = CampaignDescription.parse(campaign.description());
            descriptions.put(campaign.id(), blocks);
            for (var block : blocks) {
                if (block.image() == null) continue;
                ResourceLocation id = CampaignDescription.imageLocation(campaign.id(), block.image());
                // the datapack resource manager closes after this screen is constructed
                if (id != null) descriptionImages.computeIfAbsent(id, key -> CampaignMedia.load(resources, key));
            }
        }
    }

    private static ResourceLocation texture(String name) {
        return ResourceLocation.fromNamespaceAndPath("villagerretaliation", "textures/gui/campaign_select_screen/" + name + ".png");
    }

    @Override
    protected void init() {
        int pages = Math.max(1, (campaigns.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        page = Math.min(page, pages - 1);
        int count = Math.min(PAGE_SIZE, campaigns.size() - page * PAGE_SIZE);
        int groupWidth = expanded != null ? EXPANDED_WIDTH : Math.max(CARD_WIDTH, count * CARD_WIDTH + Math.max(0, count - 1) * 16);
        cardScale = Math.max(0.1F, Math.min(1, Math.min((width - 8F) / groupWidth, (height - 75F) / CARD_HEIGHT)));
        contentLeft = Math.round((width - groupWidth * cardScale) / 2);
        contentTop = Math.round(26 + (height - 75 - CARD_HEIGHT * cardScale) / 2);
        if (expanded != null) addCard(expanded, contentLeft, true);
        else for (int index = 0; index < count; index++) {
            addCard(campaigns.get(page * PAGE_SIZE + index), contentLeft + Math.round(index * (CARD_WIDTH + 16) * cardScale), false);
        }
        int y = height - 28;
        int buttonWidth = expanded == null && pages > 1 ? Math.min(150, (width - 104) / 2) : 150;
        addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, button -> onClose()).bounds(width / 2 - buttonWidth - 4, y, buttonWidth, 20).build());
        createButton = addRenderableWidget(Button.builder(Component.translatable("selectWorld.create"), button -> create.accept(selected))
                .bounds(width / 2 + 4, y, buttonWidth, 20).build());
        createButton.active = selected != null;
        if (expanded == null && pages > 1) {
            Button previous = addRenderableWidget(Button.builder(Component.literal("<"), button -> { page--; rebuildWidgets(); })
                    .bounds(16, y, 20, 20).build());
            previous.setTooltip(Tooltip.create(Component.translatable("screen.villagerretaliation.campaign.previous")));
            previous.active = page > 0;
            Button next = addRenderableWidget(Button.builder(Component.literal(">"), button -> { page++; rebuildWidgets(); })
                    .bounds(width - 36, y, 20, 20).build());
            next.setTooltip(Tooltip.create(Component.translatable("screen.villagerretaliation.campaign.next")));
            next.active = page + 1 < pages;
        }
    }

    private void addCard(WorldCampaign campaign, int x, boolean isExpanded) {
        addRenderableWidget(new Card(x, campaign, isExpanded));
        addRenderableWidget(new Caret(x, campaign, isExpanded));
    }

    private void select(WorldCampaign campaign) {
        selected = campaign.locked() ? null : campaign;
        createButton.active = selected != null;
    }

    private void toggle(WorldCampaign campaign) {
        if (expanded == null) {
            expanded = campaign;
            select(campaign);
        } else expanded = null;
        descriptionScroll = 0;
        rebuildWidgets();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        for (var child : children()) {
            if (child instanceof Button button && (button instanceof Card || button instanceof Caret)
                    && button.isHovered() && button.getTooltip() != null) {
                setTooltipForNextRenderPass(button.getTooltip(), DefaultTooltipPositioner.INSTANCE, true);
            }
        }
        graphics.drawCenteredString(font, title, width / 2, 12, 0xffffff);
        Component notice = reloadStatus != null && Util.getMillis() < reloadStatusUntil ? reloadStatus
                : Component.translatable("screen.villagerretaliation.campaign.permanent");
        graphics.drawCenteredString(font, notice, width / 2, height - 43, 0xffffff);
        if (campaigns.isEmpty()) graphics.drawCenteredString(font, Component.translatable("screen.villagerretaliation.campaign.empty"), width / 2, 58, 0xffffff);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (expanded != null && mouseX >= contentLeft + 132 * cardScale && mouseX < contentLeft + 365 * cardScale
                && mouseY >= contentTop + 9 * cardScale && mouseY < contentTop + 183 * cardScale) {
            descriptionScroll = Math.clamp(descriptionScroll - scrollY * 18, 0, Math.max(0, descriptionHeight - 174));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == GLFW.GLFW_KEY_F5) {
            reloadCampaigns();
            return true;
        }
        if (expanded != null) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                toggle(expanded);
                return true;
            }
            int scroll = switch (key) {
                case GLFW.GLFW_KEY_DOWN -> 18;
                case GLFW.GLFW_KEY_UP -> -18;
                case GLFW.GLFW_KEY_PAGE_DOWN -> 160;
                case GLFW.GLFW_KEY_PAGE_UP -> -160;
                default -> 0;
            };
            if (scroll != 0) {
                descriptionScroll = Math.clamp(descriptionScroll + scroll, 0, Math.max(0, descriptionHeight - 174));
                return true;
            }
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    private void reloadCampaigns() {
        WorldCampaignCatalog catalog;
        List<WorldCampaign> nextCampaigns;
        Map<ResourceLocation, List<CampaignDescription.Block>> nextDescriptions = new LinkedHashMap<>();
        Map<ResourceLocation, CampaignMedia> nextCovers = new LinkedHashMap<>();
        Map<ResourceLocation, CampaignMedia> nextImages = new LinkedHashMap<>();
        try (var resources = reloadResources.get()) {
            catalog = WorldCampaignCatalog.load(resources);
            nextCampaigns = catalog.choices();
            for (WorldCampaign campaign : nextCampaigns) {
                nextCovers.computeIfAbsent(campaign.image(), id -> CampaignMedia.load(resources, id));
                var blocks = CampaignDescription.parse(campaign.description());
                nextDescriptions.put(campaign.id(), blocks);
                for (var block : blocks) {
                    if (block.image() == null) continue;
                    ResourceLocation id = CampaignDescription.imageLocation(campaign.id(), block.image());
                    if (id != null) nextImages.computeIfAbsent(id, key -> CampaignMedia.load(resources, key));
                }
            }
        } catch (JsonParseException | IllegalArgumentException | IllegalStateException exception) {
            nextCovers.values().forEach(CampaignMedia::close);
            nextImages.values().forEach(CampaignMedia::close);
            LogUtils.getLogger().warn("Could not reload campaigns", exception);
            reloadStatus = Component.translatable("screen.villagerretaliation.campaign.reload_failed");
            reloadStatusUntil = Util.getMillis() + 5_000;
            minecraft.getNarrator().sayNow(reloadStatus);
            return;
        }
        images.values().forEach(CampaignMedia::close);
        images.clear();
        images.putAll(nextCovers);
        descriptionImages.values().forEach(CampaignMedia::close);
        descriptionImages.clear();
        descriptionImages.putAll(nextImages);
        descriptions.clear();
        descriptions.putAll(nextDescriptions);
        campaigns = nextCampaigns;
        selected = selected == null ? null : catalog.campaigns().get(selected.id());
        if (selected != null && selected.locked()) selected = null;
        expanded = expanded == null ? null : catalog.campaigns().get(expanded.id());
        if (expanded != null) page = campaigns.indexOf(expanded) / PAGE_SIZE;
        descriptionScroll = 0;
        descriptionHeight = 0;
        rebuildWidgets();
        reloadStatus = Component.translatable("screen.villagerretaliation.campaign.reloaded");
        reloadStatusUntil = Util.getMillis() + 2_500;
        minecraft.getNarrator().sayNow(reloadStatus);
    }

    @Override
    public void onClose() {
        if (expanded != null) toggle(expanded);
        else minecraft.setScreen(parent);
    }

    @Override
    public void removed() {
        images.values().forEach(CampaignMedia::close);
        descriptionImages.values().forEach(CampaignMedia::close);
    }

    private String fitAuthors(List<String> authors) {
        String full = String.join(", ", authors);
        if (authors.size() <= 1 || font.width(full) <= 114) return font.plainSubstrByWidth(full, 114);
        String fitting = "";
        for (String author : authors) {
            String next = fitting.isEmpty() ? author : fitting + ", " + author;
            if (font.width(next + "...") > 114) break;
            fitting = next;
        }
        return fitting + "...";
    }

    private final class Card extends Button {
        private final WorldCampaign campaign;
        private final boolean isExpanded;
        private final String authorText;
        private final Tooltip authorTooltip;
        private final Tooltip cardTooltip;

        private Card(int x, WorldCampaign campaign, boolean isExpanded) {
            super(x, contentTop, Math.round((isExpanded ? EXPANDED_WIDTH : CARD_WIDTH) * cardScale), Math.round(CARD_HEIGHT * cardScale),
                    Component.literal(campaign.name()), button -> select(campaign), DEFAULT_NARRATION);
            this.campaign = campaign;
            this.isExpanded = isExpanded;
            active = !campaign.locked();
            Component tooltip = Component.literal(campaign.name());
            if (campaign.locked()) tooltip = tooltip.copy().append("\n").append(Component.translatable("screen.villagerretaliation.campaign.coming_soon"));
            authorText = fitAuthors(campaign.authors());
            authorTooltip = Tooltip.create(Component.literal(String.join(", ", campaign.authors())));
            cardTooltip = Tooltip.create(tooltip);
            setTooltip(cardTooltip);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (mouseX >= getX() + 111 * cardScale && mouseX < getX() + 128 * cardScale
                    && mouseY >= getY() + 82 * cardScale && mouseY < getY() + 110 * cardScale) return false;
            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            graphics.pose().pushPose();
            graphics.pose().translate(getX(), getY(), 0);
            graphics.pose().scale(cardScale, cardScale, 1);
            CampaignMedia image = images.get(campaign.image());
            if (image != null && image.available()) {
                if (WorldCampaign.CARD_FALLBACK.equals(campaign.image())) image.render(graphics, 0, 0, CARD_WIDTH, CARD_HEIGHT, campaign.locked());
                else image.render(graphics, 4, 4, 120, 184, campaign.locked());
            } else {
                float brightness = campaign.locked() ? 0.8F : 1;
                graphics.setColor(brightness, brightness, brightness, 1);
                blit(graphics, WorldCampaign.CARD_FALLBACK, 0, 0, CARD_WIDTH, CARD_HEIGHT);
                graphics.setColor(1, 1, 1, 1);
            }
            blit(graphics, isExpanded ? EXPANDED : CARD, 0, 0, isExpanded ? EXPANDED_WIDTH : CARD_WIDTH, CARD_HEIGHT);
            if (selected != null && selected.id().equals(campaign.id())) graphics.renderOutline(2, 2, 124, 188, 0xfff2cd83);
            else if (isHoveredOrFocused() && active) graphics.renderOutline(2, 2, 124, 188, 0xffeeeeee);
            boolean overAuthors = !authorText.isEmpty() && mouseX >= getX() + 7 * cardScale
                    && mouseX < getX() + (7 + font.width(authorText)) * cardScale
                    && mouseY >= getY() + AUTHOR_Y * cardScale && mouseY < getY() + (AUTHOR_Y + font.lineHeight) * cardScale;
            setTooltip(overAuthors ? authorTooltip : cardTooltip);
            graphics.drawString(font, authorText, 7, AUTHOR_Y, 0xffffff, true);
            var titleLines = font.split(Component.literal(campaign.name()), 57);
            titleLines = titleLines.subList(0, Math.min(3, titleLines.size()));
            int titleHeight = (titleLines.size() - 1) * font.lineHeight * 2 + 16;
            graphics.pose().pushPose();
            graphics.pose().translate(7, AUTHOR_Y - 3 - titleHeight, 0);
            graphics.pose().scale(2, 2, 1);
            for (int index = 0; index < titleLines.size(); index++) graphics.drawString(font, titleLines.get(index), 0, index * font.lineHeight, 0xffffff, true);
            graphics.pose().popPose();
            if (campaign.locked()) {
                var lines = font.split(Component.translatable("screen.villagerretaliation.campaign.coming_soon_label"), 114);
                int top = (CARD_HEIGHT - lines.size() * font.lineHeight) / 2;
                for (int index = 0; index < lines.size(); index++) {
                    var line = lines.get(index);
                    graphics.drawString(font, line, (CARD_WIDTH - font.width(line)) / 2, top + index * font.lineHeight, 0xffffff, true);
                }
            }
            if (isExpanded) renderDescription(graphics, campaign);
            graphics.pose().popPose();
        }
    }

    private final class Caret extends Button {
        private final boolean isExpanded;

        private Caret(int cardX, WorldCampaign campaign, boolean isExpanded) {
            super(cardX + Math.round(111 * cardScale), contentTop + Math.round(82 * cardScale), Math.max(1, Math.round(17 * cardScale)),
                    Math.max(1, Math.round(28 * cardScale)), Component.translatable("screen.villagerretaliation.campaign." + (isExpanded ? "collapse" : "expand"), campaign.name()),
                    button -> toggle(campaign), DEFAULT_NARRATION);
            this.isExpanded = isExpanded;
            setTooltip(Tooltip.create(getMessage()));
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            graphics.pose().pushPose();
            graphics.pose().translate(getX(), getY(), 0);
            graphics.pose().scale(cardScale, cardScale, 1);
            if (isHoveredOrFocused()) graphics.setColor(1, 0.8F, 0.4F, 1);
            blit(graphics, isExpanded ? COLLAPSE : CARET, 5, 9, 7, 10);
            graphics.setColor(1, 1, 1, 1);
            graphics.pose().popPose();
        }
    }

    private void renderDescription(GuiGraphics graphics, WorldCampaign campaign) {
        int rightPadding = 9;
        int frameRight = EXPANDED_WIDTH - 3;
        int scrollbarRight = EXPANDED_WIDTH - rightPadding;
        int scrollbarLeft = scrollbarRight - 2;
        int scrollbarGap = frameRight - scrollbarRight;
        int contentRight = scrollbarLeft - scrollbarGap;
        int areaWidth = contentRight - 132;
        graphics.enableScissor(contentLeft + Math.round(132 * cardScale), contentTop + Math.round(9 * cardScale),
                contentLeft + Math.round(contentRight * cardScale), contentTop + Math.round(183 * cardScale));
        int y = 0;
        for (var block : descriptions.get(campaign.id())) {
            if (block.image() != null) {
                ResourceLocation id = CampaignDescription.imageLocation(campaign.id(), block.image());
                CampaignMedia image = descriptionImages.get(id);
                if (image != null && image.available()) {
                    int imageHeight = image.height(areaWidth);
                    image.render(graphics, 132, 9 + y - (int) descriptionScroll, areaWidth, imageHeight);
                    y += imageHeight + 4;
                    continue;
                }
            }
            var lines = font.split(block.text(), (int) (areaWidth / block.scale()));
            graphics.pose().pushPose();
            graphics.pose().translate(132, 9 + y - (int) descriptionScroll, 0);
            graphics.pose().scale(block.scale(), block.scale(), 1);
            for (int index = 0; index < lines.size(); index++) graphics.drawString(font, lines.get(index), 0, index * font.lineHeight, 0xffffff, false);
            graphics.pose().popPose();
            y += Math.round(Math.max(1, lines.size()) * font.lineHeight * block.scale()) + 4;
        }
        graphics.disableScissor();
        descriptionHeight = y;
        if (descriptionHeight > 174) {
            int thumb = Math.max(8, 174 * 174 / descriptionHeight);
            int top = 9 + (int) (descriptionScroll * (174 - thumb) / (descriptionHeight - 174));
            graphics.fill(scrollbarLeft, top, scrollbarRight, top + thumb, 0xfff2cd83);
        }
    }

    private static void blit(GuiGraphics graphics, ResourceLocation texture, int x, int y, int width, int height) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.blit(texture, x, y, 0, 0, width, height, width, height);
        RenderSystem.disableBlend();
    }
}
