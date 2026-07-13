package com.jvn.villagerretaliation.client.party;

import com.jvn.villagerretaliation.client.VillagerRetaliationClientAssets;
import com.jvn.villagerretaliation.client.ui.VillagerNineSlice;
import com.jvn.villagerretaliation.network.PartyRosterSyncPayload;
import com.jvn.villagerretaliation.network.PartyActionRequestPayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

public final class PartyInventoryOverlay {
    private static final int PLAYER_INVENTORY_WIDTH = 176;
    private static final int PANEL_WIDTH = 172;
    private static final int LINE_HEIGHT = 10;
    private static final int CHECKBOX_SIZE = 8;
    private static final int MANAGE_TAB_TEXTURE_WIDTH = 47;
    private static final int MANAGE_TAB_HEIGHT = 20;
    private static final int MANAGE_TAB_PADDING = 4;
    private static final int MANAGE_TAB_INVENTORY_INSET = 2;
    private static final int MANAGE_TAB_PRESSED_OFFSET = 1;
    private static final int MANAGE_TAB_TEXT_X_OFFSET = 1;
    private static final int MANAGE_TAB_TEXT_Y_OFFSET = 1;
    private static final int CONTAINER_TITLE_COLOR = 0x404040;
    private static final VillagerNineSlice MANAGE_TAB_NINE_SLICE = new VillagerNineSlice(
            VillagerRetaliationClientAssets.PLAYER_INVENTORY_PARTY_TAB_TEXTURE,
            MANAGE_TAB_TEXTURE_WIDTH,
            MANAGE_TAB_HEIGHT,
            MANAGE_TAB_PADDING,
            MANAGE_TAB_PADDING,
            MANAGE_TAB_PADDING,
            MANAGE_TAB_PADDING);

    private static InventoryScreen pressedManageTabScreen;

    private PartyInventoryOverlay() {
    }

    public static void renderInventoryBackground(GuiGraphics graphics, InventoryScreen screen) {
        PartyRosterSyncPayload roster = PartyRosterClient.roster();
        if (!roster.active()) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        renderRosterPanel(graphics, font, roster, screen);
        if (pressedManageTabScreen != screen) {
            renderManageTab(graphics, font, screen, false);
        }
    }

    public static void onScreenRender(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof InventoryScreen screen)) {
            return;
        }
        PartyRosterSyncPayload roster = PartyRosterClient.roster();
        if (!roster.active()) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        GuiGraphics graphics = event.getGuiGraphics();
        if (pressedManageTabScreen == screen) {
            renderManageTab(graphics, font, screen, true);
        }

        Component tooltip = manageTabBounds(screen).contains(event.getMouseX(), event.getMouseY())
                ? Component.translatable("villagerretaliation.party.action.manage.tooltip")
                : rosterTooltip(font, roster, screen, event.getMouseX(), event.getMouseY());
        if (tooltip != null) {
            graphics.renderTooltip(font, tooltip, event.getMouseX(), event.getMouseY());
        }
    }

    public static void onMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.isCanceled()
                || event.getButton() != GLFW.GLFW_MOUSE_BUTTON_LEFT
                || !(event.getScreen() instanceof InventoryScreen screen)) {
            return;
        }
        PartyRosterSyncPayload roster = PartyRosterClient.roster();
        if (!roster.active()) {
            return;
        }
        if (manageTabBounds(screen).contains(event.getMouseX(), event.getMouseY())) {
            pressedManageTabScreen = screen;
            event.setCanceled(true);
            return;
        }
        if (!roster.recipientLeader()) {
            return;
        }
        int panelHeight = 8 + lines(roster).size() * LINE_HEIGHT;
        int inventoryLeft = screen.getGuiLeft();
        int inventoryTop = screen.getGuiTop();
        int top = Math.max(2, inventoryTop - panelHeight - 4);
        int firstVillagerLine = roster.players().size() + 3;
        for (int index = 0; index < roster.villagers().size(); index++) {
            int rowY = top + (firstVillagerLine + index) * LINE_HEIGHT;
            CheckboxBounds bounds = new CheckboxBounds(
                    inventoryLeft,
                    rowY + 1,
                    inventoryLeft + CHECKBOX_SIZE,
                    rowY + 1 + CHECKBOX_SIZE);
            if (!bounds.contains(event.getMouseX(), event.getMouseY())) {
                continue;
            }
            PartyRosterSyncPayload.VillagerEntry villager = roster.villagers().get(index);
            PacketDistributor.sendToServer(new PartyActionRequestPayload(
                    PartyActionRequestPayload.Action.SET_QUICK_COMMANDS_ENABLED,
                    villager.villagerId(),
                    null,
                    !villager.quickCommandsEnabled()));
            event.setCanceled(true);
            return;
        }
    }

    public static void onMouseReleased(ScreenEvent.MouseButtonReleased.Pre event) {
        if (event.getButton() != GLFW.GLFW_MOUSE_BUTTON_LEFT
                || !(event.getScreen() instanceof InventoryScreen screen)
                || pressedManageTabScreen != screen) {
            return;
        }
        pressedManageTabScreen = null;
        event.setCanceled(true);
        if (!PartyRosterClient.roster().active()
                || !manageTabBounds(screen).contains(event.getMouseX(), event.getMouseY())) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        minecraft.setScreen(new PartyManagementScreen(screen));
    }

    private static void renderManageTab(
            GuiGraphics graphics,
            Font font,
            InventoryScreen screen,
            boolean pressed) {
        Component label = Component.translatable("villagerretaliation.party.action.manage");
        Bounds bounds = manageTabBounds(screen);
        int drawTop = bounds.top() + (pressed ? MANAGE_TAB_PRESSED_OFFSET : 0);
        MANAGE_TAB_NINE_SLICE.renderAtTextureScale(
                graphics,
                bounds.left(),
                drawTop,
                bounds.width(),
                bounds.height());
        int textLeft = bounds.left() + (bounds.width() - font.width(label)) / 2 + MANAGE_TAB_TEXT_X_OFFSET;
        int textTop = drawTop + (bounds.height() - font.lineHeight) / 2 + MANAGE_TAB_TEXT_Y_OFFSET;
        graphics.drawString(font, label, textLeft, textTop, CONTAINER_TITLE_COLOR, false);
    }

    private static void renderRosterPanel(
            GuiGraphics graphics,
            Font font,
            PartyRosterSyncPayload roster,
            InventoryScreen screen) {
        List<Component> lines = lines(roster);
        int panelHeight = 8 + lines.size() * LINE_HEIGHT;
        int left = screen.getGuiLeft();
        int top = Math.max(2, screen.getGuiTop() - panelHeight - 4);
        graphics.fill(left - 3, top - 3, left + PANEL_WIDTH + 3, top + panelHeight, 0xCC101216);

        int y = top;
        for (int i = 0; i < lines.size(); i++) {
            Component line = lines.get(i);
            int villagerIndex = villagerIndexForLine(roster, i);
            int textX = left;
            int availableWidth = PANEL_WIDTH - 4;
            if (villagerIndex >= 0) {
                PartyRosterSyncPayload.VillagerEntry villager = roster.villagers().get(villagerIndex);
                CheckboxBounds bounds = new CheckboxBounds(left, y + 1, left + CHECKBOX_SIZE, y + 1 + CHECKBOX_SIZE);
                renderCheckbox(graphics, font, bounds, villager.quickCommandsEnabled());
                textX += CHECKBOX_SIZE + 3;
                availableWidth -= CHECKBOX_SIZE + 3;
            }
            String visible = font.plainSubstrByWidth(line.getString(), availableWidth);
            int color = i == 0 ? 0xFFE7C56A : 0xFFE5E5E5;
            graphics.drawString(font, visible, textX, y, color, true);
            y += LINE_HEIGHT;
        }
    }

    private static Component rosterTooltip(
            Font font,
            PartyRosterSyncPayload roster,
            InventoryScreen screen,
            double mouseX,
            double mouseY) {
        List<Component> lines = lines(roster);
        int panelHeight = 8 + lines.size() * LINE_HEIGHT;
        int left = screen.getGuiLeft();
        int top = Math.max(2, screen.getGuiTop() - panelHeight - 4);
        int y = top;
        for (int i = 0; i < lines.size(); i++) {
            Component line = lines.get(i);
            int villagerIndex = villagerIndexForLine(roster, i);
            int textX = left;
            int availableWidth = PANEL_WIDTH - 4;
            if (villagerIndex >= 0) {
                PartyRosterSyncPayload.VillagerEntry villager = roster.villagers().get(villagerIndex);
                CheckboxBounds bounds = new CheckboxBounds(left, y + 1, left + CHECKBOX_SIZE, y + 1 + CHECKBOX_SIZE);
                if (bounds.contains(mouseX, mouseY)) {
                    return Component.translatable(
                            villager.quickCommandsEnabled()
                                    ? "villagerretaliation.party.quick_command.checkbox.enabled"
                                    : "villagerretaliation.party.quick_command.checkbox.disabled");
                }
                textX += CHECKBOX_SIZE + 3;
                availableWidth -= CHECKBOX_SIZE + 3;
            }
            if (font.width(line) > availableWidth
                    && mouseX >= textX
                    && mouseX <= left + PANEL_WIDTH
                    && mouseY >= y
                    && mouseY < y + LINE_HEIGHT) {
                return line;
            }
            y += LINE_HEIGHT;
        }
        return null;
    }

    private static Bounds manageTabBounds(InventoryScreen screen) {
        Component label = Component.translatable("villagerretaliation.party.action.manage");
        int width = Math.max(MANAGE_TAB_TEXTURE_WIDTH, Minecraft.getInstance().font.width(label) + MANAGE_TAB_PADDING * 2);
        int left = screen.getGuiLeft() + PLAYER_INVENTORY_WIDTH - width;
        int top = screen.getGuiTop() - MANAGE_TAB_HEIGHT + MANAGE_TAB_INVENTORY_INSET;
        return new Bounds(left, top, width, MANAGE_TAB_HEIGHT);
    }

    private static int villagerIndexForLine(PartyRosterSyncPayload roster, int lineIndex) {
        int firstVillagerLine = roster.players().size() + 3;
        int villagerIndex = lineIndex - firstVillagerLine;
        return villagerIndex >= 0 && villagerIndex < roster.villagers().size() ? villagerIndex : -1;
    }

    private static void renderCheckbox(
            GuiGraphics graphics,
            Font font,
            CheckboxBounds bounds,
            boolean enabled) {
        graphics.fill(bounds.left(), bounds.top(), bounds.right(), bounds.bottom(), 0xFFE5E5E5);
        graphics.fill(bounds.left() + 1, bounds.top() + 1, bounds.right() - 1, bounds.bottom() - 1,
                enabled ? 0xFF477FA5 : 0xFF20242A);
        if (enabled) {
            graphics.drawString(font, "x", bounds.left() + 1, bounds.top() - 1, 0xFFFFFFFF, false);
        }
    }

    private static List<Component> lines(PartyRosterSyncPayload roster) {
        List<Component> lines = new ArrayList<>();
        int total = roster.players().size() + roster.villagers().size();
        lines.add(Component.translatable("villagerretaliation.party.roster.title", roster.leaderName(), total));
        lines.add(Component.translatable("villagerretaliation.party.roster.players", roster.players().size()));
        for (PartyRosterSyncPayload.PlayerEntry player : roster.players()) {
            if (player.leader() && !player.online()) {
                lines.add(Component.translatable("villagerretaliation.party.roster.player_leader_offline", player.name()));
            } else if (player.leader()) {
                lines.add(Component.translatable("villagerretaliation.party.roster.player_leader", player.name()));
            } else if (!player.online()) {
                lines.add(Component.translatable("villagerretaliation.party.roster.player_offline", player.name()));
            } else {
                lines.add(Component.literal(player.name()));
            }
        }
        lines.add(Component.translatable("villagerretaliation.party.roster.villagers", roster.villagers().size()));
        for (PartyRosterSyncPayload.VillagerEntry villager : roster.villagers()) {
            Component name = villager.name().isBlank()
                    ? Component.translatable("entity.minecraft.villager")
                    : Component.literal(villager.name());
            Component profession = villager.professionKey().isBlank()
                    ? Component.translatable("villagerretaliation.gui.profession.unemployed")
                    : Component.translatable(villager.professionKey());
            Component state = !villager.available()
                    ? Component.translatable("villagerretaliation.party.state.unavailable")
                    : Component.translatable(villager.commandMode() == com.jvn.villagerretaliation.party.PartyCommandMode.STAY
                            ? "villagerretaliation.party.state.staying"
                            : "villagerretaliation.party.state.following");
            lines.add(Component.translatable(
                    "villagerretaliation.party.roster.villager",
                    name,
                    profession,
                    state));
        }
        return lines;
    }

    private record CheckboxBounds(int left, int top, int right, int bottom) {
        boolean contains(double x, double y) {
            return x >= this.left && x < this.right && y >= this.top && y < this.bottom;
        }
    }

    private record Bounds(int left, int top, int width, int height) {
        int right() {
            return this.left + this.width;
        }

        boolean contains(double x, double y) {
            return x >= this.left && x < right() && y >= this.top && y < this.top + this.height;
        }
    }
}
