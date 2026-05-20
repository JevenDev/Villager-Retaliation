package com.jvn.villagerretaliation.dialogue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.village.VillageEventMemory;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.entity.npc.VillagerProfession;

public final class VillagerDialogueResources {
    private static final String DEFAULT_LOCALE = "en_us";
    private static final String DIALOGUE_ROOT = "dialogue/" + DEFAULT_LOCALE;
    private static final String PROFESSION_ROOT = DIALOGUE_ROOT + "/professions/";

    private static volatile CachedDialoguePool cachedDialoguePool = CachedDialoguePool.empty();

    private VillagerDialogueResources() {
    }

    public static List<DialogueLine> lines(MinecraftServer server) {
        return load(server).lines();
    }

    public static List<String> openingLines(DialogueContext context, DialogueDisposition disposition) {
        return load(context.level().getServer()).openings().stream()
                .filter(line -> line.matches(context, disposition))
                .map(ConversationLine::text)
                .toList();
    }

    public static List<String> closingLines(DialogueContext context, DialogueDisposition disposition) {
        return load(context.level().getServer()).closings().stream()
                .filter(line -> line.matches(context, disposition))
                .map(ConversationLine::text)
                .toList();
    }

    private static DialoguePool load(MinecraftServer server) {
        CachedDialoguePool current = cachedDialoguePool;
        if (current.server() == server) {
            return current.pool();
        }

        synchronized (VillagerDialogueResources.class) {
            current = cachedDialoguePool;
            if (current.server() == server) {
                return current.pool();
            }

            DialoguePool loadedPool = read(server);
            cachedDialoguePool = new CachedDialoguePool(server, loadedPool);
            return loadedPool;
        }
    }

    private static DialoguePool read(MinecraftServer server) {
        List<DialogueLine> lines = new ArrayList<>();
        List<ConversationLine> openings = new ArrayList<>();
        List<ConversationLine> closings = new ArrayList<>();

        server.getResourceManager()
                .listResources(DIALOGUE_ROOT, location -> location.getNamespace().equals(VillagerRetaliation.MOD_ID)
                        && location.getPath().endsWith(".json"))
                .entrySet()
                .stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().toString()))
                .forEach(entry -> readFile(entry.getKey(), entry.getValue(), lines, openings, closings));

        return new DialoguePool(List.copyOf(lines), List.copyOf(openings), List.copyOf(closings));
    }

    private static void readFile(
            ResourceLocation location,
            Resource resource,
            List<DialogueLine> lines,
            List<ConversationLine> openings,
            List<ConversationLine> closings) {
        try (Reader reader = resource.openAsReader()) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            Set<VillagerProfession> defaultProfessions = defaultProfessionsFor(location);
            readDialogueLines(location, root, defaultProfessions, lines);
            readConversationLines(location, root, "openings", defaultProfessions, openings);
            readConversationLines(location, root, "closings", defaultProfessions, closings);
        } catch (IOException | IllegalStateException exception) {
            // Invalid dialogue resources are ignored so one bad datapack file cannot break every conversation.
        }
    }

    private static void readDialogueLines(
            ResourceLocation location,
            JsonObject root,
            Set<VillagerProfession> defaultProfessions,
            List<DialogueLine> lines) {
        JsonArray entries = root.getAsJsonArray("lines");
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
            Optional<DialogueRequestType> requestType = readEnum(entry, "type", DialogueRequestType.class);
            String text = readString(entry, "text");
            if (requestType.isEmpty() || text.isBlank()) {
                index++;
                continue;
            }

            String id = readString(entry, "id");
            DialogueLine.Builder builder = DialogueLine.builder(
                    id.isBlank() ? fallbackId(location, "line", index) : id,
                    requestType.get(),
                    text
            );
            applyDialogueOptions(builder, entry, defaultProfessions);
            lines.add(builder.build());
            index++;
        }
    }

    private static void readConversationLines(
            ResourceLocation location,
            JsonObject root,
            String key,
            Set<VillagerProfession> defaultProfessions,
            List<ConversationLine> lines) {
        JsonArray entries = root.getAsJsonArray(key);
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
            String text = readString(entry, "text");
            if (text.isBlank()) {
                index++;
                continue;
            }

            String id = readString(entry, "id");
            Set<VillagerProfession> professions = readProfessions(entry, defaultProfessions);
            Set<DialogueDisposition> dispositions = readEnumSet(entry, "dispositions", DialogueDisposition.class);
            int weight = Math.max(1, readInt(entry, "weight", 10));
            lines.add(new ConversationLine(
                    id.isBlank() ? fallbackId(location, key, index) : id,
                    text,
                    professions,
                    dispositions,
                    weight
            ));
            index++;
        }
    }

    private static void applyDialogueOptions(
            DialogueLine.Builder builder,
            JsonObject entry,
            Set<VillagerProfession> defaultProfessions) {
        Set<VillagerProfession> professions = readProfessions(entry, defaultProfessions);
        if (!professions.isEmpty()) {
            builder.professions(professions.toArray(VillagerProfession[]::new));
        }

        Set<DialogueDisposition> dispositions = readEnumSet(entry, "dispositions", DialogueDisposition.class);
        if (!dispositions.isEmpty()) {
            builder.dispositions(dispositions.toArray(DialogueDisposition[]::new));
        }

        Set<DialogueContext.WeatherState> weatherStates = readEnumSet(entry, "weather", DialogueContext.WeatherState.class);
        if (!weatherStates.isEmpty()) {
            builder.weatherStates(weatherStates.toArray(DialogueContext.WeatherState[]::new));
        }

        Set<DialogueContext.TimeOfDay> timeOfDays = readEnumSet(entry, "times", DialogueContext.TimeOfDay.class);
        if (!timeOfDays.isEmpty()) {
            builder.timeOfDays(timeOfDays.toArray(DialogueContext.TimeOfDay[]::new));
        }

        Set<VillageEventMemory.EventTag> eventTags = readEnumSet(entry, "event_tags", VillageEventMemory.EventTag.class);
        if (!eventTags.isEmpty()) {
            builder.eventTags(eventTags.toArray(VillageEventMemory.EventTag[]::new));
        }

        Set<VillageEventMemory.EventTag> playerEventTags = readEnumSet(entry, "player_event_tags", VillageEventMemory.EventTag.class);
        if (!playerEventTags.isEmpty()) {
            builder.playerEventTags(playerEventTags.toArray(VillageEventMemory.EventTag[]::new));
        }

        if (readBoolean(entry, "requires_recent_broken_bed_memory")) {
            builder.requiresRecentBrokenBedMemory();
        }
        if (readBoolean(entry, "requires_recent_direct_hit_memory")) {
            builder.requiresRecentDirectHitMemory();
        }
        if (readBoolean(entry, "first_conversation_only")) {
            builder.firstConversationOnly();
        }

        builder.weight(readInt(entry, "weight", 10));
    }

    private static Set<VillagerProfession> defaultProfessionsFor(ResourceLocation location) {
        String path = location.getPath();
        if (!path.startsWith(PROFESSION_ROOT) || !path.endsWith(".json")) {
            return Set.of();
        }

        String key = path.substring(PROFESSION_ROOT.length(), path.length() - ".json".length());
        if (key.contains("/")) {
            key = key.substring(key.lastIndexOf('/') + 1);
        }
        return parseProfession(key).map(Set::of).orElse(Set.of());
    }

    private static Set<VillagerProfession> readProfessions(JsonObject entry, Set<VillagerProfession> defaultProfessions) {
        Set<VillagerProfession> professions = java.util.HashSet.newHashSet(defaultProfessions.size() + 1);
        professions.addAll(defaultProfessions);
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

    private static int readInt(JsonObject entry, String key, int fallback) {
        JsonElement element = entry.get(key);
        return element == null || !element.isJsonPrimitive() ? fallback : element.getAsInt();
    }

    private static boolean readBoolean(JsonObject entry, String key) {
        JsonElement element = entry.get(key);
        return element != null && element.isJsonPrimitive() && element.getAsBoolean();
    }

    private static String fallbackId(ResourceLocation location, String group, int index) {
        return location.getPath().replace('/', '_').replace(".json", "") + "_" + group + "_" + index;
    }

    private record DialoguePool(List<DialogueLine> lines, List<ConversationLine> openings, List<ConversationLine> closings) {
        private static DialoguePool empty() {
            return new DialoguePool(List.of(), List.of(), List.of());
        }
    }

    private record CachedDialoguePool(MinecraftServer server, DialoguePool pool) {
        private static CachedDialoguePool empty() {
            return new CachedDialoguePool(null, DialoguePool.empty());
        }
    }

    private record ConversationLine(
            String id,
            String text,
            Set<VillagerProfession> professions,
            Set<DialogueDisposition> dispositions,
            int weight) {
        private boolean matches(DialogueContext context, DialogueDisposition disposition) {
            if (!this.professions.isEmpty() && !this.professions.contains(context.profession())) {
                return false;
            }
            return this.dispositions.isEmpty() || this.dispositions.contains(disposition);
        }
    }
}
