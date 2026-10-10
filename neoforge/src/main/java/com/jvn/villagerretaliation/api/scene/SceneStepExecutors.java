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
        ResourceLocation canonical = VillagerRetaliationRegistries.SCENE_STEPS.get(id)
                .orElseThrow(() -> new IllegalArgumentException("scene executor has no registered descriptor: " + id)).id();
        if(EXECUTORS.putIfAbsent(canonical,executor)!=null)throw new IllegalArgumentException("duplicate scene executor: "+canonical);
    }
    public static synchronized Optional<SceneStepExecutor> get(ResourceLocation id){
        ResourceLocation canonical = VillagerRetaliationRegistries.SCENE_STEPS.get(id)
                .map(descriptor -> descriptor.id()).orElse(id);
        return Optional.ofNullable(EXECUTORS.get(canonical));
    }
    public static synchronized Map<ResourceLocation,SceneStepExecutor> snapshot(){return Map.copyOf(EXECUTORS);}
}
