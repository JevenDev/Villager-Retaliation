package com.jvn.villagerretaliation.client.settlement;

import java.util.List;
import java.util.UUID;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public final class VillageMapPopup {
    private final SettlementViewPopup popup;

    public VillageMapPopup(Font font, int width, int height, UUID village, String name, double x, double y,
                           Runnable relations, Runnable select) {
        this(font, width, height, village, name, x, y, relations, select, List.of(Component.literal(name)), List.of());
    }

    public VillageMapPopup(Font font, int width, int height, UUID village, String name, double x, double y,
                           Runnable relations, Runnable select, List<Component> details, List<WorldViewMenu.Action> extraActions) {
        popup = new SettlementViewPopup(SettlementViewPopup.Target.of(SettlementViewPopup.Kind.NAVIGATION, village), x, y);
        var actions = new java.util.ArrayList<>(List.of(
                new SettlementViewPopup.Action(VillageRelationsScreen.text("title"), true, relations),
                new SettlementViewPopup.Action(VillageRelationsScreen.text("map_perspective"), true, select)));
        extraActions.forEach(action -> actions.add(new SettlementViewPopup.Action(action.label(), action.enabled(), action.run(), null, action.icon())));
        popup.update(font, width, height, details, actions);
    }

    public void render(GuiGraphics graphics, Font font, int x, int y, float partial) { popup.hover(x, y); popup.render(graphics, font, x, y, partial); }
    public boolean click(double x, double y, int button) { return popup.click(x, y, button); }
    public boolean scroll(double x, double y, double amount) { return popup.scroll(x, y, amount); }
    public boolean key(int key) { return popup.key(key); }
    public Component narration() { return popup.narration(); }
}
