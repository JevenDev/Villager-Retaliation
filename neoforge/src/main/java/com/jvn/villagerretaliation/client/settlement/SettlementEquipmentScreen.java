package com.jvn.villagerretaliation.client.settlement;

import com.jvn.villagerretaliation.client.ui.VillagerUi;
import com.jvn.villagerretaliation.client.ui.VillagerWindowScreen;
import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.work.HiredEquipmentNeed.Kind;
import com.jvn.villagerretaliation.network.SettlementManagementActionPayload;
import com.jvn.villagerretaliation.network.SettlementManagementActionResultPayload;
import com.jvn.villagerretaliation.network.SettlementManagementPayload;
import com.jvn.villagerretaliation.network.SettlementManagementRequestPayload.Page;
import com.jvn.villagerretaliation.settlement.SettlementEquipmentPlan;
import com.jvn.villagerretaliation.settlement.SettlementPermission;
import com.jvn.villagerretaliation.settlement.SettlementSupplyPolicy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public final class SettlementEquipmentScreen extends VillagerWindowScreen {
    private final SettlementWorkerScreen previousScreen;
    private final SettlementManagementScreen parent;
    private final SettlementManagementPayload.Area area;
    private final List<Kind> kinds;
    private final Map<Kind, Selection> selections = new LinkedHashMap<>();
    private final List<Button> rows = new ArrayList<>();
    private SettlementEquipmentPlan expected;
    private Kind selected;
    private int offset;
    private int priority = 5;
    private boolean enabled;
    private boolean available;
    private boolean reload = true;
    private boolean conflict;
    private boolean submitted;
    private Component feedback = Component.empty();
    private Button active;
    private Button priorityButton;
    private Button previous;
    private Button next;
    private Button include;
    private Button equip;
    private Button protect;
    private Button save;
    private Button remove;
    private Button policies;
    private EditBox amount;

    public SettlementEquipmentScreen(SettlementWorkerScreen previous) {
        super(text("title", previous.worksite().name()));
        this.previousScreen = previous;
        this.parent = previous.parent();
        this.area = previous.worksite();
        this.kinds = Arrays.stream(Kind.values()).filter(kind -> kind.reservable() && kind.reserveFor(this.area.kind().role())).toList();
        this.selected = this.kinds.getFirst();
        request();
    }

    SettlementManagementScreen parent() { return this.parent; }

    private void request() { this.parent.navigate(Page.AREAS, 0, this.area.id().toString()); }

    void accept(SettlementManagementPayload payload) {
        if (payload.page() != Page.AREAS || !payload.query().equals(this.area.id().toString()) || this.parent.snapshot() != payload) return;
        var updated = payload.entries().stream().filter(SettlementManagementPayload.Area.class::isInstance)
                .map(SettlementManagementPayload.Area.class::cast).filter(value -> value.id().equals(this.area.id())).findFirst().orElse(null);
        this.available = updated != null && updated.plan() != null && updated.kind() == this.area.kind();
        if (!this.available) {
            this.feedback = SettlementManagementScreen.text("failure.area_missing");
            update();
            return;
        }
        if (this.reload) {
            this.expected = updated.equipment();
            this.selections.clear();
            this.priority = this.expected == null ? 5 : this.expected.configuration().priority();
            this.enabled = this.expected != null && this.expected.active();
            if (this.expected != null) for (var selection : this.expected.configuration().selections())
                this.selections.put(selection.kind(), new Selection(selection));
            this.reload = false;
            this.conflict = false;
            if (this.expected != null && !this.expected.blocker().isEmpty())
                this.feedback = SettlementPolicyClient.blocker(this.expected.blocker());
            selected(this.selected);
        } else if (!java.util.Objects.equals(this.expected, updated.equipment())) {
            this.conflict = true;
            this.feedback = SettlementManagementScreen.text("failure.state_changed");
        }
        update();
    }

    @Override protected void init() {
        initWindow(400, 340);
        this.active = button(Component.empty(), this.left, this.top + 24, this.panelWidth - 104, () -> { this.enabled = !this.enabled; update(); });
        this.priorityButton = button(Component.empty(), this.left + this.panelWidth - 100, this.top + 24, 100,
                () -> { this.priority = Math.floorMod(this.priority + (hasShiftDown() ? -1 : 1), 10); update(); });
        this.priorityButton.setTooltip(Tooltip.create(text("priority_hint")));
        this.rows.clear();
        int count = Math.max(1, (this.panelHeight - 186) / 24);
        for (int index = 0; index < count; index++) {
            int row = index;
            this.rows.add(button(Component.empty(), this.left, this.top + 52 + index * 24, this.panelWidth,
                    () -> selected(this.kinds.get(this.offset + row))));
        }
        int bottom = this.top + this.panelHeight;
        this.previous = button(Component.literal("<"), this.left, bottom - 128, 24, () -> { this.offset -= this.rows.size(); update(); });
        this.next = button(Component.literal(">"), this.left + 28, bottom - 128, 24, () -> { this.offset += this.rows.size(); update(); });
        int third = (this.panelWidth - 8) / 3;
        this.include = button(Component.empty(), this.left, bottom - 102, third, this::toggle);
        this.equip = button(Component.empty(), this.left + third + 4, bottom - 102, third, this::equip);
        this.protect = button(Component.empty(), this.left + (third + 4) * 2, bottom - 102, this.panelWidth - (third + 4) * 2, () -> {
            var selection = this.selections.get(this.selected);
            selection.protectedMinimum = !selection.protectedMinimum;
            update();
        });
        this.protect.setTooltip(Tooltip.create(text("protect_hint")));
        this.amount = addRenderableWidget(new EditBox(this.font, this.left + 110, bottom - 76, 54, 20, text("amount")));
        this.amount.setMaxLength(4);
        this.amount.setFilter(value -> value.isEmpty() || value.chars().allMatch(Character::isDigit));
        this.amount.setTooltip(Tooltip.create(text("amount_hint")));
        this.amount.setResponder(value -> {
            var selection = this.selections.get(this.selected);
            if (selection != null) selection.amount = value;
            update();
        });
        this.policies = button(SettlementPolicyClient.text("title"), this.left + this.panelWidth - Math.min(128, (this.panelWidth - 8) / 3), bottom - 76, Math.min(128, (this.panelWidth - 8) / 3),
                () -> this.minecraft.setScreen(new SettlementPoliciesScreen(this, this.parent.snapshot().header().id(),
                        new SettlementSupplyPolicy.Target(SettlementSupplyPolicy.TargetKind.EQUIPMENT, this.area.id(), null, null, ItemStack.EMPTY, 0))));
        int quarter = (this.panelWidth - 12) / 4;
        button(Component.translatable("gui.back"), this.left, bottom - 20, quarter, this::onClose);
        button(text("reload"), this.left + quarter + 4, bottom - 20, quarter, () -> {
            if (this.parent.submitting()) return;
            this.reload = true;
            this.feedback = Component.empty();
            request();
            update();
        });
        this.remove = button(text("remove"), this.left + (quarter + 4) * 2, bottom - 20, quarter, () -> submit(null));
        this.save = button(SettlementManagementScreen.text("save"), this.left + (quarter + 4) * 3, bottom - 20,
                this.panelWidth - (quarter + 4) * 3, () -> submit(configuration()));
        selected(this.selected);
    }

    private Button button(Component label, int x, int y, int width, Runnable action) {
        return addRenderableWidget(VillagerUi.buttonBuilder(label, ignored -> action.run()).bounds(x, y, width, 20).build());
    }

    private void selected(Kind kind) {
        this.selected = kind;
        var selection = this.selections.get(kind);
        if (this.amount != null) this.amount.setValue(selection == null ? "" : selection.amount);
        update();
    }

    private void toggle() {
        if (this.selections.remove(this.selected) == null) {
            boolean equip = this.area.kind().role() == HiredVillagerRole.COMBAT && this.selected.guardSlot() != null;
            if (equip) releaseSlot(this.selected);
            this.selections.put(this.selected, new Selection(new SettlementEquipmentPlan.Selection(this.selected,
                    this.selected == Kind.AMMUNITION ? 32 : 1, equip, false)));
            if (equip && (this.selected == Kind.BOW || this.selected == Kind.CROSSBOW))
                this.selections.computeIfAbsent(Kind.AMMUNITION, kind -> new Selection(new SettlementEquipmentPlan.Selection(kind, 32, false, false)));
        }
        selected(this.selected);
    }

    private void equip() {
        var selection = this.selections.get(this.selected);
        if (!selection.equip) releaseSlot(this.selected);
        selection.equip = !selection.equip;
        update();
    }

    private void releaseSlot(Kind kind) {
        this.selections.entrySet().removeIf(entry -> entry.getKey() != kind && entry.getKey().guardSlot() == kind.guardSlot()
                && entry.getValue().amount.equals("0"));
        for (var entry : this.selections.entrySet()) if (entry.getKey() != kind && entry.getKey().guardSlot() == kind.guardSlot())
            entry.getValue().equip = false;
    }

    private SettlementEquipmentPlan.Configuration configuration() {
        if (this.selections.isEmpty()) return null;
        var values = new ArrayList<SettlementEquipmentPlan.Selection>();
        for (var entry : this.selections.entrySet()) {
            var selection = entry.getValue();
            int count = selection.amount.isEmpty() ? -1 : Integer.parseInt(selection.amount);
            if (count < 0 || count > SettlementEquipmentPlan.MAX_SPARES || count == 0 && !selection.equip) return null;
            values.add(new SettlementEquipmentPlan.Selection(entry.getKey(), count, selection.equip, selection.protectedMinimum));
        }
        return new SettlementEquipmentPlan.Configuration(values, this.priority, this.enabled);
    }

    private void submit(SettlementEquipmentPlan.Configuration configuration) {
        this.submitted = true;
        this.parent.submit(new SettlementManagementActionPayload.SaveEquipment(this.area.id(), this.expected == null ? -1 : this.expected.revision(), configuration));
        this.feedback = SettlementManagementScreen.text("saving");
        update();
    }

    void result(SettlementManagementActionResultPayload payload) {
        this.submitted = false;
        this.feedback = SettlementManagementScreen.resultMessage(payload);
        if (payload.applied()) this.reload = true;
        request();
        update();
    }

    private void update() {
        if (this.save == null) return;
        boolean editable = this.available && !this.reload && !this.conflict && !this.parent.submitting()
                && this.parent.permits(SettlementPermission.STAFF);
        this.active.active = editable;
        this.active.setMessage(text(this.enabled ? "active" : "paused"));
        this.priorityButton.active = editable;
        this.priorityButton.setMessage(text("priority", this.priority));
        this.offset = Math.clamp(this.offset, 0, Math.max(0, (this.kinds.size() - 1) / this.rows.size() * this.rows.size()));
        for (int index = 0; index < this.rows.size(); index++) {
            var button = (VillagerUi.OptionButton) this.rows.get(index);
            int position = this.offset + index;
            button.visible = position < this.kinds.size();
            button.active = button.visible && !this.parent.submitting();
            if (!button.visible) continue;
            var kind = this.kinds.get(position);
            button.setMessage(label(kind));
            button.checkbox(true, this.selections.containsKey(kind));
            button.selected(kind == this.selected);
        }
        var selection = this.selections.get(this.selected);
        this.include.active = editable;
        this.include.setMessage(text(selection == null ? "add" : "exclude"));
        this.equip.active = editable && selection != null && this.selected.guardSlot() != null && this.area.kind().role() == HiredVillagerRole.COMBAT;
        this.equip.setMessage(text(selection != null && selection.equip ? "equip" : "spares_only"));
        this.protect.active = editable && selection != null;
        this.protect.setMessage(text(selection != null && selection.protectedMinimum ? "protected" : "optional"));
        this.amount.setEditable(editable && selection != null);
        this.amount.active = editable && selection != null;
        this.previous.active = this.offset > 0;
        this.next.active = this.offset + this.rows.size() < this.kinds.size();
        this.save.active = editable && configuration() != null;
        this.remove.active = editable && this.expected != null;
        this.policies.active = this.available && this.expected != null && this.expected.active() && !this.parent.submitting();
    }

    @Override public void tick() {
        if (this.submitted && !this.parent.submitting()) {
            this.submitted = false;
            this.conflict = true;
            this.feedback = SettlementManagementScreen.text("acknowledgement_timeout");
            request();
        }
        update();
    }

    @Override public void onClose() {
        if (!this.parent.submitting()) this.minecraft.setScreen(this.previousScreen);
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(this.font, this.font.plainSubstrByWidth(this.title.getString(), this.panelWidth), this.left, this.top, 0xFFFFFF);
        int bottom = this.top + this.panelHeight;
        graphics.drawString(this.font, this.font.plainSubstrByWidth(label(this.selected).getString(), this.panelWidth - 60),
                this.left + 60, bottom - 122, 0xD0D0D0);
        graphics.drawString(this.font, text(this.selected == Kind.AMMUNITION ? "projectiles" : "amount"), this.left, bottom - 70, 0xD0D0D0);
        SettlementManagementScreen.renderFeedback(graphics, this.font, this.feedback.getString().isEmpty() ? text(!this.selections.isEmpty() && configuration() == null ? "invalid" : "hint") : this.feedback,
                this.left, bottom - 46, this.panelWidth, mouseX, mouseY);
    }

    private static Component label(Kind kind) { return text("kind." + kind.name().toLowerCase(java.util.Locale.ROOT)); }
    static Component text(String key, Object... args) { return SettlementManagementScreen.text("equipment." + key, args); }

    private static final class Selection {
        String amount;
        boolean equip;
        boolean protectedMinimum;
        Selection(SettlementEquipmentPlan.Selection selection) {
            this.amount = Integer.toString(selection.spares());
            this.equip = selection.equip();
            this.protectedMinimum = selection.protectedMinimum();
        }
    }
}
