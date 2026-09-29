package com.jvn.villagerretaliation.interaction.work;

import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

public record HiredTemporaryQueue(UUID owner, UUID issuer, UUID worksite, HiredVillagerRole role,
                                  BlockPos homeMin, BlockPos homeMax, List<HiredTemporaryTask> tasks) {
    public static final String STATE_TAG = "TemporaryWork";
    public static final int MAX_TASKS = 16;

    public HiredTemporaryQueue {
        Objects.requireNonNull(owner);
        Objects.requireNonNull(issuer);
        Objects.requireNonNull(role);
        homeMin = Objects.requireNonNull(homeMin).immutable();
        homeMax = Objects.requireNonNull(homeMax).immutable();
        tasks = List.copyOf(tasks);
        if (homeMin.getX() > homeMax.getX() || homeMin.getY() > homeMax.getY() || homeMin.getZ() > homeMax.getZ()
                || tasks.isEmpty() || tasks.size() > MAX_TASKS
                || tasks.stream().anyMatch(task -> task.kind().role() != role)
                || tasks.stream().map(HiredTemporaryTask::id).distinct().count() != tasks.size()) {
            throw new IllegalArgumentException("Invalid temporary task queue");
        }
    }

    public HiredTemporaryTask current() { return tasks.getFirst(); }

    public HiredTemporaryQueue append(HiredTemporaryTask task) {
        List<HiredTemporaryTask> next = new ArrayList<>(tasks);
        next.add(task);
        return withTasks(next);
    }

    public HiredTemporaryQueue advance() {
        List<HiredTemporaryTask> next = new ArrayList<>(tasks);
        HiredTemporaryTask remaining = current().consume();
        if (remaining == null) next.removeFirst();
        else next.set(0, remaining);
        return next.isEmpty() ? null : withTasks(next);
    }

    private HiredTemporaryQueue withTasks(List<HiredTemporaryTask> next) {
        return new HiredTemporaryQueue(owner, issuer, worksite, role, homeMin, homeMax, next);
    }

    public void store(CompoundTag state) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Version", 1);
        tag.putUUID("Owner", owner);
        tag.putUUID("Issuer", issuer);
        if (worksite != null) tag.putUUID("Worksite", worksite);
        tag.putString("Role", role.name());
        tag.putLong("HomeMin", homeMin.asLong());
        tag.putLong("HomeMax", homeMax.asLong());
        ListTag entries = new ListTag();
        tasks.forEach(task -> entries.add(task.save()));
        tag.put("Tasks", entries);
        state.put(STATE_TAG, tag);
    }

    public static HiredTemporaryQueue load(CompoundTag state) {
        if (!state.contains(STATE_TAG)) return null;
        if (!state.contains(STATE_TAG, Tag.TAG_COMPOUND)) throw new IllegalArgumentException("Invalid temporary queue tag");
        CompoundTag tag = state.getCompound(STATE_TAG);
        if (!tag.contains("Version", Tag.TAG_INT) || tag.getInt("Version") != 1 || !tag.hasUUID("Owner")
                || !tag.hasUUID("Issuer") || tag.contains("Worksite") && !tag.hasUUID("Worksite")
                || !tag.contains("Role", Tag.TAG_STRING) || !tag.contains("HomeMin", Tag.TAG_LONG)
                || !tag.contains("HomeMax", Tag.TAG_LONG) || !tag.contains("Tasks", Tag.TAG_LIST)) {
            throw new IllegalArgumentException("Invalid temporary queue");
        }
        ListTag entries = tag.getList("Tasks", Tag.TAG_COMPOUND);
        if (entries.isEmpty() || entries.size() > MAX_TASKS) throw new IllegalArgumentException("Invalid temporary queue size");
        List<HiredTemporaryTask> tasks = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) tasks.add(HiredTemporaryTask.load(entries.getCompound(i)));
        return new HiredTemporaryQueue(tag.getUUID("Owner"), tag.getUUID("Issuer"),
                tag.hasUUID("Worksite") ? tag.getUUID("Worksite") : null, HiredVillagerRole.valueOf(tag.getString("Role")),
                BlockPos.of(tag.getLong("HomeMin")), BlockPos.of(tag.getLong("HomeMax")), tasks);
    }
}
