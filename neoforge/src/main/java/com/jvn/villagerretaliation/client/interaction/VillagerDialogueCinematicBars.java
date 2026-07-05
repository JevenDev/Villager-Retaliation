package com.jvn.villagerretaliation.client.interaction;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

final class VillagerDialogueCinematicBars {
    private static final int BAR_COLOR = 0xFF000000;

    private VillagerDialogueCinematicBars() {
    }

    static float sampleSlant() {
        int min = Mth.clamp(VillagerRetaliationConfig.DIALOGUE_CINEMATIC_BAR_MIN_SLANT.get(), -64, 64);
        int max = Mth.clamp(VillagerRetaliationConfig.DIALOGUE_CINEMATIC_BAR_MAX_SLANT.get(), -64, 64);
        int low = Math.min(min, max);
        int high = Math.max(min, max);
        if (low == high) {
            return low;
        }
        return Mth.lerp(RandomSource.create().nextFloat(), (float) low, (float) high);
    }

    static void render(GuiGraphics graphics, int width, int height, float visibility, float slant) {
        if (!VillagerRetaliationConfig.ENABLE_DIALOGUE_CINEMATIC_BARS.get()) {
            return;
        }

        int barHeight = Mth.clamp(
                VillagerRetaliationConfig.DIALOGUE_CINEMATIC_BAR_HEIGHT.get(),
                0,
                Math.max(0, height / 2));
        if (barHeight <= 0) {
            return;
        }

        float progress = VillagerRetaliationConfig.ANIMATE_DIALOGUE_CINEMATIC_BARS.get()
                ? Mth.clamp(visibility, 0.0F, 1.0F)
                : 1.0F;
        float clampedSlant = Mth.clamp(slant, -64.0F, 64.0F);
        if (!VillagerInteractionScreenShaderRenderer.renderDialogueCinematicBars(
                graphics,
                width,
                height,
                barHeight,
                clampedSlant,
                progress)) {
            renderFallbackBars(graphics, width, height, barHeight, progress);
        }
    }

    private static void renderFallbackBars(
            GuiGraphics graphics,
            int width,
            int height,
            int barHeight,
            float progress) {
        int visibleHeight = Math.round(barHeight * progress);
        if (visibleHeight <= 0) {
            return;
        }
        graphics.fill(0, 0, width, visibleHeight, BAR_COLOR);
        graphics.fill(0, height - visibleHeight, width, height, BAR_COLOR);
    }
}
