package com.jvn.villagerretaliation.quest.content;

import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.quest.QuestDefinition;
import com.jvn.villagerretaliation.quest.VillagerQuestResources;
import com.jvn.villagerretaliation.quest.content.bundle.BuiltInQuestBundleCompatibility;
import com.jvn.villagerretaliation.quest.content.bundle.QuestBundleDiscovery;
import com.jvn.villagerretaliation.quest.content.bundle.QuestBundleTransactions;
import com.jvn.villagerretaliation.quest.content.bundle.QuestBundlePath;
import com.jvn.villagerretaliation.quest.content.reward.QuestRewardCatalog;
import com.jvn.villagerretaliation.quest.content.reward.QuestRewardRegistryContext;
import com.jvn.villagerretaliation.quest.pool.QuestPoolResources;
import com.jvn.villagerretaliation.scene.SceneResources;
import com.jvn.villagerretaliation.scene.compiler.SceneDiagnostic;
import com.jvn.villagerretaliation.scene.encounter.EncounterResources;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

/** Owns the atomically replaceable live quest-content snapshot and its separate report. */
public final class QuestContentCatalogs {
    private static final Object PUBLICATION_LOCK = new Object();
    private static final AtomicLong GENERATIONS = new AtomicLong();
    private static final AtomicLong INVALIDATIONS = new AtomicLong();
    private static final AtomicReference<Published> LIVE =
            new AtomicReference<>(new Published(
                    null, -1L, QuestContentCatalog.empty(), QuestContentLoadReport.empty()));

    private QuestContentCatalogs() {
    }

    public static QuestContentCatalog current(MinecraftServer server) {
        return server == null ? QuestContentCatalog.empty() : published(server).catalog();
    }

    public static QuestContentLoadReport loadReport(MinecraftServer server) {
        return server == null ? QuestContentLoadReport.empty() : published(server).report();
    }

    public static void warm(MinecraftServer server) {
        current(server);
    }

    private static Published published(MinecraftServer server) {
        if (server == null) {
            return new Published(
                    null, INVALIDATIONS.get(), QuestContentCatalog.empty(), QuestContentLoadReport.empty());
        }
        long revision = INVALIDATIONS.get();
        Published published = LIVE.get();
        if (published.server() == server && published.revision() == revision) {
            return published;
        }
        synchronized (PUBLICATION_LOCK) {
            revision = INVALIDATIONS.get();
            published = LIVE.get();
            if (published.server() == server && published.revision() == revision) {
                return published;
            }
            BuildResult candidate;
            try {
                candidate = build(server);
            } catch (RuntimeException exception) {
                long generation = GENERATIONS.incrementAndGet();
                QuestContentLoadReport report = QuestContentLoadReport.builder(generation)
                        .add(
                                VillagerRetaliation.id("quest_content_catalog"),
                                "catalog/publication",
                                QuestContentLoadReport.Severity.ERROR,
                                "candidate build failed: " + exception.getMessage())
                        .build();
                candidate = new BuildResult(QuestContentCatalog.empty(), report, false);
            }
            QuestContentCatalog catalog = candidate.publishable()
                    ? candidate.catalog()
                    : published.server() == server
                            ? published.catalog()
                            : QuestContentCatalog.empty();
            Published replacement = new Published(server, revision, catalog, candidate.report());
            LIVE.set(replacement);
            return replacement;
        }
    }

    public static void invalidate() {
        INVALIDATIONS.incrementAndGet();
    }

    public static void installForTests(
            MinecraftServer server,
            QuestContentCatalog catalog,
            QuestContentLoadReport report) {
        if (server == null || catalog == null) {
            invalidate();
            return;
        }
        LIVE.set(new Published(
                server,
                INVALIDATIONS.get(),
                catalog,
                report == null
                        ? new QuestContentLoadReport(catalog.generation(), java.util.List.of())
                        : report));
    }

    static QuestContentCatalog attemptPublicationForTests(
            MinecraftServer server,
            QuestContentCatalog candidate,
            QuestContentLoadReport report,
            boolean publishable) {
        synchronized (PUBLICATION_LOCK) {
            long revision = INVALIDATIONS.incrementAndGet();
            Published prior = LIVE.get();
            QuestContentCatalog selected = publishable
                    ? candidate
                    : prior.server() == server ? prior.catalog() : QuestContentCatalog.empty();
            LIVE.set(new Published(server, revision, selected, report));
            return selected;
        }
    }

    private static BuildResult build(MinecraftServer server) {
        long generation = GENERATIONS.incrementAndGet();
        List<QuestBundleTransactions.RawResource> remaining =
                new ArrayList<>(QuestBundleDiscovery.discover(server));
        List<ManualDiagnostic> manualDiagnostics = new ArrayList<>();
        QuestBundleTransactions.Result bundles = null;
        CompiledCandidate compiled = null;
        boolean fatal = false;

        for (int attempt = 0; attempt <= remaining.size(); attempt++) {
            bundles = QuestBundleTransactions.compile(
                    remaining,
                    BuiltInQuestBundleCompatibility.rules(),
                    QuestRewardRegistryContext.create(server));
            fatal |= bundles.diagnostics().stream().anyMatch(QuestContentCatalogs::fatalDiagnostic);
            compiled = compileCandidate(server, bundles, generation);
            if (compiled.invalidOwners().isEmpty()) {
                break;
            }

            boolean removed = false;
            for (Map.Entry<QuestBundlePath.Owner, List<String>> invalid
                    : compiled.invalidOwners().entrySet()) {
                RejectedLayer layer = highestStructuralLayer(remaining, invalid.getKey());
                ResourceLocation source = ownerSource(invalid.getKey());
                String message = String.join("; ", invalid.getValue());
                if (layer == null || isBuiltInPack(layer.packId())) {
                    fatal = true;
                    manualDiagnostics.add(new ManualDiagnostic(
                            source,
                            "catalog/semantic_transaction",
                            "invalid built-in owner " + invalid.getKey().key() + ": " + message));
                    continue;
                }
                int before = remaining.size();
                remaining.removeIf(resource -> sameOwnerLayer(resource, invalid.getKey(), layer));
                if (remaining.size() != before) {
                    removed = true;
                    manualDiagnostics.add(new ManualDiagnostic(
                            source,
                            "catalog/semantic_transaction",
                            "[" + layer.packId() + "] rejected owner " + invalid.getKey().key()
                                    + " and retained its lower effective bundle: " + message));
                }
            }
            if (!removed) {
                break;
            }
        }

        if (bundles == null || compiled == null) {
            QuestContentLoadReport report = QuestContentLoadReport.builder(generation)
                    .add(
                            VillagerRetaliation.id("quest_content_catalog"),
                            "catalog/publication",
                            QuestContentLoadReport.Severity.ERROR,
                            "candidate compilation did not produce a catalog")
                    .build();
            return new BuildResult(QuestContentCatalog.empty(), report, false);
        }

        Set<ResourceLocation> missingBuiltIns =
                compiled.questOverride()
                        ? new LinkedHashSet<>()
                        : new LinkedHashSet<>(BuiltInQuestBundleCompatibility.rules().frozenQuestIds());
        missingBuiltIns.removeAll(compiled.catalog().quests().keySet());
        if (!missingBuiltIns.isEmpty()) {
            fatal = true;
            manualDiagnostics.add(new ManualDiagnostic(
                    VillagerRetaliation.id("quest_content_catalog"),
                    "catalog/compatibility",
                    "candidate is missing frozen built-in quests " + missingBuiltIns));
        }

        QuestContentLoadReport report =
                buildReport(generation, bundles, compiled, manualDiagnostics);
        return new BuildResult(compiled.catalog(), report, !fatal);
    }

    private static CompiledCandidate compileCandidate(
            MinecraftServer server,
            QuestBundleTransactions.Result bundles,
            long generation) {
        QuestRewardCatalog rewards = QuestRewardCatalog.fromBundles(bundles.bundles());
        EncounterResources.ContentSnapshot encounter =
                EncounterResources.snapshotForCatalog(server, bundles, rewards);
        SceneResources.ContentSnapshot scene =
                SceneResources.snapshotForCatalog(server, bundles, encounter);
        VillagerQuestResources.ContentSnapshot quest =
                VillagerQuestResources.snapshotForCatalog(server, bundles);
        QuestPoolResources.ContentSnapshot pool =
                QuestPoolResources.snapshotForCatalog(server, bundles);

        QuestContentCatalog catalog = new QuestContentCatalog(
                generation,
                quest.compiledCatalog(),
                quest.dialogueCatalog(),
                quest.quests(),
                quest.objectiveEventQuestIds(),
                quest.factQuestIds(),
                quest.memoryEventQuestIds(),
                quest.exclusiveGroupQuestIds(),
                quest.triggerEventQuestIds(),
                scene.scenes(),
                encounter.templates(),
                pool.pools(),
                bundles.bundles(),
                bundles.localization(),
                rewards);
        boolean questOverride = VillagerQuestResources.hasTestOverride(server);
        boolean sceneOverride = SceneResources.hasTestOverride(server);
        boolean encounterOverride = EncounterResources.hasTestOverride(server);
        return new CompiledCandidate(
                catalog,
                scene,
                encounter,
                compilationErrors(
                        bundles, catalog, questOverride, sceneOverride, encounterOverride),
                questOverride);
    }

    private static Map<QuestBundlePath.Owner, List<String>> compilationErrors(
            QuestBundleTransactions.Result bundles,
            QuestContentCatalog catalog,
            boolean questOverride,
            boolean sceneOverride,
            boolean encounterOverride) {
        Map<QuestBundlePath.Owner, List<String>> errors = new LinkedHashMap<>();
        Set<ResourceLocation> poolIds = new LinkedHashSet<>();
        catalog.pools().forEach(pool -> poolIds.add(pool.id()));
        Map<ResourceLocation, QuestBundlePath.Owner> questOwners = new LinkedHashMap<>();

        for (QuestBundleTransactions.EffectiveBundle bundle : bundles.bundles().values()) {
            QuestBundlePath.Owner owner = bundle.owner();
            if (!owner.shared() && bundle.questId() != null) {
                questOwners.put(bundle.questId(), owner);
                if (!questOverride && !catalog.quests().containsKey(bundle.questId())) {
                    error(errors, owner, "quest " + bundle.questId() + " failed runtime compilation");
                }
            }
            bundle.definitions().forEach((kind, definitions) -> {
                for (ResourceLocation id : definitions.keySet()) {
                    boolean present = switch (kind) {
                        case QUEST -> questOverride || catalog.quests().containsKey(id);
                        case SCENE -> sceneOverride || catalog.scenes().containsKey(id);
                        case ENCOUNTER -> encounterOverride || catalog.encounters().containsKey(id);
                        case POOL -> poolIds.contains(id);
                        case REWARD -> catalog.rewards().bundled(id).isPresent();
                        case LOCALE -> true;
                    };
                    if (!present) {
                        error(errors, owner, kind.name().toLowerCase(java.util.Locale.ROOT)
                                + " " + id + " failed runtime compilation");
                    }
                }
            });
        }

        if (!questOverride) {
            for (QuestDefinition definition : catalog.quests().values()) {
                QuestBundlePath.Owner owner = questOwners.get(definition.id());
                if (owner == null) {
                    continue;
                }
                for (ResourceLocation prerequisite : definition.prerequisites()) {
                    if (!catalog.quests().containsKey(prerequisite)) {
                        error(errors, owner, "prerequisite quest " + prerequisite + " is unresolved");
                    }
                }
            }
        }
        Map<QuestBundlePath.Owner, List<String>> frozen = new LinkedHashMap<>();
        errors.forEach((owner, values) -> frozen.put(owner, List.copyOf(values)));
        return Map.copyOf(frozen);
    }

    private static void error(
            Map<QuestBundlePath.Owner, List<String>> errors,
            QuestBundlePath.Owner owner,
            String message) {
        errors.computeIfAbsent(owner, ignored -> new ArrayList<>()).add(message);
    }

    private static RejectedLayer highestStructuralLayer(
            List<QuestBundleTransactions.RawResource> resources,
            QuestBundlePath.Owner owner) {
        return resources.stream()
                .filter(resource -> {
                    QuestBundlePath.Classification classified = QuestBundlePath.classify(resource.location());
                    if (!classified.valid() || !classified.path().owner().equals(owner)) {
                        return false;
                    }
                    return classified.path().kind() != QuestBundlePath.Kind.LOCALE
                            || "en_us".equals(classified.path().locale());
                })
                .map(resource -> new RejectedLayer(resource.layer(), resource.packId()))
                .max(Comparator.comparingInt(RejectedLayer::layer).thenComparing(RejectedLayer::packId))
                .orElse(null);
    }

    private static boolean sameOwnerLayer(
            QuestBundleTransactions.RawResource resource,
            QuestBundlePath.Owner owner,
            RejectedLayer layer) {
        QuestBundlePath.Classification classified = QuestBundlePath.classify(resource.location());
        return classified.valid()
                && classified.path().owner().equals(owner)
                && resource.layer() == layer.layer()
                && resource.packId().equals(layer.packId());
    }

    private static boolean fatalDiagnostic(QuestBundleTransactions.Diagnostic diagnostic) {
        if (diagnostic == null || !diagnostic.rejected()) {
            return false;
        }
        return isBuiltInPack(diagnostic.packId())
                || diagnostic.packId().isBlank()
                        && diagnostic.owner() != null
                        && VillagerRetaliation.MOD_ID.equals(diagnostic.owner().namespace());
    }
    private static boolean isBuiltInPack(String packId) {
        return VillagerRetaliation.MOD_ID.equals(packId)
                || ("mod/" + VillagerRetaliation.MOD_ID).equals(packId);
    }


    private static ResourceLocation ownerSource(QuestBundlePath.Owner owner) {
        if (owner == null || owner.namespace().isBlank()) {
            return VillagerRetaliation.id("quest_content_catalog");
        }
        String path = owner.shared()
                ? "quests/_shared"
                : "quests/" + owner.questline() + "/" + owner.slug() + "/quest.json";
        return ResourceLocation.fromNamespaceAndPath(owner.namespace(), path);
    }

    private static QuestContentLoadReport buildReport(
            long generation,
            QuestBundleTransactions.Result bundles,
            CompiledCandidate compiled,
            List<ManualDiagnostic> manualDiagnostics) {
        QuestContentLoadReport.Builder report = QuestContentLoadReport.builder(generation);
        compiled.scene().diagnostics().forEach((source, entries) -> entries.forEach(diagnostic -> report.add(
                source,
                "scene",
                diagnostic.severity() == SceneDiagnostic.Severity.ERROR
                        ? QuestContentLoadReport.Severity.ERROR
                        : QuestContentLoadReport.Severity.WARNING,
                diagnostic.path().isBlank()
                        ? diagnostic.message()
                        : diagnostic.path() + ": " + diagnostic.message())));
        compiled.encounter().diagnostics().forEach((source, entries) -> entries.forEach(message -> report.add(
                source,
                "encounter",
                QuestContentLoadReport.Severity.ERROR,
                message)));
        bundles.diagnostics().forEach(diagnostic -> report.add(
                diagnostic.location() == null ? ownerSource(diagnostic.owner()) : diagnostic.location(),
                "quest_bundle/" + diagnostic.code(),
                QuestContentLoadReport.Severity.ERROR,
                (diagnostic.packId().isBlank() ? "" : "[" + diagnostic.packId() + "] ")
                        + diagnostic.message()));
        manualDiagnostics.forEach(diagnostic -> report.add(
                diagnostic.source(),
                diagnostic.system(),
                QuestContentLoadReport.Severity.ERROR,
                diagnostic.message()));
        return report.build();
    }

    private record BuildResult(
            QuestContentCatalog catalog,
            QuestContentLoadReport report,
            boolean publishable) {
    }

    private record CompiledCandidate(
            QuestContentCatalog catalog,
            SceneResources.ContentSnapshot scene,
            EncounterResources.ContentSnapshot encounter,
            Map<QuestBundlePath.Owner, List<String>> invalidOwners,
            boolean questOverride) {
    }

    private record RejectedLayer(int layer, String packId) {
    }

    private record ManualDiagnostic(ResourceLocation source, String system, String message) {
    }

    private record Published(
            MinecraftServer server,
            long revision,
            QuestContentCatalog catalog,
            QuestContentLoadReport report) {
    }
}
