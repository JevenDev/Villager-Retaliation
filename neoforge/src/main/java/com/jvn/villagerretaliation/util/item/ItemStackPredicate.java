package com.jvn.villagerretaliation.util.item;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

public record ItemStackPredicate(
        List<ComponentPredicate> components,
        DurabilityRange durability) {

    public static final ItemStackPredicate ANY = new ItemStackPredicate(List.of(), null);
    private static final int MAX_COMPONENTS = 64;

    public ItemStackPredicate {
        components = components == null ? List.of() : List.copyOf(components);
        if (components.size() > MAX_COMPONENTS) {
            throw new IllegalArgumentException("item predicates support at most " + MAX_COMPONENTS + " components");
        }
    }

    public boolean isAny() {
        return components.isEmpty() && durability == null;
    }

    public int specificity() {
        return components.size() + (durability == null ? 0 : 1);
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

    public boolean overlaps(ItemStackPredicate other) {
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

    public void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(components.size());
        for (ComponentPredicate component : components) {
            component.write(buffer);
        }
        buffer.writeBoolean(durability != null);
        if (durability != null) {
            durability.write(buffer);
        }
    }

    public static ItemStackPredicate read(RegistryFriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        if (size < 0 || size > MAX_COMPONENTS) {
            throw new IllegalArgumentException("invalid item predicate component count " + size);
        }
        java.util.ArrayList<ComponentPredicate> components = new java.util.ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            components.add(ComponentPredicate.read(buffer));
        }
        DurabilityRange durability = buffer.readBoolean() ? DurabilityRange.read(buffer) : null;
        return components.isEmpty() && durability == null
                ? ANY
                : new ItemStackPredicate(components, durability);
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

        private void write(RegistryFriendlyByteBuf buffer) {
            buffer.writeBoolean(min != null);
            if (min != null) {
                buffer.writeVarInt(min);
            }
            buffer.writeBoolean(max != null);
            if (max != null) {
                buffer.writeVarInt(max);
            }
        }

        private static DurabilityRange read(RegistryFriendlyByteBuf buffer) {
            Integer min = buffer.readBoolean() ? buffer.readVarInt() : null;
            Integer max = buffer.readBoolean() ? buffer.readVarInt() : null;
            return new DurabilityRange(min, max);
        }
    }
}
