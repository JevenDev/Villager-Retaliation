package com.jvn.villagerretaliation.quest.tutorial;

import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;

public final class TutorialPlanChecks {
    private TutorialPlanChecks() { }
    public static final String STORAGE_HELP = "Select each chest with the Clipboard, use it on this worker, and click Assign Clipboard Storage. Commit one accessible Supplies link and one Output link.";
    public static String output(ServerLevel level, Villager worker) {
        var records = AssignedStorageService.assignedStorage(level, worker).stream().filter(r -> r.purpose().equals("output") && r.dimension().equals(level.dimension())).toList();
        if (records.size() != 1 || !AssignedStorageService.hasLiveAssignedStorageForPurpose(level, worker, "supply")
                || !AssignedStorageService.hasLiveAssignedStorageForPurpose(level, worker, "output")) return null;
        var pos = AssignedStorageService.canonicalContainerPosition(level, records.getFirst().pos());
        return pos == null ? null : TutorialWorkService.position(level, pos);
    }
}
