package com.jvn.villagerretaliation.client.settlement;

import com.jvn.villagerretaliation.interaction.work.builder.BuilderStructureCatalog;
import com.jvn.villagerretaliation.network.SettlementManagementActionPayload;
import com.jvn.villagerretaliation.network.SettlementManagementActionResultPayload;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;

public final class SettlementBlueprintScreen extends Screen {
    private final SettlementManagementScreen parent;
    private final List<BuilderStructureCatalog.Entry> entries = BuilderStructureCatalog.entries();
    private String query = "";
    private BuilderStructureCatalog.Entry selected;
    private Component feedback = Component.empty();
    private Blueprints list;
    private Button draft;
    private Button place;
    private Button categoryButton;
    private String category = "";
    private com.jvn.villagerretaliation.item.ConstructionBlueprintItem.PreviewData preview;
    private net.minecraft.resources.ResourceLocation requested;
    private long previewAt;
    private int left;
    private int panelWidth;

    public SettlementBlueprintScreen(SettlementManagementScreen parent) {
        super(SettlementManagementScreen.text("blueprint_catalog"));
        this.parent = parent;
    }

    SettlementManagementScreen parent() {
        return this.parent;
    }

    void result(SettlementManagementActionResultPayload payload) {
        var candidate = com.jvn.villagerretaliation.item.ConstructionBlueprintItem.previewData(payload.preview()).orElse(null);
        if (candidate != null) {
            if (this.selected != null && candidate.structureId().equals(this.selected.id())) {
                this.preview = candidate;
                this.feedback = Component.empty();
            }
        } else {
            this.feedback = payload.reason().isEmpty() ? SettlementManagementScreen.text(payload.applied() ? "blueprint_ready" : "blueprint_failed")
                    : Component.literal(payload.reason());
        }
    }

    @Override
    protected void init() {
        this.panelWidth = Math.min(500, this.width - 32);
        this.left = (this.width - this.panelWidth) / 2;
        EditBox search = new EditBox(this.font, this.left, 36, this.panelWidth - 126, 20, SettlementManagementScreen.text("search"));
        search.setMaxLength(64);
        search.setHint(SettlementManagementScreen.text("search"));
        search.setValue(this.query);
        addRenderableWidget(search);
        this.categoryButton = addRenderableWidget(Button.builder(Component.literal(this.category.isEmpty() ? "All village types" : this.category), ignored -> {
            var categories = new java.util.ArrayList<String>();
            categories.add("");
            entries.stream().map(BuilderStructureCatalog.Entry::category).distinct().forEach(categories::add);
            this.category = categories.get((categories.indexOf(this.category) + 1) % categories.size());
            this.categoryButton.setMessage(Component.literal(this.category.isEmpty() ? "All village types" : this.category));
            this.list.refresh();
        }).bounds(this.left + this.panelWidth - 120, 36, 120, 20).build());
        this.list = addRenderableWidget(new Blueprints());
        search.setResponder(value -> {
            this.query = value;
            this.list.refresh();
        });
        this.list.refresh();
        int half = (this.panelWidth - 12) / 3;
        this.draft = addRenderableWidget(Button.builder(SettlementManagementScreen.text("draft_blueprint_cost"), ignored -> {
            if (this.selected != null) {
                this.feedback = SettlementManagementScreen.text("saving");
                this.parent.submit(new SettlementManagementActionPayload.DraftBlueprint(this.selected.id()));
            }
        }).bounds(this.left, this.height - 28, half, 20).build());
        this.place = addRenderableWidget(Button.builder(Component.literal("Place building"), ignored ->
                com.jvn.villagerretaliation.client.settlement.SettlementViewClient.beginOrder(this.parent, this.preview))
                .bounds(this.left + half + 6, this.height - 28, half, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), ignored -> onClose())
                .bounds(this.left + 2 * (half + 6), this.height - 28, half, 20).build());
        addRenderableWidget(Button.builder(Component.literal("< Previous"), ignored -> cycle(-1))
                .bounds(this.left, this.height - 125, 88, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Next >"), ignored -> cycle(1))
                .bounds(this.left + this.panelWidth - 88, this.height - 125, 88, 20).build());
        updateButton();
        setInitialFocus(search);
    }

    @Override
    public void tick() {
        if (this.selected != null && !this.selected.id().equals(this.requested) && net.minecraft.Util.getMillis() >= this.previewAt) {
            this.requested = this.selected.id();
            this.previewAt = net.minecraft.Util.getMillis() + 350L;
            net.neoforged.neoforge.network.PacketDistributor.sendToServer(new SettlementManagementActionPayload(
                    this.parent.snapshot().header().id(), new SettlementManagementActionPayload.PreviewStructure(this.selected.id())));
        }
        updateButton();
    }

    private void cycle(int direction) {
        var rows = this.list.children();
        if (rows.isEmpty()) return;
        int index = 0;
        for (int i = 0; i < rows.size(); i++) if (rows.get(i).entry.equals(this.selected)) index = i;
        this.list.setSelected(rows.get(Math.floorMod(index + direction, rows.size())));
        this.list.revealSelected();
    }

    private void updateButton() {
        if (this.place != null) this.place.active = this.preview != null && !this.parent.submitting();
        if (this.draft != null) {
            this.draft.active = this.selected != null && !this.parent.submitting()
                    && this.minecraft.player != null && this.minecraft.player.getInventory().countItem(Items.PAPER) > 0;
        }
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 16, 0xFFFFFF);
        Component message = this.feedback;
        if (message.getString().isEmpty()) message = this.preview == null ? Component.literal("Select a building to load its size and materials.")
                : Component.literal(this.preview.sizeText() + "  /  " + this.preview.totalBlocks() + " blocks\n"
                        + com.jvn.villagerretaliation.interaction.work.builder.BuilderStructureScanner.materialSummary(
                                com.jvn.villagerretaliation.interaction.work.builder.BuilderStructureScanner.materialRequirements(
                                        this.preview.blocks().stream().map(com.jvn.villagerretaliation.item.ConstructionBlueprintItem.PreviewBlock::state).toList()), 64));
        if (this.selected != null) graphics.drawCenteredString(this.font,
                this.font.plainSubstrByWidth(this.selected.label(), Math.max(32, this.panelWidth - 184)), this.width / 2, this.height - 119, 0xFFFFFF);
        var lines = this.font.split(message, this.panelWidth);
        int y = this.height - 98;
        for (var line : lines) {
            if (y + this.font.lineHeight > this.height - 34) break;
            graphics.drawString(this.font, line, this.left, y, 0xD0D0D0);
            y += this.font.lineHeight + 1;
        }
        if (mouseX >= this.left && mouseX < this.left + this.panelWidth && mouseY >= this.height - 98 && mouseY < this.height - 34)
            graphics.renderTooltip(this.font, lines, mouseX, mouseY);
    }

    private final class Blueprints extends ObjectSelectionList<BlueprintRow> {
        private Blueprints() {
            super(SettlementBlueprintScreen.this.minecraft, panelWidth, Math.max(32, SettlementBlueprintScreen.this.height - 200), 64, 26);
            setX(left);
        }

        private void revealSelected() {
            ensureVisible(getSelected());
        }

        private void refresh() {
            clearEntries();
            String needle = query.strip().toLowerCase(Locale.ROOT);
            for (var entry : entries) {
                if ((category.isEmpty() || entry.category().equals(category))
                        && (entry.menuLabel().toLowerCase(Locale.ROOT).contains(needle) || entry.id().toString().contains(needle))) {
                    var row = new BlueprintRow(entry);
                    addEntry(row);
                    if (entry.equals(selected)) {
                        setSelected(row);
                    }
                }
            }
            if (children().stream().noneMatch(row -> row.entry.equals(selected))) {
                selected = null;
                preview = null;
                if (!children().isEmpty()) setSelected(children().getFirst());
            }
            setScrollAmount(0.0D);
            updateButton();
        }

        @Override
        public void setSelected(BlueprintRow row) {
            super.setSelected(row);
            if (row != null) {
                if (!row.entry.equals(selected)) {
                    preview = null;
                    requested = null;
                }
                selected = row.entry;
                feedback = Component.empty();
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
            graphics.drawString(font, font.plainSubstrByWidth(this.entry.menuLabel(), width - 8), x + 3, y + 7, 0xFFFFFF);
        }
    }
}
