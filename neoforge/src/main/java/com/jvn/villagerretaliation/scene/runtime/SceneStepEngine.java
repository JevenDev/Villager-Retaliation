package com.jvn.villagerretaliation.scene.runtime;

import com.jvn.villagerretaliation.api.scene.SceneStepExecutor;
import com.jvn.villagerretaliation.api.scene.SceneStepExecutors;
import com.jvn.villagerretaliation.scene.model.CompiledScene;
import com.jvn.villagerretaliation.scene.persistence.SceneSavedData;
import com.jvn.villagerretaliation.scene.model.SceneResource;
import com.jvn.villagerretaliation.scene.runtime.SceneAuditEntry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.MinecraftServer;

public final class SceneStepEngine {
    private static final Set<String> PREPARED_THIS_SESSION=ConcurrentHashMap.newKeySet();
    private SceneStepEngine() { }

    public static SceneScheduler.ProcessResult process(MinecraftServer server, SceneSavedData data, SceneInstance instance,
            CompiledScene definition,long time){
        if(definition==null){instance.block("definition_missing","scene definition is unavailable",time);data.changed();return SceneScheduler.ProcessResult.idle();}
        var reconciliation=SceneDefinitionReconciler.reconcile(instance,definition);
        if(!reconciliation.safe()){instance.block("definition_incompatible",reconciliation.diagnostic(),time);data.changed();return SceneScheduler.ProcessResult.idle();}
        if (definition.timeoutTicks() > 0L && !instance.deadlineHandled()
                && time >= deadline(instance.startGameTime(), definition.timeoutTicks())) {
            return timeout(data, instance, definition, time);
        }
        if(instance.state()==SceneState.PENDING)instance.transition(SceneState.RUNNING,time);
        if(instance.state()==SceneState.WAITING)instance.transition(SceneState.RUNNING,time);
        CompiledScene.CompiledStep step=definition.steps().get(instance.currentStep());
        if(step==null){instance.block("step_missing","current step is absent from compiled definition",time);data.changed();return SceneScheduler.ProcessResult.idle();}
        SceneStepExecutor executor=SceneStepExecutors.get(step.type()).orElse(null);
        if(executor==null){instance.block("executor_missing","no executor registered for "+step.type(),time);data.changed();return SceneScheduler.ProcessResult.idle();}
        SceneStepRecord record=instance.currentRecord(step.type());String key=instance.id()+"/"+step.id();
        boolean fresh=PREPARED_THIS_SESSION.contains(key);
        SceneExecutionContext context=new SceneExecutionContext(server,data,instance,definition,step,record,time,fresh);
        SceneStepResult result;
        switch(record.status()){
            case PENDING -> {result=executor.prepare(context);if(result.outcome()==SceneStepResult.Outcome.READY){record.status(StepExecutionStatus.PREPARED,time);PREPARED_THIS_SESSION.add(key);data.changed();return SceneScheduler.ProcessResult.now();}}
            case PREPARED,RUNNING -> result=fresh?executor.apply(context):executor.reconcile(context);
            case APPLIED -> result=executor.verify(context);
            case COMPLETED,SKIPPED -> {return advance(instance,definition,step,record,time,data,"");}
            case FAILED -> {instance.block("step_failed",record.failureCode(),time);data.changed();return SceneScheduler.ProcessResult.idle();}
            default -> result=SceneStepResult.block("invalid_step_state","unsupported step state");
        }
        return handle(result,instance,definition,step,record,time,data,key);
    }

    private static SceneScheduler.ProcessResult handle(SceneStepResult result,SceneInstance instance,CompiledScene definition,
            CompiledScene.CompiledStep step,SceneStepRecord record,long time,SceneSavedData data,String key){
        return switch(result.outcome()){
            case READY -> {record.status(StepExecutionStatus.PREPARED,time);PREPARED_THIS_SESSION.add(key);data.changed();yield SceneScheduler.ProcessResult.now();}
            case APPLIED -> {record.status(StepExecutionStatus.APPLIED,time);data.changed();yield SceneScheduler.ProcessResult.now();}
            case COMPLETE -> {record.status(StepExecutionStatus.COMPLETED,time);if(!result.transition().isBlank())record.chooseTransition(result.transition());PREPARED_THIS_SESSION.remove(key);data.changed();yield advance(instance,definition,step,record,time,data,result.transition());}
            case SKIP -> {record.status(StepExecutionStatus.SKIPPED,time);PREPARED_THIS_SESSION.remove(key);data.changed();yield advance(instance,definition,step,record,time,data,result.transition());}
            case WAIT -> {record.wakeTime(result.wakeTime());instance.transition(SceneState.WAITING,time);data.changed();yield SceneScheduler.ProcessResult.wakeAt(result.wakeTime());}
            case BLOCK -> {instance.block(result.code().isBlank()?"step_blocked":result.code(),result.diagnostic(),time);data.changed();yield SceneScheduler.ProcessResult.idle();}
            case FAIL -> {record.fail(result.code());instance.fail(result.code(),result.diagnostic(),time);data.changed();yield SceneScheduler.ProcessResult.idle();}
        };
    }

    private static SceneScheduler.ProcessResult advance(SceneInstance instance,CompiledScene definition,CompiledScene.CompiledStep step,
            SceneStepRecord record,long time,SceneSavedData data,String requested){
        if(step.terminal()){
            if(step.type().getPath().equals("scene_fail"))instance.fail("scene_fail","scene reached authored failure terminal",time);else instance.complete(time);
            data.changed();return SceneScheduler.ProcessResult.idle();
        }
        String choice=!record.chosenTransition().isBlank()?record.chosenTransition():requested;
        String next=choice.isBlank()?step.transitions().get("success"):step.transitions().getOrDefault(choice,choice);
        if(next==null||!definition.steps().containsKey(next)){instance.block("transition_missing","step did not resolve a valid next transition",time);data.changed();return SceneScheduler.ProcessResult.idle();}
        instance.advance(next,time);data.changed();return SceneScheduler.ProcessResult.now();
    }

    public static void clearRuntimeState(){PREPARED_THIS_SESSION.clear();}

    private static long deadline(long start, long duration) {
        return duration > Long.MAX_VALUE - start ? Long.MAX_VALUE : start + duration;
    }

    private static SceneScheduler.ProcessResult timeout(SceneSavedData data, SceneInstance instance,
            CompiledScene definition, long time) {
        SceneState prior = instance.state();
        instance.markDeadlineHandled();
        switch (definition.failurePolicy()) {
            case FAIL_SCENE -> instance.fail("scene_timeout", "scene exceeded its authored overall timeout", time);
            case CANCEL_SCENE -> instance.cancel("scene_timeout", "scene was cancelled after its authored overall timeout", time);
            case BLOCK_FOR_REPAIR -> instance.block("scene_timeout", "scene timed out and requires operator repair", time);
            case RUN_FAILURE_STEP -> {
                CompiledScene.CompiledStep current = definition.steps().get(instance.currentStep());
                if (current != null && !current.failureStep().isBlank()
                        && definition.steps().containsKey(current.failureStep())) {
                    instance.advance(current.failureStep(), time);
                } else {
                    instance.block("scene_timeout_failure_step_missing",
                            "scene timed out but the current step has no authored failure_step", time);
                }
            }
        }
        data.audit(new SceneAuditEntry(instance.id(), "", prior.name(), instance.state().name(),
                "scene_timeout", time, "runtime"));
        if (instance.state().terminal()) {
            instance.cleanupStatus(SceneInstance.CleanupStatus.RUNNING);
            data.requestCleanup(instance);
        }
        data.changed();
        return instance.state().terminal() || instance.state() == SceneState.BLOCKED
                ? SceneScheduler.ProcessResult.idle() : SceneScheduler.ProcessResult.now();
    }
}
