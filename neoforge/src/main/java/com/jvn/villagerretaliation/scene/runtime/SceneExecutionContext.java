package com.jvn.villagerretaliation.scene.runtime;

import com.jvn.villagerretaliation.scene.model.CompiledScene;
import com.jvn.villagerretaliation.scene.persistence.SceneSavedData;
import net.minecraft.server.MinecraftServer;

public record SceneExecutionContext(MinecraftServer server,SceneSavedData repository,SceneInstance instance,
                                    CompiledScene definition,CompiledScene.CompiledStep step,SceneStepRecord record,
                                    long gameTime,boolean preparedThisSession) {
    public String operationId(String suffix){return instance.id()+"/"+step.id()+"/"+(suffix==null?"operation":suffix);}
    public SceneOperationReceipt prepareReceipt(String suffix,SceneOperationReceipt.Kind kind){return instance.prepareReceipt(operationId(suffix),kind,gameTime);}
}
