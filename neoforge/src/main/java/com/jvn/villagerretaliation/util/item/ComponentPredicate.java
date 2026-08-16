package com.jvn.villagerretaliation.util.item;

import java.math.BigDecimal;
import java.util.Objects;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public sealed interface ComponentPredicate
        permits ComponentPredicate.Exact, ComponentPredicate.NumericRange {

    DataComponentType<?> type();

    boolean matches(ItemStack stack);

    boolean overlaps(ComponentPredicate other);

    default int specificity() {
        return 1;
    }

    default void write(RegistryFriendlyByteBuf buffer) {
        DataComponentType.STREAM_CODEC.encode(buffer, this.type());
        if (this instanceof Exact<?> exact) {
            buffer.writeBoolean(true);
            writeExactValue(buffer, exact.type(), exact.expected());
            return;
        }
        NumericRange range = (NumericRange) this;
        buffer.writeBoolean(false);
        buffer.writeBoolean(range.min() != null);
        if (range.min() != null) {
            buffer.writeDouble(range.min());
        }
        buffer.writeBoolean(range.max() != null);
        if (range.max() != null) {
            buffer.writeDouble(range.max());
        }
    }

    static ComponentPredicate read(RegistryFriendlyByteBuf buffer) {
        DataComponentType<?> type = DataComponentType.STREAM_CODEC.decode(buffer);
        if (buffer.readBoolean()) {
            return exactUnchecked(type, readExactValue(buffer, type));
        }
        Double min = buffer.readBoolean() ? buffer.readDouble() : null;
        Double max = buffer.readBoolean() ? buffer.readDouble() : null;
        return new NumericRange(type, min, max);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void writeExactValue(
            RegistryFriendlyByteBuf buffer,
            DataComponentType type,
            Object value) {
        type.streamCodec().encode(buffer, value);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object readExactValue(RegistryFriendlyByteBuf buffer, DataComponentType type) {
        return type.streamCodec().decode(buffer);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static ComponentPredicate exactUnchecked(DataComponentType type, Object value) {
        return new Exact(type, value);
    }

    record Exact<T>(DataComponentType<T> type, T expected) implements ComponentPredicate {
        public Exact {
            if (type == null || expected == null) {
                throw new IllegalArgumentException("Exact component predicates require a type and value");
            }
        }

        @Override
        public boolean matches(ItemStack stack) {
            if (stack == null || stack.isEmpty()) {
                return false;
            }
            try {
                T actual = stack.get(type);
                if (actual == null) {
                    return false;
                }
                if (type == DataComponents.CUSTOM_DATA
                        && actual instanceof CustomData actualData
                        && expected instanceof CustomData expectedData) {
                    return matchesCustomData(actualData, expectedData);
                }
                return Objects.equals(actual, expected);
            } catch (RuntimeException ignored) {
                return false;
            }
        }

        @Override
        public boolean overlaps(ComponentPredicate other) {
            if (other == null || type != other.type()) {
                return true;
            }
            if (other instanceof Exact<?> exact) {
                if (type == DataComponents.CUSTOM_DATA) {
                    return true;
                }
                return Objects.equals(expected, exact.expected());
            }
            if (other instanceof NumericRange range && expected instanceof Number number) {
                return range.contains(number.doubleValue());
            }
            return true;
        }
    }

    private static boolean matchesCustomData(CustomData actual, CustomData expected) {
        return matchesNbtSubset(expected.copyTag(), actual.copyTag());
    }

    private static boolean matchesNbtSubset(Tag expected, Tag actual) {
        if (expected == null || actual == null) {
            return expected == actual;
        }
        if (NbtUtils.compareNbt(expected, actual, true)) {
            return true;
        }
        if (expected instanceof NumericTag expectedNumber && actual instanceof NumericTag actualNumber) {
            return numericValuesEqual(expectedNumber, actualNumber);
        }
        if (expected instanceof CompoundTag expectedCompound
                && actual instanceof CompoundTag actualCompound) {
            for (String key : expectedCompound.getAllKeys()) {
                if (!matchesNbtSubset(expectedCompound.get(key), actualCompound.get(key))) {
                    return false;
                }
            }
            return true;
        }
        if (expected instanceof ListTag expectedList && actual instanceof ListTag actualList) {
            if (expectedList.isEmpty()) {
                return actualList.isEmpty();
            }
            if (actualList.size() < expectedList.size()) {
                return false;
            }
            for (Tag expectedEntry : expectedList) {
                boolean found = false;
                for (Tag actualEntry : actualList) {
                    if (matchesNbtSubset(expectedEntry, actualEntry)) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    private static boolean numericValuesEqual(NumericTag first, NumericTag second) {
        try {
            return new BigDecimal(first.getAsNumber().toString())
                            .compareTo(new BigDecimal(second.getAsNumber().toString()))
                    == 0;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    record NumericRange(DataComponentType<?> type, Double min, Double max)
            implements ComponentPredicate {
        public NumericRange {
            if (type == null || (min == null && max == null)) {
                throw new IllegalArgumentException("Numeric component ranges require a type and bound");
            }
            if ((min != null && !Double.isFinite(min))
                    || (max != null && !Double.isFinite(max))
                    || (min != null && max != null && min > max)) {
                throw new IllegalArgumentException("Numeric component range bounds must be finite and ordered");
            }
        }

        @Override
        public boolean matches(ItemStack stack) {
            if (stack == null || stack.isEmpty()) {
                return false;
            }
            try {
                Object actual = stack.get(type);
                return actual instanceof Number number && contains(number.doubleValue());
            } catch (RuntimeException ignored) {
                return false;
            }
        }

        boolean contains(double value) {
            return Double.isFinite(value)
                    && (min == null || value >= min)
                    && (max == null || value <= max);
        }

        @Override
        public boolean overlaps(ComponentPredicate other) {
            if (other == null || type != other.type()) {
                return true;
            }
            if (other instanceof Exact<?> exact && exact.expected() instanceof Number number) {
                return contains(number.doubleValue());
            }
            if (other instanceof NumericRange range) {
                double lower = Math.max(
                        min == null ? Double.NEGATIVE_INFINITY : min,
                        range.min == null ? Double.NEGATIVE_INFINITY : range.min);
                double upper = Math.min(
                        max == null ? Double.POSITIVE_INFINITY : max,
                        range.max == null ? Double.POSITIVE_INFINITY : range.max);
                return lower <= upper;
            }
            return true;
        }
    }
}
