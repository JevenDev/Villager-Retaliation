package com.jvn.villagerretaliation.client.exploration;

import com.jvn.villagerretaliation.client.quest.VillagerQuestTrackerOverlay;
import com.jvn.villagerretaliation.exploration.MapView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

final class QuestMapOverlay {
    private QuestMapOverlay() { }

    static void render(GuiGraphics graphics, MapView view, Font font) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        for (var entry : VillagerQuestTrackerOverlay.journalEntries()) {
            if (!entry.state().equals("active")) continue;
            var waypoint = entry.journal().waypoint();
            if (!waypoint.present() || !waypoint.dimension().equals(level.dimension().location().toString())) continue;
            int x = (int) view.screenX(waypoint.x()), y = (int) view.screenY(waypoint.z());
            int color = com.jvn.villagerretaliation.client.quest.VillagerQuestUi.ACCENT_COLOR;
            if (waypoint.radius() > 0) {
                double radius = Math.abs(view.screenX(waypoint.x() + waypoint.radius()) - view.screenX(waypoint.x()));
                int samples = Math.clamp((int) (radius * 8), 48, 2048);
                for (int i = 0; i < samples; i++) {
                    double angle = i * Math.PI * 2 / samples;
                    int px = x + (int) Math.round(Math.cos(angle) * radius);
                    int py = y + (int) Math.round(Math.sin(angle) * radius);
                    if (px >= -1 && py >= -1 && px < view.width() && py < view.height())
                        graphics.fill(px, py, px + 2, py + 2, color);
                }
            } else {
                for (int i = -4; i <= 4; i++) {
                    graphics.fill(x + i, y + i, x + i + 2, y + i + 2, color);
                    graphics.fill(x + i, y - i, x + i + 2, y - i + 2, color);
                }
            }
            graphics.drawCenteredString(font, entry.title(), x, y + 10, color);
        }
    }
}
