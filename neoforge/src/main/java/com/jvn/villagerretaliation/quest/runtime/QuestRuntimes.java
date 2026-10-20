package com.jvn.villagerretaliation.quest.runtime;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

public final class QuestRuntimes {
    private static final Map<String, QuestRuntimeAccess> NAMESPACES = new HashMap<>();
    private static final Map<ResourceLocation, QuestRuntimeAccess> RUNTIMES = new HashMap<>();

    private QuestRuntimes() { }

    public static ResourceLocation defaultId() { return VrQuestRuntime.INSTANCE.id(); }

    public static synchronized Registration register(String namespace, QuestRuntimeAccess runtime) {
        ResourceLocation.fromNamespaceAndPath(namespace, "quests");
        Objects.requireNonNull(runtime);
        var id = Objects.requireNonNull(runtime.id());
        if (id.equals(defaultId())) throw new IllegalArgumentException("The VR quest runtime identity is reserved.");
        if (NAMESPACES.containsKey(namespace)) throw new IllegalStateException("Quest namespace already has a runtime: " + namespace);
        var existing = RUNTIMES.get(id);
        if (existing != null && existing != runtime) throw new IllegalStateException("Quest runtime identity is already registered: " + id);
        NAMESPACES.put(namespace, runtime);
        RUNTIMES.put(id, runtime);
        return new Registration(namespace, runtime);
    }

    public static synchronized QuestRuntimeAccess forQuest(ResourceLocation quest) {
        return NAMESPACES.getOrDefault(Objects.requireNonNull(quest).getNamespace(), VrQuestRuntime.INSTANCE);
    }

    public static synchronized Optional<QuestRuntimeAccess> find(ResourceLocation id) {
        return id.equals(defaultId()) ? Optional.of(VrQuestRuntime.INSTANCE) : Optional.ofNullable(RUNTIMES.get(id));
    }

    public static QuestProgressModel progress(ServerLevel level, java.util.UUID player, ResourceLocation quest) {
        return quest == null ? null : forQuest(quest).progress(level).get(player, quest);
    }

    public static final class Registration implements AutoCloseable {
        private final String namespace;
        private final ResourceLocation id;
        private final QuestRuntimeAccess runtime;
        private boolean closed;

        private Registration(String namespace, QuestRuntimeAccess runtime) {
            this.namespace = namespace;
            this.id = runtime.id();
            this.runtime = runtime;
        }

        @Override public void close() {
            synchronized (QuestRuntimes.class) {
                if (this.closed) return;
                this.closed = true;
                NAMESPACES.remove(this.namespace, this.runtime);
                if (NAMESPACES.values().stream().noneMatch(value -> value == this.runtime)) RUNTIMES.remove(this.id, this.runtime);
            }
        }
    }
}
