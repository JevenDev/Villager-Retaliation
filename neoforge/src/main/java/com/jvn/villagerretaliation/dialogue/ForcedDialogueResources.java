package com.jvn.villagerretaliation.dialogue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.profile.VillagerSocialAttribute;
import com.jvn.villagerretaliation.quest.QuestIds;
import com.jvn.villagerretaliation.reputation.VillagerReputationLevel;
import com.jvn.villagerretaliation.util.DatapackDiagnostics;
import com.jvn.villagerretaliation.util.DatapackJsonReader;
import com.jvn.villagerretaliation.util.DatapackResourceLoader;
import com.jvn.villagerretaliation.util.VillagerEquipmentCondition;
import com.jvn.villagerretaliation.util.VillagerInteractionTextUtil;
import com.jvn.villagerretaliation.util.VillagerInventoryItemRemoval;
import com.jvn.villagerretaliation.util.VillagerLocale;
import com.jvn.villagerretaliation.util.VillagerPlayerItemCondition;
import com.jvn.villagerretaliation.util.VillagerProfessionUtil;
import com.jvn.villagerretaliation.util.VillagerReputationCondition;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;

public final class ForcedDialogueResources {
    private static final String RESOURCE_ROOT = "forced_dialogue";
    private static final String DEFAULT_NAMESPACE = "villagerretaliation";
    private static final String LEAVE_OPTION_ID = "leave";
    private static final Set<String> ROOT_KEYS = Set.of(
            "entries", "notifications", "messages", "openings", "closings", "pacify",
            "metadata",
            "id", "trigger", "event", "line", "lines", "priority", "chance", "witness_radius",
            "replace", "remove", "message_prefix", "text_prefix", "line_key", "line_keys", "text_key", "text_keys",
            "witness_profession", "witness_professions", "professions",
            "requires_witness_unarmed", "witness_unarmed", "requires_witness_armed", "witness_armed",
            "player_item", "player_items", "player_item_tag", "player_item_tags", "player_item_slot", "player_item_slots",
            "min_player_item_durability", "max_player_item_durability", "min_player_item_durability_percent", "max_player_item_durability_percent",
            "min_held_item_durability", "max_held_item_durability", "min_held_item_durability_percent", "max_held_item_durability_percent",
            "player_item_enchantment", "player_item_enchantments", "held_item_enchantment", "held_item_enchantments",
            "min_player_item_enchantment_level", "max_player_item_enchantment_level", "min_held_item_enchantment_level", "max_held_item_enchantment_level",
            "requires_line_of_sight", "output", "initiate_dialogue", "aggro_immediately", "force_camera_towards_villager",
            "reputation", "reputation_level", "reputation_levels", "min_reputation", "max_reputation",
            "loot_table", "loot_tables", "target_entity_type", "target_entity_types", "target_entities",
            "min_recent_container_thefts", "max_recent_container_thefts", "min_recent_retaliations", "max_recent_retaliations",
            "options", "leave_option", "leave_options");
    private static final Set<String> ENTRY_KEYS = ROOT_KEYS;
    private static final Set<String> OPTION_KEYS = Set.of(
            "id", "label", "label_key", "message_prefix", "text_prefix", "response", "responses", "response_key", "response_keys", "reputation", "aggro", "aggro_chance", "end_conversation", "order",
            "reputation_level", "reputation_levels", "min_reputation", "max_reputation", "take_items", "take_stolen_items",
            "conditions",
            "follow_up", "requires_high_knowledge", "requires_high_guts", "requires_high_proficiency", "requires_high_kindness", "requires_high_charm",
            "min_knowledge", "max_knowledge", "min_guts", "max_guts", "min_proficiency", "max_proficiency",
            "min_kindness", "max_kindness", "min_charm", "max_charm");
    private static final Set<String> NOTIFICATION_TRIGGER_PREFIXES = Set.of(
            "ambient.", "alert.", "combat.", "dialogue.", "gift.", "quest.", "recruitment.", "reputation.", "trade.");
    private static final Comparator<ForcedDialogueDefinition> CANDIDATE_ORDER =
            Comparator.comparingInt(ForcedDialogueDefinition::priority)
                    .thenComparing(definition -> definition.lootTables().isEmpty() ? 1 : 0);
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

    public static List<ForcedDialogueDefinition> playerItemProximityCandidates(MinecraftServer server) {
        return load(server).playerItemProximityCandidates();
    }

    private static List<ForcedDialogueDefinition> select(
            MinecraftServer server,
            ForcedDialogueTrigger trigger,
            ResourceLocation lootTable,
            ResourceLocation targetEntityType) {
        return load(server).byTrigger().getOrDefault(trigger, List.of()).stream()
                .filter(definition -> definition.matchesLootTable(lootTable))
                .filter(definition -> definition.matchesTargetEntityType(targetEntityType))
                .toList();
    }

    private static CachedForcedDialogues load(MinecraftServer server) {
        CachedForcedDialogues current = cachedDialogues;
        if (current.server() == server) {
            return current;
        }

        synchronized (ForcedDialogueResources.class) {
            current = cachedDialogues;
            if (current.server() == server) {
                return current;
            }

            List<ForcedDialogueDefinition> loaded = read(server);
            cachedDialogues = CachedForcedDialogues.create(server, loaded);
            return cachedDialogues;
        }
    }

    private static List<ForcedDialogueDefinition> read(MinecraftServer server) {
        Map<String, ForcedDialogueDefinition> definitions = new LinkedHashMap<>();
        List<LoadedForcedDialogueResource> resources = DatapackResourceLoader.jsonResources(server, RESOURCE_ROOT).stream()
                .map(resource -> DatapackResourceLoader.readObject(resource.location(), "forced dialogue", resource.resource())
                        .map(root -> new LoadedForcedDialogueResource(resource, root)))
                .flatMap(Optional::stream)
                .toList();
        boolean replacementMode = resources.stream()
                .anyMatch(resource -> readBoolean(resource.root(), "replace"));
        for (LoadedForcedDialogueResource resource : resources) {
            if (replacementMode
                    && isBuiltInModResource(resource.resource())
                    && !readBoolean(resource.root(), "replace")) {
                continue;
            }
            readFile(resource.resource().location(), resource.root(), definitions, replacementMode);
        }
        return List.copyOf(definitions.values());
    }

    private static void readFile(
            ResourceLocation location,
            JsonObject root,
            Map<String, ForcedDialogueDefinition> definitions,
            boolean replacementMode) {
        DatapackDiagnostics.warnMisplacedRootKeys(location, "forced dialogue", root, Map.of(
                "notifications", "data/villagerretaliation/notifications/<locale>/<file>.json",
                "messages", "data/villagerretaliation/dialogue/<locale>/<file>.json",
                "openings", "data/villagerretaliation/dialogue/<locale>/<file>.json",
                "closings", "data/villagerretaliation/dialogue/<locale>/<file>.json",
                "pacify", "data/villagerretaliation/dialogue/<locale>/<file>.json"));
        DatapackDiagnostics.warnUnknownRootKeys(location, "forced dialogue", root, ROOT_KEYS);
        if (readBoolean(root, "replace")) {
            if (!replacementMode) {
                definitions.clear();
            }
            if (isControlOnly(root, "replace", "metadata")) {
                return;
            }
        }
        ResourceLocation defaultQuestId = defaultQuestId(location, root, null);
        String rootMessagePrefix = readMessagePrefix(root, "");
        JsonArray entries = root.getAsJsonArray("entries");
        if (entries != null) {
            int index = 0;
            for (JsonElement element : entries) {
                if (element.isJsonObject()) {
                    JsonObject entry = element.getAsJsonObject();
                    if (removeDefinition(location, entry, index, definitions)) {
                        index++;
                        continue;
                    }
                    readEntry(location, entry, index, defaultQuestId, rootMessagePrefix)
                            .ifPresent(definition -> putDefinition(location, definitions, definition));
                }
                index++;
            }
            return;
        }

        if (removeDefinition(location, root, 0, definitions)) {
            return;
        }
        readEntry(location, root, 0, defaultQuestId, rootMessagePrefix)
                .ifPresent(definition -> putDefinition(location, definitions, definition));
    }

    private static boolean isBuiltInModResource(DatapackResourceLoader.JsonResource resource) {
        return VillagerRetaliation.MOD_ID.equals(resource.location().getNamespace())
                && resource.isFromPack(VillagerRetaliation.MOD_ID);
    }

    private record LoadedForcedDialogueResource(DatapackResourceLoader.JsonResource resource, JsonObject root) {
    }

    private static boolean isControlOnly(JsonObject root, String... allowedKeys) {
        Set<String> allowed = new java.util.HashSet<>(List.of(allowedKeys));
        for (String key : root.keySet()) {
            if (!allowed.contains(key)) {
                return false;
            }
        }
        return true;
    }

    private static boolean removeDefinition(
            ResourceLocation location,
            JsonObject entry,
            int index,
            Map<String, ForcedDialogueDefinition> definitions) {
        if (!readBoolean(entry, "remove")) {
            return false;
        }
        String id = readString(entry, "id");
        if (id.isBlank()) {
            id = fallbackId(location, index);
        }
        definitions.remove(id);
        return true;
    }

    private static void putDefinition(
            ResourceLocation location,
            Map<String, ForcedDialogueDefinition> definitions,
            ForcedDialogueDefinition definition) {
        ForcedDialogueDefinition previous = definitions.put(definition.id(), definition);
        if (previous != null) {
            DatapackDiagnostics.warnDuplicateId(location, "forced dialogue", definition.id(), previous.source());
        }
    }

    private static Optional<ForcedDialogueDefinition> readEntry(
            ResourceLocation location,
            JsonObject entry,
            int index,
            ResourceLocation defaultQuestId,
            String rootMessagePrefix) {
        DatapackDiagnostics.warnUnknownKeys(location, "forced dialogue", entryContext(entry, index), entry, ENTRY_KEYS);
        DatapackDiagnostics.warnInertPlayerItemSlots(location, entryContext(entry, index), entry);
        ResourceLocation entryQuestId = defaultQuestId(location, entry, defaultQuestId);
        Optional<ForcedDialogueTrigger> trigger = readEnum(entry, "trigger", ForcedDialogueTrigger.class);
        if (trigger.isEmpty()) {
            trigger = readEnum(entry, "event", ForcedDialogueTrigger.class);
        }
        if (trigger.isEmpty()) {
            warnWrongForcedTriggerFamily(location, entry, index);
        }
        String id = readString(entry, "id");
        if (id.isBlank()) {
            id = fallbackId(location, index);
        }
        String messagePrefix = readMessagePrefix(entry, childMessagePrefix(rootMessagePrefix, id));
        List<LocalizedText> lines = readLines(entry, messagePrefix);
        if (trigger.isEmpty() || lines.isEmpty()) {
            return Optional.empty();
        }

        List<ForcedDialogueOption> leaveOptions = readLeaveOptions(location, entry, trigger.get(), entryQuestId, messagePrefix);
        ForcedDialogueOption leaveOption = leaveOptions.stream().findFirst().orElse(defaultLeaveOption());
        List<ForcedDialogueOption> options = readOptions(location, entry, leaveOption, entryQuestId, messagePrefix);
        String leaveOptionId = leaveOption.id();
        leaveOption = options.stream()
                .filter(option -> option.id().equals(leaveOptionId))
                .findFirst()
                .orElse(leaveOption);

        return Optional.of(new ForcedDialogueDefinition(
                id,
                location,
                trigger.get(),
                readOutput(entry),
                lines,
                readBoolean(entry, "initiate_dialogue", true),
                readBoolean(entry, "aggro_immediately"),
                readBoolean(entry, "force_camera_towards_villager"),
                readBoolean(entry, "requires_line_of_sight", true),
                Math.max(1.0D, readDouble(entry, "witness_radius", 12.0D)),
                clampChance(readDouble(entry, "chance", 1.0D)),
                readInt(entry, "reputation", 0),
                readInt(entry, "priority", 0),
                readInt(entry, "min_recent_container_thefts", 0),
                readInt(entry, "max_recent_container_thefts", Integer.MAX_VALUE),
                readInt(entry, "min_recent_retaliations", 0),
                readInt(entry, "max_recent_retaliations", Integer.MAX_VALUE),
                readLootTables(entry),
                readTargetEntityTypes(entry),
                readProfessions(location, entryContext(entry, index), entry),
                VillagerEquipmentCondition.read(entry, "witness"),
                VillagerPlayerItemCondition.read(entry),
                VillagerReputationCondition.read(entry),
                options,
                leaveOption,
                leaveOptions
        ));
    }

    private static ForcedDialogueOutput readOutput(JsonObject entry) {
        JsonElement element = entry.get("output");
        if (element == null || !element.isJsonObject()) {
            return ForcedDialogueOutput.forcedDialogue();
        }
        JsonObject output = element.getAsJsonObject();
        ForcedDialogueOutputMode mode = readEnum(output, "mode", ForcedDialogueOutputMode.class)
                .orElse(ForcedDialogueOutputMode.FORCED_DIALOGUE);
        double radius = readDouble(output, "radius", 0.0D);
        return new ForcedDialogueOutput(
                mode,
                radius > 0.0D ? radius : 0.0D
        );
    }

    private static Set<VillagerProfession> readProfessions(ResourceLocation location, String context, JsonObject entry) {
        java.util.LinkedHashSet<VillagerProfession> professions = new java.util.LinkedHashSet<>();
        for (String value : readStringList(entry, "witness_profession")) {
            addProfession(location, context, professions, value);
        }
        for (String value : readStringList(entry, "witness_professions")) {
            addProfession(location, context, professions, value);
        }
        for (String value : readStringList(entry, "professions")) {
            addProfession(location, context, professions, value);
        }
        return Set.copyOf(professions);
    }

    private static void addProfession(
            ResourceLocation location,
            String context,
            java.util.LinkedHashSet<VillagerProfession> professions,
            String value) {
        Optional<VillagerProfession> profession = VillagerProfessionUtil.parse(value);
        if (profession.isPresent()) {
            professions.add(profession.get());
        } else {
            DatapackDiagnostics.warnUnknownProfession(location, context, value);
        }
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

    private static List<LocalizedText> readLines(JsonObject entry, String messagePrefix) {
        List<String> lines = new ArrayList<>(readStringList(entry, "lines"));
        String line = readString(entry, "line");
        if (!line.isBlank()) {
            lines.add(0, line);
        }
        List<String> keys = readStringList(entry, "line_key", "line_keys", "text_key", "text_keys");
        if (keys.isEmpty() && !messagePrefix.isBlank() && !lines.isEmpty()) {
            keys = List.of(messagePrefix + ".line");
        }
        String fallback = line.isBlank() && !lines.isEmpty() ? lines.getFirst() : line;
        return localizedVariants(lines, keys, fallback);
    }

    private static List<LocalizedText> readResponses(JsonObject entry, String messageKey) {
        return readResponseVariants(entry, "response", "responses", messageKey);
    }

    private static List<LocalizedText> readResponseVariants(JsonObject entry, String singleKey, String listKey, String messageKey) {
        List<String> responses = new ArrayList<>(readStringList(entry, listKey));
        String response = readString(entry, singleKey);
        if (!response.isBlank()) {
            responses.add(0, response);
        }
        List<String> keys = readStringList(entry, singleKey + "_key", singleKey + "_keys");
        if (keys.isEmpty() && !messageKey.isBlank() && !responses.isEmpty()) {
            keys = List.of(messageKey);
        }
        String fallback = response.isBlank() && !responses.isEmpty() ? responses.getFirst() : response;
        return localizedVariants(responses, keys, fallback);
    }

    private static List<LocalizedText> localizedVariants(List<String> values, List<String> keys, String fallback) {
        if (!keys.isEmpty()) {
            return keys.stream()
                    .map(key -> LocalizedText.keyed(key, fallback))
                    .filter(value -> !value.isBlank())
                    .distinct()
                    .toList();
        }
        return inlineTexts(values);
    }

    public static LocalizedText inlineText(String text) {
        return LocalizedText.inline(text);
    }

    public static List<LocalizedText> inlineTexts(List<String> texts) {
        return texts.stream()
                .map(LocalizedText::inline)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
    }

    private static List<ForcedDialogueOption> readOptions(
            ResourceLocation location,
            JsonObject entry,
            ForcedDialogueOption leaveOption,
            ResourceLocation defaultQuestId,
            String messagePrefix) {
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
            if (location != null) {
                DatapackDiagnostics.warnUnknownKeys(location, "forced dialogue option", optionContext(option, index), option, OPTION_KEYS);
            }
            String id = readString(option, "id");
            String optionMessagePrefix = readMessagePrefix(option, childMessagePrefix(childMessagePrefix(messagePrefix, "option"), id));
            LocalizedText label = readLabel(option, optionMessagePrefix);
            if (id.isBlank() || label.isBlank()) {
                index++;
                continue;
            }
            options.add(readOption(location, optionContext(option, index), option, id, label, index, defaultQuestId, optionMessagePrefix));
            index++;
        }
        if (options.stream().noneMatch(option -> option.id().equals(leaveOption.id()))) {
            options.add(leaveOption);
        }
        return List.copyOf(options);
    }

    private static List<ForcedDialogueOption> readLeaveOptions(
            ResourceLocation location,
            JsonObject entry,
            ForcedDialogueTrigger trigger,
            ResourceLocation defaultQuestId,
            String messagePrefix) {
        JsonArray options = entry.getAsJsonArray("leave_options");
        if (options != null) {
            List<ForcedDialogueOption> leaveOptions = new ArrayList<>();
            int index = 0;
            for (JsonElement element : options) {
                if (element.isJsonObject()) {
                    JsonObject option = element.getAsJsonObject();
                    if (location != null) {
                        DatapackDiagnostics.warnUnknownKeys(location, "forced dialogue leave option", optionContext(option, index), option, OPTION_KEYS);
                    }
                    String optionMessagePrefix = readMessagePrefix(option, childMessagePrefix(messagePrefix, "leave." + index));
                    LocalizedText label = readLabel(option, optionMessagePrefix);
                    leaveOptions.add(readOption(
                            location,
                            optionContext(option, index),
                            option,
                            LEAVE_OPTION_ID,
                            label.isBlank() ? LocalizedText.inline("Leave") : label,
                            1000 + index,
                            defaultQuestId,
                            optionMessagePrefix));
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
        String optionMessagePrefix = readMessagePrefix(option, childMessagePrefix(messagePrefix, "leave"));
        LocalizedText label = readLabel(option, optionMessagePrefix);
        return List.of(readOption(
                location,
                optionContext(option, 0),
                option,
                LEAVE_OPTION_ID,
                label.isBlank() ? LocalizedText.inline("Leave") : label,
                1000,
                defaultQuestId,
                optionMessagePrefix));
    }

    static ForcedDialogueOption defaultLeaveOption() {
        return readOption(new JsonObject(), LEAVE_OPTION_ID, LocalizedText.inline("Leave"), 1000);
    }

    private static List<ForcedDialogueOption> defaultTheftLeaveOptions() {
        JsonObject trusted = defaultTheftLeaveOption(
                List.of("trusted", "respected", "revered", "royalty"),
                List.of("Put that back!", "Hands off!", "Not yours!"),
                -2,
                0.05D,
                1000);
        JsonObject wary = defaultTheftLeaveOption(
                List.of("neutral", "suspicious"),
                List.of("Thief!", "Hey! Thief!", "Not yours!"),
                -4,
                0.25D,
                1001);
        JsonObject hostile = defaultTheftLeaveOption(
                List.of("hostile", "despised", "feared"),
                List.of("Thief! Put it back!", "Drop it, thief!", "Put it back, now!"),
                -8,
                0.75D,
                1002);
        return List.of(
                readOption(trusted, LEAVE_OPTION_ID, LocalizedText.inline("Leave"), 1000),
                readOption(wary, LEAVE_OPTION_ID, LocalizedText.inline("Leave"), 1001),
                readOption(hostile, LEAVE_OPTION_ID, LocalizedText.inline("Leave"), 1002));
    }

    private static JsonObject defaultTheftLeaveOption(
            List<String> reputationLevels,
            List<String> responses,
            int reputation,
            double aggroChance,
            int order) {
        JsonObject option = new JsonObject();
        option.addProperty("label", "Leave");
        JsonArray responseArray = new JsonArray();
        responses.forEach(responseArray::add);
        option.add("responses", responseArray);
        option.addProperty("reputation", reputation);
        option.addProperty("end_conversation", true);
        option.addProperty("aggro_chance", aggroChance);
        option.addProperty("order", order);
        JsonArray levels = new JsonArray();
        reputationLevels.forEach(levels::add);
        option.add("reputation_levels", levels);
        JsonObject stolenItems = new JsonObject();
        stolenItems.addProperty("destination", "villager_inventory_then_source_container");
        JsonArray successResponses = new JsonArray();
        responses.forEach(successResponses::add);
        stolenItems.add("success_responses", successResponses);
        JsonArray failureResponses = new JsonArray();
        failureResponses.add("Thief! Hand it over.");
        failureResponses.add("Where is it, thief?");
        failureResponses.add("You stole from us!");
        stolenItems.add("failure_responses", failureResponses);
        stolenItems.addProperty("failure_reputation", -5);
        stolenItems.addProperty("failure_aggro", true);
        stolenItems.addProperty("failure_end_conversation", true);
        option.add("take_stolen_items", stolenItems);
        return option;
    }

    private static ForcedDialogueOption readOption(JsonObject option, String id, LocalizedText label, int fallbackOrder) {
        return readOption(null, optionContext(option, fallbackOrder), option, id, label, fallbackOrder, null, "");
    }

    private static ForcedDialogueOption readOption(
            ResourceLocation location,
            String context,
            JsonObject option,
            String id,
            LocalizedText label,
            int fallbackOrder,
            ResourceLocation defaultQuestId,
            String messagePrefix) {
        return new ForcedDialogueOption(
                id,
                label,
                readResponses(option, childMessagePrefix(messagePrefix, "response")),
                readInt(option, "reputation", 0),
                readBoolean(option, "aggro"),
                clampChance(readDouble(option, "aggro_chance", 0.0D)),
                readBoolean(option, "end_conversation", true),
                readInt(option, "order", fallbackOrder),
                readStolenItemReturn(option, messagePrefix),
                readItemPayment(option, messagePrefix),
                VillagerReputationCondition.read(option),
                readSocialAttributeCondition(option),
                DialogueCondition.readList(location, context, option, defaultQuestId),
                readFollowUp(option, defaultQuestId, messagePrefix)
        );
    }

    private static ForcedDialogueFollowUp readFollowUp(JsonObject option, ResourceLocation defaultQuestId, String optionMessagePrefix) {
        JsonElement element = option.get("follow_up");
        if (element == null || !element.isJsonObject()) {
            return ForcedDialogueFollowUp.empty();
        }

        JsonObject followUp = element.getAsJsonObject();
        String messagePrefix = readMessagePrefix(followUp, childMessagePrefix(optionMessagePrefix, "follow_up"));
        ForcedDialogueOption leaveOption = readLeaveOptions(null, followUp, ForcedDialogueTrigger.CONTAINER_OPENED, defaultQuestId, messagePrefix)
                .stream()
                .findFirst()
                .orElse(defaultLeaveOption());
        List<ForcedDialogueOption> options = readOptions(null, followUp, leaveOption, defaultQuestId, messagePrefix);
        String leaveOptionId = leaveOption.id();
        leaveOption = options.stream()
                .filter(candidate -> candidate.id().equals(leaveOptionId))
                .findFirst()
                .orElse(leaveOption);
        return new ForcedDialogueFollowUp(readLines(followUp, messagePrefix), options, leaveOption, List.of(leaveOption));
    }

    private static ForcedDialogueStolenItemReturn readStolenItemReturn(JsonObject option, String messagePrefix) {
        return readStolenItemReturnJson(option, "take_stolen_items")
                .or(() -> readStolenItemReturnJson(option, "return_stolen_items"))
                .map(entry -> new ForcedDialogueStolenItemReturn(
                        readResponseVariants(entry, "success_response", "success_responses", childMessagePrefix(messagePrefix, "take_stolen_items.success")),
                        readResponseVariants(entry, "failure_response", "failure_responses", childMessagePrefix(messagePrefix, "take_stolen_items.failure")),
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

    private static ForcedDialogueItemPayment readItemPayment(JsonObject option, String messagePrefix) {
        return readPaymentJson(option, "take_items")
                .or(() -> readPaymentJson(option, "payment"))
                .map(payment -> new ForcedDialogueItemPayment(
                        payment.removal(),
                        readResponseVariants(payment.entry(), "success_response", "success_responses", childMessagePrefix(messagePrefix, "take_items.success")),
                        readResponseVariants(payment.entry(), "failure_response", "failure_responses", childMessagePrefix(messagePrefix, "take_items.failure")),
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
        if (text == null) {
            return "";
        }
        return VillagerDialogueResources.resolveTemplate(text, templateReplacements(context, extraReplacements));
    }

    static String resolveTemplate(LocalizedText text, ForcedDialogueContext context) {
        return resolveTemplate(text, context, Map.of());
    }

    static String resolveTemplate(LocalizedText text, ForcedDialogueContext context, Map<String, String> extraReplacements) {
        if (text == null) {
            return "";
        }
        Map<String, String> replacements = templateReplacements(context, extraReplacements);
        String fallback = VillagerDialogueResources.resolveTemplate(text.text(), replacements);
        if (!text.key().isBlank() && context != null && context.server() != null && context.random() != null) {
            return VillagerDialogueResources
                    .globalMessage(context.server(), context.random(), text.key(), context.locale(), replacements)
                    .orElse(fallback);
        }
        return fallback;
    }

    private static Map<String, String> templateReplacements(ForcedDialogueContext context, Map<String, String> extraReplacements) {
        Map<String, String> replacements = new HashMap<>();
        if (context != null) {
            replacements.put("villager", context.villagerName());
            replacements.put("player", context.playerName());
            replacements.put("target", context.targetName());
            replacements.put("target_article", VillagerInteractionTextUtil.withIndefiniteArticle(context.targetName()));
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
        }
        replacements.putAll(extraReplacements);
        return replacements;
    }

    private static LocalizedText readLabel(JsonObject option, String messagePrefix) {
        String label = readString(option, "label");
        String labelKey = readString(option, "label_key");
        if (labelKey.isBlank() && !messagePrefix.isBlank() && !label.isBlank()) {
            labelKey = messagePrefix + ".label";
        }
        return labelKey.isBlank() ? LocalizedText.inline(label) : LocalizedText.keyed(labelKey, label);
    }

    private static String readMessagePrefix(JsonObject entry, String fallback) {
        String prefix = readString(entry, "message_prefix");
        if (prefix.isBlank()) {
            prefix = readString(entry, "text_prefix");
        }
        return prefix.isBlank() ? fallback : prefix.trim();
    }

    private static String childMessagePrefix(String parent, String child) {
        if (parent == null || parent.isBlank()) {
            return "";
        }
        String part = messageKeyPart(child);
        return part.isBlank() ? parent : parent + "." + part;
    }

    private static String messageKeyPart(String value) {
        if (value == null) {
            return "";
        }
        return value.trim()
                .toLowerCase(Locale.ROOT)
                .replace(':', '.')
                .replace('/', '.')
                .replaceAll("[^a-z0-9_.-]+", "_")
                .replaceAll("^[._-]+|[._-]+$", "");
    }

    private static <E extends Enum<E>> Optional<E> readEnum(JsonObject entry, String key, Class<E> enumClass) {
        return DatapackJsonReader.readEnum(entry, key, enumClass);
    }

    private static ResourceLocation defaultQuestId(ResourceLocation location, JsonObject entry, ResourceLocation fallback) {
        JsonObject metadata = DatapackJsonReader.readObject(entry, "metadata");
        if (metadata == null) {
            return fallback;
        }
        ResourceLocation questId = QuestIds.parse(DatapackJsonReader.readString(metadata, "quest"), location);
        return questId == null ? fallback : questId;
    }

    private static String readString(JsonObject entry, String key) {
        return DatapackJsonReader.readString(entry, key);
    }

    private static List<String> readStringList(JsonObject entry, String... keys) {
        return DatapackJsonReader.readStringList(entry, keys);
    }

    private static Optional<ResourceLocation> readResourceLocation(JsonObject entry, String key) {
        return DatapackJsonReader.readResourceLocation(entry, key);
    }

    private static Optional<ResourceLocation> parseResourceLocation(String value) {
        return DatapackJsonReader.parseResourceLocation(value);
    }

    private static int readInt(JsonObject entry, String key, int fallback) {
        return DatapackJsonReader.readInt(entry, key, fallback);
    }

    private static double readDouble(JsonObject entry, String key, double fallback) {
        return DatapackJsonReader.readDouble(entry, key, fallback);
    }

    private static boolean readBoolean(JsonObject entry, String key) {
        return DatapackJsonReader.readBoolean(entry, key);
    }

    private static boolean readBoolean(JsonObject entry, String key, boolean fallback) {
        return DatapackJsonReader.readBoolean(entry, key, fallback);
    }

    private static SocialAttributeCondition readSocialAttributeCondition(JsonObject entry) {
        SocialAttributeCondition.Builder builder = SocialAttributeCondition.builder();
        for (VillagerSocialAttribute attribute : VillagerSocialAttribute.values()) {
            String key = attribute.serializedName();
            if (readBoolean(entry, "requires_high_" + key)) {
                builder.min(attribute, 60);
            }
            int minValue = readInt(entry, "min_" + key, Integer.MIN_VALUE);
            if (minValue != Integer.MIN_VALUE) {
                builder.min(attribute, minValue);
            }
            int maxValue = readInt(entry, "max_" + key, Integer.MIN_VALUE);
            if (maxValue != Integer.MIN_VALUE) {
                builder.max(attribute, maxValue);
            }
        }
        return builder.build();
    }

    private static double clampChance(double value) {
        if (!Double.isFinite(value)) {
            return 0.0D;
        }
        return Math.max(0.0D, Math.min(1.0D, value));
    }

    private static String fallbackId(ResourceLocation location, int index) {
        String id = location.getPath().replace('/', '_').replace(".json", "") + "_" + index;
        return DEFAULT_NAMESPACE.equals(location.getNamespace()) ? id : location.getNamespace() + "." + id;
    }

    private static void warnWrongForcedTriggerFamily(ResourceLocation location, JsonObject entry, int index) {
        String trigger = readString(entry, "trigger");
        if (trigger.isBlank()) {
            trigger = readString(entry, "event");
        }
        String normalized = trigger.toLowerCase(Locale.ROOT);
        boolean notificationLike = NOTIFICATION_TRIGGER_PREFIXES.stream().anyMatch(normalized::startsWith);
        if (notificationLike) {
            DatapackDiagnostics.warnInvalidTrigger(
                    location,
                    "forced dialogue",
                    entryContext(entry, index),
                    trigger,
                    "Use forced dialogue triggers: container_theft, container_opened, container_broken, retaliation_started, low_guts_rally, player_item_proximity, trade_refresh, or quest.");
        }
    }

    private static String entryContext(JsonObject entry, int index) {
        String id = readString(entry, "id");
        return id.isBlank() ? "entry[" + index + "]" : "entry \"" + id + "\"";
    }

    private static String optionContext(JsonObject option, int index) {
        String id = readString(option, "id");
        return id.isBlank() ? "option[" + index + "]" : "option \"" + id + "\"";
    }

    private record CachedForcedDialogues(
            MinecraftServer server,
            List<ForcedDialogueDefinition> definitions,
            Map<ForcedDialogueTrigger, List<ForcedDialogueDefinition>> byTrigger,
            List<ForcedDialogueDefinition> playerItemProximityCandidates) {
        private static CachedForcedDialogues empty() {
            return new CachedForcedDialogues(null, List.of(), Map.of(), List.of());
        }

        private static CachedForcedDialogues create(MinecraftServer server, List<ForcedDialogueDefinition> loaded) {
            List<ForcedDialogueDefinition> definitions = loaded.stream()
                    .sorted(CANDIDATE_ORDER)
                    .toList();
            EnumMap<ForcedDialogueTrigger, List<ForcedDialogueDefinition>> byTrigger = new EnumMap<>(ForcedDialogueTrigger.class);
            for (ForcedDialogueTrigger trigger : ForcedDialogueTrigger.values()) {
                List<ForcedDialogueDefinition> matches = definitions.stream()
                        .filter(definition -> definition.trigger() == trigger)
                        .toList();
                if (!matches.isEmpty()) {
                    byTrigger.put(trigger, matches);
                }
            }
            List<ForcedDialogueDefinition> playerItemProximityCandidates = byTrigger
                    .getOrDefault(ForcedDialogueTrigger.PLAYER_ITEM_PROXIMITY, List.of())
                    .stream()
                    .filter(definition -> definition.lootTables().isEmpty())
                    .filter(definition -> definition.targetEntityTypes().isEmpty())
                    .filter(ForcedDialogueDefinition::hasPlayerItemCondition)
                    .toList();
            return new CachedForcedDialogues(
                    server,
                    definitions,
                    Map.copyOf(byTrigger),
                    playerItemProximityCandidates);
        }
    }

    private record PaymentJson(
            JsonObject entry,
            VillagerInventoryItemRemoval removal) {
    }

    public enum ForcedDialogueTrigger {
        CONTAINER_THEFT,
        CONTAINER_OPENED,
        CONTAINER_BROKEN,
        RETALIATION_STARTED,
        LOW_GUTS_RALLY,
        PLAYER_ITEM_PROXIMITY,
        TRADE_REFRESH,
        QUEST
    }

    public enum ForcedDialogueOutputMode {
        FORCED_DIALOGUE,
        CHAT
    }

    public enum ForcedDialogueItemDestination {
        DISCARD,
        VILLAGER_INVENTORY,
        VILLAGER_INVENTORY_THEN_SOURCE_CONTAINER,
        SOURCE_CONTAINER,
        DROP_AT_VILLAGER,
        DROP_AT_CONTAINER
    }

    public record LocalizedText(String text, String key) {
        private static final LocalizedText EMPTY = new LocalizedText("", "");

        public LocalizedText {
            text = text == null ? "" : text;
            key = key == null ? "" : key.trim();
        }

        public static LocalizedText inline(String text) {
            if (text == null || text.isBlank()) {
                return EMPTY;
            }
            return new LocalizedText(text, "");
        }

        public static LocalizedText keyed(String key, String fallback) {
            if ((key == null || key.isBlank()) && (fallback == null || fallback.isBlank())) {
                return EMPTY;
            }
            return new LocalizedText(fallback, key);
        }

        public boolean isBlank() {
            return this.text.isBlank() && this.key.isBlank();
        }
    }

    public record ForcedDialogueDefinition(
            String id,
            ResourceLocation source,
            ForcedDialogueTrigger trigger,
            ForcedDialogueOutput output,
            List<LocalizedText> lines,
            boolean initiateDialogue,
            boolean aggroImmediately,
            boolean forceCameraTowardsVillager,
            boolean requiresLineOfSight,
            double witnessRadius,
            double chance,
            int reputationDelta,
            int priority,
            int minRecentContainerThefts,
            int maxRecentContainerThefts,
            int minRecentRetaliations,
            int maxRecentRetaliations,
            Set<ResourceLocation> lootTables,
            Set<ResourceLocation> targetEntityTypes,
            Set<VillagerProfession> witnessProfessions,
            VillagerEquipmentCondition witnessEquipmentCondition,
            VillagerPlayerItemCondition playerItemCondition,
            VillagerReputationCondition reputationCondition,
            List<ForcedDialogueOption> options,
            ForcedDialogueOption leaveOption,
            List<ForcedDialogueOption> leaveOptions) {
        public LocalizedText selectLine(RandomSource random) {
            if (this.lines.isEmpty()) {
                return LocalizedText.EMPTY;
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
            if (!this.witnessProfessions.isEmpty() && !this.witnessProfessions.contains(villager.getVillagerData().getProfession())) {
                return false;
            }
            return this.witnessEquipmentCondition.matches(villager);
        }

        public boolean matchesRecentContainerThefts(int count) {
            return count >= this.minRecentContainerThefts && count <= this.maxRecentContainerThefts;
        }

        public boolean matchesRecentRetaliations(int count) {
            return count >= this.minRecentRetaliations && count <= this.maxRecentRetaliations;
        }

        public boolean hasPlayerItemCondition() {
            return !this.playerItemCondition.isEmpty();
        }

        public boolean matchesPlayerItem(net.minecraft.world.entity.player.Player player) {
            return this.playerItemCondition.matches(player);
        }

        public Map<String, String> playerItemReplacements(net.minecraft.world.entity.player.Player player) {
            return this.playerItemCondition.replacements(player);
        }

        public boolean matchesReputation(int reputation, VillagerReputationLevel level) {
            return this.reputationCondition.matches(reputation, level);
        }
    }

    public record ForcedDialogueOutput(
            ForcedDialogueOutputMode mode,
            double radius) {
        private static ForcedDialogueOutput forcedDialogue() {
            return new ForcedDialogueOutput(ForcedDialogueOutputMode.FORCED_DIALOGUE, 0.0D);
        }
    }

    public record ForcedDialogueOption(
            String id,
            LocalizedText label,
            List<LocalizedText> responses,
            int reputationDelta,
            boolean aggro,
            double aggroChance,
            boolean endConversation,
            int order,
            ForcedDialogueStolenItemReturn stolenItemReturn,
            ForcedDialogueItemPayment itemPayment,
            VillagerReputationCondition reputationCondition,
            SocialAttributeCondition socialAttributeCondition,
            List<DialogueCondition> conditions,
            ForcedDialogueFollowUp followUp) {
        public LocalizedText selectResponse(RandomSource random) {
            if (this.responses.isEmpty()) {
                return LocalizedText.EMPTY;
            }
            return this.responses.get(random.nextInt(this.responses.size()));
        }
    }

    public record ForcedDialogueFollowUp(
            List<LocalizedText> lines,
            List<ForcedDialogueOption> options,
            ForcedDialogueOption leaveOption,
            List<ForcedDialogueOption> leaveOptions) {
        private static final ForcedDialogueFollowUp EMPTY =
                new ForcedDialogueFollowUp(List.of(), List.of(), null, List.of());

        static ForcedDialogueFollowUp empty() {
            return EMPTY;
        }

        public boolean isEmpty() {
            return this == EMPTY || this.options.isEmpty();
        }

        public LocalizedText selectLine(RandomSource random) {
            return selectResponse(this.lines, random);
        }
    }

    public record ForcedDialogueStolenItemReturn(
            List<LocalizedText> successResponses,
            List<LocalizedText> failureResponses,
            int successReputationDelta,
            int failureReputationDelta,
            boolean failureAggro,
            boolean failureEndConversation,
            ForcedDialogueItemDestination destination,
            ForcedDialogueItemDestination overflowDestination,
            boolean requireSpace) {
        private static final ForcedDialogueStolenItemReturn EMPTY = new ForcedDialogueStolenItemReturn(
                List.of(),
                List.of(),
                0,
                0,
                false,
                false,
                ForcedDialogueItemDestination.VILLAGER_INVENTORY_THEN_SOURCE_CONTAINER,
                null,
                true);

        static ForcedDialogueStolenItemReturn empty() {
            return EMPTY;
        }

        public boolean isEmpty() {
            return this == EMPTY;
        }

        public LocalizedText selectSuccessResponse(RandomSource random) {
            return selectResponse(this.successResponses, random);
        }

        public LocalizedText selectFailureResponse(RandomSource random) {
            return selectResponse(this.failureResponses, random);
        }
    }

    public record ForcedDialogueItemPayment(
            VillagerInventoryItemRemoval removal,
            List<LocalizedText> successResponses,
            List<LocalizedText> failureResponses,
            int successReputationDelta,
            int failureReputationDelta,
            boolean failureAggro,
            boolean failureEndConversation,
            ForcedDialogueItemDestination destination,
            ForcedDialogueItemDestination overflowDestination,
            boolean requireSpace) {
        private static final ForcedDialogueItemPayment EMPTY = new ForcedDialogueItemPayment(
                VillagerInventoryItemRemoval.empty(),
                List.of(),
                List.of(),
                0,
                0,
                false,
                false,
                ForcedDialogueItemDestination.DISCARD,
                null,
                true);

        static ForcedDialogueItemPayment empty() {
            return EMPTY;
        }

        public boolean isEmpty() {
            return this.removal.isEmpty();
        }

        public LocalizedText selectSuccessResponse(RandomSource random) {
            return selectResponse(this.successResponses, random);
        }

        public LocalizedText selectFailureResponse(RandomSource random) {
            return selectResponse(this.failureResponses, random);
        }
    }

    private static LocalizedText selectResponse(List<LocalizedText> responses, RandomSource random) {
        if (responses.isEmpty()) {
            return LocalizedText.EMPTY;
        }
        return responses.get(random.nextInt(responses.size()));
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
            int z,
            MinecraftServer server,
            RandomSource random,
            String locale) {
        public ForcedDialogueContext(
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
            this(
                    villagerName,
                    playerName,
                    targetName,
                    targetKind,
                    targetType,
                    itemName,
                    itemId,
                    itemCount,
                    itemStack,
                    itemList,
                    containerName,
                    lootTable,
                    priorContainerThefts,
                    priorRetaliations,
                    x,
                    y,
                    z,
                    null,
                    null,
                    VillagerLocale.DEFAULT_LOCALE);
        }

        public ForcedDialogueContext {
            villagerName = villagerName == null ? "" : villagerName;
            playerName = playerName == null ? "" : playerName;
            targetName = targetName == null ? "" : targetName;
            targetKind = targetKind == null ? "" : targetKind;
            targetType = targetType == null ? "" : targetType;
            itemName = itemName == null ? "" : itemName;
            itemId = itemId == null ? "" : itemId;
            itemStack = itemStack == null ? "" : itemStack;
            itemList = itemList == null ? "" : itemList;
            containerName = containerName == null ? "" : containerName;
            lootTable = lootTable == null ? "" : lootTable;
            locale = VillagerLocale.normalize(locale);
        }
    }
}
