package com.jvn.villagerretaliation.settlement;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

public final class SettlementFarmJob {
    public static final int MAX_JOBS = 128;
    public static final UUID AUTOMATIC = new UUID(0, 0);
    private final UUID id;
    private final UUID area;
    private UUID issuer;
    private final BlockPos min;
    private final BlockPos max;
    private UUID worker = AUTOMATIC;
    private UUID requested = AUTOMATIC;
    private CompoundTag work;
    private SettlementProject.Status status = SettlementProject.Status.PENDING;
    private int priority;
    private boolean paused;
    private long revision;
    private String blocker = "";

    SettlementFarmJob(UUID area, UUID issuer, BlockPos min, BlockPos max, int priority, CompoundTag work) {
        this.id = work.getUUID("Task");
        this.area = area;
        this.issuer = issuer;
        this.min = min.immutable();
        this.max = max.immutable();
        this.priority = priority;
        this.work = work.copy();
    }

    public UUID id() { return this.id; }
    public UUID area() { return this.area; }
    public UUID issuer() { return this.issuer; }
    public UUID worker() { return this.worker; }
    public UUID requested() { return this.requested; }
    public BlockPos min() { return this.min; }
    public BlockPos max() { return this.max; }
    public int priority() { return this.priority; }
    public long revision() { return this.revision; }
    public boolean paused() { return this.paused; }
    public String blocker() { return this.blocker; }
    public SettlementProject.Status status() { return this.status; }
    public boolean unfinished() { return this.status == SettlementProject.Status.ACTIVE || this.status == SettlementProject.Status.PENDING; }
    CompoundTag work() { return this.work; }
    void work(CompoundTag work) { this.work = work; }
    void blocker(String reason) { this.blocker = reason.substring(0, Math.min(128, reason.length())); }
    void activate(UUID worker) { this.worker = worker; this.status = SettlementProject.Status.ACTIVE; this.blocker = ""; }
    void pending() { this.worker = AUTOMATIC; this.status = SettlementProject.Status.PENDING; }
    void finish(boolean completed) { this.status = completed ? SettlementProject.Status.COMPLETED : SettlementProject.Status.CANCELLED; this.paused = false; this.blocker = ""; this.revision++; }

    void configure(UUID issuer, int priority, boolean paused, UUID requested) {
        this.issuer = issuer;
        this.priority = priority;
        this.paused = paused;
        this.requested = requested;
        this.blocker = paused ? "Farm preparation paused" : "";
        this.revision++;
    }

    public int total(boolean water) {
        return (int) this.work.getList("Cells", Tag.TAG_COMPOUND).stream().map(CompoundTag.class::cast)
                .filter(cell -> cell.getBoolean("Water") == water).count();
    }

    public int done(boolean water) {
        return (int) this.work.getList("Cells", Tag.TAG_COMPOUND).stream().map(CompoundTag.class::cast)
                .filter(cell -> cell.getBoolean("Water") == water && cell.getBoolean("Done")).count();
    }

    SettlementFarmTerrain.Preview remaining() {
        var cells = this.work.getList("Cells", Tag.TAG_COMPOUND).stream().map(CompoundTag.class::cast)
                .filter(cell -> !cell.getBoolean("Done"))
                .map(cell -> new SettlementFarmTerrain.Cell(BlockPos.of(cell.getLong("Pos")),
                        cell.getBoolean("Water") ? SettlementFarmTerrain.Kind.WATER : SettlementFarmTerrain.Kind.TILL,
                        net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),
                        net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),
                        net.minecraft.world.level.block.Blocks.AIR.defaultBlockState())).toList();
        return new SettlementFarmTerrain.Preview(cells, "");
    }

    public CompoundTag save() {
        var tag = new CompoundTag();
        tag.putInt("Version", 1);
        tag.putUUID("Area", this.area);
        tag.putUUID("Issuer", this.issuer);
        tag.putUUID("Worker", this.worker);
        tag.putUUID("Requested", this.requested);
        tag.putLong("Min", this.min.asLong());
        tag.putLong("Max", this.max.asLong());
        tag.putInt("Priority", this.priority);
        tag.putBoolean("Paused", this.paused);
        tag.putLong("Revision", this.revision);
        tag.putString("Status", this.status.name());
        tag.putString("Blocker", this.blocker);
        tag.put("Work", this.work.copy());
        return tag;
    }

    public static SettlementFarmJob load(CompoundTag tag) {
        var work = tag.getCompound("Work");
        if (tag.getInt("Version") != 1 || !tag.hasUUID("Area") || !tag.hasUUID("Issuer")
                || !tag.hasUUID("Worker") || !tag.hasUUID("Requested") || !work.hasUUID("Task")
                || !tag.contains("Min", Tag.TAG_LONG) || !tag.contains("Max", Tag.TAG_LONG)
                || !work.contains("Cells", Tag.TAG_LIST) || !tag.contains("Priority", Tag.TAG_INT)
                || !tag.contains("Revision", Tag.TAG_LONG) || !tag.contains("Paused", Tag.TAG_BYTE)
                || !tag.contains("Status", Tag.TAG_STRING) || !tag.contains("Blocker", Tag.TAG_STRING)
                || work.getList("Cells", Tag.TAG_COMPOUND).size() > SettlementFarm.MAX_CELLS
                || tag.getInt("Priority") < 0 || tag.getInt("Priority") > 9 || tag.getLong("Revision") < 0
                || tag.getString("Blocker").length() > 128)
            throw new IllegalArgumentException("Invalid farm job");
        var min = BlockPos.of(tag.getLong("Min"));
        var max = BlockPos.of(tag.getLong("Max"));
        if (min.getX() > max.getX() || min.getY() > max.getY() || min.getZ() > max.getZ()
                || (long) max.getX() - min.getX() >= SettlementFarm.MAX_SPAN
                || (long) max.getZ() - min.getZ() >= SettlementFarm.MAX_SPAN || (long) max.getY() - min.getY() >= 64)
            throw new IllegalArgumentException("Invalid farm job bounds");
        var cells = work.getList("Cells", Tag.TAG_COMPOUND);
        if (cells.size() != ((net.minecraft.nbt.ListTag) work.get("Cells")).size())
            throw new IllegalArgumentException("Invalid farm job cells");
        for (var raw : cells) {
            var cell = (CompoundTag) raw;
            var pos = BlockPos.of(cell.getLong("Pos"));
            if (!cell.contains("Pos", Tag.TAG_LONG) || !cell.contains("Soil", Tag.TAG_COMPOUND)
                    || !cell.contains("Water", Tag.TAG_BYTE) || pos.getX() < min.getX() || pos.getX() > max.getX()
                    || pos.getY() < min.getY() || pos.getY() > max.getY() || pos.getZ() < min.getZ() || pos.getZ() > max.getZ())
                throw new IllegalArgumentException("Invalid farm job cell");
        }
        var job = new SettlementFarmJob(tag.getUUID("Area"), tag.getUUID("Issuer"), min, max, tag.getInt("Priority"), work);
        job.worker = tag.getUUID("Worker");
        job.requested = tag.getUUID("Requested");
        job.paused = tag.getBoolean("Paused");
        job.revision = tag.getLong("Revision");
        job.status = SettlementProject.Status.valueOf(tag.getString("Status"));
        job.blocker = tag.getString("Blocker");
        return job;
    }
}
