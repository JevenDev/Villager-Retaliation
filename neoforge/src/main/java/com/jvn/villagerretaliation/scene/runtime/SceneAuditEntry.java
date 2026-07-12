package com.jvn.villagerretaliation.scene.runtime;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

public record SceneAuditEntry(UUID sceneId,String actorAlias,String priorState,String newState,String reason,long gameTime,String operatorIdentity) {
    public SceneAuditEntry{actorAlias=actorAlias==null?"":actorAlias;priorState=priorState==null?"":priorState;newState=newState==null?"":newState;reason=reason==null?"":reason;operatorIdentity=operatorIdentity==null?"":operatorIdentity;}
    public CompoundTag save(){CompoundTag t=new CompoundTag();t.putUUID("Scene",sceneId);t.putString("Actor",actorAlias);t.putString("Prior",priorState);t.putString("New",newState);t.putString("Reason",reason);t.putLong("GameTime",gameTime);t.putString("Operator",operatorIdentity);return t;}
    public static SceneAuditEntry load(CompoundTag t){return new SceneAuditEntry(t.getUUID("Scene"),t.getString("Actor"),t.getString("Prior"),t.getString("New"),t.getString("Reason"),t.getLong("GameTime"),t.getString("Operator"));}
}
