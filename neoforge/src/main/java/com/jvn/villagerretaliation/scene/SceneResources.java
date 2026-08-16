package com.jvn.villagerretaliation.scene;

import com.jvn.villagerretaliation.quest.content.QuestContentCatalogs;
import com.jvn.villagerretaliation.quest.content.bundle.QuestBundlePath;
import com.jvn.villagerretaliation.quest.content.bundle.QuestBundleRuntimeMaterializer;
import com.jvn.villagerretaliation.quest.content.bundle.QuestBundleTransactions;
import com.jvn.villagerretaliation.scene.compiler.SceneCompiler;
import com.jvn.villagerretaliation.scene.compiler.SceneDiagnostic;
import com.jvn.villagerretaliation.scene.compiler.SceneParser;
import com.jvn.villagerretaliation.scene.encounter.EncounterResources;
import com.jvn.villagerretaliation.scene.model.CompiledScene;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

public final class SceneResources {
    private static volatile TestOverride testOverride = TestOverride.empty();

    private SceneResources() {
    }

    public static Optional<CompiledScene> scene(MinecraftServer server, ResourceLocation id) {
        return QuestContentCatalogs.current(server).scene(id);
    }

    public static List<CompiledScene> scenes(MinecraftServer server) {
        return List.copyOf(QuestContentCatalogs.current(server).scenes().values());
    }

    public static Map<ResourceLocation, List<SceneDiagnostic>> diagnostics(MinecraftServer server) {
        return snapshotForCatalog(server).diagnostics();
    }

    public static void warm(MinecraftServer server) {
        QuestContentCatalogs.warm(server);
    }

    public static void clearCache() {
        testOverride = TestOverride.empty();
        QuestContentCatalogs.invalidate();
    }

    public static boolean hasTestOverride(MinecraftServer server) {
        return server != null && testOverride.server() == server;
    }

    public static void installTestScenes(MinecraftServer server, List<CompiledScene> scenes) {
        Map<ResourceLocation, CompiledScene> values = new LinkedHashMap<>();
        if (scenes != null) scenes.stream().filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparing(value -> value.id().toString())).forEach(value -> values.put(value.id(), value));
        testOverride = new TestOverride(server, new ContentSnapshot(Map.copyOf(values), Map.of()));
        QuestContentCatalogs.invalidate();
    }

    public static void installTestResources(MinecraftServer server, Map<ResourceLocation, com.google.gson.JsonObject> resources) {
        Map<ResourceLocation, CompiledScene> scenes = new LinkedHashMap<>();
        Map<ResourceLocation, List<SceneDiagnostic>> diagnostics = new LinkedHashMap<>();
        if (resources != null) {
            resources.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
                SceneParser.ParseResult parsed = SceneParser.parse(entry.getKey(), entry.getValue());
                List<SceneDiagnostic> combined = parsed.diagnostics().stream()
                        .map(value -> value.atSource(entry.getKey())).collect(Collectors.toCollection(ArrayList::new));
                if (parsed.valid()) {
                    SceneCompiler.CompileResult compiled = SceneCompiler.compile(parsed.resource());
                    combined.addAll(compiled.diagnostics().stream().map(value -> value.atSource(entry.getKey())).toList());
                    if (combined.stream().noneMatch(value -> value.severity() == SceneDiagnostic.Severity.ERROR)
                            && compiled.scene() != null) scenes.put(compiled.scene().id(), compiled.scene());
                    diagnostics.put(parsed.resource().id(), List.copyOf(combined));
                } else {
                    diagnostics.put(entry.getKey(), List.copyOf(combined));
                }
            });
        }
        testOverride = new TestOverride(server, new ContentSnapshot(Map.copyOf(scenes), Map.copyOf(diagnostics)));
        QuestContentCatalogs.invalidate();
    }

    public static ContentSnapshot snapshotForCatalog(MinecraftServer server) {
        EncounterResources.ContentSnapshot encounters = EncounterResources.snapshotForCatalog(server);
        return snapshotForCatalog(
                server,
                new QuestBundleTransactions.Result(Map.of(), List.of()),
                encounters);
    }

    public static ContentSnapshot snapshotForCatalog(
            MinecraftServer server,
            QuestBundleTransactions.Result bundles,
            EncounterResources.ContentSnapshot encounters) {
        Set<ResourceLocation> encounterTemplates = encounters.templates().values().stream()
                .map(template -> template.id()).collect(Collectors.toUnmodifiableSet());
        return load(server, bundles, encounterTemplates);
    }

    private static ContentSnapshot load(
            MinecraftServer server,
            QuestBundleTransactions.Result bundles,
            Set<ResourceLocation> encounterTemplates) {
        TestOverride override = testOverride;
        if (override.server() == server) {
            return override.snapshot();
        }
        Map<ResourceLocation, CompiledScene> scenes = new LinkedHashMap<>();
        Map<ResourceLocation, List<SceneDiagnostic>> diagnostics = new LinkedHashMap<>();
        if (bundles != null) {
            bundles.bundles().values().stream()
                    .sorted(Comparator.comparing(bundle -> bundle.owner().key()))
                    .forEach(bundle -> bundle.definitions()
                            .getOrDefault(QuestBundlePath.Kind.SCENE, Map.of())
                            .keySet().stream().sorted().forEach(id -> {
                                QuestBundleRuntimeMaterializer.DefinitionResult materialized =
                                        QuestBundleRuntimeMaterializer.materializeDefinition(
                                                bundle, QuestBundlePath.Kind.SCENE, id);
                                readDefinition(
                                        bundleSource(bundle.owner(), "scenes", id),
                                        materialized.definition(),
                                        encounterTemplates,
                                        scenes,
                                        diagnostics,
                                        materialized.errors());
                            }));
        }
        return new ContentSnapshot(Map.copyOf(scenes), Map.copyOf(diagnostics));
    }

    private static void readDefinition(
            ResourceLocation source,
            com.google.gson.JsonObject root,
            Set<ResourceLocation> encounterTemplates,
            Map<ResourceLocation, CompiledScene> scenes,
            Map<ResourceLocation, List<SceneDiagnostic>> diagnostics,
            List<String> materializationErrors) {
        List<SceneDiagnostic> combined = materializationErrors.stream()
                .map(message -> new SceneDiagnostic(
                        SceneDiagnostic.Severity.ERROR,
                        "scene.localization",
                        "",
                        message).atSource(source))
                .collect(Collectors.toCollection(ArrayList::new));
        SceneParser.ParseResult parsed = SceneParser.parse(source, root);
        combined.addAll(parsed.diagnostics().stream()
                .map(diagnostic -> diagnostic.atSource(source)).toList());
        if (parsed.valid()) {
            SceneCompiler.CompileResult compiled = SceneCompiler.compile(parsed.resource(), encounterTemplates);
            combined.addAll(compiled.diagnostics().stream()
                    .map(diagnostic -> diagnostic.atSource(source)).toList());
            boolean fatal = combined.stream()
                    .anyMatch(diagnostic -> diagnostic.severity() == SceneDiagnostic.Severity.ERROR);
            if (!fatal && compiled.scene() != null) scenes.put(compiled.scene().id(), compiled.scene());
            diagnostics.put(parsed.resource().id(), List.copyOf(combined));
        } else {
            diagnostics.put(source, List.copyOf(combined));
        }
    }

    private static ResourceLocation bundleSource(
            QuestBundlePath.Owner owner, String directory, ResourceLocation id) {
        String idPath = id.getPath();
        int separator = idPath.lastIndexOf('/');
        String file = (separator < 0 ? idPath : idPath.substring(separator + 1)) + ".json";
        String path = owner.shared()
                ? "quests/_shared/" + directory + "/" + file
                : "quests/" + owner.questline() + "/" + owner.slug() + "/" + directory + "/" + file;
        return ResourceLocation.fromNamespaceAndPath(owner.namespace(), path);
    }

    public record ContentSnapshot(
            Map<ResourceLocation, CompiledScene> scenes,
            Map<ResourceLocation, List<SceneDiagnostic>> diagnostics) {
    }

    private record TestOverride(MinecraftServer server, ContentSnapshot snapshot) {
        private static TestOverride empty() {
            return new TestOverride(null, new ContentSnapshot(Map.of(), Map.of()));
        }
    }
}
