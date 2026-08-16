package com.jvn.villagerretaliation.allegiance;

import com.jvn.villagerretaliation.party.PartyRecord;
import com.jvn.villagerretaliation.party.PartyService;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class VillageCombatAuthorizationService {
    private static final long AUTHORIZATION_TTL_TICKS = 100L;
    private static final Map<CombatPair, Authorization> AUTHORIZATIONS = new HashMap<>();
    private static final Map<UUID, ProjectileAuthorization> PROJECTILES = new HashMap<>();
    private static final PriorityQueue<AuthorizationExpiry> AUTHORIZATION_EXPIRIES =
            new PriorityQueue<>((first, second) -> Long.compare(first.expiresGameTime(), second.expiresGameTime()));
    private static final PriorityQueue<ProjectileExpiry> PROJECTILE_EXPIRIES =
            new PriorityQueue<>((first, second) -> Long.compare(first.expiresGameTime(), second.expiresGameTime()));

    private VillageCombatAuthorizationService() {
    }

    public static boolean authorize(
            ServerLevel level,
            LivingEntity actor,
            LivingEntity target) {
        PartyRecord actorParty = PartyService.getPartyForEntity(actor).orElse(null);
        PartyRecord targetParty = PartyService.getPartyForEntity(target).orElse(null);
        if (actorParty == null || PartyService.areSameOrAllied(actorParty, targetParty)) {
            return false;
        }
        long expires = level.getServer().overworld().getGameTime() + AUTHORIZATION_TTL_TICKS;
        CombatPair pair = new CombatPair(actor.getUUID(), target.getUUID());
        AUTHORIZATIONS.put(pair,
                new Authorization(actorParty.id(), targetParty == null ? null : targetParty.id(), expires));
        AUTHORIZATION_EXPIRIES.add(new AuthorizationExpiry(pair, expires));
        return true;
    }

    public static boolean isAuthorized(LivingEntity actor, LivingEntity target) {
        if (actor == null || target == null || !(actor.level() instanceof ServerLevel level)) {
            return false;
        }
        CombatPair pair = new CombatPair(actor.getUUID(), target.getUUID());
        Authorization authorization = AUTHORIZATIONS.get(pair);
        if (authorization == null || authorization.expiresGameTime() < level.getServer().overworld().getGameTime()) {
            if (authorization != null) {
                AUTHORIZATIONS.remove(pair, authorization);
            }
            return false;
        }
        PartyRecord actorParty = PartyService.getPartyForEntity(actor).orElse(null);
        PartyRecord targetParty = PartyService.getPartyForEntity(target).orElse(null);
        return actorParty != null
                && actorParty.id().equals(authorization.actorPartyId())
                && Objects.equals(targetParty == null ? null : targetParty.id(), authorization.targetPartyId())
                && !PartyService.areSameOrAllied(actorParty, targetParty);
    }

    public static void associateProjectile(Entity projectile, LivingEntity actor, LivingEntity target) {
        if (projectile == null || actor == null || target == null || !(actor.level() instanceof ServerLevel level)) {
            return;
        }
        if (isAuthorized(actor, target)) {
            long expires = level.getServer().overworld().getGameTime() + AUTHORIZATION_TTL_TICKS;
            PROJECTILES.put(projectile.getUUID(), new ProjectileAuthorization(
                    actor.getUUID(), target.getUUID(),
                    expires));
            PROJECTILE_EXPIRIES.add(new ProjectileExpiry(projectile.getUUID(), expires));
        }
    }

    public static boolean projectileAuthorized(Entity projectile, LivingEntity actor, LivingEntity target) {
        if (projectile == null || actor == null || target == null || !(actor.level() instanceof ServerLevel level)) {
            return false;
        }
        UUID projectileId = projectile.getUUID();
        ProjectileAuthorization authorization = PROJECTILES.get(projectileId);
        long now = level.getServer().overworld().getGameTime();
        if (authorization != null && authorization.expiresGameTime() < now) {
            PROJECTILES.remove(projectileId, authorization);
            return false;
        }
        return authorization != null
                && authorization.actorId().equals(actor.getUUID())
                && authorization.targetId().equals(target.getUUID())
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
        pruneExpired(event.getServer().overworld().getGameTime());
    }

    private static void pruneExpired(long now) {
        while (!AUTHORIZATION_EXPIRIES.isEmpty()
                && AUTHORIZATION_EXPIRIES.peek().expiresGameTime() < now) {
            AuthorizationExpiry expiry = AUTHORIZATION_EXPIRIES.remove();
            Authorization current = AUTHORIZATIONS.get(expiry.pair());
            if (current != null && current.expiresGameTime() == expiry.expiresGameTime()) {
                AUTHORIZATIONS.remove(expiry.pair());
            }
        }
        while (!PROJECTILE_EXPIRIES.isEmpty()
                && PROJECTILE_EXPIRIES.peek().expiresGameTime() < now) {
            ProjectileExpiry expiry = PROJECTILE_EXPIRIES.remove();
            ProjectileAuthorization current = PROJECTILES.get(expiry.projectileId());
            if (current != null && current.expiresGameTime() == expiry.expiresGameTime()) {
                PROJECTILES.remove(expiry.projectileId());
            }
        }
    }

    public static void clearRuntimeState() {
        AUTHORIZATIONS.clear();
        PROJECTILES.clear();
        AUTHORIZATION_EXPIRIES.clear();
        PROJECTILE_EXPIRIES.clear();
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

    private record AuthorizationExpiry(CombatPair pair, long expiresGameTime) {
    }

    private record ProjectileExpiry(UUID projectileId, long expiresGameTime) {
    }
}
