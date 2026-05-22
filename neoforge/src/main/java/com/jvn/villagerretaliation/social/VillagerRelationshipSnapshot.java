package com.jvn.villagerretaliation.social;

import java.util.List;

public record VillagerRelationshipSnapshot(List<RomanticBondView> current, List<RomanticBondView> past) {
    public static final VillagerRelationshipSnapshot EMPTY = new VillagerRelationshipSnapshot(List.of(), List.of());

    public int relationshipCount() {
        return this.current.size() + this.past.size();
    }

    public boolean hasRelationships() {
        return relationshipCount() > 0;
    }

    public record RomanticBondView(
            String partnerName,
            boolean partnerAlive,
            VillagerRelationshipStage stage,
            int affection,
            int compatibility,
            long startedGameTime,
            long stageSinceGameTime,
            long endedGameTime,
            String endReason
    ) {
        public RomanticBondView {
            partnerName = partnerName == null ? "" : partnerName;
            stage = stage == null ? VillagerRelationshipStage.CRUSH : stage;
            endReason = endReason == null ? "" : endReason;
        }

        public String partnerStatusLabel() {
            return this.partnerAlive ? "alive" : "deceased";
        }

        public String displayLabel() {
            String label = this.stage.displayName() + ": " + this.partnerName + " (" + partnerStatusLabel() + ")";
            if (this.stage.active()) {
                return label + " - affection " + this.affection + ", compatibility " + this.compatibility;
            }
            return this.endReason.isBlank() ? label : label + " - " + this.endReason;
        }
    }
}
