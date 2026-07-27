package com.jvn.villagerretaliation.item;

import com.jvn.villagerretaliation.VillagerRetaliation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import net.minecraft.ChatFormatting;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

/**
 * Owns the complete persistent format and matching semantics for villager item filters.
 * Ordinary entries intentionally retain item identity only, except potion contents. Nested
 * filters retain their configuration and act as additional constraints.
 */
public final class VillagerItemFilterData {
    public static final int ENTRY_COUNT = 9;
    public static final int MAX_NESTING_DEPTH = 8;
    private static final String ROOT_TAG = VillagerRetaliation.MOD_ID + ":item_filter";
    private static final String MODE_TAG = "Mode";
    private static final String ENTRIES_TAG = "Entries";
    private static final String SLOT_TAG = "Slot";
    private static final String ITEM_TAG = "Item";
    private static final String DATA_TAG = "Data";
    private static final String POTION_TAG = "Potion";
    private static final RegistryOps<Tag> POTION_NBT_OPS = RegistryOps.create(
            NbtOps.INSTANCE,
            new RegistryAccess.ImmutableRegistryAccess(
                    List.of(BuiltInRegistries.POTION, BuiltInRegistries.MOB_EFFECT)));

    private final Mode mode;
    private final List<ItemStack> entries;

    private VillagerItemFilterData(Mode mode, List<ItemStack> entries) {
        this.mode = mode == null ? Mode.ALLOWLIST : mode;
        List<ItemStack> normalized = emptyEntries();
        for (int slot = 0; slot < Math.min(ENTRY_COUNT, entries.size()); slot++) {
            ItemStack entry = normalizeEntry(entries.get(slot));
            if (!entry.isEmpty() && normalized.stream().noneMatch(existing -> sameEntry(existing, entry))) {
                normalized.set(slot, entry);
            }
        }
        this.entries = Collections.unmodifiableList(normalized);
    }

    public static VillagerItemFilterData empty() {
        return new VillagerItemFilterData(Mode.ALLOWLIST, emptyEntries());
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
        List<ItemStack> entries = emptyEntries();
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
                ItemStack restored = new ItemStack(item);
                if (VillagerRetaliationItems.isFilter(restored)
                        && entry.contains(DATA_TAG, Tag.TAG_COMPOUND)) {
                    restored.set(DataComponents.CUSTOM_DATA, CustomData.of(entry.getCompound(DATA_TAG).copy()));
                }
                Tag encodedPotion = entry.get(POTION_TAG);
                if (encodedPotion != null) {
                    PotionContents.CODEC.parse(POTION_NBT_OPS, encodedPotion)
                            .result()
                            .ifPresent(contents -> restored.set(DataComponents.POTION_CONTENTS, contents));
                }
                ItemStack normalized = normalizeEntry(restored);
                if (!normalized.isEmpty()
                        && entries.stream().noneMatch(existing -> sameEntry(existing, normalized))) {
                    entries.set(slot, normalized);
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
     * Sets one ghost entry. Empty stacks clear it. Duplicate identities, potion variants, and
     * identically configured nested filters are rejected.
     *
     * @return true when the stored configuration changed
     */
    public static boolean setEntry(ItemStack filter, int slot, ItemStack entry) {
        if (!VillagerRetaliationItems.isItemFilter(filter) || slot < 0 || slot >= ENTRY_COUNT) {
            return false;
        }
        VillagerItemFilterData current = read(filter);
        ItemStack normalizedEntry = normalizeEntry(entry);
        if (VillagerRetaliationItems.isItemFilter(normalizedEntry)
                && (ItemStack.isSameItemSameComponents(filter, normalizedEntry)
                || nestingDepth(normalizedEntry, 0) >= MAX_NESTING_DEPTH)) {
            return false;
        }
        if (!normalizedEntry.isEmpty()) {
            for (int otherSlot = 0; otherSlot < ENTRY_COUNT; otherSlot++) {
                if (otherSlot != slot && sameEntry(current.entries.get(otherSlot), normalizedEntry)) {
                    return false;
                }
            }
        }
        if (sameEntry(current.entries.get(slot), normalizedEntry)) {
            return false;
        }
        List<ItemStack> updated = copyEntries(current.entries);
        updated.set(slot, normalizedEntry);
        write(filter, new VillagerItemFilterData(current.mode, updated));
        return true;
    }

    public static void clear(ItemStack filter) {
        write(filter, empty());
    }

    public static boolean isDefault(ItemStack filter) {
        VillagerItemFilterData data = read(filter);
        return data.mode == Mode.ALLOWLIST && data.entries.stream().allMatch(ItemStack::isEmpty);
    }

    public static void copyConfiguration(ItemStack source, ItemStack target) {
        write(target, read(source));
    }

    public static boolean matches(ItemStack filter, ItemStack candidate) {
        return matches(null, filter, candidate);
    }

    /**
     * Ordinary entries are alternatives; each nested attribute or item filter is an additional
     * required constraint. A nested denylist therefore excludes its matches from an outer
     * allowlist. The outer mode is applied to the complete expression.
     */
    public static boolean matches(Level level, ItemStack filter, ItemStack candidate) {
        return matches(level, filter, candidate, 0);
    }

    private static boolean matches(Level level, ItemStack filter, ItemStack candidate, int depth) {
        if (depth > MAX_NESTING_DEPTH
                || !VillagerRetaliationItems.isItemFilter(filter)
                || candidate == null
                || candidate.isEmpty()) {
            return false;
        }
        VillagerItemFilterData data = read(filter);
        boolean hasIdentityEntries = false;
        boolean identityMatches = false;
        boolean constraintsMatch = true;
        boolean configured = false;
        for (ItemStack entry : data.entries) {
            if (entry.isEmpty()) {
                continue;
            }
            configured = true;
            if (VillagerRetaliationItems.isAttributeFilter(entry)) {
                constraintsMatch &= VillagerAttributeFilterData.matches(level, entry, candidate);
            } else if (VillagerRetaliationItems.isItemFilter(entry)) {
                constraintsMatch &= matches(level, entry, candidate, depth + 1);
            } else {
                hasIdentityEntries = true;
                identityMatches |= identityMatches(entry, candidate);
            }
        }
        boolean listed = configured && (!hasIdentityEntries || identityMatches) && constraintsMatch;
        return data.mode == Mode.ALLOWLIST ? listed : !listed;
    }

    public static List<Component> tooltip(ItemStack filter) {
        VillagerItemFilterData data = read(filter);
        List<Component> tooltip = new ArrayList<>();
        tooltip.add(Component.translatable("item.villagerretaliation.item_filter.mode")
                .withStyle(ChatFormatting.GRAY)
                .append(data.mode.label().copy().withStyle(ChatFormatting.GOLD)));
        int shown = 0;
        for (ItemStack entry : data.entries) {
            if (entry.isEmpty()) {
                continue;
            }
            if (shown == 4) {
                tooltip.add(Component.literal("- ...").withStyle(ChatFormatting.DARK_GRAY));
                break;
            }
            Component description = VillagerRetaliationItems.isAttributeFilter(entry)
                    ? VillagerAttributeFilterData.read(entry).attribute() == null
                            ? entry.getHoverName()
                            : VillagerAttributeFilterData.read(entry).attribute().display()
                    : entry.getHoverName();
            tooltip.add(Component.literal("- ")
                    .withStyle(ChatFormatting.DARK_GRAY)
                    .append(description.copy().withStyle(ChatFormatting.GRAY)));
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
        return this.entries.get(slot).copy();
    }

    private static void write(ItemStack filter, VillagerItemFilterData data) {
        if (!VillagerRetaliationItems.isItemFilter(filter)) {
            return;
        }
        CustomData existing = filter.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag customData = existing.isEmpty() ? new CompoundTag() : existing.copyTag();
        if (data.mode == Mode.ALLOWLIST && data.entries.stream().allMatch(ItemStack::isEmpty)) {
            customData.remove(ROOT_TAG);
        } else {
            CompoundTag root = new CompoundTag();
            root.putString(MODE_TAG, data.mode.id());
            ListTag storedEntries = new ListTag();
            for (int slot = 0; slot < ENTRY_COUNT; slot++) {
                ItemStack configuredEntry = data.entries.get(slot);
                if (configuredEntry.isEmpty()) {
                    continue;
                }
                CompoundTag entry = new CompoundTag();
                entry.putInt(SLOT_TAG, slot);
                entry.putString(ITEM_TAG, BuiltInRegistries.ITEM.getKey(configuredEntry.getItem()).toString());
                if (VillagerRetaliationItems.isFilter(configuredEntry)) {
                    CustomData filterData =
                            configuredEntry.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
                    if (!filterData.isEmpty()) {
                        entry.put(DATA_TAG, filterData.copyTag());
                    }
                }
                PotionContents potionContents = configuredEntry.get(DataComponents.POTION_CONTENTS);
                if (potionContents != null) {
                    PotionContents.CODEC.encodeStart(POTION_NBT_OPS, potionContents)
                            .result()
                            .ifPresent(encoded -> entry.put(POTION_TAG, encoded));
                }
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

    private static List<ItemStack> emptyEntries() {
        List<ItemStack> entries = new ArrayList<>(ENTRY_COUNT);
        for (int slot = 0; slot < ENTRY_COUNT; slot++) {
            entries.add(ItemStack.EMPTY);
        }
        return entries;
    }

    private static List<ItemStack> copyEntries(List<ItemStack> entries) {
        List<ItemStack> copy = new ArrayList<>(ENTRY_COUNT);
        for (ItemStack entry : entries) {
            copy.add(entry.copy());
        }
        return copy;
    }

    private static ItemStack normalizeEntry(ItemStack entry) {
        if (entry == null || entry.isEmpty()) {
            return ItemStack.EMPTY;
        }
        if (VillagerRetaliationItems.isFilter(entry)) {
            ItemStack normalized = new ItemStack(entry.getItem());
            CustomData filterData = entry.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
            if (!filterData.isEmpty()) {
                normalized.set(DataComponents.CUSTOM_DATA, filterData);
            }
            return normalized;
        }
        PotionContents potionContents = entry.get(DataComponents.POTION_CONTENTS);
        if (potionContents != null) {
            ItemStack normalized = new ItemStack(entry.getItem());
            normalized.set(DataComponents.POTION_CONTENTS, potionContents);
            return normalized;
        }
        return new ItemStack(entry.getItem());
    }

    private static boolean identityMatches(ItemStack configured, ItemStack candidate) {
        if (!candidate.is(configured.getItem())) {
            return false;
        }
        PotionContents expectedPotion = configured.get(DataComponents.POTION_CONTENTS);
        return expectedPotion == null
                || Objects.equals(expectedPotion, candidate.get(DataComponents.POTION_CONTENTS));
    }

    private static boolean sameEntry(ItemStack first, ItemStack second) {
        if (first == null || first.isEmpty()) {
            return second == null || second.isEmpty();
        }
        if (second == null || second.isEmpty() || first.getItem() != second.getItem()) {
            return false;
        }
        if (VillagerRetaliationItems.isFilter(first)) {
            return ItemStack.isSameItemSameComponents(first, second);
        }
        PotionContents firstPotion = first.get(DataComponents.POTION_CONTENTS);
        PotionContents secondPotion = second.get(DataComponents.POTION_CONTENTS);
        return firstPotion != null || secondPotion != null
                ? Objects.equals(firstPotion, secondPotion)
                : true;
    }

    private static int nestingDepth(ItemStack filter, int depth) {
        if (depth >= MAX_NESTING_DEPTH || !VillagerRetaliationItems.isItemFilter(filter)) {
            return depth;
        }
        int deepest = depth;
        for (ItemStack entry : read(filter).entries) {
            if (VillagerRetaliationItems.isItemFilter(entry)) {
                deepest = Math.max(deepest, nestingDepth(entry, depth + 1));
            }
        }
        return deepest;
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
