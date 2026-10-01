package com.jvn.villagerretaliation.client.settlement;

import com.jvn.villagerretaliation.client.ui.VillagerUi;
import com.jvn.villagerretaliation.client.ui.VillagerWindowScreen;
import com.jvn.villagerretaliation.network.SettlementPolicySnapshotPayload;
import com.jvn.villagerretaliation.settlement.SettlementPurchasePlan;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

final class SettlementPurchaseApprovalScreen extends VillagerWindowScreen implements SettlementPolicyClient.Child {
    private final SettlementPolicyPurchasesScreen previous;
    private final UUID id;
    private final java.util.Map<String, Button> buttons = new java.util.HashMap<>();
    private UUID consumer;
    private UUID requirement;
    private ResourceLocation item;
    private BlockPos destination;
    private String price = "0";
    private SettlementPolicySnapshotPayload.MarketPage market;
    private boolean initialized;
    private EditBox priceField;

    SettlementPurchaseApprovalScreen(SettlementPolicyPurchasesScreen previous, SettlementPurchasePlan.Approval approval) {
        super(SettlementPolicyClient.text("purchase_approval"));
        this.previous = previous;
        this.id = approval == null ? UUID.randomUUID() : approval.id();
        if (approval != null) {
            this.consumer = approval.consumer();
            this.requirement = approval.requirement();
            this.item = approval.item();
            this.destination = approval.destination();
            this.price = SettlementEditScreen.currency(approval.unitPriceLimit());
        }
    }

    @Override public Screen previousPolicy() { return this.previous; }

    @Override protected void init() {
        initWindow(410, 260);
        this.buttons.clear();
        button("choose_requirement", 24, this::chooseRequirement);
        button("purchase_commodity", 48, () -> this.minecraft.setScreen(new SettlementPurchaseMarketScreen(this.previous, this,
                this.consumer, this.requirement, page -> {
                    this.market = page.page();
                    this.item = page.commodity().item();
                    this.price = SettlementEditScreen.currency(page.commodity().unitPrice());
                    selectOnlyDepot();
                    rebuildWidgets();
                })));
        button("purchase_depot", 72, () -> this.minecraft.setScreen(new SettlementPolicyChoiceScreen(this,
                SettlementPolicyClient.text("purchase_depot"), query -> depots().stream().map(depot ->
                new SettlementPolicyChoiceScreen.Choice(Component.literal(depot.name()), Component.literal(depot.position().toShortString()),
                        ItemStack.EMPTY, () -> { this.destination = depot.position(); rebuildWidgets(); })).toList())));
        this.priceField = addRenderableWidget(new EditBox(this.font, this.left, this.top + 112, this.panelWidth, 20,
                SettlementPolicyClient.text("purchase_unit_limit")));
        this.priceField.setMaxLength(40);
        this.priceField.setValue(this.price);
        this.priceField.setTooltip(Tooltip.create(SettlementPolicyClient.text("purchase_price_help")));
        this.priceField.setResponder(value -> { this.price = value; update(); });
        this.buttons.put("back", addRenderableWidget(VillagerUi.buttonBuilder(SettlementPolicyClient.text("back"), ignored -> onClose())
                .bounds(this.left, this.top + this.panelHeight - 24, 84, 20).build()));
        this.buttons.put("select", addRenderableWidget(VillagerUi.buttonBuilder(SettlementPolicyClient.text("select"), ignored -> {
            var approval = approval();
            if (this.previous.editable() && approval != null && this.previous.accepts(approval)) {
                this.previous.approval(approval);
                onClose();
            }
        }).bounds(this.left + this.panelWidth - 100, this.top + this.panelHeight - 24, 100, 20).build()));
        update();
        if (!this.initialized) {
            this.initialized = true;
            requestDepots();
        }
    }

    private void button(String key, int y, Runnable action) {
        this.buttons.put(key, addRenderableWidget(VillagerUi.buttonBuilder(SettlementPolicyClient.text(key), ignored -> action.run())
                .bounds(this.left, this.top + y, this.panelWidth, 20).build()));
    }

    private void chooseRequirement() {
        this.minecraft.setScreen(new SettlementPolicyChoiceScreen(this, SettlementPolicyClient.text("choose_requirement"), query ->
                this.previous.needs().stream().map(need -> new SettlementPolicyChoiceScreen.Choice(SettlementPolicyClient.needName(need),
                        this.previous.consumerName(need.step()), need.samples().isEmpty() ? ItemStack.EMPTY : need.samples().getFirst(), () -> {
                    this.consumer = need.step();
                    this.requirement = need.id();
                    this.item = null;
                    this.destination = null;
                    this.price = "0";
                    this.market = null;
                    requestDepots();
                    rebuildWidgets();
                })).toList()));
    }

    private void requestDepots() {
        if (this.requirement == null) return;
        UUID selectedRequirement = this.requirement;
        UUID selectedConsumer = this.consumer;
        this.previous.market(selectedConsumer, selectedRequirement, "", 0, payload -> {
            if (!Objects.equals(this.consumer, selectedConsumer) || !selectedRequirement.equals(this.requirement)) return;
            this.market = payload.market();
            selectOnlyDepot();
            update();
        });
    }

    private void selectOnlyDepot() {
        if (this.destination == null && depots().size() == 1) this.destination = depots().getFirst().position();
    }

    private List<SettlementPolicySnapshotPayload.Storage> depots() {
        return this.market == null ? List.of() : this.market.depots();
    }

    private SettlementPurchasePlan.Approval approval() {
        if (this.requirement == null || this.item == null || this.destination == null) return null;
        try {
            var price = SettlementEditScreen.amount(this.price);
            if (price.isZero()) return null;
            return new SettlementPurchasePlan.Approval(this.id, this.consumer, this.requirement, this.item, this.destination, price);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private void update() {
        if (this.buttons.isEmpty()) return;
        var need = this.previous.needs().stream().filter(value -> Objects.equals(value.step(), this.consumer)
                && value.id().equals(this.requirement)).findFirst().orElse(null);
        var label = need == null ? SettlementPolicyClient.text("choose_requirement")
                : this.previous.consumerName(this.consumer).copy().append(": ").append(SettlementPolicyClient.needName(need));
        this.buttons.get("choose_requirement").setMessage(label);
        this.buttons.get("purchase_commodity").setMessage(this.item == null ? SettlementPolicyClient.text("purchase_commodity")
                : new ItemStack(BuiltInRegistries.ITEM.get(this.item)).getHoverName());
        this.buttons.get("purchase_depot").setMessage(this.destination == null ? SettlementPolicyClient.text("purchase_depot")
                : SettlementPolicyClient.text("purchase_depot_value", this.destination.toShortString()));
        this.buttons.get("choose_requirement").active = this.previous.editable() && !this.previous.needs().isEmpty();
        this.buttons.get("purchase_commodity").active = this.previous.editable() && this.requirement != null && this.previous.ready();
        this.buttons.get("purchase_depot").active = this.previous.editable() && !depots().isEmpty() && this.previous.ready();
        this.priceField.active = this.previous.editable();
        var approval = approval();
        this.buttons.get("select").active = this.previous.ready() && this.previous.editable() && approval != null
                && this.previous.accepts(approval) && depots().stream().anyMatch(depot -> depot.position().equals(this.destination));
        for (var entry : this.buttons.entrySet()) if (!entry.getKey().equals("back") && !entry.getKey().equals("select"))
            entry.getValue().setTooltip(Tooltip.create(entry.getValue().getMessage()));
    }

    @Override public void tick() { this.previous.tick(); update(); }
    @Override public void onClose() { this.minecraft.setScreen(this.previous); }
    @Override public boolean isPauseScreen() { return false; }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.left + this.panelWidth / 2, this.top + 2, 0xFFFFFF);
        graphics.drawString(this.font, SettlementPolicyClient.text("purchase_unit_limit"), this.left, this.top + 100, 0xAAAAAA);
        var approval = approval();
        Component help = approval != null && !this.previous.accepts(approval) ? SettlementPolicyClient.text("purchase_duplicate")
                : this.market != null && depots().isEmpty() ? SettlementPolicyClient.blocker("policy_purchase_depot")
                : SettlementPolicyClient.text("purchase_price_help");
        int y = this.top + 140;
        for (var line : this.font.split(help, this.panelWidth)) {
            if (y + this.font.lineHeight > this.top + this.panelHeight - 30) break;
            graphics.drawString(this.font, line, this.left, y, 0xAAAAAA);
            y += 11;
        }
    }
}
