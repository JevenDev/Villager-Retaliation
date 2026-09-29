package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.network.VillagerReputationNetworking;
import com.jvn.villagerretaliation.network.VillagerReputationNoticeKind;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;

final class SettlementNotifications {
    private static final WeakHashMap<SettlementRecord, Observation> observations = new WeakHashMap<>();

    private SettlementNotifications() { }

    static void update(ServerLevel level, SettlementRecord town, long now) {
        var player = level.getServer().getPlayerList().getPlayer(town.leader());
        if (player == null) {
            observations.remove(town);
            return;
        }
        var history = town.history();
        UUID latest = history.isEmpty() ? null : history.getLast().id();
        var state = observations.get(town);
        if (state == null || !state.leader.equals(town.leader())) {
            observations.put(town, new Observation(town.leader(), latest));
            return;
        }
        String name = VillageAllegianceRegistrySavedData.get(level).record(town.id())
                .map(value -> value.displayName()).orElse("Settlement");
        if (!java.util.Objects.equals(latest, state.event)) {
            int start = 0;
            for (int index = 0; index < history.size(); index++) if (history.get(index).id().equals(state.event)) start = index + 1;
            var important = history.subList(start, history.size()).stream().filter(event -> switch (event.type()) {
                case STAGE_ADVANCED, PROJECT_COMPLETED, RESIDENT_DEPARTED, LEADERSHIP_TRANSFERRED -> true;
                default -> false;
            }).toList();
            if (VillagerRetaliationConfig.SETTLEMENT_STATE_NOTIFICATIONS.get() && !important.isEmpty()) {
                var event = important.getLast();
                String change = switch (event.type()) {
                    case STAGE_ADVANCED -> "advanced to a new stage";
                    case PROJECT_COMPLETED -> "completed a project";
                    case RESIDENT_DEPARTED -> "had a resident depart";
                    default -> "changed leadership";
                };
                VillagerReputationNetworking.sendNotice(player, name + " " + change + ". Open settlement History for details.",
                        VillagerReputationNoticeKind.DEFAULT);
            }
            state.event = latest;
        }
        if (!VillagerRetaliationConfig.SETTLEMENT_PROBLEM_NOTIFICATIONS.get()) {
            state.problems.clear();
            return;
        }
        var issues = SettlementIssueService.issues(level, town, SettlementProgressionService.assess(level, town)).stream()
                .filter(issue -> issue.diagnostic().severity() == SettlementWorkerDiagnostics.Severity.WARNING
                        || issue.diagnostic().severity() == SettlementWorkerDiagnostics.Severity.DANGER).toList();
        var keys = issues.stream().map(issue -> issue.id() + ":" + issue.cause()).collect(java.util.stream.Collectors.toSet());
        int fresh = state.problems.update(keys, now);
        if (fresh > 0) {
            VillagerReputationNetworking.sendNotice(player, name + ": " + fresh
                    + " new persistent problem(s). Open settlement Needs attention for causes and recovery steps.",
                    VillagerReputationNoticeKind.DEFAULT);
        }
    }

    static final class Problems {
        private Set<String> previous = Set.of();
        private final Set<String> reported = new HashSet<>();
        private long nextAt;

        int update(Set<String> current, long now) {
            this.reported.retainAll(current);
            var fresh = new HashSet<>(current);
            fresh.retainAll(this.previous);
            fresh.removeAll(this.reported);
            this.previous = Set.copyOf(current);
            if (fresh.isEmpty() || now < this.nextAt) return 0;
            this.reported.addAll(fresh);
            this.nextAt = now + 1_200L;
            return fresh.size();
        }

        void clear() {
            this.previous = Set.of();
            this.reported.clear();
        }
    }

    private static final class Observation {
        private final UUID leader;
        private UUID event;
        private final Problems problems = new Problems();

        Observation(UUID leader, UUID event) {
            this.leader = leader;
            this.event = event;
        }
    }
}
