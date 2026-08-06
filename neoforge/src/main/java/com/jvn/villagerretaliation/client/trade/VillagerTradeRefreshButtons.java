package com.jvn.villagerretaliation.client.trade;

import com.jvn.villagerretaliation.client.VillagerRetaliationClientAssets;
import com.jvn.villagerretaliation.client.config.VillagerRetaliationServerConfigClient;
import com.jvn.villagerretaliation.client.ui.VillagerClientUiUtil;
import com.jvn.villagerretaliation.client.villager.VillagerTradingTargetFinder;
import com.jvn.villagerretaliation.network.VillagerTradeRefreshRequestPayload;
import com.jvn.villagerretaliation.network.VillagerTradeRefreshStatePayload;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

public final class VillagerTradeRefreshButtons {
    private static final int VISIBLE_TRADE_ROWS = 7;
    private static final int ROW_TOP = 16;
    private static final int ROW_HEIGHT = 20;
    private static final int BUTTON_SIZE = 20;
    private static final int BUTTON_X = -15;
    private static final int BUTTON_Y_OFFSET = 2;
    private static final int TEXTURE_SIZE = 20;
    private static final Field SCROLL_OFF_FIELD = scrollOffField();
    private static final Map<Integer, Set<Integer>> PENDING_REFRESHES_BY_MERCHANT = new ConcurrentHashMap<>();

    private VillagerTradeRefreshButtons() {
    }

    public static void acceptState(VillagerTradeRefreshStatePayload payload) {
        VillagerTradingTargetFinder.acceptMerchantId(payload.entityId());
        if (payload.pendingOfferIndexes().isEmpty()) {
            PENDING_REFRESHES_BY_MERCHANT.remove(payload.entityId());
            return;
        }
        PENDING_REFRESHES_BY_MERCHANT.put(payload.entityId(), Set.copyOf(payload.pendingOfferIndexes()));
    }

    public static void onScreenRender(ScreenEvent.Render.Post event) {
        if (!VillagerRetaliationServerConfigClient.skillTradeFeaturesEnabled()
                || !(event.getScreen() instanceof MerchantScreen screen)) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Optional<Integer> merchantId = resolveMerchantId(minecraft);
        if (minecraft.options.hideGui || merchantId.isEmpty()) {
            return;
        }

        MerchantOffers offers = screen.getMenu().getOffers();
        int scrollOff = scrollOff(screen);
        for (int row = 0; row < VISIBLE_TRADE_ROWS; row++) {
            int offerIndex = scrollOff + row;
            if (offerIndex >= offers.size()) {
                break;
            }
            renderButton(event, screen, row, offers.get(offerIndex), isPending(merchantId.get(), offerIndex));
        }
    }

    public static void onMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!VillagerRetaliationServerConfigClient.skillTradeFeaturesEnabled()
                || !(event.getScreen() instanceof MerchantScreen screen)
                || event.getButton() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Optional<Integer> merchantId = resolveMerchantId(minecraft);
        if (merchantId.isEmpty()) {
            return;
        }

        MerchantOffers offers = screen.getMenu().getOffers();
        int scrollOff = scrollOff(screen);
        for (int row = 0; row < VISIBLE_TRADE_ROWS; row++) {
            int offerIndex = scrollOff + row;
            if (offerIndex >= offers.size()) {
                break;
            }
            RefreshButtonBounds bounds = bounds(screen, row);
            if (bounds.contains(event.getMouseX(), event.getMouseY())) {
                if (!isPending(merchantId.get(), offerIndex)) {
                    markPendingLocally(merchantId.get(), offerIndex);
                    PacketDistributor.sendToServer(new VillagerTradeRefreshRequestPayload(merchantId.get(), offerIndex));
                }
                event.setCanceled(true);
                return;
            }
        }
    }

    private static Optional<Integer> resolveMerchantId(Minecraft minecraft) {
        return VillagerTradingTargetFinder.findTradingVillagerOrSingleNearby(minecraft)
                .map(villager -> villager.getId());
    }

    private static void renderButton(ScreenEvent.Render.Post event, MerchantScreen screen, int row, MerchantOffer offer, boolean pending) {
        RefreshButtonBounds bounds = bounds(screen, row);
        GuiGraphics graphics = event.getGuiGraphics();
        boolean hovered = bounds.contains(event.getMouseX(), event.getMouseY());
        graphics.blit(
                hovered
                        ? VillagerRetaliationClientAssets.TRADE_REROLL_BUTTON_HIGHLIGHTED_TEXTURE
                        : VillagerRetaliationClientAssets.TRADE_REROLL_BUTTON_TEXTURE,
                bounds.left(),
                bounds.top(),
                0,
                0,
                BUTTON_SIZE,
                BUTTON_SIZE,
                TEXTURE_SIZE,
                TEXTURE_SIZE);
        graphics.blit(
                pending
                        ? VillagerRetaliationClientAssets.TRADE_REROLL_REQUEST_SENT_ICON_TEXTURE
                        : hovered
                                ? VillagerRetaliationClientAssets.TRADE_REROLL_ICON_HIGHLIGHTED_TEXTURE
                                : VillagerRetaliationClientAssets.TRADE_REROLL_ICON_TEXTURE,
                bounds.left(),
                bounds.top(),
                0,
                0,
                BUTTON_SIZE,
                BUTTON_SIZE,
                TEXTURE_SIZE,
                TEXTURE_SIZE);
        if (hovered) {
            graphics.renderTooltip(
                    Minecraft.getInstance().font,
                    List.of(
                            Component.translatable(pending
                                    ? "villagerretaliation.trade_refresh.pending"
                                    : "villagerretaliation.trade_refresh.button"),
                            Component.literal(formatOffer(offer)).withStyle(ChatFormatting.GRAY)
                    ),
                    Optional.empty(),
                    event.getMouseX(),
                    event.getMouseY());
        }
    }

    private static RefreshButtonBounds bounds(MerchantScreen screen, int row) {
        int left = screen.getGuiLeft() + BUTTON_X;
        int top = screen.getGuiTop() + ROW_TOP + row * ROW_HEIGHT + BUTTON_Y_OFFSET;
        return new RefreshButtonBounds(left, top, left + BUTTON_SIZE, top + BUTTON_SIZE);
    }

    private static boolean isPending(int merchantId, int offerIndex) {
        return PENDING_REFRESHES_BY_MERCHANT.getOrDefault(merchantId, Set.of()).contains(offerIndex);
    }

    private static void markPendingLocally(int merchantId, int offerIndex) {
        PENDING_REFRESHES_BY_MERCHANT.compute(merchantId, (ignored, current) -> {
            Set<Integer> pending = current == null ? new HashSet<>() : new HashSet<>(current);
            pending.add(offerIndex);
            return Set.copyOf(pending);
        });
    }

    private static String formatOffer(MerchantOffer offer) {
        StringBuilder builder = new StringBuilder(formatStack(offer.getCostA()));
        ItemStack costB = offer.getCostB();
        if (!costB.isEmpty()) {
            builder.append(" + ").append(formatStack(costB));
        }
        return builder.append(" for ").append(formatStack(offer.getResult())).toString();
    }

    private static String formatStack(ItemStack stack) {
        return "x" + stack.getCount() + " " + stack.getHoverName().getString();
    }

    private static int scrollOff(MerchantScreen screen) {
        if (SCROLL_OFF_FIELD == null) {
            return 0;
        }
        try {
            return Math.max(0, SCROLL_OFF_FIELD.getInt(screen));
        } catch (IllegalAccessException ignored) {
            return 0;
        }
    }

    private static Field scrollOffField() {
        try {
            Field field = MerchantScreen.class.getDeclaredField("scrollOff");
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException exception) {
            return null;
        }
    }

    private record RefreshButtonBounds(int left, int top, int right, int bottom) {
        private boolean contains(double x, double y) {
            return VillagerClientUiUtil.containsExclusive(x, y, this.left, this.top, this.right, this.bottom);
        }
    }
}
