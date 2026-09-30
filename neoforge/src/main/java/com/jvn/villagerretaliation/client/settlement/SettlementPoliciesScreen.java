package com.jvn.villagerretaliation.client.settlement;

import com.jvn.villagerretaliation.client.ui.VillagerSelectionList;
import com.jvn.villagerretaliation.client.ui.VillagerUi;
import com.jvn.villagerretaliation.client.ui.VillagerWindowScreen;
import com.jvn.villagerretaliation.network.SettlementPolicyRequestPayload;
import com.jvn.villagerretaliation.network.SettlementPolicySnapshotPayload;
import com.jvn.villagerretaliation.settlement.SettlementSupplyPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public final class SettlementPoliciesScreen extends VillagerWindowScreen implements SettlementPolicyClient.Receiver, SettlementPolicyClient.Child {
    private final Screen previous;
    private final SettlementPolicyClientState state;
    private final java.util.Map<String, Button> buttons = new java.util.HashMap<>();
    private Draft draft;
    private UUID selected;
    private UUID area;
    private boolean editing;
    private boolean loadSelected;
    private boolean initialized;
    private long previewAt = Long.MAX_VALUE;
    private long nextRequest;
    private SettlementSupplyPolicy.Configuration previewSent;
    private SettlementSupplyPolicy.Configuration previewed;
    private Runnable afterView;
    private boolean showPreview;
    private SettlementPolicyRequestPayload.Command submitted;
    private Rows rows;

    public SettlementPoliciesScreen(Screen previous, UUID town) { this(previous, town, null); }

    public SettlementPoliciesScreen(Screen previous, UUID town, SettlementSupplyPolicy.Target target) {
        super(SettlementPolicyClient.text("title"));
        this.previous = previous;
        this.state = new SettlementPolicyClientState(town);
        if (target != null) {
            this.draft = new Draft(UUID.randomUUID());
            this.draft.target = target;
            this.draft.amount = Integer.toString(target.amount());
            this.editing = true;
        }
    }

    @Override public Screen previousPolicy() { return this.previous; }
    SettlementPolicySnapshotPayload snapshot() { return this.state.snapshot(); }
    UUID town() { return this.state.town(); }

    @Override protected void init() {
        initWindow(420, 380);
        this.buttons.clear();
        if (this.editing && this.draft != null) {
            var name = addRenderableWidget(new EditBox(this.font, this.left, this.top + 24, this.panelWidth - 104, 20, SettlementPolicyClient.text("name")));
            name.setMaxLength(64);
            name.setValue(this.draft.name);
            name.setResponder(value -> { this.draft.name = value; changed(); });
            button("priority", this.panelWidth - 100, 24, 100, () -> { this.draft.priority = (this.draft.priority + 1) % 10; changed(); });
            boolean stock = this.draft.target != null && this.draft.target.kind() == SettlementSupplyPolicy.TargetKind.STOCK;
            button("target", 0, 48, this.panelWidth - (stock ? 72 : 0), this::chooseTarget);
            if (stock) {
                var amount = addRenderableWidget(new EditBox(this.font, this.left + this.panelWidth - 68, this.top + 48, 68, 20, SettlementPolicyClient.text("amount")));
                amount.setMaxLength(4);
                amount.setTooltip(net.minecraft.client.gui.components.Tooltip.create(SettlementPolicyClient.text("amount")));
                amount.setFilter(value -> value.isEmpty() || value.chars().allMatch(Character::isDigit));
                amount.setValue(this.draft.amount);
                amount.setResponder(value -> { this.draft.amount = value; changed(); });
            }
            this.rows = addRenderableWidget(new Rows(this.top + 72, Math.max(34, this.panelHeight - 176)));
            int third = (this.panelWidth - 8) / 3;
            button("add_step", 0, this.panelHeight - 100, third, () -> this.minecraft.setScreen(new SettlementPolicyStepScreen(this, null)));
            button("edit_step", third + 4, this.panelHeight - 100, third, () -> this.minecraft.setScreen(new SettlementPolicyStepScreen(this, selectedStep())));
            button("remove_step", 2 * third + 8, this.panelHeight - 100, third, this::removeStep);
            button("connect", 0, this.panelHeight - 76, third, this::connect);
            button("disconnect", third + 4, this.panelHeight - 76, third, this::disconnect);
            button("preview", 2 * third + 8, this.panelHeight - 76, third, () -> { this.showPreview = true; preview(); });
            button("back", 0, this.panelHeight - 24, third, this::backToList);
            button("save", third + 4, this.panelHeight - 24, third, () -> submit(new SettlementPolicyRequestPayload.Save(configuration(), false)));
            button("activate", 2 * third + 8, this.panelHeight - 24, third, () -> submit(new SettlementPolicyRequestPayload.Save(configuration(), true)));
        } else {
            this.rows = addRenderableWidget(new Rows(this.top + 24, Math.max(30, this.panelHeight - 108)));
            int fourth = (this.panelWidth - 12) / 4;
            button("new", 0, this.panelHeight - 76, fourth, () -> {
                this.draft = new Draft(UUID.randomUUID());
                this.selected = null;
                this.editing = true;
                this.previewed = null;
                rebuildWidgets();
                chooseTarget();
            });
            button("edit", fourth + 4, this.panelHeight - 76, fourth, this::edit);
            button("pause", 2 * fourth + 8, this.panelHeight - 76, fourth, this::toggle);
            button("remove", 3 * fourth + 12, this.panelHeight - 76, fourth, this::remove);
            button("back", 0, this.panelHeight - 24, 84, this::onClose);
            button("refresh", this.panelWidth - 100, this.panelHeight - 24, 100, () -> refresh(null, null));
        }
        this.rows.refresh();
        update();
        if (!this.initialized) {
            this.initialized = true;
            refresh(null, this.draft == null ? null : this::changed);
        }
    }

    private Button button(String key, int x, int y, int width, Runnable action) {
        var button = addRenderableWidget(VillagerUi.buttonBuilder(SettlementPolicyClient.text(key), ignored -> action.run())
                .bounds(this.left + x, this.top + y, width, 20).build());
        this.buttons.put(key, button);
        return button;
    }

    void refresh(UUID area, Runnable ready) {
        this.area = area;
        this.afterView = ready;
        this.previewed = null;
        submit(new SettlementPolicyRequestPayload.View());
    }

    private void submit(SettlementPolicyRequestPayload.Command command) {
        var summary = selectedPolicy();
        UUID policy = this.editing && this.draft != null ? this.draft.id : this.selected;
        long revision = this.editing && this.draft != null ? this.draft.revision : summary == null ? 0 : summary.revision();
        var request = this.state.request(policy, revision, this.area, command, Util.getMillis());
        if (request == null) return;
        this.submitted = command;
        this.nextRequest = Util.getMillis() + 300;
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(request);
        update();
    }

    @Override public void acceptPolicy(SettlementPolicySnapshotPayload payload) {
        if (!this.state.accept(payload)) return;
        if (payload.feedback().equals("busy")) {
            if (this.submitted instanceof SettlementPolicyRequestPayload.Preview) this.previewAt = this.nextRequest;
            return;
        }
        if (payload.selected() != null && (this.loadSelected || payload.applied() && this.editing)) {
            var policy = SettlementSupplyPolicy.load(payload.selected(), this.minecraft.level.registryAccess(), () -> {});
            this.draft = new Draft(policy);
            this.editing = true;
            this.loadSelected = false;
            changed();
            if (this.minecraft.screen == this) rebuildWidgets();
        } else if (this.loadSelected) {
            this.loadSelected = false;
            this.editing = false;
            if (this.minecraft.screen == this) rebuildWidgets();
        }
        if (this.submitted instanceof SettlementPolicyRequestPayload.Preview) {
            this.previewed = this.previewSent;
            if (this.showPreview) {
                this.showPreview = false;
                this.minecraft.setScreen(new SettlementPolicyPreviewScreen(this, payload));
            }
        }
        if (!payload.feedback().isEmpty()) this.afterView = null;
        if (this.afterView != null) {
            var ready = this.afterView;
            this.afterView = null;
            ready.run();
        }
        if (this.rows != null) this.rows.refresh();
        update();
    }

    @Override public void tick() {
        this.state.expire(Util.getMillis());
        if (!this.state.waiting() && Util.getMillis() >= this.nextRequest) {
            if (this.afterView != null) submit(new SettlementPolicyRequestPayload.View());
            else if (this.editing && this.draft != null && Util.getMillis() >= this.previewAt) preview();
        }
        update();
    }

    private void preview() {
        var configuration = configuration();
        if (configuration == null || this.state.busy()) return;
        this.previewAt = Long.MAX_VALUE;
        this.previewSent = configuration;
        submit(new SettlementPolicyRequestPayload.Preview(configuration));
    }

    void changed() {
        this.previewed = null;
        this.previewAt = Util.getMillis() + 350;
        if (this.rows != null) this.rows.refresh();
        update();
    }

    SettlementSupplyPolicy.Configuration configuration() { return this.draft == null ? null : this.draft.configuration(); }

    void step(SettlementSupplyPolicy.Step step) {
        for (int index = 0; index < this.draft.steps.size(); index++) if (this.draft.steps.get(index).id().equals(step.id())) {
            this.draft.steps.set(index, step);
            this.selected = step.id();
            changed();
            return;
        }
        if (this.draft.steps.size() < SettlementSupplyPolicy.MAX_STEPS) this.draft.steps.add(step);
        this.selected = step.id();
        changed();
    }

    private SettlementSupplyPolicy.Step selectedStep() {
        return this.draft == null ? null : this.draft.steps.stream().filter(step -> step.id().equals(this.selected)).findFirst().orElse(null);
    }

    private void removeStep() {
        if (selectedStep() == null) return;
        this.draft.steps.removeIf(step -> step.id().equals(this.selected));
        this.draft.connections.removeIf(edge -> edge.producer().equals(this.selected) || this.selected.equals(edge.consumer()));
        this.selected = null;
        changed();
    }

    private void connect() {
        if (selectedStep() == null || snapshot() == null) return;
        UUID producer = this.selected;
        this.minecraft.setScreen(new SettlementPolicyChoiceScreen(this, SettlementPolicyClient.text("choose_requirement"), query ->
                snapshot().needs().stream().filter(need -> !producer.equals(need.step())).map(need ->
                        new SettlementPolicyChoiceScreen.Choice(Component.literal(need.name()), consumerName(need.step()),
                                need.samples().isEmpty() ? ItemStack.EMPTY : need.samples().getFirst(), () -> {
                            this.draft.connections.removeIf(edge -> java.util.Objects.equals(edge.consumer(), need.step()) && edge.requirement().equals(need.id()));
                            this.draft.connections.add(new SettlementSupplyPolicy.Connection(producer, need.step(), need.id()));
                            changed();
                        })).toList()));
    }

    private void disconnect() {
        UUID producer = this.selected;
        this.minecraft.setScreen(new SettlementPolicyChoiceScreen(this, SettlementPolicyClient.text("disconnect"), query ->
                this.draft.connections.stream().filter(edge -> edge.producer().equals(producer)).map(edge ->
                        new SettlementPolicyChoiceScreen.Choice(consumerName(edge.consumer()), SettlementPolicyClient.text("remove_connection"),
                                ItemStack.EMPTY, () -> { this.draft.connections.remove(edge); changed(); })).toList()));
    }

    Component consumerName(UUID step) {
        return step == null ? SettlementPolicyClient.text("target") : this.draft.steps.stream().filter(value -> value.id().equals(step))
                .map(this::stepName).findFirst().orElse(SettlementPolicyClient.text("missing_step"));
    }

    private Component stepName(SettlementSupplyPolicy.Step step) {
        var output = step.recipe() == null ? step.sample() : step.recipe().output(this.minecraft.level);
        String area = snapshot() == null ? "" : snapshot().worksites().stream().filter(value -> value.id().equals(step.area()))
                .map(SettlementPolicySnapshotPayload.Worksite::name).findFirst().orElse("");
        return SettlementPolicyClient.text("step_name", area, output.isEmpty() ? SettlementPolicyClient.text("recipe_unavailable") : output.getHoverName());
    }

    private void chooseTarget() {
        this.minecraft.setScreen(new SettlementPolicyChoiceScreen(this, SettlementPolicyClient.text("choose_target"), query -> List.of(
                new SettlementPolicyChoiceScreen.Choice(SettlementPolicyClient.text("target_stock"), SettlementPolicyClient.text("target_stock_help"),
                        ItemStack.EMPTY, () -> chooseTargetArea(true)),
                new SettlementPolicyChoiceScreen.Choice(SettlementPolicyClient.text("target_consumer"), SettlementPolicyClient.text("target_consumer_help"),
                        ItemStack.EMPTY, () -> chooseTargetArea(false)),
                new SettlementPolicyChoiceScreen.Choice(SettlementPolicyClient.text("target_food"), SettlementPolicyClient.text("target_food_help"),
                        ItemStack.EMPTY, () -> target(new SettlementSupplyPolicy.Target(SettlementSupplyPolicy.TargetKind.FOOD, town(), null, null, ItemStack.EMPTY, 0))))));
    }

    private void chooseTargetArea(boolean stock) {
        if (snapshot() == null) return;
        this.minecraft.setScreen(new SettlementPolicyChoiceScreen(this, SettlementPolicyClient.text("choose_worksite"), query -> {
            var choices = new ArrayList<SettlementPolicyChoiceScreen.Choice>();
            choices.add(new SettlementPolicyChoiceScreen.Choice(SettlementPolicyClient.text("town_storage"), Component.empty(), ItemStack.EMPTY,
                    () -> refresh(null, () -> chooseTargetValue(stock))));
            for (var worksite : snapshot().worksites()) choices.add(new SettlementPolicyChoiceScreen.Choice(Component.literal(worksite.name()),
                    Component.literal(worksite.position().toShortString()), ItemStack.EMPTY, () -> refresh(worksite.id(), () -> chooseTargetValue(stock))));
            return choices;
        }));
    }

    private void chooseTargetValue(boolean stock) {
        if (snapshot() == null) return;
        this.minecraft.setScreen(new SettlementPolicyChoiceScreen(this, SettlementPolicyClient.text(stock ? "choose_storage" : "choose_consumer"), query -> {
            if (!stock) return snapshot().consumers().stream().map(consumer -> new SettlementPolicyChoiceScreen.Choice(
                    Component.literal(consumer.name()), SettlementPolicyClient.text("target_consumer"), ItemStack.EMPTY,
                    () -> target(new SettlementSupplyPolicy.Target(consumer.kind(), consumer.id(), null, null, ItemStack.EMPTY, 0)))).toList();
            return snapshot().storages().stream().map(storage -> new SettlementPolicyChoiceScreen.Choice(Component.literal(storage.name()),
                    SettlementPolicyClient.text(storage.known() ? "observed" : "needs_observation"), ItemStack.EMPTY, () ->
                    this.minecraft.setScreen(SettlementPolicyChoiceScreen.items(this, this.draft.target == null ? List.of() : List.of(this.draft.target.sample()),
                            sample -> target(new SettlementSupplyPolicy.Target(SettlementSupplyPolicy.TargetKind.STOCK, null, null,
                                    storage.position(), sample, 16)))))).toList();
        }));
    }

    private void target(SettlementSupplyPolicy.Target target) {
        this.draft.target = target;
        this.draft.amount = Integer.toString(target.amount());
        this.draft.connections.removeIf(edge -> edge.consumer() == null);
        changed();
        rebuildWidgets();
    }

    private void edit() {
        if (this.selected == null) return;
        this.loadSelected = true;
        this.editing = false;
        refresh(null, null);
    }

    private SettlementPolicySnapshotPayload.Summary selectedPolicy() {
        return snapshot() == null || this.selected == null ? null : snapshot().policies().stream()
                .filter(policy -> policy.id().equals(this.selected)).findFirst().orElse(null);
    }

    private void toggle() {
        var policy = selectedPolicy();
        if (policy != null) submit(new SettlementPolicyRequestPayload.Change(policy.state() == SettlementSupplyPolicy.State.ACTIVE
                ? SettlementSupplyPolicy.State.PAUSED : SettlementSupplyPolicy.State.ACTIVE));
    }

    private void remove() {
        if (selectedPolicy() == null) return;
        this.minecraft.setScreen(new SettlementConfirmScreen(this, SettlementPolicyClient.text("remove"), SettlementPolicyClient.text("remove_help"),
                () -> submit(new SettlementPolicyRequestPayload.Change(SettlementSupplyPolicy.State.REMOVING))));
    }

    private void backToList() {
        this.selected = this.draft == null ? null : this.draft.id;
        this.draft = null;
        this.editing = false;
        this.previewAt = Long.MAX_VALUE;
        this.previewed = null;
        this.afterView = null;
        rebuildWidgets();
        refresh(null, null);
    }

    private void update() {
        if (this.buttons.isEmpty() || (this.editing && this.draft != null) != this.buttons.containsKey("priority")) return;
        boolean ready = !this.state.waiting() && Util.getMillis() >= this.nextRequest;
        for (var button : this.buttons.values()) button.active = !this.state.busy();
        for (var child : children()) if (child instanceof EditBox field) field.active = !this.state.busy();
        if (this.editing && this.draft != null) {
            this.buttons.get("priority").setMessage(SettlementPolicyClient.text("priority_value", this.draft.priority));
            this.buttons.get("target").setMessage(this.draft.target == null ? SettlementPolicyClient.text("choose_target")
                    : SettlementPolicyClient.text("target_value", targetName()));
            this.buttons.get("add_step").active &= this.draft.steps.size() < SettlementSupplyPolicy.MAX_STEPS;
            this.buttons.get("edit_step").active &= selectedStep() != null;
            this.buttons.get("remove_step").active &= selectedStep() != null;
            this.buttons.get("connect").active &= selectedStep() != null && snapshot() != null && !snapshot().needs().isEmpty()
                    && this.draft.connections.size() < SettlementSupplyPolicy.MAX_CONNECTIONS;
            this.buttons.get("disconnect").active &= this.draft.connections.stream().anyMatch(edge -> edge.producer().equals(this.selected));
            this.buttons.get("save").active = ready && this.state.editable() && configuration() != null;
            this.buttons.get("activate").active = ready && this.state.editable() && configuration() != null && configuration().equals(this.previewed)
                    && snapshot() != null && snapshot().blocker().isEmpty();
            this.buttons.get("preview").active &= ready && configuration() != null;
        } else {
            var selected = selectedPolicy();
            this.buttons.get("new").active &= snapshot() != null && snapshot().policies().size() < SettlementSupplyPolicy.MAX_POLICIES;
            this.buttons.get("edit").active &= ready && selected != null;
            this.buttons.get("pause").active = ready && this.state.editable() && selected != null && selected.state() != SettlementSupplyPolicy.State.REMOVING;
            this.buttons.get("pause").setMessage(SettlementPolicyClient.text(selected != null && selected.state() == SettlementSupplyPolicy.State.ACTIVE ? "pause" : "activate"));
            this.buttons.get("remove").active = this.buttons.get("pause").active;
            this.buttons.get("refresh").active &= ready;
        }
    }

    private Component targetName() {
        var target = this.draft.target;
        if (target.kind() == SettlementSupplyPolicy.TargetKind.STOCK) return SettlementPolicyClient.text("stock_target",
                target.sample().getHoverName(), target.storage().toShortString());
        return snapshot() == null ? SettlementPolicyClient.text("target_consumer") : snapshot().consumers().stream()
                .filter(value -> value.id().equals(target.consumer())).map(value -> Component.literal(value.name()))
                .map(Component.class::cast).findFirst().orElse(SettlementPolicyClient.text("target_consumer"));
    }

    @Override public void onClose() { if (this.editing) backToList(); else this.minecraft.setScreen(this.previous); }
    @Override public boolean isPauseScreen() { return false; }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.left + this.panelWidth / 2, this.top + 2, 0xFFFFFF);
        String message = this.state.feedback();
        UUID id = this.draft == null ? this.selected : this.draft.id;
        if (message.isEmpty() && snapshot() != null) message = snapshot().policies().stream().filter(policy -> policy.id().equals(id))
                .map(SettlementPolicySnapshotPayload.Summary::blocker).findFirst().orElse("");
        var feedback = SettlementPolicyClient.blocker(message);
        graphics.drawString(this.font, this.font.plainSubstrByWidth(feedback.getString(), this.panelWidth),
                this.left, this.top + this.panelHeight - 44, 0xCCCCCC);
        if (mouseX >= this.left && mouseX <= this.left + this.panelWidth
                && mouseY >= this.top + this.panelHeight - 46 && mouseY < this.top + this.panelHeight - 30)
            graphics.renderTooltip(this.font, feedback, mouseX, mouseY);
    }

    private final class Rows extends VillagerSelectionList<Row> {
        Rows(int y, int height) { super(SettlementPoliciesScreen.this.minecraft, panelWidth, height, y, 34); setX(left); }
        void refresh() {
            clearEntries();
            if (editing && draft != null) {
                for (var step : draft.steps) {
                    var plan = snapshot() == null ? null : snapshot().production().stream().filter(value -> value.id().equals(step.id())).findFirst().orElse(null);
                    long connections = draft.connections.stream().filter(edge -> edge.producer().equals(step.id())).count();
                    addEntry(new Row(step.id(), stepName(step), plan == null ? SettlementPolicyClient.text("connections", connections)
                            : SettlementPolicyClient.text("step_quantities", plan.proposed(), plan.requested(), plan.retained(), connections)));
                }
            } else if (snapshot() != null) for (var policy : snapshot().policies()) addEntry(new Row(policy.id(), Component.literal(policy.name()),
                    SettlementPolicyClient.text("state." + policy.state().name().toLowerCase(java.util.Locale.ROOT)).copy()
                            .append(policy.blocker().isEmpty() ? Component.empty() : Component.literal(" / ").append(SettlementPolicyClient.blocker(policy.blocker())))));
            setSelected(children().stream().filter(row -> row.id.equals(selected)).findFirst().orElse(null));
        }
        @Override public int getRowWidth() { return getWidth() - 12; }
        @Override protected int getScrollbarPosition() { return getRight() - 6; }
    }

    private final class Row extends ObjectSelectionList.Entry<Row> {
        final UUID id;
        final Component name;
        final Component detail;
        Row(UUID id, Component name, Component detail) { this.id = id; this.name = name; this.detail = detail; }
        @Override public void render(GuiGraphics graphics, int index, int top, int left, int width, int height,
                                     int mouseX, int mouseY, boolean hovered, float partialTick) {
            graphics.drawString(font, font.plainSubstrByWidth(this.name.getString(), width - 6), left + 3, top + 4, 0xFFFFFF);
            graphics.drawString(font, font.plainSubstrByWidth(this.detail.getString(), width - 6), left + 3, top + 18, 0xAAAAAA);
        }
        @Override public boolean mouseClicked(double x, double y, int button) {
            if (button != 0) return false;
            selected = this.id;
            rows.setSelected(this);
            update();
            return true;
        }
        @Override public Component getNarration() { return this.name.copy().append(" ").append(this.detail); }
    }

    private static final class Draft {
        final UUID id;
        long revision;
        String name = SettlementPolicyClient.text("new_policy").getString();
        int priority = 5;
        SettlementSupplyPolicy.Target target;
        String amount = "16";
        final List<SettlementSupplyPolicy.Step> steps = new ArrayList<>();
        final List<SettlementSupplyPolicy.Connection> connections = new ArrayList<>();
        Draft(UUID id) { this.id = id; }
        Draft(SettlementSupplyPolicy policy) {
            this.id = policy.id();
            this.revision = policy.revision();
            this.name = policy.configuration().name();
            this.priority = policy.configuration().priority();
            this.target = policy.configuration().target();
            this.amount = Integer.toString(this.target.amount());
            this.steps.addAll(policy.configuration().steps());
            this.connections.addAll(policy.configuration().connections());
        }
        SettlementSupplyPolicy.Configuration configuration() {
            if (this.target == null || this.name.isBlank()) return null;
            var target = this.target;
            if (target.kind() == SettlementSupplyPolicy.TargetKind.STOCK) {
                int count;
                try { count = Integer.parseInt(this.amount); } catch (NumberFormatException exception) { return null; }
                if (count < 1 || count > 4096) return null;
                target = new SettlementSupplyPolicy.Target(target.kind(), null, null, target.storage(), target.sample(), count);
            }
            return new SettlementSupplyPolicy.Configuration(this.name, target, this.priority, this.steps, this.connections);
        }
    }
}
