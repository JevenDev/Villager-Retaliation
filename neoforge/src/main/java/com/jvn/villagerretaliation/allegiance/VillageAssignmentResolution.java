package com.jvn.villagerretaliation.allegiance;

import java.util.List;
import java.util.Set;

/** A read-only explanation of the evidence available for a village assignment. */
public record VillageAssignmentResolution(
        Status status,
        VillageAllegianceId selected,
        List<Candidate> candidates,
        boolean observationComplete) {
    public VillageAssignmentResolution {
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
        if (status != Status.RESOLVED) {
            selected = null;
        }
    }

    public enum Status {
        RESOLVED,
        AMBIGUOUS,
        INCOMPLETE,
        NONE
    }

    public enum Evidence {
        OCCUPIED_POI_CLUSTER,
        CURRENT_FOOTPRINT,
        HOME_POI,
        JOB_SITE_POI,
        PARENT_ALLEGIANCE
    }

    public record Candidate(
            VillageAllegianceId id,
            int score,
            double distanceSquared,
            Set<Evidence> evidence) {
        public Candidate {
            evidence = evidence == null ? Set.of() : Set.copyOf(evidence);
        }
    }
}
