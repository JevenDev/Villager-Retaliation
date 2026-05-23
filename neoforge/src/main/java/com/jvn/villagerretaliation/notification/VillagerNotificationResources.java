package com.jvn.villagerretaliation.notification;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.network.VillagerReputationNoticeKind;
import com.jvn.villagerretaliation.network.VillagerWorldTextIndicatorKind;
import com.jvn.villagerretaliation.reputation.VillagerReputationLevel;
import com.jvn.villagerretaliation.util.VillagerLocale;
import com.jvn.villagerretaliation.util.VillagerPlayerItemCondition;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.npc.VillagerProfession;

public final class VillagerNotificationResources {
    private static final String NOTIFICATION_ROOT = "notifications/";
    private static final int DEFAULT_COLOR = ResolvedVillagerNotification.DEFAULT_COLOR;
    private static final Map<String, Integer> NAMED_COLORS = namedColors();

    private static volatile CachedNotificationPools cachedNotificationPools = CachedNotificationPools.empty();

    private VillagerNotificationResources() {
    }

    public static void warm(MinecraftServer server) {
        load(server, VillagerLocale.DEFAULT_LOCALE);
    }

    public static void clearCache() {
        cachedNotificationPools = CachedNotificationPools.empty();
    }

    public static Optional<ResolvedVillagerNotification> select(
            VillagerNotificationContext context,
            String trigger,
            Map<String, String> replacements) {
        List<VillagerNotificationDefinition> candidates = load(context.level().getServer(), context.locale()).definitions().stream()
                .filter(definition -> definition.matches(context, trigger))
                .filter(definition -> definition.chance() >= 1.0D
                        || context.random().nextDouble() < Math.max(0.0D, definition.chance()))
                .toList();
        return select(candidates, context.random())
                .map(definition -> resolve(definition, mergedReplacements(context, definition, replacements)));
    }

    public static Optional<ResolvedVillagerNotification> select(
            VillagerNotificationContext context,
            String trigger) {
        return select(context, trigger, Map.of());
    }

    private static Optional<VillagerNotificationDefinition> select(
            List<VillagerNotificationDefinition> candidates,
            RandomSource random) {
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        int totalWeight = candidates.stream().mapToInt(VillagerNotificationDefinition::weight).sum();
        int selected = random.nextInt(Math.max(1, totalWeight));
        for (VillagerNotificationDefinition candidate : candidates) {
            selected -= candidate.weight();
            if (selected < 0) {
                return Optional.of(candidate);
            }
        }
        return Optional.of(candidates.getLast());
    }

    private static ResolvedVillagerNotification resolve(
            VillagerNotificationDefinition definition,
            Map<String, String> replacements) {
        return new ResolvedVillagerNotification(
                resolveTemplate(definition.text(), replacements),
                definition.textColor(),
                definition.chatColor(),
                definition.noticeKind(),
                definition.worldTextKind()
        );
    }

    private static NotificationPool load(MinecraftServer server, String locale) {
        String normalizedLocale = VillagerLocale.normalize(locale);
        CachedNotificationPools current = cachedNotificationPools;
        if (current.server() == server) {
            NotificationPool cachedPool = current.poolsByLocale().get(normalizedLocale);
            if (cachedPool != null) {
                return cachedPool;
            }
        }

        synchronized (VillagerNotificationResources.class) {
            current = cachedNotificationPools;
            Map<String, NotificationPool> poolsByLocale = current.server() == server
                    ? new HashMap<>(current.poolsByLocale())
                    : new HashMap<>();
            NotificationPool cachedPool = poolsByLocale.get(normalizedLocale);
            if (cachedPool != null) {
                return cachedPool;
            }

            NotificationPool loadedPool = read(server, normalizedLocale);
            poolsByLocale.put(normalizedLocale, loadedPool);
            cachedNotificationPools = new CachedNotificationPools(server, Map.copyOf(poolsByLocale));
            return loadedPool;
        }
    }

    private static NotificationPool read(MinecraftServer server, String locale) {
        Map<String, VillagerNotificationDefinition> definitions = new LinkedHashMap<>();
        readLocale(server, VillagerLocale.DEFAULT_LOCALE, definitions);
        if (!VillagerLocale.DEFAULT_LOCALE.equals(locale)) {
            readLocale(server, locale, definitions);
        }
        return new NotificationPool(List.copyOf(definitions.values()));
    }

    private static void readLocale(
            MinecraftServer server,
            String locale,
            Map<String, VillagerNotificationDefinition> definitions) {
        String root = NOTIFICATION_ROOT + locale;
        server.getResourceManager()
                .listResources(root, location -> location.getNamespace().equals(VillagerRetaliation.MOD_ID)
                        && location.getPath().endsWith(".json"))
                .entrySet()
                .stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().toString()))
                .forEach(entry -> readFile(entry.getKey(), entry.getValue(), definitions));
    }

    private static void readFile(
            ResourceLocation location,
            Resource resource,
            Map<String, VillagerNotificationDefinition> definitions) {
        try (Reader reader = resource.openAsReader()) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            JsonArray entries = root.getAsJsonArray("notifications");
            if (entries == null) {
                return;
            }

            int index = 0;
            for (JsonElement element : entries) {
                if (element.isJsonObject()) {
                    readDefinition(location, element.getAsJsonObject(), index)
                            .ifPresent(definition -> definitions.put(definition.id(), definition));
                }
                index++;
            }
        } catch (IOException | IllegalStateException exception) {
            // Invalid notification resources are ignored so one bad datapack file cannot break every notice.
        }
    }

    private static Optional<VillagerNotificationDefinition> readDefinition(
            ResourceLocation location,
            JsonObject entry,
            int index) {
        String trigger = readString(entry, "trigger");
        String text = readString(entry, "text");
        if (trigger.isBlank() || text.isBlank()) {
            return Optional.empty();
        }

        String id = readString(entry, "id");
        VillagerReputationNoticeKind noticeKind = readEnum(entry, "kind", VillagerReputationNoticeKind.class)
                .orElse(VillagerReputationNoticeKind.DEFAULT);
        VillagerWorldTextIndicatorKind worldTextKind = readEnum(entry, "world_text_kind", VillagerWorldTextIndicatorKind.class)
                .orElse(readEnum(entry, "style", VillagerWorldTextIndicatorKind.class)
                        .orElse(VillagerWorldTextIndicatorKind.DIALOGUE));
        int textColor = readColor(entry, "color")
                .or(() -> readColor(entry, "text_color"))
                .orElse(DEFAULT_COLOR);
        int chatColor = readColor(entry, "chat_color").orElse(textColor);
        int weight = Math.max(1, readInt(entry, "weight", 10));
        double chance = Math.max(0.0D, Math.min(1.0D, readDouble(entry, "chance", 1.0D)));

        return Optional.of(new VillagerNotificationDefinition(
                id.isBlank() ? fallbackId(location, index) : id,
                trigger,
                text,
                textColor,
                chatColor,
                noticeKind,
                worldTextKind,
                readBoolean(entry, "show_for_adults", true),
                readBoolean(entry, "show_for_babies", true),
                readProfessions(entry),
                readEnumSet(entry, "reputation_levels", VillagerReputationLevel.class),
                VillagerPlayerItemCondition.read(entry),
                readOptionalInt(entry, "min_reputation").orElse(null),
                readOptionalInt(entry, "max_reputation").orElse(null),
                weight,
                chance
        ));
    }

    private static Map<String, String> mergedReplacements(
            VillagerNotificationContext context,
            VillagerNotificationDefinition definition,
            Map<String, String> replacements) {
        Map<String, String> merged = new HashMap<>(definition.playerItemCondition().replacements(context.player()));
        merged.putAll(replacements);
        return merged;
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

    private static Optional<Integer> readColor(JsonObject entry, String key) {
        String value = readString(entry, key).toLowerCase(Locale.ROOT);
        if (value.isBlank()) {
            return Optional.empty();
        }
        Integer namedColor = NAMED_COLORS.get(value);
        if (namedColor != null) {
            return Optional.of(namedColor);
        }

        String hex = value;
        if (hex.startsWith("#")) {
            hex = hex.substring(1);
        } else if (hex.startsWith("0x")) {
            hex = hex.substring(2);
        }
        if (hex.length() == 6) {
            hex = "ff" + hex;
        }
        if (hex.length() != 8) {
            return Optional.empty();
        }
        try {
            return Optional.of((int) Long.parseLong(hex, 16));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    private static Map<String, Integer> namedColors() {
        Map<String, Integer> colors = new HashMap<>();
        colors.put("white", 0xFFFFFFFF);
        colors.put("gray", 0xFFAAAAAA);
        colors.put("grey", 0xFFAAAAAA);
        colors.put("dark_gray", 0xFF555555);
        colors.put("black", 0xFF000000);
        colors.put("red", 0xFFFF5555);
        colors.put("dark_red", 0xFFAA0000);
        colors.put("green", 0xFF55FF55);
        colors.put("dark_green", 0xFF00AA00);
        colors.put("blue", 0xFF5555FF);
        colors.put("aqua", 0xFF55FFFF);
        colors.put("yellow", 0xFFFFFF55);
        colors.put("gold", 0xFFFFAA00);
        colors.put("purple", 0xFFFF55FF);
        colors.put("light_purple", 0xFFFF55FF);
        return colors;
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

    private static Optional<Integer> readOptionalInt(JsonObject entry, String key) {
        JsonElement element = entry.get(key);
        return element == null || !element.isJsonPrimitive() ? Optional.empty() : Optional.of(element.getAsInt());
    }

    private static String readString(JsonObject entry, String key) {
        JsonElement element = entry.get(key);
        return element == null || !element.isJsonPrimitive() ? "" : element.getAsString().trim();
    }

    private static int readInt(JsonObject entry, String key, int fallback) {
        JsonElement element = entry.get(key);
        return element == null || !element.isJsonPrimitive() ? fallback : element.getAsInt();
    }

    private static double readDouble(JsonObject entry, String key, double fallback) {
        JsonElement element = entry.get(key);
        return element == null || !element.isJsonPrimitive() ? fallback : element.getAsDouble();
    }

    private static boolean readBoolean(JsonObject entry, String key, boolean fallback) {
        JsonElement element = entry.get(key);
        return element == null || !element.isJsonPrimitive() ? fallback : element.getAsBoolean();
    }

    private static String fallbackId(ResourceLocation location, int index) {
        return stablePath(location).replace('/', '_').replace(".json", "") + "_notification_" + index;
    }

    private static String stablePath(ResourceLocation location) {
        String path = location.getPath();
        if (!path.startsWith(NOTIFICATION_ROOT)) {
            return path;
        }
        String remainder = path.substring(NOTIFICATION_ROOT.length());
        int slash = remainder.indexOf('/');
        return slash < 0 ? remainder : remainder.substring(slash + 1);
    }

    private static String resolveTemplate(String text, Map<String, String> replacements) {
        String resolved = text;
        Map<String, String> safeReplacements = new HashMap<>(replacements);
        for (Map.Entry<String, String> entry : safeReplacements.entrySet()) {
            resolved = resolved.replace("{" + entry.getKey() + "}", entry.getValue() == null ? "" : entry.getValue());
        }
        return resolved;
    }

    private record NotificationPool(List<VillagerNotificationDefinition> definitions) {
        private static NotificationPool empty() {
            return new NotificationPool(List.of());
        }
    }

    private record CachedNotificationPools(MinecraftServer server, Map<String, NotificationPool> poolsByLocale) {
        private static CachedNotificationPools empty() {
            return new CachedNotificationPools(null, Map.of());
        }
    }
}
