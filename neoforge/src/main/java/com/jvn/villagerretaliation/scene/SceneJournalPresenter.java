package com.jvn.villagerretaliation.scene;

import com.jvn.villagerretaliation.scene.encounter.EncounterInstance;
import com.jvn.villagerretaliation.scene.persistence.SceneSavedData;
import com.jvn.villagerretaliation.scene.runtime.SceneInstance;
import com.jvn.villagerretaliation.scene.runtime.SceneState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public final class SceneJournalPresenter {
    private SceneJournalPresenter(){}
    public static String status(ServerPlayer player,ResourceLocation questId){if(player==null||questId==null)return "";SceneSavedData data=SceneSavedData.get(player.serverLevel());for(SceneInstance scene:data.all()){if(!questId.equals(scene.owningQuestId())||(!scene.participants().contains(player.getUUID())&&!player.getUUID().equals(scene.owner().playerId())))continue;if(scene.state()==SceneState.BLOCKED)return "Scene blocked — operator repair required";if(scene.state()==SceneState.FAILED)return "Scene failed";if(scene.state()==SceneState.WAITING){var record=scene.stepRecords().get(scene.currentStep());if(record!=null&&record.stepType().getPath().equals("move_actor"))return "Waiting for actor";return "Scene waiting";}for(EncounterInstance encounter:data.encounters())if(encounter.sceneId().equals(scene.id())&&encounter.state()==EncounterInstance.EncounterState.ACTIVE)return "Encounter active";if(scene.owner().partyId()!=null&&scene.participants().stream().noneMatch(id->player.getServer().getPlayerList().getPlayer(id)!=null))return "Waiting for party";}return "";}
}
