package com.jvn.villagerretaliation.settlement;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

final class SettlementConstructionState {
    private final Map<UUID, SettlementProject> projects = new LinkedHashMap<>();
    private long constructionQueueRevision;
    static final int CONSTRUCTION_QUEUE_LIMIT = 128;
    private final Map<UUID, SettlementProject> projectHistory = new LinkedHashMap<>();
    private final Set<UUID> retiredProjects = new java.util.LinkedHashSet<>();
    private final java.util.ArrayDeque<UUID> projectMaintenance = new java.util.ArrayDeque<>();
    static final int PROJECT_HISTORY_LIMIT = 32;
    static final int PROJECT_MAINTENANCE_LIMIT = 8;
    private final Map<UUID, SettlementProject> view = Collections.unmodifiableMap(this.projects);

    Map<UUID, SettlementProject> projects() {
        return this.view;
    }

    long queueRevision() {
        return this.constructionQueueRevision;
    }

    void queueChanged() {
        this.constructionQueueRevision++;
    }

    void add(SettlementProject project) {
        this.projects.put(project.id(), project);
        this.projectMaintenance.addLast(project.id());
    }

    boolean reorderProject(UUID projectId, UUID beforeId, long expected) {
        var project = this.projects.get(projectId);
        var before = this.projects.get(beforeId);
        if (expected != this.constructionQueueRevision
                || project == null || !project.pending() || before == null || !before.pending() || project == before) return false;
        var pending = new java.util.ArrayList<>(this.projects.values().stream().filter(SettlementProject::pending).toList());
        pending.remove(project);
        int destination = pending.indexOf(before);
        pending.add(destination, project);
        var iterator = pending.iterator();
        var reordered = new LinkedHashMap<UUID, SettlementProject>();
        for (var entry : this.projects.values()) {
            var next = entry.pending() ? iterator.next() : entry;
            reordered.put(next.id(), next);
        }
        this.projects.clear();
        this.projects.putAll(reordered);
        this.constructionQueueRevision++;
        return true;
    }

    Map<UUID, SettlementProject> projectHistory() {
        return Collections.unmodifiableMap(this.projectHistory);
    }

    SettlementProject project(UUID id) {
        var project = this.projects.get(id);
        return project == null ? this.projectHistory.get(id) : project;
    }

    boolean knowsProject(UUID id) {
        return this.projects.containsKey(id) || this.projectHistory.containsKey(id) || this.retiredProjects.contains(id);
    }

    int maintainProjects(SettlementRecord town) {
        int visited = Math.min(PROJECT_MAINTENANCE_LIMIT, this.projectMaintenance.size());
        for (int index = 0; index < visited; index++) {
            var id = this.projectMaintenance.removeFirst();
            var project = this.projects.get(id);
            if (project == null) continue;
            if (!project.settled(town.residents().get(project.builder()))) {
                this.projectMaintenance.addLast(id);
                continue;
            }
            town.clearProjectSite(project);
            this.projects.remove(id);
            project.summarize();
            this.projectHistory.put(id, project);
            trimProjectHistory();
            town.changed();
        }
        return visited;
    }

    private void trimProjectHistory() {
        while (this.projectHistory.size() > PROJECT_HISTORY_LIMIT) {
            var oldest = this.projectHistory.keySet().iterator().next();
            this.projectHistory.remove(oldest);
            this.retiredProjects.add(oldest);
        }
    }

    SettlementProject activeProject(UUID builder) {
        return this.projects.values().stream().filter(project -> project.active() && project.builder().equals(builder)).findFirst().orElse(null);
    }

    void save(CompoundTag tag) {
        ListTag projectTags = new ListTag();
        this.projects.values().forEach(project -> projectTags.add(project.save()));
        tag.put("Projects", projectTags);
        tag.putLong("ConstructionQueueRevision", this.constructionQueueRevision);
        var projectHistoryTags = new ListTag();
        this.projectHistory.values().forEach(project -> projectHistoryTags.add(project.save()));
        tag.put("ProjectHistory", projectHistoryTags);
        long[] retired = new long[this.retiredProjects.size() * 2];
        int retiredIndex = 0;
        for (UUID id : this.retiredProjects) {
            retired[retiredIndex++] = id.getMostSignificantBits();
            retired[retiredIndex++] = id.getLeastSignificantBits();
        }
        tag.putLongArray("RetiredProjects", retired);
    }

    void load(CompoundTag tag, SettlementRecord town, int legacyRadius) {
        for (Tag raw : tag.getList("Projects", Tag.TAG_COMPOUND)) {
            var project = SettlementProject.load((CompoundTag) raw);
            if (!project.town().equals(town.id().value()) || this.projects.putIfAbsent(project.id(), project) != null
                    || project.workplace() != null && (!town.territory().migrated() && !project.workplace().within(town.center(), legacyRadius)
                            || town.areas().containsKey(project.area()))) {
                throw new IllegalArgumentException("Invalid or duplicated construction project");
            }
        }
        this.projectMaintenance.addAll(this.projects.keySet());
        this.constructionQueueRevision = tag.getLong("ConstructionQueueRevision");
        if (this.constructionQueueRevision < 0) throw new IllegalArgumentException("Invalid construction queue revision");
        if (tag.contains("RetiredProjects") && !tag.contains("RetiredProjects", Tag.TAG_LONG_ARRAY)) throw new IllegalArgumentException("Invalid retired construction identities");
        long[] retired = tag.getLongArray("RetiredProjects");
        if (retired.length % 2 != 0) throw new IllegalArgumentException("Incomplete retired construction identity");
        for (int index = 0; index < retired.length; index += 2) {
            var id = new UUID(retired[index], retired[index + 1]);
            if (knowsProject(id) || !this.retiredProjects.add(id)) throw new IllegalArgumentException("Duplicated retired construction identity");
        }
        if (tag.contains("ProjectHistory") && (!(tag.get("ProjectHistory") instanceof ListTag history)
                || !history.isEmpty() && history.getElementType() != Tag.TAG_COMPOUND)) throw new IllegalArgumentException("Invalid construction summary history");
        for (Tag raw : tag.getList("ProjectHistory", Tag.TAG_COMPOUND)) {
            var project = SettlementProject.load((CompoundTag) raw);
            if (knowsProject(project.id()) || project.unfinished() || project.site() != null
                    || !project.town().equals(town.id().value()) || project.paymentEarned() && !project.paymentDelivered()) {
                throw new IllegalArgumentException("Construction summary has unresolved obligations or a duplicated identity");
            }
            this.projectHistory.put(project.id(), project);
        }
        trimProjectHistory();
    }

    static void merge(CompoundTag from, CompoundTag into, UUID town) {
        for (String key : Set.of("Projects", "ProjectHistory")) {
            var combined = into.getList(key, Tag.TAG_COMPOUND).copy();
            for (Tag raw : from.getList(key, Tag.TAG_COMPOUND)) {
                var project = ((CompoundTag) raw).copy();
                project.getCompound("Task").putUUID("SettlementTown", town);
                combined.add(project);
            }
            into.put(key, combined);
        }
        long[] previous = from.getLongArray("RetiredProjects"), current = into.getLongArray("RetiredProjects");
        long[] retired = java.util.Arrays.copyOf(current, current.length + previous.length);
        System.arraycopy(previous, 0, retired, current.length, previous.length);
        into.putLongArray("RetiredProjects", retired);
        into.putLong("ConstructionQueueRevision", Math.addExact(Math.max(from.getLong("ConstructionQueueRevision"),
                into.getLong("ConstructionQueueRevision")), 1L));
    }

    void validate(SettlementRecord town) {
        for (var project : this.projects.values()) {
            if (project.active() && (!project.within(town.workplace(project.area()))
                    || !project.area().equals(town.duties().get(project.builder())) && !project.blocker().equals("builder")
                    || this.projects.values().stream().anyMatch(other -> other != project && other.active() && other.builder().equals(project.builder())))
                    || project.reservesFootprint() && this.projects.values().stream().anyMatch(existing -> existing != project
                            && existing.reservesFootprint() && existing.overlaps(project))) {
                throw new IllegalArgumentException("Construction project overlaps reserved work or has no matching duty");
            }
        }
        for (var project : this.projects.values()) {
            if (!project.active()) continue;
            for (var assistant : project.assistants()) {
                var home = town.workplace(town.duties().get(assistant));
                if (home == null || home.type() != SettlementAreaType.CONSTRUCTION || !town.worksitePlans().containsKey(home.id())) {
                    throw new IllegalArgumentException("Construction assistant has no adopted construction assignment");
                }
            }
        }
    }
}
