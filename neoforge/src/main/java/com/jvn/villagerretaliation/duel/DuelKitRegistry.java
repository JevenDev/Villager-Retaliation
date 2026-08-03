package com.jvn.villagerretaliation.duel;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;
import com.jvn.villagerretaliation.VillagerRetaliation;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;

/** Datapack-backed duel kits parsed with Minecraft's registry-aware ItemStack codec. */
public final class DuelKitRegistry {
    public static final String DIRECTORY = "duel_kits";
    private static final int MAX_INVENTORY_SLOT = 255;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static volatile State state = state(fallbacks());

    private DuelKitRegistry() {
    }

    public static Optional<DuelKit> find(ResourceLocation id) {
        return id == null ? Optional.empty() : Optional.ofNullable(state.byId().get(id));
    }

    public static DuelKit builtIn(DuelLoadout loadout) {
        if (loadout == null) return null;
        return state.byId().getOrDefault(loadout.id(), fallbacks().get(loadout.id()));
    }

    public static List<DuelKit> values() {
        return state.ordered();
    }

    public static List<DuelKit.Summary> summaries(boolean bringYourOwnAllowed) {
        return state.ordered().stream()
                .filter(kit -> bringYourOwnAllowed || !kit.bringYourOwn())
                .map(kit -> new DuelKit.Summary(kit.id(), kit.name(), kit.description()))
                .toList();
    }

    public static ResourceLocation resolveId(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        normalized = switch (normalized) {
            case "byo" -> "bring_your_own";
            case "bare", "unarmed" -> "bare_handed";
            case "armoured" -> "armored";
            default -> normalized;
        };
        if (!normalized.contains(":")) normalized = VillagerRetaliation.MOD_ID + ":" + normalized;
        ResourceLocation id = ResourceLocation.tryParse(normalized);
        return id != null && state.byId().containsKey(id) ? id : null;
    }

    public static void reload(ResourceManager resources, HolderLookup.Provider registries) {
        Map<ResourceLocation, DuelKit> loaded = new LinkedHashMap<>(fallbacks());
        resources.listResources(DIRECTORY, location -> location.getPath().endsWith(".json"))
                .entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(ResourceLocation::toString)))
                .forEach(entry -> load(entry.getKey(), entry.getValue(), registries)
                        .ifPresent(kit -> loaded.put(kit.id(), kit)));
        state = state(loaded);
        LOGGER.info("Loaded {} duel kits", loaded.size());
    }

    private static Optional<DuelKit> load(
            ResourceLocation file, Resource resource, HolderLookup.Provider registries) {
        ResourceLocation id = idFromFile(file);
        try (Reader reader = resource.openAsReader()) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            String name = requiredString(root, "name", 128);
            String description = requiredString(root, "description", 512);
            int order = root.has("sort_order") ? root.get("sort_order").getAsInt() : 100;
            boolean bringOwn = root.has("bring_your_own") && root.get("bring_your_own").getAsBoolean();
            String style = root.has("combat_style")
                    ? root.get("combat_style").getAsString().toLowerCase(Locale.ROOT) : "melee";
            if (!style.equals("melee") && !style.equals("ranged")) {
                throw new JsonParseException("combat_style must be \"melee\" or \"ranged\"");
            }
            return Optional.of(new DuelKit(id, name, description, order, bringOwn, style.equals("ranged"),
                    participant(root, "player", registries),
                    participant(root, "villager", registries)));
        } catch (IOException | RuntimeException exception) {
            LOGGER.warn("Skipping invalid duel kit {} from pack {}: {}",
                    id, resource.sourcePackId(), exception.getMessage());
            return Optional.empty();
        }
    }

    private static DuelKit.Participant participant(
            JsonObject root, String key, HolderLookup.Provider registries) {
        if (!root.has(key)) return DuelKit.Participant.EMPTY;
        JsonObject participant = root.getAsJsonObject(key);
        List<DuelKit.InventoryItem> inventory = new ArrayList<>();
        Set<Integer> occupiedSlots = new HashSet<>();
        if (participant.has("inventory")) {
            for (JsonElement raw : participant.getAsJsonArray("inventory")) {
                JsonObject entry = raw.getAsJsonObject();
                int slot = entry.get("slot").getAsInt();
                if (slot < 0 || slot > MAX_INVENTORY_SLOT) {
                    throw new JsonParseException(key + " inventory slot must be between 0 and " + MAX_INVENTORY_SLOT);
                }
                if (!occupiedSlots.add(slot)) {
                    throw new JsonParseException(key + " inventory slot " + slot + " is assigned more than once");
                }
                inventory.add(new DuelKit.InventoryItem(slot, stack(entry.get("stack"), registries)));
            }
        }
        Map<EquipmentSlot, ItemStack> equipment = new EnumMap<>(EquipmentSlot.class);
        if (participant.has("equipment")) {
            for (Map.Entry<String, JsonElement> entry : participant.getAsJsonObject("equipment").entrySet()) {
                EquipmentSlot slot;
                try {
                    slot = EquipmentSlot.byName(entry.getKey().toLowerCase(Locale.ROOT));
                } catch (IllegalArgumentException exception) {
                    throw new JsonParseException("Unknown " + key + " equipment slot " + entry.getKey());
                }
                if (slot == EquipmentSlot.BODY) {
                    throw new JsonParseException("The body equipment slot is not supported for duelists");
                }
                equipment.put(slot, stack(entry.getValue(), registries));
            }
        }
        return new DuelKit.Participant(inventory, equipment);
    }

    private static ItemStack stack(JsonElement json, HolderLookup.Provider registries) {
        if (json == null) throw new JsonParseException("Missing item stack");
        return ItemStack.CODEC.parse(RegistryOps.create(JsonOps.INSTANCE, registries), json)
                .getOrThrow(JsonParseException::new);
    }

    private static String requiredString(JsonObject root, String key, int maximumLength) {
        if (!root.has(key) || !root.get(key).isJsonPrimitive()) {
            throw new JsonParseException("Missing string field \"" + key + "\"");
        }
        String value = root.get(key).getAsString().trim();
        if (value.isEmpty()) throw new JsonParseException("Field \"" + key + "\" cannot be blank");
        if (value.length() > maximumLength) {
            throw new JsonParseException("Field \"" + key + "\" cannot exceed " + maximumLength + " characters");
        }
        return value;
    }

    private static ResourceLocation idFromFile(ResourceLocation file) {
        String path = file.getPath();
        String prefix = DIRECTORY + "/";
        if (!path.startsWith(prefix) || !path.endsWith(".json")) {
            throw new IllegalArgumentException("Invalid duel kit resource path " + file);
        }
        return ResourceLocation.fromNamespaceAndPath(
                file.getNamespace(), path.substring(prefix.length(), path.length() - ".json".length()));
    }

    private static State state(Map<ResourceLocation, DuelKit> kits) {
        List<DuelKit> ordered = kits.values().stream()
                .sorted(Comparator.comparingInt(DuelKit::sortOrder)
                        .thenComparing(kit -> kit.id().toString()))
                .toList();
        return new State(Map.copyOf(kits), ordered);
    }

    private static Map<ResourceLocation, DuelKit> fallbacks() {
        Map<ResourceLocation, DuelKit> kits = new LinkedHashMap<>();
        kits.put(DuelLoadout.BARE_HANDED.id(), kit(
                DuelLoadout.BARE_HANDED, "fists only", "Fists only. No weapons.", 0, false, false,
                DuelKit.Participant.EMPTY, DuelKit.Participant.EMPTY));
        kits.put(DuelLoadout.MELEE.id(), kit(
                DuelLoadout.MELEE, "iron swords and shields", "Iron swords and shields.", 10, false, false,
                participant(Map.of(0, new ItemStack(Items.IRON_SWORD)),
                        Map.of(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD))),
                participant(Map.of(), Map.of(
                        EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD),
                        EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD)))));
        kits.put(DuelLoadout.RANGED.id(), kit(
                DuelLoadout.RANGED, "bows and arrows", "Bows and a quiver of arrows.", 20, false, true,
                participant(Map.of(0, new ItemStack(Items.BOW), 1, new ItemStack(Items.ARROW, 64)), Map.of()),
                participant(Map.of(0, new ItemStack(Items.ARROW, 64)),
                        Map.of(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW)))));
        Map<EquipmentSlot, ItemStack> armor = Map.of(
                EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS),
                EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS),
                EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE),
                EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET),
                EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        Map<EquipmentSlot, ItemStack> villagerArmor = new EnumMap<>(armor);
        villagerArmor.put(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        kits.put(DuelLoadout.ARMORED.id(), kit(
                DuelLoadout.ARMORED, "full iron battle gear", "Full iron armor, blades, and shields.", 30, false, false,
                participant(Map.of(0, new ItemStack(Items.IRON_SWORD), 1, new ItemStack(Items.IRON_AXE)), armor),
                participant(Map.of(0, new ItemStack(Items.IRON_AXE)), villagerArmor)));
        kits.put(DuelLoadout.BRING_YOUR_OWN.id(), kit(
                DuelLoadout.BRING_YOUR_OWN, "whatever we carry", "We each fight with what we carry.", 40, true, false,
                DuelKit.Participant.EMPTY, DuelKit.Participant.EMPTY));
        return kits;
    }

    private static DuelKit kit(
            DuelLoadout loadout, String name, String description, int order,
            boolean bringOwn, boolean ranged,
            DuelKit.Participant player, DuelKit.Participant villager) {
        return new DuelKit(loadout.id(), name, description, order, bringOwn, ranged, player, villager);
    }

    private static DuelKit.Participant participant(
            Map<Integer, ItemStack> inventory, Map<EquipmentSlot, ItemStack> equipment) {
        return new DuelKit.Participant(
                inventory.entrySet().stream().sorted(Map.Entry.comparingByKey())
                        .map(entry -> new DuelKit.InventoryItem(entry.getKey(), entry.getValue())).toList(),
                equipment);
    }

    private record State(Map<ResourceLocation, DuelKit> byId, List<DuelKit> ordered) {
    }
}
