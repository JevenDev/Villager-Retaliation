package com.jvn.villagerretaliation.interaction;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.reputation.VillagerReputationLevel;
import com.jvn.toucanlib.util.ToucanRandom;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.tags.TagKey;
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

    public static Optional<VillagerGiftPreferences.GiftPreference> preference(
            ServerLevel level,
            VillagerProfession profession,
            ItemStack stack) {
        List<GiftPreferenceRule> matches = load(level.getServer()).preferenceRules().stream()
                .filter(rule -> rule.matches(profession, stack))
                .sorted(GiftPreferenceRule::compareTo)
                .toList();
        if (matches.isEmpty()) {
            return Optional.empty();
        }

        boolean hasProfessionSpecificMatch = matches.stream().anyMatch(GiftPreferenceRule::professionSpecific);
        GiftPreferenceRule selected = matches.stream()
                .filter(rule -> !hasProfessionSpecificMatch || rule.professionSpecific())
                .findFirst()
                .orElse(matches.getFirst());
        return Optional.of(new VillagerGiftPreferences.GiftPreference(
                selected.reaction(),
                selected.professionSpecific(),
                0,
                selected.perItemReputation(),
                selected.responseKey()
        ));
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
        for (GiftPreferenceRule rule : pool.preferenceRules()) {
            if (!rule.appliesToProfession(profession)) {
                continue;
            }
            for (Item item : rule.items()) {
                String key = BuiltInRegistries.ITEM.getKey(item) + ":" + rule.reaction() + ":" + rule.professionSpecific();
                if (seen.add(key)) {
                    candidates.add(new VillagerGiftPreferences.GiftCandidate(item, rule.reaction(), rule.professionSpecific()));
                }
            }
        }
        return List.copyOf(candidates);
    }

    public static ItemStack highReputationReward(ServerLevel level, Villager villager, VillagerReputationLevel reputationLevel) {
        List<GiftRewardRule> matches = load(level.getServer()).rewardRules().stream()
                .filter(rule -> rule.matches(villager.getVillagerData().getProfession(), reputationLevel))
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
        List<GiftPreferenceRule> preferenceRules = new ArrayList<>();
        List<GiftRewardRule> rewardRules = new ArrayList<>();
        server.getResourceManager()
                .listResources(GIFT_ROOT, location -> location.getNamespace().equals(VillagerRetaliation.MOD_ID)
                        && location.getPath().endsWith(".json"))
                .entrySet()
                .stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().toString()))
                .forEach(entry -> readFile(entry.getKey(), entry.getValue(), preferenceRules, rewardRules));
        return new GiftPool(List.copyOf(preferenceRules), List.copyOf(rewardRules));
    }

    private static void readFile(
            ResourceLocation location,
            Resource resource,
            List<GiftPreferenceRule> preferenceRules,
            List<GiftRewardRule> rewardRules) {
        try (Reader reader = resource.openAsReader()) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            readPreferenceRules(location, root, preferenceRules);
            readRewardRules(location, root, rewardRules);
        } catch (IOException | IllegalStateException exception) {
            // Invalid gift resources are ignored so one bad datapack file cannot break every gift.
        }
    }

    private static void readPreferenceRules(
            ResourceLocation location,
            JsonObject root,
            List<GiftPreferenceRule> preferenceRules) {
        JsonArray entries = root.getAsJsonArray("preferences");
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
            Optional<VillagerGiftPreferences.GiftReaction> reaction = readEnum(
                    entry,
                    "reaction",
                    VillagerGiftPreferences.GiftReaction.class
            );
            List<ItemSelector> selectors = readItemSelectors(entry);
            if (reaction.isEmpty() || selectors.isEmpty()) {
                index++;
                continue;
            }

            Set<VillagerProfession> professions = readProfessions(entry);
            int perItemReputation = readInt(entry, "reputation_per_item", reaction.get().defaultPerItemReputation());
            int priority = readInt(entry, "priority", 0);
            String responseKey = readGiftResponseKey(entry);
            preferenceRules.add(new GiftPreferenceRule(
                    fallbackId(location, "preference", index),
                    professions,
                    reaction.get(),
                    perItemReputation,
                    responseKey,
                    priority,
                    index,
                    selectors
            ));
            index++;
        }
    }

    private static void readRewardRules(ResourceLocation location, JsonObject root, List<GiftRewardRule> rewardRules) {
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
            Item item = readItem(entry, "item").orElse(null);
            if (item == null || item == Items.AIR) {
                index++;
                continue;
            }

            int minCount = Math.max(1, readInt(entry, "min_count", 1));
            int maxCount = Math.max(minCount, readInt(entry, "max_count", minCount));
            rewardRules.add(new GiftRewardRule(
                    fallbackId(location, "reward", index),
                    readProfessions(entry),
                    readEnumSet(entry, "reputation_levels", VillagerReputationLevel.class),
                    item,
                    minCount,
                    maxCount,
                    Math.max(1, readInt(entry, "weight", 10)),
                    index
            ));
            index++;
        }
    }

    private static List<ItemSelector> readItemSelectors(JsonObject entry) {
        List<ItemSelector> selectors = new ArrayList<>();
        for (String value : readStringList(entry, "item")) {
            parseItemSelector(value).ifPresent(selectors::add);
        }
        for (String value : readStringList(entry, "items")) {
            parseItemSelector(value).ifPresent(selectors::add);
        }
        for (String value : readStringList(entry, "tag")) {
            parseTagSelector(value).ifPresent(selectors::add);
        }
        for (String value : readStringList(entry, "tags")) {
            parseTagSelector(value).ifPresent(selectors::add);
        }
        return List.copyOf(selectors);
    }

    private static Optional<ItemSelector> parseItemSelector(String value) {
        if (value.startsWith("#")) {
            return parseTagSelector(value.substring(1));
        }
        return readItem(value).map(ItemSelector::item);
    }

    private static Optional<ItemSelector> parseTagSelector(String value) {
        String normalized = value.startsWith("#") ? value.substring(1) : value;
        return parseResourceLocation(normalized)
                .map(location -> ItemSelector.tag(TagKey.create(Registries.ITEM, location)));
    }

    private static Optional<Item> readItem(JsonObject entry, String key) {
        return readItem(readString(entry, key));
    }

    private static Optional<Item> readItem(String value) {
        return parseResourceLocation(value)
                .flatMap(location -> BuiltInRegistries.ITEM.getOptional(location))
                .filter(item -> item != Items.AIR);
    }

    private static Optional<ResourceLocation> parseResourceLocation(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String normalized = value.contains(":") ? value : "minecraft:" + value;
        return Optional.ofNullable(ResourceLocation.tryParse(normalized));
    }

    private static Set<VillagerProfession> readProfessions(JsonObject entry) {
        Set<VillagerProfession> professions = new HashSet<>();
        for (String value : readStringList(entry, "professions")) {
            parseProfession(value).ifPresent(professions::add);
        }
        return Set.copyOf(professions);
    }

    private static Optional<VillagerProfession> parseProfession(String value) {
        return switch (value.toLowerCase(Locale.ROOT).replace("minecraft:", "")) {
            case "armorer" -> Optional.of(VillagerProfession.ARMORER);
            case "butcher" -> Optional.of(VillagerProfession.BUTCHER);
            case "cartographer" -> Optional.of(VillagerProfession.CARTOGRAPHER);
            case "cleric" -> Optional.of(VillagerProfession.CLERIC);
            case "farmer" -> Optional.of(VillagerProfession.FARMER);
            case "fisherman" -> Optional.of(VillagerProfession.FISHERMAN);
            case "fletcher" -> Optional.of(VillagerProfession.FLETCHER);
            case "leatherworker" -> Optional.of(VillagerProfession.LEATHERWORKER);
            case "librarian" -> Optional.of(VillagerProfession.LIBRARIAN);
            case "mason" -> Optional.of(VillagerProfession.MASON);
            case "nitwit" -> Optional.of(VillagerProfession.NITWIT);
            case "shepherd" -> Optional.of(VillagerProfession.SHEPHERD);
            case "toolsmith" -> Optional.of(VillagerProfession.TOOLSMITH);
            case "weaponsmith" -> Optional.of(VillagerProfession.WEAPONSMITH);
            case "none", "unemployed" -> Optional.of(VillagerProfession.NONE);
            default -> Optional.empty();
        };
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

    private static int readInt(JsonObject entry, String key, int fallback) {
        JsonElement element = entry.get(key);
        return element == null || !element.isJsonPrimitive() ? fallback : element.getAsInt();
    }

    private static String fallbackId(ResourceLocation location, String group, int index) {
        return location.getPath().replace('/', '_').replace(".json", "") + "_" + group + "_" + index;
    }

    private record GiftPool(List<GiftPreferenceRule> preferenceRules, List<GiftRewardRule> rewardRules) {
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

    private record GiftPreferenceRule(
            String id,
            Set<VillagerProfession> professions,
            VillagerGiftPreferences.GiftReaction reaction,
            int perItemReputation,
            String responseKey,
            int priority,
            int order,
            List<ItemSelector> selectors) implements Comparable<GiftPreferenceRule> {
        private boolean matches(VillagerProfession profession, ItemStack stack) {
            return appliesToProfession(profession) && this.selectors.stream().anyMatch(selector -> selector.matches(stack));
        }

        private boolean appliesToProfession(VillagerProfession profession) {
            return this.professions.isEmpty() || this.professions.contains(profession);
        }

        private boolean professionSpecific() {
            return !this.professions.isEmpty();
        }

        private List<Item> items() {
            return this.selectors.stream().flatMap(selector -> selector.items().stream()).toList();
        }

        @Override
        public int compareTo(GiftPreferenceRule other) {
            int priorityCompare = Integer.compare(other.priority, this.priority);
            return priorityCompare != 0 ? priorityCompare : Integer.compare(this.order, other.order);
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
            int order) {
        private boolean matches(VillagerProfession profession, VillagerReputationLevel reputationLevel) {
            if (!this.professions.isEmpty() && !this.professions.contains(profession)) {
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

    private record ItemSelector(Item item, TagKey<Item> tag) {
        private static ItemSelector item(Item item) {
            return new ItemSelector(item, null);
        }

        private static ItemSelector tag(TagKey<Item> tag) {
            return new ItemSelector(null, tag);
        }

        private boolean matches(ItemStack stack) {
            if (this.item != null) {
                return stack.is(this.item);
            }
            return this.tag != null && stack.is(this.tag);
        }

        private List<Item> items() {
            if (this.item != null) {
                return List.of(this.item);
            }
            if (this.tag == null) {
                return List.of();
            }
            List<Item> items = new ArrayList<>();
            for (Item candidate : BuiltInRegistries.ITEM) {
                if (candidate != Items.AIR && new ItemStack(candidate).is(this.tag)) {
                    items.add(candidate);
                }
            }
            return items;
        }
    }
}
