package com.jvn.villagerretaliation.progress;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public final class ExplorationHistorySavedData extends SavedData {
    private final Map<UUID, History> players = new HashMap<>();

    public static ExplorationHistorySavedData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                new Factory<>(ExplorationHistorySavedData::new, ExplorationHistorySavedData::load), "villagerretaliation_exploration_history");
    }

    public Map<StructureDestination, EventHistorySavedData.Summary> structures(UUID player) {
        var history = players.get(player);
        return history == null ? Map.of() : Map.copyOf(history.structures);
    }

    public Map<SettlementDestination, EventHistorySavedData.Summary> settlements(UUID player) {
        var history = players.get(player);
        return history == null ? Map.of() : Map.copyOf(history.settlements);
    }

    public Set<ResourceLocation> possessed(UUID player) {
        var history = players.get(player);
        return history == null ? Set.of() : Set.copyOf(history.possessed.keySet());
    }

    public boolean possess(UUID player, ResourceLocation item, long now) {
        if (now < 0) throw new IllegalArgumentException("Invalid discovery time");
        boolean discovered = players.computeIfAbsent(player, ignored -> new History()).possessed.putIfAbsent(item, now) == null;
        if (discovered) setDirty();
        return discovered;
    }

    public Set<StructureDestination> observeStructures(UUID player, ResourceLocation dimension, Set<StructureDestination> present, Set<StructureDestination> unresolved, long now) {
        var history = players.computeIfAbsent(player, ignored -> new History());
        var entered = new HashSet<>(present);
        entered.removeAll(history.activeStructures);
        for (var destination : entered) history.structures.merge(destination, visit(now), EventHistorySavedData.Summary::merge);
        var active = new HashSet<>(present);
        // only an unresolved start can keep its own previously observed visit active
        for (var destination : history.activeStructures)
            if (destination.dimension.equals(dimension) && unresolved.contains(destination)) active.add(destination);
        if (!active.equals(history.activeStructures)) {
            history.activeStructures.clear();
            history.activeStructures.addAll(active);
            setDirty();
        }
        if (!entered.isEmpty()) setDirty();
        return Set.copyOf(entered);
    }

    public boolean observeSettlement(ServerLevel level, UUID player, SettlementDestination present, long now) {
        var history = players.computeIfAbsent(player, ignored -> new History());
        var previous = history.activeSettlement;
        boolean same = previous != null && present != null && previous.dimension.equals(present.dimension)
                && canonical(level, previous.settlement).equals(canonical(level, present.settlement));
        boolean entered = present != null && !same;
        if (entered) history.settlements.merge(present, visit(now), EventHistorySavedData.Summary::merge);
        if (!Objects.equals(previous, present)) {
            history.activeSettlement = present;
            setDirty();
        }
        return entered;
    }

    public static UUID canonical(ServerLevel level, UUID settlement) {
        return VillageAllegianceRegistrySavedData.get(level).canonical(new VillageAllegianceId(settlement))
                .map(VillageAllegianceId::value).orElse(settlement);
    }

    private static EventHistorySavedData.Summary visit(long now) {
        if (now < 0) throw new IllegalArgumentException("Invalid visit time");
        return new EventHistorySavedData.Summary(1, 1, now, now);
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("Version", 1);
        var entries = new ListTag();
        players.forEach((player, history) -> {
            var entry = new CompoundTag();
            entry.putUUID("Player", player);
            var structures = new ListTag();
            history.structures.forEach((destination, summary) -> structures.add(withSummary(destination.save(), summary)));
            entry.put("Structures", structures);
            var settlements = new ListTag();
            history.settlements.forEach((destination, summary) -> settlements.add(withSummary(destination.save(), summary)));
            entry.put("Settlements", settlements);
            var items = new ListTag();
            history.possessed.forEach((item, first) -> {
                var value = new CompoundTag();
                value.putString("Item", item.toString());
                value.putLong("First", first);
                items.add(value);
            });
            entry.put("PossessedItems", items);
            var active = new ListTag();
            history.activeStructures.forEach(destination -> active.add(destination.save()));
            entry.put("ActiveStructures", active);
            if (history.activeSettlement != null) entry.put("ActiveSettlement", history.activeSettlement.save());
            entries.add(entry);
        });
        tag.put("Players", entries);
        return tag;
    }

    public static ExplorationHistorySavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.getInt("Version") < 0 || tag.getInt("Version") > 1) throw new IllegalArgumentException("Unsupported exploration history version");
        var data = new ExplorationHistorySavedData();
        for (Tag raw : tag.getList("Players", Tag.TAG_COMPOUND)) {
            var entry = (CompoundTag) raw;
            if (!entry.hasUUID("Player")) throw new IllegalArgumentException("Exploration history has no player identity");
            var history = new History();
            for (Tag value : entry.getList("Structures", Tag.TAG_COMPOUND)) {
                var record = (CompoundTag) value;
                history.structures.merge(StructureDestination.load(record), summary(record), EventHistorySavedData.Summary::merge);
            }
            for (Tag value : entry.getList("Settlements", Tag.TAG_COMPOUND)) {
                var record = (CompoundTag) value;
                history.settlements.merge(SettlementDestination.load(record), summary(record), EventHistorySavedData.Summary::merge);
            }
            for (Tag value : entry.getList("PossessedItems", Tag.TAG_COMPOUND)) {
                var record = (CompoundTag) value;
                long first = record.getLong("First");
                if (first < 0) throw new IllegalArgumentException("Invalid item discovery time");
                history.possessed.merge(ResourceLocation.parse(record.getString("Item")), first, Math::min);
            }
            for (Tag value : entry.getList("ActiveStructures", Tag.TAG_COMPOUND)) history.activeStructures.add(StructureDestination.load((CompoundTag) value));
            if (entry.contains("ActiveSettlement", Tag.TAG_COMPOUND)) history.activeSettlement = SettlementDestination.load(entry.getCompound("ActiveSettlement"));
            data.players.put(entry.getUUID("Player"), history);
        }
        return data;
    }

    private static CompoundTag withSummary(CompoundTag tag, EventHistorySavedData.Summary summary) {
        tag.putLong("Visits", summary.count());
        tag.putLong("First", summary.first());
        tag.putLong("Last", summary.last());
        return tag;
    }

    private static EventHistorySavedData.Summary summary(CompoundTag tag) {
        long count = tag.getLong("Visits"), first = tag.getLong("First"), last = tag.getLong("Last");
        if (count <= 0 || first < 0 || last < first) throw new IllegalArgumentException("Invalid persisted visit history");
        return new EventHistorySavedData.Summary(count, count, first, last);
    }

    public record StructureDestination(ResourceLocation dimension, ResourceLocation structure, long startChunk) {
        public StructureDestination { Objects.requireNonNull(dimension); Objects.requireNonNull(structure); }
        CompoundTag save() {
            var tag = new CompoundTag();
            tag.putString("Dimension", dimension.toString());
            tag.putString("Structure", structure.toString());
            tag.putLong("StartChunk", startChunk);
            return tag;
        }
        static StructureDestination load(CompoundTag tag) {
            return new StructureDestination(ResourceLocation.parse(tag.getString("Dimension")), ResourceLocation.parse(tag.getString("Structure")), tag.getLong("StartChunk"));
        }
    }

    public record SettlementDestination(ResourceLocation dimension, UUID settlement) {
        public SettlementDestination { Objects.requireNonNull(dimension); Objects.requireNonNull(settlement); }
        CompoundTag save() {
            var tag = new CompoundTag();
            tag.putString("Dimension", dimension.toString());
            tag.putUUID("Settlement", settlement);
            return tag;
        }
        static SettlementDestination load(CompoundTag tag) {
            return new SettlementDestination(ResourceLocation.parse(tag.getString("Dimension")), tag.getUUID("Settlement"));
        }
    }

    private static final class History {
        private final Map<StructureDestination, EventHistorySavedData.Summary> structures = new HashMap<>();
        private final Map<SettlementDestination, EventHistorySavedData.Summary> settlements = new HashMap<>();
        private final Map<ResourceLocation, Long> possessed = new HashMap<>();
        private final Set<StructureDestination> activeStructures = new HashSet<>();
        private SettlementDestination activeSettlement;
    }
}
