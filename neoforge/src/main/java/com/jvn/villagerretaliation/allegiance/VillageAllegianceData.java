package com.jvn.villagerretaliation.allegiance;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

public record VillageAllegianceData(
        int dataVersion,
        AllegianceState state,
        VillageAllegianceId primary,
        AllegianceAssignmentSource assignmentSource,
        AllegianceConfidence confidence,
        long assignedGameTime,
        ResourceLocation originDimension,
        BlockPos originPosition,
        List<VillageAllegianceId> protectedParents) {
    public static final int CURRENT_VERSION = 2;
    public static final int MAX_PROTECTED_PARENTS = 2;

    public VillageAllegianceData {
        state = Objects.requireNonNull(state, "state");
        assignmentSource = Objects.requireNonNull(assignmentSource, "assignmentSource");
        confidence = Objects.requireNonNull(confidence, "confidence");
        originPosition = originPosition == null ? BlockPos.ZERO : originPosition.immutable();
        LinkedHashSet<VillageAllegianceId> normalized = new LinkedHashSet<>();
        if (protectedParents != null) {
            for (VillageAllegianceId id : protectedParents) {
                if (id != null && normalized.size() < MAX_PROTECTED_PARENTS) {
                    normalized.add(id);
                }
            }
        }
        protectedParents = List.copyOf(new ArrayList<>(normalized));
        if (state != AllegianceState.KNOWN) {
            primary = null;
            protectedParents = List.of();
        } else if (primary == null) {
            throw new IllegalArgumentException("Known allegiance requires a primary id");
        }
    }

    public static VillageAllegianceData known(
            VillageAllegianceId primary,
            AllegianceAssignmentSource source,
            AllegianceConfidence confidence,
            long gameTime,
            ResourceLocation dimension,
            BlockPos position,
            List<VillageAllegianceId> protectedParents) {
        return new VillageAllegianceData(
                CURRENT_VERSION, AllegianceState.KNOWN, primary, source, confidence,
                gameTime, dimension, position, protectedParents);
    }

    public static VillageAllegianceData unknown(
            AllegianceAssignmentSource source,
            AllegianceConfidence confidence,
            long gameTime,
            ResourceLocation dimension,
            BlockPos position) {
        return new VillageAllegianceData(
                CURRENT_VERSION, AllegianceState.UNKNOWN, null, source, confidence,
                gameTime, dimension, position, List.of());
    }

    public static VillageAllegianceData unaffiliated(
            AllegianceAssignmentSource source,
            long gameTime,
            ResourceLocation dimension,
            BlockPos position) {
        return new VillageAllegianceData(
                CURRENT_VERSION, AllegianceState.UNAFFILIATED, null, source,
                AllegianceConfidence.AUTHORITATIVE, gameTime, dimension, position, List.of());
    }

    public boolean isKnown() {
        return this.state == AllegianceState.KNOWN && this.primary != null;
    }
}
