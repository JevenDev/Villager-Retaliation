package com.jvn.villagerretaliation.client.campaign;

import com.google.gson.JsonParseException;
import com.jvn.villagerretaliation.campaign.selection.WorldCampaign;
import com.jvn.villagerretaliation.campaign.selection.WorldCampaignCatalog;
import com.jvn.villagerretaliation.mixin.client.PanoramaRendererAccessor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.client.renderer.PanoramaRenderer;
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
    private final BiConsumer<WorldCampaign, List<ResourceLocation>> create;
    private WorldCampaignCatalog catalog;
    private final Map<ResourceLocation, List<ResourceLocation>> addons = new LinkedHashMap<>();
    private final Map<ResourceLocation, CampaignMedia> addonIcons = new LinkedHashMap<>();
    private Button addonsButton;
    private boolean configuringAddons;
    private final Supplier<CloseableResourceManager> reloadResources;
    private final Map<ResourceLocation, CampaignMedia> descriptionImages = new LinkedHashMap<>();
    private final Map<ResourceLocation, CampaignMedia> images = new LinkedHashMap<>();
    private final Map<ResourceLocation, List<CampaignDescription.Block>> descriptions = new LinkedHashMap<>();
    private Component reloadStatus;
    private long reloadStatusUntil;
    private final CampaignMotion backgroundMotion = new CampaignMotion();
    private boolean backgroundStarted;
    private final Map<ResourceLocation, CampaignMotion.Card> cardMotion = new LinkedHashMap<>();
    private final Map<ResourceLocation, Card> cards = new LinkedHashMap<>();
    private final List<Card> departingCards = new ArrayList<>();
    private int pageDirection;
    private boolean fadeDepartures = true;
    private WorldCampaign selected;
    private WorldCampaign expanded;
    private Button createButton;
    private int page;
    private float cardScale;
    private int contentLeft;
    private int contentTop;
    private int descriptionHeight;
    private double descriptionScroll;
    private double visibleDescriptionScroll;

    public CampaignSelectScreen(Screen parent, WorldCampaignCatalog catalog, ResourceManager resources,
                                Supplier<CloseableResourceManager> reloadResources, Consumer<WorldCampaign> create) {
        this(parent, catalog, resources, reloadResources, (campaign, addons) -> create.accept(campaign));
    }

    public CampaignSelectScreen(Screen parent, WorldCampaignCatalog catalog, ResourceManager resources,
                                Supplier<CloseableResourceManager> reloadResources,
                                BiConsumer<WorldCampaign, List<ResourceLocation>> create) {
        super(Component.translatable("screen.villagerretaliation.campaign.title"));
        this.parent = parent;
        this.catalog = catalog;
        this.campaigns = catalog.choices();
        this.create = create;
        this.reloadResources = reloadResources;
        catalog.addonChoices().forEach(addon -> addonIcons.put(addon.id(), CampaignMedia.addonIcon(resources, addon)));
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

    void prepareFirstPage() {
        for (var campaign : campaigns.subList(0, Math.min(PAGE_SIZE, campaigns.size()))) {
            images.get(campaign.image()).prepare(campaign.locked());
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
        long now = Util.getMillis();
        List<WorldCampaign> visible = expanded != null ? List.of(expanded) : campaigns.subList(page * PAGE_SIZE, page * PAGE_SIZE + count);
        var visibleIds = visible.stream().map(WorldCampaign::id).toList();
        if (pageDirection != 0 || !fadeDepartures) departingCards.clear();
        for (var card : cards.values()) {
            if (visibleIds.contains(card.campaign.id())) continue;
            if (pageDirection == 0 && fadeDepartures) {
                card.setFocused(false);
                card.motion.target(card.motion.x(), card.motion.y() + 4 * card.motion.scale(), card.motion.scale(), false, 0, now, 0);
                departingCards.add(card);
            } else cardMotion.remove(card.campaign.id());
        }
        departingCards.removeIf(card -> visibleIds.contains(card.campaign.id()));
        cards.clear();
        for (int index = 0; index < visible.size(); index++) {
            int x = contentLeft + (expanded != null ? 0 : Math.round(index * (CARD_WIDTH + 16) * cardScale));
            addCard(visible.get(index), x, expanded != null, now, index * 25);
        }
        cardMotion.keySet().removeIf(id -> !cards.containsKey(id) && departingCards.stream().noneMatch(card -> card.campaign.id().equals(id)));
        pageDirection = 0;
        fadeDepartures = true;
        int y = height - 28;
        int buttonWidth = expanded == null && pages > 1 ? Math.min(150, (width - 104) / 2) : 150;
        addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, button -> onClose()).bounds(width / 2 - buttonWidth - 4, y, buttonWidth, 20).build());
        createButton = addRenderableWidget(Button.builder(Component.translatable("selectWorld.create"), button -> createSelection())
                .bounds(width / 2 + 4, y, buttonWidth, 20).build());
        createButton.active = selected != null && addonProblem() == null;
        addonsButton = addRenderableWidget(Button.builder(Component.translatable("screen.villagerretaliation.addons.button", selectedAddons().size()),
                button -> openAddons()).bounds(width - 116, 6, 110, 20).build());
        addonsButton.active = selected != null;
        updateSelectionButtons();
        if (expanded == null && pages > 1) {
            Button previous = addRenderableWidget(Button.builder(Component.literal("<"), button -> changePage(-1))
                    .bounds(16, y, 20, 20).build());
            previous.setTooltip(Tooltip.create(Component.translatable("screen.villagerretaliation.campaign.previous")));
            previous.active = page > 0;
            Button next = addRenderableWidget(Button.builder(Component.literal(">"), button -> changePage(1))
                    .bounds(width - 36, y, 20, 20).build());
            next.setTooltip(Tooltip.create(Component.translatable("screen.villagerretaliation.campaign.next")));
            next.active = page + 1 < pages;
        }
    }

    private void addCard(WorldCampaign campaign, int x, boolean isExpanded, long now, int delay) {
        boolean entering = !cardMotion.containsKey(campaign.id());
        var motion = cardMotion.computeIfAbsent(campaign.id(), id -> new CampaignMotion.Card(x + pageDirection * 8, contentTop + 6 * cardScale, cardScale, isExpanded, now));
        motion.target(x, contentTop, cardScale, isExpanded, 1, now, entering ? delay : 0);
        Card card = addRenderableWidget(new Card(x, campaign, isExpanded, motion));
        cards.put(campaign.id(), card);
        addRenderableWidget(new Caret(x, campaign, isExpanded, motion));
    }

    private void changePage(int direction) {
        page += direction;
        pageDirection = direction;
        rebuildWidgets();
    }

    private void select(WorldCampaign campaign) {
        selected = campaign.locked() ? null : campaign;
        updateSelectionButtons();
    }

    private List<ResourceLocation> selectedAddons() {
        return selected == null ? List.of() : addons.getOrDefault(selected.id(), List.of());
    }

    private String addonProblem() {
        for (var addon : selectedAddons()) {
            String problem = catalog.addonProblem(addon, selected.id(), selectedAddons());
            if (problem != null) return problem;
        }
        return null;
    }

    private void updateSelectionButtons() {
        String problem = addonProblem();
        createButton.active = selected != null && problem == null;
        createButton.setTooltip(problem == null ? null : Tooltip.create(Component.literal(problem)));
        addonsButton.active = selected != null;
        addonsButton.setMessage(Component.translatable("screen.villagerretaliation.addons.button", selectedAddons().size()));
    }

    private void createSelection() {
        if (selected == null) return;
        catalog.validate(new com.jvn.villagerretaliation.campaign.selection.WorldCampaignSelection.Selection(
                selected.id(), selected.includeDefaultContent(), selectedAddons()), true);
        create.accept(selected, List.copyOf(selectedAddons()));
    }

    private void openAddons() {
        configuringAddons = true;
        minecraft.setScreen(new CampaignAddonScreen(catalog, selected, selectedAddons(), addonIcons, null, enabled -> {
            addons.put(selected.id(), enabled);
            returnFromAddons();
        }, this::returnFromAddons));
    }

    private void returnFromAddons() {
        configuringAddons = false;
        minecraft.setScreen(this);
    }

    private void toggle(WorldCampaign campaign) {
        if (expanded == null) {
            expanded = campaign;
            select(campaign);
        } else expanded = null;
        descriptionScroll = 0;
        visibleDescriptionScroll = 0;
        rebuildWidgets();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        long now = Util.getMillis();
        if (!backgroundStarted) {
            backgroundMotion.start(((PanoramaRendererAccessor) PANORAMA).villagerretaliation$getSpin());
            backgroundStarted = true;
        }
        boolean hovering = cards.values().stream().anyMatch(card -> card.motion.contains(mouseX, mouseY));
        backgroundMotion.update(now, mouseX, mouseY, width, height, hovering, minecraft.options.panoramaSpeed().get());
        visibleDescriptionScroll += (descriptionScroll - visibleDescriptionScroll) * (1 - Math.exp(-backgroundMotion.delta() * 14));
        for (var entry : cardMotion.entrySet()) {
            var card = cards.get(entry.getKey());
            entry.getValue().update(now, backgroundMotion.delta(), card != null && (card.motion.contains(mouseX, mouseY) || card.isFocused()),
                    selected != null && selected.id().equals(entry.getKey()));
            entry.getValue().tilt(mouseX, mouseY, backgroundMotion.delta());
        }
        departingCards.removeIf(card -> !card.motion.visible());
        cardMotion.keySet().removeIf(id -> !cards.containsKey(id) && departingCards.stream().noneMatch(card -> card.campaign.id().equals(id)));
        for (var child : children()) {
            if (child instanceof Card card) card.updateBounds();
            else if (child instanceof Caret caret) caret.updateBounds();
        }
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
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        for (var card : departingCards) card.render(graphics, -1, -1, partialTick);
    }

    @Override
    protected void renderPanorama(GuiGraphics graphics, float partialTick) {
        CUBE_MAP.render(minecraft, backgroundMotion.pitch(), backgroundMotion.yaw(), 1);
        ((PanoramaRendererAccessor) PANORAMA).villagerretaliation$setSpin(-backgroundMotion.yaw());
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.blit(PanoramaRenderer.PANORAMA_OVERLAY, 0, 0, width, height, 0, 0, 16, 128, 16, 128);
        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        Card card = expanded == null ? null : cards.get(expanded.id());
        if (card != null && mouseX >= card.motion.x() + 132 * card.motion.scale()
                && mouseX < card.motion.x() + Math.min(365, card.motion.width() - 9) * card.motion.scale()
                && mouseY >= card.motion.y() + 9 * card.motion.scale() && mouseY < card.motion.y() + 183 * card.motion.scale()) {
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
        Map<ResourceLocation, CampaignMedia> nextAddonIcons = new LinkedHashMap<>();
        try (var resources = reloadResources.get()) {
            catalog = WorldCampaignCatalog.load(resources);
            nextCampaigns = catalog.choices();
            catalog.addonChoices().forEach(addon -> nextAddonIcons.put(addon.id(), CampaignMedia.addonIcon(resources, addon)));
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
            nextAddonIcons.values().forEach(CampaignMedia::close);
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
        addonIcons.values().forEach(CampaignMedia::close);
        addonIcons.clear();
        addonIcons.putAll(nextAddonIcons);
        this.catalog = catalog;
        campaigns = nextCampaigns;
        selected = selected == null ? null : catalog.campaigns().get(selected.id());
        if (selected != null && selected.locked()) selected = null;
        expanded = expanded == null ? null : catalog.campaigns().get(expanded.id());
        if (expanded != null) page = campaigns.indexOf(expanded) / PAGE_SIZE;
        descriptionScroll = 0;
        visibleDescriptionScroll = 0;
        descriptionHeight = 0;
        fadeDepartures = false;
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
        if (configuringAddons) return;
        addonIcons.values().forEach(CampaignMedia::close);
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
        private final CampaignMotion.Card motion;
        private final String authorText;
        private final Tooltip authorTooltip;
        private final Tooltip cardTooltip;

        private Card(int x, WorldCampaign campaign, boolean isExpanded, CampaignMotion.Card motion) {
            super(x, contentTop, Math.round((isExpanded ? EXPANDED_WIDTH : CARD_WIDTH) * cardScale), Math.round(CARD_HEIGHT * cardScale),
                    Component.literal(campaign.name()), button -> select(campaign), DEFAULT_NARRATION);
            this.campaign = campaign;
            this.motion = motion;
            active = !campaign.locked();
            Component tooltip = Component.literal(campaign.name());
            if (campaign.locked()) tooltip = tooltip.copy().append("\n").append(Component.translatable("screen.villagerretaliation.campaign.coming_soon"));
            authorText = fitAuthors(campaign.authors());
            authorTooltip = Tooltip.create(Component.literal(String.join(", ", campaign.authors())));
            cardTooltip = Tooltip.create(tooltip);
            setTooltip(cardTooltip);
        }

        private void updateBounds() {
            setX(Math.round(motion.x()));
            setY(Math.round(motion.y()));
            setWidth(Math.round(motion.width() * motion.scale()));
            setHeight(Math.round(CARD_HEIGHT * motion.scale()));
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (motion.overCaret(mouseX, mouseY)) return false;
            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        protected boolean clicked(double mouseX, double mouseY) {
            return active && visible && motion.contains(mouseX, mouseY);
        }

        @Override
        public boolean isMouseOver(double mouseX, double mouseY) {
            return active && visible && motion.contains(mouseX, mouseY);
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            if (!motion.visible()) return;
            float scale = motion.scale();
            graphics.setColor(1, 1, 1, motion.opacity());
            isHovered = motion.contains(mouseX, mouseY);
            int margin = Math.max(1, Math.round(4 * scale));
            graphics.enableScissor(getX() - margin, getY() - margin, getRight() + margin, getBottom() + margin);
            graphics.pose().pushPose();
            applyCardPose(graphics, motion);
            CampaignMedia image = images.get(campaign.image());
            if (image != null && image.available()) {
                if (WorldCampaign.CARD_FALLBACK.equals(campaign.image())) image.render(graphics, 0, 0, CARD_WIDTH, CARD_HEIGHT, campaign.locked());
                else image.render(graphics, 4, 4, 120, 184, campaign.locked());
            } else {
                float brightness = campaign.locked() ? 0.8F : 1;
                graphics.setColor(brightness, brightness, brightness, motion.opacity());
                blit(graphics, WorldCampaign.CARD_FALLBACK, 0, 0, CARD_WIDTH, CARD_HEIGHT);
                graphics.setColor(1, 1, 1, motion.opacity());
            }
            renderFrame(graphics);
            int selectionAlpha = Math.round(motion.selection() * 255);
            int hoverAlpha = Math.round(motion.hover() * (1 - motion.selection()) * 255);
            if (selectionAlpha > 0) graphics.renderOutline(2, 2, 124, 188, selectionAlpha << 24 | 0xf2cd83);
            if (hoverAlpha > 0 && active) graphics.renderOutline(2, 2, 124, 188, hoverAlpha << 24 | 0xeeeeee);
            double localX = motion.localX(mouseX, mouseY);
            double localY = motion.localY(mouseX, mouseY);
            boolean overCover = localX >= 0 && localX < CARD_WIDTH && localY >= 0 && localY < CARD_HEIGHT;
            boolean overAuthors = !authorText.isEmpty() && localX >= 7 && localX < 7 + font.width(authorText)
                    && localY >= AUTHOR_Y && localY < AUTHOR_Y + font.lineHeight;
            setTooltip(overAuthors ? authorTooltip : overCover ? cardTooltip : null);
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
            if (motion.width() > 141 && motion.descriptionOpacity() > 0.005F) {
                graphics.setColor(1, 1, 1, motion.opacity() * motion.descriptionOpacity());
                renderDescription(graphics, campaign, getX(), getY(), scale, motion.width());
            }
            graphics.pose().popPose();
            graphics.disableScissor();
            graphics.setColor(1, 1, 1, 1);
        }

        private void renderFrame(GuiGraphics graphics) {
            int width = motion.width();
            if (width == CARD_WIDTH) blit(graphics, CARD, 0, 0, CARD_WIDTH, CARD_HEIGHT);
            else if (width == EXPANDED_WIDTH) blit(graphics, EXPANDED, 0, 0, EXPANDED_WIDTH, CARD_HEIGHT);
            else {
                // reveal the native texture and move its right edge without stretching or layering its alpha
                graphics.enableScissor(getX() - 1, getY() - 1, Math.round(motion.x() + (width - 3) * motion.scale()), getBottom() + 1);
                blit(graphics, EXPANDED, 0, 0, EXPANDED_WIDTH, CARD_HEIGHT);
                graphics.disableScissor();
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                graphics.blit(EXPANDED, width - 3, 0, EXPANDED_WIDTH - 3, 0, 3, CARD_HEIGHT, EXPANDED_WIDTH, CARD_HEIGHT);
                RenderSystem.disableBlend();
            }
        }
    }

    private final class Caret extends Button {
        private final boolean isExpanded;
        private final CampaignMotion.Card motion;
        private float highlight;

        private Caret(int cardX, WorldCampaign campaign, boolean isExpanded, CampaignMotion.Card motion) {
            super(cardX + Math.round(111 * cardScale), contentTop + Math.round(82 * cardScale), Math.max(1, Math.round(17 * cardScale)),
                    Math.max(1, Math.round(28 * cardScale)), Component.translatable("screen.villagerretaliation.campaign." + (isExpanded ? "collapse" : "expand"), campaign.name()),
                    button -> toggle(campaign), DEFAULT_NARRATION);
            this.isExpanded = isExpanded;
            this.motion = motion;
            setTooltip(Tooltip.create(getMessage()));
        }

        private void updateBounds() {
            setX(Math.round(motion.x() + 111 * motion.scale()));
            setY(Math.round(motion.y() + 82 * motion.scale()));
            setWidth(Math.max(1, Math.round(17 * motion.scale())));
            setHeight(Math.max(1, Math.round(28 * motion.scale())));
        }

        @Override
        protected boolean clicked(double mouseX, double mouseY) {
            return active && visible && motion.overCaret(mouseX, mouseY);
        }

        @Override
        public boolean isMouseOver(double mouseX, double mouseY) {
            return active && visible && motion.overCaret(mouseX, mouseY);
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            if (!motion.visible()) return;
            isHovered = motion.overCaret(mouseX, mouseY);
            highlight += ((isHoveredOrFocused() ? 1 : 0) - highlight) * (1 - (float) Math.exp(-backgroundMotion.delta() * 14));
            graphics.pose().pushPose();
            applyCardPose(graphics, motion);
            graphics.pose().translate(111, 82, 0);
            graphics.setColor(1, 1 - 0.2F * highlight, 1 - 0.6F * highlight, motion.opacity());
            blit(graphics, isExpanded ? COLLAPSE : CARET, 5, 9, 7, 10);
            graphics.setColor(1, 1, 1, 1);
            graphics.pose().popPose();
        }
    }

    private static void applyCardPose(GuiGraphics graphics, CampaignMotion.Card motion) {
        graphics.pose().translate(motion.x(), motion.y(), 0);
        graphics.pose().scale(motion.scale(), motion.scale(), 1);
        graphics.pose().translate(64, 96, 0);
        graphics.pose().mulPose(Axis.ZP.rotationDegrees(motion.tilt()));
        graphics.pose().translate(-64, -96, 0);
    }

    private void renderDescription(GuiGraphics graphics, WorldCampaign campaign, int x, int top, float scale, int revealWidth) {
        int rightPadding = 9;
        int frameRight = EXPANDED_WIDTH - 3;
        int scrollbarRight = EXPANDED_WIDTH - rightPadding;
        int scrollbarLeft = scrollbarRight - 2;
        int scrollbarGap = frameRight - scrollbarRight;
        int contentRight = scrollbarLeft - scrollbarGap;
        int areaWidth = contentRight - 132;
        graphics.enableScissor(x + Math.round(132 * scale), top + Math.round(9 * scale),
                x + Math.round(Math.min(contentRight, revealWidth - 9) * scale), top + Math.round(183 * scale));
        int y = 0;
        for (var block : descriptions.get(campaign.id())) {
            if (block.image() != null) {
                ResourceLocation id = CampaignDescription.imageLocation(campaign.id(), block.image());
                CampaignMedia image = descriptionImages.get(id);
                if (image != null && image.available()) {
                    int imageHeight = image.height(areaWidth);
                    image.render(graphics, 132, 9 + y - (int) visibleDescriptionScroll, areaWidth, imageHeight);
                    y += imageHeight + 4;
                    continue;
                }
            }
            var lines = font.split(block.text(), (int) (areaWidth / block.scale()));
            graphics.pose().pushPose();
            graphics.pose().translate(132, 9 + y - (int) visibleDescriptionScroll, 0);
            graphics.pose().scale(block.scale(), block.scale(), 1);
            for (int index = 0; index < lines.size(); index++) graphics.drawString(font, lines.get(index), 0, index * font.lineHeight, 0xffffff, false);
            graphics.pose().popPose();
            y += Math.round(Math.max(1, lines.size()) * font.lineHeight * block.scale()) + 4;
        }
        graphics.disableScissor();
        descriptionHeight = y;
        if (descriptionHeight > 174) {
            int thumb = Math.max(8, 174 * 174 / descriptionHeight);
            int thumbTop = 9 + (int) (visibleDescriptionScroll * (174 - thumb) / (descriptionHeight - 174));
            graphics.fill(scrollbarLeft, thumbTop, scrollbarRight, thumbTop + thumb, 0xfff2cd83);
        }
    }

    private static void blit(GuiGraphics graphics, ResourceLocation texture, int x, int y, int width, int height) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.blit(texture, x, y, 0, 0, width, height, width, height);
        RenderSystem.disableBlend();
    }
}
