package com.jvn.villagerretaliation.settlement;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

final class SettlementAidPreparation {
    final UUID donorDepot, recipientDepot, crossing;
    final BlockPos from, to;
    final boolean boatAllowed;
    final Candidate delivery, collection;

    static final class Candidate {
        final UUID resident;
        final BlockPos origin;
        UUID approach;
        BlockPos access;
        String blocker = "";
        Candidate(UUID resident, BlockPos origin) { this.resident = resident; this.origin = origin.immutable(); }
        CompoundTag save() {
            var tag = new CompoundTag(); tag.putUUID("Resident", this.resident); tag.putLong("Origin", this.origin.asLong());
            tag.putString("Blocker", this.blocker);
            if (this.approach != null) { tag.putUUID("Approach", this.approach); tag.putLong("Access", this.access.asLong()); }
            return tag;
        }
        static Candidate load(CompoundTag tag) {
            if (!tag.hasUUID("Resident") || !tag.contains("Origin", Tag.TAG_LONG) || !tag.contains("Blocker", Tag.TAG_STRING)
                    || tag.getString("Blocker").length() > 80) throw new IllegalArgumentException("Invalid courier candidate");
            var result = new Candidate(tag.getUUID("Resident"), BlockPos.of(tag.getLong("Origin")));
            result.blocker = tag.getString("Blocker");
            if (tag.contains("Approach") || tag.contains("Access")) {
                if (!tag.hasUUID("Approach") || !tag.contains("Access", Tag.TAG_LONG)) throw new IllegalArgumentException("Invalid courier approach");
                result.approach = tag.getUUID("Approach"); result.access = BlockPos.of(tag.getLong("Access"));
            }
            return result;
        }
    }
    SettlementAidPreparation(UUID donorDepot, UUID recipientDepot, BlockPos from, BlockPos to, boolean boatAllowed,
                             Candidate delivery, Candidate collection) {
        this(donorDepot, recipientDepot, UUID.randomUUID(), from, to, boatAllowed, delivery, collection);
    }
    private SettlementAidPreparation(UUID donorDepot, UUID recipientDepot, UUID crossing, BlockPos from, BlockPos to, boolean boatAllowed,
                                     Candidate delivery, Candidate collection) {
        if (donorDepot == null || recipientDepot == null || crossing == null || from == null || to == null || delivery == null && collection == null)
            throw new IllegalArgumentException("Invalid food aid preparation");
        this.donorDepot = donorDepot; this.recipientDepot = recipientDepot; this.crossing = crossing;
        this.from = from.immutable(); this.to = to.immutable(); this.boatAllowed = boatAllowed;
        this.delivery = delivery; this.collection = collection;
    }
    Candidate candidate(SettlementFoodOffer.Arrangement arrangement) {
        return arrangement == SettlementFoodOffer.Arrangement.DONOR_DELIVERY ? this.delivery : this.collection;
    }
    CompoundTag save() {
        var tag = new CompoundTag(); tag.putUUID("DonorDepot", this.donorDepot); tag.putUUID("RecipientDepot", this.recipientDepot);
        tag.putUUID("Crossing", this.crossing); tag.putLong("From", this.from.asLong()); tag.putLong("To", this.to.asLong());
        tag.putBoolean("BoatAllowed", this.boatAllowed);
        if (this.delivery != null) tag.put("Delivery", this.delivery.save());
        if (this.collection != null) tag.put("Collection", this.collection.save());
        return tag;
    }
    static SettlementAidPreparation load(CompoundTag tag) {
        if (!tag.hasUUID("DonorDepot") || !tag.hasUUID("RecipientDepot") || !tag.hasUUID("Crossing")
                || !tag.contains("From", Tag.TAG_LONG) || !tag.contains("To", Tag.TAG_LONG) || !tag.contains("BoatAllowed", Tag.TAG_BYTE))
            throw new IllegalArgumentException("Missing food aid preparation");
        return new SettlementAidPreparation(tag.getUUID("DonorDepot"), tag.getUUID("RecipientDepot"), tag.getUUID("Crossing"),
                BlockPos.of(tag.getLong("From")), BlockPos.of(tag.getLong("To")), tag.getBoolean("BoatAllowed"),
                tag.contains("Delivery") ? Candidate.load(tag.getCompound("Delivery")) : null,
                tag.contains("Collection") ? Candidate.load(tag.getCompound("Collection")) : null);
    }
}
