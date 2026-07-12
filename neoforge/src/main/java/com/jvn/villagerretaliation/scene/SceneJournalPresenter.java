package com.jvn.villagerretaliation.scene;

import com.jvn.villagerretaliation.scene.encounter.EncounterInstance;
import com.jvn.villagerretaliation.scene.persistence.SceneSavedData;
import com.jvn.villagerretaliation.scene.runtime.SceneInstance;
import com.jvn.villagerretaliation.scene.runtime.SceneState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import java.util.Map;

public final class SceneJournalPresenter {
    private SceneJournalPresenter(){}
    public static String status(ServerPlayer player,ResourceLocation questId){if(player==null||questId==null)return "";SceneSavedData data=SceneSavedData.get(player.serverLevel());for(SceneInstance scene:data.all()){if(!questId.equals(scene.owningQuestId())||(!scene.participants().contains(player.getUUID())&&!player.getUUID().equals(scene.owner().playerId())))continue;if(scene.state()==SceneState.BLOCKED)return "Scene blocked — operator repair required";if(scene.state()==SceneState.FAILED)return "Scene failed";if(scene.state()==SceneState.WAITING){var record=scene.stepRecords().get(scene.currentStep());if(record!=null&&record.stepType().getPath().equals("move_actor"))return "Waiting for actor";return "Scene waiting";}for(EncounterInstance encounter:data.encounters())if(encounter.sceneId().equals(scene.id())&&(encounter.state()==EncounterInstance.EncounterState.ACTIVE||encounter.state()==EncounterInstance.EncounterState.PAUSED||encounter.state()==EncounterInstance.EncounterState.RETRY_WAIT)){String variant=encounter.selectedVariantId().isBlank()?"":" — "+encounter.selectedVariantId();if(!encounter.customCompletion())return "Encounter active"+variant;int total=com.jvn.villagerretaliation.scene.encounter.EncounterResources.template(player.getServer(),encounter.templateId()).map(template->template.completionObjectives()==null?0:template.completionObjectives().objectives().size()).orElse(0);return "Encounter active"+variant+" — objectives "+encounter.completedObjectives().size()+"/"+total;}if(scene.owner().partyId()!=null&&scene.participants().stream().noneMatch(id->player.getServer().getPlayerList().getPlayer(id)!=null))return "Waiting for party";}return "";}
    public static Map<String,String> encounterReplacements(ServerPlayer player,ResourceLocation questId){if(player==null||questId==null)return Map.of("encounter_variant","","encounter_template","");SceneSavedData data=SceneSavedData.get(player.serverLevel());for(SceneInstance scene:data.all())if(questId.equals(scene.owningQuestId())&&(scene.participants().contains(player.getUUID())||player.getUUID().equals(scene.owner().playerId())))for(EncounterInstance encounter:data.encounters())if(encounter.sceneId().equals(scene.id()))return Map.of("encounter_variant",encounter.selectedVariantId(),"encounter_template",encounter.templateId().toString());return Map.of("encounter_variant","","encounter_template","");}
}
