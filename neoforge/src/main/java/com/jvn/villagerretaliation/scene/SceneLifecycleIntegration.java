package com.jvn.villagerretaliation.scene;

import com.jvn.villagerretaliation.scene.actor.SceneActorBinding;
import com.jvn.villagerretaliation.scene.encounter.EncounterService;
import com.jvn.villagerretaliation.scene.persistence.SceneSavedData;
import com.jvn.villagerretaliation.scene.runtime.SceneInstance;
import com.jvn.villagerretaliation.scene.runtime.SceneState;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

public final class SceneLifecycleIntegration {
    private SceneLifecycleIntegration(){}
    public static void onQuestTerminal(ServerLevel level,UUID player,ResourceLocation questId,String reason){SceneSavedData data=SceneSavedData.get(level);for(SceneInstance scene:data.active()){if(!questId.equals(scene.owningQuestId())||!ownedBy(scene,player))continue;if("completed".equals(reason)&&scene.state()==SceneState.COMPLETED)continue;scene.transition(SceneState.CANCELLED,level.getGameTime());for(var encounter:data.encounters())if(encounter.sceneId().equals(scene.id()))EncounterService.cleanup(level.getServer(),data,encounter,false);data.changed();}}
    public static void onActorDeath(Entity entity){if(!(entity.level() instanceof ServerLevel level))return;SceneSavedData data=SceneSavedData.get(level);for(SceneInstance scene:data.active())for(var entry:scene.actorBindings().entrySet())if(entity.getUUID().equals(entry.getValue().entityId())){scene.replaceBinding(entry.getKey(),entry.getValue().withState(SceneActorBinding.BindingState.DEAD));data.changed();SceneRuntime.wake(level.getServer(),scene);}}
    public static void onEntityReturn(Entity entity){if(!(entity.level() instanceof ServerLevel level))return;SceneSavedData data=SceneSavedData.get(level);for(SceneInstance scene:data.active())for(var entry:scene.actorBindings().entrySet())if(entity.getUUID().equals(entry.getValue().entityId())){scene.replaceBinding(entry.getKey(),entry.getValue().withObservation(level.dimension().location(),entity.blockPosition(),true));data.changed();SceneRuntime.wake(level.getServer(),scene);}}
    public static void onPlayerConnection(ServerPlayer player){SceneSavedData data=SceneSavedData.get(player.serverLevel());for(SceneInstance scene:data.active())if(ownedBy(scene,player.getUUID()))SceneRuntime.wake(player.getServer(),scene);}
    public static void onPartyMembershipChanged(ServerLevel level,UUID partyId){SceneSavedData data=SceneSavedData.get(level);for(SceneInstance scene:data.active())if(partyId.equals(scene.owner().partyId()))SceneRuntime.wake(level.getServer(),scene);}
    private static boolean ownedBy(SceneInstance scene,UUID player){return player!=null&&(player.equals(scene.owner().playerId())||scene.participants().contains(player));}
}
