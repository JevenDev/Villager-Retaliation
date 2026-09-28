package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

final class SettlementRecovery {
    private final List<CompoundTag> quarantined = new ArrayList<>();
    private final List<String> diagnostics = new ArrayList<>();
    private CompoundTag preservedRoot;
    private CompoundTag original;

    boolean readOnly() { return this.preservedRoot != null; }
    CompoundTag preservedRoot() { return this.preservedRoot; }
    List<String> diagnostics() { return List.copyOf(this.diagnostics); }

    boolean reserved(VillageAllegianceId id) {
        return id != null && this.quarantined.stream().anyMatch(raw -> raw.getUUID("Id").equals(id.value()));
    }

    void appendQuarantine(ListTag entries) {
        this.quarantined.forEach(raw -> entries.add(raw.copy()));
    }

    void saveOriginal(CompoundTag tag) {
        if (this.original != null) tag.put("RecoveryOriginalRoot", this.original.copy());
    }

    void protect(CompoundTag tag, String reason) {
        this.preservedRoot = tag.copy();
        report(reason + ". All settlement management is disabled and the original file will not be written.");
    }

    private void report(String reason) {
        this.diagnostics.add(reason);
        LogUtils.getLogger().error("Settlement recovery: {} Stop the server and back up data/villagerretaliation_settlements.dat before repair. "
                + "Use a compatible mod version for future formats. Repair quarantined Settlements entries using RecoveryOriginalRoot "
                + "or restore a known-good backup, then restart. Never delete the file to reset recovery.", reason);
    }

    void load(CompoundTag tag, HolderLookup.Provider provider, int supportedVersion, Runnable dirty,
              Map<VillageAllegianceId, SettlementRecord> settlements,
              Map<ResourceLocation, Map<Long, VillageAllegianceId>> ownership) {
        int version = tag.getInt("FormatVersion");
        if (version < 0 || version > supportedVersion || tag.contains("FormatVersion") && !tag.contains("FormatVersion", Tag.TAG_INT)
                || !tag.contains("Settlements", Tag.TAG_LIST)
                || !(tag.get("Settlements") instanceof ListTag entries)
                || !entries.isEmpty() && entries.getElementType() != Tag.TAG_COMPOUND) {
            protect(tag, "Unsupported or malformed settlement root (format " + version + ")");
            return;
        }
        if (tag.contains("RecoveryOriginalRoot", Tag.TAG_COMPOUND)) this.original = tag.getCompound("RecoveryOriginalRoot").copy();
        var records = new ArrayList<SettlementRecord>();
        var payloads = new ArrayList<CompoundTag>();
        var failures = new HashMap<Integer, String>();
        var identities = new HashMap<VillageAllegianceId, Integer>();
        var plots = new HashMap<ResourceLocation, Map<Long, Integer>>();
        var reserved = new LinkedHashMap<ResourceLocation, Map<Long, VillageAllegianceId>>();
        for (Tag raw : tag.getList("Settlements", Tag.TAG_COMPOUND)) {
            var payload = (CompoundTag) raw;
            int index = payloads.size();
            payloads.add(payload.copy());
            SettlementRecord town = null;
            try {
                town = SettlementRecord.load(payload, provider, dirty);
            } catch (RuntimeException exception) {
                failures.put(index, exception.toString());
            }
            records.add(town);
            try {
                if (!payload.hasUUID("Id") || !payload.contains("Center", Tag.TAG_LONG)) {
                    protect(tag, "Cannot safely reserve identity and territory of entry " + index);
                    return;
                }
                var id = new VillageAllegianceId(payload.getUUID("Id"));
                var dimension = ResourceLocation.parse(payload.getString("Dimension"));
                if (payload.contains("Territory") && (!payload.contains("Territory", Tag.TAG_COMPOUND)
                        || !payload.getCompound("Territory").contains("Plots", Tag.TAG_LONG_ARRAY)
                        || !payload.getCompound("Territory").contains("Migrated", Tag.TAG_BYTE))) {
                    protect(tag, "Cannot safely read territory boundaries of entry " + index);
                    return;
                }
                var territory = town != null ? town.territory()
                        : SettlementTerritory.load(BlockPos.of(payload.getLong("Center")), payload.getCompound("Territory"));
                if (!territory.migrated() && town == null) {
                    protect(tag, "Cannot safely reserve invalid legacy entry " + index);
                    return;
                }
                Integer duplicate = identities.putIfAbsent(id, index);
                if (duplicate != null) {
                    failures.put(index, "Duplicate identity " + id);
                    failures.put(duplicate, "Duplicate identity " + id);
                }
                var dimensionPlots = plots.computeIfAbsent(dimension, ignored -> new HashMap<>());
                for (long plot : territory.plots()) {
                    Integer conflict = dimensionPlots.putIfAbsent(plot, index);
                    if (conflict != null) {
                        failures.put(index, "Conflicting ownership with entry " + conflict);
                        failures.put(conflict, "Conflicting ownership with entry " + index);
                    }
                    reserved.computeIfAbsent(dimension, ignored -> new HashMap<>()).putIfAbsent(plot, id);
                }
            } catch (RuntimeException exception) {
                protect(tag, "Cannot safely reserve entry " + index + ": " + exception);
                return;
            }
        }
        for (int index : failures.keySet()) {
            if (records.get(index) != null && !records.get(index).territory().migrated()) {
                protect(tag, "Conflicting legacy entry " + index + " has no reliable territory boundary");
                return;
            }
        }
        for (int index = 0; index < records.size(); index++) {
            if (failures.containsKey(index)) {
                this.quarantined.add(payloads.get(index));
                report("Quarantined entry " + index + " (" + payloads.get(index).get("Id") + "): " + failures.get(index));
            } else {
                var town = records.get(index);
                settlements.put(town.id(), town);
            }
        }
        ownership.putAll(reserved);
        if (!failures.isEmpty() && this.original == null) this.original = tag.copy();
    }
}
