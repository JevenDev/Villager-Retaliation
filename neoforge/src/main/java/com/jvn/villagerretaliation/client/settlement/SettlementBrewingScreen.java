package com.jvn.villagerretaliation.client.settlement;

import com.jvn.villagerretaliation.interaction.work.brewing.HiredBrewingRecipeCatalog;
import com.jvn.villagerretaliation.interaction.work.brewing.HiredBrewingRecipeCatalog.BrewingRoute;
import com.jvn.villagerretaliation.network.SettlementManagementActionPayload;
import com.jvn.villagerretaliation.network.SettlementManagementActionResultPayload;
import com.jvn.villagerretaliation.network.SettlementManagementPayload;
import com.jvn.villagerretaliation.settlement.SettlementBrewingOrder;
import com.jvn.villagerretaliation.settlement.SettlementPermission;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;

public final class SettlementBrewingScreen extends Screen {
    private final SettlementWorkerScreen previous;
    private final SettlementManagementPayload.Resident resident;
    private List<BrewingRoute> recipes;
    private BrewingRoute selected;
    private String query = "";
    private String quantity;
    private boolean continuous;
    private Component feedback = Component.empty();
    private Recipes list;
    private EditBox amount;
    private Button mode;
    private Button save;
    private Button cancel;
    private int left;
    private int panelWidth;
    private int top;
    private int panelHeight;

    public SettlementBrewingScreen(SettlementWorkerScreen previous, SettlementManagementPayload.Resident resident) {
        super(text("brewing_title", resident.name().isBlank() ? text("unnamed_resident") : Component.literal(resident.name())));
        this.previous = previous;
        this.resident = resident;
        this.quantity = Integer.toString(resident.brewingOrder() == null ? 3 : resident.brewingOrder().amount());
        this.continuous = resident.brewingOrder() != null && resident.brewingOrder().continuous();
    }

    SettlementManagementScreen parent() {
        return this.previous.parent();
    }

    void result(SettlementManagementActionResultPayload payload) {
        if (parent().snapshot().header() == null || !parent().snapshot().header().id().equals(payload.town())) {
            return;
        }
        if (payload.applied() && !parent().resultWasLate()) {
            this.minecraft.setScreen(parent());
        } else {
            this.feedback = SettlementManagementScreen.resultMessage(payload);
            updateActions();
        }
    }

    @Override
    protected void init() {
        var layout = SettlementViewClient.panelLayout(this.width, this.height, Math.min(520, this.width - 32), this.height - 24, true);
        this.panelWidth = layout.width();
        this.left = layout.left();
        this.top = layout.top();
        this.panelHeight = layout.height();
        this.recipes = HiredBrewingRecipeCatalog.routes(this.minecraft.level);
        if (this.selected == null && this.resident.brewingOrder() != null) {
            var order = this.resident.brewingOrder();
            this.selected = HiredBrewingRecipeCatalog.find(this.minecraft.level, order.item(), order.potion()).orElse(null);
        }
        var search = addRenderableWidget(new EditBox(this.font, this.left, this.top + 24, this.panelWidth, 20, text("search")));
        search.setMaxLength(64);
        search.setHint(text("search"));
        search.setValue(this.query);
        this.list = addRenderableWidget(new Recipes());
        search.setResponder(value -> {
            this.query = value;
            this.list.refresh();
        });
        this.list.refresh();
        int controls = this.top + this.panelHeight - 92;
        this.amount = addRenderableWidget(new EditBox(this.font, this.left + 54, controls, 64, 20, text("brewing_amount")));
        this.amount.setMaxLength(4);
        this.amount.setFilter(value -> value.isEmpty() || value.chars().allMatch(Character::isDigit));
        this.amount.setValue(this.quantity);
        this.amount.setResponder(value -> {
            this.quantity = value;
            updateActions();
        });
        this.mode = addRenderableWidget(Button.builder(Component.empty(), ignored -> {
            this.continuous = !this.continuous;
            updateActions();
        }).bounds(this.left + 124, controls, this.panelWidth - 124, 20).build());
        int third = (this.panelWidth - 8) / 3;
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), ignored -> onClose())
                .bounds(this.left, this.top + this.panelHeight - 16, third, 20).build());
        this.cancel = addRenderableWidget(Button.builder(text("brewing_cancel"), ignored -> submit(null))
                .bounds(this.left + third + 4, this.top + this.panelHeight - 16, third, 20).build());
        this.save = addRenderableWidget(Button.builder(text("brewing_order"), ignored -> submit(new SettlementBrewingOrder(
                this.selected.itemId(), this.selected.potionId(), this.continuous ? 1 : amount(), this.continuous)))
                .bounds(this.left + (third + 4) * 2, this.top + this.panelHeight - 16, third, 20).build());
        updateActions();
        setInitialFocus(search);
    }

    private int amount() {
        try {
            return Integer.parseInt(this.quantity);
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private void submit(SettlementBrewingOrder order) {
        parent().submit(new SettlementManagementActionPayload.Brew(this.resident.id(), this.resident.area(), order));
        this.feedback = text("saving");
        updateActions();
    }

    private void updateActions() {
        if (this.save == null) {
            return;
        }
        boolean allowed = !parent().submitting() && parent().permits(SettlementPermission.STAFF);
        this.save.active = allowed && this.selected != null
                && (this.continuous || amount() >= 1 && amount() <= SettlementBrewingOrder.MAX_AMOUNT);
        this.cancel.active = allowed && this.resident.brewingOrder() != null;
        this.mode.active = allowed;
        this.mode.setMessage(text(this.continuous ? "brewing_continuous" : "brewing_fixed"));
        this.amount.active = allowed && !this.continuous;
    }

    @Override
    public void tick() {
        if (parent().unconfirmed()) this.feedback = text("acknowledgement_timeout");
        updateActions();
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.previous);
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
        graphics.drawCenteredString(this.font, this.font.plainSubstrByWidth(this.title.getString(), this.panelWidth),
                this.left + this.panelWidth / 2, this.top + 4, 0xFFFFFF);
        graphics.drawString(this.font, text("brewing_amount"), this.left, this.top + this.panelHeight - 86, 0xFFFFFF);
        graphics.drawWordWrap(this.font, this.feedback.getString().isEmpty() ? text("brewing_hint") : this.feedback,
                this.left, this.top + this.panelHeight - 64, this.panelWidth, 0xD0D0D0);
        var hovered = this.list.hovered();
        if (hovered != null) {
            graphics.renderTooltip(this.font, hovered.route.output(), mouseX, mouseY);
        }
    }

    private static Component text(String key, Object... arguments) {
        return SettlementManagementScreen.text(key, arguments);
    }

    private final class Recipes extends ObjectSelectionList<Recipe> {
        private Recipes() {
            super(SettlementBrewingScreen.this.minecraft, panelWidth, Math.max(32, panelHeight - 152), top + 52, 36);
            setX(left);
        }

        private Recipe hovered() {
            return getHovered();
        }

        private void refresh() {
            clearEntries();
            String needle = query.strip().toLowerCase(Locale.ROOT);
            for (var route : recipes) {
                if (route.output().getHoverName().getString().toLowerCase(Locale.ROOT).contains(needle)
                        || route.potionId().toString().contains(needle) || route.itemId().toString().contains(needle)) {
                    var row = new Recipe(route);
                    addEntry(row);
                    if (route == selected) {
                        setSelected(row);
                    }
                }
            }
            if (children().stream().noneMatch(row -> row.route == selected)) {
                selected = null;
            }
            if (getSelected() == null) {
                setScrollAmount(0.0D);
            } else {
                centerScrollOn(getSelected());
            }
            updateActions();
        }

        @Override
        public void setSelected(Recipe row) {
            super.setSelected(row);
            if (row != null) {
                selected = row.route;
                feedback = Component.empty();
                updateActions();
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

    private final class Recipe extends ObjectSelectionList.Entry<Recipe> {
        private final BrewingRoute route;
        private final Component detail;

        private Recipe(BrewingRoute route) {
            this.route = route;
            var tooltip = route.output().getTooltipLines(Item.TooltipContext.of(minecraft.level), minecraft.player, TooltipFlag.Default.NORMAL);
            this.detail = tooltip.size() > 1 ? tooltip.get(1) : Component.empty();
        }

        @Override
        public Component getNarration() {
            return this.route.output().getHoverName().copy().append(" ").append(this.detail);
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
            graphics.renderItem(this.route.output(), x + 3, y + 8);
            graphics.drawString(font, font.plainSubstrByWidth(this.route.output().getHoverName().getString(), width - 28), x + 25, y + 4, 0xFFFFFF);
            graphics.drawString(font, font.plainSubstrByWidth(this.detail.getString(), width - 28), x + 25, y + 17, 0xBBBBBB);
        }
    }
}
