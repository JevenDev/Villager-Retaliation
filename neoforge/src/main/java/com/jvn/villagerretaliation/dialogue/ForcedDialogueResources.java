package com.jvn.villagerretaliation.dialogue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.util.VillagerInventoryItemRemoval;
import com.jvn.villagerretaliation.util.VillagerProfessionUtil;
import com.jvn.villagerretaliation.util.VillagerReputationCondition;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
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
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;

public final class ForcedDialogueResources {
    private static final String RESOURCE_ROOT = "forced_dialogue";
    private static final String LEAVE_OPTION_ID = "leave";
    private static volatile CachedForcedDialogues cachedDialogues = CachedForcedDialogues.empty();

    private ForcedDialogueResources() {
    }

    public static void warm(MinecraftServer server) {
        load(server);
    }

    public static void clearCache() {
        cachedDialogues = CachedForcedDialogues.empty();
    }

    public static Optional<ForcedDialogueDefinition> select(MinecraftServer server, ForcedDialogueTrigger trigger, ResourceLocation lootTable) {
        return select(server, trigger, lootTable, null).stream().findFirst();
    }

    public static List<ForcedDialogueDefinition> selectCandidates(MinecraftServer server, ForcedDialogueTrigger trigger, ResourceLocation lootTable) {
        return select(server, trigger, lootTable, null);
    }

    public static List<ForcedDialogueDefinition> selectCandidates(
            MinecraftServer server,
            ForcedDialogueTrigger trigger,
            ResourceLocation lootTable,
            ResourceLocation targetEntityType) {
        return select(server, trigger, lootTable, targetEntityType);
    }

    private static List<ForcedDialogueDefinition> select(
            MinecraftServer server,
            ForcedDialogueTrigger trigger,
            ResourceLocation lootTable,
            ResourceLocation targetEntityType) {
        return load(server).stream()
                .filter(definition -> definition.trigger() == trigger)
                .filter(definition -> definition.matchesLootTable(lootTable))
                .filter(definition -> definition.matchesTargetEntityType(targetEntityType))
                .sorted(Comparator.comparingInt(ForcedDialogueDefinition::priority)
                        .thenComparing(definition -> definition.lootTables().isEmpty() ? 1 : 0))
                .toList();
    }

    private static List<ForcedDialogueDefinition> load(MinecraftServer server) {
        CachedForcedDialogues current = cachedDialogues;
        if (current.server() == server) {
            return current.definitions();
        }

        synchronized (ForcedDialogueResources.class) {
            current = cachedDialogues;
            if (current.server() == server) {
                return current.definitions();
            }

            List<ForcedDialogueDefinition> loaded = read(server);
            cachedDialogues = new CachedForcedDialogues(server, loaded);
            return loaded;
        }
    }

    private static List<ForcedDialogueDefinition> read(MinecraftServer server) {
        Map<String, ForcedDialogueDefinition> definitions = new LinkedHashMap<>();
        server.getResourceManager()
                .listResources(RESOURCE_ROOT, location -> location.getNamespace().equals(VillagerRetaliation.MOD_ID)
                        && location.getPath().endsWith(".json"))
                .entrySet()
                .stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().toString()))
                .forEach(entry -> readFile(entry.getKey(), entry.getValue(), definitions));
        return List.copyOf(definitions.values());
    }

    private static void readFile(
            ResourceLocation location,
            Resource resource,
            Map<String, ForcedDialogueDefinition> definitions) {
        try (Reader reader = resource.openAsReader()) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            JsonArray entries = root.getAsJsonArray("entries");
            if (entries != null) {
                int index = 0;
                for (JsonElement element : entries) {
                    if (element.isJsonObject()) {
                        readEntry(location, element.getAsJsonObject(), index).ifPresent(definition -> definitions.put(definition.id(), definition));
                    }
                    index++;
                }
                return;
            }

            readEntry(location, root, 0).ifPresent(definition -> definitions.put(definition.id(), definition));
        } catch (IOException | IllegalStateException | JsonParseException ignored) {
            // Invalid datapack files are ignored so one custom event cannot break every forced dialogue.
        }
    }

    private static Optional<ForcedDialogueDefinition> readEntry(ResourceLocation location, JsonObject entry, int index) {
        Optional<ForcedDialogueTrigger> trigger = readEnum(entry, "trigger", ForcedDialogueTrigger.class);
        if (trigger.isEmpty()) {
            trigger = readEnum(entry, "event", ForcedDialogueTrigger.class);
        }
        List<String> lines = readLines(entry);
        if (trigger.isEmpty() || lines.isEmpty()) {
            return Optional.empty();
        }

        String id = readString(entry, "id");
        if (id.isBlank()) {
            id = fallbackId(location, index);
        }

        List<ForcedDialogueOption> leaveOptions = readLeaveOptions(entry, trigger.get());
        ForcedDialogueOption leaveOption = leaveOptions.stream().findFirst().orElse(defaultLeaveOption());
        List<ForcedDialogueOption> options = readOptions(entry, leaveOption);
        String leaveOptionId = leaveOption.id();
        leaveOption = options.stream()
                .filter(option -> option.id().equals(leaveOptionId))
                .findFirst()
                .orElse(leaveOption);

        return Optional.of(new ForcedDialogueDefinition(
                id,
                trigger.get(),
                lines,
                readBoolean(entry, "initiate_dialogue", true),
                readBoolean(entry, "aggro_immediately"),
                readBoolean(entry, "force_camera_towards_villager"),
                readBoolean(entry, "requires_line_of_sight", true),
                Math.max(1.0D, readDouble(entry, "witness_radius", 12.0D)),
                readInt(entry, "reputation", 0),
                readInt(entry, "priority", 0),
                readInt(entry, "min_recent_container_thefts", 0),
                readInt(entry, "max_recent_container_thefts", Integer.MAX_VALUE),
                readInt(entry, "min_recent_retaliations", 0),
                readInt(entry, "max_recent_retaliations", Integer.MAX_VALUE),
                readLootTables(entry),
                readTargetEntityTypes(entry),
                readProfessions(entry),
                options,
                leaveOption,
                leaveOptions
        ));
    }

    private static Set<VillagerProfession> readProfessions(JsonObject entry) {
        java.util.LinkedHashSet<VillagerProfession> professions = new java.util.LinkedHashSet<>();
        for (String value : readStringList(entry, "witness_profession")) {
            VillagerProfessionUtil.parse(value).ifPresent(professions::add);
        }
        for (String value : readStringList(entry, "witness_professions")) {
            VillagerProfessionUtil.parse(value).ifPresent(professions::add);
        }
        for (String value : readStringList(entry, "professions")) {
            VillagerProfessionUtil.parse(value).ifPresent(professions::add);
        }
        return Set.copyOf(professions);
    }

    private static Set<ResourceLocation> readLootTables(JsonObject entry) {
        java.util.LinkedHashSet<ResourceLocation> lootTables = new java.util.LinkedHashSet<>();
        readResourceLocation(entry, "loot_table").ifPresent(lootTables::add);
        JsonArray entries = entry.getAsJsonArray("loot_tables");
        if (entries != null) {
            for (JsonElement element : entries) {
                if (element.isJsonPrimitive()) {
                    parseResourceLocation(element.getAsString()).ifPresent(lootTables::add);
                }
            }
        }
        return Set.copyOf(lootTables);
    }

    private static Set<ResourceLocation> readTargetEntityTypes(JsonObject entry) {
        java.util.LinkedHashSet<ResourceLocation> entityTypes = new java.util.LinkedHashSet<>();
        readResourceLocation(entry, "target_entity_type").ifPresent(entityTypes::add);
        for (String value : readStringList(entry, "target_entity_types")) {
            parseResourceLocation(value).ifPresent(entityTypes::add);
        }
        for (String value : readStringList(entry, "target_entities")) {
            parseResourceLocation(value).ifPresent(entityTypes::add);
        }
        return Set.copyOf(entityTypes);
    }

    private static List<String> readLines(JsonObject entry) {
        List<String> lines = new ArrayList<>(readStringList(entry, "lines"));
        String line = readString(entry, "line");
        if (!line.isBlank()) {
            lines.add(0, line);
        }
        return lines.stream()
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
    }

    private static List<ForcedDialogueOption> readOptions(JsonObject entry, ForcedDialogueOption leaveOption) {
        JsonArray entries = entry.getAsJsonArray("options");
        if (entries == null) {
            return List.of(leaveOption);
        }

        List<ForcedDialogueOption> options = new ArrayList<>();
        int index = 0;
        for (JsonElement element : entries) {
            if (!element.isJsonObject()) {
                index++;
                continue;
            }
            JsonObject option = element.getAsJsonObject();
            String id = readString(option, "id");
            String label = readString(option, "label");
            if (id.isBlank() || label.isBlank()) {
                index++;
                continue;
            }
            options.add(readOption(option, id, label, index));
            index++;
        }
        if (options.stream().noneMatch(option -> option.id().equals(leaveOption.id()))) {
            options.add(leaveOption);
        }
        return List.copyOf(options);
    }

    private static List<ForcedDialogueOption> readLeaveOptions(JsonObject entry, ForcedDialogueTrigger trigger) {
        JsonArray options = entry.getAsJsonArray("leave_options");
        if (options != null) {
            List<ForcedDialogueOption> leaveOptions = new ArrayList<>();
            int index = 0;
            for (JsonElement element : options) {
                if (element.isJsonObject()) {
                    JsonObject option = element.getAsJsonObject();
                    String label = readString(option, "label");
                    leaveOptions.add(readOption(option, LEAVE_OPTION_ID, label.isBlank() ? "Leave" : label, 1000 + index));
                }
                index++;
            }
            if (!leaveOptions.isEmpty()) {
                return List.copyOf(leaveOptions);
            }
        }

        JsonElement element = entry.get("leave_option");
        if (element == null) {
            element = entry.get("escape_option");
        }
        if (element == null && trigger == ForcedDialogueTrigger.CONTAINER_THEFT) {
            return defaultTheftLeaveOptions();
        }
        JsonObject option = element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
        String label = readString(option, "label");
        return List.of(readOption(option, LEAVE_OPTION_ID, label.isBlank() ? "Leave" : label, 1000));
    }

    private static ForcedDialogueOption defaultLeaveOption() {
        return readOption(new JsonObject(), LEAVE_OPTION_ID, "Leave", 1000);
    }

    private static List<ForcedDialogueOption> defaultTheftLeaveOptions() {
        JsonObject trusted = defaultTheftLeaveOption(
                List.of("trusted", "respected", "revered", "royalty"),
                "I will take {stolen_items} back. Go, and do not make me regret letting you leave.",
                -2,
                0.05D,
                1000);
        JsonObject wary = defaultTheftLeaveOption(
                List.of("neutral", "suspicious"),
                "I will take {stolen_items} back. Walking away does not make this settled.",
                -4,
                0.25D,
                1001);
        JsonObject hostile = defaultTheftLeaveOption(
                List.of("hostile", "despised", "feared"),
                "No. I will take {stolen_items} back, and you are done running from this.",
                -8,
                0.75D,
                1002);
        return List.of(
                readOption(trusted, LEAVE_OPTION_ID, "Leave", 1000),
                readOption(wary, LEAVE_OPTION_ID, "Leave", 1001),
                readOption(hostile, LEAVE_OPTION_ID, "Leave", 1002));
    }

    private static JsonObject defaultTheftLeaveOption(
            List<String> reputationLevels,
            String response,
            int reputation,
            double aggroChance,
            int order) {
        JsonObject option = new JsonObject();
        option.addProperty("label", "Leave");
        option.addProperty("response", response);
        option.addProperty("reputation", reputation);
        option.addProperty("end_conversation", true);
        option.addProperty("aggro_chance", aggroChance);
        option.addProperty("order", order);
        JsonArray levels = new JsonArray();
        reputationLevels.forEach(levels::add);
        option.add("reputation_levels", levels);
        JsonObject stolenItems = new JsonObject();
        stolenItems.addProperty("destination", "villager_inventory_then_source_container");
        stolenItems.addProperty("success_response", response);
        stolenItems.addProperty("failure_response", "You no longer have {stolen_items}. Then we are past excuses.");
        stolenItems.addProperty("failure_reputation", -5);
        stolenItems.addProperty("failure_aggro", true);
        stolenItems.addProperty("failure_end_conversation", true);
        option.add("take_stolen_items", stolenItems);
        return option;
    }

    private static ForcedDialogueOption readOption(JsonObject option, String id, String label, int fallbackOrder) {
        return new ForcedDialogueOption(
                id,
                label,
                readString(option, "response"),
                readInt(option, "reputation", 0),
                readBoolean(option, "aggro"),
                clampChance(readDouble(option, "aggro_chance", 0.0D)),
                readBoolean(option, "end_conversation", true),
                readInt(option, "order", fallbackOrder),
                readStolenItemReturn(option),
                readItemPayment(option),
                VillagerReputationCondition.read(option)
        );
    }

    private static ForcedDialogueStolenItemReturn readStolenItemReturn(JsonObject option) {
        return readStolenItemReturnJson(option, "take_stolen_items")
                .or(() -> readStolenItemReturnJson(option, "return_stolen_items"))
                .map(entry -> new ForcedDialogueStolenItemReturn(
                        readString(entry, "success_response"),
                        readString(entry, "failure_response"),
                        readInt(entry, "success_reputation", 0),
                        readInt(entry, "failure_reputation", 0),
                        readBoolean(entry, "failure_aggro"),
                        readBoolean(entry, "failure_end_conversation"),
                        readEnum(entry, "destination", ForcedDialogueItemDestination.class)
                                .orElse(ForcedDialogueItemDestination.VILLAGER_INVENTORY_THEN_SOURCE_CONTAINER),
                        readEnum(entry, "overflow_destination", ForcedDialogueItemDestination.class).orElse(null),
                        readBoolean(entry, "require_space", true)
                ))
                .orElse(ForcedDialogueStolenItemReturn.empty());
    }

    private static Optional<JsonObject> readStolenItemReturnJson(JsonObject option, String key) {
        JsonElement element = option.get(key);
        if (element == null) {
            return Optional.empty();
        }
        if (element.isJsonPrimitive() && element.getAsBoolean()) {
            return Optional.of(new JsonObject());
        }
        return element.isJsonObject() ? Optional.of(element.getAsJsonObject()) : Optional.empty();
    }

    private static ForcedDialogueItemPayment readItemPayment(JsonObject option) {
        return readPaymentJson(option, "take_items")
                .or(() -> readPaymentJson(option, "payment"))
                .map(payment -> new ForcedDialogueItemPayment(
                        payment.removal(),
                        readString(payment.entry(), "success_response"),
                        readString(payment.entry(), "failure_response"),
                        readInt(payment.entry(), "success_reputation", 0),
                        readInt(payment.entry(), "failure_reputation", 0),
                        readBoolean(payment.entry(), "failure_aggro"),
                        readBoolean(payment.entry(), "failure_end_conversation"),
                        readEnum(payment.entry(), "destination", ForcedDialogueItemDestination.class)
                                .orElse(ForcedDialogueItemDestination.DISCARD),
                        readEnum(payment.entry(), "overflow_destination", ForcedDialogueItemDestination.class).orElse(null),
                        readBoolean(payment.entry(), "require_space", true)
                ))
                .orElse(ForcedDialogueItemPayment.empty());
    }

    private static Optional<PaymentJson> readPaymentJson(JsonObject option, String key) {
        JsonElement element = option.get(key);
        if (element == null || !element.isJsonObject()) {
            return Optional.empty();
        }
        return VillagerInventoryItemRemoval.read(option, key)
                .map(removal -> new PaymentJson(element.getAsJsonObject(), removal));
    }

    static String resolveTemplate(String text, ForcedDialogueContext context) {
        return resolveTemplate(text, context, Map.of());
    }

    static String resolveTemplate(String text, ForcedDialogueContext context, Map<String, String> extraReplacements) {
        Map<String, String> replacements = new HashMap<>();
        replacements.put("villager", context.villagerName());
        replacements.put("player", context.playerName());
        replacements.put("target", context.targetName());
        replacements.put("target_name", context.targetName());
        replacements.put("target_kind", context.targetKind());
        replacements.put("target_type", context.targetType());
        replacements.put("item", context.itemName());
        replacements.put("item_id", context.itemId());
        replacements.put("count", Integer.toString(context.itemCount()));
        replacements.put("item_count", Integer.toString(context.itemCount()));
        replacements.put("item_stack", context.itemStack());
        replacements.put("items", context.itemList());
        replacements.put("stolen_item", context.itemName());
        replacements.put("stolen_item_id", context.itemId());
        replacements.put("stolen_count", Integer.toString(context.itemCount()));
        replacements.put("stolen_item_count", Integer.toString(context.itemCount()));
        replacements.put("stolen_stack", context.itemStack());
        replacements.put("stolen_items", context.itemList());
        replacements.put("container", context.containerName());
        replacements.put("loot_table", context.lootTable());
        replacements.put("prior_container_thefts", Integer.toString(context.priorContainerThefts()));
        replacements.put("container_theft_offense", Integer.toString(context.priorContainerThefts() + 1));
        replacements.put("prior_retaliations", Integer.toString(context.priorRetaliations()));
        replacements.put("retaliation_offense", Integer.toString(context.priorRetaliations() + 1));
        replacements.put("x", Integer.toString(context.x()));
        replacements.put("y", Integer.toString(context.y()));
        replacements.put("z", Integer.toString(context.z()));
        replacements.putAll(extraReplacements);
        return VillagerDialogueResources.resolveTemplate(text, replacements);
    }

    private static <E extends Enum<E>> Optional<E> readEnum(JsonObject entry, String key, Class<E> enumClass) {
        String value = readString(entry, key);
        if (value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Enum.valueOf(enumClass, value.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    private static String readString(JsonObject entry, String key) {
        JsonElement element = entry.get(key);
        return element == null || !element.isJsonPrimitive() ? "" : element.getAsString().trim();
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
            if (child.isJsonPrimitive()) {
                String value = child.getAsString().trim();
                if (!value.isBlank()) {
                    values.add(value);
                }
            }
        }
        return List.copyOf(values);
    }

    private static Optional<ResourceLocation> readResourceLocation(JsonObject entry, String key) {
        return parseResourceLocation(readString(entry, key));
    }

    private static Optional<ResourceLocation> parseResourceLocation(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(ResourceLocation.tryParse(value.trim()));
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

    private static double readDouble(JsonObject entry, String key, double fallback) {
        JsonElement element = entry.get(key);
        if (element == null || !element.isJsonPrimitive()) {
            return fallback;
        }
        try {
            return element.getAsDouble();
        } catch (NumberFormatException | UnsupportedOperationException ignored) {
            return fallback;
        }
    }

    private static boolean readBoolean(JsonObject entry, String key) {
        JsonElement element = entry.get(key);
        return element != null && element.isJsonPrimitive() && element.getAsBoolean();
    }

    private static boolean readBoolean(JsonObject entry, String key, boolean fallback) {
        JsonElement element = entry.get(key);
        return element == null || !element.isJsonPrimitive() ? fallback : element.getAsBoolean();
    }

    private static double clampChance(double value) {
        if (!Double.isFinite(value)) {
            return 0.0D;
        }
        return Math.max(0.0D, Math.min(1.0D, value));
    }

    private static String fallbackId(ResourceLocation location, int index) {
        return location.getPath().replace('/', '_').replace(".json", "") + "_" + index;
    }

    private record CachedForcedDialogues(MinecraftServer server, List<ForcedDialogueDefinition> definitions) {
        private static CachedForcedDialogues empty() {
            return new CachedForcedDialogues(null, List.of());
        }
    }

    private record PaymentJson(
            JsonObject entry,
            VillagerInventoryItemRemoval removal) {
    }

    public enum ForcedDialogueTrigger {
        CONTAINER_THEFT,
        CONTAINER_OPENED,
        RETALIATION_STARTED
    }

    public enum ForcedDialogueItemDestination {
        DISCARD,
        VILLAGER_INVENTORY,
        VILLAGER_INVENTORY_THEN_SOURCE_CONTAINER,
        SOURCE_CONTAINER,
        DROP_AT_VILLAGER,
        DROP_AT_CONTAINER
    }

    public record ForcedDialogueDefinition(
            String id,
            ForcedDialogueTrigger trigger,
            List<String> lines,
            boolean initiateDialogue,
            boolean aggroImmediately,
            boolean forceCameraTowardsVillager,
            boolean requiresLineOfSight,
            double witnessRadius,
            int reputationDelta,
            int priority,
            int minRecentContainerThefts,
            int maxRecentContainerThefts,
            int minRecentRetaliations,
            int maxRecentRetaliations,
            Set<ResourceLocation> lootTables,
            Set<ResourceLocation> targetEntityTypes,
            Set<VillagerProfession> witnessProfessions,
            List<ForcedDialogueOption> options,
            ForcedDialogueOption leaveOption,
            List<ForcedDialogueOption> leaveOptions) {
        public String selectLine(RandomSource random) {
            if (this.lines.isEmpty()) {
                return "";
            }
            return this.lines.get(random.nextInt(this.lines.size()));
        }

        private boolean matchesLootTable(ResourceLocation lootTable) {
            return this.lootTables.isEmpty() || (lootTable != null && this.lootTables.contains(lootTable));
        }

        private boolean matchesTargetEntityType(ResourceLocation targetEntityType) {
            return this.targetEntityTypes.isEmpty() || (targetEntityType != null && this.targetEntityTypes.contains(targetEntityType));
        }

        public boolean matchesWitness(Villager villager) {
            return this.witnessProfessions.isEmpty() || this.witnessProfessions.contains(villager.getVillagerData().getProfession());
        }

        public boolean matchesRecentContainerThefts(int count) {
            return count >= this.minRecentContainerThefts && count <= this.maxRecentContainerThefts;
        }

        public boolean matchesRecentRetaliations(int count) {
            return count >= this.minRecentRetaliations && count <= this.maxRecentRetaliations;
        }
    }

    public record ForcedDialogueOption(
            String id,
            String label,
            String response,
            int reputationDelta,
            boolean aggro,
            double aggroChance,
            boolean endConversation,
            int order,
            ForcedDialogueStolenItemReturn stolenItemReturn,
            ForcedDialogueItemPayment itemPayment,
            VillagerReputationCondition reputationCondition) {
    }

    public record ForcedDialogueStolenItemReturn(
            String successResponse,
            String failureResponse,
            int successReputationDelta,
            int failureReputationDelta,
            boolean failureAggro,
            boolean failureEndConversation,
            ForcedDialogueItemDestination destination,
            ForcedDialogueItemDestination overflowDestination,
            boolean requireSpace) {
        private static final ForcedDialogueStolenItemReturn EMPTY = new ForcedDialogueStolenItemReturn(
                "",
                "",
                0,
                0,
                false,
                false,
                ForcedDialogueItemDestination.VILLAGER_INVENTORY_THEN_SOURCE_CONTAINER,
                null,
                true);

        private static ForcedDialogueStolenItemReturn empty() {
            return EMPTY;
        }

        public boolean isEmpty() {
            return this == EMPTY;
        }
    }

    public record ForcedDialogueItemPayment(
            VillagerInventoryItemRemoval removal,
            String successResponse,
            String failureResponse,
            int successReputationDelta,
            int failureReputationDelta,
            boolean failureAggro,
            boolean failureEndConversation,
            ForcedDialogueItemDestination destination,
            ForcedDialogueItemDestination overflowDestination,
            boolean requireSpace) {
        private static final ForcedDialogueItemPayment EMPTY = new ForcedDialogueItemPayment(
                VillagerInventoryItemRemoval.empty(),
                "",
                "",
                0,
                0,
                false,
                false,
                ForcedDialogueItemDestination.DISCARD,
                null,
                true);

        private static ForcedDialogueItemPayment empty() {
            return EMPTY;
        }

        public boolean isEmpty() {
            return this.removal.isEmpty();
        }
    }

    public record ForcedDialogueContext(
            String villagerName,
            String playerName,
            String targetName,
            String targetKind,
            String targetType,
            String itemName,
            String itemId,
            int itemCount,
            String itemStack,
            String itemList,
            String containerName,
            String lootTable,
            int priorContainerThefts,
            int priorRetaliations,
            int x,
            int y,
            int z) {
    }
}
