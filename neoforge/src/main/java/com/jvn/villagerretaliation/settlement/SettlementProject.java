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
    private final UUID area;
    private final UUID builder;
    private final BlockPos min;
    private final BlockPos max;
    private CompoundTag task;
    private Status status;
    private SettlementConstructionSite site;
    private SettlementArea workplace;
    private UUID previousArea;
    private SettlementWorkerSettings previousSettings;
    private int fee;
    private boolean paymentEarned;
    private boolean paymentDelivered;
    private boolean paymentRefunded;
    private SettlementConstructionCrew crew;

    boolean settled(SettlementResidentState resident) {
        return !active() && (this.site == null || !this.site.unsettled())
                && (this.fee == 0 || this.paymentRefunded || this.paymentDelivered
                        && resident != null && resident.acknowledgesConstruction(this));
    }

    void summarize() {
        if (active() || this.site != null && this.site.unsettled()) throw new IllegalStateException("Construction still has world obligations");
        var summary = new CompoundTag();
        for (String key : java.util.List.of("JobId", "SettlementTown", "StructureId", "StructureLabel", "Origin", "Rotation",
                "Phase", "PlacedIndex", "TotalBlocks", "PaidCurrency", "StartedGameTime", "MaterialSummary")) {
            if (this.task.contains(key)) summary.put(key, this.task.get(key).copy());
        }
        this.task = summary;
        this.site = null;
        this.workplace = null;
        this.previousArea = null;
        this.previousSettings = null;
    }

    private SettlementProject(UUID id, UUID area, UUID builder, BlockPos min, BlockPos max, CompoundTag task, Status status) {
        this.id = Objects.requireNonNull(id);
        this.area = Objects.requireNonNull(area);
        this.builder = Objects.requireNonNull(builder);
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
    }

    static SettlementProject create(SettlementRecord town, UUID area, UUID builder, BuilderStructureScanner.StructurePlan plan,
                                     BlockPos origin, UUID id, long now) {
        CompoundTag state = new CompoundTag();
        BuilderTaskState.start(state, plan.entry(), plan, origin, plan.rotation(), 0, now, id);
        BuilderTaskState.task(state).putUUID("SettlementTown", town.id().value());
        return new SettlementProject(id, area, builder, origin.offset(plan.localMin()), origin.offset(plan.localMax()),
                BuilderTaskState.task(state), Status.ACTIVE);
    }

    static SettlementProject planned(SettlementRecord town, SettlementArea workplace, UUID builder,
                                      BuilderStructureScanner.StructurePlan plan, BlockPos origin, UUID id, long now) {
        var project = create(town, workplace.id(), builder, plan, origin, id, now);
        project.workplace = workplace;
        project.previousArea = town.duties().get(builder);
        project.previousSettings = town.workerSettings().get(builder);
        project.fee = plan.price();
        return project;
    }

    public SettlementArea workplace() {
        return this.workplace;
    }

    UUID previousArea() {
        return this.previousArea;
    }

    SettlementWorkerSettings previousSettings() {
        return this.previousSettings;
    }

    void reservePayment(int fee) {
        if (fee <= 0 || this.fee != 0) throw new IllegalArgumentException("Invalid construction fee");
        this.fee = fee;
    }

    public int fee() {
        return this.fee;
    }

    public boolean paymentEarned() {
        return this.paymentEarned;
    }

    public boolean paymentDelivered() { return this.paymentDelivered; }

    boolean acknowledgePayment() {
        if (!this.paymentEarned || this.paymentDelivered) return false;
        this.paymentDelivered = true;
        return true;
    }

    boolean earnPayment() {
        if (this.fee == 0 || this.paymentEarned || this.paymentRefunded) return false;
        this.paymentEarned = true;
        return true;
    }

    int refundPayment() {
        if (this.fee == 0 || this.paymentEarned || this.paymentRefunded) return 0;
        this.paymentRefunded = true;
        return this.fee;
    }

    SettlementConstructionSite site() {
        return this.site;
    }

    void observeSite(SettlementArea area) {
        if (this.site == null || !this.site.matches(area) && !this.site.unsettled()) this.site = new SettlementConstructionSite(this.id, area);
    }

    void clearSite() {
        if (this.site != null && !this.site.unsettled()) this.site = null;
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
        return active() || this.site != null && this.site.unsettled();
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
        return this.crew == null ? this.task.getInt("PlacedIndex") : this.crew.placed();
    }

    long startedAt() { return this.task.getLong("StartedGameTime"); }
    public int priority() { return this.crew == null ? 5 : this.crew.priority(); }
    public long crewRevision() { return this.crew == null ? 0L : this.crew.revision(); }
    public java.util.List<UUID> assistants() { return this.crew == null ? java.util.List.of() : this.crew.assistants(); }
    public boolean includes(UUID worker) { return this.builder.equals(worker) || this.crew != null && this.crew.includes(worker); }
    SettlementConstructionCrew crew() { return this.crew; }

    boolean configureCrew(long expected, int priority, java.util.List<UUID> assistants) {
        if (!active()) return false;
        var next = this.crew == null ? new SettlementConstructionCrew(this.builder, total(), this.task.getInt("PlacedIndex")) : this.crew;
        if (!next.configure(expected, priority, assistants)) return false;
        this.crew = next;
        return true;
    }

    boolean detachAssistant(UUID worker) { return this.crew != null && this.crew.detach(worker); }

    boolean refreshProgress(java.util.function.IntPredicate matches) {
        if (this.crew == null || !this.crew.refresh(matches)) return false;
        this.task.putInt("PlacedIndex", this.crew.next());
        return true;
    }

    public int total() {
        return this.task.getInt("TotalBlocks");
    }

    public String blocker() {
        if (this.site != null && this.site.conflict()) return "world_conflict";
        if (!active() && this.site != null && this.site.unsettled()) return "pending_world";
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

    boolean overlaps(SettlementArea other) {
        return this.min.getX() <= other.max().getX() && this.max.getX() >= other.min().getX()
                && this.min.getY() <= other.max().getY() && this.max.getY() >= other.min().getY()
                && this.min.getZ() <= other.max().getZ() && this.max.getZ() >= other.min().getZ();
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
        CompoundTag next = state.getCompound(BuilderTaskState.TASK_TAG).copy();
        if (this.crew != null) next.putInt("PlacedIndex", this.crew.next());
        if (!active() || next.equals(this.task) || !id.equals(BuilderTaskState.jobId(state).orElse(null))) {
            return false;
        }
        this.task = next.copy();
        return true;
    }

    boolean finish(Status status) {
        if (!active() || status == Status.ACTIVE) {
            return false;
        }
        this.status = status;
        if (this.crew != null) this.crew.releaseAll();
        if (status == Status.COMPLETED) {
            if (this.crew != null) this.crew.refresh(ignored -> true);
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
        tag.putUUID("Area", this.area);
        tag.putUUID("Builder", this.builder);
        tag.putLong("Min", this.min.asLong());
        tag.putLong("Max", this.max.asLong());
        tag.put("Task", this.task.copy());
        if (this.site != null) tag.put("Site", this.site.save());
        tag.putString("Status", this.status.name());
        if (this.workplace != null) tag.put("Workplace", this.workplace.save());
        if (this.previousArea != null) tag.putUUID("PreviousArea", this.previousArea);
        if (this.previousSettings != null) tag.put("PreviousSettings", this.previousSettings.save());
        tag.putInt("Fee", this.fee);
        tag.putBoolean("PaymentEarned", this.paymentEarned);
        tag.putBoolean("PaymentDelivered", this.paymentDelivered);
        tag.putBoolean("PaymentRefunded", this.paymentRefunded);
        if (this.crew != null) tag.put("Crew", this.crew.save());
        return tag;
    }

    static SettlementProject load(CompoundTag tag) {
        if (!tag.contains("Min", Tag.TAG_LONG) || !tag.contains("Max", Tag.TAG_LONG)) {
            throw new IllegalArgumentException("Construction project is missing its bounds");
        }
        var project = new SettlementProject(tag.getUUID("Id"), tag.getUUID("Area"), tag.getUUID("Builder"),
                BlockPos.of(tag.getLong("Min")), BlockPos.of(tag.getLong("Max")), tag.getCompound("Task"),
                Status.valueOf(tag.getString("Status")));
        if (tag.contains("Crew")) {
            if (!tag.contains("Crew", Tag.TAG_COMPOUND)) throw new IllegalArgumentException("Invalid construction crew");
            project.crew = SettlementConstructionCrew.load(tag.getCompound("Crew"), project.builder, project.total(), project.task.getInt("PlacedIndex"));
        }
        project.fee = tag.getInt("Fee");
        project.paymentEarned = tag.getBoolean("PaymentEarned");
        project.paymentDelivered = tag.getBoolean("PaymentDelivered");
        project.paymentRefunded = tag.getBoolean("PaymentRefunded");
        if (project.fee < 0 || project.paymentDelivered && (!project.paymentEarned || project.fee == 0)
                || project.paymentEarned && project.paymentRefunded
                || project.active() && project.paymentRefunded
                || project.fee > 0 && project.status == Status.COMPLETED && !project.paymentEarned
                || project.fee > 0 && project.status == Status.CANCELLED && !project.paymentEarned && !project.paymentRefunded) throw new IllegalArgumentException("Invalid construction payment");
        if (tag.contains("Workplace", Tag.TAG_COMPOUND)) {
            project.workplace = SettlementArea.load(tag.getCompound("Workplace"));
            if (!project.id.equals(project.area) || !project.area.equals(project.workplace.id()) || !project.within(project.workplace)) {
                throw new IllegalArgumentException("Invalid project workplace");
            }
        }
        if (tag.hasUUID("PreviousArea")) project.previousArea = tag.getUUID("PreviousArea");
        if (tag.contains("PreviousSettings", Tag.TAG_COMPOUND)) {
            project.previousSettings = SettlementWorkerSettings.load(tag.getCompound("PreviousSettings"));
            if (project.previousArea == null || project.previousSettings.role() != com.jvn.villagerretaliation.interaction.HiredVillagerRole.BUILDER) {
                throw new IllegalArgumentException("Invalid previous construction assignment");
            }
        }
        if (project.previousArea != null && (project.workplace == null || project.previousArea.equals(project.area))) {
            throw new IllegalArgumentException("Invalid previous project assignment");
        }
        if (tag.contains("Site", Tag.TAG_COMPOUND)) {
            project.site = SettlementConstructionSite.load(project.id, tag.getCompound("Site"));
            if (!project.site.area().id().equals(project.area)) throw new IllegalArgumentException("Construction observations belong to a different area");
        }
        return project;
    }

    public enum Status {
        ACTIVE, COMPLETED, CANCELLED
    }
}
