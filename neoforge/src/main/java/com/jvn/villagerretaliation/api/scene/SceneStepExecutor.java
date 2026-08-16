package com.jvn.villagerretaliation.api.scene;

import com.jvn.villagerretaliation.api.registry.ExtensionContracts.RecoveryMode;
import com.jvn.villagerretaliation.scene.runtime.SceneExecutionContext;
import com.jvn.villagerretaliation.scene.runtime.SceneStepResult;

public interface SceneStepExecutor {
    RecoveryMode recoveryMode();
    SceneStepResult prepare(SceneExecutionContext context);
    SceneStepResult apply(SceneExecutionContext context);
    SceneStepResult verify(SceneExecutionContext context);
    SceneStepResult reconcile(SceneExecutionContext context);
    default SceneStepResult cancel(SceneExecutionContext context){return SceneStepResult.complete();}
    default SceneStepResult cleanup(SceneExecutionContext context){return SceneStepResult.complete();}
}
