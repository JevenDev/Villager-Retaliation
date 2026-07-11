package com.jvn.villagerretaliation.scene.actor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public record SceneCast(Map<String, SceneActorDeclaration> declarations, Map<String, SceneActorBinding> bindings) {
    public SceneCast {
        declarations = orderedDeclarations(declarations);
        bindings = orderedBindings(bindings);
        for (String alias : bindings.keySet()) {
            if (!declarations.containsKey(alias)) {
                throw new IllegalArgumentException("binding references undeclared actor alias " + alias);
            }
        }
    }

    public Optional<SceneActorBinding> binding(String alias) {
        return Optional.ofNullable(this.bindings.get(alias));
    }

    public List<String> missingRequiredAliases() {
        return this.declarations.values().stream()
                .filter(SceneActorDeclaration::required)
                .map(SceneActorDeclaration::alias)
                .filter(alias -> !this.bindings.containsKey(alias))
                .toList();
    }

    private static Map<String, SceneActorDeclaration> orderedDeclarations(Map<String, SceneActorDeclaration> values) {
        Map<String, SceneActorDeclaration> result = new LinkedHashMap<>();
        if (values != null) values.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    if (!entry.getKey().equals(entry.getValue().alias())) {
                        throw new IllegalArgumentException("actor declaration key must equal alias " + entry.getKey());
                    }
                    if (result.put(entry.getKey(), entry.getValue()) != null) {
                        throw new IllegalArgumentException("duplicate actor alias " + entry.getKey());
                    }
                });
        return Map.copyOf(result);
    }

    private static Map<String, SceneActorBinding> orderedBindings(Map<String, SceneActorBinding> values) {
        Map<String, SceneActorBinding> result = new LinkedHashMap<>();
        if (values != null) values.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> result.put(entry.getKey(), entry.getValue()));
        return Map.copyOf(result);
    }
}
