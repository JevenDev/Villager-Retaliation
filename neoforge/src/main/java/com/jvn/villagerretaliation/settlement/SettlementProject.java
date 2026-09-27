package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.work.builder.BuilderStructureScanner;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderTaskState;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Rotation;

public final class SettlementProject {
    private final UUID id;
    private UUID area;
    private UUID builder;
    private final BlockPos min;
    private final BlockPos max;
    private CompoundTag task;
    private Status status;
    private SettlementConstructionSite site;

    private SettlementProject(UUID id, UUID area, UUID builder, BlockPos min, BlockPos max, CompoundTag task, Status status) {
        this.id = Objects.requireNonNull(id);
        this.area = area;
        this.builder = builder;
        this.min = min.immutable();
        this.max = max.immutable();
        this.task = task.copy();
        this.status = Objects.requireNonNull(status);
        if (min.getX() > max.getX() || min.getY() > max.getY() || min.getZ() > max.getZ()
                || !id.equals(BuilderTaskState.jobId(state()).orElse(null))
                || !task.hasUUID("SettlementTown") || ResourceLocation.tryParse(task.getString("StructureId")) == null
                || !task.contains("Origin", Tag.TAG_LONG) || task.getInt("TotalBlocks") <= 0
                || task.getInt("PlacedIndex") < 0 || task.getInt("PlacedIndex") > task.getInt("TotalBlocks")
                || task.getInt("PaidCurrency") != 0 || task.getLong("StartedGameTime") < 0L) {
            throw new IllegalArgumentException("Invalid settlement construction project");
        }
        Rotation.valueOf(task.getString("Rotation"));
        if (status == Status.ACTIVE && (area == null || builder == null)) throw new IllegalArgumentException("Active construction requires a builder");
        if (!automatic() && (area == null || builder == null)) throw new IllegalArgumentException("Legacy construction requires its assignment");
    }

    static SettlementProject create(SettlementRecord town, UUID area, UUID builder, BuilderStructureScanner.StructurePlan plan,
                                     BlockPos origin, UUID id, long now) {
        CompoundTag state = new CompoundTag();
        BuilderTaskState.start(state, plan.entry(), plan, origin, plan.rotation(), 0, now, id);
        BuilderTaskState.task(state).putUUID("SettlementTown", town.id().value());
        return new SettlementProject(id, area, builder, origin.offset(plan.localMin()), origin.offset(plan.localMax()),
                BuilderTaskState.task(state), Status.ACTIVE);
    }

    static SettlementProject queued(SettlementRecord town, BuilderStructureScanner.StructurePlan plan, BlockPos origin, long now) {
        var id = UUID.randomUUID();
        var state = new CompoundTag();
        BuilderTaskState.start(state, plan.entry(), plan, origin, plan.rotation(), 0, now, id);
        var task = BuilderTaskState.task(state);
        task.putUUID("SettlementTown", town.id().value());
        task.putBoolean("Automatic", true);
        var project = new SettlementProject(id, null, null, plan.worldMin(origin), plan.worldMax(origin), task, Status.QUEUED);
        project.observeSite(new SettlementArea(id, SettlementAreaType.CONSTRUCTION, "Construction site",
                project.min.offset(-4, -1, -4), project.max.offset(4, 4, 4), 5, true));
        return project;
    }

    public boolean automatic() {
        return this.task.getBoolean("Automatic");
    }

    public boolean unfinished() {
        return active() || this.status == Status.QUEUED || this.status == Status.PAUSED;
    }

    void assign(UUID builder, UUID area) {
        if (!automatic() || !unfinished()) throw new IllegalStateException("Only pending orders can be dispatched");
        this.builder = Objects.requireNonNull(builder);
        this.area = Objects.requireNonNull(area);
        this.status = Status.ACTIVE;
        waiting("", "");
    }

    void pause(String reason) {
        if (!automatic() || !unfinished()) return;
        this.status = Status.PAUSED;
        this.builder = null;
        this.area = null;
        this.task.putString("BlockedReason", reason);
    }

    void waiting(String reason, String missing) {
        this.task.putString("BlockedReason", reason);
        this.task.putString("MissingMaterials", missing);
    }

    SettlementArea surveyArea(SettlementArea duty) {
        return automatic() && this.site != null ? this.site.area() : duty;
    }

    SettlementConstructionSite site() {
        return this.site;
    }

    void observeSite(SettlementArea area) {
        if (this.site == null || !this.site.matches(area) && !this.site.pending()) this.site = new SettlementConstructionSite(this.id, area);
    }

    void clearSite() {
        if (this.site != null && !this.site.pending()) this.site = null;
    }

    public UUID id() {
        return this.id;
    }

    public UUID area() {
        return this.area;
    }

    public UUID builder() {
        return this.builder;
    }

    public BlockPos min() {
        return this.min;
    }

    public BlockPos max() {
        return this.max;
    }

    public Status status() {
        return this.status;
    }

    public boolean active() {
        return this.status == Status.ACTIVE;
    }

    boolean reservesFootprint() {
        return unfinished() || this.site != null && this.site.pending();
    }

    public String label() {
        return this.task.getString("StructureLabel");
    }

    public ResourceLocation structure() {
        return ResourceLocation.parse(this.task.getString("StructureId"));
    }

    public BlockPos origin() {
        return BlockPos.of(this.task.getLong("Origin"));
    }

    public Rotation rotation() {
        return Rotation.valueOf(this.task.getString("Rotation"));
    }

    public int placed() {
        return this.task.getInt("PlacedIndex");
    }

    public int total() {
        return this.task.getInt("TotalBlocks");
    }

    public String blocker() {
        if (this.site != null && this.site.conflict()) return "world_conflict";
        if (!active() && this.site != null && this.site.pending()) return "pending_world";
        return this.task.getString("BlockedReason");
    }

    public String materials() {
        return this.task.getString("MaterialSummary");
    }

    public UUID town() {
        return this.task.getUUID("SettlementTown");
    }


    public String missingMaterials() {
        return this.task.getString("MissingMaterials");
    }

    public com.jvn.villagerretaliation.interaction.work.builder.BuilderBuildPhase phase() {
        return BuilderTaskState.phase(state());
    }

    boolean overlaps(SettlementProject other) {
        return this.min.getX() <= other.max.getX() && this.max.getX() >= other.min.getX()
                && this.min.getY() <= other.max.getY() && this.max.getY() >= other.min.getY()
                && this.min.getZ() <= other.max.getZ() && this.max.getZ() >= other.min.getZ();
    }

    CompoundTag state() {
        CompoundTag state = new CompoundTag();
        state.put(BuilderTaskState.TASK_TAG, this.task.copy());
        return state;
    }

    void restore(CompoundTag state) {
        state.put(BuilderTaskState.TASK_TAG, this.task.copy());
    }

    boolean observe(CompoundTag state) {
        CompoundTag next = state.getCompound(BuilderTaskState.TASK_TAG);
        if (!active() || next.equals(this.task) || !id.equals(BuilderTaskState.jobId(state).orElse(null))) {
            return false;
        }
        this.task = next.copy();
        return true;
    }

    boolean finish(Status status) {
        if (!unfinished() || status != Status.COMPLETED && status != Status.CANCELLED) {
            return false;
        }
        this.status = status;
        if (status == Status.COMPLETED) {
            this.task.putInt("PlacedIndex", total());
            this.task.remove("BlockedReason");
        }
        return true;
    }

    boolean within(SettlementArea area) {
        return area != null && area.type() == SettlementAreaType.CONSTRUCTION && area.contains(this.min) && area.contains(this.max);
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", this.id);
        if (this.area != null) tag.putUUID("Area", this.area);
        if (this.builder != null) tag.putUUID("Builder", this.builder);
        tag.putLong("Min", this.min.asLong());
        tag.putLong("Max", this.max.asLong());
        tag.put("Task", this.task.copy());
        if (this.site != null) tag.put("Site", this.site.save());
        tag.putString("Status", this.status.name());
        return tag;
    }

    static SettlementProject load(CompoundTag tag) {
        if (!tag.contains("Min", Tag.TAG_LONG) || !tag.contains("Max", Tag.TAG_LONG)) {
            throw new IllegalArgumentException("Construction project is missing its bounds");
        }
        var project = new SettlementProject(tag.getUUID("Id"), tag.hasUUID("Area") ? tag.getUUID("Area") : null,
                tag.hasUUID("Builder") ? tag.getUUID("Builder") : null,
                BlockPos.of(tag.getLong("Min")), BlockPos.of(tag.getLong("Max")), tag.getCompound("Task"),
                Status.valueOf(tag.getString("Status")));
        if (tag.contains("Site", Tag.TAG_COMPOUND)) {
            project.site = SettlementConstructionSite.load(project.id, tag.getCompound("Site"));
            if (!project.site.area().id().equals(project.automatic() ? project.id : project.area)) throw new IllegalArgumentException("Construction observations belong to a different area");
        }
        if (project.automatic() && (project.site == null || !project.within(project.site.area()))) throw new IllegalArgumentException("Order is missing its site");
        return project;
    }

    public enum Status {
        ACTIVE, COMPLETED, CANCELLED, QUEUED, PAUSED
    }
}
