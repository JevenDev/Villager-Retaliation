package com.jvn.villagerretaliation.quest;

import com.jvn.villagerretaliation.api.VillagerRetaliationRegistries;

import com.jvn.villagerretaliation.quest.objectives.QuestObjectiveRegistry;
import com.jvn.villagerretaliation.quest.objectives.QuestObjectiveEventKind;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.action.VillagerActionDefinition;
import com.jvn.villagerretaliation.dialogue.DialogueCondition;
import com.jvn.villagerretaliation.dialogue.normal.DialogueEntryMetadata;
import com.jvn.villagerretaliation.dialogue.resources.QuestDialogueCatalog;
import com.jvn.villagerretaliation.dialogue.resources.QuestDialogueCompiler;
import com.jvn.villagerretaliation.quest.compiled.CompiledQuest;
import com.jvn.villagerretaliation.quest.compiled.CompiledQuestCatalog;
import com.jvn.villagerretaliation.quest.compiled.QuestSourcePointer;
import com.jvn.villagerretaliation.quest.compiler.QuestV1Compiler;
import com.jvn.villagerretaliation.quest.schema.QuestResourceEnvelope;
import com.jvn.villagerretaliation.quest.schema.QuestResourceSource;
import com.jvn.villagerretaliation.quest.schema.QuestSchemaVersion;
import com.jvn.villagerretaliation.quest.schema.v2.QuestV2Parser;
import com.jvn.villagerretaliation.quest.schema.v2.QuestV2Resource;
import com.jvn.villagerretaliation.reputation.VillagerReputationLevel;
import com.jvn.villagerretaliation.skill.VillagerSkill;
import com.jvn.villagerretaliation.util.DatapackDiagnostics;
import com.jvn.villagerretaliation.util.DatapackJsonReader;
import com.jvn.villagerretaliation.util.DatapackResourceLoader;
import com.jvn.villagerretaliation.util.VillagerProfessionUtil;
import com.jvn.villagerretaliation.village.VillageEventMemory;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
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

    private static volatile CachedQuests cachedQuests = emptyCache();

    private VillagerQuestResources() {
    }

    private static CachedQuests emptyCache() {
        return new CachedQuests(
                null,
                new CompiledQuestCatalog(Map.of()),
                QuestDialogueCatalog.empty(),
                Map.of(),
                Map.of(),
                Set.of(),
                Map.of(),
                Map.of(),
                Map.of());
    }

    public static void warm(MinecraftServer server) {
        quests(server);
    }

    public static void clearCache() {
        cachedQuests = emptyCache();
    }

    public static void installCompiledTestCatalog(MinecraftServer server, Collection<CompiledQuest> compiledQuests) {
        installCompiledTestCatalog(server, compiledQuests, QuestDialogueCatalog.empty());
    }

    public static void installCompiledTestCatalog(
            MinecraftServer server,
            Collection<CompiledQuest> compiledQuests,
            QuestDialogueCatalog dialogueCatalog) {
        Map<ResourceLocation, CompiledQuest> compiled = new LinkedHashMap<>();
        Map<ResourceLocation, QuestDefinition> quests = new LinkedHashMap<>();
        if (compiledQuests != null) {
            for (CompiledQuest quest : compiledQuests) {
                if (quest == null) {
                    continue;
                }
                compiled.put(quest.id(), quest);
                quests.put(quest.id(), quest.asQuestDefinition());
            }
        }
        CompiledQuestCatalog catalog = new CompiledQuestCatalog(compiled);
        Map<ResourceLocation, QuestDefinition> frozenQuests = freezeOrderedResourceMap(quests);
        cachedQuests = new CachedQuests(
                server,
                catalog,
                dialogueCatalog == null ? QuestDialogueCatalog.empty() : dialogueCatalog,
                frozenQuests,
                objectiveEventQuestIds(frozenQuests),
                objectiveQuestIds(frozenQuests, QuestDefinition.ObjectiveType.FACT),
                memoryEventQuestIds(frozenQuests),
                exclusiveGroupQuestIds(frozenQuests),
                triggerEventQuestIds(catalog));
    }

    public static Collection<QuestDefinition> quests(MinecraftServer server) {
        return loadCache(server).quests().values();
    }

    public static Collection<CompiledQuest> compiledQuests(MinecraftServer server) {
        return loadCache(server).compiledCatalog().quests();
    }

    public static QuestDialogueCatalog questDialogueCatalog(MinecraftServer server) {
        return loadCache(server).dialogueCatalog();
    }

    public static Optional<QuestDefinition> quest(MinecraftServer server, ResourceLocation id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(loadCache(server).quests().get(id));
    }

    public static Optional<CompiledQuest> compiledQuest(MinecraftServer server, ResourceLocation id) {
        if (id == null) {
            return Optional.empty();
        }
        return loadCache(server).compiledCatalog().quest(id);
    }

    public static Optional<QuestSourcePointer> objectiveSource(
            MinecraftServer server,
            ResourceLocation questId,
            String objectiveId) {
        if (objectiveId == null || objectiveId.isBlank()) {
            return Optional.empty();
        }
        return compiledQuest(server, questId)
                .map(quest -> quest.objectivesById().get(objectiveId))
                .map(objective -> objective == null ? null : objective.source());
    }

    public static Optional<QuestTriggerIndex> questTriggerIndex(MinecraftServer server, ResourceLocation id) {
        return compiledQuest(server, id).map(CompiledQuest::triggerIndex);
    }

    public static boolean hasMobKillObjectives(MinecraftServer server, ResourceLocation id) {
        return id != null && loadCache(server).objectiveEventQuestIds()
                .getOrDefault(QuestObjectiveEventKind.MOB_KILL, Set.of())
                .contains(id);
    }

    public static boolean hasBlockBreakObjectives(MinecraftServer server, ResourceLocation id) {
        return id != null && loadCache(server).objectiveEventQuestIds()
                .getOrDefault(QuestObjectiveEventKind.BLOCK_BREAK, Set.of())
                .contains(id);
    }

    public static boolean hasBlockPlaceObjectives(MinecraftServer server, ResourceLocation id) {
        return id != null && loadCache(server).objectiveEventQuestIds()
                .getOrDefault(QuestObjectiveEventKind.BLOCK_PLACE, Set.of())
                .contains(id);
    }

    public static boolean hasBlockInteractObjectives(MinecraftServer server, ResourceLocation id) {
        return id != null && loadCache(server).objectiveEventQuestIds()
                .getOrDefault(QuestObjectiveEventKind.BLOCK_INTERACT, Set.of())
                .contains(id);
    }

    public static Set<ResourceLocation> memoryEventQuestIds(MinecraftServer server, ResourceLocation memoryTag) {
        if (memoryTag == null) {
            return Set.of();
        }
        return loadCache(server).memoryEventQuestIds().getOrDefault(memoryTag, Set.of());
    }

    public static boolean hasFactObjectives(MinecraftServer server, ResourceLocation id) {
        return id != null && loadCache(server).factQuestIds().contains(id);
    }

    public static boolean hasGiftObjectives(MinecraftServer server, ResourceLocation id) {
        return id != null && loadCache(server).objectiveEventQuestIds()
                .getOrDefault(QuestObjectiveEventKind.GIFT, Set.of())
                .contains(id);
    }

    public static Set<ResourceLocation> questIdsForObjectiveEvent(
            MinecraftServer server,
            QuestObjectiveEventKind kind) {
        if (kind == null) {
            return Set.of();
        }
        return loadCache(server).objectiveEventQuestIds().getOrDefault(kind, Set.of());
    }

    public static Set<ResourceLocation> questIdsWithObjective(
            MinecraftServer server,
            QuestDefinition.ObjectiveType type) {
        CachedQuests cache = loadCache(server);
        return switch (type) {
            case MOB_KILL -> cache.objectiveEventQuestIds().getOrDefault(QuestObjectiveEventKind.MOB_KILL, Set.of());
            case BLOCK_BREAK -> cache.objectiveEventQuestIds().getOrDefault(QuestObjectiveEventKind.BLOCK_BREAK, Set.of());
            case BLOCK_PLACE -> cache.objectiveEventQuestIds().getOrDefault(QuestObjectiveEventKind.BLOCK_PLACE, Set.of());
            case BLOCK_INTERACT -> cache.objectiveEventQuestIds().getOrDefault(QuestObjectiveEventKind.BLOCK_INTERACT, Set.of());
            case FACT -> cache.factQuestIds();
            case TRADE -> cache.objectiveEventQuestIds().getOrDefault(QuestObjectiveEventKind.TRADE, Set.of());
            case GIFT -> cache.objectiveEventQuestIds().getOrDefault(QuestObjectiveEventKind.GIFT, Set.of());
            case MEMORY_EVENT -> cache.objectiveEventQuestIds().getOrDefault(QuestObjectiveEventKind.MEMORY_EVENT, Set.of());
            case REPUTATION -> cache.objectiveEventQuestIds().getOrDefault(QuestObjectiveEventKind.REPUTATION, Set.of());
            case CRITERION -> cache.objectiveEventQuestIds().getOrDefault(QuestObjectiveEventKind.CRITERION, Set.of());
            case STRUCTURE_VISIT, LOCATION_VISIT, ITEM_CHECK, CHOICE, CONDITION -> Set.of();
        };
    }

    public static Set<ResourceLocation> exclusiveGroupQuestIds(MinecraftServer server, ResourceLocation group) {
        if (group == null) {
            return Set.of();
        }
        return loadCache(server).exclusiveGroupQuestIds().getOrDefault(group, Set.of());
    }

    public static boolean hasQuestTrigger(
            MinecraftServer server,
            ResourceLocation id,
            QuestDefinition.TriggerEvent event) {
        if (id == null || event == null) {
            return false;
        }
        return loadCache(server).triggerEventQuestIds().getOrDefault(event, Set.of()).contains(id);
    }

    private static CachedQuests loadCache(MinecraftServer server) {
        VillagerRetaliationRegistries.freezeForDatapackCompilation();
        CachedQuests current = cachedQuests;
        if (current.server() == server) {
            return current;
        }

        synchronized (VillagerQuestResources.class) {
            current = cachedQuests;
            if (current.server() == server) {
                return current;
            }

            LoadedQuestCatalog catalog = read(server);
            Map<ResourceLocation, QuestDefinition> quests = catalog.questDefinitions();
            CachedQuests loaded = new CachedQuests(
                    server,
                    catalog.compiledCatalog(),
                    catalog.dialogueCatalog(),
                    quests,
                    objectiveEventQuestIds(quests),
                    objectiveQuestIds(quests, QuestDefinition.ObjectiveType.FACT),
                    memoryEventQuestIds(quests),
                    exclusiveGroupQuestIds(quests),
                    triggerEventQuestIds(catalog.compiledCatalog()));
            cachedQuests = loaded;
            return loaded;
        }
    }

    private static Set<ResourceLocation> objectiveQuestIds(
            Map<ResourceLocation, QuestDefinition> quests,
            QuestDefinition.ObjectiveType type) {
        Set<ResourceLocation> ids = new LinkedHashSet<>();
        for (Map.Entry<ResourceLocation, QuestDefinition> entry : quests.entrySet()) {
            boolean hasObjective = entry.getValue().objectives().stream()
                    .anyMatch(objective -> objective.type() == type);
            if (hasObjective) {
                ids.add(entry.getKey());
            }
        }
        return Set.copyOf(ids);
    }

    private static Map<QuestObjectiveEventKind, Set<ResourceLocation>> objectiveEventQuestIds(
            Map<ResourceLocation, QuestDefinition> quests) {
        Map<QuestObjectiveEventKind, Set<ResourceLocation>> idsByEvent = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, QuestDefinition> entry : quests.entrySet()) {
            for (QuestDefinition.Objective objective : entry.getValue().objectives()) {
                for (QuestObjectiveEventKind eventKind : QuestObjectiveRegistry.eventKinds(objective)) {
                    idsByEvent.computeIfAbsent(eventKind, ignored -> new LinkedHashSet<>()).add(entry.getKey());
                }
            }
        }
        Map<QuestObjectiveEventKind, Set<ResourceLocation>> frozen = new LinkedHashMap<>();
        for (Map.Entry<QuestObjectiveEventKind, Set<ResourceLocation>> entry : idsByEvent.entrySet()) {
            frozen.put(entry.getKey(), Set.copyOf(entry.getValue()));
        }
        return Map.copyOf(frozen);
    }

    private static Map<ResourceLocation, Set<ResourceLocation>> memoryEventQuestIds(Map<ResourceLocation, QuestDefinition> quests) {
        Map<ResourceLocation, Set<ResourceLocation>> idsByMemoryTag = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, QuestDefinition> entry : quests.entrySet()) {
            for (QuestDefinition.Objective objective : entry.getValue().objectives()) {
                if (!QuestObjectiveRegistry.eventKinds(objective).contains(QuestObjectiveEventKind.MEMORY_EVENT)) {
                    continue;
                }
                for (ResourceLocation memoryTag : QuestObjectiveRegistry.eventSubscriptionKeys(objective)) {
                    idsByMemoryTag.computeIfAbsent(memoryTag, ignored -> new LinkedHashSet<>()).add(entry.getKey());
                }
            }
        }

        Map<ResourceLocation, Set<ResourceLocation>> frozen = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, Set<ResourceLocation>> entry : idsByMemoryTag.entrySet()) {
            frozen.put(entry.getKey(), Set.copyOf(entry.getValue()));
        }
        return Map.copyOf(frozen);
    }

    private static Map<ResourceLocation, Set<ResourceLocation>> exclusiveGroupQuestIds(Map<ResourceLocation, QuestDefinition> quests) {
        Map<ResourceLocation, Set<ResourceLocation>> groups = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, QuestDefinition> entry : quests.entrySet()) {
            ResourceLocation group = entry.getValue().rules().branching().exclusiveGroup();
            if (group != null) {
                groups.computeIfAbsent(group, ignored -> new LinkedHashSet<>()).add(entry.getKey());
            }
        }

        Map<ResourceLocation, Set<ResourceLocation>> frozen = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, Set<ResourceLocation>> entry : groups.entrySet()) {
            frozen.put(entry.getKey(), Set.copyOf(entry.getValue()));
        }
        return Map.copyOf(frozen);
    }

    private static Map<QuestDefinition.TriggerEvent, Set<ResourceLocation>> triggerEventQuestIds(
            CompiledQuestCatalog catalog) {
        Map<QuestDefinition.TriggerEvent, Set<ResourceLocation>> idsByEvent =
                new EnumMap<>(QuestDefinition.TriggerEvent.class);
        for (CompiledQuest quest : catalog.quests()) {
            for (QuestDefinition.TriggerEvent event : quest.triggerIndex().events()) {
                idsByEvent.computeIfAbsent(event, ignored -> new LinkedHashSet<>()).add(quest.id());
            }
        }

        Map<QuestDefinition.TriggerEvent, Set<ResourceLocation>> frozen =
                new EnumMap<>(QuestDefinition.TriggerEvent.class);
        for (Map.Entry<QuestDefinition.TriggerEvent, Set<ResourceLocation>> entry : idsByEvent.entrySet()) {
            frozen.put(entry.getKey(), Set.copyOf(entry.getValue()));
        }
        return Map.copyOf(frozen);
    }

    private static LoadedQuestCatalog read(MinecraftServer server) {
        Map<ResourceLocation, QuestDefinition> quests = new LinkedHashMap<>();
        Map<ResourceLocation, CompiledQuest> compiledQuests = new LinkedHashMap<>();
        Map<ResourceLocation, ResourceLocation> sources = new LinkedHashMap<>();
        List<QuestDialogueCatalog> dialogueCatalogs = new ArrayList<>();
        List<QuestResourceEnvelope> resources = DatapackResourceLoader.jsonResources(server, RESOURCE_ROOT).stream()
                .map(resource -> DatapackResourceLoader.readObject(resource.location(), "quest", resource.resource())
                        .flatMap(root -> QuestResourceEnvelope.read(resource, root)))
                .flatMap(Optional::stream)
                .toList();
        boolean replacementMode = resources.stream()
                .filter(resource -> resource.schemaVersion() == QuestSchemaVersion.V1)
                .anyMatch(resource -> DatapackJsonReader.readBoolean(resource.root(), "replace"));
        for (QuestResourceEnvelope resource : resources) {
            if (replacementMode
                    && isBuiltInModResource(resource.source())
                    && !DatapackJsonReader.readBoolean(resource.root(), "replace")) {
                continue;
            }
            if (resource.schemaVersion() == QuestSchemaVersion.V2) {
                readV2File(resource, quests, compiledQuests, sources, dialogueCatalogs);
                continue;
            }
            readFile(resource, quests, compiledQuests, sources, replacementMode);
        }
        validatePrerequisiteReferences(quests, compiledQuests);
        return new LoadedQuestCatalog(
                freezeOrderedResourceMap(quests),
                new CompiledQuestCatalog(compiledQuests),
                QuestDialogueCatalog.merge(dialogueCatalogs));
    }

    private static void validatePrerequisiteReferences(
            Map<ResourceLocation, QuestDefinition> quests,
            Map<ResourceLocation, CompiledQuest> compiledQuests) {
        for (CompiledQuest quest : compiledQuests.values()) {
            if (quest.schemaVersion() != QuestSchemaVersion.V2) {
                continue;
            }
            for (int index = 0; index < quest.prerequisites().size(); index++) {
                ResourceLocation prerequisite = quest.prerequisites().get(index);
                if (!quests.containsKey(prerequisite)) {
                    DatapackDiagnostics.warnQuestV2Validation(
                            quest.source().resource(),
                            "/availability/prerequisites/" + index,
                            "prerequisite quest id \"" + prerequisite + "\" does not exist.",
                            "Declare the referenced quest or remove it from availability.prerequisites.",
                            Set.of(quest.id().toString(), prerequisite.toString()));
                }
            }
        }
    }

    private static void readFile(
            QuestResourceEnvelope resource,
            Map<ResourceLocation, QuestDefinition> quests,
            Map<ResourceLocation, CompiledQuest> compiledQuests,
            Map<ResourceLocation, ResourceLocation> sources,
            boolean replacementMode) {
        ResourceLocation location = resource.location();
        JsonObject root = resource.root();
        ResourceLocation fallbackId = fallbackQuestId(location);
        if (DatapackJsonReader.readBoolean(root, "replace")) {
            if (!replacementMode) {
                quests.clear();
                compiledQuests.clear();
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
                compiledQuests.remove(removeId);
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
        compiledQuests.put(definition.id(), QuestV1Compiler.compile(definition, resource));
    }

    private static void readV2File(
            QuestResourceEnvelope resource,
            Map<ResourceLocation, QuestDefinition> quests,
            Map<ResourceLocation, CompiledQuest> compiledQuests,
            Map<ResourceLocation, ResourceLocation> sources,
            List<QuestDialogueCatalog> dialogueCatalogs) {
        Optional<QuestV2Resource> parsed = QuestV2Parser.parse(resource);
        if (parsed.isEmpty()) {
            return;
        }
        Optional<CompiledQuest> compiled = QuestV2Compiler.compile(parsed.get(), resource);
        if (compiled.isEmpty()) {
            return;
        }
        CompiledQuest quest = compiled.get();
        ResourceLocation previous = sources.put(quest.id(), resource.location());
        if (previous != null) {
            DatapackDiagnostics.warnDuplicateId(resource.location(), "quest", quest.id().toString(), previous);
        }
        quests.put(quest.id(), quest.asQuestDefinition());
        compiledQuests.put(quest.id(), quest);
        dialogueCatalogs.add(QuestDialogueCompiler.compile(parsed.get(), resource));
    }

    private static boolean isBuiltInModResource(QuestResourceSource source) {
        return VillagerRetaliation.MOD_ID.equals(source.location().getNamespace())
                && source.isFromPack(VillagerRetaliation.MOD_ID);
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

    static QuestDefinition readCanonicalQuest(ResourceLocation location, JsonObject root, ResourceLocation fallbackId) {
        return readQuest(location, root, fallbackId);
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
        List<ResourceLocation> prerequisites = readQuestPrerequisites(root, parent);

        return new QuestDefinition(
                id,
                title,
                description,
                titleKey,
                descriptionKey,
                firstNonBlank(DatapackJsonReader.readString(root, "questline"), inferQuestline(location)),
                readQuestTags(root),
                parent,
                prerequisites,
                DatapackJsonReader.readBoolean(root, "show_locked_adventure_hint", true),
                readOffer(location, root, id),
                readTarget(root),
                readObjectives(location, root, id),
                readRules(location, root, id),
                readTracker(root),
                DatapackJsonReader.readString(root, "entry_stage"),
                readStages(location, root, id),
                readTriggers(location, root, id),
                readRewards(root),
                readDialogue(root),
                DialogueEntryMetadata.read(location, "quest", "quest", root),
                readLinks(root)
        );
    }

    private static List<ResourceLocation> readQuestPrerequisites(JsonObject root, ResourceLocation parent) {
        List<ResourceLocation> prerequisites = new ArrayList<>();
        for (String value : DatapackJsonReader.readStringList(root, "prerequisites")) {
            DatapackJsonReader.parseResourceLocation(value).ifPresent(prerequisites::add);
        }
        if (prerequisites.isEmpty() && parent != null) {
            prerequisites.add(parent);
        }
        return List.copyOf(prerequisites);
    }

    private static Set<String> readQuestTags(JsonObject root) {
        Set<String> tags = new LinkedHashSet<>(DatapackJsonReader.readStringList(root, "tag", "tags"));
        String group = DatapackJsonReader.readString(root, "group");
        if (!group.isBlank()) {
            tags.add("group." + group);
        }
        return Set.copyOf(tags);
    }

    private static QuestDefinition.Offer readOffer(ResourceLocation location, JsonObject root, ResourceLocation defaultQuestId) {
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
                readSkillRequirements(offer),
                DialogueCondition.readList(location, "quest offer", offer, defaultQuestId)
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
        Set<String> objectiveIds = new LinkedHashSet<>();
        int index = 0;
        for (JsonElement child : element.getAsJsonArray()) {
            if (child.isJsonObject()) {
                readObjective(location, child.getAsJsonObject(), index, defaultQuestId).ifPresent(objective -> {
                    if (!objectiveIds.add(objective.id())) {
                        DatapackDiagnostics.warnInvalidDialogueCondition(
                                location,
                                "quest objective \"" + objective.id() + "\"",
                                "duplicate objective id; later duplicate is ignored.");
                        return;
                    }
                    objectives.add(objective);
                });
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
        BlockSelectors blockSelectors = readBlockSelectors(location, context, entry);
        MemoryEventSelectors memoryEventSelectors = readMemoryEventSelectors(location, context, entry);
        Set<String> giftReactions = readGiftReactions(location, context, entry);
        ReputationObjective reputationObjective = readReputationObjective(location, context, entry);
        FactObjective factObjective = readFactObjective(location, context, entry, defaultQuestId);
        ResourceLocation criterion = DatapackJsonReader.readResourceLocation(entry, "criterion").orElse(null);
        Map<String, String> criterionData = readCriterionData(location, context, entry);
        List<DialogueCondition> conditions = DialogueCondition.readList(location, context, entry, defaultQuestId);

        QuestDefinition.Objective objective = new QuestDefinition.Objective(
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
                blockSelectors.blockTypes(),
                blockSelectors.blockTags(),
                memoryEventSelectors.memoryTags(),
                giftReactions,
                reputationObjective.levels(),
                reputationObjective.min(),
                reputationObjective.max(),
                factObjective.scope(),
                factObjective.questId(),
                factObjective.tags(),
                factObjective.key(),
                factObjective.values(),
                factObjective.min(),
                factObjective.max(),
                criterion,
                criterionData,
                DatapackJsonReader.readInt(entry, "count", 1),
                DatapackJsonReader.readBoolean(entry, "consume", true),
                readObjectiveItemRequirements(entry),
                conditions,
                readObjectiveTracker(entry));
        Optional<String> registryValidation = QuestObjectiveRegistry.validationError(objective);
        if (registryValidation.isPresent()) {
            DatapackDiagnostics.warnInvalidDialogueCondition(location, context, registryValidation.get());
            return Optional.empty();
        }
        return Optional.of(objective);
    }

    private static Map<String, String> readCriterionData(
            ResourceLocation location,
            String context,
            JsonObject entry) {
        JsonElement match = entry.get("match");
        if (match == null || match.isJsonNull()) {
            return Map.of();
        }
        if (!match.isJsonObject()) {
            DatapackDiagnostics.warnInvalidDialogueCondition(location, context, "match must be an object.");
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> child : match.getAsJsonObject().entrySet()) {
            if (child.getValue().isJsonPrimitive()) {
                result.put(child.getKey(), child.getValue().getAsString());
            } else {
                DatapackDiagnostics.warnInvalidDialogueCondition(
                        location,
                        context,
                        "match values must be strings, numbers, or booleans; ignored key " + child.getKey() + ".");
            }
        }
        return Map.copyOf(result);
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

    private static BlockSelectors readBlockSelectors(ResourceLocation location, String context, JsonObject entry) {
        Set<ResourceLocation> blockTypes = new LinkedHashSet<>();
        Set<ResourceLocation> blockTags = new LinkedHashSet<>();
        for (String value : DatapackJsonReader.readStringList(entry, "block", "blocks")) {
            ResourceLocation blockId = ResourceLocation.tryParse(value.startsWith("#") ? value.substring(1) : value);
            if (blockId == null) {
                DatapackDiagnostics.warnInvalidDialogueCondition(location, context, "block selector \"" + value + "\" is not a valid resource location.");
            } else if (value.startsWith("#")) {
                blockTags.add(blockId);
            } else {
                blockTypes.add(blockId);
            }
        }
        for (String value : DatapackJsonReader.readStringList(entry, "block_tag", "block_tags")) {
            ResourceLocation tagId = ResourceLocation.tryParse(value.startsWith("#") ? value.substring(1) : value);
            if (tagId == null) {
                DatapackDiagnostics.warnInvalidDialogueCondition(location, context, "block tag selector \"" + value + "\" is not a valid resource location.");
            } else {
                blockTags.add(tagId);
            }
        }
        return new BlockSelectors(Set.copyOf(blockTypes), Set.copyOf(blockTags));
    }

    private static MemoryEventSelectors readMemoryEventSelectors(ResourceLocation location, String context, JsonObject entry) {
        Set<ResourceLocation> memoryTags = new LinkedHashSet<>();
        for (String value : DatapackJsonReader.readStringList(
                entry,
                "memory",
                "memories",
                "memory_event",
                "memory_events",
                "memory_tag",
                "memory_tags",
                "event",
                "events")) {
            Optional<ResourceLocation> memoryTag = VillageEventMemory.parseTagId(value);
            if (memoryTag.isEmpty()) {
                DatapackDiagnostics.warnInvalidDialogueCondition(location, context, "memory event selector \"" + value + "\" is not a valid village memory tag.");
            } else {
                memoryTags.add(memoryTag.get());
            }
        }
        return new MemoryEventSelectors(Set.copyOf(memoryTags));
    }

    private static Set<String> readGiftReactions(ResourceLocation location, String context, JsonObject entry) {
        Set<String> reactions = new LinkedHashSet<>();
        for (String value : DatapackJsonReader.readStringList(entry, "reaction", "reactions", "gift_reaction", "gift_reactions")) {
            String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
            if (normalized.isBlank()) {
                continue;
            }
            if (!Set.of("loved", "liked", "neutral", "disliked", "hated").contains(normalized)) {
                DatapackDiagnostics.warnInvalidDialogueCondition(location, context, "gift reaction \"" + value + "\" must be loved, liked, neutral, disliked, or hated.");
            } else {
                reactions.add(normalized);
            }
        }
        return Set.copyOf(reactions);
    }

    private static ReputationObjective readReputationObjective(ResourceLocation location, String context, JsonObject entry) {
        Set<VillagerReputationLevel> levels = new LinkedHashSet<>();
        for (String value : DatapackJsonReader.readStringList(
                entry,
                "level",
                "levels",
                "reputation_level",
                "reputation_levels")) {
            readReputationLevel(value).ifPresentOrElse(
                    levels::add,
                    () -> DatapackDiagnostics.warnInvalidDialogueCondition(
                            location,
                            context,
                            "reputation level \"" + value + "\" must be royalty, revered, respected, trusted, neutral, suspicious, hostile, despised, or feared."));
        }
        Integer min = DatapackJsonReader.readNullableInt(entry, "min_reputation");
        if (min == null) {
            min = DatapackJsonReader.readNullableInt(entry, "min");
        }
        Integer max = DatapackJsonReader.readNullableInt(entry, "max_reputation");
        if (max == null) {
            max = DatapackJsonReader.readNullableInt(entry, "max");
        }
        return new ReputationObjective(Set.copyOf(levels), min, max);
    }

    private static Optional<VillagerReputationLevel> readReputationLevel(String value) {
        String normalized = value == null
                ? ""
                : value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        if (normalized.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(VillagerReputationLevel.valueOf(normalized));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    private static FactObjective readFactObjective(
            ResourceLocation location,
            String context,
            JsonObject entry,
            ResourceLocation defaultQuestId) {
        ResourceLocation questId = defaultQuestId;
        String questValue = firstNonBlank(
                DatapackJsonReader.readString(entry, "quest"),
                DatapackJsonReader.readString(entry, "quest_id"));
        if (!questValue.isBlank()) {
            questId = QuestIds.parse(questValue, location);
            if (questId == null) {
                DatapackDiagnostics.warnInvalidDialogueCondition(location, context, "fact objective quest \"" + questValue + "\" is not a valid quest id.");
            }
        }
        QuestFactScope fallbackScope = questId == null ? QuestFactScope.PLAYER : QuestFactScope.QUEST;
        QuestFactScope scope = QuestFactScope.bySerializedName(DatapackJsonReader.readString(entry, "scope"), fallbackScope);

        Set<ResourceLocation> tags = new LinkedHashSet<>();
        for (String value : DatapackJsonReader.readStringList(entry, "tag", "tags", "fact_tag", "quest_tag")) {
            ResourceLocation tag = ResourceLocation.tryParse(value);
            if (tag == null) {
                DatapackDiagnostics.warnInvalidDialogueCondition(location, context, "fact objective tag \"" + value + "\" is not a valid resource location.");
            } else {
                tags.add(tag);
            }
        }

        String key = firstNonBlank(
                DatapackJsonReader.readString(entry, "key"),
                firstNonBlank(
                        DatapackJsonReader.readString(entry, "variable"),
                        firstNonBlank(
                                DatapackJsonReader.readString(entry, "counter"),
                                DatapackJsonReader.readString(entry, "fact"))));
        Set<String> stageValues = new LinkedHashSet<>(DatapackJsonReader.readStringList(entry, "stage", "stages"));
        if (key.isBlank() && !stageValues.isEmpty()) {
            key = "stage";
        }
        Set<String> values = new LinkedHashSet<>(DatapackJsonReader.readStringList(entry, "value", "values"));
        Set<String> choiceValues = new LinkedHashSet<>(DatapackJsonReader.readStringList(entry, "choice", "choices"));
        if (key.isBlank() && !choiceValues.isEmpty()) {
            key = "choice";
        }
        values.addAll(stageValues);
        values.addAll(choiceValues);

        if (scope == QuestFactScope.QUEST && questId == null) {
            DatapackDiagnostics.warnInvalidDialogueCondition(location, context, "fact objective with quest scope must define quest or have a default quest.");
        }
        return new FactObjective(
                scope,
                questId,
                Set.copyOf(tags),
                key,
                Set.copyOf(values),
                DatapackJsonReader.readNullableInt(entry, "min"),
                DatapackJsonReader.readNullableInt(entry, "max"));
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
                readMemoryEvent(rewards),
                VillageEventMemory.MemoryScope.parse(DatapackJsonReader.readString(rewards, "memory_scope"))
                        .orElse(VillageEventMemory.MemoryScope.BOTH)
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
                DatapackJsonReader.readDurationTicks(rules, "prerequisite_cooldown", 0L),
                QuestDefinition.AbandonmentMode.bySerializedName(
                        DatapackJsonReader.readString(rules, "abandonment")),
                DatapackJsonReader.readDurationTicks(rules, "abandonment_cooldown", 0L),
                DatapackJsonReader.readBoolean(rules, "consume_on_completion", false),
                DatapackJsonReader.readBoolean(rules, "consume_on_abandonment", false),
                readActiveState(location, rules, defaultQuestId),
                readExpiration(location, rules, defaultQuestId),
                readBranching(rules)
        );
    }

    private static QuestDefinition.Branching readBranching(JsonObject rules) {
        JsonObject branch = DatapackJsonReader.readObject(rules, "branch");
        ResourceLocation exclusiveGroup = firstResourceLocation(branch, "exclusive_group", "group");
        if (exclusiveGroup == null) {
            exclusiveGroup = firstResourceLocation(rules, "exclusive_group", "branch_group");
        }
        QuestDefinition.BranchLockEvent exclusiveOn = QuestDefinition.BranchLockEvent.bySerializedName(firstNonBlank(
                firstString(branch, "exclusive_on", "lock_on"),
                firstString(rules, "exclusive_on", "exclusive_lock_on")));

        Set<ResourceLocation> blocksOnStart = new LinkedHashSet<>();
        blocksOnStart.addAll(DatapackJsonReader.readResourceLocations(rules, "blocks_on_start", "lock_on_start"));
        if (branch != null) {
            blocksOnStart.addAll(DatapackJsonReader.readResourceLocations(branch, "blocks_on_start", "lock_on_start"));
        }

        Set<ResourceLocation> blocksOnCompletion = new LinkedHashSet<>();
        blocksOnCompletion.addAll(DatapackJsonReader.readResourceLocations(
                rules,
                "blocks",
                "blocks_on_completion",
                "blocks_on_complete",
                "lock_on_completion",
                "lock_on_complete"));
        if (branch != null) {
            blocksOnCompletion.addAll(DatapackJsonReader.readResourceLocations(
                    branch,
                    "blocks",
                    "blocks_on_completion",
                    "blocks_on_complete",
                    "lock_on_completion",
                    "lock_on_complete"));
        }

        return new QuestDefinition.Branching(
                exclusiveGroup,
                exclusiveOn,
                Set.copyOf(blocksOnStart),
                Set.copyOf(blocksOnCompletion));
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

    private static Map<String, QuestDefinition.Stage> readStages(
            ResourceLocation location,
            JsonObject root,
            ResourceLocation defaultQuestId) {
        JsonElement element = root.get("stages");
        if (element == null || element.isJsonNull()) {
            return Map.of();
        }
        if (!element.isJsonObject()) {
            DatapackDiagnostics.warnInvalidDialogueCondition(location, "quest stages", "stages must be an object keyed by stage id.");
            return Map.of();
        }

        Map<String, QuestDefinition.Stage> stages = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
            String stageId = entry.getKey() == null ? "" : entry.getKey().trim();
            if (stageId.isBlank()) {
                DatapackDiagnostics.warnInvalidDialogueCondition(location, "quest stages", "stage id must not be blank.");
                continue;
            }
            if (!entry.getValue().isJsonObject()) {
                DatapackDiagnostics.warnInvalidDialogueCondition(location, "quest stage \"" + stageId + "\"", "stage must be an object.");
                continue;
            }
            if (stages.containsKey(stageId)) {
                DatapackDiagnostics.warnInvalidDialogueCondition(location, "quest stage \"" + stageId + "\"", "duplicate stage id; later duplicate is ignored.");
                continue;
            }
            stages.put(stageId, readStage(location, stageId, entry.getValue().getAsJsonObject(), defaultQuestId));
        }
        return java.util.Collections.unmodifiableMap(stages);
    }

    private static QuestDefinition.Stage readStage(
            ResourceLocation location,
            String stageId,
            JsonObject stage,
            ResourceLocation defaultQuestId) {
        String context = "quest stage \"" + stageId + "\"";
        JsonObject completion = DatapackJsonReader.readObject(stage, "completion");
        return new QuestDefinition.Stage(
                stageId,
                DatapackJsonReader.readStringList(stage, "objective", "objectives"),
                readStagePredicates(location, context + ".complete_when", stage.get("complete_when"), defaultQuestId),
                QuestDefinition.CompletionMode.bySerializedName(firstNonBlank(
                        completion == null ? "" : DatapackJsonReader.readString(completion, "mode"),
                        DatapackJsonReader.readString(stage, "completion_mode"))),
                completion == null
                        ? DatapackJsonReader.readInt(stage, "completion_count", 1)
                        : DatapackJsonReader.readInt(completion, "count", 1),
                firstNonBlank(
                        DatapackJsonReader.readString(stage, "next"),
                        DatapackJsonReader.readString(stage, "next_stage")),
                readActionsFromKey(location, context + ".entry_actions", stage, "entry_actions", defaultQuestId),
                readActionsFromKey(location, context + ".exit_actions", stage, "exit_actions", defaultQuestId),
                readStageBranches(location, context, stage.get("branches"), defaultQuestId),
                readBonusOutcomes(location, context, stage.get("bonuses"), defaultQuestId));
    }

    private static List<QuestDefinition.BonusOutcome> readBonusOutcomes(
            ResourceLocation location,
            String context,
            JsonElement element,
            ResourceLocation defaultQuestId) {
        if (element == null || element.isJsonNull()) {
            return List.of();
        }
        if (!element.isJsonArray()) {
            DatapackDiagnostics.warnInvalidDialogueCondition(location, context + ".bonuses", "bonuses must be an array.");
            return List.of();
        }
        List<QuestDefinition.BonusOutcome> bonuses = new ArrayList<>();
        Set<String> ids = new LinkedHashSet<>();
        int index = 0;
        for (JsonElement child : element.getAsJsonArray()) {
            if (!child.isJsonObject()) {
                index++;
                continue;
            }
            JsonObject bonus = child.getAsJsonObject();
            String id = firstNonBlank(DatapackJsonReader.readString(bonus, "id"), "bonus_" + index);
            if (!ids.add(id)) {
                DatapackDiagnostics.warnInvalidDialogueCondition(location, context + ".bonuses", "duplicate bonus id " + id + ".");
                index++;
                continue;
            }
            List<QuestDefinition.StagePredicate> when = readStagePredicates(
                    location,
                    context + ".bonuses[" + id + "].when",
                    bonus.get("when"),
                    defaultQuestId);
            if (when.isEmpty()) {
                DatapackDiagnostics.warnInvalidDialogueCondition(location, context + ".bonuses[" + id + "]", "bonus requires when predicates.");
                index++;
                continue;
            }
            bonuses.add(new QuestDefinition.BonusOutcome(
                    id,
                    when,
                    QuestDefinition.CompletionMode.bySerializedName(DatapackJsonReader.readString(bonus, "mode")),
                    DatapackJsonReader.readInt(bonus, "count", 1),
                    readActionsFromKey(location, context + ".bonuses[" + id + "].actions", bonus, "actions", defaultQuestId)));
            index++;
        }
        return List.copyOf(bonuses);
    }

    private static List<VillagerActionDefinition> readActionsFromKey(
            ResourceLocation location,
            String context,
            JsonObject source,
            String key,
            ResourceLocation defaultQuestId) {
        JsonElement actions = source.get(key);
        if (actions == null || actions.isJsonNull()) {
            return List.of();
        }
        if (!actions.isJsonArray()) {
            DatapackDiagnostics.warnInvalidDialogueCondition(location, context, key + " must be an array of actions.");
            return List.of();
        }
        JsonObject wrapper = new JsonObject();
        wrapper.add("actions", actions);
        return VillagerActionDefinition.readList(location, context, wrapper, defaultQuestId);
    }

    private static List<QuestDefinition.StagePredicate> readStagePredicates(
            ResourceLocation location,
            String context,
            JsonElement element,
            ResourceLocation defaultQuestId) {
        if (element == null || element.isJsonNull()) {
            return List.of();
        }
        List<QuestDefinition.StagePredicate> predicates = new ArrayList<>();
        if (element.isJsonPrimitive() || element.isJsonObject()) {
            predicates.addAll(readStagePredicate(location, context, element, defaultQuestId));
            return List.copyOf(predicates);
        }
        if (!element.isJsonArray()) {
            DatapackDiagnostics.warnInvalidDialogueCondition(location, context, "complete_when must be a string, object, or array.");
            return List.of();
        }
        int index = 0;
        for (JsonElement child : element.getAsJsonArray()) {
            predicates.addAll(readStagePredicate(location, context + "[" + index + "]", child, defaultQuestId));
            index++;
        }
        return List.copyOf(predicates);
    }

    private static List<QuestDefinition.StagePredicate> readStagePredicate(
            ResourceLocation location,
            String context,
            JsonElement element,
            ResourceLocation defaultQuestId) {
        if (element == null || element.isJsonNull()) {
            return List.of();
        }
        if (element.isJsonPrimitive()) {
            String objectiveId = element.getAsString().trim();
            return objectiveId.isBlank()
                    ? List.of()
                    : List.of(new QuestDefinition.StagePredicate(objectiveId, List.of()));
        }
        if (!element.isJsonObject()) {
            DatapackDiagnostics.warnInvalidDialogueCondition(location, context, "stage predicate must be a string or object.");
            return List.of();
        }

        JsonObject predicate = element.getAsJsonObject();
        String type = DatapackJsonReader.readString(predicate, "type").trim().toLowerCase(Locale.ROOT);
        List<String> objectiveIds = new ArrayList<>();
        String objectiveId = firstNonBlank(
                DatapackJsonReader.readString(predicate, "objective"),
                DatapackJsonReader.readString(predicate, "objective_id"));
        if (objectiveId.isBlank() && ("objective".equals(type) || "objectives".equals(type))) {
            objectiveId = DatapackJsonReader.readString(predicate, "id");
        }
        if (!objectiveId.isBlank()) {
            objectiveIds.add(objectiveId);
        }
        objectiveIds.addAll(DatapackJsonReader.readStringList(predicate, "objectives"));
        if (!objectiveIds.isEmpty()) {
            return objectiveIds.stream()
                    .filter(id -> id != null && !id.isBlank())
                    .map(id -> new QuestDefinition.StagePredicate(id.trim(), List.<DialogueCondition>of()))
                    .toList();
        }

        JsonObject condition = predicate;
        if (type.isBlank() && looksLikeQuestFactPredicate(predicate)) {
            condition = predicate.deepCopy();
            condition.addProperty("type", "quest_fact");
        }
        if (condition.has("conditions")) {
            List<DialogueCondition> conditions = DialogueCondition.readList(location, context, condition, defaultQuestId);
            return conditions.isEmpty() ? List.of() : List.of(new QuestDefinition.StagePredicate("", conditions));
        }
        com.google.gson.JsonArray conditionsArray = new com.google.gson.JsonArray();
        conditionsArray.add(condition);
        JsonObject wrapper = new JsonObject();
        wrapper.add("conditions", conditionsArray);
        List<DialogueCondition> conditions = DialogueCondition.readList(location, context, wrapper, defaultQuestId);
        return conditions.isEmpty() ? List.of() : List.of(new QuestDefinition.StagePredicate("", conditions));
    }

    private static boolean looksLikeQuestFactPredicate(JsonObject predicate) {
        return predicate.has("tag")
                || predicate.has("tags")
                || predicate.has("fact_tag")
                || predicate.has("quest_tag")
                || predicate.has("key")
                || predicate.has("variable")
                || predicate.has("counter")
                || predicate.has("fact")
                || predicate.has("stage")
                || predicate.has("stages");
    }

    private static List<QuestDefinition.StageBranch> readStageBranches(
            ResourceLocation location,
            String context,
            JsonElement element,
            ResourceLocation defaultQuestId) {
        if (element == null || element.isJsonNull()) {
            return List.of();
        }
        if (!element.isJsonArray()) {
            DatapackDiagnostics.warnInvalidDialogueCondition(location, context + ".branches", "branches must be an array.");
            return List.of();
        }
        List<QuestDefinition.StageBranch> branches = new ArrayList<>();
        Set<String> ids = new LinkedHashSet<>();
        int index = 0;
        for (JsonElement child : element.getAsJsonArray()) {
            if (!child.isJsonObject()) {
                DatapackDiagnostics.warnInvalidDialogueCondition(location, context + ".branches[" + index + "]", "branch must be an object.");
                index++;
                continue;
            }
            JsonObject branch = child.getAsJsonObject();
            String id = firstNonBlank(DatapackJsonReader.readString(branch, "id"), "branch_" + index);
            if (!ids.add(id)) {
                DatapackDiagnostics.warnInvalidDialogueCondition(location, context + ".branches[" + index + "]", "duplicate branch id; later duplicate is ignored.");
                index++;
                continue;
            }
            branches.add(new QuestDefinition.StageBranch(
                    id,
                    DatapackJsonReader.readString(branch, "label"),
                    DatapackJsonReader.readString(branch, "label_key"),
                    DialogueCondition.readList(location, context + ".branches[" + id + "]", branch, defaultQuestId),
                    readActionsFromKey(location, context + ".branches[" + id + "].actions", branch, "actions", defaultQuestId),
                    firstNonBlank(
                            DatapackJsonReader.readString(branch, "next"),
                            DatapackJsonReader.readString(branch, "next_stage")),
                    readStageBranchBlockers(location, context + ".branches[" + id + "]", branch.get("blocked_by"), defaultQuestId)));
            index++;
        }
        return List.copyOf(branches);
    }

    private static List<QuestDefinition.StageBranchBlocker> readStageBranchBlockers(
            ResourceLocation location,
            String context,
            JsonElement element,
            ResourceLocation defaultQuestId) {
        if (element == null || element.isJsonNull()) {
            return List.of();
        }
        if (!element.isJsonArray()) {
            DatapackDiagnostics.warnInvalidDialogueCondition(location, context + ".blocked_by", "blocked_by must be an array.");
            return List.of();
        }
        List<QuestDefinition.StageBranchBlocker> blockers = new ArrayList<>();
        int index = 0;
        for (JsonElement child : element.getAsJsonArray()) {
            if (!child.isJsonObject()) {
                DatapackDiagnostics.warnInvalidDialogueCondition(location, context + ".blocked_by[" + index + "]", "blocked_by entry must be an object.");
                index++;
                continue;
            }
            JsonObject blocker = child.getAsJsonObject();
            blockers.add(new QuestDefinition.StageBranchBlocker(
                    DialogueCondition.readList(location, context + ".blocked_by[" + index + "]", blocker, defaultQuestId),
                    DatapackJsonReader.readString(blocker, "reason"),
                    DatapackJsonReader.readString(blocker, "reason_key")));
            index++;
        }
        return List.copyOf(blockers);
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
        Set<String> triggerIds = new LinkedHashSet<>();
        int index = 0;
        for (JsonElement child : element.getAsJsonArray()) {
            if (child.isJsonObject()) {
                readTrigger(location, child.getAsJsonObject(), index, defaultQuestId).ifPresent(trigger -> {
                    if (!triggerIds.add(trigger.id())) {
                        DatapackDiagnostics.warnInvalidDialogueCondition(
                                location,
                                "quest trigger \"" + trigger.id() + "\"",
                                "duplicate trigger id; later duplicate is ignored.");
                        return;
                    }
                    triggers.add(trigger);
                });
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
                readTriggerStages(trigger),
                DatapackJsonReader.readDurationTicks(trigger, "cooldown", defaultTriggerCooldown(event)),
                DatapackJsonReader.readDouble(trigger, "radius", 10.0D),
                repeatable
        ));
    }

    private static Set<String> readTriggerStages(JsonObject trigger) {
        Set<String> stages = new LinkedHashSet<>();
        for (String stage : DatapackJsonReader.readStringList(trigger, "stage", "stages")) {
            String normalized = stage == null ? "" : stage.trim();
            if (!normalized.isBlank()) {
                stages.add(normalized);
            }
        }
        return Set.copyOf(stages);
    }

    private static boolean defaultTriggerRepeatable(List<VillagerActionDefinition> actions) {
        return actions.stream().noneMatch(action -> action.kind() == VillagerActionDefinition.Kind.FORCED_DIALOGUE);
    }

    private static long defaultTriggerCooldown(QuestDefinition.TriggerEvent event) {
        return QuestTriggerRegistry.defaultCooldownTicks(event);
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

    private static String inferQuestline(ResourceLocation location) {
        String path = location.getPath();
        if (!path.startsWith(RESOURCE_ROOT + "/")) {
            return "";
        }
        String questPath = path.substring((RESOURCE_ROOT + "/").length());
        int slash = questPath.indexOf('/');
        return slash <= 0 ? "" : questPath.substring(0, slash);
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

    private static <T> Map<ResourceLocation, T> freezeOrderedResourceMap(Map<ResourceLocation, T> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    private static String firstString(JsonObject object, String... keys) {
        if (object == null) {
            return "";
        }
        for (String key : keys) {
            String value = DatapackJsonReader.readString(object, key);
            if (!value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static ResourceLocation firstResourceLocation(JsonObject object, String... keys) {
        if (object == null) {
            return null;
        }
        for (String key : keys) {
            ResourceLocation location = DatapackJsonReader.readResourceLocation(object, key).orElse(null);
            if (location != null) {
                return location;
            }
        }
        return null;
    }

    private record EntitySelectors(Set<ResourceLocation> entityTypes, Set<ResourceLocation> entityTags) {
        private boolean isEmpty() {
            return this.entityTypes.isEmpty() && this.entityTags.isEmpty();
        }
    }

    private record BlockSelectors(Set<ResourceLocation> blockTypes, Set<ResourceLocation> blockTags) {
        private boolean isEmpty() {
            return this.blockTypes.isEmpty() && this.blockTags.isEmpty();
        }
    }

    private record MemoryEventSelectors(Set<ResourceLocation> memoryTags) {
        private boolean isEmpty() {
            return this.memoryTags.isEmpty();
        }
    }

    private record ReputationObjective(
            Set<VillagerReputationLevel> levels,
            Integer min,
            Integer max) {
        private boolean isEmpty() {
            return this.levels.isEmpty() && this.min == null && this.max == null;
        }
    }

    private record FactObjective(
            QuestFactScope scope,
            ResourceLocation questId,
            Set<ResourceLocation> tags,
            String key,
            Set<String> values,
            Integer min,
            Integer max) {
        private boolean isEmpty() {
            return this.tags.isEmpty() && (this.key == null || this.key.isBlank());
        }
    }

    private record CachedQuests(
            MinecraftServer server,
            CompiledQuestCatalog compiledCatalog,
            QuestDialogueCatalog dialogueCatalog,
            Map<ResourceLocation, QuestDefinition> quests,
            Map<QuestObjectiveEventKind, Set<ResourceLocation>> objectiveEventQuestIds,
            Set<ResourceLocation> factQuestIds,
            Map<ResourceLocation, Set<ResourceLocation>> memoryEventQuestIds,
            Map<ResourceLocation, Set<ResourceLocation>> exclusiveGroupQuestIds,
            Map<QuestDefinition.TriggerEvent, Set<ResourceLocation>> triggerEventQuestIds) {
    }

    private record LoadedQuestCatalog(
            Map<ResourceLocation, QuestDefinition> questDefinitions,
            CompiledQuestCatalog compiledCatalog,
            QuestDialogueCatalog dialogueCatalog) {
    }
}
