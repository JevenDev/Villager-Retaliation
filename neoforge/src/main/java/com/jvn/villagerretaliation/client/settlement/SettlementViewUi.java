package com.jvn.villagerretaliation.client.settlement;

import com.jvn.villagerretaliation.client.VillagerRetaliationClientAssets;
import com.jvn.villagerretaliation.client.ui.VillagerNineSlice;
import com.jvn.villagerretaliation.network.SettlementManagementPayload;
import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

final class SettlementViewUi {
    private static final VillagerNineSlice FRAME = new VillagerNineSlice(
            VillagerRetaliationClientAssets.ATTRIBUTE_FILTER_SCROLL_ROW_TEXTURE, 132, 24, 3, 3, 3, 3);
    private static final VillagerNineSlice SELECTED = new VillagerNineSlice(
            VillagerRetaliationClientAssets.ATTRIBUTE_FILTER_SELECTED_SCROLL_ROW_TEXTURE, 136, 28, 5, 5, 5, 5);

    private SettlementViewUi() { }

    static void frame(GuiGraphics graphics, int left, int top, int width, int height) {
        FRAME.renderAtTextureScale(graphics, left, top, width, height);
        graphics.fill(left + 3, top + 3, left + width - 3, top + height - 3, 0xF023282E);
    }

    static void portrait(GuiGraphics graphics, int left, int top, int size) {
        graphics.blit(VillagerRetaliationClientAssets.PLAYER_PARTY_INVENTORY_VILLAGER_ICON,
                left, top, size, size, 0, 0, 8, 8, 8, 8);
    }

    static void item(GuiGraphics graphics, ItemStack stack, int left, int top, int size) {
        if (stack.isEmpty()) return;
        graphics.pose().pushPose();
        graphics.pose().translate(left, top, 0);
        graphics.pose().scale(size / 16.0F, size / 16.0F, 1);
        graphics.renderItem(stack, 0, 0);
        graphics.pose().popPose();
    }

    static float healthFraction(SettlementManagementPayload.Resident resident) {
        var level = Minecraft.getInstance().level;
        if (level != null) for (var entity : level.entitiesForRendering()) {
            if (entity.getUUID().equals(resident.id()) && entity instanceof net.minecraft.world.entity.LivingEntity living)
                return Math.clamp(resident.health() / living.getMaxHealth(), 0.0F, 1.0F);
        }
        return Math.clamp(resident.health() / 20.0F, 0.0F, 1.0F);
    }

    static String profession(SettlementManagementPayload.Resident resident) {
        return (resident.baby() ? SettlementManagementScreen.text("child") : SettlementManagementScreen.profession(resident.profession())).getString();
    }

    static String status(SettlementManagementPayload.Resident resident) {
        if (resident.presence() != com.jvn.villagerretaliation.network.VillageOverviewPayload.Presence.HERE)
            return SettlementManagementScreen.enumText("presence", resident.presence()).getString();
        String key = resident.status().startsWith("workforce.")
                ? "villagerretaliation.gui.clipboard_workforce.status." + resident.status().substring("workforce.".length())
                : "villagerretaliation.gui.settlement.work." + resident.status();
        return Component.translatableWithFallback(key, SettlementManagementScreen.text("work.idle").getString()).getString();
    }

    static String readable(String value) {
        String text = value.replace('_', ' ');
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    static final class IconButton extends Button {
        private final ItemStack icon;
        private final BooleanSupplier selected;
        private final boolean label;
        private final boolean portrait;

        IconButton(Component message, int left, int top, int width, int height, ItemStack icon,
                   boolean label, boolean portrait, BooleanSupplier selected, Runnable action) {
            super(left, top, width, height, message, ignored -> action.run(), DEFAULT_NARRATION);
            this.icon = icon;
            this.label = label;
            this.portrait = portrait;
            this.selected = selected;
            setTooltip(Tooltip.create(message));
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            boolean selected = this.selected.getAsBoolean();
            (selected ? SELECTED : FRAME).renderAtTextureScale(graphics, getX(), getY(), getWidth(), getHeight());
            int color = selected ? 0xB02B589D : isHoveredOrFocused() && this.active ? 0xC0404955 : 0xE0292F36;
            graphics.fill(getX() + 3, getY() + 3, getRight() - 3, getBottom() - 3, color);
            if (selected || isFocused()) graphics.renderOutline(getX() + 1, getY() + 1, getWidth() - 2, getHeight() - 2, 0xFF81B6FF);
            int size = this.label && this.icon.isEmpty() && !this.portrait ? 0 : Math.min(16, getHeight() - 6);
            int left = this.label ? getX() + 5 : getX() + (getWidth() - size) / 2;
            int top = getY() + (getHeight() - size) / 2;
            if (this.portrait) portrait(graphics, left, top, size);
            else item(graphics, this.icon, left, top, size);
            if (this.label) {
                Font font = Minecraft.getInstance().font;
                int textLeft = size == 0 ? getX() + 3 : left + size + 4;
                graphics.drawString(font, font.plainSubstrByWidth(getMessage().getString(), getRight() - textLeft - 3),
                        textLeft, getY() + (getHeight() - font.lineHeight) / 2, this.active ? 0xFFFFFF : 0x999999);
            } else if (this.icon.isEmpty() && !this.portrait) {
                graphics.drawCenteredString(Minecraft.getInstance().font, getMessage(), getX() + getWidth() / 2,
                        getY() + (getHeight() - 8) / 2, this.active ? 0xFFFFFF : 0x999999);
            }
        }
    }

    static final class EntryButton extends Button {
        private SettlementManagementPayload.Entry entry;
        private boolean selected;

        EntryButton(int left, int top, int width, int height, Runnable action) {
            super(left, top, width, height, Component.empty(), ignored -> action.run(), DEFAULT_NARRATION);
        }

        void entry(SettlementManagementPayload.Entry entry, boolean selected) {
            this.entry = entry;
            this.selected = selected;
            setMessage(SettlementManagementScreen.name(entry));
            setTooltip(Tooltip.create(SettlementManagementScreen.name(entry).copy().append("\n")
                    .append(SettlementManagementScreen.detail(entry))));
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            if (this.entry == null) return;
            (this.selected ? SELECTED : FRAME).renderAtTextureScale(graphics, getX(), getY(), getWidth(), getHeight());
            graphics.fill(getX() + 3, getY() + 3, getRight() - 3, getBottom() - 3,
                    this.selected ? 0xE02D5284 : isHoveredOrFocused() ? 0xF03B444E : 0xF023282E);
            if (this.selected || isFocused()) graphics.renderOutline(getX() + 1, getY() + 1, getWidth() - 2, getHeight() - 2, 0xFF81B6FF);
            Font font = Minecraft.getInstance().font;
            int iconSize = Math.min(20, getHeight() - 6);
            int textLeft = getX() + iconSize + 9;
            int textWidth = getRight() - textLeft - 6;
            String detail;
            int detailColor = 0xB9C1CB;
            if (this.entry instanceof SettlementManagementPayload.Resident resident) {
                portrait(graphics, getX() + 5, getY() + (getHeight() - iconSize) / 2, iconSize);
                detail = profession(resident) + " / " + status(resident);
                if (resident.presence() == com.jvn.villagerretaliation.network.VillageOverviewPayload.Presence.HERE
                        && (resident.status().equals("working") || resident.status().equals("workforce.working"))) detailColor = 0x85DD88;
                if (getWidth() >= 160) {
                    textWidth -= 28;
                    graphics.fill(getRight() - 31, getY() + 8, getRight() - 6, getY() + 13, 0xFF0C1117);
                    graphics.fill(getRight() - 30, getY() + 9,
                            getRight() - 30 + Math.clamp(Math.round(healthFraction(resident) * 23), 0, 23),
                            getY() + 12, 0xFF67CF75);
                }
            } else {
                ItemStack icon = new ItemStack(this.entry instanceof SettlementManagementPayload.Project ? Items.BRICKS
                        : this.entry instanceof SettlementManagementPayload.Issue ? Items.REDSTONE_TORCH : Items.OAK_FENCE);
                item(graphics, icon, getX() + 5, getY() + (getHeight() - 16) / 2, 16);
                detail = readable(SettlementViewBrowser.kind(this.entry));
                if (this.entry instanceof SettlementManagementPayload.Issue issue) {
                    detail = issue.detail();
                    detailColor = issue.needsAttention() ? 0xFFB080 : 0xB9C1CB;
                }
            }
            graphics.drawString(font, font.plainSubstrByWidth(getMessage().getString(), textWidth), textLeft, getY() + 5, 0xFFFFFF);
            graphics.drawString(font, font.plainSubstrByWidth(detail, getRight() - textLeft - 6), textLeft, getY() + 16, detailColor);
        }
    }
}
