package com.jvn.villagerretaliation.sell;

import java.util.Objects;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public sealed interface ComponentPredicate
        permits ComponentPredicate.Exact, ComponentPredicate.NumericRange {

    DataComponentType<?> type();

    boolean matches(ItemStack stack);

    boolean overlaps(ComponentPredicate other);

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
                    return actualData.matchedBy(expectedData.copyTag());
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
