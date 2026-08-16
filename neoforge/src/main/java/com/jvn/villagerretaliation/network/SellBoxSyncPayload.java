package com.jvn.villagerretaliation.network;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.block.SellBoxBlockEntity;
import com.jvn.villagerretaliation.interaction.VillagerCurrencyResources;
import com.jvn.villagerretaliation.sell.CurrencyAmount;
import com.jvn.villagerretaliation.sell.DailyDemandBand;
import com.jvn.villagerretaliation.sell.MarketQuote;
import com.jvn.villagerretaliation.sell.SellBoxMarketSyncService;
import com.jvn.villagerretaliation.sell.SupplyBand;
import com.jvn.villagerretaliation.sell.VillageSellMarket;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public record SellBoxSyncPayload(
        int containerId,
        long day,
        CurrencyAmount balance,
        MarketEntry pendingEntry,
        String currencyName,
        String currencyPluralName,
        ResourceLocation currencyIconSprite,
        boolean validMarket,
        String villageName,
        boolean replaceEntries,
        List<QuotedStack> entries) implements CustomPacketPayload {
    private static final int MAX_ENTRIES = 64;
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
        entries = entries == null ? List.of() : List.copyOf(entries);
    }

    public static void send(ServerPlayer player, int containerId, SellBoxBlockEntity sellBox) {
        VillagerCurrencyResources.Text text = VillagerCurrencyResources.text(player.getServer());
        ServerLevel level = player.serverLevel();
        VillageAllegianceRegistrySavedData registry = VillageAllegianceRegistrySavedData.get(level);
        Optional<VillageAllegianceId> village =
                VillageSellMarket.resolveVillage(registry, level, sellBox.getBlockPos());
        boolean valid = village.isPresent();
        String villageName = village
                .flatMap(registry::canonicalRecord)
                .map(VillageAllegianceRegistrySavedData.AllegianceRecord::displayName)
                .orElse("");
        MarketEntry pendingEntry = VillageSellMarket.quote(
                        level, sellBox.getBlockPos(), sellBox.getItem(0))
                .map(quote -> marketEntry(quote, sellBox.getItem(0).getCount()))
                .orElse(null);
        boolean replaceEntries = SellBoxMarketSyncService.shouldSyncEntries(player, sellBox, containerId);
        ArrayList<QuotedStack> entries = new ArrayList<>();
        if (valid && replaceEntries) {
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                addQuotedStack(entries, level, sellBox, player.getInventory().getItem(slot));
            }
        }
        PacketDistributor.sendToPlayer(player, new SellBoxSyncPayload(
                containerId,
                VillageSellMarket.currentDay(player.getServer()),
                sellBox.balance(),
                pendingEntry,
                text.name(),
                text.pluralName(),
                text.iconSprite(),
                valid,
                villageName,
                replaceEntries,
                entries));
    }

    private static void addQuotedStack(
            List<QuotedStack> entries,
            ServerLevel level,
            SellBoxBlockEntity sellBox,
            ItemStack stack) {
        if (stack.isEmpty() || entries.stream()
                .anyMatch(entry -> ItemStack.isSameItemSameComponents(entry.stack(), stack))) {
            return;
        }
        ItemStack unitStack = stack.copyWithCount(1);
        VillageSellMarket.quote(level, sellBox.getBlockPos(), unitStack)
                .map(quote -> new QuotedStack(unitStack, marketEntry(quote, 1)))
                .ifPresent(entries::add);
    }

    private static MarketEntry marketEntry(MarketQuote quote, int stackSize) {
        return marketEntry(quote, quote.stackPayout(), stackSize);
    }

    private static MarketEntry marketEntry(
            MarketQuote quote,
            CurrencyAmount stackPayout,
            int stackSize) {
        return new MarketEntry(
                quote.baseUnitPrice(),
                quote.marketGroup(),
                quote.dailyDemandBand(),
                SupplyBand.forPressure(quote.recoveredPressure()),
                quote.recoveredPressure(),
                quote.effectiveUnitPrice(),
                stackPayout,
                stackSize);
    }

    private static void encode(RegistryFriendlyByteBuf buffer, SellBoxSyncPayload payload) {
        buffer.writeVarInt(payload.containerId());
        buffer.writeVarLong(payload.day());
        payload.balance().write(buffer);
        buffer.writeBoolean(payload.pendingEntry() != null);
        if (payload.pendingEntry() != null) {
            payload.pendingEntry().write(buffer);
        }
        buffer.writeUtf(payload.currencyName(), 128);
        buffer.writeUtf(payload.currencyPluralName(), 128);
        buffer.writeResourceLocation(payload.currencyIconSprite());
        buffer.writeBoolean(payload.validMarket());
        buffer.writeUtf(payload.villageName(), 128);
        buffer.writeBoolean(payload.replaceEntries());
        int size = Math.min(payload.entries().size(), MAX_ENTRIES);
        buffer.writeVarInt(size);
        for (int index = 0; index < size; index++) {
            payload.entries().get(index).write(buffer);
        }
    }

    private static SellBoxSyncPayload decode(RegistryFriendlyByteBuf buffer) {
        int containerId = buffer.readVarInt();
        long day = buffer.readVarLong();
        CurrencyAmount balance = CurrencyAmount.read(buffer);
        MarketEntry pendingEntry = buffer.readBoolean() ? MarketEntry.read(buffer) : null;
        String name = buffer.readUtf(128);
        String pluralName = buffer.readUtf(128);
        ResourceLocation currencyIconSprite = buffer.readResourceLocation();
        boolean validMarket = buffer.readBoolean();
        String villageName = buffer.readUtf(128);
        boolean replaceEntries = buffer.readBoolean();
        int size = VillagerPayloads.readCollectionSize(buffer, MAX_ENTRIES, "sell-box market entries");
        List<QuotedStack> entries = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            entries.add(QuotedStack.read(buffer));
        }
        return new SellBoxSyncPayload(
                containerId, day, balance, pendingEntry, name, pluralName, currencyIconSprite,
                validMarket, villageName, replaceEntries, entries);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record QuotedStack(ItemStack stack, MarketEntry entry) {
        public QuotedStack {
            if (stack == null || stack.isEmpty() || entry == null) {
                throw new IllegalArgumentException("Quoted sell-box stacks require an item and market entry");
            }
            stack = stack.copyWithCount(1);
        }

        private void write(RegistryFriendlyByteBuf buffer) {
            ItemStack.STREAM_CODEC.encode(buffer, this.stack);
            this.entry.write(buffer);
        }

        private static QuotedStack read(RegistryFriendlyByteBuf buffer) {
            return new QuotedStack(ItemStack.STREAM_CODEC.decode(buffer), MarketEntry.read(buffer));
        }
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
