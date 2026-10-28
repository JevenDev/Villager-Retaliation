package com.jvn.villagerretaliation.client.exploration;

import com.jvn.villagerretaliation.exploration.MapView;
import net.minecraft.client.gui.GuiGraphics;

final class QuestMapOverlay {
    private QuestMapOverlay() { }

    static void renderArea(GuiGraphics graphics, MapView view, MapDestination destination) {
        if (destination.radius() <= 0) return;
        double x = view.screenX(destination.x()), y = view.screenY(destination.z());
        double radius = destination.radius() * view.scale();
        int samples = Math.clamp((int) (radius * 8), 48, 2048);
        for (int i = 0; i < samples; i++) {
            double angle = i * Math.PI * 2 / samples;
            int px = (int) Math.round(x + Math.cos(angle) * radius), py = (int) Math.round(y + Math.sin(angle) * radius);
            if (px >= -1 && py >= -1 && px < view.width() && py < view.height())
                graphics.fill(px, py, px + 2, py + 2, destination.color());
        }
    }
}
