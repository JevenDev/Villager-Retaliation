package com.jvn.villagerretaliation.dialogue.conversation;

import com.jvn.villagerretaliation.dialogue.DialogueRuntimeContext;
import com.jvn.villagerretaliation.dialogue.NarrativeActors;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;

public final class ConversationActors {
    private static final Map<ResourceLocation, Access> ACCESS = new HashMap<>();

    private ConversationActors() { }

    public static synchronized Registration register(ResourceLocation entityType, Access access) {
        Objects.requireNonNull(entityType);
        Objects.requireNonNull(access);
        if (ACCESS.putIfAbsent(entityType, access) != null) throw new IllegalStateException("Conversation actor type is already registered: " + entityType);
        return new Registration(entityType, access);
    }

    public static boolean supports(LivingEntity actor) {
        return actor instanceof Villager || NarrativeActors.isRegistered(actor);
    }

    public static DialogueRuntimeContext context(ServerPlayer player, LivingEntity actor) {
        if (actor instanceof Villager villager) return VrConversationActors.context(player, villager);
        return NarrativeActors.context(player.serverLevel(), player, actor)
                .orElseThrow(() -> new ConversationService.Unavailable("This conversation actor is unavailable."));
    }

    public static boolean matches(ServerPlayer player, ConversationDefinition definition, LivingEntity actor) {
        var access = access(actor);
        return actor != null && supports(actor) && actor.isAlive() && actor.level() == player.serverLevel()
                && access != null && access.matches(player, definition, actor);
    }

    public static boolean near(ServerPlayer player, LivingEntity actor) {
        var access = access(actor);
        return actor != null && player.isAlive() && actor.isAlive() && supports(actor)
                && player.serverLevel() == actor.level() && player.distanceToSqr(actor) <= 256.0
                && (access == null || access.conversable(player, actor));
    }

    public static boolean interrupted(ServerPlayer player, LivingEntity actor) {
        var access = access(actor);
        return access != null && access.interrupted(player, actor);
    }

    private static synchronized Access access(LivingEntity actor) {
        if (actor == null) return null;
        if (actor instanceof Villager) return VrConversationActors.INSTANCE;
        return ACCESS.get(BuiltInRegistries.ENTITY_TYPE.getKey(actor.getType()));
    }

    @FunctionalInterface
    public interface Access {
        boolean matches(ServerPlayer player, ConversationDefinition definition, LivingEntity actor);
        default boolean conversable(ServerPlayer player, LivingEntity actor) { return true; }
        default boolean interrupted(ServerPlayer player, LivingEntity actor) { return false; }
    }

    public record Registration(ResourceLocation entityType, Access access) implements AutoCloseable {
        @Override public void close() {
            synchronized (ConversationActors.class) { ACCESS.remove(entityType, access); }
        }
    }
}
