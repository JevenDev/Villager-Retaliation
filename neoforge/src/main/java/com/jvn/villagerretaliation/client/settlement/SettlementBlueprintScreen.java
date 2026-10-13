package com.jvn.villagerretaliation.client.settlement;

import com.jvn.villagerretaliation.interaction.work.builder.BuilderStructureCatalog;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class SettlementBlueprintScreen extends Screen {
    private final SettlementManagementScreen parent;
    private final List<BuilderStructureCatalog.Entry> entries = BuilderStructureCatalog.entries();
    private String query = "";
    private String houseSize = "small";
    private BuilderStructureCatalog.Entry selected;
    private Blueprints list;
    private Button draft;
    private int left;
    private int panelWidth;
    private int top;
    private int panelHeight;
    private final java.util.Map<net.minecraft.resources.ResourceLocation, java.util.List<ThumbnailPixel>> thumbnails = new java.util.LinkedHashMap<>();
    private final java.util.Set<net.minecraft.resources.ResourceLocation> visible = new java.util.LinkedHashSet<>();
    private net.minecraft.resources.ResourceLocation requested;
    private int thumbnailRequest;
    private long thumbnailSent;

    public SettlementBlueprintScreen(SettlementManagementScreen parent) {
        super(SettlementManagementScreen.text("building_prefabs"));
        this.parent = parent;
        var draft = parent.buildingDraft();
        this.selected = draft == null ? null : entries.stream().filter(entry -> entry.id().equals(draft.structure)).findFirst().orElse(null);
    }

    SettlementManagementScreen parent() {
        return this.parent;
    }

    int thumbnailCount() { return this.thumbnails.size(); }

    @Override
    protected void init() {
        var layout = SettlementViewClient.panelLayout(this.width, this.height, Math.min(500, this.width - 32), this.height - 24, true);
        this.panelWidth = layout.width();
        this.left = layout.left();
        this.top = layout.top();
        this.panelHeight = layout.height();
        int fourth = (this.panelWidth - 12) / 4;
        String[] sizes = { "small", "medium", "large", "" };
        for (int index = 0; index < sizes.length; index++) {
            String size = sizes[index];
            addRenderableWidget(Button.builder(Component.literal(size.isEmpty() ? "All buildings" : "House " + Character.toUpperCase(size.charAt(0)) + size.substring(1)), ignored -> {
                this.houseSize = size;
                this.list.refresh();
            }).bounds(this.left + index * (fourth + 4), this.top + 24, fourth, 20).build());
        }
        EditBox search = new EditBox(this.font, this.left, this.top + 48, this.panelWidth, 20, SettlementManagementScreen.text("search"));
        search.setMaxLength(64);
        search.setHint(SettlementManagementScreen.text("search"));
        search.setValue(this.query);
        addRenderableWidget(search);
        this.list = addRenderableWidget(new Blueprints());
        search.setResponder(value -> {
            this.query = value;
            this.list.refresh();
        });
        this.list.refresh();
        int half = (this.panelWidth - 6) / 2;
        this.draft = addRenderableWidget(Button.builder(SettlementManagementScreen.text("place_prefab"), ignored -> {
            if (this.selected != null) {
                this.parent.placeBuilding(this.selected);
            }
        }).bounds(this.left, this.top + this.panelHeight - 16, half, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), ignored -> onClose())
                .bounds(this.left + half + 6, this.top + this.panelHeight - 16, half, 20).build());
        updateButton();
        setInitialFocus(search);
    }

    @Override
    public void tick() {
        updateButton();
        long now = net.minecraft.Util.getMillis();
        if (this.requested != null && now - this.thumbnailSent > 3_000L) this.requested = null;
        if (this.requested == null && now - this.thumbnailSent >= 250L && this.parent.snapshot().header() != null) {
            this.requested = this.visible.stream().filter(id -> !this.thumbnails.containsKey(id)).findFirst().orElse(null);
            if (this.requested != null) {
                this.thumbnailSent = now;
                this.thumbnailRequest++;
                net.neoforged.neoforge.network.PacketDistributor.sendToServer(new com.jvn.villagerretaliation.network.SettlementBuildingPreviewRequestPayload(
                        this.parent.snapshot().header().id(), this.thumbnailRequest, this.requested,
                        this.parent.snapshot().header().center().above(), net.minecraft.world.level.block.Rotation.NONE, null));
            }
        }
        this.visible.clear();
    }

    void acceptPreview(com.jvn.villagerretaliation.network.SettlementBuildingPreviewPayload payload) {
        if (this.requested == null || !this.requested.equals(payload.structure()) || payload.requestId() != this.thumbnailRequest
                || this.parent.snapshot().header() == null || !payload.town().equals(this.parent.snapshot().header().id())) return;
        var pixels = new java.util.ArrayList<ThumbnailPixel>();
        if (this.minecraft.level != null && !payload.blocks().isEmpty()) {
            int[] raster = new int[32 * 22];
            var occupied = payload.blocks().stream().filter(block -> !block.state().isAir())
                    .map(com.jvn.villagerretaliation.network.SettlementBuildingPreviewPayload.PreviewBlock::local)
                    .collect(java.util.stream.Collectors.toSet());
            double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY, maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
            for (var block : payload.blocks()) {
                if (block.state().isAir()) continue;
                double x = block.local().getX() - block.local().getZ();
                double y = (block.local().getX() + block.local().getZ()) * 0.5D - block.local().getY();
                minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                minY = Math.min(minY, y); maxY = Math.max(maxY, y);
            }
            double scale = Math.min(32D / Math.max(1D, maxX - minX + 1), 21D / Math.max(1D, maxY - minY + 1));
            var blocks = payload.blocks().stream().filter(block -> !block.state().isAir())
                    .sorted(java.util.Comparator.comparingInt(block -> block.local().getX() + block.local().getZ() + block.local().getY())).toList();
            for (var block : blocks) {
                int x = (int) Math.round((block.local().getX() - block.local().getZ() - minX) * scale);
                int y = (int) Math.round(((block.local().getX() + block.local().getZ()) * 0.5D - block.local().getY() - minY) * scale);
                int color = block.state().getMapColor(this.minecraft.level, net.minecraft.core.BlockPos.ZERO).col;
                // Exposed top and side faces remain legible even with a single-color sandstone palette.
                double shade = !occupied.contains(block.local().above()) ? 1.12D
                        : !occupied.contains(block.local().east()) ? 0.82D
                        : !occupied.contains(block.local().south()) ? 0.65D : 0.92D;
                int size = Math.max(1, (int) Math.ceil(scale));
                for (int py = Math.max(0, y); py < Math.min(22, y + size); py++) {
                    for (int px = Math.max(0, x); px < Math.min(32, x + size); px++) {
                        raster[py * 32 + px] = shade(color, shade);
                    }
                }
            }
            // Rasterize once. Render cost is bounded by thumbnail pixels, never by template block count.
            for (int y = 0; y < 22; y++) {
                for (int x = 0; x < 32;) {
                    int color = raster[y * 32 + x];
                    int end = x + 1;
                    while (end < 32 && raster[y * 32 + end] == color) end++;
                    if (color != 0) pixels.add(new ThumbnailPixel(x, y, end - x, color));
                    x = end;
                }
            }
        }
        if (this.thumbnails.size() >= 32) this.thumbnails.remove(this.thumbnails.keySet().iterator().next());
        this.thumbnails.put(this.requested, java.util.List.copyOf(pixels));
        this.requested = null;
    }

    private static int shade(int color, double factor) {
        int red = Math.min(255, (int) (((color >> 16) & 255) * factor));
        int green = Math.min(255, (int) (((color >> 8) & 255) * factor));
        int blue = Math.min(255, (int) ((color & 255) * factor));
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }

    private record ThumbnailPixel(int x, int y, int width, int color) {}

    private void updateButton() {
        if (this.draft != null) {
            this.draft.active = this.selected != null && !this.parent.submitting();
        }
    }

    @Override
    public void onClose() {
        if (!SettlementViewClient.resume(this.parent)) this.minecraft.setScreen(this.parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!SettlementViewClient.renderPanelBackground(graphics, this.left, this.top, this.panelWidth, this.panelHeight)) {
            super.renderBackground(graphics, mouseX, mouseY, partialTick);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.left + this.panelWidth / 2, this.top + 4, 0xFFFFFF);
        Component message = SettlementManagementScreen.text("building_prefab_hint");
        graphics.drawWordWrap(this.font, message, this.left, this.top + this.panelHeight - 68, this.panelWidth, 0xD0D0D0);
    }

    private final class Blueprints extends ObjectSelectionList<BlueprintRow> {
        private Blueprints() {
            super(SettlementBlueprintScreen.this.minecraft, panelWidth, Math.max(32, panelHeight - 150), top + 76, 26);
            setX(left);
        }

        private void refresh() {
            clearEntries();
            String needle = query.strip().toLowerCase(Locale.ROOT);
            for (var entry : entries) {
                if (!houseSize.isEmpty() && !entry.houseSize().equals(houseSize)) continue;
                if (entry.menuLabel().toLowerCase(Locale.ROOT).contains(needle) || entry.id().toString().contains(needle)) {
                    var row = new BlueprintRow(entry);
                    addEntry(row);
                    if (entry.equals(selected)) {
                        setSelected(row);
                    }
                }
            }
            if (children().stream().noneMatch(row -> row.entry.equals(selected))) {
                selected = null;
            }
            setScrollAmount(0.0D);
            updateButton();
        }

        @Override
        public void setSelected(BlueprintRow row) {
            super.setSelected(row);
            if (row != null) {
                selected = row.entry;
                updateButton();
            }
        }

        @Override
        public int getRowWidth() {
            return getWidth() - 12;
        }

        @Override
        protected int getScrollbarPosition() {
            return getRight() - 6;
        }
    }

    private final class BlueprintRow extends ObjectSelectionList.Entry<BlueprintRow> {
        private final BuilderStructureCatalog.Entry entry;

        private BlueprintRow(BuilderStructureCatalog.Entry entry) {
            this.entry = entry;
        }

        @Override
        public Component getNarration() {
            return Component.literal(this.entry.menuLabel());
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button != 0) {
                return false;
            }
            list.setSelected(this);
            return true;
        }

        @Override
        public void render(GuiGraphics graphics, int index, int y, int x, int width, int height,
                           int mouseX, int mouseY, boolean hovered, float partialTick) {
            visible.add(this.entry.id());
            var thumbnail = thumbnails.get(this.entry.id());
            if (thumbnail == null) graphics.drawString(font, "...", x + 12, y + 7, 0x888888);
            else for (var pixel : thumbnail) graphics.fill(x + 3 + pixel.x(), y + 2 + pixel.y(),
                    x + 3 + pixel.x() + pixel.width(), y + 3 + pixel.y(), pixel.color());
            graphics.drawString(font, font.plainSubstrByWidth(this.entry.menuLabel(), width - 48), x + 42, y + 7, 0xFFFFFF);
        }
    }
}
