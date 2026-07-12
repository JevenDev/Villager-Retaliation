package com.jvn.villagerretaliation.api.scene;

import com.jvn.villagerretaliation.api.VillagerRetaliationRegistries;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/** Public executor attachment for registered scene-step descriptors. */
public final class SceneStepExecutors {
    private static final Map<ResourceLocation, SceneStepExecutor> EXECUTORS = new LinkedHashMap<>();
    private SceneStepExecutors() { }
    public static synchronized void register(ResourceLocation id,SceneStepExecutor executor){
        if(id==null||executor==null)throw new IllegalArgumentException("scene executor id and implementation are required");
        if(VillagerRetaliationRegistries.SCENE_STEPS.get(id).isEmpty())throw new IllegalArgumentException("scene executor has no registered descriptor: "+id);
        if(EXECUTORS.putIfAbsent(id,executor)!=null)throw new IllegalArgumentException("duplicate scene executor: "+id);
    }
    public static synchronized Optional<SceneStepExecutor> get(ResourceLocation id){return Optional.ofNullable(EXECUTORS.get(id));}
    public static synchronized Map<ResourceLocation,SceneStepExecutor> snapshot(){return Map.copyOf(EXECUTORS);}
}
