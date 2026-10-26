package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import com.jvn.villagerretaliation.diplomacy.route.DiplomaticRoute;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.BoatItem;
import net.minecraft.world.item.ItemStack;

public final class SettlementCourierJourney {
    public enum Leg { APPROACH, OUTBOUND, HOMEBOUND, RETURN_APPROACH, FINISHED }
    public enum Presence { CLAIMING, STORED, MATERIALIZING, PHYSICAL, RETIRING, STRANDED, DEAD, RELEASED }
    public enum BoatCustody { NONE, PACKED, LAUNCHING, LAUNCHED, PACKING, LOST, RETURNED }
    final UUID id, resident;
    final VillageAllegianceId home;
    final net.minecraft.resources.ResourceLocation dimension;
    final DiplomaticRoute approach, crossing, returnApproach, returnCrossing;
    final UUID boatId;
    final ItemStack boat;
    Leg leg = Leg.APPROACH;
    Presence presence = Presence.CLAIMING;
    BoatCustody boatCustody;
    int step;
    long incarnation, boatIncarnation, revision, lastTick, lastProgress;
    CompoundTag residentContext, boatContext = new CompoundTag();
    BlockPos position;
    String blocker = "";
    boolean obstructed;
    UUID repair;
    BlockPos repairOrigin;
    DiplomaticRoute detour;
    int repairs;
    final java.util.Map<VillageAllegianceId, SettlementCourierInterference.Incident> interference = new java.util.LinkedHashMap<>();
    final java.util.List<SettlementCourierRecovery.Drop> recovery = new java.util.ArrayList<>();

    SettlementCourierJourney(UUID id, UUID resident, VillageAllegianceId home, net.minecraft.resources.ResourceLocation dimension, DiplomaticRoute approach,
                             DiplomaticRoute crossing, CompoundTag context, long incarnation, ItemStack boat, long now) {
        if (id == null || resident == null || home == null || dimension == null || approach == null || crossing == null || incarnation < 1
                || incarnation == Long.MAX_VALUE || now < 0 || !context.hasUUID("UUID") || !resident.equals(context.getUUID("UUID"))
                || !approach.steps().getLast().position().equals(crossing.steps().getFirst().position())
                || approach.needsBoat() || crossing.needsBoat() && boat.isEmpty()
                || !boat.isEmpty() && (!(boat.getItem() instanceof BoatItem) || boat.getCount() != 1))
            throw new IllegalArgumentException("Invalid diplomatic courier journey");
        this.id = id; this.resident = resident; this.home = home; this.dimension = dimension; this.approach = approach; this.crossing = crossing;
        this.returnApproach = approach.reversed(); this.returnCrossing = crossing.reversed();
        this.residentContext = context.copy(); this.incarnation = incarnation; this.boat = boat.copy(); this.boatId = UUID.randomUUID();
        this.boatCustody = boat.isEmpty() ? BoatCustody.NONE : BoatCustody.PACKED;
        this.position = approach.steps().getFirst().position(); this.lastTick = now; this.lastProgress = now;
    }
    private SettlementCourierJourney(SettlementCourierJourney original, UUID boatId) {
        this.id = original.id; this.resident = original.resident; this.home = original.home; this.dimension = original.dimension;
        this.approach = original.approach; this.crossing = original.crossing; this.residentContext = original.residentContext;
        this.returnApproach = original.returnApproach; this.returnCrossing = original.returnCrossing;
        this.incarnation = original.incarnation; this.boat = original.boat; this.boatId = boatId;
    }
    public UUID id() { return this.id; }
    public UUID resident() { return this.resident; }
    public VillageAllegianceId home() { return this.home; }
    public Leg leg() { return this.leg; }
    public Presence presence() { return this.presence; }
    public BlockPos position() { return this.position; }
    public String blocker() { return this.blocker; }
    public boolean finished() { return this.leg == Leg.FINISHED; }
    public boolean ownsResident() { return !finished() && this.presence != Presence.DEAD && this.presence != Presence.RELEASED; }
    public boolean released() { return this.presence == Presence.RELEASED; }
    public DiplomaticRoute route() {
        if (this.detour != null && !finished()) return this.detour;
        return originalRoute();
    }
    private DiplomaticRoute originalRoute() {
        return switch (this.leg) {
            case APPROACH -> this.approach;
            case OUTBOUND -> this.crossing;
            case HOMEBOUND -> this.returnCrossing;
            case RETURN_APPROACH, FINISHED -> this.returnApproach;
        };
    }
    boolean atEnd() { return this.step == route().steps().size() - 1; }
    void nextLeg(long now) {
        if (finished() || !atEnd()) throw new IllegalStateException("Courier leg has not completed");
        this.detour = null; this.repair = null; this.repairOrigin = null;
        this.leg = Leg.values()[this.leg.ordinal() + 1]; this.step = 0;
        if (finished()) this.step = route().steps().size() - 1;
        this.position = route().steps().get(this.step).position(); this.lastTick = now; this.lastProgress = now;
    }
    void advance(long now) {
        if (this.presence != Presence.STORED || this.obstructed || !this.blocker.isEmpty() || finished() || atEnd()) return;
        var target = route().steps().get(this.step + 1);
        int ticks = target.mode() == DiplomaticRoute.Mode.LAUNCH || target.mode() == DiplomaticRoute.Mode.LAND ? 40 : 8;
        if (now < this.lastTick || now - this.lastTick < ticks) return;
        if (target.mode() == DiplomaticRoute.Mode.LAUNCH && this.boatIncarnation == Long.MAX_VALUE) return;
        this.position = target.position(); this.step++; this.lastTick = now; this.lastProgress = now;
        if (target.mode() == DiplomaticRoute.Mode.LAUNCH) { this.boatCustody = BoatCustody.LAUNCHED; this.boatIncarnation++; }
        if (target.mode() == DiplomaticRoute.Mode.LAND) this.boatCustody = BoatCustody.PACKED;
    }
    CompoundTag save(HolderLookup.Provider provider) {
        var tag = new CompoundTag(); tag.putInt("Version", 1);
        tag.putUUID("Id", this.id); tag.putUUID("Resident", this.resident); tag.putUUID("Home", this.home.value()); tag.putString("Dimension", this.dimension.toString());
        tag.put("Approach", this.approach.save()); tag.put("Crossing", this.crossing.save());
        tag.putUUID("BoatId", this.boatId); if (!this.boat.isEmpty()) tag.put("Boat", this.boat.save(provider));
        tag.putLong("BoatIncarnation", this.boatIncarnation); tag.putString("BoatCustody", this.boatCustody.name()); tag.put("BoatContext", this.boatContext.copy());
        tag.putString("Leg", this.leg.name()); tag.putString("Presence", this.presence.name());
        tag.putInt("Step", this.step); tag.putLong("Incarnation", this.incarnation); tag.putLong("Revision", this.revision);
        tag.putLong("LastTick", this.lastTick); tag.putLong("LastProgress", this.lastProgress);
        tag.putLong("Position", this.position.asLong()); tag.put("ResidentContext", this.residentContext.copy());
        tag.putInt("Repairs", this.repairs);
        if (this.repair != null) { tag.putUUID("Repair", this.repair); tag.putLong("RepairOrigin", this.repairOrigin.asLong()); }
        if (this.detour != null) tag.put("Detour", this.detour.save());
        tag.put("Interference", SettlementCourierInterference.save(this.interference));
        tag.put("Recovery", SettlementCourierRecovery.save(this.recovery, provider));
        tag.putString("Blocker", this.blocker); tag.putBoolean("Obstructed", this.obstructed);
        return tag;
    }
    static SettlementCourierJourney load(CompoundTag tag, HolderLookup.Provider provider) {
        if (!tag.contains("Version", Tag.TAG_INT) || tag.getInt("Version") != 1 || !tag.hasUUID("Id") || !tag.hasUUID("Resident")
                || !tag.contains("Dimension", Tag.TAG_STRING) || !tag.hasUUID("Home") || !tag.hasUUID("BoatId") || !tag.contains("Approach", Tag.TAG_COMPOUND)
                || !tag.contains("Crossing", Tag.TAG_COMPOUND) || !tag.contains("ResidentContext", Tag.TAG_COMPOUND)
                || !tag.contains("BoatContext", Tag.TAG_COMPOUND) || !tag.contains("Obstructed", Tag.TAG_BYTE))
            throw new IllegalArgumentException("Incomplete diplomatic courier journey");
        for (String key : new String[]{"Incarnation", "BoatIncarnation", "Revision", "LastTick", "LastProgress", "Position"})
            if (!tag.contains(key, Tag.TAG_LONG)) throw new IllegalArgumentException("Missing courier field: " + key);
        for (String key : new String[]{"Leg", "Presence", "BoatCustody", "Blocker"})
            if (!tag.contains(key, Tag.TAG_STRING)) throw new IllegalArgumentException("Missing courier state: " + key);
        if (!tag.contains("Step", Tag.TAG_INT)) throw new IllegalArgumentException("Missing courier progress");
        var boat = tag.contains("Boat") ? ItemStack.parse(provider, tag.getCompound("Boat")).orElseThrow(() -> new IllegalArgumentException("Unreadable courier item")) : ItemStack.EMPTY;
        var original = new SettlementCourierJourney(tag.getUUID("Id"), tag.getUUID("Resident"), new VillageAllegianceId(tag.getUUID("Home")), net.minecraft.resources.ResourceLocation.parse(tag.getString("Dimension")),
                DiplomaticRoute.load(tag.getCompound("Approach")), DiplomaticRoute.load(tag.getCompound("Crossing")),
                tag.getCompound("ResidentContext"), tag.getLong("Incarnation"), boat, tag.getLong("LastTick"));
        var result = new SettlementCourierJourney(original, tag.getUUID("BoatId"));
        result.leg = Leg.valueOf(tag.getString("Leg")); result.presence = Presence.valueOf(tag.getString("Presence"));
        result.boatCustody = BoatCustody.valueOf(tag.getString("BoatCustody")); result.boatContext = tag.getCompound("BoatContext").copy();
        result.boatIncarnation = tag.getLong("BoatIncarnation"); result.step = tag.getInt("Step"); result.revision = tag.getLong("Revision"); result.lastTick = tag.getLong("LastTick");
        result.lastProgress = tag.getLong("LastProgress"); result.position = BlockPos.of(tag.getLong("Position"));
        if (!tag.contains("Repairs", Tag.TAG_INT) || tag.getInt("Repairs") < 0 || tag.getInt("Repairs") > 32)
            throw new IllegalArgumentException("Invalid courier route repair count");
        result.repairs = tag.getInt("Repairs");
        if (tag.contains("Repair")) {
            if (!tag.hasUUID("Repair") || !tag.contains("RepairOrigin", Tag.TAG_LONG)) throw new IllegalArgumentException("Incomplete courier route repair");
            result.repair = tag.getUUID("Repair"); result.repairOrigin = BlockPos.of(tag.getLong("RepairOrigin"));
        }
        if (tag.contains("Detour")) result.detour = DiplomaticRoute.load(tag.getCompound("Detour"));
        result.interference.putAll(SettlementCourierInterference.load(tag, result.revision));
        result.recovery.addAll(SettlementCourierRecovery.load(tag, provider));
        result.blocker = tag.getString("Blocker"); result.obstructed = tag.getBoolean("Obstructed");
        if (result.boatIncarnation < 0 || result.step < 0 || result.step >= result.route().steps().size() || result.revision < 0 || result.lastProgress < 0
                || result.blocker.length() > 80 || result.boat.isEmpty() != (result.boatCustody == BoatCustody.NONE)
                || !result.boatContext.isEmpty() && (!result.boatContext.hasUUID("UUID") || !result.boatId.equals(result.boatContext.getUUID("UUID")))
                || result.released() && !result.finished()
                || result.repair == null && tag.contains("RepairOrigin")
                || (result.detour != null || result.repair != null) && (result.finished() || result.repairs == 0)
                || result.detour != null && (!result.detour.steps().getLast().position().equals(result.originalRoute().steps().getLast().position())
                || result.detour.needsBoat() && result.boat.isEmpty()))
            throw new IllegalArgumentException("Invalid diplomatic courier custody");
        return result;
    }
}
