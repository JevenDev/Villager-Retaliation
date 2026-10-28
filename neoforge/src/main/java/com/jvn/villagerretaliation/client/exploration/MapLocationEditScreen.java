package com.jvn.villagerretaliation.client.exploration;

import com.jvn.villagerretaliation.client.settlement.WorldViewMenu;
import com.jvn.villagerretaliation.exploration.MapLocation;
import com.jvn.villagerretaliation.network.MapLocationRequest;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;
import static com.jvn.villagerretaliation.client.exploration.MapLocationsClient.text;

final class MapLocationEditScreen extends Screen {
    private final ExploredMapScreen parent;
    private final MapLocation original;
    private MapLocation.Icon icon;
    private boolean alwaysShowName;
    private EditBox name, preview;
    private Button save, chooseIcon;
    private WorldViewMenu menu;
    private MapLocation pending;
    private Component error = Component.empty();
    private int left, top, panelWidth;

    MapLocationEditScreen(ExploredMapScreen parent, MapLocation location) {
        super(text("edit"));
        this.parent = parent; original = location; icon = location.icon(); alwaysShowName = location.alwaysShowName();
    }

    @Override protected void init() {
        String nameValue = name == null ? original.name() : name.getValue();
        String previewValue = preview == null ? original.previewName() : preview.getValue();
        panelWidth = Math.min(260, width - 24); left = (width - panelWidth) / 2; top = (height - 208) / 2;
        name = addRenderableWidget(new EditBox(font, left + 8, top + 40, panelWidth - 16, 20, text("name")));
        name.setMaxLength(MapLocation.MAX_NAME); name.setValue(nameValue);
        preview = addRenderableWidget(new EditBox(font, left + 8, top + 78, panelWidth - 16, 20, text("preview")));
        preview.setMaxLength(MapLocation.MAX_NAME); preview.setValue(previewValue);
        preview.setHint(text("preview_hint"));
        preview.setTooltip(Tooltip.create(text("preview_help")));
        addRenderableWidget(Button.builder(text(alwaysShowName ? "always" : "hover"), button -> {
            alwaysShowName = !alwaysShowName; button.setMessage(text(alwaysShowName ? "always" : "hover"));
        }).bounds(left + 8, top + 106, panelWidth - 16, 20).build());
        chooseIcon = addRenderableWidget(Button.builder(text("icon", MapLocationsClient.icon(icon).getHoverName()), button -> chooseIcon())
                .bounds(left + 30, top + 134, panelWidth - 38, 20).build());
        save = addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> save())
                .bounds(left + 8, top + 180, (panelWidth - 24) / 2, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> onClose())
                .bounds(left + panelWidth / 2 + 4, top + 180, (panelWidth - 24) / 2, 20).build());
        setInitialFocus(name);
        menu = null;
    }

    private void chooseIcon() {
        menu = new WorldViewMenu(font, width, height, left + panelWidth, top + 24, List.of(text("choose_icon")),
                Arrays.stream(MapLocation.Icon.values()).map(value -> new WorldViewMenu.Action(
                        MapLocationsClient.icon(value).getHoverName(), true, () -> {
                            icon = value; chooseIcon.setMessage(text("icon", MapLocationsClient.icon(icon).getHoverName())); menu = null;
                        }, MapLocationsClient.icon(value))).toList());
    }

    private MapLocation draft() {
        return new MapLocation(original.id(), original.dimension(), original.x(), original.z(), icon,
                name.getValue(), preview.getValue(), alwaysShowName);
    }

    private void save() {
        if (pending != null) return;
        try { pending = draft(); }
        catch (IllegalArgumentException invalid) { error = text("invalid_name"); return; }
        PacketDistributor.sendToServer(new MapLocationRequest(MapLocationRequest.Action.SAVE, pending, null, MapLocationRequest.Audience.PARTY, null));
    }

    void accept(java.util.UUID acknowledgedSave) {
        if (pending == null || !pending.id().equals(acknowledgedSave)) return;
        if (MapLocationsClient.all().contains(pending)) onClose();
        else error = text("save_failed", MapLocation.MAX_LOCATIONS);
        pending = null;
    }

    @Override public void tick() {
        save.active = pending == null && !name.getValue().isBlank();
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public void renderBackground(GuiGraphics graphics, int x, int y, float partial) { }
    @Override public void render(GuiGraphics graphics, int x, int y, float partial) {
        parent.render(graphics, -1, -1, partial);
        graphics.fill(0, 0, width, height, 0x80000000);
        TooltipRenderUtil.renderTooltipBackground(graphics, left, top, panelWidth, 208, 0);
        graphics.drawCenteredString(font, title, width / 2, top + 5, 0xffffff);
        graphics.drawCenteredString(font, text("coordinates", original.x(), original.z()), width / 2, top + 17, 0xaaaaaa);
        graphics.drawString(font, text("name"), left + 8, top + 29, 0xffffff);
        graphics.drawString(font, text("preview"), left + 8, top + 67, 0xffffff);
        graphics.renderItem(MapLocationsClient.icon(icon), left + 8, top + 136);
        graphics.drawString(font, error, left + 8, top + 163, 0xff9999);
        super.render(graphics, menu == null ? x : -1, menu == null ? y : -1, partial);
        if (menu != null) menu.render(graphics, font, x, y, partial);
    }
    @Override public void mouseMoved(double x, double y) {
        if (menu != null) menu.hover(x, y);
        else super.mouseMoved(x, y);
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (menu != null) { if (!menu.click(x, y, button)) menu = null; return true; }
        return super.mouseClicked(x, y, button);
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (menu != null) { menu.scroll(x, y, vertical); return true; }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (menu != null) {
            if (key == GLFW.GLFW_KEY_ESCAPE) menu = null;
            else menu.key(key);
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) { save(); return true; }
        return super.keyPressed(key, scan, modifiers);
    }
    @Override public boolean charTyped(char character, int modifiers) {
        return menu != null || super.charTyped(character, modifiers);
    }
}
