package com.jvn.villagerretaliation.trade;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.interaction.VillagerCurrencyResources;
import com.jvn.villagerretaliation.reputation.VillagerReputationLevel;
import com.jvn.villagerretaliation.skill.VillagerSkill;
import com.jvn.villagerretaliation.skill.VillagerSkillRank;
import com.jvn.villagerretaliation.util.DatapackDiagnostics;
import com.jvn.villagerretaliation.util.DatapackJsonReader;
import com.jvn.villagerretaliation.util.DatapackResourceLoader;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
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
            "request",
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
    private static final Set<String> REQUEST_KEYS = Set.of(
            "targetable",
            "display_priority",
            "displayPriority",
            "min_reputation",
            "minReputation",
            "wait_days",
            "waitDays",
            "cooldown_days",
            "cooldownDays",
            "extra_cost",
            "extraCost"
    );
    private static final Set<String> REQUEST_COST_KEYS = Set.of("item", "count");

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

    public static Optional<SkillTradeDefinition> definition(MinecraftServer server, ResourceLocation id) {
        if (id == null) {
            return Optional.empty();
        }
        return definitions(server)
                .stream()
                .filter(definition -> definition.id().equals(id))
                .findFirst();
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
        Map<ResourceLocation, ResourceLocation> definitionSources = new LinkedHashMap<>();
        DatapackResourceLoader.forEachJsonResource(
                server,
                SKILL_TRADE_ROOT,
                (location, resource) -> readFile(server, location, resource, definitions, definitionSources));
        LOGGER.info("Loaded {} Villager Retaliation skill trade definitions.", definitions.size());
        return new SkillTradePoolData(List.copyOf(definitions.values()));
    }

    private static void readFile(
            MinecraftServer server,
            ResourceLocation location,
            Resource resource,
            Map<ResourceLocation, SkillTradeDefinition> definitions,
            Map<ResourceLocation, ResourceLocation> definitionSources) {
        DatapackResourceLoader.readObject(location, "skill trade", resource).ifPresent(root -> {
            DatapackDiagnostics.warnUnknownRootKeys(location, "skill trades", root, ROOT_KEYS);
            if (root.has("replace") && !validBoolean(root.get("replace"))) {
                DatapackDiagnostics.warnSkippedEntry(
                        location, "skill trades", "file root.replace", "Expected true or false; entries will load without replacing earlier files.");
            }
            if (readBoolean(root, "replace", false)) {
                definitions.clear();
                definitionSources.clear();
                LOGGER.info("Villager Retaliation skill trade file {} requested global replace.", location);
            }
            readEntries(server, location, root, definitions, definitionSources);
        });
    }

    private static void readEntries(
            MinecraftServer server,
            ResourceLocation location,
            JsonObject root,
            Map<ResourceLocation, SkillTradeDefinition> definitions,
            Map<ResourceLocation, ResourceLocation> definitionSources) {
        JsonElement entriesElement = root.get("entries");
        if (entriesElement != null && !entriesElement.isJsonArray()) {
            DatapackDiagnostics.warnSkippedEntry(
                    location, "skill trades", "entries", "Expected an array of skill-trade objects.");
            return;
        }
        JsonArray entries = entriesElement == null ? null : entriesElement.getAsJsonArray();
        if (entries == null) {
            if (looksLikeEntry(root)) {
                readEntry(server, location, root, 0, definitions, definitionSources);
            }
            return;
        }

        int index = 0;
        for (JsonElement element : entries) {
            if (!element.isJsonObject()) {
                DatapackDiagnostics.warnSkippedEntry(
                        location, "skill trade", "entry " + index, "Expected an object; valid sibling entries remain loaded.");
                index++;
                continue;
            }
            readEntry(server, location, element.getAsJsonObject(), index, definitions, definitionSources);
            index++;
        }
    }

    private static void readEntry(
            MinecraftServer server,
            ResourceLocation location,
            JsonObject entry,
            int index,
            Map<ResourceLocation, SkillTradeDefinition> definitions,
            Map<ResourceLocation, ResourceLocation> definitionSources) {
        DatapackDiagnostics.warnUnknownKeys(location, "skill trades", "entry " + index, entry, ENTRY_KEYS);
        if (!validateEntryShape(server, location, entry, index)) {
            return;
        }
        ResourceLocation id = readEntryId(location, entry, index);
        if (readBoolean(entry, "remove", false)) {
            definitions.remove(id);
            definitionSources.remove(id);
            return;
        }

        Optional<SkillTradeDefinition> definition = parseDefinition(server, location, entry, index, id);
        definition.ifPresent(value -> {
            if (definitions.containsKey(value.id())) {
                DatapackDiagnostics.warnDuplicateId(
                        location, "skill trade", value.id().toString(), definitionSources.get(value.id()));
            }
            definitions.put(value.id(), value);
            definitionSources.put(value.id(), location);
        });
    }

    private static boolean validateEntryShape(
            MinecraftServer server, ResourceLocation location, JsonObject entry, int index) {
        String context = "entry " + index;
        boolean remove = entry.has("remove") && validBoolean(entry.get("remove")) && entry.get("remove").getAsBoolean();
        if (!entry.has("id") || !entry.get("id").isJsonPrimitive()
                || parseLocation(readString(entry, "id"), VillagerRetaliation.MOD_ID).isEmpty()) {
            return invalid(location, context + ".id", "Expected a stable, valid resource-location id.");
        }
        if (entry.has("remove") && !validBoolean(entry.get("remove"))) {
            return invalid(location, context + ".remove", "Expected true or false.");
        }
        if (remove) return true;
        if (!validStringList(entry, "skills", "skill")) {
            return invalid(location, context + ".skills", "Expected a skill id or an array of skill ids.");
        }
        List<String> skillIds = readStringList(entry, "skills", "skill");
        if (skillIds.isEmpty() || skillIds.stream().anyMatch(value -> parseSkill(value) == null)) {
            return invalid(location, context + ".skills", "Expected at least one known Villager Retaliation skill id.");
        }
        if (!validStringList(entry, "professions", "profession")) {
            return invalid(location, context + ".professions", "Expected a profession id or an array of profession ids.");
        }
        if (readStringList(entry, "professions", "profession").stream().anyMatch(value -> {
            Optional<ResourceLocation> profession = parseMinecraftLocation(value);
            return profession.isEmpty() || (!SkillTradeDefinition.WANDERING_TRADER_PROFESSION.equals(profession.get())
                    && BuiltInRegistries.VILLAGER_PROFESSION.getOptional(profession.get()).isEmpty());
        })) {
            return invalid(location, context + ".professions", "Expected registered villager profession ids.");
        }
        for (String key : List.of("min_rank", "minRank", "max_rank", "maxRank")) {
            if (entry.has(key) && (!entry.get(key).isJsonPrimitive()
                    || VillagerSkillRank.bySerializedName(entry.get(key).getAsString()) == null)) {
                return invalid(location, context + "." + key, "Expected novice, apprentice, skilled, expert, or master.");
            }
        }
        VillagerSkillRank minRank = readRank(entry, "min_rank", "minRank", VillagerSkillRank.NOVICE);
        Optional<VillagerSkillRank> maxRank = readOptionalRank(location, context, entry, "max_rank", "maxRank");
        if (maxRank.isPresent() && maxRank.get().ordinal() < minRank.ordinal()) {
            return invalid(location, context + ".max_rank", "Expected a rank equal to or above min_rank.");
        }
        String pool = readString(entry, "pool");
        if (pool.isBlank()) pool = readString(entry, "wanderer_pool", "wandererPool");
        if (!pool.isBlank() && !Set.of("villager", "generic", "rare", "wanderer_generic", "wanderer_rare",
                "wandering_trader_generic", "wandering_trader_rare").contains(pool.toLowerCase(Locale.ROOT))) {
            return invalid(location, context + ".pool", "Expected villager, generic, rare, or a documented wandering-trader alias.");
        }
        List<String> professionIds = readStringList(entry, "professions", "profession");
        boolean wanderingProfession = professionIds.stream()
                .map(SkillTradeResources::parseMinecraftLocation)
                .flatMap(Optional::stream)
                .anyMatch(SkillTradeDefinition.WANDERING_TRADER_PROFESSION::equals);
        boolean wanderingPool = !pool.isBlank() && !pool.equalsIgnoreCase("villager");
        if (!pool.isBlank() && !professionIds.isEmpty() && wanderingProfession != wanderingPool) {
            return invalid(location, context + ".pool", "Pair wandering-trader professions with a wandering pool and resident professions with the villager pool.");
        }
        if (!entry.has("result") || !entry.get("result").isJsonObject()) {
            return invalid(location, context + ".result", "Expected an object containing item/items and count.");
        }
        JsonObject result = entry.getAsJsonObject("result");
        List<String> resultItems = readStringList(result, "items", "item");
        if (resultItems.isEmpty() || resultItems.stream().anyMatch(value -> readItem(value).isEmpty())) {
            return invalid(location, context + ".result.item", "Expected at least one registered item id.");
        }
        if (!validOptionalInt(result, "count", 1, 64)) {
            return invalid(location, context + ".result.count", "Expected an integer from 1 to 64.");
        }
        if (result.has("enchantments") && !validEnchantments(server, result.get("enchantments"))) {
            return invalid(location, context + ".result.enchantments", "Expected a valid mode, registered enchantment ids, boolean scaling, and levels from 1 to 255.");
        }
        if (entry.has("cost")) {
            if (!entry.get("cost").isJsonObject()) return invalid(location, context + ".cost", "Expected an object.");
            JsonObject cost = entry.getAsJsonObject("cost");
            if (cost.has("item") && readItem(cost, "item").isEmpty()) {
                return invalid(location, context + ".cost.item", "Expected a registered item id.");
            }
            if (!validOptionalInt(cost, "count", 1, 64)) {
                return invalid(location, context + ".cost.count", "Expected an integer from 1 to 64.");
            }
        }
        if (!validOptionalInt(entry, "villager_level", "villagerLevel", 1, 5)
                || !validOptionalInt(entry, "weight", null, 1, 10_000)
                || !validOptionalInt(entry, "xp", null, 0, 10_000)) {
            return invalid(location, context, "villager_level, weight, or xp is outside its documented integer range.");
        }
        if (entry.has("chance") && (!validNumber(entry.get("chance"))
                || entry.get("chance").getAsDouble() < 0.0D || entry.get("chance").getAsDouble() > 1.0D)) {
            return invalid(location, context + ".chance", "Expected a number from 0 to 1.");
        }
        JsonElement priceMultiplier = entry.has("price_multiplier") ? entry.get("price_multiplier") : entry.get("priceMultiplier");
        if (priceMultiplier != null && (!validNumber(priceMultiplier)
                || priceMultiplier.getAsDouble() < 0.0D || priceMultiplier.getAsDouble() > 1.0D)) {
            return invalid(location, context + ".price_multiplier", "Expected a number from 0 to 1.");
        }
        JsonElement maxUses = entry.has("max_uses") ? entry.get("max_uses") : entry.get("maxUses");
        if (maxUses != null) {
            if (maxUses.isJsonPrimitive()) {
                if (!validInteger(maxUses, 1, 64)) return invalid(location, context + ".max_uses", "Expected an integer from 1 to 64.");
            } else if (!maxUses.isJsonObject()) {
                return invalid(location, context + ".max_uses", "Expected an integer or object.");
            } else {
                JsonObject object = maxUses.getAsJsonObject();
                if (!validOptionalInt(object, "base", 1, 64) || !validOptionalInt(object, "max_bonus", "maxBonus", 0, 64)
                        || !validOptionalBoolean(object, "bonus_by_skill", "bonusBySkill")) {
                    return invalid(location, context + ".max_uses", "Expected base/max_bonus bounds and a boolean bonus_by_skill.");
                }
            }
        }
        JsonElement quality = entry.has("quality_scaling") ? entry.get("quality_scaling") : entry.get("qualityScaling");
        if (quality != null && !validBoolean(quality) && !quality.isJsonObject()) {
            return invalid(location, context + ".quality_scaling", "Expected true, false, or an object of boolean scaling switches.");
        }
        if (quality != null && quality.isJsonObject()) {
            JsonObject object = quality.getAsJsonObject();
            for (String key : QUALITY_SCALING_KEYS) {
                if (object.has(key) && !validBoolean(object.get(key))) {
                    return invalid(location, context + ".quality_scaling." + key, "Expected true or false.");
                }
            }
        }
        if (entry.has("conditions")) {
            if (!entry.get("conditions").isJsonObject()) return invalid(location, context + ".conditions", "Expected an object.");
            JsonObject conditions = entry.getAsJsonObject("conditions");
            for (String key : CONDITION_KEYS) {
                if (!conditions.has(key)) continue;
                JsonElement flags = conditions.get(key);
                if (!flags.isJsonArray()) return invalid(location, context + ".conditions." + key, "Expected an array of config flag names.");
                for (JsonElement flag : flags.getAsJsonArray()) {
                    if (!flag.isJsonPrimitive() || !flag.getAsJsonPrimitive().isString()
                            || SkillTradeConfigFlag.parse(flag.getAsString()).isEmpty()) {
                        return invalid(location, context + ".conditions." + key, "Expected only documented skill-trade config flags.");
                    }
                }
            }
        }
        JsonElement requestElement = entry.get("request");
        if (requestElement != null) {
            if (!requestElement.isJsonObject()) return invalid(location, context + ".request", "Expected an object.");
            JsonObject request = requestElement.getAsJsonObject();
            if (request.has("targetable") && !validBoolean(request.get("targetable"))) {
                return invalid(location, context + ".request.targetable", "Expected true or false.");
            }
            if (!validOptionalInt(request, "display_priority", "displayPriority", -100_000, 100_000)
                    || !validOptionalInt(request, "wait_days", "waitDays", 0, 3650)
                    || !validOptionalInt(request, "cooldown_days", "cooldownDays", 0, 3650)) {
                return invalid(location, context + ".request", "Priority or day values are outside their documented integer range.");
            }
            String reputation = readString(request, "min_reputation", "minReputation");
            if (!reputation.isBlank()) {
                try {
                    VillagerReputationLevel.valueOf(reputation.toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException exception) {
                    return invalid(location, context + ".request.min_reputation", "Expected a named reputation level.");
                }
            }
            JsonObject extraCost = request.has("extra_cost") && request.get("extra_cost").isJsonObject()
                    ? request.getAsJsonObject("extra_cost")
                    : request.has("extraCost") && request.get("extraCost").isJsonObject()
                            ? request.getAsJsonObject("extraCost") : null;
            if ((request.has("extra_cost") || request.has("extraCost")) && extraCost == null) {
                return invalid(location, context + ".request.extra_cost", "Expected an object.");
            }
            if (extraCost != null && (readItem(extraCost, "item").isEmpty()
                    || !validOptionalInt(extraCost, "count", 1, 64))) {
                return invalid(location, context + ".request.extra_cost", "Expected a registered item and count from 1 to 64.");
            }
        }
        return true;
    }

    private static boolean invalid(ResourceLocation location, String context, String reason) {
        DatapackDiagnostics.warnSkippedEntry(location, "skill trade", context, reason);
        return false;
    }

    private static boolean validStringList(JsonObject object, String... keys) {
        for (String key : keys) {
            JsonElement value = object.get(key);
            if (value == null) continue;
            if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) continue;
            if (!value.isJsonArray()) return false;
            for (JsonElement child : value.getAsJsonArray()) {
                if (!child.isJsonPrimitive() || !child.getAsJsonPrimitive().isString()) return false;
            }
        }
        return true;
    }

    private static boolean validBoolean(JsonElement value) {
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean();
    }

    private static boolean validNumber(JsonElement value) {
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber();
    }

    private static boolean validInteger(JsonElement value, int min, int max) {
        if (!validNumber(value)) return false;
        try {
            int parsed = value.getAsInt();
            return parsed >= min && parsed <= max && value.getAsDouble() == parsed;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static boolean validOptionalBoolean(JsonObject object, String snakeKey, String camelKey) {
        JsonElement value = object.has(snakeKey) ? object.get(snakeKey) : object.get(camelKey);
        return value == null || validBoolean(value);
    }

    private static boolean validEnchantments(MinecraftServer server, JsonElement value) {
        if (!value.isJsonObject()) return false;
        JsonObject enchantments = value.getAsJsonObject();
        String mode = readString(enchantments, "mode").toLowerCase(Locale.ROOT);
        if (!mode.isBlank() && !Set.of("none", "random", "random_from", "random-from", "fixed").contains(mode)) return false;
        if (!validOptionalBoolean(enchantments, "level_by_skill", "levelBySkill")
                || !validOptionalInt(enchantments, "min_level", "minLevel", 1, 255)
                || !validOptionalInt(enchantments, "max_level", "maxLevel", 1, 255)) return false;
        var registry = server.registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        for (String candidate : readStringList(enchantments, "candidates", "enchantments")) {
            Optional<ResourceLocation> id = parseMinecraftLocation(candidate);
            if (id.isEmpty() || registry.getOptional(id.get()).isEmpty()) return false;
        }
        JsonElement fixed = enchantments.get("fixed");
        if (fixed == null || fixed.isJsonNull()) {
            return !mode.equals("fixed") || !readStringList(enchantments, "candidates", "enchantments").isEmpty();
        }
        if (fixed.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : fixed.getAsJsonObject().entrySet()) {
                Optional<ResourceLocation> id = parseMinecraftLocation(entry.getKey());
                if (id.isEmpty() || registry.getOptional(id.get()).isEmpty() || !validInteger(entry.getValue(), 1, 255)) return false;
            }
            return true;
        }
        if (!fixed.isJsonArray()) return false;
        for (JsonElement element : fixed.getAsJsonArray()) {
            String idValue;
            if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
                idValue = element.getAsString();
            } else if (element.isJsonObject()) {
                JsonObject object = element.getAsJsonObject();
                idValue = readString(object, "id");
                if (idValue.isBlank()) idValue = readString(object, "enchantment");
                if (object.has("level") && !validInteger(object.get("level"), 1, 255)) return false;
            } else return false;
            Optional<ResourceLocation> id = parseMinecraftLocation(idValue);
            if (id.isEmpty() || registry.getOptional(id.get()).isEmpty()) return false;
        }
        return true;
    }

    private static boolean validOptionalInt(JsonObject object, String key, int min, int max) {
        return validOptionalInt(object, key, null, min, max);
    }

    private static boolean validOptionalInt(JsonObject object, String snakeKey, String camelKey, int min, int max) {
        JsonElement value = object.has(snakeKey) ? object.get(snakeKey)
                : camelKey != null && object.has(camelKey) ? object.get(camelKey) : null;
        if (value == null) return true;
        if (!validNumber(value)) return false;
        try {
            int parsed = value.getAsInt();
            return parsed >= min && parsed <= max && value.getAsDouble() == parsed;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static Optional<SkillTradeDefinition> parseDefinition(
            MinecraftServer server,
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
                readCost(server, location, context, entry),
                result.get(),
                readMaxUses(location, context, entry),
                readInt(entry, "xp", 0),
                (float) readDouble(entry, "price_multiplier", "priceMultiplier", 0.05D),
                readConditions(location, context, entry),
                readQualityScaling(location, context, entry),
                readRequestMetadata(server, location, context, entry),
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

    private static SkillTradeCost readCost(MinecraftServer server, ResourceLocation location, String context, JsonObject entry) {
        JsonObject cost = readObject(entry, "cost");
        if (cost == null) {
            return new SkillTradeCost(VillagerCurrencyResources.primaryItem(server), 1, SkillTradeCost.SkillDiscount.DISABLED);
        }

        DatapackDiagnostics.warnUnknownKeys(location, "skill trade cost", context, cost, COST_KEYS);
        Item item = resolveCurrencySentinel(server, readItem(cost, "item").orElse(Items.EMERALD));
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

    private static SkillTradeRequestMetadata readRequestMetadata(MinecraftServer server, ResourceLocation location, String context, JsonObject entry) {
        JsonObject request = readObject(entry, "request");
        if (request == null) {
            return SkillTradeRequestMetadata.NOT_TARGETABLE;
        }

        DatapackDiagnostics.warnUnknownKeys(location, "skill trade request", context, request, REQUEST_KEYS);
        return new SkillTradeRequestMetadata(
                readBoolean(request, "targetable", false),
                readInt(request, "display_priority", "displayPriority", 0),
                readReputationLevel(request, "min_reputation", "minReputation", VillagerReputationLevel.RESPECTED),
                readInt(request, "wait_days", "waitDays", 0),
                readInt(request, "cooldown_days", "cooldownDays", 0),
                readRequestCost(server, location, context, request)
        );
    }

    private static VillagerReputationLevel readReputationLevel(
            JsonObject entry,
            String snakeKey,
            String camelKey,
            VillagerReputationLevel fallback) {
        String value = readString(entry, snakeKey, camelKey);
        if (value.isBlank()) {
            return fallback;
        }
        try {
            return VillagerReputationLevel.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private static SpecialOrderCost readRequestCost(MinecraftServer server, ResourceLocation location, String context, JsonObject request) {
        JsonObject cost = readObject(request, "extra_cost");
        if (cost == null) {
            cost = readObject(request, "extraCost");
        }
        if (cost == null) {
            return SpecialOrderCost.EMPTY;
        }

        DatapackDiagnostics.warnUnknownKeys(location, "skill trade request extra_cost", context, cost, REQUEST_COST_KEYS);
        Item item = resolveCurrencySentinel(server, readItem(cost, "item").orElse(Items.EMERALD));
        return new SpecialOrderCost(item, readInt(cost, "count", 0));
    }

    private static Item resolveCurrencySentinel(MinecraftServer server, Item item) {
        return item == Items.EMERALD ? VillagerCurrencyResources.primaryItem(server) : item;
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
        return DatapackJsonReader.readObject(entry, key);
    }

    private static List<String> readStringList(JsonObject entry, String... keys) {
        return DatapackJsonReader.readStringList(entry, keys);
    }

    private static String readString(JsonObject entry, String... keys) {
        return DatapackJsonReader.readString(entry, keys);
    }

    private static int readInt(JsonObject entry, String key, int fallback) {
        return DatapackJsonReader.readInt(entry, key, fallback);
    }

    private static int readInt(JsonObject entry, String snakeKey, String camelKey, int fallback) {
        return DatapackJsonReader.readInt(entry, snakeKey, camelKey, fallback);
    }

    private static int readInt(JsonElement element, int fallback) {
        return DatapackJsonReader.readInt(element, fallback);
    }

    private static double readDouble(JsonObject entry, String key, double fallback) {
        return DatapackJsonReader.readDouble(entry, key, fallback);
    }

    private static double readDouble(JsonObject entry, String snakeKey, String camelKey, double fallback) {
        return DatapackJsonReader.readDouble(entry, snakeKey, camelKey, fallback);
    }

    private static double readDouble(JsonElement element, double fallback) {
        return DatapackJsonReader.readDouble(element, fallback);
    }

    private static boolean readBoolean(JsonObject entry, String key, boolean fallback) {
        return DatapackJsonReader.readBoolean(entry, key, fallback);
    }

    private static boolean readBoolean(JsonObject entry, String snakeKey, String camelKey, boolean fallback) {
        return DatapackJsonReader.readBoolean(entry, snakeKey, camelKey, fallback);
    }

    private static boolean readBoolean(JsonElement element, boolean fallback) {
        return DatapackJsonReader.readBoolean(element, fallback);
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
