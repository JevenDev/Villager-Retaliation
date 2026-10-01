package com.jvn.villagerretaliation.client.settlement;

import com.jvn.villagerretaliation.client.ui.VillagerSelectionList;
import com.jvn.villagerretaliation.client.ui.VillagerUi;
import com.jvn.villagerretaliation.client.ui.VillagerWindowScreen;
import com.jvn.villagerretaliation.network.SettlementPolicyRequestPayload;
import com.jvn.villagerretaliation.network.SettlementPolicySnapshotPayload;
import com.jvn.villagerretaliation.sell.CurrencyAmount;
import com.jvn.villagerretaliation.settlement.SettlementPurchasePlan;
import com.jvn.villagerretaliation.settlement.SettlementSupplyPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class SettlementPolicyPurchasesScreen extends VillagerWindowScreen implements SettlementPolicyClient.Child, SettlementPolicyClient.Receiver {
    private final SettlementPoliciesScreen previous;
    private final SettlementPolicyClientState state;
    private final SettlementSupplyPolicy.Configuration original;
    private final List<SettlementPurchasePlan.Approval> approvals = new ArrayList<>();
    private final java.util.Map<String, Button> buttons = new java.util.HashMap<>();
    private boolean enabled;
    private String budget;
    private UUID selected;
    private SettlementPolicySnapshotPayload preview;
    private SettlementSupplyPolicy.Configuration previewed;
    private Pending queued;
    private Pending submitted;
    private long nextRequest;
    private Rows rows;
    private EditBox budgetField;

    SettlementPolicyPurchasesScreen(SettlementPoliciesScreen previous) {
        super(SettlementPolicyClient.text("purchases"));
        this.previous = previous;
        this.original = previous.configuration();
        this.state = new SettlementPolicyClientState(previous.town());
        this.enabled = this.original.purchases().enabled();
        this.budget = SettlementEditScreen.currency(this.original.purchases().dailyBudget());
        this.approvals.addAll(this.original.purchases().approvals());
        refresh();
    }

    @Override public Screen previousPolicy() { return this.previous; }
    SettlementPolicySnapshotPayload snapshot() { return this.state.snapshot() == null ? this.previous.snapshot() : this.state.snapshot(); }
    List<SettlementPolicySnapshotPayload.Need> needs() { return this.preview == null ? List.of() : this.preview.needs(); }
    Component consumerName(UUID consumer) { return this.previous.consumerName(consumer); }
    String feedback() { return this.state.feedback(); }
    boolean editable() { return snapshot() != null && snapshot().editable(); }
    boolean ready() { return !this.state.waiting() && this.queued == null && Util.getMillis() >= this.nextRequest; }

    @Override protected void init() {
        initWindow(420, 340);
        this.buttons.clear();
        int half = (this.panelWidth - 8) / 2;
        button("purchase_enabled", 0, 36, half, () -> { this.enabled = !this.enabled; changed(); });
        this.buttons.get("purchase_enabled").setTooltip(Tooltip.create(SettlementPolicyClient.text("purchase_help")));
        this.budgetField = addRenderableWidget(new EditBox(this.font, this.left + half + 8, this.top + 36,
                this.panelWidth - half - 8, 20, SettlementPolicyClient.text("purchase_budget")));
        this.budgetField.setMaxLength(40);
        this.budgetField.setValue(this.budget);
        this.budgetField.setTooltip(Tooltip.create(SettlementPolicyClient.text("purchase_budget_help")));
        this.budgetField.setResponder(value -> {
            if (!this.budget.equals(value)) { this.budget = value; changed(); }
        });
        this.rows = addRenderableWidget(new Rows());
        int third = (this.panelWidth - 8) / 3;
        button("purchase_add", 0, this.panelHeight - 76, third, () -> this.minecraft.setScreen(new SettlementPurchaseApprovalScreen(this, null)));
        button("purchase_edit", third + 4, this.panelHeight - 76, third,
                () -> this.minecraft.setScreen(new SettlementPurchaseApprovalScreen(this, selectedApproval())));
        button("purchase_remove", 2 * third + 8, this.panelHeight - 76, third, () -> {
            this.approvals.removeIf(value -> value.id().equals(this.selected));
            this.selected = null;
            changed();
        });
        button("back", 0, this.panelHeight - 24, third, this::onClose);
        button("refresh", third + 4, this.panelHeight - 24, third, this::refresh);
        button("purchase_apply", 2 * third + 8, this.panelHeight - 24, third, () -> {
            var plan = plan();
            if (canApply(plan)) {
                this.previous.purchases(plan);
                onClose();
            }
        });
        this.rows.refresh();
        update();
    }

    private void button(String key, int x, int y, int width, Runnable action) {
        this.buttons.put(key, addRenderableWidget(VillagerUi.buttonBuilder(SettlementPolicyClient.text(key), ignored -> action.run())
                .bounds(this.left + x, this.top + y, width, 20).build()));
    }

    SettlementPurchasePlan plan() {
        try {
            return new SettlementPurchasePlan(this.enabled, SettlementEditScreen.amount(this.budget), this.approvals);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private SettlementSupplyPolicy.Configuration configuration() {
        var plan = plan();
        if (plan == null) plan = new SettlementPurchasePlan(false, CurrencyAmount.ZERO, this.approvals);
        return new SettlementSupplyPolicy.Configuration(this.original.name(), this.original.target(), this.original.priority(),
                this.original.steps(), this.original.connections(), plan);
    }

    private boolean canApply(SettlementPurchasePlan plan) {
        return ready() && editable() && plan != null
                && (!plan.enabled() || plan.equals(this.original.purchases()) || snapshot().trade());
    }

    boolean accepts(SettlementPurchasePlan.Approval approval) {
        var values = new ArrayList<>(this.approvals);
        values.removeIf(value -> value.id().equals(approval.id()));
        values.add(approval);
        try {
            new SettlementPurchasePlan(false, CurrencyAmount.ZERO, values);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    void approval(SettlementPurchasePlan.Approval approval) {
        if (!editable() || !accepts(approval)) return;
        int index = -1;
        for (int i = 0; i < this.approvals.size(); i++) if (this.approvals.get(i).id().equals(approval.id())) index = i;
        if (index < 0) this.approvals.add(approval);
        else this.approvals.set(index, approval);
        this.selected = approval.id();
        changed();
    }

    private SettlementPurchasePlan.Approval selectedApproval() {
        return this.approvals.stream().filter(value -> value.id().equals(this.selected)).findFirst().orElse(null);
    }

    private void changed() {
        this.previewed = null;
        refresh();
        if (this.rows != null) this.rows.refresh();
        update();
    }

    private void refresh() {
        this.queued = new Pending(new SettlementPolicyRequestPayload.Preview(configuration()), ignored -> {});
        this.nextRequest = Math.max(this.nextRequest, Util.getMillis() + 350);
    }

    void market(UUID consumer, UUID requirement, String query, int page, Consumer<SettlementPolicySnapshotPayload> response) {
        this.queued = new Pending(new SettlementPolicyRequestPayload.Market(configuration(), consumer, requirement, query, page), response);
    }

    @Override public void acceptPolicy(SettlementPolicySnapshotPayload payload) {
        if (!this.state.accept(payload)) {
            this.previous.acceptPolicy(payload);
            return;
        }
        if (payload.feedback().equals("busy")) {
            if (this.queued == null) this.queued = this.submitted;
            return;
        }
        if (this.submitted.command() instanceof SettlementPolicyRequestPayload.Preview command) {
            this.preview = payload;
            this.previewed = command.configuration();
            if (this.rows != null) this.rows.refresh();
        }
        this.submitted.response().accept(payload);
        update();
    }

    @Override public void tick() {
        this.state.expire(Util.getMillis());
        if (!this.state.waiting() && this.queued != null && Util.getMillis() >= this.nextRequest) {
            this.submitted = this.queued;
            this.queued = null;
            var request = this.state.request(this.previous.policyId(), this.previous.policyRevision(), null,
                    this.submitted.command(), Util.getMillis());
            if (request != null) {
                this.nextRequest = Util.getMillis() + 300;
                net.neoforged.neoforge.network.PacketDistributor.sendToServer(request);
            }
        }
        update();
    }

    private void update() {
        if (this.buttons.isEmpty()) return;
        this.buttons.get("purchase_enabled").setMessage(SettlementPolicyClient.text(this.enabled ? "purchase_on" : "purchase_off"));
        this.buttons.get("purchase_enabled").active = editable() && (this.enabled || snapshot().trade());
        this.budgetField.active = editable();
        this.buttons.get("purchase_add").active = ready() && editable() && !needs().isEmpty()
                && this.approvals.size() < SettlementPurchasePlan.MAX_APPROVALS;
        this.buttons.get("purchase_edit").active = ready() && selectedApproval() != null;
        this.buttons.get("purchase_remove").active = ready() && editable() && selectedApproval() != null;
        this.buttons.get("refresh").active = ready();
        this.buttons.get("purchase_apply").active = canApply(plan());
    }

    private SettlementPolicySnapshotPayload.Purchase purchase(UUID id) {
        return this.preview == null || !configuration().equals(this.previewed) ? null : this.preview.purchases().approvals().stream()
                .filter(value -> value.approval().equals(id)).findFirst().orElse(null);
    }

    static Component status(SettlementPolicySnapshotPayload.Purchase purchase) {
        return purchase == null ? SettlementPolicyClient.blocker("checking") : SettlementPolicyClient.text("purchase_status",
                purchase.known() ? Long.toString(purchase.missing()) : SettlementPolicyClient.text("unknown").getString(),
                purchase.waitTicks(), SettlementPolicyClient.blocker(purchase.blocker()));
    }

    private Component account() {
        if (this.preview == null || !configuration().equals(this.previewed)) return SettlementPolicyClient.blocker("checking");
        return SettlementPolicyClient.text("purchase_account", SettlementEditScreen.currency(this.preview.purchases().spent()),
                SettlementEditScreen.currency(this.preview.purchases().available()));
    }

    @Override public void onClose() { this.minecraft.setScreen(this.previous); }
    @Override public boolean isPauseScreen() { return false; }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.left + this.panelWidth / 2, this.top + 2, 0xFFFFFF);
        graphics.drawString(this.font, SettlementPolicyClient.text("purchase_budget"), this.budgetField.getX(), this.top + 24, 0xAAAAAA);
        graphics.drawString(this.font, this.font.plainSubstrByWidth(account().getString(), this.panelWidth), this.left, this.top + 62, 0xCCCCCC);
        Component feedback = plan() == null ? SettlementPolicyClient.text("purchase_invalid")
                : !editable() || this.enabled && !snapshot().trade() && !plan().equals(this.original.purchases())
                ? SettlementPolicyClient.blocker("policy_permission_changed") : SettlementPolicyClient.blocker(this.state.feedback());
        graphics.drawString(this.font, this.font.plainSubstrByWidth(feedback.getString(), this.panelWidth),
                this.left, this.top + this.panelHeight - 48, 0xCCCCCC);
        var row = this.rows.hovered();
        if (row != null) graphics.renderTooltip(this.font, this.font.split(row.help(), Math.min(330, this.width - 24)), mouseX, mouseY);
        else if (mouseX >= this.left && mouseX < this.left + this.panelWidth) {
            if (mouseY >= this.top + 60 && mouseY < this.top + 74) graphics.renderTooltip(this.font, account(), mouseX, mouseY);
            else if (mouseY >= this.top + this.panelHeight - 50 && mouseY < this.top + this.panelHeight - 34)
                graphics.renderTooltip(this.font, this.font.split(feedback, Math.min(330, this.width - 24)), mouseX, mouseY);
        }
    }

    private final class Rows extends VillagerSelectionList<Row> {
        Rows() { super(SettlementPolicyPurchasesScreen.this.minecraft, panelWidth, Math.max(28, panelHeight - 156), top + 76, 44); setX(left); }
        void refresh() {
            clearEntries();
            for (var approval : approvals) addEntry(new Row(approval));
            setSelected(children().stream().filter(row -> row.approval.id().equals(selected)).findFirst().orElse(null));
        }
        Row hovered() { return getHovered(); }
        @Override public int getRowWidth() { return getWidth() - 12; }
        @Override protected int getScrollbarPosition() { return getRight() - 6; }
    }

    private final class Row extends ObjectSelectionList.Entry<Row> {
        final SettlementPurchasePlan.Approval approval;
        Row(SettlementPurchasePlan.Approval approval) { this.approval = approval; }
        Component prices() {
            var current = purchase(this.approval.id());
            return SettlementPolicyClient.text("purchase_prices", SettlementEditScreen.currency(this.approval.unitPriceLimit()),
                    current == null || current.unitPrice().isZero() ? SettlementPolicyClient.text("unknown") : SettlementEditScreen.currency(current.unitPrice()));
        }
        Component help() {
            var need = needs().stream().filter(value -> java.util.Objects.equals(value.step(), this.approval.consumer())
                    && value.id().equals(this.approval.requirement())).findFirst().orElse(null);
            var result = this.approval.sample().getHoverName().copy().append("\n").append(consumerName(this.approval.consumer()))
                    .append(": ").append(need == null ? SettlementPolicyClient.text("missing_step") : SettlementPolicyClient.needName(need))
                    .append("\n").append(SettlementPolicyClient.text("purchase_depot_value", this.approval.destination().toShortString()))
                    .append("\n").append(prices()).append("\n").append(status(purchase(this.approval.id())));
            if (need != null) result.append("\n").append(SettlementPolicyClient.text("need_quantities", need.required(),
                    Component.translatable("villagerretaliation.gui.settlement.supply_unit." + need.unit().name().toLowerCase(java.util.Locale.ROOT)),
                    need.available(), need.committed(), need.incoming(), need.known() ? Long.toString(need.missing()) : SettlementPolicyClient.text("unknown")));
            return result;
        }
        @Override public void render(GuiGraphics graphics, int index, int top, int left, int width, int height,
                                     int mouseX, int mouseY, boolean hovered, float partialTick) {
            graphics.renderItem(this.approval.sample(), left + 3, top + 4);
            graphics.drawString(font, font.plainSubstrByWidth(this.approval.sample().getHoverName().getString(), width - 30), left + 24, top + 4, 0xFFFFFF);
            graphics.drawString(font, font.plainSubstrByWidth(prices().getString(), width - 6), left + 3, top + 18, 0xAAAAAA);
            graphics.drawString(font, font.plainSubstrByWidth(status(purchase(this.approval.id())).getString(), width - 6), left + 3, top + 30, 0xAAAAAA);
        }
        @Override public boolean mouseClicked(double x, double y, int button) {
            if (button != 0) return false;
            selected = this.approval.id();
            rows.setSelected(this);
            update();
            return true;
        }
        @Override public Component getNarration() { return help(); }
    }

    private record Pending(SettlementPolicyRequestPayload.Command command, Consumer<SettlementPolicySnapshotPayload> response) { }
}
