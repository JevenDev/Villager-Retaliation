package com.jvn.villagerretaliation.settlement;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

public record SettlementWorkerPool(UUID id, Configuration configuration, UUID authority, long revision,
                                   State state, String blocker, List<Move> history) {
    public static final int MAX_POOLS = 32;
    public static final int MAX_RESIDENTS = 1_024;
    public static final int MAX_HISTORY = 32;
    public static final long COOLDOWN_TICKS = 1_200L;

    public SettlementWorkerPool {
        Objects.requireNonNull(id);
        Objects.requireNonNull(configuration);
        Objects.requireNonNull(authority);
        Objects.requireNonNull(state);
        history = List.copyOf(history);
        if (revision < 0 || blocker == null || blocker.length() > 128 || history.size() > MAX_HISTORY
                || state == State.SUSPENDED && blocker.isEmpty() || state != State.SUSPENDED && !blocker.isEmpty()
                || state == State.ACTIVE && !configuration.staffable())
            throw new IllegalArgumentException("Invalid worker pool state");
    }

    boolean authorized(SettlementRecord town) {
        return this.state == State.ACTIVE && town.workerPools().get(this.id) == this
                && town.permits(this.authority, SettlementPermission.STAFF);
    }

    SettlementWorkerPool state(State state, UUID authority, long revision, String blocker) {
        return new SettlementWorkerPool(this.id, this.configuration, authority, revision, state, blocker, this.history);
    }

    SettlementWorkerPool moved(Move move) {
        var history = new ArrayList<>(this.history);
        if (history.size() == MAX_HISTORY) history.removeFirst();
        history.add(move);
        return new SettlementWorkerPool(this.id, this.configuration, this.authority, this.revision, this.state, this.blocker, history);
    }

    public CompoundTag save() {
        var tag = new CompoundTag();
        tag.putInt("Version", 1);
        tag.putUUID("Id", this.id);
        tag.put("Configuration", this.configuration.save());
        tag.putUUID("Authority", this.authority);
        tag.putLong("Revision", this.revision);
        tag.putString("State", this.state.name());
        tag.putString("Blocker", this.blocker);
        var history = new ListTag();
        for (var move : this.history) {
            var entry = new CompoundTag();
            entry.putLong("At", move.at());
            entry.putUUID("Resident", move.resident());
            if (move.from() != null) entry.putUUID("From", move.from());
            entry.putUUID("To", move.to());
            entry.putString("Reason", move.reason().name());
            if (move.demand() != null) entry.putUUID("Demand", move.demand());
            entry.putInt("Priority", move.priority());
            history.add(entry);
        }
        tag.put("History", history);
        return tag;
    }

    public static SettlementWorkerPool load(CompoundTag tag) {
        if (!tag.contains("Version", Tag.TAG_INT) || tag.getInt("Version") != 1 || !tag.hasUUID("Id")
                || !tag.contains("Configuration", Tag.TAG_COMPOUND) || !tag.hasUUID("Authority")
                || !tag.contains("Revision", Tag.TAG_LONG) || !tag.contains("State", Tag.TAG_STRING)
                || !tag.contains("Blocker", Tag.TAG_STRING))
            throw new IllegalArgumentException("Invalid saved worker pool");
        var history = new ArrayList<Move>();
        for (var raw : list(tag, "History", MAX_HISTORY)) {
            var entry = (CompoundTag) raw;
            if (!entry.contains("At", Tag.TAG_LONG) || !entry.hasUUID("Resident") || !entry.hasUUID("To")
                    || entry.contains("From") && !entry.hasUUID("From") || entry.contains("Demand") && !entry.hasUUID("Demand")
                    || !entry.contains("Reason", Tag.TAG_STRING) || !entry.contains("Priority", Tag.TAG_INT))
                throw new IllegalArgumentException("Invalid saved staffing move");
            history.add(new Move(entry.getLong("At"), entry.getUUID("Resident"),
                    entry.contains("From") ? entry.getUUID("From") : null, entry.getUUID("To"),
                    Reason.valueOf(entry.getString("Reason")), entry.contains("Demand") ? entry.getUUID("Demand") : null,
                    entry.getInt("Priority")));
        }
        return new SettlementWorkerPool(tag.getUUID("Id"), Configuration.load(tag.getCompound("Configuration")),
                tag.getUUID("Authority"), tag.getLong("Revision"), State.valueOf(tag.getString("State")),
                tag.getString("Blocker"), history);
    }

    static ListTag list(CompoundTag tag, String key, int maximum) {
        if (!(tag.get(key) instanceof ListTag list) || list.size() > maximum
                || !list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND)
            throw new IllegalArgumentException("Invalid worker pool collection: " + key);
        return list;
    }

    public enum State { PAUSED, ACTIVE, SUSPENDED }
    public enum Reason { MINIMUM, PROTECTED_SHORTAGE, PRIORITY, STAFFING, COMPLETED_DEMAND }

    public record Configuration(String name, List<UUID> residents, List<Staffing> worksites) {
        public Configuration {
            residents = List.copyOf(residents);
            worksites = List.copyOf(worksites);
            if (name == null || name.isBlank() || name.length() > 64 || residents.size() > MAX_RESIDENTS
                    || residents.stream().distinct().count() != residents.size() || worksites.size() > SettlementRecord.MAX_AREAS
                    || worksites.stream().map(Staffing::area).distinct().count() != worksites.size())
                throw new IllegalArgumentException("Invalid worker pool configuration");
        }

        boolean staffable() {
            return !this.residents.isEmpty() && this.worksites.stream().anyMatch(site -> site.maximum() > 0);
        }

        public CompoundTag save() {
            var tag = new CompoundTag();
            tag.putInt("Version", 1);
            tag.putString("Name", this.name);
            var residents = new ListTag();
            for (var resident : this.residents) {
                var entry = new CompoundTag();
                entry.putUUID("Resident", resident);
                residents.add(entry);
            }
            tag.put("Residents", residents);
            var worksites = new ListTag();
            for (var site : this.worksites) {
                var entry = new CompoundTag();
                entry.putUUID("Area", site.area());
                entry.putInt("Minimum", site.minimum());
                entry.putInt("Maximum", site.maximum());
                worksites.add(entry);
            }
            tag.put("Worksites", worksites);
            return tag;
        }

        public static Configuration load(CompoundTag tag) {
            if (!tag.contains("Version", Tag.TAG_INT) || tag.getInt("Version") != 1 || !tag.contains("Name", Tag.TAG_STRING))
                throw new IllegalArgumentException("Invalid saved worker pool configuration");
            var residents = new ArrayList<UUID>();
            for (var raw : list(tag, "Residents", MAX_RESIDENTS)) {
                var entry = (CompoundTag) raw;
                if (!entry.hasUUID("Resident")) throw new IllegalArgumentException("Invalid enrolled resident");
                residents.add(entry.getUUID("Resident"));
            }
            var worksites = new ArrayList<Staffing>();
            for (var raw : list(tag, "Worksites", SettlementRecord.MAX_AREAS)) {
                var entry = (CompoundTag) raw;
                if (!entry.hasUUID("Area") || !entry.contains("Minimum", Tag.TAG_INT) || !entry.contains("Maximum", Tag.TAG_INT))
                    throw new IllegalArgumentException("Invalid pool staffing limits");
                worksites.add(new Staffing(entry.getUUID("Area"), entry.getInt("Minimum"), entry.getInt("Maximum")));
            }
            return new Configuration(tag.getString("Name"), residents, worksites);
        }
    }

    public record Staffing(UUID area, int minimum, int maximum) {
        public Staffing {
            Objects.requireNonNull(area);
            if (minimum < 0 || maximum < minimum || maximum > SettlementWorksitePlan.MAX_STAFF)
                throw new IllegalArgumentException("Invalid pool staffing limits");
        }

        public static Staffing defaults(SettlementRecord town, UUID area) {
            var plan = town.worksitePlans().get(area);
            return new Staffing(area, 0, plan == null ? 1 : plan.desiredStaff());
        }
    }

    public record Move(long at, UUID resident, UUID from, UUID to, Reason reason, UUID demand, int priority) {
        public Move {
            Objects.requireNonNull(resident);
            Objects.requireNonNull(to);
            Objects.requireNonNull(reason);
            if (at < 0 || to.equals(from) || priority < 0 || priority > 9)
                throw new IllegalArgumentException("Invalid worker pool move");
        }
    }
}
