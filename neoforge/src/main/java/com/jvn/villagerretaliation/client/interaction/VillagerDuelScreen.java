package com.jvn.villagerretaliation.client.interaction;

import com.jvn.villagerretaliation.duel.DuelAvailabilityReason;
import com.jvn.villagerretaliation.duel.DuelLoadout;
import com.jvn.villagerretaliation.network.OpenVillagerDuelPayload;
import com.jvn.villagerretaliation.network.VillagerDuelRequestPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public final class VillagerDuelScreen extends Screen {
    private static final int[] STAKES = {0, 8, 16, 32, 64, Integer.MAX_VALUE};
    private final OpenVillagerDuelPayload status;
    private DuelLoadout loadout = DuelLoadout.BARE_HANDED;
    private int stakeIndex;
    private Button loadoutButton;
    private Button stakeButton;
    private Button confirmButton;
    private boolean confirmationArmed;

    public VillagerDuelScreen(OpenVillagerDuelPayload status) {
        super(Component.translatable("villagerretaliation.gui.duel.title", status.villagerName()));
        this.status = status;
    }

    @Override
    protected void init() {
        int left = this.width / 2 - 110;
        int top = this.height / 2 - 26;
        this.loadoutButton = addRenderableWidget(Button.builder(loadoutText(), ignored -> cycleLoadout())
                .bounds(left, top, 220, 20).build());
        this.stakeButton = addRenderableWidget(Button.builder(stakeText(), ignored -> cycleStake())
                .bounds(left, top + 24, 220, 20).build());
        this.confirmButton = addRenderableWidget(Button.builder(
                        Component.translatable("villagerretaliation.gui.duel.review"), ignored -> confirmOrStart())
                .bounds(left, top + 52, 106, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), ignored -> onClose())
                .bounds(left + 114, top + 52, 106, 20).build());
        updateState();
    }

    private void cycleLoadout() {
        DuelLoadout[] values = DuelLoadout.values();
        this.loadout = values[(this.loadout.ordinal() + 1) % values.length];
        this.loadoutButton.setMessage(loadoutText());
        disarmConfirmation();
    }

    private void cycleStake() {
        this.stakeIndex = (this.stakeIndex + 1) % STAKES.length;
        this.stakeButton.setMessage(stakeText());
        disarmConfirmation();
        updateState();
    }

    private void disarmConfirmation() {
        this.confirmationArmed = false;
        if (this.confirmButton != null) {
            this.confirmButton.setMessage(Component.translatable("villagerretaliation.gui.duel.review"));
        }
    }

    private int selectedStake() {
        return STAKES[this.stakeIndex] == Integer.MAX_VALUE ? this.status.maximumStake() : STAKES[this.stakeIndex];
    }

    private Component loadoutText() {
        return Component.translatable("villagerretaliation.gui.duel.loadout",
                Component.translatable("villagerretaliation.gui.duel.loadout." + this.loadout.name().toLowerCase()));
    }

    private Component stakeText() {
        String amount = STAKES[this.stakeIndex] == Integer.MAX_VALUE
                ? Component.translatable("villagerretaliation.gui.duel.maximum", this.status.maximumStake()).getString()
                : Integer.toString(selectedStake());
        return Component.translatable("villagerretaliation.gui.duel.stake", amount, this.status.currencyName());
    }

    private void updateState() {
        if (this.confirmButton != null) this.confirmButton.active = this.status.available() && selectedStake() <= this.status.maximumStake();
    }

    private void confirmOrStart() {
        if (!this.confirmButton.active) return;
        if (this.confirmationArmed) { start(); return; }
        this.confirmationArmed = true;
        this.confirmButton.setMessage(Component.translatable("villagerretaliation.gui.duel.confirm"));
    }

    private void start() {
        int wireStake = STAKES[this.stakeIndex];
        PacketDistributor.sendToServer(new VillagerDuelRequestPayload(
                this.status.entityId(), VillagerDuelRequestPayload.Action.START, this.loadout, wireStake));
        onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        int center = this.width / 2;
        graphics.drawCenteredString(this.font, this.title, center, this.height / 2 - 94, 0xFFFFFF);
        graphics.drawCenteredString(this.font, Component.translatable("villagerretaliation.gui.duel.record",
                this.status.villagerWins(), this.status.villagerLosses()), center, this.height / 2 - 78, 0xD0D0D0);
        graphics.drawCenteredString(this.font, Component.translatable("villagerretaliation.gui.duel.balances",
                this.status.playerBalance(), this.status.villagerBalance(), this.status.currencyName()), center,
                this.height / 2 - 64, 0xD0D0D0);
        Component rules = this.status.available()
                ? Component.translatable("villagerretaliation.gui.duel.rules", this.status.arenaRadius(),
                        formatTicks(this.status.boundaryGraceTicks()), formatTicks(this.status.timeoutTicks()),
                        this.status.cooldownDays())
                : unavailableReason();
        graphics.drawCenteredString(this.font, rules, center, this.height / 2 - 50,
                this.status.available() ? 0xE9C46A : 0xFF7777);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private Component unavailableReason() {
        if (this.status.reason() == DuelAvailabilityReason.COOLDOWN) {
            return Component.translatable("villagerretaliation.duel.unavailable.cooldown_remaining",
                    formatTicks(this.status.cooldownTicks()));
        }
        return Component.translatable("villagerretaliation.duel.unavailable."
                + this.status.reason().name().toLowerCase());
    }

    private static String formatTicks(long ticks) {
        long seconds = Math.max(0L, (ticks + 19L) / 20L);
        if (seconds >= 60L && seconds % 60L == 0L) return (seconds / 60L) + "m";
        return seconds + "s";
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
