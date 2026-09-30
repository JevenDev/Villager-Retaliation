package com.jvn.villagerretaliation.client.inventory;

import com.jvn.villagerretaliation.client.ui.VillagerUi;
import com.jvn.villagerretaliation.client.ui.VillagerWindowScreen;
import com.jvn.villagerretaliation.interaction.work.courier.HiredTransferQueue;
import com.jvn.villagerretaliation.network.TransferRequestPayload;
import com.jvn.villagerretaliation.network.TransferSnapshotPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public final class TransferEditorScreen extends VillagerWindowScreen implements TransferClient.Receiver {
    private final TransferOrdersScreen previous;
    private HiredTransferQueue.Endpoint source;
    private HiredTransferQueue.Endpoint destination;
    private ItemStack sample = ItemStack.EMPTY;
    private String quantity = "16";
    private HiredTransferQueue.Mode mode = HiredTransferQueue.Mode.FINITE;
    private Button modeButton;
    private boolean saving;
    private Button sourceButton;
    private Button destinationButton;
    private Button itemButton;
    private Button save;

    public TransferEditorScreen(TransferOrdersScreen previous) {
        super(Component.literal("Add transfer"));
        this.previous = previous;
    }

    public TransferEditorScreen(TransferOrdersScreen previous, TransferRequestPayload.Append draft) {
        this(previous);
        this.source = draft.source();
        this.destination = draft.destination();
        this.sample = draft.sample();
        this.quantity = Integer.toString(draft.amount());
        this.mode = draft.mode();
    }

    public TransferOrdersScreen previous() { return this.previous; }
    HiredTransferQueue.Endpoint source() { return this.source; }

    @Override
    protected void init() {
        initWindow(340, 252);
        this.sourceButton = button("Choose source", this.top + 28, this.panelWidth,
                () -> this.minecraft.setScreen(new TransferChoiceScreen(this, TransferChoiceScreen.Kind.SOURCE)));
        this.destinationButton = button("Choose destination", this.top + 62, this.panelWidth,
                () -> this.minecraft.setScreen(new TransferChoiceScreen(this, TransferChoiceScreen.Kind.DESTINATION)));
        this.itemButton = button("Choose exact item", this.top + 96, this.panelWidth,
                () -> this.minecraft.setScreen(new TransferChoiceScreen(this, TransferChoiceScreen.Kind.ITEM)));
        var amount = addRenderableWidget(new EditBox(this.font, this.left, this.top + 130, 100, 20, Component.literal("Quantity")));
        amount.setMaxLength(4);
        amount.setFilter(value -> value.matches("[0-9]*"));
        amount.setValue(this.quantity);
        amount.setResponder(value -> { this.quantity = value; update(); });
        this.modeButton = addRenderableWidget(VillagerUi.buttonBuilder(Component.literal(modeLabel()), ignored -> {
            this.mode = this.mode == HiredTransferQueue.Mode.FINITE ? HiredTransferQueue.Mode.MAINTAIN_STOCK : HiredTransferQueue.Mode.FINITE;
            update();
        }).bounds(this.left + 110, this.top + 130, this.panelWidth - 110, 20).build());
        button("Back", this.top + this.panelHeight - 28, 78, this::onClose);
        this.save = addRenderableWidget(VillagerUi.buttonBuilder(Component.literal("Add transfer"), ignored -> submit())
                .bounds(this.left + this.panelWidth - 116, this.top + this.panelHeight - 28, 116, 20).build());
        update();
    }

    private Button button(String label, int y, int width, Runnable action) {
        return addRenderableWidget(VillagerUi.buttonBuilder(Component.literal(label), ignored -> action.run()).bounds(this.left, y, width, 20).build());
    }

    void storage(HiredTransferQueue.Endpoint endpoint, boolean source) {
        if (source) {
            this.source = endpoint;
            if (endpoint.equals(this.destination)) this.destination = null;
        } else this.destination = endpoint;
        update();
    }

    void item(ItemStack sample) { this.sample = sample.copyWithCount(1); update(); }

    private int amount() {
        try { return Integer.parseInt(this.quantity); }
        catch (NumberFormatException invalid) { return 0; }
    }

    private boolean permits(HiredTransferQueue.Endpoint endpoint, boolean source) {
        var snapshot = this.previous.state().snapshot();
        return endpoint != null && snapshot != null && snapshot.storages().stream().anyMatch(storage ->
                storage.endpoint().equals(endpoint) && (source ? storage.source() : storage.destination()));
    }

    private String label(HiredTransferQueue.Endpoint endpoint, String empty) {
        if (endpoint == null) return empty;
        var snapshot = this.previous.state().snapshot();
        String name = snapshot == null ? endpoint.position().toShortString() : snapshot.storages().stream()
                .filter(storage -> storage.endpoint().equals(endpoint)).map(TransferSnapshotPayload.Storage::name).findFirst()
                .orElse(endpoint.position().toShortString() + " / unavailable");
        return this.font.plainSubstrByWidth(name, this.panelWidth - 12);
    }

    private void update() {
        if (this.save == null) return;
        boolean editable = this.previous.state().editable();
        this.sourceButton.setMessage(Component.literal(label(this.source, "Choose source")));
        this.destinationButton.setMessage(Component.literal(label(this.destination, "Choose destination")));
        this.itemButton.setMessage(Component.literal(this.sample.isEmpty() ? "Choose exact item"
                : this.font.plainSubstrByWidth(this.sample.getHoverName().getString(), this.panelWidth - 12)));
        this.modeButton.setMessage(Component.literal(modeLabel()));
        this.modeButton.active = editable;
        this.sourceButton.active = editable;
        this.destinationButton.active = editable;
        this.itemButton.active = editable;
        this.save.active = editable && permits(this.source, true) && permits(this.destination, false)
                && !this.source.position().equals(this.destination.position()) && !this.sample.isEmpty()
                && amount() >= 1 && amount() <= HiredTransferQueue.MAX_AMOUNT;
    }

    private String modeLabel() { return this.mode == HiredTransferQueue.Mode.FINITE ? "Deliver once" : "Maintain destination stock"; }

    private void submit() {
        if (!this.save.active) return;
        this.saving = this.previous.submit(new TransferRequestPayload.Append(this.source, this.destination, this.sample, amount(), this.mode));
        update();
    }

    @Override
    public void acceptTransfer(TransferSnapshotPayload payload) {
        if (!this.previous.acceptSnapshot(payload)) return;
        boolean completed = this.saving && payload.applied();
        this.saving = false;
        if (completed) this.minecraft.setScreen(this.previous);
        else update();
    }

    @Override
    public void tick() { this.previous.tick(); update(); }

    @Override
    public void onClose() { this.minecraft.setScreen(this.previous); }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.left + this.panelWidth / 2, this.top + 2, 0xFFFFFF);
        graphics.drawString(this.font, "Source storage", this.left, this.top + 16, 0xCCCCCC);
        graphics.drawString(this.font, "Destination storage", this.left, this.top + 50, 0xCCCCCC);
        graphics.drawString(this.font, "Item with exact components", this.left, this.top + 84, 0xCCCCCC);
        graphics.drawString(this.font, this.mode == HiredTransferQueue.Mode.FINITE ? "Quantity (1 to 4096)" : "Destination target (1 to 4096)", this.left, this.top + 118, 0xCCCCCC);
        String feedback = this.previous.state().feedback();
        if (feedback.isEmpty()) feedback = "Collected cargo finishes delivery after cancellation.";
        graphics.drawString(this.font, this.font.plainSubstrByWidth(feedback, this.panelWidth), this.left,
                this.top + this.panelHeight - 44, 0xBBBBBB);
        if (mouseX >= this.left && mouseX < this.left + this.panelWidth && mouseY >= this.top + this.panelHeight - 44
                && mouseY < this.top + this.panelHeight - 32)
            graphics.renderTooltip(this.font, this.font.split(Component.literal(feedback), this.panelWidth), mouseX, mouseY);
    }
}
