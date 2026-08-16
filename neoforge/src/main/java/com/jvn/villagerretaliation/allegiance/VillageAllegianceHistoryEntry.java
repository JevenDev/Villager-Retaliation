package com.jvn.villagerretaliation.allegiance;

import java.util.UUID;

/** One bounded, durable change in an entity's village allegiance. */
public record VillageAllegianceHistoryEntry(
        AllegianceState previousState,
        VillageAllegianceId previousVillage,
        AllegianceState newState,
        VillageAllegianceId newVillage,
        AllegianceAssignmentSource source,
        long gameTime,
        UUID responsiblePlayer) {
}
