package com.jvn.villagerretaliation.client.settlement;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

final class SettlementWallSpacingSlider extends AbstractSliderButton {
    private final SettlementWallEditor editor;
    SettlementWallSpacingSlider(SettlementWallEditor editor) {
        super(0, 0, 118, 18, Component.empty(), 10.0 / 58);
        this.editor = editor;
    }
    void sync() {
        if (this.editor.active()) this.value = (this.editor.draft().spacing() - 6) / 58.0;
        updateMessage();
    }
    @Override protected void updateMessage() { setMessage(Component.literal("Spacing " + (6 + (int) Math.round(this.value * 58)))); }
    @Override protected void applyValue() {
        if (this.editor.active() && !this.editor.confirming()) this.editor.draft().spacing(6 + (int) Math.round(this.value * 58));
    }
}
