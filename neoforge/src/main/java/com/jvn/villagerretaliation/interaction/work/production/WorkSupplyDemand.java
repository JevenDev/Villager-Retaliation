package com.jvn.villagerretaliation.interaction.work.production;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;

public record WorkSupplyDemand(UUID id, UUID consumer, Kind kind, WorkSupplyRequirement requirement,
                               long immediate, long targetReserve, long protectedMinimum, int priority,
                               long availableNow, long committed, long incoming, long proposed, boolean known,
                               List<BlockPos> sources, BlockPos destination, Blocker blocker) {
    public WorkSupplyDemand {
        if (id == null || consumer == null || kind == null || requirement == null || immediate < 0
                || immediate > requirement.quantity() || targetReserve < 0 || protectedMinimum < 0
                || protectedMinimum > requirement.quantity() || priority < 0 || priority > 9
                || availableNow < 0 || committed < 0 || incoming < 0 || proposed < 0 || blocker == null) {
            throw new IllegalArgumentException("Invalid work supply demand");
        }
        sources = sources.stream().map(BlockPos::immutable).distinct().toList();
        destination = destination == null ? null : destination.immutable();
    }

    public long supplied() { return Math.min(requirement.quantity(), availableNow + committed); }
    public long missing() { return known ? Math.max(0, requirement.quantity() - supplied() - incoming) : -1; }
    public long immediateMissing() { return known ? Math.max(0, immediate - supplied()) : -1; }

    public static UUID identity(HolderLookup.Provider registries, UUID consumer, Kind kind, WorkSupplyRequirement requirement) {
        return UUID.nameUUIDFromBytes((consumer + ":demand:" + kind + ":" + requirement.semanticKey(registries)).getBytes(StandardCharsets.UTF_8));
    }

    public WorkSupplyDemand merge(WorkSupplyDemand other) {
        if (!id.equals(other.id) || !consumer.equals(other.consumer) || kind != other.kind
                || !requirement.sameGroup(other.requirement) || !java.util.Objects.equals(destination, other.destination)) {
            throw new IllegalArgumentException("Mismatched supply requirements");
        }
        var combinedSources = new java.util.LinkedHashSet<>(sources);
        combinedSources.addAll(other.sources);
        return new WorkSupplyDemand(id, consumer, kind, requirement.withQuantity(Math.addExact(requirement.quantity(), other.requirement.quantity())),
                Math.addExact(immediate, other.immediate), Math.max(targetReserve, other.targetReserve),
                Math.addExact(protectedMinimum, other.protectedMinimum), Math.max(priority, other.priority),
                Math.addExact(availableNow, other.availableNow), Math.addExact(committed, other.committed),
                Math.addExact(incoming, other.incoming), Math.addExact(proposed, other.proposed), known && other.known,
                List.copyOf(combinedSources), destination, blocker.code().isEmpty() ? other.blocker : blocker);
    }

    public enum Kind { PRODUCTION, PROJECT, FOOD, EQUIPMENT, STOCK_RESERVE, PLANTING }
    public enum Action { NONE, SUPPLY, OBSERVE, INSPECT_STATION, FREE_CAPACITY, RESUME, CONFIGURE_STORAGE, INSPECT_COMPETITOR }

    public record Blocker(String code, Action action, UUID competingConsumer) {
        public Blocker {
            if (code == null || code.length() > 128 || action == null) throw new IllegalArgumentException("Invalid supply blocker");
        }

        public static Blocker of(String code) {
            Action action = code.isEmpty() ? Action.NONE
                    : code.contains("unavailable") || code.contains("unobserved") ? Action.OBSERVE
                    : code.contains("station") || code.contains("furnace") || code.contains("brewing_stand") ? Action.INSPECT_STATION
                    : code.contains("full") || code.contains("capacity") ? Action.FREE_CAPACITY
                    : code.contains("paused") ? Action.RESUME : Action.SUPPLY;
            return new Blocker(code, action, null);
        }
    }
}
