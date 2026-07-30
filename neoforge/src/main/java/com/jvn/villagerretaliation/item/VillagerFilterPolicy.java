package com.jvn.villagerretaliation.item;

import com.jvn.villagerretaliation.VillagerRetaliation;
import java.util.List;
import java.util.OptionalInt;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** Versioned transfer policy shared by every configured physical filter. */
public final class VillagerFilterPolicy {
    public static final int CURRENT_SCHEMA_VERSION = 1;
    public static final int MAX_STOCK_TARGET = 1000;
    public static final int UNLIMITED_ALLOWANCE = Integer.MAX_VALUE;

    private static final String ROOT_TAG = VillagerRetaliation.MOD_ID + ":filter_policy";
    private static final String VERSION_TAG = "Version";
    private static final String DIRECTION_TAG = "Direction";
    private static final String LIST_MODE_TAG = "ListMode";
    private static final String COMBINATION_TAG = "Combination";
    private static final String STOCK_TARGET_TAG = "StockTarget";

    private VillagerFilterPolicy() {
    }

    public static Policy read(ItemStack filter) {
        if (!VillagerRetaliationItems.isFilter(filter)) {
            return Policy.invalid();
        }
        CustomData data = filter.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        if (data.isEmpty()) {
            return legacyPolicy(filter);
        }
        CompoundTag customData = data.copyTag();
        if (!customData.contains(ROOT_TAG)) {
            return legacyPolicy(filter);
        }
        if (!customData.contains(ROOT_TAG, Tag.TAG_COMPOUND)) {
            return Policy.invalid();
        }
        CompoundTag root = customData.getCompound(ROOT_TAG);
        if (!root.contains(VERSION_TAG, Tag.TAG_INT)
                || root.getInt(VERSION_TAG) != CURRENT_SCHEMA_VERSION
                || !root.contains(DIRECTION_TAG, Tag.TAG_STRING)
                || !root.contains(LIST_MODE_TAG, Tag.TAG_STRING)
                || !root.contains(COMBINATION_TAG, Tag.TAG_STRING)) {
            return Policy.invalid();
        }

        TransferDirection direction = TransferDirection.byId(root.getString(DIRECTION_TAG));
        ListMode listMode = ListMode.byId(root.getString(LIST_MODE_TAG));
        CombinationMode combination = CombinationMode.byId(root.getString(COMBINATION_TAG));
        if (direction == null || listMode == null || combination == null || !combination.authorable()) {
            return Policy.invalid();
        }

        OptionalInt stockTarget = OptionalInt.empty();
        if (root.contains(STOCK_TARGET_TAG)) {
            if (!root.contains(STOCK_TARGET_TAG, Tag.TAG_INT)) {
                return Policy.invalid();
            }
            int target = root.getInt(STOCK_TARGET_TAG);
            if (target < 1 || target > MAX_STOCK_TARGET) {
                return Policy.invalid();
            }
            stockTarget = OptionalInt.of(target);
        }
        return Policy.explicit(direction, listMode, combination, stockTarget);
    }

    public static boolean hasStoredPolicy(ItemStack filter) {
        if (!VillagerRetaliationItems.isFilter(filter)) {
            return false;
        }
        CustomData data = filter.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        return !data.isEmpty() && data.copyTag().contains(ROOT_TAG);
    }

    public static boolean setDirection(ItemStack filter, TransferDirection direction) {
        if (direction == null) {
            return false;
        }
        Policy current = editablePolicy(filter);
        return writeIfChanged(filter, new Policy(
                PolicyState.EXPLICIT,
                CURRENT_SCHEMA_VERSION,
                direction,
                current.listMode(),
                current.combinationMode(),
                current.stockTarget()));
    }

    public static boolean setListMode(ItemStack filter, ListMode listMode) {
        if (listMode == null) {
            return false;
        }
        Policy current = editablePolicy(filter);
        return writeIfChanged(filter, new Policy(
                PolicyState.EXPLICIT,
                CURRENT_SCHEMA_VERSION,
                current.direction(),
                listMode,
                current.combinationMode(),
                current.stockTarget()));
    }

    public static boolean setCombinationMode(ItemStack filter, CombinationMode combinationMode) {
        if (combinationMode == null || !combinationMode.authorable()) {
            return false;
        }
        Policy current = editablePolicy(filter);
        return writeIfChanged(filter, new Policy(
                PolicyState.EXPLICIT,
                CURRENT_SCHEMA_VERSION,
                current.direction(),
                current.listMode(),
                combinationMode,
                current.stockTarget()));
    }

    /** Applies one fully validated editor action to a held configured filter. */
    public static boolean applyChange(ItemStack filter, PolicyField field, int value) {
        if (!VillagerRetaliationItems.isFilter(filter) || field == null) {
            return false;
        }
        return switch (field) {
            case DIRECTION -> {
                TransferDirection direction = TransferDirection.fromNetworkId(value);
                yield direction != null && setDirection(filter, direction);
            }
            case LIST_MODE -> {
                ListMode listMode = ListMode.fromNetworkId(value);
                if (listMode == null) {
                    yield false;
                }
                boolean matcherChanged = false;
                if (VillagerRetaliationItems.isItemFilter(filter)) {
                    VillagerItemFilterData.Mode itemMode = listMode == ListMode.DENY_MATCHING
                            ? VillagerItemFilterData.Mode.DENYLIST
                            : VillagerItemFilterData.Mode.ALLOWLIST;
                    matcherChanged = VillagerItemFilterData.mode(filter) != itemMode;
                    if (matcherChanged) {
                        VillagerItemFilterData.setMode(filter, itemMode);
                    }
                } else if (VillagerRetaliationItems.isAttributeFilter(filter)) {
                    boolean inverted = listMode == ListMode.DENY_MATCHING;
                    matcherChanged = VillagerAttributeFilterData.read(filter).attribute() != null
                            && VillagerAttributeFilterData.read(filter).inverted() != inverted;
                    if (matcherChanged) {
                        VillagerAttributeFilterData.setInverted(filter, inverted);
                    }
                }
                yield setListMode(filter, listMode) || matcherChanged;
            }
            case COMBINATION -> {
                CombinationMode combination = CombinationMode.fromNetworkId(value);
                if (combination == null) {
                    yield false;
                }
                boolean matcherChanged = false;
                if (VillagerRetaliationItems.isItemFilter(filter)) {
                    VillagerItemFilterData.EntryCombination itemCombination =
                            combination == CombinationMode.MATCH_ALL
                                    ? VillagerItemFilterData.EntryCombination.ALL
                                    : VillagerItemFilterData.EntryCombination.ANY;
                    matcherChanged = VillagerItemFilterData.setEntryCombination(filter, itemCombination);
                }
                yield setCombinationMode(filter, combination) || matcherChanged;
            }
            case STOCK_TARGET -> {
                Policy policy = read(filter);
                yield value >= 0
                        && value <= MAX_STOCK_TARGET
                        && policy.valid()
                        && policy.listMode() == ListMode.ALLOW_MATCHING
                        && setStockTarget(filter, value);
            }
            case STOCK_DELTA -> {
                Policy policy = read(filter);
                if (!validStockDelta(value)
                        || !policy.valid()
                        || policy.listMode() != ListMode.ALLOW_MATCHING) {
                    yield false;
                }
                int previous = policy.stockTarget().orElse(0);
                int requested = (int) Math.clamp((long) previous + value, 0L, MAX_STOCK_TARGET);
                yield requested != previous && setStockTarget(filter, requested);
            }
        };
    }

    public static boolean validStockDelta(int delta) {
        return delta == -100 || delta == -10 || delta == -5 || delta == -1
                || delta == 1 || delta == 5 || delta == 10 || delta == 100;
    }

    public static boolean setStockTarget(ItemStack filter, int requestedTarget) {
        Policy current = editablePolicy(filter);
        OptionalInt target = requestedTarget <= 0
                ? OptionalInt.empty()
                : OptionalInt.of(Math.clamp(requestedTarget, 1, MAX_STOCK_TARGET));
        return writeIfChanged(filter, new Policy(
                PolicyState.EXPLICIT,
                CURRENT_SCHEMA_VERSION,
                current.direction(),
                current.listMode(),
                current.combinationMode(),
                target));
    }

    public static boolean setPolicy(
            ItemStack filter,
            TransferDirection direction,
            ListMode listMode,
            CombinationMode combinationMode,
            OptionalInt stockTarget) {
        if (!VillagerRetaliationItems.isFilter(filter)
                || direction == null
                || listMode == null
                || combinationMode == null
                || !combinationMode.authorable()) {
            return false;
        }
        OptionalInt normalized = normalizeTarget(stockTarget);
        return writeIfChanged(filter, Policy.explicit(
                direction,
                listMode,
                combinationMode,
                normalized));
    }

    public static void clear(ItemStack filter) {
        if (!VillagerRetaliationItems.isFilter(filter)) {
            return;
        }
        CustomData existing = filter.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        if (existing.isEmpty()) {
            return;
        }
        CompoundTag customData = existing.copyTag();
        customData.remove(ROOT_TAG);
        store(filter, customData);
    }

    /** Copies the exact stored schema, including malformed future data, without unrelated data. */
    public static void copyConfiguration(ItemStack source, ItemStack target) {
        if (!VillagerRetaliationItems.isFilter(source)
                || !VillagerRetaliationItems.isFilter(target)) {
            return;
        }
        CustomData sourceData = source.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag sourceTag = sourceData.isEmpty() ? new CompoundTag() : sourceData.copyTag();
        CustomData targetData = target.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag targetTag = targetData.isEmpty() ? new CompoundTag() : targetData.copyTag();
        Tag storedPolicy = sourceTag.get(ROOT_TAG);
        if (storedPolicy == null) {
            targetTag.remove(ROOT_TAG);
        } else {
            targetTag.put(ROOT_TAG, storedPolicy.copy());
        }
        store(target, targetTag);
    }

    public static int receiveAllowance(Policy policy, int currentStock, int inboundReservations) {
        return allowance(policy, TransferOperation.RECEIVE, currentStock, inboundReservations);
    }

    public static int provideAllowance(Policy policy, int currentStock, int outboundClaims) {
        return allowance(policy, TransferOperation.PROVIDE, currentStock, outboundClaims);
    }

    public static List<Component> tooltip(ItemStack filter) {
        Policy policy = read(filter);
        if (!policy.valid()) {
            return List.of(Component.translatable("item.villagerretaliation.filter_policy.invalid")
                    .withStyle(ChatFormatting.RED));
        }
        Component direction = Component.translatable(
                "item.villagerretaliation.filter_policy.direction." + policy.direction().id());
        Component mode = Component.translatable(
                "item.villagerretaliation.filter_policy.mode." + policy.listMode().id());
        Component combination = Component.translatable(
                "item.villagerretaliation.filter_policy.combination." + policy.combinationMode().id());
        java.util.ArrayList<Component> lines = new java.util.ArrayList<>();
        lines.add(Component.translatable("item.villagerretaliation.filter_policy.direction")
                .withStyle(ChatFormatting.GRAY)
                .append(direction.copy().withStyle(ChatFormatting.GOLD)));
        lines.add(Component.translatable("item.villagerretaliation.filter_policy.mode")
                .withStyle(ChatFormatting.GRAY)
                .append(mode.copy().withStyle(ChatFormatting.GOLD)));
        lines.add(Component.translatable("item.villagerretaliation.filter_policy.combination")
                .withStyle(ChatFormatting.GRAY)
                .append(combination.copy().withStyle(ChatFormatting.AQUA)));
        if (policy.listMode() == ListMode.ALLOW_MATCHING) {
            Component amount = policy.stockTarget().isPresent()
                    ? Component.literal(Integer.toString(policy.stockTarget().getAsInt()))
                    : Component.translatable("item.villagerretaliation.filter_policy.unlimited");
            lines.add(Component.translatable(
                            "item.villagerretaliation.filter_policy.stock." + policy.direction().id(),
                            amount)
                    .withStyle(ChatFormatting.GRAY));
        }
        lines.add(Component.translatable("item.villagerretaliation.filter_policy.nested_matcher")
                .withStyle(ChatFormatting.DARK_GRAY));
        return List.copyOf(lines);
    }

    public static int allowance(
            Policy policy,
            TransferOperation operation,
            int currentStock,
            int reservationsOrClaims) {
        if (policy == null
                || !policy.valid()
                || operation == null
                || !policy.direction().permits(operation)) {
            return 0;
        }
        if (policy.listMode() == ListMode.DENY_MATCHING || policy.stockTarget().isEmpty()) {
            return UNLIMITED_ALLOWANCE;
        }
        long target = policy.stockTarget().getAsInt();
        long stored = Math.max(0, currentStock);
        long reserved = Math.max(0, reservationsOrClaims);
        long allowed = operation == TransferOperation.RECEIVE
                ? target - stored - reserved
                : stored - target - reserved;
        return (int) Math.min(UNLIMITED_ALLOWANCE, Math.max(0L, allowed));
    }

    private static Policy editablePolicy(ItemStack filter) {
        Policy current = read(filter);
        if (current.valid()) {
            return new Policy(
                    PolicyState.EXPLICIT,
                    CURRENT_SCHEMA_VERSION,
                    current.direction(),
                    current.listMode(),
                    current.combinationMode(),
                    current.stockTarget());
        }
        return Policy.explicit(
                TransferDirection.BOTH,
                ListMode.ALLOW_MATCHING,
                CombinationMode.MATCH_ANY,
                OptionalInt.empty());
    }

    private static Policy legacyPolicy(ItemStack filter) {
        ListMode listMode = ListMode.ALLOW_MATCHING;
        CombinationMode combinationMode = CombinationMode.MATCH_ANY;
        if (VillagerRetaliationItems.isItemFilter(filter)) {
            listMode = VillagerItemFilterData.mode(filter) == VillagerItemFilterData.Mode.DENYLIST
                    ? ListMode.DENY_MATCHING
                    : ListMode.ALLOW_MATCHING;
            combinationMode = switch (VillagerItemFilterData.entryCombination(filter)) {
                case ALL -> CombinationMode.MATCH_ALL;
                case LEGACY -> CombinationMode.LEGACY;
                case ANY -> CombinationMode.MATCH_ANY;
            };
        } else if (VillagerRetaliationItems.isAttributeFilter(filter)) {
            listMode = VillagerAttributeFilterData.read(filter).inverted()
                    ? ListMode.DENY_MATCHING
                    : ListMode.ALLOW_MATCHING;
        }
        return Policy.legacy(listMode, combinationMode);
    }

    private static OptionalInt normalizeTarget(OptionalInt stockTarget) {
        if (stockTarget == null || stockTarget.isEmpty()) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(Math.clamp(stockTarget.getAsInt(), 1, MAX_STOCK_TARGET));
    }

    private static boolean writeIfChanged(ItemStack filter, Policy policy) {
        if (!VillagerRetaliationItems.isFilter(filter) || policy == null || !policy.explicit()) {
            return false;
        }
        if (policy.equals(read(filter))) {
            return false;
        }
        write(filter, policy);
        return true;
    }

    private static void write(ItemStack filter, Policy policy) {
        CustomData existing = filter.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag customData = existing.isEmpty() ? new CompoundTag() : existing.copyTag();
        CompoundTag root = new CompoundTag();
        root.putInt(VERSION_TAG, CURRENT_SCHEMA_VERSION);
        root.putString(DIRECTION_TAG, policy.direction().id());
        root.putString(LIST_MODE_TAG, policy.listMode().id());
        root.putString(COMBINATION_TAG, policy.combinationMode().id());
        policy.stockTarget().ifPresent(target -> root.putInt(STOCK_TARGET_TAG, target));
        customData.put(ROOT_TAG, root);
        store(filter, customData);
    }

    private static void store(ItemStack filter, CompoundTag customData) {
        if (customData.isEmpty()) {
            filter.remove(DataComponents.CUSTOM_DATA);
        } else {
            filter.set(DataComponents.CUSTOM_DATA, CustomData.of(customData));
        }
    }

    public enum PolicyState {
        LEGACY,
        EXPLICIT,
        INVALID
    }

    public enum PolicyField {
        DIRECTION(0),
        LIST_MODE(1),
        COMBINATION(2),
        STOCK_TARGET(3),
        STOCK_DELTA(4);

        private final int networkId;

        PolicyField(int networkId) {
            this.networkId = networkId;
        }

        public int networkId() {
            return this.networkId;
        }

        public static PolicyField fromNetworkId(int id) {
            return id >= 0 && id < values().length ? values()[id] : null;
        }
    }

    public enum TransferOperation {
        RECEIVE,
        PROVIDE
    }

    public enum TransferDirection {
        RECEIVE("receive", 0),
        PROVIDE("provide", 1),
        BOTH("both", 2);

        private final String id;
        private final int networkId;

        TransferDirection(String id, int networkId) {
            this.id = id;
            this.networkId = networkId;
        }

        public String id() {
            return this.id;
        }

        public int networkId() {
            return this.networkId;
        }

        public boolean permits(TransferOperation operation) {
            return this == BOTH
                    || this == RECEIVE && operation == TransferOperation.RECEIVE
                    || this == PROVIDE && operation == TransferOperation.PROVIDE;
        }

        public static TransferDirection byId(String id) {
            for (TransferDirection direction : values()) {
                if (direction.id.equalsIgnoreCase(id)) {
                    return direction;
                }
            }
            return null;
        }

        public static TransferDirection fromNetworkId(int id) {
            for (TransferDirection direction : values()) {
                if (direction.networkId == id) {
                    return direction;
                }
            }
            return null;
        }
    }

    public enum ListMode {
        ALLOW_MATCHING("allow_matching", 0),
        DENY_MATCHING("deny_matching", 1);

        private final String id;
        private final int networkId;

        ListMode(String id, int networkId) {
            this.id = id;
            this.networkId = networkId;
        }

        public String id() {
            return this.id;
        }

        public int networkId() {
            return this.networkId;
        }

        public static ListMode byId(String id) {
            for (ListMode mode : values()) {
                if (mode.id.equalsIgnoreCase(id)) {
                    return mode;
                }
            }
            return null;
        }

        public static ListMode fromNetworkId(int id) {
            for (ListMode mode : values()) {
                if (mode.networkId == id) {
                    return mode;
                }
            }
            return null;
        }
    }

    public enum CombinationMode {
        MATCH_ANY("match_any", 0, true),
        MATCH_ALL("match_all", 1, true),
        LEGACY("legacy", -1, false);

        private final String id;
        private final int networkId;
        private final boolean authorable;

        CombinationMode(String id, int networkId, boolean authorable) {
            this.id = id;
            this.networkId = networkId;
            this.authorable = authorable;
        }

        public String id() {
            return this.id;
        }

        public int networkId() {
            return this.networkId;
        }

        public boolean authorable() {
            return this.authorable;
        }

        public static CombinationMode byId(String id) {
            for (CombinationMode mode : values()) {
                if (mode.id.equalsIgnoreCase(id)) {
                    return mode;
                }
            }
            return null;
        }

        public static CombinationMode fromNetworkId(int id) {
            for (CombinationMode mode : values()) {
                if (mode.authorable && mode.networkId == id) {
                    return mode;
                }
            }
            return null;
        }
    }

    public record Policy(
            PolicyState state,
            int schemaVersion,
            TransferDirection direction,
            ListMode listMode,
            CombinationMode combinationMode,
            OptionalInt stockTarget) {
        public Policy {
            state = state == null ? PolicyState.INVALID : state;
            direction = direction == null ? TransferDirection.RECEIVE : direction;
            listMode = listMode == null ? ListMode.ALLOW_MATCHING : listMode;
            combinationMode = combinationMode == null
                    ? CombinationMode.MATCH_ANY
                    : combinationMode;
            stockTarget = stockTarget == null ? OptionalInt.empty() : stockTarget;
        }

        public boolean valid() {
            return this.state != PolicyState.INVALID;
        }

        public boolean explicit() {
            return this.state == PolicyState.EXPLICIT;
        }

        private static Policy legacy(ListMode listMode, CombinationMode combinationMode) {
            return new Policy(
                    PolicyState.LEGACY,
                    0,
                    TransferDirection.RECEIVE,
                    listMode,
                    combinationMode,
                    OptionalInt.empty());
        }

        private static Policy explicit(
                TransferDirection direction,
                ListMode listMode,
                CombinationMode combinationMode,
                OptionalInt stockTarget) {
            return new Policy(
                    PolicyState.EXPLICIT,
                    CURRENT_SCHEMA_VERSION,
                    direction,
                    listMode,
                    combinationMode,
                    normalizeTarget(stockTarget));
        }

        private static Policy invalid() {
            return new Policy(
                    PolicyState.INVALID,
                    -1,
                    TransferDirection.RECEIVE,
                    ListMode.ALLOW_MATCHING,
                    CombinationMode.MATCH_ANY,
                    OptionalInt.empty());
        }
    }
}
