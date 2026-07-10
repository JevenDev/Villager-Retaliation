package com.jvn.villagerretaliation.client.party;

import com.jvn.villagerretaliation.network.PartyRosterSyncPayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.ScreenEvent;

public final class PartyInventoryOverlay {
    private static final int PANEL_WIDTH = 172;
    private static final int LINE_HEIGHT = 10;

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
        int y = top;
        for (int i = 0; i < lines.size(); i++) {
            Component line = lines.get(i);
            String full = line.getString();
            String visible = font.plainSubstrByWidth(full, PANEL_WIDTH - 4);
            int color = i == 0 ? 0xFFE7C56A : 0xFFE5E5E5;
            graphics.drawString(font, visible, left, y, color, true);
            if (!visible.equals(full)
                    && event.getMouseX() >= left
                    && event.getMouseX() <= left + PANEL_WIDTH
                    && event.getMouseY() >= y
                    && event.getMouseY() < y + LINE_HEIGHT) {
                hovered = line;
            }
            y += LINE_HEIGHT;
        }
        if (hovered != null) {
            graphics.renderTooltip(font, hovered, event.getMouseX(), event.getMouseY());
        }
    }

    private static List<Component> lines(PartyRosterSyncPayload roster) {
        List<Component> lines = new ArrayList<>();
        int total = roster.players().size() + roster.villagers().size();
        lines.add(Component.translatable("villagerretaliation.party.roster.title", roster.leaderName(), total));
        lines.add(Component.translatable("villagerretaliation.party.roster.players", roster.players().size()));
        for (PartyRosterSyncPayload.PlayerEntry player : roster.players()) {
            if (player.leader()) {
                lines.add(Component.translatable("villagerretaliation.party.roster.player_leader", player.name()));
            } else if (!player.online()) {
                lines.add(Component.translatable("villagerretaliation.party.roster.player_offline", player.name()));
            } else {
                lines.add(Component.literal(player.name()));
            }
        }
        lines.add(Component.translatable("villagerretaliation.party.roster.villagers", roster.villagers().size()));
        for (PartyRosterSyncPayload.VillagerEntry villager : roster.villagers()) {
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
                    villager.name(),
                    profession,
                    state));
        }
        return lines;
    }
}
