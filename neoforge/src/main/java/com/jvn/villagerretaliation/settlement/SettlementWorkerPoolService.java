package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.HiredVillagerRoles;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;

final class SettlementWorkerPoolService {
    private SettlementWorkerPoolService() { }

    static void schedule(ServerLevel level, SettlementRecord town, long now, List<Runnable> work) {
        if (!town.workerPools().isEmpty()) work.add(SettlementWork.lazy(() -> cycle(level, town, now)));
    }

    static Runnable cycle(ServerLevel level, SettlementRecord town, long now) {
        var work = new ArrayList<Runnable>();
        work.add(() -> {
            town.reconcileWorkerPoolCooldowns(now);
            town.validateWorkerPoolAuthorities();
        });
        for (var id : town.workerPools().keySet().stream().sorted().toList()) work.add(SettlementWork.lazy(() -> {
            var pool = town.workerPools().get(id);
            if (pool == null || !pool.authorized(town)) return () -> {};
            String failure = town.workerPoolConfigurationFailure(id, pool.configuration(), true);
            if (!failure.isEmpty()) {
                town.suspendWorkerPool(id, failure);
                return () -> {};
            }
            return SettlementWork.guard(new Plan(level, town, pool, now).work(), () -> pool.authorized(town));
        }));
        return new SettlementWork(work);
    }

    private static final class Plan {
        private final ServerLevel level;
        private final SettlementRecord town;
        private final SettlementWorkerPool pool;
        private final long now;
        private SettlementWorkerPoolNeeds needs;
        private boolean understaffed;
        private int member;
        private int site;
        private Villager candidate;
        private Choice best;

        private Plan(ServerLevel level, SettlementRecord town, SettlementWorkerPool pool, long now) {
            this.level = level;
            this.town = town;
            this.pool = pool;
            this.now = now;
        }

        private Runnable work() {
            return new SettlementWork(List.of(
                    SettlementWork.each(this.pool.configuration().worksites(), staffing -> {
                        if (this.understaffed) return;
                        var plan = this.town.worksitePlans().get(staffing.area());
                        int target = Math.clamp(plan == null ? 1 : plan.desiredStaff(), staffing.minimum(), staffing.maximum());
                        this.understaffed = SettlementWorkerPoolAssignment.count(this.town, staffing.area()) < target;
                    }),
                    SettlementWork.lazy(() -> this.understaffed ? plan() : () -> {})));
        }

        private Runnable plan() {
            return new SettlementWork(List.of(
                    SettlementWork.lazy(() -> SettlementDemandService.plan(this.level, this.town,
                            snapshot -> this.needs = new SettlementWorkerPoolNeeds(this.level, this.town, snapshot))),
                    SettlementWork.repeat(this::step),
                    SettlementWork.lazy(() -> this.best == null ? () -> {}
                            : SettlementDemandService.plan(this.level, this.town, snapshot -> commit(
                                    new SettlementWorkerPoolNeeds(this.level, this.town, snapshot))))));
        }

        private boolean step() {
            if (this.member >= this.pool.configuration().residents().size()) return true;
            if (this.candidate == null) {
                var candidate = SettlementWorkerPoolAssignment.inspect(this.level, this.town, this.pool,
                        this.pool.configuration().residents().get(this.member), this.now);
                if (!candidate.eligible()) {
                    this.member++;
                    return false;
                }
                this.candidate = candidate.villager();
                this.site = 0;
            }
            if (this.site >= this.pool.configuration().worksites().size()) {
                this.candidate = null;
                this.member++;
                return false;
            }
            var staffing = this.pool.configuration().worksites().get(this.site++);
            if (!SettlementWorkerPoolAssignment.destination(this.level, this.town, this.pool, this.candidate, staffing.area()).isEmpty()) return false;
            var choice = choice(this.candidate, staffing, this.needs);
            if (choice != null && (this.best == null || choice.compareTo(this.best) < 0)) this.best = choice;
            return false;
        }

        private Choice choice(Villager candidate, SettlementWorkerPool.Staffing destination, SettlementWorkerPoolNeeds needs) {
            var from = this.town.duties().get(candidate.getUUID());
            var source = source(from);
            var before = source == null ? SettlementWorkerPoolNeeds.Urgency.NONE
                    : SettlementWorkerPoolNeeds.urgency(this.town, source, needs.area(from),
                            Math.max(0, SettlementWorkerPoolAssignment.count(this.town, from) - 1));
            var target = needs.area(destination.area());
            var urgency = SettlementWorkerPoolNeeds.urgency(this.town, destination, target,
                    SettlementWorkerPoolAssignment.count(this.town, destination.area()));
            if (urgency.compareTo(before) <= 0) return null;
            var area = this.town.workplace(destination.area());
            int skill = HiredVillagerRoles.roleScore(this.level, candidate, area.type().role());
            var worker = this.town.workers().get(candidate.getUUID());
            var position = this.level.getEntity(candidate.getUUID()) == candidate || worker == null ? candidate.blockPosition() : worker.position();
            return new Choice(candidate.getUUID(), from, destination.area(), urgency, skill, position.distSqr(area.center()));
        }

        private SettlementWorkerPool.Staffing source(UUID from) {
            if (from == null || this.town.workplace(from) == null) return null;
            return this.pool.configuration().worksites().stream().filter(site -> site.area().equals(from)).findFirst()
                    .orElseGet(() -> SettlementWorkerPool.Staffing.defaults(this.town, from));
        }

        private void commit(SettlementWorkerPoolNeeds fresh) {
            if (!this.pool.authorized(this.town) || !fresh.current() || this.best == null
                    || !Objects.equals(this.best.from(), this.town.duties().get(this.best.resident()))) return;
            var candidate = SettlementWorkerPoolAssignment.inspect(this.level, this.town, this.pool, this.best.resident(), this.now);
            if (!candidate.eligible()) return;
            var destination = this.pool.configuration().worksites().stream().filter(site -> site.area().equals(this.best.to())).findFirst().orElse(null);
            if (destination == null || !SettlementWorkerPoolAssignment.destination(this.level, this.town, this.pool,
                    candidate.villager(), destination.area()).isEmpty()) return;
            var choice = choice(candidate.villager(), destination, fresh);
            if (choice == null) return;
            var target = fresh.area(choice.to());
            var source = fresh.area(choice.from());
            var reason = target.protectedShortage() ? SettlementWorkerPool.Reason.PROTECTED_SHORTAGE
                    : choice.from() != null && !source.active() && source.demand() != null ? SettlementWorkerPool.Reason.COMPLETED_DEMAND
                    : SettlementWorkerPoolAssignment.count(this.town, choice.to()) < destination.minimum() ? SettlementWorkerPool.Reason.MINIMUM
                    : choice.from() != null && target.priority() > source.priority() ? SettlementWorkerPool.Reason.PRIORITY
                    : SettlementWorkerPool.Reason.STAFFING;
            UUID demand = reason == SettlementWorkerPool.Reason.COMPLETED_DEMAND ? source.demand() : target.demand();
            SettlementWorkforceService.assignFromPool(this.level, this.town, this.pool,
                    new SettlementWorkerPool.Move(this.now, choice.resident(), choice.from(), choice.to(), reason, demand, target.priority()));
        }
    }

    private record Choice(UUID resident, UUID from, UUID to, SettlementWorkerPoolNeeds.Urgency urgency,
                          int skill, double distance) implements Comparable<Choice> {
        @Override
        public int compareTo(Choice other) {
            int comparison = other.urgency.compareTo(this.urgency);
            if (comparison == 0) comparison = Integer.compare(other.skill, this.skill);
            if (comparison == 0) comparison = Double.compare(this.distance, other.distance);
            if (comparison == 0) comparison = this.resident.compareTo(other.resident);
            return comparison != 0 ? comparison : this.to.compareTo(other.to);
        }
    }
}
