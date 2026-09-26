package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

public final class SettlementRecord {
    public static final int MAX_AREAS = 256;
    private final VillageAllegianceId id;
    private final ResourceLocation dimension;
    private final BlockPos center;
    private final long foundedAt;
    private final Runnable dirty;
    private final Map<UUID, Set<SettlementPermission>> delegates = new LinkedHashMap<>();
    private final Map<UUID, SettlementArea> areas = new LinkedHashMap<>();
    private UUID leader;
    private SettlementStage stage = SettlementStage.CAMP;
    private long revision;

    SettlementRecord(VillageAllegianceId id, ResourceLocation dimension, BlockPos center,
                     UUID leader, long foundedAt, Runnable dirty) {
        this.id = Objects.requireNonNull(id, "id");
        this.dimension = Objects.requireNonNull(dimension, "dimension");
        this.center = Objects.requireNonNull(center, "center").immutable();
        this.leader = Objects.requireNonNull(leader, "leader");
        this.foundedAt = Math.max(0L, foundedAt);
        this.dirty = Objects.requireNonNull(dirty, "dirty");
    }

    public VillageAllegianceId id() {
        return this.id;
    }

    public ResourceLocation dimension() {
        return this.dimension;
    }

    public BlockPos center() {
        return this.center;
    }

    public UUID leader() {
        return this.leader;
    }

    public long foundedAt() {
        return this.foundedAt;
    }

    public long revision() {
        return this.revision;
    }

    public SettlementStage stage() {
        return this.stage;
    }

    public Map<UUID, Set<SettlementPermission>> delegates() {
        return Collections.unmodifiableMap(this.delegates);
    }

    public Map<UUID, SettlementArea> areas() {
        return Collections.unmodifiableMap(this.areas);
    }

    public boolean permits(UUID player, SettlementPermission permission) {
        if (player == null || permission == null) {
            return false;
        }
        if (this.leader.equals(player)) {
            return true;
        }
        Set<SettlementPermission> permissions = this.delegates.getOrDefault(player, Set.of());
        return permissions.contains(permission) || permission == SettlementPermission.VIEW && !permissions.isEmpty();
    }

    public boolean setPermissions(UUID actor, UUID delegate, Set<SettlementPermission> permissions) {
        if (!this.leader.equals(actor) || delegate == null || delegate.equals(this.leader) || permissions == null) {
            return false;
        }
        Set<SettlementPermission> updated = permissions.isEmpty() ? Set.of() : Set.copyOf(permissions);
        if (this.delegates.getOrDefault(delegate, Set.of()).equals(updated)) {
            return true;
        }
        if (updated.isEmpty()) {
            this.delegates.remove(delegate);
        } else {
            this.delegates.put(delegate, updated);
        }
        changed();
        return true;
    }

    public boolean transferLeadership(UUID actor, UUID successor) {
        if (!this.leader.equals(actor) || successor == null || successor.equals(this.leader)) {
            return false;
        }
        this.delegates.remove(successor);
        this.leader = successor;
        changed();
        return true;
    }

    boolean putArea(UUID actor, SettlementArea area) {
        if (!permits(actor, SettlementPermission.BUILD) || area == null
                || !area.within(this.center, this.stage.radius())
                || !this.areas.containsKey(area.id()) && this.areas.size() >= MAX_AREAS
                || this.areas.values().stream().anyMatch(existing ->
                        !existing.id().equals(area.id()) && existing.intersects(area))) {
            return false;
        }
        if (!area.equals(this.areas.put(area.id(), area))) {
            changed();
        }
        return true;
    }

    boolean removeArea(UUID actor, UUID area) {
        if (!permits(actor, SettlementPermission.BUILD) || this.areas.remove(area) == null) {
            return false;
        }
        changed();
        return true;
    }

    void advance() {
        this.stage.next().ifPresent(next -> {
            this.stage = next;
            changed();
        });
    }

    void changed() {
        this.revision = Math.addExact(this.revision, 1L);
        this.dirty.run();
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", this.id.value());
        tag.putString("Dimension", this.dimension.toString());
        tag.putLong("Center", this.center.asLong());
        tag.putUUID("Leader", this.leader);
        tag.putLong("FoundedAt", this.foundedAt);
        tag.putLong("Revision", this.revision);
        tag.putString("Stage", this.stage.name());
        ListTag delegateTags = new ListTag();
        this.delegates.forEach((player, permissions) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Player", player);
            for (SettlementPermission permission : permissions) {
                entry.putBoolean(permission.name(), true);
            }
            delegateTags.add(entry);
        });
        tag.put("Delegates", delegateTags);
        ListTag areaTags = new ListTag();
        this.areas.values().forEach(area -> areaTags.add(area.save()));
        tag.put("Areas", areaTags);
        return tag;
    }

    static SettlementRecord load(CompoundTag tag, Runnable dirty) {
        ResourceLocation dimension = ResourceLocation.tryParse(tag.getString("Dimension"));
        if (dimension == null || !tag.hasUUID("Id") || !tag.hasUUID("Leader")
                || !tag.contains("Center", Tag.TAG_LONG)) {
            throw new IllegalArgumentException("Settlement identity, dimension, center or leader is missing");
        }
        SettlementRecord record = new SettlementRecord(new VillageAllegianceId(tag.getUUID("Id")),
                dimension, BlockPos.of(tag.getLong("Center")), tag.getUUID("Leader"),
                tag.getLong("FoundedAt"), dirty);
        record.revision = Math.max(0L, tag.getLong("Revision"));
        record.stage = SettlementStage.valueOf(tag.getString("Stage"));
        for (Tag raw : tag.getList("Delegates", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) raw;
            if (!entry.hasUUID("Player") || entry.getUUID("Player").equals(record.leader)) {
                continue;
            }
            Set<SettlementPermission> permissions = EnumSet.noneOf(SettlementPermission.class);
            for (SettlementPermission permission : SettlementPermission.values()) {
                if (entry.getBoolean(permission.name())) {
                    permissions.add(permission);
                }
            }
            if (!permissions.isEmpty()) {
                record.delegates.put(entry.getUUID("Player"), Set.copyOf(permissions));
            }
        }
        for (Tag raw : tag.getList("Areas", Tag.TAG_COMPOUND)) {
            SettlementArea area = SettlementArea.load((CompoundTag) raw);
            if (!area.within(record.center, record.stage.radius()) || record.areas.size() >= MAX_AREAS
                    || record.areas.values().stream().anyMatch(existing -> existing.intersects(area))) {
                throw new IllegalArgumentException("Settlement areas exceed their bounds or overlap");
            }
            record.areas.put(area.id(), area);
        }
        return record;
    }
}
