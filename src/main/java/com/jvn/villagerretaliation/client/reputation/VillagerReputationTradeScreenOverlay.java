package com.jvn.villagerretaliation.client.reputation;

import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.reputation.VillagerReputationLevel;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.event.ScreenEvent;

public final class VillagerReputationTradeScreenOverlay {
    private static final int ICON_SIZE = 16;
    private static final int ICON_MARGIN_RIGHT = 6;
    private static final int ICON_MARGIN_TOP = 6;
    private static final double TRADING_LOOKUP_RADIUS = 8.0D;

    private VillagerReputationTradeScreenOverlay() {
    }

    public static void onScreenRender(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof MerchantScreen screen)) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || minecraft.options.hideGui) {
            return;
        }

        Optional<VillagerReputationClientCache.DisplayEntry> entry = findCachedTradingMerchantEntry(minecraft);
        if (entry.isEmpty()) {
            return;
        }

        renderIconAndTooltip(event, screen, minecraft, entry.get());
    }

    private static Optional<VillagerReputationClientCache.DisplayEntry> findCachedTradingMerchantEntry(Minecraft minecraft) {
        AABB searchArea = minecraft.player.getBoundingBox().inflate(TRADING_LOOKUP_RADIUS);
        VillagerReputationClientCache.DisplayEntry closest = null;
        double closestDistance = Double.MAX_VALUE;
        for (AbstractVillager villager : minecraft.level.getEntitiesOfClass(AbstractVillager.class, searchArea)) {
            Optional<VillagerReputationClientCache.DisplayEntry> entry = VillagerReputationClientCache.get(villager.getUUID(), villager.getId());
            if (entry.isEmpty()) {
                continue;
            }
            if (villager.getTradingPlayer() == minecraft.player) {
                return entry;
            }
            double distance = minecraft.player.distanceToSqr(villager);
            if (distance < closestDistance) {
                closestDistance = distance;
                closest = entry.get();
            }
        }
        return Optional.ofNullable(closest);
    }

    private static void renderIconAndTooltip(
            ScreenEvent.Render.Post event,
            MerchantScreen screen,
            Minecraft minecraft,
            VillagerReputationClientCache.DisplayEntry entry
    ) {
        GuiGraphics guiGraphics = event.getGuiGraphics();
        int iconX = screen.getGuiLeft() + screen.getXSize() - ICON_SIZE - ICON_MARGIN_RIGHT;
        int iconY = screen.getGuiTop() + ICON_MARGIN_TOP;

        guiGraphics.blit(iconFor(entry.level()), iconX, iconY, 0, 0, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);

        int mouseX = event.getMouseX();
        int mouseY = event.getMouseY();
        if (!isHovering(iconX, iconY, ICON_SIZE, ICON_SIZE, mouseX, mouseY)) {
            return;
        }

        Component title = Component.literal("Reputation");
        Component tierAndValue = Component.literal(formatLevel(entry.level()) + ": " + entry.reputation())
                .withStyle(colorFor(entry.level()));
        guiGraphics.renderTooltip(minecraft.font, List.of(title, tierAndValue), Optional.empty(), mouseX, mouseY);
    }

    private static boolean isHovering(int x, int y, int width, int height, int mouseX, int mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private static String formatLevel(VillagerReputationLevel level) {
        String lower = level.name().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private static ChatFormatting colorFor(VillagerReputationLevel level) {
        return switch (level) {
            case ROYALTY -> ChatFormatting.YELLOW;
            case REVERED -> ChatFormatting.GOLD;
            case RESPECTED -> ChatFormatting.AQUA;
            case TRUSTED -> ChatFormatting.GREEN;
            case NEUTRAL -> ChatFormatting.GRAY;
            case SUSPICIOUS -> ChatFormatting.GRAY;
            case HOSTILE -> ChatFormatting.RED;
            case DESPISED -> ChatFormatting.DARK_RED;
            case FEARED -> ChatFormatting.LIGHT_PURPLE;
        };
    }

    private static ResourceLocation iconFor(VillagerReputationLevel level) {
        return switch (level) {
            case ROYALTY -> icon("royalty");
            case REVERED -> icon("revered");
            case RESPECTED -> icon("respected");
            case TRUSTED -> icon("trusted");
            case NEUTRAL -> icon("neutral");
            case SUSPICIOUS -> icon("suspicious");
            case HOSTILE -> icon("hostile");
            case DESPISED -> icon("despised");
            case FEARED -> icon("feared");
        };
    }

    private static ResourceLocation icon(String name) {
        return ResourceLocation.fromNamespaceAndPath(
                VillagerRetaliation.MOD_ID,
                "textures/gui/container/icons/" + name + ".png"
        );
    }
}
