package com.jvn.villagerretaliation.client.party;

import com.jvn.villagerretaliation.network.PartyRosterSyncPayload;
import com.jvn.villagerretaliation.network.PartyActionRequestPayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

public final class PartyInventoryOverlay {
    private static final int PANEL_WIDTH = 172;
    private static final int LINE_HEIGHT = 10;
    private static final int CHECKBOX_SIZE = 8;

    private PartyInventoryOverlay() {
    }

    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof InventoryScreen) || !PartyRosterClient.roster().active()) {
            return;
        }
        int left = (event.getScreen().width - 176) / 2;
        int top = (event.getScreen().height - 166) / 2;
        event.addListener(Button.builder(
                Component.translatable("villagerretaliation.party.action.manage"),
                button -> Minecraft.getInstance().setScreen(new PartyManagementScreen(event.getScreen())))
                .bounds(left + 180, top, 58, 18)
                .build());
    }

    public static void onScreenRender(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof InventoryScreen)) {
            return;
        }
        PartyRosterSyncPayload roster = PartyRosterClient.roster();
        if (!roster.active()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        List<Component> lines = lines(roster);
        int panelHeight = 8 + lines.size() * LINE_HEIGHT;
        int inventoryLeft = (event.getScreen().width - 176) / 2;
        int inventoryTop = (event.getScreen().height - 166) / 2;
        int left = inventoryLeft;
        int top = Math.max(2, inventoryTop - panelHeight - 4);
        GuiGraphics graphics = event.getGuiGraphics();
        graphics.fill(left - 3, top - 3, left + PANEL_WIDTH + 3, top + panelHeight, 0xCC101216);

        Component hovered = null;
        Component hoveredCheckbox = null;
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
                if (bounds.contains(event.getMouseX(), event.getMouseY())) {
                    hoveredCheckbox = Component.translatable(
                            villager.quickCommandsEnabled()
                                    ? "villagerretaliation.party.quick_command.checkbox.enabled"
                                    : "villagerretaliation.party.quick_command.checkbox.disabled");
                }
            }
            String full = line.getString();
            String visible = font.plainSubstrByWidth(full, availableWidth);
            int color = i == 0 ? 0xFFE7C56A : 0xFFE5E5E5;
            graphics.drawString(font, visible, textX, y, color, true);
            if (!visible.equals(full)
                    && event.getMouseX() >= textX
                    && event.getMouseX() <= left + PANEL_WIDTH
                    && event.getMouseY() >= y
                    && event.getMouseY() < y + LINE_HEIGHT) {
                hovered = line;
            }
            y += LINE_HEIGHT;
        }
        Component tooltip = hoveredCheckbox == null ? hovered : hoveredCheckbox;
        if (tooltip != null) {
            graphics.renderTooltip(font, tooltip, event.getMouseX(), event.getMouseY());
        }
    }

    public static void onMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.isCanceled()
                || event.getButton() != GLFW.GLFW_MOUSE_BUTTON_LEFT
                || !(event.getScreen() instanceof InventoryScreen)) {
            return;
        }
        PartyRosterSyncPayload roster = PartyRosterClient.roster();
        if (!roster.active() || !roster.recipientLeader()) {
            return;
        }
        int panelHeight = 8 + lines(roster).size() * LINE_HEIGHT;
        int inventoryLeft = (event.getScreen().width - 176) / 2;
        int inventoryTop = (event.getScreen().height - 166) / 2;
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
}
