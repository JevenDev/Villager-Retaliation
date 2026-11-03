package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import com.jvn.villagerretaliation.diplomacy.route.DiplomaticRoute;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

public final class SettlementRoute {
    public static final int SECTION_LENGTH = 16, MAX_STEPS = 8192, WEAR_LIMIT = 8;
    final UUID id;
    final VillageAllegianceId origin, destination;
    final ResourceLocation dimension;
    final DiplomaticRoute path;
    final byte[] wear, built, pendingWear;
    final UUID[] projects;
    final String[] blockers;
    java.util.List<net.minecraft.core.BlockPos> points = java.util.List.of();
    String name;
    long revision;

    SettlementRoute(UUID id, VillageAllegianceId origin, VillageAllegianceId destination, ResourceLocation dimension,
                    String name, DiplomaticRoute path, long revision) {
        if (id == null || origin == null || destination == null || origin.equals(destination) || dimension == null
                || name == null || name.isBlank() || name.length() > 48 || path == null || path.steps().size() < 2
                || path.steps().size() > MAX_STEPS || path.needsBoat() || revision < 0)
            throw new IllegalArgumentException("Invalid settlement route");
        this.id = id; this.origin = origin; this.destination = destination; this.dimension = dimension;
        this.name = name; this.path = path; this.revision = revision;
        this.wear = new byte[path.steps().size() - 1]; this.built = new byte[this.wear.length]; this.pendingWear = new byte[this.wear.length];
        this.projects = new UUID[sections()]; this.blockers = new String[sections()];
        java.util.Arrays.fill(this.blockers, "");
    }
    public UUID id() { return this.id; }
    public String name() { return this.name; }
    public DiplomaticRoute path() { return this.path; }
    public int sections() { return Math.ceilDiv(this.wear.length, SECTION_LENGTH); }
    public double trail(int section) { return average(this.wear, section) / WEAR_LIMIT; }
    public double construction(int section) { return average(this.built, section); }
    private double average(byte[] values, int section) {
        int from = section * SECTION_LENGTH, to = Math.min(values.length, from + SECTION_LENGTH);
        if (from < 0 || from >= to) throw new IllegalArgumentException("Invalid route section");
        int total = 0; for (int i = from; i < to; i++) total += values[i];
        return (double) total / (to - from);
    }
    public static double timeFactor(double trail, double road) {
        if (!Double.isFinite(trail) || !Double.isFinite(road) || trail < 0 || trail > 1 || road < 0 || road > 1)
            throw new IllegalArgumentException("Invalid route development");
        return 1 - Math.max(.25 * trail, .5 * road);
    }
    public double estimatedTicks() {
        double total = 0;
        for (int i = 0; i < this.wear.length; i++) total += 8 * timeFactor((double) this.wear[i] / WEAR_LIMIT, this.built[i]);
        return total;
    }
    double[] factors(boolean reverse) {
        var result = new double[this.path.steps().size()]; result[0] = 1;
        for (int i = 1; i < result.length; i++) {
            int edge = reverse ? this.wear.length - i : i - 1;
            result[i] = timeFactor((double) this.wear[edge] / WEAR_LIMIT, this.built[edge]);
        }
        return result;
    }
    CompoundTag save() {
        var tag = new CompoundTag(); tag.putInt("Version", 1); tag.putUUID("Id", this.id);
        tag.putUUID("Origin", this.origin.value()); tag.putUUID("Destination", this.destination.value());
        tag.putString("Dimension", this.dimension.toString()); tag.putString("Name", this.name); tag.putLong("Revision", this.revision);
        tag.putLongArray("Points", this.points.stream().mapToLong(net.minecraft.core.BlockPos::asLong).toArray());
        tag.put("Path", this.path.save()); tag.putByteArray("Wear", this.wear); tag.putByteArray("Built", this.built); tag.putByteArray("PendingWear", this.pendingWear);
        var sections = new net.minecraft.nbt.ListTag();
        for (int i = 0; i < sections(); i++) {
            var section = new CompoundTag(); section.putString("Blocker", this.blockers[i]);
            if (this.projects[i] != null) section.putUUID("Project", this.projects[i]); sections.add(section);
        }
        tag.put("Sections", sections); return tag;
    }
    static SettlementRoute load(CompoundTag tag) {
        if (tag.getInt("Version") != 1 || !tag.hasUUID("Id") || !tag.hasUUID("Origin") || !tag.hasUUID("Destination")
                || !tag.contains("Revision", Tag.TAG_LONG) || !tag.contains("Wear", Tag.TAG_BYTE_ARRAY) || !tag.contains("Built", Tag.TAG_BYTE_ARRAY))
            throw new IllegalArgumentException("Incomplete settlement route");
        var result = new SettlementRoute(tag.getUUID("Id"), new VillageAllegianceId(tag.getUUID("Origin")),
                new VillageAllegianceId(tag.getUUID("Destination")), ResourceLocation.parse(tag.getString("Dimension")),
                tag.getString("Name"), DiplomaticRoute.load(tag.getCompound("Path")), tag.getLong("Revision"));
        if (tag.contains("Points")) {
            if (!tag.contains("Points", Tag.TAG_LONG_ARRAY) || tag.getLongArray("Points").length > 12) throw new IllegalArgumentException("Invalid route points");
            result.points = java.util.Arrays.stream(tag.getLongArray("Points")).mapToObj(net.minecraft.core.BlockPos::of).toList();
        }
        byte[] wear = tag.getByteArray("Wear"), built = tag.getByteArray("Built");
        var sections = tag.getList("Sections", Tag.TAG_COMPOUND);
        if (wear.length != result.wear.length || built.length != wear.length || sections.size() != result.sections()
                || !sections.equals(tag.get("Sections"))) throw new IllegalArgumentException("Invalid route progress");
        var pending = tag.contains("PendingWear") ? tag.getByteArray("PendingWear") : new byte[wear.length];
        if (pending.length != wear.length) throw new IllegalArgumentException("Invalid pending trail wear");
        for (int i = 0; i < wear.length; i++) {
            if (pending[i] < 0 || pending[i] > WEAR_LIMIT) throw new IllegalArgumentException("Invalid pending trail wear");
            result.pendingWear[i] = pending[i];
            if (wear[i] < 0 || wear[i] > WEAR_LIMIT || built[i] < 0 || built[i] > 1) throw new IllegalArgumentException("Invalid route development");
            result.wear[i] = wear[i]; result.built[i] = built[i];
        }
        for (int i = 0; i < sections.size(); i++) {
            var section = sections.getCompound(i); result.blockers[i] = section.getString("Blocker");
            if (!section.contains("Blocker", Tag.TAG_STRING) || result.blockers[i].length() > 160
                    || section.contains("Project") && !section.hasUUID("Project")) throw new IllegalArgumentException("Invalid route section");
            if (section.hasUUID("Project")) result.projects[i] = section.getUUID("Project");
        }
        return result;
    }
}
