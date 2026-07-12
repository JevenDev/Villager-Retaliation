package com.jvn.villagerretaliation.allegiance;

import com.jvn.villagerretaliation.party.PartyRecord;
import com.jvn.villagerretaliation.party.PartyService;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class VillageCombatAuthorizationService {
    private static final long AUTHORIZATION_TTL_TICKS = 100L;
    private static final Map<CombatPair, Authorization> AUTHORIZATIONS = new HashMap<>();
    private static final Map<UUID, ProjectileAuthorization> PROJECTILES = new HashMap<>();

    private VillageCombatAuthorizationService() {
    }

    public static boolean authorize(
            ServerLevel level,
            LivingEntity actor,
            LivingEntity target) {
        PartyRecord actorParty = PartyService.getPartyForEntity(actor).orElse(null);
        PartyRecord targetParty = PartyService.getPartyForEntity(target).orElse(null);
        if (actorParty == null || targetParty != null && actorParty.id().equals(targetParty.id())) {
            return false;
        }
        long expires = level.getServer().overworld().getGameTime() + AUTHORIZATION_TTL_TICKS;
        AUTHORIZATIONS.put(new CombatPair(actor.getUUID(), target.getUUID()),
                new Authorization(actorParty.id(), targetParty == null ? null : targetParty.id(), expires));
        return true;
    }

    public static boolean isAuthorized(LivingEntity actor, LivingEntity target) {
        if (actor == null || target == null || !(actor.level() instanceof ServerLevel level)) {
            return false;
        }
        Authorization authorization = AUTHORIZATIONS.get(new CombatPair(actor.getUUID(), target.getUUID()));
        if (authorization == null || authorization.expiresGameTime() < level.getServer().overworld().getGameTime()) {
            return false;
        }
        PartyRecord actorParty = PartyService.getPartyForEntity(actor).orElse(null);
        PartyRecord targetParty = PartyService.getPartyForEntity(target).orElse(null);
        return actorParty != null
                && actorParty.id().equals(authorization.actorPartyId())
                && Objects.equals(targetParty == null ? null : targetParty.id(), authorization.targetPartyId())
                && (targetParty == null || !actorParty.id().equals(targetParty.id()));
    }

    public static void associateProjectile(Entity projectile, LivingEntity actor, LivingEntity target) {
        if (projectile == null || actor == null || target == null || !(actor.level() instanceof ServerLevel level)) {
            return;
        }
        if (isAuthorized(actor, target)) {
            PROJECTILES.put(projectile.getUUID(), new ProjectileAuthorization(
                    actor.getUUID(), target.getUUID(),
                    level.getServer().overworld().getGameTime() + AUTHORIZATION_TTL_TICKS));
        }
    }

    public static boolean projectileAuthorized(Entity projectile, LivingEntity actor, LivingEntity target) {
        if (projectile == null || actor == null || target == null || !(actor.level() instanceof ServerLevel level)) {
            return false;
        }
        ProjectileAuthorization authorization = PROJECTILES.get(projectile.getUUID());
        return authorization != null
                && authorization.actorId().equals(actor.getUUID())
                && authorization.targetId().equals(target.getUUID())
                && authorization.expiresGameTime() >= level.getServer().overworld().getGameTime()
                && isAuthorized(actor, target);
    }

    public static void clearFor(Entity entity) {
        if (entity == null) {
            return;
        }
        UUID id = entity.getUUID();
        AUTHORIZATIONS.keySet().removeIf(key -> key.actorId().equals(id) || key.targetId().equals(id));
        PROJECTILES.entrySet().removeIf(entry -> entry.getKey().equals(id)
                || entry.getValue().actorId().equals(id)
                || entry.getValue().targetId().equals(id));
    }

    public static void onServerTickPost(ServerTickEvent.Post event) {
        long now = event.getServer().overworld().getGameTime();
        AUTHORIZATIONS.entrySet().removeIf(entry -> entry.getValue().expiresGameTime() < now);
        PROJECTILES.entrySet().removeIf(entry -> entry.getValue().expiresGameTime() < now);
    }

    public static void onServerStopping(ServerStoppingEvent event) {
        clearRuntimeState();
    }

    public static void clearRuntimeState() {
        AUTHORIZATIONS.clear();
        PROJECTILES.clear();
    }

    public static int authorizationCount() {
        return AUTHORIZATIONS.size() + PROJECTILES.size();
    }

    private record CombatPair(UUID actorId, UUID targetId) {
    }

    private record Authorization(
            UUID actorPartyId,
            UUID targetPartyId,
            long expiresGameTime) {
    }

    private record ProjectileAuthorization(UUID actorId, UUID targetId, long expiresGameTime) {
    }
}
