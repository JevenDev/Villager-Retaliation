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
    private BuilderStructureCatalog.Entry selected;
    private Blueprints list;
    private Button draft;
    private int left;
    private int panelWidth;
    private int top;
    private int panelHeight;

    public SettlementBlueprintScreen(SettlementManagementScreen parent) {
        super(SettlementManagementScreen.text("building_prefabs"));
        this.parent = parent;
        var draft = parent.buildingDraft();
        this.selected = draft == null ? null : entries.stream().filter(entry -> entry.id().equals(draft.structure)).findFirst().orElse(null);
    }

    SettlementManagementScreen parent() {
        return this.parent;
    }

    @Override
    protected void init() {
        var layout = SettlementViewClient.panelLayout(this.width, this.height, Math.min(500, this.width - 32), this.height - 24, true);
        this.panelWidth = layout.width();
        this.left = layout.left();
        this.top = layout.top();
        this.panelHeight = layout.height();
        EditBox search = new EditBox(this.font, this.left, this.top + 24, this.panelWidth, 20, SettlementManagementScreen.text("search"));
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
    }

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
            super(SettlementBlueprintScreen.this.minecraft, panelWidth, Math.max(32, panelHeight - 126), top + 52, 26);
            setX(left);
        }

        private void refresh() {
            clearEntries();
            String needle = query.strip().toLowerCase(Locale.ROOT);
            for (var entry : entries) {
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
            graphics.drawString(font, font.plainSubstrByWidth(this.entry.menuLabel(), width - 8), x + 3, y + 7, 0xFFFFFF);
        }
    }
}
