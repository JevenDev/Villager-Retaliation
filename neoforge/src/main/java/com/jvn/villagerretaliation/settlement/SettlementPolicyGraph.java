package com.jvn.villagerretaliation.settlement;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.UUID;

public final class SettlementPolicyGraph {
    private SettlementPolicyGraph() { }

    public record Result(List<SettlementSupplyPolicy.Step> producersFirst, String blocker, UUID step) {
        public Result { producersFirst = List.copyOf(producersFirst); }
        public boolean valid() { return blocker.isEmpty(); }
    }

    public static Result inspect(SettlementSupplyPolicy.Configuration configuration) {
        if (configuration.steps().isEmpty()) {
            if (!configuration.connections().isEmpty()) return invalid("policy_connection_missing", null);
            return configuration.purchases().enabled() && configuration.purchases().approvals().stream().anyMatch(approval -> approval.consumer() == null)
                    ? new Result(List.of(), "", null) : invalid("policy_chain_empty", null);
        }
        var steps = new HashMap<UUID, SettlementSupplyPolicy.Step>();
        configuration.steps().forEach(step -> steps.put(step.id(), step));
        var remaining = new HashMap<UUID, Integer>();
        var consumers = new HashMap<UUID, List<UUID>>();
        var dependencies = new HashMap<UUID, List<UUID>>();
        var supplied = new HashSet<Input>();
        var roots = new HashSet<UUID>();
        for (var step : configuration.steps()) remaining.put(step.id(), 0);
        for (var connection : configuration.connections()) {
            if (!steps.containsKey(connection.producer()) || connection.consumer() != null && !steps.containsKey(connection.consumer()))
                return invalid("policy_connection_missing", connection.producer());
            if (!supplied.add(new Input(connection.consumer(), connection.requirement())))
                return invalid("policy_connection_conflict", connection.consumer());
            if (connection.consumer() == null) roots.add(connection.producer());
            else {
                remaining.merge(connection.consumer(), 1, Integer::sum);
                consumers.computeIfAbsent(connection.producer(), ignored -> new ArrayList<>()).add(connection.consumer());
                dependencies.computeIfAbsent(connection.consumer(), ignored -> new ArrayList<>()).add(connection.producer());
            }
        }
        var ready = new PriorityQueue<UUID>();
        remaining.forEach((id, count) -> { if (count == 0) ready.add(id); });
        var ordered = new ArrayList<SettlementSupplyPolicy.Step>();
        while (!ready.isEmpty()) {
            var id = ready.remove();
            ordered.add(steps.get(id));
            for (var consumer : consumers.getOrDefault(id, List.of()))
                if (remaining.merge(consumer, -1, Integer::sum) == 0) ready.add(consumer);
        }
        if (ordered.size() != steps.size()) return invalid("policy_cycle",
                remaining.entrySet().stream().filter(entry -> entry.getValue() > 0).map(Map.Entry::getKey).sorted().findFirst().orElseThrow());
        if (roots.isEmpty()) return invalid("policy_target_unconnected", null);
        var connected = new HashSet<UUID>();
        for (var root : roots) collect(root, dependencies, connected);
        if (connected.size() != steps.size()) return invalid("policy_step_unused",
                steps.keySet().stream().filter(id -> !connected.contains(id)).sorted().findFirst().orElseThrow());
        return new Result(ordered, "", null);
    }

    private static void collect(UUID id, Map<UUID, List<UUID>> dependencies, Set<UUID> connected) {
        if (!connected.add(id)) return;
        for (var dependency : dependencies.getOrDefault(id, List.of())) collect(dependency, dependencies, connected);
    }

    private static Result invalid(String blocker, UUID step) {
        return new Result(List.of(), blocker, step);
    }

    private record Input(UUID consumer, UUID requirement) { }
}
