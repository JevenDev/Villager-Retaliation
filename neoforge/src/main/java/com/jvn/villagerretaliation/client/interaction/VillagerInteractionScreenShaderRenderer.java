package com.jvn.villagerretaliation.client.interaction;

import com.jvn.toucanlib.neoforge.client.ToucanCinematicBars;
import net.minecraft.client.gui.GuiGraphics;

public final class VillagerInteractionScreenShaderRenderer {
    private VillagerInteractionScreenShaderRenderer() {
    }

    public record ShaderRect(float left, float top, float right, float bottom) {
        public ShaderRect(int left, int top, int right, int bottom) {
            this((float) left, (float) top, (float) right, (float) bottom);
        }
    }

    public static boolean renderDialogueCinematicBars(
            GuiGraphics graphics,
            int width,
            int height,
            float barHeight,
            float slant,
            float progress) {
        return ToucanCinematicBars.render(
                graphics, width, height, barHeight, slant, progress);
    }
}
