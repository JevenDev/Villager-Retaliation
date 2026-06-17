package com.jvn.villagerretaliation.quest;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.action.VillagerActionDefinition;
import com.jvn.villagerretaliation.dialogue.DialogueCondition;
import com.jvn.villagerretaliation.dialogue.DialogueEntryMetadata;
import com.jvn.villagerretaliation.skill.VillagerSkill;
import com.jvn.villagerretaliation.util.DatapackDiagnostics;
import com.jvn.villagerretaliation.util.DatapackJsonReader;
import com.jvn.villagerretaliation.util.DatapackResourceLoader;
import com.jvn.villagerretaliation.util.VillagerProfessionUtil;
import com.jvn.villagerretaliation.village.VillageEventMemory;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.Level;

public final class VillagerQuestResources {
    private static final String RESOURCE_ROOT = "quests";
    private static final int DEFAULT_STRUCTURE_SEARCH_RADIUS = 256;
    private static final int DEFAULT_DISCOVERY_RADIUS = 128;

    private static volatile CachedQuests cachedQuests = new CachedQuests(null, Map.of());

    private VillagerQuestResources() {
    }

    public static void warm(MinecraftServer server) {
        quests(server);
    }

    public static void clearCache() {
        cachedQuests = new CachedQuests(null, Map.of());
    }

    public static Collection<QuestDefinition> quests(MinecraftServer server) {
        return load(server).values();
    }

    public static Optional<QuestDefinition> quest(MinecraftServer server, ResourceLocation id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(load(server).get(id));
    }

    private static Map<ResourceLocation, QuestDefinition> load(MinecraftServer server) {
        CachedQuests current = cachedQuests;
        if (current.server() == server) {
            return current.quests();
        }

        synchronized (VillagerQuestResources.class) {
            current = cachedQuests;
            if (current.server() == server) {
                return current.quests();
            }

            Map<ResourceLocation, QuestDefinition> quests = read(server);
            cachedQuests = new CachedQuests(server, quests);
            return quests;
        }
    }

    private static Map<ResourceLocation, QuestDefinition> read(MinecraftServer server) {
        Map<ResourceLocation, QuestDefinition> quests = new LinkedHashMap<>();
        Map<ResourceLocation, ResourceLocation> sources = new LinkedHashMap<>();
        List<LoadedQuestResource> resources = DatapackResourceLoader.jsonResources(server, RESOURCE_ROOT).stream()
                .map(resource -> DatapackResourceLoader.readObject(resource.location(), "quest", resource.resource())
                        .map(root -> new LoadedQuestResource(resource, root)))
                .flatMap(Optional::stream)
                .toList();
        boolean replacementMode = resources.stream()
                .anyMatch(resource -> DatapackJsonReader.readBoolean(resource.root(), "replace"));
        for (LoadedQuestResource resource : resources) {
            if (replacementMode
                    && isBuiltInModResource(resource.resource())
                    && !DatapackJsonReader.readBoolean(resource.root(), "replace")) {
                continue;
            }
            readFile(resource.resource().location(), resource.root(), quests, sources, replacementMode);
        }
        return Map.copyOf(quests);
    }

    private static void readFile(
            ResourceLocation location,
            JsonObject root,
            Map<ResourceLocation, QuestDefinition> quests,
            Map<ResourceLocation, ResourceLocation> sources,
            boolean replacementMode) {
        ResourceLocation fallbackId = fallbackQuestId(location);
        if (DatapackJsonReader.readBoolean(root, "replace")) {
            if (!replacementMode) {
                quests.clear();
                sources.clear();
            }
            if (isControlOnly(root, "replace", "metadata")) {
                return;
            }
        }
        if (DatapackJsonReader.readBoolean(root, "remove")) {
            ResourceLocation removeId = DatapackJsonReader.readResourceLocation(root, "id").orElse(fallbackId);
            if (removeId != null) {
                quests.remove(removeId);
                sources.remove(removeId);
            }
            return;
        }
        QuestDefinition definition = readQuest(location, root, fallbackId);
        if (definition == null) {
            return;
        }
        ResourceLocation previous = sources.put(definition.id(), location);
        if (previous != null) {
            DatapackDiagnostics.warnDuplicateId(location, "quest", definition.id().toString(), previous);
        }
        quests.put(definition.id(), definition);
    }

    private static boolean isBuiltInModResource(DatapackResourceLoader.JsonResource resource) {
        return VillagerRetaliation.MOD_ID.equals(resource.location().getNamespace())
                && resource.isFromPack(VillagerRetaliation.MOD_ID);
    }

    private record LoadedQuestResource(DatapackResourceLoader.JsonResource resource, JsonObject root) {
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

    private static QuestDefinition readQuest(ResourceLocation location, JsonObject root, ResourceLocation fallbackId) {
        ResourceLocation id = DatapackJsonReader.readResourceLocation(root, "id").orElse(fallbackId);
        if (id == null) {
            return null;
        }

        JsonObject display = DatapackJsonReader.readObject(root, "display");
        String title = display == null ? "" : DatapackJsonReader.readString(display, "title");
        String description = display == null ? "" : DatapackJsonReader.readString(display, "description");
        String titleKey = display == null ? "" : DatapackJsonReader.readString(display, "title_key");
        String descriptionKey = display == null ? "" : DatapackJsonReader.readString(display, "description_key");
        ResourceLocation parent = DatapackJsonReader.readResourceLocation(root, "parent").orElse(null);

        return new QuestDefinition(
                id,
                title,
                description,
                titleKey,
                descriptionKey,
                DatapackJsonReader.readString(root, "questline"),
                parent,
                readOffer(location, root),
                readTarget(root),
                readObjectives(location, root, id),
                readRules(location, root, id),
                readTracker(root),
                readTriggers(location, root, id),
                readRewards(root),
                readDialogue(root),
                DialogueEntryMetadata.read(location, "quest", "quest", root),
                readLinks(root)
        );
    }

    private static QuestDefinition.Offer readOffer(ResourceLocation location, JsonObject root) {
        JsonObject offer = DatapackJsonReader.readObject(root, "offer");
        if (offer == null) {
            return QuestDefinition.Offer.any();
        }

        Set<VillagerProfession> professions = new LinkedHashSet<>();
        for (String value : DatapackJsonReader.readStringList(offer, "professions")) {
            Optional<VillagerProfession> profession = VillagerProfessionUtil.parse(value);
            if (profession.isPresent()) {
                professions.add(profession.get());
            } else {
                DatapackDiagnostics.warnUnknownProfession(location, "quest offer", value);
            }
        }

        int minLevel = readVillagerLevel(offer, "min_villager_level", 1);
        return new QuestDefinition.Offer(
                professions,
                minLevel,
                readSkillRequirements(offer)
        );
    }

    private static Map<VillagerSkill, Integer> readSkillRequirements(JsonObject offer) {
        JsonElement element = offer.get("skills");
        if (element == null || element.isJsonNull()) {
            return Map.of();
        }

        Map<VillagerSkill, Integer> skills = new LinkedHashMap<>();
        if (!element.isJsonObject()) {
            return Map.of();
        }

        for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
            VillagerSkill skill = VillagerSkill.bySerializedName(entry.getKey());
            if (skill == null || !entry.getValue().isJsonObject()) {
                continue;
            }
            int min = DatapackJsonReader.readInt(entry.getValue().getAsJsonObject(), "min", 1);
            skills.put(skill, Math.max(1, min));
        }
        return Map.copyOf(skills);
    }

    private static QuestDefinition.Target readTarget(JsonObject root) {
        JsonObject target = DatapackJsonReader.readObject(root, "target");
        ResourceLocation structure = target == null
                ? null
                : DatapackJsonReader.readResourceLocation(target, "structure").orElse(null);
        ResourceKey<Level> dimension = target == null
                ? null
                : readDimension(target);
        List<String> pieces = target == null
                ? List.of()
                : DatapackJsonReader.readStringList(target, "pieces");
        int searchRadius = target == null
                ? DEFAULT_STRUCTURE_SEARCH_RADIUS
                : DatapackJsonReader.readInt(target, "search_radius", DEFAULT_STRUCTURE_SEARCH_RADIUS);
        int discoveryRadius = target == null
                ? DEFAULT_DISCOVERY_RADIUS
                : DatapackJsonReader.readInt(target, "discovery_radius", DEFAULT_DISCOVERY_RADIUS);
        ResourceLocation proofItem = target == null
                ? null
                : DatapackJsonReader.readResourceLocation(target, "proof_item").orElse(null);

        return new QuestDefinition.Target(structure, dimension, pieces, searchRadius, discoveryRadius, proofItem);
    }

    private static List<QuestDefinition.Objective> readObjectives(
            ResourceLocation location,
            JsonObject root,
            ResourceLocation defaultQuestId) {
        JsonElement element = root.get("objectives");
        if (element == null || element.isJsonNull()) {
            return List.of();
        }
        if (!element.isJsonArray()) {
            DatapackDiagnostics.warnInvalidDialogueCondition(location, "quest objectives", "objectives must be an array.");
            return List.of();
        }

        List<QuestDefinition.Objective> objectives = new ArrayList<>();
        int index = 0;
        for (JsonElement child : element.getAsJsonArray()) {
            if (child.isJsonObject()) {
                readObjective(location, child.getAsJsonObject(), index, defaultQuestId).ifPresent(objectives::add);
            }
            index++;
        }
        return List.copyOf(objectives);
    }

    private static Optional<QuestDefinition.Objective> readObjective(
            ResourceLocation location,
            JsonObject entry,
            int index,
            ResourceLocation defaultQuestId) {
        String id = firstNonBlank(DatapackJsonReader.readString(entry, "id"), "objective_" + index);
        String context = "quest objective \"" + id + "\"";
        QuestDefinition.ObjectiveType type = QuestDefinition.ObjectiveType.bySerializedName(
                DatapackJsonReader.readString(entry, "type"));
        if (type == null) {
            DatapackDiagnostics.warnInvalidDialogueCondition(location, context, "unknown objective type.");
            return Optional.empty();
        }

        ResourceLocation structure = DatapackJsonReader.readResourceLocation(entry, "structure").orElse(null);
        ResourceKey<Level> dimension = readDimension(entry);
        BlockPos objectiveLocation = readLocation(entry);
        ResourceLocation item = DatapackJsonReader.readResourceLocation(entry, "item").orElse(null);
        EntitySelectors entitySelectors = readEntitySelectors(location, context, entry);
        List<DialogueCondition> conditions = DialogueCondition.readList(location, context, entry, defaultQuestId);
        if (type == QuestDefinition.ObjectiveType.STRUCTURE_VISIT && structure == null) {
            DatapackDiagnostics.warnInvalidDialogueCondition(location, context, "structure_visit objective must define structure.");
            return Optional.empty();
        }
        if (type == QuestDefinition.ObjectiveType.LOCATION_VISIT && objectiveLocation == null) {
            DatapackDiagnostics.warnInvalidDialogueCondition(location, context, "location_visit objective must define x, y, and z.");
            return Optional.empty();
        }
        if (type == QuestDefinition.ObjectiveType.ITEM_CHECK && item == null) {
            DatapackDiagnostics.warnInvalidDialogueCondition(location, context, "item_check objective must define item.");
            return Optional.empty();
        }
        if (type == QuestDefinition.ObjectiveType.MOB_KILL && entitySelectors.isEmpty()) {
            DatapackDiagnostics.warnInvalidDialogueCondition(location, context, "mob_kill objective must define entity, entities, entity_tag, or entity_tags.");
            return Optional.empty();
        }
        if (type == QuestDefinition.ObjectiveType.CONDITION && conditions.isEmpty()) {
            DatapackDiagnostics.warnInvalidDialogueCondition(location, context, "condition objective must define conditions.");
            return Optional.empty();
        }

        return Optional.of(new QuestDefinition.Objective(
                id,
                type,
                DatapackJsonReader.readBoolean(entry, "optional", false),
                structure,
                dimension,
                objectiveLocation,
                DatapackJsonReader.readInt(entry, "radius", 8),
                DatapackJsonReader.readStringList(entry, "pieces"),
                DatapackJsonReader.readInt(entry, "search_radius", DEFAULT_STRUCTURE_SEARCH_RADIUS),
                DatapackJsonReader.readInt(entry, "discovery_radius", DEFAULT_DISCOVERY_RADIUS),
                item,
                entitySelectors.entityTypes(),
                entitySelectors.entityTags(),
                DatapackJsonReader.readInt(entry, "count", 1),
                DatapackJsonReader.readBoolean(entry, "consume", true),
                readObjectiveItemRequirements(entry),
                conditions,
                readObjectiveTracker(entry)));
    }

    private static BlockPos readLocation(JsonObject entry) {
        Integer x = DatapackJsonReader.readNullableInt(entry, "x");
        Integer y = DatapackJsonReader.readNullableInt(entry, "y");
        Integer z = DatapackJsonReader.readNullableInt(entry, "z");
        if (x == null || y == null || z == null) {
            JsonElement pos = entry.get("pos");
            if (pos != null && pos.isJsonArray() && pos.getAsJsonArray().size() >= 3) {
                x = DatapackJsonReader.readInt(pos.getAsJsonArray().get(0), Integer.MIN_VALUE);
                y = DatapackJsonReader.readInt(pos.getAsJsonArray().get(1), Integer.MIN_VALUE);
                z = DatapackJsonReader.readInt(pos.getAsJsonArray().get(2), Integer.MIN_VALUE);
                if (x == Integer.MIN_VALUE || y == Integer.MIN_VALUE || z == Integer.MIN_VALUE) {
                    return null;
                }
            }
        }
        return x == null || y == null || z == null ? null : new BlockPos(x, y, z);
    }

    private static EntitySelectors readEntitySelectors(ResourceLocation location, String context, JsonObject entry) {
        Set<ResourceLocation> entityTypes = new LinkedHashSet<>();
        Set<ResourceLocation> entityTags = new LinkedHashSet<>();
        for (String value : DatapackJsonReader.readStringList(entry, "entity", "entities")) {
            readEntitySelector(location, context, value, entityTypes, entityTags);
        }
        for (String value : DatapackJsonReader.readStringList(entry, "entity_tag", "entity_tags")) {
            readEntityTag(location, context, value, entityTags);
        }
        return new EntitySelectors(Set.copyOf(entityTypes), Set.copyOf(entityTags));
    }

    private static void readEntitySelector(
            ResourceLocation location,
            String context,
            String value,
            Set<ResourceLocation> entityTypes,
            Set<ResourceLocation> entityTags) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (value.startsWith("#")) {
            readEntityTag(location, context, value.substring(1), entityTags);
            return;
        }
        ResourceLocation entityType = ResourceLocation.tryParse(value);
        if (entityType == null) {
            DatapackDiagnostics.warnInvalidDialogueCondition(location, context, "mob_kill entity \"" + value + "\" is not a valid resource location.");
            return;
        }
        entityTypes.add(entityType);
    }

    private static void readEntityTag(
            ResourceLocation location,
            String context,
            String value,
            Set<ResourceLocation> entityTags) {
        if (value == null || value.isBlank()) {
            return;
        }
        String normalized = value.startsWith("#") ? value.substring(1) : value;
        ResourceLocation tag = ResourceLocation.tryParse(normalized);
        if (tag == null) {
            DatapackDiagnostics.warnInvalidDialogueCondition(location, context, "mob_kill entity tag \"" + value + "\" is not a valid resource location.");
            return;
        }
        entityTags.add(tag);
    }

    private static QuestDefinition.ItemRequirements readObjectiveItemRequirements(JsonObject entry) {
        OptionalInt minEnchantmentLevel = readOptionalInt(entry, "min_enchantment_level");
        OptionalInt maxEnchantmentLevel = readOptionalInt(entry, "max_enchantment_level");
        List<QuestDefinition.EnchantmentRequirement> enchantments = new ArrayList<>();
        readEnchantmentRequirements(entry.get("enchantment"), minEnchantmentLevel, maxEnchantmentLevel, enchantments);
        readEnchantmentRequirements(entry.get("enchantments"), minEnchantmentLevel, maxEnchantmentLevel, enchantments);
        return new QuestDefinition.ItemRequirements(
                enchantments,
                readOptionalInt(entry, "min_durability"),
                readOptionalInt(entry, "max_durability"),
                readOptionalInt(entry, "min_durability_percent"),
                readOptionalInt(entry, "max_durability_percent"),
                readCustomData(entry));
    }

    private static void readEnchantmentRequirements(
            JsonElement element,
            OptionalInt fallbackMinLevel,
            OptionalInt fallbackMaxLevel,
            List<QuestDefinition.EnchantmentRequirement> enchantments) {
        if (element == null) {
            return;
        }
        if (element.isJsonPrimitive()) {
            readEnchantmentRequirement(element.getAsString(), fallbackMinLevel, fallbackMaxLevel)
                    .ifPresent(enchantments::add);
            return;
        }
        if (element.isJsonObject()) {
            readEnchantmentRequirement(element.getAsJsonObject(), fallbackMinLevel, fallbackMaxLevel)
                    .ifPresent(enchantments::add);
            return;
        }
        if (!element.isJsonArray()) {
            return;
        }
        for (JsonElement child : element.getAsJsonArray()) {
            if (child.isJsonPrimitive()) {
                readEnchantmentRequirement(child.getAsString(), fallbackMinLevel, fallbackMaxLevel)
                        .ifPresent(enchantments::add);
            } else if (child.isJsonObject()) {
                readEnchantmentRequirement(child.getAsJsonObject(), fallbackMinLevel, fallbackMaxLevel)
                        .ifPresent(enchantments::add);
            }
        }
    }

    private static Optional<QuestDefinition.EnchantmentRequirement> readEnchantmentRequirement(
            JsonObject entry,
            OptionalInt fallbackMinLevel,
            OptionalInt fallbackMaxLevel) {
        String id = firstNonBlank(
                DatapackJsonReader.readString(entry, "id"),
                firstNonBlank(
                        DatapackJsonReader.readString(entry, "enchantment"),
                        DatapackJsonReader.readString(entry, "name")));
        OptionalInt minLevel = firstPresent(readOptionalInt(entry, "min_level"), fallbackMinLevel);
        OptionalInt maxLevel = firstPresent(readOptionalInt(entry, "max_level"), fallbackMaxLevel);
        return readEnchantmentRequirement(id, minLevel, maxLevel);
    }

    private static Optional<QuestDefinition.EnchantmentRequirement> readEnchantmentRequirement(
            String id,
            OptionalInt minLevel,
            OptionalInt maxLevel) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        ResourceLocation location = ResourceLocation.tryParse(id.contains(":") ? id : "minecraft:" + id);
        if (location == null) {
            return Optional.empty();
        }
        return Optional.of(new QuestDefinition.EnchantmentRequirement(location, minLevel, maxLevel));
    }

    private static OptionalInt readOptionalInt(JsonObject entry, String key) {
        return DatapackJsonReader.readOptionalInt(entry, key)
                .map(OptionalInt::of)
                .orElseGet(OptionalInt::empty);
    }

    private static OptionalInt firstPresent(OptionalInt first, OptionalInt second) {
        return first.isPresent() ? first : second;
    }

    private static CompoundTag readCustomData(JsonObject entry) {
        JsonElement customData = entry.get("custom_data");
        if (customData == null) {
            customData = entry.get("nbt");
        }
        if (customData == null || !customData.isJsonObject()) {
            return null;
        }
        return readCompoundTag(customData.getAsJsonObject());
    }

    private static CompoundTag readCompoundTag(JsonObject object) {
        CompoundTag tag = new CompoundTag();
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            Tag child = readTag(entry.getValue());
            if (child != null) {
                tag.put(entry.getKey(), child);
            }
        }
        return tag;
    }

    private static Tag readTag(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (element.isJsonObject()) {
            return readCompoundTag(element.getAsJsonObject());
        }
        if (element.isJsonArray()) {
            ListTag list = new ListTag();
            for (JsonElement child : element.getAsJsonArray()) {
                Tag childTag = readTag(child);
                if (childTag != null) {
                    try {
                        list.add(childTag);
                    } catch (UnsupportedOperationException ignored) {
                        // NBT lists require one contained tag type; incompatible JSON entries are ignored.
                    }
                }
            }
            return list;
        }
        if (!element.isJsonPrimitive()) {
            return null;
        }
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (primitive.isBoolean()) {
            return ByteTag.valueOf(primitive.getAsBoolean());
        }
        if (primitive.isNumber()) {
            String value = primitive.getAsString();
            if (value.contains(".") || value.contains("e") || value.contains("E")) {
                return DoubleTag.valueOf(primitive.getAsDouble());
            }
            try {
                return IntTag.valueOf(Integer.parseInt(value));
            } catch (NumberFormatException ignored) {
                try {
                    return LongTag.valueOf(primitive.getAsLong());
                } catch (NumberFormatException ignoredToo) {
                    return DoubleTag.valueOf(primitive.getAsDouble());
                }
            }
        }
        return StringTag.valueOf(primitive.getAsString());
    }

    private static ResourceKey<Level> readDimension(JsonObject object) {
        return DatapackJsonReader.readResourceLocation(object, "dimension")
                .map(id -> ResourceKey.create(Registries.DIMENSION, id))
                .orElse(null);
    }

    private static QuestDefinition.ObjectiveTracker readObjectiveTracker(JsonObject objective) {
        JsonObject tracker = DatapackJsonReader.readObject(objective, "tracker");
        if (tracker == null) {
            return QuestDefinition.ObjectiveTracker.EMPTY;
        }
        String text = DatapackJsonReader.readString(tracker, "text");
        String completeText = DatapackJsonReader.readString(tracker, "complete_text");
        String textKey = DatapackJsonReader.readString(tracker, "text_key");
        String completeTextKey = DatapackJsonReader.readString(tracker, "complete_text_key");
        boolean showProgress = DatapackJsonReader.readBoolean(tracker, "show_progress", true);
        float progress = (float) DatapackJsonReader.readDouble(tracker, "progress", -1.0D);
        Map<String, String> metadata = readStringMap(DatapackJsonReader.readObject(tracker, "metadata"));
        return new QuestDefinition.ObjectiveTracker(text, completeText, textKey, completeTextKey, showProgress, progress, metadata);
    }

    private static QuestDefinition.Rewards readRewards(JsonObject root) {
        JsonObject rewards = DatapackJsonReader.readObject(root, "rewards");
        if (rewards == null) {
            return QuestDefinition.Rewards.EMPTY;
        }
        return new QuestDefinition.Rewards(
                Math.max(0, DatapackJsonReader.readInt(rewards, "experience", 0)),
                DatapackJsonReader.readInt(rewards, "reputation", 0),
                DatapackJsonReader.readInt(rewards, "gossip_reputation", 0),
                DatapackJsonReader.readResourceLocation(rewards, "loot_table").orElse(null),
                readMemoryEvent(rewards)
        );
    }

    private static QuestDefinition.Rules readRules(ResourceLocation location, JsonObject root, ResourceLocation defaultQuestId) {
        JsonObject rules = DatapackJsonReader.readObject(root, "rules");
        if (rules == null) {
            return QuestDefinition.Rules.DEFAULT;
        }

        boolean repeatable = DatapackJsonReader.readBoolean(rules, "repeatable", false);
        int maxStarts = Math.max(0, DatapackJsonReader.readInt(rules, "max_starts", repeatable ? 0 : 1));
        int maxCompletions = Math.max(0, DatapackJsonReader.readInt(rules, "max_completions", repeatable ? 0 : 1));
        return new QuestDefinition.Rules(
                repeatable,
                DatapackJsonReader.readBoolean(rules, "locked_to_villager", true),
                DatapackJsonReader.readBoolean(rules, "cross_villager_compatible", false),
                maxStarts,
                maxCompletions,
                QuestDefinition.CompletionScope.bySerializedName(
                        DatapackJsonReader.readString(rules, "completion_scope", "scope")),
                DatapackJsonReader.readDurationTicks(rules, "completion_cooldown", 0L),
                QuestDefinition.AbandonmentMode.bySerializedName(
                        DatapackJsonReader.readString(rules, "abandonment")),
                DatapackJsonReader.readDurationTicks(rules, "abandonment_cooldown", 0L),
                DatapackJsonReader.readBoolean(rules, "consume_on_completion", false),
                DatapackJsonReader.readBoolean(rules, "consume_on_abandonment", false),
                readActiveState(location, rules, defaultQuestId),
                readExpiration(location, rules, defaultQuestId)
        );
    }

    private static QuestDefinition.ActiveState readActiveState(
            ResourceLocation location,
            JsonObject rules,
            ResourceLocation defaultQuestId) {
        JsonObject active = DatapackJsonReader.readObject(rules, "active");
        List<DialogueCondition> conditions = active == null
                ? List.of()
                : DialogueCondition.readList(location, "quest active state", active, defaultQuestId);
        boolean hideWhenUnmet = active == null
                ? false
                : DatapackJsonReader.readBoolean(active, "hide_when_unmet", false);
        boolean pauseProgressWhenUnmet = active == null
                ? true
                : DatapackJsonReader.readBoolean(active, "pause_progress_when_unmet", true);
        return new QuestDefinition.ActiveState(conditions, hideWhenUnmet, pauseProgressWhenUnmet);
    }

    private static QuestDefinition.Expiration readExpiration(
            ResourceLocation location,
            JsonObject rules,
            ResourceLocation defaultQuestId) {
        JsonObject expiration = DatapackJsonReader.readObject(rules, "expiration");
        if (expiration == null) {
            return QuestDefinition.Expiration.DEFAULT;
        }

        long afterTicks = DatapackJsonReader.readDurationTicks(expiration, "after", 0L);
        List<DialogueCondition> conditions = DialogueCondition.readList(location, "quest expiration", expiration, defaultQuestId);
        return new QuestDefinition.Expiration(
                afterTicks,
                conditions,
                DatapackJsonReader.readBoolean(expiration, "consume", false),
                DatapackJsonReader.readBoolean(expiration, "allow_repickup", true),
                DatapackJsonReader.readBoolean(expiration, "notify", true),
                firstNonBlank(DatapackJsonReader.readString(expiration, "notification"), "quest.expired"),
                firstNonBlank(DatapackJsonReader.readString(expiration, "text"), "Quest expired: {quest}"),
                DatapackJsonReader.readString(expiration, "text_key", "notification_text_key")
        );
    }

    private static QuestDefinition.Tracker readTracker(JsonObject root) {
        JsonObject tracker = DatapackJsonReader.readObject(root, "tracker");
        if (tracker == null) {
            return QuestDefinition.Tracker.EMPTY;
        }

        Map<String, QuestDefinition.Step> steps = new LinkedHashMap<>();
        JsonObject stepsObject = DatapackJsonReader.readObject(tracker, "steps");
        if (stepsObject != null) {
            for (Map.Entry<String, JsonElement> entry : stepsObject.entrySet()) {
                if (entry.getValue().isJsonObject()) {
                    steps.put(entry.getKey(), readTrackerStep(entry.getValue().getAsJsonObject()));
                }
            }
        }

        return new QuestDefinition.Tracker(
                DatapackJsonReader.readString(tracker, "title"),
                DatapackJsonReader.readString(tracker, "title_key"),
                steps,
                readStringMap(DatapackJsonReader.readObject(tracker, "metadata"))
        );
    }

    private static QuestDefinition.Step readTrackerStep(JsonObject step) {
        return new QuestDefinition.Step(
                DatapackJsonReader.readString(step, "text"),
                DatapackJsonReader.readString(step, "text_key"),
                DatapackJsonReader.readBoolean(step, "show_progress", true),
                (float) DatapackJsonReader.readDouble(step, "progress", -1.0D),
                readStringMap(DatapackJsonReader.readObject(step, "metadata"))
        );
    }

    private static Map<String, String> readStringMap(JsonObject object) {
        if (object == null) {
            return Map.of();
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            if (entry.getValue().isJsonPrimitive()) {
                values.put(entry.getKey(), entry.getValue().getAsString());
            }
        }
        return Map.copyOf(values);
    }

    private static List<QuestDefinition.Trigger> readTriggers(
            ResourceLocation location,
            JsonObject root,
            ResourceLocation defaultQuestId) {
        JsonElement element = root.get("triggers");
        if (element == null || element.isJsonNull()) {
            return List.of();
        }
        if (!element.isJsonArray()) {
            return List.of();
        }

        List<QuestDefinition.Trigger> triggers = new ArrayList<>();
        int index = 0;
        for (JsonElement child : element.getAsJsonArray()) {
            if (child.isJsonObject()) {
                readTrigger(location, child.getAsJsonObject(), index, defaultQuestId).ifPresent(triggers::add);
            }
            index++;
        }
        return List.copyOf(triggers);
    }

    private static Optional<QuestDefinition.Trigger> readTrigger(
            ResourceLocation location,
            JsonObject trigger,
            int index,
            ResourceLocation defaultQuestId) {
        QuestDefinition.TriggerEvent event = QuestDefinition.TriggerEvent.bySerializedName(
                DatapackJsonReader.readString(trigger, "event"));
        if (event == null) {
            DatapackDiagnostics.warnInvalidDialogueCondition(location, "quest trigger", "unknown trigger event.");
            return Optional.empty();
        }
        String id = DatapackJsonReader.readString(trigger, "id");
        if (id.isBlank()) {
            id = event.name().toLowerCase(Locale.ROOT) + "_" + index;
        }
        List<VillagerActionDefinition> actions = VillagerActionDefinition.readListOrInline(
                location,
                "quest trigger \"" + id + "\"",
                trigger,
                defaultQuestId);
        if (actions.isEmpty()) {
            return Optional.empty();
        }
        boolean repeatable = defaultTriggerRepeatable(actions);
        if (trigger.has("repeatable")) {
            repeatable = DatapackJsonReader.readBoolean(trigger, "repeatable", repeatable);
        }

        return Optional.of(new QuestDefinition.Trigger(
                id,
                event,
                DialogueCondition.readList(location, "quest trigger \"" + id + "\"", trigger, defaultQuestId),
                actions,
                DatapackJsonReader.readDurationTicks(trigger, "cooldown", defaultTriggerCooldown(event)),
                DatapackJsonReader.readDouble(trigger, "radius", 10.0D),
                repeatable
        ));
    }

    private static boolean defaultTriggerRepeatable(List<VillagerActionDefinition> actions) {
        return actions.stream().noneMatch(action -> action.kind() == VillagerActionDefinition.Kind.FORCED_DIALOGUE);
    }

    private static long defaultTriggerCooldown(QuestDefinition.TriggerEvent event) {
        return event.isContinuous() ? 20L * 30L : 0L;
    }

    private static ResourceLocation readMemoryEvent(JsonObject rewards) {
        String value = DatapackJsonReader.readString(rewards, "memory_event");
        if (value.isBlank()) {
            return null;
        }
        return VillageEventMemory.parseTagId(value).orElse(null);
    }

    private static QuestDefinition.Dialogue readDialogue(JsonObject root) {
        JsonObject dialogue = DatapackJsonReader.readObject(root, "dialogue");
        if (dialogue == null) {
            return QuestDefinition.Dialogue.EMPTY;
        }
        return new QuestDefinition.Dialogue(
                readLines(dialogue, "start"),
                readLineKeys(dialogue, "start"),
                readLines(dialogue, "reminder"),
                readLineKeys(dialogue, "reminder"),
                readLines(dialogue, "turn_in"),
                readLineKeys(dialogue, "turn_in"),
                readLines(dialogue, "already_completed"),
                readLineKeys(dialogue, "already_completed"),
                readLines(dialogue, "unavailable"),
                readLineKeys(dialogue, "unavailable"),
                readLines(dialogue, "inactive"),
                readLineKeys(dialogue, "inactive"),
                readLines(dialogue, "missing_target"),
                readLineKeys(dialogue, "missing_target"),
                readLines(dialogue, "missing_proof"),
                readLineKeys(dialogue, "missing_proof"),
                readLines(dialogue, "locate_failed"),
                readLineKeys(dialogue, "locate_failed")
        );
    }

    private static QuestDefinition.Links readLinks(JsonObject root) {
        JsonObject links = DatapackJsonReader.readObject(root, "links");
        if (links == null) {
            return QuestDefinition.Links.EMPTY;
        }
        return new QuestDefinition.Links(
                DatapackJsonReader.readResourceLocation(links, "dialogue_tree").orElse(null),
                DatapackJsonReader.readString(links, "offer"),
                DatapackJsonReader.readString(links, "reminder"),
                DatapackJsonReader.readString(links, "turn_in"),
                DatapackJsonReader.readStringList(links, "forced_dialogue")
        );
    }

    private static List<String> readLines(JsonObject root, String key) {
        JsonElement element = root.get(key);
        if (element == null || element.isJsonNull()) {
            return List.of();
        }
        if (element.isJsonPrimitive()) {
            String value = element.getAsString().trim();
            return value.isBlank() ? List.of() : List.of(value);
        }
        if (element.isJsonArray()) {
            List<String> lines = new ArrayList<>();
            for (JsonElement child : element.getAsJsonArray()) {
                if (child.isJsonPrimitive()) {
                    String value = child.getAsString().trim();
                    if (!value.isBlank()) {
                        lines.add(value);
                    }
                }
            }
            return List.copyOf(lines);
        }
        if (element.isJsonObject()) {
            return DatapackJsonReader.readLines(element.getAsJsonObject());
        }
        return List.of();
    }

    private static List<String> readLineKeys(JsonObject root, String key) {
        List<String> keys = new ArrayList<>();
        keys.addAll(DatapackJsonReader.readStringList(root, key + "_key", key + "_keys"));
        JsonElement element = root.get(key);
        if (element != null && element.isJsonObject()) {
            keys.addAll(DatapackJsonReader.readStringList(element.getAsJsonObject(), "text_key", "text_keys"));
        }
        return keys.stream()
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .toList();
    }

    private static ResourceLocation fallbackQuestId(ResourceLocation location) {
        String path = location.getPath();
        if (!path.startsWith(RESOURCE_ROOT + "/") || !path.endsWith(".json")) {
            return null;
        }
        String questPath = path.substring((RESOURCE_ROOT + "/").length(), path.length() - ".json".length());
        return ResourceLocation.tryParse(location.getNamespace() + ":" + questPath);
    }

    private static int readVillagerLevel(JsonObject object, String key, int fallback) {
        String value = DatapackJsonReader.readString(object, key);
        if (value.isBlank()) {
            return fallback;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "novice" -> 1;
            case "apprentice" -> 2;
            case "journeyman" -> 3;
            case "expert" -> 4;
            case "master" -> 5;
            default -> {
                try {
                    yield Math.max(1, Math.min(5, Integer.parseInt(value)));
                } catch (NumberFormatException ignored) {
                    yield fallback;
                }
            }
        };
    }

    private static String firstNonBlank(String first, String second) {
        return first == null || first.isBlank() ? second : first;
    }

    private record EntitySelectors(Set<ResourceLocation> entityTypes, Set<ResourceLocation> entityTags) {
        private boolean isEmpty() {
            return this.entityTypes.isEmpty() && this.entityTags.isEmpty();
        }
    }

    private record CachedQuests(MinecraftServer server, Map<ResourceLocation, QuestDefinition> quests) {
    }
}
