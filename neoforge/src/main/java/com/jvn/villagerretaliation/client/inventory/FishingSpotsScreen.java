package com.jvn.villagerretaliation.client.inventory;

import com.jvn.villagerretaliation.interaction.work.HiredFishingSpots;
import com.jvn.villagerretaliation.network.HiredFishingRequestPayload;
import com.jvn.villagerretaliation.network.HiredFishingSnapshotPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.network.PacketDistributor;

public final class FishingSpotsScreen extends Screen {
    private final Screen previous;
    private final UUID worker;
    private final Consumer<HiredFishingSpots> saveDraft;
    private final Runnable inherit;
    private final List<BlockPos> positions = new ArrayList<>();
    private final List<Button> rows = new ArrayList<>();
    private EditBox x;
    private EditBox y;
    private EditBox z;
    private Button save;
    private Button add;
    private Button up;
    private Button down;
    private Button remove;
    private Button automatic;
    private Button refresh;
    private Button next;
    private Button backPage;
    private long revision;
    private UUID pending;
    private long sentAt;
    private boolean pendingSave;
    private boolean editable;
    private boolean loaded;
    private String feedback = "";
    private int selected = -1;
    private int page;
    private int left;
    private int panelWidth;
    private String inputX = "";
    private String inputY = "";
    private String inputZ = "";

    public FishingSpotsScreen(Screen previous, UUID worker) {
        this(previous, worker, HiredFishingSpots.AUTOMATIC, null, null);
    }

    public FishingSpotsScreen(Screen previous, HiredFishingSpots spots, Consumer<HiredFishingSpots> save, Runnable inherit) {
        this(previous, null, spots, save, inherit);
    }

    private FishingSpotsScreen(Screen previous, UUID worker, HiredFishingSpots spots,
                               Consumer<HiredFishingSpots> save, Runnable inherit) {
        super(Component.literal("Fishing spots"));
        this.previous = previous;
        this.worker = worker;
        this.saveDraft = save;
        this.inherit = inherit;
        this.positions.addAll(spots.positions());
        this.editable = worker == null;
        this.loaded = worker == null;
    }

    public Screen previous() { return this.previous; }

    @Override
    protected void init() {
        this.panelWidth = Math.min(460, this.width - 32);
        this.left = (this.width - this.panelWidth) / 2;
        this.rows.clear();
        int count = Math.max(1, (this.height - 168) / 24);
        for (int index = 0; index < count; index++) {
            int row = index;
            this.rows.add(button("", this.left, 52 + index * 24, this.panelWidth, () -> {
                this.selected = this.page * this.rows.size() + row;
                update();
            }));
        }
        this.backPage = button("<", this.left + this.panelWidth - 52, 16, 24, () -> { this.page--; update(); });
        this.next = button(">", this.left + this.panelWidth - 24, 16, 24, () -> { this.page++; update(); });
        int bottom = this.height - 108;
        int column = (this.panelWidth - 84) / 3;
        this.x = coordinate(this.left, bottom, column - 4, "X", this.inputX);
        this.y = coordinate(this.left + column, bottom, column - 4, "Y", this.inputY);
        this.z = coordinate(this.left + column * 2, bottom, column - 4, "Z", this.inputZ);
        this.x.setResponder(value -> this.inputX = value);
        this.y.setResponder(value -> this.inputY = value);
        this.z.setResponder(value -> this.inputZ = value);
        this.add = button("Add", this.left + this.panelWidth - 80, bottom, 80, this::add);
        int quarter = (this.panelWidth - 12) / 4;
        this.up = button("Up", this.left, bottom + 24, quarter, () -> move(-1));
        this.down = button("Down", this.left + quarter + 4, bottom + 24, quarter, () -> move(1));
        this.remove = button("Remove", this.left + (quarter + 4) * 2, bottom + 24, quarter, () -> {
            this.positions.remove(this.selected);
            this.selected = Math.min(this.selected, this.positions.size() - 1);
            update();
        });
        this.automatic = button("Automatic", this.left + (quarter + 4) * 3, bottom + 24, quarter, () -> {
            this.positions.clear();
            this.selected = -1;
            update();
        });
        this.automatic.setTooltip(Tooltip.create(Component.literal("An empty list lets the fisher discover water near the assigned work area.")));
        button("Back", this.left, this.height - 28, 64, this::onClose);
        if (this.worker != null) this.refresh = button("Refresh", this.left + 68, this.height - 28, 68, () -> request(false));
        if (this.inherit != null) button("Inherit", this.left + 68, this.height - 28, 68, () -> {
            this.inherit.run();
            onClose();
        }).setTooltip(Tooltip.create(Component.literal("Use the worksite's fishing spots.")));
        this.save = button("Save", this.left + this.panelWidth - 72, this.height - 28, 72, () -> {
            if (this.worker != null) request(true);
            else {
                this.saveDraft.accept(new HiredFishingSpots(this.positions));
                onClose();
            }
        });
        if (this.inputX.isEmpty() && this.minecraft.hitResult instanceof BlockHitResult hit) {
            this.x.setValue(Integer.toString(hit.getBlockPos().getX()));
            this.y.setValue(Integer.toString(hit.getBlockPos().getY()));
            this.z.setValue(Integer.toString(hit.getBlockPos().getZ()));
        }
        update();
        if (!this.loaded && this.pending == null) request(false);
    }

    private EditBox coordinate(int x, int y, int width, String label, String value) {
        var field = addRenderableWidget(new EditBox(this.font, x, y, width, 20, Component.literal(label)));
        field.setHint(Component.literal(label));
        field.setMaxLength(10);
        field.setFilter(text -> text.matches("-?[0-9]*"));
        field.setValue(value);
        return field;
    }

    private Button button(String label, int x, int y, int width, Runnable action) {
        return addRenderableWidget(Button.builder(Component.literal(label), ignored -> action.run()).bounds(x, y, width, 20).build());
    }

    private void add() {
        try {
            var position = new BlockPos(Integer.parseInt(this.x.getValue()), Integer.parseInt(this.y.getValue()), Integer.parseInt(this.z.getValue()));
            if (this.positions.contains(position)) this.feedback = "That spot is already selected";
            else if (this.positions.size() < HiredFishingSpots.MAX_SPOTS) {
                this.positions.add(position);
                this.selected = this.positions.size() - 1;
                this.page = this.selected / this.rows.size();
                this.feedback = "";
            }
        } catch (NumberFormatException invalid) {
            this.feedback = "Enter the water block's X, Y, and Z coordinates";
        }
        update();
    }

    private void move(int direction) {
        java.util.Collections.swap(this.positions, this.selected, this.selected + direction);
        this.selected += direction;
        this.page = this.selected / this.rows.size();
        update();
    }

    private void request(boolean save) {
        if (this.pending != null) return;
        this.pending = UUID.randomUUID();
        this.pendingSave = save;
        this.sentAt = Util.getMillis();
        this.feedback = save ? "Saving..." : "Refreshing...";
        PacketDistributor.sendToServer(new HiredFishingRequestPayload(this.worker, this.pending, this.revision,
                save ? new HiredFishingSpots(this.positions) : null));
        update();
    }

    public static void accept(HiredFishingSnapshotPayload payload) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || !minecraft.player.isAlive() || minecraft.player.isSpectator()
                || !minecraft.level.dimension().location().equals(payload.dimension())
                || !(minecraft.screen instanceof FishingSpotsScreen screen)
                || !payload.worker().equals(screen.worker) || !payload.request().equals(screen.pending)) return;
        screen.pending = null;
        screen.editable = payload.editable();
        screen.loaded = true;
        screen.feedback = payload.feedback();
        if (!screen.pendingSave || payload.applied()) {
            screen.revision = payload.revision();
            screen.positions.clear();
            screen.positions.addAll(payload.spots().positions());
        }
        if (payload.applied()) screen.onClose();
        else screen.update();
    }

    private void update() {
        if (this.save == null) return;
        boolean allowed = this.editable && this.pending == null;
        this.selected = Math.clamp(this.selected, -1, this.positions.size() - 1);
        this.page = Math.clamp(this.page, 0, Math.max(0, (this.positions.size() - 1) / this.rows.size()));
        for (int index = 0; index < this.rows.size(); index++) {
            var row = this.rows.get(index);
            int spot = this.page * this.rows.size() + index;
            row.visible = spot < this.positions.size();
            row.active = allowed && row.visible;
            if (row.visible) row.setMessage(Component.literal((spot == this.selected ? "> " : "") + (spot + 1) + ". " + this.positions.get(spot).toShortString()));
        }
        this.add.active = allowed && this.positions.size() < HiredFishingSpots.MAX_SPOTS;
        this.up.active = allowed && this.selected > 0;
        this.down.active = allowed && this.selected >= 0 && this.selected + 1 < this.positions.size();
        this.remove.active = allowed && this.selected >= 0 && this.selected < this.positions.size();
        this.automatic.active = allowed;
        this.save.active = allowed;
        this.x.active = allowed;
        this.y.active = allowed;
        this.z.active = allowed;
        this.backPage.active = this.page > 0;
        this.next.active = (this.page + 1) * this.rows.size() < this.positions.size();
        if (this.refresh != null) this.refresh.active = this.pending == null;
    }

    @Override
    public void tick() {
        if (this.pending != null && Util.getMillis() - this.sentAt >= 10_000L) {
            this.pending = null;
            this.editable = false;
            this.feedback = "No acknowledgement. Refresh to confirm the saved list";
            update();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(this.font, this.title, this.left, 20, 0xFFFFFF);
        String hint = this.positions.isEmpty() ? "Automatic discovery" : "First available spot wins. Current casts finish.";
        graphics.drawString(this.font, this.font.plainSubstrByWidth(hint, this.panelWidth), this.left, 40, 0xBBBBBB);
        int y = this.height - 58;
        for (var line : this.font.split(Component.literal(this.feedback), this.panelWidth)) {
            if (y + this.font.lineHeight > this.height - 32) break;
            graphics.drawString(this.font, line, this.left, y, 0xFFD080);
            y += this.font.lineHeight;
        }
    }

    @Override
    public void onClose() { this.minecraft.setScreen(this.previous); }

    @Override
    public boolean isPauseScreen() { return false; }
}
