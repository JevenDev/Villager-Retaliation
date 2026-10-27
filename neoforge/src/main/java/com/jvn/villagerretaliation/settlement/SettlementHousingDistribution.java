package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.network.SettlementManagementPayload.Bed;
import com.jvn.villagerretaliation.network.SettlementViewBatchPayload.Target;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;

public final class SettlementHousingDistribution {
    private SettlementHousingDistribution() { }

    public static Plan plan(Collection<UUID> residents, Collection<BlockPos> selectedBeds, List<Bed> beds) {
        var homes = new LinkedHashMap<UUID, BlockPos>();
        var candidates = new LinkedHashMap<BlockPos, Bed>();
        var positions = new LinkedHashSet<>(selectedBeds);
        for (var bed : beds) {
            if (bed.resident() != null) homes.put(bed.resident(), bed.position());
            if (positions.contains(bed.position())) candidates.put(bed.position(), bed);
        }
        var available = candidates.values().stream().filter(Bed::available)
                .sorted(Comparator.comparing(Bed::position)).map(Bed::position).iterator();
        var targets = new ArrayList<Target>();
        int unchanged = 0;
        int unassigned = 0;
        for (var resident : new LinkedHashSet<>(residents)) {
            var current = homes.get(resident);
            if (current != null && candidates.containsKey(current)) unchanged++;
            else if (available.hasNext()) targets.add(new Target(resident, null, available.next(), current));
            else unassigned++;
        }
        return new Plan(List.copyOf(targets), unchanged, unassigned);
    }

    public record Plan(List<Target> targets, int unchanged, int unassigned) { }
}
