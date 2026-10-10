package com.jvn.villagerretaliation.scene;

import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public final class SceneJournalPresenter {
    private SceneJournalPresenter() { }

    public static String status(ServerPlayer player, ResourceLocation questId) {
        return status(SceneJournalQueries.status(player, questId));
    }

    public static String status(SceneJournalQueries.Status status) {
        return switch (status.kind()) {
            case NONE -> "";
            case BLOCKED -> "Scene blocked - operator repair required";
            case FAILED -> "Scene failed";
            case DIAGNOSTIC -> status.detail();
            case FOLLOWING -> "Following the campaign character";
            case WAITING_FOR_ACTOR -> "Waiting for actor";
            case WAITING -> "Scene waiting";
            case ENCOUNTER -> "Encounter active" + (status.detail().isBlank() ? "" : " - " + status.detail())
                    + (status.customCompletion() ? " - objectives " + status.completedObjectives() + "/" + status.totalObjectives() : "");
            case WAITING_FOR_PARTY -> "Waiting for party";
        };
    }

    public static Map<String, String> encounterReplacements(ServerPlayer player, ResourceLocation questId) {
        return SceneJournalQueries.encounterReplacements(player, questId);
    }
}
