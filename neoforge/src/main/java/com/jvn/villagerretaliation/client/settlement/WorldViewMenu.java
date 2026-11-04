package com.jvn.villagerretaliation.client.settlement;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public final class WorldViewMenu {
    private final SettlementViewPopup popup;

    public WorldViewMenu(Font font, int width, int height, double x, double y, List<Component> details, List<Action> actions) {
        popup = new SettlementViewPopup(SettlementViewPopup.Target.of(SettlementViewPopup.Kind.NAVIGATION, null), x, y);
        popup.update(font, width, height, details, actions.stream().map(action ->
                new SettlementViewPopup.Action(action.label(), action.enabled(), action.run(), null, action.icon(), Component.empty(), action.renderer())).toList());
        Minecraft.getInstance().getNarrator().sayNow(popup.narration());
    }

    public void render(GuiGraphics graphics, Font font, int x, int y, float partial) { popup.render(graphics, font, x, y, partial); }
    public void hover(double x, double y) { popup.hover(x, y); }
    public boolean click(double x, double y, int button) { return popup.click(x, y, button); }
    public boolean scroll(double x, double y, double amount) { return popup.scroll(x, y, amount); }
    public boolean key(int key) { return popup.key(key); }

    @FunctionalInterface
    public interface IconRenderer {
        void render(GuiGraphics graphics, int x, int y);
    }

    public record Action(Component label, boolean enabled, Runnable run, ItemStack icon, IconRenderer renderer) {
        public Action(Component label, boolean enabled, Runnable run, ItemStack icon) { this(label, enabled, run, icon, null); }
        public Action(Component label, boolean enabled, Runnable run) { this(label, enabled, run, ItemStack.EMPTY); }
    }
}
