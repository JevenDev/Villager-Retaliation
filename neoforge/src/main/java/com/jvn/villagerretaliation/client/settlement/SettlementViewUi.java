package com.jvn.villagerretaliation.client.settlement;

import com.jvn.villagerretaliation.client.VillagerRetaliationClientAssets;
import com.jvn.villagerretaliation.network.SettlementManagementPayload;
import com.jvn.villagerretaliation.network.VillageOverviewPayload.Presence;
import com.jvn.villagerretaliation.settlement.SettlementViewAction;
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
    private SettlementViewUi() { }

    static void frame(GuiGraphics graphics, int left, int top, int width, int height) {
        com.jvn.villagerretaliation.client.ui.VillagerUi.frame(graphics, left, top, width, height);
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

    static boolean actionEligible(SettlementViewAction action, SettlementManagementPayload.Resident resident,
                                  SettlementManagementPayload.Area workplace) {
        if (!action.eligible(resident, workplace) || resident.status().equals("personal_commitment")) return false;
        boolean enabled = resident.settings() != null ? resident.settings().enabled() : !resident.status().equals("paused");
        return switch (action) {
            case ENABLE -> !enabled;
            case PAUSE -> enabled;
            case RETURN -> !resident.temporary().isBlank();
            case TEMPORARY_TASK -> resident.presence() != Presence.UNLOADED;
            case CANCEL_TASKS -> resident.presence() != Presence.UNLOADED && !resident.temporary().isBlank();
            default -> true;
        };
    }

    static Component actionLabel(String label, long eligible, int selected) {
        return Component.literal(selected > 1 ? label + " (" + eligible + "/" + selected + ")" : label);
    }

    static String readable(String value) {
        String text = value.replace('_', ' ');
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    static final class HeaderButton extends Button {
        HeaderButton(Component message, int left, int width, Runnable action) {
            super(left, 2, width, 30, message, ignored -> action.run(), DEFAULT_NARRATION);
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) { }
    }

    static final class IconButton extends Button {
        private final ItemStack icon;
        private final BooleanSupplier selected;
        private final boolean label;
        private final boolean portrait;
        private Component symbol;

        IconButton(Component message, int left, int top, int width, int height, ItemStack icon,
                   boolean label, boolean portrait, BooleanSupplier selected, Runnable action) {
            super(left, top, width, height, message, ignored -> action.run(), DEFAULT_NARRATION);
            this.icon = icon;
            this.label = label;
            this.portrait = portrait;
            this.selected = selected;
            setTooltip(Tooltip.create(message));
        }

        IconButton symbol(String value) {
            this.symbol = Component.literal(value);
            return this;
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            boolean selected = this.selected.getAsBoolean();
            com.jvn.villagerretaliation.client.ui.VillagerUi.row(graphics, getX(), getY(), getWidth(), getHeight(),
                    selected || isFocused(), isHoveredOrFocused() && this.active);
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
                graphics.drawCenteredString(Minecraft.getInstance().font, this.symbol == null ? getMessage() : this.symbol, getX() + getWidth() / 2,
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
            com.jvn.villagerretaliation.client.ui.VillagerUi.row(graphics, getX(), getY(), getWidth(), getHeight(),
                    this.selected || isFocused(), isHoveredOrFocused());
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
                        && (resident.status().equals("working") || resident.status().equals("workforce.working"))) detailColor = 0xCCCCCC;
                if (getWidth() >= 160) {
                    textWidth -= 28;
                    graphics.fill(getRight() - 31, getY() + 8, getRight() - 6, getY() + 13, 0xFF0C1117);
                    graphics.fill(getRight() - 30, getY() + 9,
                            getRight() - 30 + Math.clamp(Math.round(healthFraction(resident) * 23), 0, 23),
                            getY() + 12, 0xFFCCCCCC);
                }
            } else {
                ItemStack icon = new ItemStack(this.entry instanceof SettlementManagementPayload.Project ? Items.BRICKS
                        : this.entry instanceof SettlementManagementPayload.Issue ? Items.REDSTONE_TORCH
                        : this.entry instanceof SettlementManagementPayload.Depot ? Items.CHEST
                        : this.entry instanceof SettlementManagementPayload.Order order ? net.minecraft.core.registries.BuiltInRegistries.ITEM.get(order.item()) : Items.OAK_FENCE);
                item(graphics, icon, getX() + 5, getY() + (getHeight() - 16) / 2, 16);
                detail = readable(SettlementViewBrowser.kind(this.entry));
                if (this.entry instanceof SettlementManagementPayload.Issue issue) {
                    detail = issue.detail();
                    detailColor = 0xCCCCCC;
                }
            }
            graphics.drawString(font, font.plainSubstrByWidth(getMessage().getString(), textWidth), textLeft, getY() + 5, 0xFFFFFF);
            graphics.drawString(font, font.plainSubstrByWidth(detail, getRight() - textLeft - 6), textLeft, getY() + 16, detailColor);
        }
    }
}
