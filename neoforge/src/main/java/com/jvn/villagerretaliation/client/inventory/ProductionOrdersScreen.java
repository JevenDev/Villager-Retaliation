package com.jvn.villagerretaliation.client.inventory;

import com.jvn.villagerretaliation.client.ui.VillagerSelectionList;
import com.jvn.villagerretaliation.client.ui.VillagerUi;
import com.jvn.villagerretaliation.client.ui.VillagerWindowScreen;
import com.jvn.villagerretaliation.interaction.work.production.HiredProductionOrder;
import com.jvn.villagerretaliation.interaction.work.production.HiredProductionService;
import com.jvn.villagerretaliation.network.ProductionRequestPayload;
import com.jvn.villagerretaliation.network.ProductionSnapshotPayload;
import com.jvn.villagerretaliation.network.ProductionTarget;
import java.util.UUID;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public final class ProductionOrdersScreen extends VillagerWindowScreen implements ProductionClient.Receiver {
    private final Screen previous;
    private final Runnable close;
    private final ProductionClientState state;
    private UUID selected;
    private Orders list;
    private Button add;
    private Button up;
    private Button down;
    private Button pause;
    private Button cancel;
    private Button remove;
    private Button refresh;

    public ProductionOrdersScreen(Screen previous, ProductionTarget target) {
        this(previous, target, () -> net.minecraft.client.Minecraft.getInstance().setScreen(previous));
    }

    public ProductionOrdersScreen(Screen previous, ProductionTarget target, Runnable close) {
        this(previous, target, close, null);
    }

    public ProductionOrdersScreen(Screen previous, ProductionTarget target, Runnable close, UUID selected) {
        super(Component.literal("Production orders"));
        this.previous = previous;
        this.close = close;
        this.state = new ProductionClientState(target);
        this.selected = selected;
    }

    public Screen previous() { return this.previous; }
    public ProductionClientState state() { return this.state; }

    @Override
    protected void init() {
        initWindow(360, 330);
        this.list = addRenderableWidget(new Orders());
        this.list.refresh();
        int controls = this.top + this.panelHeight - 64;
        this.up = button("Up", this.left, controls, 30, () -> move(-1));
        this.down = button("Down", this.left + 34, controls, 42, () -> move(1));
        int width = (this.panelWidth - 90) / 3;
        this.pause = button("Pause", this.left + 82, controls, width, () -> edit(selectedOrder().paused() ? HiredProductionService.Edit.RESUME : HiredProductionService.Edit.PAUSE, 0));
        this.cancel = button("Cancel", this.left + 86 + width, controls, width, () -> edit(HiredProductionService.Edit.CANCEL, 0));
        this.remove = button("Remove", this.left + 90 + width * 2, controls, width, () -> edit(HiredProductionService.Edit.REMOVE, 0));
        button("Back", this.left, this.top + this.panelHeight - 28, 70, this::onClose);
        this.refresh = button("Refresh", this.left + 74, this.top + this.panelHeight - 28, 78, () -> submit(new ProductionRequestPayload.View()));
        this.add = button("Add order", this.left + 160, this.top + this.panelHeight - 28, this.panelWidth - 160,
                () -> this.minecraft.setScreen(new ProductionRecipeScreen(this)));
        updateActions();
        poll();
    }

    private Button button(String title, int x, int y, int width, Runnable action) {
        return addRenderableWidget(VillagerUi.buttonBuilder(Component.literal(title), ignored -> action.run()).bounds(x, y, width, 20).build());
    }

    public boolean submit(ProductionRequestPayload.Command command) {
        var request = this.state.request(command, Util.getMillis());
        if (request == null) return false;
        PacketDistributor.sendToServer(request);
        updateActions();
        return true;
    }

    private void poll() {
        var request = this.state.poll(Util.getMillis());
        if (request != null) PacketDistributor.sendToServer(request);
    }

    @Override
    public void tick() {
        poll();
        updateActions();
    }

    @Override
    public void acceptProduction(ProductionSnapshotPayload payload) {
        acceptSnapshot(payload);
    }

    boolean acceptSnapshot(ProductionSnapshotPayload payload) {
        if (!this.state.accept(payload, this.minecraft.level.registryAccess())) return false;
        if (this.list != null) this.list.refresh();
        updateActions();
        return true;
    }

    private HiredProductionOrder selectedOrder() {
        return this.state.queue() == null || this.selected == null ? null : this.state.queue().order(this.selected);
    }

    private void edit(HiredProductionService.Edit edit, int index) {
        if (selectedOrder() != null) submit(new ProductionRequestPayload.Edit(this.selected, edit, index));
    }

    private void move(int direction) {
        var order = selectedOrder();
        if (order != null) edit(HiredProductionService.Edit.MOVE, this.state.queue().orders().indexOf(order) + direction);
    }

    private void updateActions() {
        if (this.add == null) return;
        var order = selectedOrder();
        boolean editable = this.state.editable();
        var queue = this.state.queue();
        int index = order == null ? -1 : queue.orders().indexOf(order);
        this.up.active = editable && index > 0;
        this.down.active = editable && index >= 0 && index + 1 < queue.orders().size();
        this.pause.active = editable && order != null && !order.cancelled() && !order.complete();
        this.pause.setMessage(Component.literal(order != null && order.paused() ? "Resume" : "Pause"));
        this.cancel.active = editable && order != null && !order.cancelled() && !order.complete();
        this.remove.active = editable && order != null && queue.allocations().stream().noneMatch(allocation -> allocation.order().equals(order.id()) && allocation.started());
        this.refresh.active = !this.state.busy();
        this.add.active = editable && (queue == null || queue.orders().size() < 32);
    }

    @Override
    public void onClose() {
        this.close.run();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        var snapshot = this.state.snapshot();
        String title = snapshot == null ? this.title.getString() : snapshot.title() + " / Production";
        graphics.drawCenteredString(this.font, this.font.plainSubstrByWidth(title, this.panelWidth), this.left + this.panelWidth / 2, this.top + 2, 0xFFFFFF);
        graphics.drawString(this.font, this.state.target().town() == null ? "Personal hire" : "Shared worksite queue", this.left, this.top + 24, 0xBBBBBB);
        if (this.list.children().isEmpty()) {
            String empty = snapshot == null ? "Loading orders..." : snapshot.blocker().isEmpty() ? "No orders yet. Add a recipe to begin." : "Production is unavailable.";
            graphics.drawCenteredString(this.font, empty, this.left + this.panelWidth / 2, this.top + 60, 0xBBBBBB);
        }
        String feedback = this.state.feedback();
        if (feedback.isEmpty()) feedback = snapshot != null && snapshot.legacyCount() > 0
                ? "Adding an order will preserve " + snapshot.legacyCount() + " separate legacy brewing orders and their active batches."
                : "The first runnable order is processed. Started batches finish after pause or cancellation.";
        graphics.drawWordWrap(this.font, Component.literal(feedback), this.left, this.top + this.panelHeight - 100, this.panelWidth, 0xD0D0D0);
        var hovered = this.list.hovered();
        if (hovered != null) graphics.renderComponentTooltip(this.font, hovered.details, mouseX, mouseY);
    }

    static String mode(HiredProductionOrder.Mode mode) {
        return switch (mode) {
            case BATCH -> "Batch";
            case CONTINUOUS -> "Continuous";
            case MAINTAIN_STOCK -> "Maintain stock";
        };
    }

    static String status(HiredProductionOrder order) {
        if (order.cancelled()) return "Cancelled";
        if (order.complete()) return "Complete";
        if (order.paused()) return "Paused";
        return order.blocker().isEmpty() ? "Ready" : order.blocker().replace('_', ' ');
    }

    private final class Orders extends VillagerSelectionList<Row> {
        private Orders() {
            super(ProductionOrdersScreen.this.minecraft, panelWidth, Math.max(32, panelHeight - 152), top + 44, 42);
            setX(left);
        }

        private void refresh() {
            double scroll = getScrollAmount();
            clearEntries();
            if (state.queue() != null) state.queue().orders().forEach(order -> addEntry(new Row(order)));
            else if (state.snapshot() != null) state.snapshot().legacy().forEach(legacy -> addEntry(new Row(legacy)));
            var selection = children().stream().filter(row -> row.id.equals(selected)).findFirst().orElse(null);
            if (selection == null && !children().isEmpty()) selection = children().getFirst();
            if (state.snapshot() != null) setSelected(selection);
            else super.setSelected(null);
            setScrollAmount(scroll);
        }

        private Row hovered() { return getHovered(); }

        @Override
        public void setSelected(Row row) {
            super.setSelected(row);
            selected = row == null ? null : row.id;
            updateActions();
        }

        @Override
        public int getRowWidth() { return getWidth() - 12; }

        @Override
        protected int getScrollbarPosition() { return getRight() - 6; }
    }

    private final class Row extends ObjectSelectionList.Entry<Row> {
        private final UUID id;
        private final ItemStack output;
        private final String quantity;
        private final String status;
        private final java.util.List<Component> details;

        private Row(HiredProductionOrder order) {
            this.id = order.id();
            this.output = order.output();
            int pending = state.queue().pending(order.id());
            this.quantity = mode(order.mode()) + (order.mode() == HiredProductionOrder.Mode.CONTINUOUS ? "" : " " + order.amount())
                    + " / Made " + order.produced() + " / Pending " + pending;
            long stock = state.snapshot().stock().getOrDefault(order.id(), -1L);
            this.status = ProductionOrdersScreen.status(order) + (order.mode() == HiredProductionOrder.Mode.MAINTAIN_STOCK
                    ? " / Stock and deliveries: " + (stock < 0L ? "unknown" : Long.toString(stock)) : "");
            var details = new java.util.ArrayList<Component>();
            details.add(this.output.getHoverName());
            details.add(Component.literal(order.recipe().recipe() == null ? order.recipe().potion().toString() : order.recipe().recipe().toString()));
            order.recipe().alternatives().entrySet().stream().sorted(java.util.Map.Entry.comparingByKey()).forEach(entry ->
                    details.add(Component.literal("Ingredient " + (entry.getKey() + 1) + ": ")
                            .append(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(entry.getValue()).getDescription())));
            details.add(Component.literal(this.quantity));
            details.add(Component.literal(this.status));
            details.add(Component.literal("Output storage: " + (order.storage() == null ? "automatic" : order.storage().toShortString())));
            for (var need : state.snapshot().requirements()) if (need.order().equals(order.id())) {
                details.add(Component.literal(need.name() + ": required " + need.required() + " / supplied " + need.supplied()
                        + " / outstanding " + (need.missing() < 0 ? "unknown" : need.missing()) + " " + need.unit().name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ')));
            }
            this.details = java.util.List.copyOf(details);
        }

        private Row(ProductionSnapshotPayload.Legacy legacy) {
            this.id = legacy.worker();
            this.output = legacy.recipe().output(minecraft.level);
            this.quantity = "Legacy / " + (legacy.continuous() ? "Continuous" : legacy.amount() + " remaining");
            this.status = legacy.activeBottles() < 0 ? "Active stand needs observation" : legacy.activeBottles() + " active bottles / preserved during conversion";
            this.details = java.util.List.of(this.output.getHoverName(), Component.literal(this.quantity), Component.literal(this.status));
        }

        @Override
        public void render(GuiGraphics graphics, int index, int top, int rowLeft, int rowWidth, int rowHeight,
                           int mouseX, int mouseY, boolean hovered, float partialTick) {
            graphics.renderItem(this.output, rowLeft + 3, top + 4);
            graphics.drawString(font, font.plainSubstrByWidth(this.output.getHoverName().getString(), rowWidth - 28), rowLeft + 24, top + 3, 0xFFFFFF);
            graphics.drawString(font, font.plainSubstrByWidth(this.quantity, rowWidth - 28), rowLeft + 24, top + 15, 0xD0D0D0);
            graphics.drawString(font, font.plainSubstrByWidth(this.status, rowWidth - 28), rowLeft + 24, top + 27, 0xAAAAAA);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button != 0) return false;
            list.setSelected(this);
            return true;
        }

        @Override
        public Component getNarration() {
            return Component.literal(this.output.getHoverName().getString() + ", " + this.quantity + ", " + this.status);
        }
    }
}
