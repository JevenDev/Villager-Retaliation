package com.jvn.villagerretaliation.scene;

import com.jvn.villagerretaliation.scene.encounter.EncounterInstance;
import com.jvn.villagerretaliation.scene.persistence.SceneSavedData;
import com.jvn.villagerretaliation.scene.runtime.SceneInstance;
import com.jvn.villagerretaliation.scene.runtime.SceneState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import java.util.Map;
import java.util.LinkedHashMap;

public final class SceneJournalQueries {
    private SceneJournalQueries() {}

    public static Status status(ServerPlayer player, ResourceLocation questId) {
        if (player == null || questId == null) return Status.NONE;
        SceneSavedData data = SceneSavedData.get(player.serverLevel());
        for (SceneInstance scene : data.all()) {
            if (!questId.equals(scene.owningQuestId())
                    || (!scene.participants().contains(player.getUUID())
                            && !player.getUUID().equals(scene.owner().playerId()))) continue;
            if (scene.state() == SceneState.BLOCKED)
                return new Status(Kind.BLOCKED, "", false, 0, 0);
            if (scene.state() == SceneState.FAILED) return new Status(Kind.FAILED, "", false, 0, 0);
            if (scene.state() == SceneState.WAITING) {
                var record = scene.stepRecords().get(scene.currentStep());
                if (record != null && record.stepType().getPath().equals("follow_actor"))
                    return scene.campaignPaused() ? new Status(Kind.DIAGNOSTIC, scene.diagnostic(), false, 0, 0)
                            : new Status(Kind.FOLLOWING, "", false, 0, 0);
                if (record != null && record.stepType().getPath().equals("move_actor"))
                    return new Status(Kind.WAITING_FOR_ACTOR, "", false, 0, 0);
                return new Status(Kind.WAITING, "", false, 0, 0);
            }
            for (EncounterInstance encounter : data.encounters())
                if (encounter.sceneId().equals(scene.id())
                        && (encounter.state() == EncounterInstance.EncounterState.ACTIVE
                                || encounter.state() == EncounterInstance.EncounterState.PAUSED
                                || encounter.state()
                                        == EncounterInstance.EncounterState.RETRY_WAIT)) {
                    String variant = encounter.selectedVariantId();
                    if (!encounter.customCompletion()) return new Status(Kind.ENCOUNTER, variant, false, 0, 0);
                    int total =
                            com.jvn.villagerretaliation.scene.encounter.EncounterResources.template(
                                            player.getServer(), encounter.templateId())
                                    .map(
                                            template ->
                                                    template.completionObjectives() == null
                                                            ? 0
                                                            : template.completionObjectives()
                                                                    .objectives()
                                                                    .size())
                                    .orElse(0);
                    return new Status(Kind.ENCOUNTER, variant, true, encounter.completedObjectives().size(), total);
                }
            if (scene.owner().partyId() != null
                    && scene.participants().stream()
                            .noneMatch(
                                    id -> player.getServer().getPlayerList().getPlayer(id) != null))
                return new Status(Kind.WAITING_FOR_PARTY, "", false, 0, 0);
        }
        return Status.NONE;
    }

    public static Map<String, String> encounterReplacements(
            ServerPlayer player, ResourceLocation questId) {
        Map<String, String> empty = emptyEncounterReplacements();
        if (player == null || questId == null) return empty;
        SceneSavedData data = SceneSavedData.get(player.serverLevel());
        for (SceneInstance scene : data.all())
            if (questId.equals(scene.owningQuestId())
                    && (scene.participants().contains(player.getUUID())
                            || player.getUUID().equals(scene.owner().playerId())))
                for (EncounterInstance encounter : data.encounters())
                    if (encounter.sceneId().equals(scene.id())) {
                        Map<String, String> values = new LinkedHashMap<>(empty);
                        values.put("encounter_variant", encounter.selectedVariantId());
                        values.put("encounter_template", encounter.templateId().toString());
                        var template =
                                com.jvn.villagerretaliation.scene.encounter.EncounterResources
                                        .template(player.getServer(), encounter.templateId())
                                        .orElse(null);
                        values.putAll(
                                com.jvn.villagerretaliation.scene.encounter.EncounterService
                                        .guidanceReplacements(player, encounter, template));
                        return Map.copyOf(values);
                    }
        return empty;
    }

    private static Map<String, String> emptyEncounterReplacements() {
        return Map.of(
                "encounter_variant",
                "",
                "encounter_template",
                "",
                "encounter_distance",
                "",
                "encounter_direction",
                "",
                "encounter_coordinates",
                "",
                "encounter_dimension",
                "",
                "encounter_discovered",
                "false",
                "encounter_arrived",
                "false");
    }

    public enum Kind { NONE, BLOCKED, FAILED, DIAGNOSTIC, FOLLOWING, WAITING_FOR_ACTOR, WAITING, ENCOUNTER, WAITING_FOR_PARTY }
    public record Status(Kind kind, String detail, boolean customCompletion, int completedObjectives, int totalObjectives) {
        public static final Status NONE = new Status(Kind.NONE, "", false, 0, 0);
    }
}
