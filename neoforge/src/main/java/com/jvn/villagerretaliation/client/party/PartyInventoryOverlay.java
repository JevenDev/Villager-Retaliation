package com.jvn.villagerretaliation.client.party;

import com.jvn.villagerretaliation.client.VillagerRetaliationClientAssets;
import com.jvn.villagerretaliation.client.duel.DuelInventoryClientState;
import com.jvn.villagerretaliation.client.inventory.VillagerInventoryUiRenderer;
import com.jvn.villagerretaliation.client.ui.ClientScreenArea;
import com.jvn.villagerretaliation.mixin.client.AbstractContainerScreenAccessor;
import com.jvn.villagerretaliation.mixin.client.ScreenInvoker;
import com.jvn.villagerretaliation.network.PartyActionRequestPayload;
import com.jvn.villagerretaliation.network.PartyRosterSyncPayload;
import com.jvn.villagerretaliation.party.PartyAttackMode;
import com.jvn.villagerretaliation.party.PartyAttackModeState;
import com.jvn.villagerretaliation.party.PartyCombatMode;
import com.jvn.villagerretaliation.party.PartyCombatModeState;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/** Draws and handles the tabs shared by the vanilla, party, and settings inventory views. */
public final class PartyInventoryOverlay {
    private static final int CONTAINER_WIDTH = 176;
    private static final int CONTAINER_HEIGHT = 166;
    private static final int VILLAGER_CONTAINER_WIDTH = 119;
    private static final int VILLAGER_CONTAINER_HEIGHT = 101;
    private static final int VILLAGER_CONTAINER_GAP = 2;
    private static final int MAX_VILLAGER_CONTAINERS = 4;
    private static final int PANEL_ARMOR_SLOT_X = 7;
    private static final int PANEL_ARMOR_SLOT_Y = 22;
    private static final int PANEL_HAND_SLOT_X = 76;
    private static final int PANEL_HAND_SLOT_Y = 76;
    private static final int PANEL_SLOT_SIZE = 18;
    private static final int PANEL_NAME_Y = 9;
    private static final int PANEL_NAME_HORIZONTAL_PADDING = 7;
    private static final int PANEL_STATS_CENTER_X = 94;
    private static final int PANEL_STATS_Y = 24;
    private static final int PANEL_ENTITY_LEFT = 26;
    private static final int PANEL_ENTITY_TOP = 23;
    private static final int PANEL_ENTITY_RIGHT = 75;
    private static final int PANEL_ENTITY_BOTTOM = 93;

    private static final int SMALL_TAB_WIDTH = 24;
    private static final int SMALL_TAB_HEIGHT = 24;
    private static final int LARGE_TAB_WIDTH = 28;
    private static final int LARGE_TAB_HEIGHT = 26;
    private static final int TAB_INSET = 4;
    private static final int TAB_ICON_SIZE = 18;
    private static final int SMALL_TAB_ICON_TOP = 3;
    private static final int LARGE_TAB_ICON_TOP = 4;
    private static final int VILLAGER_ICON_SIZE = 8;
    private static final int VILLAGER_ICON_START_X = 124;
    private static final int VILLAGER_ICON_Y = 35;
    private static final int VILLAGER_ICON_SPACING = 12;
    private static final int VILLAGER_ICON_COUNT = 4;
    private static final int PLAYER_ICON_SIZE = 8;
    private static final int PLAYER_ICON_START_X = 81;
    private static final int PLAYER_ICON_Y = 35;
    private static final int PLAYER_ICON_SPACING = 12;
    private static final int PLAYER_ICON_COUNT = 3;
    private static final int LEADER_ICON_X = 81;
    private static final int LEADER_ICON_Y = 8;
    private static final int LEADER_ICON_SIZE = 16;
    private static final int PARTY_TITLE_X = 101;
    private static final int PARTY_TITLE_Y = 12;
    private static final int ROSTER_SUMMARY_X = 98;
    private static final int ROSTER_SUMMARY_RIGHT = 170;
    private static final int ROSTER_PLAYERS_Y = 61;
    private static final int ROSTER_VILLAGERS_Y = 72;
    private static final int PARTY_PLAYER_CAPACITY = 4;
    private static final int SETTINGS_BUTTON_X = 8;
    private static final int SETTINGS_BUTTON_Y = 18;
    private static final int SETTINGS_BUTTON_WIDTH = 160;
    private static final int SETTINGS_BUTTON_HEIGHT = 20;
    private static final int SETTINGS_BUTTON_SPACING = 22;
    private static final int PUSH_BUTTON_SIZE = 6;
    private static final int PUSH_BUTTON_START_X = 82;
    private static final int PUSH_BUTTON_Y = 46;
    private static final int PUSH_BUTTON_SPACING = 12;
    private static final int PUSH_BUTTON_GROUP_GAP = 19;
    private static final int PUSH_BUTTON_FIRST_GROUP_SIZE = 3;
    private static final int PUSH_BUTTON_COUNT = 7;
    private static final Map<InventoryScreen, Page> PAGES = new WeakHashMap<>();
    private static Page preferredInventoryPage = Page.INVENTORY;

    private PartyInventoryOverlay() {
    }

    public static Page page(InventoryScreen screen) {
        return PAGES.computeIfAbsent(screen, ignored -> preferredInventoryPage);
    }

    public static void resetPreferredInventoryPage() {
        preferredInventoryPage = Page.INVENTORY;
    }

    /** Returns an open inventory to vanilla immediately after its party roster is cleared. */
    public static void resetOpenInventoryPage() {
        preferredInventoryPage = Page.INVENTORY;
        if (!(Minecraft.getInstance().screen instanceof InventoryScreen screen)) return;
        PAGES.remove(screen);
        screen.init(Minecraft.getInstance(), screen.width, screen.height);
    }

    public static boolean isCustomPage(InventoryScreen screen) {
        return page(screen) != Page.INVENTORY;
    }

    public static boolean showsSlot(InventoryScreen screen, Slot slot) {
        Page page = page(screen);
        if (page == Page.INVENTORY) return true;
        if (page == Page.SETTINGS || slot == null) return false;
        return screen.getMenu().slots.indexOf(slot) >= InventoryMenu.ARMOR_SLOT_START;
    }

    public static boolean showsSlotAt(InventoryScreen screen, int left, int top) {
        if (page(screen) == Page.SETTINGS) return false;
        return screen.getMenu().slots.stream()
                .anyMatch(slot -> slot.x == left && slot.y == top && showsSlot(screen, slot));
    }

    /** Areas extending beyond the vanilla inventory bounds that recipe viewers must avoid. */
    public static List<ClientScreenArea> recipeViewerExclusionAreas(InventoryScreen screen) {
        if (!tabsAvailable(screen)) {
            return List.of();
        }
        List<ClientScreenArea> areas = new ArrayList<>();
        for (Page tab : Page.values()) {
            Bounds bounds = bounds(screen, tab);
            areas.add(new ClientScreenArea(bounds.left(), bounds.top(), bounds.width(), bounds.height()));
        }
        if (page(screen) == Page.PARTY) {
            int count = villagerContainerCount();
            for (int index = 0; index < count; index++) {
                Bounds bounds = villagerContainerBounds(screen, count, index);
                areas.add(new ClientScreenArea(bounds.left(), bounds.top(), bounds.width(), bounds.height()));
            }
        }
        return List.copyOf(areas);
    }

    public static void renderTabsBehindContainer(GuiGraphics graphics, InventoryScreen screen) {
        if (!tabsAvailable(screen)) return;
        Page active = page(screen);
        for (Page tab : Page.values()) {
            if (tab != active) renderTab(graphics, screen, tab);
        }
    }

    public static void renderActiveTab(GuiGraphics graphics, InventoryScreen screen) {
        if (tabsAvailable(screen)) renderTab(graphics, screen, page(screen));
    }

    public static void renderTooltips(
            GuiGraphics graphics,
            InventoryScreen screen,
            int mouseX,
            int mouseY) {
        if (!tabsAvailable(screen)) return;
        Page tab = tabAt(screen, mouseX, mouseY);
        if (tab != null) {
            graphics.renderTooltip(
                    Minecraft.getInstance().font,
                    Component.translatable(tab.tooltipKey()),
                    mouseX,
                    mouseY);
            return;
        }
        if (page(screen) != Page.PARTY) return;
        PartyRosterSyncPayload.PlayerEntry leader = partyLeader();
        if (leader != null && leaderIconBounds(screen).contains(mouseX, mouseY)) {
            graphics.renderTooltip(
                    Minecraft.getInstance().font,
                    playerName(leader).copy().withStyle(ChatFormatting.AQUA),
                    mouseX,
                    mouseY);
            return;
        }
        int playerIndex = playerIconAt(screen, mouseX, mouseY);
        if (playerIndex >= 0) {
            graphics.renderTooltip(
                    Minecraft.getInstance().font,
                    playerName(partyMembers().get(playerIndex)).copy().withStyle(ChatFormatting.AQUA),
                    mouseX,
                    mouseY);
            return;
        }
        if (emptyPlayerIconAt(screen, mouseX, mouseY)) {
            graphics.renderTooltip(
                    Minecraft.getInstance().font,
                    Component.translatable("villagerretaliation.gui.party.available_player_slot")
                            .withStyle(ChatFormatting.GRAY),
                    mouseX,
                    mouseY);
            return;
        }
        RosterSummary summary = rosterSummaryAt(screen, mouseX, mouseY);
        if (summary != null) {
            renderRosterTooltip(graphics, summary, mouseX, mouseY);
            return;
        }
        int timerVillagerIndex = villagerTimerAt(screen, mouseX, mouseY);
        if (timerVillagerIndex >= 0) {
            long remainingTicks = remainingContractTicks(
                    PartyRosterClient.roster().villagers().get(timerVillagerIndex));
            VillagerInventoryUiRenderer.renderTimerStatTooltip(graphics, remainingTicks, mouseX, mouseY);
            return;
        }
        PanelSlot panelSlot = panelSlotAt(screen, mouseX, mouseY);
        if (panelSlot != null) {
            renderPanelSlotTooltip(graphics, panelSlot.stack(), mouseX, mouseY);
            return;
        }
        int villagerIndex = villagerNameAt(screen, mouseX, mouseY);
        if (villagerIndex < 0) villagerIndex = villagerIconAt(screen, mouseX, mouseY);
        if (villagerIndex >= 0) {
            renderVillagerIdentityTooltip(graphics, villagerIndex, mouseX, mouseY);
            return;
        }
        if (emptyVillagerIconAt(screen, mouseX, mouseY)) {
            graphics.renderTooltip(
                    Minecraft.getInstance().font,
                    Component.translatable("villagerretaliation.gui.party.available_villager_slot"),
                    mouseX,
                    mouseY);
            return;
        }
        int button = pushButtonAt(screen, mouseX, mouseY);
        if (button < 0) return;
        if (button < PUSH_BUTTON_FIRST_GROUP_SIZE) {
            var member = partyMembers().get(button);
            graphics.renderTooltip(
                    Minecraft.getInstance().font,
                    List.of(
                            Component.translatable("villagerretaliation.gui.party.admin_privileges"),
                            playerName(member),
                            Component.translatable("villagerretaliation.gui.party.remove_player_hint", playerName(member))
                                    .withStyle(ChatFormatting.GRAY)),
                    Optional.empty(),
                    mouseX,
                    mouseY);
            return;
        }
        boolean enabled = villagerQuickCommandsEnabled(button - PUSH_BUTTON_FIRST_GROUP_SIZE);
        String keyBase = enabled
                ? "villagerretaliation.party.quick_command.checkbox.enabled"
                : "villagerretaliation.party.quick_command.checkbox.disabled";
        graphics.renderTooltip(
                Minecraft.getInstance().font,
                List.of(
                        Component.translatable(keyBase + ".status"),
                        Component.translatable(keyBase + ".action")),
                Optional.empty(),
                mouseX,
                mouseY);
    }

    public static void renderCustomContainer(
            GuiGraphics graphics,
            InventoryScreen screen,
            float mouseX,
            float mouseY) {
        Page page = page(screen);
        renderTabsBehindContainer(graphics, screen);
        if (page == Page.PARTY) renderVillagerContainers(graphics, screen, mouseX, mouseY);
        graphics.blit(
                page == Page.SETTINGS
                        ? VillagerRetaliationClientAssets.PLAYER_PARTY_INVENTORY_SETTINGS_CONTAINER_TEXTURE
                        : VillagerRetaliationClientAssets.PLAYER_PARTY_INVENTORY_CONTAINER_TEXTURE,
                screen.getGuiLeft(), screen.getGuiTop(), 0, 0,
                CONTAINER_WIDTH, CONTAINER_HEIGHT, CONTAINER_WIDTH, CONTAINER_HEIGHT);
        if (page == Page.PARTY && Minecraft.getInstance().player != null) {
            InventoryScreen.renderEntityInInventoryFollowsMouse(
                    graphics,
                    screen.getGuiLeft() + 26,
                    screen.getGuiTop() + 8,
                    screen.getGuiLeft() + 75,
                    screen.getGuiTop() + 78,
                    30,
                    0.0625F,
                    mouseX,
                    mouseY,
                    Minecraft.getInstance().player);
        }
        if (page == Page.PARTY) {
            renderPartyHeader(graphics, screen);
            renderPlayerIcons(graphics, screen);
            renderVillagerIcons(graphics, screen);
            renderPushButtons(graphics, screen, mouseX, mouseY);
            renderRosterSummary(graphics, screen);
        }
        renderActiveTab(graphics, screen);
    }

    /**
     * Draws an empty container for every villager currently synchronized to the player's party.
     * The arrangement stays balanced as the party grows: one above, then a pair above, then the
     * side positions, matching the four-villager party limit.
     */
    private static void renderVillagerContainers(
            GuiGraphics graphics, InventoryScreen screen, float mouseX, float mouseY) {
        int count = villagerContainerCount();
        for (int index = 0; index < count; index++) {
            Bounds bounds = villagerContainerBounds(screen, count, index);
            graphics.blit(
                    VillagerRetaliationClientAssets.PLAYER_PARTY_INVENTORY_VILLAGER_CONTAINER_TEXTURE,
                    bounds.left(), bounds.top(), 0, 0,
                    VILLAGER_CONTAINER_WIDTH, VILLAGER_CONTAINER_HEIGHT,
                    VILLAGER_CONTAINER_WIDTH, VILLAGER_CONTAINER_HEIGHT);
            var entry = PartyRosterClient.roster().villagers().get(index);
            LivingEntity villager = partyVillager(index);
            if (villager != null) renderVillagerPreview(graphics, villager, bounds, mouseX, mouseY);
            renderVillagerEquipment(graphics, villager, bounds, mouseX, mouseY);
            VillagerInventoryUiRenderer.renderCenteredName(
                    graphics,
                    villagerName(entry),
                    bounds.left() + VILLAGER_CONTAINER_WIDTH / 2,
                    bounds.top() + PANEL_NAME_Y,
                    VILLAGER_CONTAINER_WIDTH - PANEL_NAME_HORIZONTAL_PADDING * 2);
            VillagerInventoryUiRenderer.renderStats(
                    graphics,
                    villager,
                    remainingContractTicks(entry),
                    bounds.left() + PANEL_STATS_CENTER_X,
                    bounds.top() + PANEL_STATS_Y);
        }
    }

    private static void renderVillagerPreview(
            GuiGraphics graphics, LivingEntity villager, Bounds panel, float mouseX, float mouseY) {
        VillagerInventoryUiRenderer.renderModel(
                graphics,
                villager,
                panel.left() + PANEL_ENTITY_LEFT,
                panel.top() + PANEL_ENTITY_TOP,
                panel.left() + PANEL_ENTITY_RIGHT,
                panel.top() + PANEL_ENTITY_BOTTOM,
                mouseX,
                mouseY);
    }
    private static void renderVillagerEquipment(
            GuiGraphics graphics, LivingEntity villager, Bounds panel, float mouseX, float mouseY) {
        for (int index = 0; index < VillagerInventoryUiRenderer.ARMOR_SLOTS.size(); index++) {
            VillagerInventoryUiRenderer.renderReadOnlyEquipmentSlot(
                    graphics,
                    villager,
                    VillagerInventoryUiRenderer.ARMOR_SLOTS.get(index),
                    panel.left() + PANEL_ARMOR_SLOT_X,
                    panel.top() + PANEL_ARMOR_SLOT_Y + index * PANEL_SLOT_SIZE,
                    mouseX,
                    mouseY);
        }
        VillagerInventoryUiRenderer.renderReadOnlyEquipmentSlot(
                graphics,
                villager,
                EquipmentSlot.MAINHAND,
                panel.left() + PANEL_HAND_SLOT_X,
                panel.top() + PANEL_HAND_SLOT_Y,
                mouseX,
                mouseY);
        VillagerInventoryUiRenderer.renderReadOnlyEquipmentSlot(
                graphics,
                villager,
                EquipmentSlot.OFFHAND,
                panel.left() + PANEL_HAND_SLOT_X + PANEL_SLOT_SIZE,
                panel.top() + PANEL_HAND_SLOT_Y,
                mouseX,
                mouseY);
    }
    private static long remainingContractTicks(PartyRosterSyncPayload.VillagerEntry entry) {
        Minecraft minecraft = Minecraft.getInstance();
        if (entry == null || minecraft.level == null || entry.contractEndGameTime() < 0L) {
            return -1L;
        }
        return Math.max(0L, entry.contractEndGameTime() - minecraft.level.getGameTime());
    }

    private static int villagerTimerAt(InventoryScreen screen, double mouseX, double mouseY) {
        int count = villagerContainerCount();
        for (int index = 0; index < count; index++) {
            if (partyVillager(index) == null) {
                continue;
            }
            Bounds panel = villagerContainerBounds(screen, count, index);
            long remainingTicks = remainingContractTicks(PartyRosterClient.roster().villagers().get(index));
            if (VillagerInventoryUiRenderer.isTimerStatHovered(
                    remainingTicks,
                    panel.left() + PANEL_STATS_CENTER_X,
                    panel.top() + PANEL_STATS_Y,
                    mouseX,
                    mouseY)) {
                return index;
            }
        }
        return -1;
    }
    private static PanelSlot panelSlotAt(InventoryScreen screen, double mouseX, double mouseY) {
        int count = villagerContainerCount();
        for (int index = 0; index < count; index++) {
            LivingEntity villager = partyVillager(index);
            Bounds panel = villagerContainerBounds(screen, count, index);
            for (int armorIndex = 0; armorIndex < VillagerInventoryUiRenderer.ARMOR_SLOTS.size(); armorIndex++) {
                Bounds bounds = new Bounds(
                        panel.left() + PANEL_ARMOR_SLOT_X,
                        panel.top() + PANEL_ARMOR_SLOT_Y + armorIndex * PANEL_SLOT_SIZE,
                        PANEL_SLOT_SIZE,
                        PANEL_SLOT_SIZE);
                if (bounds.contains(mouseX, mouseY)) {
                    return new PanelSlot(
                            bounds,
                            VillagerInventoryUiRenderer.equipmentItem(
                                    villager, VillagerInventoryUiRenderer.ARMOR_SLOTS.get(armorIndex)));
                }
            }
            Bounds mainHand = new Bounds(
                    panel.left() + PANEL_HAND_SLOT_X,
                    panel.top() + PANEL_HAND_SLOT_Y,
                    PANEL_SLOT_SIZE,
                    PANEL_SLOT_SIZE);
            if (mainHand.contains(mouseX, mouseY)) {
                return new PanelSlot(
                        mainHand,
                        VillagerInventoryUiRenderer.equipmentItem(villager, EquipmentSlot.MAINHAND));
            }
            Bounds offHand = new Bounds(
                    panel.left() + PANEL_HAND_SLOT_X + PANEL_SLOT_SIZE,
                    panel.top() + PANEL_HAND_SLOT_Y,
                    PANEL_SLOT_SIZE,
                    PANEL_SLOT_SIZE);
            if (offHand.contains(mouseX, mouseY)) {
                return new PanelSlot(
                        offHand,
                        VillagerInventoryUiRenderer.equipmentItem(villager, EquipmentSlot.OFFHAND));
            }
        }
        return null;
    }

    private static void renderPanelSlotTooltip(GuiGraphics graphics, ItemStack stack, int mouseX, int mouseY) {
        VillagerInventoryUiRenderer.renderItemTooltip(graphics, stack, mouseX, mouseY);
    }
    private static int villagerContainerCount() {
        return Math.min(MAX_VILLAGER_CONTAINERS, PartyRosterClient.roster().villagers().size());
    }

    private static LivingEntity partyVillager(int index) {
        var villagers = PartyRosterClient.roster().villagers();
        if (index < 0 || index >= villagers.size()) return null;
        var entry = villagers.get(index);
        return VillagerInventoryUiRenderer.resolveLivingEntity(entry.entityId(), entry.villagerId());
    }
    private static Bounds villagerContainerBounds(InventoryScreen screen, int count, int index) {
        if (usesScaleFourSideLayout()) {
            return scaleFourVillagerContainerBounds(screen, count, index);
        }
        int centeredLeft = screen.getGuiLeft() + (CONTAINER_WIDTH - VILLAGER_CONTAINER_WIDTH) / 2;
        int partyTabTop = screen.getGuiTop() - LARGE_TAB_HEIGHT + TAB_INSET - 1;
        int aboveTop = partyTabTop - VILLAGER_CONTAINER_GAP - VILLAGER_CONTAINER_HEIGHT;
        int pairLeft = screen.getGuiLeft()
                + (CONTAINER_WIDTH - (VILLAGER_CONTAINER_WIDTH * 2 + VILLAGER_CONTAINER_GAP)) / 2;
        int leftSide = screen.getGuiLeft() - VILLAGER_CONTAINER_WIDTH - VILLAGER_CONTAINER_GAP;
        int rightSide = screen.getGuiLeft() + CONTAINER_WIDTH + VILLAGER_CONTAINER_GAP;

        return switch (count) {
            case 1 -> new Bounds(centeredLeft, aboveTop, VILLAGER_CONTAINER_WIDTH, VILLAGER_CONTAINER_HEIGHT);
            case 2 -> new Bounds(
                    index == 0 ? pairLeft : pairLeft + VILLAGER_CONTAINER_WIDTH + VILLAGER_CONTAINER_GAP,
                    aboveTop,
                    VILLAGER_CONTAINER_WIDTH,
                    VILLAGER_CONTAINER_HEIGHT);
            case 3 -> switch (index) {
                case 0 -> new Bounds(
                        centeredLeft - VILLAGER_CONTAINER_WIDTH - VILLAGER_CONTAINER_GAP,
                        aboveTop,
                        VILLAGER_CONTAINER_WIDTH,
                        VILLAGER_CONTAINER_HEIGHT);
                case 1 -> new Bounds(centeredLeft, aboveTop, VILLAGER_CONTAINER_WIDTH, VILLAGER_CONTAINER_HEIGHT);
                default -> new Bounds(
                        centeredLeft + VILLAGER_CONTAINER_WIDTH + VILLAGER_CONTAINER_GAP,
                        aboveTop,
                        VILLAGER_CONTAINER_WIDTH,
                        VILLAGER_CONTAINER_HEIGHT);
            };
            default -> switch (index) {
                case 0 -> new Bounds(pairLeft, aboveTop, VILLAGER_CONTAINER_WIDTH, VILLAGER_CONTAINER_HEIGHT);
                case 1 -> new Bounds(
                        pairLeft + VILLAGER_CONTAINER_WIDTH + VILLAGER_CONTAINER_GAP,
                        aboveTop,
                        VILLAGER_CONTAINER_WIDTH,
                        VILLAGER_CONTAINER_HEIGHT);
                case 2 -> new Bounds(leftSide, partyTabTop, VILLAGER_CONTAINER_WIDTH, VILLAGER_CONTAINER_HEIGHT);
                default -> new Bounds(rightSide, partyTabTop, VILLAGER_CONTAINER_WIDTH, VILLAGER_CONTAINER_HEIGHT);
            };
        };
    }
    private static Bounds scaleFourVillagerContainerBounds(InventoryScreen screen, int count, int index) {
        int columns = count == 1 ? 1 : 2;
        int rows = (count + columns - 1) / columns;
        int groupWidth = columns * VILLAGER_CONTAINER_WIDTH + (columns - 1) * VILLAGER_CONTAINER_GAP;
        int groupHeight = rows * VILLAGER_CONTAINER_HEIGHT + (rows - 1) * VILLAGER_CONTAINER_GAP;
        int groupLeft = screen.getGuiLeft() - VILLAGER_CONTAINER_GAP - groupWidth;
        int groupTop = screen.getGuiTop() + (CONTAINER_HEIGHT - groupHeight) / 2;

        int row = index / columns;
        int column = index % columns;
        int panelsInRow = Math.min(columns, count - row * columns);
        int rowWidth = panelsInRow * VILLAGER_CONTAINER_WIDTH
                + (panelsInRow - 1) * VILLAGER_CONTAINER_GAP;
        int rowLeft = groupLeft + (groupWidth - rowWidth) / 2;
        return new Bounds(
                rowLeft + column * (VILLAGER_CONTAINER_WIDTH + VILLAGER_CONTAINER_GAP),
                groupTop + row * (VILLAGER_CONTAINER_HEIGHT + VILLAGER_CONTAINER_GAP),
                VILLAGER_CONTAINER_WIDTH,
                VILLAGER_CONTAINER_HEIGHT);
    }
    private static int scaleFourPlayerInventoryLeft(InventoryScreen screen, int count) {
        int columns = count == 1 ? 1 : 2;
        int panelGroupWidth = columns * VILLAGER_CONTAINER_WIDTH
                + (columns - 1) * VILLAGER_CONTAINER_GAP;
        int combinedWidth = panelGroupWidth + VILLAGER_CONTAINER_GAP + CONTAINER_WIDTH;
        int combinedLeft = (screen.width - combinedWidth) / 2;
        return combinedLeft + panelGroupWidth + VILLAGER_CONTAINER_GAP;
    }


    private static boolean usesScaleFourSideLayout() {
        return Minecraft.getInstance().options.guiScale().get() == 4;
    }


    public static void onMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.isCanceled()
                || event.getButton() != GLFW.GLFW_MOUSE_BUTTON_LEFT
                || !(event.getScreen() instanceof InventoryScreen screen)
                || !tabsAvailable(screen)) {
            return;
        }
        Page clicked = tabAt(screen, event.getMouseX(), event.getMouseY());
        if (clicked != null) {
            event.setCanceled(true);
            if (clicked != page(screen)) {
                playButtonSound();
                open(screen, clicked);
            }
            return;
        }
        if (page(screen) != Page.PARTY) return;
        int pushButton = pushButtonAt(screen, event.getMouseX(), event.getMouseY());
        if (pushButton < 0) return;
        event.setCanceled(true);
        if (pushButton < PUSH_BUTTON_FIRST_GROUP_SIZE) {
            if (net.minecraft.client.gui.screens.Screen.hasShiftDown()) {
                removePlayer(pushButton);
            } else {
                toggleAdminPrivileges(pushButton);
            }
        } else {
            toggleVillagerQuickCommands(pushButton - PUSH_BUTTON_FIRST_GROUP_SIZE);
        }
        playButtonSound();
    }

    private static void renderPartyHeader(GuiGraphics graphics, InventoryScreen screen) {
        PartyRosterSyncPayload.PlayerEntry leader = partyLeader();
        if (leader != null) {
            PlayerFaceRenderer.draw(
                    graphics,
                    PartyPlayerSkinResolver.resolve(leader.playerId(), leader.name()),
                    screen.getGuiLeft() + LEADER_ICON_X,
                    screen.getGuiTop() + LEADER_ICON_Y,
                    LEADER_ICON_SIZE);
        }
        graphics.drawString(
                Minecraft.getInstance().font,
                Component.translatable(
                        "villagerretaliation.gui.party.title",
                        PartyRosterClient.roster().leaderName()),
                screen.getGuiLeft() + PARTY_TITLE_X,
                screen.getGuiTop() + PARTY_TITLE_Y,
                0x404040,
                false);
    }

    private static void renderPlayerIcons(GuiGraphics graphics, InventoryScreen screen) {
        List<PartyRosterSyncPayload.PlayerEntry> players = partyMembers();
        int count = Math.min(PLAYER_ICON_COUNT, players.size());
        for (int index = 0; index < count; index++) {
            var player = players.get(index);
            PlayerFaceRenderer.draw(
                    graphics,
                    PartyPlayerSkinResolver.resolve(player.playerId(), player.name()),
                    screen.getGuiLeft() + PLAYER_ICON_START_X + index * PLAYER_ICON_SPACING,
                    screen.getGuiTop() + PLAYER_ICON_Y,
                    PLAYER_ICON_SIZE);
        }
    }

    private static int playerIconAt(InventoryScreen screen, double mouseX, double mouseY) {
        int count = Math.min(PLAYER_ICON_COUNT, partyMembers().size());
        for (int index = 0; index < count; index++) {
            if (playerIconBounds(screen, index).contains(mouseX, mouseY)) return index;
        }
        return -1;
    }

    private static boolean emptyPlayerIconAt(InventoryScreen screen, double mouseX, double mouseY) {
        int occupied = Math.min(PLAYER_ICON_COUNT, partyMembers().size());
        for (int index = occupied; index < PLAYER_ICON_COUNT; index++) {
            if (playerIconBounds(screen, index).contains(mouseX, mouseY)) return true;
        }
        return false;
    }

    private static Bounds playerIconBounds(InventoryScreen screen, int index) {
        return new Bounds(
                screen.getGuiLeft() + PLAYER_ICON_START_X + index * PLAYER_ICON_SPACING,
                screen.getGuiTop() + PLAYER_ICON_Y,
                PLAYER_ICON_SIZE,
                PLAYER_ICON_SIZE);
    }

    private static List<PartyRosterSyncPayload.PlayerEntry> partyMembers() {
        return PartyRosterClient.roster().players().stream()
                .filter(player -> !player.leader())
                .limit(PLAYER_ICON_COUNT)
                .toList();
    }

    private static PartyRosterSyncPayload.PlayerEntry partyLeader() {
        return PartyRosterClient.roster().players().stream()
                .filter(PartyRosterSyncPayload.PlayerEntry::leader)
                .findFirst()
                .orElse(null);
    }

    private static Bounds leaderIconBounds(InventoryScreen screen) {
        return new Bounds(
                screen.getGuiLeft() + LEADER_ICON_X,
                screen.getGuiTop() + LEADER_ICON_Y,
                LEADER_ICON_SIZE,
                LEADER_ICON_SIZE);
    }

    private static Component playerName(PartyRosterSyncPayload.PlayerEntry player) {
        return player.name().isBlank()
                ? Component.translatable("entity.minecraft.player")
                : Component.literal(player.name());
    }

    private static void renderRosterSummary(GuiGraphics graphics, InventoryScreen screen) {
        var roster = PartyRosterClient.roster();
        renderRosterSummaryLine(
                graphics,
                screen,
                Component.translatable("villagerretaliation.gui.party.summary.players"),
                roster.players().size() + "/" + PARTY_PLAYER_CAPACITY,
                ROSTER_PLAYERS_Y);
        renderRosterSummaryLine(
                graphics,
                screen,
                Component.translatable("villagerretaliation.gui.party.summary.villagers"),
                roster.villagers().size() + "/" + VILLAGER_ICON_COUNT,
                ROSTER_VILLAGERS_Y);
    }

    private static void renderRosterSummaryLine(
            GuiGraphics graphics, InventoryScreen screen, Component label, String count, int y) {
        var font = Minecraft.getInstance().font;
        graphics.drawString(
                font,
                label,
                screen.getGuiLeft() + ROSTER_SUMMARY_X,
                screen.getGuiTop() + y,
                0x404040,
                false);
        graphics.drawString(
                font,
                count,
                screen.getGuiLeft() + ROSTER_SUMMARY_RIGHT - font.width(count),
                screen.getGuiTop() + y,
                0x404040,
                false);
    }

    private static RosterSummary rosterSummaryAt(
            InventoryScreen screen, double mouseX, double mouseY) {
        int lineHeight = Minecraft.getInstance().font.lineHeight;
        Bounds players = new Bounds(
                screen.getGuiLeft() + ROSTER_SUMMARY_X,
                screen.getGuiTop() + ROSTER_PLAYERS_Y,
                ROSTER_SUMMARY_RIGHT - ROSTER_SUMMARY_X,
                lineHeight);
        if (players.contains(mouseX, mouseY)) return RosterSummary.PLAYERS;
        Bounds villagers = new Bounds(
                screen.getGuiLeft() + ROSTER_SUMMARY_X,
                screen.getGuiTop() + ROSTER_VILLAGERS_Y,
                ROSTER_SUMMARY_RIGHT - ROSTER_SUMMARY_X,
                lineHeight);
        return villagers.contains(mouseX, mouseY) ? RosterSummary.VILLAGERS : null;
    }

    private static void renderRosterTooltip(
            GuiGraphics graphics, RosterSummary summary, int mouseX, int mouseY) {
        var roster = PartyRosterClient.roster();
        List<Component> lines = new ArrayList<>();
        if (summary == RosterSummary.PLAYERS) {
            lines.add(Component.translatable("villagerretaliation.gui.party.summary.players")
                    .withStyle(ChatFormatting.AQUA));
            roster.players().stream().map(PartyInventoryOverlay::playerName).forEach(lines::add);
        } else {
            lines.add(Component.translatable("villagerretaliation.gui.party.summary.villagers")
                    .withStyle(ChatFormatting.GOLD));
            roster.villagers().stream().map(PartyInventoryOverlay::villagerName).forEach(lines::add);
        }
        graphics.renderTooltip(
                Minecraft.getInstance().font,
                lines,
                Optional.empty(),
                mouseX,
                mouseY);
    }

    private static void renderVillagerIcons(GuiGraphics graphics, InventoryScreen screen) {
        int count = Math.min(VILLAGER_ICON_COUNT, PartyRosterClient.roster().villagers().size());
        for (int index = 0; index < count; index++) {
            Bounds bounds = villagerIconBounds(screen, index);
            graphics.blit(
                    VillagerRetaliationClientAssets.PLAYER_PARTY_INVENTORY_VILLAGER_ICON,
                    bounds.left(), bounds.top(), 0, 0,
                    VILLAGER_ICON_SIZE, VILLAGER_ICON_SIZE,
                    VILLAGER_ICON_SIZE, VILLAGER_ICON_SIZE);
        }
    }

    private static int villagerNameAt(InventoryScreen screen, double mouseX, double mouseY) {
        int count = villagerContainerCount();
        for (int index = 0; index < count; index++) {
            Bounds panel = villagerContainerBounds(screen, count, index);
            Component name = villagerName(PartyRosterClient.roster().villagers().get(index));
            if (VillagerInventoryUiRenderer.isCenteredNameHovered(
                    name,
                    panel.left() + VILLAGER_CONTAINER_WIDTH / 2,
                    panel.top() + PANEL_NAME_Y,
                    VILLAGER_CONTAINER_WIDTH - PANEL_NAME_HORIZONTAL_PADDING * 2,
                    mouseX,
                    mouseY)) {
                return index;
            }
        }
        return -1;
    }

    private static void renderVillagerIdentityTooltip(
            GuiGraphics graphics, int villagerIndex, int mouseX, int mouseY) {
        var villager = PartyRosterClient.roster().villagers().get(villagerIndex);
        Component gender = villager.genderName().isBlank()
                ? Component.translatable("villagerretaliation.gui.gender.unknown")
                : Component.translatable("villagerretaliation.gui.gender." + villager.genderName());
        Component profession = villager.professionKey().isBlank()
                ? Component.translatable("villagerretaliation.gui.profession.unemployed")
                : Component.translatable(villager.professionKey());
        graphics.renderTooltip(
                Minecraft.getInstance().font,
                List.of(
                        villagerName(villager).copy().withStyle(ChatFormatting.GOLD),
                        gender.copy().withStyle(ChatFormatting.GRAY),
                        profession.copy().withStyle(ChatFormatting.GRAY)),
                Optional.empty(),
                mouseX,
                mouseY);
    }

    private static Component villagerName(PartyRosterSyncPayload.VillagerEntry villager) {
        return villager.name().isBlank()
                ? Component.translatable("entity.minecraft.villager")
                : Component.literal(villager.name());
    }

    private static int villagerIconAt(InventoryScreen screen, double mouseX, double mouseY) {
        int count = Math.min(VILLAGER_ICON_COUNT, PartyRosterClient.roster().villagers().size());
        for (int index = 0; index < count; index++) {
            if (villagerIconBounds(screen, index).contains(mouseX, mouseY)) return index;
        }
        return -1;
    }

    private static boolean emptyVillagerIconAt(InventoryScreen screen, double mouseX, double mouseY) {
        int occupied = Math.min(VILLAGER_ICON_COUNT, PartyRosterClient.roster().villagers().size());
        for (int index = occupied; index < VILLAGER_ICON_COUNT; index++) {
            if (villagerIconBounds(screen, index).contains(mouseX, mouseY)) return true;
        }
        return false;
    }

    private static Bounds villagerIconBounds(InventoryScreen screen, int index) {
        return new Bounds(
                screen.getGuiLeft() + VILLAGER_ICON_START_X + index * VILLAGER_ICON_SPACING,
                screen.getGuiTop() + VILLAGER_ICON_Y,
                VILLAGER_ICON_SIZE,
                VILLAGER_ICON_SIZE);
    }

    private static void renderPushButtons(
            GuiGraphics graphics,
            InventoryScreen screen,
            double mouseX,
            double mouseY) {
        for (int index = 0; index < PUSH_BUTTON_COUNT; index++) {
            Bounds bounds = pushButtonBounds(screen, index);
            boolean hovered = bounds.contains(mouseX, mouseY) && isPushButtonClickable(index);
            boolean pushed = index < PUSH_BUTTON_FIRST_GROUP_SIZE
                    ? playerAdminPrivileges(index)
                    : villagerQuickCommandsEnabled(index - PUSH_BUTTON_FIRST_GROUP_SIZE);
            var texture = pushed
                    ? (hovered
                            ? VillagerRetaliationClientAssets.PLAYER_PARTY_INVENTORY_PUSHED_BUTTON_HIGHLIGHTED
                            : VillagerRetaliationClientAssets.PLAYER_PARTY_INVENTORY_PUSHED_BUTTON)
                    : (hovered
                            ? VillagerRetaliationClientAssets.PLAYER_PARTY_INVENTORY_PUSH_BUTTON_HIGHLIGHTED
                            : VillagerRetaliationClientAssets.PLAYER_PARTY_INVENTORY_PUSH_BUTTON);
            graphics.blit(
                    texture,
                    bounds.left(), bounds.top(), 0, 0,
                    PUSH_BUTTON_SIZE, PUSH_BUTTON_SIZE, PUSH_BUTTON_SIZE, PUSH_BUTTON_SIZE);
        }
    }

    private static int pushButtonAt(InventoryScreen screen, double mouseX, double mouseY) {
        for (int index = 0; index < PUSH_BUTTON_COUNT; index++) {
            if (isPushButtonClickable(index)
                    && pushButtonBounds(screen, index).contains(mouseX, mouseY)) return index;
        }
        return -1;
    }

    private static boolean isPushButtonClickable(int index) {
        if (index < PUSH_BUTTON_FIRST_GROUP_SIZE) {
            return PartyRosterClient.roster().recipientLeader() && index < partyMembers().size();
        }
        return PartyRosterClient.hasAdminPrivileges()
                && index - PUSH_BUTTON_FIRST_GROUP_SIZE < PartyRosterClient.roster().villagers().size();
    }

    private static Bounds pushButtonBounds(InventoryScreen screen, int index) {
        int relativeX = index < PUSH_BUTTON_FIRST_GROUP_SIZE
                ? PUSH_BUTTON_START_X + index * PUSH_BUTTON_SPACING
                : PUSH_BUTTON_START_X
                        + (PUSH_BUTTON_FIRST_GROUP_SIZE - 1) * PUSH_BUTTON_SPACING
                        + PUSH_BUTTON_GROUP_GAP
                        + (index - PUSH_BUTTON_FIRST_GROUP_SIZE) * PUSH_BUTTON_SPACING;
        return new Bounds(
                screen.getGuiLeft() + relativeX,
                screen.getGuiTop() + PUSH_BUTTON_Y,
                PUSH_BUTTON_SIZE,
                PUSH_BUTTON_SIZE);
    }

    private static boolean villagerQuickCommandsEnabled(int villagerIndex) {
        var villagers = PartyRosterClient.roster().villagers();
        return villagerIndex >= 0
                && villagerIndex < villagers.size()
                && villagers.get(villagerIndex).quickCommandsEnabled();
    }

    private static void toggleVillagerQuickCommands(int villagerIndex) {
        var roster = PartyRosterClient.roster();
        if (!PartyRosterClient.hasAdminPrivileges()
                || villagerIndex < 0
                || villagerIndex >= roster.villagers().size()) {
            return;
        }
        var villager = roster.villagers().get(villagerIndex);
        PacketDistributor.sendToServer(new PartyActionRequestPayload(
                PartyActionRequestPayload.Action.SET_QUICK_COMMANDS_ENABLED,
                villager.villagerId(),
                null,
                !villager.quickCommandsEnabled()));
    }
    private static boolean playerAdminPrivileges(int playerIndex) {
        List<PartyRosterSyncPayload.PlayerEntry> members = partyMembers();
        return playerIndex >= 0
                && playerIndex < members.size()
                && members.get(playerIndex).adminPrivileges();
    }

    private static void toggleAdminPrivileges(int playerIndex) {
        var roster = PartyRosterClient.roster();
        List<PartyRosterSyncPayload.PlayerEntry> members = partyMembers();
        if (!roster.recipientLeader() || playerIndex < 0 || playerIndex >= members.size()) return;
        var member = members.get(playerIndex);
        PacketDistributor.sendToServer(new PartyActionRequestPayload(
                PartyActionRequestPayload.Action.SET_ADMIN_PRIVILEGES,
                member.playerId(),
                null,
                !member.adminPrivileges()));
    }

    private static void removePlayer(int playerIndex) {
        var roster = PartyRosterClient.roster();
        List<PartyRosterSyncPayload.PlayerEntry> members = partyMembers();
        if (!roster.recipientLeader() || playerIndex < 0 || playerIndex >= members.size()) return;
        PacketDistributor.sendToServer(new PartyActionRequestPayload(
                PartyActionRequestPayload.Action.REMOVE_PLAYER,
                members.get(playerIndex).playerId(),
                null));
    }

    private static void playButtonSound() {
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    private static void open(InventoryScreen screen, Page page) {
        Minecraft minecraft = Minecraft.getInstance();
        if (page != Page.SETTINGS) preferredInventoryPage = page;
        if (page == Page.INVENTORY) {
            PAGES.remove(screen);
            screen.init(minecraft, screen.width, screen.height);
            return;
        }

        PAGES.put(screen, page);
        reinitializeCustomPage(screen);
    }

    public static void reinitializeCustomPage(InventoryScreen screen) {
        if (!isCustomPage(screen)) return;
        RecipeBookComponent recipeBook = screen.getRecipeBookComponent();
        if (recipeBook.isVisible()) recipeBook.toggleVisibility();
        ((ScreenInvoker) (Object) screen).villagerretaliation$clearWidgets();
        screen.setFocused(null);
        int villagerCount = page(screen) == Page.PARTY ? villagerContainerCount() : 0;
        boolean scaleFourSideLayout = villagerCount > 0 && usesScaleFourSideLayout();
        int left = scaleFourSideLayout
                ? scaleFourPlayerInventoryLeft(screen, villagerCount)
                : (screen.width - CONTAINER_WIDTH) / 2;
        ((AbstractContainerScreenAccessor) (Object) screen).villagerretaliation$setLeftPos(
                left);
        int partyTabHeightAboveContainer = LARGE_TAB_HEIGHT - TAB_INSET + 1;
        int villagerContainerTopOffset = VILLAGER_CONTAINER_HEIGHT
                + VILLAGER_CONTAINER_GAP
                + partyTabHeightAboveContainer;
        int top = page(screen) == Page.PARTY
                && villagerContainerCount() > 0
                && !usesScaleFourSideLayout()
                ? (screen.height - (villagerContainerTopOffset + CONTAINER_HEIGHT)) / 2
                        + villagerContainerTopOffset
                : (screen.height - CONTAINER_HEIGHT) / 2;
        ((AbstractContainerScreenAccessor) (Object) screen).villagerretaliation$setTopPos(top);
        if (page(screen) == Page.SETTINGS) addSettingsWidgets(screen);
    }

    private static void addSettingsWidgets(InventoryScreen screen) {
        var roster = PartyRosterClient.roster();
        if (!roster.active()) return;
        boolean adminPrivileges = PartyRosterClient.hasAdminPrivileges();
        ScreenInvoker invoker = (ScreenInvoker) (Object) screen;
        int left = screen.getGuiLeft() + SETTINGS_BUTTON_X;
        int top = screen.getGuiTop() + SETTINGS_BUTTON_Y;

        if (adminPrivileges) {
            PartyCombatModeState[] combatMode = {roster.combatMode()};
            invoker.villagerretaliation$addRenderableWidget(Button.builder(
                    combatModeLabel(combatMode[0]),
                    button -> {
                        PartyCombatMode next = combatMode[0].nextMode();
                        combatMode[0] = PartyCombatModeState.of(next);
                        button.setMessage(combatModeLabel(combatMode[0]));
                        PacketDistributor.sendToServer(new PartyActionRequestPayload(
                                PartyActionRequestPayload.Action.SET_COMBAT_MODE,
                                null, null, false, null, next));
                    })
                    .bounds(left, top, SETTINGS_BUTTON_WIDTH, SETTINGS_BUTTON_HEIGHT)
                    .tooltip(Tooltip.create(Component.translatable(
                            combatMode[0] == PartyCombatModeState.CUSTOM
                                    ? "villagerretaliation.party.manage.combat_mode.custom.tooltip"
                                    : "villagerretaliation.party.manage.combat_mode.tooltip")))
                    .build());

            PartyAttackModeState[] attackMode = {roster.attackMode()};
            invoker.villagerretaliation$addRenderableWidget(Button.builder(
                    attackModeLabel(attackMode[0]),
                    button -> {
                        PartyAttackMode next = attackMode[0].nextMode();
                        attackMode[0] = PartyAttackModeState.of(next);
                        button.setMessage(attackModeLabel(attackMode[0]));
                        PacketDistributor.sendToServer(new PartyActionRequestPayload(
                                PartyActionRequestPayload.Action.SET_ATTACK_MODE,
                                null, null, false, next, null));
                    })
                    .bounds(left, top + SETTINGS_BUTTON_SPACING,
                            SETTINGS_BUTTON_WIDTH, SETTINGS_BUTTON_HEIGHT)
                    .tooltip(Tooltip.create(Component.translatable(
                            attackMode[0] == PartyAttackModeState.CUSTOM
                                    ? "villagerretaliation.party.manage.attack_mode.custom.tooltip"
                                    : "villagerretaliation.party.manage.attack_mode.tooltip")))
                    .build());

            boolean[] friendlyFireAllowed = {roster.friendlyFireAllowed()};
            invoker.villagerretaliation$addRenderableWidget(Button.builder(
                    friendlyFireLabel(friendlyFireAllowed[0]),
                    button -> {
                        friendlyFireAllowed[0] = !friendlyFireAllowed[0];
                        button.setMessage(friendlyFireLabel(friendlyFireAllowed[0]));
                        PacketDistributor.sendToServer(new PartyActionRequestPayload(
                                PartyActionRequestPayload.Action.SET_FRIENDLY_FIRE_ALLOWED,
                                null, null, friendlyFireAllowed[0]));
                    })
                    .bounds(left, top + SETTINGS_BUTTON_SPACING * 2,
                            SETTINGS_BUTTON_WIDTH, SETTINGS_BUTTON_HEIGHT)
                    .tooltip(Tooltip.create(Component.translatable(
                            "villagerretaliation.party.manage.friendly_fire.tooltip")))
                    .build());


        }

        Component exitLabel = Component.translatable(roster.recipientLeader()
                ? "villagerretaliation.party.action.disband"
                : "villagerretaliation.party.action.leave_group");
        invoker.villagerretaliation$addRenderableWidget(Button.builder(
                exitLabel,
                button -> {
                    if (roster.recipientLeader()) {
                        Minecraft.getInstance().setScreen(new PartyDisbandConfirmationScreen(screen));
                    } else {
                        PacketDistributor.sendToServer(new PartyActionRequestPayload(
                                PartyActionRequestPayload.Action.LEAVE_PARTY));
                        Minecraft.getInstance().setScreen(null);
                    }
                })
                .bounds(
                        screen.getGuiLeft() + 24,
                        top + SETTINGS_BUTTON_SPACING * 3 + 2,
                        128,
                        SETTINGS_BUTTON_HEIGHT)
                .build());
    }
    private static Component combatModeLabel(PartyCombatModeState state) {
        return settingLabel(
                "villagerretaliation.party.manage.combat_mode",
                "villagerretaliation.party.combat_mode." + state.name().toLowerCase(java.util.Locale.ROOT));
    }

    private static Component attackModeLabel(PartyAttackModeState state) {
        return settingLabel(
                "villagerretaliation.party.manage.attack_mode",
                "villagerretaliation.party.attack_mode." + state.name().toLowerCase(java.util.Locale.ROOT));
    }

    private static Component friendlyFireLabel(boolean allowed) {
        return settingLabel(
                "villagerretaliation.party.manage.friendly_fire",
                "villagerretaliation.gui.party.setting." + (allowed ? "on" : "off"));
    }

    private static Component settingLabel(String labelKey, String valueKey) {
        return Component.translatable(
                "villagerretaliation.party.manage.setting",
                Component.translatable(labelKey),
                Component.translatable(valueKey));
    }

    public static int effectListTopOffset(InventoryScreen screen) {
        if (!PartyRosterClient.roster().active() || isCustomPage(screen)) return 0;
        return -(LARGE_TAB_HEIGHT - TAB_INSET + 1);
    }

    private static boolean tabsAvailable(InventoryScreen screen) {
        return !DuelInventoryClientState.assignedLoadout()
                && (isCustomPage(screen) || PartyRosterClient.roster().active());
    }

    private static Page tabAt(InventoryScreen screen, double mouseX, double mouseY) {
        Page[] tabs = Page.values();
        for (int index = tabs.length - 1; index >= 0; index--) {
            Page tab = tabs[index];
            if (bounds(screen, tab).contains(mouseX, mouseY)) return tab;
        }
        return null;
    }

    private static void renderTab(GuiGraphics graphics, InventoryScreen screen, Page tab) {
        Bounds bounds = bounds(screen, tab);
        graphics.blit(
                tab == Page.PARTY
                        ? VillagerRetaliationClientAssets.PLAYER_PARTY_INVENTORY_LARGE_TAB_TEXTURE
                        : VillagerRetaliationClientAssets.PLAYER_PARTY_INVENTORY_SMALL_TAB_TEXTURE,
                bounds.left(), bounds.top(), 0, 0,
                bounds.width(), bounds.height(), bounds.width(), bounds.height());

        int activeOffset = tab == page(screen) ? (tab == Page.PARTY ? 2 : 1) : 0;
        int iconLeft = bounds.left() + (bounds.width() - TAB_ICON_SIZE) / 2;
        int iconTop = bounds.top()
                + (tab == Page.PARTY ? LARGE_TAB_ICON_TOP : SMALL_TAB_ICON_TOP)
                + activeOffset;
        graphics.blit(
                switch (tab) {
                    case SETTINGS -> VillagerRetaliationClientAssets.PLAYER_PARTY_INVENTORY_SETTINGS_TAB_ICON;
                    case INVENTORY -> VillagerRetaliationClientAssets.PLAYER_PARTY_INVENTORY_INVENTORY_TAB_ICON;
                    case PARTY -> VillagerRetaliationClientAssets.PLAYER_PARTY_INVENTORY_PARTY_TAB_ICON;
                },
                iconLeft, iconTop, 0, 0,
                TAB_ICON_SIZE, TAB_ICON_SIZE, TAB_ICON_SIZE, TAB_ICON_SIZE);
    }

    private static Bounds bounds(InventoryScreen screen, Page tab) {
        int partyLeft = screen.getGuiLeft() + CONTAINER_WIDTH - LARGE_TAB_WIDTH;
        int smallTop = screen.getGuiTop() - SMALL_TAB_HEIGHT + TAB_INSET - 1;
        return switch (tab) {
            case SETTINGS -> new Bounds(
                    partyLeft - SMALL_TAB_WIDTH * 2 + 2, smallTop, SMALL_TAB_WIDTH, SMALL_TAB_HEIGHT);
            case INVENTORY -> new Bounds(
                    partyLeft - SMALL_TAB_WIDTH + 1, smallTop, SMALL_TAB_WIDTH, SMALL_TAB_HEIGHT);
            case PARTY -> new Bounds(
                    partyLeft, screen.getGuiTop() - LARGE_TAB_HEIGHT + TAB_INSET - 1,
                    LARGE_TAB_WIDTH, LARGE_TAB_HEIGHT);
        };
    }

    public enum Page {
        SETTINGS("villagerretaliation.gui.party.tab.settings.tooltip"),
        INVENTORY("villagerretaliation.gui.party.tab.inventory.tooltip"),
        PARTY("villagerretaliation.gui.party.tab.party.tooltip");

        private final String tooltipKey;

        Page(String tooltipKey) {
            this.tooltipKey = tooltipKey;
        }

        private String tooltipKey() {
            return this.tooltipKey;
        }
    }

    private enum RosterSummary {
        PLAYERS,
        VILLAGERS
    }

    private record PanelSlot(Bounds bounds, ItemStack stack) {
    }

    private record Bounds(int left, int top, int width, int height) {
        boolean contains(double x, double y) {
            return x >= this.left && x < this.left + this.width
                    && y >= this.top && y < this.top + this.height;
        }
    }
}
