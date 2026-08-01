package com.jvn.villagerretaliation.sell;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;

/**
 * Non-negative exact currency arithmetic. The sell market deliberately keeps fractions exact so
 * bulk vanilla trades never lose value to rounding.
 */
public record CurrencyAmount(BigInteger numerator, BigInteger denominator) implements Comparable<CurrencyAmount> {
    public static final CurrencyAmount ZERO = new CurrencyAmount(BigInteger.ZERO, BigInteger.ONE);

    public CurrencyAmount {
        if (numerator == null || denominator == null || denominator.signum() <= 0 || numerator.signum() < 0) {
            throw new IllegalArgumentException("Currency amounts must be non-negative with a positive denominator");
        }
        if (numerator.signum() == 0) {
            numerator = BigInteger.ZERO;
            denominator = BigInteger.ONE;
        } else {
            BigInteger divisor = numerator.gcd(denominator);
            numerator = numerator.divide(divisor);
            denominator = denominator.divide(divisor);
        }
    }

    public static CurrencyAmount of(long numerator, long denominator) {
        return new CurrencyAmount(BigInteger.valueOf(numerator), BigInteger.valueOf(denominator));
    }

    public CurrencyAmount add(CurrencyAmount other) {
        if (other == null || other.isZero()) {
            return this;
        }
        if (this.isZero()) {
            return other;
        }
        return new CurrencyAmount(
                this.numerator.multiply(other.denominator).add(other.numerator.multiply(this.denominator)),
                this.denominator.multiply(other.denominator));
    }

    public CurrencyAmount multiply(long factor) {
        if (factor < 0L) {
            throw new IllegalArgumentException("Currency amount multiplier must be non-negative");
        }
        return factor == 0L ? ZERO : new CurrencyAmount(this.numerator.multiply(BigInteger.valueOf(factor)), this.denominator);
    }

    public CurrencyAmount divide(long divisor) {
        if (divisor <= 0L) {
            throw new IllegalArgumentException("Currency amount divisor must be positive");
        }
        return this.isZero()
                ? ZERO
                : new CurrencyAmount(this.numerator, this.denominator.multiply(BigInteger.valueOf(divisor)));
    }

    public CurrencyAmount multiply(CurrencyAmount factor) {
        if (factor == null) {
            throw new IllegalArgumentException("Currency amount multiplier is required");
        }
        if (this.isZero() || factor.isZero()) {
            return ZERO;
        }
        return new CurrencyAmount(
                this.numerator.multiply(factor.numerator),
                this.denominator.multiply(factor.denominator));
    }

    public CurrencyAmount multiplyRatio(long numerator, long denominator) {
        return this.multiply(CurrencyAmount.of(numerator, denominator));
    }

    public CurrencyAmount subtract(CurrencyAmount other) {
        if (other == null || other.isZero()) {
            return this;
        }
        BigInteger resultNumerator = this.numerator.multiply(other.denominator)
                .subtract(other.numerator.multiply(this.denominator));
        if (resultNumerator.signum() < 0) {
            throw new IllegalArgumentException("Currency subtraction cannot produce a negative amount");
        }
        return new CurrencyAmount(resultNumerator, this.denominator.multiply(other.denominator));
    }

    public CurrencyAmount subtractClamped(CurrencyAmount other) {
        return other == null || this.compareTo(other) <= 0 ? ZERO : this.subtract(other);
    }

    public CurrencyAmount min(CurrencyAmount other) {
        if (other == null) {
            throw new IllegalArgumentException("Currency amount is required");
        }
        return this.compareTo(other) <= 0 ? this : other;
    }

    public CurrencyAmount max(CurrencyAmount other) {
        if (other == null) {
            throw new IllegalArgumentException("Currency amount is required");
        }
        return this.compareTo(other) >= 0 ? this : other;
    }

    public BigInteger wholeUnits() {
        return this.numerator.divide(this.denominator);
    }

    public CurrencyAmount withoutWholeUnits(BigInteger units) {
        if (units == null || units.signum() <= 0) {
            return this;
        }
        BigInteger removed = units.multiply(this.denominator);
        if (removed.compareTo(this.numerator) > 0) {
            throw new IllegalArgumentException("Cannot remove more currency than the balance contains");
        }
        return new CurrencyAmount(this.numerator.subtract(removed), this.denominator);
    }

    public boolean isZero() {
        return this.numerator.signum() == 0;
    }

    public boolean isWhole() {
        return this.numerator.mod(this.denominator).signum() == 0;
    }

    public boolean isExactlyRepresentable(int scale) {
        int safeScale = Math.max(0, Math.min(8, scale));
        return BigInteger.TEN.pow(safeScale).mod(this.denominator).signum() == 0;
    }

    public String decimal(int scale) {
        int safeScale = Math.max(0, Math.min(8, scale));
        BigDecimal value = new BigDecimal(this.numerator)
                .divide(new BigDecimal(this.denominator), safeScale, RoundingMode.HALF_UP)
                .stripTrailingZeros();
        return value.scale() < 0 ? value.setScale(0).toPlainString() : value.toPlainString();
    }

    public String mixedFraction() {
        BigInteger[] parts = this.numerator.divideAndRemainder(this.denominator);
        if (parts[1].signum() == 0) {
            return parts[0].toString();
        }
        if (parts[0].signum() == 0) {
            return parts[1] + "/" + this.denominator;
        }
        return parts[0] + " + " + parts[1] + "/" + this.denominator;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Numerator", this.numerator.toString());
        tag.putString("Denominator", this.denominator.toString());
        return tag;
    }

    public static CurrencyAmount load(CompoundTag tag) {
        if (tag == null || !tag.contains("Numerator") || !tag.contains("Denominator")) {
            return ZERO;
        }
        try {
            return new CurrencyAmount(
                    new BigInteger(tag.getString("Numerator")),
                    new BigInteger(tag.getString("Denominator")));
        } catch (IllegalArgumentException exception) {
            return ZERO;
        }
    }

    public void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeUtf(this.numerator.toString(), 512);
        buffer.writeUtf(this.denominator.toString(), 512);
    }

    public static CurrencyAmount read(RegistryFriendlyByteBuf buffer) {
        try {
            return new CurrencyAmount(
                    new BigInteger(buffer.readUtf(512)),
                    new BigInteger(buffer.readUtf(512)));
        } catch (IllegalArgumentException exception) {
            return ZERO;
        }
    }

    @Override
    public int compareTo(CurrencyAmount other) {
        return this.numerator.multiply(other.denominator).compareTo(other.numerator.multiply(this.denominator));
    }
}
