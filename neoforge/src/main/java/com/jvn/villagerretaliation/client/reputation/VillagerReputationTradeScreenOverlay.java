package com.jvn.villagerretaliation.client.reputation;

import com.jvn.villagerretaliation.reputation.VillagerReputationLevel;
import com.jvn.toucanlib.client.ToucanScreenRects;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
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

        guiGraphics.blit(VillagerReputationIconSet.iconFor(entry.level()), iconX, iconY, 0, 0, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);

        int mouseX = event.getMouseX();
        int mouseY = event.getMouseY();
        if (!ToucanScreenRects.contains(mouseX, mouseY, iconX, iconY, ICON_SIZE, ICON_SIZE)) {
            return;
        }

        Component title = Component.translatable("villagerretaliation.reputation.label");
        Component tierAndValue = Component.literal(I18n.get(
                        "villagerretaliation.reputation.tier_value_format",
                        VillagerReputationIconSet.formatLevel(entry.level()),
                        entry.reputation()
                ))
                .withStyle(VillagerReputationIconSet.colorFor(entry.level()));
        guiGraphics.renderTooltip(minecraft.font, List.of(title, tierAndValue), Optional.empty(), mouseX, mouseY);
    }
}
