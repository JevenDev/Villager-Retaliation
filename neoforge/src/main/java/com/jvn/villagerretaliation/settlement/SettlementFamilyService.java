package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceApi;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.social.VillagerBirthService;
import com.jvn.villagerretaliation.social.VillagerBreedingPolicy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;

final class SettlementFamilyService {
    private SettlementFamilyService() {
    }

    static void update(ServerLevel level, SettlementRecord town, long now) {
        if (!SettlementPopulationService.canGrow(level, town)) {
            for (var id : List.copyOf(town.courtships().keySet())) town.removeCourtship(id);
            return;
        }
        var members = new HashSet<UUID>();
        VillageAllegianceRegistrySavedData.get(level).residentRoster(town.id(), now).forEach(member -> members.add(member.id()));
        if (members.size() < 2) {
            for (var id : List.copyOf(town.courtships().keySet())) town.removeCourtship(id);
            return;
        }
        SettlementNavigationService.update(level, town, now);
        var routes = new SettlementFamilyRoutes(level, town);
        var parents = new java.util.LinkedHashMap<UUID, Villager>();
        for (var id : members.stream().sorted().toList()) {
            var parent = parent(level, town, id, routes);
            if (parent != null) parents.put(id, parent);
        }
        var occupied = new HashSet<UUID>();
        for (var attempt : List.copyOf(town.courtships().values())) {
            var first = parents.get(attempt.first());
            var second = parents.get(attempt.second());
            var visit = visit(level, town, routes, first, second, attempt.bed());
            if (visit == null || !town.residents().get(attempt.first()).position().equals(attempt.firstStart())
                    || !town.residents().get(attempt.second()).position().equals(attempt.secondStart())) {
                town.removeCourtship(attempt.first());
                continue;
            }
            occupied.add(attempt.first());
            occupied.add(attempt.second());
            var advanced = attempt.advance(now);
            // visits include the return journey because unloaded parents retain their native anchors
            int duration = attempt.courtshipTicks() + visit.steps() * 2 * 10;
            if (advanced.progress() < duration) {
                town.recordCourtship(advanced);
            } else {
                town.removeCourtship(attempt.first());
                birth(level, town, first, second, visit.origin(), attempt.bed());
            }
        }
        var available = new ArrayList<>(parents.entrySet().stream().filter(entry -> !occupied.contains(entry.getKey())).toList());
        if (available.size() < 2 || !SettlementPopulationService.canGrow(level, town)) return;
        java.util.Collections.rotate(available, (int) Math.floorMod(now / 200L, available.size()));
        int started = 0;
        int checked = 0;
        for (var entry : available) {
            if (occupied.contains(entry.getKey())) continue;
            var partners = available.stream().filter(other -> !other.getKey().equals(entry.getKey()) && !occupied.contains(other.getKey()))
                    .sorted(java.util.Comparator.comparingDouble(other -> other.getValue().distanceToSqr(entry.getValue()))).toList();
            for (var other : partners) {
                if (++checked > 64 || started >= 4) return;
                var first = entry.getValue();
                var second = other.getValue();
                if (first.distanceToSqr(second) > 48.0D * 48.0D || !VillagerBreedingPolicy.evaluatePair(level, first, second).allowed()) continue;
                BlockPos bed = bed(level, town, routes, first, second);
                if (bed == null) continue;
                town.recordCourtship(new SettlementCourtship(entry.getKey(), other.getKey(),
                        town.residents().get(entry.getKey()).position(), town.residents().get(other.getKey()).position(),
                        bed, 275 + first.getRandom().nextInt(50), 0, now));
                occupied.add(entry.getKey());
                occupied.add(other.getKey());
                started++;
                break;
            }
        }
    }

    private static Villager parent(ServerLevel level, SettlementRecord town, UUID id, SettlementFamilyRoutes routes) {
        if (Math.floorMod(level.getDayTime(), 24_000L) >= 12_000L) return null;
        var care = town.residents().get(id);
        if (care == null) return null;
        for (var dimension : level.getServer().getAllLevels()) {
            var entity = dimension.getEntity(id);
            if (entity != null && (!(entity instanceof Villager villager) || !entity.isAlive() || entity.level() != level
                    || !entity.blockPosition().equals(care.position()) || SettlementWorkforceService.hasPersonalCommitment(villager)
                    || com.jvn.villagerretaliation.combat.downed.VillagerDownedService.isDowned(villager)
                    || villager.getTarget() != null || villager.getLastHurtByMob() != null || villager.isOnFire() || villager.isInLava()
                    || level.isPositionEntityTicking(entity.blockPosition())
                    || VillageAllegianceApi.canonicalPrimary(level, entity).filter(town.id()::equals).isEmpty())) return null;
        }
        var parent = SettlementPopulationService.familyContext(level, town, care);
        if (parent == null) return null;
        var standing = routes.standing(parent.blockPosition(), parent.isSleeping());
        if (standing == null) return null;
        parent.clearSleepingPos();
        parent.setPose(Pose.STANDING);
        parent.setPos(standing.getBottomCenter());
        return parent.canBreed() && VillagerBreedingPolicy.evaluateParent(level, parent).allowed() ? parent : null;
    }

    private static BlockPos bed(ServerLevel level, SettlementRecord town, SettlementFamilyRoutes routes, Villager first, Villager second) {
        var beds = town.areas().values().stream().filter(area -> area.enabled() && area.type() == SettlementAreaType.HOME)
                .map(area -> town.surveys().get(area.id())).filter(java.util.Objects::nonNull)
                .flatMap(survey -> survey.beds().stream()).distinct()
                .sorted(java.util.Comparator.comparingDouble(position -> position.distSqr(second.blockPosition()))).toList();
        for (var bed : beds) {
            if (town.courtships().values().stream().anyMatch(attempt -> bed.equals(attempt.bed()))) continue;
            if (visit(level, town, routes, first, second, bed) != null) return bed;
        }
        return null;
    }

    private static Visit visit(ServerLevel level, SettlementRecord town, SettlementFamilyRoutes routes,
                               Villager first, Villager second, BlockPos bed) {
        if (first == null || second == null || !first.canBreed() || !second.canBreed()
                || !VillagerBreedingPolicy.evaluatePair(level, first, second).allowed()
                || !vacant(level, town, bed) || bed.distSqr(second.blockPosition()) > 48.0D * 48.0D) return null;
        var approach = routes.route(first.blockPosition(), second.blockPosition());
        if (approach.isEmpty()) return null;
        var origin = approach.getLast();
        if (bed.distSqr(origin) > 48.0D * 48.0D) return null;
        var home = routes.route(origin, bed);
        return home.isEmpty() ? null : new Visit(origin, approach.size() - 1);
    }

    private static boolean vacant(ServerLevel level, SettlementRecord town, BlockPos bed) {
        if (!SettlementHousingService.usable(town, bed) || !SettlementHousingService.canUse(level, bed, null)) return false;
        var blocks = new SettlementBuildingSurvey.ObservedBlocks(level, null, town);
        if (SettlementBuildingSurvey.inspectBed(blocks, null, bed) != SettlementBuildingSurvey.BedState.AVAILABLE) return false;
        if (level.getPoiManager().existsAtPosition(PoiTypes.HOME, bed)) return level.getPoiManager().getFreeTickets(bed) > 0;
        if (level.getChunkSource().getChunkNow(bed.getX() >> 4, bed.getZ() >> 4) != null) return false;
        return Boolean.TRUE.equals(SettlementHousingService.availability(town, bed));
    }

    private static void birth(ServerLevel level, SettlementRecord town, Villager first, Villager second, BlockPos origin, BlockPos bed) {
        if (!vacant(level, town, bed) || !VillagerBirthService.validateBirth(level, first, second)) return;
        var firstPosition = first.position();
        first.setPos(origin.getBottomCenter());
        first.eatAndDigestFood();
        second.eatAndDigestFood();
        var child = first.getBreedOffspring(level, second);
        if (child != null) {
            first.setAge(6_000);
            second.setAge(6_000);
        }
        first.setPos(firstPosition);
        town.residents().get(first.getUUID()).completeFamilyAttempt(first);
        town.residents().get(second.getUUID()).completeFamilyAttempt(second);
        town.changed();
        if (child == null || child.isSpawnCancelled() || SettlementAnimalBirthService.existing(level, child.getUUID()) != null
                || SettlementSavedData.get(level).settlements().stream().anyMatch(existing -> existing.residents().containsKey(child.getUUID())
                        || existing.arrivals().containsKey(child.getUUID()) || existing.departures().containsKey(child.getUUID()))) return;
        child.setAge(-24_000);
        child.setPos(origin.getBottomCenter());
        VillagerBirthService.initializeNewborn(level, first, second, child, null);
        town.recordResident(SettlementResidentState.capture(child, town, level.getGameTime()));
        town.assignHome(child.getUUID(), bed);
        town.recordArrival(new SettlementArrival(child.getUUID(), child.saveWithoutId(new CompoundTag()), origin));
    }

    private record Visit(BlockPos origin, int steps) {
    }
}
