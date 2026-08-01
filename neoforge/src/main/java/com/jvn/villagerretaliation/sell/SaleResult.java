package com.jvn.villagerretaliation.sell;

public record SaleResult(MarketQuote quote, int soldItemCount) {
    public SaleResult {
        if (quote == null || soldItemCount <= 0) {
            throw new IllegalArgumentException("Sale results require a quote and a positive sold item count");
        }
    }

    public CurrencyAmount payout() {
        return this.quote.stackPayout();
    }

    public CurrencyAmount pressureAdded() {
        return this.quote.pressureAdded();
    }
}
