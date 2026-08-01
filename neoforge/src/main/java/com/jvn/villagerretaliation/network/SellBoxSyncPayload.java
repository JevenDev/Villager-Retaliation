package com.jvn.villagerretaliation.network;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.block.SellBoxBlockEntity;
import com.jvn.villagerretaliation.interaction.VillagerCurrencyResources;
import com.jvn.villagerretaliation.sell.CurrencyAmount;
import com.jvn.villagerretaliation.sell.DailyDemandBand;
import com.jvn.villagerretaliation.sell.MarketQuote;
import com.jvn.villagerretaliation.sell.SellPriceDefinition;
import com.jvn.villagerretaliation.sell.SellPriceResources;
import com.jvn.villagerretaliation.sell.SupplyBand;
import com.jvn.villagerretaliation.sell.VillageSellMarket;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public record SellBoxSyncPayload(
        int containerId,
        long day,
        CurrencyAmount balance,
        String currencyName,
        String currencyPluralName,
        ResourceLocation currencyIconSprite,
        boolean validMarket,
        String villageName,
        Map<ResourceLocation, MarketEntry> entries) implements CustomPacketPayload {
    private static final int MAX_ENTRIES = 4096;
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
        villageName = villageName == null ? "" : villageName;
        entries = entries == null ? Map.of() : Map.copyOf(entries);
    }

    public static void send(ServerPlayer player, int containerId, SellBoxBlockEntity sellBox) {
        VillagerCurrencyResources.Text text = VillagerCurrencyResources.text(player.getServer());
        ServerLevel level = player.serverLevel();
        boolean valid = VillageSellMarket.resolveVillage(
                        VillageAllegianceRegistrySavedData.get(level), level, sellBox.getBlockPos())
                .isPresent();
        String villageName = VillageSellMarket.resolveVillage(
                        VillageAllegianceRegistrySavedData.get(level), level, sellBox.getBlockPos())
                .flatMap(VillageAllegianceRegistrySavedData.get(level)::canonicalRecord)
                .map(VillageAllegianceRegistrySavedData.AllegianceRecord::displayName)
                .orElse("");
        LinkedHashMap<ResourceLocation, MarketEntry> entries = new LinkedHashMap<>();
        if (valid) {
            for (Map.Entry<Item, SellPriceDefinition> definitionEntry
                    : SellPriceResources.definitions(player.getServer()).entrySet()) {
                Item item = definitionEntry.getKey();
                ItemStack unitStack = new ItemStack(item, 1);
                MarketQuote unit = VillageSellMarket.quote(level, sellBox.getBlockPos(), unitStack).orElse(null);
                if (unit == null) {
                    continue;
                }
                int stackSize = Math.max(1, item.getDefaultMaxStackSize());
                MarketQuote stack = VillageSellMarket.quote(
                                level, sellBox.getBlockPos(), new ItemStack(item, stackSize))
                        .orElse(unit);
                entries.put(BuiltInRegistries.ITEM.getKey(item), new MarketEntry(
                        unit.baseUnitPrice(),
                        unit.marketGroup(),
                        unit.dailyDemandBand(),
                        SupplyBand.forPressure(unit.recoveredPressure()),
                        unit.recoveredPressure(),
                        unit.effectiveUnitPrice(),
                        stack.stackPayout(),
                        stackSize));
            }
        }
        PacketDistributor.sendToPlayer(player, new SellBoxSyncPayload(
                containerId,
                VillageSellMarket.currentDay(player.getServer()),
                sellBox.balance(),
                text.name(),
                text.pluralName(),
                text.iconSprite(),
                valid,
                villageName,
                entries));
    }

    private static void encode(RegistryFriendlyByteBuf buffer, SellBoxSyncPayload payload) {
        buffer.writeVarInt(payload.containerId());
        buffer.writeVarLong(payload.day());
        payload.balance().write(buffer);
        buffer.writeUtf(payload.currencyName(), 128);
        buffer.writeUtf(payload.currencyPluralName(), 128);
        buffer.writeResourceLocation(payload.currencyIconSprite());
        buffer.writeBoolean(payload.validMarket());
        buffer.writeUtf(payload.villageName(), 128);
        int size = Math.min(payload.entries().size(), MAX_ENTRIES);
        buffer.writeVarInt(size);
        int written = 0;
        for (Map.Entry<ResourceLocation, MarketEntry> entry : payload.entries().entrySet()) {
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
        boolean validMarket = buffer.readBoolean();
        String villageName = buffer.readUtf(128);
        int size = VillagerPayloads.readCollectionSize(buffer, MAX_ENTRIES, "sell-box market entries");
        Map<ResourceLocation, MarketEntry> entries = new LinkedHashMap<>();
        for (int index = 0; index < size; index++) {
            entries.put(buffer.readResourceLocation(), MarketEntry.read(buffer));
        }
        return new SellBoxSyncPayload(
                containerId, day, balance, name, pluralName, currencyIconSprite,
                validMarket, villageName, entries);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record MarketEntry(
            CurrencyAmount baseUnitPrice,
            ResourceLocation marketGroup,
            DailyDemandBand demandBand,
            SupplyBand supplyBand,
            CurrencyAmount recoveredPressure,
            CurrencyAmount effectiveUnitPrice,
            CurrencyAmount effectiveStackPrice,
            int stackSize) {
        public MarketEntry {
            baseUnitPrice = baseUnitPrice == null ? CurrencyAmount.ZERO : baseUnitPrice;
            marketGroup = marketGroup == null ? ResourceLocation.withDefaultNamespace("air") : marketGroup;
            demandBand = demandBand == null ? DailyDemandBand.NORMAL : demandBand;
            supplyBand = supplyBand == null ? SupplyBand.FRESH : supplyBand;
            recoveredPressure = recoveredPressure == null ? CurrencyAmount.ZERO : recoveredPressure;
            effectiveUnitPrice = effectiveUnitPrice == null ? CurrencyAmount.ZERO : effectiveUnitPrice;
            effectiveStackPrice = effectiveStackPrice == null ? CurrencyAmount.ZERO : effectiveStackPrice;
            stackSize = Math.clamp(stackSize, 1, 99);
        }

        private void write(RegistryFriendlyByteBuf buffer) {
            this.baseUnitPrice.write(buffer);
            buffer.writeResourceLocation(this.marketGroup);
            buffer.writeVarInt(this.demandBand.ordinal());
            buffer.writeVarInt(this.supplyBand.ordinal());
            this.recoveredPressure.write(buffer);
            this.effectiveUnitPrice.write(buffer);
            this.effectiveStackPrice.write(buffer);
            buffer.writeVarInt(this.stackSize);
        }

        private static MarketEntry read(RegistryFriendlyByteBuf buffer) {
            CurrencyAmount base = CurrencyAmount.read(buffer);
            ResourceLocation group = buffer.readResourceLocation();
            int demandOrdinal = buffer.readVarInt();
            int supplyOrdinal = buffer.readVarInt();
            DailyDemandBand[] demands = DailyDemandBand.values();
            SupplyBand[] supplies = SupplyBand.values();
            return new MarketEntry(
                    base,
                    group,
                    demandOrdinal >= 0 && demandOrdinal < demands.length
                            ? demands[demandOrdinal] : DailyDemandBand.NORMAL,
                    supplyOrdinal >= 0 && supplyOrdinal < supplies.length
                            ? supplies[supplyOrdinal] : SupplyBand.FRESH,
                    CurrencyAmount.read(buffer),
                    CurrencyAmount.read(buffer),
                    CurrencyAmount.read(buffer),
                    buffer.readVarInt());
        }
    }
}
