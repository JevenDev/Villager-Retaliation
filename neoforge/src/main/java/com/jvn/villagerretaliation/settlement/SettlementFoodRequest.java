package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

public final class SettlementFoodRequest {
    public enum State { REQUEST_SENT, AWAITING_OFFER, OFFER_PREPARING, OFFER_SENT, AWAITING_ACCEPTANCE, ACCEPTANCE_SENT, DECLINE_SENT, CANCELLING,
        PREPARING, TRAVELLING, RETURNING, COMPLETED, DECLINED, CANCELLED, EXPIRED, BLOCKED }
    private final UUID id;
    private final VillageAllegianceId requester, donor;
    private final long nutrition;
    private final long created, expires;
    private final UUID authority;
    State state = State.REQUEST_SENT;
    long arrives;
    VillageAllegianceId messageSender;
    long revision;
    String blocker = "";
    SettlementFoodOffer offer;
    long deliveredNutrition;
    int creditedStanding;
    UUID donorAuthority;
    boolean automaticApproval;
    int maximumItems;
    SettlementAidPreparation preparation;

    SettlementFoodRequest(UUID id, VillageAllegianceId requester, VillageAllegianceId donor, long nutrition,
                          long created, long arrives, long expires, UUID authority) {
        if (id == null || requester == null || donor == null || requester.equals(donor) || authority == null
                || nutrition <= 0 || nutrition > 10000000 || created < 0 || arrives < created || expires <= created)
            throw new IllegalArgumentException("Invalid food request");
        this.id = id; this.requester = requester; this.donor = donor; this.nutrition = nutrition;
        this.messageSender = requester;
        this.created = created; this.arrives = arrives; this.expires = expires; this.authority = authority;
    }
    public UUID id() { return this.id; }
    public VillageAllegianceId requester() { return this.requester; }
    public VillageAllegianceId donor() { return this.donor; }
    public UUID authority() { return this.authority; }
    public long nutrition() { return this.nutrition; }
    public long created() { return this.created; }
    public long expires() { return this.expires; }
    public long arrives() { return this.arrives; }
    public long revision() { return this.revision; }
    public State state() { return this.state; }
    public String blocker() { return this.blocker; }
    public SettlementFoodOffer offer() { return this.offer; }
    public long deliveredNutrition() { return this.deliveredNutrition; }
    public boolean terminal() {
        return this.state == State.COMPLETED || this.state == State.DECLINED || this.state == State.CANCELLED || this.state == State.EXPIRED;
    }
    public long incomingNutrition() {
        if (terminal()) return 0;
        return this.offer == null ? this.nutrition : Math.max(0, this.offer.nutrition() - this.deliveredNutrition);
    }
    CompoundTag save(HolderLookup.Provider provider) {
        var tag = new CompoundTag();
        tag.putUUID("Id", this.id); tag.putUUID("Requester", this.requester.value()); tag.putUUID("Donor", this.donor.value());
        tag.putUUID("MessageSender", this.messageSender.value());
        tag.putUUID("Authority", this.authority); tag.putLong("Nutrition", this.nutrition); tag.putLong("Created", this.created);
        tag.putLong("Arrives", this.arrives); tag.putLong("Expires", this.expires); tag.putLong("Revision", this.revision);
        tag.putString("State", this.state.name()); tag.putString("Blocker", this.blocker);
        tag.putLong("DeliveredNutrition", this.deliveredNutrition); tag.putInt("CreditedStanding", this.creditedStanding);
        if (this.donorAuthority != null) tag.putUUID("DonorAuthority", this.donorAuthority);
        tag.putInt("MaximumItems", this.maximumItems); tag.putBoolean("AutomaticApproval", this.automaticApproval);
        if (this.preparation != null) tag.put("Preparation", this.preparation.save());
        if (this.offer != null) tag.put("Offer", this.offer.save(provider));
        return tag;
    }
    static SettlementFoodRequest load(CompoundTag tag, HolderLookup.Provider provider) {
        if (!tag.hasUUID("Id") || !tag.hasUUID("Requester") || !tag.hasUUID("Donor") || !tag.hasUUID("Authority")
                || !tag.contains("Nutrition", Tag.TAG_LONG) || !tag.contains("Created", Tag.TAG_LONG) || !tag.contains("Arrives", Tag.TAG_LONG)
                || !tag.contains("Expires", Tag.TAG_LONG) || !tag.contains("Revision", Tag.TAG_LONG) || !tag.contains("State", Tag.TAG_STRING)
                || !tag.contains("Blocker", Tag.TAG_STRING) || !tag.contains("DeliveredNutrition", Tag.TAG_LONG)
                || !tag.contains("CreditedStanding", Tag.TAG_INT)) throw new IllegalArgumentException("Missing food request fields");
        var result = new SettlementFoodRequest(tag.getUUID("Id"), new VillageAllegianceId(tag.getUUID("Requester")),
                new VillageAllegianceId(tag.getUUID("Donor")), tag.getLong("Nutrition"), tag.getLong("Created"),
                tag.getLong("Arrives"), tag.getLong("Expires"), tag.getUUID("Authority"));
        if (!tag.hasUUID("MessageSender")) throw new IllegalArgumentException("Missing food correspondent");
        result.messageSender = new VillageAllegianceId(tag.getUUID("MessageSender"));
        if (!result.messageSender.equals(result.requester) && !result.messageSender.equals(result.donor)) throw new IllegalArgumentException("Invalid food correspondent");
        result.state = State.valueOf(tag.getString("State")); result.blocker = tag.getString("Blocker");
        result.revision = tag.getLong("Revision"); result.deliveredNutrition = tag.getLong("DeliveredNutrition");
        result.creditedStanding = tag.getInt("CreditedStanding");
        if (!tag.contains("MaximumItems", Tag.TAG_INT)) throw new IllegalArgumentException("Missing food offer limit");
        if (!tag.contains("AutomaticApproval", Tag.TAG_BYTE)) throw new IllegalArgumentException("Missing food approval mode");
        result.automaticApproval = tag.getBoolean("AutomaticApproval");
        result.maximumItems = tag.getInt("MaximumItems");
        if (result.maximumItems < 0 || result.maximumItems > 1728) throw new IllegalArgumentException("Invalid food offer limit");
        if (tag.contains("DonorAuthority")) {
            if (!tag.hasUUID("DonorAuthority")) throw new IllegalArgumentException("Invalid food approval authority");
            result.donorAuthority = tag.getUUID("DonorAuthority");
        }
        if (tag.contains("Preparation")) result.preparation = SettlementAidPreparation.load(tag.getCompound("Preparation"));
        if (tag.contains("Offer")) result.offer = SettlementFoodOffer.load(tag.getCompound("Offer"), provider);
        if (result.revision < 0 || result.blocker.length() > 80 || result.deliveredNutrition < 0 || result.deliveredNutrition > result.nutrition
                || result.creditedStanding < 0 || result.creditedStanding > 3
                || result.offer != null && result.offer.nutrition() > result.nutrition
                || result.offer == null && (result.deliveredNutrition != 0 || result.state == State.OFFER_SENT || result.state == State.AWAITING_ACCEPTANCE
                || result.state == State.ACCEPTANCE_SENT || result.state == State.PREPARING || result.state == State.TRAVELLING
                || result.state == State.RETURNING || result.state == State.COMPLETED)) throw new IllegalArgumentException("Invalid food request state");
        if (result.offer != null && (result.deliveredNutrition + result.offer.remaining().stream()
                .mapToLong(parcel -> SettlementAidStock.nutrition(parcel.item())).sum() > result.offer.nutrition()
                || result.creditedStanding > (result.deliveredNutrition * 3 + result.nutrition - 1) / result.nutrition))
            throw new IllegalArgumentException("Aid credit exceeds delivered food");
        if (result.terminal() && result.offer != null && !result.offer.empty()
                || result.state == State.COMPLETED && result.deliveredNutrition != result.offer.nutrition())
            throw new IllegalArgumentException("Finished aid request has unresolved cargo");
        return result;
    }
}
