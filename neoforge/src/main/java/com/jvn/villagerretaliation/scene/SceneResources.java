package com.jvn.villagerretaliation.scene;

import com.jvn.villagerretaliation.quest.content.QuestContentCatalogs;
import com.jvn.villagerretaliation.scene.compiler.SceneCompiler;
import com.jvn.villagerretaliation.scene.compiler.SceneDiagnostic;
import com.jvn.villagerretaliation.scene.compiler.SceneParser;
import com.jvn.villagerretaliation.scene.encounter.EncounterResources;
import com.jvn.villagerretaliation.scene.model.CompiledScene;
import com.jvn.villagerretaliation.util.DatapackResourceLoader;
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
    public static final String RESOURCE_ROOT = "quest_scenes";
    private static volatile Cache cache = new Cache(null, Map.of(), Map.of());

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
        cache = new Cache(null, Map.of(), Map.of());
        QuestContentCatalogs.invalidate();
    }

    public static void installTestScenes(MinecraftServer server, List<CompiledScene> scenes) {
        Map<ResourceLocation, CompiledScene> values = new LinkedHashMap<>();
        if (scenes != null) scenes.stream().filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparing(value -> value.id().toString())).forEach(value -> values.put(value.id(), value));
        cache = new Cache(server, Map.copyOf(values), Map.of());
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
        cache = new Cache(server, Map.copyOf(scenes), Map.copyOf(diagnostics));
        QuestContentCatalogs.invalidate();
    }

    public static ContentSnapshot snapshotForCatalog(MinecraftServer server) {
        Cache snapshot = load(server);
        return new ContentSnapshot(snapshot.scenes(), snapshot.diagnostics());
    }

    private static Cache load(MinecraftServer server) {
        Cache current = cache;
        if (current.server() == server) return current;
        synchronized (SceneResources.class) {
            current = cache;
            if (current.server() == server) return current;
            Map<ResourceLocation, CompiledScene> scenes = new LinkedHashMap<>();
            Map<ResourceLocation, List<SceneDiagnostic>> diagnostics = new LinkedHashMap<>();
            Set<ResourceLocation> encounterTemplates = EncounterResources.snapshotForCatalog(server).templates().values().stream()
                    .map(template -> template.id()).collect(Collectors.toUnmodifiableSet());
            for (DatapackResourceLoader.JsonResource resource : DatapackResourceLoader.jsonResources(server, RESOURCE_ROOT)) {
                ResourceLocation source = resource.location();
                var root = DatapackResourceLoader.readObject(source, "quest scene", resource.resource()).orElse(null);
                SceneParser.ParseResult parsed = SceneParser.parse(source, root);
                List<SceneDiagnostic> combined = parsed.diagnostics().stream()
                        .map(diagnostic -> diagnostic.atSource(source)).collect(Collectors.toCollection(ArrayList::new));
                if (parsed.valid()) {
                    SceneCompiler.CompileResult compiled = SceneCompiler.compile(parsed.resource(), encounterTemplates);
                    combined.addAll(compiled.diagnostics().stream().map(diagnostic -> diagnostic.atSource(source)).toList());
                    boolean fatal = combined.stream().anyMatch(diagnostic -> diagnostic.severity() == SceneDiagnostic.Severity.ERROR);
                    if (!fatal && compiled.scene() != null) scenes.put(compiled.scene().id(), compiled.scene());
                    diagnostics.put(parsed.resource().id(), List.copyOf(combined));
                } else {
                    diagnostics.put(source, List.copyOf(combined));
                }
            }
            cache = new Cache(server, Map.copyOf(scenes), Map.copyOf(diagnostics));
            return cache;
        }
    }

    public record ContentSnapshot(
            Map<ResourceLocation, CompiledScene> scenes,
            Map<ResourceLocation, List<SceneDiagnostic>> diagnostics) {
    }

    private record Cache(MinecraftServer server, Map<ResourceLocation, CompiledScene> scenes,
                         Map<ResourceLocation, List<SceneDiagnostic>> diagnostics) {
    }
}
