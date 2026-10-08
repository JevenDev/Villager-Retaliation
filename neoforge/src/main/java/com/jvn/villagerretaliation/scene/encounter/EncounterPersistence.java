package com.jvn.villagerretaliation.scene.encounter;

import com.jvn.villagerretaliation.scene.persistence.SceneSavedData;
import com.jvn.villagerretaliation.scene.runtime.SceneReceiptGuard;
import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.server.MinecraftServer;

public final class EncounterPersistence {
    private static final Set<EncounterInstance> SAVED = Collections.newSetFromMap(new WeakHashMap<>());

    private EncounterPersistence() { }

    static String prepare(MinecraftServer server, SceneSavedData data, EncounterInstance encounter) {
        if (!required(data, encounter)) return "";
        if (data.encounter(encounter.id()).orElse(null) != encounter)
            return "The encounter no longer belongs to its original scene save. Restore matching encounter data.";
        return SAVED.contains(encounter) && !data.campaignPersistencePending() ? "" : checkpoint(server, data, encounter);
    }

    public static String checkpoint(MinecraftServer server, SceneSavedData data, EncounterInstance encounter) {
        if (!required(data, encounter)) return "";
        try {
            data.persistCampaignHandoff(server.overworld());
            SAVED.add(encounter);
            return "";
        } catch (IOException exception) {
            return "Campaign encounter progress could not be saved. Restore writable world storage to continue: " + exception.getMessage();
        }
    }

    static String checkpointBlock(MinecraftServer server, SceneSavedData data, EncounterInstance encounter,
            net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos position) {
        if (!required(data, encounter)) return "";
        try {
            com.jvn.villagerretaliation.util.WorldChunkCheckpoint.persist(level, position);
            return "";
        } catch (IOException exception) {
            String diagnostic = "Campaign encounter blocks could not be saved. Restore writable world storage to continue: " + exception.getMessage();
            var scene = data.get(encounter.sceneId()).orElse(null);
            if (scene != null && scene.pauseCampaign(diagnostic, server.overworld().getGameTime())) data.changed();
            return diagnostic;
        }
    }

    static boolean required(SceneSavedData data, EncounterInstance encounter) {
        var scene = data.get(encounter.sceneId()).orElse(null);
        return scene != null && SceneReceiptGuard.requiresPersistence(scene);
    }

    static void clearRuntimeState() { SAVED.clear(); }
}
