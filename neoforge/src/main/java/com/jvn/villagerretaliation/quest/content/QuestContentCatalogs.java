package com.jvn.villagerretaliation.quest.content;

import com.jvn.villagerretaliation.quest.VillagerQuestResources;
import com.jvn.villagerretaliation.quest.content.bundle.BuiltInQuestBundleCompatibility;
import com.jvn.villagerretaliation.quest.content.bundle.QuestBundleDiscovery;
import com.jvn.villagerretaliation.quest.content.bundle.QuestBundleTransactions;
import com.jvn.villagerretaliation.quest.content.reward.QuestRewardCatalog;
import com.jvn.villagerretaliation.quest.content.reward.QuestRewardRegistryContext;
import com.jvn.villagerretaliation.quest.pool.QuestPoolResources;
import com.jvn.villagerretaliation.scene.SceneResources;
import com.jvn.villagerretaliation.scene.compiler.SceneDiagnostic;
import com.jvn.villagerretaliation.scene.encounter.EncounterResources;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.server.MinecraftServer;

/** Owns the atomically replaceable live quest-content snapshot and its separate report. */
public final class QuestContentCatalogs {
    private static final Object PUBLICATION_LOCK = new Object();
    private static final AtomicLong GENERATIONS = new AtomicLong();
    private static final AtomicReference<Published> LIVE =
            new AtomicReference<>(new Published(null, QuestContentCatalog.empty(), QuestContentLoadReport.empty()));

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
            return new Published(null, QuestContentCatalog.empty(), QuestContentLoadReport.empty());
        }
        Published published = LIVE.get();
        if (published.server() == server) {
            return published;
        }
        synchronized (PUBLICATION_LOCK) {
            published = LIVE.get();
            if (published.server() == server) {
                return published;
            }
            Published candidate = build(server);
            LIVE.set(candidate);
            return candidate;
        }
    }

    public static void invalidate() {
        LIVE.set(new Published(null, QuestContentCatalog.empty(), QuestContentLoadReport.empty()));
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
                catalog,
                report == null
                        ? new QuestContentLoadReport(catalog.generation(), java.util.List.of())
                        : report));
    }

    private static Published build(MinecraftServer server) {
        long generation = GENERATIONS.incrementAndGet();
        QuestBundleTransactions.Result bundles = QuestBundleTransactions.compile(
                QuestBundleDiscovery.discover(server),
                BuiltInQuestBundleCompatibility.rules(),
                QuestRewardRegistryContext.create(server));
        VillagerQuestResources.ContentSnapshot quest = VillagerQuestResources.snapshotForCatalog(server, bundles);
        EncounterResources.ContentSnapshot encounter = EncounterResources.snapshotForCatalog(server);
        SceneResources.ContentSnapshot scene = SceneResources.snapshotForCatalog(server);
        QuestPoolResources.ContentSnapshot pool = QuestPoolResources.snapshotForCatalog(server);

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
                QuestRewardCatalog.fromBundles(bundles.bundles()));

        QuestContentLoadReport.Builder report = QuestContentLoadReport.builder(generation);
        scene.diagnostics().forEach((source, entries) -> entries.forEach(diagnostic -> report.add(
                source,
                "scene",
                diagnostic.severity() == SceneDiagnostic.Severity.ERROR
                        ? QuestContentLoadReport.Severity.ERROR
                        : QuestContentLoadReport.Severity.WARNING,
                diagnostic.path().isBlank()
                        ? diagnostic.message()
                        : diagnostic.path() + ": " + diagnostic.message())));
        encounter.diagnostics().forEach((source, entries) -> entries.forEach(message -> report.add(
                source,
                "encounter",
                QuestContentLoadReport.Severity.ERROR,
                message)));
        bundles.diagnostics().forEach(diagnostic -> report.add(
                diagnostic.location(),
                "quest_bundle/" + diagnostic.code(),
                QuestContentLoadReport.Severity.ERROR,
                (diagnostic.packId().isBlank() ? "" : "[" + diagnostic.packId() + "] ")
                        + diagnostic.message()));
        return new Published(server, catalog, report.build());
    }

    private record Published(
            MinecraftServer server,
            QuestContentCatalog catalog,
            QuestContentLoadReport report) {
    }
}
