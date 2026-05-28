package com.jvn.villagerretaliation.client.interaction;

import com.jvn.toucanlib.client.ToucanScrollState;
import net.minecraft.util.Mth;

final class VillagerInteractionScreenState {
    private int selectedOption = -1;
    private final ToucanScrollState optionScroll = new ToucanScrollState();

    int selectedOption() {
        return this.selectedOption;
    }

    void setSelectedOption(int selectedOption) {
        this.selectedOption = selectedOption;
    }

    float optionScroll() {
        return this.optionScroll.currentScroll();
    }

    float targetOptionScroll() {
        return this.optionScroll.targetScroll();
    }

    void resetOptions(boolean hasOptions) {
        this.selectedOption = hasOptions ? 0 : -1;
        this.optionScroll.reset();
    }

    OptionListPosition captureOptionListPosition() {
        return new OptionListPosition(this.selectedOption, this.optionScroll.currentScroll(), this.optionScroll.targetScroll());
    }

    void restoreOptionListPosition(OptionListPosition position, int optionCount, float maxScroll) {
        if (optionCount > 0) {
            this.selectedOption = Mth.clamp(position.selectedOption(), 0, optionCount - 1);
        } else {
            this.selectedOption = -1;
        }
        this.optionScroll.restore(new ToucanScrollState.Snapshot(position.optionScroll(), position.targetOptionScroll()), maxScroll);
    }

    void moveSelectedOption(int direction, int optionCount) {
        if (optionCount <= 0) {
            this.selectedOption = -1;
            return;
        }

        this.selectedOption = Mth.positiveModulo(this.selectedOption + direction, optionCount);
    }

    void tickOptionScroll(float lerp) {
        this.optionScroll.tick(lerp, 0.15F);
    }

    void setTargetOptionScroll(float scroll, float maxScroll) {
        this.optionScroll.setTargetScroll(scroll, maxScroll);
    }

    void jumpOptionScrollToTarget() {
        this.optionScroll.jumpToTarget();
    }

    record OptionListPosition(int selectedOption, float optionScroll, float targetOptionScroll) {
    }
}
