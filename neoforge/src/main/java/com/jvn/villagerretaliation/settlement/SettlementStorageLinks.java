package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

public record SettlementStorageLinks(boolean automatic, List<Link> entries) {
    public static final int MAX_LINKS = 128;
    public static final int OVERRIDE = 1 << 27;
    public static final String STATE_TAG = "SettlementStorageLinks";
    public static final SettlementStorageLinks AUTOMATIC = new SettlementStorageLinks(true, List.of());

    public SettlementStorageLinks {
        entries = List.copyOf(entries);
        if (entries.size() > MAX_LINKS || automatic && !entries.isEmpty()) throw new IllegalArgumentException("Invalid storage links");
        var keys = new HashSet<String>();
        for (var link : entries) {
            if (!keys.add(link.position().asLong() + ":" + link.purpose().name())) throw new IllegalArgumentException("Duplicate storage link");
        }
    }

    public boolean permits(BlockPos position, String purpose) {
        return !this.automatic && this.entries.stream().anyMatch(link -> link.position().equals(position)
                && link.purpose().id().equals(AssignedStorageService.normalizePurpose(purpose)));
    }

    public CompoundTag save() {
        var tag = new CompoundTag();
        tag.putInt("Version", 1);
        tag.putBoolean("Automatic", this.automatic);
        var links = new ListTag();
        for (var link : this.entries) {
            var entry = new CompoundTag();
            entry.putLong("Position", link.position().asLong());
            entry.putString("Purpose", link.purpose().name());
            entry.putInt("Priority", link.priority());
            links.add(entry);
        }
        tag.put("Links", links);
        return tag;
    }

    public static SettlementStorageLinks load(CompoundTag tag) {
        if (!tag.contains("Version", Tag.TAG_INT) || tag.getInt("Version") != 1 || !tag.contains("Automatic", Tag.TAG_BYTE)
                || !(tag.get("Links") instanceof ListTag links) || links.size() > MAX_LINKS
                || !links.isEmpty() && links.getElementType() != Tag.TAG_COMPOUND) throw new IllegalArgumentException("Invalid saved storage links");
        var entries = new java.util.ArrayList<Link>();
        for (var raw : links) {
            var entry = (CompoundTag) raw;
            if (!entry.contains("Position", Tag.TAG_LONG) || !entry.contains("Purpose", Tag.TAG_STRING)
                    || !entry.contains("Priority", Tag.TAG_INT)) throw new IllegalArgumentException("Invalid saved storage link");
            entries.add(new Link(BlockPos.of(entry.getLong("Position")), Purpose.valueOf(entry.getString("Purpose")), entry.getInt("Priority")));
        }
        return new SettlementStorageLinks(tag.getBoolean("Automatic"), entries);
    }

    public void write(net.minecraft.network.RegistryFriendlyByteBuf buffer) {
        buffer.writeBoolean(this.automatic);
        buffer.writeVarInt(this.entries.size());
        for (var link : this.entries) {
            buffer.writeBlockPos(link.position());
            buffer.writeEnum(link.purpose());
            buffer.writeVarInt(link.priority());
        }
    }

    public static SettlementStorageLinks read(net.minecraft.network.RegistryFriendlyByteBuf buffer) {
        boolean automatic = buffer.readBoolean();
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_LINKS) throw new IllegalArgumentException("Invalid civic storage link count");
        var entries = new java.util.ArrayList<Link>();
        for (int index = 0; index < count; index++) entries.add(new Link(buffer.readBlockPos(), buffer.readEnum(Purpose.class), buffer.readVarInt()));
        return new SettlementStorageLinks(automatic, entries);
    }

    public static SettlementStorageLinks read(CompoundTag state) {
        return state.contains(STATE_TAG) ? load(state.getCompound(STATE_TAG)) : AUTOMATIC;
    }

    public record Link(BlockPos position, Purpose purpose, int priority) {
        public Link {
            position = Objects.requireNonNull(position).immutable();
            Objects.requireNonNull(purpose);
            if (priority < 0 || priority > 9) throw new IllegalArgumentException("Storage priority must be between zero and nine");
        }
    }

    public enum Purpose {
        GENERAL(AssignedStorageService.GENERAL_PURPOSE, "General"),
        SUPPLY(AssignedStorageService.SUPPLY_PURPOSE, "Supplies / Input"),
        OUTPUT(AssignedStorageService.OUTPUT_PURPOSE, "Output");

        private final String id;
        private final String label;

        Purpose(String id, String label) { this.id = id; this.label = label; }
        public String id() { return this.id; }
        public String label() { return this.label; }
        public boolean supplies() { return this != OUTPUT; }
        public boolean outputs() { return this != SUPPLY; }
    }
}
