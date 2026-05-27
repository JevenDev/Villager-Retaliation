package com.jvn.villagerretaliation.client.interaction;

import com.jvn.villagerretaliation.client.VillagerRetaliationClientAssets;
import com.jvn.villagerretaliation.client.ui.VillagerClientUiUtil;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

final class VillagerInteractionGiftPage {
    private static final String GUI_KEY_PREFIX = "villagerretaliation.gui.";
    private static final int INVENTORY_COLUMNS = 9;
    private static final int INVENTORY_MAIN_ROWS = 3;
    private static final int INVENTORY_SLOT_SIZE = 18;
    private static final int INVENTORY_TEXTURE_WIDTH = 176;
    private static final int INVENTORY_TEXTURE_HEIGHT = 90;
    private static final int INVENTORY_SLOT_START_X = 7;
    private static final int INVENTORY_SLOT_START_Y = 7;
    private static final int INVENTORY_HOTBAR_Y = 65;
    private static final int INVENTORY_ITEM_OFFSET = 1;
    private static final int INVENTORY_BUTTON_WIDTH = 64;
    private static final int INVENTORY_BUTTON_HEIGHT = 18;
    private static final int INVENTORY_BUTTON_GAP = 4;
    private static final int GIFT_INFO_ICON_SIZE = 16;
    private static final int GIFT_INFO_ICON_GAP = 5;
    private static final int INVENTORY_LEFT_OFFSET = 10;

    private VillagerInteractionGiftPage() {
    }

    static void render(
            Context context,
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick,
            int optionsLeft,
            int conversationInfoTop) {
        int left = giftInventoryLeft(optionsLeft);
        int top = giftInventoryTop(conversationInfoTop);
        int hoveredSlot = giftSlotAt(mouseX, mouseY, left, top);

        renderGiftSlots(context, graphics, left, top, hoveredSlot);
        renderGiftInfoIcon(graphics, left, top);
        renderGiftButton(context, graphics, mouseX, mouseY, partialTick, left, top);

        ItemStack hoveredStack = context.stackForInventorySlot(hoveredSlot);
        if (isPointInsideGiftInfoIcon(mouseX, mouseY, left, top)) {
            renderGiftKnowledgeTooltip(context, graphics, mouseX, mouseY);
        } else if (!hoveredStack.isEmpty()) {
            graphics.renderTooltip(context.font(), hoveredStack, mouseX, mouseY);
        }
    }

    static boolean tryClick(Context context, double mouseX, double mouseY, int optionsLeft, int conversationInfoTop) {
        int left = giftInventoryLeft(optionsLeft);
        int top = giftInventoryTop(conversationInfoTop);
        int clickedSlot = giftSlotAt(mouseX, mouseY, left, top);
        if (clickedSlot >= 0) {
            ItemStack stack = context.stackForInventorySlot(clickedSlot);
            if (!stack.isEmpty()) {
                context.setSelectedInventorySlot(clickedSlot);
            }
            return true;
        }
        return false;
    }

    static int firstGiftableInventorySlot(Context context) {
        for (int slot = 0; slot < 36; slot++) {
            if (!context.stackForInventorySlot(slot).isEmpty()) {
                return slot;
            }
        }
        return -1;
    }

    static int giftInventoryTop(int conversationInfoTop) {
        return conversationInfoTop;
    }

    static int giftInventoryLeft(int optionsLeft) {
        return optionsLeft + INVENTORY_LEFT_OFFSET;
    }

    private static void renderGiftSlots(Context context, GuiGraphics graphics, int left, int top, int hoveredSlot) {
        graphics.blit(
                VillagerRetaliationClientAssets.GIFT_INVENTORY_TEXTURE,
                left,
                top,
                0,
                0,
                INVENTORY_TEXTURE_WIDTH,
                INVENTORY_TEXTURE_HEIGHT,
                INVENTORY_TEXTURE_WIDTH,
                INVENTORY_TEXTURE_HEIGHT
        );

        for (int row = 0; row < INVENTORY_MAIN_ROWS; row++) {
            for (int column = 0; column < INVENTORY_COLUMNS; column++) {
                int inventorySlot = 9 + row * INVENTORY_COLUMNS + column;
                renderGiftSlot(
                        context,
                        graphics,
                        inventorySlot,
                        left + INVENTORY_SLOT_START_X + column * INVENTORY_SLOT_SIZE,
                        top + INVENTORY_SLOT_START_Y + row * INVENTORY_SLOT_SIZE,
                        hoveredSlot
                );
            }
        }

        for (int column = 0; column < INVENTORY_COLUMNS; column++) {
            renderGiftSlot(
                    context,
                    graphics,
                    column,
                    left + INVENTORY_SLOT_START_X + column * INVENTORY_SLOT_SIZE,
                    top + INVENTORY_HOTBAR_Y,
                    hoveredSlot
            );
        }
    }

    private static void renderGiftSlot(Context context, GuiGraphics graphics, int inventorySlot, int x, int y, int hoveredSlot) {
        boolean selected = inventorySlot == context.selectedInventorySlot();
        boolean hovered = inventorySlot == hoveredSlot;
        ItemStack stack = context.stackForInventorySlot(inventorySlot);
        if (!stack.isEmpty()) {
            graphics.renderItem(stack, x + INVENTORY_ITEM_OFFSET, y + INVENTORY_ITEM_OFFSET);
            graphics.renderItemDecorations(context.font(), stack, x + INVENTORY_ITEM_OFFSET, y + INVENTORY_ITEM_OFFSET);
        }
        if (selected || hovered) {
            int color = selected ? 0x88EAE6DC : 0x55FFFFFF;
            graphics.fill(x + INVENTORY_ITEM_OFFSET, y + INVENTORY_ITEM_OFFSET, x + 16 + INVENTORY_ITEM_OFFSET, y + 16 + INVENTORY_ITEM_OFFSET, color);
        }
    }

    private static void renderGiftButton(Context context, GuiGraphics graphics, int mouseX, int mouseY, float partialTick, int left, int top) {
        Button giftButton = context.giftButton();
        if (giftButton == null) {
            return;
        }

        GiftButtonBounds bounds = giftButtonBounds(left, top);
        int selectedSlot = context.selectedInventorySlot();
        boolean enabled = selectedSlot >= 0 && !context.stackForInventorySlot(selectedSlot).isEmpty();
        giftButton.setPosition(bounds.left(), bounds.top());
        giftButton.setMessage(Component.translatable(giftButtonKey(context.stackForInventorySlot(selectedSlot))));
        giftButton.active = enabled;
        giftButton.visible = true;
        giftButton.render(graphics, mouseX, mouseY, partialTick);
    }

    private static String giftButtonKey(ItemStack selectedStack) {
        return selectedStack.getCount() > 1 ? GUI_KEY_PREFIX + "gift.give_stack" : GUI_KEY_PREFIX + "gift.give";
    }

    private static void renderGiftInfoIcon(GuiGraphics graphics, int left, int top) {
        GiftInfoIconBounds bounds = giftInfoIconBounds(left, top);
        graphics.blit(
                VillagerRetaliationClientAssets.GIFT_INFO_ICON_TEXTURE,
                bounds.left(),
                bounds.top(),
                0,
                0,
                GIFT_INFO_ICON_SIZE,
                GIFT_INFO_ICON_SIZE,
                GIFT_INFO_ICON_SIZE,
                GIFT_INFO_ICON_SIZE
        );
    }

    private static void renderGiftKnowledgeTooltip(Context context, GuiGraphics graphics, int mouseX, int mouseY) {
        List<Component> tooltip = new ArrayList<>();
        tooltip.add(Component.translatable(GUI_KEY_PREFIX + "gift.known_gifts").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.literal(context.professionName()).withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.empty());
        if (context.knownLikedGiftNames().isEmpty() && context.knownDislikedGiftNames().isEmpty()) {
            tooltip.add(Component.translatable(GUI_KEY_PREFIX + "gift.learn_more").withStyle(ChatFormatting.GRAY));
        } else {
            addGiftTooltipSection(tooltip, "gift.likes", context.knownLikedGiftNames(), ChatFormatting.GREEN);
            addGiftTooltipSection(tooltip, "gift.dislikes", context.knownDislikedGiftNames(), ChatFormatting.RED);
        }
        graphics.renderComponentTooltip(context.font(), tooltip, mouseX, mouseY);
    }

    private static void addGiftTooltipSection(List<Component> tooltip, String labelKey, List<String> giftNames, ChatFormatting color) {
        tooltip.add(Component.translatable(GUI_KEY_PREFIX + labelKey + "_header").withStyle(color));
        if (giftNames.isEmpty()) {
            tooltip.add(Component.translatable(GUI_KEY_PREFIX + "gift.unknown_indented").withStyle(ChatFormatting.GRAY));
            return;
        }
        for (String giftName : giftNames) {
            tooltip.add(Component.literal("  " + giftName).withStyle(color));
        }
    }

    private static int giftSlotAt(double mouseX, double mouseY, int left, int top) {
        int slotLeft = left + INVENTORY_SLOT_START_X;
        int mainTop = top + INVENTORY_SLOT_START_Y;
        if (VillagerClientUiUtil.containsExclusive(
                mouseX,
                mouseY,
                slotLeft,
                mainTop,
                slotLeft + INVENTORY_COLUMNS * INVENTORY_SLOT_SIZE,
                mainTop + INVENTORY_MAIN_ROWS * INVENTORY_SLOT_SIZE)) {
            int column = Mth.floor((mouseX - slotLeft) / INVENTORY_SLOT_SIZE);
            int row = Mth.floor((mouseY - mainTop) / INVENTORY_SLOT_SIZE);
            return 9 + row * INVENTORY_COLUMNS + column;
        }

        int hotbarTop = top + INVENTORY_HOTBAR_Y;
        if (VillagerClientUiUtil.containsExclusive(
                mouseX,
                mouseY,
                slotLeft,
                hotbarTop,
                slotLeft + INVENTORY_COLUMNS * INVENTORY_SLOT_SIZE,
                hotbarTop + INVENTORY_SLOT_SIZE)) {
            return Mth.floor((mouseX - slotLeft) / INVENTORY_SLOT_SIZE);
        }
        return -1;
    }

    private static boolean isPointInsideGiftInfoIcon(double mouseX, double mouseY, int left, int top) {
        GiftInfoIconBounds bounds = giftInfoIconBounds(left, top);
        return VillagerClientUiUtil.containsInclusive(mouseX, mouseY, bounds.left(), bounds.top(), bounds.right(), bounds.bottom());
    }

    private static GiftButtonBounds giftButtonBounds(int left, int top) {
        int buttonLeft = left + INVENTORY_TEXTURE_WIDTH - INVENTORY_BUTTON_WIDTH;
        int buttonTop = top - INVENTORY_BUTTON_HEIGHT - INVENTORY_BUTTON_GAP;
        return new GiftButtonBounds(buttonLeft, buttonTop, buttonLeft + INVENTORY_BUTTON_WIDTH, buttonTop + INVENTORY_BUTTON_HEIGHT);
    }

    private static GiftInfoIconBounds giftInfoIconBounds(int left, int top) {
        GiftButtonBounds giftButton = giftButtonBounds(left, top);
        int iconLeft = giftButton.left() - GIFT_INFO_ICON_GAP - GIFT_INFO_ICON_SIZE;
        int iconTop = giftButton.top() + (INVENTORY_BUTTON_HEIGHT - GIFT_INFO_ICON_SIZE) / 2;
        return new GiftInfoIconBounds(iconLeft, iconTop, iconLeft + GIFT_INFO_ICON_SIZE, iconTop + GIFT_INFO_ICON_SIZE);
    }

    interface Context {
        Font font();

        int selectedInventorySlot();

        void setSelectedInventorySlot(int slot);

        Button giftButton();

        ItemStack stackForInventorySlot(int inventorySlot);

        String professionName();

        List<String> knownLikedGiftNames();

        List<String> knownDislikedGiftNames();
    }

    private record GiftButtonBounds(int left, int top, int right, int bottom) {
    }

    private record GiftInfoIconBounds(int left, int top, int right, int bottom) {
    }
}
