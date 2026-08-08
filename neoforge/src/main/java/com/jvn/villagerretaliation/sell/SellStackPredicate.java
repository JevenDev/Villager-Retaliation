package com.jvn.villagerretaliation.sell;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.ItemStack;

public record SellStackPredicate(
        List<ComponentPredicate> components,
        DurabilityRange durability) {

    public static final SellStackPredicate ANY = new SellStackPredicate(List.of(), null);

    public SellStackPredicate {
        components = components == null ? List.of() : List.copyOf(components);
    }

    public boolean matches(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        for (ComponentPredicate component : components) {
            if (!component.matches(stack)) {
                return false;
            }
        }
        return durability == null || durability.matches(stack);
    }

    public boolean overlaps(SellStackPredicate other) {
        if (other == null) {
            return true;
        }
        Map<DataComponentType<?>, ComponentPredicate> otherComponents = new IdentityHashMap<>();
        for (ComponentPredicate component : other.components) {
            otherComponents.put(component.type(), component);
        }
        for (ComponentPredicate component : components) {
            ComponentPredicate otherComponent = otherComponents.get(component.type());
            if (otherComponent != null && !component.overlaps(otherComponent)) {
                return false;
            }
        }
        return durability == null || other.durability == null || durability.overlaps(other.durability);
    }

    public record DurabilityRange(Integer min, Integer max) {
        public DurabilityRange {
            if ((min == null && max == null)
                    || (min != null && min < 0)
                    || (max != null && max < 0)
                    || (min != null && max != null && min > max)) {
                throw new IllegalArgumentException(
                        "durability requires non-negative, ordered min and/or max bounds");
            }
        }

        public boolean matches(ItemStack stack) {
            if (stack == null || stack.isEmpty() || !stack.isDamageableItem()) {
                return false;
            }
            int remaining = stack.getMaxDamage() - stack.getDamageValue();
            return (min == null || remaining >= min) && (max == null || remaining <= max);
        }

        boolean overlaps(DurabilityRange other) {
            int lower = Math.max(min == null ? 0 : min, other.min == null ? 0 : other.min);
            int upper = Math.min(
                    max == null ? Integer.MAX_VALUE : max,
                    other.max == null ? Integer.MAX_VALUE : other.max);
            return lower <= upper;
        }
    }
}
