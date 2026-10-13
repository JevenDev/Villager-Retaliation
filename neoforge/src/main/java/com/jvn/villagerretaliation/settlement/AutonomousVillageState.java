package com.jvn.villagerretaliation.settlement;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.*;

/** Persisted decisions; no wall-clock catch-up and no second inventory or construction queue. */
public final class AutonomousVillageState {
    final Set<Long> sections = new LinkedHashSet<>();
    final Map<UUID, SettlementAreaType> projects = new LinkedHashMap<>();
    final Map<UUID,SettlementAreaType> structures=new LinkedHashMap<>();
    private transient long plotsRevision=Long.MIN_VALUE;
    private transient List<Long> cachedPlots=List.of();
    List<Long> plots(SettlementRecord town) {
        if(plotsRevision!=town.territory().revision()) {
            cachedPlots=town.territory().plots().stream().sorted().toList();
            plotsRevision=town.territory().revision();
        }
        return cachedPlots;
    }
    UUID ruler;
    UUID predecessor;
    long nextPlan;
    long lastIncome = -1;
    long incomeDay = -1;
    int dailyIncome;
    int observations;
    int completed;
    int expansions;
    int surveyCursor;
    int siteCursor;
    int maintenanceCursor;
    String need = "";
    String status = "Awaiting loaded residents";
    String nextAction = "Survey existing village facilities";

    public UUID ruler() { return ruler; }
    public String status() { return status; }
    public String nextAction() { return nextAction; }
    boolean contains(BlockPos pos) { return sections.contains(SectionPos.asLong(pos)); }
    boolean contains(BlockPos min, BlockPos max) {
        int x0 = SectionPos.blockToSectionCoord(min.getX()), x1 = SectionPos.blockToSectionCoord(max.getX());
        int y0 = SectionPos.blockToSectionCoord(min.getY()), y1 = SectionPos.blockToSectionCoord(max.getY());
        int z0 = SectionPos.blockToSectionCoord(min.getZ()), z1 = SectionPos.blockToSectionCoord(max.getZ());
        if ((long)(x1-x0+1)*(y1-y0+1)*(z1-z0+1) > sections.size()) return false;
        for (int x=x0;x<=x1;x++) for(int y=y0;y<=y1;y++) for(int z=z0;z<=z1;z++)
            if (!sections.contains(SectionPos.asLong(x,y,z))) return false;
        return true;
    }
    CompoundTag save() {
        var tag = new CompoundTag();
        tag.putInt("Version", 1);
        tag.putLongArray("Sections", sections.stream().mapToLong(Long::longValue).toArray());
        if (ruler != null) tag.putUUID("Ruler", ruler);
        if (predecessor != null) tag.putUUID("Predecessor", predecessor);
        tag.putLong("NextPlan", nextPlan); tag.putLong("LastIncome", lastIncome);
        tag.putLong("IncomeDay", incomeDay); tag.putInt("DailyIncome", dailyIncome);
        tag.putInt("Observations", observations); tag.putInt("Completed", completed);
        tag.putInt("Expansions", expansions); tag.putInt("SurveyCursor", surveyCursor); tag.putInt("SiteCursor", siteCursor);
        tag.putInt("MaintenanceCursor",maintenanceCursor);
        tag.putString("Need", need); tag.putString("Status", status); tag.putString("NextAction", nextAction);
        var jobs = new ListTag();
        projects.forEach((id,type) -> { var job=new CompoundTag(); job.putUUID("Id",id); job.putString("Type",type.name()); jobs.add(job); });
        tag.put("Projects", jobs);
        var built=new ListTag();structures.forEach((id,type)->{var row=new CompoundTag();row.putUUID("Id",id);row.putString("Type",type.name());built.add(row);});tag.put("Structures",built);
        return tag;
    }
    static AutonomousVillageState load(CompoundTag tag) {
        if (tag.getInt("Version") != 1) throw new IllegalArgumentException("Unknown autonomous village version");
        var state=new AutonomousVillageState();
        for(long section:tag.getLongArray("Sections")) state.sections.add(section);
        if(state.sections.isEmpty() || state.sections.size()>65536) throw new IllegalArgumentException("Invalid autonomous boundary");
        state.ruler=tag.hasUUID("Ruler")?tag.getUUID("Ruler"):null;
        state.predecessor=tag.hasUUID("Predecessor")?tag.getUUID("Predecessor"):null;
        state.nextPlan=tag.getLong("NextPlan"); state.lastIncome=tag.getLong("LastIncome");
        state.incomeDay=tag.getLong("IncomeDay"); state.dailyIncome=tag.getInt("DailyIncome");
        state.observations=tag.getInt("Observations"); state.completed=tag.getInt("Completed"); state.expansions=tag.getInt("Expansions");
        state.surveyCursor=tag.getInt("SurveyCursor"); state.siteCursor=tag.getInt("SiteCursor");
        state.maintenanceCursor=tag.getInt("MaintenanceCursor");
        state.need=tag.getString("Need"); state.status=tag.getString("Status"); state.nextAction=tag.getString("NextAction");
        for(var raw:tag.getList("Projects",Tag.TAG_COMPOUND)) { var job=(CompoundTag)raw;
            if(!job.hasUUID("Id") || state.projects.size()>=128 || state.projects.put(job.getUUID("Id"),SettlementAreaType.valueOf(job.getString("Type")))!=null)
                throw new IllegalArgumentException("Invalid autonomous projects");
        }
        for(var raw:tag.getList("Structures",Tag.TAG_COMPOUND)) {var row=(CompoundTag)raw;
            if(state.structures.size()>=32 || !row.hasUUID("Id")) throw new IllegalArgumentException("Invalid maintained structures");
            state.structures.put(row.getUUID("Id"),SettlementAreaType.valueOf(row.getString("Type")));
        }
        if(state.dailyIncome<0 || state.completed<0 || state.expansions<0 || state.observations<0) throw new IllegalArgumentException("Invalid autonomous counters");
        return state;
    }
}
