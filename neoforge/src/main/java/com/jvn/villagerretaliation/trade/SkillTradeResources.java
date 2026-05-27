package com.jvn.villagerretaliation.trade;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.skill.VillagerSkill;
import com.jvn.villagerretaliation.skill.VillagerSkillRank;
import com.jvn.villagerretaliation.util.DatapackDiagnostics;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.HashSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;

public final class SkillTradeResources {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String SKILL_TRADE_ROOT = "skill_trades";
    private static final Set<String> ROOT_KEYS = Set.of("replace", "entries");
    private static final Set<String> ENTRY_KEYS = Set.of(
            "id",
            "remove",
            "professions",
            "profession",
            "skills",
            "skill",
            "min_rank",
            "minRank",
            "max_rank",
            "maxRank",
            "villager_level",
            "villagerLevel",
            "chance",
            "weight",
            "cost",
            "result",
            "max_uses",
            "maxUses",
            "xp",
            "price_multiplier",
            "priceMultiplier",
            "conditions",
            "quality_scaling",
            "qualityScaling",
            "pool",
            "wanderer_pool",
            "wandererPool"
    );
    private static final Set<String> COST_KEYS = Set.of("item", "count", "skill_discount", "skillDiscount");
    private static final Set<String> RESULT_KEYS = Set.of("item", "items", "count", "enchantments");
    private static final Set<String> ENCHANTMENT_KEYS = Set.of(
            "mode",
            "candidates",
            "enchantments",
            "fixed",
            "level_by_skill",
            "levelBySkill",
            "min_level",
            "minLevel",
            "max_level",
            "maxLevel"
    );
    private static final Set<String> MAX_USES_KEYS = Set.of("base", "bonus_by_skill", "bonusBySkill", "max_bonus", "maxBonus");
    private static final Set<String> CONDITION_KEYS = Set.of(
            "config_flags",
            "configFlags",
            "disabled_config_flags",
            "disabledConfigFlags",
            "config_flags_disabled",
            "configFlagsDisabled",
            "not_config_flags",
            "notConfigFlags"
    );
    private static final Set<String> QUALITY_SCALING_KEYS = Set.of(
            "enabled",
            "count_by_skill",
            "countBySkill",
            "cost_by_skill",
            "costBySkill",
            "max_uses_by_skill",
            "maxUsesBySkill",
            "xp_by_skill",
            "xpBySkill",
            "rare_chance_by_skill",
            "rareChanceBySkill",
            "enchantments_by_skill",
            "enchantmentsBySkill"
    );

    private static volatile CachedSkillTrades cachedSkillTrades = CachedSkillTrades.empty();

    private SkillTradeResources() {
    }

    public static void warm(MinecraftServer server) {
        load(server);
    }

    public static void clearCache() {
        cachedSkillTrades = CachedSkillTrades.empty();
    }

    public static List<SkillTradeDefinition> definitions(MinecraftServer server) {
        return load(server).definitions();
    }

    private static SkillTradePoolData load(MinecraftServer server) {
        CachedSkillTrades current = cachedSkillTrades;
        if (current.server() == server) {
            return current.pool();
        }

        synchronized (SkillTradeResources.class) {
            current = cachedSkillTrades;
            if (current.server() == server) {
                return current.pool();
            }

            SkillTradePoolData loadedPool = read(server);
            cachedSkillTrades = new CachedSkillTrades(server, loadedPool);
            return loadedPool;
        }
    }

    private static SkillTradePoolData read(MinecraftServer server) {
        Map<ResourceLocation, SkillTradeDefinition> definitions = new LinkedHashMap<>();
        server.getResourceManager()
                .listResources(SKILL_TRADE_ROOT, location -> location.getPath().endsWith(".json"))
                .entrySet()
                .stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().toString()))
                .forEach(entry -> readFile(entry.getKey(), entry.getValue(), definitions));
        LOGGER.info("Loaded {} Villager Retaliation skill trade definitions.", definitions.size());
        return new SkillTradePoolData(List.copyOf(definitions.values()));
    }

    private static void readFile(
            ResourceLocation location,
            Resource resource,
            Map<ResourceLocation, SkillTradeDefinition> definitions) {
        try (Reader reader = resource.openAsReader()) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            DatapackDiagnostics.warnUnknownRootKeys(location, "skill trades", root, ROOT_KEYS);
            if (readBoolean(root, "replace", false)) {
                definitions.clear();
                LOGGER.info("Villager Retaliation skill trade file {} requested global replace.", location);
            }
            readEntries(location, root, definitions);
        } catch (IOException | IllegalStateException | JsonParseException exception) {
            LOGGER.warn("Villager Retaliation could not read skill trade file {}. It will be skipped.", location, exception);
        }
    }

    private static void readEntries(
            ResourceLocation location,
            JsonObject root,
            Map<ResourceLocation, SkillTradeDefinition> definitions) {
        JsonArray entries = root.getAsJsonArray("entries");
        if (entries == null) {
            if (looksLikeEntry(root)) {
                readEntry(location, root, 0, definitions);
            }
            return;
        }

        int index = 0;
        for (JsonElement element : entries) {
            if (!element.isJsonObject()) {
                LOGGER.warn("Villager Retaliation skill trade file {} entry {} is not an object; it will be skipped.", location, index);
                index++;
                continue;
            }
            readEntry(location, element.getAsJsonObject(), index, definitions);
            index++;
        }
    }

    private static void readEntry(
            ResourceLocation location,
            JsonObject entry,
            int index,
            Map<ResourceLocation, SkillTradeDefinition> definitions) {
        DatapackDiagnostics.warnUnknownKeys(location, "skill trades", "entry " + index, entry, ENTRY_KEYS);
        ResourceLocation id = readEntryId(location, entry, index);
        if (readBoolean(entry, "remove", false)) {
            definitions.remove(id);
            return;
        }

        Optional<SkillTradeDefinition> definition = parseDefinition(location, entry, index, id);
        definition.ifPresent(value -> definitions.put(value.id(), value));
    }

    private static Optional<SkillTradeDefinition> parseDefinition(
            ResourceLocation location,
            JsonObject entry,
            int index,
            ResourceLocation id) {
        String context = "entry " + id;
        Set<ResourceLocation> professions = readProfessions(location, context, entry);
        Set<VillagerSkill> skills = readSkills(location, context, entry);
        if (skills.isEmpty()) {
            LOGGER.warn("Villager Retaliation skill trade {} {} has no valid skills; it will be skipped.", location, context);
            return Optional.empty();
        }

        Optional<SkillTradeResult> result = readResult(location, context, entry);
        if (result.isEmpty()) {
            LOGGER.warn("Villager Retaliation skill trade {} {} has no valid result item; it will be skipped.", location, context);
            return Optional.empty();
        }

        VillagerSkillRank minRank = readRank(entry, "min_rank", "minRank", VillagerSkillRank.NOVICE);
        Optional<VillagerSkillRank> maxRank = readOptionalRank(location, context, entry, "max_rank", "maxRank");
        if (hasAnyKey(entry, "max_rank", "maxRank") && maxRank.isEmpty()) {
            return Optional.empty();
        }
        if (maxRank.isPresent() && maxRank.get().ordinal() < minRank.ordinal()) {
            LOGGER.warn(
                    "Villager Retaliation skill trade {} {} has max_rank {} below min_rank {}; it will be skipped.",
                    location,
                    context,
                    maxRank.get().serializedName(),
                    minRank.serializedName());
            return Optional.empty();
        }

        SkillTradePool pool = readPool(entry, professions);
        return Optional.of(new SkillTradeDefinition(
                id,
                professions,
                skills,
                minRank,
                maxRank.orElse(null),
                readInt(entry, "villager_level", "villagerLevel", 1),
                readChance(location, context, entry),
                readInt(entry, "weight", 1),
                readCost(location, context, entry),
                result.get(),
                readMaxUses(location, context, entry),
                readInt(entry, "xp", 0),
                (float) readDouble(entry, "price_multiplier", "priceMultiplier", 0.05D),
                readConditions(location, context, entry),
                readQualityScaling(location, context, entry),
                pool
        ));
    }

    private static SkillTradePool readPool(JsonObject entry, Set<ResourceLocation> professions) {
        String poolValue = readString(entry, "pool");
        if (poolValue.isBlank()) {
            poolValue = readString(entry, "wanderer_pool", "wandererPool");
        }

        boolean wanderingTrader = professions.contains(SkillTradeDefinition.WANDERING_TRADER_PROFESSION)
                || isWanderingTraderPool(poolValue);
        return wanderingTrader ? SkillTradePool.wanderingTraderPool(poolValue) : SkillTradePool.VILLAGER;
    }

    private static boolean isWanderingTraderPool(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("generic")
                || normalized.equals("rare")
                || normalized.contains("wanderer")
                || normalized.contains("wandering_trader");
    }

    private static Set<ResourceLocation> readProfessions(ResourceLocation location, String context, JsonObject entry) {
        Set<ResourceLocation> professions = new HashSet<>();
        for (String value : readStringList(entry, "professions", "profession")) {
            Optional<ResourceLocation> profession = parseMinecraftLocation(value);
            if (profession.isEmpty()) {
                LOGGER.warn("Villager Retaliation skill trade {} {} references invalid profession id \"{}\".", location, context, value);
                continue;
            }

            ResourceLocation professionId = profession.get();
            if (!SkillTradeDefinition.WANDERING_TRADER_PROFESSION.equals(professionId)
                    && BuiltInRegistries.VILLAGER_PROFESSION.getOptional(professionId).isEmpty()) {
                DatapackDiagnostics.warnUnknownProfession(location, context, value);
            }
            professions.add(professionId);
        }
        return Set.copyOf(professions);
    }

    private static Set<VillagerSkill> readSkills(ResourceLocation location, String context, JsonObject entry) {
        Set<VillagerSkill> skills = EnumSet.noneOf(VillagerSkill.class);
        for (String value : readStringList(entry, "skills", "skill")) {
            VillagerSkill skill = parseSkill(value);
            if (skill == null) {
                LOGGER.warn("Villager Retaliation skill trade {} {} references unknown skill \"{}\".", location, context, value);
                continue;
            }
            skills.add(skill);
        }
        return Set.copyOf(skills);
    }

    private static VillagerSkill parseSkill(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        int namespaceSeparator = normalized.indexOf(':');
        if (namespaceSeparator >= 0) {
            normalized = normalized.substring(namespaceSeparator + 1);
        }
        return VillagerSkill.bySerializedName(normalized);
    }

    private static VillagerSkillRank readRank(
            JsonObject entry,
            String snakeKey,
            String camelKey,
            VillagerSkillRank fallback) {
        VillagerSkillRank rank = VillagerSkillRank.bySerializedName(readString(entry, snakeKey, camelKey));
        return rank == null ? fallback : rank;
    }

    private static boolean hasAnyKey(JsonObject entry, String... keys) {
        for (String key : keys) {
            if (entry.has(key)) {
                return true;
            }
        }
        return false;
    }

    private static Optional<VillagerSkillRank> readOptionalRank(
            ResourceLocation location,
            String context,
            JsonObject entry,
            String snakeKey,
            String camelKey) {
        if (!entry.has(snakeKey) && !entry.has(camelKey)) {
            return Optional.empty();
        }

        String value = readString(entry, snakeKey, camelKey);
        VillagerSkillRank rank = VillagerSkillRank.bySerializedName(value);
        if (rank == null) {
            LOGGER.warn(
                    "Villager Retaliation skill trade {} {} references unknown max_rank \"{}\"; it will be skipped.",
                    location,
                    context,
                    value);
        }
        return Optional.ofNullable(rank);
    }

    private static double readChance(ResourceLocation location, String context, JsonObject entry) {
        double chance = readDouble(entry, "chance", 1.0D);
        if (chance < 0.0D || chance > 1.0D) {
            LOGGER.warn("Villager Retaliation skill trade {} {} has chance {}; it will be clamped to 0..1.", location, context, chance);
        }
        return Math.clamp(chance, 0.0D, 1.0D);
    }

    private static SkillTradeCost readCost(ResourceLocation location, String context, JsonObject entry) {
        JsonObject cost = readObject(entry, "cost");
        if (cost == null) {
            return SkillTradeCost.DEFAULT;
        }

        DatapackDiagnostics.warnUnknownKeys(location, "skill trade cost", context, cost, COST_KEYS);
        Item item = readItem(cost, "item").orElse(Items.EMERALD);
        return new SkillTradeCost(
                item,
                readInt(cost, "count", 1),
                readSkillDiscount(cost)
        );
    }

    private static SkillTradeCost.SkillDiscount readSkillDiscount(JsonObject cost) {
        JsonObject discount = readObject(cost, "skill_discount");
        if (discount == null) {
            discount = readObject(cost, "skillDiscount");
        }
        if (discount == null) {
            return SkillTradeCost.SkillDiscount.DISABLED;
        }

        return new SkillTradeCost.SkillDiscount(
                readBoolean(discount, "enabled", false),
                readInt(discount, "max_percent", "maxPercent", 0)
        );
    }

    private static Optional<SkillTradeResult> readResult(ResourceLocation location, String context, JsonObject entry) {
        JsonObject result = readObject(entry, "result");
        if (result == null) {
            return Optional.empty();
        }

        DatapackDiagnostics.warnUnknownKeys(location, "skill trade result", context, result, RESULT_KEYS);
        List<Item> items = new ArrayList<>();
        for (String value : readStringList(result, "items", "item")) {
            Optional<Item> item = readItem(value);
            if (item.isEmpty()) {
                LOGGER.warn("Villager Retaliation skill trade {} {} references unknown result item \"{}\".", location, context, value);
                continue;
            }
            items.add(item.get());
        }
        if (items.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(new SkillTradeResult(
                items,
                readInt(result, "count", 1),
                readEnchantments(location, context, result)
        ));
    }

    private static SkillTradeEnchantments readEnchantments(ResourceLocation location, String context, JsonObject result) {
        JsonObject enchantments = readObject(result, "enchantments");
        if (enchantments == null) {
            return SkillTradeEnchantments.NONE;
        }

        DatapackDiagnostics.warnUnknownKeys(location, "skill trade enchantments", context, enchantments, ENCHANTMENT_KEYS);
        SkillTradeEnchantments.Mode mode = readEnchantmentMode(enchantments);
        List<ResourceLocation> candidates = readLocationList(enchantments, "candidates", "enchantments");
        Map<ResourceLocation, Integer> fixed = readFixedEnchantments(location, context, enchantments);
        if (mode == SkillTradeEnchantments.Mode.RANDOM_FROM && candidates.isEmpty()) {
            LOGGER.warn("Villager Retaliation skill trade {} {} uses random enchantments without candidates.", location, context);
            mode = SkillTradeEnchantments.Mode.NONE;
        }
        if (mode == SkillTradeEnchantments.Mode.FIXED && fixed.isEmpty() && candidates.isEmpty()) {
            LOGGER.warn("Villager Retaliation skill trade {} {} uses fixed enchantments without enchantment ids.", location, context);
            mode = SkillTradeEnchantments.Mode.NONE;
        }

        return new SkillTradeEnchantments(
                mode,
                candidates,
                fixed,
                readBoolean(enchantments, "level_by_skill", "levelBySkill", false),
                readInt(enchantments, "min_level", "minLevel", 1),
                readInt(enchantments, "max_level", "maxLevel", 1)
        );
    }

    private static SkillTradeEnchantments.Mode readEnchantmentMode(JsonObject enchantments) {
        String mode = readString(enchantments, "mode").trim().toLowerCase(Locale.ROOT);
        if (mode.isBlank()) {
            if (enchantments.has("fixed")) {
                return SkillTradeEnchantments.Mode.FIXED;
            }
            if (enchantments.has("candidates") || enchantments.has("enchantments")) {
                return SkillTradeEnchantments.Mode.RANDOM_FROM;
            }
            return SkillTradeEnchantments.Mode.NONE;
        }

        return switch (mode) {
            case "random", "random_from", "random-from" -> SkillTradeEnchantments.Mode.RANDOM_FROM;
            case "fixed" -> SkillTradeEnchantments.Mode.FIXED;
            default -> SkillTradeEnchantments.Mode.NONE;
        };
    }

    private static Map<ResourceLocation, Integer> readFixedEnchantments(
            ResourceLocation location,
            String context,
            JsonObject enchantments) {
        JsonElement fixed = enchantments.get("fixed");
        if (fixed == null || fixed.isJsonNull()) {
            return Map.of();
        }

        Map<ResourceLocation, Integer> values = new LinkedHashMap<>();
        if (fixed.isJsonObject()) {
            JsonObject object = fixed.getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                parseMinecraftLocation(entry.getKey()).ifPresentOrElse(
                        id -> values.put(id, readInt(entry.getValue(), 1)),
                        () -> LOGGER.warn("Villager Retaliation skill trade {} {} references invalid enchantment id \"{}\".", location, context, entry.getKey())
                );
            }
            return Map.copyOf(values);
        }

        if (!fixed.isJsonArray()) {
            return Map.of();
        }

        for (JsonElement element : fixed.getAsJsonArray()) {
            if (element.isJsonPrimitive()) {
                parseMinecraftLocation(element.getAsString()).ifPresent(id -> values.put(id, 0));
            } else if (element.isJsonObject()) {
                JsonObject object = element.getAsJsonObject();
                String idValue = readString(object, "id");
                if (idValue.isBlank()) {
                    idValue = readString(object, "enchantment");
                }
                parseMinecraftLocation(idValue).ifPresent(id -> values.put(id, readInt(object, "level", 0)));
            }
        }
        return Map.copyOf(values);
    }

    private static SkillTradeMaxUses readMaxUses(ResourceLocation location, String context, JsonObject entry) {
        JsonElement element = entry.get("max_uses");
        if (element == null) {
            element = entry.get("maxUses");
        }
        if (element == null || element.isJsonNull()) {
            return SkillTradeMaxUses.DEFAULT;
        }
        if (element.isJsonPrimitive()) {
            return new SkillTradeMaxUses(readInt(element, SkillTradeMaxUses.DEFAULT.base()), false, 0);
        }
        if (!element.isJsonObject()) {
            return SkillTradeMaxUses.DEFAULT;
        }

        JsonObject maxUses = element.getAsJsonObject();
        DatapackDiagnostics.warnUnknownKeys(location, "skill trade max_uses", context, maxUses, MAX_USES_KEYS);
        return new SkillTradeMaxUses(
                readInt(maxUses, "base", SkillTradeMaxUses.DEFAULT.base()),
                readBoolean(maxUses, "bonus_by_skill", "bonusBySkill", false),
                readInt(maxUses, "max_bonus", "maxBonus", 0)
        );
    }

    private static SkillTradeConditions readConditions(ResourceLocation location, String context, JsonObject entry) {
        JsonObject conditions = readObject(entry, "conditions");
        if (conditions == null) {
            return SkillTradeConditions.EMPTY;
        }

        DatapackDiagnostics.warnUnknownKeys(location, "skill trade conditions", context, conditions, CONDITION_KEYS);
        return new SkillTradeConditions(
                readConfigFlags(location, context, conditions, "config_flags", "configFlags"),
                readConfigFlags(location, context, conditions,
                        "disabled_config_flags",
                        "disabledConfigFlags",
                        "config_flags_disabled",
                        "configFlagsDisabled",
                        "not_config_flags",
                        "notConfigFlags")
        );
    }

    private static SkillTradeQualityScaling readQualityScaling(ResourceLocation location, String context, JsonObject entry) {
        JsonElement element = entry.has("quality_scaling") ? entry.get("quality_scaling") : entry.get("qualityScaling");
        if (element == null || element.isJsonNull()) {
            return SkillTradeQualityScaling.DISABLED;
        }
        if (element.isJsonPrimitive()) {
            return readBoolean(element, false)
                    ? SkillTradeQualityScaling.ENABLED_DEFAULTS
                    : SkillTradeQualityScaling.DISABLED;
        }
        if (!element.isJsonObject()) {
            LOGGER.warn("Villager Retaliation skill trade {} {} has non-object quality_scaling; it will be ignored.", location, context);
            return SkillTradeQualityScaling.DISABLED;
        }

        JsonObject scaling = element.getAsJsonObject();
        DatapackDiagnostics.warnUnknownKeys(location, "skill trade quality_scaling", context, scaling, QUALITY_SCALING_KEYS);
        boolean enabled = readBoolean(scaling, "enabled", true);
        return new SkillTradeQualityScaling(
                enabled,
                readBoolean(scaling, "count_by_skill", "countBySkill", true),
                readBoolean(scaling, "cost_by_skill", "costBySkill", true),
                readBoolean(scaling, "max_uses_by_skill", "maxUsesBySkill", true),
                readBoolean(scaling, "xp_by_skill", "xpBySkill", false),
                readBoolean(scaling, "rare_chance_by_skill", "rareChanceBySkill", true),
                readBoolean(scaling, "enchantments_by_skill", "enchantmentsBySkill", true)
        );
    }

    private static Set<SkillTradeConfigFlag> readConfigFlags(
            ResourceLocation location,
            String context,
            JsonObject entry,
            String... keys) {
        Set<SkillTradeConfigFlag> flags = EnumSet.noneOf(SkillTradeConfigFlag.class);
        for (String value : readStringList(entry, keys)) {
            SkillTradeConfigFlag.parse(value).ifPresentOrElse(
                    flags::add,
                    () -> LOGGER.warn("Villager Retaliation skill trade {} {} references unknown config flag \"{}\".", location, context, value)
            );
        }
        return Set.copyOf(flags);
    }

    private static Optional<Item> readItem(JsonObject entry, String key) {
        return readItem(readString(entry, key));
    }

    private static Optional<Item> readItem(String value) {
        return parseMinecraftLocation(value)
                .flatMap(location -> BuiltInRegistries.ITEM.getOptional(location))
                .filter(item -> item != Items.AIR);
    }

    private static List<ResourceLocation> readLocationList(JsonObject entry, String... keys) {
        List<ResourceLocation> values = new ArrayList<>();
        for (String value : readStringList(entry, keys)) {
            parseMinecraftLocation(value).ifPresent(values::add);
        }
        return List.copyOf(values);
    }

    private static Optional<ResourceLocation> parseMinecraftLocation(String value) {
        return parseLocation(value, "minecraft");
    }

    private static Optional<ResourceLocation> parseLocation(String value, String defaultNamespace) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }

        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!normalized.contains(":")) {
            normalized = defaultNamespace + ":" + normalized;
        }
        return Optional.ofNullable(ResourceLocation.tryParse(normalized));
    }

    private static ResourceLocation readEntryId(ResourceLocation location, JsonObject entry, int index) {
        String id = readString(entry, "id");
        return parseLocation(id, VillagerRetaliation.MOD_ID)
                .orElseGet(() -> VillagerRetaliation.id(location.getNamespace()
                        + "_"
                        + location.getPath().replace('/', '_').replace(".json", "")
                        + "_entry_"
                        + index));
    }

    private static boolean looksLikeEntry(JsonObject root) {
        return root.has("result") || root.has("cost") || root.has("professions") || root.has("skills");
    }

    private static JsonObject readObject(JsonObject entry, String key) {
        JsonElement element = entry.get(key);
        return element == null || !element.isJsonObject() ? null : element.getAsJsonObject();
    }

    private static List<String> readStringList(JsonObject entry, String... keys) {
        List<String> values = new ArrayList<>();
        for (String key : keys) {
            JsonElement element = entry.get(key);
            if (element == null || element.isJsonNull()) {
                continue;
            }
            if (element.isJsonPrimitive()) {
                String value = element.getAsString().trim();
                if (!value.isBlank()) {
                    values.add(value);
                }
                continue;
            }
            if (!element.isJsonArray()) {
                continue;
            }

            for (JsonElement child : element.getAsJsonArray()) {
                if (!child.isJsonPrimitive()) {
                    continue;
                }
                String value = child.getAsString().trim();
                if (!value.isBlank()) {
                    values.add(value);
                }
            }
        }
        return List.copyOf(values);
    }

    private static String readString(JsonObject entry, String... keys) {
        for (String key : keys) {
            JsonElement element = entry.get(key);
            if (element != null && element.isJsonPrimitive()) {
                return element.getAsString().trim();
            }
        }
        return "";
    }

    private static int readInt(JsonObject entry, String key, int fallback) {
        return readInt(entry.get(key), fallback);
    }

    private static int readInt(JsonObject entry, String snakeKey, String camelKey, int fallback) {
        JsonElement element = entry.has(snakeKey) ? entry.get(snakeKey) : entry.get(camelKey);
        return readInt(element, fallback);
    }

    private static int readInt(JsonElement element, int fallback) {
        if (element == null || !element.isJsonPrimitive()) {
            return fallback;
        }
        try {
            return element.getAsInt();
        } catch (NumberFormatException | UnsupportedOperationException ignored) {
            return fallback;
        }
    }

    private static double readDouble(JsonObject entry, String key, double fallback) {
        return readDouble(entry.get(key), fallback);
    }

    private static double readDouble(JsonObject entry, String snakeKey, String camelKey, double fallback) {
        JsonElement element = entry.has(snakeKey) ? entry.get(snakeKey) : entry.get(camelKey);
        return readDouble(element, fallback);
    }

    private static double readDouble(JsonElement element, double fallback) {
        if (element == null || !element.isJsonPrimitive()) {
            return fallback;
        }
        try {
            return element.getAsDouble();
        } catch (NumberFormatException | UnsupportedOperationException ignored) {
            return fallback;
        }
    }

    private static boolean readBoolean(JsonObject entry, String key, boolean fallback) {
        return readBoolean(entry.get(key), fallback);
    }

    private static boolean readBoolean(JsonObject entry, String snakeKey, String camelKey, boolean fallback) {
        JsonElement element = entry.has(snakeKey) ? entry.get(snakeKey) : entry.get(camelKey);
        return readBoolean(element, fallback);
    }

    private static boolean readBoolean(JsonElement element, boolean fallback) {
        if (element == null || !element.isJsonPrimitive()) {
            return fallback;
        }
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (primitive.isBoolean()) {
            return primitive.getAsBoolean();
        }
        if (primitive.isString()) {
            return Boolean.parseBoolean(primitive.getAsString());
        }
        return fallback;
    }

    private record SkillTradePoolData(List<SkillTradeDefinition> definitions) {
        private static SkillTradePoolData empty() {
            return new SkillTradePoolData(List.of());
        }
    }

    private record CachedSkillTrades(MinecraftServer server, SkillTradePoolData pool) {
        private static CachedSkillTrades empty() {
            return new CachedSkillTrades(null, SkillTradePoolData.empty());
        }
    }
}
