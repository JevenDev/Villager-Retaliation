package com.jvn.villagerretaliation.interaction;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.reputation.VillagerReputationLevel;
import com.jvn.villagerretaliation.util.DatapackResourceLoader;
import com.jvn.villagerretaliation.util.VillagerEquipmentCondition;
import com.jvn.villagerretaliation.util.VillagerProfessionUtil;
import com.jvn.toucanlib.util.ToucanRandom;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class VillagerGiftResources {
    private static final String GIFT_ROOT = "gifts";

    private static volatile CachedGiftPool cachedGiftPool = CachedGiftPool.empty();
    private static final Map<GiftCandidateCacheKey, List<VillagerGiftPreferences.GiftCandidate>> GIFT_CANDIDATE_CACHE = new HashMap<>();

    private VillagerGiftResources() {
    }

    public static void warm(MinecraftServer server) {
        load(server);
    }

    public static void clearCache() {
        cachedGiftPool = CachedGiftPool.empty();
        synchronized (VillagerGiftResources.class) {
            GIFT_CANDIDATE_CACHE.clear();
        }
    }

    public static Optional<ResolvedGiftPreference> preference(
            ServerLevel level,
            VillagerProfession profession,
            ItemStack stack) {
        return preference(level, null, profession, stack);
    }

    public static Optional<ResolvedGiftPreference> preference(
            ServerLevel level,
            Villager villager,
            ItemStack stack) {
        return preference(level, villager, villager.getVillagerData().getProfession(), stack);
    }

    private static Optional<ResolvedGiftPreference> preference(
            ServerLevel level,
            Villager villager,
            VillagerProfession profession,
            ItemStack stack) {
        return GiftPreferenceResolver.resolve(load(level.getServer()).preferenceDefinitions(), villager, profession, stack);
    }

    public static List<GiftPreferenceDefinition> definitions(ServerLevel level, VillagerProfession profession) {
        return load(level.getServer()).preferenceDefinitions().stream()
                .filter(definition -> definition.appliesToProfession(profession))
                .filter(definition -> definition.equipmentCondition() == null || definition.equipmentCondition().isEmpty())
                .sorted(java.util.Comparator.comparing(definition -> definition.id().toString()))
                .toList();
    }

    public static List<VillagerGiftPreferences.GiftCandidate> giftCandidates(ServerLevel level, VillagerProfession profession) {
        return giftCandidates(level.getServer(), profession);
    }

    private static List<VillagerGiftPreferences.GiftCandidate> giftCandidates(MinecraftServer server, VillagerProfession profession) {
        GiftPool pool = load(server);
        GiftCandidateCacheKey cacheKey = new GiftCandidateCacheKey(profession);
        synchronized (VillagerGiftResources.class) {
            List<VillagerGiftPreferences.GiftCandidate> cached = GIFT_CANDIDATE_CACHE.get(cacheKey);
            if (cached != null) {
                return cached;
            }

            List<VillagerGiftPreferences.GiftCandidate> candidates = buildGiftCandidates(pool, profession);
            GIFT_CANDIDATE_CACHE.put(cacheKey, candidates);
            return candidates;
        }
    }

    private static List<VillagerGiftPreferences.GiftCandidate> buildGiftCandidates(GiftPool pool, VillagerProfession profession) {
        List<VillagerGiftPreferences.GiftCandidate> candidates = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (GiftPreferenceDefinition definition : pool.preferenceDefinitions()) {
            if (!definition.appliesToProfession(profession)
                    || definition.equipmentCondition() != null && !definition.equipmentCondition().isEmpty()) {
                continue;
            }
            for (GiftPreferenceDefinition.ItemMatcher matcher : definition.matchers()) {
                for (Item item : matcher.items()) {
                    String key = BuiltInRegistries.ITEM.getKey(item) + ":" + definition.id();
                    if (seen.add(key)) {
                        candidates.add(new VillagerGiftPreferences.GiftCandidate(
                                item,
                                VillagerGiftPreferences.GiftReaction.fromRating(definition.rating()),
                                definition.professionSpecific()));
                    }
                }
            }
        }
        return List.copyOf(candidates);
    }

    public static ItemStack highReputationReward(ServerLevel level, Villager villager, VillagerReputationLevel reputationLevel) {
        List<GiftRewardRule> matches = load(level.getServer()).rewardRules().stream()
                .filter(rule -> rule.matches(villager, reputationLevel))
                .toList();
        if (matches.isEmpty()) {
            return ItemStack.EMPTY;
        }

        boolean hasProfessionSpecificMatch = matches.stream().anyMatch(GiftRewardRule::professionSpecific);
        List<GiftRewardRule> candidates = matches.stream()
                .filter(rule -> !hasProfessionSpecificMatch || rule.professionSpecific())
                .toList();
        int totalWeight = candidates.stream().mapToInt(GiftRewardRule::weight).sum();
        int selected = villager.getRandom().nextInt(Math.max(1, totalWeight));
        for (GiftRewardRule candidate : candidates) {
            selected -= candidate.weight();
            if (selected < 0) {
                return candidate.createStack(villager);
            }
        }
        return candidates.getLast().createStack(villager);
    }

    private static GiftPool load(MinecraftServer server) {
        CachedGiftPool current = cachedGiftPool;
        if (current.server() == server) {
            return current.pool();
        }

        synchronized (VillagerGiftResources.class) {
            current = cachedGiftPool;
            if (current.server() == server) {
                return current.pool();
            }

            GiftPool loadedPool = read(server);
            GIFT_CANDIDATE_CACHE.clear();
            cachedGiftPool = new CachedGiftPool(server, loadedPool);
            return loadedPool;
        }
    }

    private static GiftPool read(MinecraftServer server) {
        Map<ResourceLocation, GiftPreferenceDefinition> preferenceDefinitions = new LinkedHashMap<>();
        Map<String, GiftRewardRule> rewardRules = new LinkedHashMap<>();
        DatapackResourceLoader.forEachJsonResource(
                server,
                GIFT_ROOT,
                location -> location.getNamespace().equals(VillagerRetaliation.MOD_ID),
                (location, resource) -> readFile(location, resource, preferenceDefinitions, rewardRules));
        List<GiftPreferenceDefinition> orderedDefinitions = preferenceDefinitions.values().stream()
                .sorted(java.util.Comparator.comparing(definition -> definition.id().toString()))
                .toList();
        return new GiftPool(orderedDefinitions, List.copyOf(rewardRules.values()));
    }

    private static void readFile(
            ResourceLocation location,
            Resource resource,
            Map<ResourceLocation, GiftPreferenceDefinition> preferenceDefinitions,
            Map<String, GiftRewardRule> rewardRules) {
        DatapackResourceLoader.readObject(location, "gift", resource).ifPresent(root -> {
            if (readBoolean(root, "replace", false)) {
                preferenceDefinitions.clear();
                rewardRules.clear();
            }
            readPreferenceDefinitions(location, root, preferenceDefinitions);
            readRewardRules(location, root, rewardRules);
        });
    }

    private static void readPreferenceDefinitions(
            ResourceLocation location,
            JsonObject root,
            Map<ResourceLocation, GiftPreferenceDefinition> definitions) {
        JsonArray entries = root.getAsJsonArray("preferences");
        if (entries == null) {
            return;
        }

        Set<VillagerProfession> inheritedProfessions = readProfessions(root);
        int index = 0;
        for (JsonElement element : entries) {
            if (!element.isJsonObject()) {
                index++;
                continue;
            }

            JsonObject entry = element.getAsJsonObject();
            ResourceLocation id = preferenceId(location, readString(entry, "id"), index);
            if (readBoolean(entry, "remove", false)) {
                definitions.remove(id);
                index++;
                continue;
            }

            Optional<VillagerGiftPreferences.GiftReaction> legacyReaction = readEnum(
                    entry,
                    "reaction",
                    VillagerGiftPreferences.GiftReaction.class);
            Integer rating = readOptionalInt(entry, "rating").orElseGet(
                    () -> legacyReaction.map(VillagerGiftPreferences.GiftReaction::legacyRating).orElse(null));
            List<GiftPreferenceDefinition.ItemMatcher> matchers = readItemMatchers(entry);
            if (rating == null || rating < -3 || rating > 3 || matchers.isEmpty()) {
                index++;
                continue;
            }

            VillagerGiftPreferences.GiftReaction reaction = VillagerGiftPreferences.GiftReaction.fromRating(rating);
            Set<VillagerProfession> professions = hasProfessionField(entry)
                    ? readProfessions(entry)
                    : inheritedProfessions;
            definitions.put(id, new GiftPreferenceDefinition(
                    id,
                    professions,
                    rating,
                    readInt(entry, "reputation_per_item", reaction.defaultPerItemReputation()),
                    readGiftResponseKey(entry),
                    readInt(entry, "priority", 0),
                    VillagerEquipmentCondition.read(entry),
                    readCategoryName(entry),
                    matchers));
            index++;
        }
    }

    private static void readRewardRules(ResourceLocation location, JsonObject root, Map<String, GiftRewardRule> rewardRules) {
        JsonArray entries = root.getAsJsonArray("rewards");
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
            String id = readString(entry, "id");
            if (readBoolean(entry, "remove", false)) {
                if (!id.isBlank()) {
                    rewardRules.remove(id);
                }
                index++;
                continue;
            }

            Item item = readItem(entry, "item").orElse(null);
            if (item == null || item == Items.AIR) {
                index++;
                continue;
            }

            int minCount = Math.max(1, readInt(entry, "min_count", 1));
            int maxCount = Math.max(minCount, readInt(entry, "max_count", minCount));
            String resolvedId = id.isBlank() ? fallbackLegacyId(location, "reward", index) : id;
            rewardRules.put(resolvedId, new GiftRewardRule(
                    resolvedId,
                    readProfessions(entry),
                    readEnumSet(entry, "reputation_levels", VillagerReputationLevel.class),
                    item,
                    minCount,
                    maxCount,
                    Math.max(1, readInt(entry, "weight", 10)),
                    VillagerEquipmentCondition.read(entry)));
            index++;
        }
    }

    private static List<GiftPreferenceDefinition.ItemMatcher> readItemMatchers(JsonObject entry) {
        List<GiftPreferenceDefinition.ItemMatcher> matchers = new ArrayList<>();
        for (String value : readStringList(entry, "item")) {
            parseItemMatcher(value).ifPresent(matchers::add);
        }
        for (String value : readStringList(entry, "items")) {
            parseItemMatcher(value).ifPresent(matchers::add);
        }
        for (String value : readStringList(entry, "tag")) {
            parseTagMatcher(value).ifPresent(matchers::add);
        }
        for (String value : readStringList(entry, "tags")) {
            parseTagMatcher(value).ifPresent(matchers::add);
        }
        return matchers.stream().distinct().toList();
    }

    private static Optional<GiftPreferenceDefinition.ItemMatcher> parseItemMatcher(String value) {
        if (value.startsWith("#")) {
            return parseTagMatcher(value.substring(1));
        }
        return parseResourceLocation(value, "minecraft").map(GiftPreferenceDefinition.ItemMatcher::item);
    }

    private static Optional<GiftPreferenceDefinition.ItemMatcher> parseTagMatcher(String value) {
        String normalized = value.startsWith("#") ? value.substring(1) : value;
        return parseResourceLocation(normalized, "minecraft").map(GiftPreferenceDefinition.ItemMatcher::tag);
    }

    private static Optional<Item> readItem(JsonObject entry, String key) {
        return parseResourceLocation(readString(entry, key), "minecraft")
                .flatMap(location -> BuiltInRegistries.ITEM.getOptional(location))
                .filter(item -> item != Items.AIR);
    }

    private static Optional<ResourceLocation> parseResourceLocation(String value, String defaultNamespace) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String normalized = value.contains(":") ? value : defaultNamespace + ":" + value;
        return Optional.ofNullable(ResourceLocation.tryParse(normalized));
    }

    private static ResourceLocation preferenceId(ResourceLocation source, String value, int index) {
        if (!value.isBlank()) {
            ResourceLocation parsed = ResourceLocation.tryParse(
                    value.contains(":") ? value : source.getNamespace() + ":" + value);
            if (parsed != null) {
                return parsed;
            }
        }
        String path = source.getPath();
        if (path.startsWith(GIFT_ROOT + "/")) {
            path = path.substring(GIFT_ROOT.length() + 1);
        }
        if (path.endsWith(".json")) {
            path = path.substring(0, path.length() - 5);
        }
        return ResourceLocation.fromNamespaceAndPath(source.getNamespace(), path + "/preference_" + index);
    }

    private static GiftCategoryName readCategoryName(JsonObject entry) {
        JsonElement element = entry.get("name");
        if (element == null) {
            return GiftCategoryName.EMPTY;
        }
        if (element.isJsonPrimitive()) {
            return new GiftCategoryName("", element.getAsString());
        }
        if (!element.isJsonObject()) {
            return GiftCategoryName.EMPTY;
        }
        JsonObject name = element.getAsJsonObject();
        return new GiftCategoryName(readString(name, "translate"), readString(name, "text"));
    }

    private static boolean hasProfessionField(JsonObject entry) {
        return entry.has("profession") || entry.has("professions");
    }

    private static Set<VillagerProfession> readProfessions(JsonObject entry) {
        Set<VillagerProfession> professions = new HashSet<>();
        for (String value : readStringList(entry, "profession")) {
            VillagerProfessionUtil.parse(value).ifPresent(professions::add);
        }
        for (String value : readStringList(entry, "professions")) {
            VillagerProfessionUtil.parse(value).ifPresent(professions::add);
        }
        return Set.copyOf(professions);
    }

    private static <E extends Enum<E>> Set<E> readEnumSet(JsonObject entry, String key, Class<E> enumClass) {
        Set<E> values = EnumSet.noneOf(enumClass);
        for (String value : readStringList(entry, key)) {
            readEnum(value, enumClass).ifPresent(values::add);
        }
        return Set.copyOf(values);
    }

    private static <E extends Enum<E>> Optional<E> readEnum(JsonObject entry, String key, Class<E> enumClass) {
        return readEnum(readString(entry, key), enumClass);
    }

    private static <E extends Enum<E>> Optional<E> readEnum(String value, Class<E> enumClass) {
        if (value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Enum.valueOf(enumClass, value.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private static List<String> readStringList(JsonObject entry, String key) {
        JsonElement element = entry.get(key);
        if (element == null) {
            return List.of();
        }
        if (element.isJsonPrimitive()) {
            String value = element.getAsString().trim();
            return value.isBlank() ? List.of() : List.of(value);
        }
        if (!element.isJsonArray()) {
            return List.of();
        }

        List<String> values = new ArrayList<>();
        for (JsonElement child : element.getAsJsonArray()) {
            if (!child.isJsonPrimitive()) {
                continue;
            }
            String value = child.getAsString().trim();
            if (!value.isBlank()) {
                values.add(value);
            }
        }
        return values;
    }

    private static String readString(JsonObject entry, String key) {
        JsonElement element = entry.get(key);
        return element == null || !element.isJsonPrimitive() ? "" : element.getAsString().trim();
    }

    private static String readGiftResponseKey(JsonObject entry) {
        String responseKey = readString(entry, "response_key");
        if (!responseKey.isBlank()) {
            return responseKey;
        }
        responseKey = readString(entry, "dialogue_key");
        if (!responseKey.isBlank()) {
            return responseKey;
        }
        return readString(entry, "gift_response_key");
    }

    private static Optional<Integer> readOptionalInt(JsonObject entry, String key) {
        JsonElement element = entry.get(key);
        if (element == null || !element.isJsonPrimitive()) {
            return Optional.empty();
        }
        try {
            return Optional.of(element.getAsInt());
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private static int readInt(JsonObject entry, String key, int fallback) {
        return readOptionalInt(entry, key).orElse(fallback);
    }

    private static boolean readBoolean(JsonObject entry, String key, boolean fallback) {
        JsonElement element = entry.get(key);
        return element == null || !element.isJsonPrimitive() ? fallback : element.getAsBoolean();
    }

    private static String fallbackLegacyId(ResourceLocation location, String group, int index) {
        return location.getPath().replace('/', '_').replace(".json", "") + "_" + group + "_" + index;
    }

    private record GiftPool(List<GiftPreferenceDefinition> preferenceDefinitions, List<GiftRewardRule> rewardRules) {
        private static GiftPool empty() {
            return new GiftPool(List.of(), List.of());
        }
    }

    private record GiftCandidateCacheKey(VillagerProfession profession) {
    }

    private record CachedGiftPool(MinecraftServer server, GiftPool pool) {
        private static CachedGiftPool empty() {
            return new CachedGiftPool(null, GiftPool.empty());
        }
    }

    private record GiftRewardRule(
            String id,
            Set<VillagerProfession> professions,
            Set<VillagerReputationLevel> reputationLevels,
            Item item,
            int minCount,
            int maxCount,
            int weight,
            VillagerEquipmentCondition equipmentCondition) {
        private boolean matches(Villager villager, VillagerReputationLevel reputationLevel) {
            VillagerProfession profession = villager.getVillagerData().getProfession();
            if (!this.professions.isEmpty() && !this.professions.contains(profession)) {
                return false;
            }
            if (!this.equipmentCondition.matches(villager)) {
                return false;
            }
            return this.reputationLevels.isEmpty() || this.reputationLevels.contains(reputationLevel);
        }

        private boolean professionSpecific() {
            return !this.professions.isEmpty();
        }

        private ItemStack createStack(Villager villager) {
            return new ItemStack(this.item, ToucanRandom.betweenInclusive(villager.getRandom(), this.minCount, this.maxCount));
        }
    }
}
