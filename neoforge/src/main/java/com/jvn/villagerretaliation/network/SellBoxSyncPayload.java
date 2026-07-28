package com.jvn.villagerretaliation.network;

import com.jvn.villagerretaliation.interaction.VillagerCurrencyResources;
import com.jvn.villagerretaliation.sell.CurrencyAmount;
import com.jvn.villagerretaliation.sell.DailySellMarket;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public record SellBoxSyncPayload(
        int containerId,
        long day,
        CurrencyAmount balance,
        String currencyName,
        String currencyPluralName,
        ResourceLocation currencyIconSprite,
        Map<ResourceLocation, CurrencyAmount> prices) implements CustomPacketPayload {
    private static final int MAX_PRICES = 4096;
    private static final ResourceLocation DEFAULT_CURRENCY_ICON =
            ResourceLocation.withDefaultNamespace("item/emerald");
    public static final Type<SellBoxSyncPayload> TYPE = VillagerPayloads.type("sell_box_sync");
    public static final StreamCodec<RegistryFriendlyByteBuf, SellBoxSyncPayload> STREAM_CODEC =
            VillagerPayloads.codec(SellBoxSyncPayload::encode, SellBoxSyncPayload::decode);

    public SellBoxSyncPayload {
        balance = balance == null ? CurrencyAmount.ZERO : balance;
        currencyName = currencyName == null || currencyName.isBlank() ? "emerald" : currencyName;
        currencyPluralName = currencyPluralName == null || currencyPluralName.isBlank() ? "emeralds" : currencyPluralName;
        currencyIconSprite = currencyIconSprite == null ? DEFAULT_CURRENCY_ICON : currencyIconSprite;
        prices = prices == null ? Map.of() : Map.copyOf(prices);
    }

    public static void send(ServerPlayer player, int containerId, CurrencyAmount balance) {
        VillagerCurrencyResources.Text text = VillagerCurrencyResources.text(player.getServer());
        PacketDistributor.sendToPlayer(player, new SellBoxSyncPayload(
                containerId,
                DailySellMarket.currentDay(player.getServer()),
                balance,
                text.name(),
                text.pluralName(),
                text.iconSprite(),
                DailySellMarket.snapshot(player.getServer())));
    }

    private static void encode(RegistryFriendlyByteBuf buffer, SellBoxSyncPayload payload) {
        buffer.writeVarInt(payload.containerId());
        buffer.writeVarLong(payload.day());
        payload.balance().write(buffer);
        buffer.writeUtf(payload.currencyName(), 128);
        buffer.writeUtf(payload.currencyPluralName(), 128);
        buffer.writeResourceLocation(payload.currencyIconSprite());
        int size = Math.min(payload.prices().size(), MAX_PRICES);
        buffer.writeVarInt(size);
        int written = 0;
        for (Map.Entry<ResourceLocation, CurrencyAmount> entry : payload.prices().entrySet()) {
            if (written++ >= size) {
                break;
            }
            buffer.writeResourceLocation(entry.getKey());
            entry.getValue().write(buffer);
        }
    }

    private static SellBoxSyncPayload decode(RegistryFriendlyByteBuf buffer) {
        int containerId = buffer.readVarInt();
        long day = buffer.readVarLong();
        CurrencyAmount balance = CurrencyAmount.read(buffer);
        String name = buffer.readUtf(128);
        String pluralName = buffer.readUtf(128);
        ResourceLocation currencyIconSprite = buffer.readResourceLocation();
        int size = VillagerPayloads.readCollectionSize(buffer, MAX_PRICES, "sell-box prices");
        Map<ResourceLocation, CurrencyAmount> prices = new LinkedHashMap<>();
        for (int index = 0; index < size; index++) {
            prices.put(buffer.readResourceLocation(), CurrencyAmount.read(buffer));
        }
        return new SellBoxSyncPayload(
                containerId,
                day,
                balance,
                name,
                pluralName,
                currencyIconSprite,
                prices);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
