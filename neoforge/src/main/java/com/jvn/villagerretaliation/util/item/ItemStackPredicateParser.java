package com.jvn.villagerretaliation.util.item;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.commands.arguments.item.ItemParser;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class ItemStackPredicateParser {
    private static final Set<String> RANGE_KEYS = Set.of("min", "max");
    public static final HolderLookup.Provider DEFAULT_REGISTRIES =
            RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);

    private ItemStackPredicateParser() {
    }

    public static ParsedItem parseItemShorthand(HolderLookup.Provider registries, String selector) {
        StringReader reader = new StringReader(selector);
        ItemParser.ItemResult parsed;
        try {
            parsed = new ItemParser(registries).parse(reader);
            reader.skipWhitespace();
            if (reader.canRead()) {
                throw new IllegalArgumentException(
                        "item shorthand has unexpected trailing content at character " + reader.getCursor() + ".");
            }
        } catch (CommandSyntaxException exception) {
            throw new IllegalArgumentException("item shorthand is invalid: " + exception.getMessage());
        }

        ArrayList<ComponentPredicate> predicates = new ArrayList<>();
        for (Map.Entry<DataComponentType<?>, Optional<?>> entry : parsed.components().entrySet()) {
            if (entry.getValue().isEmpty()) {
                throw new IllegalArgumentException("item shorthand cannot require a removed component.");
            }
            predicates.add(exactUnchecked(entry.getKey(), entry.getValue().get()));
        }
        predicates.sort(Comparator.comparing(predicate ->
                BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(predicate.type()).toString()));
        return new ParsedItem(parsed.item().value(), List.copyOf(predicates));
    }

    public static ItemStackPredicate parse(
            HolderLookup.Provider registries,
            JsonObject root,
            List<ComponentPredicate> shorthand,
            List<Item> selectedItems) {
        return parse(registries, root, shorthand, selectedItems, "components", "durability");
    }

    public static ItemStackPredicate parse(
            HolderLookup.Provider registries,
            JsonObject root,
            List<Item> selectedItems,
            String componentsKey,
            String durabilityKey,
            String... customDataKeys) {
        JsonObject normalized = new JsonObject();
        JsonElement components = root == null ? null : root.get(componentsKey);
        if (components != null) {
            normalized.add("components", components.deepCopy());
        }
        JsonElement durability = root == null || durabilityKey == null ? null : root.get(durabilityKey);
        if (durability != null) {
            normalized.add("durability", durability.deepCopy());
        }
        for (String key : customDataKeys == null ? new String[0] : customDataKeys) {
            JsonElement customData = root == null ? null : root.get(key);
            if (customData == null || customData.isJsonNull()) {
                continue;
            }
            JsonObject componentObject;
            if (!normalized.has("components")) {
                componentObject = new JsonObject();
                normalized.add("components", componentObject);
            } else if (normalized.get("components").isJsonObject()) {
                componentObject = normalized.getAsJsonObject("components");
            } else {
                throw new IllegalArgumentException(componentsKey + " must be an object keyed by registered component id.");
            }
            if (componentObject.has("minecraft:custom_data")) {
                throw new IllegalArgumentException(
                        "minecraft:custom_data cannot be specified in both " + componentsKey + " and " + key + ".");
            }
            componentObject.add("minecraft:custom_data", customData.deepCopy());
        }
        return parse(registries, normalized, List.of(), selectedItems, "components", "durability");
    }

    private static ItemStackPredicate parse(
            HolderLookup.Provider registries,
            JsonObject root,
            List<ComponentPredicate> shorthand,
            List<Item> selectedItems,
            String componentsKey,
            String durabilityKey) {
        HolderLookup.Provider safeRegistries = registries == null ? DEFAULT_REGISTRIES : registries;
        ArrayList<ComponentPredicate> components =
                new ArrayList<>(shorthand == null ? List.of() : shorthand);
        IdentityHashMap<DataComponentType<?>, ComponentPredicate> seen = new IdentityHashMap<>();
        for (ComponentPredicate predicate : components) {
            seen.put(predicate.type(), predicate);
        }

        JsonElement componentElement = root == null ? null : root.get(componentsKey);
        if (componentElement != null && !componentElement.isJsonNull()) {
            if (!componentElement.isJsonObject()) {
                throw new IllegalArgumentException(componentsKey + " must be an object keyed by registered component id.");
            }
            List<Map.Entry<String, JsonElement>> entries =
                    new ArrayList<>(componentElement.getAsJsonObject().entrySet());
            entries.sort(Map.Entry.comparingByKey());
            for (Map.Entry<String, JsonElement> entry : entries) {
                ResourceLocation id = ResourceLocation.tryParse(entry.getKey());
                if (id == null) {
                    throw new IllegalArgumentException(
                            componentsKey + " contains invalid component id \"" + entry.getKey() + "\".");
                }
                DataComponentType<?> type =
                        BuiltInRegistries.DATA_COMPONENT_TYPE.getOptional(id).orElse(null);
                if (type == null || type.isTransient()) {
                    throw new IllegalArgumentException(
                            componentsKey + " references unknown or non-persistent component \"" + id + "\".");
                }
                if (seen.containsKey(type)) {
                    throw new IllegalArgumentException(
                            "component \"" + id + "\" is specified more than once.");
                }
                ComponentPredicate predicate = parseComponent(safeRegistries, id, type, entry.getValue());
                seen.put(type, predicate);
                components.add(predicate);
            }
        }

        ItemStackPredicate.DurabilityRange durability = parseDurability(
                root == null || durabilityKey == null ? null : root.get(durabilityKey),
                durabilityKey == null ? "durability" : durabilityKey);
        List<Item> safeItems = selectedItems == null ? List.of() : selectedItems;
        if (durability != null && !safeItems.isEmpty() && !canMatchDurability(safeItems, durability)) {
            throw new IllegalArgumentException(
                    (durabilityKey == null ? "durability" : durabilityKey)
                            + " cannot match any damageable item selected by this definition.");
        }
        return components.isEmpty() && durability == null
                ? ItemStackPredicate.ANY
                : new ItemStackPredicate(components, durability);
    }

    private static ComponentPredicate parseComponent(
            HolderLookup.Provider registries,
            ResourceLocation id,
            DataComponentType<?> type,
            JsonElement value) {
        if (value == null || value.isJsonNull()) {
            throw new IllegalArgumentException("component \"" + id + "\" requires a value.");
        }
        if (type != DataComponents.CUSTOM_DATA && isRange(value)) {
            JsonObject range = value.getAsJsonObject();
            Double min = range.has("min")
                    ? decodeNumericBound(registries, id, type, range.get("min"), "min")
                    : null;
            Double max = range.has("max")
                    ? decodeNumericBound(registries, id, type, range.get("max"), "max")
                    : null;
            try {
                return new ComponentPredicate.NumericRange(type, min, max);
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException(
                        "component \"" + id + "\" range is invalid: " + exception.getMessage());
            }
        }
        Object decoded = decode(registries, id, type, value);
        return exactUnchecked(type, decoded);
    }

    private static boolean isRange(JsonElement value) {
        if (!value.isJsonObject()) {
            return false;
        }
        JsonObject object = value.getAsJsonObject();
        return !object.isEmpty()
                && object.keySet().stream().allMatch(RANGE_KEYS::contains)
                && (object.has("min") || object.has("max"));
    }

    private static Double decodeNumericBound(
            HolderLookup.Provider registries,
            ResourceLocation id,
            DataComponentType<?> type,
            JsonElement value,
            String bound) {
        Object decoded = decode(registries, id, type, value);
        if (!(decoded instanceof Number number)) {
            throw new IllegalArgumentException(
                    "component \"" + id + "\" uses a " + bound
                            + " range, but its codec value is not numeric.");
        }
        return number.doubleValue();
    }

    private static Object decode(
            HolderLookup.Provider registries,
            ResourceLocation id,
            DataComponentType<?> type,
            JsonElement value) {
        try {
            return decodeUnchecked(registries.createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE), type, value)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "component \"" + id + "\" has an invalid codec value."));
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(
                    "component \"" + id + "\" codec could not decode the value: " + exception.getMessage());
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Optional<Object> decodeUnchecked(
            DynamicOps<JsonElement> ops,
            DataComponentType type,
            JsonElement value) {
        StringBuilder error = new StringBuilder();
        DataResult<?> result = type.codecOrThrow().parse(ops, value);
        Optional<?> decoded = result.resultOrPartial(message -> {
            if (!error.isEmpty()) {
                error.append("; ");
            }
            error.append(message);
        });
        if (decoded.isEmpty() && !error.isEmpty()) {
            throw new IllegalArgumentException(error.toString());
        }
        return (Optional<Object>) decoded;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static ComponentPredicate exactUnchecked(DataComponentType type, Object value) {
        return new ComponentPredicate.Exact(type, value);
    }

    private static ItemStackPredicate.DurabilityRange parseDurability(JsonElement element, String fieldName) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (!element.isJsonObject()) {
            throw new IllegalArgumentException(fieldName + " must be an object with min and/or max.");
        }
        JsonObject object = element.getAsJsonObject();
        if (object.keySet().stream().anyMatch(key -> !RANGE_KEYS.contains(key))) {
            throw new IllegalArgumentException(fieldName + " only supports min and max.");
        }
        Integer min = object.has("min") ? readNonNegativeInteger(object.get("min"), fieldName + ".min") : null;
        Integer max = object.has("max") ? readNonNegativeInteger(object.get("max"), fieldName + ".max") : null;
        return new ItemStackPredicate.DurabilityRange(min, max);
    }

    private static int readNonNegativeInteger(JsonElement element, String fieldName) {
        if (element == null
                || !element.isJsonPrimitive()
                || !element.getAsJsonPrimitive().isNumber()
                || !element.getAsString().matches("[0-9]+")) {
            throw new IllegalArgumentException(fieldName + " must be a non-negative integer.");
        }
        try {
            return Integer.parseInt(element.getAsString());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(fieldName + " is outside the supported integer range.");
        }
    }

    private static boolean canMatchDurability(
            List<Item> selectedItems,
            ItemStackPredicate.DurabilityRange durability) {
        int requiredMinimum = durability.min() == null ? 0 : durability.min();
        for (Item item : selectedItems) {
            ItemStack stack = new ItemStack(item);
            if (stack.isDamageableItem() && stack.getMaxDamage() >= requiredMinimum) {
                return true;
            }
        }
        return false;
    }

    public record ParsedItem(Item item, List<ComponentPredicate> components) {
    }
}
