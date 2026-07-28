package com.jvn.villagerretaliation.client.interaction;

import com.jvn.villagerretaliation.client.VillagerRetaliationClientAssets;
import com.jvn.villagerretaliation.client.ui.VillagerClientUiUtil;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.interaction.VillagerGiftKnowledgeService.GiftTooltipReaction;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

final class VillagerInteractionGiftPage {
    private static final String GUI_KEY_PREFIX = "villagerretaliation.gui.";
    private static final int INVENTORY_COLUMNS = 9;
    private static final int INVENTORY_MAIN_ROWS = 3;
    private static final int INVENTORY_SLOT_SIZE = 18;
    private static final int INVENTORY_TEXTURE_WIDTH = 176;
    private static final int INVENTORY_TEXTURE_HEIGHT = 116;
    private static final int SCREEN_EDGE_PADDING = 12;
    private static final int INVENTORY_SLOT_START_X = 7;
    private static final int INVENTORY_SLOT_START_Y = 33;
    private static final int INVENTORY_HOTBAR_Y = 91;
    private static final int INVENTORY_ITEM_OFFSET = 1;
    private static final int INVENTORY_BUTTON_WIDTH = 112;
    private static final int INVENTORY_BUTTON_HEIGHT = 20;
    private static final int GIFT_PREVIEW_SLOT_X = 34;
    private static final int GIFT_PREVIEW_SLOT_Y = 9;
    private static final int GIFT_INFO_ICON_WIDTH = 24;
    private static final int GIFT_INFO_ICON_HEIGHT = 22;
    private VillagerInteractionGiftPage() {
    }

    static void render(
            Context context,
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick,
            int screenWidth,
            int screenHeight) {
        GiftTransform transform = giftTransform(screenWidth, screenHeight);
        double localMouseX = transform.localX(mouseX);
        double localMouseY = transform.localY(mouseY);
        int localMouseXi = Mth.floor(localMouseX);
        int localMouseYi = Mth.floor(localMouseY);
        int hoveredSlot = giftSlotAt(localMouseX, localMouseY, 0, 0);
        boolean previewHovered = isPointInsideGiftPreview(localMouseX, localMouseY, 0, 0);

        graphics.pose().pushPose();
        graphics.pose().translate(transform.left(), transform.top(), 0.0F);
        graphics.pose().scale(transform.scale(), transform.scale(), 1.0F);

        renderGiftSlots(context, graphics, 0, 0, hoveredSlot);
        renderGiftPreview(context, graphics, 0, 0, previewHovered);
        renderGiftInfoIcon(graphics, 0, 0);
        renderGiftButton(context, graphics, localMouseXi, localMouseYi, partialTick, 0, 0);

        ItemStack hoveredStack = previewHovered
                ? selectedGiftStack(context)
                : context.stackForInventorySlot(hoveredSlot);
        if (isPointInsideGiftInfoIcon(localMouseX, localMouseY, 0, 0)) {
            renderGiftKnowledgeTooltip(context, graphics, localMouseXi, localMouseYi, transform.scale(), transform.left(), transform.top());
        } else if (!hoveredStack.isEmpty()) {
            renderGiftItemTooltip(context, graphics, hoveredStack, localMouseXi, localMouseYi, transform.scale(), transform.left(), transform.top());
        }

        graphics.pose().popPose();
    }

    private static void renderGiftItemTooltip(Context context, GuiGraphics graphics, ItemStack stack, int mouseX, int mouseY, float scale, int originX, int originY) {
        List<Component> tooltip = new ArrayList<>(VillagerInteractionUiUtil.itemTooltipLines(stack));
        if (VillagerRetaliationConfig.SHOW_GIFT_REACTION_TOOLTIP.get()) {
            context.giftTooltipReaction(stack)
                    .filter(reaction -> !VillagerRetaliationConfig.GIFT_REACTION_TOOLTIP_REQUIRES_KNOWN_GIFT.get() || reaction.known())
                    .ifPresent(reaction -> tooltip.add(giftReactionTooltip(reaction)));
        }
        VillagerInteractionUiUtil.renderBoundedComponentTooltipInCurrentPose(
                graphics, context.font(), tooltip, mouseX, mouseY, scale, originX, originY);
    }

    private static Component giftReactionTooltip(GiftTooltipReaction reaction) {
        ChatFormatting color = switch (reaction.reaction()) {
            case LOVED -> ChatFormatting.GREEN;
            case LIKED -> ChatFormatting.DARK_GREEN;
            case NEUTRAL -> ChatFormatting.GRAY;
            case DISLIKED -> ChatFormatting.RED;
            case HATED -> ChatFormatting.DARK_RED;
        };
        String reactionKey = GUI_KEY_PREFIX + "gift.reaction." + reaction.reaction().name().toLowerCase(Locale.ROOT);
        return Component.translatable(
                GUI_KEY_PREFIX + "gift.reaction",
                Component.translatable(reactionKey).withStyle(color)).withStyle(color);
    }

    static boolean tryClick(
            Context context,
            double mouseX,
            double mouseY,
            int screenWidth,
            int screenHeight) {
        GiftTransform transform = giftTransform(screenWidth, screenHeight);
        double localMouseX = transform.localX(mouseX);
        double localMouseY = transform.localY(mouseY);
        int clickedSlot = giftSlotAt(localMouseX, localMouseY, 0, 0);
        if (clickedSlot >= 0) {
            ItemStack stack = context.stackForInventorySlot(clickedSlot);
            if (!stack.isEmpty()) {
                context.setSelectedInventorySlot(clickedSlot);
            }
            return true;
        }
        if (isPointInsideGiftPreview(localMouseX, localMouseY, 0, 0)) {
            return true;
        }
        if (tryClickGiftButton(context, localMouseX, localMouseY)) {
            return true;
        }
        return false;
    }

    static boolean tryScroll(
            Context context,
            double mouseX,
            double mouseY,
            double scrollY,
            int screenWidth,
            int screenHeight) {
        if (scrollY == 0.0D) {
            return false;
        }
        GiftTransform transform = giftTransform(screenWidth, screenHeight);
        if (!isPointInsideGiftPreview(transform.localX(mouseX), transform.localY(mouseY), 0, 0)) {
            return false;
        }
        int step = net.minecraft.client.gui.screens.Screen.hasControlDown()
                && net.minecraft.client.gui.screens.Screen.hasShiftDown()
                ? 100
                : net.minecraft.client.gui.screens.Screen.hasControlDown()
                ? 10
                : net.minecraft.client.gui.screens.Screen.hasShiftDown() ? 5 : 1;
        context.adjustSelectedGiftAmount(scrollY > 0.0D ? step : -step);
        return true;
    }

    static int firstGiftableInventorySlot(Context context) {
        for (int slot = 0; slot < 36; slot++) {
            if (!context.stackForInventorySlot(slot).isEmpty()) {
                return slot;
            }
        }
        return -1;
    }

    static int giftInventoryLeft(int screenWidth) {
        return Math.max(SCREEN_EDGE_PADDING, screenWidth - INVENTORY_TEXTURE_WIDTH - SCREEN_EDGE_PADDING);
    }

    static int giftInventoryTop(int screenHeight) {
        return Math.max(SCREEN_EDGE_PADDING, (screenHeight - INVENTORY_TEXTURE_HEIGHT) / 2);
    }

    private static GiftTransform giftTransform(int screenWidth, int screenHeight) {
        return new GiftTransform(giftInventoryLeft(screenWidth), giftInventoryTop(screenHeight), 1.0F);
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

    private static void renderGiftPreview(Context context, GuiGraphics graphics, int left, int top, boolean hovered) {
        int x = left + GIFT_PREVIEW_SLOT_X + INVENTORY_ITEM_OFFSET;
        int y = top + GIFT_PREVIEW_SLOT_Y + INVENTORY_ITEM_OFFSET;
        ItemStack stack = selectedGiftStack(context);
        if (!stack.isEmpty()) {
            graphics.renderItem(stack, x, y);
            graphics.renderItemDecorations(context.font(), stack, x, y);
        }
        if (hovered) {
            AbstractContainerScreen.renderSlotHighlight(graphics, x, y, 0);
        }
    }

    private static ItemStack selectedGiftStack(Context context) {
        ItemStack selected = context.stackForInventorySlot(context.selectedInventorySlot());
        if (selected.isEmpty()) {
            return ItemStack.EMPTY;
        }
        return selected.copyWithCount(Mth.clamp(context.selectedGiftAmount(), 1, selected.getCount()));
    }

    private static void renderGiftSlot(Context context, GuiGraphics graphics, int inventorySlot, int x, int y, int hoveredSlot) {
        boolean selected = inventorySlot == context.selectedInventorySlot();
        boolean hovered = inventorySlot == hoveredSlot;
        ItemStack stack = context.stackForInventorySlot(inventorySlot);
        if (selected) {
            graphics.fill(x + INVENTORY_ITEM_OFFSET, y + INVENTORY_ITEM_OFFSET, x + 16 + INVENTORY_ITEM_OFFSET, y + 16 + INVENTORY_ITEM_OFFSET, 0x8832D74B);
        }
        if (!stack.isEmpty()) {
            graphics.renderItem(stack, x + INVENTORY_ITEM_OFFSET, y + INVENTORY_ITEM_OFFSET);
            graphics.renderItemDecorations(context.font(), stack, x + INVENTORY_ITEM_OFFSET, y + INVENTORY_ITEM_OFFSET);
        }
        if (hovered && !selected) {
            AbstractContainerScreen.renderSlotHighlight(graphics, x + INVENTORY_ITEM_OFFSET, y + INVENTORY_ITEM_OFFSET, 0);
        }
    }

    private static void renderGiftButton(Context context, GuiGraphics graphics, int mouseX, int mouseY, float partialTick, int left, int top) {
        Button giftButton = context.giftButton();
        if (giftButton == null) {
            return;
        }

        updateGiftButton(context, left, top);
        giftButton.render(graphics, mouseX, mouseY, partialTick);
    }

    private static boolean tryClickGiftButton(Context context, double mouseX, double mouseY) {
        Button giftButton = context.giftButton();
        if (giftButton == null) {
            return false;
        }

        updateGiftButton(context, 0, 0);
        return giftButton.mouseClicked(mouseX, mouseY, GLFW.GLFW_MOUSE_BUTTON_LEFT);
    }

    private static void updateGiftButton(Context context, int left, int top) {
        Button giftButton = context.giftButton();
        if (giftButton == null) {
            return;
        }

        GiftButtonBounds bounds = giftButtonBounds(left, top);
        context.adjustSelectedGiftAmount(0);
        int selectedSlot = context.selectedInventorySlot();
        boolean enabled = selectedSlot >= 0 && !context.stackForInventorySlot(selectedSlot).isEmpty();
        giftButton.setPosition(bounds.left(), bounds.top());
        giftButton.setWidth(bounds.width());
        giftButton.setHeight(bounds.height());
        giftButton.setMessage(Component.translatable(giftButtonKey(context.selectedGiftAmount())));
        giftButton.active = enabled;
        giftButton.visible = true;
    }

    private static String giftButtonKey(int amount) {
        return amount > 1 ? GUI_KEY_PREFIX + "gift.give_stack" : GUI_KEY_PREFIX + "gift.give";
    }

    private static void renderGiftInfoIcon(GuiGraphics graphics, int left, int top) {
        GiftInfoIconBounds bounds = giftInfoIconBounds(left, top);
        graphics.blit(
                VillagerRetaliationClientAssets.GIFT_INFO_ICON_TEXTURE,
                bounds.left(),
                bounds.top(),
                0,
                0,
                GIFT_INFO_ICON_WIDTH,
                GIFT_INFO_ICON_HEIGHT,
                GIFT_INFO_ICON_WIDTH,
                GIFT_INFO_ICON_HEIGHT
        );
    }

    private static void renderGiftKnowledgeTooltip(
            Context context,
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float scale,
            int originX,
            int originY) {
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
        VillagerInteractionUiUtil.renderBoundedComponentTooltipInCurrentPose(graphics, context.font(), tooltip, mouseX, mouseY, scale, originX, originY);
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

    private static boolean isPointInsideGiftPreview(double mouseX, double mouseY, int left, int top) {
        int slotLeft = left + GIFT_PREVIEW_SLOT_X;
        int slotTop = top + GIFT_PREVIEW_SLOT_Y;
        return VillagerClientUiUtil.containsExclusive(
                mouseX, mouseY, slotLeft, slotTop, slotLeft + INVENTORY_SLOT_SIZE, slotTop + INVENTORY_SLOT_SIZE);
    }

    private static GiftButtonBounds giftButtonBounds(int left, int top) {
        int buttonLeft = left + 57;
        int buttonTop = top + 8;
        return new GiftButtonBounds(
                buttonLeft,
                buttonTop,
                buttonLeft + INVENTORY_BUTTON_WIDTH,
                buttonTop + INVENTORY_BUTTON_HEIGHT);
    }

    private static GiftInfoIconBounds giftInfoIconBounds(int left, int top) {
        int iconLeft = left + 6;
        int iconTop = top + 7;
        return new GiftInfoIconBounds(
                iconLeft,
                iconTop,
                iconLeft + GIFT_INFO_ICON_WIDTH,
                iconTop + GIFT_INFO_ICON_HEIGHT);
    }

    interface Context {
        Font font();

        int selectedInventorySlot();

        void setSelectedInventorySlot(int slot);

        int selectedGiftAmount();

        void adjustSelectedGiftAmount(int delta);

        Button giftButton();

        ItemStack stackForInventorySlot(int inventorySlot);

        String professionName();

        List<String> knownLikedGiftNames();

        List<String> knownDislikedGiftNames();

        Optional<GiftTooltipReaction> giftTooltipReaction(ItemStack stack);

    }

    private record GiftTransform(int left, int top, float scale) {
        double localX(double screenX) {
            return (screenX - this.left) / Math.max(this.scale, 0.001F);
        }

        double localY(double screenY) {
            return (screenY - this.top) / Math.max(this.scale, 0.001F);
        }
    }

    private record GiftButtonBounds(int left, int top, int right, int bottom) {
        int width() {
            return this.right - this.left;
        }

        int height() {
            return this.bottom - this.top;
        }
    }

    private record GiftInfoIconBounds(int left, int top, int right, int bottom) {
    }
}
