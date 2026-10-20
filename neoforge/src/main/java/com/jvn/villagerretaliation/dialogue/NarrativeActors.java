package com.jvn.villagerretaliation.dialogue;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

public final class NarrativeActors {
    private static final Map<ResourceLocation, Factory> FACTORIES = new HashMap<>();

    private NarrativeActors() { }

    public static synchronized Registration register(ResourceLocation entityType, Factory factory) {
        Objects.requireNonNull(entityType);
        Objects.requireNonNull(factory);
        if (FACTORIES.putIfAbsent(entityType, factory) != null) throw new IllegalStateException("Narrative actor type is already registered: " + entityType);
        return new Registration(entityType, factory);
    }

    public static synchronized boolean isRegistered(LivingEntity entity) {
        return FACTORIES.containsKey(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()));
    }

    public static synchronized boolean hasRegistrations() { return !FACTORIES.isEmpty(); }

    public static Optional<DialogueRuntimeContext> context(ServerLevel level, ServerPlayer player, LivingEntity entity) {
        Factory factory;
        synchronized (NarrativeActors.class) { factory = FACTORIES.get(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType())); }
        if (factory == null || entity.isRemoved() || entity.level() != level || player.serverLevel() != level) return Optional.empty();
        var context = Objects.requireNonNull(factory.create(level, player, entity), "narrative actor context");
        if (context.level() != level || context.player() != player || context.speaker() != entity) {
            throw new IllegalStateException("Narrative actor factory returned different participants");
        }
        return Optional.of(context);
    }

    @FunctionalInterface
    public interface Factory {
        DialogueRuntimeContext create(ServerLevel level, ServerPlayer player, LivingEntity entity);
    }

    public record Registration(ResourceLocation entityType, Factory factory) implements AutoCloseable {
        @Override
        public void close() {
            synchronized (NarrativeActors.class) { FACTORIES.remove(entityType, factory); }
        }
    }
}
