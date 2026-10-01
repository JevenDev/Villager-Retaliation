package com.jvn.villagerretaliation.client.inventory;

import com.jvn.villagerretaliation.client.settlement.SettlementManagementClient;
import com.jvn.villagerretaliation.client.settlement.SettlementPolicyClient;
import com.jvn.villagerretaliation.client.ui.VillagerSelectionList;
import com.jvn.villagerretaliation.client.ui.VillagerUi;
import com.jvn.villagerretaliation.client.ui.VillagerWindowScreen;
import com.jvn.villagerretaliation.interaction.work.production.HiredProductionDemand.Requirement;
import com.jvn.villagerretaliation.network.ProductionRequestPayload;
import com.jvn.villagerretaliation.network.ProductionSnapshotPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class ProductionSupplyScreen extends VillagerWindowScreen implements ProductionClient.Receiver, SettlementPolicyClient.Child {
    private final ProductionOrdersScreen previous;
    private final UUID order;
    private UUID selected;
    private Rows rows;
    private Button refresh;
    private Button competitor;

    ProductionSupplyScreen(ProductionOrdersScreen previous, UUID order) {
        super(text("title"));
        this.previous = previous;
        this.order = java.util.Objects.requireNonNull(order);
    }

    @Override public Screen previousPolicy() { return this.previous; }

    @Override protected void init() {
        initWindow(440, 330);
        this.rows = addRenderableWidget(new Rows());
        addRenderableWidget(VillagerUi.buttonBuilder(Component.translatable("gui.back"), ignored -> onClose())
                .bounds(this.left, this.top + this.panelHeight - 24, 76, 20).build());
        this.refresh = addRenderableWidget(VillagerUi.buttonBuilder(Component.translatable("villagerretaliation.gui.settlement.refresh"),
                ignored -> this.previous.submit(new ProductionRequestPayload.View()))
                .bounds(this.left + 80, this.top + this.panelHeight - 24, 78, 20).build());
        this.competitor = addRenderableWidget(VillagerUi.buttonBuilder(Component.translatable("villagerretaliation.gui.settlement.demand_competitor"),
                ignored -> {
                    var need = selected();
                    if (need != null && need.allocation().competitor() != null) {
                        var other = need.allocation().competitor();
                        SettlementManagementClient.inspectDemand(other.town(), other.consumer());
                    }
                }).bounds(this.left + 162, this.top + this.panelHeight - 24, this.panelWidth - 162, 20).build());
        refreshRows();
    }

    @Override public void tick() {
        this.previous.tick();
        updateActions();
    }

    @Override public void acceptProduction(ProductionSnapshotPayload payload) {
        if (this.previous.acceptSnapshot(payload)) refreshRows();
    }

    private void refreshRows() {
        double scroll = this.rows.getScrollAmount();
        this.rows.clear();
        var snapshot = this.previous.state().snapshot();
        if (snapshot != null) for (var need : snapshot.requirements()) if (need.order().equals(this.order)) this.rows.row(new Row(need));
        var chosen = this.rows.children().stream().filter(row -> row.need.id().equals(this.selected)).findFirst().orElse(null);
        if (chosen == null && !this.rows.children().isEmpty()) chosen = this.rows.children().getFirst();
        this.rows.setSelected(chosen);
        this.selected = chosen == null ? null : chosen.need.id();
        this.rows.setScrollAmount(scroll);
        updateActions();
    }

    private Requirement selected() {
        var row = this.rows.getSelected();
        return row == null ? null : row.need;
    }

    private void updateActions() {
        this.refresh.active = !this.previous.state().busy();
        var need = selected();
        this.competitor.active = !this.previous.state().busy() && need != null && need.allocation().competitor() != null;
    }

    @Override public void onClose() { this.minecraft.setScreen(this.previous); }
    @Override public boolean isPauseScreen() { return false; }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.left + this.panelWidth / 2, this.top + 2, 0xFFFFFF);
        if (this.rows.children().isEmpty()) {
            String feedback = this.previous.state().feedback();
            graphics.drawWordWrap(this.font, feedback.isEmpty() ? text("empty") : Component.literal(feedback),
                    this.left, this.top + 30, this.panelWidth, 0xBBBBBB);
        }
        var hovered = this.rows.hovered();
        if (hovered != null) graphics.renderTooltip(this.font, details(hovered.need).stream()
                .flatMap(line -> this.font.split(line, Math.min(340, this.width - 24)).stream()).toList(), mouseX, mouseY);
    }

    static Component text(String key, Object... args) { return Component.translatable("villagerretaliation.gui.production.supply." + key, args); }

    static List<Component> details(Requirement need) {
        var lines = new ArrayList<Component>();
        lines.add(Component.literal(need.name()));
        lines.add(Component.translatable("villagerretaliation.gui.settlement.policy.need_quantities", need.required(), unit(need.unit()),
                need.availableNow(), need.committed(), need.incoming(),
                need.missing() < 0 ? Component.translatable("villagerretaliation.gui.settlement.supply_unknown") : need.missing()));
        var allocation = need.allocation();
        lines.add(Component.translatable("villagerretaliation.gui.settlement.demand_allocation",
                allocation.protectedMinimum(), allocation.priority(), allocation.targetReserve()));
        if (allocation.competitor() != null) {
            var other = allocation.competitor();
            lines.add(Component.translatable("villagerretaliation.gui.settlement.demand_allocated", other.name(), other.priority(),
                    Component.literal(Long.toString(other.protectedMinimum())).append(" ").append(unit(other.unit()))));
        } else if (!allocation.blocker().isEmpty()) {
            lines.add(Component.translatableWithFallback("villagerretaliation.gui.production.supply." + allocation.blocker(), text("unavailable").getString()));
        }
        return List.copyOf(lines);
    }

    private static Component unit(com.jvn.villagerretaliation.interaction.work.production.WorkSupplyRequirement.Unit unit) {
        return Component.translatable("villagerretaliation.gui.settlement.supply_unit." + unit.name().toLowerCase(java.util.Locale.ROOT));
    }

    private final class Rows extends VillagerSelectionList<Row> {
        Rows() { super(ProductionSupplyScreen.this.minecraft, panelWidth, Math.max(32, panelHeight - 60), top + 24, 100); setX(left); }
        void row(Row row) { addEntry(row); }
        void clear() { clearEntries(); }
        Row hovered() { return getHovered(); }
        @Override public int getRowWidth() { return getWidth() - 12; }
        @Override protected int getScrollbarPosition() { return getRight() - 6; }
    }

    private final class Row extends ObjectSelectionList.Entry<Row> {
        private final Requirement need;
        Row(Requirement need) { this.need = need; }

        @Override public void render(GuiGraphics graphics, int index, int top, int left, int width, int height,
                                     int mouseX, int mouseY, boolean hovered, float partialTick) {
            int y = top + 4;
            var lines = new ArrayList<>(details(this.need));
            if (this.need.allocation().competitor() != null) lines.set(lines.size() - 1, text("competing", this.need.allocation().competitor().name()));
            for (int part = 0; part < lines.size(); part++) for (var line : font.split(lines.get(part), width - 6)) {
                if (y + font.lineHeight > top + height - 2) return;
                graphics.drawString(font, line, left + 3, y, part == 0 ? 0xFFFFFF : 0xBBBBBB);
                y += 11;
            }
        }

        @Override public boolean mouseClicked(double x, double y, int button) {
            if (button != 0) return false;
            selected = this.need.id();
            rows.setSelected(this);
            updateActions();
            return true;
        }

        @Override public Component getNarration() {
            var result = Component.empty();
            for (var line : details(this.need)) result.append(line).append(" ");
            return result;
        }
    }
}
