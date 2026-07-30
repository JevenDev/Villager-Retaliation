package com.jvn.villagerretaliation.client.inventory;

import com.jvn.villagerretaliation.client.VillagerRetaliationClientAssets;
import com.jvn.villagerretaliation.inventory.VillagerItemFilterMenu;
import com.jvn.villagerretaliation.item.VillagerFilterPolicy;
import com.jvn.villagerretaliation.item.VillagerItemFilterData;
import com.jvn.villagerretaliation.network.FilterPolicyChangePayload;
import com.jvn.villagerretaliation.network.ItemFilterAmountChangePayload;
import com.jvn.toucanlib.client.interaction.ToucanInputModifiers;
import com.jvn.toucanlib.client.interaction.ToucanLimitFeedback;
import com.jvn.toucanlib.client.interaction.ToucanSlotAmounts;
import com.jvn.toucanlib.client.interaction.ToucanSlotBounds;
import com.jvn.toucanlib.client.interaction.ToucanSlotRenderer;
import com.jvn.toucanlib.client.tooltip.ToucanTooltips;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public final class VillagerItemFilterScreen extends AbstractContainerScreen<VillagerItemFilterMenu> {
    private static final int TEXTURE_WIDTH = 176;
    private static final int TEXTURE_HEIGHT = 166;
    private static final int LIMIT_FEEDBACK_TICKS = 8;

    private final ToucanLimitFeedback[] limitFeedback = createLimitFeedback();
    private Button allowlistButton;
    private Button denylistButton;
    private Button entryCombinationButton;
    private Button directionButton;
    private Button decreaseStockButton;
    private Button stockTargetButton;
    private Button increaseStockButton;

    public VillagerItemFilterScreen(VillagerItemFilterMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = TEXTURE_WIDTH;
        this.imageHeight = TEXTURE_HEIGHT;
        this.inventoryLabelY = 72;
    }

    @Override
    protected void init() {
        super.init();
        this.allowlistButton = Button.builder(
                        Component.translatable("villagerretaliation.gui.filter_policy.mode.allow_matching"),
                        button -> setListMode(VillagerFilterPolicy.ListMode.ALLOW_MATCHING))
                .bounds(this.leftPos + 8, this.topPos + 43, 52, 20)
                .tooltip(modeTooltip(
                        "villagerretaliation.gui.filter_policy.mode.allow_matching",
                        ChatFormatting.GREEN,
                        "villagerretaliation.gui.filter_policy.mode.allow_matching.description"))
                .build();
        this.denylistButton = Button.builder(
                        Component.translatable("villagerretaliation.gui.filter_policy.mode.deny_matching"),
                        button -> setListMode(VillagerFilterPolicy.ListMode.DENY_MATCHING))
                .bounds(this.leftPos + 62, this.topPos + 43, 52, 20)
                .tooltip(modeTooltip(
                        "villagerretaliation.gui.filter_policy.mode.deny_matching",
                        ChatFormatting.RED,
                        "villagerretaliation.gui.filter_policy.mode.deny_matching.description"))
                .build();
        this.entryCombinationButton = Button.builder(
                        Component.empty(), button -> cycleEntryCombination())
                .bounds(this.leftPos + 116, this.topPos + 43, 52, 20)
                .build();
        this.directionButton = Button.builder(Component.empty(), button -> cycleDirection())
                .bounds(this.leftPos + 8, this.topPos + 64, 52, 18)
                .build();
        this.decreaseStockButton = Button.builder(Component.literal("-"), button -> adjustStock(-1))
                .bounds(this.leftPos + 62, this.topPos + 64, 20, 18)
                .build();
        this.stockTargetButton = Button.builder(Component.empty(), button -> toggleStockTarget())
                .bounds(this.leftPos + 84, this.topPos + 64, 62, 18)
                .build();
        this.increaseStockButton = Button.builder(Component.literal("+"), button -> adjustStock(1))
                .bounds(this.leftPos + 148, this.topPos + 64, 20, 18)
                .build();
        addRenderableWidget(this.allowlistButton);
        addRenderableWidget(this.denylistButton);
        addRenderableWidget(this.entryCombinationButton);
        addRenderableWidget(this.directionButton);
        addRenderableWidget(this.decreaseStockButton);
        addRenderableWidget(this.stockTargetButton);
        addRenderableWidget(this.increaseStockButton);
        refreshButtons();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        for (ToucanLimitFeedback feedback : this.limitFeedback) {
            feedback.tick();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        refreshButtons();
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(
                VillagerRetaliationClientAssets.ITEM_FILTER_CONTAINER_TEXTURE,
                this.leftPos,
                this.topPos,
                0,
                0,
                this.imageWidth,
                this.imageHeight,
                this.imageWidth,
                this.imageHeight);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 0x404040, false);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0.0
                && this.stockTargetButton != null
                && this.stockTargetButton.isMouseOver(mouseX, mouseY)
                && this.menu.filterPolicy().listMode() == VillagerFilterPolicy.ListMode.ALLOW_MATCHING) {
            int step = ToucanSlotAmounts.step(ToucanInputModifiers.current());
            adjustStock(scrollY > 0.0 ? step : -step);
            return true;
        }
        int slot = ghostSlotAt((int) mouseX, (int) mouseY);
        if (scrollY != 0.0
                && slot >= 0
                && this.menu.filterPolicy().state() == VillagerFilterPolicy.PolicyState.LEGACY
                && this.menu.mode() == VillagerItemFilterData.Mode.ALLOWLIST
                && this.menu.isAmountEntry(slot)) {
            int step = ToucanSlotAmounts.step(ToucanInputModifiers.current());
            int delta = scrollY > 0.0 ? step : -step;
            VillagerItemFilterData.AmountAdjustment adjustment =
                    this.menu.adjustEntryAmount(slot, delta);
            if (adjustment.valid()) {
                if (adjustment.hitLimit()) {
                    this.limitFeedback[slot].trigger(LIMIT_FEEDBACK_TICKS);
                }
                if (adjustment.changed()) {
                    PacketDistributor.sendToServer(new ItemFilterAmountChangePayload(slot, delta));
                }
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    protected List<Component> getTooltipFromContainerItem(ItemStack stack) {
        List<Component> tooltip = new ArrayList<>(super.getTooltipFromContainerItem(stack));
        if (this.hoveredSlot == null
                || this.menu.filterPolicy().state() != VillagerFilterPolicy.PolicyState.LEGACY
                || this.hoveredSlot.index < 0
                || this.hoveredSlot.index >= VillagerItemFilterMenu.GHOST_SLOT_COUNT
                || !this.menu.isAmountEntry(this.hoveredSlot.index)) {
            return tooltip;
        }

        int slot = this.hoveredSlot.index;
        int amount = this.menu.amount(slot);
        Component value = amount == VillagerItemFilterData.UNLIMITED_AMOUNT
                ? Component.translatable("villagerretaliation.gui.item_filter.amount.unlimited")
                : Component.literal(VillagerItemFilterData.formatAmount(amount));
        String limitKey = this.menu.identityEntryCount(slot) > 1
                ? "villagerretaliation.gui.item_filter.amount.entry_limit"
                : "villagerretaliation.gui.item_filter.amount.stock_limit";
        tooltip.add(Component.translatable(limitKey, value).withStyle(ChatFormatting.GRAY));
        if (this.menu.identityEntryCount(slot) > 1) {
            tooltip.add(Component.translatable(
                    "villagerretaliation.gui.item_filter.amount.combined_limit",
                    VillagerItemFilterData.formatAmount(this.menu.combinedAmount(slot)))
                    .withStyle(ChatFormatting.GRAY));
        }
        if (this.menu.mode() == VillagerItemFilterData.Mode.ALLOWLIST) {
            ToucanTooltips.appendScrollInstructions(
                    tooltip,
                    "villagerretaliation.gui.item_filter.amount.scroll");
        }
        if (this.menu.mode() == VillagerItemFilterData.Mode.DENYLIST) {
            tooltip.add(Component.translatable(
                    "villagerretaliation.gui.item_filter.amount.inactive_denylist")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        return tooltip;
    }

    @Override
    protected void renderSlotContents(
            GuiGraphics graphics,
            ItemStack stack,
            Slot slot,
            String countString) {
        super.renderSlotContents(graphics, stack, slot, countString);
        if (slot.index < 0
                || this.menu.filterPolicy().state() != VillagerFilterPolicy.PolicyState.LEGACY
                || slot.index >= VillagerItemFilterMenu.GHOST_SLOT_COUNT
                || !this.menu.isAmountEntry(slot.index)) {
            return;
        }

        int amount = this.menu.amount(slot.index);
        if (amount == VillagerItemFilterData.UNLIMITED_AMOUNT) {
            return;
        }

        String label = VillagerItemFilterData.formatAmount(amount);
        int color = this.menu.mode() == VillagerItemFilterData.Mode.ALLOWLIST
                ? 0xFFFFFFFF
                : 0xFFAAAAAA;
        int offset = 0;
        if (this.limitFeedback[slot.index].active()) {
            color = 0xFFFF7777;
            offset = this.limitFeedback[slot.index].horizontalOffset();
        }
        ToucanSlotRenderer.renderCountLabel(
                graphics,
                this.font,
                ToucanSlotBounds.square(slot.x + offset, slot.y, 18),
                label,
                color);
    }

    private static ToucanLimitFeedback[] createLimitFeedback() {
        ToucanLimitFeedback[] feedback = new ToucanLimitFeedback[VillagerItemFilterMenu.GHOST_SLOT_COUNT];
        for (int slot = 0; slot < feedback.length; slot++) {
            feedback[slot] = new ToucanLimitFeedback();
        }
        return feedback;
    }

    private static Tooltip modeTooltip(
            String titleKey,
            ChatFormatting titleColor,
            String descriptionKey) {
        Component message = Component.empty()
                .append(Component.translatable(titleKey).withStyle(titleColor))
                .append("\n")
                .append(Component.translatable(descriptionKey));
        return Tooltip.create(message);
    }

    private void setListMode(VillagerFilterPolicy.ListMode mode) {
        changePolicy(VillagerFilterPolicy.PolicyField.LIST_MODE, mode.networkId());
    }

    private void cycleEntryCombination() {
        VillagerFilterPolicy.CombinationMode current = this.menu.filterPolicy().combinationMode();
        VillagerFilterPolicy.CombinationMode next = current == VillagerFilterPolicy.CombinationMode.MATCH_ANY
                ? VillagerFilterPolicy.CombinationMode.MATCH_ALL
                : VillagerFilterPolicy.CombinationMode.MATCH_ANY;
        changePolicy(VillagerFilterPolicy.PolicyField.COMBINATION, next.networkId());
    }

    private void cycleDirection() {
        VillagerFilterPolicy.TransferDirection current = this.menu.filterPolicy().direction();
        VillagerFilterPolicy.TransferDirection next = switch (current) {
            case RECEIVE -> VillagerFilterPolicy.TransferDirection.PROVIDE;
            case PROVIDE -> VillagerFilterPolicy.TransferDirection.BOTH;
            case BOTH -> VillagerFilterPolicy.TransferDirection.RECEIVE;
        };
        changePolicy(VillagerFilterPolicy.PolicyField.DIRECTION, next.networkId());
    }

    private void toggleStockTarget() {
        int target = this.menu.filterPolicy().stockTarget().isPresent() ? 0 : 64;
        changePolicy(VillagerFilterPolicy.PolicyField.STOCK_TARGET, target);
    }

    private void adjustStock(int delta) {
        changePolicy(VillagerFilterPolicy.PolicyField.STOCK_DELTA, delta);
    }

    private void changePolicy(VillagerFilterPolicy.PolicyField field, int value) {
        this.menu.applyClientPolicyChange(field, value);
        PacketDistributor.sendToServer(new FilterPolicyChangePayload(field, value));
        refreshButtons();
    }

    private static Tooltip entryCombinationTooltip(VillagerFilterPolicy.CombinationMode combination) {
        String description = "villagerretaliation.gui.filter_policy.combination."
                + combination.id() + ".description";
        return modeTooltip(
                "villagerretaliation.gui.filter_policy.combination.title",
                combination == VillagerFilterPolicy.CombinationMode.LEGACY
                        ? ChatFormatting.YELLOW : ChatFormatting.AQUA,
                description);
    }

    private void refreshButtons() {
        if (this.allowlistButton == null
                || this.denylistButton == null
                || this.entryCombinationButton == null
                || this.directionButton == null
                || this.stockTargetButton == null) {
            return;
        }
        VillagerFilterPolicy.Policy policy = this.menu.filterPolicy();
        this.allowlistButton.active = policy.listMode() != VillagerFilterPolicy.ListMode.ALLOW_MATCHING;
        this.denylistButton.active = policy.listMode() != VillagerFilterPolicy.ListMode.DENY_MATCHING;
        VillagerFilterPolicy.CombinationMode combination = policy.combinationMode();
        this.entryCombinationButton.setMessage(Component.translatable(
                "villagerretaliation.gui.filter_policy.combination." + combination.id()));
        this.entryCombinationButton.setTooltip(entryCombinationTooltip(combination));
        this.directionButton.setMessage(Component.translatable(
                "villagerretaliation.gui.filter_policy.direction." + policy.direction().id()));
        this.directionButton.setTooltip(Tooltip.create(Component.translatable(
                "villagerretaliation.gui.filter_policy.direction.description")));
        Component target = policy.stockTarget().isPresent()
                ? Component.literal(Integer.toString(policy.stockTarget().getAsInt()))
                : Component.translatable("villagerretaliation.gui.filter_policy.stock.unlimited");
        this.stockTargetButton.setMessage(Component.translatable(
                "villagerretaliation.gui.filter_policy.stock." + policy.direction().id(), target));
        boolean quantitative = policy.listMode() == VillagerFilterPolicy.ListMode.ALLOW_MATCHING;
        Tooltip stockTooltip = Tooltip.create(Component.translatable(quantitative
                ? "villagerretaliation.gui.filter_policy.stock.description"
                : "villagerretaliation.gui.filter_policy.stock.inactive_deny"));
        this.stockTargetButton.setTooltip(stockTooltip);
        this.decreaseStockButton.setTooltip(stockTooltip);
        this.increaseStockButton.setTooltip(stockTooltip);
        this.stockTargetButton.active = quantitative;
        this.decreaseStockButton.active = quantitative && policy.stockTarget().isPresent();
        this.increaseStockButton.active = quantitative
                && policy.stockTarget().orElse(0) < VillagerFilterPolicy.MAX_STOCK_TARGET;
    }

    /**
     * Returns the ghost-slot index at the given screen coordinates, or {@code -1} when none is hit.
     */
    public int ghostSlotAt(int mouseX, int mouseY) {
        for (int slot = 0; slot < VillagerItemFilterMenu.GHOST_SLOT_COUNT; slot++) {
            var ghostSlot = this.menu.slots.get(slot);
            int slotX = this.leftPos + ghostSlot.x;
            int slotY = this.topPos + ghostSlot.y;
            if (mouseX >= slotX && mouseX < slotX + 18 && mouseY >= slotY && mouseY < slotY + 18) {
                return slot;
            }
        }
        return -1;
    }
}
