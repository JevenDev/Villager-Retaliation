package com.jvn.villagerretaliation.client.settlement;

import com.jvn.villagerretaliation.network.SettlementManagementPayload;
import com.jvn.villagerretaliation.network.SettlementManagementRequestPayload;
import com.jvn.villagerretaliation.network.SettlementManagementRequestPayload.Page;
import com.jvn.villagerretaliation.settlement.SettlementPermission;
import com.jvn.villagerretaliation.settlement.SettlementWorksiteLinks;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class SettlementWorksiteLinksScreen extends Screen {
    private final SettlementWorkerScreen parent;
    private final SettlementManagementPayload previousSnapshot;
    private final List<Button> rows = new ArrayList<>();
    private int tab;
    private int offset;
    private int left;
    private int top;
    private int panelWidth;
    private int panelHeight;
    private Button previous;
    private Button next;
    private Button automatic;

    public SettlementWorksiteLinksScreen(SettlementWorkerScreen parent) {
        super(text("worksite_links"));
        this.parent = parent;
        this.previousSnapshot = parent.parent().snapshot();
        request();
    }

    SettlementWorkerScreen parent() { return this.parent; }

    private Page page() { return this.tab == 2 ? Page.STATIONS : Page.DEPOTS; }
    private String query() { return this.tab == 2 ? this.parent.worksite().id().toString() : ""; }

    private void request() {
        this.parent.parent().navigate(page(), this.offset / SettlementManagementRequestPayload.PAGE_SIZE, query());
    }

    @Override
    protected void init() {
        this.panelWidth = Math.min(480, this.width - 32);
        this.panelHeight = Math.min(350, this.height - 24);
        this.left = (this.width - this.panelWidth) / 2;
        this.top = (this.height - this.panelHeight) / 2;
        int tabWidth = (this.panelWidth - 8) / 3;
        for (int index = 0; index < 3; index++) {
            int selected = index;
            addRenderableWidget(Button.builder(text(index == 0 ? "worksite_supplies" : index == 1 ? "worksite_outputs" : "worksite_stations"),
                    ignored -> {
                        this.tab = selected;
                        this.offset = 0;
                        request();
                        updateRows();
                    }).bounds(this.left + index * (tabWidth + 4), this.top + 24, tabWidth, 20).build());
        }
        this.rows.clear();
        int count = this.panelHeight < 280 ? 4 : 8;
        this.offset -= this.offset % count;
        for (int index = 0; index < count; index++) {
            int row = index;
            this.rows.add(addRenderableWidget(Button.builder(Component.empty(), ignored -> toggle(row))
                    .bounds(this.left, this.top + 54 + index * 24, this.panelWidth, 20).build()));
        }
        int bottom = this.top + this.panelHeight;
        this.previous = addRenderableWidget(Button.builder(Component.literal("<"), ignored -> move(-this.rows.size()))
                .bounds(this.left, bottom - 48, 24, 20).build());
        this.next = addRenderableWidget(Button.builder(Component.literal(">"), ignored -> move(this.rows.size()))
                .bounds(this.left + 28, bottom - 48, 24, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), ignored -> onClose())
                .bounds(this.left, bottom - 20, 94, 20).build());
        this.automatic = addRenderableWidget(Button.builder(text("worksite_automatic"), ignored -> {
            var links = this.parent.links();
            this.parent.links(new SettlementWorksiteLinks(this.tab == 0 ? java.util.Set.of() : links.supplies(),
                    this.tab == 1 ? java.util.Set.of() : links.outputs(), this.tab == 2 ? java.util.Set.of() : links.stations()));
            updateRows();
        }).bounds(this.left + this.panelWidth - 94, bottom - 20, 94, 20).build());
        updateRows();
    }

    private void move(int amount) {
        this.offset = Math.max(0, this.offset + amount);
        if (!ready()) request();
        updateRows();
    }

    private boolean ready() {
        var snapshot = this.parent.parent().snapshot();
        return snapshot.page() == page() && snapshot.query().equals(query())
                && snapshot.index() == this.offset / SettlementManagementRequestPayload.PAGE_SIZE;
    }

    private SettlementManagementPayload.Entry entry(int row) {
        var entries = this.parent.parent().snapshot().entries();
        int index = this.offset % SettlementManagementRequestPayload.PAGE_SIZE + row;
        return ready() && index < entries.size() ? entries.get(index) : null;
    }

    private int selectedCount() {
        var links = this.parent.links();
        return this.tab == 0 ? links.supplies().size() : this.tab == 1 ? links.outputs().size() : links.stations().size();
    }

    private boolean selected(SettlementManagementPayload.Entry entry) {
        var links = this.parent.links();
        return entry instanceof SettlementManagementPayload.Station station ? links.stations().contains(station.position())
                : this.tab == 0 ? links.supplies().contains(entry.id()) : links.outputs().contains(entry.id());
    }

    private void toggle(int row) {
        var entry = entry(row);
        if (entry == null) return;
        var links = this.parent.links();
        var supplies = new HashSet<>(links.supplies());
        var outputs = new HashSet<>(links.outputs());
        var stations = new HashSet<>(links.stations());
        if (entry instanceof SettlementManagementPayload.Station station) {
            if (!stations.remove(station.position())) stations.add(station.position());
        } else {
            var ids = this.tab == 0 ? supplies : outputs;
            if (!ids.remove(entry.id())) ids.add(entry.id());
        }
        this.parent.links(new SettlementWorksiteLinks(supplies, outputs, stations));
        updateRows();
    }

    private void updateRows() {
        if (this.automatic == null) return;
        var snapshot = this.parent.parent().snapshot();
        if (snapshot.page() == page() && snapshot.query().equals(query()) && this.offset >= snapshot.total()) {
            this.offset = Math.max(0, (snapshot.total() - 1) / this.rows.size() * this.rows.size());
        }
        boolean allowed = this.parent.parent().permits(this.tab == 2 ? SettlementPermission.STAFF : SettlementPermission.LOGISTICS)
                && !this.parent.parent().submitting();
        for (int index = 0; index < this.rows.size(); index++) {
            var button = this.rows.get(index);
            var entry = entry(index);
            button.visible = entry != null;
            if (entry == null) continue;
            Component name = Component.literal(entry.name());
            String position = "";
            if (entry instanceof SettlementManagementPayload.Station station) {
                var block = ResourceLocation.parse(station.name());
                name = Component.translatable("block." + block.getNamespace() + "." + block.getPath());
                position = station.position().toShortString();
            } else if (entry instanceof SettlementManagementPayload.Depot depot) {
                position = depot.position().toShortString();
            }
            Component label = Component.literal(selected(entry) ? "[x] " : "[ ] ").append(name).append(" (" + position + ")");
            button.setMessage(Component.literal(this.font.plainSubstrByWidth(label.getString(), this.panelWidth - 12)));
            button.setTooltip(Tooltip.create(label));
            boolean compatible = !(entry instanceof SettlementManagementPayload.Depot depot)
                    || this.parent.worksite().kind().role() == com.jvn.villagerretaliation.interaction.HiredVillagerRole.COURIER
                    || (this.tab == 0 ? depot.purpose().providesSupplies() : depot.purpose().acceptsOutputs());
            button.active = allowed && (selected(entry) || compatible && selectedCount() < SettlementWorksiteLinks.MAX_LINKS);
        }
        this.previous.active = ready() && this.offset > 0;
        this.next.active = ready() && this.offset + this.rows.size() < this.parent.parent().snapshot().total();
        this.automatic.active = allowed && selectedCount() > 0;
    }

    @Override
    public void tick() { updateRows(); }

    @Override
    public void onClose() {
        this.parent.parent().navigate(this.previousSnapshot.page(), this.previousSnapshot.index(), this.previousSnapshot.query());
        this.minecraft.setScreen(this.parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(this.left - 8, this.top - 8, this.left + this.panelWidth + 8, this.top + this.panelHeight + 8, 0xD0101010);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(this.font, this.title, this.left, this.top, 0xFFFFFF);
        Component status = text(selectedCount() == 0 ? "worksite_links_automatic" : "worksite_links_selected", selectedCount());
        SettlementManagementScreen.renderFeedback(graphics, this.font, status, this.left + 60,
                this.top + this.panelHeight - 42, this.panelWidth - 60, mouseX, mouseY);
    }

    private static Component text(String key, Object... arguments) { return SettlementManagementScreen.text(key, arguments); }
}
