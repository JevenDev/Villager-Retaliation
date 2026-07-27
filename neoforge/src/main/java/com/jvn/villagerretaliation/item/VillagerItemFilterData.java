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
    public static final int MAX_ENTRY_AMOUNT = 1000;
    public static final int UNLIMITED_AMOUNT = 0;
    private static final String ROOT_TAG = VillagerRetaliation.MOD_ID + ":item_filter";
    private static final String MODE_TAG = "Mode";
    private static final String ENTRIES_TAG = "Entries";
    private static final String SLOT_TAG = "Slot";
    private static final String ITEM_TAG = "Item";
    private static final String DATA_TAG = "Data";
    private static final String POTION_TAG = "Potion";
    private static final String AMOUNT_TAG = "Amount";
    private static final RegistryOps<Tag> POTION_NBT_OPS = RegistryOps.create(
            NbtOps.INSTANCE,
            new RegistryAccess.ImmutableRegistryAccess(
                    List.of(BuiltInRegistries.POTION, BuiltInRegistries.MOB_EFFECT)));

    private final Mode mode;
    private final List<ConfiguredEntry> entries;

    private VillagerItemFilterData(Mode mode, List<ConfiguredEntry> entries) {
        this.mode = mode == null ? Mode.ALLOWLIST : mode;
        List<ConfiguredEntry> normalized = emptyEntries();
        for (int slot = 0; slot < Math.min(ENTRY_COUNT, entries.size()); slot++) {
            ConfiguredEntry rawEntry = entries.get(slot);
            ItemStack stack = normalizeEntry(rawEntry.stack());
            if (stack.isEmpty()) {
                continue;
            }
            int amount = isAmountEntry(stack)
                    ? Math.clamp(rawEntry.amount(), UNLIMITED_AMOUNT, MAX_ENTRY_AMOUNT)
                    : UNLIMITED_AMOUNT;
            boolean valid = true;
            for (ConfiguredEntry existing : normalized) {
                if (existing.stack().isEmpty() || !sameEntry(existing.stack(), stack)) {
                    continue;
                }
                if (!isAmountEntry(stack)
                        || amount == UNLIMITED_AMOUNT
                        || existing.amount() == UNLIMITED_AMOUNT) {
                    valid = false;
                    break;
                }
            }
            if (valid) {
                normalized.set(slot, new ConfiguredEntry(stack, amount));
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
        List<ConfiguredEntry> entries = emptyEntries();
        ListTag storedEntries = root.getList(ENTRIES_TAG, Tag.TAG_COMPOUND);
        for (Tag rawEntry : storedEntries) {
            if (!(rawEntry instanceof CompoundTag entry)) {
                continue;
            }
            int slot = entry.getInt(SLOT_TAG);
            int amount = entry.contains(AMOUNT_TAG, Tag.TAG_INT)
                    ? entry.getInt(AMOUNT_TAG)
                    : UNLIMITED_AMOUNT;
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
                entries.set(slot, new ConfiguredEntry(restored, amount));
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

    public static int amount(ItemStack filter, int slot) {
        VillagerItemFilterData data = read(filter);
        return slot >= 0 && slot < ENTRY_COUNT ? data.entries.get(slot).amount() : UNLIMITED_AMOUNT;
    }

    public static int minimumAmount(ItemStack filter, int slot) {
        VillagerItemFilterData data = read(filter);
        if (!data.isAmountSlot(slot)) {
            return UNLIMITED_AMOUNT;
        }
        return data.identityEntryCount(slot) > 1 ? 1 : UNLIMITED_AMOUNT;
    }

    public static int identityEntryCount(ItemStack filter, int slot) {
        return read(filter).identityEntryCount(slot);
    }

    public static int combinedAmountForSlot(ItemStack filter, int slot) {
        VillagerItemFilterData data = read(filter);
        if (!data.isAmountSlot(slot)) {
            return UNLIMITED_AMOUNT;
        }
        ConfiguredEntry selected = data.entries.get(slot);
        int combined = 0;
        for (ConfiguredEntry entry : data.entries) {
            if (entry.stack().isEmpty() || !sameEntry(selected.stack(), entry.stack())) {
                continue;
            }
            if (entry.amount() == UNLIMITED_AMOUNT) {
                return UNLIMITED_AMOUNT;
            }
            combined += entry.amount();
        }
        return combined;
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
     * Sets one ghost entry. Empty stacks clear it. Concrete identities may repeat only when every
     * entry in that identity group has a positive amount; a new duplicate starts at one.
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
        if (sameEntry(current.entries.get(slot).stack(), normalizedEntry)) {
            return false;
        }

        int amount = UNLIMITED_AMOUNT;
        if (!normalizedEntry.isEmpty()) {
            for (int otherSlot = 0; otherSlot < ENTRY_COUNT; otherSlot++) {
                if (otherSlot == slot) {
                    continue;
                }
                ConfiguredEntry existing = current.entries.get(otherSlot);
                if (!sameEntry(existing.stack(), normalizedEntry)) {
                    continue;
                }
                if (!isAmountEntry(normalizedEntry) || existing.amount() == UNLIMITED_AMOUNT) {
                    return false;
                }
                amount = 1;
            }
        }

        List<ConfiguredEntry> updated = copyEntries(current.entries);
        updated.set(slot, new ConfiguredEntry(normalizedEntry, amount));
        write(filter, new VillagerItemFilterData(current.mode, updated));
        return true;
    }

    public static boolean setAmount(ItemStack filter, int slot, int requestedAmount) {
        if (!VillagerRetaliationItems.isItemFilter(filter) || slot < 0 || slot >= ENTRY_COUNT) {
            return false;
        }
        VillagerItemFilterData current = read(filter);
        if (!current.isAmountSlot(slot)) {
            return false;
        }
        int minimum = current.identityEntryCount(slot) > 1 ? 1 : UNLIMITED_AMOUNT;
        int amount = Math.clamp(requestedAmount, minimum, MAX_ENTRY_AMOUNT);
        ConfiguredEntry selected = current.entries.get(slot);
        if (selected.amount() == amount) {
            return false;
        }
        List<ConfiguredEntry> updated = copyEntries(current.entries);
        updated.set(slot, new ConfiguredEntry(selected.stack().copy(), amount));
        write(filter, new VillagerItemFilterData(current.mode, updated));
        return true;
    }

    public static AmountAdjustment adjustAmount(ItemStack filter, int slot, int delta) {
        if (delta == 0 || mode(filter) != Mode.ALLOWLIST) {
            return AmountAdjustment.invalid();
        }
        VillagerItemFilterData current = read(filter);
        if (!current.isAmountSlot(slot)) {
            return AmountAdjustment.invalid();
        }
        int previous = current.entries.get(slot).amount();
        int minimum = current.identityEntryCount(slot) > 1 ? 1 : UNLIMITED_AMOUNT;
        long requested = (long) previous + delta;
        int amount = (int) Math.clamp(requested, minimum, MAX_ENTRY_AMOUNT);
        boolean hitLimit = requested < minimum || requested > MAX_ENTRY_AMOUNT;
        boolean changed = amount != previous && setAmount(filter, slot, amount);
        return new AmountAdjustment(true, previous, changed ? amount : previous, changed, hitLimit);
    }

    public static boolean isAmountEntry(ItemStack entry) {
        return entry != null && !entry.isEmpty() && !VillagerRetaliationItems.isFilter(entry);
    }

    public static void clear(ItemStack filter) {
        write(filter, empty());
    }

    public static boolean isDefault(ItemStack filter) {
        VillagerItemFilterData data = read(filter);
        return data.mode == Mode.ALLOWLIST && data.entries.stream().allMatch(entry -> entry.stack().isEmpty());
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
        for (ConfiguredEntry configuredEntry : data.entries) {
            ItemStack entry = configuredEntry.stack();
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

    /**
     * Returns the combined positive stock limit for the candidate, or zero when its amount is
     * unlimited or quantities do not apply.
     */
    public static int amountLimit(ItemStack filter, ItemStack candidate) {
        VillagerItemFilterData data = read(filter);
        if (data.mode != Mode.ALLOWLIST || candidate == null || candidate.isEmpty()) {
            return UNLIMITED_AMOUNT;
        }
        boolean matched = false;
        int combined = 0;
        for (ConfiguredEntry entry : data.entries) {
            if (!isAmountEntry(entry.stack()) || !identityMatches(entry.stack(), candidate)) {
                continue;
            }
            matched = true;
            if (entry.amount() == UNLIMITED_AMOUNT) {
                return UNLIMITED_AMOUNT;
            }
            combined += entry.amount();
        }
        return matched ? combined : UNLIMITED_AMOUNT;
    }

    public static boolean countsTowardAmountLimit(
            Level level,
            ItemStack filter,
            ItemStack candidate,
            ItemStack stored) {
        if (amountLimit(filter, candidate) == UNLIMITED_AMOUNT
                || stored == null
                || stored.isEmpty()
                || !matches(level, filter, stored)) {
            return false;
        }
        VillagerItemFilterData data = read(filter);
        for (ConfiguredEntry entry : data.entries) {
            if (isAmountEntry(entry.stack())
                    && identityMatches(entry.stack(), candidate)
                    && identityMatches(entry.stack(), stored)) {
                return true;
            }
        }
        return false;
    }

    public static List<Component> tooltip(ItemStack filter) {
        VillagerItemFilterData data = read(filter);
        List<Component> tooltip = new ArrayList<>();
        tooltip.add(Component.translatable("item.villagerretaliation.item_filter.mode")
                .withStyle(ChatFormatting.GRAY)
                .append(data.mode.label().copy().withStyle(ChatFormatting.GOLD)));
        int shown = 0;
        for (ConfiguredEntry configuredEntry : data.entries) {
            ItemStack entry = configuredEntry.stack();
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
            Component line = Component.literal("- ")
                    .withStyle(ChatFormatting.DARK_GRAY)
                    .append(description.copy().withStyle(ChatFormatting.GRAY));
            if (configuredEntry.amount() > UNLIMITED_AMOUNT) {
                line = line.copy().append(Component.literal(" ×" + formatAmount(configuredEntry.amount()))
                        .withStyle(ChatFormatting.GRAY));
            }
            tooltip.add(line);
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

    public static String formatAmount(int amount) {
        return amount >= MAX_ENTRY_AMOUNT ? "1K" : Integer.toString(Math.max(UNLIMITED_AMOUNT, amount));
    }

    private ItemStack entry(int slot) {
        if (slot < 0 || slot >= ENTRY_COUNT) {
            return ItemStack.EMPTY;
        }
        return this.entries.get(slot).stack().copy();
    }

    private boolean isAmountSlot(int slot) {
        return slot >= 0 && slot < ENTRY_COUNT && isAmountEntry(this.entries.get(slot).stack());
    }

    private int identityEntryCount(int slot) {
        if (!isAmountSlot(slot)) {
            return 0;
        }
        ItemStack selected = this.entries.get(slot).stack();
        int count = 0;
        for (ConfiguredEntry entry : this.entries) {
            if (!entry.stack().isEmpty() && sameEntry(selected, entry.stack())) {
                count++;
            }
        }
        return count;
    }

    private static void write(ItemStack filter, VillagerItemFilterData data) {
        if (!VillagerRetaliationItems.isItemFilter(filter)) {
            return;
        }
        CustomData existing = filter.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag customData = existing.isEmpty() ? new CompoundTag() : existing.copyTag();
        if (data.mode == Mode.ALLOWLIST && data.entries.stream().allMatch(entry -> entry.stack().isEmpty())) {
            customData.remove(ROOT_TAG);
        } else {
            CompoundTag root = new CompoundTag();
            root.putString(MODE_TAG, data.mode.id());
            ListTag storedEntries = new ListTag();
            for (int slot = 0; slot < ENTRY_COUNT; slot++) {
                ConfiguredEntry configured = data.entries.get(slot);
                ItemStack configuredEntry = configured.stack();
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
                if (configured.amount() > UNLIMITED_AMOUNT) {
                    entry.putInt(AMOUNT_TAG, configured.amount());
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

    private static List<ConfiguredEntry> emptyEntries() {
        List<ConfiguredEntry> entries = new ArrayList<>(ENTRY_COUNT);
        for (int slot = 0; slot < ENTRY_COUNT; slot++) {
            entries.add(ConfiguredEntry.empty());
        }
        return entries;
    }

    private static List<ConfiguredEntry> copyEntries(List<ConfiguredEntry> entries) {
        List<ConfiguredEntry> copy = new ArrayList<>(ENTRY_COUNT);
        for (ConfiguredEntry entry : entries) {
            copy.add(new ConfiguredEntry(entry.stack().copy(), entry.amount()));
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
        for (ConfiguredEntry entry : read(filter).entries) {
            if (VillagerRetaliationItems.isItemFilter(entry.stack())) {
                deepest = Math.max(deepest, nestingDepth(entry.stack(), depth + 1));
            }
        }
        return deepest;
    }

    private record ConfiguredEntry(ItemStack stack, int amount) {
        private static ConfiguredEntry empty() {
            return new ConfiguredEntry(ItemStack.EMPTY, UNLIMITED_AMOUNT);
        }
    }

    public record AmountAdjustment(
            boolean valid,
            int previousAmount,
            int amount,
            boolean changed,
            boolean hitLimit) {
        private static AmountAdjustment invalid() {
            return new AmountAdjustment(false, UNLIMITED_AMOUNT, UNLIMITED_AMOUNT, false, false);
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
