package com.jvn.villagerretaliation.client.ui;

import com.jvn.villagerretaliation.VillagerRetaliation;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;

@EventBusSubscriber(modid = VillagerRetaliation.MOD_ID, value = Dist.CLIENT)
public final class VillagerTooltips {
    private VillagerTooltips() { }

    public interface Provider {
        default VillagerTooltipStyle tooltipStyle() { return VillagerTooltipStyle.DEFAULT; }
    }

    @SubscribeEvent
    public static void renderTooltip(RenderTooltipEvent.Color event) {
        if (!(Minecraft.getInstance().screen instanceof Provider provider)) return;
        var components = event.getComponents();
        if (components.isEmpty()) return;
        int width = 0;
        int height = components.size() == 1 ? -2 : 0;
        for (var component : components) {
            width = Math.max(width, component.getWidth(event.getFont()));
            height += component.getHeight();
        }
        provider.tooltipStyle().renderBackground(event.getGraphics(), event.getX(), event.getY(), width, height, 400);
        event.setBackground(0);
        event.setBorderStart(0);
        event.setBorderEnd(0);
    }
}
