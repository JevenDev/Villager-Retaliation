package com.jvn.villagerretaliation.client.inventory;

import com.jvn.villagerretaliation.block.SellBoxMenu;
import com.jvn.villagerretaliation.network.SellBoxSyncPayload;
import com.jvn.villagerretaliation.sell.CurrencyAmount;
import com.jvn.villagerretaliation.sell.SupplyBand;
import com.jvn.villagerretaliation.sell.VillageMarketPolicy;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

public final class SellBoxClientState {
    private static final ResourceLocation DEFAULT_CURRENCY_ICON =
            ResourceLocation.withDefaultNamespace("item/emerald");
    private static final BigInteger THOUSAND = BigInteger.valueOf(1_000L);
    private static final BigInteger MILLION = BigInteger.valueOf(1_000_000L);
    private static final BigInteger BILLION = BigInteger.valueOf(1_000_000_000L);
    private static final BigInteger TRILLION = BigInteger.valueOf(1_000_000_000_000L);
    private static Snapshot snapshot = Snapshot.empty();

    private SellBoxClientState() {
    }

    public static void accept(SellBoxSyncPayload payload) {
        snapshot = new Snapshot(
                payload.containerId(),
                payload.day(),
                payload.balance(),
                payload.currencyName(),
                payload.currencyPluralName(),
                payload.currencyIconSprite(),
                payload.validMarket(),
                payload.villageName(),
                payload.entries());
    }

    public static Snapshot snapshot(int containerId) {
        return snapshot.containerId() == containerId ? snapshot : Snapshot.empty();
    }

    public static void onItemTooltip(ItemTooltipEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.screen instanceof SellBoxScreen)
                || !(minecraft.player != null && minecraft.player.containerMenu instanceof SellBoxMenu menu)
                || menu.containerId != snapshot.containerId()
                || event.getItemStack().isEmpty()) {
            return;
        }
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(event.getItemStack().getItem());
        SellBoxSyncPayload.MarketEntry entry = snapshot.entries().get(itemId);
        if (entry == null || entry.effectiveUnitPrice().isZero()) {
            return;
        }
        CurrencyAmount total = payout(entry, event.getItemStack().getCount());
        event.getToolTip().add(Component.translatable(
                "villagerretaliation.sell_box.price",
                decimalCurrency(total, snapshot)).withStyle(ChatFormatting.GREEN));
        if (!total.isExactlyRepresentable(2)) {
            event.getToolTip().add(Component.translatable(
                    "villagerretaliation.sell_box.exact",
                    exactCurrency(total, snapshot)).withStyle(ChatFormatting.DARK_GREEN));
        }
    }

    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        snapshot = Snapshot.empty();
    }

    public static CurrencyAmount payout(SellBoxSyncPayload.MarketEntry entry, int itemCount) {
        if (entry == null || itemCount <= 0) {
            return CurrencyAmount.ZERO;
        }
        CurrencyAmount remaining = entry.baseUnitPrice().multiply(itemCount);
        CurrencyAmount cursor = entry.recoveredPressure();
        CurrencyAmount payout = CurrencyAmount.ZERO;
        while (!remaining.isZero()) {
            SupplyBand band = SupplyBand.forPressure(cursor);
            CurrencyAmount segment;
            if (band.upperBound().isPresent()) {
                segment = remaining.min(band.upperBound().get().subtractClamped(cursor));
            } else {
                segment = remaining;
            }
            if (segment.isZero()) {
                return payout;
            }
            payout = payout.add(segment.multiply(
                    VillageMarketPolicy.effectiveMultiplier(entry.demandBand(), band)));
            cursor = cursor.add(segment);
            remaining = remaining.subtract(segment);
        }
        return payout;
    }

    public static String decimalCurrency(CurrencyAmount amount, Snapshot value) {
        return amount.decimal(2) + " " + currencyName(amount, value);
    }

    public static String compactCurrency(CurrencyAmount amount, Snapshot value) {
        CompactUnit unit = CompactUnit.forAmount(amount);
        String number = unit == CompactUnit.NONE
                ? amount.decimal(2)
                : compactNumber(amount, unit.divisor()) + unit.suffix();
        return number + " " + currencyName(amount, value);
    }

    public static String exactCurrency(CurrencyAmount amount, Snapshot value) {
        return amount.mixedFraction() + " " + currencyName(amount, value);
    }

    private static String currencyName(CurrencyAmount amount, Snapshot value) {
        String name = amount.isWhole() && BigInteger.ONE.equals(amount.wholeUnits())
                ? value.currencyName()
                : value.currencyPluralName();
        return titleCase(name);
    }

    private static String compactNumber(CurrencyAmount amount, BigInteger divisor) {
        BigDecimal scaled = new BigDecimal(amount.numerator())
                .divide(
                        new BigDecimal(amount.denominator().multiply(divisor)),
                        1,
                        RoundingMode.HALF_UP)
                .stripTrailingZeros();
        return scaled.scale() < 0 ? scaled.setScale(0).toPlainString() : scaled.toPlainString();
    }

    private static String titleCase(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1);
    }

    public record Snapshot(
            int containerId,
            long day,
            CurrencyAmount balance,
            String currencyName,
            String currencyPluralName,
            ResourceLocation currencyIconSprite,
            boolean validMarket,
            String villageName,
            Map<ResourceLocation, SellBoxSyncPayload.MarketEntry> entries) {
        public Snapshot {
            balance = balance == null ? CurrencyAmount.ZERO : balance;
            currencyName = currencyName == null ? "emerald" : currencyName;
            currencyPluralName = currencyPluralName == null ? "emeralds" : currencyPluralName;
            currencyIconSprite = currencyIconSprite == null
                    ? DEFAULT_CURRENCY_ICON
                    : currencyIconSprite;
            villageName = villageName == null ? "" : villageName;
            entries = entries == null ? Map.of() : Map.copyOf(entries);
        }

        public static Snapshot empty() {
            return new Snapshot(
                    -1,
                    0L,
                    CurrencyAmount.ZERO,
                    "emerald",
                    "emeralds",
                    DEFAULT_CURRENCY_ICON,
                    false,
                    "",
                    Map.of());
        }
    }

    private enum CompactUnit {
        NONE(BigInteger.ONE, ""),
        THOUSANDS(THOUSAND, "K"),
        MILLIONS(MILLION, "M"),
        BILLIONS(BILLION, "B"),
        TRILLIONS(TRILLION, "T");

        private final BigInteger divisor;
        private final String suffix;

        CompactUnit(BigInteger divisor, String suffix) {
            this.divisor = divisor;
            this.suffix = suffix;
        }

        private static CompactUnit forAmount(CurrencyAmount amount) {
            BigInteger numerator = amount.numerator();
            BigInteger denominator = amount.denominator();
            if (numerator.compareTo(TRILLION.multiply(denominator)) >= 0) {
                return TRILLIONS;
            }
            if (numerator.compareTo(BILLION.multiply(denominator)) >= 0) {
                return BILLIONS;
            }
            if (numerator.compareTo(MILLION.multiply(denominator)) >= 0) {
                return MILLIONS;
            }
            if (numerator.compareTo(THOUSAND.multiply(denominator)) >= 0) {
                return THOUSANDS;
            }
            return NONE;
        }

        private BigInteger divisor() {
            return this.divisor;
        }

        private String suffix() {
            return this.suffix;
        }
    }
}
