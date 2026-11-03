package com.jvn.villagerretaliation.settlement;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerLevel;

public final class SettlementEncounterBehaviors {
    public interface Behavior {
        boolean hostile();
        void advance(ServerLevel level, SettlementFoodRequest leader, List<SettlementFoodRequest> participants);
        boolean resolved(SettlementFoodRequest leader);
    }
    private static final Map<String, Behavior> TYPES = new ConcurrentHashMap<>();
    static {
        register("attack", new Behavior() {
            public boolean hostile() { return true; }
            public void advance(ServerLevel level, SettlementFoodRequest leader, List<SettlementFoodRequest> participants) {
                SettlementJourneyEncounters.round(level, leader, participants);
            }
            public boolean resolved(SettlementFoodRequest leader) {
                return leader.journey.encounter.attackers.stream().allMatch(actor -> actor.getFloat("Health") <= 0);
            }
        });
        register("rest", new Behavior() {
            public boolean hostile() { return false; }
            public void advance(ServerLevel level, SettlementFoodRequest leader, List<SettlementFoodRequest> participants) { }
            public boolean resolved(SettlementFoodRequest leader) { return false; }
        });
    }
    private SettlementEncounterBehaviors() { }
    public static void register(String id, Behavior behavior) {
        if (id == null || id.isBlank() || behavior == null || TYPES.putIfAbsent(id, behavior) != null)
            throw new IllegalArgumentException("Duplicate or invalid journey encounter behavior " + id);
    }
    static Behavior get(String id) {
        var behavior = TYPES.get(id);
        if (behavior == null) throw new IllegalArgumentException("Unknown journey encounter behavior " + id);
        return behavior;
    }
}
