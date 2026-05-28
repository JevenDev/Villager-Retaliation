package com.jvn.villagerretaliation.client.interaction;

import net.minecraft.util.Mth;

final class VillagerInteractionScreenState {
    private int selectedOption = -1;
    private float optionScroll;
    private float targetOptionScroll;

    int selectedOption() {
        return this.selectedOption;
    }

    void setSelectedOption(int selectedOption) {
        this.selectedOption = selectedOption;
    }

    float optionScroll() {
        return this.optionScroll;
    }

    float targetOptionScroll() {
        return this.targetOptionScroll;
    }

    void resetOptions(boolean hasOptions) {
        this.selectedOption = hasOptions ? 0 : -1;
        this.optionScroll = 0.0F;
        this.targetOptionScroll = 0.0F;
    }

    OptionListPosition captureOptionListPosition() {
        return new OptionListPosition(this.selectedOption, this.optionScroll, this.targetOptionScroll);
    }

    void restoreOptionListPosition(OptionListPosition position, int optionCount, float maxScroll) {
        if (optionCount > 0) {
            this.selectedOption = Mth.clamp(position.selectedOption(), 0, optionCount - 1);
        } else {
            this.selectedOption = -1;
        }
        this.optionScroll = Mth.clamp(position.optionScroll(), 0.0F, maxScroll);
        this.targetOptionScroll = Mth.clamp(position.targetOptionScroll(), 0.0F, maxScroll);
    }

    void moveSelectedOption(int direction, int optionCount) {
        if (optionCount <= 0) {
            this.selectedOption = -1;
            return;
        }

        this.selectedOption = Mth.positiveModulo(this.selectedOption + direction, optionCount);
    }

    void tickOptionScroll(float lerp) {
        this.optionScroll = Mth.lerp(lerp, this.optionScroll, this.targetOptionScroll);
        if (Math.abs(this.optionScroll - this.targetOptionScroll) < 0.15F) {
            this.optionScroll = this.targetOptionScroll;
        }
    }

    void setTargetOptionScroll(float scroll, float maxScroll) {
        this.targetOptionScroll = Mth.clamp(scroll, 0.0F, maxScroll);
    }

    void jumpOptionScrollToTarget() {
        this.optionScroll = this.targetOptionScroll;
    }

    record OptionListPosition(int selectedOption, float optionScroll, float targetOptionScroll) {
    }
}
