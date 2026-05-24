package com.jvn.villagerretaliation.combat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.util.VillagerEquipmentCondition;
import com.jvn.villagerretaliation.util.VillagerProfessionUtil;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class VillagerPacifyPaymentResources {
    private static final String PACIFICATION_ROOT = "pacification";
    private static final int MIN_PAYMENT_COUNT = 1;
    private static final int MAX_PAYMENT_COUNT = 64;

    private static volatile CachedPaymentRules cachedPaymentRules = CachedPaymentRules.empty();

    private VillagerPacifyPaymentResources() {
    }

    public static void warm(MinecraftServer server) {
        load(server);
    }

    public static void clearCache() {
        cachedPaymentRules = CachedPaymentRules.empty();
    }

    public static boolean isEligiblePayment(AbstractVillager villager, ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        MinecraftServer server = villager.level().getServer();
        if (server == null) {
            return false;
        }
        return load(server).paymentRules().stream()
                .anyMatch(rule -> rule.matches(villager, stack));
    }

    public static Optional<PacifyPaymentOffer> offerFor(AbstractVillager villager, ItemStack stack) {
        if (stack.isEmpty()) {
            return Optional.empty();
        }
        MinecraftServer server = villager.level().getServer();
        if (server == null) {
            return Optional.empty();
        }

        List<PaymentRule> matches = load(server).paymentRules().stream()
                .filter(rule -> rule.matches(villager, stack))
                .sorted(PaymentRule::compareTo)
                .toList();
        if (matches.isEmpty()) {
            return Optional.empty();
        }

        boolean hasProfessionSpecificMatch = matches.stream().anyMatch(PaymentRule::professionSpecific);
        PaymentRule selected = matches.stream()
                .filter(rule -> !hasProfessionSpecificMatch || rule.professionSpecific())
                .findFirst()
                .orElse(matches.getFirst());
        return Optional.of(selected.offer(stack, villager.getRandom()));
    }

    private static PaymentRules load(MinecraftServer server) {
        CachedPaymentRules current = cachedPaymentRules;
        if (current.server() == server) {
            return current.rules();
        }

        synchronized (VillagerPacifyPaymentResources.class) {
            current = cachedPaymentRules;
            if (current.server() == server) {
                return current.rules();
            }

            PaymentRules loadedRules = read(server);
            cachedPaymentRules = new CachedPaymentRules(server, loadedRules);
            return loadedRules;
        }
    }

    private static PaymentRules read(MinecraftServer server) {
        List<PaymentRule> paymentRules = new ArrayList<>();
        server.getResourceManager()
                .listResources(PACIFICATION_ROOT, location -> location.getNamespace().equals(VillagerRetaliation.MOD_ID)
                        && location.getPath().endsWith(".json"))
                .entrySet()
                .stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().toString()))
                .forEach(entry -> readFile(entry.getKey(), entry.getValue(), paymentRules));
        return new PaymentRules(List.copyOf(paymentRules));
    }

    private static void readFile(ResourceLocation location, Resource resource, List<PaymentRule> paymentRules) {
        try (Reader reader = resource.openAsReader()) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            readPaymentRules(location, root, paymentRules);
        } catch (IOException | IllegalStateException | JsonParseException exception) {
            // Invalid payment resources are ignored so one datapack file cannot break pacification.
        }
    }

    private static void readPaymentRules(ResourceLocation location, JsonObject root, List<PaymentRule> paymentRules) {
        JsonArray entries = root.getAsJsonArray("payments");
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
            List<ItemSelector> selectors = readItemSelectors(entry);
            if (selectors.isEmpty()) {
                index++;
                continue;
            }

            int count = readInt(entry, "count", -1);
            int minCount = count > 0 ? count : readInt(entry, "min_count", 1);
            int maxCount = count > 0 ? count : readInt(entry, "max_count", minCount);
            minCount = clampPaymentCount(minCount);
            maxCount = clampPaymentCount(Math.max(minCount, maxCount));
            paymentRules.add(new PaymentRule(
                    fallbackId(location, "payment", index),
                    readProfessions(entry),
                    selectors,
                    minCount,
                    maxCount,
                    readString(entry, "name"),
                    readString(entry, "plural_name"),
                    readInt(entry, "priority", 0),
                    VillagerEquipmentCondition.read(entry),
                    index
            ));
            index++;
        }
    }

    private static int clampPaymentCount(int count) {
        return Math.max(MIN_PAYMENT_COUNT, Math.min(MAX_PAYMENT_COUNT, count));
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
            VillagerProfessionUtil.parse(value).ifPresent(professions::add);
        }
        return Set.copyOf(professions);
    }

    private static VillagerProfession professionOf(AbstractVillager villager) {
        return villager instanceof Villager typedVillager
                ? typedVillager.getVillagerData().getProfession()
                : VillagerProfession.NONE;
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

    private static int readInt(JsonObject entry, String key, int fallback) {
        JsonElement element = entry.get(key);
        if (element == null || !element.isJsonPrimitive()) {
            return fallback;
        }

        try {
            return element.getAsInt();
        } catch (NumberFormatException | UnsupportedOperationException ignored) {
            return fallback;
        }
    }

    private static String fallbackId(ResourceLocation location, String group, int index) {
        return location.getPath().replace('/', '_').replace(".json", "") + "_" + group + "_" + index;
    }

    private record PaymentRules(List<PaymentRule> paymentRules) {
        private static PaymentRules empty() {
            return new PaymentRules(List.of());
        }
    }

    private record CachedPaymentRules(MinecraftServer server, PaymentRules rules) {
        private static CachedPaymentRules empty() {
            return new CachedPaymentRules(null, PaymentRules.empty());
        }
    }

    private record PaymentRule(
            String id,
            Set<VillagerProfession> professions,
            List<ItemSelector> selectors,
            int minCount,
            int maxCount,
            String itemName,
            String pluralItemName,
            int priority,
            VillagerEquipmentCondition equipmentCondition,
            int order) implements Comparable<PaymentRule> {
        private boolean matches(AbstractVillager villager, ItemStack stack) {
            return appliesToProfession(professionOf(villager))
                    && this.equipmentCondition.matches(villager)
                    && this.selectors.stream().anyMatch(selector -> selector.matches(stack));
        }

        private boolean appliesToProfession(VillagerProfession profession) {
            return this.professions.isEmpty() || this.professions.contains(profession);
        }

        private boolean professionSpecific() {
            return !this.professions.isEmpty();
        }

        private PacifyPaymentOffer offer(ItemStack stack, RandomSource random) {
            int count = this.minCount == this.maxCount
                    ? this.minCount
                    : this.minCount + random.nextInt(this.maxCount - this.minCount + 1);
            String fallbackName = stack.getHoverName().getString();
            String name = this.itemName.isBlank() ? fallbackName : this.itemName;
            String pluralName = this.pluralItemName.isBlank() ? name : this.pluralItemName;
            return new PacifyPaymentOffer(stack, count, name, pluralName);
        }

        @Override
        public int compareTo(PaymentRule other) {
            int priorityCompare = Integer.compare(other.priority, this.priority);
            return priorityCompare != 0 ? priorityCompare : Integer.compare(this.order, other.order);
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
    }
}
