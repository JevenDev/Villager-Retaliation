package com.jvn.villagerretaliation.item;

import com.jvn.villagerretaliation.VillagerRetaliation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * Owns the complete persistent format and matching semantics for villager item filters.
 * Filter entries intentionally retain item identity only; stack components and counts are
 * never copied into the persistent representation.
 */
public final class VillagerItemFilterData {
    public static final int ENTRY_COUNT = 9;
    private static final String ROOT_TAG = VillagerRetaliation.MOD_ID + ":item_filter";
    private static final String MODE_TAG = "Mode";
    private static final String ENTRIES_TAG = "Entries";
    private static final String SLOT_TAG = "Slot";
    private static final String ITEM_TAG = "Item";

    private final Mode mode;
    private final List<Item> entries;

    private VillagerItemFilterData(Mode mode, List<Item> entries) {
        this.mode = mode == null ? Mode.ALLOWLIST : mode;
        List<Item> normalized = new ArrayList<>(Collections.nCopies(ENTRY_COUNT, null));
        for (int slot = 0; slot < Math.min(ENTRY_COUNT, entries.size()); slot++) {
            Item item = entries.get(slot);
            if (item != null && !normalized.contains(item)) {
                normalized.set(slot, item);
            }
        }
        this.entries = Collections.unmodifiableList(normalized);
    }

    public static VillagerItemFilterData empty() {
        return new VillagerItemFilterData(Mode.ALLOWLIST, Collections.nCopies(ENTRY_COUNT, null));
    }

    public static VillagerItemFilterData read(ItemStack filter) {
        if (!VillagerRetaliationItems.isItemFilter(filter)) {
            return empty();
        }
        CustomData customData = filter.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        if (customData.isEmpty()) {
            return empty();
        }
        CompoundTag root = customData.copyTag().getCompound(ROOT_TAG);
        if (root.isEmpty()) {
            return empty();
        }
        Mode mode = Mode.byId(root.getString(MODE_TAG));
        List<Item> entries = new ArrayList<>(Collections.nCopies(ENTRY_COUNT, null));
        ListTag storedEntries = root.getList(ENTRIES_TAG, Tag.TAG_COMPOUND);
        for (Tag rawEntry : storedEntries) {
            if (!(rawEntry instanceof CompoundTag entry)) {
                continue;
            }
            int slot = entry.getInt(SLOT_TAG);
            ResourceLocation itemId = ResourceLocation.tryParse(entry.getString(ITEM_TAG));
            if (slot < 0 || slot >= ENTRY_COUNT || itemId == null) {
                continue;
            }
            BuiltInRegistries.ITEM.getOptional(itemId).ifPresent(item -> {
                if (!entries.contains(item)) {
                    entries.set(slot, item);
                }
            });
        }
        return new VillagerItemFilterData(mode, entries);
    }

    public static Mode mode(ItemStack filter) {
        return read(filter).mode;
    }

    public static void setMode(ItemStack filter, Mode mode) {
        VillagerItemFilterData current = read(filter);
        write(filter, new VillagerItemFilterData(mode, current.entries));
    }

    public static void toggleMode(ItemStack filter) {
        setMode(filter, mode(filter).opposite());
    }

    public static ItemStack entry(ItemStack filter, int slot) {
        return read(filter).entry(slot);
    }

    public static List<ItemStack> entries(ItemStack filter) {
        VillagerItemFilterData data = read(filter);
        List<ItemStack> entries = new ArrayList<>(ENTRY_COUNT);
        for (int slot = 0; slot < ENTRY_COUNT; slot++) {
            entries.add(data.entry(slot));
        }
        return List.copyOf(entries);
    }

    /**
     * Sets one ghost entry. Empty stacks clear the entry. Duplicate item identities are rejected.
     *
     * @return true when the stored configuration changed
     */
    public static boolean setEntry(ItemStack filter, int slot, ItemStack entry) {
        if (!VillagerRetaliationItems.isItemFilter(filter) || slot < 0 || slot >= ENTRY_COUNT) {
            return false;
        }
        VillagerItemFilterData current = read(filter);
        Item item = entry == null || entry.isEmpty() ? null : entry.getItem();
        if (item != null) {
            for (int otherSlot = 0; otherSlot < ENTRY_COUNT; otherSlot++) {
                if (otherSlot != slot && current.entries.get(otherSlot) == item) {
                    return false;
                }
            }
        }
        if (current.entries.get(slot) == item) {
            return false;
        }
        List<Item> updated = new ArrayList<>(current.entries);
        updated.set(slot, item);
        write(filter, new VillagerItemFilterData(current.mode, updated));
        return true;
    }

    public static void clear(ItemStack filter) {
        write(filter, empty());
    }

    public static boolean isDefault(ItemStack filter) {
        VillagerItemFilterData data = read(filter);
        return data.mode == Mode.ALLOWLIST && data.entries.stream().allMatch(item -> item == null);
    }

    public static void copyConfiguration(ItemStack source, ItemStack target) {
        write(target, read(source));
    }

    public static boolean matches(ItemStack filter, ItemStack candidate) {
        if (!VillagerRetaliationItems.isItemFilter(filter) || candidate == null || candidate.isEmpty()) {
            return false;
        }
        VillagerItemFilterData data = read(filter);
        boolean listed = data.entries.stream().anyMatch(item -> item != null && candidate.is(item));
        return data.mode == Mode.ALLOWLIST ? listed : !listed;
    }

    public static List<Component> tooltip(ItemStack filter) {
        VillagerItemFilterData data = read(filter);
        List<Component> tooltip = new ArrayList<>();
        tooltip.add(Component.translatable("item.villagerretaliation.item_filter.mode")
                .withStyle(ChatFormatting.GRAY)
                .append(data.mode.label().copy().withStyle(ChatFormatting.GOLD)));
        int shown = 0;
        for (Item item : data.entries) {
            if (item == null) {
                continue;
            }
            if (shown == 4) {
                tooltip.add(Component.literal("- ...").withStyle(ChatFormatting.DARK_GRAY));
                break;
            }
            tooltip.add(Component.literal("- ")
                    .withStyle(ChatFormatting.DARK_GRAY)
                    .append(item.getDescription().copy().withStyle(ChatFormatting.GRAY)));
            shown++;
        }
        if (shown == 0) {
            tooltip.add(Component.translatable("item.villagerretaliation.item_filter.empty")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        tooltip.add(Component.translatable("item.villagerretaliation.item_filter.controls.toggle")
                .withStyle(ChatFormatting.DARK_GRAY));
        return List.copyOf(tooltip);
    }

    private ItemStack entry(int slot) {
        if (slot < 0 || slot >= ENTRY_COUNT) {
            return ItemStack.EMPTY;
        }
        Item item = this.entries.get(slot);
        return item == null ? ItemStack.EMPTY : new ItemStack(item, 1);
    }

    private static void write(ItemStack filter, VillagerItemFilterData data) {
        if (!VillagerRetaliationItems.isItemFilter(filter)) {
            return;
        }
        CustomData existing = filter.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag customData = existing.isEmpty() ? new CompoundTag() : existing.copyTag();
        if (data.mode == Mode.ALLOWLIST && data.entries.stream().allMatch(item -> item == null)) {
            customData.remove(ROOT_TAG);
        } else {
            CompoundTag root = new CompoundTag();
            root.putString(MODE_TAG, data.mode.id());
            ListTag storedEntries = new ListTag();
            for (int slot = 0; slot < ENTRY_COUNT; slot++) {
                Item item = data.entries.get(slot);
                if (item == null) {
                    continue;
                }
                CompoundTag entry = new CompoundTag();
                entry.putInt(SLOT_TAG, slot);
                entry.putString(ITEM_TAG, BuiltInRegistries.ITEM.getKey(item).toString());
                storedEntries.add(entry);
            }
            root.put(ENTRIES_TAG, storedEntries);
            customData.put(ROOT_TAG, root);
        }
        if (customData.isEmpty()) {
            filter.remove(DataComponents.CUSTOM_DATA);
        } else {
            filter.set(DataComponents.CUSTOM_DATA, CustomData.of(customData));
        }
    }

    public enum Mode {
        ALLOWLIST("allowlist"),
        DENYLIST("denylist");

        private final String id;

        Mode(String id) {
            this.id = id;
        }

        public String id() {
            return this.id;
        }

        public Component label() {
            return Component.translatable("item.villagerretaliation.item_filter.mode." + this.id);
        }

        public Mode opposite() {
            return this == ALLOWLIST ? DENYLIST : ALLOWLIST;
        }

        public static Mode byId(String id) {
            return DENYLIST.id.equalsIgnoreCase(id) ? DENYLIST : ALLOWLIST;
        }
    }
}
