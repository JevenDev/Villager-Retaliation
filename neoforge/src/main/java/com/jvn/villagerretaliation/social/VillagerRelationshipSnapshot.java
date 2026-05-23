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

    public boolean hasCurrentRelationship() {
        return !this.current.isEmpty();
    }

    public boolean hasPastRelationship() {
        return !this.past.isEmpty();
    }

    public boolean hasCurrentStage(VillagerRelationshipStage stage) {
        return stage != null && this.current.stream().anyMatch(bond -> bond.stage() == stage);
    }

    public boolean hasPastStage(VillagerRelationshipStage stage) {
        return stage != null && this.past.stream().anyMatch(bond -> bond.stage() == stage);
    }

    public boolean hasStage(VillagerRelationshipStage stage) {
        return hasCurrentStage(stage) || hasPastStage(stage);
    }

    public boolean hasCrush() {
        return hasCurrentStage(VillagerRelationshipStage.CRUSH);
    }

    public boolean hasDatingPartner() {
        return hasCurrentStage(VillagerRelationshipStage.DATING);
    }

    public boolean hasFiance() {
        return hasCurrentStage(VillagerRelationshipStage.ENGAGED);
    }

    public boolean hasRomanticSpouse() {
        return hasCurrentStage(VillagerRelationshipStage.MARRIED);
    }

    public boolean hasSeparatedPartner() {
        return hasPastStage(VillagerRelationshipStage.SEPARATED);
    }

    public boolean hasWidowedPartner() {
        return hasPastStage(VillagerRelationshipStage.WIDOWED);
    }

    public String firstCurrentPartner() {
        return firstPartnerName(this.current, null, "my partner");
    }

    public String firstPastPartner() {
        return firstPartnerName(this.past, null, "someone I was close to");
    }

    public String firstRelationshipPartner() {
        return hasCurrentRelationship() ? firstCurrentPartner() : firstPastPartner();
    }

    public String firstCrush() {
        return firstPartnerName(this.current, VillagerRelationshipStage.CRUSH, "someone I like");
    }

    public String firstDatingPartner() {
        return firstPartnerName(this.current, VillagerRelationshipStage.DATING, "the person I am seeing");
    }

    public String firstFiance() {
        return firstPartnerName(this.current, VillagerRelationshipStage.ENGAGED, "the person I am engaged to");
    }

    public String firstRomanticSpouse() {
        return firstPartnerName(this.current, VillagerRelationshipStage.MARRIED, "my spouse");
    }

    public String firstSeparatedPartner() {
        return firstPartnerName(this.past, VillagerRelationshipStage.SEPARATED, "someone from my past");
    }

    public String firstWidowedPartner() {
        return firstPartnerName(this.past, VillagerRelationshipStage.WIDOWED, "someone I lost");
    }

    private static String firstPartnerName(
            List<RomanticBondView> bonds,
            VillagerRelationshipStage stage,
            String fallback
    ) {
        return bonds.stream()
                .filter(bond -> stage == null || bond.stage() == stage)
                .findFirst()
                .map(RomanticBondView::partnerName)
                .filter(name -> !name.isBlank())
                .orElse(fallback);
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
