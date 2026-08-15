package com.jvn.villagerretaliation.quest.content.bundle;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.resources.ResourceLocation;

/** Applies structural/English bundle transactions and independent optional-locale overlays. */
public final class QuestBundleTransactions {
    private QuestBundleTransactions() {
    }

    public static Result compile(List<RawResource> resources, CompatibilityRules rules) {
        CompatibilityRules compatibility = rules == null ? CompatibilityRules.empty() : rules;
        List<Diagnostic> diagnostics = new ArrayList<>();
        Map<LayerKey, List<Classified>> layers = new TreeMap<>();
        for (RawResource resource : resources == null ? List.<RawResource>of() : resources) {
            QuestBundlePath.Classification classification = QuestBundlePath.classify(resource.location());
            if (!classification.valid()) {
                diagnostics.add(Diagnostic.error(resource, null, "path", classification.error()));
                continue;
            }
            Classified classified = new Classified(resource, classification.path());
            layers.computeIfAbsent(new LayerKey(resource.layer(), resource.packId()), ignored -> new ArrayList<>())
                    .add(classified);
        }

        Map<QuestBundlePath.Owner, EffectiveBundle> bundles = new LinkedHashMap<>();
        for (Map.Entry<LayerKey, List<Classified>> layer : layers.entrySet()) {
            Map<QuestBundlePath.Owner, List<Classified>> byOwner = groupOwners(layer.getValue());
            List<QuestBundlePath.Owner> owners = byOwner.keySet().stream()
                    .sorted(Comparator.comparing((QuestBundlePath.Owner owner) -> !owner.shared())
                            .thenComparing(QuestBundlePath.Owner::key))
                    .toList();
            for (QuestBundlePath.Owner owner : owners) {
                List<Classified> values = byOwner.get(owner);
                List<Classified> structural = values.stream()
                        .filter(value -> value.path().kind() != QuestBundlePath.Kind.LOCALE
                                || QuestLocaleCatalog.ENGLISH.equals(value.path().locale()))
                        .toList();
                if (!structural.isEmpty()) {
                    EffectiveBundle lower = bundles.get(owner);
                    Candidate candidate = Candidate.from(lower, owner);
                    List<String> errors = applyStructural(candidate, structural);
                    errors.addAll(validate(candidate, lower, bundles, compatibility));
                    if (errors.isEmpty()) {
                        bundles.put(owner, candidate.freeze());
                    } else {
                        for (String error : errors) {
                            diagnostics.add(Diagnostic.error(structural.getFirst().resource(), owner, "transaction", error));
                        }
                    }
                }
            }

            for (QuestBundlePath.Owner owner : owners) {
                Map<String, List<Classified>> locales = new TreeMap<>();
                for (Classified value : byOwner.get(owner)) {
                    if (value.path().kind() == QuestBundlePath.Kind.LOCALE
                            && !QuestLocaleCatalog.ENGLISH.equals(value.path().locale())) {
                        locales.computeIfAbsent(value.path().locale(), ignored -> new ArrayList<>()).add(value);
                    }
                }
                for (Map.Entry<String, List<Classified>> locale : locales.entrySet()) {
                    EffectiveBundle lower = bundles.get(owner);
                    if (lower == null) {
                        diagnostics.add(Diagnostic.error(locale.getValue().getFirst().resource(), owner, "locale",
                                "optional locale cannot introduce a bundle without an accepted structural/English layer"));
                        continue;
                    }
                    OptionalLocale applied = applyOptionalLocale(lower, locale.getKey(), locale.getValue());
                    if (applied.error().isBlank()) {
                        bundles.put(owner, applied.bundle());
                    } else {
                        diagnostics.add(Diagnostic.error(locale.getValue().getFirst().resource(), owner, "locale",
                                applied.error()));
                    }
                }
            }
        }

        isolatePrivateCrossOwnerReferences(bundles, diagnostics);
        return new Result(Collections.unmodifiableMap(new LinkedHashMap<>(bundles)), List.copyOf(diagnostics));
    }

    private static Map<QuestBundlePath.Owner, List<Classified>> groupOwners(List<Classified> resources) {
        Map<QuestBundlePath.Owner, List<Classified>> result = new LinkedHashMap<>();
        resources.stream().sorted(Comparator.comparing(value -> value.resource().location().toString()))
                .forEach(value -> result.computeIfAbsent(value.path().owner(), ignored -> new ArrayList<>()).add(value));
        return result;
    }

    private static List<String> applyStructural(Candidate candidate, List<Classified> resources) {
        List<String> errors = new ArrayList<>();
        Set<DefinitionKey> seen = new LinkedHashSet<>();
        int englishFiles = 0;
        for (Classified resource : resources) {
            if (!resource.resource().valid()) {
                errors.add(resource.resource().parseError());
                continue;
            }
            if (resource.path().kind() == QuestBundlePath.Kind.LOCALE) {
                englishFiles++;
                QuestLocaleCatalog.ReadResult locale = QuestLocaleCatalog.read(resource.resource().root());
                if (!locale.valid()) {
                    errors.add(locale.error());
                } else {
                    candidate.locales = candidate.locales.overlay(QuestLocaleCatalog.ENGLISH, locale.messages());
                }
                continue;
            }
            ResourceLocation id = readId(resource.resource().root());
            if (id == null) {
                errors.add(resource.path().kind().name().toLowerCase(java.util.Locale.ROOT)
                        + " definition requires an explicit stable id");
                continue;
            }
            DefinitionKey key = new DefinitionKey(resource.path().kind(), id);
            if (!seen.add(key)) {
                errors.add("duplicate stable ID " + id + " for " + resource.path().kind() + " within one datapack");
                continue;
            }
            if (!id.getNamespace().equals(candidate.owner.namespace())) {
                errors.add("definition namespace " + id.getNamespace() + " must match data namespace "
                        + candidate.owner.namespace());
                continue;
            }
            if (resource.path().kind() == QuestBundlePath.Kind.QUEST) {
                candidate.definitions.remove(QuestBundlePath.Kind.QUEST);
            }
            candidate.definitions.computeIfAbsent(resource.path().kind(), ignored -> new LinkedHashMap<>())
                    .put(id, resource.resource().root().deepCopy());
            if (resource.path().kind() == QuestBundlePath.Kind.QUEST) {
                candidate.questId = id;
                candidate.quest = resource.resource().root().deepCopy();
            }
        }
        if (englishFiles > 1) {
            errors.add("duplicate en_us locale ownership within one datapack");
        }
        candidate.englishFileInLayer = englishFiles == 1;
        return errors;
    }

    private static List<String> validate(
            Candidate candidate,
            EffectiveBundle lower,
            Map<QuestBundlePath.Owner, EffectiveBundle> accepted,
            CompatibilityRules compatibility) {
        List<String> errors = new ArrayList<>();
        candidate.references.clear();
        if (!candidate.owner.shared()) {
            if (candidate.quest == null || candidate.questId == null) {
                errors.add("quest bundle has no accepted quest.json");
                return errors;
            }
            String schema = string(candidate.quest, "schema");
            if (!"villagerretaliation:quest/v2".equals(schema)) {
                errors.add("quest.json must retain schema villagerretaliation:quest/v2");
            }
            candidate.prefix = string(candidate.quest, "localization_prefix");
            if (candidate.prefix.isBlank()) {
                errors.add("quest.json requires an explicit localization_prefix");
            }
            JsonObject metadata = object(candidate.quest, "metadata");
            if (!candidate.owner.questline().equals(string(metadata, "questline"))) {
                errors.add("metadata.questline must match the questline directory");
            }
            if (!candidate.questId.getNamespace().equals(candidate.owner.namespace())) {
                errors.add("quest ID namespace must match data/<namespace>");
            }
            boolean frozen = compatibility.frozenQuestIds().contains(candidate.questId);
            String frozenSlug = compatibility.frozenSlugs().get(candidate.questId);
            if (frozen) {
                if (frozenSlug == null) {
                    errors.add("compatibility manifest is missing the frozen built-in quest slug");
                } else if (!frozenSlug.equals(candidate.owner.slug())) {
                    errors.add("built-in quest slug is frozen as " + frozenSlug);
                }
            } else if (candidate.questId.getPath().contains("/")
                    || !candidate.questId.getPath().equals(candidate.owner.slug())) {
                errors.add("new quest IDs must have a single-segment path equal to the quest-slug directory");
            }
            String frozenPrefix = compatibility.frozenPrefixes().get(candidate.questId);
            if (frozen) {
                if (frozenPrefix == null) {
                    errors.add("compatibility manifest is missing the frozen built-in localization_prefix");
                } else if (!frozenPrefix.equals(candidate.prefix)) {
                    errors.add("built-in localization_prefix is frozen as " + frozenPrefix);
                }
            } else if (!candidate.prefix.startsWith(candidate.owner.namespace() + ".quest.")) {
                errors.add("new localization_prefix must begin with " + candidate.owner.namespace() + ".quest.");
            }
            if (lower != null && !lower.localizationPrefix().equals(candidate.prefix)) {
            if (lower != null && !lower.questId().equals(candidate.questId)) {
                errors.add("quest ID is immutable across overrides");
            }
                errors.add("localization_prefix is immutable across overrides");
            }
            QuestBundleLocalization.Validation localization =
                    QuestBundleLocalization.validateQuest(candidate.quest, candidate.prefix);
            errors.addAll(localization.errors());
            candidate.references.addAll(localization.references());
            if (lower == null && !candidate.englishFileInLayer) {
                errors.add("a layer introducing a quest must include locales/en_us.json");
            }
        }

        for (Map.Entry<QuestBundlePath.Kind, Map<ResourceLocation, JsonObject>> kind : candidate.definitions.entrySet()) {
            if (kind.getKey() == QuestBundlePath.Kind.QUEST
                    || kind.getKey() == QuestBundlePath.Kind.REWARD
                    || kind.getKey() == QuestBundlePath.Kind.POOL) {
                continue;
            }
            for (JsonObject definition : kind.getValue().values()) {
                QuestBundleLocalization.Validation localization =
                        QuestBundleLocalization.collectCompanion(definition, candidate.prefix);
                errors.addAll(localization.errors());
                candidate.references.addAll(localization.references());
            }
        }

        Map<DefinitionKey, QuestBundlePath.Owner> definitionOwners = definitionOwners(accepted, candidate.owner);
        for (Map.Entry<QuestBundlePath.Kind, Map<ResourceLocation, JsonObject>> kind : candidate.definitions.entrySet()) {
            for (ResourceLocation id : kind.getValue().keySet()) {
                QuestBundlePath.Owner previous = definitionOwners.get(new DefinitionKey(kind.getKey(), id));
                if (previous != null && !previous.equals(candidate.owner)) {
                    errors.add("stable ID " + id + " cannot move from owner " + previous.key()
                            + " to " + candidate.owner.key());
                }
            }
        }

        for (EffectiveBundle other : accepted.values()) {
            if (other.owner().equals(candidate.owner)) {
                continue;
            }
            if (!candidate.prefix.isBlank() && candidate.prefix.equals(other.localizationPrefix())) {
                errors.add("localization_prefix " + candidate.prefix + " is already owned by " + other.owner().key());
            }
            Set<String> duplicateMessages = new LinkedHashSet<>(candidate.locales.messages(QuestLocaleCatalog.ENGLISH).keySet());
            duplicateMessages.retainAll(other.locales().messages(QuestLocaleCatalog.ENGLISH).keySet());
            if (!duplicateMessages.isEmpty()) {
                errors.add("message IDs already have canonical owner " + other.owner().key() + ": " + duplicateMessages);
            }
        }

        Set<String> localEnglish = candidate.locales.messages(QuestLocaleCatalog.ENGLISH).keySet();
        for (String reference : candidate.references) {
            boolean relativeOwned = !candidate.prefix.isBlank()
                    && (reference.equals(candidate.prefix) || reference.startsWith(candidate.prefix + "."));
            if (!localEnglish.contains(reference)
                    && (relativeOwned || !sharedEnglishContains(accepted, candidate, reference))) {
                errors.add("effective English is missing referenced message " + reference);
            }
        }
        return errors;
    }

    private static OptionalLocale applyOptionalLocale(
            EffectiveBundle lower,
            String locale,
            List<Classified> resources) {
        if (resources.size() != 1) {
            return new OptionalLocale(lower, "duplicate " + locale + " locale ownership within one datapack");
        }
        RawResource raw = resources.getFirst().resource();
        if (!raw.valid()) {
            return new OptionalLocale(lower, raw.parseError());
        }
        QuestLocaleCatalog.ReadResult read = QuestLocaleCatalog.read(raw.root());
        if (!read.valid()) {
            return new OptionalLocale(lower, read.error());
        }
        Set<String> english = lower.locales().messages(QuestLocaleCatalog.ENGLISH).keySet();
        for (String messageId : read.messages().keySet()) {
            if (!english.contains(messageId)) {
                return new OptionalLocale(lower, "locale message " + messageId
                        + " is not canonically owned by bundle " + lower.owner().key());
            }
        }
        return new OptionalLocale(lower.withLocales(lower.locales().overlay(locale, read.messages())), "");
    }

    private static boolean sharedEnglishContains(
            Map<QuestBundlePath.Owner, EffectiveBundle> accepted,
            Candidate candidate,
            String messageId) {
        for (EffectiveBundle bundle : accepted.values()) {
            if (bundle.owner().shared()
                    && bundle.locales().messages(QuestLocaleCatalog.ENGLISH).containsKey(messageId)) {
                return true;
            }
        }
        return candidate.owner.shared()
                && candidate.locales.messages(QuestLocaleCatalog.ENGLISH).containsKey(messageId);
    }

    private static Map<DefinitionKey, QuestBundlePath.Owner> definitionOwners(
            Map<QuestBundlePath.Owner, EffectiveBundle> bundles,
            QuestBundlePath.Owner excluded) {
        Map<DefinitionKey, QuestBundlePath.Owner> result = new LinkedHashMap<>();
        for (EffectiveBundle bundle : bundles.values()) {
            if (bundle.owner().equals(excluded)) {
                continue;
            }
            bundle.definitions().forEach((kind, values) ->
                    values.keySet().forEach(id -> result.put(new DefinitionKey(kind, id), bundle.owner())));
        }
        return result;
    }

    private static void isolatePrivateCrossOwnerReferences(
            Map<QuestBundlePath.Owner, EffectiveBundle> bundles,
            List<Diagnostic> diagnostics) {
        Map<DefinitionKey, QuestBundlePath.Owner> owners = definitionOwners(bundles, nullOwner());
        List<QuestBundlePath.Owner> rejected = new ArrayList<>();
        for (EffectiveBundle bundle : bundles.values()) {
            if (bundle.owner().shared()) {
                continue;
            }
            for (Map.Entry<QuestBundlePath.Kind, Map<ResourceLocation, JsonObject>> kind : bundle.definitions().entrySet()) {
                if (kind.getKey() == QuestBundlePath.Kind.REWARD) {
                    continue;
                }
                for (JsonObject root : kind.getValue().values()) {
                    for (CompanionReference reference : companionReferences(root)) {
                        QuestBundlePath.Owner target = owners.get(new DefinitionKey(reference.kind(), reference.id()));
                        if (target != null && !target.shared() && !target.equals(bundle.owner())) {
                            diagnostics.add(new Diagnostic(-1, "", bundle.owner(), null, "ownership",
                                    "private " + reference.kind() + " " + reference.id()
                                            + " belongs to " + target.key(), true));
                            rejected.add(bundle.owner());
                        }
                    }
                }
            }
        }
        rejected.forEach(bundles::remove);
    }

    private static Set<CompanionReference> companionReferences(JsonElement element) {
        Set<CompanionReference> result = new LinkedHashSet<>();
        collectCompanionReferences(element, "", result);
        return result;
    }

    private static void collectCompanionReferences(
            JsonElement element, String key, Set<CompanionReference> result) {
        if (element == null || element.isJsonNull()) {
            return;
        }
        QuestBundlePath.Kind kind = referenceKind(key);
        if (kind != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            ResourceLocation id = ResourceLocation.tryParse(element.getAsString());
            if (id != null) {
                result.add(new CompanionReference(kind, id));
            }
        }
        if (element.isJsonArray()) {
            for (JsonElement value : element.getAsJsonArray()) {
                collectCompanionReferences(value, key, result);
            }
        } else if (element.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                collectCompanionReferences(entry.getValue(), entry.getKey(), result);
            }
        }
    }

    private static QuestBundlePath.Kind referenceKind(String key) {
        return switch (key) {
            case "scene", "scene_id" -> QuestBundlePath.Kind.SCENE;
            case "encounter", "encounter_id", "template" -> QuestBundlePath.Kind.ENCOUNTER;
            case "reward", "reward_id", "loot_table" -> QuestBundlePath.Kind.REWARD;
            case "pool", "pool_id" -> QuestBundlePath.Kind.POOL;
            default -> null;
        };
    }

    private static ResourceLocation readId(JsonObject root) {
        return ResourceLocation.tryParse(string(root, "id"));
    }

    private static String string(JsonObject root, String field) {
        if (root == null || !root.has(field) || !root.get(field).isJsonPrimitive()
                || !root.get(field).getAsJsonPrimitive().isString()) {
            return "";
        }
        return root.get(field).getAsString().trim();
    }

    private static JsonObject object(JsonObject root, String field) {
        return root != null && root.has(field) && root.get(field).isJsonObject()
                ? root.getAsJsonObject(field) : null;
    }

    private static QuestBundlePath.Owner nullOwner() {
        return new QuestBundlePath.Owner("", "", "", false);
    }

    public record RawResource(
            int layer, String packId, ResourceLocation location, JsonObject root, String parseError) {
        public RawResource {
            packId = packId == null ? "" : packId;
            root = root == null ? null : root.deepCopy();
            parseError = parseError == null ? "" : parseError;
        }

        public static RawResource valid(int layer, String packId, ResourceLocation location, JsonObject root) {
            return new RawResource(layer, packId, location, root, "");
        }

        public static RawResource malformed(int layer, String packId, ResourceLocation location, String error) {
            return new RawResource(layer, packId, location, null, error);
        }

        public boolean valid() {
            return this.root != null && this.parseError.isBlank();
        }

        public JsonObject root() {
            return this.root == null ? null : this.root.deepCopy();
        }
    }

    public record CompatibilityRules(
            Set<ResourceLocation> frozenQuestIds,
            Map<ResourceLocation, String> frozenSlugs,
            Map<ResourceLocation, String> frozenPrefixes) {
        public CompatibilityRules {
            frozenQuestIds = frozenQuestIds == null ? Set.of() : Set.copyOf(frozenQuestIds);
            frozenSlugs = frozenSlugs == null ? Map.of() : Map.copyOf(frozenSlugs);
            frozenPrefixes = frozenPrefixes == null ? Map.of() : Map.copyOf(frozenPrefixes);
        }

        public static CompatibilityRules empty() {
            return new CompatibilityRules(Set.of(), Map.of(), Map.of());
        }
    }

    public record EffectiveBundle(
            QuestBundlePath.Owner owner,
            ResourceLocation questId,
            String localizationPrefix,
            Map<QuestBundlePath.Kind, Map<ResourceLocation, JsonObject>> definitions,
            QuestLocaleCatalog locales,
            Set<String> references) {
        public EffectiveBundle {
            localizationPrefix = localizationPrefix == null ? "" : localizationPrefix;
            definitions = freezeDefinitions(definitions);
            locales = locales == null ? QuestLocaleCatalog.empty() : locales;
            references = references == null ? Set.of() : Set.copyOf(references);
        }

        public EffectiveBundle withLocales(QuestLocaleCatalog replacement) {
            return new EffectiveBundle(this.owner, this.questId, this.localizationPrefix,
                    this.definitions, replacement, this.references);
        }

        public Map<QuestBundlePath.Kind, Map<ResourceLocation, JsonObject>> definitions() {
            return freezeDefinitions(this.definitions);
        }
    }

    public record Diagnostic(
            int layer,
            String packId,
            QuestBundlePath.Owner owner,
            ResourceLocation location,
            String code,
            String message,
            boolean rejected) {
        private static Diagnostic error(
                RawResource resource, QuestBundlePath.Owner owner, String code, String message) {
            return new Diagnostic(resource.layer(), resource.packId(), owner, resource.location(),
                    code, message == null ? "" : message, true);
        }
    }

    public record Result(Map<QuestBundlePath.Owner, EffectiveBundle> bundles, List<Diagnostic> diagnostics) {
        public Result {
            bundles = bundles == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(bundles));
            diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
        }

        public boolean hasErrors() {
            return this.diagnostics.stream().anyMatch(Diagnostic::rejected);
        }

        public QuestLocaleCatalog localization() {
            Map<String, Map<String, JsonElement>> locales = new LinkedHashMap<>();
            for (EffectiveBundle bundle : this.bundles.values()) {
                for (String locale : bundle.locales().locales()) {
                    locales.computeIfAbsent(locale, ignored -> new LinkedHashMap<>())
                            .putAll(bundle.locales().messages(locale));
                }
            }
            return new QuestLocaleCatalog(locales);
        }
    }

    private record LayerKey(int layer, String packId) implements Comparable<LayerKey> {
        public int compareTo(LayerKey other) {
            int order = Integer.compare(this.layer, other.layer);
            return order != 0 ? order : this.packId.compareTo(other.packId);
        }
    }

    private record Classified(RawResource resource, QuestBundlePath path) {
    }

    private record DefinitionKey(QuestBundlePath.Kind kind, ResourceLocation id) {
    }

    private record CompanionReference(QuestBundlePath.Kind kind, ResourceLocation id) {
    }

    private record OptionalLocale(EffectiveBundle bundle, String error) {
    }

    private static final class Candidate {
        private final QuestBundlePath.Owner owner;
        private ResourceLocation questId;
        private JsonObject quest;
        private String prefix = "";
        private final Map<QuestBundlePath.Kind, Map<ResourceLocation, JsonObject>> definitions =
                new EnumMap<>(QuestBundlePath.Kind.class);
        private QuestLocaleCatalog locales = QuestLocaleCatalog.empty();
        private final Set<String> references = new LinkedHashSet<>();
        private boolean englishFileInLayer;

        private Candidate(QuestBundlePath.Owner owner) {
            this.owner = owner;
        }

        private static Candidate from(EffectiveBundle lower, QuestBundlePath.Owner owner) {
            Candidate result = new Candidate(owner);
            if (lower != null) {
                result.questId = lower.questId();
                result.quest = lower.questId() == null ? null
                        : lower.definitions().getOrDefault(QuestBundlePath.Kind.QUEST, Map.of())
                                .get(lower.questId());
                result.prefix = lower.localizationPrefix();
                lower.definitions().forEach((kind, values) -> {
                    Map<ResourceLocation, JsonObject> copy = new LinkedHashMap<>();
                    values.forEach((id, root) -> copy.put(id, root.deepCopy()));
                    result.definitions.put(kind, copy);
                });
                result.locales = lower.locales();
                result.references.addAll(lower.references());
            }
            return result;
        }

        private EffectiveBundle freeze() {
            return new EffectiveBundle(this.owner, this.questId, this.prefix,
                    this.definitions, this.locales, this.references);
        }
    }

    private static Map<QuestBundlePath.Kind, Map<ResourceLocation, JsonObject>> freezeDefinitions(
            Map<QuestBundlePath.Kind, Map<ResourceLocation, JsonObject>> source) {
        Map<QuestBundlePath.Kind, Map<ResourceLocation, JsonObject>> result =
                new EnumMap<>(QuestBundlePath.Kind.class);
        if (source != null) {
            source.forEach((kind, values) -> {
                Map<ResourceLocation, JsonObject> copy = new LinkedHashMap<>();
                values.forEach((id, root) -> copy.put(id, root.deepCopy()));
                result.put(kind, Collections.unmodifiableMap(copy));
            });
        }
        return Collections.unmodifiableMap(result);
    }
}
