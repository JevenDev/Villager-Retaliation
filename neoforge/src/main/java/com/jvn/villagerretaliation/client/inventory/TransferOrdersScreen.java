package com.jvn.villagerretaliation.client.inventory;

import com.jvn.villagerretaliation.interaction.work.courier.HiredTransferQueue;
import com.jvn.villagerretaliation.network.TransferRequestPayload;
import com.jvn.villagerretaliation.network.TransferSnapshotPayload;
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

public final class TransferOrdersScreen extends Screen implements TransferClient.Receiver {
    private final Screen previous;
    private final Runnable close;
    private final TransferClientState state;
    private UUID selected;
    private Orders list;
    private Button add;
    private Button up;
    private Button down;
    private Button pause;
    private Button cancel;
    private Button remove;
    private Button refresh;
    private int left;
    private int panelWidth;

    public TransferOrdersScreen(Screen previous, ProductionTarget target) {
        this(previous, target, () -> net.minecraft.client.Minecraft.getInstance().setScreen(previous));
    }

    public TransferOrdersScreen(Screen previous, ProductionTarget target, Runnable close) {
        super(Component.literal("Transfer orders"));
        this.previous = previous;
        this.close = close;
        this.state = new TransferClientState(target);
    }

    public Screen previous() { return this.previous; }
    public TransferClientState state() { return this.state; }

    @Override
    protected void init() {
        this.panelWidth = Math.min(580, this.width - 24);
        this.left = (this.width - this.panelWidth) / 2;
        this.list = addRenderableWidget(new Orders());
        this.list.refresh();
        int controls = this.height - 64;
        this.up = button("Up", this.left, controls, 30, () -> move(-1));
        this.down = button("Down", this.left + 34, controls, 42, () -> move(1));
        int width = (this.panelWidth - 90) / 3;
        this.pause = button("Pause", this.left + 82, controls, width, () -> edit(selectedOrder().paused() ? HiredTransferQueue.Edit.RESUME : HiredTransferQueue.Edit.PAUSE, 0));
        this.cancel = button("Cancel", this.left + 86 + width, controls, width, () -> edit(HiredTransferQueue.Edit.CANCEL, 0));
        this.remove = button("Remove", this.left + 90 + width * 2, controls, width, () -> edit(HiredTransferQueue.Edit.REMOVE, 0));
        button("Back", this.left, this.height - 28, 70, this::onClose);
        this.refresh = button("Refresh", this.left + 74, this.height - 28, 78, () -> submit(new TransferRequestPayload.View()));
        this.add = button("Add order", this.left + this.panelWidth - 132, this.height - 28, 132,
                () -> this.minecraft.setScreen(new TransferEditorScreen(this)));
        updateActions();
        poll();
    }

    private Button button(String title, int x, int y, int width, Runnable action) {
        return addRenderableWidget(Button.builder(Component.literal(title), ignored -> action.run()).bounds(x, y, width, 20).build());
    }

    public boolean submit(TransferRequestPayload.Command command) {
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
    public void acceptTransfer(TransferSnapshotPayload payload) {
        acceptSnapshot(payload);
    }

    boolean acceptSnapshot(TransferSnapshotPayload payload) {
        if (!this.state.accept(payload, this.minecraft.level.registryAccess())) return false;
        if (this.list != null) this.list.refresh();
        updateActions();
        return true;
    }

    private HiredTransferQueue.Order selectedOrder() {
        return this.state.queue() == null || this.selected == null ? null : this.state.queue().order(this.selected);
    }

    private void edit(HiredTransferQueue.Edit edit, int index) {
        if (selectedOrder() != null) submit(new TransferRequestPayload.Edit(this.selected, edit, index));
    }

    private void move(int direction) {
        var order = selectedOrder();
        if (order != null) edit(HiredTransferQueue.Edit.MOVE, this.state.queue().orders().indexOf(order) + direction);
    }

    private void updateActions() {
        if (this.add == null) return;
        var order = selectedOrder();
        boolean editable = this.state.editable();
        var queue = this.state.queue();
        int index = order == null ? -1 : queue.orders().indexOf(order);
        this.up.active = editable && index > 0;
        this.down.active = editable && index >= 0 && index + 1 < queue.orders().size();
        this.pause.active = editable && order != null && !order.cancelled() && order.delivered() < order.amount();
        this.pause.setMessage(Component.literal(order != null && order.paused() ? "Resume" : "Pause"));
        this.cancel.active = editable && order != null && !order.cancelled() && order.delivered() < order.amount();
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
        String title = snapshot == null ? this.title.getString() : snapshot.title() + " / Transfers";
        graphics.drawCenteredString(this.font, this.font.plainSubstrByWidth(title, this.panelWidth), this.width / 2, 14, 0xFFFFFF);
        graphics.drawString(this.font, this.state.target().town() == null ? "Personal hire" : "Shared worksite queue", this.left, 34, 0xBBBBBB);
        if (this.list.children().isEmpty()) {
            String empty = snapshot == null ? "Loading orders..." : snapshot.blocker().isEmpty() ? "No transfers yet. Choose storage and an item to begin." : "Transfers are unavailable.";
            graphics.drawCenteredString(this.font, empty, this.width / 2, 70, 0xBBBBBB);
        }
        String feedback = this.state.feedback();
        if (feedback.isEmpty()) feedback = "The first runnable transfer starts. Collected cargo is delivered after pause or cancellation.";
        graphics.drawWordWrap(this.font, Component.literal(feedback), this.left, this.height - 100, this.panelWidth, 0xD0D0D0);
        var hovered = this.list.hovered();
        if (hovered != null) graphics.renderComponentTooltip(this.font, hovered.details, mouseX, mouseY);
    }

    static String status(HiredTransferQueue.Order order) {
        if (order.cancelled()) return "Cancelled";
        if (order.delivered() >= order.amount()) return "Complete";
        if (order.paused()) return "Paused";
        return order.blocker().isEmpty() ? "Ready" : order.blocker().replace('_', ' ');
    }

    private final class Orders extends ObjectSelectionList<Row> {
        private Orders() {
            super(TransferOrdersScreen.this.minecraft, panelWidth, Math.max(32, TransferOrdersScreen.this.height - 160), 52, 42);
            setX(left);
        }

        private void refresh() {
            double scroll = getScrollAmount();
            clearEntries();
            if (state.queue() != null) state.queue().orders().forEach(order -> addEntry(new Row(order)));
            var selection = children().stream().filter(row -> row.id.equals(selected)).findFirst().orElse(null);
            if (selection == null && !children().isEmpty()) selection = children().getFirst();
            setSelected(selection);
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

        private Row(HiredTransferQueue.Order order) {
            this.id = order.id();
            this.output = order.sample();
            this.quantity = "Deliver " + order.amount() + " / Delivered " + order.delivered() + " / In transit " + order.pending();
            this.status = TransferOrdersScreen.status(order);
            this.details = java.util.List.of(this.output.getHoverName(), Component.literal(this.quantity),
                    Component.literal(this.status), Component.literal("Source: " + order.source().position().toShortString()),
                    Component.literal("Destination: " + order.destination().position().toShortString()));
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
