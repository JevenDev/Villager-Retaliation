package com.jvn.villagerretaliation.quest;

import com.jvn.villagerretaliation.api.VillagerRetaliationRegistries;

import com.jvn.villagerretaliation.quest.objectives.QuestObjectiveRegistry;
import com.jvn.villagerretaliation.quest.objectives.QuestObjectiveEventKind;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.action.VillagerActionDefinition;
import com.jvn.villagerretaliation.dialogue.DialogueCondition;
import com.jvn.villagerretaliation.dialogue.resources.QuestDialogueCatalog;
import com.jvn.villagerretaliation.dialogue.resources.QuestDialogueCompiler;
import com.jvn.villagerretaliation.quest.compiled.CompiledQuest;
import com.jvn.villagerretaliation.quest.compiled.CompiledQuestCatalog;
import com.jvn.villagerretaliation.quest.compiled.QuestSourcePointer;
import com.jvn.villagerretaliation.quest.content.QuestContentCatalog;
import com.jvn.villagerretaliation.quest.content.QuestContentCatalogs;
import com.jvn.villagerretaliation.quest.content.bundle.QuestBundlePath;
import com.jvn.villagerretaliation.quest.content.bundle.QuestBundleRuntimeMaterializer;
import com.jvn.villagerretaliation.quest.content.bundle.QuestBundleTransactions;
import com.jvn.villagerretaliation.quest.schema.QuestResourceEnvelope;
import com.jvn.villagerretaliation.quest.schema.QuestResourceSource;
import com.jvn.villagerretaliation.quest.schema.QuestSchemaVersion;
import com.jvn.villagerretaliation.quest.schema.v2.QuestV2Parser;
import com.jvn.villagerretaliation.quest.schema.v2.QuestV2Resource;
import com.jvn.villagerretaliation.reputation.VillagerReputationLevel;
import com.jvn.villagerretaliation.skill.VillagerSkill;
import com.jvn.villagerretaliation.util.DatapackDiagnostics;
import com.jvn.villagerretaliation.util.DatapackJsonReader;
import com.jvn.villagerretaliation.util.VillagerProfessionUtil;
import com.jvn.villagerretaliation.util.item.ItemStackPredicate;
import com.jvn.villagerretaliation.util.item.ItemStackPredicateParser;
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
import net.minecraft.core.HolderLookup;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.BuiltInRegistries;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

public final class VillagerQuestResources {
    private static final String RESOURCE_ROOT = "quests";
    private static final int DEFAULT_STRUCTURE_SEARCH_RADIUS = 256;
    private static final int DEFAULT_DISCOVERY_RADIUS = 128;

    private static volatile CachedQuests testOverride = emptyCache();

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
        QuestContentCatalogs.warm(server);
    }

    public static void clearCache() {
        testOverride = emptyCache();
        QuestContentCatalogs.invalidate();
    }

    public static AutoCloseable preserveTestOverride() {
        CachedQuests prior = testOverride;
        return () -> {
            testOverride = prior;
            QuestContentCatalogs.invalidate();
        };
    }

    public static boolean hasTestOverride(MinecraftServer server) {
        return server != null && testOverride.server() == server;
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
        testOverride = new CachedQuests(
                server,
                catalog,
                dialogueCatalog == null ? QuestDialogueCatalog.empty() : dialogueCatalog,
                frozenQuests,
                objectiveEventQuestIds(frozenQuests),
                objectiveQuestIds(frozenQuests, QuestDefinition.ObjectiveType.FACT),
                memoryEventQuestIds(frozenQuests),
                exclusiveGroupQuestIds(frozenQuests),
                triggerEventQuestIds(catalog));
        QuestContentCatalogs.invalidate();
    }

    public static Collection<QuestDefinition> quests(MinecraftServer server) {
        return QuestContentCatalogs.current(server).questDefinitions();
    }

    public static Collection<CompiledQuest> compiledQuests(MinecraftServer server) {
        return QuestContentCatalogs.current(server).compiledQuests();
    }

    public static QuestDialogueCatalog questDialogueCatalog(MinecraftServer server) {
        return QuestContentCatalogs.current(server).dialogueCatalog();
    }

    public static Optional<QuestDefinition> quest(MinecraftServer server, ResourceLocation id) {
        return QuestContentCatalogs.current(server).quest(id);
    }

    public static Optional<CompiledQuest> compiledQuest(MinecraftServer server, ResourceLocation id) {
        return QuestContentCatalogs.current(server).compiledQuest(id);
    }

    public static Optional<QuestSourcePointer> objectiveSource(
            MinecraftServer server,
            ResourceLocation questId,
            String objectiveId) {
        return QuestContentCatalogs.current(server).objectiveSource(questId, objectiveId);
    }

    public static Optional<QuestTriggerIndex> questTriggerIndex(MinecraftServer server, ResourceLocation id) {
        return QuestContentCatalogs.current(server).questTriggerIndex(id);
    }

    public static boolean hasMobKillObjectives(MinecraftServer server, ResourceLocation id) {
        return hasObjectiveEvent(server, id, QuestObjectiveEventKind.MOB_KILL);
    }

    public static boolean hasBlockBreakObjectives(MinecraftServer server, ResourceLocation id) {
        return hasObjectiveEvent(server, id, QuestObjectiveEventKind.BLOCK_BREAK);
    }

    public static boolean hasBlockPlaceObjectives(MinecraftServer server, ResourceLocation id) {
        return hasObjectiveEvent(server, id, QuestObjectiveEventKind.BLOCK_PLACE);
    }

    public static boolean hasBlockInteractObjectives(MinecraftServer server, ResourceLocation id) {
        return hasObjectiveEvent(server, id, QuestObjectiveEventKind.BLOCK_INTERACT);
    }

    public static Set<ResourceLocation> memoryEventQuestIds(
            MinecraftServer server, ResourceLocation memoryTag) {
        return QuestContentCatalogs.current(server).memoryEventQuestIds(memoryTag);
    }

    public static boolean hasFactObjectives(MinecraftServer server, ResourceLocation id) {
        return id != null && QuestContentCatalogs.current(server).factQuestIds().contains(id);
    }

    public static boolean hasGiftObjectives(MinecraftServer server, ResourceLocation id) {
        return hasObjectiveEvent(server, id, QuestObjectiveEventKind.GIFT);
    }

    public static Set<ResourceLocation> questIdsForObjectiveEvent(
            MinecraftServer server,
            QuestObjectiveEventKind kind) {
        return QuestContentCatalogs.current(server).questIdsForObjectiveEvent(kind);
    }

    public static Set<ResourceLocation> questIdsWithObjective(
            MinecraftServer server,
            QuestDefinition.ObjectiveType type) {
        QuestContentCatalog catalog = QuestContentCatalogs.current(server);
        return switch (type) {
            case MOB_KILL -> catalog.questIdsForObjectiveEvent(QuestObjectiveEventKind.MOB_KILL);
            case BLOCK_BREAK -> catalog.questIdsForObjectiveEvent(QuestObjectiveEventKind.BLOCK_BREAK);
            case BLOCK_PLACE -> catalog.questIdsForObjectiveEvent(QuestObjectiveEventKind.BLOCK_PLACE);
            case BLOCK_INTERACT -> catalog.questIdsForObjectiveEvent(QuestObjectiveEventKind.BLOCK_INTERACT);
            case FACT -> catalog.factQuestIds();
            case TRADE -> catalog.questIdsForObjectiveEvent(QuestObjectiveEventKind.TRADE);
            case GIFT -> catalog.questIdsForObjectiveEvent(QuestObjectiveEventKind.GIFT);
            case MEMORY_EVENT -> catalog.questIdsForObjectiveEvent(QuestObjectiveEventKind.MEMORY_EVENT);
            case REPUTATION -> catalog.questIdsForObjectiveEvent(QuestObjectiveEventKind.REPUTATION);
            case CRITERION -> catalog.questIdsForObjectiveEvent(QuestObjectiveEventKind.CRITERION);
            case STRUCTURE_VISIT, LOCATION_VISIT, ITEM_CHECK, CHOICE, CONDITION,
                    CAMPAIGN_OUTCOME, CAMPAIGN_SPEAK, CAMPAIGN_INVESTIGATE, CAMPAIGN_FOLLOW, FOLLOWER_ARRIVAL -> Set.of();
        };
    }

    public static Set<ResourceLocation> exclusiveGroupQuestIds(
            MinecraftServer server, ResourceLocation group) {
        return QuestContentCatalogs.current(server).exclusiveGroupQuestIds(group);
    }

    public static boolean hasQuestTrigger(
            MinecraftServer server,
            ResourceLocation id,
            QuestDefinition.TriggerEvent event) {
        return QuestContentCatalogs.current(server).hasQuestTrigger(id, event);
    }

    public static ContentSnapshot snapshotForCatalog(MinecraftServer server) {
        return snapshotForCatalog(server, new QuestBundleTransactions.Result(Map.of(), List.of()));
    }

    public static ContentSnapshot snapshotForCatalog(
            MinecraftServer server,
            QuestBundleTransactions.Result bundles) {
        VillagerRetaliationRegistries.freezeForDatapackCompilation();
        CachedQuests override = testOverride;
        return override.server() == server ? snapshot(override) : compileBundles(server, bundles);
    }

    public static ContentSnapshot compileBundles(MinecraftServer server, QuestBundleTransactions.Result bundles) {
        VillagerRetaliationRegistries.freezeForDatapackCompilation();
        return snapshot(compileCache(server, bundles));
    }

    private static ContentSnapshot snapshot(CachedQuests snapshot) {
        return new ContentSnapshot(
                snapshot.compiledCatalog(),
                snapshot.dialogueCatalog(),
                snapshot.quests(),
                snapshot.objectiveEventQuestIds(),
                snapshot.factQuestIds(),
                snapshot.memoryEventQuestIds(),
                snapshot.exclusiveGroupQuestIds(),
                snapshot.triggerEventQuestIds());
    }

    private static boolean hasObjectiveEvent(
            MinecraftServer server,
            ResourceLocation id,
            QuestObjectiveEventKind kind) {
        return id != null
                && QuestContentCatalogs.current(server).questIdsForObjectiveEvent(kind).contains(id);
    }

    private static CachedQuests compileCache(
            MinecraftServer server,
            QuestBundleTransactions.Result bundles) {
        LoadedQuestCatalog catalog = read(server, bundles);
        Map<ResourceLocation, QuestDefinition> quests = catalog.questDefinitions();
        return new CachedQuests(
                server,
                catalog.compiledCatalog(),
                catalog.dialogueCatalog(),
                quests,
                objectiveEventQuestIds(quests),
                objectiveQuestIds(quests, QuestDefinition.ObjectiveType.FACT),
                memoryEventQuestIds(quests),
                exclusiveGroupQuestIds(quests),
                triggerEventQuestIds(catalog.compiledCatalog()));
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

    private static LoadedQuestCatalog read(
            MinecraftServer server,
            QuestBundleTransactions.Result bundles) {
        Map<ResourceLocation, QuestDefinition> quests = new LinkedHashMap<>();
        Map<ResourceLocation, CompiledQuest> compiledQuests = new LinkedHashMap<>();
        Map<ResourceLocation, ResourceLocation> sources = new LinkedHashMap<>();
        List<QuestDialogueCatalog> dialogueCatalogs = new ArrayList<>();
        readBundles(server.registryAccess(), bundles, quests, compiledQuests, sources, dialogueCatalogs);
        validatePrerequisiteReferences(quests, compiledQuests);
        return new LoadedQuestCatalog(
                freezeOrderedResourceMap(quests),
                new CompiledQuestCatalog(compiledQuests),
                QuestDialogueCatalog.merge(dialogueCatalogs));
    }

    private static void readBundles(
            HolderLookup.Provider registries,
            QuestBundleTransactions.Result bundles,
            Map<ResourceLocation, QuestDefinition> quests,
            Map<ResourceLocation, CompiledQuest> compiledQuests,
            Map<ResourceLocation, ResourceLocation> sources,
            List<QuestDialogueCatalog> dialogueCatalogs) {
        if (bundles == null) {
            return;
        }
        for (QuestBundleTransactions.EffectiveBundle bundle : bundles.bundles().values()) {
            if (bundle.owner().shared() || bundle.questId() == null) {
                continue;
            }
            QuestBundleRuntimeMaterializer.Result materialized = QuestBundleRuntimeMaterializer.materialize(bundle);
            ResourceLocation source = ResourceLocation.fromNamespaceAndPath(
                    bundle.owner().namespace(),
                    RESOURCE_ROOT + "/" + bundle.owner().questline() + "/"
                            + bundle.owner().slug() + "/quest.json");
            if (!materialized.valid()) {
                materialized.errors().forEach(error -> DatapackDiagnostics.warnSkippedEntry(
                        source, "quest bundle", "localization", error));
                continue;
            }
            QuestResourceEnvelope.read(
                            source,
                            materialized.quest(),
                            new QuestResourceSource(source, "quest_bundle"))
                    .ifPresent(envelope -> readV3File(
                            registries, envelope, quests, compiledQuests, sources, dialogueCatalogs));
        }
    }

    private static void validatePrerequisiteReferences(
            Map<ResourceLocation, QuestDefinition> quests,
            Map<ResourceLocation, CompiledQuest> compiledQuests) {
        for (CompiledQuest quest : compiledQuests.values()) {
            if (quest.schemaVersion() != QuestSchemaVersion.V3) {
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

    private static void readV3File(
            HolderLookup.Provider registries,
            QuestResourceEnvelope resource,
            Map<ResourceLocation, QuestDefinition> quests,
            Map<ResourceLocation, CompiledQuest> compiledQuests,
            Map<ResourceLocation, ResourceLocation> sources,
            List<QuestDialogueCatalog> dialogueCatalogs) {
        if (resource.schemaVersion() != QuestSchemaVersion.V3) {
            DatapackDiagnostics.warnSkippedEntry(resource.location(), "quest", "schema",
                    "Quest V3 is required. Run tools/migrate-quest-v2-to-v3.mjs on this pack.");
            return;
        }
        JsonObject runtime = com.jvn.villagerretaliation.quest.schema.v3.QuestV3Format.runtime(resource.root());
        Optional<QuestV2Resource> parsed = QuestV2Parser.parse(resource.location(), resource.source(), runtime);
        if (parsed.isEmpty()) {
            return;
        }
        Optional<CompiledQuest> compiled = QuestV2Compiler.compile(parsed.get(), resource, registries);
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

    static QuestDefinition.Revision readRevision(JsonObject root) {
        return readRevision(root.get("revision"), DatapackJsonReader.readObject(root, "migration"));
    }

    static QuestDefinition.Revision readRevision(JsonElement revisionElement, JsonObject migration) {
        JsonObject revisionObject = revisionElement != null && revisionElement.isJsonObject()
                ? revisionElement.getAsJsonObject()
                : null;
        int number = revisionObject == null
                ? DatapackJsonReader.readInt(revisionElement, 1)
                : DatapackJsonReader.readInt(revisionObject, "number", 1);
        if (migration == null && revisionObject != null) {
            migration = DatapackJsonReader.readObject(revisionObject, "migration");
        }
        return new QuestDefinition.Revision(
                number,
                QuestDefinition.RevisionPolicy.bySerializedName(
                        migration == null ? "" : DatapackJsonReader.readString(migration, "active_policy", "on_active_change")),
                migration == null ? Map.of() : readStringMap(DatapackJsonReader.readObject(migration, "stage_aliases")),
                migration == null ? Map.of() : readStringMap(DatapackJsonReader.readObject(migration, "objective_aliases")));
    }

    static QuestDefinition.Offer readOffer(ResourceLocation location, JsonObject root, ResourceLocation defaultQuestId) {
        JsonObject offer = DatapackJsonReader.readObject(root, "offer");
        if (offer == null) {
            return QuestDefinition.Offer.any();
        }

        return readOfferFields(location, offer, DialogueCondition.readList(location, "quest offer", offer, defaultQuestId),
                DatapackJsonReader.readInt(offer, "weight", DatapackJsonReader.readInt(offer, "selection_weight", 1)));
    }

    static QuestDefinition.Offer readOfferFields(ResourceLocation location, JsonObject filters,
            List<DialogueCondition> conditions, int weight) {
        if (filters == null) return new QuestDefinition.Offer(Set.of(), 1, Map.of(), conditions, weight);
        Set<VillagerProfession> professions = new LinkedHashSet<>();
        for (String value : DatapackJsonReader.readStringList(filters, "professions")) {
            Optional<VillagerProfession> profession = VillagerProfessionUtil.parse(value);
            if (profession.isPresent()) {
                professions.add(profession.get());
            } else {
                DatapackDiagnostics.warnUnknownProfession(location, "quest offer", value);
            }
        }

        return new QuestDefinition.Offer(professions, readVillagerLevel(filters, "min_villager_level", 1),
                readSkillRequirements(filters), conditions, weight);
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

    static QuestDefinition.Target readTarget(JsonObject root, HolderLookup.Provider registries) {
        return readTargetFields(DatapackJsonReader.readObject(root, "target"), registries);
    }

    static QuestDefinition.Target readTargetFields(JsonObject target, HolderLookup.Provider registries) {
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
        ItemStackPredicate proofItemPredicate = proofItem == null
                ? ItemStackPredicate.ANY
                : ItemStackPredicateParser.parse(
                        registries,
                        target,
                        BuiltInRegistries.ITEM.getOptional(proofItem).stream().toList(),
                        "proof_item_components",
                        "proof_item_durability",
                        "proof_item_custom_data",
                        "proof_item_nbt");

        return new QuestDefinition.Target(
                structure,
                dimension,
                pieces,
                searchRadius,
                discoveryRadius,
                proofItem,
                proofItemPredicate);
    }

    static Optional<QuestDefinition.Objective> readObjective(
            ResourceLocation location,
            JsonObject entry,
            int index,
            ResourceLocation defaultQuestId,
            HolderLookup.Provider registries) {
        return readObjectiveFields(location, QuestObjectiveFields.legacy(entry, index), defaultQuestId, registries);
    }

    static Optional<QuestDefinition.Objective> readObjectiveFields(ResourceLocation location, QuestObjectiveFields entry,
            ResourceLocation defaultQuestId, HolderLookup.Provider registries) {
        String id = entry.id();
        JsonObject schemaInput = entry.schemaInput();
        String context = "quest objective \"" + id + "\"";
        var structuralIssues = QuestObjectiveRegistry.structuralIssues(schemaInput);
        if (!structuralIssues.isEmpty()) {
            throw new IllegalArgumentException(context + ": " + structuralIssues);
        }
        QuestDefinition.ObjectiveType type = QuestDefinition.ObjectiveType.bySerializedName(
                entry.type());
        if (type == null) {
            DatapackDiagnostics.warnInvalidDialogueCondition(location, context, "unknown objective type.");
            return Optional.empty();
        }
        var campaignLocationError = QuestObjectiveRegistry.campaignLocationError(schemaInput);
        if (campaignLocationError.isPresent()) {
            DatapackDiagnostics.warnInvalidDialogueCondition(location, context, campaignLocationError.get());
            return Optional.empty();
        }
        ResourceLocation structure = entry.resource("structure").orElse(null);
        ResourceKey<Level> dimension = readDimension(entry);
        BlockPos objectiveLocation = readLocation(entry);
        ResourceLocation item = entry.resource("item").orElse(null);
        ItemSelection itemSelection = readItemSelection(location, context, entry, type, item);
        if (!itemSelection.valid()) {
            return Optional.empty();
        }
        EntitySelectors entitySelectors = readEntitySelectors(location, context, entry);
        BlockSelectors blockSelectors = readBlockSelectors(location, context, entry);
        MemoryEventSelectors memoryEventSelectors = readMemoryEventSelectors(location, context, entry);
        Set<String> giftReactions = readGiftReactions(location, context, entry);
        ReputationObjective reputationObjective = readReputationObjective(location, context, entry);
        FactObjective factObjective = readFactObjective(location, context, entry, defaultQuestId);
        ResourceLocation criterion = entry.resource("criterion").orElse(null);
        Map<String, String> criterionData = readCriterionData(location, context, entry);
        List<DialogueCondition> conditions = DialogueCondition.readFields(location, context, entry::get, defaultQuestId);

        QuestDefinition.Objective objective = new QuestDefinition.Objective(
                id,
                type,
                entry.optional(),
                structure,
                dimension,
                objectiveLocation,
                entry.integer("radius", 8),
                entry.strings("pieces"),
                entry.integer("search_radius", DEFAULT_STRUCTURE_SEARCH_RADIUS),
                entry.integer("discovery_radius", DEFAULT_DISCOVERY_RADIUS),
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
                entry.integer("count", 1),
                entry.bool("consume", true),
                readObjectiveItemRequirements(entry, registries, itemSelection.entries()),
                conditions,
                readObjectiveTracker(entry),
                itemSelection.entries(),
                itemSelection.mode(),
                entry.string("campaign_location"),
                com.jvn.villagerretaliation.campaign.CampaignStoryObjective.read(type, schemaInput));
        Optional<String> registryValidation = QuestObjectiveRegistry.validationError(objective);
        if (registryValidation.isPresent()) {
            DatapackDiagnostics.warnInvalidDialogueCondition(location, context, registryValidation.get());
            return Optional.empty();
        }
        return Optional.of(objective);
    }

    private static ItemSelection readItemSelection(
            ResourceLocation location,
            String context,
            QuestObjectiveFields objective,
            QuestDefinition.ObjectiveType type,
            ResourceLocation fixedItem) {
        String selection = objective.string("selection").trim();
        JsonElement items = objective.get("items");
        boolean hasItems = items != null && !items.isJsonNull();
        if (selection.isBlank() && !hasItems) {
            return ItemSelection.fixed();
        }
        if (type != QuestDefinition.ObjectiveType.ITEM_CHECK) {
            DatapackDiagnostics.warnInvalidDialogueCondition(
                    location, context, "selection and items are only supported by item_check objectives.");
            return ItemSelection.invalid();
        }
        if (!"random".equalsIgnoreCase(selection)) {
            DatapackDiagnostics.warnInvalidDialogueCondition(
                    location, context, "items requires selection to be random.");
            return ItemSelection.invalid();
        }
        if (fixedItem != null) {
            DatapackDiagnostics.warnInvalidDialogueCondition(
                    location, context, "random item_check objectives cannot combine item with items.");
            return ItemSelection.invalid();
        }
        if (items == null || !items.isJsonArray() || items.getAsJsonArray().isEmpty()) {
            DatapackDiagnostics.warnInvalidDialogueCondition(
                    location, context, "random item_check objectives require a non-empty items array.");
            return ItemSelection.invalid();
        }

        List<QuestDefinition.ItemSelectionEntry> entries = new ArrayList<>();
        int index = 0;
        for (JsonElement element : items.getAsJsonArray()) {
            QuestDefinition.ItemSelectionEntry entry = readItemSelectionEntry(
                    location, context + " items[" + index + "]", element);
            if (entry == null) {
                return ItemSelection.invalid();
            }
            entries.add(entry);
            index++;
        }
        return new ItemSelection(List.copyOf(entries), QuestDefinition.ItemSelectionMode.RANDOM, true);
    }

    private static QuestDefinition.ItemSelectionEntry readItemSelectionEntry(
            ResourceLocation location,
            String context,
            JsonElement element) {
        String itemValue = "";
        String tagValue = "";
        int weight = 1;
        if (element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            String value = element.getAsString().trim();
            if (value.startsWith("#")) {
                tagValue = value.substring(1).trim();
            } else {
                itemValue = value;
            }
        } else if (element != null && element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            for (String key : object.keySet()) {
                if (!Set.of("item", "tag", "weight").contains(key)) {
                    DatapackDiagnostics.warnInvalidDialogueCondition(
                            location, context, "unsupported random item entry field: " + key);
                    return null;
                }
            }
            itemValue = DatapackJsonReader.readString(object, "item").trim();
            tagValue = DatapackJsonReader.readString(object, "tag").trim();
            if (tagValue.startsWith("#")) {
                tagValue = tagValue.substring(1).trim();
            }
            JsonElement rawWeight = object.get("weight");
            if (rawWeight != null) {
                if (!rawWeight.isJsonPrimitive() || !rawWeight.getAsJsonPrimitive().isNumber()) {
                    DatapackDiagnostics.warnInvalidDialogueCondition(
                            location, context, "weight must be a positive integer.");
                    return null;
                }
                double numericWeight = rawWeight.getAsDouble();
                if (!Double.isFinite(numericWeight) || numericWeight < 1.0D || numericWeight != Math.rint(numericWeight)
                        || numericWeight > Integer.MAX_VALUE) {
                    DatapackDiagnostics.warnInvalidDialogueCondition(
                            location, context, "weight must be a positive integer.");
                    return null;
                }
                weight = (int) numericWeight;
            }
        } else {
            DatapackDiagnostics.warnInvalidDialogueCondition(
                    location, context, "entry must be an item/tag string or an object.");
            return null;
        }

        if (itemValue.isBlank() == tagValue.isBlank()) {
            DatapackDiagnostics.warnInvalidDialogueCondition(
                    location, context, "entry must define exactly one of item or tag.");
            return null;
        }
        if (!itemValue.isBlank()) {
            ResourceLocation itemId = ResourceLocation.tryParse(itemValue);
            if (itemId == null || BuiltInRegistries.ITEM.getOptional(itemId).filter(item -> item != Items.AIR).isEmpty()) {
                DatapackDiagnostics.warnInvalidDialogueCondition(
                        location, context, "unknown item: " + itemValue);
                return null;
            }
            return new QuestDefinition.ItemSelectionEntry(itemId, null, weight);
        }

        ResourceLocation tagId = ResourceLocation.tryParse(tagValue);
        if (tagId == null) {
            DatapackDiagnostics.warnInvalidDialogueCondition(
                    location, context, "invalid item tag: " + tagValue);
            return null;
        }
        TagKey<Item> tag = TagKey.create(Registries.ITEM, tagId);
        boolean populated = BuiltInRegistries.ITEM.getTag(tag)
                .map(values -> values.stream().anyMatch(holder -> holder.value() != Items.AIR))
                .orElse(false);
        if (!populated) {
            DatapackDiagnostics.warnInvalidDialogueCondition(
                    location, context, "item tag is missing or empty: " + tagValue);
            return null;
        }
        return new QuestDefinition.ItemSelectionEntry(null, tagId, weight);
    }

    private record ItemSelection(
            List<QuestDefinition.ItemSelectionEntry> entries,
            QuestDefinition.ItemSelectionMode mode,
            boolean valid) {
        private static ItemSelection fixed() {
            return new ItemSelection(List.of(), QuestDefinition.ItemSelectionMode.FIXED, true);
        }

        private static ItemSelection invalid() {
            return new ItemSelection(List.of(), QuestDefinition.ItemSelectionMode.FIXED, false);
        }
    }

    private static Map<String, String> readCriterionData(
            ResourceLocation location,
            String context,
            QuestObjectiveFields entry) {
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

    private static BlockPos readLocation(QuestObjectiveFields entry) {
        Integer x = entry.nullableInt("x");
        Integer y = entry.nullableInt("y");
        Integer z = entry.nullableInt("z");
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

    private static EntitySelectors readEntitySelectors(ResourceLocation location, String context, QuestObjectiveFields entry) {
        Set<ResourceLocation> entityTypes = new LinkedHashSet<>();
        Set<ResourceLocation> entityTags = new LinkedHashSet<>();
        for (String value : entry.strings("entity", "entities")) {
            readEntitySelector(location, context, value, entityTypes, entityTags);
        }
        for (String value : entry.strings("entity_tag", "entity_tags")) {
            readEntityTag(location, context, value, entityTags);
        }
        return new EntitySelectors(Set.copyOf(entityTypes), Set.copyOf(entityTags));
    }

    private static BlockSelectors readBlockSelectors(ResourceLocation location, String context, QuestObjectiveFields entry) {
        Set<ResourceLocation> blockTypes = new LinkedHashSet<>();
        Set<ResourceLocation> blockTags = new LinkedHashSet<>();
        for (String value : entry.strings("block", "blocks")) {
            ResourceLocation blockId = ResourceLocation.tryParse(value.startsWith("#") ? value.substring(1) : value);
            if (blockId == null) {
                DatapackDiagnostics.warnInvalidDialogueCondition(location, context, "block selector \"" + value + "\" is not a valid resource location.");
            } else if (value.startsWith("#")) {
                blockTags.add(blockId);
            } else {
                blockTypes.add(blockId);
            }
        }
        for (String value : entry.strings("block_tag", "block_tags")) {
            ResourceLocation tagId = ResourceLocation.tryParse(value.startsWith("#") ? value.substring(1) : value);
            if (tagId == null) {
                DatapackDiagnostics.warnInvalidDialogueCondition(location, context, "block tag selector \"" + value + "\" is not a valid resource location.");
            } else {
                blockTags.add(tagId);
            }
        }
        return new BlockSelectors(Set.copyOf(blockTypes), Set.copyOf(blockTags));
    }

    private static MemoryEventSelectors readMemoryEventSelectors(ResourceLocation location, String context, QuestObjectiveFields entry) {
        Set<ResourceLocation> memoryTags = new LinkedHashSet<>();
        for (String value : entry.strings("memory",
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

    private static Set<String> readGiftReactions(ResourceLocation location, String context, QuestObjectiveFields entry) {
        Set<String> reactions = new LinkedHashSet<>();
        for (String value : entry.strings("reaction", "reactions", "gift_reaction", "gift_reactions")) {
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

    private static ReputationObjective readReputationObjective(ResourceLocation location, String context, QuestObjectiveFields entry) {
        Set<VillagerReputationLevel> levels = new LinkedHashSet<>();
        for (String value : entry.strings("level",
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
        Integer min = entry.nullableInt("min_reputation");
        if (min == null) {
            min = entry.nullableInt("min");
        }
        Integer max = entry.nullableInt("max_reputation");
        if (max == null) {
            max = entry.nullableInt("max");
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
            QuestObjectiveFields entry,
            ResourceLocation defaultQuestId) {
        ResourceLocation questId = defaultQuestId;
        String questValue = entry.usesDefaultQuest() ? "" : firstNonBlank(
                entry.string("quest"),
                entry.string("quest_id"));
        if (!questValue.isBlank()) {
            questId = QuestIds.parse(questValue, location);
            if (questId == null) {
                DatapackDiagnostics.warnInvalidDialogueCondition(location, context, "fact objective quest \"" + questValue + "\" is not a valid quest id.");
            }
        }
        QuestFactScope fallbackScope = questId == null ? QuestFactScope.PLAYER : QuestFactScope.QUEST;
        QuestFactScope scope = QuestFactScope.bySerializedName(entry.string("scope"), fallbackScope);

        Set<ResourceLocation> tags = new LinkedHashSet<>();
        for (String value : entry.strings("tag", "tags", "fact_tag", "quest_tag")) {
            ResourceLocation tag = ResourceLocation.tryParse(value);
            if (tag == null) {
                DatapackDiagnostics.warnInvalidDialogueCondition(location, context, "fact objective tag \"" + value + "\" is not a valid resource location.");
            } else {
                tags.add(tag);
            }
        }

        String key = firstNonBlank(
                entry.string("key"),
                firstNonBlank(
                        entry.string("variable"),
                        firstNonBlank(
                                entry.string("counter"),
                                entry.string("fact"))));
        Set<String> stageValues = new LinkedHashSet<>(entry.strings("stage", "stages"));
        if (key.isBlank() && !stageValues.isEmpty()) {
            key = "stage";
        }
        Set<String> values = new LinkedHashSet<>(entry.strings("value", "values"));
        Set<String> choiceValues = new LinkedHashSet<>(entry.strings("choice", "choices"));
        if (key.isBlank() && !choiceValues.isEmpty()) {
            key = "choice";
        }
        values.addAll(stageValues);
        values.addAll(choiceValues);
        String defaultChoice = entry.choiceDefault();
        if (!defaultChoice.isBlank()) {
            key = "choice";
            values.add(defaultChoice);
        }

        if (scope == QuestFactScope.QUEST && questId == null) {
            DatapackDiagnostics.warnInvalidDialogueCondition(location, context, "fact objective with quest scope must define quest or have a default quest.");
        }
        return new FactObjective(
                scope,
                questId,
                Set.copyOf(tags),
                key,
                Set.copyOf(values),
                entry.nullableInt("min"),
                entry.nullableInt("max"));
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

    private static QuestDefinition.ItemRequirements readObjectiveItemRequirements(
            QuestObjectiveFields entry,
            HolderLookup.Provider registries,
            List<QuestDefinition.ItemSelectionEntry> selections) {
        OptionalInt minEnchantmentLevel = readOptionalInt(entry, "min_enchantment_level");
        OptionalInt maxEnchantmentLevel = readOptionalInt(entry, "max_enchantment_level");
        List<QuestDefinition.EnchantmentRequirement> enchantments = new ArrayList<>();
        readEnchantmentRequirements(entry.get("enchantment"), minEnchantmentLevel, maxEnchantmentLevel, enchantments);
        readEnchantmentRequirements(entry.get("enchantments"), minEnchantmentLevel, maxEnchantmentLevel, enchantments);
        JsonObject predicateEntry = entry.itemPredicateInput();
        if (!predicateEntry.has("durability")
                && (predicateEntry.has("min_durability") || predicateEntry.has("max_durability"))) {
            JsonObject durability = new JsonObject();
            if (predicateEntry.has("min_durability")) {
                durability.add("min", predicateEntry.get("min_durability").deepCopy());
            }
            if (predicateEntry.has("max_durability")) {
                durability.add("max", predicateEntry.get("max_durability").deepCopy());
            }
            predicateEntry.add("durability", durability);
        }
        ResourceLocation itemId = entry.resource("item").orElse(null);
        List<Item> predicateItems = new ArrayList<>(itemId == null
                ? List.of()
                : BuiltInRegistries.ITEM.getOptional(itemId).stream().toList());
        for (QuestDefinition.ItemSelectionEntry selection : selections == null
                ? List.<QuestDefinition.ItemSelectionEntry>of()
                : selections) {
            if (selection.item() != null) {
                BuiltInRegistries.ITEM.getOptional(selection.item()).ifPresent(predicateItems::add);
            } else if (selection.tag() != null) {
                BuiltInRegistries.ITEM.getTag(TagKey.create(Registries.ITEM, selection.tag()))
                        .ifPresent(values -> values.stream()
                                .map(holder -> holder.value())
                                .filter(candidate -> candidate != Items.AIR)
                                .forEach(predicateItems::add));
            }
        }
        ItemStackPredicate stackPredicate = ItemStackPredicateParser.parse(
                registries,
                predicateEntry,
                predicateItems.stream().distinct().toList(),
                "components",
                "durability",
                "custom_data",
                "nbt");
        return new QuestDefinition.ItemRequirements(
                enchantments,
                readOptionalInt(entry, "min_durability"),
                readOptionalInt(entry, "max_durability"),
                readOptionalInt(entry, "min_durability_percent"),
                readOptionalInt(entry, "max_durability_percent"),
                readCustomData(entry),
                stackPredicate);
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

    private static OptionalInt readOptionalInt(QuestObjectiveFields entry, String key) {
        return entry.optionalInt(key);
    }

    private static OptionalInt readOptionalInt(JsonObject entry, String key) {
        return DatapackJsonReader.readOptionalInt(entry, key)
                .map(OptionalInt::of)
                .orElseGet(OptionalInt::empty);
    }

    private static OptionalInt firstPresent(OptionalInt first, OptionalInt second) {
        return first.isPresent() ? first : second;
    }

    private static CompoundTag readCustomData(QuestObjectiveFields entry) {
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

    private static ResourceKey<Level> readDimension(QuestObjectiveFields object) {
        return object.resource("dimension").map(id -> ResourceKey.create(Registries.DIMENSION, id)).orElse(null);
    }

    private static ResourceKey<Level> readDimension(JsonObject object) {
        return DatapackJsonReader.readResourceLocation(object, "dimension")
                .map(id -> ResourceKey.create(Registries.DIMENSION, id))
                .orElse(null);
    }

    private static QuestDefinition.ObjectiveTracker readObjectiveTracker(QuestObjectiveFields objective) {
        JsonObject tracker = objective.object("tracker");
        JsonObject ui = objective.v2() ? objective.object("ui") : null;
        if (tracker == null && ui == null) return QuestDefinition.ObjectiveTracker.EMPTY;
        JsonElement metadata = objectiveTrackerValue(tracker, ui, "metadata");
        return new QuestDefinition.ObjectiveTracker(
                objectiveTrackerText(tracker, ui, "text", "tracker_text"),
                objectiveTrackerText(tracker, ui, "complete_text", "tracker_complete_text"),
                objectiveTrackerText(tracker, ui, "text_key", "tracker_text_key"),
                objectiveTrackerText(tracker, ui, "complete_text_key", "tracker_complete_text_key"),
                DatapackJsonReader.readBoolean(objectiveTrackerValue(tracker, ui, "show_progress"), true),
                (float) DatapackJsonReader.readDouble(objectiveTrackerValue(tracker, ui, "progress"), -1.0D),
                readStringMap(metadata != null && metadata.isJsonObject() ? metadata.getAsJsonObject() : null));
    }

    private static String objectiveTrackerText(JsonObject tracker, JsonObject ui, String key, String uiKey) {
        return firstNonBlank(tracker == null ? "" : DatapackJsonReader.readString(tracker, key),
                ui == null ? "" : DatapackJsonReader.readString(ui, uiKey));
    }

    private static JsonElement objectiveTrackerValue(JsonObject tracker, JsonObject ui, String key) {
        return tracker != null && tracker.has(key) ? tracker.get(key) : ui == null ? null : ui.get(key);
    }

    static QuestDefinition.Rules readRules(ResourceLocation location, JsonObject root, ResourceLocation defaultQuestId) {
        JsonObject rules = DatapackJsonReader.readObject(root, "rules");
        if (rules == null) {
            return QuestDefinition.Rules.DEFAULT;
        }

        return readRuleFields(location, rules, defaultQuestId,
                DatapackJsonReader.readDurationTicks(rules, "completion_cooldown", 0L), readBranching(rules));
    }

    static QuestDefinition.Rules readRuleFields(ResourceLocation location, JsonObject rules, ResourceLocation defaultQuestId,
            long completionCooldownTicks, QuestDefinition.Branching branching) {
        boolean repeatable = DatapackJsonReader.readBoolean(rules, "repeatable", false);
        int maxStarts = Math.max(0, DatapackJsonReader.readInt(rules, "max_starts", repeatable ? 0 : 1));
        int maxCompletions = Math.max(0, DatapackJsonReader.readInt(rules, "max_completions", repeatable ? 0 : 1));
        Map<String, Integer> maxActiveByTag = new LinkedHashMap<>();
        JsonObject activeTagCaps = DatapackJsonReader.readObject(rules, "max_active_by_tag");
        if (activeTagCaps != null) {
            activeTagCaps.entrySet().forEach(entry -> {
                if (entry.getValue().isJsonPrimitive()) {
                    maxActiveByTag.put(entry.getKey(), Math.max(0, entry.getValue().getAsInt()));
                }
            });
        }
        return new QuestDefinition.Rules(
                repeatable,
                DatapackJsonReader.readBoolean(rules, "locked_to_villager", true),
                DatapackJsonReader.readBoolean(rules, "cross_villager_compatible", false),
                maxStarts,
                maxCompletions,
                QuestDefinition.CompletionScope.bySerializedName(
                        DatapackJsonReader.readString(rules, "completion_scope", "scope")),
                completionCooldownTicks,
                DatapackJsonReader.readDurationTicks(rules, "prerequisite_cooldown", 0L),
                QuestDefinition.AbandonmentMode.bySerializedName(
                        DatapackJsonReader.readString(rules, "abandonment")),
                DatapackJsonReader.readDurationTicks(rules, "abandonment_cooldown", 0L),
                DatapackJsonReader.readBoolean(rules, "consume_on_completion", false),
                DatapackJsonReader.readBoolean(rules, "consume_on_abandonment", false),
                readActiveState(location, rules, defaultQuestId),
                readExpiration(location, rules, defaultQuestId),
                branching,
                DatapackJsonReader.readInt(rules, "max_active_quests", 0),
                maxActiveByTag
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

    static Map<String, String> readStringMap(JsonObject object) {
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

    static List<QuestDefinition.BonusOutcome> readBonusOutcomes(
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

    static List<VillagerActionDefinition> readActionsFromKey(
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

    static List<QuestDefinition.StagePredicate> readStagePredicates(
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

    static List<String> readLines(JsonObject root, String key) {
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

    private static ResourceLocation fallbackQuestId(ResourceLocation location) {
        String path = location.getPath();
        if (!path.startsWith(RESOURCE_ROOT + "/") || !path.endsWith(".json")) {
            return null;
        }
        String questPath = path.substring((RESOURCE_ROOT + "/").length(), path.length() - ".json".length());
        return ResourceLocation.tryParse(location.getNamespace() + ":" + questPath);
    }

    static String inferQuestline(ResourceLocation location) {
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

    static String firstNonBlank(String first, String second) {
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

    public record ContentSnapshot(
            CompiledQuestCatalog compiledCatalog,
            QuestDialogueCatalog dialogueCatalog,
            Map<ResourceLocation, QuestDefinition> quests,
            Map<QuestObjectiveEventKind, Set<ResourceLocation>> objectiveEventQuestIds,
            Set<ResourceLocation> factQuestIds,
            Map<ResourceLocation, Set<ResourceLocation>> memoryEventQuestIds,
            Map<ResourceLocation, Set<ResourceLocation>> exclusiveGroupQuestIds,
            Map<QuestDefinition.TriggerEvent, Set<ResourceLocation>> triggerEventQuestIds) {
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
