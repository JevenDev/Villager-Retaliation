package com.jvn.villagerretaliation.villager;

import static com.jvn.villagerretaliation.util.DatapackJsonReader.readBoolean;
import static com.jvn.villagerretaliation.util.DatapackJsonReader.readDouble;
import static com.jvn.villagerretaliation.util.DatapackJsonReader.readObject;
import static com.jvn.villagerretaliation.util.DatapackJsonReader.readString;
import static com.jvn.villagerretaliation.util.DatapackJsonReader.readStringList;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.util.DatapackDiagnostics;
import com.jvn.villagerretaliation.util.DatapackResourceLoader;
import com.jvn.villagerretaliation.util.VillagerProfessionUtil;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public final class VillagerNaturalJobArmorResources {
    private static final String NATURAL_JOB_ARMOR_ROOT = "natural_job_armor";
    private static final Set<String> ARMOR_PROFILE_ROOT_KEYS = Set.of("replace", "profiles", "armor_profiles");
    private static final Set<String> ARMOR_PROFILE_KEYS = Set.of(
            "id",
            "remove",
            "professions",
            "profession",
            "chance",
            "armor_chance",
            "armorChance",
            "next_piece_chance",
            "nextPieceChance",
            "mixed_gear_chance",
            "mixedGearChance",
            "mixed_piece_chance",
            "mixedPieceChance",
            "enchant_chance",
            "enchantChance",
            "armor_sets",
            "armorSets",
            "materials"
    );
    private static final Set<String> ARMOR_SET_KEYS = Set.of(
            "id",
            "material",
            "weight",
            "weights",
            "weight_by_difficulty",
            "weightByDifficulty",
            "items",
            "feet",
            "boots",
            "legs",
            "leggings",
            "chest",
            "chestplate",
            "head",
            "helmet"
    );

    private static volatile CachedNaturalJobArmor cachedArmor = CachedNaturalJobArmor.empty();

    private VillagerNaturalJobArmorResources() {
    }

    public static void warm(MinecraftServer server) {
        load(server);
    }

    public static void clearCache() {
        cachedArmor = CachedNaturalJobArmor.empty();
    }

    public static Optional<ArmorProfile> profile(MinecraftServer server, VillagerProfession profession) {
        return load(server).profiles()
                .stream()
                .filter(profile -> profile.matches(profession))
                .findFirst();
    }

    private static NaturalJobArmorPool load(MinecraftServer server) {
        CachedNaturalJobArmor current = cachedArmor;
        if (current.server() == server) {
            return current.pool();
        }

        synchronized (VillagerNaturalJobArmorResources.class) {
            current = cachedArmor;
            if (current.server() == server) {
                return current.pool();
            }

            NaturalJobArmorPool loadedPool = read(server);
            cachedArmor = new CachedNaturalJobArmor(server, loadedPool);
            return loadedPool;
        }
    }

    private static NaturalJobArmorPool read(MinecraftServer server) {
        Map<String, ArmorProfile> profiles = new LinkedHashMap<>();
        DatapackResourceLoader.forEachJsonResource(
                server,
                NATURAL_JOB_ARMOR_ROOT,
                location -> location.getNamespace().equals(VillagerRetaliation.MOD_ID),
                (location, resource) -> readFile(location, resource, profiles));
        return new NaturalJobArmorPool(List.copyOf(profiles.values()));
    }

    private static void readFile(ResourceLocation location, Resource resource, Map<String, ArmorProfile> profiles) {
        DatapackResourceLoader.readObject(location, "natural job armor", resource).ifPresent(root -> {
            DatapackDiagnostics.warnUnknownRootKeys(location, "natural job armor", root, ARMOR_PROFILE_ROOT_KEYS);
            if (readBoolean(root, "replace", false)) {
                profiles.clear();
            }
            readProfiles(location, root, profiles);
        });
    }

    private static void readProfiles(ResourceLocation location, JsonObject root, Map<String, ArmorProfile> profiles) {
        JsonArray entries = root.getAsJsonArray("profiles");
        if (entries == null) {
            entries = root.getAsJsonArray("armor_profiles");
        }
        if (entries == null) {
            return;
        }

        int index = 0;
        for (JsonElement element : entries) {
            if (!element.isJsonObject()) {
                index++;
                continue;
            }

            JsonObject entry = element.getAsJsonObject();
            DatapackDiagnostics.warnUnknownKeys(location, "natural job armor", "profiles[" + index + "]", entry, ARMOR_PROFILE_KEYS);

            String id = readString(entry, "id");
            if (readBoolean(entry, "remove", false)) {
                if (!id.isBlank()) {
                    profiles.remove(id);
                }
                index++;
                continue;
            }

            List<VillagerProfession> professions = readProfessions(location, entry, index);
            if (professions.isEmpty()) {
                DatapackDiagnostics.warnSkippedEntry(
                        location,
                        "natural job armor",
                        "profiles[" + index + "]",
                        "Add at least one valid profession.");
                index++;
                continue;
            }

            List<WeightedArmorSet> armorSets = readArmorSets(location, entry, index);
            if (armorSets.isEmpty()) {
                DatapackDiagnostics.warnSkippedEntry(
                        location,
                        "natural job armor",
                        "profiles[" + index + "]",
                        "Add at least one armor set with valid armor items.");
                index++;
                continue;
            }

            String resolvedId = id.isBlank() ? fallbackId(location, index) : id;
            profiles.put(resolvedId, new ArmorProfile(
                    resolvedId,
                    professions,
                    readChance(entry, "chance", "armor_chance", "armorChance"),
                    readChance(entry, "next_piece_chance", "nextPieceChance"),
                    readChance(entry, "mixed_gear_chance", "mixedGearChance", "mixed_piece_chance", "mixedPieceChance"),
                    readChance(entry, "enchant_chance", "enchantChance"),
                    armorSets
            ));
            index++;
        }
    }

    private static List<VillagerProfession> readProfessions(ResourceLocation location, JsonObject entry, int profileIndex) {
        List<String> values = readStringList(entry, "professions");
        String singular = readString(entry, "profession");
        if (!singular.isBlank()) {
            values = new ArrayList<>(values);
            values.add(singular);
        }

        List<VillagerProfession> professions = new ArrayList<>();
        for (String value : values) {
            Optional<VillagerProfession> profession = VillagerProfessionUtil.parse(value);
            if (profession.isPresent()) {
                professions.add(profession.get());
            } else {
                DatapackDiagnostics.warnUnknownProfession(location, "profiles[" + profileIndex + "]", value);
            }
        }
        return List.copyOf(professions);
    }

    private static List<WeightedArmorSet> readArmorSets(ResourceLocation location, JsonObject entry, int profileIndex) {
        JsonArray entries = entry.getAsJsonArray("armor_sets");
        if (entries == null) {
            entries = entry.getAsJsonArray("armorSets");
        }
        if (entries == null) {
            entries = entry.getAsJsonArray("materials");
        }
        if (entries == null) {
            return List.of();
        }

        List<WeightedArmorSet> armorSets = new ArrayList<>();
        int index = 0;
        for (JsonElement element : entries) {
            if (!element.isJsonObject()) {
                index++;
                continue;
            }

            JsonObject armorSet = element.getAsJsonObject();
            DatapackDiagnostics.warnUnknownKeys(
                    location,
                    "natural job armor",
                    "profiles[" + profileIndex + "].armor_sets[" + index + "]",
                    armorSet,
                    ARMOR_SET_KEYS);

            ArmorItems items = readArmorItems(location, armorSet, profileIndex, index);
            if (items.isEmpty()) {
                DatapackDiagnostics.warnSkippedEntry(
                        location,
                        "natural job armor",
                        "profiles[" + profileIndex + "].armor_sets[" + index + "]",
                        "No valid armor items were configured.");
                index++;
                continue;
            }

            String id = readString(armorSet, "id");
            armorSets.add(new WeightedArmorSet(
                    id.isBlank() ? "armor_set_" + index : id,
                    readWeights(armorSet),
                    items
            ));
            index++;
        }
        return List.copyOf(armorSets);
    }

    private static ArmorItems readArmorItems(ResourceLocation location, JsonObject armorSet, int profileIndex, int armorSetIndex) {
        String material = readString(armorSet, "material").toLowerCase(Locale.ROOT);
        ArmorItems materialItems = switch (material) {
            case "leather" -> ArmorItems.LEATHER;
            case "chainmail", "chain" -> ArmorItems.CHAINMAIL;
            case "iron" -> ArmorItems.IRON;
            case "diamond" -> ArmorItems.DIAMOND;
            case "" -> ArmorItems.empty();
            default -> {
                DatapackDiagnostics.warnSkippedEntry(
                        location,
                        "natural job armor",
                        "profiles[" + profileIndex + "].armor_sets[" + armorSetIndex + "]",
                        "Unknown material \"" + material + "\". Use leather, chainmail, iron, diamond, or explicit item ids.");
                yield ArmorItems.empty();
            }
        };

        JsonObject items = readObject(armorSet, "items");
        JsonObject source = items == null ? armorSet : items;
        return new ArmorItems(
                readItem(location, source, profileIndex, armorSetIndex, materialItems.feet(), "feet", "boots"),
                readItem(location, source, profileIndex, armorSetIndex, materialItems.legs(), "legs", "leggings"),
                readItem(location, source, profileIndex, armorSetIndex, materialItems.chest(), "chest", "chestplate"),
                readItem(location, source, profileIndex, armorSetIndex, materialItems.head(), "head", "helmet")
        );
    }

    private static Item readItem(
            ResourceLocation location,
            JsonObject entry,
            int profileIndex,
            int armorSetIndex,
            Item fallback,
            String... keys
    ) {
        String value = readString(entry, keys);
        if (value.isBlank()) {
            return fallback;
        }

        String normalized = value.contains(":") ? value : "minecraft:" + value;
        ResourceLocation itemId = ResourceLocation.tryParse(normalized);
        if (itemId == null) {
            DatapackDiagnostics.warnInvalidResourceLocation(
                    location,
                    "natural job armor item",
                    "profiles[" + profileIndex + "].armor_sets[" + armorSetIndex + "]",
                    value,
                    "Use a valid item id such as minecraft:iron_helmet.");
            return fallback;
        }

        Optional<Item> item = BuiltInRegistries.ITEM.getOptional(itemId);
        if (item.isEmpty() || item.get() == Items.AIR) {
            DatapackDiagnostics.warnInvalidResourceLocation(
                    location,
                    "natural job armor item",
                    "profiles[" + profileIndex + "].armor_sets[" + armorSetIndex + "]",
                    value,
                    "Use a registered armor item id.");
            return fallback;
        }
        return item.get();
    }

    private static DifficultyWeights readWeights(JsonObject entry) {
        JsonObject weights = readObject(entry, "weight_by_difficulty");
        if (weights == null) {
            weights = readObject(entry, "weightByDifficulty");
        }
        if (weights == null) {
            weights = readObject(entry, "weights");
        }
        int fallback = Math.max(0, (int)Math.round(readDouble(entry, "weight", 1.0D)));
        return weights == null ? DifficultyWeights.fixed(fallback) : DifficultyWeights.read(weights, fallback);
    }

    private static DifficultyChances readChance(JsonObject entry, String... keys) {
        for (String key : keys) {
            JsonElement element = entry.get(key);
            if (element == null || element.isJsonNull()) {
                continue;
            }
            if (element.isJsonObject()) {
                return DifficultyChances.read(element.getAsJsonObject(), 0.0F);
            }
            if (element.isJsonPrimitive()) {
                return DifficultyChances.fixed((float)readDouble(element, 0.0D));
            }
        }
        return DifficultyChances.fixed(0.0F);
    }

    private static String fallbackId(ResourceLocation location, int index) {
        return location.getPath().replace('/', '_').replace(".json", "") + "_profile_" + index;
    }

    public record ArmorProfile(
            String id,
            List<VillagerProfession> professions,
            DifficultyChances armorChance,
            DifficultyChances nextPieceChance,
            DifficultyChances mixedGearChance,
            DifficultyChances enchantChance,
            List<WeightedArmorSet> armorSets) {
        private boolean matches(VillagerProfession profession) {
            return this.professions.contains(profession);
        }

        public Optional<WeightedArmorSet> selectArmorSet(RandomSource random, Difficulty difficulty) {
            int totalWeight = 0;
            for (WeightedArmorSet armorSet : this.armorSets) {
                totalWeight += armorSet.weight(difficulty);
            }
            if (totalWeight <= 0) {
                return Optional.empty();
            }

            int selected = random.nextInt(totalWeight);
            for (WeightedArmorSet armorSet : this.armorSets) {
                selected -= armorSet.weight(difficulty);
                if (selected < 0) {
                    return Optional.of(armorSet);
                }
            }
            return Optional.empty();
        }

        public WeightedArmorSet armorSetForSlot(RandomSource random, Difficulty difficulty, WeightedArmorSet baseArmorSet) {
            if (this.armorSets.size() < 2 || !this.mixedGearChance.passes(random, difficulty)) {
                return baseArmorSet;
            }

            return this.selectArmorSetExcept(random, difficulty, baseArmorSet).orElse(baseArmorSet);
        }

        private Optional<WeightedArmorSet> selectArmorSetExcept(
                RandomSource random,
                Difficulty difficulty,
                WeightedArmorSet excludedArmorSet
        ) {
            int totalWeight = 0;
            for (WeightedArmorSet armorSet : this.armorSets) {
                if (armorSet == excludedArmorSet) {
                    continue;
                }
                totalWeight += armorSet.weight(difficulty);
            }
            if (totalWeight <= 0) {
                return Optional.empty();
            }

            int selected = random.nextInt(totalWeight);
            for (WeightedArmorSet armorSet : this.armorSets) {
                if (armorSet == excludedArmorSet) {
                    continue;
                }
                selected -= armorSet.weight(difficulty);
                if (selected < 0) {
                    return Optional.of(armorSet);
                }
            }
            return Optional.empty();
        }
    }

    public record WeightedArmorSet(String id, DifficultyWeights weights, ArmorItems items) {
        private int weight(Difficulty difficulty) {
            return this.weights.value(difficulty);
        }
    }

    public record ArmorItems(Item feet, Item legs, Item chest, Item head) {
        private static final ArmorItems LEATHER = new ArmorItems(
                Items.LEATHER_BOOTS,
                Items.LEATHER_LEGGINGS,
                Items.LEATHER_CHESTPLATE,
                Items.LEATHER_HELMET);
        private static final ArmorItems CHAINMAIL = new ArmorItems(
                Items.CHAINMAIL_BOOTS,
                Items.CHAINMAIL_LEGGINGS,
                Items.CHAINMAIL_CHESTPLATE,
                Items.CHAINMAIL_HELMET);
        private static final ArmorItems IRON = new ArmorItems(
                Items.IRON_BOOTS,
                Items.IRON_LEGGINGS,
                Items.IRON_CHESTPLATE,
                Items.IRON_HELMET);
        private static final ArmorItems DIAMOND = new ArmorItems(
                Items.DIAMOND_BOOTS,
                Items.DIAMOND_LEGGINGS,
                Items.DIAMOND_CHESTPLATE,
                Items.DIAMOND_HELMET);

        private static ArmorItems empty() {
            return new ArmorItems(Items.AIR, Items.AIR, Items.AIR, Items.AIR);
        }

        private boolean isEmpty() {
            return this.feet == Items.AIR && this.legs == Items.AIR && this.chest == Items.AIR && this.head == Items.AIR;
        }

        public Item itemForSlot(EquipmentSlot slot) {
            return switch (slot) {
                case FEET -> this.feet;
                case LEGS -> this.legs;
                case CHEST -> this.chest;
                case HEAD -> this.head;
                default -> Items.AIR;
            };
        }
    }

    public record DifficultyChances(float peaceful, float easy, float normal, float hard) {
        private static DifficultyChances fixed(float chance) {
            float clamped = clampChance(chance);
            return new DifficultyChances(clamped, clamped, clamped, clamped);
        }

        private static DifficultyChances read(JsonObject object, float fallback) {
            return new DifficultyChances(
                    readChanceValue(object, "peaceful", fallback),
                    readChanceValue(object, "easy", fallback),
                    readChanceValue(object, "normal", fallback),
                    readChanceValue(object, "hard", fallback));
        }

        public boolean passes(RandomSource random, Difficulty difficulty) {
            return random.nextFloat() < this.value(difficulty);
        }

        private float value(Difficulty difficulty) {
            return switch (difficulty) {
                case PEACEFUL -> this.peaceful;
                case EASY -> this.easy;
                case NORMAL -> this.normal;
                case HARD -> this.hard;
            };
        }
    }

    private record DifficultyWeights(int peaceful, int easy, int normal, int hard) {
        private static DifficultyWeights fixed(int weight) {
            int clamped = Math.max(0, weight);
            return new DifficultyWeights(clamped, clamped, clamped, clamped);
        }

        private static DifficultyWeights read(JsonObject object, int fallback) {
            return new DifficultyWeights(
                    readWeightValue(object, "peaceful", fallback),
                    readWeightValue(object, "easy", fallback),
                    readWeightValue(object, "normal", fallback),
                    readWeightValue(object, "hard", fallback));
        }

        private int value(Difficulty difficulty) {
            return switch (difficulty) {
                case PEACEFUL -> this.peaceful;
                case EASY -> this.easy;
                case NORMAL -> this.normal;
                case HARD -> this.hard;
            };
        }
    }

    private record NaturalJobArmorPool(List<ArmorProfile> profiles) {
        private static NaturalJobArmorPool empty() {
            return new NaturalJobArmorPool(List.of());
        }
    }

    private record CachedNaturalJobArmor(MinecraftServer server, NaturalJobArmorPool pool) {
        private static CachedNaturalJobArmor empty() {
            return new CachedNaturalJobArmor(null, NaturalJobArmorPool.empty());
        }
    }

    private static float readChanceValue(JsonObject object, String key, float fallback) {
        return clampChance((float)readDouble(object, key, fallback));
    }

    private static float clampChance(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private static int readWeightValue(JsonObject object, String key, int fallback) {
        return Math.max(0, (int)Math.round(readDouble(object, key, fallback)));
    }
}
