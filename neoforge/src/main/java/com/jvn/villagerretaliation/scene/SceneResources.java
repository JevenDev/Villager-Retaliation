package com.jvn.villagerretaliation.scene;

import com.jvn.villagerretaliation.scene.compiler.SceneCompiler;
import com.jvn.villagerretaliation.scene.compiler.SceneDiagnostic;
import com.jvn.villagerretaliation.scene.compiler.SceneParser;
import com.jvn.villagerretaliation.scene.model.CompiledScene;
import com.jvn.villagerretaliation.util.DatapackResourceLoader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

public final class SceneResources {
    public static final String RESOURCE_ROOT = "quest_scenes";
    private static volatile Cache cache = new Cache(null, Map.of(), Map.of());

    private SceneResources() {
    }

    public static Optional<CompiledScene> scene(MinecraftServer server, ResourceLocation id) {
        return Optional.ofNullable(load(server).scenes().get(id));
    }

    public static List<CompiledScene> scenes(MinecraftServer server) {
        return List.copyOf(load(server).scenes().values());
    }

    public static Map<ResourceLocation, List<SceneDiagnostic>> diagnostics(MinecraftServer server) {
        return load(server).diagnostics();
    }

    public static void warm(MinecraftServer server) {
        load(server);
    }

    public static void clearCache() {
        cache = new Cache(null, Map.of(), Map.of());
    }

    public static void installTestScenes(MinecraftServer server, List<CompiledScene> scenes) {
        Map<ResourceLocation, CompiledScene> values = new LinkedHashMap<>();
        if (scenes != null) scenes.stream().filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparing(value -> value.id().toString())).forEach(value -> values.put(value.id(), value));
        cache = new Cache(server, Map.copyOf(values), Map.of());
    }

    private static Cache load(MinecraftServer server) {
        Cache current = cache;
        if (current.server() == server) return current;
        synchronized (SceneResources.class) {
            current = cache;
            if (current.server() == server) return current;
            Map<ResourceLocation, CompiledScene> scenes = new LinkedHashMap<>();
            Map<ResourceLocation, List<SceneDiagnostic>> diagnostics = new LinkedHashMap<>();
            for (DatapackResourceLoader.JsonResource resource : DatapackResourceLoader.jsonResources(server, RESOURCE_ROOT)) {
                ResourceLocation source = resource.location();
                var root = DatapackResourceLoader.readObject(source, "quest scene", resource.resource()).orElse(null);
                SceneParser.ParseResult parsed = SceneParser.parse(source, root);
                List<SceneDiagnostic> combined = new ArrayList<>(parsed.diagnostics());
                if (parsed.resource() != null) {
                    SceneCompiler.CompileResult compiled = SceneCompiler.compile(parsed.resource());
                    combined.addAll(compiled.diagnostics());
                    if (compiled.scene() != null) scenes.put(compiled.scene().id(), compiled.scene());
                    diagnostics.put(parsed.resource().id(), List.copyOf(combined));
                } else {
                    diagnostics.put(source, List.copyOf(combined));
                }
            }
            cache = new Cache(server, Map.copyOf(scenes), Map.copyOf(diagnostics));
            return cache;
        }
    }

    private record Cache(MinecraftServer server, Map<ResourceLocation, CompiledScene> scenes,
                         Map<ResourceLocation, List<SceneDiagnostic>> diagnostics) {
    }
}
