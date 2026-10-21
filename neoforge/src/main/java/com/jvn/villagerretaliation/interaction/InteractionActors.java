package com.jvn.villagerretaliation.interaction;

import com.jvn.villagerretaliation.dialogue.NarrativeActors;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

public final class InteractionActors {
    private static final Map<ResourceLocation, Registration> ACTORS = new HashMap<>();

    private InteractionActors() { }

    public static synchronized Registration register(ResourceLocation entityType, InteractionActorAdapter adapter) {
        Objects.requireNonNull(entityType);
        Objects.requireNonNull(adapter);
        if (ACTORS.containsKey(entityType)) throw new IllegalStateException("Interaction actor is already registered: " + entityType);
        var narrative = NarrativeActors.register(entityType, (level, player, actor) -> adapter.context(player, actor));
        var registration = new Registration(entityType, adapter, narrative);
        ACTORS.put(entityType, registration);
        return registration;
    }

    static synchronized Registration find(LivingEntity actor) {
        return ACTORS.get(BuiltInRegistries.ENTITY_TYPE.getKey(actor.getType()));
    }

    public static final class Registration implements AutoCloseable {
        private final ResourceLocation type;
        final InteractionActorAdapter adapter;
        private final NarrativeActors.Registration narrative;
        private boolean closed;

        private Registration(ResourceLocation type, InteractionActorAdapter adapter, NarrativeActors.Registration narrative) {
            this.type = type;
            this.adapter = adapter;
            this.narrative = narrative;
        }

        boolean active() {
            synchronized (InteractionActors.class) { return !this.closed && ACTORS.get(this.type) == this; }
        }

        @Override public void close() {
            synchronized (InteractionActors.class) {
                if (this.closed) return;
                this.closed = true;
                ACTORS.remove(this.type, this);
            }
            try { InteractionService.unregister(this); }
            finally { this.narrative.close(); }
        }
    }
}
